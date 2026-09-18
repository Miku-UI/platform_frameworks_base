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
package com.android.systemui.screencapture

import android.content.applicationContext
import android.graphics.drawable.Icon
import android.media.projection.StopReason
import android.platform.test.annotations.EnableFlags
import android.view.Display
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.display.data.repository.display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.plugins.activityStarter
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.data.repository.fakeScreenCaptureDeviceStateRepository
import com.android.systemui.screencapture.domain.interactor.screenCaptureComponentInteractor
import com.android.systemui.screencapture.domain.interactor.screenCaptureTargetDisplayInteractor
import com.android.systemui.screencapture.domain.interactor.screenCaptureTracingInteractor
import com.android.systemui.screencapture.domain.interactor.screenCaptureUiInteractor
import com.android.systemui.screencapture.record.domain.interactor.screenCaptureRecordFeaturesInteractor
import com.android.systemui.screencapture.ui.PostRecordingShelf
import com.android.systemui.screencapture.ui.ScreenCaptureUiDialogFactory
import com.android.systemui.screencapture.ui.postRecordingShelfFactory
import com.android.systemui.screencapture.ui.screenCaptureUiDialogFactory
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
import com.android.systemui.screenrecord.service.fakeScreenRecordingService
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParametersFactory.screenRecordingParameters
import com.android.systemui.shade.data.repository.fakeFocusedDisplayRepository
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureCoreStartableTest : SysuiTestCase() {

    private val kosmos =
        testKosmosNew().apply {
            screenCaptureUiDialogFactory =
                mock<ScreenCaptureUiDialogFactory> {
                    on { create(any(), any()) } doReturn mock<SystemUIDialog>()
                }
            postRecordingShelfFactory =
                mock<PostRecordingShelf.Factory> {
                    on { create(any(), any(), any(), any()) } doReturn mock<PostRecordingShelf>()
                }
        }
    private val displayCaptor = argumentCaptor<Display>()

    private val underTest: ScreenCaptureCoreStartable by lazy {
        with(kosmos) {
            ScreenCaptureCoreStartable(
                appScope = applicationCoroutineScope,
                context = applicationContext,
                screenCaptureComponentInteractor = screenCaptureComponentInteractor,
                screenCaptureUiInteractor = screenCaptureUiInteractor,
                screenCaptureTargetDisplayInteractor = screenCaptureTargetDisplayInteractor,
                screenRecordingServiceInteractor = screenRecordingServiceInteractor,
                postRecordingShelfFactory = postRecordingShelfFactory,
                activityStarter = activityStarter,
                screenCaptureRecordFeaturesInteractor = screenCaptureRecordFeaturesInteractor,
                screenCaptureTracingInteractor = screenCaptureTracingInteractor,
                broadcastDispatcher = broadcastDispatcher,
                screenCaptureUiReceiver = screenCaptureUiReceiver,
            )
        }
    }

    @Test
    fun focusedDisplayExists_itsUsedForTheSmallScreenUi() =
        kosmos.runTest {
            val display1 = display(id = 1)
            val display2 = display(id = 2)
            val display3 = display(id = 3)
            displayRepository.addDisplays(display1, display2, display3)
            fakeFocusedDisplayRepository.setDisplayId(display2.displayId)

            underTest.start()
            screenCaptureUiInteractor.show(ScreenCaptureUiParameters.Record())

            verify(screenCaptureUiDialogFactory).create(displayCaptor.capture(), any())
            assertThat(displayCaptor.lastValue.displayId).isEqualTo(display2.displayId)
        }

    @Test
    fun noFocusedDisplay_someDisplayIsUsedForTheSmallScreenUi() =
        kosmos.runTest {
            val display1 = display(id = 1)
            val display2 = display(id = 2)
            val display3 = display(id = 3)
            displayRepository.removeDisplay(Display.DEFAULT_DISPLAY)
            displayRepository.addDisplays(display1, display2, display3)

            underTest.start()
            screenCaptureUiInteractor.show(ScreenCaptureUiParameters.Record())

            verify(screenCaptureUiDialogFactory).create(displayCaptor.capture(), any())
            assertThat(displayCaptor.lastValue.displayId).isEqualTo(display1.displayId)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE, Flags.FLAG_LARGE_SCREEN_RECORDING)
    fun focusedDisplayExists_itsUsedForTheLargeScreenUi() =
        kosmos.runTest {
            val serviceCallback by collectLastValue(fakeScreenRecordingService.callback)
            fakeScreenCaptureDeviceStateRepository.setLargeScreen(true)
            val display1 = display(id = 1)
            val display2 = display(id = 2)
            val display3 = display(id = 3)
            displayRepository.addDisplays(display1, display2, display3)
            fakeFocusedDisplayRepository.setDisplayId(display2.displayId)

            underTest.start()
            screenRecordingServiceInteractor.startRecording(screenRecordingParameters())
            screenRecordingServiceInteractor.stopRecording(StopReason.STOP_HOST_APP)
            serviceCallback!!.onRecordingSaved(
                "content://test".toUri(),
                Icon.createWithBitmap(createBitmap(1, 1)),
                1,
            )

            verify(postRecordingShelfFactory)
                .create(
                    uri = any(),
                    thumbnail = any(),
                    display = displayCaptor.capture(),
                    notificationId = any(),
                )
            assertThat(displayCaptor.lastValue.displayId).isEqualTo(display2.displayId)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE, Flags.FLAG_LARGE_SCREEN_RECORDING)
    fun noFocusedDisplay_someDisplayIsUsedForTheLargeScreenUi() =
        kosmos.runTest {
            val serviceCallback by collectLastValue(fakeScreenRecordingService.callback)
            fakeScreenCaptureDeviceStateRepository.setLargeScreen(true)
            val display1 = display(id = 1)
            val display2 = display(id = 2)
            val display3 = display(id = 3)
            displayRepository.removeDisplay(Display.DEFAULT_DISPLAY)
            displayRepository.addDisplays(display1, display2, display3)

            underTest.start()
            screenRecordingServiceInteractor.startRecording(screenRecordingParameters())
            screenRecordingServiceInteractor.stopRecording(StopReason.STOP_HOST_APP)
            serviceCallback!!.onRecordingSaved(
                "content://test".toUri(),
                Icon.createWithBitmap(createBitmap(1, 1)),
                1,
            )

            verify(postRecordingShelfFactory)
                .create(
                    uri = any(),
                    thumbnail = any(),
                    display = displayCaptor.capture(),
                    notificationId = any(),
                )
            assertThat(displayCaptor.lastValue.displayId).isEqualTo(display1.displayId)
        }
}
