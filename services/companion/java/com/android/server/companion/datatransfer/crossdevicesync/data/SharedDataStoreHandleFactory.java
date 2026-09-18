/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.annotation.NonNull;

import com.android.server.companion.datatransfer.crossdevicesync.common.FrameworkStatsLogProxy;
import com.android.server.companion.datatransfer.crossdevicesync.data.storage.IStorage;
import com.android.server.companion.datatransfer.crossdevicesync.data.storage.StorageNamespaceDecorator;

import com.google.android.submerge.Converter;
import com.google.android.submerge.TimestampProvider;

/** A factory to create a {@link SharedDataStoreHandle}. */
public class SharedDataStoreHandleFactory implements SharedDataStoreHandle.Factory {
    private final DeviceNodeIdProvider mDeviceNodeIdProvider;
    private final IStorage mGlobalStorage;
    private final FrameworkStatsLogProxy mFrameworkStatsLogProxy;
    private final TimestampProvider mTimestampProvider;

    public SharedDataStoreHandleFactory(
            DeviceNodeIdProvider deviceNodeIdProvider,
            IStorage globalStorage,
            FrameworkStatsLogProxy frameworkStatsLogProxy,
            TimestampProvider timestampProvider) {
        mDeviceNodeIdProvider = deviceNodeIdProvider;
        mGlobalStorage = globalStorage;
        mFrameworkStatsLogProxy = frameworkStatsLogProxy;
        mTimestampProvider = timestampProvider;
    }

    /**
     * Creates a {@link SharedDataStoreHandle}
     *
     * @param name The name of the data store
     * @param converter used to serialize/deserialize the data
     * @param schemaProvider The {@link SchemaProvider} for the data store
     * @param <T> The type of the data stored in the data store.
     * @return A {@link SharedDataStoreHandle} for the given data store.
     */
    @Override
    @SuppressWarnings("unchecked")
    @NonNull
    public <T> SharedDataStoreHandle<T> create(
            @NonNull String name,
            @NonNull Converter<T> converter,
            @NonNull SchemaProvider<T> schemaProvider) {
        Object lock = new Object();
        IStorage storage = new StorageNamespaceDecorator(name, mGlobalStorage);
        SubmergeSchemaUpgradeHelper<T> schemaUpgradeHelper =
                new SubmergeSchemaUpgradeHelper<>(storage, converter, name);

        SharedDataStoreFactory<T> dataStoreFactory =
                dataStoreHandle ->
                        new SubmergeSharedDataStore<>(
                                name,
                                lock,
                                mDeviceNodeIdProvider,
                                storage,
                                mFrameworkStatsLogProxy,
                                mTimestampProvider,
                                converter,
                                schemaProvider,
                                schemaUpgradeHelper,
                                dataStoreHandle);

        return new DefaultSharedDataStoreHandle<>(
                name, lock, mDeviceNodeIdProvider, storage, dataStoreFactory);
    }
}
