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

package com.android.server.appop;

import static android.app.AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE;
import static android.app.AppOpsManager.ATTRIBUTION_FLAG_ACCESSOR;
import static android.app.AppOpsManager.ATTRIBUTION_FLAG_RECEIVER;
import static android.app.AppOpsManager.ATTRIBUTION_FLAG_TRUSTED;
import static android.app.AppOpsManager.HISTORY_FLAG_AGGREGATE;
import static android.app.AppOpsManager.HISTORY_FLAG_DISCRETE;
import static android.app.AppOpsManager.MAX_PRIORITY_UID_STATE;
import static android.app.AppOpsManager.OP_FLAG_SELF;
import static android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXIED;
import static android.app.AppOpsManager.UID_STATE_MAX_LAST_NON_RESTRICTED;
import static android.app.AppOpsManager.flagsToString;
import static android.app.AppOpsManager.getUidStateName;

import static com.android.internal.util.FrameworkStatsLog.AGGREGATED_APP_OP_ACCESS_EVENT_REPORTED;
import static com.android.internal.util.FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_CACHE_FULL;
import static com.android.internal.util.FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_MIGRATION;
import static com.android.internal.util.FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_PERIODIC;
import static com.android.internal.util.FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_READ;
import static com.android.internal.util.FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_SHUTDOWN;
import static com.android.server.appop.HistoricalRegistry.AggregationTimeWindow;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.IntArray;
import android.util.LongSparseArray;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.ServiceThread;

import java.io.File;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Helper class to read/write aggregated app op access events.
 *
 * <p>This class manages aggregation and persistence of app op access events. It aggregates app op
 * access events in a fixed time window interval and stores in a SQLite database. It also
 * provides methods for querying the data.</p>
 *
 * <p>This class uses {@link AppOpHistoryDbHelper} to interact with the SQLite database and
 * {@link AppOpHistoryCache} to manage the in-memory cache of events. It employs a
 * {@link SqliteWriteHandler} to perform database writes asynchronously, ensuring that
 * the main thread is not blocked.</p>
 */
public class AppOpHistoryHelper {
    private static final String TAG = "AppOpHistoryHelper";
    private static final long PERIODIC_JOB_MAX_VARIATION_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final long DB_WRITE_INTERVAL_PERIODIC_MILLIS =
            Duration.ofMinutes(10).toMillis();
    private static final long EXPIRED_ENTRY_DELETION_INTERVAL_MILLIS =
            Duration.ofHours(6).toMillis();
    // Event type handled by SqliteWriteHandler
    private static final int WRITE_DATABASE_PERIODIC = 1;
    private static final int DELETE_EXPIRED_ENTRIES_PERIODIC = 2;
    private static final int WRITE_DATABASE_CACHE_FULL = 3;
    private static final int CLOSE_DATABASE_ON_SHUTDOWN = 4;
    // Used in adding variation to periodic job interval
    private final Random mRandom = new Random();
    // time window interval for aggregation
    private long mQuantizationMillis;
    private long mHistoryRetentionMillis;
    private final File mDatabaseFile;
    private final Context mContext;
    private final AppOpHistoryDbHelper mDbHelper;
    private final SqliteWriteHandler mSqliteWriteHandler;
    private final MetricHandler mMetricHandler;
    private final AppOpHistoryCache mCache = new AppOpHistoryCache(1024);

    AppOpHistoryHelper(@NonNull Context context, File databaseFile,
            AggregationTimeWindow aggregationTimeWindow, int databaseVersion) {
        mContext = context;
        mDatabaseFile = databaseFile;
        mDbHelper = new AppOpHistoryDbHelper(
                context, databaseFile, aggregationTimeWindow, databaseVersion);
        ServiceThread thread =
                new ServiceThread(TAG, Process.THREAD_PRIORITY_DEFAULT, true);
        thread.start();
        mSqliteWriteHandler = new SqliteWriteHandler(thread.getLooper());
        mMetricHandler = new MetricHandler(thread.getLooper());
    }

    // Set parameters before using this class.
    void systemReady(long quantizationMillis, long historyRetentionMillis) {
        mQuantizationMillis = quantizationMillis;
        mHistoryRetentionMillis = historyRetentionMillis;

        ensurePeriodicJobsAreScheduled();
    }

    void incrementOpAccessedCount(int op, int uid, @NonNull String packageName,
            @NonNull String deviceId, @Nullable String attributionTag, int uidState, int flags,
            long accessTime, int attributionFlags, long attributionChainId, int accessCount,
            boolean isStartOrResume) {
        long duration = isStartOrResume ? 0 : -1;
        AppOpAccessEvent appOpAccess = new AppOpAccessEvent(uid, packageName, op, deviceId,
                attributionTag, flags, uidState, attributionFlags, attributionChainId, accessTime,
                discretizeTimestamp(accessTime), duration, discretizeDuration(duration));
        // increase in duration for aggregation is passed as 0 explicitly
        mCache.insertOrUpdate(appOpAccess, accessCount, 0, 0);
    }

    void incrementOpRejectedCount(int op, int uid, @NonNull String packageName,
            @NonNull String deviceId, @Nullable String attributionTag, int uidState, int flags,
            long rejectTime, int attributionFlags, long attributionChainId, int rejectCount) {
        long duration = -1;
        AppOpAccessEvent appOpAccess = new AppOpAccessEvent(uid, packageName, op, deviceId,
                attributionTag, flags, uidState, attributionFlags, attributionChainId, rejectTime,
                discretizeTimestamp(rejectTime), duration, discretizeDuration(duration));
        mCache.insertOrUpdate(appOpAccess, 0, rejectCount, 0);
    }

