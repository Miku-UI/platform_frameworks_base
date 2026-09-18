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

package com.android.wm.shell.flicker.bubbles

import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.RelaunchVisibleAppToBubbleTestCases
import com.android.wm.shell.flicker.bubbles.utils.AssumptionRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.BubbleLaunchSource.FROM_TASK_BAR
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Test entering bubble for an app that was previously open in fullscreen and visible. Bubble is
 * launched via clicking bubble menu from the task bar.
 *
 * To run this test:
 * ```
 *     atest WMShellExplicitFlickerTestsBubbles:RelaunchVisibleFullscreenAppToBubbleTest
 * ```
 *
 * Pre-steps:
 * ```
 *     Start the app in fullscreen.
 * ```
 *
 * Actions:
 * ```
 *     Click the bubble menu from task bar to launch the app into a bubble.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [RelaunchVisibleAppToBubbleTestCases]
 */
// TODO(b/479182156) Remove this when bubbling is supported in desktop mode.
@RequiresFlagsDisabled(Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class RelaunchVisibleFullscreenAppToBubbleTest(navBar: NavBar) :
    BubbleFlickerTestBase(), RelaunchVisibleAppToBubbleTestCases {

    companion object {
        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    // Pin to hotseat to ensure the app icon is on the taskbar, which is used
                    // to launch the app into a bubble.
                    SplitScreenUtils.createShortcutOnHotseatIfNotExist(tapl, testApp.appName)
                    testApp.launchViaIntent(wmHelper)
                },
                transition = {
                    launchBubbleViaBubbleMenu(testApp, tapl, wmHelper, fromSource = FROM_TASK_BAR)
                },
                tearDownAfterTransition = { testApp.exit(wmHelper) },
            )

        @Parameters(name = "{0}") @JvmStatic fun data(): List<NavBar> = NavBar.entries
    }

    @get:Rule(order = 1)
    val assumptionRule =
        AssumptionRule(
            condition = { tapl.isTablet },
            message = "The bubble bar is only available on large screen devices",
        )

    @get:Rule(order = 2)
    val setUpRule =
        RunOncePerParameterRule(
            testClass = this::class,
            wrappedRule = testSetupRule(navBar).around(recordTraceWithTransitionRule),
            params = arrayOf(navBar),
        )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    /** Verifies that the launcher window becomes visible during the transition. */
    @Test
    fun launcherWindowBecomesVisible() {
        wmTraceSubject
            .isAppWindowInvisible(LAUNCHER)
            .then()
            .isAppWindowVisible(LAUNCHER)
            .forAllEntries()
    }

    /** Verifies that the launcher layer becomes visible during the transition. */
    @Test
    fun launcherLayerBecomesVisible() {
        layersTraceSubject.isInvisible(LAUNCHER).then().isVisible(LAUNCHER).forAllEntries()
    }
}
