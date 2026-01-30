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
import com.android.app.concurrent.benchmark.base.BaseSchedulerBenchmark
import com.android.app.concurrent.benchmark.util.ExecutorServiceCoroutineScopeBuilder
import com.android.app.concurrent.benchmark.util.HandlerThreadImmediateScopeBuilder
import com.android.app.concurrent.benchmark.util.HandlerThreadScopeBuilder
import com.android.app.concurrent.benchmark.util.ThreadFactory
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
    block: (Flow<T1>, Int, CoroutineScope) -> Flow<T2>,
): (Flow<T1>, Int, CoroutineScope) -> Flow<T2> {
    return object : (Flow<T1>, Int, CoroutineScope) -> Flow<T2> {
        override fun invoke(upstream: Flow<T1>, index: Int, scope: CoroutineScope): Flow<T2> {
            return block(upstream, index, scope)
        }

        override fun toString(): String {
            return name
        }
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlowOperatorBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>,
    val chainLength: Int,
    val intermediateOperator: (Flow<Int>, Int, CoroutineScope) -> Flow<Int>,
) : BaseSchedulerBenchmark<CoroutineScope>(threadParam) {

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() =
            listOf(
                ExecutorServiceCoroutineScopeBuilder,
                HandlerThreadScopeBuilder,
                HandlerThreadImmediateScopeBuilder,
            ) *
                listOf(5, 10, 25) *
                listOf(
                    flowOpParam("cold") { upstream, _, _ -> upstream },
                    flowOpParam("stateIn") { upstream, index, scope ->
                        upstream.stateIn(
                            scope,
                            started = SharingStarted.Eagerly,
                            initialValue = index,
                        )
                    },
                    flowOpParam("conflate") { upstream, _, _ -> upstream.conflate() },
                    flowOpParam("buffer-2") { upstream, _, _ -> upstream.buffer(2) },
                    flowOpParam("buffer-4") { upstream, _, _ -> upstream.buffer(4) },
                    flowOpParam("distinctUntilChanged") { upstream, _, _ ->
                        upstream.distinctUntilChanged()
                    },
                    flowOpParam("flatMapLatest-cold") { upstream, _, _ ->
                        upstream.flatMapLatest { value -> flow { emit(value) } }
                    },
                    flowOpParam<Int, Int>("flatMapLatest-state") { upstream, _, _ ->
                        val odds = MutableStateFlow(0)
                        val evens = MutableStateFlow(0)
                        upstream.flatMapLatest { value ->
                            if (value % 2 == 0) {
                                evens.value = value
                                evens
                            } else {
                                odds.value = value
                                odds
                            }
                        }
                    },
                )
    }

    @OptIn(ExperimentalBlackHoleApi::class)
    @Test
    fun benchmark() {
        val sourceState = MutableStateFlow(0)
        var receivedVal = 0
        val flowChain = mutableListOf<Flow<Int>>()
        repeat(chainLength) { i ->
            val upstream = if (i == 0) sourceState else flowChain.last()
            flowChain.add(intermediateOperator(upstream.map { it + 1 }, i, scheduler))
        }
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    scheduler.launch {
                        flowChain.last().collect {
                            receivedVal = it
                            barrier.countDown()
                        }
                    }
                }
            }
            onEachIteration { n -> sourceState.value = n }
            stateChecker(
                isInExpectedState = { n -> receivedVal == n + chainLength },
                expectedStr = "receivedVal == n + chainLength",
                expectedCalc = { n -> "$receivedVal == $n + $chainLength" },
            )
            @OptIn(ExperimentalBlackHoleApi::class)
            afterLastIteration { BlackHole.consume(receivedVal) }
        }
    }
}
