/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.power.stats;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.os.BatteryUsageStats;
import android.os.BatteryUsageStatsQuery;
import android.os.Handler;
import android.os.Process;
import android.os.UidBatteryConsumer;
import android.os.UserHandle;
import android.util.Log;
import android.util.LogWriter;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.os.Clock;
import com.android.internal.os.MonotonicClock;
import com.android.internal.util.ArrayUtils;
import com.android.server.power.stats.BatteryStatsImpl.BatteryStatsSession;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Uses accumulated battery stats data and PowerCalculators to produce power
 * usage data attributed to subsystems and UIDs.
 */
public class BatteryUsageStatsProvider {
    private static final String TAG = "BatteryUsageStatsProv";
    private static final boolean DEBUG = false;

    private final PowerAttributor mPowerAttributor;
    private final PowerStatsStore mPowerStatsStore;
    private final int mAccumulatedBatteryUsageStatsSpanSize;
    private final Clock mClock;
    private final MonotonicClock mMonotonicClock;
    private long mLastAccumulationMonotonicHistorySize;

    private static class AccumulatedBatteryUsageStats {
        public BatteryUsageStats.Builder builder;
        public long startWallClockTime;
        public long startMonotonicTime;
        public long endMonotonicTime;
    }

    public BatteryUsageStatsProvider(@NonNull PowerAttributor powerAttributor,
            @NonNull PowerStatsStore powerStatsStore, int accumulatedBatteryUsageStatsSpanSize,
            @NonNull Clock clock, @NonNull MonotonicClock monotonicClock) {
        mPowerAttributor = powerAttributor;
        mPowerStatsStore = powerStatsStore;
        mAccumulatedBatteryUsageStatsSpanSize = accumulatedBatteryUsageStatsSpanSize;
        mClock = clock;
        mMonotonicClock = monotonicClock;

        mPowerStatsStore.addSectionReader(new BatteryUsageStatsSection.Reader());
        mPowerStatsStore.addSectionReader(new AccumulatedBatteryUsageStatsSection.Reader());
    }

    /**
     * Conditionally runs a battery usage stats accumulation on the supplied handler.
     */
    public void accumulateBatteryUsageStatsAsync(BatteryStatsImpl stats, Handler handler,
            boolean forceUpdate) {
        if (!forceUpdate) {
            synchronized (this) {
                long historySize = stats.getHistory().getMonotonicHistorySize();
                long delta = historySize - mLastAccumulationMonotonicHistorySize;
                if (delta >= 0 && delta < mAccumulatedBatteryUsageStatsSpanSize) {
                    return;
                }
                mLastAccumulationMonotonicHistorySize = historySize;
            }
        }

        BatteryStatsSession session = stats.getBatteryStatsSession();

        // No need to store the accumulated stats asynchronously, as the entire accumulation
        // operation is async
        handler.post(() -> accumulateBatteryUsageStats(session, handler));
    }

    /**
     * Computes BatteryUsageStats for the period since the last accumulated stats were stored,
     * adds them to the accumulated stats and saves the result on the handler thread.
     */
    public void accumulateBatteryUsageStats(BatteryStatsSession session, Handler handler) {
        AccumulatedBatteryUsageStats accumulatedStats = loadAccumulatedBatteryUsageStats();
        updateAccumulatedBatteryUsageStats(accumulatedStats, session);

        PowerStatsSpan powerStatsSpan = new PowerStatsSpan(AccumulatedBatteryUsageStatsSection.ID);
        powerStatsSpan.addSection(
                new AccumulatedBatteryUsageStatsSection(accumulatedStats.builder));
        powerStatsSpan.addTimeFrame(accumulatedStats.startMonotonicTime,
                accumulatedStats.startWallClockTime,
                accumulatedStats.endMonotonicTime - accumulatedStats.startMonotonicTime);
        mMonotonicClock.write();
        if (handler.getLooper().isCurrentThread()) {
            mPowerStatsStore.storePowerStatsSpan(powerStatsSpan);
            accumulatedStats.builder.discard();
        } else {
            mPowerStatsStore.storePowerStatsSpanAsync(powerStatsSpan,
                    accumulatedStats.builder::discard);
        }
    }

    /**
     * Returns true if the last update was too long ago for the tolerances specified
     * by the supplied queries.
     */
    public static boolean shouldUpdateStats(List<BatteryUsageStatsQuery> queries,
            long elapsedRealtime, long lastUpdateTimeStampMs) {
        long allowableStatsAge = Long.MAX_VALUE;
        for (int i = queries.size() - 1; i >= 0; i--) {
            BatteryUsageStatsQuery query = queries.get(i);
            allowableStatsAge = Math.min(allowableStatsAge, query.getMaxStatsAge());
        }

        return elapsedRealtime - lastUpdateTimeStampMs > allowableStatsAge;
    }

