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

import android.graphics.Bitmap
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresDevice
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import android.tools.Rotation
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import android.tools.traces.component.ComponentNameMatcher.Companion.TASK_BAR
import com.android.server.wm.flicker.helpers.ImeShownOnAppStartHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.EnterBubbleFromOverviewSingleAppWithImeTest.Companion.testApp
import com.android.wm.shell.flicker.bubbles.testcase.EnterBubbleTestCases
import com.android.wm.shell.flicker.bubbles.testcase.ImeBecomesVisibleAndBubbleIsShrunkTestCase
import com.android.wm.shell.flicker.bubbles.utils.AssumptionRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.waitAndAssertBubbleAppInExpandedState
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import com.google.common.truth.Truth.assertThat
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Test entering bubble via clicking bubble menu in taskbar from the overview screen with a single
 * app. The app is set up to show the IME when it is launched.
 *
 * To run this test:
 * ```
 *     atest WMShellExplicitFlickerTestsBubbles:EnterBubbleFromOverviewSingleAppWithImeTest
 * ```
 *
 * Pre-steps:
 * ```
 *     Launch [testApp].
 *     Enter overview via home screen.
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
 * - [ImeBecomesVisibleAndBubbleIsShrunkTestCase]
 */
// TODO(b/479182156) Remove this when bubbling is supported in desktop mode.
@RequiresFlagsDisabled(Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class EnterBubbleFromOverviewSingleAppWithImeTest(navBar: NavBar) :
    BubbleFlickerTestBase(), EnterBubbleTestCases, ImeBecomesVisibleAndBubbleIsShrunkTestCase {

    companion object {
        private val testApp = ImeShownOnAppStartHelper(instrumentation, Rotation.ROTATION_0)

        /** The screenshot took at the end of the transition. */
        private lateinit var bitmapAtEnd: Bitmap

        /** The IME inset observed from [testApp] */
        private var imeInset: Int = -1

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    // Make sure testApp is in hotseat so it appears in taskbar in overview
                    SplitScreenUtils.createShortcutOnHotseatIfNotExist(tapl, testApp.appName)
                    testApp.launchViaIntent(wmHelper)
                    tapl.goHome().switchToOverview()
                },
                transition = {
                    val taskBar = tapl.overview.taskbar ?: error("Taskbar not found")
                    val appIcon = taskBar.getAppIcon(testApp.appName)
                    appIcon.openMenu().bubbleMenuItem.click()
                    waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
                    testApp.waitIMEShown(wmHelper)
                    bitmapAtEnd = instrumentation.uiAutomation.takeScreenshot()
                    imeInset = testApp.retrieveImeBottomInset()
                    assertThat(imeInset).isGreaterThan(0)
                },
                tearDownAfterTransition = {
                    testApp.exit(wmHelper)
                    tapl.goHome()
                },
            )

        @Parameters(name = "{0}") @JvmStatic fun data(): List<NavBar> = NavBar.entries
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
            params = arrayOf(navBar),
        )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    override val testApp
        get() = Companion.testApp

    override val bitmapAtEnd
        get() = Companion.bitmapAtEnd

    override val expectedImeInset
        get() = imeInset

    @Test
    override fun focusChanges() {
        focusEventSubject.focusChanges(
            LAUNCHER.toWindowName(), // LOST, start in overview
            TASK_BAR.toWindowName(), // GAINED, interacting with taskbar
            testApp.toWindowName(), // GAINED, the app is running in bubble mode
        )
    }

    @Ignore("Ime shows up during the transition, so the Bubble view can get occluded")
    @Test
    override fun appLayerResizeConsistently() {}
}
