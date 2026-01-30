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

package com.android.server.serial;

import android.hardware.serialservice.SerialPortInfo;

import java.util.function.Predicate;

/** Filters serial devices that are exposed to the user. */
class SerialDeviceFilter implements Predicate<SerialPortInfo> {

    private static final String SERIAL_DRIVER_TYPE = "serial";

    private static final String BUILT_IN_SERIAL_SUBSYSTEM = "serial-base";

    public boolean test(SerialPortInfo info) {
        // Expose only devices having "serial" driver type.
        return info.driverType.equals(SERIAL_DRIVER_TYPE)
                // Keep built-in UARTs hidden (for security reasons).
                && !info.subsystem.equals(BUILT_IN_SERIAL_SUBSYSTEM);
    }
}
