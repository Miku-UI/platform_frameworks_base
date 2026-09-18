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

package test.virtualgamepaddemo

import android.app.Activity
import android.hardware.input.InputManager
import android.hardware.input.VirtualGamepad
import android.hardware.input.VirtualGamepadConfig
import android.hardware.input.VirtualGamepadMotionEvent
import android.hardware.input.VirtualGamepadMotionEventBuilder
import android.hardware.input.VirtualKeyEvent
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button

private data class GamepadState(
    var x: Float = 0.0f,
    var y: Float = 0.0f,
    var z: Float = 0.0f,
    var rz: Float = 0.0f,
    var hatX: Float = 0.0f,
    var hatY: Float = 0.0f,
    var lTrigger: Float = 0.0f,
    var rTrigger: Float = 0.0f,
)

private fun synthesizeGamepadMotionEvent(
    currentState: GamepadState,
    previousState: GamepadState,
    eventTimeNanos: Long,
): VirtualGamepadMotionEvent {
    val builder = VirtualGamepadMotionEventBuilder().setEventTimeNanos(eventTimeNanos)
    if (currentState.x != previousState.x) {
        builder.setX(currentState.x)
    }
    if (currentState.y != previousState.y) {
        builder.setY(currentState.y)
    }
    if (currentState.z != previousState.z) {
        builder.setZ(currentState.z)
    }
    if (currentState.rz != previousState.rz) {
        builder.setRz(currentState.rz)
    }
    if (currentState.hatX != previousState.hatX) {
        builder.setHatX(currentState.hatX)
    }
    if (currentState.hatY != previousState.hatY) {
        builder.setHatY(currentState.hatY)
    }
    if (currentState.lTrigger != previousState.lTrigger) {
        builder.setLTrigger(currentState.lTrigger)
    }
    if (currentState.rTrigger != previousState.rTrigger) {
        builder.setRTrigger(currentState.rTrigger)
    }
    return builder.build()
}

/**
 * Track the individual press state of dpad buttons to calculate the combined hatX and hatY axis
 * values for diagonal movement and release.
 */
private data class DpadState(
    var up: Boolean = false,
    var down: Boolean = false,
    var left: Boolean = false,
    var right: Boolean = false,
) {
    fun getHatX(): Float {
        return when {
            left -> -1.0f
            right -> 1.0f
            else -> 0.0f
        }
    }

    fun getHatY(): Float {
        return when {
            up -> -1.0f
            down -> 1.0f
            else -> 0.0f
        }
    }
}

class MainActivity : Activity() {

    private val gamepadState = GamepadState()
    private val dpadState = DpadState()

    private var gamepad: VirtualGamepad? = null

    private fun onGamepadStickTouch(v: View, event: MotionEvent): Boolean {
        val touchX = event.x
        val touchY = event.y

        val centerX = v.width / 2.0f
        val centerY = v.height / 2.0f

        var x = ((touchX - centerX) / centerX).coerceIn(-1.0f, 1.0f)
        var y = ((touchY - centerY) / centerY).coerceIn(-1.0f, 1.0f)

        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            x = 0.0f
            y = 0.0f
        }

        // Figure out which one is left and which one is right
        when (v.id) {
            R.id.xy_circular_button -> {
                gamepadState.x = x
                gamepadState.y = y
                Log.d(TAG, "Left stick - X: $x, Y: $y")
            }
            R.id.zrz_circular_button -> {
                gamepadState.z = x
                gamepadState.rz = y
                Log.d(TAG, "Right stick - Z: $x, RZ: $y")
            }
        }

