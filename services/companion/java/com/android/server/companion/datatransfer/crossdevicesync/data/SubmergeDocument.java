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

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FunctionalUtils.ThrowingBiConsumer;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;
import com.android.internal.util.FunctionalUtils.ThrowingSupplier;
import com.android.server.companion.datatransfer.crossdevicesync.common.DebugConfig;
import com.android.server.companion.datatransfer.crossdevicesync.common.SmartAutoClosable;
import com.android.server.companion.datatransfer.crossdevicesync.data.model.DocumentMetadata;
import com.android.server.companion.datatransfer.crossdevicesync.data.model.DocumentMetadata.RecordMetadata;

import com.google.android.submerge.DocumentTransaction;
import com.google.android.submerge.MergeException;
import com.google.android.submerge.OlderBaseNeededException;
import com.google.android.submerge.SubmergeDataType;
import com.google.android.submerge.SubmergeMap;
import com.google.android.submerge.SubmergeRegister;
import com.google.android.submerge.SubmergeSet;
import com.google.android.submerge.SubmergeVectorData;
import com.google.android.submerge.TimestampOverflowException;
import com.google.android.submerge.TransactionException;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * An implementation of the {@link SharedDataStore.MutableDocument} using submerge {@link
 * DocumentTransaction}.
 *
 * @param <T> the data type that this document manages.
 */
class SubmergeDocument<T> implements SharedDataStore.MutableDocument<T>, AutoCloseable {
    private static final String TAG = "SubmergeDocument";
    private static final boolean DEBUG = DebugConfig.DEBUG_DATA_STORE;

    private final String mDataStoreName;
    private final String mDocId;
    @VisibleForTesting final DocumentTransaction<T> mTransaction;
    private final String mLocalDeviceNodeId;
    private final Map<String, WeakReference<SubmergeRecord>> mSubmergeRecordRefs = new ArrayMap<>();
    private final Map<String, RecordMetadata> mRecordMetadataMap = new HashMap<>();
    private int mSchemaVersion;
    private volatile boolean mClosed;

    SubmergeDocument(
            String dataStoreName,
            String docId,
            DocumentTransaction<T> transaction,
            String localDeviceNodeId,
            DocumentMetadata docMetadata) {
        mDataStoreName = dataStoreName;
        mDocId = docId;
        mTransaction = transaction;
        mLocalDeviceNodeId = localDeviceNodeId;
        mSchemaVersion = docMetadata.schemaVersion();
        for (RecordMetadata metadata : docMetadata.recordMetadataList()) {
            mRecordMetadataMap.put(metadata.path(), metadata);
        }
    }

    @Override
    public String getDocId() {
        return mDocId;
    }

    @Override
    public int getSchemaVersion() {
        return mSchemaVersion;
    }

    void setSchemaVersion(int version) {
        requireDocumentOpen();
        mSchemaVersion = version;
    }

    @Nullable
    @Override
    public SharedDataStore.Record<T> getRecord(String path) {
        requireDocumentOpen();
        String normalizedPath = normalizedPath(path);
        SubmergeRecord record = getRecordFromCache(normalizedPath);
        if (record != null) {
            // Cache hit!
            return record.asSharedDataStoreRecord();
        }
        SubmergeDataType<T> node = findSubmergeNode(normalizedPath);
        if (node instanceof SubmergeRegister) {
            record = new SubmergeRegisterRecord((SubmergeRegister<T>) node, normalizedPath);
        } else if (node instanceof SubmergeSet) {
            record = new SubmergeSetRecord((SubmergeSet<T>) node, normalizedPath);
        } else if (node instanceof SubmergeVectorData<T>) {
            record = new SubmergeVectorRecord((SubmergeVectorData<T>) node, normalizedPath);
        } else if (node != null) {
            node.close();
        }
        if (record != null) {
            mSubmergeRecordRefs.put(normalizedPath, new WeakReference<>(record));
            return record.asSharedDataStoreRecord();
        }
        return null;
    }

