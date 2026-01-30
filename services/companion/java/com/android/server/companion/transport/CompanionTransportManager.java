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

package com.android.server.companion.transport;

import static android.companion.CompanionDeviceManager.MESSAGE_REQUEST_PERMISSION_RESTORE;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.companion.AssociationInfo;
import android.companion.IOnMessageReceivedListener;
import android.companion.IOnTransportEventListener;
import android.companion.IOnTransportsChangedListener;
import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.companion.association.AssociationStore;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@SuppressLint("LongLogTag")
public class CompanionTransportManager {
    private static final String TAG = "CDM_CompanionTransportManager";

    private int mOverriddenTransportType = 0;

    private final Context mContext;
    private final AssociationStore mAssociationStore;

    /** Association id -> Transport */
    @GuardedBy("mTransports")
    private final SparseArray<Transport> mTransports = new SparseArray<>();

    // Use mTransports to synchronize both mTransports and mTransportsListeners to avoid deadlock
    // between threads that access both
    @NonNull
    @GuardedBy("mTransports")
    private final RemoteCallbackList<IOnTransportsChangedListener> mTransportsListeners =
            new RemoteCallbackList<>();

    /** Association ID -> IOnTransportEventListener
     * Can be registered even if the transport for a given association ID doesn't exist yet.
     * The transport manager will retroactively add newly registered listeners to an existing
     * transport and also add all registered listeners to a new transport.
     */
    @GuardedBy("mEventListeners")
    @NonNull
    private final SparseArray<Set<IOnTransportEventListener>> mEventListeners =
            new SparseArray<>();

    /** Message type -> IOnMessageReceivedListener */
    @GuardedBy("mMessageListeners")
    @NonNull
    private final SparseArray<Set<IOnMessageReceivedListener>> mMessageListeners =
            new SparseArray<>();

    public CompanionTransportManager(Context context, AssociationStore associationStore) {
        mContext = context;
        mAssociationStore = associationStore;
    }

    /**
     * Add a listener to receive callbacks when a message is received for the message type
     */
    public void addListener(int message, @NonNull IOnMessageReceivedListener listener) {
        synchronized (mMessageListeners) {
            if (!mMessageListeners.contains(message)) {
                mMessageListeners.put(message, new HashSet<IOnMessageReceivedListener>());
            }
            mMessageListeners.get(message).add(listener);
        }
        synchronized (mTransports) {
            for (int i = 0; i < mTransports.size(); i++) {
                mTransports.valueAt(i).addListener(message, listener);
            }
        }
    }

    /**
     * Add a listener to receive callbacks when transport reports an event
     */
    public void addListener(int associationId, IOnTransportEventListener listener) {
        synchronized (mEventListeners) {
            if (!mEventListeners.contains(associationId)) {
                mEventListeners.put(associationId, new HashSet<IOnTransportEventListener>());
            }
            mEventListeners.get(associationId).add(listener);
        }
        synchronized (mTransports) {
            if (!mTransports.contains(associationId)) {
                return;
            }
            mTransports.get(associationId).addListener(listener);
        }
    }

    /**
     * Add a listener to receive callbacks when any of the transports is changed
     */
    public void addListener(IOnTransportsChangedListener listener) {
        Slog.i(TAG, "Registering OnTransportsChangedListener");
        synchronized (mTransports) {
            mTransportsListeners.register(listener);
            mTransportsListeners.broadcast(listener1 -> {
                // callback to the current listener with all the associations of the transports
                // immediately
                if (listener1 == listener) {
                    try {
                        listener.onTransportsChanged(getAssociationsWithTransport());
                    } catch (RemoteException ignored) {
                    }
                }
            });
        }
    }

    /**
     * Remove the listener for transport events. Ignore if there is no transport for the given ID.
     */
    public void removeListener(int associationId, IOnTransportEventListener listener) {
        synchronized (mEventListeners) {
            if (!mEventListeners.contains(associationId)) {
                return;
            }
            mEventListeners.get(associationId).remove(listener);
        }
        synchronized (mTransports) {
            if (!mTransports.contains(associationId)) {
                return;
            }
            mTransports.get(associationId).removeListener(listener);
        }
    }

    /**
     * Remove all listeners for a given association (for clean up during disassociation).
     */
    public void removeListeners(int associationId) {
        synchronized (mEventListeners) {
            mEventListeners.delete(associationId);
        }
    }

