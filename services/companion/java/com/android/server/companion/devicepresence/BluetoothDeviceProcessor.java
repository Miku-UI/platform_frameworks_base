/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.companion.devicepresence;

import static android.companion.DevicePresenceEvent.EVENT_BT_CONNECTED;
import static android.companion.DevicePresenceEvent.EVENT_BT_DISCONNECTED;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.companion.AssociationInfo;
import android.net.MacAddress;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.ParcelUuid;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.ArrayUtils;
import com.android.server.companion.association.AssociationStore;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("LongLogTag")
public class BluetoothDeviceProcessor
        extends BluetoothAdapter.BluetoothConnectionCallback
        implements AssociationStore.OnChangeListener {
    private static final String TAG = "CDM_BluetoothDeviceProcessor";

    interface Callback {
        void onBluetoothCompanionDeviceConnected(int associationId, int userId);

        void onBluetoothCompanionDeviceDisconnected(int associationId, int userId);

        void onDevicePresenceEventByUuid(ObservableUuid uuid, int event);
    }

    @NonNull
    private final AssociationStore mAssociationStore;
    @NonNull
    private final ObservableUuidStore mObservableUuidStore;
    @NonNull
    private final Callback mCallback;

    /** A set of ALL connected BT device (not only companion.) */
    @GuardedBy("mAllConnectedDevices")
    @NonNull
    private final Map<MacAddress, BluetoothDevice> mAllConnectedDevices = new HashMap<>();

    BluetoothDeviceProcessor(@NonNull AssociationStore associationStore,
            @NonNull ObservableUuidStore observableUuidStore, @NonNull Callback callback) {
        mAssociationStore = associationStore;
        mObservableUuidStore = observableUuidStore;
        mCallback = callback;
    }

    void init(@NonNull BluetoothAdapter btAdapter) {
        btAdapter.registerBluetoothConnectionCallback(
                new HandlerExecutor(Handler.getMain()), /* callback */this);
        mAssociationStore.registerLocalListener(this);
    }

    /**
     * Overrides
     * {@link BluetoothAdapter.BluetoothConnectionCallback#onDeviceConnected(BluetoothDevice)}.
     */
    @Override
    public void onDeviceConnected(@NonNull BluetoothDevice device) {
        final MacAddress macAddress = MacAddress.fromString(device.getAddress());

        synchronized (mAllConnectedDevices) {
            if (mAllConnectedDevices.put(macAddress, device) != null) {
                Slog.i(TAG, "Device " + device.getAddress()
                        + " already marked as connected. Skipping duplicate event.");
                return;
            }
        }


        onDeviceConnectivityChanged(device, true);
    }

    /**
     * Overrides
     * {@link BluetoothAdapter.BluetoothConnectionCallback#onDeviceConnected(BluetoothDevice)}.
     * Also invoked when user turns BT off while the device is connected.
     */
    @Override
    public void onDeviceDisconnected(@NonNull BluetoothDevice device,
            int reason) {
        final MacAddress macAddress = MacAddress.fromString(device.getAddress());

        synchronized (mAllConnectedDevices) {
            if (mAllConnectedDevices.remove(macAddress) == null) {
                Slog.i(TAG, "Device " + device.getAddress()
                        + " not found as connected. Skipping disconnect event");
                return;
            }
        }

        onDeviceConnectivityChanged(device, false);
    }

    private void onDeviceConnectivityChanged(@NonNull BluetoothDevice device, boolean connected) {
        int userId = UserHandle.myUserId();
        final List<AssociationInfo> associations =
                mAssociationStore.getActiveAssociationsByAddress(device.getAddress());

        for (AssociationInfo association : associations) {
            if (!association.isNotifyOnDeviceNearby()) continue;
            final int id = association.getId();
            if (connected) {
                mCallback.onBluetoothCompanionDeviceConnected(id, association.getUserId());
            } else {
                mCallback.onBluetoothCompanionDeviceDisconnected(id, association.getUserId());
            }
        }

        final List<ObservableUuid> observableUuids =
                mObservableUuidStore.getObservableUuidsForUser(userId);
        final ParcelUuid[] bluetoothDeviceUuids = device.getUuids();
        final List<ParcelUuid> deviceUuids = ArrayUtils.isEmpty(bluetoothDeviceUuids)
                ? Collections.emptyList() : Arrays.asList(bluetoothDeviceUuids);

        for (ObservableUuid uuid : observableUuids) {
            if (deviceUuids.contains(uuid.getUuid())) {
                mCallback.onDevicePresenceEventByUuid(uuid, connected ? EVENT_BT_CONNECTED
                        : EVENT_BT_DISCONNECTED);
            }
        }
    }

    @Override
    public void onAssociationAdded(AssociationInfo association) {
        synchronized (mAllConnectedDevices) {
            if (mAllConnectedDevices.containsKey(association.getDeviceMacAddress())) {
                mCallback.onBluetoothCompanionDeviceConnected(
                        association.getId(), association.getUserId());
            } else {
                Slog.i(TAG, "onAssociationAdded: Device " + association.getDeviceMacAddress()
                        + " is not currently connected.");
            }
        }
    }

}
