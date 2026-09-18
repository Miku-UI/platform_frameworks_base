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

package com.android.systemui.statusbar.quickactions.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.statusbar.quickactions.data.repository.QuickActionChipsRepository
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionPanelModel
import javax.inject.Inject

@SysUISingleton
class QuickActionsInteractor
@Inject
constructor(
    private val repository: QuickActionChipsRepository,
    private val sceneInteractor: SceneInteractor,
) {
    /**
     * The currently active QuickActionPanel. The panel is only considered active if the
     * Overlays.QuickActions is present in currentOverlays or if it is transitioning in or out.
     */
    val activePanel: QuickActionPanelModel?
        get() = repository.activePanel.value.takeIf { isOverlayActive() }

    /** Expands a QuickActionPanel if it isn't currently open. Closes it otherwise. */
    fun toggle(panel: QuickActionPanelModel) {
        if (panel == activePanel) {
            close()
        } else {
            expand(panel)
        }
    }

    /**
     * Closes any other overlays and then displays the provided panel in the QuickActions Overlay.
     */
    fun expand(panel: QuickActionPanelModel) {
        val currentOverlays = sceneInteractor.transitionState.currentOverlays

        currentOverlays
            .filter { it in MUTUALLY_EXCLUSIVE_OVERLAYS }
            .forEach { overlay ->
                sceneInteractor.hideOverlay(
                    overlay = overlay,
                    loggingReason = "Expanding QuickAction Overlay",
                )
            }

        // TODO(b/494657434): Exempt QuickActionChips from Shade window dismissal logic.
        // Currently, clicking a chip registers as an out-of-bounds click, which dismisses
        // the Shade. Because the Shade is transitioning out, subsequent showOverlay()
        // calls for the chip's new content are rejected.
        if (Overlays.QuickActions in currentOverlays) {
            sceneInteractor.instantlyHideOverlay(
                overlay = Overlays.QuickActions,
                loggingReason = "Switching to different panel in the QuickAction Overlay",
            )
        }

        repository.setActivePanel(panel)

        sceneInteractor.showOverlay(
            overlay = Overlays.QuickActions,
            loggingReason = "QuickActionChipsInteractor.expand",
        )
    }

    /** Clears the active panel state and hides the overlay. */
    fun close() {
        repository.setActivePanel(null)
        sceneInteractor.hideOverlay(Overlays.QuickActions, "QuickActionChipsInteractor.close")
    }

    /**
     * True if actively animating in/out, preventing double-toggles during the animation gap.
     * Otherwise, fallback to the current overlay state.
     */
    private fun isOverlayActive(): Boolean {
        return with(sceneInteractor.transitionState) {
            isTransitioningFromOrTo(Overlays.QuickActions) ||
                Overlays.QuickActions in currentOverlays
        }
    }

    companion object {
        private val MUTUALLY_EXCLUSIVE_OVERLAYS =
            setOf(Overlays.QuickSettingsShade, Overlays.NotificationsShade)
    }
}
