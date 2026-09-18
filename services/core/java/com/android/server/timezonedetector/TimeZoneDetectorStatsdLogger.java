/**
 * Copyright (C) 2026 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.timezonedetector;

import android.annotation.NonNull;

import com.android.internal.util.FrameworkStatsLog;

final class TimeZoneDetectorStatsdLogger implements TimeZoneDetectorLogger {
    @Override
    public void logTimeZoneDiscrepancy(
            @NonNull String geoLocationTimeZoneId,
            @NonNull String telephonyTimeZoneId,
            @NonNull String mcc,
            @NonNull String mnc,
            int nitzOffsetSeconds,
            int nitzDstOffsetSeconds,
            @NonNull String geolocationCountryCode,
            @NonNull String tzdbVersion,
            int locationTimeZoneProviderUid,
            @NonNull String fusedTimeZoneId) {
        FrameworkStatsLog.write(
                FrameworkStatsLog.TIME_ZONE_DETECTION_DISCREPANCY_REPORTED,
                geoLocationTimeZoneId,
                telephonyTimeZoneId,
                mcc,
                mnc,
                nitzOffsetSeconds,
                nitzDstOffsetSeconds,
                geolocationCountryCode,
                tzdbVersion,
                locationTimeZoneProviderUid,
                fusedTimeZoneId);
    }

    @Override
    public void logTimeZoneTransition(
            @NonNull String previousTimeZoneId,
            @NonNull String timeZoneId,
            long geoTimeZoneChangedTimestamp,
            long telephonyTimeZoneChangedTimestamp,
            long fusedTimeZoneChangedTimestamp,
            boolean transitionAborted,
            @NonNull String mcc,
            @NonNull String mnc,
            int nitzOffsetSeconds,
            int nitzDstOffsetSeconds,
            @NonNull String geolocationCountryCode,
            @NonNull String tzdbVersion,
            int locationTimeZoneProviderUid) {
        FrameworkStatsLog.write(
                /* atomId= */ FrameworkStatsLog.TIME_ZONE_TRANSITION_STATE_CHANGED,
                previousTimeZoneId,
                timeZoneId,
                geoTimeZoneChangedTimestamp,
                telephonyTimeZoneChangedTimestamp,
                fusedTimeZoneChangedTimestamp,
                transitionAborted,
                mcc,
                mnc,
                nitzOffsetSeconds,
                nitzDstOffsetSeconds,
                geolocationCountryCode,
                tzdbVersion,
                locationTimeZoneProviderUid);
    }

    @Override
    public void logAutomaticTimeZoneChangeRejection(
            int source,
            @NonNull String mcc,
            @NonNull String mnc,
            int nitzOffsetSeconds,
            int nitzDstOffsetSeconds,
            @NonNull String previousTimeZoneId,
            @NonNull String rejectedAutomaticTimeZone,
            @NonNull String manualTimeZone,
            @NonNull String geolocationCountryCode,
            @NonNull String tzdbVersion,
            int locationTimeZoneProviderUid) {
        FrameworkStatsLog.write(
                /* atomId= */ FrameworkStatsLog
                        .AUTOMATIC_TIME_ZONE_CHANGE_REVERTED_BY_USER_REPORTED,
                source,
                mcc,
                mnc,
                nitzOffsetSeconds,
                nitzDstOffsetSeconds,
                previousTimeZoneId,
                rejectedAutomaticTimeZone,
                manualTimeZone,
                geolocationCountryCode,
                tzdbVersion,
                locationTimeZoneProviderUid);
    }
}
