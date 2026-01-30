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
import android.platform.uiautomatorhelpers.scrollUntilFound
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.helpers.FIND_TIMEOUT
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.parsers.toFlickerComponent
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.android.server.wm.flicker.testapp.ActivityOptions

/**
 * The helper to interact with `ScrollToFinishActivity`
 */
class ScrollToFinishHelper
@JvmOverloads
constructor(
    instr: Instrumentation,
    launcherName: String = ActivityOptions.ScrollToFinish.LABEL,
    component: ComponentNameMatcher = ActivityOptions.ScrollToFinish.COMPONENT.toFlickerComponent()
) : StandardAppHelper(instr, launcherName, component) {

    /**
     * Scrolls until the finish button is found and clicks the button.
     */
    fun scrollToFinish() {
        val rootActivityLayout = uiDevice.wait(
            Until.findObject(By.res(packageName, RES_ID_ROOT_ACTIVITY_LAYOUT)),
            FIND_TIMEOUT
        ) ?: error("Unable to find $RES_ID_ROOT_ACTIVITY_LAYOUT.")

        val finishButton = rootActivityLayout.scrollUntilFound(
            By.res(packageName, RES_ID_FINISH_BUTTON)
        ) ?: error("Unable to find $RES_ID_FINISH_BUTTON")
        finishButton.click()
    }

    companion object {
        private const val RES_ID_ROOT_ACTIVITY_LAYOUT = "root_activity_layout"
        private const val RES_ID_FINISH_BUTTON = "finish_button"
    }
}