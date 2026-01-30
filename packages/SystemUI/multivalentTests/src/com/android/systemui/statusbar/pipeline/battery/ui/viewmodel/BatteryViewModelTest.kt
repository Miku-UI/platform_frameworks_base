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

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // Required for bodyMediumEmphasized style
class BatteryViewModelTest : SysuiTestCase() {

    @get:Rule val rule = createComposeRule()

    @Test
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun getStatusBarBatteryHeight_flagDisabled_scaleIsOne_returnsDefaultHeight() {
        overrideResource(R.dimen.status_bar_icon_scale_factor, 1.0f)

        val height = BatteryViewModel.getStatusBarBatteryHeight(context)

        assertThat(height.value).isEqualTo(13f)
    }

    @Test
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun getStatusBarBatteryHeight_flagDisabled_scaleIsTwo_returnsDefaultHeight() {
        overrideResource(R.dimen.status_bar_icon_scale_factor, 2.0f)

        val height = BatteryViewModel.getStatusBarBatteryHeight(context)

        assertThat(height.value).isEqualTo(13f)
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun getStatusBarBatteryHeight_flagEnabled_scaleIsOne_returnsDefaultHeight() {
        overrideResource(R.dimen.status_bar_icon_scale_factor, 1.0f)

        val height = BatteryViewModel.getStatusBarBatteryHeight(context)

        assertThat(height.value).isEqualTo(13f)
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun getStatusBarBatteryHeight_flagEnabled_scaleIsTwo_returnsScaledHeight() {
        overrideResource(R.dimen.status_bar_icon_scale_factor, 2.0f)

        val height = BatteryViewModel.getStatusBarBatteryHeight(context)

        assertThat(height.value).isEqualTo(26f)
    }

    @Test
    @DisableFlags(Flags.FLAG_FIX_SHADE_HEADER_WRONG_ICON_SIZE)
    fun getStatusBarBatteryTextStyle_flagOff_scaleIsTwo_returnsDefautFontSize() {
        overrideResource(R.dimen.status_bar_icon_scale_factor, 2.0f)

        var baseFontSize: Float? = null
        var actualTextStyle: TextStyle? = null
        rule.setContent {
            baseFontSize = MaterialTheme.typography.bodyMediumEmphasized.fontSize.value
            actualTextStyle = BatteryViewModel.getStatusBarBatteryTextStyle(context)
        }
        assertThat(actualTextStyle!!.fontSize.value).isEqualTo(baseFontSize)
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_SHADE_HEADER_WRONG_ICON_SIZE)
    fun getStatusBarBatteryTextStyle_scaleIsOne_returnsDefaultFontSize() {
        overrideResource(R.dimen.status_bar_icon_scale_factor, 1.0f)

        var baseFontSize: Float? = null
        var actualTextStyle: TextStyle? = null
        rule.setContent {
            baseFontSize = MaterialTheme.typography.bodyMediumEmphasized.fontSize.value
            actualTextStyle = BatteryViewModel.getStatusBarBatteryTextStyle(context)
        }
        assertThat(actualTextStyle!!.fontSize.value).isEqualTo(baseFontSize)
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_SHADE_HEADER_WRONG_ICON_SIZE)
    fun getStatusBarBatteryTextStyle_scaleIsTwo_returnsScaledFontSize() {
        overrideResource(R.dimen.status_bar_icon_scale_factor, 2.0f)

        var baseFontSize: Float? = null
        var actualTextStyle: TextStyle? = null
        rule.setContent {
            baseFontSize = MaterialTheme.typography.bodyMediumEmphasized.fontSize.value
            actualTextStyle = BatteryViewModel.getStatusBarBatteryTextStyle(context)
        }
        assertThat(actualTextStyle!!.fontSize.value).isEqualTo(baseFontSize!! * 2)
    }
}
