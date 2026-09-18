/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.airplane.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.statusbar.pipeline.airplane.data.repository.airplaneModeRepository
import com.android.systemui.statusbar.pipeline.shared.data.model.ConnectivitySlot
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
@RunWith(AndroidJUnit4::class)
class AirplaneModeViewModelImplTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { airplaneModeViewModel }

    @Test
    fun isAirplaneModeIconVisible_notAirplaneMode_outputsFalse() =
        kosmos.runTest {
            connectivityRepository.fake.setForceHiddenIcons(setOf())
            airplaneModeRepository.setIsAirplaneMode(false)

            val latest by collectLastValue(underTest.isAirplaneModeIconVisible)

            assertThat(latest).isFalse()
        }

    @Test
    fun isAirplaneModeIconVisible_forceHidden_outputsFalse() =
        kosmos.runTest {
            connectivityRepository.fake.setForceHiddenIcons(setOf(ConnectivitySlot.AIRPLANE))
            airplaneModeRepository.setIsAirplaneMode(true)

            val latest by collectLastValue(underTest.isAirplaneModeIconVisible)

            assertThat(latest).isFalse()
        }

    @Test
    fun isAirplaneModeIconVisible_isAirplaneModeAndNotForceHidden_outputsTrue() =
        kosmos.runTest {
            connectivityRepository.fake.setForceHiddenIcons(setOf())
            airplaneModeRepository.setIsAirplaneMode(true)

            val latest by collectLastValue(underTest.isAirplaneModeIconVisible)

            assertThat(latest).isTrue()
        }
}
