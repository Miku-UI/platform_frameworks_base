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
import android.app.TaskInfo
import android.app.TaskWindowingLayerRequestHandler.REMOTE_CALLBACK_RESULT_KEY
import android.app.TaskWindowingLayerRequestHandler.RESULT_APPROVED
import android.app.TaskWindowingLayerRequestHandler.RESULT_FAILED_BAD_STATE
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.os.RemoteException
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.desktopmode.NormalAppLayerHandler.Companion.logE
import com.android.wm.shell.desktopmode.NormalAppLayerHandler.Companion.logV
import com.android.wm.shell.desktopmode.NormalAppLayerHandler.Companion.logW
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController
import com.android.wm.shell.pinnedlayer.phone.isNotPinned
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource.LAYER_SWITCH
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionObserver

/**
 * A class responsible for managing
 * [android.app.ActivityManager.AppTask.WINDOWING_LAYER_NORMAL_APP].
 *
 * Normal layer on the Shell side is pretty loose - everything that is not on the pinned layer is on
 * the normal layer. That allows to keep API consistent, because we don't need to send layer updates
 * since the only source to change the layer - pinning or unpinning the window.
 */
class NormalAppLayerController(
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val userRepositories: DesktopUserRepositories?,
    private val desktopTasksController: DesktopTasksController?,
    private val pinnedLayerController: PinnedLayerController,
    private val desktopState: DesktopState,
) : TransitionObserver {

    private val activeTransitions = mutableMapOf<IBinder, MutableSet<ActiveTransition>>()

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
        val activeTransitions = activeTransitions.remove(transition).orEmpty()
        activeTransitions.forEach { (task, callback) ->
            val isOnNormalLayer = isOnNormalLayer(task)

            logV(
                "onTransitionReady: Normal layer request for task=%s in transition=%s: " +
                    "isOnNormalLayer=%b",
                task,
                transition,
                isOnNormalLayer,
            )
            if (callback != null) {
                val result = if (isOnNormalLayer) RESULT_APPROVED else RESULT_FAILED_BAD_STATE
                sendWindowingLayerResult(result, callback)
            }
        }
    }

    /**
     * Schedules a given task to be moved to a normal app layer.
     *
     * Moving to normal layer will either do nothing when the window is already on the normal layer
     * or move the window to the desk of current task's display.
     *
     * @param transition a transition token in which scope moving should be done.
     * @param taskInfo a target task to be moved to the normal layer.
     * @param callback a callback to notify when the task has been moved to normal layer.
     * @return [WindowContainerTransaction] that contains hierarchy operations to set normal layer
     *   properties on the given task. Non-null, but can be empty in case the operation is not
     *   supported.
     * @see DesktopTasksController
     */
    fun moveTaskToNormalLayer(
        transition: IBinder,
        taskInfo: RunningTaskInfo,
        callback: IRemoteCallback? = null,
    ): WindowContainerTransaction {
        val wct = WindowContainerTransaction()
        val isTaskPinned = pinnedLayerController.isPinned(taskInfo.taskId)
        if (isTaskPinned) {
            val isDesktopModeSupportedOnDevice =
                desktopTasksController != null && userRepositories != null
            if (isDesktopModeSupportedOnDevice) {
                moveToDeskIfRequired(transition, taskInfo, wct)
            } else {
                logE(
                    "Pinned task=%s only supported on Desktop. " +
                        "Skipping moving to the normal layer.",
                    taskInfo,
                )
            }
        } else {
            logV("Task is not pinned. Skipping moving to the normal layer.")
        }

        // Always send a transition, even if we do not move pinned window to the normal layer due
        // to not supported DW or invalid state, the callback must be dispatched.
        createMoveToNormalTransition(transition, taskInfo, callback)
        return wct
    }

    /**
     * Decorates a [wct] to move a given task to a desk if required.
     *
     * Expects the device to support desktop mode, where [desktopTasksController] and
     * [userRepositories] are not null.
     */
    private fun moveToDeskIfRequired(
        transition: IBinder,
        taskInfo: RunningTaskInfo,
        wct: WindowContainerTransaction,
    ) {
        val desktopRepository = userRepositories!!.getProfile(taskInfo.userId)
        val isDesktopModeSupportedOnDisplay =
            desktopState.isDesktopModeSupportedOnDisplay(taskInfo.displayId)
        val isDesktopWindow = desktopRepository.isActiveTask(taskInfo.taskId)
        logV(
            "Checking moving to the desk: taskId=%d, displayId=%d " +
                "isDesktopModeSupportedOnDisplay=%b, isDesktopWindow=%b",
            taskInfo.taskId,
            taskInfo.displayId,
            isDesktopModeSupportedOnDisplay,
            isDesktopWindow,
        )

        val isMovingToDesktop = isDesktopModeSupportedOnDisplay && !isDesktopWindow
        if (isMovingToDesktop) {
            val displayId = taskInfo.displayId
            val deskId = desktopRepository.getActiveDeskId(displayId)
            if (deskId != null) {
                logV("Normal layer is an active desk with id=%d", deskId)
                desktopTasksController!!.moveTaskToDesk(
                    taskId = taskInfo.taskId,
                    deskId = deskId,
                    userId = taskInfo.userId,
                    wct = wct,
                    transitionSource = LAYER_SWITCH,
                    targetTransition = transition,
                )
                if (taskInfo.isFocused) {
                    // If task is focused, we need to propagate the reorder to parents to ensure
                    // the focus is kept.
                    wct.reorder(taskInfo.token, /* onTop= */ true, /* includingParents= */ true)
                }
            } else {
                logV(
                    "Couldn't find an active desk on displayId=%d for the normal layer " +
                        "request. Trying to move to the default desk as a fallback.",
                    displayId,
                )
                desktopTasksController!!.moveTaskToDefaultDeskAndActivate(
                    taskId = taskInfo.taskId,
                    wct = wct,
                    transitionSource = LAYER_SWITCH,
                    targetTransition = transition,
                )
            }
        }

        if (isDesktopWindow) {
            logE(
                "Pinned task=%s can't be a desktop window in a Desk. " +
                    "Skipping moving to the normal layer.",
                taskInfo,
            )
        }
    }

    private fun createMoveToNormalTransition(
        transition: IBinder,
        taskInfo: TaskInfo,
        callback: IRemoteCallback?,
    ) {
        val transitions = activeTransitions.getOrPut(transition) { mutableSetOf() }
        transitions += ActiveTransition(taskInfo, callback)
    }

    /**
     * Checks whether current [android.app.TaskInfo] is on normal app layer.
     *
     * A task is positioned on a normal layer when it's not positioned on other layers, e.g. it is
     * not pinned. This provides a guarantee that the layer can only be changed only via the API
     * call.
     *
     * @param taskInfo a [android.app.TaskInfo].
     * @return `true` when task is on the normal layer, `false` otherwise.
     * @see android.app.ActivityManager.AppTask.requestWindowingLayer
     */
    fun isOnNormalLayer(taskInfo: TaskInfo): Boolean {
        return pinnedLayerController.isNotPinned(taskInfo.taskId)
    }

    private fun sendWindowingLayerResult(result: Int, callback: IRemoteCallback) {
        val bundle = Bundle()
        bundle.putInt(REMOTE_CALLBACK_RESULT_KEY, result)
        try {
            callback.sendResult(bundle)
        } catch (e: RemoteException) {
            logW("Failed to invoke callback: %s", e.toString())
        }
    }

    private data class ActiveTransition(val task: TaskInfo, val callback: IRemoteCallback? = null)
}
