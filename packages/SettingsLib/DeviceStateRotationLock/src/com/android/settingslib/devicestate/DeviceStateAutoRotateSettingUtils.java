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
package com.android.settingslib.devicestate;

import static android.hardware.devicestate.DeviceState.PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_CLOSED;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.devicestate.DeviceStateManager;

import com.android.internal.R;

public final class DeviceStateAutoRotateSettingUtils {
    private DeviceStateAutoRotateSettingUtils() {
    }

    /** Returns true if device-state based rotation lock settings are enabled. */
    public static boolean isDeviceStateRotationLockEnabled(@NonNull Context context) {
        final DeviceStateManager deviceStateManager = context.getSystemService(
                DeviceStateManager.class);
        if (deviceStateManager == null) return false;

        return context.getResources().getStringArray(
                R.array.config_perDeviceStateRotationLockDefaults).length > 0
                && isAutoRotateSupported(context) && hasFoldedState(deviceStateManager);

    }

    private static boolean isAutoRotateSupported(@NonNull Context context) {
        return context.getResources().getBoolean(R.bool.config_supportAutoRotation);
    }

    private static boolean hasFoldedState(DeviceStateManager deviceStateManager) {
        return deviceStateManager.getSupportedDeviceStates().stream()
                .anyMatch(state -> state.hasProperty(
                        PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_CLOSED));
    }
}
