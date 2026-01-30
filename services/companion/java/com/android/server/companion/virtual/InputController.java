/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.companion.virtual;

import android.annotation.NonNull;
import android.content.AttributionSource;
import android.content.Context;
import android.hardware.input.IVirtualInputDevice;
import android.hardware.input.InputManager;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualMouseConfig;
import android.hardware.input.VirtualNavigationTouchpadConfig;
import android.hardware.input.VirtualRotaryEncoderConfig;
import android.hardware.input.VirtualStylusConfig;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.view.WindowManager;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.expresslog.Counter;
import com.android.server.LocalServices;
import com.android.server.input.InputManagerInternal;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;

/** Controls virtual input devices, including device lifecycle and event dispatch. */
class InputController {

    private final Object mLock = new Object();

    /* Token -> file descriptor associations. */
    @GuardedBy("mLock")
    private final ArrayMap<IBinder, IVirtualInputDevice> mInputDevices = new ArrayMap<>();

    private final InputManagerInternal mInputManagerInternal;
    private final InputManager mInputManager;
    private final WindowManager mWindowManager;
    private final AttributionSource mAttributionSource;
    private final InputDeviceListener mInputDeviceListener = new InputDeviceListener();

    @VisibleForTesting
    InputController(@NonNull Context context, AttributionSource attributionSource) {
        mInputManagerInternal = LocalServices.getService(InputManagerInternal.class);
        mInputManager = context.getSystemService(InputManager.class);
        mWindowManager = context.getSystemService(WindowManager.class);
        mAttributionSource = attributionSource;
        mInputManager.registerInputDeviceListener(
                mInputDeviceListener, context.getMainThreadHandler());
    }

    void close() {
        mInputManager.unregisterInputDeviceListener(mInputDeviceListener);
        synchronized (mLock) {
            final Iterator<Map.Entry<IBinder, IVirtualInputDevice>> iterator =
                    mInputDevices.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<IBinder, IVirtualInputDevice> entry = iterator.next();
                final IBinder token = entry.getKey();
                iterator.remove();
                mInputManagerInternal.closeVirtualInputDevice(token);
            }
        }
    }

    IVirtualInputDevice createDpad(@NonNull IBinder token, @NonNull VirtualDpadConfig config) {
        IVirtualInputDevice device = mInputManagerInternal.createVirtualDpad(token, config);
        Counter.logIncrementWithUid("virtual_devices.value_virtual_dpad_created_count",
                mAttributionSource.getUid());
        addDevice(token, device);
        return device;
    }

    IVirtualInputDevice createKeyboard(@NonNull IBinder token,
            @NonNull VirtualKeyboardConfig config) {
        IVirtualInputDevice device = mInputManagerInternal.createVirtualKeyboard(token, config);
        Counter.logIncrementWithUid("virtual_devices.value_virtual_keyboard_created_count",
                mAttributionSource.getUid());
        addDevice(token, device);
        return device;
    }

    IVirtualInputDevice createMouse(@NonNull IBinder token, @NonNull VirtualMouseConfig config) {
        IVirtualInputDevice device = mInputManagerInternal.createVirtualMouse(token, config);
        Counter.logIncrementWithUid("virtual_devices.value_virtual_mouse_created_count",
                mAttributionSource.getUid());
        addDevice(token, device);
        return device;
    }

    IVirtualInputDevice createTouchscreen(@NonNull IBinder token,
            @NonNull VirtualTouchscreenConfig config) {
        IVirtualInputDevice device = mInputManagerInternal.createVirtualTouchscreen(token, config);
        Counter.logIncrementWithUid("virtual_devices.value_virtual_touchscreen_created_count",
                mAttributionSource.getUid());
        addDevice(token, device);
        return device;
    }

    IVirtualInputDevice createNavigationTouchpad(@NonNull IBinder token,
            @NonNull VirtualNavigationTouchpadConfig config) {
        IVirtualInputDevice device =
                mInputManagerInternal.createVirtualNavigationTouchpad(token, config);
        Counter.logIncrementWithUid(
                "virtual_devices.value_virtual_navigationtouchpad_created_count",
                mAttributionSource.getUid());
        addDevice(token, device);
        return device;
    }

    IVirtualInputDevice createStylus(@NonNull IBinder token, @NonNull VirtualStylusConfig config) {
        IVirtualInputDevice device = mInputManagerInternal.createVirtualStylus(token, config);
        Counter.logIncrementWithUid("virtual_devices.value_virtual_stylus_created_count",
                mAttributionSource.getUid());
        addDevice(token, device);
        return device;
    }

    IVirtualInputDevice createRotaryEncoder(@NonNull IBinder token,
            @NonNull VirtualRotaryEncoderConfig config) {
        IVirtualInputDevice device =
                mInputManagerInternal.createVirtualRotaryEncoder(token, config);
        Counter.logIncrementWithUid("virtual_devices.value_virtual_rotary_created_count",
                mAttributionSource.getUid());
        addDevice(token, device);
        return device;
    }

    void setShowPointerIcon(boolean visible, int displayId) {
        mInputManagerInternal.setPointerIconVisible(visible, displayId);
    }

    void setMouseScalingEnabled(boolean enabled, int displayId) {
        mInputManager.setMouseScalingEnabled(enabled, displayId);
    }

    void setDisplayEligibilityForPointerCapture(boolean isEligible, int displayId) {
        mInputManagerInternal.setDisplayEligibilityForPointerCapture(displayId, isEligible);
    }

    void setDisplayImePolicy(int displayId, @WindowManager.DisplayImePolicy int policy) {
        mWindowManager.setDisplayImePolicy(displayId, policy);
    }

    public void dump(@NonNull PrintWriter fout, String indent) {
        synchronized (mLock) {
            fout.println(indent + "InputController: " + mInputDevices.size() + " input devices");
            for (int i = 0; i < mInputDevices.size(); ++i) {
                fout.println(indent + indent + mInputDevices.valueAt(i));
            }
        }
    }

    @VisibleForTesting
    void addDevice(IBinder token, IVirtualInputDevice device) {
        synchronized (mLock) {
            mInputDevices.put(token, device);
        }
    }

    @VisibleForTesting
    void removeDeviceForTesting(IBinder token) {
        synchronized (mLock) {
            mInputDevices.remove(token);
        }
    }

    boolean isInputDevicePresent(int inputDeviceId) {
        synchronized (mLock) {
            return getTokenForInputDeviceIdLocked(inputDeviceId) != null;
        }
    }

    @GuardedBy("mLock")
    private IBinder getTokenForInputDeviceIdLocked(int inputDeviceId) {
        try {
            for (int i = 0; i < mInputDevices.size(); ++i) {
                IVirtualInputDevice device = mInputDevices.valueAt(i);
                if (device.getInputDeviceId() == inputDeviceId) {
                    return mInputDevices.keyAt(i);
                }
            }
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
        return null;
    }

    private class InputDeviceListener implements InputManager.InputDeviceListener {

        @Override
        public void onInputDeviceAdded(int deviceId) {}

        @Override
        public void onInputDeviceChanged(int deviceId) {}

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            synchronized (mLock) {
                IBinder token = getTokenForInputDeviceIdLocked(deviceId);
                if (token != null) {
                    mInputDevices.remove(token);
                }
            }
        }
    }
}
