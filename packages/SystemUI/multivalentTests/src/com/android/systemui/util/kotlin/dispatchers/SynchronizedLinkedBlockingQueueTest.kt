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

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.systemui.SysuiTestCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@LargeTest
@RunWith(AndroidJUnit4::class)
class SynchronizedLinkedBlockingQueueTest : SysuiTestCase() {
    private lateinit var underTest: SynchronizedLinkedBlockingQueue
    private val testRunnable = Runnable { /* test */ }

    @Before
    fun setUp() {
        underTest = SynchronizedLinkedBlockingQueue()
    }

    @After
    fun tearDown() {
        underTest.close()
    }

    /**
     * Tests the basic non-blocking add and remove operations.
     * It offers one item, checks the size, polls it, and checks the size again.
     */
    @Test
    fun offerAndPoll() {
        assertEquals(0, underTest.size)
        assertTrue(underTest.offer(testRunnable))
        assertEquals(1, underTest.size)
        val polled = underTest.poll()
        assertEquals(testRunnable, polled)
        assertEquals(0, underTest.size)
    }

    /**
     * Tests the basic blocking add (`put`) and remove (`take`) operations.
     * `put` is non-blocking in this impl, but `take` is tested for its removal logic.
     */
    @Test
    fun putAndTake() {
        assertEquals(0, underTest.size)
        underTest.put(testRunnable)
        assertEquals(1, underTest.size)
        val taken = underTest.take()
        assertEquals(testRunnable, taken)
        assertEquals(0, underTest.size)
    }

    /**
     * Verifies that `poll()` on an empty queue immediately returns `null`.
     */
    @Test
    fun pollEmpty() {
        assertNull(underTest.poll())
    }

    /**
     * Verifies that `peek()` returns the head of the queue without removing it.
     * It adds two items, peeks, checks size, polls, and peeks again to see the new head.
     */
    @Test
    fun peek() {
        val r1 = Runnable { }
        val r2 = Runnable { }
        underTest.offer(r1)
        underTest.offer(r2)
        assertEquals(2, underTest.size)
        assertEquals(r1, underTest.peek())
        assertEquals(2, underTest.size) // Peek should not remove
        assertEquals(r1, underTest.poll())
        assertEquals(r2, underTest.peek())
    }

    /**
     * Verifies that `offer(e, timeout, unit)` succeeds.
     * Since this queue's offer is non-blocking, this should always return true.
     */
    @Test
    fun offerWithTimeout() {
        // This implementation's offer is non-blocking, so it should just succeed
        assertTrue(underTest.offer(testRunnable, 100, TimeUnit.MILLISECONDS))
        assertEquals(1, underTest.size)
    }

    /**
     * Verifies the `size` property is correctly updated after a series of
     * add and remove operations.
     */
    @Test
    fun size() {
        assertEquals(0, underTest.size)
        underTest.offer(testRunnable)
        assertEquals(1, underTest.size)
        underTest.offer(testRunnable)
        assertEquals(2, underTest.size)
        underTest.take()
        assertEquals(1, underTest.size)
        underTest.poll()
        assertEquals(0, underTest.size)
    }

    /**
     * Verifies that `take()` blocks a thread when the queue is empty
     * and successfully unblocks and returns the item once it's offered.
     */
    @Test(timeout = 3000)
    fun takeBlocksAndUnblocks() {
        val unblockedLatch = CountDownLatch(1)
        val readyToTakeLatch = CountDownLatch(1)
        val itemRef = AtomicReference<Runnable>()

        // Start a new thread that will block on take()
        val thread = Thread {
            try {
                readyToTakeLatch.countDown()
                val item = underTest.take() // Block this new thread
                itemRef.set(item)
            } catch (e: InterruptedException) {
                // Restore interrupt status
                Thread.currentThread().interrupt()
            } finally {
                unblockedLatch.countDown()
            }
        }

        try {
            thread.isDaemon = true
            thread.start()

            assertTrue(
                "Consumer thread did not become ready in time",
                readyToTakeLatch.await(1, TimeUnit.SECONDS)
            )

            assertEquals("Thread should not have finished yet", 1, unblockedLatch.count)

            // Now, offer an item, which should unblock take()
            underTest.offer(testRunnable)

            // Wait for the latch to be released, confirming the thread unblocked
            assertTrue(
                "take() did not unblock within timeout",
                unblockedLatch.await(1, TimeUnit.SECONDS)
            )
            assertEquals(testRunnable, itemRef.get())
        } finally {
            // If the test fails/times out, this interrupts the
            // thread, causing queue.take() to throw
            // InterruptedException, which lets the thread's
            // finally block run and the thread to terminate cleanly.
            thread.interrupt()
        }
    }

    /**
     * Verifies that `poll(timeout, unit)` on an empty queue waits for the
     * specified duration and then returns `null`.
     */
    @Test
    fun pollTimeout_returnsNullAfterTimeout() {
        val pollTimeMs = 100L
        val startTime = System.nanoTime()
        val item = underTest.poll(pollTimeMs, TimeUnit.MILLISECONDS)
        val durationNs = System.nanoTime() - startTime

        assertNull("poll() should return null when queue is empty", item)
        assertTrue(
            "poll() did not wait for at least the timeout duration",
            durationNs >= TimeUnit.MILLISECONDS.toNanos(pollTimeMs)
        )
    }

