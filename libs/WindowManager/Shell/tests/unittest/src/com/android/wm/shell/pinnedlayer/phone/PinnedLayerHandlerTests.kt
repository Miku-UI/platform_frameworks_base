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

import android.app.ActivityManager.AppTask.WINDOWING_LAYER_NORMAL_APP
import android.app.ActivityManager.AppTask.WINDOWING_LAYER_PINNED
import android.app.ActivityManager.AppTask.WindowingLayer
import android.app.ActivityManager.RunningTaskInfo
import android.app.TaskWindowingLayerRequestHandler.REMOTE_CALLBACK_RESULT_KEY
import android.app.TaskWindowingLayerRequestHandler.RESULT_APPROVED
import android.app.TaskWindowingLayerRequestHandler.RESULT_FAILED_BAD_STATE
import android.graphics.Rect
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.view.WindowManager.TransitionType
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_NONE
import android.window.TransitionRequestInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.FakeShellDesktopState
import com.android.wm.shell.desktopmode.NormalAppLayerController
import com.android.wm.shell.desktopmode.WindowDragTransitionHandler
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.shared.TransactionPool
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Unit tests against [PinnedLayerHandler]
 *
 * Build/Install/Run: atest WMShellUnitTests:PinnedLayerHandlerTests
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
@RunWith(AndroidJUnit4::class)
class PinnedLayerHandlerTests : ShellTestCase() {

    @Mock private lateinit var shellInit: ShellInit
    @Mock private lateinit var transitions: Transitions
    @Mock private lateinit var startTransaction: SurfaceControl.Transaction
    @Mock private lateinit var finishTransaction: SurfaceControl.Transaction
    @Mock private lateinit var normalLayerController: NormalAppLayerController
    @Mock private lateinit var presentationController: PinnedLayerPresentationController
    @Mock private lateinit var windowDragTransitionHandler: WindowDragTransitionHandler
    @Mock
    private lateinit var pinnedWindowRepositionAnimationHandler:
        PinnedWindowRepositionAnimationHandler
    @Mock private lateinit var transactionPool: TransactionPool
    @Mock private lateinit var rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock
    private lateinit var multiDisplayDragMoveIndicatorController:
        MultiDisplayDragMoveIndicatorController
    @Mock private lateinit var desktopUserRepositories: DesktopUserRepositories
    @Mock private lateinit var desktopTasksController: DesktopTasksController
    @Mock private lateinit var desktopRepository: DesktopRepository
    @Mock private lateinit var shellTaskOrganizer: ShellTaskOrganizer
    private val windowRepositionAnimator = mock<PinnedWindowRepositionAnimator>()

    private lateinit var desktopState: FakeDesktopState
    private lateinit var shellDesktopState: FakeShellDesktopState
    private lateinit var pinnedLayerController: PinnedLayerController
    private lateinit var pinnedLayerUiState: PinnedLayerUiState
    private lateinit var pinnedLayerHandler: PinnedLayerHandler

    @Before
    fun setup() {
        desktopState = FakeDesktopState()
        desktopState.canEnterDesktopMode = true
        shellDesktopState = FakeShellDesktopState(desktopState)
        shellDesktopState.canBeWindowDropTarget = true

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
        pinnedLayerUiState = PinnedLayerUiState()
        pinnedLayerHandler =
            PinnedLayerHandler(
                shellInit,
                transitions,
                pinnedLayerController,
                pinnedLayerUiState,
                normalLayerController,
                desktopUserRepositories,
                desktopTasksController,
            )
        whenever(presentationController.isTaskSupportedForPinning(any())).thenReturn(true)
        whenever(desktopUserRepositories.getProfile(USER_ID_0)).thenReturn(desktopRepository)

        // Setup default display with a desk containing one desktop window that'll be pinned.
        whenever(
                desktopRepository.isOnlyVisibleNonClosingTaskInDesk(
                    TASK_ID_0,
                    DESK_ID_0,
                    DISPLAY_ID_0,
                )
            )
            .thenReturn(true)
        whenever(desktopRepository.getActiveDeskId(DISPLAY_ID_0)).thenReturn(DESK_ID_0)
        whenever(desktopRepository.getDefaultDeskId(DISPLAY_ID_0)).thenReturn(DESK_ID_0)
        whenever(desktopRepository.getDeskIdForTask(TASK_ID_0)).thenReturn(DESK_ID_0)
    }

