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

/**
 * Holds the calculated bounds for all elements in a split-screen layout. This is the output of the
 * RecursiveSplitLayout calculation.
 */
data class CalculatedBounds(
    val leafNodeBounds: Map<LeafNode, Rect>,
    val dividerBounds: Map<Pair<LayoutNode, LayoutNode>, Rect>,
) {
    override fun toString(): String {
        val sb = StringBuilder("LayoutResult:\n")
        sb.append("  Leaf Node Bounds:\n")
        leafNodeBounds.forEach { (leaf, bounds) ->
            val taskId = leaf.taskInfo?.taskId ?: "null"
            sb.append("    - Task($taskId): $bounds\n")
        }
        sb.append("  Divider Bounds:\n")
        dividerBounds.forEach { (nodes, bounds) ->
            val task1 = (nodes.first as? LeafNode)?.taskInfo?.taskId ?: "Branch"
            val task2 = (nodes.second as? LeafNode)?.taskInfo?.taskId ?: "Branch"
            sb.append("    - Divider($task1 | $task2): $bounds\n")
        }
        return sb.toString()
    }
}

/** Listener for receiving updates when the layout changes. */
interface OnLayoutChangeListener {
    fun onLayoutChanged(result: CalculatedBounds)
}
