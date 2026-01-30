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

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureLabelRepository
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureLabelInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    @Test
    fun loadLabel_returnsLabelFromRepository() =
        kosmos.runTest {
            // Arrange
            val interactor = ScreenCaptureLabelInteractor(fakeScreenCaptureLabelRepository)
            val fakeComponent = ComponentName("FakePackage", "FakeClass")
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
            val result = interactor.loadLabel(fakeTask)

            // Assert
            assertThat(fakeScreenCaptureLabelRepository.loadLabelCalls).hasSize(1)
            fakeScreenCaptureLabelRepository.loadLabelCalls.first().let {
                (component, userId, badged) ->
                assertThat(component).isEqualTo(fakeComponent)
                assertThat(userId).isEqualTo(3)
                assertThat(badged).isTrue()
            }
            assertThat(result).isEqualTo(fakeScreenCaptureLabelRepository.fakeLabel)
        }
}
