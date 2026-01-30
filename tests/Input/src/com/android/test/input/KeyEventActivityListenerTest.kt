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
package com.android.test.input

import android.Manifest
import android.hardware.input.InputManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.EvdevInputEventCodes
import com.android.cts.input.UinputKeyboard
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

@RequiresFlagsEnabled(com.android.hardware.input.Flags.FLAG_KEY_EVENT_ACTIVITY_DETECTION)
class KeyEventActivityListenerTest {
    private lateinit var inputManager: InputManager
    private lateinit var listener: InputManager.KeyEventActivityListener
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val rule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)

    @get:Rule
    val adoptShellPermissionsRule =
        AdoptShellPermissionsRule(
            instrumentation.getUiAutomation(),
            Manifest.permission.LISTEN_FOR_KEY_ACTIVITY,
        )

    @Before
    fun setUp() {
        lateinit var activity: CaptureEventActivity
        rule.getScenario().onActivity {
            inputManager = it.getSystemService(InputManager::class.java)
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
        listener = mock(InputManager.KeyEventActivityListener::class.java)
    }

    @Test
    fun testKeyActivityListener() {
        val isRegistered = inputManager.registerKeyEventActivityListener(listener)
        assertTrue(isRegistered)
        val latch = CountDownLatch(1)
        doAnswer {
                latch.countDown()
                null
            }
            .`when`(listener)
            .onKeyEventActivity()
        UinputKeyboard(instrumentation).use { keyboardDevice ->
            keyboardDevice.injectKeyDown(EvdevInputEventCodes.KEY_A)
            keyboardDevice.injectKeyUp(EvdevInputEventCodes.KEY_A)
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            verify(listener, times(1)).onKeyEventActivity()
            val isUnregistered = inputManager.unregisterKeyEventActivityListener(listener)
            assertTrue(isUnregistered)
            keyboardDevice.injectKeyDown(EvdevInputEventCodes.KEY_A)
            keyboardDevice.injectKeyUp(EvdevInputEventCodes.KEY_A)
            verifyNoMoreInteractions(listener)
        }
    }
}
