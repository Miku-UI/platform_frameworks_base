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

import android.view.Display
import androidx.compose.runtime.getValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureDisplayInteractor
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureDisplay
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class DisplaysViewModelImplTest : SysuiTestCase() {

    private val kosmos =
        testKosmosNew().apply {
            runBlocking {
                displayRepository.emit(
                    setOf(
                        mock<Display> {
                            on { displayId } doReturn 123
                            on { name } doReturn "FakeLabel"
                        }
                    )
                )
            }
        }

    private val Kosmos.viewModel by
        Kosmos.Fixture {
            DisplaysViewModelImpl(
                interactor = screenCaptureDisplayInteractor,
                displayViewModelFactory = displayViewModelFactory,
                drawableLoaderViewModel = drawableLoaderViewModel,
                audioSwitchViewModel = audioSwitchViewModel,
            )
        }
    private val Kosmos.fakeDisplay by
        Kosmos.Fixture { ScreenCaptureDisplay(displayId = 123, label = "FakeLabel") }

    @Test
    fun targets_returnsDisplaysFromInteractor() =
        kosmos.runTest {
            // Arrange
            viewModel.activateIn(testScope)

            // Act
            val result by viewModel.targets

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result?.first()?.displayId).isEqualTo(123)
        }

    @Test
    fun selectedTarget_returnsSelectedTarget() =
        kosmos.runTest {
            // Arrange
            val fakeDisplayViewModel = displayViewModelFactory.create(fakeDisplay)
            viewModel.activateIn(testScope)
            val result by viewModel.selectedTarget
            assertThat(result).isNull()

            // Act
            viewModel.setSelectedTarget(fakeDisplayViewModel)

            // Assert
            assertThat(result).isSameInstanceAs(fakeDisplayViewModel)
        }

    @Test
    fun createViewModelFor_returnsViewModelForDisplay() =
        kosmos.runTest {
            // Arrange
            viewModel.activateIn(testScope)

            // Act
            val result = viewModel.createViewModelFor(fakeDisplay)

            // Assert
            assertThat(result.model).isEqualTo(fakeDisplay)
        }
}
