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

package com.android.systemui.keyguard.ui.transitions

import android.util.MathUtils
import com.android.systemui.communal.domain.interactor.CommunalSceneInteractor
import com.android.systemui.keyguard.ui.KeyguardTransitionAnimationFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * {@link GlanceableHubBlurProvider} helps provide a consistent blur experience across glanceable
 * hub transitions by defining a single point where both the exit and entry flows are defined. Note
 * that since these flows are driven by the specific transition animations, a singleton provider
 * cannot be used.
 */
class GlanceableHubBlurProvider
@Inject
constructor(
    transitionAnimation: KeyguardTransitionAnimationFlow.FlowBuilder,
    blurConfig: BlurConfig,
    communalSceneInteractor: CommunalSceneInteractor,
) {
    private var wasInEditModeAtStart = false

    val exitBlurRadius: Flow<Float> =
        transitionAnimation.sharedFlow(
            onStep = {
                if (wasInEditModeAtStart) {
                    blurConfig.minBlurRadiusPx
                } else {
                    MathUtils.lerp(blurConfig.maxBlurRadiusPx, blurConfig.minBlurRadiusPx, it)
                }
            },
            onStart = {
                wasInEditModeAtStart = communalSceneInteractor.editModeState.value != null
                if (wasInEditModeAtStart) {
                    blurConfig.minBlurRadiusPx
                } else {
                    blurConfig.maxBlurRadiusPx
                }
            },
            onFinish = { blurConfig.minBlurRadiusPx },
            onCancel = {
                if (wasInEditModeAtStart) {
                    blurConfig.minBlurRadiusPx
                } else {
                    blurConfig.maxBlurRadiusPx
                }
            },
        )

    val enterBlurRadius: Flow<Float> =
        transitionAnimation.sharedFlow(
            onStep = {
                if (wasInEditModeAtStart) {
                    blurConfig.minBlurRadiusPx
                } else {
                    MathUtils.lerp(blurConfig.minBlurRadiusPx, blurConfig.maxBlurRadiusPx, it)
                }
            },
            onStart = {
                wasInEditModeAtStart = communalSceneInteractor.editModeState.value != null
                blurConfig.minBlurRadiusPx
            },
            onFinish = {
                if (wasInEditModeAtStart) {
                    blurConfig.minBlurRadiusPx
                } else {
                    blurConfig.maxBlurRadiusPx
                }
            },
            onCancel = { blurConfig.minBlurRadiusPx },
        )
}
