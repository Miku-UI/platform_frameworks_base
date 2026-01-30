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
import com.android.app.concurrent.benchmark.util.dbg
import com.android.app.concurrent.benchmark.util.instanceName
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.fail

typealias SimpleEventBoxIn<T> = EventBox<T, SimpleEvent<*>>

typealias SimpleObservableBoxOut<T> = EventBox<T, SimpleEvent<T>>

typealias SimpleObservableStateBoxOut<T> = EventBox<T, SimpleState<T>>

private class SimpleEventBoxImpl<T, R : SimpleEvent<T>>(event: R) : AbstractEventBox<T, R>(event)

private fun <T> SimpleEventBoxIn<T>.unbox(): SimpleEvent<T> =
    (this as SimpleEventBoxImpl<T, *>).event

fun interface EventListener<in T> {
    fun notify(value: T)
}

interface SimpleEvent<out T> {
    fun listen(listener: EventListener<T>): AutoCloseable
}

interface SimplePublisher<in T> {
    fun publish(value: T)
}

open class SimpleEventImpl<T> : SimpleEvent<T> {

    private val listeners = mutableListOf<EventListener<T>>()

    @Synchronized
    override fun listen(listener: EventListener<T>): AutoCloseable {
        dbg { "listeners.add(${listener.instanceName()})" }
        listeners.add(listener)
        return AutoCloseable {
            synchronized(listeners) {
                dbg { "listeners.remove(${listener.instanceName()})" }
                listeners.remove(listener)
            }
        }
    }

    @Synchronized
    protected fun notifyAll(value: T) {
        dbg { "notifyAll($value) {{{" }
        listeners.forEach { listener ->
            dbg { "listener.notify(${listener.instanceName()}) -> $value" }
            listener.notify(value)
        }
        dbg { "}}} notifyAll($value)" }
    }
}

class SimplePublisherImpl<T> : SimpleEventImpl<T>(), SimplePublisher<T> {
    @Synchronized
    override fun publish(value: T) {
        dbg { "publish($value)" }
        notifyAll(value)
    }
}

class SimpleState<T>(initialValue: T) : SimpleEventImpl<T>() {
    var value: T = initialValue
        @Synchronized
        set(newValue) {
            dbg { "set($field -> $newValue)" }
            if (field != newValue) {
                field = newValue
                notifyAll(newValue)
            }
        }
        @Synchronized get

    @Synchronized
    override fun listen(listener: EventListener<T>): AutoCloseable {
        dbg { "listener.notify(${listener.instanceName()}) -> $value" }
        listener.notify(value)
        return super.listen(listener)
    }
}

class Symbol(@JvmField val symbol: String) {
    override fun toString(): String = "<$symbol>"
}

val UNINITIALIZED: Any? = Symbol("UNINITIALIZED")

fun <T1, T2, R> combineSimpleEvents(
    a: SimpleEvent<T1>,
    b: SimpleEvent<T2>,
    transform: (T1, T2) -> R,
): SimpleEvent<R> {
    return combineSimpleEventsInternal(arrayOf(a, b), { null }) { args: Array<*> ->
        @Suppress("UNCHECKED_CAST") transform(args[0] as T1, args[1] as T2)
    }
}

fun <T1, T2, T3, R> combineSimpleEvents(
    a: SimpleEvent<T1>,
    b: SimpleEvent<T2>,
    c: SimpleEvent<T3>,
    transform: (T1, T2, T3) -> R,
): SimpleEvent<R> {
    return combineSimpleEventsInternal(arrayOf(a, b, c), { null }) { args: Array<*> ->
        @Suppress("UNCHECKED_CAST") transform(args[0] as T1, args[1] as T2, args[2] as T3)
    }
}

fun <T, R> combineSimpleEventsInternal(
    inputEvents: Array<out SimpleEvent<T>>,
    arrayFactory: () -> Array<T?>?,
    transform: (Array<T>) -> R,
): SimpleEvent<R> {
    return object : SimpleEvent<R> {
        override fun listen(listener: EventListener<R>): AutoCloseable {
            val latestValues = arrayOfNulls<Any?>(inputEvents.size)
            latestValues.fill(UNINITIALIZED)
            var remainingUninitializedValues = latestValues.size
            fun updateCombined() {
                val results = arrayFactory()
                @Suppress("UNCHECKED_CAST")
                val newValue =
                    if (results == null) {
                        transform(latestValues as Array<T>)
                    } else {
                        (latestValues as Array<T?>).copyInto(results)
                        transform(results as Array<T>)
                    }
                dbg { "listener.notify(${listener.instanceName()}) -> $newValue" }
                listener.notify(newValue)
            }
            val upstreamHandles =
                inputEvents.mapIndexed { i, event ->
                    event.listen { value ->
                        val prevValue = latestValues[i]
                        if (prevValue == UNINITIALIZED) {
                            remainingUninitializedValues--
                        }
                        latestValues[i] = value
                        if (remainingUninitializedValues == 0) {
                            updateCombined()
                        }
                    }
                }
            return AutoCloseable { upstreamHandles.forEach { it.close() } }
        }
    }
}

