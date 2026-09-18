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

import android.app.TaskInfo
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_MOVED_TO_TOP
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.multidesks.DesksController
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.annotations.ShellMainThreadImmediate
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import com.android.wm.shell.transition.Transitions
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Transition observer for recovering freeform tasks launching outside a desk. Tracks pending
 * freeform transitions and moves the associated task to a desk if required.
 */
class FreeformFallbackTransitionObserver(
    private val transitions: Transitions,
    @ShellMainThreadImmediate private val mainScopeImmediate: CoroutineScope,
    private val pinnedLayerController: Optional<PinnedLayerController>,
    private val desktopTasksControllerLazy: dagger.Lazy<Optional<DesktopTasksController>>,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desksOrganizer: DesksOrganizer,
    private val desksController: DesksController,
    private val desktopConfig: DesktopConfig,
    private val freeformFallbackTransitionHandler: Optional<FreeformFallbackTransitionHandler>,
) {

    /**
     * Called when any transition is ready, which may include transitions not tracked by this
     * observer.
     */
    fun onTransitionReady(info: TransitionInfo) {
        val deskTasksController = desktopTasksControllerLazy.get().orElse(null) ?: return
        val potentialOrphanFreeformChanges = info.potentialOrphanFreeformTasks()

        if (potentialOrphanFreeformChanges.isEmpty()) return

        val pendingMoveToDeskTasks: MutableList<TaskInfo> = mutableListOf()
        for (change in potentialOrphanFreeformChanges) {
            val taskInfo = change.taskInfo ?: continue
            val repository = desktopUserRepositories.getProfile(taskInfo.userId)
            // Ignore change if task is pinned or associated with a desk already.
            if (
                repository.isActiveTask(taskInfo.taskId) ||
                    pinnedLayerController.orElse(null)?.isPinned(taskInfo.taskId) ?: false
            ) {
                continue
            }
            pendingMoveToDeskTasks.add(taskInfo)
        }

        if (pendingMoveToDeskTasks.isEmpty()) return

        ProtoLog.wtf(
            ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
            "FreeformFallbackTransitionObserver: A freeform task(s)=%s launched outside a desk " +
                "and is not pinned, forcing them into desktop.",
            pendingMoveToDeskTasks.map { it.taskId },
        )

        transitions.runOnIdle {
            mainScopeImmediate.launch {
                val wct = WindowContainerTransaction()
                val runAllOnTransitStart: MutableSet<RunOnTransitStart> = mutableSetOf()
                val pendingDeskActivations: MutableSet<Int> = mutableSetOf()
                for (task in pendingMoveToDeskTasks) {
                    val repository = desktopUserRepositories.getProfile(task.userId)
                    // Create desk if one does not exit for display.
                    val targetDeskId: Int? =
                        repository.getDefaultDeskId(task.displayId)
                            ?: desksController.createDeskSuspending(
                                displayId = task.displayId,
                                userId = task.userId,
                                enforceDeskLimit = false,
                                enterReason = EnterReason.TASK_LAUNCH,
                            )
                    if (targetDeskId == null) {
                        ProtoLog.e(
                            ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
                            "FreeformFallbackTransitionObserver: No desk available, " +
                                "freeform task=%d not recovered.",
                            task.taskId,
                        )
                        continue
                    }

                    if (
                        !repository.isDeskActive(targetDeskId) &&
                            !pendingDeskActivations.contains(targetDeskId)
                    ) {
                        // Activate desk if not already active or pending activation.
                        deskTasksController
                            .addDeskActivationChanges(
                                deskId = targetDeskId,
                                wct = wct,
                                newTask = task,
                                displayId = task.displayId,
                                userId = task.userId,
                                enterReason = EnterReason.TASK_LAUNCH,
                            )
                            .let { result ->
                                pendingDeskActivations.add(targetDeskId)
                                runAllOnTransitStart.add(result)
                            }
                    }

                    desksOrganizer.moveTaskToDesk(wct, targetDeskId, task)

                    wct.reorder(task.token, /* onTop= */ true, /* includingParents= */ true)

                    if (desktopConfig.useDesktopOverrideDensity) {
                        wct.setDensityDpi(task.token, desktopConfig.desktopDensityOverride)
                    }
                }
                if (wct.isEmpty) return@launch
                val transition =
                    transitions.startTransition(
                        TRANSIT_CHANGE,
                        wct,
                        freeformFallbackTransitionHandler.orElse(null),
                    )
                runAllOnTransitStart.forEach { onTransitStart -> onTransitStart.invoke(transition) }
            }
        }
    }

    private fun TransitionInfo.potentialOrphanFreeformTasks(): List<TransitionInfo.Change> =
        changes.filter { c ->
            c.taskInfo?.takeIf { it.isFreeform } != null &&
                !desksOrganizer.isDeskChange(c) &&
                c.isOpeningOrToTop()
        }

    private fun TransitionInfo.Change.isToTop(): Boolean =
        (mode == TRANSIT_TO_FRONT) || hasFlags(FLAG_MOVED_TO_TOP)

    private fun TransitionInfo.Change.isOpeningOrToTop(): Boolean =
        TransitionUtil.isOpeningMode(mode) || isToTop()
}
