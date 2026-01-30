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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.os.Handler;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.Looper;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.proximity.IProximityProviderService;
import android.proximity.IProximityResultCallback;
import android.proximity.ProximityResultCode;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.R;

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
public class WatchRangingServiceTest {

    private static final long AUTHENTICATION_REQUEST_ID = 1;
    private static final String PROXIMITY_PROVIDER_SERVICE_COMPONENT_NAME = " ";

    private final TestableContext mTestableContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext(), null);

    @Rule
    public MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    private IProximityProviderService mProximityProviderService;
    @Mock
    private IProximityResultCallback mProximityResultCallback;
    @Mock
    private IBinder mBinder;
    @Mock
    private ICancellationSignal mCancellationSignal;
    @Mock
    private AlarmManager mAlarmManager;

    private TestableLooper mTestableLooper;
    private WatchRangingService mWatchRangingService;

    @Before
    public void setUp() throws Exception {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        mTestableLooper = TestableLooper.get(this);
        mTestableContext.getOrCreateTestableResources().addOverride(
                R.string.proximity_provider_service_package_name,
                PROXIMITY_PROVIDER_SERVICE_COMPONENT_NAME);
        mTestableContext.getOrCreateTestableResources().addOverride(R
                .string.proximity_provider_service_class_name,
                PROXIMITY_PROVIDER_SERVICE_COMPONENT_NAME);
        mTestableContext.addMockSystemService(AlarmManager.class, mAlarmManager);
        mTestableContext.addMockService(new ComponentName(PROXIMITY_PROVIDER_SERVICE_COMPONENT_NAME,
                        PROXIMITY_PROVIDER_SERVICE_COMPONENT_NAME), mBinder);

        mWatchRangingService = new WatchRangingService(mTestableContext,
                (binder) -> mProximityProviderService, new Handler(mTestableLooper.getLooper()));
    }

    @Test
    public void testScheduleDailyWatchStatusLogger() {
        final ArgumentCaptor<PendingIntent> pendingIntentArgumentCaptor = ArgumentCaptor.forClass(
                PendingIntent.class);

        verify(mAlarmManager).setRepeating(eq(AlarmManager.RTC_WAKEUP), anyLong(),
                eq(AlarmManager.INTERVAL_DAY), pendingIntentArgumentCaptor.capture());
        assertThat(pendingIntentArgumentCaptor.getValue().getIntent().getAction())
                .isEqualTo(ACTION_LOG_WATCH_RANGING_STATUS);
    }

    @Test
    public void testStartWatchRanging() throws RemoteException {
        mWatchRangingService.startWatchRangingForIdentityCheck(AUTHENTICATION_REQUEST_ID,
                mProximityResultCallback);
        waitForIdle();

        verify(mProximityProviderService).anyWatchNearby(any(), eq(mProximityResultCallback));
    }

    @Test
    public void testStartWatchRanging_thenCancelSuccessfully() throws RemoteException {
        when(mProximityProviderService.anyWatchNearby(any(), any())).thenReturn(
                mCancellationSignal);

        mWatchRangingService.startWatchRangingForIdentityCheck(AUTHENTICATION_REQUEST_ID,
                mProximityResultCallback);
        waitForIdle();

        verify(mProximityProviderService).anyWatchNearby(any(), eq(mProximityResultCallback));

        mWatchRangingService.cancelWatchRangingForRequestId(AUTHENTICATION_REQUEST_ID);
        waitForIdle();

        verify(mCancellationSignal).cancel();
    }

    @Test
    public void testStartWatchRanging_thenCancelUnsuccessfully() throws RemoteException {
        final int incorrectAuthenticationRequestId = 2;

        when(mProximityProviderService.anyWatchNearby(any(), any())).thenReturn(
                mCancellationSignal);

        mWatchRangingService.startWatchRangingForIdentityCheck(AUTHENTICATION_REQUEST_ID,
                mProximityResultCallback);
        waitForIdle();

        verify(mProximityProviderService).anyWatchNearby(any(), eq(mProximityResultCallback));

        mWatchRangingService.cancelWatchRangingForRequestId(incorrectAuthenticationRequestId);
        waitForIdle();

        verify(mCancellationSignal, never()).cancel();
    }

    @Test
    public void testStartWatchRanging_nullBinder_returnError() throws RemoteException {
        mTestableContext.addMockService(new ComponentName(PROXIMITY_PROVIDER_SERVICE_COMPONENT_NAME,
                PROXIMITY_PROVIDER_SERVICE_COMPONENT_NAME), null);

        mWatchRangingService.startWatchRangingForIdentityCheck(AUTHENTICATION_REQUEST_ID,
                mProximityResultCallback);
        waitForIdle();

        verify(mProximityResultCallback).onError(eq(ProximityResultCode.NO_ASSOCIATED_DEVICE));
    }

    @Test
    public void testStartWatchRanging_nullCancellationSignal_returnError() throws RemoteException {
        when(mProximityProviderService.anyWatchNearby(any(), any())).thenReturn(null);

        mWatchRangingService.startWatchRangingForIdentityCheck(AUTHENTICATION_REQUEST_ID,
                mProximityResultCallback);
        waitForIdle();

        //TestableContext calls onServiceDisconnected when the service is unbound. This causes the
        //error callback to be triggered twice.
        verify(mProximityResultCallback, times(2))
                .onError(eq(ProximityResultCode.NO_ASSOCIATED_DEVICE));
    }

    @Test
    public void isWatchRangingAvailable() throws RemoteException {
        when(mProximityProviderService.isProximityCheckingAvailable())
                .thenReturn(ProximityResultCode.PRIMARY_DEVICE_RANGING_NOT_SUPPORTED);

        mWatchRangingService.isWatchRangingAvailable(mProximityResultCallback);
        waitForIdle();

        verify(mProximityResultCallback).onError(
                eq(ProximityResultCode.PRIMARY_DEVICE_RANGING_NOT_SUPPORTED));
    }

    private void waitForIdle() {
        mTestableLooper.processAllMessages();
    }
}
