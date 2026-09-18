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
import android.util.Log
import kotlin.math.roundToInt

/**
 * A pure, stateless calculator for determining the bounds of all nodes in a recursive layout tree.
 *
 * This class is designed to be a testable component, separating the complex mathematical logic of
 * layout calculation from state management (e.g., StageCoordinator) and UI rendering (e.g., a
 * custom ViewGroup).
 */
class LayoutEngine {

    companion object {
        private const val TAG = "LayoutEngine"
    }

    /**
     * The main entry point for the layout calculation. It recursively traverses the provided tree
     * and returns a [CalculatedBounds] containing the final bounds for all leaf nodes and dividers.
     *
     * @param rootNode The root of the [LayoutNode] tree that defines the entire layout.
     * @param screenBounds The total available [Rect] (e.g., the full display area) into which the
     *   layout should be fitted.
     * @return A [CalculatedBounds] containing the calculated bounds for all leaf nodes and
     *   dividers.
     */
    fun calculateLayout(rootNode: LayoutNode, screenBounds: Rect): CalculatedBounds {
        Log.d(TAG, "calculateLayout: rootNode=$rootNode, screenBounds=$screenBounds")
        val allNodeBounds = mutableMapOf<LayoutNode, Rect>()
        val dividerBounds = mutableMapOf<Pair<LayoutNode, LayoutNode>, Rect>()

        recursiveCalculate(rootNode, screenBounds, allNodeBounds, dividerBounds)

        val leafNodeBounds =
            allNodeBounds
                .filterKeys { it is LeafNode }
                .mapValues { it.value }
                .mapKeys { it.key as LeafNode }

        Log.d(
            TAG,
            "calculateLayout finished: leafNodeBounds=$leafNodeBounds, " +
                "dividerBounds=$dividerBounds",
        )
        return CalculatedBounds(leafNodeBounds, dividerBounds)
    }

    /**
     * The recursive helper function that traverses the tree. For each node, it calculates the
     * bounds for its direct children and then calls itself for each of those children.
     *
     * @param currentNode The node currently being processed.
     * @param parentBounds The bounds allocated by the parent, within which the current node must
     *   lay out its children.
     * @param allNodeBounds A mutable map to accumulate the bounds of every node in the tree during
     *   traversal.
     * @param dividerBounds A mutable map to accumulate the bounds of every divider.
     */
    private fun recursiveCalculate(
        currentNode: LayoutNode,
        parentBounds: Rect,
        allNodeBounds: MutableMap<LayoutNode, Rect>,
        dividerBounds: MutableMap<Pair<LayoutNode, LayoutNode>, Rect>,
    ) {
        Log.d(TAG, "recursiveCalculate: currentNode=$currentNode, parentBounds=$parentBounds")
        allNodeBounds[currentNode] = parentBounds

        if (currentNode is BranchNode) {
            if (currentNode.isOffscreen) {
                calculateFlexibleSplit(currentNode, parentBounds, allNodeBounds, dividerBounds)
            } else {
                calculateStandardSplit(currentNode, parentBounds, allNodeBounds, dividerBounds)
            }

            for (child in currentNode.children) {
                val childBounds = allNodeBounds[child]
                if (childBounds != null) {
                    recursiveCalculate(child, childBounds, allNodeBounds, dividerBounds)
                }
            }
        }
    }

    /**
     * Calculates the bounds for children of a standard (non-flexible) [BranchNode], distributing
     * space according to each child's weight.
     */
    private fun calculateStandardSplit(
        node: BranchNode,
        bounds: Rect,
        allNodeBounds: MutableMap<LayoutNode, Rect>,
        dividerBounds: MutableMap<Pair<LayoutNode, LayoutNode>, Rect>,
    ) {
        Log.d(TAG, "calculateStandardSplit: node=$node, bounds=$bounds")
        val dividerSize = node.dividerSize
        val totalWeight = node.children.fold(0.0f) { acc, child -> acc + child.weight }
        if (totalWeight == 0.0f) return

        val totalDividerSpace = (node.children.size - 1) * dividerSize

        if (node.orientation == BranchNode.ORIENTATION_HORIZONTAL) {
            val availableWidth = bounds.width() - totalDividerSpace
            var currentX = bounds.left.toFloat()
            for ((index, child) in node.children.withIndex()) {
                if (index > 0) {
                    val prevChild = node.children[index - 1]
                    val dividerRect =
                        Rect(
                            currentX.roundToInt(),
                            bounds.top,
                            (currentX + dividerSize).roundToInt(),
                            bounds.bottom,
                        )
                    dividerBounds[Pair(prevChild, child)] = dividerRect
                    currentX += dividerSize
                }

                val childWidth = (child.weight / totalWeight) * availableWidth
                val childBounds =
                    Rect(
                        currentX.roundToInt(),
                        bounds.top,
                        (currentX + childWidth).roundToInt(),
                        bounds.bottom,
                    )
                allNodeBounds[child] = childBounds
                Log.d(TAG, "  - child $index bounds: $childBounds")
                currentX += childWidth
            }
        } else { // VERTICAL
            val availableHeight = bounds.height() - totalDividerSpace
            var currentY = bounds.top.toFloat()
            for ((index, child) in node.children.withIndex()) {
                if (index > 0) {
                    val prevChild = node.children[index - 1]
                    val dividerRect =
                        Rect(
                            bounds.left,
                            currentY.roundToInt(),
                            bounds.right,
                            (currentY + dividerSize).roundToInt(),
                        )
                    dividerBounds[Pair(prevChild, child)] = dividerRect
                    currentY += dividerSize
                }

                val childHeight = (child.weight / totalWeight) * availableHeight
                val childBounds =
                    Rect(
                        bounds.left,
                        currentY.roundToInt(),
                        bounds.right,
                        (currentY + childHeight).roundToInt(),
                    )
                allNodeBounds[child] = childBounds
                Log.d(TAG, "  - child $index bounds: $childBounds")
                currentY += childHeight
            }
        }
    }

