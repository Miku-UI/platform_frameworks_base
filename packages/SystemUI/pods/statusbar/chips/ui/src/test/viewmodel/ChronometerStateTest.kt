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

import android.text.format.DateUtils.formatElapsedTime
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import com.android.systemui.statusbar.chips.ui.model.EventTime
import com.android.systemui.testKosmos
import com.android.systemui.util.time.FakeSystemClock
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit.SECONDS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ChronometerStateTest : SysuiTestCase() {

    private val fakeTimeSource = testKosmos().fakeSystemClock

    @Before
    fun setup() {
        fakeTimeSource.setCurrentTime(SYSTEM_NOW)
        fakeTimeSource.setElapsedRealtime(0)
    }

    @Test
    fun initialText_isEventInFutureFalse_timeIsNow() = runTest {
        fakeTimeSource.setElapsedRealtime(3_000)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(EventTime.ElapsedRealtime(3_000), isCountdown = false),
            )
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 0))
    }

    @Test
    fun initialText_isEventInFutureFalse_timeInPast() = runTest {
        fakeTimeSource.setElapsedRealtime(3_000)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(EventTime.ElapsedRealtime(1_000), isCountdown = false),
            )
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 2))
    }

    @Test
    fun initialText_isEventInFutureFalse_timeInFuture() = runTest {
        fakeTimeSource.setElapsedRealtime(3_000)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(EventTime.ElapsedRealtime(5_000), isCountdown = false),
            )
        // When isEventInFuture=false, eventTimeMillis needs to be in the past if we want text to
        // show
        assertThat(state.currentTimeText).isNull()
    }

    @Test
    fun initialText_isEventInFutureFalse_roundsDown() = runTest {
        fakeTimeSource.setElapsedRealtime(4_400)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(EventTime.ElapsedRealtime(3_000), isCountdown = false),
            )
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 1))
    }

    @Test
    fun initialText_isEventInFutureFalse_roundsUp() = runTest {
        fakeTimeSource.setElapsedRealtime(4_600)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(EventTime.ElapsedRealtime(3_000), isCountdown = false),
            )
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 2))
    }

    @Test
    fun initialText_isEventInFutureTrue_timeIsNow() = runTest {
        fakeTimeSource.setElapsedRealtime(3_000)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(EventTime.ElapsedRealtime(3_000), isCountdown = true),
            )
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 0))
    }

    @Test
    fun initialText_isEventInFutureTrue_timeInFuture() = runTest {
        fakeTimeSource.setElapsedRealtime(3_000)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(EventTime.ElapsedRealtime(5_000), isCountdown = true),
            )
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 2))
    }

    @Test
    fun initialText_isEventInFutureTrue_timeInPast() = runTest {
        fakeTimeSource.setElapsedRealtime(3_000)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(EventTime.ElapsedRealtime(1_000), isCountdown = true),
            )
        // When isEventInFuture=true, eventTimeMillis needs to be in the future if we want text to
        // show
        assertThat(state.currentTimeText).isNull()
    }

    @Test
    fun initialText_isEventInFutureTrue_roundsDown() = runTest {
        fakeTimeSource.setElapsedRealtime(3_600)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(EventTime.ElapsedRealtime(5_000), isCountdown = true),
            )
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 1))
    }

    @Test
    fun initialText_isEventInFutureTrue_doesNotRoundUp() = runTest {
        fakeTimeSource.setElapsedRealtime(3_050)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(EventTime.ElapsedRealtime(5_000), isCountdown = true),
            )
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 1))
    }

    @Test
    fun initialText_countdownToSystemClockFuture_isTimeRemaining() = runTest {
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                chronometer =
                    Chronometer.Running(
                        EventTime.ClockTime(SYSTEM_NOW.plus(10, SECONDS)),
                        isCountdown = true,
                    ),
            )
        assertThat(state.currentTimeText).isEqualTo("00:10")
    }

    @Test
    fun initialText_countdownToSystemClockPast_isNull() = runTest {
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                chronometer =
                    Chronometer.Running(
                        EventTime.ClockTime(SYSTEM_NOW.minus(10, SECONDS)),
                        isCountdown = true,
                    ),
            )
        assertThat(state.currentTimeText).isNull()
    }

    @Test
    fun initialText_countupFromSystemClockPast_isTimeElapsed() = runTest {
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                chronometer =
                    Chronometer.Running(
                        EventTime.ClockTime(SYSTEM_NOW.minus(10, SECONDS)),
                        isCountdown = false,
                    ),
            )
        assertThat(state.currentTimeText).isEqualTo("00:10")
    }

    @Test
    fun initialText_countupFromSystemClockFuture_isNull() = runTest {
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                chronometer =
                    Chronometer.Running(
                        EventTime.ClockTime(SYSTEM_NOW.plus(10, SECONDS)),
                        isCountdown = false,
                    ),
            )
        assertThat(state.currentTimeText).isNull()
    }

    @Test
    fun initialText_adaptiveFormat() = runTest {
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Adaptive,
                chronometer = Chronometer.Paused(Duration.ofMinutes(2).plusSeconds(30)),
            )
        assertThat(state.currentTimeText).isEqualTo("2m 30s")
    }

    enum class RunningTimeTest {
        ELAPSED_REALTIME {
            override fun newEventTime(atMillis: Long) = EventTime.ElapsedRealtime(atMillis)

            override fun setClock(clock: FakeSystemClock, millis: Long) {
                clock.setElapsedRealtime(millis)
            }
        },
        CLOCK_TIME {
            override fun newEventTime(atMillis: Long) =
                EventTime.ClockTime(SYSTEM_NOW.plusMillis(atMillis))

            override fun setClock(clock: FakeSystemClock, millis: Long) {
                clock.setCurrentTime(SYSTEM_NOW.plusMillis(millis))
            }
        };

        abstract fun newEventTime(atMillis: Long): EventTime

        abstract fun setClock(clock: FakeSystemClock, millis: Long)
    }

    @Test
    fun textUpdates_elapsedRealtime_isEventInFutureFalse_timeInPast() =
        textUpdates_isEventInFutureFalse_timeInPast(RunningTimeTest.ELAPSED_REALTIME)

    @Test
    fun textUpdates_clockTime_isEventInFutureFalse_timeInPast() =
        textUpdates_isEventInFutureFalse_timeInPast(RunningTimeTest.CLOCK_TIME)

    private fun textUpdates_isEventInFutureFalse_timeInPast(test: RunningTimeTest) = runTest {
        val eventTime = 1000L
        test.setClock(fakeTimeSource, eventTime)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(test.newEventTime(eventTime), isCountdown = false),
            )
        val job = startChronometer(state)

        val elapsedTime = 5000L
        advanceClockAndTestByMillis(elapsedTime)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(elapsedTime / 1000))

        val additionalTime = 6000L
        advanceClockAndTestByMillis(additionalTime)
        assertThat(state.currentTimeText)
            .isEqualTo(formatElapsedTime((elapsedTime + additionalTime) / 1000))

        job.cancelAndJoin()
    }

    @Test
    fun textUpdates_elapedRealtime_isEventInFutureFalse_timeChangesFromFutureToPast() =
        textUpdates_isEventInFutureFalse_timeChangesFromFutureToPast(
            RunningTimeTest.ELAPSED_REALTIME
        )

    @Test
    fun textUpdates_clockTime_isEventInFutureFalse_timeChangesFromFutureToPast() =
        textUpdates_isEventInFutureFalse_timeChangesFromFutureToPast(RunningTimeTest.CLOCK_TIME)

    private fun textUpdates_isEventInFutureFalse_timeChangesFromFutureToPast(
        test: RunningTimeTest
    ) = runTest {
        val eventTime = 15_000L
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(test.newEventTime(eventTime), isCountdown = false),
            )
        val job = startChronometer(state)

        // WHEN the time is 5 but the eventTime is 15
        advanceClockAndTestByMillis(5_000L)
        // THEN no text is shown
        assertThat(state.currentTimeText).isNull()

        // WHEN the time advances to 40
        advanceClockAndTestByMillis(35_000)
        // THEN text is shown as 25 seconds (40 - 15)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 25))

        job.cancelAndJoin()
    }

    @Test
    fun textUpdates_elapsedRealtime_isEventInFutureTrue_timeInFuture() =
        textUpdates_isEventInFutureTrue_timeInFuture(RunningTimeTest.ELAPSED_REALTIME)

    @Test
    fun textUpdates_clockTime_isEventInFutureTrue_timeInFuture() =
        textUpdates_isEventInFutureTrue_timeInFuture(RunningTimeTest.CLOCK_TIME)

    private fun textUpdates_isEventInFutureTrue_timeInFuture(test: RunningTimeTest) = runTest {
        val eventTime = 15_000L
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(test.newEventTime(eventTime), isCountdown = true),
            )
        val job = startChronometer(state)

        advanceClockAndTestByMillis(5_000L)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 10))

        val additionalTime = 6000L
        advanceClockAndTestByMillis(additionalTime)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 4))

        job.cancelAndJoin()
    }

    @Test
    fun textUpdates_elapsedRealtime_isEventInFutureTrue_timeChangesFromFutureToPast() =
        textUpdates_isEventInFutureTrue_timeChangesFromFutureToPast(
            RunningTimeTest.ELAPSED_REALTIME
        )

    @Test
    fun textUpdates_clockTime_isEventInFutureTrue_timeChangesFromFutureToPast() =
        textUpdates_isEventInFutureTrue_timeChangesFromFutureToPast(RunningTimeTest.CLOCK_TIME)

    private fun textUpdates_isEventInFutureTrue_timeChangesFromFutureToPast(test: RunningTimeTest) =
        runTest {
            val eventTime = 15_000L
            val state =
                ChronometerState(
                    fakeTimeSource,
                    Formatter.Chronometer,
                    Chronometer.Running(test.newEventTime(eventTime), isCountdown = true),
                )
            val job = startChronometer(state)

            // WHEN the time is 5 and the eventTime is 15
            advanceClockAndTestByMillis(5_000L)
            // THEN 10 seconds is shown
            assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 10))

            // WHEN the time advances to 40 (past the event time)
            advanceClockAndTestByMillis(35_000)
            // THEN no text is shown
            assertThat(state.currentTimeText).isNull()

            job.cancelAndJoin()
        }

    @Test
    fun textUpdates_elapsedRealtime_isEventInFutureFalse_initialDelaySkewIsCorrect() =
        textUpdates_isEventInFutureFalse_initialDelaySkewIsCorrect(RunningTimeTest.ELAPSED_REALTIME)

    @Test
    fun textUpdates_clockTime_isEventInFutureFalse_initialDelaySkewIsCorrect() =
        textUpdates_isEventInFutureFalse_initialDelaySkewIsCorrect(RunningTimeTest.CLOCK_TIME)

    private fun textUpdates_isEventInFutureFalse_initialDelaySkewIsCorrect(test: RunningTimeTest) =
        runTest {
            // Event is at 1000ms, and our current time is 4480ms
            // We should show 00:03 for 520ms, and then update to 00:04
            val eventTime = 1_000L
            test.setClock(fakeTimeSource, 4_480L)
            val state =
                ChronometerState(
                    fakeTimeSource,
                    Formatter.Chronometer,
                    Chronometer.Running(test.newEventTime(eventTime), isCountdown = false),
                )
            val job = startChronometer(state)

            assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 3))

            advanceClockAndTestByMillis(525L) // A few milliseconds buffer above 520ms

            assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 4))

            job.cancelAndJoin()
        }

    /** Regression test for b/450909625. */
    @Test
    fun textUpdates_elapsedRealtime_isEventInFutureTrue_initialDelaySkewIsCorrect() =
        textUpdates_isEventInFutureTrue_initialDelaySkewIsCorrect(RunningTimeTest.ELAPSED_REALTIME)

    /** Regression test for b/450909625. */
    @Test
    fun textUpdates_clockTime_isEventInFutureTrue_initialDelaySkewIsCorrect() =
        textUpdates_isEventInFutureTrue_initialDelaySkewIsCorrect(RunningTimeTest.CLOCK_TIME)

    private fun textUpdates_isEventInFutureTrue_initialDelaySkewIsCorrect(test: RunningTimeTest) =
        runTest {
            // Event is at 5000ms, and our current time is 2980ms
            // We should show 00:02 for 20ms, and then update to 00:01
            val eventTime = 5_000L
            test.setClock(fakeTimeSource, 2_980L)
            val state =
                ChronometerState(
                    fakeTimeSource,
                    Formatter.Chronometer,
                    Chronometer.Running(test.newEventTime(eventTime), isCountdown = true),
                )
            val job = startChronometer(state)

            assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 2))

            advanceClockAndTestByMillis(25L) // A few milliseconds buffer above 20ms

            assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 1))

            job.cancelAndJoin()
        }

    @Test
    fun textUpdates_elapsedRealtime_isEventInFutureFalse_ticksEachSecond() =
        textUpdates_isEventInFutureFalse_ticksEachSecond(RunningTimeTest.ELAPSED_REALTIME)

    @Test
    fun textUpdates_clockTime_isEventInFutureFalse_ticksEachSecond() =
        textUpdates_isEventInFutureFalse_ticksEachSecond(RunningTimeTest.CLOCK_TIME)

    private fun textUpdates_isEventInFutureFalse_ticksEachSecond(test: RunningTimeTest) = runTest {
        val eventTime = 1_000L
        test.setClock(fakeTimeSource, 4_200L)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(test.newEventTime(eventTime), isCountdown = false),
            )
        val job = startChronometer(state)

        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 3))

        // First: get past the initial skew of 800ms (with a few milliseconds buffer)
        advanceClockAndTestByMillis(805L)

        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 4))

        // Then, verify we tick every second
        for (i in 1L..50L) {
            advanceClockAndTestByMillis(1000L)
            assertThat(state.currentTimeText)
                .isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 4 + i))
        }

        job.cancelAndJoin()
    }

    @Test
    fun textUpdates_elapsedRealtime_isEventInFutureTrue_ticksEachSecond() =
        textUpdates_isEventInFutureTrue_ticksEachSecond(RunningTimeTest.ELAPSED_REALTIME)

    @Test
    fun textUpdates_clockTime_isEventInFutureTrue_ticksEachSecond() =
        textUpdates_isEventInFutureTrue_ticksEachSecond(RunningTimeTest.CLOCK_TIME)

    private fun textUpdates_isEventInFutureTrue_ticksEachSecond(test: RunningTimeTest) = runTest {
        val eventTime = 100_000L
        test.setClock(fakeTimeSource, 4_800L)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(test.newEventTime(eventTime), isCountdown = true),
            )
        val job = startChronometer(state)

        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 95))

        // First: get past the initial skew of 200ms (with a few milliseconds buffer)
        advanceClockAndTestByMillis(205L)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 94))

        // Then, verify we tick every second
        for (i in 1L..50L) {
            advanceClockAndTestByMillis(1000L)
            assertThat(state.currentTimeText)
                .isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 94 - i))
        }

        job.cancelAndJoin()
    }

    @Test
    fun textUpdates_elapsedRealtime_afterResettingBase_isEventInFutureFalse() =
        textUpdates_afterResettingBase_isEventInFutureFalse(RunningTimeTest.ELAPSED_REALTIME)

    @Test
    fun textUpdates_clockTime_afterResettingBase_isEventInFutureFalse() =
        textUpdates_afterResettingBase_isEventInFutureFalse(RunningTimeTest.CLOCK_TIME)

    private fun textUpdates_afterResettingBase_isEventInFutureFalse(test: RunningTimeTest) =
        runTest {
            val initialElapsedTime = 30000L
            val startTime = 50000L
            test.setClock(fakeTimeSource, startTime)
            val state =
                ChronometerState(
                    fakeTimeSource,
                    Formatter.Chronometer,
                    Chronometer.Running(test.newEventTime(startTime), isCountdown = false),
                )
            val job = startChronometer(state)

            advanceClockAndTestByMillis(initialElapsedTime)
            assertThat(state.currentTimeText)
                .isEqualTo(formatElapsedTime(initialElapsedTime / 1000))

            job.cancelAndJoin()

            val newElapsedTime = 5000L
            val newStartTime = 100000L
            test.setClock(fakeTimeSource, newStartTime)
            val newState =
                ChronometerState(
                    fakeTimeSource,
                    Formatter.Chronometer,
                    Chronometer.Running(test.newEventTime(newStartTime), isCountdown = false),
                )
            val newJob = startChronometer(newState)

            advanceClockAndTestByMillis(newElapsedTime)
            assertThat(newState.currentTimeText).isEqualTo(formatElapsedTime(newElapsedTime / 1000))

            newJob.cancelAndJoin()
        }

    @Test
    fun textUpdates_elapsedRealtime_afterResettingBase_isEventInFutureTrue() =
        textUpdates_afterResettingBase_isEventInFutureTrue(RunningTimeTest.ELAPSED_REALTIME)

    @Test
    fun textUpdates_clockTime_afterResettingBase_isEventInFutureTrue() =
        textUpdates_afterResettingBase_isEventInFutureTrue(RunningTimeTest.CLOCK_TIME)

    private fun textUpdates_afterResettingBase_isEventInFutureTrue(test: RunningTimeTest) =
        runTest {
            val initialElapsedTime = 40_000L
            val eventTime = 50_000L
            val state =
                ChronometerState(
                    fakeTimeSource,
                    Formatter.Chronometer,
                    Chronometer.Running(test.newEventTime(eventTime), isCountdown = true),
                )
            val job = startChronometer(state)

            advanceClockAndTestByMillis(initialElapsedTime)
            // Time should be 50 - 40 = 10
            assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 10))

            job.cancelAndJoin()

            val newElapsedTime = 75_000L
            val newEventTime = 100_000L
            val newState =
                ChronometerState(
                    fakeTimeSource,
                    Formatter.Chronometer,
                    Chronometer.Running(test.newEventTime(newEventTime), isCountdown = true),
                )
            val newJob = startChronometer(newState)

            advanceClockAndTestByMillis(newElapsedTime - initialElapsedTime)
            // Time should be 100 - 75 = 25
            assertThat(newState.currentTimeText)
                .isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 25))

            newJob.cancelAndJoin()
        }

    @Test
    fun textUpdates_elapsedRealtime_afterResettingisEventInFuture() =
        textUpdates_afterResettingisEventInFuture(RunningTimeTest.ELAPSED_REALTIME)

    @Test
    fun textUpdates_clockTime_afterResettingisEventInFuture() =
        textUpdates_afterResettingisEventInFuture(RunningTimeTest.CLOCK_TIME)

    private fun textUpdates_afterResettingisEventInFuture(test: RunningTimeTest) = runTest {
        val initialElapsedTime = 40_000L
        val eventTime = 50_000L
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(test.newEventTime(eventTime), isCountdown = true),
            )
        val job = startChronometer(state)

        advanceClockAndTestByMillis(initialElapsedTime)
        // Time should be 50 - 40 = 10
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 10))

        job.cancelAndJoin()

        val newElapsedTime = 70_000L
        val newState =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(test.newEventTime(eventTime), isCountdown = false),
            )
        val newJob = startChronometer(newState)

        advanceClockAndTestByMillis(newElapsedTime - initialElapsedTime)
        // Time should be 70 - 50 = 20
        assertThat(newState.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 20))

        newJob.cancelAndJoin()
    }

    @Test
    fun textUpdates_systemClockJumps_textUpdatesOnNextTick() = runTest {
        // Timer started 30 seconds ago.
        fakeTimeSource.setCurrentTime(SYSTEM_NOW)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                Chronometer.Running(
                    EventTime.ClockTime(SYSTEM_NOW.minusSeconds(30)),
                    isCountdown = false,
                ),
            )
        val job = startChronometer(state)
        assertThat(state.currentTimeText).isEqualTo("00:30")

        // 5 seconds pass, all normal
        advanceClockAndTestByMillis(5_000)
        assertThat(state.currentTimeText).isEqualTo("00:35")

        // Now, we CHANGE the system clock, so it SKIPS forward by 10 seconds
        fakeTimeSource.setCurrentTime(fakeTimeSource.currentTime().plusSeconds(10))
        // Then, after 1 second of real time passes...
        advanceClockAndTestByMillis(1_000)

        // ... the chronometer has also jumped.
        assertThat(state.currentTimeText).isEqualTo("00:46")

        job.cancelAndJoin()
    }

    @Test
    fun textUpdates_adaptiveFormat() = runTest {
        // Countdown to 4min 40sec
        fakeTimeSource.setElapsedRealtime(0)
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Adaptive,
                Chronometer.Running(
                    EventTime.ElapsedRealtime(Duration.ofMinutes(4).plusSeconds(40).toMillis()),
                    isCountdown = true,
                ),
            )
        val job = startChronometer(state)

        // 4min 40sec -> Truncated to 4 min
        assertThat(state.currentTimeText).isEqualTo("4m")

        // 39 seconds pass, 4m1s remaining -> No change
        advanceClockAndTestByMillis(39_000)
        assertThat(state.currentTimeText).isEqualTo("4m")

        // 2 more seconds pass, 3m59s remaining -> Truncated to 3 min
        advanceClockAndTestByMillis(2_000)
        assertThat(state.currentTimeText).isEqualTo("3m")

        // 1 more minute passes, 2m59s remaining -> Less than 3 minutes so seconds are shown
        advanceClockAndTestByMillis(60_000)
        assertThat(state.currentTimeText).isEqualTo("2m 59s")

        job.cancelAndJoin()
    }

    @Test
    fun text_pausedDurationPositive_isShown() = runTest {
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                chronometer = Chronometer.Paused(Duration.ofSeconds(90)),
            )

        assertThat(state.currentTimeText).isEqualTo("01:30")
    }

    @Test
    fun text_pausedDurationZero_isShown() = runTest {
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                chronometer = Chronometer.Paused(Duration.ZERO),
            )

        assertThat(state.currentTimeText).isEqualTo("00:00")
    }

    @Test
    fun text_pausedDurationNegative_isNotShown() = runTest {
        val state =
            ChronometerState(
                fakeTimeSource,
                Formatter.Chronometer,
                chronometer = Chronometer.Paused(Duration.ofSeconds(-10)),
            )

        assertThat(state.currentTimeText).isNull()
    }

    /** Starts the chronometer ticking. */
    private fun TestScope.startChronometer(state: ChronometerState): Job {
        val job = launch { state.run(state.chronometer as Chronometer.Running) }
        // advancing the time ensures the first loop inside [ChronometerState.run] is invoked.
        advanceTimeBy(1)
        return job
    }

    /** Advances both the time source and the test clock by the same amount. */
    private fun TestScope.advanceClockAndTestByMillis(additionalTime: Long) {
        fakeTimeSource.advanceTime(additionalTime)
        advanceTimeBy(additionalTime)
    }

    companion object {
        private val SYSTEM_NOW = Instant.ofEpochMilli(1763645100000) // 2025-11-20 13:25:00 UTC
    }
}
