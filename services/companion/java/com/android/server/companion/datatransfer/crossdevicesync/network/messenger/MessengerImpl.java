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
package com.android.server.companion.datatransfer.crossdevicesync.network.messenger;

import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.getException;
import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.isFutureFailed;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.companion.CompanionDeviceManager;
import android.os.Trace;
import android.util.ArrayMap;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.infra.AndroidFuture;
import com.android.server.companion.datatransfer.crossdevicesync.common.Clock;
import com.android.server.companion.datatransfer.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.server.companion.datatransfer.crossdevicesync.common.DebugConfig;
import com.android.server.companion.datatransfer.crossdevicesync.common.DelayedExecutor;
import com.android.server.companion.datatransfer.crossdevicesync.common.Utils;
import com.android.server.companion.datatransfer.crossdevicesync.network.companion.CompanionActionController;
import com.android.server.companion.datatransfer.crossdevicesync.network.model.BatchedMessage;
import com.android.server.companion.datatransfer.crossdevicesync.network.model.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/** Implementation of {@link Messenger} using CompanionDeviceManager. */
public class MessengerImpl implements Messenger {
    private static final String TAG = "Messenger";
    private static final boolean DEBUG = DebugConfig.DEBUG_NETWORK;
    @VisibleForTesting static final long RETRY_DELAY_MS = 5000;
    @VisibleForTesting static final long WAITING_FOR_TRANSPORT_TIMEOUT = 2000;
    @VisibleForTesting static final long WAITING_FOR_ACK_TIMEOUT = 2000;

    private final Object mLock;
    private final CompanionDeviceManagerProxy mCompanionDeviceManager;
    private final CompanionActionController mCompanionActionController;
    private final DelayedExecutor mMainExecutor;
    private final BiConsumer<Integer, byte[]> mMessageListener = this::onMessage;
    private final Clock mClock;
    private final Runnable mInvalidateRunnable = this::doInvalidation;

    @GuardedBy("mLock")
    private final Map<Integer, AssociationMessageState> mAssociationStates = new HashMap<>();

    @GuardedBy("mLock")
    private final List<Pair<MessageListener, Executor>> mListeners = new ArrayList<>();

    @GuardedBy("mLock")
    private final Set<Integer> mAssociationsWithTransport = new HashSet<>();

    @GuardedBy("mLock")
    private boolean mInitialized;

    @GuardedBy("mLock")
    private long mNextInvalidationTime = Long.MAX_VALUE;

    @GuardedBy("mLock")
    private long mNextHandleId;

    public MessengerImpl(
            Object networkLock,
            CompanionDeviceManagerProxy companionDeviceManager,
            DelayedExecutor mainExecutor,
            Clock clock,
            CompanionActionController actionController) {
        mLock = networkLock;
        mCompanionDeviceManager = companionDeviceManager;
        mMainExecutor = mainExecutor;
        mClock = clock;
        mCompanionActionController = actionController;
    }

    @Override
    public void init() {
        synchronized (mLock) {
            if (mInitialized) {
                throw new IllegalStateException("Already initialized!");
            }
            mInitialized = true;
            mCompanionDeviceManager.addOnMessageReceivedListener(
                    mMainExecutor,
                    CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC,
                    mMessageListener);
        }
    }

    @SuppressWarnings("GuardedBy")
    @Override
    public void destroy() {
        synchronized (mLock) {
            if (!mInitialized) {
                throw new IllegalStateException("Not initialized!");
            }
            mInitialized = false;
            mNextInvalidationTime = Long.MAX_VALUE;
            mCompanionDeviceManager.removeOnMessageReceivedListener(
                    CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC, mMessageListener);
            mListeners.clear();
            mAssociationsWithTransport.clear();
            mAssociationStates.values().forEach(AssociationMessageState::destroyLocked);
            mAssociationStates.clear();
            mMainExecutor.cancel(mInvalidateRunnable);
            Log.i(TAG, "Messenger is destroyed");
        }
    }

