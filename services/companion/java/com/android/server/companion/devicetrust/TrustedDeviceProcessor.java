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

package com.android.server.companion.devicetrust;

import static android.companion.CompanionDeviceManager.MESSAGE_REQUEST_TRUSTED_DEVICE;

import static com.android.server.companion.utils.CryptoUtils.hkdfExpand;
import static com.android.server.companion.utils.CryptoUtils.hkdfExtract;
import static com.android.server.companion.utils.Utils.bitwiseOr;

import android.annotation.NonNull;
import android.annotation.RequiresNoPermission;
import android.annotation.UserIdInt;
import android.companion.AssociationInfo;
import android.companion.IOnMessageReceivedListener;
import android.companion.IOnTransportEventListener;
import android.companion.IOnTransportsChangedListener;
import android.content.Context;
import android.os.Build;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.CollectionUtils;
import com.android.server.companion.association.AssociationStore;
import com.android.server.companion.transport.CompanionTransportManager;
import com.android.server.companion.transport.Transport;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Manages trusted devices for Companion Device Manager.
 *
 * <p>This class is responsible for storing and retrieving session keys for trusted devices,
 * and handling trusted devices verification requests.
 */
public class TrustedDeviceProcessor {
    private static final String TAG = "CDM_TrustedDeviceProcessor";

    private final Context mContext;
    private final AssociationStore mAssociationStore;
    private final TrustedDeviceStore mTrustedDeviceStore;
    private final CompanionTransportManager mTransportManager;

    @GuardedBy("mLock")
    private final SparseArray<Transport> mCurrentSessions = new SparseArray<>();
    @GuardedBy("mLock")
    private final SparseArray<CountDownLatch> mVerificationLatches = new SparseArray<>();
    // Atomizes fetching available keys, which normally needs to be done twice per exchange.
    // (once for sending the keys and once for verifying the received keys)
    private final SparseArray<Map<String, byte[]>> mKeySnapshots = new SparseArray<>();
    private final Set<PskProvider> mPskProviders = new HashSet<>();
    private final Object mLock = new Object();

    public TrustedDeviceProcessor(Context context,
            AssociationStore associationStore,
            TrustedDeviceStore trustedDeviceStore,
            CompanionTransportManager transportManager) {
        mContext = context;
        mAssociationStore = associationStore;
        mTrustedDeviceStore = trustedDeviceStore;
        mTransportManager = transportManager;

        mTransportManager.addListener(MESSAGE_REQUEST_TRUSTED_DEVICE, mOnMessageReceivedListener);
        mTransportManager.addListener(mOnTransportChangedListener);
    }

    /**
     * Register a PSK provider implementation that can vend pre-shared keys to be used to verify
     * the first connection with an associated device.
     * @param provider PSK provider impl
     */
    public void addPskProvider(@NonNull PskProvider provider) {
        mPskProviders.add(provider);
    }

    /**
     * Remove a registered PSK provider implementation that matches the provided name.
     * @param name Name of the provider to remove.
     */
    public void removePskProvider(@NonNull String name) {
        mPskProviders.removeIf(provider -> name.equals(provider.getProviderName()));
    }

    /**
     * Loads persisted keys for user.
     * @param userId User ID
     */
    public void loadKeysForUser(@UserIdInt int userId) {
        mTrustedDeviceStore.readSessionKeysForUser(userId);
        for (PskProvider provider : mPskProviders) {
            provider.load(userId);
        }
    }

    @NonNull
    private synchronized Map<String, byte[]> getAvailableKeys(int userId, int associationId) {
        // If keys were already fetched, just use that instead of querying the providers again.
        // DISCARD it afterward since each exchange queries it exactly TWICE.
        Map<String, byte[]> snapshot = mKeySnapshots.removeReturnOld(associationId);
        if (snapshot != null) {
            return snapshot;
        }

        // Otherwise fetch a fresh list and cache the snapshot for the next query.
        byte[] sessionKey = mTrustedDeviceStore.getSessionKey(userId, associationId);
        if (sessionKey != null) {
            return Map.of("UKEY2", sessionKey);
        }

        Map<String, byte[]> availableKeys = new HashMap<>();
        for (PskProvider provider : mPskProviders) {
            byte[] key = provider.getKey(userId, associationId);
            if (key != null) {
                availableKeys.put(provider.getProviderName(), key);
            }
        }
        mKeySnapshots.put(associationId, availableKeys);
        return availableKeys;
    }

