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
import android.platform.test.annotations.EnableFlags
import android.tools.Rotation
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.RequestFullscreenModeAppHelper
import com.android.window.flags.Flags
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * A test that places a task into desktop and then requests fullscreen mode enter and exit through
 * the Activity API.
 */
@Ignore("Test Base Class")
@EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
abstract class FullscreenModeRequestFromDesktopAndBack(
    val rotation: Rotation = Rotation.ROTATION_0
) : TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val fullscreenModeAppHelper = RequestFullscreenModeAppHelper(instrumentation)
    val testApp = DesktopModeAppHelper(fullscreenModeAppHelper)

    @Before
    fun setup() {
        // Launch app in order to enter desktop mode
        fullscreenModeAppHelper.launchViaIntent(wmHelper)
        testApp.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun fullscreenModeRequestFromDesktopAndBack() {
        fullscreenModeAppHelper.clickEnterFullscreenButton(wmHelper)
        testApp.waitForTransitionToFullscreen(wmHelper)

        fullscreenModeAppHelper.clickExitFullscreenButton(wmHelper)
        testApp.waitForTransitionToFreeform(wmHelper)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }
}