    /**
     * Returns snapshots of battery attribution data, one per supplied query.
     */
    public List<BatteryUsageStats> getBatteryUsageStats(BatteryStatsImpl stats,
            List<BatteryUsageStatsQuery> queries) {
        ArrayList<BatteryUsageStats> results = new ArrayList<>(queries.size());
        synchronized (stats) {
            stats.prepareForDumpLocked();
        }
        BatteryStatsSession session = stats.getBatteryStatsSession();
        final long currentTimeMillis = mClock.currentTimeMillis();
        for (int i = 0; i < queries.size(); i++) {
            results.add(getBatteryUsageStats(session, queries.get(i), currentTimeMillis));
        }

        return results;
    }

    /**
     * Returns a snapshot of battery attribution data.
     */
    public BatteryUsageStats getBatteryUsageStats(BatteryStatsImpl stats,
            BatteryUsageStatsQuery query) {
        return getBatteryUsageStats(stats.getBatteryStatsSession(), query,
                mClock.currentTimeMillis());
    }

    private BatteryUsageStats getBatteryUsageStats(BatteryStatsSession session,
            BatteryUsageStatsQuery query, long currentTimeMs) {
        BatteryUsageStats batteryUsageStats;
        if ((query.getFlags()
                & BatteryUsageStatsQuery.FLAG_BATTERY_USAGE_STATS_ACCUMULATED) != 0) {
            batteryUsageStats = getAccumulatedBatteryUsageStats(session, query);
        } else if (query.getAggregatedToTimestamp() == 0) {
            BatteryUsageStats.Builder builder = computeBatteryUsageStats(session, query,
                    query.getMonotonicStartTime(),
                    query.getMonotonicEndTime(), currentTimeMs);
            batteryUsageStats = builder.build();
        } else {
            batteryUsageStats = getAggregatedBatteryUsageStats(session, query);
        }
        if (DEBUG) {
            Slog.d(TAG, "query = " + query);
            PrintWriter pw = new PrintWriter(new LogWriter(Log.DEBUG, TAG));
            batteryUsageStats.dump(pw, "");
            pw.flush();
        }
        return batteryUsageStats;
    }

    private BatteryUsageStats getAccumulatedBatteryUsageStats(BatteryStatsSession session,
            BatteryUsageStatsQuery query) {
        AccumulatedBatteryUsageStats accumulatedStats = loadAccumulatedBatteryUsageStats();
        if (accumulatedStats.endMonotonicTime == MonotonicClock.UNDEFINED
                || mMonotonicClock.monotonicTime() - accumulatedStats.endMonotonicTime
                > query.getMaxStatsAge()) {
            updateAccumulatedBatteryUsageStats(accumulatedStats, session);
        }
        return accumulatedStats.builder.build();
    }

    private AccumulatedBatteryUsageStats loadAccumulatedBatteryUsageStats() {
        AccumulatedBatteryUsageStats stats = new AccumulatedBatteryUsageStats();
        stats.startWallClockTime = 0;
        stats.startMonotonicTime = MonotonicClock.UNDEFINED;
        stats.endMonotonicTime = MonotonicClock.UNDEFINED;
        PowerStatsSpan powerStatsSpan = mPowerStatsStore.loadPowerStatsSpan(
                AccumulatedBatteryUsageStatsSection.ID,
                AccumulatedBatteryUsageStatsSection.TYPE);
        if (powerStatsSpan != null) {
            List<PowerStatsSpan.Section> sections = powerStatsSpan.getSections();
            for (int i = sections.size() - 1; i >= 0; i--) {
                PowerStatsSpan.Section section = sections.get(i);
                if (AccumulatedBatteryUsageStatsSection.TYPE.equals(section.getType())) {
                    stats.builder = ((AccumulatedBatteryUsageStatsSection) section)
                            .getBatteryUsageStatsBuilder();
                    stats.startWallClockTime = powerStatsSpan.getMetadata().getStartTime();
                    stats.startMonotonicTime =
                            powerStatsSpan.getMetadata().getStartMonotonicTime();
                    stats.endMonotonicTime = powerStatsSpan.getMetadata().getEndMonotonicTime();
                    break;
                }
            }
        }
        return stats;
    }

