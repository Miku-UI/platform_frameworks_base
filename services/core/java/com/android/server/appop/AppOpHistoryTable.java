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

/**
 * SQLite table for storing aggregated app op access events.
 * Each row summarizes the count of accessed, rejected events, and duration sum that occurred
 * within a specific time window (e.g., 1 or 15 minutes). This aggregation helps in efficiently
 * storing the app op
 * access events.
 *
 * <p>The following columns form a composite key to uniquely identify an aggregated record
 * for a given time window. App op events sharing identical values for these fields within the
 * same time window are grouped together, and their occurrences are counted in
 * {@link Columns#TOTAL_ACCESS_COUNT} and {@link Columns#TOTAL_REJECT_COUNT}.
 * <ul>
 * <li>{@link Columns#UID}: UID.</li>
 * <li>{@link Columns#PACKAGE_NAME}: Package name.</li>
 * <li>{@link Columns#DEVICE_ID}: Device Id.</li>
 * <li>{@link Columns#OP_CODE}: OP code.</li>
 * <li>{@link Columns#ATTRIBUTION_TAG}: Attribution tag.</li>
 * <li>{@link Columns#UID_STATE}: The state of the UID (e.g., foreground, background).</li>
 * <li>{@link Columns#OP_FLAGS}: App op flags.</li>
 * <li>{@link Columns#ATTRIBUTION_FLAGS}: Attribution flags.</li>
 * <li>{@link Columns#CHAIN_ID}: Attribution chain id.</li>
 * <li>{@link Columns#ACCESS_TIME}: Discretizd access time.</li>
 * <li>{@link Columns#DURATION}: Discretized duration.</li>
 * </ul>
 *
 * <p>Aggregated values stored for each unique key combination within a time window are:
 * <ul>
 * <li>{@link Columns#TOTAL_ACCESS_COUNT}: Total access counts.</li>
 * <li>{@link Columns#TOTAL_REJECT_COUNT}: Total reject counts.</li>
 * </ul>
 */
final class AppOpHistoryTable {
    private static final String TABLE_NAME = "app_op_accesses";
    private static final String INDEX_APP_OP = "app_op_access_index";

    static final class Columns {
        /** Auto increment primary key. */
        static final String ID = "id";
        /** UID in the app op event. */
        static final String UID = "uid";
        /** Package name in the app op event. */
        static final String PACKAGE_NAME = "package_name";
        /** The device for which the app op event is generated. */
        static final String DEVICE_ID = "device_id";
        /** Op code in the app op event */
        static final String OP_CODE = "op_code";
        /** Attribution tag provided in the app op event. */
        static final String ATTRIBUTION_TAG = "attribution_tag";
        /** App process state, whether the app is in foreground, background or cached etc. */
        static final String UID_STATE = "uid_state";
        /** App op flags */
        static final String OP_FLAGS = "op_flags";
        /** Attribution flags */
        static final String ATTRIBUTION_FLAGS = "attribution_flags";
        /** Chain id */
        static final String CHAIN_ID = "chain_id";
        /** The actual access time of first event in a time window. */
        static final String ACCESS_TIME = "access_time";
        /**
         * The actual duration of first event in a time window. Subsequent events with same
         * discretized duration are summed up in total duration.
         */
        static final String DURATION = "access_duration";
        /** The sum of actual duration of the app op accesses in a time window. */
        static final String TOTAL_DURATION = "total_duration";
        /** Total access count in a time window. */
        static final String TOTAL_ACCESS_COUNT = "access_count";
        /** Total reject count in a time window. */
        static final String TOTAL_REJECT_COUNT = "reject_count";
    }

    static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS "
            + TABLE_NAME + "("
            + Columns.ID + " INTEGER PRIMARY KEY,"
            + Columns.UID + " INTEGER,"
            + Columns.PACKAGE_NAME + " TEXT,"
            + Columns.DEVICE_ID + " TEXT,"
            + Columns.OP_CODE + " INTEGER,"
            + Columns.ATTRIBUTION_TAG + " TEXT,"
            + Columns.UID_STATE + " INTEGER,"
            + Columns.OP_FLAGS + " INTEGER,"
            + Columns.ATTRIBUTION_FLAGS + " INTEGER,"
            + Columns.CHAIN_ID + " INTEGER,"
            + Columns.ACCESS_TIME + " INTEGER,"
            + Columns.DURATION + " INTEGER,"
            + Columns.TOTAL_DURATION + " INTEGER,"
            + Columns.TOTAL_ACCESS_COUNT + " INTEGER,"
            + Columns.TOTAL_REJECT_COUNT + " INTEGER"
            + ")";

