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
package com.android.server.companion.datatransfer.crossdevicesync.data.fake;

import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.failedAndroidFuture;
import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.handleAsync;
import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.handleFailureAsync;
import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.toAndroidFuture;

import static java.util.Objects.requireNonNull;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.internal.infra.AndroidFuture;
import com.android.server.companion.datatransfer.crossdevicesync.data.DeviceNodeIdProvider;
import com.android.server.companion.datatransfer.crossdevicesync.data.DocumentSchemaInfo;
import com.android.server.companion.datatransfer.crossdevicesync.data.PathSchemaInfo;
import com.android.server.companion.datatransfer.crossdevicesync.data.RecordType;
import com.android.server.companion.datatransfer.crossdevicesync.data.SchemaProvider;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStoreHandle;
import com.android.server.companion.datatransfer.crossdevicesync.data.storage.IStorage;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.Network;

import com.google.android.submerge.Converter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Fake implementation of {@link SharedDataStore} for testing. */
@SuppressWarnings("EffectivelyPrivate")
public class FakeSharedDataStore<T> implements SharedDataStore<T> {
    private static final String TAG = "FakeSharedDataStore";

    private final IStorage mStorage;
    private final List<Pair<Executor, OnRemoteChangeListener>> mListeners = new ArrayList<>();
    private final String mName;
    private final DeviceNodeIdProvider mDeviceNodeIdProvider;
    private final SchemaProvider<T> mSchemaProvider;
    private final SharedDataStoreHandle<T> mHandle;
    private final Converter<T> mConverter;
    private boolean mOpen;
    private boolean mAsync;
    @Nullable private String mSelfNodeId;
    @Nullable private AndroidFuture<Void> mInitFuture;
    @Nullable private AndroidFuture<Void> mCloseFuture;
    @Nullable private AndroidFuture<Void> mTransactFuture;
    @Nullable private AndroidFuture<Void> mExternalInitFuture;
    @Nullable private AndroidFuture<Void> mExternalCloseFuture;

    /**
     * Create an empty shared data store with specified name, node id provider, and schema provider.
     */
    public FakeSharedDataStore(
            String name,
            DeviceNodeIdProvider deviceNodeIdProvider,
            SchemaProvider<T> schemaProvider,
            IStorage storage,
            SharedDataStoreHandle<T> handle,
            Converter<T> converter) {
        mName = name;
        mDeviceNodeIdProvider = deviceNodeIdProvider;
        mSchemaProvider = schemaProvider;
        mStorage = storage;
        mHandle = handle;
        mConverter = converter;
    }

