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

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.graphics.Rect
import android.os.IBinder
import android.os.Trace
import android.view.Display.INVALID_DISPLAY
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_MOVED_TO_TOP
import android.window.WindowContainerTransaction
import com.android.app.tracing.traceSection
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopModeEventLogger
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.desktopmode.homescreenpeeking.DesktopHomeScreenPeekController
import com.android.wm.shell.keyguard.KeyguardTransitionHandler.isKeyguardAppearing
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_END_RECENTS_TRANSITION
import com.android.wm.shell.transition.Transitions.TRANSIT_START_RECENTS_TRANSITION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Observer of desk-related transitions, such as adding, removing or activating a whole desk. It
 * tracks pending transitions and updates repository state once they finish.
 */
class DesksTransitionObserver(
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desksOrganizer: DesksOrganizer,
    private val transitions: Transitions,
    private val desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
    @ShellMainThread private val mainScope: CoroutineScope,
    private val desktopModeEventLogger: DesktopModeEventLogger,
    private val shellController: ShellController,
    private val displayController: DisplayController,
    private val desktopHomeScreenPeekController: DesktopHomeScreenPeekController,
) {
    // Tracks the desk transitions used to keep track of the desk state. This is usually removed
    // when the transition is ready. This map represents what a single shell transition is causing
    // in terms of the desks state.
    private val deskTransitions = mutableMapOf<IBinder, MutableSet<DeskTransition>>()
    // Tracks the desk transitions that are ongoing. This won't be removed until transition is
    // finished.
    private val runningDesksTransitions = mutableMapOf<IBinder, MutableSet<DeskTransition>>()

    /** Adds a pending desk transition to be tracked. */
    fun addPendingTransition(transition: DeskTransition) {
        val transitions = deskTransitions[transition.token] ?: mutableSetOf()
        transitions += transition
        deskTransitions[transition.token] = transitions
        logD("Added pending desk transition: %s", transition)
    }

    /**
     * Called when any transition is ready, which may include transitions not tracked by this
     * observer.
     */
    fun onTransitionReady(transition: IBinder, info: TransitionInfo) =
        traceSection(
            traceTag = Trace.TRACE_TAG_WINDOW_MANAGER,
            name = "DesksTransitionObserver#onTransitionReady",
        ) {
            val readyDeskTransitions = deskTransitions.remove(transition)
            readyDeskTransitions?.forEach { readyDeskTransition ->
                handleDeskTransition(info, readyDeskTransition)
            }
            if (readyDeskTransitions.isNullOrEmpty()) {
                // A desk transition could also occur without shell having started it or
                // intercepting it, check for that here in case launch roots need to be updated.
                handleIndependentDeskTransitionIfNeeded(info)
            } else {
                runningDesksTransitions[transition] = readyDeskTransitions
            }
        }

    /**
     * Called when a transition is merged with another transition, which may include transitions not
     * tracked by this observer.
     */
    fun onTransitionMerged(merged: IBinder, playing: IBinder) {
        deskTransitions.remove(merged)?.let { transitions ->
            val existingTransitions = deskTransitions[playing] ?: mutableSetOf()
            existingTransitions.addAll(
                transitions.map { deskTransition -> deskTransition.copyWithToken(token = playing) }
            )
            deskTransitions[playing] = existingTransitions
        }
        runningDesksTransitions.remove(merged)?.let { transitions ->
            val existingTransitions = runningDesksTransitions[playing] ?: mutableSetOf()
            existingTransitions.addAll(
                transitions.map { deskTransition -> deskTransition.copyWithToken(token = playing) }
            )
            runningDesksTransitions[playing] = existingTransitions
        }
    }

    /**
     * Called when any transition finishes, which may include transitions not tracked by this
     * observer.
     *
     * Most [DeskTransition]s are not handled here because [onTransitionReady] handles them and
     * removes them from the map. However, there can be cases where the transition was added after
     * [onTransitionReady] had already been called and they need to be handled here, such as the
     * swipe-to-home recents transition when there is no book-end transition.
     */
    fun onTransitionFinished(transition: IBinder) {
        runningDesksTransitions.remove(transition)
        deskTransitions.remove(transition)?.let { finishedDeskTransitions ->
            finishedDeskTransitions.forEach { deskTransition ->
                if (deskTransition is DeskTransition.DeactivateDesk) {
                    handleDeactivateDeskTransition(null, deskTransition)
                } else {
                    logW(
                        "Unexpected desk transition finished without being handled: %s",
                        deskTransition,
                    )
                }
            }
        }
    }

    private fun handleDeskTransition(info: TransitionInfo, deskTransition: DeskTransition) {
        logD("Desk transition ready: %s", deskTransition)
        val repository = desktopUserRepositories.getProfile(deskTransition.userId)
        when (deskTransition) {
            is DeskTransition.RemoveDesk -> {
                // TODO: b/362720497 - consider verifying the desk was actually removed through the
                //  DesksOrganizer. The transition info won't have changes if the desk was not
                //  visible, such as when dismissing from Overview.
                val deskId = deskTransition.deskId
                val displayId = deskTransition.displayId
                deskTransition.runOnTransitEnd?.invoke()
                if (repository.isDeskActive(deskTransition.deskId)) {
                    desktopModeEventLogger.logPendingSessionExit(deskId, deskTransition.exitReason)
                }
                repository.removeDesk(deskTransition.deskId)
                val removedOnlyDeskInDisplay = repository.getNumberOfDesks(displayId) == 0
                deskTransition.onDeskRemovedListener?.onDeskRemoved(
                    displayId,
                    deskId,
                    deskTransition.userId,
                    removedOnlyDeskInDisplay,
                )
            }
            is DeskTransition.ActivateDesk -> {
                val activateDeskChange =
                    info.changes.find { change ->
                        desksOrganizer.isDeskActiveAtEnd(change, deskTransition.deskId)
                    }
                if (activateDeskChange == null) {
                    // Always activate even if there is no change in the transition for the
                    // activated desk. This is necessary because some activation requests, such as
                    // those involving empty desks, may not contain visibility changes that are
                    // reported in the transition change list.
                    logD("Activating desk without transition change")
                }
                repository.setActiveDesk(
                    displayId = deskTransition.displayId,
                    deskId = deskTransition.deskId,
                )
                desktopModeEventLogger.logSessionEnter(
                    deskTransition.deskId,
                    deskTransition.enterReason,
                )
                deskTransition.runOnTransitEnd?.invoke()
            }
            is DeskTransition.ActivateDeskWithTask -> {
                val deskId = deskTransition.deskId
                val deskChange =
                    info.changes.find { change -> desksOrganizer.isDeskChange(change, deskId) }
                if (deskChange != null) {
                    val deskChangeDisplayId = deskChange.taskInfo?.displayId ?: INVALID_DISPLAY
                    if (deskChangeDisplayId != deskTransition.displayId) {
                        logW(
                            "ActivateDeskWithTask: expected displayId=%d but got displayId=%d",
                            deskTransition.displayId,
                            deskChangeDisplayId,
                        )
                    }
                    repository.setActiveDesk(
                        displayId = deskTransition.displayId,
                        deskId = deskTransition.deskId,
                    )
                    desktopModeEventLogger.logSessionEnter(
                        deskTransition.deskId,
                        deskTransition.enterReason,
                    )
                } else {
                    logW("ActivateDeskWithTask: did not find desk change")
                }
                val taskChange =
                    info.changes.find { change ->
                        change.taskInfo?.taskId == deskTransition.enterTaskId &&
                            change.taskInfo?.isVisibleRequested == true &&
                            desksOrganizer.getDeskAtEnd(change) == deskTransition.deskId
                    }
                if (taskChange != null) {
                    repository.addTaskToDesk(
                        displayId = deskTransition.displayId,
                        deskId = deskTransition.deskId,
                        taskId = deskTransition.enterTaskId,
                        isVisible = true,
                        taskBounds = taskChange.taskInfo?.configuration?.windowConfiguration?.bounds,
                    )
                } else {
                    // This is possible in cases where the task that was originally launched is a
                    // trampoline and a new task ends up being the one that appeared. It's ok as
                    // desktop task updates to the repository are handled by
                    // [DesktopTaskChangeListener].
                    logW("ActivateDeskWithTask: did not find task change")
                }
                deskTransition.runOnTransitEnd?.invoke()
            }
            is DeskTransition.DeactivateDesk -> handleDeactivateDeskTransition(info, deskTransition)
            is DeskTransition.ChangeDeskDisplay -> handleChangeDeskDisplay(deskTransition)
            is DeskTransition.RemoveDisplay -> handleRemoveDisplay(deskTransition)
            is DeskTransition.AddTaskToDesk -> handleAddTaskToDesk(deskTransition)
        }
    }

    private fun handleDeactivateDeskTransition(
        info: TransitionInfo?,
        deskTransition: DeskTransition.DeactivateDesk,
    ) {
        logD("handleDeactivateDeskTransition: %s", deskTransition)
        val desktopRepository = desktopUserRepositories.getProfile(deskTransition.userId)
        var deskChangeFound = false

        deskTransition.runOnTransitEnd?.invoke()
        val changes = info?.changes ?: emptyList()
        for (change in changes) {
            val isDeskChange = desksOrganizer.isDeskChange(change, deskTransition.deskId)
            if (isDeskChange) {
                deskChangeFound = true
                continue
            }
        }
        // Always deactivate even if there's no change that confirms the desk was
        // deactivated. Some interactions, such as the desk deactivating because it's
        // occluded by a fullscreen task result in a transition change, but others, such
        // as transitioning from an empty desk to home may not.
        if (!deskChangeFound) {
            logD("Deactivating desk without transition change")
        }
        if (deskTransition.switchingUser) {
            logD("Skipping repository deactivation because this is a user-switch")
            return
        }
        desktopRepository.setDeskInactive(deskId = deskTransition.deskId)
        desktopModeEventLogger.logPendingSessionExit(
            deskTransition.deskId,
            deskTransition.exitReason,
        )
    }

    private fun handleChangeDeskDisplay(deskTransition: DeskTransition.ChangeDeskDisplay) {
        logD("handleChangeDeskDisplay: %s", deskTransition)
        val deskId = deskTransition.deskId
        desktopUserRepositories.getRepositoriesWithDeskId(deskId).forEach { desktopRepository ->
            desktopRepository.onDeskDisplayChanged(
                deskId,
                deskTransition.displayId,
                deskTransition.uniqueDisplayId,
            )
        }
    }

    private fun handleRemoveDisplay(deskTransition: DeskTransition.RemoveDisplay) {
        logD("handleRemoveDisplay: %s", deskTransition)
        desktopUserRepositories.forAllRepositories { desktopRepository ->
            desktopRepository.removeDisplay(deskTransition.displayId)
        }
    }

    private fun handleAddTaskToDesk(deskTransition: DeskTransition.AddTaskToDesk) {
        logD("handleAddTaskToDesk: %s", deskTransition)
        val taskRepository = desktopUserRepositories.getProfile(deskTransition.userId)
        taskRepository.addTaskToDesk(
            deskTransition.displayId,
            deskTransition.deskId,
            deskTransition.taskId,
            !deskTransition.minimized,
            deskTransition.taskBounds,
        )
        if (deskTransition.minimized) {
            taskRepository.minimizeTaskInDesk(
                deskTransition.displayId,
                deskTransition.deskId,
                deskTransition.taskId,
            )
        }
    }

    private fun handleIndependentDeskTransitionIfNeeded(info: TransitionInfo) {
        val deskChanges = info.deskChanges()
        val desktopWallpaperChanges = info.desktopWallpaperChanges()
        val fullImmersiveDesktopTaskChanges = info.desktopTaskInFullImmersiveChanges()
        if (deskChanges.isEmpty() && desktopWallpaperChanges.isEmpty()) return
        logD(
            "handleIndependentDeskTransitionIfNeeded %d desk related change(s) found with " +
                "%d desktop wallpaper change(s) and %d full-immersive task change(s)",
            deskChanges.size,
            desktopWallpaperChanges.size,
            fullImmersiveDesktopTaskChanges.size,
        )
        if (info.isRecentsType()) {
            // Recents related changes are transient, so desk activation state should not change.
            logD("Desk changes in a recents transition, ignoring")
            return
        }
        val wct = WindowContainerTransaction()
        var hasSeenDesk = false

        var hasSeenOpeningTask = false
        val desksToActivate = mutableListOf<Int>()
        // Visit all task changes, not just desk/wallpaper changes because we're interested in
        // capturing user ids of showing tasks (such as Home) to detect user switches.
        for (change in info.taskChanges()) {
            val taskInfo = checkNotNull(change.taskInfo) { "Expected non-null task info" }
            val taskId = taskInfo.taskId
            logD("Handle change for taskId=%d:", taskId)
            if (change in deskChanges) {
                hasSeenDesk = true
            } else if (change.isOpeningOrToTop()) {
                hasSeenOpeningTask = true
            }
            if (change !in deskChanges && change !in desktopWallpaperChanges) {
                logD("Not desk or wallpaper, skipping")
                continue
            }
            // The [change] userId can't be used here because the type of tasks being handled
            // won't actually report the userId they're being used for right now (the current user)
            // - DesktopWallpaperActivity is |showForAllUsers|, so its |userId| is that of the user
            // that created it.
            // - Desk root tasks are shared across users, so its |userId| is also that of the user
            // that created it.
            // Use the current user id instead.
            val userId = shellController.currentUserId
            if (change in desktopWallpaperChanges) {
                logD("Desktop wallpaper change, userId=%d", userId)
                if (hasSeenDesk) {
                    logD("Saw desk change before desktop wallpaper change, skipping")
                    continue
                }
                when {
                    change.isToBack() -> {
                        // The desktop wallpaper is moving to back without seeing a desk first.
                        val repository = desktopUserRepositories.getProfile(userId)
                        val activeDeskId = repository.getActiveDeskId(change.endDisplayId)
                        if (activeDeskId != null) {
                            val hasFullImmersiveTask =
                                fullImmersiveDesktopTaskChanges.any { c ->
                                    desksOrganizer.getDeskAtEnd(c) == activeDeskId
                                }

                            // Even though the desk/wallpaper was moved to the back, we'd like it
                            // to remain active sometimes.
                            val deactivateDeskInRepo =
                                when {
                                    // (1) When keyguard is appearing, so that we return to the
                                    // active desk when it unlocks.
                                    isKeyguardAppearing(info) -> false
                                    // (2) When a task is going full-immersive, this task is
                                    // considered "in" the desk, it just happens to occlude/stop
                                    // everything behind it.
                                    hasFullImmersiveTask -> false
                                    // When peeking the home screen, do not leave the active desk.
                                    desktopHomeScreenPeekController.isPeeking -> false
                                    // Otherwise deactivate.
                                    else -> true
                                }
                            // Usually the we make the organizer deactivate the desk when we're
                            // deactivating it in the repository, but there are some cases where we
                            // want to make the organizer deactivate it (move to back and disable
                            // the launch root) despite wanting it to keep it active from the
                            // repository pov.
                            val deactivateDeskInOrganizer =
                                when {
                                    // Always disable on deactivation.
                                    deactivateDeskInRepo -> true
                                    // Disable if keyguard is appearing, the desk remains active so
                                    // that we can return to it, but we also don't want things
                                    // to launch in it while the keyguard is showing.
                                    isKeyguardAppearing(info) -> true
                                    else -> false
                                }

                            logD(
                                "Desktop wallpaper of user=%d moved to back without a visible " +
                                    "desk (hasFullImmersiveTask=%b isKeyguardAppearing=%b) - " +
                                    "deactivateDeskInRepo=%b deactivateDeskInOrganizer=%b",
                                userId,
                                hasFullImmersiveTask,
                                isKeyguardAppearing(info),
                                deactivateDeskInRepo,
                                deactivateDeskInOrganizer,
                            )

                            if (deactivateDeskInOrganizer) {
                                desksOrganizer.deactivateDesk(wct, activeDeskId, skipReorder = true)
                            }
                            if (deactivateDeskInRepo) {
                                desktopModeEventLogger.logPendingSessionExit(
                                    activeDeskId,
                                    ExitReason.UNKNOWN_EXIT,
                                )
                                repository.setDeskInactive(activeDeskId)
                            }
                        }
                    }
                    change.isToTop() -> {
                        // It is possible that the desktop wallpaper ends up on top of the desk.
                        // For example: Minimizing the last task to end in an empty desk.
                        // This is valid state, but since we don't expect desks to activate
                        // without user action, check that the desk was supposed to be active, and
                        // if not, deactivate it.
                        val repository = desktopUserRepositories.getProfile(userId)
                        val activeDeskId = repository.getActiveDeskId(change.endDisplayId)
                        logD(
                            "Found desktop wallpaper without a desk in front for userId=%d " +
                                "activeDeskId=%d",
                            userId,
                            activeDeskId,
                        )
                        if (activeDeskId != null) {
                            logD("Reactivating desk=%d", activeDeskId)
                            desksToActivate.add(activeDeskId)
                            desksOrganizer.activateDesk(wct, activeDeskId, skipReorder = false)
                            desktopModeEventLogger.logSessionEnter(
                                activeDeskId,
                                EnterReason.UNKNOWN_ENTER,
                            )
                        } else {
                            logD("Dismissing desktop wallpaper")
                            val container =
                                checkNotNull(change.container) { "Expected non-null container" }
                            wct.reorder(container, /* onTop= */ false)
                        }
                    }
                    else -> {
                        logW(
                            "Unexpected change for desktop wallpaper with mode=%s",
                            TransitionInfo.modeToString(change.mode),
                        )
                    }
                }
                continue
            }

            val deskId = desksOrganizer.getDeskIdFromChange(change)
            if (deskId == null) {
                logW("No desk found in change")
                continue
            }
            val displayId = change.endDisplayId
            logD(
                "Handle desk change for desk=%d in display=%d for userId=%d",
                deskId,
                displayId,
                userId,
            )
            when {
                change.isToBack() -> {
                    if (
                        !hasSeenOpeningTask &&
                            DesktopExperienceFlags.SKIP_DEACTIVATION_OF_DESK_WITH_NOTHING_IN_FRONT
                                .isTrue
                    ) {
                        // In cases such as minimizing the last app, where we want to remain
                        // in an empty desk, WM core may report BOTH the minimizing task and its
                        // (former) parent root as TO_BACK changes. However, the root will still
                        // remain in front (as it should), so do not interpret TO_BACK as it having
                        // to be deactivated unless something else actually showed up in front.
                        logD("desk=%d moved to back but nothing moved to front, skipping", deskId)
                        continue
                    }
                    if (desksToActivate.contains(deskId)) {
                        // In cases such as back-navigating the last app, where we want to remain
                        // in an empty desk, WM core may forcefully move the desktop wallpaper to
                        // front and the desk root to back because the next focusable activity is
                        // the desktop wallpaper. This is another case of desktop wallpaper over
                        // desk that's already handled above so the desk is already scheduled to be
                        // reactivated. Just skip the deactivation here to avoid cancelling it out.
                        logD("desk=%d moved to back but is scheduled to activate, skipping", deskId)
                        continue
                    }
                    val repository = desktopUserRepositories.getProfile(userId)
                    logD(
                        "desk=%d of user=%d moved to back, will let the organizer deactivate and " +
                            "the repository will deactivate",
                        deskId,
                        userId,
                    )
                    // Always let the organizer deactivate to clear the launch root.
                    desksOrganizer.deactivateDesk(wct, deskId, skipReorder = true)
                    desktopModeEventLogger.logPendingSessionExit(deskId, ExitReason.UNKNOWN_EXIT)
                    // The desk was independently deactivated (such as when Home is brought
                    // to front during CTS), make sure the repository state reflects that too.
                    repository.setDeskInactive(deskId)
                }
                change.isToTop() -> {
                    // Do not handle independent desk activations when a desk is pending a move to
                    // this display. The activation will be handled when that transition is
                    // processed.
                    if (
                        deskTransitions.values.any { transitionsForBinder ->
                            transitionsForBinder.any { transition ->
                                transition is DeskTransition.ChangeDeskDisplay &&
                                    transition.displayId == displayId
                            }
                        }
                    ) {
                        logD("Pending display change found; skipping.")
                        continue
                    }
                    val repository = desktopUserRepositories.getProfile(userId)
                    logD(
                        "desk=%d of user=%d moved to front, " +
                            "will let the organizer and repository activate",
                        deskId,
                        userId,
                    )
                    desksOrganizer.activateDesk(wct, deskId, skipReorder = true)
                    repository.setActiveDesk(displayId, deskId)
                    desktopModeEventLogger.logSessionEnter(deskId, EnterReason.UNKNOWN_ENTER)
                }
                else -> {
                    logW(
                        "Unexpected change for desk=%d with mode=%s",
                        deskId,
                        TransitionInfo.modeToString(change.mode),
                    )
                }
            }
        }
        if (wct.isEmpty) {
            logD("No changes, ignoring")
            return
        }
        logD("handleIndependentDeskTransitionIfNeeded starting transition")
        mainScope.launch { transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null) }
    }

    /**
     * Given a [transition] finds whether it's a desk to desk transition in the same display.
     *
     * A user switch where one users desk deactivates and another user's desk activates does not
     * count as a desk to desk transition.
     *
     * @return A [DeskToDeskTransition] if a valid desk switch is found. Otherwise, returns null.
     */
    fun findDeskToDeskTransition(transition: IBinder): DeskToDeskTransition? {
        val running = runningDesksTransitions[transition] ?: return null

        // Check if it's a user switch where same transition activating one user and deactivating
        // another.
        if (running.map { it.userId }.distinct().size > 1) return null
        val potentialDeskSwitchTransitionsByDisplayId =
            running
                .filter { transition ->
                    transition is DeskTransition.ActivateDesk ||
                        transition is DeskTransition.ActivateDeskWithTask ||
                        transition is DeskTransition.DeactivateDesk
                }
                .groupBy(
                    { transition ->
                        when (transition) {
                            is DeskTransition.ActivateDesk -> transition.displayId
                            is DeskTransition.ActivateDeskWithTask -> transition.displayId
                            is DeskTransition.DeactivateDesk -> transition.displayId
                            else -> error("Unexpected transition type: $transition")
                        }
                    },
                    { it },
                )
        for ((displayId, transitions) in potentialDeskSwitchTransitionsByDisplayId.entries) {
            val fromDesk =
                transitions.firstOrNull { it is DeskTransition.DeactivateDesk }
                    as? DeskTransition.DeactivateDesk ?: continue
            val toDesk =
                transitions.firstOrNull {
                    it is DeskTransition.ActivateDeskWithTask || it is DeskTransition.ActivateDesk
                } ?: continue
            val fromDeskId = fromDesk.deskId
            val toDeskId =
                when (toDesk) {
                    is DeskTransition.ActivateDesk -> toDesk.deskId
                    is DeskTransition.ActivateDeskWithTask -> toDesk.deskId
                    else -> error("Unexpected transition type: $toDesk")
                }
            if (fromDeskId == toDeskId) continue
            return DeskToDeskTransition(displayId, fromDesk.userId, fromDeskId, toDeskId)
        }
        return null
    }

    /**
     * Represents a transition between two different desks. [fromDeskId] is never the same as the
     * [toDeskId].
     */
    data class DeskToDeskTransition(
        val displayId: Int,
        val userId: Int,
        val fromDeskId: Int,
        val toDeskId: Int,
    )

    private fun TransitionInfo.isRecentsType() =
        type == TRANSIT_START_RECENTS_TRANSITION || type == TRANSIT_END_RECENTS_TRANSITION

    private fun TransitionInfo.Change.isToTop(): Boolean =
        (mode == TRANSIT_TO_FRONT) || hasFlags(FLAG_MOVED_TO_TOP)

    private fun TransitionInfo.Change.isToBack(): Boolean = mode == TRANSIT_TO_BACK

    private fun TransitionInfo.Change.isOpeningOrToTop(): Boolean =
        TransitionUtil.isOpeningMode(mode) || isToTop()

    private fun TransitionInfo.taskChanges(): List<TransitionInfo.Change> =
        changes.filter { c ->
            val taskId = c.taskInfo?.taskId
            taskId != null && taskId != INVALID_TASK_ID
        }

    private fun TransitionInfo.desktopTaskInFullImmersiveChanges(): List<TransitionInfo.Change> {
        return changes
            .filter { c -> c.taskInfo != null }
            .filter { c -> desksOrganizer.getDeskAtEnd(c) != null }
            .filter { c -> c.mode == TRANSIT_CHANGE }
            .filter { c ->
                val displayBounds =
                    displayController.getDisplayLayout(c.endDisplayId)?.let {
                        Rect(0, 0, it.width(), it.height())
                    }
                return@filter displayBounds != null && c.endAbsBounds == displayBounds
            }
    }

    private fun TransitionInfo.deskChanges(): List<TransitionInfo.Change> =
        changes.filter { c -> desksOrganizer.isDeskChange(c) }

    private fun TransitionInfo.desktopWallpaperChanges(): List<TransitionInfo.Change> =
        changes.filter { c ->
            val token = desktopWallpaperActivityTokenProvider.getToken(c.endDisplayId)
            token != null && c.container == token
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

    private companion object {
        private const val TAG = "DesksTransitionObserver"
    }
}
