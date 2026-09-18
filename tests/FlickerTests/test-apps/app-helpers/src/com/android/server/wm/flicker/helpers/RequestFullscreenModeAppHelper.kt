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

package com.android.server.wm.flicker.helpers

import android.app.Instrumentation
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.traces.parsers.toFlickerComponent
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.uiautomator.By
import com.android.server.wm.flicker.testapp.ActivityOptions.RequestFullscreenModeActivity

/**
 * Helper class for tests that use [RequestFullscreenModeActivity].
 */
class RequestFullscreenModeAppHelper
@JvmOverloads
constructor(
    instr: Instrumentation,
    launcherName: String = RequestFullscreenModeActivity.LABEL,
    component: ComponentNameMatcher = RequestFullscreenModeActivity.COMPONENT.toFlickerComponent()
) : StandardAppHelper(instr, launcherName, component) {

    /** Click the enter fullscreen button and waits for the app to finish transitioning. */
    fun clickEnterFullscreenButton(wmHelper: WindowManagerStateHelper) {
        clickObject(ENTER_FULLSCREEN_BUTTON_ID)
        waitForTransitionToFullscreen(wmHelper)
    }

    /** Click the exit fullscreen button and waits for the app to finish transitioning. */
    fun clickExitFullscreenButton(wmHelper: WindowManagerStateHelper) {
        clickObject(EXIT_FULLSCREEN_BUTTON_ID)
        waitForTransitionFromFullscreen(wmHelper)
    }

    private fun clickObject(resId: String) {
        val selector = By.res(packageName, resId)
        val obj = uiDevice.findObject(selector)
            ?: error("Could not find `$resId` object")
        obj.click()
    }

    /** Wait for transition to full screen to finish. */
    private fun waitForTransitionToFullscreen(
        wmHelper: WindowManagerStateHelper,
        displayId: Int = DEFAULT_DISPLAY,
    ) {
        wmHelper
            .StateSyncBuilder()
            .withFullScreenApp(this, displayId)
            .withAppTransitionIdle(displayId)
            .waitForAndVerify()
    }

    /** Wait for transition from full screen to finish. */
    private fun waitForTransitionFromFullscreen(
        wmHelper: WindowManagerStateHelper,
        displayId: Int = DEFAULT_DISPLAY,
    ) {
        wmHelper
            .StateSyncBuilder()
            .withFullScreenAppGone(this, displayId)
            .withAppTransitionIdle(displayId)
            .waitForAndVerify()
    }

    private companion object {
        private const val ENTER_FULLSCREEN_BUTTON_ID = "enter_button"
        private const val EXIT_FULLSCREEN_BUTTON_ID = "exit_button"
    }
}
