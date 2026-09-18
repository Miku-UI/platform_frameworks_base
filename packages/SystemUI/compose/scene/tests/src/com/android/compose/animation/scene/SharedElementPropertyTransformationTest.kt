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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.TestElements.Foo
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.TestScenes.SceneB
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.animation.scene.transformation.CustomSharedPropertyTransformation
import com.android.compose.animation.scene.transformation.InterpolatedSharedPropertyTransformation
import com.android.compose.animation.scene.transformation.PropertyTransformation
import com.android.compose.animation.scene.transformation.PropertyTransformationScope
import com.android.compose.test.assertSizeIsEqualTo
import com.android.compose.test.setContentAndCreateMainScope
import com.android.compose.test.transition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedElementPropertyTransformationTest {
    @get:Rule val rule = createComposeRule()

    @Composable
    private fun ContentScope.Foo(size: Dp, modifier: Modifier = Modifier) {
        Box(modifier.element(Foo).size(size))
    }

    @Test
    fun customTransformation() {
        val state =
            rule.runOnUiThread {
                MutableSceneTransitionLayoutStateForTests(
                    SceneA,
                    transitions {
                        from(SceneA, SceneB) {
                            // Set Foo at (0, 60) * progress during the transition.
                            transformation(Foo) {
                                object : CustomSharedPropertyTransformation<Offset> {
                                    override val property = PropertyTransformation.Property.Offset

                                    override fun PropertyTransformationScope.transform(
                                        element: ElementKey,
                                        transition: TransitionState.Transition,
                                        transitionScope: CoroutineScope,
                                        fromValue: Offset,
                                        toValue: Offset,
                                    ): Offset = Offset(0f, 60.dp.toPx() * transition.progress)
                                }
                            }
                        }
                    },
                )
            }
        val scope =
            rule.setContentAndCreateMainScope {
                SceneTransitionLayoutForTesting(state) {
                    scene(SceneA) {
                        // A 20x20dp Box at (0, 0).
                        Box(Modifier.fillMaxSize()) { Foo(size = 20.dp) }
                    }
                    scene(SceneB) {
                        // A 40x40dp Box at (30, 30).
                        Box(Modifier.fillMaxSize()) {
                            Foo(size = 40.dp, Modifier.offset(30.dp, 30.dp))
                        }
                    }
                }
            }

        rule
            .onNode(isElement(Foo, SceneA))
            .assertPositionInRootIsEqualTo(0.dp, 0.dp)
            .assertSizeIsEqualTo(20.dp)
        rule.onNode(isElement(Foo, SceneB)).assertDoesNotExist()

        // Start the transition.
        var progress by mutableFloatStateOf(0f)
        scope.launch { state.startTransition(transition(SceneA, SceneB, progress = { progress })) }

        // Starting offset is (0, 0).
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(0.dp, 0.dp)
            .assertSizeIsEqualTo(20.dp)

        // Offset to (0, 60) * progress at 25% = (0, 15).
        progress = 0.25f
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(0.dp, 15.dp)
            .assertSizeIsEqualTo(25.dp)

        // Offset to (0, 60) * progress at 50% = (0, 30).
        progress = 0.5f
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(0.dp, 30.dp)
            .assertSizeIsEqualTo(30.dp)

        // Offset to (0, 60) * progress at 100% = (0, 60).
        progress = 1f
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(0.dp, 60.dp)
            .assertSizeIsEqualTo(40.dp)
    }

    @Test
    fun customPreview() {
        val state =
            rule.runOnUiThread {
                MutableSceneTransitionLayoutStateForTests(
                    SceneA,
                    transitions {
                        from(
                            SceneA,
                            SceneB,
                            preview = {
                                // Set Foo at (0, 60) * previewProgress during the transition.
                                transformation(Foo) {
                                    object : CustomSharedPropertyTransformation<Offset> {
                                        override val property =
                                            PropertyTransformation.Property.Offset

                                        override fun PropertyTransformationScope.transform(
                                            element: ElementKey,
                                            transition: TransitionState.Transition,
                                            transitionScope: CoroutineScope,
                                            fromValue: Offset,
                                            toValue: Offset,
                                        ): Offset {
                                            return Offset(
                                                0f,
                                                60.dp.toPx() * transition.previewProgress,
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    },
                )
            }
        val scope =
            rule.setContentAndCreateMainScope {
                SceneTransitionLayoutForTesting(state) {
                    scene(SceneA) {
                        // A 20x20dp Box at (0, 0).
                        Box(Modifier.fillMaxSize()) { Foo(size = 20.dp) }
                    }
                    scene(SceneB) {
                        // A 40x40dp Box at (30, 30).
                        Box(Modifier.fillMaxSize()) {
                            Foo(size = 40.dp, Modifier.offset(30.dp, 30.dp))
                        }
                    }
                }
            }

        rule
            .onNode(isElement(Foo, SceneA))
            .assertPositionInRootIsEqualTo(0.dp, 0.dp)
            .assertSizeIsEqualTo(20.dp)
        rule.onNode(isElement(Foo, SceneB)).assertDoesNotExist()

        // Start the transition.
        var isInPreviewStage by mutableStateOf(true)
        var progress by mutableFloatStateOf(0f)
        var previewProgress by mutableFloatStateOf(0f)
        scope.launch {
            state.startTransition(
                transition(
                    SceneA,
                    SceneB,
                    isInitiatedByUserInput = true,
                    progress = { progress },
                    previewProgress = { previewProgress },
                    isInPreviewStage = { isInPreviewStage },
                )
            )
        }

        // Starting offset is (0, 0).
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(0.dp, 0.dp)
            .assertSizeIsEqualTo(20.dp)

        // Preview offset to (0, 60) * previewProgress at 25% = (0, 15). Size remains unchanged and
        // stays at 20dp.
        previewProgress = 0.25f
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(0.dp, 15.dp)
            .assertSizeIsEqualTo(20.dp)

        // Preview offset to (0, 60) * previewProgress at 50% = (0, 30).
        previewProgress = 0.5f
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(0.dp, 30.dp)
            .assertSizeIsEqualTo(20.dp)

        // Stop the preview.
        isInPreviewStage = false

        // Animating from preview offset (0, 30) to target offset (30, 30) at 50% = (15, 30). Size
        // starts to interpolate from 20dp to 40dp.
        progress = 0.5f
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(15.dp, 30.dp)
            .assertSizeIsEqualTo(30.dp)

        progress = 1f
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(30.dp, 30.dp)
            .assertSizeIsEqualTo(40.dp)
    }

    @Test
    fun interpolatedPreview() {
        val state =
            rule.runOnUiThread {
                MutableSceneTransitionLayoutStateForTests(
                    SceneA,
                    transitions {
                        from(
                            SceneA,
                            SceneB,
                            preview = {
                                // Set Foo at (0, 60) * progress during the transition.
                                transformation(Foo) {
                                    object : InterpolatedSharedPropertyTransformation<Offset> {
                                        override val property =
                                            PropertyTransformation.Property.Offset

                                        override fun PropertyTransformationScope.targetPreviewValue(
                                            element: ElementKey,
                                            transition: TransitionState.Transition,
                                            fromValue: Offset,
                                            toValue: Offset,
                                        ): Offset = Offset(0f, 60.dp.toPx())
                                    }
                                }
                            },
                        )
                    },
                )
            }
        val scope =
            rule.setContentAndCreateMainScope {
                SceneTransitionLayoutForTesting(state) {
                    scene(SceneA) {
                        // A 20x20dp Box at (0, 0).
                        Box(Modifier.fillMaxSize()) { Foo(size = 20.dp) }
                    }
                    scene(SceneB) {
                        // A 40x40dp Box at (30, 30).
                        Box(Modifier.fillMaxSize()) {
                            Foo(size = 40.dp, Modifier.offset(30.dp, 30.dp))
                        }
                    }
                }
            }

        rule
            .onNode(isElement(Foo, SceneA))
            .assertPositionInRootIsEqualTo(0.dp, 0.dp)
            .assertSizeIsEqualTo(20.dp)
        rule.onNode(isElement(Foo, SceneB)).assertDoesNotExist()

        // Start the transition.
        var isInPreviewStage by mutableStateOf(true)
        var progress by mutableFloatStateOf(0f)
        var previewProgress by mutableFloatStateOf(0f)
        scope.launch {
            state.startTransition(
                transition(
                    SceneA,
                    SceneB,
                    isInitiatedByUserInput = true,
                    progress = { progress },
                    previewProgress = { previewProgress },
                    isInPreviewStage = { isInPreviewStage },
                )
            )
        }

        // Starting offset is (0, 0).
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(0.dp, 0.dp)
            .assertSizeIsEqualTo(20.dp)

        // Preview offset to (0, 60) at 25% = (0, 15). Size remains unchanged and stays at 20dp.
        previewProgress = 0.25f
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(0.dp, 15.dp)
            .assertSizeIsEqualTo(20.dp)

        // Preview offset to (0, 60) at 50% = (0, 30).
        previewProgress = 0.5f
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(0.dp, 30.dp)
            .assertSizeIsEqualTo(20.dp)

        // Stop the preview.
        isInPreviewStage = false

        // Animating from preview offset (0, 30) to target offset (30, 30) at 50% = (15, 30). Size
        // starts to interpolate from 20dp to 40dp.
        progress = 0.5f
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(15.dp, 30.dp)
            .assertSizeIsEqualTo(30.dp)

        progress = 1f
        rule.onNode(isElement(Foo, SceneA)).assertIsNotDisplayed()
        rule
            .onNode(isElement(Foo, SceneB))
            .assertPositionInRootIsEqualTo(30.dp, 30.dp)
            .assertSizeIsEqualTo(40.dp)
    }
}
