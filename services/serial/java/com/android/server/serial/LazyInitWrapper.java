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

package com.android.server.serial;

import java.util.function.Supplier;

/**
 * A helper class that wraps a lazily initialized field and ensure the access to it is blocked by
 * the proper initialization of it. This class doesn't provide any thread safety.
 */
class LazyInitWrapper<T> {
    private T mValue;
    private Supplier<T> mValueSupplier;

    private LazyInitWrapper(Supplier<T> valueSupplier) {
        mValueSupplier = valueSupplier;
    }

    T get() {
        if (mValue == null) {
            mValue = mValueSupplier.get();
            if (mValue != null) {
                mValueSupplier = null;
            }
        }
        return mValue;
    }

    static <T> LazyInitWrapper<T> create(Supplier<T> valueSupplier) {
        return new LazyInitWrapper<>(valueSupplier);
    }
}
