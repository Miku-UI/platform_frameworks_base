/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;
import android.os.RemoteException;
import android.util.Log;
import android.view.InputDevice;

import java.io.Closeable;

/**
 * The base class for all virtual input devices such as VirtualKeyboard, VirtualMouse.
 * This implements the shared functionality such as closing the device and keeping track of
 * identifiers.
 *
 * @hide
 */
abstract class VirtualInputDevice implements Closeable {

    protected static final String TAG = "VirtualInputDevice";

    protected final IVirtualInputDevice mVirtualInputDevice;

    protected final VirtualInputDeviceConfig mConfig;

    /** @hide */
    VirtualInputDevice(VirtualInputDeviceConfig config, IVirtualInputDevice virtualDevice) {
        mConfig = config;
        mVirtualInputDevice = virtualDevice;
    }

    /**
     * Returns the ID of the underlying input device.
     *
     * @return The input device id of this device.
     * @see InputDevice#getId()
     * @hide
     */
    @FlaggedApi(com.android.hardware.input.Flags.FLAG_CREATE_VIRTUAL_KEYBOARD_API)
    @SystemApi
    public int getInputDeviceId() {
        try {
            return mVirtualInputDevice.getInputDeviceId();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public void close() {
        Log.d(TAG, "Closing virtual input device " + mConfig.getInputDeviceName());
        try {
            mVirtualInputDevice.close();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public String toString() {
        return mConfig.toString();
    }
}
