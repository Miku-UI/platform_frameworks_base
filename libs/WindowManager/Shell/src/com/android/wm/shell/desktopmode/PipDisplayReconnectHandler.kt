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

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Rect
import android.util.ArrayMap
import android.window.DisplayAreaInfo
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener
import com.android.wm.shell.pip2.phone.PipDisplayTransferHandler
import com.android.wm.shell.pip2.phone.PipTransitionState
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.sysui.KeyguardChangeListener
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener

/**
 * Handles restoring pip tasks to external displays on reconnect for the current user by storing the
 * taskId - uniqueDisplayId pair on disconnect, then referring against those when the same
 * uniqueDisplayId display is connected. Events that can trigger a reconnect include adding a
 * display, changing a user, and unlocking the keyguard.
 *
 * TODO: b/488139835 Consider moving this out of desktop package.
 */
class PipDisplayReconnectHandler(
    private val keyguardManager: KeyguardManager,
    private val displayController: DisplayController,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val shellController: ShellController,
    private val pipState: PipTransitionState,
    private val pipDisplayTransferHandler: PipDisplayTransferHandler,
    shellInit: ShellInit,
) : KeyguardChangeListener, UserChangeListener, OnDisplaysChangedListener {
    // Mapping of display uniqueIds to displayId. Used to match a disconnected
    // displayId to its uniqueId since we will not be able to fetch it after disconnect.
    private val uniqueIdByDisplayId = mutableMapOf<Int, String>()

    private val preservedDisplaysByUser = ArrayMap<Int, PreservedDisplayRepository>()

    private val rootTaskDisplayAreaListener =
        object : RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener {
            override fun onDisplayAreaAppeared(displayAreaInfo: DisplayAreaInfo) {
                handlePotentialReconnect(
                    displayAreaInfo.displayId,
                    shellController.currentUserId,
                    "display area changed",
                )
            }
        }

    init {
        shellInit.addInitCallback({ onInit() }, this)
    }

    private fun onInit() {
        displayController.addDisplayWindowListener(this)
        shellController.addKeyguardChangeListener(this)
        shellController.addUserChangeListener(this)
    }

    /**
     * Store a pip task for a specific display and user on display disconnect; will be used to
     * restore a fullscreen task to a reconnected display with the same uniqueId.
     */
    fun preserveTask(taskId: Int, displayId: Int, userId: Int, pipBounds: Rect) {
        val uniqueDisplayId = uniqueIdByDisplayId[displayId] ?: return
        logV(
            "preserveTask: taskId=%d, uniqueDisplayId=%s, userId=%d",
            taskId,
            uniqueDisplayId,
            userId,
        )
        val preservedDisplayRepository =
            preservedDisplaysByUser.getOrPut(userId) { PreservedDisplayRepository() }
        preservedDisplayRepository.preservedDisplaysByUniqueId.getOrPut(uniqueDisplayId) {
            PreservedDisplay(
                uniqueDisplayId = uniqueDisplayId,
                pipTaskId = taskId,
                pipBounds = pipBounds,
            )
        }
    }

    private fun handlePotentialReconnect(displayId: Int, userId: Int, reason: String) {
        logV(
            "handlePotentialReconnect: displayId=%d, userId=%d, reason= %s",
            displayId,
            userId,
            reason,
        )
        if (!Flags.enableDisplayDisconnectPip()) {
            logV("handlePotentialReconnect: Reconnect not supported; aborting.")
            return
        }
        // Reconnect is handled on keyguard unlock, so don't proceed if locked.
        if (keyguardManager.isKeyguardLocked) {
            logV("handlePotentialReconnect: Keyguard is locked; aborting.")
            return
        }

        val uniqueDisplayId = displayController.getDisplay(displayId)?.uniqueId ?: return
        // To ensure one-time restoration, remove the preserved display data.
        val preservedDisplay =
            preservedDisplaysByUser[userId]?.preservedDisplaysByUniqueId?.remove(uniqueDisplayId)
        if (preservedDisplay == null) {
            logV(
                "handlePotentialReconnect: No preserved display found for " +
                    "uniqueDisplayId=%s; aborting.",
                uniqueDisplayId,
            )
            return
        }

        restoreDisplay(displayId = displayId, preservedDisplay = preservedDisplay)
    }

    private fun restoreDisplay(displayId: Int, preservedDisplay: PreservedDisplay) {
        logV("restoreDisplay: displayId=%d", displayId)
        val pipTask = pipState.pipTaskInfo ?: return
        if (pipTask.taskId == preservedDisplay.pipTaskId) {
            pipDisplayTransferHandler.scheduleMovePipToDisplay(
                /* originDisplayId= */ pipTask.displayId,
                /* targetDisplayId= */ displayId,
                /* boundsOnRelease= */ preservedDisplay.pipBounds,
            )
        }
    }

    override fun onKeyguardVisibilityChanged(
        visible: Boolean,
        occluded: Boolean,
        animatingDismiss: Boolean,
    ) {
        if (visible) return
        val displaysByUniqueId = displayController.allDisplaysByUniqueId ?: return
        for (displayIdByUniqueId in displaysByUniqueId) {
            handlePotentialReconnect(
                displayIdByUniqueId.value,
                shellController.currentUserId,
                "keyguard unlocked.",
            )
        }
    }

    override fun onUserChanged(newUserId: Int, userContext: Context) {
        val displayIds = displayController.allDisplaysByUniqueId?.values ?: return
        for (displayId in displayIds) {
            handlePotentialReconnect(displayId, newUserId, "user changed")
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        rootTaskDisplayAreaOrganizer.registerListener(displayId, rootTaskDisplayAreaListener)
        displayController.getDisplay(displayId)?.uniqueId?.let { uniqueId ->
            logV("onDisplayAdded: displayId=%d, uniqueId=%s", displayId, uniqueId)
            uniqueIdByDisplayId[displayId] = uniqueId
        }
    }

    override fun onDisplayRemoved(displayId: Int) {
        val uniqueId = uniqueIdByDisplayId.remove(displayId)
        logV("onDisplayRemoved: displayId=%d, uniqueId=%s", displayId, uniqueId)
    }

    private data class PreservedDisplay(
        val uniqueDisplayId: String,
        val pipTaskId: Int,
        var pipBounds: Rect,
    )

    private data class PreservedDisplayRepository(
        val preservedDisplaysByUniqueId: MutableMap<String, PreservedDisplay> = mutableMapOf()
    )

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE, "%s: $msg", TAG, *arguments)
    }

    private companion object {
        private const val TAG = "PipDisplayReconnectHandler"
    }
}
