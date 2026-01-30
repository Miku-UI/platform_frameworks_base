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
package com.android.server.adb;

import android.content.Context;
import android.provider.Settings;

import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The AdbKeyStore controls two files:
 *
 * <ol>
 *   <li>adb_keys
 *   <li>adb_temp_keys.xml
 * </ol>
 *
 * <p>The ADB Daemon (adbd) reads <em>only</em> the adb_keys file for authorization. Public keys
 * from registered hosts are stored in adb_keys, one entry per line.
 *
 * <p>AdbKeyStore also keeps adb_temp_keys.xml, which is used for two things
 *
 * <ol>
 *   <li>Removing unused keys from the adb_keys file
 *   <li>Managing authorized WiFi access points for ADB over WiFi
 * </ol>
 *
 * This class is thread safe. All of the API methods are synchronized.
 */
class AdbKeyStore {
    private static final String TAG = AdbKeyStore.class.getSimpleName();

    private final Set<String> mSystemKeys;
    private AdbAuthorizationStore.Entries mAuthEntries;

    /**
     * Manages the list of keys that adbd always allows to connect, regardless of last
     * connection-time.
     *
     * <p>This list of keys along with #{mSystemKeys} represents the source of truth for adbd.
     */
    private final AdbdKeyStoreStorage mAdbKeyUser;

    /**
     * Manages the list of temporary keys, including their last connection time, and the list of
     * trusted networks.
     */
    private final AdbAuthorizationStore mAuthStore;

    private final Context mContext;
    private final AdbDebuggingManager.Ticker mTicker;

    private static final String SYSTEM_KEY_FILE = "/adb_keys";

    /**
     * Value returned by {@code getLastConnectionTime} when there is no previously saved connection
     * time for the specified key.
     */
    private static final long NO_PREVIOUS_CONNECTION = 0;

    /**
     * Create an AdbKeyStore instance.
     *
     * <p>Upon creation, we parse adb_temp_keys.xml to determine authorized WiFi APs and retrieve
     * the map of stored ADB keys and their last connected times. After that, we read the adb_keys,
     * and any keys that exist in that file that do not exist in the map are added to the map (for
     * backwards compatibility).
     */
    @VisibleForTesting
    AdbKeyStore(
            Context context,
            File tempKeysFile,
            File userKeyFile,
            AdbDebuggingManager.Ticker ticker) {
        mContext = context;
        mTicker = ticker;

        mAdbKeyUser = new AdbdKeyStoreStorage(userKeyFile);
        mAuthStore = new AdbAuthorizationStore(tempKeysFile);
        mAuthEntries = mAuthStore.load();

        // The system keystore handles keys pre-loaded into the read-only system partition at
        // /adb_keys. Unlike the user keystore (/data/misc/adb/adb_keys), these
        // system keys are considered permanently trusted, are not subject to expiration, and
        // cannot be modified by the user.
        AdbdKeyStoreStorage systemKeyStore = new AdbdKeyStoreStorage(new File(SYSTEM_KEY_FILE));
        mSystemKeys = systemKeyStore.loadKeys();
        copyUserKeysToTempAuthorizationStore();
    }

    synchronized void reloadKeyMap() {
        mAuthEntries = mAuthStore.load();
    }

    synchronized void addTrustedNetwork(String bssid) {
        mAuthEntries.trustedNetworks().add(bssid);
        persistKeyStore();
    }

    synchronized Set<String> getKeys() {
        return new HashSet<>(mAuthEntries.keys().keySet());
    }

    synchronized String findKeyFromFingerprint(String fingerprint) {
        for (Map.Entry<String, Long> entry : mAuthEntries.keys().entrySet()) {
            String f = AdbDebuggingManager.getFingerprints(entry.getKey());
            if (fingerprint.equals(f)) {
                return entry.getKey();
            }
        }
        return null;
    }

    synchronized void removeKey(String key) {
        if (mAuthEntries.keys().containsKey(key)) {
            mAuthEntries.keys().remove(key);
            persistKeyStore();
        }
    }

    /** Returns whether there are any 'always allowed' keys in the keystore. */
    synchronized boolean isEmpty() {
        return mAuthEntries.keys().isEmpty();
    }

    /**
     * Iterates through the keys in the keystore and removes any that are beyond the window within
     * which connections are automatically allowed without user interaction.
     */
    synchronized void updateKeyStore() {
        if (filterOutOldKeys()) {
            persistKeyStore();
        }
    }

    /**
     * Copies keys from the user key file to the temp authorization store. This ensures that keys
     * that were previously authorized before the introduction of the keystore are still authorized.
     */
    private void copyUserKeysToTempAuthorizationStore() {
        Set<String> keys = mAdbKeyUser.loadKeys();
        boolean mapUpdated = false;
        for (String key : keys) {
            if (!mAuthEntries.keys().containsKey(key)) {
                // if the keystore does not contain the key from the user key file then add
                // it to the Map with the current system time to prevent it from expiring
                // immediately if the user is actively using this key.
                mAuthEntries.keys().put(key, mTicker.currentTimeMillis());
                mapUpdated = true;
            }
        }
        if (mapUpdated) {
            persistKeyStore();
        }
    }

