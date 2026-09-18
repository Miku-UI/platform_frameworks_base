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
package com.android.wm.shell.common

import android.app.ActivityManager
import android.app.ActivityManager.RecentTaskInfo
import android.app.PendingIntent
import android.app.TaskInfo
import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.recents.RecentTasksController
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ComponentUtilsTest {

    private val mockTaskOrganizer = mock<ShellTaskOrganizer>()
    private val mockRecentTasksController = mock<RecentTasksController>()
    private val mockPendingIntent = mock<PendingIntent>()

    private val testPackage = "yummy.airline.food"
    private val testComponentName = ComponentName(testPackage, "TestClass")
    private val testIntent = Intent().apply { component = testComponentName }
    private val testRunningTaskInfo =
        ActivityManager.RunningTaskInfo().apply { baseIntent = testIntent }
    private val testRecentTaskInfo = RecentTaskInfo().apply { baseIntent = testIntent }

    @Before
    fun setUp() {
        whenever(mockPendingIntent.intent) doReturn testIntent
    }

    @Test
    fun getPackageName_fromIntent_returnsPackageName() {
        assertThat(ComponentUtils.getPackageName(testIntent)).isEqualTo(testPackage)
    }

    @Test
    fun getPackageName_fromIntent_noComponent_returnsPackage() {
        val intent = Intent().apply { setPackage(testPackage) }
        assertThat(ComponentUtils.getPackageName(intent)).isEqualTo(testPackage)
    }

    @Test
    fun getPackageName_fromIntent_nullIntent_returnsNull() {
        assertThat(ComponentUtils.getPackageName(null as Intent?)).isNull()
    }

    @Test
    fun getPackageName_fromPendingIntent_returnsPackageName() {
        assertThat(ComponentUtils.getPackageName(mockPendingIntent)).isEqualTo(testPackage)
    }

    @Test
    fun getPackageName_fromPendingIntent_nullIntent_returnsNull() {
        whenever(mockPendingIntent.intent) doReturn null
        assertThat(ComponentUtils.getPackageName(mockPendingIntent)).isNull()
    }

    @Test
    fun getPackageName_fromPendingIntent_nullPendingIntent_returnsNull() {
        assertThat(ComponentUtils.getPackageName(null as PendingIntent?)).isNull()
    }

    @Test
    fun getPackageName_fromTaskInfo_returnsPackageName() {
        assertThat(ComponentUtils.getPackageName(testRunningTaskInfo)).isEqualTo(testPackage)
    }

    @Test
    fun getPackageName_fromTaskInfo_nullBaseIntent_returnsNull() {
        val taskInfo = RecentTaskInfo()
        assertThat(ComponentUtils.getPackageName(taskInfo)).isNull()
    }

    @Test
    fun getPackageName_fromTaskInfo_nullTaskInfo_returnsNull() {
        assertThat(ComponentUtils.getPackageName(null as TaskInfo?)).isNull()
    }

    @Test
    fun getPackageName_fromTaskIdAndTaskOrganizer_returnsPackageName() {
        val taskId = 1
        whenever(mockTaskOrganizer.getRunningTaskInfo(taskId)) doReturn testRunningTaskInfo
        assertThat(ComponentUtils.getPackageName(taskId, mockTaskOrganizer)).isEqualTo(testPackage)
    }

    @Test
    fun getPackageName_fromTaskIdAndTaskOrganizer_taskNotFound_returnsNull() {
        val taskId = 1
        whenever(mockTaskOrganizer.getRunningTaskInfo(taskId)) doReturn null
        assertThat(ComponentUtils.getPackageName(taskId, mockTaskOrganizer)).isNull()
    }

    @Test
    fun getPackageName_fromTaskIdAndRecentTasksController_returnsPackageName() {
        val taskId = 1
        whenever(mockRecentTasksController.findTaskInBackground(taskId)) doReturn testRecentTaskInfo
        assertThat(ComponentUtils.getPackageName(taskId, mockRecentTasksController))
            .isEqualTo(testPackage)
    }

    @Test
    fun getPackageName_fromTaskIdAndRecentTasksController_taskNotFound_returnsNull() {
        val taskId = 1
        whenever(mockRecentTasksController.findTaskInBackground(taskId)) doReturn null
        assertThat(ComponentUtils.getPackageName(taskId, mockRecentTasksController)).isNull()
    }

    @Test
    fun getPackageName_fromTaskIdAndRecentTasksController_nullController_returnsNull() {
        val taskId = 1
        assertThat(ComponentUtils.getPackageName(taskId, null as RecentTasksController?)).isNull()
    }
}
