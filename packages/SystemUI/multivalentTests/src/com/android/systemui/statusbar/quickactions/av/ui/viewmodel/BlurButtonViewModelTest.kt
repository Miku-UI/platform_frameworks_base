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
class BlurButtonViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            val res = blurButtonViewModelFactory.create { currentPage = it }
            res.activateIn(kosmos.testScope)
            res
        }

    private var currentPage = PageType.MAIN

    @Before
    fun setUp() {
        kosmos.fakeSettings.userId = 0
    }

    @Test
    fun initialState_blurOff() =
        kosmos.runTest {
            assertThat(underTest.state.mainTitle)
                .isEqualTo(com.android.systemui.res.R.string.av_blur)
            assertThat(underTest.state.isEnabled).isFalse()
            assertThat(underTest.state.image)
                .isEqualTo(com.android.systemui.res.R.drawable.gs_background_blur_full_off)
        }

    @Test
    fun blurLight_stateEnabled() =
        kosmos.runTest {
            kosmos.fakeSettings.putInt(DESKTOP_EFFECTS_BLUR_LEVEL_KEY, BlurLevel.LIGHT.code)

            assertThat(underTest.state.mainTitle)
                .isEqualTo(com.android.systemui.res.R.string.av_blur)
            assertThat(underTest.state.isEnabled).isTrue()
            assertThat(underTest.state.image)
                .isEqualTo(com.android.systemui.res.R.drawable.gs_background_blur_light)
        }

    @Test
    fun blurFull_stateEnabled() =
        kosmos.runTest {
            kosmos.fakeSettings.putInt(DESKTOP_EFFECTS_BLUR_LEVEL_KEY, BlurLevel.FULL.code)

            assertThat(underTest.state.mainTitle)
                .isEqualTo(com.android.systemui.res.R.string.av_blur)
            assertThat(underTest.state.isEnabled).isTrue()
            assertThat(underTest.state.image)
                .isEqualTo(com.android.systemui.res.R.drawable.gs_background_blur_full)
        }

    @Test
    fun onClick_setsPage() =
        kosmos.runTest {
            assertThat(currentPage).isEqualTo(PageType.MAIN)

            underTest.onClick()

            assertThat(currentPage).isEqualTo(PageType.BLUR)
        }
}
