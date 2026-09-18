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

import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.MutableDocument;
import com.android.server.companion.datatransfer.crossdevicesync.data.fake.FakeSharedDataStore;
import com.android.server.companion.datatransfer.crossdevicesync.data.fake.TestSharedDataStoreFactory;
import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase;

import java.util.List;

/** Base class for tests dependent on the shared storage component. */
public abstract class SharedDataStoreTestBase extends SyncServiceTestBase {
    public static final String DOC_ID = "test_doc";

    protected final String mDataStoreName = getClass().getSimpleName();
    protected SharedDataStore<String> mSharedDataStore;
    protected SubmergeSharedDataStore<String> mSubmergeSharedDataStore;
    protected FakeSharedDataStore<String> mFakeSharedDataStore;
    protected SharedDataStoreHandle<String> mSharedDataStoreHandle;
    protected TestSharedDataStoreFactory<String> mTestSharedDataStoreFactory;

    protected SharedDataStoreTestBase() {
        mFakeNetworkManager.init();
        initialize(getSchemaProvider());
    }

    @SuppressWarnings("DataFlowIssue")
    protected void initialize(SchemaProvider<String> schemaProvider) {
        var component =
                mSharedDataStoreHandleFactory.getComponent(
                        mDataStoreName, mStringConverter, schemaProvider);
        mSharedDataStoreHandle = component.getSharedDataStoreHandle();
        mTestSharedDataStoreFactory = component.getTestSharedDataStoreFactory();
        mSubmergeSharedDataStore = component.createSubmergeSharedDataStore();
        mFakeSharedDataStore = component.createFakeSharedDataStore();
        mSharedDataStore =
                useRealSharedDataStoreImpl() ? mSubmergeSharedDataStore : mFakeSharedDataStore;
    }

    @SuppressWarnings("DataFlowIssue")
    protected SharedDataStore<String> getNewSharedDataStore(String name) {
        var handle =
                mSharedDataStoreHandleFactory.create(name, mStringConverter, getSchemaProvider());
        return mSharedDataStoreHandleFactory.<String>getFactory(name).create(handle);
    }

    protected SchemaProvider<String> getSchemaProvider() {
        return new SchemaProvider<>() {
            @Override
            public List<DocumentSchemaInfo> getAllDocumentSchema() {
                return List.of();
            }

            @Override
            public void migrateDocument(MutableDocument<String> document) {}
        };
    }

    public SharedDataStore<?> getSharedDataStore() {
        return mSharedDataStore;
    }
}
