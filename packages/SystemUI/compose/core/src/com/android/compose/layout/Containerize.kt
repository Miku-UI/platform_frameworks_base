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

package com.android.compose.layout

import android.graphics.Point
import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

/**
 * Applies a container layout to a Composable.
 *
 * The container size is calculated as a fraction of the incoming constraints, clamped by min/max
 * edge constraints defined in [ContainerConfig]. [ContainerLayout] determines if width or height
 * corresponds to the long or short edge.
 *
 * The container is centered in the available space (including any insets) if possible. If
 * [WindowInsets] are provided, the container will not overlap them.
 *
 * It's recommended to use [com.android.compose.windowsizeclass.LocalWindowSizeClass] to evaluate if
 * [containerize] modifier should be applied and which [ContainerLayout] should be used.
 *
 * Example usage:
 * ```
 * val widthThreshold = WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND
 * val heightThreshold = AdditionalHeightBreakpoints.HEIGHT_DP_EXTRA_LARGE_LOWER_BOUND
 * val windowSizeClass = LocalWindowSizeClass.current
 * val containerLayout: ContainerLayout? =
 *     when {
 *         windowSizeClass.isWidthAtLeastBreakpoint(widthThreshold) -> ContainerLayout.HORIZONTAL
 *         windowSizeClass.isHeightAtLeastBreakpoint(heightThreshold) -> ContainerLayout.VERTICAL
 *         else -> null
 *     }
 *
 * Modifier.then(
 *         if (containerLayout != null) {
 *             Modifier.containerize(containerConfig(), containerLayout)
 *         } else {
 *             Modifier.fillMaxSize()
 *         }
 *     )
 * ```
 *
 * @param config The configuration for calculating the container size.
 * @param layout The [ContainerLayout] (HORIZONTAL/VERTICAL) to map long/short edges to axes.
 * @param insets The [WindowInsets] to be consumed internally.
 */
@Composable
fun Modifier.containerize(
    config: ContainerConfig,
    layout: ContainerLayout,
    insets: WindowInsets = WindowInsets(),
): Modifier {
    return this.layout { measurable, constraints ->
            val bottomInset = insets.getBottom(this)
            val topInset = insets.getTop(this)
            val (size, offset) =
                config.calculateContainerPlacement(this, constraints, layout, bottomInset, topInset)
            val placeable = measurable.measure(Constraints.fixed(size.width, size.height))
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(x = offset.x, y = offset.y)
            }
        }
        .consumeWindowInsets(insets)
}

/**
 * Specifies how to map "long" and "short" edge constraints to width and height.
 * - [HORIZONTAL]: Width uses long edge constraints, height uses short edge constraints.
 * - [VERTICAL]: Width uses short edge constraints, height uses long edge constraints.
 */
enum class ContainerLayout {
    HORIZONTAL,
    VERTICAL,
}

/**
 * @property size Calculated container size.
 * @property offset Calculated container offset.
 */
data class ContainerPlacement(val size: IntSize, val offset: Point)

/**
 * Configuration for the [containerize] modifier.
 *
 * @property sizeFraction The target size as a fraction (0.0 to 1.0) of the parent's constraints.
 * @property minLongEdge Minimum length for the long edge.
 * @property minShortEdge Minimum length for the short edge.
 * @property maxLongEdge Maximum length for the long edge.
 * @property maxShortEdge Maximum length for the short edge.
 */
