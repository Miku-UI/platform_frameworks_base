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
package com.android.wm.shell.common

import android.app.Activity
import android.app.Activity.FULLSCREEN_MODE_REQUEST_ENTER
import android.app.Activity.FULLSCREEN_MODE_REQUEST_EXIT
import android.app.ActivityManager.RunningTaskInfo
import android.app.FullscreenRequestHandler.REMOTE_CALLBACK_RESULT_KEY
import android.app.FullscreenRequestHandler.REQUEST_ALLOW_MODE_EXIT
import android.app.FullscreenRequestHandler.REQUEST_ALLOW_MODE_INHERIT
import android.app.FullscreenRequestHandler.RESULT_APPROVED
import android.app.FullscreenRequestHandler.RESULT_FAILED_ALREADY_FULLY_EXPANDED
import android.app.FullscreenRequestHandler.RESULT_FAILED_NOT_IN_FULLSCREEN_WITH_HISTORY
import android.app.FullscreenRequestHandler.RESULT_FAILED_NOT_SUPPORTED
import android.app.FullscreenRequestHandler.RequestResult
import android.app.FullscreenRequestHandler.requestResultToString
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.graphics.Rect
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.os.RemoteException
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CLOSE
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.annotation.OpenForTesting
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.EnterResult
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.EnterResult.Approved.RestorableState
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.ExitResult
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.shared.split.SplitScreenConstants.PersistentSnapPosition
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitPosition
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/**
 * Handles client-initiated fullscreen requests, delegating responsibility to feature-specific
 * handlers that may be capable of handling the request.
 *
 * Also provides a fallback handler that runs last and always handles the requests as failed.
 */
