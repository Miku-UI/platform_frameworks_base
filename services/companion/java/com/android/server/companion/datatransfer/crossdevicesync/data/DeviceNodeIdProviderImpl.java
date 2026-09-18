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
package com.android.server.companion.datatransfer.crossdevicesync.data;

import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of {@link DeviceNodeIdProvider} that generates a random UUID and reuse the
 * same id afterwards.
 */
public class DeviceNodeIdProviderImpl implements DeviceNodeIdProvider {
    /** Prefix of keys that store the node id of a data store. */
    private static final String KEY_NODE_ID_PREFIX = "node_id_";

    /** Key that stores the unavailable node ids. */
    private static final String KEY_NODE_ID_TOMBSTONES = "tombstone_node_ids";

    private static final int MAX_ATTEMPT_FOR_NEW_ID = 10;
    private final SharedPreferences mSharedPreferences;

    public DeviceNodeIdProviderImpl(SharedPreferences sharedPreferences) {
        mSharedPreferences = sharedPreferences;
    }

    @Override
    public String getOrCreateNodeIdForDataStore(String dataStore) {
        String key = KEY_NODE_ID_PREFIX + dataStore;
        String id = mSharedPreferences.getString(key, "");
        if (TextUtils.isEmpty(id)) {
            id = newNodeIdForDataStore(dataStore);
            mSharedPreferences.edit().putString(key, id).apply();
        }
        return id;
    }

    private String newNodeIdForDataStore(String dataStore) {
        Set<String> unavailableIds = new HashSet<>();
        for (String key : mSharedPreferences.getAll().keySet()) {
            if (key.startsWith(KEY_NODE_ID_PREFIX)) {
                unavailableIds.add(mSharedPreferences.getString(key, ""));
            }
        }
        Set<String> tombstones = mSharedPreferences.getStringSet(KEY_NODE_ID_TOMBSTONES, null);
        if (tombstones != null) {
            unavailableIds.addAll(tombstones);
        }
        for (int i = 0; i < MAX_ATTEMPT_FOR_NEW_ID; i++) {
            String id = UUID.randomUUID().toString();
            if (!unavailableIds.contains(id)) {
                return id;
            }
        }
        throw new RuntimeException(
                "Unable to generate a new node id for "
                        + dataStore
                        + " after "
                        + MAX_ATTEMPT_FOR_NEW_ID
                        + " attempts!");
    }

    @Override
    public void noteDataStoreDeletion(String dataStore) {
        String key = KEY_NODE_ID_PREFIX + dataStore;
        String id = mSharedPreferences.getString(key, "");
        if (TextUtils.isEmpty(id)) {
            return;
        }
        Set<String> tombstones = mSharedPreferences.getStringSet(KEY_NODE_ID_TOMBSTONES, null);
        Set<String> mutableTombstones = new HashSet<>();
        if (tombstones != null) {
            mutableTombstones.addAll(tombstones);
        }
        mutableTombstones.add(id);
        mSharedPreferences
                .edit()
                .remove(key)
                .putStringSet(KEY_NODE_ID_TOMBSTONES, mutableTombstones)
                .apply();
    }
}
