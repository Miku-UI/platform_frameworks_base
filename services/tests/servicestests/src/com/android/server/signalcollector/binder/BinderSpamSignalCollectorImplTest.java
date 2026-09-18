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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.OutcomeReceiver;
import android.os.Process;
import android.os.binder.BinderCallsStats;
import android.os.binder.SingleSecondBinderStats;
import android.os.profiling.anomaly.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.os.profiling.anomaly.collector.SubscriptionId;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfig;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfigList;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ANOMALY_DETECTOR_CORE_C)
public class BinderSpamSignalCollectorImplTest {

    private static final String TEST_INTERFACE_1 = "com.test.TestInterface1";
    private static final String TEST_METHOD_1 = "testMethod1";
    private static final String TEST_INTERFACE_2 = "com.test.TestInterface2";
    private static final String TEST_METHOD_2 = "testMethod2";
    private static final BinderSpamConfig TEST_CONFIG_1 =
            new BinderSpamConfig.Builder()
                    .setInterfaceName(TEST_INTERFACE_1)
                    .setMethodName(TEST_METHOD_1)
                    .setUids(new int[0])
                    .setCallerImportanceList(new int[0])
                    .build();
    private static final BinderSpamConfig TEST_CONFIG_2 =
            new BinderSpamConfig.Builder()
                    .setInterfaceName(TEST_INTERFACE_2)
                    .setMethodName(TEST_METHOD_2)
                    .setUids(new int[0])
                    .setCallerImportanceList(new int[0])
                    .build();
    private static final BinderSpamConfig TEST_CONFIG_3 =
            new BinderSpamConfig.Builder()
                    .setInterfaceName(TEST_INTERFACE_2)
                    .setMethodName(TEST_METHOD_2)
                    .setUids(new int[0])
                    .setCallerImportanceList(new int[0])
                    .build();
    private static final BinderSpamConfigList TEST_CONFIG_LIST =
            new BinderSpamConfigList(List.of(TEST_CONFIG_1, TEST_CONFIG_2));
    public static final String NEVER_SUBSCRIBED_INTERFACE = "NeverSubscribedInterface";
    public static final String NEVER_SUBSCRIBED_METHOD = "NeverSubscribedMethod";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private OutcomeReceiver<BinderSpamData, Throwable> mReceiver;
    @Captor private ArgumentCaptor<BinderSpamData> mDataCaptor;

    private BinderSpamSignalCollectorImpl mCollector;

    @Before
    public void setUp() {
        mCollector = new BinderSpamSignalCollectorImpl();
    }

    @Test
    public void onBinderStatsReported_binderCalls_noSubscriptions() {
        mCollector.onBinderStatsReported(createCallsStatsArray());

        verify(mReceiver, never()).onResult(any());
    }

    @Test
    public void onBinderStatsReported_singleSecondBinderCalls_noSubscriptions() {
        mCollector.onBinderStatsReported(createSingleSecondBinderStatsArray());

        verify(mReceiver, never()).onResult(any());
    }

    @Test
    public void onBinderStatsReported_binderCalls_shouldInvokeSubscribedReceiver() {
        BinderCallsStats[] callsStatsArray = createCallsStatsArray();

        mCollector.subscribe(TEST_CONFIG_LIST, mReceiver);

        mCollector.onBinderStatsReported(callsStatsArray);
        verify(mReceiver, times(2)).onResult(mDataCaptor.capture());
        assertThat(mDataCaptor.getAllValues()).containsExactly(
                createBinderSpamData(callsStatsArray[0]),
                createBinderSpamData(callsStatsArray[1])
        );
        verify(mReceiver).onError(any());
    }

