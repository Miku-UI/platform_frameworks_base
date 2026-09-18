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
package com.android.systemui.statusbar.chips.ui.viewmodel

import android.text.format.DateUtils
import android.widget.ChronometerAdaptiveFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.util.time.SystemClock
import java.time.Duration
import kotlin.math.absoluteValue
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

/**
 * Holds and manages the state for a Chronometer, which shows a timer in a format like "MM:SS" or
 * "H:MM:SS".
 *
 * If [chronometer] is [Chronometer.Running], then this represents a "running" chronometer, either a
 * stopwatch or a countdown timer if [Chronometer.Running.isCountdown] is true.
 * * If [Chronometer.Running.isCountdown] is false, then this Chronometer is counting up from an
 *   event that started in the past, like a phone call that was answered.
 *   [Chronometer.Running.eventTime] represents the time the event started and the timer will tick
 *   up: 04:00, 04:01, ... No timer is shown if [Chronometer.Running.eventTime] is in the future and
 *   [Chronometer.Running.isCountdown] is false.
 * * If [Chronometer.Running.isCountdown] is true, then this Chronometer is counting down to an
 *   event that will occur in the future, like a future meeting. [Chronometer.Running.eventTime]
 *   represents the time the event will occur and the timer will tick down: 04:00, 03:59, ... No
 *   timer is shown if [Chronometer.Running.eventTime] is in the past and
 *   [Chronometer.Running.isCountdown] is true.
 *
 * If [chronometer] is [Chronometer.Paused], then this represents a "paused" chronometer. The
 * duration specified will be shown, unless it is negative.
 */
class ChronometerState(
    private val timeSource: SystemClock,
    private val formatter: Formatter = Formatter.Chronometer,
    val chronometer: Chronometer,
) {
    /** "Current" value of [SystemClock.elapsedRealtime]. Updated by [run]. */
    private var elapsedRealtimeMillis by mutableLongStateOf(timeSource.elapsedRealtime())

    /**
     * The current timer string in a format like "MM:SS" or "H:MM:SS", or null if we shouldn't show
     * the timer string.
     */
    val currentTimeText: String? by derivedStateOf {
        when (chronometer) {
            is Chronometer.Running -> formatRunningChronometer(chronometer)
            is Chronometer.Paused -> formatPausedChronometer(chronometer)
        }
    }

    private fun formatRunningChronometer(chronometer: Chronometer.Running): String? {
        val elapsedTimeMillis = currentValue().toMillis()
        return if (elapsedTimeMillis < 0) {
            null
        } else {
            // LINT.IfChange
            // This should exactly match the implementation in the framework Chronometer.java.
            val adjustedMillis =
                if (chronometer.isCountdown) {
                    // Ensure countdown chronometers round down. (e.g. 999ms shows 00:00).
                    elapsedTimeMillis - 499
                } else {
                    elapsedTimeMillis
                }
            val seconds = (adjustedMillis / 1000f).roundToLong()
            formatter.format(Duration.ofSeconds(seconds))
            // LINT.ThenChange(/core/java/android/widget/Chronometer.java)
        }
    }

    private fun formatPausedChronometer(chronometer: Chronometer.Paused): String? {
        return if (!chronometer.atDuration.isNegative) formatter.format(chronometer.atDuration)
        else null
    }

    suspend fun run(chronometer: Chronometer.Running) {
        // LINT.IfChange
        while (true) {
            elapsedRealtimeMillis = timeSource.elapsedRealtime()
            val currentValue = currentValue()

            // This should exactly match the implementation in the framework Chronometer.java.
            val periodInMillis = formatter.updatePeriod(currentValue).toMillis()
            val delayMillis =
                if (chronometer.isCountdown) {
                    val delay = currentValue.toMillis() % periodInMillis
                    if (delay <= 0) {
                        delay + periodInMillis
                    } else {
                        delay
                    }
                } else {
                    periodInMillis - (currentValue.toMillis().absoluteValue % periodInMillis)
                }

            // Aim for 3 milliseconds into the next second so we don't update exactly on the
            // second.
            delay(delayMillis + 3)
        }
        // LINT.ThenChange(/core/java/android/widget/Chronometer.java)
    }

    private fun currentValue(): Duration {
        if (chronometer is Chronometer.Running) {
            val currentTimeMillis = elapsedRealtimeMillis
            val eventTimeMillis = chronometer.eventTime.asElapsedRealtime(timeSource)

            return if (chronometer.isCountdown) {
                Duration.ofMillis(eventTimeMillis - currentTimeMillis)
            } else {
                Duration.ofMillis(currentTimeMillis - eventTimeMillis)
            }
        } else {
            throw IllegalStateException("Unknown Chronometer type: $chronometer")
        }
    }
}

sealed interface Formatter {
    fun format(value: Duration): String

    /**
     * Period between ticks in the chronometer. Can depend on the current value, if the precision of
     * the formatting is dynamic (e.g. "3h 12m" vs "3:12:03").
     */
    fun updatePeriod(currentValue: Duration): Duration

    /** "Standard" chronometer formater (e.g. H:MM:SS) with second precision. Ticks every second. */
    object Chronometer : Formatter {
        override fun format(value: Duration): String = DateUtils.formatElapsedTime(value.seconds)

        override fun updatePeriod(currentValue: Duration): Duration = Duration.ofSeconds(1)
    }

    object Adaptive : Formatter {
        override fun format(value: Duration): String = ChronometerAdaptiveFormat.format(value)

        override fun updatePeriod(currentValue: Duration): Duration =
            ChronometerAdaptiveFormat.getTickPeriod(currentValue)
    }
}

fun OngoingActivityChipModel.Content.Timer.Format.toFormatter() =
    when (this) {
        OngoingActivityChipModel.Content.Timer.Format.CHRONOMETER -> Formatter.Chronometer
        OngoingActivityChipModel.Content.Timer.Format.ADAPTIVE -> Formatter.Adaptive
    }

/** Remember and manage the ChronometerState */
@Composable
fun rememberChronometerState(
    chronometer: Chronometer,
    formatter: Formatter = Formatter.Chronometer,
    timeSource: SystemClock,
): ChronometerState {
    val state =
        remember(timeSource, formatter, chronometer) {
            ChronometerState(timeSource, formatter, chronometer)
        }

    if (chronometer is Chronometer.Running) {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner, timeSource, formatter, chronometer) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { state.run(chronometer) }
        }
    }

    return state
}
