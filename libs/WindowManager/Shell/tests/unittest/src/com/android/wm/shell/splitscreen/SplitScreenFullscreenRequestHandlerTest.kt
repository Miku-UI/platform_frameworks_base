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

package com.android.wm.shell.splitscreen

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Rect
import android.os.IBinder
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.ClientFullscreenRequestController
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.EnterResult
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.EnterResult.Approved.RestorableState
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.ExitResult
import com.android.wm.shell.shared.split.SplitScreenConstants
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitPosition
import com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_MAIN
import com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_SIDE
import com.android.wm.shell.splitscreen.SplitScreen.StageType
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [SplitScreenFullscreenRequestHandler].
 *
 * Usage: atest WMShellUnitTests:SplitScreenFullscreenRequestHandlerTest
 */
@SmallTest
@RunWith(JUnit4::class)
class SplitScreenFullscreenRequestHandlerTest : ShellTestCase() {

    private val shellTaskOrganizer: ShellTaskOrganizer = mock()
    private val stageCoordinator: StageCoordinatorAbstract = mock()
    private val clientFullscreenRequestController: ClientFullscreenRequestController = mock()
    private val transition: IBinder = mock()
    private val splitScreenTransitions: SplitScreenTransitions = mock()

    private lateinit var handler: SplitScreenFullscreenRequestHandler

    @Before
    fun setUp() {
        whenever(stageCoordinator.splitTransitions).thenReturn(splitScreenTransitions)
        handler =
            SplitScreenFullscreenRequestHandler(
                shellTaskOrganizer,
                stageCoordinator,
                clientFullscreenRequestController,
            )
    }

    @Test
    fun testHandleEnterFullscreen_notInSplit_returnsNull() {
        whenever(stageCoordinator.isSplitActive).thenReturn(false)
        assertNull(handler.handleEnterFullscreen(transition, createTaskInfo()))
    }

    @Test
    fun testHandleEnterFullscreen_splitNotVisible_returnsNull() {
        whenever(stageCoordinator.isSplitActive).thenReturn(true)
        whenever(stageCoordinator.isSplitScreenVisible).thenReturn(false)
        assertNull(handler.handleEnterFullscreen(transition, createTaskInfo()))
    }

    @Test
    fun testHandleEnterFullscreen_taskNotOnSplitDisplay_returnsNull() {
        whenever(stageCoordinator.isSplitActive).thenReturn(true)
        whenever(stageCoordinator.isSplitScreenVisible).thenReturn(true)
        whenever(stageCoordinator.isTaskOnSplitDisplay(any())).thenReturn(false)
        assertNull(handler.handleEnterFullscreen(transition, createTaskInfo()))
    }

    @Test
    fun testHandleEnterFullscreen_nonPersistentSnapPosition_returnsFailed() {
        val task = createTaskInfo()
        setUpActiveSplitWithTask(task)
        whenever(stageCoordinator.calculateCurrentSnapPosition())
            .thenReturn(SplitScreenConstants.SNAP_TO_MINIMIZE)

        val result = handler.handleEnterFullscreen(transition, task)

        assertNotNull(result)
        assertThat(result).isInstanceOf(EnterResult.Failed::class.java)
    }

    @Test
    fun testHandleEnterFullscreen_persistentSnapPosition_approvesRequest() {
        val task = createTaskInfo()
        setUpActiveSplitWithTask(task)
        whenever(stageCoordinator.calculateCurrentSnapPosition()).thenReturn(SNAP_TO_2_50_50)

        val result = handler.handleEnterFullscreen(transition, task)

        assertNotNull(result)
        assertThat(result).isInstanceOf(EnterResult.Approved::class.java)
    }

