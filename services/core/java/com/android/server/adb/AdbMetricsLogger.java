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
package com.android.server.adb;

import android.debug.AdbProtoEnums;

import com.android.internal.util.FrameworkStatsLog;
import com.android.server.adb.AdbPairingThread.AdbWifiPairingResult;

/** Writes events to AdbConnectionChanged atom. */
class AdbMetricsLogger {

    static void logAdbConnectionChanged(int state) {
        FrameworkStatsLog.write(
                FrameworkStatsLog.ADB_CONNECTION_CHANGED,
                0 /* deprecated lastConnectionTime */,
                0 /* deprecated authWindow */,
                state,
                false /* deprecated alwaysAllow */);
    }

    static void logAdbWifiPairingResult(AdbWifiPairingResult adbWifiPairingResult) {
        boolean isSuccess = adbWifiPairingResult.publicKey().isPresent();
        switch (adbWifiPairingResult.adbWifiPairingMethod()) {
            case QR_CODE ->
                    logAdbConnectionChanged(
                            isSuccess
                                    ? AdbProtoEnums.ADB_WIFI_QR_CODE_PAIRING_SUCCEEDED
                                    : AdbProtoEnums.ADB_WIFI_QR_CODE_PAIRING_FAILED);
            case PAIRING_CODE ->
                    logAdbConnectionChanged(
                            isSuccess
                                    ? AdbProtoEnums.ADB_WIFI_PAIRING_CODE_PAIRING_SUCCEEDED
                                    : AdbProtoEnums.ADB_WIFI_PAIRING_CODE_PAIRING_FAILED);
        }
    }
}
