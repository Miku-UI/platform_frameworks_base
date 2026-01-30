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

package com.android.systemui.topwindoweffects.ui.viewmodel

import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.VibrationEffect.EFFECT_HEAVY_CLICK
import android.util.Log

object SqueezeEffectHapticsBuilder {

    private const val TAG = "SqueezeEffectHapticsBuilder"
    private const val RISE_TO_TICK_DELAY = 50 // in milliseconds
    private const val LOW_TICK_SCALE = 0.09f
    private const val QUICK_RISE_SCALE = 0.25f
    private const val TICK_SCALE = 1f
    private const val CLICK_SCALE = 0.8f

    val VIBRATION_ATTRIBUTES =
        VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK).build()

    fun createRumbleEffect(rumbleDuration: Int, lowTickDuration: Int): SqueezeEffectHaptics? {
        // If the lowTickDuration is zero, the effect is not supported
        if (lowTickDuration == 0) {
            Log.d(TAG, "The LOW_TICK, primitive is not supported. No rumble added")
            return null
        }
        val composition = VibrationEffect.startComposition()
        addRumble(rumbleDuration, lowTickDuration, composition)
        return SqueezeEffectHaptics(composition.compose())
    }

    fun createZoomOutEffect(
        lowTickDuration: Int,
        quickRiseDuration: Int,
        tickDuration: Int,
        effectDuration: Int,
    ): SqueezeEffectHaptics {
        // If a primitive is not supported, the duration will be 0
        val isInvocationEffectSupported =
            lowTickDuration != 0 && quickRiseDuration != 0 && tickDuration != 0

        if (!isInvocationEffectSupported) {
            Log.d(
                TAG,
                """
                    The LOW_TICK, TICK and/or QUICK_RISE primitives are not supported.
                    Using EFFECT_HEAVY_CLICK as a fallback."
                """
                    .trimIndent(),
            )
            // We use the full invocation duration as a delay so that we play the
            // HEAVY_CLICK fallback in sync with the end of the squeeze effect
            return SqueezeEffectHaptics(
                initialDelay = effectDuration,
                vibration = VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK),
            )
        }

        val riseEffectDuration = quickRiseDuration + RISE_TO_TICK_DELAY + tickDuration
        if (effectDuration < riseEffectDuration) {
            Log.d(
                TAG,
                """
                The rise effect($riseEffectDuration ms) is longer than the total zoom-out effect
                ($effectDuration ms). Using EFFECT_HEAVY_CLICK as a fallback.
                """
                    .trimIndent(),
            )
            return SqueezeEffectHaptics(
                initialDelay = effectDuration,
                vibration = VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK),
            )
        }

        val composition = VibrationEffect.startComposition()

        // Rumble towards the end of the zoom-out
        val zoomOutRumbleDuration = effectDuration - riseEffectDuration
        addRumble(zoomOutRumbleDuration, lowTickDuration, composition)

        // Final rise effect
        addQuickRiseTickEffect(composition)

        return SqueezeEffectHaptics(composition.compose())
    }

    /**
     * Add a rumble to a vibration composition. The rumble is a composition of LOW_TICK primitives.
     * This assumes that the device can play the LOW_TICK primitive.
     */
    private fun addRumble(rumbleDuration: Int, lowTickDuration: Int, composition: Composition) {
        val nLowTicks = (rumbleDuration / lowTickDuration).coerceAtLeast(minimumValue = 0)
        repeat(nLowTicks) {
            composition.addPrimitive(
                Composition.PRIMITIVE_LOW_TICK,
                /*scale=*/ LOW_TICK_SCALE,
                /*delay=*/ 0,
            )
        }
    }

    /**
     * Add a quick rise and a tick to a vibration composition. This assumes that the QUICK_RISE and
     * TICK primitives are supported.
     */
    private fun addQuickRiseTickEffect(composition: Composition) {
        composition.addPrimitive(Composition.PRIMITIVE_QUICK_RISE, QUICK_RISE_SCALE, /* delay= */ 0)
        composition.addPrimitive(Composition.PRIMITIVE_TICK, TICK_SCALE, RISE_TO_TICK_DELAY)
    }

    fun createLppIndicatorEffect(): SqueezeEffectHaptics {
        val composition = VibrationEffect.startComposition()
        composition.addPrimitive(
            Composition.PRIMITIVE_CLICK,
            /*scale=*/ CLICK_SCALE,
            /*delay=*/ 0,
        )
        return SqueezeEffectHaptics(composition.compose())
    }

    fun createDefaultAssistantEffect(supportsRiseEffect: Boolean): SqueezeEffectHaptics {
        if (supportsRiseEffect) {
            val composition = VibrationEffect.startComposition()
            addQuickRiseTickEffect(composition)
            return SqueezeEffectHaptics(composition.compose())
        } else {
            return SqueezeEffectHaptics(VibrationEffect.get(EFFECT_HEAVY_CLICK))
        }
    }

    data class SqueezeEffectHaptics(val initialDelay: Int, val vibration: VibrationEffect) {
        constructor(vibration: VibrationEffect) : this(initialDelay = 0, vibration)
    }
}
