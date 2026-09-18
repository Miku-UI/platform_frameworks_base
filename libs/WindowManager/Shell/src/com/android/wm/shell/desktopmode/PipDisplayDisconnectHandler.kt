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

package com.android.wm.shell.desktopmode

import android.app.TaskInfo
import android.view.Display.DEFAULT_DISPLAY
import android.window.DisplayAreaInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.common.pip.PipBoundsState
import com.android.wm.shell.common.pip.PipDisplayLayoutState
import com.android.wm.shell.pip2.phone.PipDisplayTransferHandler
import com.android.wm.shell.pip2.phone.PipScheduler
import com.android.wm.shell.pip2.phone.PipTransitionState
import com.android.wm.shell.protolog.ShellProtoLogGroup

/**
 * Transition handler that handles reparenting pip tasks on display disconnect.
 *
 * TODO: b/488139835 Consider moving this out of desktop package.
 */
class PipDisplayDisconnectHandler(
    private val pipScheduler: PipScheduler,
    private val pipState: PipTransitionState,
    private val pipBoundsState: PipBoundsState,
    private val pipDisplayLayoutState: PipDisplayLayoutState,
    private val desktopState: ShellDesktopState,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val pipDisplayTransferHandler: PipDisplayTransferHandler,
    private val pipDisplayReconnectHandler: PipDisplayReconnectHandler,
) {

    /**
     * Schedules a pip operation when display is disconnected. Small screen form factors will exit
     * pip and move the task to the back while large screen form factors will move the pipped task
     * to the internal display.
     */
    fun onDisplayDisconnect(
        disconnectedDisplayId: Int,
        reparentDisplayId: Int,
    ): WindowContainerTransaction {
        val wct = WindowContainerTransaction()
        val pipTask = pipState.pipTaskInfo
        val reparentDisplayAreaInfo =
            rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(reparentDisplayId)
        if (reparentDisplayAreaInfo == null) {
            ProtoLog.d(
                ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "reparentDisplayAreaInfo is null; aborting",
            )
            return wct
        }
        if (pipTask == null) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE, "pipTask is null; aborting")
            return wct
        }
        if (pipDisplayLayoutState.displayId != disconnectedDisplayId) {
            ProtoLog.d(
                ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "pipTask not on disconnected display; aborting",
            )
            return wct
        }
        if (desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)) {
            ProtoLog.d(
                ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "Moving pipTask to internal display",
            )
            pipDisplayReconnectHandler.preserveTask(
                pipTask.taskId,
                disconnectedDisplayId,
                pipTask.userId,
                pipBoundsState.bounds,
            )
            scheduleMovePipToInternalDisplay(
                pipTask,
                disconnectedDisplayId,
                reparentDisplayAreaInfo,
                wct,
            )
        } else {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE, "Scheduling pip exit.")
            schedulePipExitOnInternalDisplay(reparentDisplayAreaInfo.token, pipTask, wct)
        }
        return wct
    }

    private fun scheduleMovePipToInternalDisplay(
        pipTask: TaskInfo,
        disconnectedDisplayId: Int,
        reparentDisplayAreaInfo: DisplayAreaInfo,
        wct: WindowContainerTransaction,
    ) {
        val bounds =
            if (pipBoundsState.motionBoundsState.isInMotion) {
                pipBoundsState.motionBoundsState.boundsInMotion
            } else {
                pipBoundsState.bounds
            }
        wct.reparent(pipTask.token, reparentDisplayAreaInfo.token, /* onTop= */ true)
        pipDisplayTransferHandler.scheduleMovePipToDisplay(
            disconnectedDisplayId,
            reparentDisplayAreaInfo.displayId,
            bounds,
        )
    }

    private fun schedulePipExitOnInternalDisplay(
        reparentDisplayAreaToken: WindowContainerToken,
        pipTask: TaskInfo,
        wct: WindowContainerTransaction,
    ) {
        // Move the pipped task to internal display and remove pip.
        wct.reparent(pipTask.token, reparentDisplayAreaToken, /* onTop= */ false)
        pipScheduler.scheduleRemovePip(/* withFadeout= */ false)
    }

    companion object {
        private const val TAG = "PipDisplayDisconnectHandler"
    }
}
