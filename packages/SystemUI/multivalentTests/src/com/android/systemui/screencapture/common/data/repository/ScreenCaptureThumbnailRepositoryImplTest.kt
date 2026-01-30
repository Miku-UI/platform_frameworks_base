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

package com.android.systemui.screencapture.common.data.repository

import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.activityManagerWrapper
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureThumbnailRepositoryImplTest : SysuiTestCase() {

    private val fakeThumbnail = createBitmap(100, 100)

    private val kosmos = testKosmosNew()

    @Test
    fun loadThumbnail_returnsNewThumbnail() =
        kosmos.runTest {
            // Arrange
            val thumbnailRepository =
                ScreenCaptureThumbnailRepositoryImpl(
                    bgContext = testDispatcher,
                    activityManager =
                        activityManagerWrapper.stub {
                            on { takeTaskThumbnail(any()) } doReturn
                                ThumbnailData(thumbnail = fakeThumbnail)
                        },
                )

            // Act
            val result = thumbnailRepository.loadThumbnail(123)

            // Assert
            verify(activityManagerWrapper).takeTaskThumbnail(eq(123))
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.sameAs(fakeThumbnail)).isTrue()
        }

    @Test
    fun loadThumbnail_failsToTakeThumbnail_fallsBackToCachedThumbnail() =
        kosmos.runTest {
            // Arrange
            val thumbnailRepository =
                ScreenCaptureThumbnailRepositoryImpl(
                    bgContext = testDispatcher,
                    activityManager =
                        activityManagerWrapper.stub {
                            on { takeTaskThumbnail(any()) } doReturn ThumbnailData(thumbnail = null)
                            on { getTaskThumbnail(any(), any()) } doReturn
                                ThumbnailData(thumbnail = fakeThumbnail)
                        },
                )

            // Act
            val result = thumbnailRepository.loadThumbnail(123)

            // Assert
            verify(activityManagerWrapper).takeTaskThumbnail(eq(123))
            verify(activityManagerWrapper).getTaskThumbnail(eq(123), eq(false))
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.sameAs(fakeThumbnail)).isTrue()
        }

    @Test
    fun loadThumbnail_failsToTakeThumbnailAndNoCache_returnsFailure() =
        kosmos.runTest {
            // Arrange
            val thumbnailRepository =
                ScreenCaptureThumbnailRepositoryImpl(
                    bgContext = testDispatcher,
                    activityManager =
                        activityManagerWrapper.stub {
                            on { takeTaskThumbnail(any()) } doReturn ThumbnailData(thumbnail = null)
                            on { getTaskThumbnail(any(), any()) } doReturn
                                ThumbnailData(thumbnail = null)
                        },
                )

            // Act
            val result = thumbnailRepository.loadThumbnail(123)

            // Assert
            verify(activityManagerWrapper).takeTaskThumbnail(eq(123))
            verify(activityManagerWrapper).getTaskThumbnail(eq(123), eq(false))
            assertThat(result.isFailure).isTrue()
        }
}
