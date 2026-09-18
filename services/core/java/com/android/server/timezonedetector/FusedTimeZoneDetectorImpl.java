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

import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_FUSED;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_LOCATION;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_TELEPHONY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.timezonedetector.TelephonySignal;
import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.timezone.flags.Flags;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.timezonedetector.ftzd.AirplaneModeEvent;
import com.android.server.timezonedetector.ftzd.FusedSignals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

/**
 * A concrete implementation of {@link FusedTimeZoneDetector} that serves as the primary engine for
 * determining the device's time zone by fusing signals from multiple sources, primarily telephony
 * and location.
 *
 * <p>This class is responsible for the following core logic:
 *
 * <ul>
 *   <li><b>State Management:</b> It maintains the current {@link FusedSignals}, which represents
 *       the best-known time zone for the device, along with the origins (e.g., {@code
 *       ORIGIN_TELEPHONY}, {@code ORIGIN_LOCATION}) that support it.
 *   <li><b>Signal Processing:</b> It receives time zone suggestions from telephony via {@link
 *       #onTelephonyTimeZoneDetected(QualifiedTelephonyTimeZoneSuggestion)} and from the
 *       location-based detector via {@link #onLocationTimeZoneDetected(LocationAlgorithmEvent)}.
 *   <li><b>Disagreement Resolution:</b> A key feature of this class is its ability to handle
 *       conflicts between sources. When a high-confidence location suggestion repeatedly disagrees
 *       with the current time zone (which is often derived from telephony), it can override the
 *       existing zone.
 *   <li><b>Telephony Trust Management:</b> If a location-based override occurs, this class marks
 *       the conflicting telephony network (identified by its PLMN ID) as untrusted. Subsequent
 *       suggestions from that network are ignored unless they align with the confirmed time zone.
 *       This trust state is persisted across reboots.
 *   <li><b>System State Awareness:</b> It monitors system states like airplane mode to pause or
 *       reset its detection logic appropriately.
 *   <li><b>History and Debugging:</b> It keeps a history of time zone changes and provides detailed
 *       state information via {@link #dump(IndentingPrintWriter, String[])} for debugging purposes.
 * </ul>
 *
 * @see FusedTimeZoneDetector
 * @see FusedSignals
 * @see TimeZoneDetectorStrategy
 * @see QualifiedTelephonyTimeZoneSuggestion
 * @see LocationAlgorithmEvent
 * @hide
 */
public final class FusedTimeZoneDetectorImpl implements FusedTimeZoneDetector {

    private static final int MAX_HISTORY_SIZE_DEFAULT = 20;
    private static final int MAX_HISTORY_SIZE_LOW = 10;
    private static final String SOURCE_TELEPHONY = "telephony";
    private static final String SOURCE_LOCATION = "location";

    /**
     * The number of consecutive, consistent location suggestions that must disagree with the
     * current time zone before the location-based time zone is used to override it.
     */
    @VisibleForTesting static final int LOCATION_OVERRIDE_THRESHOLD = 2;

    /**
     * The duration to wait in airplane mode before resetting location disagreement state. We picked
     * 15 minutes because the shortest commercial flight with a time zone change is around 35
     * minutes, so this provides a reasonable buffer.
     */
    private static final Duration DEFAULT_AIRPLANE_MODE_RESET_DELAY = Duration.ofMinutes(15);

    private final Context mContext;
    private final DeviceActivityMonitor mDeviceActivityMonitor;
    private final ServiceConfigAccessor mServiceConfigAccessor;
    private final Handler mHandler;
    private final Duration mAirplaneModeResetDelay;
    private final TimeZoneDetectorTelemetry mTelemetry;

    @GuardedBy("this")
    @Nullable
    private TimeZoneSetter mTimeZoneSetter;

    @GuardedBy("this")
    @VisibleForTesting
    final Map<String, Set<String>> mUntrustedTelephonyTz = new ArrayMap<>();

