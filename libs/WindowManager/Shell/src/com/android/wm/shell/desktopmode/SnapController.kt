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

import android.app.ActivityManager
import android.content.res.Configuration
import android.graphics.Rect
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler

/** Encapsulate the logic related to [SnapEventHandler]. */
class SnapController() : SnapEventHandler {

    private lateinit var snapEventHandler: SnapEventHandler

    /** Starts the snap events handler */
    fun start(handler: SnapEventHandler) {
        snapEventHandler = handler
    }

    override fun snapToHalfScreen(
        taskInfo: ActivityManager.RunningTaskInfo,
        currentDragBounds: Rect,
        position: DesktopTasksController.SnapPosition,
    ): Boolean =
        delegateIfRunning {
            snapEventHandler.snapToHalfScreen(taskInfo, currentDragBounds, position)
        } ?: false

    override fun snapPersistedTaskToHalfScreen(
        taskInfo: ActivityManager.RunningTaskInfo,
        currentDragBounds: Rect,
        position: DesktopTasksController.SnapPosition,
    ): Boolean =
        delegateIfRunning {
            snapEventHandler.snapPersistedTaskToHalfScreen(taskInfo, currentDragBounds, position)
        } ?: false

    override fun removeTaskIfTiled(displayId: Int, taskId: Int) {
        delegateIfRunning { snapEventHandler.removeTaskIfTiled(displayId, taskId) }
    }

    override fun onUserChange(userId: Int) {
        delegateIfRunning { snapEventHandler.onUserChange(userId) }
    }

    override fun onRecentsAnimationEndedToSameDesk() {
        delegateIfRunning { snapEventHandler.onRecentsAnimationEndedToSameDesk() }
    }

    override fun moveTaskToFrontIfTiled(taskInfo: ActivityManager.RunningTaskInfo): Boolean =
        delegateIfRunning { snapEventHandler.moveTaskToFrontIfTiled(taskInfo) } ?: false

    override fun getLeftSnapBoundsIfTiled(displayId: Int): Rect =
        delegateIfRunning { snapEventHandler.getLeftSnapBoundsIfTiled(displayId) } ?: UNUSED_RECT

    override fun getRightSnapBoundsIfTiled(displayId: Int): Rect =
        delegateIfRunning { snapEventHandler.getRightSnapBoundsIfTiled(displayId) } ?: UNUSED_RECT

    override fun onDeskDeactivated(deskId: Int) {
        delegateIfRunning { snapEventHandler.onDeskDeactivated(deskId) }
    }

    override fun onDisplayDisconnected(disconnectedDisplayId: Int) {
        delegateIfRunning { snapEventHandler.onDisplayDisconnected(disconnectedDisplayId) }
    }

    override fun onDeskActivated(deskId: Int, displayId: Int) {
        delegateIfRunning { snapEventHandler.onDeskActivated(deskId, displayId) }
    }

    override fun onDeskRemoved(deskId: Int) {
        delegateIfRunning { snapEventHandler.onDeskRemoved(deskId) }
    }

    override fun notifyTilingOfExplodedViewReorder(deskId: Int, topTaskId: Int) {
        delegateIfRunning { snapEventHandler.notifyTilingOfExplodedViewReorder(deskId, topTaskId) }
    }

    override fun getDividerBounds(deskId: Int): Rect =
        delegateIfRunning { snapEventHandler.getDividerBounds(deskId) } ?: UNUSED_RECT

    override fun onDisplayLayoutChange(
        displayId: Int,
        config: Configuration?,
        oldStableBounds: Rect,
        newToOldDpiRatio: Double,
    ) {
        delegateIfRunning {
            snapEventHandler.onDisplayLayoutChange(
                displayId,
                config,
                oldStableBounds,
                newToOldDpiRatio,
            )
        }
    }

    override fun onTaskLaunchStarted() {
        delegateIfRunning { snapEventHandler.onTaskLaunchStarted() }
    }

    override fun onDeskSwitchAnimationStarting(displayId: Int, fromDeskId: Int, toDeskId: Int) {
        delegateIfRunning {
            snapEventHandler.onDeskSwitchAnimationStarting(displayId, fromDeskId, toDeskId)
        }
    }

    override fun onDeskSwitchAnimationEnded(displayId: Int, deskId: Int) {
        delegateIfRunning { snapEventHandler.onDeskSwitchAnimationEnded(displayId, deskId) }
    }

    private fun <T> delegateIfRunning(fn: () -> T): T? =
        if (::snapEventHandler.isInitialized) {
            fn()
        } else {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "$TAG: SnapController is not running! SnapEventHandler missing!",
            )
            null
        }

    companion object {
        private const val TAG = "SnapController"

        @JvmStatic private val UNUSED_RECT = Rect()
    }
}
