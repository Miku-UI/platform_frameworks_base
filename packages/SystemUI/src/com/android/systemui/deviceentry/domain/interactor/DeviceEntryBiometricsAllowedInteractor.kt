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

package com.android.systemui.deviceentry.domain.interactor

import android.security.Flags.secureLockDevice
import com.android.systemui.biometrics.data.repository.FacePropertyRepository
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Individual biometrics (ie: fingerprint or face) may not be allowed to be used based on the
 * lockout states of biometrics of the same or higher sensor strength.
 *
 * This class coordinates the lockout states of each individual biometric based on the lockout
 * states of other biometrics.
 */
@SysUISingleton
class DeviceEntryBiometricsAllowedInteractor
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    deviceEntryFingerprintAuthInteractor: DeviceEntryFingerprintAuthInteractor,
    deviceEntryFaceAuthInteractor: DeviceEntryFaceAuthInteractor,
    biometricSettingsInteractor: DeviceEntryBiometricSettingsInteractor,
    facePropertyRepository: FacePropertyRepository,
    secureLockDeviceInteractor: Lazy<SecureLockDeviceInteractor>,
) {
    /**
     * Whether face is locked out due to too many failed face attempts. This currently includes
     * whether face is not allowed based on other biometric lockouts; however does not include if
     * face isn't allowed due to other strong or primary authentication requirements.
     */
    val isFaceLockedOut: StateFlow<Boolean> = deviceEntryFaceAuthInteractor.isLockedOut

    val isStrongFaceAuth: StateFlow<Boolean> =
        facePropertyRepository.sensorInfo
            .map { it?.strength == SensorStrength.STRONG }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    facePropertyRepository.sensorInfo.value?.strength == SensorStrength.STRONG,
            )

    private val isStrongFaceAuthLockedOut: StateFlow<Boolean> =
        combine(isStrongFaceAuth, isFaceLockedOut) { isStrongFaceAuth, isFaceAuthLockedOut ->
                isStrongFaceAuth && isFaceAuthLockedOut
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = isStrongFaceAuth.value && isFaceLockedOut.value,
            )

    /**
     * Whether fingerprint is locked out due to too many failed fingerprint attempts. This does NOT
     * include whether fingerprint is not allowed based on other biometric lockouts nor if
     * fingerprint isn't allowed due to other strong or primary authentication requirements.
     */
    val isFingerprintLockedOut: StateFlow<Boolean> =
        deviceEntryFingerprintAuthInteractor.isLockedOut

    /**
     * Whether fingerprint authentication is currently allowed for the user. This is true if the
     * user has fingerprint auth enabled, enrolled, it is not disabled by any security timeouts by
     * [com.android.systemui.keyguard.shared.model.AuthenticationFlags], not locked out due to too
     * many incorrect attempts, and other biometrics at a higher or equal strength are not locking
     * fingerprint out.
     */
    val isFingerprintAuthCurrentlyAllowed: StateFlow<Boolean> =
        combine(
                isFingerprintLockedOut,
                biometricSettingsInteractor.fingerprintAuthCurrentlyAllowed,
                isStrongFaceAuthLockedOut,
                secureLockDeviceInteractor.get().isSecureLockDeviceEnabled,
                secureLockDeviceInteractor.get().shouldListenForBiometricAuth,
            ) {
                fpLockedOut,
                fpAllowedBySettings,
                strongAuthFaceAuthLockedOut,
                isSecureLockDeviceEnabled,
                shouldListenForBiometricAuthDuringSecureLockDevice ->
                if (secureLockDevice() && isSecureLockDeviceEnabled) {
                    !fpLockedOut &&
                        fpAllowedBySettings &&
                        shouldListenForBiometricAuthDuringSecureLockDevice
                } else {
                    !fpLockedOut && fpAllowedBySettings && !strongAuthFaceAuthLockedOut
                }
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    !isFingerprintLockedOut.value &&
                        !isStrongFaceAuthLockedOut.value &&
                        biometricSettingsInteractor.fingerprintAuthCurrentlyAllowed.value,
            )

    /** Whether fingerprint authentication is currently allowed while on the bouncer. */
    val isFingerprintCurrentlyAllowedOnBouncer: Flow<Boolean> =
        combine(
            secureLockDeviceInteractor.get().isSecureLockDeviceEnabled,
            deviceEntryFingerprintAuthInteractor.isSensorUnderDisplay,
            isFingerprintAuthCurrentlyAllowed,
        ) { isSecureLockDeviceEnabled, sensorBelowDisplay, isFingerprintAuthCurrentlyAllowed ->
            if (secureLockDevice() && isSecureLockDeviceEnabled) {
                isFingerprintAuthCurrentlyAllowed
            } else {
                if (sensorBelowDisplay) {
                    false
                } else {
                    isFingerprintAuthCurrentlyAllowed
                }
            }
        }

    /**
     * Whether face authentication is currently allowed for the user. This is true if the user has
     * face auth enabled, enrolled, it is not disabled by any security timeouts by
     * [com.android.systemui.keyguard.shared.model.AuthenticationFlags], not locked out due to too
     * many incorrect attempts, and other biometrics at a higher or equal strength are not locking
     * face out.
     */
    val isFaceCurrentlyAllowedOnBouncer: Flow<Boolean> =
        combine(
            isFaceLockedOut,
            biometricSettingsInteractor.faceAuthCurrentlyAllowed,
            isStrongFaceAuthLockedOut,
            secureLockDeviceInteractor.get().isSecureLockDeviceEnabled,
            secureLockDeviceInteractor.get().shouldListenForBiometricAuth,
        ) {
            faceLockedOut,
            faceAllowedBySettings,
            strongAuthFaceAuthLockedOut,
            isSecureLockDeviceEnabled,
            shouldListenForBiometricAuthDuringSecureLockDevice ->
            if (secureLockDevice() && isSecureLockDeviceEnabled) {
                !faceLockedOut &&
                    faceAllowedBySettings &&
                    shouldListenForBiometricAuthDuringSecureLockDevice
            } else {
                !faceLockedOut && faceAllowedBySettings && !strongAuthFaceAuthLockedOut
            }
        }
}
