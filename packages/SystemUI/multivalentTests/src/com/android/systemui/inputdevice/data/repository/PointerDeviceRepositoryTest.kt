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

package com.android.systemui.inputdevice.data.repository

import android.hardware.input.InputManager.InputDeviceListener
import android.hardware.input.fakeInputManager
import android.os.fakeHandler
import android.testing.TestableLooper
import android.view.InputDevice
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Captor
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@TestableLooper.RunWithLooper
@RunWith(ParameterizedAndroidJunit4::class)
class PointerDeviceRepositoryTest(private val pointer: Int) : SysuiTestCase() {

    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Captor private lateinit var deviceListenerCaptor: ArgumentCaptor<InputDeviceListener>
    private val kosmos = testKosmos()
    private val fakeInputManager = kosmos.fakeInputManager

    private val dispatcher: CoroutineDispatcher = kosmos.testDispatcher
    private val testScope = kosmos.testScope
    private val inputDeviceRepo =
        InputDeviceRepository(
            kosmos.fakeHandler,
            testScope.backgroundScope,
            fakeInputManager.inputManager,
        )
    private val underTest =
        PointerDeviceRepositoryImpl(dispatcher, fakeInputManager.inputManager, inputDeviceRepo)

    @Test
    fun emitsDisconnected_ifNothingIsConnected() =
        kosmos.runTest {
            val initialState = underTest.isAnyPointerDeviceConnected.first()
            assertThat(initialState).isFalse()
        }

    @Test
    fun emitsConnected_ifPointerAlreadyConnectedAtTheStart() =
        kosmos.runTest {
            fakeInputManager.addDevice(POINTER_ID, pointer)
            val initialValue = underTest.isAnyPointerDeviceConnected.first()
            assertThat(initialValue).isTrue()
        }

    @Test
    fun emitsConnected_whenNewPointerConnects() =
        kosmos.runTest {
            captureDeviceListener()
            val isPointerConnected by collectLastValue(underTest.isAnyPointerDeviceConnected)

            fakeInputManager.addDevice(POINTER_ID, pointer)

            assertThat(isPointerConnected).isTrue()
        }

    @Test
    fun emitsDisconnected_whenDeviceWithIdDoesNotExist() =
        testScope.runTest {
            captureDeviceListener()
            val isPointerConnected by collectLastValue(underTest.isAnyPointerDeviceConnected)
            whenever(fakeInputManager.inputManager.getInputDevice(eq(NULL_DEVICE_ID)))
                .thenReturn(null)
            fakeInputManager.addDevice(NULL_DEVICE_ID, InputDevice.SOURCE_UNKNOWN)
            assertThat(isPointerConnected).isFalse()
        }

    @Test
    fun emitsDisconnected_whenPointerDisconnects() =
        testScope.runTest {
            captureDeviceListener()
            val isPointerConnected by collectLastValue(underTest.isAnyPointerDeviceConnected)

            fakeInputManager.addDevice(POINTER_ID, pointer)
            assertThat(isPointerConnected).isTrue()

            fakeInputManager.removeDevice(POINTER_ID)
            assertThat(isPointerConnected).isFalse()
        }

    private suspend fun captureDeviceListener() {
        underTest.isAnyPointerDeviceConnected.first()
        Mockito.verify(fakeInputManager.inputManager)
            .registerInputDeviceListener(deviceListenerCaptor.capture(), anyOrNull())
        fakeInputManager.registerInputDeviceListener(deviceListenerCaptor.value)
    }

    private companion object {
        private const val POINTER_ID = 1
        private const val NULL_DEVICE_ID = 4

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<Int> {
            return listOf(InputDevice.SOURCE_TOUCHPAD, InputDevice.SOURCE_MOUSE)
        }
    }
}
