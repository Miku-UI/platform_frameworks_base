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

package android.service.security.talisman;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A need for a number of identity-bound talisman sets.
 *
 * <p>Each need represents a need for a number of {@link
 * android.security.talisman.TalismanIdentitySet}s, where each set contains:
 *
 * <ul>
 *   <li>A single `VerifiedDeviceStrong` talisman.
 *   <li>A collection of identity talismans, one for each identity returned by {@link
 *       #getIdentities()}.
 * </ul>
 *
 * <p>All talismans in the set must have the same {@code public_key}. This allows the system to
 * provide the verified device talisman to other devices without the identity talismans and then
 * later provide only matched identity talismans.
 *
 * <p>The identity strings match the unencrypted, unhashed identities for "VerifiedIdentity"
 * talismans.
 *
 * <p>If the service is unable to provide a talisman for one of identities in the set, it may return
 * a partial set. However, if it is unable to provide any talismans for any identities in the set,
 * it should ignore the need.
 *
 * @hide
 */
public final class TalismanIdentitySetNeed implements Parcelable {
    // @SystemApi TODO(b/418280383): Make this visible

    private final @NonNull Map<String, List<EncryptedIdentityHashParameters>> mIdentityNeeds;
    private final int mUrgentCount;

    /** @see Builder */
    private TalismanIdentitySetNeed(
            @NonNull Map<String, List<EncryptedIdentityHashParameters>> identityNeeds,
            @IntRange(from = 0) int urgentCount) {
        mIdentityNeeds = new HashMap<>(identityNeeds.size());
        for (Map.Entry<String, List<EncryptedIdentityHashParameters>> entry :
                identityNeeds.entrySet()) {
            mIdentityNeeds.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        mUrgentCount = urgentCount;
    }

    private TalismanIdentitySetNeed(@NonNull Parcel in) {
        mUrgentCount = in.readInt();
        int size = in.readInt();
        mIdentityNeeds = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String identity = in.readString8();
            List<EncryptedIdentityHashParameters> params =
                    in.createTypedArrayList(EncryptedIdentityHashParameters.CREATOR);
            mIdentityNeeds.put(identity, params);
        }
    }

    /** Returns the list of identities for this set need. */
    @NonNull
    public List<String> getIdentities() {
        return new ArrayList<>(mIdentityNeeds.keySet());
    }

    /** Returns the number of sets needed for this group of identities. */
    @IntRange(from = 1)
    public int getCount() {
        if (mIdentityNeeds.isEmpty()) {
            return 0;
        }
        return mIdentityNeeds.values().iterator().next().size();
    }

    /**
     * Returns the number of sets that are needed urgently.
     *
     * <p>This is a subset of {@link #getCount()}.
     *
     * @see TalismanNeeds
     */
    @IntRange(from = 0)
    public int getUrgentCount() {
        return mUrgentCount;
    }

    /** Returns the list of parameters for the given identity. */
    @NonNull
    public List<EncryptedIdentityHashParameters> getParameters(@NonNull String identity) {
        List<EncryptedIdentityHashParameters> params = mIdentityNeeds.get(identity);
        if (params == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(params);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mUrgentCount);
        dest.writeInt(mIdentityNeeds.size());
        for (Map.Entry<String, List<EncryptedIdentityHashParameters>> entry :
                mIdentityNeeds.entrySet()) {
            dest.writeString8(entry.getKey());
            dest.writeTypedList(entry.getValue());
        }
    }

    @NonNull
    public static final Creator<TalismanIdentitySetNeed> CREATOR =
            new Creator<TalismanIdentitySetNeed>() {
                @Override
                public TalismanIdentitySetNeed createFromParcel(Parcel in) {
                    return new TalismanIdentitySetNeed(in);
                }

                @Override
                public TalismanIdentitySetNeed[] newArray(int size) {
                    return new TalismanIdentitySetNeed[size];
                }
            };

    /** Builder for {@link TalismanIdentitySetNeed}. */
    public static final class Builder {
        private final Map<String, List<EncryptedIdentityHashParameters>> mIdentityNeeds =
                new HashMap<>();
        private int mUrgentCount = 0;

        /**
         * Adds an identity and its associated parameters to the need.
         *
         * @param identity the identity string.
         * @param params the list of parameters for the identity.
         */
        @NonNull
        public Builder addIdentity(
                @NonNull String identity,
                @NonNull List<EncryptedIdentityHashParameters> params) {
            if (mIdentityNeeds.containsKey(identity)) {
                throw new IllegalArgumentException("Identity " + identity + " already exists");
            }
            mIdentityNeeds.put(identity, new ArrayList<>(params));
            return this;
        }

        /**
         * Adds multiple identities and their associated parameters to the need.
         *
         * @param identities a map from identity string to the list of parameters for the identity.
         */
        @NonNull
        public Builder addIdentities(
                @NonNull Map<String, List<EncryptedIdentityHashParameters>> identities) {
            for (String identity : identities.keySet()) {
                if (mIdentityNeeds.containsKey(identity)) {
                    throw new IllegalArgumentException("Identity " + identity + " already exists");
                }
            }
            identities.forEach(
                    (identity, params) -> mIdentityNeeds.put(identity, new ArrayList<>(params)));
            return this;
        }

        /**
         * Sets the number of sets of this type needed urgently.
         *
         * <p>This must be less than or equal to the total number of sets needed.
         */
        @NonNull
        public Builder setUrgentCount(@IntRange(from = 0) int urgentCount) {
            mUrgentCount = urgentCount;
            return this;
        }

        /** Builds the {@link TalismanIdentitySetNeed} object. */
        @NonNull
        public TalismanIdentitySetNeed build() {
            if (mIdentityNeeds.isEmpty()) {
                throw new IllegalArgumentException("Identities list must not be empty");
            }
            int count = mIdentityNeeds.values().iterator().next().size();
            if (!mIdentityNeeds.values().stream().allMatch(params -> params.size() == count)) {
                throw new IllegalArgumentException(
                        "All identities must have the same number of parameters");
            }
            if (mUrgentCount > count) {
                throw new IllegalArgumentException(
                        "Urgent count cannot be greater than the total count");
            }
            return new TalismanIdentitySetNeed(mIdentityNeeds, mUrgentCount);
        }
    }
}
