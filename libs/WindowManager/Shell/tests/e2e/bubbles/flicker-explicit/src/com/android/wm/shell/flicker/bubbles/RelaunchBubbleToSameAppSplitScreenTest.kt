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
import android.tools.traces.component.IComponentNameMatcher
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.NewTasksAppHelper
import com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK
import com.android.window.flags.Flags.FLAG_INHERIT_CONSECUTIVE_LAUNCH_CALLER_UID
import com.android.wm.shell.Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.RelaunchBubbleToSameAppSplitScreenTest.Companion.primaryApp
import com.android.wm.shell.flicker.bubbles.RelaunchBubbleToSameAppSplitScreenTest.Companion.secondaryApp
import com.android.wm.shell.flicker.bubbles.testcase.AppReplacesPreviousAppInSplitScreenTestCases
import com.android.wm.shell.flicker.bubbles.testcase.BubbleExitTestCases
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaTouchOutside
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import com.android.wm.shell.flicker.utils.SplitScreenUtils.enterSplit
import com.android.wm.shell.flicker.utils.SplitScreenUtils.withSplitScreenComplete
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runners.MethodSorters

/**
 * Test relaunching a bubble via same-app trampoline into split-screen.
 *
 * To run this test::
 * ```
 * atest WMShellExplicitFlickerTestsBubbles:RelaunchBubbleToSameAppSplitScreenTest
 * ```
 *
 * Pre-steps:
 * ```
 * 1. Launch [testApp] into a collapsed bubble.
 * 2. Launch [primaryApp] and [secondaryApp] into split-screen.
 * ```
 *
 * Actions:
 * ```
 * From [primaryApp], relaunch the [testApp].
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleExitTestCases]: Verifies the bubble exits.
 * - [AppReplacesPreviousAppInSplitScreenTestCases]: Verifies the [testApp] replaces the
 *   [primaryApp] in split-screen.
 */
// TODO(b/479182156) Remove this when bubbling is supported in desktop mode.
@RequiresFlagsDisabled(FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
@RequiresFlagsEnabled(
    FLAG_ENABLE_CREATE_ANY_BUBBLE,
    FLAG_ENABLE_BUBBLE_ROOT_TASK,
    FLAG_INHERIT_CONSECUTIVE_LAUNCH_CALLER_UID,
)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class RelaunchBubbleToSameAppSplitScreenTest :
    BubbleFlickerTestBase(), BubbleExitTestCases, AppReplacesPreviousAppInSplitScreenTestCases {

    companion object {
        // [primaryApp] and [testApp] are in the same app package.
        private val primaryApp = NewTasksAppHelper(instrumentation)
        private val secondaryApp = SplitScreenUtils.getSecondary(instrumentation)

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    // Launch [testApp] into collapsed bubble.
                    launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                    collapseBubbleAppViaTouchOutside(testApp, wmHelper)

                    // Launch [primaryApp] and [secondaryApp] into split screen.
                    enterSplit(wmHelper, tapl, uiDevice, primaryApp, secondaryApp)
                },
                transition = {
                    // Relaunch [testApp] bubble into primary split.
                    primaryApp.openNewTaskWithRecycle(uiDevice, wmHelper) {
                        withSplitScreenComplete(primaryApp = testApp, secondaryApp)
                    }
                },
                tearDownAfterTransition = {
                    testApp.exit(wmHelper)
                    primaryApp.exit(wmHelper)
                    secondaryApp.exit(wmHelper)
                },
            )
    }

    @get:Rule(order = 1)
    val setUpRule =
        RunOncePerParameterRule(
            testClass = this::class,
            wrappedRule = testSetupRule(NavBar.MODE_GESTURAL).around(recordTraceWithTransitionRule),
        )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    /**
     * The [primaryApp] in split-screen that is replaced by the [testApp]. Used by
     * [AppReplacesPreviousAppInSplitScreenTestCases] to verify that it is visible at the start.
     */
    override val previousApp: IComponentNameMatcher = primaryApp

    /**
     * The [secondaryApp] on the other side of the split. Used by
     * [AppReplacesPreviousAppInSplitScreenTestCases] to verify that it stays visible throughout the
     * transition.
     */
    override val otherApp: IComponentNameMatcher = secondaryApp
}
