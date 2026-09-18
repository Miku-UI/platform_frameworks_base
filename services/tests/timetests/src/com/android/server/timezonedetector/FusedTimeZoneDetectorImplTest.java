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

import static android.app.time.DetectorStatusTypes.DETECTION_ALGORITHM_STATUS_RUNNING;
import static android.app.time.LocationTimeZoneAlgorithmStatus.PROVIDER_STATUS_IS_CERTAIN;
import static android.app.time.LocationTimeZoneAlgorithmStatus.PROVIDER_STATUS_NOT_PRESENT;
import static android.service.timezone.TimeZoneProviderStatus.DEPENDENCY_STATUS_NOT_APPLICABLE;
import static android.service.timezone.TimeZoneProviderStatus.DEPENDENCY_STATUS_OK;
import static android.service.timezone.TimeZoneProviderStatus.OPERATION_STATUS_OK;

import static com.android.server.timezonedetector.FusedTimeZoneDetector.TimeZoneSetter;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_FUSED;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_LOCATION;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_TELEPHONY;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.Origin;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategyImpl.TELEPHONY_SCORE_HIGH;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategyImpl.TELEPHONY_SCORE_LOW;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.time.LocationTimeZoneAlgorithmStatus;
import android.app.timezonedetector.TelephonySignal;
import android.app.timezonedetector.TelephonyTimeZoneSuggestion;
import android.content.ContentResolver;
import android.content.Context;
import android.os.HandlerThread;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.timezone.TimeZoneProviderStatus;
import android.util.IndentingPrintWriter;

import com.android.server.SystemTimeZone.TimeZoneConfidence;
import com.android.server.timezonedetector.ftzd.FusedSignals;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

@RunWith(MockitoJUnitRunner.class)
@EnableFlags({
    android.timezone.flags.Flags.FLAG_ENABLE_TIME_ZONE_TRANSITION_TELEMETRY_LOGGING,
    android.timezone.flags.Flags.FLAG_ENABLE_PERMANENT_TIME_ZONE_CORRECTNESS_TELEMETRY_LOGGING
})
public class FusedTimeZoneDetectorImplTest {
    @ClassRule public static final SetFlagsRule.ClassRule mClassRule = new SetFlagsRule.ClassRule();
    @Rule public final SetFlagsRule mSetFlagsRule = mClassRule.createSetFlagsRule();

    public static final TimeZoneProviderStatus ARBITRARY_PROVIDER_STATUS =
            new TimeZoneProviderStatus.Builder()
                    .setConnectivityDependencyStatus(DEPENDENCY_STATUS_OK)
                    .setLocationDetectionDependencyStatus(DEPENDENCY_STATUS_NOT_APPLICABLE)
                    .setTimeZoneResolutionOperationStatus(OPERATION_STATUS_OK)
                    .build();

    public static final LocationTimeZoneAlgorithmStatus ARBITRARY_LOCATION_ALGORITHM_STATUS =
            new LocationTimeZoneAlgorithmStatus(
                    /* status= */ DETECTION_ALGORITHM_STATUS_RUNNING,
                    /* primaryProviderStatus= */ PROVIDER_STATUS_IS_CERTAIN,
                    /* primaryProviderReportedStatus= */ ARBITRARY_PROVIDER_STATUS,
                    /* secondaryProviderStatus= */ PROVIDER_STATUS_NOT_PRESENT,
                    /* secondaryProviderReportedStatus= */ null);

    @Mock private Context mMockContext;
    @Mock private ContentResolver mMockContentResolver;
    @Mock private TimeZoneDetectorTelemetry mMockTelemetry;

    private FakeDeviceActivityMonitor mFakeDeviceActivityMonitor;
    private FakeServiceConfigAccessor mFakeServiceConfigAccessor;
    private TestHandler mTestHandler;
    private FakeTimeZoneSetter mFakeTimeZoneSetter;
    private HandlerThread mHandlerThread;