    private void updateAccumulatedBatteryUsageStats(AccumulatedBatteryUsageStats accumulatedStats,
            BatteryStatsSession session) {
        long startMonotonicTime = accumulatedStats.endMonotonicTime;
        if (startMonotonicTime == MonotonicClock.UNDEFINED) {
            startMonotonicTime = session.getMonotonicStartTime();
        }
        long endWallClockTime = mClock.currentTimeMillis();
        long endMonotonicTime = mMonotonicClock.monotonicTime();

        if (accumulatedStats.builder == null) {
            accumulatedStats.builder = new BatteryUsageStats.Builder(
                    session.getCustomEnergyConsumerNames(), true, true, true, 0);
            accumulatedStats.startWallClockTime = session.getStartClockTime();
            accumulatedStats.startMonotonicTime = session.getMonotonicStartTime();
            accumulatedStats.builder.setStatsStartTimestamp(accumulatedStats.startWallClockTime);
        }

        accumulatedStats.endMonotonicTime = endMonotonicTime;

        accumulatedStats.builder.setStatsEndTimestamp(endWallClockTime);
        accumulatedStats.builder.setStatsDuration(endMonotonicTime - startMonotonicTime);

        mPowerAttributor.estimatePowerConsumption(accumulatedStats.builder, session.getHistory(),
                startMonotonicTime, endMonotonicTime);

        populateBatterySessionInfo(accumulatedStats.builder, session);
    }

    private BatteryUsageStats.Builder computeBatteryUsageStats(BatteryStatsSession session,
            BatteryUsageStatsQuery query, long monotonicStartTime, long monotonicEndTime,
            long currentTimeMs) {
        final boolean includeProcessStateData = ((query.getFlags()
                & BatteryUsageStatsQuery.FLAG_BATTERY_USAGE_STATS_INCLUDE_PROCESS_STATE_DATA) != 0);
        final boolean includeVirtualUids = ((query.getFlags()
                & BatteryUsageStatsQuery.FLAG_BATTERY_USAGE_STATS_INCLUDE_VIRTUAL_UIDS) != 0);
        final double minConsumedPowerThreshold = query.getMinConsumedPowerThreshold();

        String[] customEnergyConsumerNames = session.getCustomEnergyConsumerNames();

        final BatteryUsageStats.Builder batteryUsageStatsBuilder = new BatteryUsageStats.Builder(
                customEnergyConsumerNames, includeProcessStateData, query.isScreenStateDataNeeded(),
                query.isPowerStateDataNeeded(), minConsumedPowerThreshold);

        if (monotonicStartTime == MonotonicClock.UNDEFINED) {
            monotonicStartTime = session.getMonotonicStartTime();
        }
        batteryUsageStatsBuilder.setStatsStartTimestamp(session.getStartClockTime()
                + (monotonicStartTime - session.getMonotonicStartTime()));
        if (monotonicEndTime != MonotonicClock.UNDEFINED) {
            batteryUsageStatsBuilder.setStatsEndTimestamp(session.getStartClockTime()
                    + (monotonicEndTime - session.getMonotonicStartTime()));
        } else {
            batteryUsageStatsBuilder.setStatsEndTimestamp(currentTimeMs);
        }

        if ((query.getFlags()
                & BatteryUsageStatsQuery.FLAG_BATTERY_USAGE_STATS_INCLUDE_HISTORY) != 0) {
            batteryUsageStatsBuilder.setBatteryHistory(session.getHistory().copy(),
                    query.getPreferredHistoryDurationMs());
        }

        mPowerAttributor.estimatePowerConsumption(batteryUsageStatsBuilder, session.getHistory(),
                monotonicStartTime, monotonicEndTime);

        // Combine apps by the user if necessary
        buildUserBatteryConsumers(batteryUsageStatsBuilder, query.getUserIds());

        populateBatterySessionInfo(batteryUsageStatsBuilder, session);
        return batteryUsageStatsBuilder;
    }

    private void populateBatterySessionInfo(BatteryUsageStats.Builder builder,
            BatteryStatsSession session) {
        double estimatedBatteryCapacity = session.getEstimatedBatteryCapacity();
        if (estimatedBatteryCapacity > 0) {
            builder.setBatteryCapacity(estimatedBatteryCapacity);
        }
        builder.setBatteryTimeRemainingMs(session.getBatteryTimeRemainingMs());
        builder.setChargeTimeRemainingMs(session.getChargeTimeRemainingMs());
    }

