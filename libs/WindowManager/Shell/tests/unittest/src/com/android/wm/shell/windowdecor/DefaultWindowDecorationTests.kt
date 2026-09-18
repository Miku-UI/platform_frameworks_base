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

package com.android.wm.shell.windowdecor

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Region
import android.os.Looper
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.Display.DEFAULT_DISPLAY
import android.view.InsetsState
import android.view.WindowInsets.Type.statusBars
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestHandler
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFullscreenTask
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.windowdecor.WindowDecorationTestHelper.addInsetsSource
import com.android.wm.shell.windowdecor.common.CaptionVisibilityHelper
import com.android.wm.shell.windowdecor.common.WindowDecorationGestureExclusionTracker
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [DefaultWindowDecoration].
 *
 * Build/Install/Run: atest WMShellUnitTests:DefaultWindowDecorationTests
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class DefaultWindowDecorationTests : ShellTestCase() {
    private val mockSplitScreenController = mock<SplitScreenController>()
    private val mockDesktopTasksController = mock<DesktopTasksController>()
    private val mockTaskOperations = mock<TaskOperations>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockWindowDecorationExclusionTracker =
        mock<WindowDecorationGestureExclusionTracker>()
    private val mockWindowDecorationActions = mock<WindowDecorationActions>()
    private val mockCaptionVisibilityHelper = mock<CaptionVisibilityHelper>()

    private val testDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
    private val testHandler = TestHandler(Looper.getMainLooper())
    private val testExecutor = TestShellExecutor()
    private val shellInit = ShellInit(testExecutor)
    private val testScope = TestScope(testDispatcher)
    private val desktopState = FakeDesktopState()
    private val desktopConfig = FakeDesktopConfig()
    private val insetsState = InsetsState()

    private lateinit var userRepositories: DesktopUserRepositories

    @Before
    fun setup() {
        whenever(mockDisplayController.getInsetsState(anyInt())).thenReturn(insetsState)
        whenever(mockCaptionVisibilityHelper.shouldCreateCaption(any(), anyBoolean()))
            .thenReturn(true)
        userRepositories =
            DesktopUserRepositories(
                shellInit,
                mock(),
                mock(),
                mock(),
                testScope.backgroundScope,
                testScope.backgroundScope,
                mock(),
                desktopState,
                desktopConfig,
            )
        userRepositories.current.apply {
            addDesk(DEFAULT_DISPLAY, DEFAULT_DESK_ID)
            setActiveDesk(DEFAULT_DISPLAY, DEFAULT_DESK_ID)
        }
        shellInit.init()
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS)
    fun isCaptionVisible_onTaskDrag_returnsTrue() {
        val task = WindowDecorationTestHelper.createAppHeaderTask()
        val decoration = setUpWindowDecoration(task)
        decoration.isDragging = true

        val relayoutParams = decoration.getRelayoutParams(task)

        assertTrue(relayoutParams.isCaptionVisible)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS)
    fun isCaptionVisible_shouldCreateCaptionIsFalse_returnsFalse() {
        whenever(mockCaptionVisibilityHelper.shouldCreateCaption(any(), anyBoolean()))
            .thenReturn(false)
        val task = WindowDecorationTestHelper.createAppHeaderTask()
        val decoration = setUpWindowDecoration(task)

        val relayoutParams = decoration.getRelayoutParams(task)

        assertFalse(relayoutParams.isCaptionVisible)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS)
    fun isCaptionVisible_inImmersive_returnsFalse() {
        insetsState.addInsetsSource(type = statusBars(), visible = false)
        val task = createFullscreenTask()
        val decoration = setUpWindowDecoration(task)

        val relayoutParams = decoration.getRelayoutParams(task)

        assertFalse(relayoutParams.isCaptionVisible)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS)
    fun isCaptionVisible_inFullImmersive_returnsFalse() {
        insetsState.addInsetsSource(type = statusBars(), visible = false)
        val task = WindowDecorationTestHelper.createAppHeaderTask()
        val decoration = setUpWindowDecoration(task)
        userRepositories.current.setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = true,
        )

        val relayoutParams = decoration.getRelayoutParams(task)

        assertFalse(relayoutParams.isCaptionVisible)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS)
    fun isCaptionVisible_statusBarVisible_returnsTrue() {
        insetsState.addInsetsSource(type = statusBars(), visible = true)
        val task = WindowDecorationTestHelper.createAppHeaderTask()
        val decoration = setUpWindowDecoration(task)

        val relayoutParams = decoration.getRelayoutParams(task)

        assertTrue(relayoutParams.isCaptionVisible)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS)
    fun isCaptionVisible_fullscreen_returnsTrue() {
        insetsState.addInsetsSource(type = statusBars(), visible = true)
        val task = createFullscreenTask()
        val decoration = setUpWindowDecoration(task)

        val relayoutParams = decoration.getRelayoutParams(task)

        assertTrue(relayoutParams.isCaptionVisible)
    }

    private fun setUpWindowDecoration(taskInfo: RunningTaskInfo): DefaultWindowDecoration {
        val wrappedDecoration =
            WindowDecorationTestHelper.createWindowDecoration(
                    context = context,
                    taskInfo = taskInfo,
                    desktopState = desktopState,
                    desktopConfig = desktopConfig,
                    scope = testScope.backgroundScope,
                    handler = testHandler,
                    executor = testExecutor,
                    displayController = mockDisplayController,
                    desktopUserRepositories = userRepositories,
                    splitScreenController = mockSplitScreenController,
                    desktopTasksController = mockDesktopTasksController,
                    taskOperations = mockTaskOperations,
                    windowDecorationActions = mockWindowDecorationActions,
                    captionVisibilityHelper = mockCaptionVisibilityHelper,
                    windowDecorationExclusionTracker = mockWindowDecorationExclusionTracker,
                )
                .also { it.relayout(taskInfo) }
        return wrappedDecoration.defaultWindowDecoration
    }

    private fun DefaultWindowDecoration.getRelayoutParams(
        taskInfo: RunningTaskInfo,
        applyStartTransactionOnDraw: Boolean = false,
        shouldSetTaskVisibilityPositionAndCrop: Boolean = false,
        hasGlobalFocus: Boolean = true,
        displayExclusionRegion: Region = Region.obtain(),
        shouldIgnoreCornerRadius: Boolean = false,
        shouldExcludeCaptionFromAppBounds: Boolean = false,
        inSyncWithTransition: Boolean = false,
    ): WindowDecoration2.RelayoutParams =
        getRelayoutParams(
            taskInfo = taskInfo,
            splitScreenController = mockSplitScreenController,
            applyStartTransactionOnDraw = applyStartTransactionOnDraw,
            shouldSetTaskVisibilityPositionAndCrop = shouldSetTaskVisibilityPositionAndCrop,
            hasGlobalFocus = hasGlobalFocus,
            displayExclusionRegion = displayExclusionRegion,
            shouldIgnoreCornerRadius = shouldIgnoreCornerRadius,
            shouldExcludeCaptionFromAppBounds = shouldExcludeCaptionFromAppBounds,
            desktopConfig = desktopConfig,
            inSyncWithTransition = inSyncWithTransition,
        )

    companion object {
        private const val DEFAULT_DESK_ID = 0
    }
}
