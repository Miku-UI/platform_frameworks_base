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

package com.android.settingslib.spa.widget.preference

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.framework.compose.contentDescription
import com.android.settingslib.spa.framework.compose.highlightBackground
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsShape
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.widget.card.SettingsCard
import com.android.settingslib.spa.widget.card.SettingsCardContent

enum class StatusBannerLevel {
    GENERIC,
    LOW,
    MEDIUM,
    HIGH
}

/**
 * The widget model for [StatusBannerPreference] widget.
 */
interface StatusBannerPreferenceModel {
    /**
     * The title of this [Preference].
     */
    val title: String

    /**
     * The content description of [title].
     */
    val titleContentDescription: String?
        get() = null

    /**
     * The icon of this [Preference].
     *
     * Default is `null` which means no icon.
     */
    val icon: @Composable (() -> ImageVector)?
        get() = null

    val iconLevel: StatusBannerLevel get() = StatusBannerLevel.GENERIC
}

/**
 * Preference widget.
 *
 * Data is provided through [StatusBannerPreferenceModel].
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun StatusBannerPreference(
    model: StatusBannerPreferenceModel,
) {
    SettingsCard {
        SettingsCardContent {
            val surfaceBright = MaterialTheme.colorScheme.surfaceBright
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(SettingsSpace.small1)
                        .semantics(mergeDescendants = true) {}
                        .heightIn(min = SettingsDimension.preferenceMinHeight)
                        .highlightBackground(
                            originalColor = surfaceBright,
                            shape = SettingsShape.CornerExtraSmall2,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(model.icon, model.iconLevel)
                Spacer(modifier = Modifier.size(SettingsSpace.extraSmall6, 0.dp))
                Text(
                    text = model.title,
                    modifier = Modifier
                        .padding(vertical = SettingsDimension.paddingTiny)
                        .contentDescription(model.titleContentDescription),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun Icon(icon: @Composable (() -> ImageVector)?, iconLevel: StatusBannerLevel) {
    Box(
        modifier = Modifier
            .requiredSize(68.dp)
            .background(color = backgroundColor(iconLevel), shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val finalIcon = if (icon == null) defaultIcon(iconLevel) else icon()
        if (finalIcon != null) {
            Image(
                imageVector = finalIcon,
                contentDescription = "",
                modifier = Modifier.requiredSize(SettingsSpace.medium5),
                colorFilter = ColorFilter.tint(tintColor(iconLevel))
            )
        }
    }
}

@Composable
private fun backgroundColor(iconLevel: StatusBannerLevel): Color {
    return when (iconLevel) {
        StatusBannerLevel.GENERIC -> MaterialTheme.colorScheme.surface
        StatusBannerLevel.LOW -> Color(0xFFDDF8D8)
        StatusBannerLevel.MEDIUM -> Color(0xFFFFF2B4)
        StatusBannerLevel.HIGH -> Color(0xFFFFECEE)
    }
}

@Composable
private fun defaultIcon(iconLevel: StatusBannerLevel): ImageVector? {
    return when (iconLevel) {
        StatusBannerLevel.GENERIC -> null
        StatusBannerLevel.LOW, StatusBannerLevel.MEDIUM, StatusBannerLevel.HIGH -> Icons.Filled.GppMaybe
    }
}

@Composable
private fun tintColor(iconLevel: StatusBannerLevel): Color {
    return when (iconLevel) {
        StatusBannerLevel.GENERIC -> MaterialTheme.colorScheme.surface
        StatusBannerLevel.LOW -> Color(0xFF1AA64A)
        StatusBannerLevel.MEDIUM -> Color(0xFFFCBD00)
        StatusBannerLevel.HIGH -> Color(0xFFDB372D)
    }
}

@Preview
@Composable
private fun StatusBannerPreferencePreview() {
    SettingsTheme {
        StatusBannerPreference(model = object : StatusBannerPreferenceModel {
            override val title = "Alarm Volume"
            override val iconLevel = StatusBannerLevel.LOW
        })
    }
}