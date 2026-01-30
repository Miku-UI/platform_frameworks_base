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
import android.os.ParcelFileDescriptor;

/**
* Interface for getting a response to ISerialManager.requestOpen().
*
* @hide
*/
oneway interface ISerialPortResponseCallback {
    /** Error codes for {@link #onError}. */
    @Backing(type="int")
    enum ErrorCode {
        // Serial port with the given name does not exist.
        ERROR_PORT_NOT_FOUND = 0,
        // SecurityException due to access denied.
        ERROR_ACCESS_DENIED = 1,
        // Error while opening the serial port.
        ERROR_OPENING_PORT = 2,
    }

    /**
     * Called when the serial port has been opened successfully.
     *
     * @param port The port
     * @param fileDescriptor The file descriptor of the pseudo-file.
     */
    void onResult(in SerialPortInfo port, in ParcelFileDescriptor fileDescriptor);

    /**
     * Called when the serial port opening failed.
     *
     * @param errorCode The error code indicating the type of error that occurred.
     * @param message Additional text information about the error.
     */
    void onError(in ErrorCode errorCode, in String message);
}
