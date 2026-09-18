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
 * Represents a node in the layout tree. A node can be a container for other nodes (BranchNode) or a
 * container for a single app task (LeafNode).
 */
interface LayoutNode {
    /** The proportional weight of this node within its parent. */
    var weight: Float

    /** The calculated on-screen bounds of this node. */
    val bounds: Rect

    /** The parent of this node in the layout tree. */
    var parent: BranchNode?

    /** Debug ID */
    val debugName: String?

    /** Sets the on-screen bounds of this node during layout. */
    fun setBounds(bounds: Rect) {
        this.bounds.set(bounds)
    }
}
