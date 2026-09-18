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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityTaskManager
import android.app.TaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.windowingModeToString
import android.view.Display
import android.window.DesktopExperienceFlags
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.annotation.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.pip.PipDesktopState
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE

/** Controller to perform extra handling to PiP transitions while in Desktop mode. */
class DesktopPipTransitionController(
    private val shellTaskOrganizer: ShellTaskOrganizer,
    val desktopTasksController: DesktopTasksController,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val pipDesktopState: PipDesktopState,
    private val displayController: DisplayController,
) {
    /**
     * This is called by [PipScheduler#scheduleExitPipViaExpand] before starting an expand PiP
     * transition, and by [PipExpandHandler#handleRequest] when receiving an expand PiP request. In
     * both cases, we want to update the wct to include necessary changes based on the current
     * Desktop state.
     *
     * @param wct WindowContainerTransaction that will apply these changes
     * @param pipTask PiP task info
     * @param displayId The display id where we want to expand PiP to
     * @return RunOnTransitStart callbacks to update Desk states after the transition starts
     */
    fun updateExpandWctForDesktop(
        wct: WindowContainerTransaction?,
        pipTask: TaskInfo?,
        displayId: Int,
    ): RunOnTransitStart? {
        if (wct == null || pipTask == null) return null

        val parentTaskId = pipTask.lastParentTaskIdBeforePip
        val isMultiActivityPip = parentTaskId != ActivityTaskManager.INVALID_TASK_ID
        val taskInfo =
            shellTaskOrganizer.getRunningTaskInfo(
                if (isMultiActivityPip) parentTaskId else pipTask.taskId
            )
        if (taskInfo == null) {
            logW(
                "updateExpandWctForDesktop: Failed to find RunningTaskInfo for taskId=%d",
                if (isMultiActivityPip) parentTaskId else pipTask.taskId,
            )
            return null
        }

        // In multi-activity case, windowing mode change will reparent to original host task, so
        // we have to update the parent windowing mode to what is expected.
        val updateParentRunOnTransit =
            if (isMultiActivityPip) maybeUpdateParentInWct(wct, taskInfo) else null
        // With multiple-desks, we have to reparent the task to the root desk.
        val reparentTaskRunOnTransit =
            maybeReparentTaskToDesk(wct, taskInfo, isMultiActivityPip = isMultiActivityPip)
        var moveToDisplayRunOnTransit: RunOnTransitStart? = null
        // If we are expanding PiP in a different display than it is currently in, move the PiP
        // task to the new display (this should only be the case when PiP is in display A and we
        // are launching the task PiP was triggered from in display B).
        if (
            displayId != pipDesktopState.getCurrentDisplayId() &&
                !pipDesktopState.isDisplayDesktopFirst(Display.DEFAULT_DISPLAY)
        ) {
            logD(
                "updateExpandWctForDesktop: expanding PiP which was in displayId=%d to a " +
                    "different display (id=%d), call moveToDisplay ",
                pipDesktopState.getCurrentDisplayId(),
                displayId,
            )
            moveToDisplayRunOnTransit =
                desktopTasksController.addMoveToDisplayChanges(
                    wct = wct,
                    task = taskInfo,
                    displayId = displayId,
                    enterReason = EnterReason.EXIT_PIP,
                )
        }
        return RunOnTransitStart { transition ->
            updateParentRunOnTransit?.invoke(transition)
            reparentTaskRunOnTransit?.invoke(transition)
            moveToDisplayRunOnTransit?.invoke(transition)
        }
    }

    /**
     * In the case of multi-activity PiP, we might need to update the parent task's windowing mode
     * and bounds based on whether we are currently in Desktop Windowing.
     *
     * @param wct WindowContainerTransaction that will apply these changes
     * @param parentTask RunningTaskInfo associated with id from TaskInfo#lastParentTaskIdBeforePip
     * @return RunOnTransitStart if addMoveToFullscreenChanges is needed
     */
    @VisibleForTesting
    fun maybeUpdateParentInWct(
        wct: WindowContainerTransaction,
        parentTask: RunningTaskInfo,
    ): RunOnTransitStart? {
        if (!pipDesktopState.isDesktopWindowingPipEnabled()) {
            return null
        }

        val targetWinMode = pipDesktopState.getOutPipWindowingMode(isMultiActivityChild = true)
        logD(
            "maybeUpdateParentInWct: parentTaskId=%d parentWinMode=%s targetWinMode=%s",
            parentTask.taskId,
            windowingModeToString(parentTask.windowingMode),
            windowingModeToString(targetWinMode),
        )
        if (targetWinMode != parentTask.windowingMode) {
            wct.setWindowingMode(parentTask.token, targetWinMode)
        }
        if (targetWinMode == WINDOWING_MODE_FULLSCREEN) {
            return maybeAddMoveToFullscreenChanges(wct, parentTask)
        }
        return null
    }

    /**
     * In multi-activity PiP case, if the task entering PiP was previously active in a Desk and is
     * now expanding to fullscreen, call [DesktopTasksController#addMoveToFullscreenChanges] for the
     * parent task to properly move the task to fullscreen.
     *
     * @param wct WindowContainerTransaction that will apply these changes
     * @param parentTask the multi-activity PiP parent
     * @return RunOnTransitStart if addMoveToFullscreenChanges is needed
     */
    private fun maybeAddMoveToFullscreenChanges(
        wct: WindowContainerTransaction,
        parentTask: RunningTaskInfo,
    ): RunOnTransitStart? {
        val desktopRepository = desktopUserRepositories.getProfile(parentTask.userId)
        if (!desktopRepository.isActiveTask(parentTask.taskId)) {
            logW(
                "maybeAddMoveToFullscreenChanges: parentTask with id=%d is not active in any desk",
                parentTask.taskId,
            )
            return null
        }

        logD(
            "maybeAddMoveToFullscreenChanges: addMoveToFullscreenChanges, taskId=%d displayId=%d",
            parentTask.taskId,
            parentTask.displayId,
        )
        return desktopTasksController.addMoveToFullscreenChanges(
            wct = wct,
            taskInfo = parentTask,
            willExitDesktop = true,
        )
    }

    /**
     * If the PiP task is going to desktop mode, we need to reparent the task to the root
     * desk. In addition, if we are expanding PiP at Home (as in with a Desktop-first display), we
     * also need to activate the default desk.
     *
     * @param wct WindowContainerTransaction that will apply these changes
     * @param taskInfo of the task that is exiting PiP (this is the parent task if it is a
     *   multi-activity PiP)
     * @param isMultiActivityPip whether the PiP task is multi-activity
     * @return RunOnTransitStart if addDeskActivationChanges is needed
     */
    @VisibleForTesting
    fun maybeReparentTaskToDesk(
        wct: WindowContainerTransaction,
        taskInfo: RunningTaskInfo,
        isMultiActivityPip: Boolean,
    ): RunOnTransitStart? {
        // Temporary workaround for b/409201669: We always expand to fullscreen if we're exiting PiP
        // in the middle of Recents animation from Desktop session, so don't reparent to the Desk.
        if (
            !pipDesktopState.isDesktopWindowingPipEnabled() ||
                pipDesktopState.isRecentsAnimating()
        ) {
            return null
        }

        if (!pipDesktopState.isPipInDesktopMode()) {
            logD("maybeReparentTaskToDesk: PiP transition is not in Desktop session")
            return null
        }

        val desktopRepository = desktopUserRepositories.getProfile(taskInfo.userId)
        val taskId = taskInfo.taskId
        val displayId = pipDesktopState.getCurrentDisplayId()
        val deskId = getDeskId(desktopRepository, displayId)
        if (deskId == INVALID_DESK_ID) return null

        if (isMultiActivityPip && desktopRepository.isActiveTaskInDesk(taskId, deskId)) {
            logD(
                "maybeReparentTaskToDesk: Multi-activity PiP with parent taskId=%d already " +
                    "in the Desk with id=%d, move parent task to front",
                taskId,
                deskId,
            )
            moveParentTaskToFront(wct, taskInfo, deskId)
            return null
        }

        val runOnTransitStart =
            if (!desktopRepository.isDeskActive(deskId)) {
                logD(
                    "maybeReparentTaskToDesk: addDeskActivationChanges, taskId=%d deskId=%d, " +
                        "displayId=%d",
                    taskId,
                    deskId,
                    displayId,
                )
                desktopTasksController.addDeskActivationChanges(
                    deskId = deskId,
                    wct = wct,
                    newTask = taskInfo,
                    displayId = displayId,
                    userId = desktopRepository.userId,
                    enterReason = EnterReason.EXIT_PIP,
                )
            } else null

        logD(
            "maybeReparentTaskToDesk: addMoveToDeskTaskChanges, taskId=%d deskId=%d",
            taskId,
            deskId,
        )
        desktopTasksController.addMoveToDeskTaskChanges(wct = wct, task = taskInfo, deskId = deskId)
        // A Desk could be active but still behind the Wallpaper (and is therefore not visible).
        // When we expand PiP, we always want the Desk to be brought to front along with the task.
        wct.reorder(taskInfo.token, /* onTop= */ true, /* includingParents= */ true)

        return runOnTransitStart
    }

    /**
     * In multi-activity PiP case, call [DesktopTasksController#addMoveTaskToFrontChanges] to move
     * the parent task to front within the desk.
     *
     * @param wct WindowContainerTransaction that will apply these changes
     * @param parentTask the parent task
     * @param deskId desk id that the multi-activity PiP parent is in
     */
    private fun moveParentTaskToFront(
        wct: WindowContainerTransaction,
        parentTask: RunningTaskInfo,
        deskId: Int,
    ) {
        logD("moveParentTaskToFront: parentTaskId=%d deskId=%d", parentTask.taskId, deskId)
        desktopTasksController.addMoveTaskToFrontChanges(
            wct = wct,
            deskId = deskId,
            taskInfo = parentTask,
        )
    }

    /**
     * This is called by [PipScheduler#getRemovePipTransaction] when PiP is removed. If PiP is in
     * Desktop session, we remove the entire task directly instead of reordering the task to the
     * back.
     *
     * @param wct WindowContainerTransaction that will apply the changes
     * @param token WindowContainerToken of the PiP task
     */
    fun handleRemovePipTransition(wct: WindowContainerTransaction, token: WindowContainerToken) {
        if (!pipDesktopState.isPipInDesktopMode()) {
            logD("handleRemovePipTransition: PiP transition is not in Desktop session")
            return
        }
        if (displayController.getDisplay(pipDesktopState.getCurrentDisplayId()) == null) {
            // Rather than remove the task, PipDisplayDisconnectHandler will reparent the task
            // so it is available on the internal display if the display was disconnected.
            logD(
                "handleRemovePipTransition: In Desktop session on recently disconnected" +
                    " display, will not remove PiP task entirely"
            )
            return
        }
        logD("handleRemovePipTransition: In Desktop session, removing PiP task entirely")
        wct.removeTask(token)
    }

    private fun getDeskId(repository: DesktopRepository, displayId: Int): Int =
        repository.getActiveDeskId(displayId)
            ?: if (!pipDesktopState.isDisplayDesktopFirst(displayId)) {
                logW("getDeskId: Active desk not found for display id %d", displayId)
                INVALID_DESK_ID
            } else {
                checkNotNull(repository.getDefaultDeskId(displayId)) {
                    "$TAG: getDeskId: " +
                        "Expected a default desk to exist in display with id $displayId"
                }
            }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private companion object {
        private const val TAG = "DesktopPipTransitionController"
        private const val INVALID_DESK_ID = -1
    }
}
