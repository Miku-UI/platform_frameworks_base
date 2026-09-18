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

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.os.PersistableBundle;
import android.util.Base64;
import android.util.Slog;
import android.util.SparseArray;

import com.android.server.companion.utils.PersistableBundleStore;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * Manages disk storage for trusted devices session keys for Companion Device Manager.
 */
public class TrustedDeviceStore extends PersistableBundleStore {
    private static final String TAG = "CDM_TrustedDeviceStore";
    private static final String FILE_NAME = "cdm_trusted_devices";

    private static final String KEY = "key";
    private static final String ROOT_OF_TRUST = "root";

    private final @NonNull ConcurrentMap<Integer, SparseArray<byte[]>> mSessionKeys =
            new ConcurrentHashMap<>();
    private final @NonNull SparseArray<String> mRootsOfTrust = new SparseArray<>();

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected String getFileName() {
        return FILE_NAME;
    }

    public TrustedDeviceStore() {
        super(true);
    }

    /**
     * Get a session key for a given association id.
     */
    public byte[] getSessionKey(@UserIdInt int userId, int associationId) {
        final SparseArray<byte[]> userKeys = mSessionKeys.get(userId);
        if (userKeys == null) {
            return null;
        }
        return userKeys.get(associationId);
    }

    /**
     * Store a session key for a given association id.
     */
    public void storeSessionKey(@UserIdInt int userId, int associationId, byte[] sessionKey) {
        mSessionKeys.computeIfAbsent(userId, k -> new SparseArray<>())
                .put(associationId, sessionKey);
        writeSessionKeysForUser(userId);
    }

    /**
     * Fetches the root of trust for an association.
     */
    public String getRootOfTrust(int associationId) {
        return mRootsOfTrust.get(associationId);
    }

    /**
     * Sets the root of trust for an association. This data will not be written to storage until
     * the association successfully completes the session verification handshake.
     */
    public void setRootOfTrust(int associationId, String root) {
        mRootsOfTrust.set(associationId, root);
    }

    /**
     * Remove a session key for a given association id.
     */
    public void removeSessionKey(@UserIdInt int userId, int associationId) {
        final SparseArray<byte[]> userKeys = mSessionKeys.get(userId);
        if (userKeys == null || !userKeys.contains(associationId)) {
            return;
        }
        userKeys.remove(associationId);
        writeSessionKeysForUser(userId);
    }

    /**
     * Reads session keys for a given user from disk.
     */
    public void readSessionKeysForUser(@UserIdInt int userId) {
        Slog.i(TAG, "Reading trusted devices session keys for user " + userId + " from disk.");
        final SparseArray<byte[]> sessionKeys = new SparseArray<>();
        final PersistableBundle sessionKeysBundle = readData(userId);
        for (String id : sessionKeysBundle.keySet()) {
            int associationId = Integer.parseInt(id);
            PersistableBundle bundle = sessionKeysBundle.getPersistableBundle(id);
            if (bundle == null) {
                continue;
            }
            String encodedKey = bundle.getString(KEY);
            if (encodedKey == null) {
                continue;
            }
            sessionKeys.put(associationId, Base64.decode(encodedKey, Base64.NO_WRAP));
            String root = bundle.getString(ROOT_OF_TRUST);
            if (root != null) {
                mRootsOfTrust.put(associationId, root);
            }
        }
        mSessionKeys.put(userId, sessionKeys);
    }

    /**
     * Writes session keys for a given user to disk.
     */
    private void writeSessionKeysForUser(@UserIdInt int userId) {
        final SparseArray<byte[]> sessionKeys = mSessionKeys.get(userId);
        if (sessionKeys == null) {
            return;
        }

        Slog.i(TAG, "Writing trusted devices session keys for user " + userId + " to disk");

        PersistableBundle sessionKeysBundle = new PersistableBundle();
        for (int i = 0; i < sessionKeys.size(); i++) {
            int associationId = sessionKeys.keyAt(i);
            byte[] key = sessionKeys.valueAt(i);
            String root = mRootsOfTrust.get(associationId);
            PersistableBundle bundle = new PersistableBundle();
            bundle.putString(KEY, Base64.encodeToString(key, Base64.NO_WRAP));
            bundle.putString(ROOT_OF_TRUST, root);
            sessionKeysBundle.putPersistableBundle(Integer.toString(associationId), bundle);
        }
        writeData(userId, sessionKeysBundle);
    }
}
