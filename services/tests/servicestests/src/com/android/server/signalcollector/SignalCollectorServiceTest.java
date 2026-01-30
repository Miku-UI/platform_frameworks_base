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

package com.android.server.signalcollector;

import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT;
import static android.app.ActivityManager.PROCESS_STATE_TOP;
import static android.app.ActivityManager.PROCESS_STATE_UNKNOWN;
import static android.app.ActivityManager.UID_OBSERVER_GONE;
import static android.app.ActivityManager.UID_OBSERVER_PROCSTATE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.IActivityManager;
import android.app.UidObserver;
import android.os.RemoteException;
import android.os.profiling.anomaly.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.os.profiling.anomaly.AnomalyDetectorManagerLocal;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfig;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;
import com.android.server.LocalServices;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ANOMALY_DETECTOR_CORE)
public final class SignalCollectorServiceTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    private static final int UID_UNKNOWN = 1234567890;
    private static final int UID_PERSISTENT_UI = 1234567891;
    private static final int UID_TOP = 1234567892;

    @Mock private SignalCollectorService.Injector mInjector;
    @Mock private IActivityManager mActivityManager;
    @Mock private AnomalyDetectorManagerLocal mAnomalyDetectorManagerLocal;

    @Captor private ArgumentCaptor<UidObserver> mUidObserverCaptor;

    private SignalCollectorService mSignalCollectorService;
    private SignalCollectorManagerInternal mSignalCollectorManagerInternal;

    @Before
    public void setUp() {
        when(mInjector.getActivityManager()).thenReturn(mActivityManager);
        when(mInjector.getAnomalyDetectorManagerLocal()).thenReturn(mAnomalyDetectorManagerLocal);
        mSignalCollectorService = new SignalCollectorService(
                ApplicationProvider.getApplicationContext(), mInjector);
        mSignalCollectorManagerInternal = mSignalCollectorService.getInternal();
    }

    @After
    public void cleanUp() {
        LocalServices.removeAllServicesForTest();
    }

    @Test
    public void onStart_uidObserverRegisteredWithCorrectParameters() throws RemoteException {
        mSignalCollectorService.onStart();

        verify(mActivityManager).registerUidObserver(any(),
                eq(UID_OBSERVER_GONE | UID_OBSERVER_PROCSTATE),
                eq(PROCESS_STATE_UNKNOWN), eq(null));
    }

    @Test
    public void onStart_binderSpamSignalCollectorRegisteredWithCorrectParameters() {
        mSignalCollectorService.onStart();

        verify(mAnomalyDetectorManagerLocal).registerSignalCollector(
                BinderSpamConfig.class,
                BinderSpamData.class,
                mSignalCollectorManagerInternal.getBinderSpamSignalCollector());
    }


    @Test
    public void onStart_publishesLocalService() {
        mSignalCollectorService.onStart();

        assertThat(LocalServices.getService(SignalCollectorManagerInternal.class))
                .isEqualTo(mSignalCollectorManagerInternal);
    }

    @Test
    public void testGetProcessState_returnsUnknownWhenUidNotFound() {
        assertThat(mSignalCollectorService.getProcessState(UID_UNKNOWN))
                .isEqualTo(PROCESS_STATE_UNKNOWN);
    }

    @Test
    public void testGetProcessState_returnsCorrectProcessState() {
        UidObserver uidObserver = getUidObserver();
        uidObserver.onUidStateChanged(UID_PERSISTENT_UI, PROCESS_STATE_PERSISTENT, 0, 0);
        uidObserver.onUidStateChanged(UID_TOP, PROCESS_STATE_TOP, 0, 0);

        assertThat(mSignalCollectorService.getProcessState(UID_PERSISTENT_UI))
                .isEqualTo(PROCESS_STATE_PERSISTENT);
        assertThat(mSignalCollectorService.getProcessState(UID_TOP))
                .isEqualTo(PROCESS_STATE_TOP);
    }

    private UidObserver getUidObserver() {
        mSignalCollectorService.onStart();
        try {
            verify(mActivityManager).registerUidObserver(mUidObserverCaptor.capture(),
                    anyInt(), anyInt(), any());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        return mUidObserverCaptor.getValue();
    }
}
