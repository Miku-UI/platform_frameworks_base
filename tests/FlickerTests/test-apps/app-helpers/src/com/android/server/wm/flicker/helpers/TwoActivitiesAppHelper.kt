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
import android.tools.traces.parsers.toFlickerComponent
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.testapp.ActivityOptions.LaunchNewActivity
import com.android.server.wm.flicker.testapp.ActivityOptions.SimpleActivity

/**
 * App helper for [LaunchNewActivity], which contains a button to launch a second activity.
 */
class TwoActivitiesAppHelper
@JvmOverloads
constructor(
    instr: Instrumentation,
    launcherName: String = LaunchNewActivity.LABEL,
    component: ComponentNameMatcher = LaunchNewActivity.COMPONENT.toFlickerComponent(),
) : StandardAppHelper(instr, launcherName, component) {

    private val secondActivityComponent = SimpleActivity.COMPONENT.toFlickerComponent()

    /** Opens the second activity and waits for it to become fullscreen. */
    fun openSecondActivity(device: UiDevice, wmHelper: WindowManagerStateHelper) {
        clickButtonAndWaitForSync(
            device = device,
            wmHelper = wmHelper,
            buttonResId = LaunchNewActivity.RES_ID_LAUNCH_SECOND_ACTIVITY_BUTTON,
        ) { withFullScreenApp(secondActivityComponent) }
    }
}
