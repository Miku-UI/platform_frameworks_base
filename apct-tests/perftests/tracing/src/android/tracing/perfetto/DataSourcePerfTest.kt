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
package android.tracing.perfetto

import android.internal.perfetto.protos.TestEventOuterClass
import android.internal.perfetto.protos.TracePacketOuterClass
import android.os.Trace
import android.perftests.utils.BenchmarkState
import android.perftests.utils.PerfStatusReporter
import android.platform.test.annotations.Postsubmit
import android.tools.traces.busyWaitForDataSourceRegistration
import android.tools.traces.busyWaitTracingSessionDoesntExist
import android.tools.traces.busyWaitTracingSessionExists
import android.tools.traces.io.ResultWriter
import android.tools.traces.monitors.PerfettoTraceMonitor
import android.tools.traces.monitors.PerfettoTraceMonitor.Companion.newBuilder
import android.tools.traces.monitors.TraceMonitor
import android.util.Log
import android.util.proto.ProtoInputStream
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import perfetto.protos.PerfettoConfig

/** Performance tests for the Perfetto DataSource API. */
@RunWith(AndroidJUnit4::class)
@Postsubmit
class DataSourcePerfTest {
    @get:Rule val perfStatusReporter = PerfStatusReporter()

    // Simple DataSourceInstance for perf testing with state
    private class PerfDataSourceInstance(dataSource: DataSource<*, *, *>, instanceIndex: Int) :
        DataSourceInstance(dataSource, instanceIndex)

    // Simple DataSource for perf testing with state
    private class PerfDataSource(name: String) :
        DataSource<PerfDataSourceInstance, AtomicInteger, AtomicInteger>(name) {
        override fun createInstance(
            configStream: ProtoInputStream,
            instanceIndex: Int,
        ): PerfDataSourceInstance {
            return PerfDataSourceInstance(this, instanceIndex)
        }

        override fun createIncrementalState(
            args: CreateIncrementalStateArgs<PerfDataSourceInstance>
        ): AtomicInteger {
            return AtomicInteger(0)
        }

        override fun createTlsState(
            args: CreateTlsStateArgs<PerfDataSourceInstance>
        ): AtomicInteger {
            return AtomicInteger(0)
        }
    }

    private fun trace_NoOp() {
        val state = perfStatusReporter.benchmarkState
        val counter = AtomicInteger(0) // Ensure lambda isn't optimized away

        while (state.keepRunning()) {
            sPerfDataSource.trace {
                ctx: TracingContext<PerfDataSourceInstance, AtomicInteger, AtomicInteger> ->
                counter.incrementAndGet() // Minimal operation
            }
        }
    }

    @Test
    fun traceNoOpInactive() {
        trace_NoOp()
    }

    @Test
    fun traceNoOpActive() {
        runWithActiveTrace(1) { this.trace_NoOp() }
    }

    private fun trace_SimplePacket() {
        val state = perfStatusReporter.benchmarkState
        val value = AtomicInteger(0)

        while (state.keepRunning()) {
            val currentValue = value.incrementAndGet()
            sPerfDataSource.trace {
                ctx: TracingContext<PerfDataSourceInstance, AtomicInteger, AtomicInteger> ->
                val os = ctx.newTracePacket()
                val packetToken = os.start(TracePacketOuterClass.TracePacket.FOR_TESTING)
                val payloadToken = os.start(TestEventOuterClass.TestEvent.PAYLOAD)
                os.write(TestEventOuterClass.TestEvent.TestPayload.SINGLE_INT, currentValue)
                os.end(payloadToken)
                os.end(packetToken)
            }
        }
    }

    @Test
    fun traceSimplePacketInactive() {
        trace_SimplePacket()
    }

    @Test
    fun traceSimplePacketActive() {
        runWithActiveTrace(1) { this.trace_SimplePacket() }
    }

    @Test
    fun traceSimplePacketActiveMultiSession() {
        // NOTE: There is a maximum allowed tracing sessions per UID so we cannot exceed that.
        runWithActiveTrace(3) { this.trace_SimplePacket() }
    }

    @Test
    fun traceNewPacket() {
        runWithActiveTrace(1) {
            val state = perfStatusReporter.benchmarkState
            while (state.keepRunning()) {
                // Create packet object, but don't write
                sPerfDataSource.trace {
                    ctx: TracingContext<PerfDataSourceInstance, AtomicInteger, AtomicInteger> ->
                    ctx.newTracePacket()
                }
            }
        }
    }