    void recordOpAccessDuration(int op, int uid, @NonNull String packageName,
            @NonNull String deviceId, @Nullable String attributionTag,
            @AppOpsManager.UidState int uidState,
            @AppOpsManager.OpFlags int flags, long eventStartTime,
            int attributionFlags, long attributionChainId, long duration) {
        AppOpAccessEvent appOpAccess = new AppOpAccessEvent(uid, packageName, op, deviceId,
                attributionTag, flags, uidState, attributionFlags, attributionChainId,
                eventStartTime, discretizeTimestamp(eventStartTime), duration,
                discretizeDuration(duration));
        // This is pause or finish, no needs to increase access count.
        mCache.insertOrUpdate(appOpAccess, 0, 0, duration);
    }

    void addShortIntervalOpsToHistoricalOpsResult(AppOpsManager.HistoricalOps result,
            long beginTimeMillis, long endTimeMillis,
            @AppOpsManager.HistoricalOpsRequestFilter int filter, int uidFilter,
            @Nullable String packageNameFilter,
            @Nullable String[] opNamesFilter,
            @Nullable String attributionTagFilter, int opFlagsFilter,
            Set<String> attributionExemptPkgs, @AppOpsManager.OpHistoryFlags int historyFlags) {
        List<AggregatedAppOpAccessEvent> discreteOps = getAppOpHistory(result,
                beginTimeMillis, endTimeMillis, filter, uidFilter, packageNameFilter, opNamesFilter,
                attributionTagFilter, opFlagsFilter);
        boolean includeDiscreteEvents = (historyFlags & HISTORY_FLAG_DISCRETE) != 0;
        boolean assembleChains = attributionExemptPkgs != null && includeDiscreteEvents;
        LongSparseArray<AttributionChain> attributionChains = null;
        if (assembleChains) {
            attributionChains = createAttributionChains(discreteOps, attributionExemptPkgs);
        }

        int nEvents = discreteOps.size();
        for (int j = 0; j < nEvents; j++) {
            AggregatedAppOpAccessEvent event = discreteOps.get(j);
            AppOpsManager.OpEventProxyInfo proxy = null;
            if (assembleChains && event.attributionChainId() != ATTRIBUTION_CHAIN_ID_NONE) {
                AttributionChain chain = attributionChains.get(event.attributionChainId());
                if (chain != null && chain.isComplete()
                        && chain.isStart(event)
                        && chain.mLastVisibleEvent != null) {
                    AggregatedAppOpAccessEvent proxyEvent = chain.mLastVisibleEvent;
                    proxy = new AppOpsManager.OpEventProxyInfo(proxyEvent.uid(),
                            proxyEvent.packageName(), proxyEvent.attributionTag());
                }
            }
            if (includeDiscreteEvents) {
                // Discrete accesses doesn't include rejected events, reject event has 0 duration.
                if (event.totalAccessCount() > 0 || event.totalDurationMillis() > 0) {
                    result.addDiscreteAccess(event.opCode(), event.uid(), event.packageName(),
                            event.attributionTag(), event.uidState(), event.opFlags(),
                            discretizeTimestamp(event.accessTimeMillis()),
                            discretizeDuration(event.durationMillis()), proxy);
                }
            }
            if ((historyFlags & HISTORY_FLAG_AGGREGATE) != 0) {
                addAppOpAccessEventToHistoricalOps(result, event);
            }
        }
    }

    void addLongIntervalOpsToHistoricalOpsResult(AppOpsManager.HistoricalOps result,
            long beginTimeMillis, long endTimeMillis,
            @AppOpsManager.HistoricalOpsRequestFilter int filter, int uidFilter,
            @Nullable String packageNameFilter,
            @Nullable String[] opNamesFilter,
            @Nullable String attributionTagFilter, int opFlagsFilter) {
        List<AggregatedAppOpAccessEvent> appOpHistoryAccesses = getAppOpHistory(result,
                beginTimeMillis, endTimeMillis, filter, uidFilter, packageNameFilter, opNamesFilter,
                attributionTagFilter, opFlagsFilter);
        for (AggregatedAppOpAccessEvent opEvent : appOpHistoryAccesses) {
            addAppOpAccessEventToHistoricalOps(result, opEvent);
        }
    }

    private void addAppOpAccessEventToHistoricalOps(AppOpsManager.HistoricalOps result,
            AggregatedAppOpAccessEvent opEvent) {
        result.increaseAccessCount(opEvent.opCode(), opEvent.uid(),
                opEvent.packageName(),
                opEvent.attributionTag(), opEvent.uidState(), opEvent.opFlags(),
                opEvent.totalAccessCount());
        result.increaseRejectCount(opEvent.opCode(), opEvent.uid(),
                opEvent.packageName(),
                opEvent.attributionTag(), opEvent.uidState(), opEvent.opFlags(),
                opEvent.totalRejectCount());
        result.increaseAccessDuration(opEvent.opCode(), opEvent.uid(),
                opEvent.packageName(),
                opEvent.attributionTag(), opEvent.uidState(), opEvent.opFlags(),
                opEvent.totalDurationMillis());
    }

