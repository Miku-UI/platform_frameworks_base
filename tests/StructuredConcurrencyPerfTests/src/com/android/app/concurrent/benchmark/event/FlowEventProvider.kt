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

import com.android.app.concurrent.benchmark.util.ThreadFactory
import com.android.systemui.util.kotlin.sample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

typealias FlowBoxIn<T> = EventBox<T, Flow<*>>

typealias FlowBoxOut<T> = EventBox<T, Flow<T>>

typealias MutableStateFlowBoxOut<T> = EventBox<T, MutableStateFlow<T>>

private class FlowBoxImpl<out T, out R : Flow<T>>(flow: R) : AbstractEventBox<T, R>(flow)

private fun <T> FlowBoxIn<T>.unbox(): Flow<T> = (this as FlowBoxImpl<T, *>).event

class FlowWritableEventBuilder(val scope: CoroutineScope) :
    EventContext,
    WritableEventFactory<MutableStateFlow<*>>,
    EventContextProvider<Flow<*>>,
    EventCombiner<Flow<*>>,
    IntEventCombiner<Flow<*>>,
    DistinctUntilChangedOperator<Flow<*>>,
    FilterOperator<Flow<*>>,
    MapOperator<Flow<*>>,
    SampleOperator<Flow<*>> {

    val writeContext = FlowWriteContext()

    override fun <T> createWritableEvent(value: T): MutableStateFlowBoxOut<T> {
        return FlowBoxImpl(MutableStateFlow(value))
    }

    override fun <T1, T2, T3> combineEvents(
        a: FlowBoxIn<T1>,
        b: FlowBoxIn<T2>,
        transform: (T1, T2) -> T3,
    ): EventBox<T3, Flow<T3>> {
        return FlowBoxImpl(combine(a.unbox(), b.unbox(), transform))
    }

    override fun <T1, T2, T3, T4> combineEvents(
        a: FlowBoxIn<T1>,
        b: FlowBoxIn<T2>,
        c: FlowBoxIn<T3>,
        transform: (T1, T2, T3) -> T4,
    ): FlowBoxOut<T4> {
        return FlowBoxImpl(combine(a.unbox(), b.unbox(), c.unbox(), transform))
    }

    override fun <R> combineIntEvents(
        events: Iterable<FlowBoxIn<Int>>,
        transform: (Array<Int>) -> R,
    ): FlowBoxOut<R> {
        val flows = events.map { it.unbox() }
        return FlowBoxImpl(combine<Int, R>(flows, transform))
    }

    override fun read(block: ReadContext<Flow<*>>.() -> Unit): AutoCloseable {
        val job =
            scope.launch {
                val readContext = FlowReadContext(this)
                readContext.block()
            }
        return AutoCloseable { job.cancel() }
    }

    override fun write(block: WriteContext<Flow<*>>.() -> Unit) {
        writeContext.block()
    }

    override fun <T> FlowBoxIn<T>.distinctUntilChanged(): FlowBoxOut<T> {
        return FlowBoxImpl(unbox().distinctUntilChanged())
    }

    override fun <T> FlowBoxIn<T>.filter(predicate: (T) -> Boolean): FlowBoxOut<T> {
        return FlowBoxImpl(unbox().filter(predicate))
    }

    override fun <A, B> FlowBoxIn<A>.map(transform: (A) -> B): FlowBoxOut<B> {
        return FlowBoxImpl(unbox().map(transform))
    }

    override fun <A, B, C> FlowBoxIn<A>.sample(
        other: FlowBoxIn<B>,
        transform: (A, B) -> C,
    ): FlowBoxOut<C> {
        return FlowBoxImpl(unbox().sample(other.unbox(), transform))
    }
}

class FlowReadContext(val scope: CoroutineScope) : ReadContext<Flow<*>> {
    override fun <T> FlowBoxIn<T>.observe(block: (T) -> Unit) {
        scope.launch { unbox().collect(block) }
    }
}

class FlowWriteContext : WriteContext<Flow<*>> {
    override fun <T> FlowBoxIn<T>.update(value: T) {
        (unbox() as MutableStateFlow<T>).value = value
    }

    override fun <T> FlowBoxIn<T>.current(): T {
        return (unbox() as MutableStateFlow<T>).value
    }
}

abstract class BaseFlowEventBenchmark(threadParam: ThreadFactory<Any, CoroutineScope>) :
    BaseEventBenchmark<CoroutineScope, FlowWritableEventBuilder>(
        threadParam,
        { FlowWritableEventBuilder(it) },
    )
