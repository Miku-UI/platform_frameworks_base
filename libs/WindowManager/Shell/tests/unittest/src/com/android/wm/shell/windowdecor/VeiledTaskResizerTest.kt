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
import androidx.test.filters.SmallTest
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [VeiledTaskResizer].
 *
 * Build/Install/Run: atest WMShellUnitTests:VeiledTaskResizerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class VeiledTaskResizerTest : ShellTestCase() {
    private val mockWindowDecoration = mock<WindowDecorationWrapper>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockDesktopRepository = mock<DesktopRepository>()
    private val mockDragEventListener = mock<DragPositioningCallbackUtility.DragEventListener>()
    private val mockDisplayLayout = mock<DisplayLayout>()

    private val desktopState = FakeDesktopState()
    private lateinit var taskResizer: VeiledTaskResizer
    private lateinit var dragSession: DragSession

    @Before
    fun setUp() {
        val taskToken = mock<WindowContainerToken>()
        val taskBinder = mock<IBinder>()
        whenever(taskToken.asBinder()).thenReturn(taskBinder)
        whenever(mockDisplayController.getDisplayLayout(any())).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.densityDpi()).thenReturn(400)
        whenever(mockWindowDecoration.taskInfo)
            .thenReturn(
                ActivityManager.RunningTaskInfo().apply {
                    taskId = TASK_ID
                    configuration.windowConfiguration.setBounds(STARTING_BOUNDS)
                    token = taskToken
                }
            )

        taskResizer = VeiledTaskResizer(mockDisplayController, desktopState)
        dragSession =
            DragSession(
                mockWindowDecoration,
                ctrlType =
                    DragPositioningCallback.CTRL_TYPE_LEFT or DragPositioningCallback.CTRL_TYPE_TOP,
                taskBoundsAtDragStart = Rect(STARTING_BOUNDS),
                repositionTaskBounds = Rect(STARTING_BOUNDS),
                repositionStartPoint =
                    PointF(STARTING_BOUNDS.left.toFloat(), STARTING_BOUNDS.top.toFloat()),
                stableBounds = Rect(STABLE_BOUNDS),
                rotation = 0,
                desktopRepository = mockDesktopRepository,
                resizeEventListeners = listOf(mockDragEventListener),
            )
    }

    @Test
    fun testResize_notifiesListeners() = runOnUiThread {
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
    fun testResize_showsAndUpdatesVeilAndStartsTransition() = runOnUiThread {
        taskResizer.onResizeUpdate(dragSession, 150f, 110f)

        val expectedBoundsAfterFirstUpdate = Rect(150, 110, 200, 200)
        verify(mockWindowDecoration).showResizeVeil(expectedBoundsAfterFirstUpdate)

        taskResizer.onResizeUpdate(dragSession, 50f, 50f)

        val expectedBoundsAfterSecondUpdate = Rect(50, 50, 200, 200)
        verify(mockWindowDecoration).updateResizeVeil(expectedBoundsAfterSecondUpdate)

        val wct = taskResizer.onResizeEnd(dragSession, 10f, 50f)

        val expectedBounds = Rect(10, 50, 200, 200)
        Assert.assertNotNull(wct)
        val changes = wct!!.changes
        val taskChange = changes.values.first()
        val bounds = taskChange.configuration.windowConfiguration.bounds
        Assert.assertEquals(expectedBounds, bounds)
    }

    @Test
    fun testResize_endWithNoEffectiveMove_skipsTransactionOnEndAndClearVeil() = runOnUiThread {
        taskResizer.onResizeUpdate(
            dragSession,
            STARTING_BOUNDS.left.toFloat() + 10,
            STARTING_BOUNDS.top.toFloat() + 10,
        )
        val wct =
            taskResizer.onResizeEnd(
                dragSession,
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat(),
            )

        verify(mockWindowDecoration).hideResizeVeil()
        Assert.assertFalse(dragSession.isResizingOrAnimatingResize)
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
        taskResizer.onResizeUpdate(
            dragSession,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        verify(mockDesktopRepository, never()).removeBoundsBeforeSnapOrMaximize(any())
    }

    companion object {
        private const val TASK_ID = 5
        private val STARTING_BOUNDS = Rect(100, 100, 200, 200)
        private val STABLE_BOUNDS = Rect(0, 0, 2400, 1600)
    }
}
