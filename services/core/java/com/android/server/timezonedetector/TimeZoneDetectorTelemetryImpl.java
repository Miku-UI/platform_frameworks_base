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

import static com.android.internal.util.FrameworkStatsLog.AUTOMATIC_TIME_ZONE_CHANGE_REVERTED_BY_USER_REPORTED__SOURCE__TIME_ZONE_SOURCE_FUSED_SIGNALS;
import static com.android.internal.util.FrameworkStatsLog.AUTOMATIC_TIME_ZONE_CHANGE_REVERTED_BY_USER_REPORTED__SOURCE__TIME_ZONE_SOURCE_LOCATION;
import static com.android.internal.util.FrameworkStatsLog.AUTOMATIC_TIME_ZONE_CHANGE_REVERTED_BY_USER_REPORTED__SOURCE__TIME_ZONE_SOURCE_TELEPHONY;
import static com.android.internal.util.FrameworkStatsLog.AUTOMATIC_TIME_ZONE_CHANGE_REVERTED_BY_USER_REPORTED__SOURCE__UNSPECIFIED;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_FUSED;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_LOCATION;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_TELEPHONY;

import android.annotation.NonNull;
import android.app.timezonedetector.TelephonyTimeZoneSuggestion;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.icu.util.TimeZone;
import android.timezone.flags.Flags;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.server.timezonedetector.TimeZoneDetectorStrategy.Origin;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * A class that handles telemetry logging for the TimeZoneDetectorService.
 *
 * <p>It is responsible for logging time zone transitions and discrepancies between the fused time
 * zone and the telephony and geolocation suggestions.
 *
 * <p>It is built with the assumption that it logs both inputs and outputs of the Fusted Time Zone
 * Detector, including all incoming signals and timestamps, which will allow us to assess quality of
 * time zone transitions and discrepancies.
 */
public final class TimeZoneDetectorTelemetryImpl implements TimeZoneDetectorTelemetry {
    private static final String TAG = TimeZoneDetectorService.TAG;
    private static final boolean DEBUG = TimeZoneDetectorService.DBG;

    /*
     * The duration to wait for a disagreement between the telephony and geolocation suggestions to
     * be resolved before logging a discrepancy.
     */
    private static final Duration MAXIMUM_TRANSITION_DISAGREEMENT_DURATION = Duration.ofHours(3);

    /*
     * The value to indicate that the NITZ offset is not available.
     * We cannot log null values to statsd, and any number chosen cannot be a valid NITZ offset.
     * NITZ offset is a multiple of 15 minutes, so -1 is a safe value to use.
     */
    private static int NITZ_OFFSET_NOT_AVAILABLE = -1;

    private final Context mContext;
    private final Environment mEnvironment;
    private final PackageManager mPackageManager;

    /*
     * State for disagreement tracking which holds the last suggestions from subsystems and the
     * timestamps of the suggestion changes, as well as the timestamps of the disagreement start
     * and the fused time zone change.
     * This state is reset when the disagreement is resolved or when the disagreement timer expires.
     * These variables are in default state when there is no ongoing disagreement.
     */
    private TelephonyTimeZoneSuggestion mLastTelephonyTimeZoneSuggestion;
    private GeolocationTimeZoneSuggestion mLastGeolocationTimeZoneSuggestion;
    private String mLastFusedTimeZoneId;
    private boolean mPreviouslyAgree = true;
    private long mTelephonySuggestionTimeMillis = -1;
    private long mLocationAlgorithmEventTimeMillis = -1;
    private long mFusedTimeZoneChangeTimeMillis = -1;
    private long mDisagreementStartTimeMillis = -1;
    private String mTimeZoneIdBeforeDisagreement;

    /**
     * Transition starts with an initial disagreement between suggestions. The time zone of the
     * suggestion which caused the disagreement is assumed to be the destination time zone. It can
     * be changed if the suggestions keep changing during the disagreement, but it can never become
     * equal to the original time zone before the disagreement. If disagreement is resolved by
     * suggestions reverting back to the original time zone, this is still considered to be
     * destination time zone, but the whole transition is marked as aborted (flip-flop).
     */
    private String mDestinationTimeZoneId;

