/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.hardware.usb;

import android.hardware.usb.UsbAuthDeviceInfo;
import android.hardware.usb.UsbAuthorizationStatus;
import android.hardware.usb.UsbAuthorizationSystemState;

/** @hide */
oneway interface IUsbAuthEventsListener {
    /**
     * Callback sent when device needs user interaction to authorize.
     *
     * When a device matches an "Ask" policy, USB authorization requires user
     * interaction for an authorization decision to be made. After receiving this
     * callback, the recipient must send back the response via
     * {@link IUsbAuthManager#setAuthorizationStatus}.
     *
     * @param device - The USB device that needs an authorization interaction.
     */
    void onDeviceAskForAuthorization(in UsbAuthDeviceInfo device);

    /**
     * Callback sent when device should be authorized if persisted.
     *
     * The authorization client can choose to store previous decisions from the
     * Ask policy. After receiving this callback, the recipient must send back
     * the response via {@link IUsbAuthManager#setAuthorizationStatus} with the
     * historical decision (if stored; otherwise deny) for this device.
     *
     * @param device - The USB device that needs an authorization interaction.
     */
    void onDeviceCheckPersistedAuthorization(in UsbAuthDeviceInfo device);

    /**
     * Sent when an authorization decision is complete for a device.
     *
     * @param device - The USB device that had an authorization action taken.
     * @param status - The current status of the device.
     * @param systemState - The current system state on which this action took place.
     */
    void onDeviceAuthorizationStatusChanged(in UsbAuthDeviceInfo device,
        in UsbAuthorizationStatus status, in UsbAuthorizationSystemState systemState);
}