    @NonNull
    private Map<String, byte[]> computeVerificationTokens(int associationId, boolean isSender) {
        int userId = mAssociationStore.getAssociationById(associationId).getUserId();
        String role;
        byte[] newKey;

        synchronized (mLock) {
            Transport transport = mCurrentSessions.get(associationId);

            // Get the current session key for the association.
            role = transport.getSessionRole();
            newKey = transport.getSessionKey();
            if (role == null || newKey == null) {
                Slog.w(TAG,
                        "Association id=[" + associationId + "] does not have an active session.");
                return Collections.emptyMap();
            }
        }

        // If computing MAC for the sender, then use the role as info; otherwise flip the role
        // to match the expected value as the receiver.
        // Note: RawTransports use "PARTICIPANT" as symmetric role.
        String info = isSender || "PARTICIPANT".equals(role)
                ? role
                : "INITIATOR".equals(role)
                        ? "RESPONDER"
                        : "INITIATOR";

        // Get previously verified session key to verify the current session key. Otherwise
        // fetch a list of pre-shared keys with their provider names.
        Map<String, byte[]> sessionKeys = getAvailableKeys(userId, associationId);
        Map<String, byte[]> sessionMacs = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : sessionKeys.entrySet()) {
            byte[] name = new byte[32];
            byte[] src = entry.getKey().getBytes(StandardCharsets.UTF_8);
            System.arraycopy(src, 0, name, 0, Math.min(src.length, 32));
            try {
                byte[] key = entry.getValue();
                byte[] prk = hkdfExtract(name, bitwiseOr(key, newKey)); // Use provider name as salt
                byte[] mac = hkdfExpand(prk, info.getBytes(StandardCharsets.US_ASCII), 32);
                sessionMacs.put(entry.getKey(), mac);
            } catch (Exception e) {
                Slog.w(TAG, "Failed to compute session MAC for " + entry.getKey(), e);
                return Collections.emptyMap();
            }
        }
        return Collections.unmodifiableMap(sessionMacs);
    }

    /** Handle incoming trusted devices verification requests. */
    private final IOnMessageReceivedListener mOnMessageReceivedListener =
            new IOnMessageReceivedListener.Stub() {
                @Override
                public void onMessageReceived(int associationId, byte[] data) {
                    Slog.d(TAG, "Processing trusted devices verification request from association"
                            + " id=[" + associationId + "]...");

                    Map<String, byte[]> macs = computeVerificationTokens(associationId, false);
                    boolean result = false;

                    // Chunk the received message into blocks of 32 bytes and check for any matches
                    for (String name : macs.keySet()) {
                        byte[] mac = macs.get(name);
                        for (int offset = 0; offset < data.length; offset += 32) {
                            boolean match = true;
                            for (int i = 0; i < 32; i++) {
                                if (mac[i] != data[offset + i]) {
                                    match = false;
                                    break;
                                }
                            }
                            if (match) {
                                if (Build.IS_DEBUGGABLE) {
                                    Slog.d(TAG, "Found matching MAC from " + name);
                                }
                                mTrustedDeviceStore.setRootOfTrust(associationId, name);
                                result = true;
                                break;
                            }
                        }
                    }
                    countDownVerificationLatch(associationId, result);
                }
            };

    /**
     * Handle new connections and disconnections.
     */
    private final IOnTransportsChangedListener mOnTransportChangedListener =
            new IOnTransportsChangedListener.Stub() {
                @Override
                @RequiresNoPermission
                public void onTransportsChanged(List<AssociationInfo> associations) {
                    synchronized (mLock) {
                        Set<Integer> oldAssociations = new HashSet<>();
                        for (int i = 0; i < mCurrentSessions.size(); i++) {
                            oldAssociations.add(mCurrentSessions.keyAt(i));
                        }
                        Set<Integer> newAssociations = CollectionUtils.map(
                                new HashSet<>(associations),
                                AssociationInfo::getId);

                        // Identify new connections
                        Set<Integer> attached = new HashSet<>(newAssociations);
                        attached.removeAll(oldAssociations);
                        attached.forEach(id -> handleAttachedTransport(id));

                        // Identify disconnected associations
                        Set<Integer> detached = new HashSet<>(oldAssociations);
                        detached.removeAll(newAssociations);
                        detached.forEach(id -> handleDetachedTransport(id));
                    }
                }
            };

    private void handleAttachedTransport(int associationId) {
        synchronized (mLock) {
            // Cache the newly created transports as sessions for easier access
            mCurrentSessions.put(associationId, mTransportManager.getTransport(associationId));

            // Each verification requires two-steps (in any order).
            //  1. Reception of ACK from remote device that it has received (and processed) our MAC
            //  2. Reception of remote MAC AND successfully verifying it locally
            mVerificationLatches.put(associationId, new CountDownLatch(2));
        }

        // Listen for successful connection events to fetch the session key and role.
        IOnTransportEventListener listener = new IOnTransportEventListener.Stub() {
            @Override
            @RequiresNoPermission
            public void onTransportEvent(int event) {
                mTransportManager.removeListener(associationId, this);

                if (event != Transport.SUCCESSFUL_CONNECTION) {
                    Slog.w(TAG, "Transport on association=[" + associationId + "] failed to "
                            + "connect successfully. Trusted device verification was not sent.");
                    return;
                }

                // Compute verification tokens and send them to the other device
                Map<String, byte[]> macs = computeVerificationTokens(associationId, true);
                ByteBuffer buffer = ByteBuffer.allocate(32 * macs.size());
                for (byte[] mac : macs.values()) {
                    buffer.put(mac);
                }
                mTransportManager.requestTrustedDeviceVerification(associationId, buffer.array())
                        .whenCompleteAsync((response, e) ->
                                countDownVerificationLatch(associationId, e == null))
                        .exceptionally(error -> {
                            Slog.e(TAG, "Encountered an error in processing the "
                                    + "verification result.", error);
                            return null;
                        });
            }
        };

        // If already connected, then successful connection event will be triggered immediately.
        mTransportManager.addListener(associationId, listener);
    }

    private void handleDetachedTransport(int associationId) {
        // Clean up cached transport and verification latches
        synchronized (mLock) {
            mCurrentSessions.remove(associationId);
            mVerificationLatches.remove(associationId);
        }
    }

    /* Indicates that one of the two required steps of trusted device verification was completed. */
    private void countDownVerificationLatch(int associationId, boolean success) {
        AssociationInfo association =
                mAssociationStore.getAssociationWithCallerChecks(associationId);
        int userId = association.getUserId();

        synchronized (mLock) {
            CountDownLatch latch = mVerificationLatches.get(associationId);
            if (latch == null) {
                // It's already failed one of the verifications
                return;
            }

            if (!success) {
                // If either step failed, then just abandon the procedure and forget session key.
                Slog.w(TAG, "Association id=[" + associationId + "] is not a trusted device.");
                mVerificationLatches.remove(associationId);
                mAssociationStore.updateAssociation(associationId,
                        a -> (new AssociationInfo.Builder(a))
                                .setTrusted(false)
                                .build());

                mTrustedDeviceStore.removeSessionKey(userId, associationId);
                return;
            }

            latch.countDown();
            if (latch.getCount() == 0) {
                // Successfully passed both verification steps and is now trusted.
                Slog.i(TAG, "Association id=[" + associationId + "] is a trusted device.");
                mVerificationLatches.remove(associationId);

                mAssociationStore.updateAssociation(associationId,
                        a -> (new AssociationInfo.Builder(a))
                                .setTrusted(true)
                                .build());
                Transport session = mCurrentSessions.get(associationId);

                // Store the key for session resumption
                byte[] sessionKey = session.getSessionKey();
                mTrustedDeviceStore.storeSessionKey(userId, associationId, sessionKey);
            }
        }
    }
}