    /**
     * This is used only for logging of automatic time zone change rejection. It prevents
     * accidentally logging telephony suggestions which could be received after automatic time zone
     * change, but before automatic time zone change was rejected.
     */
    private TelephonyTimeZoneSuggestion mTelephonySuggestionAtLastTimeZoneChange;

    private final TimeZoneDetectorLogger mTimeZoneDetectorLogger;

    /** Creates a new instance of {@link TimeZoneDetectorTelemetryImpl}. */
    public TimeZoneDetectorTelemetryImpl(
            Context context,
            Environment environment,
            TimeZoneDetectorLogger timeZoneDetectorLogger,
            PackageManager packageManager) {
        mEnvironment = environment;
        mContext = context;
        mTimeZoneDetectorLogger = timeZoneDetectorLogger;
        mPackageManager = packageManager;
    }

    @Override
    public synchronized void onTelephonyTimeZoneSuggestion(
            @NonNull TelephonyTimeZoneSuggestion suggestion) {
        mLastTelephonyTimeZoneSuggestion = suggestion;
        boolean currentlyAgree = doLastSuggestionsAgree();
        if (currentlyAgree && mPreviouslyAgree) {
            return;
        }
        mTelephonySuggestionTimeMillis = mEnvironment.currentTimeMillis();
        handleSuggestionChange(currentlyAgree, suggestion.getZoneId());
    }

    @Override
    public synchronized void onGeolocationTimeZoneSuggestion(
            @NonNull GeolocationTimeZoneSuggestion suggestion) {
        mLastGeolocationTimeZoneSuggestion = suggestion;
        boolean currentlyAgree = doLastSuggestionsAgree();
        if (currentlyAgree && mPreviouslyAgree) {
            return;
        }
        mLocationAlgorithmEventTimeMillis = mEnvironment.currentTimeMillis();
        handleSuggestionChange(currentlyAgree, suggestion.getZoneIds().getFirst());
    }

    @Override
    public synchronized void onFusedTimeZoneChanged(String timeZoneId) {
        mLastFusedTimeZoneId = timeZoneId;
        mTelephonySuggestionAtLastTimeZoneChange = mLastTelephonyTimeZoneSuggestion;
        boolean currentlyAgree = doLastSuggestionsAgree();
        if (currentlyAgree && !mPreviouslyAgree) {
            // Both suggestions are now agreeing and fused time zone detector has reported a change
            mFusedTimeZoneChangeTimeMillis = mEnvironment.currentTimeMillis();
            handleReagreement();
        }
    }

