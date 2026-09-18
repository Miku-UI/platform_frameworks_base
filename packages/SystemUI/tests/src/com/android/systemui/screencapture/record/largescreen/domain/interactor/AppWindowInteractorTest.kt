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

package com.android.systemui.screencapture.record.largescreen.domain.interactor

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.content.ComponentName
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.app.activityTaskManager
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class AppWindowInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val activityTaskManager = kosmos.activityTaskManager

    private val displayId = 1

    @Test
    fun getAppWindowTasks_filtersByDisplayId() =
        kosmos.runTest {
            val taskOnCorrectDisplay = createRunningTaskInfo(taskId = 1, displayId = displayId)
            val taskOnOtherDisplay = createRunningTaskInfo(taskId = 2, displayId = 9999)
            val runningTasks = listOf(taskOnCorrectDisplay, taskOnOtherDisplay)
            whenever(activityTaskManager.getTasks(any())).thenReturn(runningTasks)

            val result = appWindowInteractor.getAppWindowTasks(displayId)

            assertThat(result).containsExactly(taskOnCorrectDisplay)
        }

    @Test
    fun getAppWindowTasks_filtersInvisibleTasks() =
        kosmos.runTest {
            val visibleTask = createRunningTaskInfo(taskId = 1, isVisible = true)
            val invisibleTask = createRunningTaskInfo(taskId = 2, isVisible = false)
            val runningTasks = listOf(visibleTask, invisibleTask)
            whenever(activityTaskManager.getTasks(any())).thenReturn(runningTasks)

            val result = appWindowInteractor.getAppWindowTasks(displayId)

            assertThat(result).containsExactly(visibleTask)
        }

    @Test
    fun getAppWindowTasks_filtersNonStandardActivityTypes() =
        kosmos.runTest {
            val standardTask =
                createRunningTaskInfo(
                    taskId = 1,
                    activityType = WindowConfiguration.ACTIVITY_TYPE_STANDARD,
                )
            val homeTask =
                createRunningTaskInfo(
                    taskId = 2,
                    activityType = WindowConfiguration.ACTIVITY_TYPE_HOME,
                )
            val runningTasks = listOf(standardTask, homeTask)
            whenever(activityTaskManager.getTasks(any())).thenReturn(runningTasks)

            val result = appWindowInteractor.getAppWindowTasks(displayId)

            assertThat(result).containsExactly(standardTask)
        }

    @Test
    fun getAppWindowTasks_filtersTasksWithNoTopActivity() =
        kosmos.runTest {
            val withTopActivity = createRunningTaskInfo(taskId = 1, hasTopActivity = true)
            val withoutTopActivity = createRunningTaskInfo(taskId = 2, hasTopActivity = false)
            val runningTasks = listOf(withTopActivity, withoutTopActivity)
            whenever(activityTaskManager.getTasks(any())).thenReturn(runningTasks)

            val result = appWindowInteractor.getAppWindowTasks(displayId)

            assertThat(result).containsExactly(withTopActivity)
        }

    @Test
    fun getAppWindowTasks_preservesZOrder() =
        kosmos.runTest {
            val task1 = createRunningTaskInfo(taskId = 1)
            val task2 = createRunningTaskInfo(taskId = 2)
            val task3 = createRunningTaskInfo(taskId = 3)
            val runningTasks = listOf(task1, task2, task3) // Z-order: task1 on top
            whenever(activityTaskManager.getTasks(any())).thenReturn(runningTasks)

            val result = appWindowInteractor.getAppWindowTasks(displayId)

            assertThat(result).containsExactly(task1, task2, task3).inOrder()
        }

    @Test
    fun getAppWindowTasks_filtersSystemUITasks() =
        kosmos.runTest {
            val systemUITask =
                createRunningTaskInfo(taskId = 1, packageName = "com.android.systemui")
            val otherTask = createRunningTaskInfo(taskId = 2, packageName = "com.other.pkg")
            val runningTasks = listOf(systemUITask, otherTask)
            whenever(activityTaskManager.getTasks(any())).thenReturn(runningTasks)

            val result = appWindowInteractor.getAppWindowTasks(displayId)

            assertThat(result).containsExactly(otherTask)
        }

    @Test
    fun getAppWindowTasks_filtersTasksWithNoActivities() =
        kosmos.runTest {
            val withActivities = createRunningTaskInfo(taskId = 1, numActivities = 1)
            val withoutActivities = createRunningTaskInfo(taskId = 2, numActivities = 0)
            val runningTasks = listOf(withActivities, withoutActivities)
            whenever(activityTaskManager.getTasks(any())).thenReturn(runningTasks)

            val result = appWindowInteractor.getAppWindowTasks(displayId)

            assertThat(result).containsExactly(withActivities)
        }

    private fun createRunningTaskInfo(
        taskId: Int,
        displayId: Int = this.displayId,
        isVisible: Boolean = true,
        activityType: Int = WindowConfiguration.ACTIVITY_TYPE_STANDARD,
        hasTopActivity: Boolean = true,
        packageName: String = "test.pkg",
        numActivities: Int = 1,
    ): ActivityManager.RunningTaskInfo {
        return ActivityManager.RunningTaskInfo().apply {
            this.taskId = taskId
            this.isVisible = isVisible
            this.displayId = displayId
            this.numActivities = numActivities
            this.topActivity =
                if (hasTopActivity) ComponentName(packageName, "test.class") else null
            this.configuration.windowConfiguration.apply {
                setBounds(Rect(0, 0, 100, 100))
                this.activityType = activityType
            }
        }
    }
}
