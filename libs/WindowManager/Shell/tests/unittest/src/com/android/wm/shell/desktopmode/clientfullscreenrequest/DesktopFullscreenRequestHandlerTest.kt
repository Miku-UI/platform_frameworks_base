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
package com.android.wm.shell.desktopmode.clientfullscreenrequest

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.graphics.Rect
import android.os.Binder
import android.os.IBinder
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_OPEN
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.ClientFullscreenRequestController
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.EnterResult
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.EnterResult.Approved.RestorableState
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.ExitResult
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopTestHelpers
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.RunOnTransitStart
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.desktopmode.multidesks.DesksController
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [DesktopFullscreenRequestHandler].
 *
 * Usage: atest WMShellUnitTests:DesktopFullscreenRequestHandlerTest
 */
@SmallTest
@RunWith(JUnit4::class)
class DesktopFullscreenRequestHandlerTest : ShellTestCase() {
    private val shellInit = ShellInit(TestShellExecutor())
    private val desktopUserRepositories = mock<DesktopUserRepositories>()
    private val desksOrganizer = mock<DesksOrganizer>()
    private val desksController = mock<DesksController>()
    private val desktopWallpaperActivityTokenProvider =
        mock<DesktopWallpaperActivityTokenProvider>()
    private val desktopTasksController = mock<DesktopTasksController>()
    private val displayController = mock<DisplayController>()
    private val transition = mock<IBinder>()
    private val clientFullscreenRequestController = mock<ClientFullscreenRequestController>()

    private val testScope = TestScope()

    private lateinit var repository: DesktopRepository
    private lateinit var handler: DesktopFullscreenRequestHandler

    @Before
    fun setUp() {
        whenever(desksController.canCreateDeskInDisplay(eq(DISPLAY_ID), eq(USER_ID), any()))
            .thenReturn(false) // Must allow creation explicitly in tests.
        repository =
            DesktopRepository(
                persistentRepository = mock(),
                mainCoroutineScope = testScope.backgroundScope,
                bgCoroutineScope = testScope.backgroundScope,
                userId = USER_ID,
                desktopConfig = FakeDesktopConfig(),
            )
        whenever(desktopUserRepositories.getProfile(USER_ID)).thenReturn(repository)
        handler =
            DesktopFullscreenRequestHandler(
                shellInit,
                mContext,
                desktopUserRepositories,
                desksOrganizer,
                desksController,
                desktopWallpaperActivityTokenProvider,
                displayController,
                Optional.of(clientFullscreenRequestController),
            )
        handler.desktopTasksController = desktopTasksController
    }

    @Test
    @DisableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun testShouldHandleRequest_notChangeTransition_rejects_delegateToShellDisabled() = runTest {
        val request =
            TransitionRequestInfo(
                TRANSIT_OPEN,
                /* triggerTask = */ null,
                /* remoteTransition = */ null,
            )

        val result = handler.shouldHandleRequest(request)

        assertThat(result).isFalse()
    }

    @Test
    @DisableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun testShouldHandleRequest_enterFullscreen_accepts() = runTest {
        // A task that is now fullscreen but was previously a desktop task in an active desk.
        val fullscreenTask = createFullscreenTask()
        val deskId = 5
        val request = TransitionRequestInfo(TRANSIT_CHANGE, fullscreenTask, null)
        setUpActiveDeskWithTask(
            displayId = fullscreenTask.displayId,
            deskId = deskId,
            taskId = fullscreenTask.taskId,
        )
        whenever(desksOrganizer.getDeskIdFromTaskInfo(fullscreenTask)).thenReturn(deskId)

        val result = handler.shouldHandleRequest(request)

        assertThat(result).isTrue()
    }

    @Test
    @DisableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun testShouldHandleRequest_exitFullscreen_accepts() = runTest {
        // A task that is now freeform, but was previously not in a desk.
        val freeformTask = createFreeformTask()
        val request = TransitionRequestInfo(TRANSIT_CHANGE, freeformTask, null)

        val result = handler.shouldHandleRequest(request)

        assertThat(result).isTrue()
    }

