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
import com.android.systemui.testKosmosNew
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class FaceRetouchButtonViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            val res =
                avControlsPopupViewModelFactory
                    .create()
                    .studioLookDrillInViewModelFactory
                    .create {}
                    .faceRetouchButtonViewModelFactory
                    .create()
            res.activateIn(testScope)
            res
        }

    @Before
    fun setUp() {
        kosmos.fakeSettings.userId = 0
    }

    @Test
    fun titleAndIcon() =
        kosmos.runTest {
            assertThat(underTest.state.mainTitle)
                .isEqualTo(com.android.systemui.res.R.string.av_camera_face_retouch)
            assertThat(underTest.state.image)
                .isEqualTo(com.android.systemui.res.R.drawable.gs_portrait_lighting)
        }

    @Test
    fun initialState_faceRetouchDisabled() =
        kosmos.runTest { assertThat(underTest.state.isEnabled).isFalse() }

    @Test
    fun faceRetouchEnabled_stateEnabled() =
        kosmos.runTest {
            kosmos.fakeSettings.putBool(DESKTOP_EFFECTS_FACE_RETOUCH_KEY, true)
            assertThat(underTest.state.isEnabled).isTrue()
        }

    @Test
    fun onClick_togglesFaceRetouch() =
        kosmos.runTest {
            // Initial state: disabled
            assertThat(underTest.state.isEnabled).isFalse()
            assertThat(kosmos.fakeSettings.getBool(DESKTOP_EFFECTS_FACE_RETOUCH_KEY)).isFalse()

            // Click to enable
            underTest.onClick()
            assertThat(kosmos.fakeSettings.getBool(DESKTOP_EFFECTS_FACE_RETOUCH_KEY)).isTrue()
            assertThat(underTest.state.isEnabled).isTrue()

            // Click to disable
            underTest.onClick()
            assertThat(kosmos.fakeSettings.getBool(DESKTOP_EFFECTS_FACE_RETOUCH_KEY)).isFalse()
            assertThat(underTest.state.isEnabled).isFalse()
        }
}
