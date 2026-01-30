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

import com.android.app.concurrent.benchmark.base.BaseSchedulerBenchmark
import com.android.app.concurrent.benchmark.util.ThreadFactory
import com.android.app.concurrent.benchmark.util.dbg
import org.junit.After
import org.junit.Before

sealed interface EventBox<out T, out E : Any> {
    val event: E
}

abstract class AbstractEventBox<out T, out E : Any>(override val event: E) : EventBox<T, E>

interface ReadContext<in E : Any> {
    fun <T> EventBox<T, E>.observe(block: (T) -> Unit)
}

interface WriteContext<E : Any> {
    fun <T> EventBox<T, E>.update(value: T)

    fun <T> EventBox<T, E>.current(): T
}

/** Defines the core capability of creating a source state. */
interface WritableEventFactory<out E : Any> {
    fun <T> createWritableEvent(value: T): EventBox<T, E>
}

/** Defines the capability of combining two or three states into a derived state. */
interface EventCombiner<E : Any> {
    fun <T1, T2, T3> combineEvents(
        a: EventBox<T1, E>,
        b: EventBox<T2, E>,
        transform: (T1, T2) -> T3,
    ): EventBox<T3, E>

    fun <T1, T2, T3, T4> combineEvents(
        a: EventBox<T1, E>,
        b: EventBox<T2, E>,
        c: EventBox<T3, E>,
        transform: (T1, T2, T3) -> T4,
    ): EventBox<T4, E>
}

/** Defines the specialized capability of combining a list of integer states. */
interface IntEventCombiner<E : Any> {
    fun <R> combineIntEvents(
        events: Iterable<EventBox<Int, E>>,
        transform: (Array<Int>) -> R,
    ): EventBox<R, E>
}

object NoopAutoCloseable : AutoCloseable {
    override fun close() {}
}

/** Defines the capability of providing read/write contexts and managing lifecycle. */
interface EventContext {
    fun dispose() {}
}

/** Defines the capability of providing read/write contexts and managing lifecycle. */
interface EventContextProvider<E : Any> {
    fun read(block: ReadContext<E>.() -> Unit): AutoCloseable

    fun write(block: WriteContext<E>.() -> Unit)
}

interface MapOperator<E : Any> {
    fun <A, B> EventBox<A, E>.map(transform: (A) -> B): EventBox<B, E>
}

interface SampleOperator<E : Any> {
    fun <A, B, C> EventBox<A, E>.sample(
        other: EventBox<B, E>,
        transform: (A, B) -> C,
    ): EventBox<C, E>
}

interface FilterOperator<E : Any> {
    fun <T> EventBox<T, E>.filter(predicate: (T) -> Boolean): EventBox<T, E>
}

interface DistinctUntilChangedOperator<E : Any> {
    fun <T> EventBox<T, E>.distinctUntilChanged(): EventBox<T, E>
}

abstract class BaseEventBenchmark<T : Any, P : EventContext>(
    threadParam: ThreadFactory<Any, T>,
    val build: (T) -> P,
) : BaseSchedulerBenchmark<T>(threadParam) {

    private lateinit var _context: P
    val context: P
        get() = _context

    @Before
    fun setupEventContext() {
        _context = build(scheduler)
    }

    @After
    fun disposeEventContext() {
        dbg { "dispose" }
        _context.dispose()
    }
}
