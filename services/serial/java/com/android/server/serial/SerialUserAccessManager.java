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

import static android.hardware.serial.flags.Flags.persistentAccess;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.serial.SerialManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseBooleanArray;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class manages app accesses to serial devices.
 */
class SerialUserAccessManager implements SerialUserAccessManagerInterface {
    private static final String TAG = "SerialUserAccessManager";

    private final Context mContext;

    /**
     * A list of serial port names configured in the internal configuration. Apps with
     * {@link Manifest.permission#SERIAL_PORT} are granted accesses to them automatically.
     */
    private final String[] mPortsInConfig;

    /**
     * Component name of the activity that shows the request for access to a serial port.
     */
    private final String mDialogComponent;

    /**
     * The serializer to read and write port access.
     */
    private final PortAccessSerializerInterface mPortAccessSerializer;

    /**
     * Mapping of serial port names to list of UIDs with temporary accesses for the device. Each
     * entry lasts until device is disconnected or the Android device reboots. These are accesses
     * granted via the access request dialog.
     */
    @GuardedBy("mLock")
    private final ArrayMap<String, SparseBooleanArray> mPortAccessMap = new ArrayMap<>();

    /**
     * Mapping of serial port names to list of UIDs with persistent access or denials for the
     * device. These are accesses granted via the access request dialog with responses to persist
     * the result.
     * <p>
     * If there is no record in {@link #mPortAccessMap} for a combination of serial port name and
     * UID, and there is no record in this map, the access request dialog should be presented to the
     * user.
     * <p>
     * If there is no record in {@link #mPortAccessMap} for a combination of serial port name and
     * UID, and records are present in this map, automatic grants or denials should be made
     * according the record in this map.
     * <p>
     * No records of the same serial port and the UID should be present in both maps at any given
     * time when no one is holding {@link #mLock}.
     */
    @GuardedBy("mLock")
    private final LazyInitWrapper<ArrayMap<String, SparseBooleanArray>> mPersistentPortAccessMap;

    @GuardedBy("mLock")
    private final List<AccessToPortRequest> mPendingAccessRequests = new ArrayList<>();

    private final Object mLock = new Object();

    /**
     * The {@link Future} of the latest task scheduled to write {@link #mPersistentPortAccessMap}.
     */
    @GuardedBy("mLock")
    private Future<Void> mPersistentPortAccessMapWriteFuture;

    SerialUserAccessManager(Context context, String[] portsInConfig, String dialogComponent,
            PortAccessSerializerInterface serializer, int userId) {
        mContext = context;
        mPortsInConfig = portsInConfig;
        mDialogComponent = dialogComponent;
        mPortAccessSerializer = serializer;

        if (persistentAccess()) {
            final Future<ArrayMap<String, SparseBooleanArray>> mapFuture =
                    mPortAccessSerializer.loadPortAccessForUser(userId);
            mPersistentPortAccessMap = LazyInitWrapper.create(() -> {
                try {
                    return mapFuture.get();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    Slog.e(TAG, "Failed to load persistent port access. User may lose access to "
                            + "serial ports.");
                    return new ArrayMap<>();
                }
            });
        } else {
            mPersistentPortAccessMap = null;
        }
    }

