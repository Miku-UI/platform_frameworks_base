/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.compose.animation.scene.transformation

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.compose.animation.scene.MutableSceneTransitionLayoutStateForTests
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.SceneTransitionLayoutForTesting
import com.android.compose.animation.scene.SceneTransitionLayoutImpl
import com.android.compose.animation.scene.TestElements.Foo
import com.android.compose.animation.scene.TestScenes
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.TestScenes.SceneB
import com.android.compose.animation.scene.TestScenes.SceneC
import com.android.compose.animation.scene.and
import com.android.compose.animation.scene.inContent
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.compose.test.setContentAndCreateMainScope
import com.android.compose.test.transition
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NestedSceneTransitionLayoutTest {
    @get:Rule val rule = createComposeRule()

    // Parent scenes
    private val parentSceneA = SceneKey("ParentA")
    private val parentSceneB = SceneKey("ParentB")

    // Nested scenes
    private val nestedSceneC = SceneKey("NestedC")
    private val nestedSceneD = SceneKey("NestedD")

    private val testElement = ElementKey("TestElement")

    @Test
    // Regression test for b/442640840
    fun syncAncestorState_removesState_andDoesNotCrash() {
        lateinit var parentState: MutableSceneTransitionLayoutState
        lateinit var nestedState: MutableSceneTransitionLayoutState
        lateinit var coroutineScope: CoroutineScope

        rule.setContent {
            parentState =
                rememberMutableSceneTransitionLayoutState(
                    initialScene = parentSceneA,
                    transitions =
                        transitions {
                            from(parentSceneA, to = parentSceneB) {
                                spec = tween(durationMillis = 300)

                                // Transformation only applies to the element in ParentB.
                                // This ensures `isElementAffectedByTransition` is true.
                                fade(testElement and inContent(parentSceneB))
                            }
                        },
                )

            nestedState =
                rememberMutableSceneTransitionLayoutState(
                    initialScene = nestedSceneC,
                    transitions =
                        transitions {
                            from(nestedSceneC, to = nestedSceneD) {
                                spec = tween(durationMillis = 100)
                            }
                        },
                )
            coroutineScope = rememberCoroutineScope()

            SceneTransitionLayout(state = parentState) {
                scene(parentSceneA) {
                    // The element needs to be in parentSceneA but no transformation is defined
                    // for this element in this scene.
                    Box(Modifier.element(testElement))
                }

                scene(parentSceneB) {
                    NestedSceneTransitionLayout(
                        state = nestedState,
                        debugName = "NestedStl",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // The nested layout starts with the element present...
                        scene(nestedSceneC) { Box(Modifier.element(testElement)) }
                        // ... and will transition to a scene where it is absent.
                        scene(nestedSceneD) {}
                    }
                }
            }
        }

        // Trigger both transitions to create the race condition.
        // In previous code versions the parent transition would be selected and while it is still
        // running the nestedTransitions stops. This triggers `syncAncestorElementState()` to remove
        // the testElement from `stateByContent`. This was causing a crash in
        // `getTransformationContentKey` because the parent transition is now missing a state.
        rule.runOnIdle {
            parentState.setTargetScene(parentSceneB, coroutineScope)
            nestedState.setTargetScene(nestedSceneD, coroutineScope)
        }

        rule.waitForIdle()
    }

    @Test
    fun nestedStls_testZIndex() {
        var nullableLayoutImpl: SceneTransitionLayoutImpl? = null

        rule.setContent {
            SceneTransitionLayoutForTesting(
                state = MutableSceneTransitionLayoutStateForTests(TestScenes.SceneA)
            ) {
                scene(TestScenes.SceneA) {
                    NestedSceneTransitionLayoutForTesting(
                        MutableSceneTransitionLayoutStateForTests(TestScenes.SceneD),
                        Modifier,
                        onLayoutImpl = null,
                        debugName = "NestedStl",
                    ) {
                        scene(TestScenes.SceneC) {}
                        scene(TestScenes.SceneD) {
                            NestedSceneTransitionLayoutForTesting(
                                MutableSceneTransitionLayoutStateForTests(TestScenes.SceneE),
                                Modifier,
                                onLayoutImpl = { nullableLayoutImpl = it },
                                debugName = "NestedStl",
                            ) {
                                scene(TestScenes.SceneE) {}
                            }
                        }
                    }
                }
                scene(TestScenes.SceneB) {}
            }

            assertThat(nullableLayoutImpl?.content(TestScenes.SceneA)?.globalZIndex)
                .isEqualTo(1_000_000_000_000_000)
            assertThat(nullableLayoutImpl?.content(TestScenes.SceneB)?.globalZIndex)
                .isEqualTo(2_000_000_000_000_000)
            assertThat(nullableLayoutImpl?.content(TestScenes.SceneC)?.globalZIndex)
                .isEqualTo(1_001_000_000_000_000)
            assertThat(nullableLayoutImpl?.content(TestScenes.SceneD)?.globalZIndex)
                .isEqualTo(1_002_000_000_000_000)
            assertThat(nullableLayoutImpl?.content(TestScenes.SceneE)?.globalZIndex)
                .isEqualTo(1_002_001_000_000_000)
        }
    }

    @Test
    // Regression test for b/442640840.
    fun ancestorTransformationDefinedForNonExistentElement() {
        val parentState =
            rule.runOnUiThread {
                MutableSceneTransitionLayoutStateForTests(
                    SceneA,
                    transitions {
                        // Add a transformation that only applies to Foo in SceneB.
                        from(SceneA, to = SceneB) { fade(Foo and inContent(SceneB)) }
                    },
                )
            }

        val scope =
            rule.setContentAndCreateMainScope {
                SceneTransitionLayout(state = parentState) {
                    scene(SceneA) {
                        val childState = rememberMutableSceneTransitionLayoutState(SceneC)
                        NestedSceneTransitionLayout(state = childState, "NestedStl") {
                            scene(SceneC) { Box(Modifier.element(Foo).fillMaxSize()) }
                        }
                    }

                    // Don't have Foo in sceneB, so that A => B is not used to transform Foo.
                    scene(SceneB) { Box(Modifier.fillMaxSize()) }
                }
            }

        val transition = transition(SceneA, SceneB, progress = { 0.5f })
        scope.launch { parentState.setTargetScene(SceneB, this) }
        rule.waitForIdle()
    }
}
