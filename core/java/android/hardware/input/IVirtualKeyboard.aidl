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

/**
 * Interface for a virtual keyboard. Provides APIs for the caller to inject keyboard input events to
 * the device, by calling the system server.
 *
 * @hide
 */
interface IVirtualKeyboard {

    /**
     * Inject a virtual key event.
     */
    @RequiresNoPermission
    boolean sendKeyEvent(in VirtualKeyEvent event);

    /**
     * Remove the input device from the system.
     */
    @RequiresNoPermission
    void close();

    /**
     * Returns the ID of the underlying input device.
     */
    @RequiresNoPermission
    int getInputDeviceId();
}
