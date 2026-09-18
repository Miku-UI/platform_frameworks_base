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
import android.os.binder.BinderCallsStats;
import android.os.binder.SingleSecondBinderStats;
import android.os.profiling.anomaly.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.os.profiling.anomaly.AnomalyDetectorManagerLocal;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfigList;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;
import com.android.server.LocalServices;
import com.android.server.signalcollector.SignalCollectorService.Injector;
import com.android.server.signalcollector.binder.BinderSpamSignalCollector;

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
@RequiresFlagsEnabled(Flags.FLAG_ANOMALY_DETECTOR_CORE_C)
public final class SignalCollectorServiceTest {

    public static final String TEST_INTERFACE_1 = "com.example.IFoo";
    public static final int TEST_UID_1 = 1000;
    public static final String TEST_METHOD_1 = "bar";
    public static final int TEST_CALL_COUNT_1 = 10;
    public static final int TEST_DURATION_COUNT_1 = 1;
    public static final int TEST_DURATION_SUM = 100;
    public static final int TEST_UID_2 = 1001;
    public static final String TEST_INTERFACE_2 = "com.example.IBar";
    public static final String TEST_METHOD_2 = "foo";
    public static final int TEST_CALL_COUNT_2 = 20;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    private static final int UID_UNKNOWN = 1234567890;
    private static final int UID_PERSISTENT_UI = 1234567891;
    private static final int UID_TOP = 1234567892;

    @Mock private Injector mInjector;
    @Mock private IActivityManager mActivityManager;
    @Mock private AnomalyDetectorManagerLocal mAnomalyDetectorManagerLocal;
    @Mock private BinderSpamSignalCollector mBinderSpamSignalCollector;

    @Captor private ArgumentCaptor<UidObserver> mUidObserverCaptor;

    private SignalCollectorService mSignalCollectorService;

    @Before
    public void setUp() {
        setUpInjector();
        mSignalCollectorService = new SignalCollectorService(
                ApplicationProvider.getApplicationContext(), mInjector);
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
                BinderSpamConfigList.class,
                BinderSpamData.class,
                mSignalCollectorService.mBinderSpamSignalCollector);
    }


    @Test
    public void onStart_publishesLocalService() {
        mSignalCollectorService.onStart();

        assertThat(LocalServices.getService(SignalCollectorManagerInternal.class))
                .isEqualTo(mSignalCollectorService.mInternal);
    }

    @Test
    public void reportBinderStatsToCollector_callStats() {
        mSignalCollectorService.onStart();

        BinderCallsStats[] stats = new BinderCallsStats[2];
        stats[0] = new BinderCallsStats();
        stats[0].clientUid = TEST_UID_1;
        stats[0].interfaceDescriptor = TEST_INTERFACE_1;
        stats[0].aidlMethod = TEST_METHOD_1;
        stats[0].callCount = TEST_CALL_COUNT_1;

        stats[1] = new BinderCallsStats();
        stats[1].clientUid = TEST_UID_2;
        stats[1].interfaceDescriptor = TEST_INTERFACE_2;
        stats[1].aidlMethod = TEST_METHOD_2;
        stats[1].callCount = TEST_CALL_COUNT_2;

        LocalServices.getService(SignalCollectorManagerInternal.class).reportBinderStats(stats);
        verify(mBinderSpamSignalCollector).onBinderStatsReported(stats);
    }

    @Test
    public void reportBinderStatsToCollector_singleSecondStats() {
        mSignalCollectorService.onStart();

        SingleSecondBinderStats[] stats = new SingleSecondBinderStats[2];
        stats[0] = new SingleSecondBinderStats();
        stats[0].clientUid = TEST_UID_1;
        stats[0].interfaceDescriptor = TEST_INTERFACE_1;
        stats[0].aidlMethod = TEST_METHOD_1;
        stats[0].callCount = TEST_CALL_COUNT_1;

        stats[1] = new SingleSecondBinderStats();
        stats[1].clientUid = TEST_UID_2;
        stats[1].interfaceDescriptor = TEST_INTERFACE_2;
        stats[1].aidlMethod = TEST_METHOD_2;
        stats[1].callCount = TEST_CALL_COUNT_2;

        LocalServices.getService(SignalCollectorManagerInternal.class).reportBinderStats(stats);
        verify(mBinderSpamSignalCollector).onBinderStatsReported(stats);
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

    private void setUpInjector() {
        when(mInjector.getActivityManager()).thenReturn(mActivityManager);
        when(mInjector.getAnomalyDetectorManagerLocal()).thenReturn(mAnomalyDetectorManagerLocal);
        when(mInjector.getBinderSpamSignalCollector()).thenReturn(mBinderSpamSignalCollector);
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
