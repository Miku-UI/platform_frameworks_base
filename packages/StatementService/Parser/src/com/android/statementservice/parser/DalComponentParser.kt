/*
 * Copyright (C) 2024 The Android Open Source Project
 *
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
@file:JvmName("DalComponentParser")

package com.android.statementservice.parser

import android.os.PatternMatcher.PATTERN_LITERAL
import android.os.PatternMatcher.PATTERN_PREFIX
import android.os.PatternMatcher.PATTERN_SIMPLE_GLOB

/**
 * Parses a DAL component matching expression to Android's {@link android.os.PatternMatcher} type
 * and pattern. Matching expressions support the following wildcards:
 *
 *  1) An asterisk (*) matches zero to as many characters as possible
 *  2) A question mark (?) matches any single character.
 *
 * Matching one to many characters can be done with a question mark followed by an asterisk (?*).
 *
 * @param expression A matching expression string from a DAL relation extension component used for
 *                   matching a URI part. This must be a non-empty string and all characters in the
 *                   string should be decoded.
 *
 * @return Returns a Pair containing a {@link android.os.PatternMatcher} type and pattern.
 */
fun parseMatchingExpression(expression: String): Pair<Int, String> {
    if (expression.isNullOrEmpty()) {
        throw IllegalArgumentException("Matching expressions cannot be an empty string")
    }
    var wildcardCount = 0
    for (i in expression.indices) {
        if (expression[i] == '?' || expression[i] == '*') {
            wildcardCount += 1
        }
    }
    if (wildcardCount == 0) {
        return Pair(PATTERN_LITERAL, expression)
    }
    if (wildcardCount == 1 && expression.endsWith('*')) {
        return Pair(PATTERN_PREFIX, expression.substring(0, expression.length - 1))
    }

    // For PATTERN_SIMPLE_GLOB `.` is a wildcard so it needs to be escaped.
    val pattern = buildString {
        for (char in expression) {
            when (char) {
                '*' -> {
                    append(".*")
                }
                '?' -> {
                    append('.')
                }
                '.' -> {
                    append("\\.")
                }
                '\\' -> {
                    append("\\\\")
                }
                else -> append(char)
            }
        }
    }
    return Pair(PATTERN_SIMPLE_GLOB, pattern)
}