    @Override
    public boolean containsPath(String path) {
        // This will create a cache which speeds up next query to the same path.
        return getRecord(path) != null;
    }

    @Override
    public void putData(String path, @Nullable T data) throws IllegalArgumentException {
        requireDocumentOpen();
        String normalizedPath = normalizedPath(path);
        findSubmergeNodeSafely(
                normalizedPath,
                node -> {
                    SubmergeRegister<T> r = (SubmergeRegister<T>) node;
                    boolean changed = !Objects.equals(r.get(), data);
                    r.set(data);
                    if (changed || mRecordMetadataMap.get(normalizedPath) == null) {
                        setPathLastModifiedByLocalDevice(normalizedPath, true);
                    }
                });
    }

    @Override
    public void putUnmergedData(String path, @Nullable T data) throws IllegalArgumentException {
        requireDocumentOpen();
        String normalizedPath = normalizedPath(path);
        findSubmergeNodeSafely(
                normalizedPath,
                node -> {
                    SubmergeVectorData<T> v = (SubmergeVectorData<T>) node;
                    boolean changed = v.set(data);
                    if (changed || mRecordMetadataMap.get(normalizedPath) == null) {
                        setPathLastModifiedByLocalDevice(normalizedPath, true);
                    }
                });
    }

    @Override
    public void addDataToSet(String path, T data) throws IllegalArgumentException {
        requireDocumentOpen();
        String normalizedPath = normalizedPath(path);
        findSubmergeNodeSafely(
                normalizedPath,
                node -> {
                    SubmergeSet<T> s = (SubmergeSet<T>) node;
                    boolean changed = s.add(data);
                    if (changed) {
                        setPathLastModifiedByLocalDevice(normalizedPath, true);
                    }
                });
    }

    @Override
    public void removeDataFromSet(String path, T data) throws IllegalArgumentException {
        requireDocumentOpen();
        String normalizedPath = normalizedPath(path);
        findSubmergeNodeSafely(
                normalizedPath,
                node -> {
                    SubmergeSet<T> s = (SubmergeSet<T>) node;
                    boolean changed = s.remove(data);
                    if (changed) {
                        setPathLastModifiedByLocalDevice(normalizedPath, true);
                    }
                });
    }

    @Override
    public void close() {
        if (mClosed) {
            return;
        }
        mClosed = true;
        // Close all records, then close transaction.
        try (DocumentTransaction<T> transaction = mTransaction) {
            mSubmergeRecordRefs
                    .values()
                    .forEach(
                            ref -> {
                                SubmergeRecord r = ref.get();
                                if (r != null) {
                                    r.close();
                                }
                            });
            mSubmergeRecordRefs.clear();
        }
    }

    void addSetSchema(String path) throws IllegalSchemaChangeException {
        addSchemaToPath(path, mTransaction::newSet, SubmergeSet.class);
    }

    void addRegisterSchema(String path) throws IllegalSchemaChangeException {
        addSchemaToPath(path, mTransaction::newRegister, SubmergeRegister.class);
    }

    void addUnmergedSchema(String path) throws IllegalSchemaChangeException {
        addSchemaToPath(path, mTransaction::newVectorData, SubmergeVectorData.class);
    }

    private void addSchemaToPath(
            String path,
            ThrowingSupplier<SubmergeDataType<T>> nodeCreator,
            Class<? extends SubmergeDataType> type)
            throws IllegalSchemaChangeException {
        requireDocumentOpen();
        String normalizedPath = normalizedPath(path);
        try (SubmergeDataType<T> node = findSubmergeNode(normalizedPath)) {
            if (type.isInstance(node)) {
                // Schema already exists. Nothing to add.
                return;
            }
        }
        try {
            findOrCreateLeafSafely(
                    normalizedPath,
                    nodeCreator,
                    node -> {
                        if (!type.isInstance(node)) {
                            throw new IllegalSchemaChangeException(
                                    "addSchemaToPath: path "
                                            + getDebugStringForPath(normalizedPath)
                                            + " is unavailable for adding schema.");
                        }
                        setPathLastModifiedByLocalDevice(normalizedPath, true);
                    });
        } catch (IllegalSchemaChangeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalSchemaChangeException(e);
        }
    }

