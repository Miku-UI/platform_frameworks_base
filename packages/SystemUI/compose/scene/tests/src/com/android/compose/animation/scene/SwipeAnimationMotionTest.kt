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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.compose.animation.scene

import android.platform.test.annotations.MotionTest
import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.SetFlagsRule
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.TestScenes.SceneB
import com.android.compose.animation.scene.TestScenes.SceneC
import com.android.compose.animation.scene.TestScenes.SceneD
import com.android.compose.gesture.effect.OffsetOverscrollEffect
import com.android.compose.gesture.effect.rememberOffsetOverscrollEffectFactory
import com.android.compose.modifiers.thenIf
import com.android.systemui.Flags.FLAG_STL_FLING_ANIMATION_CONSUME_OVERSHOOT
import com.android.systemui.Flags.stlFlingAnimationConsumeOvershoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.RecordedMotion
import platform.test.motion.compose.ComposeFeatureCaptures
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.MotionControlFn
import platform.test.motion.compose.createFixedConfigurationComposeMotionTestRule
import platform.test.motion.compose.feature
import platform.test.motion.compose.fetchAllSemanticsNodesMaybeCached
import platform.test.motion.compose.hasMotionTestValue
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.compose.values.MotionTestValueKey
import platform.test.motion.compose.values.motionTestValues
import platform.test.motion.golden.asDataPoint
import platform.test.motion.golden.dataPointType
import platform.test.motion.golden.feature
import platform.test.motion.testing.createGoldenPathManager
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.PathConfig
import platform.test.screenshot.PathElementNoContext

