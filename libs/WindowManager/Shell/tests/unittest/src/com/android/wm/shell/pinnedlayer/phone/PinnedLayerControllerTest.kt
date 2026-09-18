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

package com.android.wm.shell.pinnedlayer.phone

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.graphics.Rect
import android.os.IBinder
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.util.DisplayMetrics
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.DisplayAreaInfo
import android.window.DisplayAreaOrganizer
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_IS_WALLPAPER
import android.window.TransitionInfo.FLAG_NONE
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController
import com.android.wm.shell.desktopmode.DesktopTestHelpers
import com.android.wm.shell.desktopmode.FakeShellDesktopState
import com.android.wm.shell.desktopmode.WindowDragTransitionHandler
import com.android.wm.shell.shared.TransactionPool
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Unit tests against [PinnedLayerController]
 *
 * Build/Install/Run: atest WMShellUnitTests:PinnedLayerControllerTest
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
@RunWith(AndroidJUnit4::class)
class PinnedLayerControllerTest : ShellTestCase() {
    @Mock private lateinit var shellInit: ShellInit
    @Mock private lateinit var transitions: Transitions
    @Mock private lateinit var windowDragTransitionHandler: WindowDragTransitionHandler
    @Mock private lateinit var transactionPool: TransactionPool
    @Mock private lateinit var poolTransaction: SurfaceControl.Transaction
    @Mock private lateinit var startTransaction: SurfaceControl.Transaction
    @Mock private lateinit var finishTransaction: SurfaceControl.Transaction
    @Mock private lateinit var repositionTransaction: SurfaceControl.Transaction
    @Mock private lateinit var leash: SurfaceControl
    @Mock private lateinit var displayController: DisplayController
    @Mock private lateinit var rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock private lateinit var shellTaskOrganizer: ShellTaskOrganizer
    @Mock
    private lateinit var multiDisplayDragMoveIndicatorController:
        MultiDisplayDragMoveIndicatorController
    private val windowRepositionAnimator = mock<PinnedWindowRepositionAnimator>()

    private lateinit var defaultDisplayToken: WindowContainerToken
    private lateinit var desktopState: FakeDesktopState
    private lateinit var shellDesktopState: FakeShellDesktopState
    private lateinit var presentationController: PinnedLayerPresentationController
    private lateinit var pinnedWindowRepositionAnimationHandler:
        PinnedWindowRepositionAnimationHandler
    private lateinit var pinnedLayerController: PinnedLayerController

    @Before
    fun setup() {
        defaultDisplayToken = MockToken.token()
        desktopState = FakeDesktopState()
        shellDesktopState = FakeShellDesktopState(desktopState)

        whenever(transactionPool.acquire()).thenReturn(poolTransaction)

        setupDisplay(DEFAULT_DISPLAY, defaultDisplayToken, isDesktopModeSupported = true)

        presentationController =
            PinnedLayerPresentationController(context, displayController, desktopState)
        pinnedWindowRepositionAnimationHandler =
            PinnedWindowRepositionAnimationHandler(transitions, windowRepositionAnimator)
        pinnedLayerController =
            PinnedLayerController(
                shellInit,
                transitions,
                shellDesktopState,
                rootTaskDisplayAreaOrganizer,
                shellTaskOrganizer,
                presentationController,
                windowDragTransitionHandler,
                pinnedWindowRepositionAnimationHandler,
                windowRepositionAnimator,
                transactionPool,
                multiDisplayDragMoveIndicatorController,
            )
    }

    @Test
    fun dragEnded_notPinnedTask_doNothing() {
        val task = setupTask()
        val dragStartBounds = Rect(DEFAULT_TASK_BOUNDS)
        val dragEndBounds = Rect(DEFAULT_TASK_BOUNDS)
        dragEndBounds.offset(100, 100)

        val result = pinnedLayerController.onDragEnded(leash, task, dragStartBounds, dragEndBounds)

        assertFalse(result)
        verifyNoInteractions(transitions, transactionPool)
    }

