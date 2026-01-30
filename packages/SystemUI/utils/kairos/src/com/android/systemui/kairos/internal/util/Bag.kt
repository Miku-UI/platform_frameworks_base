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

package com.android.systemui.kairos.internal.util

import androidx.collection.MutableObjectIntMap
import androidx.collection.MutableScatterSet
import androidx.collection.ScatterSet
import androidx.collection.mutableObjectIntMapOf
import androidx.collection.mutableScatterSetOf

internal class Bag<T> private constructor(private val intMap: MutableObjectIntMap<T>) {

    constructor() : this(mutableObjectIntMapOf())

    override fun toString(): String = intMap.toString()

    fun addAllKeysTo(other: MutableScatterSet<T>) {
        intMap.forEachKey { other.add(it) }
    }

    fun add(element: T): Boolean =
        if (element in intMap) {
            val entry = intMap[element]
            intMap[element] = entry + 1
            false
        } else {
            intMap[element] = 1
            true
        }

    fun remove(element: T): Boolean {
        if (element !in intMap) return false
        val entry = intMap[element]
        return if (entry <= 1) {
            intMap.remove(element)
            true
        } else {
            intMap[element] = entry - 1
            false
        }
    }

    /**
     * Adds all [elements], skipping [butNot], and returns the subset of [elements] that were not
     * already present in this bag.
     */
    fun addAll(elements: ScatterSet<T>, butNot: T? = null): ScatterSet<T>? {
        val newlyAdded = mutableScatterSetOf<T>()
        elements.forEach { value ->
            if (value != butNot) {
                if (add(value)) {
                    newlyAdded.add(value)
                }
            }
        }
        return if (newlyAdded.isEmpty()) null else newlyAdded
    }

    fun clear() {
        intMap.clear()
    }

    /**
     * Removes all [elements], and returns the subset of [elements] that are no longer present in
     * this bag.
     */
    fun removeAll(elements: ScatterSet<T>): ScatterSet<T>? {
        val result = mutableScatterSetOf<T>()
        elements.forEach { element ->
            if (remove(element)) {
                result.add(element)
            }
        }
        return if (result.isEmpty()) null else result
    }

    fun isNotEmpty(): Boolean = intMap.isNotEmpty()

    fun isEmpty(): Boolean = intMap.isEmpty()
}
