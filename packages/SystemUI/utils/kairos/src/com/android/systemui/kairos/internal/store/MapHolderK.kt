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
internal inline fun <K, V> MapK<MapHolderK.W, K, V>.asMapHolderK(): HashMapK<K, V> =
    this as HashMapK<K, V>

@JvmInline
internal value class MapHolderK<K, out V>(val storage: Map<K, V>) :
    MapK<MapHolderK.W, K, V>, Map<K, V> by storage {
    object W

    override fun forEach(yield: (K, V) -> Unit) {
        storage.forEach { k, v -> yield(k, v) }
    }

    override fun getValue(key: K): V = storage.getValue(key)
}
