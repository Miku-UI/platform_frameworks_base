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

package com.android.systemui.keyguard.ui.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.android.systemui.deviceentry.domain.interactor.AuthRippleInteractor.DwellRipplePhase
import com.android.systemui.keyguard.ui.viewmodel.AuthRippleScrimViewModel
import com.android.systemui.keyguard.ui.viewmodel.AuthRippleScrimViewModel.Companion.DWELL_FADE_OUT_DURATION_MS
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.surfaceeffects.compose.dwellRippleEffect
import com.android.systemui.surfaceeffects.compose.rippleCircleEffect

@Composable
fun AuthRippleScrim(
    viewModelFactory: AuthRippleScrimViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    val authRippleScrimViewModel =
        rememberViewModel(traceName = "AuthRippleScrim") { viewModelFactory.create() }

    Box(modifier = modifier) {
        DwellEffect(authRippleScrimViewModel, Modifier.fillMaxSize())
        UnlockRippleEffect(authRippleScrimViewModel, Modifier.fillMaxSize())
    }
}

/**
 * Renders the UDFPS dwell ripple effect, which is the pulsing out or retracting effect shown when a
 * user places their finger on the UDFPS sensor.
 */
@Composable
private fun DwellEffect(viewModel: AuthRippleScrimViewModel, modifier: Modifier = Modifier) {
    val phase = viewModel.dwellRipplePhase
    if (phase == DwellRipplePhase.IDLE) {
        return
    }
    val targetAlpha = if (phase == DwellRipplePhase.FADE_OUT) 0f else 1f
    val alphaDuration =
        if (phase == DwellRipplePhase.FADE_OUT) DWELL_FADE_OUT_DURATION_MS.toInt() else 0
    val dwellRippleAlpha: Float by
        animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(durationMillis = alphaDuration),
            label = "DwellEffectAlpha",
        )
    if (dwellRippleAlpha > 0f) {
        val isExpanding = viewModel.isDwellExpanding
        viewModel.dwellEffectConfig?.let {
            Box(
                modifier
                    .graphicsLayer { alpha = dwellRippleAlpha }
                    .dwellRippleEffect(isExpanding = isExpanding, dwellEffectConfig = it)
            )
        }
    }
}

/**
 * Renders the full-screen unlock ripple effect that plays upon successful biometric authentication.
 */
@Composable
private fun UnlockRippleEffect(viewModel: AuthRippleScrimViewModel, modifier: Modifier = Modifier) {
    val isEnabled = viewModel.showUnlockRipple
    viewModel.unlockRippleConfig?.let {
        Box(
            modifier
                .fillMaxSize()
                .rippleCircleEffect(
                    shaderConfig = it,
                    isEnabled = isEnabled,
                    onAnimationFinished = { viewModel.finishShowingUnlockRipple() },
                )
        )
    }
}
