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

package com.android.server.signalcollector.binder;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.os.OutcomeReceiver;
import android.os.profiling.anomaly.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.os.profiling.anomaly.collector.SubscriptionId;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfig;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ANOMALY_DETECTOR_CORE)
public class BinderSpamDataCollectorTest {

    private static final String TEST_INTERFACE_1 = "com.test.TestInterface1";
    private static final String TEST_METHOD_1 = "testMethod1";
    private static final String TEST_INTERFACE_2 = "com.test.TestInterface2";
    private static final String TEST_METHOD_2 = "testMethod2";

    private static final BinderSpamConfig TEST_CONFIG_1 = new BinderSpamConfig.Builder()
            .setInterfaceName(TEST_INTERFACE_1)
            .setMethodName(TEST_METHOD_1)
            .build();
    private static final BinderSpamConfig TEST_CONFIG_2 = new BinderSpamConfig.Builder()
            .setInterfaceName(TEST_INTERFACE_2)
            .setMethodName(TEST_METHOD_2)
            .build();
    public static final BinderSpamData BINDER_SPAM_DATA =
            new BinderSpamData.Builder()
                    .setCallingUid(1000)
                    .setInterfaceName(TEST_INTERFACE_1)
                    .setMethodName(TEST_METHOD_1)
                    .setCallCount(50L)
                    .setTimespanMillis(1_000L)
                    .build();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    private OutcomeReceiver<BinderSpamData, Throwable> mReceiver;

    private BinderSpamSignalCollector mCollector;

    @Before
    public void setUp() {
        mCollector = new BinderSpamSignalCollector();
    }

    @Test
    public void onSignalReported_subscribedData_shouldInvokeReceiver() {
        mCollector.subscribe(TEST_CONFIG_1, mReceiver);

        mCollector.onBinderSpamDataReported(BINDER_SPAM_DATA);

        verify(mReceiver).onResult(BINDER_SPAM_DATA);
    }

    @Test
    public void onSignalReported_unsubscribedData_shouldNotInvokeReceiver() {
        SubscriptionId id = mCollector.subscribe(TEST_CONFIG_1, mReceiver);
        mCollector.unsubscribe(id);

        mCollector.onBinderSpamDataReported(BINDER_SPAM_DATA);

        verify(mReceiver, never()).onResult(any());
    }

    @Test
    public void onSignalReported_notSubscribedData_shouldNotInvokeReceiver() {
        mCollector.subscribe(TEST_CONFIG_2, mReceiver);

        mCollector.onBinderSpamDataReported(BINDER_SPAM_DATA);

        verify(mReceiver, never()).onResult(any());
    }

    @Test
    public void subscribe_shouldReturnDifferentSubscriptionId() {
        SubscriptionId id1 = mCollector.subscribe(TEST_CONFIG_1, result -> {});
        SubscriptionId id2 = mCollector.subscribe(TEST_CONFIG_2, result -> {});

        assertThat(id1).isNotNull();
        assertThat(id2).isNotNull();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    public void subscribe_nullInput_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> mCollector.subscribe(null, result -> {}));
        assertThrows(NullPointerException.class, () -> mCollector.subscribe(TEST_CONFIG_1, null));
        assertThrows(NullPointerException.class, () -> mCollector.subscribe(null, null));
    }

    @Test
    public void unsubscribe_nullInput_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> mCollector.unsubscribe(null));
    }

    @Test
    public void getData_throwUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> mCollector.getData(SubscriptionId.generateNew()));
    }

    @Test
    public void requestUpdate_throwUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> mCollector.requestUpdate(SubscriptionId.generateNew()));
    }

}
