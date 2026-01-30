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

package com.android.compose.gesture

import android.platform.test.annotations.MotionTest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.debug.LocalMotionValueDebugController
import com.android.mechanics.debug.MotionValueDebugController
import com.google.common.truth.Truth.assertThat
import kotlin.math.sin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.asDataPoint
import platform.test.motion.compose.createFixedConfigurationComposeMotionTestRule
import platform.test.motion.compose.feature
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.golden.FeatureCapture
import platform.test.motion.testing.createGoldenPathManager

@RunWith(AndroidJUnit4::class)
@MotionTest
class OverscrollToDismissTest {
    private val goldenPathManager =
        createGoldenPathManager("frameworks/base/packages/SystemUI/compose/core/tests/goldens")

    @get:Rule val motionTestRule = createFixedConfigurationComposeMotionTestRule(goldenPathManager)

    @Test
    fun detachGesture_onFirstPage_swipeRightDismisses() =
        motionTestRule.runTest {
            val motion =
                recordMotion(
                    { UnderTest() },
                    ComposeRecordingSpec(
                        performGesture {
                            swipeRight(startX = centerX, endX = centerX + 100.dp.toPx())
                        }
                    ) {
                        feature(hasTestTag("Page0"), xPositionInRoot)
                    },
                )
            assertThat(motion).timeSeriesMatchesGolden()
            assertThat(isDismissed).isTrue()
        }

    @Test
    fun detachGesture_onLastPage_swipeLeftDismisses() =
        motionTestRule.runTest {
            val motion =
                recordMotion(
                    { UnderTest(initialPage = 1) },
                    ComposeRecordingSpec(
                        performGesture {
                            swipeLeft(startX = centerX, endX = centerX - 100.dp.toPx())
                        }
                    ) {
                        feature(hasTestTag("Page1"), xPositionInRoot)
                    },
                )
            assertThat(motion).timeSeriesMatchesGolden()
            assertThat(isDismissed).isTrue()
        }

    @Test
    fun onFirstPage_swipeLeftScrollsPages() =
        motionTestRule.runTest {
            val motion =
                recordMotion(
                    { UnderTest() },
                    ComposeRecordingSpec(
                        performGesture {
                            swipeLeft(startX = centerX, endX = centerX - 100.dp.toPx())
                        }
                    ) {
                        feature(hasTestTag("Page0"), xPositionInRoot, "page0")
                        feature(hasTestTag("Page1"), xPositionInRoot, "page1")
                    },
                )
            assertThat(motion).timeSeriesMatchesGolden()
            assertThat(isDismissed).isFalse()
        }

    @Test
    fun draggingBack_reattaches() =
        motionTestRule.runTest {
            val motion =
                recordMotion(
                    { UnderTest() },
                    ComposeRecordingSpec(
                        performGesture {
                            val gestureDurationMillis = 300L
                            swipe(
                                curve = {
                                    val progress = it / gestureDurationMillis.toFloat()
                                    val x = sin(progress * Math.PI * .9f).toFloat() * 100.dp.toPx()
                                    Offset(centerX + x, centerY)
                                },
                                gestureDurationMillis,
                            )
                        }
                    ) {
                        feature(hasTestTag("Page0"), xPositionInRoot)
                    },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }

    private fun performGesture(gestureControl: TouchInjectionScope.() -> Unit) = MotionControl {
        val debugInspector = debugger.observed.single().debugInspector()
        try {
            performTouchInputAsync(onNodeWithTag("DismissContainer")) { gestureControl() }
            awaitCondition { !debugInspector.isAnimating && !pagerState.isScrollInProgress }
        } finally {
            debugInspector.dispose()
        }
    }

    private var isDismissed = false
    private val debugger = MotionValueDebugController()
    private lateinit var pagerState: PagerState

    @Composable
    fun UnderTest(
        modifier: Modifier = Modifier,
        initialPage: Int = 0,
        pageCount: Int = 2,
        isSwipingEnabled: Boolean = true,
    ) {
        pagerState = rememberPagerState(initialPage) { pageCount }
        CompositionLocalProvider(LocalMotionValueDebugController provides debugger) {
            Box(
                modifier =
                    Modifier.size(150.dp, 100.dp)
                        .background(Color.Blue)
                        .testTag("DismissContainer")
                        .overscrollToDismiss(
                            enabled = isSwipingEnabled,
                            onDismissed = { isDismissed = true },
                        )
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = isSwipingEnabled,
                    pageSpacing = 8.dp,
                    key = { it },
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex: Int ->
                    Box(
                        modifier =
                            Modifier.height(64.dp)
                                .fillMaxWidth()
                                .background(Color.Red)
                                .testTag("Page$pageIndex")
                    )
                }
            }
        }
    }

    companion object {
        val xPositionInRoot =
            FeatureCapture<SemanticsNode, Dp>("position") {
                with(it.layoutInfo.density) { it.positionInRoot.x.toDp().asDataPoint() }
            }
    }
}
