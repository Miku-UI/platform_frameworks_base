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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor.Companion.DESKTOP_EFFECTS_BLUR_LEVEL_KEY
import com.android.systemui.statusbar.quickactions.av.shared.model.BlurLevel
import com.android.systemui.testKosmosNew
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class BlurDrillInViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            val popupViewModel = avControlsPopupViewModelFactory.create()
            val res =
                popupViewModel.blurDrillInViewModelFactory.create(
                    returnToMainPage = { currentPage = PageType.MAIN }
                )
            res.activateIn(testScope)
            res
        }
    private var currentPage = PageType.BLUR

    @Before
    fun setUp() {
        kosmos.fakeSettings.userId = 0
    }

    @Test
    fun title() =
        kosmos.runTest {
            assertThat(underTest.drillInTitle)
                .isEqualTo(com.android.systemui.res.R.string.av_camera_blur_drill_in_title)
        }

    @Test
    fun returnToMainPage() =
        kosmos.runTest {
            assertThat(currentPage).isEqualTo(PageType.BLUR)
            underTest.returnToMainPage()
            assertThat(currentPage).isEqualTo(PageType.MAIN)
        }

    @Test
    fun initialState_blurOff_offButtonEnabled() =
        kosmos.runTest {
            assertThat(underTest.blurOffButton.state.isEnabled).isTrue()
            assertThat(underTest.blurLightButton.state.isEnabled).isFalse()
            assertThat(underTest.blurFullButton.state.isEnabled).isFalse()
        }

    @Test
    fun blurLight_lightButtonEnabled() =
        kosmos.runTest {
            kosmos.fakeSettings.putInt(DESKTOP_EFFECTS_BLUR_LEVEL_KEY, BlurLevel.LIGHT.code)
            assertThat(underTest.blurOffButton.state.isEnabled).isFalse()
            assertThat(underTest.blurLightButton.state.isEnabled).isTrue()
            assertThat(underTest.blurFullButton.state.isEnabled).isFalse()
        }

    @Test
    fun blurFull_fullButtonEnabled() =
        kosmos.runTest {
            kosmos.fakeSettings.putInt(DESKTOP_EFFECTS_BLUR_LEVEL_KEY, BlurLevel.FULL.code)
            assertThat(underTest.blurOffButton.state.isEnabled).isFalse()
            assertThat(underTest.blurLightButton.state.isEnabled).isFalse()
            assertThat(underTest.blurFullButton.state.isEnabled).isTrue()
        }

    @Test
    fun onClickFull_setsBlurFull() =
        kosmos.runTest {
            underTest.blurFullButton.onClick()
            assertThat(kosmos.fakeSettings.getInt(DESKTOP_EFFECTS_BLUR_LEVEL_KEY, -1))
                .isEqualTo(BlurLevel.FULL.code)
        }

    @Test
    fun onClickLight_setsBlurLight() =
        kosmos.runTest {
            underTest.blurLightButton.onClick()
            assertThat(kosmos.fakeSettings.getInt(DESKTOP_EFFECTS_BLUR_LEVEL_KEY, -1))
                .isEqualTo(BlurLevel.LIGHT.code)
        }

    @Test
    fun onClickOff_setsBlurOff() =
        kosmos.runTest {
            kosmos.fakeSettings.putInt(DESKTOP_EFFECTS_BLUR_LEVEL_KEY, BlurLevel.FULL.code)
            underTest.blurOffButton.onClick()
            assertThat(kosmos.fakeSettings.getInt(DESKTOP_EFFECTS_BLUR_LEVEL_KEY, -1))
                .isEqualTo(BlurLevel.OFF.code)
        }
}
