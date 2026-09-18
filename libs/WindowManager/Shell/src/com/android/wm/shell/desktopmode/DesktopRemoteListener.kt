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

import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SingleInstanceRemoteListener
import com.android.wm.shell.common.transition.TransitionStateHolder
import com.android.wm.shell.dagger.DynamicOverride
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.desktopmode.data.DesktopRepository.DeskChangeListener
import com.android.wm.shell.desktopmode.data.DesktopRepository.VisibleTasksListener
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellController
import javax.inject.Inject

/** Represents a remote-listener forwarding calls for [IDesktopTaskListener]. */
@WMSingleton
class DesktopRemoteListener
@Inject
constructor(
    @param:ShellMainThread private val mainExecutor: ShellExecutor,
    @param:DynamicOverride private val userRepositories: DesktopUserRepositories,
    private val shellController: ShellController,
    private val transitionStateHolder: TransitionStateHolder,
) {

    private var remoteListener:
        SingleInstanceRemoteListener<DesktopTasksController, IDesktopTaskListener>? =
        null

    /**
     * Registers the remote listener to receive desktop mode transition callbacks.
     *
     * @param remoteDesktopTaskListener The listener wrapper that handles the IPC communication.
     */
    fun register(
        remoteDesktopTaskListener:
            SingleInstanceRemoteListener<DesktopTasksController, IDesktopTaskListener>
    ) {
        remoteListener = remoteDesktopTaskListener
        if (!::deskChangeListener.isInitialized) deskChangeListener = createDeskChangeListener()
        with(userRepositories.current) {
            addDeskChangeListener(deskChangeListener, mainExecutor)
            addVisibleTasksListener(visibleTasksListener, mainExecutor)
        }
    }

    /** Unregisters the current remote listener and stops further callbacks. */
    fun unregister() {
        if (::deskChangeListener.isInitialized) {
            userRepositories.current.removeDeskChangeListener(deskChangeListener)
        } else {
            ProtoLog.v(
                WM_SHELL_DESKTOP_MODE,
                "$TAG: unregister() invoked with uninitialized deskChangeListener",
            )
        }
        userRepositories.current.removeVisibleTasksListener(visibleTasksListener)
        remoteListener = null
    }

    /**
     * Notifies the registered listener that a transition to enter desktop mode has started.
     *
     * @param transitionDuration The duration of the transition animation in milliseconds.
     */
    fun onEnterDesktopModeTransitionStarted(transitionDuration: Int) {
        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "$TAG: onEnterDesktopModeTransitionStarted transitionTime=%d remoteListener running=%b",
            transitionDuration,
            remoteListener != null,
        )
        remoteListener?.call { l -> l.onEnterDesktopModeTransitionStarted(transitionDuration) }
    }

    /**
     * Notifies the registered listener that a transition to exit desktop mode has started.
     *
     * @param transitionDuration The duration of the transition animation in milliseconds.
     * @param shouldEndUpAtHome {@code true} if the transition should finalize showing the
     *   Home/Launcher screen; {@code false} if it returns to a specific application task.
     */
    fun onExitDesktopModeTransitionStarted(transitionDuration: Int, shouldEndUpAtHome: Boolean) {
        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "$TAG: onExitDesktopModeTransitionStarted " +
                "transitionTime=%d shouldEndUpAtHome=%b  remoteListener running=%b",
            transitionDuration,
            shouldEndUpAtHome,
            remoteListener != null,
        )
        remoteListener?.call { l ->
            l.onExitDesktopModeTransitionStarted(transitionDuration, shouldEndUpAtHome)
        }
    }

    /**
     * [hasTasksRequiringTaskbarRounding] is true when a task is either maximized or snapped
     * left/right and rounded corners are enabled.
     */
    fun onTaskbarCornerRoundingUpdate(hasTasksRequiringTaskbarRounding: Boolean, displayId: Int) {
        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "$TAG: onTaskbarCornerRoundingUpdate doesAnyTaskRequireTaskbarRounding=%b, displayId=%d",
            hasTasksRequiringTaskbarRounding,
            displayId,
        )

        remoteListener?.call { l ->
            l.onTaskbarCornerRoundingUpdate(hasTasksRequiringTaskbarRounding, displayId)
        }
    }

    private lateinit var deskChangeListener: DeskChangeListener

    private fun createDeskChangeListener(): DeskChangeListener =
        object : DeskChangeListener {
            override fun onDeskAdded(displayId: Int, deskId: Int) {
                ProtoLog.v(
                    WM_SHELL_DESKTOP_MODE,
                    "$TAG: onDeskAdded display=%d deskId=%d",
                    displayId,
                    deskId,
                )
                remoteListener?.call { l -> l.onDeskAdded(displayId, deskId) }
            }

            override fun onDeskRemoved(displayId: Int, deskId: Int) {
                ProtoLog.v(
                    WM_SHELL_DESKTOP_MODE,
                    "$TAG: onDeskRemoved display=%d deskId=%d",
                    displayId,
                    deskId,
                )
                remoteListener?.call { l -> l.onDeskRemoved(displayId, deskId) }
            }

            override fun onActiveDeskChanged(
                displayId: Int,
                newActiveDeskId: Int,
                oldActiveDeskId: Int,
            ) {
                ProtoLog.v(
                    WM_SHELL_DESKTOP_MODE,
                    "$TAG: onActiveDeskChanged display=%d new=%d old=%d",
                    displayId,
                    newActiveDeskId,
                    oldActiveDeskId,
                )
                remoteListener?.call { l ->
                    l.onActiveDeskChanged(displayId, newActiveDeskId, oldActiveDeskId)
                }
            }

            override fun onCanCreateDesksChanged(canCreateDesks: Boolean) {
                ProtoLog.v(
                    WM_SHELL_DESKTOP_MODE,
                    "$TAG: onCanCreateDesksChanged canCreateDesks=%b",
                    canCreateDesks,
                )
                remoteListener?.call { l -> l.onCanCreateDesksChanged(canCreateDesks) }
            }

            override fun onTaskAppearingInDesk(taskId: Int, displayId: Int, deskId: Int) {
                if (shellController.isOverviewVisible(displayId) != true) return
                if (transitionStateHolder.isRecentsTransitionRunning() != false) return
                ProtoLog.v(
                    WM_SHELL_DESKTOP_MODE,
                    "$TAG: onTaskAppearingInDesk taskId=%d displayId=%d deskId=%d",
                    taskId,
                    displayId,
                    deskId,
                )
                remoteListener?.call { l ->
                    l.onTaskAppearingInDeskWithOverviewShowing(taskId, displayId, deskId)
                }
            }
        }

    private val visibleTasksListener: VisibleTasksListener =
        object : VisibleTasksListener {
            override fun onTasksVisibilityChanged(displayId: Int, visibleTasksCount: Int) {
                ProtoLog.v(
                    WM_SHELL_DESKTOP_MODE,
                    "$TAG: onVisibilityChanged display=%d visible=%d",
                    displayId,
                    visibleTasksCount,
                )
                remoteListener?.call { l ->
                    l.onTasksVisibilityChanged(displayId, visibleTasksCount)
                }
            }
        }

    private companion object {
        private val TAG = "DesktopRemoteListener"
    }
}