    /**
     * The key for the {@link android.provider.Settings.Global} setting that stores whether the
     * device is in "location-only" time zone detection mode.
     *
     * <p>In this mode, the device will prioritize time zone suggestions from the location provider
     * and ignore conflicting suggestions from untrusted telephony networks. This is typically
     * enabled after persistent disagreements between the two sources.
     *
     * <p>The value is stored as an integer (0 for false, 1 for true).
     */
    private static final String KEY_LOCATION_ONLY = "is_location_only_tz_detection";

    @GuardedBy("this")
    private boolean mIsLocationOnlyTzDetection;

    // Number of telephony updates that are valid and used to update the current time zone.
    private int mValidTelephonyUpdates;
    // Total number of telephony updates received.
    private int mTotalTelephonyUpdates;
    // Number of location updates that are valid and used to update the current time zone.
    private int mValidLocationUpdates;
    // Total number of location updates received.
    private int mTotalLocationUpdates;
    // Total number of times telephony and location updates disagreed.
    private int mTotalDisagreements;

    @GuardedBy("this")
    private FusedSignals mCurrentFusedSignals;

    @GuardedBy("this")
    private QualifiedTelephonyTimeZoneSuggestion mLastTelephonySuggestion;

    @GuardedBy("this")
    private final ReferenceWithHistory<FusedSignals> mTimeZoneHistory =
            new ReferenceWithHistory<>(MAX_HISTORY_SIZE_DEFAULT);

    @GuardedBy("this")
    private final ReferenceWithHistory<AirplaneModeEvent> mAirplaneModeEventHistory =
            new ReferenceWithHistory<>(MAX_HISTORY_SIZE_LOW);

    @GuardedBy("this")
    private AirplaneModeEvent mLastAirplaneModeEvent;

    @GuardedBy("this")
    private boolean mIsAirplaneModeOn;

    /**
     * The location suggestions that disagreed with the current time zone. When the number of
     * location suggestions that disagree with the current time zone reaches the threshold, the
     * location-based time zone is used to override the current time zone.
     */
    @GuardedBy("this")
    private TimeZoneDisagreementCandidate mLocationDisagreementCandidate;

    /**
     * The telephony suggestion that disagreed with the current time zone while the device was in
     * location-only mode. This is used to record when a potential telephony-based disagreement
     * started and to resolve it when the device exits location-only mode.
     */
    @GuardedBy("this")
    private TimeZoneDisagreementCandidate mTelephonyDisagreementCandidate;

    private final Runnable mAirplaneModeTimeoutRunnable =
            () -> {
                synchronized (FusedTimeZoneDetectorImpl.this) {
                    clearLocationDisagreementCandidates();
                    updateCurrentFusedSignals(
                            FusedSignals.copyWithoutOrigins(mCurrentFusedSignals));
                }
            };

    /** Creates a new instance of {@link FusedTimeZoneDetectorImpl}. */
    @RequiresPermission("android.permission.INTERACT_ACROSS_USERS_FULL")
    public static FusedTimeZoneDetectorImpl create(
            @NonNull Context context,
            @NonNull ServiceConfigAccessor serviceConfigAccessor,
            @NonNull TimeZoneDetectorTelemetry telemetry,
            @NonNull Handler handler) {
        FusedTimeZoneDetectorImpl fusedTimeZoneDetector =
                new FusedTimeZoneDetectorImpl(
                        context,
                        serviceConfigAccessor,
                        DeviceActivityMonitorImpl.create(context, handler),
                        telemetry,
                        handler);
        fusedTimeZoneDetector.init();

        return fusedTimeZoneDetector;
    }

    @VisibleForTesting
    public FusedTimeZoneDetectorImpl(
            @NonNull Context context,
            @NonNull ServiceConfigAccessor serviceConfigAccessor,
            @NonNull DeviceActivityMonitor deviceActivityMonitor,
            @NonNull TimeZoneDetectorTelemetry telemetry,
            @NonNull Handler handler) {
        this(
                context,
                serviceConfigAccessor,
                deviceActivityMonitor,
                telemetry,
                handler,
                DEFAULT_AIRPLANE_MODE_RESET_DELAY);
    }

