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
import android.graphics.PointF
import android.graphics.Rect
import android.os.IBinder
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [FluidTaskResizer].
 *
 * Build/Install/Run: atest WMShellUnitTests:FluidTaskResizerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class FluidTaskResizerTest : ShellTestCase() {
    private val mockDesktopState = FakeDesktopState()
    private val mockDisplayController = mock<DisplayController>()
    private val mockTaskBinder = mock<IBinder>()
    private val mockTaskOrganizer = mock<ShellTaskOrganizer>()
    private val mockTaskToken = mock<WindowContainerToken>()
    private val mockWindowDecoration = mock<WindowDecorationWrapper>()
    private val mockDesktopRepository = mock<DesktopRepository>()
    private val mockDragEventListener = mock<DragPositioningCallbackUtility.DragEventListener>()
    private val mockDisplayLayout = mock<DisplayLayout>()
    private lateinit var taskResizer: FluidTaskResizer
    private lateinit var dragSession: DragSession

    @Before
    fun setUp() {
        whenever(mockTaskToken.asBinder()).thenReturn(mockTaskBinder)
        whenever(mockDisplayController.getDisplayLayout(anyInt())).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.densityDpi()).thenReturn(400)
        whenever(mockWindowDecoration.taskInfo)
            .thenReturn(
                ActivityManager.RunningTaskInfo().apply {
                    taskId = TASK_ID
                    configuration.windowConfiguration.setBounds(STARTING_BOUNDS)
                    token = mockTaskToken
                }
            )

        taskResizer = FluidTaskResizer(mockTaskOrganizer, mockDisplayController, mockDesktopState)
        dragSession =
            DragSession(
                mockWindowDecoration,
                ctrlType =
                    DragPositioningCallback.CTRL_TYPE_LEFT or DragPositioningCallback.CTRL_TYPE_TOP,
                taskBoundsAtDragStart = Rect(STARTING_BOUNDS),
                repositionTaskBounds = Rect(STARTING_BOUNDS),
                repositionStartPoint = PointF(100f, 100f),
                stableBounds = Rect(STABLE_BOUNDS),
                rotation = 0,
                desktopRepository = mockDesktopRepository,
                resizeEventListeners = listOf(mockDragEventListener),
            )
    }

    @Test
    fun testResize_notifiesListeners() {
        taskResizer.onResizeStart(dragSession)

        verify(mockDragEventListener)
            .onDragResizeStarted(
                eq(TASK_ID),
                eq(dragSession.resizeTrigger),
                eq(dragSession.inputMethod),
                eq(STARTING_BOUNDS),
            )

        taskResizer.onResizeUpdate(dragSession, 150f, 110f)

        verify(mockDragEventListener).onDragMove(TASK_ID)

        taskResizer.onResizeEnd(dragSession, 10f, 50f)

        val expectedEndBounds = Rect(10, 50, 200, 200)
        verify(mockDragEventListener)
            .onDragResizeEnded(
                eq(TASK_ID),
                eq(dragSession.resizeTrigger),
                eq(dragSession.inputMethod),
                eq(expectedEndBounds),
            )
    }

    @Test
    fun testResize_haveTransactionWithCorrectDragResizingFlag() {
        taskResizer.onResizeUpdate(dragSession, 150f, 110f)

        val expectedBoundsAfterUpdate = Rect(150, 110, 200, 200)
        verify(mockTaskOrganizer)
            .applyTransaction(
                argThat { wct: WindowContainerTransaction ->
                    return@argThat wct.changes.any { (token, change) ->
                        token == mockTaskBinder &&
                            ((change.changeMask and
                                WindowContainerTransaction.Change.CHANGE_DRAG_RESIZING) != 0) &&
                            change.dragResizing &&
                            change.configuration.windowConfiguration.bounds ==
                                expectedBoundsAfterUpdate
                    }
                }
            )

        val wctEnd =
            checkNotNull(taskResizer.onResizeEnd(dragSession, 10f, 50f)) {
                "Expected non-null return from onResizeEnd"
            }

        val expectedBoundsAfterEnd = Rect(10, 50, 200, 200)
        Assert.assertNotNull(wctEnd)
        Assert.assertTrue(wctEnd.changes.containsKey(mockTaskBinder))
        val endChange =
            checkNotNull(wctEnd.changes[mockTaskBinder]) {
                "Expected non-null changes in WindowContainerTransaction"
            }
        Assert.assertTrue(
            endChange.changeMask and WindowContainerTransaction.Change.CHANGE_DRAG_RESIZING != 0
        )
        Assert.assertFalse(endChange.dragResizing)
        Assert.assertEquals(
            expectedBoundsAfterEnd,
            endChange.configuration.windowConfiguration.bounds,
        )
    }

    @Test
    fun testResize_noBoundsChange_noTransition() {
        taskResizer.onResizeUpdate(dragSession, 100f, 100f)

        verify(mockTaskOrganizer, never()).applyTransaction(any())

        val wct = taskResizer.onResizeEnd(dragSession, 100f, 100f)

        Assert.assertNull(wct)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BOUNDS_RESTORING_ON_DRAG_EXIT)
    fun testResize_removesPreSnapOrPreMaximizeBounds() {
        taskResizer.onResizeUpdate(dragSession, 150f, 110f)

        verify(mockDesktopRepository).removeBoundsBeforeSnapOrMaximize(TASK_ID)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BOUNDS_RESTORING_ON_DRAG_EXIT)
    fun testResize_noBoundsChange_doesNotRemovePreSnapOrPreMaximizeBounds() {
        taskResizer.onResizeUpdate(dragSession, 100f, 100f)

        verify(mockDesktopRepository, never()).removeBoundsBeforeSnapOrMaximize(any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_NO_RESIZE_CURSOR)
    fun testResize_returnsViolatingSizeConstraints() {
        whenever(mockWindowDecoration.taskInfo)
            .thenReturn(
                ActivityManager.RunningTaskInfo().apply {
                    taskId = TASK_ID
                    configuration.windowConfiguration.setBounds(STARTING_BOUNDS)
                    token = mockTaskToken
                    minWidth = 100
                    minHeight = 100
                }
            )

        // Resize below the minimum width.
        // STARTING_BOUNDS width is 100, moving left side coordinate from 100 to 110 makes width to
        // be 90.
        val violatingSizeConstraints = taskResizer.onResizeUpdate(dragSession, 110f, 100f)

        Assert.assertTrue(violatingSizeConstraints)
    }

    companion object {
        private const val TASK_ID = 5
        private val STARTING_BOUNDS = Rect(100, 100, 200, 200)
        private val STABLE_BOUNDS = Rect(0, 0, 2400, 1600)
    }
}
