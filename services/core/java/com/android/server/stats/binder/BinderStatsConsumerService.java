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

package com.android.server.stats.binder;

import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.content.Context;
import android.os.Binder;
import android.os.SystemClock;
import android.os.binder.BinderCallsStats;
import android.os.binder.BinderSpamStats;
import android.os.binder.IBinderStatsConsumerService;
import android.os.binder.SingleSecondBinderStats;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.signalcollector.SignalCollectorManagerInternal;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service that acts as a proxy to statsd for native binder stats.
 *
 * <p>The binder stats are collected in libbinder and sent to this service. This service then
 * forwards the stats to statsd via socket using {@link FrameworkStatsLog} class.
 *
 * @hide
 */
public class BinderStatsConsumerService extends IBinderStatsConsumerService.Stub {
    private static final String TAG = "BinderStatsConsumerService";
    private static final boolean DEBUG = false;
    private static final int MAX_STATS_BUFFER_SIZE = 6000;
    private static final long AGGREGATION_WINDOW_MS = 60_000; // 1 minute
    private static final int MAX_INCOMING_STATS_ARRAY_LENGTH = 256;
    private static final int MAX_INCOMING_STRING_LENGTH = 256;
    private static final long RATE_LIMIT_TIME_WINDOW_MS = 100;
    private static final int MAX_REPORTED_STATS_IN_TIME_WINDOW = 10;

    private static final int[] EMPTY_HISTOGRAM = new int[0];

    @GuardedBy("mFlushLock")
    private long mStartOfCurrentTimeWindowMillis = 0;
    @GuardedBy("mFlushLock")
    private int mReportingCapacityInCurrentTimeWindow = MAX_REPORTED_STATS_IN_TIME_WINDOW;

    // Map to buffer and aggregate stats
    private final ConcurrentHashMap<BinderStatsKey, AidlTargetStats> mStatsBuffer =
            new ConcurrentHashMap<>();
    // Entries are added to this queue when a new AidlTargetStats is created, which occurs the first
    // time stats for a particular (clientUid, callingUid, interface, method) tuple are received.
    // Entries are removed and processed after they have been in the queue for at least
    // AGGREGATION_WINDOW_MS.
    // For every BinderStatsReport in this queue, the corresponding BinderStatsKey and
    // AidlTargetStats must exist in mStatsBuffer.
    private final ConcurrentLinkedQueue<BinderStatsReport> mStatsQueue =
            new ConcurrentLinkedQueue<>();
    // Lock to synchronize flushing of stats.
    private final Object mFlushLock = new Object();
    // Tracks the number of entries in mStatsBuffer.
    // mStatsBufferCount is always equal to mStatsBuffer.size() and mStatsQueue.size().
    private AtomicInteger mStatsBufferCount = new AtomicInteger(0);

    @Nullable private volatile SignalCollectorManagerInternal mSignalCollectorManagerInternal;

    BinderStatsConsumerService() {
    }

    @Override
    @RequiresNoPermission
    public void reportCallStats(BinderCallsStats[] callStatsArray) {
        if (DEBUG) {
            Slog.d(TAG, "reportCallStats");
        }
        if (callStatsArray.length > MAX_INCOMING_STATS_ARRAY_LENGTH) {
            Slog.w(TAG,
                    "Too many call stats received, dropping stats. Count: "
                            + callStatsArray.length);
            return;
        }
        if (getSignalCollectorManagerInternal() != null) {
            getSignalCollectorManagerInternal().reportBinderStats(callStatsArray);
        }

        int callingUid = Binder.getCallingUid();
        for (BinderCallsStats callStats : callStatsArray) {
            if (callStats.interfaceDescriptor.length() > MAX_INCOMING_STRING_LENGTH
                    || callStats.aidlMethod.length() > MAX_INCOMING_STRING_LENGTH) {
                Slog.w(TAG, "Interface descriptor or AIDL method too long, dropping stats.");
                continue;
            }
            FrameworkStatsLog.write(FrameworkStatsLog.BINDER_CALLS_REPORTED, callStats.clientUid,
                    callingUid, callStats.interfaceDescriptor, callStats.aidlMethod,
                    callStats.callCount, callStats.durationSumMicros,
                    callStats.secondsWithAtLeast10Calls, callStats.secondsWithAtLeast50Calls,
                    callStats.callDurationSumSquaredMicros, callStats.cpuTimeCount,
                    callStats.cpuTimeSumMicros, callStats.cpuTimeSumSquaredMicros,
                    /* secondsWithAtLeast125Calls = */ 0,
                    /* secondsWithAtLeast250Calls = */ 0, EMPTY_HISTOGRAM);
        }
    }

