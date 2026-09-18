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
package com.android.app.concurrent.benchmark.base

import android.os.Handler
import android.util.Log
import androidx.benchmark.BenchmarkState
import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.MicrobenchmarkConfig
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.PerfettoTraceRule
import androidx.benchmark.macro.runSingleSessionServer
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.perfetto.PerfettoConfig
import androidx.benchmark.traceprocessor.PerfettoTrace
import androidx.benchmark.traceprocessor.Row
import androidx.benchmark.traceprocessor.TraceProcessor
import com.android.app.concurrent.benchmark.util.BG_THREAD_NAME_PREFIX
import com.android.app.concurrent.benchmark.util.BgExceptionHandler
import com.android.app.concurrent.benchmark.util.DEBUG
import com.android.app.concurrent.benchmark.util.LooperThreadWithHandlerBuilder
import com.android.app.concurrent.benchmark.util.PERFETTO_CONFIG
import com.android.app.concurrent.benchmark.util.PERFETTO_SQL_QUERY_FORMAT_STR
import com.android.app.concurrent.benchmark.util.PerfettoMeasurementTimelineMetricCollector
import com.android.app.concurrent.benchmark.util.TAG
import com.android.app.concurrent.benchmark.util.ThreadBuilder
import com.android.app.concurrent.benchmark.util.dbg
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

abstract class BaseConcurrentBenchmark {
    @get:Rule val benchmarkRule = ConcurrentBenchmarkRule()
}

abstract class BaseSchedulerBenchmark<T : Any>(param: ThreadBuilder<out T>) {
    val schedulerRule = SchedulerBenchmarkRule(param)
    val benchmarkRule = ConcurrentBenchmarkRule()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(benchmarkRule).around(schedulerRule)

    val scheduler: T
        get() = schedulerRule.scheduler
}

abstract class BaseLooperThreadBenchmark() {
    val handlerThreadRule = SchedulerBenchmarkRule(LooperThreadWithHandlerBuilder)
    val benchmarkRule = ConcurrentBenchmarkRule()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(benchmarkRule).around(handlerThreadRule)

    interface SingleThreadContext {
        val state: BenchmarkState
    }

    interface HandlerThreadContext : SingleThreadContext {
        val handler: Handler
    }

    interface SynchronousContext {
        fun stopBenchmark()
    }

    interface HandlerSynchronousContext : HandlerThreadContext, SynchronousContext

    protected open class HandlerMonitorImpl(
        override val state: BenchmarkState,
        override val handler: Handler,
    ) : HandlerSynchronousContext {
        val latch = CountDownLatch(1)

        fun waitForBenchmarkCompletion() {
            dbg { "waitForBenchmarkCompletion" }
            latch.await()
        }

        override fun stopBenchmark() {
            dbg { "stopBenchmark" }
            latch.countDown()
        }
    }

    /**
     * Posts [initialBlock] to the bg handler, running until
     * [stopBenchmark][HandlerSynchronousContext.stopBenchmark] is called.
     */
    fun measure(initialBlock: HandlerSynchronousContext.() -> Unit) {
        val handlerMonitor =
            HandlerMonitorImpl(benchmarkRule.benchmarkRule.getState(), handlerThreadRule.scheduler)
        handlerMonitor.handler.post {
            dbg { "startBenchmark" }
            handlerMonitor.initialBlock()
        }
        handlerMonitor.waitForBenchmarkCompletion()
    }
}

abstract class CoroutineLooperThreadBenchmark() : BaseLooperThreadBenchmark() {

    interface CoroutineScopeContext : SingleThreadContext, HandlerThreadContext {
        suspend fun stopBenchmark()
    }

    protected open class CoroutineScopeContextImpl(
        override val state: BenchmarkState,
        override val handler: Handler,
        val job: Job,
    ) : CoroutineScopeContext {

        override suspend fun stopBenchmark() {
            dbg { "stopBenchmark, cancelling scope" }
            job.cancelAndJoin()
        }
    }

    fun measureCoroutine(initialBlock: CoroutineScopeContext.(CoroutineScope) -> Unit) {
        val job = Job()
        val handler = handlerThreadRule.scheduler
        dbg { "startBenchmark" }
        val scope = CoroutineScope(handler.asCoroutineDispatcher() + job)
        CoroutineScopeContextImpl(benchmarkRule.benchmarkRule.getState(), handler, job)
            .initialBlock(scope)
        runBlocking { job.join() }
    }
}

class SchedulerBenchmarkRule<S : Any>(val threadBuilder: ThreadBuilder<S>) : TestRule {
    lateinit var scheduler: S
        private set

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                dbg { "SchedulerBenchmarkRule START $description" }
                threadBuilder.startThread().use { handle ->
                    scheduler = handle.scheduler
                    base.evaluate()
                }
                dbg { "SchedulerBenchmarkRule END $description" }
            }
        }
    }
}

