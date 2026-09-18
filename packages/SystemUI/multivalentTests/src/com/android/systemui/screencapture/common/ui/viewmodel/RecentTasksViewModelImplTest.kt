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
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.mediaprojection.appselector.data.RecentTask
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureIconRepository
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureLabelRepository
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureRecentTaskRepository
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureThumbnailRepository
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureRecentTaskInteractor
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.testKosmosNew
import com.android.users.UserType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RecentTasksViewModelImplTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val Kosmos.viewModel by
        Kosmos.Fixture {
            RecentTasksViewModelImpl(
                interactor = screenCaptureRecentTaskInteractor,
                recentTaskViewModelFactory = recentTaskViewModelFactory,
                drawableLoaderViewModel = drawableLoaderViewModel,
                audioSwitchViewModel = audioSwitchViewModel,
            )
        }
    private val Kosmos.fakeSystemRecentTask by
        Kosmos.Fixture {
            RecentTask(
                taskId = 1,
                displayId = 2,
                userId = 3,
                topActivityComponent = ComponentName("FakeTopPackage", "FakeTopClass"),
                baseIntentComponent = ComponentName("FakeBasePackage", "FakeBaseClass"),
                baseIntent = null,
                colorBackground = 0x99123456.toInt(),
                isForegroundTask = true,
                userType = UserType.MAIN,
                splitBounds = null,
            )
        }
    private val Kosmos.fakeRecentTask by
        Kosmos.Fixture { ScreenCaptureRecentTask(fakeSystemRecentTask) }
    private val Kosmos.fakeRecentTaskViewModel by
        Kosmos.Fixture { recentTaskViewModelFactory.create(fakeRecentTask) }
    private val Kosmos.fakeIcon by Kosmos.Fixture { createBitmap(100, 100) }
    private val Kosmos.fakeThumbnail by Kosmos.Fixture { createBitmap(200, 200) }

    @Test
    fun targets_returnsRecentTasksFromRepository() =
        kosmos.runTest {
            // Arrange
            viewModel.activateIn(testScope)

            // Act
            val result = viewModel.targets
            fakeScreenCaptureRecentTaskRepository.setRecentTasks(fakeSystemRecentTask)

            // Assert
            assertThat(result.value).containsExactly(fakeRecentTask)
        }

    @Test
    fun selectedTarget_returnsSelectedTarget() =
        kosmos.runTest {
            // Arrange
            viewModel.activateIn(testScope)
            val result by viewModel.selectedTarget
            assertThat(result).isNull()

            // Act
            viewModel.setSelectedTarget(fakeRecentTaskViewModel)

            // Assert
            assertThat(result).isSameInstanceAs(fakeRecentTaskViewModel)
        }

    @Test
    fun createViewModelFor_returnsViewModelForRecentTask() =
        kosmos.runTest {
            // Arrange
            viewModel.activateIn(testScope)
            fakeScreenCaptureIconRepository.fakeIcon = Result.success(fakeIcon)
            fakeScreenCaptureLabelRepository.fakeLabel = Result.success("FakeLabel")
            fakeScreenCaptureThumbnailRepository.defaultFakeThumbnail =
                Result.success(fakeThumbnail)

            // Act
            val result =
                viewModel.createViewModelFor(fakeRecentTask).apply { activateIn(testScope) }

            // Assert
            with(result) {
                assertThat(model).isSameInstanceAs(fakeRecentTask)
                assertThat(icon?.getOrNull()?.sameAs(fakeIcon)).isTrue()
                assertThat(label?.getOrNull()).isEqualTo("FakeLabel")
                assertThat(thumbnail?.getOrNull()?.sameAs(fakeThumbnail)).isTrue()
                assertThat(backgroundColorOpaque).isEqualTo(Color(0xFF123456))
            }
        }
}
