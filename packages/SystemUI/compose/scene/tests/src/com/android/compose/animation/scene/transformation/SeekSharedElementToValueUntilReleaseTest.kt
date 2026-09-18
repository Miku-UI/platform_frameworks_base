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

package com.android.compose.animation.scene.transformation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.MutableSceneTransitionLayoutStateForTests
import com.android.compose.animation.scene.SceneTransitionLayoutForTesting
import com.android.compose.animation.scene.TestElements.Foo
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.TestScenes.SceneB
import com.android.compose.animation.scene.isElement
import com.android.compose.animation.scene.transitions
import com.android.compose.test.assertSizeIsEqualTo
import com.android.compose.test.setContentAndCreateMainScope
import com.android.compose.test.transition
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@RunWith(AndroidJUnit4::class)
class SeekSharedElementToValueUntilReleaseTest {
    @get:Rule val rule = createComposeRule()

    @Composable
    private fun ContentScope.Foo(size: Dp, modifier: Modifier = Modifier) {
        Box(modifier.element(Foo).size(size))
    }

    @Test
    fun seekOffset() {
        val state =
            rule.runOnUiThread {
                MutableSceneTransitionLayoutStateForTests(
                    SceneA,
                    transitions {
                        from(SceneA, SceneB) {
                            seekSharedElementToOffsetUntilRelease(Foo) { _, _ ->
                                // During the drag (user input), interpolate to (0, 100).
                                Offset(0f, 100.dp.toPx())
                            }
                        }
                    },
                )
            }
        val scope =
            rule.setContentAndCreateMainScope {
                SceneTransitionLayoutForTesting(state) {
                    scene(SceneA) { Box(Modifier.fillMaxSize()) { Foo(size = 20.dp) } }
                    scene(SceneB) {
                        Box(Modifier.fillMaxSize()) {
                            Foo(size = 20.dp, Modifier.offset(30.dp, 30.dp))
                        }
                    }
                }
            }

        // Start the transition.
        var progress by mutableFloatStateOf(0f)
        var isUserInputOngoing by mutableStateOf(true)
        scope.launch {
            state.startTransition(
                transition(
                    from = SceneA,
                    to = SceneB,
                    isInitiatedByUserInput = true,
                    isUserInputOngoing = { isUserInputOngoing },
                    progress = { progress },
                )
            )
        }

        // At 0%: (0,0)
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(0.dp, 0.dp)

        // At 50%: Lerp((0,0), (0, 100), 0.5) = (0, 50).
        // Note: The standard SceneB target (30,30) is ignored during user input
        // because the transformation overrides it with the seek value.
        progress = 0.5f
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(0.dp, 50.dp)

        // At 100%: (0, 100).
        progress = 1f
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(0.dp, 100.dp)

        // Release should animate to SceneB end state.
        isUserInputOngoing = false
        rule.waitForIdle()
        rule.onNode(isElement(Foo, SceneB)).assertPositionInRootIsEqualTo(30.dp, 30.dp)
    }

    @Test
    fun seekSize() {
        val state =
            rule.runOnUiThread {
                MutableSceneTransitionLayoutStateForTests(
                    SceneA,
                    transitions {
                        from(SceneA, SceneB) {
                            seekSharedElementToSizeUntilRelease(Foo) { _, _ ->
                                // During drag, interpolate to 60dp.
                                IntSize(60.dp.roundToPx(), 60.dp.roundToPx())
                            }
                        }
                    },
                )
            }
        val scope =
            rule.setContentAndCreateMainScope {
                SceneTransitionLayoutForTesting(state) {
                    scene(SceneA) { Box(Modifier.fillMaxSize()) { Foo(size = 20.dp) } }
                    scene(SceneB) { Box(Modifier.fillMaxSize()) { Foo(size = 30.dp) } }
                }
            }

        // Start the transition.
        var progress by mutableFloatStateOf(0f)
        var isUserInputOngoing by mutableStateOf(true)
        scope.launch {
            state.startTransition(
                transition(
                    from = SceneA,
                    to = SceneB,
                    isInitiatedByUserInput = true,
                    isUserInputOngoing = { isUserInputOngoing },
                    progress = { progress },
                )
            )
        }

        // At 0%: 20dp.
        rule.onNode(isElement(Foo, SceneB)).assertSizeIsEqualTo(20.dp)

        // At 50%: Lerp(20dp, 60dp, 0.5) = 40dp.
        progress = 0.5f
        rule.onNode(isElement(Foo, SceneB)).assertSizeIsEqualTo(40.dp)

        // At 100%: 60dp.
        progress = 1f
        rule.onNode(isElement(Foo, SceneB)).assertSizeIsEqualTo(60.dp)

        // Release should animate to SceneB end state.
        isUserInputOngoing = false
        rule.waitForIdle()
        rule.onNode(isElement(Foo, SceneB)).assertSizeIsEqualTo(30.dp)
    }
}
