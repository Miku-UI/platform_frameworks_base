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
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.After
import org.junit.Ignore
import org.junit.Test

/**
 * This test focuses on entering split screen from fullscreen state via app handle. Test focusing on
 * entering split screen from desktop via app handle is covered by
 * ExitDesktopToSplitScreenWithAppHeaderMenu
 *
 * @see ExitDesktopToSplitScreenWithAppHeaderMenu
 */
@Ignore("Test Base Class")
abstract class EnterSplitScreenWithAppHandleMenu(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val keyEventHelper = KeyEventHelper(InstrumentationRegistry.getInstrumentation())
    val calculatorApp = CalculatorAppHelper(instrumentation)
    val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))

    @Test
    open fun enterSplitScreenFromAppHandle() {
        testApp.launchViaIntent(wmHelper)
        // Split-screen from desktop mode is covered by another test case, this test case focus
        // on entering from fullscreen on both desktop-first and touch-first
        testApp.exitDesktopModeToFullScreenIfNeeded(wmHelper, device)
        testApp.enterSplitScreenFromAppHandleMenu(wmHelper, device)
        // Open allApps via keyboard shortcut
        keyEventHelper.press(KEYCODE_META_RIGHT)
        tapl.allApps.getAppIcon(calculatorApp.appName).launch(calculatorApp.packageName)
        SplitScreenUtils.waitForSplitComplete(wmHelper, testApp, calculatorApp)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
        calculatorApp.exit(wmHelper)
    }
}