    @Override
    @RequiresNoPermission
    public void reportSpamStats(BinderSpamStats[] spamStatsArray) {
        if (DEBUG) {
            Slog.d(TAG, "reportSpamStats");
        }
        if (spamStatsArray.length > MAX_INCOMING_STATS_ARRAY_LENGTH) {
            Slog.w(TAG,
                    "Too many spam stats received, dropping stats. Count: "
                            + spamStatsArray.length);
            return;
        }
        int callingUid = Binder.getCallingUid();
        for (BinderSpamStats spamStats : spamStatsArray) {
            if (spamStats.interfaceDescriptor.length() > MAX_INCOMING_STRING_LENGTH
                    || spamStats.aidlMethod.length() > MAX_INCOMING_STRING_LENGTH) {
                Slog.w(TAG, "Interface descriptor or AIDL method too long, dropping spam stats.");
                continue;
            }
            FrameworkStatsLog.write(FrameworkStatsLog.BINDER_SPAM_REPORTED, spamStats.clientUid,
                    callingUid, spamStats.interfaceDescriptor, spamStats.aidlMethod,
                    spamStats.secondsWithAtLeast125Calls, spamStats.secondsWithAtLeast250Calls);
        }
    }

    @Override
    @RequiresNoPermission
    public void reportSecondGranularityStats(SingleSecondBinderStats[] singleSecondStatsArray) {
        reportSecondGranularityStats(singleSecondStatsArray, SystemClock.elapsedRealtime());
    }

    @VisibleForTesting
    void reportSecondGranularityStats(
            SingleSecondBinderStats[] singleSecondStatsArray, long nowMillis) {
        if (DEBUG) {
            Slog.d(TAG,
                    "reportSecondGranularityStats called with " + singleSecondStatsArray.length
                            + " items");
        }
        if (singleSecondStatsArray.length > MAX_INCOMING_STATS_ARRAY_LENGTH) {
            Slog.wtf(TAG,
                    "Too many stats received in reportSecondGranularityStats. Count: "
                            + singleSecondStatsArray.length);
            return;
        }
        if (getSignalCollectorManagerInternal() != null) {
            getSignalCollectorManagerInternal().reportBinderStats(singleSecondStatsArray);
        }

        int callingUid = Binder.getCallingUid();

    outerLoop:
        for (SingleSecondBinderStats stats : singleSecondStatsArray) {
            if (stats.interfaceDescriptor == null || stats.aidlMethod == null) {
                Slog.wtf(TAG, "Received stats with null interface or method.");
                continue;
            }
            if (stats.interfaceDescriptor.length() > MAX_INCOMING_STRING_LENGTH
                    || stats.aidlMethod.length() > MAX_INCOMING_STRING_LENGTH) {
                Slog.wtf(TAG, "Interface descriptor or AIDL method too long.");
                continue;
            }
            if (stats.durationBinIndices.length > 0) {
                if (stats.durationBinIndices.length != stats.durationCount) {
                    Slog.wtf(TAG, "Invalid number of duration bin Indices.");
                    continue outerLoop;
                }
                for (byte x : stats.durationBinIndices) {
                    if (x < 0 || x > 99) {
                        Slog.wtf(TAG, "Invalid duration bin index.");
                        continue outerLoop;
                    }
                }
            }

            BinderStatsKey key = new BinderStatsKey(
                    stats.clientUid, callingUid, stats.interfaceDescriptor, stats.aidlMethod);

            // Drop stats if Buffer is full.
            if (!mStatsBuffer.containsKey(key)
                    && mStatsBufferCount.get() >= MAX_STATS_BUFFER_SIZE) {
                continue;
            }

            mStatsBuffer.compute(key, (k, v) -> {
                if (v == null) {
                    v = new AidlTargetStats(nowMillis);
                    mStatsQueue.add(new BinderStatsReport(k, v));
                    mStatsBufferCount.incrementAndGet();
                }
                v.add(stats);
                return v;
            });
        }
        maybeFlushOldValues(nowMillis);
    }

    @VisibleForTesting
    void maybeFlushOldValues(long nowMillis) {
        // Keep calling flushStat as long as it returns true.
        while (flushStat(nowMillis)) { /* Continue flushing */ }
    }

