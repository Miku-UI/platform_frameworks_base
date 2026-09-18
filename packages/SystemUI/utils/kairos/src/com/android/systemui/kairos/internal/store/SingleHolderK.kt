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

@JvmInline
internal value class SingleHolderK<V>(val value: Any? = NoValue) : MapK<SingleHolderK.W, Unit, V> {
    object W

    override fun isEmpty(): Boolean = value === NoValue

    override fun forEach(yield: (Unit, V) -> Unit) {
        @Suppress("UNCHECKED_CAST") if (value !== NoValue) yield(Unit, value as V)
    }

    override fun containsKey(key: Unit): Boolean = !isEmpty()

    override fun getValue(key: Unit): V =
        if (value === NoValue) {
            throw NoSuchElementException("SingleHolderK is empty")
        } else {
            @Suppress("UNCHECKED_CAST")
            value as V
        }

    override fun get(key: Unit): V? =
        if (value === NoValue) {
            null
        } else {
            @Suppress("UNCHECKED_CAST")
            value as V
        }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun <V> MapK<SingleHolderK.W, Unit, V>.asSingleHolderK() = this as SingleHolderK<V>
