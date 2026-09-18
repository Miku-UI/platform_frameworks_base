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
package com.android.server.companion.datatransfer.crossdevicesync.data.fake;

import com.android.server.companion.datatransfer.crossdevicesync.data.DeviceNodeIdProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Fake implementation of {@link DeviceNodeIdProvider} for testing. */
public class FakeDeviceNodeIdProvider implements DeviceNodeIdProvider {
    private final Map<String, String> mNodeIds = new HashMap<>();
    private final Set<String> mTombStones = new HashSet<>();

    public FakeDeviceNodeIdProvider() {}

    @Override
    public String getOrCreateNodeIdForDataStore(String dataStore) {
        return mNodeIds.computeIfAbsent(
                dataStore,
                k -> {
                    for (int i = 0; i < 10; i++) {
                        String id = UUID.randomUUID().toString();
                        if (!mNodeIds.containsValue(id) && !mTombStones.contains(id)) {
                            return id;
                        }
                    }
                    throw new RuntimeException();
                });
    }

    @Override
    public void noteDataStoreDeletion(String dataStore) {
        String id = mNodeIds.remove(dataStore);
        if (id != null) {
            mTombStones.add(id);
        }
    }
}
