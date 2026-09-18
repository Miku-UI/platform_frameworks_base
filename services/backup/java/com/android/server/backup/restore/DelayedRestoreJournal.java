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

package com.android.server.backup.restore;

import com.android.internal.annotations.GuardedBy;

import android.annotation.Nullable;
import android.app.backup.DelayedRestoreRequest;
import android.util.AtomicFile;
import android.util.Slog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A journal implementation to persistently log delayed restore requests.
 *
 * <p>This class is responsible for maintaining a record of delayed restore requests that need to be
 * executed at a later time. The journal ensures that these requests survive system reboots.
 *
 * <p><strong>Persistence Mechanism:</strong>
 *
 * <p>The journal uses {@link AtomicFile} to ensure that file writes are atomic. This prevents data
 * corruption in case of a system crash during a write operation. The entire state of the journal is
 * rewritten to disk whenever a modification is made.
 *
 * <p><strong>Encoding Format:</strong>
 *
 * <p>The data is serialized in a custom binary format using {@link DataOutputStream}. This format
 * is compact and efficient, avoiding the overhead of text-based formats like XML or JSON.
 *
 * <p><strong>Usage:</strong>
 *
 * <p>This class is primarily used by the backup system to keep track of {@link
 * DelayedRestoreRequest}s that cannot be satisfied immediately.
 *
 * <p>This class is thread-safe. All public methods are synchronized on an internal lock.
 *
 * @hide
 */
public class DelayedRestoreJournal {
    private static final String TAG = "DelayedRestoreJournal";
    private static final String JOURNAL_FILENAME = "delayed-restore-journal";

    private final Object mLock = new Object();
    private boolean mLoaded = false;
    private final AtomicFile mJournalFile;

    @GuardedBy("mLock")
    private final Map<DelayedRestoreRequest, Set<String>> mRequestsToPackages = new HashMap<>();

    /**
     * Initializes the journal with the given directory.
     *
     * @param journalDirectory The directory where the journal file should be stored.
     */
    public DelayedRestoreJournal(File journalDirectory) {
        mJournalFile = new AtomicFile(new File(journalDirectory, JOURNAL_FILENAME));
    }

