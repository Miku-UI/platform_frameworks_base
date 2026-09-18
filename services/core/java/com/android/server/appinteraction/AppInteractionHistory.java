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
import android.annotation.WorkerThread;
import android.app.AppInteractionAttribution;
import android.database.Cursor;

/** Manages the App Interaction Histories. */
public interface AppInteractionHistory extends AutoCloseable {
    /** Queries the App Interaction histories. */
    @Nullable
    Cursor queryAppInteractionHistories(
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder);

    /**
     * Inserts an App Interaction history.
     *
     * @return The row id or -1 if fail to insert.
     */
    @WorkerThread
    long insertAppInteractionHistory(
            @NonNull String sourcePackage,
            @NonNull String targetPackage,
            @Nullable AppInteractionAttribution appInteractionAttribution,
            @CurrentTimeMillisLong long accessTime);

    /**
     * Deletes expired App Interaction histories.
     *
     * @param retentionMillis The maximum age of records to keep, in milliseconds. Records older
     *     than this will be deleted.
     */
    @WorkerThread
    void deleteExpiredAppInteractionHistories(long retentionMillis);

    /** Deletes App Interaction histories that are associated with the given packageName. */
    @WorkerThread
    void deleteAppInteractionHistories(@NonNull String packageName);

    /** Deletes all App Interaction histories. */
    @WorkerThread
    void deleteAll();
}
