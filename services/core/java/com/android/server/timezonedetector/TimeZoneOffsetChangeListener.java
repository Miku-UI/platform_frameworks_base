/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.timezonedetector;

import android.annotation.CurrentTimeMillisLong;
import android.annotation.ElapsedRealtimeLong;
import android.util.IndentingPrintWriter;

import com.android.server.timezonedetector.TimeZoneOffsetChangeListener.TimeZoneOffsetChangeEvent;

import java.util.Objects;

/** An interface for classes that can be notified of daylight saving time changes. */
public interface TimeZoneOffsetChangeListener {
    /**
     * Records a daylight saving time change event.
     *
     * @param event the daylight saving time change event to process.
     */
    void process(TimeZoneOffsetChangeEvent event);

    /** Dumps internal state. */
    void dump(IndentingPrintWriter ipw);

    /** A class that represents a daylight saving time change event. */
    class TimeZoneOffsetChangeEvent {
        private final @ElapsedRealtimeLong long mElapsedRealtimeMillis;
        private final @CurrentTimeMillisLong long mUnixEpochTimeMillis;
        private final int mOldOffsetSeconds;
        private final int mNewOffsetSeconds;

        public TimeZoneOffsetChangeEvent(
                @ElapsedRealtimeLong long elapsedRealtimeMillis,
                @CurrentTimeMillisLong long unixEpochTimeMillis,
                int oldOffsetSeconds,
                int newOffsetSeconds) {
            mElapsedRealtimeMillis = elapsedRealtimeMillis;
            mUnixEpochTimeMillis = unixEpochTimeMillis;
            mOldOffsetSeconds = oldOffsetSeconds;
            mNewOffsetSeconds = newOffsetSeconds;
        }

        public @ElapsedRealtimeLong long getElapsedRealtimeMillis() {
            return mElapsedRealtimeMillis;
        }

        public @CurrentTimeMillisLong long getUnixEpochTimeMillis() {
            return mUnixEpochTimeMillis;
        }

        public int getOldOffsetSeconds() {
            return mOldOffsetSeconds;
        }

        public int getNewOffsetSeconds() {
            return mNewOffsetSeconds;
        }

        public int getOffsetDifferenceSeconds() {
            return mNewOffsetSeconds - mOldOffsetSeconds;
        }

        @Override
        public String toString() {
            return "TimeZoneOffsetChangeEvent{"
                    + "mElapsedRealtimeMillis="
                    + mElapsedRealtimeMillis
                    + ", mUnixEpochTimeMillis="
                    + mUnixEpochTimeMillis
                    + ", mOldOffsetSeconds="
                    + mOldOffsetSeconds
                    + ", mNewOffsetSeconds="
                    + mNewOffsetSeconds
                    + '}';
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof TimeZoneOffsetChangeEvent that) {
                return mElapsedRealtimeMillis == that.mElapsedRealtimeMillis
                        && mUnixEpochTimeMillis == that.mUnixEpochTimeMillis
                        && mOldOffsetSeconds == that.mOldOffsetSeconds
                        && mNewOffsetSeconds == that.mNewOffsetSeconds;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mElapsedRealtimeMillis,
                    mUnixEpochTimeMillis,
                    mOldOffsetSeconds,
                    mNewOffsetSeconds);
        }
    }
}