    private void insertAppOpHistory(@NonNull List<AggregatedAppOpAccessEvent> appOpEvents,
            int writeSource) {
        mDbHelper.insertAppOpHistory(appOpEvents, writeSource);
        sendToMetricsHandler(appOpEvents);
    }

    private List<AggregatedAppOpAccessEvent> getAppOpHistory(AppOpsManager.HistoricalOps result,
            long beginTimeMillis, long endTimeMillis,
            @AppOpsManager.HistoricalOpsRequestFilter int filter, int uidFilter,
            @Nullable String packageNameFilter,
            @Nullable String[] opNamesFilter,
            @Nullable String attributionTagFilter, int opFlagsFilter) {
        IntArray opCodes = AppOpHistoryQueryHelper.getAppOpCodes(filter, opNamesFilter);
        // flush the cache into database before read.
        if (opCodes != null) {
            insertAppOpHistory(mCache.evict(opCodes),
                    SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_READ);
        } else {
            insertAppOpHistory(mCache.evictAll(),
                    SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_READ);
        }
        // Adjust begin & end time to time window's boundary.
        beginTimeMillis = Math.max(discretizeTimestamp(beginTimeMillis),
                discretizeTimestamp((System.currentTimeMillis() - mHistoryRetentionMillis)));
        endTimeMillis = discretizeTimestamp(endTimeMillis + mQuantizationMillis);
        result.setBeginAndEndTime(beginTimeMillis, endTimeMillis);

        return mDbHelper.getAppOpHistory(
                filter, beginTimeMillis, endTimeMillis, uidFilter, packageNameFilter,
                attributionTagFilter, opCodes, opFlagsFilter, -1, null, false);
    }

    boolean deleteDatabase() {
        mDbHelper.close();
        return mContext.deleteDatabase(mDatabaseFile.getAbsolutePath());
    }

    long getLargestAttributionChainId() {
        return mDbHelper.getLargestAttributionChainId();
    }

    void shutdown() {
        persistPendingHistory();
        // Remove pending delayed message.
        mMetricHandler.removeMessages(MetricHandler.SEND_EVENTS);
        mMetricHandler.sendEmptyMessage(MetricHandler.SEND_EVENTS);
        mSqliteWriteHandler.sendEmptyMessage(CLOSE_DATABASE_ON_SHUTDOWN);
    }

    // Write app op records from cache to the database.
    void persistPendingHistory() {
        mSqliteWriteHandler.removeAllPendingMessages();
        insertAppOpHistory(mCache.evictAll(),
                SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_SHUTDOWN);
    }

    void clearHistory() {
        mCache.clear();
        mDbHelper.execSQL(AppOpHistoryTable.DELETE_TABLE_DATA);
    }

    void clearHistory(int uid, String packageName) {
        mCache.clear(uid, packageName);
        mDbHelper.execSQL(AppOpHistoryTable.DELETE_DATA_FOR_UID_PACKAGE,
                new Object[]{uid, packageName});
    }

    long discretizeTimestamp(long timestamp) {
        return timestamp / mQuantizationMillis * mQuantizationMillis;
    }

    long discretizeDuration(long duration) {
        return duration == -1 ? -1 : (duration + mQuantizationMillis - 1)
                / mQuantizationMillis * mQuantizationMillis;
    }

    void migrateDiscreteAppOpHistory(List<AggregatedAppOpAccessEvent> appOpEvents) {
        mDbHelper.insertAppOpHistory(appOpEvents,
                SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_MIGRATION);
    }

    long getTotalRecordsCount() {
        return mDbHelper.getTotalRecordsCount();
    }

    @NonNull
    ArraySet<String> getRecentlyUsedPackageNames(@NonNull String[] opNames,
            @AppOpsManager.HistoricalOpsRequestFilter int filter, long beginTimeMillis,
            long endTimeMillis, @AppOpsManager.OpFlags int opFlags) {
        return mDbHelper.getRecentlyUsedPackageNames(opNames, filter, beginTimeMillis,
             endTimeMillis, opFlags);
    }

    @VisibleForTesting
    List<AggregatedAppOpAccessEvent> getAppOpHistory() {
        List<AggregatedAppOpAccessEvent> ops = new ArrayList<>();
        synchronized (mCache) {
            ops.addAll(mCache.snapshot());
            ops.addAll(mDbHelper.getAppOpHistory());
        }
        return ops;
    }

    @VisibleForTesting
    List<AggregatedAppOpAccessEvent> getAppOpHistory(
            long beginTimeMillis, long endTimeMillis, int filter, int uidFilter,
            @Nullable String packageNameFilter,
            @Nullable String[] opNamesFilter,
            @Nullable String attributionTagFilter, int opFlagsFilter) {
        IntArray opCodes = AppOpHistoryQueryHelper.getAppOpCodes(filter, opNamesFilter);
        // flush the cache into database before read.
        if (opCodes != null) {
            insertAppOpHistory(mCache.evict(opCodes),
                    SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_READ);
        } else {
            insertAppOpHistory(mCache.evictAll(),
                    SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_READ);
        }
        // Adjust begin & end time to time window's boundary.
        beginTimeMillis = Math.max(discretizeTimestamp(beginTimeMillis),
                discretizeTimestamp((System.currentTimeMillis() - mHistoryRetentionMillis)));
        endTimeMillis = discretizeTimestamp(endTimeMillis + mQuantizationMillis);
        return mDbHelper.getAppOpHistory(
                filter, beginTimeMillis, endTimeMillis, uidFilter, packageNameFilter,
                attributionTagFilter, opCodes, opFlagsFilter, -1, null, false);
    }

