/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.app.AlarmManager
import android.content.Intent
import android.provider.AlarmClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.plugins.activityStarter
import com.android.systemui.statusbar.policy.NextAlarmController.NextAlarmChangeCallback
import com.android.systemui.statusbar.policy.nextAlarmController
import com.android.systemui.testKosmos
import com.android.systemui.tuner.TunerService.Tunable
import com.android.systemui.tuner.tunerService
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ClockInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val underTest = kosmos.clockInteractor

    @Test
    fun launchClockActivity_default() =
        kosmos.runTest {
            underTest.launchClockActivity()
            verify(activityStarter)
                .postStartActivityDismissingKeyguard(
                    argThat { intent: Intent? -> intent?.action == AlarmClock.ACTION_SHOW_ALARMS },
                    any<Int>(),
                )
        }

    @Test
    fun launchClockActivity_nextAlarmIntent() =
        kosmos.runTest {
            val captor =
                argumentCaptor<NextAlarmChangeCallback> {
                    verify(nextAlarmController).addCallback(capture())
                }
            captor.firstValue.onNextAlarmChanged(AlarmManager.AlarmClockInfo(1L, mock()))

            underTest.launchClockActivity()
            verify(activityStarter).postStartActivityDismissingKeyguard(any())
        }

    @Test
    fun onTimezoneOrLocaleChanged_localeAndTimezoneChanged_emitsForEach() =
        kosmos.runTest {
            val timeZoneOrLocaleChanges by collectValues(underTest.onTimezoneOrLocaleChanged)

            sendIntentActionBroadcast(Intent.ACTION_TIMEZONE_CHANGED)
            sendIntentActionBroadcast(Intent.ACTION_LOCALE_CHANGED)
            sendIntentActionBroadcast(Intent.ACTION_LOCALE_CHANGED)
            sendIntentActionBroadcast(Intent.ACTION_TIMEZONE_CHANGED)

            assertThat(timeZoneOrLocaleChanges).hasSize(4)
        }

    @Test
    fun onTimezoneOrLocaleChanged_timeChanged_doesNotEmit() =
        kosmos.runTest {
            val timeZoneOrLocaleChanges by collectValues(underTest.onTimezoneOrLocaleChanged)
            assertThat(timeZoneOrLocaleChanges).hasSize(1)

            sendIntentActionBroadcast(Intent.ACTION_TIME_CHANGED)
            sendIntentActionBroadcast(Intent.ACTION_TIME_TICK)

            // Expect only 1 event to have been emitted onStart, but no more.
            assertThat(timeZoneOrLocaleChanges).hasSize(1)
        }

    @Test
    fun currentTime_initialTime() =
        kosmos.runTest {
            assertThat(underTest.currentTime.value)
                .isEqualTo(Date(fakeSystemClock.currentTimeMillis()))
        }

    @Test
    fun currentTime_timeChanged() =
        kosmos.runTest {
            val currentTime by collectLastValue(underTest.currentTime)

            sendIntentActionBroadcast(Intent.ACTION_TIME_CHANGED)
            val earlierTime = checkNotNull(currentTime)

            fakeSystemClock.advanceTime(3.seconds.inWholeMilliseconds)
            runCurrent()

            sendIntentActionBroadcast(Intent.ACTION_TIME_CHANGED)
            val laterTime = checkNotNull(currentTime)

            assertThat(differenceBetween(laterTime, earlierTime)).isEqualTo(3.seconds)
        }

    @Test
    fun currentTime_timeTicked() =
        kosmos.runTest {
            val currentTime by collectLastValue(underTest.currentTime)

            sendIntentActionBroadcast(Intent.ACTION_TIME_TICK)
            val earlierTime = checkNotNull(currentTime)

            fakeSystemClock.advanceTime(7.seconds.inWholeMilliseconds)
            runCurrent()

            sendIntentActionBroadcast(Intent.ACTION_TIME_TICK)
            val laterTime = checkNotNull(currentTime)

            assertThat(differenceBetween(laterTime, earlierTime)).isEqualTo(7.seconds)
        }

    @Test
    fun showSeconds_tunerChanges_flowEmits() =
        kosmos.runTest {
            val showSeconds by collectLastValue(underTest.showSeconds)
            assertThat(showSeconds).isFalse()

            getTunable().onTuningChanged(ClockInteractor.CLOCK_SECONDS_TUNER_KEY, "1")

            assertThat(showSeconds).isTrue()

            getTunable().onTuningChanged(ClockInteractor.CLOCK_SECONDS_TUNER_KEY, "0")

            assertThat(showSeconds).isFalse()
        }

    @Test
    fun currentTime_showSecondsFalse_notChangeEverySecond() =
        kosmos.runTest {
            val currentTime by collectLastValue(underTest.currentTime)
            val showSeconds by collectLastValue(underTest.showSeconds)
            val initialTime = currentTime!!

            assertThat(showSeconds).isFalse()

            fakeSystemClock.advanceTime(1000)
            advanceTimeBy(1000)

            // currentTime should not tick since showSeconds is false by default
            assertThat(currentTime).isEqualTo(initialTime)
        }

    @Test
    fun currentTime_showSecondsTrue_changesEverySecond() =
        kosmos.runTest {
            val currentTime by collectLastValue(underTest.currentTime)
            val showSeconds by collectLastValue(underTest.showSeconds)
            val initialTime = currentTime!!

            getTunable().onTuningChanged(ClockInteractor.CLOCK_SECONDS_TUNER_KEY, "1")

            assertThat(showSeconds).isTrue()

            fakeSystemClock.advanceTime(1000)
            advanceTimeBy(1000)

            assertThat(currentTime).isNotEqualTo(initialTime)

            val timeAfterTick = currentTime!!
            fakeSystemClock.advanceTime(1000)
            advanceTimeBy(1000)

            assertThat(currentTime).isNotEqualTo(timeAfterTick)
        }

    @Test
    fun currentTime_showSecondsTrueToFalse_notChangesEverySecond() =
        kosmos.runTest {
            val currentTime by collectLastValue(underTest.currentTime)
            val showSeconds by collectLastValue(underTest.showSeconds)
            val initialTime = currentTime!!

            getTunable().onTuningChanged(ClockInteractor.CLOCK_SECONDS_TUNER_KEY, "1")

            assertThat(showSeconds).isTrue()

            fakeSystemClock.advanceTime(1000)
            advanceTimeBy(1000)

            assertThat(currentTime).isNotEqualTo(initialTime)

            val timeAfterTick = currentTime!!

            getTunable().onTuningChanged(ClockInteractor.CLOCK_SECONDS_TUNER_KEY, "0")

            assertThat(showSeconds).isFalse()

            advanceTimeBy(1000)
            fakeSystemClock.advanceTime(1000)

            // currentTime should not tick since showSeconds is now false.
            assertThat(currentTime).isEqualTo(timeAfterTick)
        }

    @Test
    fun currentTime_showSecondsFalseToTrue_changesEverySecond() =
        kosmos.runTest {
            val currentTime by collectLastValue(underTest.currentTime)
            val showSeconds by collectLastValue(underTest.showSeconds)
            val initialTime = currentTime!!

            assertThat(showSeconds).isFalse()

            fakeSystemClock.advanceTime(1000)
            advanceTimeBy(1000)

            assertThat(currentTime).isEqualTo(initialTime)

            val timeAfterTick = currentTime!!

            getTunable().onTuningChanged(ClockInteractor.CLOCK_SECONDS_TUNER_KEY, "1")

            assertThat(showSeconds).isTrue()

            advanceTimeBy(1000)
            fakeSystemClock.advanceTime(1000)

            assertThat(currentTime).isNotEqualTo(timeAfterTick)
        }

    private fun differenceBetween(date1: Date, date2: Date): Duration {
        return (date1.time - date2.time).milliseconds
    }

    private fun Kosmos.sendIntentActionBroadcast(intentAction: String) {
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, Intent(intentAction))
        runCurrent()
    }

    private fun Kosmos.getTunable(): Tunable {
        val tunableCaptor = argumentCaptor<Tunable>()
        verify(tunerService).addTunable(tunableCaptor.capture(), any())
        return tunableCaptor.firstValue
    }
}