    @Test
    fun dragEnded_snapsBackToStartBounds_animatesWithoutTransition() {
        val task = setupTask()
        pinTask(task)

        val dragStartBounds = Rect(DEFAULT_TASK_BOUNDS)
        val dragEndBounds = Rect(dragStartBounds)
        // Move the window a bit.
        dragEndBounds.offset(-10, -10)

        val result = pinnedLayerController.onDragEnded(leash, task, dragStartBounds, dragEndBounds)

        // Snapshotting surfaces should be cleared by the controller itself.
        assertTrue(result)
        // No Transition is started.
        verify(transitions, never()).startTransition(any(), any(), anyOrNull())
        // Manually animate the leash snap to start.
        verify(windowRepositionAnimator)
            .start(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun dragEnded_noChangesInBounds_applyTransactionImmediately() {
        val task = setupTask()
        val transition = mock<IBinder>()
        val transitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        pinnedLayerController.pinTask(transition, task, null)
        pinnedLayerController.onTransitionReady(
            transition,
            transitionInfo,
            startTransaction,
            finishTransaction,
        )

        val dragStartBounds = Rect(DEFAULT_TASK_BOUNDS)
        val dragEndBounds = Rect(dragStartBounds)
        val result = pinnedLayerController.onDragEnded(leash, task, dragStartBounds, dragEndBounds)

        assertTrue(result)
        verify(poolTransaction)
            .setPosition(leash, dragStartBounds.left.toFloat(), dragStartBounds.top.toFloat())
        verify(transitions, never()).startTransition(any(), any(), any())
    }

    @Test
    fun dragEndedOutsideValidArea_animateToSnappedBounds() {
        val task = setupTask()
        val transition = mock<IBinder>()
        val transitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        pinnedLayerController.pinTask(transition, task, null)
        pinnedLayerController.onTransitionReady(
            transition,
            transitionInfo,
            startTransaction,
            finishTransaction,
        )

        val displayLayout = requireNotNull(displayController.getDisplayLayout(DEFAULT_DISPLAY))
        val dragStartBounds = Rect(DEFAULT_TASK_BOUNDS)
        val dragEndBounds = Rect(dragStartBounds)
        // Drag behind right edge of the drag area.
        dragEndBounds.offset(displayLayout.width(), 0)

        val result = pinnedLayerController.onDragEnded(leash, task, dragStartBounds, dragEndBounds)
        assertFalse(result)

        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(transitions).startTransition(any(), wctCaptor.capture(), anyOrNull())

        val wct = wctCaptor.firstValue
        val changeBounds = wct.findBoundsChange(task)
        val expectedBounds =
            Rect(
                DISPLAY_STABLE_BOUNDS.right - MIN_WINDOW_SIZE,
                dragEndBounds.top,
                DISPLAY_STABLE_BOUNDS.right,
                dragStartBounds.bottom,
            )

        assertEquals(expectedBounds, changeBounds)
    }

    @Test
    fun dragEnded_endBoundsWithinDragArea_updateTaskBounds() {
        val task = setupTask()
        val transition = mock<IBinder>()
        val transitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        pinnedLayerController.pinTask(transition, task, null)
        pinnedLayerController.onTransitionReady(
            transition,
            transitionInfo,
            startTransaction,
            finishTransaction,
        )

        val dragStartBounds = Rect(DEFAULT_TASK_BOUNDS)
        val dragEndBounds = Rect(DEFAULT_TASK_BOUNDS)
        dragEndBounds.offset(100, 100)

        val result = pinnedLayerController.onDragEnded(leash, task, dragStartBounds, dragEndBounds)
        assertFalse(result)

        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(transitions).startTransition(any(), wctCaptor.capture(), any())

        val wct = wctCaptor.firstValue
        val changeBounds = wct.findBoundsChange(task)
        assertEquals(dragEndBounds, changeBounds)
    }

    @Test
    fun moveToDisplay_taskNotPinned_doNothing() {
        val task = setupTask()
        setupDisplay(DISPLAY_1, isDesktopModeSupported = true)

        val result = pinnedLayerController.moveToDisplay(task, DISPLAY_1)
        assertFalse(result)
    }

    @Test
    fun moveToDisplay_displayDoNotExist_doNothing() {
        val task = setupTask()

        val result = pinnedLayerController.moveToDisplay(task, DISPLAY_1)
        assertFalse(result)
    }

    @Test
    fun moveToDisplay_displayDoNotSupportDesktopMode_doNothing() {
        val task = setupTask()
        setupDisplay(DISPLAY_1, isDesktopModeSupported = false)

        val result = pinnedLayerController.moveToDisplay(task, DISPLAY_1)
        assertFalse(result)
    }

    @Test
    fun moveToSameDisplay_doNothing() {
        val task = setupTask()
        setupDisplay(DISPLAY_1, isDesktopModeSupported = true)

        val result = pinnedLayerController.moveToDisplay(task, DEFAULT_DISPLAY)
        assertFalse(result)
    }

    @Test
    fun moveToDisplay_policiesAreValid_startTransition() {
        val task = setupTask()
        pinTask(task)
        setupDisplay(DISPLAY_1, isDesktopModeSupported = true)

        val result = pinnedLayerController.moveToDisplay(task, DISPLAY_1, DEFAULT_TASK_BOUNDS)
        assertTrue(result)
    }

    @Test
    fun moveToDisplay_taskHasInvalidBounds_clampBoundsAndMove() {
        val task = setupTask()
        pinTask(task)
        setupDisplay(DISPLAY_1)

        val displayLayout = requireNotNull(displayController.getDisplayLayout(DISPLAY_1))
        val newBounds = Rect(DEFAULT_TASK_BOUNDS)
        // The window was moved behind the right edge.
        newBounds.offset(displayLayout.width(), 0)

        // Assert transition was started.
        val result = pinnedLayerController.moveToDisplay(task, DISPLAY_1, newBounds)
        assertTrue(result)

        // Assert bounds change.
        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(transitions).startTransition(any(), wctCaptor.capture(), anyOrNull())

        val wct = wctCaptor.firstValue
        val changeBounds = wct.findBoundsChange(task)
        val expectedBounds =
            Rect(
                DISPLAY_STABLE_BOUNDS.right - MIN_WINDOW_SIZE,
                newBounds.top,
                DISPLAY_STABLE_BOUNDS.right,
                newBounds.bottom,
            )

        assertEquals(expectedBounds, changeBounds)
    }

    @Test
    fun onDragEnded_displayIsDisconnected_moveToOriginalBounds() {
        val task = setupTask()
        pinTask(task)
        setupDisplay(DISPLAY_1)

        // Disconnect the display.
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DISPLAY_1)).thenReturn(null)
        whenever(displayController.getDisplayLayout(DISPLAY_1)).thenReturn(null)

        val newBounds = Rect(DEFAULT_TASK_BOUNDS)
        // Assert no transitions has been started and mirrors will be cleared by the API caller.
        val result =
            pinnedLayerController.onDragEnded(
                leash,
                task,
                DEFAULT_TASK_BOUNDS,
                newBounds,
                DISPLAY_1,
            )
        assertTrue(result)

        // Verify bounds restored to the start drag bounds.
        val leftCaptor = argumentCaptor<Float>()
        val topCaptor = argumentCaptor<Float>()
        verify(poolTransaction).setPosition(any(), leftCaptor.capture(), topCaptor.capture())

        assertEquals(DEFAULT_TASK_BOUNDS.left, leftCaptor.firstValue.roundToInt())
        assertEquals(DEFAULT_TASK_BOUNDS.top, topCaptor.firstValue.roundToInt())
    }

