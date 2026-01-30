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

package com.android.systemui.clock.ui.viewmodel

import android.icu.text.DateTimePatternGenerator
import androidx.compose.runtime.getValue
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.clock.domain.interactor.ClockInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.util.time.DateFormatUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** AM/PM styling for the clock UI */
enum class AmPmStyle {
    Shown,
    Gone,
}

/** Models UI state for the clock. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClockViewModel
@AssistedInject
constructor(
    clockInteractor: ClockInteractor,
    private val dateFormatUtil: DateFormatUtil,
    @Assisted private val amPmStyle: AmPmStyle,
) : ExclusiveActivatable() {
    private val hydrator = Hydrator("ClockViewModel.hydrator")

    // For content description, we use `DateTimePatternGenerator` to generate the best time format
    // for all the locales. For clock time, since we want to utilize removing AM/PM marker for
    // `AmPmStyle.Gone`, we will just use `SimpleDateFormat` instead.
    private lateinit var dateTimePatternGenerator: DateTimePatternGenerator

    private val contentDescriptionFormat: Flow<DateFormat> =
        combine(clockInteractor.onTimezoneOrLocaleChanged, clockInteractor.showSeconds) {
            _,
            showSeconds ->
            getSimpleDateFormat(getContentDescriptionFormatString(showSeconds))
        }

    private val _contentDescriptionText: Flow<String> =
        combine(contentDescriptionFormat, clockInteractor.currentTime) {
            contentDescriptionFormat,
            time ->
            contentDescriptionFormat.format(time)
        }

    val contentDescriptionText: String by
        hydrator.hydratedStateOf(
            traceName = "clockContentDescriptionText",
            initialValue = clockInteractor.currentTime.value.toString(),
            source = _contentDescriptionText,
        )

    private val clockTextFormat: Flow<SimpleDateFormat> =
        combine(clockInteractor.onTimezoneOrLocaleChanged, clockInteractor.showSeconds) {
            _,
            showSeconds ->
            getSimpleDateFormat(getClockTextFormatString(showSeconds))
        }

    private val _clockText: Flow<String> =
        combine(clockTextFormat, clockInteractor.currentTime) { clockTextFormat, time ->
            clockTextFormat.format(time)
        }

    val clockText: String by
        hydrator.hydratedStateOf(
            traceName = "clockText",
            initialValue = clockInteractor.currentTime.value.toString(),
            source = _clockText,
        )

    val longerDateText: String by
        hydrator.hydratedStateOf(
            traceName = "longerDateText",
            initialValue = "",
            source =
                combine(clockInteractor.longerDateFormat, clockInteractor.currentTime) {
                    format,
                    time ->
                    format.format(time)
                },
        )

    val shorterDateText: String by
        hydrator.hydratedStateOf(
            traceName = "shorterDateText",
            initialValue = "",
            source =
                combine(clockInteractor.shorterDateFormat, clockInteractor.currentTime) {
                    format,
                    time ->
                    format.format(time)
                },
        )

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }

            awaitCancellation()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(amPmStyle: AmPmStyle): ClockViewModel
    }

    private fun getContentDescriptionFormatString(showSeconds: Boolean): String {
        dateTimePatternGenerator = DateTimePatternGenerator.getInstance(Locale.getDefault())

        var formatSkeleton = if (dateFormatUtil.is24HourFormat) "Hm" else "hm"
        if (showSeconds) {
            formatSkeleton += "s"
        }

        return dateTimePatternGenerator.getBestPattern(formatSkeleton)
    }

    private fun getClockTextFormatString(showSeconds: Boolean): String {
        var formatString =
            if (dateFormatUtil.is24HourFormat) {
                "H:mm"
            } else {
                "h:mm"
            }

        if (showSeconds) {
            formatString += ":ss"
        }

        if (amPmStyle == AmPmStyle.Shown && !dateFormatUtil.is24HourFormat) {
            // Note that we always put the AM/PM marker at the end of the string, and this could be
            // wrong for certain languages.
            formatString += "\u202Fa"
        }

        return formatString
    }

    private fun getSimpleDateFormat(formatString: String): SimpleDateFormat {
        return SimpleDateFormat(formatString, Locale.getDefault())
    }
}
