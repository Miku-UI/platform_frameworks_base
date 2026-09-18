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

package com.android.wm.shell.desktopmode.multidesks

import android.os.UserHandle
import android.os.UserManager
import android.view.Display.INVALID_DISPLAY
import android.window.DesktopExperienceFlags
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.data.DesktopRepositoryInitializer.DeskRootHelper
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellController
import kotlin.coroutines.suspendCoroutine

/** Encapsulate all the logic related to Desks. */
class DesksController(
    private val shellController: ShellController,
    private val userRepositories: DesktopUserRepositories,
    private val desktopConfig: DesktopConfig,
    private val desktopState: DesktopState,
    private val displayController: DisplayController,
    private val desksOrganizer: DesksOrganizer,
    private val shellTaskOrganizer: ShellTaskOrganizer,
) : DeskRootHelper {

    // Temporal reference back to the DesktopTasksController to allow an incremental moving of the
    // code. it'll be eventually removed.
    // TODO(b/467367552): Remove temporal dependency to DesktopTasksController.
    private lateinit var backDependency: DesktopTasksController

    fun setBackDependency(backDep: DesktopTasksController) {
        backDependency = backDep
    }

    override suspend fun recreateDeskRoot(
        userId: Int,
        destinationDisplayId: Int,
        deskId: Int,
    ): Int? = createDeskRootSuspending(displayId = destinationDisplayId, userId = userId)

    override suspend fun removeDeskRoots(requests: List<DeskRootHelper.DeskRootRemovalRequest>) {
        val wct = WindowContainerTransaction()
        for (request in requests) {
            desksOrganizer.removeDesk(wct, request.deskId, request.userId)
        }
        if (!wct.isEmpty()) {
            ProtoLog.d(
                WM_SHELL_DESKTOP_MODE,
                "$TAG: removeDeskRoots: applying removal of %s",
                requests,
            )
            shellTaskOrganizer.applyTransaction(wct)
        }
    }

    /** Returns whether the given display has an active desk. */
    @JvmOverloads
    fun isAnyDeskActive(displayId: Int, userId: Int = shellController.currentUserId): Boolean =
        userRepositories.getProfile(userId).isAnyDeskActive(displayId)

    /** Returns the id of the active desk in [displayId]. */
    @JvmOverloads
    fun getActiveDeskId(displayId: Int, userId: Int = shellController.currentUserId): Int? =
        userRepositories.getProfile(userId).getActiveDeskId(displayId)

    /** Returns whether the user can create new desks. */
    fun canCreateDesks(userId: Int = shellController.currentUserId): Boolean {
        val deskLimit = desktopConfig.maxDeskLimit
        val repository = userRepositories.getProfile(userId)
        return deskLimit == 0 || repository.getNumberOfDesks() < deskLimit
    }

    /**
     * Returns whether the user can create a new desk in the given display.
     *
     * @param enforceDeskLimit set to false to bypass the desk-limit verification.
     */
    fun canCreateDeskInDisplay(
        displayId: Int,
        userId: Int = shellController.currentUserId,
        enforceDeskLimit: Boolean = true,
    ): Boolean {
        if (!desktopState.isDesktopModeSupportedOnDisplay(displayId)) {
            return false
        }
        if (enforceDeskLimit && !canCreateDesks(userId)) {
            // At the limit, no-op.
            return false
        }
        return true
    }

    /**
     * Suspending version of [createDesk] that returns the deskId. Returns null if the desk could
     * not be created.
     */
    suspend fun createDeskSuspending(
        displayId: Int,
        userId: Int = shellController.currentUserId,
        enforceDeskLimit: Boolean = true,
        activateDesk: Boolean = false,
        enterReason: EnterReason = EnterReason.UNKNOWN_ENTER,
    ): Int? {
        logV(
            "createDeskSuspending displayId=%d, userId=%d enforceDeskLimit=%b",
            displayId,
            userId,
            enforceDeskLimit,
        )
        if (!canCreateDeskInDisplay(displayId, userId, enforceDeskLimit)) {
            logW("createDeskSuspending new desk cannot be created, ignoring request")
            return null
        }
        val repository = userRepositories.getProfile(userId)
        val deskId = createDeskRootSuspending(displayId, userId)
        if (deskId == null) {
            logW("Failed to add desk in displayId=%d for userId=%d", displayId, userId)
        } else {
            repository.addDesk(
                displayId = displayId,
                deskId = deskId,
                uniqueDisplayId = displayController.getDisplayUniqueId(displayId),
            )
            if (activateDesk) {
                backDependency.activateDesk(
                    deskId = deskId,
                    userId = userId,
                    enterReason = enterReason,
                )
            }
        }
        return deskId
    }

    /**
     * Adds a new desk to the given display for the given user and invokes [onResult] once the desk
     * is created, but not necessarily activated.
     */
    fun createDesk(
        displayId: Int,
        userId: Int = shellController.currentUserId,
        enforceDeskLimit: Boolean = true,
        activateDesk: Boolean = false,
        enterReason: EnterReason = EnterReason.UNKNOWN_ENTER,
        onResult: ((Int) -> Unit) = {},
    ) {
        logV(
            "createDesk displayId=%d, userId=%d enforceDeskLimit=%b",
            displayId,
            userId,
            enforceDeskLimit,
        )
        if (!canCreateDeskInDisplay(displayId, userId, enforceDeskLimit)) {
            logW("createDesk new desk cannot be created, ignoring request")
            return
        }
        val repository = userRepositories.getProfile(userId)
        createDeskRoot(displayId, userId) { deskId ->
            if (deskId == null) {
                logW("Failed to add desk in displayId=%d for userId=%d", displayId, userId)
            } else {
                repository.addDesk(
                    displayId = displayId,
                    deskId = deskId,
                    uniqueDisplayId = displayController.getDisplayUniqueId(displayId),
                )
                onResult(deskId)
                if (activateDesk) {
                    backDependency.activateDesk(
                        deskId = deskId,
                        userId = userId,
                        enterReason = enterReason,
                    )
                }
            }
        }
    }

    /**
     * Creates a desk root for the specified display and user, suspending the current coroutine
     * until the operation completes.
     *
     * This function acts as a suspending adapter for the callback-based [createDeskRoot] method. It
     * pauses execution until the callback is triggered and resumes with the result.
     *
     * @param displayId The ID of the display where the desk root should be created.
     * @param userId The ID of the user associated with the desk root.
     * @return The unique identifier (ID) of the created desk root, or `null` if the operation
     *   returned a null result.
     */
    // TODO(b/467367552): Consider implementing a DesksController wrapper for suspend enhancement.
    suspend fun createDeskRootSuspending(displayId: Int, userId: Int): Int? =
        suspendCoroutine { cont ->
            createDeskRoot(displayId, userId) { deskId -> cont.resumeWith(Result.success(deskId)) }
        }

    private fun createDeskRoot(displayId: Int, userId: Int, onResult: (Int?) -> Unit) {
        if (displayId == INVALID_DISPLAY) {
            logW("createDesk attempt with invalid displayId: %d", displayId)
            onResult(null)
            return
        }
        if (UserManager.isHeadlessSystemUserMode() && UserHandle.USER_SYSTEM == userId) {
            logW("createDesk ignoring attempt for system user")
            onResult(null)
            return
        }
        desksOrganizer.createDesk(displayId, userId) { deskId ->
            logD(
                "createDesk obtained deskId=%d for displayId=%d and userId=%d",
                deskId,
                displayId,
                userId,
            )
            onResult(deskId)
        }
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesksController"
    }
}
