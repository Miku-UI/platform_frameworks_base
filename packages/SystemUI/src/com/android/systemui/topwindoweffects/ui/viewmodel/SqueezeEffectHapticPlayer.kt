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

import android.os.VibrationEffect
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.statusbar.VibratorHelper
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SqueezeEffectHapticPlayer
@AssistedInject
constructor(
    private val vibratorHelper: VibratorHelper,
    @Application private val applicationScope: CoroutineScope,
) {

    private val primitiveDurations =
        vibratorHelper.getPrimitiveDurations(
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            VibrationEffect.Composition.PRIMITIVE_TICK,
        )

    private fun buildZoomOutHaptics(durationMillis: Int) =
        SqueezeEffectHapticsBuilder.createZoomOutEffect(
            lowTickDuration = primitiveDurations[0],
            quickRiseDuration = primitiveDurations[1],
            tickDuration = primitiveDurations[2],
            effectDuration = durationMillis,
        )

    private val lppIndicationEffect = SqueezeEffectHapticsBuilder.createLppIndicatorEffect()

    private val defaultAssistantEffect =
        SqueezeEffectHapticsBuilder.createDefaultAssistantEffect(
            supportsRiseEffect = primitiveDurations[1] != 0 && primitiveDurations[2] != 0
        )

    private var vibrationJob: Job? = null

    fun startZoomOutEffect(durationMillis: Int) {
        cancel()
        val zoomOutHaptics = buildZoomOutHaptics(durationMillis)
        if (zoomOutHaptics.initialDelay <= 0) {
            vibrate(zoomOutHaptics)
        } else {
            vibrationJob =
                applicationScope.launch {
                    delay(zoomOutHaptics.initialDelay.toLong())
                    if (isActive) {
                        vibrate(zoomOutHaptics)
                    }
                    vibrationJob = null
                }
        }
    }

    fun playRumble(rumbleDuration: Int) {
        val effect =
            SqueezeEffectHapticsBuilder.createRumbleEffect(
                rumbleDuration = rumbleDuration,
                lowTickDuration = primitiveDurations[0],
            )
        vibrate(effect)
    }

    fun playLppIndicator() {
        vibratorHelper.cancel()
        vibrate(lppIndicationEffect)
    }

    fun playDefaultAssistantEffect() = vibrate(defaultAssistantEffect)

    fun cancel() {
        vibrationJob?.cancel()
        vibrationJob = null
        vibratorHelper.cancel()
    }

    private fun vibrate(effect: SqueezeEffectHapticsBuilder.SqueezeEffectHaptics?) {
        effect?.let {
            vibratorHelper.vibrate(it.vibration, SqueezeEffectHapticsBuilder.VIBRATION_ATTRIBUTES)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): SqueezeEffectHapticPlayer
    }

    companion object {
        private const val TAG = "SqueezeEffectHapticPlayer"
    }
}
