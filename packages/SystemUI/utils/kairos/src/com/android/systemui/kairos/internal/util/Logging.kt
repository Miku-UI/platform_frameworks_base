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

package com.android.systemui.kairos.internal.util

import com.android.app.tracing.coroutines.traceCoroutine
import com.android.app.tracing.traceSection
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

private const val LoggingEnabled = false

internal inline fun logLn(indent: Int = 0, getMessage: () -> Any?) {
    if (!LoggingEnabled) return
    log(indent, getMessage)
    println()
}

internal inline fun log(indent: Int = 0, getMessage: () -> Any?) {
    if (!LoggingEnabled) return
    printIndent(indent)
    print(getMessage())
}

@JvmInline
internal value class LogIndent(val currentLogIndent: Int) {
    @OptIn(ExperimentalContracts::class)
    inline fun <R> logDuration(
        getPrefix: () -> String,
        start: Boolean = true,
        trace: Boolean = false,
        crossinline block: LogIndent.() -> R,
    ): R {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return logDuration(currentLogIndent, getPrefix, start, trace, block)
    }

    @OptIn(ExperimentalContracts::class)
    suspend inline fun <R> logDurationCoroutine(
        crossinline getPrefix: () -> String,
        start: Boolean = true,
        trace: Boolean = false,
        block: suspend LogIndent.() -> R,
    ): R {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return logDurationCoroutine(currentLogIndent, getPrefix, start, trace, block)
    }

    inline fun logLn(getMessage: () -> Any?) = logLn(currentLogIndent, getMessage)
}

@OptIn(ExperimentalContracts::class)
internal inline fun <R> logDuration(
    indent: Int,
    getPrefix: () -> String,
    start: Boolean = true,
    trace: Boolean = false,
    block: LogIndent.() -> R,
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        callsInPlace(getPrefix, InvocationKind.AT_MOST_ONCE)
    }
    return if (!LoggingEnabled) {
        if (trace) {
            traceSection(getPrefix) { LogIndent(0).block() }
        } else {
            LogIndent(0).block()
        }
    } else {
        val prefix = getPrefix()
        if (trace) {
            traceSection(prefix) { logDurationInternal(start, indent, prefix, block) }
        } else {
            logDurationInternal(start, indent, prefix, block)
        }
    }
}

@OptIn(ExperimentalContracts::class)
internal suspend inline fun <R> logDurationCoroutine(
    indent: Int,
    crossinline getPrefix: () -> String,
    start: Boolean = true,
    trace: Boolean = false,
    block: suspend LogIndent.() -> R,
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        callsInPlace(getPrefix, InvocationKind.AT_MOST_ONCE)
    }
    return if (!LoggingEnabled) {
        if (trace) {
            traceCoroutine(getPrefix) { LogIndent(0).block() }
        } else {
            LogIndent(0).block()
        }
    } else {
        val prefix = getPrefix()
        if (trace) {
            traceCoroutine(prefix) { logDurationInternal(start, indent, prefix) { block() } }
        } else {
            logDurationInternal(start, indent, prefix) { block() }
        }
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <R> logDurationInternal(
    start: Boolean,
    indent: Int,
    prefix: String,
    block: LogIndent.() -> R,
): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    if (start) {
        logLn(indent) { prefix }
    }
    val (result, duration) = measureTimedValue { LogIndent(indent + 1).block() }

    printIndent(indent)
    print(prefix)
    print(": ")
    println(duration.toString(DurationUnit.MICROSECONDS))
    return result
}

@Suppress("NOTHING_TO_INLINE")
private inline fun printIndent(indent: Int) {
    for (i in 0 until indent) {
        print("  ")
    }
}
