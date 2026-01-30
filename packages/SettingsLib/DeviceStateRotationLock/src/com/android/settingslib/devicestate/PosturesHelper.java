/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.hardware.devicestate.DeviceState.PROPERTY_FEATURE_REAR_DISPLAY;
import static android.hardware.devicestate.DeviceState.PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_INNER_PRIMARY;
import static android.hardware.devicestate.DeviceState.PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_OUTER_PRIMARY;
import static android.hardware.devicestate.DeviceState.PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_HALF_OPEN;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_FOLDED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_HALF_FOLDED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_UNFOLDED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_UNKNOWN;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import android.content.Context;
import android.hardware.devicestate.DeviceState;
import android.hardware.devicestate.DeviceStateManager;
import android.hardware.devicestate.feature.flags.Flags;
import android.provider.Settings.Secure.DeviceStateRotationLockKey;

import com.android.internal.R;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Helps to convert between device state and posture. */
public class PosturesHelper {

    private final Map<Integer, Set<Integer>> mPostures;

    public PosturesHelper(Context context, DeviceStateManager deviceStateManager) {
        if (deviceStateManager != null && Flags.deviceStatePropertyMigration()) {
            mPostures = deviceStateManager.getSupportedDeviceStates().stream()
                    .filter(state -> this.toPosture(state) != DEVICE_STATE_ROTATION_KEY_UNKNOWN)
                    .collect(groupingBy(this::toPosture,
                            mapping(DeviceState::getIdentifier, toSet())));
            return;
        }

        mPostures = new HashMap<>();
        final int[] foldedDeviceStatesConfig = context.getResources().getIntArray(
                R.array.config_foldedDeviceStates);
        final Set<Integer> foldedDeviceStates = new HashSet<>();
        for (int state : foldedDeviceStatesConfig) {
            foldedDeviceStates.add(state);
        }
        final int[] halfFoldedDeviceStatesConfig = context.getResources().getIntArray(
                R.array.config_foldedDeviceStates);
        final Set<Integer> halfFoldedDeviceStates = new HashSet<>();
        for (int state : halfFoldedDeviceStatesConfig) {
            halfFoldedDeviceStates.add(state);
        }
        final int[] unfoldedDeviceStatesConfig = context.getResources().getIntArray(
                R.array.config_openDeviceStates);
        final Set<Integer> unfoldedDeviceStates = new HashSet<>();
        for (int state : unfoldedDeviceStatesConfig) {
            unfoldedDeviceStates.add(state);
        }
        final int[] rearDisplayDeviceStatesConfig = context.getResources().getIntArray(
                R.array.config_rearDisplayDeviceStates);
        final Set<Integer> rearDisplayDeviceStates = new HashSet<>();
        for (int state : rearDisplayDeviceStatesConfig) {
            rearDisplayDeviceStates.add(state);
        }

        mPostures.put(DEVICE_STATE_ROTATION_KEY_FOLDED, foldedDeviceStates);
        mPostures.put(DEVICE_STATE_ROTATION_KEY_HALF_FOLDED,
                halfFoldedDeviceStates);
        mPostures.put(DEVICE_STATE_ROTATION_KEY_UNFOLDED, unfoldedDeviceStates);
        mPostures.put(DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY,
                rearDisplayDeviceStates);

    }

    /**
     * Returns posture mapped to the given {@link deviceState}, returns
     * {@link DEVICE_STATE_ROTATION_KEY_UNKNOWN} if no posture found.
     */
    @DeviceStateRotationLockKey
    public int deviceStateToPosture(int deviceState) {
        for (Map.Entry<Integer, Set<Integer>> entry : mPostures.entrySet()) {
            if (entry.getValue().contains(deviceState)) {
                return entry.getKey();
            }
        }
        return DEVICE_STATE_ROTATION_KEY_UNKNOWN;
    }

    /**
     * Returns device state mapped to the given {@link posture}, returns null if no device state
     * found.
     */
    public Integer postureToDeviceState(@DeviceStateRotationLockKey int posture) {
        Set<Integer> deviceStates = mPostures.get(posture);
        if (deviceStates != null && !deviceStates.isEmpty()) {
            return deviceStates.stream().findFirst().orElse(null);
        }
        return null;
    }

    /**
     * Maps a {@link DeviceState} to the corresponding {@link DeviceStateRotationLockKey} value
     * based on the properties of the state.
     */
    @DeviceStateRotationLockKey
    private int toPosture(DeviceState deviceState) {
        if (deviceState.hasProperty(PROPERTY_FEATURE_REAR_DISPLAY)) {
            return DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY;
        }
        if (deviceState.hasProperty(PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_OUTER_PRIMARY)) {
            return DEVICE_STATE_ROTATION_KEY_FOLDED;
        }
        if (deviceState.hasProperties(PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_INNER_PRIMARY,
                PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_HALF_OPEN)) {
            return DEVICE_STATE_ROTATION_KEY_HALF_FOLDED;
        }
        if (deviceState.hasProperty(PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_INNER_PRIMARY)) {
            return DEVICE_STATE_ROTATION_KEY_UNFOLDED;
        }
        return DEVICE_STATE_ROTATION_KEY_UNKNOWN;

    }
}