    static final String INSERT_TABLE_SQL = "INSERT INTO " + TABLE_NAME + "("
            + Columns.UID + ", "
            + Columns.PACKAGE_NAME + ", "
            + Columns.DEVICE_ID + ", "
            + Columns.OP_CODE + ", "
            + Columns.ATTRIBUTION_TAG + ", "
            + Columns.UID_STATE + ", "
            + Columns.OP_FLAGS + ", "
            + Columns.ATTRIBUTION_FLAGS + ", "
            + Columns.CHAIN_ID + ", "
            + Columns.ACCESS_TIME + ", "
            + Columns.DURATION + ", "
            + Columns.TOTAL_DURATION + ", "
            + Columns.TOTAL_ACCESS_COUNT + ", "
            + Columns.TOTAL_REJECT_COUNT
            + " ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    // Insert indexes
    static final int UID_INDEX = 1;
    static final int PACKAGE_NAME_INDEX = 2;
    static final int DEVICE_ID_INDEX = 3;
    static final int OP_CODE_INDEX = 4;
    static final int ATTRIBUTION_TAG_INDEX = 5;
    static final int UID_STATE_INDEX = 6;
    static final int OP_FLAGS_INDEX = 7;
    static final int ATTRIBUTION_FLAGS_INDEX = 8;
    static final int CHAIN_ID_INDEX = 9;
    static final int ACCESS_TIME_INDEX = 10;
    static final int DURATION_INDEX = 11;
    static final int TOTAL_DURATION_INDEX = 12;
    static final int ACCESS_COUNT_INDEX = 13;
    static final int REJECT_COUNT_INDEX = 14;


    static final String SELECT_TABLE_DATA = "SELECT "
            + Columns.UID + ","
            + Columns.PACKAGE_NAME + ","
            + Columns.DEVICE_ID + ","
            + Columns.OP_CODE + ","
            + Columns.ATTRIBUTION_TAG + ","
            + Columns.UID_STATE + ","
            + Columns.OP_FLAGS + ","
            + Columns.ATTRIBUTION_FLAGS + ","
            + Columns.CHAIN_ID + ","
            + Columns.ACCESS_TIME + ","
            + Columns.DURATION + ","
            + Columns.TOTAL_DURATION + ","
            + Columns.TOTAL_ACCESS_COUNT + ","
            + Columns.TOTAL_REJECT_COUNT
            + " FROM " + TABLE_NAME;

    static final String DELETE_TABLE_DATA = "DELETE FROM " + TABLE_NAME;

    static final String DELETE_TABLE_DATA_BEFORE_ACCESS_TIME = "DELETE FROM " + TABLE_NAME
            + " WHERE " + Columns.ACCESS_TIME + " < ?";

    // Delete at least 1024 (cache size) records, in other words the batch size for insertion.
    static final String DELETE_TABLE_DATA_LEAST_RECENT_ENTRIES = "DELETE FROM " + TABLE_NAME
            + " WHERE " + Columns.ACCESS_TIME + " <= (SELECT " + Columns.ACCESS_TIME + " FROM "
            + TABLE_NAME + " ORDER BY " + Columns.ACCESS_TIME + " LIMIT 1 OFFSET 1024)";

    static final String DELETE_DATA_FOR_UID_PACKAGE = "DELETE FROM "
            + AppOpHistoryTable.TABLE_NAME
            + " WHERE " + Columns.UID + " = ? AND " + Columns.PACKAGE_NAME + " = ?";

    static final String SELECT_MAX_ATTRIBUTION_CHAIN_ID = "SELECT MAX(" + Columns.CHAIN_ID + ")"
            + " FROM " + TABLE_NAME;

    static final String SELECT_RECORDS_COUNT = "SELECT COUNT(1) FROM " + TABLE_NAME;

    static final String SELECT_DISTINCT_PACKAGE_NAMES = "SELECT DISTINCT "
            + Columns.PACKAGE_NAME + " FROM " + TABLE_NAME;

    // Index on access time
    static final String CREATE_INDEX_SQL = "CREATE INDEX IF NOT EXISTS "
            + INDEX_APP_OP + " ON " + TABLE_NAME
            + " (" + DiscreteOpsTable.Columns.ACCESS_TIME + ")";
}
