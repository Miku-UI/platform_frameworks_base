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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class DeviceNodeIdProviderImplTest extends SyncServiceTestBase {
    private static final String TEST_FILE = "test_prefs";

    private SharedPreferences mSharedPreferences;
    private DeviceNodeIdProviderImpl mDeviceNodeIdProvider;

    @Before
    public void setUp() {
        mSharedPreferences = mContext.getSharedPreferences(TEST_FILE, Context.MODE_PRIVATE);
        mDeviceNodeIdProvider = new DeviceNodeIdProviderImpl(mSharedPreferences);
    }

    @After
    public void tearDown() {
        mSharedPreferences.edit().clear().apply();
    }

    @Test
    public void getOrCreateNodeIdForDataStore_uniqueId() {
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            assertThat(
                            ids.add(
                                    mDeviceNodeIdProvider.getOrCreateNodeIdForDataStore(
                                            String.valueOf(i))))
                    .isTrue();
        }
    }

    @Test
    public void getOrCreateNodeIdForDataStore_reuseIdForSameDataStore() {
        Map<String, String> nameToId = new HashMap<>();

        for (int i = 0; i < 1000; i++) {
            String name = String.valueOf(i);
            assertThat(
                            nameToId.put(
                                    name,
                                    mDeviceNodeIdProvider.getOrCreateNodeIdForDataStore(name)))
                    .isNull();
        }

        for (int i = 0; i < 1000; i++) {
            String name = String.valueOf(i);
            assertThat(nameToId.get(name))
                    .isEqualTo(mDeviceNodeIdProvider.getOrCreateNodeIdForDataStore(name));
        }
    }

    @Test
    public void noteDataStoreDeletion_idNotReused() {
        Map<String, String> nameToId = new HashMap<>();

        for (int i = 0; i < 1000; i++) {
            String name = String.valueOf(i);
            assertThat(
                            nameToId.put(
                                    name,
                                    mDeviceNodeIdProvider.getOrCreateNodeIdForDataStore(name)))
                    .isNull();
            mDeviceNodeIdProvider.noteDataStoreDeletion(name);
        }

        for (int i = 0; i < 1000; i++) {
            String name = String.valueOf(i);
            assertThat(nameToId.get(name))
                    .isNotEqualTo(mDeviceNodeIdProvider.getOrCreateNodeIdForDataStore(name));
        }
    }
}
