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
package com.android.server.timezonedetector.ftzd;

import android.os.SystemClock;
import java.time.Duration;

/**
 * Represents an airplane mode event with a start and end time.
 *
 * <p>This class is not thread-safe.
 *
 * @hide
 */
public final class AirplaneModeEvent {
    private final long mStartTimeMillis;
    private long mEndTimeMillis;

    public AirplaneModeEvent() {
        this(SystemClock.elapsedRealtime());
    }

    public AirplaneModeEvent(long startTimeMillis) {
        mStartTimeMillis = startTimeMillis;
    }

    /** Returns the start time of the event as a string. */
    public String getStartTimeToString() {
        return mStartTimeMillis == 0
                ? "AIRPLANE_MODE_AT_BOOT"
                : Duration.ofMillis(mStartTimeMillis).toString();
    }

    /** Records the end time of the event. */
    public void recordEndTime() {
        mEndTimeMillis = SystemClock.elapsedRealtime();
    }

    @Override
    public String toString() {
        return "AirplaneModeEvent{"
                + "startTimeMillis="
                + getStartTimeToString()
                + ", endTimeMillis="
                + Duration.ofMillis(mEndTimeMillis).toString()
                + '}';
    }
}
