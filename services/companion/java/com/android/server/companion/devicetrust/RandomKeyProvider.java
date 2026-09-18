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

package com.android.server.companion.devicetrust;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.SecureRandom;
import java.util.Random;

/**
 * A random key provider that outputs a randomized byte array each query.
 * The key is cached to be queried exactly once before expiration.
 * This class must only be used for testing only.
 */
public class RandomKeyProvider implements PskProvider {
    public static final String NAME = "RANDOM_KEY";
    private final Random mRandom = new SecureRandom();
    private final SparseArray<byte[]> mKeys = new SparseArray<>();

    @NonNull
    @Override
    public String getProviderName() {
        return NAME;
    }

    @Nullable
    @Override
    public synchronized byte[] getKey(int userId, int associationId) {
        if (mKeys.contains(associationId)) {
            return mKeys.removeReturnOld(associationId);
        }

        byte[] key = new byte[64];
        mRandom.nextBytes(key);
        mKeys.put(associationId, key);
        return key;
    }
}
