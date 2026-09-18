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

package com.android.server.personalcontext.util;

/**
 * Like a {@link java.lang.ref.WeakReference}, but holds a strong reference until
 * {@link FragileReference#expire} is called.
 *
 * @param <T> type of object that a reference is being held to.
 * @hide
 */
public final class FragileReference<T> {
    private T mValue;

    public FragileReference(T value) {
        mValue = value;
    }

    /** Gets the referenced object or returns null if the reference is expired. */
    public synchronized T get() {
        return mValue;
    }

    /** Expires the referenced object. */
    public synchronized void expire() {
        // Clear out the strong reference to the object.
        mValue = null;
    }
}
