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
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The talisman requirements of the system, communicated to a {@link TalismanService}.
 *
 * <p>An instance of this class communicates three types of needs:
 *
 * <ul>
 *   <li><b>Trust Configuration Update:</b> The system can indicate the timestamp of its last known
 *       trust configuration via {@link #getTrustConfigurationLastUpdatedTime()}. The {@link
 *       TalismanService} can use this to determine if a newer configuration needs to be provided.
 *   <li><b>Verified Device Talismans:</b> The system can request a specific number of "verified
 *       device" talismans using {@link #getVerifiedDeviceTalismanCount()}.
 *   <li><b>Identity-Bound Talisman Sets:</b> The system can request sets of talismans using {@link
 *       #getIdentitySetNeeds()}. Each need in this list, represented by a {@link
 *       TalismanIdentitySetNeed}, specifies a group of identities and the number of sets required
 *       for that group. Each set contains a single "verified device" talisman that is
 *       cryptographically bound to a collection of identity talismans (one for each specified
 *       identity).
 * </ul>
 *
 * <p>The service is responsible for acquiring talismans to satisfy the needs. The service should
 * satisfy the needs in a way that balances the urgency of the need with the cost of providing the
 * talismans at any given time. For example, if there is no urgent need for talismans, then the
 * service may wait until the device is idle, charging, on Wifi, etc. to provide the talismans. When
 * multiple talismans are needed, the service may provide them in small batches.
 *
 * <p>For each type of need for talismans, the system specifies the count of the need that is
 * urgent. When talismans are needed urgently, the service should provide them as soon as possible,
 * ignoring the system health implications. The system attempts to maintain a pool of talismans of
 * each type so that the service should only need to fetch talismans once per day. Therefore, an
 * urgent need for talismans is a result of atypical user behavior or an infrequent operation (e.g.
 * adding a new identity).
 *
 * <p>As the system receives new talismans, uses talismans, or talismans expire, the system will
 * call {@link TalismanService#onReportTalismanNeeds(TalismanNeeds)} to update the needs.
 *
 * <p>Instances of this class are constructed using the {@link Builder}.
 *
 * @hide
 */
public final class TalismanNeeds implements Parcelable {
    // @SystemApi TODO(b/418280383): Make this visible

    private final @Nullable Instant mLastTrustConfigTime;
    private final int mVerifiedDeviceTalismanCount;
    private final int mVerifiedDeviceTalismanUrgentCount;
    private final List<TalismanIdentitySetNeed> mIdentitySetNeeds;

    /**
     * @see Builder
     */
    private TalismanNeeds(
            @Nullable Instant lastTrustConfigTime,
            int verifiedDeviceTalismanCount,
            int verifiedDeviceTalismanUrgentCount,
            @NonNull List<TalismanIdentitySetNeed> identitySetNeeds) {
        mLastTrustConfigTime = lastTrustConfigTime;
        mVerifiedDeviceTalismanCount = verifiedDeviceTalismanCount;
        mVerifiedDeviceTalismanUrgentCount = verifiedDeviceTalismanUrgentCount;
        mIdentitySetNeeds = new ArrayList<>(identitySetNeeds);
    }

    private TalismanNeeds(@NonNull Parcel in) {
        long lastTrustConfigTimeMillis = in.readLong();
        mLastTrustConfigTime =
                lastTrustConfigTimeMillis == -1L
                        ? null
                        : Instant.ofEpochMilli(lastTrustConfigTimeMillis);
        mVerifiedDeviceTalismanCount = in.readInt();
        mVerifiedDeviceTalismanUrgentCount = in.readInt();
        mIdentitySetNeeds = in.createTypedArrayList(TalismanIdentitySetNeed.CREATOR);
    }

    /**
     * Returns the timestamp of the last trust configuration received by the system.
     *
     * <p>This can be used by the {@link TalismanService} to determine if it should provide a newer
     * configuration.
     *
     * @return the time of the last update, or null if no update has been received.
     */
    @Nullable
    public Instant getTrustConfigurationLastUpdatedTime() {
        return mLastTrustConfigTime;
    }

    /** Returns the total number of unbound "verified device" talismans needed by the system. */
    @IntRange(from = 0)
    public int getVerifiedDeviceTalismanCount() {
        return mVerifiedDeviceTalismanCount;
    }

    /**
     * Returns the number of "verified device" talismans that are needed urgently.
     *
     * <p>This is a subset of {@link #getVerifiedDeviceTalismanCount()}.
     */
    @IntRange(from = 0)
    public int getVerifiedDeviceTalismanUrgentCount() {
        return mVerifiedDeviceTalismanUrgentCount;
    }

    /**
     * Returns a copy of the list of needs for sets of identity talismans.
     *
     * <p>Each item in the list represents a need for a number of sets, where each set contains one
     * "verified device" talisman and a group of identity talismans.
     */
    @NonNull
    public List<TalismanIdentitySetNeed> getIdentitySetNeeds() {
        return new ArrayList<>(mIdentitySetNeeds);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(mLastTrustConfigTime != null ? mLastTrustConfigTime.toEpochMilli() : -1);
        dest.writeInt(mVerifiedDeviceTalismanCount);
        dest.writeInt(mVerifiedDeviceTalismanUrgentCount);
        dest.writeTypedList(mIdentitySetNeeds);
    }

    @NonNull
    public static final Creator<TalismanNeeds> CREATOR =
            new Creator<TalismanNeeds>() {
                @Override
                public TalismanNeeds createFromParcel(Parcel in) {
                    return new TalismanNeeds(in);
                }

                @Override
                public TalismanNeeds[] newArray(int size) {
                    return new TalismanNeeds[size];
                }
            };

    /** Builder for {@link TalismanNeeds}. */
    public static final class Builder {
        private @Nullable Instant mLastTrustConfigTime = null;
        private int mVerifiedDeviceTalismanCount = 0;
        private int mVerifiedDeviceTalismanUrgentCount = 0;
        private final List<TalismanIdentitySetNeed> mIdentitySetNeeds = new ArrayList<>();

        /**
         * Sets the timestamp of the last trust configuration received by the system.
         *
         * <p>Do not call this if the system has never received a trust configuration.
         *
         * @param lastTrustConfigTime the time of the last configuration.
         */
        @NonNull
        public Builder setLastTrustConfigTime(@NonNull Instant lastTrustConfigTime) {
            mLastTrustConfigTime = lastTrustConfigTime;
            return this;
        }

        /** Sets the number of "verified device" talismans needed. */
        @NonNull
        public Builder setVerifiedDeviceTalismanCount(
                @IntRange(from = 0) int verifiedDeviceTalismanCount) {
            mVerifiedDeviceTalismanCount = verifiedDeviceTalismanCount;
            return this;
        }

        /**
         * Sets the number of "verified device" talismans that are needed urgently.
         *
         * <p>This must be less than or equal to the total number of talismans needed.
         *
         * @see #setVerifiedDeviceTalismanCount(int)
         */
        @NonNull
        public Builder setVerifiedDeviceTalismanUrgentCount(
                @IntRange(from = 0) int verifiedDeviceTalismanUrgentCount) {
            mVerifiedDeviceTalismanUrgentCount = verifiedDeviceTalismanUrgentCount;
            return this;
        }

        /**
         * Adds one or more needs for sets of identity talismans.
         *
         * @param needs the needs for sets of identity talismans.
         */
        @NonNull
        public Builder addIdentitySetNeed(@NonNull TalismanIdentitySetNeed... needs) {
            Collections.addAll(mIdentitySetNeeds, needs);
            return this;
        }

        /** Builds the {@link TalismanNeeds} object. */
        @NonNull
        public TalismanNeeds build() {
            if (mVerifiedDeviceTalismanUrgentCount > mVerifiedDeviceTalismanCount) {
                throw new IllegalStateException(
                        "Urgent count for verified device talismans ("
                                + mVerifiedDeviceTalismanUrgentCount
                                + ") cannot be greater than the total count ("
                                + mVerifiedDeviceTalismanCount
                                + ")");
            }
            return new TalismanNeeds(
                    mLastTrustConfigTime,
                    mVerifiedDeviceTalismanCount,
                    mVerifiedDeviceTalismanUrgentCount,
                    mIdentitySetNeeds);
        }
    }
}
