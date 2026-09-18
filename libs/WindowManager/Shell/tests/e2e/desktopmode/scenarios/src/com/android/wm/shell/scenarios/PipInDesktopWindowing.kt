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
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.KeyEventHelper
import com.android.server.wm.flicker.helpers.PipAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.wm.shell.Flags
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/** Base scenario tests for PiP in Desktop Windowing */
@Ignore("Test Base Class")
abstract class PipInDesktopWindowing(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {
    private val instrumentation: Instrumentation = getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val simpleApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    private val pipApp = PipAppHelper(instrumentation)
    private val keyEventHelper = KeyEventHelper(getInstrumentation())
    private val pipAppDesktopMode = DesktopModeAppHelper(pipApp)

    @Before
    fun setup() {
        Assume.assumeTrue(Flags.enablePip2())

        simpleApp.enterDesktopMode(wmHelper, device)
        pipAppDesktopMode.enterDesktopMode(wmHelper, device)
        pipApp.enableAutoEnterForPipActivity()
        pipAppDesktopMode.minimizeDesktopApp(wmHelper, device, isPip = true)
    }

    @Test
    open fun switchBetweenDesktopAndFullscreen() {
        // Desktop -> Fullscreen.
        keyEventHelper.press(KeyEvent.KEYCODE_FULLSCREEN)
        simpleApp.waitForTransitionToFullscreen(wmHelper)
        wmHelper.StateSyncBuilder().withAppTransitionIdle().withPipShown().waitForAndVerify()

        // Fullscreen -> Desktop.
        keyEventHelper.press(KeyEvent.KEYCODE_FULLSCREEN)
        simpleApp.waitForTransitionToFreeform(wmHelper)
        wmHelper.StateSyncBuilder().withAppTransitionIdle().withPipShown().waitForAndVerify()
    }

    @Test
    open fun expandPip() {
        // verify simpleApp is still in freeform
        simpleApp.waitForTransitionToFreeform(wmHelper)
        // expand the PiP and verify it goes to freeform
        pipApp.expandPipWindowToFreeformApp(wmHelper)
    }

    @Test
    open fun dragCornerToResizePip() {
        pipAppDesktopMode.cornerResize(
            wmHelper,
            device,
            DesktopModeAppHelper.Corners.LEFT_TOP,
            -200,
            -200,
        )
    }

    @After
    fun teardown() {
        pipApp.exit(wmHelper)
        simpleApp.exit(wmHelper)
    }
}
