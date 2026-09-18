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

import android.app.ActivityManager.RunningTaskInfo
import android.app.TaskWindowingLayerRequestHandler.REMOTE_CALLBACK_RESULT_KEY
import android.app.TaskWindowingLayerRequestHandler.RESULT_APPROVED
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource.LAYER_SWITCH
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Unit tests for [NormalAppLayerController].
 *
 * Build/Install/Run: atest WMShellUnitTests:NormalAppLayerControllerTest
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
@RunWith(AndroidJUnit4::class)
class NormalAppLayerControllerTest : ShellTestCase() {

    @Mock private lateinit var shellInit: ShellInit
    @Mock private lateinit var transitions: Transitions
    @Mock private lateinit var userRepositories: DesktopUserRepositories
    @Mock private lateinit var desktopTasksController: DesktopTasksController
    @Mock private lateinit var pinnedLayerController: PinnedLayerController
    @Mock private lateinit var desktopRepository: DesktopRepository
    @Mock private lateinit var callback: IRemoteCallback
    private lateinit var desktopState: FakeDesktopState

    private lateinit var normalAppLayerController: NormalAppLayerController

    @Before
    fun setup() {
        desktopState = FakeDesktopState()
        whenever(userRepositories.getProfile(USER_ID_0)).thenReturn(desktopRepository)
        normalAppLayerController = createController(desktopTasksController)
    }

    @Test
    fun moveTaskToNormalLayer_pinnedTaskAndActiveDesk_movesTaskToActiveDesk() {
        setupPinnedTask(TASK_ID_0, isPinned = true)
        setupDesktopModeSupportedOnDisplay(DISPLAY_ID_0, true)
        whenever(desktopRepository.isActiveTask(TASK_ID_0)).thenReturn(false)
        whenever(desktopRepository.getActiveDeskId(DISPLAY_ID_0)).thenReturn(DESK_ID_0)

        val wct =
            normalAppLayerController.moveTaskToNormalLayer(
                TRANSITION_TOKEN,
                makeTaskInfo(TASK_ID_0, DISPLAY_ID_0, USER_ID_0),
                callback,
            )

        assertNotNull(wct)
        verify(desktopTasksController)
            .moveTaskToDesk(
                eq(TASK_ID_0),
                eq(DESK_ID_0),
                eq(USER_ID_0),
                any(),
                eq(LAYER_SWITCH),
                isNull(),
                isNull(),
                eq(TRANSITION_TOKEN),
            )
        assertCallbackSuccess()
    }

    @Test
    fun moveTaskToNormalLayer_pinnedTaskAndNoActiveDesk_movesTaskToDefaultDesk() {
        setupPinnedTask(TASK_ID_0, isPinned = true)
        setupDesktopModeSupportedOnDisplay(displayId = DISPLAY_ID_0, isSupported = true)
        whenever(desktopRepository.getActiveDeskId(DISPLAY_ID_0)).thenReturn(null)

        val wct =
            normalAppLayerController.moveTaskToNormalLayer(
                TRANSITION_TOKEN,
                makeTaskInfo(TASK_ID_0, DISPLAY_ID_0, USER_ID_0),
                callback,
            )

        assertNotNull(wct)
        verify(desktopTasksController)
            .moveTaskToDefaultDeskAndActivate(
                eq(TASK_ID_0),
                any(),
                eq(LAYER_SWITCH),
                isNull(),
                isNull(),
                eq(TRANSITION_TOKEN),
            )
        assertCallbackSuccess()
    }

    @Test
    fun moveTaskToNormalLayer_taskNotPinned_doesNothing() {
        setupPinnedTask(TASK_ID_0, isPinned = false)
        setupDesktopModeSupportedOnDisplay(displayId = DISPLAY_ID_0, isSupported = true)
        whenever(pinnedLayerController.isPinned(TASK_ID_0)).thenReturn(false)

        val wct =
            normalAppLayerController.moveTaskToNormalLayer(
                TRANSITION_TOKEN,
                makeTaskInfo(TASK_ID_0, DISPLAY_ID_0, USER_ID_0),
                callback,
            )

        assertNotNull(wct)
        verifyNoInteractions(desktopTasksController)
        assertCallbackSuccess()
    }

