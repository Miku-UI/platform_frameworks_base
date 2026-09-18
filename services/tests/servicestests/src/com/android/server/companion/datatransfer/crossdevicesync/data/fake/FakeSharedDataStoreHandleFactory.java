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

package com.android.server.companion.datatransfer.crossdevicesync.data.fake;

import android.annotation.NonNull;

import androidx.annotation.Nullable;

import com.android.server.companion.datatransfer.crossdevicesync.common.FrameworkStatsLogProxy;
import com.android.server.companion.datatransfer.crossdevicesync.data.DefaultSharedDataStoreHandle;
import com.android.server.companion.datatransfer.crossdevicesync.data.DeviceNodeIdProvider;
import com.android.server.companion.datatransfer.crossdevicesync.data.SchemaProvider;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStoreFactory;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStoreHandle;
import com.android.server.companion.datatransfer.crossdevicesync.data.SubmergeSchemaUpgradeHelper;
import com.android.server.companion.datatransfer.crossdevicesync.data.SubmergeSharedDataStore;
import com.android.server.companion.datatransfer.crossdevicesync.data.storage.IStorage;

import com.google.android.submerge.Converter;
import com.google.android.submerge.TimestampProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** A fake factory to create {@link SharedDataStoreHandle} for tests. */
public class FakeSharedDataStoreHandleFactory implements SharedDataStoreHandle.Factory {
    private final DeviceNodeIdProvider mDeviceNodeIdProvider;
    private final IStorage mGlobalStorage;
    private final FrameworkStatsLogProxy mFrameworkStatsLogProxy;
    private final TimestampProvider mTimestampProvider;

    private final Supplier<Boolean> mUseRealSharedDataStoreImpl;
    private final Supplier<Boolean> mUseRealSharedDataStoreHandleImpl;

    private final Map<String, TestSharedDataStoreFactory<?>> mFactories = new HashMap<>();

    public FakeSharedDataStoreHandleFactory(
            DeviceNodeIdProvider deviceNodeIdProvider,
            IStorage globalStorage,
            FrameworkStatsLogProxy frameworkStatsLogProxy,
            TimestampProvider timestampProvider,
            Supplier<Boolean> useRealSharedDataStoreImpl,
            Supplier<Boolean> useRealSharedDataStoreHandleImpl) {
        mDeviceNodeIdProvider = deviceNodeIdProvider;
        mGlobalStorage = globalStorage;
        mFrameworkStatsLogProxy = frameworkStatsLogProxy;
        mTimestampProvider = timestampProvider;
        mUseRealSharedDataStoreImpl = useRealSharedDataStoreImpl;
        mUseRealSharedDataStoreHandleImpl = useRealSharedDataStoreHandleImpl;
    }

    /** Returns the {@link TestSharedDataStoreFactory} for the given data store name. */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> TestSharedDataStoreFactory<T> getFactory(String name) {
        return (TestSharedDataStoreFactory<T>) mFactories.get(name);
    }

    @NonNull
    @Override
    public <T> SharedDataStoreHandle<T> create(
            @NonNull String name,
            @NonNull Converter<T> converter,
            @NonNull SchemaProvider<T> schemaProvider) {
        return getComponent(name, converter, schemaProvider).getSharedDataStoreHandle();
    }

    /** Creates a dagger-like component for a SharedDataStore. */
    public <T> SharedDataStoreComponent<T> getComponent(
            @NonNull String name,
            @NonNull Converter<T> converter,
            @NonNull SchemaProvider<T> schemaProvider) {
        return new SharedDataStoreComponent<>(name, converter, schemaProvider);
    }

    /** A dagger-like component for a SharedDataStore. */
    public class SharedDataStoreComponent<T> {
        private final Object mLock;
        private final String mName;
        private final Converter<T> mConverter;
        private final SchemaProvider<T> mSchemaProvider;
        private final SharedDataStoreHandle<T> mHandle;
        private final TestSharedDataStoreFactory<T> mFactory;

        private SharedDataStoreComponent(
                @NonNull String name,
                @NonNull Converter<T> converter,
                @NonNull SchemaProvider<T> schemaProvider) {
            mLock = new Object();
            mName = name;
            mConverter = converter;
            mSchemaProvider = schemaProvider;
            mFactory = new TestSharedDataStoreFactory<>(getDataStoreFactory());
            mFactories.put(name, mFactory);
            mHandle =
                    mUseRealSharedDataStoreHandleImpl.get()
                            ? new DefaultSharedDataStoreHandle<>(
                                    name, mLock, mDeviceNodeIdProvider, mGlobalStorage, mFactory)
                            : new FakeSharedDataStoreHandle<>(
                                    name, mDeviceNodeIdProvider, mGlobalStorage, mFactory);
        }

        /** Creates a {@link SubmergeSharedDataStore} */
        public SubmergeSharedDataStore<T> createSubmergeSharedDataStore() {
            return new SubmergeSharedDataStore<>(
                    mName,
                    mLock,
                    mDeviceNodeIdProvider,
                    mGlobalStorage,
                    mFrameworkStatsLogProxy,
                    mTimestampProvider,
                    mConverter,
                    mSchemaProvider,
                    new SubmergeSchemaUpgradeHelper<>(mGlobalStorage, mConverter, mName),
                    mHandle);
        }

        /** Creates a {@link FakeSharedDataStore}. */
        public FakeSharedDataStore<T> createFakeSharedDataStore() {
            return new FakeSharedDataStore<>(
                    mName,
                    mDeviceNodeIdProvider,
                    mSchemaProvider,
                    mGlobalStorage,
                    mHandle,
                    mConverter);
        }

        public SharedDataStoreHandle<T> getSharedDataStoreHandle() {
            return mHandle;
        }

        public TestSharedDataStoreFactory<T> getTestSharedDataStoreFactory() {
            return mFactory;
        }

        public SharedDataStoreFactory<T> getDataStoreFactory() {
            return handle -> {
                if (mUseRealSharedDataStoreImpl.get()) {
                    return createSubmergeSharedDataStore();
                } else {
                    return createFakeSharedDataStore();
                }
            };
        }
    }
}
