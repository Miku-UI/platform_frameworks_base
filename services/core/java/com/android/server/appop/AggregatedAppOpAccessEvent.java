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
 * Represents an aggregated access event in the app-op history. An object of this class
 * represents one row/record in sqlite database table i.e. {@link AppOpHistoryTable}
 * All parameters except {@link #totalAccessCount}, {@link #totalRejectCount},
 * {@link  #totalDurationMillis} works as a key for the aggregation.
 *
 * @param uid                 The UID of the application that performed the operation.
 * @param packageName         The package name of the application.
 * @param opCode              The specific operation code (e.g., camera access, location access).
 * @param deviceId            The identifier of the device associated with the access.
 * @param attributionTag      The attribution tag associated with the access, if any. Can be null.
 * @param opFlags             Additional flags associated with the operation code.
 * @param uidState            The state of the UID at the time of access (e.g., foreground,
 *                            background).
 * @param attributionFlags    Flags related to the attribution.
 * @param attributionChainId  An ID to link related attribution events.
 * @param accessTimeMillis    The actual access time of first event in a time window.
 * @param durationMillis      The actual duration of first event in a time window.
 * @param totalDurationMillis Sum of app op access duration in a time window.
 * @param totalAccessCount    Sum of app op access counts in a time window.
 * @param totalRejectCount    Sum of app op reject counts in a time window.
 */
public record AggregatedAppOpAccessEvent(
        int uid,
        String packageName,
        int opCode,
        String deviceId,
        String attributionTag,
        int opFlags,
        int uidState,
        int attributionFlags,
        long attributionChainId,
        long accessTimeMillis,
        long durationMillis,
        long totalDurationMillis,
        int totalAccessCount,
        int totalRejectCount
) {
}
