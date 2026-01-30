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

import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.systemui.SysuiTestCase
import com.android.systemui.SysuiTestableContext
import com.android.systemui.plugins.Plugin
import com.android.systemui.plugins.PluginListener
import com.android.systemui.plugins.PluginManager
import com.android.systemui.plugins.annotations.Requires
import com.android.systemui.shared.plugins.PluginEnabler.DisableReason
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer

@SmallTest
@RunWith(AndroidJUnit4::class)
class PluginActionManagerTest : SysuiTestCase() {
    @Mock private lateinit var mMockPlugin: TestPlugin
    @Mock private lateinit var mMockPm: PackageManager
    @Mock private lateinit var mMockListener: PluginListener<TestPlugin>
    private lateinit var mPluginActionManager: PluginActionManager<TestPlugin>
    @Mock private lateinit var mMockEnabler: PluginEnabler
    private val mTestPluginComponentName =
        ComponentName(PRIVILEGED_PACKAGE, TestPlugin::class.java.name)
    private val mFakeExecutor = FakeExecutor(FakeSystemClock())
    @Mock private lateinit var mNotificationManager: NotificationManager
    @Mock private lateinit var mPluginInstance: PluginInstance<TestPlugin>
    private val mPluginInstanceFactory: PluginInstance.Factory =
        object :
            PluginInstance.Factory(
                VersionCheckerImpl(),
                this::class.java.classLoader!!,
                PluginManager.Config(),
                BuildInfo(BuildVariant.User, isDebuggable = false),
            ) {
            override fun <T : Plugin> create(
                hostContext: Context,
                pluginAppInfo: ApplicationInfo,
                componentName: ComponentName,
                pluginClass: Class<T>,
                listener: PluginListener<T>,
            ): PluginInstance<T> {
                return mPluginInstance as PluginInstance<T>
            }
        }

    private lateinit var mActionManagerFactory: PluginActionManager.Factory

    @Before
    @Throws(Exception::class)
    fun setup() {
        mContext = MyContextWrapper(mContext)
        MockitoAnnotations.openMocks(this)
        whenever(mPluginInstance.componentName).thenReturn(mTestPluginComponentName)
        whenever(mPluginInstance.packageName).thenReturn(mTestPluginComponentName.packageName)
        mActionManagerFactory =
            PluginActionManager.Factory(
                context,
                mMockPm,
                mFakeExecutor,
                mFakeExecutor,
                mNotificationManager,
                mMockEnabler,
                PluginManager.Config(),
                mPluginInstanceFactory,
            )

        mPluginActionManager =
            mActionManagerFactory.create(
                "myAction",
                mMockListener,
                TestPlugin::class.java,
                allowMultiple = true,
            )
        whenever(mMockPlugin.version).thenReturn(1)
    }

    @Test
    fun testNoPlugins() {
        whenever(mMockPm.queryIntentServices(any(), anyInt())).thenReturn(emptyList())
        mPluginActionManager.loadAll()

        mFakeExecutor.runAllReady()

        verify(mMockListener, never()).onPluginConnected(any(), any())
    }

    @Test
    @Throws(Exception::class)
    fun testPluginCreate() {
        // Debug.waitForDebugger();
        createPlugin()

        // Verify startup lifecycle
        verify(mPluginInstance).onCreate()
    }

    @Test
    @Throws(Exception::class)
    fun testPluginDestroy() {
        createPlugin() // Get into valid created state.

        mPluginActionManager.destroy()

        mFakeExecutor.runAllReady()

        // Verify shutdown lifecycle
        verify(mPluginInstance).onDestroy()
    }

    @Test
    @Throws(Exception::class)
    fun testReloadOnChange() {
        createPlugin() // Get into valid created state.

        mPluginActionManager.reloadPackage(PRIVILEGED_PACKAGE)

        mFakeExecutor.runAllReady()

        // Verify the old one was destroyed.
        verify(mPluginInstance).onDestroy()
        verify(mPluginInstance, times(2)).onCreate()
    }

    @Test
    @Throws(Exception::class)
    fun testNonDebuggable() {
        // Create a version that thinks the build is not debuggable.
        mPluginActionManager =
            mActionManagerFactory.create(
                "myAction",
                mMockListener,
                TestPlugin::class.java,
                allowMultiple = true,
            )
        setupFakePmQuery()

        mPluginActionManager.loadAll()

        mFakeExecutor.runAllReady()

        // Non-debuggable build should receive no plugins.
        verify(mMockListener, never()).onPluginConnected(any(), any())
    }

