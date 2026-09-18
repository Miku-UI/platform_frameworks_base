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
package com.android.wm.shell.desktopmode

import android.app.ActivityManager
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.transition.FocusTransitionObserver
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyInt
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

/**
 * Tests for [ShellDesktopStateImpl].
 *
 * Build/Install/Run: atest WMShellUnitTests:ShellDesktopStateImplTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class ShellDesktopStateImplTest : ShellTestCase() {
    private lateinit var desktopState: FakeDesktopState
    private val mockUserRepositories = mock<DesktopUserRepositories>()
    private val mockDesktopRepository = mock<DesktopRepository>()
    private val mockFocusTransitionObserver = mock<FocusTransitionObserver>()
    private val mockShellController = mock<ShellController>()
    private val mockShellTaskOrganizer = mock<ShellTaskOrganizer>()

    private lateinit var mShellDesktopState: ShellDesktopStateImpl

    @Before
    fun setUp() {
        whenever(mockUserRepositories.getProfile(anyInt())).thenReturn(mockDesktopRepository)
        desktopState = FakeDesktopState()

        mShellDesktopState =
            ShellDesktopStateImpl(
                desktopState,
                mockUserRepositories,
                mockFocusTransitionObserver,
                mockShellController,
                mockShellTaskOrganizer,
            )
    }

    private fun createTask(activityType: Int): ActivityManager.RunningTaskInfo {
        val taskInfo = TestRunningTaskInfoBuilder().setActivityType(activityType).build()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(taskInfo.taskId)).thenReturn(taskInfo)
        return taskInfo
    }

    @Test
    @DisableFlags(Flags.FLAG_ALLOW_DRAG_AND_DROP_WHEN_INTERACTIVE_BUGFIX)
    fun testIsEligibleWindowDropTarget_inActiveDesktop_returnsTrue() {
        whenever(mockDesktopRepository.getActiveDeskId(DISPLAY_ID)).thenReturn(1)

        assertTrue(mShellDesktopState.isEligibleWindowDropTarget(DISPLAY_ID))
    }

    @Test
    @DisableFlags(Flags.FLAG_ALLOW_DRAG_AND_DROP_WHEN_INTERACTIVE_BUGFIX)
    fun testIsEligibleWindowDropTarget_homeFocusedOnDesktopSupportedDisplay_returnsTrue() {
        val taskInfo = createTask(ACTIVITY_TYPE_HOME)
        desktopState.canEnterDesktopMode = true
        whenever(mockDesktopRepository.getActiveDeskId(DISPLAY_ID)).thenReturn(null)
        whenever(mockFocusTransitionObserver.getFocusedTaskIdOnDisplay(DISPLAY_ID))
            .thenReturn(taskInfo.taskId)

        assertTrue(mShellDesktopState.isEligibleWindowDropTarget(DISPLAY_ID))
    }

    @Test
    @DisableFlags(Flags.FLAG_ALLOW_DRAG_AND_DROP_WHEN_INTERACTIVE_BUGFIX)
    fun testIsEligibleWindowDropTarget_focusedTaskIsNullOnDesktopSupportedDisplay_returnsTrue() {
        desktopState.canEnterDesktopMode = true
        whenever(mockDesktopRepository.getActiveDeskId(DISPLAY_ID)).thenReturn(null)
        whenever(mockFocusTransitionObserver.getFocusedTaskIdOnDisplay(DISPLAY_ID))
            .thenReturn(INVALID_TASK_ID)

        assertTrue(mShellDesktopState.isEligibleWindowDropTarget(DISPLAY_ID))
    }

    @Test
    @DisableFlags(Flags.FLAG_ALLOW_DRAG_AND_DROP_WHEN_INTERACTIVE_BUGFIX)
    fun testIsEligibleWindowDropTarget_notInDesktopAndHomeNotFocused_returnsFalse() {
        val taskInfo = createTask(ACTIVITY_TYPE_STANDARD)
        desktopState.canEnterDesktopMode = true
        whenever(mockDesktopRepository.getActiveDeskId(DISPLAY_ID)).thenReturn(null)
        whenever(mockFocusTransitionObserver.getFocusedTaskIdOnDisplay(DISPLAY_ID))
            .thenReturn(taskInfo.taskId)

        assertFalse(mShellDesktopState.isEligibleWindowDropTarget(DISPLAY_ID))
    }

    @Test
    @DisableFlags(Flags.FLAG_ALLOW_DRAG_AND_DROP_WHEN_INTERACTIVE_BUGFIX)
    fun testIsEligibleWindowDropTarget_displayDoesNotSupportDesktop_returnsFalse() {
        val taskInfo = createTask(ACTIVITY_TYPE_HOME)
        desktopState.canEnterDesktopMode = false
        whenever(mockDesktopRepository.getActiveDeskId(DISPLAY_ID)).thenReturn(null)
        whenever(mockFocusTransitionObserver.getFocusedTaskIdOnDisplay(DISPLAY_ID))
            .thenReturn(taskInfo.taskId)

        assertFalse(mShellDesktopState.isEligibleWindowDropTarget(DISPLAY_ID))
    }

    @Test
    @EnableFlags(Flags.FLAG_ALLOW_DRAG_AND_DROP_WHEN_INTERACTIVE_BUGFIX)
    fun testIsEligibleWindowDropTarget_deskInteractive_returnsTrue() {
        val taskInfo = TestRunningTaskInfoBuilder().setTaskId(DESK_ID).build()
        taskInfo.isInteractive = true
        mockDesktopRepository.stub { on { getActiveDeskId(DISPLAY_ID) } doReturn DESK_ID }
        mockShellTaskOrganizer.stub { on { getRunningTaskInfo(DESK_ID) } doReturn taskInfo }

        assertTrue(mShellDesktopState.isEligibleWindowDropTarget(DISPLAY_ID))
    }

    @Test
    @EnableFlags(Flags.FLAG_ALLOW_DRAG_AND_DROP_WHEN_INTERACTIVE_BUGFIX)
    fun testIsEligibleWindowDropTarget_homeInteractiveOnDesktopSupportedDisplay_returnsTrue() {
        val taskInfo = createTask(ACTIVITY_TYPE_HOME)
        taskInfo.isInteractive = true
        desktopState.canEnterDesktopMode = true
        mockDesktopRepository.stub { on { getActiveDeskId(DISPLAY_ID) } doReturn null }
        mockShellTaskOrganizer.stub {
            on { getRunningTasks(DISPLAY_ID) } doReturn arrayListOf(taskInfo)
        }

        assertTrue(mShellDesktopState.isEligibleWindowDropTarget(DISPLAY_ID))
    }

    @Test
    @EnableFlags(Flags.FLAG_ALLOW_DRAG_AND_DROP_WHEN_INTERACTIVE_BUGFIX)
    fun testIsEligibleWindowDropTarget_wallpaperInteractiveOnDesktopSupportedDisplay_returnsTrue() {
        val intent =
            Intent().apply { component = DesktopWallpaperActivity.wallpaperActivityComponent }
        val taskInfo = TestRunningTaskInfoBuilder().setBaseIntent(intent).build()
        taskInfo.isInteractive = true
        desktopState.canEnterDesktopMode = true
        mockDesktopRepository.stub { on { getActiveDeskId(DISPLAY_ID) } doReturn null }
        mockShellTaskOrganizer.stub {
            on { getRunningTasks(DISPLAY_ID) } doReturn arrayListOf(taskInfo)
        }

        assertTrue(mShellDesktopState.isEligibleWindowDropTarget(DISPLAY_ID))
    }

    @Test
    @EnableFlags(Flags.FLAG_ALLOW_DRAG_AND_DROP_WHEN_INTERACTIVE_BUGFIX)
    fun testIsEligibleWindowDropTarget_deskAndHomeNotResumed_returnsFalse() {
        val taskInfo = TestRunningTaskInfoBuilder().setTaskId(DESK_ID).build()
        taskInfo.isInteractive = false
        mockDesktopRepository.stub { on { getActiveDeskId(DISPLAY_ID) } doReturn DESK_ID }
        mockShellTaskOrganizer.stub { on { getRunningTasks(DISPLAY_ID) } doReturn arrayListOf() }
        mockShellTaskOrganizer.stub { on { getRunningTaskInfo(DESK_ID) } doReturn taskInfo }

        assertFalse(mShellDesktopState.isEligibleWindowDropTarget(DISPLAY_ID))
    }

    private companion object {
        const val DISPLAY_ID = 1
        const val DESK_ID = 1
    }
}
