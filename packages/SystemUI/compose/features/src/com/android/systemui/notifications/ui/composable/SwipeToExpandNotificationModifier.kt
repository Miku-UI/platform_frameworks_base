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

import android.util.Log
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.ExpandableView
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Bridges compatibility between the Legacy NSSL and Compose, allowing the Compose modifier to query
 * and interact with legacy View notifications.
 */
interface SwipeToExpandCallback {
    /** Returns the view at the given raw coordinates. */
    fun getChildAtRawPosition(x: Float, y: Float): ExpandableView?

    /** Returns whether the given view is expandable. */
    fun canChildBeExpanded(v: View?): Boolean

    /** Sets the user expanded state of the given view. */
    fun setUserExpandedChild(v: View?, userExpanded: Boolean)

    /** Sets whether the user is currently swiping to expand the given view. */
    fun setUserSwipingToExpand(v: View?, isUserSwiping: Boolean)

    /** Called when the expansion state changes. */
    fun expansionStateChanged(isExpanding: Boolean)

    /** Called when the expansion finish animation gets cancelled. */
    fun setExpansionCancelled(v: View?)

    /** Plays haptic feedback when the target view starts the expansion. */
    fun playExpandStartHaptic()
}

/**
 * A [Modifier] that enables swipe-to-expand gestures for notifications by bridging Compose gesture
 * detection to the legacy NSSL. It uses a [pointerInput] node to detect a vertical drag. Uses a
 * [callback] to obtain a target [ExpandableView], and directly manipulates it by the drag down
 * amount. Dispatches the remaining delta to the received [overscrollEffect].
 */
fun Modifier.swipeToExpandNotification(
    callback: SwipeToExpandCallback,
    overscrollEffect: OverscrollEffect,
    layoutCoordinatesProvider: () -> LayoutCoordinates?,
    allowStartGesture: () -> Boolean = { true },
    velocityThresholdPx: Float,
    distanceThresholdPx: Float,
): Modifier =
    this then
        SwipeToExpandNotificationElement(
            callback = callback,
            overscrollEffect = overscrollEffect,
            layoutCoordinatesProvider = layoutCoordinatesProvider,
            allowStartGestureCallback = allowStartGesture,
            velocityThresholdPx = velocityThresholdPx,
            distanceThresholdPx = distanceThresholdPx,
        )

