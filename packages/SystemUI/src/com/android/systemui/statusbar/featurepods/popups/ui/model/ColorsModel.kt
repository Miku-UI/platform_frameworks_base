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

package com.android.systemui.statusbar.featurepods.popups.ui.model

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.android.systemui.res.R

/**
 * Model representing how the popup chip in the status bar should be colored, accounting for whether
 * the popup is shown/hidden and whether the chip is hovered.
 */
@Immutable
sealed interface ColorsModel {
    /** The color for the background of the chip. */
    @Composable fun chipBackground(isPopupShown: Boolean, colorScheme: ColorScheme): Color

    /** The color for the text and default (non-hovered) icon on the chip. */
    @Composable fun chipContent(isPopupShown: Boolean, colorScheme: ColorScheme): Color

    /** The color to use for the chip outline. */
    @Composable fun chipOutline(isPopupShown: Boolean, colorScheme: ColorScheme): Color

    /** The color to use for the icon */
    @Composable fun icon(isPopupShown: Boolean, isHovered: Boolean, colorScheme: ColorScheme): Color

    /** The background color applied to the icon area when it is hovered. */
    @Composable fun iconBackgroundOnHover(isPopupShown: Boolean, colorScheme: ColorScheme): Color

    /** The default system themed chip colors, changing based on the popup state. */
    data object SystemTheme : ColorsModel {
        @Composable
        override fun chipBackground(isPopupShown: Boolean, colorScheme: ColorScheme): Color =
            if (isPopupShown) colorScheme.primary else colorScheme.surfaceDim

        @Composable
        override fun chipContent(isPopupShown: Boolean, colorScheme: ColorScheme): Color =
            if (isPopupShown) colorScheme.onPrimary else colorScheme.onSurface

        @Composable
        override fun chipOutline(isPopupShown: Boolean, colorScheme: ColorScheme): Color =
            colorScheme.outlineVariant

        @Composable
        override fun icon(
            isPopupShown: Boolean,
            isHovered: Boolean,
            colorScheme: ColorScheme,
        ): Color =
            if (isHovered) {
                chipBackground(isPopupShown = isPopupShown, colorScheme = colorScheme)
            } else {
                chipContent(isPopupShown = isPopupShown, colorScheme = colorScheme)
            }

        @Composable
        override fun iconBackgroundOnHover(isPopupShown: Boolean, colorScheme: ColorScheme): Color =
            if (isPopupShown) colorScheme.onPrimary else colorScheme.onSurface
    }

    /** The colors for the AvControls (Privacy Indicator) Chip */
    data object AvControlsTheme : ColorsModel {
        @Composable private fun privacyGreen() = colorResource(R.color.privacy_chip_background)

        @Composable
        override fun chipBackground(isPopupShown: Boolean, colorScheme: ColorScheme): Color =
            privacyGreen()

        @Composable
        override fun chipOutline(isPopupShown: Boolean, colorScheme: ColorScheme): Color =
            privacyGreen()

        @Composable
        override fun chipContent(isPopupShown: Boolean, colorScheme: ColorScheme): Color =
            colorScheme.onPrimary

        @Composable
        override fun icon(
            isPopupShown: Boolean,
            isHovered: Boolean,
            colorScheme: ColorScheme,
        ): Color = colorScheme.onPrimary

        @Composable
        override fun iconBackgroundOnHover(isPopupShown: Boolean, colorScheme: ColorScheme): Color =
            colorScheme.onPrimary
    }
}
