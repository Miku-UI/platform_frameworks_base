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

import static android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION;
import static android.companion.DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED;
import static android.companion.DevicePresenceEvent.EVENT_BLE_APPEARED;
import static android.companion.DevicePresenceEvent.EVENT_BLE_DISAPPEARED;
import static android.companion.DevicePresenceEvent.EVENT_BT_CONNECTED;
import static android.companion.DevicePresenceEvent.EVENT_BT_DISCONNECTED;
import static android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED;
import static android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED;
import static android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY;
import static android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY;
import static android.companion.DevicePresenceEvent.NO_ASSOCIATION;
import static android.content.Context.BLUETOOTH_SERVICE;

import static com.android.server.companion.utils.MetricUtils.logDevicePresenceEvent;
import static com.android.server.companion.utils.PermissionsUtils.enforceCallerCanManageAssociationsForPackage;
import static com.android.server.companion.utils.PermissionsUtils.enforceCallerCanObserveDevicePresenceByDeviceId;
import static com.android.server.companion.utils.PermissionsUtils.enforceCallerCanObserveDevicePresenceByUuid;
import static com.android.server.companion.utils.PermissionsUtils.enforceCallerShellOrRoot;

import android.annotation.NonNull;
import android.annotation.PermissionManuallyEnforced;
import android.annotation.SuppressLint;
import android.annotation.TestApi;
import android.annotation.UserIdInt;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.companion.AssociationInfo;
import android.companion.DeviceId;
import android.companion.DeviceNotAssociatedException;
import android.companion.DevicePresenceEvent;
import android.companion.Flags;
import android.companion.IOnDevicePresenceEventListener;
import android.companion.ObservingDevicePresenceRequest;
import android.content.Context;
import android.hardware.power.Mode;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManagerInternal;
import android.os.RemoteException;
import android.os.UserManager;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.CollectionUtils;
import com.android.server.companion.CompanionExemptionProcessor;
import com.android.server.companion.association.AssociationStore;
import com.android.server.companion.utils.MetricUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Class responsible for monitoring companion devices' "presence" status (i.e.
 * connected/disconnected for Bluetooth devices; nearby or not for BLE devices).
 * SelfManaged devices are able to report their device presence status to the system.
 * <p>
 * Should only be used by
 * {@link com.android.server.companion.CompanionDeviceManagerService CompanionDeviceManagerService}
 * to which it provides the following API:
 * <ul>
 * <li> {@link #processSelfManagedDevicePresenceEvent(int, DevicePresenceEvent)}
 * <li> {@link #isDevicePresent(int)}
 * </ul>
 */
@SuppressLint("LongLogTag")
public class DevicePresenceProcessor implements AssociationStore.OnChangeListener,
        BluetoothDeviceProcessor.Callback, BleDeviceProcessor.Callback {
    private static final String TAG = "CDM_DevicePresenceProcessor";

    @NonNull
    private final Context mContext;
    @NonNull
    private final CompanionAppBinder mCompanionAppBinder;
    @NonNull
    private final AssociationStore mAssociationStore;
    @NonNull
    private final ObservableUuidStore mObservableUuidStore;
    @NonNull
    private final BluetoothDeviceProcessor mBluetoothDeviceProcessor;
    @NonNull
    private final BleDeviceProcessor mBleDeviceProcessor;
    @NonNull
    private final PowerManagerInternal mPowerManagerInternal;
    @NonNull
    private final UserManager mUserManager;
    @NonNull
    private final CompanionExemptionProcessor mCompanionExemptionProcessor;

    // NOTE: Same association may appear in more than one of the following sets at the same time.
    // (E.g. self-managed devices that have MAC addresses, could be reported as present by their
    // companion applications, while at the same be connected via BT, or detected nearby by BLE
    // scanner)
    @NonNull
    private final Set<Integer> mConnectedBtDevices = new HashSet<>();
    @NonNull
    private final Set<Integer> mNearbyBleDevices = new HashSet<>();
    @NonNull
    private final Set<Integer> mConnectedSelfManagedDevices = new HashSet<>();
    @NonNull
    private final Set<ParcelUuid> mConnectedUuidDevices = new HashSet<>();
    @NonNull
    @GuardedBy("mBtDisconnectedDevices")
    private final Set<Integer> mBtDisconnectedDevices = new HashSet<>();

    @NonNull
    private final Set<Integer> mSelfManagedNearByDevices = new HashSet<>();

    @NonNull
    // A map of each presence state set to its corresponding event type.
    private final Map<Integer, Set<Integer>> mPresentDevices;

    // A map to track device presence within 10 seconds of Bluetooth disconnection.
    // The key is the association ID, and the boolean value indicates if the device
    // was detected again within that time frame.
    @GuardedBy("mBtDisconnectedDevices")
    private final @NonNull SparseBooleanArray mBtDisconnectedDevicesBlePresence =
            new SparseBooleanArray();

    // Tracking "simulated" presence. Used for debugging and testing only.
    private final @NonNull Set<Integer> mSimulated = new HashSet<>();
    private final SimulatedDevicePresenceSchedulerHelper mSchedulerHelper =
            new SimulatedDevicePresenceSchedulerHelper();

    private final BleDeviceDisappearedScheduler mBleDeviceDisappearedScheduler =
            new BleDeviceDisappearedScheduler();

    // The list for tracking which services are listening for presence events.
    private final List<DevicePresenceListener> mDevicePresenceListener =
            new CopyOnWriteArrayList<>();
    /**
     * A structure hold the DevicePresenceEvents that are pending to be reported to the companion
     * app when the user unlocks the local device per userId.
     */
    @GuardedBy("mPendingDevicePresenceEvents")
    public final SparseArray<List<DevicePresenceEvent>> mPendingDevicePresenceEvents =
            new SparseArray<>();

    public DevicePresenceProcessor(@NonNull Context context,
            @NonNull CompanionAppBinder companionAppBinder,
            @NonNull UserManager userManager,
            @NonNull AssociationStore associationStore,
            @NonNull ObservableUuidStore observableUuidStore,
            @NonNull PowerManagerInternal powerManagerInternal,
            @NonNull CompanionExemptionProcessor companionExemptionProcessor) {
        mContext = context;
        mCompanionAppBinder = companionAppBinder;
        mAssociationStore = associationStore;
        mObservableUuidStore = observableUuidStore;
        mUserManager = userManager;
        mBluetoothDeviceProcessor = new BluetoothDeviceProcessor(associationStore,
                mObservableUuidStore, this);
        mBleDeviceProcessor = new BleDeviceProcessor(associationStore, this, this);
        mPowerManagerInternal = powerManagerInternal;
        mCompanionExemptionProcessor = companionExemptionProcessor;
        mPresentDevices = Map.of(
                EVENT_BT_CONNECTED, mConnectedBtDevices,
                EVENT_BLE_APPEARED, mNearbyBleDevices,
                EVENT_SELF_MANAGED_APPEARED, mConnectedSelfManagedDevices,
                EVENT_SELF_MANAGED_NEARBY, mSelfManagedNearByDevices
        );
    }

    /** Initialize {@link DevicePresenceProcessor} */
    public void init(Context context) {
        BluetoothManager bm = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
        if (bm == null) {
            Slog.w(TAG, "BluetoothManager is not available.");
            return;
        }
        final BluetoothAdapter btAdapter = bm.getAdapter();
        if (btAdapter == null) {
            Slog.w(TAG, "BluetoothAdapter is NOT available.");
            return;
        }

        mBluetoothDeviceProcessor.init(btAdapter);
        mBleDeviceProcessor.init(context, btAdapter);

        mAssociationStore.registerLocalListener(this);
    }

    /**
     * Process device presence start request.
     */

    @PermissionManuallyEnforced
    public void startObservingDevicePresence(ObservingDevicePresenceRequest request,
            String callingPackage, int userId, boolean enforcePermissions) {
        Slog.i(TAG,
                "Start observing request=[" + request + "] for userId=[" + userId + "], package=["
                        + callingPackage + "]...");
        final ParcelUuid requestUuid = request.getUuid();
        final DeviceId deviceId = request.getDeviceId();

        if (requestUuid != null) {
            if (enforcePermissions) {
                enforceCallerCanObserveDevicePresenceByUuid(mContext, callingPackage, userId);
            }

            // If it's already being observed, then no-op.
            if (mObservableUuidStore.isUuidBeingObserved(requestUuid, userId, callingPackage)) {
                Slog.i(TAG, "UUID=[" + requestUuid + "], package=["
                        + callingPackage + "], userId=[" + userId + "] is already being observed.");
                return;
            }

            final ObservableUuid observableUuid = new ObservableUuid(userId, requestUuid,
                    callingPackage, System.currentTimeMillis());
            mObservableUuidStore.writeObservableUuid(userId, observableUuid);
        } else if (deviceId != null) {
            enforceCallerCanObserveDevicePresenceByDeviceId(mContext);

            Slog.i(TAG, "Register device presence for Device id: " + deviceId);

            AssociationInfo associationInfo = mAssociationStore.getAssociationByDeviceId(
                    userId, deviceId);
            if (associationInfo == null) {
                throw new IllegalArgumentException(
                        "Association is not found for DeviceId=(" + deviceId + ")"
                );
            }

            if (callingPackage.equals(associationInfo.getPackageName())) {
                throw new IllegalArgumentException(
                      "When observing device presence for the own package,"
                              + " use setAssociationId instead, not the device id."
                );
            }

            List<String> packagesToNotify;
            if (associationInfo.getPackagesToNotify() != null) {
                packagesToNotify = new ArrayList<>(associationInfo.getPackagesToNotify());
            } else {
                packagesToNotify = new ArrayList<>();
            }

            if (!packagesToNotify.contains(callingPackage)) {
                packagesToNotify.add(callingPackage);
            } else {
                Slog.i(TAG, "Package: " + callingPackage
                        + " has been already observing, no action needed");
                return;
            }

            associationInfo = (new AssociationInfo.Builder(associationInfo))
                    .setPackagesToNotify(packagesToNotify).build();
            mAssociationStore.updateAssociation(associationInfo);

            // Device already present, trigger the callback immediately.
            if (associationInfo.shouldBindWhenPresent()) {
                if (isBlePresent(associationInfo.getId())) {
                    notifyAndExemptApp(
                            EVENT_BLE_APPEARED, associationInfo, callingPackage, userId);
                }
                if (isBtConnected(associationInfo.getId())) {
                    notifyAndExemptApp(
                            EVENT_BT_CONNECTED, associationInfo, callingPackage, userId);
                }
            }
        } else {
            final int associationId = request.getAssociationId();
            AssociationInfo association = mAssociationStore.getAssociationWithCallerChecks(
                    associationId);

            // If it's already being observed, then no-op.
            if (association.isNotifyOnDeviceNearby()) {
                Slog.i(TAG, "Associated device id=[" + association.getId()
                        + "] is already being observed. No-op.");
                return;
            }

            association = (new AssociationInfo.Builder(association)).setNotifyOnDeviceNearby(true)
                    .build();
            mAssociationStore.updateAssociation(association);

            // Send callback immediately if the device is present.
            if (isDevicePresent(associationId)) {
                Slog.i(TAG, "Device is already present. Triggering callback.");
                if (isBlePresent(associationId)) {
                    onDevicePresenceEvent(mNearbyBleDevices, associationId,
                            new DevicePresenceEvent(associationId, EVENT_BLE_APPEARED, null));
                } else if (isBtConnected(associationId)) {
                    onDevicePresenceEvent(mConnectedBtDevices, associationId,
                            new DevicePresenceEvent(associationId, EVENT_BT_CONNECTED, null));
                } else if (isSimulatePresent(associationId)) {
                    onDevicePresenceEvent(mSimulated, associationId,
                            new DevicePresenceEvent(associationId, EVENT_BLE_APPEARED, null));
                }
            }
        }

        Slog.i(TAG, "Registered device presence listener.");
    }

    /**
     * Process device presence stop request.
     */
    @PermissionManuallyEnforced
    public void stopObservingDevicePresence(ObservingDevicePresenceRequest request,
            String packageName, int userId, boolean enforcePermissions) {
        Slog.i(TAG,
                "Stop observing request=[" + request + "] for userId=[" + userId + "], package=["
                        + packageName + "]...");

        final ParcelUuid requestUuid = request.getUuid();
        final DeviceId deviceId = request.getDeviceId();

        if (requestUuid != null) {
            if (enforcePermissions) {
                enforceCallerCanObserveDevicePresenceByUuid(mContext, packageName, userId);
            }

            if (!mObservableUuidStore.isUuidBeingObserved(requestUuid, userId, packageName)) {
                Slog.i(TAG, "UUID=[" + requestUuid + "], package=[" + packageName + "], userId=["
                        + userId + "] is already not being observed.");
                return;
            }

            mObservableUuidStore.removeObservableUuid(userId, requestUuid, packageName);
            removeCurrentConnectedUuidDevice(requestUuid);
        } else if (deviceId != null) {
            enforceCallerCanObserveDevicePresenceByDeviceId(mContext);

            Slog.i(TAG, "Unregister device presence for Device id: " + request.getDeviceId());

            AssociationInfo associationInfo = mAssociationStore.getAssociationByDeviceId(userId,
                    deviceId);
            if (associationInfo == null) {
                throw new IllegalArgumentException(
                        "Association is not found for DeviceId=(" + deviceId + ")"
                );
            }

            if (associationInfo.getPackagesToNotify() != null
                    && associationInfo.getPackagesToNotify().contains(packageName)) {
                List<String> packagesToNotify =
                        new ArrayList<>(associationInfo.getPackagesToNotify());
                packagesToNotify.remove(packageName);
                if (packagesToNotify.isEmpty()) {
                    packagesToNotify = null;
                }
                associationInfo = (new AssociationInfo.Builder(associationInfo))
                        .setPackagesToNotify(packagesToNotify).build();
                mAssociationStore.updateAssociation(associationInfo);
            } else {
                Slog.w(TAG, "DeviceId: " + request.getDeviceId() + " is not currently observed");
                return;
            }
        } else {
            final int associationId = request.getAssociationId();
            AssociationInfo association = mAssociationStore.getAssociationWithCallerChecks(
                    associationId);

            // If it's already being observed, then no-op.
            if (!association.isNotifyOnDeviceNearby()) {
                Slog.i(TAG, "Associated device id=[" + association.getId()
                        + "] is already not being observed. No-op.");
                return;
            }

            association = (new AssociationInfo.Builder(association)).setNotifyOnDeviceNearby(false)
                    .build();
            mAssociationStore.updateAssociation(association);
        }

        Slog.i(TAG, "Unregistered device presence listener.");

        // If last listener is unregistered, then unbind application.
        if (!shouldBindPackage(userId, packageName)) {
            mCompanionAppBinder.unbindCompanionApp(userId, packageName);
        }
    }

    /**
     * For legacy device presence below Android V.
     *
     * @deprecated Use {@link #startObservingDevicePresence(ObservingDevicePresenceRequest, String,
     * int, boolean)}
     */
    @Deprecated
    public void startObservingDevicePresence(int userId, String packageName, String deviceAddress)
            throws RemoteException {
        Slog.i(TAG,
                "Start observing device=[" + deviceAddress + "] for userId=[" + userId
                        + "], package=["
                        + packageName + "]...");

        enforceCallerCanManageAssociationsForPackage(mContext, userId, packageName, null);

        AssociationInfo association = mAssociationStore.getFirstAssociationByAddress(userId,
                packageName, deviceAddress);

        if (association == null) {
            throw new RemoteException(new DeviceNotAssociatedException("App " + packageName
                    + " is not associated with device " + deviceAddress
                    + " for user " + userId));
        }

        startObservingDevicePresence(
                new ObservingDevicePresenceRequest.Builder().setAssociationId(association.getId())
                        .build(), packageName, userId, /* enforcePermissions */ true);
    }

    /**
     * For legacy device presence below Android V.
     *
     * @deprecated Use {@link #stopObservingDevicePresence(ObservingDevicePresenceRequest, String,
     * int, boolean)}
     */
    @Deprecated
    public void stopObservingDevicePresence(int userId, String packageName, String deviceAddress)
            throws RemoteException {
        Slog.i(TAG,
                "Stop observing device=[" + deviceAddress + "] for userId=[" + userId
                        + "], package=["
                        + packageName + "]...");

        enforceCallerCanManageAssociationsForPackage(mContext, userId, packageName, null);

        AssociationInfo association = mAssociationStore.getFirstAssociationByAddress(userId,
                packageName, deviceAddress);

        if (association == null) {
            throw new RemoteException(new DeviceNotAssociatedException("App " + packageName
                    + " is not associated with device " + deviceAddress
                    + " for user " + userId));
        }

        stopObservingDevicePresence(
                new ObservingDevicePresenceRequest.Builder().setAssociationId(association.getId())
                        .build(), packageName, userId, /* enforcePermissions */ true);
    }


    /**
     * Sets a listener to receive device presence events for a given system service.
     */
    public void setOnDevicePresenceEventListener(int[] associationIds,
            @NonNull String serviceName, @NonNull IOnDevicePresenceEventListener listener) {
        if (associationIds == null || associationIds.length == 0) {
            throw new IllegalArgumentException("associationIds must be a non-empty array.");
        }


        Slog.i(TAG, "Setting DevicePresenceEventListener for service: " + serviceName);

        final Set<Integer> filterSet =
                Arrays.stream(associationIds).boxed().collect(Collectors.toSet());

        boolean removed = mDevicePresenceListener.removeIf(
                l -> l.mServiceName.equals(serviceName));
        if (removed) {
            Slog.i(TAG, "Removed previous listener for service: " + serviceName);
        }

        mDevicePresenceListener.add(new DevicePresenceListener(serviceName, listener, filterSet));

        // Immediately notify the new listener of any currently present devices
        // that match its filter to avoid race conditions.
        for (Map.Entry<Integer, Set<Integer>> entry : mPresentDevices.entrySet()) {
            final int eventType = entry.getKey();
            final Set<Integer> presentIds = entry.getValue();
            for (int id : presentIds) {
                if (filterSet.contains(id)) {
                    try {
                        listener.onDevicePresence(new DevicePresenceEvent(id, eventType, null));
                    } catch (RemoteException e) {
                        Slog.w(TAG, "Listener for service " + serviceName
                                + " died while sending initial presence state.", e);
                    }
                }
            }
        }
    }

    /**
     * Unregisters a previously registered listener.
     */
    public void removeOnDevicePresenceEventListener(
            @NonNull String serviceName) {
        Slog.i(TAG, "Removing DevicePresenceEventListener for service: " + serviceName);

        final boolean removed = mDevicePresenceListener.removeIf(
                it -> it.mServiceName.equals(serviceName));

        // Remove the listener and its filter.
        if (!removed) {
            Slog.w(TAG, "Attempted to remove a listener for service '" + serviceName
                    + "' that was not registered.");
        }
    }

    /**
     * @return whether the package should be bound (i.e. at least one of the devices associated with
     * the package is currently present OR the UUID to be observed by this package is
     * currently present).
     */
    private boolean shouldBindPackage(@UserIdInt int userId, @NonNull String packageName) {
        final List<AssociationInfo> packageAssociations =
                mAssociationStore.getActiveAssociationsByPackage(userId, packageName);
        final List<ObservableUuid> observableUuids =
                mObservableUuidStore.getObservableUuidsForPackage(userId, packageName);
        final List<AssociationInfo> associationInfos =
                mAssociationStore.getActiveAssociationsByUser(userId);

        for (AssociationInfo association : packageAssociations) {
            if (!association.shouldBindWhenPresent()) continue;
            if (isDevicePresent(association.getId())) return true;
        }

        for (ObservableUuid uuid : observableUuids) {
            if (isDeviceUuidPresent(uuid.getUuid())) {
                return true;
            }
        }

        for (AssociationInfo ai : associationInfos) {
            List<String> packagesToNotify = ai.getPackagesToNotify();
            if (packagesToNotify != null
                    && packagesToNotify.contains(packageName) && isDevicePresent(ai.getId())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Bind the system to the app if it's not bound.
     *
     * Set bindImportant to true when the association is self-managed to avoid the target service
     * being killed.
     */
    public void bindApplicationIfNeeded(int userId, String packageName, boolean bindImportant) {
        if (!mCompanionAppBinder.isCompanionApplicationBound(userId, packageName)) {
            mCompanionAppBinder.bindCompanionApp(
                    userId, packageName, bindImportant, this::onBinderDied);
        } else {
            Slog.i(TAG,
                    "UserId=[" + userId + "], packageName=[" + packageName + "] is already bound.");
        }
    }

    /**
     * @return current connected UUID devices.
     */
    public Set<ParcelUuid> getCurrentConnectedUuidDevices() {
        return mConnectedUuidDevices;
    }

    /**
     * Remove current connected UUID device.
     */
    public void removeCurrentConnectedUuidDevice(ParcelUuid uuid) {
        mConnectedUuidDevices.remove(uuid);
    }

    /**
     * @return whether the associated companion devices is present. I.e. device is nearby (for BLE);
     * or devices is connected (for Bluetooth); or reported (by the application) to be
     * nearby (for "self-managed" associations).
     */
    public boolean isDevicePresent(int associationId) {
        if (mSimulated.contains(associationId)) {
            return true;
        }
        for (Set<Integer> presentIds : mPresentDevices.values()) {
            if (presentIds.contains(associationId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether the current uuid to be observed is present.
     */
    public boolean isDeviceUuidPresent(ParcelUuid uuid) {
        return mConnectedUuidDevices.contains(uuid);
    }

    /**
     * @return whether the current device is BT connected and had already reported to the app.
     */

    public boolean isBtConnected(int associationId) {
        return mConnectedBtDevices.contains(associationId);
    }

    /**
     * @return whether the current device in BLE range and had already reported to the app.
     */
    public boolean isBlePresent(int associationId) {
        return mNearbyBleDevices.contains(associationId);
    }

    /**
     * @return whether the current device had been already reported by the simulator.
     */
    public boolean isSimulatePresent(int associationId) {
        return mSimulated.contains(associationId);
    }

    /**
     * Marks a "self-managed" device as disconnected when binderDied.
     */
    public void onSelfManagedDeviceReporterBinderDied(int associationId) {
        onDevicePresenceEvent(
                mConnectedSelfManagedDevices,
                associationId,
                new DevicePresenceEvent(associationId, EVENT_SELF_MANAGED_DISAPPEARED, null)
        );
    }

    @Override
    public void onBluetoothCompanionDeviceConnected(int associationId, int userId) {
        Slog.i(TAG, "onBluetoothCompanionDeviceConnected: "
                + "associationId( " + associationId + " )");
        if (!mUserManager.isUserUnlockingOrUnlocked(userId)) {
            onDeviceLocked(associationId, userId, EVENT_BT_CONNECTED, /* ParcelUuid */ null);
            return;
        }

        synchronized (mBtDisconnectedDevices) {
            // A device is considered reconnected within 10 seconds if a pending BLE lost report is
            // followed by a detected Bluetooth connection.
            boolean isReconnected = mBtDisconnectedDevices.contains(associationId);
            if (isReconnected) {
                Slog.i(TAG, "Device ( " + associationId + " ) is reconnected within 10s.");
                mBleDeviceDisappearedScheduler.unScheduleDeviceDisappeared(associationId);
            }

            Slog.i(TAG, "onBluetoothCompanionDeviceConnected: "
                    + "associationId( " + associationId + " )");
            onDevicePresenceEvent(mConnectedBtDevices, associationId,
                    new DevicePresenceEvent(associationId, EVENT_BT_CONNECTED, null));

            // Stop the BLE scan if all devices report BT connected status and BLE was present.
            if (canStopBleScan()) {
                mBleDeviceProcessor.stopScanIfNeeded();
            }

        }
    }

    @Override
    public void onBluetoothCompanionDeviceDisconnected(int associationId, int userId) {
        Slog.i(TAG, "onBluetoothCompanionDeviceDisconnected "
                + "associationId( " + associationId + " )");

        if (!mUserManager.isUserUnlockingOrUnlocked(userId)) {
            onDeviceLocked(associationId, userId, EVENT_BT_DISCONNECTED, /* ParcelUuid */ null);
            return;
        }

        // Start BLE scanning when the device is disconnected.
        mBleDeviceProcessor.startScan();

        onDevicePresenceEvent(mConnectedBtDevices, associationId,
                new DevicePresenceEvent(associationId, EVENT_BT_DISCONNECTED, null));
        // If current device is BLE present but BT is disconnected , means it will be
        // potentially out of range later. Schedule BLE disappeared callback.
        if (isBlePresent(associationId)) {
            synchronized (mBtDisconnectedDevices) {
                mBtDisconnectedDevices.add(associationId);
            }
            mBleDeviceDisappearedScheduler.scheduleBleDeviceDisappeared(associationId);
        }
    }


    @Override
    public void onBleCompanionDeviceFound(int associationId, int userId) {
        Slog.i(TAG, "onBleCompanionDeviceFound " + "associationId( " + associationId + " )");
        if (!mUserManager.isUserUnlockingOrUnlocked(userId)) {
            onDeviceLocked(associationId, userId, EVENT_BLE_APPEARED, /* ParcelUuid */ null);
            return;
        }

        onDevicePresenceEvent(mNearbyBleDevices, associationId,
                new DevicePresenceEvent(associationId, EVENT_BLE_APPEARED, null));
        synchronized (mBtDisconnectedDevices) {
            final boolean isCurrentPresent = mBtDisconnectedDevicesBlePresence.get(associationId);
            if (mBtDisconnectedDevices.contains(associationId) && isCurrentPresent) {
                mBleDeviceDisappearedScheduler.unScheduleDeviceDisappeared(associationId);
            }
        }
    }

    @Override
    public void onBleCompanionDeviceLost(int associationId, int userId) {
        Slog.i(TAG, "onBleCompanionDeviceLost " + "associationId( " + associationId + " )");
        if (!mUserManager.isUserUnlockingOrUnlocked(userId)) {
            onDeviceLocked(associationId, userId, EVENT_BLE_APPEARED, /* ParcelUuid */ null);
            return;
        }

        onDevicePresenceEvent(mNearbyBleDevices, associationId,
                new DevicePresenceEvent(associationId, EVENT_BLE_DISAPPEARED, null));
    }

    /** FOR DEBUGGING AND/OR TESTING PURPOSES ONLY. */
    @TestApi
    public void simulateDeviceEvent(int associationId, int event) {
        // IMPORTANT: this API should only be invoked via the
        // 'companiondevice simulate-device-appeared' Shell command, so the only uid-s allowed to
        // make this call are SHELL and ROOT.
        // No other caller (including SYSTEM!) should be allowed.
        enforceCallerShellOrRoot();
        // Make sure the association exists.
        enforceAssociationExists(associationId);

        final AssociationInfo associationInfo = mAssociationStore.getAssociationById(associationId);

        switch (event) {
            case EVENT_BLE_APPEARED:
                simulateDeviceAppeared(associationId, event);
                break;
            case EVENT_BT_CONNECTED:
                onBluetoothCompanionDeviceConnected(associationId, associationInfo.getUserId());
                break;
            case EVENT_BLE_DISAPPEARED:
                simulateDeviceDisappeared(associationId, event);
                break;
            case EVENT_BT_DISCONNECTED:
                onBluetoothCompanionDeviceDisconnected(associationId, associationInfo.getUserId());
                break;
            default:
                throw new IllegalArgumentException("Event: " + event + "is not supported");
        }
    }

    /** FOR DEBUGGING AND/OR TESTING PURPOSES ONLY. */
    @TestApi
    public void simulateDeviceEventByUuid(ObservableUuid uuid, int event) {
        // IMPORTANT: this API should only be invoked via the
        // 'companiondevice simulate-device-uuid-events' Shell command, so the only uid-s allowed to
        // make this call are SHELL and ROOT.
        // No other caller (including SYSTEM!) should be allowed.
        enforceCallerShellOrRoot();
        onDevicePresenceEventByUuid(uuid, event);
    }

    /** FOR DEBUGGING AND/OR TESTING PURPOSES ONLY. */
    @TestApi
    public void simulateDeviceEventOnDeviceLocked(
            int associationId, int userId, int event, ParcelUuid uuid) {
        // IMPORTANT: this API should only be invoked via the
        // 'companiondevice simulate-device-event-device-locked' Shell command,
        // so the only uid-s allowed to make this call are SHELL and ROOT.
        // No other caller (including SYSTEM!) should be allowed.
        enforceCallerShellOrRoot();
        onDeviceLocked(associationId, userId, event, uuid);
    }

    /** FOR DEBUGGING AND/OR TESTING PURPOSES ONLY. */
    @TestApi
    public void simulateDeviceEventOnUserUnlocked(int userId) {
        // IMPORTANT: this API should only be invoked via the
        // 'companiondevice simulate-device-event-device-unlocked' Shell command,
        // so the only uid-s allowed to make this call are SHELL and ROOT.
        // No other caller (including SYSTEM!) should be allowed.
        enforceCallerShellOrRoot();
        sendDevicePresenceEventOnUnlocked(userId);
    }

    private void simulateDeviceAppeared(int associationId, int state) {
        onDevicePresenceEvent(mSimulated, associationId,
                new DevicePresenceEvent(associationId, state, null));
        mSchedulerHelper.scheduleOnDeviceGoneCallForSimulatedDevicePresence(associationId);
    }

    private void simulateDeviceDisappeared(int associationId, int state) {
        mSchedulerHelper.unscheduleOnDeviceGoneCallForSimulatedDevicePresence(associationId);
        onDevicePresenceEvent(mSimulated, associationId,
                new DevicePresenceEvent(associationId, state, null));
    }

    private void enforceAssociationExists(int associationId) {
        if (mAssociationStore.getAssociationById(associationId) == null) {
            throw new IllegalArgumentException(
                    "Association with id " + associationId + " does not exist.");
        }
    }

    private void onDevicePresenceEvent(@NonNull Set<Integer> presentDevicesForSource,
            int associationId, @NonNull DevicePresenceEvent event) {
        Slog.i(TAG,
                "onDevicePresenceEvent() id=[" + associationId + "], event=[" + event + "]...");

        AssociationInfo association = mAssociationStore.getAssociationById(associationId);
        if (association == null) {
            Slog.e(TAG, "Association doesn't exist.");
            return;
        }

        final int userId = association.getUserId();
        final String packageName = association.getPackageName();
        final String deviceProfile = association.getDeviceProfile();

        if (event.getEvent() == EVENT_BLE_APPEARED) {
            synchronized (mBtDisconnectedDevices) {
                // If a BLE device is detected within 10 seconds after BT is disconnected,
                // flag it as BLE is present.
                if (mBtDisconnectedDevices.contains(associationId)) {
                    Slog.i(TAG, "Device ( " + associationId + " ) is present,"
                            + " do not need to send the callback with event ( "
                            + EVENT_BLE_APPEARED + " ).");
                    mBtDisconnectedDevicesBlePresence.append(associationId, true);
                }
            }
        }

        switch (event.getEvent()) {
            case EVENT_BLE_APPEARED:
            case EVENT_BT_CONNECTED:
            case EVENT_SELF_MANAGED_APPEARED:
            case EVENT_SELF_MANAGED_NEARBY:
                final boolean added = presentDevicesForSource.add(associationId);
                if (!added) {
                    Slog.w(TAG, "The association is already present.");
                }

                if (association.shouldBindWhenPresent()) {
                    bindApplicationIfNeeded(userId, packageName, association.isSelfManaged());
                    mCompanionExemptionProcessor.exemptPackage(userId, packageName, true);
                } else {
                    return;
                }

                if (association.isSelfManaged() || added) {
                    notifyDevicePresenceEvent(userId, packageName, deviceProfile, event);
                    // Also send the legacy callback.
                    legacyNotifyDevicePresenceEvent(association, true);
                    // Also send the callback to the package that registered to be notified.
                    if (association.getPackagesToNotify() != null) {
                        for (String packageToNotify : association.getPackagesToNotify()) {
                            notifyAndExemptApp(
                                    event.getEvent(), association, packageToNotify, userId);
                        }
                    }
                }
                break;
            case EVENT_BLE_DISAPPEARED:
            case EVENT_BT_DISCONNECTED:
            case EVENT_SELF_MANAGED_DISAPPEARED:
            case EVENT_SELF_MANAGED_NOT_NEARBY:
                final boolean removed = presentDevicesForSource.remove(associationId);
                if (!removed) {
                    Slog.w(TAG, "The association is already NOT present.");
                }

                if (!mCompanionAppBinder.isCompanionApplicationBound(userId, packageName)) {
                    Slog.e(TAG, "Package is not bound");
                    return;
                }

                if (association.isSelfManaged() || removed) {
                    notifyDevicePresenceEvent(userId, packageName, deviceProfile, event);
                    // Also send the legacy callback.
                    legacyNotifyDevicePresenceEvent(association, false);
                    // Send the callback to other packages that registered to be notified
                    if (association.getPackagesToNotify() != null) {
                        for (String packageToNotify : association.getPackagesToNotify()) {
                            notifyDevicePresenceEvent(
                                    userId, packageToNotify, deviceProfile, event);
                            // Also unbind the package that registered to be notified.
                            if (!shouldBindPackage(userId, packageToNotify)) {
                                unbindAndRemoveExemptionForApp(userId, packageToNotify);
                            }
                        }
                    }
                }

                // Check if there are other devices associated to the app that are present.
                if (!shouldBindPackage(userId, packageName)) {
                    unbindAndRemoveExemptionForApp(userId, packageName);
                }
                break;
            default:
                Slog.e(TAG, "Event: " + event.getEvent() + " is not supported.");
                break;
        }
    }

    @Override
    public void onDevicePresenceEventByUuid(ObservableUuid uuid, int eventType) {
        Slog.i(TAG, "onDevicePresenceEventByUuid ObservableUuid=[" + uuid + "], event=[" + eventType
                + "]...");

        final ParcelUuid parcelUuid = uuid.getUuid();
        final int userId = uuid.getUserId();
        if (!mUserManager.isUserUnlockingOrUnlocked(userId)) {
            onDeviceLocked(NO_ASSOCIATION, userId, eventType, parcelUuid);
            return;
        }

        final String packageName = uuid.getPackageName();
        final DevicePresenceEvent event = new DevicePresenceEvent(NO_ASSOCIATION, eventType,
                parcelUuid);

        switch (eventType) {
            case EVENT_BT_CONNECTED:
                boolean added = mConnectedUuidDevices.add(parcelUuid);
                if (!added) {
                    Slog.w(TAG, "This device is already connected.");
                }

                bindApplicationIfNeeded(userId, packageName, false);

                notifyDevicePresenceEvent(userId, packageName, MetricUtils.UUID, event);
                break;
            case EVENT_BT_DISCONNECTED:
                final boolean removed = mConnectedUuidDevices.remove(parcelUuid);
                if (!removed) {
                    Slog.w(TAG, "This device is already disconnected.");
                    return;
                }

                if (!mCompanionAppBinder.isCompanionApplicationBound(userId, packageName)) {
                    Slog.e(TAG, "Package is not bound.");
                    return;
                }

                notifyDevicePresenceEvent(userId, packageName, MetricUtils.UUID, event);

                if (!shouldBindPackage(userId, packageName)) {
                    mCompanionAppBinder.unbindCompanionApp(userId, packageName);
                }
                break;
            default:
                Slog.e(TAG, "Event: " + eventType + " is not supported");
                break;
        }
    }

    /**
     * Notify device presence event to the app.
     *
     * @deprecated Use
     * {@link #notifyDevicePresenceEvent(int, String, String, DevicePresenceEvent)} instead.
     */
    @Deprecated
    private void legacyNotifyDevicePresenceEvent(AssociationInfo association,
            boolean isAppeared) {
        Slog.i(TAG, "legacyNotifyDevicePresenceEvent() association=[" + association.toShortString()
                + "], isAppeared=[" + isAppeared + "]");

        final int userId = association.getUserId();
        final String packageName = association.getPackageName();

        final CompanionServiceConnector primaryServiceConnector =
                mCompanionAppBinder.getPrimaryServiceConnector(userId, packageName);
        if (primaryServiceConnector == null) {
            Slog.e(TAG, "Package is not bound.");
            return;
        }

        if (isAppeared) {
            primaryServiceConnector.postOnDeviceAppeared(association);
        } else {
            primaryServiceConnector.postOnDeviceDisappeared(association);
        }
    }

    /**
     * Notify the device presence event to the app.
     */
    private void notifyDevicePresenceEvent(int userId, String packageName,
            String deviceProfileOrUuid, DevicePresenceEvent event) {
        Slog.i(TAG,
                "notifyCompanionDevicePresenceEvent userId=[" + userId + "],"
                        + "packageName=[" + packageName + "],"
                        + "deviceProfileOrUuid=[" + deviceProfileOrUuid + "],"
                        + "event=[" + event + "]...");

        final CompanionServiceConnector primaryServiceConnector =
                mCompanionAppBinder.getPrimaryServiceConnector(userId, packageName);

        if (primaryServiceConnector == null) {
            Slog.e(TAG, "Package is NOT bound.");
            return;
        }
        logDevicePresenceEvent(
                userId, mContext, deviceProfileOrUuid, packageName, event.getEvent());
        broadcastDevicePresenceEvents(event);
        primaryServiceConnector.postOnDevicePresenceEvent(event);
    }

    /**
     * Notify the self-managed device presence event to the app.
     * @deprecated Use {@link #processSelfManagedDevicePresenceEvent(int, DevicePresenceEvent)}
     * instead.
     */
    public void notifySelfManagedDevicePresenceEvent(int associationId, boolean isAppeared) {
        Slog.i(TAG, "notifySelfManagedDeviceAppeared() id=" + associationId);
        mAssociationStore.getAssociationWithCallerChecks(associationId);

        final int eventType = isAppeared
                ? EVENT_SELF_MANAGED_APPEARED
                : EVENT_SELF_MANAGED_DISAPPEARED;
        final DevicePresenceEvent event = new DevicePresenceEvent(associationId, eventType, null);

        processSelfManagedDevicePresenceEvent(associationId, event);
    }

    /**
     * Processes a presence event reported by a self-managed companion app.
     * This involves validating the request, updating the association, and then triggering
     * the core presence logic or broadcasting the event.
     *
     * @param associationId the ID of the association reporting the event.
     * @param event the {@link DevicePresenceEvent} reported by the app.
     */
    public void processSelfManagedDevicePresenceEvent(
            int associationId, @NonNull DevicePresenceEvent event) {
        Slog.i(TAG,
                "processSelfManagedDevicePresenceEvent() id=" + associationId + ", event=" + event);

        // 1. Get the association and verify it is self-managed.
        AssociationInfo association = mAssociationStore.getAssociationById(associationId);
        if (association == null) {
            Slog.w(TAG, "Presence event for non-existent association " + associationId);
            return;
        }
        if (!association.isSelfManaged()) {
            throw new IllegalArgumentException("Association id=[" + associationId
                    + "] is not self-managed.");
        }

        // 2. Update the last connected timestamp.
        association = (new AssociationInfo.Builder(association))
                .setLastTimeConnected(System.currentTimeMillis())
                .build();
        mAssociationStore.updateAssociation(association);

        final String deviceProfile = association.getDeviceProfile();
        if (DEVICE_PROFILE_AUTOMOTIVE_PROJECTION.equals(deviceProfile)) {
            Slog.i(TAG, "Setting power mode for Automotive Projection profile.");
            boolean isAppeared = (event.getEvent() == EVENT_SELF_MANAGED_APPEARED);
            mPowerManagerInternal.setPowerMode(Mode.AUTOMOTIVE_PROJECTION, isAppeared);
        }
        // 3. Process the event.
        switch (event.getEvent()) {
            case EVENT_SELF_MANAGED_APPEARED, EVENT_SELF_MANAGED_DISAPPEARED ->
                    onDevicePresenceEvent(mConnectedSelfManagedDevices, associationId, event);
            case EVENT_SELF_MANAGED_NEARBY, EVENT_SELF_MANAGED_NOT_NEARBY -> onDevicePresenceEvent(
                    mSelfManagedNearByDevices, associationId, event);
            default -> Slog.w(TAG,
                    "Unsupported event type for self-managed presence: " + event.getEvent());
        }
    }

    private void onBinderDied(@UserIdInt int userId, @NonNull String packageName,
            @NonNull CompanionServiceConnector serviceConnector) {

        boolean isPrimary = serviceConnector.isPrimary();
        Slog.i(TAG, "onBinderDied() u" + userId + "/" + packageName + " isPrimary: " + isPrimary);

        // First, disable hint mode for Auto profile and mark not BOUND for primary service ONLY.
        if (isPrimary) {
            final List<AssociationInfo> associations =
                    mAssociationStore.getActiveAssociationsByPackage(userId, packageName);

            for (AssociationInfo association : associations) {
                final String deviceProfile = association.getDeviceProfile();
                if (DEVICE_PROFILE_AUTOMOTIVE_PROJECTION.equals(deviceProfile)) {
                    Slog.i(TAG, "Disable hint mode for device profile: " + deviceProfile);
                    mPowerManagerInternal.setPowerMode(Mode.AUTOMOTIVE_PROJECTION, false);
                    break;
                }
            }

            mCompanionAppBinder.removePackage(userId, packageName);
        }

        // Second: schedule rebinding if needed.
        final boolean shouldScheduleRebind = shouldScheduleRebind(userId, packageName, isPrimary);

        if (shouldScheduleRebind) {
            mCompanionAppBinder.scheduleRebinding(userId, packageName, serviceConnector);
        }
    }

    /**
     * Check if the system should rebind the self-managed secondary services
     * OR non-self-managed services.
     */
    private boolean shouldScheduleRebind(int userId, String packageName, boolean isPrimary) {
        // Make sure do not schedule rebind for the case ServiceConnector still gets callback after
        // app is uninstalled.
        boolean stillAssociated = false;
        // Make sure to clean up the state for all the associations
        // that associate with this package.
        boolean shouldScheduleRebind = false;
        boolean shouldScheduleRebindForUuid = false;
        final List<ObservableUuid> uuids =
                mObservableUuidStore.getObservableUuidsForPackage(userId, packageName);

        for (AssociationInfo ai :
                mAssociationStore.getActiveAssociationsByPackage(userId, packageName)) {
            final int associationId = ai.getId();
            stillAssociated = true;
            if (ai.isSelfManaged()) {
                // Do not rebind if primary one is died for selfManaged application.
                if (isPrimary && isDevicePresent(associationId)) {
                    onSelfManagedDeviceReporterBinderDied(associationId);
                    shouldScheduleRebind = false;
                }
                // Do not rebind if both primary and secondary services are died for
                // selfManaged application.
                shouldScheduleRebind = mCompanionAppBinder.isCompanionApplicationBound(userId,
                        packageName);
            } else if (ai.isNotifyOnDeviceNearby()) {
                // Always rebind for non-selfManaged devices.
                shouldScheduleRebind = true;
            }
        }

        for (ObservableUuid uuid : uuids) {
            if (isDeviceUuidPresent(uuid.getUuid())) {
                shouldScheduleRebindForUuid = true;
                break;
            }
        }

        return (stillAssociated && shouldScheduleRebind) || shouldScheduleRebindForUuid;
    }

    /**
     * Implements
     * {@link AssociationStore.OnChangeListener#onAssociationRemoved(AssociationInfo)}
     */
    @Override
    public void onAssociationRemoved(@NonNull AssociationInfo association) {
        final int id = association.getId();

        if (association.isNotifyOnDeviceNearby() && Flags.notifyAssociationRemoved()) {
            final int userId = association.getUserId();
            final String packageName = association.getPackageName();
            final DevicePresenceEvent event = new DevicePresenceEvent(
                    id, EVENT_ASSOCIATION_REMOVED, /* uuid */ null);
            // Notify and unbind for the original package that created this association.
            processNotifyAssociationRemoved(userId, packageName, event,
                    association.getDeviceProfile(), association.isSelfManaged());
            // Notify and unbind for non companion app.
            if (Flags.associationVerification() && association.getPackagesToNotify() != null) {
                for (String packageToNotify : association.getPackagesToNotify()) {
                    if (!packageToNotify.equals(packageName)) {
                        processNotifyAssociationRemoved(userId, packageToNotify, event,
                                association.getDeviceProfile(), association.isSelfManaged());
                    }
                }
            }
        }
        // Remove the association from all presence sets.
        for (Set<Integer> presentIds : mPresentDevices.values()) {
            presentIds.remove(id);
        }

        // Clean up the listener filters. If a listener's filter becomes empty
        // after removing this association, remove the listener entirely.
        mDevicePresenceListener.removeIf(devicePresenceListener -> {
            final boolean idWasPresent = devicePresenceListener.mAssociationIdFilter.remove(id);

            // Only consider removing the listener if the id was part of its filter.
            if (idWasPresent && devicePresenceListener.mAssociationIdFilter.isEmpty()) {
                Slog.i(TAG, "Listener for service '" + devicePresenceListener.mServiceName
                        + "' has no more associations to observe. Removing listener.");
                return true;
            }

            return false;
        });


        synchronized (mBtDisconnectedDevices) {
            mBtDisconnectedDevices.remove(id);
            mBtDisconnectedDevicesBlePresence.delete(id);
        }

        // Do NOT call mCallback.onDeviceDisappeared()!
        // CompanionDeviceManagerService will know that the association is removed, and will do
        // what's needed.
    }

    /**
     * The BLE scan can be only stopped if all the devices have been reported
     * BT connected and are not pending to report BLE lost.
     */
    private boolean canStopBleScan() {
        for (AssociationInfo ai : mAssociationStore.getActiveAssociations()) {
            int id = ai.getId();
            synchronized (mBtDisconnectedDevices) {
                if (ai.isNotifyOnDeviceNearby() && !(isBtConnected(id)
                        && mBtDisconnectedDevices.isEmpty())) {

                    Slog.i(TAG, "The BLE scan cannot be stopped, "
                            + "device( " + id + " ) is not yet connected "
                            + "Or it is pending to report BLE lost");
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Store the positive DevicePresenceEvent in the cache if the current device is still
     * locked.
     * Remove the current DevicePresenceEvent if there's a negative event occurs.
     */
    private void onDeviceLocked(int associationId, int userId, int event, ParcelUuid uuid) {
        switch (event) {
            case EVENT_BLE_APPEARED, EVENT_BT_CONNECTED -> {
                // Try to bind and notify the app after the phone is unlocked.
                Slog.i(TAG, "Current user is not in unlocking or unlocked stage yet. "
                        + "Notify the application when the phone is unlocked");
                synchronized (mPendingDevicePresenceEvents) {
                    final DevicePresenceEvent devicePresenceEvent = new DevicePresenceEvent(
                            associationId, event, uuid);
                    List<DevicePresenceEvent> deviceEvents = mPendingDevicePresenceEvents.get(
                            userId, new ArrayList<>());
                    deviceEvents.add(devicePresenceEvent);
                    mPendingDevicePresenceEvents.put(userId, deviceEvents);
                }
            }
            case EVENT_BLE_DISAPPEARED -> {
                synchronized (mPendingDevicePresenceEvents) {
                    List<DevicePresenceEvent> deviceEvents = mPendingDevicePresenceEvents
                            .get(userId);
                    if (deviceEvents != null) {
                        deviceEvents.removeIf(deviceEvent ->
                                deviceEvent.getEvent() == EVENT_BLE_APPEARED
                                        && Objects.equals(deviceEvent.getUuid(), uuid)
                                        && deviceEvent.getAssociationId() == associationId);
                    }
                }
            }
            case EVENT_BT_DISCONNECTED -> {
                // Do not need to report the event since the user is not unlock the
                // phone so that cdm is not bind with the app yet.
                synchronized (mPendingDevicePresenceEvents) {
                    List<DevicePresenceEvent> deviceEvents = mPendingDevicePresenceEvents
                            .get(userId);
                    if (deviceEvents != null) {
                        deviceEvents.removeIf(deviceEvent ->
                                deviceEvent.getEvent() == EVENT_BT_CONNECTED
                                        && Objects.equals(deviceEvent.getUuid(), uuid)
                                        && deviceEvent.getAssociationId() == associationId);
                    }
                }
            }
            default -> Slog.e(TAG, "Event: " + event + "is not supported");
        }
    }

    /**
     * Send the device presence event by userID when the device is unlocked.
     */
    public void sendDevicePresenceEventOnUnlocked(int userId) {
        final List<DevicePresenceEvent> deviceEvents = getPendingDevicePresenceEventsByUserId(
                userId);
        if (CollectionUtils.isEmpty(deviceEvents)) {
            return;
        }
        final List<ObservableUuid> observableUuids =
                mObservableUuidStore.getObservableUuidsForUser(userId);
        // Notify and bind the app after the phone is unlocked.
        for (DevicePresenceEvent deviceEvent : deviceEvents) {
            boolean isUuid = deviceEvent.getUuid() != null;
            if (isUuid) {
                for (ObservableUuid uuid : observableUuids) {
                    if (uuid.getUuid().equals(deviceEvent.getUuid())) {
                        onDevicePresenceEventByUuid(uuid, EVENT_BT_CONNECTED);
                    }
                }
            } else {
                int event = deviceEvent.getEvent();
                int associationId = deviceEvent.getAssociationId();
                final AssociationInfo associationInfo = mAssociationStore.getAssociationById(
                        associationId);

                if (associationInfo == null) {
                    return;
                }

                switch (event) {
                    case EVENT_BLE_APPEARED:
                        onBleCompanionDeviceFound(
                                associationInfo.getId(), associationInfo.getUserId());
                        break;
                    case EVENT_BT_CONNECTED:
                        onBluetoothCompanionDeviceConnected(
                                associationInfo.getId(), associationInfo.getUserId());
                        break;
                    default:
                        Slog.e(TAG, "Event: " + event + "is not supported");
                        break;
                }
            }
        }

        removePendingDevicePresenceEventsByUserId(userId);
    }

    private List<DevicePresenceEvent> getPendingDevicePresenceEventsByUserId(int userId) {
        synchronized (mPendingDevicePresenceEvents) {
            return mPendingDevicePresenceEvents.get(userId, new ArrayList<>());
        }
    }

    private void removePendingDevicePresenceEventsByUserId(int userId) {
        synchronized (mPendingDevicePresenceEvents) {
            if (mPendingDevicePresenceEvents.contains(userId)) {
                mPendingDevicePresenceEvents.remove(userId);
            }
        }
    }

    /**
     * Dumps system information about devices that are marked as "present".
     */
    public void dump(@NonNull PrintWriter out) {
        out.append("Companion Device Present: ");
        if (mConnectedBtDevices.isEmpty()
                && mNearbyBleDevices.isEmpty()
                && mConnectedSelfManagedDevices.isEmpty()) {
            out.append("<empty>\n");
            return;
        } else {
            out.append("\n");
        }

        out.append("  Connected Bluetooth Devices: ");
        if (mConnectedBtDevices.isEmpty()) {
            out.append("<empty>\n");
        } else {
            out.append("\n");
            for (int associationId : mConnectedBtDevices) {
                AssociationInfo a = mAssociationStore.getAssociationById(associationId);
                out.append("    ").append(a.toShortString()).append('\n');
            }
        }

        out.append("  Nearby BLE Devices: ");
        if (mNearbyBleDevices.isEmpty()) {
            out.append("<empty>\n");
        } else {
            out.append("\n");
            for (int associationId : mNearbyBleDevices) {
                AssociationInfo a = mAssociationStore.getAssociationById(associationId);
                out.append("    ").append(a.toShortString()).append('\n');
            }
        }

        out.append("  Self-Reported Devices: ");
        if (mConnectedSelfManagedDevices.isEmpty()) {
            out.append("<empty>\n");
        } else {
            out.append("\n");
            for (int associationId : mConnectedSelfManagedDevices) {
                AssociationInfo a = mAssociationStore.getAssociationById(associationId);
                out.append("    ").append(a.toShortString()).append('\n');
            }
        }

        out.append("  SelfManaged-Nearby Devices: ");
        if (mSelfManagedNearByDevices.isEmpty()) {
            out.append("<empty>\n");
        } else {
            out.append("\n");
            for (int associationId : mSelfManagedNearByDevices) {
                AssociationInfo a = mAssociationStore.getAssociationById(associationId);
                out.append("    ").append(a.toShortString()).append('\n');
            }
        }
    }

    private class SimulatedDevicePresenceSchedulerHelper extends Handler {
        SimulatedDevicePresenceSchedulerHelper() {
            super(Looper.getMainLooper());
        }

        void scheduleOnDeviceGoneCallForSimulatedDevicePresence(int associationId) {
            // First, unschedule if it was scheduled previously.
            if (hasMessages(/* what */ associationId)) {
                removeMessages(/* what */ associationId);
            }

            sendEmptyMessageDelayed(/* what */ associationId, 60 * 1000 /* 60 seconds */);
        }

        void unscheduleOnDeviceGoneCallForSimulatedDevicePresence(int associationId) {
            removeMessages(/* what */ associationId);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            final int associationId = msg.what;
            if (mSimulated.contains(associationId)) {
                onDevicePresenceEvent(mSimulated, associationId,
                        new DevicePresenceEvent(associationId, EVENT_BLE_DISAPPEARED, null));
            }
        }
    }

    private class BleDeviceDisappearedScheduler extends Handler {
        BleDeviceDisappearedScheduler() {
            super(Looper.getMainLooper());
        }

        void scheduleBleDeviceDisappeared(int associationId) {
            if (hasMessages(associationId)) {
                removeMessages(associationId);
            }
            Slog.i(TAG, "scheduleBleDeviceDisappeared for Device: ( " + associationId + " ).");
            sendEmptyMessageDelayed(associationId, 10 * 1000 /* 10 seconds */);
        }

        void unScheduleDeviceDisappeared(int associationId) {
            if (hasMessages(associationId)) {
                Slog.i(TAG, "unScheduleDeviceDisappeared for Device( " + associationId + " )");
                synchronized (mBtDisconnectedDevices) {
                    mBtDisconnectedDevices.remove(associationId);
                    mBtDisconnectedDevicesBlePresence.delete(associationId);
                }

                removeMessages(associationId);
            }
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            final int associationId = msg.what;
            synchronized (mBtDisconnectedDevices) {
                final boolean isCurrentPresent = mBtDisconnectedDevicesBlePresence.get(
                        associationId);
                // If a device hasn't reported after 10 seconds and is not currently present,
                // assume BLE is lost and trigger the onDeviceEvent callback with the
                // EVENT_BLE_DISAPPEARED event.
                if (mBtDisconnectedDevices.contains(associationId)
                        && !isCurrentPresent) {
                    Slog.i(TAG, "Device ( " + associationId + " ) is likely BLE out of range, "
                            + "sending callback with event ( " + EVENT_BLE_DISAPPEARED + " )");
                    onDevicePresenceEvent(mNearbyBleDevices, associationId,
                            new DevicePresenceEvent(associationId, EVENT_BLE_DISAPPEARED, null));
                }

                mBtDisconnectedDevices.remove(associationId);
                mBtDisconnectedDevicesBlePresence.delete(associationId);
            }
        }
    }

    private void notifyAndExemptApp(
            int eventType, AssociationInfo associationInfo, String packageName, int userId) {
        final DevicePresenceEvent event = new DevicePresenceEvent(
                associationInfo.getId(), eventType, null);
        bindApplicationIfNeeded(userId, packageName, associationInfo.isSelfManaged());
        mCompanionExemptionProcessor.exemptPackage(
                userId, packageName, /* hasPresentDevices */ true);
        notifyDevicePresenceEvent(userId, packageName, associationInfo.getDeviceProfile(), event);
    }

    private void unbindAndRemoveExemptionForApp(int userId, String packageName) {
        mCompanionAppBinder.unbindCompanionApp(userId, packageName);
        mCompanionExemptionProcessor.exemptPackage(userId, packageName, false);
    }

    private void processNotifyAssociationRemoved(int userId, @NonNull String packageName,
            @NonNull DevicePresenceEvent event, String deviceProfile, boolean isSelfManaged) {
        bindApplicationIfNeeded(userId, packageName, isSelfManaged);
        notifyDevicePresenceEvent(userId, packageName, deviceProfile, event);

        if (!shouldBindPackage(userId, packageName)) {
            mCompanionAppBinder.unbindCompanionApp(userId, packageName);
        } else {
            Slog.i(TAG, "Not unbinding package " + packageName
                    + " as other associations are still present.");
        }
    }

    private void broadcastDevicePresenceEvents(@NonNull DevicePresenceEvent event) {
        Slog.i(TAG, "Broadcasting DevicePresenceEvent: " + event);
        final int associationId = event.getAssociationId();

        // Iterate over all registered service listeners.
        for (DevicePresenceListener devicePresenceListener : mDevicePresenceListener) {
            if (devicePresenceListener.mAssociationIdFilter.contains(associationId)) {
                try {
                    Slog.d(TAG, "  - Sending presence event for service: "
                            + devicePresenceListener.mServiceName);
                    devicePresenceListener.mListener.onDevicePresence(event);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to send presence event to listener for service '"
                            + devicePresenceListener.mServiceName + "'.", e);
                }
            }
        }
    }

    private static class DevicePresenceListener {
        @NonNull
        final String mServiceName;
        @NonNull
        final IOnDevicePresenceEventListener mListener;
        @NonNull
        final Set<Integer> mAssociationIdFilter;

        DevicePresenceListener(@NonNull String serviceName,
                @NonNull IOnDevicePresenceEventListener listener,
                @NonNull Set<Integer> filter) {
            this.mServiceName = serviceName;
            this.mListener = listener;
            this.mAssociationIdFilter = filter;
        }
    }
}