    private boolean flushStat(long nowMillis) {
        BinderStatsReport report = mStatsQueue.peek();
        if (report == null) {
            return false;
        }
        if (nowMillis - report.mStats.mCreationTimestampMs < AGGREGATION_WINDOW_MS) {
            return false;
        }
        synchronized (mFlushLock) {
            if (mStartOfCurrentTimeWindowMillis + RATE_LIMIT_TIME_WINDOW_MS <= nowMillis) {
                mStartOfCurrentTimeWindowMillis = nowMillis;
                mReportingCapacityInCurrentTimeWindow = MAX_REPORTED_STATS_IN_TIME_WINDOW;
            }

            if (mReportingCapacityInCurrentTimeWindow <= 0) {
                return false;
            }
            if (report == mStatsQueue.peek()) {
                mStatsQueue.poll();
                mReportingCapacityInCurrentTimeWindow--;
            } else {
                return false;
            }
        }
        if (mStatsBuffer.remove(report.mKey, report.mStats)) {
            reportStats(report.mKey, report.mStats);
            mStatsBufferCount.decrementAndGet();
        } else {
            Slog.wtf(TAG, "Failed to remove stats from buffer for key: " + report.mKey
                    + " and stats: " + report.mStats);
        }
        return true;
    }

    private void reportStats(BinderStatsKey key, AidlTargetStats aggregatedStats) {
        if (aggregatedStats.mSecondsWithAtLeast125Calls > 0
                || aggregatedStats.mSecondsWithAtLeast250Calls > 0) {
            FrameworkStatsLog.write(FrameworkStatsLog.BINDER_SPAM_REPORTED, key.clientUid(),
                    key.callingUid(), key.interfaceDescriptor(), key.aidlMethod(),
                    aggregatedStats.mSecondsWithAtLeast125Calls,
                    aggregatedStats.mSecondsWithAtLeast250Calls);
        }

        if (aggregatedStats.mDurationCount > 0) {
            FrameworkStatsLog.write(FrameworkStatsLog.BINDER_CALLS_REPORTED, key.clientUid(),
                    key.callingUid(), key.interfaceDescriptor(), key.aidlMethod(),
                    aggregatedStats.mCallCount, aggregatedStats.mDurationMicrosSum,
                    aggregatedStats.mSecondsWithAtLeast10Calls,
                    aggregatedStats.mSecondsWithAtLeast50Calls,
                    aggregatedStats.mDurationMicrosSquaredSum, aggregatedStats.mCpuTimeCount,
                    aggregatedStats.mCpuTimeMicrosSum, aggregatedStats.mCpuTimeMicrosSquaredSum,
                    aggregatedStats.mSecondsWithAtLeast125Calls,
                    aggregatedStats.mSecondsWithAtLeast250Calls,
                    aggregatedStats.mLatencyHistogram.getHistogram());
        }
    }

    /**
     * Get the {@link SignalCollectorManagerInternal} local service if it is available. This method
     * could return null if the local service not published yet, or the service is not enabled at
     * all.
     */
    @Nullable
    private SignalCollectorManagerInternal getSignalCollectorManagerInternal() {
        if (mSignalCollectorManagerInternal == null) {
            mSignalCollectorManagerInternal =
                    LocalServices.getService(SignalCollectorManagerInternal.class);
        }
        return mSignalCollectorManagerInternal;
    }

