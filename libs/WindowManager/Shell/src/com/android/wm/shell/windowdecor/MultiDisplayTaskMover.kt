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

package com.android.wm.shell.windowdecor

import android.graphics.Rect
import android.hardware.display.DisplayTopology
import android.view.Choreographer
import android.view.SurfaceControl
import android.window.DesktopExperienceFlags
import android.window.WindowContainerTransaction
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.MultiDisplayDragMoveBoundsCalculator
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController

/**
 * Implementation of [TaskMover] for handling task drag-move operations, with support for
 * multi-display scenarios.
 */
class MultiDisplayTaskMover(
    private val displayController: DisplayController,
    private val transactionSupplier: () -> SurfaceControl.Transaction,
    private val indicatorController: MultiDisplayDragMoveIndicatorController,
) : TaskMover, DisplayController.OnDisplaysChangedListener {
    private val displayIds = mutableSetOf<Int>()

    init {
        displayController.addDisplayWindowListener(this)
    }

    override fun onTopologyChanged(topology: DisplayTopology?) {
        displayIds.clear()
        topology ?: return
        displayIds.addAll(topology.allNodesIdMap().keys)
        logD(TAG, "onTopologyChanged: displayIds=%s", displayIds)
    }

    override fun onMoveUpdate(
        session: DragSession,
        displayId: Int,
        x: Float,
        y: Float,
    ): WindowContainerTransaction? {
        // A transition is in progress to restore the window to its pre-snapped or pre-maximized
        // bounds. Defer the move until the transition is complete to avoid visual glitches.
        if (
            DesktopExperienceFlags.ENABLE_BOUNDS_RESTORING_ON_DRAG_EXIT.isTrue &&
                session.pendingBoundsRestoreTransition != null
        ) {
            logD(
                TAG,
                session.windowDecoration.taskInfo.taskId,
                "onMoveUpdate: deferring move due to ongoing bounds restore transition",
            )
            return null
        }

        // The window is currently snapped or maximized. On the first drag-move event,
        // calculate and apply its pre-snapped or pre-maximized bounds to initiate the restoration.
        if (
            DesktopExperienceFlags.ENABLE_BOUNDS_RESTORING_ON_DRAG_EXIT.isTrue &&
                session.shouldRestoreBoundsOnMove
        ) {
            session.shouldRestoreBoundsOnMove = false
            val prevBounds =
                session.desktopRepository?.getBoundsBeforeSnapOrMaximize(
                    session.windowDecoration.taskInfo.taskId
                )

            val wct =
                prevBounds?.run {
                    val currentBounds =
                        session.windowDecoration.taskInfo.configuration.windowConfiguration.bounds
                    val restoredBounds = calculateOnMoveRestoredBounds(prevBounds, currentBounds, x)
                    logD(
                        TAG,
                        session.windowDecoration.taskInfo.taskId,
                        "onMoveUpdate: restoredBounds=%s",
                        restoredBounds,
                    )
                    session.taskBoundsAtDragStart.set(restoredBounds)

                    WindowContainerTransaction()
                        .setBounds(
                            session.windowDecoration.taskInfo.token,
                            session.taskBoundsAtDragStart,
                        )
                }

            session.repositionTaskBounds.set(session.taskBoundsAtDragStart)
            return wct
        }

        val t = transactionSupplier()
        val startDisplayLayout =
            displayController.getDisplayLayout(session.windowDecoration.taskInfo.displayId)
        val currentDisplayLayout = displayController.getDisplayLayout(displayId)

        if (startDisplayLayout == null || currentDisplayLayout == null) {
            logD(
                TAG,
                session.windowDecoration.taskInfo.taskId,
                "onMoveUpdate: falling back to single-display move (startLayout=%s, " +
                    "currentLayout=%s)",
                startDisplayLayout,
                currentDisplayLayout,
            )
            // Fall back to single-display drag behavior if any display layout is unavailable.
            DragPositioningCallbackUtility.setPositionOnDrag(
                session.windowDecoration,
                session.repositionTaskBounds,
                session.taskBoundsAtDragStart,
                session.repositionStartPoint,
                t,
                x,
                y,
            )
        } else {
            val boundsDp =
                MultiDisplayDragMoveBoundsCalculator.calculateGlobalDpBoundsForDrag(
                    startDisplayLayout,
                    session.repositionStartPoint,
                    session.taskBoundsAtDragStart,
                    currentDisplayLayout,
                    x,
                    y,
                )
            session.repositionTaskBounds.set(
                MultiDisplayDragMoveBoundsCalculator.convertGlobalDpToLocalPxForRect(
                    boundsDp,
                    // repositionTaskBounds must match the display ID in TaskInfo. During the drag,
                    // this is still the start display.
                    startDisplayLayout,
                )
            )

            indicatorController.onDragMove(
                boundsDp,
                displayId,
                session.windowDecoration.taskInfo.displayId,
                session.windowDecoration.taskSurface,
                session.windowDecoration.taskInfo,
                displayIds,
                t,
            )
            // Move the original task surface off-screen to hide it. A mirrored surface is
            // used for the drag indicator on all displays, including the start display.
            // This is necessary for independent opacity control, as a mirror's alpha is
            // capped by its source.
            if (!session.hasMovedTaskSurfaceOffScreen) {
                session.hasMovedTaskSurfaceOffScreen = true
                t.setPosition(
                    session.windowDecoration.taskSurface,
                    startDisplayLayout.width().toFloat(),
                    startDisplayLayout.height().toFloat(),
                )
            }
        }
        t.setFrameTimeline(Choreographer.getInstance().vsyncId)
        t.apply()
        return null
    }

    override fun onMoveEnd(session: DragSession, displayId: Int, x: Float, y: Float) {
        logD(
            TAG,
            session.windowDecoration.taskInfo.taskId,
            "onMoveEnd: displayId=%d, x=%f, y=%f",
            displayId,
            x,
            y,
        )
        val startDisplayLayout =
            displayController.getDisplayLayout(session.windowDecoration.taskInfo.displayId)
        val currentDisplayLayout = displayController.getDisplayLayout(displayId)

        if (
            session.windowDecoration.taskInfo.displayId == displayId ||
                startDisplayLayout == null ||
                currentDisplayLayout == null
        ) {
            logD(
                TAG,
                session.windowDecoration.taskInfo.taskId,
                "onMoveEnd: falling back to single-display move (sameDisplay=%b, " +
                    "startLayout=%s, currentLayout=%s)",
                session.windowDecoration.taskInfo.displayId == displayId,
                startDisplayLayout,
                currentDisplayLayout,
            )
            // Fall back to single-display drag behavior if:
            // 1. The drag destination display is the same as the start display. This prevents
            // unnecessary animations caused by minor width/height changes due to DPI scaling.
            // 2. Either the starting or current display layout is unavailable.
            DragPositioningCallbackUtility.updateTaskBounds(
                session.repositionTaskBounds,
                session.taskBoundsAtDragStart,
                session.repositionStartPoint,
                x,
                y,
            )
        } else {
            val boundsDp =
                MultiDisplayDragMoveBoundsCalculator.calculateGlobalDpBoundsForDrag(
                    startDisplayLayout,
                    session.repositionStartPoint,
                    session.taskBoundsAtDragStart,
                    currentDisplayLayout,
                    x,
                    y,
                )
            session.repositionTaskBounds.set(
                MultiDisplayDragMoveBoundsCalculator.convertGlobalDpToLocalPxForRect(
                    boundsDp,
                    // The drag is complete. We are about to update the TaskInfo display ID to the
                    // new display, so we use currentDisplayLayout here.
                    currentDisplayLayout,
                )
            )
        }
        logD(
            TAG,
            session.windowDecoration.taskInfo.taskId,
            "onMoveEnd: bounds=%s",
            session.repositionTaskBounds,
        )
    }

    private fun calculateOnMoveRestoredBounds(
        prevBounds: Rect,
        currentBounds: Rect,
        dragStartX: Float,
    ): Rect {
        val prevWidth = prevBounds.width()
        val prevHeight = prevBounds.height()

        val touchPointHorizontalRatio = (dragStartX - currentBounds.left) / currentBounds.width()
        val positionOffset = (prevWidth * touchPointHorizontalRatio).toInt()

        val newLeft = dragStartX - positionOffset
        return Rect(
            newLeft.toInt(),
            currentBounds.top,
            (newLeft + prevWidth).toInt(),
            currentBounds.top + prevHeight,
        )
    }

    companion object {
        private const val TAG = "MultiDisplayTaskMover"
    }
}
