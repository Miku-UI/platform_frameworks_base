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

import android.app.ActivityManager.AppTask.WINDOWING_LAYER_PINNED
import android.app.TaskInfo
import android.app.TaskWindowingLayerRequestHandler.REMOTE_CALLBACK_RESULT_KEY
import android.app.TaskWindowingLayerRequestHandler.RESULT_APPROVED
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.graphics.Rect
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.os.RemoteException
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_IS_WALLPAPER
import android.window.TransitionInfo.FLAG_SHOW_WALLPAPER
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController
import com.android.wm.shell.desktopmode.ShellDesktopState
import com.android.wm.shell.desktopmode.WindowDragTransitionHandler
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerLogs.logD
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerLogs.logV
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerLogs.logW
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerUtils.getLayerPinnedWct
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerUtils.getLayerUnpinnedWct
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerUtils.getRemovedFromLayerWct
import com.android.wm.shell.shared.TransactionPool
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.OnTaskRepositionAnimationListener

/**
 * A controller that is responsible for managing [WINDOWING_LAYER_PINNED] layer that has PiP
 * policies like single window and always-on-top.
 *
 * The controller is the main decider when the task is pinned and it's responsible for dispatching
 * callbacks.
 */
class PinnedLayerController(
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val desktopState: ShellDesktopState,
    private val taskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val presentationController: PinnedLayerPresentationController,
    private val windowDragTransitionHandler: WindowDragTransitionHandler,
    private val windowRepositionAnimationHandler: PinnedWindowRepositionAnimationHandler,
    private val windowRepositionAnimator: PinnedWindowRepositionAnimator,
    private val transactionPool: TransactionPool,
    private val multiDisplayDragMoveIndicatorController: MultiDisplayDragMoveIndicatorController,
) : Transitions.TransitionObserver {

    // Stores ids of pinned TaskInfo.
    private val pinnedTasks = mutableSetOf<Int>()
    private var currentPinnedTaskId: Int? = null

    private val pinnedTasksListeners = mutableSetOf<PinnedTasksListener>()

    // Stores pin layer transitions that are in progress.
    private val activeTransitions = mutableMapOf<IBinder, MutableSet<ActiveTransition>>()

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        transitions.registerObserver(this)
    }

    /**
     * Provides transitions that are currently being processed by this controller.
     *
     * @param transition an [IBinder] token for the given transitions.
     * @return a [Set] of [ActiveTransition], never returns `null`.
     */
    fun getActiveTransitions(transition: IBinder): Set<ActiveTransition> =
        activeTransitions.getOrDefault(transition, emptySet())

    /**
     * Provides currently pinned task.
     *
     * @return a pinned [TaskInfo].
     */
    fun getCurrentPinnedTask(): TaskInfo? {
        return currentPinnedTaskId?.let { shellTaskOrganizer.getRunningTaskInfo(it) }
    }

    /**
     * Checks whether a task with [taskId] is pinned.
     *
     * The task is considered to be pinned when the API call has triggered a transition and it
     * reported to be ready, marking that the Core has applied changes to the task.
     *
     * @param taskId an id of task to check for pinned state.
     * @return `true` when the task is pinned, `false` otherwise.
     * @see Transitions.TransitionObserver.onTransitionReady
     */
    fun isPinned(taskId: Int): Boolean = pinnedTasks.contains(taskId)

    /**
     * Pins a task and adds it to the active transitions for the given transition token.
     *
     * @param transition a transition token.
     * @param task a task to pin.
     * @param remoteCallback a callback to notify about the result of the operation.
     * @return a [WindowContainerTransaction] that contains pinned properties or `null` if the
     *   pinning is not supported for given task.
     */
    fun pinTask(
        transition: IBinder,
        task: TaskInfo,
        remoteCallback: IRemoteCallback?,
    ): WindowContainerTransaction? {
        if (!isPinningSupported(task)) {
            return null
        }
        logV(
            "pinTask: Added pending pin transition=%s taskId=%d, callback=%s",
            transition,
            task.taskId,
            remoteCallback,
        )
        return WindowContainerTransaction().apply {
            val transitions = activeTransitions.getOrPut(transition) { mutableSetOf() }
            transitions += ActiveTransition.Pin(task, remoteCallback)
            val bounds =
                if (!isPinned(task.taskId)) {
                    presentationController.getPinEntryDestinationBounds(task)
                } else {
                    presentationController.clampToDisplay(
                        task,
                        task.configuration.windowConfiguration.bounds,
                    )
                }
            merge(getLayerPinnedWct(task.token, bounds), /* transfer= */ true)

            val pinnedTask = getCurrentPinnedTask()
            if (pinnedTask != null && pinnedTask.token != task.token) {
                merge(unpinTask(transition, pinnedTask, UnpinStrategy.CLOSE), /* transfer= */ true)
            }
        }
    }

    /**
     * Unpins a specific task and adds it to the active transitions for the given transition token.
     *
     * @param transition a transition token.
     * @param task a task to unpin.
     * @param unpinStrategy a strategy for unpinning.
     * @return a [WindowContainerTransaction] that performs unpinning.
     */
    fun unpinTask(
        transition: IBinder,
        task: TaskInfo,
        unpinStrategy: UnpinStrategy,
    ): WindowContainerTransaction {
        logV(
            "unpinTask: Added pending unpin transition=%s taskId=%d, unpinStrategy=%s",
            transition,
            task.taskId,
            unpinStrategy,
        )
        val transitions = activeTransitions.getOrPut(transition) { mutableSetOf() }
        transitions += ActiveTransition.Unpin(task)

        return when (unpinStrategy) {
            UnpinStrategy.HIDE,
            UnpinStrategy.CLOSE ->
                getLayerUnpinnedWct(task.token, isMinimizing = unpinStrategy == UnpinStrategy.HIDE)
            UnpinStrategy.CLEAN -> getRemovedFromLayerWct(task.token)
        }
    }

    /**
     * Starts a transition to close a pinned task.
     *
     * @param task a [TaskInfo] of the task to close.
     * @return `true` if the task closing transition has been started, `false` otherwise.
     */
    fun closeTask(task: TaskInfo): Boolean {
        if (isNotPinned(task.taskId)) {
            logV("closeTask: the task=%s is not pinned. Skipping.", task)
            return false
        }

        logV("closeTask: starting unpin transition task=%s", task)
        val wct = WindowContainerTransaction()
        wct.removeTask(task.token)
        val transition = transitions.startTransition(TRANSIT_CLOSE, wct, /* handler= */ null)
        activeTransitions[transition] = mutableSetOf(ActiveTransition.Unpin(task))
        return true
    }

    /**
     * Starts a transition to move a task to a display with a given display id.
     *
     * Current method will set bounds considering constraints and valid drag area on display.
     *
     * @param task a [TaskInfo] that should be moved to another display.
     * @param displayId a new display id to move task to.
     * @param bounds a [Rect] representing new bounds the task wants to be placed at relative to the
     *   [displayId] coordinate system.
     * @return `true` when started a transition to move task to a new display, `false` otherwise.
     */
    fun moveToDisplay(
        task: TaskInfo,
        displayId: Int,
        bounds: Rect? = null,
        handler: Transitions.TransitionHandler? = null,
    ): Boolean {
        val wct = getMoveToDisplayChanges(task, displayId, bounds) ?: return false
        transitions.startTransition(TRANSIT_CHANGE, wct, handler)
        return true
    }

    /**
     * Provides [WindowContainerTransaction] changes to be added on display disconnection.
     *
     * If a destination display is not eligible to host pinned tasks the task will be closed by
     * default.
     *
     * @param transition a running display disconnect transition.
     * @param disconnectedDisplayId a display id that was disconnected.
     * @param destinationDisplayId a display id that should host a pinned task.
     * @return a [WindowContainerTransaction] that stores operations to move a task to a display or
     *   close it.
     */
    fun getDisplayDisconnectChanges(
        transition: IBinder,
        disconnectedDisplayId: Int,
        destinationDisplayId: Int,
    ): WindowContainerTransaction? {
        val task = getCurrentPinnedTask() ?: return null

        // This method can be called for any disconnected display and pinned task may not be on it,
        // so we filter such displays out.
        if (task.displayId != disconnectedDisplayId) {
            return null
        }

        logD(
            "onDisplayDisconnect: disconnectedDisplayId=%d, destinationDisplayId=%d, task=%d",
            disconnectedDisplayId,
            destinationDisplayId,
            task.taskId,
        )

        val finalBounds =
            presentationController.getPinEntryDestinationBounds(task, destinationDisplayId)
        val moveWct = getMoveToDisplayChanges(task, destinationDisplayId, finalBounds)
        if (moveWct != null && !moveWct.isEmpty) {
            return moveWct
        }

        return unpinTask(transition, task, UnpinStrategy.CLOSE)
    }

    private fun getMoveToDisplayChanges(
        task: TaskInfo,
        displayId: Int,
        bounds: Rect? = null,
    ): WindowContainerTransaction? {
        val displayAreaInfo = taskDisplayAreaOrganizer.getDisplayAreaInfo(displayId)
        val isPinned = isPinned(task.taskId)
        val isSameDisplay = task.displayId == displayId
        val isDisplayUnavailable = displayAreaInfo == null
        val isDisplayEligibleTarget = desktopState.isEligibleWindowDropTarget(displayId)
        logD(
            "moveToDisplay: task=%d, displayId=%d, isPinned=%b, isSameDisplayRequest=%b, " +
                "isDisplayUnavailable=%b, isDisplayEligibleDropTarget=%b",
            task.taskId,
            displayId,
            isPinned,
            isSameDisplay,
            isDisplayUnavailable,
            isDisplayEligibleTarget,
        )

        if (!isPinned || isSameDisplay || isDisplayUnavailable || !isDisplayEligibleTarget) {
            logV("moveToDisplay: skipping for the task=%s and display=%s.", task, displayAreaInfo)
            return null
        }

        val finalBounds =
            bounds?.let { newBounds ->
                presentationController.clampToDisplay(task, newBounds, displayId)
            }
        logV(
            "moveToDisplay: moving a task=%s to display=%s.\n Original bounds=%s, " +
                "clamped bounds=%s",
            task,
            displayAreaInfo,
            bounds,
            finalBounds,
        )
        val wct = WindowContainerTransaction()
        if (finalBounds != null) {
            wct.setBounds(task.token, finalBounds)
        }

        wct.reparent(task.token, displayAreaInfo.token, /* onTop= */ true)
        return wct
    }

    /**
     * Starts a focus change transitions.
     *
     * This function will also move the task to front, but since always-on-top task are effectively
     * in front, that should only cause a focus change.
     *
     * @param task a [TaskInfo] that should be focused.
     */
    fun requestFocus(task: TaskInfo) {
        if (isNotPinned(task.taskId)) {
            logV("requestFocus: the task=%s is not pinned. Skipping.", task)
            return
        }

        logV("requestFocus: starting focus transition task=%s", task)
        val wct = WindowContainerTransaction()
        wct.reorder(task.token, /* onTop= */ true, /* includingParents= */ true)
        transitions.startTransition(TRANSIT_CHANGE, wct, null)
    }

    /**
     * Handles drag end event for the given [TaskInfo].
     *
     * Visual indicators and mirroring surfaces sometimes should be disposed by the API consumer,
     * for example, when a [SurfaceControl] is snapped back to original position without any
     * following bounds changes, therefore, a caller should rely on the returned value that
     * indicates whether the controller committed state changes or not.
     *
     * @param leash a [SurfaceControl] of the given task.
     * @param taskInfo the task to update.
     * @param dragStartBounds the bounds of the task when the drag was started.
     * @param dragEndBounds the bounds of the task relative to the display on which drag has
     *   stopped.
     * @return `true` when the API caller should clear visual indicators itself, `false` otherwise.
     */
    fun onDragEnded(
        leash: SurfaceControl,
        taskInfo: TaskInfo,
        dragStartBounds: Rect,
        dragEndBounds: Rect,
        displayId: Int = taskInfo.displayId,
    ): Boolean {
        if (isNotPinned(taskInfo.taskId)) return false

        val isCrossDisplayDrag = taskInfo.displayId != displayId
        if (
            isCrossDisplayDrag &&
                moveToDisplay(taskInfo, displayId, dragEndBounds, windowDragTransitionHandler)
        ) {
            // Mirroring surfaces will be cleared by the window drag handler, until then we keep
            // them to prevent flickering.
            return false
        }

        // Post-process final drag bounds to keep them inside the valid drag area.
        val destinationBounds =
            presentationController.clampToDisplay(taskInfo, dragEndBounds) ?: dragStartBounds
        val isTargetDisplayAvailable =
            taskDisplayAreaOrganizer.getDisplayAreaInfo(displayId) != null
        if (!isTargetDisplayAvailable || destinationBounds == dragStartBounds) {
            // The task was dragged back to original position or there's no longer a valid display
            // to drag to, so calculating final bounds in case display is not available.
            if (destinationBounds != dragEndBounds) {
                // No transition needed because the task is moved back to the start position, but
                // the user moved the surface, and we should animate it.
                windowRepositionAnimator.start(leash, dragEndBounds, destinationBounds)
            } else {
                // Make the surface visible just in case it was moved off-screen.
                val t = transactionPool.acquire()
                t.setPosition(leash, dragStartBounds.left.toFloat(), dragStartBounds.top.toFloat())
                t.apply()
                transactionPool.release(t)
            }
            return true
        }

        if (destinationBounds != dragEndBounds) {
            // Drag bounds were snapped and we want to animate that.
            val onTransitionStateChange: (SurfaceControl.Transaction) -> Unit = { transition ->
                multiDisplayDragMoveIndicatorController.onDragEnd(taskInfo.taskId, transition)
            }
            windowRepositionAnimationHandler.startTransition(
                taskInfo,
                dragEndBounds,
                destinationBounds,
                onAnimationStart = onTransitionStateChange,
                onAnimationCanceled = onTransitionStateChange,
            )
            return false
        }

        // That's a simple user drag, just match task bounds to leash bounds.
        startBoundsChangeTransition(taskInfo, destinationBounds, windowDragTransitionHandler)
        return false
    }

    /** @see PinnedWindowRepositionAnimationHandler.setOnTaskRepositionAnimationListener */
    fun setOnTaskRepositionAnimationListener(listener: OnTaskRepositionAnimationListener?) {
        windowRepositionAnimationHandler.setOnTaskRepositionAnimationListener(listener)
    }

    private fun startBoundsChangeTransition(
        taskInfo: TaskInfo,
        bounds: Rect,
        handler: Transitions.TransitionHandler? = null,
    ) {
        val wct = WindowContainerTransaction()
        wct.setBounds(taskInfo.token, bounds)
        transitions.startTransition(TRANSIT_CHANGE, wct, handler)
    }

    fun requestTaskLocationChange(
        triggerTask: TaskInfo,
        requestedLocation: TransitionRequestInfo.RequestedLocation,
        outWct: WindowContainerTransaction,
    ) {
        val newBounds =
            presentationController.calculateNewTaskBounds(triggerTask, requestedLocation) ?: return
        outWct.setBounds(triggerTask.token, newBounds)
    }

    // TODO(b/449681882): Remove when Handler introduces its own state management for animations.
    // Use PinnedLayerUiState instead.
    fun cleanup(transition: IBinder) {
        activeTransitions.remove(transition)
    }

    fun addPinnedTasksListener(listener: PinnedTasksListener) {
        pinnedTasksListeners += listener
    }

    fun removePinnedTasksListener(listener: PinnedTasksListener) {
        pinnedTasksListeners -= listener
    }

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    ) {
        // TODO(b/449681882): Clean transitions here. Handler should track animation data
        // separately. Use PinnedLayerUiState instead.
        val transitions = activeTransitions.getOrDefault(transition, emptySet())
        transitions.forEach { transition ->
            logV("onTransitionReady: Pin layer transition ready: %s", transition)
            when (transition) {
                is ActiveTransition.Pin -> {
                    pin(transition.taskInfo)

                    // Only send result if the transition caused by an API request.
                    if (transition.resultCallback != null) {
                        sendWindowingLayerResult(RESULT_APPROVED, transition.resultCallback)
                    }
                }
                is ActiveTransition.Unpin -> {
                    val isMovingToBack =
                        info.changes.any {
                            it.taskInfo?.token == transition.taskInfo.token &&
                                it.mode == TRANSIT_TO_BACK
                        }
                    unpin(transition.taskInfo, rememberAsPinned = isMovingToBack)
                }
            }
        }
        checkFullscreenTaskOpenedAndClosePinnedIfNeeded(info)
    }

    /**
     * Pinned tasks does not work well over the fullscreen tasks yet: b/488007558 In order to not
     * break windowing state, pinned tasks are going to be closed if fullscreen task appears on the
     * same display.
     */
    private fun checkFullscreenTaskOpenedAndClosePinnedIfNeeded(info: TransitionInfo) {
        val pinnedTask = getCurrentPinnedTask() ?: return

        val changeWithFullscreenTask =
            info.changes.firstOrNull { change ->
                val taskInfo = change.taskInfo
                taskInfo != null &&
                    !isPinned(taskInfo.taskId) &&
                    taskInfo.displayId == pinnedTask.displayId &&
                    taskInfo.windowingMode == WINDOWING_MODE_FULLSCREEN &&
                    !(change.hasFlags(FLAG_IS_WALLPAPER or FLAG_SHOW_WALLPAPER)) &&
                    (change.mode == TRANSIT_OPEN ||
                        change.mode == TRANSIT_TO_FRONT ||
                        change.mode == TRANSIT_CHANGE)
            }

        if (changeWithFullscreenTask != null) {
            logD(
                "Closing pinned task as fullscreen task appeared on same display. " +
                    "Pinned task id=%d, caused by change=%s",
                pinnedTask.taskId,
                changeWithFullscreenTask,
            )
            closeTask(pinnedTask)
        }
    }

    private fun pin(taskInfo: TaskInfo) {
        pinnedTasks += taskInfo.taskId
        currentPinnedTaskId = taskInfo.taskId
        pinnedTasksListeners.forEach { it.onPinnedTasksAdded(taskInfo) }
    }

    private fun unpin(taskInfo: TaskInfo, rememberAsPinned: Boolean = false) {
        if (!rememberAsPinned) {
            pinnedTasks -= taskInfo.taskId
            pinnedTasksListeners.forEach { it.onPinnedTasksRemoved(taskInfo) }
        }

        if (currentPinnedTaskId == taskInfo.taskId) {
            currentPinnedTaskId = null
        }
    }

    private fun isPinningSupported(task: TaskInfo): Boolean =
        // check support only for tasks that are not pinned yet.
        isPinned(task.taskId) || presentationController.isTaskSupportedForPinning(task)

    fun sendWindowingLayerResult(result: Int, callback: IRemoteCallback) {
        val bundle = Bundle()
        bundle.putInt(REMOTE_CALLBACK_RESULT_KEY, result)
        try {
            callback.sendResult(bundle)
        } catch (e: RemoteException) {
            logW("Failed to invoke callback, error=%s", e)
        }
    }

    /** A set of strategies that changes how the task is unpinned. */
    enum class UnpinStrategy {
        /**
         * The task should be unpinned visually and hidden from the display. The controller keeps
         * the task as pinned.
         */
        HIDE,

        /** The task should be unpinned and closed. The task is not kept as pinned. */
        CLOSE,

        /**
         * The task should be unpinned and kept visible on the display. This is useful when another
         * handler wants to decide where to put the task.
         */
        CLEAN,
    }

    interface PinnedTasksListener {
        fun onPinnedTasksAdded(taskInfo: TaskInfo)

        fun onPinnedTasksRemoved(taskInfo: TaskInfo)
    }
}
