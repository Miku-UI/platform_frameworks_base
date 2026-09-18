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

package com.android.compose.gesture

// TODO: Remove this copy of DragGestureDetector.kt when b/487283944 is fixed.

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.sign

/**
 * Waits for drag motion and uses [orientation] to detect the direction of touch slop detection. It
 * passes [pointerId] as the pointer to examine. If [pointerId] is raised, another pointer from
 * those that are down will be chosen to lead the gesture, and if none are down, `null` is returned.
 * If [pointerId] is not down when [awaitPointerSlopOrCancellation] is called, then `null` is
 * returned.
 *
 * When pointer slop is detected, [onPointerSlopReached] is called with the change and the distance
 * beyond the pointer slop. If [onPointerSlopReached] does not consume the position change, pointer
 * slop will not have been considered detected and the detection will continue or, if it is
 * consumed, the [PointerInputChange] that was consumed will be returned.
 *
 * This works with [awaitTouchSlopOrCancellation] for the other axis to ensure that only horizontal
 * or vertical dragging is done, but not both. It also works for dragging in two ways when using
 * [awaitTouchSlopOrCancellation]
 *
 * We use [initialPositionChange] to consider any amount of initial movement in this gesture before
 * the slop detector is called.
 *
 * @return The [PointerInputChange] of the event that was consumed in [onPointerSlopReached] or
 *   `null` if all pointers are raised or the position change was consumed by another gesture
 *   detector.
 */
internal suspend inline fun AwaitPointerEventScope.awaitPointerSlopOrCancellation(
    pointerId: PointerId,
    pointerType: PointerType,
    orientation: Orientation?,
    initialPositionChange: Offset = Offset.Zero,
    onPointerSlopReached: (PointerInputChange, Offset) -> Unit,
): PointerInputChange? {
    if (currentEvent.isPointerUp(pointerId)) {
        return null // The pointer has already been lifted, so the gesture is canceled
    }

    val touchSlop = viewConfiguration.pointerSlop(pointerType)
    var pointer: PointerId = pointerId
    val touchSlopDetector = TouchSlopDetector(orientation, initialPositionChange)
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.isConsumed) {
            return null
        } else if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                // This is the last "up"
                return null
            } else {
                pointer = otherDown.id
            }
        } else {
            val postSlopOffset =
                touchSlopDetector.getPostSlopOffset(
                    dragEvent.positionChangeIgnoreConsumed(),
                    touchSlop,
                )
            if (postSlopOffset.isSpecified) {
                onPointerSlopReached(dragEvent, postSlopOffset)
                if (dragEvent.isConsumed) {
                    return dragEvent
                } else {
                    touchSlopDetector.reset()
                }
            } else {
                // verify that nothing else consumed the drag event
                awaitPointerEvent(PointerEventPass.Final)
                if (dragEvent.isConsumed) {
                    return null
                }
            }
        }
    }
}

/**
 * Detects if touch slop has been crossed after adding a series of [PointerInputChange]. For every
 * new [PointerInputChange] one should add it to this detector using [getPostSlopOffset]. If the
 * position change causes the touch slop to be crossed, [getPostSlopOffset] will return true.
 */
internal class TouchSlopDetector(
    var orientation: Orientation? = null,
    initialPositionChange: Offset = Offset.Zero,
) {

    fun Offset.mainAxis() = if (orientation == Orientation.Horizontal) x else y

    fun Offset.crossAxis() = if (orientation == Orientation.Horizontal) y else x

    /** The accumulation of drag deltas in this detector. */
    private var totalPositionChange: Offset = initialPositionChange

    /**
     * Adds [dragEvent] to this detector. If the accumulated position changes crosses the touch slop
     * provided by [touchSlop], this method will return the post slop offset, that is the total
     * accumulated delta change minus the touch slop value, otherwise this should return null. If
     * [shouldCommit] is true, the delta will be added to the total position change.
     */
    fun getPostSlopOffset(
        positionChange: Offset,
        touchSlop: Float,
        shouldCommit: Boolean = true,
    ): Offset {
        val finalChange =
            if (shouldCommit) {
                totalPositionChange += positionChange
                totalPositionChange
            } else {
                totalPositionChange + positionChange
            }

        val inDirection =
            if (orientation == null) {
                finalChange.getDistance()
            } else {
                finalChange.mainAxis().absoluteValue
            }

        val hasCrossedSlop = inDirection >= touchSlop

        return if (hasCrossedSlop) {
            calculatePostSlopOffset(touchSlop)
        } else {
            Offset.Unspecified
        }
    }

    /**
     * Resets the accumulator associated with this detector.
     *
     * @param initialPositionAccumulator Use to initialize the position change accumulator, for
     *   instance in cases where slop detection may happen "mid-gesture", that is, the slop
     *   detection didn't start from the first down event but somewhere after.
     */
    fun reset(initialPositionAccumulator: Offset = Offset.Zero) {
        totalPositionChange = initialPositionAccumulator
    }

    fun isDeltaAtAngleOfInterest(delta: Offset): Boolean {
        val projectedPositionChange = totalPositionChange + delta
        val angle =
            atan2(
                x = projectedPositionChange.x.absoluteValue,
                y = projectedPositionChange.y.absoluteValue,
            ) * 180 / PI
        return when (orientation) {
            Orientation.Horizontal -> {
                angle < GestureAngleThreshold
            }
            Orientation.Vertical -> {
                angle > GestureAngleThreshold
            }
            else -> {
                false
            }
        }
    }

    private fun calculatePostSlopOffset(touchSlop: Float): Offset {
        return if (orientation == null) {
            val touchSlopOffset =
                totalPositionChange / totalPositionChange.getDistance() * touchSlop
            // update postSlopOffset
            totalPositionChange - touchSlopOffset
        } else {
            val finalMainAxisChange =
                totalPositionChange.mainAxis() - (sign(totalPositionChange.mainAxis()) * touchSlop)
            val finalCrossAxisChange = totalPositionChange.crossAxis()
            if (orientation == Orientation.Horizontal) {
                Offset(finalMainAxisChange, finalCrossAxisChange)
            } else {
                Offset(finalCrossAxisChange, finalMainAxisChange)
            }
        }
    }
}

private fun PointerEvent.isPointerUp(pointerId: PointerId): Boolean =
    changes.fastFirstOrNull { it.id == pointerId }?.pressed != true

// This value was determined using experiments and common sense.
// We can't use zero slop, because some hypothetical desktop/mobile devices can send
// pointer events with a very high precision (but I haven't encountered any that send
// events with less than 1px precision)
private val mouseSlop = 0.125.dp
private val defaultTouchSlop = 18.dp // The default touch slop on Android devices
private val mouseToTouchSlopRatio = mouseSlop / defaultTouchSlop

// TODO(demin): consider this as part of ViewConfiguration class after we make *PointerSlop*
//  functions public (see the comment at the top of the file).
//  After it will be a public API, we should get rid of `touchSlop / 144` and return absolute
//  value 0.125.dp.toPx(). It is not possible right now, because we can't access density.
internal fun ViewConfiguration.pointerSlop(pointerType: PointerType): Float {
    return when (pointerType) {
        PointerType.Mouse -> touchSlop * mouseToTouchSlopRatio
        else -> touchSlop
    }
}

// An angle in degrees where horizontal and vertical gestures are disambiguated.
private const val GestureAngleThreshold = 30
