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
import android.tools.device.apphelpers.MessagingAppHelper
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.SwitchExpandedBubbleTestCases
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaBackKey
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.switchBubble
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Test switching between bubbles by clicking on each bubble icon.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:SwitchBetweenBubblesTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp] to bubble and collapse
 *     Launch [previousApp] to bubble
 * ```
 *
 * Actions:
 * ```
 *     Click on the [testApp] bubble icon to switch to it.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [SwitchExpandedBubbleTestCases]
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
@RunWith(Parameterized::class)
class SwitchBetweenBubblesTest(navBar: NavBar) :
    BubbleFlickerTestBase(), SwitchExpandedBubbleTestCases {
    companion object {
        private val previousApp = MessagingAppHelper()

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                    collapseBubbleAppViaBackKey(testApp, tapl, wmHelper)
                    launchBubbleViaBubbleMenu(previousApp, tapl, wmHelper)
                },
                transition = {
                    switchBubble(appSwitchedFrom = previousApp, appSwitchTo = testApp, wmHelper)
                },
                tearDownAfterTransition = {
                    testApp.exit()
                    previousApp.exit()
                },
            )

        @Parameters(name = "{0}") @JvmStatic fun data(): List<NavBar> = NavBar.entries
    }

    @get:Rule(order = 1)
    val setUpRule =
        RunOncePerParameterRule(
            testClass = this::class,
            wrappedRule = testSetupRule(navBar).around(recordTraceWithTransitionRule),
            params = arrayOf(navBar),
        )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    override val previousApp = Companion.previousApp
}
