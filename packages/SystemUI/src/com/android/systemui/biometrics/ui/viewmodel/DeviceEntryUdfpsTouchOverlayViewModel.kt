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
import com.android.keyguard.logging.DeviceEntryIconLogger
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor
import com.android.systemui.keyguard.ui.viewmodel.DeviceEntryIconViewModel
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import com.android.systemui.statusbar.phone.SystemUIDialogManager
import com.android.systemui.statusbar.phone.hideAffordancesRequest
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    logger: DeviceEntryIconLogger,
) : UdfpsTouchOverlayViewModel {
    private val deviceEntryViewAlphaIsMostlyVisible: Flow<Boolean> =
        deviceEntryIconViewModel.deviceEntryViewAlpha
            .map { it > ALLOW_TOUCH_ALPHA_THRESHOLD }
            .distinctUntilChanged()

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
    }
}