    /**
     * Verifies that a thread blocked on `poll(timeout, unit)` unblocks
     * immediately when an item is added, rather than waiting for the full timeout.
     */
    @Test(timeout = 6000)
    fun pollTimeout_unblocksOnItem() {
        val unblockedLatch = CountDownLatch(1)
        val readyToPollLatch = CountDownLatch(1)
        val itemRef = AtomicReference<Runnable>()
        val pollTimeMs = 5000L

        // Start a new thread that will block on poll()
        val thread = Thread {
            try {
                readyToPollLatch.countDown()
                // This will block the new thread
                val item = underTest.poll(pollTimeMs, TimeUnit.MILLISECONDS)
                itemRef.set(item)
            } catch (e: InterruptedException) {
                // Restore interrupt status
                Thread.currentThread().interrupt()
            } finally {
                unblockedLatch.countDown()
            }
        }

        try {
            thread.isDaemon = true
            thread.start()

            assertTrue(
                "Consumer thread did not become ready in time",
                readyToPollLatch.await(1, TimeUnit.SECONDS)
            )
            assertEquals(1, unblockedLatch.count)

            // Offer an item to unblock poll()
            underTest.offer(testRunnable)

            // Wait for the thread to unblock (should be fast)
            assertTrue(
                "poll() did not unblock within timeout",
                unblockedLatch.await(1, TimeUnit.SECONDS)
            )
            assertEquals(testRunnable, itemRef.get())
        } finally {
            // If the test fails/times out, this interrupts the
            // thread, causing queue.poll() to throw
            // InterruptedException, which lets the thread's
            // finally block run and the thread to terminate cleanly.
            thread.interrupt()
        }
    }

    /**
     * Verifies that the queue reports its remaining capacity as `Int.MAX_VALUE`,
     * indicating it is effectively unbounded.
     */
    @Test
    fun remainingCapacity() {
        assertEquals(Int.MAX_VALUE, underTest.remainingCapacity())
    }

    /**
     * Verifies that methods not required by `ThreadPoolExecutor` (and thus not
     * implemented) correctly throw `UnsupportedOperationException`.
     */
    @Test
    fun unsupportedOperations() {
        try {
            underTest.iterator()
            fail("iterator() should throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            // Expected
        }

        try {
            underTest.drainTo(mutableListOf())
            fail("drainTo() should throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            // Expected
        }

        try {
            underTest.drainTo(mutableListOf(), 1)
            fail("drainTo(max) should throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            // Expected
        }
    }

    /**
     * A high-load stress test with multiple producer and consumer threads.
     * This test is designed to catch race conditions and deadlocks.
     * The consumer logic is made robust to handle flaky behavior
     * from the queue implementation itself, ensuring the test only passes
     * if all items are *actually* consumed.
     *
     * This test is ignored without a bug since it could take too long to run.
     * This is only for reference, and is able to reproduce said race conditions
     * about 1 in 3 runs.
     */
    @SuppressLint("DemotingTestWithoutBug")
    @Ignore("This test is a bruteforce hammer test to reproduce a race condition")
    @Test(timeout = 10_000) // 10 second timeout for safety
    fun hammerConcurrency() {
        val numProducers = 10
        val numConsumers = 10
        val itemsPerProducer = 100_000
        val totalItems = numProducers * itemsPerProducer

        // This must be atomic as it's incremented by multiple consumer threads.
        val itemsConsumed = AtomicInteger(0)
        val producerLatch = CountDownLatch(numProducers)
        val consumerLatch = CountDownLatch(numConsumers)

        val producerExecutor = Executors.newFixedThreadPool(numProducers)
        val consumerExecutor = Executors.newFixedThreadPool(numConsumers)

        val dummyRunnable = Runnable {}

        try {
            // Start consumers
            repeat(numConsumers) {
                consumerExecutor.submit {
                    var itemsToConsume = totalItems / numConsumers
                    try {
                        // This loop *must* complete fully.
                        while (itemsToConsume > 0) {
                            try {
                                // Block until an item is available.
                                underTest.take()

                                // Only increment/decrement after take() succeeds.
                                itemsConsumed.incrementAndGet()
                                itemsToConsume--

                            } catch (e: InterruptedException) {
                                Thread.interrupted()
                                break
                            }
                        }
                    } catch (t: Throwable) {
                        // Log if the whole loop fails unexpectedly
                        t.printStackTrace()
                    } finally {
                        // This latch is now only counted down when the
                        // consumer has actually finished its full quota.
                        consumerLatch.countDown()
                    }
                }
            }

            // Start producers
            repeat(numProducers) {
                producerExecutor.submit {
                    try {
                        repeat(itemsPerProducer) {
                            underTest.put(dummyRunnable)
                        }
                    } finally {
                        producerLatch.countDown()
                    }
                }
            }

            // Wait for all producers and consumers to finish
            assertTrue("Producers did not finish in time", producerLatch.await(8, TimeUnit.SECONDS))
            assertTrue("Consumers did not finish in time", consumerLatch.await(8, TimeUnit.SECONDS))

            // Verify
            assertEquals("All items should have been consumed", totalItems, itemsConsumed.get())
            assertEquals("Queue should be empty", 0, underTest.size)

        } finally {
            producerExecutor.shutdownNow()
            consumerExecutor.shutdownNow()
        }
    }
}

