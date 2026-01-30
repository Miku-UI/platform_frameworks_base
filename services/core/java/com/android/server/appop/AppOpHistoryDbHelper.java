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

import static com.android.server.appop.HistoricalRegistry.AggregationTimeWindow;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.companion.virtual.VirtualDeviceManager;
import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.DefaultDatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteRawStatement;
import android.os.Process;
import android.os.SystemClock;
import android.os.Trace;
import android.util.ArraySet;
import android.util.IntArray;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FrameworkStatsLog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sqlite database helper to read/write app op events.
 */
class AppOpHistoryDbHelper extends SQLiteOpenHelper {
    private static final String LOG_TAG = "AppOpHistoryDbHelper";
    private final File mDatabaseFile;
    private static final boolean DEBUG = false;
    private final AggregationTimeWindow mAggregationTimeWindow;

    AppOpHistoryDbHelper(@NonNull Context context, @NonNull File databaseFile,
            AggregationTimeWindow aggregationTimeWindow, int databaseVersion) {
        super(context, databaseFile.getAbsolutePath(), null, databaseVersion,
                new AppOpHistoryDatabaseErrorHandler());
        mDatabaseFile = databaseFile;
        mAggregationTimeWindow = aggregationTimeWindow;
        setOpenParams(getDatabaseOpenParams());
    }