    private LongSparseArray<AttributionChain> createAttributionChains(
            List<AggregatedAppOpAccessEvent> discreteOps, Set<String> attributionExemptPkgs) {
        LongSparseArray<AttributionChain> chains = new LongSparseArray<>();
        final int count = discreteOps.size();

        for (int i = 0; i < count; i++) {
            AggregatedAppOpAccessEvent opEvent = discreteOps.get(i);
            if (opEvent.attributionChainId() == ATTRIBUTION_CHAIN_ID_NONE
                    || (opEvent.attributionFlags() & ATTRIBUTION_FLAG_TRUSTED) == 0) {
                continue;
            }
            AttributionChain chain = chains.get(opEvent.attributionChainId());
            if (chain == null) {
                chain = new AttributionChain(attributionExemptPkgs);
                chains.put(opEvent.attributionChainId(), chain);
            }
            chain.addEvent(opEvent);
        }
        return chains;
    }

    void dump(long beginTimeMillis, long endTimeMillis, PrintWriter pw, int filterUid,
            @Nullable String filterPackage, @Nullable String filterAttributionTag, IntArray opCodes,
            @AppOpsManager.HistoricalOpsRequestFilter int filter, @NonNull SimpleDateFormat sdf,
            @NonNull Date date, int limit) {
        // flush caches to the database
        insertAppOpHistory(mCache.evictAll(),
                SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_READ);
        beginTimeMillis = discretizeTimestamp(beginTimeMillis);

        List<AggregatedAppOpAccessEvent> appOpHistoryAccesses = mDbHelper.getAppOpHistory(
                filter, beginTimeMillis, endTimeMillis, filterUid, filterPackage,
                filterAttributionTag, opCodes, AppOpsManager.OP_FLAGS_ALL, limit,
                AppOpHistoryTable.Columns.ACCESS_TIME, false);

        pw.println();
        pw.println("UID|PACKAGE_NAME|DEVICE_ID|OP_NAME|ATTRIBUTION_TAG|ACCESS_TIME|ACCESS_COUNTS"
                + "|REJECT_COUNTS|DURATION|UID_STATE|OP_FLAGS"
                + "|ATTRIBUTION_CHAIN_ID|ATTRIBUTION_FLAGS");
        for (AggregatedAppOpAccessEvent aggAppOpAccess : appOpHistoryAccesses) {
            date.setTime(aggAppOpAccess.accessTimeMillis());
            pw.println(aggAppOpAccess.uid() + "|"
                    + aggAppOpAccess.packageName() + "|"
                    + aggAppOpAccess.deviceId() + "|"
                    + AppOpsManager.opToName(aggAppOpAccess.opCode()) + "|"
                    + aggAppOpAccess.attributionTag() + "|"
                    + sdf.format(date) + "|"
                    + aggAppOpAccess.totalAccessCount() + "|"
                    + aggAppOpAccess.totalRejectCount() + "|"
                    + aggAppOpAccess.totalDurationMillis() + "|"
                    + getUidStateName(aggAppOpAccess.uidState()) + "|"
                    + flagsToString(aggAppOpAccess.opFlags()) + "|"
                    + aggAppOpAccess.attributionChainId() + "|"
                    + aggAppOpAccess.attributionFlags());
        }
        pw.println();
    }

    private void ensurePeriodicJobsAreScheduled() {
        if (!mSqliteWriteHandler.hasMessages(WRITE_DATABASE_PERIODIC)) {
            mSqliteWriteHandler.sendEmptyMessageDelayed(WRITE_DATABASE_PERIODIC,
                    DB_WRITE_INTERVAL_PERIODIC_MILLIS + mRandom.nextLong(0,
                            PERIODIC_JOB_MAX_VARIATION_MILLIS));
        }
        if (!mSqliteWriteHandler.hasMessages(DELETE_EXPIRED_ENTRIES_PERIODIC)) {
            mSqliteWriteHandler.sendEmptyMessageDelayed(
                    DELETE_EXPIRED_ENTRIES_PERIODIC,
                    EXPIRED_ENTRY_DELETION_INTERVAL_MILLIS + mRandom.nextLong(0,
                            PERIODIC_JOB_MAX_VARIATION_MILLIS));
        }
    }

    private class SqliteWriteHandler extends Handler {
        // Max database size 50 MB
        private static final long MAX_DATABASE_SIZE_BYTES = 50 * 1024 * 1024;

        SqliteWriteHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case WRITE_DATABASE_PERIODIC -> {
                    try {
                        insertAppOpHistory(mCache.evict(),
                                SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_PERIODIC);
                    } finally {
                        ensurePeriodicJobsAreScheduled();
                        ensureDatabaseSize();
                    }
                }
                case WRITE_DATABASE_CACHE_FULL -> {
                    try {
                        List<AggregatedAppOpAccessEvent> evictedEvents;
                        synchronized (mCache) {
                            evictedEvents = mCache.evict();
                            // if nothing to evict, just write the whole cache to database.
                            if (evictedEvents.isEmpty()
                                    && mCache.size() >= mCache.capacity()) {
                                evictedEvents.addAll(mCache.evictAll());
                            }
                        }
                        insertAppOpHistory(evictedEvents,
                                SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_CACHE_FULL);
                    } finally {
                        ensurePeriodicJobsAreScheduled();
                        ensureDatabaseSize();
                    }
                }
                case DELETE_EXPIRED_ENTRIES_PERIODIC -> {
                    try {
                        long cutOffTimeStamp = System.currentTimeMillis() - mHistoryRetentionMillis;
                        mDbHelper.execSQL(
                                AppOpHistoryTable.DELETE_TABLE_DATA_BEFORE_ACCESS_TIME,
                                new Object[]{cutOffTimeStamp});
                    } finally {
                        ensurePeriodicJobsAreScheduled();
                    }
                }
                case CLOSE_DATABASE_ON_SHUTDOWN ->  {
                    mDbHelper.close();
                }
            }
        }

        private void ensureDatabaseSize() {
            long databaseSize = mDatabaseFile.length();
            if (databaseSize > MAX_DATABASE_SIZE_BYTES) {
                mDbHelper.execSQL(AppOpHistoryTable.DELETE_TABLE_DATA_LEAST_RECENT_ENTRIES);
            }
        }

        void removeAllPendingMessages() {
            removeMessages(WRITE_DATABASE_PERIODIC);
            removeMessages(DELETE_EXPIRED_ENTRIES_PERIODIC);
            removeMessages(WRITE_DATABASE_CACHE_FULL);
        }
    }

    void sendToMetricsHandler(@NonNull List<AggregatedAppOpAccessEvent> accessEvents) {
        Message msg = mMetricHandler.obtainMessage(MetricHandler.RECEIVE_EVENTS, accessEvents);
        mMetricHandler.sendMessage(msg);
    }

    private record AppOpMetricKey(
            @NonNull String packageName,
            int opCode,
            @Nullable String attributionTag
    ) {
    }

    private static class AppOpMetricValue {
        private long mForegroundDurationMillis;
        private int mForegroundAccessCount;
        private int mForegroundRejectCount;
        private long mBackgroundDurationMillis;
        private int mBackgroundAccessCount;
        private int mBackgroundRejectCount;

        AppOpMetricValue(
                long foregroundDurationMillis,
                int foregroundAccessCount,
                int foregroundRejectCount,
                long backgroundDurationMillis,
                int backgroundAccessCount,
                int backgroundRejectCount
        ) {
            this.mForegroundDurationMillis = foregroundDurationMillis;
            this.mForegroundAccessCount = foregroundAccessCount;
            this.mForegroundRejectCount = foregroundRejectCount;
            this.mBackgroundDurationMillis = backgroundDurationMillis;
            this.mBackgroundAccessCount = backgroundAccessCount;
            this.mBackgroundRejectCount = backgroundRejectCount;
        }

        void addForegroundDurationMillis(long foregroundDurationMillis) {
            this.mForegroundDurationMillis += foregroundDurationMillis;
        }

        void addForegroundAccessCount(int foregroundAccessCount) {
            this.mForegroundAccessCount += foregroundAccessCount;
        }

        void addForegroundRejectCount(int foregroundRejectCount) {
            this.mForegroundRejectCount += foregroundRejectCount;
        }

        void addBackgroundDurationMillis(long backgroundDurationMillis) {
            this.mBackgroundDurationMillis += backgroundDurationMillis;
        }

        void addBackgroundAccessCount(int backgroundAccessCount) {
            this.mBackgroundAccessCount += backgroundAccessCount;
        }

        void addBackgroundRejectCount(int backgroundRejectCount) {
            this.mBackgroundRejectCount += backgroundRejectCount;
        }
    }

    /**
     * This handler reports aggregated app op access events to StatsD.
     */
    private static class MetricHandler extends Handler {
        // Process events after 2 minutes once received by metrics handler.
        private static final long EVENT_PROCESSING_DELAY_MILLIS = Duration.ofMinutes(2).toMillis();
        // Process events immediately when reaching capacity limits.
        private static final int CACHE_CAPACITY = 128;
        private static final int OP_FLAGS_REPORTED = OP_FLAG_SELF | OP_FLAG_TRUSTED_PROXIED;
        static final int RECEIVE_EVENTS = 1;
        static final int SEND_EVENTS = 2;
        private final ArrayMap<AppOpMetricKey, AppOpMetricValue> mCache = new ArrayMap<>();

        MetricHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case RECEIVE_EVENTS -> {
                    List<AggregatedAppOpAccessEvent> events =
                            (List<AggregatedAppOpAccessEvent>) msg.obj;
                    if (events.isEmpty()) {
                        return;
                    }

                    synchronized (mCache) {
                        for (AggregatedAppOpAccessEvent accessEvent : events) {
                            if ((accessEvent.opFlags() & OP_FLAGS_REPORTED) == 0) {
                                continue;
                            }
                            AppOpMetricKey metricKey =
                                    new AppOpMetricKey(accessEvent.packageName(),
                                    accessEvent.opCode(), accessEvent.attributionTag());
                            boolean isUidStateInForeground =
                                    accessEvent.uidState() >= MAX_PRIORITY_UID_STATE
                                    && accessEvent.uidState() <= UID_STATE_MAX_LAST_NON_RESTRICTED;
                            long foregroundDurationMillis = 0;
                            int foregroundAccessCount = 0;
                            int foregroundRejectCount = 0;
                            long backgroundDurationMillis = 0;
                            int backgroundAccessCount = 0;
                            int backgroundRejectCount = 0;

                            if (isUidStateInForeground) {
                                foregroundDurationMillis = accessEvent.totalDurationMillis();
                                foregroundAccessCount = accessEvent.totalAccessCount();
                                foregroundRejectCount = accessEvent.totalRejectCount();
                            } else {
                                backgroundDurationMillis = accessEvent.totalDurationMillis();
                                backgroundAccessCount = accessEvent.totalAccessCount();
                                backgroundRejectCount = accessEvent.totalRejectCount();
                            }

                            AppOpMetricValue metricValue = mCache.get(metricKey);
                            if (metricValue != null) {
                                metricValue.addForegroundDurationMillis(foregroundDurationMillis);
                                metricValue.addForegroundAccessCount(foregroundAccessCount);
                                metricValue.addForegroundRejectCount(foregroundRejectCount);
                                metricValue.addBackgroundDurationMillis(backgroundDurationMillis);
                                metricValue.addBackgroundAccessCount(backgroundAccessCount);
                                metricValue.addBackgroundRejectCount(backgroundRejectCount);
                            } else {
                                mCache.put(metricKey, new AppOpMetricValue(
                                        foregroundDurationMillis,
                                        foregroundAccessCount,
                                        foregroundRejectCount,
                                        backgroundDurationMillis,
                                        backgroundAccessCount,
                                        backgroundRejectCount
                                ));
                            }
                        }

                        if (mCache.size() >= CACHE_CAPACITY) {
                            removeMessages(SEND_EVENTS); // Remove any pending delayed messages
                            sendEmptyMessage(SEND_EVENTS);
                        } else if (!hasMessages(SEND_EVENTS)) {
                            sendEmptyMessageDelayed(SEND_EVENTS, EVENT_PROCESSING_DELAY_MILLIS);
                        }
                    }
                }
                case SEND_EVENTS -> {
                    List<Pair<AppOpMetricKey, AppOpMetricValue>> events;
                    synchronized (mCache) {
                        if (mCache.isEmpty()) {
                            return;
                        }
                        events = new ArrayList<>(mCache.size());
                        for (Map.Entry<AppOpMetricKey, AppOpMetricValue> event: mCache.entrySet()) {
                            events.add(new Pair<>(event.getKey(), event.getValue()));
                        }
                        mCache.clear();
                    }
                    Slog.d(TAG, "MetricHandler reporting " + events.size() + " records.");
                    for (Pair<AppOpMetricKey, AppOpMetricValue> event : events) {
                        AppOpMetricKey metricKey = event.first;
                        AppOpMetricValue value = event.second;
                        FrameworkStatsLog.write(AGGREGATED_APP_OP_ACCESS_EVENT_REPORTED,
                                metricKey.packageName, metricKey.attributionTag,
                                metricKey.opCode, value.mForegroundAccessCount,
                                value.mBackgroundAccessCount, value.mForegroundRejectCount,
                                value.mBackgroundRejectCount, value.mForegroundDurationMillis,
                                value.mBackgroundDurationMillis);
                    }
                }
            }
        }
    }

    /**
     * A cache for aggregating app op accesses in a time window. Individual app op events
     * aren't stored on the disk, instead an aggregated events are persisted on the disk.
     *
     * <p>
     * These events are persisted into sqlite database
     * 1) Periodic interval.
     * 2) When the cache become full.
     * 3) During read call, flush the cache to disk to make read simpler.
     * 4) During shutdown.
     */
    class AppOpHistoryCache {
        private final int mCapacity;
        private final ArrayMap<AppOpAccessEvent, AggregatedAppOpValues> mCache;

        AppOpHistoryCache(int capacity) {
            mCapacity = capacity;
            // The initial capacity is set to 64 as a balanced compromise between performance
            // and memory usage for handling small bursts of app op events.
            // For example, the capacity of 32 would require three consecutive array expansions
            // to accommodate a 50 event burst, as the capacity grows from 32 → 48 → 72 → 108.
            mCache = new ArrayMap<>(64);
        }

        /**
         * Records an app op access event, aggregating access, reject count and duration.
         *
         * @param accessKey   Key to group/aggregate app op events in a time window.
         * @param accessCount Access counts to be aggregated for an event.
         * @param rejectCount Reject counts to be aggregated for an event.
         * @param duration    Access duration to be aggregated for an event.
         */
        private void insertOrUpdate(AppOpAccessEvent accessKey, int accessCount,
                int rejectCount, long duration) {
            synchronized (this) {
                AggregatedAppOpValues appOpAccessValue = mCache.get(accessKey);
                if (appOpAccessValue != null) {
                    appOpAccessValue.add(accessCount, rejectCount, duration);
                    return;
                }
                mCache.put(accessKey,
                        new AggregatedAppOpValues(accessCount, rejectCount, duration));
                if (mCache.size() >= mCapacity) {
                    mSqliteWriteHandler.sendEmptyMessage(WRITE_DATABASE_CACHE_FULL);
                }
            }
        }

        private int size() {
            return mCache.size();
        }

        private int capacity() {
            return mCapacity;
        }

        /**
         * Evict older events i.e. events from previous time windows.
         */
        private List<AggregatedAppOpAccessEvent> evict() {
            final long evictionTimestamp = discretizeTimestamp(
                    System.currentTimeMillis() - mQuantizationMillis);
            return evict(opAccessEvent -> opAccessEvent.mAccessTime <= evictionTimestamp);
        }

        private List<AggregatedAppOpAccessEvent> evict(Predicate<AppOpAccessEvent> predicate) {
            synchronized (this) {
                if (mCache.isEmpty()) {
                    return Collections.emptyList();
                }

                List<AggregatedAppOpAccessEvent> evictedOps = new ArrayList<>();
                List<AppOpAccessEvent> keysToBeRemoved = new ArrayList<>();
                for (Map.Entry<AppOpAccessEvent, AggregatedAppOpValues> event :
                        mCache.entrySet()) {
                    if (predicate.test(event.getKey())) {
                        keysToBeRemoved.add(event.getKey());
                        evictedOps.add(getAggregatedAppOpEvent(event.getKey(), event.getValue()));
                    }
                }
                for (AppOpAccessEvent eventKey : keysToBeRemoved) {
                    mCache.remove(eventKey);
                }
                return evictedOps;
            }
        }

        /**
         * Evict specified app ops from cache, and return the list of evicted ops.
         */
        private List<AggregatedAppOpAccessEvent> evict(IntArray ops) {
            return evict(appOpAccessEvent -> ops.contains(appOpAccessEvent.mOpCode));
        }

        /**
         * Remove all the entries from cache.
         *
         * @return return all removed entries.
         */
        private List<AggregatedAppOpAccessEvent> evictAll() {
            synchronized (this) {
                List<AggregatedAppOpAccessEvent> cachedOps = snapshot();
                mCache.clear();
                return cachedOps;
            }
        }

        private AggregatedAppOpAccessEvent getAggregatedAppOpEvent(AppOpAccessEvent accessEvent,
                AggregatedAppOpValues appOpValues) {
            return new AggregatedAppOpAccessEvent(accessEvent.mUid, accessEvent.mPackageName,
                    accessEvent.mOpCode, accessEvent.mDeviceId, accessEvent.mAttributionTag,
                    accessEvent.mOpFlags, accessEvent.mUidState, accessEvent.mAttributionFlags,
                    accessEvent.mAttributionChainId, accessEvent.mAccessTime,
                    accessEvent.mDuration, appOpValues.mTotalDuration,
                    appOpValues.mTotalAccessCount, appOpValues.mTotalRejectCount);
        }

        /**
         * Remove all entries from the cache.
         */
        private void clear() {
            synchronized (this) {
                mCache.clear();
            }
        }

        private List<AggregatedAppOpAccessEvent> snapshot() {
            List<AggregatedAppOpAccessEvent> events = new ArrayList<>();
            synchronized (this) {
                for (Map.Entry<AppOpAccessEvent, AggregatedAppOpValues> event :
                        mCache.entrySet()) {
                    events.add(getAggregatedAppOpEvent(event.getKey(), event.getValue()));
                }
            }
            return events;
        }

        /** Remove cached events for given UID and package. */
        private void clear(int uid, String packageName) {
            synchronized (this) {
                List<AppOpAccessEvent> keysToBeDeleted = new ArrayList<>();
                for (Map.Entry<AppOpAccessEvent, AggregatedAppOpValues> event :
                        mCache.entrySet()) {
                    if (Objects.equals(packageName, event.getKey().mPackageName)
                            && uid == event.getKey().mUid) {
                        keysToBeDeleted.add(event.getKey());
                    }
                }
                for (AppOpAccessEvent key : keysToBeDeleted) {
                    mCache.remove(key);
                }
            }
        }
    }

    /**
     * This class represents an individual app op access event. It is used as a key in the cache
     * to aggregate access counts during a time window. {@link #mDiscretizedAccessTime} and
     * {@link #mDiscretizedDuration} are used in {@link #equals} and {@link #hashCode}.
     */
    private record AppOpAccessEvent(
            int mUid,
            String mPackageName,
            int mOpCode,
            String mDeviceId,
            String mAttributionTag,
            int mOpFlags,
            int mUidState,
            int mAttributionFlags,
            long mAttributionChainId,
            long mAccessTime,
            long mDiscretizedAccessTime,
            long mDuration,
            long mDiscretizedDuration
    ) {
        public AppOpAccessEvent {
            if (mPackageName != null) {
                mPackageName = mPackageName.intern();
            }
            if (mAttributionTag != null) {
                mAttributionTag = mAttributionTag.intern();
            }
            if (mDeviceId != null) {
                mDeviceId = mDeviceId.intern();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AppOpAccessEvent that)) return false;
            return mUid == that.mUid
                    && mOpCode == that.mOpCode
                    && mOpFlags == that.mOpFlags
                    && mUidState == that.mUidState
                    && mAttributionFlags == that.mAttributionFlags
                    && mAttributionChainId == that.mAttributionChainId
                    && mDiscretizedAccessTime == that.mDiscretizedAccessTime
                    && mDiscretizedDuration == that.mDiscretizedDuration
                    && Objects.equals(mPackageName, that.mPackageName)
                    && Objects.equals(mDeviceId, that.mDeviceId)
                    && Objects.equals(mAttributionTag, that.mAttributionTag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mUid, mPackageName, mOpCode, mDeviceId, mAttributionTag,
                    mOpFlags, mUidState, mAttributionFlags, mAttributionChainId,
                    mDiscretizedAccessTime, mDiscretizedDuration);
        }

        @Override
        public String toString() {
            return "AppOpHistoryAccessKey{"
                    + "uid=" + mUid
                    + ", packageName='" + mPackageName + '\''
                    + ", attributionTag='" + mAttributionTag + '\''
                    + ", deviceId='" + mDeviceId + '\''
                    + ", opCode=" + AppOpsManager.opToName(mOpCode)
                    + ", opFlag=" + flagsToString(mOpFlags)
                    + ", uidState=" + getUidStateName(mUidState)
                    + ", attributionFlags=" + mAttributionFlags
                    + ", attributionChainId=" + mAttributionChainId
                    + ", mDuration=" + mDuration
                    + ", mAccessTime=" + mAccessTime + '}';
        }
    }

    private static final class AggregatedAppOpValues {
        private int mTotalAccessCount;
        private int mTotalRejectCount;
        private long mTotalDuration;

        AggregatedAppOpValues(int totalAccessCount, int totalRejectCount, long totalDuration) {
            mTotalAccessCount = totalAccessCount;
            mTotalRejectCount = totalRejectCount;
            mTotalDuration = totalDuration;
        }

        private void add(int accessCount, int rejectCount, long totalDuration) {
            mTotalAccessCount += accessCount;
            mTotalRejectCount += rejectCount;
            mTotalDuration += totalDuration;
        }
    }

    static class AttributionChain {
        List<AggregatedAppOpAccessEvent> mChain = new ArrayList<>();
        Set<String> mExemptPkgs;
        AggregatedAppOpAccessEvent mStartEvent = null;
        AggregatedAppOpAccessEvent mLastVisibleEvent = null;

        AttributionChain(Set<String> exemptPkgs) {
            mExemptPkgs = exemptPkgs;
        }

        boolean isComplete() {
            return !mChain.isEmpty() && getStart() != null && isEnd(mChain.get(mChain.size() - 1));
        }

        AggregatedAppOpAccessEvent getStart() {
            return mChain.isEmpty() || !isStart(mChain.get(0)) ? null : mChain.get(0);
        }

        private boolean isEnd(AggregatedAppOpAccessEvent event) {
            return event != null
                    && (event.attributionFlags() & ATTRIBUTION_FLAG_ACCESSOR) != 0;
        }

        private boolean isStart(AggregatedAppOpAccessEvent event) {
            return event != null
                    && (event.attributionFlags() & ATTRIBUTION_FLAG_RECEIVER) != 0;
        }

        AggregatedAppOpAccessEvent getLastVisible() {
            // Search all nodes but the first one, which is the start node
            for (int i = mChain.size() - 1; i > 0; i--) {
                AggregatedAppOpAccessEvent event = mChain.get(i);
                if (!mExemptPkgs.contains(event.packageName())) {
                    return event;
                }
            }
            return null;
        }

        boolean equalsExceptDuration(AggregatedAppOpAccessEvent obj1,
                AggregatedAppOpAccessEvent obj2) {
            if (obj1.uid() != obj2.uid()) return false;
            if (obj1.opCode() != obj2.opCode()) return false;
            if (obj1.opFlags() != obj2.opFlags()) return false;
            if (obj1.attributionFlags() != obj2.attributionFlags()) return false;
            if (obj1.uidState() != obj2.uidState()) return false;
            if (obj1.attributionChainId() != obj2.attributionChainId()) return false;
            if (!Objects.equals(obj1.packageName(), obj2.packageName())) {
                return false;
            }
            if (!Objects.equals(obj1.attributionTag(), obj2.attributionTag())) {
                return false;
            }
            if (!Objects.equals(obj1.deviceId(), obj2.deviceId())) {
                return false;
            }
            return obj1.accessTimeMillis() == obj2.accessTimeMillis();
        }

        void addEvent(AggregatedAppOpAccessEvent opEvent) {
            // check if we have a matching event except duration.
            AggregatedAppOpAccessEvent matchingItem = null;
            for (int i = 0; i < mChain.size(); i++) {
                AggregatedAppOpAccessEvent item = mChain.get(i);
                if (equalsExceptDuration(item, opEvent)) {
                    matchingItem = item;
                    break;
                }
            }

            if (matchingItem != null) {
                // exact match or existing event has longer duration
                if (matchingItem.durationMillis() == opEvent.durationMillis()
                        || matchingItem.durationMillis() > opEvent.durationMillis()) {
                    return;
                }
                mChain.remove(matchingItem);
            }

            if (mChain.isEmpty() || isEnd(opEvent)) {
                mChain.add(opEvent);
            } else if (isStart(opEvent)) {
                mChain.add(0, opEvent);
            } else {
                for (int i = 0; i < mChain.size(); i++) {
                    AggregatedAppOpAccessEvent currEvent = mChain.get(i);
                    if ((!isStart(currEvent)
                            && currEvent.accessTimeMillis() > opEvent.accessTimeMillis())
                            || (i == mChain.size() - 1 && isEnd(currEvent))) {
                        mChain.add(i, opEvent);
                        break;
                    } else if (i == mChain.size() - 1) {
                        mChain.add(opEvent);
                        break;
                    }
                }
            }
            mStartEvent = isComplete() ? getStart() : null;
            mLastVisibleEvent = isComplete() ? getLastVisible() : null;
        }
    }
}
