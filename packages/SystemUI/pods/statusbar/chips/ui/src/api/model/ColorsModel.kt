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

package com.android.systemui.statusbar.chips.ui.model

import android.annotation.ColorRes
import android.content.Context
import android.content.res.ColorStateList
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import com.android.internal.util.ContrastColorUtil
import com.android.systemui.res.R

/** Model representing how the chip in the status bar should be colored. */
sealed interface ColorsModel {
    /** The color for the background of the chip. */
    fun background(context: Context): ColorStateList

    /** The color for the text (and icon) on the chip. */
    @ColorInt fun text(context: Context): Int

    /** The color to use for the chip outline, or null if the chip shouldn't have an outline. */
    @ColorInt fun outline(context: Context): Int?

    /** The chip should match the theme's primary accent color. */
    data object AccentThemed : ColorsModel {
        override fun background(context: Context): ColorStateList =
            ColorStateList.valueOf(
                context.getColor(com.android.internal.R.color.materialColorPrimaryFixedDim)
            )

        override fun text(context: Context) =
            context.getColor(com.android.internal.R.color.materialColorOnPrimaryFixed)

        override fun outline(context: Context) = null
    }

    /** The chip should match the system theme main color. */
    data object SystemThemed : ColorsModel {
        override fun background(context: Context): ColorStateList =
            ColorStateList.valueOf(
                context.getColor(com.android.internal.R.color.materialColorSurfaceDim)
            )

        override fun text(context: Context) =
            context.getColor(com.android.internal.R.color.materialColorOnSurface)

        override fun outline(context: Context) =
            // Outline is required on the SystemThemed chip to guarantee the chip doesn't completely
            // blend in with the background.
            context.getColor(com.android.internal.R.color.materialColorOutlineVariant)
    }

    /**
     * The chip should match the system theme main color, but its individual colors can be tweaked
     * by choosing other color resources. Note that the colors may be adjusted to ensure sufficient
     * contrast between text and background.
     */
    data class SystemThemedWithOverride(
        @param:ColorRes private val backgroundRes: Int? = null,
        @param:ColorRes @property:VisibleForTesting val textRes: Int? = null,
        @param:ColorRes private val outlineRes: Int? = null,
    ) : ColorsModel {

        override fun background(context: Context): ColorStateList =
            if (backgroundRes != null) context.getColorStateList(backgroundRes)
            else SystemThemed.background(context)

        override fun text(context: Context): Int {
            val textColor =
                if (textRes != null) context.getColor(textRes) else SystemThemed.text(context)

            // If FG or BG is nondefault, ensure contrast (assumes it's built-in in SystemTheme).
            if (textRes != null || backgroundRes != null) {
                // This isn't fully correct, but given that all other ColorsModel variants use ONE
                // background color, it is sufficient. It could be impossible to find contrast with
                // the color for all possible background states anyway!
                val backgroundColor = background(context).defaultColor
                return ContrastColorUtil.ensureContrast(textColor, backgroundColor, TEXT_CONTRAST)
            } else {
                return textColor
            }
        }

        override fun outline(context: Context): Int =
            if (outlineRes != null) context.getColor(outlineRes) else SystemThemed.outline(context)
    }

    /** The chip should have the given background color and primary text color. */
    data class Custom(val backgroundColorInt: Int, val primaryTextColorInt: Int) : ColorsModel {
        override fun background(context: Context): ColorStateList =
            ColorStateList.valueOf(backgroundColorInt)

        override fun text(context: Context): Int = primaryTextColorInt

        override fun outline(context: Context) = null
    }

    /** The chip should have a red background with white text. */
    data object Red : ColorsModel {
        override fun background(context: Context): ColorStateList {
            return ColorStateList.valueOf(context.getColor(R.color.GM2_red_700))
        }

        override fun text(context: Context) = context.getColor(android.R.color.white)

        override fun outline(context: Context) = null
    }
}

/** Minimal contrast for text-on-background. */
private const val TEXT_CONTRAST = 4.5
