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

import android.graphics.Point
import android.graphics.Rect

/**
 * A stateful manager for a recursive split-screen layout. It holds the current layout tree, handles
 * user interactions (divider drags), and orchestrates calls to its stateless helpers (LayoutPolicy,
 * DividerSnapAlgorithm, LayoutEngine).
 */
class RecursiveSplitLayout {

    var listener: OnLayoutChangeListener? = null

    private var currentTree: LayoutNode? = null
    private val layoutPolicy = LayoutPolicy()
    private val snapAlgorithm = DividerSnapAlgorithmTemp()
    private val layoutEngine = LayoutEngine()

    fun setLayout(tree: LayoutNode) {
        currentTree = tree
    }

    /**
     * Calculates the bounds for all elements in the current layout tree by delegating to the
     * LayoutEngine.
     *
     * @param rootBounds The total available space for the layout.
     * @return A CalculatedBounds containing the calculated bounds.
     */
    fun calculateLayout(rootBounds: Rect): CalculatedBounds {
        val tree = currentTree ?: return CalculatedBounds(emptyMap(), emptyMap())
        return layoutEngine.calculateLayout(tree, rootBounds)
    }

    /**
     * Called by the controller when a divider drag is finished. This will calculate the new layout
     * and notify the listener.
     */
    fun onDividerReleased(nodeBefore: LayoutNode, nodeAfter: LayoutNode, releasePosition: Point) {
        val parentNode = nodeBefore.parent ?: return
        val combinedBounds = Rect(nodeBefore.bounds).apply { union(nodeAfter.bounds) }
        val orientation = parentNode.orientation
        val relevantReleasePos =
            if (orientation == BranchNode.ORIENTATION_HORIZONTAL) releasePosition.x
            else releasePosition.y

        val availablePoints = layoutPolicy.getAvailableSnapPoints(nodeBefore, nodeAfter)
        val finalTarget =
            snapAlgorithm.findClosestSnapTarget(
                relevantReleasePos,
                availablePoints,
                combinedBounds,
                orientation,
            )

        val totalWeight = nodeBefore.weight + nodeAfter.weight
        val newWeightBefore = totalWeight * finalTarget.proportion
        val newWeightAfter = totalWeight - newWeightBefore

        val weightChanges = mapOf(nodeBefore to newWeightBefore, nodeAfter to newWeightAfter)

        val newTree = cloneTreeWithChanges(currentTree!!, weightChanges)
        setLayout(newTree)

        // The owner is responsible for getting the root bounds.
        // For now, we use the old bounds as an estimate.
        val newResult = calculateLayout(parentNode.bounds)
        listener?.onLayoutChanged(newResult)
    }

    private fun cloneTreeWithChanges(
        original: LayoutNode,
        changes: Map<LayoutNode, Float>,
    ): LayoutNode {
        val newWeight = changes[original] ?: original.weight
        return when (original) {
            is LeafNode -> LeafNode(original.taskInfo, newWeight)
            is BranchNode -> {
                val newChildren = original.children.map { cloneTreeWithChanges(it, changes) }
                BranchNode(
                        original.orientation,
                        newChildren,
                        newWeight,
                        original.dividerSize,
                        original.isOffscreen,
                        original.mainChildIndex,
                    )
                    .also { newParent -> newParent.children.forEach { it.parent = newParent } }
            }
            else -> throw IllegalStateException("Unknown LayoutNode type")
        }
    }
}
