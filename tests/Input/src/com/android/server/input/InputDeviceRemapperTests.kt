/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.server.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Looper
import android.os.TestLooperManager
import android.os.UserHandle
import android.os.UserManager
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.Presubmit
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.TestableContext
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.input.data.TestDataStore
import com.android.server.testutils.TestUtils
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.anEmptyMap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verifyNoInteractions

/**
 * Tests for {@link InputDeviceRemapper}.
 *
 * Build/Install/Run: atest InputTests:InputDeviceRemapperTests
 */
@Presubmit
@RunWith(MockitoJUnitRunner::class)
@EnableFlags(com.android.hardware.input.Flags.FLAG_CONTROLLER_REMAPPING)
class InputDeviceRemapperTests {

    @JvmField
    @Rule
    val testableContext = TestableContext(ApplicationProvider.getApplicationContext())

    @JvmField @Rule val rule = SetFlagsRule()

    @Mock private lateinit var native: NativeInputManagerService
    @Mock private lateinit var inputManager: InputManager
    @Mock private lateinit var userManager: UserManager

    private lateinit var mainLooperManager: TestLooperManager
    private lateinit var testDataStore: TestDataStore
    private var inputDeviceIds = mutableListOf<Int>()

    @Before
    fun setup() {
        testableContext.addMockSystemService(Context.INPUT_SERVICE, inputManager)
        testableContext.addMockSystemService(Context.USER_SERVICE, userManager)
        whenever(inputManager.inputDeviceIds).thenAnswer { inputDeviceIds.toIntArray() }
        whenever(userManager.getUserHandles(any()))
            .thenReturn(listOf(UserHandle(USER_ID), UserHandle(SECOND_USER_ID)))
        mainLooperManager =
            InstrumentationRegistry.getInstrumentation()
                .acquireLooperManager(Looper.getMainLooper())
        testDataStore = TestDataStore()
    }

    @After
    fun tearDown() {
        mainLooperManager.release()
    }

    private fun setupControllerRemapper(userId: Int = USER_ID): InputDeviceRemapper {
        val inputDeviceRemapper =
            InputDeviceRemapper(
                testableContext,
                native,
                Looper.getMainLooper(),
                Looper.getMainLooper(),
                testDataStore.getDataStore(),
            )
        inputDeviceRemapper.systemRunning()
        inputDeviceRemapper.setCurrentUserId(userId)
        TestUtils.flushLoopers(mainLooperManager)
        return inputDeviceRemapper
    }

