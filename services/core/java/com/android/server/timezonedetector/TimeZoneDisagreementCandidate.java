/*
 * Copyright 2026 The Android Open Source Project
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
package com.android.server.timezonedetector;

import java.util.List;

/**
 * Represents a time zone suggestion from an origin that disagrees with the current fused time zone.
 *
 * <p>It tracks the number of consecutive times this same disagreement has been observed via a
 * mutable counter. This is used to decide whether to override the current time zone. While records
 * are typically immutable, the counter is an exception to allow for efficient state tracking.
 *
 * <p>This class is not thread-safe.
 *
 * @hide
 */
public final class TimeZoneDisagreementCandidate {
    private final int mOrigin;
    private final List<String> mZoneIds;
    private final long mTimestamp;
    private int mOccurrenceCount = 1;

    public TimeZoneDisagreementCandidate(int origin, List<String> zoneIds, long timestamp) {
        this.mOrigin = origin;
        this.mZoneIds = List.copyOf(zoneIds);
        this.mTimestamp = timestamp;
    }

    public void markOccurrence() {
        mOccurrenceCount++;
    }

    public int getOccurrenceCount() {
        return mOccurrenceCount;
    }

    public int getOrigin() {
        return mOrigin;
    }

    public List<String> getZoneIds() {
        return mZoneIds;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    @Override
    public String toString() {
        return "TimeZoneDisagreementCandidate{"
                + "origin="
                + mOrigin
                + ", zoneIds="
                + mZoneIds
                + ", timestamp="
                + mTimestamp
                + ", counter="
                + mOccurrenceCount
                + '}';
    }
}
