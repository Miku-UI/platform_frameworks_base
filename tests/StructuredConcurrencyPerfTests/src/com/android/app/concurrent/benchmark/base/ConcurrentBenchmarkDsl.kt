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
package com.android.app.concurrent.benchmark.base

import com.android.app.concurrent.benchmark.base.StateChecker.NoOpStateChecker
import com.android.app.concurrent.benchmark.util.BARRIER_TIMEOUT_MILLIS
import com.android.app.concurrent.benchmark.util.CyclicCountDownBarrier
import com.android.app.concurrent.benchmark.util.dbg
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

@DslMarker annotation class ConcurrentBenchmarkDslMarker

/**
 * A property delegate that allows a variable to be assigned only once. Attempting to set it a
 * second time will throw an IllegalStateException.
 */
private class SetOnceDelegate<T>(defaultValue: T) : ReadWriteProperty<Any, T> {

    private var _value: T = defaultValue
    private var alreadySetOnce = false

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        return _value
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        if (alreadySetOnce) {
            error("Property '${property.name}' can only be set once.")
        } else {
            _value = value
            alreadySetOnce = true
        }
    }
}

@ConcurrentBenchmarkDslMarker
class ConcurrentBenchmarkDsl(build: ConcurrentBenchmarkDsl.() -> Unit) {

    private var beforeFirstIteration: (() -> Unit) by SetOnceDelegate { /* do nothing */ }

    private var withBarrier = mutableListOf<WithBarrierDsl>()

    private var onEachIteration: ((Int) -> Unit) by SetOnceDelegate { n -> /* do nothing */ }

    private var afterLastIteration: (() -> Unit) by SetOnceDelegate { /* do nothing */ }

    // Checks whether we are in the expected state after all barrier parties await and the
    // synchronization point is reached
    private var stateChecker: StateChecker by SetOnceDelegate(NoOpStateChecker)

    init {
        dbg { "build" }
        build()
    }

    /**
     * Setup step that runs before the first iteration and before any calls to
     * [WithBarrierDsl.beforeFirstIteration]. This will not be assigned a party for the sake of the
     * `CyclicBarrier`. This also means that if this block schedules bg work, the main
     * [onEachIteration] block may start before the initial bg work scheduled here is completed.
     */
    fun beforeFirstIteration(block: () -> Unit) {
        beforeFirstIteration = block
    }

    /**
     * Blocking work to run on the main thread on each iteration. The given block is passed the
     * current iteration count, starting from 1. The first iteration is called with `n=1`, the
     * second with `n=2`, etc.
     *
     * Internally, the main thread is implicitly assigned one party for the `CyclicBarrier`, which
     * is shared with any bg thread that registers itself using [withBarrier]. After the given block
     * runs for an iteration, the benchmark test will call `await` on the barrier. If no bg work is
     * registered, the party count is `1`, meaning `await` returns immediately.
     */
    internal fun onEachIteration(block: (n: Int) -> Unit) {
        onEachIteration = block
    }

    /**
     * Creates a new [CyclicCountDownBarrier] with the given count.
     * [CyclicCountDownBarrier.countDown] should only ever be called from one thread. The given
     * block can use [WithBarrierDsl.beforeFirstIteration] or [WithBarrierDsl.onEachIteration] to
     * schedule additional work.
     */
    fun withBarrier(count: Int, block: WithBarrierDsl.() -> Unit) {
        withBarrier += WithBarrierDsl(count).apply { block() }
    }

    /** Optional cleanup steps. Also useful for calling [androidx.benchmark.BlackHole.consume]. */
    fun afterLastIteration(block: () -> Unit) {
        afterLastIteration = block
    }

    /** Optional state validation to be run after each iteration. */
    fun stateChecker(
        isInExpectedState: (Int) -> Boolean,
        expectedStr: String,
        expectedCalc: (Int) -> String,
    ) {
        stateChecker = StateChecker(isInExpectedState, expectedStr, expectedCalc)
    }

