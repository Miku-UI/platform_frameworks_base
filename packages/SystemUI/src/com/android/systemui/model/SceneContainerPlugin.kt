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
import com.android.systemui.scene.data.model.peek
import com.android.systemui.scene.domain.interactor.SceneBackInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.data.repository.ShadeDisplaysRepository
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
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
        val sceneBehind: SceneKey? = null,
        val overlays: Set<OverlayKey>,
        val isVisible: Boolean,
        val shadeMode: ShadeMode,
    )
}

@SysUISingleton
class SceneContainerPluginImpl
@Inject
constructor(
    private val sceneInteractor: Lazy<SceneInteractor>,
    private val sceneBackInteractor: Lazy<SceneBackInteractor>,
    private val shadeDisplaysRepository: Lazy<ShadeDisplaysRepository>,
    private val shadeModeInteractor: Lazy<ShadeModeInteractor>,
) : SceneContainerPlugin {

    private val flagsHandledBySceneContainerForAllDisplays =
        setOf(
            SYSUI_STATE_BOUNCER_SHOWING,
            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED,
        )

    private val shadeDisplayId: StateFlow<Int> by lazy {
        shadeDisplaysRepository.get().pendingDisplayId
    }

    override fun flagValueOverride(@SystemUiStateFlags flag: Long, displayId: Int): Boolean? {
        if (!SceneContainerFlag.isEnabled) {
            return null
        }

        val evaluator = EvaluatorByFlag[flag] ?: return null

        if (
            shadeDisplayId.value != displayId &&
                !flagsHandledBySceneContainerForAllDisplays.contains(flag)
        ) {
            // The shade is in another display. All flags related to the shade will map to false on
            // other displays. Flags that are not shade-specific (keyguard, bouncer, etc.) will be
            // respected.
            return false
        }
        val transitionState = sceneInteractor.get().transitionStateFlow.value
        val idleTransitionStateOrNull = transitionState as? ObservableTransitionState.Idle
        if (idleTransitionStateOrNull != null) {
            val sceneBehind = sceneBackInteractor.get().backStack.value.peek()
            val shadeMode = shadeModeInteractor.get().shadeMode.value
            return idleTransitionStateOrNull.let { idleState ->
                evaluator.invoke(
                    SceneContainerPlugin.SceneContainerPluginState(
                        scene = idleState.currentScene,
                        sceneBehind = sceneBehind,
                        overlays = idleState.currentOverlays,
                        isVisible = sceneInteractor.get().isVisibleFlow.value,
                        shadeMode = shadeMode,
                    )
                )
            }
        } else if (
            flag == SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED &&
                transitionState is ObservableTransitionState.Transition
        ) {
            // The one scene container state that activates at the start of a transition instead of
            // an Idle scene is that the SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED flag should be set
            // as soon at the notification shade (note: does not include QS) starts expanding
            return transitionState.toContent == Overlays.NotificationsShade ||
                transitionState.toContent == Scenes.Shade
        }
        return null
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
                            Overlays.NotificationsShade in it.overlays -> true
                            Overlays.QuickSettingsShade in it.overlays -> true
                            it.scene == Scenes.Lockscreen && Overlays.Bouncer in it.overlays ->
                                false
                            it.scene.isOccluded() -> false
                            it.scene != Scenes.Gone -> true
                            else -> false
                        }
                    },
                SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED to
                    {
                        when {
                            !it.isVisible -> false
                            it.scene == Scenes.Shade && it.shadeMode !is ShadeMode.Split -> true
                            Overlays.NotificationsShade in it.overlays -> true
                            it.scene == Scenes.Lockscreen &&
                                Overlays.Bouncer !in it.overlays &&
                                Overlays.QuickSettingsShade !in it.overlays -> true
                            else -> false
                        }
                    },
                SYSUI_STATE_QUICK_SETTINGS_EXPANDED to
                    {
                        when {
                            !it.isVisible -> false
                            it.scene == Scenes.QuickSettings -> true
                            it.scene == Scenes.Shade && it.shadeMode is ShadeMode.Split -> true
                            Overlays.QuickSettingsShade in it.overlays -> true
                            else -> false
                        }
                    },
                SYSUI_STATE_BOUNCER_SHOWING to { it.isVisible && Overlays.Bouncer in it.overlays },
                SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING to
                    {
                        when {
                            !it.isVisible -> false
                            it.scene == Scenes.Lockscreen -> true
                            it.sceneBehind == Scenes.Lockscreen -> true
                            Overlays.Bouncer in it.overlays -> true
                            else -> false
                        }
                    },
                SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED to
                    {
                        it.scene.isOccluded() || it.sceneBehind?.isOccluded() == true
                    },
                SYSUI_STATE_COMMUNAL_HUB_SHOWING to { it.isVisible && it.scene == Scenes.Communal },
            )
    }
}

private fun SceneKey.isOccluded() = this == Scenes.Occluded || this == Scenes.Dream
