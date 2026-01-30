/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.shared.plugins

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.messages.nano.SystemMessageProto
import com.android.systemui.SysuiTestCase
import com.android.systemui.plugins.Plugin
import com.android.systemui.plugins.PluginListener
import com.android.systemui.plugins.PluginManager
import com.android.systemui.plugins.annotations.ProvidesInterface
import com.android.systemui.shared.system.UncaughtExceptionPreHandlerManager
import java.lang.Thread.UncaughtExceptionHandler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class PluginManagerTest : SysuiTestCase() {
    @Mock lateinit var mMockFactory: PluginActionManager.Factory
    @Mock lateinit var mMockPluginInstance: PluginActionManager<TestPlugin>
    @Mock lateinit var mMockListener: PluginListener<TestPlugin>
    @Mock lateinit var mMockPackageManager: PackageManager
    @Mock lateinit var mMockPluginEnabler: PluginEnabler
    @Mock lateinit var mMockPluginPrefs: PluginPrefs
    @Mock lateinit var mMockExPreHandlerManager: UncaughtExceptionPreHandlerManager

    private lateinit var mPluginManager: PluginManagerImpl
    private lateinit var mPluginExceptionHandler: UncaughtExceptionHandler

    @Before
    @Throws(Exception::class)
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(mMockFactory.create(anyString(), any(), eq(TestPlugin::class.java), anyBoolean()))
            .thenReturn(mMockPluginInstance)

        mPluginManager =
            PluginManagerImpl(
                context,
                mMockFactory,
                mMockExPreHandlerManager,
                mMockPluginEnabler,
                mMockPluginPrefs,
                PluginManager.Config(),
            )
        captureExceptionHandler()
    }

    @Test
    fun testAddListener() {
        mPluginManager.addPluginListener("myAction", mMockListener, TestPlugin::class.java)

        verify(mMockPluginInstance).loadAll()
    }

    @Test
    fun testRemoveListener() {
        mPluginManager.addPluginListener("myAction", mMockListener, TestPlugin::class.java)
        mPluginManager.removePluginListener(mMockListener)

        verify(mMockPluginInstance).destroy()
    }

    @Test
    @RunWithLooper(setAsMainLooper = true)
    fun testNonDebuggable_nonPrivileged() {
        mPluginManager =
            PluginManagerImpl(
                context,
                mMockFactory,
                mMockExPreHandlerManager,
                mMockPluginEnabler,
                mMockPluginPrefs,
                PluginManager.Config(),
            )
        captureExceptionHandler()

        val sourceDir = "myPlugin"
        val applicationInfo = ApplicationInfo()
        applicationInfo.sourceDir = sourceDir
        applicationInfo.packageName = PRIVILEGED_PACKAGE
        mPluginManager.addPluginListener("myAction", mMockListener, TestPlugin::class.java)

        verify(mMockFactory)
            .create(eq("myAction"), eq(mMockListener), eq(TestPlugin::class.java), eq(false))
        verify(mMockPluginInstance).loadAll()
    }

    @Test
    @RunWithLooper(setAsMainLooper = true)
    fun testNonDebuggable_privilegedPackage() {
        mPluginManager =
            PluginManagerImpl(
                context,
                mMockFactory,
                mMockExPreHandlerManager,
                mMockPluginEnabler,
                mMockPluginPrefs,
                PluginManager.Config(listOf(PRIVILEGED_PACKAGE)),
            )
        captureExceptionHandler()

        val sourceDir = "myPlugin"
        val privilegedApplicationInfo = ApplicationInfo()
        privilegedApplicationInfo.sourceDir = sourceDir
        privilegedApplicationInfo.packageName = PRIVILEGED_PACKAGE
        val invalidApplicationInfo = ApplicationInfo()
        invalidApplicationInfo.sourceDir = sourceDir
        invalidApplicationInfo.packageName = "com.android.invalidpackage"
        mPluginManager.addPluginListener("myAction", mMockListener, TestPlugin::class.java)

        verify(mMockFactory)
            .create(eq("myAction"), eq(mMockListener), eq(TestPlugin::class.java), eq(false))
        verify(mMockPluginInstance).loadAll()
    }

    @Test
    fun testExceptionHandler_foundPlugin() {
        mPluginManager.addPluginListener("myAction", mMockListener, TestPlugin::class.java)
        whenever(mMockPluginInstance.checkAndDisable(any())).thenReturn(true)

        mPluginExceptionHandler.uncaughtException(Thread.currentThread(), Throwable())

        verify(mMockPluginInstance, atLeastOnce())
            .checkAndDisable(argumentCaptor<String>().capture())
        verify(mMockPluginInstance, never()).disableAll()
    }

    @Test
    fun testExceptionHandler_noFoundPlugin() {
        mPluginManager.addPluginListener("myAction", mMockListener, TestPlugin::class.java)
        whenever(mMockPluginInstance.checkAndDisable(any())).thenReturn(false)

        mPluginExceptionHandler.uncaughtException(Thread.currentThread(), Throwable())

        verify(mMockPluginInstance, atLeastOnce())
            .checkAndDisable(argumentCaptor<String>().capture())
        verify(mMockPluginInstance).disableAll()
    }

    @Test
    fun testDisableIntent() {
        val nm = mock<NotificationManager>()
        mContext.addMockSystemService(Context.NOTIFICATION_SERVICE, nm)
        mContext.setMockPackageManager(mMockPackageManager)

        val testComponent = ComponentName(context.packageName, PluginManagerTest::class.java.name)
        val intent = Intent(PluginManagerImpl.DISABLE_PLUGIN)
        intent.setData(Uri.parse("package://" + testComponent.flattenToString()))
        mPluginManager.onReceive(mContext, intent)

        verify(nm)
            .cancel(eq(testComponent.className), eq(SystemMessageProto.SystemMessage.NOTE_PLUGIN))
        verify(mMockPluginEnabler)
            .setDisabled(testComponent, PluginEnabler.DisableReason.DISABLED_INVALID_VERSION)
    }

    private fun captureExceptionHandler() {
        val captor = argumentCaptor<UncaughtExceptionHandler>()
        verify(mMockExPreHandlerManager, atLeastOnce()).registerHandler(captor.capture())
        mPluginExceptionHandler = captor.lastValue
    }

    @ProvidesInterface(action = TestPlugin.ACTION, version = TestPlugin.VERSION)
    interface TestPlugin : Plugin {
        companion object {
            const val ACTION: String = "testAction"
            const val VERSION: Int = 1
        }
    }

    companion object {
        private const val PRIVILEGED_PACKAGE = "com.android.systemui"
    }
}
