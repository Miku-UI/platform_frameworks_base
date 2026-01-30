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

package com.android.systemui.securelockdevice.domain.interactor

import android.hardware.fingerprint.FingerprintSensorProperties.TYPE_POWER_BUTTON
import android.platform.test.annotations.EnableFlags
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.fakeFacePropertyRepository
import com.android.systemui.biometrics.data.repository.fakeFingerprintPropertyRepository
import com.android.systemui.biometrics.faceSensorPropertiesInternal
import com.android.systemui.biometrics.fingerprintSensorPropertiesInternal
import com.android.systemui.biometrics.shared.model.BiometricModalities
import com.android.systemui.biometrics.shared.model.BiometricModality
import com.android.systemui.biometrics.shared.model.toFaceSensorInfo
import com.android.systemui.biometrics.shared.model.toFingerprintSensorInfo
import com.android.systemui.biometrics.ui.viewmodel.PromptAuthState
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.keyguard.data.repository.biometricSettingsRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.securelockdevice.data.repository.fakeSecureLockDeviceRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_SECURE_LOCK_DEVICE)
class SecureLockDeviceInteractorTest : SysuiTestCase() {
    @JvmField @Rule var mockitoRule: MockitoRule = MockitoJUnit.rule()

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val underTest = kosmos.secureLockDeviceInteractor

    @Before
    fun setup() {
        kosmos.biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
        kosmos.biometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
    }

    @Test
    fun secureLockDeviceStateUpdates_acrossAuthenticationProgress() =
        kosmos.testScope.runTest {
            val isSecureLockDeviceEnabled by collectLastValue(underTest.isSecureLockDeviceEnabled)
            val requiresPrimaryAuthForSecureLockDevice by
                collectLastValue(underTest.requiresPrimaryAuthForSecureLockDevice)
            val requiresStrongBiometricAuthForSecureLockDevice by
                collectLastValue(underTest.requiresStrongBiometricAuthForSecureLockDevice)
            runCurrent()

            kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceEnabled()
            runCurrent()

            assertThat(isSecureLockDeviceEnabled).isEqualTo(true)
            assertThat(requiresPrimaryAuthForSecureLockDevice).isEqualTo(true)
            assertThat(requiresStrongBiometricAuthForSecureLockDevice).isEqualTo(false)

            kosmos.fakeSecureLockDeviceRepository.onSuccessfulPrimaryAuth()
            runCurrent()

            assertThat(isSecureLockDeviceEnabled).isEqualTo(true)
            assertThat(requiresPrimaryAuthForSecureLockDevice).isEqualTo(false)
            assertThat(requiresStrongBiometricAuthForSecureLockDevice).isEqualTo(true)

            kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceDisabled()
            runCurrent()

            assertThat(isSecureLockDeviceEnabled).isEqualTo(false)
            assertThat(requiresPrimaryAuthForSecureLockDevice).isEqualTo(false)
            assertThat(requiresStrongBiometricAuthForSecureLockDevice).isEqualTo(false)
        }

    @Test
    fun updatesModalitiesFromInteractor_strongFp() {
        testScope.runTest {
            val modalities by collectLastValue(underTest.enrolledStrongBiometricModalities)
            val fpSensorInfo =
                fingerprintSensorPropertiesInternal(sensorType = TYPE_POWER_BUTTON)
                    .first()
                    .toFingerprintSensorInfo()
            assertThat(modalities).isEqualTo(BiometricModalities())

            kosmos.biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()
            runCurrent()

            assertThat(modalities).isEqualTo(BiometricModalities(fpSensorInfo, null))
        }
    }

    @Test
    fun updatesModalitiesFromInteractor_strongFace() {
        testScope.runTest {
            val modalities by collectLastValue(underTest.enrolledStrongBiometricModalities)
            val faceSensorInfo = faceSensorPropertiesInternal().first().toFaceSensorInfo()
            assertThat(modalities).isEqualTo(BiometricModalities())

            kosmos.biometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
            kosmos.fakeFacePropertyRepository.setSensorInfo(faceSensorInfo)
            runCurrent()

            assertThat(modalities).isEqualTo(BiometricModalities(null, faceSensorInfo))
        }
    }

