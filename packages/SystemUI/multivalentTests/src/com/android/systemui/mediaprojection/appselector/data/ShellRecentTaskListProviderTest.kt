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

package com.android.systemui.mediaprojection.appselector.data

import android.app.ActivityManager.RecentTaskInfo
import android.content.Intent
import android.content.pm.UserInfo
import android.graphics.Rect
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.screencapture.sharescreen.domain.interactor.ScreenCaptureShareScreenFeaturesInteractor
import com.android.systemui.settings.UserTracker
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.whenever
import com.android.users.UserType
import com.android.users.UserType.CLONED
import com.android.users.UserType.MAIN
import com.android.users.UserType.PRIVATE
import com.android.users.UserType.WORK
import com.android.wm.shell.recents.RecentTasks
import com.android.wm.shell.shared.GroupedTaskInfo
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import java.util.function.Consumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt

@RunWith(AndroidJUnit4::class)
@SmallTest
class ShellRecentTaskListProviderTest : SysuiTestCase() {

    private val dispatcher = Dispatchers.Unconfined
    private val recentTasks: RecentTasks = mock()
    private val userTracker: UserTracker = mock()
    private val userManager: UserManager = mock {
        whenever(getUserInfo(anyInt())).thenReturn(mock())
    }
    private val screenShareFeatureInteractor: ScreenCaptureShareScreenFeaturesInteractor = mock()
    private val recentTaskListProvider =
        ShellRecentTaskListProvider(
            dispatcher,
            Runnable::run,
            Optional.of(recentTasks),
            userTracker,
            userManager,
            screenShareFeatureInteractor,
        )

