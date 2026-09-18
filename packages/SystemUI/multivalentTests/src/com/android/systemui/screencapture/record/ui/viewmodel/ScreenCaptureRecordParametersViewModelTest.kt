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

package com.android.systemui.screencapture.record.ui.viewmodel

import android.media.projection.StopReason
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.record.shared.model.ScreenRecordEvent
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParametersFactory
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureRecordParametersViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val underTest by lazy { kosmos.screenCaptureRecordParametersViewModelFactory.create() }

    @Test
    fun shouldShowTaps_preRecording_logsEvents() =
        kosmos.runTest {
            screenRecordingServiceInteractor.stopRecording(StopReason.STOP_HOST_APP)

            underTest.shouldShowTaps = true
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_SHOW_TOUCHES_ENABLED_PRE_RECORDING.id)

            underTest.shouldShowTaps = false
            assertThat(uiEventLoggerFake.eventId(1))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_SHOW_TOUCHES_DISABLED_PRE_RECORDING.id)
        }

    @Test
    fun shouldShowTaps_midRecording_logsEvents() =
        kosmos.runTest {
            screenRecordingServiceInteractor.startRecording(
                ScreenRecordingParametersFactory.screenRecordingParameters()
            )

            underTest.shouldShowTaps = true
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_SHOW_TOUCHES_ENABLED_MID_RECORDING.id)

            underTest.shouldShowTaps = false
            assertThat(uiEventLoggerFake.eventId(1))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_SHOW_TOUCHES_DISABLED_MID_RECORDING.id)
        }

    @Test
    fun shouldShowFrontCamera_preRecording_logsEvents() =
        kosmos.runTest {
            screenRecordingServiceInteractor.stopRecording(StopReason.STOP_HOST_APP)

            underTest.shouldShowFrontCamera = true
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_CAMERA_ENABLED_PRE_RECORDING.id)

            underTest.shouldShowFrontCamera = false
            assertThat(uiEventLoggerFake.eventId(1))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_CAMERA_DISABLED_PRE_RECORDING.id)
        }

    @Test
    fun shouldShowFrontCamera_midRecording_logsEvents() =
        kosmos.runTest {
            screenRecordingServiceInteractor.startRecording(
                ScreenRecordingParametersFactory.screenRecordingParameters()
            )

            underTest.shouldShowFrontCamera = true
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_CAMERA_ENABLED_MID_RECORDING.id)

            underTest.shouldShowFrontCamera = false
            assertThat(uiEventLoggerFake.eventId(1))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_CAMERA_DISABLED_MID_RECORDING.id)
        }

    @Test
    fun audioSource_device_logsEvents() =
        kosmos.runTest {
            underTest.shouldRecordDevice = true
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_AUDIO_DEVICE_ENABLED.id)

            underTest.shouldRecordDevice = false
            assertThat(uiEventLoggerFake.eventId(1))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_AUDIO_DEVICE_DISABLED.id)
        }

    @Test
    fun audioSource_mic_logsEvents() =
        kosmos.runTest {
            underTest.shouldRecordMicrophone = true
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_AUDIO_MIC_ENABLED.id)

            underTest.shouldRecordMicrophone = false
            assertThat(uiEventLoggerFake.eventId(1))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_AUDIO_MIC_DISABLED.id)
        }
}
