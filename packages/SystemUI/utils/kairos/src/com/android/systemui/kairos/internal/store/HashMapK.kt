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

package com.android.systemui.kairos.internal.store

import androidx.collection.MutableScatterMap

@Suppress("NOTHING_TO_INLINE")
internal inline fun <K, V> MapK<HashMapK.W, K, V>.asHashMapK(): HashMapK<K, V> =
    this as HashMapK<K, V>

@JvmInline
internal value class HashMapK<K, V>(val storage: MutableScatterMap<K, V>) :
    MutableMapK<HashMapK.W, K, V> {
    object W

    override fun readOnlyCopy() =
        HashMapK(MutableScatterMap<K, V>(storage.size).apply { putAll(storage) })

    override fun clear() {
        storage.clear()
    }

    override fun remove(key: K): V? = storage.remove(key)

    override fun isEmpty(): Boolean = storage.isEmpty()

    override fun containsKey(key: K): Boolean = storage.containsKey(key)

    override fun get(key: K): V? = storage[key]

    override fun set(key: K, value: V) {
        storage.put(key, value)
    }

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun forEach(crossinline yield: (K, V) -> Unit) {
        storage.forEach { k, v -> yield(k, v) }
    }

    override fun getValue(key: K): V =
        if (storage.containsKey(key)) {
            storage[key]!!
        } else {
            throw NoSuchElementException("no value for key=$key")
        }

    override fun getOrPut(key: K, getValue: () -> V): V = storage.getOrPut(key, getValue)

    class Factory<K> : MutableMapK.Factory<W, K> {
        override fun <V> create(capacity: Int?) =
            HashMapK<K, V>(capacity?.let { MutableScatterMap(capacity) } ?: MutableScatterMap())
    }
}
