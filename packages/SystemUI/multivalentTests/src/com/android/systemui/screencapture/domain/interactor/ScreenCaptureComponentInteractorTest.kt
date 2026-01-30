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

package com.android.systemui.screencapture.domain.interactor

import android.media.projection.StopReason
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.recordScreenCaptureUiParameters
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.domain.ScreenRecordingParameters
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureComponentInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val underTest by lazy { kosmos.screenCaptureComponentInteractor }

    @Test
    fun testRecordComponentLifecycle() =
        kosmos.runTest {
            testComponentLifecycle(
                screenCaptureType = ScreenCaptureType.RECORD,
                parameters = recordScreenCaptureUiParameters,
                startCapture = {
                    screenRecordingServiceInteractor.startRecording(
                        ScreenRecordingParameters(
                            captureTarget = null,
                            audioSource = ScreenRecordingAudioSource.NONE,
                            displayId = Display.DEFAULT_DISPLAY,
                            shouldShowTaps = false,
                        )
                    )
                },
                stopCapture = {
                    screenRecordingServiceInteractor.stopRecording(StopReason.STOP_HOST_APP)
                },
            )
        }

    private fun Kosmos.testComponentLifecycle(
        screenCaptureType: ScreenCaptureType,
        parameters: ScreenCaptureUiParameters,
        startCapture: () -> Unit,
        stopCapture: () -> Unit,
    ) {
        backgroundScope.launch { underTest.initialize() }
        val component by collectLastValue(underTest.screenCaptureComponent(screenCaptureType))
        assertThat(component).isNull()

        screenCaptureUiInteractor.show(parameters)
        assertThat(component).isNotNull()

        screenCaptureUiInteractor.hide(screenCaptureType)
        assertThat(component).isNull()

        screenCaptureUiInteractor.show(parameters)
        startCapture()
        screenCaptureUiInteractor.hide(screenCaptureType)
        val capturingComponent = component
        assertThat(capturingComponent).isNotNull()

        screenCaptureUiInteractor.show(parameters)
        assertThat(component).isSameInstanceAs(capturingComponent)

        screenCaptureUiInteractor.hide(screenCaptureType)
        stopCapture()
        assertThat(component).isNull()
    }
}
