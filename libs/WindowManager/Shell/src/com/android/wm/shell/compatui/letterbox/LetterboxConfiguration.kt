/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.annotation.ColorRes
import android.content.Context
import android.graphics.Color
import com.android.internal.R
import com.android.wm.shell.dagger.WMSingleton
import javax.inject.Inject

/** Contains configuration properties for the letterbox implementation in Shell. */
@WMSingleton
class LetterboxConfiguration @Inject constructor(private val context: Context) {
    // Color to use for the solid color letterbox background type.
    private var letterboxBackgroundColorOverride: Color? = null

    // Color resource id for the solid color letterbox background type.
    private var letterboxBackgroundColorResourceIdOverride: Int? = null

    // Default value for corners radius for activities presented in the letterbox mode.
    // Values < 0 will be ignored.
    private val letterboxActivityDefaultCornersRadius: Int

    // The current wallpaper type
    private val letterboxBackgroundType: LetterboxBackgroundMode

    // The override for letterbox background type in case it's different from
    // LETTERBOX_BACKGROUND_OVERRIDE_UNSET
    private var letterboxBackgroundTypeOverride: LetterboxBackgroundMode =
        LetterboxBackgroundMode.OVERRIDE_UNSET

    // Current corners radius for activities presented in the letterbox mode.
    // Values can be modified at runtime and values < 0 will be ignored.
    private var letterboxActivityCornersRadius = 0

    // Blur radius for LETTERBOX_BACKGROUND_WALLPAPER option from getLetterboxBackgroundType().
    // Values <= 0 are ignored and 0 is used instead.
    private var letterboxBackgroundWallpaperBlurRadiusPx: Int

    // Alpha of a black scrim shown over wallpaper letterbox background when
    // LETTERBOX_BACKGROUND_WALLPAPER option is returned from getLetterboxBackgroundType().
    // Values < 0 or >= 1 are ignored and 0.0 (transparent) is used instead.
    private var letterboxBackgroundWallpaperDarkScrimAlpha = 0f

    init {
        letterboxActivityDefaultCornersRadius =
            context.resources.getInteger(R.integer.config_letterboxActivityCornersRadius)
        letterboxActivityCornersRadius = letterboxActivityDefaultCornersRadius

        val backgroundModeId =
            context.getResources().getInteger(R.integer.config_letterboxBackgroundType)
        letterboxBackgroundType = LetterboxBackgroundMode.fromId(backgroundModeId)

        letterboxBackgroundWallpaperBlurRadiusPx =
            context.resources.getDimensionPixelSize(
                R.dimen.config_letterboxBackgroundWallpaperBlurRadius
            )
        letterboxBackgroundWallpaperDarkScrimAlpha =
            context.resources.getFloat(R.dimen.config_letterboxBackgroundWallaperDarkScrimAlpha)
    }

    /** Sets color of letterbox background which is used when using the solid background mode. */
    fun setLetterboxBackgroundColor(color: Color) {
        letterboxBackgroundColorOverride = color
    }

    /** Sets color ID of letterbox background which is used when using the solid background mode. */
    fun setLetterboxBackgroundColorResourceId(@ColorRes colorId: Int) {
        letterboxBackgroundColorResourceIdOverride = colorId
    }

    /** Gets color of letterbox background which is used when the solid color mode is active. */
    fun getLetterboxBackgroundColor(): Color {
        if (letterboxBackgroundColorOverride != null) {
            return letterboxBackgroundColorOverride!!
        }
        val colorId =
            if (letterboxBackgroundColorResourceIdOverride != null) {
                letterboxBackgroundColorResourceIdOverride
            } else {
                R.color.config_letterboxBackgroundColor
            }
        // Query color dynamically because material colors extracted from wallpaper are updated
        // when wallpaper is changed.
        return Color.valueOf(context.getResources().getColor(colorId!!, null))
    }

    /** Resets color of letterbox background to the default. */
    fun resetLetterboxBackgroundColor() {
        letterboxBackgroundColorOverride = null
        letterboxBackgroundColorResourceIdOverride = null
    }

    /** The background color for the Letterbox. */
    fun getBackgroundColorRgbArray(): FloatArray = getLetterboxBackgroundColor().components

    /**
     * Overrides corners radius for activities presented in the letterbox mode. Values < 0, will be
     * ignored and corners of the activity won't be rounded.
     */
    fun setLetterboxActivityCornersRadius(cornersRadius: Int) {
        letterboxActivityCornersRadius = cornersRadius
    }

    /** Resets corners radius for activities presented in the letterbox mode. */
    fun resetLetterboxActivityCornersRadius() {
        letterboxActivityCornersRadius = letterboxActivityDefaultCornersRadius
    }

    /** Overrides the [LetterboxBackgroundMode] specified via ADB command. */
    fun setLetterboxBackgroundType(backgroundType: Int) {
        letterboxBackgroundTypeOverride = LetterboxBackgroundMode.fromId(backgroundType)
    }

    /** Whether corners of letterboxed activities are rounded. */
    fun isLetterboxActivityCornersRounded(): Boolean {
        return getLetterboxActivityCornersRadius() > 0
    }

