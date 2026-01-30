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
import com.android.app.concurrent.benchmark.event.IntEventCombiner
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
private sealed interface ActivateEventBenchmark<T, E : Any>
    where T : WritableEventFactory<E>, T : EventContextProvider<E>, T : IntEventCombiner<E> {
    val benchmarkRule: ConcurrentBenchmarkRule
    val context: T

    @Test
    fun benchmark_activateEventBenchmark() {
        val numEvents = 4
        val sourceEvents = List(numEvents) { context.createWritableEvent(0) }
        val combinedEvents =
            context.combineIntEvents(sourceEvents) { values ->
                val sum = values.sum()
                dbg { "sum = $sum" }
                sum
            }
        val signal = context.createWritableEvent(0)
        var result = 0
        benchmarkRule.runBenchmark {
            withBarrier(count = numEvents + 1) {
                beforeFirstIteration { barrier ->
                    context.read {
                        signal.observe {
                            if (it == 0) {
                                // trigger the first iteration so the test can begin:
                                repeat(numEvents) {
                                    // in future iterations, this will be handled by the sourceEvent
                                    // observers that are registered on each loop
                                    barrier.countDown()
                                }
                            }
                            // will countDown() after combinedEvents reaches desired state
                            barrier.countDown()
                        }
                    }
                }
                lateinit var lastRead: AutoCloseable
                onEachIteration { n, barrier ->
                    lastRead =
                        context.read {
                            sourceEvents.forEach { sourceEvent ->
                                // will countDown() numEvents times
                                sourceEvent.observe { if (it == n) barrier.countDown() }
                            }
                            combinedEvents.observe {
                                dbg { "combineIntEvents it=$it" }
                                // Only countdown when the sum is a multiple of the number of source
                                // events, which indicates events were incremented as many times as
                                // there are `numEvents` (whether that was done in a random order or
                                // sequential).
                                if (it % numEvents == 0) {
                                    result = it
                                    context.write { signal.update(it) }
                                }
                            }
                        }
                }
                afterEachIteration { lastRead.close() }
            }

            onEachIteration { n ->
                context.write { sourceEvents.forEach { sourceEvent -> sourceEvent.update(n) } }
            }

            stateChecker(
                isInExpectedState = { n -> result == n * numEvents },
                expectedStr = "result == n * $numEvents",
                expectedCalc = { n -> "$result == $n * $numEvents" },
            )

            afterLastIteration { BlackHole.consume(result) }
        }
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleActivateEventBenchmark(param: ThreadFactory<Any, Executor>) :
    BaseEventBenchmark<Executor, SimpleWritableEventBuilder>(
        param,
        { SimpleWritableEventBuilder(it) },
    ),
    ActivateEventBenchmark<SimpleWritableEventBuilder, SimpleEvent<*>> {

    companion object {
        @Parameters(name = "{0}") @JvmStatic fun getDispatchers() = listOf(ExecutorThreadBuilder)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlowActivateEventBenchmark(param: ThreadFactory<Any, CoroutineScope>) :
    BaseEventBenchmark<CoroutineScope, FlowWritableEventBuilder>(
        param,
        { FlowWritableEventBuilder(it) },
    ),
    ActivateEventBenchmark<FlowWritableEventBuilder, Flow<*>> {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorServiceCoroutineScopeBuilder)
    }
}
