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
package com.android.server.wm

import android.os.IBinder
import android.perftests.utils.PerfStatusReporter
import android.platform.test.annotations.Postsubmit
import android.tools.traces.busyWaitTracingSessionDoesntExist
import android.tools.traces.busyWaitTracingSessionExists
import android.tools.traces.io.ResultWriter
import android.tools.traces.monitors.PerfettoTraceMonitor
import android.tools.traces.monitors.PerfettoTraceMonitor.Companion.newBuilder
import android.tracing.perfetto.InitArguments
import android.tracing.perfetto.Producer
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.android.wm.shell.transition.Transitions
import java.io.File
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
class WmShellTransitionPerfTest(private val mTracingState: TracingState?) {

    @get:Rule val perfStatusReporter = PerfStatusReporter()

    enum class TracingState {
        ACTIVE,
        INACTIVE,
    }

    private val mShellTracer = com.android.wm.shell.transition.tracing.PerfettoTransitionTracer()

    private var mPerfettoTracingMonitor: PerfettoTraceMonitor? = null

    @Before
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun setUp() {
        if (mTracingState == TracingState.ACTIVE) {
            mPerfettoTracingMonitor =
                newBuilder()
                    .setUniqueSessionName(TRACE_SESSION_NAME)
                    .enableTransitionsTrace()
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

    private fun tempDataSourceDir(): File {
        val tempDir = InstrumentationRegistry.getInstrumentation().context.cacheDir
        return File(tempDir, "DataSourcePerfTest_Dummy_" + System.nanoTime())
    }

    // Helper to create a dummy ResultWriter
    private fun createDummyWriter(scenarioDir: File): ResultWriter {
        return ResultWriter()
            .withName("DataSourcePerfTest")
            .withOutputDir(scenarioDir)
            .setRunComplete()
    }

    @Test
    fun logDispatched() {
        val state = perfStatusReporter.benchmarkState
        while (state.keepRunning()) {
            mShellTracer.logDispatched(TEST_TRANSITION_ID, sMockHandler)
        }
    }

    @Test
    fun logMergeRequested() {
        val state = perfStatusReporter.benchmarkState
        while (state.keepRunning()) {
            mShellTracer.logMergeRequested(TEST_TRANSITION_ID, TEST_OTHER_TRANSITION_ID)
        }
    }

    @Test
    fun logMerged() {
        val state = perfStatusReporter.benchmarkState
        while (state.keepRunning()) {
            mShellTracer.logMerged(TEST_TRANSITION_ID, TEST_OTHER_TRANSITION_ID)
        }
    }

    @Test
    fun logAborted() {
        val state = perfStatusReporter.benchmarkState
        while (state.keepRunning()) {
            mShellTracer.logAborted(TEST_TRANSITION_ID)
        }
    }

    private class MockTransitionHandler : Transitions.TransitionHandler {
        override fun startAnimation(
            transition: IBinder,
            info: TransitionInfo,
            startTransaction: SurfaceControl.Transaction,
            finishTransaction: SurfaceControl.Transaction,
            finishCallback: Transitions.TransitionFinishCallback,
        ): Boolean {
            return false
        }

        override fun handleRequest(
            transition: IBinder,
            request: TransitionRequestInfo,
        ): WindowContainerTransaction? {
            return null
        }
    }

    companion object {
        private const val TRACE_SESSION_NAME = "transition-trace-perf-test"

        /** @return test parameters */
        @JvmStatic
        @Parameters(name = "tracing_{0}")
        fun params(): MutableCollection<Array<Any?>?> {
            val params = ArrayList<Array<Any?>?>()
            for (state in TracingState.entries) {
                params.add(arrayOf(state))
            }
            return params
        }

        private var sMockHandler: Transitions.TransitionHandler? = null
        private const val TEST_TRANSITION_ID = 1
        private const val TEST_OTHER_TRANSITION_ID = 2

        @JvmStatic
        @BeforeClass
        fun initOnce() {
            Producer.init(InitArguments.DEFAULTS)
            sMockHandler = MockTransitionHandler()
        }
    }
}