    @Test
    @Throws(Exception::class)
    fun testNonDebuggable_privileged() {
        // Create a version that thinks the build is not debuggable.
        val factory =
            PluginActionManager.Factory(
                context,
                mMockPm,
                mFakeExecutor,
                mFakeExecutor,
                mNotificationManager,
                mMockEnabler,
                PluginManager.Config(listOf(PRIVILEGED_PACKAGE)),
                mPluginInstanceFactory,
            )
        mPluginActionManager =
            factory.create("myAction", mMockListener, TestPlugin::class.java, allowMultiple = true)
        setupFakePmQuery()

        mPluginActionManager.loadAll()

        mFakeExecutor.runAllReady()

        // Verify startup lifecycle
        verify(mPluginInstance).onCreate()
    }

    @Test
    @Throws(Exception::class)
    fun testCheckAndDisable() {
        createPlugin() // Get into valid created state.

        // Start with an unrelated class.
        var result = mPluginActionManager.checkAndDisable(Activity::class.java.name)
        assertFalse(result)
        verify(mMockEnabler, never()).setDisabled(any(), any())

        // Now hand it a real class and make sure it disables the plugin.
        result = mPluginActionManager.checkAndDisable(TestPlugin::class.java.name)
        assertTrue(result)
        verify(mMockEnabler)
            .setDisabled(mTestPluginComponentName, DisableReason.DISABLED_FROM_EXPLICIT_CRASH)
    }

    @Test
    @Throws(Exception::class)
    fun testDisableAll() {
        createPlugin() // Get into valid created state.

        mPluginActionManager.disableAll()

        verify(mMockEnabler)
            .setDisabled(mTestPluginComponentName, DisableReason.DISABLED_FROM_SYSTEM_CRASH)
    }

    @Test
    @Throws(Exception::class)
    fun testDisablePrivileged() {
        val factory =
            PluginActionManager.Factory(
                context,
                mMockPm,
                mFakeExecutor,
                mFakeExecutor,
                mNotificationManager,
                mMockEnabler,
                PluginManager.Config(listOf(PRIVILEGED_PACKAGE)),
                mPluginInstanceFactory,
            )
        mPluginActionManager =
            factory.create("myAction", mMockListener, TestPlugin::class.java, allowMultiple = true)

        createPlugin() // Get into valid created state.

        mPluginActionManager.disableAll()

        verify(mMockPm, never())
            .setComponentEnabledSetting(
                argumentCaptor<ComponentName>().capture(),
                argumentCaptor<Int>().capture(),
                argumentCaptor<Int>().capture(),
            )
    }

    @Throws(Exception::class)
    private fun setupFakePmQuery() {
        val list: MutableList<ResolveInfo> = ArrayList()
        val info = ResolveInfo()
        info.serviceInfo = mock()
        info.serviceInfo.packageName = mTestPluginComponentName.packageName
        info.serviceInfo.name = mTestPluginComponentName.className
        whenever(info.serviceInfo.loadLabel(any())).thenReturn("Test Plugin")
        list.add(info)
        whenever(mMockPm.queryIntentServices(any(), anyInt())).thenReturn(list)
        whenever(mMockPm.getServiceInfo(any(), anyInt())).thenReturn(info.serviceInfo)

        whenever(mMockPm.checkPermission(anyString(), anyString()))
            .thenReturn(PackageManager.PERMISSION_GRANTED)

        whenever(mMockPm.getApplicationInfo(anyString(), anyInt()))
            .thenAnswer(
                Answer { invocation: InvocationOnMock ->
                    val appInfo = context.applicationInfo
                    appInfo.packageName = invocation.getArgument(0)
                    appInfo
                }
                    as Answer<ApplicationInfo>
            )
        whenever(mMockEnabler.isEnabled(mTestPluginComponentName)).thenReturn(true)
    }

    @Throws(Exception::class)
    private fun createPlugin() {
        setupFakePmQuery()

        mPluginActionManager.loadAll()

        mFakeExecutor.runAllReady()
    }

    // Real context with no registering/unregistering of receivers.
    private class MyContextWrapper(base: Context?) : SysuiTestableContext(base) {
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? {
            return null
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver) {}

        override fun sendBroadcast(intent: Intent) {
            // Do nothing.
        }
    }

    // This target class doesn't matter, it just needs to have a Requires to hit the flow where
    // the mock version info is called.
    @Requires(target = PluginManagerTest::class, version = 1)
    class TestPlugin : Plugin {
        override fun getVersion(): Int {
            return 1
        }

        override fun onCreate(sysuiContext: Context, pluginContext: Context) {}

        override fun onDestroy() {}
    }

    companion object {
        private const val PRIVILEGED_PACKAGE = "com.android.systemui.shared.plugins"
    }
}