private data class SwipeToExpandNotificationElement(
    val callback: SwipeToExpandCallback,
    val overscrollEffect: OverscrollEffect,
    val layoutCoordinatesProvider: () -> LayoutCoordinates?,
    val allowStartGestureCallback: () -> Boolean,
    val velocityThresholdPx: Float,
    val distanceThresholdPx: Float,
) : ModifierNodeElement<SwipeToExpandNotificationNode>() {
    override fun create(): SwipeToExpandNotificationNode {
        return SwipeToExpandNotificationNode(
            callback = callback,
            overscrollEffect = overscrollEffect,
            layoutCoordinatesProvider = layoutCoordinatesProvider,
            allowStartGestureCallback = allowStartGestureCallback,
            velocityThresholdPx = velocityThresholdPx,
            distanceThresholdPx = distanceThresholdPx,
        )
    }

    override fun update(node: SwipeToExpandNotificationNode) {
        node.update(
            callback = callback,
            overscrollEffect = overscrollEffect,
            layoutCoordinatesProvider = layoutCoordinatesProvider,
            allowStartGestureCallback = allowStartGestureCallback,
            velocityThresholdPx = velocityThresholdPx,
            distanceThresholdPx = distanceThresholdPx,
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "swipeToExpandNotification"
        properties["velocityThresholdPx"] = velocityThresholdPx
        properties["distanceThresholdPx"] = distanceThresholdPx
    }
}

private class SwipeToExpandNotificationNode(
    var callback: SwipeToExpandCallback,
    var overscrollEffect: OverscrollEffect,
    var layoutCoordinatesProvider: () -> LayoutCoordinates?,
    var allowStartGestureCallback: () -> Boolean,
    var velocityThresholdPx: Float,
    var distanceThresholdPx: Float,
) : DelegatingNode(), PointerInputModifierNode {

    private val pointerInputNode =
        delegate(SuspendingPointerInputModifierNode { swipeToExpandPointerInput() })

    fun update(
        callback: SwipeToExpandCallback,
        overscrollEffect: OverscrollEffect,
        layoutCoordinatesProvider: () -> LayoutCoordinates?,
        allowStartGestureCallback: () -> Boolean,
        velocityThresholdPx: Float,
        distanceThresholdPx: Float,
    ) {
        val velocityChanged = this.velocityThresholdPx != velocityThresholdPx
        val distanceChanged = this.distanceThresholdPx != distanceThresholdPx

        this.callback = callback
        this.overscrollEffect = overscrollEffect
        this.layoutCoordinatesProvider = layoutCoordinatesProvider
        this.allowStartGestureCallback = allowStartGestureCallback
        this.velocityThresholdPx = velocityThresholdPx
        this.distanceThresholdPx = distanceThresholdPx

        if (velocityChanged || distanceChanged) {
            pointerInputNode.resetPointerInputHandler()
        }
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        pointerInputNode.onPointerEvent(pointerEvent, pass, bounds)
    }

    override fun onCancelPointerInput() {
        pointerInputNode.onCancelPointerInput()
    }

    private suspend fun PointerInputScope.swipeToExpandPointerInput() {
        val velocityTracker = VelocityTracker()
        var animationJob: Job? = null

        awaitEachGesture {
            // Phase 1: Finding a target
            val down = awaitFirstDown()
            velocityTracker.resetTracking()
            velocityTracker.addPointerInputChange(down)
            animationJob?.cancel()
            debugLog { "DOWN at ${down.position}" }

            if (!allowStartGestureCallback()) {
                debugLog { "Gesture not allowed.." }
                return@awaitEachGesture
            }

            val targetView =
                obtainExpandableTarget(down.position, callback, layoutCoordinatesProvider)
                    ?: run {
                        debugLog { "No target found." }
                        return@awaitEachGesture
                    }

            var isExpanding = false
            var currentHeight = targetView.actualHeight.toFloat()
            val collapsedHeight = targetView.collapsedHeight.toFloat()
            val expandedHeight = targetView.maxContentHeight.toFloat()

            /** Inline helper to handle the drag for an expansion. */
            fun processDrag(dragAmount: Float) {
                if (!isExpanding) {
                    val movingDown = dragAmount > 0
                    if (movingDown) {
                        debugLog { "START expanding" }
                        isExpanding = true
                        callback.setUserSwipingToExpand(targetView, true)
                        callback.expansionStateChanged(true)
                        callback.playExpandStartHaptic()
                    } else {
                        debugLog { "Upward drag, not expanding yet" }
                    }
                }

                if (isExpanding) {
                    val dragDelta = Offset(0f, dragAmount)

                    // Dispatch DRAG to OverscrollEffect
                    overscrollEffect.applyToScroll(
                        delta = dragDelta,
                        source = NestedScrollSource.UserInput,
                    ) { availableDelta ->
                        val availableY = availableDelta.y
                        // Theoretical new height based on the drag
                        val rawHeight = currentHeight + availableY
                        val newHeight = rawHeight.coerceIn(collapsedHeight, expandedHeight)
                        val actualDelta = newHeight - currentHeight

                        if (newHeight != currentHeight) {
                            targetView.setFinalActualHeight(newHeight.toInt())
                            currentHeight = newHeight
                        }

                        debugLog {
                            "processDrag isExpanding:$isExpanding totalDrag:$dragAmount currentHeight:$currentHeight newHeight:$newHeight"
                        }

                        // Return exactly how much the expand/collapse consumed
                        Offset(0f, actualDelta)
                    }
                }
            }

            // Phase 2: Vertical drag slop detection
            // Wait for a vertical drag motion before doing anything.
            var lastChange =
                awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    velocityTracker.addPointerInputChange(change)
                    processDrag(over)
                    if (isExpanding) {
                        // Consume all of the change during an expansion.
                        change.consume()
                    }
                }

            // Phase 3: Drag
            // Consume pointer input events, and handle drags in any directions.
            while (lastChange != null && lastChange.pressed) {
                val change = awaitDragOrCancellation(lastChange.id)
                if (change != null && change.pressed) {
                    velocityTracker.addPointerInputChange(change)
                    processDrag(change.positionChange().y)
                    if (isExpanding) {
                        // Consume all of the change during an expansion.
                        change.consume()
                    }
                }
                lastChange = change
            }

            // Phase 4: Finish the expansion
            // Animate the target to either collapsed or expanded.
            if (isExpanding) {
                val initialVelocity = velocityTracker.calculateVelocity().y
                debugLog { "STOP expanding with velocity:$initialVelocity" }
                animationJob =
                    coroutineScope.launch {
                        finishExpansion(
                            view = targetView,
                            callback = callback,
                            overscrollEffect = overscrollEffect,
                            currentHeight = currentHeight,
                            collapsedHeight = collapsedHeight,
                            expandedHeight = expandedHeight,
                            initialVelocity = initialVelocity,
                            velocityThresholdPx = velocityThresholdPx,
                            distanceThresholdPx = distanceThresholdPx,
                        )
                    }
                isExpanding = false
            }
        }
    }
}

