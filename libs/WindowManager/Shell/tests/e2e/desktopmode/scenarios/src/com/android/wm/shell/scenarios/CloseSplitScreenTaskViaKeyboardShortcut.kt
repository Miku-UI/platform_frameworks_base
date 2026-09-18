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
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_META_RIGHT
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.ImmersiveAppHelper
import com.android.server.wm.flicker.helpers.KeyEventHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags.Flags
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.After
import org.junit.Test

/** Base scenario test for closing a split screen task via the keyboard shortcut. */
@RequiresFlagsEnabled(
    Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
    Flags.FLAG_CLOSE_FULLSCREEN_AND_SPLITSCREEN_KEYBOARD_SHORTCUT,
)
abstract class CloseSplitScreenTaskViaKeyboardShortcut : TestScenarioBase() {
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(getInstrumentation())
    private val device = UiDevice.getInstance(getInstrumentation())
    private val primaryApp = DesktopModeAppHelper(SimpleAppHelper(getInstrumentation(), "primary"))
    private val immersiveAppHelper = ImmersiveAppHelper(getInstrumentation())
    private val secondaryApp = DesktopModeAppHelper(immersiveAppHelper)
    private val keyEventHelper = KeyEventHelper(getInstrumentation())

    @Test
    open fun closeTaskViaKeyboardShortcut() {
        primaryApp.enterDesktopMode(wmHelper, device)

        // Enter split screen
        primaryApp.exitDesktopModeToSplitScreenWithDesktopLayoutMenu(wmHelper, device)
        // Open allApps via keyboard shortcut
        keyEventHelper.press(KEYCODE_META_RIGHT)
        tapl.allApps.getAppIcon(immersiveAppHelper.appName).launch(immersiveAppHelper.packageName)
        SplitScreenUtils.waitForSplitComplete(wmHelper, primaryApp, secondaryApp)

        // Focus on the primary app.
        primaryApp.bringToFront(wmHelper, device)

        keyEventHelper.press(KeyEvent.KEYCODE_W, KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON)
        wmHelper.StateSyncBuilder().withWindowSurfaceDisappeared(primaryApp).waitForAndVerify()

        // Focus on the secondary app.
        secondaryApp.bringToFront(wmHelper, device)

        keyEventHelper.press(KeyEvent.KEYCODE_W, KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON)
        wmHelper.StateSyncBuilder().withWindowSurfaceDisappeared(secondaryApp).waitForAndVerify()
    }

    @After
    fun teardown() {
        primaryApp.exit(wmHelper)
        secondaryApp.exit(wmHelper)
    }
}
