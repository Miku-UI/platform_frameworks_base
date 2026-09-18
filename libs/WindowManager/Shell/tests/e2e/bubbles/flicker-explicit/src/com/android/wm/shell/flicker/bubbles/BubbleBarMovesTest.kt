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

import android.graphics.Point
import android.platform.systemui_tapl.ui.Root
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.ExpandBubbleFromHomeTestCases
import com.android.wm.shell.flicker.bubbles.utils.AssumptionRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaTouchOutside
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.expandBubbleAppViaBubbleBar
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import com.google.common.truth.Truth.assertWithMessage
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Test clicking bubble to expand a bubble that was in collapsed state.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:BubbleBarMovesTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp] into bubble and collapse the bubble
 * ```
 *
 * Actions:
 * ```
 *     Drag and move the bubble bar to the other side
 *     Expand the [testApp] bubble via clicking the bubble bar
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [ExpandBubbleFromHomeTestCases]
 * - the bubble bar is moved
 */
// TODO(b/479182156) Remove this when bubbling is supported in desktop mode.
@RequiresFlagsDisabled(Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class BubbleBarMovesTest(navBar: NavBar) : BubbleFlickerTestBase(), ExpandBubbleFromHomeTestCases {

    companion object {
        private lateinit var bubbleBarBeforeTransition: Point
        private lateinit var bubbleBarAfterTransition: Point

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    // Launch and collapse the bubble.
                    launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                    collapseBubbleAppViaTouchOutside(testApp, wmHelper)
                },
                transition = {
                    bubbleBarBeforeTransition = Root.get().bubbleBar.visibleCenter
                    Root.get().bubbleBar.dragToTheOtherSide()
                    bubbleBarAfterTransition = Root.get().bubbleBar.visibleCenter
                    expandBubbleAppViaBubbleBar(testApp, uiDevice, wmHelper)
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

    /** Verifies that the bubble bar is moved to the other side. */
    @Test
    fun bubbleBarMovesToTheOtherSide() {
        assertWithMessage("The bubble bar position must be changed")
            .that(bubbleBarAfterTransition)
            .isNotEqualTo(bubbleBarBeforeTransition)
    }
}
