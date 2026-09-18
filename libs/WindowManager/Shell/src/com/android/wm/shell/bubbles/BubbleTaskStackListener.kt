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

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.bubbles.util.BubbleUtils.isBubbleMovedToAnotherRootTask
import com.android.wm.shell.bubbles.util.BubbleUtils.isBubbleToFullscreen
import com.android.wm.shell.bubbles.util.DefaultBubblePolicyHelper
import com.android.wm.shell.common.TaskStackListenerCallback
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY
import com.android.wm.shell.shared.bubbles.logging.BubbleLog

/**
 * Listens for task stack changes to manage associated bubble interactions.
 *
 * This class monitors task stack events, including task restarts and movements to the front, to
 * determine how bubbles should behave. It handles scenarios where bubbles should be expanded or
 * moved to fullscreen based on the task's windowing mode. This includes skipping split task
 * restarts, as they are handled by the split screen controller.
 *
 * @property bubbleHelper The [BubbleHelper] to query bubble state.
 * @property bubbleData The [BubbleData] to access and update bubble information.
 */
class BubbleTaskStackListener(
    private val bubbleHelper: BubbleHelper,
    private val bubbleData: BubbleData,
) : TaskStackListenerCallback {

    override fun onActivityRestartAttempt(
        task: ActivityManager.RunningTaskInfo,
        homeTaskVisible: Boolean,
        clearedTask: Boolean,
        wasVisible: Boolean,
    ) {
        val taskId = task.taskId
        ProtoLog.d(
            WM_SHELL_BUBBLES_NOISY,
            "BubbleTaskStackListener.onActivityRestartAttempt(): taskId=%d",
            taskId,
        )
        bubbleData.getBubbleInStackWithTaskId(taskId)?.let { bubble ->
            when {
                task.isBubbleToFullscreen() -> {
                    DefaultBubblePolicyHelper.moveExistingTaskOutOfBubble(
                        bubbleHelper,
                        bubble,
                        task,
                    )
                    if (bubbleData.isExpanded) bubbleData.isExpanded = false
                }
                // skip task restarts to other multi-tasking mode
                task.isBubbleMovedToAnotherRootTask(bubbleHelper) -> return
                !task.isAppBubbleMovingToFront() -> selectAndExpandInStackBubble(bubble, task)
            }
        }
    }

    override fun onTaskMovedToFront(task: ActivityManager.RunningTaskInfo) {
        val taskId = task.taskId
        bubbleData.getBubbleInStackWithTaskId(taskId)?.let { bubble ->
            if (bubble.currentTransition != null) {
                BubbleLog.d(
                    "BubbleTaskStackListener.onTaskMovedToFront(): taskId=%d bubble=%s" +
                        " has an active transition, skip handling",
                    taskId,
                    bubble.key,
                )
                return
            }
            BubbleLog.d(
                "BubbleTaskStackListener.onTaskMovedToFront(): taskId=%d bubble=%s",
                taskId,
                bubble.key,
            )
            if (task.isBubbleToFullscreen()) {
                DefaultBubblePolicyHelper.moveExistingTaskOutOfBubble(bubbleHelper, bubble, task)
                if (bubbleData.isExpanded) bubbleData.isExpanded = false
            }
        }
    }

    /**
     * Returns whether the given bubble task restart should move the app bubble to front and be
     * handled in DefaultMixedTransition#animateEnterBubblesFromBubble. This occurs when a
     * startActivity call resolves to an existing activity, causing the task to move to front, and
     * the mixed transition will then expand the bubble.
     */
    private fun ActivityManager.RunningTaskInfo?.isAppBubbleMovingToFront(): Boolean {
        return this?.activityType == ACTIVITY_TYPE_STANDARD && bubbleHelper.isAppBubbleTask(this)
    }

    /** Selects and expands a bubble that is currently in the stack. */
    private fun selectAndExpandInStackBubble(
        bubble: Bubble,
        task: ActivityManager.RunningTaskInfo,
    ) {
        BubbleLog.d(
            "BubbleTaskStackListener.selectAndExpandInStackBubble() taskId=%d bubble=%s",
            task.taskId,
            bubble.key,
        )
        bubbleData.setSelectedBubbleAndExpandStack(bubble)
    }
}
