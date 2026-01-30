/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.accessibility

import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceParams
import android.content.Context
import android.hardware.input.IInputManager
import android.hardware.input.InputManager
import android.hardware.input.InputManagerGlobal
import android.hardware.input.VirtualMouse
import android.hardware.input.VirtualMouseButtonEvent
import android.hardware.input.VirtualMouseConfig
import android.hardware.input.VirtualMouseRelativeEvent
import android.hardware.input.VirtualMouseScrollEvent
import android.os.RemoteException
import android.os.test.TestLooper
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import android.testing.TestableContext
import android.util.ArraySet
import android.util.MathUtils.sqrt
import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.android.server.LocalServices
import com.android.server.companion.virtual.VirtualDeviceManagerInternal
import com.android.server.testutils.OffsettableClock
import java.util.LinkedList
import java.util.Queue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import com.google.common.truth.Truth.assertThat

/**
 * Tests for {@link MouseKeysInterceptor}
 *
 * Build/Install/Run:
 * atest FrameworksServicesTests:MouseKeysInterceptorTest
 */
@Presubmit
class MouseKeysInterceptorTest {
    companion object {
        const val DISPLAY_ID = 1
        const val DEVICE_ID = 123
        const val VIRTUAL_DEVICE_ID = 456
        // This delay is required for key events to be sent and handled correctly.
        // The handler only performs a move/scroll event if it receives the key event
        // at INTERVAL_MILLIS (which happens in practice). Hence, we need this delay in the tests.
        const val KEYBOARD_POST_EVENT_DELAY_MILLIS = 20L
        // The initial offset applied to the KeyEvent's downTime for mouse pointer movement tests.
        // This is used when FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT is enabled in the test.
        // This value ensures the first mouse movement action is triggered immediately
        // when the handler processes the initial key down event, satisfying the required
        // time interval (MOVE_REPEAT_DELAY_MILLS). It should be >= MOVE_REPEAT_DELAY_MILLS.
        const val KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER = 30L
        // The initial movement step for the mouse pointer before acceleration begins.
        // This directly corresponds to `INITIAL_MOUSE_POINTER_MOVEMENT_STEP` in the
        // MouseKeysInterceptor.
        const val INITIAL_STEP_BEFORE_ACCEL = 1.0f
        // The time interval, in milliseconds, at which mouse pointer movement actions
        // are repeated when a key is held down and FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT is enabled.
        // This directly corresponds to `INTERVAL_MILLIS_MOUSE_POINTER` in the MouseKeysInterceptor.
        const val MOVE_REPEAT_DELAY_MILLS = 25L
        const val USER_ID = 0
        const val USE_PRIMARY_KEYS = true
        const val USE_NUMPAD_KEYS = false
    }

    private lateinit var mouseKeysInterceptor: MouseKeysInterceptor
    private lateinit var inputDevice: InputDevice
    private lateinit var virtualInputDevice: InputDevice

    private val clock = OffsettableClock()
    private val testLooper = TestLooper { clock.now() }
    private val nextInterceptor = TrackingInterceptor()

    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val testableContext = TestableContext(ApplicationProvider.getApplicationContext<Context>())

    @Mock
    private lateinit var mockAms: AccessibilityManagerService

    @Mock
    private lateinit var iInputManager: IInputManager
    private lateinit var testSession: InputManagerGlobal.TestSession
    private lateinit var mockInputManager: InputManager

    @Mock
    private lateinit var mockVirtualDeviceManagerInternal: VirtualDeviceManagerInternal
    @Mock
    private lateinit var mockVirtualDevice: VirtualDeviceManager.VirtualDevice
    @Mock
    private lateinit var mockVirtualMouse: VirtualMouse

    @Mock
    private lateinit var mockTraceManager: AccessibilityTraceManager

    private val testTimeSource = object : TimeSource {
        override fun uptimeMillis(): Long {
            return clock.now()
        }
    }

