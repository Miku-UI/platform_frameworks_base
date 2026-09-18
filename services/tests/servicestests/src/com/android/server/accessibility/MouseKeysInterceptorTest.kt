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

import android.Manifest
import android.content.Context
import android.hardware.input.IInputManager
import android.hardware.input.IVirtualMouse
import android.hardware.input.InputManager
import android.hardware.input.InputManagerGlobal
import android.hardware.input.VirtualMouseButtonEvent
import android.hardware.input.VirtualMouseConfig
import android.hardware.input.VirtualMouseRelativeEvent
import android.hardware.input.VirtualMouseScrollEvent
import android.os.IBinder
import android.os.RemoteException
import android.os.test.TestLooper
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import android.testing.TestableContext
import android.util.MathUtils.sqrt
import android.view.InputDevice
import android.view.KeyEvent
import androidx.annotation.RequiresPermission
import androidx.test.core.app.ApplicationProvider
import com.android.server.accessibility.MouseKeysInterceptor.FAKE_DEVICE_GENERATION_ID
import com.android.server.accessibility.MouseKeysInterceptor.FAKE_NUMPAD_DEVICE_GENERATION_ID
import com.android.server.testutils.OffsettableClock
import com.google.common.truth.Truth.assertThat
import java.util.LinkedList
import java.util.Queue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/**
 * Tests for {@link MouseKeysInterceptor}
 *
 * Build/Install/Run: atest FrameworksServicesTests:MouseKeysInterceptorTest
 */
@Presubmit
class MouseKeysInterceptorTest {
    companion object {
        const val DISPLAY_ID = 1
        const val DEVICE_ID = 123
        const val VIRTUAL_DEVICE_ID = 456
        const val NUMPAD_DEVICE_ID = 789
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
        // This multiplier is used to scale diagonal movement to match cardinal movement via
        // computing the sides of a triangle whose hypotenuse is `1.0`. This constant was computed
        // using `1.0f / sqrt(2.0f)`.
        const val DIAGONAL_MULTIPLIER = 0.7071068f
    }

    private lateinit var mouseKeysInterceptor: MouseKeysInterceptor
    private lateinit var inputDevice: InputDevice
    private lateinit var virtualInputDevice: InputDevice
    private lateinit var numpadInputDevice: InputDevice
    private lateinit var testLooper: TestLooper

    private val clock = OffsettableClock()
    private val nextInterceptor = TrackingInterceptor()

    @get:Rule val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val testableContext = TestableContext(ApplicationProvider.getApplicationContext<Context>())

    @Mock private lateinit var mockAms: AccessibilityManagerService

    @Mock private lateinit var iInputManager: IInputManager
    private lateinit var testSession: InputManagerGlobal.TestSession
    private lateinit var mockInputManager: InputManager

    @Mock private lateinit var mockVirtualMouse: IVirtualMouse

    @Mock private lateinit var mockTraceManager: AccessibilityTraceManager

    private val testTimeSource =
        object : TimeSource {
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
        testableContext.addMockSystemService(InputManager::class.java, mockInputManager)

        inputDevice = createInputDevice(DEVICE_ID, /* isVirtual= */ false)
        virtualInputDevice = createInputDevice(VIRTUAL_DEVICE_ID, /* isVirtual */ true)
        numpadInputDevice =
            createInputDevice(
                NUMPAD_DEVICE_ID,
                /* isVirtual= */ false,
                FAKE_NUMPAD_DEVICE_GENERATION_ID,
            )
        Mockito.`when`(iInputManager.getInputDevice(DEVICE_ID)).thenReturn(inputDevice)
        Mockito.`when`(iInputManager.getInputDevice(VIRTUAL_DEVICE_ID))
            .thenReturn(virtualInputDevice)
        Mockito.`when`(iInputManager.getInputDevice(NUMPAD_DEVICE_ID)).thenReturn(numpadInputDevice)

        Mockito.`when`(
            iInputManager.createVirtualMouse(
                any(IBinder::class.java),
                any(VirtualMouseConfig::class.java))
            )
            .thenReturn(mockVirtualMouse)

