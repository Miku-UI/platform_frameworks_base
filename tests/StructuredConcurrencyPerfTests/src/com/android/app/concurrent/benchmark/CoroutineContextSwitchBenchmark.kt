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
@file:OptIn(ExperimentalBlackHoleApi::class)

package com.android.app.concurrent.benchmark

import androidx.benchmark.BlackHole
import androidx.benchmark.ExperimentalBlackHoleApi
import com.android.app.concurrent.benchmark.base.BaseSchedulerBenchmark
import com.android.app.concurrent.benchmark.util.ExecutorThreadBuilder
import com.android.app.concurrent.benchmark.util.ThreadFactory
import com.android.app.concurrent.benchmark.util.times
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

class ContextTransformParam(val name: String, val transform: (Executor) -> CoroutineContext) {
    override fun toString(): String = name
}

/**
 * Returns a dispatcher that runs on the same thread as the given dispatcher, except different so
 * that it won't be merged during CoroutineContext operations.
 */
private fun wrapDispatcher(executor: Executor): CoroutineDispatcher {
    return Executor { command -> executor.execute(command) }.asCoroutineDispatcher()
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CoroutineContextSwitchBenchmark(
    param: ThreadFactory<Any, Executor>,
    contextTransformer: ContextTransformParam,
) : BaseSchedulerBenchmark<Executor>(param) {

    companion object {
        @Parameters(name = "{0},{1}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorThreadBuilder) *
                listOf(
                    ContextTransformParam("unwrapped") { _ -> EmptyCoroutineContext },
                    ContextTransformParam("wrapped") { executor -> wrapDispatcher(executor) },
                )
    }

    lateinit var bgScope: CoroutineScope

    val transformContext = contextTransformer.transform

    @Before
    fun setupScope() {
        bgScope = CoroutineScope(scheduler.asCoroutineDispatcher())
    }

    @After
    fun tearDownScope() {
        bgScope.cancel()
    }

    @Test
    fun benchmark_withContext() {
        var sum = 0.0
        val state = MutableStateFlow(0.00)
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    bgScope.launch {
                        state.collect {
                            withContext(transformContext(scheduler)) {
                                sum += sqrt(it)
                                barrier.countDown()
                            }
                        }
                    }
                }
            }
            onEachIteration { n -> state.value = sqrt(n.toDouble()) }
        }
        BlackHole.consume(sum)
    }

    @Test
    fun benchmark_flowOn() {
        var sum = 0.0
        val state = MutableStateFlow(0.00)
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    bgScope.launch {
                        state
                            .filter { it > -1.0 } // always true
                            .flowOn(transformContext(scheduler))
                            .map { it + 1.0 }
                            .flowOn(transformContext(scheduler))
                            .filter { it > -1.0 } // always true
                            .flowOn(transformContext(scheduler))
                            .map { it - 1.0 } // restore to original input value
                            .flowOn(transformContext(scheduler))
                            .map { sqrt(it) }
                            .collect {
                                sum += it
                                barrier.countDown()
                            }
                    }
                }
            }
            onEachIteration { n -> state.value = sqrt(n.toDouble()) }
        }
        BlackHole.consume(sum)
    }
}
