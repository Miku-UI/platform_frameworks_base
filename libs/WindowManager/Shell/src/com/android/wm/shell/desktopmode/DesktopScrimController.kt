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

import android.app.ActivityManager.RunningTaskInfo
import android.app.IWallpaperManager
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Rect
import android.os.ServiceManager
import android.os.SystemProperties
import android.util.ArrayMap
import com.android.internal.annotations.VisibleForTesting
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopTasksController.SnapPosition
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopScrimListener
import com.android.wm.shell.sysui.OverviewVisibilityChangeListener
import com.android.wm.shell.sysui.ShellController
import java.util.concurrent.Executor

/** Handle enabling and disabling the desktop scrim effect. */
class DesktopScrimController(
    private val desktopRemoteListener: DesktopRemoteListener,
    private val desktopTasksController: DesktopTasksController,
    private val shellController: ShellController,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val keyguardManager: KeyguardManager,
    @ShellMainThread private val mainExecutor: ShellExecutor,
) : KeyguardManager.KeyguardLockedStateListener, OverviewVisibilityChangeListener {
    private val mDesktopScrimListeners = ArrayMap<DesktopScrimListener, Executor>()

    private val wallpaperService: IWallpaperManager =
        IWallpaperManager.Stub.asInterface(ServiceManager.getService(Context.WALLPAPER_SERVICE))

    private val wallpaperDimAmount: Float =
        SystemProperties.getInt("persist.wm.debug.wallpaper_dim_amount", 100).toFloat() / 100

    private val lastAppliedScrimState =
        mutableMapOf<Int, Boolean>() // displayId to applyLightOutEffect

    fun onInit() {
        if (Flags.updateDesktopScrimWhenLockedBugfix()) {
            keyguardManager.addKeyguardLockedStateListener(mainExecutor, this)
        }
        if (Flags.handleOverviewDesktopScrimBugfix()) {
            shellController.addOverviewVisibilityChangeListener(this)
        }
    }

    /**
     * Adds a listener to the desktop scrim effect changes.
     *
     * @param listener the listener to add.
     * @param callbackExecutor the executor to call the listener on.
     */
    fun addDesktopScrimListener(listener: DesktopScrimListener, callbackExecutor: Executor) {
        mDesktopScrimListeners[listener] = callbackExecutor
    }

    /**
     * Remove a listener to status bar changes.
     *
     * @param listener the listener to remove.
     */
    fun removeDesktopScrimListener(listener: DesktopScrimListener) {
        mDesktopScrimListeners.remove(listener)
    }

    /**
     * Check the current desktop condition and if should apply, apply the scrim effect.
     *
     * @param displayId the display ID.
     * @param userId the user ID.
     * @param excludeTaskId the task ID to exclude from consideration. May be null.
     * @param targetDeskId the desk ID for which the condition is checked. If null, the active desk
     *   of [displayId] will be used.
     * @param pendingTaskBounds the bounds of a task which might not be visible in the desk but
     *   needs to be considered, e.g., a newly launching task. May be null.
     */
    fun updateDesktopScrimIfNeeded(
        displayId: Int,
        userId: Int,
        excludeTaskId: Int? = null,
        targetDeskId: Int? = null,
        pendingTaskBounds: Rect? = null,
    ) {
        val applyLightOutEffect =
            if (Flags.fixWallpaperDimIssues26q2()) {
                desktopTasksController.isAnyTaskMaximizedOrDoubleTiled(
                    displayId,
                    userId,
                    excludeTaskId,
                    targetDeskId,
                    pendingTaskBounds,
                )
            } else {
                desktopTasksController.isAnyTaskMaximizedOrSnapped(displayId, userId, excludeTaskId)
            }
        updateDesktopScrim(displayId, applyLightOutEffect)
    }

    /**
     * Update the appearance when scrim effect is in place. When the opaque_status_bar flag is
     * enabled, the status bar will have an opaque background when the effect is enabled. Otherwise,
     * the task bar will have a rounded corner coverage and the wallpaper will dim.
     */
    @VisibleForTesting
    fun updateDesktopScrim(displayId: Int, applyLightOutEffect: Boolean) {
        if (lastAppliedScrimState[displayId] == applyLightOutEffect) {
            return
        }
        lastAppliedScrimState[displayId] = applyLightOutEffect
        if (com.android.systemui.Flags.opaqueStatusBar()) {
            mDesktopScrimListeners.forEach { (listener, executor) ->
                executor.execute {
                    listener.onDesktopScrimEffectChanged(displayId, applyLightOutEffect)
                }
            }
            return
        }
        desktopRemoteListener.onTaskbarCornerRoundingUpdate(applyLightOutEffect, displayId)
        wallpaperService.setWallpaperDimAmount(
            if (applyLightOutEffect) wallpaperDimAmount else 0f,
            displayId,
            true, /* temporary */
        )
    }

    override fun onKeyguardLockedStateChanged(isKeyguardLocked: Boolean) {
        rootTaskDisplayAreaOrganizer.displayIds.forEach { displayId ->
            if (isKeyguardLocked) {
                updateDesktopScrim(displayId, false)
            } else {
                updateDesktopScrimIfNeeded(displayId, shellController.currentUserId)
            }
        }
    }

    override fun onOverviewHidden(displayId: Int) {
        updateDesktopScrimIfNeeded(displayId, shellController.currentUserId)
    }

    override fun onOverviewShown(displayId: Int) {
        updateDesktopScrim(displayId, false)
    }

    /** Response to a task size toggle event, to update the scrim effect when needed. */
    fun handleToggleTaskSize(
        willMaximize: Boolean,
        taskInfo: RunningTaskInfo,
        shouldRestoreToSnap: Boolean,
    ) {
        val applyLightOutEffect =
            if (Flags.fixWallpaperDimIssues26q2()) {
                // The maximized / restored task will not be tiled. So just check if it will be
                // maximized, or any other task is already maximized or double-tiled.
                willMaximize ||
                    desktopTasksController.isAnyTaskMaximizedOrDoubleTiled(
                        taskInfo.displayId,
                        taskInfo.userId,
                        excludeTaskId = taskInfo.taskId,
                    )
            } else {
                willMaximize ||
                    shouldRestoreToSnap ||
                    desktopTasksController.isAnyTaskMaximizedOrSnapped(
                        displayId = taskInfo.displayId,
                        userId = taskInfo.userId,
                        excludeTaskId = taskInfo.taskId,
                    )
            }

        updateDesktopScrim(taskInfo.displayId, applyLightOutEffect)
    }

    /**
     * Update the rounding state of the taskbar on the given display, based on the task with ID
     * [taskId] having bounds [newBounds].
     */
    fun updateDesktopScrimOnResize(displayId: Int, taskId: Int, newBounds: Rect) {
        val applyLightOutEffect =
            if (Flags.fixWallpaperDimIssues26q2()) {
                // The resized task will not be tiled. So just check if it will be maximized, or any
                // other task is already maximized or double-tiled.
                desktopTasksController.isMaximizedToStableBoundsEdges(displayId, newBounds) ||
                    desktopTasksController.isAnyTaskMaximizedOrDoubleTiled(
                        displayId,
                        shellController.currentUserId,
                        excludeTaskId = taskId,
                    )
            } else {
                val otherTasksRequireTaskbarRounding =
                    desktopTasksController.isAnyTaskMaximizedOrSnapped(
                        displayId,
                        shellController.currentUserId,
                        excludeTaskId = taskId,
                    )
                val resizedTaskRequiresTaskbarRounding =
                    desktopTasksController.doesTaskMaximizedOrSnapped(displayId, newBounds)
                otherTasksRequireTaskbarRounding || resizedTaskRequiresTaskbarRounding
            }
        updateDesktopScrim(displayId, applyLightOutEffect)
    }

    fun handleOnSnapped(isTiled: Boolean, taskInfo: RunningTaskInfo, position: SnapPosition) {
        if (!Flags.fixWallpaperDimIssues26q2()) {
            if (isTiled) {
                updateDesktopScrim(taskInfo.displayId, true)
            }
            return
        }
        // snapController.snapToHalfScreen() returns false when the task's bounds are not
        // updated, even if the task is newly tiled. On the other hand, tiled tasks have
        // already been updated at this point. So, instead of checking the return value,
        // directly check if any task is tiled on `position` to see if the task has been
        // newly tiled and thus the wallpaper dimming effect might need to be updated.
        if (
            !desktopTasksController.isAnyTaskTiledAndVisible(
                taskInfo.displayId,
                taskInfo.userId,
                position,
            )
        ) {
            return
        }
        val oppositePosition =
            when (position) {
                SnapPosition.LEFT -> SnapPosition.RIGHT
                SnapPosition.RIGHT -> SnapPosition.LEFT
            }
        val isDoubleTiled =
            desktopTasksController.isAnyTaskTiledAndVisible(
                taskInfo.displayId,
                taskInfo.userId,
                oppositePosition,
                excludeTaskId = taskInfo.taskId,
            )
        if (isDoubleTiled) {
            updateDesktopScrim(taskInfo.displayId, true)
        } else {
            // We might need to update the effects when either:
            // 1. The task has been snapped from the maximized state.
            // 2. The task has been snapped from the opposite position.
            updateDesktopScrimIfNeeded(
                taskInfo.displayId,
                taskInfo.userId,
                excludeTaskId = taskInfo.taskId,
            )
        }
    }

    fun handleExitCleanUp(displayId: Int, shouldEndUpAtHome: Boolean, exitReason: ExitReason) {
        if (shouldEndUpAtHome && exitReason == ExitReason.RETURN_HOME_OR_OVERVIEW) {
            // We are going back to home, remove any effects for the maximized/snapped tasks.
            updateDesktopScrim(displayId, applyLightOutEffect = false)
        }
        if (
            Flags.updateDesktopScrimOnFullscreenLaunch() &&
                exitReason == ExitReason.FULLSCREEN_LAUNCH
        ) {
            // Exiting desktop mode because there is a fullscreen activity launched and taking the
            // entire display.
            updateDesktopScrim(displayId, applyLightOutEffect = false)
        }
    }
}
