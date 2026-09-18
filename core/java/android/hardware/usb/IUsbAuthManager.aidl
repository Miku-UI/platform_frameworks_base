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

import android.hardware.usb.IUsbAuthEventsListener;
import android.hardware.usb.UsbAuthDeviceInfo;
import android.hardware.usb.UsbAuthorizationStatus;
import android.hardware.usb.UsbAuthorizationSystemState;
import java.util.List;

/** @hide */
interface IUsbAuthManager {
    /**
     * Gets a list of all currently authorized USB devices.
     *
     * @return A List of UsbAuthDeviceInfo objects that are authorized.
     */
    List<UsbAuthDeviceInfo> getAuthorizedUsbDevices();

    /**
     * Gets a list of USB devices that are currently in a deferred state.
     * These devices might be waiting for user input or further policy evaluation.
     * A deferred device has the state `DENIED_AND_DEFERRED`.
     *
     * @return A List of UsbAuthDeviceInfo objects that are deferred.
     */
    List<UsbAuthDeviceInfo> getDeferredUsbDevices();

    /**
     * Gets a list of USB devices that require explicit authorization from the user
     * (i.e., "ask devices"). These are devices that are not yet
     * authorized and are not internal or automatically handled.
     * These devices are in DENIED state.
     *
     * @return A List of UsbAuthDeviceInfo objects awaiting explicit authorization.
     */
    List<UsbAuthDeviceInfo> getDevicesAwaitingAuthorization();

    /**
     * Gets a list of USB devices that require authorization from the client
     * (i.e., "allow-persisted devices"). These are devices that are not yet
     * authorized and are waiting for the client to call setAuthorizationStatus.
     *
     * Unlike devices with the "Ask" policy action, the client should only
     * authorize devices with "AllowPersisted" if a user had previously
     * authorized the device and chose to remember that decision.
     *
     * @return A List of UsbAuthDeviceInfo objects awaiting authorization.
     */
    List<UsbAuthDeviceInfo> getDevicesAwaitingPersistedAuthorization();

    /**
     * Gets the authorization status for a specific USB device.
     *
     * @param device The UsbAuthDeviceInfo to check.
     * @return The authorization status of the device.
     */
    UsbAuthorizationStatus getAuthorizationStatus(in UsbAuthDeviceInfo device);

    /**
     * Sets the authorization status for a specific USB device.
     *
     * @param device The UsbAuthDeviceInfo to update.
     * @param status The new authorization status to set.
     */
    void setAuthorizationStatus(in UsbAuthDeviceInfo device, in UsbAuthorizationStatus status);

    /**
     * Inform {@link IUsbAuthManager} about the current system state. This is a stateful
     * event that will cause policy evaluations.
     */
    void setSystemState(in UsbAuthorizationSystemState state);

    /**
     * Registers a callback to provide user interaction for authorization.
     *
     * @param listener - Binder interface to add to listeners list.
     * @return True if listener was registered successfully or false.
     */
    boolean registerForUsbAuthorizationEvents(in IUsbAuthEventsListener listener);

    /**
     * Unregisters a callback for authorization events.
     *
     * @param listener - Binder interface to remove from listeners list.
     */
    void unregisterForUsbAuthorizationEvents(in IUsbAuthEventsListener listener);
}