    void removePath(String path) throws IllegalSchemaChangeException {
        requireDocumentOpen();
        String normalizedPath = normalizedPath(path);
        ArrayDeque<String> keys = normalizedKeys(normalizedPath);
        if (keys.isEmpty()) {
            // Remove root node.
            try (SubmergeDataType<T> root = mTransaction.getRoot()) {
                if (root == null) {
                    throw new IllegalSchemaChangeException(
                            getDebugIdentityString()
                                    + " - removePath: unable to remove a null root node.");
                }
            }
            try {
                logInfo("trying to remove root node.");
                mTransaction.setRoot(null);
            } catch (TimestampOverflowException e) {
                throw new IllegalSchemaChangeException(e);
            }
        } else {
            // Remove a leaf node.
            String lastKey = keys.pollLast();
            try {
                findSubmergeNodeSafely(
                        String.join("/", keys),
                        node -> {
                            logInfo(
                                    "trying to remove node: \""
                                            + getDebugStringForPath(normalizedPath)
                                            + "\"");
                            SubmergeMap<T> m = (SubmergeMap<T>) node;
                            m.removeChild(lastKey);
                        });
            } catch (Exception e) {
                throw new IllegalSchemaChangeException(e);
            }
        }
        // Remove all metadata under the path.
        List<RecordMetadata> removed = removeMatchedRecordMetadata(normalizedPath);
        // Close all open records under the path.
        Iterator<WeakReference<SubmergeRecord>> iterator = mSubmergeRecordRefs.values().iterator();
        while (iterator.hasNext()) {
            SubmergeRecord r = iterator.next().get();
            if (r != null && r.mPath.startsWith(normalizedPath)) {
                r.close();
            }
            iterator.remove();
        }
        for (RecordMetadata metadata : removed) {
            logInfo("removed record: " + getDebugStringForPath(metadata.path()));
        }
    }

    /**
     * Merge the document with a local update.
     *
     * @param update the raw update message from submerge.
     * @return paths that are changed by this merge.
     * @throws OlderBaseNeededException if the merge failed due to an older version base needed.
     */
    public List<String> mergeLocalUpdate(byte[] update)
            throws OlderBaseNeededException, MergeException {
        requireDocumentOpen();
        List<String> changed = doMergeNetworkUpdate(update, /* isFromRemote= */ false);
        logInfo(
                "mergeLocalUpdate: update merged for "
                        + getDebugIdentityString()
                        + ". Changed: "
                        + changed);
        return changed;
    }

    /**
     * Merge the document with a network update.
     *
     * @param update the raw network update message from submerge.
     * @return paths that are changed by this merge.
     * @throws OlderBaseNeededException if the merge failed due to an older version base needed.
     */
    public List<String> mergeNetworkUpdate(byte[] update)
            throws OlderBaseNeededException, MergeException {
        requireDocumentOpen();
        List<String> changed = doMergeNetworkUpdate(update, /* isFromRemote= */ true);
        logInfo(
                "mergeNetworkUpdate: update merged for "
                        + getDebugIdentityString()
                        + ". Changed: "
                        + changed);
        return changed;
    }

    private List<String> doMergeNetworkUpdate(byte[] update, boolean isFromRemote)
            throws OlderBaseNeededException, MergeException {
        Map<String, Object> before = collectAllData();
        mTransaction.mergeNetworkUpdate(update);
        Map<String, Object> after = collectAllData();
        List<String> changed = new ArrayList<>();
        for (String path : before.keySet()) {
            if (!after.containsKey(path)) {
                // A path has been removed.
                changed.add(path);
                removeMatchedRecordMetadata(path);
                Log.w(TAG, "mergeNetworkUpdate: removed path: " + getDebugStringForPath(path));
            } else if (!Objects.equals(before.get(path), after.get(path))) {
                // A path has changed.
                changed.add(path);
                setPathLastModifiedByLocalDevice(path, !isFromRemote);
            }
        }
        for (String path : after.keySet()) {
            if (!before.containsKey(path)) {
                // A path has been added.
                changed.add(path);
                setPathLastModifiedByLocalDevice(path, !isFromRemote);
            }
        }
        return changed;
    }

