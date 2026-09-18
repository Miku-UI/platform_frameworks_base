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

package com.android.wm.shell.scenarios

import android.platform.uiautomatorhelpers.DeviceHelpers.waitForObj
import android.provider.Settings
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/** Base scenario test to set toggle mirroring switch via Connected Display Settings */
@Ignore("Test Base Class")
abstract class SettingsToggleMirroringSwitch : SettingsConnectedDisplayTestBase() {

    @Before
    fun setup() {
        // PeripheralDeviceRule monitoring ensures that launched displays keeps the same
        // DisplayTopology after. Stop monitoring since DisplayTopology will change on mirroring
        peripheralDeviceRule.stopMonitoring()
    }

    @Test
    open fun enableMirrorBuiltInDisplaySwitch() {
        Settings.Secure.putInt(instrumentation.context.contentResolver, MIRROR_SETTING, 0)
        selectDisplay(DEFAULT_DISPLAY)

        getMirroringPreference().click()

        // As display is just mirroring the main display, the display itself doesn't have UI
        // components
        wmHelper.StateSyncBuilder().withEmptyDisplay(addedDisplayId).waitForAndVerify()
    }

    @Test
    open fun disableMirrorBuiltInDisplaySwitch() {
        Settings.Secure.putInt(instrumentation.context.contentResolver, MIRROR_SETTING, 1)
        selectDisplay(DEFAULT_DISPLAY)

        getMirroringPreference().click()

        // Once display stops mirroring, Desktop UI components should be visible again
        wmHelper.StateSyncBuilder().withDesktopModeOnDisplay(addedDisplayId).waitForAndVerify()
    }

    @After
    fun teardown() {
        // Ensure the mirroring switch is disabled after running the test.
        Settings.Secure.putInt(instrumentation.context.contentResolver, MIRROR_SETTING, 0)
    }

    private fun getMirroringPreference() =
        waitForObj(By.res(MIRROR_BUILT_IN_DISPLAY_SWITCH_ID)) {
            "Could not find the 'Mirroring' preference, have `selectDisplay(displayId)` been " +
                "called and display is DEFAULT_DISPLAY?"
        }

    private companion object {
        const val MIRROR_BUILT_IN_DISPLAY_SWITCH_ID = "com.android.settings:id/switchWidget"
        const val MIRROR_SETTING = Settings.Secure.MIRROR_BUILT_IN_DISPLAY
    }
}
