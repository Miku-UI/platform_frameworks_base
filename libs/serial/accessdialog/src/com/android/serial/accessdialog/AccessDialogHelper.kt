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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.serial.SerialManager
import android.hardware.serial.SerialPort
import android.hardware.serial.SerialPortListener
import android.os.Handler
import android.os.HandlerExecutor
import android.os.Process
import android.util.Log
import com.android.serial.accessdialog.AccessDialogActivity.Companion.TAG

/**
 * Helper class to separate model and view of serial permission and confirm dialogs.
 */
class AccessDialogHelper(
    val context: Context,
    val intent: Intent,
    val serialAccessManager: SerialAccessManager
) {
    val token = requireNotNull(intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)) {
        "Missing ${SerialManager.EXTRA_REQUEST_TOKEN} parameter"
    }
    val requestedPort = requireNotNull(intent.getStringExtra(SerialManager.EXTRA_PORT)) {
        "Missing ${SerialManager.EXTRA_PORT} parameter"
    }
    val packageName = requireNotNull(intent.getStringExtra(SerialManager.EXTRA_PACKAGE_NAME)) {
        "Missing ${SerialManager.EXTRA_PACKAGE_NAME} parameter"
    }
    val uid = intent.getIntExtra(SerialManager.EXTRA_UID, Process.INVALID_UID).also {
        require(it != Process.INVALID_UID) { "Missing ${SerialManager.EXTRA_UID} parameter" }
    }
    val appLabel: CharSequence

    private lateinit var listener: SerialPortDisconnectedListener

    var granted = false
    var persistent = false

    init {
        val pm = context.packageManager
        appLabel = try {
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.MATCH_ANY_USER)
            appInfo.loadLabel(pm)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Can't find package $packageName", e)
            packageName
        } catch (e: RuntimeException) {
            Log.e(TAG, "Can't access label for package $packageName", e)
            packageName
        }
    }

    fun registerSerialPortDisconnectedCallback(callback: Runnable) {
        listener = SerialPortDisconnectedListener(callback)
        serialAccessManager.registerSerialPortListener(
            HandlerExecutor(Handler.getMain()),
            listener
        )
    }

    fun unregisterSerialPortDisconnectedCallback() {
        serialAccessManager.unregisterSerialPortListener(listener)
    }

    fun sendResult() {
        if (granted) {
            serialAccessManager.grantSerialPortAccess(requestedPort, uid, persistent, token)
        } else {
            serialAccessManager.revokeSerialPortAccess(requestedPort, uid, persistent, token)
        }
    }

    inner class SerialPortDisconnectedListener(val callback: Runnable) :
            SerialPortListener {
        override fun onSerialPortConnected(port: SerialPort) {}

        override fun onSerialPortDisconnected(port: SerialPort) {
            if (requestedPort != port.name) {
                return
            }
            callback.run()
        }
    }
}
