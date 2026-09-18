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

package android.hardware.usb;

import android.hardware.usb.IUsbOperationInternal;
import android.hardware.usb.InternalAuthorizationPinModeReason;
import android.hardware.usb.InternalUsbDataSignalDisableReason;
import android.hardware.usb.InternalPciTunnelControlDisableReason;

/** @hide */
interface IUsbManagerInternal {

    /* Disable/enable USB data on a port for System Service callers. */
    boolean enableUsbDataSignal(boolean enable, InternalUsbDataSignalDisableReason disableReason);

    /* Disallow/allow PCI tunnel control for System Service callers. */
    boolean allowPciTunnelControl(boolean allow,
            InternalPciTunnelControlDisableReason disableReason);

    /**
     * Pins the authorization system state to a specific mode for this boot.
     *
     * USB Host mode authorization uses a system policy that depends on the system state
     * in order to determine which USB devices are allowed to connect to the system.
     *
     * This can be restrictive in certain environments (such as Repair and Factory images) so
     * this API is exposed to allow those modes to be pinned to a specific system state. This
     * behavior can introduce security gaps so it is the responsibility of the caller to
     * ensure user data is appropriately protected while this is active.
     *
     * @param reason - A pre-defined list of users of this api that map to specific states.
     *
     * @return True if a mode was pinned successfully, false otherwise.
     */
    boolean pinAuthorizationMode(InternalAuthorizationPinModeReason reason);
}

