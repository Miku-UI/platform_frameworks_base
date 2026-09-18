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

package com.android.systemui.customization.clocks

import android.view.animation.Interpolator
import com.android.systemui.animation.TextAnimator

interface TextStyle {
    /** scale factor applied to the default clock's font size. */
    val fontSizeScale: Float
}

interface FontTextStyle : TextStyle {
    val lineHeight: Float?
    val fontFeatureSettings: String
    val transitionDuration: Long
    val transitionInterpolator: Interpolator?
}

data class FontTextStyleImpl(
    override val lineHeight: Float? = null,
    override val fontSizeScale: Float = 1f,
    override val fontFeatureSettings: String = "",
    override val transitionDuration: Long = TextAnimator.DEFAULT_ANIMATION_DURATION,
    override val transitionInterpolator: Interpolator? = null,
) : FontTextStyle
