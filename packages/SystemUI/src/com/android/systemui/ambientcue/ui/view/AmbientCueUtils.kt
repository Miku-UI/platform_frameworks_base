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
package com.android.systemui.ambientcue.ui.view

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager.LayoutParams

object AmbientCueUtils {
    fun getAmbientCueLayoutParams(spyTouches: Boolean): LayoutParams {
        return LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            )
            .apply {
                gravity = Gravity.BOTTOM or Gravity.START
                fitInsetsTypes = 0
                isFitInsetsIgnoringVisibility = false
                privateFlags = privateFlags or LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY
                title = "AmbientCue"
                if (spyTouches) {
                    inputFeatures = inputFeatures or LayoutParams.INPUT_FEATURE_SPY
                }
                receiveInsetsIgnoringZOrder = true
            }
    }
}
