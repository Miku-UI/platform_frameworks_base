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
import android.tools.device.apphelpers.CalculatorAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.KeyEvent.KEYCODE_META_RIGHT
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.KeyEventHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/** Base scenario test for switch between desktop app and split screen app from overview. */
@Ignore("Test Base Class")
abstract class SwitchBetweenDesktopAndSplitScreen(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val keyEventHelper = KeyEventHelper(InstrumentationRegistry.getInstrumentation())
    private val secondaryApp = CalculatorAppHelper(instrumentation)
    private val primaryApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    private val desktopApp = DesktopModeAppHelper(MailAppHelper(instrumentation))

    @Before
    fun setup() {
        // Launch app in order to enter split screen
        primaryApp.launchViaIntent(wmHelper)
        primaryApp.enterSplitScreenFromAppHandleMenu(wmHelper, device)
        // Open allApps via keyboard shortcut
        keyEventHelper.press(KEYCODE_META_RIGHT)
        tapl.allApps.getAppIcon(secondaryApp.appName).launch(secondaryApp.packageName)
        SplitScreenUtils.waitForSplitComplete(wmHelper, primaryApp, secondaryApp)
        desktopApp.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun switchToSplitScreenApps() {
        tapl.launchedAppState.switchToOverview().apply { flingForward() }.currentTask.open()
        SplitScreenUtils.waitForSplitComplete(wmHelper, primaryApp, secondaryApp)
    }

    @After
    fun teardown() {
        primaryApp.exit(wmHelper)
        secondaryApp.exit(wmHelper)
        desktopApp.exit(wmHelper)
    }
}
