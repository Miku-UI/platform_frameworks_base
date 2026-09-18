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
import com.android.app.concurrent.benchmark.base.BaseLooperThreadBenchmark
import com.android.app.concurrent.benchmark.base.ConcurrentBenchmarkRule
import com.android.app.concurrent.benchmark.base.CoroutineLooperThreadBenchmark
import com.android.app.concurrent.benchmark.event.BaseEventBenchmark
import com.android.app.concurrent.benchmark.event.BaseFlowEventBenchmark
import com.android.app.concurrent.benchmark.event.BaseSimpleEventBenchmark
import com.android.app.concurrent.benchmark.event.DirectEvent
import com.android.app.concurrent.benchmark.event.DirectWritableEventBuilder
import com.android.app.concurrent.benchmark.event.DistinctUntilChangedOperator
import com.android.app.concurrent.benchmark.event.EventCombiner
import com.android.app.concurrent.benchmark.event.EventContextProvider
import com.android.app.concurrent.benchmark.event.FilterOperator
import com.android.app.concurrent.benchmark.event.FlowWritableEventBuilder
import com.android.app.concurrent.benchmark.event.IntEventCombiner
import com.android.app.concurrent.benchmark.event.SimpleEvent
import com.android.app.concurrent.benchmark.event.SimpleState
import com.android.app.concurrent.benchmark.event.SimpleWritableEventBuilder
import com.android.app.concurrent.benchmark.event.WritableEventFactory
import com.android.app.concurrent.benchmark.util.ExecutorServiceThreadWithExecutorBuilder
import com.android.app.concurrent.benchmark.util.ExecutorServiceThreadWithExecutorCoroutineDispatcherBuilder
import com.android.app.concurrent.benchmark.util.ThreadBuilder
import com.android.app.concurrent.benchmark.util.allCpuWorkloads
import com.android.app.concurrent.benchmark.util.dbg
import com.android.app.concurrent.benchmark.util.stressCpu
import com.android.app.concurrent.benchmark.util.times
import java.util.concurrent.Executor
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

