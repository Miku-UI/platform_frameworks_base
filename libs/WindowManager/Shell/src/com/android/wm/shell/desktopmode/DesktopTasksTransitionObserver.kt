/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.ActivityManager
import android.app.TaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.graphics.Rect
import android.graphics.RectF
import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.DesktopModeFlags.ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER
import android.window.DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController
import com.android.wm.shell.pinnedlayer.phone.isNotPinned
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil.isClosingMode
import com.android.wm.shell.shared.TransitionUtil.isOpeningMode
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/**
 * A [Transitions.TransitionObserver] that observes shell transitions and updates the
 * [DesktopRepository] state TODO: b/332682201 This observes transitions related to desktop mode and
 * other transitions that originate both within and outside shell.
 */
class DesktopTasksTransitionObserver(
    private val desktopUserRepositories: DesktopUserRepositories,
    private val transitions: Transitions,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val desktopMixedTransitionHandler: DesktopMixedTransitionHandler,
    private val desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
    private val displayController: DisplayController,
    private val pinnedLayerController: PinnedLayerController?,
    desktopState: DesktopState,
    shellInit: ShellInit,
) : Transitions.TransitionObserver {

    data class CloseWallpaperTransition(val transition: IBinder, val displayId: Int)

    private var transitionToCloseWallpaper: CloseWallpaperTransition? = null
    private var closingTransitionToTransitionInfo = HashMap<IBinder, TransitionInfo>()
    private var currentProfileId: Int
    private val pendingUserBoundsChangeTransitions = mutableSetOf<IBinder>()

    init {
        if (desktopState.canEnterDesktopMode) {
            shellInit.addInitCallback(::onInit, this)
        }
        currentProfileId = ActivityManager.getCurrentUser()
    }

    fun onInit() {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "DesktopTasksTransitionObserver: onInit")
        transitions.registerObserver(this)
    }

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    ) {
        // TODO: b/332682201 Update repository state
        updateTopTransparentFullscreenTaskId(info)
        updateWallpaperToken(info)
        if (
            !desktopMixedTransitionHandler.hasTransition(transition) &&
                containsClosingTaskInDesktop(info)
        ) {
            desktopMixedTransitionHandler.addPendingMixedTransition(
                DesktopMixedTransitionHandler.PendingMixedTransition.Close(transition)
            )
            closingTransitionToTransitionInfo.put(transition, info)
        }
        removeWallpaperOnLastTaskClosingIfNeeded(transition, info)
        updateRememberedBoundsIfNeeded(info, transition)
    }

    private fun containsClosingTaskInDesktop(info: TransitionInfo): Boolean {
        for (change in info.changes) {
            val taskInfo = change.taskInfo
            if (taskInfo == null || taskInfo.taskId == -1) {
                continue
            }
            val desktopRepository = desktopUserRepositories.getProfile(taskInfo.userId)
            val isInDesktop = desktopRepository.isAnyDeskActive(taskInfo.displayId)
            if (
                isInDesktop &&
                    change.mode == TRANSIT_CLOSE &&
                    taskInfo.windowingMode == WINDOWING_MODE_FREEFORM
            ) {
                return true
            }
        }
        return false
    }

    private fun removeWallpaperOnLastTaskClosingIfNeeded(
        transition: IBinder,
        info: TransitionInfo,
    ) {
        // TODO: 380868195 - Smooth animation for wallpaper activity closing just by itself
        for (change in info.changes) {
            val taskInfo = change.taskInfo
            if (taskInfo == null || taskInfo.taskId == -1) {
                continue
            }

            val desktopRepository = desktopUserRepositories.getProfile(taskInfo.userId)
            if (
                !desktopRepository.isAnyDeskActive(taskInfo.displayId) &&
                    change.mode == TRANSIT_CLOSE &&
                    taskInfo.windowingMode == WINDOWING_MODE_FREEFORM &&
                    desktopWallpaperActivityTokenProvider.getToken(taskInfo.displayId) != null
            ) {
                transitionToCloseWallpaper =
                    CloseWallpaperTransition(transition, taskInfo.displayId)
                currentProfileId = taskInfo.userId
            }
        }
    }

    override fun onTransitionStarting(transition: IBinder) {
        // TODO: b/332682201 Update repository state
    }

    override fun onTransitionMerged(merged: IBinder, playing: IBinder) {
        // TODO: b/332682201 Update repository state
    }

    override fun onTransitionFinished(transition: IBinder, aborted: Boolean) {
        val lastSeenTransitionToCloseWallpaper = transitionToCloseWallpaper
        // TODO: b/332682201 Update repository state
        if (lastSeenTransitionToCloseWallpaper?.transition == transition) {
            // TODO: b/362469671 - Handle merging the animation when desktop is also closing.
            desktopWallpaperActivityTokenProvider
                .getToken(lastSeenTransitionToCloseWallpaper.displayId)
                ?.let { wallpaperActivityToken ->
                    if (ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER.isTrue()) {
                        transitions.startTransition(
                            TRANSIT_TO_BACK,
                            WindowContainerTransaction()
                                .reorder(wallpaperActivityToken, /* onTop= */ false),
                            null,
                        )
                    } else {
                        transitions.startTransition(
                            TRANSIT_CLOSE,
                            WindowContainerTransaction().removeTask(wallpaperActivityToken),
                            null,
                        )
                    }
                }
            transitionToCloseWallpaper = null
        }

        // If a task is closing and is not handled by back navigation logic, remove it here with a
        // follow up transition fully so it doesn't show up on recents.
        //
        // The reason that this is done here and not on [DesktopTasksController#handleRequest] is
        // because for closing tasks we first need to check whether it's because of back navigation
        // so that we can minimize it if needed.
        val info = closingTransitionToTransitionInfo.remove(transition) ?: return
        removeClosingTasks(info)
    }

    /**
     * Finds the closing tasks in the change and removes them full by a [TRANSIT_CLOSE] transition.
     */
    private fun removeClosingTasks(info: TransitionInfo) {
        val wct = WindowContainerTransaction()
        // Keeping processes alive allows for faster (warm) future launches of the same apps.
        val killProcess = !Flags.skipKillProcessForDesktopTaskCoreCloseTransition()
        info.changes
            .filter { it.mode == TRANSIT_CLOSE }
            .mapNotNull { it.taskInfo }
            .forEach { taskInfo ->
                if (taskInfo.windowingMode != WINDOWING_MODE_FREEFORM) return@forEach
                wct.removeTask(taskInfo.token, /* removeFromRecents= */ true, killProcess)
                ProtoLog.d(
                    WM_SHELL_DESKTOP_MODE,
                    "DesktopTasksTransitionObserver: removing closing task=%d fully",
                    taskInfo.taskId,
                )
            }

        if (!wct.isEmpty) transitions.startTransition(TRANSIT_CLOSE, wct, null)
    }

    private fun updateWallpaperToken(info: TransitionInfo) {
        if (!ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY.isTrue()) {
            return
        }
        info.changes.forEach { change ->
            change.taskInfo?.let { taskInfo ->
                if (DesktopWallpaperActivity.isWallpaperTask(taskInfo)) {
                    when (change.mode) {
                        TRANSIT_OPEN -> {
                            desktopWallpaperActivityTokenProvider.setToken(
                                taskInfo.token,
                                taskInfo.displayId,
                            )
                            // After the task for the wallpaper is created, set it non-trimmable.
                            // This is important to prevent recents from trimming and removing the
                            // task.
                            shellTaskOrganizer.applyTransaction(
                                WindowContainerTransaction()
                                    .setTaskTrimmableFromRecents(taskInfo.token, false)
                            )
                        }
                        TRANSIT_CLOSE ->
                            desktopWallpaperActivityTokenProvider.removeToken(taskInfo.displayId)
                        else -> {}
                    }
                }
            }
        }
    }

    private fun updateTopTransparentFullscreenTaskId(info: TransitionInfo) {
        run forEachLoop@{
            info.changes.forEach { change ->
                change.taskInfo?.let { task ->
                    val desktopRepository = desktopUserRepositories.getProfile(task.userId)
                    val displayId = task.displayId
                    val deskId = desktopRepository.getActiveDeskId(displayId) ?: return@forEachLoop
                    val transparentTaskId =
                        desktopRepository.getTopTransparentFullscreenTaskData(deskId)?.taskId
                    if (transparentTaskId == null) return@forEachLoop
                    val changeMode = change.mode
                    val taskId = task.taskId
                    val isTopTransparentFullscreenTaskClosing =
                        taskId == transparentTaskId && isClosingMode(changeMode)
                    val isNonTopTransparentFullscreenTaskOpening =
                        taskId != transparentTaskId && isOpeningMode(changeMode)
                    // Clear `topTransparentFullscreenTask` information from repository if task
                    // is closed, sent to back or if a different task is opened, brought to front.
                    if (
                        isTopTransparentFullscreenTaskClosing ||
                            isNonTopTransparentFullscreenTaskOpening
                    ) {
                        desktopRepository.clearTopTransparentFullscreenTaskData(deskId)
                        return@forEachLoop
                    }
                }
            }
        }
    }

    private fun updateRememberedBoundsIfNeeded(info: TransitionInfo, transition: IBinder) {
        if (!Flags.enableRememberedBounds()) {
            return
        }

        // Clears remembered bounds if needed.
        run forEachLoop@{
            info.changes.forEach { change ->
                change.taskInfo?.let { taskInfo ->
                    // TODO: b/477848767 - Remember only position if the task is unresizable.
                    if (change.mode == TRANSIT_OPEN && !taskInfo.isResizeable) {
                        val desktopRepository = desktopUserRepositories.getProfile(taskInfo.userId)
                        val packageName =
                            taskInfo.componentNameForRememberedBounds?.packageName
                                ?: return@forEachLoop
                        desktopRepository.clearRememberedBoundsRatio(packageName)
                        logV(
                            "Remembered bounds is cleared as task#%d is unresizable",
                            taskInfo.taskId,
                        )
                    }
                }
            }
        }

        if (!pendingUserBoundsChangeTransitions.contains(transition)) return
        pendingUserBoundsChangeTransitions.remove(transition)

        run forEachLoop@{
            info.changes.forEach { change ->
                change.taskInfo?.let { taskInfo ->
                    if (change.mode != TRANSIT_CHANGE) return@forEachLoop
                    // Has any bounds change.
                    if (change.startAbsBounds == change.endAbsBounds) return@forEachLoop
                    // Is a freeform task.
                    if (!taskInfo.isDesktopTask()) return@forEachLoop
                    // TODO: b/477848767 - Remember only position if the task is unresizable.
                    // Is resizable.
                    if (!taskInfo.isResizeable) {
                        logV(
                            "Remembered bounds is NOT updated as task#%d is unresizable",
                            taskInfo.taskId,
                        )
                        return@forEachLoop
                    }
                    if (taskInfo.appCompatTaskInfo?.hasIsExcludeCaptionInsets() == true) {
                        logV(
                            "Remembered bounds is NOT updated as task#%d has " +
                                "isExcludeCaptionInsets",
                            taskInfo.taskId,
                        )
                        return@forEachLoop
                    }

                    val desktopRepository = desktopUserRepositories.getProfile(taskInfo.userId)
                    if (desktopRepository.isTaskInFullImmersiveState(taskInfo.taskId)) {
                        // We don't update the remembered bounds while the task is in full immersive
                        // state.
                        return@forEachLoop
                    }

                    val displayLayout =
                        displayController.getDisplayLayout(taskInfo.displayId) ?: return@forEachLoop
                    val packageName =
                        taskInfo.componentNameForRememberedBounds?.packageName ?: return@forEachLoop
                    val stableBounds =
                        Rect().apply { displayLayout.getStableBoundsForDesktopMode(this) }
                    val bounds = taskInfo.configuration.windowConfiguration.bounds
                    val boundsRatio =
                        RectF().apply {
                            left =
                                (bounds.left - stableBounds.left) / stableBounds.width().toFloat()
                            top = (bounds.top - stableBounds.top) / stableBounds.height().toFloat()
                            right =
                                (bounds.right - stableBounds.left) / stableBounds.width().toFloat()
                            bottom =
                                (bounds.bottom - stableBounds.top) / stableBounds.height().toFloat()
                        }
                    logV(
                        "Remembered bounds for package=%s is updated as task#%d was resized to " +
                            "%s: boundsRatio=%s",
                        packageName,
                        taskInfo.taskId,
                        bounds,
                        boundsRatio,
                    )
                    desktopRepository.setRememberedBoundsRatio(packageName, boundsRatio)
                }
            }
        }
    }

    fun addPendingUserBoundsChangeTransition(transition: IBinder) {
        if (!Flags.enableRememberedBounds()) {
            return
        }
        pendingUserBoundsChangeTransitions.add(transition)
    }

    private fun TaskInfo.isDesktopTask(): Boolean =
        isFreeform && pinnedLayerController?.isNotPinned(taskId) == true

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private companion object {
        const val TAG = "DesktopTasksTransitionObserver"
    }
}