    private FusedTimeZoneDetectorImpl mFusedTimeZoneDetector;
    private Script mScript;
    private TimeZone mOriginalDefaultTimeZone;

    @Before
    public void setUp() throws Exception {
        mOriginalDefaultTimeZone = TimeZone.getDefault();
        // Set a default that is unlikely to be used in tests to avoid flakiness.
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/UTC"));

        mHandlerThread = new HandlerThread("FusedTimeZoneDetectorImplTest");
        mHandlerThread.start();
        mTestHandler = new TestHandler(mHandlerThread.getLooper());

        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        doNothing().when(mMockContext).sendBroadcast(any());

        mFakeServiceConfigAccessor = spy(new FakeServiceConfigAccessor());
        mFakeDeviceActivityMonitor = new FakeDeviceActivityMonitor();
        mFakeTimeZoneSetter = new FakeTimeZoneSetter();

        mFusedTimeZoneDetector =
                new FusedTimeZoneDetectorImpl(
                        mMockContext,
                        mFakeServiceConfigAccessor,
                        mFakeDeviceActivityMonitor,
                        mMockTelemetry,
                        mTestHandler,
                        /* airplaneModeResetDelay= */ Duration.ofSeconds(1));
        mFusedTimeZoneDetector.init();
        mFusedTimeZoneDetector.setTimeZoneSetter(mFakeTimeZoneSetter);

        mFakeServiceConfigAccessor.initializeCurrentUserConfiguration(
                ConfigInternalForTests.CONFIG_AUTO_ENABLED_GEO_ENABLED);

        mScript = new Script();
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quitSafely();
        mHandlerThread.join();
    }

    @After
    public void resetDefaultTimeZone() throws Exception {
        TimeZone.setDefault(mOriginalDefaultTimeZone);
    }

    @Test
    public void onTelephonyTimeZoneDetected_setsInitialTimeZone() {
        String timeZoneId = "America/New_York";
        QualifiedTelephonyTimeZoneSuggestion suggestion =
                createTelephonySuggestion(timeZoneId, TELEPHONY_SCORE_HIGH, "310", "us");

        mScript.simulateTelephonySuggestion(suggestion).verifyTimeZoneSuggested(timeZoneId);
        verify(mMockTelemetry).onTelephonyTimeZoneSuggestion(eq(suggestion.suggestion()));
    }

    @Test
    public void onTelephonyTimeZoneDetected_ignoredIfScoreIsTooLow() {
        String timeZoneId = "America/New_York";
        QualifiedTelephonyTimeZoneSuggestion suggestion =
                createTelephonySuggestion(timeZoneId, TELEPHONY_SCORE_LOW, "310", "us");

        mScript.simulateTelephonySuggestion(suggestion).verifyTimeZoneNotChanged();
    }

    @Test
    public void onTelephonyTimeZoneDetected_resistsDisagreeingLocationSuggestion() {
        String initialZoneId = "America/New_York";
        String disagreeingZoneId = "Europe/London";

        // 1. Set initial time zone via location.
        LocationAlgorithmEvent locationEvent = createLocationEvent(initialZoneId);
        mScript.simulateLocationEvent(locationEvent).verifyTimeZoneSuggested(initialZoneId);
        verify(mMockTelemetry).onGeolocationTimeZoneSuggestion(eq(locationEvent.getSuggestion()));

        // 2. Send an agreeing telephony suggestion. The time zone should not change.
        // This internally strengthens the current time zone by adding a second origin.
        QualifiedTelephonyTimeZoneSuggestion telephonySuggestion =
                createTelephonySuggestion(initialZoneId, TELEPHONY_SCORE_HIGH, "310", "us");
        mScript.simulateTelephonySuggestion(telephonySuggestion).verifyTimeZoneNotChanged();
        verify(mMockTelemetry).onTelephonyTimeZoneSuggestion(eq(telephonySuggestion.suggestion()));

        // 3. Send a disagreeing location suggestion. Because the current zone is supported by
        // both location and telephony, it should not be immediately overridden. A disagreement
        // process should start instead.
        mScript.simulateLocationEvent(createLocationEvent(disagreeingZoneId))
                .verifyTimeZoneNotChanged();
    }