private sealed interface CombineEventBenchmark<T, E : Any>
    where
        T : WritableEventFactory<E>,
        T : EventContextProvider<E>,
        T : EventCombiner<E>,
        T : DistinctUntilChangedOperator<E>,
        T : FilterOperator<E> {
    val benchmarkRule: ConcurrentBenchmarkRule
    val context: T

    @Test
    fun benchmarkA_2EventsWith1combinedCollector() {
        var combinedVal1 = 0
        val event1 = context.createWritableEvent(0)
        val event2 = context.createWritableEvent(0)
        val combined1 =
            with(context) { combineEvents(event1, event2) { a, b -> a + b }.distinctUntilChanged() }

        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    context.read {
                        combined1.observe {
                            combinedVal1 = it
                            // We should only countDown() when it is an even number, which means
                            // we waited for the state to settle.
                            if (it % 2 == 0) {
                                barrier.countDown()
                            }
                        }
                    }
                }
            }
            onEachIteration { n ->
                context.write {
                    event1.update(n)
                    event2.update(n)
                }
            }
            stateChecker(
                isInExpectedState = { n -> combinedVal1 == n * 2 },
                expectedStr = "combinedVal1 == n * 2",
                expectedCalc = { n -> "$combinedVal1 == $n * 2" },
            )
            afterLastIteration { BlackHole.consume(combinedVal1) }
        }
    }

    @Test
    fun benchmarkB_1stateWith2CombineAnd2Collects() {
        var combinedVal1 = 0
        var combinedVal2 = 0

        val event1 = context.createWritableEvent(0)
        val combined1 =
            with(context) {
                context.combineEvents(event1, event1) { a, b -> a + b }.distinctUntilChanged()
            }
        val combined2 =
            with(context) {
                context.combineEvents(event1, event1) { a, b -> a + b }.distinctUntilChanged()
            }

        benchmarkRule.runBenchmark {
            withBarrier(count = 2) {
                beforeFirstIteration { barrier ->
                    context.read {
                        combined1.observe {
                            combinedVal1 = it
                            // We should only countDown() when it is an even number, which means
                            // we waited for the state to settle.
                            if (it % 2 == 0) {
                                barrier.countDown()
                            }
                        }
                        combined2.observe {
                            combinedVal2 = it
                            // We should only countDown() when it is an even number, which means
                            // we waited for the state to settle.
                            if (it % 2 == 0) {
                                barrier.countDown()
                            }
                        }
                    }
                }
            }
            onEachIteration { n -> context.write { event1.update(n) } }

            stateChecker(
                isInExpectedState = { n -> combinedVal1 == n * 2 && combinedVal2 == n * 2 },
                expectedStr = "combinedVal1 == n * 2 && combinedVal2 == n * 2",
                expectedCalc = { n -> "$combinedVal1 == $n * 2 && $combinedVal2 == $n * 2" },
            )

            afterLastIteration {
                BlackHole.consume(combinedVal1)
                BlackHole.consume(combinedVal2)
            }
        }
    }

    private fun T.twoEventsWithThreeCollectors() {
        var receivedVal1 = 0
        var receivedVal2 = 0
        var combinedVal1 = 0
        val event1 = createWritableEvent(0)
        val event2 = createWritableEvent(0)
        val combined1 = combineEvents(event1, event2) { a, b -> a + b }.distinctUntilChanged()

        benchmarkRule.runBenchmark {
            withBarrier(count = 3) {
                beforeFirstIteration { barrier ->
                    read {
                        event1.observe {
                            receivedVal1 = it
                            barrier.countDown()
                        }
                        event2.observe {
                            receivedVal2 = it
                            barrier.countDown()
                        }
                        combined1.observe {
                            combinedVal1 = it
                            // We should only countDown() when it is an even number, which means
                            // we waited for the state to settle.
                            if (it % 2 == 0) {
                                barrier.countDown()
                            }
                        }
                    }
                }
            }
            onEachIteration { n ->
                write {
                    event1.update(n)
                    event2.update(n)
                }
            }
            stateChecker(
                isInExpectedState = { n ->
                    receivedVal1 == n && receivedVal2 == n && combinedVal1 == n * 2
                },
                expectedStr = "receivedVal1 == n && receivedVal2 == n && combinedVal1 == n * 2",
                expectedCalc = { n ->
                    "$receivedVal1 == $n && $receivedVal2 == $n && $combinedVal1 == n * 2"
                },
            )
            afterLastIteration {
                BlackHole.consume(receivedVal1)
                BlackHole.consume(receivedVal2)
                BlackHole.consume(combinedVal1)
            }
        }
    }

    @Test
    fun benchmarkC_twoEventsWithThreeCollectors() {
        context.twoEventsWithThreeCollectors()
    }

    private fun T.twoEventsWithTwoCombine() {
        val event1 = createWritableEvent(0)
        val event2 = createWritableEvent(0)
        val combined1 = combineEvents(event1, event2) { a, b -> a + b }.distinctUntilChanged()
        val combined1evens = combined1.filter { it % 2 == 0 }
        val combined2 =
            combineEvents(event1, event2, combined1evens) { a, b, c -> a - b + c + 7 }
                .distinctUntilChanged()
        var receivedVal1 = 0
        var receivedVal2 = 0
        var combinedVal1 = 0
        var combinedVal1Evens = 0
        var combinedVal2 = 0

        benchmarkRule.runBenchmark {
            withBarrier(count = 2) {
                beforeFirstIteration { barrier ->
                    read {
                        event1.observe {
                            receivedVal1 = it
                            dbg { "receivedVal1=$receivedVal1" }
                            barrier.countDown()
                        }
                        event2.observe {
                            receivedVal2 = it
                            dbg { "receivedVal2=$receivedVal2" }
                            barrier.countDown()
                        }
                        combined1.observe {
                            // Since it is less predictable how often this is called, do not use
                            // the
                            // countDown() barrier here, and do not use it in the state check
                            // assertion
                            dbg { "combinedVal1=$combinedVal1" }
                            combinedVal1 = it
                        }
                        combined1evens.observe {
                            combinedVal1Evens = it
                            dbg { "combinedVal1Evens=$combinedVal1Evens" }
                        }
                        combined2.observe {
                            combinedVal2 = it
                            dbg { "combinedVal2=$combinedVal2" }
                        }
                    }
                }
            }
            onEachIteration { n ->
                write {
                    event1.update(n)
                    event2.update(n)
                }
            }
            stateChecker(
                isInExpectedState = { n -> receivedVal1 == n && receivedVal2 == n },
                expectedStr = "receivedVal1 == n && receivedVal2 == n",
                expectedCalc = { n -> "$receivedVal1 == $n && $receivedVal2 == $n" },
            )
            afterLastIteration {
                BlackHole.consume(receivedVal1)
                BlackHole.consume(receivedVal2)
                BlackHole.consume(combinedVal1)
                BlackHole.consume(combinedVal1Evens)
                BlackHole.consume(combinedVal2)
            }
        }
    }

    @Test
    fun benchmarkD_twoEventsWithTwoCombine() {
        context.twoEventsWithTwoCombine()
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleCombineEventBenchmark(param: ThreadBuilder<Executor>) :
    BaseSimpleEventBenchmark(param),
    CombineEventBenchmark<SimpleWritableEventBuilder, SimpleEvent<*>> {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getParameters() = listOf(ExecutorServiceThreadWithExecutorBuilder)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlowCombineEventBenchmark(param: ThreadBuilder<CoroutineScope>) :
    BaseFlowEventBenchmark(param), CombineEventBenchmark<FlowWritableEventBuilder, Flow<*>> {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getParameters() = listOf(ExecutorServiceThreadWithExecutorCoroutineDispatcherBuilder)
    }
}

sealed interface Order

private object RandomOrder : Order {
    override fun toString() = "Random"
}

private object SequentialOrder : Order {
    override fun toString() = "Sequential"
}

private sealed interface HighArityCombineEventBenchmark<T, E : Any>
    where T : WritableEventFactory<E>, T : EventContextProvider<E>, T : IntEventCombiner<E> {
    val benchmarkRule: ConcurrentBenchmarkRule
    val context: T
    val randomUpdates: Boolean

    /**
     * Tests the performance of combining a high number of StateFlows. This mimics a pattern found
     * in SystemUI where many states are combined to derive a single UI-related state.
     */
    @Test
    fun benchmark_highArityCombine() {
        val numEvents = 4
        val sourceEvents = List(numEvents) { context.createWritableEvent(0) }
        val sourceEventsArray = sourceEvents.toTypedArray()
        val combinedEvents =
            context.combineIntEvents(sourceEvents) { values ->
                val sum = values.sum()
                dbg { "sum = $sum" }
                sum
            }
        var result = 0
        val rand = Random(0)
        benchmarkRule.runBenchmark {
            withBarrier(count = 1) {
                beforeFirstIteration { barrier ->
                    context.read {
                        combinedEvents.observe {
                            dbg { "combineIntEvents it=$it" }
                            // Only countdown when the sum is a multiple of the number of source
                            // events, which indicates events were incremented as many times as
                            // there are `numEvents` (whether that was done in a random order or
                            // sequential).
                            if (it % numEvents == 0) {
                                result = it
                                barrier.countDown()
                            }
                        }
                    }
                }
            }

            onEachIteration { n ->
                context.write {
                    repeat(sourceEventsArray.size) {
                        // Randomly increment values in the source event list. This breaks the
                        // optimization in the flow combine operator where all sources are given
                        // a chance to emit before the transform operator is called.
                        val index = if (randomUpdates) rand.nextInt(numEvents) else it
                        val event = sourceEventsArray[index]
                        val curValue = event.current()
                        event.update(curValue + 1)
                    }
                }
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
class DirectHighArityCombineEventBenchmark(param: ThreadBuilder<Executor>, order: Order) :
    BaseEventBenchmark<Executor, DirectWritableEventBuilder>(
        param,
        { DirectWritableEventBuilder(it) },
    ),
    HighArityCombineEventBenchmark<DirectWritableEventBuilder, DirectEvent<*>> {

    override val randomUpdates: Boolean = order == RandomOrder

    companion object {
        @Parameters(name = "{0}:order={1}")
        @JvmStatic
        fun getParameters() =
            listOf(ExecutorServiceThreadWithExecutorBuilder) * listOf(RandomOrder, SequentialOrder)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleHighArityCombineEventBenchmark(param: ThreadBuilder<Executor>, order: Order) :
    BaseEventBenchmark<Executor, SimpleWritableEventBuilder>(
        param,
        { SimpleWritableEventBuilder(it) },
    ),
    HighArityCombineEventBenchmark<SimpleWritableEventBuilder, SimpleEvent<*>> {
    override val randomUpdates: Boolean = order == RandomOrder

    companion object {
        @Parameters(name = "{0}:order={1}")
        @JvmStatic
        fun getParameters() =
            listOf(ExecutorServiceThreadWithExecutorBuilder) * listOf(RandomOrder, SequentialOrder)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlowHighArityCombineEventBenchmark(param: ThreadBuilder<CoroutineScope>, order: Order) :
    BaseEventBenchmark<CoroutineScope, FlowWritableEventBuilder>(
        param,
        { FlowWritableEventBuilder(it) },
    ),
    HighArityCombineEventBenchmark<FlowWritableEventBuilder, Flow<*>> {
    override val randomUpdates: Boolean = order == RandomOrder

    companion object {
        @Parameters(name = "{0}:order={1}")
        @JvmStatic
        fun getParameters() =
            listOf(ExecutorServiceThreadWithExecutorCoroutineDispatcherBuilder) *
                listOf(RandomOrder, SequentialOrder)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LooperCombineSingleThreadBenchmark(val consumeCpuIterations: Int) :
    BaseLooperThreadBenchmark() {

    companion object {
        @Parameters(name = "mainWorkload={0}") @JvmStatic fun getParameters() = allCpuWorkloads
    }

    interface BenchmarkContext : HandlerSynchronousContext {
        val consumeCpu: () -> Unit
        val a: SimpleState<Boolean>
        val b: SimpleState<Boolean>
        val c: SimpleState<Boolean>

        fun endMeasurement() {
            if (state.keepRunning()) {
                a.value = false
                b.value = false
                c.value = false
                consumeCpu()
                handler.post { a.value = true }
                handler.post { b.value = true }
                handler.post { c.value = true }
            } else {
                stopBenchmark()
            }
        }
    }

    class BenchmarkContextImpl(
        threadCtx: HandlerSynchronousContext,
        override val consumeCpu: () -> Unit,
    ) : BenchmarkContext, HandlerSynchronousContext by threadCtx {
        override val a = SimpleState(false)
        override val b = SimpleState(false)
        override val c = SimpleState(false)
    }

    @Test
    fun benchmark_listen3_direct() {
        var sum = 0.0
        measure {
            val ctx = BenchmarkContextImpl(this) { sum += stressCpu(consumeCpuIterations) }
            ctx.a.listen { a -> if (a && ctx.b.value && ctx.c.value) ctx.endMeasurement() }
            ctx.b.listen { b -> if (ctx.a.value && b && ctx.c.value) ctx.endMeasurement() }
            ctx.c.listen { c -> if (ctx.a.value && ctx.b.value && c) ctx.endMeasurement() }
            ctx.endMeasurement()
        }
        BlackHole.consume(sum)
    }

    @Test
    fun benchmark_listen3_deferred() {
        var sum = 0.0
        measure {
            val ctx = BenchmarkContextImpl(this) { sum += stressCpu(consumeCpuIterations) }
            ctx.a.listen { a ->
                handler.post { if (a && ctx.b.value && ctx.c.value) ctx.endMeasurement() }
            }
            ctx.b.listen { b ->
                handler.post { if (ctx.a.value && b && ctx.c.value) ctx.endMeasurement() }
            }
            ctx.c.listen { c ->
                handler.post { if (ctx.a.value && ctx.b.value && c) ctx.endMeasurement() }
            }
            ctx.endMeasurement()
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CoroutineCombineSingleThreadBenchmark(val mainWorkload: Int) :
    CoroutineLooperThreadBenchmark() {

    companion object {
        @Parameters(name = "mainWorkload={0}") @JvmStatic fun getParameters() = allCpuWorkloads
    }

    interface BenchmarkContext : CoroutineScopeContext {
        val consumeCpu: () -> Unit
        val a: MutableStateFlow<Boolean>
        val b: MutableStateFlow<Boolean>
        val c: MutableStateFlow<Boolean>

        suspend fun endMeasurement() {
            if (state.keepRunning()) {
                a.value = false
                b.value = false
                c.value = false
                consumeCpu()
                handler.post { a.value = true }
                handler.post { b.value = true }
                handler.post { c.value = true }
            } else {
                stopBenchmark()
            }
        }
    }

    class BenchmarkContextImpl(
        context: CoroutineScopeContext,
        override val consumeCpu: () -> Unit,
    ) : BenchmarkContext, CoroutineScopeContext by context {
        override val a = MutableStateFlow(false)
        override val b = MutableStateFlow(false)
        override val c = MutableStateFlow(false)
    }

    @Test
    fun benchmark_collect3_direct() {
        var sum = 0.0
        measureCoroutine { scope ->
            val ctx = BenchmarkContextImpl(this) { sum += stressCpu(mainWorkload) }
            ctx.a
                .filter { a -> a && ctx.b.value && ctx.c.value }
                .onEach { ctx.endMeasurement() }
                .launchIn(scope)
            ctx.b
                .filter { b -> ctx.a.value && b && ctx.c.value }
                .onEach { ctx.endMeasurement() }
                .launchIn(scope)
            ctx.c
                .filter { c -> ctx.a.value && ctx.b.value && c }
                .onEach { ctx.endMeasurement() }
                .launchIn(scope)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { ctx.endMeasurement() }
        }
        BlackHole.consume(sum)
    }

    @Test
    fun benchmark_collect1_combine() {
        var sum = 0.0
        measureCoroutine { scope ->
            val ctx = BenchmarkContextImpl(this) { sum += stressCpu(mainWorkload) }
            val abc = combine(ctx.a, ctx.b, ctx.c) { a, b, c -> a && b && c }
            abc.filter { it }.onEach { ctx.endMeasurement() }.launchIn(scope)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { ctx.endMeasurement() }
        }
        BlackHole.consume(sum)
    }

    @Test
    fun benchmark_collect1_combineChained1() {
        var sum = 0.0
        measureCoroutine { scope ->
            val ctx = BenchmarkContextImpl(this) { sum += stressCpu(mainWorkload) }
            val ab = combine(ctx.a, ctx.b) { a, b -> a && b }
            val abc = combine(ab, ctx.c) { ab, c -> ab && c }
            abc.filter { it }.onEach { ctx.endMeasurement() }.launchIn(scope)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { ctx.endMeasurement() }
        }
        BlackHole.consume(sum)
    }

    @Test
    fun benchmark_collect1_combineChained2() {
        var sum = 0.0
        measureCoroutine { scope ->
            val ctx = BenchmarkContextImpl(this) { sum += stressCpu(mainWorkload) }
            val ab = combine(ctx.a, ctx.b) { a, b -> a && b }
            val bc = combine(ctx.b, ctx.c) { b, c -> b && c }
            val abc = combine(ab, bc) { ab, bc -> ab && bc }
            abc.filter { it }.onEach { ctx.endMeasurement() }.launchIn(scope)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { ctx.endMeasurement() }
        }
        BlackHole.consume(sum)
    }
}