    @Override
    public synchronized void logRejectedTimeZoneChange(
            @Origin int origin,
            @NonNull String oldZoneId,
            @NonNull String rejectedZoneId,
            @NonNull String newZoneId) {

        TelephonyTimeZoneSuggestion telephonySuggestion = mTelephonySuggestionAtLastTimeZoneChange;

        int source =
                switch (origin) {
                    // Generated code - impossible to fit into 100 columns
                    case ORIGIN_LOCATION ->
                            AUTOMATIC_TIME_ZONE_CHANGE_REVERTED_BY_USER_REPORTED__SOURCE__TIME_ZONE_SOURCE_LOCATION;
                    case ORIGIN_TELEPHONY ->
                            AUTOMATIC_TIME_ZONE_CHANGE_REVERTED_BY_USER_REPORTED__SOURCE__TIME_ZONE_SOURCE_TELEPHONY;
                    case ORIGIN_FUSED ->
                            AUTOMATIC_TIME_ZONE_CHANGE_REVERTED_BY_USER_REPORTED__SOURCE__TIME_ZONE_SOURCE_FUSED_SIGNALS;
                    default ->
                            AUTOMATIC_TIME_ZONE_CHANGE_REVERTED_BY_USER_REPORTED__SOURCE__UNSPECIFIED;
                };
        int locationTimeZoneProviderUid =
                getPrimaryLocationTimeZoneProviderPackageUid(mContext, mPackageManager);
        mTimeZoneDetectorLogger.logAutomaticTimeZoneChangeRejection(
                /* source= */ source,
                /* mcc= */ getMcc(telephonySuggestion),
                /* mnc= */ getMnc(telephonySuggestion),
                /* nitz_offset_seconds= */ getNitzOffsetSeconds(telephonySuggestion),
                /* nitz_dst_offset_seconds= */ getNitzDstOffsetSeconds(telephonySuggestion),
                /* previous_time_zone= */ oldZoneId,
                /* rejected_automatic_time_zone= */ rejectedZoneId,
                /* manual_time_zone= */ newZoneId,
                /* country_code= */ "", // not available yet
                /* tzdb_version= */ TimeZone.getTZDataVersion(),
                /* location_time_zone_provider_uid= */ locationTimeZoneProviderUid);
        Log.i(
                TAG,
                "Time zone change rejected: source="
                        + source
                        + ", previous_time_zone="
                        + oldZoneId
                        + ", rejected_automatic_time_zone="
                        + rejectedZoneId
                        + ", manual_time_zone="
                        + newZoneId
                        + ", location_time_zone_provider='"
                        + locationTimeZoneProviderUid
                        + "'"
                        + ", mcc="
                        + getMcc(telephonySuggestion)
                        + ", mnc="
                        + getMnc(telephonySuggestion)
                        + ", nitz_offset_seconds="
                        + getNitzOffsetSeconds(telephonySuggestion)
                        + ", nitz_dst_offset_seconds="
                        + getNitzDstOffsetSeconds(telephonySuggestion)
                        + ", tzdb_version="
                        + TimeZone.getTZDataVersion());
    }

    @GuardedBy("this")
    private void handleSuggestionChange(boolean currentlyAgree, String newZoneId) {
        if (mPreviouslyAgree && !currentlyAgree) {
            if (DEBUG) {
                Log.d(TAG, "Transition started to time zone " + newZoneId);
            }
            mPreviouslyAgree = false;
            mDisagreementStartTimeMillis = mEnvironment.currentTimeMillis();
            mTimeZoneIdBeforeDisagreement = mLastFusedTimeZoneId;
            mDestinationTimeZoneId = newZoneId;

            mEnvironment.postDelayed(
                    mCheckDisagreementRunnable,
                    MAXIMUM_TRANSITION_DISAGREEMENT_DURATION.toMillis());

        } else if (!currentlyAgree) { // Transition: Disagree -> Disagree
            if (DEBUG) {
                Log.d(TAG, "Transition changed to time zone " + newZoneId);
            }
            // Transition: Disagree -> Disagree, destination time zone changed unless it reverted to
            // the time zone before the disagreement.
            if (newZoneId != mTimeZoneIdBeforeDisagreement) {
                mDestinationTimeZoneId = newZoneId;
            }
            // Reset the disagreement timer
            mEnvironment.postDelayed(
                    mCheckDisagreementRunnable,
                    MAXIMUM_TRANSITION_DISAGREEMENT_DURATION.toMillis());
        } else if (currentlyAgree) { // Transition: Disagree -> Agree
            if (!doTimeZonesAgree(mLastFusedTimeZoneId, newZoneId)) {
                // Fused time zone detector has not reported the change, wait
                return;
            }
            // Fused time zone in agreement with both suggestions, no need to wait to report
            // transition
            handleReagreement();
        }
    }

