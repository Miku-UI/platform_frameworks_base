/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.shared.plugins

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.test.filters.FlakyTest
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.systemui.SysuiTestCase
import com.android.systemui.log.LogcatOnlyMessageBuffer
import com.android.systemui.log.assertRunnableLogsWtf
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.core.MessageBuffer
import com.android.systemui.plugins.Plugin
import com.android.systemui.plugins.PluginLifecycleManager
import com.android.systemui.plugins.PluginListener
import com.android.systemui.plugins.PluginManager
import com.android.systemui.plugins.PluginWrapper
import com.android.systemui.plugins.TestPlugin
import com.android.systemui.plugins.annotations.Requires
import java.lang.ref.WeakReference
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@FlakyTest(bugId = 395832204)
@RunWith(AndroidJUnit4::class)
class PluginInstanceTest : SysuiTestCase() {
    private lateinit var mPluginListener: FakeListener
    private lateinit var mVersionInfo: VersionInfo

    private var mVersionCheckResult = true
    private lateinit var mVersionChecker: VersionChecker

    private lateinit var mCounter: RefCounter
    private lateinit var mPluginInstance: PluginInstance<TestPlugin>
    private lateinit var mPluginInstanceFactory: PluginInstance.Factory
    private lateinit var mAppInfo: ApplicationInfo

    // Because we're testing memory in this file, we must be careful not to assert the target
    // objects, or capture them via mockito if we expect the garbage collector to later free them.
    // Both JUnit and Mockito will save references and prevent these objects from being cleaned up.
    private var mPlugin: WeakReference<TestPluginImpl>? = null
    private var mPluginContext: WeakReference<Context>? = null

    @Before
    @Throws(Exception::class)
    fun setup() {
        mCounter = RefCounter()
        mAppInfo = mContext.applicationInfo
        mAppInfo.packageName = TEST_PLUGIN_COMPONENT_NAME.packageName
        mPluginListener = FakeListener()
        mVersionInfo = VersionInfo()
        mVersionChecker =
            object : VersionChecker {
                override fun <T : Plugin> checkVersion(
                    instanceClass: Class<T>,
                    pluginClass: Class<T>,
                    plugin: Plugin?,
                ): Boolean {
                    return mVersionCheckResult
                }

                override fun <T : Plugin> getVersionInfo(instanceClass: Class<T>): VersionInfo {
                    return mVersionInfo
                }
            }

        mPluginInstanceFactory =
            PluginInstance.Factory(
                mVersionChecker,
                javaClass.classLoader!!,
                PluginManager.Config(listOf(PRIVILEGED_PACKAGE)),
                BuildInfo(BuildVariant.User, isDebuggable = false),
            ) { _ ->
                val plugin = TestPluginImpl(mCounter)
                mPlugin = WeakReference(plugin)
                plugin
            }

        mPluginInstance =
            mPluginInstanceFactory.create(
                mContext,
                mAppInfo,
                TEST_PLUGIN_COMPONENT_NAME,
                TestPlugin::class.java,
                mPluginListener,
            )!!
        mPluginContext = WeakReference(mPluginInstance.pluginContext)
    }

    @Test
    fun testCorrectVersion_onCreateBuildsPlugin() {
        mVersionCheckResult = true
        assertFalse(mPluginInstance.hasError)

        mPluginInstance.onCreate()
        assertFalse(mPluginInstance.hasError)
        assertNotNull(mPluginInstance.plugin)
    }

    @Test
    @Throws(Exception::class)
    fun testIncorrectVersion_destroysPluginInstance() {
        val wrongVersionTestPluginComponentName =
            ComponentName(PRIVILEGED_PACKAGE, TestPlugin::class.java.name)

        mVersionCheckResult = false
        assertFalse(mPluginInstance.hasError)

        mPluginInstanceFactory
            .create(
                mContext,
                mAppInfo,
                wrongVersionTestPluginComponentName,
                TestPlugin::class.java,
                mPluginListener,
            )
            ?.let { errorInstance ->
                mPluginInstance = errorInstance
                assertRunnableLogsWtf { errorInstance.onCreate() }
                assertTrue(errorInstance.hasError)
                assertNull(errorInstance.plugin)
            } ?: fail("returned null plugin instance")
    }