    @Test
    fun loadRecentTasks_largeScreen_firstTaskIsForeground() {
        whenever(screenShareFeatureInteractor.isLargeScreenSharingEnabled).thenReturn(true)
        givenRecentTasks(
            createSingleTask(taskId = 1, isVisible = true),
            createSingleTask(taskId = 2, isVisible = true),
        )

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result[0].taskId).isEqualTo(1)
        assertThat(result[0].isForegroundTask).isTrue()
        assertThat(result[1].taskId).isEqualTo(2)
        assertThat(result[1].isForegroundTask).isFalse()
    }

    @Test
    fun loadRecentTasks_typeDesk_largeScreen_firstTaskInDeskIsForeground() {
        whenever(screenShareFeatureInteractor.isLargeScreenSharingEnabled).thenReturn(true)
        givenRecentTasks(createDeskTask(taskIds = listOf(1, 2, 3), isVisible = true))

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result.map { it.taskId }).containsExactly(1, 2, 3).inOrder()
        assertThat(result.map { it.isForegroundTask }).containsExactly(true, false, false).inOrder()
    }

    @Test
    fun loadRecentTasks_typeDesk_notLargeScreen_secondTaskInDeskIsForeground() {
        whenever(screenShareFeatureInteractor.isLargeScreenSharingEnabled).thenReturn(false)
        givenRecentTasks(createDeskTask(taskIds = listOf(1, 2, 3), isVisible = true))

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result.map { it.taskId }).containsExactly(1, 2, 3).inOrder()
        assertThat(result.map { it.isForegroundTask }).containsExactly(false, true, false).inOrder()
    }

    @Test
    fun loadRecentTasks_hiddenFullscreenAndVisibleDesk_largeScreen_identifiesDeskTaskAsForeground() {
        whenever(screenShareFeatureInteractor.isLargeScreenSharingEnabled).thenReturn(true)
        givenRecentTasks(
            createSingleTask(taskId = 1, isVisible = false),
            createDeskTask(taskIds = listOf(2, 3), isVisible = true),
        )

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        // Task 1 is NOT foreground because it's invisible
        assertThat(result.find { it.taskId == 1 }?.isForegroundTask).isFalse()
        // Task 2 is foreground because it's the first task in its visible desk
        assertThat(result.find { it.taskId == 2 }?.isForegroundTask).isTrue()
        // Task 3 is NOT foreground
        assertThat(result.find { it.taskId == 3 }?.isForegroundTask).isFalse()
    }

    @Test
    fun loadRecentTasks_oneTask_returnsTheSameTask() {
        givenRecentTasks(createSingleTask(taskId = 1))

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result).containsExactly(createRecentTask(taskId = 1))
    }

    @Test
    fun loadRecentTasks_multipleTasks_returnsTheSameTasks() {
        givenRecentTasks(
            createSingleTask(taskId = 1),
            createSingleTask(taskId = 2),
            createSingleTask(taskId = 3),
        )

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result.map { it.taskId }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun loadRecentTasks_groupedTask_returnsUngroupedTasks() {
        givenRecentTasks(createTaskPair(taskId1 = 1, taskId2 = 2))

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result.map { it.taskId }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun loadRecentTasks_mixedSingleAndGroupedTask_returnsUngroupedTasks() {
        givenRecentTasks(
            createSingleTask(taskId = 1),
            createTaskPair(taskId1 = 2, taskId2 = 3),
            createSingleTask(taskId = 4),
            createTaskPair(taskId1 = 5, taskId2 = 6),
        )

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result.map { it.taskId }).containsExactly(1, 2, 3, 4, 5, 6).inOrder()
    }

    @Test
    fun loadRecentTasks_singleTask_returnsTaskAsNotForeground() {
        givenRecentTasks(createSingleTask(taskId = 1, isVisible = true))

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result[0].isForegroundTask).isFalse()
    }

    @Test
    fun loadRecentTasks_singleTaskPair_returnsTasksAsForeground() {
        givenRecentTasks(createTaskPair(taskId1 = 2, taskId2 = 3, isVisible = true))

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result[0].isForegroundTask).isTrue()
    }

    @Test
    fun loadRecentTasks_multipleTasks_returnsSecondVisibleTaskAsForegroundTask() {
        givenRecentTasks(
            createSingleTask(taskId = 1),
            createSingleTask(taskId = 2, isVisible = true),
            createSingleTask(taskId = 3),
        )

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result.map { it.isForegroundTask }).containsExactly(false, true, false).inOrder()
    }

    @Test
    fun loadRecentTasks_multipleTasks_returnsSecondInvisibleTaskAsNotForegroundTask() {
        givenRecentTasks(
            createSingleTask(taskId = 1),
            createSingleTask(taskId = 2, isVisible = false),
            createSingleTask(taskId = 3),
        )

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result.map { it.isForegroundTask })
            .containsExactly(false, false, false)
            .inOrder()
    }

    @Test
    fun loadRecentTasks_secondTaskIsGroupedAndVisible_marksBothGroupedTasksAsForeground() {
        givenRecentTasks(
            createSingleTask(taskId = 1),
            createTaskPair(taskId1 = 2, taskId2 = 3, isVisible = true),
            createSingleTask(taskId = 4),
        )

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result.map { it.isForegroundTask })
            .containsExactly(false, true, true, false)
            .inOrder()
    }

    @Test
    fun loadRecentTasks_firstTaskIsGroupedAndVisible_marksBothGroupedTasksAsForeground() {
        givenRecentTasks(
            createTaskPair(taskId1 = 1, taskId2 = 2, isVisible = true),
            createSingleTask(taskId = 3),
            createSingleTask(taskId = 4),
        )

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result.map { it.isForegroundTask })
            .containsExactly(true, true, false, false)
            .inOrder()
    }

    @Test
    fun loadRecentTasks_secondTaskIsGroupedAndInvisible_marksBothGroupedTasksAsNotForeground() {
        givenRecentTasks(
            createSingleTask(taskId = 1),
            createTaskPair(taskId1 = 2, taskId2 = 3, isVisible = false),
            createSingleTask(taskId = 4),
        )

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result.map { it.isForegroundTask })
            .containsExactly(false, false, false, false)
            .inOrder()
    }

    @Test
    fun loadRecentTasks_firstTaskIsGroupedAndInvisible_marksBothGroupedTasksAsNotForeground() {
        givenRecentTasks(
            createTaskPair(taskId1 = 1, taskId2 = 2, isVisible = false),
            createSingleTask(taskId = 3),
            createSingleTask(taskId = 4),
        )

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result.map { it.isForegroundTask })
            .containsExactly(false, false, false, false)
            .inOrder()
    }

    @Test
    fun loadRecentTasks_assignsCorrectUserType() {
        givenRecentTasks(
            createSingleTask(taskId = 1, userId = 10, userType = MAIN),
            createSingleTask(taskId = 2, userId = 20, userType = WORK),
            createSingleTask(taskId = 3, userId = 30, userType = CLONED),
            createSingleTask(taskId = 4, userId = 40, userType = PRIVATE),
        )

        val result = runBlocking { recentTaskListProvider.loadRecentTasks() }

        assertThat(result.map { it.userType })
            .containsExactly(MAIN, WORK, CLONED, PRIVATE)
            .inOrder()
    }

    @Suppress("UNCHECKED_CAST")
    private fun givenRecentTasks(vararg tasks: GroupedTaskInfo) {
        whenever(recentTasks.getRecentTasks(any(), any(), any(), any(), any())).thenAnswer {
            val consumer = it.arguments.last() as Consumer<List<GroupedTaskInfo>>
            consumer.accept(tasks.toList())
        }
    }

    private fun createRecentTask(
        taskId: Int,
        userType: UserType = MAIN,
        baseIntent: Intent? = null,
    ): RecentTask =
        RecentTask(
            taskId = taskId,
            displayId = 0,
            userId = 0,
            topActivityComponent = null,
            baseIntentComponent = null,
            baseIntent = baseIntent,
            colorBackground = null,
            isForegroundTask = false,
            userType = userType,
            splitBounds = null,
        )

    private fun createSingleTask(
        taskId: Int,
        userId: Int = 0,
        isVisible: Boolean = false,
        userType: UserType = MAIN,
    ): GroupedTaskInfo {
        val userInfo =
            mock<UserInfo> {
                whenever(isCloneProfile).thenReturn(userType == CLONED)
                whenever(isManagedProfile).thenReturn(userType == WORK)
                whenever(isPrivateProfile).thenReturn(userType == PRIVATE)
            }
        whenever(userManager.getUserInfo(userId)).thenReturn(userInfo)
        return GroupedTaskInfo.forFullscreenTasks(createTaskInfo(taskId, userId, isVisible))
    }

    private fun createDeskTask(
        taskIds: List<Int>,
        userId: Int = 0,
        isVisible: Boolean = false,
    ): GroupedTaskInfo {
        val taskInfos = taskIds.map { createTaskInfo(it, userId, isVisible) }
        return GroupedTaskInfo.forDeskTasks(1, 0, taskInfos, emptySet())
    }

    private fun createTaskPair(
        taskId1: Int,
        userId1: Int = 0,
        taskId2: Int,
        userId2: Int = 0,
        isVisible: Boolean = false,
    ): GroupedTaskInfo =
        GroupedTaskInfo.forSplitTasks(
            createTaskInfo(taskId1, userId1, isVisible),
            createTaskInfo(taskId2, userId2, isVisible),
            SplitBounds(Rect(), Rect(), taskId1, taskId2, SNAP_TO_2_50_50),
        )

    private fun createTaskInfo(taskId: Int, userId: Int, isVisible: Boolean = false) =
        RecentTaskInfo().apply {
            this.taskId = taskId
            this.isVisible = isVisible
            this.userId = userId
        }
}
