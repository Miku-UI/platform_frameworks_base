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

package com.android.wm.shell.desktopmode.homescreenpeeking

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityUtils
import com.android.wm.shell.shared.R
import com.android.wm.shell.sysui.ShellController
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [DesktopHomeScreenPeekController].
 *
 * Usage: atest WMShellUnitTests:DesktopHomeScreenPeekControllerTest
 */
@SmallTest
@RunWith(JUnit4::class)
class DesktopHomeScreenPeekControllerTest : ShellTestCase() {
    private val peekTransitionHandler = mock<DesktopHomeScreenPeekTransitionHandler>()
    private val displayController = mock<DisplayController>()
    private val displayLayout = mock<DisplayLayout>()
    private val shellController = mock<ShellController>()
    private val userRepositories = mock<DesktopUserRepositories>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val desktopRepository = mock<DesktopRepository>()
    private val desktopWallpaperActivityUtils = mock<DesktopWallpaperActivityUtils>()
    private val desktopWallpaperActivityTokenProvider =
        mock<DesktopWallpaperActivityTokenProvider>()
    private val wallpaperToken = mock<android.window.WindowContainerToken>()
    private val wallpaperBinder = mock<android.os.IBinder>()

    private lateinit var peekController: DesktopHomeScreenPeekController

