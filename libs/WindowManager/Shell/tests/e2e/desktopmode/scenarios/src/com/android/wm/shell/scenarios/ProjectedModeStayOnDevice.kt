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

import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.tools.NavBar
import android.tools.PlatformConsts.DEFAULT_DISPLAY
import android.tools.Rotation
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.shared.desktopmode.DesktopState
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import platform.test.desktop.SimulatedConnectedDisplayTestRule

/**
 * Base scenario test for verifying that an app opened on device stays on device when an external
 * display is connected in projected mode.
 */
@Ignore("Test Base Class")
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
abstract class ProjectedModeStayOnDevice {
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(getInstrumentation())
    private val testApp = DesktopModeAppHelper(SimpleAppHelper(getInstrumentation()))

    @get:Rule(order = 0) val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule(order = 1)
    val testSetupRule = Utils.testSetupRuleFunctional(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)
    @get:Rule(order = 2) val connectedDisplayRule = SimulatedConnectedDisplayTestRule()

    @Before
    fun setup() {
        val desktopState = DesktopState.fromContext(getInstrumentation().context)
        Assume.assumeTrue(desktopState.canEnterDesktopMode)
        Assume.assumeFalse(desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY))
    }

    @Test
    open fun projectedModeStayOnDevice() {
        // Launch app on default display
        testApp.launchViaIntent(wmHelper)
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle(DEFAULT_DISPLAY)
            .add("${testApp.packageName} is on default display") { dump ->
                dump.wmState.getDisplay(DEFAULT_DISPLAY)?.containsActivity(testApp) == true
            }
            .add("${testApp.packageName} is fullscreen") { dump ->
                dump.wmState.getActivity(testApp)?.windowingMode == WINDOWING_MODE_FULLSCREEN
            }
            .waitForAndVerify()

        // Connect external display
        val externalDisplayId = connectedDisplayRule.setupTestDisplay()

        // Verify app stays on default display
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle(DEFAULT_DISPLAY)
            .withAppTransitionIdle(externalDisplayId)
            .add("${testApp.packageName} is still on default display") { dump ->
                dump.wmState.getDisplay(DEFAULT_DISPLAY)?.containsActivity(testApp) == true
            }
            .add("${testApp.packageName} is still fullscreen") { dump ->
                dump.wmState.getActivity(testApp)?.windowingMode == WINDOWING_MODE_FULLSCREEN
            }
            .add("${testApp.packageName} is NOT on external display") { dump ->
                dump.wmState.getDisplay(externalDisplayId)?.containsActivity(testApp) == false
            }
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
        connectedDisplayRule.cleanupTestDisplays()
    }
}
