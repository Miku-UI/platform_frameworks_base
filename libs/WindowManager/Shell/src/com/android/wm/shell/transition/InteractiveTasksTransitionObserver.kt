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

package com.android.wm.shell.transition

import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.sysui.ShellInit

/**
 * An observer that hooks into the transitions and searches [TransitionInfo] to find lifecycle
 * changes to update the [ShellTaskOrganizer].
 */
class InteractiveTasksTransitionObserver(
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val shellTaskOrganizer: ShellTaskOrganizer,
) : Transitions.TransitionObserver {

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        transitions.registerObserver(this)
    }

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    ) {
        info.changes
            .asSequence()
            .mapNotNull { change -> change.taskInfo }
            .forEach { taskInfo -> shellTaskOrganizer.onTaskInfoUpdated(taskInfo) }
    }
}