    @VisibleForTesting
    FusedTimeZoneDetectorImpl(
            @NonNull Context context,
            @NonNull ServiceConfigAccessor serviceConfigAccessor,
            @NonNull DeviceActivityMonitor deviceActivityMonitor,
            @NonNull TimeZoneDetectorTelemetry telemetry,
            @NonNull Handler handler,
            @NonNull Duration airplaneModeResetDelay) {
        mContext = Objects.requireNonNull(context);
        mServiceConfigAccessor = Objects.requireNonNull(serviceConfigAccessor);
        mDeviceActivityMonitor = Objects.requireNonNull(deviceActivityMonitor);
        mHandler = handler;
        mAirplaneModeResetDelay = airplaneModeResetDelay;
        mTelemetry = telemetry;

        synchronized (this) {
            mIsLocationOnlyTzDetection =
                    Settings.Global.getInt(mContext.getContentResolver(), KEY_LOCATION_ONLY, 0)
                            != 0;
        }
    }

    @VisibleForTesting
    void init() {
        setupAirplaneModeListener();

        synchronized (this) {
            updateCurrentFusedSignals(new FusedSignals(TimeZone.getDefault().getID(), null));
        }
    }

    public synchronized void setTimeZoneSetter(@NonNull TimeZoneSetter timeZoneSetter) {
        mTimeZoneSetter = timeZoneSetter;
    }

    @GuardedBy("this")
    private void setLocationOnlyTzDetection(boolean enabled) {
        mIsLocationOnlyTzDetection = enabled;
        Settings.Global.putInt(mContext.getContentResolver(), KEY_LOCATION_ONLY, enabled ? 1 : 0);
    }

    @Override
    public void onTelephonyTimeZoneDetected(
            @NonNull QualifiedTelephonyTimeZoneSuggestion suggestion) {
        mTotalTelephonyUpdates++;

        if (mIsAirplaneModeOn || suggestion.suggestion() == null) {
            return;
        }

        synchronized (this) {
            if (isSuggestionFromUntrustedTelephony(suggestion)) {
                return;
            }
        }

        if (suggestion.suggestion().getZoneId() == null) {
            handleNullTelephonySuggestion();
            return;
        }

        if (suggestion.score() < TimeZoneDetectorStrategyImpl.TELEPHONY_SCORE_USAGE_THRESHOLD) {
            return;
        }

        if (Flags.enableTimeZoneTransitionTelemetryLogging()
                || Flags.enablePermanentTimeZoneCorrectnessTelemetryLogging()) {
            mTelemetry.onTelephonyTimeZoneSuggestion(suggestion.suggestion());
        }

        mValidTelephonyUpdates++;

        synchronized (this) {
            String newZoneId = suggestion.suggestion().getZoneId();
            if (mCurrentFusedSignals == null) {
                handleTelephonyOverride(newZoneId);
            } else if (newZoneId.equals(mCurrentFusedSignals.getTimeZoneId())) {
                handleAgreeingTelephonySuggestion(newZoneId);
            } else if (mCurrentFusedSignals.hasNoOrigins(/* qualityThreshold= */ QUALITY_AVERAGE)
                    || mCurrentFusedSignals.hasSingleOrigin(
                            ORIGIN_TELEPHONY, /* qualityThreshold= */ QUALITY_AVERAGE)) {
                handleTelephonyOverride(newZoneId);
            } else {
                handleDisagreeingTelephonySuggestion(suggestion);
            }
            mLastTelephonySuggestion = suggestion;
        }
    }

    @Override
    public void onLocationTimeZoneDetected(@NonNull LocationAlgorithmEvent event) {
        mTotalLocationUpdates++;

        if (mIsAirplaneModeOn || event.getSuggestion() == null) {
            return;
        }

        if (handleInvalidOrDisabledLocationEvent(event)) {
            return;
        }

        mValidLocationUpdates++;
        List<String> newZoneIds = event.getSuggestion().getZoneIds();

        if (Flags.enableTimeZoneTransitionTelemetryLogging()
                || Flags.enablePermanentTimeZoneCorrectnessTelemetryLogging()) {
            mTelemetry.onGeolocationTimeZoneSuggestion(event.getSuggestion());
        }

        synchronized (this) {
            if (mCurrentFusedSignals == null) {
                handleLocationOverride(newZoneIds);
            } else if (newZoneIds.contains(mCurrentFusedSignals.getTimeZoneId())) {
                handleAgreeingLocationSuggestion(newZoneIds);
            } else if (mCurrentFusedSignals.hasNoOrigins(/* qualityThreshold= */ QUALITY_AVERAGE)
                    || mCurrentFusedSignals.hasSingleOrigin(
                            ORIGIN_LOCATION, /* qualityThreshold= */ QUALITY_AVERAGE)) {
                handleLocationOverride(newZoneIds);
            } else {
                handleDisagreeingLocationSuggestion(event);
            }
        }
    }

