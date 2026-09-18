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

package com.android.systemui.scene.ui.viewmodel

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.reveal.ContainerRevealHaptics
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.domain.MSDLPlayer
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Models haptics UI state for the scene container. */
class SceneContainerHapticsViewModel
@AssistedInject
constructor(
    sceneInteractor: SceneInteractor,
    shadeInteractor: ShadeInteractor,
    private val msdlPlayer: MSDLPlayer,
) : ExclusiveActivatable() {

    /** Should haptics be played by pulling down the shade */
    private val isShadePullHapticsRequired: Flow<Boolean> =
        combine(shadeInteractor.isUserInteracting, sceneInteractor.transitionStateFlow) {
                interacting,
                transitionState ->
                interacting && transitionState.isValidForShadePullHaptics()
            }
            .distinctUntilChanged()

    override suspend fun onActivated() {
        playShadePullHaptics()
    }

    /**
     * Returns a handler for reveal transition haptics in [SceneContainer] for the given
     * [hapticFeedback] target.
     */
    fun getRevealHaptics(hapticFeedback: HapticFeedback): ContainerRevealHaptics {
        return object : ContainerRevealHaptics {
            override fun onRevealThresholdCrossed(revealed: Boolean) {
                if (revealed) {
                    hapticFeedback.performHapticFeedback(
                        HapticFeedbackType.GestureThresholdActivate
                    )
                }
            }
        }
    }

    private suspend fun playShadePullHaptics() {
        isShadePullHapticsRequired
            .filter { it }
            .collect { msdlPlayer.playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR) }
    }

    private fun ObservableTransitionState.isValidForShadePullHaptics(): Boolean {
        val validOrigin =
            isTransitioning(from = Scenes.Gone) || isTransitioning(from = Scenes.Lockscreen)
        val validDestination =
            isTransitioning(to = Scenes.Shade) ||
                isTransitioning(to = Scenes.QuickSettings) ||
                isTransitioning(to = Overlays.QuickSettingsShade) ||
                isTransitioning(to = Overlays.NotificationsShade)
        return validOrigin && validDestination
    }

    @AssistedFactory
    interface Factory {
        fun create(): SceneContainerHapticsViewModel
    }
}
