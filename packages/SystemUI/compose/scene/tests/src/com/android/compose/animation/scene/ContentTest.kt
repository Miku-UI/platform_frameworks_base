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

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.TestScenes.SceneB
import com.android.compose.animation.scene.TestScenes.SceneC
import com.android.compose.test.setContentAndCreateMainScope
import com.android.compose.test.transition
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun disableSwipesWhenScrolling() {
        lateinit var layoutImpl: SceneTransitionLayoutImpl
        rule.setContent {
            SceneTransitionLayoutForTesting(
                remember { MutableSceneTransitionLayoutStateForTests(SceneA) },
                onLayoutImpl = { layoutImpl = it },
            ) {
                scene(SceneA) {
                    Box(
                        Modifier.fillMaxSize()
                            .disableSwipesWhenScrolling()
                            .scrollable(rememberScrollableState { it }, Orientation.Vertical)
                    )
                }
            }
        }

        val content = layoutImpl.content(SceneA)
        assertThat(content.areNestedSwipesAllowed()).isTrue()
        rule.onRoot().performTouchInput {
            down(topLeft)
            moveBy(bottomLeft)
        }

        assertThat(content.areNestedSwipesAllowed()).isFalse()
        rule.onRoot().performTouchInput { up() }
        assertThat(content.areNestedSwipesAllowed()).isTrue()
    }

    @Test
    fun disableSwipesWhenScrolling_outerDragDisabled() {
        val state = rule.runOnUiThread { MutableSceneTransitionLayoutStateForTests(SceneA) }
        var consumeScrolls = true
        var touchSlop = 0f

        rule.setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
            SceneTransitionLayout(state) {
                scene(SceneA, mapOf(Swipe.Down to SceneB)) {
                    Box(
                        Modifier.fillMaxSize()
                            .disableSwipesWhenScrolling()
                            .scrollable(
                                rememberScrollableState { if (consumeScrolls) it else 0f },
                                Orientation.Vertical,
                            )
                    )
                }
                scene(SceneB) { Box(Modifier.fillMaxSize()) }
            }
        }

        // Draw down. The whole drag is consumed by the scrollable and the STL should still be idle.
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, touchSlop + 10f))
        }
        assertThat(state.currentTransition).isNull()

        // Continue dragging down but don't consume the scrolls. The STL should still be idle given
        // that we use disableSwipesWhenScrolling().
        consumeScrolls = false
        rule.onRoot().performTouchInput { moveBy(Offset(0f, 10f)) }
        assertThat(state.currentTransition).isNull()
    }

    @Test
    fun lifecycle() {
        @Composable
        fun OnLifecycle(f: (Lifecycle?) -> Unit) {
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle) {
                f(lifecycle)
                onDispose { f(null) }
            }
        }

        val state = rule.runOnUiThread { MutableSceneTransitionLayoutStateForTests(SceneA) }
        var lifecycleA: Lifecycle? = null
        var lifecycleB: Lifecycle? = null
        var lifecycleC: Lifecycle? = null

        val parentLifecycleOwner =
            rule.runOnUiThread {
                object : LifecycleOwner {
                    override val lifecycle = LifecycleRegistry(this)

                    init {
                        lifecycle.currentState = Lifecycle.State.RESUMED
                    }
                }
            }

        var composeContent by mutableStateOf(true)
        rule.setContent {
            if (!composeContent) return@setContent

            CompositionLocalProvider(LocalLifecycleOwner provides parentLifecycleOwner) {
                SceneTransitionLayout(state) {
                    scene(SceneA) { OnLifecycle { lifecycleA = it } }
                    scene(SceneB) { OnLifecycle { lifecycleB = it } }
                    scene(SceneC, alwaysCompose = true) { OnLifecycle { lifecycleC = it } }
                }
            }
        }

        // currentScene = A. B is not composed, C is CREATED.
        val parentLifecycle = parentLifecycleOwner.lifecycle
        assertThat(lifecycleA).isSameInstanceAs(parentLifecycle)
        assertThat(lifecycleA?.currentState).isEqualTo(Lifecycle.State.RESUMED)
        assertThat(lifecycleB).isNull()
        assertThat(lifecycleC?.currentState).isEqualTo(Lifecycle.State.CREATED)

        // currentScene = B. A is not composed, C is CREATED.
        rule.runOnUiThread { state.snapTo(SceneB) }
        rule.waitForIdle()
        assertThat(lifecycleA).isNull()
        assertThat(lifecycleB).isSameInstanceAs(parentLifecycle)
        assertThat(lifecycleB?.currentState).isEqualTo(Lifecycle.State.RESUMED)
        assertThat(lifecycleC?.currentState).isEqualTo(Lifecycle.State.CREATED)

        // currentScene = C. A and B are not composed, C is RESUMED.
        rule.runOnUiThread { state.snapTo(SceneC) }
        rule.waitForIdle()
        assertThat(lifecycleA).isNull()
        assertThat(lifecycleB).isNull()
        assertThat(lifecycleC?.currentState).isEqualTo(Lifecycle.State.RESUMED)

        // parentLifecycle = STARTED. A and B are not composed, C is STARTED.
        rule.runOnUiThread { parentLifecycleOwner.lifecycle.currentState = Lifecycle.State.STARTED }
        rule.waitForIdle()
        assertThat(lifecycleA?.currentState).isEqualTo(null)
        assertThat(lifecycleB?.currentState).isEqualTo(null)
        assertThat(lifecycleC?.currentState).isEqualTo(Lifecycle.State.STARTED)

        // currentScene = A. B is not composed, C is CREATED.
        rule.runOnUiThread { state.snapTo(SceneA) }
        rule.waitForIdle()
        assertThat(lifecycleA).isSameInstanceAs(parentLifecycle)
        assertThat(lifecycleA?.currentState).isEqualTo(Lifecycle.State.STARTED)
        assertThat(lifecycleB).isNull()
        assertThat(lifecycleC?.currentState).isEqualTo(Lifecycle.State.CREATED)

        // Remove the STL from composition. The lifecycle of scene C should be destroyed.
        val lastLifecycleC = lifecycleC
        composeContent = false
        rule.waitForIdle()
        assertThat(lifecycleC).isNull()
        assertThat(lastLifecycleC?.currentState).isEqualTo(Lifecycle.State.DESTROYED)
    }

    @Test
    fun isAlwaysComposedContentVisible() {
        @Composable
        fun ContentScope.Visibility(f: (Boolean) -> Unit) {
            val isVisible = isAlwaysComposedContentVisible()
            SideEffect { f(isVisible) }
        }

        var outerSceneAVisible by mutableStateOf(false)
        var outerSceneBVisible by mutableStateOf(false)
        var outerOverlayAVisible by mutableStateOf(false)
        var innerSceneAVisible by mutableStateOf(false)
        var innerSceneBVisible by mutableStateOf(false)
        var innerOverlayAVisible by mutableStateOf(false)

        val outerSceneA = SceneKey("OuterSceneA")
        val outerSceneB = SceneKey("OuterSceneB")
        val outerOverlayA = OverlayKey("OuterOverlayA")
        val innerSceneA = SceneKey("InnerSceneA")
        val innerSceneB = SceneKey("InnerSceneB")
        val innerOverlayA = OverlayKey("InnerOverlayA")

        val outerState =
            rule.runOnUiThread { MutableSceneTransitionLayoutStateForTests(outerSceneA) }
        val innerState =
            rule.runOnUiThread { MutableSceneTransitionLayoutStateForTests(innerSceneA) }

        val scope =
            rule.setContentAndCreateMainScope {
                SceneTransitionLayout(outerState) {
                    scene(outerSceneA, alwaysCompose = true) {
                        Visibility { outerSceneAVisible = it }

                        NestedSceneTransitionLayout(innerState, Modifier) {
                            scene(innerSceneA, alwaysCompose = true) {
                                Visibility { innerSceneAVisible = it }
                            }
                            scene(innerSceneB, alwaysCompose = true) {
                                Visibility { innerSceneBVisible = it }
                            }
                            overlay(innerOverlayA, alwaysCompose = true) {
                                Visibility { innerOverlayAVisible = it }
                            }
                        }
                    }
                    scene(outerSceneB, alwaysCompose = true) {
                        Visibility { outerSceneBVisible = it }
                    }
                    overlay(outerOverlayA, alwaysCompose = true) {
                        Visibility { outerOverlayAVisible = it }
                    }
                }
            }

        // Initial state.
        rule.waitForIdle()
        assertThat(outerSceneAVisible).isTrue()
        assertThat(innerSceneAVisible).isTrue()
        assertThat(outerSceneBVisible).isFalse()
        assertThat(outerOverlayAVisible).isFalse()
        assertThat(innerSceneBVisible).isFalse()
        assertThat(innerOverlayAVisible).isFalse()

        // Transition in inner layout: InnerSceneA -> InnerSceneB.
        val innerAToB = transition(innerSceneA, innerSceneB)
        scope.launch { innerState.startTransition(innerAToB) }
        rule.waitForIdle()
        assertThat(outerSceneAVisible).isTrue()
        assertThat(innerSceneAVisible).isTrue()
        assertThat(innerSceneBVisible).isTrue()

        // Finish transition.
        innerAToB.finish()
        rule.waitForIdle()
        assertThat(innerSceneAVisible).isFalse()
        assertThat(innerSceneBVisible).isTrue()

        // Transition to show inner overlay.
        val showInnerOverlay = transition(innerState.currentScene, innerOverlayA)
        scope.launch { innerState.startTransition(showInnerOverlay) }
        rule.waitForIdle()
        assertThat(innerSceneBVisible).isTrue()
        assertThat(innerOverlayAVisible).isTrue()

        // Finish transition.
        showInnerOverlay.finish()
        rule.waitForIdle()
        assertThat(innerSceneBVisible).isTrue()
        assertThat(innerOverlayAVisible).isTrue()

        // Transition in outer layout: OuterSceneA -> OuterSceneB.
        val outerAToB = transition(outerSceneA, outerSceneB)
        scope.launch { outerState.startTransition(outerAToB) }
        rule.waitForIdle()
        assertThat(outerSceneAVisible).isTrue()
        assertThat(outerSceneBVisible).isTrue()
        assertThat(innerSceneBVisible).isTrue()
        assertThat(innerOverlayAVisible).isTrue()

        // Finish transition.
        outerAToB.finish()
        rule.waitForIdle()
        assertThat(outerSceneAVisible).isFalse()
        assertThat(outerSceneBVisible).isTrue()
        assertThat(innerSceneBVisible).isFalse()
        assertThat(innerOverlayAVisible).isFalse()

        // Transition to show outer overlay.
        val showOuterOverlay = transition(outerState.currentScene, outerOverlayA)
        scope.launch { outerState.startTransition(showOuterOverlay) }
        rule.waitForIdle()
        assertThat(outerSceneBVisible).isTrue()
        assertThat(outerOverlayAVisible).isTrue()

        // Finish transition.
        showOuterOverlay.finish()
        rule.waitForIdle()
        assertThat(outerSceneBVisible).isTrue()
        assertThat(outerOverlayAVisible).isTrue()
    }

    @Test
    fun nestedStateInstances() {
        @Composable
        fun ContentScope.OnLayoutState(onLayoutState: (SceneTransitionLayoutState) -> Unit) {
            SideEffect { onLayoutState(layoutState) }
        }

        val outerState = rule.runOnUiThread { MutableSceneTransitionLayoutStateForTests(SceneA) }
        val middleState = rule.runOnUiThread { MutableSceneTransitionLayoutStateForTests(SceneB) }
        val innerState = rule.runOnUiThread { MutableSceneTransitionLayoutStateForTests(SceneC) }

        lateinit var outerStateFromScope: SceneTransitionLayoutState
        lateinit var middleStateFromScope: SceneTransitionLayoutState
        lateinit var innerStateFromScope: SceneTransitionLayoutState

        rule.setContent {
            SceneTransitionLayout(outerState) {
                scene(SceneA) {
                    OnLayoutState { outerStateFromScope = it }
                    NestedSceneTransitionLayout(middleState, Modifier) {
                        scene(SceneB) {
                            OnLayoutState { middleStateFromScope = it }
                            NestedSceneTransitionLayout(innerState, Modifier) {
                                scene(SceneC) {
                                    OnLayoutState { innerStateFromScope = it }
                                    Box(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
        }

        // This simple test on instances ensures that the layout state checks don't grow
        // quadratically with the number of ancestors.
        assertThat(outerStateFromScope).isSameInstanceAs(outerState)
        assertThat(middleStateFromScope).isNotSameInstanceAs(middleState)
        assertThat(innerStateFromScope).isNotSameInstanceAs(innerState)
        assertThat(middleStateFromScope).isInstanceOf(NestedSceneTransitionLayoutState::class.java)
        assertThat(innerStateFromScope).isInstanceOf(NestedSceneTransitionLayoutState::class.java)

        val nestedMiddle = middleStateFromScope as NestedSceneTransitionLayoutState
        val nestedInner = innerStateFromScope as NestedSceneTransitionLayoutState
        assertThat(nestedMiddle.delegate).isSameInstanceAs(middleState)
        assertThat(nestedInner.delegate).isSameInstanceAs(innerState)

        // Make sure that ancestors point to the base STL states and not the
        // NestedSceneTransitionLayoutState ones.
        assertThat(nestedMiddle.ancestors).containsExactly(outerState)
        assertThat(nestedInner.ancestors).containsExactly(outerState, middleState).inOrder()
    }

    @Test
    fun currentElementAlpha() {
        var lastAlpha: Float? = null

        val state =
            rule.runOnUiThread {
                MutableSceneTransitionLayoutStateForTests(
                    SceneA,
                    transitions =
                        transitions { from(SceneA, to = SceneB) { fade(TestElements.Foo) } },
                )
            }
        val scope =
            rule.setContentAndCreateMainScope {
                SceneTransitionLayout(state) {
                    scene(SceneA) {}
                    scene(SceneB) {
                        LaunchedEffect(Unit) {
                            snapshotFlow { TestElements.Foo.currentAlpha() }
                                .collect { lastAlpha = it }
                        }

                        Box(Modifier.element(TestElements.Foo).fillMaxSize())
                    }
                }
            }

        assertThat(lastAlpha).isNull()

        var progress by mutableStateOf(0f)
        scope.launch { state.startTransition(transition(SceneA, SceneB, progress = { progress })) }
        rule.waitForIdle()
        assertThat(lastAlpha).isWithin(0.01f).of(0f)

        progress = 0.25f
        rule.waitForIdle()
        assertThat(lastAlpha).isWithin(0.01f).of(0.25f)

        progress = 0.5f
        rule.waitForIdle()
        assertThat(lastAlpha).isWithin(0.01f).of(0.5f)

        progress = 0.75f
        rule.waitForIdle()
        assertThat(lastAlpha).isWithin(0.01f).of(0.75f)

        progress = 1f
        rule.waitForIdle()
        assertThat(lastAlpha).isWithin(0.01f).of(1f)
    }
}
