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

package com.android.server.lskfreset;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fake implementation of ScheduledExecutorService for use in testing. Implementation only covers
 * the subset of features that are relevant to the LSKF reset services. All commands will be run
 * synchronously when time is advanced.
 */
class FakeScheduledExecutorService implements ScheduledExecutorService {
    // The "current time" from the point of view of the executor. This is only ever modified by
    // fastForwardMillis which only runs on the execution thread but any thread could implicitly
    // read this and do it is defined as an atomic.
    private final AtomicLong mElapsedMillis = new AtomicLong(0);

    // Helper wrapper that combines queuing of futures with a semaphore. For tracking futures and
    // future timeouts we mostly just need a DelayQueue, but adding in the semaphore functionality
    // allows us to implement useful "wait for X things to be in the queue" operations that are
    // necessary to correctly (and efficiently) implement multi-threaded tests.
    private static class DelayQueueSemaphore<E extends Delayed> extends Semaphore {
        private final DelayQueue<E> mQueue = new DelayQueue<E>();

        // Indicates that the queue has been shut down and no new items can be added. This does not
        // prevent tasks from being removed or drained.
        @GuardedBy("this")
        private boolean mShutdown = false;

        DelayQueueSemaphore() {
            super(0);
        }

        int size() {
            return mQueue.size();
        }

        void put(E e) {
            synchronized (this) {
                if (mShutdown) {
                    throw new RejectedExecutionException("Executor has been shut down");
                }
                mQueue.put(e);
            }
            release();
        }

        boolean remove(E e) {
            if (mQueue.remove(e)) {
                reducePermits(1);
                return true;
            } else {
                return false;
            }
        }

        List<E> drain() {
            List<E> drained = new ArrayList<>();
            mQueue.drainTo(drained);
            reducePermits(drained.size());
            return drained;
        }

        void shutdown() {
            synchronized (this) {
                mShutdown = true;
            }
        }

        boolean isShutdown() {
            synchronized (this) {
                return mShutdown;
            }
        }

        /* Returns the entire contents of the queue without removing any elements. */
        List<E> toList() {
            Object[] array = mQueue.toArray();
            List<E> list = new ArrayList<>(array.length);
            for (Object e : array) {
                list.add((E) e);
            }
            return list;
        }
    }

    private final DelayQueueSemaphore<FakeScheduledFuture<?>> mFutureQueue =
            new DelayQueueSemaphore<>();
    private final DelayQueueSemaphore<FakeScheduledFuture<?>.WaitForTimeout> mTimeoutQueue =
            new DelayQueueSemaphore<>();

    // A dummy future that will get run when the executor is terminated. This can be used to
    // implement blocking on termination via get().
    private final FakeScheduledFuture<?> mTerminationFuture =
            new FakeScheduledFuture<>(() -> {}, 0, 0, 0);

    // The thread that can run fastForwardMillis.
    private final Thread mExecutionThread;

    /** Assert that the currently executing thread is the execution thread. */
    private void assertExecutionThread() {
        Preconditions.checkState(Thread.currentThread() == mExecutionThread);
    }

    /** Assert that the currently executing thread is not the execution thread. */
    private void assertNotExecutionThread() {
        Preconditions.checkState(Thread.currentThread() != mExecutionThread);
    }

    /**
     * Construct a new fake implementation of ScheduledExecutorService.
     *
     * @param executionThread The thread that is allowed to call fastForwardMillis. It is an error
     *     to fast forward time from any other thread. It is also an error to call any operation
     *     that would wait/block on the execution of a task from this thread, as that would lead to
     *     a deadlock.
     */
    FakeScheduledExecutorService(Thread executionThread) {
        mExecutionThread = executionThread;
    }

    /**
     * Reports the number of currently queued tasks.
     *
     * @return The number of tasks currently awaiting execution.
     */
    int numTasks() {
        return mFutureQueue.size();
    }

    /**
     * Waits for the number of enqueued tasks to reach a given count.
     *
     * <p>This is useful in tests that want to launch a bunch of test threads and then wait for them
     * all to reach a point where they've queued up all of their tasks.
     */
    void waitForNumTasks(int n) {
        assertExecutionThread();
        mFutureQueue.acquireUninterruptibly(n);
        mFutureQueue.release(n);
    }

