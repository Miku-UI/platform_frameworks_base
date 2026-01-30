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

package com.android.systemui.ambientcue.ui.shape

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.min

/**
 * A [Shape] that is rectangular on the left, bottom, and right sides, but has a concave top edge
 * formed by two arcs connected by a straight line.
 *
 * @property cornerRadius The radius of the arc.
 */
class TopConcaveArcShape(private val cornerRadius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radiusPx = with(density) { cornerRadius.toPx() }

        val clampedRadiusPx = max(0f, min(radiusPx, min(size.width / 2f, size.height)))
        // Return a rectangle directly if the radius is too small.
        if (clampedRadiusPx < 0.01f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }

        val path =
            Path().apply {
                moveTo(0f, 0f)

                // Arc is bottom left quadrant of a circle, going from the left down to the bottom
                arcTo(
                    rect =
                        Rect(
                            left = 0f,
                            top = -clampedRadiusPx,
                            right = clampedRadiusPx * 2,
                            bottom = clampedRadiusPx,
                        ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false,
                )
                lineTo(size.width - clampedRadiusPx, clampedRadiusPx)
                // Arc is bottom right quadrant of a circle, going from the bottom up to the right
                arcTo(
                    rect =
                        Rect(
                            left = size.width - (clampedRadiusPx * 2),
                            top = -clampedRadiusPx,
                            right = size.width,
                            bottom = clampedRadiusPx,
                        ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false,
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
        return Outline.Generic(path)
    }
}
