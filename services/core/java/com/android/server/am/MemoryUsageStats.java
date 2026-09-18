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

import android.os.Debug;

import com.android.internal.util.MemInfoReader;

/**
 * A helper class that holds data that is useful for calculating system memory
 * usage.
 */
public final class MemoryUsageStats {
    long nativePss;
    long nativeSwapPss;
    long nativeRss;
    long nativePrivateDirty;
    long dalvikPss;
    long dalvikSwapPss;
    long dalvikRss;
    long dalvikPrivateDirty;
    long otherPss;
    long otherSwapPss;
    long otherRss;
    long otherPrivateDirty;
    long totalPss;
    long totalSwapPss;
    long totalRss;
    long totalPrivateDirty;
    long totalNativePss;
    long totalMemtrackGraphics;
    long totalMemtrackGl;
    long cachedPss;
    long cachedSwapPss;
    private long mDmabufMapped;

    long totalMappedBitmapCount;
    long totalMappedBitmapSize;

    public MemoryUsageStats() {
        // Cache this value since it's expensive to calculate.
        mDmabufMapped = Debug.getDmabufMappedSizeKb();
    }

    public long getDmabufMappedSizeKb() {
        return mDmabufMapped;
    }

    public long getKernelUsedSizeKb(MemInfoReader memInfo) {
        long kernelUsedKb = memInfo.getKernelUsedSizeKb();
        final long totalExportedDmabuf = Debug.getDmabufTotalExportedKb();
        if (totalExportedDmabuf >= 0) {
            kernelUsedKb += totalExportedDmabuf - mDmabufMapped;
        }

        return kernelUsedKb;
    }

    private long getTotalPss() {
        long val = totalPss;
        if (mDmabufMapped > 0) {
            // Note: mapped DMA-BUF memory is not accounted in PSS due to VM_PFNMAP flag being
            // set on those VMAs. However it might be included by the memtrack HAL.
            // Replace memtrack HAL reported Graphics category with mapped dmabufs.
            val -= totalMemtrackGraphics;
            val += mDmabufMapped;
        }

        if (Debug.getGpuTotalUsageKb() >= 0) {
            final long gpuPrivateUsage = Debug.getGpuPrivateMemoryKb();
            if (gpuPrivateUsage >= 0) {
                // private GPU allocations include memtrack GL category, and are already
                // accounted as part of the kernel memory used, so subtract it from total
                // pss to avoid double counting.
                val -= totalMemtrackGl;
            }
        }

        return val;
    }

    public long getUsedPss() {
        return getTotalPss() - cachedPss;
    }

    public long getLostRam(MemInfoReader memInfo) {
        // Subtract totalSwapPss since we're only interested in memory that is actually resident
        // in RAM.
        return memInfo.getTotalSizeKb() - (getTotalPss() - totalSwapPss)
               // NR_SHMEM is subtracted twice (getCachedSizeKb() and getKernelUsedSizeKb())
               - memInfo.getFreeSizeKb() - memInfo.getCachedSizeKb() + memInfo.getShmemSizeKb()
               - getKernelUsedSizeKb(memInfo) - memInfo.getZramTotalSizeKb();
    }
}
