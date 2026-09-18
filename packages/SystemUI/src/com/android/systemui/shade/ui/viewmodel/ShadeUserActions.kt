/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shade.ui.viewmodel

import androidx.compose.ui.input.pointer.PointerType
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys.ToSplitShade
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea

private val singleShadeUserActionResult = UserActionResult(Scenes.Shade)
private val quickSettingsSceneUserActionResult = UserActionResult(Scenes.QuickSettings)
private val splitShadeUserActionResult = UserActionResult(Scenes.Shade, ToSplitShade)

/** Returns collection of [UserAction] to [UserActionResult] pairs for opening the single shade. */
fun singleShadeActions(
    isDownFromTopEdgeEnabled: Boolean = true,
    requireTwoPointersForTopEdgeForQs: Boolean = false,
): Array<Pair<UserAction, UserActionResult>> {

    return buildList {
            // Swiping down, not from the edge, always goes to shade.
            add(Swipe.Down to singleShadeUserActionResult)
            add(Swipe.Down(pointerCount = 2) to singleShadeUserActionResult)
            if (isDownFromTopEdgeEnabled) {
                add(
                    swipeDownFromTopEdge() to
                        if (requireTwoPointersForTopEdgeForQs) {
                            singleShadeUserActionResult
                        } else {
                            quickSettingsSceneUserActionResult
                        }
                )
                add(swipeDownFromTopEdge(pointerCount = 2) to quickSettingsSceneUserActionResult)
            }
        }
        .toTypedArray()
}

/** Returns collection of [UserAction] to [UserActionResult] pairs for opening the split shade. */
fun splitShadeActions(): Array<Pair<UserAction, UserActionResult>> {
    return arrayOf(
        // Swiping down, not from the edge, always goes to shade.
        Swipe.Down to splitShadeUserActionResult,
        Swipe.Down(pointerCount = 2) to splitShadeUserActionResult,
        // Swiping down from the top edge goes to QS.
        swipeDownFromTopEdge() to splitShadeUserActionResult,
        swipeDownFromTopEdge(pointerCount = 2) to splitShadeUserActionResult,
    )
}

/** Returns collection of [UserAction] to [UserActionResult] pairs for opening the dual shade. */
fun dualShadeActions(
    twoFingerSwipeEnabled: Boolean = true
): Array<Pair<UserAction, UserActionResult>> {
    return buildList {
            allPointerTypesButMouse {
                // Don't add the "single pointer swipe down" actions for PointerType.Mouse because
                // it is easy to accidentally trigger them when trying to dragging windows.
                add(Swipe.Down(pointerType = it) to Overlays.NotificationsShade)
                add(
                    Swipe.Down(fromSource = SceneContainerArea.TopEdgeEndHalf, pointerType = it) to
                        Overlays.QuickSettingsShade
                )
            }
            if (twoFingerSwipeEnabled) {
                add(Swipe.Down(pointerCount = 2) to Overlays.QuickSettingsShade)
            }
        }
        .toTypedArray()
}

private fun swipeDownFromTopEdge(pointerCount: Int = 1): Swipe {
    return Swipe.Down(fromSource = Edge.Top, pointerCount = pointerCount)
}

/** Invokes [block] for all PointerTypes except MOUSE (which means Mouse / Touchpad). */
private fun allPointerTypesButMouse(block: (pointerType: PointerType) -> Unit) {
    block(PointerType.Eraser)
    block(PointerType.Stylus)
    block(PointerType.Touch)
}
