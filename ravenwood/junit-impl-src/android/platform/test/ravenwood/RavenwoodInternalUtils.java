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
package android.platform.test.ravenwood;

import java.util.HashMap;

public class RavenwoodInternalUtils {
    private RavenwoodInternalUtils() {
    }

    /**
     * Base class for a simple key value cache. Subclass implements {@link #compute}.
     */
    public abstract static class MapCache<K, V> {
        private final Object mLock = new Object();

        private final HashMap<K, V> mCache = new HashMap<>();

        /**
         * Subclass implements it.
         *
         * This method may be called for the same key multiple times if access to the same
         * key happens on multiple threads at the same time.
         */
        protected abstract V compute(K key);

        /**
         * @return the value for a given key, using the cache.
         */
        public V get(K key) {
            synchronized (mLock) {
                var cached = mCache.get(key);
                if (cached != null) {
                    return cached;
                }
            }
            V value = compute(key);
            synchronized (mLock) {
                mCache.put(key, value);
            }
            return value;
        }
    }
}
