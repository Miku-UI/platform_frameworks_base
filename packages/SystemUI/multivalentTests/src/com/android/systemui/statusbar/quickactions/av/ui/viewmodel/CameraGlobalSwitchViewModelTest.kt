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

import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.quickactions.av.domain.interactor.avControlsChipInteractor
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class CameraGlobalSwitchViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            val res = cameraGlobalSwitchViewModelFactory.create()
            res.activateIn(testScope)
            res
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun initialState_cameraEnabled() =
        kosmos.runTest {
            assertThat(underTest.state.isEnabled).isTrue()
            assertThat(underTest.state.image)
                .isEqualTo(com.android.systemui.res.R.drawable.gs_videocam)
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraBlocked_stateDisabled() =
        kosmos.runTest {
            assertThat(underTest.state.isEnabled).isTrue()

            kosmos.avControlsChipInteractor.setCameraBlocked(true)

            assertThat(underTest.state.isEnabled).isFalse()
            assertThat(underTest.state.image)
                .isEqualTo(com.android.systemui.res.R.drawable.gs_videocam_off)
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun onClick_togglesCameraBlocked() =
        kosmos.runTest {
            // Initial state: blocked = false, enabled = true
            assertThat(underTest.state.isEnabled).isTrue()

            // Click to block
            underTest.onClick()
            assertThat(kosmos.avControlsChipInteractor.cameraBlocked.value).isTrue()
            assertThat(underTest.state.isEnabled).isFalse()

            // Click to unblock
            underTest.onClick()
            assertThat(kosmos.avControlsChipInteractor.cameraBlocked.value).isFalse()
            assertThat(underTest.state.isEnabled).isTrue()
        }
}
