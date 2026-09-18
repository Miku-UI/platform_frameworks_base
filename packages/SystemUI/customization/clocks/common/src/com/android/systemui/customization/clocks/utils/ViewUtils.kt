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

package com.android.systemui.customization.clocks.utils

import android.view.View
import com.android.systemui.customization.clocks.view.IDigitalClockView
import com.android.systemui.plugins.keyguard.VMeasurePoint
import com.android.systemui.plugins.keyguard.VPointF
import com.android.systemui.plugins.keyguard.VRect
import com.android.systemui.plugins.keyguard.VRectF
import kotlin.math.ceil
import kotlin.math.floor

object ViewUtils {
    fun View.computeLayoutDiff(targetRegion: VRect, isLargeClock: Boolean): VPointF {
        val parent = this.parent
        if (parent is View && parent.isLaidOut && isLargeClock) {
            return targetRegion.center - parent.size / 2f
        }
        return VPointF.ZERO
    }

    val View.position: VPointF
        get() = VPointF(left, top)

    val View.size: VPointF
        get() = VPointF(width, height)

    val View.measuredSize: VPointF
        get() = VPointF(measuredWidth, measuredHeight)

    var View.translation: VPointF
        get() = VPointF(translationX, translationY)
        set(value) {
            translationX = value.x
            translationY = value.y
        }

    val View.measuredSizeAndState: VMeasurePoint
        get() = VMeasurePoint.fromSpecs(measuredWidthAndState, measuredHeightAndState)

    fun View.animateToAlpha(float: Float) {
        this.animate()
            .alpha(float)
            .setDuration(
                this.resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
            )
            .start()
    }

    fun IDigitalClockView.setLeftTopRightBottom(rect: VRectF) {
        (this as View).setLeftTopRightBottom(
            floor(rect.left).toInt(),
            floor(rect.top).toInt(),
            ceil(rect.right).toInt(),
            ceil(rect.bottom).toInt(),
        )
    }

    fun IDigitalClockView.layout(rect: VRectF) {
        (this as View).layout(
            floor(rect.left).toInt(),
            floor(rect.top).toInt(),
            ceil(rect.right).toInt(),
            ceil(rect.bottom).toInt(),
        )
    }
}
