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

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.compose.ui.Alignment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class ShadeModeInteractorImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { shadeModeInteractor }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun legacyShadeMode_narrowScreen_singleShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSingleShade(wideLayout = false)

            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun legacyShadeMode_narrowLargeScreen_singleShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            // This simulates the case of a tablet or certain unfolded foldables in portrait mode.
            enableSingleShade(wideLayout = false)

            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun legacyShadeMode_wideScreen_singleShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSingleShade(wideLayout = true)

            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun legacyShadeMode_wideScreen_splitShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSplitShade()

            assertThat(shadeMode).isEqualTo(ShadeMode.Split)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun legacyIsFullWidthShade_singleShadeWide_true() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)
            enableSingleShade(wideLayout = true)

            assertThat(isFullWidthShade).isTrue()
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun legacyIsFullWidthShade_singleShadeNarrow_true() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)
            enableSingleShade(wideLayout = false)

            assertThat(isFullWidthShade).isTrue()
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun legacyIsFullWidthShade_splitShade_false() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)
            enableSplitShade()

            assertThat(isFullWidthShade).isFalse()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun defaultShadeMode_singleShadeOverridden_dualShade() =
        kosmos.runTest {
            overrideResource(R.bool.config_dualShadeEnabledByDefault, true)
            enableSingleShade()
            val shadeMode by collectLastValue(underTest.shadeMode)
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)

            overrideResource(com.android.settingslib.R.bool.config_useDualShadeSetting, false)
            fakeConfigurationRepository.onConfigurationChange()

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun shadeMode_wideScreenDefault_isDual() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableDualShade(wideLayout = true)

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun shadeMode_narrowScreenDefault_isDual() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableDualShade(wideLayout = false)

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isFullWidthShade_singleShadeNarrow_true() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)
            enableSingleShade(wideLayout = false)

            assertThat(isFullWidthShade).isTrue()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isFullWidthShade_singleShadeWide_true() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)
            enableSingleShade(wideLayout = true)

            assertThat(isFullWidthShade).isTrue()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isFullWidthShade_dualShadeNarrow_true() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)

            enableDualShade(wideLayout = false)

            assertThat(isFullWidthShade).isTrue()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isFullWidthShade_dualShadeWide_false() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)

            enableDualShade(wideLayout = true)

            assertThat(isFullWidthShade).isFalse()
        }

    @Test
    fun notificationStackHorizontalAlignment_singleShade_centeredHorizontally() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableSingleShade(wideLayout = true)

            assertThat(alignment).isEqualTo(Alignment.CenterHorizontally)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun notificationStackHorizontalAlignment_splitShade_endAligned() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableSplitShade()

            assertThat(alignment).isEqualTo(Alignment.End)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun notificationStackHorizontalAlignment_dualShadeNarrow_centeredHorizontally() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableDualShade(wideLayout = false)

            assertThat(alignment).isEqualTo(Alignment.CenterHorizontally)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun notificationStackHorizontalAlignment_dualShadeWide_startAligned() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableDualShade(wideLayout = true)

            assertThat(alignment).isEqualTo(Alignment.Start)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun notificationStackHorizontalAlignment_desktopWithTopEndConfig_endAligned() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            overrideResource(R.bool.config_notificationShadeOnTopEnd, true)
            fakeConfigurationRepository.onConfigurationChange()

            enableDualShade(wideLayout = true)

            assertThat(alignment).isEqualTo(Alignment.End)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun notificationStackHorizontalAlignment_desktopWithoutTopEndConfig_startAligned() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            overrideResource(R.bool.config_notificationShadeOnTopEnd, false)
            fakeConfigurationRepository.onConfigurationChange()

            enableDualShade(wideLayout = true)

            assertThat(alignment).isEqualTo(Alignment.Start)
        }
}
