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

package com.android.settingslib.spa.widget.ui

import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.android.settingslib.color.R

internal object LottieColorUtils {
    private val DARK_TO_LIGHT_THEME_COLOR_MAP =
        mapOf(
            ".grey200" to R.color.settingslib_color_grey800,
            ".grey600" to R.color.settingslib_color_grey400,
            ".grey800" to R.color.settingslib_color_grey300,
            ".grey900" to R.color.settingslib_color_grey50,
            ".red400" to R.color.settingslib_color_red600,
            ".black" to android.R.color.white,
            ".blue400" to R.color.settingslib_color_blue600,
            ".green400" to R.color.settingslib_color_green600,
            ".green200" to R.color.settingslib_color_green500,
            ".red200" to R.color.settingslib_color_red500,
            ".cream" to R.color.settingslib_color_charcoal,
        )

    @Composable
    private fun getMaterialColorMap(): Map<String, Color> =
        mapOf(
            ".primary" to MaterialTheme.colorScheme.primary,
            ".onPrimary" to MaterialTheme.colorScheme.onPrimary,
            ".primaryContainer" to MaterialTheme.colorScheme.primaryContainer,
            ".onPrimaryContainer" to MaterialTheme.colorScheme.onPrimaryContainer,
            ".primaryInverse" to MaterialTheme.colorScheme.inversePrimary,
            ".primaryFixed" to MaterialTheme.colorScheme.primaryFixed,
            ".primaryFixedDim" to MaterialTheme.colorScheme.primaryFixedDim,
            ".onPrimaryFixed" to MaterialTheme.colorScheme.onPrimaryFixed,
            ".onPrimaryFixedVariant" to MaterialTheme.colorScheme.onPrimaryFixedVariant,
            ".secondary" to MaterialTheme.colorScheme.secondary,
            ".onSecondary" to MaterialTheme.colorScheme.onSecondary,
            ".secondaryContainer" to MaterialTheme.colorScheme.secondaryContainer,
            ".onSecondaryContainer" to MaterialTheme.colorScheme.onSecondaryContainer,
            ".secondaryFixed" to MaterialTheme.colorScheme.secondaryFixed,
            ".secondaryFixedDim" to MaterialTheme.colorScheme.secondaryFixedDim,
            ".onSecondaryFixed" to MaterialTheme.colorScheme.onSecondaryFixed,
            ".onSecondaryFixedVariant" to MaterialTheme.colorScheme.onSecondaryFixedVariant,
            ".tertiary" to MaterialTheme.colorScheme.tertiary,
            ".onTertiary" to MaterialTheme.colorScheme.onTertiary,
            ".tertiaryContainer" to MaterialTheme.colorScheme.tertiaryContainer,
            ".onTertiaryContainer" to MaterialTheme.colorScheme.onTertiaryContainer,
            ".tertiaryFixed" to MaterialTheme.colorScheme.tertiaryFixed,
            ".tertiaryFixedDim" to MaterialTheme.colorScheme.tertiaryFixedDim,
            ".onTertiaryFixed" to MaterialTheme.colorScheme.onTertiaryFixed,
            ".onTertiaryFixedVariant" to MaterialTheme.colorScheme.onTertiaryFixedVariant,
            ".error" to MaterialTheme.colorScheme.error,
            ".onError" to MaterialTheme.colorScheme.onError,
            ".errorContainer" to MaterialTheme.colorScheme.errorContainer,
            ".onErrorContainer" to MaterialTheme.colorScheme.onErrorContainer,
            ".outline" to MaterialTheme.colorScheme.outline,
            ".outlineVariant" to MaterialTheme.colorScheme.outlineVariant,
            ".background" to MaterialTheme.colorScheme.background,
            ".onBackground" to MaterialTheme.colorScheme.onBackground,
            ".surface" to MaterialTheme.colorScheme.surface,
            ".onSurface" to MaterialTheme.colorScheme.onSurface,
            ".surfaceVariant" to MaterialTheme.colorScheme.surfaceVariant,
            ".onSurfaceVariant" to MaterialTheme.colorScheme.onSurfaceVariant,
            ".surfaceInverse" to MaterialTheme.colorScheme.inverseSurface,
            ".onSurfaceInverse" to MaterialTheme.colorScheme.inverseOnSurface,
            ".surfaceBright" to MaterialTheme.colorScheme.surfaceBright,
            ".surfaceDim" to MaterialTheme.colorScheme.surfaceDim,
            ".surfaceContainer" to MaterialTheme.colorScheme.surfaceContainer,
            ".surfaceContainerLow" to MaterialTheme.colorScheme.surfaceContainerLow,
            ".surfaceContainerLowest" to MaterialTheme.colorScheme.surfaceContainerLowest,
            ".surfaceContainerHigh" to MaterialTheme.colorScheme.surfaceContainerHigh,
            ".surfaceContainerHighest" to MaterialTheme.colorScheme.surfaceContainerHighest,
        )

    @Composable
    private fun getDefaultPropertiesList(): List<LottieDynamicProperty<ColorFilter>> = buildList {
        if (!isSystemInDarkTheme()) {
            for ((key, colorRes) in DARK_TO_LIGHT_THEME_COLOR_MAP) {
                add(createColorFilter(key, colorResource(colorRes)))
            }
        }
        for ((key, color) in getMaterialColorMap()) {
            add(createColorFilter(key, color))
        }
    }

    @Composable
    private fun createColorFilter(key: String, color: Color): LottieDynamicProperty<ColorFilter> =
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            keyPath = arrayOf("**", key, "**"),
        ) {
            PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_ATOP)
        }

    @Composable
    fun getDefaultDynamicProperties() =
        rememberLottieDynamicProperties(*getDefaultPropertiesList().toTypedArray())
}
