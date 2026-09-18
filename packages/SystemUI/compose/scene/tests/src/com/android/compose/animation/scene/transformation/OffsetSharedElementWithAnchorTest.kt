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

package com.android.compose.animation.scene.transformation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.test.assertPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.MutableSceneTransitionLayoutStateForTests
import com.android.compose.animation.scene.SceneTransitionLayoutForTesting
import com.android.compose.animation.scene.TestElements.Bar
import com.android.compose.animation.scene.TestElements.Foo
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.TestScenes.SceneB
import com.android.compose.animation.scene.isElement
import com.android.compose.animation.scene.transitions
import com.android.compose.test.setContentAndCreateMainScope
import com.android.compose.test.transition
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OffsetSharedElementWithAnchorTest {
    @get:Rule val rule = createComposeRule()

    @Composable
    private fun ContentScope.Foo(modifier: Modifier = Modifier) {
        Box(modifier.element(Foo).size(20.dp))
    }

    @Composable
    private fun ContentScope.Bar(modifier: Modifier = Modifier) {
        Box(modifier.element(Bar).size(20.dp))
    }

    @Test
    fun slideUpFromAnchor() {
        val state =
            rule.runOnUiThread {
                MutableSceneTransitionLayoutStateForTests(
                    SceneA,
                    transitions {
                        from(SceneA, SceneB) {
                            offsetSharedElementWithAnchor(Foo, Bar) { from, to, fromAnchor, _ ->
                                val animatedOffset = lerp(fromAnchor, to, transition.progress)
                                Offset(animatedOffset.x, minOf(from.y, animatedOffset.y))
                            }
                        }
                    },
                )
            }
        val scope =
            rule.setContentAndCreateMainScope {
                SceneTransitionLayoutForTesting(state) {
                    scene(SceneA) {
                        Box(Modifier.fillMaxSize()) {
                            Foo(Modifier.offset(0.dp, 20.dp))
                            Bar(Modifier.offset(0.dp, 40.dp))
                        }
                    }
                    scene(SceneB) {
                        Box(Modifier.fillMaxSize()) {
                            Foo(Modifier.offset(0.dp, 0.dp))
                            Bar(Modifier.offset(0.dp, 0.dp))
                        }
                    }
                }
            }

        var progress by mutableFloatStateOf(0f)
        scope.launch {
            state.startTransition(transition(from = SceneA, to = SceneB, progress = { progress }))
        }

        // At 0% Foo is at its original position from SceneA.
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(0.dp, 20.dp)

        // At 25%, Foo should be at (0, 40) - (0, 40) * 0,25 = (0, 30),
        // but that's lower than its original position, so Foo stays at its start position.
        progress = 0.5f
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(0.dp, 20.dp)

        // At 50%, Foo should be at (0, 40) - (0, 40) * 0,5 = (0, 20),
        // but that's the same to its original position, , so Foo stays at its start position.
        progress = 0.5f
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(0.dp, 20.dp)

        // At 75%, Foo should be at (0, 40) - (0, 40) * 0,75 = (0, 10).
        // In other words Bar moves up (by 0, 30) and starts moving Foo (by 0, 10) from its origin.
        progress = 0.75f
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(0.dp, 10.dp)

        // At 100%, Foo should be at its final position.
        progress = 1f
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(0.dp, 0.dp)
    }

    @Test
    fun slideDownFromAnchor() {
        val state =
            rule.runOnUiThread {
                MutableSceneTransitionLayoutStateForTests(
                    SceneA,
                    transitions {
                        from(SceneA, SceneB) {
                            offsetSharedElementWithAnchor(Foo, Bar) { from, to, fromAnchor, _ ->
                                val animatedOffset = lerp(fromAnchor, to, transition.progress)
                                Offset(animatedOffset.x, maxOf(from.y, animatedOffset.y))
                            }
                        }
                    },
                )
            }
        val scope =
            rule.setContentAndCreateMainScope {
                SceneTransitionLayoutForTesting(state) {
                    scene(SceneA) {
                        Box(Modifier.fillMaxSize()) {
                            Foo(Modifier.offset(0.dp, 10.dp))
                            Bar(Modifier.offset(0.dp, 0.dp))
                        }
                    }
                    scene(SceneB) {
                        Box(Modifier.fillMaxSize()) {
                            Foo(Modifier.offset(0.dp, 40.dp))
                            Bar(Modifier.offset(0.dp, 40.dp))
                        }
                    }
                }
            }

        var progress by mutableFloatStateOf(0f)
        scope.launch {
            state.startTransition(transition(from = SceneA, to = SceneB, progress = { progress }))
        }

        // At 0% Foo is at its original position.
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(0.dp, 10.dp)

        // At 50%, the animated offset is halfway to the target, so Foo has moved down.
        progress = 0.5f
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(0.dp, 20.dp)

        // At 100%, Foo should be at its final position.
        progress = 1f
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(0.dp, 40.dp)
    }

    @Test
    fun diagonalMovementTracksAnchor() {
        val state =
            rule.runOnUiThread {
                MutableSceneTransitionLayoutStateForTests(
                    SceneA,
                    transitions {
                        from(SceneA, SceneB) {
                            offsetSharedElementWithAnchor(Foo, Bar) { from, _, fromAnchor, toAnchor
                                ->
                                val anchorDelta =
                                    lerp(fromAnchor, toAnchor, transition.progress) - fromAnchor
                                from + anchorDelta
                            }
                        }
                    },
                )
            }
        val scope =
            rule.setContentAndCreateMainScope {
                SceneTransitionLayoutForTesting(state) {
                    scene(SceneA) {
                        Box(Modifier.fillMaxSize()) {
                            Foo(Modifier.offset(10.dp, 10.dp))
                            Bar(Modifier.offset(20.dp, 20.dp))
                        }
                    }
                    scene(SceneB) {
                        Box(Modifier.fillMaxSize()) {
                            Foo(Modifier.offset(50.dp, 50.dp))
                            Bar(Modifier.offset(60.dp, 60.dp))
                        }
                    }
                }
            }

        var progress by mutableFloatStateOf(0f)
        scope.launch {
            state.startTransition(transition(from = SceneA, to = SceneB, progress = { progress }))
        }

        // At 0%, Foo is at its original position.
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(10.dp, 10.dp)

        // At 50%, the anchor has moved by (20, 20), so Foo should have moved by the same amount,
        // placing it at (30, 30).
        progress = 0.5f
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(30.dp, 30.dp)

        // At 100%, Foo should be at its final position.
        progress = 1f
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(50.dp, 50.dp)
    }

    @Test
    fun stationaryAnchor() {
        val state =
            rule.runOnUiThread {
                MutableSceneTransitionLayoutStateForTests(
                    SceneA,
                    transitions {
                        from(SceneA, SceneB) {
                            offsetSharedElementWithAnchor(Foo, Bar) { from, to, _, _ ->
                                // Anchor not used, so this is a normal lerp.
                                lerp(from, to, transition.progress)
                            }
                        }
                    },
                )
            }
        val scope =
            rule.setContentAndCreateMainScope {
                SceneTransitionLayoutForTesting(state) {
                    scene(SceneA) {
                        Box(Modifier.fillMaxSize()) {
                            Foo(Modifier.offset(0.dp, 0.dp))
                            Bar(Modifier.offset(50.dp, 50.dp))
                        }
                    }
                    scene(SceneB) {
                        Box(Modifier.fillMaxSize()) {
                            Foo(Modifier.offset(100.dp, 100.dp))
                            Bar(Modifier.offset(50.dp, 50.dp))
                        }
                    }
                }
            }

        var progress by mutableFloatStateOf(0f)
        scope.launch {
            state.startTransition(transition(from = SceneA, to = SceneB, progress = { progress }))
        }

        // At 0%, Foo is at its original position.
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(0.dp, 0.dp)

        // At 50%, Foo should be halfway to its destination.
        progress = 0.5f
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(50.dp, 50.dp)

        // At 100%, Foo should be at its final position.
        progress = 1f
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(100.dp, 100.dp)
    }
}