internal inline fun <T, R> SimpleEvent<T>.transformSimpleObservable(
    crossinline block: EventListener<R>.(T) -> Unit
): SimpleEvent<R> {
    val upstream = this
    return object : SimpleEvent<R> {
        override fun listen(listener: EventListener<R>): AutoCloseable {
            return upstream.listen { value -> listener.block(value) }
        }
    }
}

internal inline fun <T, R> SimpleEvent<T>.map(crossinline transform: (T) -> R): SimpleEvent<R> {
    return transformSimpleObservable { value -> notify(transform(value)) }
}

internal inline fun <T> SimpleEvent<T>.filter(
    crossinline predicate: (T) -> Boolean
): SimpleEvent<T> {
    return transformSimpleObservable { value ->
        if (predicate(value)) {
            notify(value)
        }
    }
}

fun <T> SimpleEvent<T>.distinctUntilChanged(): SimpleEvent<T> {
    val upstream = this
    return object : SimpleEvent<T> {
        override fun listen(listener: EventListener<T>): AutoCloseable {
            // Store previous value here so that the cache does not leak to other listeners
            var previousValue = UNINITIALIZED
            return upstream.listen { value ->
                if (previousValue === UNINITIALIZED || previousValue != value) {
                    previousValue = value
                    dbg { "listener.notify(${listener.instanceName()}) -> $value" }
                    listener.notify(value)
                }
            }
        }
    }
}

fun <A, B, C> SimpleEvent<A>.sample(other: SimpleEvent<B>, transform: (A, B) -> C): SimpleEvent<C> {
    val upstream = this
    return object : SimpleEvent<C> {
        override fun listen(listener: EventListener<C>): AutoCloseable {
            val noVal = Any()
            val sampledRef = AtomicReference(noVal)
            val a = other.listen { sampledRef.set(it) }
            val b =
                upstream.listen {
                    val sampled = sampledRef.get()
                    if (sampled != noVal) {
                        @Suppress("UNCHECKED_CAST")
                        val transformedValue = transform(it, sampled as B)
                        dbg { "listener.notify(${listener.instanceName()}) -> $transformedValue" }
                        listener.notify(transformedValue)
                    }
                }
            return AutoCloseable {
                a.close()
                b.close()
            }
        }
    }
}

interface SimpleSuspendableObserver<T> {
    suspend fun awaitNextValue(): T

    fun cancel()
}

fun <T> SimpleEventImpl<T>.asSuspendableObserver(executor: Executor): SimpleSuspendableObserver<T> {
    val activeContinuation = AtomicReference<Continuation<T>?>(null)
    listen { newValue ->
        executor.execute {
            val awaitingCont = activeContinuation.getAndSet(null)
            awaitingCont!!.resume(newValue)
        }
    }
    return object : SimpleSuspendableObserver<T> {
        override suspend fun awaitNextValue(): T {
            return suspendCoroutine { c ->
                if (!activeContinuation.compareAndSet(null, c)) {
                    fail("Only one awaiter permitted at a time.")
                }
            }
        }

        override fun cancel() {
            activeContinuation.getAndSet(null)?.resumeWithException(CancellationException())
        }
    }
}

// Similar concept to SynchronousQueue; can only pass one value at a time, and can only pass
// values if actively being listened to
class SimpleSynchronousState<T>() {
    var nextInput: Continuation<T>? = null

    fun putValueOrThrow(newValue: T) {
        val c = nextInput
        if (c != null) {
            nextInput = null
            c.resume(newValue)
        } else {
            fail("No one is awaiting. Can't send new value if there are no listeners.")
        }
    }

    suspend fun awaitValue(): T {
        return suspendCoroutine { continuation ->
            if (nextInput != null) {
                fail("Already awaiting. Can't override next continuation")
            }
            nextInput = continuation
        }
    }
}

