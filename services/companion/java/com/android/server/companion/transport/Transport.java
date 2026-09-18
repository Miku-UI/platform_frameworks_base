/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.companion.transport;

import static android.companion.CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC;
import static android.companion.CompanionDeviceManager.MESSAGE_ONEWAY_FROM_WEARABLE;
import static android.companion.CompanionDeviceManager.MESSAGE_ONEWAY_PCC;
import static android.companion.CompanionDeviceManager.MESSAGE_ONEWAY_PING;
import static android.companion.CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY;
import static android.companion.CompanionDeviceManager.MESSAGE_ONEWAY_TO_WEARABLE;
import static android.companion.CompanionDeviceManager.MESSAGE_REQUEST_CONTEXT_SYNC;
import static android.companion.CompanionDeviceManager.MESSAGE_REQUEST_METADATA_UPDATE;
import static android.companion.CompanionDeviceManager.MESSAGE_REQUEST_PERMISSION_RESTORE;
import static android.companion.CompanionDeviceManager.MESSAGE_REQUEST_PING;
import static android.companion.CompanionDeviceManager.MESSAGE_REQUEST_REMOTE_AUTHENTICATION;
import static android.companion.CompanionDeviceManager.MESSAGE_REQUEST_TRUSTED_DEVICE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.companion.AssociationRequest;
import android.companion.CompanionDeviceManager.MessageType;
import android.companion.IOnMessageReceivedListener;
import android.companion.IOnTransportEventListener;
import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import libcore.util.EmptyArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class represents the channel established between two devices.
 */
public abstract class Transport {
    /**
     * Flag to extend attestation patch validation allowance to 2 years instead of 1 year.
     * Requires {@link AssociationRequest#DEVICE_PROFILE_WEARABLE_SENSING} device profile.
     */
    public static final int FLAG_EXTEND_PATCH_DIFF = 1;

    protected static final String TAG = "CDM_CompanionTransport";
    protected static final boolean DEBUG = Build.IS_DEBUGGABLE;

    static final int MESSAGE_RESPONSE_SUCCESS = 0x33838567; // !SUC
    static final int MESSAGE_RESPONSE_FAILURE = 0x33706573; // !FAI

    protected static final int HEADER_LENGTH = 12;

    protected final int mAssociationId;
    protected final ParcelFileDescriptor mFd;
    protected final InputStream mRemoteIn;
    protected final OutputStream mRemoteOut;
    protected final Context mContext;

    protected final MessageQueue mMessageQueue;

