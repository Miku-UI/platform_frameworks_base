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

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.domain.interactor.TrustInteractor
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Encapsulates any state relevant to active unlock. */
@SysUISingleton
class ActiveUnlockInteractor
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    userInteractor: SelectedUserInteractor,
    deviceUnlockedInteractor: DeviceUnlockedInteractor,
    biometricsAllowedInteractor: DeviceEntryBiometricsAllowedInteractor,
    trustInteractor: TrustInteractor,
) {
    /** Whether the current user has active unlock setup and enabled. */
    private val currentUserActiveUnlockEnabled = trustInteractor.isCurrentUserActiveUnlockEnabled

    /** Whether the device state would allow for active unlock to trigger. */
    private val activeUnlockAllowed =
        combine(
            userInteractor.isUserSwitching,
            deviceUnlockedInteractor.deviceUnlockStatus,
            biometricsAllowedInteractor.isFingerprintAuthCurrentlyAllowed,
        ) { isUserSwitching, deviceUnlockStatus, isFingerprintCurrentlyAllowed ->
            !isUserSwitching && !deviceUnlockStatus.isUnlocked && isFingerprintCurrentlyAllowed
        }

    /** Whether active unlock will run in the current device state. */
    val canRunActiveUnlock: StateFlow<Boolean> =
        currentUserActiveUnlockEnabled
            .flatMapLatest { activeUnlockEnabled ->
                if (activeUnlockEnabled) {
                    activeUnlockAllowed
                } else {
                    flowOf(false)
                }
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )
}
