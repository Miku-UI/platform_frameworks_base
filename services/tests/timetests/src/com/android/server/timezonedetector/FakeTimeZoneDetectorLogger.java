/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.server.timezonedetector;

import android.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/** A fake implementation of TimeZoneDetectorLogger for tests. */
final class FakeTimeZoneDetectorLogger implements TimeZoneDetectorLogger {
    List<TransitionLog> transitionLogs = new ArrayList<>();
    List<DiscrepancyLog> discrepancyLogs = new ArrayList<>();
    List<TimeZoneChangeRejectionLog> timeZoneChangeRejectionLogs = new ArrayList<>();

    record TransitionLog(
            String previousTimeZoneId,
            String timeZoneId,
            long geoTimeZoneChangedTimestamp,
            long telephonyTimeZoneChangedTimestamp,
            long fusedTimeZoneChangedTimestamp,
            boolean transitionAborted,
            String mcc,
            String mnc,
            int nitzOffsetSeconds,
            int nitzDstOffsetSeconds,
            String geolocationCountryCode,
            String tzdbVersion,
            int locationTimeZoneProviderUid) {}

    record DiscrepancyLog(
            String locationTimeZoneId,
            String telephonyTimeZoneId,
            String mcc,
            String mnc,
            int nitzOffsetSeconds,
            int nitzDstOffsetSeconds,
            String geolocationCountryCode,
            String tzdbVersion,
            int locationTimeZoneProviderUid,
            String fusedTimeZoneId) {}

    record TimeZoneChangeRejectionLog(
            int source,
            String mcc,
            String mnc,
            int nitzOffsetSeconds,
            int nitzDstOffsetSeconds,
            String previousTimeZoneId,
            String rejectedAutomaticTimeZone,
            String manualTimeZone,
            String geolocationCountryCode,
            String tzdbVersion,
            int locationTimeZoneProviderUid) {}

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
        discrepancyLogs.add(
                new DiscrepancyLog(
                        geoLocationTimeZoneId,
                        telephonyTimeZoneId,
                        mcc,
                        mnc,
                        nitzOffsetSeconds,
                        nitzDstOffsetSeconds,
                        geolocationCountryCode,
                        tzdbVersion,
                        locationTimeZoneProviderUid,
                        fusedTimeZoneId));
    }

    @Override
    public void logTimeZoneTransition(
            String previousTimeZoneId,
            String timeZoneId,
            long geoTimeZoneChangedTimestamp,
            long telephonyTimeZoneChangedTimestamp,
            long fusedTimeZoneChangedTimestamp,
            boolean transitionAborted,
            String mcc,
            String mnc,
            int nitzOffsetSeconds,
            int nitzDstOffsetSeconds,
            String geolocationCountryCode,
            String tzdbVersion,
            int locationTimeZoneProviderUid) {
        transitionLogs.add(
                new TransitionLog(
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
                        locationTimeZoneProviderUid));
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
        timeZoneChangeRejectionLogs.add(
                new TimeZoneChangeRejectionLog(
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
                        locationTimeZoneProviderUid));
    }

    int getDiscrepancyLogCount() {
        return discrepancyLogs.size();
    }

    int getTransitionLogCount() {
        return transitionLogs.size();
    }

    int getTimeZoneChangeRejectionLogCount() {
        return timeZoneChangeRejectionLogs.size();
    }

    DiscrepancyLog getDiscrepancyLog(int index) {
        return discrepancyLogs.get(index);
    }

    TransitionLog getTransitionLog(int index) {
        return transitionLogs.get(index);
    }

    TimeZoneChangeRejectionLog getTimeZoneChangeRejectionLog(int index) {
        return timeZoneChangeRejectionLogs.get(index);
    }
}
