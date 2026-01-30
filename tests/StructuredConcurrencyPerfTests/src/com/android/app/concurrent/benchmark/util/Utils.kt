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
package com.android.app.concurrent.benchmark.util

import android.util.Log
import java.lang.System.identityHashCode
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified R> R.instanceName(): String {
    return if (VERBOSE_DEBUG) {
        "${this::class.java.name.substringAfterLast(".")}@${identityHashCode(this).toHexString()}"
    } else {
        ""
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <reified R> R.dbg(msg: () -> String) {
    contract { callsInPlace(msg, InvocationKind.AT_MOST_ONCE) }
    if (VERBOSE_DEBUG) {
        @OptIn(ExperimentalStdlibApi::class) Log.v(VLOG_TAG, "${instanceName()}: ${msg()}")
    }
}

@OptIn(ExperimentalContracts::class)
inline fun dbg(msg: () -> String) {
    contract { callsInPlace(msg, InvocationKind.AT_MOST_ONCE) }
    if (VERBOSE_DEBUG) {
        @OptIn(ExperimentalStdlibApi::class) Log.v(VLOG_TAG, msg())
    }
}

operator fun <T1, T2> Iterable<T1>.times(other: Iterable<T2>): Iterable<Array<Any?>> {
    return flatMap { leftValue ->
        other.map { rightValue ->
            if (leftValue is Array<*>) {
                arrayOf(*leftValue, rightValue)
            } else {
                arrayOf(leftValue, rightValue)
            }
        }
    }
}
