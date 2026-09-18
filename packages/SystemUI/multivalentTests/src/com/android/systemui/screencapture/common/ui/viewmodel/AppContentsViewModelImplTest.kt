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
import android.media.projection.MediaProjectionAppContent
import android.os.UserHandle
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
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureAppContentRepository
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureAppContentInteractor
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureAppContent
import com.android.systemui.screencapture.common.repository.FakeAppContentProjectionCallback
import com.android.systemui.testKosmosNew
import com.android.users.UserType
import com.google.common.truth.Truth.assertThat
import java.lang.ref.WeakReference
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AppContentsViewModelImplTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val Kosmos.viewModel by
        Kosmos.Fixture {
            AppContentsViewModelImpl(
                appContentInteractor = screenCaptureAppContentInteractor,
                appContentViewModelFactory = appContentViewModelFactory,
                drawableLoaderViewModel = drawableLoaderViewModel,
                audioSwitchViewModel = audioSwitchViewModel,
                hostPackageName = "FakeBasePackage",
                thumbnailWidthPx = 200,
                thumbnailHeightPx = 100,
                iconSizePx = 50,
            )
        }

    private val Kosmos.fakeThumbnail by Kosmos.Fixture { createBitmap(200, 100) }
    private val Kosmos.fakeRecentTask by
        Kosmos.Fixture {
            RecentTask(
                taskId = 1,
                displayId = 2,
                userId = 3,
                topActivityComponent = ComponentName("FakeTopPackage", "FakeTopClass"),
                baseIntentComponent = ComponentName("FakeBasePackage", "FakeBaseClass"),
                baseIntent = null,
                colorBackground = 0x12345699,
                isForegroundTask = true,
                userType = UserType.MAIN,
                splitBounds = null,
            )
        }
    private val Kosmos.fakeMediaProjectionAppContent by
        Kosmos.Fixture {
            MediaProjectionAppContent.Builder(123)
                .setThumbnail(fakeThumbnail)
                .setTitle("FakeLabel")
                .build()
        }
    private val Kosmos.fakeAppContent by
        Kosmos.Fixture { ScreenCaptureAppContent("FakeBasePackage", fakeMediaProjectionAppContent) }
    private val Kosmos.fakeAppContentViewModel by
        Kosmos.Fixture { appContentViewModelFactory.create(fakeAppContent) }

    @Test
    fun targets_returnsAppContentsFromRepository_andSetsCallbacks() =
        kosmos.runTest {
            // Arrange
            val fakeCallback = FakeAppContentProjectionCallback(context)
            viewModel.activateIn(testScope)

            // Act
            val result = viewModel.targets
            fakeScreenCaptureAppContentRepository.setAppContentSuccess(
                packageName = "FakeBasePackage",
                user = UserHandle.CURRENT,
                listOf(fakeMediaProjectionAppContent),
                WeakReference(fakeCallback),
            )

            // Assert
            assertThat(result.value).containsExactly(fakeAppContent)
            viewModel.setSelectedTarget(fakeAppContentViewModel)
            assertThat(viewModel.projectionCallback.value?.get()).isEqualTo(fakeCallback)
        }

    @Test
    fun selectedTarget_returnsSelectedTarget() =
        kosmos.runTest {
            // Arrange
            viewModel.activateIn(testScope)
            val result by viewModel.selectedTarget
            assertThat(result).isNull()

            // Act
            viewModel.setSelectedTarget(fakeAppContentViewModel)

            // Assert
            assertThat(result).isSameInstanceAs(fakeAppContentViewModel)
        }

    @Test
    fun createViewModelFor_returnsViewModelForRecentTask() =
        kosmos.runTest {
            // Arrange
            viewModel.activateIn(testScope)

            // Act
            val result =
                viewModel.createViewModelFor(fakeAppContent).apply { activateIn(testScope) }

            // Assert
            with(result) {
                assertThat(model).isSameInstanceAs(fakeAppContent)
                assertThat(icon?.isFailure).isTrue()
                assertThat(label?.getOrNull()).isEqualTo("FakeLabel")
                assertThat(thumbnail?.getOrNull()?.sameAs(fakeThumbnail)).isTrue()
                assertThat(backgroundColorOpaque).isEqualTo(Color.Black)
            }
        }

    @Test
    fun projectionCallback_returnsCallbackForSelectedTarget() =
        kosmos.runTest {
            // Arrange
            val fakeCallback = FakeAppContentProjectionCallback(context)
            val appContent =
                ScreenCaptureAppContent("FakeBasePackage", fakeMediaProjectionAppContent)
            val appContentViewModel = appContentViewModelFactory.create(appContent)

            viewModel.activateIn(testScope)

            // 1. Initial state
            assertThat(viewModel.projectionCallback.value?.get()).isNull()

            // 2. Populate the targets
            fakeScreenCaptureAppContentRepository.setAppContentSuccess(
                packageName = "FakeBasePackage",
                user = UserHandle.CURRENT,
                listOf(fakeMediaProjectionAppContent),
                WeakReference(fakeCallback),
            )

            // 3. Select the target
            viewModel.setSelectedTarget(appContentViewModel)
            assertThat(viewModel.projectionCallback.value?.get()).isEqualTo(fakeCallback)

            // 4. Deselect the target
            viewModel.setSelectedTarget(null)
            assertThat(viewModel.projectionCallback.value?.get()).isNull()
        }
}
