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

package com.android.systemui.util.kotlin.dispatchers

import com.android.systemui.Flags
import java.io.Closeable
import java.util.AbstractQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A custom implementation of a blocking queue that uses separate locks for
 * producers and consumers, and a dedicated, non-blocking notifier thread.
 * This is designed to make producer operations (like on the main thread)
 * extremely fast and free of contention with consumer threads.
 */
class SynchronizedLinkedBlockingQueue : AbstractQueue<Runnable>(), BlockingQueue<Runnable>,
    Closeable {

    /**
     * Internal node for the linked list.
     * [task] is mutable to allow nulling for GC.
     */
    private class Node(var task: Runnable?, var next: Node? = null)

    /**
     * The head of the list, which is *always* a dummy node.
     * The first *real* item is at [head.next].
     * Guarded by: [takeLock]
     */
    @Suppress("KDocUnresolvedReference")
    private var head: Node = Node(null)

    /**
     * The tail of the list.
     * Guarded by: [putLock]
     */
    private var tail: Node = head

    /**
     * Links a new node at the tail.
     * MUST be called while holding 'putLock'.
     */
    private fun enqueue(node: Node) {
        tail.next = node
        tail = node
    }

    /**
     * Unlinks and returns the first item from the head.
     * MUST be called while holding 'takeLock' and
     * MUST only be called when count > 0.
     */
    private fun dequeue(): Runnable {
        // head is the dummy node, head.next is the first real item
        val firstNode = head.next!!
        val item = firstNode.task!!
        firstNode.task = null // Help GC clean this node up
        head = firstNode // The (now empty) item becomes the new dummy
        return item
    }

    /** The current size of the queue. */
    private val count = AtomicInteger(0)

    /** The lock for all producer operations (`offer`, `put`). */
    private val putLock = Object()

    /** The lock for all consumer operations (`take`, `poll`). */
    private val takeLock = Object()

    /**
     * A dedicated thread to send notifications, so the producer thread
     * (e.g., main thread) never has to acquire the consumer's `takeLock`.
     */
    private val notifierExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "IntrinsicQueueNotifier").also { it.isDaemon = true }
    }

    /**
     * A gate to ensure we only have one notification task pending at a time.
     * This "batches" notifications.
     */
    private val notificationPending = AtomicBoolean(false)

    private val signalConsumersRunnable = Runnable {
        // We are on the notifier thread.
        // Open the gate so new offers can schedule a notification.
        notificationPending.set(false)

        if (Flags.synchronizedQueueCascadeNotify()) {
            // Acquire the lock and notify a waiting consumer. If there are more
            // tasks in the queue, `take()` will cascade `notify()`.
            synchronized(takeLock) {
                takeLock.notify()
            }
        } else {
            // Acquire the lock and notify all waiting consumers.
            synchronized(takeLock) {
                takeLock.notifyAll()
            }
        }
    }

    override val size: Int
        get() = count.get()

    override fun offer(e: Runnable): Boolean {
        val newNode = Node(e)

        synchronized(putLock) {
            enqueue(newNode)
            count.getAndIncrement()
        }

        // If a notification isn't already pending, schedule one.
        // This CAS is very fast and non-blocking.
        if (notificationPending.compareAndSet(false, true)) {
            notifierExecutor.execute(signalConsumersRunnable)
        }
        return true
    }

    override fun offer(e: Runnable, timeout: Long, unit: TimeUnit): Boolean {
        return offer(e)
    }

    override fun put(e: Runnable) {
        offer(e)
    }

    override fun take(): Runnable {
        val item: Runnable

        try {
            synchronized(takeLock) {
                while (count.get() == 0) {
                    takeLock.wait()
                }
                // We hold the lock, and we know count > 0
                item = dequeue()

                if (Flags.synchronizedQueueCascadeNotify()) {
                    // Handoff signal to the next consumer if the queue is not empty.
                    // This avoids the thundering herd problem from notifyAll().
                    // Given that `take()` will always be a worker thread, we
                    // don't need to fire-and-forget here to prevent lock
                    // contentions on the UI thread
                    if (count.decrementAndGet() > 0) {
                        takeLock.notify()
                    }
                } else {
                    count.decrementAndGet()
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }

        return item
    }

    override fun poll(timeout: Long, unit: TimeUnit): Runnable? {
        var item: Runnable?
        var nanos = unit.toNanos(timeout)
        val deadline = System.nanoTime() + nanos

        try {
            synchronized(takeLock) {
                while (count.get() == 0) {
                    if (nanos <= 0) {
                        return null
                    }
                    val millis = nanos / 1_000_000
                    val nanosPart = (nanos % 1_000_000).toInt()
                    takeLock.wait(millis, nanosPart)
                    nanos = deadline - System.nanoTime()
                }

                // We hold the lock, and we know count > 0
                item = dequeue()
                count.decrementAndGet()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }

        return item
    }

    override fun poll(): Runnable? {
        // Fast-path check without locking
        if (count.get() == 0) {
            return null
        }

        var item: Runnable? = null
        synchronized(takeLock) {
            // Re-check inside the lock
            if (count.get() > 0) {
                item = dequeue()
                count.decrementAndGet()
            }
        }

        return item
    }

    override fun peek(): Runnable? {
        // Fast-path check without locking
        if (count.get() == 0) {
            return null
        }

        synchronized(takeLock) {
            // Re-check inside lock
            if (count.get() == 0) {
                return null
            }
            // head.next could be null if a poll() just ran
            // but count.decrementAndGet() hasn't completed yet.
            // Such a race condition is harmless, since we don't
            // alter count in peek()
            return head.next?.task
        }
    }

    /**
     * Shuts down the internal notifier thread.
     */
    override fun close() {
        notifierExecutor.shutdown()
    }


    // Not required by ThreadPoolExecutor
    override fun iterator(): MutableIterator<Runnable> {
        throw UnsupportedOperationException("iterator() not supported by SynchronizedLinkedBlockingQueue")
    }

    // Not required by ThreadPoolExecutor
    override fun drainTo(c: MutableCollection<in Runnable>): Int {
        throw UnsupportedOperationException("drainTo() not supported by SynchronizedLinkedBlockingQueue")
    }

    // Not required by ThreadPoolExecutor
    override fun drainTo(c: MutableCollection<in Runnable>, maxElements: Int): Int {
        throw UnsupportedOperationException("drainTo() not supported by SynchronizedLinkedBlockingQueue")
    }

    override fun remainingCapacity(): Int {
        return Int.MAX_VALUE // Unbounded
    }
}
