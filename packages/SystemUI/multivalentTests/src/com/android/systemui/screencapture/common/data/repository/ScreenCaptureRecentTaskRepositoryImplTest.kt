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

package com.android.systemui.screencapture.common.data.repository

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.currentValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.mediaprojection.appselector.data.RecentTask
import com.android.systemui.mediaprojection.appselector.data.RecentTaskListProvider
import com.android.systemui.shared.system.taskStackChangeListeners
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("VisibleForTests")
@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureRecentTaskRepositoryImplTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val fakeRecentTask1 =
        RecentTask(
            taskId = 1,
            displayId = 1,
            userId = 1,
            topActivityComponent = null,
            baseIntentComponent = null,
            colorBackground = null,
            isForegroundTask = true,
            userType = RecentTask.UserType.STANDARD,
            splitBounds = null,
        )

    private val fakeRecentTask2 =
        RecentTask(
            taskId = 2,
            displayId = 2,
            userId = 2,
            topActivityComponent = null,
            baseIntentComponent = null,
            colorBackground = null,
            isForegroundTask = false,
            userType = RecentTask.UserType.STANDARD,
            splitBounds = null,
        )

    private val fakeRecentTaskListProvider = FakeRecentTaskListProvider()

    @Test
    fun recentTasks_emitsCurrentValue() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureRecentTaskRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    recentTaskListProvider =
                        fakeRecentTaskListProvider.apply {
                            fakeRecentTasks = listOf(fakeRecentTask1)
                        },
                    taskStackChangeListeners = taskStackChangeListeners,
                )

            // Act
            val result = currentValue(repository.recentTasks)

            // Assert
            assertThat(result).containsExactly(fakeRecentTask1)
        }

    @Test
    fun recentTasks_whenTaskStackChanges_Updates() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureRecentTaskRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    recentTaskListProvider =
                        fakeRecentTaskListProvider.apply {
                            fakeRecentTasks = listOf(fakeRecentTask1)
                        },
                    taskStackChangeListeners = taskStackChangeListeners,
                )
            var result: List<RecentTask>? = null
            val job = testScope.launch { repository.recentTasks.collect { result = it } }
            assertThat(result).containsExactly(fakeRecentTask1)

            // Act
            fakeRecentTaskListProvider.fakeRecentTasks = listOf(fakeRecentTask1, fakeRecentTask2)
            taskStackChangeListeners.listenerImpl.onTaskStackChanged()

            // Assert
            assertThat(result).containsExactly(fakeRecentTask1, fakeRecentTask2).inOrder()

            // Cleanup
            job.cancel()
        }

    private class FakeRecentTaskListProvider : RecentTaskListProvider {

        var fakeRecentTasks = emptyList<RecentTask>()
        var loadRecentTasksCallCount = 0

        override suspend fun loadRecentTasks(): List<RecentTask> {
            loadRecentTasksCallCount++
            return fakeRecentTasks
        }
    }
}
