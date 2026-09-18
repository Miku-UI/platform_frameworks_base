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

import android.media.projection.MediaProjectionAppContent
import android.os.UserHandle
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureAppContentRepository
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureAppContent
import com.android.systemui.screencapture.common.repository.FakeAppContentProjectionCallback
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import java.lang.ref.WeakReference
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureAppContentInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val fakeUserHandle = UserHandle.of(123)
    private val fakeMediaProjectionAppContent1 =
        MediaProjectionAppContent.Builder(456)
            .setTitle("FakeTitle1")
            .setThumbnail(createBitmap(100, 100))
            .build()
    private val fakeMediaProjectionAppContent2 =
        MediaProjectionAppContent.Builder(789)
            .setTitle("FakeTitle2")
            .setThumbnail(createBitmap(200, 200))
            .build()
    private val fakeThrowable = IllegalStateException("FakeMessage")

    @Test
    fun appContentsFor_singlePackage_propagatesSuccess() =
        kosmos.runTest {
            // Arrange
            val interactor =
                ScreenCaptureAppContentInteractor(
                    repository = fakeScreenCaptureAppContentRepository,
                    parameters =
                        ScreenCaptureUiParameters.ShareScreen(hostAppUserHandle = fakeUserHandle),
                )
            var result: Result<SingleAppContent>? = null

            // Act
            val appContents = interactor.appContentsFor("FakePackage", 200, 150, 50)
            val job = testScope.launch { appContents.collect { result = it } }
            assertThat(result).isNull()
            val fakeCallback = FakeAppContentProjectionCallback(context)
            fakeScreenCaptureAppContentRepository.setAppContentSuccess(
                packageName = "FakePackage",
                user = fakeUserHandle,
                listOf(fakeMediaProjectionAppContent1),
                WeakReference(fakeCallback),
            )

            // Assert
            assertThat(fakeScreenCaptureAppContentRepository.appContentsForCalls).hasSize(1)
            with(fakeScreenCaptureAppContentRepository.appContentsForCalls.last()) {
                assertThat(packageName).isEqualTo("FakePackage")
                assertThat(user).isEqualTo(fakeUserHandle)
                assertThat(thumbnailWidthPx).isEqualTo(200)
                assertThat(thumbnailHeightPx).isEqualTo(150)
                assertThat(iconSizePx).isEqualTo(50)
            }
            assertThat(result?.isSuccess).isTrue()
            val (appContentList, callback) = result?.getOrNull()!!
            assertThat(appContentList)
                .containsExactly(
                    ScreenCaptureAppContent("FakePackage", fakeMediaProjectionAppContent1)
                )
            assertThat(callback).isNotNull()

            // Cleanup
            job.cancel()
        }

    @Test
    fun appContentsFor_singlePackage_propagatesFailure() =
        kosmos.runTest {
            // Arrange
            val interactor =
                ScreenCaptureAppContentInteractor(
                    repository = fakeScreenCaptureAppContentRepository,
                    parameters =
                        ScreenCaptureUiParameters.ShareScreen(hostAppUserHandle = fakeUserHandle),
                )
            var result: Result<SingleAppContent>? = null

            // Act
            val appContents = interactor.appContentsFor("FakePackage", 200, 150, 50)
            val job = testScope.launch { appContents.collect { result = it } }
            assertThat(result).isNull()
            fakeScreenCaptureAppContentRepository.setAppContentFailure(
                packageName = "FakePackage",
                user = fakeUserHandle,
                fakeThrowable,
            )

            // Assert
            assertThat(fakeScreenCaptureAppContentRepository.appContentsForCalls).hasSize(1)
            with(fakeScreenCaptureAppContentRepository.appContentsForCalls.last()) {
                assertThat(packageName).isEqualTo("FakePackage")
                assertThat(user).isEqualTo(fakeUserHandle)
                assertThat(thumbnailWidthPx).isEqualTo(200)
                assertThat(thumbnailHeightPx).isEqualTo(150)
                assertThat(iconSizePx).isEqualTo(50)
            }
            assertThat(result?.isFailure).isTrue()
            assertThat(result?.exceptionOrNull()).isSameInstanceAs(fakeThrowable)

            // Cleanup
            job.cancel()
        }
}
