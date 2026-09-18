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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.keyguard.keyguardUpdateMonitor
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.biometrics.authController
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.statusbar.phone.dozeScrimController
import com.android.systemui.statusbar.phone.screenOffAnimationController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceEntrySourceInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val underTest: DeviceEntrySourceInteractor by lazy {
        kosmos.deviceEntrySourceInteractor
    }

    @Before
    fun setup() {
        with(kosmos) {
            if (SceneContainerFlag.isEnabled) {
                whenever(authController.isUdfpsFingerDown).thenReturn(false)
                whenever(dozeScrimController.isPulsing).thenReturn(false)
                whenever(keyguardUpdateMonitor.isUnlockingWithBiometricAllowed(anyBoolean()))
                    .thenReturn(true)
                whenever(screenOffAnimationController.isKeyguardShowDelayed()).thenReturn(false)
                fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
            }
        }
    }

    @Test
    fun deviceEntryFromFaceUnlock() =
        kosmos.runTest {
            val deviceEntryFromBiometricAuthentication by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            fakeKeyguardRepository.setBiometricUnlockState(
                BiometricUnlockMode.WAKE_AND_DISMISS,
                BiometricUnlockSource.FACE_SENSOR,
            )
            runCurrent()

            assertThat(deviceEntryFromBiometricAuthentication)
                .isEqualTo(BiometricUnlockSource.FACE_SENSOR)
        }

    @Test
    fun deviceEntryFromFingerprintUnlock() =
        kosmos.runTest {
            val deviceEntryFromBiometricAuthentication by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            fakeKeyguardRepository.setBiometricUnlockState(
                BiometricUnlockMode.WAKE_AND_DISMISS,
                BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()

            assertThat(deviceEntryFromBiometricAuthentication)
                .isEqualTo(BiometricUnlockSource.FINGERPRINT_SENSOR)
        }

    @Test
    fun noDeviceEntry() =
        kosmos.runTest {
            val deviceEntryFromBiometricAuthentication by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            fakeKeyguardRepository.setBiometricUnlockState(
                BiometricUnlockMode.ONLY_WAKE, // doesn't dismiss keyguard:
                BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()

            assertThat(deviceEntryFromBiometricAuthentication).isNull()
        }
}
