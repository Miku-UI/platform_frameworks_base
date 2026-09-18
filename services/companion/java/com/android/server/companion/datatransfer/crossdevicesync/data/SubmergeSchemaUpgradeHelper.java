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

import android.annotation.Nullable;

import com.android.server.companion.datatransfer.crossdevicesync.data.model.DocumentMetadata;
import com.android.server.companion.datatransfer.crossdevicesync.data.storage.IStorage;

import com.google.android.submerge.Converter;
import com.google.android.submerge.DataStore;
import com.google.android.submerge.NetworkInterface;
import com.google.android.submerge.StorageInterface;
import com.google.android.submerge.TimestampProvider;
import com.google.android.submerge.TransactionException;
import com.google.errorprone.annotations.MustBeClosed;

import org.checkerframework.checker.signedness.qual.Unsigned;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Helper class for upgrading the document schema.
 *
 * <p>Document schema are stored in a separate submerge data store attributed to a "virtual node",
 * which can be considered as a "virtual device" in the sync network. Any schema change is initiated
 * in the virtual node, then merged to the "real" submerge document. This setup ensures concurrent
 * schema change across different devices gets correctly merged and prevent data loss caused by
 * schema override.
 *
 * @param <T> the type of the document.
 */
public class SubmergeSchemaUpgradeHelper<T> {
    /**
     * Prefix for a virtual node ID used for schema changes.
     *
     * <p>Submerge uses a "last change wins" strategy for concurrent updates. If multiple devices
     * update the schema concurrently, the one with the latest timestamp overwrites the others,
     * potentially causing data loss.
     *
     * <p>To avoid this, schema changes are attributed to a virtual node ID like "schema_node_xxx"
     * (where "xxx" is the document ID). The timestamp for this virtual node is the schema version.
     * This makes all schema updates appear to come from the same virtual node with the same
     * timestamp, allowing Submerge to merge changes correctly instead of overwriting the entire
     * subtree.
     *
     * <p>Warning: this should never change, or cross-device concurrent schema change will lead to
     * data loss.
     */
    private static final String SCHEMA_NODE_ID_PREFIX = "schema_node_";

    private final IStorage mStorage;
    private final Converter<T> mConverter;
    private final String mDataStoreName;
    private final StorageInterface mSchemaStorageInterface =
            new StorageInterface() {
                @Override
                public void onNewUpdate(String docId, byte[] serializedSchema)
                        throws StorageException {
                    mStorage.persistDocumentSchema(docId, serializedSchema);
                }

                @Nullable
                @Override
                public byte[] readFromStorage(String docId) throws StorageException {
                    return mStorage.getDocumentSchema(docId);
                }
            };
    private final SchemaDataStoreTimestampProvider mTimestampProvider =
            new SchemaDataStoreTimestampProvider();

    public SubmergeSchemaUpgradeHelper(
            IStorage storage, Converter<T> converter, String dataStoreName) {
        mStorage = storage;
        mConverter = converter;
        mDataStoreName = dataStoreName;
    }

    /**
     * Upgrade the schema data store based on the latest schema info, and return a {@code byte[]}
     * that can be merged with the main data store to apply the schema change.
     *
     * @param schema the latest schema info.
     * @return the schema update message that can be merged into the real data store.
     * @throws IllegalSchemaChangeException if error happens when upgrading the schema.
     * @throws TransactionException if error happens when committing the schema change.
     */
    public byte[] upgradeDocumentSchema(DocumentSchemaInfo schema)
            throws IllegalSchemaChangeException, TransactionException, IOException {
        // Group paths by version.
        Map<Integer, List<PathSchemaInfo>> versionToPathSchema = new TreeMap<>();
        for (PathSchemaInfo pathSchema : schema.getPathSchema()) {
            List<PathSchemaInfo> schemaList =
                    versionToPathSchema.computeIfAbsent(
                            pathSchema.version(), k -> new ArrayList<>());
            schemaList.add(pathSchema);
        }

        try (DataStore<T> schemaDataStore = openSchemaDataStore(schema.getDocId())) {
            // Perform upgrade iteratively from lower version to higher version.
            for (Map.Entry<Integer, List<PathSchemaInfo>> entry : versionToPathSchema.entrySet()) {
                int version = entry.getKey();
                List<PathSchemaInfo> pathSchemaInfos = entry.getValue();
                mTimestampProvider.setCurrentSchemaVersion(version);
                try (SubmergeDocument<T> schemaDoc =
                        openSchemaDoc(schemaDataStore, schema.getDocId())) {
                    for (PathSchemaInfo pathSchema : pathSchemaInfos) {
                        String path = pathSchema.path();
                        int type = pathSchema.type();
                        switch (type) {
                            case RecordType.TYPE_REGISTER -> schemaDoc.addRegisterSchema(path);
                            case RecordType.TYPE_UNMERGED -> schemaDoc.addUnmergedSchema(path);
                            case RecordType.TYPE_SET -> schemaDoc.addSetSchema(path);
                            default ->
                                    throw new IllegalSchemaChangeException(
                                            "update: unknown data type " + type);
                        }
                    }
                    // Commit the schema change.
                    schemaDoc.commitTransaction();
                }
            }
            return schemaDataStore.getFullUpdateMessage(schema.getDocId());
        }
    }

    /** Get the node id used for creating a schema data store. */
    private static String getSchemaNodeId(String docId) {
        return SCHEMA_NODE_ID_PREFIX + docId;
    }

    /** Open the submerge schema data store. */
    @MustBeClosed
    private DataStore<T> openSchemaDataStore(String docId) {
        // Create a submerge data store representing a virtual device for updating schema. The
        // virtual device's node id is always SCHEMA_NODE_ID_PREFIX + docId. Its timestamp is always
        // the schemaVersion.
        return new DataStore<>(
                getSchemaNodeId(docId),
                new NoOpNetworkInterface(),
                mSchemaStorageInterface,
                mTimestampProvider,
                mConverter);
    }

    /** Open a schema doc. */
    @SuppressWarnings("MustBeClosedChecker")
    @MustBeClosed
    private SubmergeDocument<T> openSchemaDoc(DataStore<T> schemaDataStore, String docId) {
        return new SubmergeDocument<>(
                mDataStoreName,
                docId,
                schemaDataStore.newDocumentTransaction(docId),
                getSchemaNodeId(docId),
                new DocumentMetadata(0, new ArrayList<>(), new byte[0]));
    }

    private static class NoOpNetworkInterface implements NetworkInterface {
        @Override
        public void onNewUpdate(String docId, byte[] updateMessage) {
            // Do nothing.
        }
    }

    /**
     * Schema datastore's timestamp provider.
     *
     * <p>Schema datastore's timestamp must match the schema version, so that concurrent schema
     * upgrade is considered happening at the "same time" when submerge attempts to merge changes.
     */
    private static class SchemaDataStoreTimestampProvider implements TimestampProvider {

        private long mCurrentSchemaVersion;

        @Override
        public @Unsigned long now() {
            return mCurrentSchemaVersion;
        }

        public void setCurrentSchemaVersion(int version) {
            mCurrentSchemaVersion = version;
        }
    }
}
