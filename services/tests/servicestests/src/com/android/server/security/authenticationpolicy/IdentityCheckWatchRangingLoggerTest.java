/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.security.authenticationpolicy;

import static com.android.server.security.authenticationpolicy.IdentityCheckWatchRangingLogger.ACTION_LOG_WATCH_RANGING_STATUS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.proximity.IProximityProviderService;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Presubmit
@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class IdentityCheckWatchRangingLoggerTest {
    private final TestableContext mTestableContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext(), null);
    private static final String PROXIMITY_PROVIDER_SERVICE_PACKAGE_CLASS = " ";

    @Rule
    public MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    private IProximityProviderService mProximityProviderService;
    @Mock
    private IBinder mBinder;
    @Mock
    private AlarmManager mAlarmManager;

    private TestableLooper mTestableLooper;
    private IdentityCheckWatchRangingLogger mIdentityCheckWatchRangingLogger;

    @Before
    public void setUp() throws Exception {
        mTestableLooper = TestableLooper.get(this);
        mTestableContext.addMockService(new ComponentName(PROXIMITY_PROVIDER_SERVICE_PACKAGE_CLASS,
                PROXIMITY_PROVIDER_SERVICE_PACKAGE_CLASS), mBinder);
        mTestableContext.addMockSystemService(AlarmManager.class, mAlarmManager);

        mIdentityCheckWatchRangingLogger = new IdentityCheckWatchRangingLogger(mTestableContext,
                PROXIMITY_PROVIDER_SERVICE_PACKAGE_CLASS, PROXIMITY_PROVIDER_SERVICE_PACKAGE_CLASS,
                new Handler(mTestableLooper.getLooper()), (binder) -> mProximityProviderService);
    }

    @Test
    public void testScheduleLogger() {
        final ArgumentCaptor<PendingIntent> pendingIntentArgumentCaptor = ArgumentCaptor.forClass(
                PendingIntent.class);

        mIdentityCheckWatchRangingLogger.scheduleLogger();

        verify(mAlarmManager).setRepeating(eq(AlarmManager.RTC_WAKEUP), anyLong(),
                eq(AlarmManager.INTERVAL_DAY), pendingIntentArgumentCaptor.capture());
        assertThat(pendingIntentArgumentCaptor.getValue().getIntent().getAction())
                .isEqualTo(ACTION_LOG_WATCH_RANGING_STATUS);
    }

    @Test
    public void testOnReceiveActionLogWatchRangingStatus_whenWatchRangingUnavailable()
            throws RemoteException, Settings.SettingNotFoundException {
        when(mProximityProviderService.isProximityCheckingSupported()).thenReturn(false);

        mIdentityCheckWatchRangingLogger.onReceive(mTestableContext,
                new Intent(ACTION_LOG_WATCH_RANGING_STATUS));
        waitForIdle();

        assertThat(isWatchRangingAvailable()).isFalse();
    }

    @Test
    public void testOnReceiveActionLogWatchRangingStatus_whenWatchRangingAvailable()
            throws RemoteException, Settings.SettingNotFoundException {
        when(mProximityProviderService.isProximityCheckingSupported()).thenReturn(true);

        mIdentityCheckWatchRangingLogger.onReceive(mTestableContext,
                new Intent(ACTION_LOG_WATCH_RANGING_STATUS));
        waitForIdle();

        assertThat(isWatchRangingAvailable()).isTrue();
    }

    private boolean isWatchRangingAvailable() throws Settings.SettingNotFoundException {
        return Settings.Global.getInt(mTestableContext.getContentResolver(),
                Settings.Global.WATCH_RANGING_AVAILABLE) == 1;
    }

    private void waitForIdle() {
        mTestableLooper.processAllMessages();
    }
}
