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

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.clock.domain.interactor.ClockInteractor
import com.android.systemui.clock.domain.interactor.clockInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.testKosmos
import com.android.systemui.tuner.TunerService.Tunable
import com.android.systemui.tuner.tunerService
import com.android.systemui.util.time.dateFormatUtil
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ClockViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest by lazy { kosmos.clockViewModel }

    private val defaultLocale: Locale = Locale.getDefault()
    private val defaultTimeZone: TimeZone = TimeZone.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultTimeZone)
    }

    @Before
    fun setup() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun is24HourFormatTrue_clockTextAndDescription_equalsCurrentTime() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            whenever(dateFormatUtil.is24HourFormat).thenReturn(true)
            underTest.activateIn(testScope)

            assertThat(underTest.clockText).isEqualTo("23:12")
            assertThat(underTest.contentDescriptionText).isEqualTo("23:12")
        }

    @Test
    fun is24HourFormatFalse_clockTextAndDescription_equalsCurrentTime() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            whenever(dateFormatUtil.is24HourFormat).thenReturn(false)
            underTest.activateIn(testScope)

            assertThat(underTest.clockText).isEqualTo("11:12\u202FPM")
            assertThat(underTest.contentDescriptionText).isEqualTo("11:12\u202FPM")
        }

    @Test
    fun clockTextAndDescription_updatesWhenTimeTick() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            whenever(dateFormatUtil.is24HourFormat).thenReturn(true)
            underTest.activateIn(testScope)

            assertThat(underTest.clockText).isEqualTo("23:12")
            assertThat(underTest.contentDescriptionText).isEqualTo("23:12")

            fakeSystemClock.advanceTime(2.minutes.inWholeMilliseconds)
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_TIME_TICK),
            )
            runCurrent()

            assertThat(underTest.clockText).isEqualTo("23:14")
            assertThat(underTest.contentDescriptionText).isEqualTo("23:14")
        }

    @Test
    fun clockTextAndDescription_updatesWhenTimeChanged() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            whenever(dateFormatUtil.is24HourFormat).thenReturn(true)
            underTest.activateIn(testScope)

            assertThat(underTest.clockText).isEqualTo("23:12")
            assertThat(underTest.contentDescriptionText).isEqualTo("23:12")

            fakeSystemClock.advanceTime(2.minutes.inWholeMilliseconds)
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_TIME_CHANGED),
            )
            runCurrent()

            assertThat(underTest.clockText).isEqualTo("23:14")
            assertThat(underTest.contentDescriptionText).isEqualTo("23:14")
        }

    @Test
    fun clockTextAndDescription_updatesWhenTimezoneChanged() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            whenever(dateFormatUtil.is24HourFormat).thenReturn(true)
            underTest.activateIn(testScope)

            assertThat(underTest.clockText).isEqualTo("23:12")
            assertThat(underTest.contentDescriptionText).isEqualTo("23:12")

            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_TIMEZONE_CHANGED),
            )
            runCurrent()

            assertThat(underTest.clockText).isEqualTo("8:12")
            assertThat(underTest.contentDescriptionText).isEqualTo("08:12")
        }

    @Test
    fun clockTextAndDescription_updatesWhenLocaleChanged_traditionalChinese() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            underTest.activateIn(testScope)

            assertThat(underTest.clockText).isEqualTo("11:12\u202FPM")
            assertThat(underTest.contentDescriptionText).isEqualTo("11:12\u202FPM")

            Locale.setDefault(Locale.TRADITIONAL_CHINESE)
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_LOCALE_CHANGED),
            )
            runCurrent()

            assertThat(underTest.clockText).isEqualTo("11:12\u202F下午")
            assertThat(underTest.contentDescriptionText).isEqualTo("下午11:12")
        }

    @Test
    fun clockTextAndDescription_updatesWhenLocaleChanged_burmese() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            underTest.activateIn(testScope)

            assertThat(underTest.clockText).isEqualTo("11:12\u202FPM")
            assertThat(underTest.contentDescriptionText).isEqualTo("11:12\u202FPM")

            Locale.setDefault(Locale.Builder().setLanguage("my").build())
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_LOCALE_CHANGED),
            )
            runCurrent()

            assertThat(underTest.clockText).isEqualTo("၁၁:၁၂\u202Fညနေ")
            assertThat(underTest.contentDescriptionText).isEqualTo("ညနေ ၁၁:၁၂")
        }

    @Test
    fun clockTextAndDescription_amPmStyleGone() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            whenever(dateFormatUtil.is24HourFormat).thenReturn(false)
            val viewModel =
                ClockViewModel(
                    clockInteractor = clockInteractor,
                    dateFormatUtil = dateFormatUtil,
                    amPmStyle = AmPmStyle.Gone,
                )
            viewModel.activateIn(testScope)

            assertThat(viewModel.clockText).isEqualTo("11:12")
            assertThat(viewModel.contentDescriptionText).isEqualTo("11:12\u202FPM")
        }

    @Test
    fun clockTextAndDescription_amPmStyleShown() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            whenever(dateFormatUtil.is24HourFormat).thenReturn(false)
            val viewModel =
                ClockViewModel(
                    clockInteractor = clockInteractor,
                    dateFormatUtil = dateFormatUtil,
                    amPmStyle = AmPmStyle.Shown,
                )
            viewModel.activateIn(testScope)

            assertThat(viewModel.clockText).isEqualTo("11:12\u202FPM")
            assertThat(viewModel.contentDescriptionText).isEqualTo("11:12\u202FPM")
        }

    @Test
    fun showSeconds_is24HourFormatTrue_clockTextUpdates() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            whenever(dateFormatUtil.is24HourFormat).thenReturn(true)
            underTest.activateIn(testScope)
            assertThat(underTest.clockText).isEqualTo("23:12")
            assertThat(underTest.contentDescriptionText).isEqualTo("23:12")

            getTunable().onTuningChanged(ClockInteractor.CLOCK_SECONDS_TUNER_KEY, "1")

            assertThat(underTest.clockText).isEqualTo("23:12:19")
            assertThat(underTest.contentDescriptionText).isEqualTo("23:12:19")

            getTunable().onTuningChanged(ClockInteractor.CLOCK_SECONDS_TUNER_KEY, "0")

            assertThat(underTest.clockText).isEqualTo("23:12")
            assertThat(underTest.contentDescriptionText).isEqualTo("23:12")
        }

    @Test
    fun showSeconds_is24HourFormatFalse_clockTextUpdates() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            whenever(dateFormatUtil.is24HourFormat).thenReturn(false)
            underTest.activateIn(testScope)
            assertThat(underTest.clockText).isEqualTo("11:12\u202FPM")
            assertThat(underTest.contentDescriptionText).isEqualTo("11:12\u202FPM")

            getTunable().onTuningChanged(ClockInteractor.CLOCK_SECONDS_TUNER_KEY, "1")

            assertThat(underTest.clockText).isEqualTo("11:12:19\u202FPM")
            assertThat(underTest.contentDescriptionText).isEqualTo("11:12:19\u202FPM")

            getTunable().onTuningChanged(ClockInteractor.CLOCK_SECONDS_TUNER_KEY, "0")

            assertThat(underTest.clockText).isEqualTo("11:12\u202FPM")
            assertThat(underTest.contentDescriptionText).isEqualTo("11:12\u202FPM")
        }

    @Test
    fun shorterDateText_updatesWhenTimeTicks() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            underTest.activateIn(testScope)

            assertThat(underTest.shorterDateText).isEqualTo("May 8")

            fakeSystemClock.advanceTime(2.days.inWholeMilliseconds)
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_TIME_TICK),
            )

            assertThat(underTest.shorterDateText).isEqualTo("May 10")
        }

    @Test
    fun longerDateText_updatesWhenTimeTicks() =
        kosmos.runTest {
            fakeSystemClock.setCurrentTimeMillis(CURRENT_TIME_MILLIS)
            underTest.activateIn(testScope)

            assertThat(underTest.longerDateText).isEqualTo("Wed, May 8")

            fakeSystemClock.advanceTime(2.days.inWholeMilliseconds)
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_TIME_TICK),
            )

            assertThat(underTest.longerDateText).isEqualTo("Fri, May 10")
        }

    private fun Kosmos.getTunable(): Tunable {
        val tunableCaptor = argumentCaptor<Tunable>()
        verify(tunerService).addTunable(tunableCaptor.capture(), any())
        return tunableCaptor.firstValue
    }

    companion object {
        private const val CURRENT_TIME_MILLIS = 16641673939408L
    }
}
