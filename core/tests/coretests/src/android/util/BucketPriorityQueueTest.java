/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.NoSuchElementException;

/**
 * Unit tests for {@link BucketPriorityQueue}.
 * Run the test by:
 * atest FrameworksCoreTestsRavenwood:android.util.BucketPriorityQueueTest
 */
@SmallTest
@Presubmit
public class BucketPriorityQueueTest {
    /** A simple placeholder class for testing the queue. */
    private static final class TestItem {
        private final String mName;

        private TestItem() {
            mName = "Untitled";
        }

        private TestItem(String name) {
            mName = name;
        }

        @Override
        public String toString() {
            return mName;
        }
    }

    @Test
    public void shouldWorkWithBasicOperations() {
        final BucketPriorityQueue<TestItem> queue = new BucketPriorityQueue<>(/* maxBucket= */5);
        final TestItem item0 = new TestItem();
        final TestItem item2 = new TestItem();
        final TestItem item5 = new TestItem();

        assertTrue(queue.isEmpty());
        assertNull(queue.poll());

        queue.add(item5, 5);
        assertFalse(queue.isEmpty());
        assertEquals(item5, queue.poll());

        queue.add(item0, 0);
        assertEquals(item0, queue.remove());

        queue.add(item5, 5);
        queue.add(item2, 2);
        assertEquals(item2, queue.remove());
        assertEquals(item5, queue.remove());
        assertTrue(queue.isEmpty());
    }

    @Test
    public void shouldBeEmptyAfterClear() {
        final BucketPriorityQueue<TestItem> queue = new BucketPriorityQueue<>(/* maxBucket= */5);
        queue.add(new TestItem(), 1);
        queue.add(new TestItem(), 3);
        assertFalse(queue.isEmpty());

        queue.clear();
        assertTrue(queue.isEmpty());
        assertNull(queue.poll());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionOnNegativePriority() {
        final BucketPriorityQueue<TestItem> queue = new BucketPriorityQueue<>(/* maxBucket= */5);
        queue.add(new TestItem(), -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionOnTooHighPriority() {
        final BucketPriorityQueue<TestItem> queue = new BucketPriorityQueue<>(/* maxBucket= */5);
        queue.add(new TestItem(), 6);
    }

    @Test(expected = NoSuchElementException.class)
    public void shouldThrowExceptionOnRemoveFromEmpty() {
        final BucketPriorityQueue<TestItem> queue = new BucketPriorityQueue<>(/* maxBucket= */5);
        queue.remove();
    }

    @Test
    public void shouldOfferAndPollCorrectly() {
        final BucketPriorityQueue<TestItem> queue = new BucketPriorityQueue<>(/* maxBucket= */5);
        final TestItem item0 = new TestItem();
        final TestItem item2 = new TestItem();
        final TestItem item5 = new TestItem();

        assertFalse(queue.offer(new TestItem(), -1));
        assertFalse(queue.offer(new TestItem(), 6));
        assertTrue(queue.isEmpty());
        assertNull(queue.poll());

        assertTrue(queue.offer(item2, 2));
        assertTrue(queue.offer(item0, 0));
        assertTrue(queue.offer(item5, 5));
        assertFalse(queue.isEmpty());

        assertEquals(item0, queue.poll());
        assertEquals(item2, queue.poll());
        assertEquals(item5, queue.poll());
    }

    @Test
    public void shouldHandleDuplicatePrioritiesLIFO() {
        final BucketPriorityQueue<TestItem> queue = new BucketPriorityQueue<>(/* maxBucket= */5);
        final TestItem item1First = new TestItem();
        final TestItem item1Second = new TestItem();
        final TestItem item2 = new TestItem();

        queue.add(item1First, 1);
        queue.add(item2, 2);
        queue.add(item1Second, 1);

        // LIFO for the items with the same priority.
        assertEquals(item1Second, queue.remove());
        assertEquals(item1First, queue.remove());
        assertEquals(item2, queue.remove());
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testToString() {
        final BucketPriorityQueue<TestItem> queue = new BucketPriorityQueue<>(/* maxBucket= */ 2);
        queue.add(new TestItem("A"), 1);
        queue.add(new TestItem("B"), 1);
        queue.add(new TestItem("C"), 1);
        queue.add(new TestItem("D"), 1);
        queue.add(new TestItem("E"), 2);

        assertEquals("BucketPriorityQueue{mMinBucketIndex=1, mMaxBucket=2,"
                        + " mBuckets=[null, [A, B, C, ...(size=4)], [E]]}", queue.toString());
    }
}
