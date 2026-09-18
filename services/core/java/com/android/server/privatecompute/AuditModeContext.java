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

import android.annotation.NonNull;
import android.os.Binder;
import android.os.Environment;
import android.os.FileUtils;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.sysprop.PccProperties;
import android.util.Log;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;


/**
 * Manages the state of audit mode; when on, collects logs and periodically writes them to disk.
 * This class is thread-safe.
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
class AuditModeContext {
    private static final String TAG = "PccSandboxManagerServiceAuditMode";

    static final String AUDIT_LOG_FILES_DIRNAME = "audit_logs";

    // Max number of audit log files to keep on disk. When this limit is reached, old files will be
    // overwritten. Overridden by the system property `persist.pcc.audit_mode.max_log_files` if set.
    private static final int MAX_FILES = 10;

    // Max size in KB of a single audit log file. Overridden by the system property
    // `persist.pcc.audit_mode.log_file_max_size_kb` if set.
    private static final int MAX_SIZE_KILOBYTES = 10 * 1024; // 10 MB

    // We have two kind of background tasks: serializing to a byte array, then writing to disk.
    // They are isolated to avoid one kind of task starving the other.
    private final ExecutorService mBundleSerializerExecutor;
    private final ExecutorService mDiskWriterExecutor;

    // Time left to worker threads (both serializer and writer) to finish their tasks before being
    // stopped.
    private static final int SHUTDOWN_TIMEOUT_MS = 10000; // 10 seconds.

    // Number of threads to use for background tasks; this is used for both serialization and
    // writing to disk. The total number of threads is twice this number.
    private static final int N_THREADS = 2;

    private final File mFolder;
    private final AuditLogFileManager mAuditLogFileManager;
    private final Object mLock = new Object();
    private final Injector mInjector;

    // Once stopped, this can't be restarted. A new AuditModeContext should be created instead.
    @GuardedBy("mLock")
    private boolean mIsStopping = false;

    @GuardedBy("mLock")
    private AuditLogInMemoryBuffer mAuditLogInMemoryBuffer;

    final int mUserId;

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    AuditModeContext(
            int userId,
            ExecutorService serializerExecutor,
            ExecutorService diskWriterExecutor,
            File folder,
            Injector injector) {
        mUserId = userId;
        mBundleSerializerExecutor = serializerExecutor;
        mDiskWriterExecutor = diskWriterExecutor;
        mFolder = folder;
        mInjector = injector;
        mAuditLogFileManager = new AuditLogFileManager(mFolder, mInjector);
        mAuditLogInMemoryBuffer =
                new AuditLogInMemoryBuffer(
                        mAuditLogFileManager.rotateAndReturnNewFile(), mInjector);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static ExecutorService getBundleSerializerExecutorService() {
        return Executors.newFixedThreadPool(N_THREADS);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static ExecutorService getDiskWriterExecutorService() {
        return Executors.newFixedThreadPool(N_THREADS);
    }

    /**
     * Instantiates an AuditModeContext, including an output stream to the audit log file, or
     * returns null if an error occurred.
     */
    public static @NonNull AuditModeContext create(int userId, File folder) {
        return new AuditModeContext(
                userId,
                getBundleSerializerExecutorService(),
                getDiskWriterExecutorService(),
                folder,
                new Injector());
    }

    /** Deletes all audit log files from the user's audit log directory. */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static void deleteAuditLogFiles(File dir) {
        if (dir.exists()) {
            if (!FileUtils.deleteContentsAndDir(dir)) {
                Log.w(TAG, "Failed to delete audit log directory: " + dir.getAbsolutePath());
            }
        }
    }

    @VisibleForTesting
    static class Injector {
        /**
         * The maximum number of audit log files to keep on disk. When this limit is reached, old
         * files will be overwritten.
         */
        int auditModeMaxLogFiles() {
            int maxFiles = PccProperties.audit_mode_max_log_files().orElse(MAX_FILES);
            if (maxFiles <= 0) {
                return MAX_FILES;
            }
            return maxFiles;
        }

        /**
         * The maximum size of one audit log file in kB. When this limit is reached, the log file
         * will be written to disk.
         */
        int auditModeLogFileMaxSizeKb() {
            int maxSizeKB =
                    PccProperties.audit_mode_log_file_max_size_kb().orElse(MAX_SIZE_KILOBYTES);
            if (maxSizeKB <= 0) {
                return MAX_SIZE_KILOBYTES;
            }
            return maxSizeKB;
        }
    }

    /**
     * Writes data to the in-memory audit log, returning immediately.
     *
     * <p>If the in-memory queue is full, it is emptied and asynchronously written to disk.
     */
    void writeToAuditLog(@NonNull PersistableBundle data, @NonNull String packageName,
                         int callingUid) {
        synchronized (mLock) {
            if (mIsStopping) {
                return;
            }
        }
        if (UserHandle.getUserId(callingUid) != mUserId) {
            throw new SecurityException("Attempted to write to audit log from a different user.");
        }
        AuditLogEntry entry =
                new AuditLogEntry(
                        data,
                        SystemClock.elapsedRealtimeNanos(),
                        System.currentTimeMillis(),
                        packageName,
                        callingUid);
        mBundleSerializerExecutor.execute(
                () -> {
                    try {
                        serializeAndWrite(entry);
                    } catch (IOException | SecurityException e) {
                        Log.e(TAG, "Failed to write to audit log file: " + e);
                    }
                });
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    void serializeAndWrite(AuditLogEntry entry) throws IOException, SecurityException {
        try {
            PccBundleSanitizationUtil.sanitizeBundle(entry.getBundle());
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Failed to sanitize bundle: " + e.getMessage());
        }
        byte[] data = entry.toByteArray();
        synchronized (mLock) {
            if (mIsStopping) {
                return;
            }
            if (!mAuditLogInMemoryBuffer.add(data)) {
                try {
                    // Can't be added to this file, write it to disk and create a new one.
                    mDiskWriterExecutor.execute(mAuditLogInMemoryBuffer::writeToFile);
                } catch (RejectedExecutionException e) {
                    Log.i(TAG, "Executor is shutting down, dropping data. " + e);
                    return;
                }
                mAuditLogInMemoryBuffer =
                        new AuditLogInMemoryBuffer(
                                mAuditLogFileManager.rotateAndReturnNewFile(), mInjector);
                // New file, this will succeed.
                if (!mAuditLogInMemoryBuffer.add(data)) {
                    // Log just in case, but this should never happen.
                    Log.e(TAG, "Failed to write bundle to new in-memory buffer.");
                }
            }
        }
    }

    /** Stops audit mode, writing pending data to disk. */
    public void stopAuditing() {
        synchronized (mLock) {
            mIsStopping = true;
        }
        // Triggers write of the in-memory buffer to disk.
        mDiskWriterExecutor.execute(mAuditLogInMemoryBuffer::writeToFile);
        // Prevent the addition of new tasks.
        mBundleSerializerExecutor.shutdown();
        mDiskWriterExecutor.shutdown();
        try {
            if (!mBundleSerializerExecutor.awaitTermination(
                    SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                mBundleSerializerExecutor.shutdownNow();
            }
            if (!mDiskWriterExecutor.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                mDiskWriterExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to wait for executor to terminate", e);
            mBundleSerializerExecutor.shutdownNow();
            mDiskWriterExecutor.shutdownNow();
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    File getCurrentAuditLogFile() {
        synchronized (mLock) {
            return mAuditLogInMemoryBuffer.mAuditLogFile;
        }
    }

    /**
     * Returns all logs on disk for the given user. Logs are sorted by increasing timestamp. Skips
     * unreadable logs, if any.
     */
    public static List<AuditLogEntry> readAuditLogs(File folder, int userId) {
        List<AuditLogEntry> entries = readAuditLogs(folder);
        List<AuditLogEntry> filteredEntries = new ArrayList<>();
        for (AuditLogEntry entry : entries) {
            if (UserHandle.getUserId(entry.mCallingUid) == userId) {
                filteredEntries.add(entry);
            }
        }
        return filteredEntries;
    }

    /**
     * Reads all logs on disk for the given folder. Results are sorted by increasing timestamp.
     * Skips unreadable logs, if any.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static List<AuditLogEntry> readAuditLogs(File folder) {
        String prefix = AuditLogFileManager.AUDIT_LOG_FILE_PREFIX;
        String suffix = AuditLogFileManager.AUDIT_LOG_FILE_SUFFIX;
        File[] files =
                folder.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(suffix));
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }
        List<AuditLogEntry> entries = AuditLogFileWriter.readEntriesForFiles(Arrays.asList(files));
        entries.sort(Comparator.comparingLong(entry -> entry.mRealTimeNanos));
        return entries;
    }
}
