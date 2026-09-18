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
package com.android.wm.shell.common

import android.app.Activity
import android.app.Activity.FULLSCREEN_MODE_REQUEST_ENTER
import android.app.Activity.FULLSCREEN_MODE_REQUEST_EXIT
import android.app.ActivityManager.RunningTaskInfo
import android.app.FullscreenRequestHandler
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.graphics.Rect
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.testing.AndroidTestingRunner
import android.view.WindowManager
import android.window.TransitionRequestInfo
import android.window.TransitionRequestInfo.FullscreenRequestChange
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.Change
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.ClientFullscreenRequestController.ExitFullscreenInvalidationMonitor
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.EnterResult
import com.android.wm.shell.common.ClientFullscreenRequestController.FullscreenRequestHandler.ExitResult
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock

/**
 * Tests for [ClientFullscreenRequestController].
 *
 * Usage: atest WMShellUnitTests:ClientFullscreenRequestControllerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class ClientFullscreenRequestControllerTest : ShellTestCase() {

    private val shellInit = mock<ShellInit>()
    private val transitions = mock<Transitions>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val invalidationMonitorSupplier =
        TestExitFullscreenInvalidationMonitorSupplier(
            transitions = transitions,
            shellTaskOrganizer = shellTaskOrganizer,
        )
    private val remoteCallback = mock<IRemoteCallback>()

    private lateinit var controller: ClientFullscreenRequestController
    private lateinit var bundleCaptor: ArgumentCaptor<Bundle>

    @Before
    fun setUp() {
        bundleCaptor = ArgumentCaptor.forClass(Bundle::class.java)
        controller =
            ClientFullscreenRequestController(
                shellInit,
                transitions,
                shellTaskOrganizer,
                invalidationMonitorSupplier,
            )
    }

    @Test
    fun handleRequestFallback_enterFullscreenWhenFullscreen_fails() {
        val transition = Binder()
        val task = createTask(WINDOWING_MODE_FULLSCREEN)
        val request = request(task, FULLSCREEN_MODE_REQUEST_ENTER, remoteCallback)

        val result = controller.handleRequest(transition, request)

        assertThat(result).isNull()
        verifyRemoteCallbackResult(FullscreenRequestHandler.RESULT_FAILED_ALREADY_FULLY_EXPANDED)
    }

    @Test
    fun handleRequestFallback_enterFullscreenWhenNotFullscreen_fails() {
        val transition = Binder()
        val task = createTask(WINDOWING_MODE_MULTI_WINDOW)
        val request = request(task, FULLSCREEN_MODE_REQUEST_ENTER, remoteCallback)

        val result = controller.handleRequest(transition, request)

        assertThat(result).isNull()
        verifyRemoteCallbackResult(FullscreenRequestHandler.RESULT_FAILED_NOT_SUPPORTED)
    }

    @Test
    fun handleRequestFallback_exitFullscreenWhenFullscreen_fails() {
        val transition = Binder()
        val task = createTask(WINDOWING_MODE_FULLSCREEN)
        val request = request(task, FULLSCREEN_MODE_REQUEST_EXIT, remoteCallback)

        val result = controller.handleRequest(transition, request)

        assertThat(result).isNull()
        verifyRemoteCallbackResult(FullscreenRequestHandler.RESULT_FAILED_NOT_SUPPORTED)
    }

    @Test
    fun handleRequestFallback_exitFullscreenWhenNotFullscreen_fails() {
        val transition = Binder()
        val task = createTask(WINDOWING_MODE_MULTI_WINDOW)
        val request = request(task, FULLSCREEN_MODE_REQUEST_EXIT, remoteCallback)

        val result = controller.handleRequest(transition, request)

        assertThat(result).isNull()
        verifyRemoteCallbackResult(
            FullscreenRequestHandler.RESULT_FAILED_NOT_IN_FULLSCREEN_WITH_HISTORY
        )
    }

    @Test
    fun handleRequestWithHandler_unhandledRequest_fallbackRunsCallback() {
        controller.addHandler(TestHandler.neverAccepts())
        val task = createTask(WINDOWING_MODE_MULTI_WINDOW)
        val request = request(task, FULLSCREEN_MODE_REQUEST_ENTER, remoteCallback)

        val result = controller.handleRequest(Binder(), request)

        assertThat(result).isNull()
        verifyRemoteCallbackResult(FullscreenRequestHandler.RESULT_FAILED_NOT_SUPPORTED)
    }

    @Test
    fun addHandler_firstHandlerAccepts_secondHandlerNotCalled() {
        val testHandler1 = TestHandler.acceptsRequest()
        val testHandler2 = TestHandler.acceptsRequest()
        controller.addHandler(testHandler1)
        controller.addHandler(testHandler2)

        val task = createTask(WINDOWING_MODE_MULTI_WINDOW)
        val request = request(task, FULLSCREEN_MODE_REQUEST_ENTER, remoteCallback)

        controller.handleRequest(Binder(), request)

        assertThat(testHandler1.isEnterCalled).isTrue()
        assertThat(testHandler2.isEnterCalled).isFalse()
    }

    @Test
    fun fallbackHandlerIsAlwaysLast() {
        val testHandler1 = TestHandler.neverAccepts()
        val testHandler2 = TestHandler.neverAccepts()
        controller.addHandler(testHandler1)
        controller.addHandler(testHandler2)

        val task = createTask(WINDOWING_MODE_MULTI_WINDOW)
        val request = request(task, FULLSCREEN_MODE_REQUEST_ENTER, remoteCallback)

        controller.handleRequest(Binder(), request)

        assertThat(testHandler1.isEnterCalled).isTrue()
        assertThat(testHandler2.isEnterCalled).isTrue()
        verifyRemoteCallbackResult(FullscreenRequestHandler.RESULT_FAILED_NOT_SUPPORTED)
    }

    @Test
    fun exitFullscreen_afterApprovedEnter_providesRestorableState() {
        val wct = WindowContainerTransaction()
        val restorableState =
            EnterResult.Approved.RestorableState.Desktop(
                originalDeskId = 123,
                bounds = Rect(200, 200, 800, 800),
            )
        val handler =
            TestHandler(
                enterResult = EnterResult.Approved(wct, mock(), restorableState),
                exitResult = ExitResult.Approved(wct, mock()),
            )
        controller.addHandler(handler)

        val enterTask = createTask(WINDOWING_MODE_MULTI_WINDOW)
        val enterRequest = request(enterTask, FULLSCREEN_MODE_REQUEST_ENTER)
        // Enter fullscreen, which should save the state
        controller.handleRequest(Binder(), enterRequest)
        assertThat(handler.isEnterCalled).isTrue()

        val exitTask = enterTask.apply { setWindowingMode(WINDOWING_MODE_FULLSCREEN) }
        val exitRequest = request(exitTask, FULLSCREEN_MODE_REQUEST_EXIT)
        // Exit fullscreen, which should provide the saved state
        controller.handleRequest(Binder(), exitRequest)
        assertThat(handler.isExitCalled).isTrue()
        assertThat(handler.receivedRestorableState).isEqualTo(restorableState)
    }

    @Test
    fun exitFullscreen_stateIsClearedAfterUse() {
        val wct = WindowContainerTransaction()
        val restorableState =
            EnterResult.Approved.RestorableState.Desktop(
                originalDeskId = 123,
                bounds = Rect(200, 200, 800, 800),
            )
        val handler =
            TestHandler(
                enterResult = EnterResult.Approved(wct, mock(), restorableState),
                exitResult = ExitResult.Approved(wct, mock()),
            )
        controller.addHandler(handler)

        val enterTask = createTask(WINDOWING_MODE_MULTI_WINDOW)
        val enterRequest = request(enterTask, FULLSCREEN_MODE_REQUEST_ENTER)
        // Enter fullscreen, which should save the state
        controller.handleRequest(Binder(), enterRequest)

        val exitTask = enterTask.apply { setWindowingMode(WINDOWING_MODE_FULLSCREEN) }
        val exitRequest = request(exitTask, FULLSCREEN_MODE_REQUEST_EXIT)
        // First exit, which should provide and clear the state
        controller.handleRequest(Binder(), exitRequest)
        assertThat(handler.receivedRestorableState).isEqualTo(restorableState)

        // Second exit, the state should now be null
        controller.handleRequest(Binder(), exitRequest)
        assertThat(handler.receivedRestorableState).isNull()
    }

    @Test
    fun enterFullscreen_notApproved_stateIsNotSaved() {
        val handler =
            TestHandler(
                enterResult =
                    EnterResult.Failed(
                        FullscreenRequestHandler.RESULT_FAILED_NOT_SUPPORTED,
                        mock(),
                    ),
                exitResult = ExitResult.Approved(WindowContainerTransaction(), mock()),
            )
        controller.addHandler(handler)
        val enterTask = createTask(WINDOWING_MODE_MULTI_WINDOW)
        val exitTask = createTask(WINDOWING_MODE_FULLSCREEN)
        val enterRequest = request(enterTask, FULLSCREEN_MODE_REQUEST_ENTER)
        val exitRequest = request(exitTask, FULLSCREEN_MODE_REQUEST_EXIT)

        // This enter request will fail, so no state should be saved.
        controller.handleRequest(Binder(), enterRequest)

        // The exit request should receive a null state.
        controller.handleRequest(Binder(), exitRequest)
        assertThat(handler.receivedRestorableState).isNull()
    }

    @Test
    fun enterFullscreen_approved_setsTaskAllowedToExit() {
        val wct = WindowContainerTransaction()
        val restorableState =
            EnterResult.Approved.RestorableState.Desktop(
                originalDeskId = 123,
                bounds = Rect(200, 200, 800, 800),
            )
        val handler = TestHandler(enterResult = EnterResult.Approved(wct, mock(), restorableState))
        controller.addHandler(handler)
        val enterTask = createTask(WINDOWING_MODE_MULTI_WINDOW)
        val enterRequest = request(enterTask, FULLSCREEN_MODE_REQUEST_ENTER)

        val resultWct = controller.handleRequest(Binder(), enterRequest)

        assertNotNull(resultWct)
        resultWct.assertSetFullscreenAllowMode(
            container = enterTask.token,
            mode = FullscreenRequestHandler.REQUEST_ALLOW_MODE_EXIT,
        )
    }

    @Test
    fun exitFullscreen_afterApprovedEnter_setsTaskInheritsAllowMode() {
        val wct = WindowContainerTransaction()
        val restorableState =
            EnterResult.Approved.RestorableState.Desktop(
                originalDeskId = 123,
                bounds = Rect(200, 200, 800, 800),
            )
        val handler =
            TestHandler(
                enterResult = EnterResult.Approved(wct, mock(), restorableState),
                exitResult = ExitResult.Approved(wct, mock()),
            )
        controller.addHandler(handler)

        val enterTask = createTask(WINDOWING_MODE_MULTI_WINDOW)
        val enterRequest = request(enterTask, FULLSCREEN_MODE_REQUEST_ENTER)
        // Enter fullscreen.
        controller.handleRequest(Binder(), enterRequest)

        val exitTask = enterTask.apply { setWindowingMode(WINDOWING_MODE_FULLSCREEN) }
        val exitRequest = request(exitTask, FULLSCREEN_MODE_REQUEST_EXIT)
        // Exit fullscreen.
        val resultWct = controller.handleRequest(Binder(), exitRequest)
        assertNotNull(resultWct)
        resultWct.assertSetFullscreenAllowMode(
            container = exitTask.token,
            mode = FullscreenRequestHandler.REQUEST_ALLOW_MODE_INHERIT,
        )
    }

    @Test
    fun enterExitFullscreen_startsAndStopsInvalidationMonitor() {
        val wct = WindowContainerTransaction()
        val restorableState =
            EnterResult.Approved.RestorableState.Desktop(
                originalDeskId = 123,
                bounds = Rect(200, 200, 800, 800),
            )
        val handler =
            TestHandler(
                enterResult = EnterResult.Approved(wct, mock(), restorableState),
                exitResult = ExitResult.Approved(wct, mock()),
            )
        controller.addHandler(handler)

        val enterTask = createTask(WINDOWING_MODE_MULTI_WINDOW)
        val enterRequest = request(enterTask, FULLSCREEN_MODE_REQUEST_ENTER)
        // Enter fullscreen.
        controller.handleRequest(Binder(), enterRequest)
        val monitor = invalidationMonitorSupplier.getMonitor(enterTask.taskId)
        assertNotNull(monitor)
        assertThat(monitor.started).isTrue()

        val exitTask = enterTask.apply { setWindowingMode(WINDOWING_MODE_FULLSCREEN) }
        val exitRequest = request(exitTask, FULLSCREEN_MODE_REQUEST_EXIT)
        // Exit fullscreen.
        controller.handleRequest(Binder(), exitRequest)
        assertNotNull(monitor)
        assertThat(monitor.stopped).isTrue()
    }

    private fun createTask(windowingMode: Int): RunningTaskInfo {
        return TestRunningTaskInfoBuilder().setWindowingMode(windowingMode).build()
    }

    private fun RunningTaskInfo.setWindowingMode(windowingMode: Int) {
        configuration.windowConfiguration.windowingMode = windowingMode
    }

    private fun request(
        task: RunningTaskInfo,
        @Activity.FullscreenModeRequest mode: Int,
        callback: IRemoteCallback? = null,
    ): TransitionRequestInfo {
        val fullscreenRequest = FullscreenRequestChange(mode, callback)
        return TransitionRequestInfo(
            WindowManager.TRANSIT_CHANGE,
            task,
            null,
            null,
            null,
            null,
            null,
            null,
            fullscreenRequest,
            0,
            0,
        )
    }

    private fun verifyRemoteCallbackResult(expectedResultCode: Int) {
        verify(remoteCallback).sendResult(bundleCaptor.capture())
        val bundle = bundleCaptor.value
        val resultCode = bundle.getInt(FullscreenRequestHandler.REMOTE_CALLBACK_RESULT_KEY)
        assertThat(resultCode).isEqualTo(expectedResultCode)
    }

    private fun WindowContainerTransaction.assertSetFullscreenAllowMode(
        container: WindowContainerToken,
        @FullscreenRequestHandler.RequestAllowMode mode: Int,
    ) {
        assertThat(
                changes.any { change ->
                    change.key == container.asBinder() &&
                        (change.value.changeMask and Change.CHANGE_FULLSCREEN_REQUEST_ALLOW_MODE !=
                            0) &&
                        change.value.fullscreenRequestAllowMode == mode
                }
            )
            .isTrue()
    }

    /** A test handler that can be configured to accept or reject future requests. */
    private class TestHandler(
        private val enterResult: EnterResult? = null,
        private val exitResult: ExitResult? = null,
    ) : ClientFullscreenRequestController.FullscreenRequestHandler {
        override val name: String = "TestHandler"

        var isEnterCalled = false
        var isExitCalled = false
        var receivedRestorableState: EnterResult.Approved.RestorableState? = null

        override fun handleEnterFullscreen(
            transition: IBinder,
            task: RunningTaskInfo,
        ): EnterResult? {
            isEnterCalled = true
            return enterResult
        }

        override fun handleExitFullscreen(
            transition: IBinder,
            task: RunningTaskInfo,
            restorableState: EnterResult.Approved.RestorableState?,
        ): ExitResult? {
            isExitCalled = true
            receivedRestorableState = restorableState
            return exitResult
        }

        companion object {
            /** A [TestHandler] that rejects all requests. */
            fun neverAccepts() = TestHandler(enterResult = null, exitResult = null)

            /** A [TestHandler] that accepts all requests. */
            fun acceptsRequest(): TestHandler {
                val wct = WindowContainerTransaction()
                val restorableState = EnterResult.Approved.RestorableState.Desktop(0, Rect())
                return TestHandler(
                    enterResult = EnterResult.Approved(wct, mock(), restorableState),
                    exitResult = ExitResult.Approved(wct, mock()),
                )
            }
        }
    }

    private class TestExitFullscreenInvalidationMonitorSupplier(
        private val transitions: Transitions,
        private val shellTaskOrganizer: ShellTaskOrganizer,
    ) : (Int) -> TestExitFullscreenInvalidationMonitor {
        private val taskToMonitorMap = mutableMapOf<Int, TestExitFullscreenInvalidationMonitor>()

        override fun invoke(taskId: Int): TestExitFullscreenInvalidationMonitor {
            return TestExitFullscreenInvalidationMonitor(taskId, transitions, shellTaskOrganizer)
                .also { monitor -> taskToMonitorMap[taskId] = monitor }
        }

        fun getMonitor(taskId: Int) = taskToMonitorMap[taskId]
    }

    private class TestExitFullscreenInvalidationMonitor(
        taskId: Int,
        transitions: Transitions,
        shellTaskOrganizer: ShellTaskOrganizer,
    ) : ExitFullscreenInvalidationMonitor(taskId, transitions, shellTaskOrganizer) {
        var started: Boolean = false
            private set

        var stopped: Boolean = false
            private set

        override fun start() {
            super.start()
            started = true
            stopped = false
        }

        override fun close(reason: String) {
            super.close(reason)
            started = false
            stopped = true
        }
    }
}
