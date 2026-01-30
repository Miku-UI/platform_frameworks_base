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
package com.android.systemui.statusbar.phone.domain.interactor

import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher.DarkChange
import com.android.systemui.statusbar.phone.data.repository.fakeDarkIconRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DarkIconInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.darkIconInteractor }

    @Test
    fun isDarkTheme_noDarkIconAreas_darkIconIntensityLightIcons_isDarkTheme() =
        kosmos.runTest {
            val isAreaDark by collectLastValue(underTest.isAreaDark(DEFAULT_DISPLAY_ID))
            val iconBounds = Rect(10, 10, 20, 20)

            fakeDarkIconRepository.darkState(DEFAULT_DISPLAY_ID).value =
                DarkChange(arrayListOf(), DARK_INTENSITY_LIGHT_ICONS, TINT)

            // The dark icon areas list is empty, which means the entire status bar is one color.
            // The status bar is requesting light icons, so this icon should be light,
            // which means the theme should be dark.
            assertThat(isAreaDark!!.isDarkTheme(iconBounds)).isTrue()
        }

    @Test
    fun isDarkTheme_noDarkIconAreas_darkIconIntensityDarkIcons_isLightTheme() =
        kosmos.runTest {
            val isAreaDark by collectLastValue(underTest.isAreaDark(DEFAULT_DISPLAY_ID))
            val iconBounds = Rect(10, 10, 20, 20)

            fakeDarkIconRepository.darkState(DEFAULT_DISPLAY_ID).value =
                DarkChange(arrayListOf(), DARK_INTENSITY_DARK_ICONS, TINT)

            // The dark icon areas list is empty, which means the entire status bar is one color.
            // The status bar is requesting dark icons, so this icon should be dark,
            // which means the theme should be light.
            assertThat(isAreaDark!!.isDarkTheme(iconBounds)).isFalse()
        }

    @Test
    fun isDarkTheme_nonEmptyDarkIconAreas_iconInDarkIconArea_isLightTheme() =
        kosmos.runTest {
            val isAreaDark by collectLastValue(underTest.isAreaDark(DEFAULT_DISPLAY_ID))
            val darkIconArea = Rect(0, 0, 100, 100)
            val iconBounds = Rect(10, 10, 20, 20)

            fakeDarkIconRepository.darkState(DEFAULT_DISPLAY_ID).value =
                DarkChange(arrayListOf(darkIconArea), DARK_INTENSITY_DARK_ICONS, TINT)

            // The icon is within the dark icon area, so the icon should be dark,
            // which means the theme should be light.
            assertThat(isAreaDark!!.isDarkTheme(iconBounds)).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_DARK_ICON_INTERACTOR_MIXED_FIX)
    fun isDarkTheme_nonEmptyDarkIconAreas_iconOutsideDarkIconArea_isDarkTheme() =
        kosmos.runTest {
            val isAreaDark by collectLastValue(underTest.isAreaDark(DEFAULT_DISPLAY_ID))
            val darkIconArea = Rect(0, 0, 100, 100)
            val iconBounds = Rect(110, 10, 120, 20)

            fakeDarkIconRepository.darkState(DEFAULT_DISPLAY_ID).value =
                DarkChange(arrayListOf(darkIconArea), DARK_INTENSITY_DARK_ICONS, TINT)

            // The icon is not within the dark icon area, so the icon should be light,
            // which means the theme should be dark.
            assertThat(isAreaDark!!.isDarkTheme(iconBounds)).isTrue()
        }

    companion object {
        private const val DEFAULT_DISPLAY_ID = 0
        private const val TINT = 0
        private const val DARK_INTENSITY_DARK_ICONS = 1f
        private const val DARK_INTENSITY_LIGHT_ICONS = 0f
    }
}
