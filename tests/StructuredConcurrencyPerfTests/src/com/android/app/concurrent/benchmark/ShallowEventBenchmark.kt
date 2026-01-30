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
import androidx.compose.runtime.State as SnapshotState
import com.android.app.concurrent.benchmark.base.ConcurrentBenchmarkRule
import com.android.app.concurrent.benchmark.event.BaseCoroutineSnapshotEventBenchmark
import com.android.app.concurrent.benchmark.event.BaseExecutorSnapshotEventBenchmark
import com.android.app.concurrent.benchmark.event.BaseFlowEventBenchmark
import com.android.app.concurrent.benchmark.event.BaseKairosEventBenchmark
import com.android.app.concurrent.benchmark.event.BaseSimpleEventBenchmark
import com.android.app.concurrent.benchmark.event.EventContextProvider
import com.android.app.concurrent.benchmark.event.FlowWritableEventBuilder
import com.android.app.concurrent.benchmark.event.KairosWritableEventBuilder
import com.android.app.concurrent.benchmark.event.SimpleEvent
import com.android.app.concurrent.benchmark.event.SimpleWritableEventBuilder
import com.android.app.concurrent.benchmark.event.SnapshotWritableEventCoroutineBuilder
import com.android.app.concurrent.benchmark.event.SnapshotWritableEventExecutorBuilder
import com.android.app.concurrent.benchmark.event.WritableEventFactory
import com.android.app.concurrent.benchmark.util.ExecutorServiceCoroutineScopeBuilder
import com.android.app.concurrent.benchmark.util.ExecutorThreadBuilder
import com.android.app.concurrent.benchmark.util.ThreadFactory
import com.android.app.concurrent.benchmark.util.times
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.State as KairosState
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

private val PRODUCER_LIST = listOf(10)
private val CONSUMER_LIST = listOf(50)

private sealed interface ShallowEventBenchmark<T, E : Any>
    where T : WritableEventFactory<E>, T : EventContextProvider<E> {
    val benchmarkRule: ConcurrentBenchmarkRule
    val context: T

    val producerCount: Int

    val consumerCount: Int

    @Test
    fun benchmark_stateObservers_shallow() {
        val receivedVal = Array(consumerCount) { IntArray(producerCount) }
        val producers = List(producerCount) { context.createWritableEvent(0) }

        benchmarkRule.runBenchmark {
            if (consumerCount != 0) {
                withBarrier(producerCount * consumerCount) {
                    beforeFirstIteration { barrier ->
                        context.read {
                            repeat(consumerCount) { consumerIndex ->
                                producers.forEachIndexed { producerIndex, state ->
                                    state.observe { newValue ->
                                        receivedVal[consumerIndex][producerIndex] = newValue
                                        barrier.countDown()
                                    }
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
            @OptIn(ExperimentalBlackHoleApi::class)
            afterLastIteration { BlackHole.consume(receivedVal) }
        }
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlowShallowEventBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>,
    override val producerCount: Int,
    override val consumerCount: Int,
) : BaseFlowEventBenchmark(threadParam), ShallowEventBenchmark<FlowWritableEventBuilder, Flow<*>> {

    companion object {
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorServiceCoroutineScopeBuilder) * PRODUCER_LIST * CONSUMER_LIST
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleEventShallowEventBenchmark(
    threadParam: ThreadFactory<Any, Executor>,
    override val producerCount: Int,
    override val consumerCount: Int,
) :
    BaseSimpleEventBenchmark(threadParam),
    ShallowEventBenchmark<SimpleWritableEventBuilder, SimpleEvent<*>> {

    companion object {
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorThreadBuilder) * PRODUCER_LIST * CONSUMER_LIST
    }
}

@Ignore("Experimental")
@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ExecutorSnapshotShallowEventBenchmark(
    threadParam: ThreadFactory<Any, Executor>,
    override val producerCount: Int,
    override val consumerCount: Int,
) :
    BaseExecutorSnapshotEventBenchmark(threadParam),
    ShallowEventBenchmark<SnapshotWritableEventExecutorBuilder, SnapshotState<*>> {

    companion object {
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorThreadBuilder) * PRODUCER_LIST * CONSUMER_LIST
    }
}

@Ignore("Experimental")
@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CoroutineSnapshotShallowEventBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>,
    override val producerCount: Int,
    override val consumerCount: Int,
) :
    BaseCoroutineSnapshotEventBenchmark(threadParam),
    ShallowEventBenchmark<SnapshotWritableEventCoroutineBuilder, SnapshotState<*>> {

    companion object {
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorServiceCoroutineScopeBuilder) * PRODUCER_LIST * CONSUMER_LIST
    }
}

@OptIn(ExperimentalKairosApi::class)
@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KairosShallowEventBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>,
    override val producerCount: Int,
    override val consumerCount: Int,
) :
    BaseKairosEventBenchmark(threadParam),
    ShallowEventBenchmark<KairosWritableEventBuilder, KairosState<*>> {

    companion object {
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorServiceCoroutineScopeBuilder) * PRODUCER_LIST * CONSUMER_LIST
    }
}
