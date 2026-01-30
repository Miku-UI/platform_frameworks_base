/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.os;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.perftests.utils.BenchmarkState;
import android.perftests.utils.PerfStatusReporter;
import android.perftests.utils.Stats;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/** Performance tests for {@link MessageQueue}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MessageQueuePerfTest {
    static final String TAG = "MessageQueuePerfTest";
    private static final int PER_THREAD_MESSAGE_COUNT = 1000;
    private static final int THREAD_COUNT = 8;
    private static final int TOTAL_MESSAGE_COUNT = PER_THREAD_MESSAGE_COUNT * THREAD_COUNT;
    private static final int DEFAULT_MESSAGE_WHAT = 2;

    @Rule public TestName mTestName = new TestName();

    @Before
    public void setUp() {
        mHandlerThread = new HandlerThread("MessageQueuePerfTest");
        mHandlerThread.start();
    }

    @After
    public void tearDown() {
        mHandlerThread.quitSafely();
    }

    static class EnqueueThread extends Thread {
        CountDownLatch mStartLatch;
        CountDownLatch mEndLatch;
        Handler mHandler;
        int mMessageStartIdx;
        Message[] mMessages;
        long[] mDelays;
        ArrayList<Long> mResults;

        EnqueueThread(
                CountDownLatch startLatch,
                CountDownLatch endLatch,
                Handler handler,
                int startIdx,
                Message[] messages,
                long[] delays) {
            super();
            mStartLatch = startLatch;
            mEndLatch = endLatch;
            mHandler = handler;
            mMessageStartIdx = startIdx;
            mMessages = messages;
            mDelays = delays;
            mResults = new ArrayList<>();
        }

        @Override
        public void run() {
            Log.d(TAG, "Enqueue thread started at message index " + mMessageStartIdx);
            try {
                mStartLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            long now = SystemClock.uptimeMillis();
            long startTimeNS = SystemClock.elapsedRealtimeNanos();
            for (int i = mMessageStartIdx; i < (mMessageStartIdx + PER_THREAD_MESSAGE_COUNT); i++) {
                if (mDelays[i] == 0) {
                    mHandler.sendMessageAtFrontOfQueue(mMessages[i]);
                } else {
                    mHandler.sendMessageAtTime(mMessages[i], now + mDelays[i]);
                }
            }
            long endTimeNS = SystemClock.elapsedRealtimeNanos();

            mResults.add(endTimeNS - startTimeNS);
            mEndLatch.countDown();
        }
    }

    static class RemoveThread extends Thread {
        CountDownLatch mStartLatch;
        CountDownLatch mEndLatch;
        Handler mHandler;
        Thread mBlockingThread;
        int mWhat;
        ArrayList<Long> mResults;

        RemoveThread(
                CountDownLatch startLatch,
                CountDownLatch endLatch,
                Handler handler,
                Thread blockingThread,
                int what) {
            super();
            mStartLatch = startLatch;
            mEndLatch = endLatch;
            mHandler = handler;
            mBlockingThread = blockingThread;
            mWhat = what;
            mResults = new ArrayList<>();
        }

        @Override
        public void run() {
            Log.d(TAG, "Remove thread started for message " + mWhat);
            try {
                mStartLatch.await();
                // We wait for mBlockingThread to complete in case it is still in the process of
                // enqueuing messages, to ensure that the expected number of messages is removed.
                if (mBlockingThread != null) {
                    mBlockingThread.join();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            long now = SystemClock.uptimeMillis();
            long startTimeNS = SystemClock.elapsedRealtimeNanos();
            mHandler.removeMessages(mWhat);
            long endTimeNS = SystemClock.elapsedRealtimeNanos();

            mResults.add(endTimeNS - startTimeNS);
            mEndLatch.countDown();
        }
    }

    class TestHandler extends Handler {
        TestHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {}
    }

    void reportPerf(
            String prefix,
            int threadCount,
            int perThreadMessageCount,
            EnqueueThread[] enqueueThreads,
            RemoveThread[] removeThreads) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        // Accumulate enqueue/remove results.
        ArrayList<Long> enqueueResults = new ArrayList<>();
        for (EnqueueThread thread : enqueueThreads) {
            enqueueResults.addAll(thread.mResults);
        }
        Stats stats = new Stats(enqueueResults);

        ArrayList<Long> removeResults = new ArrayList<>();
        if (removeThreads != null) {
            for (RemoveThread thread : removeThreads) {
                removeResults.addAll(thread.mResults);
            }
        }
        Stats removeStats = (removeResults.size() > 0) ? new Stats(removeResults) : null;

        Log.d(TAG, "Reporting perf now");

        Bundle status = new Bundle();
        status.putLong(prefix + "_median_ns", stats.getMedian());
        status.putLong(prefix + "_mean_ns", (long) stats.getMean());
        status.putLong(prefix + "_min_ns", stats.getMin());
        status.putLong(prefix + "_max_ns", stats.getMax());
        status.putLong(prefix + "_stddev_ns", (long) stats.getStandardDeviation());
        if (removeStats != null) {
            status.putLong(prefix + "_remove_median_ns", removeStats.getMedian());
            status.putLong(prefix + "_remove_mean_ns", (long) removeStats.getMean());
            status.putLong(prefix + "_remove_min_ns", removeStats.getMin());
            status.putLong(prefix + "_remove_max_ns", removeStats.getMax());
            status.putLong(prefix + "_remove_stddev_ns", (long) removeStats.getStandardDeviation());
        }
        status.putLong(prefix + "_nr_threads", threadCount);
        status.putLong(prefix + "_msgs_per_thread", perThreadMessageCount);
        instrumentation.sendStatus(Activity.RESULT_OK, status);
    }

    HandlerThread mHandlerThread;

    private void fillMessagesArray(Message[] messages, int what, int startIdx, int endIdx) {
        for (int i = startIdx; i < endIdx; i++) {
            messages[i] = mHandlerThread.getThreadHandler().obtainMessage(what);
        }
    }

    private void fillMessagesArray(Message[] messages) {
        fillMessagesArray(messages, DEFAULT_MESSAGE_WHAT, 0, messages.length);
    }

    private void startTestAndWaitOnThreads(
            CountDownLatch threadStartLatch, CountDownLatch threadEndLatch) {
        try {
            threadStartLatch.countDown();
            Log.e(TAG, "Test threads started");
            threadEndLatch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        Log.e(TAG, "Test threads ended, quitting handler thread");
    }

    /**
     * Benchmark for enqueueing messages at the front of the message queue.
     *
     * <p>This benchmark adds messages to the front of the message queue from multiple threads. It
     * measures the latency of enqueue operations.
     */
    @Test
    public void benchmarkEnqueueAtFrontOfQueue() {
        CountDownLatch threadStartLatch = new CountDownLatch(1);
        CountDownLatch threadEndLatch = new CountDownLatch(THREAD_COUNT);
        Message[] messages = new Message[TOTAL_MESSAGE_COUNT];
        fillMessagesArray(messages);

        long[] delays = new long[TOTAL_MESSAGE_COUNT];

        TestHandler handler = new TestHandler(mHandlerThread.getLooper());
        EnqueueThread[] enqueueThreads = new EnqueueThread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            EnqueueThread thread =
                    new EnqueueThread(
                            threadStartLatch,
                            threadEndLatch,
                            handler,
                            i * PER_THREAD_MESSAGE_COUNT,
                            messages,
                            delays);
            enqueueThreads[i] = thread;
            thread.start();
        }

        startTestAndWaitOnThreads(threadStartLatch, threadEndLatch);

        reportPerf("enqueueAtFront", THREAD_COUNT, PER_THREAD_MESSAGE_COUNT, enqueueThreads, null);
    }

    /** Fill array with random delays, for benchmarkEnqueueDelayed */
    public long[] fillDelayArray() {
        long[] delays = new long[TOTAL_MESSAGE_COUNT];
        Random rand = new Random(0xDEADBEEF);
        for (int i = 0; i < TOTAL_MESSAGE_COUNT; i++) {
            delays[i] = Math.abs(rand.nextLong() % 5000);
        }
        return delays;
    }

    /**
     * Benchmark for enqueuing delayed messages to the message queue.
     *
     * <p>This benchmark adds messages at random points in the message queue from multiple threads.
     * It measures the latency of enqueue operations.
     */
    @Test
    public void benchmarkEnqueueDelayed() {
        CountDownLatch threadStartLatch = new CountDownLatch(1);
        CountDownLatch threadEndLatch = new CountDownLatch(THREAD_COUNT);
        Message[] messages = new Message[TOTAL_MESSAGE_COUNT];
        fillMessagesArray(messages);

        long[] delays = fillDelayArray();

        TestHandler handler = new TestHandler(mHandlerThread.getLooper());
        EnqueueThread[] enqueueThreads = new EnqueueThread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            EnqueueThread thread =
                    new EnqueueThread(
                            threadStartLatch,
                            threadEndLatch,
                            handler,
                            i * PER_THREAD_MESSAGE_COUNT,
                            messages,
                            delays);
            enqueueThreads[i] = thread;
            thread.start();
        }

        startTestAndWaitOnThreads(threadStartLatch, threadEndLatch);

        reportPerf("enqueueDelayed", THREAD_COUNT, PER_THREAD_MESSAGE_COUNT, enqueueThreads, null);
    }

    /**
     * Benchmark for enqueuing delayed messages and removing them from the message queue.
     *
     * <p>This benchmark adds messages at random points in the message queue from multiple threads,
     * with each thread enqueuing messages with a different 'what' field. After a thread has
     * completed adding its messages, another thread removes them. This measures the latency of
     * enqueue and remove operations.
     */
    @Test
    public void benchmarkConcurrentEnqueueDelayedAndRemove() {
        // taskThreadCount threads are used for both enqueuing and removing.
        final int taskThreadCount = THREAD_COUNT / 2;
        final int messageCount = taskThreadCount * PER_THREAD_MESSAGE_COUNT;

        // We use taskThreadCount * 2 in case THREAD_COUNT is not an even number.
        CountDownLatch threadStartLatch = new CountDownLatch(1);
        CountDownLatch threadEndLatch = new CountDownLatch(taskThreadCount * 2);

        long[] delays = fillDelayArray();
        TestHandler handler = new TestHandler(mHandlerThread.getLooper());

        // Fill with taskThreadCount blocks of PER_THREAD_MESSAGE_COUNT messages.
        Message[] messages = new Message[messageCount];
        for (int i = 0; i < taskThreadCount; i++) {
            fillMessagesArray(
                    messages,
                    /* what= */ i,
                    /* startIdx= */ i * PER_THREAD_MESSAGE_COUNT,
                    /* endIdx= */ (i + 1) * PER_THREAD_MESSAGE_COUNT);
        }

        EnqueueThread[] enqueueThreads = new EnqueueThread[taskThreadCount];
        RemoveThread[] removeThreads = new RemoveThread[taskThreadCount];

        // Start by enqueuing the first block of messages.
        enqueueThreads[0] =
                new EnqueueThread(
                        threadStartLatch,
                        threadEndLatch,
                        handler,
                        /* startIdx= */ 0,
                        messages,
                        delays);
        enqueueThreads[0].start();

        for (int i = 1; i < taskThreadCount; i++) {
            // Remove messages from the corresponding enqueue thread from the previous iteration.
            removeThreads[i - 1] =
                    new RemoveThread(
                            threadStartLatch,
                            threadEndLatch,
                            handler,
                            enqueueThreads[i - 1],
                            /* what= */ i - 1);
            removeThreads[i - 1].start();

            // Concurrently enqueue the next set of messages.
            enqueueThreads[i] =
                    new EnqueueThread(
                            threadStartLatch,
                            threadEndLatch,
                            handler,
                            i * PER_THREAD_MESSAGE_COUNT,
                            messages,
                            delays);
            enqueueThreads[i].start();
        }

        // End by removing the last block of messages.
        removeThreads[taskThreadCount - 1] =
                new RemoveThread(
                        threadStartLatch,
                        threadEndLatch,
                        handler,
                        enqueueThreads[taskThreadCount - 1],
                        /* what= */ taskThreadCount - 1);
        removeThreads[taskThreadCount - 1].start();

        startTestAndWaitOnThreads(threadStartLatch, threadEndLatch);

        reportPerf(
                "concurrentEnqueueDelayedAndRemove",
                THREAD_COUNT,
                PER_THREAD_MESSAGE_COUNT,
                enqueueThreads,
                removeThreads);
    }

    /**
     * Benchmark for enqueueing and removing messages from a single thread.
     *
     * <p>This benchmark measures the time it takes to enqueue a message, then remove it. This is
     * repeated multiple times.
     */
    @Test
    public void benchmarkSingleThreadedEnqueueAndRemove() throws InterruptedException {
        final CountDownLatch threadEndLatch = new CountDownLatch(1);
        final TestHandler handler = new TestHandler(mHandlerThread.getLooper());

        Runnable runTest =
                new Runnable() {
                    @Override
                    public void run() {
                        // Can't make this an @Rule otherwise the multi threaded tests that don't
                        // use
                        // PerfStatusReporter will throw the error:
                        // "java.lang.IllegalStateException: The benchmark hasn't finished"
                        final PerfStatusReporter perfStatusReporter = new PerfStatusReporter();
                        final BenchmarkState state = perfStatusReporter.getBenchmarkState();

                        while (state.keepRunning()) {
                            Message m = handler.obtainMessage(DEFAULT_MESSAGE_WHAT);
                            handler.sendMessageDelayed(m, 10_000);
                            handler.removeMessages(DEFAULT_MESSAGE_WHAT);
                        }

                        state.sendFullStatusReport(
                                InstrumentationRegistry.getInstrumentation(),
                                "singleThreadedEnqueueAndRemove");

                        threadEndLatch.countDown();
                    }
                };

        handler.post(runTest);

        threadEndLatch.await();
    }
}
