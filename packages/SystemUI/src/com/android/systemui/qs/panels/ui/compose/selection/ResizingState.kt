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

package com.android.systemui.qs.panels.ui.compose.selection

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.systemui.qs.panels.ui.compose.selection.ResizingState.ResizeOperation.FinalResizeOperation
import com.android.systemui.qs.panels.ui.compose.selection.ResizingState.ResizeOperation.TemporaryResizeOperation
import com.android.systemui.qs.pipeline.shared.TileSpec

@Composable
fun rememberResizingState(tileSpec: TileSpec, startsAsIcon: Boolean): ResizingState {
    return remember(tileSpec) { ResizingState(tileSpec, startsAsIcon) }
}

enum class QSDragAnchor {
    Icon,
    Large,
}

/**
 * Holds the data required to calculate the anchors of a resizable tile.
 *
 * @property isIcon whether or not the tile is currently an icon or a large tile
 * @property width the current width in pixel of the tile
 */
data class QSDragAnchorsData(val isIcon: Boolean, val width: Int)

class ResizingState(tileSpec: TileSpec, startsAsIcon: Boolean) {
    val anchoredDraggableState =
        AnchoredDraggableState(if (startsAsIcon) QSDragAnchor.Icon else QSDragAnchor.Large)

    val bounds by derivedStateOf {
        anchoredDraggableState.anchors.minPosition().takeIf { !it.isNaN() } to
            anchoredDraggableState.anchors.maxPosition().takeIf { !it.isNaN() }
    }

    /** Whether or not the resizing state is idle at one of the anchors */
    val isIdle by derivedStateOf { progress().let { it == 0f || it == 1f } }

    /** Whether or not the handle is currently being dragged. */
    var isDragActive by mutableStateOf(false)
        private set

    val temporaryResizeOperation by derivedStateOf {
        TemporaryResizeOperation(
            tileSpec,
            toIcon = anchoredDraggableState.currentValue == QSDragAnchor.Icon,
        )
    }

    val finalResizeOperation by derivedStateOf {
        FinalResizeOperation(
            tileSpec,
            toIcon = anchoredDraggableState.settledValue == QSDragAnchor.Icon,
        )
    }

    fun dragStarted() {
        isDragActive = true
    }

    fun dragEnded() {
        isDragActive = false
    }

    /** Calculates and updates the drag anchors based on the size and maximum span. */
    fun updateAnchors(data: QSDragAnchorsData, maxSpan: Int, padding: Int) {
        val totalPadding = (maxSpan - 1) * padding
        val min = if (data.isIcon) data.width else (data.width - totalPadding) / maxSpan
        val max = if (data.isIcon) (data.width * maxSpan) + totalPadding else data.width
        updateAnchors(min.toFloat(), max.toFloat())
    }

    private fun updateAnchors(min: Float, max: Float) {
        anchoredDraggableState.updateAnchors(
            DraggableAnchors {
                QSDragAnchor.Icon at min
                QSDragAnchor.Large at max
            }
        )
    }

    suspend fun updateCurrentValue(isIcon: Boolean) {
        anchoredDraggableState.animateTo(if (isIcon) QSDragAnchor.Icon else QSDragAnchor.Large)
    }

    suspend fun toggleCurrentValue() {
        val isIcon = anchoredDraggableState.currentValue == QSDragAnchor.Icon
        updateCurrentValue(!isIcon)
    }

    fun progress(): Float {
        // Check if anchors are defined before calculating progress since it defaults to 1f
        // otherwise.
        return if (
            anchoredDraggableState.anchors.positionOf(QSDragAnchor.Icon).isNaN() ||
                anchoredDraggableState.anchors.positionOf(QSDragAnchor.Large).isNaN()
        ) {
            if (anchoredDraggableState.currentValue == QSDragAnchor.Icon) 0f else 1f
        } else {
            anchoredDraggableState.progress(QSDragAnchor.Icon, QSDragAnchor.Large)
        }
    }

    /**
     * Represents a resizing operation for a tile.
     *
     * @property spec The tile's [TileSpec]
     * @property toIcon The new size for the tile.
     */
    sealed class ResizeOperation private constructor(val spec: TileSpec, val toIcon: Boolean) {
        /** A temporary resizing operation, used while a resizing movement is in motion. */
        class TemporaryResizeOperation(spec: TileSpec, toIcon: Boolean) :
            ResizeOperation(spec, toIcon)

        /** A final resizing operation, used while a resizing movement is done. */
        class FinalResizeOperation(spec: TileSpec, toIcon: Boolean) : ResizeOperation(spec, toIcon)
    }
}
