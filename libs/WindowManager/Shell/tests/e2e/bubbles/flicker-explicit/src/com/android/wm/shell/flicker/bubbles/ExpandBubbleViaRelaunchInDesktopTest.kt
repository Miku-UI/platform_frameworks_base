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
import android.platform.test.annotations.RequiresDesktopDevice
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar.MODE_GESTURAL
import android.view.Display.DEFAULT_DISPLAY
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.NewTasksAppHelper
import com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK
import com.android.wm.shell.Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAppBecomesExpandedTestCases
import com.android.wm.shell.flicker.bubbles.testcase.DesktopAppAlwaysVisibleTestCases
import com.android.wm.shell.flicker.bubbles.testcase.ExpandBubbleTestCases
import com.android.wm.shell.flicker.bubbles.utils.AssumptionRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaTouchOutside
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.withBubbleExpanded
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import com.android.wm.shell.shared.desktopmode.DesktopState
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runners.MethodSorters

/**
 * Test expanding a collapsed bubble by relaunching its app from a desktop mode app.
 *
 * This test verifies that the collapsed bubble expands correctly when its activity is relaunched
 * from another app running in a freeform desktop window.
 *
 * To run this test: `atest WMShellFlickerTestsBubbles:ExpandBubbleViaRelaunchInDesktopTest`
 *
 * Pre-steps:
 * ```
 * 1. Launch [bubbleApp] into a collapsed bubble.
 * 2. Launch [desktopApp] into a freeform window in desktop mode.
 * ```
 *
 * Actions:
 * ```
 * From [desktopApp], relaunch the [bubbleApp].
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [ExpandBubbleTestCases]: Verifies the bubble stack expands and [bubbleApp] becomes visible.
 * - [DesktopAppAlwaysVisibleTestCases]: Verifies [desktopApp] remains visible in desktop mode.
 */
// TODO(b/479182156) Remove this when bubbling is supported in desktop mode.
@RequiresFlagsDisabled(FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
@RequiresFlagsEnabled(FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ENABLE_BUBBLE_ROOT_TASK)
@RequiresDesktopDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class ExpandBubbleViaRelaunchInDesktopTest :
    BubbleFlickerTestBase(), ExpandBubbleTestCases, DesktopAppAlwaysVisibleTestCases {

    companion object ExpandBubbleViaRelaunchInDesktopTestProperties {
        private val context = instrumentation.targetContext
        private val desktopState = DesktopState.fromContext(context)
        private val bubbleApp = testApp
        private val desktopApp = NewTasksAppHelper(instrumentation)

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    // Launch [bubbleApp] into collapsed bubble.
                    launchBubbleViaBubbleMenu(bubbleApp, tapl, wmHelper)
                    collapseBubbleAppViaTouchOutside(bubbleApp, wmHelper)

                    // Launch [desktopApp] into desktop mode.
                    DesktopModeAppHelper(innerHelper = desktopApp)
                        .enterDesktopMode(wmHelper, uiDevice)
                },
                transition = {
                    // Relaunch [bubbleApp] from [desktopApp].
                    desktopApp.openNewTaskWithRecycle(uiDevice, wmHelper) {
                        // Reopen the collapsed bubble.
                        withBubbleExpanded(bubbleApp)
                    }
                },
                tearDownAfterTransition = {
                    bubbleApp.exit(wmHelper)
                    desktopApp.exit(wmHelper)
                },
            )
    }

    @get:Rule(order = 1)
    val assumptionRule =
        AssumptionRule(
            condition = { desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY) },
            message = "Skipping test on ${Build.PRODUCT} as it doesn't support desktop mode.",
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
     * The [desktopApp] that is launched into a freeform window in desktop mode. Used by
     * [DesktopAppAlwaysVisibleTestCases] to verify that this app remains visible when the
     * [bubbleApp] expands on top of it.
     */
    override val desktopApp = ExpandBubbleViaRelaunchInDesktopTestProperties.desktopApp

    /**
     * The [desktopApp] that was previously the top app, which will be replaced by [bubbleApp] when
     * the bubble expands. Used by [BubbleAppBecomesExpandedTestCases] to verify that [bubbleApp]
     * becomes the new top app during the bubble expansion transition.
     */
    override val previousApp = ExpandBubbleViaRelaunchInDesktopTestProperties.desktopApp

    /** The [desktopApp] is visible behind the expanded bubble; skipping invisible assertion. */
    override fun shouldAssertPreviousBecomesInvisible() = false
}
