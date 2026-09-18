/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.Intent
import android.graphics.Rect
import android.platform.test.annotations.DesktopTest
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.server.am.Flags.FLAG_PERCEPTIBLE_TASKS
import com.android.testing.wm.util.MockToken
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFullscreenTask
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellController
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for {@link DesktopTaskChangeListener}
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopTaskChangeListenerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopTaskChangeListenerTest : ShellTestCase() {

    private lateinit var desktopTaskChangeListener: DesktopTaskChangeListener

    private val desktopUserRepositories = mock<DesktopUserRepositories>()
    private val desktopRepository = mock<DesktopRepository>()
    private val shellController = mock<ShellController>()
    private val pinnedController = mock<PinnedLayerController>()
    private val desksOrganizer = mock<DesksOrganizer>()
    private val desktopState =
        FakeDesktopState().apply {
            canEnterDesktopMode = true
            overrideDesktopModeSupportPerDisplay[UNSUPPORTED_DISPLAY_ID] = false
        }

    @Before
    fun setUp() {
        desktopTaskChangeListener =
            DesktopTaskChangeListener(
                desktopUserRepositories,
                desktopState,
                shellController,
                pinnedController,
                desksOrganizer,
            )

        whenever(desktopUserRepositories.current).thenReturn(desktopRepository)
        whenever(desktopUserRepositories.getProfile(anyInt())).thenReturn(desktopRepository)
        whenever(pinnedController.isPinned(any())).thenReturn(false)
        whenever(desktopRepository.getActiveDeskId(DEFAULT_DISPLAY)).thenReturn(ACTIVE_DESK_ID)
    }

    @Test
    fun onTaskOpening_fullscreenTask_nonActiveDesktopTask_noop() {
        val task = createFullscreenTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(false)
        whenever(desksOrganizer.getDeskIdFromTaskInfo(task)).thenReturn(null)

        desktopTaskChangeListener.onTaskOpening(task)

        verify(desktopUserRepositories.current, never())
            .addTaskToDesk(task.displayId, ACTIVE_DESK_ID, task.taskId, task.isVisible, Rect())
        verify(desktopUserRepositories.current, never()).removeTask(task.taskId)
    }

    @Test
    fun onTaskOpening_fullscreenTaskInNewDisplay_activeFreeformTask_removeTaskFromRepo() {
        val task = createFullscreenTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desksOrganizer.getDeskIdFromTaskInfo(task)).thenReturn(null)
        desktopUserRepositories.current.addTaskToDesk(
            task.displayId,
            ACTIVE_DESK_ID,
            task.taskId,
            task.isVisible,
            taskBounds = Rect(),
        )

        task.displayId += 1
        desktopTaskChangeListener.onTaskOpening(task)

        verify(desktopUserRepositories.current).removeTask(task.taskId)
    }

    @Test
    fun onTaskOpening_fullscreenTask_taskIsActiveInDesktopRepo_removesTaskFromDesktopRepo() {
        val task = createFullscreenTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)

        desktopTaskChangeListener.onTaskOpening(task)

        verify(desktopUserRepositories.current).removeTask(task.taskId)
    }

    @Test
    fun onTaskOpening_freeformTask_activeInDesktopRepository_addsTaskToDesk() {
        val task = createFreeformTask(bounds = TASK_BOUNDS).apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desksOrganizer.getDeskIdFromTaskInfo(task)).thenReturn(ACTIVE_DESK_ID)

        desktopTaskChangeListener.onTaskOpening(task)

        verify(desktopUserRepositories.current)
            .addTaskToDesk(task.displayId, ACTIVE_DESK_ID, task.taskId, task.isVisible, TASK_BOUNDS)
    }

    @Test
    fun onTaskOpening_freeformTask_notActiveInDesktopRepo_notAddedToRepository() {
        val task = createFreeformTask(bounds = TASK_BOUNDS).apply { isVisible = false }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(false)
        whenever(desksOrganizer.getDeskIdFromTaskInfo(task)).thenReturn(null)

        desktopTaskChangeListener.onTaskOpening(task)

        verify(desktopUserRepositories.current, never())
            .addTaskToDesk(eq(task.displayId), any(), eq(task.taskId), any(), any())
    }

    @Test
    fun onTaskOpening_freeformWallpaperActivityTask_noop() {
        val freeformWallpaperActivity = createWallpaperTaskInfo(WINDOWING_MODE_FREEFORM)
        whenever(desktopUserRepositories.current.isActiveTask(freeformWallpaperActivity.taskId))
            .thenReturn(false)
        whenever(desksOrganizer.getDeskIdFromTaskInfo(freeformWallpaperActivity)).thenReturn(null)

        desktopTaskChangeListener.onTaskOpening(freeformWallpaperActivity)

        verify(desktopUserRepositories.current, never())
            .addTaskToDesk(
                freeformWallpaperActivity.displayId,
                ACTIVE_DESK_ID,
                freeformWallpaperActivity.taskId,
                freeformWallpaperActivity.isVisible,
                freeformWallpaperActivity.configuration.windowConfiguration.bounds,
            )
    }

    @Test
    fun onTaskOpening_desktopModeNotSupportedInDisplay_noOp() {
        val task = createFreeformTask(UNSUPPORTED_DISPLAY_ID)
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(false)
        whenever(desksOrganizer.getDeskIdFromTaskInfo(task)).thenReturn(null)

        desktopTaskChangeListener.onTaskOpening(task)

        verify(desktopUserRepositories.current, never())
            .addTaskToDesk(
                displayId = eq(UNSUPPORTED_DISPLAY_ID),
                deskId = eq(ACTIVE_DESK_ID),
                taskId = any(),
                isVisible = any(),
                taskBounds = any(),
            )
    }

    @Test
    @EnableFlags(FLAG_PERCEPTIBLE_TASKS)
    @DesktopTest(cujs = ["b/429989932"])
    fun onTaskOpening_freeformTask_enablePerceptibleTask() {
        val task = createFreeformTask(bounds = TASK_BOUNDS).apply { isVisible = false }
        desktopTaskChangeListener.onTaskOpening(task)
        assertThat(desktopTaskChangeListener.isTaskPerceptible(task.taskId)).isTrue()
    }

    @Test
    @EnableFlags(FLAG_PERCEPTIBLE_TASKS)
    @DesktopTest(cujs = ["b/429989932"])
    fun onTaskOpening_fullscreenTask_notEnablePerceptibleTask() {
        val task = createFullscreenTask().apply { isVisible = true }
        desktopTaskChangeListener.onTaskOpening(task)
        assertThat(desktopTaskChangeListener.isTaskPerceptible(task.taskId)).isFalse()
    }

    @Test
    fun onTaskOpening_pinDesktopTask_removeTaskFromRepo() {
        val task = createFreeformTask(bounds = TASK_BOUNDS).apply { isVisible = false }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        whenever(pinnedController.isPinned(task.taskId)).thenReturn(true)

        desktopTaskChangeListener.onTaskOpening(task)

        verify(desktopUserRepositories.current).removeTask(task.taskId)
    }

    @Test
    fun onTaskChanging_fullscreenTask_activeInDesktopRepository_removesTaskFromRepo() {
        val task = createFullscreenTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)

        desktopTaskChangeListener.onTaskChanging(task)

        verify(desktopUserRepositories.current).removeTask(task.taskId)
    }

    @Test
    fun onTaskChanging_fullscreenTask_nonActiveInDesktopRepo_noop() {
        val task = createFullscreenTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(false)

        desktopTaskChangeListener.onTaskChanging(task)

        verify(desktopUserRepositories.current, never()).removeTask(task.taskId)
    }

    @Test
    fun onTaskChanging_freeformTaskNotInDesk_notAddedToRepository() {
        val task = createFreeformTask(bounds = TASK_BOUNDS).apply { isVisible = true }
        whenever(desksOrganizer.getDeskIdFromTaskInfo(task)).thenReturn(null)

        desktopTaskChangeListener.onTaskChanging(task)

        verify(desktopUserRepositories.current, never())
            .addTaskToDesk(eq(task.displayId), any(), eq(task.taskId), any(), any())
    }

    @Test
    fun onTaskChanging_freeformTaskInDesk_addsTaskToSameDesk() {
        val task = createFreeformTask(bounds = TASK_BOUNDS).apply { isVisible = true }
        whenever(desksOrganizer.getDeskIdFromTaskInfo(task)).thenReturn(ACTIVE_DESK_ID + 1)

        desktopTaskChangeListener.onTaskChanging(task)

        verify(desktopUserRepositories.current)
            .addTaskToDesk(
                task.displayId,
                ACTIVE_DESK_ID + 1,
                task.taskId,
                task.isVisible,
                TASK_BOUNDS,
            )
    }

    @Test
    fun onTaskChanging_freeformWallpaperActivityTask_noop() {
        val task = createWallpaperTaskInfo(WINDOWING_MODE_FREEFORM)
        whenever(desksOrganizer.getDeskIdFromTaskInfo(task)).thenReturn(null)

        desktopTaskChangeListener.onTaskChanging(task)

        verify(desktopUserRepositories.current, never())
            .addTaskToDesk(
                task.displayId,
                ACTIVE_DESK_ID,
                task.taskId,
                task.isVisible,
                task.configuration.windowConfiguration.bounds,
            )
    }

    @Test
    @EnableFlags(FLAG_PERCEPTIBLE_TASKS)
    @DesktopTest(cujs = ["b/429989932"])
    fun onTaskChanging_fullscreenTask_notEnablePerceptibleTask() {
        val task = createFullscreenTask().apply { isVisible = true }
        desktopTaskChangeListener.onTaskChanging(task)
        assertThat(desktopTaskChangeListener.isTaskPerceptible(task.taskId)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_PERCEPTIBLE_TASKS)
    @DesktopTest(cujs = ["b/429989932"])
    fun onTaskChanging_freeformTask_enablePerceptibleTask() {
        val task = createFreeformTask(bounds = TASK_BOUNDS).apply { isVisible = true }
        desktopTaskChangeListener.onTaskChanging(task)
        assertThat(desktopTaskChangeListener.isTaskPerceptible(task.taskId)).isTrue()
    }

    @Test
    fun onTaskChanging_desktopModeNotSupportedInDisplay_noOp() {
        val task = createFreeformTask(UNSUPPORTED_DISPLAY_ID)
        whenever(desksOrganizer.getDeskIdFromTaskInfo(task)).thenReturn(null)

        desktopTaskChangeListener.onTaskChanging(task)

        verify(desktopUserRepositories.current, never())
            .addTaskToDesk(
                displayId = eq(UNSUPPORTED_DISPLAY_ID),
                deskId = eq(ACTIVE_DESK_ID),
                taskId = any(),
                isVisible = any(),
                taskBounds = any(),
            )
    }

    @Test
    fun onTaskChanging_taskMovedToUnsupportedDisplay_removesTaskFromRepo() {
        val task = createFullscreenTask()
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        // Task is no longer freeform as it moved to a display that does not support it.
        task.displayId = UNSUPPORTED_DISPLAY_ID

        desktopTaskChangeListener.onTaskChanging(task)

        verify(desktopUserRepositories.current).removeTask(task.taskId)
    }

    @Test
    fun onTaskChanging_taskIsPinned_removeTaskFromRepo() {
        val task = createFreeformTask(bounds = TASK_BOUNDS).apply { isVisible = false }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        whenever(pinnedController.isPinned(task.taskId)).thenReturn(true)

        desktopTaskChangeListener.onTaskChanging(task)

        verify(desktopUserRepositories.current).removeTask(task.taskId)
    }

    @Test
    fun onTaskMovingToFront_fullscreenTask_activeTaskInDesktopRepo_removesTaskFromRepo() {
        val task = createFullscreenTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)

        desktopTaskChangeListener.onTaskMovingToFront(task)

        verify(desktopUserRepositories.current).removeTask(task.taskId)
    }

    @Test
    fun onTaskMovingToFront_fullscreenTask_nonActiveTaskInDesktopRepo_noop() {
        val task = createFullscreenTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(false)

        desktopTaskChangeListener.onTaskMovingToFront(task)

        verify(desktopUserRepositories.current, never()).removeTask(task.taskId)
    }

    @Test
    fun onTaskMovingToFront_freeformTask_addsTaskToRepo() {
        val task = createFreeformTask(bounds = TASK_BOUNDS).apply { isVisible = true }
        whenever(desksOrganizer.getDeskIdFromTaskInfo(task)).thenReturn(ACTIVE_DESK_ID + 1)

        desktopTaskChangeListener.onTaskMovingToFront(task)

        verify(desktopUserRepositories.current)
            .addTaskToDesk(
                task.displayId,
                ACTIVE_DESK_ID + 1,
                task.taskId,
                task.isVisible,
                TASK_BOUNDS,
            )
    }

    @Test
    fun onTaskMovingToFront_freeformWallpaperActivityTask_noop() {
        val task = createWallpaperTaskInfo(WINDOWING_MODE_FREEFORM)
        whenever(desksOrganizer.getDeskIdFromTaskInfo(task)).thenReturn(null)

        desktopTaskChangeListener.onTaskMovingToFront(task)

        verify(desktopUserRepositories.current, never())
            .addTaskToDesk(
                task.displayId,
                ACTIVE_DESK_ID,
                task.taskId,
                task.isVisible,
                task.configuration.windowConfiguration.bounds,
            )
    }

    @Test
    fun onTaskMovingToFront_desktopModeNotSupportedInDisplay_noOp() {
        val task = createFreeformTask(UNSUPPORTED_DISPLAY_ID).apply { isVisible = true }
        whenever(desksOrganizer.getDeskIdFromTaskInfo(task)).thenReturn(null)

        desktopTaskChangeListener.onTaskMovingToFront(task)

        verify(desktopUserRepositories.current, never())
            .addTaskToDesk(
                displayId = eq(UNSUPPORTED_DISPLAY_ID),
                deskId = eq(ACTIVE_DESK_ID),
                taskId = any(),
                isVisible = any(),
                taskBounds = any(),
            )
    }

    @Test
    @EnableFlags(FLAG_PERCEPTIBLE_TASKS)
    @DesktopTest(cujs = ["b/429989932"])
    fun onTaskMovingToFront_fullscreenTask_notEnablePerceptibleTask() {
        val task = createFullscreenTask().apply { isVisible = true }
        desktopTaskChangeListener.onTaskMovingToFront(task)
        assertThat(desktopTaskChangeListener.isTaskPerceptible(task.taskId)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_PERCEPTIBLE_TASKS)
    @DesktopTest(cujs = ["b/429989932"])
    fun onTaskMovingToFront_freeformTask_enablePerceptibleTask() {
        val task = createFreeformTask(bounds = TASK_BOUNDS).apply { isVisible = true }
        desktopTaskChangeListener.onTaskMovingToFront(task)
        assertThat(desktopTaskChangeListener.isTaskPerceptible(task.taskId)).isTrue()
    }

    @Test
    fun onTaskMovingToFront_taskIsPinned_removeTaskFromRepo() {
        val task = createFreeformTask(bounds = TASK_BOUNDS).apply { isVisible = false }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        whenever(pinnedController.isPinned(task.taskId)).thenReturn(true)

        desktopTaskChangeListener.onTaskMovingToFront(task)

        verify(desktopUserRepositories.current).removeTask(task.taskId)
    }

    @Test
    fun onTaskMovingToBack_activeTaskInRepo_updatesTask() {
        val task = createFreeformTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)

        desktopTaskChangeListener.onTaskMovingToBack(task)

        verify(desktopUserRepositories.current)
            .updateTask(
                task.displayId,
                task.taskId,
                /* isVisible= */ false,
                task.configuration.windowConfiguration.bounds,
            )
    }

    @Test
    fun onTaskMovingToBack_nonActiveTaskInRepo_noop() {
        val task = createFreeformTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(false)

        desktopTaskChangeListener.onTaskMovingToBack(task)

        verify(desktopUserRepositories.current, never())
            .updateTask(
                task.displayId,
                task.taskId,
                /* isVisible= */ false,
                task.configuration.windowConfiguration.bounds,
            )
    }

    @Test
    fun onTaskMovingToBack_desktopModeNotSupportedInDisplay_noOp() {
        val task = createFreeformTask(UNSUPPORTED_DISPLAY_ID)
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)

        desktopTaskChangeListener.onTaskMovingToBack(task)

        verify(desktopUserRepositories.current, never())
            .updateTask(
                displayId = eq(UNSUPPORTED_DISPLAY_ID),
                taskId = any(),
                isVisible = any(),
                taskBounds = any(),
            )
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun onTaskClosing_backNavEnabled_removedFromRepo() {
        val task = createFreeformTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desktopUserRepositories.current.isClosingTask(task.taskId)).thenReturn(false)

        desktopTaskChangeListener.onTaskClosing(task)

        verify(desktopUserRepositories.current).removeTask(task.taskId)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun onTaskClosing_backNavEnabled_minimizedTask_notRemovedFromRepo() {
        val task = createFreeformTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desktopUserRepositories.current.isMinimizedTask(task.taskId)).thenReturn(true)

        desktopTaskChangeListener.onTaskClosing(task)

        verify(desktopUserRepositories.current)
            .updateTask(
                task.displayId,
                task.taskId,
                /* isVisible= */ false,
                task.configuration.windowConfiguration.bounds,
            )
        verify(desktopUserRepositories.current, never()).removeTask(task.taskId)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun onTaskClosing_backNavDisabled_closingTask_removesTaskInRepo() {
        val task = createFreeformTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desktopUserRepositories.current.isClosingTask(task.taskId)).thenReturn(true)

        desktopTaskChangeListener.onTaskClosing(task)

        verify(desktopUserRepositories.current, never()).minimizeTask(task.displayId, task.taskId)
        verify(desktopUserRepositories.current).removeClosingTask(task.taskId)
        verify(desktopUserRepositories.current).removeTask(task.taskId)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun onTaskClosing_backNavEnabled_closingTask_removesTaskFromRepo() {
        val task = createFreeformTask().apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desktopUserRepositories.current.isClosingTask(task.taskId)).thenReturn(true)

        desktopTaskChangeListener.onTaskClosing(task)

        verify(desktopUserRepositories.current).removeClosingTask(task.taskId)
        verify(desktopUserRepositories.current).removeTask(task.taskId)
    }

    @Test
    @EnableFlags(
        FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION,
    )
    fun onTaskClosing_desktopModeNotSupportedInDisplay_noOp() {
        val task = createFreeformTask(UNSUPPORTED_DISPLAY_ID).apply { isVisible = true }
        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desktopUserRepositories.current.isClosingTask(task.taskId)).thenReturn(true)

        desktopTaskChangeListener.onTaskClosing(task)

        verify(desktopUserRepositories.current, never()).removeClosingTask(task.taskId)
        verify(desktopUserRepositories.current, never()).removeTask(task.taskId)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION, FLAG_PERCEPTIBLE_TASKS)
    @DesktopTest(cujs = ["b/429989932"])
    fun onTaskClosing_backNavEnabled_disablePerceptibleTask() {
        val task = createFreeformTask().apply { isVisible = true }
        desktopTaskChangeListener.onTaskOpening(task)
        assertThat(desktopTaskChangeListener.isTaskPerceptible(task.taskId)).isTrue()

        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        desktopTaskChangeListener.onTaskClosing(task)
        assertThat(desktopTaskChangeListener.isTaskPerceptible(task.taskId)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION, FLAG_PERCEPTIBLE_TASKS)
    @DesktopTest(cujs = ["b/429989932"])
    fun onTaskClosing_backNavEnabled_minimizedTask_perceptibleTasks_noop() {
        val task = createFreeformTask().apply { isVisible = true }
        desktopTaskChangeListener.onTaskOpening(task)
        assertThat(desktopTaskChangeListener.isTaskPerceptible(task.taskId)).isTrue()

        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desktopUserRepositories.current.isMinimizedTask(task.taskId)).thenReturn(true)
        desktopTaskChangeListener.onTaskClosing(task)
        assertThat(desktopTaskChangeListener.isTaskPerceptible(task.taskId)).isTrue()
    }

    @Test
    @EnableFlags(FLAG_PERCEPTIBLE_TASKS)
    @DisableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    @DesktopTest(cujs = ["b/429989932"])
    fun onTaskClosing_backNavDisabled_closingTask_disablePerceptibleTask() {
        val task = createFreeformTask().apply { isVisible = true }
        desktopTaskChangeListener.onTaskOpening(task)
        assertThat(desktopTaskChangeListener.isTaskPerceptible(task.taskId)).isTrue()

        whenever(desktopUserRepositories.current.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desktopUserRepositories.current.isClosingTask(task.taskId)).thenReturn(true)
        desktopTaskChangeListener.onTaskClosing(task)
        assertThat(desktopTaskChangeListener.isTaskPerceptible(task.taskId)).isFalse()
    }

    @Test
    fun onNonTransitionTaskClosing_invisibleFreeformTask_removesTaskFromRepo() {
        val task = createFreeformTask().apply { isVisible = false }
        desktopTaskChangeListener.onTaskOpening(task)

        whenever(desktopUserRepositories.current.isVisibleTask(task.taskId)).thenReturn(false)
        desktopTaskChangeListener.onNonTransitionTaskClosing(task)

        verify(desktopUserRepositories.current, times(1)).removeClosingTask(task.taskId)
        verify(desktopUserRepositories.current, times(1)).removeTask(task.taskId)
    }

    private fun createWallpaperTaskInfo(windowingMode: Int): RunningTaskInfo =
        TestRunningTaskInfoBuilder()
            .setBaseIntent(
                Intent().apply { component = DesktopWallpaperActivity.wallpaperActivityComponent }
            )
            .setToken(MockToken().token())
            .setWindowingMode(windowingMode)
            .build()

    companion object {
        private const val UNSUPPORTED_DISPLAY_ID = 3
        private val TASK_BOUNDS = Rect(100, 100, 300, 300)
        private const val DEFAULT_USER_ID = 1000
        private const val ACTIVE_DESK_ID = 1
    }
}
