/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.securelockdevice.ui.viewmodel

import android.view.accessibility.accessibilityManager
import com.android.systemui.biometrics.ui.viewmodel.biometricAuthIconViewModelFactory_secureLockDevice
import com.android.systemui.bouncer.domain.interactor.bouncerActionButtonInteractor
import com.android.systemui.deviceentry.data.ui.viewmodel.alternateBouncerUdfpsAccessibilityOverlayViewModel
import com.android.systemui.deviceentry.domain.interactor.biometricMessageInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceEntryFaceAuthInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceEntryFingerprintAuthInteractor
import com.android.systemui.haptics.msdl.bouncerHapticPlayer
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.securelockdevice.domain.interactor.secureLockDeviceInteractor

var Kosmos.secureLockDeviceBiometricAuthContentViewModel by Fixture {
    SecureLockDeviceBiometricAuthContentViewModel(
        accessibilityManager = accessibilityManager,
        actionButtonInteractor = bouncerActionButtonInteractor,
        biometricAuthIconViewModelFactory = biometricAuthIconViewModelFactory_secureLockDevice,
        biometricMessageInteractor = biometricMessageInteractor,
        bouncerHapticPlayer = bouncerHapticPlayer,
        deviceEntryFaceAuthInteractor = deviceEntryFaceAuthInteractor,
        deviceEntryFingerprintAuthInteractor = deviceEntryFingerprintAuthInteractor,
        secureLockDeviceInteractor = secureLockDeviceInteractor,
        udfpsAccessibilityOverlayViewModel = alternateBouncerUdfpsAccessibilityOverlayViewModel,
        interactionJankMonitor = interactionJankMonitor,
    )
}

val Kosmos.secureLockDeviceBiometricAuthContentViewModelFactory by Fixture {
    object : SecureLockDeviceBiometricAuthContentViewModel.Factory {
        override fun create(): SecureLockDeviceBiometricAuthContentViewModel {
            return secureLockDeviceBiometricAuthContentViewModel
        }
    }
}
