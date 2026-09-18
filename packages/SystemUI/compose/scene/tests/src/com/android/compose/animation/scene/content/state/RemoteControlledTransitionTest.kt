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

package com.android.compose.animation.scene.content.state

import android.platform.test.annotations.MotionTest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.FeatureCaptures
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.TestElements
import com.android.compose.animation.scene.TestScenes
import com.android.compose.animation.scene.isElement
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.RecordedMotion
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.MotionControlScope
import platform.test.motion.compose.createFixedConfigurationComposeMotionTestRule
import platform.test.motion.compose.recordMotion
import platform.test.motion.golden.DataPointTypes
import platform.test.motion.golden.asDataPoint
import platform.test.motion.golden.feature
import platform.test.motion.testing.createGoldenPathManager

@RunWith(AndroidJUnit4::class)
@MotionTest
class RemoteControlledTransitionTest {

    private val goldenPaths =
        createGoldenPathManager("frameworks/base/packages/SystemUI/compose/scene/tests/goldens")

    private val testScope = TestScope()
    @get:Rule val motionRule = createFixedConfigurationComposeMotionTestRule(goldenPaths, testScope)
    private val composeRule = motionRule.toolkit.composeContentTestRule

    @Test
    fun changeScene_simplyCommit_commitsAndAnimatesToTargetScene() {
        val motion = recordSceneValueTransition { stlState, animationScope ->
            val rcTransition =
                TransitionState.createRemoteControlledSceneTransition(
                    fromScene = TestScenes.SceneA,
                    toScene = TestScenes.SceneB,
                ) {
                    true
                }

            composeRule.runOnUiThread {
                stlState.startTransitionImmediately(animationScope, rcTransition)
            }
            awaitCondition { !stlState.isTransitioning() }
        }

        motionRule.assertThat(motion).timeSeriesMatchesGolden()
    }

    @Test
    fun changeScene_customProgress_drivesAnimation() {
        val motion = recordSceneValueTransition { stlState, animationScope ->
            val rcTransition =
                TransitionState.createRemoteControlledSceneTransition(
                    fromScene = TestScenes.SceneA,
                    toScene = TestScenes.SceneB,
                ) {
                    Animatable(0f).animateTo(
                        1f,
                        animationSpec =
                            keyframes {
                                durationMillis = 200
                                0f at 0
                                .75f at 50
                                .25f at 100
                                1f at 200
                            },
                    ) {
                        progress = value
                    }

                    true
                }

            composeRule.runOnUiThread {
                stlState.startTransitionImmediately(animationScope, rcTransition)
            }
            awaitCondition { !stlState.isTransitioning() }
        }

        motionRule.assertThat(motion).timeSeriesMatchesGolden()
    }

    @Test
    fun changeScene_commitHalfwayThrough_animatesToTargetScene() {
        val motion = recordSceneValueTransition { stlState, animationScope ->
            val rcTransition =
                TransitionState.createRemoteControlledSceneTransition(
                    fromScene = TestScenes.SceneA,
                    toScene = TestScenes.SceneB,
                ) {
                    Animatable(0f).animateTo(0.5f) { progress = value }
                    true
                }

            composeRule.runOnUiThread {
                stlState.startTransitionImmediately(animationScope, rcTransition)
            }
            awaitCondition { !stlState.isTransitioning() }
        }

        motionRule.assertThat(motion).timeSeriesMatchesGolden()
    }

    @Test
    fun changeScene_abortHalfwayThrough_animatesToSourceScene() {
        val motion = recordSceneValueTransition { stlState, animationScope ->
            val rcTransition =
                TransitionState.createRemoteControlledSceneTransition(
                    fromScene = TestScenes.SceneA,
                    toScene = TestScenes.SceneB,
                ) {
                    Animatable(0f).animateTo(0.5f) { progress = value }
                    false
                }

            composeRule.runOnUiThread {
                stlState.startTransitionImmediately(animationScope, rcTransition)
            }
            awaitCondition { !stlState.isTransitioning() }
        }

        motionRule.assertThat(motion).timeSeriesMatchesGolden()
    }

    @Test
    fun changeScene_interrupted_cancelsTransition() {
        var isCancelled = false
        val motion = recordSceneValueTransition { stlState, animationScope ->
            val rcTransition =
                TransitionState.createRemoteControlledSceneTransition(
                    fromScene = TestScenes.SceneA,
                    toScene = TestScenes.SceneB,
                ) {
                    try {
                        Animatable(0f).animateTo(1f) { progress = value }
                        true
                    } catch (e: CancellationException) {
                        isCancelled = true
                        throw e
                    }
                }

            composeRule.runOnUiThread {
                stlState.startTransitionImmediately(animationScope, rcTransition)
            }

            awaitCondition { stlState.currentTransition?.progress?.let { it > .5f } ?: false }

            composeRule.runOnUiThread { stlState.setTargetScene(TestScenes.SceneC, animationScope) }
            awaitCondition { !stlState.isTransitioning() }
        }

        assertThat(isCancelled).isTrue()
        motionRule.assertThat(motion).timeSeriesMatchesGolden()
    }

    @Composable
    private fun ContentScope.TestScene(alignment: Alignment) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.element(TestElements.Foo).size(10.dp).align(alignment))
        }
    }

    val floatArrayType = DataPointTypes.listOf(DataPointTypes.float)

    private fun recordSceneValueTransition(
        recording:
            suspend MotionControlScope.(
                stlState: MutableSceneTransitionLayoutState, animationScope: CoroutineScope,
            ) -> Unit
    ): RecordedMotion {
        lateinit var state: MutableSceneTransitionLayoutState
        lateinit var animationScope: CoroutineScope
        return motionRule.recordMotion(
            content = {
                state = rememberMutableSceneTransitionLayoutState(TestScenes.SceneA)
                animationScope = rememberCoroutineScope()
                SceneTransitionLayout(
                    state,
                    modifier = Modifier.size(100.dp),
                    implicitTestTags = true,
                ) {
                    scene(TestScenes.SceneA) { TestScene(Alignment.TopStart) }
                    scene(TestScenes.SceneB) { TestScene(Alignment.TopEnd) }
                    scene(TestScenes.SceneC) { TestScene(Alignment.BottomStart) }
                }
            },
            ComposeRecordingSpec(
                MotionControl { recording(state, animationScope) },
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
                    on({ state }) {
                        feature("currentScene") { it.currentScene.debugName.asDataPoint() }
                        feature("isIdle") { it.isIdle().asDataPoint() }
                        on({ it.currentTransition }) {
                            feature("name") {
                                "${it.fromContent.debugName}->${it.toContent.debugName}"
                                    .asDataPoint()
                            }
                            feature("isInitiatedByUserInput") {
                                it.isInitiatedByUserInput.asDataPoint()
                            }
                            feature("isUserInputOngoing") { it.isUserInputOngoing.asDataPoint() }
                            feature("isProgressStable") { it.isProgressStable.asDataPoint() }
                            feature("progress") { it.progress.asDataPoint() }
                            feature("progressVelocity") { it.progressVelocity.asDataPoint() }
                        }
                    }
                },
            ),
        )
    }
}
