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

package com.android.systemui.statusbar.systemstatusicons.devicesatellite.ui.viewmodel

import android.content.testableContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.pipeline.satellite.data.repository.deviceBasedSatelliteRepository
import com.android.systemui.statusbar.pipeline.satellite.shared.model.SatelliteConnectionState
import com.android.systemui.statusbar.systemstatusicons.flags.EnableSystemStatusIconsInCompose
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSystemStatusIconsInCompose
class DeviceBasedSatelliteIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            deviceBasedSatelliteIconViewModelFactory.create(kosmos.testableContext).apply {
                activateIn(kosmos.testScope)
            }
        }

    @Test
    fun visible_isFalse_byDefault() = kosmos.runTest { assertThat(underTest.visible).isFalse() }

    @Test
    fun visible_satelliteOn_isTrue() =
        kosmos.runTest {
            setSatelliteIconOn()

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_stateChanges_flipsCorrectly() =
        kosmos.runTest {
            assertThat(underTest.visible).isFalse()

            // WHEN satellite conditions are met
            setSatelliteIconOn()
            // THEN the icon is visible
            assertThat(underTest.visible).isTrue()

            // WHEN satellite conditions are no longer met (e.g., cell is in service)
            deviceBasedSatelliteRepository.connectionState.value = SatelliteConnectionState.Off
            // THEN the icon is not visible
            assertThat(underTest.visible).isFalse()
        }

    @Test fun icon_notVisible_isNull() = kosmos.runTest { assertThat(underTest.icon).isNull() }

    @Test
    fun icon_visible_isNotNull() =
        kosmos.runTest {
            setSatelliteIconOn()
            assertThat(underTest.icon).isNotNull()
        }

    private fun Kosmos.setSatelliteIconOn() {
        deviceBasedSatelliteRepository.isSatelliteProvisioned.value = true
        deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
        deviceBasedSatelliteRepository.connectionState.value = SatelliteConnectionState.Connected
    }
}
