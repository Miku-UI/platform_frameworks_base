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

package com.android.compose.animation.scene.debugger

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.ValueKey
import com.android.compose.animation.scene.animateContentColorAsState
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import kotlinx.coroutines.CoroutineScope

object ParentSTL {
    object Scenes {
        val SceneA = SceneKey("SceneA")
        val SceneB = SceneKey("SceneBWithVeryLongDebugName")
    }

    object Overlays {
        val OverlayA = OverlayKey("OverlayA")
    }
}

object ChildSTL {
    object Scenes {
        val NestedTop = SceneKey("NestedTop")
        val NestedBottom = SceneKey("NestedBottom")
    }
}

object Elements {
    val Shared = ElementKey("Shared")
    val NotShared = ElementKey("NotShared")
    val NotShared2 = ElementKey("NotShared2")
    val Container = ElementKey("ContainerWithVeryLongDebugName")
    val Child = ElementKey("Child")
}

object ValueKeys {
    val ColorContentKey = ValueKey("Color")
}

@Composable
fun DebugPreviewStl(modifier: Modifier = Modifier) {
    Column(modifier) {
        val state =
            rememberMutableSceneTransitionLayoutState(
                ParentSTL.Scenes.SceneA,
                initialOverlays = setOf(ParentSTL.Overlays.OverlayA),
                transitions =
                    remember {
                        transitions {
                            from(ParentSTL.Scenes.SceneA, to = ParentSTL.Scenes.SceneB) {
                                spec = tween(1500)
                                translate(Elements.NotShared, y = (-20).dp)
                                fade(Elements.NotShared)
                                scaleSize(Elements.NotShared, 0.5f, 0.5f)
                            }
                            from(ParentSTL.Overlays.OverlayA) {
                                spec = tween(1000)
                                fade(Elements.Container)
                            }
                        }
                    },
            )
        val childState =
            rememberMutableSceneTransitionLayoutState(
                ChildSTL.Scenes.NestedTop,
                transitions =
                    remember {
                        transitions {
                            from(ChildSTL.Scenes.NestedTop, to = ChildSTL.Scenes.NestedBottom) {
                                spec = tween(1500)
                                translate(Elements.NotShared, x = 20.dp)
                                fade(Elements.NotShared)
                                scaleSize(Elements.NotShared, 0.5f, 0.5f)

                                translate(Elements.NotShared2, y = 20.dp)
                                scaleSize(Elements.NotShared2, 1.5f, 1.5f)
                            }
                        }
                    },
            )
        val scope = rememberCoroutineScope()

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OptionButton(text = "Toggle ParentStl", onClick = { state.toggleParentScene(scope) })
            OptionButton(
                text = "Toggle NestedStl",
                onClick = { childState.toggleNestedScene(scope) },
            )
            OptionButton(
                text = "Toggle OverlayA",
                onClick = { state.toggleOverlay(ParentSTL.Overlays.OverlayA, scope) },
            )
        }

        SceneTransitionLayout(
            state,
            Modifier.weight(1f).clickable(null, null) { state.toggleParentScene(scope) },
            debugName = "ParentSTL",
        ) {
            scene(ParentSTL.Scenes.SceneA) {
                Box(Modifier.fillMaxSize()) {
                    ChildSTL(
                        childState,
                        Modifier.align(Alignment.TopEnd)
                            .fillMaxWidth(fraction = 0.5f)
                            .fillMaxHeight()
                            .padding(24.dp),
                    )
                }
            }
            scene(ParentSTL.Scenes.SceneB) {
                Box(Modifier.fillMaxSize()) {
                    SharedElement(Color.Magenta, Modifier.size(30.dp).align(Alignment.TopStart))
                }
            }
            overlay(
                ParentSTL.Overlays.OverlayA,
                alignment = Alignment.CenterStart,
                isModal = false,
            ) {
                Box(Modifier.size(200.dp)) {
                    Box(
                        Modifier.align(Alignment.BottomStart)
                            .size(150.dp)
                            .element(Elements.Container)
                            .background(Color.LightGray)
                    ) {
                        Box(Modifier.fillMaxSize().padding(50.dp).element(Elements.Child)) {}
                    }
                    Text(text = "OverlayA", modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun RowScope.OptionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.LightGray,
                contentColor = Color.Black,
            ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
    ) {
        Text(text = text, fontSize = 10.sp)
    }
}

private fun MutableSceneTransitionLayoutState.toggleParentScene(scope: CoroutineScope) {
    val targetScene =
        when (currentScene) {
            ParentSTL.Scenes.SceneA -> ParentSTL.Scenes.SceneB
            else -> ParentSTL.Scenes.SceneA
        }
    setTargetScene(targetScene, scope)
}

private fun MutableSceneTransitionLayoutState.toggleNestedScene(scope: CoroutineScope) {
    val targetScene =
        when (currentScene) {
            ChildSTL.Scenes.NestedTop -> ChildSTL.Scenes.NestedBottom
            else -> ChildSTL.Scenes.NestedTop
        }
    setTargetScene(targetScene, scope)
}

private fun MutableSceneTransitionLayoutState.toggleOverlay(
    overlayKey: OverlayKey,
    scope: CoroutineScope,
) {
    if (currentOverlays.any { it == overlayKey }) {
        hideOverlay(overlayKey, scope)
    } else {
        showOverlay(overlayKey, scope)
    }
}

@Composable
private fun ContentScope.ChildSTL(
    state: MutableSceneTransitionLayoutState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    NestedSceneTransitionLayout(
        state,
        debugName = "NestedSTL",
        modifier = modifier.clickable(null, null) { state.toggleNestedScene(scope) },
    ) {
        scene(ChildSTL.Scenes.NestedTop, mapOf(Swipe.Down to ChildSTL.Scenes.NestedBottom)) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .element(Elements.NotShared)
                        .size(55.dp)
                        .background(Color.Red)
                )
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .element(Elements.NotShared2)
                        .size(45.dp)
                        .background(Color.Gray)
                )
                SharedElement(Color.Green, Modifier.size(60.dp))
            }
        }
        scene(ChildSTL.Scenes.NestedBottom, mapOf(Swipe.Up to ChildSTL.Scenes.NestedTop)) {
            Box(Modifier.fillMaxSize()) {
                SharedElement(Color.Yellow, Modifier.align(Alignment.BottomStart).size(30.dp))
            }
        }
    }
}

@Composable
private fun ContentScope.SharedElement(color: Color, modifier: Modifier = Modifier) {
    val color by animateContentColorAsState(color, ValueKeys.ColorContentKey)
    Box(modifier.element(key = Elements.Shared).background(color = color, shape = CircleShape))
}
