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
import android.tools.traces.parsers.toFlickerComponent
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.CollapseBubbleAppTestCases
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaBackKey
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Test collapse bubble app that has overridden onBackPressed via clicking back key.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:CollapseBackOverriddenAppViaBackTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp] into bubble
 * ```
 *
 * Actions:
 * ```
 *     Collapse bubbled [testApp] via back key
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [CollapseBubbleAppTestCases]
 */
// TODO(b/479182156) Remove this when bubbling is supported in desktop mode.
@RequiresFlagsDisabled(Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class CollapseBackOverriddenAppViaBackTest(navBar: NavBar) :
    BubbleFlickerTestBase(), CollapseBubbleAppTestCases {

    companion object {

        private val testApp =
            SimpleAppHelper(
                instrumentation,
                launcherName = ActivityOptions.MoveToBackOnBackPressedActivity.LABEL,
                component =
                    ActivityOptions.MoveToBackOnBackPressedActivity.COMPONENT.toFlickerComponent(),
            )

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = { launchBubbleViaBubbleMenu(testApp, tapl, wmHelper) },
                transition = { collapseBubbleAppViaBackKey(testApp, tapl, wmHelper) },
                tearDownAfterTransition = { testApp.exit(wmHelper) },
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

    override val testApp = Companion.testApp
}