    @Override
    public AndroidFuture<Boolean> sendMessage(
            String networkId, int associationId, byte[] message, int maxDeliveryAttempts) {
        synchronized (mLock) {
            if (maxDeliveryAttempts < 1) {
                return Utils.failedAndroidFuture(
                        new IllegalArgumentException(
                                "Illegal maxDeliveryAttempts: " + maxDeliveryAttempts));
            }
            MessageHandle handle =
                    mAssociationStates
                            .computeIfAbsent(associationId, AssociationMessageState::new)
                            .addMessageLocked(networkId, message, maxDeliveryAttempts);
            invalidate();
            return handle;
        }
    }

    private void invalidate() {
        invalidate(/* delay= */ 0);
    }

    private void invalidate(long delay) {
        synchronized (mLock) {
            if (!mInitialized) {
                return;
            }
            long now = mClock.elapsedRealtime();
            delay = Math.max(delay, 0);
            long scheduleTime = now + delay;
            if (scheduleTime >= mNextInvalidationTime) {
                return;
            }
            mNextInvalidationTime = scheduleTime;
            if (DEBUG && delay > 0) {
                Log.d(TAG, "Invalidating in " + delay + " ms");
            }
            mMainExecutor.cancel(mInvalidateRunnable);
            mMainExecutor.executeDelayed(mInvalidateRunnable, delay);
        }
    }

