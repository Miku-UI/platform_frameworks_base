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

package com.android.systemui.deviceentry.ui.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * For views that overlap with the UDFPS accessibility overlay. This view type intercepts touch
 * events within the bounds of the accessibility overlay view, to prevent overlapping / interfering
 * talkback messages.
 */
class UdfpsAccessibilityOverlayOverlappingView(context: Context?, attrs: AttributeSet? = null) :
    View(context, attrs) {

    private var overlappingAccessibilityViewBounds: Rect? = null

    /**
     * Used to set bounds where accessibility feedback from touch events should be delayed to avoid
     * interference with an overlapping view with accessibility feedback.
     */
    fun setOverlappingAccessibilityViewBounds(bounds: Rect?) {
        overlappingAccessibilityViewBounds = bounds
    }

    /**
     * Used to intercept and ignore touch events within the bounds of the UDFPS accessibility
     * overlay.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (
            overlappingAccessibilityViewBounds?.contains(event.x.toInt(), event.y.toInt()) == true
        ) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
}
