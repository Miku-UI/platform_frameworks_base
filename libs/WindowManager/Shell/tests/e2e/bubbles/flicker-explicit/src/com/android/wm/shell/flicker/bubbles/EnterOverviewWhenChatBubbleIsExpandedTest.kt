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

import android.platform.systemui_tapl.ui.Root
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import androidx.test.filters.RequiresDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.server.wm.flicker.helpers.ChatBubbleActivityHelper
import com.android.server.wm.flicker.helpers.LaunchBubbleHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAppShowsAtEndTestCases
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.expandBubbleAppViaTapOnBubbleStack
import com.android.wm.shell.flicker.bubbles.utils.BubbleNotificationsEnabler
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runners.MethodSorters

/**
 * Test entering Overview via clicking the overview button in the navigation bar when a chat bubble
 * is expanded, and then re-expanding the bubble in overview.
 *
 * To run this test: `atest
 * WMShellExplicitFlickerTestsBubbles:EnterOverviewWhenChatBubbleIsExpandedTest`
 *
 * Pre-steps:
 * ```
 *     Enable bubble notification settings for [testApp].
 *     Launch the LaunchBubbleApp and click on the Add Bubble button to create a chat bubble
 *     notification.
 *     Tap on the bubble to expand it.
 * ```
 *
 * Actions:
 * ```
 *     Enter overview by tapping on the overview navigation button.
 *     Tap the bubble to expand it.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleAppShowsAtEndTestCases]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class EnterOverviewWhenChatBubbleIsExpandedTest :
    BubbleFlickerTestBase(), BubbleAppShowsAtEndTestCases {

    override val testApp = chatBubbleActivity

    companion object {

        private val launchBubbleApp = LaunchBubbleHelper(instrumentation)
        private val chatBubbleActivity = ChatBubbleActivityHelper(instrumentation)
        private val bubbleNotificationsEnabler =
            BubbleNotificationsEnabler(instrumentation, launchBubbleApp.packageName)

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    bubbleNotificationsEnabler.enable()
                    launchBubbleApp.launchViaIntent(wmHelper)
                    addBubbleButton.click()
                    expandBubbleAppViaTapOnBubbleStack(chatBubbleActivity, wmHelper)
                },
                transition = {
                    tapl.launchedAppState.switchToOverview()
                    expandBubbleAppViaTapOnBubbleStack(chatBubbleActivity, wmHelper)
                },
                tearDownAfterTransition = {
                    bubbleNotificationsEnabler.disable()
                    waitForBubblesToBeGone()
                    launchBubbleApp.exit(wmHelper)
                    tapl.goHome()
                },
            )

        private val navBar = NavBar.MODE_3BUTTON

        private val addBubbleButton: UiObject2
            get() =
                uiDevice.wait(Until.findObject(By.text("Add Bubble")), 4000)
                    ?: error("cannot find add button")

        private fun waitForBubblesToBeGone() {
            Root.get().verifyBubbleBarIsHidden()
            Root.get().verifyNoBubbleIsVisible()
        }
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
