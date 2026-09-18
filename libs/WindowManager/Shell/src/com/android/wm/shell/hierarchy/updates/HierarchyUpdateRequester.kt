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

import android.window.TaskCreationParams
import android.window.WindowContainerTransaction
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.modes.Mode

/**
 * Interface for requesting updates to a container hierarchy.
 */
interface HierarchyUpdateRequester {

    /**
     * Requests the creation of a new persistent shell-owned root task with the given parameters,
     * and an optional mode to directly associate with the created task. If a mode is specified,
     * {@link Mode#attachToContainer()} will also be called after the task is created.
     *
     * Because this is creating a new, empty container in both WM and the Shell hierarchy, it can be
     * called while another update is in progress.
     */
    fun createTask(
        params: TaskCreationParams,
        mode: Mode? = null
    ) : Container {
        throw IllegalStateException("Requesting update before initialization")
    }

    /**
     * Requests the removal of a task previously created with 'createTask()'. If there was a mode
     * associated with the task when it was created, then {@link Mode#detachFromContainer()} will
     * be called before the task is removed.
     *
     * This can be called while another update is in progress.
     */
    fun removeTask(task: Container) {
        throw IllegalStateException("Requesting update before initialization")
    }

    /**
     * Requests an update to the WindowManager hierarchy. This request will be sent to WM to be
     * applied, and then the container hierarchy will be updated when a new WM Transition is started
     * as a result of the requested changes (if there are any).
     *
     * This can be called while another update is in progress as the changes in the WCT are
     * requested to be applied asynchronously.
     */
    fun wm(
        reason: String,
        transitType: Int,
        wct: WindowContainerTransaction,
    ) {
        throw IllegalStateException("Requesting update before initialization")
    }

    /**
     * A call to specifically update the displays in the hierarchy, and for modes to be given an
     * opportunity to update the state of their managed tasks in the new display configuration.
     */
    fun updateDisplay(
        displayId: Int,
        outWct: WindowContainerTransaction
    ) {
        throw IllegalStateException("Requesting update before initialization")
    }
}