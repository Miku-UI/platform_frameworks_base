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
package com.android.server.accessibility.integration

import android.app.Instrumentation
import android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
import android.graphics.Point
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SettingsStateChangerRule
import com.android.server.accessibility.Flags
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.desktop.DesktopMouseTestRule

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
@Ignore("b/438414507")
class AutoclickPanelTests {
    @Rule(order = 0)
    @JvmField
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Rule(order = 1)
    @JvmField
    val autoclickEnabledSettingRule: SettingsStateChangerRule =
        SettingsStateChangerRule(
            InstrumentationRegistry.getInstrumentation().context,
            Settings.Secure.ACCESSIBILITY_AUTOCLICK_ENABLED,
            "1"
        )

    @Rule(order = 2)
    @JvmField
    val desktopMouseTestRule = DesktopMouseTestRule()

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var uiDevice: UiDevice

    @Before
    fun setUp() {
        Configurator.getInstance().setUiAutomationFlags(FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        uiDevice = UiDevice.getInstance(instrumentation)

        initiateAutoclickPanel(
            InstrumentationRegistry.getInstrumentation().context, uiDevice, desktopMouseTestRule
        )
    }

    private fun clickPauseButton() {
        findObject(
            uiDevice,
            By.res(PAUSE_BUTTON_LAYOUT_ID)
        ).click()
    }

    private fun clickPositionButton() {
        findObject(uiDevice, By.res(POSITION_BUTTON_LAYOUT_ID)).click()
    }

    // The panel is considered open when every click type button is showing.
    private fun isAutoclickPanelOpen(): Boolean {
        val PANEL_OPEN_CLICK_TYPE_COUNT = 6
        val clickTypeButtonGroupContainer = findObject(
            uiDevice,
            By.res(CLICK_TYPE_BUTTON_GROUP_ID)
        )
        return clickTypeButtonGroupContainer.childCount == PANEL_OPEN_CLICK_TYPE_COUNT
    }

    private fun getAutoclickPanelPosition(): Point {
        return findObject(
            uiDevice,
            By.res(AUTOCLICK_PANEL_ID)
        ).visibleCenter
    }

    @Test
    fun togglePauseResumeButton_contentDescriptionReflectsTheState() {
        // Expect the panel to start with the pause button.
        assertNotNull(
            findObject(
                uiDevice,
                By.res(PAUSE_BUTTON_LAYOUT_ID).desc("Pause")
            )
        )

        // After clicking, verify it's changed to the resume button.
        clickPauseButton()
        assertNotNull(
            findObject(
                uiDevice,
                By.res(PAUSE_BUTTON_LAYOUT_ID).desc("Resume")
            )
        )

        // Click again and verify it's back to the pause button.
        clickPauseButton()
        assertNotNull(
            findObject(
                uiDevice,
                By.res(PAUSE_BUTTON_LAYOUT_ID).desc("Pause")
            )
        )
    }

    @Test
    fun switchClickType_LongPressClickTypeIsSelected() {
        // Click the long press button then verify only the long press button is visible with all
        // other click type buttons hidden.
        changeClickType(uiDevice, desktopMouseTestRule, LONG_PRESS_BUTTON_LAYOUT_ID)
        assertNotNull(
            findObject(uiDevice, By.res(LONG_PRESS_BUTTON_LAYOUT_ID))
        )
        assertFalse(isAutoclickPanelOpen())
    }

    @Test
    fun clickPositionButton_autoclickPanelMovesAroundTheScreen() {
        // Capture the position of the panel and confirm it moves after clicking the positio
        // button.
        val startingPosition = getAutoclickPanelPosition()
        clickPositionButton()
        waitAndAssert {
            startingPosition != getAutoclickPanelPosition()
        }

        val secondPosition = getAutoclickPanelPosition()
        clickPositionButton()
        waitAndAssert {
            secondPosition != getAutoclickPanelPosition()
        }

        val thirdPosition = getAutoclickPanelPosition()
        clickPositionButton()
        waitAndAssert {
            thirdPosition != getAutoclickPanelPosition()
        }

        val fourthPosition = getAutoclickPanelPosition()
        clickPositionButton()
        waitAndAssert {
            fourthPosition != getAutoclickPanelPosition()
        }

        // Confirm the panel moved around the screen and finished in the starting location.
        val fifthPosition = getAutoclickPanelPosition()
        assertEquals(startingPosition, fifthPosition)
    }

    private companion object {
        @AfterClass
        @JvmStatic
        fun teardownAfterClass() {
            // Wait for the Autoclick panel to be closed.
            waitAndAssert {
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .findObject(By.res(AUTOCLICK_PANEL_ID)) == null
            }
        }
    }
}
