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

package com.android.server.input.data;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.input.InputDeviceIdentifier;
import android.util.ArrayMap;

import java.util.Map;

/**
 * A container for holding the button and axis remappings for a specific input device.
 *
 * @hide
 */
public record InputDeviceRemappingData(
        InputDeviceIdentifier deviceIdentifier,
        Map<Integer, Integer> buttonRemappingMap,
        Map<Integer, Integer> buttonToAxisRemappingMap,
        Map<Integer, Integer> axisRemappingMap) {
    /**
     * A copy constructor to create a deep copy of an {@link InputDeviceRemappingData} object.
     *
     * @param original The object to create a copy of.
     */
    public InputDeviceRemappingData(@NonNull InputDeviceRemappingData original) {
        this(
                new InputDeviceIdentifier(
                        original.deviceIdentifier().getDescriptor(),
                        original.deviceIdentifier().getVendorId(),
                        original.deviceIdentifier().getProductId()
                ),
                deepCopy(original.buttonRemappingMap()),
                deepCopy(original.buttonToAxisRemappingMap()),
                deepCopy(original.axisRemappingMap()));
    }

    private static @Nullable ArrayMap<Integer, Integer> deepCopy(
            @Nullable Map<Integer, Integer> original) {
        if (original == null) {
            return null;
        }
        ArrayMap<Integer, Integer> copy = new ArrayMap<>();
        copy.putAll(original);
        return copy;
    }
}
