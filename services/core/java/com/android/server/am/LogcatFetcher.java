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

package com.android.server.am;

import static com.android.server.am.ActivityManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.am.ActivityManagerDebugConfig.TAG_AM;

import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

final class LogcatFetcher {

    static final String TAG = TAG_WITH_CLASS_NAME ? "LogcatFetcher" : TAG_AM;

    // Assumes logcat entries average around 100 bytes; that's not perfect stack traces count
    // as one line, but close enough for now.
    static final int RESERVED_BYTES_PER_LOGCAT_LINE = 100;

    // How many seconds should the system wait before terminating the spawned logcat process.
    static final int LOGCAT_TIMEOUT_SEC = Flags.logcatLongerTimeout() ? 15 : 10;

    // The minimum allowed size for calling logcat, accounting for a reserved line and a header.
    private static final int MIN_LOGCAT_FILE_SIZE = 2 * RESERVED_BYTES_PER_LOGCAT_LINE;

    // The divisor used for the aggregated logs
    private static final String LOGCAT_AGGREGATED_DIVISOR = "--------- beginning of logcat";

    // The divisor prefix used to identify logcat divisors
    private static final String LOGCAT_DIVISOR_PREFIX = "---------";

    // List of logcat buffers to fetch from the Android system logs
    private static final List<String> CORE_BUFFERS =
            List.of("events", "system", "main", "crash");

    // List of full log buffers to fetch system kernel logs
    private static final List<String> ALL_BUFFERS =
            Stream.concat(CORE_BUFFERS.stream(), Stream.of("kernel")).toList();

    // Date formatter that is consistent with logcat's format
    private static final DateTimeFormatter LOGCAT_FORMATTER = DateTimeFormatter
            .ofPattern("MM-dd HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC);

    // Maximum number of log lines allowed to prevent potential OOM issues.
    private static final int MAX_ALLOWED_CORE_LOG_LINES = 8_000;

    /**
     * Retrieves and appends logcat output from certain buffers into the StringBuilder,
     * respecting a maximum buffer size and a timestamp. First, It first attempts to
     * include general logs, then kernel logs if space remains.
     *
     * @param sb             The StringBuilder where logs will be appended.
     * @param maxBufferSize  Maximum buffer size in characters.
     * @param maxTimestamp      The latest allowed log timestamp to include.
     * @param logcatLines    Number of logcat lines to fetch.
     * @param kernelLogLines Number of kernel log lines to fetch.
     */
    public static void appendLogcatLogs(StringBuilder sb, int maxBufferSize, Instant maxTimestamp,
            int logcatLines, int kernelLogLines) {

        String formattedTimestamp = LOGCAT_FORMATTER.format(maxTimestamp);
        int lines = Math.min(logcatLines + kernelLogLines, MAX_ALLOWED_CORE_LOG_LINES);
        // Check if we can at least include two lines (a header and a log line), otherwise,
        // we shouldn't call logcat
        if (lines > 0 && maxBufferSize >= MIN_LOGCAT_FILE_SIZE) {
            List<String> logs = fetchLogcatBuffers(lines, LOGCAT_TIMEOUT_SEC, formattedTimestamp,
                    kernelLogLines > 0  ? ALL_BUFFERS : CORE_BUFFERS);
            trimAndAppendLogs(sb, logs, maxBufferSize);
        }
    }

    /**
     * Fetches logcat logs from the specified buffers and returns them as a list of strings.
     *
     * @param lines   Number of lines to retrieve.
     * @param timeout Maximum time allowed for logcat to run (in seconds).
     * @param timestampUtc The latest allowed log timestamp to include, as a formatted string.
     * @param buffers List of log buffers to retrieve logs from.
     * @return A list of logcat output lines.
     */
    private static List<String> fetchLogcatBuffers(int lines, int timeout, String timestampUtc,
            List<String> buffers) {
        if (buffers.isEmpty() || lines <= 0 || timeout <= 0) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(lines);
        List<String> command = new ArrayList<>(10 + (2 * buffers.size()));
        // Time out after 10s of inactivity, but kill logcat with ABRT
        // so we can investigate why it didn't finish.
        command.add("/system/bin/timeout");
        command.add("-i");
        command.add("-s");
        command.add("ABRT");
        command.add(timeout + "s");

        // Merge several logcat streams, and take the last N lines.
        command.add("/system/bin/logcat");
        command.add("-v");
        // This adds a timestamp and thread info to each log line.
        // Also change the timestamps to use UTC time.
        command.add("threadtime,UTC");
        for (String buffer : buffers) {
            command.add("-b");
            command.add(buffer);
        }
        // Limit the output to the last N lines.
        command.add("-t");
        command.add(String.valueOf(lines));
        try {
            java.lang.Process proc =
                    new ProcessBuilder(command).redirectErrorStream(true).start();

            // Close the output stream immediately as we do not send input to the process.
            try {
                proc.getOutputStream().close();
            } catch (IOException e) {
            }
            // Read all lines from the child process
            try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(proc.getInputStream()), 8192)) {
                String line;
                while ((line = reader.readLine()) != null
                        // Only include logs up to the provided timestamp
                        && extractLogTimestamp(line).compareTo(timestampUtc) <= 0) {
                    // Skip divisors, as we don't care about buffer starting markers
                    if (!line.startsWith(LOGCAT_DIVISOR_PREFIX)) {
                        result.add(line);
                    }
                }
            }
        } catch (IOException e) {
            Slog.e(TAG, "Error running logcat", e);
        }

        return result;
    }

    /**
     * Extracts the timestamp from the beginning of a log line.
     * Assumes the timestamp is in the first 18 characters.
     * Returns the full line if it is shorter than 18 characters.
     *
     * @param line the log line to extract the timestamp from
     * @return the extracted timestamp or the original line if too short
     */
    @VisibleForTesting
    static String extractLogTimestamp(String line) {
        if (line.length() < 18) {
            // fallback if line is too short
            return line;
        }
        return line.substring(0, 18);
    }

    /**
     * Appends as many log lines as possible from the provided list into the StringBuilder,
     * without exceeding the specified maxBufferSize. The first element of the list
     * is treated as a special "header" or "divider" line and is always included if logs
     * are non-empty.
     *
     * @param sb            The StringBuilder to append logs into.
     * @param logs          The list of log lines (with logs.get(0) assumed to be a header).
     * @param maxBufferSize The maximum allowable size (in characters) to consume.
     * @return The total number of characters (including newlines) appended to the StringBuilder.
     */
    @VisibleForTesting
    static int trimAndAppendLogs(StringBuilder sb, List<String> logs, int maxBufferSize) {

        if (logs.isEmpty()) return 0;

        // Start from the last log entry and move backward to see what fits.
        int preStartIndex = logs.size() - 1;
        // Reserve space for the first line
        int logSize = LOGCAT_AGGREGATED_DIVISOR.length() + 1;

        // Calculate our starting point by moving backwards.
        while (preStartIndex >= 0
                && logSize + logs.get(preStartIndex).length() + 1 <= maxBufferSize) {
            logSize += logs.get(preStartIndex).length() + 1;
            preStartIndex--;
        }

        // If no logs were included, return 0
        if (preStartIndex == logs.size() - 1) return 0;

        // Append the header first
        sb.append(LOGCAT_AGGREGATED_DIVISOR).append("\n");
        // Then add the logs in the correct (non-decreasing) order
        for (int i = preStartIndex + 1; i < logs.size(); i++) {
            sb.append(logs.get(i)).append("\n");
        }

        return logSize;
    }

}
