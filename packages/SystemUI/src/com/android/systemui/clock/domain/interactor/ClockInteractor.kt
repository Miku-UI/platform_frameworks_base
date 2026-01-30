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

package com.android.systemui.clock.domain.interactor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.os.UserHandle
import android.provider.AlarmClock
import androidx.annotation.VisibleForTesting
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.clock.data.repository.ClockRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.tuner.TunerService
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.util.time.SystemClock
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class ClockInteractor
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val repository: ClockRepository,
    private val activityStarter: ActivityStarter,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val systemClock: SystemClock,
    @Background private val coroutineScope: CoroutineScope,
    private val tunerService: TunerService,
) {
    /** [Flow] that emits `Unit` whenever the timezone or locale has changed. */
    val onTimezoneOrLocaleChanged: Flow<Unit> =
        broadcastFlowForActions(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED)
            .emitOnStart()

    /** [StateFlow] that emits whether the clock should show seconds. */
    val showSeconds: StateFlow<Boolean> =
        conflatedCallbackFlow {
                val tunable =
                    TunerService.Tunable { key, newValue ->
                        if (key == CLOCK_SECONDS_TUNER_KEY) {
                            trySend(TunerService.parseIntegerSwitch(newValue, false))
                        }
                    }
                tunerService.addTunable(tunable, CLOCK_SECONDS_TUNER_KEY)
                awaitClose { tunerService.removeTunable(tunable) }
            }
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    /**
     * [StateFlow] that emits the current `Date`.
     *
     * This flow is designed to be efficient. It ticks once per second only when seconds are being
     * displayed, otherwise, it ticks once per minute. It will also emit a new value whenever the
     * time is changed by the system.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentTime: StateFlow<Date> =
        showSeconds
            .flatMapLatest { show ->
                val ticker =
                    if (show) {
                        // Flow that emits every second. minus a few milliseconds to dispatch the
                        // delay.
                        flow {
                            val startTime = systemClock.currentTimeMillis()
                            while (true) {
                                emit(Unit)

                                val delaySkewMillis =
                                    (systemClock.currentTimeMillis() - startTime) % 1000L
                                delay(1000L - delaySkewMillis)
                            }
                        }
                    } else {
                        // Flow that emits every minute.
                        broadcastFlowForActions(Intent.ACTION_TIME_TICK)
                    }

                // A separate flow that emits when time is changed manually.
                val manualOrTimezoneChanges = broadcastFlowForActions(Intent.ACTION_TIME_CHANGED)

                merge(ticker, manualOrTimezoneChanges).emitOnStart()
            }
            .map { Date(systemClock.currentTimeMillis()) }
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.Eagerly,
                initialValue = Date(systemClock.currentTimeMillis()),
            )

    private val longerPattern = context.getString(R.string.abbrev_wday_month_day_no_year_alarm)
    private val shorterPattern = context.getString(R.string.abbrev_month_day_no_year)

    @OptIn(ExperimentalCoroutinesApi::class)
    val longerDateFormat: Flow<DateFormat> =
        onTimezoneOrLocaleChanged.mapLatest { getFormatFromPattern(longerPattern) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val shorterDateFormat: Flow<DateFormat> =
        onTimezoneOrLocaleChanged.mapLatest { getFormatFromPattern(shorterPattern) }

    /** Launch the clock activity. */
    fun launchClockActivity() {
        val nextAlarmIntent = repository.nextAlarmIntent
        if (nextAlarmIntent != null) {
            activityStarter.postStartActivityDismissingKeyguard(nextAlarmIntent)
        } else {
            activityStarter.postStartActivityDismissingKeyguard(
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
                0,
            )
        }
    }

    /**
     * Returns a `Flow` that, when collected, emits `Unit` whenever a broadcast matching one of the
     * given [actionsToFilter] is received.
     */
    private fun broadcastFlowForActions(
        vararg actionsToFilter: String,
        user: UserHandle = UserHandle.SYSTEM,
    ): Flow<Unit> {
        return broadcastDispatcher.broadcastFlow(
            filter = IntentFilter().apply { actionsToFilter.forEach(::addAction) },
            user = user,
        )
    }

    private fun getFormatFromPattern(pattern: String?): DateFormat {
        return DateFormat.getInstanceForSkeleton(pattern, Locale.getDefault()).apply {
            setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
        }
    }

    companion object {
        @VisibleForTesting const val CLOCK_SECONDS_TUNER_KEY = "clock_seconds"
    }
}