    /**
     * Remove the listener for receiving callbacks when any of the transports is changed
     */
    public void removeListener(IOnTransportsChangedListener listener) {
        synchronized (mTransports) {
            mTransportsListeners.unregister(listener);
        }
    }

    /**
     * Remove the listener to stop receiving calbacks when a message is received for the given type
     */
    public void removeListener(int messageType, IOnMessageReceivedListener listener) {
        synchronized (mMessageListeners) {
            if (!mMessageListeners.contains(messageType)) {
                return;
            }
            mMessageListeners.get(messageType).remove(listener);
        }
    }

    /**
     * Send a message to remote devices through the transports
     */
    public SparseArray<Future<byte[]>> sendMessage(int message, byte[] data, int[] associationIds) {
        Slog.d(TAG, "Sending message 0x" + Integer.toHexString(message)
                + " data length " + data.length);
        SparseArray<Future<byte[]>> futures = new SparseArray<>();
        synchronized (mTransports) {
            for (int i = 0; i < associationIds.length; i++) {
                int associationId = associationIds[i];
                if (mTransports.contains(associationId)) {
                    futures.put(associationId,
                            mTransports.get(associationId).sendMessage(message, data));
                }
            }
        }
        return futures;
    }

    /**
     * Attach transport.
     */
    public void attachSystemDataTransport(int associationId, ParcelFileDescriptor fd) {
        Slog.i(TAG, "Attaching transport for association id=[" + associationId + "]...");

        AssociationInfo association =
                mAssociationStore.getAssociationWithCallerChecks(associationId);

        synchronized (mTransports) {
            if (mTransports.contains(associationId)) {
                detachSystemDataTransport(associationId);
            }

            // TODO: Implement new API to pass a PSK
            initializeTransport(association, fd, null);

            notifyOnTransportsChanged();
        }

        Slog.i(TAG, "Transport attached.");
    }

    /**
     * Detach transport.
     */
    public void detachSystemDataTransport(int associationId) {
        Slog.i(TAG, "Detaching transport for association id=[" + associationId + "]...");

        mAssociationStore.getAssociationWithCallerChecks(associationId);

        synchronized (mTransports) {
            final Transport transport = mTransports.removeReturnOld(associationId);
            if (transport == null) {
                return;
            }

            transport.close();
            notifyOnTransportsChanged();
        }

        Slog.i(TAG, "Transport detached.");
    }

    /**
     * Returns a list of associations that has a transport attached.
     * @return a list of associations
     */
    public List<AssociationInfo> getAssociationsWithTransport() {
        List<AssociationInfo> associations = new ArrayList<>();
        synchronized (mTransports) {
            for (int i = 0; i < mTransports.size(); i++) {
                AssociationInfo association = mAssociationStore.getAssociationById(
                        mTransports.keyAt(i));
                if (association != null) {
                    associations.add(association);
                }
            }
        }
        return associations;
    }

    private void notifyOnTransportsChanged() {
        synchronized (mTransports) {
            mTransportsListeners.broadcast(listener -> {
                try {
                    listener.onTransportsChanged(getAssociationsWithTransport());
                } catch (RemoteException ignored) {
                }
            });
        }
    }

    private void initializeTransport(AssociationInfo association,
                                     ParcelFileDescriptor fd,
                                     byte[] preSharedKey) {
        Slog.i(TAG, "Initializing transport");
        int flags = association.getTransportFlags();
        Transport transport = createTransport(association, fd, preSharedKey, flags);
        addListenersToTransport(transport);
        transport.setOnTransportClosedListener(this::detachSystemDataTransport);
        transport.start();
        synchronized (mTransports) {
            mTransports.put(association.getId(), transport);
        }
    }

