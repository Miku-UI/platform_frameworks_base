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
import android.tools.NavBar
import android.tools.Rotation
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/** Base scenario test to launch a new instance from app window menu and select the new window. */
@Ignore("Test Base Class")
abstract class LaunchNewInstanceFromAppWindowMenu(
    val navigationMode: NavBar = NavBar.MODE_GESTURAL,
    val rotation: Rotation = Rotation.ROTATION_0,
) : TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    val browserApp = BrowserAppHelper(instrumentation)
    val browserDesktopAppHelper = DesktopModeAppHelper(browserApp)

    @Before
    fun setup() {
        browserApp.launchViaIntent(wmHelper)
        browserApp.closePopupsIfNeeded(device)
    }

    @Test
    open fun launchNewInstanceFromFullScreenAndEnterSplitScreen() {
        browserDesktopAppHelper.exitDesktopModeToFullScreenIfNeeded(wmHelper, device)
        browserApp.openThreeDotsMenu()
        browserApp.clickNewWindowInMenu()
        SplitScreenUtils.waitForSplitComplete(wmHelper, browserApp, browserApp)
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withTopVisibleApp(browserApp)
            .waitForAndVerify()
    }

    @Test
    open fun launchNewInstanceFromDesktopMode() {
        browserDesktopAppHelper.enterDesktopMode(wmHelper, device)
        browserDesktopAppHelper.clickNewWindowButton(wmHelper, device)
        wmHelper
            .StateSyncBuilder()
            .withFreeformApp(browserApp.componentMatcher)
            .withAppTransitionIdle()
            .withTopVisibleApp(browserDesktopAppHelper)
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        // The test launches new windows. We want to make sure to clear storage (and remove all
        // opened windows) to prevent hitting the Chrome window limit.
        browserApp.clearStorage()
        browserApp.exit(wmHelper)
    }
}
