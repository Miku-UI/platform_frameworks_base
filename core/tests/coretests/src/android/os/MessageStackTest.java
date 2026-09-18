/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License athasEqualMessages
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

import static android.os.Message.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
public final class MessageStackTest {
    private static final String TAG = "MessageStackTest";

    private static final int THREAD_COUNT = 8;
    private static final int PER_THREAD_MESSAGE_COUNT = 10000;

    /**
     * Verify stack size after pushing messages.
     */
    @Test
    public void testPush() {
        MessageStack stack = new MessageStack();
        for (int i = 0; i < 10; i++) {
            stack.pushMessage(new Message());
        }
        assertEquals(10, stack.sizeForTest());
    }

    /**
     * Check that the stack doesn't sweep already-removed messages.
     */
    @Test
    public void testPushRemovedMessage() {
        MessageStack stack = new MessageStack();
        Message m = new Message();
        m.markRemoved();
        stack.pushMessage(m);
        stack.heapSweep();
        assertEquals(0, stack.combinedHeapSizesForTest());
    }

    /**
     * Verify quitting state
     */
    @Test
    public void testQuitting() {
        MessageStack stack = new MessageStack();
        for (int i = 0; i < 10; i++) {
            stack.pushMessage(new Message());
        }

        assertFalse(stack.isQuitting());

        stack.pushQuitting(42);
        assertFalse(stack.pushMessage(new Message()));
        assertTrue(stack.isQuitting());
        assertEquals(stack.getQuittingTimestamp(), 42);
    }

    /**
     * Verify heap size after sweeping messages from a MessageStack.
     */
    @Test
    public void testHeapSweep() {
        MessageStack stack = new MessageStack();
        for (int i = 0; i < 10; i++) {
            stack.pushMessage(new Message());
        }
        stack.heapSweep();
        assertEquals(10, stack.combinedHeapSizesForTest());
    }

    /**
     * Verify stack and heap sizes after pushing, sweeping, and removing messages.
     */
    @Test
    public void testPop() {
        MessageStack stack = new MessageStack();
        for (int i = 0; i < 10; i++) {
            stack.pushMessage(new Message());
        }
        stack.heapSweep();

        Message m = stack.pop(false);
        assertEquals(9, stack.sizeForTest());
        assertEquals(9, stack.combinedHeapSizesForTest());
    }

    /**
     * Verify stack and heap sizes after updating the messages in the freelist and removing them.
     * Also check that the remaining messages in the stack didn't meet the previous conditions for
     * removal.
     */
    @Test
    public void testmoveMatchingToFreelistAndDrainFreelist() {
        MessageStack stack = new MessageStack();
        Handler h = new Handler(Looper.getMainLooper());
        int removeWhat = 1;
        int keepWhat = 2;
        int neverPushedWhat = 3;

        // Interleave to-remove and to-keep messages.
        for (int i = 0; i < 5; i++) {
            stack.pushMessage(Message.obtain(h, removeWhat));
            stack.pushMessage(Message.obtain(h, keepWhat));
        }
        stack.heapSweep();
        assertEquals(5, stack.moveMatchingToFreelist(new MatchHandlerWhatAndObject(),
                h, removeWhat, null, null, 0));

        // Try deleting a message we never pushed
        assertEquals(0, stack.moveMatchingToFreelist(new MatchHandlerWhatAndObject(),
                h, neverPushedWhat, null, null, 0));

        assertEquals(5, stack.freelistSizeForTest());
        stack.drainFreelist();
        assertEquals(0, stack.freelistSizeForTest());

        assertEquals(5, stack.sizeForTest());
        assertEquals(5, stack.combinedHeapSizesForTest());

        int initialSize = 5;
        for (int i = 0; i < initialSize; i++) {
            Message m = stack.pop(false);
            assertEquals(m.what, keepWhat);
        }

        assertEquals(0, stack.sizeForTest());
        assertEquals(0, stack.combinedHeapSizesForTest());
    }

    /**
     * Test our basic message search.
     */
    @Test
    public void testHasMessages() {
        MessageStack stack = new MessageStack();
        Handler h = new Handler(Looper.getMainLooper());
        int skipWhat = 1;
        int findWhat = 2;

        // Interleave message types
        for (int i = 0; i < 5; i++) {
            stack.pushMessage(Message.obtain(h, skipWhat));
            stack.pushMessage(Message.obtain(h, findWhat));
        }

        assertTrue(stack.hasMessages(new MatchHandlerWhatAndObject(),
                h, findWhat, null, null, 0));

        assertFalse(stack.hasMessages(new MatchHandlerWhatAndObject(),
                h, 3, null, null, 0));

        stack.moveMatchingToFreelist(new MatchHandlerWhatAndObject(),
                h, findWhat, null, null, 0);

        assertFalse(stack.hasMessages(new MatchHandlerWhatAndObject(),
                h, findWhat, null, null, 0));
    }

