/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.os;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

/**
 * Test class for {@link DebugStore}.
 *
 * <p>To run it: atest FrameworksCoreTests:com.android.internal.os.DebugStoreTest
 */
@RunWith(AndroidJUnit4.class)
@DisabledOnRavenwood(blockedBy = DebugStore.class)
@SmallTest
public class DebugStoreTest {
    @Rule public final RavenwoodRule mRavenwood = new RavenwoodRule();

    @Mock private DebugStore.DebugStoreNative mDebugStoreNativeMock;

    @Captor private ArgumentCaptor<List<String>> mListCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        DebugStore.setDebugStoreNative(mDebugStoreNativeMock);
    }

    @Test
    public void testRecordScheduleServiceStart() {
        Intent intent = new Intent();
        intent.setAction("com.android.ACTION");
        intent.setComponent(new ComponentName("com.android", "androidService"));

        DebugStore.recordScheduleServiceStart(1, intent);
        assertThat(paramsForRecordEvent("SchSvcStart"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "act",
                        "com.android.ACTION",
                        "cmp",
                        "com.android/androidService",
                        "mid",
                        "1")
                .inOrder();
    }

    @Test
    public void testRecordScheduleServiceStart_withNullIntent() {
        DebugStore.recordScheduleServiceStart(1, null);
        assertThat(paramsForRecordEvent("SchSvcStart"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "act",
                        "null",
                        "cmp",
                        "null",
                        "mid",
                        "1")
                .inOrder();
    }

    @Test
    public void testRecordServiceStart() {
        when(mDebugStoreNativeMock.beginEvent(anyString(), anyList())).thenReturn(2L);

        long eventId = DebugStore.recordServiceStart(1);
        assertThat(paramsForBeginEvent("SvcStart")).containsExactly("mid", "1").inOrder();
        assertThat(eventId).isEqualTo(2L);
    }

    @Test
    public void testRecordScheduleServiceCreate() {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.name = "androidService";
        serviceInfo.packageName = "com.android";

        DebugStore.recordScheduleServiceCreate(1, serviceInfo);
        assertThat(paramsForRecordEvent("SchSvcCreate"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "name",
                        "androidService",
                        "mid",
                        "1")
                .inOrder();
    }

    @Test
    public void testRecordScheduleServiceCreate_withNullServiceInfo() {
        DebugStore.recordScheduleServiceCreate(1, null);
        assertThat(paramsForRecordEvent("SchSvcCreate"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "name",
                        "null",
                        "mid",
                        "1")
                .inOrder();
    }

    @Test
    public void testRecordServiceCreate() {
        when(mDebugStoreNativeMock.beginEvent(anyString(), anyList())).thenReturn(2L);

        long eventId = DebugStore.recordServiceCreate(1);
        assertThat(paramsForBeginEvent("SvcCreate")).containsExactly("mid", "1").inOrder();
        assertThat(eventId).isEqualTo(2L);
    }

    @Test
    public void testRecordScheduleServiceBind() {
        Intent intent = new Intent();
        intent.setAction("com.android.ACTION");
        intent.setComponent(new ComponentName("com.android", "androidService"));

        DebugStore.recordScheduleServiceBind(1, intent);
        assertThat(paramsForRecordEvent("SchSvcBind"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "act",
                        "com.android.ACTION",
                        "cmp",
                        "com.android/androidService",
                        "mid",
                        "1")
                .inOrder();
    }

    @Test
    public void testRecordScheduleServiceBind_withNullIntent() {
        DebugStore.recordScheduleServiceBind(1, null);
        assertThat(paramsForRecordEvent("SchSvcBind"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "act",
                        "null",
                        "cmp",
                        "null",
                        "mid",
                        "1")
                .inOrder();
    }

    @Test
    public void testRecordServiceBind() {
        when(mDebugStoreNativeMock.beginEvent(anyString(), anyList())).thenReturn(2L);

        long eventId = DebugStore.recordServiceBind(1);
        assertThat(paramsForBeginEvent("SvcBind")).containsExactly("mid", "1").inOrder();
        assertThat(eventId).isEqualTo(2L);
    }

    @Test
    public void testRecordScheduleBroadcastReceive() {
        Intent intent = new Intent();
        intent.setAction("com.android.ACTION");
        intent.setComponent(new ComponentName("com.android", "androidService"));

        DebugStore.recordScheduleBroadcastReceive(1, intent);
        assertThat(paramsForRecordEvent("SchRcv"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "act",
                        "com.android.ACTION",
                        "cmp",
                        "com.android/androidService",
                        "prid",
                        "1")
                .inOrder();
    }

    @Test
    public void testRecordScheduleBroadcastReceive_withNullIntent() {
        DebugStore.recordScheduleBroadcastReceive(1, null);
        assertThat(paramsForRecordEvent("SchRcv"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "act",
                        "null",
                        "cmp",
                        "null",
                        "prid",
                        "1")
                .inOrder();
    }

    @Test
    public void testRecordBroadcastReceive() {
        when(mDebugStoreNativeMock.beginEvent(anyString(), anyList())).thenReturn(2L);

        long eventId = DebugStore.recordBroadcastReceive(1, "com.android.Receiver");
        assertThat(paramsForBeginEvent("Rcv"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "cls",
                        "com.android.Receiver",
                        "prid",
                        "1")
                .inOrder();
        assertThat(eventId).isEqualTo(2L);
    }

    @Test
    public void testRecordScheduleBroadcastReceiveReg() {
        Intent intent = new Intent();
        intent.setAction("com.android.ACTION");
        intent.setComponent(new ComponentName("com.android", "androidService"));

        DebugStore.recordScheduleBroadcastReceiveReg(1, intent);
        assertThat(paramsForRecordEvent("SchRcvReg"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "act",
                        "com.android.ACTION",
                        "cmp",
                        "com.android/androidService",
                        "prid",
                        "1")
                .inOrder();
    }

    @Test
    public void testRecordScheduleBroadcastReceiveReg_withNullIntent() {
        DebugStore.recordScheduleBroadcastReceiveReg(1, null);
        assertThat(paramsForRecordEvent("SchRcvReg"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "act",
                        "null",
                        "cmp",
                        "null",
                        "prid",
                        "1")
                .inOrder();
    }

    @Test
    public void testRecordBroadcastReceiveReg() {
        when(mDebugStoreNativeMock.beginEvent(anyString(), anyList())).thenReturn(2L);

        long eventId = DebugStore.recordBroadcastReceiveReg(1, "com.android.Receiver");
        assertThat(paramsForBeginEvent("RcvReg"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "cls",
                        "com.android.Receiver",
                        "prid",
                        "1")
                .inOrder();
        assertThat(eventId).isEqualTo(2L);
    }

    @Test
    public void testRecordGoAsync() {
        DebugStore.recordGoAsync(3840 /* 0xf00 */);

        assertThat(paramsForRecordEvent("GoAsync"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "prid",
                        "f00")
                .inOrder();
    }

    @Test
    public void testRecordFinish() {
        DebugStore.recordFinish(3840 /* 0xf00 */);

        assertThat(paramsForRecordEvent("Finish"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "prid",
                        "f00")
                .inOrder();
    }

    @Test
    public void testRecordLongLooperMessage() {
        DebugStore.recordLongLooperMessage(100, "androidHandler", 500L);

        assertThat(paramsForRecordEvent("LooperMsg"))
                .containsExactly(
                        "code", "100",
                        "trgt", "androidHandler",
                        "elapsed", "500")
                .inOrder();
    }

    @Test
    public void testRecordLongLooperMessage_withNullTargetClass() {
        DebugStore.recordLongLooperMessage(200, null, 1000L);

        assertThat(paramsForRecordEvent("LooperMsg"))
                .containsExactly(
                        "code", "200",
                        "trgt", "null",
                        "elapsed", "1000")
                .inOrder();
    }

    @Test
    public void testRecordScheduleBindApplication() {
        when(mDebugStoreNativeMock.beginEvent(anyString(), anyList())).thenReturn(6L);
        DebugStore.recordScheduleBindApplication();

        assertThat(paramsForRecordEvent("SchBindApp")).isEmpty();
    }

    @Test
    public void testRecordBindApplication() {
        when(mDebugStoreNativeMock.beginEvent(anyString(), anyList())).thenReturn(6L);
        long eventId = DebugStore.recordBindApplication();

        assertThat(paramsForBeginEvent("BindApp")).isEmpty();
        assertThat(eventId).isEqualTo(6L);
    }

    @Test
    public void testRecordScheduleStartJob() {
        int jobId = 123;
        String jobNamespace = "com.android.job";
        DebugStore.recordScheduleStartJob(1, jobId, jobNamespace);
        assertThat(paramsForRecordEvent("SchJobStart"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "jobid",
                        "123",
                        "jobns",
                        "com.android.job",
                        "mid",
                        "1")
                .inOrder();
    }

    @Test
    public void testRecordStartJob() {
        DebugStore.recordStartJob(1);
        assertThat(paramsForBeginEvent("JobStart")).containsExactly("mid", "1").inOrder();
    }

    @Test
    public void testRecordScheduleStopJob() {
        int jobId = 123;
        String jobNamespace = "com.android.job";
        DebugStore.recordScheduleStopJob(1, jobId, jobNamespace);
        assertThat(paramsForRecordEvent("SchJobStop"))
                .containsExactly(
                        "tname",
                        Thread.currentThread().getName(),
                        "tid",
                        String.valueOf(Thread.currentThread().getId()),
                        "jobid",
                        "123",
                        "jobns",
                        "com.android.job",
                        "mid",
                        "1")
                .inOrder();
    }

    @Test
    public void testRecordStopJob() {
        DebugStore.recordStopJob(1);
        assertThat(paramsForBeginEvent("JobStop")).containsExactly("mid", "1").inOrder();
    }

    @Test
    public void testRecordEventEnd() {
        DebugStore.recordEventEnd(1L);

        verify(mDebugStoreNativeMock).endEvent(eq(1L), anyList());
    }

    private List<String> paramsForBeginEvent(String eventName) {
        verify(mDebugStoreNativeMock).beginEvent(eq(eventName), mListCaptor.capture());
        return mListCaptor.getValue();
    }

    private List<String> paramsForRecordEvent(String eventName) {
        verify(mDebugStoreNativeMock).recordEvent(eq(eventName), mListCaptor.capture());
        return mListCaptor.getValue();
    }
}
