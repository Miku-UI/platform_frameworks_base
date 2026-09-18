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

package com.android.systemui.deviceentry.domain.interactor

import androidx.annotation.VisibleForTesting
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.util.kotlin.FlowDumperImpl
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Hosts application business logic related to the source of the user entering the device. Note: The
 * source of the user entering the device isn't equivalent to the reason the device is unlocked.
 *
 * For example, the user successfully enters the device when they dismiss the lockscreen via a
 * bypass biometric or, if the device is already unlocked, by triggering an affordance that
 * dismisses the lockscreen.
 */
@SysUISingleton
class DeviceEntrySourceInteractor
@Inject
constructor(keyguardInteractor: KeyguardInteractor, dumpManager: DumpManager) :
    FlowDumperImpl(dumpManager) {
    @VisibleForTesting
    val deviceEntryFromBiometricSource: Flow<BiometricUnlockSource> =
        keyguardInteractor.biometricUnlockState
            .filter { BiometricUnlockMode.dismissesKeyguard(it.mode) }
            .map { it.source }
            .filterNotNull()
            .dumpWhileCollecting("deviceEntryFromBiometricSource")

    private val _attemptEnterDeviceFromDeviceEntryIcon: MutableSharedFlow<Unit> =
        MutableSharedFlow()
    val attemptEnterDeviceFromDeviceEntryIcon = _attemptEnterDeviceFromDeviceEntryIcon

    suspend fun attemptEnterDeviceFromDeviceEntryIcon() {
        _attemptEnterDeviceFromDeviceEntryIcon.emit(Unit)
    }
}
