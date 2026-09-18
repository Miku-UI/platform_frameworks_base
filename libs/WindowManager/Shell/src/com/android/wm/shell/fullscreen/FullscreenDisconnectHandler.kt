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

package com.android.wm.shell.fullscreen

import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.window.WindowContainerTransaction
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.desktopmode.DesktopWallpaperActivity
import com.android.wm.shell.windowdecor.extension.isFullscreen

/** Transition handler that handles reparenting fullscreen tasks on display disconnect. */
class FullscreenDisconnectHandler(
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val fullscreenReconnectHandler: FullscreenReconnectHandler,
) {

    /**
     * Constructs a wct to reparent all fullscreen tasks on the disconnected display to the
     * reparentDisplay. Excludes non-standard activity types and DesktopWallpaperActivity tasks.
     */
    fun onDisplayDisconnect(
        disconnectedDisplayId: Int,
        reparentDisplay: Int,
    ): WindowContainerTransaction {
        val wct = WindowContainerTransaction()
        val taskDisplayArea =
            rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(reparentDisplay) ?: return wct
        for (task in shellTaskOrganizer.getRunningTasks(disconnectedDisplayId)) {
            if (
                !task.isFullscreen ||
                    task.activityType != ACTIVITY_TYPE_STANDARD ||
                    DesktopWallpaperActivity.isWallpaperTask(task)
            ) {
                continue
            }
            // TODO (b/434035592): Preserve display state for all users, not just current.
            fullscreenReconnectHandler.preserveTask(
                task.taskId,
                disconnectedDisplayId,
                task.userId,
                task.isFocused,
            )
            wct.reparent(task.token, taskDisplayArea.token, /* onTop= */ false)
        }
        return wct
    }
}
