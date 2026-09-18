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

package com.android.wm.shell.flicker.bubbles.utils

import android.tools.device.apphelpers.StandardAppHelper
import com.android.wm.shell.flicker.bubbles.BubbleFlickerTestBase.FlickerProperties.tapl
import com.android.wm.shell.flicker.bubbles.BubbleFlickerTestBase.FlickerProperties.uiDevice
import com.android.wm.shell.flicker.bubbles.BubbleFlickerTestBase.FlickerProperties.wmHelper
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.BubbleLaunchSource.FROM_TASK_BAR
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.VisibleSplitToBubbleTestScenario.TestScenario.FOCUS_PRIMARY_TO_BUBBLE_PRIMARY
import com.android.wm.shell.flicker.bubbles.utils.VisibleSplitToBubbleTestScenario.TestScenario.FOCUS_PRIMARY_TO_BUBBLE_SECONDARY
import com.android.wm.shell.flicker.bubbles.utils.VisibleSplitToBubbleTestScenario.TestScenario.FOCUS_SECONDARY_TO_BUBBLE_PRIMARY
import com.android.wm.shell.flicker.bubbles.utils.VisibleSplitToBubbleTestScenario.TestScenario.FOCUS_SECONDARY_TO_BUBBLE_SECONDARY
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import com.android.wm.shell.flicker.utils.SplitScreenUtils.enterSplitFromAllAppsNoVerify
import com.android.wm.shell.flicker.utils.SplitScreenUtils.waitForSplitComplete

/**
 * Defines test scenarios for checking visibility when transitioning from split-screen to bubble.
 *
 * @property scenario The [TestScenario]
 * @property rule The rule to record traces with transitions for the scenario.
 */
data class VisibleSplitToBubbleTestScenario(
    val scenario: TestScenario,
    val rule: RecordTraceWithTransitionRule,
) {
    override fun toString(): String = scenario.toString()

    /**
     * Defines the different test scenarios for relaunching a visible split-screen app into a
     * bubble.
     */
    enum class TestScenario {
        /** Scenario: Focus on primary app, convert primary app to bubble. */
        FOCUS_PRIMARY_TO_BUBBLE_PRIMARY,
        /** Scenario: Focus on primary app, convert secondary app to bubble. */
        FOCUS_SECONDARY_TO_BUBBLE_PRIMARY,
        /** Scenario: Focus on secondary app, convert primary app to bubble. */
        FOCUS_PRIMARY_TO_BUBBLE_SECONDARY,
        /** Scenario: Focus on secondary app, convert secondary app to bubble. */
        FOCUS_SECONDARY_TO_BUBBLE_SECONDARY,
    }

    companion object {
        /**
         * Generates a list of test scenarios.
         *
         * @param toBubbleApp the app that will be converted to a bubble
         * @param toFullscreenApp the app that will move to fullscreen from split
         * @param trampolineApp optional trampoline app if specified
         * @return a list of [VisibleSplitToBubbleTestScenario]
         */
        fun generateTestScenarios(
            toBubbleApp: StandardAppHelper,
            toFullscreenApp: StandardAppHelper,
            trampolineApp: StandardAppHelper? = null,
        ): List<VisibleSplitToBubbleTestScenario> =
            listOf(
                VisibleSplitToBubbleTestScenario(
                    FOCUS_PRIMARY_TO_BUBBLE_PRIMARY,
                    generateTransitionRule(
                        toBubbleApp,
                        toFullscreenApp,
                        trampolineApp,
                        focusOnPrimary = true,
                        primaryToBubble = true,
                    ),
                ),
                VisibleSplitToBubbleTestScenario(
                    FOCUS_SECONDARY_TO_BUBBLE_PRIMARY,
                    generateTransitionRule(
                        toBubbleApp,
                        toFullscreenApp,
                        trampolineApp,
                        focusOnPrimary = false,
                        primaryToBubble = true,
                    ),
                ),
                VisibleSplitToBubbleTestScenario(
                    FOCUS_PRIMARY_TO_BUBBLE_SECONDARY,
                    generateTransitionRule(
                        toBubbleApp,
                        toFullscreenApp,
                        trampolineApp,
                        focusOnPrimary = true,
                        primaryToBubble = false,
                    ),
                ),
                VisibleSplitToBubbleTestScenario(
                    FOCUS_SECONDARY_TO_BUBBLE_SECONDARY,
                    generateTransitionRule(
                        toBubbleApp,
                        toFullscreenApp,
                        trampolineApp,
                        focusOnPrimary = false,
                        primaryToBubble = false,
                    ),
                ),
            )

        private fun generateTransitionRule(
            toBubbleApp: StandardAppHelper,
            toFullscreenApp: StandardAppHelper,
            trampolineApp: StandardAppHelper? = null,
            focusOnPrimary: Boolean,
            primaryToBubble: Boolean,
        ): RecordTraceWithTransitionRule {
            // primary/secondaryApp: the app actually shown in split
            val primaryApp =
                if (primaryToBubble) {
                    toBubbleApp
                } else {
                    toFullscreenApp
                }
            val secondaryApp =
                if (primaryToBubble) {
                    toFullscreenApp
                } else {
                    toBubbleApp
                }

            // The app that launched to bubble with its app icon on the task bar, which may differ
            // from [toBubbleApp] for trampoline app.
            val launchingToBubbleApp = trampolineApp ?: toBubbleApp

            // launchingPrimary/SecondaryApp: the app that launched to split with its app icon,
            // which may differ from primary/secondaryApp for trampoline app.
            val launchingPrimaryApp =
                if (primaryToBubble) {
                    launchingToBubbleApp
                } else {
                    toFullscreenApp
                }

            val launchingSecondaryApp =
                if (primaryToBubble) {
                    toFullscreenApp
                } else {
                    launchingToBubbleApp
                }

            return RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    SplitScreenUtils.createShortcutOnHotseatIfNotExist(
                        tapl,
                        launchingToBubbleApp.appName,
                    )
                    enterSplitFromAllAppsNoVerify(tapl, launchingPrimaryApp, launchingSecondaryApp)
                    waitForSplitComplete(wmHelper, primaryApp, secondaryApp)
                    // Update focus to either primary or secondary app.
                    val bounds =
                        wmHelper
                            .getWindowRegion(
                                if (focusOnPrimary) {
                                    primaryApp
                                } else {
                                    secondaryApp
                                }
                            )
                            .bounds
                    uiDevice.click(bounds.centerX(), bounds.centerY())
                },
                transition = {
                    launchBubbleViaBubbleMenu(
                        toBubbleApp,
                        tapl,
                        wmHelper,
                        fromSource = FROM_TASK_BAR,
                        trampolineApp = trampolineApp,
                    )
                },
                tearDownAfterTransition = {
                    toBubbleApp.exit(wmHelper)
                    toFullscreenApp.exit(wmHelper)
                },
            )
        }
    }
}