    @VisibleForTesting
    synchronized FusedSignals getCurrentFusedSignals() {
        return mCurrentFusedSignals;
    }

    /** Checks if a telephony suggestion is from a network currently marked as untrusted. */
    @GuardedBy("this")
    private boolean isSuggestionFromUntrustedTelephony(
            @NonNull QualifiedTelephonyTimeZoneSuggestion suggestion) {
        TelephonySignal telephonySignal = suggestion.suggestion().getTelephonySignal();
        String newZoneId = suggestion.suggestion().getZoneId();
        if (telephonySignal != null
                && mUntrustedTelephonyTz
                        .getOrDefault(telephonySignal.getMcc(), Set.of())
                        .contains(newZoneId)
                && !Objects.equals(newZoneId, mCurrentFusedSignals.getTimeZoneId())) {
            // Telephony detection is not trusted and it disagrees with the current zone, so we
            // ignore the event.
            return true;
        }
        return false;
    }

    /** Handles a null telephony suggestion by lowering the quality of the telephony origin. */
    @GuardedBy("this")
    private void handleNullTelephonySuggestion() {
        synchronized (this) {
            if (mCurrentFusedSignals.hasOrigin(
                    ORIGIN_TELEPHONY, /* qualityThreshold= */ QUALITY_LOW)) {
                mCurrentFusedSignals.setQualityForOrigin(ORIGIN_TELEPHONY, QUALITY_LOW);
            }
        }
    }

    /** Handles a telephony suggestion that agrees with the current time zone. */
    @GuardedBy("this")
    private void handleAgreeingTelephonySuggestion(@NonNull String newZoneId) {
        mCurrentFusedSignals.update(ORIGIN_TELEPHONY, List.of(newZoneId));
        mCurrentFusedSignals.setQualityForOrigin(ORIGIN_TELEPHONY, QUALITY_HIGH);
        setLocationOnlyTzDetection(false);
    }

    /** Handles a telephony suggestion that overrides a weak or non-existent time zone. */
    @GuardedBy("this")
    private void handleTelephonyOverride(@NonNull String newZoneId) {
        FusedSignals timeZone = new FusedSignals(newZoneId, ORIGIN_TELEPHONY);

        if (mLocationDisagreementCandidate != null
                && mLocationDisagreementCandidate.getZoneIds().contains(newZoneId)) {
            // The new telephony suggestion agrees with a pending location candidate. This resolves
            // the disagreement. We can adopt the new zone and clear the candidates.
            timeZone.addOrigin(
                    mLocationDisagreementCandidate.getOrigin(),
                    mLocationDisagreementCandidate.getZoneIds(),
                    mLocationDisagreementCandidate.getTimestamp());
            clearLocationDisagreementCandidates();
        }

        updateCurrentFusedSignals(timeZone);
        setDeviceTimeZoneIfRequired(timeZone, SOURCE_TELEPHONY);
        setLocationOnlyTzDetection(false);
    }

