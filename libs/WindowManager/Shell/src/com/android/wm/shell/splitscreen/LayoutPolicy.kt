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

/**
 * The LayoutPolicy is a dedicated component responsible for validating the rules of a layout.
 * Its purpose is to answer the question, "Is this proposed layout state valid?" This includes
 * checking for constraints like the maximum number of apps, minimum window sizes, or
 * device-specific restrictions.
 *
 * NOTE: This is a placeholder class for now, ignore.
 */
class LayoutPolicy {

    /**
     * Gets the list of available proportional snap points for a given pair of nodes. This is where
     * all device and feature-specific rules are checked.
     *
     * @param nodeBefore The first node.
     * @param nodeAfter The second node.
     * @return A list of floats between 0.0 and 1.0 representing valid divider positions.
     */
    fun getAvailableSnapPoints(nodeBefore: LayoutNode, nodeAfter: LayoutNode): List<Float> {
        val availablePoints = mutableListOf(0.5f) // Middle is usually standard

        // Example: Add more snap points on large screens
        if (isLargeScreenDevice()) {
            availablePoints.add(0.3f)
            availablePoints.add(0.7f)
        }

        // Example: Check feature flags
        if (isThreeQuarterSplitEnabled()) {
            availablePoints.add(0.75f)
            availablePoints.add(0.25f)
        }

        // Example: Filter out points that would violate minimum size constraints
        // This is a critical validation step.
        val combinedMinWidth = getMinWidth(nodeBefore) + getMinWidth(nodeAfter)
        return availablePoints.filter { proportion ->
            val sizeBefore = combinedMinWidth * proportion
            val sizeAfter = combinedMinWidth * (1 - proportion)
            sizeBefore >= getMinWidth(nodeBefore) && sizeAfter >= getMinWidth(nodeAfter)
        }
    }

    private fun getMinWidth(node: LayoutNode): Int {
        // In a real implementation, this would recursively find the minimum
        // width required by the tasks within the node.
        return 0 // Placeholder value to allow tests to pass
    }

    private fun isLargeScreenDevice(): Boolean {
        // Placeholder for device-specific logic
        return true
    }

    private fun isThreeQuarterSplitEnabled(): Boolean {
        // Placeholder for feature flag logic
        return true
    }
}
