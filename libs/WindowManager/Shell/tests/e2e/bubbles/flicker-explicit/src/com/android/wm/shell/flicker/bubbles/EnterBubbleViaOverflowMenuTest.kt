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
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.ComponentNameMatcher.Companion.BUBBLE
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.MultipleBubbleExpandBubbleAppTestCases
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.dismissBubbleAppViaBubbleBarItem
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.dismissBubbleAppViaFloatingBubbleView
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaOverflow
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
 * Test enter bubble via clicking the overflow view in the overflow page.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:EnterBubbleViaOverflowMenuTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp] into bubble and dismiss. -> It's to make [testApp] shown in overflow page.
 *     Launch [messageApp] into bubble.
 * ```
 *
 * Actions:
 * ```
 *     Switch to the overflow page
 *     Launch [testApp] into bubble again by clicking the overflow view
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [MultipleBubbleExpandBubbleAppTestCases]
 */
// TODO(b/479182156) Remove this when bubbling is supported in desktop mode.
@RequiresFlagsDisabled(Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class EnterBubbleViaOverflowMenuTest(navBar: NavBar) :
    BubbleFlickerTestBase(), MultipleBubbleExpandBubbleAppTestCases {

    companion object {
        private val messageApp = MessagingAppHelper()

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    // Launch and dismiss a bubble app to make it show in overflow.
                    launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                    if (tapl.isTablet) {
                        dismissBubbleAppViaBubbleBarItem(testApp, wmHelper)
                    } else {
                        dismissBubbleAppViaFloatingBubbleView(testApp, wmHelper)
                    }
                    // Launch message app to bubble to make overflow show.
                    launchBubbleViaBubbleMenu(messageApp, tapl, wmHelper)
                },
                transition = { launchBubbleViaOverflow(testApp, wmHelper) },
                tearDownAfterTransition = {
                    testApp.exit()
                    messageApp.exit()
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

    @Test
    override fun focusChanges() {
        focusEventSubject.focusChanges(
            messageApp.toWindowName(),
            // Switch to the overflow page
            BUBBLE.toWindowName(),
            // Launch the test app to bubble
            testApp.toWindowName(),
        )
    }

    @Test
    override fun appWindowReplacesPreviousAppAsTopWindow() {
        wmTraceSubject
            // Before clicking the overflow, the focused app is messageApp.
            .isAppWindowOnTop(messageApp)
            .then()
            .isAppWindowOnTop(LAUNCHER)
            .then()
            .isAppWindowOnTop(
                ComponentNameMatcher.SNAPSHOT.or(ComponentNameMatcher.SPLASH_SCREEN),
                isOptional = true,
            )
            .then()
            .isAppWindowOnTop(testApp)
            .forAllEntries()
    }
}
