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
package com.android.systemui.communal.ui.viewmodel

import android.content.ComponentName
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.android.app.tracing.coroutines.coroutineScopeTraced as coroutineScope
import com.android.internal.logging.UiEventLogger
import com.android.systemui.Flags.communalAccessibilityResize
import com.android.systemui.communal.ui.metrics.CommunalUiEvent
import com.android.systemui.lifecycle.HydratedActivatable
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

enum class ResizeHandle {
    TOP,
    BOTTOM,
}

data class ResizeInfo(
    /**
     * The number of spans to resize by. A positive number indicates expansion, whereas a negative
     * number indicates shrinking.
     */
    val spans: Int,
    /** The resize handle which was used to resize the element. */
    val fromHandle: ResizeHandle,
) {
    /** Whether we are expanding. If false, then we are shrinking. */
    val isExpanding = spans > 0
}

class ResizeableItemFrameViewModel
@AssistedInject
constructor(
    private val uiEventLogger: UiEventLogger,
    @Assisted private val componentName: ComponentName?,
) : HydratedActivatable(enableEnqueuedActivations = true) {

    @AssistedFactory
    interface Factory {
        fun create(componentName: ComponentName?): ResizeableItemFrameViewModel
    }

    private val _visibleAccessibilityResizeHandle = mutableStateOf<ResizeHandle?>(null)
    val visibleAccessibilityResizeHandle: State<ResizeHandle?> = _visibleAccessibilityResizeHandle

    /**
     * Toggles the visibility of the accessibility resize handle. If the given handle is already
     * visible, it will be hidden.
     */
    fun toggleAccessibilityResizeHandle(handle: ResizeHandle) {
        if (_visibleAccessibilityResizeHandle.value == handle) {
            _visibleAccessibilityResizeHandle.value = null
            uiEventLogger.log(
                CommunalUiEvent.COMMUNAL_HUB_WIDGET_HIDE_ACCESSIBILITY_RESIZE_BUTTONS,
                0,
                componentName?.packageName,
            )
        } else {
            _visibleAccessibilityResizeHandle.value = handle
            uiEventLogger.log(
                CommunalUiEvent.COMMUNAL_HUB_WIDGET_SHOW_ACCESSIBILITY_RESIZE_BUTTONS,
                0,
                componentName?.packageName,
            )
        }
    }

    /** Hides the accessibility resize handle. */
    private fun clearAccessibilityResizeHandle() {
        _visibleAccessibilityResizeHandle.value?.let {
            uiEventLogger.log(
                CommunalUiEvent.COMMUNAL_HUB_WIDGET_HIDE_ACCESSIBILITY_RESIZE_BUTTONS,
                0,
                componentName?.packageName,
            )
        }
        _visibleAccessibilityResizeHandle.value = null
    }

    /** Resets the state of the view model, including the resize handle and drag state. */
    suspend fun reset() {
        clearAccessibilityResizeHandle()
        topDragState.snapTo(0)
        bottomDragState.snapTo(0)
    }

    data class GridLayoutInfo(
        val currentRow: Int,
        val currentSpan: Int,
        val maxHeightPx: Int,
        val minHeightPx: Int,
        val resizeMultiple: Int,
        val totalSpans: Int,
        private val heightPerSpanPx: Float,
        private val verticalItemSpacingPx: Float,
    ) {
        fun getPxOffsetForResize(spans: Int): Int =
            (spans * (heightPerSpanPx + verticalItemSpacingPx)).toInt()

        private fun getSpansForPx(height: Int): Int {
            val spanHeightInt = heightPerSpanPx.roundToInt()
            val spacingInt = verticalItemSpacingPx.roundToInt()
            val exactSpans =
                (height + spacingInt).toFloat() / (spanHeightInt + spacingInt).toFloat()
            return ceil(exactSpans).toInt().coerceIn(resizeMultiple, totalSpans)
        }

        private fun roundDownToMultiple(spans: Int): Int =
            floor(spans.toDouble() / resizeMultiple).toInt() * resizeMultiple

        val maxSpans: Int
            get() = roundDownToMultiple(getSpansForPx(maxHeightPx)).coerceAtLeast(currentSpan)

        val minSpans: Int
            get() = roundDownToMultiple(getSpansForPx(minHeightPx)).coerceAtMost(currentSpan)
    }

    /** Check if widget can expanded based on current resize states */
    fun canExpand(): Boolean {
        return canExpand(ResizeHandle.BOTTOM) || canExpand(ResizeHandle.TOP)
    }

    /** Check if widget can shrink based on current resize states */
    fun canShrink(): Boolean {
        return canShrink(ResizeHandle.TOP) || canShrink(ResizeHandle.BOTTOM)
    }

    /** Get the next anchor value in the specified direction */
    private fun getNextAnchor(state: AnchoredDraggableState<Int>, moveUp: Boolean): Int? {
        var nextAnchor: Int? = null
        var nextAnchorDiff = Int.MAX_VALUE
        val currentValue = state.currentValue

        for (i in 0 until state.anchors.size) {
            val anchor = state.anchors.anchorAt(i) ?: continue
            if (anchor == currentValue) continue

            val diff =
                if (moveUp) {
                    currentValue - anchor
                } else {
                    anchor - currentValue
                }

            if (diff in 1..<nextAnchorDiff) {
                nextAnchor = anchor
                nextAnchorDiff = diff
            }
        }

        return nextAnchor
    }

    /** Handle expansion to the next anchor. Tries bottom handle first. */
    fun expandToNextAnchor() {
        if (canExpand(ResizeHandle.BOTTOM)) {
            expand(ResizeHandle.BOTTOM)
        } else if (canExpand(ResizeHandle.TOP)) {
            expand(ResizeHandle.TOP)
        }
    }

    /** Handle shrinking to the next anchor. Tries top handle first. */
    fun shrinkToNextAnchor() {
        if (canShrink(ResizeHandle.TOP)) {
            shrink(ResizeHandle.TOP)
        } else if (canShrink(ResizeHandle.BOTTOM)) {
            shrink(ResizeHandle.BOTTOM)
        }
    }

    /** Checks if expansion is possible from a specific handle. */
    fun canExpand(handle: ResizeHandle): Boolean {
        return when (handle) {
            ResizeHandle.TOP -> getNextAnchor(topDragState, moveUp = true) != null
            ResizeHandle.BOTTOM -> getNextAnchor(bottomDragState, moveUp = false) != null
        }
    }

    /** Checks if shrinking is possible from a specific handle. */
    fun canShrink(handle: ResizeHandle): Boolean {
        return when (handle) {
            ResizeHandle.TOP -> getNextAnchor(topDragState, moveUp = false) != null
            ResizeHandle.BOTTOM -> getNextAnchor(bottomDragState, moveUp = true) != null
        }
    }

    /** Handle expansion to the next anchor from a specific handle. */
    fun expand(handle: ResizeHandle) {
        uiEventLogger.log(
            CommunalUiEvent.COMMUNAL_HUB_WIDGET_EXPAND_BY_ACCESSIBILITY_BUTTON,
            0,
            componentName?.packageName,
        )
        performResize(handle, isExpand = true)
    }

    /** Handle shrinking to the next anchor from a specific handle. */
    fun shrink(handle: ResizeHandle) {
        uiEventLogger.log(
            CommunalUiEvent.COMMUNAL_HUB_WIDGET_SHRINK_BY_ACCESSIBILITY_BUTTON,
            0,
            componentName?.packageName,
        )
        performResize(handle, isExpand = false)
    }

    private fun performResize(handle: ResizeHandle, isExpand: Boolean) {
        enqueueOnActivatedScope {
            val (state, moveUp) =
                if (handle == ResizeHandle.TOP) {
                    topDragState to isExpand
                } else {
                    bottomDragState to !isExpand
                }
            getNextAnchor(state = state, moveUp = moveUp)?.let { state.snapTo(it) }
        }
    }

    /**
     * The layout information necessary in order to calculate the pixel offsets of the resize anchor
     * points.
     */
    private val gridLayoutInfo = MutableStateFlow<GridLayoutInfo?>(null)

    val topDragState = AnchoredDraggableState(0, DraggableAnchors { 0 at 0f })
    val bottomDragState = AnchoredDraggableState(0, DraggableAnchors { 0 at 0f })

    /** Emits a [ResizeInfo] when the element is resized using a drag gesture. */
    private val resizeInfo: Flow<ResizeInfo> =
        merge(
                snapshotFlow { topDragState.settledValue }
                    .map { ResizeInfo(-it, ResizeHandle.TOP) },
                snapshotFlow { bottomDragState.settledValue }
                    .map { ResizeInfo(it, ResizeHandle.BOTTOM) },
            )
            .filter { it.spans != 0 }
            .onEach {
                if (communalAccessibilityResize()) {
                    topDragState.snapTo(0)
                    bottomDragState.snapTo(0)
                }
            }

    /** Observes the resize info flow and executes the given action when a resize occurs. */
    suspend fun observeResize(action: (ResizeInfo) -> Unit) {
        resizeInfo.collect { action(it) }
    }

    /**
     * Sets the necessary grid layout information needed for calculating the pixel offsets of the
     * drag anchors.
     */
    fun setGridLayoutInfo(
        verticalItemSpacingPx: Float,
        currentRow: Int?,
        maxHeightPx: Int,
        minHeightPx: Int,
        currentSpan: Int,
        resizeMultiple: Int,
        totalSpans: Int,
        viewportHeightPx: Int,
        verticalContentPaddingPx: Float,
    ) {
        if (currentRow == null) {
            gridLayoutInfo.value = null
            return
        }
        require(maxHeightPx >= minHeightPx) {
            "Maximum item span of $maxHeightPx cannot be less than the minimum span of $minHeightPx"
        }

        require(currentSpan <= totalSpans) {
            "Current span ($currentSpan) cannot exceed the total number of spans ($totalSpans)"
        }

        require(resizeMultiple > 0) {
            "Resize multiple ($resizeMultiple) must be a positive integer"
        }
        val availableHeight = viewportHeightPx - verticalContentPaddingPx
        val heightPerSpanPx =
            (availableHeight - (totalSpans - 1) * verticalItemSpacingPx) / totalSpans

        gridLayoutInfo.value =
            GridLayoutInfo(
                heightPerSpanPx = heightPerSpanPx,
                verticalItemSpacingPx = verticalItemSpacingPx,
                currentRow = currentRow,
                currentSpan = currentSpan,
                maxHeightPx = maxHeightPx.coerceAtMost(availableHeight.toInt()),
                minHeightPx = minHeightPx,
                resizeMultiple = resizeMultiple,
                totalSpans = totalSpans,
            )
    }

    private fun calculateAnchorsForHandle(
        handle: ResizeHandle,
        layoutInfo: GridLayoutInfo?,
    ): DraggableAnchors<Int> {

        if (layoutInfo == null || (!isResizeAllowed(handle, layoutInfo))) {
            return DraggableAnchors { 0 at 0f }
        }
        val currentRow = layoutInfo.currentRow
        val currentSpan = layoutInfo.currentSpan
        val minItemSpan = layoutInfo.minSpans
        val maxItemSpan = layoutInfo.maxSpans
        val totalSpans = layoutInfo.totalSpans

        // The maximum row this handle can be dragged to.
        val maxRow =
            if (handle == ResizeHandle.TOP) {
                (currentRow + currentSpan - minItemSpan).coerceAtLeast(0)
            } else {
                (currentRow + maxItemSpan).coerceAtMost(totalSpans)
            }

        // The minimum row this handle can be dragged to.
        val minRow =
            if (handle == ResizeHandle.TOP) {
                (currentRow + currentSpan - maxItemSpan).coerceAtLeast(0)
            } else {
                (currentRow + minItemSpan).coerceAtMost(totalSpans)
            }

        // The current row position of this handle
        val currentPosition =
            if (handle == ResizeHandle.TOP) currentRow else currentRow + currentSpan

        return DraggableAnchors {
            for (targetRow in minRow..maxRow step layoutInfo.resizeMultiple) {
                val diff = targetRow - currentPosition
                val pixelOffset = (layoutInfo.getPxOffsetForResize(abs(diff)) * diff.sign).toFloat()
                diff at pixelOffset
            }
        }
    }

    private fun isResizeAllowed(handle: ResizeHandle, layoutInfo: GridLayoutInfo): Boolean {
        val minItemSpan = layoutInfo.minSpans
        val maxItemSpan = layoutInfo.maxSpans
        val currentRow = layoutInfo.currentRow
        val currentSpan = layoutInfo.currentSpan
        val atMinSize = currentSpan == minItemSpan

        // If already at the minimum size and in the first row, item cannot be expanded from the top
        if (handle == ResizeHandle.TOP && currentRow == 0 && atMinSize) {
            return false
        }

        // If already at the minimum size and occupying the last row, item cannot be expanded from
        // the
        // bottom
        if (
            handle == ResizeHandle.BOTTOM && (currentRow + currentSpan) == maxItemSpan && atMinSize
        ) {
            return false
        }

        // If at maximum size, item can only be shrunk from the bottom and not the top.
        if (handle == ResizeHandle.TOP && currentSpan == maxItemSpan) {
            return false
        }

        return true
    }

    override suspend fun onActivated(): Nothing {
        coroutineScope("ResizeableItemFrameViewModel.onActivated") {
            gridLayoutInfo
                .onEach { layoutInfo ->
                    topDragState.updateAnchors(
                        calculateAnchorsForHandle(ResizeHandle.TOP, layoutInfo)
                    )
                    bottomDragState.updateAnchors(
                        calculateAnchorsForHandle(ResizeHandle.BOTTOM, layoutInfo)
                    )
                }
                .launchIn(this)
            awaitCancellation()
        }
    }
}
