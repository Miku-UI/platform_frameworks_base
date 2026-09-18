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
import android.tools.device.apphelpers.BrowserAppHelper
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.NewTasksAppHelper
import com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK
import com.android.window.flags.Flags.FLAG_INHERIT_CONSECUTIVE_LAUNCH_CALLER_UID
import com.android.wm.shell.Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.AppAnimateInTestCases
import com.android.wm.shell.flicker.bubbles.testcase.AppReplacesPreviousAppTestCases
import com.android.wm.shell.flicker.bubbles.testcase.ExpandBubbleTestCases
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaTouchOutside
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.withBubbleExpanded
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import com.android.wm.shell.flicker.utils.SplitScreenUtils.enterSplit
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Test relaunching a bubble from a split-screen app where the bubble belongs to a different app.
 *
 * To run this test:
 * ```
 * atest WMShellExplicitFlickerTestsBubbles:ExpandBubbleViaRelaunchFromSplitDifferentAppTest
 * ```
 *
 * Pre-steps:
 * ```
 * 1. Launch [bubbleApp] into a collapsed bubble.
 * 2. Launch [primaryApp] and [secondaryApp] into split-screen.
 * ```
 *
 * Actions:
 * ```
 * From [primaryApp], relaunch the [bubbleApp].
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [ExpandBubbleTestCases]: Verifies the collapsed [bubbleApp] expands.
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
class ExpandBubbleViaRelaunchFromSplitDifferentAppTest :
    BubbleFlickerTestBase(), ExpandBubbleTestCases {

    companion object {
        private val bubbleApp = BrowserAppHelper(instrumentation)
        private val primaryApp = NewTasksAppHelper(instrumentation)
        private val secondaryApp = SplitScreenUtils.getSecondary(instrumentation)

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    // Launch [bubbleApp] into collapsed bubble.
                    launchBubbleViaBubbleMenu(bubbleApp, tapl, wmHelper)
                    collapseBubbleAppViaTouchOutside(bubbleApp, wmHelper)

                    // Launch [primaryApp] and [secondaryApp] into split screen.
                    enterSplit(wmHelper, tapl, uiDevice, primaryApp, secondaryApp)
                },
                transition = {
                    // Relaunch bubble from primary split.
                    primaryApp.openNewBrowser(uiDevice, wmHelper) {
                        // Reopen the collapsed bubble.
                        withBubbleExpanded(bubbleApp)
                    }
                },
                tearDownAfterTransition = {
                    bubbleApp.exit(wmHelper)
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
     * The browser app in bubble. Used by [AppAnimateInTestCases] to verify that the bubble app
     * layer becomes visible when the bubble is expanded.
     */
    override val testApp = bubbleApp

    /**
     * The [primaryApp] in split-screen that launches the bubble. Used by
     * [AppReplacesPreviousAppTestCases] to verify that the bubble is opened on top of.
     */
    override val previousApp = primaryApp

    /**
     * The split-screen tasks remain visible behind the expanded bubble, so we should not assert
     * that they become invisible.
     */
    override fun shouldAssertPreviousBecomesInvisible() = false

    @Ignore("Rounded corners assertion fails after bubble expanded from split screen (b/493767015)")
    @Test
    override fun appLayerHasRoundedCorner() {}
}
