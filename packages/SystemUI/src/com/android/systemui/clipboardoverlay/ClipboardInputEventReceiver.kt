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

package com.android.systemui.clipboardoverlay

import android.hardware.input.InputManager
import android.os.Looper
import android.util.Log
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.InputMonitor
import javax.inject.Inject

open class ClipboardInputEventReceiver @Inject constructor(private val inputManager: InputManager) {
    private var inputMonitor: InputMonitor? = null
    private var inputEventReceiver: InputEventReceiver? = null

    fun monitorOutsideTouches(onInputEvent: (InputEvent?) -> Unit) {
        if (inputMonitor != null || inputEventReceiver != null) {
            Log.wtf(TAG, "monitorOutsideTouches called multiple times without disposal")
            dispose()
        }
        inputMonitor =
            inputManager.monitorGestureInput("clipboard overlay", 0).also {
                inputEventReceiver =
                    object : InputEventReceiver(it.inputChannel, Looper.getMainLooper()) {
                        override fun onInputEvent(event: InputEvent?) {
                            onInputEvent(event)
                            finishInputEvent(event, true)
                        }
                    }
            }
    }

    fun dispose() {
        inputMonitor?.dispose()
        inputMonitor = null
        inputEventReceiver?.dispose()
        inputEventReceiver = null
    }

    companion object {
        private const val TAG = "ClipboardInputEventReceiver"
    }
}
