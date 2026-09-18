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
package com.android.server.companion.datatransfer.crossdevicesync.data.storage;

import android.annotation.Nullable;

import com.android.internal.infra.AndroidFuture;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;
import com.android.internal.util.FunctionalUtils.ThrowingRunnable;

import com.google.android.submerge.StorageInterface.StorageException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/** Interface representing a persistent storage. */
public interface IStorage {

    /** Open the storage. */
    void open();

    /** Get the serialized document. */
    byte[] getDocument(String docId) throws StorageException;

    /** Save the serialized document into the persistent storage. */
    void persistDocument(String docId, byte[] document) throws StorageException;

    /**
     * Get the serialized document schema. This is a submerge document that can be merged via
     * network update to mutate schema.
     */
    byte[] getDocumentSchema(String docId) throws StorageException;

    /** Save the serialized document schema into the persistent storage. */
    void persistDocumentSchema(String docId, byte[] documentSchema) throws StorageException;

    /** Get the serialized metadata of a document. */
    @Nullable
    byte[] getMetadata(String docId) throws StorageException;

    /** Save the serialized document metadata into the persistent storage. */
    void persistMetadata(String docId, byte[] metadata) throws StorageException;

    /**
     * Open a database transaction and apply the given transaction applier within the transaction.
     */
    void transact(ThrowingConsumer<IStorage> transactionApplier) throws Exception;

    /** Get all document ids from this storage. */
    List<String> getAllDocumentIds() throws StorageException;

    /** Delete documents based on the given doc ids. */
    void deleteDocuments(List<String> docIds) throws StorageException;

    /** Delete the database. */
    boolean deleteDatabase();

    /**
     * Close the database. Note that this will not shutdown the IO thread if it's running, since the
     * database could be reopen via {@link #open()}. To shutdown the IO thread, please call {@link
     * #shutdownIoThread()} instead.
     */
    void close();

    /**
     * Submit a task to to the internal IO thread.
     *
     * @param task a task to be executed in the IO thread
     * @return a {@link AndroidFuture} that will be completed with the result of the task
     */
    <R> AndroidFuture<R> submitToIoThread(Callable<R> task);

    /**
     * Same as {@link #submitToIoThread(Callable)} but accepts a {@link ThrowingRunnable} instead.
     */
    @CanIgnoreReturnValue
    default AndroidFuture<Void> runInIoThread(ThrowingRunnable runnable) {
        return submitToIoThread(
                () -> {
                    runnable.run();
                    return null;
                });
    }

    /**
     * Get an {@link Executor} that submits tasks to the internal IO thread as if directly calling
     * {@link #runInIoThread(ThrowingRunnable)}.
     */
    default Executor getIoThreadExecutor() {
        return runnable -> runInIoThread(runnable::run);
    }

    /**
     * Shutdown the IO thread if it's running. This is a no-op if the IO thread is not running.
     * After calling this method, {@link #submitToIoThread(Callable)} will fail immediately.
     */
    void shutdownIoThread();
}
