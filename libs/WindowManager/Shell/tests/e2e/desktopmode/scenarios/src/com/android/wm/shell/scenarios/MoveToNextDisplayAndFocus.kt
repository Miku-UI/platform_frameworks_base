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

import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.traces.ConditionsFactory
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.Display.DEFAULT_DISPLAY
import android.view.KeyEvent.KEYCODE_MINUS
import android.view.KeyEvent.META_META_ON
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.KeyEventHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags.Flags
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import platform.test.desktop.SimulatedConnectedDisplayTestRule

/** Base scenario test to test if the window moved to next display via keyboard keeps the focus. */
@Ignore("Test Base Class")
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
abstract class MoveToNextDisplayAndFocus() : TestScenarioBase() {
    private val wmHelper = WindowManagerStateHelper(getInstrumentation())
    private val device = UiDevice.getInstance(getInstrumentation())

    private val testAppInMainDisplay = DesktopModeAppHelper(SimpleAppHelper(getInstrumentation()))
    private val testAppInExternalDisplay = DesktopModeAppHelper(MailAppHelper(getInstrumentation()))
    private val keyEventHelper = KeyEventHelper(getInstrumentation())

    @get:Rule(order = 0) val connectedDisplayRule = SimulatedConnectedDisplayTestRule()

    @Before
    fun setup() {
        val displayId = connectedDisplayRule.setupTestDisplay()
        wmHelper.StateSyncBuilder().withDesktopModeOnDisplay(displayId).waitForAndVerify()
        testAppInMainDisplay.enterDesktopMode(wmHelper, device)
        testAppInExternalDisplay.launchViaIntent(wmHelper)
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withTopVisibleApps(testAppInExternalDisplay, testAppInMainDisplay)
            .waitForAndVerify()
    }

    @Test
    open fun moveToNextDisplayAndFocus() {
        val externalDisplayId = connectedDisplayRule.addedDisplays.first()
        testAppInExternalDisplay.moveToNextDisplayViaKeyboard(wmHelper, externalDisplayId)

        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withAppTransitionIdle(externalDisplayId)
            .add(ConditionsFactory.isWindowVisible(testAppInMainDisplay, DEFAULT_DISPLAY))
            .add(ConditionsFactory.isWindowVisible(testAppInExternalDisplay, externalDisplayId))
            .waitForAndVerify()

        // Send minimize via keyboard and observe window to check display focus.
        keyEventHelper.press(KEYCODE_MINUS, META_META_ON)

        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withAppTransitionIdle(externalDisplayId)
            .add(ConditionsFactory.isWindowVisible(testAppInMainDisplay, DEFAULT_DISPLAY))
            .add(
                ConditionsFactory.isWindowVisible(testAppInExternalDisplay, externalDisplayId)
                    .negate()
            )
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        testAppInMainDisplay.exit(wmHelper)
        testAppInExternalDisplay.exit(wmHelper)
    }
}
