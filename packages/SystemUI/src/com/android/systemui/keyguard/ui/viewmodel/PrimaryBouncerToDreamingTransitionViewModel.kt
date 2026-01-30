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

package com.android.systemui.keyguard.ui.viewmodel

import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.domain.interactor.FromPrimaryBouncerTransitionInteractor
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState.DREAMING
import com.android.systemui.keyguard.shared.model.KeyguardState.PRIMARY_BOUNCER
import com.android.systemui.keyguard.shared.model.ScrimAlpha
import com.android.systemui.keyguard.ui.KeyguardTransitionAnimationFlow
import com.android.systemui.keyguard.ui.transitions.BlurConfig
import com.android.systemui.keyguard.ui.transitions.PrimaryBouncerTransition
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.statusbar.phone.ScrimState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SysUISingleton
class PrimaryBouncerToDreamingTransitionViewModel
@Inject
constructor(blurConfig: BlurConfig, animationFlow: KeyguardTransitionAnimationFlow) :
    PrimaryBouncerTransition {
    private val transitionAnimation =
        animationFlow
            .setup(
                duration = FromPrimaryBouncerTransitionInteractor.TO_DREAMING_DURATION,
                edge = Edge.create(from = Overlays.Bouncer, to = DREAMING),
            )
            .setupWithoutSceneContainer(edge = Edge.create(from = PRIMARY_BOUNCER, to = DREAMING))

    /**
     * Bouncer container alpha. The dream starts underneath the bouncer so we want to fade the
     * bouncer away as the dream launches.
     */
    val bouncerAlpha: Flow<Float> =
        transitionAnimation.sharedFlow(
            duration = FromPrimaryBouncerTransitionInteractor.TO_DREAMING_DURATION,
            onStep = { 1f - it },
        )

    override val windowBlurRadius: Flow<Float> =
        transitionAnimation.sharedFlowWithShade(
            onStep = { progress, isShadeExpanded ->
                if (isShadeExpanded && Flags.notificationShadeBlur()) {
                    blurConfig.maxBlurRadiusPx
                } else {
                    transitionProgressToBlurRadius(
                        blurConfig.maxBlurRadiusPx,
                        endBlurRadius = blurConfig.minBlurRadiusPx,
                        transitionProgress = progress,
                    )
                }
            },
            onFinish = { isShadeExpanded ->
                if (isShadeExpanded && Flags.notificationShadeBlur()) {
                    blurConfig.maxBlurRadiusPx
                } else {
                    blurConfig.minBlurRadiusPx
                }
            },
        )

    override val notificationBlurRadius: Flow<Float> =
        transitionAnimation.immediatelyTransitionTo(blurConfig.minBlurRadiusPx)

    // Fade out behind scrim as it's on top of the dream.
    val scrimAlpha: Flow<ScrimAlpha> =
        transitionAnimation
            .sharedFlow(
                duration = FromPrimaryBouncerTransitionInteractor.TO_DREAMING_DURATION,
                onStep = { step -> (1 - step) * ScrimState.BOUNCER.behindAlpha },
                onFinish = { 0f },
                onCancel = { ScrimState.BOUNCER.behindAlpha },
            )
            .map { ScrimAlpha(behindAlpha = it) }
}
