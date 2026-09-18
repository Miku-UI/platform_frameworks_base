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

import android.icu.util.Calendar
import com.android.systemui.customization.clocks.DigitalTimespec.DATE_FORMAT
import com.android.systemui.customization.clocks.DigitalTimespec.DIGIT_PAIR
import com.android.systemui.customization.clocks.DigitalTimespec.FIRST_DIGIT
import com.android.systemui.customization.clocks.DigitalTimespec.SECOND_DIGIT
import com.android.systemui.customization.clocks.DigitalTimespec.TIME_FULL_FORMAT

class DigitalTimespecHandler(val timespec: DigitalTimespec, val formatter: DigitalFormatter) {
    fun getViewId(): Int = timespec.getViewId("h" in formatter.textPattern)

    fun getText(): String {
        val text = formatter.getText()

        return when (timespec) {
            // These directly return the result from the ICU time formatter
            DIGIT_PAIR,
            TIME_FULL_FORMAT,
            DATE_FORMAT -> text

            // We expect a digit pair string to extract FIRST_DIGIT/SECOND_DIGIT, but some languages
            // produce numerals at utf-8 code points that are not representable by a single char. To
            // account for this, we break the string in half instead.
            FIRST_DIGIT -> text.substring(0, text.length / 2)
            SECOND_DIGIT -> text.substring(text.length / 2, text.length)
        }
    }

    fun getContentDescription(): String? {
        return when (timespec) {
            TIME_FULL_FORMAT,
            DATE_FORMAT -> formatter.getContentDescription()
            DIGIT_PAIR,
            FIRST_DIGIT,
            SECOND_DIGIT -> null
        }
    }
}

class AnalogTimespecHandler(
    val timespec: AnalogTimespec,
    val tickMode: AnalogTickMode,
    val timeKeeper: TimeKeeper,
) {
    val isSweeping: Boolean
        get() = tickMode == AnalogTickMode.SWEEP

    fun getRotation(): Float {
        return when (timespec) {
            AnalogTimespec.SECONDS -> query(Calendar.SECOND)
            AnalogTimespec.MINUTES -> query(Calendar.MINUTE)
            AnalogTimespec.HOURS -> query(Calendar.HOUR)
            AnalogTimespec.HOURS_OF_DAY -> query(Calendar.HOUR_OF_DAY)
            AnalogTimespec.DAY_OF_WEEK -> query(Calendar.DAY_OF_WEEK)
            AnalogTimespec.DAY_OF_MONTH -> query(Calendar.DAY_OF_MONTH)
            AnalogTimespec.DAY_OF_YEAR -> query(Calendar.DAY_OF_YEAR)
            AnalogTimespec.WEEK -> query(Calendar.WEEK_OF_YEAR)
            AnalogTimespec.MONTH -> query(Calendar.MONTH)
        }
    }

    private fun query(calTimespec: Int, depth: Int = 0): Float {
        return timeKeeper.queryCalendar(calTimespec) { time, min, max ->
            val diff = (max - min + 1).toFloat()
            if (diff <= 0) return@queryCalendar 0f

            var result = (time - min) / diff
            if (isSweeping && depth < SWEEP_QUERY_DEPTH_LIMIT) {
                SWEEP_MAP[calTimespec]?.let { lowerTimespec ->
                    val lowerTimescale = query(lowerTimespec, depth + 1)
                    result += lowerTimescale / diff
                }
            }
            return@queryCalendar result
        }
    }

    companion object {
        private const val SWEEP_QUERY_DEPTH_LIMIT = 2
        private val SWEEP_MAP =
            mapOf(
                Calendar.SECOND to Calendar.MILLISECOND,
                Calendar.MINUTE to Calendar.SECOND,
                Calendar.HOUR to Calendar.MINUTE,
                Calendar.HOUR_OF_DAY to Calendar.MINUTE,
                Calendar.DAY_OF_WEEK to Calendar.HOUR_OF_DAY,
                Calendar.DAY_OF_MONTH to Calendar.HOUR_OF_DAY,
                Calendar.DAY_OF_YEAR to Calendar.HOUR_OF_DAY,
                Calendar.WEEK_OF_YEAR to Calendar.DAY_OF_WEEK,
                Calendar.MONTH to Calendar.DAY_OF_MONTH,
            )
    }
}
