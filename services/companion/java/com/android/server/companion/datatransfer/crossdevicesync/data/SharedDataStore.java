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

import com.android.internal.infra.AndroidFuture;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.Network;

import com.google.android.submerge.StorageInterface.StorageException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Interface representing a data store that is in sync with remote devices. Data stored in this
 * interface will be eventually synced across other authorized devices. This is the main interface a
 * sync feature should interact with.
 *
 * @param <T> the data type that this data store manages.
 */
public interface SharedDataStore<T> {

    /**
     * Initialize the data store. A data store is usable only after this method is called.
     *
     * @param network the network to be used for syncing.
     * @return a {@link AndroidFuture} to get notified when initialization is done or failed. {@link
     *     IllegalSchemaChangeException} will be passed to the future if the schema upgrade fails
     *     due to illegal schema. {@link SchemaValidationException} will be passed to the future if
     *     the data store upgrade results in invalid data. {@link StorageException} will be passed
     *     to the future if the underlying persistent data store fails. Schema related failure are
     *     usually non-recoverable, so you may want to delete the entire data store and rebuild it
     *     from empty state.
     */
    AndroidFuture<?> init(Network network);

    /**
     * Gets the local device node id. This is a unique string representing a device that
     * participates sync in this data store.
     */
    String getLocalDeviceNodeId();

    /**
     * Request a transaction. The supplied {@link TransactionApplier} will be asynchronously invoked
     * in the background IO thread after the transaction is successfully open. Any interactions with
     * {@link Document} should be done in the {@link TransactionApplier#transact(MutableDocument)}
     * method synchronously. After the method returns, the transaction will be committed if no error
     * occurs. The calling thread will be notified the transaction result via a {@link
     * AndroidFuture}.
     *
     * @param <R> the type of return value of the transaction.
     * @param docId id of the document to interact with. If the document doesn't exist, a new
     *     document will be lazily created by this transaction.
     * @return a {@link AndroidFuture} to get notified when the transaction is done or aborted due
     *     to error. The applier can optionally return a result, which will be delivered to the
     *     {@link AndroidFuture}. {@link SchemaValidationException} will be passed to the future if
     *     the change failed schema validation. {@link StorageException} will be passed to the
     *     future if the underlying persistent data store fails.
     */
    <R> AndroidFuture<R> transact(String docId, TransactionApplier<T, R> applier);

    /** Register a listener for monitoring remote change. */
    void registerOnRemoteChangeListener(Executor executor, OnRemoteChangeListener listener);

    /** Unregister a {@link OnRemoteChangeListener}. */
    void unregisterOnRemoteChangeListener(OnRemoteChangeListener listener);

    /**
     * Close the data store. Any further interactions with this data store will fail. Also returns a
     * {@link AndroidFuture} to notify callers when all resources are released. Note that the close
     * state is immediately effective. The actual resource cleanup (e.g. close the database,
     * shutdown IO thread, etc) may happen asynchronously and will be notified via the {@link
     * AndroidFuture}.
     *
     * @return a {@link AndroidFuture} to get notified when all resources are closed
     */
    AndroidFuture<?> close();

    /** Check if the data store is open. */
    boolean isOpen();

    /**
     * A document that can be used to access data.
     *
     * <p>A document represents a tree structure where data records are stored in leaf nodes.
     * Intermediate nodes are represented by keys, and they can be accessed via a path similar to
     * the file system path.
     *
     * <p>For example, if a data record is organized under "first_key" and "second_key", it can be
     * accessed via path "/first_key/second_key".
     *
     * <p>Note: a document can only be accessed within {@link
     * TransactionApplier#transact(MutableDocument)} method. Any interactions with the document
     * after transaction will lead to {@link IllegalStateException}.
     *
     * @param <T> the type of data in the document.
     */
    interface Document<T> {

        /** Get the id of this document. */
        String getDocId();

        /** Get the schema version of this document. */
        int getSchemaVersion();

        /**
         * Get a record from a path.
         *
         * @param path the path to the record.
         * @return null if the path is invalid.
         */
        @Nullable
        Record<T> getRecord(String path);

        /** Check if a path exists. */
        boolean containsPath(String path);

        /** Get a user readable string for a path. */
        String getDebugStringForPath(String path);
    }