    /** Runs the benchmark */
    fun measure(rule: ConcurrentBenchmarkRule) {
        dbg { "measure" }
        // Each thread should call `CyclicBarrier.await()` when all work it expected to do is
        // completed, including the main thread (named "BenchmarkRunner"), so create a
        // `CyclicBarrier` with a party count matching the number of threads (which should be
        // 1 + the number of times `withBarrier` was called)
        val barrier = CyclicBarrier(withBarrier.size + 1) // +1 for the main thread
        val barrierTasks = withBarrier.map { it.build(barrier) }
        val stateChecker = stateChecker
        rule.measureRepeated(
            beforeFirstIteration = {
                barrierTasks.forEach { it.beforeFirstIteration() }
                beforeFirstIteration.invoke()
                try {
                    // wait for all bg setup to be completed
                    barrier.await(BARRIER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                } catch (e: TimeoutException) {
                    fail("Timeout while awaiting initial setup")
                    throw e
                }
            },
            onEachIteration = { n: Int ->
                assertEquals(
                    "Barrier should have 0 parties awaiting before mainBlock runs",
                    0,
                    barrier.numberWaiting,
                )
                barrierTasks.forEach { it.onEachIteration(n) }
                dbg { "mainBlock n=$n" }
                onEachIteration(n)
                try {
                    barrier.await(BARRIER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                } catch (e: TimeoutException) {
                    fail("Timeout while awaiting iteration #$n")
                    throw e
                }
                barrierTasks.forEach { it.afterEachIteration(n) }
                if (!stateChecker.isInExpectedState(n)) {
                    var message = "Benchmark is not in expected state."
                    message += " Expected (${stateChecker.expectedStr}) == true, "
                    message +=
                        "but it evaluated to false instead (${stateChecker.expectedCalc(n)})."
                    fail(message)
                }
            },
            afterLastIteration = afterLastIteration,
        )
    }
}

@ConcurrentBenchmarkDslMarker
class WithBarrier(
    val beforeFirstIteration: () -> Unit,
    val onEachIteration: (Int) -> Unit,
    val afterEachIteration: (Int) -> Unit,
)

@ConcurrentBenchmarkDslMarker
class WithBarrierDsl(count: Int) {

    private val barrierBuilder = CyclicCountDownBarrier.Builder(count)

    fun build(barrier: CyclicBarrier): WithBarrier {
        val barrier = barrierBuilder.build(barrier)
        return WithBarrier(
            beforeFirstIteration = { beforeFirstIteration(barrier) },
            onEachIteration = { n: Int -> onEachIteration(n, barrier) },
            afterEachIteration = afterEachIteration,
        )
    }

    private var beforeFirstIteration:
        (CyclicCountDownBarrier) -> Unit by SetOnceDelegate { /* do nothing */ }

    private var onEachIteration: (Int, CyclicCountDownBarrier) -> Unit by SetOnceDelegate {
        n,
        barrier ->
        /* do nothing */
    }

    private var afterEachIteration: (Int) -> Unit by SetOnceDelegate { /* do nothing */ }

    /**
     * Setup step that runs before the [ConcurrentBenchmarkDsl.beforeFirstIteration] is called and
     * before the first iteration. This scope's [CyclicCountDownBarrier.countDown] must reach 0
     * * for the test to continue on each iteration (including continuing to the first iteration).
     */
    fun beforeFirstIteration(block: (CyclicCountDownBarrier) -> Unit) {
        beforeFirstIteration = block
    }

    /**
     * Runs once on each iteration before the main [ConcurrentBenchmarkDsl.onEachIteration] block is
     * called. This scope's [CyclicCountDownBarrier.countDown] must reach 0 for the test to continue
     * on each iteration (including continuing to the first iteration).
     */
    fun onEachIteration(block: (Int, CyclicCountDownBarrier) -> Unit) {
        onEachIteration = block
    }

    fun afterEachIteration(block: (Int) -> Unit) {
        afterEachIteration = block
    }
}

@ConcurrentBenchmarkDslMarker
open class StateChecker(
    val isInExpectedState: (Int) -> Boolean,
    val expectedStr: String,
    val expectedCalc: (Int) -> String,
) {
    object NoOpStateChecker :
        StateChecker(
            isInExpectedState = { n -> true },
            expectedStr = "",
            expectedCalc = { n -> "" },
        )
}
