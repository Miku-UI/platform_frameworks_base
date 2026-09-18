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
package com.android.server.companion.datatransfer.crossdevicesync.network.fake;

import android.companion.AssociationInfo;
import android.os.UserHandle;
import android.util.IndentingPrintWriter;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class FakeNetworkManager implements NetworkManager {

    private final Map<Integer, FakeRemoteDevice> mRemoteDevices = new HashMap<>();
    private final Map<String, Map<Integer, FakeNetwork>> mNetworks = new HashMap<>();
    private boolean mInitialized = false;
    private int mNextAssociationId = 1;

    public FakeNetworkManager() {}

    @Override
    public void init() {
        if (mInitialized) {
            throw new IllegalStateException("Already initialized!");
        }
        mInitialized = true;
    }

    @Override
    public void destroy() {
        throwIfUninitialized();
        new ArrayList<>(mNetworks.values())
                .forEach(
                        userNetworks ->
                                new ArrayList<>(userNetworks.values())
                                        .forEach(FakeNetwork::destroy));
        mNetworks.clear();
        mRemoteDevices.clear();
        mInitialized = false;
    }

    @Override
    public Map<Integer, RemoteDevice> getRemoteDevices() {
        throwIfUninitialized();
        return Collections.unmodifiableMap(mRemoteDevices);
    }

    @Nullable
    @Override
    public FakeRemoteDevice getRemoteDevice(int associationId) {
        return mRemoteDevices.get(associationId);
    }

    @Override
    public List<Network> getNetworks() {
        throwIfUninitialized();
        List<Network> networks = new ArrayList<>();
        forEachNetwork(networks::add);
        return networks;
    }

    @Override
    public Network createNetworkForUser(
            int userId,
            String networkId,
            int feature,
            Predicate<RemoteDevice> remoteDeviceCondition) {
        throwIfUninitialized();
        FakeNetwork network = getNetwork(networkId, userId);
        if (network != null) {
            return network;
        }
        network = new FakeNetwork(userId, networkId, feature, remoteDeviceCondition);
        mNetworks.computeIfAbsent(networkId, k -> new HashMap<>()).put(userId, network);
        network.invalidate();
        return network;
    }

    /** Returns the network with the given ID and user ID. */
    public FakeNetwork getNetwork(String networkId, int userId) {
        Map<Integer, FakeNetwork> userNetworkMap = mNetworks.get(networkId);
        if (userNetworkMap == null || userNetworkMap.isEmpty()) {
            return null;
        }
        FakeNetwork network = userNetworkMap.get(userId);
        if (network != null) {
            // Match exact user id first.
            return network;
        }
        if (userId == UserHandle.USER_ALL) {
            // Match any network if we want USER_ALL.
            return userNetworkMap.values().iterator().next();
        }
        // We want a specific user but can't find a network with the exact same user. Match against
        // USER_ALL.
        return userNetworkMap.get(UserHandle.USER_ALL);
    }

    @Override
    public void invalidateNetworks() {
        throwIfUninitialized();
        forEachNetwork(FakeNetwork::invalidate);
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        throwIfUninitialized();
        // No-op
    }

    private void throwIfUninitialized() {
        if (!mInitialized) {
            throw new IllegalStateException("NetworkManager not initialized!");
        }
    }

    private void forEachNetwork(Consumer<FakeNetwork> consumer) {
        mNetworks.values().forEach(userNetworks -> userNetworks.values().forEach(consumer));
    }

    /** Returns whether the manager is initialized. */
    public boolean isInitialized() {
        return mInitialized;
    }

    /** Adds a remote device to this manager for testing. */
    public FakeRemoteDevice addRemoteDevice() {
        return addRemoteDeviceForUser(UserHandle.USER_SYSTEM);
    }

    /** Adds a remote device to this manager for testing. */
    public FakeRemoteDevice addRemoteDeviceForUser(int userId) {
        FakeRemoteDevice device = new FakeRemoteDevice(mNextAssociationId++, userId);
        addRemoteDevice(device);
        return device;
    }

    /** Adds a remote device to this manager for testing. */
    public FakeRemoteDevice addRemoteDevice(AssociationInfo associationInfo) {
        FakeRemoteDevice device = new FakeRemoteDevice(associationInfo);
        addRemoteDevice(device);
        return device;
    }

    /** Adds a remote device to this manager for testing. */
    public void addRemoteDevice(FakeRemoteDevice device) {
        throwIfUninitialized();
        if (mRemoteDevices.containsKey(device.getAssociationId())) {
            throw new IllegalStateException(
                    "Device with association ID " + device.getAssociationId() + " already exists.");
        }
        mRemoteDevices.put(device.getAssociationId(), device);
        forEachNetwork(n -> n.maybeAddDevice(device));
    }

    /** Connect with a remote network manager for exchanging messages with the other device. */
    public void connect(FakeNetworkManager remoteNetworkManager) {
        maybeConnectToRemoteNetworkManager(remoteNetworkManager);
        remoteNetworkManager.maybeConnectToRemoteNetworkManager(this);
    }

    private void maybeConnectToRemoteNetworkManager(FakeNetworkManager remoteNetworkManager) {
        if (findRemoteDevice(remoteNetworkManager) != null) {
            // Already connected.
            return;
        }
        addRemoteDevice().setRemoteNetworkManager(remoteNetworkManager);
    }

    /** Returns the {@link FakeRemoteDevice} that's using the given {@link FakeNetworkManager}. */
    @Nullable
    public FakeRemoteDevice findRemoteDevice(FakeNetworkManager remoteNetworkManager) {
        for (FakeRemoteDevice device : mRemoteDevices.values()) {
            if (device.mRemoteNetworkManager == remoteNetworkManager) {
                return device;
            }
        }
        return null;
    }

    /** Removes a remote device from this manager for testing. */
    public void removeRemoteDevice(int associationId) {
        throwIfUninitialized();
        mRemoteDevices.remove(associationId);
        forEachNetwork(n -> n.removeDevice(associationId));
    }

    /**
     * Flushes all messages to the remote devices to simulate message received by remote devices.
     *
     * @return whether there were any messages flushed.
     */
    public boolean flushAllMessages() {
        return mRemoteDevices.values().stream()
                .map(device -> device.flushMessages(this))
                .reduce((a, b) -> a || b)
                .orElse(false);
    }

    private void onMessageReceived(
            FakeNetworkManager remoteNetworkManager, String networkId, byte[] message) {
        if (!mInitialized) {
            return;
        }
        FakeRemoteDevice device = findRemoteDevice(remoteNetworkManager);
        if (device == null) {
            return;
        }
        FakeNetwork network = getNetwork(networkId, device.getUserId());
        if (network == null || !network.mAssociationIds.contains(device.getAssociationId())) {
            return;
        }
        network.receiveMessage(device.getAssociationId(), message);
    }

    /** A fake implementation of {@link RemoteDevice} for testing. */
    public static class FakeRemoteDevice implements RemoteDevice {
        private final int mAssociationId;
        private final int mUserId;
        private final Queue<SentMessage> mSentMessages = new LinkedList<>();
        private CharSequence mDisplayName;
        private String mDeviceProfile;
        private boolean mHasTransport;
        private boolean mIsBleAppeared;
        private boolean mIsBtConnected;
        private boolean mIsSelfManagedAppeared;
        private boolean mIsSelfManagedNearby;
        private boolean mIsAssociationRemoved;
        @Nullable FakeNetworkManager mRemoteNetworkManager;
        private AssociationInfo mAssociationInfoCache;

        public FakeRemoteDevice(int associationId, int userId) {
            this(
                    new AssociationInfo.Builder(associationId, userId, "package")
                            .setDisplayName("name")
                            .build());
        }

        public FakeRemoteDevice(AssociationInfo associationInfo) {
            mAssociationId = associationInfo.getId();
            mUserId = associationInfo.getUserId();
            mDeviceProfile = associationInfo.getDeviceProfile();
            mDisplayName = associationInfo.getDisplayName();
            mAssociationInfoCache = associationInfo;
        }

        @Override
        public int getAssociationId() {
            return mAssociationId;
        }

        @Override
        public AssociationInfo getAssociationInfoCache() {
            return mAssociationInfoCache;
        }

        /** Sets the association info cache for testing. */
        public FakeRemoteDevice setAssociationInfoCache(AssociationInfo associationInfo) {
            mAssociationInfoCache = associationInfo;
            return this;
        }

        @Override
        public int getUserId() {
            return mUserId;
        }

        @Override
        public CharSequence getDisplayName() {
            return mDisplayName;
        }

        /** Sets the display name for testing. */
        public FakeRemoteDevice setDisplayName(CharSequence displayName) {
            mDisplayName = displayName;
            return this;
        }

        @Override
        public String getDeviceProfile() {
            return mDeviceProfile;
        }

        /** Sets the device profile for testing. */
        public FakeRemoteDevice setDeviceProfile(String deviceProfile) {
            mDeviceProfile = deviceProfile;
            return this;
        }

        @Override
        public boolean hasTransport() {
            return mHasTransport;
        }

        /** Sets whether the device has transport for testing. */
        public FakeRemoteDevice setHasTransport(boolean hasTransport) {
            mHasTransport = hasTransport;
            return this;
        }

        @Override
        public boolean isBleAppeared() {
            return mIsBleAppeared;
        }

        /** Sets whether the device has BLE appeared for testing. */
        public FakeRemoteDevice setBleAppeared(boolean bleAppeared) {
            mIsBleAppeared = bleAppeared;
            return this;
        }

        @Override
        public boolean isBtConnected() {
            return mIsBtConnected;
        }

        /** Sets whether the device has BT connected for testing. */
        public FakeRemoteDevice setBtConnected(boolean btConnected) {
            mIsBtConnected = btConnected;
            return this;
        }

        @Override
        public boolean isSelfManagedAppeared() {
            return mIsSelfManagedAppeared;
        }

        /** Sets whether the device has self-managed appeared for testing. */
        public FakeRemoteDevice setSelfManagedAppeared(boolean selfManagedAppeared) {
            mIsSelfManagedAppeared = selfManagedAppeared;
            return this;
        }

        @Override
        public boolean isSelfManagedNearby() {
            return mIsSelfManagedNearby;
        }

        /** Sets whether the device is self-managed nearby for testing. */
        public FakeRemoteDevice setSelfManagedNearby(boolean selfManagedNearby) {
            mIsSelfManagedNearby = selfManagedNearby;
            return this;
        }

        @Override
        public boolean isAssociationRemoved() {
            return mIsAssociationRemoved;
        }

        /** Sets whether the association is removed for testing. */
        public FakeRemoteDevice setAssociationRemoved(boolean associationRemoved) {
            mIsAssociationRemoved = associationRemoved;
            return this;
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            // No-op
        }

        /** Sends a message to the remote network manager for testing. */
        public void sendMessage(String network, byte[] message) {
            mSentMessages.add(new SentMessage(network, message));
        }

        /** Sets the remote network manager for testing. */
        public FakeRemoteDevice setRemoteNetworkManager(
                @Nullable FakeNetworkManager remoteNetworkManager) {
            mRemoteNetworkManager = remoteNetworkManager;
            return this;
        }

        private boolean flushMessages(FakeNetworkManager localNetworkManager) {
            boolean hasMessage = false;
            while (!mSentMessages.isEmpty()) {
                SentMessage msg = mSentMessages.poll();
                if (mRemoteNetworkManager != null) {
                    mRemoteNetworkManager.onMessageReceived(
                            localNetworkManager, msg.network, msg.message);
                }
                hasMessage = true;
            }
            return hasMessage;
        }

        public List<SentMessage> getSentMessages() {
            return new ArrayList<>(mSentMessages);
        }
    }

    /** A fake implementation of {@link Network} for testing. */
    public class FakeNetwork implements Network {
        private final int mUserId;
        private final String mNetworkId;
        private final int mFeature;
        private final Predicate<RemoteDevice> mDevicePredicate;
        private final Set<Integer> mAssociationIds = new HashSet<>();
        private final List<Pair<Executor, NetworkListener>> mListeners = new ArrayList<>();
        private final Map<Long, byte[]> mMessageContents = new HashMap<>();
        private final Map<Long, Set<Integer>> mStickyBroadcastRecipients = new HashMap<>();
        private long mNextMessageId = 0;
        private boolean mIsDestroyed = false;

        /** Creates a new FakeNetwork. */
        FakeNetwork(
                int userId,
                String networkId,
                int feature,
                Predicate<RemoteDevice> devicePredicate) {
            mUserId = userId;
            mNetworkId = networkId;
            mFeature = feature;
            mDevicePredicate = devicePredicate;
        }

        @Override
        public String getNetworkId() {
            return mNetworkId;
        }

        @Override
        public int getUserId() {
            return mUserId;
        }

        @Override
        public int getFeature() {
            return mFeature;
        }

        @Override
        public Map<Integer, RemoteDevice> getRemoteDevices() {
            Map<Integer, RemoteDevice> devices = new HashMap<>();
            for (int id : mAssociationIds) {
                RemoteDevice device = FakeNetworkManager.this.mRemoteDevices.get(id);
                if (device != null) {
                    devices.put(id, device);
                }
            }
            return Collections.unmodifiableMap(devices);
        }

        @Override
        public long broadcastMessage(byte[] message, int flags) {
            if (mIsDestroyed) {
                return -1;
            }
            long id = mNextMessageId++;
            mMessageContents.put(id, message);
            if ((flags & MESSAGE_FLAG_STICKY) != 0) {
                mStickyBroadcastRecipients.put(id, new HashSet<>(mAssociationIds));
            }
            for (int associationId : mAssociationIds) {
                mRemoteDevices.get(associationId).sendMessage(mNetworkId, message);
            }
            return id;
        }

        @Override
        public long multicastMessage(List<Integer> associationIds, byte[] message, int flags) {
            if (mIsDestroyed) {
                return -1;
            }
            long id = mNextMessageId++;
            mMessageContents.put(id, message);
            for (int associationId : associationIds) {
                if (mAssociationIds.contains(associationId)) {
                    mRemoteDevices.get(associationId).sendMessage(mNetworkId, message);
                }
            }
            return id;
        }

        @Override
        public boolean isMessagePending(long id) {
            return mStickyBroadcastRecipients.containsKey(id);
        }

        @Override
        public void cancelMessage(long id) {
            if (mIsDestroyed) {
                return;
            }
            mMessageContents.remove(id);
            mStickyBroadcastRecipients.remove(id);
        }

        @Override
        public void registerListener(Executor executor, NetworkListener listener) {
            if (mIsDestroyed) {
                return;
            }
            mListeners.add(Pair.create(executor, listener));
            mAssociationIds.forEach(
                    associationId ->
                            executor.execute(
                                    () ->
                                            listener.onDeviceChanged(
                                                    mRemoteDevices.get(associationId))));
        }

        @Override
        public void unregisterListener(NetworkListener listener) {
            if (mIsDestroyed) {
                return;
            }
            mListeners.removeIf(pair -> pair.second == listener);
        }

        @Override
        public void destroy() {
            if (mIsDestroyed) {
                return;
            }
            mListeners.clear();
            mAssociationIds.clear();
            mMessageContents.clear();
            mStickyBroadcastRecipients.clear();
            mIsDestroyed = true;
            FakeNetworkManager.this.mNetworks.get(mNetworkId).values().remove(this);
            if (FakeNetworkManager.this.mNetworks.get(mNetworkId).isEmpty()) {
                FakeNetworkManager.this.mNetworks.remove(mNetworkId);
            }
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            // No-op
        }

        /** Simulates receiving a message from a remote device for testing. */
        public void receiveMessage(int fromAssociationId, byte[] message) {
            if (mIsDestroyed) {
                return;
            }
            for (Pair<Executor, NetworkListener> pair : mListeners) {
                pair.first.execute(() -> pair.second.onNetworkMessage(fromAssociationId, message));
            }
        }

        /** Returns whether the network is destroyed. */
        public boolean isDestroyed() {
            return mIsDestroyed;
        }

        private void maybeAddDevice(RemoteDevice device) {
            if (mIsDestroyed) {
                return;
            }
            if (mDevicePredicate.test(device)
                    && (device.getUserId() == mUserId || mUserId == UserHandle.USER_ALL)) {
                addDeviceInternal(device);
            }
        }

        private void addDeviceInternal(RemoteDevice device) {
            if (mIsDestroyed) {
                return;
            }
            int associationId = device.getAssociationId();
            if (mAssociationIds.add(associationId)) {
                for (Pair<Executor, NetworkListener> pair : mListeners) {
                    pair.first.execute(
                            () -> pair.second.onDeviceChanged(mRemoteDevices.get(associationId)));
                }
                for (Map.Entry<Long, Set<Integer>> entry : mStickyBroadcastRecipients.entrySet()) {
                    long stickyId = entry.getKey();
                    Set<Integer> recipients = entry.getValue();
                    if (recipients.add(associationId)) {
                        byte[] message = mMessageContents.get(stickyId);
                        if (message != null) {
                            mRemoteDevices.get(associationId).sendMessage(mNetworkId, message);
                        }
                    }
                }
            }
        }

        void removeDevice(int associationId) {
            if (mIsDestroyed) {
                return;
            }
            if (mAssociationIds.remove(associationId)) {
                for (Pair<Executor, NetworkListener> pair : mListeners) {
                    pair.first.execute(() -> pair.second.onDeviceRemoved(associationId));
                }
            }
        }

        /** Returns whether the given remote device is eligible for this network. */
        public boolean isDeviceEligibleForNetwork(RemoteDevice remoteDevice) {
            return !mIsDestroyed && mDevicePredicate.test(remoteDevice);
        }

        /** Re-evaluate device conditions. */
        public void invalidate() {
            for (RemoteDevice device : mRemoteDevices.values()) {
                maybeAddDevice(device);
            }
            for (int associationId : new ArrayList<>(mAssociationIds)) {
                if (!mRemoteDevices.containsKey(associationId)
                        || !mDevicePredicate.test(mRemoteDevices.get(associationId))) {
                    removeDevice(associationId);
                }
            }
        }
    }

    /** A record to hold a sent message for testing. */
    public record SentMessage(String network, byte[] message) {}
}
