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
package com.android.server.companion.datatransfer.crossdevicesync.data.storage.fake;

import android.annotation.Nullable;

import com.android.internal.infra.AndroidFuture;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;
import com.android.server.companion.datatransfer.crossdevicesync.common.Utils;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.SynchronousExecutorService;
import com.android.server.companion.datatransfer.crossdevicesync.data.storage.IStorage;

import com.google.android.submerge.StorageInterface;
import com.google.android.submerge.StorageInterface.StorageException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/** Fake implementation of {@link StorageInterface}. */
public class FakeStorage implements IStorage {

    private final Map<String, byte[]> mDocuments = new HashMap<>();
    private final Map<String, byte[]> mMetadata = new HashMap<>();
    private final Map<String, byte[]> mSchema = new HashMap<>();
    private final ExecutorService mExecutorService = new SynchronousExecutorService();
    private boolean mOpen;
    private RuntimeException mException;

    public FakeStorage() {}

    @Override
    public void open() {
        maybeThrowException();
        mOpen = true;
    }

    @Nullable
    @Override
    public byte[] getDocument(String docId) throws StorageException {
        maybeThrowException();
        if (!mOpen) {
            throw new StorageException();
        }
        return mDocuments.get(docId);
    }

    /** Get a document without any precondition check. */
    @Nullable
    public byte[] getDocumentUnchecked(String docId) {
        return mDocuments.get(docId);
    }

    @Override
    public void persistDocument(String docId, byte[] document) throws StorageException {
        maybeThrowException();
        if (!mOpen) {
            throw new StorageException();
        }
        mDocuments.put(docId, document);
    }

    @Override
    public byte[] getDocumentSchema(String docId) throws StorageException {
        maybeThrowException();
        if (!mOpen) {
            throw new StorageException();
        }
        return mSchema.get(docId);
    }

    @Override
    public void persistDocumentSchema(String docId, byte[] documentSchema) throws StorageException {
        maybeThrowException();
        if (!mOpen) {
            throw new StorageException();
        }
        mSchema.put(docId, documentSchema);
    }

    @Override
    @Nullable
    public byte[] getMetadata(String docId) throws StorageException {
        maybeThrowException();
        if (!mOpen) {
            throw new StorageException();
        }
        return mMetadata.get(docId);
    }

    @Override
    public void persistMetadata(String docId, byte[] metadata) throws StorageException {
        maybeThrowException();
        if (!mOpen) {
            throw new StorageException();
        }
        mMetadata.put(docId, metadata);
    }

    @Override
    public void transact(ThrowingConsumer<IStorage> transactionApplier) throws Exception {
        maybeThrowException();
        if (!mOpen) {
            throw new StorageException();
        }
        transactionApplier.acceptOrThrow(this);
    }

    @Override
    public List<String> getAllDocumentIds() throws StorageException {
        maybeThrowException();
        if (!mOpen) {
            throw new StorageException();
        }
        Set<String> ids = new HashSet<>();
        ids.addAll(mDocuments.keySet());
        ids.addAll(mMetadata.keySet());
        ids.addAll(mSchema.keySet());
        return ids.stream().toList();
    }

    @Override
    public void deleteDocuments(List<String> docIds) throws StorageException {
        maybeThrowException();
        if (!mOpen) {
            throw new StorageException();
        }
        for (String docId : docIds) {
            mDocuments.remove(docId);
            mMetadata.remove(docId);
            mSchema.remove(docId);
        }
    }

    @Override
    public void close() {
        maybeThrowException();
        mOpen = false;
    }

    @Override
    public <R> AndroidFuture<R> submitToIoThread(Callable<R> task) {
        if (mExecutorService.isShutdown()) {
            return Utils.failedAndroidFuture(new IllegalStateException("Shutdown"));
        }
        return AndroidFuture.supplyAsync(
                () -> {
                    try {
                        return task.call();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                },
                mExecutorService);
    }

    @Override
    public void shutdownIoThread() {
        maybeThrowException();
        mExecutorService.shutdown();
    }

    public boolean isIoThreadShutdown() {
        return mExecutorService.isShutdown();
    }

    @Override
    public boolean deleteDatabase() {
        maybeThrowException();
        close();
        boolean hasData = !mDocuments.isEmpty() || !mMetadata.isEmpty() || !mSchema.isEmpty();
        mDocuments.clear();
        mMetadata.clear();
        mSchema.clear();
        return hasData;
    }

    public void setRuntimeException(@Nullable RuntimeException e) {
        mException = e;
    }

    private void maybeThrowException() {
        if (mException != null) {
            RuntimeException e = mException;
            mException = null;
            throw e;
        }
    }

    public boolean isOpen() {
        return mOpen;
    }
}
