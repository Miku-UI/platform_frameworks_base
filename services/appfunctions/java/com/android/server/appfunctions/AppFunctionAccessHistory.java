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
import android.annotation.WorkerThread;
import android.app.appfunctions.ExecuteAppFunctionAidlRequest;
import android.database.Cursor;

/** Manages the AppFunction Access Histories. */
public interface AppFunctionAccessHistory extends AutoCloseable {
    /** Queries the AppFunction access histories. */
    @Nullable
    Cursor queryAppFunctionAccessHistory(
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder);

    /**
     * Inserts an AppFunction access history.
     *
     * @return The row id or -1 if fail to insert.
     */
    @WorkerThread
    long insertAppFunctionAccessHistory(
            @NonNull ExecuteAppFunctionAidlRequest aidlRequest, long accessTime, long duration);

    /**
     * Deletes expired AppFunction access histories.
     *
     * @param retentionMillis The maximum age of records to keep, in milliseconds. Records older
     *     than this will be deleted.
     */
    @WorkerThread
    void deleteExpiredAppFunctionAccessHistories(long retentionMillis);

    /** Deletes AppFunction access histories that are associated with the given packageName. */
    @WorkerThread
    void deleteAppFunctionAccessHistories(@NonNull String packageName);

    /** Deletes all AppFunction access histories. */
    @WorkerThread
    void deleteAll();
}
