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

import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureDisplay
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureDisplayInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val Kosmos.interactor by
        Kosmos.Fixture { ScreenCaptureDisplayInteractor(displayRepository = displayRepository) }

    @Test
    fun displays_emitsDisplays() =
        kosmos.runTest {
            // Arrange
            displayRepository.emit(setOf(makeDisplay(1)))
            val fakeDisplay2 = makeDisplay(2)
            val result by collectValues(interactor.displays)
            assertThat(result).hasSize(1)
            assertThat(result.last()).containsExactly(ScreenCaptureDisplay(1, "FakeLabel1"))

            // Act
            displayRepository.addDisplay(fakeDisplay2)

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result.last())
                .containsExactly(
                    ScreenCaptureDisplay(1, "FakeLabel1"),
                    ScreenCaptureDisplay(2, "FakeLabel2"),
                )
        }

    private fun makeDisplay(displayId: Int) =
        mock<Display> {
            on { this.displayId } doReturn displayId
            on { name } doReturn "FakeLabel$displayId"
        }
}
