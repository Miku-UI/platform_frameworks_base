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

package com.android.server.companion.datasync;

import static com.android.server.companion.utils.DataStoreUtils.createStorageFileForUser;
import static com.android.server.companion.utils.DataStoreUtils.fileToByteArray;
import static com.android.server.companion.utils.DataStoreUtils.writeToFileSafely;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.os.PersistableBundle;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;

import com.android.internal.annotations.GuardedBy;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.xmlpull.v1.XmlPullParserException;

/**
 * This store manages the cache and disk data for data sync.
 *
 * Metadata is stored in a per-user file named "datasync_metadata.xml".
 * The file is stored in the device-encrypted storage. The file is created lazily when the metadata
 * is first set for a user.
 */
public class LocalMetadataStore {

    private static final String TAG = "CDM_LocalMetadataStore";
    private static final String FILE_NAME = "cdm_local_metadata.xml";
    private static final String ROOT_TAG = "bundle";
    private static final int READ_FROM_DISK_TIMEOUT = 5; // in seconds

    private final ExecutorService mExecutor;
    private final ConcurrentMap<Integer, AtomicFile> mUserIdToStorageFile =
            new ConcurrentHashMap<>();

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<PersistableBundle> mCachedPerUser = new SparseArray<>();

    public LocalMetadataStore() {
        mExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Set the metadata for a given user.
     */
    public void setMetadataForUser(@UserIdInt int userId, @NonNull PersistableBundle metadata) {
        Slog.i(TAG, "Setting metadata for user=[" + userId + "] value=" + metadata + "...");

        synchronized (mLock) {
            mCachedPerUser.put(userId, metadata);
        }

        mExecutor.execute(() -> writeMetadataToStore(userId, metadata));
    }

    /**
     * Read the metadata for a given user.
     */
    @NonNull
    public PersistableBundle getMetadataForUser(@UserIdInt int userId) {
        synchronized (mLock) {
            return readMetadataFromCache(userId).deepCopy();
        }
    }

    @GuardedBy("mLock")
    @NonNull
    private PersistableBundle readMetadataFromCache(@UserIdInt int userId) {
        PersistableBundle cachedMetadata = mCachedPerUser.get(userId);
        if (cachedMetadata == null) {
            Future<PersistableBundle> future =
                    mExecutor.submit(() -> readMetadataFromStore(userId));
            try {
                cachedMetadata = future.get(READ_FROM_DISK_TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Slog.e(TAG, "Thread reading metadata from disk is interrupted.", e);
            } catch (ExecutionException e) {
                Slog.e(TAG, "Error occurred while reading metadata from disk.", e);
            } catch (TimeoutException e) {
                Slog.e(TAG, "Reading metadata from disk timed out.", e);
            }
            if (cachedMetadata == null) {
                cachedMetadata = new PersistableBundle();
            }
            mCachedPerUser.put(userId, cachedMetadata);
        }
        return cachedMetadata;
    }

    private void writeMetadataToStore(@UserIdInt int userId, @NonNull PersistableBundle metadata) {
        final AtomicFile file = getStorageFileForUser(userId);
        Slog.i(TAG, "Writing metadata for user " + userId + " to file="
                + file.getBaseFile().getPath());

        synchronized (file) {
            writeToFileSafely(file, out -> {
                final TypedXmlSerializer serializer = Xml.resolveSerializer(out);
                serializer.startDocument(null, true);
                serializer.startTag(null, ROOT_TAG);
                metadata.saveToXml(serializer);
                serializer.endTag(null, ROOT_TAG);
                serializer.endDocument();
            });
        }
    }

    @NonNull
    private PersistableBundle readMetadataFromStore(@UserIdInt int userId) {
        final AtomicFile file = getStorageFileForUser(userId);
        Slog.i(TAG, "Reading metadata for user " + userId + " from "
                + "file=" + file.getBaseFile().getPath());

        synchronized (file) {
            if (!file.getBaseFile().exists()) {
                Slog.d(TAG, "File does not exist -> Abort");
                return new PersistableBundle();
            }
            try (FileInputStream in = file.openRead()) {
                return readMetadataFromInputStream(in);
            } catch (IOException e) {
                Slog.e(TAG, "Error while reading metadata file", e);
                return new PersistableBundle();
            }
        }
    }

    /**
     * Get the backup payload for a given user.
     */
    @NonNull
    public byte[] getBackupPayload(@UserIdInt int userId) {
        Slog.i(TAG, "Getting backup payload for user=[" + userId + "]");
        final AtomicFile file = getStorageFileForUser(userId);
        synchronized (file) {
            return fileToByteArray(file);
        }
    }

    @NonNull
    public PersistableBundle readMetadataFromPayload(@NonNull byte[] payload) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(payload)) {
            return readMetadataFromInputStream(in);
        } catch (IOException e) {
            Slog.w(TAG, "Error while reading metadata from payload.", e);
            return new PersistableBundle();
        }
    }

    @NonNull
    private PersistableBundle readMetadataFromInputStream(@NonNull InputStream in)
            throws IOException {
        try {
            final TypedXmlPullParser parser = Xml.resolvePullParser(in);
            parser.next();
            return PersistableBundle.restoreFromXml(parser);
        } catch (XmlPullParserException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    private AtomicFile getStorageFileForUser(@UserIdInt int userId) {
        return mUserIdToStorageFile.computeIfAbsent(userId,
                u -> createStorageFileForUser(userId, FILE_NAME));
    }
}