    private static ArrayDeque<String> normalizedKeys(String path) {
        ArrayDeque<String> normalizedKeys = new ArrayDeque<>();
        for (String key : path.split("/")) {
            String normalizedKey = key.trim();
            if (!normalizedKey.isEmpty()) {
                normalizedKeys.add(normalizedKey);
            }
        }
        return normalizedKeys;
    }

    private static String normalizedPath(String path) {
        return "/" + String.join("/", normalizedKeys(path));
    }

    private void setPathLastModifiedByLocalDevice(String path, boolean lastModifiedByLocalDevice) {
        RecordMetadata recordMetadata = new RecordMetadata(path, lastModifiedByLocalDevice);
        mRecordMetadataMap.put(recordMetadata.path(), recordMetadata);
    }

    private RecordMetadata getOrCreateRecordMetadata(String path) {
        RecordMetadata metadata = mRecordMetadataMap.get(path);
        if (metadata != null) {
            return metadata;
        }
        // We always explicitly set the "last modified by local device" bit when we create a record.
        // If we have a record but don't have a metadata, it's assumed to be created by a remote
        // device.
        metadata = new RecordMetadata(path, false);
        mRecordMetadataMap.put(path, metadata);
        return metadata;
    }

    /**
     * Remove all metadata whose path starts with given prefix.
     *
     * @return a list of removed metadata instances.
     */
    private List<RecordMetadata> removeMatchedRecordMetadata(String pathPrefix) {
        List<RecordMetadata> removed = new ArrayList<>();
        Iterator<Map.Entry<String, RecordMetadata>> iterator =
                mRecordMetadataMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RecordMetadata> entry = iterator.next();
            if (entry.getKey().startsWith(pathPrefix)) {
                removed.add(entry.getValue());
                iterator.remove();
            }
        }
        return removed;
    }

    @Nullable
    private SubmergeRecord getRecordFromCache(String path) {
        WeakReference<SubmergeRecord> ref = mSubmergeRecordRefs.get(path);
        if (ref == null) {
            return null;
        }
        SubmergeRecord record = ref.get();
        if (record == null) {
            // Already dereferenced. Remove entry.
            mSubmergeRecordRefs.remove(path);
            return null;
        }
        return record;
    }

    /**
     * Wrapper of {@link #findOrCreateLeaf(String, ThrowingSupplier)} that automatically queries
     * cache, create node, send it to consumer for action, and close the resource if necessary.
     *
     * @param path the path to find or create leaf node.
     * @param nodeCreator a function for creating the leaf node.
     * @param consumer a function to consume the leaf node.
     * @throws Exception if unable to find or create leaf node.
     */
    private void findOrCreateLeafSafely(
            String path,
            ThrowingSupplier<SubmergeDataType<T>> nodeCreator,
            ThrowingConsumer<SubmergeDataType<T>> consumer)
            throws Exception {
        SubmergeRecord r = getRecordFromCache(path);
        if (r != null) {
            // Cache hit.
            consumer.acceptOrThrow(r.mSubmergeData);
        } else {
            // Cache miss.
            try (SubmergeDataType<T> node = findOrCreateLeaf(path, nodeCreator)) {
                consumer.acceptOrThrow(node);
            }
        }
    }

    /**
     * A wrapper of {@link #findSubmergeNode(String)} that automatically queries cache, send it to
     * consumer for action, and close the resource if necessary.
     *
     * @param path the path to find the node.
     * @param consumer a function to consume the node.
     * @throws IllegalArgumentException if an error occurs.
     */
    private void findSubmergeNodeSafely(
            String path, ThrowingConsumer<SubmergeDataType<T>> consumer) {
        SubmergeRecord r = getRecordFromCache(path);
        if (r != null) {
            // Cache hit.
            try {
                consumer.acceptOrThrow(r.mSubmergeData);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            // Cache miss.
            try (SubmergeDataType<T> node = findSubmergeNode(path)) {
                consumer.acceptOrThrow(requireNonNull(node));
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    private Map<String, Object> collectAllData() {
        Map<String, Object> res = new HashMap<>();
        forEachNode(
                (path, node) -> {
                    if (node instanceof SubmergeRegister<T> reg) {
                        res.put(path, reg.get());
                    } else if (node instanceof SubmergeSet<T> set) {
                        res.put(path, set.entries());
                    } else if (node instanceof SubmergeVectorData<T> vec) {
                        res.put(path, vec.entries());
                    }
                });
        return res;
    }

    private void forEachNode(ThrowingBiConsumer<String, SubmergeDataType<T>> consumer) {
        try (SubmergeDataType<T> root = mTransaction.getRoot()) {
            forEachNodeRecursive(root, new LinkedList<>(), consumer);
        } catch (Exception e) {
            Log.e(
                    TAG,
                    "forEachNode: couldn't complete enumeration of "
                            + getDebugIdentityString()
                            + " due to error.",
                    e);
        }
    }

    private void forEachNodeRecursive(
            @Nullable SubmergeDataType<T> root,
            LinkedList<String> path,
            ThrowingBiConsumer<String, SubmergeDataType<T>> consumer)
            throws Exception {
        if (root == null) {
            // Stop if root node is null.
            return;
        }
        consumer.acceptOrThrow("/" + String.join("/", path), root);
        if (!(root instanceof SubmergeMap<T> map)) {
            return;
        }
        for (String key : map.keySet()) {
            path.add(key);
            try (SubmergeDataType<T> child = map.getChild(key)) {
                forEachNodeRecursive(child, path, consumer);
            }
            path.pollLast();
        }
    }

    /**
     * Find a node from the given path.
     *
     * @param path the path to look up.
     * @return a node in that path, or {@code null} if the path doesn't exist.
     */
    @VisibleForTesting
    @Nullable
    SubmergeDataType<T> findSubmergeNode(String path) {
        try (SmartAutoClosable<SubmergeDataType<T>> root =
                new SmartAutoClosable<>(mTransaction.getRoot())) {
            SubmergeDataType<T> result =
                    findSubmergeNodeRecursive(root.unwrap(), normalizedKeys(path));
            if (result == root.unwrap()) {
                // Do not close root if it's the target node.
                root.setKeepOpen(true);
            }
            return result;
        }
    }

    private SubmergeDataType<T> findSubmergeNodeRecursive(
            @Nullable SubmergeDataType<T> root, Queue<String> path) {
        if (root == null) {
            // Stop searching if root node is null.
            return null;
        }
        if (path.isEmpty()) {
            // Reached the end of the path and there is no more children to traverse. Return current
            // root as the result.
            return root;
        }
        if (!(root instanceof SubmergeMap)) {
            // Stop searching if we reached the leaf and can no longer traverse further.
            return null;
        }
        // Get the next child.
        SubmergeDataType<T> child = ((SubmergeMap<T>) root).getChild(path.poll());
        try (SmartAutoClosable<?> closeable = new SmartAutoClosable<>(child)) {
            // Recursively search from the child.
            SubmergeDataType<T> result = findSubmergeNodeRecursive(child, path);
            if (result == child) {
                // Do not close child if it's the target node.
                closeable.setKeepOpen(true);
            }
            return result;
        }
    }

    /**
     * Find or create a leaf node in the given path.
     *
     * @param path the path to find or create leaf node.
     * @param nodeCreator a function for creating the leaf node if necessary.
     * @return the leaf node in the given path.
     * @throws Exception if we are unable to find or create the leaf node.
     */
    @VisibleForTesting
    SubmergeDataType<T> findOrCreateLeaf(
            String path, ThrowingSupplier<SubmergeDataType<T>> nodeCreator) throws Exception {
        Queue<String> keys = normalizedKeys(path);
        SubmergeDataType<T> root =
                getOrCreateRoot(
                        () -> keys.isEmpty() ? nodeCreator.getOrThrow() : mTransaction.newMap());
        try (SmartAutoClosable<?> closable = new SmartAutoClosable<>(root)) {
            SubmergeDataType<T> result = findOrCreateLeafRecursive(root, keys, nodeCreator);
            if (result == root) {
                closable.setKeepOpen(true);
            }
            return result;
        }
    }

    /**
     * Find or create a leaf node in the given path. Recursively create {@link SubmergeMap} in the
     * path if necessary.
     *
     * @param root the root node to begin traversal from.
     * @param path the relative path to traverse from the current root node.
     * @param nodeCreator a function for creating the leaf node.
     * @return the leaf node in the given path.
     * @throws Exception if unable to get or create leaf node in the given path.
     */
    private SubmergeDataType<T> findOrCreateLeafRecursive(
            SubmergeDataType<T> root,
            Queue<String> path,
            ThrowingSupplier<SubmergeDataType<T>> nodeCreator)
            throws Exception {
        // Step 1: base case - already reached the target path. Return current root if feasible.
        if (path.isEmpty()) {
            if (root instanceof SubmergeMap) {
                throw new IllegalArgumentException(
                        getDebugIdentityString()
                                + " - unable to insert leaf node. Path points to intermediate "
                                + "node");
            }
            return root;
        }
        // Step 2: recursion case.
        if (!(root instanceof SubmergeMap)) {
            // Root is not map. Abort.
            throw new IllegalArgumentException(
                    getDebugIdentityString()
                            + " - unable to insert leaf node. Intermediate path is already"
                            + " occupied.");
        }
        // Root node is a map. Recursively traverse the tree and create new node as necessary.
        SubmergeDataType<T> child =
                getOrCreateChild(
                        (SubmergeMap<T>) root,
                        path.poll(),
                        () -> {
                            if (path.isEmpty()) {
                                logDebug("insertLeafRecursive: creating leaf node");
                                return nodeCreator.getOrThrow();
                            } else {
                                logDebug("insertLeafRecursive: creating intermediate node");
                                return mTransaction.newMap();
                            }
                        });
        try (SmartAutoClosable<?> closeable = new SmartAutoClosable<>(child)) {
            SubmergeDataType<T> result = findOrCreateLeafRecursive(child, path, nodeCreator);
            if (result == child) {
                // Keep the object open if it's the target.
                closeable.setKeepOpen(true);
            }
            return result;
        }
    }

    private SubmergeDataType<T> getOrCreateChild(
            SubmergeMap<T> root, String key, ThrowingSupplier<SubmergeDataType<T>> nodeCreator)
            throws Exception {
        SubmergeDataType<T> child = root.getChild(key);
        if (child != null) {
            return child;
        }
        try (SmartAutoClosable<SubmergeDataType<T>> node =
                new SmartAutoClosable<>(nodeCreator.getOrThrow())) {
            logDebug(
                    "getOrCreateChild: inserting node "
                            + node.unwrap()
                            + " into intermediate node "
                            + root
                            + " under the key \""
                            + key
                            + "\"");
            root.setChild(key, requireNonNull(node.unwrap()));
            node.setKeepOpen(true);
            return node.unwrap();
        }
    }

    private SubmergeDataType<T> getOrCreateRoot(ThrowingSupplier<SubmergeDataType<T>> nodeCreator)
            throws Exception {
        SubmergeDataType<T> root = mTransaction.getRoot();
        if (root != null) {
            return root;
        }
        try (SmartAutoClosable<SubmergeDataType<T>> node =
                new SmartAutoClosable<>(nodeCreator.getOrThrow())) {
            logDebug("getOrCreateRoot: inserting root node " + node.unwrap());
            mTransaction.setRoot(requireNonNull(node.unwrap()));
            node.setKeepOpen(true);
            return node.unwrap();
        }
    }

    private String getDebugIdentityString() {
        return mDataStoreName + "::" + mDocId;
    }

    @Override
    public String getDebugStringForPath(String path) {
        return getDebugIdentityString() + ":" + path;
    }

    private void logDebug(String message) {
        if (DEBUG) {
            Log.d(TAG, getDebugIdentityString() + " - " + message);
        }
    }

    private void logInfo(String message) {
        Log.i(TAG, getDebugIdentityString() + " - " + message);
    }

    private void requireDocumentOpen() {
        if (mClosed) {
            throw new IllegalStateException(getDebugIdentityString() + " - document is closed.");
        }
    }

    public void commitTransaction() throws TransactionException {
        requireDocumentOpen();
        // Always close self regardless of if transaction succeeded or failed.
        try (SubmergeDocument<T> document = this) {
            mTransaction.commit();
        }
    }

    public List<RecordMetadata> getAllRecordMetaData() {
        requireDocumentOpen();
        return List.copyOf(mRecordMetadataMap.values());
    }

    private abstract class SubmergeRecord implements AutoCloseable {
        protected final SubmergeDataType<T> mSubmergeData;
        protected final String mPath;
        private volatile boolean mClosed;

        SubmergeRecord(SubmergeDataType<T> submergeData, String path) {
            mSubmergeData = submergeData;
            mPath = path;
        }

        @Override
        public void close() {
            if (mClosed) {
                return;
            }
            mClosed = true;
            mSubmergeData.close();
        }

        public SharedDataStore.Metadata getMetadata() {
            requireRecordOpen();
            return new RecordMetadataWrapper(getOrCreateRecordMetadata(mPath));
        }

        protected void requireRecordOpen() {
            if (mClosed) {
                throw new IllegalStateException(getDebugIdentityString() + " - record is closed.");
            }
        }

        public abstract SharedDataStore.Record<T> asSharedDataStoreRecord();
    }

    private class SubmergeRegisterRecord extends SubmergeRecord
            implements SharedDataStore.Record<T> {
        SubmergeRegisterRecord(SubmergeRegister<T> submergeRegister, String path) {
            super(submergeRegister, path);
        }

        @Nullable
        @Override
        public T get() {
            requireRecordOpen();
            return ((SubmergeRegister<T>) mSubmergeData).get();
        }

        @Override
        public SharedDataStore.Record<T> asSharedDataStoreRecord() {
            return this;
        }
    }

    private class SubmergeSetRecord extends SubmergeRecord implements SharedDataStore.SetRecord<T> {
        SubmergeSetRecord(SubmergeSet<T> submergeSet, String path) {
            super(submergeSet, path);
        }

        @Override
        public Set<T> entries() {
            requireRecordOpen();
            return ((SubmergeSet<T>) mSubmergeData).entries();
        }

        @Override
        public SharedDataStore.SetRecord<T> asSharedDataStoreRecord() {
            return this;
        }
    }

    private class SubmergeVectorRecord extends SubmergeRecord
            implements SharedDataStore.UnmergedRecord<T> {
        SubmergeVectorRecord(SubmergeVectorData<T> submergeVectorData, String path) {
            super(submergeVectorData, path);
        }

        @Override
        public Map<String, T> entries() {
            requireRecordOpen();
            return ((SubmergeVectorData<T>) mSubmergeData).entries();
        }

        @Nullable
        @Override
        public T get() {
            return get(mLocalDeviceNodeId);
        }

        @Override
        public SharedDataStore.UnmergedRecord<T> asSharedDataStoreRecord() {
            return this;
        }
    }

    private static class RecordMetadataWrapper implements SharedDataStore.Metadata {
        private final RecordMetadata mMetadata;

        RecordMetadataWrapper(RecordMetadata metadata) {
            this.mMetadata = metadata;
        }

        @Override
        public boolean isLastModifiedByLocalDevice() {
            return mMetadata.lastModifiedByLocalDevice();
        }
    }
}