    /**
     * Persists a delayed restore request to the journal.
     *
     * <p>This method adds the request to the in-memory state and immediately triggers a disk write
     * to persist the updated journal. If the disk write fails, the in-memory state is reverted to
     * ensure consistency.
     *
     * @param request The delayed restore request.
     * @param requesterPackageName The package name of the app requesting the delayed restore.
     * @return {@code true} if the journal was successfully updated.
     */
    public boolean addRequest(DelayedRestoreRequest request, String requesterPackageName) {
        synchronized (mLock) {
            if (!ensureJournalReadyLocked()) {
                return false;
            }
            Set<String> requesters =
                    mRequestsToPackages.computeIfAbsent(request, k -> new HashSet<>());
            boolean changed = requesters.add(requesterPackageName);
            if (changed) {
                try {
                    writeToJournalLocked();
                } catch (IOException e) {
                    Slog.e(TAG, "Failed to write to journal", e);
                    requesters.remove(requesterPackageName);
                    if (requesters.isEmpty()) {
                        mRequestsToPackages.remove(request);
                    }
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Removes all delayed restore requests by the package.
     *
     * <p>This is typically called when a package is uninstalled or its cached data is cleared,
     * requiring any pending restore requests for it to be cancelled.
     *
     * <p>This method updates the in-memory state and immediately triggers a disk write to persist
     * the changes. If the disk write fails, the in-memory state is reverted.
     *
     * @param packageName The package name whose requests should be cleared.
     * @return {@code true} if the journal was successfully updated.
     */
    public boolean clearAllRequestsForPackage(String packageName) {
        synchronized (mLock) {
            if (!ensureJournalReadyLocked()) {
                return false;
            }

            // Create a deep copy to restore in case of write failure
            Map<DelayedRestoreRequest, Set<String>> originalMap = new HashMap<>();
            for (Map.Entry<DelayedRestoreRequest, Set<String>> entry :
                    mRequestsToPackages.entrySet()) {
                originalMap.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }

            boolean changed = false;
            for (Set<String> requesters : mRequestsToPackages.values()) {
                if (requesters.remove(packageName)) {
                    changed = true;
                }
            }
            mRequestsToPackages.values().removeIf(Set::isEmpty);

            if (changed) {
                try {
                    writeToJournalLocked();
                } catch (IOException e) {
                    Slog.e(TAG, "Failed to write to journal", e);
                    // Revert changes
                    mRequestsToPackages.clear();
                    mRequestsToPackages.putAll(originalMap);
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Removes a delayed restore request from the journal.
     *
     * <p>This method removes the request from the in-memory state and immediately triggers a disk
     * write to persist the updated journal. If the disk write fails, the in-memory state is
     * reverted to ensure consistency.
     *
     * @param request The delayed restore request.
     * @param requesterPackageName The package name of the app which made the request.
     * @return {@code true} if the journal was successfully updated.
     */
    public boolean removeRequest(DelayedRestoreRequest request, String requesterPackageName) {
        synchronized (mLock) {
            if (!ensureJournalReadyLocked()) {
                return false;
            }
            boolean changed = false;
            Set<String> requesters = mRequestsToPackages.get(request);
            if (requesters != null) {
                if (requesters.remove(requesterPackageName)) {
                    changed = true;
                }
            }
            mRequestsToPackages.values().removeIf(Set::isEmpty);
            if (changed) {
                try {
                    writeToJournalLocked();
                } catch (IOException e) {
                    Slog.e(TAG, "Failed to write to journal", e);
                    if (requesters.isEmpty()) {
                        mRequestsToPackages.put(request, requesters);
                    }
                    requesters.add(requesterPackageName);
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Retrieves all the packages which made the specified delayed restore request.
     *
     * <p>The journal organizes requests based on the {@link DelayedRestoreRequest} object. This
     * method allows retrieving all packages that have requested a delayed restore with the
     * specified request.
     *
     * @param request The {@link DelayedRestoreRequest} to look up.
     * @return A set of package names waiting for this request. Returns {@code null} if there was an
     *     error reading the journal. Returns an empty set if no requests are found.
     */
    @Nullable
    public Set<String> getPackagesForRequest(DelayedRestoreRequest request) {
        synchronized (mLock) {
            if (!ensureJournalReadyLocked()) {
                return null;
            }
            Set<String> requesters = mRequestsToPackages.get(request);
            if (requesters == null) {
                return new HashSet<>();
            }
            return new HashSet<>(requesters);
        }
    }

    /**
     * Dumps the state of the journal to the provided printer.
     *
     * <p>This method prints the loaded status, the number of requests, and the details of each
     * request currently in the journal.
     *
     * @param pw The {@link PrintWriter} to write the dump to.
     */
    public void dump(PrintWriter pw) {
        synchronized (mLock) {
            pw.println(TAG + " state:");
            pw.println("  Loaded: " + mLoaded);
            pw.println("  Requests: " + mRequestsToPackages.size());
            for (Map.Entry<DelayedRestoreRequest, Set<String>> entry :
                    mRequestsToPackages.entrySet()) {
                DelayedRestoreRequest key = entry.getKey();
                Set<String> requesters = entry.getValue();
                pw.println(
                        "    Request: "
                                + key.getPackageName()
                                + " type: "
                                + typeToString(key.getType()));
                pw.println("      Requesters Packages: " + requesters.size());
                for (String requester : requesters) {
                    pw.println("        " + requester);
                }
            }
        }
    }

    private String typeToString(int type) {
        return switch (type) {
            case DelayedRestoreRequest.TYPE_APP_INSTALL -> "TYPE_APP_INSTALL";
            case DelayedRestoreRequest.TYPE_APP_UPDATE -> "TYPE_APP_UPDATE";
            case DelayedRestoreRequest.TYPE_SETUP_COMPLETE -> "TYPE_SETUP_COMPLETE";
            case DelayedRestoreRequest.TYPE_MANAGED_PROFILE_PROVISIONED ->
                    "TYPE_MANAGED_PROFILE_PROVISIONED";
            default -> "UNKNOWN";
        };
    }

    @GuardedBy("mLock")
    private boolean ensureJournalReadyLocked() {
        if (!mLoaded) {
            if (!loadFromDiskLocked()) {
                return false;
            }
            mLoaded = true;
        }
        return true;
    }

    @GuardedBy("mLock")
    private void writeToJournalLocked() throws IOException {
        FileOutputStream stream = null;
        try {
            stream = mJournalFile.startWrite();
            DataOutputStream out = new DataOutputStream(stream);
            for (Map.Entry<DelayedRestoreRequest, Set<String>> entry :
                    mRequestsToPackages.entrySet()) {
                DelayedRestoreRequest key = entry.getKey();
                Set<String> requesters = entry.getValue();

                out.writeBoolean(key.getPackageName() != null);
                if (key.getPackageName() != null) {
                    out.writeUTF(key.getPackageName());
                }
                out.writeInt(key.getType());
                out.writeInt(requesters.size());
                for (String requester : requesters) {
                    out.writeUTF(requester);
                }
            }
            out.flush();
            mJournalFile.finishWrite(stream);
        } catch (IOException e) {
            if (stream != null) {
                mJournalFile.failWrite(stream);
            }
            throw e;
        }
    }

    @GuardedBy("mLock")
    private boolean loadFromDiskLocked() {
        mRequestsToPackages.clear();

        if (!mJournalFile.exists()) {
            return true;
        }

        try (DataInputStream in = new DataInputStream(mJournalFile.openRead())) {
            while (true) {
                String packageName = null;
                boolean hasPackageName;
                try {
                    hasPackageName = in.readBoolean();
                } catch (EOFException e) {
                    // We reached the end of the file cleanly.
                    break;
                }

                if (hasPackageName) {
                    packageName = in.readUTF();
                }

                int type = in.readInt();
                DelayedRestoreRequest.Builder builder = new DelayedRestoreRequest.Builder(type);
                if (packageName != null) {
                    builder.setPackageName(packageName);
                }
                DelayedRestoreRequest key = builder.build();

                int numRequesters = in.readInt();
                Set<String> requesters = new HashSet<>(numRequesters);
                for (int i = 0; i < numRequesters; i++) {
                    requesters.add(in.readUTF());
                }
                mRequestsToPackages.put(key, requesters);
            }
        } catch (IOException e) {
            Slog.e(TAG, "Failed to read journal", e);
            return false;
        }
        return true;
    }
}
