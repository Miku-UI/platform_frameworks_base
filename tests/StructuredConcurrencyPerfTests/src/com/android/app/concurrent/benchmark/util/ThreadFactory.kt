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

import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.Process.SCHED_OTHER
import android.util.Log
import com.android.app.concurrent.benchmark.util.CsvMetricCollector.Helper.getCurrentBgThreadName
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals

/**
 * Least nice value for the highest priority among the SCHED_OTHER threads. This should match
 * priority used by AndroidX Microbenchmark for the BenchmarkRunner thread.
 */
private const val THREAD_PRIORITY_MOST_FAVORABLE = -20

/**
 * Helper for naming JUnit parameters
 *
 * @param startThread start a new thread, and return a handle that can be used to later shutdown
 *   that thread
 * @param getScheduler using the thread handle, returns an object, such as a [CoroutineDispatcher]
 *   or [Executor], that can be used for scheduling work on the thread.
 * @param stopThread stop a thread using the handle
 */
sealed class ThreadFactory<R : Any, T : Any>(private val name: String) {
    private var thread: R? = null
    private var scheduler: T? = null

    abstract fun startThread(): R

    abstract fun getScheduler(thread: R): T

    open fun quitScheduler(scheduler: T) {}

    open fun stopThread(thread: R) {}

    fun startThreadAndGetScheduler(): T {
        dbg { "startThreadAndGetScheduler" }
        if (thread != null) {
            throw IllegalStateException(
                "Attempting to start a new background thread before the prior one was terminated"
            )
        }
        thread = startThread()
        scheduler = getScheduler(thread!!)
        return scheduler!!
    }

    fun stopThreadAndQuitScheduler() {
        dbg { "stopThreadAndQuitScheduler" }
        if (thread == null) {
            throw IllegalStateException("Attempting to shutdown thread before it was started")
        }
        quitScheduler(scheduler!!)
        scheduler = null
        stopThread(thread!!)
        thread = null
    }

    override fun toString(): String {
        return name
    }
}

private fun tracingContextIfDebug(): CoroutineContext {
    return if (DEBUG) {
        createCoroutineTracingContext("bg", walkStackForDefaultNames = true)
    } else {
        EmptyCoroutineContext
    }
}

