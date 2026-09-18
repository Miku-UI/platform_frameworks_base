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

import android.annotation.RequiresNoPermission;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManagerInternal;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.serial.SerialPort;
import android.hardware.serial.SerialPortInfo;
import android.hardware.serialservice.ISerialManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages and filters the list of serial ports available to applications.
 *
 * <p>This class queries the native serial service for all present ports and filters them based on
 * system configuration (e.g., blocklists), device policies (e.g., USB data signaling), and
 * testing flags (e.g., exposing PTYs). It dynamically updates the list of available ports in
 * response to port hotplug events and device policy changes.
 */
class SerialDeviceFilter {
    private static final String TAG = "SerialDeviceFilter";
    private static final String SERIAL_DRIVER_TYPE = "serial";
    private static final String BUILT_IN_SERIAL_SUBSYSTEM = "serial-base";

    // An array of integers compressing vid in the most significant 16 bits and pid in the
    // least significant 16 bits.
    private final int[] mBlockedUsbIdsInConfig;
    private final DevicePolicyManagerInternal mDevicePolicyManager;
    private final Object mLock;

    // All serial ports obtained from mNativeService keyed by the serial port name (eg. ttyS0).
    @GuardedBy("mLock")
    private final HashMap<String, android.hardware.serialservice.SerialPortInfo> mSerialPorts =
            new HashMap<>();

    // All available serial ports keyed by the serial port name (eg. ttyS0).
    @GuardedBy("mLock")
    private final HashMap<String, SerialPortInfo> mAvailableSerialPorts =
            new HashMap<>();

    @GuardedBy("mLock")
    private FilteredSerialPortListener mFilteredSerialPortListener;

    @GuardedBy("mLock")
    private boolean mIsAccessForbidden;

    @GuardedBy("mLock")
    private boolean mIsPtyExposed;

