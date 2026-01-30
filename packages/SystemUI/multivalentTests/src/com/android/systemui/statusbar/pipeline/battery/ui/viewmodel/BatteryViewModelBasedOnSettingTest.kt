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

package com.android.systemui.statusbar.pipeline.battery.ui.viewmodel

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.shared.settings.data.repository.fakeSystemSettingsRepository
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryGlyph
import com.android.systemui.statusbar.policy.batteryController
import com.android.systemui.statusbar.policy.fake
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class BatteryViewModelBasedOnSettingTest : SysuiTestCase() {
    val kosmos = testKosmos()
    val Kosmos.underTest by Kosmos.Fixture { batteryViewModelBasedOnSettingFactory.create() }

    @Before
    fun setUp() {
        kosmos.useUnconfinedTestDispatcher()
        kosmos.underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun glyphList_notCharging_settingOff_isEmpty() =
        kosmos.runTest {
            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 0)
            batteryController.fake._isPluggedIn = false
            batteryController.fake._level = 42

            assertThat(underTest.glyphList).isEmpty()
        }

    @Test
    fun glyphList_notCharging_settingOn_hasOnlyLevelGlyphs() =
        kosmos.runTest {
            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 1)
            batteryController.fake._isPluggedIn = false
            batteryController.fake._level = 42

            assertThat(underTest.glyphList).isEqualTo(listOf(BatteryGlyph.Four, BatteryGlyph.Two))
        }

    @Test
    fun glyphList_charging_settingOn_notFull_hasLevel() =
        kosmos.runTest {
            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 1)
            batteryController.fake._isPluggedIn = true
            batteryController.fake._level = 39

            assertThat(underTest.glyphList).isEqualTo(listOf(BatteryGlyph.Three, BatteryGlyph.Nine))
        }

    @Test
    fun attribution_charging_settingOn_isFull_onlyHasLargeBolt() =
        kosmos.runTest {
            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 1)
            batteryController.fake._isPluggedIn = true
            batteryController.fake._level = 100

            assertThat(underTest.attribution).isEqualTo(BatteryGlyph.Bolt)
        }

    @Test
    fun attribution_charging_settingOff_notFull_onlyHasLargeGlyph() =
        kosmos.runTest {
            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 0)
            batteryController.fake._isPluggedIn = true
            batteryController.fake._level = 39

            assertThat(underTest.attribution).isEqualTo(BatteryGlyph.Bolt)
        }

    @Test
    fun glyphList_attributionOrdering_prioritizesDefendOverCharging() =
        kosmos.runTest {
            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 0)
            batteryController.fake._level = 39
            batteryController.fake._isPluggedIn = true
            batteryController.fake._isDefender = true

            assertThat(underTest.attribution).isEqualTo(BatteryGlyph.Defend)
        }

    @Test
    fun attribution_ordering_prioritizesPowerSaveOverDefend() =
        kosmos.runTest {
            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 0)
            batteryController.fake._level = 39
            batteryController.fake._isPluggedIn = true
            batteryController.fake._isDefender = true
            batteryController.fake._isPowerSave = true

            assertThat(underTest.attribution).isEqualTo(BatteryGlyph.Plus)
        }

    @Test
    fun attribution_ordering_prioritizesPowerSaveOverCharging() =
        kosmos.runTest {
            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 0)
            batteryController.fake._level = 39
            batteryController.fake._isPluggedIn = true
            batteryController.fake._isDefender = false
            batteryController.fake._isPowerSave = true

            assertThat(underTest.attribution).isEqualTo(BatteryGlyph.Plus)
        }

    @Test
    fun attribution_charging_settingOn_notFull_hasBolt() =
        kosmos.runTest {
            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 1)
            batteryController.fake._isPluggedIn = true
            batteryController.fake._level = 39

            assertThat(underTest.attribution).isEqualTo(BatteryGlyph.Bolt)
        }

    @Test
    fun contentDescription_default() =
        kosmos.runTest {
            batteryController.fake._isPluggedIn = false
            batteryController.fake._isPowerSave = false
            batteryController.fake._isDefender = false
            batteryController.fake._level = 39

            val expected =
                ContentDescription.Loaded(
                    context.getString(R.string.accessibility_battery_level, 39)
                )

            assertThat(underTest.contentDescription).isEqualTo(expected)
        }

    @Test
    fun contentDescription_defendEnabled() =
        kosmos.runTest {
            batteryController.fake._isDefender = true
            batteryController.fake._level = 39

            val expected =
                ContentDescription.Loaded(
                    context.getString(R.string.accessibility_battery_level_charging_paused, 39)
                )

            assertThat(underTest.contentDescription).isEqualTo(expected)
        }

    @Test
    fun contentDescription_charging() =
        kosmos.runTest {
            batteryController.fake._isPluggedIn = true
            batteryController.fake._level = 39

            val expected =
                ContentDescription.Loaded(
                    context.getString(R.string.accessibility_battery_level_charging, 39)
                )

            assertThat(underTest.contentDescription).isEqualTo(expected)
        }

    @Test
    fun contentDescription_batterySaver() =
        kosmos.runTest {
            batteryController.fake._isPowerSave = true
            batteryController.fake._level = 39

            val expected =
                ContentDescription.Loaded(
                    context.getString(
                        R.string.accessibility_battery_level_battery_saver_with_percent,
                        39,
                    )
                )

            assertThat(underTest.contentDescription).isEqualTo(expected)
        }

    @Test
    fun batteryRemainingEstimate_onlyShowWhenNotCharging() =
        kosmos.runTest {
            val timeRemaining = "1234"
            batteryController.fake._isPluggedIn = true
            batteryController.fake._level = 39
            batteryController.fake._estimatedTimeRemainingString = timeRemaining

            assertThat(underTest.batteryTimeRemainingEstimate).isNull()

            batteryController.fake._isPluggedIn = false

            assertThat(underTest.batteryTimeRemainingEstimate).isEqualTo(timeRemaining)
        }

    @Test
    fun incompatibleCharging_pluggedIn_notShowingChargingState() =
        kosmos.runTest {
            batteryController.fake._isPluggedIn = true
            batteryController.fake._level = 39
            batteryController.fake._isIncompatibleCharging = true

            assertThat(underTest.attribution).isNull()
        }

    @Test
    fun stateUnknown_usesQuestionMarkGlyph() =
        kosmos.runTest {
            batteryController.fake._isStateUnknown = true

            assertThat(underTest.attribution).isEqualTo(BatteryGlyph.Question)
        }

    @Test
    fun stateUnknown_levelIsNull() =
        kosmos.runTest {
            batteryController.fake._level = 41
            batteryController.fake._isStateUnknown = true

            assertThat(underTest.level).isNull()
        }
}
