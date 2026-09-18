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

package com.android.systemui.motioncues

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.app.motioncues.MotionCuesSettings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.CommandQueue
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class MotionCuesManagerTest : SysuiTestCase() {

    private lateinit var context: Context
    private lateinit var commandQueue: CommandQueue
    private lateinit var motionCuesUi: MotionCuesUi
    private lateinit var userTracker: UserTracker
    private val mainExecutor: Executor = MoreExecutors.directExecutor()

    private lateinit var serviceConnectionCaptor: KArgumentCaptor<ServiceConnection>
    private lateinit var userTrackerCallbackCaptor: KArgumentCaptor<UserTracker.Callback>

    private lateinit var manager: MotionCuesManager

    private val componentName = ComponentName("com.example", "com.example.Service")
    private val userId = 1
    private val settings = MotionCuesSettings.Builder().build()

    @Before
    fun setUp() {
        context = mock<Context>()
        commandQueue = mock<CommandQueue>()
        motionCuesUi = mock<MotionCuesUi>()
        userTracker = mock<UserTracker>()

        serviceConnectionCaptor = argumentCaptor<ServiceConnection>()
        userTrackerCallbackCaptor = argumentCaptor<UserTracker.Callback>()

        manager = MotionCuesManager(context, commandQueue, motionCuesUi, userTracker, mainExecutor)
    }

    @Test
    fun testStart() {
        manager.start()
        verify(commandQueue).addCallback(eq(manager))
        verify(userTracker).addCallback(eq(manager), any<Executor>())
    }

    @Test
    fun startMotionCuesSession_whenAlreadyStarted_logsWarning() {
        whenever(motionCuesUi.isStarted).thenReturn(true)

        manager.startMotionCuesSession(componentName, userId, settings)

        verify(context, never()).bindService(any<Intent>(), any<ServiceConnection>(), any<Int>())
    }

    @Test
    fun startMotionCuesSession_bindServiceFails_endsSession() {
        whenever(context.bindService(any<Intent>(), any<ServiceConnection>(), any<Int>())).thenReturn(false)

        manager.startMotionCuesSession(componentName, userId, settings)

        verify(motionCuesUi).stop()
    }

    @Test
    fun startMotionCuesSession_bindServiceSucceeds_startsUi() {
        whenever(motionCuesUi.isStarted).thenReturn(false)
        whenever(context.bindService(any<Intent>(), serviceConnectionCaptor.capture(), eq(Context.BIND_AUTO_CREATE))).thenReturn(true)

        manager.startMotionCuesSession(componentName, userId, settings)

        verify(motionCuesUi).start(eq(settings), eq(userId), eq(componentName.packageName))
    }

    @Test
    fun endMotionCuesSession_unbindsAndStopsUi() {
        whenever(motionCuesUi.isStarted).thenReturn(false)
        whenever(context.bindService(any<Intent>(), serviceConnectionCaptor.capture(), eq(Context.BIND_AUTO_CREATE))).thenReturn(true)
        manager.startMotionCuesSession(componentName, userId, settings)

        manager.endMotionCuesSession()

        verify(context).unbindService(eq(serviceConnectionCaptor.lastValue))
        verify(motionCuesUi).stop()
    }

    @Test
    fun onServiceDisconnected_resetsSession() {
        whenever(context.bindService(any<Intent>(), serviceConnectionCaptor.capture(), any<Int>())).thenReturn(true)
        manager.startMotionCuesSession(componentName, userId, settings)

        serviceConnectionCaptor.lastValue.onServiceDisconnected(componentName)

        verify(motionCuesUi).stop()
    }

    @Test
    fun onBindingDied_endsSession() {
        whenever(context.bindService(any<Intent>(), serviceConnectionCaptor.capture(), any<Int>())).thenReturn(true)
        manager.startMotionCuesSession(componentName, userId, settings)

        serviceConnectionCaptor.lastValue.onBindingDied(componentName)

        verify(context).unbindService(eq(serviceConnectionCaptor.lastValue))
        verify(motionCuesUi).stop()
    }

    @Test
    fun onNullBinding_endsSession() {
        whenever(context.bindService(any<Intent>(), serviceConnectionCaptor.capture(), any<Int>())).thenReturn(true)
        manager.startMotionCuesSession(componentName, userId, settings)

        serviceConnectionCaptor.lastValue.onNullBinding(componentName)

        verify(context).unbindService(eq(serviceConnectionCaptor.lastValue))
        verify(motionCuesUi).stop()
    }

    @Test
    fun onUserChanged_endsSession() {
        manager.start()
        verify(userTracker).addCallback(userTrackerCallbackCaptor.capture(), any<Executor>())

        userTrackerCallbackCaptor.lastValue.onUserChanged(99, context)

        verify(motionCuesUi).stop()
    }
}