private fun obtainExpandableTarget(
    positionDown: Offset,
    callback: SwipeToExpandCallback,
    layoutCoordinatesProvider: () -> LayoutCoordinates?,
): ExpandableView? {
    val coords = layoutCoordinatesProvider()
    if (coords == null || !coords.isAttached) return null

    val startPositionInWindow = coords.localToWindow(positionDown)
    val targetView =
        callback.getChildAtRawPosition(startPositionInWindow.x, startPositionInWindow.y)
            ?: return null

    if (!callback.canChildBeExpanded(targetView)) return null
    if (targetView.intrinsicHeight == targetView.maxContentHeight) return null

    debugLog { "Obtained target ${(targetView as? ExpandableNotificationRow)?.key ?: targetView}" }

    return targetView
}

private suspend fun finishExpansion(
    view: ExpandableView,
    callback: SwipeToExpandCallback,
    overscrollEffect: OverscrollEffect,
    currentHeight: Float,
    collapsedHeight: Float,
    expandedHeight: Float,
    initialVelocity: Float,
    velocityThresholdPx: Float,
    distanceThresholdPx: Float,
) {
    val totalChange = expandedHeight - collapsedHeight
    if (totalChange <= 0) return

    val progress = (currentHeight - collapsedHeight) / totalChange

    val shouldExpand =
        when {
            // Fast fling down -> Expand
            initialVelocity > velocityThresholdPx -> true

            // Fast fling up -> Collapse
            initialVelocity < -velocityThresholdPx -> false

            // Dragging slowly: decide based on the position
            else -> {
                val draggedFarEnough = (currentHeight - collapsedHeight) > distanceThresholdPx
                val isMoreThanHalfWay = progress > 0.5f
                draggedFarEnough || isMoreThanHalfWay
            }
        }

    callback.expansionStateChanged(false)
    val targetHeight = if (shouldExpand) expandedHeight else collapsedHeight

    // Dispatch FLING to OverscrollEffect
    overscrollEffect.applyToFling(velocity = Velocity(0f, initialVelocity)) { availableVelocity ->
        val availableY = availableVelocity.y

        if (targetHeight != currentHeight) {
            val animatable = Animatable(currentHeight)
            try {
                animatable.animateTo(
                    targetValue = targetHeight,
                    initialVelocity = availableY,
                    animationSpec = spring(),
                ) {
                    view.setFinalActualHeight(value.roundToInt())
                }
            } catch (e: CancellationException) {
                callback.setExpansionCancelled(view)
            } finally {
                finalizeAnimation(view, callback, shouldExpand)
            }

            // The spring inherently consumes the velocity to brake exactly at the target.
            Velocity(0f, availableY)
        } else {
            // Already at the target, so the expand/collapse didn't consume any velocity.
            finalizeAnimation(view, callback, shouldExpand)
            Velocity.Zero
        }
    }
}

private fun finalizeAnimation(
    view: ExpandableView,
    callback: SwipeToExpandCallback,
    expanded: Boolean,
) {
    callback.setUserExpandedChild(view, expanded)
    callback.setUserSwipingToExpand(view, false)
}

private inline fun debugLog(msg: () -> String) {
    if (IS_DEBUG) {
        Log.d(TAG, msg())
    }
}

private const val TAG = "SwipeToExpandNotification"
private const val IS_DEBUG = false
