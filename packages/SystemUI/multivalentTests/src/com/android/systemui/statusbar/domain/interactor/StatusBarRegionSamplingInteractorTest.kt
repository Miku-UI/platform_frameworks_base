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

package com.android.systemui.statusbar.domain.interactor

import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.view.AppearanceRegion
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.statusbar.StatusBarAlwaysUseRegionSampling
import com.android.systemui.statusbar.StatusBarRegionSampling
import com.android.systemui.statusbar.data.repository.fakeStatusBarModeRepository
import com.android.systemui.testKosmos
import com.android.systemui.uimode.data.repository.fakeForceInvertRepository
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarRegionSamplingInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val Kosmos.underTest by Kosmos.Fixture { kosmos.statusBarRegionSamplingInteractor }

    @Test
    @DisableFlags(StatusBarAlwaysUseRegionSampling.FLAG_NAME)
    fun isRegionSamplingEnabled_alwaysUseFlagOff_forceInvertOff_returnsFalse() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isRegionSamplingEnabled)

            kosmos.fakeForceInvertRepository.setForceInvertDark(false)

            assertThat(latest).isFalse()
        }

    @Test
    @EnableFlags(StatusBarRegionSampling.FLAG_NAME)
    @DisableFlags(StatusBarAlwaysUseRegionSampling.FLAG_NAME)
    fun isRegionSamplingEnabled_forceInvertDark_onlyA11yFlagEnabled_returnsTrue() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isRegionSamplingEnabled)

            kosmos.fakeForceInvertRepository.setForceInvertDark(true)

            assertThat(latest).isTrue()
        }

    @Test
    @DisableFlags(StatusBarRegionSampling.FLAG_NAME, StatusBarAlwaysUseRegionSampling.FLAG_NAME)
    fun isRegionSamplingEnabled_forceInvertDark_bothFlagsDisabled_returnsFalse() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isRegionSamplingEnabled)

            kosmos.fakeForceInvertRepository.setForceInvertDark(true)

            assertThat(latest).isFalse()
        }

    @Test
    @EnableFlags(StatusBarAlwaysUseRegionSampling.FLAG_NAME)
    fun isRegionSamplingEnabled_alwaysUseFlagEnabled_forceInvertDark_returnsTrue() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isRegionSamplingEnabled)

            fakeForceInvertRepository.setForceInvertDark(true)

            assertThat(latest).isTrue()
        }

    @Test
    @EnableFlags(StatusBarAlwaysUseRegionSampling.FLAG_NAME)
    fun isRegionSamplingEnabled_alwaysUseFlagEnabled_forceInvertOff_returnsTrue() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isRegionSamplingEnabled)

            fakeForceInvertRepository.setForceInvertDark(false)

            assertThat(latest).isTrue()
        }

    @Test
    @EnableFlags(StatusBarRegionSampling.FLAG_NAME, StatusBarAlwaysUseRegionSampling.FLAG_NAME)
    fun setSampledAppearanceRegions_propagatesNonNullToStatusBarModeRepository() =
        kosmos.runTest {
            val firstRegion = AppearanceRegion(0, Rect())
            val secondRegion = null
            val thirdRegion = AppearanceRegion(APPEARANCE_LIGHT_STATUS_BARS, Rect())
            val appearanceRegions = listOf(firstRegion, secondRegion, thirdRegion)

            underTest.setSampledAppearanceRegions(Display.DEFAULT_DISPLAY, appearanceRegions)

            assertThat(fakeStatusBarModeRepository.defaultDisplay.fakeSampledAppearanceRegions)
                .containsExactly(firstRegion, thirdRegion)
        }

    @Test
    @EnableFlags(StatusBarRegionSampling.FLAG_NAME)
    @DisableFlags(StatusBarAlwaysUseRegionSampling.FLAG_NAME)
    fun setSampledAppearanceRegions_onlyA11yFlagEnabled_propagates() =
        kosmos.runTest {
            val firstRegion = AppearanceRegion(0, Rect())
            val secondRegion = null
            val thirdRegion = AppearanceRegion(APPEARANCE_LIGHT_STATUS_BARS, Rect())
            val appearanceRegions = listOf(firstRegion, secondRegion, thirdRegion)

            underTest.setSampledAppearanceRegions(Display.DEFAULT_DISPLAY, appearanceRegions)

            assertThat(fakeStatusBarModeRepository.defaultDisplay.fakeSampledAppearanceRegions)
                .containsExactly(firstRegion, thirdRegion)
        }

    @Test
    @EnableFlags(StatusBarAlwaysUseRegionSampling.FLAG_NAME)
    @DisableFlags(StatusBarRegionSampling.FLAG_NAME)
    fun setSampledAppearanceRegions_onlyAlwaysFlagEnabled_propagates() =
        kosmos.runTest {
            val firstRegion = AppearanceRegion(0, Rect())
            val secondRegion = null
            val thirdRegion = AppearanceRegion(APPEARANCE_LIGHT_STATUS_BARS, Rect())
            val appearanceRegions = listOf(firstRegion, secondRegion, thirdRegion)

            underTest.setSampledAppearanceRegions(Display.DEFAULT_DISPLAY, appearanceRegions)

            assertThat(fakeStatusBarModeRepository.defaultDisplay.fakeSampledAppearanceRegions)
                .containsExactly(firstRegion, thirdRegion)
        }

    @Test
    @DisableFlags(StatusBarRegionSampling.FLAG_NAME, StatusBarAlwaysUseRegionSampling.FLAG_NAME)
    fun setSampledAppearanceRegions_bothFlagsDisabled_doesNothing() =
        kosmos.runTest {
            val region = AppearanceRegion(0, Rect())

            underTest.setSampledAppearanceRegions(Display.DEFAULT_DISPLAY, listOf(region))

            assertThat(fakeStatusBarModeRepository.defaultDisplay.fakeSampledAppearanceRegions)
                .isNull()
        }
}
