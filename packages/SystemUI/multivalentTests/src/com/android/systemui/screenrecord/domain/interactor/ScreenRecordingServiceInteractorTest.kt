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

package com.android.systemui.screenrecord.domain.interactor

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.mockedContext
import android.media.projection.StopReason
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.domain.ScreenRecordingParameters
import com.android.systemui.screenrecord.screenRecordUxController
import com.android.systemui.screenrecord.service.FakeScreenRecordingService
import com.android.systemui.screenrecord.service.FakeScreenRecordingServiceCallbackWrapper
import com.android.systemui.screenrecord.service.callbackStatus
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.userRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

private val componentName = ComponentName("com.android.systemui", "test")

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenRecordingServiceInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val service = FakeScreenRecordingService()

    private var serviceConnection: ServiceConnection? = null

    private val underTest: ScreenRecordingServiceInteractor by lazy {
        with(kosmos) {
            ScreenRecordingServiceInteractor(
                mockedContext,
                applicationCoroutineScope,
                userRepository,
                screenRecordUxController,
            )
        }
    }

    @Before
    fun setUp() {
        with(kosmos) {
            whenever(mockedContext.createContextAsUser(any(), any())).thenReturn(mockedContext)
            whenever(mockedContext.bindService(any<Intent>(), any<ServiceConnection>(), anyInt()))
                .then {
                    serviceConnection =
                        (it.arguments[1] as ServiceConnection).apply {
                            onServiceConnected(componentName, service)
                        }
                    true
                }
        }
    }

    @Test
    fun testStartRecording_startsRecording() =
        kosmos.runTest {
            val interactorStatus: Status? by collectLastValue(underTest.status)
            val serviceStatus: Status? by collectLastValue(service.status)
            val callbackStatus: FakeScreenRecordingServiceCallbackWrapper.RecordingStatus? by
                collectLastValue(service.callbackStatus)

            underTest.startRecording()

            assertThat(interactorStatus).isInstanceOf(Status.Started::class.java)
            assertThat(serviceStatus).isInstanceOf(Status.Started::class.java)
            assertThat(callbackStatus)
                .isInstanceOf(
                    FakeScreenRecordingServiceCallbackWrapper.RecordingStatus.Started::class.java
                )
            assertThat(service.currentCallback).isNotNull()
        }

    @Test
    fun testStopRecording_stopsRecording() =
        kosmos.runTest {
            val interactorStatus: Status? by collectLastValue(underTest.status)
            val serviceStatus: Status? by collectLastValue(service.status)
            val callbackStatus: FakeScreenRecordingServiceCallbackWrapper.RecordingStatus? by
                collectLastValue(service.callbackStatus)
            underTest.startRecording()

            underTest.stopRecording(StopReason.STOP_HOST_APP)

            assertThat(interactorStatus).isEqualTo(Status.Stopped(StopReason.STOP_HOST_APP))
            assertThat(serviceStatus).isEqualTo(Status.Stopped(StopReason.STOP_HOST_APP))
            assertThat(callbackStatus)
                .isInstanceOf(
                    FakeScreenRecordingServiceCallbackWrapper.RecordingStatus.Interrupted::class
                        .java
                )
            assertThat(service.currentCallback).isNull()
        }
}

private fun ScreenRecordingServiceInteractor.startRecording() {
    startRecording(
        ScreenRecordingParameters(
            captureTarget = null,
            audioSource = ScreenRecordingAudioSource.NONE,
            displayId = 0,
            shouldShowTaps = false,
        )
    )
}