    /** Lifecycle and related code */
    public static final class Lifecycle extends SystemService {
        private BinderStatsConsumerService mService;

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            mService = new BinderStatsConsumerService();
            try {
                publishBinderService(Context.BINDER_STATS_CONSUMER_SERVICE, mService);
                if (DEBUG) {
                    Slog.d(TAG, "Published binder_stats_consumer service");
                }
            } catch (Exception e) {
                Slog.e(TAG, "Failed to publish binder_stats_consumer service", e);
            }
        }
    }

    private static class BinderStatsReport {
        final BinderStatsKey mKey;
        final AidlTargetStats mStats;

        BinderStatsReport(BinderStatsKey key, AidlTargetStats stats) {
            mKey = key;
            mStats = stats;
        }
    }

    // Key for the aggregation map
    private record BinderStatsKey(
            int clientUid, int callingUid, String interfaceDescriptor, String aidlMethod) {
        BinderStatsKey {
            Objects.requireNonNull(interfaceDescriptor);
            Objects.requireNonNull(aidlMethod);
        }
    }

    /**
     * Efficiently stores histogram counts. Uses a compact byte array for small sample
     * sizes (< 16) and upgrades to a full bucket array for larger datasets to save memory.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public static class OptimizedHistogram {
        // The fixed size of the final, uncompressed histogram. This must be kept in sync with the
        // native side (see frameworks/native/libs/binder/observer/Histogram.h).
        private static final int FULL_HISTOGRAM_SIZE = 100;
        private static final int SMALL_HISTOGRAM_REPRESENTATION_LENGTH = 16;

        private byte[] mSmallRepresentationIndices; // bucket indices or null if not used
        private int[] mFullHistogramCounts; // full buckets or null if not used
        private int mSize; // total number of samples

        /**
         * Adds samples to the histogram from the provided bucket indices.
         * All data points must be strictly lie between 0 and 100.
         */
        public void addSamplesToHistogram(byte[] bucketIndices) {
            if (bucketIndices.length == 0) {
                return;
            }

            if (mFullHistogramCounts == null
                    && mSize + bucketIndices.length <= SMALL_HISTOGRAM_REPRESENTATION_LENGTH) {
                if (mSmallRepresentationIndices == null) {
                    mSmallRepresentationIndices = new byte[SMALL_HISTOGRAM_REPRESENTATION_LENGTH];
                }
                for (byte bucketIndex : bucketIndices) {
                    mSmallRepresentationIndices[mSize++] = bucketIndex;
                }
            } else {
                if (mFullHistogramCounts == null) {
                    convertToFullHistogram();
                }
                incrementBuckets(bucketIndices, bucketIndices.length);
                mSize += bucketIndices.length;
            }
        }

        private void convertToFullHistogram() {
            mFullHistogramCounts = new int[FULL_HISTOGRAM_SIZE];
            if (mSmallRepresentationIndices != null) {
                incrementBuckets(mSmallRepresentationIndices, mSize);
                mSmallRepresentationIndices = null;
            }
        }

        private void incrementBuckets(byte[] bucketIndices, int length) {
            for (int i = 0; i < length; i++) {
                int index = bucketIndices[i];
                mFullHistogramCounts[index]++;
            }
        }

        /**
         * Returns an uncompressed histogram that can be sent statsD.
         */
        public int[] getHistogram() {
            if (mSize == 0) {
                return EMPTY_HISTOGRAM;
            }
            if (mFullHistogramCounts == null) {
                convertToFullHistogram();
            }
            return mFullHistogramCounts;
        }
    }

    // Mutable container for aggregated stats
    static class AidlTargetStats {
        final long mCreationTimestampMs;
        int mCallCount;
        int mDurationCount;
        long mDurationMicrosSum;
        long mDurationMicrosSquaredSum;
        int mCpuTimeCount;
        long mCpuTimeMicrosSum;
        long mCpuTimeMicrosSquaredSum;
        // For spam stats
        int mSecondsWithAtLeast125Calls;
        int mSecondsWithAtLeast250Calls;
        // For call stats
        int mSecondsWithAtLeast10Calls;
        int mSecondsWithAtLeast50Calls;
        OptimizedHistogram mLatencyHistogram = new OptimizedHistogram();

        AidlTargetStats(long creationTimestampMs) {
            mCreationTimestampMs = creationTimestampMs;
        }

        void add(SingleSecondBinderStats stats) {
            mCallCount += stats.callCount;
            mDurationCount += stats.durationCount;
            mDurationMicrosSum += stats.durationMicrosSum;
            mDurationMicrosSquaredSum += stats.durationMicrosSquaredSum;
            mCpuTimeCount += stats.cpuTimeCount;
            mCpuTimeMicrosSum += stats.cpuTimeMicrosSum;
            mCpuTimeMicrosSquaredSum += stats.cpuTimeMicrosSquaredSum;

            if (stats.callCount >= 125) {
                mSecondsWithAtLeast125Calls++;
            }
            if (stats.callCount >= 250) {
                mSecondsWithAtLeast250Calls++;
            }
            if (stats.durationCount >= 10) {
                mSecondsWithAtLeast10Calls++;
            }
            if (stats.durationCount >= 50) {
                mSecondsWithAtLeast50Calls++;
            }

            mLatencyHistogram.addSamplesToHistogram(stats.durationBinIndices);
        }
    }
}
