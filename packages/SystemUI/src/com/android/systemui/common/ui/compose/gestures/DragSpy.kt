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

package com.android.systemui.common.ui.compose.gestures

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.IntSize

/**
 * A [Modifier] that reports on drag start/end events without consuming them.
 *
 * This modifier allows you to intercept and react to drag gesture events (start and end/cancel)
 * without consuming them. This is useful for observing drag behavior without interfering with the
 * actual drag handling.
 *
 * ```kotlin
 * Box(
 *     modifier = Modifier
 *         .size(100.dp)
 *         .background(Color.Blue)
 *         .dragSpy(
 *             onDragStart = {
 *                 // Called when a drag gesture starts.
 *                 println("Drag started")
 *             },
 *             onDragEnd = {
 *                 // Called when a drag gesture ends (either by lifting the pointer or cancellation).
 *                 println("Drag ended")
 *             }
 *         )
 *         .draggable(
 *             orientation = Orientation.Horizontal,
 * ```
 *
 * @param onDragStart callback when the drag gesture starts moving
 * @param onDragEnd callback when the drag is ended or cancelled
 */
fun Modifier.dragSpy(onDragStart: () -> Unit, onDragEnd: () -> Unit): Modifier =
    this then DragSpyElement(onDragStart, onDragEnd)

private class DragSpyNode(var onDragStart: () -> Unit, var onDragEnd: () -> Unit) :
    PointerInputModifierNode, Modifier.Node() {

    // We only track the first pointer that starts a potential drag. Subsequent pointers are
    // ignored.
    private var currentPointerId: PointerId? = null
    private var initialPosition: Offset = Offset.Zero
    private var isDragging = false

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        // Only inspect events in the Final pass to avoid interfering with other handlers
        if (pass == PointerEventPass.Final) {
            val changes = pointerEvent.changes

            changes.forEach { change ->
                when {
                    // Down event
                    change.pressed && !change.previousPressed -> {
                        if (currentPointerId == null) {
                            currentPointerId = change.id
                            initialPosition = change.position
                            isDragging = false
                        }
                    }

                    // Move event
                    change.id == currentPointerId && change.pressed -> {
                        // Only start the drag if the pointer actually moved to ignore tap gestures
                        val dragAmount = change.position - initialPosition
                        if (dragAmount.getDistanceSquared() > 0f) {
                            if (!isDragging) {
                                onDragStart()
                                isDragging = true
                            }
                        }
                    }

                    // Up event
                    change.id == currentPointerId && !change.pressed -> {
                        if (isDragging) {
                            onDragEnd()
                        }
                        currentPointerId = null
                        isDragging = false
                    }
                }
            }
        }
    }

    override fun onCancelPointerInput() {
        if (isDragging) {
            onDragEnd()
        }
        currentPointerId = null
        isDragging = false
    }
}

private data class DragSpyElement(val onDragStart: () -> Unit, val onDragEnd: () -> Unit) :
    ModifierNodeElement<DragSpyNode>() {
    override fun create(): DragSpyNode {
        return DragSpyNode(onDragStart, onDragEnd)
    }

    override fun update(node: DragSpyNode) {
        node.onDragStart = onDragStart
        node.onDragEnd = onDragEnd
    }
}
