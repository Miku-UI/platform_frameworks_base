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
import android.util.Log;

import com.android.internal.infra.AndroidFuture;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;
import com.android.server.companion.datatransfer.crossdevicesync.common.Utils;

import com.google.android.submerge.StorageInterface.StorageException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decorator of {@link IStorage} that delegates r/w operations into another {@link IStorage} and
 * prepends document ids with a namespace string. This class is useful to allow multiple features to
 * reuse the same storage. Features under different namespaces will interact with the decorated
 * {@link IStorage} as if it's a private storage.
 */
public class StorageNamespaceDecorator implements IStorage {
    private static final String NAMESPACE_SEPARATOR = "::";
    private static final String TAG = "StorageDecorator";
    private final String mNamespace;
    private final IStorage mDelegate;
    private final AtomicBoolean mOpen = new AtomicBoolean();
    private volatile boolean mIoThreadShutdownRequested;

    public StorageNamespaceDecorator(String namespace, IStorage delegate) {
        this.mNamespace = namespace;
        this.mDelegate = delegate;
    }

    @Override
    public void open() {
        if (!mOpen.getAndSet(true)) {
            mDelegate.open();
        }
    }

    @Override
    public byte[] getDocument(String docId) throws StorageException {
        requireOpen();
        return mDelegate.getDocument(decoratedDocId(docId));
    }

    @Override
    public void persistDocument(String docId, byte[] document) throws StorageException {
        requireOpen();
        mDelegate.persistDocument(decoratedDocId(docId), document);
    }

    @Override
    public byte[] getDocumentSchema(String docId) throws StorageException {
        requireOpen();
        return mDelegate.getDocumentSchema(decoratedDocId(docId));
    }

    @Override
    public void persistDocumentSchema(String docId, byte[] documentSchema) throws StorageException {
        requireOpen();
        mDelegate.persistDocumentSchema(decoratedDocId(docId), documentSchema);
    }

    @Nullable
    @Override
    public byte[] getMetadata(String docId) throws StorageException {
        requireOpen();
        return mDelegate.getMetadata(decoratedDocId(docId));
    }

    @Override
    public void persistMetadata(String docId, byte[] metadata) throws StorageException {
        requireOpen();
        mDelegate.persistMetadata(decoratedDocId(docId), metadata);
    }

    @Override
    public void transact(ThrowingConsumer<IStorage> transactionApplier) throws Exception {
        requireOpen();
        mDelegate.transact(unused -> transactionApplier.acceptOrThrow(this));
    }

    @Override
    public void deleteDocuments(List<String> docIds) throws StorageException {
        requireOpen();
        mDelegate.deleteDocuments(decorateDocIds(docIds));
    }

    @Override
    public List<String> getAllDocumentIds() throws StorageException {
        requireOpen();
        return getAllDocumentIdsUnchecked();
    }

    private List<String> getAllDocumentIdsUnchecked() throws StorageException {
        final String prefix = mNamespace + NAMESPACE_SEPARATOR;
        List<String> filteredList = new ArrayList<>();
        for (String id : mDelegate.getAllDocumentIds()) {
            if (id.startsWith(prefix)) {
                String rawId = id.substring(prefix.length());
                if (rawId.isEmpty()) {
                    continue;
                }
                filteredList.add(rawId);
            }
        }
        return filteredList;
    }

    @Override
    public boolean deleteDatabase() {
        // Instead of deleting the entire delegated storage, we only delete docs that are under the
        // right namespace.
        try {
            mDelegate.transact(
                    unused ->
                            mDelegate.deleteDocuments(
                                    decorateDocIds(getAllDocumentIdsUnchecked())));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete documents", e);
            return false;
        }
    }

    private String decoratedDocId(String docId) {
        return mNamespace + NAMESPACE_SEPARATOR + docId;
    }

    private List<String> decorateDocIds(List<String> docIds) {
        List<String> decoratedDocIds = new ArrayList<>(docIds.size());
        docIds.forEach(id -> decoratedDocIds.add(decoratedDocId(id)));
        return decoratedDocIds;
    }

    @Override
    public void close() {
        // Do not close the delegated storage which may be used by other features.
        mOpen.set(false);
    }

    private void requireOpen() throws StorageException {
        if (!mOpen.get()) {
            throw new StorageException();
        }
    }

    @Override
    public <R> AndroidFuture<R> submitToIoThread(Callable<R> task) {
        if (mIoThreadShutdownRequested) {
            // Simulate a shutdown response so that io thread is no longer accessible from this
            // decorator.
            return Utils.failedAndroidFuture(new IllegalStateException("IO thread is shutdown!"));
        }
        return mDelegate.submitToIoThread(task);
    }

    @Override
    public void shutdownIoThread() {
        // Do not shutdown the delegate storage's IO thread since the io thread may be used by other
        // features.
        mIoThreadShutdownRequested = true;
    }

    @Override
    public String toString() {
        return "StorageNamespaceDecorator{namespace="
                + mNamespace
                + ", delegate="
                + mDelegate
                + "}";
    }
}
