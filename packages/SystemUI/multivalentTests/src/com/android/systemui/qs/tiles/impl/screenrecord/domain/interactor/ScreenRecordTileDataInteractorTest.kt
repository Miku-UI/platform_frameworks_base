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

package com.android.systemui.qs.tiles.impl.screenrecord.domain.interactor

import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.qs.tiles.base.domain.model.DataUpdateTrigger
import com.android.systemui.qs.tiles.impl.screenrecord.domain.model.ScreenRecordTileModel
import com.android.systemui.screencapture.data.repository.fakeScreenCaptureDeviceStateRepository
import com.android.systemui.screencapture.record.domain.interactor.screenCaptureRecordFeaturesInteractor
import com.android.systemui.screenrecord.data.model.ScreenRecordModel
import com.android.systemui.screenrecord.data.repository.screenRecordingServiceRepository
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParametersFactory
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenRecordTileDataInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val screenRecordRepo = kosmos.screenRecordingServiceRepository
    private val underTest: ScreenRecordTileDataInteractor =
        ScreenRecordTileDataInteractor(
            screenRecordRepo,
            kosmos.screenCaptureRecordFeaturesInteractor,
        )

    @Test
    fun isAvailable_returnsTrue() =
        kosmos.runTest {
            val availability by collectLastValue(underTest.availability(TEST_USER))

            assertThat(availability).isTrue()
        }

    @Test
    fun dataMatchesRepo() =
        kosmos.runTest {
            val isRecording = ScreenRecordTileModel(ScreenRecordModel.Recording)
            val isDoingNothing = ScreenRecordTileModel(ScreenRecordModel.DoingNothing)
            val isStartingIn1 = ScreenRecordTileModel(ScreenRecordModel.Starting(1))
            val lastModel by
                collectLastValue(
                    underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest))
                )

            assertThat(lastModel).isEqualTo(isDoingNothing)

            screenRecordRepo.startRecordingDelayed(defaultParams, 1.milliseconds)
            assertThat(lastModel).isEqualTo(isStartingIn1)

            screenRecordRepo.stopRecording(ScreenRecordingStatus.Stopped.STOP_REASON_NOT_STARTED)
            assertThat(lastModel).isEqualTo(isDoingNothing)

            screenRecordRepo.startRecording(defaultParams)
            assertThat(lastModel).isEqualTo(isRecording)

            screenRecordRepo.stopRecording(ScreenRecordingStatus.Stopped.STOP_REASON_NOT_STARTED)
            assertThat(lastModel).isEqualTo(isDoingNothing)
        }

    @Test
    fun dataMatchesRepo_largeScreenRecordingEnabled() =
        kosmos.runTest {
            kosmos.fakeScreenCaptureDeviceStateRepository.setLargeScreen(true)
            val lastModel by
                collectLastValue(
                    underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest))
                )

            assertThat(lastModel?.isLargeScreenRecordingEnabled).isTrue()
        }

    private companion object {
        val TEST_USER = UserHandle.of(1)!!
        val defaultParams = ScreenRecordingParametersFactory.screenRecordingParameters()
    }
}