    @Test
    fun moveTaskToNormalLayer_desktopModeNotSupportedOnDisplay_doesNothing() {
        setupPinnedTask(TASK_ID_0, isPinned = true)
        setupDesktopModeSupportedOnDisplay(displayId = DISPLAY_ID_0, isSupported = false)

        normalAppLayerController.moveTaskToNormalLayer(
            TRANSITION_TOKEN,
            makeTaskInfo(TASK_ID_0, DISPLAY_ID_0, USER_ID_0),
            callback,
        )

        verifyNoInteractions(desktopTasksController)
        assertCallbackSuccess()
    }

    @Test
    fun moveTaskToNormalLayer_desktopModeNotSupportedOnDevice_doesNothing() {
        normalAppLayerController = createController(desktopTasksController = null)
        setupPinnedTask(TASK_ID_0, isPinned = true)

        val wct =
            normalAppLayerController.moveTaskToNormalLayer(
                TRANSITION_TOKEN,
                makeTaskInfo(TASK_ID_0, DISPLAY_ID_0, USER_ID_0),
                callback,
            )

        assertNotNull(wct)
        verifyNoInteractions(desktopTasksController)
        assertCallbackSuccess()
    }

    @Test
    fun moveTaskToNormalLayer_taskAlreadyOnDesk_returnsNull() {
        setupPinnedTask(TASK_ID_0, isPinned = true)
        setupDesktopModeSupportedOnDisplay(displayId = DISPLAY_ID_0, isSupported = true)
        whenever(desktopRepository.isActiveTask(TASK_ID_0)).thenReturn(true)

        val wct =
            normalAppLayerController.moveTaskToNormalLayer(
                TRANSITION_TOKEN,
                makeTaskInfo(TASK_ID_0, DISPLAY_ID_0, USER_ID_0),
                callback,
            )

        assertNotNull(wct)
        assertTrue(wct.isEmpty)
        verifyNoInteractions(desktopTasksController)
        assertCallbackSuccess()
    }

    private fun assertCallbackSuccess() {
        // Mock the system state where the task is not on pinned layer anymore.
        setupPinnedTask(TASK_ID_0, isPinned = false)
        // Complete the transition to trigger the callback.
        normalAppLayerController.onTransitionReady(TRANSITION_TOKEN, mock(), mock(), mock())
        val bundleCaptor = argumentCaptor<Bundle>()
        verify(callback).sendResult(bundleCaptor.capture())
        val resultBundle = bundleCaptor.firstValue
        assertEquals(RESULT_APPROVED, resultBundle.getInt(REMOTE_CALLBACK_RESULT_KEY))
    }

    private fun createController(
        desktopTasksController: DesktopTasksController?
    ): NormalAppLayerController {
        return NormalAppLayerController(
            shellInit,
            transitions,
            userRepositories,
            desktopTasksController,
            pinnedLayerController,
            desktopState,
        )
    }

    private fun setupPinnedTask(taskId: Int, isPinned: Boolean) {
        whenever(pinnedLayerController.isPinned(taskId)).thenReturn(isPinned)
    }

    private fun setupDesktopModeSupportedOnDisplay(displayId: Int, isSupported: Boolean) {
        desktopState.overrideDesktopModeSupportPerDisplay[displayId] = isSupported
    }

    private fun makeTaskInfo(taskId: Int, displayId: Int, userId: Int): RunningTaskInfo {
        return RunningTaskInfo().apply {
            this.taskId = taskId
            this.displayId = displayId
            this.userId = userId
        }
    }

    private companion object {
        private const val TASK_ID_0 = 0
        private const val DISPLAY_ID_0 = 0
        private const val USER_ID_0 = 0
        private const val DESK_ID_0 = 0
        private val TRANSITION_TOKEN: IBinder = MockToken.token().asBinder()
    }
}
