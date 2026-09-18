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

package com.android.wm.shell.desktopai.core

import android.platform.test.annotations.EnableFlags
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.desktopai.api.ITriggerSource
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.TriggerEventType
import com.android.wm.shell.desktopai.api.config.TriggerStrategy
import com.android.wm.shell.sysui.ShellInit
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock

/** Tests for [TriggerManager]. Usage: atest WMShellUnitTests:TriggerManagerTest */
@SmallTest
@RunWith(JUnit4::class)
@EnableFlags(Flags.FLAG_DESKTOP_AI_PLATFORM)
class TriggerManagerTest : ShellTestCase() {

    private val shellExecutor = TestShellExecutor()
    private lateinit var mockSource: ITriggerSource

    private lateinit var triggerManager: TriggerManager
    private lateinit var sourceCallback: (TriggerEvent) -> Unit
    private lateinit var shellInit: ShellInit

    @Before
    fun setUp() {
        shellInit = ShellInit(shellExecutor)
        mockSource = mock<ITriggerSource>()

        triggerManager = TriggerManager(shellInit, listOf(mockSource))
        shellInit.init()

        val triggerEventCaptor = argumentCaptor<(TriggerEvent) -> Unit>()

        verify(mockSource).start(triggerEventCaptor.capture())
        sourceCallback = triggerEventCaptor.lastValue
    }

    @Test
    fun testRegisterTrigger_callbackInvokedOnMatchingEvent() {
        val strategy = TriggerStrategy.SystemEvent("TEST_EVENT")
        val callback: (TriggerEvent) -> Unit = mock()
        triggerManager.registerTrigger(strategy, callback)

        val event = TriggerEvent(TriggerEventType.SYSTEM, "TEST_EVENT")
        sourceCallback.invoke(event)

        verify(callback).invoke(event)
    }

    @Test
    fun testRegisterTrigger_callbackNotInvokedOnNonMatchingEvent() {
        val strategy = TriggerStrategy.SystemEvent("TEST_EVENT")
        val callback: (TriggerEvent) -> Unit = mock()
        triggerManager.registerTrigger(strategy, callback)

        val event = TriggerEvent(TriggerEventType.SYSTEM, "OTHER_EVENT")
        sourceCallback.invoke(event)

        verify(callback, never()).invoke(any())
    }

    @Test
    fun testRegisterTrigger_multipleCallbacksInvokedOnMatchingEvent() {
        val strategy = TriggerStrategy.SystemEvent("TEST_EVENT")
        val callback1: (TriggerEvent) -> Unit = mock()
        val callback2: (TriggerEvent) -> Unit = mock()
        triggerManager.registerTrigger(strategy, callback1)
        triggerManager.registerTrigger(strategy, callback2)

        val event = TriggerEvent(TriggerEventType.SYSTEM, "TEST_EVENT")
        sourceCallback.invoke(event)

        verify(callback1).invoke(event)
        verify(callback2).invoke(event)
    }

    @Test
    fun testRegisterTrigger_differentStrategies_correctDispatch() {
        val strategy1 = TriggerStrategy.SystemEvent("EVENT_1")
        val strategy2 = TriggerStrategy.SystemEvent("EVENT_2")
        val callback1: (TriggerEvent) -> Unit = mock()
        val callback2: (TriggerEvent) -> Unit = mock()
        triggerManager.registerTrigger(strategy1, callback1)
        triggerManager.registerTrigger(strategy2, callback2)

        val event1 = TriggerEvent(TriggerEventType.SYSTEM, "EVENT_1")
        sourceCallback.invoke(event1)

        verify(callback1).invoke(event1)
        verify(callback2, never()).invoke(any())
    }
}