    @Override
    public void requestAccess(String requestedPortName, int pid, int uid,
            String requestingPackageName, AccessToPortDecidedCallback callback) {
        if (hasAccessToPort(requestedPortName, pid, uid)) {
            callback.onAccessToPortDecided(requestedPortName, pid, uid, /* granted */ true);
            return;
        }

        if (persistentAccess()) {
            // Persistent denial
            final SparseBooleanArray uidToGranted =
                    mPersistentPortAccessMap.get().get(requestedPortName);
            if (uidToGranted != null && !uidToGranted.get(uid, true)) {
                callback.onAccessToPortDecided(requestedPortName, pid, uid, /* granted */ false);
                return;
            }
        }

        final AccessToPortRequest request = new AccessToPortRequest(requestedPortName, pid, uid,
                requestingPackageName, callback);
        synchronized (mLock) {
            mPendingAccessRequests.add(request);
        }

        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.startActivityAsUser(
                    request.getAccessDialogIntent(), UserHandle.of(UserHandle.getUserId(uid)));
        } catch (RuntimeException e) {
            Slog.e(TAG, "Failed to start access dialog", e);
            synchronized (mLock) {
                mPendingAccessRequests.remove(request);
            }
            request.notifyRequestResult(/* granted */ false);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private boolean hasAccessToPort(String requestedPortName, int pid, int uid) {
        if (canGrantPortAccessAutomatically(requestedPortName, pid, uid)) {
            return true;
        }

        synchronized (mLock) {
            if (!persistentAccess()) {
                final SparseBooleanArray uidToGranted = mPortAccessMap.get(requestedPortName);
                if (uidToGranted == null) {
                    return false;
                }
                return uidToGranted.get(uid);
            }

            return hasAccessToPortInMap(requestedPortName, uid, mPortAccessMap)
                    || hasAccessToPortInMap(requestedPortName, uid, mPersistentPortAccessMap.get());
        }
    }

    @GuardedBy("mLock")
    private static boolean hasAccessToPortInMap(
            String requestedPortName, int uid, ArrayMap<String, SparseBooleanArray> map) {
        final SparseBooleanArray uidToGranted = map.get(requestedPortName);
        if (uidToGranted == null) {
            return false;
        }
        return uidToGranted.get(uid);
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private boolean canGrantPortAccessAutomatically(String requestedPortName, int pid, int uid) {
        if (!SerialManagerService.hasSerialPortPermission(mContext, pid, uid)) {
            return false;
        }

        for (int i = 0; i < mPortsInConfig.length; ++i) {
            if (requestedPortName.equals(mPortsInConfig[i])) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void grantAccess(String portName, int uid, boolean persistent, @Nullable IBinder token) {
        updatePortAccess(portName, uid, token, persistent, /* granted */ true);
    }

    @Override
    public void revokeAccess(
            String portName, int uid, boolean persistent, @Nullable IBinder token) {
        updatePortAccess(portName, uid, token, persistent, /* granted */ false);
    }

    /**
     * Updates the temporary access to a serial port for a specific UID.
     * <p>
     * If token == null, the access lasts until the device is disconnected,
     * otherwise the access is used only once, for the caller with the given token.
     *
     * @param portName The name of the serial port.
     * @param uid The user ID to revoke access from.
     * @param persistent Whether this change should be persistent
     * @param token An optional token associated with the revocation.
     */
    private void updatePortAccess(String portName, int uid, @Nullable IBinder token,
            boolean persistent, boolean granted) {
        Slog.d(TAG, "updatePortAccess(" + portName + ", " + uid + ", " + persistent + ", "
                + granted + ")");
        synchronized (mLock) {
            final boolean writeToDisk;
            if (persistentAccess()) {
                if (persistent) {
                    writeToDisk = updatePortAccessInMap(
                            portName, uid, granted, mPersistentPortAccessMap.get());
                    removeAccessRecordInMap(portName, uid, mPortAccessMap);
                } else {
                    updatePortAccessInMap(portName, uid, granted, mPortAccessMap);
                    writeToDisk =
                            removeAccessRecordInMap(portName, uid, mPersistentPortAccessMap.get());
                }
            } else {
                SparseBooleanArray uidToGranted = mPortAccessMap.get(portName);
                if (uidToGranted == null) {
                    uidToGranted = new SparseBooleanArray();
                    mPortAccessMap.put(portName, uidToGranted);
                }
                uidToGranted.put(uid, granted);

                writeToDisk = false;
            }

            final AccessToPortRequest pendingRequest =
                    findAndRemovePendingRequest(portName, uid, token);
            if (pendingRequest != null) {
                pendingRequest.notifyRequestResult(granted);
            }

            if (!writeToDisk) {
                return;
            }
            savePortAccess(UserHandle.getUserId(uid));
        }
    }

    /**
     * Updates the record in the given map.
     *
     * @return {@code true} if the record is updated, {@code false} if not.
     */
    private static boolean updatePortAccessInMap(
            String portName, int uid, boolean granted, ArrayMap<String, SparseBooleanArray> map) {
        SparseBooleanArray uidToGranted = map.get(portName);
        if (uidToGranted == null) {
            uidToGranted = new SparseBooleanArray();
            map.put(portName, uidToGranted);
        }
        if (uidToGranted.get(uid, !granted) == granted) {
            return false;
        }
        uidToGranted.put(uid, granted);
        return true;
    }

    /**
     * Removes the record in the given map.
     *
     * @return {@code true} if the record is removed, {@code false} if not.
     */
    private static boolean removeAccessRecordInMap(
            String portName, int uid, ArrayMap<String, SparseBooleanArray> map) {
        SparseBooleanArray uidToGranted = map.get(portName);
        if (uidToGranted == null) {
            return false;
        }
        final int idx = uidToGranted.indexOfKey(uid);
        if (idx < 0) {
            return false;
        }
        uidToGranted.removeAt(idx);
        return true;
    }

    @GuardedBy("mLock")
    @Nullable
    private AccessToPortRequest findAndRemovePendingRequest(
            String portName, int uid, @Nullable IBinder token) {
        if (token == null) {
            return null;
        }
        for (int i = 0; i < mPendingAccessRequests.size(); ++i) {
            final AccessToPortRequest request = mPendingAccessRequests.get(i);
            if (request.mToken == token) {
                if (request.mRequestingUid != uid || !portName.equals(request.mRequestedPort)) {
                    throw new IllegalStateException("Found a wrong pending request");
                }
                mPendingAccessRequests.remove(i);
                return request;
            }
        }
        return null;
    }

    private void savePortAccess(int userId) {
        if (mPersistentPortAccessMapWriteFuture != null) {
            mPersistentPortAccessMapWriteFuture.cancel(false);
        }
        mPersistentPortAccessMapWriteFuture = mPortAccessSerializer.savePortAccessForUser(
                userId, mPersistentPortAccessMap.get());
    }

    @Override
    public void onPortRemoved(String name) {
        synchronized (mLock) {
            mPortAccessMap.remove(name);
            // Persistent access should be left intact.
        }
    }

    @Override
    public void onUserStopping() {
        if (mPersistentPortAccessMapWriteFuture == null) {
            return;
        }

        try {
            mPersistentPortAccessMapWriteFuture.get(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException | TimeoutException e) {
            Slog.e(TAG, "The last task to write persistent map isn't successful.", e);
        }
    }

    @Override
    public void clearUserAccess(int userId) {
        synchronized (mLock) {
            mPortAccessMap.clear();
            boolean writeToDisk = !mPersistentPortAccessMap.get().isEmpty();
            mPersistentPortAccessMap.get().clear();
            if (writeToDisk) {
                savePortAccess(userId);
            }
        }
    }

    private class AccessToPortRequest {
        /**
         * The unique token of this request
         */
        private final Binder mToken = new Binder();

        private final String mRequestedPort;

        private final int mRequestingPid;

        private final int mRequestingUid;

        private final String mRequestingPackageName;

        private final AccessToPortDecidedCallback mCallback;

        private AccessToPortRequest(String requestedPort, int requestingPid, int requestingUid,
                String requestingPackageName, AccessToPortDecidedCallback callback) {
            mRequestedPort = requestedPort;
            mRequestingPid = requestingPid;
            mRequestingUid = requestingUid;
            mRequestingPackageName = requestingPackageName;
            mCallback = callback;
        }

        private Intent getAccessDialogIntent() {
            Intent intent = new Intent();
            intent.setComponent(ComponentName.unflattenFromString(mDialogComponent));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Bundle binderExtras = new Bundle();
            binderExtras.putBinder(SerialManager.EXTRA_REQUEST_TOKEN, mToken);
            intent.putExtras(binderExtras);
            intent.putExtra(SerialManager.EXTRA_PORT, mRequestedPort);
            intent.putExtra(SerialManager.EXTRA_PACKAGE_NAME, mRequestingPackageName);
            intent.putExtra(SerialManager.EXTRA_UID, mRequestingUid);
            return intent;
        }

        private void notifyRequestResult(boolean granted) {
            mCallback.onAccessToPortDecided(
                    mRequestedPort, mRequestingPid, mRequestingUid, granted);
        }
    }
}
