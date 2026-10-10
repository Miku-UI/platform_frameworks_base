/*
 * Copyright (C) 2026 Miku UI
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

package com.android.systemui.volume.dialog.sliders.ui

import android.view.View

/** Combines overscroll with optional hardware-key motion onto the same slider views. */
class VolumeDialogSliderTranslation(
    private val views: Array<View>,
    private val isVertical: Boolean,
) {
    var overscrollPx: Float = 0f
        set(value) {
            field = value
            apply()
        }

    var keyPressPx: Float = 0f
        set(value) {
            field = value
            apply()
        }

    var shakeXPx: Float = 0f
        set(value) {
            field = value
            apply()
        }

    var shakeYPx: Float = 0f
        set(value) {
            field = value
            apply()
        }

    private fun apply() {
        val translationX =
            if (isVertical) {
                shakeXPx
            } else {
                overscrollPx + shakeXPx
            }
        val translationY =
            if (isVertical) {
                overscrollPx + keyPressPx + shakeYPx
            } else {
                keyPressPx + shakeYPx
            }
        for (view in views) {
            view.translationX = translationX
            view.translationY = translationY
        }
    }
}
