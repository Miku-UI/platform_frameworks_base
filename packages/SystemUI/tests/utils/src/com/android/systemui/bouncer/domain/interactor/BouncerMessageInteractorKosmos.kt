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

package com.android.systemui.bouncer.domain.interactor

import com.android.keyguard.keyguardSecurityModel
import com.android.keyguard.keyguardUpdateMonitor
import com.android.systemui.biometrics.data.repository.facePropertyRepository
import com.android.systemui.bouncer.data.repository.bouncerMessageRepository
import com.android.systemui.deviceentry.domain.interactor.deviceEntryBiometricsAllowedInteractor
import com.android.systemui.flags.fakeSystemPropertiesHelper
import com.android.systemui.keyguard.data.repository.fakeBiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.fakeTrustRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.testScope
import com.android.systemui.securelockdevice.domain.interactor.secureLockDeviceInteractor
import com.android.systemui.user.data.repository.fakeUserRepository
import org.mockito.kotlin.mock

val Kosmos.bouncerMessageInteractor by Fixture {
    BouncerMessageInteractor(
        repository = bouncerMessageRepository,
        userRepository = fakeUserRepository,
        countDownTimerUtil = countDownTimerUtil,
        updateMonitor = keyguardUpdateMonitor,
        biometricSettingsRepository = fakeBiometricSettingsRepository,
        applicationScope = testScope.backgroundScope,
        trustRepository = fakeTrustRepository,
        systemPropertiesHelper = fakeSystemPropertiesHelper,
        primaryBouncerInteractor = primaryBouncerInteractor,
        facePropertyRepository = facePropertyRepository,
        securityModel = keyguardSecurityModel,
        deviceEntryBiometricsAllowedInteractor = deviceEntryBiometricsAllowedInteractor,
        secureLockDeviceInteractor = { secureLockDeviceInteractor },
    )
}
val Kosmos.countDownTimerUtil by Fixture { mock<CountDownTimerUtil>() }
