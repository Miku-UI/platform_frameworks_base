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
import android.app.timezonedetector.TelephonyTimeZoneSuggestion;

import com.android.server.timezonedetector.TimeZoneDetectorStrategy.Origin;

interface TimeZoneDetectorTelemetry {
    /**
     * Processes a new Telephony suggestion.
     *
     * <p>It is called from {@link FusedTimeZoneDetectorImpl} when a new Telephony suggestion is
     * received. Before calling this method, it is required to check that the suggestion is valid
     * and has a zone ID.
     *
     * @param suggestion The new Telephony suggestion.
     */
    public void onTelephonyTimeZoneSuggestion(@NonNull TelephonyTimeZoneSuggestion suggestion);

    /**
     * Processes a new Geolocation suggestion.
     *
     * <p>It is called from {@link FusedTimeZoneDetectorImpl} when a new Geolocation suggestion is
     * received. Before calling this method, it is required to check that the suggestion is valid
     * and has a zone ID.
     *
     * @param suggestion The new Geolocation suggestion.
     */
    public void onGeolocationTimeZoneSuggestion(@NonNull GeolocationTimeZoneSuggestion suggestion);

    /**
     * Processes a new Fused time zone change.
     *
     * <p>It is called from {@link FusedTimeZoneDetectorImpl} when the system time zone is changed.
     *
     * @param timeZoneId The new Fused time zone ID.
     */
    public void onFusedTimeZoneChanged(String timeZoneId);

    /**
     * Logs a rejected time zone change.
     *
     * <p>It is called from {@link FusedTimeZoneDetectorImpl} when a time zone change is rejected.
     *
     * @param oldZoneId Time zone ID before the rejected change.
     * @param rejectedZoneId Time zone ID which was rejected.
     * @param newZoneId Time zone ID which was set manually by the user.
     */
    public void logRejectedTimeZoneChange(
            @Origin int origin,
            @NonNull String oldZoneId,
            @NonNull String rejectedZoneId,
            @NonNull String newZoneId);
}
