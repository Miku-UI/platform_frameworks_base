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
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryUdfpsInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTouchHandlingInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.scene.shared.flag.SceneContainerFlag
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
    deviceEntryUdfpsInteractor: DeviceEntryUdfpsInteractor,
) : ExclusiveActivatable() {
    private val hydrator = Hydrator("KeyguardTouchHandlingViewModel.hydrator")

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
        hydrator.hydratedStateOf(
            traceName = "longPressHandlingEnabled",
            initialValue = false,
            source = interactor.isLongPressHandlingEnabled,
        )

    /** Whether the double tap handling feature should be enabled. */
    val isDoubleTapHandlingEnabled: Boolean by
        hydrator.hydratedStateOf(
            traceName = "doubleTapHandlingEnabled",
            initialValue = false,
            source = interactor.isDoubleTapHandlingEnabled,
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    /**
     * Notifies that the user has long-pressed on the lock screen.
     *
     * @param isA11yAction: Whether the action was performed as an a11y action
     */
    fun onLongPress(isA11yAction: Boolean) {
        if (
            SceneContainerFlag.isEnabled &&
                !isA11yAction &&
                falsingManager.isFalseLongTap(FalsingManager.LOW_PENALTY)
        ) {
            return
        }

        if (Flags.msdlFeedback()) {
            msdlPlayer.playToken(MSDLToken.LONG_PRESS)
        }
        interactor.onLongPress(isA11yAction)
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

    /** Notifies that the lockscreen has been double clicked. */
    fun onDoubleClick() {
        if (SceneContainerFlag.isEnabled && falsingManager.isFalseDoubleTap()) return
        interactor.onDoubleClick()
    }

    @AssistedFactory
    interface Factory {
        fun create(): KeyguardTouchHandlingViewModel
    }
}
