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

import com.android.app.concurrent.benchmark.util.dbg
import java.util.concurrent.Executor

typealias DirectEventBoxIn<T> = EventBox<T, DirectEvent<*>>

typealias DirectEventBoxOut<T> = EventBox<T, DirectEvent<T>>

typealias DirectWritableEventBoxOut<T> = EventBox<T, DirectWritableEvent<T>>

private class DirectEventBoxImpl<T, E : DirectEvent<T>>(event: E) : AbstractEventBox<T, E>(event)

private fun <T> DirectEventBoxIn<T>.unbox(): DirectEvent<T> =
    (this as DirectEventBoxImpl<T, *>).event

class DirectWritableEventBuilder(val executor: Executor) :
    EventContext,
    WritableEventFactory<DirectWritableEvent<*>>,
    EventContextProvider<DirectEvent<*>>,
    IntEventCombiner<DirectEvent<*>> {
    private val producers = mutableListOf<DirectEvent<*>>()

    override fun <T> createWritableEvent(value: T): DirectWritableEventBoxOut<T> {
        return DirectEventBoxImpl(DirectWritableEvent(value))
    }

    override fun <R> combineIntEvents(
        events: Iterable<DirectEventBoxIn<Int>>,
        transform: (Array<Int>) -> R,
    ): DirectEventBoxOut<R> {
        dbg { "combineIntEvents" }
        val producer =
            DirectEvent(
                recalculate = {
                    val intArray =
                        events
                            .map { (it.unbox() as DirectWritableEvent<Int>).value }
                            .toList()
                            .toTypedArray()
                    val rv = transform(intArray)
                    dbg { "recalculate rv=$rv" }
                    rv
                }
            )
        producers += producer
        return DirectEventBoxImpl(producer)
    }

    val readContext =
        object : ReadContext<DirectEvent<*>> {
            override fun <T> DirectEventBoxIn<T>.observe(block: (T) -> Unit) {
                val directEvent = (this as DirectEventBoxImpl<T, *>).event
                directEvent.callback = block
                directEvent.refresh(executor)
            }
        }

    override fun read(block: ReadContext<DirectEvent<*>>.() -> Unit): AutoCloseable {
        with(readContext) { block() }
        return NoopAutoCloseable
    }

    val writeContext =
        object : WriteContext<DirectEvent<*>> {
            override fun <T> DirectEventBoxIn<T>.update(value: T) {
                (unbox() as DirectWritableEvent<T>).value = value
            }

            override fun <T> DirectEventBoxIn<T>.current(): T {
                return (unbox() as DirectWritableEvent<T>).value
            }
        }

    override fun write(block: WriteContext<DirectEvent<*>>.() -> Unit) {
        with(writeContext) {
            dbg { "START write block {{{" }
            block()
            dbg { "}}} END write block" }
            producers.forEach { it.refresh(executor) }
        }
    }
}

open class DirectEvent<T>(val recalculate: (() -> T)? = null) {
    var callback: (T) -> Unit = {}

    fun refresh(executor: Executor) {
        dbg { "refresh()" }
        recalculate?.let { executor.execute { callback(it()) } }
    }
}

class DirectWritableEvent<T>(initialValue: T) : DirectEvent<T>() {
    var value: T = initialValue
        get() {
            dbg { "DirectWritableEvent#getValue -> $field" }
            return field
        }
        set(value) {
            dbg { "DirectWritableEvent#setValue $field -> $value" }
            field = value
        }

    init {
        dbg { "DirectWritableEvent#init value=$initialValue" }
    }
}
