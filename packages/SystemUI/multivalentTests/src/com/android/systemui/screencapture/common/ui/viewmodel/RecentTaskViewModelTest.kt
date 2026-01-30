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

package com.android.systemui.screencapture.common.ui.viewmodel

import android.content.ComponentName
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureIconRepository
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureLabelRepository
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureThumbnailRepository
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureIconInteractor
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureLabelInteractor
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureThumbnailInteractor
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RecentTaskViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    val fakeComponent = ComponentName("FakePackage", "FakeClass")

    @Test
    fun constructor_initializesFields() =
        kosmos.runTest {
            // Arrange
            val fakeTask =
                ScreenCaptureRecentTask(
                    taskId = 1,
                    displayId = 2,
                    userId = 3,
                    component = fakeComponent,
                    backgroundColor = 0x99123456.toInt(),
                    splitBounds = null,
                )

            // Act
            val viewModel =
                RecentTaskViewModel(
                    task = fakeTask,
                    iconInteractor = screenCaptureIconInteractor,
                    labelInteractor = screenCaptureLabelInteractor,
                    thumbnailInteractor = screenCaptureThumbnailInteractor,
                )

            // Assert
            with(viewModel) {
                assertThat(task).isEqualTo(fakeTask)
                assertThat(icon).isNull()
                assertThat(label).isNull()
                assertThat(thumbnail).isNull()
                assertThat(backgroundColorOpaque).isEqualTo(Color(0xFF123456))
            }
        }

    @Test
    fun constructor_noBackgroundColor_initializesBackgroundColorOpaqueToBlack() =
        kosmos.runTest {
            // Arrange
            val fakeTask =
                ScreenCaptureRecentTask(
                    taskId = 1,
                    displayId = 2,
                    userId = 3,
                    component = fakeComponent,
                    backgroundColor = null,
                    splitBounds = null,
                )

            // Act
            val viewModel =
                RecentTaskViewModel(
                    task = fakeTask,
                    iconInteractor = screenCaptureIconInteractor,
                    labelInteractor = screenCaptureLabelInteractor,
                    thumbnailInteractor = screenCaptureThumbnailInteractor,
                )

            // Assert
            with(viewModel) {
                assertThat(task).isEqualTo(fakeTask)
                assertThat(icon).isNull()
                assertThat(label).isNull()
                assertThat(thumbnail).isNull()
                assertThat(backgroundColorOpaque).isEqualTo(Color.Black)
            }
        }

    @Test
    fun onActivated_loadsResources() =
        kosmos.runTest {
            // Arrange
            val fakeTask =
                ScreenCaptureRecentTask(
                    taskId = 1,
                    displayId = 2,
                    userId = 3,
                    component = fakeComponent,
                    backgroundColor = 0x99123456.toInt(),
                    splitBounds = null,
                )
            val viewModel =
                RecentTaskViewModel(
                    task = fakeTask,
                    iconInteractor = screenCaptureIconInteractor,
                    labelInteractor = screenCaptureLabelInteractor,
                    thumbnailInteractor = screenCaptureThumbnailInteractor,
                )
            with(viewModel) {
                assertThat(task).isEqualTo(fakeTask)
                assertThat(icon).isNull()
                assertThat(label).isNull()
                assertThat(thumbnail).isNull()
                assertThat(backgroundColorOpaque).isEqualTo(Color(0xFF123456))
            }

            // Act
            val job = testScope.launch { viewModel.activate() }

            // Assert
            with(viewModel) {
                assertThat(task).isEqualTo(fakeTask)
                assertThat(icon).isEqualTo(fakeScreenCaptureIconRepository.fakeIcon)
                assertThat(label).isEqualTo(fakeScreenCaptureLabelRepository.fakeLabel)
                assertThat(thumbnail).isEqualTo(fakeScreenCaptureThumbnailRepository.fakeThumbnail)
                assertThat(backgroundColorOpaque).isEqualTo(Color(0xFF123456))
            }

            // Cleanup
            job.cancel()
        }
}
