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

import android.app.ActivityManager.RunningTaskInfo

/**
 * A serializable data structure that defines the hierarchy and properties of a multi-app layout.
 * This can be used to construct a LayoutNode tree.
 *
 * @property taskInfo The task information if this is a leaf node.
 * @property orientation The orientation of the split if this is a branch node.
 * @property children The children of this node if it is a branch node.
 * @property weight The proportional weight of this node within its parent.
 */
data class LayoutDefinition(
    var taskInfo: RunningTaskInfo? = null,
    var orientation: Int = 0,
    var children: List<LayoutDefinition>? = null,
    var weight: Float = 0f,
) {

    /** Returns true if this is a leaf node. */
    val isLeaf: Boolean
        get() = taskInfo != null

    /**
     * A builder for creating complex LayoutDefinition objects.
     *
     * @param orientation The orientation of the split.
     */
    class Builder(private val orientation: Int) {
        private var totalWeight = 0f
        private val children = mutableListOf<LayoutDefinition>()

        /**
         * Adds a node to the layout.
         *
         * @param weight The proportional weight of the node.
         * @param node The node to add.
         */
        fun addNode(weight: Float, node: LayoutDefinition): Builder {
            node.weight = weight
            children.add(node)
            totalWeight += weight
            return this
        }

        /**
         * Adds a task to the layout.
         *
         * @param weight The proportional weight of the task.
         * @param taskInfo The task to add.
         */
        fun addTask(weight: Float, taskInfo: RunningTaskInfo): Builder {
            val leaf = LayoutDefinition(taskInfo = taskInfo, weight = weight)
            children.add(leaf)
            totalWeight += weight
            return this
        }

        /** Builds the LayoutDefinition. */
        fun build(): LayoutDefinition {
            if (totalWeight > 1.0f) {
                // Normalize weights if they exceed 1.0
                for (child in children) {
                    child.weight /= totalWeight
                }
            }
            return LayoutDefinition(orientation = orientation, children = children)
        }
    }
}
