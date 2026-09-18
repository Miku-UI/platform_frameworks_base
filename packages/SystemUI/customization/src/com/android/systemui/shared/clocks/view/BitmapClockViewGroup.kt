/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.shared.clocks.view

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.core.view.children
import com.android.systemui.customization.clocks.R
import com.android.systemui.customization.clocks.utils.ViewUtils.measuredSize
import com.android.systemui.customization.clocks.view.DigitalClockViewGroup
import com.android.systemui.plugins.keyguard.VMeasurePoint
import com.android.systemui.plugins.keyguard.VPointF
import com.android.systemui.plugins.keyguard.VRectF
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import com.android.systemui.shared.clocks.FlexClockContext
import java.util.Locale

@SuppressLint("ViewConstructor")
class BitmapClockViewGroup(clockCtx: FlexClockContext) :
    DigitalClockViewGroup<BitmapClockView>(clockCtx) {

    override val children: Sequence<BitmapClockView>
        get() = (this as ViewGroup).children.filterIsInstance<BitmapClockView>()

    override fun calculateSize(measureSpec: VMeasurePoint): VPointF {
        // This is where the parent decides how big the whole clock is.
        // We assume 2 digits wide, 2 digits tall for the large clock
        val yBuffer = context.resources.getDimensionPixelSize(R.dimen.clock_vertical_digit_buffer)
        return maxChildSize * VPointF(2f, 2f) + VPointF(0f, yBuffer.toFloat())
    }

    override fun getChildFrame(child: BitmapClockView): VRectF {
        val yBuffer = context.resources.getDimensionPixelSize(R.dimen.clock_vertical_digit_buffer)

        // This math places the 4 bitmaps in a 2x2 grid
        var offset =
            maxChildSize.run {
                when (child.id) {
                    ClockViewIds.HOUR_FIRST_DIGIT -> VPointF.ZERO
                    ClockViewIds.HOUR_SECOND_DIGIT -> VPointF(x, 0f)
                    ClockViewIds.MINUTE_FIRST_DIGIT -> VPointF(0f, y + yBuffer)
                    ClockViewIds.MINUTE_SECOND_DIGIT -> VPointF(x, y + yBuffer)
                    else -> VPointF.ZERO
                }
            }
        return VRectF.fromTopLeft(offset, child.measuredSize)
    }

    override fun refreshTime() {
        children.forEach { it.refreshText() }
    }

    override fun onLocaleChanged(locale: Locale) {}

    override fun animateDoze(isDozing: Boolean, isAnimated: Boolean) {
        // tell the children to update their alpha/doze state
        children.forEach { it.dozeFraction = if (isDozing) 1f else 0f }
    }

    override fun updateAxes(axes: ClockAxisStyle, isAnimated: Boolean) {}

    override fun animateCharge() {}

    override fun onFontSettingChanged(fontSizePx: Float) {}

    override fun updateColor(lockscreenColor: Int, aodColor: Int) {}
}
