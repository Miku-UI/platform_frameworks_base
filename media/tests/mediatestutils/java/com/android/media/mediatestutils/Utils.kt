/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.media.mediatestutils

/**
 * A scope for registering cleanup actions that will be executed when the scope exits. Registered
 * blocks are executed in reverse order of registration.
 */
class DeferScope {
    private val defers = mutableListOf<() -> Unit>()

    /**
     * Registers a block of code to be executed when this scope is closed. Defers are executed in
     * reverse order of registration.
     */
    fun defer(block: () -> Unit) = defers.add(block)

    /** Registers the [value] AutoCloseable to be closed when this scope is closed. */
    inline fun <T : AutoCloseable> autoClose(value: T) = defer { value.close() }

    /**
     * Closes the scope, executing all registered defers in reverse order. If [cause] is provided,
     * any exceptions thrown by defers are added as suppressed to it. If [cause] is null, the first
     * exception thrown by a defer is thrown, with subsequent defer exceptions added as suppressed.
     */
    fun close(cause: Throwable?) {
        var exception = cause
        defers.asReversed().forEach { defer ->
            try {
                defer()
            } catch (e: Throwable) {
                exception = exception?.apply { addSuppressed(e) } ?: e
            }
        }
        exception?.takeIf { it !== cause }?.let { throw it }
    }
}

/**
 * Creates a [DeferScope] and executes the given [block] within it. All registered defers are
 * executed when the block completes, regardless of whether it completes normally or throws an
 * exception. The defers are called in reverse order.
 *
 * Prevents repeated nesting of `use` blocks.
 *
 * Example:
 * ```
 * withDefer {
 *     val resource = openResource()
 *     autoClose(resource)
 *     defer { cleanupSomethingElse() }
 *     // use resource
 * } // cleanupSomethingElse and resource.close are called here
 * ```
 */
inline fun <R> withDefer(block: DeferScope.() -> R): R {
    val scope = DeferScope()
    var exception: Throwable? = null
    try {
        return scope.block()
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        scope.close(exception)
    }
}
