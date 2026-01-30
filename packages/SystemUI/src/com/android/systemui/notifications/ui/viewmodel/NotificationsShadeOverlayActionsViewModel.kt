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

package com.android.systemui.notifications.ui.viewmodel

import com.android.compose.animation.scene.Back
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.UserActionResult.HideOverlay
import com.android.compose.animation.scene.UserActionResult.ShowOverlay
import com.android.compose.animation.scene.UserActionResult.ShowOverlay.HideCurrentOverlays
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.EndHalf
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.TopEdgeEndHalf
import com.android.systemui.scene.ui.viewmodel.UserActionsViewModel
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/** Models the UI state for the user actions for navigating to other scenes or overlays. */
class NotificationsShadeOverlayActionsViewModel
@AssistedInject
constructor(private val shadeModeInteractor: ShadeModeInteractor) : UserActionsViewModel() {

    override suspend fun hydrateActions(setActions: (Map<UserAction, UserActionResult>) -> Unit) {
        val hideNotificationsShade = HideOverlay(Overlays.NotificationsShade)
        val openQuickSettingsShade =
            ShowOverlay(
                Overlays.QuickSettingsShade,
                hideCurrentOverlays = HideCurrentOverlays.Some(Overlays.NotificationsShade),
            )

        shadeModeInteractor.isFullWidthShade
            .map { isFullWidthShade ->
                buildMap {
                    put(Swipe.Up, hideNotificationsShade)
                    put(Back, hideNotificationsShade)
                    if (!isFullWidthShade) {
                        put(Swipe.Down(fromSource = EndHalf), openQuickSettingsShade)
                    }
                    put(Swipe.Down(fromSource = TopEdgeEndHalf), openQuickSettingsShade)
                }
            }
            .collect { actions -> setActions(actions) }
    }

    @AssistedFactory
    interface Factory {
        fun create(): NotificationsShadeOverlayActionsViewModel
    }
}
