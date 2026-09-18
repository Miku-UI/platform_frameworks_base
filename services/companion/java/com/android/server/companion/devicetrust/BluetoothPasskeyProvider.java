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

import static com.android.server.companion.utils.AssociationUtils.getMacAddress;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.bluetooth.BluetoothDevice;
import android.companion.AssociationInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.MacAddress;
import android.os.PersistableBundle;
import android.util.Slog;

import com.android.internal.util.CollectionUtils;
import com.android.server.companion.association.AssociationStore;
import com.android.server.companion.utils.PersistableBundleStore;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A plugin that uses bluetooth pairing PIN as the preshared keys for associated devices.
 */
public class BluetoothPasskeyProvider implements PskProvider {
    private static final String TAG = "CDM_BtPasskeyProvider";
    private static final String NAME = "BT_PASSKEY";
    private static final String FILE_NAME = "cdm_bt_keys";

    private final AssociationStore mAssociationStore;
    private final Map<MacAddress, Passkey> mPassKeys = new ConcurrentHashMap<>();
    private final PersistableBundleStore mKeyStore = new PersistableBundleStore(true) {
        @Override
        protected String getTag() {
            return TAG;
        }

        @Override
        protected String getFileName() {
            return FILE_NAME;
        }
    };

    private final Object mLock = new Object();

