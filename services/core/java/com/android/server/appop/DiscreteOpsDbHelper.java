/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.DefaultDatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteRawStatement;
import android.os.Environment;
import android.os.SystemClock;
import android.os.Trace;
import android.permission.flags.Flags;
import android.util.IntArray;
import android.util.Slog;

import com.android.internal.util.FrameworkStatsLog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class DiscreteOpsDbHelper extends SQLiteOpenHelper {
    private static final String LOG_TAG = "DiscreteOpsDbHelper";
    static final String DATABASE_NAME = "app_op_history.db";
    private static final int DATABASE_VERSION = 1;
    private static final boolean DEBUG = false;

    DiscreteOpsDbHelper(@NonNull Context context, @NonNull File databaseFile) {
        super(context, databaseFile.getAbsolutePath(), null, DATABASE_VERSION,
                new DiscreteOpsDatabaseErrorHandler());
        setOpenParams(getDatabaseOpenParams());
    }

    private static SQLiteDatabase.OpenParams getDatabaseOpenParams() {
        return new SQLiteDatabase.OpenParams.Builder()
                .addOpenFlags(SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
                .build();
    }

    @NonNull
    static File getDatabaseFile() {
        return new File(new File(Environment.getDataSystemDirectory(), "appops"), DATABASE_NAME);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.execSQL("PRAGMA synchronous = NORMAL");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DiscreteOpsTable.CREATE_TABLE_SQL);
        db.execSQL(DiscreteOpsTable.CREATE_INDEX_SQL);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    void insertDiscreteOps(@NonNull List<DiscreteOpsSqlRegistry.DiscreteOp> opEvents) {
        if (opEvents.isEmpty()) {
            return;
        }
        long startTime = 0;
        if (Flags.sqliteDiscreteOpEventLoggingEnabled()) {
            startTime = SystemClock.uptimeMillis();
        }

        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER, "DiscreteOpsWrite");
        try {
            SQLiteDatabase db = getWritableDatabase();
            // TODO (b/383157289) what if database is busy and can't start a transaction? will read
            //  more about it and can be done in a follow up cl.
            db.beginTransaction();
            try (SQLiteRawStatement statement = db.createRawStatement(
                    DiscreteOpsTable.INSERT_TABLE_SQL)) {
                for (DiscreteOpsSqlRegistry.DiscreteOp event : opEvents) {
                    try {
                        statement.bindInt(DiscreteOpsTable.UID_INDEX, event.getUid());
                        bindTextOrNull(statement, DiscreteOpsTable.PACKAGE_NAME_INDEX,
                                event.getPackageName());
                        bindTextOrNull(statement, DiscreteOpsTable.DEVICE_ID_INDEX,
                                event.getDeviceId());
                        statement.bindInt(DiscreteOpsTable.OP_CODE_INDEX, event.getOpCode());
                        bindTextOrNull(statement, DiscreteOpsTable.ATTRIBUTION_TAG_INDEX,
                                event.getAttributionTag());
                        statement.bindLong(DiscreteOpsTable.ACCESS_TIME_INDEX,
                                event.getAccessTime());
                        statement.bindLong(
                                DiscreteOpsTable.ACCESS_DURATION_INDEX, event.getDuration());
                        statement.bindInt(DiscreteOpsTable.UID_STATE_INDEX, event.getUidState());
                        statement.bindInt(DiscreteOpsTable.OP_FLAGS_INDEX, event.getOpFlags());
                        statement.bindInt(DiscreteOpsTable.ATTRIBUTION_FLAGS_INDEX,
                                event.getAttributionFlags());
                        statement.bindLong(DiscreteOpsTable.CHAIN_ID_INDEX, event.getChainId());
                        statement.step();
                    } catch (Exception exception) {
                        Slog.e(LOG_TAG, "Error inserting the discrete op: " + event, exception);
                    } finally {
                        statement.reset();
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                try {
                    db.endTransaction();
                } catch (SQLiteException exception) {
                    Slog.e(LOG_TAG,
                            "Couldn't commit transaction when inserting discrete ops, database"
                                    + " file size (bytes) : " + getDatabaseFile().length(),
                            exception);
                }

            }
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
        }
        if (Flags.sqliteDiscreteOpEventLoggingEnabled()) {
            long timeTaken = SystemClock.uptimeMillis() - startTime;
            FrameworkStatsLog.write(FrameworkStatsLog.SQLITE_DISCRETE_OP_EVENT_REPORTED,
                    -1, timeTaken, getDatabaseFile().length());
        }
    }

    private void bindTextOrNull(SQLiteRawStatement statement, int index, @Nullable String text) {
        if (text == null) {
            statement.bindNull(index);
        } else {
            statement.bindText(index, text);
        }
    }

    /**
     * This will be used as an offset for inserting new chain id in discrete ops table.
     */
    long getLargestAttributionChainId() {
        long chainId = 0;
        try {
            SQLiteDatabase db = getReadableDatabase();
            db.beginTransactionReadOnly();
            try (SQLiteRawStatement statement =
                     db.createRawStatement(DiscreteOpsTable.SELECT_MAX_ATTRIBUTION_CHAIN_ID)) {
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
            Slog.e(LOG_TAG, "Error reading attribution chain id", exception);
        }
        return chainId;
    }

    void execSQL(@NonNull String sql) {
        execSQL(sql, null);
    }

    void execSQL(@NonNull String sql, Object[] bindArgs) {
        if (DEBUG) {
            Slog.i(LOG_TAG, "DB execSQL, sql: " + sql);
        }
        try {
            SQLiteDatabase db = getWritableDatabase();
            if (bindArgs == null) {
                db.execSQL(sql);
            } else {
                db.execSQL(sql, bindArgs);
            }
        } catch (SQLiteFullException exception) {
            Slog.e(LOG_TAG, "Couldn't exec sql command, disk is full. Discrete ops "
                    + "db file size (bytes) : " + getDatabaseFile().length(), exception);
        }
    }

    /**
     * Returns a list of {@link DiscreteOpsSqlRegistry.DiscreteOp} based on the given filters.
     */
    List<DiscreteOpsSqlRegistry.DiscreteOp> getDiscreteOps(
            @AppOpsManager.HistoricalOpsRequestFilter int requestFilters,
            int uidFilter, @Nullable String packageNameFilter,
            @Nullable String attributionTagFilter, IntArray opCodesFilter, int opFlagsFilter,
            long beginTime, long endTime, int limit, String orderByColumn, boolean ascending) {

        List<AppOpHistoryQueryHelper.SQLCondition> conditions =
                AppOpHistoryQueryHelper.prepareConditions(
                beginTime, endTime, requestFilters,
                uidFilter, packageNameFilter,
                attributionTagFilter, opCodesFilter, opFlagsFilter);
        String sql = AppOpHistoryQueryHelper.buildSqlQuery(
                DiscreteOpsTable.SELECT_TABLE_DATA, conditions, orderByColumn,
                ascending, limit);
        long startTime = 0;
        if (Flags.sqliteDiscreteOpEventLoggingEnabled()) {
            startTime = SystemClock.uptimeMillis();
        }
        SQLiteDatabase db = getReadableDatabase();
        List<DiscreteOpsSqlRegistry.DiscreteOp> results = new ArrayList<>();
        db.beginTransactionReadOnly();
        try (SQLiteRawStatement statement = db.createRawStatement(sql)) {
            int size = conditions.size();
            for (int i = 0; i < size; i++) {
                AppOpHistoryQueryHelper.SQLCondition condition = conditions.get(i);
                if (DEBUG) {
                    Slog.i(LOG_TAG, condition + ", binding value = " + condition.getFilterValue());
                }
                switch (condition.getColumnFilter()) {
                    case PACKAGE_NAME, ATTR_TAG -> statement.bindText(i + 1,
                            condition.getFilterValue().toString());
                    case UID, OP_CODE_EQUAL, OP_FLAGS -> statement.bindInt(i + 1,
                            Integer.parseInt(condition.getFilterValue().toString()));
                    case BEGIN_TIME, END_TIME -> statement.bindLong(i + 1,
                            Long.parseLong(condition.getFilterValue().toString()));
                    case OP_CODE_IN -> Slog.d(LOG_TAG, "No binding for In operator");
                    default -> Slog.w(LOG_TAG, "unknown sql condition " + condition);
                }
            }

            while (statement.step()) {
                DiscreteOpsSqlRegistry.DiscreteOp event = readFromStatement(statement);
                results.add(event);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (Flags.sqliteDiscreteOpEventLoggingEnabled()) {
            long timeTaken = SystemClock.uptimeMillis() - startTime;
            FrameworkStatsLog.write(FrameworkStatsLog.SQLITE_DISCRETE_OP_EVENT_REPORTED,
                    timeTaken, -1, getDatabaseFile().length());
        }
        return results;
    }

    private DiscreteOpsSqlRegistry.DiscreteOp readFromStatement(SQLiteRawStatement statement) {
        int uid = statement.getColumnInt(0);
        String packageName = statement.getColumnText(1);
        String deviceId = statement.getColumnText(2);
        int opCode = statement.getColumnInt(3);
        String attributionTag = statement.getColumnText(4);
        long accessTime = statement.getColumnLong(5);
        long duration = statement.getColumnLong(6);
        int uidState = statement.getColumnInt(7);
        int opFlags = statement.getColumnInt(8);
        int attributionFlags = statement.getColumnInt(9);
        long chainId = statement.getColumnLong(10);

        return new DiscreteOpsSqlRegistry.DiscreteOp(uid,
                packageName, attributionTag, deviceId, opCode,
                opFlags, attributionFlags, uidState, chainId, accessTime, duration);
    }

    static final class DiscreteOpsDatabaseErrorHandler implements DatabaseErrorHandler {
        private final DefaultDatabaseErrorHandler mDefaultDatabaseErrorHandler =
                new DefaultDatabaseErrorHandler();

        @Override
        public void onCorruption(SQLiteDatabase dbObj) {
            Slog.e(LOG_TAG, "discrete ops database got corrupted.");
            mDefaultDatabaseErrorHandler.onCorruption(dbObj);
        }
    }

    // USED for testing only
    List<DiscreteOpsSqlRegistry.DiscreteOp> getAllDiscreteOps(@NonNull String sql) {
        SQLiteDatabase db = getReadableDatabase();
        List<DiscreteOpsSqlRegistry.DiscreteOp> results = new ArrayList<>();
        db.beginTransactionReadOnly();
        try (SQLiteRawStatement statement = db.createRawStatement(sql)) {
            while (statement.step()) {
                DiscreteOpsSqlRegistry.DiscreteOp event = readFromStatement(statement);
                results.add(event);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return results;
    }
}
