/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.wm.shell.hierarchy.properties

import android.app.ActivityManager.RunningTaskInfo
import android.window.TransitionInfo
import com.android.wm.shell.common.ComponentUtils
import com.android.wm.shell.dagger.hierarchy.WmSyncedProperty
import com.android.wm.shell.hierarchy.updates.HierarchyChangeFlags
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_PIP_PARAMS
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_TASK_DESCRIPTION

/**
 * Properties for a container that is associated with a task in the WindowManager hierarchy.
 */
class TaskContainerProperties(
    @WmSyncedProperty var taskInfo: RunningTaskInfo,
) : ContainerProperties(taskInfo.userId) {
    // Convenience property
    val taskId: Int
        get() = taskInfo.taskId

    /**
     * There are a some properties that are currently still only updated via
     * TaskOrganizer.onTaskInfoChanged(). We should still update them on the container properties
     * when that happens, and also diff them below.
     */
    fun updateFromTaskInfoChanged(info: RunningTaskInfo) {
        // Update the task description for window decors/task backgrounds
        taskInfo.taskDescription = info.taskDescription
        // Update the PIP params
        taskInfo.pictureInPictureParams = info.pictureInPictureParams
    }

    /** @see ContainerProperties.updateFromWindowChange */
    override fun updateFromWindowChange(change: TransitionInfo.Change) {
        val info = change.taskInfo
        super.updateFromWindowChange(change)
        if (info == null) {
            return
        }
        taskInfo = info
        config = info.configuration
    }

    /** @see ContainerProperties.diff */
    override fun diff(
        other: ContainerProperties,
        chgs: HierarchyChangeFlags
    ) {
        super.diff(other, chgs)
        val otherTask = other as TaskContainerProperties
        chgs.compareAndSet(
            taskInfo.taskDescription,
            otherTask.taskInfo.taskDescription,
            CHANGED_TASK_DESCRIPTION
        )
        chgs.compareAndSet(
            taskInfo.pictureInPictureParams,
            otherTask.taskInfo.pictureInPictureParams,
            CHANGED_PIP_PARAMS
        )
    }

    /** @see ContainerProperties.copy */
    override fun copy(): TaskContainerProperties {
        return TaskContainerProperties(RunningTaskInfo(taskInfo)).apply {
            copyFrom(this@TaskContainerProperties)
        }
    }

    /** @see ContainerProperties.propsToString */
    override fun propsToString(): String {
        return "#$taskId pkg=${ComponentUtils.getPackageName(taskInfo)} | " + super.propsToString()
    }

    /** @see ContainerProperties.getTypeName */
    override fun getTypeName(): String {
        return "Task#$taskId"
    }
}