    /**
     * Waits for the number of blocked-with-timeouts to reach a given count.
     *
     * <p>This is useful in tests that want to launch a bunch of test threads and then wait for them
     * all to reach a point where they're blocked on a future that can timeout. Because timeouts a
     * relative to the current time, you need a way to ensure that all the threads have started
     * their timeout before you fast-forward time, and this method provides that.
     */
    void waitForNumTimeoutWaiters(int n) {
        assertExecutionThread();
        mTimeoutQueue.acquireUninterruptibly(n);
        mTimeoutQueue.release(n);
    }

    /**
     * Advances the scheduler and executes any tasks whose delay has expired.
     *
     * @param millis The number of milliseconds to advance the time forward.
     * @return The number of tasks that were executed.
     */
    int fastForwardMillis(long millis) {
        assertExecutionThread();
        mElapsedMillis.getAndAdd(millis);

        // Execute all futures that are ready to run.
        List<FakeScheduledFuture<?>> readyFutures = mFutureQueue.drain();
        for (FakeScheduledFuture<?> future : readyFutures) {
            future.execute();
        }
        // If the executor has been shut down and there are no more pending tasks then run the
        // termination future to unblock any termination waiters.
        if (mFutureQueue.isShutdown() && mFutureQueue.size() == 0) {
            mTerminationFuture.execute();
        }
        // Signal all timeouts that have expired. We run this after all of the ready futures have
        // been executed and so if moving time forward triggers both execution and timeout then the
        // exeuction takes precedence.
        List<FakeScheduledFuture<?>.WaitForTimeout> readyTimeouts = mTimeoutQueue.drain();
        for (FakeScheduledFuture<?>.WaitForTimeout waiter : readyTimeouts) {
            waiter.signal();
        }
        // The number of tasks that were executed earlier.
        return readyFutures.size();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        FakeScheduledFuture<?> future =
                new FakeScheduledFuture<>(command, unit.toMillis(delay), 0, 0);
        mFutureQueue.put(future);
        return future;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        FakeScheduledFuture<V> future = new FakeScheduledFuture<>(callable, unit.toMillis(delay));
        mFutureQueue.put(future);
        return future;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
            Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (period <= 0) {
            throw new IllegalArgumentException("Period must be positive");
        }
        FakeScheduledFuture<?> future =
                new FakeScheduledFuture<>(
                        command, unit.toMillis(initialDelay), unit.toMillis(period), 0);
        mFutureQueue.put(future);
        return future;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
            Runnable command, long initialDelay, long delay, TimeUnit unit) {
        if (delay <= 0) {
            throw new IllegalArgumentException("Delay must be positive");
        }
        FakeScheduledFuture<?> future =
                new FakeScheduledFuture<>(
                        command, unit.toMillis(initialDelay), 0, unit.toMillis(delay));
        mFutureQueue.put(future);
        return future;
    }

    @Override
    public void shutdown() {
        // Shut down the future queue. Note that we do not shut down the timeout queue as new
        // timeouts on existing futures can still be created.
        mFutureQueue.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        // Try to cancel as many tasks as possible.
        List<Runnable> cancelledTasks = new ArrayList<>();
        for (FakeScheduledFuture<?> future : mFutureQueue.toList()) {
            if (future.cancel(false)) {
                if (future.mRunnable != null) {
                    // Add the underlying runnable to the cancelled tasks list.
                    cancelledTasks.add(future.mRunnable);
                } else {
                    // Convert the underlying callable to a runnable that discards the result and
                    // ignores any thrown exceptions.
                    final Callable<?> callable = future.mCallable;
                    cancelledTasks.add(
                            () -> {
                                try {
                                    callable.call();
                                } catch (Exception e) {
                                    // Ignore exceptions, Runnable can't throw them.
                                }
                            });
                }
            }
        }
        return cancelledTasks;
    }