    @Test
    public void onLocationTimeZoneDetected_setsInitialTimeZone() {
        String timeZoneId = "Europe/London";
        LocationAlgorithmEvent event = createLocationEvent(timeZoneId);

        mScript.simulateLocationEvent(event).verifyTimeZoneSuggested(timeZoneId);
    }

    @Test
    public void onLocationTimeZoneDetected_ignoredWhenGeoDetectionIsEffectivelyDisabled() {
        String initialZoneId = "America/New_York";
        String newZoneId = "Europe/London";

        // Set initial time zone via location.
        LocationAlgorithmEvent locationEvent = createLocationEvent(initialZoneId);
        mScript.simulateLocationEvent(locationEvent).verifyTimeZoneSuggested(initialZoneId);
        verify(mMockTelemetry).onGeolocationTimeZoneSuggestion(eq(locationEvent.getSuggestion()));

        // A base configuration that enables everything.
        ConfigurationInternal.Builder baseConfigBuilder =
                new ConfigurationInternal.Builder(
                        ConfigInternalForTests.CONFIG_AUTO_ENABLED_GEO_ENABLED);

        // Test case 1: isGeoDetectionSupported() is false.
        ConfigurationInternal geoUnsupportedConfig =
                baseConfigBuilder.setGeoDetectionFeatureSupported(false).build();
        mFakeServiceConfigAccessor.simulateCurrentUserConfigurationInternalChange(
                geoUnsupportedConfig);
        mScript.simulateLocationEvent(createLocationEvent(newZoneId)).verifyTimeZoneNotChanged();

        // Test case 2: getGeoDetectionEnabledSetting() is false.
        // This only matters when telephony detection is also supported.
        ConfigurationInternal geoDisabledConfig =
                new ConfigurationInternal.Builder(
                                ConfigInternalForTests.CONFIG_AUTO_ENABLED_GEO_ENABLED)
                        .setGeoDetectionEnabledSetting(false)
                        .build();
        mFakeServiceConfigAccessor.simulateCurrentUserConfigurationInternalChange(
                geoDisabledConfig);
        mScript.simulateLocationEvent(createLocationEvent(newZoneId)).verifyTimeZoneNotChanged();

        // Test case 3: getLocationEnabledSetting() is false.
        ConfigurationInternal locationDisabledConfig =
                new ConfigurationInternal.Builder(
                                ConfigInternalForTests.CONFIG_AUTO_ENABLED_GEO_ENABLED)
                        .setLocationEnabledSetting(false)
                        .build();
        mFakeServiceConfigAccessor.simulateCurrentUserConfigurationInternalChange(
                locationDisabledConfig);
        mScript.simulateLocationEvent(createLocationEvent(newZoneId)).verifyTimeZoneNotChanged();

        // Test case 4: Not ignored when only geo is supported and auto detection is on.
        ConfigurationInternal geoOnlyConfig =
                new ConfigurationInternal.Builder(
                                ConfigInternalForTests.CONFIG_AUTO_ENABLED_GEO_ENABLED)
                        .setTelephonyDetectionFeatureSupported(false)
                        .setGeoDetectionEnabledSetting(false)
                        .build();
        mFakeServiceConfigAccessor.simulateCurrentUserConfigurationInternalChange(geoOnlyConfig);
        mScript.simulateLocationEvent(createLocationEvent(newZoneId))
                .verifyTimeZoneSuggested(newZoneId);
    }

