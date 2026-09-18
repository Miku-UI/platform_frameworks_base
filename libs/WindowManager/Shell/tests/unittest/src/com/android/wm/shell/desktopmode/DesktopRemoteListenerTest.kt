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

package com.android.wm.shell.desktopmode

import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SingleInstanceRemoteListener
import com.android.wm.shell.common.transition.TransitionStateHolder
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.data.DesktopRepository.DeskChangeListener
import com.android.wm.shell.desktopmode.data.DesktopRepository.VisibleTasksListener
import com.android.wm.shell.sysui.ShellController
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [DesktopRemoteListener]
 *
 * Usage: `atest WMShellUnitTests:DesktopRemoteListenerTest`
 */
@SmallTest
class DesktopRemoteListenerTest : ShellTestCase() {

    private val shellExecutor = mock<ShellExecutor>()
    private val currentRepository = mock<DesktopRepository>()
    private val userRepositories = mock<DesktopUserRepositories>()
    private val shellController = mock<ShellController>()
    private val transitionStateHolder = mock<TransitionStateHolder>()
    private val remoteListener =
        mock<SingleInstanceRemoteListener<DesktopTasksController, IDesktopTaskListener>>()
    private val desktopTaskListener = mock<IDesktopTaskListener>()
    private val listener =
        DesktopRemoteListener(
            shellExecutor,
            userRepositories,
            shellController,
            transitionStateHolder,
        )

    private val deskChangeListenerCaptor = argumentCaptor<DeskChangeListener>()
    private val visibleTasksListenerCaptor = argumentCaptor<VisibleTasksListener>()

    @Before
    fun setUp() {
        whenever(userRepositories.current).thenReturn(currentRepository)
        // Set up the remote listener to execute the callback with the mock IDesktopTaskListener
        whenever(remoteListener.call(any())).thenAnswer {
            val callback =
                it.getArgument(0) as SingleInstanceRemoteListener.RemoteCall<IDesktopTaskListener>
            callback.accept(desktopTaskListener)
        }
    }

    @Test
    fun register_addsListeners() {
        listener.register(remoteListener)
        // Verify listeners were added
        verify(currentRepository).addDeskChangeListener(any(), any())
        verify(currentRepository).addVisibleTasksListener(any(), any())
    }

    @Test
    fun deskChangeListener_onDeskAdded_notifiesRemote() {
        listener.register(remoteListener)
        verify(currentRepository).addDeskChangeListener(deskChangeListenerCaptor.capture(), any())
        deskChangeListenerCaptor.firstValue.onDeskAdded(1, 2)
        verify(desktopTaskListener).onDeskAdded(1, 2)
    }

    @Test
    fun deskChangeListener_onDeskRemoved_notifiesRemote() {
        listener.register(remoteListener)
        verify(currentRepository).addDeskChangeListener(deskChangeListenerCaptor.capture(), any())
        deskChangeListenerCaptor.firstValue.onDeskRemoved(1, 2)
        verify(desktopTaskListener).onDeskRemoved(1, 2)
    }

    @Test
    fun deskChangeListener_onActiveDeskChanged_notifiesRemote() {
        listener.register(remoteListener)
        verify(currentRepository).addDeskChangeListener(deskChangeListenerCaptor.capture(), any())
        deskChangeListenerCaptor.firstValue.onActiveDeskChanged(1, 2, 3)
        verify(desktopTaskListener).onActiveDeskChanged(1, 2, 3)
    }

    @Test
    fun deskChangeListener_onCanCreateDesksChanged_notifiesRemote() {
        listener.register(remoteListener)
        verify(currentRepository).addDeskChangeListener(deskChangeListenerCaptor.capture(), any())
        deskChangeListenerCaptor.firstValue.onCanCreateDesksChanged(true)
        verify(desktopTaskListener).onCanCreateDesksChanged(true)
    }

    @Test
    fun deskChangeListener_onTaskAppearingInDesk_overviewVisible_notifiesRemote() {
        whenever(shellController.isOverviewVisible(any())).thenReturn(true)
        whenever(transitionStateHolder.isRecentsTransitionRunning()).thenReturn(false)
        listener.register(remoteListener)
        verify(currentRepository).addDeskChangeListener(deskChangeListenerCaptor.capture(), any())
        deskChangeListenerCaptor.firstValue.onTaskAppearingInDesk(1, 2, 3)
        verify(desktopTaskListener).onTaskAppearingInDeskWithOverviewShowing(1, 2, 3)
    }

