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
package com.android.server.companion.datatransfer.crossdevicesync.metadata;

import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_METADATA;

import android.companion.CompanionDeviceManager;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.companion.datatransfer.crossdevicesync.common.Clock;
import com.android.server.companion.datatransfer.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.Network;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.NetworkListener;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.RemoteDevice;
import com.android.server.companion.datatransfer.crossdevicesync.user.UserHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/** Default implementation of {@link MetadataPublisher}. */
public class MetadataPublisherImpl
        implements MetadataPublisher, NetworkListener, UserHelper.UserListener {
    private static final String TAG = "MetadataPublisher";

    /** Id of the network for pushing metadata to devices owned by individual users. */
    @VisibleForTesting static final String NETWORK_ID = "metadata_publisher";

    /** Id of the network for pushing metadata to any devices. */
    @VisibleForTesting static final String NETWORK_ID_USER_ALL = NETWORK_ID + "_all";

    @VisibleForTesting
    static final String LAST_METADATA_UPDATE_TIMESTAMP_PREFIX = "last_metadata_update_timestamp_";

    private final Object mLock = new Object();
    private final SharedPreferences mSharedPreferences;
    private final NetworkManager mNetworkManager;
    private final CompanionDeviceManagerProxy mCompanionDeviceManager;
    private final Clock mClock;
    private final Executor mMainExecutor;
    private final UserHelper mUserHelper;

    @GuardedBy("mLock")
    private final Map<Integer, Map<String, Object>> mStagingMetadata = new HashMap<>();

    @GuardedBy("mLock")
    private final Map<Integer, Network> mUserNetworks = new HashMap<>();

    @GuardedBy("mLock")
    private final Map<Integer, Long> mUserBroadcastMessages = new HashMap<>();

    @GuardedBy("mLock")
    private final Map<Integer, Map<Integer, Long>> mUserUnicastMessages = new HashMap<>();

    @GuardedBy("mLock")
    private boolean mInitialized;

    public MetadataPublisherImpl(
            SharedPreferences sharedPreferences,
            NetworkManager networkManager,
            CompanionDeviceManagerProxy companionDeviceManagerProxy,
            Clock clock,
            Executor mainExecutor,
            UserHelper userHelper) {
        mSharedPreferences = sharedPreferences;
        mNetworkManager = networkManager;
        mCompanionDeviceManager = companionDeviceManagerProxy;
        mClock = clock;
        mMainExecutor = mainExecutor;
        mUserHelper = userHelper;
    }

    @Override
    public void init() {
        synchronized (mLock) {
            if (mInitialized) {
                throw new IllegalStateException("init: already initialized");
            }
            mInitialized = true;
            mUserHelper.registerUserListener(mMainExecutor, this);
            for (Map.Entry<Integer, Long> entry :
                    getLastModifiedTimestampForAllUsers().entrySet()) {
                int userId = entry.getKey();
                // Always create network for users who have ever modified metadata.
                getOrCreateNetworkForUserLocked(userId);
            }
        }
    }

    private Map<Integer, Long> getLastModifiedTimestampForAllUsers() {
        Map<Integer, Long> res = new HashMap<>();
        for (Map.Entry<String, ?> entry : mSharedPreferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX)) {
                continue;
            }
            try {
                int userId =
                        Integer.parseInt(
                                key.substring(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX.length()));
                long lastModifiedTimestamp = (long) entry.getValue();
                res.put(userId, lastModifiedTimestamp);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse metadata update timestamp!", e);
            }
        }
        return res;
    }

    @GuardedBy("mLock")
    private Network getOrCreateNetworkForUserLocked(int userId) {
        Network network = mUserNetworks.get(userId);
        if (network != null) {
            return network;
        }
        Log.i(TAG, "Creating network for user " + userId);
        // Need a different network id for USER_ALL since it cannot share the same id with per-user
        // network.
        String networkId = userId == UserHandle.USER_ALL ? NETWORK_ID_USER_ALL : NETWORK_ID;
        network =
                mNetworkManager.createNetworkForUser(
                        userId,
                        networkId,
                        CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_METADATA,
                        device -> true);
        mUserNetworks.put(userId, network);
        network.registerListener(mMainExecutor, this);
        return network;
    }

    @Override
    public void onNetworkMessage(int associationId, byte[] message) {
        Log.i(TAG, "Received metadata ping from association " + associationId);
    }

    @Override
    public void onDeviceChanged(RemoteDevice device) {
        int userId = device.getUserId();
        synchronized (mLock) {
            if (mUserBroadcastMessages.containsKey(userId)
                    || mUserBroadcastMessages.containsKey(UserHandle.USER_ALL)) {
                // Already entered broadcast phase for user. No need to send unicast ping.
                return;
            }
            long lastSentTimestamp = device.getAssociationInfoCache().getMetadataSentTimestamp();
            if (getLastModifiedTimestampForUser(device.getUserId()) > lastSentTimestamp) {
                // Send unicast ping to the target device for pushing user metadata.
                unicastPingLocked(userId, device.getAssociationId());
            } else if (getLastModifiedTimestampForUser(UserHandle.USER_ALL) > lastSentTimestamp) {
                // Send unicast ping to the target device for pushing USER_ALL metadata.
                unicastPingLocked(UserHandle.USER_ALL, device.getAssociationId());
            }
        }
    }

    private long getLastModifiedTimestampForUser(int userId) {
        String key = LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId;
        return mSharedPreferences.getLong(key, -1);
    }

    @GuardedBy("mLock")
    private void unicastPingLocked(int userId, int associationId) {
        Network network = getOrCreateNetworkForUserLocked(userId);

        // Check existing unicast ping first.
        if (mUserUnicastMessages.containsKey(userId)) {
            Map<Integer, Long> unicastMessages = mUserUnicastMessages.get(userId);
            if (unicastMessages.containsKey(associationId)) {
                long msgId = unicastMessages.get(associationId);
                if (network.isMessagePending(msgId)) {
                    // Last ping is still pending. No need to create a new ping.
                    return;
                }
            }
        }

        // Send a unicast ping to trigger a scanning/transport request and let CDM push the
        // metadata to the target remote device. The message itself is empty since we only
        // care about establishing a CDM transport. CDM will take care of sending the real
        // metadata out.
        long msgId =
                network.unicastMessage(
                        associationId,
                        /* message= */ new byte[0],
                        Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT | Network.MESSAGE_FLAG_STICKY);
        mUserUnicastMessages
                .computeIfAbsent(userId, k -> new HashMap<>())
                .put(associationId, msgId);
        Log.i(TAG, "Pinging association " + associationId + " for metadata push.");
    }

    @GuardedBy("mLock")
    private void broadcastPingLocked(int userId) {
        Network network = getOrCreateNetworkForUserLocked(userId);
        // Cancel all existing unicast and broadcast for the user.
        if (mUserUnicastMessages.containsKey(userId)) {
            for (Long msgId : mUserUnicastMessages.get(userId).values()) {
                network.cancelMessage(msgId);
            }
            mUserUnicastMessages.remove(userId);
        }
        if (mUserBroadcastMessages.containsKey(userId)) {
            network.cancelMessage(mUserBroadcastMessages.get(userId));
        }

        // Send a broadcast ping to trigger a scanning/transport request and let CDM push the
        // metadata to the target remote devices. The message itself is empty since we only
        // care about establishing a CDM transport. CDM will take care of sending the real
        // metadata out.
        long msgId =
                network.broadcastMessage(
                        /* message= */ new byte[0],
                        Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT | Network.MESSAGE_FLAG_STICKY);
        mUserBroadcastMessages.put(userId, msgId);
        Log.i(TAG, "Pinging all associations owned by user " + userId + " for metadata push.");
    }

    @Override
    public void onUserRemoved(UserHandle user) {
        synchronized (mLock) {
            if (!mInitialized) {
                return;
            }
            Log.i(TAG, "Cleaning up metadata for removed user " + user.getIdentifier());
            Network network = mUserNetworks.remove(user.getIdentifier());
            if (network != null) {
                network.destroy();
            }
            mUserUnicastMessages.remove(user.getIdentifier());
            mUserBroadcastMessages.remove(user.getIdentifier());
            mStagingMetadata.remove(user.getIdentifier());
            mSharedPreferences
                    .edit()
                    .remove(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + user.getIdentifier())
                    .apply();
            mCompanionDeviceManager.setLocalMetadata(
                    user.getIdentifier(), CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC, null);
        }
    }

    @Override
    public void destroy() {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            mInitialized = false;
            mUserNetworks.values().forEach(Network::destroy);
            mUserNetworks.clear();
            mUserUnicastMessages.clear();
            mUserBroadcastMessages.clear();
            mUserHelper.unregisterUserListener(this);
        }
    }

    @GuardedBy("mLock")
    private void throwIfUninitializedLocked() {
        if (!mInitialized) {
            throw new IllegalStateException("Not initialized!");
        }
    }

    @Override
    public void putBooleanMetaData(int userId, String key, boolean val) {
        putObject(userId, key, val);
    }

    @Override
    public void putIntMetaData(int userId, String key, int val) {
        putObject(userId, key, val);
    }

    @Override
    public void putStringMetaData(int userId, String key, String val) {
        putObject(userId, key, val);
    }

    private void putObject(int userId, String key, Object val) {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            boolean wasEmpty = mStagingMetadata.isEmpty();
            Map<String, Object> userMetadata =
                    mStagingMetadata.computeIfAbsent(userId, k -> new HashMap<>());
            userMetadata.put(key, val);
            if (wasEmpty) {
                // flushStagingMetadata() will clear the staging data. So if the staging data was
                // not empty, it means we already have a pending flush in the queue. In that case,
                // we don't have to post another flush runnable.
                mMainExecutor.execute(this::flushStagingMetadata);
            }
        }
    }

    private void flushStagingMetadata() {
        synchronized (mLock) {
            if (!mInitialized) {
                return;
            }
            Set<Integer> aliveUsers = new HashSet<>();
            aliveUsers.add(UserHandle.USER_ALL);
            mUserHelper.getAliveUsers().forEach(userInfo -> aliveUsers.add(userInfo.id));
            for (Entry<Integer, Map<String, Object>> entry : mStagingMetadata.entrySet()) {
                int userId = entry.getKey();
                if (!aliveUsers.contains(userId)) {
                    Log.w(TAG, "Ignoring metadata for unknown user " + userId + ".");
                    continue;
                }
                Map<String, Object> userMetadata = entry.getValue();
                PersistableBundle bundle =
                        mCompanionDeviceManager
                                .getLocalMetadata(userId)
                                .getPersistableBundle(
                                        CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC);
                if (bundle == null) {
                    bundle = new PersistableBundle();
                }
                boolean changed = false;
                for (Entry<String, Object> metadataEntry : userMetadata.entrySet()) {
                    String key = metadataEntry.getKey();
                    Object val = metadataEntry.getValue();
                    if (!Objects.equals(bundle.get(key), val)) {
                        changed = true;
                        bundle.putObject(key, val);
                    }
                }
                if (!changed) {
                    // Unchanged.
                    continue;
                }
                long now = mClock.currentTimeMillis();
                mCompanionDeviceManager.setLocalMetadata(
                        userId, CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC, bundle);
                mSharedPreferences
                        .edit()
                        .putLong(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId, now)
                        .apply();
                broadcastPingLocked(userId);
            }
            mStagingMetadata.clear();
        }
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        synchronized (mLock) {
            pw.println("MetadataPublisher");
            pw.increaseIndent();
            pw.println("mInitialized=" + mInitialized);
            pw.println("mUserBroadcastMessages=" + mUserBroadcastMessages);
            pw.println("mUserUnicastMessages=" + mUserUnicastMessages);
            pw.println("mStagingMetadata=" + mStagingMetadata);
            pw.decreaseIndent();
        }
    }
}
