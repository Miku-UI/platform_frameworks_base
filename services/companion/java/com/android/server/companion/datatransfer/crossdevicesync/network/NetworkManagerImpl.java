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
package com.android.server.companion.datatransfer.crossdevicesync.network;

import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_MESSAGE_DROPPED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_MESSAGE_RECEIVED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_QUEUE_FAILED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_QUEUE_FINISHED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_QUEUE_STARTED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_SEND_FAILED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_SEND_FINISHED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_SEND_STARTED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_UNSPECIFIED;
import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.toIntArray;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager.OnAssociationsChangedListener;
import android.companion.DevicePresenceEvent;
import android.content.Context;
import android.os.Trace;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;
import com.android.server.companion.datatransfer.crossdevicesync.common.Clock;
import com.android.server.companion.datatransfer.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.server.companion.datatransfer.crossdevicesync.common.DebugConfig;
import com.android.server.companion.datatransfer.crossdevicesync.common.FrameworkStatsLogProxy;
import com.android.server.companion.datatransfer.crossdevicesync.network.advertiser.Advertiser;
import com.android.server.companion.datatransfer.crossdevicesync.network.advertiser.Advertiser.AdvertisingSession;
import com.android.server.companion.datatransfer.crossdevicesync.network.companion.CompanionActionController;
import com.android.server.companion.datatransfer.crossdevicesync.network.messenger.Messenger;
import com.android.server.companion.datatransfer.crossdevicesync.network.messenger.Messenger.MessageListener;
import com.android.server.companion.datatransfer.crossdevicesync.network.scanner.Scanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Default implementation of {@link NetworkManager}. */
public class NetworkManagerImpl implements NetworkManager {
    private static final String TAG = "NetworkManager";
    private static final boolean DEBUG = DebugConfig.DEBUG_NETWORK;

    private final Object mLock;
    private final Executor mMainExecutor;
    private final CompanionDeviceManagerProxy mCompanionDeviceManager;
    private final CompanionActionController mCompanionActionController;
    private final Messenger mMessenger;
    private final Advertiser mAdvertiser;
    private final Scanner mScanner;
    private final Clock mClock;
    private final Context mContext;
    private final FrameworkStatsLogProxy mFrameworkStatsLogProxy;
    private final Consumer<List<AssociationInfo>> mOnTransportChanged = this::onTransportChanged;
    private final OnAssociationsChangedListener mOnAssociationsChanged =
            this::onAssociationsChanged;
    private final Consumer<DevicePresenceEvent> mOnDevicePresenceEvent =
            this::onDevicePresenceEvent;
    private final MessageListener mOnMessage = this::onMessage;

    @GuardedBy("mLock")
    private boolean mInitialized;

    @GuardedBy("mLock")
    private final Map<Integer, RemoteDeviceImpl> mRemoteDevices = new HashMap<>();

    @GuardedBy("mLock")
    private final Map<String, Map<Integer, NetworkImpl>> mNetworks = new HashMap<>();

    @GuardedBy("mLock")
    private boolean mPendingInvalidation;

    // Use tree map so that messages are iterated in chronological order.
    @GuardedBy("mLock")
    private final Map<Long, MessageRecord> mMessageRecords = new TreeMap<>();

    @GuardedBy("mLock")
    private long mNextMessageId;

    public NetworkManagerImpl(
            Object networkLock,
            Executor mainExecutor,
            CompanionDeviceManagerProxy companionDeviceManager,
            CompanionActionController companionActionController,
            Messenger messenger,
            Advertiser advertiser,
            Scanner scanner,
            Clock clock,
            Context context,
            FrameworkStatsLogProxy frameworkStatsLogProxy) {
        mLock = networkLock;
        mMainExecutor = mainExecutor;
        mCompanionDeviceManager = companionDeviceManager;
        mCompanionActionController = companionActionController;
        mAdvertiser = advertiser;
        mScanner = scanner;
        mMessenger = messenger;
        mClock = clock;
        mContext = context;
        mFrameworkStatsLogProxy = frameworkStatsLogProxy;
    }

    @Override
    public void init() {
        synchronized (mLock) {
            if (mInitialized) {
                throw new IllegalStateException("Already initialized!");
            }
            mInitialized = true;
            processAssociationsAndMessagesLocked(
                    mCompanionDeviceManager.getAllAssociations(UserHandle.USER_ALL));
            mCompanionDeviceManager.addOnAssociationsChangedListener(
                    mMainExecutor, mOnAssociationsChanged, UserHandle.USER_ALL);
            mCompanionDeviceManager.addOnTransportsChangedListener(
                    mMainExecutor, mOnTransportChanged);
            mMessenger.init();
            mMessenger.registerMessageListener(mMainExecutor, mOnMessage);
            mCompanionActionController.init();
        }
    }

