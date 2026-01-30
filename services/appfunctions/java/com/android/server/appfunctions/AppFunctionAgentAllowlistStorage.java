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

package com.android.server.appfunctions;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.WorkerThread;
import android.content.pm.SignedPackage;
import android.util.AtomicFile;
import android.util.Slog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Handles reading and writing the App Function agent allowlist to persistent storage. */
public final class AppFunctionAgentAllowlistStorage {

    private static final String TAG = "AppFunctionAgentAllowlistStorage";

    @NonNull private final AtomicFile mAtomicFile;
    @NonNull private final Object mAllowlistStorageLock = new Object();

    /**
     * Creates an instance that manages the allowlist at the specified file path.
     *
     * @param file The file to read from and write to.
     */
    public AppFunctionAgentAllowlistStorage(@NonNull File file) {
        mAtomicFile = new AtomicFile(file);
    }

    /**
     * Reads and parses the allowlist from persistent storage.
     *
     * <p>This is a blocking operation and should be called on a worker thread.
     *
     * <p>If the file is found to be corrupt during reading or parsing, it will be deleted.
     *
     * @return A list of {@link SignedPackage}s if the file exists and is valid, or {@code null}
     */
    @WorkerThread
    @Nullable
    public List<SignedPackage> readPreviousValidAllowlist() {
        synchronized (mAllowlistStorageLock) {
            if (!mAtomicFile.exists()) {
                Slog.d(TAG, "Allowlist file does not exist.");
                return null;
            }

            try {
                byte[] data = mAtomicFile.readFully();
                String allowlistString = new String(data, StandardCharsets.UTF_8);
                return SignedPackageParser.parseList(allowlistString);
            } catch (Exception e) {
                Slog.e(TAG, "Error reading or parsing allowlist file", e);
                mAtomicFile.delete();
                return null;
            }
        }
    }

    /**
     * Writes the given allowlist string to persistent storage atomically.
     *
     * <p>This is a blocking operation and should be called on a worker thread.
     *
     * @param allowlistString The raw string representation of the allowlist to be written.
     */
    @WorkerThread
    public void writeCurrentAllowlist(@NonNull String allowlistString) {
        synchronized (mAllowlistStorageLock) {
            FileOutputStream fos = null;
            try {
                fos = mAtomicFile.startWrite();
                fos.write(allowlistString.getBytes(StandardCharsets.UTF_8));
                mAtomicFile.finishWrite(fos);
            } catch (IOException e) {
                Slog.e(TAG, "Error writing allowlist file", e);
                if (fos != null) {
                    mAtomicFile.failWrite(fos);
                }
            }
        }
    }
}
