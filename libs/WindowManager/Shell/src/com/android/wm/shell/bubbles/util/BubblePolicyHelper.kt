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

package com.android.wm.shell.bubbles.util

import android.app.ActivityManager
import android.content.Context
import android.widget.Toast
import com.android.wm.shell.R
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.bubbles.BubbleHelper
import com.android.wm.shell.shared.bubbles.logging.BubbleLog
import com.android.wm.shell.taskview.TaskViewTaskController

/** Helper interface for methods related to bubbles policy. */
interface BubblePolicyHelper {
    /** Moves an existing task that is currently in the stack out of Bubble. */
    fun moveExistingTaskOutOfBubble(
        bubbleHelper: BubbleHelper,
        bubble: Bubble,
        task: ActivityManager.RunningTaskInfo,
    )

    /** Shows a toast indicating that the task cannot be bubbled. */
    fun showBubbleNotSupportedErrorToast(context: Context)

    /** Returns true if the task is valid for Bubble. */
    fun isValidToBubble(taskInfo: ActivityManager.RunningTaskInfo): Boolean
}

/** Default implementation of [BubblePolicyHelper]. */
object DefaultBubblePolicyHelper : BubblePolicyHelper {

    override fun moveExistingTaskOutOfBubble(
        bubbleHelper: BubbleHelper,
        bubble: Bubble,
        task: ActivityManager.RunningTaskInfo,
    ) {
        BubbleLog.d(
            "BubblePolicyHelper.moveExistingTaskOutOfBubble() taskId=%d bubble=%s",
            task.taskId,
            bubble.key,
        )

        // Show an error toast if the task became invalid to bubble and that is why we're moving it
        // out of bubble.
        if (!isValidToBubble(task)) {
            showBubbleNotSupportedErrorToast(bubble.taskView.getContext())
        }
        val taskViewTaskController: TaskViewTaskController = bubble.taskView.controller
        val taskOrganizer: ShellTaskOrganizer = taskViewTaskController.taskOrganizer

        val wct =
            BubbleUtils.getExitBubbleTransaction(
                bubbleHelper,
                task.token,
                bubble.taskView.captionInsetsOwner,
            )
        taskOrganizer.applyTransaction(wct)

        taskViewTaskController.notifyTaskRemovalStarted(task)
    }

    override fun showBubbleNotSupportedErrorToast(context: Context) {
        Toast.makeText(context, R.string.bubble_not_supported_text, Toast.LENGTH_SHORT).show()
    }

    /**
     * Returns true if the task is valid for Bubble.
     *
     * For now, this is just if the task supports multi-window, which checks under the hood if the
     * system framework policy supports multi-window for non-resizable activities. The support is
     * defined by a device specific config and can be conditional on screen size.
     */
    override fun isValidToBubble(taskInfo: ActivityManager.RunningTaskInfo): Boolean {
        return taskInfo.supportsMultiWindow
    }
}