    /** Gets corners radius for activities presented in the letterbox mode. */
    fun getLetterboxActivityCornersRadius(): Int {
        return maxOf(letterboxActivityCornersRadius, 0)
    }

    /** Gets id for the [LetterboxBackgroundMode] specified via ADB command or the default one. */
    fun getLetterboxBackgroundType(): Int {
        if (letterboxBackgroundTypeOverride != LetterboxBackgroundMode.OVERRIDE_UNSET) {
            return letterboxBackgroundTypeOverride.id
        }
        return letterboxBackgroundType.id
    }

    /**
     * Overrides blur radius for [LETTERBOX_BACKGROUND_WALLPAPER] option from
     * [getLetterboxBackgroundType].
     *
     * If the given value is <= 0, both it and the value of
     * [com.android.internal.R.dimen.config_letterboxBackgroundWallpaperBlurRadius] are ignored and
     * 0 is used instead.
     */
    fun setLetterboxBackgroundWallpaperBlurRadiusPx(radius: Int) {
        letterboxBackgroundWallpaperBlurRadiusPx = radius
    }

    /**
     * Resets blur radius for [LETTERBOX_BACKGROUND_WALLPAPER] option returned by
     * [getLetterboxBackgroundType] to
     * [com.android.internal.R.dimen.config_letterboxBackgroundWallpaperBlurRadius].
     */
    fun resetLetterboxBackgroundWallpaperBlurRadiusPx() {
        letterboxBackgroundWallpaperBlurRadiusPx =
            context.resources.getDimensionPixelSize(
                R.dimen.config_letterboxBackgroundWallpaperBlurRadius
            )
    }

    /**
     * Gets blur radius for [LETTERBOX_BACKGROUND_WALLPAPER] option returned by
     * [getLetterboxBackgroundType].
     */
    fun getLetterboxBackgroundWallpaperBlurRadiusPx(): Int {
        return letterboxBackgroundWallpaperBlurRadiusPx
    }

    /**
     * Resets letterbox background type value depending on the
     * [KEY_ENABLE_LETTERBOX_BACKGROUND_WALLPAPER] build-time and runtime flags.
     *
     * If enabled, the letterbox background type value is set to [LETTERBOX_BACKGROUND_WALLPAPER].
     * When disabled the letterbox background type value comes from
     * [R.integer.config_letterboxBackgroundType].
     */
    fun resetLetterboxBackgroundType() {
        letterboxBackgroundTypeOverride = LetterboxBackgroundMode.OVERRIDE_UNSET
    }

    /**
     * Overrides alpha of a black scrim shown over wallpaper for [LETTERBOX_BACKGROUND_WALLPAPER]
     * option returned from [getLetterboxBackgroundType].
     *
     * If given value is < 0 or >= 1, both it and a value of
     * [com.android.internal.R.dimen.config_letterboxBackgroundWallaperDarkScrimAlpha] are ignored
     * and 0.0 (transparent) is used instead.
     */
    fun setLetterboxBackgroundWallpaperDarkScrimAlpha(alpha: Float) {
        letterboxBackgroundWallpaperDarkScrimAlpha = alpha
    }

    /**
     * Resets alpha of a black scrim shown over wallpaper letterbox background to
     * [com.android.internal.R.dimen.config_letterboxBackgroundWallaperDarkScrimAlpha].
     */
    fun resetLetterboxBackgroundWallpaperDarkScrimAlpha() {
        letterboxBackgroundWallpaperDarkScrimAlpha =
            context.resources.getFloat(R.dimen.config_letterboxBackgroundWallaperDarkScrimAlpha)
    }

    /** Gets alpha of a black scrim shown over wallpaper letterbox background. */
    fun getLetterboxBackgroundWallpaperDarkScrimAlpha(): Float {
        return letterboxBackgroundWallpaperDarkScrimAlpha
    }

    enum class LetterboxBackgroundMode(val id: Int, val asString: String) {
        OVERRIDE_UNSET(-1, "LETTERBOX_BACKGROUND_UNSET"),
        SOLID_COLOR(0, "LETTERBOX_BACKGROUND_SOLID_COLOR"),
        APP_COLOR_BACKGROUND(1, "LETTERBOX_BACKGROUND_APP_COLOR_BACKGROUND"),
        APP_COLOR_BACKGROUND_FLOATING(2, "LETTERBOX_BACKGROUND_APP_COLOR_BACKGROUND_FLOATING"),
        WALLPAPER(3, "LETTERBOX_BACKGROUND_WALLPAPER");

        override fun toString(): String = asString

        companion object {
            // Returns the matching enum, or falls back to OVERRIDE_UNSET (or null)
            fun fromId(id: Int): LetterboxBackgroundMode {
                return entries.find { it.id == id } ?: SOLID_COLOR
            }

            /**
             * @param id The id for background mode
             * @return [true] if the input is a valid background mode id.
             */
            fun validForInput(id: Int): Boolean = id >= SOLID_COLOR.id && id <= WALLPAPER.id
        }
    }
}
