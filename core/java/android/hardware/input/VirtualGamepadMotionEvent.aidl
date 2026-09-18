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

package android.hardware.input;

/**
 * A motion event to be sent to the virtual gamepad.
 *
 * @hide
 */
parcelable VirtualGamepadMotionEvent {
    /**
     * The X axis value, from MotionEvent.AXIS_X. This is for the left stick horizontal motion.
     * If this axis is not applicable for this event, it should be set to Float.NaN.
     */
    float x;

    /**
     * The Y axis value, from MotionEvent.AXIS_Y. This is for the left stick vertical motion.
     * If this axis is not applicable for this event, it should be set to Float.NaN.
     */
    float y;

    /**
     * The Z axis value, from MotionEvent.AXIS_Z. This is for the right stick horizontal motion.
     * If this axis is not applicable for this event, it should be set to Float.NaN.
     */
    float z;

    /**
     * The RZ axis value, from MotionEvent.AXIS_RZ. This is for the right stick vertical motion.
     * If this axis is not applicable for this event, it should be set to Float.NaN.
     */
    float rz;

    /**
     * The Hat X axis value, from MotionEvent.AXIS_HAT_X. This is for the dpad horizontal motion.
     * If this axis is not applicable for this event, it should be set to Float.NaN.
     */
    float hatX;

    /**
     * The Hat Y axis value, from MotionEvent.AXIS_HAT_Y. This is for the dpad vertical motion.
     * If this axis is not applicable for this event, it should be set to Float.NaN.
     */
    float hatY;

    /**
     * The LTRIGGER axis value, from MotionEvent.AXIS_LTRIGGER. This is for the left trigger.
     * Valid values are from 0.0f (released) to 1.0f (fully pressed).
     * If this axis is not applicable for this event, it should be set to Float.NaN.
     * If the device did not register the trigger axes in the config, this must be set to Float.NaN.
     */
    float lTrigger;

    /**
     * The RTRIGGER axis value, from MotionEvent.AXIS_RTRIGGER. This is for the right trigger.
     * Valid values are from 0.0f (released) to 1.0f (fully pressed).
     * If this axis is not applicable for this event, it should be set to Float.NaN.
     * If the device did not register the trigger axes in the config, this must be set to Float.NaN.
     */
    float rTrigger;

    // The time at which the event occurred, in SystemClock.uptimeMillis() time base, but with
    // nanosecond precision (this will be trimmed to microseconds by linux, though).
    long eventTimeNanos;
}
