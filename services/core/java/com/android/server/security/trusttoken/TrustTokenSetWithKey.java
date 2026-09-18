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

package com.android.server.security.trusttoken;

import java.util.Objects;

/** Represents a {@link TrustTokenSet} and the associated {@link TrustTokenKey}. */
class TrustTokenSetWithKey {
    private final TrustTokenKey mKey;
    private final TrustTokenSet mTokenSet;

    TrustTokenSetWithKey(TrustTokenKey key, TrustTokenSet tokenSet) {
        mKey = key;
        mTokenSet = tokenSet;
    }

    TrustTokenKey getKey() {
        return mKey;
    }

    TrustTokenSet getTokenSet() {
        return mTokenSet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrustTokenSetWithKey)) return false;
        TrustTokenSetWithKey that = (TrustTokenSetWithKey) o;
        return Objects.equals(mKey, that.mKey) && Objects.equals(mTokenSet, that.mTokenSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKey, mTokenSet);
    }

    @Override
    public String toString() {
        return "TrustTokenSetWithKey[" + "key=" + mKey + ", " + "tokenSet=" + mTokenSet + "]";
    }
}
