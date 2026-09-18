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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public class FakeScheduledExecutorServiceTest {
    private FakeScheduledExecutorService mExecutor;

    @Before
    public void setUp() {
        mExecutor = new FakeScheduledExecutorService(Thread.currentThread());
    }

    @Test
    public void testInitialState() {
        assertEquals(0, mExecutor.numTasks());
    }

    @Test
    public void testFastForwardEmptyExecutor() {
        assertEquals(0, mExecutor.fastForwardMillis(TimeUnit.DAYS.toMillis(1)));
    }

    @Test
    public void testFastForwardOnAnotherThreadFails() throws InterruptedException {
        AtomicBoolean threadCompleted = new AtomicBoolean(false);
        Thread otherThread =
                new Thread(
                        () -> {
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> mExecutor.fastForwardMillis(TimeUnit.DAYS.toMillis(1)));
                            threadCompleted.set(true);
                        });
        otherThread.start();
        otherThread.join();
        assertTrue(threadCompleted.get());
    }

    @Test
    public void testZeroDelayDoesNotRunImmediately() {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        0,
                        TimeUnit.MILLISECONDS);
        assertEquals(0, executions.get());
        assertFalse(future.isDone());
    }

    @Test
    public void testFutureGetOnTestThreadFails() {
        ScheduledFuture<?> future = mExecutor.schedule(() -> {}, 1, TimeUnit.SECONDS);
        assertFalse(future.isDone());
        assertThrows(IllegalStateException.class, () -> future.get());
    }

    @Test
    public void testScheduleRunnable() {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testScheduleRunnableReturnsNull() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        AtomicBoolean futureGetReturned = new AtomicBoolean(false);
        ScheduledFuture<?> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                future.get();
                                futureGetReturned.set(true);
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(futureGetReturned.get());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        futureGetThread.join();
        assertTrue(futureGetReturned.get());
    }

    @Test
    public void testScheduleCallableWithResult() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<Integer> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                            return 31415;
                        },
                        1,
                        TimeUnit.SECONDS);
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                assertEquals(31415, future.get().intValue());
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testScheduleCallableWithResultAndTimeout() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<Integer> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                            return 31415;
                        },
                        2,
                        TimeUnit.SECONDS);
        Thread futureGetTimeoutThread =
                new Thread(
                        () -> {
                            assertThrows(
                                    TimeoutException.class, () -> future.get(1, TimeUnit.SECONDS));
                        });
        Thread futureGetSuccessThread =
                new Thread(
                        () -> {
                            try {
                                assertEquals(31415, future.get(3, TimeUnit.SECONDS).intValue());
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetTimeoutThread.start();
        futureGetSuccessThread.start();
        mExecutor.waitForNumTimeoutWaiters(2);
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        futureGetTimeoutThread.join();
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetSuccessThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testScheduleCallableWithCancelAndTimeout() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<Integer> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                            return 31415;
                        },
                        2,
                        TimeUnit.SECONDS);
        Thread futureGetThread =
                new Thread(
                        () -> {
                            assertThrows(
                                    CancellationException.class,
                                    () -> future.get(1, TimeUnit.SECONDS));
                        });
        futureGetThread.start();
        mExecutor.waitForNumTimeoutWaiters(1);
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(0, executions.get());
        assertTrue(future.cancel(false));
        futureGetThread.join();
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertEquals(0, mExecutor.fastForwardMillis(3000));
        assertEquals(0, executions.get());
    }

    @Test
    public void testScheduleCallableWithNull() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<Integer> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                            return null;
                        },
                        1,
                        TimeUnit.SECONDS);
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                assertNull(future.get());
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testScheduleCallableWithException() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<Integer> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                            throw new RuntimeException("testing");
                        },
                        1,
                        TimeUnit.SECONDS);
        Thread futureGetThread =
                new Thread(
                        () -> {
                            assertThrows(ExecutionException.class, () -> future.get());
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testScheduleFixedRate() {
        AtomicInteger fastExecutions = new AtomicInteger(0);
        AtomicInteger slowExecutions = new AtomicInteger(0);
        ScheduledFuture<?> futureFast =
                mExecutor.scheduleAtFixedRate(
                        () -> {
                            fastExecutions.incrementAndGet();
                        },
                        1,
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> futureSlow =
                mExecutor.scheduleAtFixedRate(
                        () -> {
                            slowExecutions.incrementAndGet();
                        },
                        1,
                        2,
                        TimeUnit.SECONDS);
        assertFalse(futureFast.isDone());
        assertFalse(futureSlow.isDone());
        assertEquals(0, fastExecutions.get());
        assertEquals(0, slowExecutions.get());
        assertEquals(2, mExecutor.fastForwardMillis(1000));
        assertFalse(futureFast.isDone());
        assertFalse(futureSlow.isDone());
        assertEquals(1, fastExecutions.get());
        assertEquals(1, slowExecutions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1500));
        assertFalse(futureFast.isDone());
        assertFalse(futureSlow.isDone());
        assertEquals(2, fastExecutions.get());
        assertEquals(1, slowExecutions.get());
        assertEquals(2, mExecutor.fastForwardMillis(500));
        assertFalse(futureFast.isDone());
        assertFalse(futureSlow.isDone());
        assertEquals(3, fastExecutions.get());
        assertEquals(2, slowExecutions.get());

        assertEquals(2, mExecutor.shutdownNow().size());
        assertTrue(futureFast.isDone());
        assertTrue(futureSlow.isDone());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(3, fastExecutions.get());
        assertEquals(2, slowExecutions.get());
    }

    @Test
    public void testScheduleFixedDelay() {
        AtomicInteger fastExecutions = new AtomicInteger(0);
        AtomicInteger slowExecutions = new AtomicInteger(0);
        ScheduledFuture<?> futureFast =
                mExecutor.scheduleWithFixedDelay(
                        () -> {
                            fastExecutions.incrementAndGet();
                        },
                        1,
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> futureSlow =
                mExecutor.scheduleWithFixedDelay(
                        () -> {
                            slowExecutions.incrementAndGet();
                        },
                        1,
                        2,
                        TimeUnit.SECONDS);
        assertFalse(futureFast.isDone());
        assertFalse(futureSlow.isDone());
        assertEquals(0, fastExecutions.get());
        assertEquals(0, slowExecutions.get());
        assertEquals(2, mExecutor.fastForwardMillis(1000));
        assertFalse(futureFast.isDone());
        assertFalse(futureSlow.isDone());
        assertEquals(1, fastExecutions.get());
        assertEquals(1, slowExecutions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1500));
        assertFalse(futureFast.isDone());
        assertFalse(futureSlow.isDone());
        assertEquals(2, fastExecutions.get());
        assertEquals(1, slowExecutions.get());
        assertEquals(1, mExecutor.fastForwardMillis(500));
        assertFalse(futureFast.isDone());
        assertFalse(futureSlow.isDone());
        assertEquals(2, fastExecutions.get());
        assertEquals(2, slowExecutions.get());
        assertEquals(1, mExecutor.fastForwardMillis(500));
        assertFalse(futureFast.isDone());
        assertFalse(futureSlow.isDone());
        assertEquals(3, fastExecutions.get());
        assertEquals(2, slowExecutions.get());

        assertEquals(2, mExecutor.shutdownNow().size());
        assertTrue(futureFast.isDone());
        assertTrue(futureSlow.isDone());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(3, fastExecutions.get());
        assertEquals(2, slowExecutions.get());
    }

    @Test
    public void testExecute() {
        AtomicInteger executions = new AtomicInteger(0);
        mExecutor.execute(() -> executions.incrementAndGet());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertEquals(1, executions.get());
    }

    @Test
    public void testSubmitRunnable() {
        AtomicInteger executions = new AtomicInteger(0);
        Future<?> future =
                mExecutor.submit(
                        () -> {
                            executions.incrementAndGet();
                        });
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testSubmitRunnableReturnsNull() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        AtomicBoolean futureGetReturned = new AtomicBoolean(false);
        Future<?> future =
                mExecutor.submit(
                        () -> {
                            executions.incrementAndGet();
                        });
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                future.get();
                                futureGetReturned.set(true);
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(futureGetReturned.get());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        futureGetThread.join();
        assertTrue(futureGetReturned.get());
    }

    @Test
    public void testSubmitRunnableWithResultValue() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        Future<Integer> future =
                mExecutor.submit(
                        () -> {
                            executions.incrementAndGet();
                        },
                        31415);
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                assertEquals(31415, future.get().intValue());
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testSubmitCallableWithResult() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        Future<Integer> future =
                mExecutor.submit(
                        () -> {
                            executions.incrementAndGet();
                            return 31415;
                        });
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                assertEquals(31415, future.get().intValue());
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testSubmitCallableWithNull() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        Future<Integer> future =
                mExecutor.submit(
                        () -> {
                            executions.incrementAndGet();
                            return null;
                        });
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                assertNull(future.get());
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testSubmitCallableWithException() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        Future<Integer> future =
                mExecutor.submit(
                        () -> {
                            executions.incrementAndGet();
                            throw new RuntimeException("testing");
                        });
        Thread futureGetThread =
                new Thread(
                        () -> {
                            assertThrows(ExecutionException.class, () -> future.get());
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testRunSequentialTasks() {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future1 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future2 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        2,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future3 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        3,
                        TimeUnit.SECONDS);
        assertFalse(future1.isDone());
        assertFalse(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertFalse(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(1, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(2, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(3, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(3, executions.get());
    }

    @Test
    public void testRunMultipleTasks() {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future1 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future2 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future3 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        2,
                        TimeUnit.SECONDS);
        assertFalse(future1.isDone());
        assertFalse(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(0, executions.get());
        assertEquals(2, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(2, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(3, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(3, executions.get());
    }

    @Test
    public void testRunMultipleTasksAndCancelAll() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future1 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future2 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future3 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        2,
                        TimeUnit.SECONDS);

        assertFalse(future1.isDone());
        assertFalse(future1.isCancelled());
        assertFalse(future2.isDone());
        assertFalse(future2.isCancelled());
        assertFalse(future3.isDone());
        assertFalse(future3.isCancelled());
        assertEquals(0, executions.get());

        assertTrue(future1.cancel(false));
        assertTrue(future2.cancel(false));
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future1.isCancelled());
        assertTrue(future2.isDone());
        assertTrue(future2.isCancelled());
        assertFalse(future3.isDone());
        assertFalse(future3.isCancelled());
        assertEquals(0, executions.get());

        assertTrue(future1.cancel(false));
        assertTrue(future2.cancel(false));
        assertTrue(future3.cancel(false));
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future1.isCancelled());
        assertTrue(future2.isDone());
        assertTrue(future2.isCancelled());
        assertTrue(future3.isDone());
        assertTrue(future3.isCancelled());
        assertEquals(0, executions.get());

        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                assertThrows(CancellationException.class, () -> future1.get());
                            } catch (Exception e) {
                                fail("future2.get() threw unexpected exception: " + e);
                            }
                            try {
                                assertThrows(CancellationException.class, () -> future2.get());
                            } catch (Exception e) {
                                fail("future2.get() threw unexpected exception: " + e);
                            }
                            try {
                                assertThrows(CancellationException.class, () -> future3.get());
                            } catch (Exception e) {
                                fail("future2.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        futureGetThread.join();
    }

    @Test
    public void testRunMultipleTasksAndCancelSome() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future1 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future2 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future3 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        2,
                        TimeUnit.SECONDS);

        assertFalse(future1.isDone());
        assertFalse(future1.isCancelled());
        assertFalse(future2.isDone());
        assertFalse(future2.isCancelled());
        assertFalse(future3.isDone());
        assertFalse(future3.isCancelled());
        assertEquals(0, executions.get());

        assertTrue(future2.cancel(false));
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertFalse(future1.isCancelled());
        assertTrue(future2.isDone());
        assertTrue(future2.isCancelled());
        assertFalse(future3.isDone());
        assertFalse(future3.isCancelled());
        assertEquals(1, executions.get());

        assertFalse(future1.cancel(false));
        assertTrue(future2.cancel(false));
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertFalse(future1.isCancelled());
        assertTrue(future2.isDone());
        assertTrue(future2.isCancelled());
        assertTrue(future3.isDone());
        assertFalse(future3.isCancelled());
        assertEquals(2, executions.get());

        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                assertNull(future1.get());
                            } catch (Exception e) {
                                fail("future1.get() threw unexpected exception: " + e);
                            }
                            try {
                                assertThrows(CancellationException.class, () -> future2.get());
                            } catch (Exception e) {
                                fail("future2.get() threw unexpected exception: " + e);
                            }
                            try {
                                assertNull(future3.get());
                            } catch (Exception e) {
                                fail("future3.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        futureGetThread.join();
    }

    @Test
    public void testScheduleNewTasksLater() {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future1 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future2 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        4,
                        TimeUnit.SECONDS);
        assertFalse(future1.isDone());
        assertFalse(future2.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        ScheduledFuture<?> future3 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        2,
                        TimeUnit.SECONDS);
        assertTrue(future1.isDone());
        assertFalse(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertFalse(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(1, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertFalse(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(2, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(3, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(3, executions.get());
    }

    @Test
    public void testShutdown() {
        assertFalse(mExecutor.isShutdown());
        assertFalse(mExecutor.isTerminated());
        mExecutor.shutdown();
        assertTrue(mExecutor.isShutdown());
        assertFalse(mExecutor.isTerminated());
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertTrue(mExecutor.isShutdown());
        assertTrue(mExecutor.isTerminated());
    }

    @Test
    public void testShutdownWithTasks() {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future1 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future2 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        2,
                        TimeUnit.SECONDS);

        assertFalse(mExecutor.isShutdown());
        assertFalse(mExecutor.isTerminated());
        assertFalse(future1.isDone());
        assertFalse(future1.isCancelled());
        assertFalse(future2.isDone());
        assertFalse(future2.isCancelled());
        assertEquals(0, executions.get());

        mExecutor.shutdown();
        assertTrue(mExecutor.isShutdown());
        assertFalse(mExecutor.isTerminated());
        assertFalse(future1.isDone());
        assertFalse(future1.isCancelled());
        assertFalse(future2.isDone());
        assertFalse(future2.isCancelled());
        assertEquals(0, executions.get());

        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(mExecutor.isShutdown());
        assertFalse(mExecutor.isTerminated());
        assertTrue(future1.isDone());
        assertFalse(future1.isCancelled());
        assertFalse(future2.isDone());
        assertFalse(future2.isCancelled());
        assertEquals(1, executions.get());

        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(mExecutor.isShutdown());
        assertTrue(mExecutor.isTerminated());
        assertTrue(future1.isDone());
        assertFalse(future1.isCancelled());
        assertTrue(future2.isDone());
        assertFalse(future2.isCancelled());
        assertEquals(2, executions.get());

        assertEquals(0, mExecutor.fastForwardMillis(1000));
    }

    @Test
    public void testShutdownWithAwait() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future1 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future2 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        2,
                        TimeUnit.SECONDS);

        Thread awaitAndTimeoutThread =
                new Thread(
                        () -> {
                            try {
                                assertFalse(mExecutor.awaitTermination(1, TimeUnit.SECONDS));
                            } catch (Exception e) {
                                fail("awaitTermination(1s) threw unexpected exception: " + e);
                            }
                        });
        awaitAndTimeoutThread.start();
        Thread awaitAndSucceedThread =
                new Thread(
                        () -> {
                            try {
                                assertTrue(mExecutor.awaitTermination(2, TimeUnit.SECONDS));
                            } catch (Exception e) {
                                fail("awaitTermination(2s) threw unexpected exception: " + e);
                            }
                        });
        awaitAndSucceedThread.start();
        mExecutor.waitForNumTimeoutWaiters(2);

        assertFalse(mExecutor.isShutdown());
        assertFalse(mExecutor.isTerminated());
        assertFalse(future1.isDone());
        assertFalse(future1.isCancelled());
        assertFalse(future2.isDone());
        assertFalse(future2.isCancelled());
        assertEquals(0, executions.get());

        mExecutor.shutdown();
        assertTrue(mExecutor.isShutdown());
        assertFalse(mExecutor.isTerminated());
        assertFalse(future1.isDone());
        assertFalse(future1.isCancelled());
        assertFalse(future2.isDone());
        assertFalse(future2.isCancelled());
        assertEquals(0, executions.get());

        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(mExecutor.isShutdown());
        assertFalse(mExecutor.isTerminated());
        assertTrue(future1.isDone());
        assertFalse(future1.isCancelled());
        assertFalse(future2.isDone());
        assertFalse(future2.isCancelled());
        assertEquals(1, executions.get());
        awaitAndTimeoutThread.join();

        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(mExecutor.isShutdown());
        assertTrue(mExecutor.isTerminated());
        assertTrue(future1.isDone());
        assertFalse(future1.isCancelled());
        assertTrue(future2.isDone());
        assertFalse(future2.isCancelled());
        assertEquals(2, executions.get());
        awaitAndSucceedThread.join();

        assertEquals(0, mExecutor.fastForwardMillis(1000));
    }

    @Test
    public void testShutdownNowWithTasks() {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future1 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future2 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        2,
                        TimeUnit.SECONDS);

        assertFalse(mExecutor.isShutdown());
        assertFalse(mExecutor.isTerminated());
        assertFalse(future1.isDone());
        assertFalse(future1.isCancelled());
        assertFalse(future2.isDone());
        assertFalse(future2.isCancelled());
        assertEquals(0, executions.get());

        List<Runnable> cancelled = mExecutor.shutdownNow();
        assertEquals(2, cancelled.size());
        assertTrue(mExecutor.isShutdown());
        assertFalse(mExecutor.isTerminated());
        assertTrue(future1.isDone());
        assertTrue(future1.isCancelled());
        assertTrue(future2.isDone());
        assertTrue(future2.isCancelled());
        assertEquals(0, executions.get());

        assertEquals(0, mExecutor.fastForwardMillis(3000));
        assertTrue(mExecutor.isShutdown());
        assertTrue(mExecutor.isTerminated());
        assertEquals(0, executions.get());
        for (Runnable runnable : cancelled) {
            runnable.run();
        }
        assertEquals(2, executions.get());
    }

    @Test
    public void testTaskSchedulesTask() {
        AtomicInteger executions = new AtomicInteger(0);
        List<ScheduledFuture<?>> futures = new ArrayList<>();
        futures.add(
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                            futures.add(
                                    mExecutor.schedule(
                                            () -> {
                                                executions.incrementAndGet();
                                            },
                                            2,
                                            TimeUnit.SECONDS));
                        },
                        1,
                        TimeUnit.SECONDS));
        assertEquals(1, futures.size());
        assertFalse(futures.get(0).isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(2, futures.size());
        assertTrue(futures.get(0).isDone());
        assertFalse(futures.get(1).isDone());
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(2, futures.size());
        assertTrue(futures.get(0).isDone());
        assertFalse(futures.get(1).isDone());
        assertEquals(1, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(2, futures.size());
        assertTrue(futures.get(0).isDone());
        assertTrue(futures.get(1).isDone());
        assertEquals(2, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(2, futures.size());
        assertTrue(futures.get(0).isDone());
        assertTrue(futures.get(1).isDone());
        assertEquals(2, executions.get());
    }

    @Test
    public void testInvokeAll() throws InterruptedException, ExecutionException {
        AtomicInteger executions = new AtomicInteger(0);
        List<Future<Integer>> futures = new ArrayList<>();
        Thread invokeThread =
                new Thread(
                        () -> {
                            try {
                                futures.addAll(
                                        mExecutor.invokeAll(
                                                List.of(
                                                        () -> {
                                                            executions.incrementAndGet();
                                                            return 1;
                                                        },
                                                        () -> {
                                                            executions.incrementAndGet();
                                                            return 2;
                                                        },
                                                        () -> {
                                                            executions.incrementAndGet();
                                                            return 3;
                                                        })));

                            } catch (Exception e) {
                                fail("mExecutor.invokeAll() threw unexpected exception: " + e);
                            }
                        });
        invokeThread.start();
        mExecutor.waitForNumTasks(3);
        assertEquals(3, mExecutor.fastForwardMillis(0));
        invokeThread.join();
        assertEquals(3, futures.size());
        assertEquals(3, executions.get());
        for (int i = 0; i < 3; ++i) {
            Future<Integer> future = futures.get(i);
            assertTrue(future.isDone());
            int expectedValue = i + 1;
            Thread getThread =
                    new Thread(
                            () -> {
                                try {
                                    assertEquals(expectedValue, future.get().intValue());
                                } catch (Exception e) {
                                    fail("future.get() threw unexpected exception: " + e);
                                }
                            });
            getThread.start();
            getThread.join();
        }
    }

    @Test
    public void testInvokeAllWithTimeout() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        List<Future<Integer>> futures = new ArrayList<>();
        Thread invokeThread =
                new Thread(
                        () -> {
                            try {
                                futures.addAll(
                                        mExecutor.invokeAll(
                                                List.of(
                                                        () -> {
                                                            executions.incrementAndGet();
                                                            return 1;
                                                        },
                                                        () -> {
                                                            executions.incrementAndGet();
                                                            return 2;
                                                        },
                                                        () -> {
                                                            executions.incrementAndGet();
                                                            return 3;
                                                        }),
                                                1,
                                                TimeUnit.NANOSECONDS));

                            } catch (Exception e) {
                                fail("mExecutor.invokeAll() threw unexpected exception: " + e);
                            }
                        });
        invokeThread.start();
        mExecutor.waitForNumTasks(3);
        assertEquals(3, mExecutor.fastForwardMillis(1));
        invokeThread.join();
        assertEquals(3, futures.size());
        assertEquals(3, executions.get());
        for (int i = 0; i < 3; ++i) {
            Future<Integer> future = futures.get(i);
            assertTrue(future.isDone());
            int expectedValue = i + 1;
            Thread getThread =
                    new Thread(
                            () -> {
                                try {
                                    assertEquals(expectedValue, future.get().intValue());
                                } catch (Exception e) {
                                    fail("future.get() threw unexpected exception: " + e);
                                }
                            });
            getThread.start();
            getThread.join();
        }
    }

    @Test
    public void testInvokeAny() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        List<Integer> results = new ArrayList<>();
        Thread invokeThread =
                new Thread(
                        () -> {
                            try {
                                results.add(
                                        mExecutor.invokeAny(
                                                List.of(
                                                        () -> {
                                                            executions.incrementAndGet();
                                                            return 1;
                                                        },
                                                        () -> {
                                                            executions.incrementAndGet();
                                                            return 2;
                                                        },
                                                        () -> {
                                                            executions.incrementAndGet();
                                                            return 3;
                                                        })));
                            } catch (Exception e) {
                                fail("mExecutor.invokeAny() threw unexpected exception: " + e);
                            }
                        });
        invokeThread.start();
        mExecutor.waitForNumTasks(3);
        assertEquals(3, mExecutor.fastForwardMillis(0));
        invokeThread.join();
        assertEquals(3, executions.get());
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).intValue());
    }

    @Test
    public void testInvokeAnySomeFail() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        List<Integer> results = new ArrayList<>();
        Thread invokeThread =
                new Thread(
                        () -> {
                            try {
                                results.add(
                                        mExecutor.invokeAny(
                                                List.of(
                                                        () -> {
                                                            executions.incrementAndGet();
                                                            throw new RuntimeException("testing");
                                                        },
                                                        () -> {
                                                            executions.incrementAndGet();
                                                            throw new RuntimeException("testing");
                                                        },
                                                        () -> {
                                                            executions.incrementAndGet();
                                                            return 3;
                                                        })));
                            } catch (Exception e) {
                                fail("mExecutor.invokeAny() threw unexpected exception: " + e);
                            }
                        });
        invokeThread.start();
        mExecutor.waitForNumTasks(3);
        assertEquals(3, mExecutor.fastForwardMillis(0));
        invokeThread.join();
        assertEquals(3, executions.get());
        assertEquals(1, results.size());
        assertEquals(3, results.get(0).intValue());
    }

    @Test
    public void testInvokeAnyAllFail() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        Thread invokeThread =
                new Thread(
                        () -> {
                            try {
                                assertThrows(
                                        ExecutionException.class,
                                        () ->
                                                mExecutor.invokeAny(
                                                        List.of(
                                                                () -> {
                                                                    executions.incrementAndGet();
                                                                    throw new RuntimeException(
                                                                            "testing");
                                                                },
                                                                () -> {
                                                                    executions.incrementAndGet();
                                                                    throw new RuntimeException(
                                                                            "testing");
                                                                },
                                                                () -> {
                                                                    executions.incrementAndGet();
                                                                    throw new RuntimeException(
                                                                            "testing");
                                                                })));
                            } catch (Exception e) {
                                fail("mExecutor.invokeAny() threw unexpected exception: " + e);
                            }
                        });
        invokeThread.start();
        mExecutor.waitForNumTasks(3);
        assertEquals(3, mExecutor.fastForwardMillis(0));
        invokeThread.join();
        assertEquals(3, executions.get());
    }
}
