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

import java.util.AbstractQueue
import java.util.ArrayDeque
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A custom implementation of a blocking queue that uses a simple LinkedList
 * and intrinsic monitor locks (`synchronized`) instead of `ReentrantLock`.
 * This avoids the specific contention issues that can arise from ReentrantLock
 * in scenarios like the one described in the Perfetto case study.
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal class SynchronizedLinkedBlockingQueue : AbstractQueue<Runnable>(), BlockingQueue<Runnable> {

    private val queue = ArrayDeque<Runnable>()

    override val size: Int
        @Synchronized get() = queue.size

    @Synchronized
    override fun offer(e: Runnable): Boolean {
        // This queue is unbounded, so it always succeeds.
        queue.addLast(e)
        (this as Object).notifyAll()
        return true
    }

    @Synchronized
    override fun offer(e: Runnable, timeout: Long, unit: TimeUnit): Boolean {
        // Unbounded queue, timeout is irrelevant.
        return offer(e)
    }

    @Synchronized
    override fun put(e: Runnable) {
        queue.addLast(e)
        (this as Object).notifyAll()
    }

    @Synchronized
    override fun take(): Runnable {
        while (queue.isEmpty()) {
            (this as Object).wait()
        }
        return queue.removeFirst()
    }

    @Synchronized
    override fun poll(timeout: Long, unit: TimeUnit): Runnable? {
        if (queue.isNotEmpty()) {
            return queue.removeFirst()
        }
        val millis = unit.toMillis(timeout)
        // Check for timeout > 0 to avoid waiting forever on wait(0).
        if (millis > 0) {
            (this as Object).wait(millis)
        }
        return queue.pollFirst()
    }

    @Synchronized
    override fun poll(): Runnable? = queue.pollFirst()

    @Synchronized
    override fun peek(): Runnable? = queue.peekFirst()

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
