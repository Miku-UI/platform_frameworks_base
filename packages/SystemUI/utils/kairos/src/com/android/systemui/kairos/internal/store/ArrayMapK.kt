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

/** A [Map] backed by a flat array. */
internal class ArrayMapK<V>(val unwrapped: List<MutableMap.MutableEntry<Int, V>>) :
    MapK<ArrayMapK.W, Int, V>, AbstractMap<Int, V>() {
    object W

    override val entries: Set<Map.Entry<Int, V>> =
        object : AbstractSet<Map.Entry<Int, V>>() {
            override val size: Int
                get() = unwrapped.size

            override fun iterator(): Iterator<Map.Entry<Int, V>> = unwrapped.iterator()
        }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun <V> MapK<ArrayMapK.W, Int, V>.asArrayHolder(): ArrayMapK<V> =
    this as ArrayMapK<V>

internal class MutableArrayMapK<V>
private constructor(private val storage: Array<MutableMap.MutableEntry<Int, V>?>) :
    MutableMapK<ArrayMapK.W, Int, V>, AbstractMutableMap<Int, V>() {

    constructor(length: Int) : this(arrayOfNulls(length))

    override fun readOnlyCopy(): ArrayMapK<V> {
        val size1 = storage.size
        return ArrayMapK(
            buildList {
                for (i in 0 until size1) {
                    storage[i]?.let { entry -> add(StoreEntry(entry.key, entry.value)) }
                }
            }
        )
    }

    override fun asReadOnly(): MapK<ArrayMapK.W, Int, V> = readOnlyCopy()

    private fun getNumEntries(): Int {
        val capacity = storage.size
        var total = 0
        for (i in 0 until capacity) {
            storage[i]?.let { total++ }
        }
        return total
    }

    override fun put(key: Int, value: V): V? =
        storage[key]?.value.also { storage[key] = StoreEntry(key, value) }

    override val entries: MutableSet<MutableMap.MutableEntry<Int, V>> =
        object : AbstractMutableSet<MutableMap.MutableEntry<Int, V>>() {
            override val size: Int
                get() = getNumEntries()

            override fun add(element: MutableMap.MutableEntry<Int, V>): Boolean {
                if (storage[element.key] != element) {
                    storage[element.key] = element
                    return true
                }
                return false
            }

            override fun iterator(): MutableIterator<MutableMap.MutableEntry<Int, V>> =
                object : MutableIterator<MutableMap.MutableEntry<Int, V>> {

                    var cursor = -1
                    var nextIndex = -1

                    override fun hasNext(): Boolean {
                        val capacity = storage.size
                        if (nextIndex >= capacity) return false
                        if (nextIndex != cursor) return true
                        while (++nextIndex < capacity) {
                            if (storage[nextIndex] != null) {
                                return true
                            }
                        }
                        return false
                    }

                    override fun next(): MutableMap.MutableEntry<Int, V> {
                        if (!hasNext()) throw NoSuchElementException()
                        cursor = nextIndex
                        return storage[cursor]!!
                    }

                    override fun remove() {
                        check(
                            cursor >= 0 &&
                                cursor < storage.size &&
                                storage.getAndSet(cursor, null) != null
                        )
                    }

                    private fun <T> Array<T>.getAndSet(index: Int, newVal: T): T =
                        get(index).also { set(index, newVal) }
                }
        }

    class Factory : MutableMapK.Factory<ArrayMapK.W, Int> {
        override fun <V> create(capacity: Int?) =
            MutableArrayMapK<V>(
                checkNotNull(capacity) { "Cannot use ArrayMapK with null capacity." }
            )

        override fun <V> create(input: MapK<ArrayMapK.W, Int, V>): MutableArrayMapK<V> {
            val holder = input.asArrayHolder()
            return MutableArrayMapK(holder.unwrapped.toTypedArray())
        }
    }
}