    @Test
    fun updatesModalitiesFromInteractor_strongCoex() {
        testScope.runTest {
            val modalities by collectLastValue(underTest.enrolledStrongBiometricModalities)
            val fpSensorInfo =
                fingerprintSensorPropertiesInternal(sensorType = TYPE_POWER_BUTTON)
                    .first()
                    .toFingerprintSensorInfo()
            val faceSensorInfo = faceSensorPropertiesInternal().first().toFaceSensorInfo()
            assertThat(modalities).isEqualTo(BiometricModalities())

            kosmos.biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            kosmos.biometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()
            kosmos.fakeFacePropertyRepository.setSensorInfo(faceSensorInfo)
            runCurrent()

            assertThat(modalities).isEqualTo(BiometricModalities(fpSensorInfo, faceSensorInfo))
        }
    }

    @Test
    fun updatesSuppressBouncerMessageUpdatesFromRepository() {
        testScope.runTest {
            val suppressBouncerMessageUpdates by
                collectLastValue(underTest.suppressBouncerMessageUpdates)

            kosmos.fakeSecureLockDeviceRepository.suppressBouncerMessageUpdates.value = true
            runCurrent()
            assertThat(suppressBouncerMessageUpdates).isTrue()

            kosmos.fakeSecureLockDeviceRepository.suppressBouncerMessageUpdates.value = false
            runCurrent()
            assertThat(suppressBouncerMessageUpdates).isFalse()
        }
    }

    @Test
    fun showsConfirmButtonOnPendingFaceAuth_hidesOnBiometricAuthHidden() {
        testScope.runTest {
            val showingConfirmButton by collectLastValue(underTest.showConfirmBiometricAuthButton)

            underTest.onBiometricAuthRequested()
            runCurrent()
            assertThat(showingConfirmButton).isFalse()

            underTest.onBiometricAuthenticatedStateUpdated(
                PromptAuthState(
                    isAuthenticated = true,
                    authenticatedModality = BiometricModality.Face,
                    needsUserConfirmation = true,
                )
            )
            runCurrent()
            assertThat(showingConfirmButton).isTrue()

            underTest.onBiometricAuthUiHidden()
            runCurrent()
            assertThat(showingConfirmButton).isFalse()
        }
    }

    @Test
    fun stopsListeningForBiometricAuth_whileConfirmButtonIsShown() {
        testScope.runTest {
            val shouldListenForBiometricAuth by
                collectLastValue(underTest.shouldListenForBiometricAuth)
            val showingConfirmButton by collectLastValue(underTest.showConfirmBiometricAuthButton)

            kosmos.fakeSecureLockDeviceRepository.setRequiresStrongBiometricAuthForSecureLockDevice(
                true
            )
            underTest.onBiometricAuthRequested()
            runCurrent()
            assertThat(shouldListenForBiometricAuth).isTrue()

            underTest.onBiometricAuthenticatedStateUpdated(
                PromptAuthState(
                    isAuthenticated = true,
                    authenticatedModality = BiometricModality.Face,
                    needsUserConfirmation = true,
                )
            )
            runCurrent()
            assertThat(showingConfirmButton).isTrue()
            assertThat(shouldListenForBiometricAuth).isFalse()
        }
    }

