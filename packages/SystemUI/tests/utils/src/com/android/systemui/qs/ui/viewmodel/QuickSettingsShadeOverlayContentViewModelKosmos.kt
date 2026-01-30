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

package com.android.systemui.qs.ui.viewmodel

import android.content.applicationContext
import com.android.systemui.desktop.domain.interactor.desktopInteractor
import com.android.systemui.development.ui.viewmodel.buildNumberViewModelFactory
import com.android.systemui.keyguard.ui.transitions.blurConfig
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.qs.panels.domain.interactor.qsPanelAppearanceInteractor
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.toolbarViewModelFactory
import com.android.systemui.qs.tiles.dialog.audioDetailsViewModelFactory
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationStackAppearanceInteractor
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.audioStreamSliderViewModelFactory
import com.android.systemui.window.domain.interactor.windowRootViewBlurInteractor
import kotlinx.coroutines.CoroutineScope

val Kosmos.quickSettingsShadeOverlayContentViewModelFactory:
    QuickSettingsShadeOverlayContentViewModel.Factory by
    Kosmos.Fixture {
        object : QuickSettingsShadeOverlayContentViewModel.Factory {
            override fun create(
                volumeSliderCoroutineScope: CoroutineScope?
            ): QuickSettingsShadeOverlayContentViewModel {
                return QuickSettingsShadeOverlayContentViewModel(
                    mainDispatcher = testDispatcher,
                    shadeInteractor = shadeInteractor,
                    shadeModeInteractor = shadeModeInteractor,
                    sceneInteractor = sceneInteractor,
                    desktopInteractor = desktopInteractor,
                    notificationStackAppearanceInteractor = notificationStackAppearanceInteractor,
                    shadeContext = applicationContext,
                    audioDetailsViewModelFactory = audioDetailsViewModelFactory,
                    audioStreamSliderViewModelFactory = audioStreamSliderViewModelFactory,
                    buildNumberViewModelFactory = buildNumberViewModelFactory,
                    volumeSliderCoroutineScope = volumeSliderCoroutineScope,
                    toolbarViewModelFactory = toolbarViewModelFactory,
                    blurConfig = blurConfig,
                    windowRootViewBlurInteractor = windowRootViewBlurInteractor,
                    qsPanelAppearanceInteractor = qsPanelAppearanceInteractor,
                )
            }
        }
    }