    @Test
    @DisableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun testHandleRequest_enterFullscreen_returnsMoveToFullscreenWct() = runTest {
        // A task that is now fullscreen but was previously a desktop task in an active desk.
        val fullscreenTask = createFullscreenTask()
        val deskId = 5
        val request = TransitionRequestInfo(TRANSIT_CHANGE, fullscreenTask, null)
        setUpActiveDeskWithTask(
            displayId = fullscreenTask.displayId,
            deskId = deskId,
            taskId = fullscreenTask.taskId,
        )
        whenever(desksOrganizer.getDeskIdFromTaskInfo(fullscreenTask)).thenReturn(deskId)

        val result = handler.handleRequest(transition, request)

        assertThat(result).isNotNull()
        verify(desktopTasksController)
            .addMoveToFullscreenChanges(
                wct = any(),
                taskInfo = eq(fullscreenTask),
                willExitDesktop = eq(true),
                destinationDisplayId = eq(fullscreenTask.displayId),
                skipSetWindowingMode = eq(true),
                exitReason = eq(ExitReason.CLIENT_REQUEST_ENTER_FULLSCREEN),
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun testHandleRequest_enterFullscreen_appliesRunOnTransitCallback() = runTest {
        // A task that is now fullscreen but was previously a desktop task in an active desk.
        val fullscreenTask = createFullscreenTask()
        val deskId = 5
        val request = TransitionRequestInfo(TRANSIT_CHANGE, fullscreenTask, null)
        setUpActiveDeskWithTask(
            displayId = fullscreenTask.displayId,
            deskId = deskId,
            taskId = fullscreenTask.taskId,
        )
        whenever(desksOrganizer.getDeskIdFromTaskInfo(fullscreenTask)).thenReturn(deskId)
        val runOnTransit: (IBinder) -> Unit = mock()
        whenever(
                desktopTasksController.addMoveToFullscreenChanges(
                    wct = any(),
                    taskInfo = eq(fullscreenTask),
                    willExitDesktop = eq(true),
                    destinationDisplayId = eq(fullscreenTask.displayId),
                    skipSetWindowingMode = eq(true),
                    exitReason = eq(ExitReason.CLIENT_REQUEST_ENTER_FULLSCREEN),
                )
            )
            .thenReturn(runOnTransit)

        val result = handler.handleRequest(transition, request)

        assertThat(result).isNotNull()
        verify(runOnTransit).invoke(transition)
    }

    @Test
    @DisableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun testHandleRequest_exitFullscreen_noDeskAssigned_returnsWct() = runTest {
        // A task that is now freeform, but was previously not in a desk.
        val freeformTask = createFreeformTask()
        val request = TransitionRequestInfo(TRANSIT_CHANGE, freeformTask, null)
        // Task was not assigned to a desk by core.
        whenever(desksOrganizer.getDeskIdFromTaskInfo(freeformTask)).thenReturn(null)
        whenever(
                desktopTasksController.handleFreeformTaskPlacement(
                    task = eq(freeformTask),
                    transition = eq(transition),
                    targetDisplayId = eq(freeformTask.displayId),
                    suggestedTargetDeskId = eq(null), // No suggested desk.
                    requestedTaskBounds = eq(null),
                    requestType = eq(TRANSIT_CHANGE),
                    enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                    forceBringTaskToFront = eq(false),
                )
            )
            .thenReturn(WindowContainerTransaction())

        val result = handler.handleRequest(transition, request)

        assertThat(result).isNotNull()
        verify(desktopTasksController)
            .handleFreeformTaskPlacement(
                task = eq(freeformTask),
                transition = eq(transition),
                targetDisplayId = eq(freeformTask.displayId),
                suggestedTargetDeskId = eq(null), // No suggested desk.
                requestedTaskBounds = eq(null),
                requestType = eq(TRANSIT_CHANGE),
                enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                forceBringTaskToFront = eq(false),
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun testHandleRequest_exitFullscreen_deskAssigned_returnsWct() = runTest {
        // A task that is now freeform, but was previously not in a desk.
        val freeformTask = createFreeformTask()
        val request = TransitionRequestInfo(TRANSIT_CHANGE, freeformTask, null)
        // Task was assigned to a desk by core.
        val deskId = 5
        whenever(desksOrganizer.getDeskIdFromTaskInfo(freeformTask)).thenReturn(deskId)
        whenever(
                desktopTasksController.handleFreeformTaskPlacement(
                    task = eq(freeformTask),
                    transition = eq(transition),
                    targetDisplayId = eq(freeformTask.displayId),
                    suggestedTargetDeskId = eq(deskId), // Suggested desk.
                    requestedTaskBounds = eq(null),
                    requestType = eq(TRANSIT_CHANGE),
                    enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                    forceBringTaskToFront = eq(false),
                )
            )
            .thenReturn(WindowContainerTransaction())

        val result = handler.handleRequest(transition, request)

        assertThat(result).isNotNull()
        verify(desktopTasksController)
            .handleFreeformTaskPlacement(
                task = eq(freeformTask),
                transition = eq(transition),
                targetDisplayId = eq(freeformTask.displayId),
                suggestedTargetDeskId = eq(deskId), // Suggested desk.
                requestedTaskBounds = eq(null),
                requestType = eq(TRANSIT_CHANGE),
                enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                forceBringTaskToFront = eq(false),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun handleEnterFullscreen_taskNotInDesk_rejects() = runTest {
        val task = createNonDesktopTask()
        val deskId = 5
        // Desk is active, but task doesn't belong to it.
        setUpActiveDesk(displayId = task.displayId, deskId = deskId)

        val result = handler.handleEnterFullscreen(Binder(), task)

        assertThat(result).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun handleEnterFullscreen_taskInDeskButDeskInactive_rejects() = runTest {
        val task = createFreeformTask()
        val deskId = 5
        // The task belongs to the desk, but the desk is not active.
        setUpInactiveDeskWithTask(displayId = task.displayId, deskId = deskId, taskId = task.taskId)

        val result = handler.handleEnterFullscreen(Binder(), task)

        assertThat(result).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun handleEnterFullscreen_approves() = runTest {
        val task = createFreeformTask()
        val deskId = 5
        // The task belongs to the desk, but the desk is not active.
        setUpActiveDeskWithTask(displayId = task.displayId, deskId = deskId, taskId = task.taskId)

        val result = handler.handleEnterFullscreen(Binder(), task)

        assertThat(result).isNotNull()
        assertThat(result is EnterResult.Approved).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun handleEnterFullscreen_approved_movesToFullscreen() = runTest {
        val task = createFreeformTask()
        val runOnTransitStart = mock<RunOnTransitStart>()
        whenever(
                desktopTasksController.addMoveToFullscreenChanges(
                    wct = any(),
                    taskInfo = eq(task),
                    willExitDesktop = any(),
                    destinationDisplayId = any(),
                    skipSetWindowingMode = any(),
                    exitReason = any(),
                )
            )
            .thenReturn(runOnTransitStart)
        val deskId = 5
        // The task belongs to the desk, but the desk is not active.
        setUpActiveDeskWithTask(displayId = task.displayId, deskId = deskId, taskId = task.taskId)

        val transition = Binder()
        handler.handleEnterFullscreen(transition, task)

        verify(desktopTasksController)
            .addMoveToFullscreenChanges(
                wct = any(),
                taskInfo = eq(task),
                willExitDesktop = eq(true),
                destinationDisplayId = eq(task.displayId),
                skipSetWindowingMode = eq(false),
                exitReason = eq(ExitReason.CLIENT_REQUEST_ENTER_FULLSCREEN),
            )
        verify(runOnTransitStart).invoke(transition)
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun handleEnterFullscreen_approved_returnsDeskAsRestorableState() = runTest {
        val task = createFreeformTask(bounds = Rect(200, 200, 800, 800))
        val deskId = 5
        // The task belongs to the desk, but the desk is not active.
        setUpActiveDeskWithTask(displayId = task.displayId, deskId = deskId, taskId = task.taskId)

        val result = handler.handleEnterFullscreen(Binder(), task)

        assertThat(result).isNotNull()
        assertThat(result is EnterResult.Approved).isTrue()
        assertThat((result as EnterResult.Approved).restorableState)
            .isEqualTo(
                RestorableState.Desktop(originalDeskId = deskId, bounds = Rect(200, 200, 800, 800))
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun handleExitFullscreen_nullDesktopRestorableState_ignores() = runTest {
        val task = createFullscreenTask()

        val result =
            handler.handleExitFullscreen(transition = Binder(), task = task, restorableState = null)

        assertThat(result).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun handleExitFullscreen_accepts() = runTest {
        val restoreBounds = Rect(200, 200, 800, 800)
        val transition = Binder()
        val task = createFullscreenTask()
        val deskId = 5
        setUpInactiveDesk(task.displayId, deskId)
        whenever(
                desktopTasksController.handleFreeformTaskPlacement(
                    task = eq(task),
                    transition = eq(transition),
                    targetDisplayId = eq(task.displayId),
                    suggestedTargetDeskId = eq(deskId),
                    requestedTaskBounds = eq(restoreBounds),
                    requestType = eq(TRANSIT_CHANGE),
                    enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                    forceBringTaskToFront = eq(true),
                )
            )
            .thenReturn(WindowContainerTransaction())

        val result =
            handler.handleExitFullscreen(
                transition = transition,
                task = task,
                restorableState =
                    RestorableState.Desktop(originalDeskId = 5, bounds = restoreBounds),
            )

        assertThat(result).isNotNull()
        assertThat(result is ExitResult.Approved).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun handleExitFullscreen_accepted_movesToOriginalDesk() = runTest {
        val restoreBounds = Rect(200, 200, 800, 800)
        val transition = Binder()
        val task = createFullscreenTask()
        val deskId = 5
        setUpInactiveDesk(task.displayId, deskId)
        whenever(
                desktopTasksController.handleFreeformTaskPlacement(
                    task = eq(task),
                    transition = eq(transition),
                    targetDisplayId = eq(task.displayId),
                    suggestedTargetDeskId = eq(deskId),
                    requestedTaskBounds = eq(restoreBounds),
                    requestType = eq(TRANSIT_CHANGE),
                    enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                    forceBringTaskToFront = eq(true),
                )
            )
            .thenReturn(WindowContainerTransaction())

        handler.handleExitFullscreen(
            transition = transition,
            task = task,
            restorableState = RestorableState.Desktop(originalDeskId = 5, bounds = restoreBounds),
        )

        verify(desktopTasksController)
            .handleFreeformTaskPlacement(
                task = eq(task),
                transition = eq(transition),
                targetDisplayId = eq(task.displayId),
                suggestedTargetDeskId = eq(deskId),
                requestedTaskBounds = eq(restoreBounds),
                requestType = eq(TRANSIT_CHANGE),
                enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                forceBringTaskToFront = eq(true),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun handleExitFullscreen_accepted_originalDeskDeleted_movesToDefaultDesk() = runTest {
        val restoreBounds = Rect(200, 200, 800, 800)
        val transition = Binder()
        val task = createFullscreenTask()
        val deskId = 5
        setUpInactiveDesk(task.displayId, deskId)
        whenever(
                desktopTasksController.handleFreeformTaskPlacement(
                    task = eq(task),
                    transition = eq(transition),
                    targetDisplayId = eq(task.displayId),
                    suggestedTargetDeskId = eq(deskId),
                    requestedTaskBounds = eq(restoreBounds),
                    requestType = eq(TRANSIT_CHANGE),
                    enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                    forceBringTaskToFront = eq(true),
                )
            )
            .thenReturn(WindowContainerTransaction())

        handler.handleExitFullscreen(
            transition = transition,
            task = task,
            // Desk doesn't exist.
            restorableState = RestorableState.Desktop(originalDeskId = 10, bounds = restoreBounds),
        )

        verify(desktopTasksController)
            .handleFreeformTaskPlacement(
                task = eq(task),
                transition = eq(transition),
                targetDisplayId = eq(task.displayId),
                suggestedTargetDeskId = eq(deskId),
                requestedTaskBounds = eq(restoreBounds),
                requestType = eq(TRANSIT_CHANGE),
                enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                forceBringTaskToFront = eq(true),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun handleExitFullscreen_accepted_originalDeskDeletedA_noOtherDeskExist_movesToNewDesk() =
        runTest {
            val restoreBounds = Rect(200, 200, 800, 800)
            val transition = Binder()
            val task = createFullscreenTask()
            val deskId = 5
            setUpDeskCreationRequest(deskId)
            whenever(
                    desktopTasksController.handleFreeformTaskPlacement(
                        task = eq(task),
                        transition = eq(transition),
                        targetDisplayId = eq(task.displayId),
                        suggestedTargetDeskId = eq(deskId),
                        requestedTaskBounds = eq(restoreBounds),
                        requestType = eq(TRANSIT_CHANGE),
                        enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                        forceBringTaskToFront = eq(true),
                    )
                )
                .thenReturn(WindowContainerTransaction())

            handler.handleExitFullscreen(
                transition = transition,
                task = task,
                // Desk doesn't exist.
                restorableState =
                    RestorableState.Desktop(originalDeskId = 10, bounds = restoreBounds),
            )

            verify(desktopTasksController)
                .handleFreeformTaskPlacement(
                    task = eq(task),
                    transition = eq(transition),
                    targetDisplayId = eq(task.displayId),
                    suggestedTargetDeskId = eq(deskId),
                    requestedTaskBounds = eq(restoreBounds),
                    requestType = eq(TRANSIT_CHANGE),
                    enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                    forceBringTaskToFront = eq(true),
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    fun handleExitFullscreen_originalDeskDeleted_noOtherDeskExist_cannotCreate_rejects() = runTest {
        val restoreBounds = Rect(200, 200, 800, 800)
        val transition = Binder()
        val task = createFullscreenTask()
        whenever(desksController.canCreateDeskInDisplay(eq(DISPLAY_ID), eq(USER_ID), any()))
            .thenReturn(false)

        val result =
            handler.handleExitFullscreen(
                transition = transition,
                task = task,
                // Desk doesn't exist.
                restorableState =
                    RestorableState.Desktop(originalDeskId = 10, bounds = restoreBounds),
            )

        assertThat(result).isNotNull()
        assertThat(result is ExitResult.Failed).isTrue()
    }

    private fun setUpActiveDeskWithTask(displayId: Int, deskId: Int, taskId: Int) {
        setUpActiveDesk(displayId, deskId)
        repository.addTaskToDesk(
            displayId = displayId,
            deskId = deskId,
            taskId = taskId,
            isVisible = true,
            taskBounds = Rect(200, 200, 1000, 1000),
        )
        whenever(desksOrganizer.getDeskIdFromTaskInfo(argThat { this.taskId == taskId }))
            .thenReturn(deskId)
    }

    private fun setUpInactiveDeskWithTask(displayId: Int, deskId: Int, taskId: Int) {
        setUpInactiveDesk(displayId, deskId)
        repository.addTaskToDesk(
            displayId = displayId,
            deskId = deskId,
            taskId = taskId,
            isVisible = false,
            taskBounds = Rect(200, 200, 1000, 1000),
        )
    }

    private fun setUpActiveDesk(displayId: Int, deskId: Int) {
        repository.addDesk(displayId, deskId)
        repository.setActiveDesk(displayId, deskId)
    }

    private fun setUpInactiveDesk(displayId: Int, deskId: Int) {
        repository.addDesk(displayId, deskId)
        repository.setDeskInactive(deskId)
    }

    private suspend fun setUpDeskCreationRequest(deskId: Int) {
        whenever(desksController.canCreateDeskInDisplay(eq(DISPLAY_ID), eq(USER_ID), any()))
            .thenReturn(true)
        whenever(
                desktopTasksController.createDeskSuspending(
                    displayId = eq(DISPLAY_ID),
                    userId = eq(USER_ID),
                    enforceDeskLimit = any(),
                )
            )
            .thenAnswer { invocationOnMock ->
                val displayId = invocationOnMock.arguments[0] as Int
                repository.addDesk(displayId, deskId)
                return@thenAnswer deskId
            }
    }

    private fun createFreeformTask(bounds: Rect? = null): RunningTaskInfo {
        return DesktopTestHelpers.createFreeformTask(userId = USER_ID, bounds = bounds)
    }

    private fun createFullscreenTask(): RunningTaskInfo {
        return DesktopTestHelpers.createFullscreenTaskBuilder().setUserId(USER_ID).build()
    }

    private fun createNonDesktopTask(): RunningTaskInfo {
        return TestRunningTaskInfoBuilder()
            .setUserId(USER_ID)
            .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
            .build()
    }

    private companion object {
        private const val USER_ID = 0
        private const val DISPLAY_ID = 0
    }
}
