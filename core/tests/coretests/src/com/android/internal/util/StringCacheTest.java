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

package com.android.internal.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import android.platform.test.flag.junit.CheckFlagsRule;

import android.os.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StringCacheTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int CACHE_SIZE = 3;
    private StringCache mCache;

    @Before
    public void setUp() {
        mCache = new StringCache(CACHE_SIZE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testCacheHit() {
        String s1 = new String("hello");
        String s2 = new String("hello");

        String cachedS1 = mCache.cache(s1);
        String cachedS2 = mCache.cache(s2);

        assertSame(s1, cachedS1);
        assertSame(s1, cachedS2); // Should be the same instance
        assertEquals(1, mCache.getMissCount());
        assertEquals(1, mCache.getHitCount());
        assertEquals(1, mCache.size());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testCacheMiss() {
        String s1 = mCache.cache("one");
        String s2 = mCache.cache("two");

        assertEquals("one", s1);
        assertEquals("two", s2);
        assertEquals(2, mCache.getMissCount());
        assertEquals(0, mCache.getHitCount());
        assertEquals(2, mCache.size());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testEviction() {
        mCache.cache("1");
        mCache.cache("2");
        mCache.cache("3");
        assertEquals(3, mCache.size());
        assertEquals(3, mCache.getMissCount());
        assertEquals(0, mCache.getEvictCount());

        // This should evict "1"
        mCache.cache("4");
        assertEquals(3, mCache.size());
        assertEquals(4, mCache.getMissCount());
        assertEquals(1, mCache.getEvictCount());

        // "1" should now be a miss, and "2" should be evicted
        mCache.cache("1");
        assertEquals(3, mCache.size());
        assertEquals(5, mCache.getMissCount());
        assertEquals(0, mCache.getHitCount());
        assertEquals(2, mCache.getEvictCount());

        // "3" should still be in the cache and a hit.
        mCache.cache("3");
        assertEquals(3, mCache.size());
        assertEquals(5, mCache.getMissCount());
        assertEquals(1, mCache.getHitCount());
        assertEquals(2, mCache.getEvictCount());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    /** Null strings are not cached. */
    public void testNullString() {
        String result = mCache.cache(null);
        assertSame(null, result);
        assertEquals(0, mCache.size());
        assertEquals(0, mCache.getHitCount() + mCache.getMissCount());
        assertEquals(0, mCache.getRejectCount());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    /** Long strings are not cached. */
    public void testStringTooLong() {
        // Create a string longer than the max length (256)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("a");
        }
        String longString = sb.toString();
        String result = mCache.cache(longString);

        assertSame(longString, result);
        assertEquals(0, mCache.size());
        assertEquals(0, mCache.getHitCount() + mCache.getMissCount());
        assertEquals(1, mCache.getRejectCount());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    /** The cache can be used safely from multiple threads. */
    public void testMultiThreaded() throws InterruptedException {
        final int numThreads = 10;
        final int iterations = 1000;
        final String[] strings = {"a", "b", "c", "d", "e"};

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] =
                    new Thread(
                            () -> {
                                for (int j = 0; j < iterations; j++) {
                                    mCache.cache(strings[j % strings.length]);
                                }
                            });
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        assertEquals(numThreads * iterations, mCache.getHitCount() + mCache.getMissCount());
        assertTrue(mCache.size() <= CACHE_SIZE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    /** The cache can be used safely from multiple threads under high contention. */
    public void testMultiThreadedStress() throws InterruptedException {
        final int numThreads = 50;
        final int iterations = 2000;
        final String[] strings = new String[100];
        for (int i = 0; i < strings.length; i++) {
            strings[i] = "s" + i;
        }

        // The cache size is small, so there will be a lot of evictions.
        final StringCache stressCache = new StringCache(10);

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] =
                    new Thread(
                            () -> {
                                for (int j = 0; j < iterations; j++) {
                                    stressCache.cache(strings[j % strings.length]);
                                }
                            });
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        // Validate cache size.
        assertTrue("Cache size should be less than or equal to max size", stressCache.size() <= 10);

        // Validate stats.
        long totalOps = stressCache.getHitCount() + stressCache.getMissCount();
        assertEquals(numThreads * iterations, totalOps);

        // With so many threads and a small cache, there should be evictions.
        assertTrue("Should have evictions", stressCache.getEvictCount() > 0);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testClear() {
        mCache.cache("a");
        mCache.cache("b");
        mCache.cache("a"); // Hit

        assertEquals(2, mCache.size());
        assertEquals(1, mCache.getHitCount());
        assertEquals(2, mCache.getMissCount());

        mCache.clear();

        assertEquals(0, mCache.size());
        assertEquals(0, mCache.getHitCount());
        assertEquals(0, mCache.getMissCount());
        assertEquals(0, mCache.getRejectCount());
        assertEquals(0, mCache.getEvictCount());

        // Caching after clearing should work as if new.
        mCache.cache("c");
        assertEquals(1, mCache.size());
        assertEquals(1, mCache.getMissCount());
        assertEquals(0, mCache.getHitCount());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    /** When the flag is disabled, the cache should be a no-op. */
    public void testFlagDisabled() {
        // Use the singleton cache, which should be disabled by flag.
        mCache = StringCache.INSTANCE;

        String s1 = new String("hello");
        String s2 = new String("hello");

        String cachedS1 = StringCache.INSTANCE.cache(s1);
        String cachedS2 = StringCache.INSTANCE.cache(s2);

        assertSame(s1, cachedS1);
        assertSame(s2, cachedS2);

        assertEquals(0, mCache.getHitCount());
        assertEquals(0, mCache.getMissCount());
        assertEquals(0, mCache.size());
    }
}
