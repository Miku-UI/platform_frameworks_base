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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.FileUtils;
import android.os.HandlerThread;
import android.os.storage.StorageManager;
import android.util.DataUnit;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.SystemService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * This is a system service that provides offloading of bitmaps to storage. The bitmaps
 * are stored in a private data location that is not accessible to any application.
 *
 * Each subsystem that uses the bitmap offloader must define a
 * {@link BitmapOffload.BitmapSource} value.
 *
 * Bitmaps can then be offloaded using the {@link BitmapOffloadInternal#offloadBitmap(int, Bitmap)}
 * method. The method returns a Uri that is valid for the lifetime of the system_server process.
 * Reboots or process restarts will result in removal of all Bitmaps.
 *
 * A permission callback must be provided for each {@link BitmapOffload.BitmapSource} using the
 * {@link BitmapOffloadInternal#registerPermissionHandler(int, BitmapOffloadInternal.PermissionHandler)}
 * API. Every time a Bitmap of the corresponding source is accessed, the registered callback will
 * be invoked to check permissions on said Bitmap.
 */
public class BitmapOffloadService extends SystemService {
    public static final String TAG = "BitmapOffloader";

    private static final String BITMAP_DIR = "offloaded-bitmaps";

    private final ContentResolver mResolver;

    @VisibleForTesting
    File mBitmapDir;

    private final long mLowBytes;

    @VisibleForTesting
    BitmapOffloadInternal mInternalService = new BitmapOffloadInternalImpl();

    @VisibleForTesting
    HandlerThread mOffloadThread;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<BitmapOffloadInternal.PermissionHandler>
            mPermissionHandlers = new SparseArray<>();

    public BitmapOffloadService(Context context) {
        super(context);

        mResolver = context.getContentResolver();
        mBitmapDir = new File(Environment.getDataSystemDirectory(), BITMAP_DIR);

        if (!mBitmapDir.exists()) {
            boolean created = mBitmapDir.mkdir();
            if (!created) {
                Slog.e(TAG, "Failed to create bitmap directory");
            }
        } else {
            // Remove any existing bitmaps
            FileUtils.deleteContents(mBitmapDir);
        }
        StorageManager storageManager = context.getSystemService(StorageManager.class);
        if (storageManager != null) {
            mLowBytes = storageManager.getStorageLowBytes(mBitmapDir);
        } else {
            // Should never happen, but let's have a valid default
            mLowBytes = DataUnit.MEBIBYTES.toBytes(256);
        }

        mOffloadThread = new HandlerThread("BitmapOffloadThread");
        mOffloadThread.start();
    }

    @Override
    public void onStart() {
        LocalServices.addService(BitmapOffloadInternal.class, mInternalService);
    }

    private class BitmapOffloadRunnable implements Runnable {
        private final String mPath;
        private final Bitmap mBitmap;
        private final Uri mUri;

        BitmapOffloadRunnable(Bitmap bitmap, String path, Uri uri) {
            mPath = path;
            mBitmap = bitmap;
            mUri = uri;
        }

        private boolean compressBitmap() {
            try (FileOutputStream out = new FileOutputStream(mPath)) {
                boolean success = mBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 100, out);
                if (!success) {
                    Slog.w(TAG, "Failed compressing bitmap to " + mPath);
                    return false;
                }
            } catch (IOException e) {
                Slog.w(TAG, "Failed compressing bitmap to " + mPath, e);
                return false;
            }

            return true;
        }

        @Override
        public void run() {
            ContentValues values = new ContentValues();
            if (compressBitmap()) {
                File bitmapFile = new File(mPath);
                long fileSize = bitmapFile.length();
                // Update the Uri with the file size and status
                values.put(BitmapOffloadContract.COLUMN_FILE_SIZE, fileSize);
                values.put(BitmapOffloadContract.COLUMN_STATUS,
                        BitmapOffloadContract.OFFLOAD_STATUS_COMPLETED);
            } else {
                values.put(BitmapOffloadContract.COLUMN_STATUS,
                        BitmapOffloadContract.OFFLOAD_STATUS_FAILED);
            }
            mResolver.update(mUri, values, null);
        }
    }

    public class BitmapOffloadInternalImpl extends BitmapOffloadInternal {
        private boolean hasFreeSpace() {
            return (mBitmapDir.getFreeSpace() > mLowBytes);
        }

        @Override
        public Uri offloadBitmap(@BitmapOffload.BitmapSource int source, Bitmap bitmap) {
            if (!hasFreeSpace()) {
                Slog.i(TAG, "Not offloading because storage space is limited.");
                return null;
            }
            String path = generateFilePath();

            ContentValues values = new ContentValues();
            values.put(BitmapOffloadContract.COLUMN_FILE_NAME, path);
            values.put(BitmapOffloadContract.COLUMN_WIDTH, bitmap.getWidth());
            values.put(BitmapOffloadContract.COLUMN_HEIGHT, bitmap.getHeight());
            values.put(BitmapOffloadContract.COLUMN_OWNER_UID, Binder.getCallingUid());
            values.put(BitmapOffloadContract.COLUMN_SOURCE, source);

            // Do the insert with our own identity, as only the system is allowed to insert
            final long token = Binder.clearCallingIdentity();
            try {
                Uri uri = mResolver.insert(BitmapOffloadContract.CONTENT_URI, values);
                if (uri != null) {
                    // Do the actual offload asynchronously
                    mOffloadThread.getThreadHandler().post(
                            new BitmapOffloadRunnable(bitmap, path, uri));
                }

                return uri;
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void registerPermissionHandler(@BitmapOffload.BitmapSource int source,
                PermissionHandler handler) {
            synchronized (mLock) {
                mPermissionHandlers.put(source, handler);
            }
        }

        @Override
        public boolean checkPermission(@BitmapOffload.BitmapSource int source, Uri uri,
                int callingUid, int owningUid) {
            PermissionHandler handler;
            synchronized (mLock) {
                handler = mPermissionHandlers.get(source);
            }
            if (handler == null) {
                throw new SecurityException("No permission handler registered for bitmap source.");
            }
            return handler.isAllowedToOpen(uri, callingUid, owningUid);
        }

        private String generateFilePath() {
            String fileName = UUID.randomUUID().toString();
            File path = new File(mBitmapDir, fileName);

            return path.getPath();
        }
    }
}
