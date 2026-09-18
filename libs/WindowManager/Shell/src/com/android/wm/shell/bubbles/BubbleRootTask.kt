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
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.Context
import android.view.SurfaceControl
import android.window.TaskCreationParams
import android.window.TaskPropertiesRequest
import android.window.WindowContainerToken
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.shared.bubbles.BubbleFeatureConfig
import com.android.wm.shell.shared.bubbles.BubbleFlagHelper
import com.android.wm.shell.shared.bubbles.logging.BubbleLog
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.taskview.TaskViewRootTask
import javax.inject.Inject

/**
 * Class to manage the bubble root task.
 *
 * Note: This class should only be accessed through [BubbleHelper].
 */
@WMSingleton
class BubbleRootTask
@Inject
constructor(
    private val context: Context,
    shellInit: ShellInit,
    private val taskOrganizer: ShellTaskOrganizer,
    bubbleFeatureConfig: BubbleFeatureConfig,
) : ShellTaskOrganizer.TaskListener, TaskViewRootTask {

    init {
        if (
            BubbleFlagHelper.enableRootTaskForBubble() &&
                bubbleFeatureConfig.areAppBubblesSupported()
        ) {
            shellInit.addInitCallback({ onInit() }, this)
        }
    }

    override var rootTaskInfo: ActivityManager.RunningTaskInfo? = null
        private set

    val windowContainerToken: WindowContainerToken?
        get() = rootTaskInfo?.token

    val taskId: Int
        get() = rootTaskInfo?.taskId ?: INVALID_TASK_ID

    var visibilityBarrierToken: WindowContainerToken? = null
        private set

    /** Create the root task for bubbles. */
    private fun onInit() {
        // Create a root-task in WM Core. The app bubble tasks will be positioned as the leaf
        // tasks under this root-task.
        // The app bubble should be dismissed with proper transition (such as need to convert
        // it to fullscreen) if the bubble task is no longer be a leaf task under this leaf
        // task. Note that bubbles should ignore insets and should not show app compat rounded
        // corners for better UX (e.g. when landscape apps are letterboxed).
        val taskProperties =
            TaskPropertiesRequest()
                .setIgnoreInsets(true)
                .setDisableAppCompatRoundedCorners(true)
                .setReparentLeafTaskIfRelaunchFromHome(true)
                .setDisallowOverrideWindowingModeForChildren(true)
                // Preserve bubble leaf tasks if relaunched from different windowing mode.
                .setPreserveLeafTaskIfRelaunch(true)
                .setInterceptBackPressedOnTaskRoot(true)
                .setTaskForceExcludedFromRecents(true)
                .setDisablePip(true)
                .setDisableLaunchAdjacent(true)
                .setForceTranslucent(true)
                // We are using the visibility barrier to control the task visibility.
                // Force leaf tasks to be non-occluding, so that all tasks above the visibility
                // barrier can be visible.
                // During quick switch, TaskViewTransitions will ensure all participated tasks
                // are visible, and animate their visibility through TaskView instead of actually
                // toggling the window visibility, so that it doesn't have to wait for the activity
                // lifecycle until stable (user stop switching).
                .setForceLeafTasksNonOccluding(true)
        val params =
            TaskCreationParams.Builder()
                .setName("Bubbles")
                .setDisplayId(context.displayId)
                .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                .setOnTop(false)
                .setTaskPropertiesRequest(taskProperties)
                .build()
        val rootTaskAppearedInfo = taskOrganizer.createTask(params, this)
        if (rootTaskAppearedInfo == null) {
            BubbleLog.e("Failed to create Bubble root Task")
        }
    }

    override fun onTaskAppeared(taskInfo: ActivityManager.RunningTaskInfo, leash: SurfaceControl) {
        if (rootTaskInfo != null) {
            return
        }
        rootTaskInfo = taskInfo
        addVisibilityBarrier(taskInfo.token)
    }

    override fun onTaskInfoChanged(taskInfo: ActivityManager.RunningTaskInfo?) {
        if (taskInfo?.token == windowContainerToken) {
            rootTaskInfo = taskInfo
        }
    }

    private fun addVisibilityBarrier(rootTaskToken: WindowContainerToken) {
        val params =
            TaskCreationParams.Builder()
                .setName("Bubbles-visibility-barrier")
                .setDisplayId(context.displayId)
                .setParentContainer(rootTaskToken)
                .setVisibilityBarrier(true)
                .build()
        visibilityBarrierToken = taskOrganizer.createTask(params, this)?.taskInfo?.token
        if (visibilityBarrierToken == null) {
            BubbleLog.e("Failed to create Bubble visibility barrier")
        }
    }
}
