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

import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.database.sqlite.SQLiteRawStatement;
import android.util.IntArray;
import android.util.Slog;

import java.util.ArrayList;
import java.util.List;

/**
 * A helper class to construct SQL queries for retrieving app ops history data from the
 * SQLite database.
 */
final class AppOpHistoryQueryHelper {
    private static final String TAG = "AppOpHistoryQueryHelper";

    static IntArray getAppOpCodes(@AppOpsManager.HistoricalOpsRequestFilter int filter,
            @Nullable String[] opNamesFilter) {
        if ((filter & AppOpsManager.FILTER_BY_OP_NAMES) != 0) {
            IntArray opCodes = new IntArray(opNamesFilter.length);
            for (int i = 0; i < opNamesFilter.length; i++) {
                int op;
                try {
                    op = AppOpsManager.strOpToOp(opNamesFilter[i]);
                } catch (IllegalArgumentException ex) {
                    Slog.w(TAG, "Appop name `" + opNamesFilter[i] + "` is not recognized.");
                    continue;
                }
                opCodes.add(op);
            }
            return opCodes;
        }
        return null;
    }

    static void bindValues(SQLiteRawStatement statement, List<SQLCondition> conditions) {
        int size = conditions.size();
        for (int i = 0; i < size; i++) {
            AppOpHistoryQueryHelper.SQLCondition condition = conditions.get(i);
            if (HistoricalRegistry.DEBUG) {
                Slog.i(TAG, condition + ", binding value = " + condition.getFilterValue());
            }
            switch (condition.getColumnFilter()) {
                case PACKAGE_NAME, ATTR_TAG -> statement.bindText(i + 1,
                        condition.getFilterValue().toString());
                case UID, OP_CODE_EQUAL, OP_FLAGS -> statement.bindInt(i + 1,
                        Integer.parseInt(condition.getFilterValue().toString()));
                case BEGIN_TIME, END_TIME -> statement.bindLong(i + 1,
                        Long.parseLong(condition.getFilterValue().toString()));
                case OP_CODE_IN -> Slog.d(TAG, "No binding for In operator");
                default -> Slog.w(TAG, "unknown sql condition " + condition);
            }
        }
    }

    static String buildSqlQuery(String baseSql, List<SQLCondition> conditions,
            String orderByColumn, boolean ascending, int limit) {
        StringBuilder sql = new StringBuilder(baseSql);
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ");
            int size = conditions.size();
            for (int i = 0; i < size; i++) {
                sql.append(conditions.get(i).toString());
                if (i < size - 1) {
                    sql.append(" AND ");
                }
            }
        }

        if (orderByColumn != null) {
            sql.append(" ORDER BY ").append(orderByColumn);
            sql.append(ascending ? " ASC " : " DESC ");
        }
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        if (HistoricalRegistry.DEBUG) {
            Slog.i(TAG, "Sql query " + sql);
        }
        return sql.toString();
    }

    /**
     * Creates where conditions for package, uid, attribution tag and app op codes,
     * app op codes condition does not support argument binding.
     */
    static List<SQLCondition> prepareConditions(long beginTime, long endTime, int requestFilters,
            int uid, @Nullable String packageName, @Nullable String attributionTag,
            IntArray opCodes, int opFlags) {
        final List<SQLCondition> conditions = new ArrayList<>();

        if (beginTime > 0) {
            conditions.add(new SQLCondition(ColumnFilter.BEGIN_TIME, beginTime));
        }
        if (endTime > 0) {
            conditions.add(new SQLCondition(ColumnFilter.END_TIME, endTime));
        }
        if (opFlags != 0) {
            conditions.add(new SQLCondition(ColumnFilter.OP_FLAGS, opFlags));
        }

        if (requestFilters != 0) {
            if ((requestFilters & AppOpsManager.FILTER_BY_PACKAGE_NAME) != 0) {
                conditions.add(new SQLCondition(ColumnFilter.PACKAGE_NAME, packageName));
            }
            if ((requestFilters & AppOpsManager.FILTER_BY_UID) != 0) {
                conditions.add(new SQLCondition(ColumnFilter.UID, uid));

            }
            if ((requestFilters & AppOpsManager.FILTER_BY_ATTRIBUTION_TAG) != 0) {
                conditions.add(new SQLCondition(ColumnFilter.ATTR_TAG, attributionTag));
            }
            // filter op codes
            if (opCodes != null && opCodes.size() == 1) {
                conditions.add(new SQLCondition(ColumnFilter.OP_CODE_EQUAL, opCodes.get(0)));
            } else if (opCodes != null && opCodes.size() > 1) {
                StringBuilder b = new StringBuilder();
                int size = opCodes.size();
                for (int i = 0; i < size; i++) {
                    b.append(opCodes.get(i));
                    if (i < size - 1) {
                        b.append(", ");
                    }
                }
                conditions.add(new SQLCondition(ColumnFilter.OP_CODE_IN, b.toString()));
            }
        }
        return conditions;
    }

    /**
     * This class prepares a where clause condition for discrete ops table column.
     */
    static final class SQLCondition {
        private final ColumnFilter mColumnFilter;
        private final Object mFilterValue;

        SQLCondition(ColumnFilter columnFilter, Object filterValue) {
            mColumnFilter = columnFilter;
            mFilterValue = filterValue;
        }

        @Override
        public String toString() {
            if (mColumnFilter == ColumnFilter.OP_CODE_IN) {
                return mColumnFilter + " ( " + mFilterValue + " )";
            }
            return mColumnFilter.toString();
        }

        public ColumnFilter getColumnFilter() {
            return mColumnFilter;
        }

        public Object getFilterValue() {
            return mFilterValue;
        }
    }

    /**
     * This enum describes the where clause conditions for different columns in discrete ops
     * table.
     */
    enum ColumnFilter {
        PACKAGE_NAME(DiscreteOpsTable.Columns.PACKAGE_NAME + " = ? "),
        UID(DiscreteOpsTable.Columns.UID + " = ? "),
        ATTR_TAG(DiscreteOpsTable.Columns.ATTRIBUTION_TAG + " = ? "),
        END_TIME(DiscreteOpsTable.Columns.ACCESS_TIME + " <= ? "),
        OP_CODE_EQUAL(DiscreteOpsTable.Columns.OP_CODE + " = ? "),
        BEGIN_TIME(DiscreteOpsTable.Columns.ACCESS_TIME + " + "
                + DiscreteOpsTable.EFFECTIVE_ACCESS_DURATION + " >= ? "),
        OP_FLAGS("(" + DiscreteOpsTable.Columns.OP_FLAGS + " & ? ) != 0"),
        OP_CODE_IN(DiscreteOpsTable.Columns.OP_CODE + " IN ");

        final String mCondition;

        ColumnFilter(String condition) {
            mCondition = condition;
        }

        @Override
        public String toString() {
            return mCondition;
        }
    }
}
