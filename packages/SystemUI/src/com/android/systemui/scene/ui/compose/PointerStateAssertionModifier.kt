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

package com.android.systemui.scene.ui.compose

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastForEach

/**
 * A diagnostic modifier that validates the consistency of the pointer input stream.
 *
 * It tracks the lifecycle of individual pointer IDs to ensure:
 * 1. A pointer doesn't go DOWN if we are already tracking it.
 * 2. A pointer doesn't go UP if we aren't tracking it.
 * 3. The internal count of tracked pointers matches the event's report of currently pressed
 *    pointers.
 *
 * Use this to debug ghost touches, state desyncs, or dropped events.
 *
 * @param sharePointerInputWithSiblings Whether to let other modifiers share the input events.
 * @param onError Callback invoked with a descriptive message when an inconsistency is detected.
 */
fun Modifier.assertPointerState(
    sharePointerInputWithSiblings: Boolean = true,
    onError: (String) -> Unit,
): Modifier = this then PointerStateAssertionElement(sharePointerInputWithSiblings, onError)

private data class PointerStateAssertionElement(
    val sharePointerInputWithSiblings: Boolean,
    val onError: (String) -> Unit,
) : ModifierNodeElement<PointerStateAssertionNode>() {
    override fun create(): PointerStateAssertionNode {
        return PointerStateAssertionNode(sharePointerInputWithSiblings, onError)
    }

    override fun update(node: PointerStateAssertionNode) {
        node.onError = onError
        node.sharePointers = sharePointerInputWithSiblings
    }

    override fun InspectorInfo.inspectableProperties() {
        properties["sharePointerInputWithSiblings"] = sharePointerInputWithSiblings
        name = "assertPointerState"
    }
}

@VisibleForTesting
class PointerStateAssertionNode(var sharePointers: Boolean, var onError: (String) -> Unit) :
    Modifier.Node(), PointerInputModifierNode {

    // The source of truth for what THIS node thinks is currently on screen.
    private val pointersDown = mutableSetOf<Long>()

    // Configurable: If false, this modifier might consume/block input from siblings
    // depending on the phase, though typically this affects how the dispatcher offers events.
    override fun sharePointerInputWithSiblings(): Boolean = sharePointers

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass != PointerEventPass.Initial) return

        val changes = pointerEvent.changes
        var pressedCount = 0
        changes.fastForEach { change ->
            if (change.pressed) pressedCount++

            when {
                change.changedToDownIgnoreConsumed() -> {
                    val isAdded = pointersDown.add(change.id.value)
                    if (!isAdded) {
                        onError("cannot add ${change.id}: already in pointersDown: $pointersDown")
                    }
                }

                change.changedToUpIgnoreConsumed() -> {
                    val isRemoved = pointersDown.remove(change.id.value)
                    if (!isRemoved) {
                        onError("cannot remove ${change.id}: not in pointersDown: $pointersDown")
                    }
                }
            }
        }

        val downCount = pointersDown.size
        if (downCount != pressedCount) {
            onError("desyncs $downCount != $pressedCount pointersDown: $pointersDown")
        }
    }

    override fun onCancelPointerInput() {
        pointersDown.clear()
    }
}
