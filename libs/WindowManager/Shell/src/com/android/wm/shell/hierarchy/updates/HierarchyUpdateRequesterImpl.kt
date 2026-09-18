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
package com.android.wm.shell.hierarchy.updates

import android.util.Log
import android.window.TaskCreationParams
import android.window.WindowContainerTransaction
import com.android.wm.shell.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_MODES
import com.android.wm.shell.transition.Transitions

/**
 * Helper class to request changes to the container hierarchy.
 * Allows overriding the wm/sf update appliers for easier testing.
 */
class HierarchyUpdateRequesterImpl(
    private val displayController: DisplayController,
    private val transitions: Transitions,
    private val hierarchy: ContainerHierarchy,
    private val updater: HierarchyUpdater,
    private val shellTaskOrganizer: ShellTaskOrganizer,
) : HierarchyUpdateRequester {

    init {
        if (Flags.enableShellModes()) {
            // Bind the update requester on the hierarchy (this is to get around some dependency
            // issues with modes and the requester)
            hierarchy.update = this
        }
    }

    /** @see HierarchyUpdateRequester.createTask */
    override fun createTask(params: TaskCreationParams, mode: Mode?): Container {
        if (params.name == null) {
            throw IllegalArgumentException("Root task must have an associated name")
        }
        val appearedInfo = shellTaskOrganizer.createTask(params, null)

        val task = hierarchy.getTask(appearedInfo!!.taskInfo.taskId)!!
        if (mode != null) {
            // Create task will synchronously call the updater's handleCreatedTask(), so we just
            // need to update the mode after the container is created
            task.mode = mode
        }
        return task
    }

    /** @see HierarchyUpdateRequester.removeTask */
    override fun removeTask(task: Container) {
        shellTaskOrganizer.deleteTask(task.token)
    }

    /** @see HierarchyUpdateRequester.wm */
    override fun wm(
        reason: String,
        transitType: Int,
        wct: WindowContainerTransaction,
    ) {
        // TODO: validate the given request -- look at the tokens and determine if the
        //  operations on that token are valid or not
        transitions.startTransition(transitType, wct, null)
    }

    /** @see HierarchyUpdateRequester.updateDisplay */
    override fun updateDisplay(
        displayId: Int,
        outWct: WindowContainerTransaction
    ) {
        val display = hierarchy.getDisplay(displayId)
        if (display == null) {
            Log.w(WM_SHELL_MODES.tag, "Update requested for unknown display: $displayId")
            return
        }
        // The display layout is always updated prior to this callback
        val displayLayout = displayController.getDisplayLayout(displayId)!!
        updater.updateDisplay(displayId, displayLayout, outWct)
    }
}