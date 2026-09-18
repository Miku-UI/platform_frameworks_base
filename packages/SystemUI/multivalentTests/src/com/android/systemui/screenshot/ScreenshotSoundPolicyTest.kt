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

package com.android.systemui.screenshot

import android.hardware.camera2.CameraManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotSoundPolicyTest : SysuiTestCase() {
    private val cameraManager = mock<CameraManager>()
    private val shutterSoundPolicy = mock<MediaShutterSoundPolicy>()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var cameraAvailabilityCallback: CameraManager.AvailabilityCallback
    private lateinit var screenshotSoundPolicy: ScreenshotSoundPolicy

    @Before
    fun setup() {
        whenever(
                cameraManager.registerAvailabilityCallback(
                    any<Executor>(),
                    any<CameraManager.AvailabilityCallback>(),
                )
            )
            .thenAnswer {
                cameraAvailabilityCallback = it.arguments[1] as CameraManager.AvailabilityCallback
                return@thenAnswer Unit
            }
        screenshotSoundPolicy =
            ScreenshotSoundPolicy(
                cameraManager,
                shutterSoundPolicy,
                context.mainExecutor,
                testDispatcher,
                testScope,
            )
    }

    @Test
    fun cameraOff_shouldNotForce() = runTest {
        shutterSoundPolicy.stub { onBlocking { mustPlayShutterSound() }.doReturn(true) }
        cameraAvailabilityCallback.onCameraOpened("testCameraId", "testPackageId")
        cameraAvailabilityCallback.onCameraClosed("testCameraId")

        assertThat(screenshotSoundPolicy.shouldForceShutterSound()).isFalse()
    }

    @Test
    fun shutterNotForced_shouldNotForce() = runTest {
        shutterSoundPolicy.stub { onBlocking { mustPlayShutterSound() }.doReturn(false) }
        cameraAvailabilityCallback.onCameraOpened("testCameraId", "testPackageId")

        assertThat(screenshotSoundPolicy.shouldForceShutterSound()).isFalse()
    }

    @Test
    fun shutterForcedAndCameraOpen_shouldForce() = runTest {
        shutterSoundPolicy.stub { onBlocking { mustPlayShutterSound() }.doReturn(true) }
        cameraAvailabilityCallback.onCameraOpened("testCameraId", "testPackageId")

        assertThat(screenshotSoundPolicy.shouldForceShutterSound()).isTrue()
    }
}
