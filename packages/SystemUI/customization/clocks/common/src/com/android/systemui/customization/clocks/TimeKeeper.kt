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

package com.android.systemui.customization.clocks

import android.icu.util.Calendar
import android.icu.util.TimeZone
import java.util.Date

interface TimeKeeper {
    val callbacks: MutableList<Callback>
    var timeZone: TimeZone
    val time: Date

    fun updateTime()

    fun <T> queryCalendar(calTimespec: Int, callback: (time: Int, min: Int, max: Int) -> T): T

    interface Callback {
        fun onTimeChanged() {}

        fun onTimeZoneChanged() {}
    }
}

open class TimeKeeperImpl(private val calendar: Calendar = Calendar.getInstance()) : TimeKeeper {
    override var timeZone: TimeZone
        get() = calendar.timeZone
        set(value) {
            if (calendar.timeZone != value) {
                calendar.timeZone = value
                callbacks.forEach { it.onTimeZoneChanged() }
            }
        }

    override val callbacks = mutableListOf<TimeKeeper.Callback>()

    override val time: Date
        get() = calendar.time

    override fun updateTime() = updateTime(System.currentTimeMillis())

    override fun <T> queryCalendar(
        calTimespec: Int,
        callback: (time: Int, min: Int, max: Int) -> T,
    ): T {
        return callback(
            calendar.get(calTimespec),
            calendar.getActualMinimum(calTimespec),
            calendar.getActualMaximum(calTimespec),
        )
    }

    protected fun updateTime(timeMillis: Long) {
        calendar.timeInMillis = timeMillis
        callbacks.forEach { it.onTimeChanged() }
    }
}

class FixedTimeKeeper(var currentTimeMillis: Long = 0L) : TimeKeeperImpl() {
    override fun updateTime() {
        updateTime(currentTimeMillis)
    }
}
