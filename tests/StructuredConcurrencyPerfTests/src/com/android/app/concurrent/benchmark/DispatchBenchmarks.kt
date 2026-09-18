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
import com.android.app.concurrent.benchmark.base.ConcurrentBenchmarkRule
import com.android.app.concurrent.benchmark.event.BaseEventBenchmark
import com.android.app.concurrent.benchmark.event.EventContextProvider
import com.android.app.concurrent.benchmark.event.FlowWritableEventBuilder
import com.android.app.concurrent.benchmark.event.SimpleEvent
import com.android.app.concurrent.benchmark.event.SimpleWritableEventBuilder
import com.android.app.concurrent.benchmark.event.WritableEventFactory
import com.android.app.concurrent.benchmark.util.CyclicCountDownBarrier
import com.android.app.concurrent.benchmark.util.ExecutorServiceThreadWithExecutorBuilder
import com.android.app.concurrent.benchmark.util.ExecutorServiceThreadWithExecutorCoroutineDispatcherBuilder
import com.android.app.concurrent.benchmark.util.LooperThreadWithExecutorBuilder
import com.android.app.concurrent.benchmark.util.LooperThreadWithHandlerDispatcherBuilder
import com.android.app.concurrent.benchmark.util.LooperThreadWithImmediateHandlerDispatcherBuilder
import com.android.app.concurrent.benchmark.util.ThreadBuilder
import com.android.app.concurrent.benchmark.util.allCpuWorkloads
import com.android.app.concurrent.benchmark.util.dbg
import com.android.app.concurrent.benchmark.util.stressCpu
import com.android.app.concurrent.benchmark.util.times
import java.util.concurrent.Executor
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

interface ConsumeCpuBenchmark {
    val mainWorkload: Int
    val bgWorkload: Int

    fun runMainWorkload(): Double {
        return stressCpu(mainWorkload)
    }

    fun runBgWorkload(): Double {
        return stressCpu(bgWorkload)
    }
}

/**
 * Calculate the runtime of [stressCpu] when dispatched to the background thread.
 *
 * @param mainCpu arg to pass to [stressCpu] when called on the main "BenchmarkRunner" thread
 * @param bgCpu arg to pass to [stressCpu] when called on the bg thread
 */
@RunWith(Parameterized::class)
class BaselineDispatchBenchmark(
    param: ThreadBuilder<Executor>,
    override val mainWorkload: Int,
    override val bgWorkload: Int,
) : BaseSchedulerBenchmark<Executor>(param), ConsumeCpuBenchmark {

    companion object {
        @Parameters(name = "{0}:mainWorkload={1}:bgWorkLoad={2}")
        @JvmStatic
        fun getParameters() =
            listOf(ExecutorServiceThreadWithExecutorBuilder, LooperThreadWithExecutorBuilder) *
                allCpuWorkloads *
                allCpuWorkloads
    }

    @Test
    fun benchmark() {
        var mainSum = 0.0
        var bgSum = 0.0

        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier -> scheduler.execute { barrier.countDown() } }
                onEachIteration { _, barrier ->
                    dbg { "main:0" }
                    scheduler.execute {
                        dbg { "bg:A" }
                        bgSum += runBgWorkload()
                        dbg { "bg:B" }
                        barrier.countDown()
                    }
                    dbg { "main:1" }
                    mainSum += runMainWorkload()
                    dbg { "main:2" }
                }
            }
        }
        BlackHole.consume(mainSum)
        BlackHole.consume(bgSum)
    }
}