    /**
     * Handles a telephony suggestion that disagrees with a strong existing time zone (e.g., from
     * location). This can lead to untrusting the telephony network or reconciling a conflict.
     */
    @GuardedBy("this")
    private void handleDisagreeingTelephonySuggestion(
            @NonNull QualifiedTelephonyTimeZoneSuggestion suggestion) {
        mTotalDisagreements++;
        String newZoneId = suggestion.suggestion().getZoneId();

        if (mIsLocationOnlyTzDetection
                && !mCurrentFusedSignals.hasNoOrigins(/* qualityThreshold= */ QUALITY_LOW)
                && mLastTelephonySuggestion != null
                && Objects.equals(
                        mLastTelephonySuggestion.suggestion().getZoneId(),
                        suggestion.suggestion().getZoneId())) {
            // We are in location-only mode and telephony is persistently suggesting a
            // different zone. Mark this telephony signal as untrusted.
            String mcc = suggestion.suggestion().getTelephonySignal().getMcc();
            String zoneId = suggestion.suggestion().getZoneId();

            mUntrustedTelephonyTz.computeIfAbsent(mcc, k -> new ArraySet<>()).add(zoneId);
        } else if (mLocationDisagreementCandidate != null
                && mLocationDisagreementCandidate.getZoneIds().contains(newZoneId)) {
            // The new telephony suggestion agrees with a pending location candidate. This resolves
            // the disagreement. We can adopt the new zone and clear the candidates.
            FusedSignals timeZone =
                    new FusedSignals(newZoneId, ORIGIN_TELEPHONY)
                            .addOrigin(
                                    mLocationDisagreementCandidate.getOrigin(),
                                    mLocationDisagreementCandidate.getZoneIds(),
                                    mLocationDisagreementCandidate.getTimestamp());

            clearLocationDisagreementCandidates();
            updateCurrentFusedSignals(timeZone);
            setDeviceTimeZoneIfRequired(timeZone, SOURCE_TELEPHONY);
        } else if (!mIsLocationOnlyTzDetection) {
            // Not in location-only mode, so telephony suggestion takes precedence.
            handleTelephonyOverride(newZoneId);
        } else {
            // In location-only mode, telephony suggestions are untrusted. We cache the candidate to
            // validate it against the next incoming location suggestion.
            if (mTelephonyDisagreementCandidate != null
                    && mTelephonyDisagreementCandidate.getZoneIds().contains(newZoneId)) {
                mTelephonyDisagreementCandidate.markOccurrence();
            } else {
                mTelephonyDisagreementCandidate =
                        new TimeZoneDisagreementCandidate(
                                ORIGIN_TELEPHONY,
                                List.of(newZoneId),
                                SystemClock.elapsedRealtime());
            }
        }
    }

    /**
     * Checks if a location event is invalid or if the feature is disabled, and handles state
     * accordingly. Returns {@code true} if the event should be ignored.
     */
    private boolean handleInvalidOrDisabledLocationEvent(@NonNull LocationAlgorithmEvent event) {
        if (!isLocationTimeZoneDetectionEnabled()
                || event.getSuggestion().getZoneIds() == null
                || event.getSuggestion().getZoneIds().isEmpty()) {
            synchronized (this) {
                if (mCurrentFusedSignals.hasOrigin(
                        ORIGIN_LOCATION, /* qualityThreshold= */ QUALITY_LOW)) {
                    mCurrentFusedSignals.setQualityForOrigin(ORIGIN_LOCATION, QUALITY_LOW);
                }
                // Clear candidates if location detection is disabled or suggestion is invalid
                clearLocationDisagreementCandidates();
            }
            return true;
        }
        return false;
    }

    @GuardedBy("this")
    private void clearLocationDisagreementCandidates() {
        mLocationDisagreementCandidate = null;
    }

    @GuardedBy("this")
    private void clearTelephonyDisagreementCandidates() {
        mTelephonyDisagreementCandidate = null;
    }

    /** Handles a location suggestion that agrees with the current time zone. */
    @GuardedBy("this")
    private void handleAgreeingLocationSuggestion(@NonNull List<String> newZoneIds) {
        mCurrentFusedSignals.update(ORIGIN_LOCATION, newZoneIds);
        mCurrentFusedSignals.setQualityForOrigin(ORIGIN_LOCATION, QUALITY_HIGH);
        clearLocationDisagreementCandidates();
    }

