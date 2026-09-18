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

package com.android.systemui.notifications.ui.composable

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.compose.gesture.NestedDraggable
import com.android.compose.gesture.nestedDraggable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * State holder for the `headsUpSnoozeDraggable` modifier.
 *
 * It implements [NestedDraggable] and [NestedDraggable.Controller] directly to simplify attachment.
 *
 * @param minOffset The target Y offset for a successful snooze action (must be negative).
 * @param isEnabled returns true when the snooze gesture is allowed.
 * @param onSnoozed Called only when the Snooze gesture completes, and its animation completes.
 * @param onSnoozeProgressChanged Called when the Snooze gesture starts and finishes.
 */
@Stable
class HeadsUpSnoozeDragController(
    private val minOffset: () -> Float,
    val isEnabled: () -> Boolean,
    val onSnoozed: () -> Unit,
    val onSnoozeProgressChanged: (Boolean) -> Unit,
    private val scope: CoroutineScope,
    private val velocityThresholdPx: Float,
) : NestedDraggable, NestedDraggable.Controller {

    private val offsetAnimatable = Animatable(0f)

    /** The current driven Y offset. Clamped strictly inside range [minOffset, 0]. */
    val offset: Float
        get() = offsetAnimatable.value

    /**
     * Visual Translation Logic: Calculates the Y offset for the visual placeholder, allowing the
     * notification to move faster than the user's finger to ensure it completely clears the screen.
     *
     * @param restPosition The Y position of the HUN when not being dragged (headsUpTopInset).
     * @param topHeadsUpHeight The height of the heads-up notification content.
     */
    fun calculateHeadsUpPlaceholderYOffset(restPosition: Float, topHeadsUpHeight: Float): Float {
        val minLimit = minOffset()
        if (minLimit >= 0f) return restPosition

        // Maps scrollOffset [minLimit, 0] to visual Y [-topHeadsUpHeight, restPosition]
        return restPosition + (offset * (restPosition + topHeadsUpHeight) / -minLimit)
    }

    // ---- NestedDraggable ------------------------------------------------------------------------

    override fun shouldStartDrag(change: PointerInputChange): Boolean = isEnabled()

    override fun shouldConsumeNestedPreScroll(sign: Float): Boolean {
        if (!isEnabled()) return false
        val currentOffset = offset

        // at rest (offset == 0), only consume upward drags
        if (currentOffset == 0f && sign < 0f) return true

        // started to drag (offset < 0), consume both direction drags within range bounds
        if (currentOffset < 0f) return true

        return false
    }

    override fun shouldConsumeNestedPostScroll(sign: Float): Boolean = false

    override fun onDragStarted(
        position: Offset,
        sign: Float,
        pointersDown: Int,
        pointerType: PointerType?,
    ): NestedDraggable.Controller {
        onSnoozeProgressChanged(true)
        return this
    }

    // ---- Controller -----------------------------------------------------------------------------

    override fun onDrag(delta: Float): Float {
        val currentOffset = offset
        val targetOffset = (currentOffset + delta).coerceIn(minOffset(), 0f)
        snapTo(targetOffset)
        return targetOffset - currentOffset
    }

    /** Updates the internal target state. Interrupts the currently running offset animation. */
    @VisibleForTesting
    fun snapTo(value: Float) {
        val clampedValue = value.coerceIn(minOffset(), 0f)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offsetAnimatable.snapTo(clampedValue) }
    }

    override suspend fun onDragStopped(velocity: Float, awaitFling: suspend () -> Unit): Float {
        val shouldSnooze =
            when {
                // Fast fling up -> Snooze
                velocity < -velocityThresholdPx -> true

                // Fast fling down -> Rest
                velocity > velocityThresholdPx -> false

                // Dragging slowly: decide based on the position
                else -> offset < minOffset() / 2
            }

        try {
            if (shouldSnooze) {
                animateToSnooze()
            } else {
                animateToSettle()
            }
        } finally {
            if (shouldSnooze) {
                onSnoozed()
            }
            onSnoozeProgressChanged(false)
        }

        return velocity
    }

    fun reset() {
        snapTo(0f)
    }

    /** COMMIT to Dismiss: Accelerating curve to feel like it’s being "thrown out". */
    private suspend fun animateToSnooze() =
        offsetAnimatable.animateTo(
            targetValue = minOffset(),
            animationSpec = tween(easing = FastOutSlowInEasing),
        )

    /** ABORT to settle back to resting position */
    private suspend fun animateToSettle() =
        offsetAnimatable.animateTo(targetValue = 0f, animationSpec = spring())
}

/** Remembers a [HeadsUpSnoozeDragController] that handles the swipe to snooze gesture for HUNs. */
@Composable
fun rememberHeadsUpSnoozeController(
    minOffset: () -> Float,
    isEnabled: () -> Boolean = { true },
    onSnoozed: () -> Unit,
    onSnoozeStateChanged: (Boolean) -> Unit = {},
): HeadsUpSnoozeDragController {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember(minOffset, isEnabled) {
        HeadsUpSnoozeDragController(
            minOffset = minOffset,
            isEnabled = isEnabled,
            onSnoozed = onSnoozed,
            onSnoozeProgressChanged = onSnoozeStateChanged,
            scope = scope,
            velocityThresholdPx = with(density) { 125.dp.toPx() }, // px/sec
        )
    }
}

/** A [NestedDraggable] modifier that intercepts vertical scroll gestures to snooze a HUN. */
fun Modifier.headsUpSnoozeDraggable(
    controller: HeadsUpSnoozeDragController,
    enabled: Boolean = true,
): Modifier =
    this.nestedDraggable(
        draggable = controller,
        orientation = Orientation.Vertical,
        enabled = enabled,
    )
