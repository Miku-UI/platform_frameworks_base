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
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.WindowContainerToken
import com.android.wm.shell.shared.TransitionUtil.isClosingMode
import com.android.wm.shell.shared.TransitionUtil.isOpeningMode
import com.android.wm.shell.shared.bubbles.BubbleFlagHelper
import javax.inject.Inject

/** Helper class to query Bubble info from other components. */
class BubbleHelperImpl
@Inject
constructor(private val bubbleRootTask: BubbleRootTask, private val bubbleData: BubbleData) :
    BubbleHelper {
    override fun getAppBubbleRootTaskToken(): WindowContainerToken? =
        bubbleRootTask.windowContainerToken

    override fun getAppBubbleVisibilityBarrierToken(): WindowContainerToken? =
        bubbleRootTask.visibilityBarrierToken

    override fun getAppBubbleRootTaskId(): Int = bubbleRootTask.taskId

    override fun isAppBubbleRootTask(taskId: Int): Boolean =
        bubbleRootTask.taskId == taskId && taskId != INVALID_TASK_ID

    override fun isAppBubbleRootTask(taskInfo: ActivityManager.RunningTaskInfo?): Boolean {
        val info = taskInfo ?: return false
        return isAppBubbleRootTask(info.taskId)
    }

    override fun isAppBubbleTask(taskInfo: ActivityManager.RunningTaskInfo?): Boolean {
        val info = taskInfo ?: return false

        if (BubbleFlagHelper.enableRootTaskForBubble()) {
            return isAppBubbleRootTask(info.parentTaskId)
        }

        // Skip treating the task as an app bubble if it's transitioning from bubble to split or
        // desktop.
        // In BubblesTransitionObserver#removeBubbleIfLaunchingToSplit, a WCT is applied to set
        // LaunchNextToBubble=false. Then TaskViewTaskController#notifyTaskRemovalStarted is called,
        // which triggers this check. However, the isAppBubble flag is only updated during the next
        // Task#fillTaskInfo by the WM core, so the flag we are currently processing is still true.
        // Later, TaskViewTransitions#onExternalDone unblocks the animation. Without this check,
        // DefaultMixedHandler could misinterpret the OPEN change as a bubble-enter transition,
        // incorrectly re-creating the bubble instead of completing the split-screen transition.
        if (info.hasParentTask()) {
            return false
        }

        return info.isAppBubble
    }

    override fun isBubbleTask(taskInfo: ActivityManager.RunningTaskInfo?): Boolean {
        val info = taskInfo ?: return false
        return isAppBubbleTask(info) || bubbleData.hasAnyBubbleWithTaskId(info.taskId)
    }

    override fun getEnterBubbleTask(info: TransitionInfo): TransitionInfo.Change? =
        info.changes.firstOrNull { change ->
            val taskInfo = change.taskInfo
            // Exclude non-standard activity transition scenarios.
            taskInfo != null &&
                taskInfo.activityType == ACTIVITY_TYPE_STANDARD &&
                // Only process opening or change transitions
                // For CHANGE type change, only the one that is reparented to the Bubble root Task
                // should be considered as "entering" Bubble.
                (isOpeningMode(change.mode) || isReparentChange(change)) &&
                // Skip non-app-bubble tasks (e.g., a reused task in a bubble-to-fullscreen
                // scenario).
                isAppBubbleTask(taskInfo)
        }

    private fun isReparentChange(change: TransitionInfo.Change): Boolean {
        if (
            BubbleFlagHelper.enableRootTaskForBubble() &&
                com.android.window.flags.Flags.quickBubbleSwitch()
        ) {
            return change.mode == TRANSIT_CHANGE && change.lastParent != null
        }
        return change.mode == TRANSIT_CHANGE
    }

    override fun getClosingBubbleTask(info: TransitionInfo): TransitionInfo.Change? =
        info.changes.firstOrNull { change ->
            val taskInfo = change.taskInfo
            // Exclude non-standard activity transition scenarios.
            taskInfo != null &&
                taskInfo.activityType == ACTIVITY_TYPE_STANDARD &&
                // Only process closing transitions.
                isClosingMode(change.mode) &&
                // Skip non-app-bubble tasks (e.g., a reused task in a bubble-to-fullscreen
                // scenario).
                isAppBubbleTask(taskInfo)
        }

    override fun containsBubbleSwitch(info: TransitionInfo): Boolean =
        getEnterBubbleTask(info) != null && getClosingBubbleTask(info) != null
}
