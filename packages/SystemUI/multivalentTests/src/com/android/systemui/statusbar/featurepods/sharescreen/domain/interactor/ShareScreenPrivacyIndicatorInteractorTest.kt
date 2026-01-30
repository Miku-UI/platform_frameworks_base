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

package com.android.systemui.statusbar.featurepods.sharescreen.domain.interactor

import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.currentValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class ShareScreenPrivacyIndicatorInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.shareScreenPrivacyIndicatorInteractor }

    @Before
    fun setUp() {
        mContext.orCreateTestableResources.addOverride(
            R.bool.config_largeScreenPrivacyIndicator,
            true,
        )
    }

    @Test
    fun isChipVisible_initiallyFalse() =
        kosmos.runTest { assertThat(currentValue(underTest.isChipVisible)).isFalse() }

    @Test
    fun isChipVisible_showChip_true() =
        kosmos.runTest {
            underTest.showChip()
            assertThat(currentValue(underTest.isChipVisible)).isTrue()
        }

    @Test
    fun isChipVisible_showAndHide_false() =
        kosmos.runTest {
            underTest.showChip()
            assertThat(currentValue(underTest.isChipVisible)).isTrue()

            underTest.hideChip()
            assertThat(currentValue(underTest.isChipVisible)).isFalse()
        }
}
