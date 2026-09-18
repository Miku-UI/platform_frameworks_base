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
import android.graphics.Rect

/**
 * A terminal node in the layout tree that holds a single application task.
 *
 * @param taskInfo The task information for the application in this node.
 * @param weight The proportional weight of this node within its parent.
 */
class LeafNode(
    val taskInfo: RunningTaskInfo,
    override var weight: Float = 1.0f,
    override val debugName: String? = null,
) : LayoutNode {

    override val bounds = Rect()
    override var parent: BranchNode? = null

    override fun toString(): String {
        return "LeafNode(${debugName ?: ""}, w=$weight, taskId=${taskInfo.taskId})"
    }
}
