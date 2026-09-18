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

package com.android.wm.shell.splitscreen

import android.graphics.Rect
import kotlin.math.abs

/**
 * A stateless calculator that determines the closest valid snap point for a divider based on user
 * input and a list of available snap points.
 *
 * TODO: This probably will go away and be replaced by the existing DividerSnapAlgorithm
 */
class DividerSnapAlgorithmTemp {

    /**
     * Finds the closest snap target from a list of available points.
     *
     * @param releasePosition The pixel position where the user let go.
     * @param availablePoints The list of valid *proportional* snap points (e.g., 0.3f, 0.5f).
     * @param bounds The total rectangular area the two nodes occupy.
     * @param orientation The orientation of the split.
     * @return The SnapTarget that is closest to the release position.
     */
    fun findClosestSnapTarget(
        releasePosition: Int,
        availablePoints: List<Float>,
        bounds: Rect,
        orientation: Int,
    ): SnapTargetTemp {
        val relevantTotalSize =
            if (orientation == BranchNode.ORIENTATION_HORIZONTAL) {
                bounds.width()
            } else {
                bounds.height()
            }
        val relevantStart =
            if (orientation == BranchNode.ORIENTATION_HORIZONTAL) {
                bounds.left
            } else {
                bounds.top
            }

        // 1. Convert proportional points to absolute pixel positions.
        val absoluteTargets =
            availablePoints.map { proportion ->
                val pixelPosition = relevantStart + (relevantTotalSize * proportion).toInt()
                // Store both for later use
                Pair(pixelPosition, proportion)
            }

        // 2. Find the pair with the minimum distance to the release position.
        val closestTarget =
            absoluteTargets.minByOrNull { (pixelPosition, _) ->
                abs(pixelPosition - releasePosition)
            }

        // 3. Return a structured SnapTarget object.
        return SnapTargetTemp(
            position = closestTarget?.first ?: relevantStart + (relevantTotalSize / 2),
            proportion = closestTarget?.second ?: 0.5f,
            isDismissTarget = false, // This could be determined in LayoutPolicy
        )
    }
}
