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
package com.android.systemui.securelockdevice.dagger

import android.security.authenticationpolicy.AuthenticationPolicyManager
import com.android.internal.widget.LockPatternUtils
import com.android.systemui.biometrics.domain.interactor.FacePropertyInteractor
import com.android.systemui.biometrics.domain.interactor.FingerprintPropertyInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryBiometricSettingsInteractor
import com.android.systemui.deviceentry.domain.interactor.SystemUIDeviceEntryFaceAuthInteractor
import com.android.systemui.keyguard.data.repository.BiometricSettingsRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.log.dagger.SecureLockDeviceLog
import com.android.systemui.securelockdevice.data.repository.SecureLockDeviceRepository
import com.android.systemui.securelockdevice.data.repository.SecureLockDeviceRepositoryImpl
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import dagger.Lazy
import dagger.Module
import dagger.Provides
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope

/** Dagger module for secure lock device feature. */
@Module
interface SecureLockDeviceModule {

    companion object {
        @Provides
        @SysUISingleton
        fun providesSecureLockDeviceRepository(
            @Background backgroundExecutor: Executor,
            authenticationPolicyManager: AuthenticationPolicyManager?,
            biometricSettingsRepository: BiometricSettingsRepository,
        ): SecureLockDeviceRepository {
            return SecureLockDeviceRepositoryImpl(
                backgroundExecutor = backgroundExecutor,
                authenticationPolicyManager = authenticationPolicyManager,
                biometricSettingsRepository = biometricSettingsRepository,
            )
        }

        @Provides
        @SysUISingleton
        fun providesSecureLockDeviceInteractor(
            @Application applicationScope: CoroutineScope,
            @SecureLockDeviceLog logBuffer: LogBuffer,
            secureLockDeviceRepository: SecureLockDeviceRepository,
            biometricSettingsInteractor: DeviceEntryBiometricSettingsInteractor,
            deviceEntryFaceAuthInteractor: SystemUIDeviceEntryFaceAuthInteractor,
            fingerprintPropertyInteractor: Lazy<FingerprintPropertyInteractor>,
            facePropertyInteractor: FacePropertyInteractor,
            lockPatternUtils: LockPatternUtils,
            authenticationPolicyManager: AuthenticationPolicyManager?,
            selectedUserInteractor: SelectedUserInteractor,
            keyguardTransitionInteractor: Lazy<KeyguardTransitionInteractor>,
        ): SecureLockDeviceInteractor {
            return SecureLockDeviceInteractor(
                applicationScope = applicationScope,
                logBuffer = logBuffer,
                secureLockDeviceRepository = secureLockDeviceRepository,
                biometricSettingsInteractor = biometricSettingsInteractor,
                deviceEntryFaceAuthInteractor = deviceEntryFaceAuthInteractor,
                fingerprintPropertyInteractor = fingerprintPropertyInteractor,
                facePropertyInteractor = facePropertyInteractor,
                lockPatternUtils = lockPatternUtils,
                authenticationPolicyManager = authenticationPolicyManager,
                selectedUserInteractor = selectedUserInteractor,
                keyguardTransitionInteractor = keyguardTransitionInteractor,
            )
        }

        @Provides
        @SysUISingleton
        @SecureLockDeviceLog
        fun provideSecureLockDeviceLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("SecureLockDeviceLog", 100)
        }
    }
}
