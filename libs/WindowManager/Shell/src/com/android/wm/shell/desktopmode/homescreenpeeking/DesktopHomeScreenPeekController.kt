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

package com.android.wm.shell.desktopmode.homescreenpeeking

import android.app.ActivityManager
import android.content.Context
import android.graphics.Rect
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityUtils
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.R
import com.android.wm.shell.sysui.ShellController

/**
 * Controls peeking of task windows to show the home screen in Desktop Mode.
 *
 * Home Screen Peeking allows users to temporarily shift all visible desktop tasks to the edges of
 * the screen, revealing the underlying Home Screen (Launcher). This class manages the lifecycle of
 * a peek operation, including state tracking (e.g., [PeekState]), calculating the destination
 * bounds for tasks, and coordinating with the [DesktopHomeScreenPeekTransitionHandler] to perform
 * the animations.
 *
 * When a peek is triggered, tasks are shifted either to the left or right edge of the screen
 * depending on whether their center point is left or right of the screen's center point. A small
 * portion of the task remains visible on screen to indicate the tasks are still active and can be
 * restored (unpeeked).
 */
class DesktopHomeScreenPeekController(
    private val context: Context,
    private val peekTransitionHandler: DesktopHomeScreenPeekTransitionHandler,
    private val displayController: DisplayController,
    private val shellController: ShellController,
    private val userRepositories: DesktopUserRepositories,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val desktopWallpaperActivityUtils: DesktopWallpaperActivityUtils,
    private val desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
) {
    private val peekAmount =
        context.resources.getDimensionPixelSize(
            R.dimen.desktop_home_screen_peeking_visible_peek_amount
        )

    var isPeeking = false

    private var taskBoundsBeforePeek: Map<Int, Rect>? = null

    /**
     * Initiates the "Home Screen Peek" transition.
     *
     * This method calculates the peek destination for all currently visible desktop tasks and
     * starts a [TRANSIT_CHANGE] transition to move them. If the feature flag is disabled or a peek
     * is already in progress, this method does nothing.
     */
    fun peek() {
        if (!Flags.enableHomeScreenPeeking()) {
            ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: Unable to peek. Flag not enabled", TAG)
            return
        }
        if (isPeeking) {
            ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: Unable to peek. Already peeking.", TAG)
            return
        }
        val desktopTasks = getVisibleDesktopTasks()
        if (desktopTasks.isEmpty()) {
            ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: not peeking. No visible desktop tasks", TAG)
            return
        }
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: peek requested", TAG)
        isPeeking = true
        val wct = WindowContainerTransaction()
        taskBoundsBeforePeek =
            desktopTasks.associate { task ->
                val taskBounds = calculatePeekBounds(task)
                wct.setBounds(task.token, taskBounds)
                task.taskId to Rect(task.configuration.windowConfiguration.bounds)
            }
        hideDesktopWallpaperActivityIfExists(wct)
        peekTransitionHandler.startTransition(wct) {
            ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: peek transition completed", TAG)
        }
    }

    /**
     * Initiates a transition to restore peeked tasks to their original positions.
     *
     * This method restores the bounds of all desktop tasks to their state before the peek was
     * initiated. If the controller is not currently in a peeked state, this method does nothing.
     */
    fun unpeek() {
        if (!isPeeking) {
            ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: Unable to unpeek. Not currently peeking.", TAG)
            return
        }
        val desktopTasks = getVisibleDesktopTasks()
        if (desktopTasks.isEmpty()) {
            ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: not unpeeking. No visible desktop tasks", TAG)
            return
        }
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: unpeek requested", TAG)
        val wct = WindowContainerTransaction()
        desktopTasks.forEach { task ->
            val restoreBounds = getRestoreBoundsForTask(task)
            if (restoreBounds == null || restoreBounds.isEmpty) {
                ProtoLog.w(
                    WM_SHELL_DESKTOP_MODE,
                    "%s: restoreBounds=%s for taskId %d, not setting bounds.",
                    TAG,
                    restoreBounds,
                    task.taskId,
                )
            } else {
                wct.setBounds(task.token, restoreBounds)
                wct.reorder(task.token, true)
            }
        }
        restoreDesktopWallpaperActivityIfExists(wct)
        peekTransitionHandler.startTransition(wct) {
            isPeeking = false
            taskBoundsBeforePeek = null
            ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: unpeek transition completed", TAG)
        }
    }

    private fun getRestoreBoundsForTask(task: ActivityManager.RunningTaskInfo): Rect? =
        taskBoundsBeforePeek?.get(task.taskId)
            ?: task.lastNonFullscreenBounds.also {
                ProtoLog.w(
                    WM_SHELL_DESKTOP_MODE,
                    "%s: appBoundsBeforePeek for task ID %d are null." +
                        "Falling back to lastNonFullscreenBounds",
                    TAG,
                    task.taskId,
                )
            }

    // Currently we only peek the default display, as Launcher is not present on other displays,
    // but define this as a function here for future expansion.
    fun getDisplayId() = DEFAULT_DISPLAY

    /**
     * Calculates the destination peek bounds for a task.
     *
     * The task is shifted off the screen based on its current position: if the task's center is
     * left of the screen's center, it shifts off the left edge. Otherwise, it shifts off the right
     * edge. A small, fixed size margin of the window remains visible in both cases.
     */
    private fun calculatePeekBounds(task: ActivityManager.RunningTaskInfo): Rect {
        val taskBounds = task.configuration.windowConfiguration.bounds
        val displayLayout =
            displayController.getDisplayLayout(getDisplayId())
                ?: DisplayLayout(context, context.display).also {
                    ProtoLog.e(
                        WM_SHELL_DESKTOP_MODE,
                        "%s: DisplayLayout for DisplayId %d is null.",
                        TAG,
                        getDisplayId(),
                    )
                }
        val screenWidth = displayLayout.width()
        val screenCenterX = screenWidth / 2
        val taskCenterX = taskBounds.centerX()
        val targetX =
            if (taskCenterX < screenCenterX) -taskBounds.width() + peekAmount
            else screenWidth - peekAmount
        return Rect(taskBounds).apply { offsetTo(targetX, taskBounds.top) }
    }

    private fun getVisibleDesktopTasks(): List<ActivityManager.RunningTaskInfo> =
        userRepositories.getProfile(shellController.currentUserId).let { desktopRepository ->
            desktopRepository
                .getActiveDeskId(getDisplayId())
                ?.let { activeDeskId ->
                    desktopRepository.getExpandedTasksIdsInDeskOrdered(activeDeskId).mapNotNull {
                        taskId ->
                        shellTaskOrganizer.getRunningTaskInfo(taskId)
                    }
                }
                .orEmpty()
        }

    /** Hides the DesktopWallpaperActivity. */
    private fun hideDesktopWallpaperActivityIfExists(wct: WindowContainerTransaction) {
        if (desktopWallpaperActivityUtils.hasDesktopWallpaperActivityEnabled(getDisplayId())) {
            val wallpaperActivityToken =
                desktopWallpaperActivityTokenProvider.getToken(getDisplayId())
            if (wallpaperActivityToken != null) {
                wct.setHidden(wallpaperActivityToken, true)
            }
        }
    }

    /** Restores the DesktopWallpaperActivity, hiding the home screen. */
    private fun restoreDesktopWallpaperActivityIfExists(wct: WindowContainerTransaction) {
        if (desktopWallpaperActivityUtils.hasDesktopWallpaperActivityEnabled(getDisplayId())) {
            val wallpaperActivityToken =
                desktopWallpaperActivityTokenProvider.getToken(getDisplayId())
            if (wallpaperActivityToken != null) {
                wct.setHidden(wallpaperActivityToken, false)
                wct.reorder(wallpaperActivityToken, true)
            }
        }
    }

    companion object {
        private const val TAG = "DesktopHomeScreenPeekController"
    }
}
