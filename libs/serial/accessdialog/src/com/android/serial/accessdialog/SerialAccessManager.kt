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

package com.android.serial.accessdialog

import android.Manifest
import android.annotation.CallbackExecutor
import android.annotation.RequiresPermission
import android.hardware.serial.SerialPortListener
import android.os.IBinder
import java.util.concurrent.Executor

/**
 * This interface contains methods from [SerialManager] that are used by [AccessDialogHelper],
 * it is used for mocking [SerialManager] in the tests.
 */
interface SerialAccessManager {
    /**
     * Register a listener to monitor serial port connections and disconnections.
     *
     * @throws IllegalStateException if this listener has already been registered.
     */
    fun registerSerialPortListener(
        @CallbackExecutor executor: Executor,
        listener: SerialPortListener
    )

    /**
     * Unregister a listener that monitored serial port connections and disconnections.
     */
    fun unregisterSerialPortListener(listener: SerialPortListener)

    /**
     * Grants a specific UID access to a serial port.
     *
     * @param serialPort The name of the serial port.
     * @param uid The user ID to grant access to.
     * @param persistent {@code true} if the grant doesn't expire on disconnection and must be
     *                   revoked in system settings; {@code false} otherwise
     * @param token An optional token associated with the grant.
     */
    @RequiresPermission(Manifest.permission.MANAGE_SERIAL_PORTS)
    fun grantSerialPortAccess(serialPort: String, uid: Int, persistent: Boolean, token: IBinder?)

    /**
     * Revokes a specific UID's access to a serial port.
     *
     * @param serialPort The name of the serial port.
     * @param uid The user ID to revoke access from.
     * @param persistent {@code true} if future access requests will be automatically denied until
     *                   users changes the access in system settings
     * @param token An optional token associated with the revocation.
     */
    @RequiresPermission(Manifest.permission.MANAGE_SERIAL_PORTS)
    fun revokeSerialPortAccess(serialPort: String, uid: Int, persistent: Boolean, token: IBinder?)
}
