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

import android.os.Handler
import android.os.HandlerExecutor
import android.os.Looper
import android.os.Process
import android.os.Process.SCHED_OTHER
import android.os.Trace
import android.util.Log
import com.android.app.concurrent.benchmark.base.getCurrentBgThreadName
import com.android.app.concurrent.benchmark.util.BgExceptionHandler.rethrowInterruptWithCause
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicReference
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
 * Object for creating and stopping a thread, returns [scheduler], which is a type used for
 * scheduling work.
 *
 * The thread starts when this object is constructed and terminates when [close] is called.
 */
interface ThreadHandle<T> : AutoCloseable {
    /**
     * Returns an object, such as a [CoroutineDispatcher] or [Executor], that can be used for
     * scheduling work on this thread.
     */
    val scheduler: T

    /** Stop the thread associated with this handle */
    override fun close()
}

/**
 * Helper for naming JUnit parameters
 *
 * @param startThread start a new thread, and return a handle that can be used to later shutdown
 *   that thread
 */
sealed class ThreadBuilder<S : Any>(
    private val threadType: String,
    private val schedulerType: String,
) {
    abstract fun startThread(): ThreadHandle<S>

    override fun toString(): String {
        return "threadType=$threadType:scheduler=$schedulerType"
    }
}

abstract class LooperThreadHandleBuilder<S : Any>(schedulerType: String) :
    ThreadBuilder<S>("Looper", schedulerType) {
    abstract override fun startThread(): LooperThreadHandle<S>
}

abstract class ExecutorServiceThreadBuilder<S : Any>(schedulerType: String) :
    ThreadBuilder<S>("ExecutorService", schedulerType) {
    abstract override fun startThread(): ExecutorServiceThread<S>
}

abstract class NoThreadBuilder<S : Any>(schedulerType: String) :
    ThreadBuilder<S>("None", schedulerType)

private fun tracingContextIfDebug(): CoroutineContext {
    return if (DEBUG) {
        createCoroutineTracingContext("bg", walkStackForDefaultNames = true)
    } else {
        EmptyCoroutineContext
    }
}

object BgExceptionHandler : Thread.UncaughtExceptionHandler {
    private val mainThreadHolder = AtomicReference(Thread.currentThread())
    private val bgExceptionHolder = AtomicReference<Throwable>()

    /**
     * Requests that the current thread is interrupted when an exception is thrown on the bg thread.
     *
     * Upon receiving an interrupt, the current thread should call [rethrowInterruptWithCause] to
     * connect the current thread's exception to the bg exception (if it exists) and rethrow.
     */
    fun beginMonitoring() {
        val mainThread = Thread.currentThread()
        val oldMainThread = mainThreadHolder.getAndUpdate { mainThread }
        dbg {
            "beginMonitoring, mainThread is now ${mainThread.threadId()} (was: #${oldMainThread?.threadId()})"
        }
    }

    /**
     * Requests that the current thread no longer be interrupted when an exception is thrown on the
     * bg thread.
     */
    fun endMonitoring() {
        val oldMainThread = mainThreadHolder.getAndUpdate { null }
        dbg { "endMonitoring, mainThread is now null (was: #${oldMainThread?.threadId()})" }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        dbg(e) { "uncaughtException on thread #${t.threadId()}" }
        val mainThread = mainThreadHolder.get()
        if (mainThread != null) {
            if (bgExceptionHolder.compareAndSet(null, e)) {
                dbg { "exceptionHolder updated, interrupting mainThread=${mainThread.threadId()}" }
                mainThread.interrupt()
            } else {
                dbg { "exceptionHolder already has an exception, ignoring new exception" }
            }
        } else {
            dbg { "Clearing exceptionHolder, mainThread=null" }
            bgExceptionHolder.updateAndGet { null }
        }
    }