class SimpleWritableEventBuilder(val executor: Executor) :
    EventContext,
    WritableEventFactory<SimpleState<*>>,
    EventContextProvider<SimpleEvent<*>>,
    EventCombiner<SimpleEvent<*>>,
    IntEventCombiner<SimpleEvent<*>>,
    DistinctUntilChangedOperator<SimpleEvent<*>>,
    FilterOperator<SimpleEvent<*>>,
    MapOperator<SimpleEvent<*>>,
    SampleOperator<SimpleEvent<*>> {

    val writeContext = SimpleEventWriteContext()

    override fun <T> createWritableEvent(value: T): SimpleObservableStateBoxOut<T> {
        return SimpleEventBoxImpl(SimpleState(value))
    }

    override fun <T1, T2, R> combineEvents(
        a: SimpleEventBoxIn<T1>,
        b: SimpleEventBoxIn<T2>,
        transform: (T1, T2) -> R,
    ): SimpleObservableBoxOut<R> {
        return SimpleEventBoxImpl(combineSimpleEvents(a.unbox(), b.unbox(), transform))
    }

    override fun <T1, T2, T3, R> combineEvents(
        a: SimpleEventBoxIn<T1>,
        b: SimpleEventBoxIn<T2>,
        c: SimpleEventBoxIn<T3>,
        transform: (T1, T2, T3) -> R,
    ): SimpleObservableBoxOut<R> {
        return SimpleEventBoxImpl(combineSimpleEvents(a.unbox(), b.unbox(), c.unbox(), transform))
    }

    override fun <R> combineIntEvents(
        events: Iterable<SimpleEventBoxIn<Int>>,
        transform: (Array<Int>) -> R,
    ): SimpleObservableBoxOut<R> {
        val inputEvents = events.map { it.unbox() }.toList().toTypedArray()
        return SimpleEventBoxImpl(
            combineSimpleEventsInternal(
                inputEvents,
                { arrayOfNulls<Int?>(inputEvents.size) },
                transform,
            )
        )
    }

    override fun read(block: ReadContext<SimpleEvent<*>>.() -> Unit): AutoCloseable {
        val readContext = SimpleEventObservationContext(executor)
        readContext.block()
        return readContext
    }

    override fun write(block: WriteContext<SimpleEvent<*>>.() -> Unit) {
        writeContext.block()
    }

    override fun <T> SimpleEventBoxIn<T>.distinctUntilChanged(): SimpleObservableBoxOut<T> {
        return SimpleEventBoxImpl(unbox().distinctUntilChanged())
    }

    override fun <T> SimpleEventBoxIn<T>.filter(
        predicate: (T) -> Boolean
    ): SimpleObservableBoxOut<T> {
        return SimpleEventBoxImpl(unbox().filter(predicate))
    }

    override fun <A, B> SimpleEventBoxIn<A>.map(transform: (A) -> B): SimpleObservableBoxOut<B> {
        this as SimpleEventBoxImpl<A, *>
        return SimpleEventBoxImpl(unbox().map(transform))
    }

    override fun <A, B, C> SimpleEventBoxIn<A>.sample(
        other: SimpleEventBoxIn<B>,
        transform: (A, B) -> C,
    ): SimpleObservableBoxOut<C> {
        return SimpleEventBoxImpl(unbox().sample(other.unbox(), transform))
    }
}

class SimpleEventObservationContext(val executor: Executor) :
    ReadContext<SimpleEvent<*>>, AutoCloseable {
    private val cancellationList = mutableListOf<AutoCloseable>()
    private var closed = false

    override fun <T> SimpleEventBoxIn<T>.observe(block: (T) -> Unit) {
        val observable = unbox()
        // Start listening on bg thread, and always resume with latest value on the bg thread
        executor.execute {
            synchronized(cancellationList) {
                cancellationList += observable.listen { value -> executor.execute { block(value) } }
            }
        }
    }

    override fun close() {
        synchronized(cancellationList) {
            if (closed) {
                fail("Read context was already closed")
            }
            cancellationList.forEach { it.close() }
            closed = true
        }
    }
}

class SimpleEventWriteContext() : WriteContext<SimpleEvent<*>> {
    override fun <T> SimpleEventBoxIn<T>.update(value: T) {
        (unbox() as SimpleState<T>).value = value
    }

    override fun <T> SimpleEventBoxIn<T>.current(): T {
        return (unbox() as SimpleState<T>).value
    }
}

abstract class BaseSimpleEventBenchmark(threadParam: ThreadFactory<Any, Executor>) :
    BaseEventBenchmark<Executor, SimpleWritableEventBuilder>(
        threadParam,
        { SimpleWritableEventBuilder(it) },
    )
