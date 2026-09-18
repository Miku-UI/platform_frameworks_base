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
import com.android.app.concurrent.benchmark.base.BaseSchedulerBenchmark
import com.android.app.concurrent.benchmark.util.CONSUME_CPU_NONE
import com.android.app.concurrent.benchmark.util.CONSUME_CPU_SMALL
import com.android.app.concurrent.benchmark.util.ExecutorServiceThreadWithExecutorCoroutineDispatcherBuilder
import com.android.app.concurrent.benchmark.util.LooperThreadWithHandlerDispatcherBuilder
import com.android.app.concurrent.benchmark.util.LooperThreadWithImmediateHandlerDispatcherBuilder
import com.android.app.concurrent.benchmark.util.ThreadBuilder
import com.android.app.concurrent.benchmark.util.stressCpu
import com.android.app.concurrent.benchmark.util.times
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

private fun <T1, T2> flowOpParam(
    name: String,
    block: Flow<T1>.(CoroutineScope, T2) -> Flow<T2>,
): Flow<T1>.(CoroutineScope, T2) -> Flow<T2> {
    return object : (Flow<T1>, CoroutineScope, T2) -> Flow<T2> {
        override fun invoke(upstream: Flow<T1>, scope: CoroutineScope, initialValue: T2): Flow<T2> {
            return upstream.block(scope, initialValue)
        }

        override fun toString(): String {
            return name
        }
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlowOperatorBenchmark(
    threadParam: ThreadBuilder<CoroutineScope>,
    val chainLength: Int,
    val intermediateOperator: Flow<Int>.(CoroutineScope, Int) -> Flow<Int>,
    val mainWorkload: Int,
    val bgWorkload: Int,
) : BaseSchedulerBenchmark<CoroutineScope>(threadParam) {

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        @Parameters(
            name = "{0}:chainLength={1}:intermediateOperator={2}:mainWorkload={3}:bgWorkload={4}"
        )
        @JvmStatic
        fun getParameters() =
            listOf(
                ExecutorServiceThreadWithExecutorCoroutineDispatcherBuilder,
                LooperThreadWithHandlerDispatcherBuilder,
                LooperThreadWithImmediateHandlerDispatcherBuilder,
            ) *
                listOf(5, 10, 25) *
                listOf(
                    flowOpParam("cold") { _, _ -> this },
                    flowOpParam("stateIn") { scope, initialValue ->
                        stateIn(
                            scope = scope,
                            started = SharingStarted.Eagerly,
                            initialValue = initialValue,
                        )
                    },
                    flowOpParam("conflate") { _, _ -> conflate() },
                    flowOpParam("buffer-2") { _, _ -> buffer(2) },
                    flowOpParam("buffer-4") { _, _ -> buffer(4) },
                    flowOpParam("distinctUntilChanged") { _, _ -> distinctUntilChanged() },
                    flowOpParam("map") { _, _ -> map { value -> value } },
                    flowOpParam("mapLatest") { _, _ -> mapLatest { value -> value } },
                    flowOpParam("flatMapLatest-newColdFlow") { _, _ ->
                        flatMapLatest { value -> flow { emit(value) } }
                    },
                    flowOpParam("flatMapLatest-toggleStateFlow") { _, _ ->
                        val odds = MutableStateFlow(0)
                        val evens = MutableStateFlow(0)
                        flatMapLatest { value: Int ->
                            if (value % 2 == 0) {
                                evens.value = value
                                evens
                            } else {
                                odds.value = value
                                odds
                            }
                        }
                    },
                ) *
                listOf(CONSUME_CPU_NONE, CONSUME_CPU_SMALL) *
                listOf(CONSUME_CPU_NONE, CONSUME_CPU_SMALL)
    }

    @Test
    fun benchmark() {
        val sourceState = MutableStateFlow(0)
        var lastFlow: Flow<Int> = sourceState
        var result = 0.0
        repeat(chainLength) {
            lastFlow =
                lastFlow
                    .onEach { result += stressCpu(bgWorkload) }
                    .intermediateOperator(scheduler, 0)
        }
        var receivedVal = 0
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    scheduler.launch {
                        lastFlow.collect {
                            receivedVal = it
                            barrier.countDown()
                        }
                    }
                }
            }
            onEachIteration { n ->
                sourceState.value = n
                result += stressCpu(mainWorkload)
            }
            stateChecker(
                isInExpectedState = { n -> receivedVal == n },
                expectedStr = "receivedVal == n",
                expectedCalc = { n -> "$receivedVal == $n + $chainLength" },
            )
            afterLastIteration {
                BlackHole.consume(receivedVal)
                BlackHole.consume(result)
            }
        }
    }
}
