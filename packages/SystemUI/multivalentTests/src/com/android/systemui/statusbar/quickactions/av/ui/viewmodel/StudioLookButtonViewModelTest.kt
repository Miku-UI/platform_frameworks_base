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
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor.Companion.DESKTOP_EFFECTS_FACE_RETOUCH_KEY
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor.Companion.DESKTOP_EFFECTS_PORTRAIT_RELIGHT_KEY
import com.android.systemui.testKosmosNew
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class StudioLookButtonViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            val res =
                avControlsPopupViewModelFactory
                    .create()
                    .avControlsPanelContentViewModelFactory
                    .create {}
                    .studioLookButtonViewModelFactory
                    .create(setCurrentPage = { currentPage = it })
            res.activateIn(testScope)
            res
        }
    private var currentPage = PageType.MAIN

    @Before
    fun setUp() {
        kosmos.fakeSettings.userId = 0
    }

    @Test
    fun initialState_disabled() =
        kosmos.runTest {
            assertThat(underTest.state.isEnabled).isFalse()
            assertThat(underTest.state.image)
                .isEqualTo(com.android.systemui.res.R.drawable.gs_face_retouch)
            assertThat(underTest.state.mainTitle)
                .isEqualTo(com.android.systemui.res.R.string.av_studio_look)
            assertThat(underTest.state.subTitle)
                .isEqualTo(com.android.systemui.res.R.string.av_studio_look_effects)
            assertThat(underTest.state.subTitleArg).isEqualTo(0)
        }

    @Test
    fun faceRetouchEnabled() =
        kosmos.runTest {
            kosmos.fakeSettings.putBool(DESKTOP_EFFECTS_FACE_RETOUCH_KEY, true)
            assertThat(underTest.state.isEnabled).isTrue()
            assertThat(underTest.state.subTitle)
                .isEqualTo(com.android.systemui.res.R.string.av_studio_look_effects)
            assertThat(underTest.state.subTitleArg).isEqualTo(1)
        }

    @Test
    fun portraitRelightEnabled() =
        kosmos.runTest {
            kosmos.fakeSettings.putBool(DESKTOP_EFFECTS_PORTRAIT_RELIGHT_KEY, true)
            assertThat(underTest.state.isEnabled).isTrue()
            assertThat(underTest.state.subTitle)
                .isEqualTo(com.android.systemui.res.R.string.av_studio_look_effects)
            assertThat(underTest.state.subTitleArg).isEqualTo(1)
        }

    @Test
    fun bothEnabled() =
        kosmos.runTest {
            kosmos.fakeSettings.putBool(DESKTOP_EFFECTS_FACE_RETOUCH_KEY, true)
            kosmos.fakeSettings.putBool(DESKTOP_EFFECTS_PORTRAIT_RELIGHT_KEY, true)
            assertThat(underTest.state.isEnabled).isTrue()
            assertThat(underTest.state.subTitle)
                .isEqualTo(com.android.systemui.res.R.string.av_studio_look_effects)
            assertThat(underTest.state.subTitleArg).isEqualTo(2)
        }

    @Test
    fun onClick_setsPageToStudioLook() =
        kosmos.runTest {
            underTest.onClick()
            assertThat(currentPage).isEqualTo(PageType.STUDIO_LOOK)
        }
}
