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

package com.android.systemui.brightness.ui.compose

import android.platform.test.annotations.MotionTest
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.brightness.ui.compose.BrightnessSliderMotionTestKeys.ActiveIconAlpha
import com.android.systemui.brightness.ui.compose.BrightnessSliderMotionTestKeys.InactiveIconAlpha
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.haptics.slider.sliderHapticsViewModelFactory
import com.android.systemui.integration.SystemUiIntegrationTest
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.testKosmos
import com.android.systemui.util.policy.PolicyRestriction
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import org.junit.Rule
import org.junit.runner.RunWith
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.feature
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.golden.dataPointType
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays.Phone

@RunWith(AndroidJUnit4::class)
@LargeTest
@MotionTest
@SystemUiIntegrationTest
class BrightnessSliderMotionTest : SysuiTestCase() {

    private val deviceSpec = DeviceEmulationSpec(Phone)
    private val kosmos = testKosmos()

    @get:Rule val motionTestRule = createSysUiComposeMotionTestRule(kosmos, deviceSpec)

    @Composable
    private fun BrightnessSliderUnderTest(startingValue: Int) {
        PlatformTheme {
            BrightnessSlider(
                gammaValue = startingValue,
                modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                valueRange = 0..100,
                iconResProvider = BrightnessSliderViewModel::getIconForPercentage,
                imageLoader = { resId, context -> context.getDrawable(resId)!!.asIcon(null) },
                restriction = PolicyRestriction.NoRestriction,
                onRestrictedClick = {},
                onDrag = {},
                onStop = {},
                overriddenByAppState = false,
                hapticsViewModelFactory = kosmos.sliderHapticsViewModelFactory,
            )
        }
    }

    @Test
    fun iconAlphaChanges() {
        motionTestRule.runTest(timeout = 30.seconds) {
            val motion =
                recordMotion(
                    content = { BrightnessSliderUnderTest(0) },
                    ComposeRecordingSpec(
                        MotionControl(delayReadyToPlay = { awaitIdle() }) {
                            coroutineScope {
                                performTouchInputAsync(
                                    onNode(hasTestTag("com.android.systemui:id/slider"))
                                ) {
                                    swipeRight(startX = left, endX = right, durationMillis = 500)
                                }

                                awaitIdle()
                            }
                        }
                    ) {
                        feature(ActiveIconAlpha, Float.dataPointType)
                        feature(InactiveIconAlpha, Float.dataPointType)
                    },
                )
            assertThat(motion).timeSeriesMatchesGolden("brightnessSlider_iconAlphaChanges")
        }
    }
}