    @Test
    fun traceMultiplePackets() {
        runWithActiveTrace(1) {
            val state = perfStatusReporter.benchmarkState
            val value = AtomicInteger(0)
            val packetsPerTraceCall = 5 // Write 5 packets per trace call
            while (state.keepRunning()) {
                val startValue = value.getAndAdd(packetsPerTraceCall)
                sPerfDataSource.trace {
                    ctx: TracingContext<PerfDataSourceInstance, AtomicInteger, AtomicInteger> ->
                    for (i in 0..<packetsPerTraceCall) {
                        val os = ctx.newTracePacket()
                        val packetToken = os.start(TracePacketOuterClass.TracePacket.FOR_TESTING)
                        val payloadToken = os.start(TestEventOuterClass.TestEvent.PAYLOAD)
                        os.write(
                            TestEventOuterClass.TestEvent.TestPayload.SINGLE_INT,
                            startValue + i,
                        )
                        os.end(payloadToken)
                        os.end(packetToken)
                    }
                }
            }
        }
    }

    @Test
    fun traceLargePacket() {
        runWithActiveTrace(1) {
            val state = perfStatusReporter.benchmarkState
            // Define a large string payload (approx 4KB)
            val largePayload = kotlin.text.String(CharArray(4 * 1024)).replace('\u0000', 'A')
            while (state.keepRunning()) {
                sPerfDataSource.trace {
                    ctx: TracingContext<PerfDataSourceInstance, AtomicInteger, AtomicInteger> ->
                    val os = ctx.newTracePacket()
                    val packetToken = os.start(TracePacketOuterClass.TracePacket.FOR_TESTING)
                    val payloadToken = os.start(TestEventOuterClass.TestEvent.PAYLOAD)
                    // Write the large string payload
                    os.write(TestEventOuterClass.TestEvent.TestPayload.SINGLE_STRING, largePayload)
                    os.end(payloadToken)
                    os.end(packetToken)
                }
            }
        }
    }

    @Test
    fun traceWithTraceSection() {
        runWithActiveTrace(1) {
            val state = perfStatusReporter.benchmarkState
            val value = AtomicInteger(0)
            val sectionName = "DataSourcePerfTest::TraceSection"
            while (state.keepRunning()) {
                val currentValue = value.incrementAndGet()
                sPerfDataSource.trace {
                    ctx: TracingContext<PerfDataSourceInstance, AtomicInteger, AtomicInteger> ->
                    Trace.beginSection(sectionName)
                    try {
                        val os = ctx.newTracePacket()
                        val packetToken = os.start(TracePacketOuterClass.TracePacket.FOR_TESTING)
                        val payloadToken = os.start(TestEventOuterClass.TestEvent.PAYLOAD)
                        os.write(TestEventOuterClass.TestEvent.TestPayload.SINGLE_INT, currentValue)
                        os.end(payloadToken)
                        os.end(packetToken)
                    } finally {
                        Trace.endSection()
                    }
                }
            }
        }
    }

