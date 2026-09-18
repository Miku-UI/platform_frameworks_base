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

package com.android.app.concurrent.benchmark

import androidx.benchmark.BlackHole
import com.android.app.concurrent.benchmark.base.BaseConcurrentBenchmark
import com.android.app.concurrent.benchmark.base.BaseLooperThreadBenchmark
import com.android.app.concurrent.benchmark.base.CoroutineLooperThreadBenchmark
import com.android.app.concurrent.benchmark.util.allCpuWorkloads
import com.android.app.concurrent.benchmark.util.dbg
import com.android.app.concurrent.benchmark.util.stressCpu
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Calculate the runtime of [stressCpu] when run on the main "BenchmarkRunner" thread.
 *
 * Compare this to [LooperSingleThreadBenchmark.benchmark0_baseline], which should have the same
 * runtime.
 */
@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BenchmarkRunnerSingleThreadBenchmark(val mainWorkload: Int) : BaseConcurrentBenchmark() {

    companion object {
        @Parameters(name = "mainWorkload={0}") @JvmStatic fun getParameters() = allCpuWorkloads
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        benchmarkRule.runBenchmark { onEachIteration { sum += stressCpu(mainWorkload) } }
        BlackHole.consume(sum)
    }
}

/**
 * Calculate the runtime of [consumeCpu] when run on a single background `Looper` thread.
 *
 * A separate `Looper` thread is used because the main "BenchmarkRunner" cannot have a `Looper`
 * installed on it. Doing so would work for a single test run, but the `Looper` would need to call
 * [quit][android.os.Looper.quit] in order for testing to continue, preventing its usage again.
 *
 * Compare [benchmark0_baseline] to [BenchmarkRunnerSingleThreadBenchmark], which should have the
 * same runtime.
 *
 * Contrast [benchmark0_baseline] and [benchmark1_deferred]:
 * - `benchmark_baseline` runs all the computation in a single message is posted to the `Looper`
 *   thread, which runs continuously until the benchmark is completed
 * - `benchmark_deferred` posts a message to the `Looper` on each iteration to perform the same
 *   computations.
 */
@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LooperSingleThreadBenchmark(val consumeCpuIterations: Int) : BaseLooperThreadBenchmark() {

    companion object {
        @Parameters(name = "mainWorkload={0}") @JvmStatic fun getParameters() = allCpuWorkloads
    }

    fun consumeCpu(): Double {
        return stressCpu(consumeCpuIterations)
    }

    @Test
    fun benchmark0_baseline() {
        var sum = 0.0
        measure {
            while (state.keepRunning()) {
                sum += consumeCpu()
            }
            stopBenchmark()
        }
        BlackHole.consume(sum)
    }

    @Test
    fun benchmark1_deferred() {
        var sum = 0.0
        measure {
            fun increment() {
                if (state.keepRunning()) {
                    sum += consumeCpu()
                    handler.post(::increment)
                } else {
                    stopBenchmark()
                }
            }
            increment()
        }
        BlackHole.consume(sum)
    }
}

/**
 * Calculate the runtime of [stressCpu] when run on a single background Looper thread which is also
 * using coroutines.
 */
@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CoroutineSingleThreadBenchmark(val mainWorkload: Int) : CoroutineLooperThreadBenchmark() {

    companion object {
        @Parameters(name = "mainWorkload={0}") @JvmStatic fun getParameters() = allCpuWorkloads
    }

    fun stressCpu(): Double {
        return stressCpu(mainWorkload)
    }

    @Test
    fun benchmark0_channel() {
        var sum = 0.0
        measureCoroutine { scope ->
            val values = Channel<Double>()
            scope.launch {
                var n = 0
                while (true) {
                    dbg { "values.send" }
                    values.send(stressCpu() + n++)
                }
            }
            scope.launch {
                while (state.keepRunning()) {
                    dbg { "values.receive" }
                    val result = values.receive()
                    dbg { "sum += input" }
                    sum += result
                }
                stopBenchmark()
            }
        }
        BlackHole.consume(sum)
    }

    private fun benchmarkColdFlow(operator: Flow<Double>.((Double) -> Double) -> Flow<Double>) {
        var sum = 0.0
        val values =
            flow {
                    var n = 0
                    while (true) {
                        val value = stressCpu() + n++
                        dbg { "emit:$value" }
                        emit(value)
                    }
                }
                .operator { value ->
                    dbg { "operator:$value" }
                    value + stressCpu()
                }
        measureCoroutine { scope ->
            scope.launch {
                values.collect { value ->
                    dbg { "collect:$value" }
                    if (state.keepRunning()) {
                        sum += value
                    } else {
                        stopBenchmark()
                    }
                }
            }
        }
        BlackHole.consume(sum)
    }

    @Test
    fun benchmark1_flow1_map() {
        benchmarkColdFlow { transform -> map(transform) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun benchmark1_flow2_mapLatest() {
        benchmarkColdFlow { transform -> mapLatest(transform) }
    }

    @Test
    fun benchmark2_stateFlow() {
        var sum = 0.0
        measureCoroutine { scope ->
            val trigger = MutableStateFlow(0)
            val values =
                trigger
                    .onEach { n -> sum += stressCpu() + n }
                    .stateIn(scope, started = SharingStarted.Lazily, 0)
            values.onEach { if (!state.keepRunning()) stopBenchmark() }.launchIn(scope)
            var n = 0
            scope.launch {
                while (true) {
                    dbg { "counter.value n=$n" }
                    trigger.value = n++
                    yield()
                }
            }
        }
        BlackHole.consume(sum)
    }

    @Test
    fun benchmark3_sharedFlow() {
        var sum = 0.0
        measureCoroutine { scope ->
            val trigger = MutableStateFlow(0)
            val values =
                trigger
                    .onEach { n -> sum += stressCpu() + n }
                    .shareIn(scope, started = SharingStarted.Lazily)
            values
                .onEach {
                    if (!state.keepRunning()) {
                        stopBenchmark()
                    }
                }
                .launchIn(scope)
            var n = 0
            scope.launch {
                while (true) {
                    dbg { "counter.value n=$n" }
                    trigger.value = n++
                    yield()
                }
            }
        }
        BlackHole.consume(sum)
    }
}
