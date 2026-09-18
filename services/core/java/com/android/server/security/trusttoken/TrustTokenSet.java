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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.security.trusttoken.TrustToken;
import android.security.trusttoken.TrustTokenIdentitySet;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a set of trust tokens, which can be either a verified device trust token or a {@link
 * TrustTokenIdentitySet}. The trust tokens are under the same public key.
 */
class TrustTokenSet {
    static final int TYPE_VERIFIED_DEVICE = 1;
    static final int TYPE_VERIFIED_IDENTITIES = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = {"TYPE_"},
            value = {
                TYPE_VERIFIED_DEVICE,
                TYPE_VERIFIED_IDENTITIES,
            })
    @interface Type {}

    private final @Type int mType;
    private final byte[] mPublicKey;

    /** The serialized trust tokens. */
    private final byte[] mTokenSet;

    /**
     * The time at which the trust tokens was created. If the trust tokens have different creation
     * time, the earliest one is chosen.
     */
    private final Instant mCreatedAt;

    /**
     * The time at which the trust tokens expires. If the trust tokens have different expiry time,
     * the earliest one is chosen.
     */
    private final Instant mExpireAt;

    TrustTokenSet(
            @Type int type,
            byte[] publicKey,
            byte[] tokenSet,
            Instant createdAt,
            Instant expireAt) {
        mType = type;
        mPublicKey = publicKey;
        mTokenSet = tokenSet;
        mCreatedAt = createdAt;
        mExpireAt = expireAt;
    }

    @Type
    int getType() {
        return mType;
    }

    byte[] getPublicKey() {
        return mPublicKey;
    }

    byte[] getTokenSet() {
        return mTokenSet;
    }

    Instant getCreatedAt() {
        return mCreatedAt;
    }

    Instant getExpireAt() {
        return mExpireAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrustTokenSet)) return false;
        TrustTokenSet that = (TrustTokenSet) o;
        return mType == that.mType
                && Arrays.equals(mPublicKey, that.mPublicKey)
                && Arrays.equals(mTokenSet, that.mTokenSet)
                && Objects.equals(mCreatedAt, that.mCreatedAt)
                && Objects.equals(mExpireAt, that.mExpireAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mType, mCreatedAt, mExpireAt);
        result = 31 * result + Arrays.hashCode(mPublicKey);
        result = 31 * result + Arrays.hashCode(mTokenSet);
        return result;
    }

    @Override
    public String toString() {
        return "TrustTokenSet["
                + "type="
                + mType
                + ", "
                + "publicKey="
                + Arrays.toString(mPublicKey)
                + ", "
                + "tokenSet="
                + Arrays.toString(mTokenSet)
                + ", "
                + "createdAt="
                + mCreatedAt
                + ", "
                + "expireAt="
                + mExpireAt
                + "]";
    }

    /**
     * Converts this TrustTokenSet to a {@link TrustToken} if its type is {@link
     * #TYPE_VERIFIED_DEVICE}.
     *
     * @return A {@link TrustToken} instance.
     */
    @NonNull
    TrustToken asVerifiedDeviceToken() {
        if (getType() != TYPE_VERIFIED_DEVICE) {
            throw new IllegalStateException("not a verified device token");
        }
        return new TrustToken(mTokenSet);
    }

    /**
     * Converts this TrustTokenSet to a {@link TrustTokenIdentitySet} if its type is {@link
     * #TYPE_VERIFIED_IDENTITIES}.
     *
     * @return A {@link TrustTokenIdentitySet} instance.
     */
    TrustTokenIdentitySet asTrustTokenIdentitySet() {
        throw new UnsupportedOperationException("TODO");
    }

    static final class Builder {
        private @Type int mType;
        private byte[] mPublicKey;
        private byte[] mTokenSet;
        private Instant mCreatedAt;
        private Instant mExpireAt;

        Builder() {}

        Builder setType(@Type int type) {
            this.mType = type;
            return this;
        }

        Builder setPublicKey(byte[] publicKey) {
            this.mPublicKey = Arrays.copyOf(publicKey, publicKey.length);
            return this;
        }

        Builder setTokenSet(byte[] tokenSet) {
            this.mTokenSet = Arrays.copyOf(tokenSet, tokenSet.length);
            return this;
        }

        Builder setCreatedAt(Instant createdAt) {
            this.mCreatedAt = createdAt;
            return this;
        }

        Builder setExpireAt(Instant expireAt) {
            this.mExpireAt = expireAt;
            return this;
        }

        TrustTokenSet build() {
            return new TrustTokenSet(mType, mPublicKey, mTokenSet, mCreatedAt, mExpireAt);
        }
    }
}
