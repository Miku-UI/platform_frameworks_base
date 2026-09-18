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

package com.android.systemui.common.ui.compose.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope

/**
 * Detects tap and long press gestures, strictly enforcing touch slop thresholds to reject swipes.
 *
 * Unlike the standard [androidx.compose.foundation.gestures.detectTapGestures], this function
 * manually tracks pointer movement. If the pointer moves beyond the system's
 * [androidx.compose.ui.platform.ViewConfiguration.touchSlop] threshold, the gesture is classified
 * as a swipe/drag, and neither [onTap] nor [onLongPress] is invoked.
 *
 * @param onTap Called when a tap is detected (pointer down + pointer up within touch slop).
 * @param onLongPress Called when a long press is detected (pointer held within touch slop for the
 *   long press timeout).
 */
suspend fun PointerInputScope.detectTapGesturesStrict(
    onTap: ((Offset) -> Unit)? = null,
    onLongPress: ((Offset) -> Unit)? = null,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val startPosition = down.position

        var currentChange = down

        val result =
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                do {
                    val event = awaitPointerEvent()
                    val change =
                        event.changes.firstOrNull { it.id == down.id }
                            ?: return@withTimeoutOrNull GestureResult.Ignore
                    currentChange = change

                    if (
                        currentChange.isConsumed ||
                            (currentChange.position - startPosition).getDistance() >
                                viewConfiguration.touchSlop
                    ) {
                        return@withTimeoutOrNull GestureResult.Ignore
                    }
                } while (currentChange.pressed)

                currentChange.consume()
                GestureResult.Tap
            } ?: GestureResult.LongPress

        when (result) {
            GestureResult.LongPress -> {
                onLongPress?.invoke(currentChange.position)
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
            GestureResult.Tap -> {
                onTap?.invoke(currentChange.position)
            }
            GestureResult.Ignore -> {}
        }
    }
}

private enum class GestureResult {
    Tap,
    LongPress,
    Ignore,
}
