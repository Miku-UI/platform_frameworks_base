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
import android.graphics.RectF
import android.os.IBinder
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableResources
import android.view.Display
import android.view.SurfaceControl
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController
import com.android.wm.shell.common.MultiDisplayTestUtil.TestDisplay.DISPLAY_0
import com.android.wm.shell.common.MultiDisplayTestUtil.TestDisplay.DISPLAY_1
import com.android.wm.shell.desktopmode.data.DesktopRepository
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [MultiDisplayTaskMover].
 *
 * Build/Install/Run: atest WMShellUnitTests:MultiDisplayTaskMoverTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class MultiDisplayTaskMoverTest : ShellTestCase() {
    private val mockDesktopRepository = mock<DesktopRepository>()
    private val mockDisplay = mock<Display>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockIndicatorController = mock<MultiDisplayDragMoveIndicatorController>()
    private val mockSurfaceControl = mock<SurfaceControl>()
    private val mockTaskBinder = mock<IBinder>()
    private val mockTaskToken = mock<WindowContainerToken>()
    private val mockTransaction = mock<SurfaceControl.Transaction>()
    private val mockWindowDecoration = mock<WindowDecorationWrapper>()

    private lateinit var dragSession: DragSession
    private lateinit var resources: TestableResources
    private lateinit var spyDisplayLayout0: DisplayLayout
    private lateinit var spyDisplayLayout1: DisplayLayout
    private lateinit var taskInfo: ActivityManager.RunningTaskInfo

    private lateinit var taskMover: MultiDisplayTaskMover

    @Before
    fun setUp() {
        taskInfo =
            ActivityManager.RunningTaskInfo().apply {
                taskId = TASK_ID
                displayId = DISPLAY_0.id
                configuration.windowConfiguration.setBounds(STARTING_BOUNDS)
                token = mockTaskToken
            }
        whenever(mockTaskToken.asBinder()).thenReturn(mockTaskBinder)
        whenever(mockWindowDecoration.taskInfo).thenReturn(taskInfo)
        whenever(mockWindowDecoration.taskSurface).thenReturn(mockSurfaceControl)
        whenever(mockWindowDecoration.display).thenReturn(mockDisplay)
        whenever(mockWindowDecoration.decorWindowContext).thenReturn(mContext)

        resources = mContext.orCreateTestableResources
        spyDisplayLayout0 = DISPLAY_0.getSpyDisplayLayout(resources.resources)
        spyDisplayLayout1 = DISPLAY_1.getSpyDisplayLayout(resources.resources)
        whenever(mockDisplayController.getDisplayLayout(DISPLAY_0.id)).thenReturn(spyDisplayLayout0)
        whenever(mockDisplayController.getDisplayLayout(DISPLAY_1.id)).thenReturn(spyDisplayLayout1)

        // Window starting at Rect(100, 100, 200, 200), cursor starting at (100, 100) on DISPLAY_0.
        dragSession =
            DragSession(
                mockWindowDecoration,
                ctrlType = DragPositioningCallback.CTRL_TYPE_UNDEFINED,
                taskBoundsAtDragStart = Rect(STARTING_BOUNDS),
                repositionTaskBounds = Rect(STARTING_BOUNDS),
                repositionStartPoint =
                    PointF(STARTING_BOUNDS.left.toFloat(), STARTING_BOUNDS.top.toFloat()),
                rotation = 0,
                desktopRepository = mockDesktopRepository,
            )

        taskMover =
            MultiDisplayTaskMover(
                mockDisplayController,
                { mockTransaction },
                mockIndicatorController,
            )
    }

    @Test
    fun testMoveOnSameDisplay_updatesIndicatorAndSurface() = runOnUiThread {
        // Cursor move to (160f, 110f) on DISPLAY_0
        taskMover.onMoveUpdate(dragSession, DISPLAY_0.id, 160f, 110f)

        // Verify returning correct bounds and triggering indicator controller.
        val expectedBoundsUpdate = Rect(160, 110, 260, 210)
        Assert.assertEquals(expectedBoundsUpdate, dragSession.repositionTaskBounds)
        verify(mockIndicatorController)
            .onDragMove(
                any(),
                eq(DISPLAY_0.id),
                eq(DISPLAY_0.id),
                eq(mockSurfaceControl),
                eq(taskInfo),
                any(),
                eq(mockTransaction),
            )

        // Verify original task surface is offscreen.
        val leftAfterMoveCaptor = argumentCaptor<Float>()
        val topAfterMoveCaptor = argumentCaptor<Float>()
        verify(mockTransaction)
            .setPosition(
                eq(mockSurfaceControl),
                leftAfterMoveCaptor.capture(),
                topAfterMoveCaptor.capture(),
            )
        val rectAfterMove = RectF(STARTING_BOUNDS)
        rectAfterMove.offsetTo(leftAfterMoveCaptor.firstValue, topAfterMoveCaptor.firstValue)
        Assert.assertFalse(DISPLAY_0.bounds.intersect(rectAfterMove))

        clearInvocations(mockTransaction)

        // Cursor move to (170f, 110f) on DISPLAY_0.
        // As the task surface is already offscreen, setPosition shouldn't be called again.
        taskMover.onMoveUpdate(dragSession, DISPLAY_0.id, 170f, 110f)
        verify(mockTransaction, never()).setPosition(any(), any<Float>(), any<Float>())
    }

    @Test
    fun testMoveOnNewDisplay_updatesIndicatorAndSurface() = runOnUiThread {
        // Cursor move to (200, 100) on DISPLAY_1.
        taskMover.onMoveUpdate(dragSession, DISPLAY_1.id, 200f, 100f)

        // Verify returning correct bounds and triggering indicator controller.
        val expectedBoundsUpdate = Rect(200, -950, 300, -850)
        Assert.assertEquals(expectedBoundsUpdate, dragSession.repositionTaskBounds)
        verify(mockIndicatorController)
            .onDragMove(
                any(),
                eq(DISPLAY_1.id),
                eq(DISPLAY_0.id),
                eq(mockSurfaceControl),
                eq(taskInfo),
                any(),
                eq(mockTransaction),
            )

        // Verify original task surface is offscreen.
        val leftAfterMoveCaptor = argumentCaptor<Float>()
        val topAfterMoveCaptor = argumentCaptor<Float>()
        verify(mockTransaction)
            .setPosition(
                eq(mockSurfaceControl),
                leftAfterMoveCaptor.capture(),
                topAfterMoveCaptor.capture(),
            )
        val rectAfterMove = RectF(STARTING_BOUNDS)
        rectAfterMove.offsetTo(leftAfterMoveCaptor.firstValue, topAfterMoveCaptor.firstValue)
        Assert.assertFalse(DISPLAY_0.bounds.intersect(rectAfterMove))
    }

    @Test
    fun testMoveEndOnSameDisplay_returnsCorrectBoundsAndNoPxDpConversion() {
        // Cursor move to (160f, 110f) on DISPLAY_0
        taskMover.onMoveEnd(dragSession, DISPLAY_0.id, 160f, 110f)

        // Verify returning correct bounds and no dp px conversion.
        val expectedBoundsUpdate = Rect(160, 110, 260, 210)
        Assert.assertEquals(expectedBoundsUpdate, dragSession.repositionTaskBounds)
        verify(spyDisplayLayout0, never()).localPxToGlobalDp(any(), any())
        verify(spyDisplayLayout0, never()).globalDpToLocalPx(any(), any())
    }

    @Test
    fun testMoveEndOnNewDisplay_returnsCorrectBounds() = runOnUiThread {
        // Cursor move to (200, 100) on DISPLAY_1.
        taskMover.onMoveEnd(dragSession, DISPLAY_1.id, 200f, 100f)

        // Verify returning correct bounds and triggering indicator controller.
        val expectedBoundsUpdate = Rect(200, 100, 400, 300)
        Assert.assertEquals(expectedBoundsUpdate, dragSession.repositionTaskBounds)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BOUNDS_RESTORING_ON_DRAG_EXIT)
    fun testOnMoveUpdate_firstMoveWithBoundsRestoration_returnsWctAndRestoresBounds() =
        runOnUiThread {
            val prevBounds = Rect(100, 250, 200, 500)
            val expectedRestoredBounds = Rect(100, 100, 200, 350)
            dragSession.shouldRestoreBoundsOnMove = true
            whenever(mockDesktopRepository.getBoundsBeforeSnapOrMaximize(TASK_ID))
                .thenReturn(prevBounds)

            // First Move: Trigger the restoration of previous bounds and return a WCT.
            val wct =
                checkNotNull(taskMover.onMoveUpdate(dragSession, DISPLAY_0.id, 500f, 100f)) {
                    "Expected non-null return from onResizeUpdate with bounds restore"
                }
            val change =
                checkNotNull(wct.changes[mockTaskBinder]) {
                    "Expected non-null changes in WindowContainerTransaction"
                }
            Assert.assertEquals(
                expectedRestoredBounds,
                change.configuration.windowConfiguration.bounds,
            )
            Assert.assertEquals(expectedRestoredBounds, dragSession.repositionTaskBounds)
            Assert.assertFalse(dragSession.shouldRestoreBoundsOnMove)

            dragSession.pendingBoundsRestoreTransition = mock<IBinder>()

            // Second Move: If a restore transition is active, it should return null and not update
            // bounds.
            Assert.assertNull(taskMover.onMoveUpdate(dragSession, DISPLAY_0.id, 300f, 300f))
            Assert.assertEquals(expectedRestoredBounds, dragSession.repositionTaskBounds)

            dragSession.pendingBoundsRestoreTransition = null

            // Third Move: If the restore transition has finished, it should continue to move the
            // window.
            val expectedBounds = Rect(300, 300, 400, 550)
            Assert.assertNull(taskMover.onMoveUpdate(dragSession, DISPLAY_0.id, 300f, 300f))
            Assert.assertEquals(expectedBounds, dragSession.repositionTaskBounds)
        }

    companion object {
        private const val TASK_ID = 5
        private val STARTING_BOUNDS = Rect(100, 100, 200, 200)
    }
}
