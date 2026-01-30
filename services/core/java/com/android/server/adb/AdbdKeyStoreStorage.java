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
package com.android.server.adb;

import android.annotation.NonNull;
import android.os.FileUtils;
import android.util.AtomicFile;
import android.util.Slog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * This class handles the adbd plain-text keystore format. The keys are stored in two files:
 *
 * <ol>
 *   <li>/adb_keys: stores system keys that are permanently trusted, and can't be modified by user.
 *   <li>/data/misc/adb/adb_keys: stores user keys that are allowed to connect to the device, and
 *       can be modified by user.
 * </ol>
 */
class AdbdKeyStoreStorage {
    private static final String TAG = AdbdKeyStoreStorage.class.getSimpleName();

    private final File mTextFile;

    /**
     * Constructs a new storage manager for the given text file.
     *
     * @param textFile The file to read from and write to. Must not be null.
     */
    AdbdKeyStoreStorage(@NonNull File textFile) {
        Objects.requireNonNull(textFile, "textFile must not be null");
        this.mTextFile = textFile;
    }

    /**
     * Loads a set of ADB keys from the plain-text file.
     *
     * @return A {@link Set} of public key strings. Returns an empty set if the file does not exist
     *     or an error occurs.
     */
    Set<String> loadKeys() {
        Set<String> keys = new HashSet<>();
        if (!mTextFile.exists()) {
            return keys;
        }

        try (BufferedReader in = new BufferedReader(new FileReader(mTextFile))) {
            String key;
            while ((key = in.readLine()) != null) {
                key = key.trim();
                if (!key.isEmpty()) {
                    keys.add(key);
                }
            }
        } catch (IOException e) {
            Slog.e(TAG, "Caught an exception reading " + mTextFile, e);
        }
        return keys;
    }

    /**
     * Saves a set of ADB keys to the plain-text file, overwriting existing content.
     *
     * @param keys The {@link Set} of public key strings to save.
     */
    void saveKeys(@NonNull Set<String> keys) {
        Objects.requireNonNull(keys, "keys set must not be null");

        AtomicFile atomicKeyFile = new AtomicFile(mTextFile);
        // Note: Do not use a try-with-resources with the FileOutputStream, because AtomicFile
        // requires that it's cleaned up with AtomicFile.failWrite();
        FileOutputStream fo = null;
        try {
            fo = atomicKeyFile.startWrite();
            for (String key : keys) {
                fo.write(key.getBytes());
                fo.write('\n');
            }
            atomicKeyFile.finishWrite(fo);
        } catch (IOException ex) {
            Slog.e(TAG, "Error writing keys to " + atomicKeyFile.getBaseFile(), ex);
            if (fo != null) {
                atomicKeyFile.failWrite(fo);
            }
            return;
        }

        FileUtils.setPermissions(
                atomicKeyFile.getBaseFile().toString(),
                FileUtils.S_IRUSR | FileUtils.S_IWUSR | FileUtils.S_IRGRP,
                -1,
                -1);
    }

    /** Deletes the underlying text key file. */
    void delete() {
        if (mTextFile.exists()) {
            mTextFile.delete();
        }
    }
}
