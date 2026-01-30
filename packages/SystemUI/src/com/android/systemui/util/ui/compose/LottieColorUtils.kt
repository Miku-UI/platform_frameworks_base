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

package com.android.systemui.util.ui.compose

import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieDynamicProperties
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.android.settingslib.widget.LottieColorUtils

/**
 * Kotlin util class which dynamically changes the color of tags in a lottie json file between Dark
 * Theme (DT) and Light Theme (LT). This class assumes the json file is for Dark Theme.
 */
object LottieColorUtils {
    /**
     * Returns [com.airbnb.lottie.compose.LottieDynamicProperties] object for composables to apply
     * dynamic colors based on DT vs. LT, and optionally material colors based on the value of
     * includeMaterialColorProperties.
     *
     * @param includeMaterialColorProperties whether to include material color properties
     */
    @Composable
    fun getDynamicProperties(includeMaterialColorProperties: Boolean): LottieDynamicProperties? {
        val context = LocalContext.current
        val allProperties = mutableListOf<LottieDynamicProperty<ColorFilter>>()

        if (!LottieColorUtils.isDarkMode(context)) {
            val dynamicColorProperties =
                LottieColorUtils.DARK_TO_LIGHT_THEME_COLOR_MAP.map { (key, colorResId) ->
                    val color = context.getColor(colorResId)
                    rememberLottieDynamicProperty(
                        property = LottieProperty.COLOR_FILTER,
                        value = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP),
                        keyPath = arrayOf("**", key, "**"),
                    )
                }
            allProperties.addAll(dynamicColorProperties)
        }

        if (includeMaterialColorProperties) {
            val materialColorProperties =
                LottieColorUtils.MATERIAL_COLOR_MAP.map { (key, colorResId) ->
                    val color = context.getColor(colorResId)
                    rememberLottieDynamicProperty(
                        property = LottieProperty.COLOR_FILTER,
                        value = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP),
                        keyPath = arrayOf("**", key, "**"),
                    )
                }
            allProperties.addAll(materialColorProperties)
        }

        return rememberLottieDynamicProperties(*allProperties.toTypedArray())
    }
}