    @Test
    fun onBiometricAuthRequested_resetsStateAndShowsBiometricAuthUi() =
        testScope.runTest {
            val isBiometricAuthVisible by collectLastValue(underTest.isBiometricAuthVisible)
            val showConfirmBiometricAuthButton by
                collectLastValue(underTest.showConfirmBiometricAuthButton)
            val showTryAgainButton by collectLastValue(underTest.showTryAgainButton)
            val showingError by collectLastValue(underTest.showingError)
            val suppressBouncerMessageUpdates by
                collectLastValue(underTest.suppressBouncerMessageUpdates)

            underTest.onBiometricAuthRequested()
            runCurrent()

            assertThat(isBiometricAuthVisible).isTrue()
            assertThat(showConfirmBiometricAuthButton).isFalse()
            assertThat(showTryAgainButton).isFalse()
            assertThat(showingError).isFalse()
            assertThat(suppressBouncerMessageUpdates).isFalse()
        }

    @Test
    fun onBiometricAuthUiHiddenWithoutAuthenticationComplete_resetsUIState() =
        testScope.runTest {
            val isBiometricAuthVisible by collectLastValue(underTest.isBiometricAuthVisible)
            val showConfirmBiometricAuthButton by
                collectLastValue(underTest.showConfirmBiometricAuthButton)
            val showTryAgainButton by collectLastValue(underTest.showTryAgainButton)
            val showingError by collectLastValue(underTest.showingError)
            val suppressBouncerMessageUpdates by
                collectLastValue(underTest.suppressBouncerMessageUpdates)

            // Sample state, pending face auth confirmation, suppressing bouncer message updates
            underTest.onBiometricAuthRequested()
            underTest.onBiometricAuthenticatedStateUpdated(
                PromptAuthState(
                    isAuthenticated = true,
                    authenticatedModality = BiometricModality.Face,
                    needsUserConfirmation = true,
                )
            )
            underTest.suppressBouncerMessages()
            runCurrent()

            assertThat(isBiometricAuthVisible).isTrue()
            assertThat(showConfirmBiometricAuthButton).isTrue()
            assertThat(showingError).isFalse()
            assertThat(suppressBouncerMessageUpdates).isTrue()

            // Biometric auth UI is hidden without confirmation (e.g. screen turned off)
            underTest.onBiometricAuthUiHidden()
            runCurrent()

            assertThat(isBiometricAuthVisible).isFalse()
            assertThat(showConfirmBiometricAuthButton).isFalse()
            assertThat(showTryAgainButton).isFalse()
            assertThat(showingError).isFalse()
            assertThat(suppressBouncerMessageUpdates).isFalse()
        }

    @Test
    fun onGoneTransitionFinished_resetsUIState() =
        testScope.runTest {
            val isBiometricAuthVisible by collectLastValue(underTest.isBiometricAuthVisible)
            val isFullyUnlockedAndReadyToDismiss by
                collectLastValue(underTest.isFullyUnlockedAndReadyToDismiss)
            val showConfirmBiometricAuthButton by
                collectLastValue(underTest.showConfirmBiometricAuthButton)
            val showTryAgainButton by collectLastValue(underTest.showTryAgainButton)
            val showingError by collectLastValue(underTest.showingError)
            val suppressBouncerMessageUpdates by
                collectLastValue(underTest.suppressBouncerMessageUpdates)

            // Authentication complete, animations played, ready to dismiss
            underTest.onBiometricAuthRequested()
            underTest.onBiometricAuthenticatedStateUpdated(
                PromptAuthState(
                    isAuthenticated = true,
                    authenticatedModality = BiometricModality.Face,
                    needsUserConfirmation = true,
                )
            )
            underTest.suppressBouncerMessages()
            underTest.onReadyToDismissBiometricAuth()
            runCurrent()

            assertThat(isBiometricAuthVisible).isTrue()
            assertThat(isFullyUnlockedAndReadyToDismiss).isTrue()

            underTest.onGoneTransitionFinished()
            runCurrent()

            assertThat(isBiometricAuthVisible).isFalse()
            assertThat(isFullyUnlockedAndReadyToDismiss).isFalse()
            assertThat(showConfirmBiometricAuthButton).isFalse()
            assertThat(showTryAgainButton).isFalse()
            assertThat(showingError).isFalse()
            assertThat(suppressBouncerMessageUpdates).isFalse()
        }
}
