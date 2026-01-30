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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.ArrayMap;
import android.util.SparseBooleanArray;

import com.android.internal.annotations.GuardedBy;

/**
 * This class manages app accesses to serial devices.
 */
class SerialUserAccessManager {
    private final Context mContext;

    /**
     * A list of serial port names configured in the internal configuration. Apps with
     * {@link Manifest.permission#SERIAL_PORT} are granted accesses to them automatically.
     */
    private final String[] mPortsInConfig;

    /**
     * Mapping of serial port names to list of UIDs with accesses for the device Each entry last
     * until device is disconnected. There are accesses granted via access request dialog.
     */
    @GuardedBy("mLock")
    private final ArrayMap<String, SparseBooleanArray> mPortAccessMap = new ArrayMap<>();

    private final Object mLock = new Object();

    SerialUserAccessManager(Context context, String[] portsInConfig) {
        mContext = context;
        mPortsInConfig = portsInConfig;
    }

    void requestAccess(
            String requestedPortName, int pid, int uid, AccessToPortDecidedCallback callback) {
        if (hasAccessToPort(requestedPortName, pid, uid)) {
            callback.onAccessToPortDecided(requestedPortName, pid, uid, /* granted */ true);
            return;
        }

        // TODO(b/429003921): Implement access request dialog.
        callback.onAccessToPortDecided(requestedPortName, pid, uid, /* granted */ false);
    }

    private boolean hasAccessToPort(String requestedPortName, int pid, int uid) {
        if (canGrantPortAccessAutomatically(requestedPortName, pid, uid)) {
            return true;
        }

        synchronized (mLock) {
            final SparseBooleanArray uidToGranted = mPortAccessMap.get(requestedPortName);
            if (uidToGranted == null) {
                return false;
            }
            return uidToGranted.get(uid);
        }
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private boolean canGrantPortAccessAutomatically(String requestedPortName, int pid, int uid) {
        if (mContext.checkPermission(Manifest.permission.SERIAL_PORT, pid, uid)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        for (int i = 0; i < mPortsInConfig.length; ++i) {
            if (requestedPortName.equals(mPortsInConfig[i])) {
                return true;
            }
        }

        return false;
    }

    void onPortRemoved(String name) {
        synchronized (mLock) {
            mPortAccessMap.remove(name);
        }
    }

    interface AccessToPortDecidedCallback {
        void onAccessToPortDecided(String requestedPort, int pid, int uid, boolean granted);
    }
}
