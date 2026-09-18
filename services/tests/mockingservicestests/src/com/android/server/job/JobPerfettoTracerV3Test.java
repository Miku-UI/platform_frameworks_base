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

package com.android.server.job;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import android.os.PerfettoCategories;

import com.android.internal.dev.perfetto.sdk.PerfettoTrackEventBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

public final class JobPerfettoTracerV3Test {
    private MockitoSession mMockingSession;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .mockStatic(com.android.internal.dev.perfetto.sdk.PerfettoTrace.class)
                .startMocking();
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void testV3Tracer() {
        final PerfettoTrackEventBuilder mockBuilderV3 =
                Mockito.mock(PerfettoTrackEventBuilder.class, Mockito.RETURNS_SELF);
        doReturn(mockBuilderV3)
                .when(() -> com.android.internal.dev.perfetto.sdk.PerfettoTrace.instant(
                        eq(PerfettoCategories.JOB_SCHEDULER_CATEGORY), anyString()));
        JobPerfettoTracer tracer = new JobPerfettoTracer.JobPerfettoTracerV3();

        tracer.startEvent("test_event_v3")
                .addField(1, 100)
                .addField(2, 200L)
                .emit();

        InOrder inOrder = Mockito.inOrder(mockBuilderV3);

        inOrder.verify(mockBuilderV3).beginProto();
        inOrder.verify(mockBuilderV3).beginNested(JobPerfettoTracer.JOB_SCHEDULER_JOB_FIELD_ID);
        inOrder.verify(mockBuilderV3).addField(1, 100L);
        inOrder.verify(mockBuilderV3).addField(2, 200L);
        inOrder.verify(mockBuilderV3).endNested();
        inOrder.verify(mockBuilderV3).endProto();
        inOrder.verify(mockBuilderV3).emit();
    }
}
