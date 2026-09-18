/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the a License.
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

import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualGamepadMotionEvent;

/**
 * Interface for a virtual gamepad. Provides APIs for the caller to inject gamepad-related input
 * events to the device, by calling the system server.
 *
 * @hide
 */
interface IVirtualGamepad {

    /**
     * Remove the input device from the system.
     */
    @EnforcePermission("INJECT_EVENTS")
    void close();

    /**
     * Inject a virtual gamepad key event.
     */
    @EnforcePermission("INJECT_EVENTS")
    boolean sendGamepadKeyEvent(in VirtualKeyEvent event);

    /**
     * Inject a virtual gamepad motion event.
     */
    @EnforcePermission("INJECT_EVENTS")
    boolean sendGamepadMotionEvent(in VirtualGamepadMotionEvent event);
}
