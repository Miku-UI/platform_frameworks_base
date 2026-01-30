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
 *
 */

package com.android.systemui.deviceentry.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.fingerprintPropertyRepository
import com.android.systemui.keyguard.data.repository.biometricSettingsRepository
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceEntryUdfpsInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val underTest: DeviceEntryUdfpsInteractor = kosmos.deviceEntryUdfpsInteractor

    @Test
    fun udfpsSupported_rearFp_false() =
        kosmos.runTest {
            val isUdfpsSupported by collectLastValue(underTest.isUdfpsSupported)
            fingerprintPropertyRepository.supportsRearFps()
            assertThat(isUdfpsSupported).isFalse()
        }

    @Test
    fun udfpsSupoprted() =
        kosmos.runTest {
            val isUdfpsSupported by collectLastValue(underTest.isUdfpsSupported)
            fingerprintPropertyRepository.supportsUdfps()
            assertThat(isUdfpsSupported).isTrue()
        }

    @Test
    fun udfpsEnrolledAndEnabled() =
        kosmos.runTest {
            val isUdfpsEnrolledAndEnabled by collectLastValue(underTest.isUdfpsEnrolledAndEnabled)
            fingerprintPropertyRepository.supportsUdfps()
            biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            assertThat(isUdfpsEnrolledAndEnabled).isTrue()
        }

    @Test
    fun udfpsEnrolledAndEnabled_rearFp_false() =
        kosmos.runTest {
            val isUdfpsEnrolledAndEnabled by collectLastValue(underTest.isUdfpsEnrolledAndEnabled)
            fingerprintPropertyRepository.supportsRearFps()
            biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            assertThat(isUdfpsEnrolledAndEnabled).isFalse()
        }

    @Test
    fun udfpsEnrolledAndEnabled_notEnrolledOrEnabled_false() =
        kosmos.runTest {
            val isUdfpsEnrolledAndEnabled by collectLastValue(underTest.isUdfpsEnrolledAndEnabled)
            fingerprintPropertyRepository.supportsUdfps()
            biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(false)
            assertThat(isUdfpsEnrolledAndEnabled).isFalse()
        }

    @Test
    fun isListeningForUdfps() =
        kosmos.runTest {
            val isListeningForUdfps by collectLastValue(underTest.isListeningForUdfps)
            fingerprintPropertyRepository.supportsUdfps()
            deviceEntryFingerprintAuthRepository.setIsRunning(true)
            assertThat(isListeningForUdfps).isTrue()
        }

    @Test
    fun isListeningForUdfps_rearFp_false() =
        kosmos.runTest {
            val isListeningForUdfps by collectLastValue(underTest.isListeningForUdfps)
            fingerprintPropertyRepository.supportsRearFps()
            deviceEntryFingerprintAuthRepository.setIsRunning(true)
            assertThat(isListeningForUdfps).isFalse()
        }

    @Test
    fun isListeningForUdfps_notRunning_false() =
        kosmos.runTest {
            val isListeningForUdfps by collectLastValue(underTest.isListeningForUdfps)
            fingerprintPropertyRepository.supportsUdfps()
            deviceEntryFingerprintAuthRepository.setIsRunning(false)
            assertThat(isListeningForUdfps).isFalse()
        }
}
