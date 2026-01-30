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

import android.ravenwood.annotation.RavenwoodKeepWholeClass;
import com.android.internal.annotations.VisibleForTesting;
import dalvik.annotation.optimization.NeverCompile;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A cache for deduplicating String instances.
 *
 * <p>This class is similar to {@link String#intern()}, but with some key differences:
 *
 * <ol>
 *   <li>Strings are not promoted to live forever.
 *   <li>The number of strings in the cache is bounded.
 *   <li>Uncontended performance is likely faster, since there is a smaller intern cache to search
 *       through.
 *   <li>Contended performance is better, because the underlying cache supports some degree of
 *       concurrency.
 * </ol>
 *
 * <p>This class is thread-safe.
 *
 * <p>Prefer {@link String#intern()} when it's safe to assume that the string is worth interning
 * forever from context. For instance:
 *
 * <ul>
 *   <li>Intern strings that are likely to be short and often repeated, like package names.
 *   <li>Intern strings that already live forever for runtime reasons, like class and method names.
 *   <li>Intern strings that are from a small range of enumerated constants, like intent actions.
 * </ul>
 *
 * <p>Prefer {@link StringPool} for well-scoped single-threaded String pooling, e.g. when parsing a
 * large JSON or XML document where duplicate strings are expected.
 *
 * <p>Prefer {@link StringCache} when it's not known whether the string is worth interning, but may
 * still be worth caching. For instance, Strings that are read from IPC Parcels or from Bundles.
 *
 * @hide
 */
@RavenwoodKeepWholeClass
public final class StringCache {

    /**
     * Maximum number of strings to keep in the cache at any given time. A larger cache reduces the
     * number of cache misses, but increases memory usage.
     */
    private static final int DEFAULT_CACHE_SIZE = 256;

    /** Reject strings longer than this length. */
    private static final int MAX_STRING_LENGTH = 128;

    /** Intern table for cached strings. */
    private final AtomicReferenceArray<String> mCache;

    private final int mCacheSize;

    private final AtomicLong mHitCount = new AtomicLong(0);
    private final AtomicLong mMissCount = new AtomicLong(0);
    private final AtomicLong mRejectCount = new AtomicLong(0);
    private final AtomicLong mEvictCount = new AtomicLong(0);
    private final AtomicLong mClearCount = new AtomicLong(0);

    public static final StringCache INSTANCE;
    static {
        if (android.os.Flags.parcelStringCacheEnabled()) {
            INSTANCE = new StringCache(DEFAULT_CACHE_SIZE);
        } else {
            INSTANCE = new StringCache(0);
        }
    }

    /**
     * Creates a StringCache with a custom size.
     *
     * @param size The maximum number of strings to cache.
     */
    StringCache(int size) {
        Preconditions.checkArgument(size >= 0);
        mCache = new AtomicReferenceArray<>(size);
        mCacheSize = size;
    }

    /**
     * Returns a string instance equal to the given string in value, but possibly not equal in
     * identity.
     *
     * <p>If a string with the same value exists in the internal cache, the cached instance is
     * returned.
     *
     * <p>If the string is null or is longer than the maximum length, the given string is always
     * returned. These operations do not affect the cache statistics.
     *
     * @param toCache The string to cache.
     * @return A string instance equal to {@code s}, from the cache if possible.
     */
    public final String cache(String toCache) {
        // Never cache null.
        if (toCache == null) {
            return null;
        }

        // A cache size of 0 indicates that the cache is disabled.
        if (mCacheSize == 0) {
            return toCache;
        }

        // Reject long strings. We don't want to be holding on to those.
        if (toCache.length() > MAX_STRING_LENGTH) {
            mRejectCount.incrementAndGet();
            return toCache;
        }

        final int index = (toCache.hashCode() & 0x7FFFFFFF) % mCacheSize;

        // The common case is a cache hit. Check with a read-only 'get' first.
        String fromCache = mCache.getAcquire(index);
        // Note that fromCache may be null
        if (toCache.equals(fromCache)) {
            mHitCount.incrementAndGet();
            return fromCache;
        }

        // Miss.
        mMissCount.incrementAndGet();

        // Try to cache the caller's string for future lookups.
        // If we race with another thread writing to the same cache index, that's ok.
        mCache.lazySet(index, toCache);

        if (fromCache != null) {
            // We had to evict a previously cached string.
            mEvictCount.incrementAndGet();
        }

        // Return the caller's string instance, which might be in the cache now.
        return toCache;
    }

    /** Returns the number of strings currently in the cache. */
    @VisibleForTesting int size() {
        int size = 0;
        for (int i = 0; i < mCacheSize; i++) {
            if (mCache.get(i) != null) {
                size++;
            }
        }
        return size;
    }

    /** Returns the number of cache hits. */
    @VisibleForTesting long getHitCount() {
        return mHitCount.get();
    }

    /** Returns the number of cache misses. */
    @VisibleForTesting long getMissCount() {
        return mMissCount.get();
    }

    /** Returns the number of rejected strings (too long). */
    @VisibleForTesting long getRejectCount() {
        return mRejectCount.get();
    }

    /** Returns the number of evicted strings. */
    @VisibleForTesting long getEvictCount() {
        return mEvictCount.get();
    }

    /** Returns the number of times the cache was cleared. */
    @VisibleForTesting long getClearCount() {
        return mClearCount.get();
    }

    /** Clear the cache. */
    public void clear() {
        for (int i = 0; i < mCacheSize; i++) {
            mCache.set(i, null);
        }
        mClearCount.incrementAndGet();
    }

    /** Dumps cache statistics to the given writer. */
    @NeverCompile
    public void dump(PrintWriter pw) {
        pw.println(" StringCache");
        pw.format("                Size: %6d\n", size());
        pw.format(
                "                Hits: %6d                 Misses: %6d\n",
                getHitCount(), getMissCount());
        pw.format(
                "             Rejects: %6d              Evictions: %6d\n",
                getRejectCount(), getEvictCount());
    }
}
