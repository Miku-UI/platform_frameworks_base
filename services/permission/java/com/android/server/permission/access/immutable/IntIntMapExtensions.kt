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

inline fun IntIntMap.allIndexed(predicate: (Int, Int, Int) -> Boolean): Boolean {
    forEachIndexed { index, key, value ->
        if (!predicate(index, key, value)) {
            return false
        }
    }
    return true
}

inline fun IntIntMap.anyIndexed(predicate: (Int, Int, Int) -> Boolean): Boolean {
    forEachIndexed { index, key, value ->
        if (predicate(index, key, value)) {
            return true
        }
    }
    return false
}

inline fun <R> IntIntMap.firstNotNullOfOrNullIndexed(transform: (Int, Int, Int) -> R): R? {
    forEachIndexed { index, key, value ->
        transform(index, key, value)?.let {
            return it
        }
    }
    return null
}

inline fun IntIntMap.forEachIndexed(action: (Int, Int, Int) -> Unit) {
    for (index in 0 until size) {
        action(index, keyAt(index), valueAt(index))
    }
}

inline fun IntIntMap.forEachReversedIndexed(action: (Int, Int, Int) -> Unit) {
    for (index in lastIndex downTo 0) {
        action(index, keyAt(index), valueAt(index))
    }
}

fun IntIntMap?.getWithDefault(key: Int, defaultValue: Int): Int {
    this ?: return defaultValue
    val index = indexOfKey(key)
    return if (index >= 0) valueAt(index) else defaultValue
}

inline val IntIntMap.lastIndex: Int
    get() = size - 1

inline fun IntIntMap.noneIndexed(predicate: (Int, Int, Int) -> Boolean): Boolean {
    forEachIndexed { index, key, value ->
        if (predicate(index, key, value)) {
            return false
        }
    }
    return true
}

inline fun MutableIntIntMap.getOrPut(key: Int, defaultValue: () -> Int): Int {
    get(key)?.let {
        return it
    }
    return defaultValue().also { put(key, it) }
}

operator fun MutableIntIntMap.minusAssign(key: Int) {
    array.delete(key)
}

fun MutableIntIntMap.putWithDefault(key: Int, value: Int, defaultValue: Int): Int {
    val index = indexOfKey(key)
    if (index >= 0) {
        val oldValue = valueAt(index)
        if (value != oldValue) {
            if (value == defaultValue) {
                removeAt(index)
            } else {
                putAt(index, value)
            }
        }
        return oldValue
    } else {
        if (value != defaultValue) {
            put(key, value)
        }
        return defaultValue
    }
}

operator fun MutableIntIntMap.set(key: Int, value: Int) {
    array.put(key, value)
}