    fun rethrowInterruptWithCause(mainThreadInterrupt: InterruptedException) {
        val bgException = bgExceptionHolder.getAndUpdate { null }
        dbg { "rethrowInterruptWithCause" }
        if (bgException != null) {
            mainThreadInterrupt.initCause(bgException)
            throw AssertionError("Uncaught exception on background thread", mainThreadInterrupt)
        } else {
            throw mainThreadInterrupt
        }
    }
}

object BgThreadFactory : ThreadFactory {
    override fun newThread(r: Runnable): Thread {
        return Thread(
                {
                    Process.setThreadPriority(THREAD_PRIORITY_MOST_FAVORABLE)
                    assertEquals(SCHED_OTHER, Process.getThreadScheduler(Process.myTid()))
                    Thread.setDefaultUncaughtExceptionHandler(BgExceptionHandler)
                    r.run()
                },
                getCurrentBgThreadName(),
            )
            .also {
                it.isDaemon = true
                dbg { "newThread t=$it" }
            }
    }
}

abstract class ExecutorServiceThread<T>() : ThreadHandle<T> {

    val executorService: ExecutorService = Executors.newSingleThreadExecutor(BgThreadFactory)

    override fun close() {
        dbg { "shutdown" }
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(THREAD_FACTORY_TIMEOUT_MILLIS, MILLISECONDS)) {
                dbg { "shutdownNow" }
                executorService.shutdownNow()
                if (
                    !executorService.awaitTermination(THREAD_FACTORY_TIMEOUT_MILLIS, MILLISECONDS)
                ) {
                    Log.e("ConcurrentBenchmark", "Executor thread did not terminate")
                }
            }
        } catch (e: InterruptedException) {
            dbg(e) { "shutdownNow" }
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}

abstract class LooperThreadHandle<T>() : ThreadHandle<T> {
    companion object {
        const val TAG = "LooperThread"
    }

    val thread: Thread
    lateinit var looper: Looper
    lateinit var handler: Handler
    lateinit var executor: Executor

