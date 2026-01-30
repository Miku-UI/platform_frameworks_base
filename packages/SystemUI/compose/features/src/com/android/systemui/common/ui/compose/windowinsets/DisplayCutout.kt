/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.systemui.common.ui.compose.windowinsets

import android.view.DisplayCutout as ViewDisplayCutout
import kotlin.math.abs

/**
 * Represents the global position of the bounds for the display cutout for this display.
 *
 * Important: The bounds are expressed in raw pixels (and not dips) because these bounds should be
 * used either during layout or drawing but *not* during composition. This is because insets are
 * computed after composition but before layout. Moreover, these insets can be animated and we don't
 * want to recompose every frame.
 */
data class DisplayCutout(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val location: CutoutLocation = CutoutLocation.NONE,
    /**
     * The original `DisplayCutout` for the `View` world; only use this when feeding it back to a
     * `View`.
     */
    val viewDisplayCutoutKeyguardStatusBarView: ViewDisplayCutout? = null,
) {
    val width: Int = abs(right - left)

    val height: Int = abs(bottom - top)
}

enum class CutoutLocation {
    NONE,
    CENTER,
    LEFT,
    RIGHT,
}