    @Test
    public void onLocationTimeZoneDetected_disagreementTriggersOverride() {
        String telephonyZoneId = "America/New_York";
        String locationZoneId = "Europe/London";

        // Set initial zone with Telephony
        mScript.simulateTelephonySuggestion(
                        createTelephonySuggestion(
                                telephonyZoneId, TELEPHONY_SCORE_HIGH, "310", "us"))
                .verifyTimeZoneSuggested(telephonyZoneId);

        // First disagreement from location
        mScript.simulateLocationEvent(createLocationEvent(locationZoneId))
                .verifyTimeZoneNotChanged();

        // Second disagreement from location should trigger an override
        mScript.simulateLocationEvent(createLocationEvent(locationZoneId))
                .verifyTimeZoneSuggested(locationZoneId);
    }

    @Test
    public void onLocationTimeZoneDetected_disagreementAndUntrustingTelephony() throws Exception {
        String telephonyZoneId = "America/New_York";
        String locationZoneId = "Europe/London";
        String telephonyMcc = "310";

        // 1. Telephony update arrives, check that it gets set.
        QualifiedTelephonyTimeZoneSuggestion telephonySuggestion =
                createTelephonySuggestion(
                        telephonyZoneId, TELEPHONY_SCORE_HIGH, telephonyMcc, "us");
        mScript.simulateTelephonySuggestion(telephonySuggestion)
                .verifyTimeZoneSuggested(telephonyZoneId);

        // 2. Then a disagreeing location update arrives, check that time zone doesn't change.
        LocationAlgorithmEvent locationEvent = createLocationEvent(locationZoneId);
        mScript.simulateLocationEvent(locationEvent).verifyTimeZoneNotChanged();

        // 3. Then the same disagreeing location update is received LOCATION_OVERRIDE_THRESHOLD
        // times, check that the time zone changes.
        for (int i = 1; i < FusedTimeZoneDetectorImpl.LOCATION_OVERRIDE_THRESHOLD; i++) {
            mScript.simulateLocationEvent(locationEvent);
        }
        mScript.verifyTimeZoneSuggested(locationZoneId);

        // 4. Then the same telephony from the start arrives, check that time zone doesn't change
        //    and that the mcc is added to mUntrustedTelephonyTz.
        mScript.simulateTelephonySuggestion(telephonySuggestion).verifyTimeZoneNotChanged();
        assertTrue(mFusedTimeZoneDetector.mUntrustedTelephonyTz.containsKey(telephonyMcc));
        assertTrue(
                mFusedTimeZoneDetector
                        .mUntrustedTelephonyTz
                        .get(telephonyMcc)
                        .contains(telephonyZoneId));

        // 5. Then a different location arrives, check that the time zone changes.
        String newLocationZoneId = "Asia/Tokyo";
        LocationAlgorithmEvent newLocationEvent = createLocationEvent(newLocationZoneId);
        mScript.simulateLocationEvent(newLocationEvent).verifyTimeZoneSuggested(newLocationZoneId);

        // 6. Then a different telephony arrives, check that the time zone does not change
        //    because the detector is in location-only mode.
        String newTelephonyZoneId = "Australia/Sydney";
        QualifiedTelephonyTimeZoneSuggestion newTelephonySuggestion =
                createTelephonySuggestion(newTelephonyZoneId, TELEPHONY_SCORE_HIGH, "505", "au");
        mScript.simulateTelephonySuggestion(newTelephonySuggestion).verifyTimeZoneNotChanged();

        // 7. Then a different telephony arrives, confirming the location-based time zone.
        newTelephonyZoneId = "Asia/Tokyo";
        newTelephonySuggestion =
                createTelephonySuggestion(newTelephonyZoneId, TELEPHONY_SCORE_HIGH, "440", "jp");
        mScript.simulateTelephonySuggestion(newTelephonySuggestion).verifyTimeZoneNotChanged();

        // 8. Then a different telephony arrives, check that the time zone changes because the
        //    detector is in not in location-only mode.
        newTelephonyZoneId = "Australia/Sydney";
        newTelephonySuggestion =
                createTelephonySuggestion(newTelephonyZoneId, TELEPHONY_SCORE_HIGH, "506", "au");
        mScript.simulateTelephonySuggestion(newTelephonySuggestion)
                .verifyTimeZoneSuggested(newTelephonyZoneId);
    }

