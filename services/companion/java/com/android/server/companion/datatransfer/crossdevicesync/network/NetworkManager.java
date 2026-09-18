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

import android.annotation.Nullable;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.os.UserHandle;

import com.android.internal.util.FrameworkStatsLog;
import com.android.server.companion.datatransfer.crossdevicesync.common.Dumpable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/** Interface for managing cross-device connections in the sync network. */
public interface NetworkManager extends Dumpable {

    /** Initialize network manager. */
    void init();

    /** Destroy the network manager. */
    void destroy();

    /** Returns the mapping of association ID to the remote device. */
    Map<Integer, RemoteDevice> getRemoteDevices();

    /** Find a remote device from an association ID. */
    @Nullable
    RemoteDevice getRemoteDevice(int associationId);

    /** Returns a list of all networks. */
    List<Network> getNetworks();

    /**
     * Creates a new network.
     *
     * @param networkId a unique ID for the network. This can't match the networkId used for
     *     user-restricted networks.
     * @param remoteDeviceCondition a predicate for filtering remote devices. Devices will join or
     *     leave the network dynamically based on the evaluation result of predicate. Predicate will
     *     be evaluated dynamically by observing events from {@link CompanionDeviceManager}.
     */
    default Network createNetwork(String networkId, Predicate<RemoteDevice> remoteDeviceCondition) {
        return createNetworkForUser(
                UserHandle.USER_ALL,
                networkId,
                FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_UNSPECIFIED,
                remoteDeviceCondition);
    }

    /**
     * Creates a new network.
     *
     * @param networkId a unique ID for the network. This can't match the networkId used for
     *     user-restricted networks.
     * @param feature the {@code SyncFeature} logging enum to use for logging inbound and outbound
     *     messages in this network.
     * @param remoteDeviceCondition a predicate for filtering remote devices. Devices will join or
     *     leave the network dynamically based on the evaluation result of predicate. Predicate will
     *     be evaluated dynamically by observing events from {@link CompanionDeviceManager}.
     */
    default Network createNetwork(
            String networkId, int feature, Predicate<RemoteDevice> remoteDeviceCondition) {
        return createNetworkForUser(UserHandle.USER_ALL, networkId, feature, remoteDeviceCondition);
    }

    /**
     * Creates a new network for a user. This allows each user to have their own network instance
     * under the same network id. This is useful for multi-user features that want to share the same
     * {@code networkId} across users.
     *
     * @param userId the user that this network is restricted to. Only devices owned by the same
     *     user can join this network. If this is {@link UserHandle#USER_ALL}, all devices are
     *     allowed to join.
     * @param networkId a unique ID for the network. This can be reused across different users but
     *     must differ from the networkId used for non user-restricted networks that use {@link
     *     UserHandle#USER_ALL}.
     * @param remoteDeviceCondition a predicate for filtering remote devices. Devices will join or
     *     leave the network dynamically based on the evaluation result of predicate. Predicate will
     *     be evaluated dynamically by observing events from {@link CompanionDeviceManager}.
     */
    default Network createNetworkForUser(
            int userId, String networkId, Predicate<RemoteDevice> remoteDeviceCondition) {
        return createNetworkForUser(
                userId,
                networkId,
                FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_UNSPECIFIED,
                remoteDeviceCondition);
    }

    /**
     * Creates a new network for a user. This allows each user to have their own network instance
     * under the same network id. This is useful for multi-user features that want to share the same
     * {@code networkId} across users.
     *
     * @param userId the user that this network is restricted to. Only devices owned by the same
     *     user can join this network. If this is {@link UserHandle#USER_ALL}, all devices are
     *     allowed to join.
     * @param networkId a unique ID for the network. This can be reused across different users but
     *     must differ from the networkId used for non user-restricted networks that use {@link
     *     UserHandle#USER_ALL}.
     * @param feature the {@code SyncFeature} logging enum to use for logging inbound and outbound
     *     messages in this network.
     * @param remoteDeviceCondition a predicate for filtering remote devices. Devices will join or
     *     leave the network dynamically based on the evaluation result of predicate. Predicate will
     *     be evaluated dynamically by observing events from {@link CompanionDeviceManager}.
     */
    Network createNetworkForUser(
            int userId,
            String networkId,
            int feature,
            Predicate<RemoteDevice> remoteDeviceCondition);

    /**
     * Invalidates all networks and add or remove devices from networks based on the latest
     * information. This will force a re-evaluation of all network predicates and update network
     * members if needed.
     */
    void invalidateNetworks();

    /** An interface representing a remote device. */
    interface RemoteDevice extends Dumpable {
        /** Returns the association ID. */
        int getAssociationId();

        /** Returns the display name. */
        CharSequence getDisplayName();

        /** Returns the device profile. */
        String getDeviceProfile();

        /** Returns the id of user who owns this remote device. */
        int getUserId();