class ContainerConfig(
    val sizeFraction: Float,
    val minLongEdge: Dp,
    val minShortEdge: Dp,
    val maxLongEdge: Dp,
    val maxShortEdge: Dp,
) {
    /**
     * Calculates the [ContainerPlacement] for the container.
     * - The preferred [Constraints] are calculated based on [ContainerConfig], [ContainerLayout]
     *   and display [Density].
     * - A specified fraction of parents' max constraints is calculated and the value is clamped
     *   between the preferred [Constraints].
     * - The value is capped by parents' max constraints minus insets to prevent overflow. Resulting
     *   width and height are saved to [ContainerPlacement.size].
     * - Using the calculated size, [ContainerPlacement.offset] is calculated to place the container
     *   in [Constraints]' center if possible or aligned to insets otherwise.
     *
     * @param density Display [Density].
     * @param constraints The [Constraints] from the parent.
     * @param layout The [ContainerLayout] to interpret edge constraints.
     * @param bottomInset Bottom inset value to apply.
     * @param topInset Top inset value to apply.
     * @return Calculated `size` and `offset` in [ContainerPlacement].
     */
    fun calculateContainerPlacement(
        density: Density,
        constraints: Constraints,
        layout: ContainerLayout,
        bottomInset: Int,
        topInset: Int,
    ): ContainerPlacement {
        val preferredConstraints = calculatePreferredConstraints(density, layout)
        val size = calculateContainerSize(constraints, preferredConstraints, topInset + bottomInset)
        val offset = calculateOffsetToPlaceInCenter(constraints, size, bottomInset, topInset)
        return ContainerPlacement(size, offset)
    }

    private fun calculateContainerSize(
        parentConstraints: Constraints,
        preferredConstraints: Constraints,
        verticalInsets: Int,
    ) =
        IntSize(
            width =
                minOf(
                    (parentConstraints.maxWidth * sizeFraction)
                        .toInt()
                        .coerceIn(preferredConstraints.minWidth, preferredConstraints.maxWidth),
                    parentConstraints.maxWidth,
                ),
            height =
                minOf(
                    (parentConstraints.maxHeight * sizeFraction)
                        .toInt()
                        .coerceIn(preferredConstraints.minHeight, preferredConstraints.maxHeight),
                    parentConstraints.maxHeight - verticalInsets,
                ),
        )

    private fun calculateOffsetToPlaceInCenter(
        constraints: Constraints,
        size: IntSize,
        bottomInset: Int,
        topInset: Int,
    ): Point {
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            Log.w(TAG, "Container can be placed only in bounded constraints, are $constraints")
            return Point(0, 0)
        }
        val xOffsetToAlignCenter = (constraints.maxWidth - size.width) / 2
        val yOffsetToAlignCenter = (constraints.maxHeight - size.height) / 2
        val yOffset =
            if (bottomInset > yOffsetToAlignCenter) {
                // Move the container up if it would overlap bottom inset.
                yOffsetToAlignCenter - (bottomInset - yOffsetToAlignCenter)
            } else if (topInset > yOffsetToAlignCenter) {
                // Move the container down if it would overlap top inset.
                // Equivalent to `yOffsetToAlignCenter + (topInset - yOffsetToAlignCenter)`
                topInset
            } else {
                // Center container otherwise.
                yOffsetToAlignCenter
            }
        return Point(xOffsetToAlignCenter, yOffset)
    }

    /**
     * Calculates preferred container constraints based on [ContainerConfig], [ContainerLayout] and
     * display [Density].
     *
     * Preferred constraints are calculated by mapping specified min/max short/long edges into
     * min/max width/height based on [ContainerLayout] orientation (VERTICAL or HORIZONTAL) and
     * converting [Dp] values to [Px] based on [Density].
     */
    private fun calculatePreferredConstraints(
        density: Density,
        layout: ContainerLayout,
    ): Constraints {
        with(density) {
            return when (layout) {
                ContainerLayout.VERTICAL -> {
                    // When VERTICAL: width is constrained to short edge, height to long edge
                    Constraints(
                        minWidth = minShortEdge.roundToPx(),
                        minHeight = minLongEdge.roundToPx(),
                        maxWidth = maxShortEdge.roundToPx(),
                        maxHeight = maxLongEdge.roundToPx(),
                    )
                }
                ContainerLayout.HORIZONTAL -> {
                    // When HORIZONTAL: width is constrained to long edge, height to short edge
                    Constraints(
                        minWidth = minLongEdge.roundToPx(),
                        minHeight = minShortEdge.roundToPx(),
                        maxWidth = maxLongEdge.roundToPx(),
                        maxHeight = maxShortEdge.roundToPx(),
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "ContainerConfig"
    }
}