    private Transport createTransport(AssociationInfo association,
            ParcelFileDescriptor fd,
            byte[] preSharedKey,
            int flags) {
        int associationId = association.getId();

        // If transport type is overridden to secure, create a secure transport.
        if (mOverriddenTransportType == 2) {
            Slog.i(TAG, "Creating secure transport by override");
            return new SecureTransport(associationId, fd, mContext, flags);
        }

        // If transport type is overridden to raw, create a raw transport.
        if (mOverriddenTransportType == 1) {
            Slog.i(TAG, "Creating raw transport by override");
            return new RawTransport(associationId, fd, mContext);
        }

        // If device is debug build, use hardcoded test key for authentication
        if (Build.isDebuggable()) {
            Slog.d(TAG, "Creating an unauthenticated secure channel");
            final byte[] testKey = "CDM".getBytes(StandardCharsets.UTF_8);
            return new SecureTransport(associationId, fd, mContext, testKey, null, 0);
        }

        // If either device is not Android, then use app-specific pre-shared key
        if (preSharedKey != null) {
            Slog.d(TAG, "Creating a PSK-authenticated secure channel");
            return new SecureTransport(associationId, fd, mContext, preSharedKey, null, 0);
        }

        // If none of the above applies, then use secure channel with attestation verification
        Slog.d(TAG, "Creating a secure channel");
        return new SecureTransport(associationId, fd, mContext, flags);
    }

    public Future<?> requestPermissionRestore(int associationId, byte[] data) {
        synchronized (mTransports) {
            final Transport transport = mTransports.get(associationId);
            if (transport == null) {
                return CompletableFuture.failedFuture(new IOException("Missing transport"));
            }
            return transport.sendMessage(MESSAGE_REQUEST_PERMISSION_RESTORE, data);
        }
    }

    /**
     * Dumps current list of active transports.
     */
    public void dump(@NonNull PrintWriter out) {
        synchronized (mTransports) {
            out.append("System Data Transports: ");
            if (mTransports.size() == 0) {
                out.append("<empty>\n");
            } else {
                out.append("\n");
                for (int i = 0; i < mTransports.size(); i++) {
                    final int associationId = mTransports.keyAt(i);
                    final Transport transport = mTransports.get(associationId);
                    out.append("  ").append(transport.toString()).append('\n');
                }
            }
        }
    }

    /**
     * @hide
     */
    public void overrideTransportType(int typeOverride) {
        this.mOverriddenTransportType = typeOverride;
    }

    /**
     * For testing purpose only.
     *
     * Create an emulated RawTransport and notify onTransportChanged listeners.
     */
    public EmulatedTransport createEmulatedTransport(int associationId) {
        synchronized (mTransports) {
            FileDescriptor fd = new FileDescriptor();
            ParcelFileDescriptor pfd = new ParcelFileDescriptor(fd);
            EmulatedTransport transport = new EmulatedTransport(associationId, pfd, mContext);
            addListenersToTransport(transport);
            mTransports.put(associationId, transport);
            notifyOnTransportsChanged();
            return transport;
        }
    }

    /**
     * For testing purposes only.
     *
     * Emulates a transport for incoming messages but black-holes all messages sent back through it.
     */
    public static class EmulatedTransport extends RawTransport {

        EmulatedTransport(int associationId, ParcelFileDescriptor fd, Context context) {
            super(associationId, fd, context);
        }

        /** Process an incoming message for testing purposes. */
        public void processMessage(int messageType, int sequence, byte[] data) throws IOException {
            handleMessage(messageType, sequence, data);
        }

        @Override
        protected void sendMessage(int messageType, int sequence, @NonNull byte[] data)
                throws IOException {
            Slog.e(TAG, "Black-holing emulated message type 0x" + Integer.toHexString(messageType)
                    + " sequence " + sequence + " length " + data.length
                    + " to association " + mAssociationId);
        }
    }

    private void addListenersToTransport(Transport transport) {
        synchronized (mMessageListeners) {
            for (int i = 0; i < mMessageListeners.size(); i++) {
                for (IOnMessageReceivedListener listener : mMessageListeners.valueAt(i)) {
                    transport.addListener(mMessageListeners.keyAt(i), listener);
                }
            }
        }
        synchronized (mEventListeners) {
            int associationId = transport.getAssociationId();
            Set<IOnTransportEventListener> listeners = mEventListeners.get(associationId);
            if (listeners != null) {
                for (IOnTransportEventListener listener : listeners) {
                    transport.addListener(listener);
                }
            }
        }
    }

    void detachSystemDataTransport(Transport transport) {
        int associationId = transport.mAssociationId;
        AssociationInfo association = mAssociationStore.getAssociationById(associationId);
        if (association != null) {
            detachSystemDataTransport(
                    association.getId());
        }
    }
}