    /** Handles a location suggestion that overrides a weak or non-existent time zone. */
    @GuardedBy("this")
    private void handleLocationOverride(@NonNull List<String> newZoneIds) {
        FusedSignals timeZone = new FusedSignals(newZoneIds, ORIGIN_LOCATION);

        if (mTelephonyDisagreementCandidate != null
                && newZoneIds.contains(mTelephonyDisagreementCandidate.getZoneIds().getFirst())) {
            // If this location override agrees with a pending telephony disagreement candidate
            // (from location-only mode), we can resolve the disagreement by adding telephony as an
            // origin for the new time zone.
            timeZone.addOrigin(
                    mTelephonyDisagreementCandidate.getOrigin(),
                    mTelephonyDisagreementCandidate.getZoneIds(),
                    mTelephonyDisagreementCandidate.getTimestamp());
        }

        clearTelephonyDisagreementCandidates();
        clearLocationDisagreementCandidates();
        updateCurrentFusedSignals(timeZone);
        setDeviceTimeZoneIfRequired(timeZone, SOURCE_LOCATION);
    }

    /**
     * Handles a location suggestion that disagrees with the current (likely telephony-set) zone.
     * This accumulates candidates and can trigger a location override.
     */
    @GuardedBy("this")
    private void handleDisagreeingLocationSuggestion(@NonNull LocationAlgorithmEvent event) {
        mTotalDisagreements++;

        if (mLocationDisagreementCandidate != null
                && event.getSuggestion()
                        .getZoneIds()
                        .get(0)
                        .equals(mLocationDisagreementCandidate.getZoneIds().getFirst())) {
            mLocationDisagreementCandidate.markOccurrence();
        } else {
            // The new disagreement is inconsistent with previous ones, so start over.
            mLocationDisagreementCandidate =
                    new TimeZoneDisagreementCandidate(
                            ORIGIN_LOCATION,
                            event.getSuggestion().getZoneIds(),
                            SystemClock.elapsedRealtime());
        }

        if (mTelephonyDisagreementCandidate != null
                && mLocationDisagreementCandidate
                        .getZoneIds()
                        .contains(mTelephonyDisagreementCandidate.getZoneIds().getFirst())) {
            // A new location suggestion has arrived that disagrees with the current time zone.
            // If this new location suggestion *agrees* with a pending telephony disagreement
            // candidate, it means both location and telephony are now aligned on a new time zone.
            // This is a strong signal to switch immediately, resolving both disagreements at once.
            FusedSignals fusedSignals =
                    new FusedSignals(event.getSuggestion().getZoneIds(), ORIGIN_LOCATION)
                            .setOriginDetectedTimestamp(
                                    ORIGIN_LOCATION, mLocationDisagreementCandidate.getTimestamp())
                            .addOrigin(
                                    mTelephonyDisagreementCandidate.getOrigin(),
                                    mTelephonyDisagreementCandidate.getZoneIds(),
                                    mTelephonyDisagreementCandidate.getTimestamp());

            clearTelephonyDisagreementCandidates();
            clearLocationDisagreementCandidates();

            updateCurrentFusedSignals(fusedSignals);
            setDeviceTimeZoneIfRequired(fusedSignals, SOURCE_LOCATION);
        } else if (mLocationDisagreementCandidate.getOccurrenceCount()
                >= LOCATION_OVERRIDE_THRESHOLD) {
            // Location has disagreed consistently, so we trust it over other signals.
            setLocationOnlyTzDetection(true);

            FusedSignals fusedSignals =
                    new FusedSignals(event.getSuggestion().getZoneIds(), ORIGIN_LOCATION)
                            .setOriginDetectedTimestamp(
                                    ORIGIN_LOCATION, mLocationDisagreementCandidate.getTimestamp());

            clearLocationDisagreementCandidates();

            updateCurrentFusedSignals(fusedSignals);
            setDeviceTimeZoneIfRequired(fusedSignals, SOURCE_LOCATION);
        }
    }

    @VisibleForTesting
    TimeZoneDisagreementCandidate getLocationDisagreementCandidate() {
        synchronized (this) {
            return mLocationDisagreementCandidate;
        }
    }

    @Override
    public void replay() {
        synchronized (this) {
            if (mCurrentFusedSignals.hasNoOrigins(QUALITY_LOW)) {
                return;
            }

            setDeviceTimeZoneIfRequired(mCurrentFusedSignals, "replay");
        }
    }

    private void printMapItems(@NonNull IndentingPrintWriter ipw, Map<String, Set<String>> map) {
        map.forEach(
                (key, value) -> {
                    ipw.println(key + ":");
                    printListItems(ipw, value);
                });
    }