    @Test
    fun testOnCreate() {
        mPluginInstance.onCreate()
        assertEquals(1, mPluginListener.mAttachedCount)
        assertEquals(1, mPluginListener.mLoadCount)
        assertEquals(mPlugin?.get(), unwrap(mPluginInstance.plugin))
        assertInstances(1, 1)
    }

    @Test
    fun testOnDestroy() {
        mPluginInstance.onCreate()
        mPluginInstance.onDestroy()
        assertEquals(1, mPluginListener.mDetachedCount)
        assertEquals(1, mPluginListener.mUnloadCount)
        assertNull(mPluginInstance.plugin)
        assertInstances(0, 0) // Destroyed but never created
    }

    @Test
    fun testOnUnloadAfterLoad() {
        mPluginInstance.onCreate()
        mPluginInstance.loadPlugin()
        assertNotNull(mPluginInstance.plugin)
        assertInstances(1, 1)

        mPluginInstance.unloadPlugin()
        assertNull(mPluginInstance.plugin)
        assertInstances(0, 0)
    }

    @Test
    fun testOnAttach_SkipLoad() {
        mPluginListener.mOnAttach = { false }
        mPluginInstance.onCreate()
        assertEquals(1, mPluginListener.mAttachedCount)
        assertEquals(0, mPluginListener.mLoadCount)
        assertNull(mPluginInstance.plugin)
        assertInstances(0, 0)
    }

    @Test
    fun testLinkageError_caughtAndPluginDestroyed() {
        mPluginInstance.onCreate()
        assertFalse(mPluginInstance.hasError)

        assertRunnableLogsWtf { mPluginInstance.plugin?.methodThrowsError() }
        assertTrue(mPluginInstance.hasError)
        assertNull(mPluginInstance.plugin)
    }

    @Test
    @Throws(Throwable::class)
    fun testLoadUnloadSimultaneous_HoldsUnload() {
        val loadLock = Semaphore(1)
        val unloadLock = Semaphore(1)

        mPluginListener.mOnAttach = { false }
        mPluginListener.mOnLoad = {
            assertNotNull(mPluginInstance.plugin)
            // Allow the bg thread the opportunity to delete the plugin
            loadLock.release()
            Thread.yield()
            val isLocked = getLock(unloadLock, 1000)

            // Ensure the bg thread failed to delete the plugin
            assertNotNull(mPluginInstance.plugin)
            // We expect that bgThread deadlocked holding the semaphore
            assertFalse(isLocked)
        }

        val bgFailure = AtomicReference<Throwable?>(null)
        val bgThread = Thread {
            assertTrue(getLock(unloadLock, 10))
            assertTrue(getLock(loadLock, 10000)) // Wait for the foreground thread
            assertNotNull(mPluginInstance.plugin)
            // Attempt to delete the plugin, this should block until the load completes
            mPluginInstance.unloadPlugin()
            assertNull(mPluginInstance.plugin)
            unloadLock.release()
            loadLock.release()
        }

        // This protects the test suite from crashing due to the uncaught exception.
        bgThread.uncaughtExceptionHandler =
            Thread.UncaughtExceptionHandler { _, ex ->
                Log.e(
                    "PluginInstanceTest#testLoadUnloadSimultaneous_HoldsUnload",
                    "Exception from BG Thread",
                    ex,
                )
                bgFailure.set(ex)
            }

        loadLock.acquire()
        mPluginInstance.onCreate()

        assertNull(mPluginInstance.plugin)
        bgThread.start()
        mPluginInstance.loadPlugin()

        bgThread.join(5000)

        // Rethrow final background exception on test thread
        val bgEx = bgFailure.get()
        if (bgEx != null) {
            throw bgEx
        }

        assertNull(mPluginInstance.plugin)
    }

    private fun getLock(lock: Semaphore, millis: Long): Boolean {
        try {
            return lock.tryAcquire(millis, TimeUnit.MILLISECONDS)
        } catch (ex: InterruptedException) {
            Log.e("PluginInstanceTest#getLock", "Interrupted Exception getting lock", ex)
            fail()
            return false
        }
    }

