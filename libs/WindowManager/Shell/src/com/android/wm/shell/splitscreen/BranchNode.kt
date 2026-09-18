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

import android.annotation.IntDef
import android.graphics.Rect

/**
 * A node in the layout tree that contains other nodes. It represents a split container that can be
 * oriented either vertically or horizontally.
 *
 * @param orientation The orientation of the split, either [ORIENTATION_VERTICAL] or
 *   [ORIENTATION_HORIZONTAL].
 * @param children The children of this node.
 * @param weight The proportional weight of this node within its parent.
 * @param dividerSize The size of the space in pixels to leave between children.
 * @param isOffscreen If true, children are laid out with one mostly offscreen.
 * @param mainChildIndex The index of the child that is primary when isOffscreen is true.
 */
class BranchNode(
    @Orientation val orientation: Int,
    children: List<LayoutNode>,
    override var weight: Float = 1.0f,
    val dividerSize: Int = 0,
    val isOffscreen: Boolean = false,
    val mainChildIndex: Int = 0,
    override val debugName: String? = null,
) : LayoutNode {

    @IntDef(prefix = ["ORIENTATION_"], value = [ORIENTATION_VERTICAL, ORIENTATION_HORIZONTAL])
    annotation class Orientation

    private val childrenInternal: MutableList<LayoutNode> = mutableListOf()

    /** The children of this node. */
    val children: List<LayoutNode>
        get() = childrenInternal

    override val bounds = Rect()
    override var parent: BranchNode? = null

    init {
        children.forEach { child ->
            child.parent = this
            childrenInternal.add(child)
        }
    }

    override fun toString(): String {
        val orientationStr = if (orientation == ORIENTATION_VERTICAL) "V" else "H"
        return "BranchNode(${debugName ?: ""}, $orientationStr, w=$weight, " +
            "children=${children.size}, offscreen=$isOffscreen)"
    }

    /** Adds a child to this node at the given index. */
    fun addChild(child: LayoutNode, index: Int) {
        child.parent = this
        childrenInternal.add(index, child)
    }

    /** Removes a child from this node. */
    fun removeChild(child: LayoutNode): Boolean {
        return childrenInternal.remove(child)
    }

    companion object {
        /** Vertical orientation for a split. */
        const val ORIENTATION_VERTICAL = 0

        /** Horizontal orientation for a split. */
        const val ORIENTATION_HORIZONTAL = 1
    }
}
