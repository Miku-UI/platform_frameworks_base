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

package com.android.server.companion.virtual.computercontrol;

import android.annotation.NonNull;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.computercontrol.IInteractiveMirrorDisplay;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.IVirtualInputDevice;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Binder;
import android.os.RemoteException;
import android.view.Display;

import java.util.Objects;

/**
 * A wrapper around a mirror virtual display and the associated touchscreen.
 */
final class InteractiveMirrorDisplayImpl extends IInteractiveMirrorDisplay.Stub {

    private final VirtualDisplayConfig mVirtualDisplayConfig;
    private final IVirtualDevice mVirtualDevice;
    private final VirtualDisplay mVirtualDisplay;
    private IVirtualInputDevice mVirtualTouchscreen;

    InteractiveMirrorDisplayImpl(VirtualDisplayConfig virtualDisplayConfig,
            IVirtualDevice virtualDevice) throws RemoteException {
        mVirtualDisplayConfig = virtualDisplayConfig;
        mVirtualDevice = virtualDevice;

        // This is used as a death detection token to release the display upon app death. We're in
        // the system process, so this won't happen, but this is OK because we already do death
        // detection in the virtual device based on the app token and closing it will also release
        // the display.
        // The same applies to the input devices. We can't reuse the app token there because it's
        // used as a map key for the virtual input devices.
        IVirtualDisplayCallback virtualDisplayCallback =
                new DisplayManagerGlobal.VirtualDisplayCallback(null, null);
        int displayId = Binder.withCleanCallingIdentity(() ->
                mVirtualDevice.createVirtualDisplay(virtualDisplayConfig, virtualDisplayCallback));
        DisplayManagerGlobal displayManager = DisplayManagerGlobal.getInstance();
        mVirtualDisplay = displayManager.createVirtualDisplayWrapper(
                virtualDisplayConfig, virtualDisplayCallback, displayId);

        createTouchscreen();
    }

    @Override
    public void resize(int width, int height) throws RemoteException {
        mVirtualDisplay.resize(width, height, mVirtualDisplayConfig.getDensityDpi());

        // Since there is no way to resize a touchscreen, just recreate it.
        mVirtualTouchscreen.close();
        createTouchscreen();
    }

    @Override
    public void sendTouchEvent(@NonNull VirtualTouchEvent event) throws RemoteException {
        mVirtualTouchscreen.sendTouchEvent(Objects.requireNonNull(event));
    }

    @Override
    public void close() throws RemoteException {
        mVirtualDisplay.release();
        mVirtualTouchscreen.close();
    }

    private void createTouchscreen() throws RemoteException {
        Display display = mVirtualDisplay.getDisplay();
        // The display may no longer be valid if the session has been closed.
        if (!display.isValid()) {
            return;
        }
        String touchscreenName = display.getName() + "-touchscreen";
        VirtualTouchscreenConfig virtualTouchscreenConfig =
                new VirtualTouchscreenConfig.Builder(display.getWidth(), display.getHeight())
                        .setAssociatedDisplayId(display.getDisplayId())
                        .setInputDeviceName(touchscreenName)
                        .build();
        mVirtualTouchscreen = mVirtualDevice.createVirtualTouchscreen(
                virtualTouchscreenConfig, new Binder(touchscreenName));
    }
}
