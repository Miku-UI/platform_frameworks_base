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

import android.app.Instrumentation
import android.tools.Rotation
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.ActivityEmbeddingAppHelper
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.ImmersiveAppHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.server.wm.flicker.helpers.NonResizeableAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Base scenario to test invoke Overview and select "Clear All" after opening 3 apps in Desktop Mode
 * and 2 full screen apps.
 */
@Ignore("Test Base Class")
abstract class ClearOpenAppsFromOverview(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    val openedApp: List<DesktopModeAppHelper> =
        listOf(
                NonResizeableAppHelper(instrumentation),
                MailAppHelper(instrumentation),
                SimpleAppHelper(instrumentation),
                ActivityEmbeddingAppHelper(instrumentation),
                ImmersiveAppHelper(instrumentation),
            )
            .map { DesktopModeAppHelper(it) }

    @Before
    fun setup() {
        openedApp.forEachIndexed { index, app ->
            if (index < 2) {
                app.launchViaIntent(wmHelper)
            } else {
                app.enterDesktopMode(wmHelper, device)
            }
        }
    }

    @Test
    open fun clearAllAppsFromOverview() {
        tapl.launchedAppState.switchToOverview().dismissAllTasks()
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withHomeActivityVisible()
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        openedApp.reversed().forEach { desktopApp -> desktopApp.exit(wmHelper) }
    }
}
