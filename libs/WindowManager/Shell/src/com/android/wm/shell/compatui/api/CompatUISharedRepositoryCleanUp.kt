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

package com.android.wm.shell.compatui.api

import android.app.ActivityManager.RunningTaskInfo
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTaskOrganizer.TaskVanishedListener
import com.android.wm.shell.sysui.ShellInit

/** This object is responsible for managing the Shared state in [CompatUISharedStateRepository]. */
class CompatUISharedRepositoryCleanUp(
    shellInit: ShellInit,
    shellTaskOrganizer: ShellTaskOrganizer,
    private val sharedStateRepository: CompatUISharedStateRepository,
) : TaskVanishedListener {

    init {
        shellInit.addInitCallback({ shellTaskOrganizer.addTaskVanishedListener(this) }, this)
    }

    override fun onTaskVanished(taskInfo: RunningTaskInfo) {
        sharedStateRepository.delete(taskInfo.taskId)
    }
}