abstract class AbstractExecutorServiceBuilder<T : Any>(name: String) :
    ThreadFactory<ExecutorService, T>(name) {

    /**
     * Creates a thread backed by a worker / ExecutorService that uses a
     * [java.util.concurrent.BlockingQueue] schedule work.
     */
    final override fun startThread(): ExecutorService {
        dbg { "startExecutorService" }
        return Executors.newSingleThreadExecutor { runnable ->
            val t =
                Thread(
                    {
                        Process.setThreadPriority(THREAD_PRIORITY_MOST_FAVORABLE)
                        assertEquals(SCHED_OTHER, Process.getThreadScheduler(Process.myTid()))
                        runnable.run()
                    },
                    getCurrentBgThreadName(),
                )
            t.isDaemon = true
            dbg { "newSingleThreadExecutor t=$t" }
            t
        }
    }

    final override fun stopThread(thread: ExecutorService) {
        dbg { "stopExecutorService t=$thread" }
        thread.shutdown()
        try {
            if (!thread.awaitTermination(BARRIER_TIMEOUT_MILLIS * 2, MILLISECONDS)) {
                thread.shutdownNow()
                if (!thread.awaitTermination(BARRIER_TIMEOUT_MILLIS * 2, MILLISECONDS)) {
                    Log.e("ConcurrentBenchmark", "Executor thread did not terminate")
                }
            }
        } catch (_: InterruptedException) {
            thread.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}

object ExecutorThreadBuilder : AbstractExecutorServiceBuilder<Executor>(name = "Executor") {
    override fun getScheduler(thread: ExecutorService) = thread
}

abstract class AbstractHandlerThreadBuilder<T : Any>(name: String) :
    ThreadFactory<HandlerThread, T>(name) {
    /**
     * Creates a thread backed by a [Looper] that uses [android.os.MessageQueue] to schedule work.
     */
    final override fun startThread(): HandlerThread {
        dbg { "startHandlerThread" }
        val handlerThread =
            object : HandlerThread(getCurrentBgThreadName(), THREAD_PRIORITY_MOST_FAVORABLE) {
                    override fun onLooperPrepared() {
                        assertEquals(
                            SCHED_OTHER,
                            android.os.Process.getThreadScheduler(Process.myTid()),
                        )
                    }
                }
                .apply {
                    isDaemon = true
                    dbg { "newHandlerThread t=$this" }
                    start()
                }
        return handlerThread
    }

    final override fun stopThread(thread: HandlerThread) {
        thread.quitSafely()
    }
}

object HandlerThreadBuilder : AbstractHandlerThreadBuilder<Executor>(name = "Handler") {

    override fun getScheduler(thread: HandlerThread) = thread.threadExecutor
}

object HandlerImmediateThreadBuilder :
    AbstractHandlerThreadBuilder<Executor>(name = "Handler.immediate") {
    override fun getScheduler(thread: HandlerThread) =
        object : Executor {
            val wrappedExecutor = thread.threadExecutor

            override fun execute(r: Runnable?) {
                if (r != null && Looper.myLooper() == thread.looper) {
                    r.run()
                } else {
                    wrappedExecutor.execute(r)
                }
            }
        }
}

object UnconfinedThreadBuilder : ThreadFactory<Unit, Executor>(name = "Unconfined") {
    override fun startThread() = Unit

    override fun getScheduler(thread: Unit) = Executor { r -> r.run() }

    override fun stopThread(thread: Unit) {}
}

object ExecutorServiceCoroutineScopeBuilder :
    AbstractExecutorServiceBuilder<CoroutineScope>(name = "Executor") {

    override fun getScheduler(thread: ExecutorService) =
        CoroutineScope(thread.asCoroutineDispatcher() + tracingContextIfDebug())

    override fun quitScheduler(scheduler: CoroutineScope) {
        scheduler.cancel()
    }
}

object HandlerThreadScopeBuilder : AbstractHandlerThreadBuilder<CoroutineScope>(name = "Handler") {
    override fun getScheduler(thread: HandlerThread) =
        CoroutineScope(thread.threadHandler.asCoroutineDispatcher() + tracingContextIfDebug())

    override fun quitScheduler(scheduler: CoroutineScope) {
        scheduler.cancel()
    }
}

object HandlerThreadImmediateScopeBuilder :
    AbstractHandlerThreadBuilder<CoroutineScope>(name = "Handler.immediate") {

    override fun getScheduler(thread: HandlerThread) =
        CoroutineScope(
            thread.threadHandler.asCoroutineDispatcher().immediate + tracingContextIfDebug()
        )

    override fun quitScheduler(scheduler: CoroutineScope) {
        scheduler.cancel()
    }
}

object UnconfinedExecutorThreadScopeBuilder :
    ThreadFactory<CoroutineDispatcher, CoroutineScope>(name = "Unconfined") {
    override fun startThread() = Dispatchers.Unconfined

    override fun getScheduler(thread: CoroutineDispatcher) =
        CoroutineScope(thread + tracingContextIfDebug())

    override fun quitScheduler(scheduler: CoroutineScope) {
        scheduler.cancel()
    }

    override fun stopThread(thread: CoroutineDispatcher) {}
}

object UnsafeImmediateThreadScopeBuilder :
    ThreadFactory<CoroutineDispatcher, CoroutineScope>(name = "UnsafeImmediate") {
    override fun startThread() =
        object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                block.run()
            }
        }

    override fun getScheduler(thread: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(thread + tracingContextIfDebug())

    override fun quitScheduler(scheduler: CoroutineScope) {
        scheduler.cancel()
    }

    override fun stopThread(thread: CoroutineDispatcher) {}
}
