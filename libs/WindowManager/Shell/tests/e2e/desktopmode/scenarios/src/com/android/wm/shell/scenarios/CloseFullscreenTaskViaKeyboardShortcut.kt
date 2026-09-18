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

import android.app.ActivityOptions
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.KeyEventHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags.Flags
import org.junit.After
import org.junit.Test

/** Base scenario test for closing a fullscreen task via the keyboard shortcut. */
@RequiresFlagsEnabled(
    Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
    Flags.FLAG_CLOSE_FULLSCREEN_AND_SPLITSCREEN_KEYBOARD_SHORTCUT,
    Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
)
abstract class CloseFullscreenTaskViaKeyboardShortcut : TestScenarioBase() {
    private val wmHelper = WindowManagerStateHelper(getInstrumentation())
    private val testApp = DesktopModeAppHelper(SimpleAppHelper(getInstrumentation()))
    private val keyEventHelper = KeyEventHelper(getInstrumentation())

    @Test
    open fun closeTaskViaKeyboardShortcut() {
        testApp.launchViaIntent(
            wmHelper,
            options =
                ActivityOptions.makeBasic().apply {
                    launchWindowingMode = WINDOWING_MODE_FULLSCREEN
                },
        )

        keyEventHelper.press(KeyEvent.KEYCODE_W, KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON)

        wmHelper.StateSyncBuilder().withWindowSurfaceDisappeared(testApp).waitForAndVerify()
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }
}