    @Test
    @Throws(InterruptedException::class)
    fun traceConcurrentSimplePacket() {
        runWithActiveTrace(1) {
            val numThreads = 12
            val executor = Executors.newFixedThreadPool(numThreads)
            executor.use {
                try {
                    val latch = CountDownLatch(1)
                    val valueCounter = AtomicInteger(0)
                    val isFinished = AtomicBoolean(false)

                    val state = perfStatusReporter.benchmarkState
                    state.setCustomizedIterations(
                        1000,
                        object : BenchmarkState.CustomizedIterationListener {
                            override fun onStart(iteration: Int) {}

                            override fun onFinished(iteration: Int) {
                                isFinished.set(true)
                            }
                        },
                    )

                    // Start non-benchmarked tracing threads
                    for (t in 0..<numThreads - 1) {
                        executor.submit {
                            while (!isFinished.get()) {
                                val currentValue = valueCounter.incrementAndGet()
                                sPerfDataSource.trace {
                                    ctx:
                                        TracingContext<
                                            PerfDataSourceInstance,
                                            AtomicInteger,
                                            AtomicInteger,
                                        > ->
                                    val os = ctx.newTracePacket()
                                    val packetToken =
                                        os.start(TracePacketOuterClass.TracePacket.FOR_TESTING)
                                    val payloadToken =
                                        os.start(TestEventOuterClass.TestEvent.PAYLOAD)
                                    os.write(
                                        TestEventOuterClass.TestEvent.TestPayload.SINGLE_INT,
                                        currentValue,
                                    )
                                    os.end(payloadToken)
                                    os.end(packetToken)
                                }
                            }
                        }
                    }

                    // Start benchmarked tracing thread
                    executor.submit {
                        try {
                            val state = perfStatusReporter.benchmarkState

                            while (state.keepRunning()) {
                                val currentValue = valueCounter.incrementAndGet()
                                sPerfDataSource.trace {
                                    ctx:
                                        TracingContext<
                                            PerfDataSourceInstance,
                                            AtomicInteger,
                                            AtomicInteger,
                                        > ->
                                    val os = ctx.newTracePacket()
                                    val packetToken =
                                        os.start(TracePacketOuterClass.TracePacket.FOR_TESTING)
                                    val payloadToken =
                                        os.start(TestEventOuterClass.TestEvent.PAYLOAD)
                                    os.write(
                                        TestEventOuterClass.TestEvent.TestPayload.SINGLE_INT,
                                        currentValue,
                                    )
                                    os.end(payloadToken)
                                    os.end(packetToken)
                                }
                            }
                        } finally {
                            latch.countDown()
                        }
                    }

                    try {
                        latch.await(120, TimeUnit.SECONDS) // Wait for benchmark threads to finish
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    }
                } finally {
                    executor.shutdown()
                }
            }
        }
    }

    @Test
    fun traceTlsStateAccess() {
        runWithActiveTrace(1) {
            val state = perfStatusReporter.benchmarkState
            while (state.keepRunning()) {
                sPerfDataSource.trace {
                    ctx: TracingContext<PerfDataSourceInstance, AtomicInteger, AtomicInteger> ->
                    // Access and increment the thread-local counter
                    val tlsCounter = ctx.getCustomTlsState()
                    val currentValue = tlsCounter.incrementAndGet()

                    // Write the TLS value
                    val os = ctx.newTracePacket()
                    val packetToken = os.start(TracePacketOuterClass.TracePacket.FOR_TESTING)
                    val payloadToken = os.start(TestEventOuterClass.TestEvent.PAYLOAD)
                    os.write(
                        TestEventOuterClass.TestEvent.TestPayload.SINGLE_INT,
                        currentValue,
                    ) // Write the TLS counter value
                    os.end(payloadToken)
                    os.end(packetToken)
                }
            }
        }
    }

    @Test
    fun traceIncrementalStateAccess() {
        runWithActiveTrace(1) {
            val state = perfStatusReporter.benchmarkState
            while (state.keepRunning()) {
                sPerfDataSource.trace {
                    ctx: TracingContext<PerfDataSourceInstance, AtomicInteger, AtomicInteger> ->
                    // Access and increment the instance's incremental counter
                    val incrementalCounter = ctx.getIncrementalState()
                    val currentValue = incrementalCounter.incrementAndGet()

                    // Write the incremental value
                    val os = ctx.newTracePacket()
                    val packetToken = os.start(TracePacketOuterClass.TracePacket.FOR_TESTING)
                    val payloadToken = os.start(TestEventOuterClass.TestEvent.PAYLOAD)
                    os.write(
                        TestEventOuterClass.TestEvent.TestPayload.SINGLE_INT,
                        currentValue,
                    ) // Write the incremental counter value
                    os.end(payloadToken)
                    os.end(packetToken)
                }
            }
        }
    }