        Mockito.`when`(iInputManager.inputDeviceIds)
            .thenReturn(intArrayOf(DEVICE_ID, VIRTUAL_DEVICE_ID, NUMPAD_DEVICE_ID))
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
     * specific test being run. This will prevent any race conditions between the ContentObserver
     * correctly reading the primary keys settings and the test being run with a stale setting when
     * mouseKeysInterceptor.onKeyEvent() is called. This will ensure the test is run with the
     * correct primary keys setting. This function should be called at the beginning of each test.
     */
    @RequiresPermission(Manifest.permission.INJECT_EVENTS)
    private fun setupMouseKeysInterceptor(usePrimaryKeys: Boolean) {
        val setting = if (usePrimaryKeys) 1 else 0
        Settings.Secure.putIntForUser(
            testableContext.getContentResolver(),
            Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_USE_PRIMARY_KEYS,
            setting,
            USER_ID,
        )
        Settings.Secure.putIntForUser(
            testableContext.getContentResolver(),
            Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_MAX_SPEED,
            5,
            USER_ID,
        )
        Settings.Secure.putFloatForUser(
            testableContext.getContentResolver(),
            Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_ACCELERATION,
            0.2f,
            USER_ID,
        )

        testLooper = TestLooper { clock.now() }
        mouseKeysInterceptor =
            MouseKeysInterceptor(
                mockAms,
                testableContext,
                testLooper.looper,
                DISPLAY_ID,
                testTimeSource,
                USER_ID,
            )

        mouseKeysInterceptor.next = nextInterceptor
        mouseKeysInterceptor.mCreateVirtualMouseThread.join()
    }