    @Test
    fun testHandlePinRequest_noPinnedWindow_shouldPinWindow() {
        val transition = mock<IBinder>()
        val callback = mock<IRemoteCallback>()
        val requestInfo =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                callback,
                triggerTaskId = TASK_ID_0,
            )
        val transitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        pinnedLayerHandler.handleRequest(transition, requestInfo)
        pinnedLayerController.onTransitionReady(
            transition,
            transitionInfo,
            startTransaction,
            finishTransaction,
        )

        verifyCallbackResult(callback, RESULT_APPROVED)
        assertTrue(pinnedLayerController.isPinned(TASK_ID_0))
        assertEquals(TASK_ID_0, pinnedLayerController.getCurrentPinnedTask()?.taskId)
    }

    @Test
    fun testHandlePinRequest_taskIsPinned_approveRequestAndDoNothing() {
        val transition1 = mock<IBinder>()
        val callback1 = mock<IRemoteCallback>()
        val pinnedToken = MockToken.token()
        val requestInfo1 =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                callback1,
                triggerTaskId = TASK_ID_0,
                triggerTaskToken = pinnedToken,
            )
        val transitionInfo1 = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)
        transitionInfo1.addChange(
            buildChange(TRANSIT_CHANGE, requireNotNull(requestInfo1.triggerTask))
        )

        val transition2 = mock<IBinder>()
        val callback2 = mock<IRemoteCallback>()
        val requestInfo2 =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                callback2,
                triggerTaskId = TASK_ID_0,
                triggerTaskToken = pinnedToken,
            )
        val transitionInfo2 = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)
        transitionInfo2.addChange(
            buildChange(TRANSIT_CHANGE, requireNotNull(requestInfo2.triggerTask))
        )

        val wct1 = pinnedLayerHandler.handleRequest(transition1, requestInfo1)
        pinnedLayerController.onTransitionReady(
            transition1,
            transitionInfo1,
            startTransaction,
            finishTransaction,
        )

        val wct2 = pinnedLayerHandler.handleRequest(transition2, requestInfo2)
        pinnedLayerController.onTransitionReady(
            transition2,
            transitionInfo2,
            startTransaction,
            finishTransaction,
        )
        pinnedLayerHandler.onTransitionConsumed(transition2, /* aborted= */ true, null)

        assertNotNull(wct1)
        assertNotNull(wct2)
        verifyCallbackResult(callback1, RESULT_APPROVED)
        verifyCallbackResult(callback2, RESULT_APPROVED)
        assertTrue(pinnedLayerController.isPinned(TASK_ID_0))
        assertEquals(TASK_ID_0, pinnedLayerController.getCurrentPinnedTask()?.taskId)
    }

    @Test
    fun testHandleNotPinRequest_noPinnedWindow_doNothing() {
        val transition = mock<IBinder>()
        val callback = mock<IRemoteCallback>()
        val requestInfo =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_NORMAL_APP,
                callback,
                triggerTaskId = TASK_ID_0,
            )
        val transitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        pinnedLayerHandler.handleRequest(transition, requestInfo)
        pinnedLayerController.onTransitionReady(
            transition,
            transitionInfo,
            startTransaction,
            finishTransaction,
        )

        verifyNoInteractions(callback)
        assertTrue(pinnedLayerController.isNotPinned(TASK_ID_0))
        assertNull(pinnedLayerController.getCurrentPinnedTask())
    }

    @Test
    fun testObserveUnrelatedChangeRequest_doNothing() {
        val transition = mock<IBinder>()
        val token = MockToken.token()
        val requestInfo =
            sendTransitionRequest(
                TRANSIT_CHANGE,
                triggerTaskId = TASK_ID_0,
                triggerTaskToken = token,
            )
        val transitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        pinnedLayerHandler.handleRequest(transition, requestInfo)
        pinnedLayerController.onTransitionReady(
            transition,
            transitionInfo,
            startTransaction,
            finishTransaction,
        )

        assertTrue(pinnedLayerController.isNotPinned(TASK_ID_0))
        assertNull(pinnedLayerController.getCurrentPinnedTask())
    }

    @Test
    fun handlePinRequest_hasPinnedWindow_pinNewAndMinimizePrev() {
        val transition1 = mock<IBinder>()
        val callback1 = mock<IRemoteCallback>()
        val requestInfo1 =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                callback1,
                triggerTaskId = TASK_ID_0,
            )
        val transitionInfo1 = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        val transition2 = mock<IBinder>()
        val callback2 = mock<IRemoteCallback>()
        val requestInfo2 =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                callback2,
                triggerTaskId = TASK_ID_1,
            )
        val transitionInfo2 = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)
        transitionInfo2.addChange(
            buildChange(TRANSIT_TO_BACK, requireNotNull(requestInfo1.triggerTask))
        )

        pinnedLayerHandler.handleRequest(transition1, requestInfo1)
        pinnedLayerController.onTransitionReady(
            transition1,
            transitionInfo1,
            startTransaction,
            finishTransaction,
        )
        pinnedLayerHandler.handleRequest(transition2, requestInfo2)
        pinnedLayerController.onTransitionReady(
            transition2,
            transitionInfo2,
            startTransaction,
            finishTransaction,
        )

        verifyCallbackResult(callback1, RESULT_APPROVED)
        verifyCallbackResult(callback2, RESULT_APPROVED)
        assertTrue(pinnedLayerController.isPinned(TASK_ID_0))
        assertTrue(pinnedLayerController.isPinned(TASK_ID_1))
        assertEquals(TASK_ID_1, pinnedLayerController.getCurrentPinnedTask()?.taskId)
    }

    @Test
    fun handlePinRequest_theWindowIsTheLastDesktopWindow_cleanDesktopState() {
        val transition1 = mock<IBinder>()
        val callback1 = mock<IRemoteCallback>()
        val requestInfo1 =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                callback1,
                triggerTaskId = TASK_ID_0,
            )
        val transitionInfo1 = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        pinnedLayerHandler.handleRequest(transition1, requestInfo1)
        pinnedLayerController.onTransitionReady(
            transition1,
            transitionInfo1,
            startTransaction,
            finishTransaction,
        )

        verifyCallbackResult(callback1, RESULT_APPROVED)
        assertTrue(pinnedLayerController.isPinned(TASK_ID_0))
        assertEquals(TASK_ID_0, pinnedLayerController.getCurrentPinnedTask()?.taskId)
        verify(desktopTasksController)
            .performDesktopExitCleanUp(
                any(),
                eq(DESK_ID_0),
                eq(DISPLAY_ID_0),
                eq(USER_ID_0),
                eq(true),
                eq(TASK_ID_0),
                eq(false),
                eq(true),
                eq(false),
                eq(false),
                eq(ExitReason.TASK_MOVED_FROM_DESK),
            )
    }

    @Test
    fun handlePinRequest_theWindowIsNotTheLastDesktopWindow_doNotCleanDesktopState() {
        whenever(
                desktopRepository.isOnlyVisibleNonClosingTaskInDesk(
                    TASK_ID_0,
                    DESK_ID_0,
                    DISPLAY_ID_0,
                )
            )
            .thenReturn(false)

        val transition1 = mock<IBinder>()
        val callback1 = mock<IRemoteCallback>()
        val requestInfo1 =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                callback1,
                triggerTaskId = TASK_ID_0,
            )
        val transitionInfo1 = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        pinnedLayerHandler.handleRequest(transition1, requestInfo1)
        pinnedLayerController.onTransitionReady(
            transition1,
            transitionInfo1,
            startTransaction,
            finishTransaction,
        )

        verifyCallbackResult(callback1, RESULT_APPROVED)
        assertTrue(pinnedLayerController.isPinned(TASK_ID_0))
        assertEquals(TASK_ID_0, pinnedLayerController.getCurrentPinnedTask()?.taskId)
        verify(desktopTasksController, never())
            .performDesktopExitCleanUp(
                any(),
                eq(DESK_ID_0),
                eq(DISPLAY_ID_0),
                eq(USER_ID_0),
                eq(true),
                eq(TASK_ID_0),
                eq(false),
                eq(true),
                eq(false),
                eq(false),
                eq(ExitReason.TASK_MOVED_FROM_DESK),
            )
    }

    @Test
    fun handlePinRequest_findsProperBounds() {
        val transition = mock<IBinder>()
        val callback = mock<IRemoteCallback>()
        val requestInfo = setupWindowingLayerTransition(WINDOWING_LAYER_PINNED, callback)
        val transitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)
        val bounds = Rect(100, 100, 200, 200)
        whenever(presentationController.getPinEntryDestinationBounds(any(), anyInt()))
            .thenReturn(bounds)

        val wct = pinnedLayerHandler.handleRequest(transition, requestInfo)

        val actualBounds =
            wct?.changes
                ?.get(requestInfo.triggerTask!!.token.asBinder())
                ?.configuration
                ?.windowConfiguration
                ?.bounds
        assertEquals(bounds, actualBounds)
    }

    @Test
    fun handleRequest_pinRequestNotSupported_rejectsRequest() {
        val transition = mock<IBinder>()
        val callback = mock<IRemoteCallback>()
        val requestInfo =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                callback,
                triggerTaskId = TASK_ID_0,
            )
        whenever(presentationController.isTaskSupportedForPinning(any())).thenReturn(false)

        val wct = pinnedLayerHandler.handleRequest(transition, requestInfo)

        assertNull(wct)
        verifyCallbackResult(callback, RESULT_FAILED_BAD_STATE)
    }

    @Test
    fun handleMinimizeRequest_taskIsPinned_keepPinnedRecord() {
        val pinTransition = mock<IBinder>()
        val pinCallback = mock<IRemoteCallback>()
        val pinnedToken = MockToken.token()
        val pinRequestInfo =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                pinCallback,
                triggerTaskId = TASK_ID_0,
                triggerTaskToken = pinnedToken,
            )
        val pinTransitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        val backTransition = mock<IBinder>()
        val backRequestInfo =
            sendTransitionRequest(
                TRANSIT_TO_BACK,
                triggerTaskId = TASK_ID_0,
                triggerTaskToken = pinnedToken,
            )
        val backTransitionInfo = TransitionInfo(TRANSIT_TO_BACK, FLAG_NONE)
        backTransitionInfo.addChange(
            buildChange(TRANSIT_TO_BACK, requireNotNull(backRequestInfo.triggerTask))
        )

        pinnedLayerHandler.handleRequest(pinTransition, pinRequestInfo)
        pinnedLayerController.onTransitionReady(
            pinTransition,
            pinTransitionInfo,
            startTransaction,
            finishTransaction,
        )
        pinnedLayerHandler.handleRequest(backTransition, backRequestInfo)
        pinnedLayerController.onTransitionReady(
            backTransition,
            backTransitionInfo,
            startTransaction,
            finishTransaction,
        )

        verifyCallbackResult(pinCallback, RESULT_APPROVED)
        assertTrue(pinnedLayerController.isPinned(TASK_ID_0))
        assertNull(pinnedLayerController.getCurrentPinnedTask())
    }

    @Test
    fun handleCloseRequest_taskIsPinned_unpinWindowAndClose() {
        val pinTransition = mock<IBinder>()
        val pinCallback = mock<IRemoteCallback>()
        val pinRequestInfo =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                pinCallback,
                triggerTaskId = TASK_ID_0,
            )
        val pinTransitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        val closeTransition = mock<IBinder>()
        val closeRequestInfo = sendTransitionRequest(TRANSIT_CLOSE, triggerTaskId = TASK_ID_0)
        val closeTransitionInfo = TransitionInfo(TRANSIT_CLOSE, FLAG_NONE)
        closeTransitionInfo.addChange(
            buildChange(TRANSIT_CLOSE, requireNotNull(closeRequestInfo.triggerTask))
        )

        pinnedLayerHandler.handleRequest(pinTransition, pinRequestInfo)
        pinnedLayerController.onTransitionReady(
            pinTransition,
            pinTransitionInfo,
            startTransaction,
            finishTransaction,
        )
        pinnedLayerHandler.handleRequest(closeTransition, closeRequestInfo)
        pinnedLayerController.onTransitionReady(
            closeTransition,
            closeTransitionInfo,
            startTransaction,
            finishTransaction,
        )

        verifyCallbackResult(pinCallback, RESULT_APPROVED)
        assertTrue(pinnedLayerController.isNotPinned(TASK_ID_0))
        assertNull(pinnedLayerController.getCurrentPinnedTask())
    }

    @Test
    fun handleMoveToFront_taskIsPinned_repinAsActive() {
        val pinTransition = mock<IBinder>()
        val pinCallback = mock<IRemoteCallback>()
        val pinnedTaskToken = MockToken.token()
        val pinRequestInfo =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                pinCallback,
                triggerTaskId = TASK_ID_0,
                triggerTaskToken = pinnedTaskToken,
            )
        val pinTransitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        val backTransition = mock<IBinder>()
        val backRequestInfo =
            sendTransitionRequest(
                TRANSIT_TO_BACK,
                triggerTaskId = TASK_ID_0,
                triggerTaskToken = pinnedTaskToken,
            )
        val backTransitionInfo = TransitionInfo(TRANSIT_TO_BACK, FLAG_NONE)
        backTransitionInfo.addChange(
            buildChange(TRANSIT_TO_BACK, requireNotNull(backRequestInfo.triggerTask))
        )

        val frontTransition = mock<IBinder>()
        val frontRequestInfo =
            sendTransitionRequest(
                TRANSIT_TO_FRONT,
                triggerTaskId = TASK_ID_0,
                triggerTaskToken = pinnedTaskToken,
            )
        val frontTransitionInfo = TransitionInfo(TRANSIT_TO_FRONT, FLAG_NONE)
        frontTransitionInfo.addChange(
            buildChange(TRANSIT_TO_FRONT, requireNotNull(frontRequestInfo.triggerTask))
        )

        pinnedLayerHandler.handleRequest(pinTransition, pinRequestInfo)
        pinnedLayerController.onTransitionReady(
            pinTransition,
            pinTransitionInfo,
            startTransaction,
            finishTransaction,
        )
        pinnedLayerHandler.handleRequest(backTransition, backRequestInfo)
        pinnedLayerController.onTransitionReady(
            backTransition,
            backTransitionInfo,
            startTransaction,
            finishTransaction,
        )

        pinnedLayerHandler.handleRequest(frontTransition, frontRequestInfo)
        pinnedLayerController.onTransitionReady(
            frontTransition,
            frontTransitionInfo,
            startTransaction,
            finishTransaction,
        )

        verifyCallbackResult(pinCallback, RESULT_APPROVED)
        assertTrue(pinnedLayerController.isPinned(TASK_ID_0))
        assertEquals(TASK_ID_0, pinnedLayerController.getCurrentPinnedTask()?.taskId)
    }

    @Test
    fun augmentToDismissPinnedTask_hasActivePinnedTask_unpinsTask() {
        val pinTransition = mock<IBinder>()
        val pinCallback = mock<IRemoteCallback>()
        val pinRequestInfo =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                pinCallback,
                triggerTaskId = TASK_ID_0,
            )
        val pinTransitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        // Pin the task
        pinnedLayerHandler.handleRequest(pinTransition, pinRequestInfo)
        pinnedLayerController.onTransitionReady(
            pinTransition,
            pinTransitionInfo,
            startTransaction,
            finishTransaction,
        )
        assertTrue(pinnedLayerHandler.hasActivePinnedTask())

        // Augment to dismiss the pin
        val dismissTransition = mock<IBinder>()
        val dismissRequestInfo = mock<TransitionRequestInfo>()
        val wct = WindowContainerTransaction()
        pinnedLayerHandler.augmentRequestDismissPinnedTask(
            dismissTransition,
            dismissRequestInfo,
            wct,
        )

        // After augmenting, start the animation to process the unpin
        val backRequestInfo = sendTransitionRequest(TRANSIT_TO_BACK, triggerTaskId = TASK_ID_0)
        val backTransitionInfo = TransitionInfo(TRANSIT_TO_BACK, FLAG_NONE)
        backTransitionInfo.addChange(
            buildChange(TRANSIT_TO_BACK, requireNotNull(backRequestInfo.triggerTask))
        )
        pinnedLayerController.onTransitionReady(
            dismissTransition,
            backTransitionInfo,
            startTransaction,
            finishTransaction,
        )

        assertFalse(pinnedLayerHandler.hasActivePinnedTask())
    }

    @Test
    fun handleUnpinRequest_taskIsPinned_unpinAndSwitchLayer() {
        val pinTransition = mock<IBinder>()
        val pinCallback = mock<IRemoteCallback>()
        val pinnedToken = MockToken.token()
        val pinRequestInfo =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                pinCallback,
                triggerTaskId = TASK_ID_0,
                triggerTaskToken = pinnedToken,
            )
        val pinTransitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)
        pinTransitionInfo.addChange(
            buildChange(TRANSIT_CHANGE, requireNotNull(pinRequestInfo.triggerTask))
        )

        val normalLayerTransition = mock<IBinder>()
        val normalLayerCallback = mock<IRemoteCallback>()
        val normalLayerRequestInfo =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_NORMAL_APP,
                normalLayerCallback,
                triggerTaskId = TASK_ID_0,
                triggerTaskToken = pinnedToken,
            )
        val normalLayerTransitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)
        normalLayerTransitionInfo.addChange(
            buildChange(TRANSIT_CHANGE, requireNotNull(normalLayerRequestInfo.triggerTask))
        )

        whenever(
                normalLayerController.moveTaskToNormalLayer(
                    normalLayerTransition,
                    requireNotNull(normalLayerRequestInfo.triggerTask),
                    normalLayerCallback,
                )
            )
            .thenReturn(WindowContainerTransaction())

        // Pin the task first
        pinnedLayerHandler.handleRequest(pinTransition, pinRequestInfo)
        pinnedLayerController.onTransitionReady(
            pinTransition,
            pinTransitionInfo,
            startTransaction,
            finishTransaction,
        )

        // Then unpin it by switching layer
        pinnedLayerHandler.handleRequest(normalLayerTransition, normalLayerRequestInfo)
        pinnedLayerController.onTransitionReady(
            normalLayerTransition,
            normalLayerTransitionInfo,
            startTransaction,
            finishTransaction,
        )

        verifyCallbackResult(pinCallback, RESULT_APPROVED)
        assertTrue(pinnedLayerController.isNotPinned(TASK_ID_0))
        assertNull(pinnedLayerController.getCurrentPinnedTask())
    }

    @Test
    fun augmentToDismissPinnedTask_withoutPinnedTask_doesNothing() {
        val wct = WindowContainerTransaction()

        pinnedLayerHandler.augmentRequestDismissPinnedTask(
            mock<IBinder>(),
            mock<TransitionRequestInfo>(),
            wct,
        )

        assertTrue(wct.isEmpty)
    }

    @Test
    fun hasActivePinnedTask_withoutPinnedTask_returnsFalse() {
        assertFalse(pinnedLayerHandler.hasActivePinnedTask())
    }

    @Test
    fun hasActivePinnedTask_withPinnedTask_returnsFalse() {
        val transition = mock<IBinder>()
        val callback = mock<IRemoteCallback>()
        val requestInfo = setupWindowingLayerTransition(WINDOWING_LAYER_PINNED, callback)
        val transitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        pinnedLayerHandler.handleRequest(transition, requestInfo)
        pinnedLayerController.onTransitionReady(
            transition,
            transitionInfo,
            startTransaction,
            finishTransaction,
        )

        assertTrue(pinnedLayerHandler.hasActivePinnedTask())
    }

    @Test
    fun closeTask_taskNotPinned_returnsFalse() {
        // This task should not be pinned as it hasn't been registered anywhere in the
        // PinnedLayerController.
        val taskInfo = RunningTaskInfo().apply { taskId = TASK_ID_0 }

        assertFalse(pinnedLayerController.closeTask(taskInfo))
    }

    @Test
    fun closeTask_taskPinned_returnsTrueAndSendsTransition() {
        val taskToken = MockToken.token()
        val taskInfo =
            RunningTaskInfo().apply {
                token = taskToken
                taskId = TASK_ID_0
            }

        val transition = mock<IBinder>()
        val callback = mock<IRemoteCallback>()
        val requestInfo =
            setupWindowingLayerTransition(
                WINDOWING_LAYER_PINNED,
                callback,
                triggerTaskId = TASK_ID_0,
                triggerTaskToken = taskToken,
            )
        val transitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)
        pinnedLayerHandler.handleRequest(transition, requestInfo)
        pinnedLayerController.onTransitionReady(
            transition,
            transitionInfo,
            startTransaction,
            finishTransaction,
        )

        assertTrue(pinnedLayerController.isPinned(TASK_ID_0))
        assertTrue(pinnedLayerController.closeTask(taskInfo))

        val wctCaptor = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        verify(transitions).startTransition(eq(TRANSIT_CLOSE), wctCaptor.capture(), anyOrNull())
        assertThat(
                wctCaptor.value.hierarchyOps.any { hop ->
                    hop.type == HIERARCHY_OP_TYPE_REMOVE_TASK &&
                        hop.container == taskToken.asBinder()
                }
            )
            .isTrue()
    }

    @Test
    fun requestedLocation_whenTaskIsPinned_updatesBoundsUsingPresentationController() {
        // Given a pinned task
        val pinTransition = mock<IBinder>()
        val callback = mock<IRemoteCallback>()
        val pinRequestInfo = setupWindowingLayerTransition(WINDOWING_LAYER_PINNED, callback)
        val transitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)
        pinnedLayerHandler.handleRequest(pinTransition, pinRequestInfo)
        pinnedLayerController.onTransitionReady(
            pinTransition,
            transitionInfo,
            startTransaction,
            finishTransaction,
        )

        // When a location change is requested
        val newBounds = Rect(300, 300, 400, 400)
        val moveTransition = mock<IBinder>()
        val triggerTask = pinRequestInfo.triggerTask!!
        val moveRequestInfo =
            createTransitionRequestInfo(
                TRANSIT_CHANGE,
                triggerTask,
                TransitionRequestInfo.RequestedLocation(
                    /* displayId= */ 0,
                    Rect(100, 100, 200, 200),
                ),
            )
        whenever(presentationController.calculateNewTaskBounds(any(), any())).thenReturn(newBounds)
        val wct =
            pinnedLayerHandler.handleRequest(moveTransition, moveRequestInfo)
                ?: fail("Expected a non-null WCT")

        // Then wct should contain the new bounds from the presentation controller
        assertThat(wct.changes).hasSize(1)
        assertThat(
                wct.changes
                    .get(triggerTask.token.asBinder())!!
                    .configuration
                    .windowConfiguration
                    .bounds
            )
            .isEqualTo(newBounds)
    }

    private fun verifyCallbackResult(callback: IRemoteCallback, expected: Int) {
        val bundleCaptor = ArgumentCaptor.forClass(Bundle::class.java)
        verify(callback).sendResult(bundleCaptor.capture())

        val bundle = bundleCaptor.value
        assertNotNull(bundle, "Result callback should receive bundle, but it's null")
        assertEquals(expected, bundle.getInt(REMOTE_CALLBACK_RESULT_KEY))
    }

    private fun setupWindowingLayerTransition(
        @WindowingLayer layer: Int = WINDOWING_LAYER_NORMAL_APP,
        callback: IRemoteCallback,
        triggerTaskId: Int = TASK_ID_0,
        triggerTaskToken: WindowContainerToken = MockToken.token(),
    ): TransitionRequestInfo {
        val windowingLayerChange = TransitionRequestInfo.WindowingLayerChange(layer, callback)
        val info =
            sendTransitionRequest(
                TRANSIT_CHANGE,
                triggerTaskId,
                triggerTaskToken = triggerTaskToken,
                windowingLayerChange = windowingLayerChange,
            )
        val triggerTask = info.triggerTask
        shellTaskOrganizer.stub {
            on { getRunningTaskInfo(requireNotNull(triggerTask).taskId) } doReturn triggerTask
        }
        return info
    }

    private fun sendTransitionRequest(
        @TransitionType type: Int,
        triggerTaskId: Int,
        triggerTaskToken: WindowContainerToken = MockToken.token(),
        windowingLayerChange: TransitionRequestInfo.WindowingLayerChange? = null,
    ): TransitionRequestInfo {
        val triggerTask =
            RunningTaskInfo().apply {
                token = triggerTaskToken
                taskId = triggerTaskId
                displayId = DISPLAY_ID_0
                userId = USER_ID_0
            }
        shellTaskOrganizer.stub { on { getRunningTaskInfo(triggerTaskId) } doReturn triggerTask }
        return TransitionRequestInfo(
            type,
            triggerTask,
            null,
            null,
            null,
            null,
            null,
            windowingLayerChange,
            null,
            0,
            0,
        )
    }

    private fun createTransitionRequestInfo(
        @TransitionType type: Int,
        triggerTask: RunningTaskInfo,
        requestedLocation: TransitionRequestInfo.RequestedLocation,
    ) =
        TransitionRequestInfo(
            type,
            triggerTask,
            null,
            null,
            null,
            requestedLocation,
            null,
            null,
            null,
            0,
            0,
        )

    private fun buildChange(
        @TransitionType mode: Int,
        taskInfo: RunningTaskInfo,
    ): TransitionInfo.Change {
        val surfaceControl = mock<SurfaceControl>()
        return TransitionInfo.Change(taskInfo.token, surfaceControl).apply {
            this.mode = mode
            this.taskInfo = taskInfo
        }
    }

    private companion object {
        private const val TASK_ID_0 = 0
        private const val TASK_ID_1 = 1
        private const val DESK_ID_0 = 0
        private const val DISPLAY_ID_0 = 0
        private const val USER_ID_0 = 0
    }
}