        /** Returns {@code true} if the device has transport. */
        boolean hasTransport();

        /** Returns {@code true} if the device has BLE appeared. */
        boolean isBleAppeared();

        /** Returns {@code true} if the device has BT connected. */
        boolean isBtConnected();

        /** Returns {@code true} if the device has self-managed appeared. */
        boolean isSelfManagedAppeared();

        /** Returns {@code true} if the device is self-managed nearby. */
        boolean isSelfManagedNearby();

        /** Returns {@code true} if the association is removed. */
        boolean isAssociationRemoved();

        /**
         * Returns the last received association info.
         *
         * <p>Note: this could be outdated, so only use this to access static information or
         * information whose staleness can be checked (e.g. metadata).
         */
        AssociationInfo getAssociationInfoCache();

        /** Returns {@code true} if the device is considered present. */
        default boolean isPresent() {
            if (isAssociationRemoved()) {
                return false;
            }
            return hasTransport() || isBleAppeared() || isBtConnected() || isSelfManagedAppeared();
        }

        /** Returns {@code true} if the device is considered to be nearby. */
        default boolean isNearby() {
            if (!isPresent()) {
                return false;
            }
            return isBleAppeared() || isBtConnected() || isSelfManagedNearby();
        }
    }

    /**
     * A network represents a group of {@link RemoteDevice} that can communicate with each other for
     * syncing data belonging to the group. A network is not a static concept, and devices can join
     * or leave the network after it's created.
     */
    interface Network extends Dumpable {
        /**
         * Flag indicating that a message should be retried if it failed to reach a remote device.
         * The number of retry is determined by implementation of this interface. Messages won't be
         * retried by default.
         */
        int MESSAGE_FLAG_RETRY_IF_FAILED = 1;

        /**
         * Flag indicating that a message should be sticky and be queued indefinitely until it gets
         * delivered to the target. Messages are non-sticky by default.
         *
         * <p>Note: adding this flag will implicitly add {@link #MESSAGE_FLAG_RETRY_IF_FAILED} since
         * a sticky message will always be retried.
         */
        int MESSAGE_FLAG_STICKY = 1 << 1;

        /**
         * Flag indicating that a message should trigger a scanning if any device in the network is
         * not available at this moment. The scanning strategy is determined by the implementation
         * of this interface. If a device is discovered afterwards, it will be delivered to the
         * target device. Messages won't trigger scanning by default.
         */
        int MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT = 1 << 2;

        /** Flag indicating that a message should only be delivered to devices that are nearby. */
        int MESSAGE_FLAG_NEARBY_ONLY = 1 << 3;

        /**
         * Get the unique id of this network. This value should be recognized by all devices in the
         * same network for multiplexing purposes.
         */
        String getNetworkId();

        /**
         * Get the id of user who owns this network. Return {@link UserHandle#USER_ALL} if this
         * network is shared across all users.
         */
        int getUserId();

        /** The feature ID used to log the inbound and outbound messages for this network. */
        int getFeature();

        /** Returns the mapping of association ID to the remote device. */
        Map<Integer, RemoteDevice> getRemoteDevices();

        /**
         * Broadcast the message to the network.
         *
         * @param message the message to broadcast
         * @param flags the flags that control the message behavior.
         * @return a message id that refers to this message.
         */
        long broadcastMessage(byte[] message, int flags);

        /**
         * Unicast the message to a specific remote device.
         *
         * @param associationId the association id of remote device.
         * @param message the message to send.
         * @param flags the flags that control the message behavior.
         * @return a message id that refers to this message.
         */
        default long unicastMessage(int associationId, byte[] message, int flags) {
            return multicastMessage(List.of(associationId), message, flags);
        }

        /**
         * Multicast the message to several remote devices.
         *
         * @param associationIds the association ids of remote devices.
         * @param message the message to send.
         * @param flags the flags that control the message behavior.
         * @return a message id that refers to this message.
         */
        long multicastMessage(List<Integer> associationIds, byte[] message, int flags);

        /** Check if a message is still pending. */
        boolean isMessagePending(long id);

        /**
         * Cancel a message if it's not delivered yet.
         *
         * @param id the message id.
         */
        void cancelMessage(long id);

        /** Register a {@link NetworkListener} for monitoring network change. */
        void registerListener(Executor executor, NetworkListener listener);

        /** Unregister a {@link NetworkListener}. */
        void unregisterListener(NetworkListener listener);

        /** Destroy this network and clean up all local states. */
        void destroy();
    }

    /** An interface for monitoring network change. */
    interface NetworkListener {
        /** Called when a network message is received. */
        void onNetworkMessage(int associationId, byte[] message);

        /** Called when a remote device is added to the network or changed. */
        default void onDeviceChanged(RemoteDevice device) {}

        /** Called when a remote device is removed from the network. */
        default void onDeviceRemoved(int associationId) {}
    }
}
