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

import android.animation.AnimatorTestRule
import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.ComponentName
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.ChangeBuilder
import com.android.testing.wm.util.TransitionInfoBuilder
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.transition.Transitions
import java.util.function.Supplier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [DesktopHomeScreenPeekTransitionHandler].
 *
 * Usage: atest WMShellUnitTests:DesktopHomeScreenPeekTransitionHandlerTest
 */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DesktopHomeScreenPeekTransitionHandlerTest : ShellTestCase() {
    @JvmField @Rule val animatorTestRule = AnimatorTestRule(this)
    private val mainExecutor = TestShellExecutor()
    private val transitions = mock<Transitions>()
    private val transactionSupplier = mock<Supplier<SurfaceControl.Transaction>>()
    private val transaction = mock<SurfaceControl.Transaction>()
    private val shellController = mock<ShellController>()
    private val userRepositories = mock<DesktopUserRepositories>()
    private val desktopRepository = mock<DesktopRepository>()
    private val desktopWallpaperActivityTokenProvider =
        mock<DesktopWallpaperActivityTokenProvider>()

    private lateinit var peekTransitionHandler: DesktopHomeScreenPeekTransitionHandler

    @Before
    fun setUp() {
        whenever(transactionSupplier.get()).thenReturn(transaction)
        whenever(userRepositories.getProfile(any())).thenReturn(desktopRepository)
        whenever(desktopRepository.isActiveTask(any())).thenReturn(true)
        peekTransitionHandler =
            DesktopHomeScreenPeekTransitionHandler(
                mainExecutor,
                transitions,
                transactionSupplier,
                shellController,
                userRepositories,
                desktopWallpaperActivityTokenProvider,
            )
    }

    @Test
    fun startTransition_startsWct() {
        val wct = mock<WindowContainerTransaction>()

        peekTransitionHandler.startTransition(wct)

        verify(transitions).startTransition(eq(TRANSIT_CHANGE), eq(wct), eq(peekTransitionHandler))
    }

    @Test
    fun startTransition_withCallback_startsWct() {
        val wct = mock<WindowContainerTransaction>()
        val callback = mock<() -> Unit>()

        peekTransitionHandler.startTransition(wct, callback)

        verify(transitions).startTransition(eq(TRANSIT_CHANGE), eq(wct), eq(peekTransitionHandler))
    }

    @Test
    fun request_returnsNull() {
        val request = TransitionRequestInfo(TRANSIT_TO_FRONT, null, null)

        val wct = peekTransitionHandler.handleRequest(mock(), request)

        assertNull(wct)
    }

    @Test
    fun startAnimation_peeking_animates() {
        val taskInfo = createDesktopTask(1)
        val change = createChange(taskInfo, Rect(0, 0, 100, 100), Rect(50, 0, 150, 100))
        val info = TransitionInfoBuilder(TRANSIT_CHANGE).addChange(change).build()
        val finishCallback = mock<Transitions.TransitionFinishCallback>()

        val result =
            peekTransitionHandler.startAnimation(mock(), info, mock(), mock(), finishCallback)
        mainExecutor.flushAll()
        animatorTestRule.advanceTimeBy(TIME_MS)

        assertTrue(result)
        verify(finishCallback).onTransitionFinished(null)
    }

    @Test
    fun startAnimation_peeking_onAnimationFinishedRuns() {
        val taskInfo = createDesktopTask(1)
        val change = createChange(taskInfo, Rect(0, 0, 100, 100), Rect(50, 0, 150, 100))
        val info = TransitionInfoBuilder(TRANSIT_CHANGE).addChange(change).build()
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        val onAnimationFinished = mock<() -> Unit>()
        // Use startTransition to set the callback
        peekTransitionHandler.startTransition(mock(), onAnimationFinished)

        peekTransitionHandler.startAnimation(mock(), info, mock(), mock(), finishCallback)
        mainExecutor.flushAll()
        animatorTestRule.advanceTimeBy(TIME_MS)

        verify(onAnimationFinished).invoke()
    }

    @Test
    fun startAnimation_noDesktopChanges_returnsFalse() {
        val info = TransitionInfoBuilder(TRANSIT_CHANGE).build()

        val result = peekTransitionHandler.startAnimation(mock(), info, mock(), mock(), mock())

        assertFalse(result)
    }

    @Test
    fun startAnimation_withNonDesktopTasks_filtersOutNonDesktop() {
        // Active desktop task
        val desktopTaskInfo = createDesktopTask(1)
        val desktopChange =
            createChange(desktopTaskInfo, Rect(0, 0, 100, 100), Rect(50, 0, 150, 100))
        val desktopLeash = desktopChange.leash
        // Non-desktop task
        val nonDesktopTaskInfo = createDesktopTask(2)
        whenever(desktopRepository.isActiveTask(2)).thenReturn(false)
        val nonDesktopChange =
            createChange(nonDesktopTaskInfo, Rect(0, 0, 100, 100), Rect(50, 0, 150, 100))
        val nonDesktopLeash = nonDesktopChange.leash
        // Transition Info
        val info =
            TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(desktopChange)
                .addChange(nonDesktopChange)
                .build()
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        val startTransaction = mock<SurfaceControl.Transaction>()
        val finishTransaction = mock<SurfaceControl.Transaction>()

        val result =
            peekTransitionHandler.startAnimation(
                mock(),
                info,
                startTransaction,
                finishTransaction,
                finishCallback,
            )
        mainExecutor.flushAll()
        animatorTestRule.advanceTimeBy(TIME_MS)

        assertTrue(result)
        verify(finishCallback).onTransitionFinished(null)
        // desktop task was animated
        verify(startTransaction).setPosition(eq(desktopLeash), any(), any())
        verify(finishTransaction).setPosition(eq(desktopLeash), any(), any())
        // non-desktop task was ignored
        verify(startTransaction, never()).setPosition(eq(nonDesktopLeash), any(), any())
        verify(finishTransaction, never()).setPosition(eq(nonDesktopLeash), any(), any())
    }

    @Test
    fun startAnimation_peeking_animatesHomeAndWallpaper() {
        val desktopTaskInfo = createDesktopTask(1)
        val desktopChange =
            createChange(desktopTaskInfo, Rect(0, 0, 100, 100), Rect(50, 0, 150, 100))
        val homeTaskInfo =
            TestRunningTaskInfoBuilder().setTaskId(2).setActivityType(ACTIVITY_TYPE_HOME).build()
        val homeChange =
            createChange(homeTaskInfo, Rect(0, 0, 1000, 1000), Rect(0, 0, 1000, 1000)).apply {
                mode = TRANSIT_TO_FRONT
            }
        val wallpaperToken = mock<android.window.WindowContainerToken>()
        whenever(desktopWallpaperActivityTokenProvider.getToken(any())).thenReturn(wallpaperToken)
        val wallpaperTaskInfo =
            TestRunningTaskInfoBuilder().setTaskId(3).setToken(wallpaperToken).build()
        val wallpaperChange = createChange(wallpaperTaskInfo, Rect(), Rect())
        val info =
            TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(desktopChange)
                .addChange(homeChange)
                .addChange(wallpaperChange)
                .build()
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        val startTransaction = mock<SurfaceControl.Transaction>()
        val finishTransaction = mock<SurfaceControl.Transaction>()

        val result =
            peekTransitionHandler.startAnimation(
                mock(),
                info,
                startTransaction,
                finishTransaction,
                finishCallback,
            )

        assertTrue(result)
        // Assert start states
        verify(startTransaction).setAlpha(eq(homeChange.leash), eq(0f))
        verify(startTransaction).setAlpha(eq(wallpaperChange.leash), eq(1f))
        mainExecutor.flushAll()
        animatorTestRule.advanceTimeBy(TIME_MS)
        // Assert finish states
        verify(finishTransaction).setAlpha(eq(homeChange.leash), eq(1f))
        verify(finishTransaction).setAlpha(eq(wallpaperChange.leash), eq(0f))
        verify(finishCallback).onTransitionFinished(null)
    }

    @Test
    fun startAnimation_unpeeking_animatesHomeAndWallpaper() {
        val desktopTaskInfo = createDesktopTask(1)
        val desktopChange =
            createChange(desktopTaskInfo, Rect(50, 0, 150, 100), Rect(0, 0, 100, 100))
        val homeTaskInfo =
            TestRunningTaskInfoBuilder().setTaskId(2).setActivityType(ACTIVITY_TYPE_HOME).build()
        val homeChange =
            createChange(homeTaskInfo, Rect(0, 0, 1000, 1000), Rect(0, 0, 1000, 1000)).apply {
                mode = android.view.WindowManager.TRANSIT_TO_BACK
            }
        val wallpaperToken = mock<android.window.WindowContainerToken>()
        whenever(desktopWallpaperActivityTokenProvider.getToken(any())).thenReturn(wallpaperToken)
        val wallpaperTaskInfo =
            TestRunningTaskInfoBuilder().setTaskId(3).setToken(wallpaperToken).build()
        val wallpaperChange = createChange(wallpaperTaskInfo, Rect(), Rect())
        val info =
            TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(desktopChange)
                .addChange(homeChange)
                .addChange(wallpaperChange)
                .build()
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        val startTransaction = mock<SurfaceControl.Transaction>()
        val finishTransaction = mock<SurfaceControl.Transaction>()

        val result =
            peekTransitionHandler.startAnimation(
                mock(),
                info,
                startTransaction,
                finishTransaction,
                finishCallback,
            )

        assertTrue(result)
        // Unpeeking: start states
        verify(startTransaction).setAlpha(eq(wallpaperChange.leash), eq(0f))
        mainExecutor.flushAll()
        animatorTestRule.advanceTimeBy(TIME_MS)
        // Assert finish states
        verify(finishTransaction).setAlpha(eq(wallpaperChange.leash), eq(1f))
        verify(finishCallback).onTransitionFinished(null)
    }

    private fun createDesktopTask(id: Int): RunningTaskInfo {
        return TestRunningTaskInfoBuilder()
            .setTaskId(id)
            .setWindowingMode(WINDOWING_MODE_FREEFORM)
            .setVisible(true)
            .setBaseActivity(ComponentName("com.example", "DesktopActivity"))
            .build()
    }

    private fun createChange(
        taskInfo: RunningTaskInfo,
        startBounds: Rect,
        endBounds: Rect,
    ): TransitionInfo.Change {
        val change = ChangeBuilder(taskInfo, TRANSIT_CHANGE).build()
        change.startAbsBounds.set(startBounds)
        change.endAbsBounds.set(endBounds)
        return change
    }

    private companion object {
        private const val TIME_MS = 500L
    }
}
