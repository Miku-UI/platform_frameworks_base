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

package com.android.systemui.securelockdevice.domain.interactor

import android.security.authenticationpolicy.authenticationPolicyManager
import com.android.internal.widget.lockPatternUtils
import com.android.systemui.biometrics.domain.interactor.facePropertyInteractor
import com.android.systemui.biometrics.domain.interactor.fingerprintPropertyInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceEntryBiometricSettingsInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceEntryFaceAuthInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.securelockdevice.data.repository.secureLockDeviceRepository
import com.android.systemui.user.domain.interactor.selectedUserInteractor

val Kosmos.secureLockDeviceInteractor by
    Kosmos.Fixture {
        SecureLockDeviceInteractor(
            applicationScope = applicationCoroutineScope,
            logBuffer = logcatLogBuffer("SecureLockDeviceLog"),
            secureLockDeviceRepository = secureLockDeviceRepository,
            biometricSettingsInteractor = deviceEntryBiometricSettingsInteractor,
            deviceEntryFaceAuthInteractor = deviceEntryFaceAuthInteractor,
            fingerprintPropertyInteractor = { fingerprintPropertyInteractor },
            facePropertyInteractor = facePropertyInteractor,
            lockPatternUtils = lockPatternUtils,
            authenticationPolicyManager = authenticationPolicyManager,
            selectedUserInteractor = selectedUserInteractor,
            keyguardTransitionInteractor = { keyguardTransitionInteractor },
        )
    }