    private void doInvalidation() {
        Trace.beginSection("MessengerImpl.doInvalidation");
        try {
            synchronized (mLock) {
                if (!mInitialized) {
                    return;
                }
                final long now = mClock.elapsedRealtime();
                if (now < mNextInvalidationTime) {
                    return;
                }
                if (DEBUG) {
                    Log.d(TAG, "doInvalidation: now=" + now);
                }
                mNextInvalidationTime = Long.MAX_VALUE;
                long nextInvalidation = Long.MAX_VALUE;
                for (AssociationMessageState state : mAssociationStates.values()) {
                    state.invalidateLocked(now);
                    nextInvalidation =
                            Math.min(nextInvalidation, state.getNextInvalidationTimeLocked(now));
                }
                if (nextInvalidation != Long.MAX_VALUE) {
                    invalidate(nextInvalidation - now);
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    @Override
    public void onTransportsChanged(Set<Integer> associationsWithTransport) {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "onTransportsChanged: ignore since not initialized");
                return;
            }
            if (mAssociationsWithTransport.equals(associationsWithTransport)) {
                return;
            }
            mAssociationsWithTransport.clear();
            mAssociationsWithTransport.addAll(associationsWithTransport);
            invalidate();
        }
    }

    @Override
    public void registerMessageListener(Executor executor, MessageListener listener) {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "registerMessageListener: ignore since not initialized");
                return;
            }
            mListeners.add(Pair.create(listener, executor));
        }
    }

    @Override
    public void unregisterMessageListener(MessageListener listener) {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "unregisterMessageListener: ignore since not initialized");
                return;
            }
            mListeners.removeIf(pair -> pair.first == listener);
        }
    }

    private void onMessage(int associationId, byte[] message) {
        Trace.beginSection("MessengerImpl.onMessage");
        try {
            synchronized (mLock) {
                if (!mInitialized) {
                    Log.w(TAG, "onMessage: ignore since not initialized");
                    return;
                }
                BatchedMessage batchedMessage;
                try {
                    batchedMessage = BatchedMessage.parseFrom(message);
                } catch (IOException e) {
                    Log.e(TAG, "onMessage: failed to parse incoming message!", e);
                    return;
                }
                List<Message> messages = new ArrayList(batchedMessage.messages());
                messages.sort(Comparator.comparingLong(Message::handleId));
                List<Long> ackIds = batchedMessage.ackIds();
                Log.i(
                        TAG,
                        "onMessage: received "
                                + message.length
                                + " bytes from association "
                                + associationId
                                + " containing "
                                + messages.size()
                                + " messages and "
                                + ackIds.size()
                                + " ACKs");
                if (messages.isEmpty() && ackIds.isEmpty()) {
                    return;
                }
                AssociationMessageState state =
                        mAssociationStates.computeIfAbsent(
                                associationId, AssociationMessageState::new);
                if (!messages.isEmpty()) {
                    for (Message m : messages) {
                        if (state.noteReceivedMessageLocked(
                                m.handleId(), batchedMessage.senderInstanceId())) {
                            notifyMessageLocked(associationId, m);
                        }
                    }
                    // Send the ACK in next invalidation.
                    invalidate();
                }
                state.noteReceivedAcksLocked(ackIds);
            }
        } finally {
            Trace.endSection();
        }
    }

    @GuardedBy("mLock")
    private void notifyMessageLocked(int associationId, Message message) {
        mListeners.forEach(
                l ->
                        l.second.execute(
                                () ->
                                        l.first.onMessage(
                                                message.networkId(),
                                                associationId,
                                                message.payload())));
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        synchronized (mLock) {
            pw.println("Messenger:");
            pw.increaseIndent();
            pw.println("mInitialized=" + mInitialized);
            pw.println("mNextInvalidationTime=" + mNextInvalidationTime);
            pw.println("mNextHandleId=" + mNextHandleId);
            pw.println("listeners=" + mListeners.size());
            pw.println("associationsWithTransport=" + mAssociationsWithTransport);
            if (mAssociationStates.isEmpty()) {
                pw.println("No association states.");
            } else {
                pw.println("Association states:");
                pw.increaseIndent();
                mAssociationStates.values().forEach(state -> state.dump(pw));
                pw.decreaseIndent();
            }
            pw.decreaseIndent();
        }
    }

    /** A class representing the message delivery state of an association. */
    @SuppressWarnings("EffectivelyPrivate")
    private class AssociationMessageState {
        /** Nothing to do. */
        private static final int STATE_IDLE = 0;

        /**
         * Waiting for delay before trying to attach transport, often after a failed transport
         * attachment.
         */
        private static final int STATE_WAITING_TO_ATTACH_TRANSPORT = 1;

        /** Attaching transport. */
        private static final int STATE_ATTACHING_TRANSPORT = 2;

        /** Waiting for transport. */
        private static final int STATE_WAITING_FOR_TRANSPORT = 3;

        /** Message is being sent. */
        private static final int STATE_SENDING = 4;

        public final int associationId;

        @GuardedBy("mLock")
        private final Map<Long, MessageHandle> mMessageHandles = new ArrayMap<>();

        @GuardedBy("mLock")
        private final Map<Long, MessageHandle> mAckHandles = new ArrayMap<>();

        @GuardedBy("mLock")
        private int mState = STATE_IDLE;

        @GuardedBy("mLock")
        private String mChangeReason = "initialized";

        @GuardedBy("mLock")
        private long mLastChangeTimestamp = mClock.elapsedRealtime();

        @Nullable
        @GuardedBy("mLock")
        private AndroidFuture<?> mAttachTransportFuture;

        /**
         * The maximum message handle id we received from this association. This will be used to
         * de-dup incoming messages.
         */
        @GuardedBy("mLock")
        private long mMaxReceivedHandleId = -1;

        /**
         * Keep track of the remote side instance id so that we will know when remote side has
         * restarted and clean-up local cache.
         */
        @GuardedBy("mLock")
        private long mRemoteInstanceId = -1;

        AssociationMessageState(int associationId) {
            this.associationId = associationId;
        }

        @GuardedBy("mLock")
        public MessageHandle addMessageLocked(
                String networkId, byte[] message, int maxDeliveryAttempts) {
            long handleId = mNextHandleId++;
            MessageHandle handle =
                    new MessageHandle(
                            mLock,
                            mClock,
                            handleId,
                            maxDeliveryAttempts,
                            /* name= */ "messageHandle#"
                                    + handleId
                                    + ":association#"
                                    + associationId,
                            new Message(handleId, networkId, message));
            mMessageHandles.put(handleId, handle);
            return (MessageHandle)
                    handle.whenComplete((unused, t) -> MessengerImpl.this.invalidate());
        }

        @GuardedBy("mLock")
        public void invalidateLocked(long now) {
            maybeTimeoutHandlesWaitingForAckLocked(now);
            pruneHandlesLocked();
            Pair<Integer, String> nextStateAndReason = getNextAssociationStateAndReasonLocked(now);
            boolean changed =
                    setStateLocked(nextStateAndReason.first, nextStateAndReason.second, now);
            if (mState == STATE_SENDING) {
                // Sending needs to be re-evaluated even if association state didn't change.
                maybeSendCDMMessageLocked(now);
            }
            if (!changed) {
                return;
            }
            if (mState == STATE_ATTACHING_TRANSPORT) {
                if (DEBUG) {
                    Log.d(TAG, "Requesting transport attachment for association " + associationId);
                }
                mAttachTransportFuture =
                        mCompanionActionController
                                .attachTransport(associationId)
                                .whenComplete((unused, t) -> MessengerImpl.this.invalidate());
            } else if (mState != STATE_SENDING
                    && mState != STATE_WAITING_FOR_TRANSPORT
                    && mAttachTransportFuture != null) {
                // We are neither attaching transport nor sending message nor waiting for transport.
                // Should cancel any unfinished transport request and send detach request.
                mAttachTransportFuture.cancel(true);
                mAttachTransportFuture = null;
                if (DEBUG) {
                    Log.d(TAG, "Requesting transport detachment for association " + associationId);
                }
                var ignored = mCompanionActionController.detachTransport(associationId);
                // For any pending messages, detaching is considered transport failure.
                noteTransportFailureLocked(now, new Exception(mChangeReason));
            }
        }

        @GuardedBy("mLock")
        private void maybeTimeoutHandlesWaitingForAckLocked(long now) {
            for (MessageHandle handle : mMessageHandles.values()) {
                if (handle.getState() == MessageHandle.STATE_SENDING
                        && now - handle.getLastChangeTime() >= WAITING_FOR_ACK_TIMEOUT) {
                    handle.noteSendingFailure(now, new RuntimeException("timeout waiting for ACK"));
                    Log.w(
                            TAG,
                            "Timeout waiting for ACK from association "
                                    + associationId
                                    + " for message "
                                    + handle.getHandleId());
                }
            }
        }

        @GuardedBy("mLock")
        private void pruneHandlesLocked() {
            mMessageHandles.values().removeIf(MessageHandle::isDone);
            mAckHandles.values().removeIf(MessageHandle::isDone);
        }

        @GuardedBy("mLock")
        private void noteTransportFailureLocked(long now, @Nullable Throwable error) {
            mMessageHandles.values().forEach(h -> h.noteTransportFailure(now, error));
            mAckHandles.values().forEach(h -> h.noteTransportFailure(now, error));
        }

        @GuardedBy("mLock")
        private Pair<Integer, String> getNextAssociationStateAndReasonLocked(long now) {
            if (mMessageHandles.isEmpty() && mAckHandles.isEmpty()) {
                // Message buffer is empty.
                return Pair.create(STATE_IDLE, "buffer is empty");
            }
            // Message buffer is non-empty
            final boolean hasTransport = mAssociationsWithTransport.contains(associationId);
            switch (mState) {
                case STATE_IDLE -> {
                    return Pair.create(STATE_ATTACHING_TRANSPORT, "Request attaching transport");
                }
                case STATE_WAITING_TO_ATTACH_TRANSPORT -> {
                    if (now - mLastChangeTimestamp >= RETRY_DELAY_MS) {
                        return Pair.create(
                                STATE_ATTACHING_TRANSPORT, "Retry attaching transport request");
                    } else {
                        // Continue waiting for retry delay.
                        return Pair.create(mState, mChangeReason);
                    }
                }
                case STATE_ATTACHING_TRANSPORT -> {
                    if (hasTransport) {
                        // Already have transport. No need to wait for transport attachment result.
                        return Pair.create(STATE_SENDING, "sending message");
                    }
                    if (!requireNonNull(mAttachTransportFuture).isDone()) {
                        // Continue waiting for attaching result.
                        return Pair.create(mState, mChangeReason);
                    }
                    if (isFutureFailed(mAttachTransportFuture)) {
                        // Failed to attach transport. Retry later.
                        return Pair.create(
                                STATE_WAITING_TO_ATTACH_TRANSPORT,
                                getException(mAttachTransportFuture).getMessage());
                    } else {
                        // Success!
                        return Pair.create(
                                STATE_WAITING_FOR_TRANSPORT,
                                "successfully requested transport but waiting for it to "
                                        + "appear");
                    }
                }
                case STATE_WAITING_FOR_TRANSPORT -> {
                    if (hasTransport) {
                        // Transport appeared. Sending message now.
                        return Pair.create(STATE_SENDING, "sending message");
                    }
                    if (now - mLastChangeTimestamp >= WAITING_FOR_TRANSPORT_TIMEOUT) {
                        // Attach request succeeded but didn't see transport appear within
                        // timeout. Detach now and retry later.
                        return Pair.create(
                                STATE_WAITING_TO_ATTACH_TRANSPORT,
                                "transport doesn't appear within timeout");
                    } else {
                        // Continue waiting for transport to appear.
                        return Pair.create(mState, mChangeReason);
                    }
                }
                case STATE_SENDING -> {
                    if (!hasTransport) {
                        return Pair.create(
                                STATE_WAITING_TO_ATTACH_TRANSPORT, "lost transport unexpectedly");
                    }
                    // Continue sending.
                    return Pair.create(mState, mChangeReason);
                }
                default -> {
                    throw new IllegalStateException("Unknown state: " + mState);
                }
            }
        }

        @GuardedBy("mLock")
        private long getNextInvalidationTimeLocked(long now) {
            switch (mState) {
                case STATE_WAITING_TO_ATTACH_TRANSPORT -> {
                    return mLastChangeTimestamp + RETRY_DELAY_MS;
                }
                case STATE_ATTACHING_TRANSPORT -> {
                    // Invalidate immediately when a transport is already attached.
                    return mAssociationsWithTransport.contains(associationId)
                            ? now
                            : Long.MAX_VALUE;
                }
                case STATE_WAITING_FOR_TRANSPORT -> {
                    return mLastChangeTimestamp + WAITING_FOR_TRANSPORT_TIMEOUT;
                }
                case STATE_SENDING -> {
                    long nextInvalidation = Long.MAX_VALUE;
                    for (MessageHandle handle : mMessageHandles.values()) {
                        if (handle.isDone()) {
                            continue;
                        }
                        if (handle.getState() == MessageHandle.STATE_PENDING_RETRY) {
                            nextInvalidation =
                                    Math.min(
                                            nextInvalidation,
                                            handle.getLastChangeTime() + RETRY_DELAY_MS);
                        } else if (handle.getState() == MessageHandle.STATE_SENDING) {
                            nextInvalidation =
                                    Math.min(
                                            nextInvalidation,
                                            handle.getLastChangeTime() + WAITING_FOR_ACK_TIMEOUT);
                        }
                    }
                    return nextInvalidation;
                }
                default -> {
                    return Long.MAX_VALUE;
                }
            }
        }

        @GuardedBy("mLock")
        private boolean setStateLocked(int state, String changeReason, long now) {
            if (this.mState == state) {
                return false;
            }
            if (DEBUG) {
                Log.d(
                        TAG,
                        "Association "
                                + associationId
                                + " message state changed: "
                                + stateToString(this.mState)
                                + " -> "
                                + stateToString(state)
                                + ", reason: "
                                + changeReason);
            }
            this.mState = state;
            this.mChangeReason = changeReason;
            this.mLastChangeTimestamp = now;
            return true;
        }

        @GuardedBy("mLock")
        private void maybeSendCDMMessageLocked(long now) {
            List<Message> messages = new ArrayList<>(mMessageHandles.size());
            for (MessageHandle handle : mMessageHandles.values()) {
                if (handle.isDone() || handle.getState() == MessageHandle.STATE_SENDING) {
                    // No need to send.
                    continue;
                }
                if (handle.getState() == MessageHandle.STATE_PENDING_RETRY
                        && (now - handle.getLastChangeTime()) < RETRY_DELAY_MS) {
                    // Waiting for retry timeout.
                    continue;
                }
                messages.add(handle.getMessage());
                handle.noteSending(now);
            }
            List<Long> acks = new ArrayList<>(mAckHandles.size());
            for (MessageHandle handle : mAckHandles.values()) {
                if (handle.isDone()) {
                    // No need to send.
                    continue;
                }
                acks.add(handle.getHandleId());
                // ACKs should be fire and forget.
                handle.noteSuccess(now);
            }
            if (messages.isEmpty() && acks.isEmpty()) {
                // Nothing to send.
                return;
            }
            byte[] encodedMessage =
                    new BatchedMessage(messages, acks, MessengerImpl.this.hashCode()).toByteArray();
            Log.i(
                    TAG,
                    "Sending "
                            + messages.size()
                            + " messages and "
                            + acks.size()
                            + " ACKs to association "
                            + associationId
                            + ". Total size: "
                            + encodedMessage.length
                            + " bytes.");
            mCompanionDeviceManager.sendMessage(
                    CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC,
                    encodedMessage,
                    new int[] {associationId});
        }

        @GuardedBy("mLock")
        public boolean noteReceivedMessageLocked(long handleId, long remoteInstanceId) {
            if (mAckHandles.containsKey(handleId)) {
                Log.w(
                        TAG,
                        "noteReceivedMessageLocked: received duplicated handle id "
                                + handleId
                                + " from association "
                                + associationId
                                + ". Ignoring.");
                return false;
            }
            if (remoteInstanceId != mRemoteInstanceId && mRemoteInstanceId != -1) {
                Log.i(
                        TAG,
                        "Detected remote instance id change in association "
                                + associationId
                                + ". Cleaning up local cache.");
                mAckHandles.values().forEach(h -> h.cancel(true));
                mAckHandles.clear();
                mMaxReceivedHandleId = -1;
            }
            mRemoteInstanceId = remoteInstanceId;
            MessageHandle handle =
                    new MessageHandle(
                            mLock,
                            mClock,
                            handleId,
                            /* maxAttempts= */ 1,
                            /* name= */ "ackHandle#" + handleId + ":association#" + associationId,
                            /* message= */ null);
            mAckHandles.put(handleId, handle);
            handle.whenComplete((unused, t) -> MessengerImpl.this.invalidate());
            boolean shouldDeliver = handleId > mMaxReceivedHandleId;
            mMaxReceivedHandleId = Math.max(mMaxReceivedHandleId, handleId);
            if (!shouldDeliver) {
                Log.w(
                        TAG,
                        "noteReceivedMessageLocked: received too old handle id "
                                + handleId
                                + " from association "
                                + associationId
                                + ". This is likely a duplicated message and will be "
                                + "ignored.");
            }
            return shouldDeliver;
        }

        @GuardedBy("mLock")
        public void noteReceivedAcksLocked(Collection<Long> acks) {
            long now = mClock.elapsedRealtime();
            for (Long ackId : acks) {
                MessageHandle handle = mMessageHandles.get(ackId);
                if (handle != null) {
                    handle.noteSuccess(now);
                }
            }
        }

        @GuardedBy("mLock")
        public void destroyLocked() {
            mMessageHandles.values().forEach(h -> h.cancel(true));
            mMessageHandles.clear();
            mAckHandles.values().forEach(h -> h.cancel(true));
            mAckHandles.clear();
            if (mAttachTransportFuture != null) {
                mAttachTransportFuture.cancel(true);
                mAttachTransportFuture = null;
            }
        }

        private static String stateToString(int state) {
            return switch (state) {
                case STATE_IDLE -> "IDLE";
                case STATE_WAITING_TO_ATTACH_TRANSPORT -> "WAITING_TO_ATTACH_TRANSPORT";
                case STATE_ATTACHING_TRANSPORT -> "ATTACHING_TRANSPORT";
                case STATE_WAITING_FOR_TRANSPORT -> "WAITING_FOR_TRANSPORT";
                case STATE_SENDING -> "SENDING";
                default -> "UNKNOWN(" + state + ")";
            };
        }

        private void dump(IndentingPrintWriter pw) {
            synchronized (mLock) {
                pw.println("AssociationMessageState:");
                pw.increaseIndent();
                pw.println("associationId=" + associationId);
                pw.println("state=" + stateToString(mState));
                pw.println("changeReason=" + mChangeReason);
                pw.println("lastChangeTimestamp=" + mLastChangeTimestamp);
                pw.println("maxReceivedHandleId=" + mMaxReceivedHandleId);
                pw.println("remoteInstanceId=" + mRemoteInstanceId);
                if (mMessageHandles.isEmpty()) {
                    pw.println("No message handles.");
                } else {
                    pw.println("messageHandles (" + mMessageHandles.size() + "):");
                    pw.increaseIndent();
                    mMessageHandles.values().forEach(h -> h.dump(pw));
                    pw.decreaseIndent();
                }
                if (mAckHandles.isEmpty()) {
                    pw.println("No ack handles.");
                } else {
                    pw.println("ackHandles (" + mAckHandles.size() + "):");
                    pw.increaseIndent();
                    mAckHandles.values().forEach(h -> h.dump(pw));
                    pw.decreaseIndent();
                }
                pw.decreaseIndent();
            }
        }
    }
}
