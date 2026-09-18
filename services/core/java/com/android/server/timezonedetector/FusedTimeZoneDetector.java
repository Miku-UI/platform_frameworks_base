/*
 * Copyright 2025 The Android Open Source Project
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

import android.annotation.IntDef;
import android.annotation.NonNull;

import com.android.server.timezonedetector.TimeZoneDetectorStrategy.Origin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An interface for a time zone detector that fuses signals from multiple sources, such as telephony
 * and location, to determine the device's time zone.
 *
 * <p>This component acts as the central decision-maker for the device's time zone. It receives time
 * zone suggestions from origins like telephony and location, resolves any conflicts, and sets the
 * suggests time zone for the device.
 *
 * @hide
 */
public interface FusedTimeZoneDetector extends Dumpable {

    /** A listener that can set the device time zone. */
    public interface TimeZoneSetter {
        /**
         * Sets the device time zone.
         *
         * @param timeZoneId the new time zone ID
         * @param cause a description of why the time zone is being set
         */
        void setDeviceTimeZoneIfRequired(
                @NonNull String timeZoneId, @NonNull String cause, @Origin int origin);
    }

    @IntDef({QUALITY_LOW, QUALITY_AVERAGE, QUALITY_HIGH})
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
    @interface Quality {}

    /** Quality score for an invalid or low-confidence time zone signal. */
    @Quality int QUALITY_LOW = 0;

    /** Quality score for a time zone signal of medium confidence. */
    @Quality int QUALITY_AVERAGE = 50;

    /** Quality score for a time zone signal of high confidence. */
    @Quality int QUALITY_HIGH = 100;

    /**
     * Sets the time zone setter.
     *
     * <p>Without a {@link TimeZoneSetter}, this detector is a no-op. This method is required to
     * hook the detector up to the logic in {@code TimeZoneDetectorStrategyImpl} that can perform
     * the device time zone setting.
     *
     * <p>This method exists during a transition from {@code TimeZoneDetectorStrategyImpl} to {@code
     * FusedTimeZoneDetector} to minimize code duplication and focus on the algorithm. After the
     * transition is complete, this will be refactored and this method will be removed.
     *
     * @param timeZoneSetter the time zone setter to use
     */
    void setTimeZoneSetter(@NonNull TimeZoneSetter timeZoneSetter);

    /**
     * Processes a time zone suggestion originating from the telephony stack.
     *
     * @param suggestion the telephony time zone suggestion
     */
    void onTelephonyTimeZoneDetected(@NonNull QualifiedTelephonyTimeZoneSuggestion suggestion);

    /**
     * Processes a time zone suggestion originating from a location-based detection algorithm.
     *
     * @param event the location algorithm event containing the suggestion
     */
    void onLocationTimeZoneDetected(@NonNull LocationAlgorithmEvent event);

    /**
     * Re-evaluates and applies the current time zone. This can be used to re-assert the fused time
     * zone after a state change.
     */
    void replay();
}
