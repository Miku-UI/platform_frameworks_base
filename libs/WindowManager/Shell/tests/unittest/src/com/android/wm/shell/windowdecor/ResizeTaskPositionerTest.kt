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

import android.app.ActivityManager
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.android.internal.jank.Cuj
import com.android.internal.jank.InteractionJankMonitor
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.desktopmode.DesktopScrimController
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.transition.Transitions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Tests for [ResizeTaskPositioner].
 *
 * Build/Install/Run: atest WMShellUnitTests:ResizeTaskPositionerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class ResizeTaskPositionerTest : ShellTestCase() {
    private val mockDisplay = mock<Display>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockTaskMover = mock<TaskMover>()
    private val mockTaskResizer = mock<TaskResizer>()
    private val mockTransitions = mock<Transitions>()
    private val mockTransitionBinder = mock<IBinder>()
    private val mockWindowContainerTransaction = mock<WindowContainerTransaction>()
    private val mockWindowDecoration = mock<WindowDecorationWrapper>()
    private val mockDesktopTasksController = mock<DesktopTasksController>()
    private val mockDesktopUserRepositories = mock<DesktopUserRepositories>()
    private val mockDesktopRepository = mock<DesktopRepository>()
    private val mockDragEventListener = mock<DragPositioningCallbackUtility.DragEventListener>()
    private val mockInteractionJankMonitor = mock<InteractionJankMonitor>()
    private val mockDesktopScrimController = mock<DesktopScrimController>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var taskPositioner: ResizeTaskPositioner

    @Before
    fun setUp() {
        val displayLayout = DisplayLayout(mContext, mockDisplay)
        whenever(mockDisplayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(mockWindowDecoration.taskInfo)
            .thenReturn(
                ActivityManager.RunningTaskInfo().apply {
                    taskId = TASK_ID
                    configuration.windowConfiguration.setBounds(STARTING_BOUNDS)
                }
            )

        whenever(mockTaskResizer.onResizeEnd(any(), any(), any()))
            .thenReturn(mockWindowContainerTransaction)
        whenever(mockTransitions.startTransition(any(), any(), any()))
            .thenReturn(mockTransitionBinder)

        whenever(mockDesktopUserRepositories.getProfile(any())).thenReturn(mockDesktopRepository)
        whenever(mockDesktopRepository.hasBoundsBeforeSnapOrMaximize(any())).thenReturn(true)
        whenever(mockDesktopRepository.getBoundsBeforeSnapOrMaximize(TASK_ID))
            .thenReturn(mock<Rect>())
        whenever(mockDesktopTasksController.getDesktopScrimController())
            .thenReturn(mockDesktopScrimController)

        taskPositioner =
            ResizeTaskPositioner(
                mockWindowDecoration,
                mockDisplayController,
                mockTaskMover,
                mockTaskResizer,
                mockTransitions,
                mainHandler,
                mockDesktopTasksController,
                mockDesktopUserRepositories,
                mockInteractionJankMonitor,
            )
        taskPositioner.addDragEventListener(mockDragEventListener)
    }

    @Test
    fun testResize() = runOnUiThread {
        // Start
        taskPositioner.onDragPositioningStart(
            DragPositioningCallback.CTRL_TYPE_RIGHT,
            0,
            100f,
            100f,
            DragPositioningCallback.INPUT_METHOD_TYPE_UNKNOWN,
        )
        verify(mockTaskResizer).onResizeStart(any())

        // First Move, simulate a bounds change in the mock.
        doAnswer { invocation ->
                val session = invocation.getArgument<DragSession>(0)
                session.repositionTaskBounds.set(mock<Rect>()); false
            }
            .whenever(mockTaskResizer)
            .onResizeUpdate(any(), any(), any())

        taskPositioner.onDragPositioningMove(0, 200f, 200f)
        verify(mockTaskResizer).onResizeUpdate(any(), eq(200f), eq(200f))
        verify(mockDesktopScrimController).updateDesktopScrimOnResize(any(), any(), any())

        // Second move, expecting no taskbar rounding update.
        clearInvocations(mockDesktopScrimController)
        taskPositioner.onDragPositioningMove(0, 210f, 210f)
        verify(mockDesktopScrimController, never()).updateDesktopScrimOnResize(any(), any(), any())

        // End
        taskPositioner.onDragPositioningEnd(0, 50f, 50f)
        verify(mockTaskResizer).onResizeEnd(any(), eq(50f), eq(50f))

        // Start animation
        taskPositioner.startAnimation(
            mockTransitionBinder,
            mock<TransitionInfo>(),
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>(),
            mock<Transitions.TransitionFinishCallback>(),
        )
        verify(mockTaskResizer).cleanup(any())

        verifyNoInteractions(mockTaskMover)
    }

    @Test
    fun testResizeCuj_beginsAndEnds() = runOnUiThread {
        // Start
        taskPositioner.onDragPositioningStart(
            DragPositioningCallback.CTRL_TYPE_RIGHT,
            0,
            100f,
            100f,
            DragPositioningCallback.INPUT_METHOD_TYPE_UNKNOWN,
        )
        verify(mockInteractionJankMonitor)
            .begin(any<InteractionJankMonitor.Configuration.Builder>())

        // First Move, simulate a bounds change in the mock.
        doAnswer { invocation ->
                val session = invocation.getArgument<DragSession>(0)
                session.repositionTaskBounds.set(mock<Rect>()); false
            }
            .whenever(mockTaskResizer)
            .onResizeUpdate(any(), any(), any())
        taskPositioner.onDragPositioningMove(0, 200f, 200f)

        // End
        taskPositioner.onDragPositioningEnd(0, 50f, 50f)

        // Start animation
        taskPositioner.startAnimation(
            mockTransitionBinder,
            mock<TransitionInfo>(),
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>(),
            mock<Transitions.TransitionFinishCallback>(),
        )
        verify(mockInteractionJankMonitor).end(Cuj.CUJ_DESKTOP_MODE_RESIZE_WINDOW)

        verify(mockInteractionJankMonitor, never()).end(Cuj.CUJ_DESKTOP_MODE_DRAG_WINDOW)
    }

    @Test
    fun testMove() = runOnUiThread {
        // Start
        taskPositioner.onDragPositioningStart(
            DragPositioningCallback.CTRL_TYPE_UNDEFINED,
            0,
            100f,
            100f,
            DragPositioningCallback.INPUT_METHOD_TYPE_UNKNOWN,
        )

        // First Move
        taskPositioner.onDragPositioningMove(1, 200f, 200f)
        verify(mockTaskMover).onMoveUpdate(any(), eq(1), eq(200f), eq(200f))
        verify(mockDesktopScrimController).updateDesktopScrimOnResize(any(), any(), any())

        // Second move, expecting no taskbar rounding update.
        clearInvocations(mockDesktopScrimController)
        taskPositioner.onDragPositioningMove(1, 210f, 210f)
        verify(mockDesktopScrimController, never()).updateDesktopScrimOnResize(any(), any(), any())

        // End
        taskPositioner.onDragPositioningEnd(2, 50f, 50f)
        verify(mockTaskMover).onMoveEnd(any(), eq(2), eq(50f), eq(50f))

        // Start animation
        taskPositioner.startAnimation(
            mockTransitionBinder,
            mock<TransitionInfo>(),
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>(),
            mock<Transitions.TransitionFinishCallback>(),
        )

        verifyNoInteractions(mockTaskResizer)
    }

    @Test
    fun testMoveCuj_beginsAndEnds() = runOnUiThread {
        // Start
        taskPositioner.onDragPositioningStart(
            DragPositioningCallback.CTRL_TYPE_UNDEFINED,
            0,
            100f,
            100f,
            DragPositioningCallback.INPUT_METHOD_TYPE_UNKNOWN,
        )

        // First Move
        taskPositioner.onDragPositioningMove(1, 200f, 200f)
        verify(mockInteractionJankMonitor)
            .begin(any<InteractionJankMonitor.Configuration.Builder>())

        // End
        taskPositioner.onDragPositioningEnd(2, 50f, 50f)
        verify(mockInteractionJankMonitor, never()).end(Cuj.CUJ_DESKTOP_MODE_DRAG_WINDOW)

        verify(mockInteractionJankMonitor, never()).end(Cuj.CUJ_DESKTOP_MODE_RESIZE_WINDOW)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BOUNDS_RESTORING_ON_DRAG_EXIT)
    fun testMove_withBoundsRestoration() = runOnUiThread {
        whenever(mockTaskMover.onMoveUpdate(any(), any(), any(), any()))
            .thenReturn(mockWindowContainerTransaction)

        taskPositioner.onDragPositioningStart(
            DragPositioningCallback.CTRL_TYPE_UNDEFINED,
            0,
            150f,
            150f,
            DragPositioningCallback.INPUT_METHOD_TYPE_UNKNOWN,
        )
        taskPositioner.onDragPositioningMove(1, 250f, 250f)

        verify(mockTransitions)
            .startTransition(eq(android.view.WindowManager.TRANSIT_CHANGE), any(), any())
    }

    companion object {
        private const val TASK_ID = 5
        private val STARTING_BOUNDS = Rect(100, 100, 200, 200)
    }
}