    private void handleReagreement() {
        if (DEBUG) {
            Log.d(
                    TAG,
                    "Time zone suggestions agree again, transition to time zone "
                            + mLastFusedTimeZoneId
                            + " completed");
        }
        mEnvironment.removePendingRunnable(mCheckDisagreementRunnable);
        Duration disagreementDuration =
                Duration.between(
                        Instant.ofEpochMilli(mDisagreementStartTimeMillis),
                        Instant.ofEpochMilli(mEnvironment.currentTimeMillis()));
        if (disagreementDuration.compareTo(MAXIMUM_TRANSITION_DISAGREEMENT_DURATION) < 0) {
            logTimeZoneTransition(
                    mTimeZoneIdBeforeDisagreement,
                    mLastFusedTimeZoneId,
                    mDestinationTimeZoneId,
                    mLocationAlgorithmEventTimeMillis,
                    mTelephonySuggestionTimeMillis,
                    mFusedTimeZoneChangeTimeMillis,
                    mLastTelephonyTimeZoneSuggestion);
        }
        resetDisagreementState();
    }

    final Runnable mCheckDisagreementRunnable =
            () -> {
                synchronized (TimeZoneDetectorTelemetryImpl.this) {
                    if (mPreviouslyAgree) return;

                    if (!doLastSuggestionsAgree()) {
                        if (DEBUG) {
                            Log.d(TAG, "Time zone suggestions permanently disagree.");
                        }
                        String geoLocationTimeZoneId =
                                mLastGeolocationTimeZoneSuggestion == null
                                        ? null
                                        : mLastGeolocationTimeZoneSuggestion
                                                .getZoneIds()
                                                .getFirst();
                        logTimeZoneDiscrepancy(
                                geoLocationTimeZoneId, mLastTelephonyTimeZoneSuggestion);
                    }
                    resetDisagreementState();
                }
            };

    private void resetDisagreementState() {
        mPreviouslyAgree = true;
        mTimeZoneIdBeforeDisagreement = null;
        mDestinationTimeZoneId = null;
        mFusedTimeZoneChangeTimeMillis = -1;
        mTelephonySuggestionTimeMillis = -1;
        mLocationAlgorithmEventTimeMillis = -1;
        mDisagreementStartTimeMillis = -1;
    }

    private boolean doLastSuggestionsAgree() {
        String zoneIdString1 =
                mLastTelephonyTimeZoneSuggestion == null
                        ? null
                        : mLastTelephonyTimeZoneSuggestion.getZoneId();
        String zoneIdString2 =
                mLastGeolocationTimeZoneSuggestion == null
                        ? null
                        : mLastGeolocationTimeZoneSuggestion.getZoneIds().getFirst();
        return doTimeZonesAgree(zoneIdString1, zoneIdString2);
    }

    private static boolean doTimeZonesAgree(String zoneIdString1, String zoneIdString2) {
        if (zoneIdString1 == null || zoneIdString2 == null) {
            // If we received only one or no suggestions, there is no disagreement.
            return true;
        }
        if (zoneIdString1.equals(zoneIdString2)) {
            return true;
        }

        try {
            ZoneId zoneId1 = ZoneId.of(zoneIdString1);
            ZoneId zoneId2 = ZoneId.of(zoneIdString2);

            return Objects.equals(zoneId1, zoneId2);

        } catch (DateTimeException e) {
            Log.e(
                    TAG,
                    "areTimeZonesEffectivelySame: Invalid time zone ID provided - "
                            + e.getMessage());
            return false;
        }
    }

