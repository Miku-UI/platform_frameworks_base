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

import android.hardware.input.VirtualTouchEvent;

/**
 * Interface for a virtual touchscreen. Provides APIs for the caller to inject touch input events to
 * the device, by calling the system server.
 *
 * @hide
 */
interface IVirtualTouchscreen {

    /**
     * Removes the input device from the framework.
     */
    @RequiresNoPermission
    void close();

    /**
     * Returns the ID of the device corresponding to this virtual input device, as registered with
     * the input framework.
     */
    @RequiresNoPermission
    int getInputDeviceId();

    /**
     * Injects a virtual touch event.
     */
    @RequiresNoPermission
    boolean sendTouchEvent(in VirtualTouchEvent event);
}
