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

package com.android.wm.shell.desktopmode

import android.app.ActivityTaskManager
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.os.Binder
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.view.Display
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER
import androidx.compose.ui.input.key.type
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags

import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.pip.PipDesktopState
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFullscreenTask
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import android.testing.AndroidTestingRunner

/**
 * Tests for [DesktopPipTransitionController].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopPipTransitionControllerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopPipTransitionControllerTest : ShellTestCase() {
    private val mockShellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val mockDesktopTasksController = mock<DesktopTasksController>()
    private val mockDesktopUserRepositories = mock<DesktopUserRepositories>()
    private val mockDesktopRepository = mock<DesktopRepository>()
    private val mockPipDesktopState = mock<PipDesktopState>()
    private val mockDisplayController = mock<DisplayController>()

    private lateinit var controller: DesktopPipTransitionController

    private val transition = Binder()
    private val wct = WindowContainerTransaction()
    private val taskInfo =
        createFreeformTask().apply {
            lastParentTaskIdBeforePip = ActivityTaskManager.INVALID_TASK_ID
            userId = mockDesktopRepository.userId
            displayId = DISPLAY_ID
        }
    private val freeformParentTask = createFreeformTask()
    private val fullscreenParentTask = createFullscreenTask()

    @Before
    fun setUp() {
        whenever(mockPipDesktopState.isDesktopWindowingPipEnabled()).thenReturn(true)
        whenever(mockPipDesktopState.isDisplayDesktopFirst(any())).thenReturn(false)
        whenever(mockPipDesktopState.isPipInDesktopMode()).thenReturn(true)
        whenever(mockPipDesktopState.isRecentsAnimating()).thenReturn(false)
        whenever(mockPipDesktopState.getCurrentDisplayId()).thenReturn(DISPLAY_ID)
        whenever(mockDesktopUserRepositories.getProfile(any())).thenReturn(mockDesktopRepository)
        whenever(mockDesktopRepository.getActiveDeskId(any())).thenReturn(DESK_ID)
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(taskInfo.taskId)).thenReturn(taskInfo)
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(freeformParentTask.taskId))
            .thenReturn(freeformParentTask)
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(fullscreenParentTask.taskId))
            .thenReturn(fullscreenParentTask)

        controller =
            DesktopPipTransitionController(
                mockShellTaskOrganizer,
                mockDesktopTasksController,
                mockDesktopUserRepositories,
                mockPipDesktopState,
                mockDisplayController,
            )
    }

    @Test
    fun updateExpandWctForDesktop_multiActivity_nullParentInfo_returnsNull() {
        wct.apply { setWindowingMode(taskInfo.token, WINDOWING_MODE_FREEFORM) }
        taskInfo.parentTaskId = 1
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(any())).thenReturn(null)

        assertThat(
                controller.updateExpandWctForDesktop(
                    wct = wct,
                    pipTask = taskInfo,
                    displayId = DISPLAY_ID,
                )
            )
            .isNull()
    }

    @Test
    fun updateExpandWctForDesktop_nullTaskInfo_returnsNull() {
        wct.apply { setWindowingMode(taskInfo.token, WINDOWING_MODE_FREEFORM) }
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(any())).thenReturn(null)

        assertThat(
                controller.updateExpandWctForDesktop(
                    wct = wct,
                    pipTask = taskInfo,
                    displayId = DISPLAY_ID,
                )
            )
            .isNull()
    }

    @Test
    fun updateExpandWctForDesktop_sameDisplay_moveToDisplayNotInvoked() {
        wct.apply { setWindowingMode(taskInfo.token, WINDOWING_MODE_FREEFORM) }

        val runOnTransitStart =
            controller.updateExpandWctForDesktop(
                wct = wct,
                pipTask = taskInfo,
                displayId = DISPLAY_ID,
            )
        runOnTransitStart!!.invoke(Binder())

        verify(mockDesktopTasksController, never())
            .moveToDisplay(
                task = any(),
                displayId = any(),
                bounds = anyOrNull(),
                transitionHandler = anyOrNull(),
                enterReason = any(),
                captionInsets = any(),
            )
    }

    @Test
    fun updateExpandWctForDesktop_differentDisplay_moveToDisplayInvoked() {
        val newDisplay = DISPLAY_ID + 1
        wct.apply { setWindowingMode(taskInfo.token, WINDOWING_MODE_FREEFORM) }

        val runOnTransitStart =
            controller.updateExpandWctForDesktop(
                wct = wct,
                pipTask = taskInfo,
                displayId = newDisplay,
            )
        runOnTransitStart!!.invoke(Binder())

        verify(mockDesktopTasksController)
            .addMoveToDisplayChanges(
                wct = eq(wct),
                task = eq(taskInfo),
                displayId = eq(newDisplay),
                bounds = anyOrNull(),
                enterReason = eq(EnterReason.EXIT_PIP),
                captionInsets = any(),
            )
    }

    @Test
    fun maybeUpdateParentInWct_fullscreenParent_freeformChild_addFreeformChangesToWct() {
        val wct = WindowContainerTransaction()
        whenever(mockPipDesktopState.getOutPipWindowingMode(isMultiActivityChild = any()))
            .thenReturn(WINDOWING_MODE_FREEFORM)

        controller.maybeUpdateParentInWct(wct, fullscreenParentTask)

        val parentToken = fullscreenParentTask.token.asBinder()
        assertThat(wct.changes[parentToken]?.windowingMode).isEqualTo(WINDOWING_MODE_FREEFORM)
    }

    @Test
    fun maybeUpdateParentInWct_freeformParent_fullscreenChild_addFullscreenChangesToWct() {
        val wct = WindowContainerTransaction()
        whenever(mockPipDesktopState.getOutPipWindowingMode(isMultiActivityChild = any()))
            .thenReturn(WINDOWING_MODE_FULLSCREEN)

        controller.maybeUpdateParentInWct(wct, freeformParentTask)

        val parentToken = freeformParentTask.token.asBinder()
        assertThat(wct.changes[parentToken]?.windowingMode).isEqualTo(WINDOWING_MODE_FULLSCREEN)
    }

    @Test
    fun maybeUpdateParentInWct_freeformParent_freeformChild_noWctChanges() {
        val wct = WindowContainerTransaction()
        whenever(mockPipDesktopState.getOutPipWindowingMode(isMultiActivityChild = any()))
            .thenReturn(WINDOWING_MODE_FREEFORM)

        controller.maybeUpdateParentInWct(wct, freeformParentTask)

        assertThat(wct.changes.isEmpty()).isTrue()
    }

    @Test
    fun maybeUpdateParentInWct_fullscreenParent_fullscreenChild_noWctChanges() {
        val wct = WindowContainerTransaction()
        whenever(mockPipDesktopState.getOutPipWindowingMode(isMultiActivityChild = any()))
            .thenReturn(WINDOWING_MODE_FULLSCREEN)

        controller.maybeUpdateParentInWct(wct, fullscreenParentTask)

        assertThat(wct.changes.isEmpty()).isTrue()
    }

    @Test
    fun maybeReparentTaskToDesk_recentsAnimating_noAddMoveToDeskTaskChanges() {
        whenever(mockPipDesktopState.isRecentsAnimating()).thenReturn(true)

        controller.maybeReparentTaskToDesk(wct, taskInfo, isMultiActivityPip = false)

        verify(mockDesktopTasksController, never())
            .addMoveToDeskTaskChanges(wct = any(), task = any(), deskId = any())
    }

    @Test
    fun maybeReparentTaskToDesk_multiActivity_parentInDesk_addMoveTaskToFrontChanges() {
        val wct = WindowContainerTransaction()
        whenever(mockDesktopRepository.isActiveTaskInDesk(freeformParentTask.taskId, DESK_ID))
            .thenReturn(true)

        controller.maybeReparentTaskToDesk(wct, freeformParentTask, isMultiActivityPip = true)

        verify(mockDesktopTasksController)
            .addMoveTaskToFrontChanges(wct = wct, deskId = DESK_ID, taskInfo = freeformParentTask)
    }

    @Test
    fun maybeReparentTaskToDesk_multiActivity_parentNotInDesk_addMoveToDeskTaskChanges() {
        val wct = WindowContainerTransaction()
        whenever(mockDesktopRepository.isActiveTaskInDesk(freeformParentTask.taskId, DESK_ID))
            .thenReturn(false)

        controller.maybeReparentTaskToDesk(wct, freeformParentTask, isMultiActivityPip = true)

        verify(mockDesktopTasksController)
            .addMoveToDeskTaskChanges(wct = wct, task = freeformParentTask, deskId = DESK_ID)
        assertThat(wct.hierarchyOps).hasSize(1)
        wct.assertReorderAt(index = 0, freeformParentTask.token, toTop = true)
    }

    @Test
    fun maybeReparentTaskToDesk_noDeskActive_noAddMoveToDeskTaskChanges() {
        val wct = WindowContainerTransaction()
        whenever(mockDesktopRepository.getActiveDeskId(any())).thenReturn(null)

        controller.maybeReparentTaskToDesk(wct, taskInfo, isMultiActivityPip = false)

        verify(mockDesktopTasksController, never())
            .addMoveToDeskTaskChanges(wct = any(), task = any(), deskId = any())
    }

    @Test
    fun maybeReparentTaskToDesk_deskActive_addMoveToDeskTaskChanges() {
        val wct = WindowContainerTransaction()

        controller.maybeReparentTaskToDesk(wct, taskInfo, isMultiActivityPip = false)

        verify(mockDesktopTasksController)
            .addMoveToDeskTaskChanges(wct = wct, task = taskInfo, deskId = DESK_ID)
        assertThat(wct.hierarchyOps).hasSize(1)
        wct.assertReorderAt(index = 0, taskInfo.token, toTop = true)
    }

    @Test
    fun maybeReparentTaskToDesk_noDeskActive_desktopFirstDisplay_addDeskActivationChanges() {
        val wct = WindowContainerTransaction()
        whenever(mockDesktopRepository.getActiveDeskId(any())).thenReturn(null)
        whenever(mockPipDesktopState.isDisplayDesktopFirst(any())).thenReturn(true)
        whenever(mockDesktopRepository.getDefaultDeskId(any())).thenReturn(DESK_ID)

        controller.maybeReparentTaskToDesk(wct, taskInfo, isMultiActivityPip = false)

        verify(mockDesktopTasksController)
            .addDeskActivationChanges(
                deskId = DESK_ID,
                wct = wct,
                newTask = taskInfo,
                userId = mockDesktopRepository.userId,
                displayId = DISPLAY_ID,
                enterReason = EnterReason.EXIT_PIP,
            )
        verify(mockDesktopTasksController)
            .addMoveToDeskTaskChanges(wct = wct, task = taskInfo, deskId = DESK_ID)
        assertThat(wct.hierarchyOps).hasSize(1)
        wct.assertReorderAt(index = 0, taskInfo.token, toTop = true)
    }

    @Test
    fun handleRemovePipTransition_notInDesktop_wctEmpty() {
        whenever(mockPipDesktopState.isPipInDesktopMode()).thenReturn(false)
        whenever(mockDisplayController.getDisplay(taskInfo.displayId)).thenReturn(mock<Display>())

        controller.handleRemovePipTransition(wct = wct, token = taskInfo.token)

        assertThat(wct.changes.isEmpty()).isTrue()
    }

    @Test
    fun handleRemovePipTransition_inDesktop_wctRemoveTask() {
        whenever(mockDisplayController.getDisplay(taskInfo.displayId)).thenReturn(mock<Display>())

        controller.handleRemovePipTransition(wct = wct, token = taskInfo.token)

        assertThat(wct.hierarchyOps).hasSize(1)
        val taskRemoval = wct.hierarchyOps.find { op -> op.container == taskInfo.token.asBinder() }
        assertThat(taskRemoval).isNotNull()
        assertThat(taskRemoval!!.type).isEqualTo(HIERARCHY_OP_TYPE_REMOVE_TASK)
    }

    private fun WindowContainerTransaction.assertReorderAt(
        index: Int,
        token: WindowContainerToken,
        toTop: Boolean,
    ) {
        assertThat(hierarchyOps.size).isGreaterThan(index)
        val op = hierarchyOps[index]
        assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_REORDER)
        assertThat(op.container).isEqualTo(token.asBinder())
        assertThat(op.toTop).isEqualTo(toTop)
    }

    @Test
    fun handleRemovePipTransition_displayIsNull_doesNotRemoveTask() {
        val displayId = 10
        val wct = WindowContainerTransaction()
        whenever(mockPipDesktopState.isPipInDesktopMode()).thenReturn(true)
        whenever(mockPipDesktopState.getCurrentDisplayId()).thenReturn(displayId)
        whenever(mockDisplayController.getDisplay(displayId)).thenReturn(null)

        controller.handleRemovePipTransition(wct, taskInfo.token)

        val hasRemoveTaskOp =
            wct.hierarchyOps.any { op ->
                op.type == HIERARCHY_OP_TYPE_REMOVE_TASK &&
                    op.container == taskInfo.token.asBinder()
            }

        assertThat(hasRemoveTaskOp).isFalse()
    }

    private companion object {
        const val DESK_ID = 1
        const val DISPLAY_ID = 0
    }
}