    SerialDeviceFilter(Context context, String[] blockedUsbIdsInConfig,
            ISerialManager nativeService, Object lock) throws RemoteException {
        mBlockedUsbIdsInConfig = parseUsbIdList(blockedUsbIdsInConfig);
        mLock = lock;
        mDevicePolicyManager = LocalServices.getService(DevicePolicyManagerInternal.class);
        mIsAccessForbidden = isAccessForbidden();

        nativeService.registerSerialPortListener(new SerialPortListener());

        List<android.hardware.serialservice.SerialPortInfo> infos = nativeService.getSerialPorts();
        for (int i = 0; i < infos.size(); i++) {
            var info = infos.get(i);
            mSerialPorts.put(info.name, info);
            if (isAvailable(info)) {
                onSerialPortAdded(info);
            }
        }

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED
                        .equals(intent.getAction())) {
                    synchronized (mLock) {
                        mIsAccessForbidden = isAccessForbidden();
                        refilterSerialPorts();
                    }
                }
            }
        };
        final IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addAction(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        context.registerReceiverAsUser(receiver, UserHandle.ALL, filter, null, null);
    }

    /** Get all available serial ports keyed by the serial port name (eg. ttyS0). */
    @GuardedBy("mLock")
    Map<String, SerialPortInfo> getAvailablePorts() {
        return mAvailableSerialPorts;
    }

    /**
     * Tests if the serial port should be available for the given app, or for some apps.
     *
     * @param info        The serial port to test.
     * @return {@code true} if the port should be available, {@code false} otherwise.
     */
    @VisibleForTesting
    @GuardedBy("mLock")
    boolean isAvailable(android.hardware.serialservice.SerialPortInfo info) {
        // Ports blocked by device policy.
        if (mIsAccessForbidden) {
            return false;
        }

        // Ports blocked by the configuration parameter.
        if (info.vendorId != SerialPort.INVALID_ID && info.productId != SerialPort.INVALID_ID) {
            final int compressedId = compressIds(info.vendorId, info.productId);
            for (int i = 0; i < mBlockedUsbIdsInConfig.length; ++i) {
                if (compressedId == mBlockedUsbIdsInConfig[i]) {
                    return false;
                }
            }
        }

        // Expose only devices having "serial" driver type.
        if (info.driverType.equals(SERIAL_DRIVER_TYPE)
                // Keep built-in UARTs hidden (for security reasons)
                && !info.subsystem.equals(BUILT_IN_SERIAL_SUBSYSTEM)) {
            return true;
        }

        // PTY ports can be exposed via adb shell command for CTS tests.
        return mIsPtyExposed && (info.name.equals("ptmx") || info.name.startsWith("pts/"));
    }

    private boolean isAccessForbidden() {
        // A dedicated Serial Port device policy will be implemented in the next releases.
        // For now, we reuse the USB data signaling policy as a master switch for serial port
        // access for Android Enterprise.
        return mDevicePolicyManager != null && !mDevicePolicyManager.isUsbDataSignalingEnabled();
    }

    @GuardedBy("mLock")
    private void refilterSerialPorts() {
        for (var info : mSerialPorts.values()) {
            boolean wasAvailable = mAvailableSerialPorts.containsKey(info.name);
            boolean isAvailable = isAvailable(info);
            if (wasAvailable && !isAvailable) {
                onSerialPortRemoved(info);
            }
            if (!wasAvailable && isAvailable) {
                onSerialPortAdded(info);
            }
        }
    }

    @GuardedBy("mLock")
    private void onSerialPortAdded(android.hardware.serialservice.SerialPortInfo info) {
        if (mAvailableSerialPorts.containsKey(info.name)) {
            return;
        }
        SerialPortInfo port = new SerialPortInfo(info.name, info.vendorId, info.productId);
        mAvailableSerialPorts.put(info.name, port);
        if (mFilteredSerialPortListener != null) {
            mFilteredSerialPortListener.onSerialPortAdded(port);
        }
    }

    @GuardedBy("mLock")
    private void onSerialPortRemoved(android.hardware.serialservice.SerialPortInfo info) {
        if (!mAvailableSerialPorts.containsKey(info.name)) {
            return;
        }
        mAvailableSerialPorts.remove(info.name);
        if (mFilteredSerialPortListener != null) {
            SerialPortInfo port = new SerialPortInfo(info.name, info.vendorId, info.productId);
            mFilteredSerialPortListener.onSerialPortRemoved(port);
        }
    }

    // For use in `SerialShellCommand`.
    void setIsPtyExposed(boolean isPtyExposed) {
        synchronized (mLock) {
            mIsPtyExposed = isPtyExposed;
            refilterSerialPorts();
        }
    }

    @GuardedBy("mLock")
    void setFilteredSerialPortListener(FilteredSerialPortListener filteredSerialPortListener) {
        mFilteredSerialPortListener = filteredSerialPortListener;
    }

    private static int[] parseUsbIdList(String[] blockedUsbIdsInConfig) {
        final int[] result = new int[blockedUsbIdsInConfig.length];
        for (int i = 0; i < blockedUsbIdsInConfig.length; ++i) {
            final String[] ids = blockedUsbIdsInConfig[i].split(":");
            if (ids.length != 2) {
                Slog.w(TAG, "Unrecognized USB ID " + blockedUsbIdsInConfig[i]);
                // Safe to leave the value to be 0 because that's the USB implementer forum and not
                // any serial ports.
                continue;
            }

            final int vid;
            final int pid;
            try {
                vid = Integer.parseUnsignedInt(ids[0], 16);
                pid = Integer.parseUnsignedInt(ids[1], 16);
            } catch (NumberFormatException e) {
                Slog.w(TAG, "Failed to parse USB ID " + blockedUsbIdsInConfig[i], e);
                continue;
            }

            result[i] = compressIds(vid, pid);
        }
        return result;
    }

    private static int compressIds(int vid, int pid) {
        if (vid < 0 || vid > Short.MAX_VALUE || pid < 0 || pid > Short.MAX_VALUE) {
            Slog.e(TAG, "Invalid USB ID "
                    + Integer.toHexString(vid) + ":" + Integer.toHexString(pid));
            return 0;
        }
        return ((vid << 16) | pid);
    }

    /**
     * Listener for serial port connection events from the native service.
     */
    private class SerialPortListener extends
            android.hardware.serialservice.ISerialPortListener.Stub {

        @RequiresNoPermission
        public void onSerialPortConnected(android.hardware.serialservice.SerialPortInfo info) {
            synchronized (mLock) {
                mSerialPorts.put(info.name, info);
                if (isAvailable(info)) {
                    onSerialPortAdded(info);
                }
            }
        }

        @RequiresNoPermission
        public void onSerialPortDisconnected(android.hardware.serialservice.SerialPortInfo info) {
            synchronized (mLock) {
                mSerialPorts.remove(info.name);
                onSerialPortRemoved(info);
            }
        }
    }

    /** Listener for filtered ports events. */
    interface FilteredSerialPortListener {
        @GuardedBy("mLock")
        void onSerialPortAdded(SerialPortInfo port);

        @GuardedBy("mLock")
        void onSerialPortRemoved(SerialPortInfo port);
    }
}
