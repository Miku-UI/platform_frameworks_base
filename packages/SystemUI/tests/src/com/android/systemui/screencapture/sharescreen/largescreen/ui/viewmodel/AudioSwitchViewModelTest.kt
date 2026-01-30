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

package com.android.systemui.screencapture.sharescreen.largescreen.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AudioSwitchViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val testScope = kosmos.testScope

    private val viewModel: AudioSwitchViewModel = kosmos.audioSwitchViewModel

    @Before
    fun setUp() {
        viewModel.activateIn(testScope)
    }

    @Test
    fun initialState() =
        kosmos.runTest {
            // Assert that the initial values are as expected upon creation and activation.
            assertThat(viewModel.audioSwitchChecked).isEqualTo(false)
        }
}
