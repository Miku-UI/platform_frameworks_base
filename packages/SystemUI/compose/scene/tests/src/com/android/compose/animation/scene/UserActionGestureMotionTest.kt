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

package com.android.compose.animation.scene

import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.MotionTest
import android.platform.test.flag.junit.SetFlagsRule
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.TestScenes.SceneB
import com.android.compose.animation.scene.mechanics.UserActionGesture
import com.android.compose.animation.scene.mechanics.UserActionGestureFlag
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.builder.spatialDirectionalMotionSpec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.RecordedMotion
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.MotionControlFn
import platform.test.motion.compose.createFixedConfigurationComposeMotionTestRule
import platform.test.motion.compose.recordMotion
import platform.test.motion.golden.DataPointTypes
import platform.test.motion.golden.asDataPoint
import platform.test.motion.golden.feature
import platform.test.motion.testing.createGoldenPathManager

@RunWith(AndroidJUnit4::class)
@MotionTest
@EnableFlags(UserActionGestureFlag.FLAG_NAME)
class UserActionGestureMotionTest {

    private val goldenPaths =
        createGoldenPathManager("frameworks/base/packages/SystemUI/compose/scene/tests/goldens")

    @get:Rule val motionRule = createFixedConfigurationComposeMotionTestRule(goldenPaths)
    private val composeRule = motionRule.toolkit.composeContentTestRule

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val userActionGesture = UserActionGesture { _, _, _, distance ->
        MotionSpec(
            spatialDirectionalMotionSpec {
                target(breakpoint = 0f, from = 0f, to = distance * .1f)
                target(breakpoint = distance * .5f, from = distance * .5f, to = distance)
                identity(breakpoint = distance)
            }
        )
    }

    @Test
    fun userActionGesture_swipeRight_asDefined() {
        val motion =
            recordSceneTransition(
                initialScene = SceneA,
                transitions =
                    transitions { from(SceneA, to = SceneB) { distance = userActionGesture } },
            ) {
                performTouchInputAsync(onNodeWithTag("stl")) { swipeRight(durationMillis = 500) }
            }

        motionRule.assertThat(motion).timeSeriesMatchesGolden()
    }

    @Test
    fun userActionGesture_swipeLeft_reversedDefinition() {
        val motion =
            recordSceneTransition(
                initialScene = SceneB,
                transitions =
                    transitions { from(SceneA, to = SceneB) { distance = userActionGesture } },
            ) {
                performTouchInputAsync(onNodeWithTag("stl")) { swipeLeft(durationMillis = 500) }
            }

        motionRule.assertThat(motion).timeSeriesMatchesGolden()
    }

    @Test
    fun userActionGesture_swipeLeft_asDefined() {
        val motion =
            recordSceneTransition(
                initialScene = SceneB,
                transitions =
                    transitions { from(SceneB, to = SceneA) { distance = userActionGesture } },
            ) {
                performTouchInputAsync(onNodeWithTag("stl")) { swipeLeft(durationMillis = 500) }
            }

        motionRule.assertThat(motion).timeSeriesMatchesGolden()
    }

    @Test
    fun userActionGesture_swipeRight_reversedDefinition() {
        val motion =
            recordSceneTransition(
                initialScene = SceneA,
                transitions =
                    transitions { from(SceneB, to = SceneA) { distance = userActionGesture } },
            ) {
                performTouchInputAsync(onNodeWithTag("stl")) { swipeRight(durationMillis = 500) }
            }

        motionRule.assertThat(motion).timeSeriesMatchesGolden()
    }

    @Composable
    private fun ContentScope.TestScene(alignment: Alignment) {
        Box(Modifier.fillMaxSize().background(Color.DarkGray)) {
            Box(
                Modifier.element(TestElements.Foo)
                    .size(10.dp)
                    .align(alignment)
                    .background(Color.Red)
            )
        }
    }

    val floatArrayType = DataPointTypes.listOf(DataPointTypes.float)

    private fun recordSceneTransition(
        initialScene: SceneKey,
        transitions: SceneTransitions,
        recording: MotionControlFn,
    ): RecordedMotion {
        lateinit var state: MutableSceneTransitionLayoutState
        return motionRule.recordMotion(
            content = {
                state = rememberMutableSceneTransitionLayoutState(initialScene, transitions)
                SceneTransitionLayout(
                    state = state,
                    modifier = Modifier.size(100.dp).testTag("stl").background(Color.Yellow),
                    implicitTestTags = true,
                ) {
                    scene(TestScenes.SceneA, userActions = mapOf(Swipe.Right to SceneB)) {
                        TestScene(Alignment.TopStart)
                    }
                    scene(TestScenes.SceneB, userActions = mapOf(Swipe.Left to SceneA)) {
                        TestScene(Alignment.TopEnd)
                    }
                }
            },
            ComposeRecordingSpec(
                MotionControl {
                    recording()
                    awaitCondition { !state.isTransitioning() }
                },
                recordBefore = false,
                recordAfter = false,
                timeSeriesCapture = {
                    on({
                        it.onAllNodes(isElement(TestElements.Foo))
                            .fetchSemanticsNodes()
                            .filter { it.layoutInfo.isPlaced }
                            .firstOrNull()
                    }) {
                        feature(FeatureCaptures.elementOffset)
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
