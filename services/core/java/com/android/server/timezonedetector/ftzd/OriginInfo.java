/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.server.timezonedetector.ftzd;

import android.os.SystemClock;

import java.time.Duration;
import java.util.Objects;

/**
 * Represents metadata associated with a time zone origin (e.g., telephony, location) within a
 * {@link FusedTimeZone}.
 *
 * <p>This class is not thread-safe.
 *
 * @hide
 */
public final class OriginInfo {
    /** The timestamp when the origin was created. */
    private final long mTimestampCreated;

    /**
     * The timestamp when the origin was first detected. This is used to determine the first
     * sighting of an origin and can be earlier than {@link #mTimestampCreated}.
     */
    private long mTimestampDetected;

    /** The timestamp when the origin was last updated. */
    private long mTimestampLastUpdated;

    private int mUpdates;
    private int mQuality = 100;

    /**
     * Creates a new {@link OriginInfo}, capturing the current timestamp as the creation and
     * last-updated time.
     */
    public OriginInfo() {
        this(SystemClock.elapsedRealtime());
    }

    /**
     * Creates a new {@link OriginInfo} with a specific timestamp.
     *
     * @param timestampDetected the timestamp at which the origin was first detected
     */
    public OriginInfo(long timestampDetected) {
        mTimestampCreated = SystemClock.elapsedRealtime();
        mTimestampDetected = timestampDetected;
        mTimestampLastUpdated = Math.max(mTimestampCreated, mTimestampDetected);
        mUpdates = 1;
    }

    /**
     * Creates a copy of another {@link OriginInfo} instance.
     *
     * @param other the instance to copy
     */
    public OriginInfo(OriginInfo other) {
        mTimestampCreated = other.mTimestampCreated;
        mTimestampDetected = other.mTimestampDetected;
        mTimestampLastUpdated = other.mTimestampLastUpdated;
        mUpdates = other.mUpdates;
        mQuality = other.mQuality;
    }

    /** Updates the last-updated timestamp to the current time and increments the update count. */
    public OriginInfo updateLastUpdated() {
        mTimestampLastUpdated = SystemClock.elapsedRealtime();
        mUpdates++;

        return this;
    }

    /**
     * Sets the quality score for this origin and increments the update count.
     *
     * @param quality the quality score to set
     */
    public OriginInfo setQuality(int quality) {
        mQuality = quality;
        updateLastUpdated();

        return this;
    }

    /**
     * Sets the detected timestamp for this origin.
     *
     * @param timestampDetected the timestamp to set
     */
    public OriginInfo setDetectedTimestamp(long timestampDetected) {
        mTimestampDetected = timestampDetected;

        return this;
    }

    /** Returns the quality score for this origin. */
    public int getQuality() {
        return mQuality;
    }

    /** Returns the timestamp when this origin was created. */
    public long getTimestampCreated() {
        return mTimestampCreated;
    }

    /** Returns the timestamp when this origin was first detected. */
    public long getTimestampDetected() {
        return mTimestampDetected;
    }

    /** Returns the timestamp when this origin was last updated. */
    public long getTimestampLastUpdated() {
        return mTimestampLastUpdated;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof OriginInfo that) {
            return mTimestampCreated == that.mTimestampCreated
                    && mTimestampDetected == that.mTimestampDetected
                    && mTimestampLastUpdated == that.mTimestampLastUpdated
                    && mUpdates == that.mUpdates
                    && mQuality == that.mQuality;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTimestampCreated, mTimestampDetected, mTimestampLastUpdated, mUpdates, mQuality);
    }

    @Override
    public String toString() {
        return "OriginInfo{"
                + "mTimestampCreated="
                + Duration.ofMillis(mTimestampCreated).toString()
                + ", mTimestampDetected="
                + Duration.ofMillis(mTimestampDetected).toString()
                + ", mTimestampLastUpdated="
                + Duration.ofMillis(mTimestampLastUpdated).toString()
                + ", mQuality="
                + mQuality
                + ", mUpdates="
                + mUpdates
                + '}';
    }
}
