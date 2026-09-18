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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.app.timezonedetector.NitzSignal;
import android.app.timezonedetector.TelephonySignal;
import android.app.timezonedetector.TelephonyTimeZoneSuggestion;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.icu.util.TimeZone;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.core.app.ApplicationProvider;

import com.android.internal.R;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@RunWith(JUnit4.class)
@EnableFlags({
    android.timezone.flags.Flags.FLAG_ENABLE_TIME_ZONE_TRANSITION_TELEMETRY_LOGGING,
    android.timezone.flags.Flags.FLAG_ENABLE_PERMANENT_TIME_ZONE_CORRECTNESS_TELEMETRY_LOGGING
})
public class TimeZoneDetectorTelemetryImplTest {

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    @ClassRule public static final SetFlagsRule.ClassRule mClassRule = new SetFlagsRule.ClassRule();
    @Rule public final SetFlagsRule mSetFlagsRule = mClassRule.createSetFlagsRule();

    private static final String ZONE_A = "America/Los_Angeles";
    private static final String ZONE_B = "America/New_York";
    private static final String ZONE_C = "Europe/London";
    private static final String MCC = "310";
    private static final String MNC = "260";
    private static final String COUNTRY_ISO_CODE = "us";
    private static final int NITZ_OFFSET_SECONDS = 120;
    private static final int NITZ_DST_OFFSET_SECONDS = 60;
    private static final int PRIMARY_LOCATION_TIME_ZONE_PROVIDER_UID = 12345;

    @Mock private PackageManager mMockPackageManager;
    private TimeZoneDetectorTelemetry mTelemetry;
    private FakeEnvironment mFakeEnvironment;
    private FakeTimeZoneDetectorLogger mFakeStatsdLogger;

    @Before
    public void setUp() throws Exception {
        mFakeEnvironment = new FakeEnvironment();
        mFakeEnvironment.initializeClock(
                /* currentTimeMillis= */ 1700000000000L,
                /* elapsedRealtimeMillis= */ 1700000000000L);
        mFakeStatsdLogger = new FakeTimeZoneDetectorLogger();
        // Set up the primary location time zone provider uid.
        Resources resources = ApplicationProvider.getApplicationContext().getResources();
        String packageName = null;
        try {
            packageName =
                    resources.getString(R.string.config_primaryLocationTimeZoneProviderPackageName);
        } catch (Resources.NotFoundException e) {
            // Do nothing. Location time zone provider is not set on the test device.
        }
        ApplicationInfo mApplicationInfo = new ApplicationInfo();
        mApplicationInfo.uid = PRIMARY_LOCATION_TIME_ZONE_PROVIDER_UID;
        when(mMockPackageManager.getApplicationInfo(packageName, 0)).thenReturn(mApplicationInfo);
        mTelemetry =
                new TimeZoneDetectorTelemetryImpl(
                        ApplicationProvider.getApplicationContext(),
                        mFakeEnvironment,
                        mFakeStatsdLogger,
                        mMockPackageManager);
    }

    @Test
    public void initialState_noLogs() {
        assertEquals(0, mFakeStatsdLogger.getTransitionLogCount());
        assertEquals(0, mFakeStatsdLogger.getDiscrepancyLogCount());
    }

