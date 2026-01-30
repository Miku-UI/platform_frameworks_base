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

package com.android.server.wm;

import static android.tools.traces.Utils.busyWaitForDataSourceRegistration;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static java.io.File.createTempFile;
import static java.nio.file.Files.createTempDirectory;

import android.platform.test.annotations.Presubmit;
import android.tools.traces.io.ResultWriter;
import android.tools.traces.monitors.PerfettoTraceMonitor;
import android.util.Log;
import android.view.Choreographer;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;
import androidx.test.uiautomator.UiDevice;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;

import perfetto.protos.PerfettoConfig.WindowManagerConfig.LogFrequency;

import java.io.IOException;

/**
 * Test class for {@link WindowTracingPerfetto}.
 */
@FlakyTest(bugId = 372558379)
@SmallTest
@Presubmit
public class WindowTracingPerfettoTest {
    private static final String TEST_DATA_SOURCE_NAME = "android.windowmanager.test";

    private static WindowManagerService sWmMock;
    private static Choreographer sChoreographerMock;
    @Captor
    ArgumentCaptor<Choreographer.FrameCallback> mFrameCallbackCaptor =
            ArgumentCaptor.forClass(Choreographer.FrameCallback.class);
    private static WindowTracing sWindowTracing;
    private static Boolean sIsDataSourceRegisteredSuccessfully;

    private PerfettoTraceMonitor mTraceMonitor;

    @BeforeClass
    public static void setUpOnce() throws Exception {
        sWmMock = Mockito.mock(WindowManagerService.class);
        sChoreographerMock = Mockito.mock(Choreographer.class);
        Mockito.doNothing().when(sWmMock).dumpDebugLocked(Mockito.any(), Mockito.anyInt());
        sWindowTracing = new WindowTracingPerfetto(sWmMock, sChoreographerMock,
                new WindowManagerGlobalLock(), TEST_DATA_SOURCE_NAME);
    }

    @AfterClass
    public static void tearDownOnce() {
        sWmMock = null;
        sWindowTracing = null;
    }

    @Before
    public void setUp() throws IOException {
        if (sIsDataSourceRegisteredSuccessfully != null) {
            assumeTrue("Failed to register data source", sIsDataSourceRegisteredSuccessfully);
            return;
        }
        try {
            busyWaitForDataSourceRegistration(TEST_DATA_SOURCE_NAME);
            sIsDataSourceRegisteredSuccessfully = true;
        } catch (Exception e) {
            sIsDataSourceRegisteredSuccessfully = false;
            final String perfettoStatus = UiDevice.getInstance(getInstrumentation())
                    .executeShellCommand("perfetto --query");
            Log.e(WindowTracingPerfettoTest.class.getSimpleName(),
                    "Failed to register data source: " + perfettoStatus);
            // Only fail once. The rest tests will be skipped by assumeTrue.
            fail("Failed to register data source");
        }
    }

    @After
    public void tearDown() throws IOException {
        Mockito.clearInvocations(sWmMock);
        stopTracing();
    }

    @Test
    public void isEnabled_returnsFalseByDefault() {
        assertFalse(sWindowTracing.isEnabled());
    }

    @Test
    public void isEnabled_returnsTrueAfterStartThenFalseAfterStop() throws IOException {
        startTracing(LogFrequency.LOG_FREQUENCY_TRANSACTION);
        assertTrue(sWindowTracing.isEnabled());

        stopTracing();
        assertFalse(sWindowTracing.isEnabled());
    }

    @Test
    public void trace_ignoresLogStateCalls_ifTracingIsDisabled() {
        sWindowTracing.logState("where");
        verifyNoMoreInteractions(sWmMock);
    }

    @Test
    public void trace_writesInitialStateSnapshot_whenTracingStarts() {
        startTracing(LogFrequency.LOG_FREQUENCY_TRANSACTION);
        verify(sWmMock, times(1)).dumpDebugLocked(any(), eq(WindowTracingLogLevel.ALL));
    }

    @Test
    public void trace_writesStateSnapshot_onLogStateCall() {
        startTracing(LogFrequency.LOG_FREQUENCY_TRANSACTION);
        sWindowTracing.logState("where");
        verify(sWmMock, times(2)).dumpDebugLocked(any(), eq(WindowTracingLogLevel.ALL));
    }

    @Test
    // This test case covers the race condition from b/408175513
    public void trace_FrameCallbackRaceCondition() throws IOException {
        Mockito.doNothing().when(sChoreographerMock)
                .postFrameCallback(mFrameCallbackCaptor.capture());

        startTracing(LogFrequency.LOG_FREQUENCY_FRAME);

        // Schedule snapshot (Choreographer#postFrameCallback)
        sWindowTracing.logState("where");
        verify(sChoreographerMock, times(1)).postFrameCallback(Mockito.any());

        // Stop tracing
        stopTracing();

        // Execute snapshot callback (but after tracing stopped)
        mFrameCallbackCaptor.getValue().doFrame(0);

        // Start tracing
        startTracing(LogFrequency.LOG_FREQUENCY_FRAME);

        // Schedule snapshot (Choreographer#postFrameCallback),
        // Expect scheduling even if previous snapshot callback executed after tracing stop.
        sWindowTracing.logState("where");
        verify(sChoreographerMock, times(2)).postFrameCallback(Mockito.any());
    }

    @Test
    public void dump_writesOneSingleStateSnapshot() {
        startTracing(LogFrequency.LOG_FREQUENCY_SINGLE_DUMP);
        sWindowTracing.logState("where");
        verify(sWmMock, times(1)).dumpDebugLocked(any(), eq(WindowTracingLogLevel.ALL));
    }

    private void startTracing(LogFrequency logFrequency) {
        if (logFrequency == LogFrequency.LOG_FREQUENCY_SINGLE_DUMP) {
            mTraceMonitor = PerfettoTraceMonitor
                    .newBuilder()
                    .enableWindowManagerDump(TEST_DATA_SOURCE_NAME)
                    .build();
        } else {
            mTraceMonitor = PerfettoTraceMonitor
                    .newBuilder()
                    .enableWindowManagerTrace(logFrequency, TEST_DATA_SOURCE_NAME)
                    .build();
        }
        mTraceMonitor.start();
    }

    private void stopTracing() throws IOException {
        if (mTraceMonitor == null || !mTraceMonitor.isEnabled()) {
            return;
        }

        ResultWriter writer = new ResultWriter()
                .withName(createTempFile("temp", "").getName())
                .withOutputDir(createTempDirectory("temp").toFile())
                .setRunComplete();

        mTraceMonitor.stop(writer);
    }
}
