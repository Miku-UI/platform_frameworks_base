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
import android.platform.test.annotations.DisabledOnRavenwood;

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

    private static final long LONG_DELAY_MS = 1000;
    private static final long SHORT_DELAY_MS = 100;

    private HandlerThread thread;
    private MessageQueue queue;
    private Handler handler;

    @Before
    public void setUp() {
        thread = new HandlerThread("MessageQueueTestThread");
        thread.start();
        queue = thread.getLooper().getQueue();
        handler = new Handler(thread.getLooper());
    }

    @After
    public void tearDown() {
        if (thread != null) {
            thread.quitSafely();
        }
    }

    @Test
    public void testPeekLastMessage() throws Exception {
        handler.sendEmptyMessageDelayed(0, LONG_DELAY_MS);
        handler.sendEmptyMessageDelayed(1, LONG_DELAY_MS + 1);
        handler.sendEmptyMessageDelayed(4, LONG_DELAY_MS + 4);
        handler.sendEmptyMessageDelayed(3, LONG_DELAY_MS + 3);
        handler.sendEmptyMessageDelayed(2, LONG_DELAY_MS + 2);
        Message m = queue.peekLastMessageForTest();
        assertEquals(m.what, 4);
    }

    @Test
    public void testPeekLastMessageWithEqualWhen() throws Exception {
        handler.sendEmptyMessageDelayed(0, LONG_DELAY_MS);
        handler.sendEmptyMessageDelayed(4, LONG_DELAY_MS + 1);
        handler.sendEmptyMessageDelayed(1, LONG_DELAY_MS + 1);
        Message m = queue.peekLastMessageForTest();
        assertEquals(m.what, 1);
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

        queue.resetForTest();

        assertNull(queue.peekLastMessageForTest());
    }

    @Test
    public void testResetAllowsAdditionalMessages() throws Exception {
        queue.resetForTest();
        assertTrue(handler.sendEmptyMessageDelayed(1, LONG_DELAY_MS));
        assertEquals(1, queue.peekLastMessageForTest().what);
    }

    @Test
    public void testResetClearsSyncBarrierTokens() throws Exception {
        int barrierToken1 = queue.postSyncBarrier();
        int barrierToken2 = queue.postSyncBarrier();

        queue.resetForTest();

        int barrierToken3 = queue.postSyncBarrier();
        assertEquals(barrierToken1, barrierToken3);
    }

    @Test
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

        queue.resetForTest();

        // Sending a message will make the queue active, then idle again.
        handler.sendEmptyMessage(0);
        SystemClock.sleep(SHORT_DELAY_MS);

        // Since the idle handler has been cleared by resetForTest(), our latch should hold the same count.
        assertEquals(idleLatch.getCount(), 1);
    }

    @Test
    @DisabledOnRavenwood(blockedBy = android.os.ParcelFileDescriptor.class)
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

            queue.resetForTest();

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
        assertTrue("Handler got stuck.", latch.await(LONG_DELAY_MS, TimeUnit.MILLISECONDS));
    }

}
