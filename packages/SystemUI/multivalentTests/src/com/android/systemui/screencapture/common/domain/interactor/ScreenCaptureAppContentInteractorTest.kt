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
import com.android.systemui.screencapture.common.shared.model.castScreenCaptureUiParameters
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureAppContentInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val fakeUserHandle = UserHandle.of(123)
    private val fakeMediaProjectionAppContent1 =
        MediaProjectionAppContent(
            /* thumbnail= */ createBitmap(100, 100),
            /* title= */ "FakeTitle1",
            /* id= */ 456,
        )
    private val fakeMediaProjectionAppContent2 =
        MediaProjectionAppContent(
            /* thumbnail= */ createBitmap(200, 200),
            /* title= */ "FakeTitle2",
            /* id= */ 789,
        )
    private val fakeThrowable = IllegalStateException("FakeMessage")

    @Test
    fun appContentsFor_singlePackage_propagatesSuccess() =
        kosmos.runTest {
            // Arrange
            val interactor =
                ScreenCaptureAppContentInteractor(
                    repository = fakeScreenCaptureAppContentRepository,
                    parameters =
                        castScreenCaptureUiParameters.copy(hostAppUserHandle = fakeUserHandle),
                )
            var result: Result<List<ScreenCaptureAppContent>>? = null

            // Act
            val appContents = interactor.appContentsFor("FakePackage", 200, 150)
            val job = testScope.launch { appContents.collect { result = it } }
            assertThat(result).isNull()
            fakeScreenCaptureAppContentRepository.setAppContentSuccess(
                packageName = "FakePackage",
                user = fakeUserHandle,
                fakeMediaProjectionAppContent1,
            )

            // Assert
            assertThat(fakeScreenCaptureAppContentRepository.appContentsForCalls).hasSize(1)
            with(fakeScreenCaptureAppContentRepository.appContentsForCalls.last()) {
                assertThat(packageName).isEqualTo("FakePackage")
                assertThat(user).isEqualTo(fakeUserHandle)
                assertThat(thumbnailWidthPx).isEqualTo(200)
                assertThat(thumbnailHeightPx).isEqualTo(150)
            }
            assertThat(result?.isSuccess).isTrue()
            assertThat(result?.getOrNull())
                .containsExactly(
                    ScreenCaptureAppContent("FakePackage", fakeMediaProjectionAppContent1)
                )

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
                        castScreenCaptureUiParameters.copy(hostAppUserHandle = fakeUserHandle),
                )
            var result: Result<List<ScreenCaptureAppContent>>? = null

            // Act
            val appContents = interactor.appContentsFor("FakePackage", 200, 150)
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
            }
            assertThat(result?.isFailure).isTrue()
            assertThat(result?.exceptionOrNull()).isSameInstanceAs(fakeThrowable)

            // Cleanup
            job.cancel()
        }

    @Test
    fun appContentsFor_multiplePackages_propagatesOnlySuccessfulFetches() =
        kosmos.runTest {
            // Arrange
            val interactor =
                ScreenCaptureAppContentInteractor(
                    repository = fakeScreenCaptureAppContentRepository,
                    parameters =
                        castScreenCaptureUiParameters.copy(hostAppUserHandle = fakeUserHandle),
                )
            var result: List<ScreenCaptureAppContent>? = null

            // Act
            val appContents =
                interactor.appContentsFor(
                    listOf("FakePackage1", "FakePackage2", "FakePackage3"),
                    200,
                    150,
                )
            val job = testScope.launch { appContents.collect { result = it } }
            assertThat(result).isNull()
            with(fakeScreenCaptureAppContentRepository) {
                setAppContentSuccess(
                    packageName = "FakePackage1",
                    user = fakeUserHandle,
                    fakeMediaProjectionAppContent1,
                )
                setAppContentFailure(
                    packageName = "FakePackage2",
                    user = fakeUserHandle,
                    throwable = fakeThrowable,
                )
                setAppContentSuccess(
                    packageName = "FakePackage3",
                    user = fakeUserHandle,
                    fakeMediaProjectionAppContent2,
                )
            }

            // Assert
            assertThat(fakeScreenCaptureAppContentRepository.appContentsForCalls).hasSize(3)
            fakeScreenCaptureAppContentRepository.appContentsForCalls.forEachIndexed { index, call
                ->
                with(call) {
                    assertThat(packageName).isEqualTo("FakePackage${index + 1}")
                    assertThat(user).isEqualTo(fakeUserHandle)
                    assertThat(thumbnailWidthPx).isEqualTo(200)
                    assertThat(thumbnailHeightPx).isEqualTo(150)
                }
            }
            assertThat(result)
                .containsExactly(
                    ScreenCaptureAppContent("FakePackage1", fakeMediaProjectionAppContent1),
                    ScreenCaptureAppContent("FakePackage3", fakeMediaProjectionAppContent2),
                )

            // Cleanup
            job.cancel()
        }
}
