/*
 * Copyright (C) 2026 The Android Open Source Project
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

/**
 * An interface for logging time zone detector events to statsd.
 *
 * <p>This interface is used to abstract the logging process from the implementation details of the
 * logging system. This allows to properly test {@link TimeZoneDetectorTelemetry} class.
 */
interface TimeZoneDetectorLogger {
    /**
     * Logs a time zone discrepancy event to statsd.
     *
     * @param geoLocationTimeZoneId The time zone ID suggested by the geolocation-based detector.
     * @param telephonyTimeZoneId The time zone ID suggested by the telephony network.
     * @param mcc The mobile country code.
     * @param mnc The mobile network code.
     * @param nitzOffsetSeconds The offset in seconds from UTC to the network time zone.
     * @param nitzDstOffsetSeconds The DST offset in seconds from UTC to the network time zone.
     * @param geolocationCountryCode The country code of the device's geolocation.
     * @param tzdbVersion The version of the time zone database used.
     * @param locationTimeZoneProviderUid The UID of the location time zone provider.
     */
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
            @NonNull String fusedTimeZoneId);

    /**
     * Logs a time zone transition event to statsd.
     *
     * @param previousTimeZoneId The time zone ID before the transition.
     * @param timeZoneId The time zone ID after the transition.
     * @param geoTimeZoneChangedTimestamp The timestamp of the last geolocation suggestion.
     * @param telephonyTimeZoneChangedTimestamp The timestamp of the last telephony suggestion.
     * @param fusedTimeZoneChangedTimestamp The timestamp of the last fused time zone change.
     * @param transitionAborted Whether the transition was aborted.
     * @param mcc The mobile country code.
     * @param mnc The mobile network code.
     * @param nitzOffsetSeconds The offset in seconds from UTC to the network time zone.
     * @param nitzDstOffsetSeconds The DST offset in seconds from UTC to the network time zone.
     * @param geolocationCountryCode The country code of the device's geolocation.
     * @param tzdbVersion The version of the time zone database used.
     * @param locationTimeZoneProviderUid The UID of the location time zone provider.
     */
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
            int locationTimeZoneProviderUid);

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
            int locationTimeZoneProviderUid);
}
