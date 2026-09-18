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

package com.android.server.appinteraction;

import android.annotation.CurrentTimeMillisLong;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppInteractionAttribution;
import android.app.AppInteractionContract;
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

/** The database helper for managing App Interaction histories. */
public final class AppInteractionSQLiteHistory extends SQLiteOpenHelper
        implements AppInteractionHistory {

    private static final class AccessHistoryTable {
        static final String DB_TABLE = "appinteraction";

        // TODO(b/452916227): Update to use App Interaction contracts
        static final String CREATE_TABLE_SQL =
                "CREATE TABLE IF NOT EXISTS "
                        + DB_TABLE
                        + " ("
                        + AppInteractionContract._ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + AppInteractionContract.COLUMN_AGENT_PACKAGE_NAME
                        + " TEXT, "
                        + AppInteractionContract.COLUMN_TARGET_PACKAGE_NAME
                        + " TEXT, "
                        + AppInteractionContract.COLUMN_INTERACTION_TYPE
                        + " INTEGER, "
                        + AppInteractionContract.COLUMN_CUSTOM_INTERACTION_TYPE
                        + " TEXT, "
                        + AppInteractionContract.COLUMN_INTERACTION_URI
                        + " TEXT, "
                        + AppInteractionContract.COLUMN_ACCESS_TIME
                        + " INTEGER);";

        static final String DELETE_TABLE_DATA_BEFORE_ACCESS_TIME =
                "DELETE FROM "
                        + DB_TABLE
                        + " WHERE "
                        + AppInteractionContract.COLUMN_ACCESS_TIME
                        + " < ?";

        static final String DELETE_TABLE_DATA_FOR_PACKAGE =
                "DELETE FROM "
                        + DB_TABLE
                        + " WHERE "
                        + AppInteractionContract.COLUMN_AGENT_PACKAGE_NAME
                        + " = ?"
                        + " OR "
                        + AppInteractionContract.COLUMN_TARGET_PACKAGE_NAME
                        + " = ?";

        static final String DELETE_TABLE_DATA = "DELETE FROM " + DB_TABLE;
    }

    static final String DB_NAME = "appinteraction.db";

    private static final int DB_VERSION = 1;

    private static final String TAG = "AppInteractionDatabase";

    AppInteractionSQLiteHistory(@NonNull Context context, @NonNull String dbFilePath) {
        super(context, dbFilePath, /* factory= */ null, DB_VERSION);
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
    public Cursor queryAppInteractionHistories(
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
    public long insertAppInteractionHistory(
            @NonNull String sourcePackage,
            @NonNull String targetPackage,
            @Nullable AppInteractionAttribution appInteractionAttribution,
            @CurrentTimeMillisLong long accessTime) {
        Objects.requireNonNull(sourcePackage);
        Objects.requireNonNull(targetPackage);

        try {
            final SQLiteDatabase db = getWritableDatabase();
            final ContentValues values =
                    prepareAppInteractionContentValue(
                            sourcePackage, targetPackage, appInteractionAttribution, accessTime);

            return db.insert(AccessHistoryTable.DB_TABLE, /* nullColumnHack= */ null, values);
        } catch (Exception e) {
            Slog.e(TAG, "Insert AppFunction access histories: Failed.", e);
            return -1;
        }
    }

    @NonNull
    private ContentValues prepareAppInteractionContentValue(
            @NonNull String sourcePackage,
            @NonNull String targetPackage,
            @Nullable AppInteractionAttribution appInteractionAttribution,
            @CurrentTimeMillisLong long accessTime) {
        Objects.requireNonNull(sourcePackage);
        Objects.requireNonNull(targetPackage);

        final ContentValues values = new ContentValues();

        values.put(AppInteractionContract.COLUMN_AGENT_PACKAGE_NAME, sourcePackage);
        values.put(AppInteractionContract.COLUMN_TARGET_PACKAGE_NAME, targetPackage);
        values.put(AppInteractionContract.COLUMN_ACCESS_TIME, accessTime);

        if (appInteractionAttribution != null) {
            values.put(
                    AppInteractionContract.COLUMN_INTERACTION_TYPE,
                    appInteractionAttribution.getInteractionType());
            final String customInteractionType =
                    appInteractionAttribution.getCustomInteractionType();
            if (customInteractionType != null) {
                values.put(
                        AppInteractionContract.COLUMN_CUSTOM_INTERACTION_TYPE,
                        customInteractionType);
            }
            final Uri interactionUri = appInteractionAttribution.getInteractionUri();
            if (interactionUri != null) {
                values.put(
                        AppInteractionContract.COLUMN_INTERACTION_URI, interactionUri.toString());
            }
        }

        return values;
    }

    @Override
    public void deleteExpiredAppInteractionHistories(long retentionMillis) {
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
    public void deleteAppInteractionHistories(@NonNull String packageName) {
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
