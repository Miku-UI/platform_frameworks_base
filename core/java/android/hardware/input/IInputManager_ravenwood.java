/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static android.platform.test.ravenwood.RavenwoodExperimentalApiChecker.onExperimentalApiCalled;

import static com.android.ravenwood.common.RavenwoodInternalUtils.getRavenwoodRuntimePath;

import android.platform.test.ravenwood.RavenwoodNativeLoader;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;

import java.nio.file.Path;

/**
 * Minimal implementation of {@link IInputManager} for Ravenwood.
 */
public class IInputManager_ravenwood extends IInputManager.Default {

    private final InputDevice mFakeInputDevice;

    public IInputManager_ravenwood() {
        var keyMapFile = Path.of(getRavenwoodRuntimePath(), "keymaps", "Virtual.kcm");
        var virtualKcm = RavenwoodNativeLoader.createKeyCharacterMap(
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                keyMapFile.toAbsolutePath().toString());
        mFakeInputDevice = new InputDevice.Builder()
                .setId(KeyCharacterMap.VIRTUAL_KEYBOARD)
                .setKeyCharacterMap(virtualKcm)
                .build();
    }

    @Override
    public int[] getInputDeviceIds() {
        return new int[]{KeyCharacterMap.VIRTUAL_KEYBOARD};
    }

    @Override
    public InputDevice getInputDevice(int deviceId) {
        if (deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) {
            return mFakeInputDevice;
        }
        return null;
    }

    @Override
    public String getVelocityTrackerStrategy() {
        return null;
    }

    @Override
    public boolean injectInputEventToTarget(InputEvent ev, int mode, int targetUid) {
        onExperimentalApiCalled(2);
        return false;
    }

    @Override
    public void registerInputDevicesChangedListener(IInputDevicesChangedListener listener) {
    }
}