    /**
     * Calculates the bounds for children of a "Flexible Split" [BranchNode], where one child is
     * primarily visible and the other is mostly laid out off-screen.
     */
    private fun calculateFlexibleSplit(
        node: BranchNode,
        bounds: Rect,
        allNodeBounds: MutableMap<LayoutNode, Rect>,
        dividerBounds: MutableMap<Pair<LayoutNode, LayoutNode>, Rect>,
    ) {
        Log.d(TAG, "calculateFlexibleSplit: node=$node, bounds=$bounds")
        if (node.children.size != 2) return

        val dividerSize = node.dividerSize
        val child1 = node.children[0]
        val child2 = node.children[1]

        if (node.orientation == BranchNode.ORIENTATION_HORIZONTAL) {
            val availableWidth = bounds.width() - dividerSize
            val dominantWidth = availableWidth * 0.9f
            val nonDominantVisibleWidth = availableWidth * 0.1f

            val child1Bounds: Rect
            val child2Bounds: Rect

            if (node.mainChildIndex == 0) { // Child 1 is dominant
                child1Bounds =
                    Rect(
                        bounds.left,
                        bounds.top,
                        (bounds.left + dominantWidth).roundToInt(),
                        bounds.bottom,
                    )
                val dividerLeft = child1Bounds.right
                child2Bounds =
                    Rect(
                        dividerLeft + dividerSize,
                        bounds.top,
                        (dividerLeft + dividerSize + dominantWidth).roundToInt(),
                        bounds.bottom,
                    )
            } else { // Child 2 is dominant
                val dividerLeft = (bounds.left + nonDominantVisibleWidth).roundToInt()
                child1Bounds =
                    Rect(
                        (dividerLeft - dominantWidth).roundToInt(),
                        bounds.top,
                        dividerLeft,
                        bounds.bottom,
                    )
                child2Bounds =
                    Rect(
                        dividerLeft + dividerSize,
                        bounds.top,
                        (dividerLeft + dividerSize + dominantWidth).roundToInt(),
                        bounds.bottom,
                    )
            }

            allNodeBounds[child1] = child1Bounds
            allNodeBounds[child2] = child2Bounds
            dividerBounds[Pair(child1, child2)] =
                Rect(child1Bounds.right, bounds.top, child2Bounds.left, bounds.bottom)
            Log.d(TAG, "  - child 1 bounds: $child1Bounds child 2 bounds: $child2Bounds")
        } else { // VERTICAL
            val availableHeight = bounds.height() - dividerSize
            val dominantHeight = availableHeight * 0.9f
            val nonDominantVisibleHeight = availableHeight * 0.1f

            val child1Bounds: Rect
            val child2Bounds: Rect

            if (node.mainChildIndex == 0) { // Child 1 is dominant
                child1Bounds =
                    Rect(
                        bounds.left,
                        bounds.top,
                        bounds.right,
                        (bounds.top + dominantHeight).roundToInt(),
                    )
                val dividerTop = child1Bounds.bottom
                child2Bounds =
                    Rect(
                        bounds.left,
                        dividerTop + dividerSize,
                        bounds.right,
                        (dividerTop + dividerSize + dominantHeight).roundToInt(),
                    )
            } else { // Child 2 is dominant
                val dividerTop = (bounds.top + nonDominantVisibleHeight).roundToInt()
                child1Bounds =
                    Rect(
                        bounds.left,
                        (dividerTop - dominantHeight).roundToInt(),
                        bounds.right,
                        dividerTop,
                    )
                child2Bounds =
                    Rect(
                        bounds.left,
                        dividerTop + dividerSize,
                        bounds.right,
                        (dividerTop + dividerSize + dominantHeight).roundToInt(),
                    )
            }

            allNodeBounds[child1] = child1Bounds
            allNodeBounds[child2] = child2Bounds
            dividerBounds[Pair(child1, child2)] =
                Rect(bounds.left, child1Bounds.bottom, bounds.right, child2Bounds.top)
            Log.d(TAG, "  - child 1 bounds: $child1Bounds child 2 bounds: $child2Bounds")
        }
    }
}
