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

package com.android.systemui.deviceentry.domain.interactor

import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.bouncer.domain.interactor.SimBouncerInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * A restricted mode where the lockscreen content will not be visible. Rather, the user must
 * authenticate to continue. One example of this state is when a SIM PIN/PUK is required.
 */
@SysUISingleton
class RestrictedModeInteractor
@Inject
constructor(
    private val simBouncerInteractor: Lazy<SimBouncerInteractor>,
    private val sceneInteractor: Lazy<SceneInteractor>,
    private val powerInteractor: PowerInteractor,
) {

    /** Whether the restricted mode is currently active. */
    val isActive: StateFlow<Boolean> = simBouncerInteractor.get().isAnySimSecure

    /** Filter user actions that attempt to hide the bouncer when on lockscreen */
    fun filteredUserActions(
        unfiltered: Flow<Map<UserAction, UserActionResult>>
    ): Flow<Map<UserAction, UserActionResult>> {
        return combine(isActive, unfiltered) { isActive, unfilteredUserActions ->
            if (isActive) {
                unfilteredUserActions.filterValues { actionResult ->
                    when (actionResult) {
                        is UserActionResult.HideOverlay -> {
                            !(actionResult.overlay == Overlays.Bouncer &&
                                sceneInteractor.get().transitionState.currentScene ==
                                    Scenes.Lockscreen)
                        }
                        else -> {
                            true
                        }
                    }
                }
            } else {
                unfilteredUserActions
            }
        }
    }

    /**
     * Enforce the bouncer overlay when on lockscreen but awake, and hide it in other scenarios, but
     * only when active
     */
    fun modifyOverlaysOnSceneChange(toScene: SceneKey) {
        if (!isActive.value) return
        when {
            toScene == Scenes.Lockscreen && powerInteractor.detailedWakefulness.value.isAwake() ->
                sceneInteractor
                    .get()
                    .showOverlay(
                        overlay = Overlays.Bouncer,
                        loggingReason = "RestrictedMode active",
                    )
            toScene == Scenes.Occluded ||
                toScene == Scenes.Dream ||
                powerInteractor.detailedWakefulness.value.isAsleep() ->
                sceneInteractor
                    .get()
                    .hideOverlay(
                        overlay = Overlays.Bouncer,
                        loggingReason = "dreaming or occluded, hiding bouncer",
                    )
        }
    }
}
