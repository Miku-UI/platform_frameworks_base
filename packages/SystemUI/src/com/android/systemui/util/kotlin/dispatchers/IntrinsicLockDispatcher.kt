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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine dispatcher backed by a ThreadPoolExecutor that uses a custom,
 * ReentrantLock-free BlockingQueue.
 *
 * This serves as a drop-in replacement for `newFixedThreadPoolContext` to avoid
 * the main-thread stalls caused by lock contention in the standard implementation.
 *
 * @param corePoolSize The number of threads to keep in the pool, even if they are idle.
 * @param maxPoolSize The maximum number of threads to allow in the pool.
 * @param keepAliveTime When the number of threads is greater than the core, this is the maximum
 * time that excess idle threads will wait for new tasks before terminating.
 * @param unit The time unit for the keepAliveTime argument.
 * @param dispatcherName A base name for the dispatcher's threads, useful for debugging.
 */
class IntrinsicLockDispatcher(
    corePoolSize: Int,
    maxPoolSize: Int,
    keepAliveTime: Long = 10L,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
    dispatcherName: String = "IntrinsicLockDispatcherPool"
) : CoroutineDispatcher(), Closeable {

    private val executor: ExecutorService

    companion object {
        /**
         * Helper function to create a [ThreadFactory] with a custom name prefix for
         * easier debugging.
         */
        private fun threadFactory(namePrefix: String) = object : ThreadFactory {
            private val count = AtomicInteger(0)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "$namePrefix-${count.incrementAndGet()}").also { it.isDaemon = true }
            }
        }
    }

    init {
        executor = ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            keepAliveTime,
            unit,
            SynchronizedLinkedBlockingQueue(),
            threadFactory(dispatcherName)
        )
    }
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        executor.execute(block)
    }

    override fun close() {
        executor.shutdown()
    }
}

/**
 * Creates a new CoroutineDispatcher with a fixed-size thread pool that is free
 * of `ReentrantLock` to avoid specific main-thread contention issues.
 */
@DelicateCoroutinesApi
fun newIntrinsicLockFixedThreadPoolContext(nThreads: Int, name: String): CoroutineDispatcher {
    require(nThreads >= 1) { "Expected at least one thread, but $nThreads specified" }
    return IntrinsicLockDispatcher(
        corePoolSize = nThreads,
        maxPoolSize = nThreads,
        dispatcherName = name
    )
}
