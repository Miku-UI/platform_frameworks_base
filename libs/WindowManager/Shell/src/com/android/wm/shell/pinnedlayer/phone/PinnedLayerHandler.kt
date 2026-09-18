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

package com.android.wm.shell.pinnedlayer.phone

import android.app.ActivityManager.AppTask.WINDOWING_LAYER_NORMAL_APP
import android.app.ActivityManager.AppTask.WINDOWING_LAYER_PINNED
import android.app.ActivityManager.AppTask.WINDOWING_LAYER_UNDEFINED
import android.app.ActivityManager.RunningTaskInfo
import android.app.TaskInfo
import android.app.TaskWindowingLayerRequestHandler.RESULT_FAILED_BAD_STATE
import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.TransitionRequestInfo.WindowingLayerChange
import android.window.WindowContainerTransaction
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.NormalAppLayerController
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController.UnpinStrategy
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerLogs.logV
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerLogs.logW
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/**
 * A [WINDOWING_LAYER_PINNED] [Transitions.TransitionHandler] that handles incoming transition
 * requests.
 */
class PinnedLayerHandler(
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val pinnedLayerController: PinnedLayerController,
    private val pinnedLayerUiState: PinnedLayerUiState,
    private val normalLayerController: NormalAppLayerController,
    private val desktopUserRepositories: DesktopUserRepositories?,
    private val desktopTasksController: DesktopTasksController?,
) : Transitions.TransitionHandler {

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        transitions.addHandler(this)
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        val triggerTask = request.triggerTask ?: return null
        val windowingLayerChange = request.windowingLayerChange

        if (
            pinnedLayerController.isNotPinned(triggerTask.taskId) &&
                !windowingLayerChange.isLayerPinningRequest()
        ) {
            return null
        }

        val wct = WindowContainerTransaction()
        val isLayerPinningRequest = windowingLayerChange.isLayerPinningRequest()
        val isLayerOpeningPinnedRequest = request.isOpeningPinnedRequest()
        val isRequestingLocation = request.isRequestLocationOfPinnedTask()
        logV(
            "Creating a transition: isLayerPinningRequest=%b, isOpeningPinnedRequest=%b, " +
                "isRequestingLocation=%b",
            isLayerPinningRequest,
            isLayerOpeningPinnedRequest,
            isRequestingLocation,
        )
        // There's either a pin request or an already pinned task is brought to foreground.
        if (isLayerPinningRequest || isLayerOpeningPinnedRequest) {
            val pinWct =
                pinnedLayerController.pinTask(
                    transition,
                    triggerTask,
                    windowingLayerChange?.remoteCallback,
                )
            if (pinWct == null) {
                // Pin is not supported for the task, handler should reject the callback (if
                // provided) and do not request any changes
                logV("Pinning is not supported for task=%s", triggerTask)
                windowingLayerChange?.remoteCallback?.let {
                    pinnedLayerController.sendWindowingLayerResult(RESULT_FAILED_BAD_STATE, it)
                }
                return null
            }

            cleanUpDesktopIfNeeded(transition, triggerTask, pinWct)
            wct.merge(pinWct, /* transfer= */ true)
        }

        if (isRequestingLocation) {
            // It's a request to move or resize a pinned task. Due to security reasons, pinned tasks
            // have limited ability to change their location so we delegate the request to the
            // PinnedLayerController.
            pinnedLayerController.requestTaskLocationChange(
                triggerTask,
                request.requestedLocation!!,
                wct,
            )
        }

        val isClosePinnedRequest = request.isClosingPinnedRequest()
        val isSwitchingToAnotherLayer =
            request.windowingLayerChange.isSwitchingToAnotherLayerRequest()
        logV(
            "Creating an unpinning transition: isClosePinnedRequest=%b, " +
                "isSwitchingToAnotherLayer=%b",
            isClosePinnedRequest,
            isSwitchingToAnotherLayer,
        )
        if (isClosePinnedRequest || isSwitchingToAnotherLayer) {
            // Reparenting must happen first because WCT operations order matters, otherwise AoT
            // is not cleared.
            moveToAnotherLayerIfNeeded(transition, triggerTask, windowingLayerChange, wct)

            val targetUnpinType = resolveTargetUnpinType(request)
            wct.merge(
                pinnedLayerController.unpinTask(transition, triggerTask, targetUnpinType),
                true,
            )
        }

        return wct
    }

    private fun cleanUpDesktopIfNeeded(
        transition: IBinder,
        taskInfo: TaskInfo,
        wct: WindowContainerTransaction,
    ) {
        if (desktopUserRepositories == null || desktopTasksController == null) {
            logV("cleanUpDesktopIfNeeded: desktop mode is not supported, skipping.")
            return
        }

        val repository = desktopUserRepositories.getProfile(taskInfo.userId)
        val deskId = repository.getDeskIdForTask(taskInfo.taskId)
        val activeDesk = repository.getActiveDeskId(taskInfo.displayId)
        if (deskId == null || deskId != activeDesk) {
            logV(
                "cleanUpDesktopIfNeeded: task=%d is not in the active desk=%d, " +
                    "but in the desk=%d, skipping.",
                taskInfo.taskId,
                activeDesk,
                deskId,
            )
            return
        }

        val isLastTask =
            repository.isOnlyVisibleNonClosingTaskInDesk(
                taskInfo.taskId,
                deskId,
                taskInfo.displayId,
            )
        if (!isLastTask) {
            logV(
                "cleanUpDesktopIfNeeded: task with id=%d is not the last one, skipping.",
                taskInfo.taskId,
            )
            return
        }

        val runOnTransitionStart =
            desktopTasksController.performDesktopExitCleanUp(
                wct = wct,
                deskId = deskId,
                displayId = taskInfo.displayId,
                userId = taskInfo.userId,
                willExitDesktop = true,
                removingLastTaskId = taskInfo.taskId,
                exitReason = ExitReason.TASK_MOVED_FROM_DESK,
            )
        runOnTransitionStart?.invoke(transition)
    }

    private fun moveToAnotherLayerIfNeeded(
        transition: IBinder,
        targetTask: RunningTaskInfo,
        windowingLayerChange: WindowingLayerChange?,
        wct: WindowContainerTransaction,
    ) {
        when (windowingLayerChange?.windowingLayer) {
            WINDOWING_LAYER_NORMAL_APP -> {
                val normalLayerWct =
                    normalLayerController.moveTaskToNormalLayer(
                        transition,
                        targetTask,
                        windowingLayerChange.remoteCallback,
                    )
                wct.merge(normalLayerWct, true)
            }
            WINDOWING_LAYER_PINNED,
            WINDOWING_LAYER_UNDEFINED,
            null -> {}
            else -> {
                logW("PinnedLayerHandler tried to move a task, but the layer is skipped.")
            }
        }
    }

    private fun resolveTargetUnpinType(request: TransitionRequestInfo): UnpinStrategy {
        val isClosePinnedRequest = request.isClosingPinnedRequest()
        val isSwitchingToAnotherLayer =
            request.windowingLayerChange.isSwitchingToAnotherLayerRequest()
        return when {
            isClosePinnedRequest -> UnpinStrategy.CLOSE
            isSwitchingToAnotherLayer -> UnpinStrategy.CLEAN
            else ->
                UnpinStrategy.CLOSE.also {
                    logW(
                        "Defaulting to the unpin type=%s. Check that your unpin " +
                            "condition is added to the heuristics.",
                        it,
                    )
                }
        }
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        return animate(transition, info, startTransaction, finishTransaction, finishCallback).also {
            pinnedLayerController.cleanup(transition)
        }
    }

    private fun animate(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        // TODO(b/449681882): Do not rely on transitions, introduce separate animations state.
        val transitions = pinnedLayerController.getActiveTransitions(transition)

        val pinChange =
            transitions
                .asSequence()
                .filterIsInstance<ActiveTransition.Pin>()
                .mapNotNull { activePin ->
                    info.changes.find { it.taskInfo?.token == activePin.taskInfo.token }
                }
                .firstOrNull()

        pinChange?.let {
            val animator =
                PinnedLayerAnimator.createPinAnimator(
                    it,
                    startTransaction,
                    finishTransaction,
                    finishCallback,
                    onAnimationStart = pinnedLayerUiState::onResizeMoveStarted,
                    onAnimationUpdate = pinnedLayerUiState::onResizeMoveUpdated,
                    onAnimationEnd = pinnedLayerUiState::onResizeMoveEnded,
                )
            animator.start()
            return true
        }

        // Should accept all the TransitionInfo.Change if there's an unpin transition.
        // Required to properly handle animation for mixed transitions with pip.
        if (transitions.any { it is ActiveTransition.Unpin }) {
            startTransaction.apply()
            finishCallback.onTransitionFinished(null)
            return true
        }
        return false
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: SurfaceControl.Transaction?,
    ) {
        pinnedLayerController.cleanup(transition)
    }

    /**
     * Augments a [WindowContainerTransaction] to dismiss a task if it is pinned.
     *
     * @param transition the transition that may dismiss a pinned task.
     * @param request the transition request.
     * @param outWct the [WindowContainerTransaction] to augment.
     */
    fun augmentRequestDismissPinnedTask(
        transition: IBinder,
        request: TransitionRequestInfo,
        outWct: WindowContainerTransaction,
    ) {
        pinnedLayerController.getCurrentPinnedTask()?.let {
            outWct.merge(
                pinnedLayerController.unpinTask(transition, it, UnpinStrategy.CLOSE),
                /* transfer= */ true,
            )
        }
    }

    /** @return `true` if there is a task that is currently pinned and visible. */
    fun hasActivePinnedTask(): Boolean {
        return pinnedLayerController.getCurrentPinnedTask() != null
    }

    /**
     * Checks whether controller is observing a transition. This is used to determine whether
     * controller is expecting to receive a [startAnimation] call for a given transition.
     *
     * @return `true` if the transition is active within the controller, `false` otherwise.
     */
    fun observes(transition: IBinder): Boolean {
        return pinnedLayerController.getActiveTransitions(transition).isNotEmpty()
    }

    /**
     * Checks whether controller is observing a transition and awaits changes for a particular task
     * in this transition.
     *
     * Used to determine what changes should be propagated to this handler.
     *
     * @return `true` if the transition is active within the controller and contains a task, `false`
     *   otherwise.
     */
    fun awaitsChangesFor(taskInfo: TaskInfo?, transition: IBinder): Boolean {
        val taskId = taskInfo?.taskId ?: return false
        val activeTransitions = pinnedLayerController.getActiveTransitions(transition)
        return activeTransitions.any { it.taskInfo.taskId == taskId }
    }

    fun isPinningRequest(request: TransitionRequestInfo): Boolean =
        request.windowingLayerChange?.isLayerPinningRequest() ?: false ||
            request.isOpeningPinnedRequest()

    private fun WindowingLayerChange?.isLayerPinningRequest(): Boolean {
        return this != null && windowingLayer == WINDOWING_LAYER_PINNED
    }

    private fun WindowingLayerChange?.isSwitchingToAnotherLayerRequest(): Boolean {
        return this != null &&
            !isLayerPinningRequest() &&
            windowingLayer > WINDOWING_LAYER_UNDEFINED
    }

    private fun TransitionRequestInfo.isOpeningPinnedRequest(): Boolean {
        val task = triggerTask
        return task != null &&
            pinnedLayerController.isPinned(task.taskId) &&
            TransitionUtil.isOpeningType(type)
    }

    private fun TransitionRequestInfo.isClosingPinnedRequest(): Boolean {
        val task = triggerTask
        return task != null &&
            pinnedLayerController.isPinned(task.taskId) &&
            TransitionUtil.isClosingType(type)
    }

    private fun TransitionRequestInfo.isRequestLocationOfPinnedTask(): Boolean {
        val task = triggerTask
        return task != null &&
            pinnedLayerController.isPinned(task.taskId) &&
            requestedLocation != null
    }
}
