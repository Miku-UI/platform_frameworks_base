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
import com.android.app.concurrent.benchmark.base.BaseConcurrentBenchmark
import com.android.app.concurrent.benchmark.base.BaseSchedulerBenchmark
import com.android.app.concurrent.benchmark.base.ConcurrentBenchmarkRule
import com.android.app.concurrent.benchmark.event.BaseEventBenchmark
import com.android.app.concurrent.benchmark.event.EventContextProvider
import com.android.app.concurrent.benchmark.event.FlowWritableEventBuilder
import com.android.app.concurrent.benchmark.event.SimpleEvent
import com.android.app.concurrent.benchmark.event.SimplePublisherImpl
import com.android.app.concurrent.benchmark.event.SimpleState
import com.android.app.concurrent.benchmark.event.SimpleSynchronousState
import com.android.app.concurrent.benchmark.event.SimpleWritableEventBuilder
import com.android.app.concurrent.benchmark.event.WritableEventFactory
import com.android.app.concurrent.benchmark.event.asSuspendableObserver
import com.android.app.concurrent.benchmark.util.CyclicCountDownBarrier
import com.android.app.concurrent.benchmark.util.ExecutorServiceCoroutineScopeBuilder
import com.android.app.concurrent.benchmark.util.ExecutorThreadBuilder
import com.android.app.concurrent.benchmark.util.HandlerThreadBuilder
import com.android.app.concurrent.benchmark.util.HandlerThreadImmediateScopeBuilder
import com.android.app.concurrent.benchmark.util.HandlerThreadScopeBuilder
import com.android.app.concurrent.benchmark.util.ThreadFactory
import java.util.concurrent.Executor
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(JUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SingleThreadSumDoubleBaselineBenchmark() : BaseConcurrentBenchmark() {

    @Test
    fun benchmark() {
        var sum = 0.0
        benchmarkRule.runBenchmark { onEachIteration { n -> sum += n.toDouble() } }
        BlackHole.consume(sum)
    }
}

@RunWith(JUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SingleThreadSum1xDoMathBaselineBenchmark() : BaseConcurrentBenchmark() {

    @Test
    fun benchmark() {
        var sum = 0.0
        benchmarkRule.runBenchmark { onEachIteration { n -> sum += doMath(n) } }
        BlackHole.consume(sum)
    }
}

@RunWith(JUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SingleThreadSum2xDoMathBaselineBenchmark() : BaseConcurrentBenchmark() {

    @Test
    fun benchmark() {
        var sum = 0.0
        benchmarkRule.runBenchmark { onEachIteration { n -> sum += doMath(doMath(n)) } }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
class ExecutorDispatchBaselineBenchmark(param: ThreadFactory<Any, Executor>) :
    BaseSchedulerBenchmark<Executor>(param) {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorThreadBuilder, HandlerThreadBuilder)
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier -> scheduler.execute { barrier.countDown() } }
                onEachIteration { n, barrier ->
                    val next = doMath(n)
                    scheduler.execute {
                        sum += doMath(next)
                        barrier.countDown()
                    }
                }
            }
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StartIntrinsicCoroutineBaselineBenchmark(param: ThreadFactory<Any, Executor>) :
    BaseSchedulerBenchmark<Executor>(param) {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorThreadBuilder, HandlerThreadBuilder)
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        suspend fun doMath2x(n: Int, barrier: CyclicCountDownBarrier): Double {
            val next = doMath(n)
            return suspendCoroutine { continuation: Continuation<Double> ->
                scheduler.execute {
                    continuation.resume(doMath(next))
                    barrier.countDown()
                }
            }
        }
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier -> scheduler.execute { barrier.countDown() } }
                onEachIteration { n, barrier ->
                    suspend { doMath2x(n, barrier) }
                        .startCoroutine(
                            Continuation(
                                context = EmptyCoroutineContext,
                                resumeWith = { r: Result<Double> ->
                                    if (r.isSuccess) {
                                        sum += r.getOrNull()!!
                                    } else {
                                        error(r.exceptionOrNull()!!)
                                    }
                                },
                            )
                        )
                }
            }
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ResumeIntrinsicCoroutineBaselineBenchmark(param: ThreadFactory<Any, Executor>) :
    BaseSchedulerBenchmark<Executor>(param) {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorThreadBuilder, HandlerThreadBuilder)
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        val nextInput = SimpleSynchronousState<Double>()
        suspend fun doMathForever(barrier: CyclicCountDownBarrier) {
            while (true) {
                val next = nextInput.awaitValue()
                sum += suspendCoroutine { continuation: Continuation<Double> ->
                    scheduler.execute {
                        continuation.resume(doMath(next))
                        barrier.countDown()
                    }
                }
            }
        }
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    suspend { doMathForever(barrier) }
                        .startCoroutine(
                            Continuation(
                                context = EmptyCoroutineContext,
                                resumeWith = { r: Result<Unit> ->
                                    r.exceptionOrNull()?.let { error(it) }
                                },
                            )
                        )
                    nextInput.putValueOrThrow(0.00)
                }
            }
            onEachIteration { n -> nextInput.putValueOrThrow(doMath(n)) }
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LaunchCoroutineBaselineBenchmark(param: ThreadFactory<Any, CoroutineScope>) :
    BaseSchedulerBenchmark<CoroutineScope>(param) {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() =
            listOf(
                ExecutorServiceCoroutineScopeBuilder,
                HandlerThreadScopeBuilder,
                HandlerThreadImmediateScopeBuilder,
            )
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier -> scheduler.launch { barrier.countDown() } }
                onEachIteration { n, barrier ->
                    val next = doMath(n)
                    scheduler.launch {
                        sum += doMath(next)
                        barrier.countDown()
                    }
                }
            }
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MutableStateFlowBaselineBenchmark(param: ThreadFactory<Any, CoroutineScope>) :
    BaseSchedulerBenchmark<CoroutineScope>(param) {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() =
            listOf(
                ExecutorServiceCoroutineScopeBuilder,
                HandlerThreadScopeBuilder,
                HandlerThreadImmediateScopeBuilder,
            )
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        val state = MutableStateFlow(0.00)
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    scheduler.launch {
                        state.collect { next ->
                            sum += doMath(next)
                            barrier.countDown()
                        }
                    }
                }
            }
            onEachIteration { n -> state.value = doMath(n) }
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleObservableStateBaselineBenchmark(param: ThreadFactory<Any, Executor>) :
    BaseSchedulerBenchmark<Executor>(param) {

    companion object {
        @Parameters(name = "{0}") @JvmStatic fun getDispatchers() = listOf(ExecutorThreadBuilder)
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        val state = SimpleState(0.0)
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    state.listen { next ->
                        scheduler.execute {
                            sum += doMath(next)
                            barrier.countDown()
                        }
                    }
                }
            }
            onEachIteration { n -> state.value = doMath(n) }
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimplePublisherIntrinsicCoroutineBaselineBenchmark(param: ThreadFactory<Any, Executor>) :
    BaseSchedulerBenchmark<Executor>(param) {

    companion object {
        @Parameters(name = "{0}") @JvmStatic fun getDispatchers() = listOf(ExecutorThreadBuilder)
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        val state = SimplePublisherImpl<Double>()
        val stateWatcher = state.asSuspendableObserver(scheduler)
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    scheduler.execute { barrier.countDown() }
                    suspend fun collectLambda(): Nothing {
                        while (true) {
                            val next = stateWatcher.awaitNextValue()
                            sum += doMath(next)
                            barrier.countDown()
                        }
                    }
                    ::collectLambda.startCoroutine(
                        Continuation(context = EmptyCoroutineContext, resumeWith = {})
                    )
                }
            }
            onEachIteration { n -> state.publish(doMath(n)) }
        }
        BlackHole.consume(sum)
    }
}