private class SimpleExecutorDispatcher(val executor: Executor) :
    AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
        DispatchedContinuation(continuation)

    inner class DispatchedContinuation<T>(val delegate: Continuation<T>) : Continuation<T> {
        override val context: CoroutineContext = delegate.context

        override fun resumeWith(result: Result<T>) {
            executor.execute { delegate.resumeWith(result) }
        }
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StartCoroutineDispatchBenchmark(
    param: ThreadBuilder<Executor>,
    override val mainWorkload: Int,
    override val bgWorkload: Int,
) : BaseSchedulerBenchmark<Executor>(param), ConsumeCpuBenchmark {

    companion object {
        @Parameters(name = "{0}:mainWorkload={1}:bgWorkload={2}")
        @JvmStatic
        fun getParameters() =
            listOf(ExecutorServiceThreadWithExecutorBuilder, LooperThreadWithExecutorBuilder) *
                allCpuWorkloads *
                allCpuWorkloads
    }

    @Test
    fun benchmark() {
        var mainSum = 0.0
        var bgSum = 0.0
        // Interceptor that always runs the coroutine on the bg executor
        val simpleInterceptor = SimpleExecutorDispatcher(scheduler)
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier -> scheduler.execute { barrier.countDown() } }
                onEachIteration { _, barrier ->
                    dbg { "main:0" }
                    suspend {
                            dbg { "bg:A" }
                            bgSum += runBgWorkload()
                            dbg { "bg:B" }
                            barrier.countDown()
                        }
                        .createCoroutineUnintercepted(
                            Continuation(context = simpleInterceptor, resumeWith = {})
                        )
                        .intercepted()
                        .resume(Unit)
                    dbg { "main:1" }
                    mainSum += runMainWorkload()
                    dbg { "main:2" }
                }
            }
        }
        BlackHole.consume(mainSum)
        BlackHole.consume(bgSum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ResumeCoroutineDispatchBenchmark(
    param: ThreadBuilder<Executor>,
    override val mainWorkload: Int,
    override val bgWorkload: Int,
) : BaseSchedulerBenchmark<Executor>(param), ConsumeCpuBenchmark {

    companion object {
        @Parameters(name = "{0}:mainWorkload={1}:bgWorkLoad={2}")
        @JvmStatic
        fun getParameters() =
            listOf(ExecutorServiceThreadWithExecutorBuilder, LooperThreadWithExecutorBuilder) *
                allCpuWorkloads *
                allCpuWorkloads
    }

    @Test
    fun benchmark() {
        var mainSum = 0.0
        var bgSum = 0.0
        // Interceptor that always runs the coroutine on the bg executor
        val simpleInterceptor = SimpleExecutorDispatcher(scheduler)
        suspend fun CyclicCountDownBarrier.waitForNextIteration() {
            return suspendCoroutine { continuation ->
                scheduler.execute {
                    countDown()
                    continuation.resume(Unit)
                }
            }
        }
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    suspend {
                            while (true) {
                                dbg { "bg:A" }
                                bgSum += runBgWorkload()
                                dbg { "bg:B" }
                                barrier.waitForNextIteration()
                            }
                        }
                        .createCoroutineUnintercepted(
                            Continuation(context = simpleInterceptor, resumeWith = {})
                        )
                        .intercepted()
                        .resume(Unit)
                }
            }
            disableNumPartiesCheck()
            onEachIteration {
                dbg { "main:0" }
                mainSum += runMainWorkload()
                dbg { "main:1" }
            }
        }
        BlackHole.consume(mainSum)
        BlackHole.consume(bgSum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LaunchCoroutineDispatchBenchmark(
    param: ThreadBuilder<CoroutineScope>,
    override val mainWorkload: Int,
    override val bgWorkload: Int,
) : BaseSchedulerBenchmark<CoroutineScope>(param), ConsumeCpuBenchmark {

    companion object {
        @Parameters(name = "{0}:mainWorkload={1}:bgWorkLoad={2}")
        @JvmStatic
        fun getParameters() =
            listOf(
                ExecutorServiceThreadWithExecutorCoroutineDispatcherBuilder,
                LooperThreadWithHandlerDispatcherBuilder,
                LooperThreadWithImmediateHandlerDispatcherBuilder,
            ) * allCpuWorkloads * allCpuWorkloads
    }

    @Test
    fun benchmark() {
        var mainSum = 0.0
        var bgSum = 0.0
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier -> scheduler.launch { barrier.countDown() } }
                onEachIteration { _, barrier ->
                    dbg { "main:0" }
                    scheduler.launch {
                        dbg { "bg:A" }
                        bgSum += runBgWorkload()
                        dbg { "bg:B" }
                        barrier.countDown()
                    }
                    dbg { "main:1" }
                    mainSum += runMainWorkload()
                    dbg { "main:2" }
                }
            }
        }
        BlackHole.consume(mainSum)
        BlackHole.consume(bgSum)
    }
}

private sealed interface BaseEventDispatchBenchmark<T, E : Any> : ConsumeCpuBenchmark
    where T : WritableEventFactory<E>, T : EventContextProvider<E> {
    val benchmarkRule: ConcurrentBenchmarkRule
    val context: T

    @Test
    fun benchmark() {
        val trigger = context.createWritableEvent(0)
        var mainSum = 0.0
        var bgSum = 0.0
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    context.read {
                        trigger.observe {
                            dbg { "bg:A" }
                            bgSum += runBgWorkload()
                            dbg { "bg:B" }
                            barrier.countDown()
                        }
                    }
                }
            }
            onEachIteration { n ->
                context.write {
                    dbg { "main:0" }
                    trigger.update(n)
                    dbg { "main:1" }
                }
                mainSum += runMainWorkload()
                dbg { "main:2" }
            }
        }
        BlackHole.consume(mainSum)
        BlackHole.consume(bgSum)
    }
}

@RunWith(Parameterized::class)
class SimpleObservableEventDispatchBenchmark(
    param: ThreadBuilder<Executor>,
    override val mainWorkload: Int,
    override val bgWorkload: Int,
) :
    BaseEventBenchmark<Executor, SimpleWritableEventBuilder>(
        param,
        { SimpleWritableEventBuilder(it) },
    ),
    BaseEventDispatchBenchmark<SimpleWritableEventBuilder, SimpleEvent<*>>,
    ConsumeCpuBenchmark {

    companion object {
        @Parameters(name = "{0}:mainWorkload={1}:bgWorkLoad={2}")
        @JvmStatic
        fun getParameters() =
            listOf(ExecutorServiceThreadWithExecutorBuilder, LooperThreadWithExecutorBuilder) *
                allCpuWorkloads *
                allCpuWorkloads
    }
}

@RunWith(Parameterized::class)
class MutableStateFlowEventDispatchBenchmark(
    param: ThreadBuilder<CoroutineScope>,
    override val mainWorkload: Int,
    override val bgWorkload: Int,
) :
    BaseEventBenchmark<CoroutineScope, FlowWritableEventBuilder>(
        param,
        { FlowWritableEventBuilder(it) },
    ),
    BaseEventDispatchBenchmark<FlowWritableEventBuilder, Flow<*>>,
    ConsumeCpuBenchmark {

    companion object {
        @Parameters(name = "{0}:mainWorkload={1}:bgWorkLoad={2}")
        @JvmStatic
        fun getParameters() =
            listOf(
                ExecutorServiceThreadWithExecutorCoroutineDispatcherBuilder,
                LooperThreadWithHandlerDispatcherBuilder,
                LooperThreadWithImmediateHandlerDispatcherBuilder,
            ) * allCpuWorkloads * allCpuWorkloads
    }
}
