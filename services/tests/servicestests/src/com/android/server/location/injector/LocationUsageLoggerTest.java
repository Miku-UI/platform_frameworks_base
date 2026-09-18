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

package com.android.server.location.injector;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.location.injector.LocationUsageLogger.GRACE_PERIOD_MILLIS;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.flags.Flags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.FrameworkStatsLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link LocationUsageLogger}.
 */
@RunWith(AndroidJUnit4.class)
public class LocationUsageLoggerTest {

    private static final String PACKAGE_NAME = "com.android.test.app";
    private static final String ATTRIBUTION_TAG = "test_attribution";
    private static final int UID = 10001;
    private static final String OP_CODE = AppOpsManager.OPSTR_FINE_LOCATION;

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private Context mContext;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private LocationUsageLogger.MockableSystemClock mSystemClock;
    @Mock
    private LocationUsageLogger.MockableStatsLog mStatsLog;

    private LocationUsageLogger mLogger;

    @Before
    public void setUp() {
        mSetFlagsRule.enableFlags(Flags.FLAG_LOCATION_AUDITING);
        MockitoAnnotations.openMocks(this);
        when(mContext.getSystemServiceName(ActivityManager.class)).thenReturn(
                Context.ACTIVITY_SERVICE);
        when(mContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(mActivityManager);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        // Default clock start
        when(mSystemClock.elapsedRealtime()).thenReturn(0L);

        mLogger = new LocationUsageLogger(mContext, mSystemClock, mStatsLog);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testLogLocationOpNoted_NoViolation_MlsEnabled() {
        mLogger.logLocationEnabledStateChanged(true);

        long eventTime = GRACE_PERIOD_MILLIS + 500_000L;
        when(mSystemClock.elapsedRealtime()).thenReturn(eventTime);

        mLogger.logLocationOpNoted(OP_CODE, UID, PACKAGE_NAME, ATTRIBUTION_TAG, 0,
                AppOpsManager.MODE_ALLOWED);

        // Advance time to ensure no maturation triggers a log
        eventTime += GRACE_PERIOD_MILLIS + 500_000L;
        when(mSystemClock.elapsedRealtime()).thenReturn(eventTime);
        mLogger.logLocationEnabledStateChanged(false);

        verify(mStatsLog, never()).write(
                eq(FrameworkStatsLog.LOCATION_MLS_VIOLATION_OBSERVED),
                anyString(), anyString(), anyString(), anyInt(), anyInt(),
                anyBoolean(), anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyInt(), anyBoolean());
    }

    @Test
    public void testLogLocationOpNoted_ViolationDetected_LoggedAfterGrace() {
        mLogger.logLocationEnabledStateChanged(false);
        when(mActivityManager.getUidProcessState(UID)).thenReturn(
                ActivityManager.PROCESS_STATE_TOP);

        // Event outside of the "before" grace period
        long eventTime = GRACE_PERIOD_MILLIS + 100_000L;
        when(mSystemClock.elapsedRealtime()).thenReturn(eventTime);

        mLogger.logLocationOpNoted(OP_CODE, UID, PACKAGE_NAME, ATTRIBUTION_TAG, 0,
                AppOpsManager.MODE_ALLOWED);

        // Verify it is NOT logged immediately (the "after" grace period has not passed)
        verify(mStatsLog, never()).write(eq(FrameworkStatsLog.LOCATION_MLS_VIOLATION_OBSERVED),
                anyString(), anyString(), anyString(), anyInt(), anyInt(), anyBoolean(), anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(),
                anyBoolean());

        // Triggers a log
        when(mSystemClock.elapsedRealtime()).thenReturn(eventTime + GRACE_PERIOD_MILLIS + 1);
        mLogger.logLocationEnabledStateChanged(true);

        // Logged inGraceWindow=false
        verify(mStatsLog).write(
                eq(FrameworkStatsLog.LOCATION_MLS_VIOLATION_OBSERVED),
                eq(OP_CODE), eq(PACKAGE_NAME), eq(ATTRIBUTION_TAG), eq(0),
                eq(AppOpsManager.MODE_ALLOWED),
                eq(true) /* isForeground */, anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), eq(GRACE_PERIOD_MILLIS), eq(false) /* inGraceWindow */);
    }

    @Test
    public void testGracePeriod_BeforeWindow_MarkedInGrace() {
        // MLS turns ON at 0s
        mLogger.logLocationEnabledStateChanged(true);
        // MLS turns OFF at 90s
        when(mSystemClock.elapsedRealtime()).thenReturn(90_000L);
        mLogger.logLocationEnabledStateChanged(false);

        // Event within the "before" grace window
        long eventTime = 90_000L + GRACE_PERIOD_MILLIS - 1;
        when(mSystemClock.elapsedRealtime()).thenReturn(eventTime);
        mLogger.logLocationOpNoted(OP_CODE, UID, PACKAGE_NAME, ATTRIBUTION_TAG, 0,
                AppOpsManager.MODE_ALLOWED);

        // Pass the "after" grace window
        when(mSystemClock.elapsedRealtime()).thenReturn(eventTime + GRACE_PERIOD_MILLIS + 1);
        mLogger.logLocationEnabledStateChanged(true);

        // The event was logged with inGraceWindow=true
        verify(mStatsLog).write(
                eq(FrameworkStatsLog.LOCATION_MLS_VIOLATION_OBSERVED),
                anyString(), anyString(), anyString(), anyInt(), anyInt(), anyBoolean(), anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
                eq(GRACE_PERIOD_MILLIS), eq(true) /* inGraceWindow */);
    }

    @Test
    public void testGracePeriod_AfterWindow_MarkedInGrace() {
        mLogger.logLocationEnabledStateChanged(false);

        // Event outside of the "before" grace period
        long eventTime = GRACE_PERIOD_MILLIS + 100_000L;
        when(mSystemClock.elapsedRealtime()).thenReturn(eventTime);
        mLogger.logLocationOpNoted(OP_CODE, UID, PACKAGE_NAME, ATTRIBUTION_TAG, 0,
                AppOpsManager.MODE_ALLOWED);

        // MLS turns ON within the "after" grace period
        when(mSystemClock.elapsedRealtime()).thenReturn(eventTime + GRACE_PERIOD_MILLIS - 1);
        mLogger.logLocationEnabledStateChanged(true);

        // Triggers a log
        when(mSystemClock.elapsedRealtime()).thenReturn(eventTime + GRACE_PERIOD_MILLIS + 1);
        mLogger.logLocationEnabledStateChanged(false);

        verify(mStatsLog).write(
                eq(FrameworkStatsLog.LOCATION_MLS_VIOLATION_OBSERVED),
                anyString(), anyString(), anyString(), anyInt(), anyInt(), anyBoolean(), anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
                eq(GRACE_PERIOD_MILLIS), eq(true) /* inGraceWindow */);
    }

}
