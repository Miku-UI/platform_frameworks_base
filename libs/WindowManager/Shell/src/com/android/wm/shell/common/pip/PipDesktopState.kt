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
package com.android.wm.shell.common.pip

import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.window.DesktopExperienceFlags
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.DragToDesktopTransitionHandler
import com.android.wm.shell.desktopmode.desktopfirst.isDisplayDesktopFirst
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.recents.RecentsTransitionHandler
import com.android.wm.shell.recents.RecentsTransitionStateListener
import com.android.wm.shell.recents.RecentsTransitionStateListener.RecentsTransitionState
import com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_NOT_RUNNING
import com.android.wm.shell.shared.pip.PipFlags
import java.util.Optional

/** Helper class for PiP on Desktop Mode. */
class PipDesktopState(
    private val pipDisplayLayoutState: PipDisplayLayoutState,
    recentsTransitionHandler: RecentsTransitionHandler,
    private val desktopUserRepositoriesOptional: Optional<DesktopUserRepositories>,
    private val dragToDesktopTransitionHandlerOptional: Optional<DragToDesktopTransitionHandler>,
    val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
) {
    @RecentsTransitionState private var recentsTransitionState = TRANSITION_STATE_NOT_RUNNING

    init {
        recentsTransitionHandler.addTransitionStateListener(
            object : RecentsTransitionStateListener {
                override fun onTransitionStateChanged(
                    @RecentsTransitionState state: Int,
                    displayId: Int,
                ) {
                    logD(
                        "Recents transition state changed: %s",
                        RecentsTransitionStateListener.stateToString(state),
                    )
                    recentsTransitionState = state
                }
            }
        )
    }

    /**
     * Returns whether PiP in Desktop Windowing is enabled by checking the following:
     * - DesktopUserRepositories is present
     * - DragToDesktopTransitionHandler is present
     * - PiP2 is enabled
     */
    fun isDesktopWindowingPipEnabled(): Boolean =
        desktopUserRepositoriesOptional.isPresent &&
            dragToDesktopTransitionHandlerOptional.isPresent &&
            PipFlags.isPip2ExperimentEnabled

    /**
     * Returns whether dragging PiP in Connected Displays is enabled by checking the following:
     * - PiP in Desktop Windowing is enabled
     */
    fun isDraggingPipAcrossDisplaysEnabled(): Boolean = isDesktopWindowingPipEnabled()

    /** Returns whether the display with the PiP task is in freeform windowing mode. */
    private fun isDisplayInFreeform(): Boolean {
        val tdaInfo =
            rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(pipDisplayLayoutState.displayId)

        return tdaInfo?.configuration?.windowConfiguration?.windowingMode == WINDOWING_MODE_FREEFORM
    }

    /** Returns whether PiP is active in a display that is in active Desktop Mode session. */
    fun isPipInDesktopMode(): Boolean {
        if (!isDesktopWindowingPipEnabled()) {
            return false
        }

        val displayId = pipDisplayLayoutState.displayId
        logD(
            "isPipInDesktopMode isAnyDeskActive=%b isDisplayDesktopFirst=%b",
            desktopUserRepositoriesOptional.get().current.isAnyDeskActive(displayId),
            isDisplayDesktopFirst(displayId),
        )
        return desktopUserRepositoriesOptional.get().current.isAnyDeskActive(displayId) ||
            isDisplayDesktopFirst(displayId)
    }

    /** Returns whether the display with the given id is a Desktop-first display. */
    fun isDisplayDesktopFirst(displayId: Int) =
        rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId)

    /**
     * Returns whether PiP is allowed to free-float (can be placed anywhere on the screen, but will
     * snap to edge if dragged past display bounds, and doesn't stash) by checking the following:
     * - ENABLE_DESKTOP_WINDOWING_FREE_FLOATING_PIP flag is enabled
     * - The display that PiP is active in is a Desktop-first display
     */
    fun isFreeFloatingPipEnabled(): Boolean {
        val displayId = pipDisplayLayoutState.displayId
        logD(
            "isFreeFloatingPipEnabled displayId=%d flag=%b isDisplayDesktopFirst=%b",
            displayId,
            Flags.enableDesktopWindowingFreeFloatingPip(),
            isDisplayDesktopFirst(displayId),
        )
        return Flags.enableDesktopWindowingFreeFloatingPip() && isDisplayDesktopFirst(displayId)
    }

    /** Returns whether Recents is in the middle of animating. */
    fun isRecentsAnimating(): Boolean =
        RecentsTransitionStateListener.isAnimating(recentsTransitionState)

    /** Returns the windowing mode to restore to when resizing out of PIP direction. */
    fun getOutPipWindowingMode(isMultiActivityChild: Boolean = false): Int {
        val isInDesktop = isPipInDesktopMode()
        // Temporary workaround for b/409201669: Always expand to fullscreen if we're exiting PiP
        // in the middle of Recents animation from Desktop session.
        if (isRecentsAnimating() && isInDesktop) {
            logD(
                "getOutPipWindowingMode forcing WINDOWING_MODE_FULLSCREEN due to Recents animating"
            )
            return WINDOWING_MODE_FULLSCREEN
        }

        // If we are resolving the windowing mode of a multi-activity PiP child and Desktop session
        // is not active, set it to FULLSCREEN explicitly so we can update the parent's windowing
        // mode if necessary
        if (isMultiActivityChild) {
            return if (isInDesktop) WINDOWING_MODE_UNDEFINED else WINDOWING_MODE_FULLSCREEN
        }

        // If we are exiting PiP while the device is in Desktop mode, the task should expand to
        // desktop mode. Set the windowing mode to UNDEFINED so it will resolve the windowing mode
        // to the root desk's windowing mode (which is always FREEFORM).
        logD(
            "getOutPipWindowingMode isInDesktop=%b isDisplayInFreeform=%b",
            isInDesktop,
            isDisplayInFreeform(),
        )
        if (isInDesktop) return WINDOWING_MODE_UNDEFINED

        // By default, or if the task is going to fullscreen, reset the windowing mode to undefined.
        return WINDOWING_MODE_UNDEFINED
    }

    /** Returns whether there is a drag-to-desktop transition in progress. */
    fun isDragToDesktopInProgress(): Boolean =
        isDesktopWindowingPipEnabled() && dragToDesktopTransitionHandlerOptional.get().inProgress

    /** Returns the DisplayLayout associated with the display where PiP window is in. */
    fun getCurrentDisplayLayout(): DisplayLayout = pipDisplayLayoutState.displayLayout

    /** Returns the id associated with the display where PiP window is in. */
    fun getCurrentDisplayId(): Int = pipDisplayLayoutState.displayId

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "PipDesktopState"
    }
}
