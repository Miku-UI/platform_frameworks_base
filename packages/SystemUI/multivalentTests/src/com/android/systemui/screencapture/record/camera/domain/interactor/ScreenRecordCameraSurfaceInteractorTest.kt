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

package com.android.systemui.screencapture.record.camera.domain.interactor

import android.util.Size
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.record.camera.data.repository.fakeScreenRecordCameraRepository
import com.android.systemui.screencapture.record.camera.shared.model.CameraState
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

private val optimalCameraStreamSize = Size(1, 1)

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenRecordCameraSurfaceInteractorTest : SysuiTestCase() {

    private val kosmos =
        testKosmosNew().apply {
            fakeScreenRecordCameraRepository.optimalCameraStreamSize = optimalCameraStreamSize
            screenRecordCameraInteractor.onDisplayReady("unique-id", Surface.ROTATION_0)
            backgroundScope.launch { screenRecordCameraInteractor.start() }
        }

    private val surface: Surface = mock {}

    private val underTest: ScreenRecordCameraSurfaceInteractor by lazy {
        kosmos.screenRecordCameraSurfaceInteractor
    }

    @Test
    fun startStream_startsCamera() =
        kosmos.runTest {
            val state by collectLastValue(fakeScreenRecordCameraRepository.state)
            underTest.startStream(surface, optimalCameraStreamSize)

            assertThat(state).isEqualTo(CameraState.Started)
        }

    @Test
    fun stopStream_stopsCamera() =
        kosmos.runTest {
            val state by collectLastValue(fakeScreenRecordCameraRepository.state)
            underTest.startStream(surface, optimalCameraStreamSize)

            assertThat(state).isEqualTo(CameraState.Started)

            underTest.stopStream()

            assertThat(state).isEqualTo(CameraState.Stopped)
        }

    @Test
    fun updateStream_restartsCamera() =
        kosmos.runTest {
            val state by collectLastValue(fakeScreenRecordCameraRepository.state)
            underTest.startStream(surface, optimalCameraStreamSize)
            assertThat(state).isEqualTo(CameraState.Started)

            val newSurface: Surface = mock {}
            underTest.startStream(newSurface, optimalCameraStreamSize)

            assertThat(state).isEqualTo(CameraState.Started)
        }

    @Test
    fun release_stopsCamera() =
        kosmos.runTest {
            val state by collectLastValue(fakeScreenRecordCameraRepository.state)
            underTest.startStream(surface, optimalCameraStreamSize)
            assertThat(state).isEqualTo(CameraState.Started)

            underTest.release()

            assertThat(state).isEqualTo(CameraState.Stopped)
        }
}

private fun ScreenRecordCameraSurfaceInteractor.startStream(surface: Surface, size: Size) =
    startStream(surface, size.width, size.height)
