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

package com.android.systemui.flashlight.ui.compose

import android.platform.test.annotations.MotionTest
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flashlight.ui.composable.VerticalFlashlightSlider
import com.android.systemui.flashlight.ui.composable.VerticalFlashlightSliderMotionTestKeys
import com.android.systemui.haptics.slider.sliderHapticsViewModelFactory
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.testKosmos
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.junit.Rule
import org.junit.runner.RunWith
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.asDataPoint
import platform.test.motion.compose.feature
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.compose.values.MotionTestValueKey
import platform.test.motion.golden.FeatureCapture
import platform.test.motion.golden.TimeSeriesCaptureScope
import platform.test.motion.golden.asDataPoint
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays.Phone

@RunWith(AndroidJUnit4::class)
@LargeTest
@MotionTest
class VerticalFlashlightSliderMotionTest : SysuiTestCase() {

    private val deviceSpec = DeviceEmulationSpec(Phone)
    private val kosmos = testKosmos()

    @get:Rule val motionTestRule = createSysUiComposeMotionTestRule(kosmos, deviceSpec)

    @Composable
    private fun VerticalFlashlightSliderUnderTest(startingValue: Int) {

        VerticalFlashlightSlider(
            levelValue = startingValue,
            valueRange = 0..45,
            onValueChange = {},
            onValueChangeFinished = {},
            isEnabled = true,
            hapticsViewModelFactory = kosmos.sliderHapticsViewModelFactory,
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            modifier = Modifier.padding(48.dp),
        )
    }

    @Test
    fun colorGradientChanges() {
        motionTestRule.runTest(timeout = 30.seconds) {
            val motion =
                recordMotion(
                    content = { VerticalFlashlightSliderUnderTest(0) },
                    ComposeRecordingSpec(
                        MotionControl {
                            coroutineScope {
                                async {
                                        performTouchInputAsync(
                                            onNode(hasTestTag("com.android.systemui:id/slider"))
                                        ) {
                                            swipeUp(
                                                startY = bottom,
                                                endY = top,
                                                durationMillis = 500,
                                            )
                                        }
                                    }
                                    .join()
                            }
                        }
                    ) {
                        featureFloat(VerticalFlashlightSliderMotionTestKeys.TrackEndAlpha)
                        featureDp(VerticalFlashlightSliderMotionTestKeys.TrackWidth)
                        featureDp(VerticalFlashlightSliderMotionTestKeys.TrackHeight)
                    },
                )
            assertThat(motion).timeSeriesMatchesGolden("VerticalFlashlightSlider_trackChanges")
        }
    }

    private companion object {

        fun TimeSeriesCaptureScope<SemanticsNodeInteractionsProvider>.featureFloat(
            motionTestValueKey: MotionTestValueKey<Float>
        ) {
            feature(
                motionTestValueKey = motionTestValueKey,
                capture =
                    FeatureCapture(motionTestValueKey.semanticsPropertyKey.name) {
                        it.asDataPoint()
                    },
            )
        }

        fun TimeSeriesCaptureScope<SemanticsNodeInteractionsProvider>.featureDp(
            motionTestValueKey: MotionTestValueKey<Dp>
        ) {
            feature(
                motionTestValueKey = motionTestValueKey,
                capture =
                    FeatureCapture(motionTestValueKey.semanticsPropertyKey.name) {
                        it.asDataPoint()
                    },
            )
        }
    }
}
