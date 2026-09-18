/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 */

package com.android.systemui.keyguard.ui.viewmodel

import android.graphics.Rect
import androidx.compose.runtime.getValue
import com.android.systemui.Flags
import com.android.systemui.communal.domain.interactor.CommunalInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryUdfpsInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTouchHandlingInteractor
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.domain.MSDLPlayer
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Models UI state to support top-level touch handling in the lock screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyguardTouchHandlingViewModel
@AssistedInject
constructor(
    private val interactor: KeyguardTouchHandlingInteractor,
    private val msdlPlayer: MSDLPlayer,
    private val falsingManager: FalsingManager,
    private val keyguardSettingsMenuViewModel: KeyguardSettingsMenuViewModel,
    private val sceneInteractor: SceneInteractor,
    communalInteractor: CommunalInteractor,
    deviceEntryUdfpsInteractor: DeviceEntryUdfpsInteractor,
) : HydratedActivatable() {

    /**
     * Bounds of the UDFPS accessibility overlay. This is needed in order to prevent interrupted
     * accessibility feedback from user interaction where the keyguard touch handling view and the
     * accessibility overlay overlap.
     */
    val accessibilityOverlayBoundsWhenListeningForUdfps: Flow<Rect?> =
        combine(
            interactor.udfpsAccessibilityOverlayBounds,
            deviceEntryUdfpsInteractor.isListeningForUdfps,
        ) { bounds, isListeningForUdfps ->
            if (isListeningForUdfps) {
                bounds
            } else {
                null
            }
        }

    /** Whether the long-press handling feature should be enabled. */
    val isLongPressHandlingEnabled: Boolean by
        interactor.isLongPressHandlingEnabled.hydratedStateOf(initialValue = false)

    /** Whether the double tap handling feature should be enabled. */
    val isDoubleTapHandlingEnabled: Boolean by
        interactor.isDoubleTapHandlingEnabled.hydratedStateOf(initialValue = false)

    /** Whether communal features are enabled and available. */
    val isCommunalAvailable by communalInteractor.isCommunalAvailable.hydratedStateOf(
        traceName = "KeyguardTouchHandlingViewModel.hydrator",
        initialValue = false,
    )

    /**
     * Notifies that the user has long-pressed on the lock screen.
     */
    fun onLongPress() {
        if (
            SceneContainerFlag.isEnabled &&
                falsingManager.isFalseLongTap(FalsingManager.LOW_PENALTY)
        ) {
            return
        }

        if (Flags.msdlFeedback()) {
            msdlPlayer.playToken(MSDLToken.LONG_PRESS)
        }
        interactor.onLongPress()
    }

    /**
     * Notifies that some input gesture has started somewhere outside of the lock screen settings
     * menu item pop-up.
     */
    fun onTouchedOutside() {
        interactor.onTouchedOutside()
    }

    /** Notifies that the lockscreen has been clicked at position [x], [y]. */
    fun onClick(x: Float, y: Float) {
        interactor.onClick(x, y)
    }

    /** Notifies that anything in the lockscreen scene has been clicked at position [x], [y]. */
    fun onSceneClick(x: Float, y: Float) {
        interactor.onSceneClick(x, y)
    }

    /** Notifies that the lockscreen has been double clicked. */
    fun onDoubleClick() {
        if (SceneContainerFlag.isEnabled && falsingManager.isFalseDoubleTap()) return
        interactor.onDoubleClick()
    }

    fun openKeyguardSettingsPopupMenu() {
        keyguardSettingsMenuViewModel.openKeyguardSettingsPopupMenu()
    }

    fun goToCommunalSceneViaA11yInteraction() {
        sceneInteractor.changeScene(
            toScene = Scenes.Communal,
            loggingReason = "Transitioning due to a11y interaction."
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): KeyguardTouchHandlingViewModel
    }
}