    @Test
    fun testHandleEnterFullscreen_approved_includesRestorableState() {
        val task = createTaskInfo()
        val otherTask = createTaskInfo()
        setUpActiveSplitWithTask(
            task = task,
            splitPosition = SPLIT_POSITION_BOTTOM_OR_RIGHT,
            stageOfTask = STAGE_TYPE_SIDE,
            otherTask = otherTask,
        )
        whenever(stageCoordinator.calculateCurrentSnapPosition()).thenReturn(SNAP_TO_2_50_50)

        val result = handler.handleEnterFullscreen(transition, task)

        assertNotNull(result)
        assertThat(result).isInstanceOf(EnterResult.Approved::class.java)
        val approvedResult = result as EnterResult.Approved
        assertThat(approvedResult.restorableState).isNotNull()
        assertThat(approvedResult.restorableState)
            .isInstanceOf(RestorableState.SplitScreen::class.java)
        val splitScreenState = approvedResult.restorableState as RestorableState.SplitScreen
        assertEquals(SPLIT_POSITION_BOTTOM_OR_RIGHT, splitScreenState.originalSplitPosition)
        assertEquals(STAGE_TYPE_SIDE, splitScreenState.originalSnapPosition)
        assertEquals(otherTask.taskId, splitScreenState.otherTaskId)
    }

    @Test
    fun testHandleExitFullscreen_nullRestorableState_returnsNull() {
        assertNull(handler.handleExitFullscreen(transition, createTaskInfo(), null))
    }

    @Test
    fun testHandleExitFullscreen_nonSplitScreenRestorableState_returnsNull() {
        val nonSplitState = RestorableState.Desktop(originalDeskId = 5, bounds = Rect())
        assertNull(handler.handleExitFullscreen(transition, createTaskInfo(), nonSplitState))
    }

    @Test
    fun testHandleExitFullscreen_otherTaskNotRunning_returnsFailed() {
        val task = createTaskInfo()
        val restorableState =
            RestorableState.SplitScreen(
                originalSplitPosition = SPLIT_POSITION_TOP_OR_LEFT,
                originalSnapPosition = SNAP_TO_2_50_50,
                otherTaskId = 2,
            )
        whenever(shellTaskOrganizer.getRunningTaskInfo(restorableState.otherTaskId))
            .thenReturn(null)

        val result = handler.handleExitFullscreen(transition, task, restorableState)

        assertNotNull(result)
        assertThat(result).isInstanceOf(ExitResult.Failed::class.java)
    }

    @Test
    fun testHandleExitFullscreen_otherTaskRunning_approvesRequest() {
        val task = createTaskInfo()
        val otherTask = createTaskInfo()
        val restorableState =
            RestorableState.SplitScreen(
                originalSplitPosition = SPLIT_POSITION_TOP_OR_LEFT,
                originalSnapPosition = SNAP_TO_2_50_50,
                otherTaskId = otherTask.taskId,
            )
        whenever(shellTaskOrganizer.getRunningTaskInfo(restorableState.otherTaskId))
            .thenReturn(otherTask)

        val result = handler.handleExitFullscreen(transition, task, restorableState)

        assertNotNull(result)
        assertThat(result).isInstanceOf(ExitResult.Approved::class.java)
    }

    private fun setUpActiveSplitWithTask(
        task: RunningTaskInfo,
        @SplitPosition splitPosition: Int = SPLIT_POSITION_TOP_OR_LEFT,
        @StageType stageOfTask: Int = STAGE_TYPE_MAIN,
        otherTask: RunningTaskInfo = createTaskInfo(),
    ) {
        whenever(stageCoordinator.isSplitActive).thenReturn(true)
        whenever(stageCoordinator.isSplitScreenVisible).thenReturn(true)
        whenever(stageCoordinator.isTaskOnSplitDisplay(task)).thenReturn(true)
        whenever(stageCoordinator.getCurrentStageTypeOfTask(task.taskId)).thenReturn(stageOfTask)
        whenever(stageCoordinator.getSplitPosition(task.taskId)).thenReturn(splitPosition)
        // Other task.
        val oppositePosition =
            if (splitPosition == SPLIT_POSITION_TOP_OR_LEFT) SPLIT_POSITION_BOTTOM_OR_RIGHT
            else SPLIT_POSITION_TOP_OR_LEFT
        whenever(stageCoordinator.getTaskId(oppositePosition)).thenReturn(otherTask.taskId)
    }

    private fun createTaskInfo(): RunningTaskInfo {
        return TestRunningTaskInfoBuilder().build()
    }
}
