/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.stats.pull;

import static android.net.NetworkStats.METERED_YES;
import static android.net.NetworkTemplate.MATCH_MOBILE;
import static android.provider.Settings.Global.NETSTATS_UID_BUCKET_DURATION;

import static com.android.server.stats.Flags.useNetworkStatsQuerySummary;
import static com.android.server.stats.pull.netstats.NetworkStatsUtils.fromPublicNetworkStats;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.StatsManager;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.NetworkStats;
import android.net.NetworkTemplate;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.Trace;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.StatsEvent;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.selinux.RateLimiter;
import com.android.server.stats.pull.netstats.NetworkStatsAccumulator;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Aggregates Mobile Data Usage by process state per uid
 */
class AggregatedMobileDataStatsPuller {
    private static final String TAG = "AggregatedMobileDataStatsPuller";

    private static final boolean DEBUG = false;

    private static final long NETSTATS_UID_DEFAULT_BUCKET_DURATION_MS = HOURS.toMillis(2);

    private static class UidProcState {

        private final int mUid;
        private final int mState;

        UidProcState(int uid, int state) {
            mUid = uid;
            mState = state;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof UidProcState key)) {
                return false;
            }
            return mUid == key.mUid && mState == key.mState;
        }

        @Override
        public int hashCode() {
            int result = mUid;
            result = 31 * result + mState;
            return result;
        }

        public int getUid() {
            return mUid;
        }

        public int getState() {
            return mState;
        }
    }

    private static class MobileDataStats {
        private long mRxPackets = 0;
        private long mTxPackets = 0;
        private long mRxBytes = 0;
        private long mTxBytes = 0;

        public long getRxPackets() {
            return mRxPackets;
        }

        public long getTxPackets() {
            return mTxPackets;
        }

        public long getRxBytes() {
            return mRxBytes;
        }

        public long getTxBytes() {
            return mTxBytes;
        }

        public void addRxPackets(long rxPackets) {
            mRxPackets += rxPackets;
        }

        public void addTxPackets(long txPackets) {
            mTxPackets += txPackets;
        }

        public void addRxBytes(long rxBytes) {
            mRxBytes += rxBytes;
        }

        public void addTxBytes(long txBytes) {
            mTxBytes += txBytes;
        }

        public boolean isEmpty() {
            return mRxPackets == 0 && mTxPackets == 0 && mRxBytes == 0 && mTxBytes == 0;
        }
    }

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final Map<UidProcState, MobileDataStats> mUidStats;

    // No reason to keep more dimensions than 3000. The 3000 is the hard top for the statsd metrics
    // dimensions guardrail. It also will keep the result binder transaction size capped to
    private static final int UID_STATS_MAX_SIZE = 3000;

    private final SparseIntArray mUidPreviousState;

    private NetworkStats mLastMobileUidStats = new NetworkStats(0, -1);

    private final NetworkStatsManager mNetworkStatsManager;

    private final Handler mMobileDataStatsHandler;

    private final RateLimiter mRateLimiter;

    private final Context mContext;

    private final NetworkStatsAccumulator mStatsAccumulator;

    /**
     * Polling NetworkStats is a heavy operation and it should be done sparingly. Atom pulls may
     * happen in bursts, but these should be infrequent. The poll rate limit ensures that data is
     * sufficiently fresh (i.e. not stale) while reducing system load during atom pull bursts.
     */
    private static final long NETSTATS_POLL_RATE_LIMIT_MS = 15000;
    private long mLastNetworkStatsPollTime = -NETSTATS_POLL_RATE_LIMIT_MS;

    AggregatedMobileDataStatsPuller(@NonNull Context context) {
        final boolean traceEnabled = Trace.isTagEnabled(Trace.TRACE_TAG_SYSTEM_SERVER);
        if (traceEnabled) {
            Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER, TAG + "-Init");
        }

        mContext = context;

        if (useNetworkStatsQuerySummary()) {
            // to be aligned with networkStatsManager.forceUpdate() frequency
            mRateLimiter =
                    new RateLimiter(/* window= */ Duration.ofMillis(NETSTATS_POLL_RATE_LIMIT_MS));
        } else {
            mRateLimiter = new RateLimiter(/* window= */ Duration.ofSeconds(2));
        }

        mUidStats = new ArrayMap<>();
        mUidPreviousState = new SparseIntArray();

        final long elapsedMillisSinceBoot = SystemClock.elapsedRealtime();
        final long currentTimeMillis = MICROSECONDS.toMillis(SystemClock.currentTimeMicro());
        final long bootTimeMillis = currentTimeMillis - elapsedMillisSinceBoot;
        final long bucketDurationMillis =
                Settings.Global.getLong(
                        mContext.getContentResolver(),
                        NETSTATS_UID_BUCKET_DURATION,
                        NETSTATS_UID_DEFAULT_BUCKET_DURATION_MS);

        mNetworkStatsManager = mContext.getSystemService(NetworkStatsManager.class);

        if (useNetworkStatsQuerySummary()) {
            NetworkTemplate template =
                    new NetworkTemplate.Builder(MATCH_MOBILE).setMeteredness(METERED_YES).build();
            mStatsAccumulator =
                    new NetworkStatsAccumulator(
                            template,
                            false,
                            bucketDurationMillis,
                            bootTimeMillis - bucketDurationMillis);
        } else {
            mStatsAccumulator = null;
        }

        HandlerThread mMobileDataStatsHandlerThread = new HandlerThread("MobileDataStatsHandler");
        mMobileDataStatsHandlerThread.start();
        mMobileDataStatsHandler = new Handler(mMobileDataStatsHandlerThread.getLooper());

        if (mNetworkStatsManager != null) {
            mMobileDataStatsHandler.post(
                    () -> {
                        updateNetworkStats(mNetworkStatsManager);
                    });
        }
        if (traceEnabled) {
            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
        }
    }

    public void noteUidProcessState(int uid, int state, long unusedElapsedRealtime,
                                    long unusedUptime) {
        if (mRateLimiter.tryAcquire()) {
            mMobileDataStatsHandler.post(
                () -> {
                    noteUidProcessStateImpl(uid, state);
                });
        } else {
            synchronized (mLock) {
                mUidPreviousState.put(uid, state);
            }
        }
    }

    public int pullDataBytesTransfer(List<StatsEvent> data) {
        synchronized (mLock) {
            return pullDataBytesTransferLocked(data);
        }
    }

    @GuardedBy("mLock")
    private MobileDataStats getUidStatsForPreviousStateLocked(int uid) {
        final int previousState = mUidPreviousState.get(uid, ActivityManager.PROCESS_STATE_UNKNOWN);
        final UidProcState statsKey = new UidProcState(uid, previousState);
        if (mUidStats.containsKey(statsKey)) {
            return mUidStats.get(statsKey);
        } else {
            if (DEBUG && previousState == ActivityManager.PROCESS_STATE_UNKNOWN) {
                Slog.d(TAG, "getUidStatsForPreviousStateLocked() no prev state info for uid "
                        + uid + ". Tracking stats with ActivityManager.PROCESS_STATE_UNKNOWN");
            }
        }
        if (mUidStats.size() < UID_STATS_MAX_SIZE) {
            MobileDataStats stats = new MobileDataStats();
            mUidStats.put(statsKey, stats);
            return stats;
        }
        if (DEBUG) {
            Slog.w(TAG, "getUidStatsForPreviousStateLocked() UID_STATS_MAX_SIZE reached");
        }
        return null;
    }

    private void noteUidProcessStateImpl(int uid, int state) {
        // noteUidProcessStateImpl can be called back to back several times while
        // the updateNetworkStats loops over several stats for multiple uids
        // and during the first call in a batch of proc state change event it can
        // contain info for uid with unknown previous state yet which can happen due to
        // a few
        // reasons:
        // - app was just started
        // - app was started before the ActivityManagerService
        // as result stats would be created with state ==
        // ActivityManager.PROCESS_STATE_UNKNOWN
        if (mNetworkStatsManager != null) {
            updateNetworkStats(mNetworkStatsManager);
        } else {
            Slog.w(TAG, "noteUidProcessStateLocked() can not get mNetworkStatsManager");
        }
        synchronized (mLock) {
            mUidPreviousState.put(uid, state);
        }
    }

    private NetworkStats getMobileUidStats(NetworkStatsManager networkStatsManager) {
        if (useNetworkStatsQuerySummary()) {
            // networkStatsManager.querySummary provides reduced data resolution
            // and higher latency for fresh data compared to networkStatsManager.getMobileUidStats.
            // On the other hand getMobileUidStats provides realtime data in the memory
            // with higher performance cost.
            // Assumption is the tradeoff is acceptable in regards to existing
            // atom MOBILE_BYTES_TRANSFER use case
            final long elapsedMillisSinceBoot = SystemClock.elapsedRealtime();
            if (elapsedMillisSinceBoot - mLastNetworkStatsPollTime >= NETSTATS_POLL_RATE_LIMIT_MS) {
                mLastNetworkStatsPollTime = elapsedMillisSinceBoot;
                Slog.d(TAG, "getMobileUidStats() forceUpdate");
                networkStatsManager.forceUpdate();
            }

            final long currentTimeMillis = MICROSECONDS.toMillis(SystemClock.currentTimeMicro());
            return mStatsAccumulator.queryStats(
                    currentTimeMillis,
                    (aTemplate, aIncludeTags, aStartTime, aEndTime) -> {
                        final android.app.usage.NetworkStats queryNonTaggedStats =
                                networkStatsManager.querySummary(aTemplate, aStartTime, aEndTime);
                        final NetworkStats nonTaggedStats =
                                fromPublicNetworkStats(queryNonTaggedStats);
                        queryNonTaggedStats.close();
                        return nonTaggedStats;
                    });
        } else {
            return networkStatsManager.getMobileUidStats();
        }
    }

    private void updateNetworkStats(NetworkStatsManager networkStatsManager) {
        final boolean traceEnabled = Trace.isTagEnabled(Trace.TRACE_TAG_SYSTEM_SERVER);
        if (traceEnabled) {
            Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER, TAG + "-updateNetworkStats");
        }

        final NetworkStats latestStats = getMobileUidStats(networkStatsManager);
        if (isEmpty(latestStats)) {
            if (DEBUG) {
                Slog.w(TAG, "getMobileUidStats() failed");
                Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
            }
            return;
        }
        if (DEBUG) {
            Slog.d(TAG, "latestStats: \n" + latestStats.toString());
        }

        NetworkStats delta = latestStats.subtract(mLastMobileUidStats);
        mLastMobileUidStats = latestStats;

        if (!isEmpty(delta)) {
            updateNetworkStatsDelta(delta);
        } else if (DEBUG) {
            Slog.w(TAG, "updateNetworkStats() no delta");
        }
        if (traceEnabled) {
            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
        }
    }

    private void updateNetworkStatsDelta(NetworkStats delta) {
        final boolean traceEnabled = DEBUG && Trace.isTagEnabled(Trace.TRACE_TAG_SYSTEM_SERVER);
        if (traceEnabled) {
            Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER, TAG + "-updateNetworkStatsDelta");
        }
        synchronized (mLock) {
            for (NetworkStats.Entry entry : delta) {
                if (entry.getRxPackets() != 0 || entry.getTxPackets() != 0) {
                    if (DEBUG) {
                        Slog.d(TAG, entry.toString());
                    }
                    MobileDataStats stats = getUidStatsForPreviousStateLocked(entry.getUid());
                    if (stats != null) {
                        stats.addTxBytes(entry.getTxBytes());
                        stats.addRxBytes(entry.getRxBytes());
                        stats.addTxPackets(entry.getTxPackets());
                        stats.addRxPackets(entry.getRxPackets());
                    }
                }
            }
        }
        if (traceEnabled) {
            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
        }
    }

    @GuardedBy("mLock")
    private int pullDataBytesTransferLocked(List<StatsEvent> pulledData) {
        for (Map.Entry<UidProcState, MobileDataStats> uidStats : mUidStats.entrySet()) {
            if (!uidStats.getValue().isEmpty()) {
                MobileDataStats stats = uidStats.getValue();
                pulledData.add(FrameworkStatsLog.buildStatsEvent(
                        FrameworkStatsLog.MOBILE_BYTES_TRANSFER_BY_PROC_STATE,
                        uidStats.getKey().getUid(),
                        ActivityManager.processStateAmToProto(uidStats.getKey().getState()),
                        stats.getRxBytes(),
                        stats.getRxPackets(),
                        stats.getTxBytes(),
                        stats.getTxPackets()));
            }
        }
        if (DEBUG) {
            Slog.d(TAG,
                    "pullDataBytesTransferLocked() done. results count " + pulledData.size());
        }
        return StatsManager.PULL_SUCCESS;
    }

    private static boolean isEmpty(NetworkStats stats) {
        for (NetworkStats.Entry entry : stats) {
            if (entry.getRxPackets() != 0 || entry.getTxPackets() != 0) {
                // at least one non empty entry located
                return false;
            }
        }
        return true;
    }
}
