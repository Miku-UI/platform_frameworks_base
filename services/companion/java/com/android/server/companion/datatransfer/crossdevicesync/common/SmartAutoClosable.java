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
package com.android.server.companion.datatransfer.crossdevicesync.common;

import android.annotation.Nullable;

/**
 * A wrapper of auto-closeable object that is allowed to be kept open. This is useful when combined
 * with the try-with-resources statement to conditionally keep a resource open based on logic inside
 * the try-with-resources block.
 *
 * <p>For example:
 *
 * {@snippet :
 *     try (SmartAutoClosable<T> resource = new SmartAutoClosable<T>(openResource())) {
 *       // ...
 *       if (shouldKeepOpen) {
 *         // Keep resource open when necessary.
 *         resource.setKeepOpen(true);
 *       }
 *     }
 * }
 *
 * @param <T> the type of the inner object.
 */
public class SmartAutoClosable<T extends AutoCloseable> implements AutoCloseable {
    @Nullable private final T mInner;
    private boolean mKeepOpen;

    public SmartAutoClosable(@Nullable T inner) {
        mInner = inner;
    }

    /** Set whether the inner object should be kept open. */
    public void setKeepOpen(boolean keepOpen) {
        mKeepOpen = keepOpen;
    }

    /** Unwrap the inner object. */
    @Nullable
    public T unwrap() {
        return mInner;
    }

    @Override
    public void close() {
        if (!mKeepOpen && mInner != null) {
            try {
                mInner.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
