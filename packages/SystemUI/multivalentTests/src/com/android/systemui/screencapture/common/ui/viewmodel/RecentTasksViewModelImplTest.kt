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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureRecentTaskInteractor
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RecentTasksViewModelImplTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    @Test
    fun recentTasks_returnsRecentTasksFromRepository() =
        kosmos.runTest {
            // Arrange
            val viewModel = RecentTasksViewModelImpl(screenCaptureRecentTaskInteractor)

            // Act
            val result = viewModel.recentTasks

            // Assert
            Truth.assertThat(result).isSameInstanceAs(screenCaptureRecentTaskInteractor.recentTasks)
        }
}