    /**
     * A mutable interface of {@link Document} which supports mutations.
     *
     * @param <T> the type of data in the document.
     */
    interface MutableDocument<T> extends Document<T> {

        /**
         * Put a data into a {@link Record} in the path.
         *
         * @throws IllegalArgumentException if the path doesn't exist or is not a base {@link
         *     Record}.
         */
        void putData(String path, @Nullable T data) throws IllegalArgumentException;

        /**
         * Put a data into an {@link UnmergedRecord} in the path.
         *
         * @throws IllegalArgumentException if the path doesn't exist or is not an {@link
         *     UnmergedRecord}.
         */
        void putUnmergedData(String path, @Nullable T data) throws IllegalArgumentException;

        /**
         * Add a data to a {@link SetRecord} in a path.
         *
         * @throws IllegalArgumentException if the path doesn't exist or is not a {@link SetRecord}.
         */
        void addDataToSet(String path, T data) throws IllegalArgumentException;

        /**
         * Remove a data from a {@link SetRecord} in a path. Do nothing if the data doesn't exist in
         * the target set.
         *
         * @throws IllegalArgumentException if the path doesn't exist or is not a {@link SetRecord}.
         */
        void removeDataFromSet(String path, T data) throws IllegalArgumentException;
    }

    /**
     * A record represents a leaf node in the document tree where data is stored. Data stored in
     * {@link Record} is merged using "last change wins" strategy.
     *
     * <p>Note: if this record is also a {@link SetRecord} or {@link UnmergedRecord}, it may contain
     * more than a single value. See {@link SetRecord} and {@link UnmergedRecord} for details.
     *
     * @param <T> the type of data in the record.
     */
    interface Record<T> {

        /** Get the data stored in this record. */
        @Nullable
        T get();

        /** Get the metadata of this record. This information is local only, and is not synced. */
        Metadata getMetadata();
    }

    /** Metadata of a data record. */
    interface Metadata {

        /** If this record is last modified by this local device instead of by a remote device. */
        boolean isLastModifiedByLocalDevice();
    }

    /**
     * A record represents a set data structure. It implements eventually consistency for add() and
     * remove() operations.
     *
     * @param <T> the type of data in the set.
     */
    interface SetRecord<T> extends Record<T> {

        /** Get any data in the set, or null if the set is empty. */
        @Nullable
        default T get() {
            Set<T> entries = entries();
            if (entries.isEmpty()) {
                return null;
            }
            return entries.iterator().next();
        }

        /** Get all entries in this set. */
        Set<T> entries();
    }

    /**
     * A record represents an unmerged data. Because the data is unmerged, each device writes into
     * its own copy. As a result, the record maintains a map between device node id and the last
     * known value.
     *
     * <p>For example, this record type can be used to compute "global counter" where each device
     * independently increments a counter, which get synced in the network so that each device can
     * see other device's counter value. Then each device can independently sum the counters to
     * compute a "global counter" based on the values in {@link UnmergedRecord#entries()} .
     *
     * @param <T> the type of data in the record.
     */
    interface UnmergedRecord<T> extends Record<T> {

        /**
         * Get the last known data written by a specific device.
         *
         * @param deviceNodeId the node id of a device. Returns null if we don't know the data.
         */
        @Nullable
        default T get(String deviceNodeId) {
            return entries().get(deviceNodeId);
        }

        /** Get data from all known nodes. */
        Map<String, T> entries();
    }

    /** A listener called when a data has been updated by a remote device. */
    interface OnRemoteChangeListener {

        /**
         * Called when data paths have been updated by a remote device.
         *
         * @param paths the paths that have been updated
         */
        void onRemoteChange(List<String> paths);
    }

    /**
     * An interface that interacts with a document in a transaction.
     *
     * @param <T> the type of data in the document.
     * @param <R> the type of return value of the transaction.
     */
    interface TransactionApplier<T, R> {

        /**
         * Access or modify a document. Modifications to the document will be applied after the
         * method returns. Interactions with the document after the method returns will lead to
         * {@link IllegalStateException}.
         *
         * @return the result of the transaction.
         * @throws Exception if the transaction should be aborted.
         */
        R transact(MutableDocument<T> document) throws Exception;
    }
}
