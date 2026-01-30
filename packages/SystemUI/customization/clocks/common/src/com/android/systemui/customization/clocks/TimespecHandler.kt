/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import java.util.Date
import java.util.Locale

interface TimeKeeper {
    val callbacks: MutableList<TimeKeeper.Callback>
    var timeZone: TimeZone
    val time: Date

    fun updateTime()

    interface Callback {
        fun onTimeChanged() {}

        fun onTimeZoneChanged() {}
    }
}

open class TimeKeeperImpl(private val cal: Calendar = Calendar.getInstance()) : TimeKeeper {
    override var timeZone: TimeZone
        get() = cal.timeZone
        set(value) {
            if (cal.timeZone != value) {
                cal.timeZone = value
                callbacks.forEach { it.onTimeZoneChanged() }
            }
        }

    override val callbacks = mutableListOf<TimeKeeper.Callback>()

    override val time: Date
        get() = cal.time

    override fun updateTime() = updateTime(System.currentTimeMillis())

    protected fun updateTime(timeMillis: Long) {
        cal.timeInMillis = timeMillis
        callbacks.forEach { it.onTimeChanged() }
    }
}

class FixedTimeKeeper(val fixedTimeMillis: Long) : TimeKeeperImpl() {
    override fun updateTime() {
        updateTime(fixedTimeMillis)
    }
}

private fun TimeFormatKind.getTextPattern(pattern: String): String {
    return when (this) {
        TimeFormatKind.HALF_DAY -> pattern
        TimeFormatKind.FULL_DAY -> pattern.replace("hh", "h").replace("h", "HH")
    }
}

private fun TimeFormatKind.getContentDescriptionPattern(pattern: String): String {
    return when (this) {
        TimeFormatKind.HALF_DAY -> "hh:mm"
        TimeFormatKind.FULL_DAY -> "HH:mm"
    }
}

class DigitalTimeFormatter(
    val pattern: String,
    val timeKeeper: TimeKeeper,
    val enableContentDescription: Boolean = false,
) : TimeKeeper.Callback {
    var formatKind = TimeFormatKind.HALF_DAY
        set(value) {
            if (field != value) {
                field = value
                applyPattern()
            }
        }

    var locale = Locale.getDefault()
        set(value) {
            if (field != value) {
                field = value
                onLocaleChanged()
            }
        }

    private lateinit var textFormat: SimpleDateFormat
    private var contentDescriptionFormat: SimpleDateFormat? = null

    init {
        timeKeeper.callbacks.add(this)
        onLocaleChanged()
    }

    fun onLocaleChanged() {
        textFormat = getTextFormat(locale)
        contentDescriptionFormat = getContentDescriptionFormat(locale)
        onTimeZoneChanged()
    }

    override fun onTimeZoneChanged() {
        textFormat.timeZone = timeKeeper.timeZone
        contentDescriptionFormat?.timeZone = timeKeeper.timeZone
        applyPattern()
    }

    private fun getTextFormat(locale: Locale): SimpleDateFormat {
        if (locale.language.equals(Locale.ENGLISH.language)) {
            // force date format in English, and time format to use format defined in json
            return SimpleDateFormat(pattern, pattern, ULocale.forLocale(locale))
        } else {
            return SimpleDateFormat.getInstanceForSkeleton(pattern, locale) as SimpleDateFormat
        }
    }

    private fun getContentDescriptionFormat(locale: Locale): SimpleDateFormat? {
        if (!enableContentDescription) return null
        return SimpleDateFormat.getInstanceForSkeleton("hh:mm", locale) as SimpleDateFormat
    }

    private fun applyPattern() {
        textFormat.applyPattern(formatKind.getTextPattern(pattern))
        contentDescriptionFormat?.applyPattern(formatKind.getContentDescriptionPattern(pattern))
    }

    fun getText(): String {
        return textFormat.format(timeKeeper.time)
    }

    fun getContentDescription(): String? {
        return contentDescriptionFormat?.format(timeKeeper.time)
    }
}

class DigitalTimespecHandler(val timespec: DigitalTimespec, val formatter: DigitalTimeFormatter) {
    fun getViewId(): Int = timespec.getViewId("h" in formatter.pattern)

    fun getText(): String {
        val text = formatter.getText()
        return when (timespec) {
            // DIGIT_PAIR & TIME_FULL_FORMAT directly return the result from the ICU time formatter
            DigitalTimespec.DIGIT_PAIR -> text
            DigitalTimespec.TIME_FULL_FORMAT -> text

            // We expect a digit pair string to extract FIRST_DIGIT/SECOND_DIGIT, but some languages
            // produce numerals at utf-8 code points that are not representable by a single char. To
            // account for this, we break the string in half instead.
            DigitalTimespec.FIRST_DIGIT -> text.substring(0, text.length / 2)
            DigitalTimespec.SECOND_DIGIT -> text.substring(text.length / 2, text.length)
        }
    }

    fun getContentDescription(): String? {
        if (timespec != DigitalTimespec.TIME_FULL_FORMAT) return null
        return formatter.getContentDescription()
    }
}
