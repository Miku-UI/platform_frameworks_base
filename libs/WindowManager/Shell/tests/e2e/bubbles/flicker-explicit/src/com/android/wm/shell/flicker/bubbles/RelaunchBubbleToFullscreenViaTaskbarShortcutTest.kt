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

package com.android.wm.shell.flicker.bubbles

import android.os.Build
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar.MODE_GESTURAL
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import android.tools.traces.component.ComponentNameMatcher.Companion.TASK_BAR_OVERLAY
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.StaticShortcutsAppHelper
import com.android.window.flags.Flags.FLAG_TRACK_LAUNCH_ORIGINATOR
import com.android.wm.shell.Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.AppOpenInFullscreenTestCases
import com.android.wm.shell.flicker.bubbles.testcase.BubbleExitTestCases
import com.android.wm.shell.flicker.bubbles.utils.AssumptionRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaTouchOutside
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Test relaunching a bubbled app to fullscreen via its app shortcut in taskbar all apps.
 *
 * To run this test:
 * ```
 *     atest WMShellFlickerTestsBubbles:RelaunchBubbleToFullscreenViaTaskbarShortcutTest
 * ```
 *
 * Pre-steps:
 * ```
 *     1. Start the test app in a bubble.
 *     2. Collapse the bubble.
 *     3. Close home all apps that opened while launching the bubble.
 * ```
 *
 * Actions:
 * ```
 *     1. Enter the overview mode.
 *     2. Open the all apps from the taskbar.
 *     3. Relaunch the test app using the app shortcut in taskbar all apps.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleExitTestCases]: Verifies the bubble exits.
 * - [AppOpenInFullscreenTestCases]: Verifies the test app is fullscreen.
 *
 * Note: Relaunching from the taskbar is a special case because it lacks a valid activity token.
 */
// TODO(b/479182156) Remove this when bubbling is supported in desktop mode.
@RequiresFlagsDisabled(FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
@RequiresFlagsEnabled(FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_TRACK_LAUNCH_ORIGINATOR)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class RelaunchBubbleToFullscreenViaTaskbarShortcutTest :
    BubbleFlickerTestBase(), BubbleExitTestCases, AppOpenInFullscreenTestCases {

    companion object RelaunchBubbleToFullscreenViaTaskbarShortcutTestProperties {
        val testApp = StaticShortcutsAppHelper(instrumentation)

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    // Launch [testApp] into collapsed bubble.
                    launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                    collapseBubbleAppViaTouchOutside(testApp, wmHelper)
                    // Close home all apps that opened while launching the bubble.
                    tapl.goHome()
                },
                transition = {
                    // Enter the overview mode.
                    val overview = tapl.workspace.switchToOverview()
                    // Open the all apps from the taskbar.
                    val taskbar = overview.taskbar ?: error("Taskbar not found in Overview")
                    // Relaunch [testApp] from app shortcut in taskbar all apps.
                    val testAppIcon = taskbar.openAllApps().getAppIcon(testApp.appName)
                    testApp.launchViaShortcut(testAppIcon)
                },
                tearDownAfterTransition = { testApp.exit(wmHelper) },
            )
    }

    @get:Rule(order = 1)
    val assumptionRule =
        AssumptionRule(
            condition = { tapl.isTablet },
            message =
                "Skipping test on ${Build.PRODUCT}: Taskbar is only available on " +
                    "large screens (e.g., tablets, unfolded foldables)",
        )

    @get:Rule(order = 2)
    val setUpRule =
        RunOncePerParameterRule(
            testClass = this::class,
            wrappedRule = testSetupRule(MODE_GESTURAL).around(recordTraceWithTransitionRule),
        )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    /**
     * The [testApp] that is initially launched as a bubble. Used by [BubbleExitTestCases] to verify
     * that the bubble is removed, and by [AppOpenInFullscreenTestCases] to verify that the app
     * becomes fullscreen when it is relaunched via a shortcut.
     */
    override val testApp = RelaunchBubbleToFullscreenViaTaskbarShortcutTestProperties.testApp

    /** Verifies the focus transition from [LAUNCHER] to [TASK_BAR_OVERLAY] and [testApp]. */
    @Test
    override fun focusChanges() {
        focusEventSubject.focusChanges(
            LAUNCHER.toWindowName(), // LOST, launcher losts focus
            TASK_BAR_OVERLAY.toWindowName(), // GAINED, taskbar in overview mode gains focus
            testApp.toWindowName(), // GAINED, relaunched app gains focus
        )
    }
}
