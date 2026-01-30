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

package com.android.systemui.statusbar.systemstatusicons.airplane.ui.viewmodel

import android.content.testableContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.airplane.data.repository.airplaneModeRepository
import com.android.systemui.statusbar.pipeline.shared.data.model.ConnectivitySlot
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AirplaneModeIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val fakeConnectivityRepository = kosmos.connectivityRepository.fake
    private val expectedAirplaneIcon =
        Icon.Resource(
            resId = com.android.internal.R.drawable.ic_qs_airplane,
            contentDescription = ContentDescription.Resource(R.string.accessibility_airplane_mode),
        )

    private val underTest =
        kosmos.airplaneModeIconViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }

    @Test
    fun visible_isFalse_byDefault() = kosmos.runTest { assertThat(underTest.visible).isFalse() }

    @Test fun icon_notVisible_isNull() = kosmos.runTest { assertThat(underTest.icon).isNull() }

    @Test
    fun visible_airplaneModeOnAndNotForceHidden_isTrue() =
        kosmos.runTest {
            airplaneModeRepository.setIsAirplaneMode(true)
            fakeConnectivityRepository.setForceHiddenIcons(setOf())

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_forceHidden_isFalse() =
        kosmos.runTest {
            airplaneModeRepository.setIsAirplaneMode(true)
            fakeConnectivityRepository.setForceHiddenIcons(setOf(ConnectivitySlot.AIRPLANE))

            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun visible_airplaneModeChanges_flips() =
        kosmos.runTest {
            fakeConnectivityRepository.setForceHiddenIcons(setOf())

            airplaneModeRepository.setIsAirplaneMode(false)
            assertThat(underTest.visible).isFalse()

            airplaneModeRepository.setIsAirplaneMode(true)

            assertThat(underTest.visible).isTrue()

            airplaneModeRepository.setIsAirplaneMode(false)

            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun visible_forceHiddenChanges_flips() =
        kosmos.runTest {
            airplaneModeRepository.setIsAirplaneMode(true)

            fakeConnectivityRepository.setForceHiddenIcons(setOf())
            assertThat(underTest.visible).isTrue()

            fakeConnectivityRepository.setForceHiddenIcons(setOf(ConnectivitySlot.AIRPLANE))

            assertThat(underTest.visible).isFalse()

            fakeConnectivityRepository.setForceHiddenIcons(setOf())

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun icon_visible_isCorrect() =
        kosmos.runTest {
            airplaneModeRepository.setIsAirplaneMode(true)
            fakeConnectivityRepository.setForceHiddenIcons(setOf())

            assertThat(underTest.icon).isEqualTo(expectedAirplaneIcon)
        }
}