    @Override
    public boolean isShutdown() {
        return mFutureQueue.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        // Note that termination is only triggered when time is advanced and so even if you call
        // shutdown and there are no pending tasks isTerminated() will still return false until
        // fastForwardMillis() is called.
        return mTerminationFuture.isDone();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            mTerminationFuture.get(timeout, unit);
            return true;
        } catch (ExecutionException e) {
            // This shouldn't happen because the termination future body is just a no-op but if it
            // does that still would mean the termination was triggered and so return true.
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return schedule(task, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return schedule(
                () -> {
                    task.run();
                    return result;
                },
                0,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return schedule(task, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        assertNotExecutionThread();
        CountDownLatch mAllComplete = new CountDownLatch(tasks.size());
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            Future<T> future =
                    submit(
                            () -> {
                                try {
                                    return task.call();
                                } finally {
                                    mAllComplete.countDown();
                                }
                            });
            futures.add(future);
        }
        mAllComplete.await();
        return futures;
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        // Ignore the timeout parameters and just use regular invokeAll. Because the fake executor
        // is designed to only advance time when explicitly told to do so, tasks are treated as if
        // they run instantaneously. This means that it's not actually possible for tasks to hit
        // the timeout; as soon as time moves forward they will all get run.
        return invokeAll(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws ExecutionException, InterruptedException {
        assertNotExecutionThread();
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("No tasks provided");
        }
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            futures.add(submit(task));
        }
        // Go through every task, looking for one that completed successfully. In a more general
        // executor you would want to go through them in the order in which they complete but
        // because all of the tasks will be run at once as soon as time is advanced we can just
        // go through them in submission order.
        for (Future<T> future : futures) {
            try {
                return future.get();
            } catch (CancellationException | ExecutionException e) {
                // Ignore these; we're looking for a successful result.
            }
        }
        // If we get here than all of the tasks were either failed or were cancelled.
        throw new ExecutionException("All tasks failed or were cancelled", null);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws ExecutionException, InterruptedException, TimeoutException {
        // Ignore the timeout parameters and just use regular invokeAny. See the timeout invokeAll
        // comment for more details as to why timeouts don't do anything.
        return invokeAny(tasks);
    }

    @Override
    public void execute(Runnable command) {
        schedule(command, 0, TimeUnit.MILLISECONDS);
    }

    private class FakeScheduledFuture<V> implements ScheduledFuture<V> {
        // The underlying object to be called when the scheduled time arrives. Exactly one of these
        // will be non-null.
        private final Runnable mRunnable;
        private final Callable<V> mCallable;

        // The interval in milliseconds at which the future should be scheduled to re-run after it
        // is executed. The first value is the interval for fixed rate scheduling, the second value
        // is for fixed delay scheduling. Only one of these will be non-zero. If both are zero then
        // future should only be run once.
        private final long mFixedRateInterval;
        private final long mFixedDelayInterval;

        // The time at which the future should be next run. This is an atomic because while it is
        // only ever modified by the (single) execution thread it may be read by other threads.
        private final AtomicLong mTimeToExecuteAt;

        private final Object mStateLock = new Object();

        private enum State {
            // The future has not been executed or cancelled.
            PENDING,
            // The future has been cancelled without being executed. Note that because of how the
            // fake executor works it's not possible for a future to be cancelled after it begins
            // execution and so cancellation always means it was cancelled before it ran.
            CANCELLED,
            // The future has been executed. This may or may not have been "successful" in the
            // sense that if it fails with an exception that still counts as completion.
            COMPLETED,
        }

        @GuardedBy("mStateLock")
        private State mState = State.PENDING;

        // The result of the callable, if this future is for a callable. After execution if the
        // callable threw an exception then mResultException will be non-null; otherwise it returned
        // mResult. Note that callables can return null and so mResult==null does not necessarily
        // indicate that the callable was not executed.
        @GuardedBy("mStateLock")
        private V mResult = null;

        @GuardedBy("mStateLock")
        private ExecutionException mResultException = null;

        // Latch used to signal completion of the future.
        private final CountDownLatch mCompletionLatch = new CountDownLatch(1);

        // A list of timeout waiters, one for every get() call with a timeout. This list includes
        // both timeouts that have already expired and those that are still waiting. Entries are
        // only cleared once the future is completed or cancelled and the entire list is emptied.
        @GuardedBy("itself")
        private final List<WaitForTimeout> mTimeouts = new ArrayList<>();

        private class WaitForTimeout implements Delayed {
            private final long mTimeoutAt;
            private final CountDownLatch mTimeoutLatch = new CountDownLatch(1);

            WaitForTimeout(long timeout, TimeUnit unit) {
                mTimeoutAt = mElapsedMillis.get() + unit.toMillis(timeout);
            }

            @Override
            public long getDelay(TimeUnit unit) {
                long delayInMs = mTimeoutAt - mElapsedMillis.get();
                return unit.convert(delayInMs, TimeUnit.MILLISECONDS);
            }

            @Override
            public int compareTo(Delayed o) {
                long lhs = getDelay(TimeUnit.NANOSECONDS);
                long rhs = o.getDelay(TimeUnit.NANOSECONDS);
                return Long.compare(lhs, rhs);
            }

            private void await() throws InterruptedException, TimeoutException {
                mTimeoutLatch.await();
                if (mCompletionLatch.getCount() > 0) {
                    throw new TimeoutException();
                }
            }

            private void signal() {
                mTimeoutLatch.countDown();
            }
        }

        private FakeScheduledFuture(
                Runnable runnable, long delay, long fixedRateInterval, long fixedDelayInterval) {
            mRunnable = runnable;
            mCallable = null;
            mFixedRateInterval = fixedRateInterval;
            mFixedDelayInterval = fixedDelayInterval;
            mTimeToExecuteAt = new AtomicLong(mElapsedMillis.get() + delay);
        }

        private FakeScheduledFuture(Callable<V> callable, long delay) {
            mRunnable = null;
            mCallable = callable;
            // Callable futures do not support repeated execution.
            mFixedRateInterval = 0;
            mFixedDelayInterval = 0;
            mTimeToExecuteAt = new AtomicLong(mElapsedMillis.get() + delay);
        }

        /** Executes the task if it is still pending. */
        private void execute() {
            assertExecutionThread();
            try {
                synchronized (mStateLock) {
                    if (mState != State.PENDING) {
                        return;
                    }
                    State nextState = State.COMPLETED;
                    try {
                        if (mRunnable != null) {
                            mRunnable.run();
                            // If the future is repeating then reschedule it.
                            if (mFixedRateInterval > 0) {
                                nextState = State.CANCELLED;
                                mTimeToExecuteAt.getAndAdd(mFixedRateInterval);
                                mFutureQueue.put(this);
                                nextState = State.PENDING;
                            } else if (mFixedDelayInterval > 0) {
                                nextState = State.CANCELLED;
                                mTimeToExecuteAt.set(mElapsedMillis.get() + mFixedDelayInterval);
                                mFutureQueue.put(this);
                                nextState = State.PENDING;
                            }
                        } else {
                            try {
                                mResult = mCallable.call();
                            } catch (Exception e) {
                                mResultException = new ExecutionException(e);
                            }
                        }
                    } finally {
                        mState = nextState;
                    }
                }
            } finally {
                signal();
            }
        }

        /**
         * Signals all waiters that the future is no longer pending. This does not distinguish
         * between cancellation, successful completion, or exceptional completion.
         */
        private void signal() {
            synchronized (mTimeouts) {
                mCompletionLatch.countDown();
                for (WaitForTimeout waiter : mTimeouts) {
                    mTimeoutQueue.remove(waiter);
                    waiter.signal();
                }
                mTimeouts.clear();
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long delayInMs = mTimeToExecuteAt.get() - mElapsedMillis.get();
            return unit.convert(delayInMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            long lhs = getDelay(TimeUnit.NANOSECONDS);
            long rhs = o.getDelay(TimeUnit.NANOSECONDS);
            return Long.compare(lhs, rhs);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            // We can never interrupt running tasks and so mayInterruptIfRunning is ignored. If a
            // task is PENDING then it hasn't started running yet and if it's COMPLETED then it has
            // already finished.
            synchronized (mStateLock) {
                if (mState == State.COMPLETED) {
                    return false;
                }
                mState = State.CANCELLED;
                mFutureQueue.remove(this);
            }
            signal();
            return true;
        }

        @Override
        public boolean isCancelled() {
            synchronized (mStateLock) {
                return mState == State.CANCELLED;
            }
        }

        @Override
        public boolean isDone() {
            synchronized (mStateLock) {
                return mState != State.PENDING;
            }
        }

        @Override
        public V get() throws ExecutionException, InterruptedException {
            assertNotExecutionThread();
            mCompletionLatch.await();
            synchronized (mStateLock) {
                if (mState == State.CANCELLED) {
                    throw new CancellationException();
                } else if (mResultException != null) {
                    throw mResultException;
                } else {
                    return mResult;
                }
            }
        }

        @Override
        public V get(long timeout, TimeUnit unit)
                throws ExecutionException, InterruptedException, TimeoutException {
            assertNotExecutionThread();
            WaitForTimeout waiter;
            synchronized (mTimeouts) {
                // If the future is already complete we can return immediately. Otherwise we need
                // to queue up a wait-for-timeout.
                if (mCompletionLatch.getCount() == 0) {
                    return get();
                }
                waiter = new WaitForTimeout(timeout, unit);
                mTimeouts.add(waiter);
                mTimeoutQueue.put(waiter);
            }
            // Wait for completion or timeout. Do this outside of the synchronized block or else
            // this will deadlock with execute().
            waiter.await();
            return get();
        }
    }
}