    void logTimeZoneTransition(
            String timeZoneIdBeforeDisagreement,
            String timeZoneIdAfterDisagreement,
            String destinationTimeZoneId,
            long lastGeolocationSuggestionTimeMillis,
            long lastTelephonySuggestionTimeMillis,
            long lastFusedTimeZoneChangeTimeMillis,
            TelephonyTimeZoneSuggestion telephonySuggestion) {
        if (!Flags.enableTimeZoneTransitionTelemetryLogging()) {
            return;
        }
        String mcc = getMcc(telephonySuggestion);
        String mnc = getMnc(telephonySuggestion);
        boolean flipFlop =
                isFlipFlop(
                        timeZoneIdBeforeDisagreement,
                        destinationTimeZoneId,
                        timeZoneIdAfterDisagreement);

        mTimeZoneDetectorLogger.logTimeZoneTransition(
                /* previousTimeZoneId= */ timeZoneIdBeforeDisagreement == null
                        ? ""
                        : timeZoneIdBeforeDisagreement,
                /* timeZoneId= */ destinationTimeZoneId == null ? "" : destinationTimeZoneId,
                /* geoTimeZoneChangedTimestamp= */ lastGeolocationSuggestionTimeMillis,
                /* telephonyTimeZoneChangedTimestamp= */ lastTelephonySuggestionTimeMillis,
                /* fusedTimeZoneChangedTimestamp= */ lastFusedTimeZoneChangeTimeMillis,
                /* transitionAborted= */ flipFlop,
                /* mcc= */ mcc == null ? "" : mcc,
                /* mnc= */ mnc == null ? "" : mnc,
                /* nitzOffsetSeconds= */ getNitzOffsetSeconds(telephonySuggestion),
                /* nitzDstOffsetSeconds= */ getNitzDstOffsetSeconds(telephonySuggestion),
                /* geolocationCountryCode= */ "", // Geolocation country code not yet available
                /* tzdbVersion= */ TimeZone.getTZDataVersion() == null
                        ? ""
                        : TimeZone.getTZDataVersion(),
                /* locationTimeZoneProviderUid= */ getPrimaryLocationTimeZoneProviderPackageUid(
                        mContext, mPackageManager));

        Log.i(
                TAG,
                "ime zone transition state changed:"
                        + "\n  previous_time_zone_id: "
                        + timeZoneIdBeforeDisagreement
                        + "\n  time_zone_id: "
                        + (flipFlop ? destinationTimeZoneId : timeZoneIdAfterDisagreement)
                        + "\n  geo_time_zone_changed_timestamp: "
                        + lastGeolocationSuggestionTimeMillis
                        + "\n  telephony_time_zone_changed_timestamp: "
                        + lastTelephonySuggestionTimeMillis
                        + "\n  fused_time_zone_changed_timestamp: "
                        + lastFusedTimeZoneChangeTimeMillis
                        + "\n  transition_aborted: "
                        + flipFlop
                        + "\n  mcc: "
                        + mcc
                        + "\n  mnc: "
                        + mnc
                        + "\n  nitz_offset_seconds: "
                        + getNitzOffsetSeconds(telephonySuggestion)
                        + "\n  nitz_dst_offset_seconds: "
                        + getNitzDstOffsetSeconds(telephonySuggestion)
                        + "\n  tzdb_version: "
                        + TimeZone.getTZDataVersion()
                        + "\n  location_time_zone_provider:"
                        + getPrimaryLocationTimeZoneProviderPackageUid(mContext, mPackageManager));
    }