    @Test
    fun deskChangeListener_onTaskAppearingInDesk_overviewNotVisible_doesNotNotifyRemote() {
        whenever(shellController.isOverviewVisible(any())).thenReturn(false)
        listener.register(remoteListener)
        verify(currentRepository).addDeskChangeListener(deskChangeListenerCaptor.capture(), any())
        deskChangeListenerCaptor.firstValue.onTaskAppearingInDesk(1, 2, 3)
        verify(desktopTaskListener, never())
            .onTaskAppearingInDeskWithOverviewShowing(any(), any(), any())
    }

    @Test
    fun visibleTasksListener_onTasksVisibilityChanged_notifiesRemote() {
        listener.register(remoteListener)
        verify(currentRepository)
            .addVisibleTasksListener(visibleTasksListenerCaptor.capture(), any())
        visibleTasksListenerCaptor.firstValue.onTasksVisibilityChanged(1, 5)
        verify(desktopTaskListener).onTasksVisibilityChanged(1, 5)
    }

    @Test
    fun unregister_afterRegister_stopsNotifications() {
        listener.register(remoteListener)
        verify(currentRepository).addDeskChangeListener(deskChangeListenerCaptor.capture(), any())
        verify(currentRepository)
            .addVisibleTasksListener(visibleTasksListenerCaptor.capture(), any())

        listener.unregister()

        // Simulate events after unregister
        deskChangeListenerCaptor.firstValue.onDeskAdded(1, 2)
        visibleTasksListenerCaptor.firstValue.onTasksVisibilityChanged(1, 5)

        verify(desktopTaskListener, never()).onDeskAdded(any(), any())
        verify(desktopTaskListener, never()).onTasksVisibilityChanged(any(), any())
    }

    @Test
    fun testOnEnterDesktopModeTransitionStarted() {
        listener.register(remoteListener)
        val transitionDuration = 100
        listener.onEnterDesktopModeTransitionStarted(transitionDuration)
        verify(desktopTaskListener).onEnterDesktopModeTransitionStarted(transitionDuration)
    }

    @Test
    fun testOnExitDesktopModeTransitionStarted() {
        listener.register(remoteListener)
        val transitionDuration = 200
        val shouldEndUpAtHome = true
        listener.onExitDesktopModeTransitionStarted(transitionDuration, shouldEndUpAtHome)
        verify(desktopTaskListener)
            .onExitDesktopModeTransitionStarted(transitionDuration, shouldEndUpAtHome)
    }

    @Test
    fun onTaskbarCornerRoundingUpdate_callsRemoteListener() {
        listener.register(remoteListener)
        listener.onTaskbarCornerRoundingUpdate(
            hasTasksRequiringTaskbarRounding = true,
            displayId = 2,
        )
        verify(desktopTaskListener)
            .onTaskbarCornerRoundingUpdate(
                /* hasTasksRequiringTaskbarRounding= */ true,
                /* displayId= */ 2,
            )
    }

    @Test
    fun unregister_directCallbacksCannotBeCalled() {
        listener.register(remoteListener)
        listener.unregister()
        // After stopping, the remote listener should not be called.
        val transitionDuration = 100
        val shouldEndUpAtHome = true

        listener.onEnterDesktopModeTransitionStarted(transitionDuration)
        listener.onExitDesktopModeTransitionStarted(transitionDuration, shouldEndUpAtHome)
        listener.onTaskbarCornerRoundingUpdate(
            hasTasksRequiringTaskbarRounding = true,
            displayId = 2,
        )

        verify(desktopTaskListener, never()).onEnterDesktopModeTransitionStarted(any())
        verify(desktopTaskListener, never()).onExitDesktopModeTransitionStarted(any(), any())
        verify(desktopTaskListener, never()).onTaskbarCornerRoundingUpdate(any(), any())
    }

    @Test
    fun unregister_removesListeners() {
        listener.register(remoteListener)

        // Now capture listeners
        verify(currentRepository).addDeskChangeListener(deskChangeListenerCaptor.capture(), any())
        verify(currentRepository)
            .addVisibleTasksListener(visibleTasksListenerCaptor.capture(), any())

        listener.unregister()
        verify(currentRepository).removeDeskChangeListener(deskChangeListenerCaptor.firstValue)
        verify(currentRepository).removeVisibleTasksListener(visibleTasksListenerCaptor.firstValue)
    }

    @Test
    fun unregister_beforeRegister_doesNotRemoveDeskChangeListener() {
        // Create a new instance to ensure register() is not called on the listener member
        val newListener =
            DesktopRemoteListener(
                shellExecutor,
                userRepositories,
                shellController,
                transitionStateHolder,
            )
        newListener.unregister()
        // Verify removeDeskChangeListener is never called as it was not initialized
        verify(currentRepository, never()).removeDeskChangeListener(any())
        // Verify removeVisibleTasksListener is still called as it is not guarded
        verify(currentRepository).removeVisibleTasksListener(any())
    }
}
