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

package com.android.server.bitmapoffload;

import android.annotation.NonNull;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.server.LocalServices;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ContentProvider responsible for managing and providing access to bitmaps offloaded to disk
 * by the {@link BitmapOffloadService}.
 *
 * <p>This provider stores metadata associated with each offloaded bitmap, such as its source,
 * owning UID, dimensions, and file path. It handles requests to access the bitmap data via
 * {@link #openAssetFile(Uri, String)}, ensuring that the calling UID has the necessary permissions
 * by checking with the {@link BitmapOffloadInternal} service.</p>
 *
 * <p>The provider also manages the state of the offload process for each bitmap (pending,
 * completed, failed). The {@link BitmapOffloadService} uses {@link #insert(Uri, ContentValues)}
 * to register a new bitmap being offloaded and
 * {@link #update(Uri, ContentValues, String, String[])} to update its status and file size upon
 * completion or failure of the compression process.</p>
 *
 * <p>Access to insert, update, and delete operations is restricted to the system process.</p>
 *
 */
public class BitmapOffloadProvider extends ContentProvider {
    public static final String MIME_TYPE = "image/webp";
    private static final String TAG = "BitmapOffloadProvider";

    private static final int MAX_OFFLOAD_WAIT_MS = 500;

    private BitmapOffloadInternal mBitmapOffloadService;

    private static class BitmapEntry {
        final BitmapData mBitmapData;

        AtomicLong mSize;
        AtomicInteger mNumOpens;

        @GuardedBy("this")
        @BitmapOffloadContract.OffloadStatus int mStatus;

        private BitmapEntry(BitmapData bitmapData) {
            mBitmapData = bitmapData;
            mStatus = BitmapOffloadContract.OFFLOAD_STATUS_PENDING;
            mSize = new AtomicLong(0);
            mNumOpens = new AtomicInteger(0);
        }

        /**
         * Waits for completion of the bitmap being offloaded. This may take some
         * time, depending on disk I/O and the compression algorithm. Waits for
         * at most {@link #MAX_OFFLOAD_WAIT_MS} milliseconds.
         *
         * @return true if the bitmap was successfully offloaded, false otherwise
         */
        public boolean waitForCompletion() {
            synchronized (this) {
                while (mStatus == BitmapOffloadContract.OFFLOAD_STATUS_PENDING) {
                    try {
                        this.wait(MAX_OFFLOAD_WAIT_MS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return mStatus == BitmapOffloadContract.OFFLOAD_STATUS_COMPLETED;
            }
        }

        @Override
        public String toString() {
            return mBitmapData + ", file size: " + mSize + ", opens: " + mNumOpens;
        }

        public void updateStatus(int status) {
            synchronized (this) {
                mStatus = status;
                this.notifyAll();
            }
        }
    }

    private record BitmapData(int source, int ownerUid, int width, int height, String filePath) {
        @Override
        public String toString() {
            return width + "x" + height + " path " + filePath + " Owner UID: " + ownerUid;
        }
    }

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final HashMap<Uri, BitmapEntry> mEntries = new HashMap<>();

    private final AtomicInteger mId = new AtomicInteger();

    @Override
    public boolean onCreate() {
        return true;
    }

    private void enforcePermissions(Uri uri, BitmapData bitmapData, int callingUid) {
        if (mBitmapOffloadService == null) {
            mBitmapOffloadService = LocalServices.getService(BitmapOffloadInternal.class);
        }
        boolean allowed = mBitmapOffloadService.checkPermission(bitmapData.source, uri, callingUid,
                bitmapData.ownerUid);
        if (!allowed) {
            throw new SecurityException("Access to offloaded bitmap " + uri + " not allowed.");
        }
    }

    private void enforceCallerSystem() {
        // Only local calls are allowed, so using PID is safe here
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        if (Process.myUid() != callingUid || Process.myPid() != callingPid) {
            throw new SecurityException("Only local calls are allowed");
        }
    }

    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        BitmapEntry entry;
        final BitmapData bitmapData;
        synchronized (mLock) {
            entry = mEntries.get(uri);
            if (entry == null) {
                Slog.w(TAG, "Could not retrieve bitmap for " + uri);
                return null;
            }
            bitmapData = entry.mBitmapData;
        }

        final int callingUid = Binder.getCallingUid();
        enforcePermissions(uri, bitmapData, callingUid);

        if (!entry.waitForCompletion()) {
            return null;
        }

        entry.mNumOpens.incrementAndGet();

        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                new File(bitmapData.filePath), ParcelFileDescriptor.parseMode(mode));
        return new AssetFileDescriptor(pfd, 0, -1);
    }


    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return MIME_TYPE;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        enforceCallerSystem();

        if (values == null) {
            throw new IllegalArgumentException("values can't be null");
        }

        Uri itemUri = ContentUris.withAppendedId(BitmapOffloadContract.CONTENT_URI,
                mId.incrementAndGet());

        String filePath = values.getAsString(BitmapOffloadContract.COLUMN_FILE_NAME);
        int width = values.getAsInteger(BitmapOffloadContract.COLUMN_WIDTH);
        int height = values.getAsInteger(BitmapOffloadContract.COLUMN_HEIGHT);
        int ownerUid = values.getAsInteger(BitmapOffloadContract.COLUMN_OWNER_UID);
        int source = values.getAsInteger(BitmapOffloadContract.COLUMN_SOURCE);

        BitmapEntry entry = new BitmapEntry(
                new BitmapData(source, ownerUid, width, height, filePath));
        synchronized (mLock) {
            mEntries.put(itemUri, entry);
        }

        return itemUri;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        enforceCallerSystem();

        synchronized (mLock) {
            BitmapEntry entry = mEntries.remove(uri);
            return entry != null ? 1 : 0;
        }
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        enforceCallerSystem();

        if (values == null) {
            throw new IllegalArgumentException("values can't be null");
        }

        final BitmapEntry entry;
        synchronized (mLock) {
            entry = mEntries.get(uri);
            if (entry == null) {
                Slog.w(TAG, "Could not retrieve bitmap for " + uri);
                return 0;
            }
            int status = values.getAsInteger(BitmapOffloadContract.COLUMN_STATUS);
            if (values.containsKey(BitmapOffloadContract.COLUMN_FILE_SIZE)) {
                entry.mSize.set(values.getAsLong(BitmapOffloadContract.COLUMN_FILE_SIZE));
            }
            entry.updateStatus(status);
        }
        return 0;
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(fd, writer, args);
        writer.println("Bitmap Offload Cache stats:");
        int i = 0;
        synchronized (mLock) {
            for (Uri uri : mEntries.keySet()) {
                BitmapEntry entry = mEntries.get(uri);
                if (entry != null) {
                    writer.println("  #" + i + ": " + entry);
                }
                i++;
            }
        }
    }
}