class ConcurrentBenchmarkRule() : TestRule {

    @OptIn(ExperimentalBenchmarkConfigApi::class)
    val benchmarkRule = BenchmarkRule(config = MicrobenchmarkConfig())

    @OptIn(ExperimentalPerfettoCaptureApi::class)
    override fun apply(base: Statement, description: Description): Statement {
        val traceCallback: ((PerfettoTrace) -> Unit) = { trace ->
            TraceProcessor.runSingleSessionServer(trace.path) {
                if (DEBUG) return@runSingleSessionServer
                dbg { "Running Perfetto SQL query" }
                val rowSequence =
                    query(String.format(PERFETTO_SQL_QUERY_FORMAT_STR, getCurrentBgThreadName()))
                dbg { "Query completed" }
                val row = rowSequence.firstOrNull()
                if (row != null) {
                    var allMetricsValid = true
                    row.keys.forEach { key ->
                        dbg { "putValueFromRow where key=$key" }
                        allMetricsValid = putValueFromRow(row, key) && allMetricsValid
                    }
                    clearBgThreadName()
                    if (!allMetricsValid) {
                        error(
                            "Trace has data loss or other errors. For more details, " +
                                "open the trace in Perfetto and view its info and stats."
                        )
                    }
                } else {
                    Log.e(TAG, "No row found for query")
                }
            }
        }
        return RuleChain.outerRule(::applyInternal)
            .around(benchmarkRule)
            .around(
                PerfettoTraceRule(
                    config = PerfettoConfig.Text(PERFETTO_CONFIG),
                    enableUserspaceTracing = false,
                    labelProvider = {
                        "${it.className.substringAfterLast('.')}_${it.methodName.substringBefore('[')}"
                    },
                    traceCallback = traceCallback,
                )
            )
            .apply(base, description)
    }

    private fun applyInternal(base: Statement, description: Description) =
        object : Statement() {
            override fun evaluate() {
                dbg { "ConcurrentBenchmarkRule START $description" }
                setBgThreadName()
                try {
                    base.evaluate()
                } finally {
                    clearBgThreadName()
                    dbg { "ConcurrentBenchmarkRule END $description" }
                }
            }
        }

    fun runBenchmark(build: ConcurrentBenchmarkDsl.() -> Unit) {
        ConcurrentBenchmarkDsl(build).measure(this)
    }

    fun measureRepeated(
        beforeFirstIteration: () -> Unit,
        onEachIteration: (n: Int) -> Unit,
        afterLastIteration: () -> Unit,
    ) {
        BgExceptionHandler.beginMonitoring()
        try {
            dbg { "measureRepeated" }
            var n = 0
            beforeFirstIteration()
            // No need to set THREAD_PRIORITY_MOST_FAVORABLE here; it is already set by the
            // benchmark initialization in AndroidX
            val state = benchmarkRule.getState()
            while (state.keepRunning()) {
                if (Thread.interrupted()) throw InterruptedException()
                n++
                dbg { ">>>> onEachIteration >>>> n=$n " }
                onEachIteration(n)
                dbg { "<<<< onEachIteration <<<<" }
            }
            afterLastIteration()
        } catch (e: InterruptedException) {
            BgExceptionHandler.rethrowInterruptWithCause(e)
        } finally {
            BgExceptionHandler.endMonitoring()
        }
    }
}

private lateinit var bgThreadName: String

fun setBgThreadName() {
    bgThreadName = "${BG_THREAD_NAME_PREFIX}${randomThreadId()}"
}

fun clearBgThreadName() {
    bgThreadName = "${BG_THREAD_NAME_PREFIX}not_set"
}

fun getCurrentBgThreadName(): String {
    return bgThreadName
}

private fun randomThreadId(): String {
    val numericalChars = ('0'..'9')
    return String(CharArray(8) { numericalChars.random() })
}

private fun putValueFromRow(row: Row, key: String): Boolean {
    // Key name for Perfetto metrics computed by looking at each measurement slice, e.g.
    // "measurement 0", "measurement 1", "measurement 2", etc.
    // mt = "measurement timeline"
    val metricName = "perfetto_mt_$key"
    val metricValue = row[key]
    val strValue =
        when (metricValue) {
            is String,
            is Int,
            is Long -> "$metricValue"
            is Float,
            is Double -> String.format("%.3f", metricValue)
            null -> {
                Log.w(TAG, "Metric not found for key: $key")
                null
            }
            else -> {
                Log.w(TAG, "Unsupported metric type: ${metricValue::class}")
                null
            }
        }
    if (strValue != null) {
        if (metricValue is Number) {
            PerfettoMeasurementTimelineMetricCollector.putMetric(metricName, metricValue.toDouble())
        }
    }
    return strValue != "=NA()"
}
