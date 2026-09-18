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
 *
 */

package com.android.systemui.util.kotlin.dispatchers

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.systemui.SysuiTestCase
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.runner.RunWith
import org.junit.Test
import java.io.Closeable
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(DelicateCoroutinesApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
class IntrinsicLockDispatcherTest : SysuiTestCase() {

    private val testRunnable = Runnable { /* test */ }

    /**
     * Verifies that coroutines launched on the [IntrinsicLockDispatcher] actually run
     * on a thread created and managed by that dispatcher's internal thread pool.
     *
     * It does this by:
     * 1. Creating a dispatcher with a specific dispatcherName.
     * 2. Launching a coroutine using `withContext(dispatcher)`.
     * 3. Capturing the name of the thread the coroutine runs on.
     * 4. Asserting that the captured thread name starts with the specific dispatcherName.
     */
    @Test
    fun testDispatcherRunsOnCorrectThread() = runTest {
        val dispatcherName = "TestDispatcher"
        val dispatcher = IntrinsicLockDispatcher(2, 2, dispatcherName = dispatcherName)
        val threadName = AtomicReference<String>()
        val latch = CountDownLatch(1)

        try {
            withContext(dispatcher) {
                threadName.set(Thread.currentThread().name)
                latch.countDown()
            }

            assertTrue("Dispatcher task did not run", latch.await(1, TimeUnit.SECONDS))
            assertTrue(
                "Thread name '${threadName.get()}' does not start with '$dispatcherName'",
                threadName.get().startsWith(dispatcherName)
            )
        } finally {
            dispatcher.close()
        }
    }

    /**
     * Verifies that after [IntrinsicLockDispatcher.close] is called, the dispatcher
     * rejects any new tasks submitted to it.
     *
     * It does this by:
     * 1. Creating a new dispatcher.
     * 2. Calling [IntrinsicLockDispatcher.close].
     * 3. Attempting to `dispatch` a new task.
     * 4. Asserting that a [RejectedExecutionException] is thrown.
     */
    @Test
    fun testDispatcherClose() {
        val dispatcher = IntrinsicLockDispatcher(1, 1, dispatcherName = "CloseTest")
        dispatcher.close()

        try {
            dispatcher.dispatch(EmptyCoroutineContext, testRunnable)
            fail("Dispatching on a closed dispatcher should throw RejectedExecutionException")
        } catch (e: RejectedExecutionException) {
            // Expected
        }
    }

    /**
     * Verifies that the [newIntrinsicLockFixedThreadPoolContext] factory function
     * creates a valid dispatcher that respects the requested thread count and name.
     *
     * It does this by:
     * 1. Creating a dispatcher with a fixed size (`nThreads`) and name.
     * 2. Launching more jobs than threads (`nThreads * 2`) to force thread reuse.
     * 3. Collecting the names of all unique threads that execute jobs.
     * 4. Asserting that all thread names start with the correct name prefix.
     * 5. Asserting that the total number of unique threads used is not more than `nThreads`.
     */
    @Test
    fun testFactoryFunction_createsValidDispatcher() = runTest {
        val nThreads = 4
        val dispatcherName = "FactoryTest"
        val dispatcher = newIntrinsicLockFixedThreadPoolContext(nThreads, dispatcherName)

        try {
            val threads = Collections.synchronizedSet(HashSet<String>())
            val latch = CountDownLatch(nThreads * 2)

            // Launch more jobs than threads to ensure reuse and thread limits
            val jobs = (1..(nThreads * 2)).map {
                launch(dispatcher) {
                    threads.add(Thread.currentThread().name)
                    delay(50) // Keep the thread busy
                    latch.countDown()
                }
            }

            assertTrue("Jobs did not complete in time", latch.await(5, TimeUnit.SECONDS))
            jobs.joinAll()

            // Verify that all threads used have the correct name prefix
            assertTrue(threads.isNotEmpty())
            assertTrue(threads.all { it.startsWith(dispatcherName) })

            // Verify that no more than nThreads were created
            assertTrue(
                "Used ${threads.size} threads, but pool size was $nThreads",
                threads.size <= nThreads
            )

        } finally {
            (dispatcher as Closeable).close()
        }
    }

    /**
     * Verifies that the [newIntrinsicLockFixedThreadPoolContext] factory function
     * throws an [IllegalArgumentException] if created with an invalid thread count (e.g., 0).
     */
    @Test(expected = java.lang.IllegalArgumentException::class)
    fun testFactoryFunction_throwsOnInvalidThreadCount() {
        newIntrinsicLockFixedThreadPoolContext(0, "fail")
    }
}

