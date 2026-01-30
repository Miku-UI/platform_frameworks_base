/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.hoststubgen

import java.io.PrintWriter
import kotlin.system.exitProcess

/**
 * Name of this executable. Set it in the main method.
 */
var executableName = "[command name not set]"

/**
 * A regex that maches whitespate.
 */
val whitespaceRegex = """\s+""".toRegex()

/**
 * Remove the comment ('#' and following) and surrounding whitespace from a line.
 */
fun normalizeTextLine(s: String): String {
    // Remove # and after. (comment)
    val pos = s.indexOf('#')
    val uncommented = if (pos < 0) s else s.substring(0, pos)

    // Remove surrounding whitespace.
    return uncommented.trim()
}

/**
 * Get the inline comment in a string. (Opposite of [normalizeTextLine])
 */
fun getInlineComment(s: String): String {
    val pos = s.indexOf('#')
    if (pos < 0) {
        return ""
    }
    return s.substring(pos + 1).trim()
}

// Note, Soong clears unknown env vars, so $HSG_ALWAYS_SHOW_STACKTRACE doesn't work
// if the command is executed on soong. In that case, you need to change this flag directly.
val ALWAYS_SHOW_STACKTRACE: Boolean = false ||
    "1".equals(System.getenv("HSG_ALWAYS_SHOW_STACKTRACE"))

/**
 * Concatenate list [a] and [b] and return it. As an optimization, it returns an input
 * [List] as-is if the other [List] is empty, so do not modify input [List]'s.
 */
fun <T> addLists(a: List<T>, b: List<T>): List<T> {
    if (a.isEmpty()) {
        return b
    }
    if (b.isEmpty()) {
        return a
    }
    return a + b
}

/**
 * Add element [b] to list [a] if [b] is not null. Otherwise, just return [a].
 * (because the method may return [a] as-is, do not modify it after passing it.)
 */
fun <T> addNonNullElement(a: List<T>, b: T?): List<T> {
    if (b == null) {
        return a
    }
    if (a.isEmpty()) {
        return listOf(b)
    }
    return a + b
}


/**
 * Exception for a parse error in a file
 */
class ParseException : Exception, UserErrorException {
    val hasSourceInfo: Boolean

    constructor(message: String) : super(message) {
        hasSourceInfo = false
    }

    constructor(message: String, file: String, line: Int) :
            super("$message in file $file line $line") {
        hasSourceInfo = true
    }

    fun withSourceInfo(filename: String, lineNo: Int): ParseException {
        if (hasSourceInfo) {
            return this // Already has source information.
        } else {
            return ParseException(this.message ?: "", filename, lineNo)
        }
    }
}

/**
 * Escape a string for a CSV field.
 */
fun csvEscape(value: String): String {
    return "\"" + value.replace("\"", "\"\"") + "\""
}

inline fun runMainWithBoilerplate(realMain: () -> Unit) {
    var success = false

    try {
        realMain()

        success = true
    } catch (e: Throwable) {
        log.e("$executableName: Error: ${e.message}")
        if (e !is UserErrorException || ALWAYS_SHOW_STACKTRACE) {
            e.printStackTrace(PrintWriter(log.getWriter(LogLevel.Error)))
        }
    } finally {
        log.i("$executableName finished")
        allLoggers.forEach { it.flush() }
    }

    exitProcess(if (success) 0 else 1)
}
