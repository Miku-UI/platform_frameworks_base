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
 */

package com.android.systemui.biometrics.ui.viewmodel

import android.security.Flags.secureLockDevice
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.keyguard.logging.DeviceEntryIconLogger
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor
import com.android.systemui.keyguard.ui.viewmodel.DeviceEntryIconViewModel
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import com.android.systemui.statusbar.phone.SystemUIDialogManager
import com.android.systemui.statusbar.phone.hideAffordancesRequest
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * View model for the UdfpsTouchOverlay for when UDFPS is being requested for device entry. Handles
 * touches as long as the device entry view is visible (the lockscreen or the alternate bouncer
 * view), or during secure lock device biometric authentication on the primary bouncer.
 */
class DeviceEntryUdfpsTouchOverlayViewModel
@Inject
constructor(
    deviceEntryIconViewModel: DeviceEntryIconViewModel,
    alternateBouncerInteractor: AlternateBouncerInteractor,
    secureLockDeviceInteractor: Lazy<SecureLockDeviceInteractor>,
    systemUIDialogManager: SystemUIDialogManager,
    sceneInteractor: Lazy<SceneInteractor>,
    logger: DeviceEntryIconLogger,
) : UdfpsTouchOverlayViewModel {
    private val deviceEntryViewAlphaIsMostlyVisible: Flow<Boolean> =
        if (SceneContainerFlag.isEnabled) {
            sceneInteractor
                .get()
                .transitionStateFlow
                .flatMapLatestConflated { state ->
                    when (state) {
                        is ObservableTransitionState.Idle -> {
                            if (
                                state.currentScene == Scenes.Lockscreen &&
                                    !state.currentOverlays.contains(Overlays.Bouncer)
                            ) {
                                flowOf(true)
                            } else {
                                flowOf(false)
                            }
                        }
                        is ObservableTransitionState.Transition ->
                            if (state.toContent == Scenes.Lockscreen) {
                                state.progress.map { progress ->
                                    progress > 1 - ALLOW_TOUCH_SHADE_EXPANSION_MAX_THRESHOLD
                                }
                            } else if (state.fromContent == Scenes.Lockscreen) {
                                state.progress.map { progress ->
                                    progress < ALLOW_TOUCH_SHADE_EXPANSION_MAX_THRESHOLD
                                }
                            } else {
                                flowOf(false)
                            }
                    }
                }
                .distinctUntilChanged()
        } else {
            deviceEntryIconViewModel.deviceEntryViewAlpha
                .map { it > ALLOW_TOUCH_ALPHA_THRESHOLD }
                .distinctUntilChanged()
        }

    override val shouldHandleTouches: Flow<Boolean> =
        combine(
                deviceEntryViewAlphaIsMostlyVisible,
                alternateBouncerInteractor.isVisible,
                systemUIDialogManager.hideAffordancesRequest,
                deviceEntryIconViewModel.transitioningToDozing,
                secureLockDeviceInteractor.get().shouldListenForBiometricAuth,
            ) {
                canTouchDeviceEntryViewAlpha,
                alternateBouncerVisible,
                hideAffordancesRequest,
                toDozing,
                shouldListenForBiometricAuthDuringSecureLockDevice ->
                val handleTouchesForSecureLockDeviceBiometricAuth =
                    (secureLockDevice() && shouldListenForBiometricAuthDuringSecureLockDevice)
                val shouldHandleTouches =
                    (canTouchDeviceEntryViewAlpha && !hideAffordancesRequest) ||
                        alternateBouncerVisible ||
                        toDozing ||
                        handleTouchesForSecureLockDeviceBiometricAuth
                logger.logDeviceEntryUdfpsTouchOverlayShouldHandleTouches(
                    shouldHandleTouches,
                    canTouchDeviceEntryViewAlpha,
                    handleTouchesForSecureLockDeviceBiometricAuth,
                    alternateBouncerVisible,
                    hideAffordancesRequest,
                )
                shouldHandleTouches
            }
            .distinctUntilChanged()

    companion object {
        // only allow touches if the view is still mostly visible
        const val ALLOW_TOUCH_ALPHA_THRESHOLD = .9f

        /**
         * The maximum shade expansion (0.0f to 1.0f) allowed for UDFPS touches to be handled.
         *
         * This value is derived from `FromLockscreenToOverlayTransition.kt`. The lockscreen fades
         * out during the first/end 20% of the transition (progress 0.0f to 0.2f). To ensure UDFPS
         * touches are only handled when the lockscreen alpha is greater than 0.9f, the maximum
         * allowed shade expansion is set to 0.02f (since 0.02f / 0.2f = 0.1, meaning 10% of the
         * fade, resulting in 0.9 alpha).
         */
        const val ALLOW_TOUCH_SHADE_EXPANSION_MAX_THRESHOLD = .02f
    }
}
