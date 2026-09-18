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

import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureThumbnailInteractor
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureDisplay
import com.android.systemui.screenshot.mockImageCapture
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub

@SmallTest
@RunWith(AndroidJUnit4::class)
class DisplayViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val Kosmos.fakeDisplay by
        Kosmos.Fixture { ScreenCaptureDisplay(displayId = 123, label = "FakeLabel") }
    private val Kosmos.viewModel by
        Kosmos.Fixture {
            DisplayViewModel(
                model = fakeDisplay,
                thumbnailInteractor = screenCaptureThumbnailInteractor,
            )
        }

    @Test
    fun constructor_initializesFields() =
        kosmos.runTest {
            // Arrange

            // Act
            val viewModel = viewModel

            // Assert
            with(viewModel) {
                assertThat(model).isEqualTo(fakeDisplay)
                assertThat(icon?.isFailure).isTrue()
                assertThat(label?.getOrNull()).isEqualTo("FakeLabel")
                assertThat(thumbnail).isNull()
            }
        }

    @Test
    fun onActivated_loadsThumbnail() =
        kosmos.runTest {
            // Arrange
            val fakeThumbnail = createBitmap(200, 100)
            mockImageCapture.stub {
                on { captureDisplay(any(), anyOrNull()) } doReturn fakeThumbnail
            }
            with(viewModel) {
                assertThat(model).isEqualTo(fakeDisplay)
                assertThat(icon?.isFailure).isTrue()
                assertThat(label?.getOrNull()).isEqualTo("FakeLabel")
                assertThat(thumbnail).isNull()
            }

            // Act
            viewModel.activateIn(testScope)

            // Assert
            with(viewModel) {
                assertThat(model).isEqualTo(fakeDisplay)
                assertThat(icon?.isFailure).isTrue()
                assertThat(label?.getOrNull()).isEqualTo("FakeLabel")
                assertThat(thumbnail?.getOrNull()?.sameAs(fakeThumbnail)).isTrue()
            }
        }
}