    @Before
    @Throws(RemoteException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        testSession = InputManagerGlobal.createTestSession(iInputManager)
        mockInputManager = InputManager(testableContext)

        inputDevice = createInputDevice(DEVICE_ID, /* isVirtual= */ false)
        virtualInputDevice = createInputDevice(VIRTUAL_DEVICE_ID, /* isVirtual */ true)
        Mockito.`when`(iInputManager.getInputDevice(DEVICE_ID))
                .thenReturn(inputDevice)
        Mockito.`when`(iInputManager.getInputDevice(VIRTUAL_DEVICE_ID))
            .thenReturn(virtualInputDevice)

        Mockito.`when`(mockVirtualDeviceManagerInternal.getDeviceIdsForUid(Mockito.anyInt()))
            .thenReturn(ArraySet(setOf(DEVICE_ID)))
        LocalServices.removeServiceForTest(VirtualDeviceManagerInternal::class.java)
        LocalServices.addService<VirtualDeviceManagerInternal>(
            VirtualDeviceManagerInternal::class.java, mockVirtualDeviceManagerInternal
        )

        Mockito.`when`(mockVirtualDeviceManagerInternal.createVirtualDevice(
            Mockito.any(VirtualDeviceParams::class.java)
        )).thenReturn(mockVirtualDevice)
        Mockito.`when`(mockVirtualDevice.createVirtualMouse(
            Mockito.any(VirtualMouseConfig::class.java)
        )).thenReturn(mockVirtualMouse)

        Mockito.`when`(iInputManager.inputDeviceIds)
            .thenReturn(intArrayOf(DEVICE_ID, VIRTUAL_DEVICE_ID))
        Mockito.`when`(mockAms.traceManager).thenReturn(mockTraceManager)
    }

    @After
    fun tearDown() {
        testLooper.dispatchAll()
        if (this::testSession.isInitialized) {
            testSession.close()
        }
    }

    /**
     * Ensure that the MouseKeysInterceptor is created with the correct configuration for the
     * specific test being run. This will prevent any race conditions between
     * the ContentObserver correctly reading the primary keys settings and the test being run
     * with a stale setting when mouseKeysInterceptor.onKeyEvent() is called.
     * This will ensure the test is run with the correct primary keys setting.
     * This function should be called at the beginning of each test.
     */
    private fun setupMouseKeysInterceptor(usePrimaryKeys: Boolean) {
        val setting = if (usePrimaryKeys) 1 else 0
        Settings.Secure.putIntForUser(testableContext.getContentResolver(),
            Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_USE_PRIMARY_KEYS, setting, USER_ID)
        Settings.Secure.putIntForUser(testableContext.getContentResolver(),
            Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_MAX_SPEED, 5, USER_ID)
        Settings.Secure.putFloatForUser(testableContext.getContentResolver(),
            Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_ACCELERATION, 0.2f, USER_ID)

        mouseKeysInterceptor = MouseKeysInterceptor(mockAms, testableContext,
            testLooper.looper, DISPLAY_ID, testTimeSource, USER_ID)

        mouseKeysInterceptor.next = nextInterceptor
        mouseKeysInterceptor.mCreateVirtualMouseThread.join()
    }

