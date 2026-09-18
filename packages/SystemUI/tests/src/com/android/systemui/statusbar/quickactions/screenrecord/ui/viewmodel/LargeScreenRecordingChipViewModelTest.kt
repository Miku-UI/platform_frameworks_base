/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.quickactions.screenrecord.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParametersFactory.screenRecordingParameters
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus.Starting
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus.Stopped
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import com.android.systemui.statusbar.chips.ui.model.EventTime
import com.android.systemui.statusbar.quickactions.shared.model.ChipContent
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipId
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipModel
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class LargeScreenRecordingChipViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val interactor = mock<ScreenRecordingServiceInteractor>()
    private val systemClock = kosmos.fakeSystemClock

    private val statusFlow =
        MutableStateFlow<ScreenRecordingStatus>(Stopped(Stopped.STOP_REASON_NOT_STARTED))

    private lateinit var underTest: LargeScreenRecordingChipViewModel

    @Before
    fun setUp() {
        whenever(interactor.status).thenReturn(statusFlow)
        kosmos.screenRecordingServiceInteractor = interactor
        underTest = kosmos.largeScreenRecordingChipViewModel
    }

    @Test
    fun chip_stoppedState_isHidden() =
        testScope.runTest {
            underTest.activateIn(testScope)

            statusFlow.value = Stopped(Stopped.STOP_REASON_NOT_STARTED)
            runCurrent()

            val latest = underTest.chip
            assertThat(latest).isInstanceOf(QuickActionChipModel.Hidden::class.java)
            assertThat(latest.chipId).isEqualTo(QuickActionChipId.ScreenRecording)
        }

    @Test
    fun chip_startingState_isShownAsPopupChipWithCountdown() =
        testScope.runTest {
            underTest.activateIn(testScope)

            statusFlow.value =
                Starting(untilStarted = 3.seconds, parameters = screenRecordingParameters())
            runCurrent()

            val latest = underTest.chip
            assertThat(latest).isInstanceOf(QuickActionChipModel.PopupChip::class.java)
            val popupChip = latest as QuickActionChipModel.PopupChip
            assertThat(popupChip.chipId).isEqualTo(QuickActionChipId.ScreenRecording)
            assertThat(popupChip.chipContent).isEqualTo(ChipContent.Text("3"))
            assertThat(popupChip.icons).isEmpty()
        }

    @Test
    fun chip_startedState_isShownAsPopupChipWithTimer() =
        testScope.runTest {
            underTest.activateIn(testScope)

            systemClock.setElapsedRealtime(1000L)
            statusFlow.value =
                ScreenRecordingStatus.Started(parameters = screenRecordingParameters())
            runCurrent()

            val latest = underTest.chip
            assertThat(latest).isInstanceOf(QuickActionChipModel.PopupChip::class.java)
            val popupChip = latest as QuickActionChipModel.PopupChip
            assertThat(popupChip.chipId).isEqualTo(QuickActionChipId.ScreenRecording)
            assertThat(popupChip.chipContent).isInstanceOf(ChipContent.Timer::class.java)
            val timerContent = popupChip.chipContent as ChipContent.Timer
            assertThat(timerContent.chronometer).isInstanceOf(Chronometer.Running::class.java)
            assertThat((timerContent.chronometer as Chronometer.Running).eventTime)
                .isEqualTo(EventTime.ElapsedRealtime(1000L))
            assertThat(popupChip.icons).isNotEmpty()
        }
}
