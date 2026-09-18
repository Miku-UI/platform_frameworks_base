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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.Context
import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.window.DisplayAreaInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.pip2.phone.PipDisplayTransferHandler
import com.android.wm.shell.pip2.phone.PipTransitionState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

/**
 * Test class for {@link PipDisplayReconnectHandlerTest}
 *
 * Usage: atest WMShellUnitTests:PipDisplayReconnectHandlerTest
 *
 * TODO: b/488139835 Consider moving this out of desktop package as it becomes less
 *   desktop-specific.
 */
@SmallTest
@EnableFlags(Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_PIP)
class PipDisplayReconnectHandlerTest : ShellTestCase() {
    private val keyguardManager = mock(KeyguardManager::class.java)
    private val displayController = mock(DisplayController::class.java)
    private val shellTaskOrganizer = mock(ShellTaskOrganizer::class.java)
    private val rootTaskDisplayAreaOrganizer = mock(RootTaskDisplayAreaOrganizer::class.java)
    private val shellController = mock(ShellController::class.java)
    private val context = mock(Context::class.java)

    private val mockDisplay = mock(Display::class.java)
    private val tda = DisplayAreaInfo(MockToken().token(), TEST_DISPLAY_ID, 0)

    private val onRootTdaListenerCaptor = argumentCaptor<RootTaskDisplayAreaListener>()
    private val runningTasks = arrayListOf<ActivityManager.RunningTaskInfo>()
    private val pipState = mock(PipTransitionState::class.java)
    private val pipDisplayTransferHandler = mock(PipDisplayTransferHandler::class.java)

    private lateinit var handler: PipDisplayReconnectHandler
    private val shellInit = mock(ShellInit::class.java)

