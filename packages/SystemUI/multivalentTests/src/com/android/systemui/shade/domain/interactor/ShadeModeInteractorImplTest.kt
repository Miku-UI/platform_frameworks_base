/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shade.domain.interactor

import android.content.testableContext
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.display.data.repository.displayStateRepository
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.shade.data.repository.shadeRepository
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.shared.settings.data.repository.fakeSecureSettingsRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class ShadeModeInteractorImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { shadeModeInteractor }

    @Test
    fun legacyShadeMode_narrowScreen_singleShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSingleShade(wideLayout = false)

            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    fun legacyShadeMode_narrowLargeScreen_singleShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            // This simulates the case of a tablet or certain unfolded foldables in portrait mode.
            enableSingleShade(wideLayout = false)
            displayStateRepository.setIsLargeScreen(true)

            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    fun legacyShadeMode_wideScreen_singleShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSingleShade(wideLayout = true)

            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    fun legacyShadeMode_wideScreen_splitShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSplitShade()

            assertThat(shadeMode).isEqualTo(ShadeMode.Split)
        }

    @Test
    fun legacyShadeMode_disableSplitShade_wideScreen_dualShade() =
        kosmos.runTest {
            overrideResource(R.bool.config_disableSplitShade, true)
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSplitShade()
            fakeConfigurationRepository.onConfigurationChange()

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
        }

    @Test
    fun legacyShadeMode_disableSplitShade_narrowScreen_singleShade() =
        kosmos.runTest {
            overrideResource(R.bool.config_disableSplitShade, true)
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSingleShade()

            fakeConfigurationRepository.onConfigurationChange()

            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    fun shadeMode_wideScreen_isDual() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableDualShade(wideLayout = true)

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
        }

    @Test
    fun shadeMode_narrowScreen_isDual() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableDualShade(wideLayout = false)

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
        }

    @Test
    fun isDualShade_settingEnabledSceneContainerEnabled_returnsTrue() =
        kosmos.runTest {
            // TODO(b/391578667): Add a test case for user switching once the bug is fixed.
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableDualShade()

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
            assertThat(underTest.isDualShade).isTrue()
        }

    @Test
    fun isDualShade_settingDisabled_returnsFalse() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            disableDualShade()

            assertThat(shadeMode).isNotEqualTo(ShadeMode.Dual)
            assertThat(underTest.isDualShade).isFalse()
        }

    @Test
    fun isFullWidthShade_largeScreenPortrait() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)

            // Large screen portrait
            setupScreenConfig(wideScreen = true, legacyUseSplitShade = false)

            setupShadeConfig(dualShadeSettingEnabled = true, disableSplitShade = false)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = true, disableSplitShade = true)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = false, disableSplitShade = true)
            assertThat(isFullWidthShade).isTrue()

            setupShadeConfig(dualShadeSettingEnabled = false, disableSplitShade = false)
            assertThat(isFullWidthShade).isTrue()
        }

    @Test
    fun isFullWidthShade_largeScreenLandscape() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)

            // Large screen landscape
            setupScreenConfig(wideScreen = true, legacyUseSplitShade = true)

            setupShadeConfig(dualShadeSettingEnabled = true, disableSplitShade = false)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = true, disableSplitShade = true)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = false, disableSplitShade = true)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = false, disableSplitShade = false)
            assertThat(isFullWidthShade).isFalse()
        }

    @Test
    fun isFullWidthShade_compactScreenPortrait() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)

            // Compact screen portrait
            setupScreenConfig(wideScreen = false, legacyUseSplitShade = false)

            setupShadeConfig(dualShadeSettingEnabled = true, disableSplitShade = false)
            assertThat(isFullWidthShade).isTrue()

            setupShadeConfig(dualShadeSettingEnabled = true, disableSplitShade = true)
            assertThat(isFullWidthShade).isTrue()

            setupShadeConfig(dualShadeSettingEnabled = false, disableSplitShade = true)
            assertThat(isFullWidthShade).isTrue()

            setupShadeConfig(dualShadeSettingEnabled = false, disableSplitShade = false)
            assertThat(isFullWidthShade).isTrue()
        }

    @Test
    fun isFullWidthShade_compactScreenLandscape() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)

            // Compact screen landscape
            setupScreenConfig(wideScreen = true, legacyUseSplitShade = false)

            setupShadeConfig(dualShadeSettingEnabled = true, disableSplitShade = false)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = true, disableSplitShade = true)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = false, disableSplitShade = true)
            assertThat(isFullWidthShade).isTrue()

            setupShadeConfig(dualShadeSettingEnabled = false, disableSplitShade = false)
            assertThat(isFullWidthShade).isTrue()
        }

    private fun Kosmos.setupScreenConfig(wideScreen: Boolean, legacyUseSplitShade: Boolean) {
        testableContext.orCreateTestableResources.apply {
            addOverride(R.bool.config_isFullWidthShade, !wideScreen)
            addOverride(R.bool.config_use_split_notification_shade, legacyUseSplitShade)
            addOverride(R.bool.config_use_large_screen_shade_header, legacyUseSplitShade)
        }
        fakeConfigurationRepository.onConfigurationChange()
        shadeRepository.legacyUseSplitShade.value = legacyUseSplitShade
    }

    private fun Kosmos.setupShadeConfig(
        dualShadeSettingEnabled: Boolean,
        disableSplitShade: Boolean,
    ) = runBlocking {
        fakeSecureSettingsRepository.setBoolean(Settings.Secure.DUAL_SHADE, dualShadeSettingEnabled)
        testableContext.orCreateTestableResources.addOverride(
            R.bool.config_disableSplitShade,
            disableSplitShade,
        )
        fakeConfigurationRepository.onConfigurationChange()
    }
}
