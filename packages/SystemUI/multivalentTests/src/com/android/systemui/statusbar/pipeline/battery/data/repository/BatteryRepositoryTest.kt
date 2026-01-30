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

package com.android.systemui.statusbar.pipeline.battery.data.repository

import android.content.testableContext
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useStandardTestDispatcher
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.shared.settings.data.repository.fakeSystemSettingsRepository
import com.android.systemui.shared.settings.data.repository.systemSettingsRepository
import com.android.systemui.statusbar.policy.batteryController
import com.android.systemui.statusbar.policy.fake
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class BatteryRepositoryTest : SysuiTestCase() {
    // Always use standard dispatcher so that our backpressure tests work correctly.
    val kosmos = testKosmos().useStandardTestDispatcher()

    val Kosmos.underTest by
        Kosmos.Fixture {
            BatteryRepositoryImpl(
                testableContext,
                backgroundScope,
                testDispatcher,
                batteryController,
                systemSettingsRepository,
                logcatTableLogBuffer(this, "BatteryTableLog"),
            )
        }

    @Test
    fun pluggedIn() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isPluggedIn)

            assertThat(latest).isFalse()

            batteryController.fake._isPluggedIn = true

            assertThat(latest).isTrue()
        }

    @Test
    fun powerSave() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isPowerSaveEnabled)

            assertThat(latest).isFalse()

            batteryController.fake._isPowerSave = true

            assertThat(latest).isTrue()
        }

    @Test
    fun extremePowerSave() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isExtremePowerSaveEnabled)

            assertThat(latest).isFalse()

            batteryController.fake._isExtremePowerSave = true

            assertThat(latest).isTrue()
        }

    @Test
    fun defend() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isBatteryDefenderEnabled)

            assertThat(latest).isFalse()

            batteryController.fake._isDefender = true

            assertThat(latest).isTrue()
        }

    @Test
    fun level() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.level)

            batteryController.fake._level = 42

            assertThat(latest).isEqualTo(42)

            batteryController.fake._level = 84

            assertThat(latest).isEqualTo(84)
        }

    @Test
    fun isStateUnknown() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isStateUnknown)

            assertThat(latest).isFalse()

            batteryController.fake._isStateUnknown = true

            assertThat(latest).isTrue()
        }

    @Test
    fun showBatteryPercentSetting() =
        kosmos.runTest {
            // Set the default to true, so it's detectable in test
            testableContext.orCreateTestableResources.addOverride(
                com.android.internal.R.bool.config_defaultBatteryPercentageSetting,
                true,
            )

            val latest by collectLastValue(underTest.isShowBatteryPercentSettingEnabled)

            assertThat(latest).isTrue()

            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 0)

            assertThat(latest).isFalse()

            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 1)

            assertThat(latest).isTrue()
        }

    @Test
    fun batteryRemainingEstimateString_queriesEveryTwoMinutes() =
        kosmos.runTest {
            batteryController.fake._estimatedTimeRemainingString = null

            val latest by collectLastValue(underTest.batteryTimeRemainingEstimate)

            assertThat(latest).isNull()

            batteryController.fake._estimatedTimeRemainingString = "test time remaining"

            testScope.advanceTimeBy(2.minutes)

            assertThat(latest).isEqualTo("test time remaining")
        }

    @Test
    fun incompatibleCharging() =
        kosmos.runTest {
            batteryController.fake._isIncompatibleCharging = true

            val latest by collectLastValue(underTest.isIncompatibleCharging)

            assertThat(latest).isTrue()

            batteryController.fake._isIncompatibleCharging = false

            assertThat(latest).isFalse()
        }

    /** Regression test for b/433239990. */
    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_BATTERY_NO_CONFLATION)
    fun backpressure_isStateUnknown() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isStateUnknown)
            testScope.runCurrent()
            assertThat(latest).isFalse()

            batteryController.fake._isStateUnknown = true
            testScope.runCurrent()
            assertThat(latest).isTrue()

            // Introduce backpressure by sending multiple events before `runCurrent`
            // The first and the last event will be processed, but the middle event would get
            // dropped if there's backpressure. So, put the event we care about in the middle.
            batteryController.fake._isPowerSave = true
            batteryController.fake._isStateUnknown = false
            batteryController.fake._isDefender = true

            // Use many `runCurrent`s to be sure we've processed all the events we have
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()

            // Verify we didn't drop the middle unknown=false event
            assertThat(latest).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_BATTERY_NO_CONFLATION)
    fun backpressure_isPluggedIn() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isPluggedIn)
            testScope.runCurrent()
            assertThat(latest).isFalse()

            batteryController.fake._isPluggedIn = true
            testScope.runCurrent()
            assertThat(latest).isTrue()

            // Introduce backpressure by sending multiple events before `runCurrent`
            // The first and the last event will be processed, but the middle event would get
            // dropped if there's backpressure. So, put the event we care about in the middle.
            batteryController.fake._isPowerSave = true
            batteryController.fake._isPluggedIn = false
            batteryController.fake._isDefender = true

            // Use many `runCurrent`s to be sure we've processed all the events we have
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()

            // Verify we didn't drop the middle isPluggedIn=false event
            assertThat(latest).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_BATTERY_NO_CONFLATION)
    fun backpressure_isPowerSave() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isPowerSaveEnabled)
            testScope.runCurrent()
            assertThat(latest).isFalse()

            batteryController.fake._isPowerSave = true
            testScope.runCurrent()
            assertThat(latest).isTrue()

            // Introduce backpressure by sending multiple events before `runCurrent`
            // The first and the last event will be processed, but the middle event would get
            // dropped if there's backpressure. So, put the event we care about in the middle.
            batteryController.fake._level = 44
            batteryController.fake._isPowerSave = false
            batteryController.fake._isPluggedIn = true

            // Use many `runCurrent`s to be sure we've processed all the events we have
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()

            // Verify we didn't drop the middle isPowerSave=false event
            assertThat(latest).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_BATTERY_NO_CONFLATION)
    fun backpressure_isExtremePowerSave() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isExtremePowerSaveEnabled)
            testScope.runCurrent()
            assertThat(latest).isFalse()

            batteryController.fake._isExtremePowerSave = true
            testScope.runCurrent()
            assertThat(latest).isTrue()

            // Introduce backpressure by sending multiple events before `runCurrent`
            // The first and the last event will be processed, but the middle event would get
            // dropped if there's backpressure. So, put the event we care about in the middle.
            batteryController.fake._isPluggedIn = true
            batteryController.fake._isExtremePowerSave = false
            batteryController.fake._level = 33

            // Use many `runCurrent`s to be sure we've processed all the events we have
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()

            // Verify we didn't drop the middle isExtremePowerSave=false event
            assertThat(latest).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_BATTERY_NO_CONFLATION)
    fun backpressure_isBatteryDefender() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isBatteryDefenderEnabled)
            testScope.runCurrent()
            assertThat(latest).isFalse()

            batteryController.fake._isDefender = true
            testScope.runCurrent()
            assertThat(latest).isTrue()

            // Introduce backpressure by sending multiple events before `runCurrent`
            // The first and the last event will be processed, but the middle event would get
            // dropped if there's backpressure. So, put the event we care about in the middle.
            batteryController.fake._isPluggedIn = true
            batteryController.fake._isDefender = false
            batteryController.fake._isPowerSave = true

            // Use many `runCurrent`s to be sure we've processed all the events we have
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()

            // Verify we didn't drop the middle isDefender=false event
            assertThat(latest).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_BATTERY_NO_CONFLATION)
    fun backpressure_level() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.level)
            testScope.runCurrent()
            batteryController.fake._level = 10
            testScope.runCurrent()
            assertThat(latest).isEqualTo(10)

            // Introduce backpressure by sending multiple events before `runCurrent`
            // The first and the last event will be processed, but the middle event would get
            // dropped if there's backpressure. So, put the event we care about in the middle.
            batteryController.fake._isPluggedIn = true
            batteryController.fake._level = 15
            batteryController.fake._isPowerSave = true

            // Use many `runCurrent`s to be sure we've processed all the events we have
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()

            // Verify we didn't drop the middle level=15 event
            assertThat(latest).isEqualTo(15)
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_BATTERY_NO_CONFLATION)
    fun backpressure_isIncompatibleCharging() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isIncompatibleCharging)
            testScope.runCurrent()
            assertThat(latest).isFalse()

            batteryController.fake._isIncompatibleCharging = true
            testScope.runCurrent()
            assertThat(latest).isTrue()

            // Introduce backpressure by sending multiple events before `runCurrent`
            // The first and the last event will be processed, but the middle event would get
            // dropped if there's backpressure. So, put the event we care about in the middle.
            batteryController.fake._isPluggedIn = true
            batteryController.fake._isIncompatibleCharging = false
            batteryController.fake._isPowerSave = true

            // Use many `runCurrent`s to be sure we've processed all the events we have
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()
            testScope.runCurrent()

            // Verify we didn't drop the middle isIncompatibleCharging=false event
            assertThat(latest).isFalse()
        }
}
