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
package com.android.resources

import android.platform.test.annotations.RequiresReadWriteFeatureFlags

import com.android.tradefed.device.DeviceNotAvailableException
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(DeviceJUnit4ClassRunner::class)
class OverlayFlaggingHostTest : BaseHostJUnit4Test() {
    companion object {
        private const val WITH_OVERLAYS_PACKAGE =
            "com.android.resources.targetappwithoverlayablexml"
        private const val WITHOUT_OVERLAYS_PACKAGE =
            "com.android.resources.targetappwithoutoverlayablexml"
        private const val OVERLAY_WITH_OVERLAYS_PACKAGE =
            "com.android.resources.overlayappforoverlayablexml"
        private const val OVERLAY_WITHOUT_OVERLAYS_PACKAGE =
            "com.android.resources.overlayappwithoutoverlayablexml"
        private const val FLAG_NAME = "android.content.res.test_flag_1"
    }

    @Before
    @kotlin.Throws(Exception::class)
    fun setup() {
        enableOverlays()
        setFlag(false)
    }

    @After
    @kotlin.Throws(Exception::class)
    fun teardown() {
        getDevice().executeShellCommand("aflags unset $FLAG_NAME --immediate")
    }

    @Test
    @RequiresReadWriteFeatureFlags
    @kotlin.Throws(Exception::class)
    fun testWithOverlaysXmlFlagDisabled() {
        assertTrue(runDeviceOverlaysTest(WITH_OVERLAYS_PACKAGE, "testWithOverlaysNotOverlaid"))
    }

    @Test
    @RequiresReadWriteFeatureFlags
    @kotlin.Throws(Exception::class)
    fun testWithOverlaysXmlFlagEnabled() {
        setFlag(true)
        assertTrue(runDeviceOverlaysTest(WITH_OVERLAYS_PACKAGE, "testWithOverlaysOverlaid"))
    }

    @Test
    @RequiresReadWriteFeatureFlags
    @kotlin.Throws(Exception::class)
    fun testWithoutOverlaysXmlFlagDisabled() {
        assertTrue(runDeviceOverlaysTest(WITHOUT_OVERLAYS_PACKAGE, "testWithoutOverlaysNotOverlaid"))
    }

    @Test
    @RequiresReadWriteFeatureFlags
    @kotlin.Throws(Exception::class)
    fun testWithoutOverlaysXmlFlagEnabled() {
        setFlag(true)
        assertTrue(runDeviceOverlaysTest(WITHOUT_OVERLAYS_PACKAGE, "testWithoutOverlaysOverlaid"))
    }

    @kotlin.Throws(Exception::class)
    private fun enableOverlays() {
        getDevice().executeShellCommand("cmd overlay enable $OVERLAY_WITH_OVERLAYS_PACKAGE")
        getDevice().executeShellCommand("cmd overlay enable $OVERLAY_WITHOUT_OVERLAYS_PACKAGE")
        var enableOutput1 =
            device.executeShellCommand("cmd overlay dump $OVERLAY_WITH_OVERLAYS_PACKAGE")
        assertTrue("overlay should be enabled: $enableOutput1",
            "mIsEnabled.............: true" in enableOutput1)
        var enableOutput2 =
            device.executeShellCommand("cmd overlay dump $OVERLAY_WITHOUT_OVERLAYS_PACKAGE")
        assertTrue("overlay should be enabled: $enableOutput2",
            "mIsEnabled.............: true" in enableOutput2)
    }

    @kotlin.Throws(Exception::class)
    private fun setFlag(enabled: Boolean) {
        val device = getDevice()

        val enabledStr = if (enabled) "enable" else "disable"
        if (enabledStr in device.executeShellCommand("aflags list | grep $FLAG_NAME")) {
            return
        }

        device.enableAdbRoot()
        assertTrue(device.isAdbRoot())
        // since we pass --immediate we don't need to reboot
        device.executeShellCommand("aflags $enabledStr $FLAG_NAME --immediate")
        assertTrue(enabledStr in device.executeShellCommand("aflags list | grep $FLAG_NAME"))
    }

    @kotlin.Throws(DeviceNotAvailableException::class)
    private fun runDeviceOverlaysTest(testPackage: String?, testName: String?): Boolean {
        return runDeviceTests(testPackage, "com.android.resources.targetapp.OverlayFlaggingDeviceTest", testName)
    }
}