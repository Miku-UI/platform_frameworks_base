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

package com.android.server.privatecompute;

import android.util.Log;
import com.android.internal.annotations.VisibleForTesting;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a list of serialized AuditLogEntry to a file. If the file already exists, it is
 * overwritten. {@link #writeEntries} is synchronous and meant to be executed in a background
 * thread. Prepends the version number to the file, to allow for format changes in the future.
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
class AuditLogFileWriter {
    private static final String TAG = "PccSandboxManagerServiceAuditMode.AuditLogFileWriter";

    // Open file for writing, creating it if it doesn't exist, or truncating it if it does.
    private static final StandardOpenOption[] OPEN_OPTIONS =
            new StandardOpenOption[] {
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            };

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static final int AUDIT_FILE_FORMAT_VERSION = 0;

    private final File mFile;

    AuditLogFileWriter(File file) {
        mFile = file;
    }

    /**
     * Writes a list of AuditLogEntry to a file. If the file already exists, it is overwritten.
     *
     * <p>If the current thread is interrupted, the method stops early and returns; this is to allow
     * an ExecutorService to stop all of its threads somewhat gracefully.
     *
     * @param entries The list of AuditLogEntry to write.
     * @throws IOException If the parent directory cannot be created, or if there's an error writing
     *     to the file.
     */
    void writeEntries(List<byte[]> entries) throws IOException {
        File parent = mFile.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Unable to create directory for audit file: " + parent);
            }
        }

        try (DataOutputStream outputStream =
                new DataOutputStream(
                        new BufferedOutputStream(
                                Files.newOutputStream(mFile.toPath(), OPEN_OPTIONS)))) {
            outputStream.writeInt(AUDIT_FILE_FORMAT_VERSION);
            for (byte[] entry : entries) {
                if (Thread.currentThread().isInterrupted()) {
                    Log.w(TAG, "writeBundlesToStream was interrupted. Exiting.");
                    return;
                }
                outputStream.write(entry);
            }
            outputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write audit log to disk", e);
        }
    }

    /** Reads all entries from an audit log file. Only version 0 is supported at the moment. */
    static List<AuditLogEntry> readEntries(File file) throws IOException {
        List<AuditLogEntry> entries = new ArrayList<>();
        try (DataInputStream input =
                new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            try {
                int version = input.readInt();
                if (version != AUDIT_FILE_FORMAT_VERSION) {
                    Log.w(TAG, "Unknown version: " + version);
                    return entries;
                }
            } catch (EOFException e) {
                return entries;
            }

            while (true) {
                try {
                    entries.add(AuditLogEntry.readFromStream(input));
                } catch (EOFException e) {
                    break;
                }
            }
        }
        return entries;
    }

    /** Reads all entries from a list of audit log files, skipping any files that cannot be read. */
    static List<AuditLogEntry> readEntriesForFiles(List<File> files) {
        List<AuditLogEntry> entries = new ArrayList<>();
        for (File file : files) {
            try {
                entries.addAll(readEntries(file));
            } catch (IOException e) {
                Log.e(TAG, "Failed to read audit log from file: " + file.getName(), e);
            }
        }
        return entries;
    }
}