    /** Writes the key map to the key file. */
    synchronized void persistKeyStore() {
        // if there is nothing in the key map then ensure any keys left in the keystore files
        // are deleted as well.
        filterOutOldKeys();
        if (mAuthEntries.isEmpty()) {
            deleteKeyStore();
            return;
        }
        mAuthStore.save(mAuthEntries);
        mAdbKeyUser.saveKeys(mAuthEntries.keys().keySet());
    }

    private boolean filterOutOldKeys() {
        long allowedTime = getAllowedConnectionTime();
        if (allowedTime == 0) {
            return false;
        }
        boolean keysDeleted = false;
        long systemTime = mTicker.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> keyMapIterator =
                mAuthEntries.keys().entrySet().iterator();
        while (keyMapIterator.hasNext()) {
            Map.Entry<String, Long> keyEntry = keyMapIterator.next();
            long connectionTime = keyEntry.getValue();
            if (systemTime > (connectionTime + allowedTime)) {
                keyMapIterator.remove();
                keysDeleted = true;
            }
        }
        // if any keys were deleted then the key file should be rewritten with the active keys
        // to prevent authorizing a key that is now beyond the allowed window.
        if (keysDeleted) {
            mAdbKeyUser.saveKeys(mAuthEntries.keys().keySet());
        }
        return keysDeleted;
    }

    /**
     * Returns the time in ms that the next key will expire or -1 if there are no keys or the keys
     * will not expire.
     */
    synchronized long getNextExpirationTime() {
        long minExpiration = -1;
        long allowedTime = getAllowedConnectionTime();
        // if the allowedTime is 0 then keys never expire; return -1 to indicate this
        if (allowedTime == 0) {
            return minExpiration;
        }
        long systemTime = mTicker.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> keyMapIterator =
                mAuthEntries.keys().entrySet().iterator();
        while (keyMapIterator.hasNext()) {
            Map.Entry<String, Long> keyEntry = keyMapIterator.next();
            long connectionTime = keyEntry.getValue();
            // if the key has already expired then ensure that the result is set to 0 so that
            // any scheduled jobs to clean up the keystore can run right away.
            long keyExpiration = Math.max(0, (connectionTime + allowedTime) - systemTime);
            if (minExpiration == -1 || keyExpiration < minExpiration) {
                minExpiration = keyExpiration;
            }
        }
        return minExpiration;
    }

    /** Removes all of the entries in the key map and deletes the key file. */
    synchronized void deleteKeyStore() {
        mAuthEntries.clear();
        mAuthStore.delete();
        mAdbKeyUser.delete();
    }

    /**
     * Returns the time of the last connection from the specified key, or {@code
     * NO_PREVIOUS_CONNECTION} if the specified key does not have an active adb grant.
     */
    synchronized long getLastConnectionTime(String key) {
        return mAuthEntries.keys().getOrDefault(key, NO_PREVIOUS_CONNECTION);
    }

    /** Sets the time of the last connection for the specified key to the provided time. */
    synchronized void setLastConnectionTime(String key, long connectionTime) {
        setLastConnectionTime(key, connectionTime, false);
    }

    /**
     * Sets the time of the last connection for the specified key to the provided time. If force is
     * set to true the time will be set even if it is older than the previously written connection
     * time.
     */
    @VisibleForTesting
    synchronized void setLastConnectionTime(String key, long connectionTime, boolean force) {
        // Do not set the connection time to a value that is earlier than what was previously
        // stored as the last connection time unless force is set.
        if (mAuthEntries.keys().containsKey(key)
                && mAuthEntries.keys().get(key) >= connectionTime
                && !force) {
            return;
        }
        // System keys are always allowed so there's no need to keep track of their connection
        // time.
        if (mSystemKeys.contains(key)) {
            return;
        }
        mAuthEntries.keys().put(key, connectionTime);
    }

    /**
     * Returns the connection time within which a connection from an allowed key is automatically
     * allowed without user interaction.
     */
    synchronized long getAllowedConnectionTime() {
        return Settings.Global.getLong(
                mContext.getContentResolver(),
                Settings.Global.ADB_ALLOWED_CONNECTION_TIME,
                Settings.Global.DEFAULT_ADB_ALLOWED_CONNECTION_TIME);
    }

    /**
     * Returns whether the specified key should be authorized to connect without user interaction.
     * This requires that the user previously connected this device and selected the option to
     * 'Always allow', and the time since the last connection is within the allowed window.
     */
    synchronized boolean isKeyAuthorized(String key) {
        // A system key is always authorized to connect.
        if (mSystemKeys.contains(key)) {
            return true;
        }
        long lastConnectionTime = getLastConnectionTime(key);
        if (lastConnectionTime == NO_PREVIOUS_CONNECTION) {
            return false;
        }
        long allowedConnectionTime = getAllowedConnectionTime();
        // if the allowed connection time is 0 then revert to the previous behavior of always
        // allowing previously granted adb grants.
        return allowedConnectionTime == 0
                || (mTicker.currentTimeMillis() < (lastConnectionTime + allowedConnectionTime));
    }

    /**
     * Returns whether the specified bssid is in the list of trusted networks. This requires that
     * the user previously allowed wireless debugging on this network and selected the option to
     * 'Always allow'.
     */
    synchronized boolean isTrustedNetwork(String bssid) {
        return mAuthEntries.trustedNetworks().contains(bssid);
    }
}
