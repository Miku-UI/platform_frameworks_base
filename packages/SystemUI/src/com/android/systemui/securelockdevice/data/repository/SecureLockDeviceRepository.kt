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

import android.security.Flags.secureLockDevice
import android.security.authenticationpolicy.AuthenticationPolicyManager
import android.util.Log
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.data.repository.BiometricSettingsRepository
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/** A repository for the state of secure lock device. */
interface SecureLockDeviceRepository {
    /**
     * Whether bouncer messages should be suppressed. This is true after the user has successfully
     * authenticated a strong biometric in the secure lock device UI, in order to prevent animation
     * jank if the keyguard mode resets back to the LSKF security mode before the UI is dismissed.
     */
    val suppressBouncerMessageUpdates: MutableStateFlow<Boolean>
    val isSecureLockDeviceEnabled: Flow<Boolean>

    /** @see BiometricSettingsRepository.requiresPrimaryAuthForSecureLockDevice */
    val requiresPrimaryAuthForSecureLockDevice: Flow<Boolean>

    /** @see BiometricSettingsRepository.requiresStrongBiometricAuthForSecureLockDevice */
    val requiresStrongBiometricAuthForSecureLockDevice: Flow<Boolean>
}

@SysUISingleton
class SecureLockDeviceRepositoryImpl
@Inject
constructor(
    @Background backgroundExecutor: Executor,
    authenticationPolicyManager: AuthenticationPolicyManager?,
    biometricSettingsRepository: BiometricSettingsRepository,
) : SecureLockDeviceRepository {
    override val isSecureLockDeviceEnabled: Flow<Boolean> =
        if (secureLockDevice()) {
            callbackFlow {
                val updateSecureLockDeviceEnabledState = { enabled: Boolean ->
                    Log.d(TAG, "isSecureLockDeviceEnabled updated: $enabled")
                    trySendWithFailureLogging(
                        enabled,
                        TAG,
                        "Error sending isSecureLockDeviceEnabled state",
                    )
                }

                val listener =
                    object : AuthenticationPolicyManager.SecureLockDeviceStatusListener {
                        override fun onSecureLockDeviceEnabledStatusChanged(enabled: Boolean) {
                            updateSecureLockDeviceEnabledState(enabled)
                        }

                        override fun onSecureLockDeviceAvailableStatusChanged(
                            @AuthenticationPolicyManager.GetSecureLockDeviceAvailabilityRequestStatus
                            available: Int
                        ) {}
                    }

                updateSecureLockDeviceEnabledState(false)
                authenticationPolicyManager?.registerSecureLockDeviceStatusListener(
                    backgroundExecutor,
                    listener,
                )
                awaitClose {
                    authenticationPolicyManager?.unregisterSecureLockDeviceStatusListener(listener)
                }
            }
        } else {
            flowOf(false)
        }

    override val requiresPrimaryAuthForSecureLockDevice: Flow<Boolean> =
        biometricSettingsRepository.requiresPrimaryAuthForSecureLockDevice

    override val requiresStrongBiometricAuthForSecureLockDevice: Flow<Boolean> =
        biometricSettingsRepository.requiresStrongBiometricAuthForSecureLockDevice

    override val suppressBouncerMessageUpdates = MutableStateFlow(false)

    companion object {
        private const val TAG = "SecureLockDeviceRepositoryImpl"
    }
}
