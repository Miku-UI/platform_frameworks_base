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

package com.android.wm.shell.pinnedlayer.phone

import android.app.TaskInfo
import android.app.WindowConfiguration
import android.content.Context
import android.graphics.Rect
import android.util.Slog
import android.util.SparseArray
import android.util.TypedValue
import android.window.TransitionRequestInfo
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.shared.desktopmode.DesktopState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A controller that tracks the available display area for the pinned layer. */
class PinnedLayerPresentationController(
    context: Context,
    private val displayController: DisplayController,
    private val desktopState: DesktopState,
) {

    private val entryBoundsPadding: Int =
        context.resources.getDimensionPixelSize(R.dimen.pinned_window_init_padding)

    // Stores the tasks' initial and overridden bounds, which are used as a reference for
    // subsequent resize operations to prevent implicit movement of the task by resizing it in
    // various ways (e.g., by expanding or contracting against display boundaries).
    private val overriddenBounds: SparseArray<OverridableBounds> = SparseArray()

    /**
     * Calculates the entry destination bounds for a task window which is pinned.
     *
     * The size of the window is calculated based on the task's original bounds, adjusted to fit
     * within the defined constraints (min 220dp, max 70% of display dimensions). The aspect ratio
     * of the task is preserved.
     *
     * As a security mitigation -- to limit the app's ability to position the pinned window -- the
     * task is moved into the right bottom corner of the display, everytime it's pinned.
     *
     * @param task The task for which to calculate the bounds.
     * @return A [Rect] with the calculated bounds, or an empty Rect if calculation is not possible.
     */
    fun getPinEntryDestinationBounds(task: TaskInfo, displayId: Int = task.displayId): Rect? {
        val displayDetails = getDisplayDetails(displayId) ?: return null

        val taskBounds = task.configuration.windowConfiguration.getBounds()
        val clampedBounds = clampSizeToConstraints(taskBounds, displayDetails) ?: return null

        // Calculate the bounds based on the final size, stable insets and padding.
        val displayLayout = displayDetails.layout
        val stableInsets = displayLayout.stableInsets()
        val right = displayLayout.width() - stableInsets.right - entryBoundsPadding
        val bottom = displayLayout.height() - stableInsets.bottom - entryBoundsPadding
        val left = right - clampedBounds.width()
        val top = bottom - clampedBounds.height()
        return Rect(left, top, right, bottom).also { ensureNotOffscreen(it, displayDetails) }
    }

    /**
     * Checks if the task is in a state that is supported for pinning.
     *
     * @param task The task to check.
     * @return True if the task is supported, false otherwise.
     */
    fun isTaskSupportedForPinning(task: TaskInfo): Boolean {
        // Freeform ensures tasks is not in pip/split/fullscreen/bubble.
        val isFreeform = task.windowingMode == WindowConfiguration.WINDOWING_MODE_FREEFORM
        // Limit to pining only in same display and require desktop mode support.
        val displaySupportsDesktopMode =
            desktopState.isDesktopModeSupportedOnDisplay(task.displayId)
        return isFreeform && displaySupportsDesktopMode
    }

    /**
     * Changes task's bounds to match constraints and ensures that it's positioned correctly on the
     * display.
     *
     * @param task a task that's being clamped.
     * @param bounds current task's bounds, either real task position or leash position.
     * @param displayId a source display id based on which to calculate bounds.
     * @return new bounds that match window constraints and that're placed inside visible display
     *   bounds.
     */
    fun clampToDisplay(task: TaskInfo, bounds: Rect, displayId: Int = task.displayId): Rect? {
        val displayDetails = getDisplayDetails(displayId) ?: return null

        return clampSizeToConstraints(bounds, displayDetails)?.also {
            ensureNotOffscreen(it, displayDetails)
        }
    }

    /**
     * Calculates the new bounds for a pinned task.
     *
     * For pinned tasks, only resizing is allowed; movement is not. The new bounds must adhere to
     * min/max size constraints and remain on-screen.
     *
     * To prevent users from implicitly moving the task by resizing it in various ways (e.g., by
     * expanding or contracting against display boundaries), the task's initial position is
     * preserved. Subsequent resize operations utilize this preserved position to determine the new
     * bounds. The task is expanded or contracted against the display's center, see
     * [resizeAndAdjustPosition] for more details.
     *
     * @param task The task for which to calculate the bounds.
     * @param requestedLocation The requested location of the task.
     * @return A [Rect] with the calculated bounds, or [null] if calculation is not possible.
     */
    fun calculateNewTaskBounds(
        task: TaskInfo,
        requestedLocation: TransitionRequestInfo.RequestedLocation,
    ): Rect? {
        val currentBounds = task.configuration.windowConfiguration.bounds
        if (requestedLocation.displayId != task.displayId) {
            Slog.d(TAG, "Requested location on different display, ignoring the requested bounds")
            return currentBounds
        }
        val displayDetails = getDisplayDetails(task.displayId) ?: return null

        if (currentBounds != overriddenBounds.get(task.taskId)?.resolvedBounds()) {
            // Bounds have changed since the last positioning, e.g., due to user interaction.
            // Clear the overrides.
            overriddenBounds.remove(task.taskId)
        }
        val overridableBounds: OverridableBounds =
            overriddenBounds.getOrCompute(task.taskId) { OverridableBounds(currentBounds) }

        val clampedBounds =
            clampSizeToConstraints(requestedLocation.bounds, displayDetails) ?: return null
        // always compare against the initial bounds, to disallow moving the task by subsequent
        // resizes.
        overridableBounds.overriddenBounds =
            resizeAndAdjustPosition(displayDetails, overridableBounds.initBounds, clampedBounds)

        return overridableBounds.overriddenBounds
    }

    /**
     * Adjusts the task [bounds] to match the [targetSize] and adjusts the position to prevent task
     * movement by subsequent resizes.
     *
     * When resized, strives to move only one task's edge on each axis. If the task is expanding,
     * the edge that's farther away from the center is moved. If shrinking, the edge that's closer
     * to the center is moved. Final position could be adjusted to not end up offscreen.
     *
     * @param displayDetails The display details for the task.
     * @param bounds The initial bounds of the task.
     * @param targetSize The requested size of the task.
     */
    private fun resizeAndAdjustPosition(
        displayDetails: DisplayDetails,
        bounds: Rect,
        targetSize: Rect,
    ): Rect {
        val result = Rect(bounds)
        val displayCenter = displayDetails.getCenter()
        val widthExpands = targetSize.width() > bounds.width()
        val heightExpands = targetSize.height() > bounds.height()
        val leftIsCloser =
            displayCenter.xAxisDistanceTo(bounds.left) < displayCenter.xAxisDistanceTo(bounds.right)
        val topIsCloser =
            displayCenter.yAxisDistanceTo(bounds.top) < displayCenter.yAxisDistanceTo(bounds.bottom)

        // If expanding, take the edge father away from the center.
        // If shrinking, take the edge closer to the center.
        val moveFactor =
            Rect(
                // 1 - if edge should move, 0 - otherwise.
                /* left= */ if (widthExpands xor leftIsCloser) 1 else 0,
                /* top= */ if (heightExpands xor topIsCloser) 1 else 0,
                /* right= */ if (widthExpands xor leftIsCloser) 0 else 1,
                /* bottom= */ if (heightExpands xor topIsCloser) 0 else 1,
            )

        val widthDelta = targetSize.width() - bounds.width()
        val heightDelta = targetSize.height() - bounds.height()
        return result.apply {
            left -= widthDelta * moveFactor.left
            top -= heightDelta * moveFactor.top
            right += widthDelta * moveFactor.right
            bottom += heightDelta * moveFactor.bottom
            ensureNotOffscreen(this, displayDetails)
        }
    }

    private fun clampSizeToConstraints(bounds: Rect, displayDetails: DisplayDetails): Rect? {
        val displayLayout = displayDetails.layout
        val displayContext = displayDetails.context

        if (bounds.width() <= 0 || bounds.height() <= 0) {
            Slog.w(TAG, "Cannot get min/max bounds for task with empty or invalid bounds")
            return null
        }

        val w0 = bounds.width().toFloat()
        val h0 = bounds.height().toFloat()

        val minSizePx =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                MIN_SIZE_DP,
                displayContext.resources.displayMetrics,
            )
        val maxWidth = displayLayout.width() * MAX_SIZE_RATIO
        val maxHeight = displayLayout.height() * MAX_SIZE_RATIO

        // Adjust the task size to fit within the constraints.
        val minScale = max(minSizePx / w0, minSizePx / h0)
        val maxScale = minOf(maxWidth / w0, maxHeight / h0)
        val scale =
            if (minScale > maxScale) {
                // Constraints conflict, prioritize max size
                // Should not happen, CDD requires min 440dp display for freeform
                maxScale
            } else {
                1.0f.coerceIn(minScale, maxScale)
            }
        val finalWidth = w0 * scale
        val finalHeight = h0 * scale

        return Rect(
            bounds.left,
            bounds.top,
            bounds.left + finalWidth.toInt(),
            bounds.top + finalHeight.toInt(),
        )
    }

    /**
     * Ensures that the bounds are not offscreen, by adjusting its position or size if necessary.
     */
    private fun ensureNotOffscreen(bounds: Rect, displayDetails: DisplayDetails) {
        val stableBounds = displayDetails.stableBounds()
        bounds.apply {
            // 1. Try to fit |bounds| in |stableBounds| without changing size
            offset(max(stableBounds.left - left, 0), 0)
            offset(min(stableBounds.right - right, 0), 0)
            offset(0, max(stableBounds.top - top, 0))
            offset(0, min(stableBounds.bottom - bottom, 0))

            // 2. Ensure that |bounds| fit inside |stableBounds| even if that
            // requires size changes.
            intersect(stableBounds)
        }
    }

    private companion object {
        private const val TAG = "PinnedLayerDisplayAreaController"
        // min = 220dp, max = 70% of the display width and height
        private const val MIN_SIZE_DP = 220f
        private const val MAX_SIZE_RATIO = 0.7f
    }

    /** Stores the initial bounds of a task, allowing overrides for subsequent resize operations. */
    private class OverridableBounds(val initBounds: Rect) {
        var overriddenBounds: Rect? = null

        fun resolvedBounds(): Rect {
            return overriddenBounds ?: initBounds
        }
    }

    private data class DisplayDetails(val layout: DisplayLayout, val context: Context) {
        fun stableBounds(): Rect = Rect().also { layout.getStableBounds(it) }

        fun getCenter(): DisplayCenter =
            stableBounds().let { DisplayCenter(it.exactCenterX(), it.exactCenterY()) }
    }

    private fun getDisplayDetails(displayId: Int): DisplayDetails? {
        val displayLayout = displayController.getDisplayLayout(displayId)
        val displayContext = displayController.getDisplayContext(displayId)
        if (displayLayout == null || displayContext == null) {
            Slog.w(TAG, "Cannot get bounds for display $displayId, layout or context not found")
            return null
        }
        return DisplayDetails(displayLayout, displayContext)
    }

    private data class DisplayCenter(val x: Float, val y: Float) {
        fun xAxisDistanceTo(x: Int) = abs(x - this.x)

        fun yAxisDistanceTo(y: Int) = abs(y - this.y)
    }

    private fun <T> SparseArray<T>.getOrCompute(key: Int, compute: () -> T): T {
        if (!contains(key)) {
            put(key, compute())
        }
        return get(key)
    }
}
