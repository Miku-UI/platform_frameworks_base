/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.settingslib.utils

import android.util.Log

/**
 * Executes a synchronous block of code safely by catching any [Exception].
 * If an exception occurs, it logs the error and returns the provided [fallback] value.
 *
 * @param T The return type of the block.
 * @param tag The log tag to use for [Log.e].
 * @param name A descriptive name of the operation being performed, used in the log message.
 * @param fallback The value to return if an exception is caught.
 * @param block The synchronous operation to execute.
 * @return The result of [block], or [fallback] if an exception occurs.
 */
inline fun <T> runSafely(
    tag: String,
    name: String,
    fallback: T,
    block: () -> T
): T {
    return try {
        block()
    } catch (e: Exception) {
        Log.e(tag, "Failed to retrieve $name", e)
        fallback
    }
}

/**
 * Executes a synchronous block of code safely by catching any [Exception].
 * If an exception occurs, it logs the error but does not return a value.
 *
 * @param tag The log tag to use for [Log.e].
 * @param name A descriptive name of the operation being performed, used in the log message.
 * @param block The synchronous operation to execute.
 */
inline fun runSafely(
    tag: String,
    name: String,
    block: () -> Unit
) {
    try {
        block()
    } catch (e: Exception) {
        Log.e(tag, "Failed to retrieve $name", e)
    }
}

/**
 * Executes a suspending block of code safely by catching any [Exception].
 * If an exception occurs, it logs the error and returns the provided [fallback] value.
 *
 * @param T The return type of the block.
 * @param tag The log tag to use for [Log.e].
 * @param name A descriptive name of the operation being performed, used in the log message.
 * @param fallback The value to return if an exception is caught.
 * @param block The suspending operation to execute.
 * @return The result of [block], or [fallback] if an exception occurs.
 */
suspend inline fun <T> runSafelyAsync(
    tag: String,
    name: String,
    fallback: T,
    crossinline block: suspend () -> T
): T {
    return try {
        block()
    } catch (e: Exception) {
        Log.e(tag, "Failed to execute $name", e)
        fallback
    }
}

/**
 * Executes a suspending block of code safely by catching any [Exception].
 * If an exception occurs, it logs the error but does not return a value.
 *
 * @param tag The log tag to use for [Log.e].
 * @param name A descriptive name of the operation being performed, used in the log message.
 * @param block The suspending operation to execute.
 */
suspend inline fun runSafelyAsync(
    tag: String,
    name: String,
    crossinline block: suspend () -> Unit
) {
    try {
        block()
    } catch (e: Exception) {
        Log.e(tag, "Failed to execute $name", e)
    }
}