    @Before
    fun setUp() {
        handler =
            PipDisplayReconnectHandler(
                keyguardManager,
                displayController,
                rootTaskDisplayAreaOrganizer,
                shellController,
                pipState,
                pipDisplayTransferHandler,
                shellInit,
            )

        whenever(shellController.currentUserId).thenReturn(TEST_USER_ID)
        whenever(displayController.getDisplay(TEST_DISPLAY_ID)).thenReturn(mockDisplay)
        whenever(mockDisplay.uniqueId).thenReturn(TEST_UNIQUE_DISPLAY_ID)
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(TEST_DISPLAY_ID)).thenReturn(tda)
        whenever(shellTaskOrganizer.getRunningTasks(DEFAULT_DISPLAY)).thenReturn(runningTasks)
        addDisplay(DEFAULT_DISPLAY)
        addDisplay(TEST_DISPLAY_ID)
    }

    @After
    fun tearDown() {
        runningTasks.clear()
    }

    @Test
    fun testHandlePotentialReconnect_onKeyguardUnlock() {
        val pipBounds = Rect(0, 0, 100, 100)
        handler.preserveTask(
            taskId = TEST_TASK_ID,
            displayId = TEST_DISPLAY_ID,
            userId = TEST_USER_ID,
            pipBounds = pipBounds,
        )
        whenever(keyguardManager.isKeyguardLocked).thenReturn(false)
        val displaysByUniqueId = mapOf(TEST_UNIQUE_DISPLAY_ID to TEST_DISPLAY_ID)
        whenever(displayController.allDisplaysByUniqueId).thenReturn(displaysByUniqueId)
        val pipTask = setupTask(TEST_TASK_ID, isVisible = true)

        handler.onKeyguardVisibilityChanged(
            visible = false,
            occluded = false,
            animatingDismiss = false,
        )

        verify(pipDisplayTransferHandler)
            .scheduleMovePipToDisplay(pipTask.displayId, TEST_DISPLAY_ID, pipBounds)
    }

    @Test
    fun testHandlePotentialReconnect_onDisplayAdded() {
        val pipTask = setupTask(TEST_TASK_ID, isVisible = true)
        val pipBounds = Rect(0, 0, 100, 100)
        handler.preserveTask(
            taskId = TEST_TASK_ID,
            displayId = TEST_DISPLAY_ID,
            userId = TEST_USER_ID,
            pipBounds = pipBounds,
        )
        whenever(keyguardManager.isKeyguardLocked).thenReturn(false)

        onRootTdaListenerCaptor.lastValue.onDisplayAreaAppeared(
            createDisplayAreaInfo(TEST_DISPLAY_ID)
        )

        verify(pipDisplayTransferHandler)
            .scheduleMovePipToDisplay(pipTask.displayId, TEST_DISPLAY_ID, pipBounds)
    }

    @Test
    fun testHandlePotentialReconnect_onUserChanged() {
        val newUserId = TEST_USER_ID + 1
        val pipTask = setupTask(TEST_TASK_ID, isVisible = true)
        val pipBounds = Rect(0, 0, 100, 100)
        handler.preserveTask(
            taskId = TEST_TASK_ID,
            displayId = TEST_DISPLAY_ID,
            userId = newUserId,
            pipBounds = pipBounds,
        )
        whenever(keyguardManager.isKeyguardLocked).thenReturn(false)
        whenever(displayController.allDisplaysByUniqueId)
            .thenReturn(mapOf(TEST_UNIQUE_DISPLAY_ID to TEST_DISPLAY_ID))

        handler.onUserChanged(newUserId, context)

        verify(pipDisplayTransferHandler)
            .scheduleMovePipToDisplay(pipTask.displayId, TEST_DISPLAY_ID, pipBounds)
    }

    @Test
    fun testHandlePotentialReconnect_extendedMode_constructsWctToReparentTask() {
        val pipTask = setupTask(TEST_TASK_ID, isVisible = true)
        val pipBounds = Rect(0, 0, 100, 100)
        handler.preserveTask(
            taskId = TEST_TASK_ID,
            displayId = TEST_DISPLAY_ID,
            userId = TEST_USER_ID,
            pipBounds = pipBounds,
        )
        whenever(keyguardManager.isKeyguardLocked).thenReturn(false)
        val displaysByUniqueId = mapOf(TEST_UNIQUE_DISPLAY_ID to TEST_DISPLAY_ID)
        whenever(displayController.allDisplaysByUniqueId).thenReturn(displaysByUniqueId)

        handler.onKeyguardVisibilityChanged(
            visible = false,
            occluded = false,
            animatingDismiss = false,
        )

        verify(pipDisplayTransferHandler)
            .scheduleMovePipToDisplay(pipTask.displayId, TEST_DISPLAY_ID, pipBounds)
    }

    @Test
    fun testHandlePotentialReconnect_notTriggeredWhileKeyguardLocked() {
        val pipTask = setupTask(TEST_TASK_ID, isVisible = true)
        val pipBounds = Rect(0, 0, 100, 100)
        handler.preserveTask(
            taskId = TEST_TASK_ID,
            displayId = TEST_DISPLAY_ID,
            userId = TEST_USER_ID,
            pipBounds = pipBounds,
        )
        whenever(keyguardManager.isKeyguardLocked).thenReturn(true)
        val displaysByUniqueId = mapOf(TEST_UNIQUE_DISPLAY_ID to TEST_DISPLAY_ID)
        whenever(displayController.allDisplaysByUniqueId).thenReturn(displaysByUniqueId)

        handler.onDisplayAdded(TEST_DISPLAY_ID)

        verify(pipDisplayTransferHandler, never())
            .scheduleMovePipToDisplay(pipTask.displayId, TEST_DISPLAY_ID, pipBounds)
    }

    // TODO (b/458088732): Consolidate these functions with their desktop counterparts.
    private fun setupTask(
        taskId: Int,
        windowingMode: Int = WINDOWING_MODE_FULLSCREEN,
        displayId: Int = DEFAULT_DISPLAY,
        isVisible: Boolean,
    ): ActivityManager.RunningTaskInfo {
        val taskInfo = createTaskInfo(taskId, windowingMode, isVisible, displayId)
        whenever(shellTaskOrganizer.getRunningTaskInfo(taskId)).thenReturn(taskInfo)
        whenever(pipState.pipTaskInfo).thenReturn(taskInfo)
        runningTasks.add(taskInfo)
        return taskInfo
    }

    private fun createTaskInfo(
        taskId: Int,
        windowingMode: Int,
        isVisible: Boolean = false,
        displayId: Int = DEFAULT_DISPLAY,
    ): ActivityManager.RunningTaskInfo {
        return TestRunningTaskInfoBuilder().setWindowingMode(windowingMode).build().apply {
            this.taskId = taskId
            this.isVisible = isVisible
            this.token = mock(WindowContainerToken::class.java)
            this.displayId = displayId
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

    private fun addDisplay(displayId: Int) {
        handler.onDisplayAdded(displayId)
        verify(rootTaskDisplayAreaOrganizer)
            .registerListener(eq(displayId), onRootTdaListenerCaptor.capture())
        onRootTdaListenerCaptor.lastValue.onDisplayAreaAppeared(createDisplayAreaInfo(displayId))
    }

    private fun createDisplayAreaInfo(displayId: Int) =
        DisplayAreaInfo(/* token= */ mock(), displayId, /* featureId= */ 0)

    companion object {
        private const val TEST_DISPLAY_ID = 1
        private const val TEST_UNIQUE_DISPLAY_ID = "test_unique_display_id"
        private const val TEST_TASK_ID = 101
        private const val TEST_USER_ID = 0
    }
}