    init {
        val startupLatch = CountDownLatch(1)
        thread =
            BgThreadFactory.newThread {
                Looper.prepare()
                looper = Looper.myLooper()!!
                if (DEBUG) {
                    looper.setTraceTag(Trace.TRACE_TAG_APP)
                }
                handler = Handler.createAsync(looper)
                executor = HandlerExecutor(handler)
                handler.post { startupLatch.countDown() }
                dbg { "loop" }
                Looper.loop()
                dbg { "looper exited" }
            }
        thread.start()
        try {
            startupLatch.await(THREAD_FACTORY_TIMEOUT_MILLIS, MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.e(TAG, "Thread interrupted while waiting for new LooperThread to start", e)
        }
    }

    override fun close() {
        dbg { "quitSafely" }
        handler.post { looper.quitSafely() }
    }
}

object ExecutorServiceThreadWithExecutorBuilder :
    ExecutorServiceThreadBuilder<Executor>("Executor") {

    /**
     * Creates a thread backed by a worker / ExecutorService that uses a
     * [java.util.concurrent.BlockingQueue] schedule work.
     */
    override fun startThread(): ExecutorServiceThread<Executor> {
        return object : ExecutorServiceThread<Executor>() {
            override val scheduler: Executor = executorService
        }
    }
}

object ExecutorServiceThreadWithExecutorCoroutineDispatcherBuilder :
    ExecutorServiceThreadBuilder<CoroutineScope>("ExecutorCoroutineDispatcher") {

    /**
     * Creates a thread backed by a worker / ExecutorService that uses a
     * [java.util.concurrent.BlockingQueue] schedule work.
     */
    override fun startThread(): ExecutorServiceThread<CoroutineScope> {
        return object : ExecutorServiceThread<CoroutineScope>() {
            override val scheduler: CoroutineScope =
                CoroutineScope(executorService.asCoroutineDispatcher() + tracingContextIfDebug())

            override fun close() {
                scheduler.cancel()
                super.close()
            }
        }
    }
}

object LooperThreadWithHandlerBuilder : LooperThreadHandleBuilder<Handler>("Handler") {
    /**
     * Creates a thread backed by a [Looper] that uses [android.os.MessageQueue] to schedule work.
     */
    override fun startThread(): LooperThreadHandle<Handler> {
        return object : LooperThreadHandle<Handler>() {
            override val scheduler: Handler = handler
        }
    }
}

object LooperThreadWithExecutorBuilder : LooperThreadHandleBuilder<Executor>("Executor") {
    /**
     * Creates a thread backed by a [Looper] that uses [android.os.MessageQueue] to schedule work.
     */
    override fun startThread(): LooperThreadHandle<Executor> {
        return object : LooperThreadHandle<Executor>() {
            override val scheduler = executor
        }
    }
}

object LooperThreadWithImmediateExecutorBuilder :
    LooperThreadHandleBuilder<Executor>("ImmediateExecutor") {
    /**
     * Creates a thread backed by a [Looper] that uses [android.os.MessageQueue] to schedule work.
     */
    override fun startThread(): LooperThreadHandle<Executor> {
        return object : LooperThreadHandle<Executor>() {
            override val scheduler = Executor { r ->
                if (r != null && Looper.myLooper() == looper) {
                    r.run()
                } else {
                    executor.execute(r)
                }
            }
        }
    }
}

object LooperThreadWithHandlerDispatcherBuilder :
    LooperThreadHandleBuilder<CoroutineScope>("HandlerDispatcher") {
    override fun startThread(): LooperThreadHandle<CoroutineScope> {
        return object : LooperThreadHandle<CoroutineScope>() {
            override val scheduler: CoroutineScope =
                CoroutineScope(handler.asCoroutineDispatcher() + tracingContextIfDebug())

            override fun close() {
                scheduler.cancel()
                super.close()
            }
        }
    }
}

object LooperThreadWithImmediateHandlerDispatcherBuilder :
    LooperThreadHandleBuilder<CoroutineScope>("HandlerDispatcher.immediate") {
    override fun startThread(): LooperThreadHandle<CoroutineScope> {
        return object : LooperThreadHandle<CoroutineScope>() {
            override val scheduler: CoroutineScope =
                CoroutineScope(handler.asCoroutineDispatcher().immediate + tracingContextIfDebug())

            override fun close() {
                scheduler.cancel()
                super.close()
            }
        }
    }
}

object NoThreadWithDirectImmediateExecutorBuilder :
    NoThreadBuilder<Executor>("DirectImmediateExecutor") {
    override fun startThread(): ThreadHandle<Executor> {
        return object : ThreadHandle<Executor> {
            override val scheduler: Executor = Executor { r -> r.run() }

            override fun close() {}
        }
    }
}

object NoThreadWithUnconfinedDispatcherBuilder :
    NoThreadBuilder<CoroutineScope>("UnconfinedDispatcher") {
    override fun startThread(): ThreadHandle<CoroutineScope> {
        return object : ThreadHandle<CoroutineScope> {
            override val scheduler: CoroutineScope =
                CoroutineScope(Dispatchers.Unconfined + tracingContextIfDebug())

            override fun close() = scheduler.cancel()
        }
    }
}

object NoThreadWithDirectImmediateDispatcherBuilder :
    NoThreadBuilder<CoroutineScope>("DirectImmediateDispatcher") {
    override fun startThread(): ThreadHandle<CoroutineScope> {
        return object : ThreadHandle<CoroutineScope> {
            override val scheduler: CoroutineScope =
                CoroutineScope(
                    object : CoroutineDispatcher() {
                        override fun dispatch(context: CoroutineContext, block: Runnable) {
                            block.run()
                        }
                    } + tracingContextIfDebug()
                )

            override fun close() = scheduler.cancel()
        }
    }
}
