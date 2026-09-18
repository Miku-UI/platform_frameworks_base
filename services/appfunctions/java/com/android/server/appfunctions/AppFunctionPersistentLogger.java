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

package com.android.server.appfunctions;

import android.annotation.WorkerThread;
import android.os.Handler;
import android.util.Slog;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * A logger for AppFunction system services that persists logs to disk and enforces storage limits
 * using a rotating file strategy.
 *
 * <p>Strategy:
 *
 * <ol>
 *   <li>Writes to "app_functions.log".
 *   <li>When the file exceeds MAX_FILE_SIZE, it rotates: log.1 -> log.2, log -> log.1, etc.,
 *       keeping up to {@link #MAX_FILE_COUNT} archived logs.
 *   <li>The oldest file is deleted to maintain the storage cap. At most, there will be {@code
 *       MAX_FILE_COUNT + 1} log files (one active, the rest archived).
 *   <li>Dumps are streamed line-by-line to avoid RAM spikes.
 * </ol>
 */
public class AppFunctionPersistentLogger {
    private static final String TAG = "AppFunctionPersistentLogger";

    // Configuration: 400KB per file, 5 total files (1 active + 4 archived) = 2MB total cap
    static final long MAX_FILE_SIZE_BYTES = 400 * 1024;
    static final int MAX_FILE_COUNT = 4;
    // Configuration: 7 days TTL for log files from the last modified time.
    static final long LOG_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L;
    static final long CLEANUP_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L;

    private final File mLogDir;
    private final DateTimeFormatter mDateFormat;
    private final String mBaseLogFileName;
    private final Handler mBackgroundHandler;

    private final Object mLock = new Object();

    private final Runnable mCleanupTask =
            new Runnable() {
                @Override
                public void run() {
                    pruneOldLogs(LOG_TTL_MILLIS);
                    // Schedule next run
                    mBackgroundHandler.postDelayed(this, CLEANUP_INTERVAL_MILLIS);
                }
            };

    /**
     * @param baseDir The directory to store logs (e.g., /data/system_ce/0/appfunctions)
     * @param baseLogFileName The base name of the log file (e.g., app_functions_execution.log). The
     *     file will be rotated and archived as log.1, log.2, etc.
     */
    AppFunctionPersistentLogger(File baseDir, String baseLogFileName) {
        this(baseDir, baseLogFileName, BackgroundThread.getHandler());
    }

    @VisibleForTesting
    AppFunctionPersistentLogger(File baseDir, String baseLogFileName, Handler backgroundHandler) {
        mLogDir = baseDir;
        mDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        mBaseLogFileName = baseLogFileName;
        mBackgroundHandler = backgroundHandler;

        // Start the maintenance loop
        mBackgroundHandler.post(mCleanupTask);
    }

    /**
     * Logs a message with a timestamp to the persistent log file. Rotates files if the message
     * would exceed the size limit.
     */
    @WorkerThread
    public void log(String message) {
        synchronized (mLock) {
            if (!mLogDir.exists() && !mLogDir.mkdirs()) {
                Slog.e(TAG, "Failed to create log directory: " + mLogDir);
                return;
            }
            File currentLogFile = new File(mLogDir, mBaseLogFileName);

            // Truncate massive messages to avoid a single entry from exceeding the file size limit.
            // This is an approximation as we're comparing chars to bytes, but it's acceptable if we
            // exceed the limit slightly.
            if (message.length() > MAX_FILE_SIZE_BYTES) {
                String truncationMsg = " ... [TRUNCATED]";
                int cutPoint = (int) MAX_FILE_SIZE_BYTES - truncationMsg.length();
                message = message.substring(0, Math.max(0, cutPoint)) + truncationMsg;
            }

            // Prepare the content
            String timestamp = LocalDateTime.now().format(mDateFormat);
            String logEntry = timestamp + " : " + message + "\n";
            long entrySize = logEntry.getBytes().length;

            // If adding this message would push the file over the limit, rotate now.
            if (currentLogFile.exists()
                    && (currentLogFile.length() + entrySize > MAX_FILE_SIZE_BYTES)) {
                rotateLogs();
            }

            try (FileWriter fw = new FileWriter(currentLogFile, /* append= */ true)) {
                fw.write(logEntry);
            } catch (IOException e) {
                // Fallback to system log to ensure visibility of the failure
                Slog.e(TAG, "Failed to write to AppFunction log", e);
            }
        }
    }

    /**
     * Dumps the logs to the PrintWriter.
     *
     * <p>Iterates files from oldest (.N) to newest (current) and streams them line-by-line to avoid
     * high RAM usage.
     */
    public void dump(PrintWriter pw) {
        synchronized (mLock) {
            pw.println("--- AppFunction Logs (Disk) ---");
            pw.println("Dir: " + mLogDir.getAbsolutePath());

            // Iterate from oldest archive to newest
            for (int i = MAX_FILE_COUNT; i >= 1; i--) {
                File archive = getArchivedLogFile(i);
                dumpFile(archive, pw);
            }

            // Dump current file last
            File current = new File(mLogDir, mBaseLogFileName);
            dumpFile(current, pw);

            pw.println("-------------------------------");
        }
    }

    /**
     * Helper to read a file line-by-line and print it. Does NOT load the whole file into memory.
     */
    private void dumpFile(File file, PrintWriter pw) {
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                pw.println(line);
            }
        } catch (IOException e) {
            pw.println("[Error reading file " + file.getName() + ": " + e.getMessage() + "]");
        }
    }

    /** Rotates logs: .n -> .n+1, ..., .1 -> .2, current -> .1 */
    private void rotateLogs() {
        if (!mLogDir.exists()) {
            Slog.w(TAG, "Log directory does not exist, skipping rotation.");
            return;
        }

        // Delete the oldest file if it exists
        File oldestFile = getArchivedLogFile(MAX_FILE_COUNT);
        if (oldestFile.exists()) {
            if (!oldestFile.delete()) {
                Slog.w(TAG, "Failed to delete oldest log: " + oldestFile);
            }
        }

        // Shift existing archives (e.g., log.4 -> log.5, log.3 -> log.4)
        for (int i = MAX_FILE_COUNT - 1; i >= 1; i--) {
            File from = getArchivedLogFile(i);
            File to = getArchivedLogFile(i + 1);
            if (from.exists()) {
                if (!from.renameTo(to)) {
                    Slog.w(TAG, "Failed to rename " + from + " to " + to);
                }
            }
        }

        // Rename current log to .1
        File current = new File(mLogDir, mBaseLogFileName);
        File archive1 = getArchivedLogFile(1);
        if (current.exists()) {
            if (!current.renameTo(archive1)) {
                Slog.w(TAG, "Failed to archive current log");
            }
        }
    }

    /** Returns the File object for an archived log file. */
    private File getArchivedLogFile(int index) {
        return new File(mLogDir, mBaseLogFileName + "." + index);
    }

    /** Deletes log files older than the specified time-to-live. */
    private void pruneOldLogs(long ttlMillis) {
        if (!mLogDir.exists()) return;

        synchronized (mLock) {
            File[] files = mLogDir.listFiles();
            if (files == null) return;

            long now = System.currentTimeMillis();
            for (File file : files) {
                if (file.getName().startsWith(mBaseLogFileName)) {
                    if (now - file.lastModified() > ttlMillis) {
                        if (!file.delete()) {
                            Slog.w(TAG, "Failed to delete expired log: " + file.getName());
                        }
                    }
                }
            }
        }
    }

    /** Stops the periodic log cleanup task. */
    public void close() {
        mBackgroundHandler.removeCallbacks(mCleanupTask);
    }
}