    @Test
    fun onDisplayDisconnect_taskOnRemovedDisplay_returnsWct() {
        setupDisplay(displayId = DISPLAY_1, isDesktopModeSupported = true)
        val task = setupTask(displayId = DISPLAY_1)

        pinTask(task)

        val wct =
            pinnedLayerController.getDisplayDisconnectChanges(mock(), DISPLAY_1, DEFAULT_DISPLAY)
        assertNotNull(wct, "wct should not be null if task is on disconnected display")
    }

    @Test
    fun onDisplayDisconnect_taskOnDifferentDisplay_returnsNull() {
        setupDisplay(displayId = DISPLAY_1, isDesktopModeSupported = true)
        val task = setupTask(displayId = DEFAULT_DISPLAY)

        pinTask(task)

        val wct =
            pinnedLayerController.getDisplayDisconnectChanges(mock(), DISPLAY_1, DEFAULT_DISPLAY)
        assertTrue(wct == null, "wct should be null if task is on different display")
    }

    @Test
    fun checkFullscreenTaskOpened_closesPinnedTask() {
        val pinnedTask = setupTask(displayId = DEFAULT_DISPLAY)
        pinTask(pinnedTask)

        val fullscreenTask = setupTask(displayId = DEFAULT_DISPLAY, taskId = 100)
        fullscreenTask.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN

        val change = TransitionInfo.Change(MockToken.token(), mock())
        change.taskInfo = fullscreenTask
        change.mode = TRANSIT_OPEN

        val info = TransitionInfo(TRANSIT_OPEN, FLAG_NONE)
        info.addChange(change)

        pinnedLayerController.onTransitionReady(mock(), info, startTransaction, finishTransaction)

        verify(transitions).startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull())
    }

    @Test
    fun checkFullscreenTaskToFront_closesPinnedTask() {
        val pinnedTask = setupTask(displayId = DEFAULT_DISPLAY)
        pinTask(pinnedTask)

        val fullscreenTask = setupTask(displayId = DEFAULT_DISPLAY, taskId = 100)
        fullscreenTask.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN

        val change = TransitionInfo.Change(MockToken.token(), mock())
        change.taskInfo = fullscreenTask
        change.mode = TRANSIT_TO_FRONT

        val info = TransitionInfo(TRANSIT_TO_FRONT, FLAG_NONE)
        info.addChange(change)

        pinnedLayerController.onTransitionReady(mock(), info, startTransaction, finishTransaction)

        verify(transitions).startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull())
    }

    @Test
    fun checkFullscreenTaskChanged_closesPinnedTask() {
        val pinnedTask = setupTask(displayId = DEFAULT_DISPLAY)
        pinTask(pinnedTask)

        val fullscreenTask = setupTask(displayId = DEFAULT_DISPLAY, taskId = 100)
        fullscreenTask.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN

        val change = TransitionInfo.Change(MockToken.token(), mock())
        change.taskInfo = fullscreenTask
        change.mode = TRANSIT_CHANGE

        val info = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)
        info.addChange(change)

        pinnedLayerController.onTransitionReady(mock(), info, startTransaction, finishTransaction)

        verify(transitions).startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull())
    }

    @Test
    fun checkFullscreenTaskOnDifferentDisplay_doesNotClosePinnedTask() {
        val pinnedTask = setupTask(displayId = DEFAULT_DISPLAY)
        pinTask(pinnedTask)

        val fullscreenTask = setupTask(displayId = DISPLAY_1, taskId = 100)
        fullscreenTask.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN

        val change = TransitionInfo.Change(MockToken.token(), mock())
        change.taskInfo = fullscreenTask
        change.mode = TRANSIT_OPEN

        val info = TransitionInfo(TRANSIT_OPEN, FLAG_NONE)
        info.addChange(change)

        pinnedLayerController.onTransitionReady(mock(), info, startTransaction, finishTransaction)

        verify(transitions, never()).startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull())
    }

    @Test
    fun checkNonFullscreenTaskOpened_doesNotClosePinnedTask() {
        val pinnedTask = setupTask(displayId = DEFAULT_DISPLAY)
        pinTask(pinnedTask)

        val otherTask = setupTask(displayId = DEFAULT_DISPLAY, taskId = 100)
        otherTask.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM

        val change = TransitionInfo.Change(MockToken.token(), mock())
        change.taskInfo = otherTask
        change.mode = TRANSIT_OPEN

        val info = TransitionInfo(TRANSIT_OPEN, FLAG_NONE)
        info.addChange(change)

        pinnedLayerController.onTransitionReady(mock(), info, startTransaction, finishTransaction)

        verify(transitions, never()).startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull())
    }

    @Test
    fun checkWallpaperChange_doesNotClosePinnedTask() {
        val pinnedTask = setupTask(displayId = DEFAULT_DISPLAY)
        pinTask(pinnedTask)

        val fullscreenTask = setupTask(displayId = DEFAULT_DISPLAY, taskId = 100)
        fullscreenTask.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN

        val change = TransitionInfo.Change(MockToken.token(), mock())
        change.taskInfo = fullscreenTask
        change.mode = TRANSIT_OPEN
        change.flags = FLAG_IS_WALLPAPER

        val info = TransitionInfo(TRANSIT_OPEN, FLAG_NONE)
        info.addChange(change)

        pinnedLayerController.onTransitionReady(mock(), info, startTransaction, finishTransaction)

        verify(transitions, never()).startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull())
    }

    private fun setupTask(
        displayId: Int = DEFAULT_DISPLAY,
        bounds: Rect = DEFAULT_TASK_BOUNDS,
        taskId: Int? = null,
    ): RunningTaskInfo {
        val task =
            DesktopTestHelpers.createFreeformTask(
                displayId = displayId,
                bounds = bounds,
                taskId = taskId,
            )
        return task
    }

    private fun setupDisplay(
        displayId: Int = DEFAULT_DISPLAY,
        token: WindowContainerToken = MockToken.token(),
        isDesktopModeSupported: Boolean = true,
    ) {
        desktopState.overrideDesktopModeSupportPerDisplay[displayId] = isDesktopModeSupported
        shellDesktopState.overrideWindowDropTargetEligibility[displayId] = isDesktopModeSupported

        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(displayId))
            .thenReturn(DisplayAreaInfo(token, displayId, DisplayAreaOrganizer.FEATURE_UNDEFINED))

        // Default display setup: 1080x1920, density 1.0, insets top=100 bottom=50
        // 10px padding for pinned window
        val testableResources = mContext.getOrCreateTestableResources()
        testableResources.addOverride(R.dimen.pinned_window_init_padding, 10)

        val displayMetrics = DisplayMetrics()
        displayMetrics.density = 1.0f

        val displayLayout = mock<DisplayLayout>()
        whenever(testableResources.resources.displayMetrics).thenReturn(displayMetrics)
        whenever(displayController.getDisplayLayout(displayId)).thenReturn(displayLayout)
        whenever(displayController.getDisplayContext(displayId)).thenReturn(context)
        whenever(displayLayout.width()).thenReturn(DISPLAY_WIDTH)
        whenever(displayLayout.height()).thenReturn(DISPLAY_HEIGHT)
        whenever(displayLayout.stableInsets()).thenReturn(DISPLAY_STABLE_INSETS)
        whenever(displayLayout.getStableBounds(any())).thenAnswer {
            val outRect = it.getArgument<Rect>(0)
            outRect.set(DISPLAY_STABLE_BOUNDS)
        }
    }

    private fun pinTask(task: RunningTaskInfo) {
        val transition = mock<IBinder>()
        val transitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        shellTaskOrganizer.stub { on { getRunningTaskInfo(task.taskId) } doReturn task }
        pinnedLayerController.pinTask(transition, task, null)
        pinnedLayerController.onTransitionReady(
            transition,
            transitionInfo,
            startTransaction,
            finishTransaction,
        )
    }

    private fun WindowContainerTransaction.findBoundsChange(task: RunningTaskInfo): Rect? =
        changes.entries
            .find { (token, change) ->
                token == task.token.asBinder() &&
                    (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0
            }
            ?.value
            ?.configuration
            ?.windowConfiguration
            ?.bounds

    private companion object {
        private const val DEFAULT_DISPLAY = 0
        private const val DISPLAY_1 = 1
        private const val MIN_WINDOW_SIZE = 220
        private const val DISPLAY_WIDTH = 1920
        private const val DISPLAY_HEIGHT = 1080

        private val DISPLAY_STABLE_INSETS = Rect(0, 100, 0, 50)
        private val DEFAULT_TASK_BOUNDS =
            Rect(
                0,
                DISPLAY_STABLE_INSETS.top,
                MIN_WINDOW_SIZE,
                MIN_WINDOW_SIZE + DISPLAY_STABLE_INSETS.top,
            )

        private val DISPLAY_STABLE_BOUNDS =
            Rect(
                DISPLAY_STABLE_INSETS.left,
                DISPLAY_STABLE_INSETS.top,
                DISPLAY_WIDTH - DISPLAY_STABLE_INSETS.right,
                DISPLAY_HEIGHT - DISPLAY_STABLE_INSETS.bottom,
            )
    }
}
