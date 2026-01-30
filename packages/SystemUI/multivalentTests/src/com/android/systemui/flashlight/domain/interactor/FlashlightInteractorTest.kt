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

package com.android.systemui.flashlight.domain.interactor

import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flashlight.data.repository.flashlightRepository
import com.android.systemui.flashlight.data.repository.startFlashlightRepository
import com.android.systemui.flashlight.shared.model.FlashlightModel
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
@RunWith(AndroidJUnit4::class)
class FlashlightInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val underTest = kosmos.flashlightInteractor

    @Before
    fun setUp() {
        kosmos.startFlashlightRepository(true)
    }

    @Test
    fun enableDisable_StateMatches() =
        kosmos.runTest {
            val state by collectLastValue(underTest.state)
            val expectedStateDisabled =
                FlashlightModel.Available.Level(false, DEFAULT_DEFAULT_LEVEL, DEFAULT_MAX_LEVEL)
            assertThat(state).isEqualTo(expectedStateDisabled)

            underTest.setEnabled(true)

            val expectedStateEnabled =
                FlashlightModel.Available.Level(true, DEFAULT_DEFAULT_LEVEL, DEFAULT_MAX_LEVEL)
            assertThat(state).isEqualTo(expectedStateEnabled)

            underTest.setEnabled(false)
            assertThat(state).isEqualTo(expectedStateDisabled)
        }

    @Test
    fun setLevel_interactorStateMatches() =
        kosmos.runTest {
            val state by collectLastValue(underTest.state)
            val expectedStateDefault =
                FlashlightModel.Available.Level(false, DEFAULT_DEFAULT_LEVEL, DEFAULT_MAX_LEVEL)
            assertThat(state).isEqualTo(expectedStateDefault)

            underTest.setLevel(BASE_TORCH_LEVEL)

            val expectedState =
                FlashlightModel.Available.Level(true, BASE_TORCH_LEVEL, DEFAULT_MAX_LEVEL)
            assertThat(state).isEqualTo(expectedState)
        }

    @Test
    fun deviceSupportsFlashlight_matchesRepository() =
        kosmos.runTest {
            val hasFlashlightFeature = flashlightRepository.deviceSupportsFlashlight

            assertThat(underTest.deviceSupportsFlashlight).isEqualTo(hasFlashlightFeature)
        }

    companion object {
        private const val BASE_TORCH_LEVEL = 1
        private const val DEFAULT_DEFAULT_LEVEL = 21
        private const val DEFAULT_MAX_LEVEL = 45
    }
}
