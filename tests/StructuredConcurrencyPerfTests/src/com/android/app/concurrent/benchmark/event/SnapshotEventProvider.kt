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
package com.android.app.concurrent.benchmark.event

import androidx.compose.runtime.MutableState as MutableSnapshotState
import androidx.compose.runtime.State as SnapshotState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import com.android.app.concurrent.benchmark.util.ThreadFactory
import java.io.Closeable
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

// NOTE: The Snapshot APIs used here would typically not be called directly. This benchmark is for
// stress testing snapshot updates and observations. It's not meant to portray a realistic scenario.

typealias SnapshotStateBoxIn<T> = EventBox<T, SnapshotState<*>>

typealias SnapshotStateBoxOut<T> = EventBox<T, SnapshotState<T>>

typealias MutableSnapshotStateBoxOut<T> = EventBox<T, MutableSnapshotState<T>>

private class SnapshotStateBoxImpl<T, E : SnapshotState<T>>(event: E) :
    AbstractEventBox<T, E>(event)

private fun <T> SnapshotStateBoxIn<T>.unbox(): SnapshotState<T> =
    (this as SnapshotStateBoxImpl<T, *>).event

class SnapshotReadContext : ReadContext<SnapshotState<*>> {
    override fun <T> EventBox<T, SnapshotState<*>>.observe(block: (T) -> Unit) {
        block(unbox().value)
    }
}

class SnapshotWriteContext : WriteContext<SnapshotState<*>> {
    override fun <T> EventBox<T, SnapshotState<*>>.update(value: T) {
        (unbox() as MutableSnapshotState<T>).value = value
    }

    override fun <T> EventBox<T, SnapshotState<*>>.current(): T {
        return (unbox() as MutableSnapshotState<T>).value
    }
}

abstract class SnapshotWritableEventBuilder() :
    EventContext,
    WritableEventFactory<MutableSnapshotState<*>>,
    EventContextProvider<SnapshotState<*>>,
    EventCombiner<SnapshotState<*>>,
    IntEventCombiner<SnapshotState<*>>,
    MapOperator<SnapshotState<*>> {

    val openResources = mutableListOf<Closeable>()

    val stateReader = SnapshotReadContext()
    val stateWriter = SnapshotWriteContext()

    override fun <T> createWritableEvent(value: T): MutableSnapshotStateBoxOut<T> {
        return SnapshotStateBoxImpl(mutableStateOf(value))
    }

    override fun read(block: ReadContext<SnapshotState<*>>.() -> Unit): NoopAutoCloseable {
        synchronized(openResources) {
            openResources += startObservation { with(stateReader) { block() } }
        }
        return NoopAutoCloseable
    }

    override fun write(block: WriteContext<SnapshotState<*>>.() -> Unit) {
        Snapshot.withMutableSnapshot { with(stateWriter) { block() } }
    }

    override fun <T1, T2, T3> combineEvents(
        a: EventBox<T1, SnapshotState<*>>,
        b: EventBox<T2, SnapshotState<*>>,
        transform: (T1, T2) -> T3,
    ): EventBox<T3, SnapshotState<T3>> {
        return SnapshotStateBoxImpl(derivedStateOf { transform(a.unbox().value, b.unbox().value) })
    }

    override fun <T1, T2, T3, T4> combineEvents(
        a: EventBox<T1, SnapshotState<*>>,
        b: EventBox<T2, SnapshotState<*>>,
        c: EventBox<T3, SnapshotState<*>>,
        transform: (T1, T2, T3) -> T4,
    ): EventBox<T4, SnapshotState<T4>> {
        return SnapshotStateBoxImpl(
            derivedStateOf { transform(a.unbox().value, b.unbox().value, c.unbox().value) }
        )
    }

    override fun <R> combineIntEvents(
        events: Iterable<EventBox<Int, SnapshotState<*>>>,
        transform: (Array<Int>) -> R,
    ): SnapshotStateBoxOut<R> {
        val stateArray = events.map { it.unbox() }.toList().toTypedArray()
        return SnapshotStateBoxImpl(
            derivedStateOf {
                val intArray = stateArray.map { it.value }.toTypedArray()
                transform(intArray)
            }
        )
    }

    override fun dispose() {
        synchronized(openResources) {
            openResources.forEach { it.close() }
            openResources.clear()
        }
    }

    override fun <A, B> EventBox<A, SnapshotState<*>>.map(
        transform: (A) -> B
    ): SnapshotStateBoxOut<B> {
        return SnapshotStateBoxImpl(derivedStateOf { transform(unbox().value) })
    }

    abstract fun startObservation(block: () -> Unit): Closeable
}

class SnapshotWritableEventExecutorBuilder(val executor: Executor) :
    SnapshotWritableEventBuilder() {
    override fun startObservation(block: () -> Unit): Closeable {
        return SnapshotStateExecutorObserver(executor, block).start()
    }
}

class SnapshotWritableEventCoroutineBuilder(val scope: CoroutineScope) :
    SnapshotWritableEventBuilder() {
    override fun startObservation(block: () -> Unit): Closeable {
        return SnapshotStateCoroutineObserver(scope) { with(stateReader) { block() } }.start()
    }
}

private class SnapshotStateExecutorObserver(val executor: Executor, private val block: () -> Unit) {
    private val observer =
        SnapshotStateObserver(onChangedExecutor = { callback -> executor.execute(callback) })

    private val onValueChanged = { _: Unit -> observeBlock() }

    private fun observeBlock() {
        observer.observeReads(
            // Scope would only need to be used if we wanted to pass different data to
            // onValueChangedInBlock
            scope = Unit,
            onValueChangedForScope = onValueChanged,
            block = block,
        )
    }

    fun start(): Closeable {
        executor.execute {
            observer.start()
            observeBlock()
        }
        return Closeable { observer.stop() }
    }
}

private class SnapshotStateCoroutineObserver(
    val scope: CoroutineScope,
    private val block: () -> Unit,
) {
    private val changeCallbacks = Channel<() -> Unit>(Channel.UNLIMITED)

    private val observer = SnapshotStateObserver { callback -> changeCallbacks.trySend(callback) }

    private val onValueChanged = { _: Unit -> observeBlock() }

    private fun observeBlock() {
        observer.observeReads(
            // Scope would only need to be used if we wanted to pass different data to
            // onValueChangedInBlock
            scope = Unit,
            onValueChangedForScope = onValueChanged,
            block = block,
        )
    }

    fun start(): Closeable {
        val job =
            scope.launch {
                observer.start()
                try {
                    observeBlock()

                    // Process changes until cancelled:
                    for (callback in changeCallbacks) {
                        callback()
                    }
                } finally {
                    observer.stop()
                }
            }
        return Closeable { job.cancel() }
    }
}

abstract class BaseExecutorSnapshotEventBenchmark(threadParam: ThreadFactory<Any, Executor>) :
    BaseEventBenchmark<Executor, SnapshotWritableEventExecutorBuilder>(
        threadParam,
        { SnapshotWritableEventExecutorBuilder(it) },
    )

abstract class BaseCoroutineSnapshotEventBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>
) :
    BaseEventBenchmark<CoroutineScope, SnapshotWritableEventCoroutineBuilder>(
        threadParam,
        { SnapshotWritableEventCoroutineBuilder(it) },
    )
