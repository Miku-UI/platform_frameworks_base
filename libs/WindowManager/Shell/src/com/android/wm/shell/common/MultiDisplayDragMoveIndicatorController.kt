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
package com.android.wm.shell.common

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.RectF
import android.view.SurfaceControl
import android.window.DesktopExperienceFlags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.desktopmode.ShellDesktopState

/**
 * Controller to manage the indicators that show users the current position of the dragged window on
 * the new display when performing drag move across displays.
 */
class MultiDisplayDragMoveIndicatorController(
    private val displayController: DisplayController,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val indicatorSurfaceFactory: MultiDisplayDragMoveIndicatorSurface.Factory,
    private val shellDesktopState: ShellDesktopState,
) {
    private val dragIndicators =
        mutableMapOf<Int, MutableMap<Int, MultiDisplayDragMoveIndicatorSurface>>()

    /**
     * Updates the position and visibility of drag move indicators for a single task.
     *
     * @param boundsDp the current bounds of the dragged task
     * @param currentDisplayId the ID of the display the drag is currently at
     * @param startDisplayId the ID of the display the drag started at
     * @param taskLeash the surface representing the task being dragged
     * @param taskInfo the task being dragged
     * @param displayIds contains the IDs of the displays to show drag move indicators on
     * @param transaction is used to apply updates to indicator surfaces
     */
    fun onDragMove(
        boundsDp: RectF,
        currentDisplayId: Int,
        startDisplayId: Int,
        taskLeash: SurfaceControl,
        taskInfo: RunningTaskInfo,
        displayIds: Set<Int>,
        transaction: SurfaceControl.Transaction,
    ) {
        val startDisplayDpi =
            displayController.getDisplayLayout(startDisplayId)?.densityDpi() ?: return
        for (displayId in displayIds) {
            val allowDropToDisplay = shellDesktopState.isEligibleWindowDropTarget(displayId)
            if (!allowDropToDisplay) {
                // No need to render indicators on displays that do not support desktop mode.
                continue
            }
            val displayLayout = displayController.getDisplayLayout(displayId) ?: continue
            val displayContext = displayController.getDisplayContext(displayId) ?: continue
            val visibility =
                if (RectF.intersects(RectF(boundsDp), displayLayout.globalBoundsDp())) {
                    if (displayId == currentDisplayId) {
                        MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE
                    } else {
                        MultiDisplayDragMoveIndicatorSurface.Visibility.TRANSLUCENT
                    }
                } else {
                    MultiDisplayDragMoveIndicatorSurface.Visibility.INVISIBLE
                }
            if (
                dragIndicators[taskInfo.taskId]?.containsKey(displayId) != true &&
                    visibility == MultiDisplayDragMoveIndicatorSurface.Visibility.INVISIBLE
            ) {
                // Skip this display if:
                // - It doesn't have an existing indicator that needs to be updated, AND
                // - The latest dragged window bounds don't intersect with this display.
                continue
            }

            val boundsPx =
                MultiDisplayDragMoveBoundsCalculator.convertGlobalDpToLocalPxForRect(
                    boundsDp,
                    displayLayout,
                )

            // Get or create the inner map for the current task.
            val dragIndicatorsForTask = dragIndicators.getOrPut(taskInfo.taskId) { mutableMapOf() }
            dragIndicatorsForTask[displayId]?.also { existingIndicator ->
                existingIndicator.relayout(boundsPx, transaction, visibility)
            }
                ?: run {
                    val newIndicator = indicatorSurfaceFactory.create(displayContext, taskLeash)
                    newIndicator.show(
                        transaction,
                        taskInfo,
                        rootTaskDisplayAreaOrganizer,
                        displayId,
                        boundsPx,
                        visibility,
                        displayLayout.densityDpi().toFloat() / startDisplayDpi.toFloat(),
                    )
                    dragIndicatorsForTask[displayId] = newIndicator
                }
        }
    }

    /**
     * Called when the drag ends. Disposes of the drag move indicator surfaces associated with the
     * given [taskId] and applies the surface changes with the provided [transaction].
     */
    fun onDragEnd(taskId: Int, transaction: SurfaceControl.Transaction) {
        dragIndicators
            .remove(taskId)
            ?.values
            ?.takeIf { it.isNotEmpty() }
            ?.let { indicators ->
                indicators.forEach { indicator -> indicator.dispose(transaction) }
            }
    }

    /** Disposes all of the indicator surfaces with the [transaction]. */
    fun disposeAllIndicators(transaction: SurfaceControl.Transaction) {
        dragIndicators.values.forEach { innerIndicatorMap ->
            innerIndicatorMap.values.forEach { indicator -> indicator.dispose(transaction) }
        }
        dragIndicators.clear()
    }
}
