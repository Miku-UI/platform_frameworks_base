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

package com.android.systemui.deviceentry.domain.interactor

import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.keyguard.data.repository.fakeTrustRepository
import com.android.systemui.keyguard.domain.interactor.trustInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.user.domain.interactor.selectedUserInteractor

val Kosmos.activeUnlockInteractor by Fixture {
    ActiveUnlockInteractor(
        applicationScope = applicationCoroutineScope,
        userInteractor = selectedUserInteractor,
        deviceUnlockedInteractor = deviceUnlockedInteractor,
        biometricsAllowedInteractor = deviceEntryBiometricsAllowedInteractor,
        trustInteractor = trustInteractor,
    )
}

fun Kosmos.canTriggerActiveUnlock(canRun: Boolean = true) {
    fakeTrustRepository.setCurrentUserActiveUnlockAvailable(canRun)
    runCurrent()

    allowFingerprint()
    fakeDeviceEntryRepository.deviceUnlockStatus.value =
        DeviceUnlockStatus(isUnlocked = false, deviceUnlockSource = null)
    runCurrent()
}
