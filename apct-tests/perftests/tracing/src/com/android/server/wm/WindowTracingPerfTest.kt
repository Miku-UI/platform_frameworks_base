/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may in compliance with the License.
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
package com.android.server.wm

import android.perftests.utils.PerfStatusReporter
import android.platform.test.annotations.Postsubmit
import android.tools.traces.busyWaitTracingSessionDoesntExist
import android.tools.traces.busyWaitTracingSessionExists
import android.tools.traces.io.ResultWriter
import android.tools.traces.monitors.PerfettoTraceMonitor
import android.tools.traces.monitors.PerfettoTraceMonitor.Companion.newBuilder
import android.tracing.perfetto.InitArguments
import android.tracing.perfetto.Producer
import android.view.Choreographer
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
@Postsubmit
class WindowTracingPerfTest(private val mTracingState: TracingState) {

    @get:Rule val perfStatusReporter = PerfStatusReporter()

    enum class TracingState {
        ACTIVE,
        INACTIVE,
    }

    private var mPerfettoTracingMonitor: PerfettoTraceMonitor? = null

    @Before
    fun setUp() {
        if (mTracingState == TracingState.ACTIVE) {
            mPerfettoTracingMonitor =
                newBuilder()
                    .enableWindowManagerTrace()
                    .setUniqueSessionName(TRACE_SESSION_NAME)
                    .build()

            mPerfettoTracingMonitor?.start()
            busyWaitTracingSessionExists(TRACE_SESSION_NAME)
        }
    }

    @After
    fun tearDown() {
        if (mTracingState == TracingState.ACTIVE) {
            val dir: File = tempDataSourceDir()
            val writer: ResultWriter = createDummyWriter(dir)
            mPerfettoTracingMonitor?.stop(writer)
            busyWaitTracingSessionDoesntExist(TRACE_SESSION_NAME)
        }
    }

    @Test
    fun triggerUiTrace() {
        val state = perfStatusReporter.benchmarkState
        var choreographer: Choreographer? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            choreographer = Choreographer.getInstance()
        }

        while (state.keepRunning()) {
            val latch = CountDownLatch(1)
            choreographer!!.postFrameCallback { latch.countDown() }
            latch.await(1, TimeUnit.SECONDS)
        }
    }

    private fun tempDataSourceDir(): File {
        val tempDir = InstrumentationRegistry.getInstrumentation().context.cacheDir
        return File(tempDir, "UiTracingPerfTest_Dummy_" + System.nanoTime())
    }

    private fun createDummyWriter(scenarioDir: File): ResultWriter {
        return ResultWriter()
            .withName("UiTracingPerfTest")
            .withOutputDir(scenarioDir)
            .setRunComplete()
    }

    companion object {
        private const val TRACE_SESSION_NAME = "ui-tracing-perf-test"

        /** @return test parameters */
        @JvmStatic
        @Parameters(name = "tracing_{0}")
        fun params(): Collection<Array<Any>> {
            return TracingState.values().map { arrayOf(it) }
        }

        @JvmStatic
        @BeforeClass
        fun initOnce() {
            Producer.init(InitArguments.DEFAULTS)
        }
    }
}
