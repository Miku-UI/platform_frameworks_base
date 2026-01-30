/*
 * Copyright (C) 2022 The Android Open Source Project
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

@file:OptIn(ExperimentalTextApi::class)

package com.android.settingslib.spa.framework.theme

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.android.settingslib.spa.framework.compose.rememberContext

internal interface SettingsFontFamily {
    val brand: FontFamily
    val plain: FontFamily

    val displayLargeEmphasized: FontFamily
    val displayMediumEmphasized: FontFamily
    val displaySmallEmphasized: FontFamily
    val headlineLargeEmphasized: FontFamily
    val headlineMediumEmphasized: FontFamily
    val headlineSmallEmphasized: FontFamily
    val titleLargeEmphasized: FontFamily
    val titleMediumEmphasized: FontFamily
    val titleSmallEmphasized: FontFamily
    val bodyLargeEmphasized: FontFamily
    val bodyMediumEmphasized: FontFamily
    val bodySmallEmphasized: FontFamily
    val labelLargeEmphasized: FontFamily
    val labelMediumEmphasized: FontFamily
    val labelSmallEmphasized: FontFamily
}

private fun Context.getSettingsFontFamily() = object : SettingsFontFamily {
    override val brand = getFontFamily(
        configFontFamilyNormal = "config_headlineFontFamily",
        configFontFamilyMedium = "config_headlineFontFamilyMedium",
    )
    override val plain = getFontFamily(
        configFontFamilyNormal = "config_bodyFontFamily",
        configFontFamilyMedium = "config_bodyFontFamilyMedium",
    )
    override val displayLargeEmphasized = fontFamily("variable-display-large-emphasized")
    override val displayMediumEmphasized = fontFamily("variable-display-medium-emphasized")
    override val displaySmallEmphasized = fontFamily("variable-display-small-emphasized")
    override val headlineLargeEmphasized = fontFamily("variable-headline-large-emphasized")
    override val headlineMediumEmphasized = fontFamily("variable-headline-medium-emphasized")
    override val headlineSmallEmphasized = fontFamily("variable-headline-small-emphasized")
    override val titleLargeEmphasized = fontFamily("variable-title-large-emphasized")
    override val titleMediumEmphasized = fontFamily("variable-title-medium-emphasized")
    override val titleSmallEmphasized = fontFamily("variable-title-small-emphasized")
    override val bodyLargeEmphasized = fontFamily("variable-body-large-emphasized")
    override val bodyMediumEmphasized = fontFamily("variable-body-medium-emphasized")
    override val bodySmallEmphasized = fontFamily("variable-body-small-emphasized")
    override val labelLargeEmphasized = fontFamily("variable-label-large-emphasized")
    override val labelMediumEmphasized = fontFamily("variable-label-medium-emphasized")
    override val labelSmallEmphasized = fontFamily("variable-label-small-emphasized")

    private fun fontFamily(name: String): FontFamily =
        FontFamily(Font(DeviceFontFamilyName(name)))
}

private fun Context.getFontFamily(
    configFontFamilyNormal: String,
    configFontFamilyMedium: String,
): FontFamily {
    val fontFamilyNormal = getAndroidConfig(configFontFamilyNormal)
    val fontFamilyMedium = getAndroidConfig(configFontFamilyMedium)
    if (fontFamilyNormal.isEmpty() || fontFamilyMedium.isEmpty()) return FontFamily.Default
    return FontFamily(
        Font(DeviceFontFamilyName(fontFamilyNormal), FontWeight.Normal),
        Font(DeviceFontFamilyName(fontFamilyMedium), FontWeight.Medium),
    )
}

private fun Context.getAndroidConfig(configName: String): String {
    @SuppressLint("DiscouragedApi")
    val configId = resources.getIdentifier(configName, "string", "android")
    return resources.getString(configId)
}

@Composable
internal fun rememberSettingsFontFamily(): SettingsFontFamily {
    return rememberContext(Context::getSettingsFontFamily)
}
