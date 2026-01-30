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
import androidx.benchmark.ExperimentalBlackHoleApi
import com.android.app.concurrent.benchmark.base.ConcurrentBenchmarkRule
import com.android.app.concurrent.benchmark.event.BaseEventBenchmark
import com.android.app.concurrent.benchmark.event.EventContextProvider
import com.android.app.concurrent.benchmark.event.FlowWritableEventBuilder
import com.android.app.concurrent.benchmark.event.SampleOperator
import com.android.app.concurrent.benchmark.event.SimpleEvent
import com.android.app.concurrent.benchmark.event.SimpleWritableEventBuilder
import com.android.app.concurrent.benchmark.event.WritableEventFactory
import com.android.app.concurrent.benchmark.util.ExecutorServiceCoroutineScopeBuilder
import com.android.app.concurrent.benchmark.util.ExecutorThreadBuilder
import com.android.app.concurrent.benchmark.util.ThreadFactory
import com.android.app.concurrent.benchmark.util.dbg
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@OptIn(ExperimentalBlackHoleApi::class)
private sealed interface SampleEventBenchmark<T, E : Any>
    where T : WritableEventFactory<E>, T : EventContextProvider<E>, T : SampleOperator<E> {
    val benchmarkRule: ConcurrentBenchmarkRule
    val context: T

    /**
     * Tests the performance of System UI's [sample] operator, which is used to snapshot a flow's
     * value based on a trigger.
     */
    @Test
    fun benchmark_sample() {
        val dataFlow = context.createWritableEvent(0)
        val triggerFlow = context.createWritableEvent(0)
        var result = 0
        val sampledFlow = with(context) { triggerFlow.sample(dataFlow) { trigger, data -> data } }

        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    context.read {
                        sampledFlow.observe { data ->
                            dbg { "sampledFlow.collect, value = $data" }
                            result = data
                            // countdown on 0 so the test doesn't get stuck before the first
                            // iteration
                            if (result == 0 || (result - 9) % 10 == 0) {
                                barrier.countDown()
                            }
                        }
                    }
                }
            }

            onEachIteration { n ->
                dbg { "mainBlock n = $n" }
                // Update the data flow multiple times. Only the last value should be captured when
                // the trigger fires
                context.write {
                    repeat(10) { dataFlow.update(n * 10 + it) }
                    triggerFlow.update(n) // trigger sample collection
                }
            }

            stateChecker(
                isInExpectedState = { n -> result == n * 10 + 9 },
                expectedStr = "result == n * 10 + 9",
                expectedCalc = { n -> "$result == $n * 10 + 9" },
            )

            afterLastIteration { BlackHole.consume(result) }
        }
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleSampleEventBenchmark(param: ThreadFactory<Any, Executor>) :
    BaseEventBenchmark<Executor, SimpleWritableEventBuilder>(
        param,
        { SimpleWritableEventBuilder(it) },
    ),
    SampleEventBenchmark<SimpleWritableEventBuilder, SimpleEvent<*>> {

    companion object {
        @Parameters(name = "{0}") @JvmStatic fun getDispatchers() = listOf(ExecutorThreadBuilder)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlowSampleEventBenchmark(param: ThreadFactory<Any, CoroutineScope>) :
    BaseEventBenchmark<CoroutineScope, FlowWritableEventBuilder>(
        param,
        { FlowWritableEventBuilder(it) },
    ),
    SampleEventBenchmark<FlowWritableEventBuilder, Flow<*>> {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorServiceCoroutineScopeBuilder)
    }
}
