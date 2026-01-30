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
import com.android.app.concurrent.benchmark.event.EventBox
import com.android.app.concurrent.benchmark.event.EventContextProvider
import com.android.app.concurrent.benchmark.event.FlowWritableEventBuilder
import com.android.app.concurrent.benchmark.event.MapOperator
import com.android.app.concurrent.benchmark.event.SimpleEvent
import com.android.app.concurrent.benchmark.event.SimpleWritableEventBuilder
import com.android.app.concurrent.benchmark.event.WritableEventFactory
import com.android.app.concurrent.benchmark.util.ExecutorServiceCoroutineScopeBuilder
import com.android.app.concurrent.benchmark.util.ExecutorThreadBuilder
import com.android.app.concurrent.benchmark.util.ThreadFactory
import com.android.app.concurrent.benchmark.util.times
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

private val CHAIN_LENGTHS = listOf(10, 25)

private sealed interface ChainedEventBenchmark<T, E : Any>
    where T : WritableEventFactory<E>, T : EventContextProvider<E>, T : MapOperator<E> {
    val benchmarkRule: ConcurrentBenchmarkRule
    val context: T

    val chainLength: Int

    @Test
    fun benchmark_eventObservers_chained() {
        var receivedVal = 0
        val sourceEvent = context.createWritableEvent(0)
        lateinit var lastEvent: EventBox<Int, E>

        repeat(chainLength) { i ->
            val upstream =
                if (i == 0) {
                    sourceEvent
                } else {
                    lastEvent
                }
            lastEvent = with(context) { upstream.map { it + 1 } }
        }

        benchmarkRule.runBenchmark {
            if (chainLength != 0) {
                withBarrier(count = 1) {
                    beforeFirstIteration { barrier ->
                        context.read {
                            lastEvent.observe { newValue ->
                                receivedVal = newValue
                                barrier.countDown()
                            }
                        }
                    }
                }
            }
            onEachIteration { n -> context.write { sourceEvent.update(n) } }
            stateChecker(
                isInExpectedState = expectedState@{ n ->
                        return@expectedState receivedVal == n + chainLength
                    },
                expectedStr = "receivedVal == n + chainLength",
                expectedCalc = result@{ n ->
                        return@result "$receivedVal == $n + $chainLength"
                    },
            )
            @OptIn(ExperimentalBlackHoleApi::class)
            afterLastIteration { BlackHole.consume(receivedVal) }
        }
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleChainedEventBenchmark(
    threadParam: ThreadFactory<Any, Executor>,
    override val chainLength: Int,
) :
    BaseEventBenchmark<Executor, SimpleWritableEventBuilder>(
        threadParam,
        { SimpleWritableEventBuilder(it) },
    ),
    ChainedEventBenchmark<SimpleWritableEventBuilder, SimpleEvent<*>> {

    companion object {
        @Parameters(name = "{0},{1}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorThreadBuilder) * CHAIN_LENGTHS
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlowChainedEventBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>,
    override val chainLength: Int,
) :
    BaseEventBenchmark<CoroutineScope, FlowWritableEventBuilder>(
        threadParam,
        { FlowWritableEventBuilder(it) },
    ),
    ChainedEventBenchmark<FlowWritableEventBuilder, Flow<*>> {

    companion object {
        @Parameters(name = "{0},{1}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorServiceCoroutineScopeBuilder) * CHAIN_LENGTHS
    }
}
