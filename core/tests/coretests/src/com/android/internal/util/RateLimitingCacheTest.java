/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the RateLimitingCache class.
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class RateLimitingCacheTest {

    private volatile int mCounter = 0;

    @Before
    public void before() {
        mCounter = 0;
    }

    private final RateLimitingCache.ValueFetcher<Integer> mFetcher = () -> {
        return ++mCounter;
    };

    /**
     * Test zero period passed into RateLimitingCache. A new value should be returned for each
     * time the cache's get() is invoked.
     */
    @Test
    public void testTtl_Zero() {
        TestRateLimitingCache<Integer> s = new TestRateLimitingCache<>(0);

        int first = s.get(mFetcher);
        assertEquals(1, first);
        int second = s.get(mFetcher);
        assertEquals(2, second);
        s.advanceTime(20);
        int third = s.get(mFetcher);
        assertEquals(3, third);
    }

    /**
     * Test a period of 100ms passed into RateLimitingCache. A new value should not be fetched
     * any more frequently than every 100ms.
     */
    @Test
    public void testTtl_100() {
        TestRateLimitingCache<Integer> s = new TestRateLimitingCache<>(100);

        int first = s.get(mFetcher);
        assertEquals(1, first);
        int second = s.get(mFetcher);
        // Too early to change
        assertEquals(1, second);
        s.advanceTime(150);
        int third = s.get(mFetcher);
        // Changed by now
        assertEquals(2, third);
        int fourth = s.get(mFetcher);
        // Too early to change again
        assertEquals(2, fourth);
    }

    /**
     * Test a negative period passed into RateLimitingCache. A new value should only be fetched the
     * first call to get().
     */
    @Test
    public void testTtl_Negative() {
        TestRateLimitingCache<Integer> s = new TestRateLimitingCache<>(-1);

        int first = s.get(mFetcher);
        assertEquals(1, first);
        s.advanceTime(200);
        // Should return the original value every time
        int second = s.get(mFetcher);
        assertEquals(1, second);
    }

    /**
     * Test making tons of calls to the speed-limiter and make sure number of fetches does not
     * exceed expected number of fetches.
     */
    @Test
    public void testTtl_Spam() {
        TestRateLimitingCache<Integer> s = new TestRateLimitingCache<>(100);
        assertCount(s, 1000, 7, 15);
    }

    /**
     * Test rate-limiting across multiple periods and make sure the expected number of fetches is
     * within the specified rate.
     */
    @Test
    public void testRate_10hz() {
        TestRateLimitingCache<Integer> s = new TestRateLimitingCache<>(1000, 10);
        // At 10 per second, 2 seconds should not exceed about 30, assuming overlap into left and
        // right windows that allow 10 each
        assertCount(s, 2000, 20, 33);
    }

    /**
     * Exercises concurrent access to the cache.
     */
    @Test
    public void testMultipleThreads() throws InterruptedException {
        // Definitely won't have more than one period elapsed during the test.
        final long periodMillis = 1_000 * 60 * 10;  // 10 minutes
        final int maxCountPerPeriod = 10;
        final RateLimitingCache<Integer> s =
                new RateLimitingCache<>(periodMillis, maxCountPerPeriod);

        final int maxIncrementsPerWorker = 1_000;
        Runnable work = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < maxIncrementsPerWorker; i++) {
                    s.get(mFetcher);
                }
            }
        };

        final int numWorkers = 5;
        List<Thread> workers = new ArrayList<>(numWorkers);
        for (int i = 0; i < numWorkers; i++) {
            workers.add(new Thread(work));
        }
        for (Thread worker : workers) {
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        final long expectedAtMost =
                Math.min(
                        // No more than the max increments that each worker can do
                        // times the number of workers
                        maxIncrementsPerWorker * numWorkers,
                        // Workers may race and get at most one more increment each
                        maxCountPerPeriod + numWorkers);
        assertTrue(
                "mCounter should be <=" + expectedAtMost + " but was " + mCounter,
                mCounter <= expectedAtMost);
    }

    /**
     * Multiple threads calling get() on the cache while the cached value is stale are allowed
     * to fetch, regardless of the rate limiting.
     * This is to prevent a slow getting thread from blocking other threads from getting a fresh
     * value.
     */
    @Test
    public void testMultipleThreads_oneThreadIsSlow() throws InterruptedException {
        final long periodMillis = 1000;
        final int maxCountPerPeriod = 1;
        final RateLimitingCache<Integer> s =
                new RateLimitingCache<>(periodMillis, maxCountPerPeriod);

        final CountDownLatch latch1 = new CountDownLatch(2);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger();
        final RateLimitingCache.ValueFetcher<Integer> fetcher = () -> {
            latch1.countDown();
            try {
                latch2.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return counter.incrementAndGet();
        };

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                s.get(fetcher);
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                s.get(fetcher);
            }
        });

        t1.start();
        t2.start();
        // Both threads should be admitted to fetch because there is no fresh cached value,
        // even though this exceeds the rate limit of at most 1 call per period.
        // Wait for both threads to be fetching.
        latch1.await();
        // Allow the fetcher to return.
        latch2.countDown();
        // Wait for both threads to finish their fetches.
        t1.join();
        t2.join();

        assertEquals(2, counter.get());
    }

    /**
     * Even if multiple threads race to refresh the cache, only one thread gets to set a new value.
     * This ensures, among other things, that the cache never returns values that were fetched out
     * of order.
     */
    @Test
    @LargeTest
    public void testMultipleThreads_cachedValueNeverGoesBackInTime() throws InterruptedException {
        final long periodMillis = 10;
        final int maxCountPerPeriod = 3;
        final RateLimitingCache<Integer> s =
                new RateLimitingCache<>(periodMillis, maxCountPerPeriod);
        final AtomicInteger counter = new AtomicInteger();
        final RateLimitingCache.ValueFetcher<Integer> fetcher = () -> {
            // Note that this fetcher has a side effect, which is strictly not allowed for
            // RateLimitingCache users, but we make an exception for the purpose of this test.
            return counter.incrementAndGet();
        };

        // Make three threads that spin on getting from the cache
        final AtomicBoolean shouldRun = new AtomicBoolean(true);
        Runnable worker = new Runnable() {
            @Override
            public void run() {
                while (shouldRun.get()) {
                    s.get(fetcher);
                }
            }
        };
        Thread t1 = new Thread(worker);
        Thread t2 = new Thread(worker);
        Thread t3 = new Thread(worker);
        t1.start();
        t2.start();
        t3.start();

        // Get values until a sufficiently convincing high value while ensuring that values are
        // monotonically non-decreasing.
        int lastSeen = 0;
        while (lastSeen < 10000) {
            int value = s.get(fetcher);
            if (value < lastSeen) {
                fail("Unexpectedly saw decreasing value " + value + " after " + lastSeen);
            }
            lastSeen = value;
        }

        shouldRun.set(false);
        t1.join();
        t2.join();
        t3.join();
    }

    /**
     * Helper to make repeated calls every 5 millis to verify the number of expected fetches for
     * the given parameters.
     * @param cache the cache object
     * @param period the period for which to make get() calls
     * @param minCount the lower end of the expected number of fetches, with a margin for error
     * @param maxCount the higher end of the expected number of fetches, with a margin for error
     */
    private void assertCount(TestRateLimitingCache<Integer> cache, long period,
            int minCount, int maxCount) {
        long startTime = cache.getTime();
        while (cache.getTime() < startTime + period) {
            int value = cache.get(mFetcher);
            cache.advanceTime(5);
        }
        int latest = cache.get(mFetcher);
        assertTrue("Latest should be between " + minCount + " and " + maxCount
                        + " but is " + latest, latest <= maxCount && latest >= minCount);
    }

    private static class TestRateLimitingCache<Value> extends RateLimitingCache<Value> {
        // Start at a non-zero time to avoid confusion with uninitialized state.
        private long mTime = 1;

        public TestRateLimitingCache(long periodMillis) {
            super(periodMillis);
        }

        public TestRateLimitingCache(long periodMillis, int count) {
            super(periodMillis, count);
        }

        public void advanceTime(long time) {
            mTime += time;
        }

        @Override
        public long getTime() {
            return mTime;
        }
    }
}