    @Before
    fun setUp() {
        whenever(displayController.getDisplayLayout(DEFAULT_DISPLAY)).thenReturn(displayLayout)
        whenever(userRepositories.getProfile(any())).thenReturn(desktopRepository)
        whenever(desktopRepository.getActiveDeskId(any())).thenReturn(0)
        whenever(desktopRepository.getExpandedTasksIdsInDeskOrdered(any()))
            .thenReturn(listOf(TASK_ID))
        whenever(displayLayout.width()).thenReturn(SCREEN_WIDTH)
        val resources = mContext.getOrCreateTestableResources()
        resources.addOverride(R.dimen.desktop_home_screen_peeking_visible_peek_amount, PEEK_AMOUNT)
        whenever(wallpaperToken.asBinder()).thenReturn(wallpaperBinder)
        whenever(desktopWallpaperActivityTokenProvider.getToken(any())).thenReturn(wallpaperToken)
        whenever(desktopWallpaperActivityUtils.hasDesktopWallpaperActivityEnabled(any()))
            .thenReturn(true)
        peekController =
            DesktopHomeScreenPeekController(
                mContext,
                peekTransitionHandler,
                displayController,
                shellController,
                userRepositories,
                shellTaskOrganizer,
                desktopWallpaperActivityUtils,
                desktopWallpaperActivityTokenProvider,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_HOME_SCREEN_PEEKING)
    fun peek_startsTransition() {
        val initialBounds = Rect(100, 100, 600, 600) // Center X = 350
        val desktopTask = createTask(initialBounds)
        whenever(shellTaskOrganizer.getRunningTaskInfo(any())).thenReturn(desktopTask)

        peekController.peek()

        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        val peekAnimationFinishedCallback = argumentCaptor<(() -> Unit)>()
        verify(peekTransitionHandler)
            .startTransition(wctCaptor.capture(), peekAnimationFinishedCallback.capture())
        val wct = wctCaptor.firstValue
        val peekBounds = getWctBoundsForDesktopTask(wct, desktopTask)
        // Screen center is 1000. Task center 350 is left of center.
        // Expected X = -width + peekAmount = -500 + 100 = -400
        val expectedBounds = Rect(-400, 100, 100, 600)
        assertEquals(expectedBounds, peekBounds)
        peekAnimationFinishedCallback.firstValue.invoke()
        assertTrue(peekController.isPeeking)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_HOME_SCREEN_PEEKING)
    fun peek_flagDisabled_doesNothing() {
        peekController.peek()

        verify(peekTransitionHandler, never()).startTransition(any(), anyOrNull())
        assertFalse(peekController.isPeeking)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_HOME_SCREEN_PEEKING)
    fun peek_alreadyPeeking_doesNothing() {
        val originalBounds = Rect(10, 10, 200, 200)
        val desktopTask = createTask(originalBounds)
        whenever(shellTaskOrganizer.getRunningTaskInfo(any())).thenReturn(desktopTask)
        peekAndRunAnimationFinishCallback()

        peekController.peek()

        verify(peekTransitionHandler, never()).startTransition(any(), anyOrNull())
        assertTrue(peekController.isPeeking)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_HOME_SCREEN_PEEKING)
    fun unpeek_startsTransition() {
        val originalBounds = Rect(10, 10, 200, 200)
        val desktopTask = createTask(originalBounds)
        whenever(shellTaskOrganizer.getRunningTaskInfo(any())).thenReturn(desktopTask)
        peekAndRunAnimationFinishCallback()

        peekController.unpeek()

        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        val unpeekAnimationFinishedCallback = argumentCaptor<(() -> Unit)>()
        verify(peekTransitionHandler)
            .startTransition(wctCaptor.capture(), unpeekAnimationFinishedCallback.capture())
        val wct = wctCaptor.firstValue
        val restoredBounds = getWctBoundsForDesktopTask(wct, desktopTask)
        assertEquals(originalBounds, restoredBounds)
        unpeekAnimationFinishedCallback.firstValue.invoke()
        assertFalse(peekController.isPeeking)
    }

    @Test
    fun unpeek_notPeeking_doesNothing() {
        peekController.unpeek()

        verify(peekTransitionHandler, never()).startTransition(any(), anyOrNull())
        assertFalse(peekController.isPeeking)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_HOME_SCREEN_PEEKING)
    fun peek_taskLeftOfCenter_boundsShiftedLeft() {
        val taskBounds = Rect(100, 100, 600, 600) // Center X = 350
        val taskInfo = createTask(taskBounds)
        whenever(shellTaskOrganizer.getRunningTaskInfo(any())).thenReturn(taskInfo)

        peekController.peek()

        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(peekTransitionHandler).startTransition(wctCaptor.capture(), anyOrNull())
        val wct = wctCaptor.firstValue
        val peekBounds = getWctBoundsForDesktopTask(wct, taskInfo)
        // Screen center is 1000. Task center 350 is left of center.
        // Expected X = -width + peekAmount = -500 + 100 = -400
        val expectedLeft = -taskBounds.width() + PEEK_AMOUNT
        assertThat(peekBounds?.left).isEqualTo(expectedLeft)
        assertThat(peekBounds?.top).isEqualTo(taskBounds.top)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_HOME_SCREEN_PEEKING)
    fun peek_taskRightOfCenter_boundsShiftedRight() {
        val taskBounds = Rect(1400, 100, 1900, 600) // Center X = 1650
        val taskInfo = createTask(taskBounds)
        whenever(shellTaskOrganizer.getRunningTaskInfo(any())).thenReturn(taskInfo)

        peekController.peek()

        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(peekTransitionHandler).startTransition(wctCaptor.capture(), anyOrNull())
        val wct = wctCaptor.firstValue
        val peekBounds = getWctBoundsForDesktopTask(wct, taskInfo)
        // Screen center is 1000. Task center 1650 is right of center.
        // Expected X = screenWidth - peekAmount
        val expectedLeft = SCREEN_WIDTH - PEEK_AMOUNT
        assertThat(peekBounds?.left).isEqualTo(expectedLeft)
        assertThat(peekBounds?.top).isEqualTo(taskBounds.top)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_HOME_SCREEN_PEEKING)
    fun peek_noVisibleTasks_doesNothing() {
        whenever(desktopRepository.getExpandedTasksIdsInDeskOrdered(any())).thenReturn(emptyList())

        peekController.peek()

        verify(peekTransitionHandler, never()).startTransition(any(), anyOrNull())
        assertFalse(peekController.isPeeking)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_HOME_SCREEN_PEEKING)
    fun unpeek_originalBoundsNotAvailable_usesLastNonFullscreenBounds() {
        val originalBounds = Rect(10, 10, 200, 200)
        val desktopTask =
            createTask(originalBounds).apply { lastNonFullscreenBounds = originalBounds }
        // Do not add bounds to taskBoundsBeforePeek to force fallback
        whenever(shellTaskOrganizer.getRunningTaskInfo(any())).thenReturn(desktopTask)
        peekAndRunAnimationFinishCallback()

        peekController.unpeek()

        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        val unpeekAnimationFinishedCallback = argumentCaptor<(() -> Unit)>()
        verify(peekTransitionHandler)
            .startTransition(wctCaptor.capture(), unpeekAnimationFinishedCallback.capture())
        val wct = wctCaptor.firstValue
        val restoredBounds = getWctBoundsForDesktopTask(wct, desktopTask)
        assertEquals(originalBounds, restoredBounds)
        // Run the animationFinishedCallback to set peek state.
        unpeekAnimationFinishedCallback.firstValue.invoke()
        assertFalse(peekController.isPeeking)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_PEEKING,
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
    )
    fun peek_wallpaperActivityEnabled_hidesWallpaperActivity() {
        val initialBounds = Rect(100, 100, 600, 600)
        val desktopTask = createTask(initialBounds)
        whenever(shellTaskOrganizer.getRunningTaskInfo(any())).thenReturn(desktopTask)

        peekController.peek()

        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(peekTransitionHandler).startTransition(wctCaptor.capture(), anyOrNull())
        val wct = wctCaptor.firstValue
        val change = wct.changes.entries.find { it.key == wallpaperToken.asBinder() }
        assertTrue(change!!.value.hidden)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_PEEKING,
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
    )
    fun unpeek_wallpaperActivityEnabled_restoresWallpaperActivity() {
        val originalBounds = Rect(10, 10, 200, 200)
        val desktopTask = createTask(originalBounds)
        whenever(shellTaskOrganizer.getRunningTaskInfo(any())).thenReturn(desktopTask)
        peekAndRunAnimationFinishCallback()

        peekController.unpeek()

        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(peekTransitionHandler).startTransition(wctCaptor.capture(), anyOrNull())
        val wct = wctCaptor.firstValue
        val change = wct.changes.entries.find { it.key == wallpaperToken.asBinder() }
        assertFalse(change!!.value.hidden)
        // Check reorder for wallpaper
        val reorderChange =
            wct.hierarchyOps.find {
                it.type == HIERARCHY_OP_TYPE_REORDER && it.container == wallpaperToken.asBinder()
            }
        assertTrue(reorderChange != null && reorderChange.toTop)
    }

    private fun createTask(bounds: Rect): RunningTaskInfo {
        return TestRunningTaskInfoBuilder()
            .setTaskId(TASK_ID)
            .setBounds(bounds)
            .setVisible(true)
            .build()
    }

    private fun getWctBoundsForDesktopTask(
        wct: WindowContainerTransaction,
        desktopTask: RunningTaskInfo,
    ): Rect? =
        wct.changes.entries
            .find { (token, change) ->
                token == desktopTask.token.asBinder() &&
                    (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0
            }
            ?.value
            ?.configuration
            ?.windowConfiguration
            ?.bounds

    // Runs peek(), and then runs the animation finished callback to set the peek state.
    private fun peekAndRunAnimationFinishCallback() {
        peekController.peek()
        val animationFinishedCallback = argumentCaptor<(() -> Unit)>()
        verify(peekTransitionHandler).startTransition(any(), animationFinishedCallback.capture())
        // Invoke the callback so peek state is set.
        animationFinishedCallback.firstValue.invoke()
        // Clear mock invocation history so we can verify peek does not call startTransition again.
        clearInvocations(peekTransitionHandler)
    }

    companion object {
        private const val TASK_ID = 123
        private const val PEEK_AMOUNT = 100
        private const val SCREEN_WIDTH = 2000
    }
}
