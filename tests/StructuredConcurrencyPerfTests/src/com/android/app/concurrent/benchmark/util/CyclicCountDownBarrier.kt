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

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.fail

private const val TAG = "CyclicCountDownBarrier"

/**
 * Awaits on the given [barrier] when [CyclicCountDownBarrier.countDown] is called [count] times,
 * then resets.
 *
 * This class is NOT thread safe. It should only be called from one background thread.
 */
class CyclicCountDownBarrier(private val barrier: CyclicBarrier, private val count: Int) {
    class Builder(val count: Int) {
        fun build(barrier: CyclicBarrier): CyclicCountDownBarrier {
            return CyclicCountDownBarrier(barrier, count)
        }
    }

    private var assignedThread: Thread? = null

    private var currentCount = count

    private var numAwaits = 0

    /**
     * IMPORTANT: This should only be called from ONE thread.
     *
     * Each thread should have its own instance of [CyclicCountDownBarrier], if necessary, or it
     * should call await on the associated [CyclicBarrier] manually.
     */
    fun countDown() {
        dbg { "barrier#countDown $currentCount -> ${currentCount - 1}" }
        val curThread = Thread.currentThread()
        if (assignedThread == null) {
            assignedThread = curThread
        }
        if (curThread != assignedThread) {
            fail(
                "CyclicCountDownBarrier.countDown() must only ever be called from one thread." +
                    " Was first called on Thread #${assignedThread?.threadId()}," +
                    " but was now called on Thread #${curThread.threadId()}"
            )
        }
        currentCount--
        if (currentCount == 0) {
            try {
                dbg { "barrier#await" }
                barrier.await(BARRIER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                numAwaits++
            } catch (e: TimeoutException) {
                fail(
                    "Timeout on Thread #${curThread.threadId()} while awaiting next iteration. " +
                        "Barrier was used $numAwaits times on this thread prior to this."
                )
                throw e
            }
            currentCount = count
        }
    }
}
