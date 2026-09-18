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
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.fingerprintPropertyRepository
import com.android.systemui.biometrics.shared.model.FingerprintSensorType
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.deviceentry.shared.model.FailedFaceAuthenticationStatus
import com.android.systemui.keyevent.data.repository.fakeKeyEventRepository
import com.android.systemui.keyguard.data.repository.biometricSettingsRepository
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.keyguard.shared.model.FailFingerprintAuthenticationStatus
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.power.data.repository.powerRepository
import com.android.systemui.power.shared.model.WakeSleepReason
import com.android.systemui.power.shared.model.WakefulnessState
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceEntryHapticsInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Test
    fun nonPowerButtonFPS_vibrateSuccess() =
        kosmos.runTest {
            val playSuccessHaptic by
                collectLastValue(deviceEntryHapticsInteractor.playSuccessHapticOnDeviceEntry)
            enrollFingerprint(FingerprintSensorType.UDFPS_ULTRASONIC)
            runCurrent()
            enterDeviceFromFingerprintUnlock()
            assertThat(playSuccessHaptic).isNotNull()
        }

    @Test
    fun powerButtonFPS_vibrateSuccess() =
        kosmos.runTest {
            val playSuccessHaptic by
                collectLastValue(deviceEntryHapticsInteractor.playSuccessHapticOnDeviceEntry)
            enrollFingerprint(FingerprintSensorType.POWER_BUTTON)
            kosmos.fakeKeyEventRepository.setPowerButtonDown(false)

            // It's been 10 seconds since the last power button wakeup
            setAwakeFromPowerButton()
            advanceTimeBy(10000)
            runCurrent()

            enterDeviceFromFingerprintUnlock()
            assertThat(playSuccessHaptic).isNotNull()
        }

    @Test
    fun powerButtonFPS_powerDown_doNotVibrateSuccess() =
        kosmos.runTest {
            val playSuccessHaptic by
                collectLastValue(deviceEntryHapticsInteractor.playSuccessHapticOnDeviceEntry)
            enrollFingerprint(FingerprintSensorType.POWER_BUTTON)
            kosmos.fakeKeyEventRepository.setPowerButtonDown(true) // power button is currently DOWN

            // It's been 10 seconds since the last power button wakeup
            setAwakeFromPowerButton()
            advanceTimeBy(10000)
            runCurrent()

            enterDeviceFromFingerprintUnlock()
            assertThat(playSuccessHaptic).isNull()
        }

    @Test
    fun powerButtonFPS_powerButtonRecentlyPressed_doNotVibrateSuccess() =
        kosmos.runTest {
            val playSuccessHaptic by
                collectLastValue(deviceEntryHapticsInteractor.playSuccessHapticOnDeviceEntry)
            enrollFingerprint(FingerprintSensorType.POWER_BUTTON)
            kosmos.fakeKeyEventRepository.setPowerButtonDown(false)

            // It's only been 50ms since the last power button wakeup
            setAwakeFromPowerButton()
            advanceTimeBy(50)
            runCurrent()

            enterDeviceFromFingerprintUnlock()
            assertThat(playSuccessHaptic).isNull()
        }

    @Test
    fun nonPowerButtonFPS_vibrateError() =
        kosmos.runTest {
            val playErrorHaptic by collectLastValue(deviceEntryHapticsInteractor.playErrorHaptic)
            enrollFingerprint(FingerprintSensorType.UDFPS_ULTRASONIC)
            runCurrent()
            fingerprintFailure()
            assertThat(playErrorHaptic).isNotNull()
        }

    @Test
    fun nonPowerButtonFPS_coExFaceFailure_doNotVibrateError() =
        kosmos.runTest {
            val playErrorHaptic by collectLastValue(deviceEntryHapticsInteractor.playErrorHaptic)
            enrollFingerprint(FingerprintSensorType.UDFPS_ULTRASONIC)
            enrollFace()
            runCurrent()
            faceFailure()
            assertThat(playErrorHaptic).isNull()
        }

    @Test
    fun powerButtonFPS_vibrateError() =
        kosmos.runTest {
            val playErrorHaptic by collectLastValue(deviceEntryHapticsInteractor.playErrorHaptic)
            enrollFingerprint(FingerprintSensorType.POWER_BUTTON)
            runCurrent()
            fingerprintFailure()
            assertThat(playErrorHaptic).isNotNull()
        }

    @Test
    fun powerButtonFPS_powerDown_doNotVibrateError() =
        kosmos.runTest {
            val playErrorHaptic by collectLastValue(deviceEntryHapticsInteractor.playErrorHaptic)
            enrollFingerprint(FingerprintSensorType.POWER_BUTTON)
            kosmos.fakeKeyEventRepository.setPowerButtonDown(true)
            runCurrent()
            fingerprintFailure()
            assertThat(playErrorHaptic).isNull()
        }

    @Test
    fun playSuccessHaptic_onDeviceEntry_fromDeviceEntryIcon() =
        kosmos.runTest {
            val playSuccessHaptic by
                collectLastValue(deviceEntryHapticsInteractor.playSuccessHapticOnDeviceEntry)

            kosmos.fakeKeyguardRepository.setKeyguardDismissible(true)
            runCurrent()
            kosmos.deviceEntrySourceInteractor.attemptEnterDeviceFromDeviceEntryIcon()

            assertThat(playSuccessHaptic).isNotNull()
        }

    private fun enterDeviceFromFingerprintUnlock() {
        kosmos.fakeKeyguardRepository.setBiometricUnlockSource(
            BiometricUnlockSource.FINGERPRINT_SENSOR
        )
        kosmos.fakeKeyguardRepository.setBiometricUnlockState(BiometricUnlockMode.WAKE_AND_DISMISS)
    }

    private fun fingerprintFailure() {
        kosmos.deviceEntryFingerprintAuthRepository.setAuthenticationStatus(
            FailFingerprintAuthenticationStatus
        )
    }

    private fun faceFailure() {
        kosmos.fakeDeviceEntryFaceAuthRepository.setAuthenticationStatus(
            FailedFaceAuthenticationStatus()
        )
    }

    private fun enrollFingerprint(fingerprintSensorType: FingerprintSensorType?) {
        if (fingerprintSensorType == null) {
            kosmos.biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(false)
        } else {
            kosmos.biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            kosmos.fingerprintPropertyRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = fingerprintSensorType,
                sensorLocations = mapOf(),
            )
        }
    }

    private fun enrollFace() {
        kosmos.biometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
    }

    private fun setAwakeFromPowerButton() {
        kosmos.powerRepository.updateWakefulness(
            WakefulnessState.AWAKE,
            WakeSleepReason.POWER_BUTTON,
            WakeSleepReason.POWER_BUTTON,
            powerButtonLaunchGestureTriggered = false,
        )
    }
}
