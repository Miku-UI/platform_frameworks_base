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

package android.os;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.MessageQueue.IdleHandler;
import android.os.MessageQueue.OnFileDescriptorEventListener;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests internal MessageQueue APIs. For public APIs, please use the MessageQueueTest in CTS.
 */
@RunWith(AndroidJUnit4.class)
public class MessageQueueTest {

    private static final long JOIN_TIMEOUT = 30_000;
    private static final long LONG_DELAY_MS = 1000;
    private static final long SHORT_DELAY_MS = 100;

    private static final int WHAT_FIRST = 0;
    private static final int WHAT_MIDDLE = 1;
    private static final int WHAT_LAST = 2;

    private AssertableHandlerThread thread;
    private MessageQueue queue;
    private Handler handler;
    private TestLooperManager tlm;

    @Before
    public void setUp() {
        thread = new AssertableHandlerThread("MessageQueueTestThread");
        thread.start();

        Looper looper = thread.getLooper();
        handler = new Handler(looper);
        tlm = InstrumentationRegistry.getInstrumentation().acquireLooperManager(looper);
        queue = looper.getQueue();
    }

    @After
    public void tearDown() throws Throwable {
        tlm.release();
        if (thread != null) {
            thread.quitAndRethrow();
        }
    }

    @Test
    public void testPeekLastMessage() throws Exception {
        final long future = SystemClock.uptimeMillis() + LONG_DELAY_MS;
        handler.sendEmptyMessageAtTime(0, future);
        handler.sendEmptyMessageAtTime(1, future + 1);
        handler.sendEmptyMessageAtTime(4, future + 4);
        handler.sendEmptyMessageAtTime(3, future + 3);
        handler.sendEmptyMessageAtTime(2, future + 2);
        Message m = queue.peekLastMessageForTest();
        assertEquals(m.what, 4);
    }

    @Test
    public void testPeekLastMessageWithEqualWhen() throws Exception {
        final long future = SystemClock.uptimeMillis() + LONG_DELAY_MS;
        handler.sendEmptyMessageAtTime(WHAT_FIRST, future);
        // We send a large number of messages of the same latest-timestamp to make it more likely
        // that we hit a race condition in the distribution of messages within the internal data
        // structures used in different MQ implementations.
        for (int i = 0; i < 100; i++) {
            handler.sendEmptyMessageAtTime(WHAT_MIDDLE, future + 1);
        }
        handler.sendEmptyMessageAtTime(WHAT_LAST, future + 1);
        Message m = queue.peekLastMessageForTest();
        assertEquals(m.what, WHAT_LAST);
    }

    @Test
    public void testPeekLastMessageWithNoMessages() throws Exception {
        assertNull(queue.peekLastMessageForTest());
    }

    @Test
    public void testResetClearsMessages() throws Exception {
        for (int i = 0; i < 100; i++) {
            handler.sendEmptyMessageDelayed(0, LONG_DELAY_MS);
        }

        resetQueue();

        assertNull(queue.peekLastMessageForTest());
    }

    @Test
    public void testResetClearsMessages_differentThread() throws Exception {
        for (int i = 0; i < 100; i++) {
            handler.sendEmptyMessageDelayed(0, LONG_DELAY_MS);
        }

        queue.resetForTest();

        assertNull(queue.peekLastMessageForTest());
    }

    @Test
    public void testResetAllowsAdditionalMessages() throws Exception {
        resetQueue();
        assertTrue(handler.sendEmptyMessageDelayed(1, LONG_DELAY_MS));
        assertEquals(1, queue.peekLastMessageForTest().what);
    }

    @Test
    public void testResetAllowsAdditionalMessages_differentThread() throws Exception {
        queue.resetForTest();
        assertTrue(handler.sendEmptyMessageDelayed(1, LONG_DELAY_MS));
        assertEquals(1, queue.peekLastMessageForTest().what);
    }

