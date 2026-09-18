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

package com.android.systemui.statusbar.chips.ui.model

import android.annotation.ElapsedRealtimeLong
import com.android.systemui.util.time.SystemClock
import java.time.Duration
import java.time.Instant

/** Data about the state of a chronometer (timer/stopwatch). */
sealed class Chronometer {
    /**
     * Running chronometer (either counting up or down to [eventTime]). Won't be displayed if the
     * current value is negative (e.g. countdown timer past [eventTime] or stopwatch before
     * [eventTime]).
     */
    data class Running(val eventTime: EventTime, val isCountdown: Boolean = false) : Chronometer()

    /** Chronometer paused at a specific time. Won't be displayed if negative. */
    data class Paused(val atDuration: Duration) : Chronometer()
}

/** Event, or "zero" time, of a chronometer. */
sealed class EventTime {
    @ElapsedRealtimeLong abstract fun asElapsedRealtime(timeSource: SystemClock): Long

    /**
     * Chronometer whose zero time is expressed in the base of [SystemClock.elapsedRealtime]
     * (milliseconds since boot).
     */
    data class ElapsedRealtime(@ElapsedRealtimeLong val elapsedRealtime: Long) : EventTime() {
        @ElapsedRealtimeLong
        override fun asElapsedRealtime(timeSource: SystemClock): Long {
            return elapsedRealtime
        }
    }

    /**
     * Chronometer whose zero time is expressed in the base of [SystemClock.currentTime] (i.e. UTC
     * time). Can skip forwards or backwards if device clock changes (excluding timezone
     * adjustments).
     */
    data class ClockTime(val instant: Instant) : EventTime() {
        @ElapsedRealtimeLong
        override fun asElapsedRealtime(timeSource: SystemClock): Long {
            return (timeSource.elapsedRealtime() +
                (instant.toEpochMilli() - timeSource.currentTimeMillis()))
        }
    }
}