private sealed interface GenericBaselineBenchmark<T, E : Any>
    where T : WritableEventFactory<E>, T : EventContextProvider<E> {
    val benchmarkRule: ConcurrentBenchmarkRule
    val context: T

    @Test
    fun benchmark_doMathBg() {
        val state = context.createWritableEvent(0.00)
        var sum = 0.0
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    context.read {
                        state.observe { next ->
                            sum += doMath(next)
                            barrier.countDown()
                        }
                    }
                }
            }
            onEachIteration { n -> context.write { state.update(doMath(n)) } }
        }
    }
}

@RunWith(Parameterized::class)
class SimpleGenericBaselineBenchmark(param: ThreadFactory<Any, Executor>) :
    BaseEventBenchmark<Executor, SimpleWritableEventBuilder>(
        param,
        { SimpleWritableEventBuilder(it) },
    ),
    GenericBaselineBenchmark<SimpleWritableEventBuilder, SimpleEvent<*>> {

    companion object {
        @Parameters(name = "{0}") @JvmStatic fun getDispatchers() = listOf(ExecutorThreadBuilder)
    }
}

@RunWith(Parameterized::class)
class FlowGenericBaselineBenchmark(param: ThreadFactory<Any, CoroutineScope>) :
    BaseEventBenchmark<CoroutineScope, FlowWritableEventBuilder>(
        param,
        { FlowWritableEventBuilder(it) },
    ),
    GenericBaselineBenchmark<FlowWritableEventBuilder, Flow<*>> {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorServiceCoroutineScopeBuilder)
    }
}

private fun doMath(num: Number): Double {
    val n = num.toDouble()
    var sum = 0.0
    repeat(100) {
        sum += kotlin.math.sqrt(n + it) / 1.2345
        sum /= kotlin.math.PI
        sum /= kotlin.math.sin(n)
        sum /= kotlin.math.cos(n)
    }
    return sum
}
