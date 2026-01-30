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
package com.android.internal.os;

import static android.os.Process.PROC_OUT_LONG;

import android.annotation.Nullable;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class MemcgProcMemoryUtil {

    private static final String TAG = "MemcgProcMemoryUtil";

    private static final int[] MEMCG_MEMORY_FORMAT = new int[] {PROC_OUT_LONG};
    private static final String CGROUP_ROOT = "/sys/fs/cgroup";
    private static final String PROC_ROOT = "/proc/";

    private MemcgProcMemoryUtil() {}

     /**
      * Reads memcg accounting of memory stats of a process.
      *
      * Returns values of memory.current, memory.swap in bytes or null if not available.
      */
    public static MemcgMemorySnapshot readMemcgMemorySnapshot(int pid) {
        String cgroupPath = getCgroupPathForPid(pid);
        if (cgroupPath == null) {
            return null;
        }
        return readMemcgMemorySnapshot(cgroupPath);
    }

    /**
      *
      * Reads memcg accounting of memory hwm stats of a process.
      *
      * Returns values of memory.current.peak, memory.swap.peak in bytes or null if not available.
     */
    public static MemcgHighWaterMarkMemorySnapshot readHighWaterMarkMemorySnapshot(
            int pid
    ) {
        String cgroupPath = getCgroupPathForPid(pid);
        if (cgroupPath == null) {
            return null;
        }
        return readMemcgHighWaterMarkMemorySnapshot(cgroupPath);
    }

    /**
     * Gets the cgroup v2 path for a given process ID (pid).
     *
     * @param pid The process ID.
     * @return The cgroup v2 path string, or null if not found or an error occurs.
     */
    @Nullable
    private static String getCgroupPathForPid(int pid) {
        Path cgroupPathFile = Paths.get(PROC_ROOT, String.valueOf(pid), "cgroup");

        try (BufferedReader reader = Files.newBufferedReader(cgroupPathFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("0::")) {
                    return line.substring(3);
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "Failed to read cgroup file for pid " + pid, e);
        }
        return null;
    }

    private static MemcgHighWaterMarkMemorySnapshot
            readMemcgHighWaterMarkMemorySnapshot(String cgroupPath) {
        Path fullMemcgPath = Paths.get(CGROUP_ROOT, cgroupPath);

        final MemcgHighWaterMarkMemorySnapshot snapshot = new MemcgHighWaterMarkMemorySnapshot();
        Long memoryPeak = readSingleValueFromMemcgFile(
                fullMemcgPath,
                "memory.peak",
                MEMCG_MEMORY_FORMAT
        );
        if (memoryPeak == null) {
            return null;
        }

        Long swapMemoryPeak = readSingleValueFromMemcgFile(
                fullMemcgPath,
                "memory.swap.peak",
                MEMCG_MEMORY_FORMAT
        );
        if (swapMemoryPeak == null) {
            return null;
        }

        snapshot.memcgMemoryPeakInBytes = memoryPeak;
        snapshot.memcgSwapMemoryPeakInBytes = swapMemoryPeak;
        return snapshot;
    }

    private static MemcgMemorySnapshot readMemcgMemorySnapshot(String cgroupPath) {
        Path fullMemcgPath = Paths.get(CGROUP_ROOT, cgroupPath);

        final MemcgMemorySnapshot snapshot = new MemcgMemorySnapshot();

        long[] currentMemoryOutput = new long[1];
        String memoryCurrentPath = fullMemcgPath.resolve("memory.current").toString();
        if (Process.readProcFile(
                memoryCurrentPath,
                MEMCG_MEMORY_FORMAT,
                null,
                currentMemoryOutput,
                null
        )) {
            snapshot.memcgMemoryInBytes = currentMemoryOutput[0];
        } else {
            Log.d(TAG, "Failed to read memory.current for " + cgroupPath);
            return null;
        }

        long[] currentSwapMemoryOutput = new long[1];
        String memorySwapPath =
                fullMemcgPath.resolve("memory.swap.current").toString();
        if (Process.readProcFile(
                memorySwapPath,
                MEMCG_MEMORY_FORMAT,
                null,
                currentSwapMemoryOutput,
                null
        )) {
            snapshot.memcgSwapMemoryInBytes = currentSwapMemoryOutput[0];
        } else {
            Log.d(TAG, "Failed to read memory.current for " + cgroupPath);
            return null;
        }
        return snapshot;
    }

    private static Long readSingleValueFromMemcgFile(
            Path fullMemcgPath, String fileKey, int[] fileFormat) {
        long[] memoryValue = new long[1];
        String memoryNodePath = fullMemcgPath.resolve(fileKey).toString();
        if (Process.readProcFile(
                memoryNodePath,
                fileFormat,
                null,
                memoryValue,
                null
        )) {
            return memoryValue[0];
        } else {
            return null;
        }
    }

    public static final class MemcgHighWaterMarkMemorySnapshot {
        public long memcgMemoryPeakInBytes;
        public long memcgSwapMemoryPeakInBytes;
    }

    public static final class MemcgMemorySnapshot {
        public long memcgMemoryInBytes;
        public long memcgSwapMemoryInBytes;
    }
}
