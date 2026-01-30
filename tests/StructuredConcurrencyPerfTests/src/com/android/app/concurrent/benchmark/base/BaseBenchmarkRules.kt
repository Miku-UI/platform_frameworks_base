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

import android.device.collectors.util.SendToInstrumentation
import android.os.Bundle
import android.util.Log
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.PerfettoTraceRule
import androidx.benchmark.macro.runSingleSessionServer
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.perfetto.PerfettoConfig
import androidx.benchmark.traceprocessor.PerfettoTrace
import androidx.benchmark.traceprocessor.Row
import androidx.benchmark.traceprocessor.TraceProcessor
import androidx.test.platform.app.InstrumentationRegistry
import com.android.app.concurrent.benchmark.util.CsvMetricCollector
import com.android.app.concurrent.benchmark.util.CsvMetricCollector.Helper.getCurrentBgThreadName
import com.android.app.concurrent.benchmark.util.DEBUG
import com.android.app.concurrent.benchmark.util.PERFETTO_CONFIG
import com.android.app.concurrent.benchmark.util.PERFETTO_SQL_QUERY_FORMAT_STR
import com.android.app.concurrent.benchmark.util.ThreadFactory
import com.android.app.concurrent.benchmark.util.dbg
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

abstract class BaseConcurrentBenchmark {
    @get:Rule val benchmarkRule = ConcurrentBenchmarkRule()
}

abstract class BaseSchedulerBenchmark<T : Any>(param: ThreadFactory<Any, out T>) {
    val schedulerRule = SchedulerBenchmarkRule(param)
    val benchmarkRule = ConcurrentBenchmarkRule()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(schedulerRule).around(benchmarkRule)

    val scheduler: T
        get() = schedulerRule.scheduler
}

class SchedulerBenchmarkRule<R : Any, S : Any>(val threadFactory: ThreadFactory<R, S>) : TestRule {
    lateinit var scheduler: S
        private set

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                dbg { "evaluate $base#$description" }
                scheduler = threadFactory.startThreadAndGetScheduler()
                try {
                    base.evaluate()
                } finally {
                    threadFactory.stopThreadAndQuitScheduler()
                }
            }
        }
    }
}

class ConcurrentBenchmarkRule() : TestRule {

    val benchmarkRule = BenchmarkRule()

    companion object {
        private val TAG = "ConcurrentBenchmarkRule"
    }

    @OptIn(ExperimentalPerfettoCaptureApi::class)
    override fun apply(base: Statement, description: Description): Statement {
        val traceCallback: ((PerfettoTrace) -> Unit) = { trace ->
            TraceProcessor.runSingleSessionServer(trace.path) {
                if (DEBUG) return@runSingleSessionServer
                val rowSequence =
                    query(String.format(PERFETTO_SQL_QUERY_FORMAT_STR, getCurrentBgThreadName()))
                val row = rowSequence.firstOrNull() ?: return@runSingleSessionServer
                val results = Bundle()
                var allMetricsValid = true
                row.keys.forEach { key ->
                    allMetricsValid = putValueFromRow(results, row, key) && allMetricsValid
                }
                CsvMetricCollector.Helper.clearActiveName()
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                SendToInstrumentation.sendBundle(instrumentation, results)
                if (!allMetricsValid) {
                    error(
                        "Trace has data loss or other errors. For more details, " +
                            "open the trace in Perfetto and view its info and stats."
                    )
                }
            }
        }
        return RuleChain.outerRule(::applyInternal)
            .around(benchmarkRule)
            .around(
                PerfettoTraceRule(
                    config = PerfettoConfig.Text(PERFETTO_CONFIG),
                    enableUserspaceTracing = false,
                    traceCallback = traceCallback,
                )
            )
            .apply(base, description)
    }

    private fun applyInternal(base: Statement, description: Description) =
        object : Statement() {
            override fun evaluate() {
                dbg { "evaluate" }
                CsvMetricCollector.Helper.setActiveName(
                    description.testClass.simpleName,
                    description.methodName,
                )
                base.evaluate()
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
        dbg { "measureRepeated" }
        var n = 0
        beforeFirstIteration()

        // No need to set THREAD_PRIORITY_MOST_FAVORABLE here; it is already set by the benchmark
        // initialization in AndroidX
        val state = benchmarkRule.getState()
        while (state.keepRunning()) {
            n++
            onEachIteration(n)
        }
        afterLastIteration()
    }

    private fun putValueFromRow(bundle: Bundle, row: Row, key: String): Boolean {
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
            bundle.putString(metricName, strValue)
            CsvMetricCollector.Helper.putMetric(metricName, strValue)
        }
        return strValue != "=NA()"
    }
}
