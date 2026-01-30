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

package android.hardware.serial;

import android.hardware.serial.SerialPortInfo;
import android.hardware.serial.ISerialPortListener;
import android.hardware.serial.ISerialPortResponseCallback;

/** @hide */
interface ISerialManager {
    /** Returns a list of all available serial ports */
    List<SerialPortInfo> getSerialPorts();

    /** Registers a listener to monitor serial port connections and disconnections. */
    void registerSerialPortListener(in ISerialPortListener listener);

    /** Unregisters a listener to monitor serial port connections and disconnections. */
    void unregisterSerialPortListener(in ISerialPortListener listener);

    /**
     * Requests opening a file descriptor for the serial port.
     *
     * @param flags       open flags {@code SerialPort.OPEN_FLAG_*} that define read/write mode and
     *                    other options.
     * @param exclusive   whether the app needs exclusive access with TIOCEXCL(2const)
     * @param packageName the package name of the calling application
     * @param callback    the receiver of the operation result.
     * @throws IllegalArgumentException if the set of flags is not correct.
     */
    void requestOpen(in String portName, in int flags, in boolean exclusive, in String packageName,
            in ISerialPortResponseCallback callback);
}
