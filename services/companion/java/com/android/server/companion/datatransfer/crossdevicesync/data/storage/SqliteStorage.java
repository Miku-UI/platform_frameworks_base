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

package com.android.server.companion.datatransfer.crossdevicesync.data.storage;

import android.annotation.Nullable;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.android.internal.infra.AndroidFuture;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;
import com.android.server.companion.datatransfer.crossdevicesync.common.Utils;

import com.google.android.submerge.StorageInterface.StorageException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Implementation of {@link IStorage} using SQLite. */
public class SqliteStorage implements IStorage {
    private static final String TAG = "SqliteStorage";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_NAME = "Document";
    private static final String COLUMN_DOC_ID = "docId";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_SCHEMA = "schema";
    private static final String COLUMN_METADATA = "metadata";

    private static final ThreadLocal<Boolean> IS_IO_THREAD = ThreadLocal.withInitial(() -> false);

    private final Context mContext;
    private final String mName;

    @Nullable private DatabaseHelper mDbHelper;
    @Nullable private SQLiteDatabase mDatabase;
    @Nullable private volatile ExecutorService mIoThreadExecutorService;

    public SqliteStorage(Context context, String name) {
        mContext = context.createDeviceProtectedStorageContext();
        mName = name;
    }

    @Override
    public void open() {
        requireIoThread();
        if (mDatabase != null) {
            return;
        }
        mDbHelper = new DatabaseHelper(mContext, mName);
        mDatabase = mDbHelper.getWritableDatabase();
        Log.i(TAG, "Database \"" + mName + "\" is open!");
    }

    @Override
    @Nullable
    public byte[] getDocument(String docId) throws StorageException {
        return getBlob(docId, COLUMN_CONTENT);
    }

    @Override
    public void persistDocument(String docId, byte[] document) throws StorageException {
        persistBlob(docId, COLUMN_CONTENT, document);
    }

    @Override
    @Nullable
    public byte[] getDocumentSchema(String docId) throws StorageException {
        return getBlob(docId, COLUMN_SCHEMA);
    }

    @Override
    public void persistDocumentSchema(String docId, byte[] documentSchema) throws StorageException {
        persistBlob(docId, COLUMN_SCHEMA, documentSchema);
    }

    @Override
    @Nullable
    public byte[] getMetadata(String docId) throws StorageException {
        return getBlob(docId, COLUMN_METADATA);
    }

    @Override
    public void persistMetadata(String docId, byte[] metadata) throws StorageException {
        persistBlob(docId, COLUMN_METADATA, metadata);
    }

    @Nullable
    private byte[] getBlob(String docId, String columnName) throws StorageException {
        requireIoThread();
        try (Cursor cursor =
                mDatabase.query(
                        TABLE_NAME,
                        new String[] {columnName},
                        COLUMN_DOC_ID + "=?",
                        new String[] {docId},
                        null,
                        null,
                        null)) {
            if (cursor.moveToFirst()) {
                return cursor.getBlob(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "getBlob " + columnName + " failed!", e);
            throw new StorageException();
        }
        return null;
    }

    private void persistBlob(String docId, String columnName, byte[] blob) throws StorageException {
        transact(
                storage -> {
                    ContentValues values = new ContentValues();
                    values.put(columnName, blob);
                    int rows =
                            mDatabase.update(
                                    TABLE_NAME, values, COLUMN_DOC_ID + "=?", new String[] {docId});
                    if (rows == 0) {
                        values.put(COLUMN_DOC_ID, docId);
                        mDatabase.insert(TABLE_NAME, null, values);
                    }
                });
    }

    @Override
    public void transact(ThrowingConsumer<IStorage> transactionApplier) throws StorageException {
        requireIoThread();
        if (mDatabase == null) {
            throw new StorageException();
        }
        mDatabase.beginTransaction();
        try {
            transactionApplier.acceptOrThrow(this);
            mDatabase.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Failed database transaction!", e);
            throw new StorageException();
        } finally {
            mDatabase.endTransaction();
        }
    }

    @Override
    public List<String> getAllDocumentIds() throws StorageException {
        requireIoThread();
        List<String> docIds = new ArrayList<>();
        try (Cursor cursor =
                mDatabase.query(
                        TABLE_NAME, new String[] {COLUMN_DOC_ID}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                docIds.add(cursor.getString(0));
            }
        } catch (Exception e) {
            Log.e(TAG, "getAllDocumentIds failed!", e);
            throw new StorageException();
        }
        return docIds;
    }

    @Override
    public void deleteDocuments(List<String> docIds) throws StorageException {
        requireIoThread();
        if (docIds.isEmpty()) {
            return;
        }
        transact(
                storage -> {
                    for (int i = 0; i < docIds.size(); i += 999) {
                        int end = Math.min(i + 999, docIds.size());
                        List<String> subList = docIds.subList(i, end);
                        StringBuilder whereClause = new StringBuilder(COLUMN_DOC_ID + " IN (");
                        String[] whereArgs = new String[subList.size()];
                        for (int j = 0; j < subList.size(); j++) {
                            whereClause.append("?");
                            if (j < subList.size() - 1) {
                                whereClause.append(",");
                            }
                            whereArgs[j] = subList.get(j);
                        }
                        whereClause.append(")");
                        mDatabase.delete(TABLE_NAME, whereClause.toString(), whereArgs);
                    }
                });
    }

    @Override
    public void close() {
        requireIoThread();
        if (mDbHelper == null) {
            return;
        }
        mDbHelper.close();
        mDatabase = null;
        mDbHelper = null;
        Log.i(TAG, "Database \"" + mName + "\" is closed!");
    }

    @Override
    public boolean deleteDatabase() {
        requireIoThread();
        close();
        if (mContext.deleteDatabase(mName)) {
            Log.i(TAG, "deleteDatabase: database \"" + mName + "\" is deleted!");
            return true;
        }
        return false;
    }

    @Override
    public <R> AndroidFuture<R> submitToIoThread(Callable<R> task) {
        ExecutorService ioExecutorService = getOrCreateIoThreadExecutor();
        if (ioExecutorService.isShutdown()) {
            return Utils.failedAndroidFuture(new IllegalStateException("IO thread is shutdown!"));
        }
        return AndroidFuture.supplyAsync(
                () -> {
                    try {
                        return task.call();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                },
                ioExecutorService);
    }

    @Override
    public void shutdownIoThread() {
        ExecutorService ioExecutorService = mIoThreadExecutorService;
        if (ioExecutorService != null) {
            ioExecutorService.shutdown();
        }
    }

    private ExecutorService getOrCreateIoThreadExecutor() {
        if (mIoThreadExecutorService != null) {
            return mIoThreadExecutorService;
        }
        synchronized (this) {
            if (mIoThreadExecutorService == null) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> IS_IO_THREAD.set(true));
                mIoThreadExecutorService = executor;
            }
        }
        return mIoThreadExecutorService;
    }

    private static void requireIoThread() {
        if (!IS_IO_THREAD.get()) {
            throw new IllegalThreadStateException("Not in IO thread!");
        }
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context, String name) {
            super(context, name, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE "
                            + TABLE_NAME
                            + " ("
                            + COLUMN_DOC_ID
                            + " TEXT PRIMARY KEY NOT NULL, "
                            + COLUMN_CONTENT
                            + " BLOB, "
                            + COLUMN_SCHEMA
                            + " BLOB, "
                            + COLUMN_METADATA
                            + " BLOB"
                            + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // No upgrades yet
        }
    }
}
