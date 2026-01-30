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

package com.android.systemui.statusbar.pipeline.battery.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.policy.batteryController
import com.android.systemui.statusbar.policy.fake
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class BatteryInteractorTest : SysuiTestCase() {
    val kosmos = testKosmos().useUnconfinedTestDispatcher()
    val Kosmos.underTest by Kosmos.Fixture { batteryInteractor }

    @Test
    fun batteryAttributionType() =
        kosmos.runTest {
            batteryController.fake._isPluggedIn = false
            batteryController.fake._isDefender = false
            batteryController.fake._isPowerSave = false

            val latest by collectLastValue(underTest.batteryAttributionType)

            assertThat(latest).isNull()

            batteryController.fake._isDefender = true

            assertThat(latest).isEqualTo(BatteryAttributionModel.Defend)

            batteryController.fake._isDefender = false
            batteryController.fake._isPowerSave = true

            assertThat(latest).isEqualTo(BatteryAttributionModel.PowerSave)

            batteryController.fake._isPowerSave = false
            batteryController.fake._isPluggedIn = true

            assertThat(latest).isEqualTo(BatteryAttributionModel.Charging)

            batteryController.fake._isStateUnknown = true

            assertThat(latest).isEqualTo(BatteryAttributionModel.Unknown)
        }

    @Test
    fun attributionType_prioritizesPowerSaveOverCharging() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.batteryAttributionType)

            batteryController.fake._isPluggedIn = true
            batteryController.fake._isDefender = true
            batteryController.fake._isPowerSave = true

            assertThat(latest).isEqualTo(BatteryAttributionModel.PowerSave)
        }

    @Test
    fun attributionType_prioritizesPowerSaveOverDefender() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.batteryAttributionType)

            batteryController.fake._isPluggedIn = true
            batteryController.fake._isPowerSave = true
            batteryController.fake._isDefender = false

            assertThat(latest).isEqualTo(BatteryAttributionModel.PowerSave)
        }

    @Test
    fun attributionType_prioritizesDefenderOverCharging() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.batteryAttributionType)

            batteryController.fake._isPluggedIn = true
            batteryController.fake._isPowerSave = false
            batteryController.fake._isDefender = true

            assertThat(latest).isEqualTo(BatteryAttributionModel.Defend)
        }

    @Test
    fun attributionType_prioritizesChargingOnly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.batteryAttributionType)

            batteryController.fake._isPluggedIn = true
            batteryController.fake._isDefender = false
            batteryController.fake._isPowerSave = false

            assertThat(latest).isEqualTo(BatteryAttributionModel.Charging)
        }

    @Test
    fun incompatibleCharging_notConsideredCharging() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isCharging)

            batteryController.fake._isPluggedIn = true
            batteryController.fake._isIncompatibleCharging = true

            assertThat(latest).isFalse()
        }

    @Test
    fun stateUnknown_isTopPrioritizedState() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.batteryAttributionType)

            // Not a real scenario, but assume that every state is enabled
            batteryController.fake._isStateUnknown = true
            batteryController.fake._isPluggedIn = true
            batteryController.fake._isPowerSave = true
            batteryController.fake._isDefender = true

            assertThat(latest).isEqualTo(BatteryAttributionModel.Unknown)
        }

    @Test
    fun stateUnknown_levelIsNull() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.level)

            batteryController.fake._level = 50
            batteryController.fake._isStateUnknown = true

            assertThat(latest).isNull()
        }

    @Test
    fun stateUnknown_isInCriticalRange_criticalIsFalse() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isCritical)

            batteryController.fake._level = 5
            batteryController.fake._isStateUnknown = true

            assertThat(latest).isFalse()
        }
}