    @Test
    public void suggestionsAgree_noLogs() {
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_A));
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_A));
        mTelemetry.onFusedTimeZoneChanged(ZONE_A);
        advanceTime(Duration.ofHours(4));
        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());
        assertEquals(0, mFakeStatsdLogger.getTransitionLogCount());
        assertEquals(0, mFakeStatsdLogger.getDiscrepancyLogCount());
    }

    @Test
    public void disagree_telephonyFirst_discrepancyLoggedAfterTimeout() {
        mTelemetry.onFusedTimeZoneChanged(ZONE_A); // Initial state
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_A));
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_A));

        advanceTime(Duration.ofSeconds(1));
        TelephonyTimeZoneSuggestion telSuggestionB = createTelephonySuggestion(ZONE_B);
        mTelemetry.onTelephonyTimeZoneSuggestion(telSuggestionB); // Disagreement starts

        assertEquals(1, mFakeEnvironment.getDelayedRunnableCount());

        advanceTime(Duration.ofHours(4)); // Time passes, disagreement is deemed to be permanent
        mFakeEnvironment.runDelayedRunnables();
        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());

        assertEquals(1, mFakeStatsdLogger.getDiscrepancyLogCount());
        FakeTimeZoneDetectorLogger.DiscrepancyLog discrepancyLog =
                mFakeStatsdLogger.getDiscrepancyLog(0);
        assertEquals(ZONE_A, discrepancyLog.locationTimeZoneId());
        assertEquals(ZONE_B, discrepancyLog.telephonyTimeZoneId());
        assertEquals(MCC, discrepancyLog.mcc());
        assertEquals(MNC, discrepancyLog.mnc());
        assertEquals(NITZ_OFFSET_SECONDS, discrepancyLog.nitzOffsetSeconds());
        assertEquals(NITZ_DST_OFFSET_SECONDS, discrepancyLog.nitzDstOffsetSeconds());
        assertEquals(TimeZone.getTZDataVersion(), discrepancyLog.tzdbVersion());
        assertEquals(
                PRIMARY_LOCATION_TIME_ZONE_PROVIDER_UID,
                discrepancyLog.locationTimeZoneProviderUid());
    }

    @Test
    public void disagree_geoFirst_discrepancyLoggedAfterTimeout() {
        mTelemetry.onFusedTimeZoneChanged(ZONE_A);
        TelephonyTimeZoneSuggestion telSuggestionA = createTelephonySuggestion(ZONE_A);
        mTelemetry.onTelephonyTimeZoneSuggestion(telSuggestionA);
        GeolocationTimeZoneSuggestion geoSuggestionA = createGeoSuggestion(ZONE_A);
        mTelemetry.onGeolocationTimeZoneSuggestion(geoSuggestionA);

        advanceTime(Duration.ofSeconds(1));
        GeolocationTimeZoneSuggestion geoSuggestionB = createGeoSuggestion(ZONE_B);
        mTelemetry.onGeolocationTimeZoneSuggestion(geoSuggestionB); // Disagreement starts

        assertEquals(1, mFakeEnvironment.getDelayedRunnableCount());

        advanceTime(Duration.ofHours(3).plusMillis(1));
        mFakeEnvironment.runDelayedRunnables();
        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());

        assertEquals(1, mFakeStatsdLogger.getDiscrepancyLogCount());
        FakeTimeZoneDetectorLogger.DiscrepancyLog discrepancyLog =
                mFakeStatsdLogger.getDiscrepancyLog(0);
        assertEquals(ZONE_B, discrepancyLog.locationTimeZoneId());
        assertEquals(ZONE_A, discrepancyLog.telephonyTimeZoneId());
        assertEquals(MCC, discrepancyLog.mcc());
        assertEquals(MNC, discrepancyLog.mnc());
        assertEquals(NITZ_OFFSET_SECONDS, discrepancyLog.nitzOffsetSeconds());
        assertEquals(NITZ_DST_OFFSET_SECONDS, discrepancyLog.nitzDstOffsetSeconds());
        assertEquals(TimeZone.getTZDataVersion(), discrepancyLog.tzdbVersion());
        assertEquals(
                PRIMARY_LOCATION_TIME_ZONE_PROVIDER_UID,
                discrepancyLog.locationTimeZoneProviderUid());
    }

    @Test
    public void disagree_resolveByTelephonyReverting_transitionLogged() {
        mTelemetry.onFusedTimeZoneChanged(ZONE_A);
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_A));
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_B)); // Disagree
        advanceTime(Duration.ofSeconds(1));

        assertEquals(1, mFakeEnvironment.getDelayedRunnableCount());

        advanceTime(Duration.ofSeconds(1));
        TelephonyTimeZoneSuggestion telSuggestionA = createTelephonySuggestion(ZONE_A);
        mTelemetry.onTelephonyTimeZoneSuggestion(telSuggestionA); // Re-agree
        long telTime = mFakeEnvironment.currentTimeMillis();

        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());
        assertEquals(1, mFakeStatsdLogger.getTransitionLogCount());

        FakeTimeZoneDetectorLogger.TransitionLog transitionLog =
                mFakeStatsdLogger.getTransitionLog(0);
        assertEquals(ZONE_A, transitionLog.previousTimeZoneId());
        assertEquals(ZONE_B, transitionLog.timeZoneId());
        assertEquals(telTime, transitionLog.telephonyTimeZoneChangedTimestamp());
        assertEquals(-1, transitionLog.geoTimeZoneChangedTimestamp());
        assertEquals(-1, transitionLog.fusedTimeZoneChangedTimestamp());
        assertEquals(MCC, transitionLog.mcc());
        assertEquals(MNC, transitionLog.mnc());
        assertEquals(NITZ_OFFSET_SECONDS, transitionLog.nitzOffsetSeconds());
        assertEquals(NITZ_DST_OFFSET_SECONDS, transitionLog.nitzDstOffsetSeconds());
        assertEquals(true, transitionLog.transitionAborted());
        assertEquals(TimeZone.getTZDataVersion(), transitionLog.tzdbVersion());
        assertEquals(
                PRIMARY_LOCATION_TIME_ZONE_PROVIDER_UID,
                transitionLog.locationTimeZoneProviderUid());
    }

    @Test
    public void disagree_resolveByGeolocationFollowing_transitionLogged() {
        mTelemetry.onFusedTimeZoneChanged(ZONE_A);
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_A));
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_B)); // Disagree
        long telTime = mFakeEnvironment.currentTimeMillis();
        advanceTime(Duration.ofSeconds(1));

        assertEquals(1, mFakeEnvironment.getDelayedRunnableCount());

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_B)); // Re-agree
        long geoTime = mFakeEnvironment.currentTimeMillis();

        assertEquals(1, mFakeEnvironment.getDelayedRunnableCount());

        assertEquals(0, mFakeStatsdLogger.getTransitionLogCount());

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onFusedTimeZoneChanged(ZONE_B); // Fused agrees
        long fusedTime = mFakeEnvironment.currentTimeMillis();

        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());
        assertEquals(1, mFakeStatsdLogger.getTransitionLogCount());

        FakeTimeZoneDetectorLogger.TransitionLog transitionLog =
                mFakeStatsdLogger.getTransitionLog(0);
        assertEquals(ZONE_A, transitionLog.previousTimeZoneId());
        assertEquals(ZONE_B, transitionLog.timeZoneId());
        assertEquals(telTime, transitionLog.telephonyTimeZoneChangedTimestamp());
        assertEquals(geoTime, transitionLog.geoTimeZoneChangedTimestamp());
        assertEquals(fusedTime, transitionLog.fusedTimeZoneChangedTimestamp());
        assertEquals(MCC, transitionLog.mcc());
        assertEquals(MNC, transitionLog.mnc());
        assertEquals(NITZ_OFFSET_SECONDS, transitionLog.nitzOffsetSeconds());
        assertEquals(NITZ_DST_OFFSET_SECONDS, transitionLog.nitzDstOffsetSeconds());
        assertEquals(false, transitionLog.transitionAborted());
        assertEquals(TimeZone.getTZDataVersion(), transitionLog.tzdbVersion());
        assertEquals(
                PRIMARY_LOCATION_TIME_ZONE_PROVIDER_UID,
                transitionLog.locationTimeZoneProviderUid());
    }

    @Test
    public void reAgreement_cancelsTimeout() {
        mTelemetry.onFusedTimeZoneChanged(ZONE_A);
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_A));
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_B)); // Disagree

        assertEquals(1, mFakeEnvironment.getDelayedRunnableCount());

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_A)); // Re-agree
        mTelemetry.onFusedTimeZoneChanged(ZONE_A);

        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());

        // Simulate time passing beyond the original timeout
        advanceTime(Duration.ofHours(4));
        assertEquals(0, mFakeStatsdLogger.getDiscrepancyLogCount());
    }

    @Test
    public void flipFlop_telephony_transitionAborted() {
        mTelemetry.onFusedTimeZoneChanged(ZONE_A);
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_A));
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_A));

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_B)); // A -> B
        assertEquals(1, mFakeEnvironment.getDelayedRunnableCount());

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_A)); // B -> A
        // Re-agreement, runnable should be removed.
        long telTime = mFakeEnvironment.currentTimeMillis();
        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());

        assertEquals(1, mFakeStatsdLogger.getTransitionLogCount());
        FakeTimeZoneDetectorLogger.TransitionLog transitionLog =
                mFakeStatsdLogger.getTransitionLog(0);
        assertEquals(ZONE_A, transitionLog.previousTimeZoneId());
        assertEquals(ZONE_B, transitionLog.timeZoneId());
        assertEquals(telTime, transitionLog.telephonyTimeZoneChangedTimestamp());
        assertEquals(-1, transitionLog.geoTimeZoneChangedTimestamp());
        assertEquals(-1, transitionLog.fusedTimeZoneChangedTimestamp());
        assertEquals(MCC, transitionLog.mcc());
        assertEquals(MNC, transitionLog.mnc());
        assertEquals(NITZ_OFFSET_SECONDS, transitionLog.nitzOffsetSeconds());
        assertEquals(NITZ_DST_OFFSET_SECONDS, transitionLog.nitzDstOffsetSeconds());
        assertEquals(true, transitionLog.transitionAborted());
        assertEquals(TimeZone.getTZDataVersion(), transitionLog.tzdbVersion());
        assertEquals(
                PRIMARY_LOCATION_TIME_ZONE_PROVIDER_UID,
                transitionLog.locationTimeZoneProviderUid());

        // Ensure no new runnable was posted after re-agreement
        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());
    }

    @Test
    public void flipFlop_telephonyAndFusedFollowed_transitionAborted() {
        mTelemetry.onFusedTimeZoneChanged(ZONE_A);
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_A));
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_A));

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_B)); // A -> B
        assertEquals(1, mFakeEnvironment.getDelayedRunnableCount());

        mTelemetry.onFusedTimeZoneChanged(ZONE_B); // A -> B

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_A)); // B -> A
        long telTime = mFakeEnvironment.currentTimeMillis();
        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onFusedTimeZoneChanged(ZONE_A); // A -> B
        long fusedTime = mFakeEnvironment.currentTimeMillis();
        // Re-agreement, runnable should be removed.
        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());

        assertEquals(1, mFakeStatsdLogger.getTransitionLogCount());
        FakeTimeZoneDetectorLogger.TransitionLog transitionLog =
                mFakeStatsdLogger.getTransitionLog(0);
        assertEquals(ZONE_A, transitionLog.previousTimeZoneId());
        assertEquals(ZONE_B, transitionLog.timeZoneId());
        assertEquals(telTime, transitionLog.telephonyTimeZoneChangedTimestamp());
        assertEquals(-1, transitionLog.geoTimeZoneChangedTimestamp());
        assertEquals(fusedTime, transitionLog.fusedTimeZoneChangedTimestamp());
        assertEquals(MCC, transitionLog.mcc());
        assertEquals(MNC, transitionLog.mnc());
        assertEquals(NITZ_OFFSET_SECONDS, transitionLog.nitzOffsetSeconds());
        assertEquals(NITZ_DST_OFFSET_SECONDS, transitionLog.nitzDstOffsetSeconds());
        assertEquals(true, transitionLog.transitionAborted());
        assertEquals(TimeZone.getTZDataVersion(), transitionLog.tzdbVersion());
        assertEquals(
                PRIMARY_LOCATION_TIME_ZONE_PROVIDER_UID,
                transitionLog.locationTimeZoneProviderUid());

        // Ensure no new runnable was posted after re-agreement
        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());
    }

    @Test
    public void flipFlop_telephonyTwiceThen_transitionAborted() {
        mTelemetry.onFusedTimeZoneChanged(ZONE_A);
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_A));
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_A));

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_B)); // A -> B
        assertEquals(1, mFakeEnvironment.getDelayedRunnableCount());

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_C)); // B -> C
        assertEquals(1, mFakeEnvironment.getDelayedRunnableCount());

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_A)); // C -> A
        long telTime = mFakeEnvironment.currentTimeMillis();
        // Re-agreement, runnable should be removed.
        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onFusedTimeZoneChanged(ZONE_A);

        assertEquals(1, mFakeStatsdLogger.getTransitionLogCount());
        FakeTimeZoneDetectorLogger.TransitionLog transitionLog =
                mFakeStatsdLogger.getTransitionLog(0);
        assertEquals(ZONE_A, transitionLog.previousTimeZoneId());
        assertEquals(ZONE_C, transitionLog.timeZoneId());
        assertEquals(telTime, transitionLog.telephonyTimeZoneChangedTimestamp());
        assertEquals(-1, transitionLog.geoTimeZoneChangedTimestamp());
        assertEquals(-1, transitionLog.fusedTimeZoneChangedTimestamp());
        assertEquals(MCC, transitionLog.mcc());
        assertEquals(MNC, transitionLog.mnc());
        assertEquals(NITZ_OFFSET_SECONDS, transitionLog.nitzOffsetSeconds());
        assertEquals(NITZ_DST_OFFSET_SECONDS, transitionLog.nitzDstOffsetSeconds());
        assertEquals(true, transitionLog.transitionAborted());
        assertEquals(TimeZone.getTZDataVersion(), transitionLog.tzdbVersion());
        assertEquals(
                PRIMARY_LOCATION_TIME_ZONE_PROVIDER_UID,
                transitionLog.locationTimeZoneProviderUid());
        // Ensure no new runnable was posted after re-agreement
        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());
    }

    @Test
    public void flipFlop_telephonyThenDifferentGeolocationThen_transitionAborted() {
        mTelemetry.onFusedTimeZoneChanged(ZONE_A);
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_A));
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_A));

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_B)); // A -> B
        assertEquals(1, mFakeEnvironment.getDelayedRunnableCount());

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_C)); // A -> C
        assertEquals(1, mFakeEnvironment.getDelayedRunnableCount());

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onTelephonyTimeZoneSuggestion(createTelephonySuggestion(ZONE_A)); // B -> A
        long telTime = mFakeEnvironment.currentTimeMillis();

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onGeolocationTimeZoneSuggestion(createGeoSuggestion(ZONE_A)); // C -> A
        long geoTime = mFakeEnvironment.currentTimeMillis();
        // Re-agreement, runnable should be removed.
        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());

        advanceTime(Duration.ofSeconds(1));
        mTelemetry.onFusedTimeZoneChanged(ZONE_A);

        assertEquals(1, mFakeStatsdLogger.getTransitionLogCount());
        FakeTimeZoneDetectorLogger.TransitionLog transitionLog =
                mFakeStatsdLogger.getTransitionLog(0);
        assertEquals(ZONE_A, transitionLog.previousTimeZoneId());
        assertEquals(ZONE_C, transitionLog.timeZoneId());
        assertEquals(telTime, transitionLog.telephonyTimeZoneChangedTimestamp());
        assertEquals(geoTime, transitionLog.geoTimeZoneChangedTimestamp());
        assertEquals(-1, transitionLog.fusedTimeZoneChangedTimestamp());
        assertEquals(MCC, transitionLog.mcc());
        assertEquals(MNC, transitionLog.mnc());
        assertEquals(NITZ_OFFSET_SECONDS, transitionLog.nitzOffsetSeconds());
        assertEquals(NITZ_DST_OFFSET_SECONDS, transitionLog.nitzDstOffsetSeconds());
        assertEquals(true, transitionLog.transitionAborted());
        assertEquals(TimeZone.getTZDataVersion(), transitionLog.tzdbVersion());
        assertEquals(
                PRIMARY_LOCATION_TIME_ZONE_PROVIDER_UID,
                transitionLog.locationTimeZoneProviderUid());

        assertEquals(0, mFakeEnvironment.getDelayedRunnableCount());
    }

    private void advanceTime(Duration duration) {
        mFakeEnvironment.advanceClock(duration.toMillis());
    }

    private TelephonyTimeZoneSuggestion createTelephonySuggestion(String zoneId) {
        return new TelephonyTimeZoneSuggestion.Builder(/* slotIndex= */ 0)
                .setZoneId(zoneId)
                .setTelephonySignal(
                        new TelephonySignal(
                                MCC,
                                MNC,
                                COUNTRY_ISO_CODE,
                                new HashSet<>(Arrays.asList(COUNTRY_ISO_CODE)),
                                new NitzSignal(
                                        /* receiptElapsedMillis= */ 0,
                                        /* ageMillis= */ 0,
                                        (int) Duration.ofSeconds(NITZ_OFFSET_SECONDS).toMillis(),
                                        (int)
                                                Duration.ofSeconds(NITZ_DST_OFFSET_SECONDS)
                                                        .toMillis(),
                                        /* currentTimeMillis= */ 0,
                                        /* emulatorHostTimeZone= */ null)))
                .setQuality(TelephonyTimeZoneSuggestion.QUALITY_SINGLE_ZONE)
                .setMatchType(TelephonyTimeZoneSuggestion.MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET)
                .build();
    }

    private GeolocationTimeZoneSuggestion createGeoSuggestion(String zoneId) {
        return GeolocationTimeZoneSuggestion.createCertainSuggestion(
                mFakeEnvironment.currentTimeMillis(), List.of(zoneId));
    }
}
