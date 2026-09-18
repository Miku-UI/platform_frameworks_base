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

package com.android.wm.shell.scenarios

import android.app.Instrumentation
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags.Flags
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
@RequiresFlagsEnabled(Flags.FLAG_MOVE_TASK_TO_FRONT_ON_DRAG_RESIZING_BUGFIX)
abstract class FocusAppByDragResize : TestScenarioBase() {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val mailApp = DesktopModeAppHelper(MailAppHelper(instrumentation))
    private val simpleApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))

    @Before
    fun setup() {
        mailApp.enterDesktopMode(wmHelper, device)
        simpleApp.launchViaIntent(wmHelper)

        // Snap Simple App to the left to expose Mail App's right top corner
        simpleApp.dragToSnapResizeRegion(wmHelper, device, instrumentation.context, isLeft = true)

        // Verify Simple App is focused
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withTopVisibleApp(simpleApp)
            .waitForAndVerify()
    }

    @Test
    open fun resizeAppWithCornerResizeToFocus() {
        // Resize Mail App (which is behind)
        // We use corner resize on the top right corner
        mailApp.cornerResize(
            wmHelper,
            device,
            DesktopModeAppHelper.Corners.RIGHT_TOP,
            horizontalChange = 50,
            verticalChange = -50,
        )

        // Verify Mail App comes to front/focus
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withTopVisibleApp(mailApp)
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        simpleApp.exit(wmHelper)
        mailApp.exit(wmHelper)
    }
}