    private void printListItems(@NonNull IndentingPrintWriter ipw, Collection<?> list) {
        list.forEach(x -> ipw.println("- " + x));
    }

    @GuardedBy("this")
    private void updateCurrentFusedSignals(@NonNull FusedSignals fusedSignals) {
        mCurrentFusedSignals = fusedSignals;
        addTimeZoneToHistory(fusedSignals);
    }

    private void setDeviceTimeZoneIfRequired(@NonNull FusedSignals fusedSignals, String source) {
        if (mServiceConfigAccessor.getCurrentUserConfigurationInternal().getDetectionMode()
                == ConfigurationInternal.DETECTION_MODE_MANUAL) {
            return;
        }
        if (fusedSignals.getTimeZoneId() == null) {
            return;
        }
        if (mTimeZoneSetter == null) {
            return;
        }
        String cause = "Found good suggestion from " + source;
        if (Flags.enableTimeZoneTransitionTelemetryLogging()
                || Flags.enablePermanentTimeZoneCorrectnessTelemetryLogging()) {
            mTelemetry.onFusedTimeZoneChanged(fusedSignals.getTimeZoneId());
        }
        mTimeZoneSetter.setDeviceTimeZoneIfRequired(
                fusedSignals.getTimeZoneId(), cause, ORIGIN_FUSED);
    }

    @GuardedBy("this")
    private void addTimeZoneToHistory(FusedSignals fusedSignals) {
        mTimeZoneHistory.set(fusedSignals);
    }

    private void setupAirplaneModeListener() {
        synchronized (this) {
            mIsAirplaneModeOn = isAirplaneModeOn();

            if (mIsAirplaneModeOn) {
                // The device has rebooted while Airplane Mode is on.
                mLastAirplaneModeEvent = new AirplaneModeEvent(/* startTimeMillis= */ 0L);
            }
        }

        mDeviceActivityMonitor.addListener(
                new DeviceActivityMonitor.Listener() {
                    @Override
                    public void onFlightStart() {
                        synchronized (FusedTimeZoneDetectorImpl.this) {
                            mIsAirplaneModeOn = true;
                            mLastAirplaneModeEvent = new AirplaneModeEvent();
                            mHandler.postDelayed(
                                    mAirplaneModeTimeoutRunnable,
                                    mAirplaneModeResetDelay.toMillis());
                        }
                    }

                    @Override
                    public void onFlightComplete() {
                        synchronized (FusedTimeZoneDetectorImpl.this) {
                            mIsAirplaneModeOn = false;

                            if (mLastAirplaneModeEvent != null) {
                                mLastAirplaneModeEvent.recordEndTime();
                                mAirplaneModeEventHistory.set(mLastAirplaneModeEvent);
                                mLastAirplaneModeEvent = null;
                            }

                            mHandler.removeCallbacks(mAirplaneModeTimeoutRunnable);
                        }
                    }
                });
    }

    private boolean isLocationTimeZoneDetectionEnabled() {
        return mServiceConfigAccessor
                        .getCurrentUserConfigurationInternal()
                        .getLocationEnabledSetting()
                && mServiceConfigAccessor
                        .getCurrentUserConfigurationInternal()
                        .isGeoDetectionSupported()
                && isGeoDetectionEnabledInSettings();
    }

    private boolean isTelephonyTimeZoneDetectionSupported() {
        return mServiceConfigAccessor
                .getCurrentUserConfigurationInternal()
                .isTelephonyDetectionSupported();
    }

