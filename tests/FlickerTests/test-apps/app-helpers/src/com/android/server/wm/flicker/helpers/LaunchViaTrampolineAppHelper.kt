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
import android.content.Intent
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.component.IComponentNameMatcher
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.traces.parsers.toFlickerComponent
import com.android.server.wm.flicker.testapp.ActivityOptions

/**
 * App helper that uses the [trampolineApp] to launch the target app.
 *
 * [trampolineApp] must launch the app specified via [appName] and [component].
 */
class LaunchViaTrampolineAppHelper(
    instr: Instrumentation,
    appName: String = ActivityOptions.TrampolineFinishActivity.LABEL,
    component: IComponentNameMatcher =
        ActivityOptions.TrampolineFinishActivity.COMPONENT.toFlickerComponent(),
    private val trampolineApp: StandardAppHelper,
) : StandardAppHelper(instr, appName, component) {

    override fun launchViaIntent(
        wmHelper: WindowManagerStateHelper,
        launchedAppComponentMatcherOverride: IComponentMatcher?,
        action: String?,
        stringExtras: Map<String, String>,
        waitConditionsBuilder: WindowManagerStateHelper.StateSyncBuilder,
        options: android.app.ActivityOptions?,
    ) {
        trampolineApp.launchViaIntent(
            wmHelper,
            launchedAppComponentMatcherOverride ?: this,
            action,
            stringExtras,
            waitConditionsBuilder,
            options,
        )
    }

    override fun launchViaIntent(
        expectedPackageName: String,
        action: String?,
        stringExtras: Map<String, String>,
        options: android.app.ActivityOptions?,
    ) {
        trampolineApp.launchViaIntent(expectedPackageName, action, stringExtras, options)
    }

    override fun launchViaIntent(
        wmHelper: WindowManagerStateHelper,
        intent: Intent,
        launchedAppComponentMatcherOverride: IComponentMatcher?,
        waitConditionsBuilder: WindowManagerStateHelper.StateSyncBuilder,
        options: android.app.ActivityOptions?,
    ) {
        trampolineApp.launchViaIntent(
            wmHelper,
            intent,
            launchedAppComponentMatcherOverride,
            waitConditionsBuilder,
            options,
        )
    }
}
