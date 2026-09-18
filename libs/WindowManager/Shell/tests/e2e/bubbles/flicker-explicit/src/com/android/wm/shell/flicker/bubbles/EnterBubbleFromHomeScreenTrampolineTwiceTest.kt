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

import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.ExpandBubbleFromHomeTestCases
import com.android.wm.shell.flicker.bubbles.testcase.TaskTrampolineBecomesExpandedTestCases
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.BubbleLaunchSource.FROM_HOME_SCREEN
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaTouchOutside
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runners.MethodSorters

/**
 * Test entering bubble TWICE for an app that launches via trampoline task. Bubble is launched via
 * clicking bubble menu from the home screen.
 *
 * To run this test:
 * ```
 *     atest WMShellExplicitFlickerTestsBubbles:EnterBubbleFromHomeScreenTrampolineTwiceTest
 * ```
 *
 * Pre-steps:
 * ```
 * 1. Long press [trampolineApp] icon on the home screen to show [AppIconMenu].
 * 2. Click the bubble menu to launch [trampolineApp] into bubble.
 * 3. Collapse bubbled [runningApp] via touching outside the bubble window
 * ```
 *
 * Actions:
 * ```
 *     Long press [trampolineApp] icon on the home screen to show [AppIconMenu].
 *     Click the bubble menu to launch [trampolineApp] into bubble.
 * ```
 *
 * Verified tests:
 * - [ExpandBubbleFromHomeTestCases]
 * - [TaskTrampolineBecomesExpandedTestCases]
 */
// TODO(b/479182156) Remove this when bubbling is supported in desktop mode.
@RequiresFlagsDisabled(Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
@RequiresFlagsEnabled(
    Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
    com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK,
)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class EnterBubbleFromHomeScreenTrampolineTwiceTest :
    BubbleFlickerTrampolineTestBase(),
    ExpandBubbleFromHomeTestCases,
    TaskTrampolineBecomesExpandedTestCases {

    companion object {
        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    // Launch and collapse the bubble of the trampoline app.
                    launchBubbleViaBubbleMenu(
                        runningApp,
                        tapl,
                        wmHelper,
                        FROM_HOME_SCREEN,
                        trampolineApp,
                    )
                    collapseBubbleAppViaTouchOutside(runningApp, wmHelper)
                },
                transition = {
                    // Launch the trampoline app into bubble again
                    launchBubbleViaBubbleMenu(
                        runningApp,
                        tapl,
                        wmHelper,
                        FROM_HOME_SCREEN,
                        trampolineApp,
                    )
                },
                tearDownAfterTransition = {
                    runningApp.exit(wmHelper)
                    // Clean up the app icon that might have been added to the home screen during
                    // the test transition.
                    val testAppIcon = tapl.workspace.getWorkspaceAppIcon(trampolineApp.appName)
                    tapl.workspace.deleteAppIcon(testAppIcon)
                },
            )

        private val navBar = NavBar.MODE_GESTURAL
    }

    @get:Rule(order = 1)
    val setUpRule =
        RunOncePerParameterRule(
            testClass = this::class,
            wrappedRule = testSetupRule(navBar).around(recordTraceWithTransitionRule),
        )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader
}