    private BatteryUsageStats getAggregatedBatteryUsageStats(BatteryStatsSession stats,
            BatteryUsageStatsQuery query) {
        final boolean includeProcessStateData = ((query.getFlags()
                & BatteryUsageStatsQuery.FLAG_BATTERY_USAGE_STATS_INCLUDE_PROCESS_STATE_DATA) != 0);
        final double minConsumedPowerThreshold = query.getMinConsumedPowerThreshold();

        final String[] customEnergyConsumerNames = stats.getCustomEnergyConsumerNames();
        final BatteryUsageStats.Builder builder = new BatteryUsageStats.Builder(
                customEnergyConsumerNames, includeProcessStateData,
                query.isScreenStateDataNeeded(), query.isPowerStateDataNeeded(),
                minConsumedPowerThreshold);
        if (mPowerStatsStore == null) {
            Log.e(TAG, "PowerStatsStore is unavailable");
            return builder.build();
        }

        List<PowerStatsSpan.Metadata> toc = mPowerStatsStore.getTableOfContents();
        for (PowerStatsSpan.Metadata spanMetadata : toc) {
            if (!spanMetadata.getSections().contains(BatteryUsageStatsSection.TYPE)) {
                continue;
            }

            // BatteryUsageStatsQuery is expressed in terms of wall-clock time range for the
            // session end time.
            //
            // The following algorithm is correct when there is only one time frame in the span.
            // When the wall-clock time is adjusted in the middle of an stats span,
            // constraining it by wall-clock time becomes ambiguous. In this case, the algorithm
            // only covers some situations, but not others.  When using the resulting data for
            // analysis, we should always pay attention to the full set of included timeframes.
            // TODO(b/298459065): switch to monotonic clock
            long minTime = Long.MAX_VALUE;
            long maxTime = 0;
            for (PowerStatsSpan.TimeFrame timeFrame : spanMetadata.getTimeFrames()) {
                long spanEndTime = timeFrame.startTime + timeFrame.duration;
                minTime = Math.min(minTime, spanEndTime);
                maxTime = Math.max(maxTime, spanEndTime);
            }

            // Per BatteryUsageStatsQuery API, the "from" timestamp is *exclusive*,
            // while the "to" timestamp is *inclusive*.
            boolean isInRange =
                    (query.getAggregatedFromTimestamp() == 0
                            || minTime > query.getAggregatedFromTimestamp())
                            && (query.getAggregatedToTimestamp() == 0
                            || maxTime <= query.getAggregatedToTimestamp());
            if (!isInRange) {
                continue;
            }

            try (PowerStatsSpan powerStatsSpan = mPowerStatsStore.loadPowerStatsSpan(
                    spanMetadata.getId(), BatteryUsageStatsSection.TYPE)) {
                if (powerStatsSpan == null) {
                    continue;
                }

                for (PowerStatsSpan.Section section : powerStatsSpan.getSections()) {
                    BatteryUsageStats snapshot =
                            ((BatteryUsageStatsSection) section).getBatteryUsageStats();
                    if (!Arrays.equals(snapshot.getCustomPowerComponentNames(),
                            customEnergyConsumerNames)) {
                        Log.w(TAG, "Ignoring older BatteryUsageStats snapshot, which has different "
                                + "custom power components: "
                                + Arrays.toString(snapshot.getCustomPowerComponentNames()));
                        continue;
                    }

                    if (includeProcessStateData && !snapshot.isProcessStateDataIncluded()) {
                        Log.w(TAG, "Ignoring older BatteryUsageStats snapshot, which "
                                + " does not include process state data");
                        continue;
                    }
                    builder.add(snapshot);
                }
            }
        }
        return builder.build();
    }

    private void buildUserBatteryConsumers(BatteryUsageStats.Builder builder,
            @UserIdInt int[] userIds) {
        if (ArrayUtils.contains(userIds, UserHandle.USER_ALL)) {
            return;
        }

        SparseArray<UidBatteryConsumer.Builder> uidBatteryConsumerBuilders =
                builder.getUidBatteryConsumerBuilders();

        for (int i = uidBatteryConsumerBuilders.size() - 1; i >= 0; i--) {
            final UidBatteryConsumer.Builder uidBuilder = uidBatteryConsumerBuilders.valueAt(i);
            if (uidBuilder.isVirtualUid()) {
                continue;
            }

            final int uid = uidBuilder.getUid();
            if (UserHandle.getAppId(uid) < Process.FIRST_APPLICATION_UID) {
                continue;
            }

            final int userId = UserHandle.getUserId(uid);
            if (!ArrayUtils.contains(userIds, userId)) {
                uidBuilder.excludeFromBatteryUsageStats();
                builder.getOrCreateUserBatteryConsumerBuilder(userId)
                        .addUidBatteryConsumer(uidBuilder);
            }
        }
    }
}
