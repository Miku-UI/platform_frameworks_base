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
internal inline fun <V> MapK<ArrayMapK.W, Int, V>.asArrayMapK() = this as ArrayMapK<V>

/** A [Map] backed by a flat array. */
@JvmInline
internal value class ArrayMapK<V> private constructor(val storage: Array<Any?>) :
    MutableMapK<ArrayMapK.W, Int, V> {

    constructor(length: Int) : this(Array(length) { NoValue })

    object W

    override fun getOrPut(key: Int, getValue: () -> V): V {
        val current = storage[key]
        return if (current === NoValue) {
            getValue().also { storage[key] = it }
        } else {
            @Suppress("UNCHECKED_CAST")
            current as V
        }
    }

    override fun readOnlyCopy() = ArrayMapK<V>(storage.clone())

    override fun set(key: Int, value: V) {
        storage[key] = value
    }

    override fun remove(key: Int): V? = get(key).also { storage[key] = NoValue }

    override fun clear() {
        val capacity = storage.size
        for (i in 0 until capacity) {
            storage[i] = NoValue
        }
    }

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun forEach(yield: (Int, V) -> Unit) {
        val capacity = storage.size
        for (i in 0 until capacity) {
            val value = storage[i]
            if (value !== NoValue) {
                @Suppress("UNCHECKED_CAST") yield(i, value as V)
            }
        }
    }

    // TODO: we could cache this
    override fun isEmpty(): Boolean {
        val capacity = storage.size
        for (i in 0 until capacity) {
            val value = storage[i]
            if (value !== NoValue) return false
        }
        return true
    }

    override fun get(key: Int): V? {
        val value = storage[key]
        @Suppress("UNCHECKED_CAST")
        return if (value === NoValue) null else value as V
    }

    override fun getValue(key: Int): V {
        val value = storage[key]
        return if (value === NoValue) {
            throw NoSuchElementException("No element found for key $key")
        } else {
            @Suppress("UNCHECKED_CAST")
            value as V
        }
    }

    override fun containsKey(key: Int): Boolean = storage[key] !== NoValue

    fun iterator(): Iterator<Map.Entry<Int, V>> = iterator {
        val capacity = storage.size
        for (i in 0 until capacity) {
            val value = storage[i]
            if (value !== NoValue) {
                @Suppress("UNCHECKED_CAST") yield(StoreEntry(i, value as V))
            }
        }
    }

    object Factory : MutableMapK.Factory<W, Int> {
        override fun <V> create(capacity: Int?) =
            ArrayMapK<V>(checkNotNull(capacity) { "Cannot use ArrayMapK with null capacity." })
    }
}
