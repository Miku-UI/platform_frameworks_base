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
package com.android.server.permission.access.immutable

import android.util.SparseIntArray

/** Immutable map with indexed-base access, [Int] keys, and [Int] values */
sealed class IntIntMap(internal val array: SparseIntArray) : Immutable<MutableIntIntMap> {
    val size: Int
        get() = array.size()

    fun isEmpty(): Boolean = array.size() == 0

    operator fun contains(key: Int): Boolean = array.indexOfKey(key) >= 0

    operator fun get(key: Int): Int? = array.get(key)

    fun indexOfKey(key: Int): Int = array.indexOfKey(key)

    fun keyAt(index: Int): Int = array.keyAt(index)

    fun valueAt(index: Int): Int = array.valueAt(index)

    override fun toMutable(): MutableIntIntMap = MutableIntIntMap(this)

    override fun toString(): String = array.toString()
}

/** Mutable map with indexed-base access, [Int] keys, and [Int] values */
class MutableIntIntMap(array: SparseIntArray = SparseIntArray()) : IntIntMap(array) {
    constructor(intMap: IntIntMap) : this(intMap.array.clone())

    fun put(key: Int, value: Int): Int? = array.putReturnOld(key, value)

    fun remove(key: Int): Int? = array.removeReturnOld(key)

    fun clear() {
        array.clear()
    }

    fun putAt(index: Int, value: Int): Int = array.setValueAtReturnOld(index, value)

    fun removeAt(index: Int): Int = array.removeAtReturnOld(index)
}

internal fun SparseIntArray.putReturnOld(key: Int, value: Int): Int? {
    val index = indexOfKey(key)
    return if (index >= 0) {
        val oldValue = valueAt(index)
        setValueAt(index, value)
        oldValue
    } else {
        put(key, value)
        null
    }
}

internal fun SparseIntArray.removeReturnOld(key: Int): Int? {
    val index = indexOfKey(key)
    return if (index >= 0) {
        val oldValue = valueAt(index)
        removeAt(index)
        oldValue
    } else {
        null
    }
}

internal fun SparseIntArray.setValueAtReturnOld(index: Int, value: Int): Int {
    val oldValue = valueAt(index)
    setValueAt(index, value)
    return oldValue
}

internal fun SparseIntArray.removeAtReturnOld(index: Int): Int {
    val oldValue = valueAt(index)
    removeAt(index)
    return oldValue
}
