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

package com.android.wm.shell.fullscreen

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.ComponentName
import android.platform.test.annotations.EnableFlags
import android.window.DisplayAreaInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.desktopmode.DesktopWallpaperActivity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.eq

/**
 * Tests for [FullscreenDisconnectHandler].
 *
 * Usage: atest
 * WMShellUnitTests:FullscreenDisconnectHandlerTest#onDisplayDisconnect_reparentsValidFullscreenTask
 */
@SmallTest
@RunWith(JUnit4::class)
class FullscreenDisconnectHandlerTest : ShellTestCase() {

    private val shellTaskOrganizer = mock(ShellTaskOrganizer::class.java)
    private val rootTaskDisplayAreaOrganizer = mock(RootTaskDisplayAreaOrganizer::class.java)
    private val displayAreaInfo = DisplayAreaInfo(MockToken().token(), REPARENT_DISPLAY_ID, 0)
    private val fullscreenReconnectHandler = mock(FullscreenReconnectHandler::class.java)

    private lateinit var handler: FullscreenDisconnectHandler

    private val runningTasks = arrayListOf<RunningTaskInfo>()

    @Before
    fun setUp() {
        handler =
            FullscreenDisconnectHandler(
                shellTaskOrganizer,
                rootTaskDisplayAreaOrganizer,
                fullscreenReconnectHandler,
            )

        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(REPARENT_DISPLAY_ID))
            .thenReturn(displayAreaInfo)
        whenever(shellTaskOrganizer.getRunningTasks(DISCONNECTED_DISPLAY_ID))
            .thenReturn(runningTasks)
    }

    @After
    fun tearDown() {
        runningTasks.clear()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_FULLSCREEN)
    fun onDisplayDisconnect_reparentsValidFullscreenTask() {
        val validTask =
            setupTask(
                taskId = 1,
                windowingMode = WINDOWING_MODE_FULLSCREEN,
                activityType = ACTIVITY_TYPE_STANDARD,
            )

        val wct =
            handler.onDisplayDisconnect(
                disconnectedDisplayId = DISCONNECTED_DISPLAY_ID,
                reparentDisplay = REPARENT_DISPLAY_ID,
            )

        assertThat(wct.hasReparentHop(validTask.token, displayAreaInfo.token, false)).isTrue()
        verify(fullscreenReconnectHandler)
            .preserveTask(eq(validTask.taskId), eq(DISCONNECTED_DISPLAY_ID), anyInt(), eq(false))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_FULLSCREEN)
    fun onDisplayDisconnect_taskIsNotFullscreen_wctIsEmpty() {
        setupTask(
            taskId = 1,
            windowingMode = WINDOWING_MODE_FREEFORM,
            activityType = ACTIVITY_TYPE_STANDARD,
        )

        val wct =
            handler.onDisplayDisconnect(
                disconnectedDisplayId = DISCONNECTED_DISPLAY_ID,
                reparentDisplay = REPARENT_DISPLAY_ID,
            )

        assertThat(wct.isEmpty).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_FULLSCREEN)
    fun onDisplayDisconnect_taskIsNonStandardActivityType_wctIsEmpty() {
        setupTask(
            taskId = 1,
            windowingMode = WINDOWING_MODE_FULLSCREEN,
            activityType = ACTIVITY_TYPE_HOME,
        )

        val wct =
            handler.onDisplayDisconnect(
                disconnectedDisplayId = DISCONNECTED_DISPLAY_ID,
                reparentDisplay = REPARENT_DISPLAY_ID,
            )

        assertThat(wct.isEmpty).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_FULLSCREEN)
    fun onDisplayDisconnect_taskIsDesktopWallpaper_wctIsEmpty() {
        setupTask(
                taskId = 1,
                windowingMode = WINDOWING_MODE_FULLSCREEN,
                activityType = ACTIVITY_TYPE_STANDARD,
            )
            .apply {
                baseIntent.component =
                    ComponentName("com.android.systemui", DesktopWallpaperActivity::class.java.name)
            }

        val wct =
            handler.onDisplayDisconnect(
                disconnectedDisplayId = DISCONNECTED_DISPLAY_ID,
                reparentDisplay = REPARENT_DISPLAY_ID,
            )

        assertThat(wct.isEmpty).isTrue()
    }

    // TODO (b/458088732): Consolidate these functions with their desktop counterparts.
    private fun setupTask(
        taskId: Int,
        windowingMode: Int = WINDOWING_MODE_FULLSCREEN,
        activityType: Int = ACTIVITY_TYPE_STANDARD,
    ): RunningTaskInfo {
        val taskInfo = createTaskInfo(taskId, windowingMode, activityType = activityType)
        whenever(shellTaskOrganizer.getRunningTaskInfo(taskId)).thenReturn(taskInfo)
        runningTasks.add(taskInfo)
        return taskInfo
    }

    private fun createTaskInfo(
        taskId: Int,
        windowingMode: Int,
        activityType: Int,
    ): RunningTaskInfo {
        return TestRunningTaskInfoBuilder()
            .setActivityType(activityType)
            .setWindowingMode(windowingMode)
            .build()
            .apply {
                this.taskId = taskId
                this.token = mock(WindowContainerToken::class.java)
            }
    }

    private fun WindowContainerTransaction.hasReparentHop(
        taskToken: WindowContainerToken,
        newParent: WindowContainerToken,
        toTop: Boolean,
    ): Boolean {
        return hierarchyOps.any { hop ->
            hop.isReparent &&
                hop.container == taskToken.asBinder() &&
                hop.newParent == newParent.asBinder() &&
                hop.toTop == toTop
        }
    }

    companion object {
        private const val DISCONNECTED_DISPLAY_ID = 1
        private const val REPARENT_DISPLAY_ID = 0
    }
}
