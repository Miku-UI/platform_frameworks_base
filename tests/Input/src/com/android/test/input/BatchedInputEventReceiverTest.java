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

package com.android.test.input;

import static com.android.cts.input.inputeventmatchers.InputEventMatchersKt.withMotionAction;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import android.os.HandlerThread;
import android.os.Looper;
import android.view.BatchedInputEventReceiver;
import android.view.Choreographer;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import com.android.compatibility.common.util.PollingCheck;
import com.android.cts.input.BlockingQueueEventVerifier;
import com.android.cts.input.MotionEventBuilder;
import com.android.cts.input.PointerBuilder;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class BatchedInputEventReceiverTest {
    private static final String TAG = "BatchedInputEventReceiverTest";

    private static class TestBatchedInputEventReceiver extends BatchedInputEventReceiver {
        // If set, event receiver will disable, and then immediately enable batching after handling
        // next pending batched input event - i.e. after scheduling batched input, but before the
        // scheduled batched input is actually consumed.
        boolean mResetBatchingAfterNextPendingEvent = false;

        // Keeps track of received input events.
        private final LinkedBlockingQueue<InputEvent> mInputEvents = new LinkedBlockingQueue<>();
        private final BlockingQueueEventVerifier mVerifier;

        TestBatchedInputEventReceiver(InputChannel channel, Looper looper,
                Choreographer choreographer) {
            super(channel, looper, choreographer);
            mVerifier = new BlockingQueueEventVerifier(mInputEvents);
        }

        @Override
        public void onBatchedInputEventPending(int source) {
            super.onBatchedInputEventPending(source);

            if (mResetBatchingAfterNextPendingEvent) {
                mResetBatchingAfterNextPendingEvent = false;
                setBatchingEnabled(false);
                setBatchingEnabled(true);
            }
        }

        @Override
        public void onInputEvent(InputEvent event) {
            if (event instanceof MotionEvent motionEvent) {
                mInputEvents.offer(MotionEvent.obtain(motionEvent));
            } else {
                throw new RuntimeException("Received " + event + " is not a motion");
            }
            finishInputEvent(event, true /* handled */);
        }

        void assertReceivedMotion(Matcher<MotionEvent> matcher) {
            mVerifier.assertReceivedMotion(matcher);
        }
    }

    @Rule
    public MockitoRule mMockitoJUnitRule = MockitoJUnit.rule();

    private InputChannel[] mChannels;

    // Use the custom class that exposes the Handler for posting Runnables
    private HandlerThread mReceiverThread;
    private HandlerThread mSenderThread;
    private SpyInputEventSender mSender;
    private TestBatchedInputEventReceiver mBatchedReceiver;

    @Mock
    private Choreographer mMockChoreographer;

    // Changed to a public field to match Kotlin's 'var' behavior for easy access/modification
    public long lastChoreographerFrameTimeMs;

    private MotionEvent getTestMouseMotionEvent(int action, long eventTime) {
        // The MotionEventBuilder and PointerBuilder classes are assumed to exist
        return new MotionEventBuilder(action, InputDevice.SOURCE_MOUSE)
                .downTime(eventTime)
                .eventTime(eventTime)
                .pointer(new PointerBuilder(/* id= */ 0, MotionEvent.TOOL_TYPE_MOUSE).x(0f).y(0f))
                .build();
    }

    private void awaitCountDownLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("CountDownLatch await was interrupted", e);
        }
    }

    private void sendInputEventUntilSuccess(int seq, InputEvent event) {
        PollingCheck.waitFor(() -> mSender.sendInputEvent(seq, event));
    }

    @Before
    public void setUp() {
        mChannels = InputChannel.openInputChannelPair("TestChannel");
        mReceiverThread = new HandlerThread("Process input events");
        mSenderThread = new HandlerThread("Send input events");

        lastChoreographerFrameTimeMs  = 995;

        mReceiverThread.start();
        mSenderThread.start();

        Looper senderLooper = mSenderThread.getLooper();
        mSender = new SpyInputEventSender(mChannels[0], senderLooper);

        Looper receiverLooper = mReceiverThread.getLooper();
        mBatchedReceiver = new TestBatchedInputEventReceiver(
                mChannels[1],
                receiverLooper,
                mMockChoreographer
        );
    }

    @After
    public void tearDown() throws Exception {
        mSender.dispose();
        CountDownLatch disposeLatch = new CountDownLatch(1);
        mReceiverThread.getThreadHandler().post(() -> {
            mBatchedReceiver.dispose();
            disposeLatch.countDown();
        });
        mReceiverThread.quitSafely();
        mSenderThread.quitSafely();
    }

    // Consumption of batched input events should continue if batching is disabled, and then enabled
    // again before running the task to consume pending events in response to batching getting
    // disabled.
    @Test
    public void testKeepConsumingEventsAfterBatchingRestart() {
        // Mocking Choreographer to run the posted callback immediately on the HandlerThread.
        doAnswer(invocation -> {
            mReceiverThread.getThreadHandler().post(() -> {
                lastChoreographerFrameTimeMs += 5;
                ((Choreographer.VsyncCallback) invocation.getArgument(1)).onVsync(
                    new Choreographer.FrameData(lastChoreographerFrameTimeMs * 1000000L));
            });
            return null;
        }).when(mMockChoreographer).postVsyncCallback(eq(Choreographer.CALLBACK_INPUT), any());

        int seq = 12;
        // Send ACTION_DOWN, and verify it gets handled by the receiver without batching.
        mSender.sendInputEvent(seq, getTestMouseMotionEvent(MotionEvent.ACTION_DOWN, 900L));
        mBatchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN));
        mSender.assertReceivedFinishedSignal(seq, true);

        mBatchedReceiver.mResetBatchingAfterNextPendingEvent = true;

        // Send hover move events, which will get batched.
        // The receiver resets batching after receiving batched input notification for this event.
        mSender.sendInputEvent(seq + 1, getTestMouseMotionEvent(MotionEvent.ACTION_MOVE, 930L));
        mSender.sendInputEvent(seq + 2, getTestMouseMotionEvent(MotionEvent.ACTION_MOVE, 1010L));

        // Verify that the receiver consumed batched hover move event, even though batching was
        // restarted upon receiving first pending batched input events notification.
        mBatchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE));
        mSender.assertReceivedFinishedSignal(seq + 1, true);
        mBatchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE));
        mSender.assertReceivedFinishedSignal(seq + 2, true);

        // Send another hover move event, and verify it gets consumed in case where batching is not
        // restarted.
        mSender.sendInputEvent(seq + 3, getTestMouseMotionEvent(MotionEvent.ACTION_MOVE, 1060L));
        mBatchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE));

        mSender.assertReceivedFinishedSignal(seq + 3, true);
    }

    // Simulates the situation where the socket is filled (e.g. when under high load) on the
    // Receiver send buffer, which causes finished events to fail and return WOULD_BLOCK. This is
    // important for testing that these failed events are successfully finished later by the caller,
    // which would otherwise cause an ANR.
    @Test
    public void testCancellationFillsFinishSocket() {
        final int seq = 1;
        long eventTime = lastChoreographerFrameTimeMs + 1000L;

        // Send ACTION_DOWN event and verify it is received and finished.
        mSender.sendInputEvent(seq, getTestMouseMotionEvent(MotionEvent.ACTION_DOWN, eventTime));
        mBatchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN));
        mSender.assertReceivedFinishedSignal(seq, true);

        // Build a set to verify the received finished events have the sequence numbers we expect
        // in spite of any reordering which happens due to retries.
        Set<Integer> finishableSeqs = new HashSet<>();

        // Send 100 move messages to fill the socket - the exact number to fill the socket is 87 as
        // determined by InputChannelTest.FinishedMessageCapacityInSocket, however, its useful to
        // err on sending more messages in case InputMessage size  or SOCKET_BUFFER_SIZE ever
        // increases.
        final int moveMessages = 100;

        CountDownLatch senderThreadLatch = new CountDownLatch(1);
        mSenderThread.getThreadHandler().post(
                () -> {
                    for (int i = 1; i <= moveMessages; i++) {
                        sendInputEventUntilSuccess(seq + i,
                                getTestMouseMotionEvent(MotionEvent.ACTION_MOVE,
                                        eventTime + i * 10L));
                        finishableSeqs.add(seq + i);
                    }
                    sendInputEventUntilSuccess(
                            seq + moveMessages + 1,
                            getTestMouseMotionEvent(MotionEvent.ACTION_CANCEL,
                                    eventTime + moveMessages * 10 + 10L)
                    );
                    finishableSeqs.add(seq + moveMessages + 1);
                    mBatchedReceiver.assertReceivedMotion(
                            withMotionAction(MotionEvent.ACTION_CANCEL));
                    // Notify the main thread that the receiver has received the cancel event.
                    senderThreadLatch.countDown();
                }
        );
        awaitCountDownLatch(senderThreadLatch);

        while (!finishableSeqs.isEmpty()) {
            SpyInputEventSender.FinishedSignal signal = mSender.popFinishedSignal();
            assertNotNull("Did not receive 'finished' event", signal);
            assertTrue(finishableSeqs.remove(signal.getSeq()));
        }
        mSender.assertNoEvents();
    }
}
