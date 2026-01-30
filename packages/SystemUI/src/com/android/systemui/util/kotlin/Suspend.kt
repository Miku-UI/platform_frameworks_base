/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.util.kotlin

import com.android.app.tracing.coroutines.launchTraced as launch
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Runs the given [blocks] in parallel, returning the result of the first one to complete, and
 * cancelling all others.
 */
suspend fun <R> race(vararg blocks: suspend () -> R): R = coroutineScope {
    val completion = CompletableDeferred<R>()
    val raceJob = launch {
        for (block in blocks) {
            launch { completion.complete(block()) }
        }
    }
    completion.await().also { raceJob.cancel() }
}

/**
 * Runs the given [block] in parallel with a timeout, returning failure if the timeout completes
 * first.
 */
suspend fun <R> launchWithTimeout(timeout: Duration, block: suspend () -> R): Result<R> =
    race(
        { Result.success(block()) },
        {
            delay(timeout)
            Result.failure(TimeoutException("$block timed out after $timeout"))
        },
    )

/**
 * Awaits this [Deferred] until the given [timeout] has elapsed.
 * * If the timeout is reached before the deferred completes, the returned [Result] will contain
 *   [TimeoutException], and the deferred will be cancelled.
 * * If the deferred completes normally and in time, the returned Result will contain the value.
 * * If the deferred completes exceptionally, the returned Result will contain the exception.
 *
 * This is designed to solve situations that arise when the deferred work is not cooperative, for
 * example when it is loading bitmap data from another process and we want to have a limit on how
 * long we will wait, even if we cannot cancel that operation.
 */
suspend fun <R> Deferred<R>.awaitAndCancelOnTimeout(timeout: Duration): Result<R> {
    val deferred = this
    return race(
        {
            try {
                Result.success(deferred.await())
            } catch (ex: CancellationException) {
                deferred.cancel()
                throw ex
            } catch (ex: Exception) {
                Result.failure(ex)
            }
        },
        {
            delay(timeout)
            Result.failure(TimeoutException("$deferred timed out after $timeout"))
        },
    )
}
