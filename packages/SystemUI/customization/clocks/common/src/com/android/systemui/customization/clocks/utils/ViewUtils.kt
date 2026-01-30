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

import android.graphics.Rect
import android.view.View
import com.android.systemui.plugins.keyguard.VPoint.Companion.center
import com.android.systemui.plugins.keyguard.VPointF
import com.android.systemui.plugins.keyguard.VPointF.Companion.center

object ViewUtils {
    fun View.computeLayoutDiff(targetRegion: Rect, isLargeClock: Boolean): VPointF {
        val parent = this.parent
        if (parent is View && parent.isLaidOut() && isLargeClock) {
            return targetRegion.center - parent.size / 2f
        }
        return VPointF.ZERO
    }

    val View.size: VPointF
        get() = VPointF(width, height)

    val View.measuredSize: VPointF
        get() = VPointF(measuredWidth, measuredHeight)

    fun View.animateToAlpha(float: Float) {
        this.animate()
            .alpha(float)
            .setDuration(
                this.resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
            )
            .start()
    }
}