    private static SQLiteDatabase.OpenParams getDatabaseOpenParams() {
        return new SQLiteDatabase.OpenParams.Builder()
                .addOpenFlags(SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
                .build();
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.execSQL("PRAGMA synchronous = NORMAL");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(AppOpHistoryTable.CREATE_TABLE_SQL);
        // Create index for discrete ops only, as they are read often for privacy dashboard.
        if (mAggregationTimeWindow == AggregationTimeWindow.SHORT) {
            db.execSQL(AppOpHistoryTable.CREATE_INDEX_SQL);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    void insertAppOpHistory(@NonNull List<AggregatedAppOpAccessEvent> appOpEvents,
            int writeSource) {
        if (appOpEvents.isEmpty()) {
            return;
        }
        long startTime = SystemClock.uptimeMillis();
        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER,
                "AppOpHistoryDbHelper_" + mAggregationTimeWindow + "_Write");
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try (SQLiteRawStatement statement = db.createRawStatement(
                    AppOpHistoryTable.INSERT_TABLE_SQL)) {
                for (AggregatedAppOpAccessEvent event : appOpEvents) {
                    try {
                        statement.bindInt(AppOpHistoryTable.UID_INDEX, event.uid());
                        bindTextOrNull(statement, AppOpHistoryTable.PACKAGE_NAME_INDEX,
                                event.packageName());
                        bindTextOrNull(statement, AppOpHistoryTable.DEVICE_ID_INDEX,
                                getDeviceIdForDatabaseWrite(event.deviceId()));
                        statement.bindInt(AppOpHistoryTable.OP_CODE_INDEX, event.opCode());
                        bindTextOrNull(statement, AppOpHistoryTable.ATTRIBUTION_TAG_INDEX,
                                event.attributionTag());
                        statement.bindInt(AppOpHistoryTable.UID_STATE_INDEX,
                                event.uidState());
                        statement.bindInt(AppOpHistoryTable.OP_FLAGS_INDEX,
                                event.opFlags());
                        statement.bindLong(AppOpHistoryTable.ATTRIBUTION_FLAGS_INDEX,
                                event.attributionFlags());
                        statement.bindLong(AppOpHistoryTable.CHAIN_ID_INDEX,
                                event.attributionChainId());
                        statement.bindLong(AppOpHistoryTable.ACCESS_TIME_INDEX,
                                event.accessTimeMillis());
                        statement.bindLong(
                                AppOpHistoryTable.DURATION_INDEX, event.durationMillis());
                        statement.bindLong(
                                AppOpHistoryTable.TOTAL_DURATION_INDEX,
                                event.totalDurationMillis());
                        statement.bindInt(AppOpHistoryTable.ACCESS_COUNT_INDEX,
                                event.totalAccessCount());
                        statement.bindLong(AppOpHistoryTable.REJECT_COUNT_INDEX,
                                event.totalRejectCount());
                        statement.step();
                    } catch (Exception exception) {
                        Slog.e(LOG_TAG, "Couldn't insert app op event: " + event + ", database "
                                + mDatabaseFile.getName(), exception);
                    } finally {
                        statement.reset();
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                try {
                    db.endTransaction();
                } catch (SQLiteException exception) {
                    Slog.e(LOG_TAG, "Couldn't commit transaction inserting app ops, database"
                            + mDatabaseFile.getName() + ", file size (bytes) : "
                            + mDatabaseFile.length(), exception);
                }
            }
        } catch (Exception ex) {
            Slog.e(LOG_TAG, "Couldn't insert app op records in " + mDatabaseFile.getName(), ex);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
            long writeTimeMillis = SystemClock.uptimeMillis() - startTime;
            FrameworkStatsLog.write(FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED, /* read_time= */
                    -1, writeTimeMillis,
                    mDatabaseFile.length(), getDatabaseType(mAggregationTimeWindow), writeSource);
        }
    }

    // Save disk space, use null as almost all entries would be for default device only.
    private @Nullable String getDeviceIdForDatabaseWrite(@NonNull String deviceId) {
        Objects.requireNonNull(deviceId);
        return Objects.equals(VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, deviceId)
                ? null : deviceId;
    }

    // Convert null back to default device.
    private @NonNull String getDeviceIdForDatabaseRead(@Nullable String deviceId) {
        return deviceId == null ?
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT : deviceId;
    }

    private int getDatabaseType(AggregationTimeWindow aggregationTimeWindow) {
        if (aggregationTimeWindow == AggregationTimeWindow.SHORT) {
            return FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__DATABASE_TYPE__DB_SHORT_INTERVAL;
        } else {
            return FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__DATABASE_TYPE__DB_LONG_INTERVAL;
        }
    }

    List<AggregatedAppOpAccessEvent> getAppOpHistory(
            @AppOpsManager.HistoricalOpsRequestFilter int requestFilters, long beginTime,
            long endTime, int uidFilter, @Nullable String packageNameFilter,
            @Nullable String attributionTagFilter, IntArray opCodeFilter, int opFlagsFilter,
            int limit, String orderByColumn, boolean ascending) {
        List<AggregatedAppOpAccessEvent> results = new ArrayList<>();
        List<AppOpHistoryQueryHelper.SQLCondition> conditions =
                AppOpHistoryQueryHelper.prepareConditions(
                        beginTime, endTime, requestFilters, uidFilter, packageNameFilter,
                        attributionTagFilter, opCodeFilter, opFlagsFilter);
        String sql = AppOpHistoryQueryHelper.buildSqlQuery(
                AppOpHistoryTable.SELECT_TABLE_DATA, conditions, orderByColumn, ascending, limit);

        long startTime = SystemClock.uptimeMillis();
        try {
            SQLiteDatabase db = getReadableDatabase();
            db.beginTransactionReadOnly();
            try (SQLiteRawStatement statement = db.createRawStatement(sql)) {
                AppOpHistoryQueryHelper.bindValues(statement, conditions);

                while (statement.step()) {
                    results.add(readFromStatement(statement));
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception ex) {
            Slog.e(LOG_TAG, "Couldn't read app op records from " + mDatabaseFile.getName(), ex);
        } finally {
            long readTimeMillis = SystemClock.uptimeMillis() - startTime;
            FrameworkStatsLog.write(FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED,
                    readTimeMillis, /* write_time= */ -1,
                    mDatabaseFile.length(), getDatabaseType(mAggregationTimeWindow),
                    FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_UNKNOWN);
        }
        Slog.d(LOG_TAG, "Read " + results.size() + " records from " + mDatabaseFile.getName());
        return results;
    }

    /**
     * This will be used as an offset for inserting new chain id in discrete ops table.
     */
    long getLargestAttributionChainId() {
        long chainId = 0;
        try {
            SQLiteDatabase db = getReadableDatabase();
            db.beginTransactionReadOnly();
            try (SQLiteRawStatement statement = db.createRawStatement(
                    AppOpHistoryTable.SELECT_MAX_ATTRIBUTION_CHAIN_ID)) {
                if (statement.step()) {
                    chainId = statement.getColumnLong(0);
                    if (chainId < 0) {
                        chainId = 0;
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (SQLiteException exception) {
            Slog.e(LOG_TAG, "Error reading chain id " + mDatabaseFile.getName(), exception);
        }
        return chainId;
    }

    long getTotalRecordsCount() {
        long count = 0;
        try {
            SQLiteDatabase db = getReadableDatabase();
            db.beginTransactionReadOnly();
            try (SQLiteRawStatement statement = db.createRawStatement(
                    AppOpHistoryTable.SELECT_RECORDS_COUNT)) {
                if (statement.step()) {
                    count = statement.getColumnLong(0);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (SQLiteException exception) {
            Slog.e(LOG_TAG, "Error reading records count " + mDatabaseFile.getName(), exception);
        }
        return count;
    }

    @NonNull
    ArraySet<String> getRecentlyUsedPackageNames(@NonNull String[] opNames,
            @AppOpsManager.HistoricalOpsRequestFilter int filter, long beginTimeMillis,
            long endTimeMillis, @AppOpsManager.OpFlags int opFlags) {
        ArraySet<String> results = new ArraySet<>();
        IntArray opCodes = AppOpHistoryQueryHelper.getAppOpCodes(filter, opNames);
        if (opCodes == null || opCodes.size() == 0) {
            return results;
        }

        List<AppOpHistoryQueryHelper.SQLCondition> conditions =
                AppOpHistoryQueryHelper.prepareConditions(
                        beginTimeMillis, endTimeMillis, filter, Process.INVALID_UID,
                        null /*packageNameFilter*/, null /*attributionTagFilter*/,
                        opCodes, opFlags);
        String sql = AppOpHistoryQueryHelper.buildSqlQuery(
                AppOpHistoryTable.SELECT_DISTINCT_PACKAGE_NAMES, conditions, null, false, -1);
        Slog.d(LOG_TAG, "getRecentlyUsedPackageNames sql: " + sql);
        long startTime = SystemClock.uptimeMillis();
        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER,
                "AppOpHistoryDbHelper_" + mAggregationTimeWindow + "_Read2");
        try {
            SQLiteDatabase db = getReadableDatabase();
            db.beginTransactionReadOnly();
            try (SQLiteRawStatement statement = db.createRawStatement(sql)) {
                AppOpHistoryQueryHelper.bindValues(statement, conditions);
                while (statement.step()) {
                    results.add(statement.getColumnText(0));
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception exception) {
            Slog.e(LOG_TAG, "Error reading recently used packages from "
                    + mDatabaseFile.getName(), exception);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
            long readTimeMillis = SystemClock.uptimeMillis() - startTime;
            FrameworkStatsLog.write(FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED,
                    readTimeMillis, /* write_time= */ -1,
                    mDatabaseFile.length(), getDatabaseType(mAggregationTimeWindow),
                    FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_UNKNOWN);
        }
        Slog.d(LOG_TAG, "Read " + results.size() + " packages from " + mDatabaseFile.getName());
        return results;
    }

    @VisibleForTesting
    List<AggregatedAppOpAccessEvent> getAppOpHistory() {
        List<AggregatedAppOpAccessEvent> results = new ArrayList<>();

        SQLiteDatabase db = getReadableDatabase();
        db.beginTransactionReadOnly();
        try (SQLiteRawStatement statement =
                     db.createRawStatement(AppOpHistoryTable.SELECT_TABLE_DATA)) {
            while (statement.step()) {
                AggregatedAppOpAccessEvent event = readFromStatement(statement);
                results.add(event);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return results;
    }

    private void bindTextOrNull(SQLiteRawStatement statement, int index, @Nullable String text) {
        if (text == null) {
            statement.bindNull(index);
        } else {
            statement.bindText(index, text);
        }
    }

    void execSQL(@NonNull String sql) {
        execSQL(sql, null);
    }

    void execSQL(@NonNull String sql, Object[] bindArgs) {
        if (DEBUG) {
            Slog.i(LOG_TAG, "DB execSQL, sql: " + sql);
        }
        SQLiteDatabase db = getWritableDatabase();
        if (bindArgs == null) {
            db.execSQL(sql);
        } else {
            db.execSQL(sql, bindArgs);
        }
    }

    private AggregatedAppOpAccessEvent readFromStatement(SQLiteRawStatement statement) {
        int uid = statement.getColumnInt(0);
        String packageName = statement.getColumnText(1);
        String deviceId = getDeviceIdForDatabaseRead(statement.getColumnText(2));
        int opCode = statement.getColumnInt(3);
        String attributionTag = statement.getColumnText(4);
        int uidState = statement.getColumnInt(5);
        int opFlags = statement.getColumnInt(6);
        int attributionFlags = statement.getColumnInt(7);
        int attributionChainId = statement.getColumnInt(8);
        long accessTime = statement.getColumnLong(9);
        long duration = statement.getColumnLong(10);
        long totalDuration = statement.getColumnLong(11);
        int totalAccessCount = statement.getColumnInt(12);
        int totalRejectCount = statement.getColumnInt(13);

        return new AggregatedAppOpAccessEvent(uid, packageName, opCode, deviceId, attributionTag,
                opFlags, uidState, attributionFlags, attributionChainId, accessTime,
                duration, totalDuration, totalAccessCount, totalRejectCount);
    }

    static final class AppOpHistoryDatabaseErrorHandler implements DatabaseErrorHandler {
        private final DefaultDatabaseErrorHandler mDefaultDatabaseErrorHandler =
                new DefaultDatabaseErrorHandler();

        @Override
        public void onCorruption(SQLiteDatabase dbObj) {
            Slog.e(LOG_TAG, "app ops database " + dbObj.getPath() + " got corrupted.");
            mDefaultDatabaseErrorHandler.onCorruption(dbObj);
        }
    }
}