    @Test
    fun whenNonMouseKeyEventArrives_eventIsPassedToNextInterceptor() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        val downTime = clock.now()
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_Q, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        assertThat(nextInterceptor.events).hasSize(1)
        verifyKeyEventsEqual(downEvent, nextInterceptor.events.poll()!!)
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenMouseDirectionalKeyIsPressed_relativeEventIsSent() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        // There should be some delay between the downTime of the key event and calling onKeyEvent
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS
        val keyCode = MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_DOWN_LEFT_MOVE.getKeyCodeValue(
            USE_PRIMARY_KEYS)
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCode, 0, 0, DEVICE_ID, 0)

        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        // Verify the sendRelativeEvent method is called once and capture the arguments
        verifyRelativeEvents(
            expectedX = floatArrayOf(-MouseKeysInterceptor.MOUSE_POINTER_MOVEMENT_STEP / sqrt(2.0f)),
            expectedY = floatArrayOf(MouseKeysInterceptor.MOUSE_POINTER_MOVEMENT_STEP / sqrt(2.0f))
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenMouseDirectionalKeyIsPressedWithFlagOn_relativeEventIsSent() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        // There should be some delay between the downTime of the key event and calling onKeyEvent
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val keyCode = MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_DOWN_LEFT_MOVE.getKeyCodeValue(
                USE_PRIMARY_KEYS)
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCode, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        // Verify the sendRelativeEvent method is called once and capture the arguments
        val expectedStepValue = INITIAL_STEP_BEFORE_ACCEL * (1.0f + mouseKeysInterceptor.mAcceleration)
        verifyRelativeEvents(expectedX = floatArrayOf(-expectedStepValue / sqrt(2.0f)),
            expectedY = floatArrayOf(expectedStepValue / sqrt(2.0f)))
    }

    @Test
    fun whenClickKeyIsPressed_buttonEventIsSent() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        // There should be some delay between the downTime of the key event and calling onKeyEvent
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS
        val keyCode = MouseKeysInterceptor.MouseKeyEvent.LEFT_CLICK.getKeyCodeValue(
            USE_PRIMARY_KEYS)
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCode, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        val actions = intArrayOf(
            VirtualMouseButtonEvent.ACTION_BUTTON_PRESS,
            VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE)
        val buttons = intArrayOf(
            VirtualMouseButtonEvent.BUTTON_PRIMARY,
            VirtualMouseButtonEvent.BUTTON_PRIMARY)
        // Verify the sendButtonEvent method is called twice and capture the arguments
        verifyButtonEvents(actions = actions, buttons = buttons)
    }

    @Test
    fun whenHoldKeyIsPressed_buttonEventIsSent() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS
        val keyCode = MouseKeysInterceptor.MouseKeyEvent.HOLD.getKeyCodeValue(
            USE_PRIMARY_KEYS)
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCode, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        // Verify the sendButtonEvent method is called once and capture the arguments
        verifyButtonEvents(
            actions = intArrayOf(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS),
            buttons = intArrayOf(VirtualMouseButtonEvent.BUTTON_PRIMARY)
        )
    }

    @Test
    fun whenReleaseKeyIsPressed_buttonEventIsSent() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS
        val keyCode = MouseKeysInterceptor.MouseKeyEvent.RELEASE.getKeyCodeValue(
            USE_PRIMARY_KEYS)
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCode, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        // Verify the sendButtonEvent method is called once and capture the arguments
        verifyButtonEvents(
            actions = intArrayOf(VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE),
            buttons = intArrayOf(VirtualMouseButtonEvent.BUTTON_PRIMARY)
        )
    }

    @Test
    fun whenScrollToggleOn_ScrollUpKeyIsPressed_scrollEventIsSent() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        // There should be some delay between the downTime of the key event and calling onKeyEvent
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS
        val keyCodeScrollToggle = MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(
            USE_PRIMARY_KEYS)
        val keyCodeScroll = MouseKeysInterceptor.MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCodeValue(
            USE_PRIMARY_KEYS)

        val scrollToggleDownEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCodeScrollToggle, 0, 0, DEVICE_ID, 0)
        val scrollDownEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCodeScroll, 0, 0, DEVICE_ID, 0)

        mouseKeysInterceptor.onKeyEvent(scrollToggleDownEvent, 0)
        mouseKeysInterceptor.onKeyEvent(scrollDownEvent, 0)
        testLooper.dispatchAll()

        // Verify the sendScrollEvent method is called once and capture the arguments
        verifyScrollEvents(xAxisMovements = floatArrayOf(0f),
            yAxisMovements = floatArrayOf(MouseKeysInterceptor.MOUSE_SCROLL_STEP)
        )
    }

    @Test
    fun whenScrollToggleOn_ScrollRightKeyIsPressed_scrollEventIsSent() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        // There should be some delay between the downTime of the key event and calling onKeyEvent
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS
        val keyCodeScrollToggle = MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(
            USE_PRIMARY_KEYS)
        val keyCodeScroll = MouseKeysInterceptor.MouseKeyEvent.RIGHT_MOVE_OR_SCROLL.getKeyCodeValue(
            USE_PRIMARY_KEYS)

        val scrollToggleDownEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCodeScrollToggle, 0, 0, DEVICE_ID, 0)
        val scrollDownEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCodeScroll, 0, 0, DEVICE_ID, 0)

        mouseKeysInterceptor.onKeyEvent(scrollToggleDownEvent, 0)
        mouseKeysInterceptor.onKeyEvent(scrollDownEvent, 0)
        testLooper.dispatchAll()

        // Verify the sendScrollEvent method is called once and capture the arguments
        verifyScrollEvents(xAxisMovements = floatArrayOf(-MouseKeysInterceptor.MOUSE_SCROLL_STEP),
            yAxisMovements = floatArrayOf(0f)
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenScrollToggleOn_NumpadScrollRightKeyIsPressed_scrollEventIsSent() {
        setupMouseKeysInterceptor(usePrimaryKeys = false)
        // There should be some delay between the downTime of the key event and calling onKeyEvent
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS
        val keyCodeScrollToggle = MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(
            USE_NUMPAD_KEYS)
        val keyCodeScroll = MouseKeysInterceptor.MouseKeyEvent.RIGHT_MOVE_OR_SCROLL.getKeyCodeValue(
            USE_NUMPAD_KEYS)

        val scrollToggleDownEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCodeScrollToggle, 0, 0, DEVICE_ID, 0)
        val scrollDownEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCodeScroll, 0, 0, DEVICE_ID, 0)

        mouseKeysInterceptor.onKeyEvent(scrollToggleDownEvent, 0)
        mouseKeysInterceptor.onKeyEvent(scrollDownEvent, 0)
        testLooper.dispatchAll()

        // Verify the sendScrollEvent method is called once and capture the arguments
        verifyScrollEvents(xAxisMovements = floatArrayOf(-MouseKeysInterceptor.MOUSE_SCROLL_STEP),
            yAxisMovements = floatArrayOf(0f))
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenScrollToggleOff_DirectionalUpKeyIsPressed_RelativeEventIsSent() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        // There should be some delay between the downTime of the key event and calling onKeyEvent
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS
        val keyCode = MouseKeysInterceptor.MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCodeValue(
            USE_PRIMARY_KEYS)
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCode, 0, 0, DEVICE_ID, 0)

        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        // Verify the sendRelativeEvent method is called once and capture the arguments
        verifyRelativeEvents(expectedX = floatArrayOf(0f),
            expectedY = floatArrayOf(-MouseKeysInterceptor.MOUSE_POINTER_MOVEMENT_STEP)
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenScrollToggleOffWithFlagOn_NumpadDirectionalUpKeyIsPressed_relativeEventIsSent() {
        setupMouseKeysInterceptor(usePrimaryKeys = false)
        // There should be some delay between the downTime of the key event and calling onKeyEvent
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val keyCode = MouseKeysInterceptor.MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCodeValue(
            USE_NUMPAD_KEYS)
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCode, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        // Verify the sendRelativeEvent method is called once and capture the arguments
        val expectedStepValue = INITIAL_STEP_BEFORE_ACCEL * (1.0f + mouseKeysInterceptor.mAcceleration)
        verifyRelativeEvents(expectedX = floatArrayOf(0f),
            expectedY = floatArrayOf(-expectedStepValue))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenScrollToggleOffWithFlagOn_directionalUpKeyIsPressed_relativeEventIsSent() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        // There should be some delay between the downTime of the key event and calling onKeyEvent
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val keyCode = MouseKeysInterceptor.MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCodeValue(
            USE_PRIMARY_KEYS)
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCode, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        // Verify the sendRelativeEvent method is called once and capture the arguments
        val expectedStepValue = INITIAL_STEP_BEFORE_ACCEL * (1.0f + mouseKeysInterceptor.mAcceleration)
        verifyRelativeEvents(expectedX = floatArrayOf(0f),
            expectedY = floatArrayOf(-expectedStepValue))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenDirectionalKeyHeld_movementAccelerates() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val keyCode = MouseKeysInterceptor.MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCodeValue(
            USE_PRIMARY_KEYS)
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCode, 0, 0, DEVICE_ID, 0)

        val expectedRelativeXs = mutableListOf<Float>()
        val expectedRelativeYs = mutableListOf<Float>()
        var currentMovementStepForExpectation = INITIAL_STEP_BEFORE_ACCEL

        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        // Update initial calculations
        currentMovementStepForExpectation = minOf(
            currentMovementStepForExpectation * (1 + mouseKeysInterceptor.mAcceleration),
            mouseKeysInterceptor.mMaxMovementStep)
        expectedRelativeXs.add(0f)
        expectedRelativeYs.add(-currentMovementStepForExpectation)

        // Simulate holding for 2 intervals
        val subsequentMovementsToObserve = 2
        for (i in 1..subsequentMovementsToObserve) {
            clock.fastForward(MOVE_REPEAT_DELAY_MILLS)
            testLooper.dispatchAll()
            currentMovementStepForExpectation = minOf(
                currentMovementStepForExpectation * (1 + mouseKeysInterceptor.mAcceleration),
                    mouseKeysInterceptor.mMaxMovementStep)
            expectedRelativeXs.add(0f)
            expectedRelativeYs.add(-currentMovementStepForExpectation)
        }

        val captor = ArgumentCaptor.forClass(VirtualMouseRelativeEvent::class.java)
        val expectedTotalEvents = 1 + subsequentMovementsToObserve
        Mockito.verify(mockVirtualMouse, Mockito.times(expectedTotalEvents))
            .sendRelativeEvent(captor.capture())

        val allCapturedEvents = captor.allValues
        assertThat(allCapturedEvents).hasSize(expectedTotalEvents)

        val actualRelativeXs = allCapturedEvents.map { it.relativeX }
        val actualRelativeYs = allCapturedEvents.map { it.relativeY }

        assertThat(actualRelativeXs).containsExactlyElementsIn(expectedRelativeXs).inOrder()
        assertThat(actualRelativeYs).containsExactlyElementsIn(expectedRelativeYs).inOrder()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenDirectionalKeyHeldLong_movementCapsAtMaxMovementStep() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val keyCode = MouseKeysInterceptor.MouseKeyEvent.RIGHT_MOVE_OR_SCROLL.getKeyCodeValue(
            USE_PRIMARY_KEYS)
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCode, 0, 0, DEVICE_ID, 0)

        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        // Simulate holding until max movement step is definitely reached
        // (1.0 * 1.1^N >= 10.0 => 1.1^N >= 10 => N * log(1.1) >= log(10) => N >= log(10)/log(1.1)
        // approx 24.15) so, around 25-30 intervals should be enough.
        val numIntervals = 26
        for (i in 1..numIntervals) {
            clock.fastForward(MOVE_REPEAT_DELAY_MILLS)
            testLooper.dispatchAll()
        }

        val captor = ArgumentCaptor.forClass(VirtualMouseRelativeEvent::class.java)
        Mockito.verify(mockVirtualMouse, Mockito.times(numIntervals + 1))
            .sendRelativeEvent(captor.capture())

        val allEvents = captor.allValues
        val lastCapturedEvent = allEvents.last()
        assertThat(lastCapturedEvent.relativeX).isEqualTo(mouseKeysInterceptor.mMaxMovementStep)
        assertThat(lastCapturedEvent.relativeY).isEqualTo(0f)

        // Also check a few before last to ensure it was capped
        val thirdLastCapturedEvent = allEvents[allEvents.size - 3]
        assertThat(thirdLastCapturedEvent.relativeX).isEqualTo(
                mouseKeysInterceptor.mMaxMovementStep)
        assertThat(thirdLastCapturedEvent.relativeY).isEqualTo(0f)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenKeyReleasedAndPressedAgain_accelerationResets() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        val keyCode = MouseKeysInterceptor.MouseKeyEvent.DOWN_MOVE_OR_SCROLL.getKeyCodeValue(
            USE_PRIMARY_KEYS)

        // First Press and Hold
        var downTime1 = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val downEvent1 = KeyEvent(downTime1, downTime1, KeyEvent.ACTION_DOWN,
            keyCode, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent1, 0)
        testLooper.dispatchAll()

        clock.fastForward(MOVE_REPEAT_DELAY_MILLS)
        testLooper.dispatchAll()
        clock.fastForward(MOVE_REPEAT_DELAY_MILLS)
        testLooper.dispatchAll()

        // Release Key
        val upEventTime1 = clock.now()
        val upEvent1 = KeyEvent(downTime1, upEventTime1, KeyEvent.ACTION_UP,
            keyCode, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(upEvent1, 0)
        testLooper.dispatchAll()

        // Clear previous interactions with mockVirtualMouse before the second press
        // to only capture events from the second press sequence.
        Mockito.reset(mockVirtualMouse)

        // Second Press
        clock.fastForward(100L)
        val downTime2 = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val downEvent2 = KeyEvent(downTime2, downTime2, KeyEvent.ACTION_DOWN,
            keyCode, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent2, 0)
        testLooper.dispatchAll()

        // Calculate expected first step for a new press
        val expectedFirstStepAfterReset = INITIAL_STEP_BEFORE_ACCEL * (1 + mouseKeysInterceptor.mAcceleration)
        // Verify the sendRelativeEvent method is called once and capture the arguments
        verifyRelativeEvents(expectedX = floatArrayOf(0f),
            expectedY = floatArrayOf(expectedFirstStepAfterReset))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenSettingIsNumpad_respondsToNumpadKeysAndIgnoresPrimaryKeys() {
        setupMouseKeysInterceptor(usePrimaryKeys = false)
        val numpadDownTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val numpadKeyDownEvent = KeyEvent(
            numpadDownTime, numpadDownTime, KeyEvent.ACTION_DOWN,
            MouseKeysInterceptor.MouseKeyEvent.LEFT_MOVE_OR_SCROLL.getKeyCodeValue(USE_NUMPAD_KEYS),
            0, 0, DEVICE_ID, 0
        )
        mouseKeysInterceptor.onKeyEvent(numpadKeyDownEvent, 0)
        testLooper.dispatchAll()

        val expectedStepValue = INITIAL_STEP_BEFORE_ACCEL * (1.0f + mouseKeysInterceptor.mAcceleration)
        verifyRelativeEvents(expectedX = floatArrayOf(-expectedStepValue), expectedY = floatArrayOf(0f))
        assertThat(nextInterceptor.events).isEmpty()

        Mockito.clearInvocations(mockVirtualMouse)
        nextInterceptor.events.clear()

        // Send the primary key 'U', which also corresponds to moving left.
        var primaryDownTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val primaryKeyDownEvent = KeyEvent(
            primaryDownTime, primaryDownTime, KeyEvent.ACTION_DOWN,
            MouseKeysInterceptor.MouseKeyEvent.LEFT_MOVE_OR_SCROLL.getKeyCodeValue(USE_PRIMARY_KEYS),
            0, 0, DEVICE_ID, 0
        )
        mouseKeysInterceptor.onKeyEvent(primaryKeyDownEvent, 0)
        testLooper.dispatchAll()

        // Verify the corresponding primary key is ignored
        assertThat(nextInterceptor.events).hasSize(1)
        Mockito.verify(mockVirtualMouse, Mockito.never()).sendRelativeEvent(Mockito.any())

        // Verify that the received event is the same as the primary key that was pressed
        verifyKeyEventsEqual(primaryKeyDownEvent, nextInterceptor.events.poll()!!)
    }

    @Test
    fun whenMouseKeyEventArrives_fromVirtualKeyboard_eventIsPassedToNextInterceptor() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        for (ev in MouseKeysInterceptor.MouseKeyEvent.entries) {
            val downTime = clock.now()
            val keyCode = ev.getKeyCodeValue(USE_PRIMARY_KEYS)
            val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                keyCode, 0, 0, VIRTUAL_DEVICE_ID, 0
            )
            mouseKeysInterceptor.onKeyEvent(downEvent, 0)
            testLooper.dispatchAll()

            assertThat(nextInterceptor.events).hasSize(1)
            verifyKeyEventsEqual(downEvent, nextInterceptor.events.poll()!!)
        }
    }

    private fun verifyRelativeEvents(expectedX: FloatArray, expectedY: FloatArray) {
        assertThat(expectedX.size).isEqualTo(expectedY.size)
        val expectedSize = expectedX.size

        val captor = ArgumentCaptor.forClass(VirtualMouseRelativeEvent::class.java)
        Mockito.verify(mockVirtualMouse, Mockito.times(expectedSize))
            .sendRelativeEvent(captor.capture())

        val actualEvents = captor.allValues
        assertThat(actualEvents).hasSize(expectedSize)

        val actualXs = actualEvents.map { it.relativeX }
        val actualYs = actualEvents.map { it.relativeY }

        assertThat(actualXs).containsExactlyElementsIn(expectedX.toList()).inOrder()
        assertThat(actualYs).containsExactlyElementsIn(expectedY.toList()).inOrder()
    }

    private fun verifyButtonEvents(actions: IntArray, buttons: IntArray) {
        assertThat(actions.size).isEqualTo(buttons.size)
        val expectedSize = actions.size

        val captor = ArgumentCaptor.forClass(VirtualMouseButtonEvent::class.java)
        Mockito.verify(mockVirtualMouse, Mockito.times(actions.size))
                .sendButtonEvent(captor.capture())

        val actualEvents = captor.allValues
        assertThat(actualEvents).hasSize(expectedSize)

        val actualActions = actualEvents.map { it.action }
        val actualButtons = actualEvents.map { it.buttonCode }

        assertThat(actualActions).containsExactlyElementsIn(actions.toList()).inOrder()
        assertThat(actualButtons).containsExactlyElementsIn(buttons.toList()).inOrder()
    }

    private fun verifyScrollEvents(xAxisMovements: FloatArray, yAxisMovements: FloatArray) {
        assertThat(xAxisMovements.size).isEqualTo(yAxisMovements.size)
        val expectedSize = xAxisMovements.size

        val captor = ArgumentCaptor.forClass(VirtualMouseScrollEvent::class.java)
        Mockito.verify(mockVirtualMouse, Mockito.times(expectedSize))
            .sendScrollEvent(captor.capture())

        val actualEvents = captor.allValues
        assertThat(actualEvents).hasSize(expectedSize)

        val actualXAxis = actualEvents.map { it.xAxisMovement }
        val actualYAxis = actualEvents.map { it.yAxisMovement }

        assertThat(actualXAxis).containsExactlyElementsIn(xAxisMovements.toList()).inOrder()
        assertThat(actualYAxis).containsExactlyElementsIn(yAxisMovements.toList()).inOrder()
    }

    private fun verifyKeyEventsEqual(expected: KeyEvent, received: KeyEvent) {
        assertThat(received.keyCode).isEqualTo(expected.keyCode)
        assertThat(received.action).isEqualTo(expected.action)
        assertThat(received.downTime).isEqualTo(expected.downTime)
        assertThat(received.eventTime).isEqualTo(expected.eventTime)
    }

    private fun createInputDevice(
        deviceId: Int,
        isVirtual: Boolean,
        generation: Int = -1
    ): InputDevice =
        InputDevice.Builder()
            .setId(deviceId)
            .setName("Device $deviceId")
            .setDescriptor("descriptor $deviceId")
            .setGeneration(generation)
            .setIsVirtualDevice(isVirtual)
            .setSources(InputDevice.SOURCE_KEYBOARD)
            .setKeyboardType(InputDevice.KEYBOARD_TYPE_ALPHABETIC)
            .build()

    private class TrackingInterceptor : BaseEventStreamTransformation() {
        val events: Queue<KeyEvent> = LinkedList()

        override fun onKeyEvent(event: KeyEvent, policyFlags: Int) {
            events.add(event)
        }
    }
}
