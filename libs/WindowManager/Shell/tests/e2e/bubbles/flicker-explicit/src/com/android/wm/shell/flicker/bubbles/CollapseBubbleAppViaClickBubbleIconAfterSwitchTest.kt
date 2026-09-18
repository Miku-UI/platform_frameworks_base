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
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import androidx.test.filters.RequiresDevice
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.server.wm.flicker.helpers.MotionEventHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.CollapseBubbleAppTestCases
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.clickBubbleAppIcon
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaBackKey
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.getBubbleAppIcon
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.waitAndAssertBubbleAppInCollapseState
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.waitAndAssertBubbleAppInExpandedState
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Test collapse bubble app via clicking on the bubble icon of the expanded Task immediately after
 * bubble switch.
 *
 * To run this test:
 * ```
 *     atest WMShellExplicitFlickerTestsBubbles:CollapseBubbleAppViaClickBubbleIconAfterSwitchTest
 * ```
 *
 * Pre-steps:
 * ```
 *     Launch [previousApp] to bubble and collapse
 *     Launch [testApp] to bubble
 * ```
 *
 * Actions:
 * ```
 *     Fast double click on the collapse [previousApp], which should first switch to [previousApp]
 *     and then collapse all bubbles.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [CollapseBubbleAppTestCases]
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
class CollapseBubbleAppViaClickBubbleIconAfterSwitchTest(navBar: NavBar) :
    BubbleFlickerTestBase(), CollapseBubbleAppTestCases {

    companion object {
        private val previousApp = CalculatorAppHelper()

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    launchBubbleViaBubbleMenu(previousApp, tapl, wmHelper)
                    collapseBubbleAppViaBackKey(previousApp, tapl, wmHelper)
                    launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                },
                transition = {
                    if (tapl.isTablet) {
                        // Simulate a very fast double click to verify the transition interruption.
                        val motionEventHelper =
                            MotionEventHelper(
                                getInstrumentation(),
                                MotionEventHelper.InputMethod.TOUCH,
                            )
                        val previousAppBubbleIcon = getBubbleAppIcon(previousApp)
                        val x = previousAppBubbleIcon.visibleCenter.x
                        val y = previousAppBubbleIcon.visibleCenter.y
                        motionEventHelper.doubleClick(x, y)

                        waitAndAssertBubbleAppInCollapseState(testApp, wmHelper)
                        waitAndAssertBubbleAppInCollapseState(previousApp, wmHelper)
                    } else {
                        // On phone, the second click won't take effect until the previous expansion
                        // is done.
                        clickBubbleAppIcon(previousApp)

                        waitAndAssertBubbleAppInExpandedState(previousApp, wmHelper)

                        clickBubbleAppIcon(previousApp)

                        waitAndAssertBubbleAppInCollapseState(previousApp, wmHelper)
                    }
                },
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

    override val previousApp = Companion.previousApp

    @Test
    override fun focusChanges() {
        focusEventSubject.focusChanges(
            testApp.toWindowName(), // LOST, the first click to switch out
            previousApp.toWindowName(), // GAINED, previousApp gets switch to expand
            LAUNCHER.toWindowName(), // GAINED, previousApp collapse from the second click
        )
    }

    @Test
    override fun previousAppWindowReplacesTestAppAsTopWindow() {
        wmTraceSubject
            .isAppWindowOnTop(testApp)
            .then()
            .isAppWindowOnTop(previousApp)
            .then()
            .isAppWindowOnTop(LAUNCHER)
            .forAllEntries()
    }
}