        return true
    }

    private fun onDpadButtonTouch(v: View, event: MotionEvent): Boolean {
        val action = event.action
        if (action == MotionEvent.ACTION_DOWN) {
            when (v.id) {
                R.id.dpad_up -> dpadState.up = true
                R.id.dpad_down -> dpadState.down = true
                R.id.dpad_left -> dpadState.left = true
                R.id.dpad_right -> dpadState.right = true
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            when (v.id) {
                R.id.dpad_up -> dpadState.up = false
                R.id.dpad_down -> dpadState.down = false
                R.id.dpad_left -> dpadState.left = false
                R.id.dpad_right -> dpadState.right = false
            }
        } else {
            // Ignore move events for dpad
            return true
        }

        gamepadState.hatX = dpadState.getHatX()
        gamepadState.hatY = dpadState.getHatY()
        return true
    }

    // For L2/R2, this generates both a key event and (indirectly, through dispatchTouchEvent) a
    // motion event. This simulates both the digital "click" and the analog travel of a real
    // trigger.
    private fun onButtonTouch(v: View, event: MotionEvent): Boolean {
        val action = event.action

        val keyAction =
            when (action) {
                MotionEvent.ACTION_DOWN -> VirtualKeyEvent.ACTION_DOWN
                MotionEvent.ACTION_UP -> VirtualKeyEvent.ACTION_UP
                MotionEvent.ACTION_CANCEL -> VirtualKeyEvent.ACTION_UP
                else -> return true // Ignore other actions like MOVE
            }

        val isDown = keyAction == VirtualKeyEvent.ACTION_DOWN
        when (v.id) {
            R.id.button_l2 -> {
                gamepadState.lTrigger = if (isDown) 1.0f else 0.0f
            }
            R.id.button_r2 -> {
                gamepadState.rTrigger = if (isDown) 1.0f else 0.0f
            }
        }

        val keyCode =
            when (v.id) {
                R.id.button_a -> KeyEvent.KEYCODE_BUTTON_A
                R.id.button_b -> KeyEvent.KEYCODE_BUTTON_B
                R.id.button_x -> KeyEvent.KEYCODE_BUTTON_X
                R.id.button_y -> KeyEvent.KEYCODE_BUTTON_Y
                R.id.button_l1 -> KeyEvent.KEYCODE_BUTTON_L1
                R.id.button_r1 -> KeyEvent.KEYCODE_BUTTON_R1
                R.id.button_l2 -> KeyEvent.KEYCODE_BUTTON_L2
                R.id.button_r2 -> KeyEvent.KEYCODE_BUTTON_R2
                R.id.button_start -> KeyEvent.KEYCODE_BUTTON_START
                R.id.button_select -> KeyEvent.KEYCODE_BUTTON_SELECT
                else -> throw IllegalStateException("Unknown button id: " + v.id)
            }

        val keyEvent = VirtualKeyEvent.Builder().setKeyCode(keyCode).setAction(keyAction).build()
        checkNotNull(gamepad).sendKeyEvent(keyEvent)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This will allow the app to send keys to the other windows on screens.... but it will ANR
        // if there's no other app present.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)

        window.setDecorFitsSystemWindows(false)
        val rootView = findViewById<View>(R.id.root_layout)
        // In order to re-inject the joystick events as quickly as possible, request unbuffered
        // dispatch here. This increases the chances of the current touch event to generate a
        // joystick event, send it to the kernel, and have the Android framework deliver it to the
        // target application before the next vsync. If the touches were batched, we would only
        // process them near vsync, which would almost guarantee that the joystick event will be
        // send to the target application in the next frame (and not the current one).
        // See README.md for more info.
        rootView.requestUnbufferedDispatch(InputDevice.SOURCE_TOUCHSCREEN)
        rootView.setOnApplyWindowInsetsListener { _, insets ->
            val systemBarInsets = insets.getInsets(WindowInsets.Type.systemBars())
            rootView.setPadding(
                systemBarInsets.left,
                systemBarInsets.top,
                systemBarInsets.right,
                systemBarInsets.bottom,
            )
            insets
        }

        val buttonXY = findViewById<Button>(R.id.xy_circular_button)
        val buttonZRZ = findViewById<Button>(R.id.zrz_circular_button)
        buttonXY.setOnTouchListener(::onGamepadStickTouch)
        buttonZRZ.setOnTouchListener(::onGamepadStickTouch)

        GAMEPAD_BUTTON_IDS.forEach { id ->
            findViewById<Button>(id).setOnTouchListener(::onButtonTouch)
        }

        DPAD_BUTTON_IDS.forEach { id ->
            findViewById<Button>(id).setOnTouchListener(::onDpadButtonTouch)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val previousState = gamepadState.copy()
        val result = super.dispatchTouchEvent(event)
        // To reduce binder traffic, only sent the new motion if any of the gamepad axes have
        // changed. See README.md for more info.
        if (gamepadState != previousState) {
            checkNotNull(gamepad)
                .sendMotionEvent(
                    synthesizeGamepadMotionEvent(gamepadState, previousState, event.eventTimeNanos)
                )
        }
        return result
    }

    override fun onStart() {
        super.onStart()

        val inputManager = getSystemService(InputManager::class.java)
        val config = VirtualGamepadConfig()
        config.name = "Virtual Gamepad device"
        config.vendorId = 1234
        config.productId = 5678
        config.associatedDisplayId = displayId
        config.registerTriggerAxes = true
        gamepad = inputManager.createVirtualGamepad(config)
    }

    override fun onStop() {
        super.onStop()
        checkNotNull(gamepad) { "Gamepad was not initialized in onStart" }.close()
        gamepad = null
    }

    companion object {
        val TAG = "VirtualGamepadDemo"

        private val GAMEPAD_BUTTON_IDS =
            listOf(
                R.id.button_a,
                R.id.button_b,
                R.id.button_x,
                R.id.button_y,
                R.id.button_l1,
                R.id.button_r1,
                R.id.button_l2,
                R.id.button_r2,
                R.id.button_start,
                R.id.button_select,
            )

        private val DPAD_BUTTON_IDS =
            listOf(R.id.dpad_up, R.id.dpad_down, R.id.dpad_left, R.id.dpad_right)
    }
}
