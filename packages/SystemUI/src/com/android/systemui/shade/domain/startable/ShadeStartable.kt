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

package com.android.systemui.shade.domain.startable

import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.dagger.ShadeTouchLog
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.ShadeExpansionStateManager
import com.android.systemui.shade.TouchLogger.Companion.logTouchesTo
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.shade.transition.ScrimShadeTransitionController
import com.android.systemui.statusbar.NotificationShadeDepthController
import com.android.systemui.statusbar.PulseExpansionHandler
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn

@SysUISingleton
class ShadeStartable
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    @ShadeTouchLog private val touchLog: LogBuffer,
    private val shadeInteractorProvider: Provider<ShadeInteractor>,
    private val shadeModeInteractorProvider: Provider<ShadeModeInteractor>,
    private val scrimShadeTransitionController: ScrimShadeTransitionController,
    private val sceneInteractorProvider: Provider<SceneInteractor>,
    private val shadeExpansionStateManager: ShadeExpansionStateManager,
    private val pulseExpansionHandler: PulseExpansionHandler,
    private val nsslc: NotificationStackScrollLayoutController,
    private val depthController: NotificationShadeDepthController,
) : CoreStartable {

    override fun start() {
        hydrateFullWidth()
        hydrateShadeExpansionStateManager()
        logTouchesTo(touchLog)
        scrimShadeTransitionController.init()
        pulseExpansionHandler.setUp(nsslc)
    }

    private fun hydrateShadeExpansionStateManager() {
        if (SceneContainerFlag.isEnabled) {
            val shadeInteractor = shadeInteractorProvider.get()

            combine(
                    shadeInteractor.anyExpansion,
                    shadeModeInteractorProvider.get().shadeMode,
                    sceneInteractorProvider.get().isTransitionUserInputOngoing,
                    sceneInteractorProvider.get().transitionStateFlow,
                ) { panelExpansion, shadeMode, tracking, transitionState ->
                    val fraction =
                        if (transitionState.isIdle(Scenes.Lockscreen)) {
                            1f
                        } else if (
                            shadeMode == ShadeMode.Single &&
                                (transitionState.isTransitioning(
                                    Scenes.Shade,
                                    Scenes.QuickSettings,
                                ) ||
                                    transitionState.isTransitioning(
                                        Scenes.QuickSettings,
                                        Scenes.Shade,
                                    ))
                        ) {
                            // Legacy behavior was that shade to QS and vice versa was 1f
                            1f
                        } else {
                            panelExpansion
                        }
                    shadeExpansionStateManager.onPanelExpansionChanged(
                        fraction = fraction,
                        expanded = fraction > 0f,
                        tracking = tracking,
                    )
                }
                .launchIn(applicationScope)

            applicationScope.launch {
                shadeInteractor.qsExpansion.collect { depthController.qsPanelExpansion = it }
            }

            applicationScope.launch {
                shadeInteractor.anyExpansion.collect {
                    depthController.transitionToFullShadeProgress = it
                }
            }
        }
    }

    private fun hydrateFullWidth() {
        if (SceneContainerFlag.isEnabled) {
            val shadeModeInteractor = shadeModeInteractorProvider.get()
            applicationScope.launch {
                shadeModeInteractor.isFullWidthShade.collect { isFullWidth ->
                    nsslc.setIsFullWidth(isFullWidth)
                }
            }
        }
    }
}
