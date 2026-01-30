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

package com.android.systemui.securelockdevice.ui.viewmodel

import android.platform.test.annotations.EnableFlags
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.shared.model.BiometricModality
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.haptics.msdl.fakeMSDLPlayer
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.securelockdevice.data.repository.fakeSecureLockDeviceRepository
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import com.android.systemui.securelockdevice.domain.interactor.secureLockDeviceInteractor
import com.android.systemui.testKosmos
import com.google.android.msdl.data.model.MSDLToken
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper(setAsMainLooper = true)
@EnableFlags(FLAG_SECURE_LOCK_DEVICE)
class SecureLockDeviceBiometricAuthContentViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private lateinit var secureLockDeviceInteractor: SecureLockDeviceInteractor

    @Before
    fun setUp() {
        kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceEnabled()
        kosmos.fakeSecureLockDeviceRepository.onSuccessfulPrimaryAuth()
        secureLockDeviceInteractor = kosmos.secureLockDeviceInteractor
    }

    @Test
    fun onBiometricAuthRequested_showsAuthenticating_() =
        testScope.runTest {
            val underTest = kosmos.secureLockDeviceBiometricAuthContentViewModel
            underTest.activateIn(this)
            val isAuthenticating by collectLastValue(underTest.isAuthenticating)

            secureLockDeviceInteractor.onBiometricAuthRequested()
            runCurrent()

            assertThat(isAuthenticating).isTrue()
        }

    @Test
    fun updatesStateAndPlaysHaptics_onFaceFailureOrError() =
        testScope.runTest {
            val underTest = kosmos.secureLockDeviceBiometricAuthContentViewModel
            underTest.activateIn(this)
            val isAuthenticating by collectValues(underTest.isAuthenticating)
            val isAuthenticated by collectLastValue(underTest.isAuthenticated)
            val showingError by collectValues(underTest.showingError)

            // On face error shown
            underTest.showTemporaryError(
                authenticateAfterError = false,
                failedModality = BiometricModality.Face,
            )

            // Verify internal state updated to show error
            assertThat(isAuthenticating[1]).isFalse()
            assertThat(showingError[1]).isTrue()
            assertThat(isAuthenticated?.isAuthenticated).isFalse()
            assertThat(kosmos.fakeMSDLPlayer.latestTokenPlayed).isEqualTo(MSDLToken.FAILURE)

            runCurrent()

            // Verify internal state updated to clear error
            assertThat(showingError[2]).isFalse()
        }

    @Test
    fun updatesStateOnRetryAfterFaceFailureOrError() =
        testScope.runTest {
            val underTest = kosmos.secureLockDeviceBiometricAuthContentViewModel
            underTest.activateIn(this)
            val isAuthenticating by collectLastValue(underTest.isAuthenticating)
            val isAuthenticated by collectLastValue(underTest.isAuthenticated)
            val showingError by collectLastValue(underTest.showingError)
            val canTryAgainNow by collectLastValue(underTest.canTryAgainNow)

            // On face error shown
            underTest.showTemporaryError(
                authenticateAfterError = false,
                failedModality = BiometricModality.Face,
            )
            runCurrent()

            // On retry button clicked
            underTest.onTryAgainButtonClicked()

            // Verify internal state updated to restart authentication
            assertThat(isAuthenticating).isTrue()
            assertThat(showingError).isFalse()
            assertThat(isAuthenticated?.isAuthenticated).isFalse()
            assertThat(canTryAgainNow).isFalse()
        }

    @Test
    fun updatesStateAndSkipsHaptics_onFaceHelp() =
        testScope.runTest {
            val underTest = kosmos.secureLockDeviceBiometricAuthContentViewModel
            underTest.activateIn(this)
            val isAuthenticating by collectLastValue(underTest.isAuthenticating)
            val isAuthenticated by collectLastValue(underTest.isAuthenticated)
            val showingError by collectLastValue(underTest.showingError)

            // On face help shown
            underTest.showHelp()
            runCurrent()

            // Verify internal state updated to show help
            assertThat(isAuthenticating).isFalse()
            assertThat(showingError).isFalse()
            assertThat(isAuthenticated?.isAuthenticated).isFalse()
            assertThat(kosmos.fakeMSDLPlayer.latestTokenPlayed).isNull()
        }

    @Test
    fun updatesState_onFaceSuccess_andPlaysHapticsOnConfirm() =
        testScope.runTest {
            val underTest = kosmos.secureLockDeviceBiometricAuthContentViewModel
            underTest.activateIn(this)
            val isAuthenticating by collectLastValue(underTest.isAuthenticating)
            val isAuthenticated by collectLastValue(underTest.isAuthenticated)
            val showingError by collectLastValue(underTest.showingError)

            // On face success shown
            underTest.showAuthenticated(modality = BiometricModality.Face)
            runCurrent()

            // Verify internal state updated to show success
            assertThat(isAuthenticating).isFalse()
            assertThat(showingError).isFalse()
            assertThat(isAuthenticated?.isAuthenticated).isTrue()
            assertThat(isAuthenticated?.isAuthenticatedAndExplicitlyConfirmed).isFalse()
            assertThat(kosmos.fakeMSDLPlayer.latestTokenPlayed).isNull()

            underTest.onConfirmButtonClicked()
            runCurrent()

            // Verify internal state updated to show confirmed
            assertThat(isAuthenticating).isFalse()
            assertThat(showingError).isFalse()
            assertThat(isAuthenticated?.isAuthenticatedAndExplicitlyConfirmed).isTrue()
            assertThat(kosmos.fakeMSDLPlayer.latestTokenPlayed).isEqualTo(MSDLToken.UNLOCK)
        }

    @Test
    fun updatesStateAndPlaysHaptics_onFingerprintFailureOrError() =
        testScope.runTest {
            val underTest = kosmos.secureLockDeviceBiometricAuthContentViewModel
            underTest.activateIn(this)
            val isAuthenticating by collectValues(underTest.isAuthenticating)
            val isAuthenticated by collectLastValue(underTest.isAuthenticated)
            val showingError by collectValues(underTest.showingError)

            // On fingerprint error shown
            underTest.showTemporaryError(
                authenticateAfterError = true,
                failedModality = BiometricModality.Fingerprint,
            )

            // Verify internal state updated to show error
            assertThat(isAuthenticating[1]).isFalse()
            assertThat(showingError[1]).isTrue()
            assertThat(isAuthenticated?.isAuthenticated).isFalse()
            assertThat(kosmos.fakeMSDLPlayer.latestTokenPlayed).isEqualTo(MSDLToken.FAILURE)

            // Verify internal state updated to clear error, restart authentication
            assertThat(isAuthenticating[2]).isTrue()
            assertThat(showingError[2]).isFalse()
            assertThat(isAuthenticated?.isAuthenticated).isFalse()
        }

    @Test
    fun updatesStateAndSkipsHaptics_onFingerprintHelp() =
        testScope.runTest {
            val underTest = kosmos.secureLockDeviceBiometricAuthContentViewModel
            underTest.activateIn(this)
            val isAuthenticating by collectLastValue(underTest.isAuthenticating)
            val isAuthenticated by collectLastValue(underTest.isAuthenticated)
            val showingError by collectLastValue(underTest.showingError)

            // On face error shown
            underTest.showHelp()
            runCurrent()

            // Verify internal state updated to show help
            assertThat(isAuthenticating).isFalse()
            assertThat(showingError).isFalse()
            assertThat(isAuthenticated?.isAuthenticated).isFalse()
            assertThat(kosmos.fakeMSDLPlayer.latestTokenPlayed).isNull()
        }

    @Test
    fun updatesStateAndPlaysHaptics_onFingerprintSuccess() =
        testScope.runTest {
            val underTest = kosmos.secureLockDeviceBiometricAuthContentViewModel
            underTest.activateIn(this)
            val isAuthenticating by collectLastValue(underTest.isAuthenticating)
            val isAuthenticated by collectLastValue(underTest.isAuthenticated)
            val showingError by collectLastValue(underTest.showingError)

            // On fingerprint success shown
            underTest.showAuthenticated(modality = BiometricModality.Fingerprint)
            runCurrent()

            // Verify internal state updated to show success
            assertThat(isAuthenticating).isFalse()
            assertThat(showingError).isFalse()
            assertThat(isAuthenticated?.isAuthenticated).isTrue()
            assertThat(isAuthenticated?.isAuthenticatedAndConfirmed).isTrue()
            assertThat(kosmos.fakeMSDLPlayer.latestTokenPlayed).isEqualTo(MSDLToken.UNLOCK)
        }

    @Test
    fun onSuccessfulAuthentication_notifiesInteractorAndHides_uponAnimationFinished() =
        testScope.runTest {
            val underTest = kosmos.secureLockDeviceBiometricAuthContentViewModel
            underTest.activateIn(this)
            val isReadyToDismiss by
                collectLastValue(secureLockDeviceInteractor.isFullyUnlockedAndReadyToDismiss)

            underTest.startAppearAnimation()
            runCurrent()

            assertThat(underTest.isVisible).isTrue()
            assertThat(isReadyToDismiss).isFalse()

            underTest.showAuthenticated(BiometricModality.Fingerprint)
            runCurrent()

            assertThat(underTest.isAuthenticationComplete).isTrue()
            assertThat(isReadyToDismiss).isFalse()

            underTest.onIconAnimationFinished()
            runCurrent()

            assertThat(isReadyToDismiss).isTrue()
            assertThat(underTest.isVisible).isFalse()
        }

    @Test
    fun onDeactivated_hidesComposable_notifiesInteractor() =
        testScope.runTest {
            val underTest = kosmos.secureLockDeviceBiometricAuthContentViewModel
            val job = Job()
            underTest.activateIn(this, job)
            runCurrent()

            secureLockDeviceInteractor.onBiometricAuthRequested()
            underTest.startAppearAnimation()
            runCurrent()

            assertThat(underTest.isVisible).isTrue()
            assertThat(secureLockDeviceInteractor.isBiometricAuthVisible.value).isTrue()

            job.cancel()
            runCurrent()

            // THEN the viewmodel is no longer visible and the interactor is notified
            assertThat(underTest.isVisible).isFalse()
            assertThat(secureLockDeviceInteractor.isBiometricAuthVisible.value).isFalse()
        }
}
