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
package com.android.wm.shell.desktopmode.clientfullscreenrequest

import android.app.ActivityManager.RunningTaskInfo
import android.app.FullscreenRequestHandler.RESULT_FAILED_NOT_SUPPORTED
import android.app.WindowConfiguration
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.content.Context
import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags
import com.android.wm.shell.common.ClientFullscreenRequestController
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.EnterResult
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.EnterResult.Approved.RestorableState
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.ExitResult
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.animation.DesktopToFullscreenTaskAnimator
import com.android.wm.shell.desktopmode.animation.EnterDesktopTaskAnimator
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.desktopmode.multidesks.DesksController
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener
import com.android.wm.shell.windowdecor.extension.isFullscreen
import java.util.Optional
import kotlinx.coroutines.runBlocking

/**
 * Handles client-started fullscreen requests when the requester task was a desktop task. See
 * [android.app.Activity.requestFullscreenMode].
 *
 * Currently supports moving desktop tasks to fullscreen and back to their original desk. It does
 * not support restoring to a different or new desk if the original desk was removed during the
 * transient fullscreen state.
 */
open class DesktopFullscreenRequestHandler(
    shellInit: ShellInit,
    private val context: Context,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desksOrganizer: DesksOrganizer,
    private val desksController: DesksController,
    private val desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
    private val displayController: DisplayController,
    private val clientFullscreenRequestController: Optional<ClientFullscreenRequestController>,
    private val transactionSupplier: () -> SurfaceControl.Transaction,
) : FullscreenRequestHandler {

    constructor(
        shellInit: ShellInit,
        context: Context,
        desktopUserRepositories: DesktopUserRepositories,
        desksOrganizer: DesksOrganizer,
        desksController: DesksController,
        desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
        displayController: DisplayController,
        clientFullscreenRequestController: Optional<ClientFullscreenRequestController>,
    ) : this(
        shellInit = shellInit,
        context = context,
        desktopUserRepositories = desktopUserRepositories,
        desksOrganizer = desksOrganizer,
        desksController = desksController,
        desktopWallpaperActivityTokenProvider = desktopWallpaperActivityTokenProvider,
        displayController = displayController,
        clientFullscreenRequestController = clientFullscreenRequestController,
        transactionSupplier = { SurfaceControl.Transaction() },
    )

    /** Using setter to avoid circular dependencies. */
    lateinit var desktopTasksController: DesktopTasksController
    /** A listener to invoke on animation changes during entry/exit. */
    var onTaskResizeAnimationListener: OnTaskResizeAnimationListener? = null

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        clientFullscreenRequestController.ifPresent { c -> c.addHandler(this) }
    }

    override val name: String = TAG

    override fun handleEnterFullscreen(transition: IBinder, task: RunningTaskInfo): EnterResult? {
        val repository = desktopUserRepositories.getProfile(task.userId)
        val activeDesk = repository.getActiveDeskId(task.displayId)
        val isActiveTaskInDesk =
            activeDesk?.let { deskId ->
                repository.isActiveTaskInDesk(taskId = task.taskId, deskId = deskId)
            } ?: false
        val deskIdFromTaskInfo = desksOrganizer.getDeskIdFromTaskInfo(task)
        logV(
            "handleEnterFullscreen taskId=%d displayId=%d winMode=%s activeDesk=%d " +
                "isActiveTaskInDesk=%b deskIdFromTaskInfo=%d",
            task.taskId,
            task.displayId,
            WindowConfiguration.windowingModeToString(task.windowingMode),
            activeDesk,
            isActiveTaskInDesk,
            deskIdFromTaskInfo,
        )

        if (!isActiveTaskInDesk || activeDesk != deskIdFromTaskInfo) {
            logI("handleEnterFullscreen task must be in active desk but wasn't, ignoring")
            return null
        }
        // The task requesting fullscreen is a desktop task in the active desk, accept the request.
        val wct = WindowContainerTransaction()
        desktopTasksController
            .addMoveToFullscreenChanges(
                wct = wct,
                taskInfo = task,
                willExitDesktop = true,
                exitReason = ExitReason.CLIENT_REQUEST_ENTER_FULLSCREEN,
            )
            ?.invoke(transition)
        return EnterResult.Approved(
            wct = wct,
            handler = this,
            restorableState =
                RestorableState.Desktop(
                    originalDeskId = requireNotNull(activeDesk),
                    bounds = task.configuration.windowConfiguration.bounds,
                ),
        )
    }

    override fun handleExitFullscreen(
        transition: IBinder,
        task: RunningTaskInfo,
        restorableState: RestorableState?,
    ): ExitResult? = runBlocking {
        val repository = desktopUserRepositories.getProfile(task.userId)
        val activeDesk = repository.getActiveDeskId(task.displayId)
        val deskIdFromTaskInfo = desksOrganizer.getDeskIdFromTaskInfo(task)
        logV(
            "handleExitFullscreen taskId=%d displayId=%d winMode=%s activeDesk=%d " +
                "deskIdFromTaskInfo=%d restorableState=%s",
            task.taskId,
            task.displayId,
            WindowConfiguration.windowingModeToString(task.windowingMode),
            activeDesk,
            deskIdFromTaskInfo,
            restorableState,
        )
        if (restorableState == null) {
            logI("handleExitFullscreen with null restorable state, ignoring")
            return@runBlocking null
        }
        if (restorableState !is RestorableState.Desktop) {
            logI("handleExitFullscreen for non-desktop state, ignoring")
            return@runBlocking null
        }

        val targetDeskId = getExitTargetDeskId(task, restorableState, repository)
        if (targetDeskId == null) {
            logI("handleExitFullscreen but could not find a target desk, rejecting")
            return@runBlocking ExitResult.Failed(
                resultCode = RESULT_FAILED_NOT_SUPPORTED,
                handler = this@DesktopFullscreenRequestHandler,
            )
        }
        val targetDisplayId = repository.getDisplayForDesk(targetDeskId)

        val wct =
            desktopTasksController.handleFreeformTaskPlacement(
                task = task,
                transition = transition,
                targetDisplayId = targetDisplayId,
                suggestedTargetDeskId = targetDeskId,
                requestedTaskBounds = restorableState.bounds,
                requestType = TRANSIT_CHANGE,
                enterReason = EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN,
                forceBringTaskToFront = true,
            )
        if (wct == null) {
            logI("handleExitFullscreen but placement failed, rejecting")
            return@runBlocking ExitResult.Failed(
                resultCode = RESULT_FAILED_NOT_SUPPORTED,
                handler = this@DesktopFullscreenRequestHandler,
            )
        }
        return@runBlocking ExitResult.Approved(wct, this@DesktopFullscreenRequestHandler)
    }

    private suspend fun getExitTargetDeskId(
        task: RunningTaskInfo,
        restorableState: RestorableState.Desktop,
        repository: DesktopRepository,
    ): Int? {
        val originalDeskId = restorableState.originalDeskId
        val deskIdsInDisplay = repository.getDeskIds(task.displayId)
        if (deskIdsInDisplay.contains(originalDeskId)) {
            // Original desk still exists, use it.
            return originalDeskId
        }
        if (deskIdsInDisplay.isNotEmpty()) {
            logI("getExitTargetDeskId original desk does not exist, falling back to the default")
            return checkNotNull(repository.getDefaultDeskId(task.displayId)) {
                "Expected a default desk to exist after having checked at least 1 desk exists"
            }
        }
        if (!desksController.canCreateDeskInDisplay(task.displayId, task.userId)) {
            logW("getExitTargetDeskId original desk does not exist and can't create new one")
            return null
        }
        logI("getExitTargetDeskId original desk does not exist, creating a new one")
        return desktopTasksController.createDeskSuspending(
            displayId = task.displayId,
            userId = task.userId,
        )
    }

    /** Whether the given [request] should be handled by this handler. */
    fun shouldHandleRequest(request: TransitionRequestInfo): Boolean {
        if (Flags.delegateRequestFullscreenHandlingToShell()) return false
        val type = request.type
        if (type != TRANSIT_CHANGE)
            return false.also {
                // Requests from the client API always use TRANSIT_CHANGE.
                logV("shouldHandleRequest type=%d is not TRANSIT_CHANGE, rejecting", type)
            }
        val task =
            request.triggerTask
                ?: return false.also { logV("shouldHandleRequest triggerTask is null, rejecting") }
        val repository = desktopUserRepositories.getProfile(task.userId)
        val activeDesk = repository.getActiveDeskId(task.displayId)
        val isActiveTask = repository.isActiveTask(task.taskId)
        val isActiveTaskInDesk =
            activeDesk?.let { deskId ->
                repository.isActiveTaskInDesk(taskId = task.taskId, deskId = deskId)
            } ?: false
        val deskIdFromTaskInfo = desksOrganizer.getDeskIdFromTaskInfo(task)
        logV(
            "shouldHandleRequest type=%s taskId=%d displayId=%d winMode=%s activeDesk=%d " +
                "isActiveTask=%b isActiveTaskInDesk=%b deskIdFromTaskInfo=%d",
            Transitions.transitTypeToString(type),
            task.taskId,
            task.displayId,
            WindowConfiguration.windowingModeToString(task.windowingMode),
            activeDesk,
            isActiveTask,
            isActiveTaskInDesk,
            deskIdFromTaskInfo,
        )
        // TODO: b/446235140 - During an exit-request, if the desk the task belonged to was deleted
        //  while the task was in fullscreen, then WM core doesn't know where to reparent the task
        //  and (in tablets / touch-first) it doesn't know how to force it to freeform mode either
        //  because the "restore mode" is undefined. This means this request will have the task's
        //  windowing mode set to fullscreen (because the TDA is fullscreen) and thus we don't have
        //  enough information to identify it as an exit request and correctly put it in an
        //  existing/new desk. Therefore this request will be rejected and the task will remain
        //  in fullscreen.
        when {
            task.isFullscreen -> {
                // To be an enter-fullscreen request, a desk must be active in this display, it
                // must contain the task requesting fullscreen, and the task must still be in this
                // desk at the time of the request.
                if (activeDesk != null && isActiveTaskInDesk && activeDesk == deskIdFromTaskInfo) {
                    return true.also {
                        logV("shouldHandleRequest is an enter-fullscreen request, accepting")
                    }
                }
                return false.also {
                    logV("shouldHandleRequest is not an enter-fullscreen request, rejecting")
                }
            }
            task.isFreeform -> {
                // To be an exit-fullscreen request (aka restore to desktop), a desk must not be
                // active in this display and the task must not be a desktop task. It is usually
                // expected that the task is already parented to a desk when WM core was able to
                // restore to it, but it is also possible that the desk was removed prior to the
                // exit and the task won't have a parent desk assigned.
                if (activeDesk == null && !isActiveTask) {
                    return true.also {
                        logV("shouldHandleRequest is an exit-fullscreen request, accepting")
                    }
                }
                return false.also {
                    logV("shouldHandleRequest is not an exit-fullscreen request, rejecting")
                }
            }
            else -> {
                // Other modes are not supported.
                return false.also {
                    logV(
                        "shouldHandleRequest is unexpected winMode=%s, rejecting",
                        WindowConfiguration.windowingModeToString(task.windowingMode),
                    )
                }
            }
        }
    }

    /** Handles the request as a client fullscreen request if valid. */
    fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        if (!shouldHandleRequest(request)) return null
        val task = request.triggerTask ?: return null
        val requestType = request.type
        val userId = task.userId
        val repository = desktopUserRepositories.getProfile(userId)
        val taskId = task.taskId
        val displayId = task.displayId
        val activeDeskId = repository.getActiveDeskId(displayId)
        val taskDeskId = desksOrganizer.getDeskIdFromTaskInfo(task)
        logV(
            "handleRequest taskId=%d displayId=%d activeDeskId=%d taskDeskId=%d userId=%d",
            taskId,
            displayId,
            activeDeskId,
            taskDeskId,
            userId,
        )
        when {
            task.isFullscreen -> {
                val wct = WindowContainerTransaction()
                desktopTasksController
                    .addMoveToFullscreenChanges(
                        wct = wct,
                        taskInfo = task,
                        willExitDesktop = true,
                        // The task is already fullscreen, make sure to skip setting the windowing
                        // mode again here, to avoid it being set to WINDOWING_MODE_UNDEFINED which
                        // would make WM core clear the multiwindow restore mode since it's
                        // considered to be different to WINDOWING_MODE_FULLSCREEN, even when it
                        // resolves to the same.
                        skipSetWindowingMode = true,
                        exitReason = ExitReason.CLIENT_REQUEST_ENTER_FULLSCREEN,
                    )
                    ?.invoke(transition)
                return wct
            }
            task.isFreeform -> {
                return desktopTasksController.handleFreeformTaskPlacement(
                    task = task,
                    transition = transition,
                    targetDisplayId = displayId,
                    suggestedTargetDeskId = taskDeskId,
                    requestedTaskBounds = null,
                    requestType = requestType,
                    enterReason = EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN,
                )
            }
            else -> {
                logE("Unsupported mode=%d", task.windowingMode)
                return null
            }
        }
    }

    /**
     * Animates [taskId] that is entering fullscreen from desktop and the rest of the desktop that
     * is disappearing.
     */
    open fun startEnterFullscreenFromDesktopAnimation(
        taskId: Int,
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: TransitionFinishCallback,
    ) {
        logV("startEnterFullscreenFromDesktopAnimation taskId=%d transition=%s", taskId, transition)
        val change =
            requireNotNull(info.changes.firstOrNull { c -> c.taskInfo?.taskId == taskId }) {
                "The task moving to fullscreen must exist in the transition"
            }
        DesktopToFullscreenTaskAnimator(
                context,
                transactionSupplier,
                displayController,
                onTaskResizeAnimationListener,
            )
            .animate(
                change = change,
                startTransaction = startTransaction,
                finishCallback = finishCallback,
            )
    }

    /**
     * Animates [taskId] that is exiting fullscreen into desktop and the rest of the desktop that is
     * appearing.
     */
    open fun startExitFullscreenToDesktopAnimation(
        taskId: Int,
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: TransitionFinishCallback,
    ) {
        logV("startExitFullscreenToDesktopAnimation taskId=%d transition=%s", taskId, transition)
        // At the top, we have desktop tasks.
        val tasksLayer = info.changes.size * 4
        // Then the desk they belong to.
        val deskLayer = info.changes.size * 3
        // Then the desk backdrop (Home or Desktop Wallpaper task).
        val backdropLayer = info.changes.size * 2
        // Then the actual wallpaper.
        val wallpaperLayer = info.changes.size * 1

        var changeToAnimate: Change? = null
        info.changes.withIndex().forEach { (i, change) ->
            val leash = change.leash
            val startBounds = change.startAbsBounds
            val endBounds = change.endAbsBounds
            val endOffset = change.endRelOffset
            val isDesktopTask = isDesktopTaskChange(change)
            val isDesk = isDeskChange(change)
            val isHome = isHomeChange(change)
            val isDesktopWallpaper = isDesktopWallpaper(change)
            val isWallpaper = isWallpaper(change)

            if (isDesktopTask && change.taskInfo?.taskId == taskId) {
                changeToAnimate = change
            }

            when {
                isDesktopTask -> {
                    startTransaction
                        .setLayer(leash, tasksLayer - i)
                        .setPosition(leash, startBounds.left.toFloat(), startBounds.top.toFloat())
                        .setWindowCrop(leash, startBounds.width(), startBounds.height())
                        .show(leash)
                }
                isDesk -> {
                    startTransaction
                        .setLayer(leash, deskLayer - i)
                        .setPosition(leash, endOffset.x.toFloat(), endOffset.y.toFloat())
                        .setWindowCrop(leash, endBounds.width(), endBounds.height())
                        .show(leash)
                }
                isHome || isDesktopWallpaper -> {
                    startTransaction
                        .setLayer(leash, backdropLayer - i)
                        .setPosition(leash, endOffset.x.toFloat(), endOffset.y.toFloat())
                        .setWindowCrop(leash, endBounds.width(), endBounds.height())
                        .show(leash)
                }
                isWallpaper -> {
                    startTransaction
                        .setLayer(leash, wallpaperLayer - i)
                        .setPosition(leash, endOffset.x.toFloat(), endOffset.y.toFloat())
                        .setWindowCrop(leash, endBounds.width(), endBounds.height())
                        .show(leash)
                }
                else -> {
                    logW("Unexpected change: %s", change)
                }
            }
        }

        val change =
            requireNotNull(changeToAnimate) {
                "The task moving to desktop must exist in the transition"
            }
        EnterDesktopTaskAnimator(context, transactionSupplier, onTaskResizeAnimationListener)
            .animate(
                change = change,
                startTransaction = startTransaction,
                finishTransaction = finishTransaction,
                finishCallback = finishCallback,
            )
    }

    private fun isDesktopTaskChange(change: Change): Boolean {
        val task = change.taskInfo ?: return false
        return desktopUserRepositories.getProfile(task.userId).isActiveTask(task.taskId)
    }

    private fun isDeskChange(change: Change): Boolean {
        val task = change.taskInfo ?: return false
        return task.taskId in
            desktopUserRepositories.getProfile(task.userId).getDeskIds(task.displayId)
    }

    private fun isHomeChange(change: Change): Boolean {
        val task = change.taskInfo ?: return false
        return task.activityType == ACTIVITY_TYPE_HOME
    }

    private fun isDesktopWallpaper(change: Change): Boolean {
        val task = change.taskInfo ?: return false
        return desktopWallpaperActivityTokenProvider.getToken(task.displayId) == task.token
    }

    private fun isWallpaper(change: Change) = TransitionUtil.isWallpaper(change)

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logI(msg: String, vararg arguments: Any?) {
        ProtoLog.i(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logE(msg: String, vararg arguments: Any?) {
        ProtoLog.e(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopFullscreenRequestHandler"
    }
}
