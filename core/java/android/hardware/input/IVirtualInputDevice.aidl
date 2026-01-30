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

package android.hardware.input;

import android.graphics.PointF;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualMouseButtonEvent;
import android.hardware.input.VirtualMouseRelativeEvent;
import android.hardware.input.VirtualMouseScrollEvent;
import android.hardware.input.VirtualRotaryEncoderScrollEvent;
import android.hardware.input.VirtualStylusButtonEvent;
import android.hardware.input.VirtualStylusMotionEvent;
import android.hardware.input.VirtualTouchEvent;

/**
 * Interface for a virtual input device to communication between the system server and the process
 * of owner of that device.
 *
 * @hide
 */
interface IVirtualInputDevice {

    /**
     * Removes the input device from the framework.
     */
    void close();

    /**
     * Returns the ID of the device corresponding to this virtual input device, as registered with
     * the input framework.
     */
    int getInputDeviceId();

    /**
     * Returns the ID of the display that this virtual input device is associated with, or
     * {@code INVALID_DISPLAY} if not associated with any display.
     */
    int getAssociatedDisplayId();

    /**
     * Injects a virtual dpad key event.
     */
    boolean sendDpadKeyEvent(in VirtualKeyEvent event);

    /**
     * Injects a virtual keyboard key event.
     */
    boolean sendKeyEvent(in VirtualKeyEvent event);

    /**
     * Injects a virtual mouse button event.
     */
    boolean sendMouseButtonEvent(in VirtualMouseButtonEvent event);

    /**
     * Injects a virtual mouse relative event.
     */
    boolean sendMouseRelativeEvent(in VirtualMouseRelativeEvent event);

    /**
     * Injects a virtual mouse scroll event.
     */
    boolean sendMouseScrollEvent(in VirtualMouseScrollEvent event);

    /**
    * Injects a virtual touchscreen / touchpad touch event.
    */
    boolean sendTouchEvent(in VirtualTouchEvent event);

    /**
     * Injects a virtual stylus motion event.
     */
    boolean sendStylusMotionEvent(in VirtualStylusMotionEvent event);

    /**
     * Injects a virtual stylus button event.
     */
    boolean sendStylusButtonEvent(in VirtualStylusButtonEvent event);

    /**
     * Injects a virtual rotary encoder scroll event.
     */
    boolean sendRotaryEncoderScrollEvent(in VirtualRotaryEncoderScrollEvent event);

    /**
     * Returns the current cursor position of the mouse corresponding to this device, in the
     * physical display coordinates.
     */
    PointF getCursorPositionInPhysicalDisplay();

    /**
     * Returns the current cursor position of the mouse corresponding to this device, in the
     * logical display coordinates.
     */
    PointF getCursorPositionInLogicalDisplay();
}
