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

import android.platform.test.annotations.EnableFlags
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.fakeFacePropertyRepository
import com.android.systemui.biometrics.data.repository.fakeFingerprintPropertyRepository
import com.android.systemui.biometrics.shared.model.FaceSensorInfo
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.keyguard.data.repository.fakeBiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.securelockdevice.data.repository.fakeSecureLockDeviceRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceEntryBiometricsAllowedInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val secureLockDeviceRepository = kosmos.fakeSecureLockDeviceRepository
    private lateinit var underTest: DeviceEntryBiometricsAllowedInteractor

    @Before
    fun setup() {
        underTest = kosmos.deviceEntryBiometricsAllowedInteractor
    }

    @Test
    fun isFingerprintAuthCurrentlyAllowed_initialFalse() =
        testScope.runTest {
            val fpAllowed by collectLastValue(underTest.isFingerprintAuthCurrentlyAllowed)
            assertThat(fpAllowed).isFalse()
        }

    @Test
    fun isFingerprintAuthCurrentlyAllowed_initialStateTrue() =
        testScope.runTest {
            kosmos.allowFingerprint()
            val fpAllowed by collectLastValue(underTest.isFingerprintAuthCurrentlyAllowed)
            assertThat(fpAllowed).isTrue()
        }

    @Test
    fun isFingerprintAuthCurrentlyAllowed_becomesTrue() =
        testScope.runTest {
            val fpAllowed by collectLastValue(underTest.isFingerprintAuthCurrentlyAllowed)

            // WHEN: not locked out, no face sensor, no strong auth requirements
            kosmos.allowFingerprint()

            // THEN fp is allowed
            assertThat(fpAllowed).isTrue()
        }

    @Test
    fun isFingerprintAuthCurrentlyAllowed_strongFaceLockedOut() =
        testScope.runTest {
            val fpAllowed by collectLastValue(underTest.isFingerprintAuthCurrentlyAllowed)

            // WHEN: not locked out, face is strong & locked out,  no strong auth requirements
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            kosmos.fakeFacePropertyRepository.setSensorInfo(
                FaceSensorInfo(id = 0, strength = SensorStrength.STRONG)
            )
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(true)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)

            // THEN fp is NOT allowed
            assertThat(fpAllowed).isFalse()
        }

    @Test
    fun isFingerprintAuthCurrentlyAllowed_convenienceFaceLockedOut() =
        testScope.runTest {
            val fpAllowed by collectLastValue(underTest.isFingerprintAuthCurrentlyAllowed)

            // WHEN: not locked out, face is convenience & locked out, no strong auth requirements
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            kosmos.fakeFacePropertyRepository.setSensorInfo(
                FaceSensorInfo(id = 0, strength = SensorStrength.CONVENIENCE)
            )
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(true)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)

            // THEN fp is allowed
            assertThat(fpAllowed).isTrue()
        }

    @Test
    fun isFingerprintAuthCurrentlyAllowed_primaryAuthRequired() =
        testScope.runTest {
            val fpAllowed by collectLastValue(underTest.isFingerprintAuthCurrentlyAllowed)

            // WHEN: not locked out, no face sensor,  no strong auth requirements
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(false)

            // THEN fp is NOT allowed
            assertThat(fpAllowed).isFalse()
        }

    @Test
    fun isFingerprintAuthCurrentlyAllowedOnBouncer_sfps() =
        testScope.runTest {
            val fpAllowedOnBouncer by
                collectLastValue(underTest.isFingerprintCurrentlyAllowedOnBouncer)

            // GIVEN fingerprint is generally allowed
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)

            // WHEN side fps
            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()

            // THEN fp is allowed on the primary bouncer
            assertThat(fpAllowedOnBouncer).isTrue()
        }

    @Test
    fun isFingerprintAuthCurrentlyAllowedOnBouncer_rearFps() =
        testScope.runTest {
            val fpAllowedOnBouncer by
                collectLastValue(underTest.isFingerprintCurrentlyAllowedOnBouncer)

            // GIVEN fingerprint is generally allowed
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)

            // WHEN rear fps
            kosmos.fakeFingerprintPropertyRepository.supportsRearFps()

            // THEN fp is allowed on the primary bouncer
            assertThat(fpAllowedOnBouncer).isTrue()
        }

    @Test
    fun isFingerprintAuthCurrentlyAllowedOnBouncer_udfps() =
        testScope.runTest {
            val fpAllowedOnBouncer by
                collectLastValue(underTest.isFingerprintCurrentlyAllowedOnBouncer)

            // GIVEN fp is generally allowed
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)

            // WHEN UDFPS
            kosmos.fakeFingerprintPropertyRepository.supportsUdfps()

            // THEN fp is never allowed on the primary bouncer
            assertThat(fpAllowedOnBouncer).isFalse()
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun isBiometricAuthCurrentlyAllowedOnBouncer_secureLockDeviceDisabled() =
        testScope.runTest {
            val fpAllowedOnBouncer by
                collectLastValue(underTest.isFingerprintCurrentlyAllowedOnBouncer)
            val faceAllowedOnBouncer by collectLastValue(underTest.isFaceCurrentlyAllowedOnBouncer)

            // GIVEN fp and face are generally allowed
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(false)
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthCurrentlyAllowed(true)

            // While secure lock device is disabled
            secureLockDeviceRepository.onSecureLockDeviceDisabled()
            runCurrent()

            // sfps allowed on bouncer
            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()
            assertThat(fpAllowedOnBouncer).isTrue()

            // rfps allowed on bouncer
            kosmos.fakeFingerprintPropertyRepository.supportsRearFps()
            assertThat(fpAllowedOnBouncer).isTrue()

            // udfps not allowed on bouncer
            kosmos.fakeFingerprintPropertyRepository.supportsUdfps()
            assertThat(fpAllowedOnBouncer).isFalse()

            // face allowed on bouncer
            kosmos.fakeFacePropertyRepository.setSensorInfo(
                FaceSensorInfo(id = 0, strength = SensorStrength.STRONG)
            )
            assertThat(faceAllowedOnBouncer).isTrue()
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun isFingerprintAuthCurrentlyAllowedOnBouncer_secureLockDeviceEnabled_primaryAuthStep() =
        testScope.runTest {
            val fpAllowedOnBouncer by
                collectLastValue(underTest.isFingerprintCurrentlyAllowedOnBouncer)

            // GIVEN fp is generally not allowed
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(false)

            // While secure lock device is enabled
            secureLockDeviceRepository.onSecureLockDeviceEnabled()
            runCurrent()

            // sfps not allowed on bouncer
            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()
            assertThat(fpAllowedOnBouncer).isFalse()

            // rfps not allowed on bouncer
            kosmos.fakeFingerprintPropertyRepository.supportsRearFps()
            assertThat(fpAllowedOnBouncer).isFalse()

            // udfps not allowed on bouncer
            kosmos.fakeFingerprintPropertyRepository.supportsUdfps()
            assertThat(fpAllowedOnBouncer).isFalse()
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun isFaceAuthCurrentlyAllowedOnBouncer_secureLockDeviceEnabled_primaryAuthStep() =
        testScope.runTest {
            val faceAllowedOnBouncer by collectLastValue(underTest.isFaceCurrentlyAllowedOnBouncer)

            // GIVEN face is generally allowed
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(false)
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthCurrentlyAllowed(false)

            // While secure lock device is enabled
            secureLockDeviceRepository.onSecureLockDeviceEnabled()
            runCurrent()

            // face auth not allowed on bouncer
            kosmos.fakeFacePropertyRepository.setSensorInfo(
                FaceSensorInfo(id = 0, strength = SensorStrength.STRONG)
            )
            assertThat(faceAllowedOnBouncer).isFalse()
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun isFingerprintAuthCurrentlyAllowedOnBouncer_secureLockDeviceEnabled_strongBiometricAuthStep() =
        testScope.runTest {
            val fpAllowedOnBouncer by
                collectLastValue(underTest.isFingerprintCurrentlyAllowedOnBouncer)

            // GIVEN fp is generally allowed
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)

            // While secure lock device is enabled
            secureLockDeviceRepository.onSecureLockDeviceEnabled()
            // After primary auth success
            secureLockDeviceRepository.onSuccessfulPrimaryAuth()
            runCurrent()

            // sfps allowed on bouncer
            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()
            assertThat(fpAllowedOnBouncer).isTrue()

            // rfps not allowed on bouncer
            kosmos.fakeFingerprintPropertyRepository.supportsRearFps()
            assertThat(fpAllowedOnBouncer).isTrue()

            // udfps allowed on bouncer
            kosmos.fakeFingerprintPropertyRepository.supportsUdfps()
            assertThat(fpAllowedOnBouncer).isTrue()
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun isFaceAuthCurrentlyAllowedOnBouncer_secureLockDeviceEnabled_strongBiometricAuthStep() =
        testScope.runTest {
            secureLockDeviceRepository.onSecureLockDeviceEnabled()
            val faceAllowedOnBouncer by collectLastValue(underTest.isFaceCurrentlyAllowedOnBouncer)

            // GIVEN face is generally allowed
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(false)
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthCurrentlyAllowed(true)

            // While secure lock device is enabled
            secureLockDeviceRepository.onSecureLockDeviceEnabled()
            // After primary auth success
            secureLockDeviceRepository.onSuccessfulPrimaryAuth()
            runCurrent()

            // face auth allowed on bouncer
            kosmos.fakeFacePropertyRepository.setSensorInfo(
                FaceSensorInfo(id = 0, strength = SensorStrength.STRONG)
            )
            assertThat(faceAllowedOnBouncer).isTrue()
        }
}