class ClientFullscreenRequestController(
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val invalidationMonitorSupplier: (Int) -> ExitFullscreenInvalidationMonitor,
) : Transitions.TransitionHandler {

    private val handlers =
        mutableListOf<FullscreenRequestHandler>(FallbackFullscreenRequestHandler())
    // TODO: b/296268915 - find a way to clean this up if the task is removed and will never get an
    //  exit request.
    private val taskToRestorableState = mutableMapOf<Int, RestorableState>()
    private val taskToInvalidationMonitor = mutableMapOf<Int, ExitFullscreenInvalidationMonitor>()

    constructor(
        shellInit: ShellInit,
        transitions: Transitions,
        shellTaskOrganizer: ShellTaskOrganizer,
    ) : this(
        shellInit,
        transitions,
        shellTaskOrganizer,
        { taskId -> ExitFullscreenInvalidationMonitor(taskId, transitions, shellTaskOrganizer) },
    )

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        transitions.addHandler(this)
    }

    /** Adds a handler capable of handling fullscreen requests. */
    fun addHandler(handler: FullscreenRequestHandler) {
        // Insert at second from last position, so the fallback handler always runs last.
        handlers.add(handlers.size - 1, handler)
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        val fullscreenRequestChange = request.fullscreenRequestChange ?: return null
        val task = request.triggerTask ?: return null
        val mode = fullscreenRequestChange.modeRequest
        val callback = fullscreenRequestChange.remoteCallback
        return handle(transition, task, mode, callback)
    }

    private fun handle(
        transition: IBinder,
        task: RunningTaskInfo,
        @Activity.FullscreenModeRequest mode: Int,
        callback: IRemoteCallback?,
    ): WindowContainerTransaction? {
        logV(
            "handle mode=%s task=%d hasCallback=%b transition=%s",
            Activity.fullscreenModeRequestToString(mode),
            task.taskId,
            callback != null,
            transition,
        )
        val result =
            when (mode) {
                FULLSCREEN_MODE_REQUEST_ENTER -> {
                    handlers
                        .firstNotNullOf { it.handleEnterFullscreen(transition, task) }
                        .also { result -> handleEnterResult(task, result) }
                }
                FULLSCREEN_MODE_REQUEST_EXIT -> {
                    val restorable = removeRestorableState(task.taskId)
                    handlers
                        .firstNotNullOf { it.handleExitFullscreen(transition, task, restorable) }
                        .also { result -> handleExitResult(task, result) }
                }
                else -> error("Unexpected fullscreen request mode: $mode")
            }
        logV("handle result=%s", result)
        reportResult(callback, result)
        return result.wct
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        return false
    }

    private fun reportResult(callback: IRemoteCallback?, result: FullscreenRequestHandler.Result) {
        logV("reportResult code=%s callback=%s", requestResultToString(result.resultCode), callback)
        if (callback == null) return
        val bundle = Bundle().apply { putInt(REMOTE_CALLBACK_RESULT_KEY, result.resultCode) }
        try {
            callback.sendResult(bundle)
        } catch (e: RemoteException) {
            logW("Failed to send result to client: %s", e)
        }
    }

    private fun handleEnterResult(task: RunningTaskInfo, result: EnterResult) {
        when (result) {
            is EnterResult.Approved -> {
                // Successfully entered fullscreen.
                saveRestorableState(task.taskId, result)
                // Let this task request exit from now on.
                result.wct.setFullscreenRequestAllowMode(task.token, REQUEST_ALLOW_MODE_EXIT)
                // Also start monitoring mode changes that would invalidate the allowed exit.
                taskToInvalidationMonitor[task.taskId] =
                    invalidationMonitorSupplier(task.taskId).apply { start() }
            }
            is EnterResult.Failed -> {}
        }
    }

    private fun handleExitResult(task: RunningTaskInfo, result: ExitResult) {
        when (result) {
            is ExitResult.Approved -> {
                // Successfully exited fullscreen.
                // Reset the allowed mode back to the default.
                result.wct.setFullscreenRequestAllowMode(task.token, REQUEST_ALLOW_MODE_INHERIT)
                taskToInvalidationMonitor[task.taskId]?.close("exit approved")
            }
            is ExitResult.Failed -> {}
        }
    }

    private fun saveRestorableState(taskId: Int, result: EnterResult.Approved) {
        logV("saveRestorableState taskId=%d state=%s", taskId, result.restorableState)
        taskToRestorableState[taskId] = result.restorableState
    }

    private fun removeRestorableState(taskId: Int): RestorableState? {
        return taskToRestorableState.remove(taskId)
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL, "%s: $msg", TAG, *arguments)
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(ShellProtoLogGroup.WM_SHELL, "%s: $msg", TAG, *arguments)
    }

    /** A handler capable of handling fullscreen requests. */
    interface FullscreenRequestHandler {
        /** The name of this handler for debugging. */
        val name: String

        /**
         * Called when a fullscreen entry is being requested for [task].
         *
         * @return an [EnterResult] if the handler chooses to handle this request either by
         *   approving or rejecting it which means other handlers will not get a chance to handle
         *   it, or null to not indicate it won't be handled and should be passed on to the next
         *   handler.
         */
        fun handleEnterFullscreen(transition: IBinder, task: RunningTaskInfo): EnterResult?

        /**
         * Called when a fullscreen exit is being requested for [task].
         *
         * @return an [ExitResult] if the handler chooses to handle this request either by approving
         *   or rejecting it which means other handlers will not get a chance to handle it, or null
         *   to not indicate it won't be handled and should be passed on to the next handler.
         */
        fun handleExitFullscreen(
            transition: IBinder,
            task: RunningTaskInfo,
            restorableState: RestorableState?,
        ): ExitResult?

        /** The result of a fullscreen request. */
        sealed interface Result {
            /** The transaction to apply as a result of this request, if any. */
            val wct: WindowContainerTransaction?

            /** The result code of this request. */
            @RequestResult val resultCode: Int

            /** The handler that handled this request. */
            val handler: FullscreenRequestHandler
        }

        /** The result of a fullscreen entry request. */
        sealed class EnterResult : Result {
            @ConsistentCopyVisibility
            data class Approved
            private constructor(
                @RequestResult override val resultCode: Int,
                override val handler: FullscreenRequestHandler,
                override val wct: WindowContainerTransaction,
                val restorableState: RestorableState,
            ) : EnterResult() {

                constructor(
                    wct: WindowContainerTransaction,
                    handler: FullscreenRequestHandler,
                    restorableState: RestorableState,
                ) : this(RESULT_APPROVED, handler, wct, restorableState)

                override fun toString(): String {
                    return "Approved(" +
                        "resultCode=${requestResultToString(resultCode)}, " +
                        "handler=${handler.name}, " +
                        "restorableState=$restorableState" +
                        ")"
                }

                sealed class RestorableState {
                    data class Desktop(val originalDeskId: Int, val bounds: Rect) :
                        RestorableState()

                    data class SplitScreen(
                        @SplitPosition val originalSplitPosition: Int,
                        @PersistentSnapPosition val originalSnapPosition: Int,
                        val otherTaskId: Int,
                    ) : RestorableState()
                    // TODO: b/296268915 - add support for 3-app split.
                }
            }

            data class Failed(
                @RequestResult override val resultCode: Int,
                override val handler: FullscreenRequestHandler,
            ) : EnterResult() {
                override val wct: WindowContainerTransaction? = null

                init {
                    require(resultCode != RESULT_APPROVED)
                }

                override fun toString(): String {
                    return "Failed(" +
                        "resultCode=${requestResultToString(resultCode)}, " +
                        "handler=${handler.name}" +
                        ")"
                }
            }
        }

        /** The result of a fullscreen exit request. */
        sealed class ExitResult : Result {
            @ConsistentCopyVisibility
            data class Approved
            private constructor(
                @RequestResult override val resultCode: Int,
                override val handler: FullscreenRequestHandler,
                override val wct: WindowContainerTransaction,
            ) : ExitResult() {

                constructor(
                    wct: WindowContainerTransaction,
                    handler: FullscreenRequestHandler,
                ) : this(RESULT_APPROVED, handler, wct)

                override fun toString(): String {
                    return "Approved(" +
                        "resultCode=${requestResultToString(resultCode)}, " +
                        "handler=${handler.name}" +
                        ")"
                }
            }

            data class Failed(
                @RequestResult override val resultCode: Int,
                override val handler: FullscreenRequestHandler,
            ) : ExitResult() {
                override val wct: WindowContainerTransaction? = null

                init {
                    require(resultCode != RESULT_APPROVED)
                }

                override fun toString(): String {
                    return "Failed(" +
                        "resultCode=${requestResultToString(resultCode)}, " +
                        "handler=${handler.name}" +
                        ")"
                }
            }
        }
    }

    /**
     * A fallback handler that runs last and always handles the requests as failed. This is
     * important to notify the client about the failure as quickly as possible as opposed to
     * system_server awaiting shell's response for longer than needed.
     */
    private class FallbackFullscreenRequestHandler : FullscreenRequestHandler {
        override val name: String = "FallbackFullscreenRequestHandler"

        override fun handleEnterFullscreen(
            transition: IBinder,
            task: RunningTaskInfo,
        ): EnterResult {
            if (task.windowingMode == WINDOWING_MODE_FULLSCREEN) {
                return EnterResult.Failed(RESULT_FAILED_ALREADY_FULLY_EXPANDED, this)
            }
            return EnterResult.Failed(RESULT_FAILED_NOT_SUPPORTED, this)
        }

        override fun handleExitFullscreen(
            transition: IBinder,
            task: RunningTaskInfo,
            restorableState: RestorableState?,
        ): ExitResult {
            if (task.windowingMode != WINDOWING_MODE_FULLSCREEN) {
                return ExitResult.Failed(RESULT_FAILED_NOT_IN_FULLSCREEN_WITH_HISTORY, this)
            }
            return ExitResult.Failed(RESULT_FAILED_NOT_SUPPORTED, this)
        }
    }

    /**
     * Monitors events that would invalidate [taskId]'s ability to exit fullscreen mode through an
     * API request, such as it changing windowing mode or closing.
     */
    @OpenForTesting
    open class ExitFullscreenInvalidationMonitor(
        private val taskId: Int,
        private val transitions: Transitions,
        private val shellTaskOrganizer: ShellTaskOrganizer,
    ) : Transitions.TransitionObserver, ShellTaskOrganizer.TaskVanishedListener {

        /** Starts monitoring. */
        @OpenForTesting
        open fun start() {
            logV("Starting monitor for taskId=%d", taskId)
            transitions.registerObserver(this)
            shellTaskOrganizer.addTaskVanishedListener(this)
        }

        /** Stops monitoring. */
        @OpenForTesting
        open fun close(reason: String) {
            logV("Closing monitor for taskId=%d, reason=%s", taskId, reason)
            transitions.unregisterObserver(this)
            shellTaskOrganizer.removeTaskVanishedListener(this)
        }

        override fun onTransitionReady(
            transition: IBinder,
            info: TransitionInfo,
            startTransaction: SurfaceControl.Transaction,
            finishTransaction: SurfaceControl.Transaction,
        ) {
            for (c in info.findTaskChanges(taskId)) {
                if (c.mode == TRANSIT_CLOSE) {
                    // Task closed, no longer need to monitor it.
                    close("transit close")
                    return
                }
                val taskInfo = c.taskInfo ?: continue
                if (taskInfo.windowingMode != WINDOWING_MODE_FULLSCREEN) {
                    // Task changed from fullscreen to another mode.
                    invalidate(taskInfo.token, "mode change")
                    close("mode change")
                    return
                }
            }
        }

        override fun onTaskVanished(taskInfo: RunningTaskInfo?) {
            if (taskInfo?.taskId == taskId) {
                // Task closed, no longer need to monitor it.
                close("task vanished")
            }
        }

        private fun invalidate(taskToken: WindowContainerToken, reason: String) {
            logV("Invalidating exit for taskId=%d reason=%s", taskId, reason)
            val wct =
                WindowContainerTransaction().apply {
                    setFullscreenRequestAllowMode(taskToken, REQUEST_ALLOW_MODE_INHERIT)
                }
            shellTaskOrganizer.applyTransaction(wct)
        }

        private fun TransitionInfo.findTaskChanges(taskId: Int): List<TransitionInfo.Change> {
            return changes.filter { it.taskInfo?.taskId == taskId }
        }

        // TODO(b/478792808): Remove suppression
        @SuppressWarnings("ProtoLogNonConstantFormat")
        private fun logV(msg: String, vararg arguments: Any?) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL, "%s: $msg", TAG, *arguments)
        }
    }

    companion object {
        private const val TAG = "ClientFullscreenRequestController"
    }
}
