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
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.companion.datatransfer.crossdevicesync.common.Dumpable;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.Network;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** A class representing a message to be sent. */
class MessageRecord implements Dumpable {
    @VisibleForTesting static final int MAX_DELIVERY_ATTEMPT = 3;

    public final long id;
    public final byte[] message;
    public final int maxAttempts;
    public final int userId;
    public final String networkId;
    public final int feature;
    public final boolean isBroadcast;
    public final long creationTimestamp;

    private final int mFlags;
    private final Map<Integer, DeliveryState> mDeliveryStates = new HashMap<>();

    private boolean mCancelled;
    private boolean mLogged;

    MessageRecord(
            long id,
            byte[] message,
            Collection<Integer> targetAssociationIds,
            int flags,
            int userId,
            String networkId,
            int feature,
            boolean isBroadcast,
            long now) {
        this.id = id;
        this.message = message;
        this.mFlags = flags;
        this.maxAttempts = shouldRetryAfterFail() ? MAX_DELIVERY_ATTEMPT : 1;
        this.userId = userId;
        this.networkId = networkId;
        this.feature = feature;
        this.isBroadcast = isBroadcast;
        this.creationTimestamp = now;
        for (Integer associationId : targetAssociationIds) {
            mDeliveryStates.put(associationId, new DeliveryState(now));
        }
    }

    /** Returns {@code true} if the message should be retried during delivery. */
    public boolean shouldRetryAfterFail() {
        return isSticky() || (mFlags & Network.MESSAGE_FLAG_RETRY_IF_FAILED) != 0;
    }

    /** Returns {@code true} if the message is sticky. */
    public boolean isSticky() {
        return (mFlags & Network.MESSAGE_FLAG_STICKY) != 0;
    }

    /** Returns {@code true} if scanning should be triggered if the target device is not found. */
    public boolean shouldScanIfNotPresent() {
        return (mFlags & Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT) != 0;
    }

    /** Returns {@code true} if the message should only be delivered to nearby devices. */
    public boolean isNearbyOnly() {
        return (mFlags & Network.MESSAGE_FLAG_NEARBY_ONLY) != 0;
    }

    /** Returns {@code true} if the message is completed. */
    public boolean isDone() {
        if (isCancelled()) {
            return true;
        }
        if (isSticky() && isBroadcast) {
            // Sticky broadcasts should be queued indefinitely until cancelled.
            return false;
        }
        for (DeliveryState state : mDeliveryStates.values()) {
            if (!state.isDone()) {
                return false;
            }
        }
        return true;
    }

    /** Returns {@code true} if any message was cancelled. */
    public boolean isAnyCancelled() {
        for (DeliveryState state : mDeliveryStates.values()) {
            if (state.state == DeliveryState.CANCELLED) {
                return true;
            }
        }
        return false;
    }

    /** Returns {@code true} if all messages are delivered. */
    public boolean isAllDelivered() {
        for (DeliveryState state : mDeliveryStates.values()) {
            if (state.state != DeliveryState.DELIVERED) {
                return false;
            }
        }
        return true;
    }

    /** Cancels this message. */
    public boolean cancelAll() {
        if (!isDone()) {
            mCancelled = true;
            return true;
        }
        return false;
    }

    /** Returns {@code true} if this message has been cancelled. */
    public boolean isCancelled() {
        return mCancelled;
    }

    /** Record that this message has been logged. */
    public void noteLogged() {
        mLogged = true;
    }

    /** Returns {@code true} if the message has been logged already. */
    public boolean isLogged() {
        return mLogged;
    }

    /** Iterates through each delivery that is not yet finished. */
    public void forEachUnfinishedDelivery(BiConsumer<Integer, DeliveryState> consumer) {
        for (Map.Entry<Integer, DeliveryState> entry : mDeliveryStates.entrySet()) {
            if (!entry.getValue().isDone()) {
                consumer.accept(entry.getKey(), entry.getValue());
            }
        }
    }

    /** Adds a new delivery target for this message. */
    public void addDeliveryState(Collection<Integer> associationIds, long now) {
        for (Integer associationId : associationIds) {
            if (!mDeliveryStates.containsKey(associationId)) {
                mDeliveryStates.put(associationId, new DeliveryState(now));
            }
        }
    }

    /** Returns the {@link DeliveryState} for the given association ID. */
    @Nullable
    public DeliveryState getDeliveryState(int associationId) {
        return mDeliveryStates.get(associationId);
    }

    @Override
    public String toString() {
        return "MessageRecord{"
                + "id="
                + id
                + ", userId="
                + userId
                + ", networkId='"
                + networkId
                + "', isBroadcast="
                + isBroadcast
                + ", flags="
                + mFlags
                + ", isCancelled="
                + mCancelled
                + ", deliveryStates.size()="
                + mDeliveryStates.size()
                + ", isDone="
                + isDone()
                + '}';
    }

    @Override
    public void dump(android.util.IndentingPrintWriter pw) {
        pw.println("MessageRecord:");
        pw.increaseIndent();
        pw.println("id=" + id);
        pw.println("userId=" + (userId == UserHandle.USER_ALL ? " USER_ALL" : userId));
        pw.println("networkId='" + networkId + "'");
        pw.println("isBroadcast=" + isBroadcast);
        pw.println("flags=" + mFlags);
        pw.println("isCancelled=" + mCancelled);
        pw.println("isDone=" + isDone());
        pw.println("size=" + message.length);
        pw.println("DeliveryStates:");
        pw.increaseIndent();
        for (Map.Entry<Integer, DeliveryState> entry : mDeliveryStates.entrySet()) {
            pw.println("associationId=" + entry.getKey() + ": " + entry.getValue());
        }
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