    @Override
    public AndroidFuture<?> init(Network network) {
        if (mOpen) {
            return requireNonNull(mExternalInitFuture);
        }
        if (!mHandle.lock(this)) {
            return failedAndroidFuture(new IllegalStateException("Already locked"));
        }
        mOpen = true;
        mSelfNodeId = mDeviceNodeIdProvider.getOrCreateNodeIdForDataStore(mName);
        List<CompletableFuture<?>> futures = new ArrayList<>();
        futures.add(mStorage.runInIoThread(mStorage::open));
        for (DocumentSchemaInfo schemaInfo : mSchemaProvider.getAllDocumentSchema()) {
            futures.add(
                    transact(
                            schemaInfo.getDocId(),
                            doc -> {
                                ((FakeDocument) doc).initWithSchema(schemaInfo);
                                mSchemaProvider.migrateDocument(doc);
                                return true;
                            }));
        }
        if (mAsync) {
            mInitFuture = new AndroidFuture<>();
            futures.add(mInitFuture);
        }
        // Returned future should be successful only if all the pending tasks are done.
        AndroidFuture<Void> future =
                toAndroidFuture(CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])));
        mExternalInitFuture =
                handleFailureAsync(future, t -> handleAsync(close(), (unused1, unused2) -> future));
        return mExternalInitFuture;
    }

    @Nullable
    public AndroidFuture<?> getInitFuture() {
        return mInitFuture;
    }

    @Override
    public String getLocalDeviceNodeId() {
        return mSelfNodeId;
    }

    @Override
    public <R> AndroidFuture<R> transact(String docId, TransactionApplier<T, R> applier) {
        if (mAsync) {
            // Re-use current future is possible since transact can be called multiple times
            // during test.
            if (mTransactFuture == null || mTransactFuture.isDone()) {
                mTransactFuture = new AndroidFuture<>();
            }
            return mTransactFuture.thenCompose(
                    unused -> doTransact(docId, applier, /* isLocal= */ true, mSelfNodeId));
        } else {
            return doTransact(docId, applier, /* isLocal= */ true, mSelfNodeId);
        }
    }

    /** Simulate a remote change. */
    public <R> AndroidFuture<R> transactAsRemote(
            String docId, TransactionApplier<T, R> applier, String remoteNodeId) {
        return doTransact(
                docId,
                doc -> {
                    Map<String, Object> before = ((FakeDocument) doc).getAll();
                    R result = applier.transact(doc);
                    Map<String, Object> after = ((FakeDocument) doc).getAll();
                    List<String> paths = new ArrayList<>();
                    for (String path : after.keySet()) {
                        if (!Objects.equals(before.get(path), after.get(path))) {
                            paths.add(path);
                        }
                    }
                    if (!paths.isEmpty()) {
                        mListeners.forEach(
                                l -> l.first.execute(() -> l.second.onRemoteChange(paths)));
                    }
                    return result;
                },
                /* isLocal= */ false,
                remoteNodeId);
    }

    private <R> AndroidFuture<R> doTransact(
            String docId, TransactionApplier<T, R> applier, boolean isLocal, String nodeId) {
        return mStorage.submitToIoThread(
                () -> {
                    if (!mOpen) {
                        throw new IllegalStateException("Closed");
                    }
                    DocumentSchemaInfo schemaInfo = mSchemaProvider.findSchema(docId);
                    if (schemaInfo == null) {
                        throw new IllegalStateException("No schema");
                    }
                    final int schemaVersion = schemaInfo.getVersion();
                    byte[] docBytes = mStorage.getDocument(docId);
                    FakeDocument doc =
                            docBytes == null
                                    ? new FakeDocument(docId, schemaVersion, nodeId)
                                    : new FakeDocument(docBytes);
                    R result = applier.transact(doc.withIdentity(isLocal, nodeId));
                    mSchemaProvider.validateDocument(doc);
                    mStorage.persistDocument(docId, doc.toByteArray());
                    return result;
                });
    }

    /** Get document without any precondition check. */
    @Nullable
    public MutableDocument<T> getDocumentUnchecked(String docId) {
        try {
            byte[] docBytes = mStorage.getDocument(docId);
            return docBytes == null ? null : new FakeDocument(docBytes);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void registerOnRemoteChangeListener(Executor executor, OnRemoteChangeListener listener) {
        mListeners.add(Pair.create(executor, listener));
    }

    @Override
    public void unregisterOnRemoteChangeListener(OnRemoteChangeListener listener) {
        mListeners.removeIf(l -> l.second == listener);
    }

    @Override
    public AndroidFuture<Void> close() {
        if (!mOpen && mExternalCloseFuture != null) {
            return mExternalCloseFuture;
        }
        mOpen = false;
        AndroidFuture<Void> closeFuture;
        if (mAsync) {
            closeFuture = mCloseFuture = new AndroidFuture<>();
        } else {
            closeFuture = AndroidFuture.completedFuture(null);
        }
        mExternalCloseFuture =
                handleAsync(
                        closeFuture,
                        (res, ex) -> {
                            mHandle.unlock(this);
                            return closeFuture;
                        });
        return mExternalCloseFuture;
    }

    @Nullable
    public AndroidFuture<?> getCloseFuture() {
        return mCloseFuture;
    }

    @Override
    public boolean isOpen() {
        return mOpen;
    }

    /** Make init/close/delete/transact operations async. */
    public FakeSharedDataStore<T> setAsync(boolean async) {
        mAsync = async;
        if (!mAsync) {
            if (mInitFuture != null) {
                mInitFuture.complete(null);
                mInitFuture = null;
            }
            if (mTransactFuture != null) {
                mTransactFuture.complete(null);
                mTransactFuture = null;
            }
            if (mCloseFuture != null) {
                mCloseFuture.complete(null);
                mCloseFuture = null;
            }
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder docsString = new StringBuilder();
        try {
            for (String docId : mStorage.getAllDocumentIds()) {
                FakeDocument doc = new FakeDocument(mStorage.getDocument(docId));
                docsString.append("\n").append(doc.toString());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading document", e);
        }
        return "{name=" + mName + ", open=" + mOpen + ", docs=" + docsString + "}";
    }

    private class FakeRecord implements Record<T> {
        @Nullable private T mData;
        private final FakeMetadata mMetadata;

        FakeRecord() {
            mMetadata = new FakeMetadata();
        }

        FakeRecord(DataInputStream dis) throws IOException {
            mData = mConverter.deserialize(readByteArray(dis));
            mMetadata = new FakeMetadata(dis);
        }

        @Nullable
        @Override
        public T get() {
            return mData;
        }

        @Override
        public FakeMetadata getMetadata() {
            return mMetadata;
        }

        public void set(T val) {
            mData = val;
        }

        @Override
        public String toString() {
            return "{data="
                    + (mData == null ? "null" : mData.toString())
                    + ", metadata="
                    + mMetadata
                    + "}";
        }

        public void write(DataOutputStream dos) throws IOException {
            writeByteArray(dos, mConverter.serialize(mData));
            mMetadata.write(dos);
        }
    }

    private class FakeSetRecord implements SetRecord<T> {
        private final Set<T> mSet = new HashSet<>();
        private final FakeMetadata mMetadata;

        FakeSetRecord() {
            mMetadata = new FakeMetadata();
        }

        FakeSetRecord(DataInputStream dis) throws IOException {
            for (byte[] bytes : readByteArrayList(dis)) {
                mSet.add(mConverter.deserialize(bytes));
            }
            mMetadata = new FakeMetadata(dis);
        }

        @Override
        public Set<T> entries() {
            return mSet;
        }

        @Override
        public FakeMetadata getMetadata() {
            return mMetadata;
        }

        @Override
        public String toString() {
            return "{set=" + mSet + ", metadata=" + mMetadata + "}";
        }

        public void write(DataOutputStream dos) throws IOException {
            writeByteArrayList(dos, mSet.stream().map(mConverter::serialize).toList());
            mMetadata.write(dos);
        }
    }

    private class FakeUnmergedRecord implements UnmergedRecord<T> {
        private final Map<String, T> mEntries = new HashMap<>();
        private final FakeMetadata mMetadata;
        private final String mSelfNodeId;

        FakeUnmergedRecord(String selfNodeId) {
            mSelfNodeId = selfNodeId;
            mMetadata = new FakeMetadata();
        }

        FakeUnmergedRecord(DataInputStream dis) throws IOException {
            mSelfNodeId = dis.readUTF();
            for (Map.Entry<String, byte[]> entry : readByteArrayMap(dis).entrySet()) {
                mEntries.put(entry.getKey(), mConverter.deserialize(entry.getValue()));
            }
            mMetadata = new FakeMetadata(dis);
        }

        @Override
        public Map<String, T> entries() {
            return mEntries;
        }

        @Nullable
        @Override
        public T get() {
            return mEntries.get(mSelfNodeId);
        }

        @Override
        public FakeMetadata getMetadata() {
            return mMetadata;
        }

        @Override
        public String toString() {
            return "{entries=" + mEntries + ", metadata=" + mMetadata + "}";
        }

        public void write(DataOutputStream dos) throws IOException {
            dos.writeUTF(mSelfNodeId);
            Map<String, byte[]> bytesMap = new HashMap<>();
            for (Map.Entry<String, T> entry : mEntries.entrySet()) {
                bytesMap.put(entry.getKey(), mConverter.serialize(entry.getValue()));
            }
            writeByteArrayMap(dos, bytesMap);
            mMetadata.write(dos);
        }
    }

    public class FakeDocument implements MutableDocument<T> {
        private final Map<String, Record<T>> mRecords = new HashMap<>();
        private final String mId;
        private final int mSchemaVersion;
        private final String mSelfNodeId;
        private boolean mIsNextUpdateLocal;
        private String mNextUpdateNodeId;

        FakeDocument(String id, int schemaVersion, String selfNodeId) {
            mId = id;
            mSchemaVersion = schemaVersion;
            mSelfNodeId = selfNodeId;
            mIsNextUpdateLocal = true;
            mNextUpdateNodeId = mSelfNodeId;
        }

        public FakeDocument(byte[] data) throws IOException {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            DataInputStream dis = new DataInputStream(bis);
            mId = dis.readUTF();
            mSchemaVersion = dis.readInt();
            mSelfNodeId = dis.readUTF();
            mIsNextUpdateLocal = true;
            mNextUpdateNodeId = mSelfNodeId;

            int count = dis.readInt();
            for (int i = 0; i < count; i++) {
                String path = dis.readUTF();
                int type = dis.readInt();
                if (type == RecordType.TYPE_REGISTER) {
                    mRecords.put(path, new FakeRecord(dis));
                } else if (type == RecordType.TYPE_SET) {
                    mRecords.put(path, new FakeSetRecord(dis));
                } else if (type == RecordType.TYPE_UNMERGED) {
                    mRecords.put(path, new FakeUnmergedRecord(dis));
                } else {
                    throw new IOException("Unknown record type " + type);
                }
            }
        }

        byte[] toByteArray() throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeUTF(mId);
            dos.writeInt(mSchemaVersion);
            dos.writeUTF(mSelfNodeId);

            dos.writeInt(mRecords.size());
            for (Map.Entry<String, Record<T>> entry : mRecords.entrySet()) {
                dos.writeUTF(entry.getKey());
                Record<T> record = entry.getValue();
                if (record instanceof FakeRecord) {
                    dos.writeInt(RecordType.TYPE_REGISTER);
                    ((FakeRecord) record).write(dos);
                } else if (record instanceof FakeSetRecord) {
                    dos.writeInt(RecordType.TYPE_SET);
                    ((FakeSetRecord) record).write(dos);
                } else if (record instanceof FakeUnmergedRecord) {
                    dos.writeInt(RecordType.TYPE_UNMERGED);
                    ((FakeUnmergedRecord) record).write(dos);
                } else {
                    throw new IOException("Unknown record type " + record.getClass());
                }
            }
            return bos.toByteArray();
        }

        @Override
        public void putData(String path, @Nullable T data) {
            FakeRecord r = (FakeRecord) mRecords.computeIfAbsent(path, k -> new FakeRecord());
            if (Objects.equals(r.get(), data)) {
                return;
            }
            r.set(data);
            r.getMetadata().setLastModifiedByLocalDevice(mIsNextUpdateLocal);
        }

        @Override
        public void putUnmergedData(String path, @Nullable T data) {
            FakeUnmergedRecord r =
                    ((FakeUnmergedRecord)
                            mRecords.computeIfAbsent(
                                    path, k -> new FakeUnmergedRecord(mSelfNodeId)));
            if (Objects.equals(r.entries().get(mSelfNodeId), data)) {
                return;
            }
            r.entries().put(mNextUpdateNodeId, data);
            r.getMetadata().setLastModifiedByLocalDevice(mIsNextUpdateLocal);
        }

        @Override
        public void addDataToSet(String path, T data) {
            FakeSetRecord r =
                    ((FakeSetRecord) mRecords.computeIfAbsent(path, k -> new FakeSetRecord()));
            if (r.entries().add(data)) {
                r.getMetadata().setLastModifiedByLocalDevice(mIsNextUpdateLocal);
            }
        }

        @Override
        public void removeDataFromSet(String path, T data) {
            FakeSetRecord r =
                    ((FakeSetRecord) mRecords.computeIfAbsent(path, k -> new FakeSetRecord()));
            if (r.entries().remove(data)) {
                r.getMetadata().setLastModifiedByLocalDevice(mIsNextUpdateLocal);
            }
        }

        @Override
        public String getDocId() {
            return mId;
        }

        @Override
        public int getSchemaVersion() {
            return mSchemaVersion;
        }

        @Nullable
        @Override
        public Record<T> getRecord(String path) {
            return mRecords.get(path);
        }

        @Override
        public boolean containsPath(String path) {
            return mRecords.containsKey(path);
        }

        @Override
        public String getDebugStringForPath(String path) {
            return mId + "::" + path;
        }

        public Map<String, Object> getAll() {
            Map<String, Object> res = new HashMap<>();
            for (Map.Entry<String, Record<T>> entry : mRecords.entrySet()) {
                String path = entry.getKey();
                Record<T> r = entry.getValue();
                Object val;
                if (r instanceof UnmergedRecord<?> unmerged) {
                    val = unmerged.entries();
                } else if (r instanceof SetRecord<?> set) {
                    val = set.entries();
                } else {
                    val = r.get();
                }
                res.put(path, val);
            }
            return res;
        }

        /** Save the identity of the caller. */
        public FakeDocument withIdentity(boolean isLocal, String nodeId) {
            mIsNextUpdateLocal = isLocal;
            if (isLocal) {
                mNextUpdateNodeId = mSelfNodeId;
            } else {
                mNextUpdateNodeId = nodeId;
            }
            return this;
        }

        public void initWithSchema(DocumentSchemaInfo schema) {
            for (PathSchemaInfo pathSchema : schema.getPathSchema()) {
                switch (pathSchema.type()) {
                    case RecordType.TYPE_REGISTER ->
                            mRecords.put(pathSchema.path(), new FakeRecord());
                    case RecordType.TYPE_SET ->
                            mRecords.put(pathSchema.path(), new FakeSetRecord());
                    case RecordType.TYPE_UNMERGED ->
                            mRecords.put(pathSchema.path(), new FakeUnmergedRecord(mSelfNodeId));
                }
            }
        }

        @Override
        public String toString() {
            return "{id="
                    + mId
                    + ", schemaVersion="
                    + mSchemaVersion
                    + ", records="
                    + mRecords
                    + "}";
        }
    }

    private static class FakeMetadata implements Metadata {
        private boolean mIsLastModifiedByLocalDevice = true;

        FakeMetadata() {}

        FakeMetadata(DataInputStream dis) throws IOException {
            mIsLastModifiedByLocalDevice = dis.readBoolean();
        }

        @Override
        public boolean isLastModifiedByLocalDevice() {
            return mIsLastModifiedByLocalDevice;
        }

        public void setLastModifiedByLocalDevice(boolean val) {
            mIsLastModifiedByLocalDevice = val;
        }

        @Override
        public String toString() {
            return "{lastModifiedByLocalDevice=" + mIsLastModifiedByLocalDevice + "}";
        }

        public void write(DataOutputStream dos) throws IOException {
            dos.writeBoolean(mIsLastModifiedByLocalDevice);
        }
    }

    private static void writeByteArray(DataOutputStream dos, byte[] val) throws IOException {
        dos.writeInt(val.length);
        dos.write(val);
    }

    private static byte[] readByteArray(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        byte[] b = new byte[len];
        dis.readFully(b);
        return b;
    }

    private static void writeByteArrayList(DataOutputStream dos, List<byte[]> val)
            throws IOException {
        dos.writeInt(val.size());
        for (byte[] bytes : val) {
            writeByteArray(dos, bytes);
        }
    }

    private static List<byte[]> readByteArrayList(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        List<byte[]> res = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            res.add(readByteArray(dis));
        }
        return res;
    }

    private static void writeByteArrayMap(DataOutputStream dos, Map<String, byte[]> val)
            throws IOException {
        dos.writeInt(val.size());
        for (Map.Entry<String, byte[]> entry : val.entrySet()) {
            dos.writeUTF(entry.getKey());
            writeByteArray(dos, entry.getValue());
        }
    }

    private static Map<String, byte[]> readByteArrayMap(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        Map<String, byte[]> res = new HashMap<>();
        for (int i = 0; i < len; i++) {
            String key = dis.readUTF();
            byte[] val = readByteArray(dis);
            res.put(key, val);
        }
        return res;
    }
}
