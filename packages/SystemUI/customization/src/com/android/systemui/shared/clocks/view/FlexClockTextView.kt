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

package com.android.systemui.shared.clocks.view

import android.util.AttributeSet
import com.android.systemui.animation.AxisDefinition
import com.android.systemui.animation.GSFAxes
import com.android.systemui.customization.clocks.ClockContext
import com.android.systemui.customization.clocks.utils.FontUtils.set
import com.android.systemui.customization.clocks.view.DigitalClockTextView
import com.android.systemui.customization.clocks.view.DigitalClockTextView.FontVariations
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.shared.clocks.FLEX_CLOCK_ID

class FlexClockTextView(
    clockCtx: ClockContext,
    isLargeClock: Boolean,
    attrs: AttributeSet? = null,
) : DigitalClockTextView(clockCtx, isLargeClock, attrs) {
    private val isLegacyFlex: Boolean
        get() = clockCtx.settings.clockId == FLEX_CLOCK_ID

    private val fixedAodAxes: ClockAxisStyle
        get() {
            return when {
                !isLegacyFlex -> fromAxes(AOD_WEIGHT_AXIS, WIDTH_AXIS)
                isLargeClock -> fromAxes(FLEX_AOD_LARGE_WEIGHT_AXIS, FLEX_AOD_WIDTH_AXIS)
                else -> fromAxes(FLEX_AOD_SMALL_WEIGHT_AXIS, FLEX_AOD_WIDTH_AXIS)
            }
        }

    override fun initializeFontVariations(): FontVariations {
        val roundAxis = if (!isLegacyFlex) ROUND_AXIS else FLEX_ROUND_AXIS
        val lsFontAxes =
            if (!isLegacyFlex) fromAxes(LS_WEIGHT_AXIS, WIDTH_AXIS, ROUND_AXIS, SLANT_AXIS)
            else fromAxes(FLEX_LS_WEIGHT_AXIS, FLEX_LS_WIDTH_AXIS, FLEX_ROUND_AXIS, SLANT_AXIS)
        val aodAxes = fixedAodAxes.copyWith(fromAxes(roundAxis, SLANT_AXIS))
        return buildFontVariations(lsFontAxes, aodAxes)
    }

    override fun updateFontVariations(lsAxes: ClockAxisStyle): FontVariations {
        val aodAxes = lsAxes.copyWith(fixedAodAxes)
        return buildFontVariations(lsAxes, aodAxes)
    }

    private fun buildFontVariations(
        lsAxes: ClockAxisStyle,
        aodAxes: ClockAxisStyle,
    ): FontVariations {
        return FontVariations(
            lockscreen = lsAxes.toFVar(),
            doze = aodAxes.toFVar(),
            fidget = buildAnimationTargetVariation(lsAxes, FIDGET_DISTS).toFVar(),
            chargeLockscreen = buildAnimationTargetVariation(lsAxes, CHARGE_DISTS).toFVar(),
            chargeDoze = buildAnimationTargetVariation(aodAxes, CHARGE_DISTS).toFVar(),
        )
    }

    companion object {
        private val LS_WEIGHT_AXIS = GSFAxes.WEIGHT to 400f
        private val AOD_WEIGHT_AXIS = GSFAxes.WEIGHT to 200f
        private val WIDTH_AXIS = GSFAxes.WIDTH to 85f
        private val ROUND_AXIS = GSFAxes.ROUND to 0f
        private val SLANT_AXIS = GSFAxes.SLANT to 0f

        // Axes for Legacy version of the Flex Clock
        private val FLEX_LS_WEIGHT_AXIS = GSFAxes.WEIGHT to 600f
        private val FLEX_AOD_LARGE_WEIGHT_AXIS = GSFAxes.WEIGHT to 74f
        private val FLEX_AOD_SMALL_WEIGHT_AXIS = GSFAxes.WEIGHT to 133f
        private val FLEX_LS_WIDTH_AXIS = GSFAxes.WIDTH to 100f
        private val FLEX_AOD_WIDTH_AXIS = GSFAxes.WIDTH to 43f
        private val FLEX_ROUND_AXIS = GSFAxes.ROUND to 100f

        private fun fromAxes(vararg axes: Pair<AxisDefinition, Float>): ClockAxisStyle {
            return ClockAxisStyle(axes.map { (def, value) -> def.tag to value }.toMap())
        }
    }
}
