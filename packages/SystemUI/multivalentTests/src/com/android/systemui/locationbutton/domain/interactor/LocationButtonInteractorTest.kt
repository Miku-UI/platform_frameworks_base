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
package com.android.systemui.locationbutton.domain.interactor

import android.app.permissionui.LocationButtonRequest
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.graphics.ColorUtils
import com.android.internal.util.ContrastColorUtil
import com.android.settingslib.Utils
import com.android.systemui.SysuiTestCase
import com.android.systemui.locationbutton.data.repository.locationButtonRepository
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LocationButtonInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val underTest = kosmos.locationButtonInteractor
    private val repository = kosmos.locationButtonRepository

    @Test
    fun setButtonState_withRequest_usesFallbacks() {
        val sessionId = 1
        val request = LocationButtonRequest.Builder(100, 100, Configuration()).build()
        // No optional properties set

        underTest.setButtonState(sessionId, request, 1.0f)

        val state = underTest.getButtonState(sessionId)!!
        val density = 1.0f
        assertThat(state.paddingLeft).isEqualTo(4)
        assertThat(state.paddingTop).isEqualTo(4)
        assertThat(state.paddingRight).isEqualTo(4)
        assertThat(state.paddingBottom).isEqualTo(4)
        assertThat(state.strokeWidth).isEqualTo(0)
        assertThat(state.cornerRadius).isNull()
        assertThat(state.pressedCornerRadius).isNull()
        assertThat(state.textResId).isNull()

        val defaultBg =
            Utils.getColorAttrDefaultColor(mContext, com.android.internal.R.attr.colorAccentPrimary)
        val defaultText =
            Utils.getColorAttrDefaultColor(mContext, com.android.internal.R.attr.textColorOnAccent)

        val expectedBgColor = Color(ColorUtils.setAlphaComponent(defaultBg, 255))
        val expectedTextColor =
            Color(
                ContrastColorUtil.ensureContrast(
                    defaultText,
                    expectedBgColor.toArgb(),
                    MIN_CONTRAST_RATIO,
                )
            )

        assertThat(state.backgroundColor).isEqualTo(expectedBgColor)
        assertThat(state.textColor).isEqualTo(expectedTextColor)
        assertThat(state.iconTint).isEqualTo(expectedTextColor)
        assertThat(state.strokeColor).isEqualTo(expectedBgColor)
    }

    @Test
    fun setButtonState_withRequest_usesExplicitValues() {
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration())
                .setPaddingLeft(5)
                .setStrokeWidth(2)
                .setCornerRadius(15f)
                .setBackgroundColor(Color.Blue.toArgb())
                .setTextColor(Color.Yellow.toArgb())
                .setTextType(
                    android.app.permissionui.LocationButtonSession.TEXT_TYPE_PRECISE_LOCATION
                )
                .build()

        underTest.setButtonState(sessionId, request, 1.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.paddingLeft).isEqualTo(5)
        assertThat(state.strokeWidth).isEqualTo(2)
        assertThat(state.cornerRadius).isEqualTo(15f)
        assertThat(state.backgroundColor.toArgb()).isEqualTo(Color.Blue.toArgb())
        assertThat(state.textColor.toArgb()).isEqualTo(Color.Yellow.toArgb())
        assertThat(state.textResId).isEqualTo(R.string.location_button_text_precise_location)
    }

    @Test
    fun setButtonState_validatesDimensionsAndSetsState() {
        // Min 48dp = 48px at 1.0x density
        val sessionId = 1
        val request = LocationButtonRequest.Builder(10, 50, Configuration()).build()

        underTest.setButtonState(sessionId, request, 1.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.width).isEqualTo(48) // Clamped to 48
    }

    @Test
    fun setButtonState_widthValid_remainsUnchanged() {
        val sessionId = 1
        val request = LocationButtonRequest.Builder(100, 50, Configuration()).build()

        underTest.setButtonState(sessionId, request, 1.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.width).isEqualTo(100)
    }

    @Test
    fun setButtonState_widthTooSmall_highDensity_clampedToScaledMin() {
        // Min 48dp = 96px at 2.0x density
        val sessionId = 1
        // Input 80px is < 96px
        val request = LocationButtonRequest.Builder(80, 100, Configuration()).build()

        underTest.setButtonState(sessionId, request, 2.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.width).isEqualTo(96)
    }

    @Test
    fun setButtonState_heightTooSmall_clampedToMin() {
        val sessionId = 1
        val request = LocationButtonRequest.Builder(100, 10, Configuration()).build()

        underTest.setButtonState(sessionId, request, 1.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.height).isEqualTo(48)
    }

    @Test
    fun setButtonState_heightTooLarge_clampedToMax() {
        // Max 136dp = 408px at 3.0x density
        val sessionId = 1
        val request = LocationButtonRequest.Builder(100, 500, Configuration()).build()

        underTest.setButtonState(sessionId, request, 3.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.height).isEqualTo(408)
    }

    @Test
    fun setBackgroundColor_forcesOpaque() {
        val sessionId = 1
        val request = createTestRequest()
        underTest.setButtonState(sessionId, request, 1.0f)
        val transparentRed = Color.Red.copy(alpha = 0.5f).toArgb()

        underTest.setBackgroundColor(sessionId, transparentRed)

        val state = underTest.getButtonState(sessionId)!!
        // Alpha should be forced to 255 (opaque)
        assertThat(state.backgroundColor.toArgb()).isEqualTo(Color.Red.toArgb())
    }

    @Test
    fun setButtonState_heightValid_remainsUnchanged() {
        val sessionId = 1
        val request = LocationButtonRequest.Builder(100, 100, Configuration()).build()

        underTest.setButtonState(sessionId, request, 1.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.height).isEqualTo(100)
    }

    @Test
    fun setButtonState_heightValidation_highDensity_scalesCorrectly() {
        // Density 2.0x: Min 48dp = 96px, Max 136dp = 272px
        val sessionId = 1

        // Test Min Clamp
        val minRequest = LocationButtonRequest.Builder(100, 50, Configuration()).build()
        underTest.setButtonState(sessionId, minRequest, 2.0f)
        assertThat(underTest.getButtonState(sessionId)!!.height).isEqualTo(96)

        // Test Max Clamp
        val maxRequest = LocationButtonRequest.Builder(100, 300, Configuration()).build()
        underTest.setButtonState(sessionId, maxRequest, 2.0f)
        assertThat(underTest.getButtonState(sessionId)!!.height).isEqualTo(272)
    }

    @Test
    fun setButtonState_paddingTooLarge_highDensity_clampedToScaledMax() {
        // Max 8dp = 24px at 3.0x density
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration())
                .setPaddingLeft(100)
                .setPaddingTop(100)
                .setPaddingRight(100)
                .setPaddingBottom(100)
                .build()

        underTest.setButtonState(sessionId, request, 3.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.paddingLeft).isEqualTo(24)
        assertThat(state.paddingTop).isEqualTo(24)
        assertThat(state.paddingRight).isEqualTo(24)
        assertThat(state.paddingBottom).isEqualTo(24)
    }

    @Test
    fun setButtonState_paddingTooSmall_highDensity_clampedToScaledMin() {
        // Min 4dp = 12px at 3.0x density
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration())
                .setPaddingLeft(1)
                .setPaddingTop(1)
                .setPaddingRight(1)
                .setPaddingBottom(1)
                .build()

        underTest.setButtonState(sessionId, request, 3.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.paddingLeft).isEqualTo(12)
        assertThat(state.paddingTop).isEqualTo(12)
        assertThat(state.paddingRight).isEqualTo(12)
        assertThat(state.paddingBottom).isEqualTo(12)
    }

    @Test
    fun setButtonState_paddingValid_remainsUnchanged() {
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration()).setPaddingLeft(5).build()

        underTest.setButtonState(sessionId, request, 1.0f)

        assertThat(underTest.getButtonState(sessionId)!!.paddingLeft).isEqualTo(5)
    }

    @Test
    fun setButtonState_paddingHighDensity_clampedToScaledMax() {
        // Max 8dp = 12px at 1.5x density
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration()).setPaddingLeft(20).build()

        underTest.setButtonState(sessionId, request, 1.5f)

        assertThat(underTest.getButtonState(sessionId)!!.paddingLeft).isEqualTo(12)
    }

    @Test
    fun setButtonState_strokeWidthTooLarge_clampedToMax() {
        // Max 3dp = 3px at 1.0x density
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration()).setStrokeWidth(10).build()

        underTest.setButtonState(sessionId, request, 1.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.strokeWidth).isEqualTo(MAX_STROKE_WIDTH_DP)
    }

    @Test
    fun setButtonState_strokeWidthValid_remainsUnchanged() {
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration()).setStrokeWidth(2).build()

        underTest.setButtonState(sessionId, request, 1.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.strokeWidth).isEqualTo(2)
    }

    @Test
    fun setButtonState_negativeDimensions_clampedToMin() {
        val sessionId = 1
        val request = LocationButtonRequest.Builder(-10, -10, Configuration()).build()

        underTest.setButtonState(sessionId, request, 1.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.width).isEqualTo(48)
        assertThat(state.height).isEqualTo(48)
    }

    @Test
    fun setButtonState_negativePaddingAndStroke_clampedToMin() {
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration())
                .setPaddingLeft(-5)
                .setStrokeWidth(-2)
                .setCornerRadius(-10f)
                .build()

        underTest.setButtonState(sessionId, request, 1.0f)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.paddingLeft).isEqualTo(4)
        assertThat(state.strokeWidth).isEqualTo(0)
        assertThat(state.cornerRadius).isEqualTo(0f)
    }

    @Test
    fun setConfiguration_reValidatesDimensionsAgainstNewDensity() {
        // High density: Max 8dp = 24px
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(200, 200, Configuration()).setPaddingLeft(24).build()
        underTest.setButtonState(sessionId, request, 3.0f)
        assertThat(underTest.getButtonState(sessionId)!!.paddingLeft).isEqualTo(24)

        // Change to low density: Max 8dp = 8px. The 24px should be clamped.
        val newConfig = Configuration().apply { densityDpi = 160 }
        underTest.setConfiguration(sessionId, newConfig, 1.0f)

        assertThat(underTest.getButtonState(sessionId)!!.paddingLeft).isEqualTo(8)
    }

    @Test
    fun setSize_validatesAndUpdatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestRequest(), 1.0f)

        // Set size too small (10x10) -> Should clamp to 48x48
        underTest.setSize(sessionId, 10, 10)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.width).isEqualTo(48)
        assertThat(state.height).isEqualTo(48)
    }

    @Test
    fun removeButtonState_removesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestRequest(), 1.0f)
        underTest.removeButtonState(sessionId)

        assertThat(underTest.getButtonState(sessionId)).isNull()
    }

    @Test
    fun setCornerRadius_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestRequest(), 1.0f)
        val newRadius = 25f
        underTest.setCornerRadius(sessionId, newRadius)

        assertThat(underTest.getButtonState(sessionId)!!.cornerRadius).isEqualTo(newRadius)
    }

    @Test
    fun setBackgroundColor_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestRequest(), 1.0f)
        val newColor = Color.Red.toArgb()
        underTest.setBackgroundColor(sessionId, newColor)

        assertThat(underTest.getButtonState(sessionId)!!.backgroundColor.toArgb())
            .isEqualTo(newColor)
    }

    @Test
    fun setTextColor_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestRequest(), 1.0f)
        val newColor = Color.Cyan.toArgb()
        underTest.setTextColor(sessionId, newColor)

        assertThat(underTest.getButtonState(sessionId)!!.textColor.toArgb()).isEqualTo(newColor)
    }

    @Test
    fun setIconTint_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestRequest(), 1.0f)
        val newColor = Color.Green.toArgb()
        underTest.setIconTint(sessionId, newColor)

        assertThat(underTest.getButtonState(sessionId)!!.iconTint.toArgb()).isEqualTo(newColor)
    }

    @Test
    fun setTextType_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestRequest(), 1.0f)
        val textType = android.app.permissionui.LocationButtonSession.TEXT_TYPE_PRECISE_LOCATION
        underTest.setTextType(sessionId, textType)

        assertThat(underTest.getButtonState(sessionId)!!.textResId)
            .isEqualTo(R.string.location_button_text_precise_location)
    }

    @Test
    fun setStrokeColor_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestRequest(), 1.0f)
        val newColor = Color.Yellow.toArgb()
        underTest.setStrokeColor(sessionId, newColor)

        assertThat(underTest.getButtonState(sessionId)!!.strokeColor.toArgb()).isEqualTo(newColor)
    }

    @Test
    fun clearRepositoryState_clearsState() {
        underTest.setButtonState(1, createTestRequest(), 1.0f)
        underTest.setButtonState(2, createTestRequest(), 1.0f)
        underTest.clearRepositoryState()

        assertThat(underTest.getButtonState(1)).isNull()
        assertThat(underTest.getButtonState(2)).isNull()
    }

    @Test
    fun setButtonState_preservesHighContrastColorsAndUpdatesState() {
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration())
                .setBackgroundColor(Color.Black.toArgb())
                .setTextColor(Color.White.toArgb())
                .setIconTint(Color.White.toArgb())
                .setStrokeColor(Color.White.toArgb())
                .build()

        underTest.setButtonState(sessionId, request, 1.0f)

        val state = underTest.getButtonState(sessionId)!!
        val backgroundArgb = state.backgroundColor.toArgb()
        assertThat(state.backgroundColor).isEqualTo(Color.Black)
        assertThat(state.textColor).isEqualTo(Color.White)
        assertThat(state.iconTint).isEqualTo(Color.White)
        assertThat(state.strokeColor).isEqualTo(Color.White)
        assertThat(ContrastColorUtil.calculateContrast(state.iconTint.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
        assertThat(ContrastColorUtil.calculateContrast(state.textColor.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
    }

    @Test
    fun setButtonState_adjustsLowContrastIconTintAndTextColorsAndUpdatesState() {
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration())
                .setBackgroundColor(Color.White.toArgb())
                .setTextColor(COLOR_LIGHT_GRAY)
                .setIconTint(COLOR_LIGHT_GRAY)
                .setStrokeColor(COLOR_LIGHT_GRAY)
                .build()
        // Pre-validate that the initial color contrast ratios are insufficient
        assertThat(ContrastColorUtil.calculateContrast(request.iconTint, request.backgroundColor))
            .isLessThan(MIN_CONTRAST_RATIO)
        assertThat(ContrastColorUtil.calculateContrast(request.textColor, request.backgroundColor))
            .isLessThan(MIN_CONTRAST_RATIO)

        underTest.setButtonState(sessionId, request, 1.0f)

        val state = underTest.getButtonState(sessionId)!!
        val backgroundArgb = state.backgroundColor.toArgb()
        val expectedIconTint =
            ContrastColorUtil.ensureContrast(request.iconTint, backgroundArgb, MIN_CONTRAST_RATIO)
        assertThat(state.iconTint.toArgb()).isEqualTo(expectedIconTint)
        assertThat(ContrastColorUtil.calculateContrast(state.iconTint.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
        val expectedTextColor =
            ContrastColorUtil.ensureContrast(request.textColor, backgroundArgb, MIN_CONTRAST_RATIO)
        assertThat(state.textColor.toArgb()).isEqualTo(expectedTextColor)
        assertThat(ContrastColorUtil.calculateContrast(state.textColor.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
        assertThat(state.strokeColor.toArgb()).isEqualTo(COLOR_LIGHT_GRAY)
    }

    @Test
    fun setConfiguration_adjustsContrast() {
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration())
                .setBackgroundColor(Color.White.toArgb())
                .setTextColor(Color.White.toArgb())
                .setIconTint(Color.White.toArgb())
                .build()
        // Inject an invalid contrast setup into the interactor.
        underTest.setButtonState(sessionId, request, 1.0f)

        val config = Configuration()
        underTest.setConfiguration(sessionId, config, config.densityDpi / 160f)

        val state = underTest.getButtonState(sessionId)!!
        // In the current implementation, colors ARE adjusted during setConfiguration.
        assertThat(state.backgroundColor).isEqualTo(Color.White)
        assertThat(state.textColor.toArgb()).isNotEqualTo(Color.White.toArgb())
        assertThat(state.iconTint.toArgb()).isNotEqualTo(Color.White.toArgb())
        assertThat(
                ContrastColorUtil.calculateContrast(state.textColor.toArgb(), Color.White.toArgb())
            )
            .isAtLeast(MIN_CONTRAST_RATIO)
        assertThat(
                ContrastColorUtil.calculateContrast(state.iconTint.toArgb(), Color.White.toArgb())
            )
            .isAtLeast(MIN_CONTRAST_RATIO)
    }

    @Test
    fun setBackgroundColor_adjustsLowContrastIconTintAndTextColorsAndUpdatesState() {
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration())
                .setBackgroundColor(Color.Black.toArgb())
                .setTextColor(Color.White.toArgb())
                .setIconTint(Color.White.toArgb())
                .build()
        underTest.setButtonState(sessionId, request, 1.0f)
        val newLowContrastBackgroundColorArgb = Color.White.toArgb()

        underTest.setBackgroundColor(sessionId, newLowContrastBackgroundColorArgb)

        val state = underTest.getButtonState(sessionId)!!
        val backgroundArgb = state.backgroundColor.toArgb()
        assertThat(backgroundArgb).isEqualTo(newLowContrastBackgroundColorArgb)
        val expectedIconTint =
            ContrastColorUtil.ensureContrast(request.iconTint, backgroundArgb, MIN_CONTRAST_RATIO)
        assertThat(state.iconTint.toArgb()).isEqualTo(expectedIconTint)
        assertThat(ContrastColorUtil.calculateContrast(state.iconTint.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
        val expectedTextColor =
            ContrastColorUtil.ensureContrast(request.textColor, backgroundArgb, MIN_CONTRAST_RATIO)
        assertThat(state.textColor.toArgb()).isEqualTo(expectedTextColor)
        assertThat(ContrastColorUtil.calculateContrast(state.textColor.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
    }

    @Test
    fun setIconTint_adjustsLowContrastIconTintColorAndUpdatesState() {
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration())
                .setBackgroundColor(Color.White.toArgb())
                .setIconTint(Color.Black.toArgb())
                .build()
        underTest.setButtonState(sessionId, request, 1.0f)
        val newLowContrastIconTintArgb = Color.White.toArgb()

        underTest.setIconTint(sessionId, newLowContrastIconTintArgb)

        val state = underTest.getButtonState(sessionId)!!
        val backgroundArgb = state.backgroundColor.toArgb()
        val expectedIconTint =
            ContrastColorUtil.ensureContrast(
                newLowContrastIconTintArgb,
                backgroundArgb,
                MIN_CONTRAST_RATIO,
            )
        assertThat(state.iconTint.toArgb()).isEqualTo(expectedIconTint)
        assertThat(ContrastColorUtil.calculateContrast(state.iconTint.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
    }

    @Test
    fun setTextColor_adjustsLowContrastTextColorAndUpdatesState() {
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration())
                .setBackgroundColor(Color.White.toArgb())
                .setTextColor(Color.Black.toArgb())
                .build()
        underTest.setButtonState(sessionId, request, 1.0f)
        val newLowContrastTextColor = Color.White

        underTest.setTextColor(sessionId, newLowContrastTextColor.toArgb())

        val state = underTest.getButtonState(sessionId)!!
        val backgroundArgb = state.backgroundColor.toArgb()
        val expectedTextColor =
            ContrastColorUtil.ensureContrast(
                newLowContrastTextColor.toArgb(),
                backgroundArgb,
                MIN_CONTRAST_RATIO,
            )
        assertThat(state.textColor.toArgb()).isEqualTo(expectedTextColor)
        assertThat(ContrastColorUtil.calculateContrast(state.textColor.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
    }

    @Test
    fun setStrokeColor_setStrokeColorAndUpdatesState() {
        val sessionId = 1
        val request =
            LocationButtonRequest.Builder(100, 100, Configuration())
                .setBackgroundColor(Color.White.toArgb())
                .build()
        underTest.setButtonState(sessionId, request, 1.0f)
        val newStrokeColor = COLOR_LIGHT_GRAY

        underTest.setStrokeColor(sessionId, newStrokeColor)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.strokeColor.toArgb()).isEqualTo(newStrokeColor)
    }

    private fun createTestRequest(): LocationButtonRequest {
        return LocationButtonRequest.Builder(100, 100, Configuration())
            .setBackgroundColor(Color.Black.toArgb())
            .setTextColor(Color.White.toArgb())
            .setIconTint(Color.White.toArgb())
            .setStrokeColor(Color.White.toArgb())
            .build()
    }

    private companion object {
        const val MAX_STROKE_WIDTH_DP = 3
        const val MIN_TOUCH_TARGET_DP = 48
        const val DENSITY_DP = 240
        private const val MIN_CONTRAST_RATIO = 4.5
        private val COLOR_LIGHT_GRAY = Color(0xFFBBBBBB).toArgb()
        const val DENSITY_DPI = 160
    }
}