    /** Gets the state of Airplane Mode. */
    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(
                        mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0)
                != 0;
    }

    private boolean isGeoDetectionEnabledInSettings() {
        ConfigurationInternal config = mServiceConfigAccessor.getCurrentUserConfigurationInternal();

        // If the user has explicitly enabled "Use location for time zone", use that setting.
        if (config.getGeoDetectionEnabledSetting()) {
            return true;
        }

        // On devices without telephony (e.g. Wi-Fi-only tablets), the single "Automatic time zone
        // detection" toggle is the only way to enable location-based time zone detection. As per
        // the setting's wording, enabling this toggle acts as a direct acceptance to use location
        // for this purpose.
        return config.getAutoDetectionEnabledBehavior()
                && !config.isTelephonyDetectionSupported()
                && config.isGeoDetectionSupported();
    }

    /** Dumps internal state such as field values. */
    @Override
    public synchronized void dump(@NonNull IndentingPrintWriter ipw, @Nullable String[] args) {
        ipw.println(
                "FusedTimeZoneDetector ("
                        + Duration.ofMillis(SystemClock.elapsedRealtime()).toString()
                        + ")");
        ipw.increaseIndent(); // level 1

        ipw.println("Configuration:");
        ipw.increaseIndent(); // level 2
        ipw.println("isLocationTimeZoneDetectionEnabled=" + isLocationTimeZoneDetectionEnabled());

        ipw.increaseIndent(); // level 3
        ipw.println(
                "isLocationEnabledSetting="
                        + mServiceConfigAccessor
                                .getCurrentUserConfigurationInternal()
                                .getLocationEnabledSetting());
        ipw.println(
                "isGeoDetectionSupported="
                        + mServiceConfigAccessor
                                .getCurrentUserConfigurationInternal()
                                .isGeoDetectionSupported());
        ipw.println("isGeoDetectionEnabledSetting=" + isGeoDetectionEnabledInSettings());
        ipw.decreaseIndent(); // level 3

        ipw.println("isTelephonySupported=" + isTelephonyTimeZoneDetectionSupported());
        ipw.println(
                "mIsAirplaneModeOn="
                        + mIsAirplaneModeOn
                        + (mIsAirplaneModeOn && mLastAirplaneModeEvent != null
                                ? " (" + mLastAirplaneModeEvent.getStartTimeToString() + ")"
                                : ""));
        ipw.println("mIsLocationOnlyTzDetection=" + mIsLocationOnlyTzDetection);
        ipw.println("mCurrentFusedSignals=" + mCurrentFusedSignals);
        ipw.decreaseIndent(); // level 1

        ipw.println("Time zone history:");
        ipw.increaseIndent(); // level 2
        mTimeZoneHistory.dump(ipw);
        ipw.decreaseIndent(); // level 1

        ipw.println("Airplane mode history:");
        ipw.increaseIndent(); // level 2
        mAirplaneModeEventHistory.dump(ipw);
        ipw.decreaseIndent(); // level 1

        ipw.println("Usage stats:");
        ipw.increaseIndent(); // level 2
        ipw.println("mValidLocationUpdates=" + mValidLocationUpdates);
        ipw.println("mTotalLocationUpdates=" + mTotalLocationUpdates);
        ipw.println("mValidTelephonyUpdates=" + mValidTelephonyUpdates);
        ipw.println("mTotalTelephonyUpdates=" + mTotalTelephonyUpdates);
        ipw.println("mTotalDisagreements=" + mTotalDisagreements);
        ipw.decreaseIndent(); // level 1

        ipw.println("Detection state:");
        ipw.increaseIndent(); // level 2
        ipw.println("mLastTelephonySuggestion=" + mLastTelephonySuggestion);

        if (!mUntrustedTelephonyTz.isEmpty()) {
            ipw.println("mUntrustedTelephonyTz (count=" + mUntrustedTelephonyTz.size() + "):");
            ipw.increaseIndent(); // level 3
            printMapItems(ipw, mUntrustedTelephonyTz);
            ipw.decreaseIndent(); // level 2
        }

        if (mLocationDisagreementCandidate != null) {
            ipw.println("mLocationDisagreementCandidate:");
            ipw.increaseIndent(); // level 3
            ipw.println("- " + mLocationDisagreementCandidate);
            ipw.decreaseIndent(); // level 2
        }

        if (mTelephonyDisagreementCandidate != null) {
            ipw.println("mTelephonyDisagreementCandidate:");
            ipw.increaseIndent(); // level 3
            ipw.println("- " + mTelephonyDisagreementCandidate);
            ipw.decreaseIndent(); // level 2
        }

        ipw.decreaseIndent(); // level 1
        ipw.decreaseIndent(); // level 0
    }
}