    @Override
    public void destroy() {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            mInitialized = false;
            mCompanionDeviceManager.removeOnAssociationsChangedListener(mOnAssociationsChanged);
            mCompanionDeviceManager.removeOnTransportsChangedListener(mOnTransportChanged);
            mCompanionDeviceManager.removeOnDevicePresenceEventListener(mContext.getPackageName());
            mRemoteDevices.clear();
            for (Map<Integer, NetworkImpl> userNetworks : new ArrayList<>(mNetworks.values())) {
                for (NetworkImpl network : new ArrayList<>(userNetworks.values())) {
                    network.destroy();
                }
            }
            mPendingInvalidation = false;
            // Clear all messages and stop any message processing.
            mMessageRecords.clear();
            mMessenger.unregisterMessageListener(mOnMessage);
            mMessenger.destroy();
            mScanner.closeAllScanningSessions();
            mAdvertiser.closeAllAdvertisingSessions();
            mCompanionActionController.destroy();
        }
    }

    @Override
    public Network createNetworkForUser(
            int userId,
            String networkId,
            int feature,
            Predicate<RemoteDevice> remoteDeviceCondition) {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            NetworkImpl network = getNetworkLocked(networkId, userId);
            if (network != null) {
                if (network.getUserId() != userId) {
                    throw new IllegalArgumentException(
                            "Unable to create network '"
                                    + networkId
                                    + "' for user "
                                    + userId
                                    + ". A conflicting network already exists for user "
                                    + network.getUserId()
                                    + ".");
                }
                return network;
            }
            network = new NetworkImpl(userId, networkId, feature, remoteDeviceCondition);
            mNetworks.computeIfAbsent(networkId, k -> new HashMap<>()).put(userId, network);
            invalidateNetworks();
            return network;
        }
    }

    @GuardedBy("mLock")
    @Nullable
    private NetworkImpl getNetworkLocked(String networkId, int userId) {
        Map<Integer, NetworkImpl> userNetworkMap = mNetworks.get(networkId);
        if (userNetworkMap == null || userNetworkMap.isEmpty()) {
            return null;
        }
        NetworkImpl network = userNetworkMap.get(userId);
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

    @GuardedBy("mLock")
    private void throwIfUninitializedLocked() {
        if (!mInitialized) {
            throw new IllegalStateException("Network manager not initialized!");
        }
    }

    @Override
    public void invalidateNetworks() {
        invalidate(/* async= */ true);
    }

    private void invalidate(boolean async) {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "invalidateNetworksAndMessages: ignore since not initialized");
                return;
            }
            if (mPendingInvalidation) {
                return;
            }
            mPendingInvalidation = true;
            if (async) {
                mMainExecutor.execute(this::doInvalidate);
            } else {
                doInvalidate();
            }
        }
    }

    private void doInvalidate() {
        synchronized (mLock) {
            if (!mPendingInvalidation) {
                return;
            }
            processAssociationsAndMessagesLocked(
                    mCompanionDeviceManager.getAllAssociations(UserHandle.USER_ALL));
        }
    }

    private void onAssociationsChanged(List<AssociationInfo> associations) {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "onAssociationsChanged: ignore since not initialized");
                return;
            }
            processAssociationsAndMessagesLocked(associations);
        }
    }

    @SuppressWarnings("GuardedBy")
    @GuardedBy("mLock")
    private void processAssociationsAndMessagesLocked(List<AssociationInfo> allInfo) {
        Trace.beginSection("NetworkManagerImpl.processAssociationsAndMessages");
        try {
            mPendingInvalidation = false;
            // Add new info if not existing.
            Set<Integer> allInfoIds = new HashSet<>(allInfo.size());
            boolean deviceChanged = false;
            for (AssociationInfo info : allInfo) {
                int id = info.getId();
                allInfoIds.add(id);
                RemoteDeviceImpl device = mRemoteDevices.get(id);
                if (device != null) {
                    device.setAssociationInfoCache(info);
                } else {
                    device = new RemoteDeviceImpl(info);
                    mRemoteDevices.put(id, device);
                    deviceChanged = true;
                    if (DEBUG) {
                        Log.d(TAG, "Added remote device " + device);
                    }
                }
            }
            // Remove old info if not existing.
            deviceChanged |= mRemoteDevices.keySet().retainAll(allInfoIds);
            if (deviceChanged) {
                updateDevicePresenceListenerLocked();
            }
            forEachNetworkLocked(NetworkImpl::invalidateLocked);
            // Process messages.
            processMessagesLocked();
        } finally {
            Trace.endSection();
        }
    }

    @GuardedBy("mLock")
    private void forEachNetworkLocked(Consumer<NetworkImpl> consumer) {
        for (Map<Integer, NetworkImpl> userNetworks : mNetworks.values()) {
            for (NetworkImpl network : userNetworks.values()) {
                consumer.accept(network);
            }
        }
    }

    @GuardedBy("mLock")
    private void updateDevicePresenceListenerLocked() {
        int[] associationIds = toIntArray(mRemoteDevices.keySet());
        if (associationIds.length == 0) {
            mCompanionDeviceManager.removeOnDevicePresenceEventListener(mContext.getPackageName());
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Update device presence listener for " + Arrays.toString(associationIds));
        }
        mCompanionDeviceManager.setOnDevicePresenceEventListener(
                associationIds, mContext.getPackageName(), mMainExecutor, mOnDevicePresenceEvent);
    }

    @SuppressWarnings("GuardedBy")
    private void onTransportChanged(List<AssociationInfo> associationsWithTransport) {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "onTransportChanged: ignore since not initialized");
                return;
            }
            Set<Integer> idsWithTransport = new HashSet<>();
            for (AssociationInfo associationInfo : associationsWithTransport) {
                int id = associationInfo.getId();
                if (!mRemoteDevices.containsKey(id)) {
                    Log.w(TAG, "updateTransportFlag: unknown device " + id);
                    continue;
                }
                idsWithTransport.add(id);
            }
            boolean changed = false;
            for (RemoteDeviceImpl device : mRemoteDevices.values()) {
                boolean hasTransport = idsWithTransport.contains(device.getAssociationId());
                if (device.setHasTransport(hasTransport)) {
                    if (DEBUG) {
                        Log.d(
                                TAG,
                                "updateTransportFlag: "
                                        + device
                                        + (device.hasTransport() ? " established" : " lost")
                                        + " transport");
                    }
                    changed = true;
                    forEachNetworkLocked(
                            n -> n.notifyDeviceChangedLocked(device.getAssociationId()));
                }
            }
            if (changed) {
                // Transport has changed. Need to re-evaluate network members.
                invalidateNetworks();
                mMessenger.onTransportsChanged(idsWithTransport);
            }
        }
    }

    @SuppressWarnings("GuardedBy")
    private void onDevicePresenceEvent(DevicePresenceEvent event) {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "onDevicePresenceEvent: ignore since not initialized");
                return;
            }

            RemoteDeviceImpl device = mRemoteDevices.get(event.getAssociationId());
            if (device == null) {
                Log.w(
                        TAG,
                        "updateDevicePresenceLocked: unknown device " + event.getAssociationId());
                return;
            }
            boolean changed =
                    switch (event.getEvent()) {
                        case DevicePresenceEvent.EVENT_BLE_APPEARED -> device.setBleAppeared(true);
                        case DevicePresenceEvent.EVENT_BLE_DISAPPEARED ->
                                device.setBleAppeared(false);
                        case DevicePresenceEvent.EVENT_BT_CONNECTED -> device.setBtConnected(true);
                        case DevicePresenceEvent.EVENT_BT_DISCONNECTED ->
                                device.setBtConnected(false);
                        case DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED ->
                                device.setSelfManagedAppeared(true);
                        case DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED ->
                                device.setSelfManagedAppeared(false);
                        case DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY ->
                                device.setSelfManagedNearby(true);
                        case DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY ->
                                device.setSelfManagedNearby(false);
                        case DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED ->
                                device.setAssociationRemoved(true);
                        default -> {
                            Log.w(TAG, "Unhandled device presence event: " + event);
                            yield false;
                        }
                    };
            if (changed) {
                forEachNetworkLocked(n -> n.notifyDeviceChangedLocked(device.getAssociationId()));
                if (DEBUG) {
                    Log.d(TAG, "Device presence flag changed for " + device + ", event: " + event);
                }
                // Device presence state changed. Need to re-evaluate network members.
                invalidateNetworks();
            }
        }
    }

    private void onMessage(String networkId, int associationId, byte[] message) {
        boolean messageReceived = false;
        int feature = CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_UNSPECIFIED;
        try {
            synchronized (mLock) {
                if (!mInitialized) {
                    Log.w(
                            TAG,
                            "onMessage: ignore incoming message from "
                                    + getAddressString(networkId, associationId)
                                    + " since network manager is not initialized!");
                    return;
                }
                RemoteDeviceImpl device = mRemoteDevices.get(associationId);
                if (device == null) {
                    Log.w(
                            TAG,
                            "onMessage: ignore incoming message from "
                                    + getAddressString(networkId, associationId)
                                    + " since device is not found!");
                    return;
                }
                NetworkImpl network = getNetworkLocked(networkId, device.getUserId());
                if (network == null) {
                    Log.w(
                            TAG,
                            "onMessage: ignore incoming message from "
                                    + getAddressString(networkId, associationId)
                                    + " since network is not found for user "
                                    + device.getUserId()
                                    + "!");
                    return;
                }
                feature = network.getFeature();
                // Always invalidate networks to ensure the network members are up-to-date.
                invalidate(/* async= */ false);
                if (network.notifyInboundMessageLocked(associationId, message)) {
                    messageReceived = true;
                    Log.i(
                            TAG,
                            "onMessage: delivered message from "
                                    + getAddressString(networkId, associationId)
                                    + " to network "
                                    + networkId
                                    + " for user "
                                    + network.getUserId()
                                    + ".");
                } else {
                    Log.i(
                            TAG,
                            "onMessage: dropped incoming message from "
                                    + getAddressString(networkId, associationId)
                                    + " since the source address is not found for user "
                                    + network.getUserId());
                }
            }
        } finally {
            if (messageReceived) {
                mFrameworkStatsLogProxy.logSyncEvent(
                        CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_MESSAGE_RECEIVED, feature);
            } else {
                mFrameworkStatsLogProxy.logSyncEvent(
                        CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_MESSAGE_DROPPED, feature);
            }
        }
    }

    @Override
    public Map<Integer, RemoteDevice> getRemoteDevices() {
        synchronized (mLock) {
            return Collections.unmodifiableMap(mRemoteDevices);
        }
    }

    @Nullable
    @Override
    public RemoteDevice getRemoteDevice(int associationId) {
        synchronized (mLock) {
            return mRemoteDevices.get(associationId);
        }
    }

    @Override
    public List<Network> getNetworks() {
        synchronized (mLock) {
            List<Network> networks = new ArrayList<>();
            forEachNetworkLocked(networks::add);
            return networks;
        }
    }

    @GuardedBy("mLock")
    private long enqueueMessageLocked(
            Network network,
            byte[] message,
            Collection<Integer> targetAssociationIds,
            int flags,
            boolean isBroadcast) {
        if (!mInitialized) {
            Log.w(TAG, "enqueueMessage: ignore since not initialized");
            return -1;
        }
        long id = mNextMessageId++;
        MessageRecord record =
                new MessageRecord(
                        id,
                        message,
                        targetAssociationIds,
                        flags,
                        network.getUserId(),
                        network.getNetworkId(),
                        network.getFeature(),
                        isBroadcast,
                        mClock.elapsedRealtime());
        mMessageRecords.put(id, record);
        mFrameworkStatsLogProxy.logSyncEvent(
                CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_QUEUE_STARTED, network.getFeature());
        invalidate(/* async= */ true);
        if (DEBUG) {
            Log.d(
                    TAG,
                    "enqueueMessage: queued message " + id + " with " + message.length + " bytes.");
        }
        return id;
    }

    @GuardedBy("mLock")
    private void processMessagesLocked() {
        if (DEBUG) {
            Log.d(TAG, "Processing messages ...");
        }
        Iterator<MessageRecord> iterator = mMessageRecords.values().iterator();
        final long now = mClock.elapsedRealtime();
        while (iterator.hasNext()) {
            MessageRecord r = iterator.next();
            processMessageLocked(r, now);
            if (r.isDone()) {
                iterator.remove();
                if (DEBUG) {
                    Log.d(TAG, "processMessage: message " + r.id + " is done.");
                }
            }
        }
    }

    @SuppressWarnings("GuardedBy")
    @GuardedBy("mLock")
    private void processMessageLocked(MessageRecord record, final long now) {
        NetworkImpl network = getNetworkLocked(record.networkId, record.userId);
        Set<Integer> associationIds =
                network == null ? Collections.emptySet() : network.getAssociationIdsLocked();
        if (network == null) {
            // The network is gone. Cancel message.
            if (DEBUG) {
                Log.d(
                        TAG,
                        "processMessage: cancelling message "
                                + record.id
                                + " because network "
                                + record.networkId
                                + " is gone.");
            }
            record.cancelAll();
        } else if (record.isBroadcast) {
            // Add missing delivery targets.
            record.addDeliveryState(associationIds, now);
        }
        record.forEachUnfinishedDelivery(
                (associationId, deliveryState) -> {
                    Pair<Integer, String> nextStateAndReason =
                            getNextDeliveryStateAndReasonLocked(
                                    record, associationId, deliveryState, associationIds, network);
                    int nextDeliveryState = nextStateAndReason.first;
                    String changeReason = nextStateAndReason.second;
                    if (!deliveryState.setState(nextDeliveryState, now, changeReason)) {
                        // Delivery state unchanged
                        return;
                    }
                    if (DEBUG) {
                        Log.d(
                                TAG,
                                "processMessage: delivery state of message "
                                        + record.id
                                        + " to "
                                        + getAddressString(record.networkId, associationId)
                                        + " changed to "
                                        + DeliveryState.stateToString(nextDeliveryState)
                                        + ". reason=\""
                                        + changeReason
                                        + "\"");
                    }

                    if (nextDeliveryState == DeliveryState.SENDING) {
                        Log.i(
                                TAG,
                                "processMessage: sending message "
                                        + record.id
                                        + " to "
                                        + getAddressString(record.networkId, associationId)
                                        + ".");
                        mFrameworkStatsLogProxy.logSyncEvent(
                                CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_SEND_STARTED,
                                record.feature);
                        final long sendingStartTimestamp = mClock.elapsedRealtime();
                        deliveryState.messageSendingFuture =
                                mMessenger.sendMessage(
                                        record.networkId,
                                        associationId,
                                        record.message,
                                        record.maxAttempts);
                        deliveryState.messageSendingFuture.whenComplete(
                                getMessageSendingCallback(record, sendingStartTimestamp));
                    } else {
                        // State is not SENDING. Cancel the message request.
                        if (deliveryState.messageSendingFuture != null
                                && !deliveryState.messageSendingFuture.isDone()) {
                            if (DEBUG) {
                                Log.d(
                                        TAG,
                                        "processMessage: cancelling message request for "
                                                + record.id
                                                + " to "
                                                + getAddressString(record.networkId, associationId)
                                                + ".");
                            }
                            deliveryState.messageSendingFuture.cancel(true);
                        }
                        deliveryState.messageSendingFuture = null;
                    }

                    if (nextDeliveryState == DeliveryState.WAITING_FOR_PRESENCE) {
                        if (record.shouldScanIfNotPresent()) {
                            if (DEBUG) {
                                Log.d(
                                        TAG,
                                        "processMessage: scanning for "
                                                + getAddressString(record.networkId, associationId)
                                                + ".");
                            }
                            deliveryState.scanningSession =
                                    mScanner.startScanning(
                                            associationId,
                                            "Scan for deliver message "
                                                    + record.id
                                                    + " to "
                                                    + getAddressString(
                                                            record.networkId, associationId)
                                                    + ".");
                        }
                    } else {
                        // No need to scanning. Close scanning session.
                        if (deliveryState.scanningSession != null
                                && deliveryState.scanningSession.isActive()) {
                            if (DEBUG) {
                                Log.d(
                                        TAG,
                                        "processMessage: closing scanning session "
                                                + deliveryState.scanningSession);
                            }
                            deliveryState.scanningSession.close();
                        }
                        deliveryState.scanningSession = null;
                    }
                });
        if (!record.isLogged()) {
            if (record.isAllDelivered()) {
                mFrameworkStatsLogProxy.logSyncEvent(
                        CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_QUEUE_FINISHED,
                        record.feature,
                        /* duration= */ now - record.creationTimestamp);
                record.noteLogged();
            } else if (record.isCancelled() || record.isAnyCancelled()) {
                mFrameworkStatsLogProxy.logSyncEvent(
                        CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_QUEUE_FAILED,
                        record.feature,
                        /* duration= */ now - record.creationTimestamp);
                record.noteLogged();
            }
        }
    }

    private BiConsumer<Object, Throwable> getMessageSendingCallback(
            MessageRecord record, final long sendingStartTimestamp) {
        return (result, t) -> {
            invalidate(/* async= */ true);
            long now = mClock.elapsedRealtime();
            if (t == null) {
                mFrameworkStatsLogProxy.logSyncEvent(
                        CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_SEND_FINISHED,
                        record.feature,
                        /* duration= */ now - sendingStartTimestamp);
            } else {
                mFrameworkStatsLogProxy.logSyncEvent(
                        CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_SEND_FAILED,
                        record.feature,
                        /* duration= */ now - sendingStartTimestamp);
            }
        };
    }

    /** Advance the delivery state machine to next state based on latest information. */
    @GuardedBy("mLock")
    private Pair<Integer, String> getNextDeliveryStateAndReasonLocked(
            MessageRecord record,
            int associationId,
            DeliveryState deliveryState,
            Set<Integer> associationsInNetwork,
            @Nullable NetworkImpl network) {
        final RemoteDeviceImpl device = mRemoteDevices.get(associationId);
        if (deliveryState.isDone()) {
            // End state. No change.
            return Pair.create(deliveryState.state, deliveryState.reason);
        }
        if (device == null) {
            // Device is gone.
            return Pair.create(DeliveryState.CANCELLED, "device is not found");
        }
        if (record.isCancelled()) {
            // The message has been cancelled. Also cancel any pending delivery.
            return Pair.create(DeliveryState.CANCELLED, "message is cancelled");
        }
        if (!associationsInNetwork.contains(associationId)) {
            if (record.isSticky()
                    && network != null
                    && network.isAccessibleByUser(device.getUserId())) {
                // Sticky message should stay queued until the device joins the network.
                return Pair.create(
                        DeliveryState.QUEUED,
                        getAddressString(record.networkId, associationId) + " not found");
            } else {
                // The association has been removed from network. Delivery should be
                // cancelled.
                return Pair.create(
                        DeliveryState.CANCELLED,
                        getAddressString(record.networkId, associationId) + " not found");
            }
        }

        final boolean devicePresent = isDevicePresentForMessage(device, record);
        if (deliveryState.state == DeliveryState.SENDING) {
            if (!requireNonNull(deliveryState.messageSendingFuture).isDone()) {
                // Continue waiting for sending result.
                return Pair.create(deliveryState.state, deliveryState.reason);
            }
            try {
                deliveryState.messageSendingFuture.get();
                // Success!
                return Pair.create(DeliveryState.DELIVERED, "message delivered");
            } catch (Exception e) {
                if (!record.isSticky()) {
                    // Give up since we've failed and message is non-sticky.
                    return Pair.create(DeliveryState.CANCELLED, e.getMessage());
                }
                if (devicePresent) {
                    // Delivery failed but don't cancel the sticky message yet. It will be retried
                    // when the same device re-appear in the future.
                    return Pair.create(DeliveryState.DELIVERY_FAILED, e.getMessage());
                }
                // Device is no longer present. Wait for it to re-appear due to message being
                // sticky.
                return Pair.create(
                        DeliveryState.WAITING_FOR_PRESENCE, "waiting for presence after failure");
            }
        }

        if (!devicePresent) {
            if (record.isSticky() || record.shouldScanIfNotPresent()) {
                // For sticky or scanning messages, wait for presence.
                return Pair.create(DeliveryState.WAITING_FOR_PRESENCE, "waiting for presence");
            } else {
                // Give up since device is not present.
                return Pair.create(DeliveryState.CANCELLED, "device is not present or nearby");
            }
        }

        if (deliveryState.state == DeliveryState.DELIVERY_FAILED) {
            // Already failed while device is present. Stay in the failed state and retry when the
            // same device re-appear in the future.
            return Pair.create(deliveryState.state, deliveryState.reason);
        }

        return Pair.create(DeliveryState.SENDING, "sending message");
    }

    private static boolean isDevicePresentForMessage(
            RemoteDeviceImpl device, MessageRecord message) {
        return device.isPresent() && (device.isNearby() || !message.isNearbyOnly());
    }

    private static String getAddressString(String networkId, int associationId) {
        return networkId + "/" + associationId;
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        synchronized (mLock) {
            pw.println("NetworkManager:");
            pw.increaseIndent();
            pw.println("mInitialized=" + mInitialized);
            pw.println("mPendingInvalidation=" + mPendingInvalidation);
            pw.println("mNextMessageId=" + mNextMessageId);
            mScanner.dump(pw);
            mAdvertiser.dump(pw);
            mCompanionActionController.dump(pw);
            if (mRemoteDevices.isEmpty()) {
                pw.println("No remote devices.");
            } else {
                pw.println("Remote devices:");
                pw.increaseIndent();
                mRemoteDevices.values().forEach(device -> device.dump(pw));
                pw.decreaseIndent();
            }
            if (mNetworks.isEmpty()) {
                pw.println("No networks.");
            } else {
                pw.println("Networks:");
                pw.increaseIndent();
                forEachNetworkLocked(network -> network.dump(pw));
                pw.decreaseIndent();
            }
            if (mMessageRecords.isEmpty()) {
                pw.println("No message records.");
            } else {
                pw.println("Message records:");
                pw.increaseIndent();
                mMessageRecords.values().forEach(record -> record.dump(pw));
                pw.decreaseIndent();
            }
            mMessenger.dump(pw);
            pw.decreaseIndent();
        }
    }

    /** A class representing a group of devices. */
    @SuppressWarnings("EffectivelyPrivate")
    private class NetworkImpl implements Network {
        private final int mUserId;
        private final String mId;
        private final int mFeature;
        private final Predicate<RemoteDevice> mDeviceCondition;

        @GuardedBy("mLock")
        private final Map<Integer, AdvertisingSession> mAdvertisingSessions = new HashMap<>();

        @GuardedBy("mLock")
        private final Set<Integer> mAssociationIds = new HashSet<>();

        @GuardedBy("mLock")
        private final List<Pair<Executor, NetworkListener>> mListeners = new ArrayList<>();

        @GuardedBy("mLock")
        private boolean mDestroyed;

        NetworkImpl(int userId, String id, int feature, Predicate<RemoteDevice> deviceCondition) {
            mUserId = userId;
            mId = id;
            mFeature = feature;
            mDeviceCondition = deviceCondition;
        }

        @Override
        public String getNetworkId() {
            return mId;
        }

        @Override
        public int getUserId() {
            return mUserId;
        }

        @Override
        public int getFeature() {
            return mFeature;
        }

        @SuppressWarnings("GuardedBy")
        @Override
        public Map<Integer, RemoteDevice> getRemoteDevices() {
            synchronized (mLock) {
                return NetworkManagerImpl.this.getRemoteDevices().entrySet().stream()
                        .filter(e -> mAssociationIds.contains(e.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }
        }

        @GuardedBy("mLock")
        public Set<Integer> getAssociationIdsLocked() {
            return Collections.unmodifiableSet(mAssociationIds);
        }

        @Override
        public long broadcastMessage(byte[] message, int flags) {
            synchronized (mLock) {
                if (mDestroyed) {
                    Log.w(TAG, "broadcastMessage: network " + this + " is already destroyed");
                    return -1;
                }
                return enqueueMessageLocked(
                        this, message, mAssociationIds, flags, /* isBroadcast= */ true);
            }
        }

        @Override
        public long multicastMessage(List<Integer> associationIds, byte[] message, int flags) {
            synchronized (mLock) {
                if (mDestroyed) {
                    Log.w(TAG, "multicastMessage: network " + this + " is already destroyed");
                    return -1;
                }
                return enqueueMessageLocked(
                        this, message, associationIds, flags, /* isBroadcast= */ false);
            }
        }

        @Override
        public boolean isMessagePending(long id) {
            synchronized (mLock) {
                if (mDestroyed) {
                    return false;
                }
                MessageRecord r = mMessageRecords.get(id);
                return r != null && r.networkId.equals(mId) && r.userId == mUserId;
            }
        }

        @Override
        public void cancelMessage(long id) {
            synchronized (mLock) {
                if (mDestroyed) {
                    Log.w(TAG, "cancelMessage: network " + this + " is already destroyed");
                    return;
                }
                MessageRecord r = mMessageRecords.get(id);
                if (r == null || !r.networkId.equals(mId) || r.userId != mUserId) {
                    return;
                }
                if (r.cancelAll()) {
                    invalidate(/* async= */ true);
                }
            }
        }

        @SuppressWarnings("GuardedBy")
        @Override
        public void registerListener(Executor executor, NetworkListener listener) {
            synchronized (mLock) {
                if (mDestroyed) {
                    Log.w(TAG, "registerListener: network " + this + " is already destroyed");
                    return;
                }
                mListeners.add(Pair.create(executor, listener));
                mAssociationIds.forEach(this::notifyDeviceChangedLocked);
            }
        }

        @Override
        public void unregisterListener(NetworkListener listener) {
            synchronized (mLock) {
                if (mDestroyed) {
                    Log.w(TAG, "unregisterListener: network " + this + " is already destroyed");
                    return;
                }
                mListeners.removeIf(pair -> pair.second == listener);
            }
        }

        /** Re-evaluate devices in this network. */
        @GuardedBy("mLock")
        private void invalidateLocked() {
            for (RemoteDeviceImpl device : mRemoteDevices.values()) {
                int associationId = device.getAssociationId();
                if (isAccessibleByUser(device.getUserId())
                        && mDeviceCondition.test(device)
                        && !device.isAssociationRemoved()) {
                    if (mAssociationIds.add(associationId)) {
                        Log.i(TAG, "Device " + device + " joined network " + this);
                        notifyListenersLocked(l -> l.onDeviceChanged(device));
                        mAdvertisingSessions.put(
                                associationId,
                                mAdvertiser.startAdvertising(associationId, "Network " + mId));
                    }
                } else if (mAssociationIds.remove(associationId)) {
                    notifyListenersLocked(l -> l.onDeviceRemoved(associationId));
                    stopAdvertisingLocked(associationId);
                    Log.i(TAG, "Device " + device + " left network " + this);
                }
            }
            Set<Integer> set = new ArraySet<>(mAssociationIds);
            mAssociationIds.retainAll(mRemoteDevices.keySet());
            set.removeAll(mAssociationIds);
            for (Integer removedId : set) {
                Log.i(TAG, "Device with id " + removedId + " left network " + this);
                notifyListenersLocked(l -> l.onDeviceRemoved(removedId));
                stopAdvertisingLocked(removedId);
            }
        }

        @GuardedBy("mLock")
        private void stopAdvertisingLocked(int associationId) {
            AdvertisingSession advertisingSession = mAdvertisingSessions.remove(associationId);
            if (advertisingSession != null) {
                advertisingSession.close();
            }
        }

        @GuardedBy("mLock")
        private void notifyListenersLocked(Consumer<NetworkListener> listenerConsumer) {
            mListeners.forEach(
                    pair -> pair.first.execute(() -> listenerConsumer.accept(pair.second)));
        }

        @GuardedBy("mLock")
        private void notifyDeviceChangedLocked(int associationId) {
            if (mAssociationIds.contains(associationId)) {
                RemoteDeviceImpl device = mRemoteDevices.get(associationId);
                if (device == null) {
                    Log.w(
                            TAG,
                            "notifyDeviceChangedLocked: device not found for id " + associationId);
                    return;
                }
                notifyListenersLocked(l -> l.onDeviceChanged(device));
            }
        }

        @GuardedBy("mLock")
        private boolean notifyInboundMessageLocked(int sourceAssociationId, byte[] message) {
            if (mAssociationIds.contains(sourceAssociationId)) {
                notifyListenersLocked(l -> l.onNetworkMessage(sourceAssociationId, message));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void destroy() {
            synchronized (mLock) {
                if (mDestroyed) {
                    return;
                }
                mDestroyed = true;
                mAssociationIds.clear();
                mListeners.clear();
                Map<Integer, NetworkImpl> userNetworkMap = mNetworks.get(mId);
                userNetworkMap.remove(mUserId);
                if (userNetworkMap.isEmpty()) {
                    mNetworks.remove(mId);
                }
                mAdvertisingSessions.values().forEach(AdvertisingSession::close);
                mAdvertisingSessions.clear();
                maybeInvalidateMessagesLocked();
                Log.i(TAG, "Destroyed network " + this);
            }
        }

        @GuardedBy("mLock")
        private void maybeInvalidateMessagesLocked() {
            for (MessageRecord r : mMessageRecords.values()) {
                if (r.networkId.equals(mId) && r.userId == mUserId) {
                    invalidate(/* async= */ true);
                    return;
                }
            }
        }

        public boolean isAccessibleByUser(int userId) {
            return mUserId == userId || mUserId == UserHandle.USER_ALL;
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            pw.println("NetworkImpl:");
            pw.increaseIndent();
            synchronized (mLock) {
                pw.println("userId=" + (mUserId == UserHandle.USER_ALL ? "USER_ALL" : mUserId));
                pw.println("id=" + mId);
                pw.println("feature=" + mFeature);
                pw.println("destroyed=" + mDestroyed);
                pw.println("deviceCondition=" + mDeviceCondition);
                pw.println("advertisingSessions=" + mAdvertisingSessions.keySet());
                pw.println("listeners=" + mListeners.size());
                pw.println("devices=" + mAssociationIds);
            }
            pw.decreaseIndent();
        }

        @Override
        public String toString() {
            return "Network{id="
                    + mId
                    + ", userId="
                    + (mUserId == UserHandle.USER_ALL ? "USER_ALL" : mUserId)
                    + "}";
        }
    }
}
