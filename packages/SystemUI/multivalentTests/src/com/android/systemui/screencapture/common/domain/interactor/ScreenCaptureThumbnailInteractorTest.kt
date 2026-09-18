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

package com.android.systemui.screencapture.common.domain.interactor

import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureThumbnailRepository
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureDisplay
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screenshot.mockImageCapture
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureThumbnailInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val Kosmos.interactor by
        Kosmos.Fixture {
            ScreenCaptureThumbnailInteractor(
                bgContext = testDispatcher,
                repository = fakeScreenCaptureThumbnailRepository,
                imageCapture = mockImageCapture,
            )
        }

    @Test
    fun loadThumbnail_recentTask_returnsThumbnailFromRepository() =
        kosmos.runTest {
            // Arrange
            val fakeTask =
                ScreenCaptureRecentTask(
                    taskId = 1,
                    displayId = 2,
                    userId = 3,
                    component = null,
                    backgroundColor = null,
                    splitBounds = null,
                    baseIntent = null,
                    isForegroundTask = false,
                )

            // Act
            val result = interactor.loadThumbnail(fakeTask)

            // Assert
            assertThat(fakeScreenCaptureThumbnailRepository.loadThumbnailCalls).containsExactly(1)
            assertThat(result).isEqualTo(fakeScreenCaptureThumbnailRepository.defaultFakeThumbnail)
        }

    @Test
    fun loadThumbnail_display_returnsCapturedThumbnail() =
        kosmos.runTest {
            // Arrange
            val fakeThumbnail = createBitmap(200, 100)
            mockImageCapture.stub {
                on { captureDisplay(any(), anyOrNull()) } doReturn fakeThumbnail
            }
            val fakeDisplay = ScreenCaptureDisplay(displayId = 1, label = "FakeLabel")

            // Act
            val result = interactor.loadThumbnail(fakeDisplay)

            // Assert
            verify(mockImageCapture).captureDisplay(eq(1), isNull())
            assertThat(result.getOrNull()?.sameAs(fakeThumbnail))
        }
}