    /**
     * Push messages from multiple threads and verify stack and heap sizes.
     */
    @Test
    public void testConcurrentPush() throws InterruptedException {
        MessageStack stack = new MessageStack();

        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            new Thread(() -> {
                for (int j = 0; j < PER_THREAD_MESSAGE_COUNT; j++) {
                    stack.pushMessage(new Message());
                }
                latch.countDown();
            }).start();
        }
        latch.await();

        stack.heapSweep();

        int expectedSize = THREAD_COUNT * PER_THREAD_MESSAGE_COUNT;
        assertEquals(expectedSize, stack.sizeForTest());
        assertEquals(expectedSize, stack.combinedHeapSizesForTest());
    }

    /**
     * Push and pop messages from multiple threads and verify stack and heap sizes.
     */
    @Test
    public void testConcurrentPushAndPop() throws InterruptedException {
        MessageStack stack = new MessageStack();

        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT - 1; i++) {
            new Thread(() -> {
                for (int j = 0; j < PER_THREAD_MESSAGE_COUNT; j++) {
                    stack.pushMessage(new Message());
                }
                latch.countDown();
            }).start();
        }
        new Thread(() -> {
            for (int j = 0; j < PER_THREAD_MESSAGE_COUNT; j++) {
                // Pop until non-null result (i.e. until a message has been added by one of the
                // previous threads).
                do {
                    stack.heapSweep();
                } while (stack.pop(false) == null);
            }
            latch.countDown();
        }).start();
        latch.await();

        stack.heapSweep();

        int expectedSize = (THREAD_COUNT - 2) * PER_THREAD_MESSAGE_COUNT;
        assertEquals(expectedSize, stack.sizeForTest());
        assertEquals(expectedSize, stack.combinedHeapSizesForTest());
    }

    /**
     * Peek and remove messages and verify stack and heap sizes.
     */
    @Test
    public void testPeekAndRemove() {
        MessageStack stack = new MessageStack();
        for (int i = 0; i < 10; i++) {
            stack.pushMessage(new Message());
        }
        stack.heapSweep();

        Message m = stack.peek(false);
        assertEquals(10, stack.sizeForTest());
        assertEquals(10, stack.combinedHeapSizesForTest());

        stack.remove(m);
        assertEquals(9, stack.sizeForTest());
        assertEquals(9, stack.combinedHeapSizesForTest());
    }

    /**
     * Verify removing a message that was at the top during a heapSweep, but is no longer at the
     * top of the stack due to new messages being pushed.
     */
    @Test
    public void testRemoveMessageAfterPushingOnTopOfIt() {
        MessageStack stack = new MessageStack();
        Handler h = new Handler(Looper.getMainLooper());
        Message m1 = Message.obtain(h, 1);
        Message m2 = Message.obtain(h, 2);

        stack.pushMessage(m1);
        stack.pushMessage(m2); // m2 is now at the top
        assertEquals(2, stack.sizeForTest());

        // m2 was at the top of the stack, so its prev link will be null after the sweep.
        stack.heapSweep();
        assertEquals(2, stack.combinedHeapSizesForTest());

        // Push a new message on top of m2.
        Message m3 = Message.obtain(h, 3);
        stack.pushMessage(m3);
        assertEquals(3, stack.sizeForTest());
        // Only m1 and m2 are in the heap, since `heapSweep` is not invoked after m3 is pushed.
        assertEquals(2, stack.combinedHeapSizesForTest());

        // Now remove m2. This will fail the sTop.CAS in removeMessage() because m3, not m2, is the
        // top, forcing the creation of backlinks before removal.
        stack.remove(m2);

        // Verify m2 is gone but m1 and m3 remain.
        assertEquals(2, stack.sizeForTest());
        // Only m1 is in the heap: m2 was removed from heap and m3 is never in the heap (because
        // `heapSweep` is not invoked since m3 was pushed to the stack.)
        assertEquals(1, stack.combinedHeapSizesForTest());
        assertFalse(stack.hasMessages(new MatchHandlerWhatAndObject(),
                h, 2, null, null, 0));
        assertTrue(stack.hasMessages(new MatchHandlerWhatAndObject(),
                h, 1, null, null, 0));
        assertTrue(stack.hasMessages(new MatchHandlerWhatAndObject(),
                h, 3, null, null, 0));
    }

    /**
     * Peek messages from a stack with only removed messages and verify that the return is null.
     */
    @Test
    public void testPeekOnlyRemovedMessages() {
        MessageStack stack = new MessageStack();
        Handler h = new Handler(Looper.getMainLooper());
        int removeWhat = 1;

        for (int i = 0; i < 10; i++) {
            stack.pushMessage(Message.obtain(h, removeWhat));
        }
        stack.heapSweep();
        stack.moveMatchingToFreelist(new MatchHandlerWhatAndObject(),
                h, removeWhat, null, null, 0);

        assertNull(stack.peek(false));
    }

    /**
     * Peek messages from a stack with only removed messages and verify stack and heap sizes.
     */
    @Test
    public void testPeekRemovedMessagesAndDrainFreelist() {
        MessageStack stack = new MessageStack();
        Handler h = new Handler(Looper.getMainLooper());
        int removeWhat = 1;

        for (int i = 0; i < 10; i++) {
            stack.pushMessage(Message.obtain(h, removeWhat));
        }
        stack.heapSweep();
        assertEquals(10, stack.sizeForTest());
        assertEquals(10, stack.combinedHeapSizesForTest());

        stack.moveMatchingToFreelist(new MatchHandlerWhatAndObject(),
                h, removeWhat, null, null, 0);
        assertEquals(0, stack.sizeForTest());
        assertEquals(10, stack.freelistSizeForTest());
        assertEquals(10, stack.combinedHeapSizesForTest());

        assertNull(stack.peek(false));
        assertEquals(0, stack.sizeForTest());
        assertEquals(10, stack.freelistSizeForTest());
        assertEquals(0, stack.combinedHeapSizesForTest());

        stack.drainFreelist();
        assertEquals(0, stack.freelistSizeForTest());
    }

    /**
     * Verify that peek() correctly skips over removed messages and returns the first
     * available non-removed message. Also verifies that the skipped messages are removed
     * from the heap.
     */
    @Test
    public void testPeekSkipsRemovedMessages() {
        MessageStack stack = new MessageStack();
        Handler h = new Handler(Looper.getMainLooper());
        int removeWhat = 1;
        int keepWhat = 2;

        // Push 5 messages to keep, with increasing 'when'
        for (int i = 0; i < 5; i++) {
            Message m = Message.obtain(h, keepWhat);
            m.when = 100L + i;
            stack.pushMessage(m);
        }
        // Push 5 messages to remove, with 'when' that interleaves them
        for (int i = 0; i < 5; i++) {
            Message m = Message.obtain(h, removeWhat);
            // earlier 'when' so they are at the top of the min-heap
            m.when = 95L + i;
            stack.pushMessage(m);
        }

        stack.heapSweep();
        assertEquals(10, stack.sizeForTest());
        assertEquals(10, stack.combinedHeapSizesForTest());

        // Mark the 'removeWhat' messages as removed
        stack.moveMatchingToFreelist(new MatchHandlerWhatAndObject(),
                h, removeWhat, null, null, 0);

        // At this point, the 5 messages are marked as removed but are still in the heap.
        // The freelist contains these 5 messages.
        assertEquals(5, stack.sizeForTest()); // sizeForTest only counts non-removed
        assertEquals(5, stack.freelistSizeForTest());
        assertEquals(10, stack.combinedHeapSizesForTest());

        // Now, peek the stack. It should skip the 5 removed messages and return the first
        // message with 'keepWhat'.
        Message m = stack.peek(false);
        assertNotNull(m);
        assertEquals(keepWhat, m.what);
        assertEquals(100, m.when); // The first 'keep' message

        // After peeking, the 5 removed messages should have been purged from the heap.
        // The peeked message is still in the heap.
        assertEquals(5, stack.sizeForTest());
        assertEquals(5, stack.freelistSizeForTest());
        assertEquals(5, stack.combinedHeapSizesForTest());
    }

    /*
     * Grow the underlying heaps and ensure that they shrink after item deletion.
     */
    @Test
    public void testMessageHeapShrunk() {
        MessageStack stack = new MessageStack();
        final int initialHeapCapacity = stack.combinedHeapCapacitiesForTest();
        final int numMessagesToPush = 2 * initialHeapCapacity;
        for (int i = 0; i < numMessagesToPush; i++) {
            stack.pushMessage(new Message());
        }
        stack.heapSweep();
        final int grownHeapCapacity = stack.combinedHeapCapacitiesForTest();
        assertEquals(numMessagesToPush, stack.moveMatchingToFreelist(new MatchAllMessages(),
                null, 0, null, null, 0));
        stack.drainFreelist();
        assertTrue("Expected stack to shrink: " + grownHeapCapacity + " <= "
                + stack.combinedHeapCapacitiesForTest(),
                grownHeapCapacity > stack.combinedHeapCapacitiesForTest());
    }

    /*
     * Add two messages to stack and check that removing messages with MatchAllMessages only removes
     * one message if firstMatchOnly is true.
     */
    @Test
    public void testMoveSyncBarrierToFreelist() {
        MessageStack stack = new MessageStack();
        int token1 = 123;
        int token2 = 456;


        Message msg1 = Message.obtain();
        Message msg2 = Message.obtain();
        msg1.arg1 = token1;
        msg2.arg1 = token1;

        Message msg3 = Message.obtain();
        msg3.arg1 = token2;

        stack.pushMessage(msg1);
        stack.pushMessage(msg2);
        stack.pushMessage(msg3);

        assertTrue(stack.moveSyncBarrierToFreelist(token1));
        assertEquals(2, stack.sizeForTest());
        assertEquals(1, stack.freelistSizeForTest());
    }
}
