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
package com.android.wm.shell.common

import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.view.DragEvent
import android.view.InputChannel
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.View
import android.view.WindowInputChannelParams
import android.view.WindowManager
import android.view.WindowManagerGlobal
import android.window.InputTransferToken

/** Manages a touchable surface that is intended to intercept touches, but does not draw */
class TouchInterceptLayer(val name: String = TAG) {
    private var clientToken: IBinder? = null
    private var inputChannel: InputChannel? = null
    private var inputEventReceiver: InputEventReceiver? = null
    private var layerLeash: SurfaceControl? = null

    var touchListener: View.OnTouchListener? = null

    /**
     * Note: This currently does not support the full drag-and-drop flow, as input only sends
     * DRAG_EXIT and DRAG_LOCATION events (see ViewRootImpl's
     * WindowInputEventReceiver#onDragEvent()), and the other events (ie. START/ENDED/DROP) are
     * either dispatched by WM core to known windows, or internally in the view system (ie.
     * ENTERED/EXIT).
     *
     *       For now, we only simulate the ENTERED/EXIT, and can extend this later as needed.
     */
    var dragListener: View.OnDragListener? = null

    /** Creates a touch zone. */
    fun inflate(rootLeash: SurfaceControl, displayId: Int) {
        clientToken = Binder()

        // Create a new leash under our stage leash.
        val layer =
            SurfaceControl.Builder()
                .setContainerLayer()
                .setName(name)
                .setCallsite("$name [TouchInterceptLayer.inflate]")
                .setParent(rootLeash)
                .build()
        layerLeash = layer

        // Create a new input channel to receive input
        val windowSession = WindowManagerGlobal.getWindowSession()
        val inputTransferToken = InputTransferToken()
        val params = WindowInputChannelParams().apply {
            this.displayId = displayId
            surface = layer
            clientToken = this@TouchInterceptLayer.clientToken
            this.inputTransferToken = inputTransferToken
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            privateFlags = WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY
            inputHandleName = name
        }
        inputChannel = windowSession.grantInputChannel(params)

        // Create an input event receiver and proxy calls to the provided listeners
        inputEventReceiver =
            object : InputEventReceiver(inputChannel, Looper.myLooper()) {
                override fun onInputEvent(event: InputEvent?) {
                    if (event is MotionEvent) {
                        touchListener?.onTouch(null, event)
                    }
                    super.onInputEvent(event)
                }

                override fun onDragEvent(isExiting: Boolean, x: Float, y: Float, displayId: Int) {
                    val dragEvent =
                        DragEvent.obtain(
                            if (isExiting) DragEvent.ACTION_DRAG_EXITED
                            else DragEvent.ACTION_DRAG_ENTERED,
                            x,
                            y,
                            0f /* offsetX */,
                            0f /* offsetY */,
                            displayId,
                            0 /* flags */,
                            null /* localState */,
                            null /* description */,
                            null /* data */,
                            null /* dragSurface */,
                            null /* dragAndDropPermissions */,
                            false, /* result */
                        )
                    dragListener?.onDrag(null, dragEvent)
                }
            }
    }

    /** Shows the `layerLeash` with a given zOrder. */
    fun show(zOrder: Int) {
        if (layerLeash != null) {
            SurfaceControl.Transaction()
                .setLayer(layerLeash!!, zOrder)
                .setCrop(layerLeash!!, null)
                .show(layerLeash!!)
                .apply()
        }
    }

    /** Hides the `layerLeash`. */
    fun hide() {
        if (layerLeash != null) {
            SurfaceControl.Transaction().hide(layerLeash!!).apply()
        }
    }

    /** Releases the touch zone when it's no longer needed. */
    fun release() {
        inputEventReceiver?.dispose()
        inputEventReceiver = null
        if (layerLeash != null) {
            val t = SurfaceControl.Transaction()
            t.remove(layerLeash!!)
            t.apply()
            layerLeash = null
        }
        if (clientToken != null) {
            val windowSession = WindowManagerGlobal.getWindowSession()
            windowSession.remove(clientToken)
            clientToken = null
        }
        touchListener = null
        dragListener = null
    }

    companion object {
        private const val TAG: String = "TouchInterceptLayer"
    }
}
