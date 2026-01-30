/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.server.wm.flicker.testapp.ActivityOptions.LaunchNewTask

/**
 * App helper for [LaunchNewTask], which contains buttons to launch activities into new tasks using
 * different intent flags.
 */
class NewTasksAppHelper
@JvmOverloads
constructor(
    instr: Instrumentation,
    launcherName: String = LaunchNewTask.LABEL,
    component: ComponentNameMatcher = LaunchNewTask.COMPONENT.toFlickerComponent(),
) : StandardAppHelper(instr, launcherName, component) {

    /**
     * Launches a new, separate task and waits for the transition to idle.
     *
     * Clicks the button in the test app that launches an activity with
     * `FLAG_ACTIVITY_NEW_TASK` and `FLAG_ACTIVITY_MULTIPLE_TASK`.
     *
     * @param device The [UiDevice] instance to interact with the app.
     * @param wmHelper The [WindowManagerStateHelper] for state verification.
     * @param withSyncCondition extension function to configure the state synchronization condition.
     */
    fun openNewTask(
        device: UiDevice,
        wmHelper: WindowManagerStateHelper,
        withSyncCondition: StateSyncBuilder.() -> StateSyncBuilder = { withAppTransitionIdle() },
    ) {
        clickButtonAndWaitForSync(
            device = device,
            wmHelper = wmHelper,
            buttonResId = LaunchNewTask.RES_ID_NEW_MULTIPLE_TASK_BUTTON,
            withSyncCondition = withSyncCondition,
        )
    }

    /**
     * Launches a task, recycling an existing one if possible, and waits for the transition to idle.
     *
     * Clicks the button in the test app that launches an activity with
     * `FLAG_ACTIVITY_CLEAR_TOP` and `FLAG_ACTIVITY_NEW_TASK`.
     *
     * @param device The [UiDevice] instance to interact with the app.
     * @param wmHelper The [WindowManagerStateHelper] for state verification.
     * @param withSyncCondition extension function to configure the state synchronization condition.
     */
    fun openNewTaskWithRecycle(
        device: UiDevice,
        wmHelper: WindowManagerStateHelper,
        withSyncCondition: StateSyncBuilder.() -> StateSyncBuilder = { withAppTransitionIdle() },
    ) {
        clickButtonAndWaitForSync(
            device = device,
            wmHelper = wmHelper,
            buttonResId = LaunchNewTask.RES_ID_NEW_TASK_RECYCLE_IF_POSSIBLE_BUTTON,
            withSyncCondition = withSyncCondition,
        )
    }
}
