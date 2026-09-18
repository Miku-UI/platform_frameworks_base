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

import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.ImmersiveAppHelper
import com.android.server.wm.flicker.helpers.KeyEventHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Base scenario test for toggling fullscreen status via the fullscreen key. */
abstract class ToggleFullscreenViaFullscreenKey : TestScenarioBase() {
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(getInstrumentation())
    private val device = UiDevice.getInstance(getInstrumentation())
    private val immersiveAppHelper = ImmersiveAppHelper(getInstrumentation())
    private val immersiveApp = DesktopModeAppHelper(immersiveAppHelper)
    private val simpleApp = DesktopModeAppHelper(SimpleAppHelper(getInstrumentation()))
    private val keyEventHelper = KeyEventHelper(getInstrumentation())

    @Before
    fun setup() {
        immersiveApp.enterDesktopMode(wmHelper, device, isImmersiveApp = true)
        simpleApp.enterDesktopMode(wmHelper, device, isImmersiveApp = false)

        tapl.showTaskbarIfHidden()
    }

    @Test
    open fun toggleBetweenDesktopAndFullscreen() {
        // Focus on the immersive app.
        immersiveApp.bringToFront(wmHelper, device)

        // Desktop -> Fullscreen.
        keyEventHelper.press(KeyEvent.KEYCODE_FULLSCREEN)
        immersiveApp.waitForTransitionToFullscreen(wmHelper)

        // Fullscreen -> Desktop.
        keyEventHelper.press(KeyEvent.KEYCODE_FULLSCREEN)
        immersiveApp.waitForTransitionToFreeform(wmHelper)

        // Desktop -> Fullscreen.
        keyEventHelper.press(KeyEvent.KEYCODE_FULLSCREEN)
        immersiveApp.waitForTransitionToFullscreen(wmHelper)

        // Fullscreen -> Desktop.
        keyEventHelper.press(KeyEvent.KEYCODE_FULLSCREEN)
        immersiveApp.waitForTransitionToFreeform(wmHelper)
    }

    @Test
    open fun moveSplitScreenToFullscreen() {
        // Enter split screen mode.
        simpleApp.exitDesktopModeToSplitScreenWithDesktopLayoutMenu(wmHelper, device)
        tapl.launchedAppState.taskbar
            .getAppIcon(immersiveAppHelper.appName)
            .launch(immersiveApp.packageName)
        SplitScreenUtils.waitForSplitComplete(wmHelper, simpleApp, immersiveApp)

        // Ensure that the immersive app is in focus.
        immersiveApp.bringToFront(wmHelper, device)

        // Split screen -> Fullscreen.
        keyEventHelper.press(KeyEvent.KEYCODE_FULLSCREEN)
        immersiveApp.waitForTransitionToFullscreen(wmHelper)
    }

    @After
    fun teardown() {
        immersiveApp.exit(wmHelper)
        simpleApp.exit(wmHelper)
    }
}
