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

package com.android.systemui.statusbar.featurepods.sharescreen.ui.viewmodel

import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.sharescreen.domain.interactor.shareScreenPrivacyIndicatorInteractor
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class ShareScreenPrivacyIndicatorViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val interactor by lazy { kosmos.shareScreenPrivacyIndicatorInteractor }
    private lateinit var underTest: ShareScreenPrivacyIndicatorViewModel

    @Before
    fun setUp() {
        mContext.orCreateTestableResources.addOverride(
            R.bool.config_largeScreenPrivacyIndicator,
            true,
        )
        underTest = kosmos.shareScreenPrivacyIndicatorViewModelFactory.create()
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun chip_whenInteractorNotVisible_isHidden() =
        kosmos.runTest {
            assertThat(underTest.chip).isInstanceOf(PopupChipModel.Hidden::class.java)
        }

    @Test
    fun chip_whenInteractorVisible_isShown() =
        kosmos.runTest {
            interactor.showChip()
            assertThat(underTest.chip).isInstanceOf(PopupChipModel.Shown::class.java)
        }

    @Test
    fun chip_shownThenHidden() =
        kosmos.runTest {
            interactor.showChip()
            assertThat(underTest.chip).isInstanceOf(PopupChipModel.Shown::class.java)

            // Hide the chip and verify it's hidden
            interactor.hideChip()
            assertThat(underTest.chip).isInstanceOf(PopupChipModel.Hidden::class.java)
        }
}
