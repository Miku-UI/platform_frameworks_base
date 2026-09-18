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

package com.android.wm.shell.splitscreen

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.FullscreenRequestHandler.REQUEST_ALLOW_MODE_ENTER
import android.app.FullscreenRequestHandler.RESULT_FAILED_NOT_SUPPORTED
import android.os.IBinder
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.ClientFullscreenRequestController
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.EnterResult
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.EnterResult.Approved.RestorableState
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.ExitResult
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN
import com.android.wm.shell.shared.split.SplitScreenConstants
import com.android.wm.shell.shared.split.SplitScreenConstants.SnapPosition
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitPosition
import com.android.wm.shell.splitscreen.SplitScreen.StageType

/**
 * Handles client-started fullscreen requests when the requester task was a split-screen task. See
 * [android.app.Activity.requestFullscreenMode].
 *
 * Supports moving a split task to fullscreen and back into split with its original pairing. It does
 * not support restoring to split-select, restoring with a different task if the original paired
 * task was removed during the transient fullscreen state, or 2x1 and other flexible split
 * configurations.
 */
class SplitScreenFullscreenRequestHandler(
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val stageCoordinator: StageCoordinatorAbstract,
    clientFullscreenRequestController: ClientFullscreenRequestController,
) : FullscreenRequestHandler {

    init {
        clientFullscreenRequestController.addHandler(this)
        // Let tasks under the split root request fullscreen mode.
        stageCoordinator.setFullscreenRequestAllowMode(REQUEST_ALLOW_MODE_ENTER)
    }

    override val name: String = TAG

    @SuppressLint("WrongConstant")
    override fun handleEnterFullscreen(
        transition: IBinder,
        task: ActivityManager.RunningTaskInfo,
    ): EnterResult? {
        if (!stageCoordinator.isSplitActive()) return null
        if (!stageCoordinator.isSplitScreenVisible()) return null
        if (!stageCoordinator.isTaskOnSplitDisplay(task)) return null
        @StageType val stageType: Int = stageCoordinator.getCurrentStageTypeOfTask(task.taskId)
        @SplitPosition val position: Int = stageCoordinator.getSplitPosition(task.taskId)
        @SnapPosition val snapPosition: Int = stageCoordinator.calculateCurrentSnapPosition()
        @SplitPosition
        val oppositePosition =
            if (position == SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT)
                SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
            else SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
        val otherTaskId: Int = stageCoordinator.getTaskId(oppositePosition)

        if (!SplitScreenConstants.isPersistentSnapPosition(snapPosition)) {
            ProtoLog.d(
                WM_SHELL_SPLIT_SCREEN,
                "handleEnterFullscreen: snap position is not persistent, rejecting request for " +
                    "transition=%s, taskId=%d, stageType=%d, position=%d, snapPosition=%d, " +
                    "otherTaskId=%d",
                transition,
                task.taskId,
                stageType,
                position,
                snapPosition,
                otherTaskId,
            )
            return EnterResult.Failed(RESULT_FAILED_NOT_SUPPORTED, this)
        }

        ProtoLog.d(
            WM_SHELL_SPLIT_SCREEN,
            "handleEnterFullscreen: approving request for transition=%s, " +
                "taskId=%d, stageType=%d, position=%d, snapPosition=%d, otherTaskId=%d",
            transition,
            task.taskId,
            stageType,
            position,
            snapPosition,
            otherTaskId,
        )

        val wct = WindowContainerTransaction()
        stageCoordinator.prepareExitSplitScreen(
            stageType,
            wct,
            SplitScreenController.EXIT_REASON_FULLSCREEN_REQUEST,
        )
        stageCoordinator.splitTransitions.setDismissTransition(
            transition,
            stageType,
            SplitScreenController.EXIT_REASON_FULLSCREEN_REQUEST,
        )

        val state =
            RestorableState.SplitScreen(
                originalSplitPosition = position,
                originalSnapPosition = snapPosition,
                otherTaskId = otherTaskId,
            )
        return EnterResult.Approved(wct, this, state)
    }

    override fun handleExitFullscreen(
        transition: IBinder,
        task: ActivityManager.RunningTaskInfo,
        restorableState: RestorableState?,
    ): ExitResult? {
        if (restorableState == null) {
            ProtoLog.d(
                WM_SHELL_SPLIT_SCREEN,
                "handleExitFullscreen with null restorable state, ignoring",
            )
            return null
        }
        if (restorableState !is RestorableState.SplitScreen) {
            ProtoLog.d(
                WM_SHELL_SPLIT_SCREEN,
                "handleExitFullscreen for non split-screen state, ignoring",
            )
            return null
        }
        val otherTaskId = restorableState.otherTaskId
        val otherTaskRunning = shellTaskOrganizer.getRunningTaskInfo(otherTaskId) != null
        if (!otherTaskRunning) {
            ProtoLog.d(
                WM_SHELL_SPLIT_SCREEN,
                "handleExitFullscreen but other task is not running, rejecting",
            )
            return ExitResult.Failed(RESULT_FAILED_NOT_SUPPORTED, this)
        }
        ProtoLog.d(
            WM_SHELL_SPLIT_SCREEN,
            "handleExitFullscreen: approving request for transition=%s, " +
                "taskId=%d, otherTaskId=%d",
            transition,
            task.taskId,
            otherTaskId,
        )

        val wct = WindowContainerTransaction()
        stageCoordinator.startTasksWithExistingTransition(
            transition,
            wct,
            task.taskId,
            null,
            otherTaskId,
            null,
            restorableState.originalSplitPosition,
            restorableState.originalSnapPosition,
        )
        return ExitResult.Approved(wct, this)
    }

    companion object {
        private const val TAG = "SplitScreenFullscreenRequestHandler"
    }
}