    @Test
    fun traceComplexPacket() {
        runWithActiveTrace(1) {
            val state = perfStatusReporter.benchmarkState
            val value = AtomicInteger(0)
            while (state.keepRunning()) {
                val currentValue = value.incrementAndGet()
                sPerfDataSource.trace(
                    TraceFunction {
                        ctx: TracingContext<PerfDataSourceInstance, AtomicInteger, AtomicInteger> ->
                        val os = ctx.newTracePacket()
                        val packetToken = os.start(TracePacketOuterClass.TracePacket.FOR_TESTING)
                        val payloadToken = os.start(TestEventOuterClass.TestEvent.PAYLOAD)

                        // Write nested message
                        val nestedToken = os.start(TestEventOuterClass.TestEvent.TestPayload.NESTED)
                        os.write(TestEventOuterClass.TestEvent.TestPayload.SINGLE_INT, currentValue)
                        os.end(nestedToken)

                        // Write repeated field
                        os.write(
                            TestEventOuterClass.TestEvent.TestPayload.REPEATED_INTS,
                            currentValue + 1,
                        )
                        os.write(
                            TestEventOuterClass.TestEvent.TestPayload.REPEATED_INTS,
                            currentValue + 2,
                        )
                        os.write(
                            TestEventOuterClass.TestEvent.TestPayload.REPEATED_INTS,
                            currentValue + 3,
                        )

                        os.end(payloadToken)
                        os.end(packetToken)
                    }
                )
            }
        }
    }

    /**
     * Helper method to run a performance test snippet while one or more Perfetto trace sessions are
     * actively collecting data from our data source.
     *
     * @param numInstances The number of concurrent trace sessions to start.
     * @param testLogic The core logic of the performance test to run.
     */
    private fun runWithActiveTrace(numInstances: Int, testLogic: Runnable) {
        val monitors = ArrayList<TraceMonitor>(numInstances)
        val writers = ArrayList<ResultWriter>(numInstances)
        val outputDirs = ArrayList<File>(numInstances)
        val sessionNames = ArrayList<String>(numInstances)

        try {
            // Setup monitors and writers
            for (i in 0..<numInstances) {
                val dir = tempDataSourceDir()
                val writer = createDummyWriter(dir)
                writers.add(writer)
                outputDirs.add(dir) // Store dir for cleanup
                val sessionName = "perf-test-" + UUID.randomUUID()
                sessionNames.add(sessionName)
                monitors.add(createActiveTraceMonitor(sessionName))
            }

            // Start all monitors
            for (monitor in monitors) {
                monitor.start()
            }

            // Wait for all tracing sessions to be active
            for (sessionName in sessionNames) {
                busyWaitTracingSessionExists(sessionName)
            }

            // Execute the actual performance test logic
            testLogic.run()
        } finally {
            // Stop all monitors
            for (i in monitors.indices) {
                try {
                    monitors[i].stop(writers[i])
                } catch (e: Exception) {
                    // Log error but continue stopping others
                    Log.e(LOG_TAG, "Error stopping trace monitor $i", e)
                }
            }
            // Wait for all tracing instances to be stopped
            for (sessionName in sessionNames) {
                busyWaitTracingSessionDoesntExist(sessionName)
            }

            // Clean up dummy directories
            for (dir in outputDirs) {
                if (dir.exists()) {
                    for (file in dir.listFiles() ?: error("Failed to get files in out dir")) {
                        file.delete()
                    }
                    dir.delete()
                }
            }
        }
    }

    private fun tempDataSourceDir(): File {
        val tempDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        return File(tempDir, "DataSourcePerfTest_Dummy_" + System.nanoTime())
    }

    // Helper to create a dummy ResultWriter
    private fun createDummyWriter(scenarioDir: File): ResultWriter {
        return ResultWriter()
            .withName("DataSourcePerfTest")
            .withOutputDir(scenarioDir)
            .setRunComplete()
    }

    // Helper to create a basic PerfettoTraceMonitor enabling our data source
    private fun createActiveTraceMonitor(sessionName: String): PerfettoTraceMonitor {
        return newBuilder()
            .setUniqueSessionName(sessionName)
            .enableCustomTrace(
                PerfettoConfig.DataSourceConfig.newBuilder()
                    .setName(
                        PERF_TEST_DATA_SOURCE_NAME
                    ) // Add minimal config if needed, e.g., buffer size
                    .build()
            )
            .build()
    }

    companion object {
        private const val LOG_TAG = "DataSourcePerfTest"

        private const val PERF_TEST_DATA_SOURCE_NAME = "perftest"
        private var sPerfDataSource = PerfDataSource(PERF_TEST_DATA_SOURCE_NAME)

        @BeforeClass
        @JvmStatic
        fun init() {
            Producer.init(InitArguments.DEFAULTS)
            sPerfDataSource.register(DataSourceParams.DEFAULTS)
            busyWaitForDataSourceRegistration(PERF_TEST_DATA_SOURCE_NAME)
        }
    }
}
