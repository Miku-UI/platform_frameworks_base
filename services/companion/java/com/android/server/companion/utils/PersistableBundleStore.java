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

package com.android.server.companion.utils;

import static com.android.server.companion.utils.DataStoreUtils.createStorageFileForUser;
import static com.android.server.companion.utils.DataStoreUtils.writeToFileSafely;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.os.PersistableBundle;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class PersistableBundleStore {

    protected abstract String getTag();

    protected abstract String getFileName();

    protected final Object mLock = new Object();

    private static final int READ_FROM_DISK_TIMEOUT = 5; // in seconds

    private final boolean mCredentialsEncrypted; // Device-Encrypted if false

    private final ExecutorService mExecutor;

    private final ConcurrentMap<Integer, AtomicFile> mUserIdToStorageFile =
            new ConcurrentHashMap<>();

    @GuardedBy("mLock")
    protected final SparseArray<PersistableBundle> mCachedPerUser = new SparseArray<>();

    protected PersistableBundleStore() {
        this(false);
    }

    protected PersistableBundleStore(boolean credentialsEncrypted) {
        mCredentialsEncrypted = credentialsEncrypted;
        mExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Read data from cache.
     * Note: The return is a deep copy of the data instead of the reference in the cache.
     */
    @NonNull
    public PersistableBundle readData(@UserIdInt int userId) {
        synchronized (mLock) {
            return readDataFromCacheLocked(userId).deepCopy();
        }
    }

    /** Write data to cache and disk. */
    public void writeData(@UserIdInt int userId, @NonNull PersistableBundle data) {
        Slog.i(getTag(),
                "Setting data for user=[" + userId + "] num_entries=[" + data.size() + "]...");

        synchronized (mLock) {
            mCachedPerUser.put(userId, data);
            mExecutor.execute(() -> writeDataToDisk(userId, data));
        }
    }

    /** Get the backup payload of the store. */
    @NonNull
    public byte[] getBackupPayload(@UserIdInt int userId) {
        Slog.i(getTag(), "Getting backup payload for user=[" + userId + "]");
        final AtomicFile file = getStorageFileForUser(userId);
        synchronized (file) {
            try {
                return file.exists() ? file.readFully() : new byte[0];
            } catch (IOException e) {
                Slog.e(getTag(), "Error reading data from disk.", e);
                return new byte[0];
            }
        }
    }

    /**
     * Restore data from the given payload.
     * Note: If the key exists, the current value wins and the payload value will be skipped.
     * otherwise the key-value pair is added.
     */
    public void restoreFromPayload(@UserIdInt int userId, @NonNull byte[] payload) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(payload)) {
            PersistableBundle restoredData = PersistableBundle.readFromStream(in);
            PersistableBundle currentData = readData(userId);
            restoredData.putAll(currentData);
            writeData(userId, restoredData);
        } catch (IOException e) {
            Slog.w(getTag(), "Error while restoring data from payload.", e);
        }
    }

    @GuardedBy("mLock")
    @NonNull
    private PersistableBundle readDataFromCacheLocked(@UserIdInt int userId) {
        PersistableBundle cachedMetadata = mCachedPerUser.get(userId);
        if (cachedMetadata == null) {
            Future<PersistableBundle> future =
                    mExecutor.submit(() -> readDataFromDisk(userId));
            try {
                cachedMetadata = future.get(READ_FROM_DISK_TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Slog.e(getTag(), "Thread reading data from disk is interrupted.", e);
            } catch (ExecutionException e) {
                Slog.e(getTag(), "Error occurred while reading data from disk.", e);
            } catch (TimeoutException e) {
                Slog.e(getTag(), "Reading data from disk timed out.", e);
            }
            if (cachedMetadata == null) {
                cachedMetadata = new PersistableBundle();
            }
            mCachedPerUser.put(userId, cachedMetadata);
        }
        return cachedMetadata;
    }

    @NonNull
    private PersistableBundle readDataFromDisk(@UserIdInt int userId) {
        final AtomicFile file = getStorageFileForUser(userId);
        Slog.d(getTag(), "Reading data for user " + userId + " from "
                + "file=" + file.getBaseFile().getPath());

        synchronized (file) {
            if (!file.getBaseFile().exists()) {
                Slog.d(getTag(), "File does not exist -> Abort");
                return new PersistableBundle();
            }
            try (FileInputStream in = file.openRead()) {
                return PersistableBundle.readFromStream(in);
            } catch (IOException e) {
                Slog.e(getTag(), "Error while reading file", e);
                return new PersistableBundle();
            }
        }
    }

    private void writeDataToDisk(@UserIdInt int userId, @NonNull PersistableBundle data) {
        final AtomicFile file = getStorageFileForUser(userId);
        Slog.d(getTag(), "Writing data for user " + userId + " to file="
                + file.getBaseFile().getPath());

        synchronized (file) {
            writeToFileSafely(file, data::writeToStream);
        }
    }

    @NonNull
    private AtomicFile getStorageFileForUser(@UserIdInt int userId) {
        return mUserIdToStorageFile.computeIfAbsent(userId,
                u -> createStorageFileForUser(userId, getFileName(), !mCredentialsEncrypted));
    }
}
