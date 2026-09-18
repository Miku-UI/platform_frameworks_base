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

package com.android.systemui.keyguard.ui.viewmodel

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.animation.Interpolator
import androidx.core.animation.LinearInterpolator
import androidx.core.animation.PathInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.android.internal.graphics.ColorUtils
import com.android.settingslib.Utils
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.deviceentry.domain.interactor.AuthRippleInteractor
import com.android.systemui.deviceentry.domain.interactor.AuthRippleInteractor.DwellRipplePhase
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.surfaceeffects.core.dwellrippleeffect.AnimationConfig
import com.android.systemui.surfaceeffects.core.dwellrippleeffect.DwellEffectConfig
import com.android.systemui.surfaceeffects.core.ripple.RippleAnimationConfig
import com.android.systemui.surfaceeffects.core.ripple.RippleShader
import com.android.systemui.surfaceeffects.core.ripple.RippleShader.FadeParams
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

class AuthRippleScrimViewModel
@AssistedInject
constructor(
    @Main private val sysuiContext: Context,
    private val authRippleInteractor: AuthRippleInteractor,
    keyguardInteractor: KeyguardInteractor,
) : HydratedActivatable() {
    private val color =
        keyguardInteractor.isDozing.map { isDozing ->
            val color =
                if (isDozing) {
                    Color.WHITE
                } else {
                    Utils.getColorAttrDefaultColor(sysuiContext, R.attr.wallpaperTextColorAccent)
                }
            ColorUtils.setAlphaComponent(color, LOCKSCREEN_COLOR_ALPHA)
        }

    private val unlockRippleOrigin = authRippleInteractor.sensorOrigin

    /**
     * Whether to show an unlock ripple animation.
     *
     * Consume it by calling [onAnimationFinished].
     */
    var showUnlockRipple: Boolean by mutableStateOf(false)
        private set

    val unlockRippleConfig: RippleAnimationConfig? by
        combine(unlockRippleOrigin, color) { origin: PointF, color: Int ->
                RippleAnimationConfig(
                    rippleShape = RippleShader.RippleShape.CIRCLE,
                    duration = RIPPLE_ANIMATION_DURATION_MS,
                    centerX = origin.x,
                    centerY = origin.y,
                    color = color,
                    sparkleStrength = RIPPLE_SPARKLE_STRENGTH,
                    baseRingFadeParams = baseRingFadeParams,
                    centerFillFadeParams = centerFillFadeParams,
                )
            }
            .hydratedStateOf(null)

    val dwellEffectConfig: DwellEffectConfig? by
        combine(
                authRippleInteractor.udfpsLocation,
                authRippleInteractor.udfpsRadius.map { DWELL_RADIUS_SCALE * it },
                color,
            ) { origin: PointF, dwellRadius: Float, color: Int ->
                DwellEffectConfig(
                    centerX = origin.x,
                    centerY = origin.y,
                    maxRadius = dwellRadius,
                    color = color,
                    distortionStrength = DwellEffectConfig.DEFAULT_DISTORTION_STRENGTH,
                    expandingAnimationConfig =
                        AnimationConfig(
                            duration =
                                (DWELL_PULSE_DURATION_MS + DWELL_EXPAND_DURATION_MS).toFloat(),
                            interpolator = dwellPulseExpandInterpolator,
                        ),
                    retractingAnimationConfig =
                        AnimationConfig(
                            duration = DWELL_RETRACT_DURATION_MS.toFloat(),
                            interpolator = dwellRetractInterpolator,
                        ),
                )
            }
            .hydratedStateOf(null)

    val dwellRipplePhase: DwellRipplePhase by
        authRippleInteractor.dwellRipplePhase.hydratedStateOf(DwellRipplePhase.IDLE)

    val isDwellExpanding: Boolean by
        authRippleInteractor.dwellRipplePhase
            .filter { it == DwellRipplePhase.RETRACT || it == DwellRipplePhase.PULSE_OUT }
            .map { it == DwellRipplePhase.PULSE_OUT }
            .hydratedStateOf(false)

    /**
     * Notifies that an unlock ripple effect animation has finished running. Must be called by the
     * UI to report that an unlock ripple animation, which was started due to [showUnlockRipple]
     * being `true`, has finished.
     */
    fun finishShowingUnlockRipple() {
        showUnlockRipple = false
        authRippleInteractor.resetAdbTriggeredRipple()
    }

    override suspend fun onActivated() {
        super.onActivated()
        authRippleInteractor.showUnlockRipple.collect { showUnlockRipple = true }
    }

    override suspend fun onDeactivated() {
        super.onDeactivated()
        finishShowingUnlockRipple()
    }

    @AssistedFactory
    interface Factory {
        fun create(): AuthRippleScrimViewModel
    }

    companion object {
        const val LOCKSCREEN_COLOR_ALPHA = 62

        const val DWELL_PULSE_DURATION_MS = 100L
        const val DWELL_EXPAND_DURATION_MS = 2000L - DWELL_PULSE_DURATION_MS
        const val DWELL_RETRACT_DURATION_MS = 400L
        const val DWELL_FADE_OUT_DURATION_MS = 83L

        const val RIPPLE_ANIMATION_DURATION_MS: Long = 800

        private const val DWELL_PULSE_TIME_FRACTION =
            (DWELL_PULSE_DURATION_MS.toFloat()) /
                (DWELL_PULSE_DURATION_MS + DWELL_EXPAND_DURATION_MS)
        private const val DWELL_PULSE_PROGRESS_TARGET = 0.8f

        // Calculated constants for segment management
        private const val EXPAND_TIME_FRACTION = 1f - DWELL_PULSE_TIME_FRACTION
        private const val EXPAND_PROGRESS_RANGE = 1f - DWELL_PULSE_PROGRESS_TARGET

        val dwellRetractInterpolator = PathInterpolator(.05f, .93f, .1f, 1f)

        val dwellPulseExpandInterpolator =
            object : Interpolator {
                private val linearInterpolator = LinearInterpolator()
                private val easeOutInterpolator = LinearOutSlowInInterpolator()

                override fun getInterpolation(input: Float): Float {
                    if (input <= DWELL_PULSE_TIME_FRACTION) {
                        val pulseTime = input / DWELL_PULSE_TIME_FRACTION
                        val interpolatedValue = linearInterpolator.getInterpolation(pulseTime)
                        return interpolatedValue * DWELL_PULSE_PROGRESS_TARGET
                    } else {
                        val expandTime = (input - DWELL_PULSE_TIME_FRACTION) / EXPAND_TIME_FRACTION
                        val interpolatedValue = easeOutInterpolator.getInterpolation(expandTime)
                        return DWELL_PULSE_PROGRESS_TARGET +
                            (interpolatedValue * EXPAND_PROGRESS_RANGE)
                    }
                }
            }

        val baseRingFadeParams =
            FadeParams(fadeInStart = 0f, fadeInEnd = .2f, fadeOutStart = .2f, fadeOutEnd = 1f)
        val centerFillFadeParams =
            FadeParams(fadeInStart = 0f, fadeInEnd = .15f, fadeOutStart = .15f, fadeOutEnd = .56f)
        const val RIPPLE_SPARKLE_STRENGTH: Float = 0.3f
        const val DWELL_RADIUS_SCALE = 1.5F
        const val TAG = "AuthRippleScrimViewModel"
    }
}
