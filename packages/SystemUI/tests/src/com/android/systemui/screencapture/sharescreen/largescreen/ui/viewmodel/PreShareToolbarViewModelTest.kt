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
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureTarget
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.data.repository.screenCaptureUiRepository
import com.android.systemui.statusbar.featurepods.sharescreen.domain.interactor.shareScreenPrivacyIndicatorInteractor
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PreShareToolbarViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val viewModel: PreShareToolbarViewModel by lazy { kosmos.preShareToolbarViewModel }

    @Before
    fun setUp() {
        mContext.orCreateTestableResources.addOverride(
            R.bool.config_largeScreenPrivacyIndicator,
            true,
        )
        viewModel.activateIn(kosmos.testScope)
    }

    @Test
    fun initialState() =
        kosmos.runTest {
            // Assert that the initial values are as expected upon creation and activation.
            assertThat(viewModel.selectedScreenCaptureTarget)
                .isEqualTo(ScreenCaptureTarget.AppContent(contentId = 0))
        }

    @Test
    fun onCloseClicked_hidesUi() =
        kosmos.runTest {
            val uiState by
                collectLastValue(
                    kosmos.screenCaptureUiRepository.uiState(ScreenCaptureType.SHARE_SCREEN)
                )

            viewModel.onCloseClicked()

            assertThat(uiState).isEqualTo(ScreenCaptureUiState.Invisible)
        }

    @Test
    fun onShareClicked_hidesUi() =
        kosmos.runTest {
            val uiState by
                collectLastValue(
                    kosmos.screenCaptureUiRepository.uiState(ScreenCaptureType.SHARE_SCREEN)
                )

            viewModel.onShareClicked()

            assertThat(uiState).isEqualTo(ScreenCaptureUiState.Invisible)
        }

    @Test
    fun onShareClicked_showsPrivacyIndicator() =
        kosmos.runTest {
            val isChipVisible by
                collectLastValue(kosmos.shareScreenPrivacyIndicatorInteractor.isChipVisible)
            assertThat(isChipVisible).isFalse()
            viewModel.onShareClicked()
            assertThat(isChipVisible).isTrue()
        }
}
