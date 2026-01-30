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

package com.android.systemui.screencapture.record.largescreen.ui.compose

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

// Helper functions to handle the calculation for each side of the rectangle.
private fun newLeft(rect: Rect, dragAmount: Offset, minSizePx: Float): Float {
    return (rect.left + dragAmount.x).coerceIn(0f, rect.right - minSizePx)
}

private fun newTop(rect: Rect, dragAmount: Offset, minSizePx: Float): Float {
    return (rect.top + dragAmount.y).coerceIn(0f, rect.bottom - minSizePx)
}

private fun newRight(rect: Rect, dragAmount: Offset, minSizePx: Float, maxWidth: Float): Float {
    return (rect.right + dragAmount.x).coerceIn(rect.left + minSizePx, maxWidth)
}

private fun newBottom(rect: Rect, dragAmount: Offset, minSizePx: Float, maxHeight: Float): Float {
    return (rect.bottom + dragAmount.y).coerceIn(rect.top + minSizePx, maxHeight)
}

/**
 * Defines the different zones of the box that can be dragged for resizing. A zone could be a Corner
 * or Edge.
 */
sealed interface ResizeZone {

    /**
     * Processes a drag gesture and calculates the new geometry of the rectangle.
     *
     * @param rect The current geometry of the rectangle.
     * @param dragAmount The amount of drag in pixels.
     * @param minSizePx The minimum size of the rectangle in pixels.
     * @param maxWidth The maximum width constraint for the rectangle.
     * @param maxHeight The maximum height constraint for the rectangle.
     * @return The updated Rect after processing the drag.
     */
    fun processResizeDrag(
        rect: Rect,
        dragAmount: Offset,
        minSizePx: Float,
        maxWidth: Float,
        maxHeight: Float,
    ): Rect

    /**
     * A corner of the rectangle.
     *
     * @param alignment The alignment of the corner.
     */
    sealed interface Corner : ResizeZone {
        val alignment: Alignment

        data object TopLeft : Corner {
            override val alignment = Alignment.TopStart

            override fun processResizeDrag(
                rect: Rect,
                dragAmount: Offset,
                minSizePx: Float,
                maxWidth: Float,
                maxHeight: Float,
            ): Rect {
                val newLeft = newLeft(rect, dragAmount, minSizePx)
                val newTop = newTop(rect, dragAmount, minSizePx)
                return Rect(newLeft, newTop, rect.right, rect.bottom)
            }
        }

        data object TopRight : Corner {
            override val alignment = Alignment.TopEnd

            override fun processResizeDrag(
                rect: Rect,
                dragAmount: Offset,
                minSizePx: Float,
                maxWidth: Float,
                maxHeight: Float,
            ): Rect {
                val newRight = newRight(rect, dragAmount, minSizePx, maxWidth)
                val newTop = newTop(rect, dragAmount, minSizePx)
                return Rect(rect.left, newTop, newRight, rect.bottom)
            }
        }

        data object BottomLeft : Corner {
            override val alignment = Alignment.BottomStart

            override fun processResizeDrag(
                rect: Rect,
                dragAmount: Offset,
                minSizePx: Float,
                maxWidth: Float,
                maxHeight: Float,
            ): Rect {
                val newLeft = newLeft(rect, dragAmount, minSizePx)
                val newBottom = newBottom(rect, dragAmount, minSizePx, maxHeight)
                return Rect(newLeft, rect.top, rect.right, newBottom)
            }
        }

        data object BottomRight : Corner {
            override val alignment = Alignment.BottomEnd

            override fun processResizeDrag(
                rect: Rect,
                dragAmount: Offset,
                minSizePx: Float,
                maxWidth: Float,
                maxHeight: Float,
            ): Rect {
                val newRight = newRight(rect, dragAmount, minSizePx, maxWidth)
                val newBottom = newBottom(rect, dragAmount, minSizePx, maxHeight)
                return Rect(rect.left, rect.top, newRight, newBottom)
            }
        }
    }

    /** An edge of the rectangle. */
    sealed interface Edge : ResizeZone {
        data object Left : Edge {
            override fun processResizeDrag(
                rect: Rect,
                dragAmount: Offset,
                minSizePx: Float,
                maxWidth: Float,
                maxHeight: Float,
            ): Rect {
                val newLeft = newLeft(rect, dragAmount, minSizePx)
                return Rect(newLeft, rect.top, rect.right, rect.bottom)
            }
        }

        data object Top : Edge {
            override fun processResizeDrag(
                rect: Rect,
                dragAmount: Offset,
                minSizePx: Float,
                maxWidth: Float,
                maxHeight: Float,
            ): Rect {
                val newTop = newTop(rect, dragAmount, minSizePx)
                return Rect(rect.left, newTop, rect.right, rect.bottom)
            }
        }

        data object Right : Edge {
            override fun processResizeDrag(
                rect: Rect,
                dragAmount: Offset,
                minSizePx: Float,
                maxWidth: Float,
                maxHeight: Float,
            ): Rect {
                val newRight = newRight(rect, dragAmount, minSizePx, maxWidth)
                return Rect(rect.left, rect.top, newRight, rect.bottom)
            }
        }

        data object Bottom : Edge {
            override fun processResizeDrag(
                rect: Rect,
                dragAmount: Offset,
                minSizePx: Float,
                maxWidth: Float,
                maxHeight: Float,
            ): Rect {
                val newBottom = newBottom(rect, dragAmount, minSizePx, maxHeight)
                return Rect(rect.left, rect.top, rect.right, newBottom)
            }
        }
    }
}
