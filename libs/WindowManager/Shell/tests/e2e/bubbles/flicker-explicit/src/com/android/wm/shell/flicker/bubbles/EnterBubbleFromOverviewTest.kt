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
import android.tools.device.apphelpers.CalculatorAppHelper
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.EnterBubbleTestCases
import com.android.wm.shell.flicker.bubbles.utils.AssumptionRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.waitAndAssertBubbleAppInExpandedState
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runners.MethodSorters

/**
 * Test entering bubble via clicking bubble menu in taskbar from the overview screen.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:EnterBubbleFromOverviewTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp].
 *     Launch [secondApp] to ensure overview is not empty after moving [testApp] to a bubble and
 *     that we are not entering a bubble from a live tile.
 *     Enter overview.
 * ```
 *
 * Actions:
 * ```
 *     Long press [testApp] icon on the taskbar to show [AppIconMenu].
 *     Click the bubble menu to launch [testApp] into bubble.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [EnterBubbleTestCases]
 */
// TODO(b/479182156) Remove this when bubbling is supported in desktop mode.
@RequiresFlagsDisabled(Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class EnterBubbleFromOverviewTest : BubbleFlickerTestBase(), EnterBubbleTestCases {

    companion object {
        private val secondApp = CalculatorAppHelper(instrumentation)

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    // Make sure testApp is in hotseat so it appears in taskbar in overview
                    SplitScreenUtils.createShortcutOnHotseatIfNotExist(tapl, testApp.appName)

                    testApp.launchViaIntent(wmHelper)
                    secondApp.launchViaIntent(wmHelper)

                    tapl.goHome().switchToOverview()
                },
                transition = {
                    val taskBar = tapl.overview.taskbar ?: error("Taskbar not found")
                    val appIcon = taskBar.getAppIcon(testApp.appName)
                    appIcon.openMenu().bubbleMenuItem.click()
                    waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
                },
                tearDownAfterTransition = {
                    testApp.exit(wmHelper)
                    secondApp.exit(wmHelper)
                    tapl.goHome()
                },
            )

        private val navBar = NavBar.MODE_GESTURAL
    }

    @get:Rule(order = 1)
    val assumptionRule =
        AssumptionRule(
            condition = { tapl.isTablet },
            message = "Taskbar is only enabled on large screen device",
        )

    @get:Rule(order = 2)
    val setUpRule =
        RunOncePerParameterRule(
            testClass = this::class,
            wrappedRule = testSetupRule(navBar).around(recordTraceWithTransitionRule),
        )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader
}