    @Test
    public void onLocationTimeZoneDetected_inconsistentDisagreementResetsCounter() {
        String telephonyZoneId = "America/New_York";
        String firstLocationZoneId = "Europe/London";
        String secondLocationZoneId = "Asia/Tokyo";

        // 1. Set initial zone with Telephony.
        mScript.simulateTelephonySuggestion(
                        createTelephonySuggestion(
                                telephonyZoneId, TELEPHONY_SCORE_HIGH, "310", "us"))
                .verifyTimeZoneSuggested(telephonyZoneId);

        // 2. First disagreement from location, time zone should not change.
        mScript.simulateLocationEvent(createLocationEvent(firstLocationZoneId))
                .verifyTimeZoneNotChanged();

        // 3. A second, but different, disagreement from location. This should reset the
        // previous disagreement and not trigger an override.
        mScript.simulateLocationEvent(createLocationEvent(secondLocationZoneId))
                .verifyTimeZoneNotChanged();

        // 4. A third, consistent disagreement from location. This should now trigger an override
        // to the second location zone.
        mScript.simulateLocationEvent(createLocationEvent(secondLocationZoneId))
                .verifyTimeZoneSuggested(secondLocationZoneId);
    }

    @Test
    public void telephonyResolvesLocationDisagreement_onlyTelephonyOrigin() throws Exception {
        String initialTelephonyZoneId = "America/New_York";
        String locationZoneId = "Europe/London";

        // Set initial time zone with a telephony suggestion.
        mScript.simulateTelephonySuggestion(
                        createTelephonySuggestion(
                                initialTelephonyZoneId, TELEPHONY_SCORE_HIGH, "310", "us"))
                .verifyTimeZoneSuggested(initialTelephonyZoneId);

        // A disagreeing location suggestion arrives. Time zone should not change, but a
        // disagreement candidate should be recorded.
        mScript.simulateLocationEvent(createLocationEvent(locationZoneId))
                .verifyTimeZoneNotChanged()
                .verifyLocationDisagreementCandidatesPresent();

        // A new telephony suggestion arrives that agrees with the pending location suggestion.
        // This should resolve the disagreement and update the time zone.
        // Sleep to ensure the new telephony suggestion has a later timestamp than the location
        // suggestion, which is asserted below.
        Thread.sleep(1000);
        mScript.simulateTelephonySuggestion(
                        createTelephonySuggestion(
                                locationZoneId, TELEPHONY_SCORE_HIGH, "234", "gb"))
                .verifyTimeZoneSuggested(locationZoneId)
                .verifyNoLocationDisagreementCandidatesPresent();

        FusedSignals currentSignals = mFusedTimeZoneDetector.getCurrentFusedSignals();
        assertTrue(
                "No location origin found: " + currentSignals,
                currentSignals.hasOrigin(ORIGIN_LOCATION, 0));
        assertTrue(
                "No telephony origin found: " + currentSignals,
                currentSignals.hasOrigin(ORIGIN_TELEPHONY, 0));

        assertTrue(
                currentSignals.getOriginInfoForOrigin(ORIGIN_TELEPHONY).getTimestampDetected()
                        > currentSignals
                                .getOriginInfoForOrigin(ORIGIN_LOCATION)
                                .getTimestampDetected());
    }