@MotionTest
@RunWith(ParameterizedAndroidJunit4::class)
class SwipeAnimationMotionTest(flags: FlagsParameterization) {

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(
                FLAG_STL_FLING_ANIMATION_CONSUME_OVERSHOOT
            )
        }
    }

    @get:Rule val setFlagsRule = SetFlagsRule().apply { setFlagsParameterization(flags) }

    private val pathConfig =
        PathConfig(
            PathElementNoContext("flag", isDir = false) {
                if (stlFlingAnimationConsumeOvershoot()) "flag_on" else "flag_off"
            }
        )
    private val goldenPaths =
        createGoldenPathManager(
            "frameworks/base/packages/SystemUI/compose/scene/tests/goldens",
            pathConfig,
        )

    @get:Rule val motionRule = createFixedConfigurationComposeMotionTestRule(goldenPaths)
    private val composeRule = motionRule.toolkit.composeContentTestRule

    @Test
    fun dragGesture_lowVelocity_animatesWithSpring() =
        motionRule.runTest {
            val motion =
                recordSceneTransition(initialScene = SceneA, recordScrollState = false) {
                    performTouchInputAsync(onNodeWithTag("stl")) {
                        swipeRight(endX = centerX, durationMillis = 250)
                    }
                }

            motionRule
                .assertThat(motion)
                .timeSeriesMatchesGolden("dragGesture_lowVelocity_animatesWithSpring")
        }

    @Test
    fun dragGesture_overdrag_animatesBack() =
        motionRule.runTest {
            val motion =
                recordSceneTransition(initialScene = SceneA) {
                    performTouchInputAsync(onNodeWithTag("stl")) {
                        swipeRight(startX = 10f, endX = width * 1.5f, durationMillis = 250)
                    }
                    awaitIdle()
                }

            motionRule
                .assertThat(motion)
                .timeSeriesMatchesGolden("dragGesture_overdrag_animatesBack")
        }

    @Test
    fun flingGesture_highVelocity_overshoots() =
        motionRule.runTest {
            val motion =
                recordSceneTransition(initialScene = SceneA, recordScrollState = false) {
                    performTouchInputAsync(onNodeWithTag("stl")) {
                        swipeWithVelocity(
                            start = centerLeft,
                            end = Offset(width * .8f, centerY),
                            durationMillis = 60,
                            endVelocity = 2000.dp.toPx(),
                        )
                    }
                }

            motionRule
                .assertThat(motion)
                .timeSeriesMatchesGolden("flingGesture_highVelocity_overshoots")
        }

    @Test
    fun dragGesture_onNestedScroll_lowVelocity_animatesWithSpring() =
        motionRule.runTest {
            val motion =
                recordSceneTransition(initialScene = SceneC) {
                    performTouchInputAsync(onNodeWithTag("stl")) {
                        swipeRight(endX = centerX, durationMillis = 250)
                    }
                }

            motionRule
                .assertThat(motion)
                .timeSeriesMatchesGolden(
                    "dragGesture_onNestedScroll_lowVelocity_animatesWithSpring"
                )
        }

    @Test
    fun flingGesture_onNestedScroll_highVelocity_overshoots() =
        motionRule.runTest {
            val motion =
                recordSceneTransition(initialScene = SceneC) {
                    performTouchInputAsync(onNodeWithTag("stl")) {
                        swipeWithVelocity(
                            start = centerLeft,
                            end = Offset(width * .8f, centerY),
                            durationMillis = 60,
                            endVelocity = 2000.dp.toPx(),
                        )
                    }
                }

            motionRule
                .assertThat(motion)
                .timeSeriesMatchesGolden("flingGesture_onNestedScroll_highVelocity_overshoots")
        }

    @Test
    fun dragGesture_onNestedScroll_swipesDisabled_lowVelocity_animatesWithSpring() =
        motionRule.runTest {
            val motion =
                recordSceneTransition(initialScene = SceneD) {
                    performTouchInputAsync(onNodeWithTag("stl")) {
                        swipeRight(endX = centerX, durationMillis = 250)
                    }
                }

            motionRule
                .assertThat(motion)
                .timeSeriesMatchesGolden(
                    "dragGesture_onNestedScroll_swipesDisabled_lowVelocity_animatesWithSpring"
                )
        }

    @Test
    fun flingGesture_onNestedScroll_swipesDisabled_highVelocity_overshoots() =
        motionRule.runTest {
            val motion =
                recordSceneTransition(initialScene = SceneD) {
                    performTouchInputAsync(onNodeWithTag("stl")) {
                        swipeWithVelocity(
                            start = centerLeft,
                            end = Offset(width * .8f, centerY),
                            durationMillis = 60,
                            endVelocity = 2000.dp.toPx(),
                        )
                    }
                }

            motionRule
                .assertThat(motion)
                .timeSeriesMatchesGolden(
                    "flingGesture_onNestedScroll_swipesDisabled_highVelocity_overshoots"
                )
        }

    val overscrollDistanceKey = MotionTestValueKey<Float>("overscrollDistance")
    val scrollValueKey = MotionTestValueKey<Int>("scrollValue")

    @Composable
    private fun ContentScope.SourceSceneFixed(alignment: Alignment) {
        Box(Modifier.fillMaxSize().background(Color.DarkGray)) {
            Box(
                Modifier.overscroll(horizontalOverscrollEffect)
                    .element(TestElements.Foo)
                    .motionTestValues {
                        val overscrollDistance =
                            (horizontalOverscrollEffect as OffsetOverscrollEffect)
                                .overscrollDistance
                        overscrollDistance exportAs overscrollDistanceKey
                    }
                    .size(10.dp)
                    .align(alignment)
                    .background(Color.Magenta)
            )
        }
    }

    @Composable
    private fun ContentScope.SourceSceneScrollable(
        alignment: Alignment,
        disableSwipesWhenScrolling: Boolean,
    ) {
        val scrollState =
            rememberScrollState(initial = with(LocalDensity.current) { 10.dp.roundToPx() })
        Box(
            Modifier.fillMaxSize()
                .background(Color.DarkGray)
                .thenIf(disableSwipesWhenScrolling) { Modifier.disableSwipesWhenScrolling() }
                .horizontalScroll(scrollState)
        ) {
            Box(
                Modifier.overscroll(horizontalOverscrollEffect)
                    .element(TestElements.Foo)
                    .motionTestValues {
                        val overscrollDistance =
                            (horizontalOverscrollEffect as OffsetOverscrollEffect)
                                .overscrollDistance
                        overscrollDistance exportAs overscrollDistanceKey
                        scrollState.value exportAs scrollValueKey
                    }
                    .size(width = 120.dp, 10.dp)
                    .align(alignment)
                    .background(Brush.linearGradient(listOf(Color.Green, Color.White)))
            )
        }
    }

    @Composable
    private fun ContentScope.TargetScene(alignment: Alignment) {
        Box(Modifier.fillMaxSize().background(Color.DarkGray)) {
            Box(
                Modifier.overscroll(horizontalOverscrollEffect)
                    .element(TestElements.Foo)
                    .motionTestValues {
                        val overscrollDistance =
                            (horizontalOverscrollEffect as OffsetOverscrollEffect)
                                .overscrollDistance
                        overscrollDistance exportAs overscrollDistanceKey
                    }
                    .size(10.dp)
                    .align(alignment)
                    .background(Color.Red)
            )
        }
    }

    private fun recordSceneTransition(
        initialScene: SceneKey,
        recordScrollState: Boolean = true,
        transitions: SceneTransitions = transitions {
            from(SceneA, to = SceneB)
            from(SceneC, to = SceneB)
            from(SceneD, to = SceneB)
        },
        recording: MotionControlFn,
    ): RecordedMotion {
        lateinit var state: MutableSceneTransitionLayoutState
        return motionRule.recordMotion(
            content = {
                state = rememberMutableSceneTransitionLayoutState(initialScene, transitions)

                val factory = rememberOffsetOverscrollEffectFactory()
                CompositionLocalProvider(LocalOverscrollFactory provides factory) {
                    Box(modifier = Modifier.padding(20.dp)) {
                        SceneTransitionLayout(
                            state = state,
                            modifier =
                                Modifier.size(100.dp).testTag("stl").background(Color.Yellow),
                            implicitTestTags = true,
                        ) {
                            scene(TestScenes.SceneA, userActions = mapOf(Swipe.Right to SceneB)) {
                                SourceSceneFixed(Alignment.TopStart)
                            }

                            scene(TestScenes.SceneC, userActions = mapOf(Swipe.Right to SceneB)) {
                                SourceSceneScrollable(
                                    Alignment.TopStart,
                                    disableSwipesWhenScrolling = false,
                                )
                            }
                            scene(TestScenes.SceneD, userActions = mapOf(Swipe.Right to SceneB)) {
                                SourceSceneScrollable(
                                    Alignment.TopStart,
                                    disableSwipesWhenScrolling = true,
                                )
                            }
                            scene(TestScenes.SceneB) { TargetScene(Alignment.TopEnd) }
                        }
                    }
                }
            },
            ComposeRecordingSpec(
                MotionControl {
                    recording()
                    awaitIdle()
                },
                recordBefore = false,
                recordAfter = false,
                timeSeriesCapture = {
                    on({
                        it.fetchAllSemanticsNodesMaybeCached(isElement(TestElements.Foo))
                            .firstOrNull { it.layoutInfo.isPlaced }
                    }) {
                        feature(FeatureCaptures.elementOffset)
                        feature(ComposeFeatureCaptures.positionInRoot)
                    }

                    feature(
                        overscrollDistanceKey,
                        Float.dataPointType,
                        matcher =
                            hasMotionTestValue(overscrollDistanceKey) and
                                SemanticsMatcher.inContent(SceneB),
                    )

                    if (recordScrollState) {

                        feature(
                            scrollValueKey,
                            Int.dataPointType,
                            matcher =
                                hasMotionTestValue(scrollValueKey) and
                                    (SemanticsMatcher.inContent(SceneC) or
                                        SemanticsMatcher.inContent(SceneD)),
                        )
                    }

                    on({ state.currentTransition }) {
                        feature("progress") { it.progress.asDataPoint() }
                        feature("dragOffset") {
                            checkNotNull(it.gestureContext).dragOffset.asDataPoint()
                        }
                    }
                },
            ),
        )
    }
}