    @Test
    public void testResetClearsSyncBarrierTokens() throws Exception {
        int barrierToken1 = queue.postSyncBarrier();
        int barrierToken2 = queue.postSyncBarrier();

        resetQueue();

        int barrierToken3 = queue.postSyncBarrier();
        assertEquals(barrierToken1, barrierToken3);
    }

    @Test
    @org.junit.Ignore("b/458068359") // The looper for TestLooperManager will never be idle.
    public void testResetClearsIdleHandlers() throws Exception {
        final CountDownLatch idleLatch = new CountDownLatch(2);

        IdleHandler idleHandler = new IdleHandler() {
            @Override
            public boolean queueIdle() {
                idleLatch.countDown();
                return false;
            }
        };

        // We wait before adding the idle handler to avoid capturing the initial idle.
        SystemClock.sleep(SHORT_DELAY_MS);
        queue.addIdleHandler(idleHandler);

        // Sending a message will make the queue active then idle, which will count down the latch.
        handler.sendEmptyMessage(0);
        SystemClock.sleep(SHORT_DELAY_MS);
        assertEquals(idleLatch.getCount(), 1);

        resetQueue();

        // Sending a message will make the queue active, then idle again.
        handler.sendEmptyMessage(0);
        SystemClock.sleep(SHORT_DELAY_MS);

        // Since the idle handler has been cleared by resetForTest(), our latch should hold the same count.
        assertEquals(idleLatch.getCount(), 1);
    }

    @Test
    public void testResetClearsFileDescriptorEventListeners() throws Exception {
        final CountDownLatch fdEventLatch = new CountDownLatch(2);

        final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        try (final FileInputStream reader = new AutoCloseInputStream(pipe[0]);
                final FileOutputStream writer = new AutoCloseOutputStream(pipe[1])) {
            OnFileDescriptorEventListener readerCallback =
                    new OnFileDescriptorEventListener() {
                        @Override
                        public int onFileDescriptorEvents(FileDescriptor fd, int events) {
                            assertEquals(pipe[0].getFileDescriptor(), fd);
                            if ((events & OnFileDescriptorEventListener.EVENT_INPUT) != 0) {
                                fdEventLatch.countDown();
                            } else {
                                fail("Unexpected event: " + events);
                            }
                            return 0;
                        }
                    };

            queue.addOnFileDescriptorEventListener(
                    reader.getFD(), OnFileDescriptorEventListener.EVENT_INPUT, readerCallback);

            // Writing to the pipe should count down the latch.
            writer.write(0);
            writer.flush();
            // Wait for the looper to catch up and run the callback.
            syncWait(handler);
            assertEquals(1, fdEventLatch.getCount());

            resetQueue();

            // Writing to the pipe shouldn't affect the latch this time, since the listener is no
            // longer registered with the message queue.
            writer.write(0);
            writer.flush();
            // Wait for the looper to catch up and run the callback.
            syncWait(handler);
            assertEquals(1, fdEventLatch.getCount());
        }
    }

    private void syncWait(Handler handler) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        handler.post(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        });
        tlm.execute(tlm.next());
        assertTrue("Handler got stuck.", latch.await(LONG_DELAY_MS, TimeUnit.MILLISECONDS));
    }

    private void resetQueue() {
        assertTrue(handler.postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                queue.resetForTest();
            }
        }));
        tlm.execute(tlm.next());
    }

    /**
     * A HandlerThread that propagates exceptions out of the event loop
     * instead of crashing the process.
     */
    private static final class AssertableHandlerThread extends HandlerThread {
        private Throwable mThrowable;

        public AssertableHandlerThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            try {
                super.run();
            } catch (Throwable t) {
                mThrowable = t;
            }
        }

        public void quitAndRethrow() throws Throwable {
            quitSafely();
            join(JOIN_TIMEOUT);
            if (isAlive()) {
                throw new RuntimeException(
                        "Looper thread did not quit in 30s", new ThreadState(getStackTrace()));
            }
            if (mThrowable != null) {
                throw mThrowable;
            }
        }
    }

    private static final class ThreadState extends Exception {
        ThreadState(StackTraceElement[] stack) {
            setStackTrace(stack);
        }
    }

}
