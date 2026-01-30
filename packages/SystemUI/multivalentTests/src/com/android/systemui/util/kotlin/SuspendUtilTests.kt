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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import java.lang.System.currentTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RaceSuspendTest : SysuiTestCase() {
    @Test
    fun raceSimple() = runBlocking {
        val winner = CompletableDeferred<Int>()
        val result = async { race({ winner.await() }, { awaitCancellation() }) }
        winner.complete(1)
        assertThat(result.await()).isEqualTo(1)
    }

    @Test fun raceImmediate() = runBlocking { assertThat(race<Int>({ 1 }, { 2 })).isEqualTo(1) }
}

@SmallTest
@RunWith(AndroidJUnit4::class)
class TimeoutSuspendTest : SysuiTestCase() {
    @Test
    fun launchWithTimeout() = runBlocking {
        val result = launchWithTimeout(500.milliseconds) { 1 }
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun launchWithTimeout_timesOutWhenDelay() = runBlocking {
        val launchStart = currentTimeMillis()
        val result =
            launchWithTimeout(100.milliseconds) {
                println("Starting delay")
                delay(1000.milliseconds)
                println("Finished delay")
            }
        val launchDurationMs = currentTimeMillis() - launchStart
        // VERIFY that block timed out
        assertThat(result.isSuccess).isFalse()
        // VERIFY that the result was returned faster than the delay's minimum duration
        assertThat(launchDurationMs).isAtMost(999)
    }

    @Test
    fun awaitAndCancelOnTimeout_coroutine() = runBlocking {
        val deferred = CompletableDeferred<Int>()
        launch {
            delay(100.milliseconds)
            deferred.complete(1)
        }
        val result = deferred.awaitAndCancelOnTimeout(500.milliseconds)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(1)
    }

    @Test
    fun awaitAndCancelOnTimeout_thread_sleep() = runBlocking {
        val deferred = CompletableDeferred<Int>()
        Thread {
                Thread.sleep(100)
                deferred.complete(1)
            }
            .start()
        val result = deferred.awaitAndCancelOnTimeout(500.milliseconds)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(1)
    }

    @Test
    fun awaitAndCancelOnTimeout_thread_sleepForeverUntilCancelled() = runBlocking {
        val deferred = CompletableDeferred<Int>()
        val thread = Thread {
            while (!deferred.isCancelled) {
                Thread.sleep(100)
            }
        }
        thread.start()
        val result = deferred.awaitAndCancelOnTimeout(500.milliseconds)
        assertThat(result.isFailure).isTrue()
        delay(200.milliseconds)
        assertThat(thread.isAlive).isFalse()
    }

    @Test
    fun awaitAndCancelOnTimeout_thread_busyLoop() = runBlocking {
        val deferred = CompletableDeferred<Int>()
        val thread = Thread {
            val busyLoopStart = currentTimeMillis()
            println("Starting busy loop")
            while (currentTimeMillis() - busyLoopStart < 1000) {
                // busy loop
            }
            println("Finished busy loop")
        }
        val operationStart = currentTimeMillis()
        thread.start()
        val result = deferred.awaitAndCancelOnTimeout(500.milliseconds)
        val operationDurationMs = currentTimeMillis() - operationStart
        // VERIFY that the thread is still running
        assertThat(thread.isAlive).isTrue()
        // VERIFY that block timed out
        assertThat(result.isSuccess).isFalse()
        // VERIFY that the result was returned faster than the busy loop's minimum duration
        assertThat(operationDurationMs).isAtMost(999)
        // CLEANUP: let the busy loop end
        delay(1000.milliseconds)
        // VERIFY that the thread is stopped
        assertThat(thread.isAlive).isFalse()
    }
}