    @Test
    public void telephonyResolvesLocationDisagreement_AllOrigin() throws Exception {
        String initialTelephonyZoneId = "America/New_York";
        String locationZoneId = "Europe/London";

        // Set initial time zone with a telephony suggestion.
        mScript.simulateTelephonySuggestion(
                        createTelephonySuggestion(
                                initialTelephonyZoneId, TELEPHONY_SCORE_HIGH, "310", "us"))
                .verifyTimeZoneSuggested(initialTelephonyZoneId);

        // Confirm time zone with a location suggestion.
        mScript.simulateLocationEvent(createLocationEvent(initialTelephonyZoneId))
                .verifyTimeZoneNotChanged();

        // A disagreeing location suggestion arrives. Time zone should not change, but a
        // disagreement candidate should be recorded.
        mScript.simulateLocationEvent(createLocationEvent(locationZoneId))
                .verifyTimeZoneNotChanged()
                .verifyLocationDisagreementCandidatesPresent();

        // A new telephony suggestion arrives that agrees with the pending location suggestion.
        // This should resolve the disagreement and update the time zone.
        // Sleep to ensure the new telephony suggestion has a later timestamp than the location
        // suggestion, which is asserted below.
        Thread.sleep(1000);
        mScript.simulateTelephonySuggestion(
                        createTelephonySuggestion(
                                locationZoneId, TELEPHONY_SCORE_HIGH, "234", "gb"))
                .verifyTimeZoneSuggested(locationZoneId)
                .verifyNoLocationDisagreementCandidatesPresent();

        FusedSignals currentSignals = mFusedTimeZoneDetector.getCurrentFusedSignals();
        assertTrue(
                "No location origin found: " + currentSignals,
                currentSignals.hasOrigin(ORIGIN_LOCATION, 0));
        assertTrue(
                "No telephony origin found: " + currentSignals,
                currentSignals.hasOrigin(ORIGIN_TELEPHONY, 0));

        assertTrue(
                currentSignals.getOriginInfoForOrigin(ORIGIN_TELEPHONY).getTimestampDetected()
                        > currentSignals
                                .getOriginInfoForOrigin(ORIGIN_LOCATION)
                                .getTimestampDetected());
    }

    @Test
    public void airplaneMode_ignoresSuggestions() {
        mFakeDeviceActivityMonitor.simulateFlightStart();

        mScript.simulateTelephonySuggestion(
                        createTelephonySuggestion(
                                "America/New_York", TELEPHONY_SCORE_HIGH, "310", "us"))
                .verifyTimeZoneNotChanged();

        mScript.simulateLocationEvent(createLocationEvent("Europe/London"))
                .verifyTimeZoneNotChanged();

        mFakeDeviceActivityMonitor.simulateFlightComplete();

        // After airplane mode is off, suggestions should be processed.
        mScript.simulateTelephonySuggestion(
                        createTelephonySuggestion(
                                "America/Chicago", TELEPHONY_SCORE_HIGH, "310", "us"))
                .verifyTimeZoneSuggested("America/Chicago");
    }

    @Test
    public void dump_doesNotCrash() {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        IndentingPrintWriter indentingPrintWriter = new IndentingPrintWriter(printWriter);
        mFusedTimeZoneDetector.dump(indentingPrintWriter, null);
        assertTrue(stringWriter.toString().length() > 0);
    }

    @Test
    public void replay_setsCurrentTimeZone() {
        String timeZoneId = "America/Los_Angeles";
        mScript.simulateTelephonySuggestion(
                        createTelephonySuggestion(timeZoneId, TELEPHONY_SCORE_HIGH, "310", "us"))
                .verifyTimeZoneSuggested(timeZoneId);

        mFusedTimeZoneDetector.replay();
        mTestHandler.waitForMessagesToBeProcessed();

        mFakeTimeZoneSetter.assertTimeZoneChangedTo(timeZoneId);
    }