    @Test
    fun testRemapKey_appliesToCorrectDevice() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_KEYBOARD,
            )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.remapKey(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )

        verify(native)
            .setKeyRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_1)),
                eq(intArrayOf(KeyEvent.KEYCODE_2)),
            )
        assertEquals(
            inputDeviceRemapper.getKeyRemappings(USER_ID, device.identifier),
            mapOf(KeyEvent.KEYCODE_1 to KeyEvent.KEYCODE_2),
        )
    }

    @Test
    fun testRemapKey_doesNotApplyToMouse() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_MOUSE,
            )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.remapKey(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )

        verify(native, never()).setKeyRemappingForDevice(eq(deviceId), any(), any())
    }

    @Test
    fun testRemoveKeyRemapping() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_KEYBOARD,
            )
        inputDeviceRemapper.remapKey(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.removeKeyRemapping(USER_ID, device.identifier, KeyEvent.KEYCODE_1)

        verify(native).setKeyRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
        assertEquals(
            inputDeviceRemapper.getKeyRemappings(USER_ID, device.identifier),
            mapOf<Int, Int>(),
        )
    }

    @Test
    fun testClearAllKeyRemappings() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_KEYBOARD,
            )
        inputDeviceRemapper.remapKey(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.clearAllKeyRemappings(USER_ID, device.identifier)

        verify(native).setKeyRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
        assertEquals(
            inputDeviceRemapper.getKeyRemappings(USER_ID, device.identifier),
            mapOf<Int, Int>(),
        )
    }

    @Test
    fun testDeviceRemoved_removesKeyRemapping() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_KEYBOARD,
            )
        inputDeviceRemapper.remapKey(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        removeInputDevice(deviceId, device.descriptor)
        inputDeviceRemapper.onInputDeviceRemoved(deviceId)

        verify(native).setKeyRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
    }

    @Test
    fun testSetCurrentUser_appliesKeyRemapping() {
        val inputDeviceRemapper = setupControllerRemapper(userId = USER_ID)
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_KEYBOARD,
            )
        inputDeviceRemapper.remapKey(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        // Switch to a different user with no remapping
        inputDeviceRemapper.setCurrentUserId(SECOND_USER_ID)
        TestUtils.flushLoopers(mainLooperManager)
        verify(native).setKeyRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))

        // Switch back to the user with remapping
        inputDeviceRemapper.setCurrentUserId(USER_ID)
        TestUtils.flushLoopers(mainLooperManager)
        verify(native)
            .setKeyRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_1)),
                eq(intArrayOf(KeyEvent.KEYCODE_2)),
            )
    }

    @Test
    fun testKeyRemappingRestored_onServiceRecreation() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_KEYBOARD,
            )
        inputDeviceRemapper.remapKey(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )
        TestUtils.flushLoopers(mainLooperManager)

        val recreatedInputDeviceRemapper = setupControllerRemapper()
        addInputDevice(deviceId, device.descriptor, device)
        recreatedInputDeviceRemapper.onInputDeviceAdded(deviceId)

        verify(native)
            .setKeyRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_1)),
                eq(intArrayOf(KeyEvent.KEYCODE_2)),
            )
        assertEquals(
            recreatedInputDeviceRemapper.getKeyRemappings(USER_ID, device.identifier),
            mapOf(KeyEvent.KEYCODE_1 to KeyEvent.KEYCODE_2),
        )
    }

    @Test
    fun testRemapAxis_appliesToCorrectDevice() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )

        verify(native)
            .setAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(MotionEvent.AXIS_X)),
                eq(intArrayOf(MotionEvent.AXIS_Y)),
            )
        assertEquals(
            inputDeviceRemapper.getAxisRemappings(USER_ID, device.identifier),
            mapOf(MotionEvent.AXIS_X to MotionEvent.AXIS_Y),
        )
    }

    @Test
    fun testRemapAxis_toUnknown() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_DISABLED,
        )

        verify(native)
            .setAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(MotionEvent.AXIS_X)),
                eq(intArrayOf(MotionEvent.AXIS_DISABLED)),
            )
        assertEquals(
            inputDeviceRemapper.getAxisRemappings(USER_ID, device.identifier),
            mapOf(MotionEvent.AXIS_X to MotionEvent.AXIS_DISABLED),
        )
    }

    @Test
    fun testRemapAxis_doesNotApplyToKeyboard() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_KEYBOARD,
            )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )

        verify(native, never()).setAxisRemappingForDevice(eq(deviceId), any(), any())
    }

    @Test
    fun testRemoveAxisRemapping() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )
        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
        )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.removeAxisRemapping(USER_ID, device.identifier, MotionEvent.AXIS_X)

        verify(native)
            .setAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(MotionEvent.AXIS_Y)),
                eq(intArrayOf(MotionEvent.AXIS_Z)),
            )
        assertEquals(
            inputDeviceRemapper.getAxisRemappings(USER_ID, device.identifier),
            mapOf(MotionEvent.AXIS_Y to MotionEvent.AXIS_Z),
        )
    }

    @Test
    fun testClearAllAxisRemappings() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )
        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
        )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.clearAllAxisRemappings(USER_ID, device.identifier)

        verify(native).setAxisRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
        assertEquals(
            inputDeviceRemapper.getAxisRemappings(USER_ID, device.identifier),
            mapOf<Int, Int>(),
        )
    }

    @Test
    fun testDeviceRemoved_removesAxisRemappings() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        removeInputDevice(deviceId, device.descriptor)
        inputDeviceRemapper.onInputDeviceRemoved(deviceId)

        verify(native).setAxisRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
    }

    @Test
    fun testSetCurrentUser_appliesAxisRemappings() {
        val inputDeviceRemapper = setupControllerRemapper(userId = USER_ID)
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        // Switch to a different user with no remapping
        inputDeviceRemapper.setCurrentUserId(SECOND_USER_ID)
        TestUtils.flushLoopers(mainLooperManager)
        verify(native).setAxisRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))

        // Switch back to the user with remapping
        inputDeviceRemapper.setCurrentUserId(USER_ID)
        TestUtils.flushLoopers(mainLooperManager)
        verify(native)
            .setAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(MotionEvent.AXIS_X)),
                eq(intArrayOf(MotionEvent.AXIS_Y)),
            )
    }

    @Test
    fun testAxisRemappingRestored_onServiceRecreation() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )
        TestUtils.flushLoopers(mainLooperManager)

        val recreatedInputDeviceRemapper = setupControllerRemapper()
        addInputDevice(deviceId, device.descriptor, device)
        recreatedInputDeviceRemapper.onInputDeviceAdded(deviceId)

        verify(native)
            .setAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(MotionEvent.AXIS_X)),
                eq(intArrayOf(MotionEvent.AXIS_Y)),
            )
        assertEquals(
            recreatedInputDeviceRemapper.getAxisRemappings(USER_ID, device.identifier),
            mapOf(MotionEvent.AXIS_X to MotionEvent.AXIS_Y),
        )
    }

    @Test
    fun remapKeyToAxis_appliesToCorrectDevice() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_KEYBOARD,
            )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.remapKeyToAxis(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_A,
            MotionEvent.AXIS_X,
        )

        verify(native)
            .setKeyToAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_BUTTON_A)),
                eq(intArrayOf(MotionEvent.AXIS_X)),
            )
    }

    @Test
    fun remapKeyToAxis_doesNotApplyToKeyboardOnly() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_KEYBOARD,
            )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.remapKeyToAxis(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_A,
            MotionEvent.AXIS_X,
        )

        verify(native, never()).setKeyToAxisRemappingForDevice(eq(deviceId), any(), any())
    }

    @Test
    fun remapKeyToAxis_doesNotApplyToJoystickOnly() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.remapKeyToAxis(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_A,
            MotionEvent.AXIS_X,
        )

        verify(native, never()).setKeyToAxisRemappingForDevice(eq(deviceId), any(), any())
    }

    @Test
    fun removeKeyToAxisRemapping_removesOnlySpecifiedKey() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_KEYBOARD,
            )
        inputDeviceRemapper.remapKeyToAxis(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_A,
            MotionEvent.AXIS_X,
        )
        inputDeviceRemapper.remapKeyToAxis(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_B,
            MotionEvent.AXIS_Y,
        )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.removeKeyToAxisRemapping(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_A,
        )

        verify(native)
            .setKeyToAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_BUTTON_B)),
                eq(intArrayOf(MotionEvent.AXIS_Y)),
            )
    }

    @Test
    fun clearAllKeyToAxisRemappings_removesAll() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_KEYBOARD,
            )
        inputDeviceRemapper.remapKeyToAxis(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_A,
            MotionEvent.AXIS_X,
        )
        inputDeviceRemapper.remapKeyToAxis(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_B,
            MotionEvent.AXIS_Y,
        )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        inputDeviceRemapper.clearAllKeyToAxisRemappings(USER_ID, device.identifier)

        verify(native)
            .setKeyToAxisRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
    }

    @Test
    fun testDeviceRemoved_removesKeyToAxisRemappings() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_KEYBOARD,
            )
        inputDeviceRemapper.remapKeyToAxis(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_A,
            MotionEvent.AXIS_X,
        )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        removeInputDevice(deviceId, device.descriptor)
        inputDeviceRemapper.onInputDeviceRemoved(deviceId)

        verify(native)
            .setKeyToAxisRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
    }

    @Test
    fun setCurrentUser_appliesAllRemappings() {
        val inputDeviceRemapper = setupControllerRemapper(userId = USER_ID)
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_KEYBOARD,
            )
        inputDeviceRemapper.remapKey(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
        )
        inputDeviceRemapper.remapKeyToAxis(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_A,
            MotionEvent.AXIS_X,
        )
        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
        )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        // Switch to a different user with no remapping
        inputDeviceRemapper.setCurrentUserId(SECOND_USER_ID)
        TestUtils.flushLoopers(mainLooperManager)
        verify(native).setKeyRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
        verify(native)
            .setKeyToAxisRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
        verify(native)
            .setAxisRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))

        // Switch back to the user with remapping
        inputDeviceRemapper.setCurrentUserId(USER_ID)
        TestUtils.flushLoopers(mainLooperManager)
        verify(native)
            .setKeyRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_BUTTON_X)),
                eq(intArrayOf(KeyEvent.KEYCODE_BUTTON_Y)),
            )
        verify(native)
            .setKeyToAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_BUTTON_A)),
                eq(intArrayOf(MotionEvent.AXIS_X)),
            )
        verify(native)
            .setAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(MotionEvent.AXIS_LTRIGGER)),
                eq(intArrayOf(MotionEvent.AXIS_RTRIGGER)),
            )
    }

    @Test
    fun onServiceRecreation_appliesAllRemappings() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_KEYBOARD,
            )
        inputDeviceRemapper.remapKey(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
        )
        inputDeviceRemapper.remapKeyToAxis(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_A,
            MotionEvent.AXIS_Z,
        )
        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
        )
        TestUtils.flushLoopers(mainLooperManager)

        val recreatedInputDeviceRemapper = setupControllerRemapper()
        addInputDevice(deviceId, device.descriptor, device)
        recreatedInputDeviceRemapper.onInputDeviceAdded(deviceId)

        verify(native)
            .setKeyRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_BUTTON_X)),
                eq(intArrayOf(KeyEvent.KEYCODE_BUTTON_Y)),
            )
        verify(native)
            .setKeyToAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_BUTTON_A)),
                eq(intArrayOf(MotionEvent.AXIS_Z)),
            )
        verify(native)
            .setAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(MotionEvent.AXIS_LTRIGGER)),
                eq(intArrayOf(MotionEvent.AXIS_RTRIGGER)),
            )
        assertEquals(
            recreatedInputDeviceRemapper.getKeyRemappings(USER_ID, device.identifier),
            mapOf(KeyEvent.KEYCODE_BUTTON_X to KeyEvent.KEYCODE_BUTTON_Y),
        )
        assertEquals(
            recreatedInputDeviceRemapper.getAxisRemappings(USER_ID, device.identifier),
            mapOf(MotionEvent.AXIS_LTRIGGER to MotionEvent.AXIS_RTRIGGER),
        )
    }

    @Test
    fun onBackupAndRestore_allRemappingsRestored() {
        val inputDeviceRemapper = setupControllerRemapper()
        val joystickDevice =
            createDevice(
                /* deviceId= */ 1,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        inputDeviceRemapper.remapAxis(
            USER_ID,
            joystickDevice.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )
        val keyboardDevice =
            createDevice(
                /* deviceId */ 2,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_KEYBOARD,
            )
        inputDeviceRemapper.remapKey(
            USER_ID,
            keyboardDevice.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )
        val keyboardAndJoystickDevice =
            createDevice(
                /* deviceId */ 3,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_JOYSTICK,
            )
        inputDeviceRemapper.remapKeyToAxis(
            USER_ID,
            keyboardAndJoystickDevice.identifier,
            KeyEvent.KEYCODE_BUTTON_C,
            MotionEvent.AXIS_Z,
        )
        TestUtils.flushLoopers(mainLooperManager)
        reset(native)

        val backupData = inputDeviceRemapper.getInputDeviceRemappingBackupPayload(USER_ID)
        // Clear data and create new instance of remapper to simulate fresh install of device */
        testDataStore.clear()
        val recreatedInputDeviceRemapper = setupControllerRemapper()
        recreatedInputDeviceRemapper.applyInputDeviceRemappingBackupPayload(backupData, USER_ID)

        assertEquals(
            recreatedInputDeviceRemapper.getAxisRemappings(USER_ID, joystickDevice.identifier),
            mapOf(MotionEvent.AXIS_X to MotionEvent.AXIS_Y),
        )
        assertEquals(
            recreatedInputDeviceRemapper.getKeyRemappings(USER_ID, keyboardDevice.identifier),
            mapOf(KeyEvent.KEYCODE_1 to KeyEvent.KEYCODE_2),
        )
        addInputDevice(joystickDevice.id, joystickDevice.descriptor, joystickDevice)
        addInputDevice(keyboardDevice.id, keyboardDevice.descriptor, keyboardDevice)
        addInputDevice(
            keyboardAndJoystickDevice.id,
            keyboardAndJoystickDevice.descriptor,
            keyboardAndJoystickDevice,
        )
        recreatedInputDeviceRemapper.onInputDeviceAdded(joystickDevice.id)
        recreatedInputDeviceRemapper.onInputDeviceAdded(keyboardDevice.id)
        recreatedInputDeviceRemapper.onInputDeviceAdded(keyboardAndJoystickDevice.id)
        TestUtils.flushLoopers(mainLooperManager)
        verify(native)
            .setKeyRemappingForDevice(
                eq(keyboardDevice.id),
                eq(intArrayOf(KeyEvent.KEYCODE_1)),
                eq(intArrayOf(KeyEvent.KEYCODE_2)),
            )
        verify(native)
            .setKeyToAxisRemappingForDevice(
                eq(keyboardAndJoystickDevice.id),
                eq(intArrayOf(KeyEvent.KEYCODE_BUTTON_C)),
                eq(intArrayOf(MotionEvent.AXIS_Z)),
            )
        verify(native)
            .setAxisRemappingForDevice(
                eq(joystickDevice.id),
                eq(intArrayOf(MotionEvent.AXIS_X)),
                eq(intArrayOf(MotionEvent.AXIS_Y)),
            )
    }

    @Test
    fun testDeviceTypeChanged_appliesKeyRemappings() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        // Device starts with a non-keyboard source
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_MOUSE,
            )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        // This should not be applied yet
        inputDeviceRemapper.remapKey(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )
        verify(native, never()).setKeyRemappingForDevice(any(), any(), any())

        // Device changes to a keyboard source
        val updatedDevice =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_KEYBOARD,
            )
        changeInputDevice(deviceId, device.descriptor, updatedDevice)
        inputDeviceRemapper.onInputDeviceChanged(deviceId)
        TestUtils.flushLoopers(mainLooperManager)

        // Verify that the key remapping was applied
        verify(native)
            .setKeyRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_1)),
                eq(intArrayOf(KeyEvent.KEYCODE_2)),
            )
    }

    @Test
    fun deviceTypeChanged_appliesAllRemappings() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        // Device starts with a non-keyboard source
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_MOUSE,
            )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        // This should not be applied yet
        inputDeviceRemapper.remapKey(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
        )
        inputDeviceRemapper.remapKeyToAxis(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_X,
            MotionEvent.AXIS_LTRIGGER,
        )
        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
        )
        verifyNoInteractions(native)

        // Device changes to both joystick and keyboard source
        val updatedDevice =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_KEYBOARD,
            )
        changeInputDevice(deviceId, device.descriptor, updatedDevice)
        inputDeviceRemapper.onInputDeviceChanged(deviceId)
        TestUtils.flushLoopers(mainLooperManager)

        verify(native)
            .setKeyRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_BUTTON_A)),
                eq(intArrayOf(KeyEvent.KEYCODE_BUTTON_B)),
            )
        verify(native)
            .setKeyToAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_BUTTON_X)),
                eq(intArrayOf(MotionEvent.AXIS_LTRIGGER)),
            )
        verify(native)
            .setAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(MotionEvent.AXIS_LTRIGGER)),
                eq(intArrayOf(MotionEvent.AXIS_RTRIGGER)),
            )
    }

    @Test
    fun testDeviceTypeChanged_appliesAxisRemappings() {
        val inputDeviceRemapper = setupControllerRemapper()
        val deviceId = 1
        // Device starts with a non-joystick source
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_MOUSE,
            )
        addInputDevice(deviceId, device.descriptor, device)
        inputDeviceRemapper.onInputDeviceAdded(deviceId)
        reset(native)

        // This should not be applied yet
        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )
        verify(native, never()).setAxisRemappingForDevice(any(), any(), any())

        // Device changes to a joystick source
        val updatedDevice =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        changeInputDevice(deviceId, device.descriptor, updatedDevice)
        inputDeviceRemapper.onInputDeviceChanged(deviceId)
        TestUtils.flushLoopers(mainLooperManager)

        // Verify that the axis remapping was applied
        verify(native)
            .setAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(MotionEvent.AXIS_X)),
                eq(intArrayOf(MotionEvent.AXIS_Y)),
            )
    }

    @Test
    fun emptyRemappingsGetPersisted() {
        val inputDeviceRemapper = setupControllerRemapper()
        val device =
            createDevice(
                deviceId = 1,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_KEYBOARD,
            )
        inputDeviceRemapper.remapKey(
            USER_ID,
            device.identifier,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
        )
        inputDeviceRemapper.remapAxis(
            USER_ID,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )
        TestUtils.flushLoopers(mainLooperManager)
        // Now clear them
        inputDeviceRemapper.clearAllKeyRemappings(USER_ID, device.identifier)
        inputDeviceRemapper.clearAllAxisRemappings(USER_ID, device.identifier)
        TestUtils.flushLoopers(mainLooperManager)
        // Recreate and check that they are still cleared
        val recreatedInputDeviceRemapper = setupControllerRemapper()
        addInputDevice(device.id, device.descriptor, device)
        reset(native)

        recreatedInputDeviceRemapper.onInputDeviceAdded(device.id)

        // The remappings should be cleared on the native side
        verify(native).setKeyRemappingForDevice(eq(device.id), eq(intArrayOf()), eq(intArrayOf()))
        verify(native).setAxisRemappingForDevice(eq(device.id), eq(intArrayOf()), eq(intArrayOf()))
        assertThat(
            recreatedInputDeviceRemapper.getKeyRemappings(USER_ID, device.identifier),
            anEmptyMap(),
        )
        assertThat(
            recreatedInputDeviceRemapper.getAxisRemappings(USER_ID, device.identifier),
            anEmptyMap(),
        )
    }

    private fun addInputDevice(deviceId: Int, descriptor: String, device: InputDevice) {
        inputDeviceIds.add(deviceId)
        whenever(inputManager.getInputDevice(deviceId)).thenReturn(device)
        whenever(inputManager.getInputDeviceByDescriptor(descriptor)).thenReturn(device)
    }

    private fun removeInputDevice(deviceId: Int, descriptor: String) {
        inputDeviceIds.remove(deviceId)
        whenever(inputManager.getInputDevice(deviceId)).thenReturn(null)
        whenever(inputManager.getInputDeviceByDescriptor(descriptor)).thenReturn(null)
    }

    private fun changeInputDevice(deviceId: Int, descriptor: String, device: InputDevice) {
        whenever(inputManager.getInputDevice(deviceId)).thenReturn(device)
        whenever(inputManager.getInputDeviceByDescriptor(descriptor)).thenReturn(device)
    }

    private fun createDevice(
        deviceId: Int,
        vendorId: Int,
        productId: Int,
        sources: Int,
    ): InputDevice =
        InputDevice.Builder()
            .setId(deviceId)
            .setVendorId(vendorId)
            .setProductId(productId)
            .setName("Device $deviceId")
            .setDescriptor("descriptor $deviceId")
            .setSources(sources)
            .build()

    companion object {
        const val USER_ID = 1
        const val SECOND_USER_ID = 2
    }
}