    /** @hide */
    @IntDef(value = {
            SUCCESSFUL_CONNECTION,
            ERROR_UPDATE_REQUIRED,
            ERROR_TOO_MANY_REQUESTS,
            ERROR_UNKNOWN,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TransportEvent {}

    /**
     * Indicates channel is connected and ready to exchange messages.
     */
    public static final int SUCCESSFUL_CONNECTION = 200;

    /**
     * Reserved for AVF patch level difference error.
     */
    public static final int ERROR_UPDATE_REQUIRED = 427;

    /**
     * Indicates that the message queue is full.
     */
    public static final int ERROR_TOO_MANY_REQUESTS = 429;

    /**
     * Fallback error code.
     */
    public static final int ERROR_UNKNOWN = 500;

    /**
     * Message type -> Listener
     *
     * For now, the transport only supports 1 listener for each message type. If there's a need in
     * the future to allow multiple listeners to receive callbacks for the same message type, the
     * value of the map can be a list.
     */
    @GuardedBy("mListeners")
    private final SparseArray<Set<IOnMessageReceivedListener>> mListeners = new SparseArray<>();

    @GuardedBy("mEventListeners")
    private final RemoteCallbackList<IOnTransportEventListener> mEventListeners =
            new RemoteCallbackList<>();

    private OnTransportClosedListener mOnTransportClosed;

    private static boolean isRequest(@MessageType int message) {
        return (message & 0xFF000000) == 0x63000000;
    }

    private static boolean isResponse(@MessageType int message) {
        return (message & 0xFF000000) == 0x33000000;
    }

    private static boolean isOneway(@MessageType int message) {
        return (message & 0xFF000000) == 0x43000000;
    }

    @GuardedBy("mPendingRequests")
    protected final SparseArray<CompletableFuture<byte[]>> mPendingRequests =
            new SparseArray<>();
    protected final AtomicInteger mNextSequence = new AtomicInteger();

    Transport(int associationId, ParcelFileDescriptor fd, Context context) {
        mAssociationId = associationId;
        mFd = fd;
        mRemoteIn = new ParcelFileDescriptor.AutoCloseInputStream(fd);
        mRemoteOut = new ParcelFileDescriptor.AutoCloseOutputStream(fd);
        mContext = context;
        mMessageQueue = new MessageQueue();
    }

    /**
     * Add a listener when a message is received for the message type
     * @param message Message type
     * @param listener Execute when a message with the type is received
     */
    public void addListener(@MessageType int message, IOnMessageReceivedListener listener) {
        synchronized (mListeners) {
            if (!mListeners.contains(message)) {
                mListeners.put(message, new HashSet<IOnMessageReceivedListener>());
            }
            mListeners.get(message).add(listener);
        }
    }

    /**
     * Add a listener for transport events
     * @param listener Executes when this transport reports an event
     */
    void addListener(IOnTransportEventListener listener) {
        synchronized (mEventListeners) {
            mEventListeners.register(listener);
        }
    }

    /**
     * Remove registered event listener.
     */
    void removeListener(IOnTransportEventListener listener) {
        synchronized (mEventListeners) {
            mEventListeners.unregister(listener);
        }
    }

    /**
     * Returns the session key for the transport of the given association id.
     *
     * @return the session key. Null if transport does not have a session.
     */
    public abstract byte[] getSessionKey();

    /**
     * Returns the session role for the transport of the given association id.
     *
     * @return the session role. Null if transport does not have a session.
     */
    public abstract String getSessionRole();

    public int getAssociationId() {
        return mAssociationId;
    }

    protected ParcelFileDescriptor getFd() {
        return mFd;
    }

    /**
     * Start sending and listening to messages.
     */
    abstract void start();

    /**
     * Soft stop listening to the incoming data without closing the streams.
     */
    abstract void stop();

    /**
     * Stop listening to the incoming data and close the streams. If a listener for closed event
     * is set, then trigger it to assist with its clean-up.
     */
    void close() {
        if (mOnTransportClosed != null) {
            mOnTransportClosed.onClosed(this);
        }
    }

    protected void enqueueMessage(int message, int sequence, @NonNull byte[] data)
            throws IOException {
        if (DEBUG) {
            Slog.d(TAG, "Queueing message 0x" + Integer.toHexString(message)
                    + " sequence " + sequence + " length " + data.length
                    + " to association " + mAssociationId);
        }

        // Queue up a message to send
        try {
            byte[] request = ByteBuffer.allocate(HEADER_LENGTH + data.length)
                    .putInt(message)
                    .putInt(sequence)
                    .putInt(data.length)
                    .put(data)
                    .array();
            mMessageQueue.add(message, request);
        } catch (IllegalStateException e) {
            // Request buffer can only be full if too many requests are being added or
            // the request processing thread is dead. Assume latter and detach the transport.
            Slog.w(TAG, "Failed to queue message 0x" + Integer.toHexString(message)
                    + " . Request buffer is full; detaching transport.", e);
            eventCallback(ERROR_TOO_MANY_REQUESTS);
            close();
        }
    }

    /**
     * Send a message using this transport. If the message was a request, then the returned Future
     * object will complete successfully only if the remote device both received and processed it
     * as expected. If the message was a send-and-forget type message, then the Future object will
     * resolve successfully immediately (with null) upon sending the message.
     *
     * @param message the message type
     * @param data the message payload
     * @return CompletableFuture object containing the result of the sent message.
     */
    public CompletableFuture<byte[]> sendMessage(@MessageType int message, byte[] data) {
        final CompletableFuture<byte[]> pending = new CompletableFuture<>();
        if (data == null) {
            pending.completeExceptionally(new IllegalArgumentException(
                    "The payload cannot be null."
            ));
        } else if (isOneway(message)) {
            return sendAndForget(message, data);
        } else if (isRequest(message)) {
            return requestForResponse(message, data);
        } else {
            Slog.w(TAG, "Failed to send message 0x" + Integer.toHexString(message));
            pending.completeExceptionally(new IllegalArgumentException(
                    "The message being sent must be either a one-way or a request."
            ));
        }
        return pending;
    }

    /**
     * @deprecated Method was renamed to sendMessage(int, byte[]) to support both
     * send-and-forget type messages as well as wait-for-response type messages.
     *
     * @param message request message type
     * @param data the message payload
     * @return future object containing the result of the request.
     *
     * @see #sendMessage(int, byte[])
     */
    @Deprecated
    public CompletableFuture<byte[]> requestForResponse(@MessageType int message, byte[] data) {
        if (DEBUG) Slog.d(TAG, "Requesting for response");
        final int sequence = mNextSequence.incrementAndGet();
        final CompletableFuture<byte[]> pending = new CompletableFuture<>();
        synchronized (mPendingRequests) {
            mPendingRequests.put(sequence, pending);
        }

        try {
            enqueueMessage(message, sequence, data);
        } catch (IOException e) {
            synchronized (mPendingRequests) {
                mPendingRequests.remove(sequence);
            }
            pending.completeExceptionally(e);
        }

        return pending;
    }

    private CompletableFuture<byte[]> sendAndForget(@MessageType int message, byte[]data) {
        if (DEBUG) Slog.d(TAG, "Sending a one-way message");
        final CompletableFuture<byte[]> pending = new CompletableFuture<>();

        try {
            enqueueMessage(message, -1, data);
            pending.complete(null);
        } catch (IOException e) {
            pending.completeExceptionally(e);
        }

        return pending;
    }

    protected final void handleMessage(@MessageType int message, int sequence, @NonNull byte[] data)
            throws IOException {
        if (DEBUG) {
            Slog.d(TAG, "Received message 0x" + Integer.toHexString(message)
                    + " sequence " + sequence + " length " + data.length
                    + " from association " + mAssociationId);
        }

        if (isOneway(message)) {
            processOneway(message, data);
        } else if (isRequest(message)) {
            try {
                processRequest(message, sequence, data);
            } catch (IOException e) {
                Slog.w(TAG, "Failed to respond to 0x" + Integer.toHexString(message), e);
            }
        } else if (isResponse(message)) {
            processResponse(message, sequence, data);
        } else {
            Slog.w(TAG, "Unknown message 0x" + Integer.toHexString(message));
        }
    }

    protected final void eventCallback(@TransportEvent int event) {
        synchronized (mEventListeners) {
            mEventListeners.broadcast(listener -> {
                try {
                    listener.onTransportEvent(event);
                } catch (RemoteException ignored) {
                }
            });
        }
    }

    @TransportEvent
    protected int translateError(Throwable error) {
        return ERROR_UNKNOWN;
    }

    private void processOneway(int message, byte[] data) {
        switch (message) {
            case MESSAGE_ONEWAY_PING:
            case MESSAGE_ONEWAY_PCC:
            case MESSAGE_ONEWAY_FROM_WEARABLE:
            case MESSAGE_ONEWAY_TO_WEARABLE:
            case MESSAGE_ONEWAY_TASK_CONTINUITY:
            case MESSAGE_ONEWAY_CROSS_DEVICE_SYNC: {
                callback(message, data);
                break;
            }
            default: {
                Slog.w(TAG, "Ignoring unknown message 0x" + Integer.toHexString(message));
                break;
            }
        }
    }

    private void processRequest(int message, int sequence, byte[] data)
            throws IOException {
        switch (message) {
            case MESSAGE_REQUEST_PING: {
                enqueueMessage(MESSAGE_RESPONSE_SUCCESS, sequence, data);
                break;
            }
            case MESSAGE_REQUEST_PERMISSION_RESTORE: {
                try {
                    callback(message, data);
                    enqueueMessage(MESSAGE_RESPONSE_SUCCESS, sequence, EmptyArray.BYTE);
                } catch (Exception e) {
                    Slog.w(TAG, "Failed to restore permissions");
                    enqueueMessage(MESSAGE_RESPONSE_FAILURE, sequence, EmptyArray.BYTE);
                }
                break;
            }
            case MESSAGE_REQUEST_TRUSTED_DEVICE:
            case MESSAGE_REQUEST_CONTEXT_SYNC:
            case MESSAGE_REQUEST_REMOTE_AUTHENTICATION:
            case MESSAGE_REQUEST_METADATA_UPDATE: {
                try {
                    callback(message, data);
                    enqueueMessage(MESSAGE_RESPONSE_SUCCESS, sequence, EmptyArray.BYTE);
                } catch (Exception e) {
                    Slog.w(TAG, "Failed to execute callback for request 0x"
                            + Integer.toHexString(message));
                    enqueueMessage(MESSAGE_RESPONSE_FAILURE, sequence, EmptyArray.BYTE);
                }
                break;
            }
            default: {
                Slog.w(TAG, "Unknown request 0x" + Integer.toHexString(message));
                enqueueMessage(MESSAGE_RESPONSE_FAILURE, sequence, EmptyArray.BYTE);
                break;
            }
        }
    }

    private void callback(@MessageType int message, byte[] data) {
        Set<IOnMessageReceivedListener> listenersToCall;
        synchronized (mListeners) {
            if (!mListeners.contains(message)) {
                return;
            }
            listenersToCall = mListeners.get(message);
        }
        Slog.d(TAG, "Message 0x" + Integer.toHexString(message)
                + " is received from associationId " + mAssociationId
                + ", sending data length " + data.length + " to the listener(s).");
        for (IOnMessageReceivedListener listener: listenersToCall) {
            try {
                listener.onMessageReceived(getAssociationId(), data);
            } catch (RemoteException ignored) {
            }
        }
    }

    private void processResponse(int message, int sequence, byte[] data) {
        final CompletableFuture<byte[]> future;
        synchronized (mPendingRequests) {
            future = mPendingRequests.removeReturnOld(sequence);
        }
        if (future == null) {
            Slog.w(TAG, "Ignoring unknown sequence " + sequence);
            return;
        }

        switch (message) {
            case MESSAGE_RESPONSE_SUCCESS: {
                future.complete(data);
                break;
            }
            case MESSAGE_RESPONSE_FAILURE: {
                future.completeExceptionally(new RuntimeException("Remote failure"));
                break;
            }
            default: {
                Slog.w(TAG, "Ignoring unknown response 0x" + Integer.toHexString(message));
            }
        }
    }

    void setOnTransportClosedListener(OnTransportClosedListener callback) {
        this.mOnTransportClosed = callback;
    }

    // Interface to pass transport to the transport manager to assist with detachment.
    @FunctionalInterface
    interface OnTransportClosedListener {
        void onClosed(Transport transport);
    }
}