    private QualifiedTelephonyTimeZoneSuggestion createTelephonySuggestion(
            String zoneId, int score, String mcc, String countryIsoCode) {
        TelephonyTimeZoneSuggestion.Builder builder = new TelephonyTimeZoneSuggestion.Builder(0);
        if (zoneId != null) {
            builder.setZoneId(zoneId);
            builder.setMatchType(TelephonyTimeZoneSuggestion.MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET);
            builder.setQuality(TelephonyTimeZoneSuggestion.QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET);
        }
        TelephonySignal signal =
                new TelephonySignal(mcc, "123", countryIsoCode, Set.of(countryIsoCode), null);
        builder.setTelephonySignal(signal);
        return new QualifiedTelephonyTimeZoneSuggestion(builder.build(), score);
    }

    private LocationAlgorithmEvent createLocationEvent(String... zoneIds) {
        GeolocationTimeZoneSuggestion suggestion =
                GeolocationTimeZoneSuggestion.createCertainSuggestion(
                        1000L, Arrays.asList(zoneIds));

        return new LocationAlgorithmEvent(ARBITRARY_LOCATION_ALGORITHM_STATUS, suggestion);
    }

    private class Script {
        Script simulateTelephonySuggestion(QualifiedTelephonyTimeZoneSuggestion suggestion) {
            mFusedTimeZoneDetector.onTelephonyTimeZoneDetected(suggestion);
            mTestHandler.waitForMessagesToBeProcessed();
            return this;
        }

        Script simulateLocationEvent(LocationAlgorithmEvent event) {
            mFusedTimeZoneDetector.onLocationTimeZoneDetected(event);
            mTestHandler.waitForMessagesToBeProcessed();
            return this;
        }

        Script verifyTimeZoneSuggested(String zoneId) {
            mFakeTimeZoneSetter.assertTimeZoneChangedTo(zoneId);
            mFakeTimeZoneSetter.reset();
            return this;
        }

        Script verifyTimeZoneNotChanged() {
            mFakeTimeZoneSetter.assertTimeZoneNotChanged();
            return this;
        }

        Script verifyLocationDisagreementCandidatesPresent() {
            assertNotNull(mFusedTimeZoneDetector.getLocationDisagreementCandidate());
            return this;
        }

        Script verifyNoLocationDisagreementCandidatesPresent() {
            assertNull(mFusedTimeZoneDetector.getLocationDisagreementCandidate());
            return this;
        }
    }

    private static class FakeDeviceActivityMonitor implements DeviceActivityMonitor {
        private final List<Listener> mListeners = new ArrayList<>();

        @Override
        public void addListener(Listener listener) {
            mListeners.add(listener);
        }

        public void simulateFlightStart() {
            for (Listener listener : mListeners) {
                listener.onFlightStart();
            }
        }

        public void simulateFlightComplete() {
            for (Listener listener : mListeners) {
                listener.onFlightComplete();
            }
        }

        @Override
        public void dump(@NonNull IndentingPrintWriter pw, @Nullable String[] args) {
            // No-op for tests
        }
    }

    private static class FakeTimeZoneSetter implements TimeZoneSetter {
        private String mCurrentTimeZoneId;
        private @TimeZoneConfidence int mCurrentConfidence;
        private @TimeZoneDetectorStrategy.Origin int mLastOrigin = -1;
        private boolean mTimeZoneChanged = false;

        FakeTimeZoneSetter() {}

        @Override
        public void setDeviceTimeZoneIfRequired(
                @NonNull String timeZoneId, @NonNull String cause, @Origin int origin) {
            mCurrentTimeZoneId = timeZoneId;
            mLastOrigin = origin;
            mTimeZoneChanged = true;
        }

        void assertTimeZoneChangedTo(String zoneId) {
            assertEquals(zoneId, mCurrentTimeZoneId);
            assertEquals(ORIGIN_FUSED, mLastOrigin);
        }

        void assertTimeZoneNotChanged() {
            assertFalse(mTimeZoneChanged);
        }

        void reset() {
            mCurrentTimeZoneId = null;
            mLastOrigin = -1;
            mTimeZoneChanged = false;
        }
    }
}
