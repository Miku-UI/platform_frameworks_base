/*
 * Copyright 2026 The Android Open Source Project
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

import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.uiautomatorhelpers.DeviceHelpers.waitForObj
import android.platform.uiautomatorhelpers.WaitUtils
import android.view.Display.INVALID_DISPLAY
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import com.android.graphics.surfaceflinger.flags.Flags.FLAG_FOLLOWER_ARBITRARY_REFRESH_RATE_SELECTION_PLATFORM
import com.android.graphics.surfaceflinger.flags.Flags.FLAG_FOLLOWER_DISPLAY_BACKPRESSURE_PLATFORM
import com.android.graphics.surfaceflinger.flags.Flags.FLAG_FORCE_SLOWER_FOLLOWER_GPU_COMPOSITION_PLATFORM
import com.android.graphics.surfaceflinger.flags.Flags.FLAG_SYNCED_RESOLUTION_SWITCH
import com.android.wm.shell.Utils
import kotlin.math.abs
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import platform.test.desktop.DisplayMode
import platform.test.desktop.DisplaySize
import platform.test.desktop.SimulatedDisplayPeripheral

/** Base scenario test to set resolution and refresh rate via Connected Display Settings */
@Ignore("Test Base Class")
@RequiresFlagsEnabled(
    FLAG_FOLLOWER_ARBITRARY_REFRESH_RATE_SELECTION_PLATFORM,
    FLAG_FOLLOWER_DISPLAY_BACKPRESSURE_PLATFORM,
    FLAG_FORCE_SLOWER_FOLLOWER_GPU_COMPOSITION_PLATFORM,
    FLAG_SYNCED_RESOLUTION_SWITCH,
)
abstract class SettingsUpdateResolutionRefreshRate :
    SettingsConnectedDisplayTestBase(
        SimulatedDisplayPeripheral(listOf(MODE_1080P_60, MODE_1080P_59_5, MODE_2K_60))
    ) {

    @After
    fun tearDown() {
        if (addedDisplayId == INVALID_DISPLAY) {
            return
        }
        // Clear the user-preferred mode to reset the display state after the test
        instrumentation.uiAutomation.executeShellCommand(
            "cmd display clear-user-preferred-display-mode $addedDisplayId"
        )
    }

    @Test
    fun testSwitchResolution() {
        val pref = getResolutionAndRefreshRatePreference()
        // Validate text is showing the correct resolution and refresh rate
        assertNotNull(
            pref.findObject(By.text(INITIAL_MODE_STRING)),
            "Resolution and refresh rate is not set to $INITIAL_MODE_STRING",
        )
        pref.click()

        // Find and click the 2K resolution option
        waitForObj(By.text(MODE_2K_60.size.asString()), UIAUTOMATOR_TIMEOUT).click()

        waitForObj(By.res(APPLY_BUTTON_RES_ID), UIAUTOMATOR_TIMEOUT).click()
        waitForObj(By.text(CONFIRM_DIALOG_TEXT), UIAUTOMATOR_TIMEOUT).click()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        WaitUtils.ensureThat(
            errorProvider = {
                val display = displayManager.getDisplay(addedDisplayId)
                "Failed to update mode for Display#$addedDisplayId, current mode is: ${display.mode}"
            }
        ) {
            val display = displayManager.getDisplay(addedDisplayId)
            display.mode.physicalWidth == MODE_2K_60.size.width &&
                display.mode.physicalHeight == MODE_2K_60.size.height
        }
    }

    @Test
    fun testSwitchRefreshRate() {
        // Refresh rate change is disallowed if refreshRateSync config is set to true
        val isRefreshRateChangeAllowed =
            !Utils.getBooleanConfig(CONFIG_REFRESH_RATE_SYNCHRONIZATION_ENABLED)!!
        assumeTrue("Ignore test, refresh rate change is not allowed", isRefreshRateChangeAllowed)

        val pref = getResolutionAndRefreshRatePreference()
        // Validate text is showing the correct resolution and refresh rate
        assertNotNull(
            pref.findObject(By.text(INITIAL_MODE_STRING)),
            "Resolution and refresh rate is not set to $INITIAL_MODE_STRING",
        )
        pref.click()

        // Find and click the 59.5 Hz refresh rate
        waitForObj(By.text(toRefreshRateString(MODE_1080P_59_5.refreshRate)), UIAUTOMATOR_TIMEOUT)
            .click()

        waitForObj(By.res(APPLY_BUTTON_RES_ID), UIAUTOMATOR_TIMEOUT).click()
        waitForObj(By.text(CONFIRM_DIALOG_TEXT), UIAUTOMATOR_TIMEOUT).click()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        WaitUtils.ensureThat(
            errorProvider = {
                val display = displayManager.getDisplay(addedDisplayId)
                "Failed to update mode for Display#$addedDisplayId, current mode is: ${display.mode}"
            }
        ) {
            val display = displayManager.getDisplay(addedDisplayId)
            abs(display.mode.refreshRate - MODE_1080P_59_5.refreshRate) < 0.01f
        }
    }

    private fun getResolutionAndRefreshRatePreference(): UiObject2 {
        return waitForObj(
            By.clazz("android.widget.RelativeLayout")
                .hasDescendant(By.text(RESOLUTION_REFRESH_RATE_TEXT))
        ) {
            "Could not find the $RESOLUTION_REFRESH_RATE_TEXT preference, have" +
                "`selectDisplay(displayId)` been  called and display is external display?"
        }
    }

    companion object {
        fun DisplaySize.asString(): String = "$width x $height"

        fun DisplayMode.asString(): String =
            "${size.asString()} (${toRefreshRateString(refreshRate)})"

        private fun toRefreshRateString(refreshRate: Float): String {
            return "${"%.2f".format(refreshRate)} Hz"
        }

        const val APPLY_BUTTON_RES_ID =
            "com.android.settings:id/resolution_change_apply_button_view"
        const val CONFIG_REFRESH_RATE_SYNCHRONIZATION_ENABLED =
            "config_refreshRateSynchronizationEnabled"
        const val CONFIRM_DIALOG_TEXT = "Use settings"
        const val RESOLUTION_REFRESH_RATE_TEXT = "Resolution and refresh rate"

        val MODE_1080P_60 = DisplayMode(DisplaySize.SIZE_1080P, 60f)
        val MODE_1080P_59_5 = DisplayMode(DisplaySize.SIZE_1080P, 59.5f)
        val MODE_2K_60 = DisplayMode(DisplaySize.SIZE_2K, 60f)

        val INITIAL_MODE_STRING = MODE_1080P_60.asString()
    }
}
