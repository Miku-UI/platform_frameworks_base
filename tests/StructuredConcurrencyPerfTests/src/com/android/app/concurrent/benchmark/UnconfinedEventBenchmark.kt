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
import com.android.app.concurrent.benchmark.base.ConcurrentBenchmarkRule
import com.android.app.concurrent.benchmark.event.BaseFlowEventBenchmark
import com.android.app.concurrent.benchmark.event.BaseSimpleEventBenchmark
import com.android.app.concurrent.benchmark.event.EventContextProvider
import com.android.app.concurrent.benchmark.event.FlowWritableEventBuilder
import com.android.app.concurrent.benchmark.event.SimpleEvent
import com.android.app.concurrent.benchmark.event.SimpleWritableEventBuilder
import com.android.app.concurrent.benchmark.event.WritableEventFactory
import com.android.app.concurrent.benchmark.util.NoThreadWithDirectImmediateDispatcherBuilder
import com.android.app.concurrent.benchmark.util.NoThreadWithDirectImmediateExecutorBuilder
import com.android.app.concurrent.benchmark.util.NoThreadWithUnconfinedDispatcherBuilder
import com.android.app.concurrent.benchmark.util.ThreadBuilder
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

private val PRODUCER_LIST = listOf(10)
private val CONSUMER_LIST = listOf(50)

private sealed interface UnconfinedEventBenchmark<T, E : Any>
    where T : WritableEventFactory<E>, T : EventContextProvider<E> {
    val benchmarkRule: ConcurrentBenchmarkRule
    val context: T

    val producerCount: Int

    val consumerCount: Int

    @Test
    fun benchmark_unconfinedListeners() {
        val receivedVal = Array(producerCount) { IntArray(consumerCount) }
        val producers = List(producerCount) { context.createWritableEvent(0) }

        benchmarkRule.runBenchmark {
            if (consumerCount != 0) {
                beforeFirstIteration {
                    producers.forEachIndexed { producerIndex, state ->
                        context.read {
                            repeat(consumerCount) { consumerIndex ->
                                state.observe { newValue ->
                                    receivedVal[producerIndex][consumerIndex] = newValue
                                }
                            }
                        }
                    }
                }
            }
            onEachIteration { n -> context.write { producers.forEach { it.update(n) } } }
            stateChecker(
                isInExpectedState = expectedState@{ n ->
                        receivedVal.forEachIndexed { i, row ->
                            row.forEachIndexed { j, value ->
                                if (value != n) {
                                    return@expectedState false
                                }
                            }
                        }
                        return@expectedState true
                    },
                expectedStr = "receivedVal[i][j] == n (for all i and j)",
                expectedCalc = result@{ n ->
                        receivedVal.forEachIndexed { i, row ->
                            row.forEachIndexed { j, value ->
                                if (value != n) {
                                    return@result "receivedVal[$i][$j] == $value"
                                }
                            }
                        }
                        return@result "ok"
                    },
            )
            afterLastIteration { BlackHole.consume(receivedVal) }
        }
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleUnconfinedEventBenchmark(
    threadParam: ThreadBuilder<Executor>,
    override val producerCount: Int,
    override val consumerCount: Int,
) :
    BaseSimpleEventBenchmark(threadParam),
    UnconfinedEventBenchmark<SimpleWritableEventBuilder, SimpleEvent<*>> {

    companion object {
        @Parameters(name = "{0}:producers={1}:consumers={2}")
        @JvmStatic
        fun getParameters() =
            listOf(NoThreadWithDirectImmediateExecutorBuilder) * PRODUCER_LIST * CONSUMER_LIST
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlowUnconfinedEventBenchmark(
    threadParam: ThreadBuilder<CoroutineScope>,
    override val producerCount: Int,
    override val consumerCount: Int,
) :
    BaseFlowEventBenchmark(threadParam),
    UnconfinedEventBenchmark<FlowWritableEventBuilder, Flow<*>> {

    companion object {
        @Parameters(name = "{0}:producers={1}:consumers={2}")
        @JvmStatic
        fun getParameters() =
            listOf(
                NoThreadWithUnconfinedDispatcherBuilder,
                NoThreadWithDirectImmediateDispatcherBuilder,
            ) * PRODUCER_LIST * CONSUMER_LIST
    }
}