    private static int getPrimaryLocationTimeZoneProviderPackageUid(
            Context context, PackageManager packageManager) {
        String packageName =
                context.getResources()
                        .getString(R.string.config_primaryLocationTimeZoneProviderPackageName);
        try {
            return packageManager.getApplicationInfo(packageName, 0).uid;
        } catch (Resources.NotFoundException | PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    /**
     * Returns true if the time zone transition is a flip-flop, i.e. the attempted destination time
     * zone was different from the previous time zone, but then provider reverted to the previous
     * suggestion.
     */
    static boolean isFlipFlop(
            String prevTimeZone, String destinationTimeZone, String currentTimeZone) {
        return prevTimeZone != null
                && currentTimeZone != null
                && destinationTimeZone != null
                && !doTimeZonesAgree(prevTimeZone, destinationTimeZone)
                && doTimeZonesAgree(prevTimeZone, currentTimeZone);
    }

    void logTimeZoneDiscrepancy(
            @NonNull String locationTimeZoneId,
            @NonNull TelephonyTimeZoneSuggestion telephonySuggestion) {
        if (!Flags.enablePermanentTimeZoneCorrectnessTelemetryLogging()) {
            return;
        }
        String mcc = getMcc(telephonySuggestion);
        String mnc = getMnc(telephonySuggestion);
        String telephonyZoneId = telephonySuggestion == null ? "" : telephonySuggestion.getZoneId();
        mTimeZoneDetectorLogger.logTimeZoneDiscrepancy(
                /* geoLocationTimeZoneId= */ locationTimeZoneId == null ? "" : locationTimeZoneId,
                /* telephonyTimeZoneId= */ telephonyZoneId == null ? "" : telephonyZoneId,
                /* mcc= */ mcc == null ? "" : mcc,
                /* mnc= */ mnc == null ? "" : mnc,
                /* nitzOffsetSeconds= */ getNitzOffsetSeconds(telephonySuggestion),
                /* nitzDstOffsetSeconds= */ getNitzDstOffsetSeconds(telephonySuggestion),
                /* geolocationCountryCode= */ "", // Geolocation country code not yet available
                /* tzdbVersion= */ TimeZone.getTZDataVersion() == null
                        ? ""
                        : TimeZone.getTZDataVersion(),
                /* locationTimeZoneProviderUid= */ getPrimaryLocationTimeZoneProviderPackageUid(
                        mContext, mPackageManager),
                /* fusedTimeZoneId= */ mLastFusedTimeZoneId == null ? "" : mLastFusedTimeZoneId);
        Log.i(
                TAG,
                "Time zone discrepancy detected:"
                        + "\n  geolocation_time_zone_id: "
                        + locationTimeZoneId
                        + "\n  telephony_time_zone_id: "
                        + telephonySuggestion.getZoneId()
                        + "\n  mcc: "
                        + mcc
                        + "\n  mnc: "
                        + mnc
                        + "\n  nitz_offset_seconds: "
                        + getNitzOffsetSeconds(telephonySuggestion)
                        + "\n  nitz_dst_offset_seconds: "
                        + getNitzDstOffsetSeconds(telephonySuggestion)
                        + "\n  tzdb_version: "
                        + TimeZone.getTZDataVersion()
                        + "\n  location_time_zone_provider:"
                        + getPrimaryLocationTimeZoneProviderPackageUid(mContext, mPackageManager)
                        + "\n  fused_time_zone_id: "
                        + mLastFusedTimeZoneId);
    }

    private static int getNitzDstOffsetSeconds(TelephonyTimeZoneSuggestion telephonySuggestion) {
        if (telephonySuggestion == null
                || telephonySuggestion.getTelephonySignal() == null
                || telephonySuggestion.getTelephonySignal().getNitzSignal() == null
                || telephonySuggestion.getTelephonySignal().getNitzSignal().getDstOffset()
                        == null) {
            return 0;
        }
        return telephonySuggestion.getTelephonySignal().getNitzSignal().getDstOffset() / 1000;
    }

    private static int getNitzOffsetSeconds(TelephonyTimeZoneSuggestion telephonySuggestion) {
        if (telephonySuggestion == null
                || telephonySuggestion.getTelephonySignal() == null
                || telephonySuggestion.getTelephonySignal().getNitzSignal() == null) {
            return NITZ_OFFSET_NOT_AVAILABLE;
        }
        return telephonySuggestion.getTelephonySignal().getNitzSignal().getZoneOffset() / 1000;
    }

    @NonNull
    private static String getMcc(TelephonyTimeZoneSuggestion suggestion) {
        if (suggestion == null
                || suggestion.getTelephonySignal() == null
                || suggestion.getTelephonySignal().getMcc() == null) {
            return "";
        }
        return suggestion.getTelephonySignal().getMcc();
    }

    @NonNull
    private static String getMnc(TelephonyTimeZoneSuggestion suggestion) {
        if (suggestion == null
                || suggestion.getTelephonySignal() == null
                || suggestion.getTelephonySignal().getMnc() == null) {
            return "";
        }
        return suggestion.getTelephonySignal().getMnc();
    }
}