    @Test
    public void onBinderStatsReported_singleSecondBinderCalls_shouldInvokeSubscribedReceiver() {
        SingleSecondBinderStats[] singleSecondBinderStats = createSingleSecondBinderStatsArray();

        mCollector.subscribe(TEST_CONFIG_LIST, mReceiver);
        mCollector.onBinderStatsReported(singleSecondBinderStats);

        verify(mReceiver, times(2)).onResult(mDataCaptor.capture());
        assertThat(mDataCaptor.getAllValues()).containsExactly(
                createBinderSpamData(singleSecondBinderStats[0]),
                createBinderSpamData(singleSecondBinderStats[1]));
        verify(mReceiver).onError(any());
    }

    @Test
    public void onBinderStatsReported_binderCalls_shouldInvokeAllSubscribedReceivers() {
        BinderCallsStats[] callsStatsArray = createCallsStatsArray();

        OutcomeReceiver<BinderSpamData, Throwable> anotherReceiver = mock(OutcomeReceiver.class);

        mCollector.subscribe(TEST_CONFIG_LIST, mReceiver);
        mCollector.subscribe(new BinderSpamConfigList(List.of(TEST_CONFIG_3)), anotherReceiver);

        mCollector.onBinderStatsReported(callsStatsArray);
        verify(mReceiver, times(2)).onResult(mDataCaptor.capture());
        assertThat(mDataCaptor.getAllValues()).containsExactly(
                createBinderSpamData(callsStatsArray[0]),
                createBinderSpamData(callsStatsArray[1])
        );
        verify(anotherReceiver).onResult(createBinderSpamData(callsStatsArray[1]));
        verify(mReceiver).onError(any());
    }

    @Test
    public void onBinderStatsReported_singleSecondBinderStats_shouldInvokeAllSubscribedReceivers() {
        SingleSecondBinderStats[] singleSecondBinderStats = createSingleSecondBinderStatsArray();

        OutcomeReceiver<BinderSpamData, Throwable> anotherReceiver = mock(OutcomeReceiver.class);

        mCollector.subscribe(TEST_CONFIG_LIST, mReceiver);
        mCollector.subscribe(new BinderSpamConfigList(List.of(TEST_CONFIG_3)), anotherReceiver);

        mCollector.onBinderStatsReported(singleSecondBinderStats);
        verify(mReceiver, times(2)).onResult(mDataCaptor.capture());
        assertThat(mDataCaptor.getAllValues()).containsExactly(
                createBinderSpamData(singleSecondBinderStats[0]),
                createBinderSpamData(singleSecondBinderStats[1])
        );
        verify(anotherReceiver).onResult(createBinderSpamData(singleSecondBinderStats[1]));
        verify(mReceiver).onError(any());
    }

    @Test
    public void onBinderStatsReported_binderCalls_shouldNotInvokeUnsubscribedReceiver() {
        BinderCallsStats[] callsStatsArray = createCallsStatsArray();

        SubscriptionId id = mCollector.subscribe(TEST_CONFIG_LIST, mReceiver);
        mCollector.unsubscribe(id);

        mCollector.onBinderStatsReported(callsStatsArray);
        verify(mReceiver, never()).onResult(any());
    }

    @Test
    public void
            onBinderStatsReported_singleSecondBinderStats_shouldNotInvokeUnsubscribedReceiver() {
        SingleSecondBinderStats[] singleSecondBinderStats = createSingleSecondBinderStatsArray();

        SubscriptionId id = mCollector.subscribe(TEST_CONFIG_LIST, mReceiver);
        mCollector.unsubscribe(id);

        mCollector.onBinderStatsReported(singleSecondBinderStats);
        verify(mReceiver, never()).onResult(any());
    }