    @Test
    fun whenNonMouseKeyEventArrives_eventIsPassedToNextInterceptor() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        val downTime = clock.now()
        val downEvent =
            KeyEvent(
                downTime,
                downTime,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_Q,
                0,
                0,
                DEVICE_ID,
                0,
            )
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
        val keyCode =
            MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_DOWN_LEFT_MOVE.getKeyCodeValue(
                USE_PRIMARY_KEYS
            )
        val downEvent =
            KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, 0, DEVICE_ID, 0)

        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        // Verify the sendMouseRelativeEvent method is called once and capture the arguments
        verifyRelativeEvents(
            expectedX =
                floatArrayOf(-MouseKeysInterceptor.MOUSE_POINTER_MOVEMENT_STEP / sqrt(2.0f)),
            expectedY = floatArrayOf(MouseKeysInterceptor.MOUSE_POINTER_MOVEMENT_STEP / sqrt(2.0f)),
        )
    }

    // This test checks that all directional keys for primary keys work when "use primary keys" is
    // on.
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenDirectionalKeyIsPressed_relativeEventIsSent_withUsePrimaryKeysOn_primaryKeys() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)

        // Primary keys up
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
            deviceId = DEVICE_ID,
            metaState = 0,
            xMultiplier = 0f,
            yMultiplier = -1f,
        )

        // Primary keys down
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DOWN_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
            deviceId = DEVICE_ID,
            metaState = 0,
            xMultiplier = 0f,
            yMultiplier = 1f,
        )

        // Primary keys left
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.LEFT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
            deviceId = DEVICE_ID,
            metaState = 0,
            xMultiplier = -1f,
            yMultiplier = 0f,
        )

        // Primary keys right
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.RIGHT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
            deviceId = DEVICE_ID,
            metaState = 0,
            xMultiplier = 1f,
            yMultiplier = 0f,
        )

        // Primary keys up-left
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_UP_LEFT_MOVE.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
            deviceId = DEVICE_ID,
            metaState = 0,
            xMultiplier = -DIAGONAL_MULTIPLIER,
            yMultiplier = -DIAGONAL_MULTIPLIER,
        )

        // Primary keys up-right
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_UP_RIGHT_MOVE.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
            deviceId = DEVICE_ID,
            metaState = 0,
            xMultiplier = DIAGONAL_MULTIPLIER,
            yMultiplier = -DIAGONAL_MULTIPLIER,
        )

        // Primary keys down-left
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_DOWN_LEFT_MOVE.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
            deviceId = DEVICE_ID,
            metaState = 0,
            xMultiplier = -DIAGONAL_MULTIPLIER,
            yMultiplier = DIAGONAL_MULTIPLIER,
        )

        // Primary keys down-right
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_DOWN_RIGHT_MOVE.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
            deviceId = DEVICE_ID,
            metaState = 0,
            xMultiplier = DIAGONAL_MULTIPLIER,
            yMultiplier = DIAGONAL_MULTIPLIER,
        )
    }

    // This test checks that all directional keys for the numpad work when "use primary keys" is on.
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenDirectionalKeyIsPressed_relativeEventIsSent_withUsePrimaryKeysOn_numpad() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)

        // Numpad up
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = 0f,
            yMultiplier = -1f,
        )

        // Numpad down
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DOWN_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = 0f,
            yMultiplier = 1f,
        )

        // Numpad left
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.LEFT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = -1f,
            yMultiplier = 0f,
        )

        // Numpad right
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.RIGHT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = 1f,
            yMultiplier = 0f,
        )

        // Numpad up-left
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_UP_LEFT_MOVE.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = -DIAGONAL_MULTIPLIER,
            yMultiplier = -DIAGONAL_MULTIPLIER,
        )

        // Numpad up-right
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_UP_RIGHT_MOVE.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = DIAGONAL_MULTIPLIER,
            yMultiplier = -DIAGONAL_MULTIPLIER,
        )

        // Numpad down-left
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_DOWN_LEFT_MOVE.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = -DIAGONAL_MULTIPLIER,
            yMultiplier = DIAGONAL_MULTIPLIER,
        )

        // Numpad down-right
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_DOWN_RIGHT_MOVE.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = DIAGONAL_MULTIPLIER,
            yMultiplier = DIAGONAL_MULTIPLIER,
        )
    }

    // This test checks that all directional keys for the numpad work when "use primary keys" is
    // off.
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenDirectionalKeyIsPressed_relativeEventIsSent_withUsePrimaryKeysOff() {
        setupMouseKeysInterceptor(usePrimaryKeys = false)

        // Numpad up
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = 0f,
            yMultiplier = -1f,
        )

        // Numpad down
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DOWN_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = 0f,
            yMultiplier = 1f,
        )

        // Numpad left
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.LEFT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = -1f,
            yMultiplier = 0f,
        )

        // Numpad right
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.RIGHT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = 1f,
            yMultiplier = 0f,
        )

        // Numpad up-left
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_UP_LEFT_MOVE.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = -DIAGONAL_MULTIPLIER,
            yMultiplier = -DIAGONAL_MULTIPLIER,
        )

        // Numpad up-right
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_UP_RIGHT_MOVE.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = DIAGONAL_MULTIPLIER,
            yMultiplier = -DIAGONAL_MULTIPLIER,
        )

        // Numpad down-left
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_DOWN_LEFT_MOVE.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = -DIAGONAL_MULTIPLIER,
            yMultiplier = DIAGONAL_MULTIPLIER,
        )

        // Numpad down-right
        testDirectionalKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_DOWN_RIGHT_MOVE.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xMultiplier = DIAGONAL_MULTIPLIER,
            yMultiplier = DIAGONAL_MULTIPLIER,
        )
    }

    // This test checks that the click, hold, and release keys for primary keys work when "use
    // primary keys" is on.
    @Test
    fun whenClickKeyIsPressed_buttonEventIsSent_withUsePrimaryKeysOn_primaryKeys() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)

        // Primary keys left click
        testClickKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.LEFT_CLICK.getKeyCodeValue(USE_PRIMARY_KEYS),
            deviceId = DEVICE_ID,
            metaState = 0,
            expectedButton = VirtualMouseButtonEvent.BUTTON_PRIMARY,
        )

        // Primary keys right click
        testClickKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.RIGHT_CLICK.getKeyCodeValue(USE_PRIMARY_KEYS),
            deviceId = DEVICE_ID,
            metaState = 0,
            expectedButton = VirtualMouseButtonEvent.BUTTON_SECONDARY,
        )

        // Primary keys hold
        testButtonEvent(
            keyCode = MouseKeysInterceptor.MouseKeyEvent.HOLD.getKeyCodeValue(USE_PRIMARY_KEYS),
            deviceId = DEVICE_ID,
            metaState = 0,
            expectedAction = VirtualMouseButtonEvent.ACTION_BUTTON_PRESS,
            expectedButton = VirtualMouseButtonEvent.BUTTON_PRIMARY,
        )

        // Primary keys release
        testButtonEvent(
            keyCode = MouseKeysInterceptor.MouseKeyEvent.RELEASE.getKeyCodeValue(USE_PRIMARY_KEYS),
            deviceId = DEVICE_ID,
            metaState = 0,
            expectedAction = VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE,
            expectedButton = VirtualMouseButtonEvent.BUTTON_PRIMARY,
        )
    }

    // This test checks that the click, hold, and release keys for the numpad work when "use primary
    // keys" is on.
    @Test
    fun whenClickKeyIsPressed_buttonEventIsSent_withUsePrimaryKeysOn_numpad() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)

        // Numpad left click
        testClickKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.LEFT_CLICK.getKeyCodeValue(USE_NUMPAD_KEYS),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            expectedButton = VirtualMouseButtonEvent.BUTTON_PRIMARY,
        )

        // Numpad right click
        testClickKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.RIGHT_CLICK.getKeyCodeValue(USE_NUMPAD_KEYS),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            expectedButton = VirtualMouseButtonEvent.BUTTON_SECONDARY,
        )

        // Numpad hold
        testButtonEvent(
            keyCode = MouseKeysInterceptor.MouseKeyEvent.HOLD.getKeyCodeValue(USE_NUMPAD_KEYS),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            expectedAction = VirtualMouseButtonEvent.ACTION_BUTTON_PRESS,
            expectedButton = VirtualMouseButtonEvent.BUTTON_PRIMARY,
        )

        // Numpad release
        testButtonEvent(
            keyCode = MouseKeysInterceptor.MouseKeyEvent.RELEASE.getKeyCodeValue(USE_NUMPAD_KEYS),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            expectedAction = VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE,
            expectedButton = VirtualMouseButtonEvent.BUTTON_PRIMARY,
        )
    }

    // This test checks that the click, hold, and release keys for the numpad work when "use primary
    // keys" is on.
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenClickKeyIsPressed_buttonEventIsSent_withUsePrimaryKeysOff_numpad() {
        setupMouseKeysInterceptor(usePrimaryKeys = false)

        // Numpad left click
        testClickKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.LEFT_CLICK.getKeyCodeValue(USE_NUMPAD_KEYS),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            expectedButton = VirtualMouseButtonEvent.BUTTON_PRIMARY,
        )

        // Numpad right click
        testClickKey(
            keyCode =
                MouseKeysInterceptor.MouseKeyEvent.RIGHT_CLICK.getKeyCodeValue(USE_NUMPAD_KEYS),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            expectedButton = VirtualMouseButtonEvent.BUTTON_SECONDARY,
        )

        // Numpad hold
        testButtonEvent(
            keyCode = MouseKeysInterceptor.MouseKeyEvent.HOLD.getKeyCodeValue(USE_NUMPAD_KEYS),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            expectedAction = VirtualMouseButtonEvent.ACTION_BUTTON_PRESS,
            expectedButton = VirtualMouseButtonEvent.BUTTON_PRIMARY,
        )

        // Numpad release
        testButtonEvent(
            keyCode = MouseKeysInterceptor.MouseKeyEvent.RELEASE.getKeyCodeValue(USE_NUMPAD_KEYS),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            expectedAction = VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE,
            expectedButton = VirtualMouseButtonEvent.BUTTON_PRIMARY,
        )
    }

    // This test checks that all scroll keys for primary keys work when "use primary keys" is on.
    @Test
    fun whenScrollKeyIsPressed_scrollEventIsSent_withUsePrimaryKeysOn_primaryKeys() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)

        // Primary keys up
        testScrollKey(
            keyCodeScrollToggle =
                MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(USE_PRIMARY_KEYS),
            keyCodeScroll =
                MouseKeysInterceptor.MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
            deviceId = DEVICE_ID,
            metaState = 0,
            xAxisMultiplier = 0f,
            yAxisMultiplier = 1f,
        )

        // Primary keys down
        testScrollKey(
            keyCodeScrollToggle =
                MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(USE_PRIMARY_KEYS),
            keyCodeScroll =
                MouseKeysInterceptor.MouseKeyEvent.DOWN_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
            deviceId = DEVICE_ID,
            metaState = 0,
            xAxisMultiplier = 0f,
            yAxisMultiplier = -1f,
        )

        // Primary keys left
        testScrollKey(
            keyCodeScrollToggle =
                MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(USE_PRIMARY_KEYS),
            keyCodeScroll =
                MouseKeysInterceptor.MouseKeyEvent.LEFT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
            deviceId = DEVICE_ID,
            metaState = 0,
            xAxisMultiplier = 1f,
            yAxisMultiplier = 0f,
        )

        // Primary keys right
        testScrollKey(
            keyCodeScrollToggle =
                MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(USE_PRIMARY_KEYS),
            keyCodeScroll =
                MouseKeysInterceptor.MouseKeyEvent.RIGHT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
            deviceId = DEVICE_ID,
            metaState = 0,
            xAxisMultiplier = -1f,
            yAxisMultiplier = 0f,
        )
    }

    // This test checks that all scroll keys for the numpad work when "use primary keys" is on.
    @Test
    fun whenScrollKeyIsPressed_scrollEventIsSent_withUsePrimaryKeysOn_numpad() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)

        // Numpad up
        testScrollKey(
            keyCodeScrollToggle =
                MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(USE_NUMPAD_KEYS),
            keyCodeScroll =
                MouseKeysInterceptor.MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xAxisMultiplier = 0f,
            yAxisMultiplier = 1f,
        )

        // Numpad down
        testScrollKey(
            keyCodeScrollToggle =
                MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(USE_NUMPAD_KEYS),
            keyCodeScroll =
                MouseKeysInterceptor.MouseKeyEvent.DOWN_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xAxisMultiplier = 0f,
            yAxisMultiplier = -1f,
        )

        // Numpad left
        testScrollKey(
            keyCodeScrollToggle =
                MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(USE_NUMPAD_KEYS),
            keyCodeScroll =
                MouseKeysInterceptor.MouseKeyEvent.LEFT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xAxisMultiplier = 1f,
            yAxisMultiplier = 0f,
        )

        // Numpad right
        testScrollKey(
            keyCodeScrollToggle =
                MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(USE_NUMPAD_KEYS),
            keyCodeScroll =
                MouseKeysInterceptor.MouseKeyEvent.RIGHT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xAxisMultiplier = -1f,
            yAxisMultiplier = 0f,
        )
    }

    // This test checks that all scroll keys for the numpad work when "use primary keys" is off.
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenScrollKeyIsPressed_scrollEventIsSent_withUsePrimaryKeysOff() {
        setupMouseKeysInterceptor(usePrimaryKeys = false)

        // Numpad up
        testScrollKey(
            keyCodeScrollToggle =
                MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(USE_NUMPAD_KEYS),
            keyCodeScroll =
                MouseKeysInterceptor.MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xAxisMultiplier = 0f,
            yAxisMultiplier = 1f,
        )

        // Numpad down
        testScrollKey(
            keyCodeScrollToggle =
                MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(USE_NUMPAD_KEYS),
            keyCodeScroll =
                MouseKeysInterceptor.MouseKeyEvent.DOWN_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xAxisMultiplier = 0f,
            yAxisMultiplier = -1f,
        )

        // Numpad left
        testScrollKey(
            keyCodeScrollToggle =
                MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(USE_NUMPAD_KEYS),
            keyCodeScroll =
                MouseKeysInterceptor.MouseKeyEvent.LEFT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xAxisMultiplier = 1f,
            yAxisMultiplier = 0f,
        )

        // Numpad right
        testScrollKey(
            keyCodeScrollToggle =
                MouseKeysInterceptor.MouseKeyEvent.SCROLL_TOGGLE.getKeyCodeValue(USE_NUMPAD_KEYS),
            keyCodeScroll =
                MouseKeysInterceptor.MouseKeyEvent.RIGHT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_NUMPAD_KEYS
                ),
            deviceId = NUMPAD_DEVICE_ID,
            metaState = KeyEvent.META_NUM_LOCK_ON,
            xAxisMultiplier = -1f,
            yAxisMultiplier = 0f,
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenDirectionalKeyHeld_movementAccelerates() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val keyCode =
            MouseKeysInterceptor.MouseKeyEvent.UP_MOVE_OR_SCROLL.getKeyCodeValue(USE_PRIMARY_KEYS)
        val downEvent =
            KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, 0, DEVICE_ID, 0)

        val expectedRelativeXs = mutableListOf<Float>()
        val expectedRelativeYs = mutableListOf<Float>()
        var currentMovementStepForExpectation = INITIAL_STEP_BEFORE_ACCEL

        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        // Update initial calculations
        currentMovementStepForExpectation =
            minOf(
                currentMovementStepForExpectation * (1 + mouseKeysInterceptor.mAcceleration),
                mouseKeysInterceptor.mMaxMovementStep,
            )
        expectedRelativeXs.add(0f)
        expectedRelativeYs.add(-currentMovementStepForExpectation)

        // Simulate holding for 2 intervals
        val subsequentMovementsToObserve = 2
        for (i in 1..subsequentMovementsToObserve) {
            clock.fastForward(MOVE_REPEAT_DELAY_MILLS)
            testLooper.dispatchAll()
            currentMovementStepForExpectation =
                minOf(
                    currentMovementStepForExpectation * (1 + mouseKeysInterceptor.mAcceleration),
                    mouseKeysInterceptor.mMaxMovementStep,
                )
            expectedRelativeXs.add(0f)
            expectedRelativeYs.add(-currentMovementStepForExpectation)
        }

        val captor = ArgumentCaptor.forClass(VirtualMouseRelativeEvent::class.java)
        val expectedTotalEvents = 1 + subsequentMovementsToObserve
        verify(mockVirtualMouse, times(expectedTotalEvents))
            .sendMouseRelativeEvent(captor.capture())

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
        val keyCode =
            MouseKeysInterceptor.MouseKeyEvent.RIGHT_MOVE_OR_SCROLL.getKeyCodeValue(
                USE_PRIMARY_KEYS
            )
        val downEvent =
            KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, 0, DEVICE_ID, 0)

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
        verify(mockVirtualMouse, times(numIntervals + 1))
            .sendMouseRelativeEvent(captor.capture())

        val allEvents = captor.allValues
        val lastCapturedEvent = allEvents.last()
        assertThat(lastCapturedEvent.relativeX).isEqualTo(mouseKeysInterceptor.mMaxMovementStep)
        assertThat(lastCapturedEvent.relativeY).isEqualTo(0f)

        // Also check a few before last to ensure it was capped
        val thirdLastCapturedEvent = allEvents[allEvents.size - 3]
        assertThat(thirdLastCapturedEvent.relativeX)
            .isEqualTo(mouseKeysInterceptor.mMaxMovementStep)
        assertThat(thirdLastCapturedEvent.relativeY).isEqualTo(0f)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenKeyReleasedAndPressedAgain_accelerationResets() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        val keyCode =
            MouseKeysInterceptor.MouseKeyEvent.DOWN_MOVE_OR_SCROLL.getKeyCodeValue(USE_PRIMARY_KEYS)

        // First Press and Hold
        var downTime1 = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val downEvent1 =
            KeyEvent(downTime1, downTime1, KeyEvent.ACTION_DOWN, keyCode, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent1, 0)
        testLooper.dispatchAll()

        clock.fastForward(MOVE_REPEAT_DELAY_MILLS)
        testLooper.dispatchAll()
        clock.fastForward(MOVE_REPEAT_DELAY_MILLS)
        testLooper.dispatchAll()

        // Release Key
        val upEventTime1 = clock.now()
        val upEvent1 =
            KeyEvent(downTime1, upEventTime1, KeyEvent.ACTION_UP, keyCode, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(upEvent1, 0)
        testLooper.dispatchAll()

        // Clear previous interactions with mockVirtualMouse before the second press
        // to only capture events from the second press sequence.
        reset(mockVirtualMouse)

        // Second Press
        clock.fastForward(100L)
        val downTime2 = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val downEvent2 =
            KeyEvent(downTime2, downTime2, KeyEvent.ACTION_DOWN, keyCode, 0, 0, DEVICE_ID, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent2, 0)
        testLooper.dispatchAll()

        // Calculate expected first step for a new press
        val expectedFirstStepAfterReset =
            INITIAL_STEP_BEFORE_ACCEL * (1 + mouseKeysInterceptor.mAcceleration)
        // Verify the sendMouseRelativeEvent method is called once and capture the arguments
        verifyRelativeEvents(
            expectedX = floatArrayOf(0f),
            expectedY = floatArrayOf(expectedFirstStepAfterReset),
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenUsePrimaryKeysOff_ignoresPrimaryKeys() {
        setupMouseKeysInterceptor(usePrimaryKeys = false)

        var primaryDownTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val primaryKeyDownEvent =
            KeyEvent(
                primaryDownTime,
                primaryDownTime,
                KeyEvent.ACTION_DOWN,
                MouseKeysInterceptor.MouseKeyEvent.LEFT_MOVE_OR_SCROLL.getKeyCodeValue(
                    USE_PRIMARY_KEYS
                ),
                0,
                0,
                DEVICE_ID,
                0,
            )
        mouseKeysInterceptor.onKeyEvent(primaryKeyDownEvent, 0)
        testLooper.dispatchAll()

        // Verify the corresponding primary key is ignored.
        assertThat(nextInterceptor.events).hasSize(1)
        verify(mockVirtualMouse, never()).sendMouseRelativeEvent(any())

        // Verify that the received event is the same as the primary key that was pressed.
        verifyKeyEventsEqual(primaryKeyDownEvent, nextInterceptor.events.poll()!!)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenUsePrimaryKeysOn_andNumLockOff_ignoresNumpad() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)

        val numpadDownTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val numpadKeycode =
            MouseKeysInterceptor.MouseKeyEvent.LEFT_MOVE_OR_SCROLL.getKeyCodeValue(USE_NUMPAD_KEYS)
        val numpadKeyDownEvent =
            KeyEvent(
                numpadDownTime,
                numpadDownTime,
                KeyEvent.ACTION_DOWN,
                numpadKeycode,
                0,
                0,
                NUMPAD_DEVICE_ID,
                0,
            )
        mouseKeysInterceptor.onKeyEvent(numpadKeyDownEvent, 0)
        testLooper.dispatchAll()

        // Verify the corresponding numpad key is ignored.
        assertThat(nextInterceptor.events).hasSize(1)
        verify(mockVirtualMouse, never()).sendMouseRelativeEvent(any())

        // Verify that the received event is the same as the numpad key that was pressed.
        verifyKeyEventsEqual(numpadKeyDownEvent, nextInterceptor.events.poll()!!)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)
    fun whenUsePrimaryKeysOff_andNumLockOff_ignoresNumpad() {
        setupMouseKeysInterceptor(usePrimaryKeys = false)

        val numpadDownTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val numpadKeycode =
            MouseKeysInterceptor.MouseKeyEvent.LEFT_MOVE_OR_SCROLL.getKeyCodeValue(USE_NUMPAD_KEYS)
        val numpadKeyDownEvent =
            KeyEvent(
                numpadDownTime,
                numpadDownTime,
                KeyEvent.ACTION_DOWN,
                numpadKeycode,
                0,
                0,
                NUMPAD_DEVICE_ID,
                0,
            )
        mouseKeysInterceptor.onKeyEvent(numpadKeyDownEvent, 0)
        testLooper.dispatchAll()

        // Verify the corresponding numpad key is ignored.
        assertThat(nextInterceptor.events).hasSize(1)
        verify(mockVirtualMouse, never()).sendMouseRelativeEvent(any())

        // Verify that the received event is the same as the numpad key that was pressed.
        verifyKeyEventsEqual(numpadKeyDownEvent, nextInterceptor.events.poll()!!)
    }

    @Test
    fun whenMouseKeyEventArrives_fromVirtualKeyboard_eventIsPassedToNextInterceptor() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        for (ev in MouseKeysInterceptor.MouseKeyEvent.entries) {
            val downTime = clock.now()
            val keyCode = ev.getKeyCodeValue(USE_PRIMARY_KEYS)
            val downEvent =
                KeyEvent(
                    downTime,
                    downTime,
                    KeyEvent.ACTION_DOWN,
                    keyCode,
                    0,
                    0,
                    VIRTUAL_DEVICE_ID,
                    0,
                )
            mouseKeysInterceptor.onKeyEvent(downEvent, 0)
            testLooper.dispatchAll()

            assertThat(nextInterceptor.events).hasSize(1)
            verifyKeyEventsEqual(downEvent, nextInterceptor.events.poll()!!)
        }
    }

    @Test
    fun whenMultipleInterceptorsCreated_virtualDeviceNamesAreUnique() {
        val vmcCaptor = ArgumentCaptor.forClass(VirtualMouseConfig::class.java)

        setupMouseKeysInterceptor(usePrimaryKeys = true)
        setupMouseKeysInterceptor(usePrimaryKeys = true)

        verify(iInputManager, times(2)).createVirtualMouse(
            any(IBinder::class.java),
            vmcCaptor.capture())

        val mouseKeysVirtualDevicePrefix = "Mouse Keys Virtual Device ("
        val firstName = vmcCaptor.allValues[0].inputDeviceName
        assertThat(firstName).startsWith(mouseKeysVirtualDevicePrefix)

        val secondName = vmcCaptor.allValues[1].inputDeviceName
        assertThat(secondName).startsWith(mouseKeysVirtualDevicePrefix)

        assertThat(firstName).isNotEqualTo(secondName)
    }

    @Test
    fun onKeyEvent_mapClearedDuringMovement_doesNotCrash() {
        setupMouseKeysInterceptor(usePrimaryKeys = true)
        // There should be some delay between the downTime of the key event and calling onKeyEvent.
        val delayMillis =
            if (DeviceFlagsValueProvider().getBoolean(Flags.FLAG_ENABLE_MOUSE_KEY_ENHANCEMENT)) {
                KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
            } else {
                KEYBOARD_POST_EVENT_DELAY_MILLIS
            }
        val downTime = clock.now() - delayMillis
        val keyCode =
            MouseKeysInterceptor.MouseKeyEvent.DIAGONAL_DOWN_LEFT_MOVE.getKeyCodeValue(
                USE_PRIMARY_KEYS
            )
        val downEvent =
            KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, 0, DEVICE_ID, 0)

        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        // Simulate race condition by clearing the device mapping.
        // This would happen during onInputDeviceRemoved or a settings change
        mouseKeysInterceptor.onInputDeviceRemoved(DEVICE_ID)
        testLooper.dispatchAll()

        // Verify that the virtual mouse didn't get a second event after the map was cleared
        verify(mockVirtualMouse, times(1)).sendMouseRelativeEvent(any())
    }

    private fun testDirectionalKey(
        keyCode: Int,
        deviceId: Int,
        metaState: Int,
        xMultiplier: Float,
        yMultiplier: Float,
    ) {
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS_FOR_MOUSE_POINTER
        val downEvent =
            KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState, deviceId, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        val stepValue = INITIAL_STEP_BEFORE_ACCEL * (1.0f + mouseKeysInterceptor.mAcceleration)
        verifyRelativeEvents(
            expectedX = floatArrayOf(xMultiplier * stepValue),
            expectedY = floatArrayOf(yMultiplier * stepValue),
        )

        val upEvent =
            KeyEvent(downTime, clock.now(), KeyEvent.ACTION_UP, keyCode, 0, metaState, deviceId, 0)
        mouseKeysInterceptor.onKeyEvent(upEvent, 0)
        testLooper.dispatchAll()
        reset(mockVirtualMouse)
    }

    private fun testScrollKey(
        keyCodeScrollToggle: Int,
        keyCodeScroll: Int,
        deviceId: Int,
        metaState: Int,
        xAxisMultiplier: Float,
        yAxisMultiplier: Float,
    ) {
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS
        val scrollToggleDownEvent =
            KeyEvent(
                downTime,
                downTime,
                KeyEvent.ACTION_DOWN,
                keyCodeScrollToggle,
                0,
                metaState,
                deviceId,
                0,
            )
        val scrollDownEvent =
            KeyEvent(
                downTime,
                downTime,
                KeyEvent.ACTION_DOWN,
                keyCodeScroll,
                0,
                metaState,
                deviceId,
                0,
            )

        mouseKeysInterceptor.onKeyEvent(scrollToggleDownEvent, 0)
        mouseKeysInterceptor.onKeyEvent(scrollDownEvent, 0)
        testLooper.dispatchAll()

        verifyScrollEvents(
            xAxisMovements = floatArrayOf(xAxisMultiplier * MouseKeysInterceptor.MOUSE_SCROLL_STEP),
            yAxisMovements = floatArrayOf(yAxisMultiplier * MouseKeysInterceptor.MOUSE_SCROLL_STEP),
        )

        val upEvent =
            KeyEvent(
                downTime,
                clock.now(),
                KeyEvent.ACTION_UP,
                keyCodeScroll,
                0,
                metaState,
                deviceId,
                0,
            )
        mouseKeysInterceptor.onKeyEvent(upEvent, 0)
        val scrollToggleOffEvent =
            KeyEvent(
                clock.now(),
                clock.now(),
                KeyEvent.ACTION_DOWN,
                keyCodeScrollToggle,
                0,
                metaState,
                deviceId,
                0,
            )
        mouseKeysInterceptor.onKeyEvent(scrollToggleOffEvent, 0)
        testLooper.dispatchAll()
        reset(mockVirtualMouse)
    }

    private fun testClickKey(keyCode: Int, deviceId: Int, metaState: Int, expectedButton: Int) {
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS
        val downEvent =
            KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState, deviceId, 0)
        mouseKeysInterceptor.onKeyEvent(downEvent, 0)
        testLooper.dispatchAll()

        val actions =
            intArrayOf(
                VirtualMouseButtonEvent.ACTION_BUTTON_PRESS,
                VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE,
            )
        val buttons = intArrayOf(expectedButton, expectedButton)
        verifyButtonEvents(actions = actions, buttons = buttons)
        reset(mockVirtualMouse)
    }

    private fun testButtonEvent(
        keyCode: Int,
        deviceId: Int,
        metaState: Int,
        expectedAction: Int,
        expectedButton: Int,
    ) {
        val downTime = clock.now() - KEYBOARD_POST_EVENT_DELAY_MILLIS
        val event =
            KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState, deviceId, 0)
        mouseKeysInterceptor.onKeyEvent(event, 0)
        testLooper.dispatchAll()

        verifyButtonEvents(
            actions = intArrayOf(expectedAction),
            buttons = intArrayOf(expectedButton),
        )
        reset(mockVirtualMouse)
    }

    private fun verifyRelativeEvents(expectedX: FloatArray, expectedY: FloatArray) {
        assertThat(expectedX.size).isEqualTo(expectedY.size)
        val expectedSize = expectedX.size

        val captor = ArgumentCaptor.forClass(VirtualMouseRelativeEvent::class.java)
        verify(mockVirtualMouse, times(expectedSize))
            .sendMouseRelativeEvent(captor.capture())

        val actualEvents = captor.allValues
        assertThat(actualEvents).hasSize(expectedSize)

        val actualXs = actualEvents.map { it.relativeX }
        val actualYs = actualEvents.map { it.relativeY }

        // With certain directions there can be a discrepancy in the seventh digit of precision.
        // This seems like it may be due to the Java `sqrt` implementation converting its argument
        // to a `double`, and the returned value being converted back to a `float`.
        for (i in 0 until expectedSize) {
            assertThat(actualXs[i]).isWithin(0.0001f).of(expectedX[i])
            assertThat(actualYs[i]).isWithin(0.0001f).of(expectedY[i])
        }
    }

    private fun verifyButtonEvents(actions: IntArray, buttons: IntArray) {
        assertThat(actions.size).isEqualTo(buttons.size)
        val expectedSize = actions.size

        val captor = ArgumentCaptor.forClass(VirtualMouseButtonEvent::class.java)
        verify(mockVirtualMouse, times(actions.size))
            .sendMouseButtonEvent(captor.capture())

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
        verify(mockVirtualMouse, times(expectedSize))
            .sendMouseScrollEvent(captor.capture())

        val actualEvents = captor.allValues
        assertThat(actualEvents).hasSize(expectedSize)

        val actualXAxis = actualEvents.map { it.xAxisMovement }
        val actualYAxis = actualEvents.map { it.yAxisMovement }

        // With certain directions there can be a discrepancy in the seventh digit of precision.
        // This seems like it may be due to the Java `sqrt` implementation converting its argument
        // to a `double`, and the returned value being converted back to a `float`.
        for (i in 0 until expectedSize) {
            assertThat(actualXAxis[i]).isWithin(0.0001f).of(xAxisMovements[i])
            assertThat(actualYAxis[i]).isWithin(0.0001f).of(yAxisMovements[i])
        }
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
        generation: Int = FAKE_DEVICE_GENERATION_ID,
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
