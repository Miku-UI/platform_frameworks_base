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

package com.android.systemui.model

import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.data.repository.ShadeDisplaysRepository
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BOUNCER_SHOWING
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_COMMUNAL_HUB_SHOWING
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * A plugin for [SysUiState] that provides overrides for certain state flags that must be pulled
 * from the scene framework when that framework is enabled.
 *
 * Note that those flags only apply to the display id containing the shade window, as defined by
 * [com.android.systemui.shade.domain.interactor.ShadeDisplaysInteractor.displayId]
 */
interface SceneContainerPlugin {
    /**
     * Returns an override value for the given [flag] or `null` if the scene framework isn't enabled
     * or if the flag value doesn't need to be overridden.
     */
    fun flagValueOverride(@SystemUiStateFlags flag: Long, displayId: Int): Boolean?

    data class SceneContainerPluginState(
        val scene: SceneKey,
        val overlays: Set<OverlayKey>,
        val isVisible: Boolean,
    )
}

@SysUISingleton
class SceneContainerPluginImpl
@Inject
constructor(
    private val sceneInteractor: Lazy<SceneInteractor>,
    private val shadeDisplaysRepository: Lazy<ShadeDisplaysRepository>,
) : SceneContainerPlugin {

    private val shadeDisplayId: StateFlow<Int> by lazy {
        shadeDisplaysRepository.get().pendingDisplayId
    }

    override fun flagValueOverride(@SystemUiStateFlags flag: Long, displayId: Int): Boolean? {
        if (!SceneContainerFlag.isEnabled) {
            return null
        }

        if (ShadeWindowGoesAround.isEnabled && shadeDisplayId.value != displayId) {
            // The shade is in another display. All flags related to the shade container will map to
            // false on other displays now.
            //
            // Note that this assumes there is only one SceneContainer and it is only on the shade
            // window display. If there will be more, this will need to be revisited
            return false
        }
        val transitionState = sceneInteractor.get().transitionState.value
        val idleTransitionStateOrNull = transitionState as? ObservableTransitionState.Idle
        return idleTransitionStateOrNull?.let { idleState ->
            EvaluatorByFlag[flag]?.invoke(
                SceneContainerPlugin.SceneContainerPluginState(
                    scene = idleState.currentScene,
                    overlays = idleState.currentOverlays,
                    isVisible = sceneInteractor.get().isVisible.value,
                )
            )
        }
    }

    companion object {

        /**
         * Value evaluator function by state flag ID.
         *
         * The value evaluator function can be invoked, passing in the current [SceneKey] to know
         * the override value of the flag ID.
         *
         * If the map doesn't contain an entry for a certain flag ID, it means that it doesn't need
         * to be overridden by the scene framework.
         */
        val EvaluatorByFlag =
            mapOf<Long, (SceneContainerPlugin.SceneContainerPluginState) -> Boolean>(
                SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE to
                    {
                        when {
                            !it.isVisible -> false
                            it.scene != Scenes.Gone -> true
                            it.overlays.isNotEmpty() -> true
                            else -> false
                        }
                    },
                SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED to
                    {
                        when {
                            !it.isVisible -> false
                            it.scene == Scenes.Lockscreen -> true
                            it.scene == Scenes.Shade -> true
                            Overlays.NotificationsShade in it.overlays -> true
                            else -> false
                        }
                    },
                SYSUI_STATE_QUICK_SETTINGS_EXPANDED to
                    {
                        when {
                            !it.isVisible -> false
                            it.scene == Scenes.QuickSettings -> true
                            Overlays.QuickSettingsShade in it.overlays -> true
                            else -> false
                        }
                    },
                SYSUI_STATE_BOUNCER_SHOWING to { it.isVisible && Overlays.Bouncer in it.overlays },
                SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING to
                    {
                        it.isVisible && it.scene == Scenes.Lockscreen
                    },
                SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED to { it.scene == Scenes.Occluded },
                SYSUI_STATE_COMMUNAL_HUB_SHOWING to { it.isVisible && it.scene == Scenes.Communal },
            )
    }
}