    private fun assertInstances(allocated: Int, created: Int) {
        // If there are more than the expected number of allocated instances, then we run the
        // garbage collector to finalize and deallocate any outstanding non-referenced instances.
        // Since the GC doesn't always appear to want to run completely when we ask, we do this up
        // to 10 times before failing the test.
        var i = 0
        while (mCounter.allocatedInstances > allocated && i < 10) {
            System.runFinalization()
            System.gc()
            i++
        }

        assertEquals(allocated, mCounter.allocatedInstances)
        assertEquals(created, mCounter.createdInstances)
    }

    class RefCounter {
        val mAllocatedInstances: AtomicInteger = AtomicInteger()
        val mCreatedInstances: AtomicInteger = AtomicInteger()

        val allocatedInstances: Int
            get() = mAllocatedInstances.get()

        val createdInstances: Int
            get() = mCreatedInstances.get()
    }

    @Requires(target = TestPlugin::class, version = TestPlugin.VERSION)
    class TestPluginImpl(private val counter: RefCounter) : TestPlugin {
        init {
            counter.mAllocatedInstances.getAndIncrement()
        }

        fun finalize() {
            counter.mAllocatedInstances.getAndDecrement()
        }

        override fun onCreate(sysuiContext: Context, pluginContext: Context) {
            counter.mCreatedInstances.getAndIncrement()
        }

        override fun onDestroy() {
            counter.mCreatedInstances.getAndDecrement()
        }

        override fun methodThrowsError(): Any {
            throw LinkageError()
        }
    }

    inner class FakeListener : PluginListener<TestPlugin> {
        var mOnAttach: (() -> Boolean)? = null
        var mOnDetach: (() -> Unit)? = null
        var mOnLoad: (() -> Unit)? = null
        var mOnUnload: (() -> Unit)? = null

        var mAttachedCount: Int = 0
            private set

        var mDetachedCount: Int = 0
            private set

        var mLoadCount: Int = 0
            private set

        var mUnloadCount: Int = 0
            private set

        override fun getLogBuffer(): MessageBuffer {
            return LogcatOnlyMessageBuffer(LogLevel.DEBUG)
        }

        override fun onPluginAttached(manager: PluginLifecycleManager<TestPlugin>): Boolean {
            mAttachedCount++
            assertEquals(this@PluginInstanceTest.mPluginInstance, manager)
            return mOnAttach?.invoke() ?: true
        }

        override fun onPluginDetached(manager: PluginLifecycleManager<TestPlugin>) {
            mDetachedCount++
            assertEquals(this@PluginInstanceTest.mPluginInstance, manager)
            mOnDetach?.invoke()
        }

        override fun onPluginLoaded(
            plugin: TestPlugin,
            pluginContext: Context,
            manager: PluginLifecycleManager<TestPlugin>,
        ) {
            mLoadCount++
            mPlugin?.get()?.let { expectedPlugin -> assertEquals(expectedPlugin, unwrap(plugin)) }
            mPluginContext?.get()?.let { expectedContext ->
                assertEquals(expectedContext, pluginContext)
            }
            assertEquals(this@PluginInstanceTest.mPluginInstance, manager)
            mOnLoad?.invoke()
        }

        override fun onPluginUnloaded(
            plugin: TestPlugin,
            manager: PluginLifecycleManager<TestPlugin>,
        ) {
            mUnloadCount++
            mPlugin?.get()?.let { expectedPlugin -> assertEquals(expectedPlugin, unwrap(plugin)) }
            assertEquals(this@PluginInstanceTest.mPluginInstance, manager)
            mOnUnload?.invoke()
        }
    }

    companion object {
        private const val PRIVILEGED_PACKAGE = "com.android.systemui.plugins"
        private val TEST_PLUGIN_COMPONENT_NAME =
            ComponentName(PRIVILEGED_PACKAGE, TestPluginImpl::class.java.name)

        private inline fun <reified T> unwrap(plugin: T): T {
            return (plugin as? PluginWrapper<*>)?.plugin as? T ?: plugin
        }
    }
}
