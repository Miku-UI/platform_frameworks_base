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

package com.android.systemui.securelockdevice.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSecureLockDeviceRepository : SecureLockDeviceRepository {
    private val _isSecureLockDeviceEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val isSecureLockDeviceEnabled: StateFlow<Boolean> =
        _isSecureLockDeviceEnabled.asStateFlow()

    private val _requiresPrimaryAuthForSecureLockDevice: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    override val requiresPrimaryAuthForSecureLockDevice: Flow<Boolean> =
        _requiresPrimaryAuthForSecureLockDevice.asStateFlow()

    private val _requiresStrongBiometricAuthForSecureLockDevice: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    override val requiresStrongBiometricAuthForSecureLockDevice: Flow<Boolean> =
        _requiresStrongBiometricAuthForSecureLockDevice.asStateFlow()

    override val suppressBouncerMessageUpdates: MutableStateFlow<Boolean> =
        MutableStateFlow(false)

    fun setRequiresPrimaryAuthForSecureLockDevice(requiresPrimaryAuth: Boolean) {
        _requiresPrimaryAuthForSecureLockDevice.value = requiresPrimaryAuth
    }

    fun setRequiresStrongBiometricAuthForSecureLockDevice(requiresStrongBiometricAuth: Boolean) {
        _requiresStrongBiometricAuthForSecureLockDevice.value = requiresStrongBiometricAuth
    }

    fun onSecureLockDeviceEnabled() {
        _isSecureLockDeviceEnabled.value = true
        _requiresPrimaryAuthForSecureLockDevice.value = true
        _requiresStrongBiometricAuthForSecureLockDevice.value = false
    }

    fun onSecureLockDeviceDisabled() {
        _isSecureLockDeviceEnabled.value = false
        _requiresPrimaryAuthForSecureLockDevice.value = false
        _requiresStrongBiometricAuthForSecureLockDevice.value = false
    }

    fun onSuccessfulPrimaryAuth() {
        _requiresPrimaryAuthForSecureLockDevice.value = false
        _requiresStrongBiometricAuthForSecureLockDevice.value = true
    }

    fun onBiometricLockout() {
        _requiresPrimaryAuthForSecureLockDevice.value = true
        _requiresStrongBiometricAuthForSecureLockDevice.value = false
    }
}
