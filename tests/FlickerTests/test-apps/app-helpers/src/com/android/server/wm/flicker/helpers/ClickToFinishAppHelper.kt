/*
 * Copyright (C) 2026 The Android Open Source Project
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
import android.tools.traces.parsers.WindowManagerStateHelper.StateSyncBuilder
import android.tools.traces.parsers.toFlickerComponent
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.testapp.ActivityOptions.ClickToFinishActivity

/**
 * App helper for [ClickToFinishActivity], which contains a button to finish itself.
 */
class ClickToFinishAppHelper
@JvmOverloads
constructor(
    instr: Instrumentation,
    launcherName: String = ClickToFinishActivity.LABEL,
    component: ComponentNameMatcher = ClickToFinishActivity.COMPONENT.toFlickerComponent(),
) : StandardAppHelper(instr, launcherName, component) {

    /**
     * Clicks the button in the test app to finish itself.
     *
     * @param device The [UiDevice] instance to interact with the app.
     * @param wmHelper The [WindowManagerStateHelper] for state verification.
     * @param withSyncCondition extension function to configure the state synchronization condition.
     */
    fun clickToFinish(
        device: UiDevice,
        wmHelper: WindowManagerStateHelper,
        withSyncCondition: StateSyncBuilder.() -> StateSyncBuilder = { withAppTransitionIdle() },
    ) {
        clickButtonAndWaitForSync(
            device = device,
            wmHelper = wmHelper,
            buttonResId = ClickToFinishActivity.RES_ID_FINISH_BUTTON,
            withSyncCondition = withSyncCondition,
        )
    }
}