    public BluetoothPasskeyProvider(Context context,
            AssociationStore associationStore) {
        mAssociationStore = associationStore;

        // Listen for BT pairing event broadcast to cache passkeys to be used as PSK
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onBluetoothPair(intent);
            }
        }, new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST));

        // Listen for new associations and updates to filter for passkeys to persist
        mAssociationStore.registerLocalListener(new AssociationStore.OnChangeListener() {
            @Override
            public void onAssociationAdded(AssociationInfo association) {
                persistIfAddressExists(association);
            }

            @Override
            public void onAssociationUpdated(AssociationInfo association, boolean addressChanged) {
                if (addressChanged) {
                    persistIfAddressExists(association);
                }
            }

            private void persistIfAddressExists(AssociationInfo association) {
                MacAddress address = getMacAddress(association);
                if (address != null && mPassKeys.containsKey(address)) {
                    persistKeysForUser(association.getUserId());
                }
            }
        });
    }

    @Override
    @NonNull
    public String getProviderName() {
        return NAME;
    }

    @Override
    @Nullable
    public byte[] getKey(@UserIdInt int userId, int associationId) {
        AssociationInfo association = mAssociationStore.getAssociationById(associationId);
        MacAddress address = getMacAddress(association);
        if (address == null) {
            return null;
        }

        Passkey passkey = mPassKeys.get(address);
        if (passkey == null || passkey.isExpired()) {
            mPassKeys.remove(address);
            return null;
        }

        // Update the disk to ensure the usage is recorded
        byte[] key;
        synchronized (mLock) {
            key = passkey.getKey();
            persistKeysForUser(userId);
        }

        return key;
    }

    @Override
    public void load(@UserIdInt int userId) {
        loadKeysForUser(userId);
    }

    private void onBluetoothPair(Intent intent) {
        // Get the BluetoothDevice and pairing variant from the intent
        BluetoothDevice device = intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice.class);
        MacAddress address = MacAddress.fromString(device.getAddress());
        int pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1);
        int pairingKey = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1);
        int algorithm = com.android.bluetooth.flags.Flags.providePairingAlgo()
                ? intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_ALGORITHM, -1)
                : -1;

        if (!isAcceptablePairingVariant(pairingVariant, algorithm)) {
            Slog.w(TAG, "Pairing variant for device: " + device.getAnonymizedAddress()
                    + " cannot be used for trusted devices verification.");
            return;
        }

        if (pairingKey == -1) {
            Slog.w(TAG, "Pairing key for device: " + device.getAnonymizedAddress()
                    + " was not provided");
            return;
        }

        // Cache the pairing key
        Passkey key = new Passkey(pairingKey);
        mPassKeys.put(address, key);

        // Persist to disk if at least one association exists for this device
        Set<AssociationInfo> associations = new HashSet<>(mAssociationStore
                .getActiveAssociationsByAddress(device.getAddress()));
        Set<Integer> userIds = CollectionUtils.map(associations, AssociationInfo::getUserId);
        for (int userId : userIds) {
            persistKeysForUser(userId);
        }
    }

    private boolean isAcceptablePairingVariant(int pairingVariant, int algorithm) {
        // If the pairing algorithm flag is not enabled, only allow the restrictive pairing variants
        if (algorithm == -1) {
            return pairingVariant == BluetoothDevice.PAIRING_VARIANT_PASSKEY
                    || pairingVariant == BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION
                    || pairingVariant == BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY;
        }

        if (pairingVariant == BluetoothDevice.PAIRING_VARIANT_CONSENT) {
            return false;
        }

        if (pairingVariant == BluetoothDevice.PAIRING_VARIANT_OOB_CONSENT) {
            return true;
        }

        return switch (algorithm) {
            case BluetoothDevice.PAIRING_ALGORITHM_BREDR_SSP,
                 BluetoothDevice.PAIRING_ALGORITHM_SC -> true;
            case BluetoothDevice.PAIRING_ALGORITHM_LE_LEGACY,
                 BluetoothDevice.PAIRING_ALGORITHM_BREDR_LEGACY -> false;
            default -> false;
        };
    }

    private synchronized void persistKeysForUser(@UserIdInt int userId) {
        PersistableBundle keysToPersist = new PersistableBundle();
        for (AssociationInfo association : mAssociationStore.getActiveAssociationsByUser(userId)) {
            MacAddress address = getMacAddress(association);
            if (address == null) {
                continue;
            }
            Passkey passkey = mPassKeys.get(address);
            if (passkey != null && !passkey.isExpired()) {
                keysToPersist.putPersistableBundle(address.toString(),
                        passkey.asPersistableBundle());
            }
        }
        mKeyStore.writeData(userId, keysToPersist);
    }

    private synchronized void loadKeysForUser(@UserIdInt int userId) {
        PersistableBundle persistedKeys = mKeyStore.readData(userId);
        for (String address : persistedKeys.keySet()) {
            PersistableBundle passkeyBundle = persistedKeys.getPersistableBundle(address);
            if (passkeyBundle == null) {
                continue;
            }
            Passkey passkey = Passkey.fromPersistableBundle(passkeyBundle);
            if (!passkey.isExpired()) {
                mPassKeys.put(MacAddress.fromString(address), passkey);
            }
        }
    }

    private static final class Passkey {
        // If a passkey has been used 3 times, discard the passkey to prevent brute-force attack
        private static final int MAX_PASSKEY_VEND_COUNT = 3;
        private static final long EXPIRATION_IN_DAYS = 7; // Expire the key after a week
        private static final String KEY = "key";
        private static final String REMAINING = "remaining";
        private static final String EXPIRATION = "expires";

        private final int mKey;
        private final long mExpires;
        private int mRemainingAccesses;

        Passkey(int pairingKey) {
            this(pairingKey, MAX_PASSKEY_VEND_COUNT,
                    System.currentTimeMillis() + TimeUnit.DAYS.toMillis(EXPIRATION_IN_DAYS));
        }

        private Passkey(int key, int remaining, long expires) {
            this.mKey = key;
            this.mRemainingAccesses = remaining;
            this.mExpires = expires;
        }

        @Nullable
        public byte[] getKey() {
            if (isExpired()) {
                return null;
            }

            // Each read uses up the allocated allowance of access
            mRemainingAccesses--;
            return ByteBuffer.allocate(4)
                    .putInt(mKey)
                    .array();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > mExpires || mRemainingAccesses <= 0;
        }

        public PersistableBundle asPersistableBundle() {
            PersistableBundle bundle = new PersistableBundle();
            bundle.putInt(KEY, mKey);
            bundle.putInt(REMAINING, mRemainingAccesses);
            bundle.putLong(EXPIRATION, mExpires);
            return bundle;
        }

        public static Passkey fromPersistableBundle(PersistableBundle bundle) {
            int key = bundle.getInt(KEY);
            int remaining = bundle.getInt(REMAINING);
            long expires = bundle.getLong(EXPIRATION);
            return new Passkey(key, remaining, expires);
        }
    }
}
