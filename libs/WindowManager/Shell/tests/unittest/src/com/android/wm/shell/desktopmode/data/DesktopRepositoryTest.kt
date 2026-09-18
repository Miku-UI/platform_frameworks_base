/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.desktopmode.data

import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.testing.AndroidTestingRunner
import android.util.ArrayMap
import android.util.ArraySet
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.data.DesktopRepository.Companion.INVALID_DESK_ID
import com.android.wm.shell.desktopmode.data.persistence.Desktop
import com.android.wm.shell.desktopmode.data.persistence.DesktopPersistentRepository
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import junit.framework.Assert.fail
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

/**
 * Tests for [@link DesktopRepository].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopRepositoryTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
class DesktopRepositoryTest : ShellTestCase() {

    private lateinit var repo: DesktopRepository
    private lateinit var shellInit: ShellInit
    private lateinit var datastoreScope: CoroutineScope
    private lateinit var bgScope: TestScope
    @Mock private lateinit var testExecutor: ShellExecutor
    @Mock private lateinit var persistentRepository: DesktopPersistentRepository

    private val desktopConfig = FakeDesktopConfig()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        datastoreScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        bgScope = TestScope()
        shellInit = spy(ShellInit(testExecutor))

        repo =
            spy(
                DesktopRepository(
                    persistentRepository,
                    datastoreScope,
                    bgScope,
                    DEFAULT_USER_ID,
                    desktopConfig,
                )
            )
        whenever(runBlocking { persistentRepository.readDesktop(any(), any()) })
            .thenReturn(Desktop.getDefaultInstance())
        shellInit.init()
        repo.addDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = DEFAULT_DESKTOP_ID,
            uniqueDisplayId = UNIQUE_DISPLAY_ID,
        )
        repo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = DEFAULT_DESKTOP_ID)
    }

    @After
    fun tearDown() {
        datastoreScope.cancel()
        bgScope.cancel()
    }

    @Test
    fun addTask_notifiesActiveTaskListener() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)

        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(listener.activeChangesOnDefaultDisplay).isEqualTo(1)
    }

    @Test
    fun addTask_marksTaskActive() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)

        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(repo.isActiveTask(1)).isTrue()
    }

    @Test
    fun addSameTaskTwice_notifiesOnce() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)

        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(listener.activeChangesOnDefaultDisplay).isEqualTo(1)
    }

    @Test
    fun addTask_multipleTasksAdded_notifiesForAllTasks() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)

        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, taskId = 2, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(listener.activeChangesOnDefaultDisplay).isEqualTo(2)
    }

    @Test
    fun addTask_multipleDisplays_notifiesCorrectListener() {
        repo.addDesk(
            displayId = SECOND_DISPLAY,
            deskId = SECOND_DISPLAY,
            uniqueDisplayId = UNIQUE_DISPLAY_ID,
        )
        val listener = TestListener()
        repo.addActiveTaskListener(listener)

        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, taskId = 2, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(SECOND_DISPLAY, taskId = 3, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(listener.activeChangesOnDefaultDisplay).isEqualTo(2)
        assertThat(listener.activeChangesOnSecondaryDisplay).isEqualTo(1)
    }

    @Test
    fun addTask_multipleDisplays_moveToAnotherDisplay() {
        repo.addDesk(
            displayId = SECOND_DISPLAY,
            deskId = SECOND_DISPLAY,
            uniqueDisplayId = UNIQUE_DISPLAY_ID,
        )
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(SECOND_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        assertThat(repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)).isEmpty()
        assertThat(repo.getFreeformTasksInZOrder(SECOND_DISPLAY)).containsExactly(1)
    }

    @Test
    fun addTask_withSavedBounds_updatesDeskObject() {
        val taskId = 1
        // Create a freeform task to and add it to the desk first.
        repo.addTask(DEFAULT_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        // Assume the user adjusts the bounds of the task.
        val boundsBeforeSnapOrMaximize = Rect(100, 200, 300, 400)

        // Then, user snaps or maximizes this task.
        repo.saveBoundsBeforeSnapOrMaximize(taskId, boundsBeforeSnapOrMaximize)

        // Calling addTask again will call updateTaskInDesk, which should copy the previous
        // bounds to the desk object.
        // taskBounds parameter below can be seen as the current bounds of the task in its maximized
        // or snapped state.
        repo.addTask(DEFAULT_DISPLAY, taskId, isVisible = true, taskBounds = Rect(0, 0, 1280, 800))

        val desk = repo.getAllDesks().find { it.deskId == DEFAULT_DESKTOP_ID }
        assertNotNull(desk)
        assertThat(desk.boundsBeforeSnapOrMaximizeByTaskId[taskId])
            .isEqualTo(boundsBeforeSnapOrMaximize)
    }

    @Test
    fun addTask_deskDoesNotExist_throws() {
        repo.removeDesk(deskId = 0)

        assertThrows(Exception::class.java) {
            repo.addTask(
                displayId = DEFAULT_DISPLAY,
                taskId = 5,
                isVisible = true,
                taskBounds = TEST_TASK_BOUNDS,
            )
        }
    }

    @Test
    fun addTaskToDesk_deskDoesNotExist_throws() {
        repo.removeDesk(deskId = 2)

        assertThrows(Exception::class.java) {
            repo.addTaskToDesk(
                displayId = DEFAULT_DISPLAY,
                deskId = 2,
                taskId = 4,
                isVisible = true,
                taskBounds = TEST_TASK_BOUNDS,
            )
        }
    }

    @Test
    fun addTaskToDesk_addsToZOrderList() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 2)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 3)
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 2,
            taskId = 5,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 2,
            taskId = 6,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 2,
            taskId = 7,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 3,
            taskId = 8,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        val orderedTasks = repo.getFreeformTasksIdsInDeskInZOrder(deskId = 2)
        assertThat(orderedTasks[0]).isEqualTo(7)
        assertThat(orderedTasks[1]).isEqualTo(6)
        assertThat(orderedTasks[2]).isEqualTo(5)
    }

    @Test
    fun addTaskToDesk_visible_addsToVisible() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 2)

        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 2,
            taskId = 5,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.isVisibleTask(5)).isTrue()
    }

    @Test
    fun addTaskToDesk_removesFromAllOtherDesks() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 2)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 3)
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 2,
            taskId = 7,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 3,
            taskId = 7,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getActiveTaskIdsInDesk(2)).doesNotContain(7)
    }

    @Test
    fun addTaskToDesk_notifiesTaskAppearingInDeskListener() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 5)
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)

        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 5,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        val lastAppearance = assertNotNull(listener.lastTaskAppearingInDesk)
        assertThat(lastAppearance.taskId).isEqualTo(1)
        assertThat(lastAppearance.displayId).isEqualTo(DEFAULT_DISPLAY)
        assertThat(lastAppearance.deskId).isEqualTo(5)
    }

    @Test
    fun removeActiveTask_notifiesActiveTaskListener() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeActiveTask(1)

        // Notify once for add and once for remove
        assertThat(listener.activeChangesOnDefaultDisplay).isEqualTo(2)
    }

    @Test
    fun removeActiveTask_marksTaskNotActive() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeActiveTask(1)

        assertThat(repo.isActiveTask(1)).isFalse()
    }

    @Test
    fun removeActiveTask_nonExistingTask_doesNotNotify() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)

        repo.removeActiveTask(99)

        assertThat(listener.activeChangesOnDefaultDisplay).isEqualTo(0)
    }

    @Test
    fun remoteActiveTask_listenerForOtherDisplayNotNotified() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeActiveTask(1)

        assertThat(listener.activeChangesOnSecondaryDisplay).isEqualTo(0)
        assertThat(repo.isActiveTask(1)).isFalse()
    }

    @Test
    fun removeActiveTask_excludingDesk_leavesTaskInDesk() {
        repo.addDesk(displayId = 2, deskId = 11)
        repo.addDesk(displayId = 3, deskId = 12)
        repo.addTaskToDesk(
            displayId = 3,
            deskId = 12,
            taskId = 100,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.removeActiveTask(taskId = 100, excludedDeskId = 12)

        assertThat(repo.getActiveTaskIdsInDesk(12)).contains(100)
    }

    @Test
    fun isActiveTask_nonExistingTask_returnsFalse() {
        assertThat(repo.isActiveTask(99)).isFalse()
    }

    @Test
    fun isOnlyVisibleNonClosingTask_noTasks_returnsFalse() {
        // No visible tasks
        assertThat(repo.isOnlyVisibleNonClosingTask(1)).isFalse()
    }

    @Test
    fun isClosingTask_noTasks_returnsFalse() {
        // No visible tasks
        assertThat(repo.isClosingTask(1)).isFalse()
    }

    @Test
    fun updateTask_singleVisibleNonClosingTask_updatesTasksCorrectly() {
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.isVisibleTask(1)).isTrue()
        assertThat(repo.isClosingTask(1)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(1)).isTrue()

        assertThat(repo.isVisibleTask(99)).isFalse()
        assertThat(repo.isClosingTask(99)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(99)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun updateTaskVisibility_multipleTasks_persistsAllOfRepository() =
        runTest(StandardTestDispatcher()) {
            repo.updateTask(
                DEFAULT_DISPLAY,
                taskId = 1,
                isVisible = true,
                taskBounds = TEST_TASK_BOUNDS,
            )
            val expectedDesks1 = repo.getAllDesks().map { it.deepCopy() }

            repo.updateTask(
                DEFAULT_DISPLAY,
                taskId = 2,
                isVisible = true,
                taskBounds = TEST_TASK_BOUNDS,
            )
            bgScope.testScheduler.advanceUntilIdle()
            val expectedDesks2 = repo.getAllDesks()

            inOrder(persistentRepository).run {
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesks1,
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesks2,
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
            }
        }

    @Test
    fun isOnlyVisibleNonClosingTask_singleVisibleClosingTask() {
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addClosingTask(displayId = DEFAULT_DISPLAY, deskId = 0, taskId = 1)

        // A visible task that's closing
        assertThat(repo.isVisibleTask(1)).isTrue()
        assertThat(repo.isClosingTask(1)).isTrue()
        assertThat(repo.isOnlyVisibleNonClosingTask(1)).isFalse()
        // Not a visible task
        assertThat(repo.isVisibleTask(99)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(99)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun leftTiledTask_addAndRemove_updatedInRepoAndPersisted() {
        runTest(StandardTestDispatcher()) {
            repo.addLeftTiledTaskToDesk(
                displayId = DEFAULT_DISPLAY,
                taskId = 1,
                deskId = DEFAULT_DESKTOP_ID,
            )

            val expectedDesksAfterAdding = repo.getAllDesks().map { it.deepCopy() }
            assertThat(repo.getLeftTiledTask(deskId = DEFAULT_DESKTOP_ID)).isEqualTo(1)

            repo.removeLeftTiledTaskFromDesk(
                displayId = DEFAULT_DISPLAY,
                deskId = DEFAULT_DESKTOP_ID,
            )
            bgScope.testScheduler.advanceUntilIdle()

            val expectedDesksAfterRemoval = repo.getAllDesks().map { it.deepCopy() }
            assertThat(repo.getLeftTiledTask(deskId = DEFAULT_DESKTOP_ID)).isNull()
            inOrder(persistentRepository).run {
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksAfterAdding,
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksAfterRemoval,
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun rightTiledTask_addAndRemove_updatedInRepoAndPersisted() {
        runTest(StandardTestDispatcher()) {
            repo.addRightTiledTaskToDesk(
                displayId = DEFAULT_DISPLAY,
                taskId = 1,
                deskId = DEFAULT_DESKTOP_ID,
            )

            val expectedDesksAfterAdding = repo.getAllDesks().map { it.deepCopy() }
            assertThat(repo.getRightTiledTask(deskId = DEFAULT_DESKTOP_ID)).isEqualTo(1)

            repo.removeRightTiledTaskFromDesk(
                displayId = DEFAULT_DISPLAY,
                deskId = DEFAULT_DESKTOP_ID,
            )
            bgScope.testScheduler.advanceUntilIdle()

            val expectedDesksAfterRemoval = repo.getAllDesks().map { it.deepCopy() }
            assertThat(repo.getRightTiledTask(deskId = DEFAULT_DESKTOP_ID)).isNull()
            inOrder(persistentRepository).run {
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksAfterAdding,
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksAfterRemoval,
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
            }
        }
    }

    @Test
    fun isOnlyVisibleNonClosingTask_singleVisibleMinimizedTask() {
        val taskId = 1
        repo.addTask(DEFAULT_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.minimizeTask(DEFAULT_DISPLAY, taskId)

        // The visible task that's closing
        assertThat(repo.isVisibleTask(taskId)).isFalse()
        assertThat(repo.isMinimizedTask(taskId)).isTrue()
        assertThat(repo.isOnlyVisibleNonClosingTask(taskId)).isFalse()
        // Not a visible task
        assertThat(repo.isVisibleTask(99)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(99)).isFalse()
    }

    @Test
    fun isOnlyVisibleNonClosingTask_multipleVisibleNonClosingTasks() {
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        // Not the only task
        assertThat(repo.isVisibleTask(1)).isTrue()
        assertThat(repo.isClosingTask(1)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(1)).isFalse()

        // Not the only task
        assertThat(repo.isVisibleTask(2)).isTrue()
        assertThat(repo.isClosingTask(2)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(2)).isFalse()

        // Not a visible task
        assertThat(repo.isVisibleTask(99)).isFalse()
        assertThat(repo.isClosingTask(99)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(99)).isFalse()
    }

    @Test
    fun isOnlyVisibleNonClosingTask_multipleDisplays() {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.setActiveDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.updateTask(SECOND_DISPLAY, taskId = 3, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        // Not the only task on DEFAULT_DISPLAY
        assertThat(repo.isVisibleTask(1)).isTrue()
        assertThat(repo.isOnlyVisibleNonClosingTask(1, DEFAULT_DISPLAY)).isFalse()
        // Not the only task on DEFAULT_DISPLAY
        assertThat(repo.isVisibleTask(2)).isTrue()
        assertThat(repo.isOnlyVisibleNonClosingTask(2, DEFAULT_DISPLAY)).isFalse()
        // The only visible task on SECOND_DISPLAY
        assertThat(repo.isVisibleTask(3)).isTrue()
        assertThat(repo.isOnlyVisibleNonClosingTask(3, SECOND_DISPLAY)).isTrue()
        // Not a visible task
        assertThat(repo.isVisibleTask(99)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(99)).isFalse()
    }

    @Test
    fun addVisibleTasksListener_notifiesVisibleFreeformTask() {
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()

        repo.addVisibleTasksListener(listener, executor)
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(1)
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(1)
    }

    @Test
    fun addListener_tasksOnDifferentDisplay_doesNotNotify() {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.updateTask(SECOND_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()
        repo.addVisibleTasksListener(listener, executor)
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(0)
        // One call as adding listener notifies it
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(1)
    }

    @Test
    fun updateTask_visible_addVisibleTasksNotifiesListener() {
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()
        repo.addVisibleTasksListener(listener, executor)

        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(2)
        // 1 from registration, 2 for the updates.
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(3)
    }

    @Test
    fun updateTask_visibleTask_addVisibleTaskNotifiesListenerForThatDisplay() {
        repo.addDesk(displayId = 1, deskId = 1)
        repo.setActiveDesk(displayId = 1, deskId = 1)
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()
        repo.addVisibleTasksListener(listener, executor)

        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(1)
        // 1 for the registration, 1 for the update.
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(2)
        assertThat(listener.visibleTasksCountOnSecondaryDisplay).isEqualTo(0)
        // 1 for the registration.
        assertThat(listener.visibleChangesOnSecondaryDisplay).isEqualTo(1)

        repo.updateTask(displayId = 1, taskId = 2, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        executor.flushAll()

        // Listener for secondary display is notified
        assertThat(listener.visibleTasksCountOnSecondaryDisplay).isEqualTo(1)
        // 1 for the registration, 1 for the update.
        assertThat(listener.visibleChangesOnSecondaryDisplay).isEqualTo(2)
        // No changes to listener for default display
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(2)
    }

    @Test
    fun updateTask_taskOnDefaultBecomesVisibleOnSecondDisplay_listenersNotified() {
        repo.addDesk(displayId = 1, deskId = 1)
        repo.setActiveDesk(displayId = 1, deskId = 1)
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()
        repo.addVisibleTasksListener(listener, executor)

        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()
        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(1)

        // Mark task 1 visible on secondary display
        repo.updateTask(displayId = 1, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        executor.flushAll()

        // Default display should have 3 calls
        // 1 - listener registered
        // 2 - visible task added
        // 3 - visible task removed
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(3)
        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(0)

        // Secondary display should have 2 calls for registration + visible task added
        assertThat(listener.visibleChangesOnSecondaryDisplay).isEqualTo(2)
        assertThat(listener.visibleTasksCountOnSecondaryDisplay).isEqualTo(1)
    }

    @Test
    fun updateTask_removeVisibleTasksNotifiesListener() {
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()
        repo.addVisibleTasksListener(listener, executor)
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(2)

        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(4)

        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(0)
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(5)
    }

    /**
     * When a task vanishes, the displayId of the task is set to INVALID_DISPLAY. This tests that
     * task is removed from the last parent display when it vanishes.
     */
    @Test
    fun updateTask_removeVisibleTasksRemovesTaskWithInvalidDisplay() {
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()
        repo.addVisibleTasksListener(listener, executor)
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, taskId = 2, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(2)

        repo.updateTask(
            INVALID_DISPLAY,
            taskId = 1,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        // 1 from registering, 1x3 for each update including the one to the invalid display.
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(4)
        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(1)
    }

    @Test
    fun getVisibleTaskCount_defaultDisplay_returnsCorrectCount() {
        // No tasks, count is 0
        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)

        // New task increments count to 1
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(1)

        // Visibility update to same task does not increase count
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(1)

        // Second task visible increments count
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(2)

        // Hiding a task decrements count
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )
        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(1)

        // Hiding all tasks leaves count at 0
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )
        assertThat(repo.getVisibleTaskCount(displayId = 9)).isEqualTo(0)

        // Hiding a not existing task, count remains at 0
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 999,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )
        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)
    }

    @Test
    fun getVisibleTaskCount_multipleDisplays_returnsCorrectCount() {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.setActiveDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)
        assertThat(repo.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(0)

        // New task on default display increments count for that display only
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(1)
        assertThat(repo.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(0)

        // New task on secondary display, increments count for that display only
        repo.updateTask(SECOND_DISPLAY, taskId = 2, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(1)
        assertThat(repo.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(1)

        // Marking task visible on another display, updates counts for both displays
        repo.updateTask(SECOND_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)
        assertThat(repo.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(2)

        // Marking task that is on secondary display, hidden on default display, does not affect
        // secondary display
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)
        assertThat(repo.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(2)

        // Hiding a task on that display, decrements count
        repo.updateTask(
            SECOND_DISPLAY,
            taskId = 1,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)
        assertThat(repo.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(1)
    }

    @Test
    fun addTask_didNotExist_addsToTop() {
        repo.addTask(DEFAULT_DISPLAY, 5, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 7, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
        assertThat(tasks.size).isEqualTo(3)
        assertThat(tasks[0]).isEqualTo(7)
        assertThat(tasks[1]).isEqualTo(6)
        assertThat(tasks[2]).isEqualTo(5)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun addTask_noTaskExists_persistenceEnabled_addsTasksToTop() =
        runTest(StandardTestDispatcher()) {
            val taskIdsToAdd = listOf(5, 6, 7)
            val expectedDesksInOrder =
                taskIdsToAdd.map { taskId ->
                    repo.addTask(
                        DEFAULT_DISPLAY,
                        taskId,
                        isVisible = true,
                        taskBounds = TEST_TASK_BOUNDS,
                    )
                    repo.getAllDesks().map { it.deepCopy() }
                }
            bgScope.testScheduler.advanceUntilIdle()

            val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
            assertThat(tasks).containsExactly(7, 6, 5).inOrder()
            inOrder(persistentRepository).run {
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksInOrder[0],
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksInOrder[1],
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksInOrder[2],
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
            }
        }

    @Test
    fun addTask_alreadyExists_movesToTop() {
        repo.addTask(DEFAULT_DISPLAY, 5, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 7, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.addTask(DEFAULT_DISPLAY, 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
        assertThat(tasks.size).isEqualTo(3)
        assertThat(tasks.first()).isEqualTo(6)
    }

    @Test
    fun addTask_taskIsMinimized_unminimizesTask() {
        repo.addTask(DEFAULT_DISPLAY, 5, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 7, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.minimizeTask(displayId = 0, taskId = 6)

        val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
        assertThat(tasks).containsExactly(7, 6, 5).inOrder()
        assertThat(repo.isMinimizedTask(taskId = 6)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun minimizeTask_repositoryBasedPersistenceEnabled_taskIsPersistedAsMinimized() =
        runTest(StandardTestDispatcher()) {
            val taskIdsToAdd = listOf(5, 6, 7)
            var expectedDesksInOrder =
                taskIdsToAdd
                    .map { taskId ->
                        repo.addTask(
                            DEFAULT_DISPLAY,
                            taskId,
                            isVisible = true,
                            taskBounds = TEST_TASK_BOUNDS,
                        )
                        repo.getAllDesks().map { it.deepCopy() }
                    }
                    .toMutableList()

            repo.minimizeTask(displayId = 0, taskId = 6)
            bgScope.testScheduler.advanceUntilIdle()

            val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
            assertThat(tasks).containsExactly(7, 6, 5).inOrder()
            assertThat(repo.isMinimizedTask(taskId = 6)).isTrue()

            expectedDesksInOrder.add(repo.getAllDesks().map { it.deepCopy() })
            inOrder(persistentRepository).run {
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksInOrder[0],
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksInOrder[1],
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksInOrder[2],
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
                // Triggers once for updateTask and once for minimize task
                verify(persistentRepository, times(2))
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksInOrder[3],
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
            }
        }

    @Test
    fun addTask_taskIsUnminimized_noop() {
        repo.addTask(DEFAULT_DISPLAY, 5, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 7, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
        assertThat(tasks).containsExactly(7, 6, 5).inOrder()
        assertThat(repo.isMinimizedTask(taskId = 6)).isFalse()
    }

    @Test
    fun removeTask_invalidDisplay_removesTaskFromFreeformTasks() {
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeTask(taskId = 1)

        val validDisplayTasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
        assertThat(validDisplayTasks).isEmpty()
    }

    @Test
    fun removeTask_validDisplay_removesTaskFromFreeformTasks() {
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeTask(taskId = 1)

        val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
        assertThat(tasks).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun removeTask_validDisplay_repositoryBasedPersistenceEnabled_removesTaskFromFreeformTasks() {
        runTest(StandardTestDispatcher()) {
            repo.addTask(
                DEFAULT_DISPLAY,
                taskId = 1,
                isVisible = true,
                taskBounds = TEST_TASK_BOUNDS,
            )
            val expectedDesksAfterAddingTask = repo.getAllDesks().map { it.deepCopy() }

            repo.removeTask(taskId = 1)
            val expectedDesksAfterRemovingTask = repo.getAllDesks().map { it.deepCopy() }
            bgScope.testScheduler.advanceUntilIdle()

            inOrder(persistentRepository).run {
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksAfterAddingTask,
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
                verify(persistentRepository)
                    .addOrUpdateRepository(
                        DEFAULT_USER_ID,
                        expectedDesksAfterRemovingTask,
                        mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                        ArrayMap(),
                        ArrayMap(),
                    )
            }
        }
    }

    @Test
    fun removeTask_removesTaskBoundsBeforeSnapOrMaximize() {

        val taskId = 1
        // Create a freeform task to and add it to the desk first.
        repo.addTask(DEFAULT_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        // Assume the user adjusts the bounds of the task.
        val boundsBeforeSnapOrMaximize = Rect(100, 200, 300, 400)

        // Then, user snaps or maximizes this task.
        repo.saveBoundsBeforeSnapOrMaximize(taskId, boundsBeforeSnapOrMaximize)

        // Calling addTask again will call updateTaskInDesk, which should copy the previous
        // bounds to the desk object.
        // taskBounds parameter below can be seen as the current bounds of the task in its maximized
        // or snapped state.
        repo.addTask(DEFAULT_DISPLAY, taskId, isVisible = true, taskBounds = Rect(0, 0, 1280, 800))

        // Task is removed due to drag-exit or other reasons like closing the app.
        repo.removeTask(taskId)

        // Verify bounds are removed from repository's sparse array
        // (boundsBeforeSnapOrMaximizeByTaskId)
        assertThat(repo.removeBoundsBeforeSnapOrMaximize(taskId)).isNull()

        // Verify bounds are removed from desk object as well
        val desk = repo.getAllDesks().find { it.deskId == DEFAULT_DESKTOP_ID }
        assertNotNull(desk)
        assertThat(desk.boundsBeforeSnapOrMaximizeByTaskId.containsKey(taskId)).isFalse()
    }

    @Test
    fun removeTask_removesTaskBoundsBeforeImmersive() {
        val taskId = 1
        repo.addTask(DEFAULT_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.saveBoundsBeforeFullImmersive(taskId, Rect(0, 0, 200, 200))

        repo.removeTask(taskId)

        assertThat(repo.removeBoundsBeforeFullImmersive(taskId)).isNull()
    }

    @Test
    fun removeTask_removesActiveTask() {
        repo.addDesk(THIRD_DISPLAY, THIRD_DISPLAY)
        val taskId = 1
        val listener = TestListener()
        repo.addActiveTaskListener(listener)
        repo.addTask(THIRD_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeTask(taskId)

        assertThat(repo.isActiveTask(taskId)).isFalse()
        assertThat(listener.activeChangesOnThirdDisplay).isEqualTo(2)
    }

    @Test
    fun removeTask_unminimizesTask() {
        val taskId = 1
        repo.addTask(DEFAULT_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.minimizeTask(DEFAULT_DISPLAY, taskId)

        repo.removeTask(taskId)

        assertThat(repo.isMinimizedTask(taskId)).isFalse()
    }

    @Test
    fun removeTask_updatesTaskVisibility() {
        repo.addDesk(displayId = THIRD_DISPLAY, deskId = THIRD_DISPLAY)
        val taskId = 1
        repo.addTask(THIRD_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeTask(taskId)

        assertThat(repo.isVisibleTask(taskId)).isFalse()
    }

    @Test
    fun saveBoundsBeforeSnapOrMaximize_boundsSavedByTaskId() {
        val taskId = 1
        val bounds = Rect(0, 0, 200, 200)

        repo.saveBoundsBeforeSnapOrMaximize(taskId, bounds)

        assertThat(repo.removeBoundsBeforeSnapOrMaximize(taskId)).isEqualTo(bounds)
    }

    @Test
    fun saveBoundsBeforeSnapOrMaximize_alreadyExists_doesNotOverwrite() {
        val taskId = 1
        val initialBounds = Rect(0, 0, 200, 200)
        val newBounds = Rect(10, 10, 300, 300)

        // Save initial bounds
        repo.saveBoundsBeforeSnapOrMaximize(taskId, initialBounds)
        // Attempt to save new bounds, which should be ignored
        repo.saveBoundsBeforeSnapOrMaximize(taskId, newBounds)

        // Verify that the initial bounds are still the ones stored
        assertThat(repo.removeBoundsBeforeSnapOrMaximize(taskId)).isEqualTo(initialBounds)
    }

    @Test
    fun removeBoundsBeforeSnapOrMaximize_returnsNullAfterBoundsRemoved() {
        val taskId = 1
        val bounds = Rect(0, 0, 200, 200)
        repo.saveBoundsBeforeSnapOrMaximize(taskId, bounds)
        repo.removeBoundsBeforeSnapOrMaximize(taskId)

        val boundsBeforeMaximize = repo.removeBoundsBeforeSnapOrMaximize(taskId)

        assertThat(boundsBeforeMaximize).isNull()
    }

    @Test
    fun saveBoundsBeforeImmersive_boundsSavedByTaskId() {
        val taskId = 1
        val bounds = Rect(0, 0, 200, 200)

        repo.saveBoundsBeforeFullImmersive(taskId, bounds)

        assertThat(repo.removeBoundsBeforeFullImmersive(taskId)).isEqualTo(bounds)
    }

    @Test
    fun removeBoundsBeforeImmersive_returnsNullAfterBoundsRemoved() {
        val taskId = 1
        val bounds = Rect(0, 0, 200, 200)
        repo.saveBoundsBeforeFullImmersive(taskId, bounds)
        repo.removeBoundsBeforeFullImmersive(taskId)

        val boundsBeforeImmersive = repo.removeBoundsBeforeFullImmersive(taskId)

        assertThat(boundsBeforeImmersive).isNull()
    }

    @Test
    fun isMinimizedTask_minimizeTaskNotCalled_noTasksMinimized() {
        assertThat(repo.isMinimizedTask(taskId = 0)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 1)).isFalse()
    }

    @Test
    fun minimizeTask_minimizesCorrectTask() {
        repo.minimizeTask(displayId = 0, taskId = 0)

        assertThat(repo.isMinimizedTask(taskId = 0)).isTrue()
        assertThat(repo.isMinimizedTask(taskId = 1)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 2)).isFalse()
    }

    @Test
    fun minimizeTask_withInvalidDisplay_minimizesCorrectTask() {
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 0,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.minimizeTask(displayId = INVALID_DISPLAY, taskId = 0)

        assertThat(repo.isMinimizedTask(taskId = 0)).isTrue()
        assertThat(repo.isMinimizedTask(taskId = 1)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 2)).isFalse()
    }

    @Test
    fun unminimizeTask_unminimizesTask() {
        repo.minimizeTask(displayId = 0, taskId = 0)

        repo.unminimizeTask(displayId = 0, taskId = 0)

        assertThat(repo.isMinimizedTask(taskId = 0)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 1)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 2)).isFalse()
    }

    @Test
    fun unminimizeTask_nonExistentTask_doesntCrash() {
        repo.unminimizeTask(displayId = 0, taskId = 0)

        // No change
        assertThat(repo.isMinimizedTask(taskId = 0)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 1)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 2)).isFalse()
    }

    @Test
    fun updateTask_minimizedTaskBecomesVisible_unminimizesTask() {
        repo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = 2)
        repo.updateTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        val isMinimizedTask = repo.isMinimizedTask(taskId = 2)

        assertThat(isMinimizedTask).isFalse()
    }

    @Test
    fun saveBoundsBeforeMinimize_boundsSavedByTaskId() {
        val taskId = 1
        val bounds = Rect(0, 0, 200, 200)

        repo.saveBoundsBeforeMinimize(taskId, bounds)

        assertThat(repo.removeBoundsBeforeMinimize(taskId)).isEqualTo(bounds)
    }

    @Test
    fun removeBoundsBeforeMinimize_returnsNullAfterBoundsRemoved() {
        val taskId = 1
        val bounds = Rect(0, 0, 200, 200)
        repo.saveBoundsBeforeMinimize(taskId, bounds)
        repo.removeBoundsBeforeMinimize(taskId)

        val boundsBeforeMinimize = repo.removeBoundsBeforeMinimize(taskId)

        assertThat(boundsBeforeMinimize).isNull()
    }

    @Test
    fun getExpandedTasksOrdered_returnsFreeformTasksInCorrectOrder() {
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 3,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        val tasks = repo.getExpandedTasksOrdered(displayId = 0)

        assertThat(tasks).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun getExpandedTasksOrdered_excludesMinimizedTasks() {
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 3,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = 2)

        val tasks = repo.getExpandedTasksOrdered(displayId = DEFAULT_DISPLAY)

        assertThat(tasks).containsExactly(1, 3).inOrder()
    }

    @Test
    fun setTaskAsTopTransparentFullscreenTaskData_savesTaskData() {
        val taskInfo =
            TestRunningTaskInfoBuilder().setTaskId(1).setToken(MockToken().token()).build()
        repo.setTopTransparentFullscreenTaskData(DEFAULT_DESKTOP_ID, taskInfo)
        val topTransparentTaskData = repo.getTopTransparentFullscreenTaskData(DEFAULT_DESKTOP_ID)

        assertThat(topTransparentTaskData).isNotNull()
        assertThat(topTransparentTaskData!!.taskId).isEqualTo(taskInfo.taskId)
        assertThat(topTransparentTaskData.token).isEqualTo(taskInfo.token)
    }

    @Test
    fun clearTaskAsTopTransparentFullscreenTask_clearsTask() {
        val taskInfo =
            TestRunningTaskInfoBuilder().setTaskId(1).setToken(MockToken().token()).build()
        repo.setTopTransparentFullscreenTaskData(DEFAULT_DESKTOP_ID, taskInfo)
        repo.clearTopTransparentFullscreenTaskData(DEFAULT_DESKTOP_ID)

        assertThat(repo.getTopTransparentFullscreenTaskData(DEFAULT_DESKTOP_ID)).isNull()
    }

    @Test
    fun setTaskInFullImmersiveState_savedAsInImmersiveState() {
        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isFalse()

        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 1, immersive = true)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isTrue()
    }

    @Test
    fun removeTaskInFullImmersiveState_removedAsInImmersiveState() {
        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 1, immersive = true)
        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isTrue()

        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 1, immersive = false)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isFalse()
    }

    @Test
    fun setTaskInFullImmersiveState_inDesk_savedAsInImmersiveState() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        assertThat(repo.isTaskInFullImmersiveState(6)).isFalse()

        repo.setTaskInFullImmersiveStateInDesk(deskId = 6, taskId = 10, immersive = true)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 10)).isTrue()
    }

    @Test
    fun removeTaskInFullImmersiveState_inDesk_removedAsInImmersiveState() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.setTaskInFullImmersiveStateInDesk(deskId = 6, taskId = 10, immersive = true)

        repo.setTaskInFullImmersiveStateInDesk(deskId = 6, taskId = 10, immersive = false)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 10)).isFalse()
    }

    @Test
    fun removeTaskInFullImmersiveState_otherWasImmersive_otherRemainsImmersive() {
        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 1, immersive = true)

        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 2, immersive = false)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isTrue()
    }

    @Test
    fun setTaskInFullImmersiveState_sameDisplay_overridesExistingFullImmersiveTask() {
        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 1, immersive = true)
        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 2, immersive = true)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isFalse()
        assertThat(repo.isTaskInFullImmersiveState(taskId = 2)).isTrue()
    }

    @Test
    fun setTaskInFullImmersiveState_differentDisplay_bothAreImmersive() {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.setActiveDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 1, immersive = true)
        repo.setTaskInFullImmersiveState(SECOND_DISPLAY, taskId = 2, immersive = true)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isTrue()
        assertThat(repo.isTaskInFullImmersiveState(taskId = 2)).isTrue()
    }

    @Test
    fun removeDesk_multipleTasks_removesAll() {
        // The front-most task will be the one added last through `addTask`.
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 3,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = 2)

        val tasksBeforeRemoval = repo.removeDesk(deskId = DEFAULT_DISPLAY)

        verify(repo, times(1)).notifyVisibleTaskListeners(DEFAULT_DISPLAY, visibleTasksCount = 0)
        assertThat(tasksBeforeRemoval).containsExactly(1, 2, 3).inOrder()
        assertThat(repo.getActiveTasks(displayId = DEFAULT_DISPLAY)).isEmpty()
    }

    @Test
    fun removeDesk_multipleDesks_active_removes() {
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)
        repo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        repo.removeDesk(deskId = 3)

        verify(repo, times(1)).notifyVisibleTaskListeners(DEFAULT_DISPLAY, visibleTasksCount = 0)
        assertThat(repo.getDeskIds(displayId = DEFAULT_DISPLAY)).doesNotContain(3)
    }

    @Test
    fun removeDesk_multipleDesks_active_marksInactiveInDisplay() {
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)
        repo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        repo.removeDesk(deskId = 3)

        assertThat(repo.getActiveDeskId(displayId = DEFAULT_DISPLAY)).isNull()
    }

    @Test
    fun removeDesk_multipleDesks_inactive_removes() {
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)
        repo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        repo.removeDesk(deskId = 2)

        verify(repo, times(1)).notifyVisibleTaskListeners(DEFAULT_DISPLAY, visibleTasksCount = 0)
        assertThat(repo.getDeskIds(displayId = DEFAULT_DISPLAY)).doesNotContain(2)
    }

    @Test
    fun removeDesk_multipleDesks_inactive_keepsOtherDeskActiveInDisplay() {
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)
        repo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        repo.removeDesk(deskId = 2)

        assertThat(repo.getActiveDeskId(displayId = DEFAULT_DISPLAY)).isEqualTo(3)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun removeDesk_repositoryBasedPersistenceEnabled_removesFromPersistence() =
        runTest(StandardTestDispatcher()) {
            repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)

            repo.removeDesk(deskId = 2)
            bgScope.testScheduler.advanceUntilIdle()
            val expectedDesksAfterRemovingDesk = repo.getAllDesks().map { it.deepCopy() }

            verify(persistentRepository)
                .addOrUpdateRepository(
                    DEFAULT_USER_ID,
                    expectedDesksAfterRemovingDesk,
                    mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                    ArrayMap(),
                    ArrayMap(),
                )
        }

    @Test
    fun getTaskInFullImmersiveState_byDisplay() {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.setActiveDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.setTaskInFullImmersiveState(DEFAULT_DESKTOP_ID, taskId = 1, immersive = true)
        repo.setTaskInFullImmersiveState(SECOND_DISPLAY, taskId = 2, immersive = true)

        assertThat(repo.getTaskInFullImmersiveState(DEFAULT_DESKTOP_ID)).isEqualTo(1)
        assertThat(repo.getTaskInFullImmersiveState(SECOND_DISPLAY)).isEqualTo(2)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun setRememberedBoundsRatio_getRememberedBoundsRatio_returnsCorrectValue() {
        val packageName = "com.test.app"
        val bounds = RectF(0.1f, 0.2f, 0.8f, 0.9f)
        repo.setRememberedBoundsRatio(packageName, bounds)
        assertThat(repo.getRememberedBoundsRatio(packageName)).isEqualTo(bounds)
    }



    @Test
    fun getDisplayForDesk_multipleDesks() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 7)
        repo.addDesk(SECOND_DISPLAY, deskId = 8)
        repo.addDesk(SECOND_DISPLAY, deskId = 9)

        assertEquals(DEFAULT_DISPLAY, repo.getDisplayForDesk(deskId = 7))
        assertEquals(SECOND_DISPLAY, repo.getDisplayForDesk(deskId = 8))
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
    )
    fun testRemoveDisplay_multiDesk_removesAllDesksOnDisplay() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 7)
        repo.addDesk(SECOND_DISPLAY, deskId = 8)
        repo.addDesk(SECOND_DISPLAY, deskId = 9)

        repo.removeDisplay(SECOND_DISPLAY)
        executor.flushAll()

        assertEquals(repo.getDeskIds(SECOND_DISPLAY), emptySet())
        assertEquals(repo.getDeskIds(DEFAULT_DISPLAY), setOf(0, 6, 7))
        verify(repo, times(2)).notifyVisibleTaskListeners(SECOND_DISPLAY, visibleTasksCount = 0)
        val lastRemoval = assertNotNull(listener.lastRemoval)
        assertThat(lastRemoval.displayId).isEqualTo(SECOND_DISPLAY)
        assertThat(lastRemoval.deskId).isEqualTo(9)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
    )
    fun testOnDeskDisplayChanged_movesDeskToNewDisplay_invokesCallbacks() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 7)
        repo.addDesk(SECOND_DISPLAY, deskId = 8)
        repo.addDesk(SECOND_DISPLAY, deskId = 9)

        repo.onDeskDisplayChanged(
            deskId = 8,
            newDisplayId = DEFAULT_DISPLAY,
            newUniqueDisplayId = UNIQUE_DISPLAY_ID,
        )
        executor.flushAll()

        assertThat(repo.getDeskIds(DEFAULT_DISPLAY)).containsExactly(0, 6, 7, 8)
        assertThat(repo.getDeskIds(SECOND_DISPLAY)).containsExactly(9)
        // Assert listeners invoked for desk removal from old display.
        verify(repo, times(1)).notifyVisibleTaskListeners(SECOND_DISPLAY, visibleTasksCount = 0)
        val lastRemoval = assertNotNull(listener.lastRemoval)
        assertThat(lastRemoval.displayId).isEqualTo(SECOND_DISPLAY)
        assertThat(lastRemoval.deskId).isEqualTo(8)
        // Assert listeners invoked for desk addition to new display.
        val lastAddition = assertNotNull(listener.lastAddition)
        assertThat(lastAddition.displayId).isEqualTo(DEFAULT_DISPLAY)
        assertThat(lastAddition.deskId).isEqualTo(8)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
    )
    fun preserveDisplay_storesDisplay() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addDesk(SECOND_DISPLAY, deskId = 7)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 11,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTaskToDesk(
            SECOND_DISPLAY,
            deskId = 7,
            taskId = 12,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        val secondTaskBounds = Rect(TEST_TASK_BOUNDS)
        secondTaskBounds.offset(100, 100)
        repo.addTaskToDesk(
            SECOND_DISPLAY,
            deskId = 7,
            taskId = 13,
            isVisible = true,
            taskBounds = secondTaskBounds,
        )
        repo.addRightTiledTaskToDesk(SECOND_DISPLAY, 12, 7)
        repo.addLeftTiledTaskToDesk(SECOND_DISPLAY, 13, 7)
        repo.preserveDisplay(SECOND_DISPLAY, UNIQUE_DISPLAY_ID)
        val preservedDisplay = repo.removePreservedDisplay(UNIQUE_DISPLAY_ID)

        if (preservedDisplay != null) {
            assertThat(repo.getPreservedTaskBounds(preservedDisplay))
                .isEqualTo(mapOf(12 to TEST_TASK_BOUNDS, 13 to secondTaskBounds))
            assertThat(repo.getPreservedDeskIds(preservedDisplay)).containsExactly(7)
            assertThat(repo.getPreservedTilingData(preservedDisplay, 7))
                .isEqualTo(DesktopRepository.PreservedTiledAppData(13, 12))
        } else fail("Expected to find preserved display.")
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
    )
    fun preserveDisplay_noOpIfDisplayEmpty() {
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = DEFAULT_DISPLAY,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = DEFAULT_DISPLAY,
            taskId = 11,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addDesk(SECOND_DISPLAY, deskId = SECOND_DISPLAY)

        repo.preserveDisplay(SECOND_DISPLAY, UNIQUE_DISPLAY_ID)
        val preservedDisplay = repo.removePreservedDisplay(UNIQUE_DISPLAY_ID)

        assertThat(preservedDisplay).isNull()
    }

    @Test
    fun setDeskActive() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)

        repo.setActiveDesk(DEFAULT_DISPLAY, deskId = 6)

        assertThat(repo.getActiveDeskId(DEFAULT_DISPLAY)).isEqualTo(6)
    }

    @Test
    fun setDeskInactive() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.setActiveDesk(DEFAULT_DISPLAY, deskId = 6)

        repo.setDeskInactive(deskId = 6)

        assertThat(repo.getActiveDeskId(DEFAULT_DISPLAY)).isNull()
    }

    @Test
    fun getDeskIdForTask() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getDeskIdForTask(10)).isEqualTo(6)
    }

    @Test
    fun removeTaskFromDesk_clearsBoundsBeforeMaximize() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.saveBoundsBeforeSnapOrMaximize(taskId = 10, bounds = Rect(10, 10, 100, 100))

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.removeBoundsBeforeSnapOrMaximize(taskId = 10)).isNull()
    }

    @Test
    fun removeTaskFromDesk_clearsBoundsBeforeImmersive() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.saveBoundsBeforeFullImmersive(taskId = 10, bounds = Rect(10, 10, 100, 100))

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.removeBoundsBeforeFullImmersive(taskId = 10)).isNull()
    }

    @Test
    fun removeTaskFromDesk_removesFromZOrderList() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.getFreeformTasksIdsInDeskInZOrder(deskId = 6)).doesNotContain(10)
    }

    @Test
    fun removeTaskFromDesk_removesFromMinimized() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.minimizeTaskInDesk(DEFAULT_DISPLAY, deskId = 6, taskId = 10)

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.getMinimizedTaskIdsInDesk(deskId = 6)).doesNotContain(10)
    }

    @Test
    fun removeTaskFromDesk_removesFromImmersive() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.setTaskInFullImmersiveStateInDesk(deskId = 6, taskId = 10, immersive = true)

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 10)).isFalse()
    }

    @Test
    fun removeTaskFromDesk_removesFromActiveTasks() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.isActiveTaskInDesk(taskId = 10, deskId = 6)).isFalse()
    }

    @Test
    fun removeTaskFromDesk_removesFromVisibleTasks() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.isVisibleTaskInDesk(taskId = 10, deskId = 6)).isFalse()
    }

    @Test
    fun addDesk_updatesListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)

        repo.addDesk(displayId = 0, deskId = 1)
        executor.flushAll()

        val lastAddition = assertNotNull(listener.lastAddition)
        assertThat(lastAddition.displayId).isEqualTo(0)
        assertThat(lastAddition.deskId).isEqualTo(1)
    }

    @Test
    fun addDesk_atDeskLimit_updatesCanCreateDeskListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        desktopConfig.maxDeskLimit = 3
        repo.addDeskChangeListener(listener, executor)

        repo.addDesk(displayId = 0, deskId = 1) // Second desk.
        executor.flushAll()
        assertThat(listener.lastCanCreateDesks).isNull()

        repo.addDesk(displayId = 0, deskId = 2) // Third desk.
        executor.flushAll()
        val lastCanCreateDesks = assertNotNull(listener.lastCanCreateDesks)
        assertThat(lastCanCreateDesks).isFalse()
    }

    @Test
    fun removeDesk_updatesListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 0, deskId = 1)

        repo.removeDesk(deskId = 1)
        executor.flushAll()

        verify(repo, times(1)).notifyVisibleTaskListeners(DEFAULT_DISPLAY, visibleTasksCount = 0)
        val lastRemoval = assertNotNull(listener.lastRemoval)
        assertThat(lastRemoval.displayId).isEqualTo(0)
        assertThat(lastRemoval.deskId).isEqualTo(1)
    }

    @Test
    fun removeDesk_belowDeskLimit_updatesCanCreateDeskListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        desktopConfig.maxDeskLimit = 3
        repo.addDeskChangeListener(listener, executor)

        repo.addDesk(displayId = 0, deskId = 1) // Second desk.
        repo.addDesk(displayId = 0, deskId = 2) // Third desk.
        executor.flushAll()
        val lastCanCreateDesks1 = assertNotNull(listener.lastCanCreateDesks)
        assertThat(lastCanCreateDesks1).isFalse()

        repo.removeDesk(deskId = 1) // Back to two desks.
        executor.flushAll()
        val lastCanCreateDesks2 = assertNotNull(listener.lastCanCreateDesks)
        assertThat(lastCanCreateDesks2).isTrue()
    }

    @Test
    fun removeDesk_didNotExist_doesNotUpdateListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 0, deskId = 1)

        repo.removeDesk(deskId = 2)
        executor.flushAll()

        verify(repo, times(0)).notifyVisibleTaskListeners(DEFAULT_DISPLAY, visibleTasksCount = 0)
        assertThat(listener.lastRemoval).isNull()
    }

    @Test
    fun removeDesk_wasActive_updatesActiveChangeListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 0, deskId = 1)
        repo.setActiveDesk(displayId = 0, deskId = 1)

        repo.removeDesk(deskId = 1)
        executor.flushAll()

        verify(repo, times(1)).notifyVisibleTaskListeners(DEFAULT_DISPLAY, visibleTasksCount = 0)
        val lastActivationChange = assertNotNull(listener.lastActivationChange)
        assertThat(lastActivationChange.displayId).isEqualTo(0)
        assertThat(lastActivationChange.oldActive).isEqualTo(1)
        assertThat(lastActivationChange.newActive).isEqualTo(INVALID_DESK_ID)
    }

    @Test
    fun setDeskActive_fromNoActive_updatesListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 1, deskId = 1)

        repo.setActiveDesk(displayId = 1, deskId = 1)
        executor.flushAll()

        val lastActivationChange = assertNotNull(listener.lastActivationChange)
        assertThat(lastActivationChange.displayId).isEqualTo(1)
        assertThat(lastActivationChange.oldActive).isEqualTo(INVALID_DESK_ID)
        assertThat(lastActivationChange.newActive).isEqualTo(1)
    }

    @Test
    fun setDeskActive_fromOtherActive_updatesListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 1, deskId = 1)
        repo.addDesk(displayId = 1, deskId = 2)
        repo.setActiveDesk(displayId = 1, deskId = 1)

        repo.setActiveDesk(displayId = 1, deskId = 2)
        executor.flushAll()

        val lastActivationChange = assertNotNull(listener.lastActivationChange)
        assertThat(lastActivationChange.displayId).isEqualTo(1)
        assertThat(lastActivationChange.oldActive).isEqualTo(1)
        assertThat(lastActivationChange.newActive).isEqualTo(2)
    }

    @Test
    fun setDeskActive_alreadyActive_doesNotUpdateListenerTwice() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 1, deskId = 1)
        repo.setActiveDesk(displayId = 1, deskId = 1)

        repo.setActiveDesk(displayId = 1, deskId = 1)
        executor.flushAll()

        assertThat(listener.activationChanges.size).isEqualTo(1)
        val lastActivationChange = assertNotNull(listener.lastActivationChange)
        assertThat(lastActivationChange.displayId).isEqualTo(1)
        assertThat(lastActivationChange.oldActive).isEqualTo(-1)
        assertThat(lastActivationChange.newActive).isEqualTo(1)
    }

    @Test
    fun setDeskInactive_updatesListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 0, deskId = 1)
        repo.setActiveDesk(displayId = 0, deskId = 1)

        repo.setDeskInactive(deskId = 1)
        executor.flushAll()

        val lastActivationChange = assertNotNull(listener.lastActivationChange)
        assertThat(lastActivationChange.displayId).isEqualTo(0)
        assertThat(lastActivationChange.oldActive).isEqualTo(1)
        assertThat(lastActivationChange.newActive).isEqualTo(INVALID_DESK_ID)
    }

    @Test
    fun getPreviousDeskId() {
        repo.addDesk(displayId = 5, deskId = 1)
        repo.addDesk(displayId = 5, deskId = 2)
        repo.addDesk(displayId = 5, deskId = 3)

        assertThat(repo.getPreviousDeskId(1)).isNull()
        assertThat(repo.getPreviousDeskId(2)).isEqualTo(1)
        assertThat(repo.getPreviousDeskId(3)).isEqualTo(2)
    }

    @Test
    fun getNextDeskId() {
        repo.addDesk(displayId = 5, deskId = 1)
        repo.addDesk(displayId = 5, deskId = 2)
        repo.addDesk(displayId = 5, deskId = 3)

        assertThat(repo.getNextDeskId(1)).isEqualTo(2)
        assertThat(repo.getNextDeskId(2)).isEqualTo(3)
        assertThat(repo.getNextDeskId(3)).isNull()
    }

    @Test
    fun addDesk_transientDesk_persistentRepoNotUpdated() = runTest {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)

        repo.addDesk(
            displayId = 0,
            deskId = 1,
            uniqueDisplayId = UNIQUE_DISPLAY_ID,
            transientDesk = true,
        )

        assertThat(listener.lastAddition).isNull()
        verify(persistentRepository, never())
            .addOrUpdateDesktop(any(), any(), any(), any(), any(), any(), any(), any())
        verify(persistentRepository, never())
            .addOrUpdateRepository(any(), any(), any(), any(), any())
    }

    @Test
    fun addTiledTasks_toTransientDesk_persistentRepoNotUpdated() = runTest {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)

        repo.addDesk(
            displayId = 0,
            deskId = 1,
            uniqueDisplayId = UNIQUE_DISPLAY_ID,
            transientDesk = true,
        )
        repo.addLeftTiledTaskToDesk(displayId = 0, taskId = 2, deskId = 1)
        repo.addRightTiledTaskToDesk(displayId = 0, taskId = 3, deskId = 1)

        assertThat(listener.lastAddition).isNull()
        verify(persistentRepository, never())
            .addOrUpdateDesktop(any(), any(), any(), any(), any(), any(), any(), any())
        verify(persistentRepository, never())
            .addOrUpdateRepository(any(), any(), any(), any(), any())
    }

    @Test
    fun addActiveTask_toTransientDesk_persistentRepoNotUpdated() = runTest {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)

        repo.addDesk(
            displayId = 0,
            deskId = 1,
            uniqueDisplayId = UNIQUE_DISPLAY_ID,
            transientDesk = true,
        )
        repo.addTaskToDesk(
            displayId = 0,
            deskId = 1,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(listener.lastAddition).isNull()
        verify(persistentRepository, never())
            .addOrUpdateDesktop(any(), any(), any(), any(), any(), any(), any(), any())
        verify(persistentRepository, never())
            .addOrUpdateRepository(any(), any(), any(), any(), any())
    }

    @Test
    fun addTaskToDesk_transientDesk_doesNotRemoveFromOtherDesks() {
        // Create non-transient desk with task, then transient desk.
        repo.addDesk(DEFAULT_DISPLAY, deskId = 1)
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 1,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addDesk(DEFAULT_DISPLAY, deskId = 2, transientDesk = true)

        // Add task to transient desk.
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 2,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        // Assert desk 1 didn't lose the task and desk 2 successfully added it.
        assertThat(repo.getFreeformTasksIdsInDeskInZOrder(1)).contains(10)
        assertThat(repo.getFreeformTasksIdsInDeskInZOrder(2)).contains(10)
    }

    @Test
    fun removeActiveTask_fromTransientDesk_listenersNotUpdated() = runTest {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)

        repo.addDesk(
            displayId = 0,
            deskId = 1,
            uniqueDisplayId = UNIQUE_DISPLAY_ID,
            transientDesk = true,
        )
        repo.addTaskToDesk(
            displayId = 0,
            deskId = 1,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.removeTaskFromDesk(deskId = 1, taskId = 2)

        assertThat(listener.lastAddition).isNull()
        assertThat(listener.lastRemoval).isNull()
        verify(persistentRepository, never())
            .addOrUpdateDesktop(any(), any(), any(), any(), any(), any(), any(), any())
        verify(persistentRepository, never())
            .addOrUpdateRepository(any(), any(), any(), any(), any())
    }

    @Test
    fun preserveDesk_activeDesk_deskPreservedAsActive() = runTest {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 1,
            uniqueDisplayId = UNIQUE_DISPLAY_ID,
            transientDesk = true,
        )
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 1,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.preserveDesk(deskId = 1, uniqueDisplayId = UNIQUE_DISPLAY_ID, preserveAsActive = true)

        assertThat(repo.removePreservedDisplay(UNIQUE_DISPLAY_ID)?.activeDeskId).isEqualTo(1)
    }

    @Test
    fun preserveDesk_inactiveDesk_preservedActiveDeskIsNull() = runTest {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 1,
            uniqueDisplayId = UNIQUE_DISPLAY_ID,
            transientDesk = true,
        )
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 1,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.preserveDesk(deskId = 1, uniqueDisplayId = UNIQUE_DISPLAY_ID, preserveAsActive = false)

        assertThat(repo.removePreservedDisplay(UNIQUE_DISPLAY_ID)?.activeDeskId).isNull()
    }

    @Test
    fun minimizeTask_inTransientDesk_persistentRepoNotUpdated() = runTest {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)

        repo.addDesk(
            displayId = 0,
            deskId = 1,
            uniqueDisplayId = UNIQUE_DISPLAY_ID,
            transientDesk = true,
        )
        repo.addTaskToDesk(
            displayId = 0,
            deskId = 1,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.minimizeTaskInDesk(displayId = 0, deskId = 1, taskId = 2)

        assertThat(listener.lastAddition).isNull()
        verify(persistentRepository, never())
            .addOrUpdateDesktop(any(), any(), any(), any(), any(), any(), any(), any())
        verify(persistentRepository, never())
            .addOrUpdateRepository(any(), any(), any(), any(), any())
    }

    @Test
    fun hasBoundsBeforeSnapOrMaximize_boundsExist_returnsTrue() {
        val taskId = 123
        val taskInfo = TestRunningTaskInfoBuilder().setTaskId(taskId).build()
        val bounds = Rect(0, 0, 100, 100)
        repo.saveBoundsBeforeSnapOrMaximize(taskId, bounds)

        assertThat(repo.hasBoundsBeforeSnapOrMaximize(taskInfo)).isTrue()
    }

    @Test
    fun hasBoundsBeforeSnapOrMaximize_noBounds_returnsFalse() {
        val taskId = 456
        val taskInfo = TestRunningTaskInfoBuilder().setTaskId(taskId).build()

        // taskId and its bounds are not saved into the storage.
        assertThat(repo.hasBoundsBeforeSnapOrMaximize(taskInfo)).isFalse()
    }

    @Test
    fun removeDesk_lastDeskOnDisplay_persistsEmptyList() = runTest {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = 1, uniqueDisplayId = "unique_id_1")
        clearInvocations(persistentRepository)

        repo.removeDesk(deskId = DEFAULT_DESKTOP_ID)
        repo.removeDesk(deskId = 1)
        bgScope.testScheduler.advanceUntilIdle()

        verify(persistentRepository)
            .addOrUpdateRepository(
                userId = eq(DEFAULT_USER_ID),
                desks = eq(emptyList()),
                activeDeskIdToUniqueDisplayId = any(),
                preservedDisplays = any(),
                rememberedBoundsRatioByPackageName = any(),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun setRememberedBoundsRatio_clearRememberedBoundsRatio() = runTest {
        val packageName = "com.test.app"
        val bounds = RectF(0.1f, 0.2f, 0.8f, 0.9f)
        repo.setRememberedBoundsRatio(packageName, bounds)
        assertThat(repo.getRememberedBoundsRatio(packageName)).isEqualTo(bounds)

        repo.clearRememberedBoundsRatio(packageName)
        bgScope.testScheduler.advanceUntilIdle()

        assertThat(repo.getRememberedBoundsRatio(packageName)).isNull()
        verify(persistentRepository)
            .addOrUpdateRepository(
                userId = eq(DEFAULT_USER_ID),
                desks = any(),
                activeDeskIdToUniqueDisplayId = any(),
                preservedDisplays = any(),
                rememberedBoundsRatioByPackageName = eq(ArrayMap()),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun setRememberedBoundsRatio_clearRememberedBoundsRatio_noChange() = runTest {
        val packageName = "com.test.app"
        assertThat(repo.getRememberedBoundsRatio(packageName)).isNull()

        repo.clearRememberedBoundsRatio(packageName)
        bgScope.testScheduler.advanceUntilIdle()

        verify(persistentRepository, never())
            .addOrUpdateRepository(any(), any(), any(), any(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun clearAllRememberedBoundsRatio_flagEnabled_clearsAllBoundsAndPersists() = runTest {
        // GIVEN that remembered bounds are stored for multiple packages
        val packageName1 = "com.test.app1"
        val bounds1 = RectF(0.1f, 0.2f, 0.8f, 0.9f)
        repo.setRememberedBoundsRatio(packageName1, bounds1)
        val packageName2 = "com.test.app2"
        val bounds2 = RectF(0.2f, 0.3f, 0.7f, 0.8f)
        repo.setRememberedBoundsRatio(packageName2, bounds2)
        assertThat(repo.getRememberedBoundsRatio(packageName1)).isEqualTo(bounds1)
        assertThat(repo.getRememberedBoundsRatio(packageName2)).isEqualTo(bounds2)

        // WHEN clearAllRememberedBoundsRatio is called
        repo.clearAllRememberedBoundsRatio()
        bgScope.testScheduler.advanceUntilIdle()

        // THEN all bounds are cleared
        assertThat(repo.getRememberedBoundsRatio(packageName1)).isNull()
        assertThat(repo.getRememberedBoundsRatio(packageName2)).isNull()
        // AND the change is persisted
        verify(persistentRepository)
            .addOrUpdateRepository(
                userId = eq(DEFAULT_USER_ID),
                desks = any(),
                activeDeskIdToUniqueDisplayId = any(),
                preservedDisplays = any(),
                rememberedBoundsRatioByPackageName = eq(ArrayMap()),
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun clearAllRememberedBoundsRatio_flagDisabled_isNoOp() = runTest {
        // GIVEN the remembered bounds flag is disabled
        // (and we attempt to set a value, which should be a no-op)
        val packageName = "com.test.app"
        val bounds = RectF(0.1f, 0.2f, 0.8f, 0.9f)
        repo.setRememberedBoundsRatio(packageName, bounds)
        assertThat(repo.getRememberedBoundsRatio(packageName)).isNull()

        // WHEN clearAllRememberedBoundsRatio is called
        repo.clearAllRememberedBoundsRatio()
        bgScope.testScheduler.advanceUntilIdle()

        // THEN nothing happens and persistence is not triggered
        verify(persistentRepository, never())
            .addOrUpdateRepository(any(), any(), any(), any(), any())
    }

    @Test
    fun setExclusionRegionListener_notifiesListenerWithCurrentRegion() {
        // Setup: Add an exclusion region before setting the listener to ensure the listener
        // is called with the current state upon registration.
        val taskId = 1
        val region = Region(10, 10, 100, 100)
        repo.updateTaskExclusionRegions(taskId, region)

        val mockListener: Consumer<Region> = mock()
        val executor = TestShellExecutor()

        // Action: Set the listener.
        repo.setExclusionRegionListener(mockListener, executor)
        executor.flushAll()

        // Verification: Listener should be notified with the existing region.
        val regionCaptor = argumentCaptor<Region>()
        verify(mockListener).accept(regionCaptor.capture())
        assertThat(regionCaptor.firstValue).isEqualTo(region)
    }

    @Test
    fun updateTaskExclusionRegions_notifiesListenerWithUpdatedRegion() {
        // Setup: Set a listener.
        val mockListener: Consumer<Region> = mock()
        val executor = TestShellExecutor()
        repo.setExclusionRegionListener(mockListener, executor)
        executor.flushAll() // Initial notification
        clearInvocations(mockListener)

        // Action: Update task exclusion regions.
        val taskId = 1
        val region = Region(10, 10, 100, 100)
        repo.updateTaskExclusionRegions(taskId, region)
        executor.flushAll()

        // Verification: Listener should be notified with the new region.
        val regionCaptor = argumentCaptor<Region>()
        verify(mockListener).accept(regionCaptor.capture())
        assertThat(regionCaptor.firstValue).isEqualTo(region)
    }

    @Test
    fun updateTaskExclusionRegions_multipleTasks_notifiesWithUnionRegion() {
        // Setup
        val mockListener: Consumer<Region> = mock()
        val executor = TestShellExecutor()
        repo.setExclusionRegionListener(mockListener, executor)
        executor.flushAll() // Initial notification with empty region
        clearInvocations(mockListener)

        // Action: Add region for first task
        val taskId1 = 1
        val region1 = Region(10, 10, 100, 100)
        repo.updateTaskExclusionRegions(taskId1, region1)
        executor.flushAll()

        // Verification for first task
        val regionCaptor1 = argumentCaptor<Region>()
        verify(mockListener).accept(regionCaptor1.capture())
        assertThat(regionCaptor1.firstValue).isEqualTo(region1)
        clearInvocations(mockListener)

        // Action: Add region for second task
        val taskId2 = 2
        val region2 = Region(150, 150, 200, 200)
        repo.updateTaskExclusionRegions(taskId2, region2)
        executor.flushAll()

        // Verification for second task (union of regions)
        val expectedUnionRegion = Region()
        expectedUnionRegion.op(region1, Region.Op.UNION)
        expectedUnionRegion.op(region2, Region.Op.UNION)

        val regionCaptor2 = argumentCaptor<Region>()
        verify(mockListener).accept(regionCaptor2.capture())
        assertThat(regionCaptor2.firstValue).isEqualTo(expectedUnionRegion)
    }

    @Test
    fun removeExclusionRegion_notifiesListenerWithUpdatedRegion() {
        // Setup
        val mockListener: Consumer<Region> = mock()
        val executor = TestShellExecutor()
        repo.setExclusionRegionListener(mockListener, executor)
        val taskId = 1
        val region = Region(10, 10, 100, 100)
        repo.updateTaskExclusionRegions(taskId, region)
        executor.flushAll() // Notifications for set listener and update
        clearInvocations(mockListener)

        // Action: Remove the exclusion region.
        repo.removeExclusionRegion(taskId)
        executor.flushAll()

        // Verification: Listener should be notified with an empty region.
        val regionCaptor = argumentCaptor<Region>()
        verify(mockListener).accept(regionCaptor.capture())
        assertThat(regionCaptor.firstValue.isEmpty).isTrue()
    }

    @Test
    fun updateTaskExclusionRegions_noListenerSet_doesNotCrash() {
        // Action: Update task exclusion regions without a listener set.
        val taskId = 1
        val region = Region(10, 10, 100, 100)
        repo.updateTaskExclusionRegions(taskId, region)
        // No crash is the verification.
    }

    private class TestDeskChangeListener : DesktopRepository.DeskChangeListener {
        var lastAddition: LastAddition? = null
            private set

        var lastRemoval: LastRemoval? = null
            private set

        val activationChanges = mutableListOf<ActivationChange>()
        val lastActivationChange: ActivationChange?
            get() = activationChanges.lastOrNull()

        var lastCanCreateDesks: Boolean? = null
            private set

        var lastTaskAppearingInDesk: LastTaskAppearingInDesk? = null
            private set

        override fun onDeskAdded(displayId: Int, deskId: Int) {
            lastAddition = LastAddition(displayId, deskId)
        }

        override fun onDeskRemoved(displayId: Int, deskId: Int) {
            lastRemoval = LastRemoval(displayId, deskId)
        }

        override fun onActiveDeskChanged(
            displayId: Int,
            newActiveDeskId: Int,
            oldActiveDeskId: Int,
        ) {
            activationChanges +=
                ActivationChange(
                    displayId = displayId,
                    oldActive = oldActiveDeskId,
                    newActive = newActiveDeskId,
                )
        }

        override fun onCanCreateDesksChanged(canCreateDesks: Boolean) {
            lastCanCreateDesks = canCreateDesks
        }

        override fun onTaskAppearingInDesk(taskId: Int, displayId: Int, deskId: Int) {
            lastTaskAppearingInDesk = LastTaskAppearingInDesk(taskId, displayId, deskId)
        }

        data class LastAddition(val displayId: Int, val deskId: Int)

        data class LastRemoval(val displayId: Int, val deskId: Int)

        data class ActivationChange(val displayId: Int, val oldActive: Int, val newActive: Int)

        data class LastTaskAppearingInDesk(val taskId: Int, val displayId: Int, val deskId: Int)
    }

    class TestListener : DesktopRepository.ActiveTasksListener {
        var activeChangesOnDefaultDisplay = 0
        var activeChangesOnSecondaryDisplay = 0
        var activeChangesOnThirdDisplay = 0

        override fun onActiveTasksChanged(displayId: Int) {
            when (displayId) {
                DEFAULT_DISPLAY -> activeChangesOnDefaultDisplay++
                SECOND_DISPLAY -> activeChangesOnSecondaryDisplay++
                THIRD_DISPLAY -> activeChangesOnThirdDisplay++
                else -> fail("Active task listener received unexpected display id: $displayId")
            }
        }
    }

    class TestVisibilityListener : DesktopRepository.VisibleTasksListener {
        var visibleTasksCountOnDefaultDisplay = 0
        var visibleTasksCountOnSecondaryDisplay = 0
        var visibleTasksCountOnThirdDisplay = 0

        var visibleChangesOnDefaultDisplay = 0
        var visibleChangesOnSecondaryDisplay = 0
        var visibleChangesOnThirdDisplay = 0

        override fun onTasksVisibilityChanged(displayId: Int, visibleTasksCount: Int) {
            when (displayId) {
                DEFAULT_DISPLAY -> {
                    visibleTasksCountOnDefaultDisplay = visibleTasksCount
                    visibleChangesOnDefaultDisplay++
                }
                SECOND_DISPLAY -> {
                    visibleTasksCountOnSecondaryDisplay = visibleTasksCount
                    visibleChangesOnSecondaryDisplay++
                }
                THIRD_DISPLAY -> {
                    visibleTasksCountOnThirdDisplay = visibleTasksCount
                    visibleChangesOnThirdDisplay++
                }
                else -> fail("Visible task listener received unexpected display id: $displayId")
            }
        }
    }

    companion object {
        const val SECOND_DISPLAY = 1
        const val THIRD_DISPLAY = 345
        private const val DEFAULT_USER_ID = 1000
        private const val DEFAULT_DESKTOP_ID = 0
        private const val UNIQUE_DISPLAY_ID = "uniqueDisplayId"
        private const val UNIQUE_DISPLAY_ID2 = "uniqueDisplayId2"
        private val TEST_TASK_BOUNDS = Rect(100, 100, 200, 200)
    }
}
