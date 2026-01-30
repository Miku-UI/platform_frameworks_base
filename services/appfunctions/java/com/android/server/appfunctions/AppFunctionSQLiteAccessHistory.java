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

package com.android.server.appfunctions;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionAttribution;
import android.app.appfunctions.AppFunctionManager.AccessHistory;
import android.app.appfunctions.ExecuteAppFunctionAidlRequest;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;
import android.util.Slog;

import java.util.Objects;

/** The database helper for managing AppFunction access histories. */
public final class AppFunctionSQLiteAccessHistory extends SQLiteOpenHelper
        implements AppFunctionAccessHistory {

    private static final class AccessHistoryTable {
        static final String DB_TABLE = "appfunction_access";

        static final String CREATE_TABLE_SQL =
                "CREATE TABLE IF NOT EXISTS "
                        + DB_TABLE
                        + " ("
                        + AccessHistory._ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + AccessHistory.COLUMN_AGENT_PACKAGE_NAME
                        + " TEXT, "
                        + AccessHistory.COLUMN_TARGET_PACKAGE_NAME
                        + " TEXT, "
                        + AccessHistory.COLUMN_INTERACTION_TYPE
                        + " INTEGER, "
                        + AccessHistory.COLUMN_CUSTOM_INTERACTION_TYPE
                        + " TEXT, "
                        + AccessHistory.COLUMN_INTERACTION_URI
                        + " TEXT, "
                        + AccessHistory.COLUMN_THREAD_ID
                        + " TEXT, "
                        + AccessHistory.COLUMN_ACCESS_TIME
                        + " INTEGER, "
                        + AccessHistory.COLUMN_DURATION
                        + " INTEGER);";

        static final String DELETE_TABLE_DATA_BEFORE_ACCESS_TIME =
                "DELETE FROM " + DB_TABLE + " WHERE " + AccessHistory.COLUMN_ACCESS_TIME + " < ?";

        static final String DELETE_TABLE_DATA_FOR_PACKAGE =
                "DELETE FROM "
                        + DB_TABLE
                        + " WHERE "
                        + AccessHistory.COLUMN_AGENT_PACKAGE_NAME
                        + " = ?"
                        + " OR "
                        + AccessHistory.COLUMN_TARGET_PACKAGE_NAME
                        + " = ?";

        static final String DELETE_TABLE_DATA = "DELETE FROM " + DB_TABLE;
    }

    private static final String DB_NAME = "appfunction_access.db";

    private static final int DB_VERSION = 1;

    private static final String TAG = "AppFunctionDatabase";

    AppFunctionSQLiteAccessHistory(@NonNull Context context) {
        super(context, DB_NAME, /* factory= */ null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            db.execSQL(AccessHistoryTable.CREATE_TABLE_SQL);
        } catch (Exception e) {
            Log.e(TAG, "CreateTable: Failed.");
            throw e;
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    @Override
    @Nullable
    public Cursor queryAppFunctionAccessHistory(
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        try {
            final SQLiteDatabase db = getReadableDatabase();
            final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
            qb.setStrict(/* strict= */ true);
            qb.setTables(AccessHistoryTable.DB_TABLE);
            Cursor cursor =
                    qb.query(
                            db,
                            projection,
                            selection,
                            selectionArgs,
                            /* groupBy= */ null,
                            /* having= */ null,
                            sortOrder);
            if (cursor == null) {
                Log.e(TAG, "Query AppFunction access histories: Failed.");
                return null;
            }
            return cursor;
        } catch (Exception e) {
            Log.e(TAG, "Query AppFunction access histories: Failed.", e);
            return null;
        }
    }

    @Override
    public long insertAppFunctionAccessHistory(
            @NonNull ExecuteAppFunctionAidlRequest aidlRequest, long accessTime, long duration) {
        Objects.requireNonNull(aidlRequest);

        try {
            final SQLiteDatabase db = getWritableDatabase();
            final ContentValues values =
                    prepareAppFunctionAccessContentValue(aidlRequest, accessTime, duration);

            return db.insert(AccessHistoryTable.DB_TABLE, /* nullColumnHack= */ null, values);
        } catch (Exception e) {
            Slog.e(TAG, "Insert AppFunction access histories: Failed.", e);
            return -1;
        }
    }

    @NonNull
    private ContentValues prepareAppFunctionAccessContentValue(
            @NonNull ExecuteAppFunctionAidlRequest aidlRequest, long accessTime, long duration) {
        Objects.requireNonNull(aidlRequest);

        final ContentValues values = new ContentValues();

        values.put(
                AccessHistory.COLUMN_AGENT_PACKAGE_NAME,
                Objects.requireNonNull(aidlRequest.getCallingPackage()));
        final String targetPackage =
                Objects.requireNonNull(aidlRequest.getClientRequest().getTargetPackageName());
        values.put(AccessHistory.COLUMN_TARGET_PACKAGE_NAME, targetPackage);
        values.put(AccessHistory.COLUMN_ACCESS_TIME, accessTime);
        values.put(AccessHistory.COLUMN_DURATION, duration);

        final AppFunctionAttribution attribution = aidlRequest.getClientRequest().getAttribution();

        if (attribution != null) {
            values.put(AccessHistory.COLUMN_INTERACTION_TYPE, attribution.getInteractionType());
            final String customInteractionType = attribution.getCustomInteractionType();
            if (customInteractionType != null) {
                values.put(AccessHistory.COLUMN_CUSTOM_INTERACTION_TYPE, customInteractionType);
            }
            final Uri interactionUri = attribution.getInteractionUri();
            if (interactionUri != null) {
                values.put(AccessHistory.COLUMN_INTERACTION_URI, interactionUri.toString());
            }
            final String threadId = attribution.getThreadId();
            if (threadId != null) {
                values.put(AccessHistory.COLUMN_THREAD_ID, threadId);
            }
        }

        return values;
    }

    @Override
    public void deleteExpiredAppFunctionAccessHistories(long retentionMillis) {
        try {
            final SQLiteDatabase db = getWritableDatabase();
            final long cutOffTimestamp = System.currentTimeMillis() - retentionMillis;

            final String[] whereArgs = {String.valueOf(cutOffTimestamp)};
            db.execSQL(AccessHistoryTable.DELETE_TABLE_DATA_BEFORE_ACCESS_TIME, whereArgs);
        } catch (Exception e) {
            Slog.e(TAG, "Delete expired AppFunction access histories: Failed", e);
        }
    }

    @Override
    public void deleteAppFunctionAccessHistories(@NonNull String packageName) {
        Objects.requireNonNull(packageName);

        try {
            final SQLiteDatabase db = getWritableDatabase();

            final String[] whereArgs = {packageName, packageName};
            db.execSQL(AccessHistoryTable.DELETE_TABLE_DATA_FOR_PACKAGE, whereArgs);
        } catch (Exception e) {
            Slog.e(
                    TAG,
                    "Delete AppFunction access histories for "
                            + "{packageName="
                            + packageName
                            + "} : Failed",
                    e);
        }
    }

    @Override
    public void deleteAll() {
        try {
            final SQLiteDatabase db = getWritableDatabase();
            db.execSQL(AccessHistoryTable.DELETE_TABLE_DATA);
        } catch (Exception e) {
            Slog.e(TAG, "Delete all AppFunction access histories: Failed.", e);
        }
    }
}
