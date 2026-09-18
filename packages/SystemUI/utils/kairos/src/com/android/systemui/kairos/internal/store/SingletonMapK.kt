/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.kairos.internal.store

@Suppress("NOTHING_TO_INLINE")
internal inline fun <V> singletonMapKOf(value: V) = SingletonMapK<V>(value)

@Suppress("NOTHING_TO_INLINE") internal inline fun <V> singletonMapKOf() = SingletonMapK<V>()

@Suppress("NOTHING_TO_INLINE")
internal inline fun <V> MapK<SingletonMapK.W, Unit, V>.asSingletonMapK() = this as SingletonMapK<V>

/** A [Map] with a single element that has key [Unit]. */
internal class SingletonMapK<V>(var value: Any?) : MutableMapK<SingletonMapK.W, Unit, V> {

    constructor() : this(NoValue)

    object W

    override fun getOrPut(key: Unit, getValue: () -> V): V =
        if (value === NoValue) {
            getValue().also { value = it }
        } else {
            @Suppress("UNCHECKED_CAST")
            value as V
        }

    override fun readOnlyCopy() = SingletonMapK<V>(value)

    override fun set(key: Unit, value: V) {
        this.value = value
    }

    override fun remove(key: Unit): V? = get(Unit).also { value = NoValue }

    override fun clear() {
        value = NoValue
    }

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun forEach(crossinline yield: (Unit, V) -> Unit) {
        if (value !== NoValue) {
            @Suppress("UNCHECKED_CAST") yield(Unit, value as V)
        }
    }

    override fun isEmpty(): Boolean = value === NoValue

    @Suppress("UNCHECKED_CAST")
    override fun get(key: Unit): V? = if (value === NoValue) null else value as V

    override fun getValue(key: Unit): V =
        if (value !== NoValue) {
            @Suppress("UNCHECKED_CAST")
            value as V
        } else {
            throw NoSuchElementException("No element found for key $key")
        }

    override fun containsKey(key: Unit): Boolean = value !== NoValue

    @Suppress("UNCHECKED_CAST")
    fun iterator(): Iterator<Map.Entry<Unit, V>> = iterator {
        if (value !== NoValue) yield(StoreEntry(Unit, value as V))
    }

    object Factory : MutableMapK.Factory<W, Unit> {
        override fun <V> create(capacity: Int?): SingletonMapK<V> {
            check(capacity == null || capacity == 0 || capacity == 1) {
                "Can't use singleton store with capacity > 1. Got: $capacity"
            }
            return SingletonMapK()
        }
    }
}