    @Test
    public void subscribe_shouldReturnDifferentSubscriptionId() {
        SubscriptionId id1 = mCollector.subscribe(TEST_CONFIG_LIST, result -> {});
        SubscriptionId id2 = mCollector.subscribe(
                new BinderSpamConfigList(List.of(TEST_CONFIG_1)),
                result -> {});

        assertThat(id1).isNotNull();
        assertThat(id2).isNotNull();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    public void subscribe_nullInput_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> mCollector.subscribe(null, result -> {}));
        assertThrows(NullPointerException.class,
                () -> mCollector.subscribe(TEST_CONFIG_LIST, null));
        assertThrows(NullPointerException.class, () -> mCollector.subscribe(null, null));
    }

    @Test
    public void unsubscribe_nullInput_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> mCollector.unsubscribe(null));
    }

    @Test
    public void unsubscribe_notFoundInput_shouldThrowException() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mCollector.unsubscribe(SubscriptionId.generateNew()));
        assertThat(e).hasMessageThat().contains("The provided subscription ID can not be found!");
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

    private static BinderCallsStats[] createCallsStatsArray() {
        return new BinderCallsStats[]{
                createBinderCallsStats(TEST_INTERFACE_1, TEST_METHOD_1, 1000, 200),
                createBinderCallsStats(TEST_INTERFACE_2, TEST_METHOD_2, 1001, 201),
                createBinderCallsStats(
                        NEVER_SUBSCRIBED_INTERFACE,
                        NEVER_SUBSCRIBED_METHOD, 1002, 202),
                // This is an invalid data entry, should be reported as error.
                createBinderCallsStats(TEST_INTERFACE_1, TEST_METHOD_1, 1000, 0),
        };
    }

    private static BinderCallsStats createBinderCallsStats(
            String interfaceDescriptor,
            String aidlMethod,
            int clientUid,
            int callCount) {
        BinderCallsStats binderCallsStats = new BinderCallsStats();
        binderCallsStats.interfaceDescriptor = interfaceDescriptor;
        binderCallsStats.aidlMethod = aidlMethod;
        binderCallsStats.clientUid = clientUid;
        binderCallsStats.callCount = callCount;
        return binderCallsStats;
    }

    private static SingleSecondBinderStats[] createSingleSecondBinderStatsArray() {
        return new SingleSecondBinderStats[]{
                createSingleSecondBinderStats(TEST_INTERFACE_1, TEST_METHOD_1, 1000, 200),
                createSingleSecondBinderStats(TEST_INTERFACE_2, TEST_METHOD_2, 1001, 201),
                createSingleSecondBinderStats(
                        NEVER_SUBSCRIBED_INTERFACE,
                        NEVER_SUBSCRIBED_METHOD, 1002, 202),
                // This is an invalid data entry. Should report as error.
                createSingleSecondBinderStats(TEST_INTERFACE_1, TEST_METHOD_1, 1000, 0)
        };
    }

    private static BinderSpamData createBinderSpamData(BinderCallsStats stats) {
        return new BinderSpamData.Builder()
                .setInterfaceName(stats.interfaceDescriptor)
                .setMethodName(stats.aidlMethod)
                .setCallingUid(stats.clientUid)
                .setServerUid(Process.myUid())
                .setCallCount((int) stats.callCount)
                .setTimespan(Duration.ofSeconds(5))
                .build();
    }

    private static SingleSecondBinderStats createSingleSecondBinderStats(
            String interfaceDescriptor,
            String aidlMethod,
            int clientUid,
            int callCount) {
        SingleSecondBinderStats singleSecondBinderStats = new SingleSecondBinderStats();
        singleSecondBinderStats.interfaceDescriptor = interfaceDescriptor;
        singleSecondBinderStats.aidlMethod = aidlMethod;
        singleSecondBinderStats.clientUid = clientUid;
        singleSecondBinderStats.callCount = callCount;
        return singleSecondBinderStats;
    }

    private static BinderSpamData createBinderSpamData(SingleSecondBinderStats stats) {
        return new BinderSpamData.Builder()
                .setInterfaceName(stats.interfaceDescriptor)
                .setMethodName(stats.aidlMethod)
                .setCallingUid(stats.clientUid)
                .setServerUid(Process.myUid())
                .setCallCount(stats.callCount)
                .setTimespan(Duration.ofSeconds(1))
                .build();
    }
}
