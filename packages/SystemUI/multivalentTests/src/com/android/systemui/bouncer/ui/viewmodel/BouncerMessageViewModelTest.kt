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

package com.android.systemui.bouncer.ui.viewmodel

import android.content.pm.UserInfo
import android.hardware.biometrics.BiometricFaceConstants
import android.hardware.biometrics.BiometricFaceConstants.FACE_ERROR_LOCKOUT
import android.hardware.biometrics.BiometricFingerprintConstants.FINGERPRINT_ERROR_LOCKOUT
import android.hardware.fingerprint.FingerprintManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import androidx.test.filters.SmallTest
import com.android.internal.widget.LockPatternUtils
import com.android.internal.widget.LockPatternUtils.StrongAuthTracker.PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE
import com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE
import com.android.systemui.Flags.FLAG_STRONG_AUTH_REQUIRED_AFTER_SIGN_OUT_MESSAGE_FIX
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.fakeAccessibilityRepository
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository.Companion.WRONG_PASSWORD
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository.Companion.WRONG_PATTERN
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository.Companion.WRONG_PIN
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Password
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pattern
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pin
import com.android.systemui.authentication.shared.model.AuthenticationPatternCoordinate
import com.android.systemui.authentication.shared.model.AuthenticationResult
import com.android.systemui.biometrics.FaceHelpMessageDebouncer
import com.android.systemui.biometrics.data.repository.fakeFacePropertyRepository
import com.android.systemui.biometrics.data.repository.fakeFingerprintPropertyRepository
import com.android.systemui.biometrics.shared.model.FaceSensorInfo
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.bouncer.domain.interactor.bouncerInteractor
import com.android.systemui.bouncer.ui.viewmodel.BouncerMessageViewModel.Companion.DEFAULT_MESSAGE_DURATION
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.deviceentry.domain.interactor.DeviceUnlockedInteractor
import com.android.systemui.deviceentry.shared.model.ErrorFaceAuthenticationStatus
import com.android.systemui.deviceentry.shared.model.FaceAuthenticationStatus
import com.android.systemui.deviceentry.shared.model.FailedFaceAuthenticationStatus
import com.android.systemui.deviceentry.shared.model.HelpFaceAuthenticationStatus
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.fakeSystemPropertiesHelper
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeBiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeTrustRepository
import com.android.systemui.keyguard.shared.model.AuthenticationFlags
import com.android.systemui.keyguard.shared.model.ErrorFingerprintAuthenticationStatus
import com.android.systemui.keyguard.shared.model.FailFingerprintAuthenticationStatus
import com.android.systemui.keyguard.shared.model.FingerprintAuthenticationStatus
import com.android.systemui.keyguard.shared.model.HelpFingerprintAuthenticationStatus
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.securelockdevice.data.repository.fakeSecureLockDeviceRepository
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.data.repository.userSwitcherRepository
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.Parameter
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

data class Params(
    val wrongCred: List<Any>,
    val generateWrongCredSuffix: (index: Int) -> List<Any>,
    val enterCredString: String,
    val wrongCredString: String,
    val fpOrCredString: String,
    val duplicateCredString: String,
    val lockedOutString: String,
    val deviceUpdatedString: String,
    val tooManyAttemptsString: String,
    val afterRestartString: String,
    val afterSignOutString: String,
    val afterTimeoutString: String,
    val afterLockoutString: String,
    val unattendedUpdateString: String,
    val tryAgainOrCredString: String,
) {
    fun generateWrongCred(i: Int) = wrongCred + generateWrongCredSuffix(i)
}

private val paramsByMethod =
    mapOf(
        Pin to
            Params(
                wrongCred = WRONG_PIN,
                generateWrongCredSuffix = { listOf(it) },
                enterCredString = "Enter PIN",
                wrongCredString = "Wrong PIN. Try again.",
                fpOrCredString = "Unlock with PIN or fingerprint",
                duplicateCredString = "Already tried that PIN. Try another.",
                lockedOutString = "Too many attempts with incorrect PIN",
                deviceUpdatedString = "Device updated. Enter PIN to continue.",
                tooManyAttemptsString = "PIN is required after too many attempts",
                afterRestartString = "PIN is required after device restarts",
                afterSignOutString = "PIN is required to sign in",
                afterTimeoutString = "Added security required. PIN not used for a while.",
                afterLockoutString = "PIN is required after lockdown",
                unattendedUpdateString = "PIN required for additional security",
                tryAgainOrCredString = "Try again or enter PIN",
            ),
        Pattern to
            Params(
                wrongCred = WRONG_PATTERN,
                generateWrongCredSuffix = {
                    listOf(AuthenticationPatternCoordinate((it % 9) / 3, it % 3))
                },
                enterCredString = "Draw pattern",
                wrongCredString = "Wrong pattern. Try again.",
                fpOrCredString = "Unlock with pattern or fingerprint",
                duplicateCredString = "Already tried that pattern. Try another.",
                lockedOutString = "Too many attempts with incorrect pattern",
                deviceUpdatedString = "Device updated. Draw pattern to continue.",
                tooManyAttemptsString = "Pattern is required after too many attempts",
                afterRestartString = "Pattern is required after device restarts",
                afterSignOutString = "Pattern is required to sign in",
                afterTimeoutString = "Added security required. Pattern not used for a while.",
                afterLockoutString = "Pattern is required after lockdown",
                unattendedUpdateString = "Pattern required for additional security",
                tryAgainOrCredString = "Try again or draw pattern",
            ),
        Password to
            Params(
                wrongCred = WRONG_PASSWORD,
                generateWrongCredSuffix = { listOf(it.toChar()) },
                enterCredString = "Enter password",
                wrongCredString = "Wrong password. Try again.",
                fpOrCredString = "Unlock with password or fingerprint",
                duplicateCredString = "Already tried that password. Try another.",
                lockedOutString = "Too many attempts with incorrect password",
                deviceUpdatedString = "Device updated. Enter password to continue.",
                tooManyAttemptsString = "Password is required after too many attempts",
                afterRestartString = "Password is required after device restarts",
                afterSignOutString = "Password is required to sign in",
                afterTimeoutString = "Added security required. Password not used for a while.",
                afterLockoutString = "Password is required after lockdown",
                unattendedUpdateString = "Password required for additional security",
                tryAgainOrCredString = "Try again or enter password",
            ),
    )

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@EnableSceneContainer
class BouncerMessageViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val authenticationInteractor by lazy { kosmos.authenticationInteractor }
    private val bouncerInteractor by lazy { kosmos.bouncerInteractor }
    private lateinit var underTest: BouncerMessageViewModel
    private lateinit var activationJob: Job
    private val defaultHelpMsg: String = "some helpful message"
    private val ignoreHelpMessageId = 1

    @field:Parameter(0) lateinit var authMethod: AuthenticationMethodModel

    private val params
        get() = paramsByMethod[authMethod]!!

    private val wrongCred
        get() = params.wrongCred

    private fun generateWrongCred(i: Int) = params.generateWrongCred(i)

    private val enterCredString
        get() = params.enterCredString

    private val wrongCredString
        get() = params.wrongCredString

    private val fpOrCredString
        get() = params.fpOrCredString

    private val duplicateCredString
        get() = params.duplicateCredString

    private val lockedOutString
        get() = params.lockedOutString

    private val deviceUpdatedString
        get() = params.deviceUpdatedString

    private val tooManyAttemptsString
        get() = params.tooManyAttemptsString

    private val afterRestartString
        get() = params.afterRestartString

    private val afterSignOutString
        get() = params.afterSignOutString

    private val afterTimeoutString
        get() = params.afterTimeoutString

    private val afterLockoutString
        get() = params.afterLockoutString

    private val unattendedUpdateString
        get() = params.unattendedUpdateString

    private val tryAgainOrCredString
        get() = params.tryAgainOrCredString

    @Before
    fun setUp() {
        // Set auth method to test the initial message value
        kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)

        // Set default a11y timeout to ensure that there is a timeout.
        kosmos.fakeAccessibilityRepository.setRecommendedTimeout(DEFAULT_MESSAGE_DURATION)

        kosmos.fakeUserRepository.setUserInfos(listOf(PRIMARY_USER))
        overrideResource(
            R.array.config_face_acquire_device_entry_ignorelist,
            intArrayOf(ignoreHelpMessageId),
        )
        underTest = kosmos.bouncerMessageViewModel
        activationJob = underTest.activateIn(testScope)
        overrideResource(R.string.kg_trust_agent_disabled, "Trust agent is unavailable")
        kosmos.fakeSystemPropertiesHelper.set(
            DeviceUnlockedInteractor.SYS_BOOT_REASON_PROP,
            "not mainline reboot",
        )
        kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
        testScope.runCurrent()
    }

    @Test
    fun message_initialDefaultMessage_isValid() =
        testScope.runTest {
            val message by collectLastValue(underTest.message)
            assertThat(message!!.text).isEqualTo(enterCredString)
        }

    @Test
    fun message_defaultMessage() =
        testScope.runTest {
            val message by collectLastValue(underTest.message)

            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)
            runCurrent()

            assertThat(message!!.text).isEqualTo(fpOrCredString)
        }

    @Test
    fun message() =
        testScope.runTest {
            val message by collectLastValue(underTest.message)
            assertThat(message?.isUpdateAnimated).isTrue()

            repeat(FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT) {
                bouncerInteractor.authenticate(wrongCred)
            }

            val lockoutEndTime = authenticationInteractor.lockoutEndTime ?: 0.seconds
            advanceTimeBy(lockoutEndTime - testScope.currentTime.milliseconds)
            assertThat(message?.isUpdateAnimated).isTrue()
        }

    @Test
    fun lockoutMessage() =
        testScope.runTest {
            val message by collectLastValue(underTest.message)
            val lockoutSeconds = FakeAuthenticationRepository.LOCKOUT_DURATION_SECONDS
            assertThat(kosmos.fakeAuthenticationRepository.lockoutEndTime).isNull()
            runCurrent()

            repeat(FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT) { times ->
                bouncerInteractor.authenticate(generateWrongCred(times))
                runCurrent()
                if (
                    times == FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT - 1
                ) {
                    assertTryAgainMessage(message?.text, lockoutSeconds)
                    assertLockedOutMessage(
                        message?.secondaryText,
                        android.security.Flags.lockscreenTimeoutShortlink(),
                    )
                    assertThat(message?.isUpdateAnimated).isFalse()
                } else {
                    assertThat(message?.text).isEqualTo(wrongCredString)
                    assertThat(message?.isUpdateAnimated).isTrue()
                }
            }

            repeat(FakeAuthenticationRepository.LOCKOUT_DURATION_SECONDS) { time ->
                advanceTimeBy(1.seconds)
                val remainingSeconds = lockoutSeconds - time - 1
                if (remainingSeconds > 0) {
                    assertTryAgainMessage(message?.text, remainingSeconds)
                }
            }
            assertThat(message?.text).isEqualTo(enterCredString)
            assertThat(message?.isUpdateAnimated).isTrue()
        }

    @Test
    fun duplicateCredential_showsDuplicateCredentialMessage() =
        testScope.runTest {
            val message by collectLastValue(underTest.message)
            runCurrent()
            bouncerInteractor.authenticate(wrongCred)
            runCurrent()
            assertThat(message?.text).isEqualTo(wrongCredString)

            bouncerInteractor.authenticate(wrongCred)
            runCurrent()

            assertThat(message?.text).isEqualTo(enterCredString)
            assertThat(message?.secondaryText).isEqualTo(duplicateCredString)
        }

    @Test
    fun defaultMessage_mapsToDeviceEntryRestrictionReason_whenTrustAgentIsEnabled() =
        testScope.runTest {
            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(false)
            kosmos.fakeTrustRepository.setTrustUsuallyManaged(true)
            kosmos.fakeTrustRepository.setCurrentUserTrustManaged(false)
            runCurrent()

            val defaultMessage = Pair(enterCredString, null)

            verifyMessagesForAuthFlags(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_NOT_REQUIRED to defaultMessage,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT to
                    Pair(enterCredString, afterRestartString),
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_TIMEOUT to
                    Pair(enterCredString, afterTimeoutString),
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW to
                    Pair(enterCredString, "For added security, device was locked by work policy"),
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_USER_REQUEST to
                    Pair(enterCredString, "Trust agent is unavailable"),
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_TRUSTAGENT_EXPIRED to
                    Pair(enterCredString, "Trust agent is unavailable"),
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_USER_LOCKDOWN to
                    Pair(enterCredString, afterLockoutString),
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_FOR_UNATTENDED_UPDATE to
                    Pair(enterCredString, unattendedUpdateString),
                LockPatternUtils.StrongAuthTracker
                    .STRONG_AUTH_REQUIRED_AFTER_NON_STRONG_BIOMETRICS_TIMEOUT to
                    Pair(
                        enterCredString,
                        "Added security required. Device wasn’t unlocked for a while.",
                    ),
            )
        }

    @Test
    fun defaultMessage_mapsToDeviceEntryRestrictionReason_whenFingerprintIsAvailable() =
        testScope.runTest {
            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)
            kosmos.fakeTrustRepository.setCurrentUserTrustManaged(false)
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(false)
            runCurrent()

            verifyMessagesForAuthFlags(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_NOT_REQUIRED to
                    Pair(fpOrCredString, null),
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_USER_REQUEST to
                    Pair(fpOrCredString, null),
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_TRUSTAGENT_EXPIRED to
                    Pair(fpOrCredString, null),
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT to
                    Pair(enterCredString, afterRestartString),
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_TIMEOUT to
                    Pair(enterCredString, afterTimeoutString),
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW to
                    Pair(enterCredString, "For added security, device was locked by work policy"),
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_USER_LOCKDOWN to
                    Pair(enterCredString, afterLockoutString),
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_FOR_UNATTENDED_UPDATE to
                    Pair(enterCredString, unattendedUpdateString),
                LockPatternUtils.StrongAuthTracker
                    .STRONG_AUTH_REQUIRED_AFTER_NON_STRONG_BIOMETRICS_TIMEOUT to
                    Pair(
                        fpOrCredString,
                        "Added security required. Device wasn’t unlocked for a while.",
                    ),
            )
        }

    @EnableFlags(FLAG_STRONG_AUTH_REQUIRED_AFTER_SIGN_OUT_MESSAGE_FIX)
    @Test
    fun defaultMessage_mapsToDeviceEntryRestrictionReason_whenUserNotUnlockedSinceSignOut() =
        testScope.runTest {
            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            kosmos.userSwitcherRepository.isUserSwitchingMustGoThroughLoginScreen = true
            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)
            kosmos.fakeTrustRepository.setCurrentUserTrustManaged(false)
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(false)
            runCurrent()

            verifyMessagesForAuthFlags(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT to
                    Pair(enterCredString, afterSignOutString)
            )
        }

    private suspend fun TestScope.setupSecureLockDeviceState(
        faceAuthCurrentlyAllowed: Boolean = false,
        faceAuthEnrolledAndEnabled: Boolean = false,
        fingerprintAuthCurrentlyAllowed: Boolean = false,
        fingerprintAuthEnrolledAndEnabled: Boolean = false,
        secureLockDeviceEnabled: Boolean = false,
        secureLockDeviceBiometricAuthActive: Boolean = false,
    ) {
        kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
        kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
        if (fingerprintAuthEnrolledAndEnabled) {
            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
        }
        kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(
            fingerprintAuthEnrolledAndEnabled
        )
        kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(
            fingerprintAuthCurrentlyAllowed
        )

        if (faceAuthEnrolledAndEnabled) {
            kosmos.fakeFacePropertyRepository.setSensorInfo(
                FaceSensorInfo(id = 0, strength = SensorStrength.STRONG)
            )
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(false)
        }
        kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(
            faceAuthEnrolledAndEnabled
        )
        kosmos.fakeBiometricSettingsRepository.setIsFaceAuthCurrentlyAllowed(
            faceAuthCurrentlyAllowed
        )
        runCurrent()

        if (secureLockDeviceEnabled) {
            kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceEnabled()
            if (secureLockDeviceBiometricAuthActive) {
                kosmos.fakeSecureLockDeviceRepository.onSuccessfulPrimaryAuth()
                kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                    AuthenticationMethodModel.Biometric
                )
                kosmos.fakeBiometricSettingsRepository.setAuthenticationFlags(
                    AuthenticationFlags(
                        PRIMARY_USER_ID,
                        STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                    )
                )
            } else {
                kosmos.fakeBiometricSettingsRepository.setAuthenticationFlags(
                    AuthenticationFlags(
                        PRIMARY_USER_ID,
                        (PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE or
                            STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE),
                    )
                )
            }
        } else {
            kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceDisabled()
        }
        runCurrent()
    }

    private data class SecureLockDeviceTestCase(
        val testName: String,
        val faceAuthCurrentlyAllowed: Boolean = false,
        val faceAuthEnrolledAndEnabled: Boolean = false,
        val fingerprintAuthCurrentlyAllowed: Boolean = false,
        val fingerprintAuthEnrolledAndEnabled: Boolean = false,
        val secureLockDeviceEnabled: Boolean,
        val secureLockDeviceBiometricAuthActive: Boolean,
        val expectedTitle: String,
        val expectedSubtitle: String,
        val fingerprintAuthenticationStatus: FingerprintAuthenticationStatus? = null,
        val faceAuthenticationStatus: FaceAuthenticationStatus? = null,
    )

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun secureLockDeviceMessages() =
        testScope.runTest {
            val testCases =
                listOf(
                    SecureLockDeviceTestCase(
                        testName = "first-factor primary auth",
                        faceAuthEnrolledAndEnabled = true,
                        fingerprintAuthEnrolledAndEnabled = true,
                        secureLockDeviceEnabled = true,
                        secureLockDeviceBiometricAuthActive = false,
                        expectedTitle =
                            resString(R.string.kg_prompt_title_after_secure_lock_device),
                        expectedSubtitle = enterCredString,
                    ),
                    SecureLockDeviceTestCase(
                        testName = "second-factor strong biometric auth, fingerprint-only",
                        fingerprintAuthCurrentlyAllowed = true,
                        fingerprintAuthEnrolledAndEnabled = true,
                        secureLockDeviceEnabled = true,
                        secureLockDeviceBiometricAuthActive = true,
                        expectedTitle =
                            resString(R.string.kg_prompt_title_after_secure_lock_device),
                        expectedSubtitle =
                            resString(
                                R.string
                                    .kg_prompt_subtitle_for_secure_lock_device_biometric_auth_fingerprint
                            ),
                    ),
                    SecureLockDeviceTestCase(
                        testName = "second-factor strong biometric auth, face-only",
                        faceAuthCurrentlyAllowed = true,
                        faceAuthEnrolledAndEnabled = true,
                        secureLockDeviceEnabled = true,
                        secureLockDeviceBiometricAuthActive = true,
                        expectedTitle =
                            resString(R.string.kg_prompt_title_after_secure_lock_device),
                        expectedSubtitle =
                            resString(
                                R.string
                                    .kg_prompt_subtitle_for_secure_lock_device_biometric_auth_face
                            ),
                    ),
                    SecureLockDeviceTestCase(
                        testName = "second-factor strong biometric auth, co-ex",
                        faceAuthCurrentlyAllowed = true,
                        faceAuthEnrolledAndEnabled = true,
                        fingerprintAuthCurrentlyAllowed = true,
                        fingerprintAuthEnrolledAndEnabled = true,
                        secureLockDeviceEnabled = true,
                        secureLockDeviceBiometricAuthActive = true,
                        expectedTitle =
                            resString(R.string.kg_prompt_title_after_secure_lock_device),
                        expectedSubtitle =
                            resString(
                                R.string
                                    .kg_prompt_subtitle_for_secure_lock_device_biometric_auth_coex
                            ),
                    ),
                    SecureLockDeviceTestCase(
                        testName =
                            "second-factor strong biometric auth, fingerprint-only, show help msg",
                        faceAuthCurrentlyAllowed = false,
                        faceAuthEnrolledAndEnabled = false,
                        fingerprintAuthCurrentlyAllowed = true,
                        fingerprintAuthEnrolledAndEnabled = true,
                        secureLockDeviceEnabled = true,
                        secureLockDeviceBiometricAuthActive = true,
                        expectedTitle =
                            resString(R.string.kg_prompt_title_after_secure_lock_device),
                        expectedSubtitle = defaultHelpMsg,
                        fingerprintAuthenticationStatus =
                            HelpFingerprintAuthenticationStatus(1, defaultHelpMsg),
                    ),
                    SecureLockDeviceTestCase(
                        testName =
                            "second-factor strong biometric auth, fingerprint-only, show fail msg",
                        faceAuthCurrentlyAllowed = false,
                        faceAuthEnrolledAndEnabled = false,
                        fingerprintAuthCurrentlyAllowed = true,
                        fingerprintAuthEnrolledAndEnabled = true,
                        secureLockDeviceEnabled = true,
                        secureLockDeviceBiometricAuthActive = true,
                        expectedTitle =
                            resString(R.string.kg_prompt_title_after_secure_lock_device),
                        expectedSubtitle = resString(R.string.kg_fp_not_recognized),
                        fingerprintAuthenticationStatus = FailFingerprintAuthenticationStatus,
                    ),
                    SecureLockDeviceTestCase(
                        testName =
                            "second-factor strong biometric auth, fingerprint-only, show lockout msg",
                        faceAuthCurrentlyAllowed = false,
                        faceAuthEnrolledAndEnabled = false,
                        fingerprintAuthCurrentlyAllowed = true,
                        fingerprintAuthEnrolledAndEnabled = true,
                        secureLockDeviceEnabled = true,
                        secureLockDeviceBiometricAuthActive = false,
                        expectedTitle =
                            resString(R.string.kg_prompt_title_after_secure_lock_device),
                        expectedSubtitle = tooManyAttemptsString,
                        fingerprintAuthenticationStatus =
                            ErrorFingerprintAuthenticationStatus(
                                FINGERPRINT_ERROR_LOCKOUT,
                                tooManyAttemptsString,
                            ),
                    ),
                    SecureLockDeviceTestCase(
                        testName = "second-factor strong biometric auth, face-only, show help msg",
                        faceAuthCurrentlyAllowed = true,
                        faceAuthEnrolledAndEnabled = true,
                        fingerprintAuthCurrentlyAllowed = false,
                        fingerprintAuthEnrolledAndEnabled = false,
                        secureLockDeviceEnabled = true,
                        secureLockDeviceBiometricAuthActive = true,
                        expectedTitle =
                            resString(R.string.kg_prompt_title_after_secure_lock_device),
                        expectedSubtitle = defaultHelpMsg,
                        faceAuthenticationStatus =
                            HelpFaceAuthenticationStatus(0, defaultHelpMsg, 0),
                    ),
                    SecureLockDeviceTestCase(
                        testName = "second-factor strong biometric auth, face-only, show fail msg",
                        faceAuthCurrentlyAllowed = true,
                        faceAuthEnrolledAndEnabled = true,
                        fingerprintAuthCurrentlyAllowed = false,
                        fingerprintAuthEnrolledAndEnabled = false,
                        secureLockDeviceEnabled = true,
                        secureLockDeviceBiometricAuthActive = true,
                        expectedTitle =
                            resString(R.string.kg_prompt_title_after_secure_lock_device),
                        expectedSubtitle = resString(R.string.keyguard_face_failed),
                        faceAuthenticationStatus = FailedFaceAuthenticationStatus(),
                    ),
                    SecureLockDeviceTestCase(
                        testName =
                            "second-factor strong biometric auth, face-only, show lockout msg",
                        faceAuthCurrentlyAllowed = true,
                        faceAuthEnrolledAndEnabled = true,
                        fingerprintAuthCurrentlyAllowed = false,
                        fingerprintAuthEnrolledAndEnabled = false,
                        secureLockDeviceEnabled = true,
                        secureLockDeviceBiometricAuthActive = true,
                        expectedTitle =
                            resString(R.string.kg_prompt_title_after_secure_lock_device),
                        expectedSubtitle = tooManyAttemptsString,
                        faceAuthenticationStatus =
                            ErrorFaceAuthenticationStatus(FACE_ERROR_LOCKOUT, tooManyAttemptsString),
                    ),
                )

            val bouncerMessage by collectLastValue(underTest.message)

            testCases.forEach { case ->
                setupSecureLockDeviceState(
                    faceAuthCurrentlyAllowed = case.faceAuthCurrentlyAllowed,
                    faceAuthEnrolledAndEnabled = case.faceAuthEnrolledAndEnabled,
                    fingerprintAuthCurrentlyAllowed = case.fingerprintAuthCurrentlyAllowed,
                    fingerprintAuthEnrolledAndEnabled = case.fingerprintAuthEnrolledAndEnabled,
                    secureLockDeviceEnabled = case.secureLockDeviceEnabled,
                    secureLockDeviceBiometricAuthActive = case.secureLockDeviceBiometricAuthActive,
                )
                runCurrent()

                case.fingerprintAuthenticationStatus?.let {
                    kosmos.deviceEntryFingerprintAuthRepository.setAuthenticationStatus(it)
                    runCurrent()
                }

                case.faceAuthenticationStatus?.let {
                    kosmos.fakeDeviceEntryFaceAuthRepository.setAuthenticationStatus(it)
                    if (it is HelpFaceAuthenticationStatus) {
                        runCurrent()
                        kosmos.fakeDeviceEntryFaceAuthRepository.setAuthenticationStatus(
                            HelpFaceAuthenticationStatus(
                                it.msgId,
                                it.msg,
                                FaceHelpMessageDebouncer.DEFAULT_WINDOW_MS,
                            )
                        )
                    }
                }

                if (
                    (case.fingerprintAuthenticationStatus is ErrorFingerprintAuthenticationStatus &&
                        case.fingerprintAuthenticationStatus.msgId == FINGERPRINT_ERROR_LOCKOUT) ||
                        (case.faceAuthenticationStatus is ErrorFaceAuthenticationStatus &&
                            case.faceAuthenticationStatus.msgId == FACE_ERROR_LOCKOUT)
                ) {
                    kosmos.fakeSecureLockDeviceRepository.onBiometricLockout()
                    kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
                    runCurrent()
                }

                assertWithMessage("Title for case: ${case.testName}")
                    .that(bouncerMessage?.text)
                    .isEqualTo(case.expectedTitle)
                assertWithMessage("Subtitle for case: ${case.testName}")
                    .that(bouncerMessage?.secondaryText)
                    .isEqualTo(case.expectedSubtitle)
            }
        }

    private fun resString(msgResId: Int): String = context.resources.getString(msgResId)

    @Test
    fun onFingerprintLockout_messageUpdated() =
        testScope.runTest {
            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)

            val lockedOutMessage by collectLastValue(underTest.message)

            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(true)
            runCurrent()

            assertThat(lockedOutMessage?.text).isEqualTo(enterCredString)
            assertThat(lockedOutMessage?.secondaryText).isEqualTo(tooManyAttemptsString)
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            runCurrent()

            assertThat(lockedOutMessage?.text).isEqualTo(fpOrCredString)
            assertThat(lockedOutMessage?.secondaryText.isNullOrBlank()).isTrue()
        }

    @Test
    fun onUdfpsFingerprint_DoesNotShowFingerprintMessage() =
        testScope.runTest {
            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            kosmos.fakeFingerprintPropertyRepository.supportsUdfps()
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            val message by collectLastValue(underTest.message)

            runCurrent()

            assertThat(message?.text).isEqualTo(enterCredString)
        }

    @Test
    fun onRestartForMainlineUpdate_shouldProvideRelevantMessage() =
        testScope.runTest {
            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            kosmos.fakeSystemPropertiesHelper.set("sys.boot.reason.last", "reboot,mainline_update")
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            runCurrent()

            verifyMessagesForAuthFlags(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT to
                    Pair(enterCredString, deviceUpdatedString)
            )
        }

    @Test
    fun onFaceLockout_whenItIsClass3_shouldProvideRelevantMessage() =
        testScope.runTest {
            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
            val lockoutMessage by collectLastValue(underTest.message)
            kosmos.fakeFacePropertyRepository.setSensorInfo(
                FaceSensorInfo(1, SensorStrength.STRONG)
            )
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(true)
            runCurrent()

            assertThat(lockoutMessage?.text).isEqualTo(enterCredString)
            assertThat(lockoutMessage?.secondaryText).isEqualTo(tooManyAttemptsString)

            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(false)
            runCurrent()

            assertThat(lockoutMessage?.text).isEqualTo(enterCredString)
            assertThat(lockoutMessage?.secondaryText.isNullOrBlank()).isTrue()
        }

    @Test
    fun onFaceLockout_whenItIsNotStrong_shouldProvideRelevantMessage() =
        testScope.runTest {
            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
            val lockoutMessage by collectLastValue(underTest.message)
            kosmos.fakeFacePropertyRepository.setSensorInfo(FaceSensorInfo(1, SensorStrength.WEAK))
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(true)
            runCurrent()

            assertThat(lockoutMessage?.text).isEqualTo(enterCredString)
            assertThat(lockoutMessage?.secondaryText)
                .isEqualTo("Can’t unlock with face. Too many attempts.")

            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(false)
            runCurrent()

            assertThat(lockoutMessage?.text).isEqualTo(enterCredString)
            assertThat(lockoutMessage?.secondaryText.isNullOrBlank()).isTrue()
        }

    @Test
    fun setFingerprintMessage_propagateValue() =
        testScope.runTest {
            val bouncerMessage by collectLastValue(underTest.message)

            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)
            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()
            runCurrent()

            kosmos.deviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                HelpFingerprintAuthenticationStatus(1, "some helpful message")
            )
            runCurrent()
            assertThat(bouncerMessage?.text).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryText).isEqualTo("some helpful message")

            kosmos.deviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                FailFingerprintAuthenticationStatus
            )
            runCurrent()
            assertThat(bouncerMessage?.text).isEqualTo("Fingerprint not recognized")
            assertThat(bouncerMessage?.secondaryText).isEqualTo(tryAgainOrCredString)

            kosmos.deviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                ErrorFingerprintAuthenticationStatus(
                    FingerprintManager.FINGERPRINT_ERROR_LOCKOUT,
                    "locked out",
                )
            )
            runCurrent()
            assertThat(bouncerMessage?.text).isEqualTo(enterCredString)
            assertThat(bouncerMessage?.secondaryText).isEqualTo(tooManyAttemptsString)
        }

    @Test
    fun setFaceMessage_propagateValue() =
        testScope.runTest {
            val bouncerMessage by collectLastValue(underTest.message)

            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthCurrentlyAllowed(true)
            runCurrent()

            kosmos.fakeDeviceEntryFaceAuthRepository.setAuthenticationStatus(
                HelpFaceAuthenticationStatus(0, "some helpful message", 0)
            )
            runCurrent()
            kosmos.fakeDeviceEntryFaceAuthRepository.setAuthenticationStatus(
                HelpFaceAuthenticationStatus(
                    0,
                    "some helpful message",
                    FaceHelpMessageDebouncer.DEFAULT_WINDOW_MS,
                )
            )
            runCurrent()
            assertThat(bouncerMessage?.text).isEqualTo(enterCredString)
            assertThat(bouncerMessage?.secondaryText).isEqualTo("some helpful message")

            kosmos.fakeDeviceEntryFaceAuthRepository.setAuthenticationStatus(
                ErrorFaceAuthenticationStatus(
                    BiometricFaceConstants.FACE_ERROR_TIMEOUT,
                    "Try again",
                )
            )
            runCurrent()
            assertThat(bouncerMessage?.text).isEqualTo(enterCredString)
            assertThat(bouncerMessage?.secondaryText).isEqualTo("Try again")

            kosmos.fakeDeviceEntryFaceAuthRepository.setAuthenticationStatus(
                FailedFaceAuthenticationStatus()
            )
            runCurrent()
            assertThat(bouncerMessage?.text).isEqualTo("Face not recognized")
            assertThat(bouncerMessage?.secondaryText).isEqualTo(tryAgainOrCredString)

            kosmos.fakeDeviceEntryFaceAuthRepository.setAuthenticationStatus(
                ErrorFaceAuthenticationStatus(
                    BiometricFaceConstants.FACE_ERROR_LOCKOUT,
                    "locked out",
                )
            )
            runCurrent()
            assertThat(bouncerMessage?.text).isEqualTo(enterCredString)
            assertThat(bouncerMessage?.secondaryText)
                .isEqualTo("Can’t unlock with face. Too many attempts.")
        }

    @Test
    @DisableFlags(android.security.Flags.FLAG_LOCKSCREEN_LARGER_TIMEOUT_TIME_UNITS)
    fun startLockdownCountdown_onActivatedShowsSeconds() =
        testScope.runTest {
            val bouncerMessage by collectLastValue(underTest.message)
            val lockout = 200.seconds
            kosmos.fakeAuthenticationRepository.overrideLockout(lockout)
            bouncerInteractor.authenticate(wrongCred)
            runCurrent()

            assertThat(bouncerMessage?.text).isEqualTo("Try again in 200 seconds.")
            advanceTimeBy(100.seconds)
            assertThat(bouncerMessage?.text).isEqualTo("Try again in 100 seconds.")
            advanceTimeBy(101.seconds)
            assertThat(bouncerMessage?.text).isEqualTo(enterCredString)
        }

    @Test
    @EnableFlags(android.security.Flags.FLAG_LOCKSCREEN_LARGER_TIMEOUT_TIME_UNITS)
    fun startLockdownCountdown_onActivatedShowsLargerUnits() =
        testScope.runTest {
            val bouncerMessage by collectLastValue(underTest.message)
            val lockout = 200.seconds
            kosmos.fakeAuthenticationRepository.overrideLockout(lockout)
            bouncerInteractor.authenticate(wrongCred)
            runCurrent()

            assertThat(bouncerMessage?.text).isEqualTo("Try again in 4 minutes")
            advanceTimeBy(100.seconds)
            assertThat(bouncerMessage?.text).isEqualTo("Try again in 2 minutes")
            advanceTimeBy(101.seconds)
            assertThat(bouncerMessage?.text).isEqualTo(enterCredString)
        }

    private val Int.years: Duration
        get() = 365.days * this

    @Test
    @EnableFlags(android.security.Flags.FLAG_LOCKSCREEN_LARGER_TIMEOUT_TIME_UNITS)
    fun startLockdownCountdown_onActivatedShowsAllLargerUnits() =
        testScope.runTest {
            var attemptNumber = 0
            activationJob.cancelAndJoin()
            advanceUntilIdle()

            /** Set up the viewModel fresh for each assert */
            suspend fun assertTextFor(lockout: Duration, text: String) {
                coroutineScope {
                    val job = launch { underTest.activate() }
                    val bouncerMessage by collectLastValue(underTest.message)
                    runCurrent()

                    kosmos.fakeAuthenticationRepository.overrideLockout(lockout)
                    bouncerInteractor.authenticate(generateWrongCred(attemptNumber++))
                    runCurrent()

                    assertThat(bouncerMessage?.text).isEqualTo(text)

                    kosmos.fakeAuthenticationRepository.reportAuthenticationAttempt(
                        AuthenticationResult.SUCCEEDED
                    )

                    job.cancelAndJoin()
                }
            }

            // Round up to the year above 364 days
            assertTextFor(9.years, "Try again in 9 years")
            assertTextFor(9.years - 1.days, "Try again in 9 years")
            assertTextFor(8.years + 1.days, "Try again in 9 years")
            assertTextFor(1.years + 1.days, "Try again in 2 years")
            assertTextFor(1.years, "Try again in 1 year")
            assertTextFor(364.days + 1.hours, "Try again in 1 year")

            // Round up to the day above 36 hours
            assertTextFor(364.days, "Try again in 364 days")
            assertTextFor(364.days - 1.hours, "Try again in 364 days")
            assertTextFor(363.days + 1.hours, "Try again in 364 days")
            assertTextFor(363.days, "Try again in 363 days")
            assertTextFor(2.days, "Try again in 2 days")
            assertTextFor(47.hours, "Try again in 2 days")
            assertTextFor(37.hours, "Try again in 2 days")

            // Round up to the hour above 90 minutes
            assertTextFor(36.hours, "Try again in 36 hours")
            assertTextFor(36.hours - 1.minutes, "Try again in 36 hours")
            assertTextFor(35.hours + 1.minutes, "Try again in 36 hours")
            assertTextFor(35.hours, "Try again in 35 hours")
            assertTextFor(2.hours, "Try again in 2 hours")
            assertTextFor(90.minutes + 1.seconds, "Try again in 2 hours")

            // Round up to the minute above 59 seconds
            assertTextFor(90.minutes, "Try again in 90 minutes")
            assertTextFor(90.minutes - 1.seconds, "Try again in 90 minutes")
            assertTextFor(89.minutes + 1.seconds, "Try again in 90 minutes")
            assertTextFor(89.minutes, "Try again in 89 minutes")
            assertTextFor(1.minutes + 1.seconds, "Try again in 2 minutes")
            assertTextFor(1.minutes, "Try again in 1 minute")

            // Show seconds simply as seconds
            assertTextFor(59.seconds, "Try again in 59 seconds")
            assertTextFor(1.seconds, "Try again in 1 second")
        }

    private fun TestScope.verifyMessagesForAuthFlags(
        vararg authFlagToMessagePair: Pair<Int, Pair<String, String?>>
    ) {
        val actualMessage by collectLastValue(underTest.message)

        authFlagToMessagePair.forEach { (flag, expectedMessagePair) ->
            kosmos.fakeBiometricSettingsRepository.setAuthenticationFlags(
                AuthenticationFlags(userId = PRIMARY_USER_ID, flag = flag)
            )
            runCurrent()

            assertThat(actualMessage?.text).isEqualTo(expectedMessagePair.first)
            if (expectedMessagePair.second == null) {
                assertThat(actualMessage?.secondaryText.isNullOrBlank()).isTrue()
            } else {
                assertThat(actualMessage?.secondaryText).isEqualTo(expectedMessagePair.second)
            }
        }
    }

    private fun assertTryAgainMessage(message: String?, time: Int) {
        assertThat(message).contains("Try again in $time second")
    }

    private fun assertLockedOutMessage(message: String?, includesShortlink: Boolean) {
        assertThat(message).startsWith(lockedOutString)
        if (includesShortlink) {
            assertThat(message)
                .contains(
                    resString(com.android.internal.R.string.config_lockscreenLockoutShortlink)
                )
        }
    }

    companion object {
        private const val PRIMARY_USER_ID = 0
        private val PRIMARY_USER =
            UserInfo(
                /* id= */ PRIMARY_USER_ID,
                /* name= */ "primary user",
                /* flags= */ UserInfo.FLAG_PRIMARY,
            )

        @JvmStatic
        @Parameters(name = "authenticationMethodModel={0}")
        fun data() = listOf(arrayOf(Pin), arrayOf(Pattern), arrayOf(Password))
    }
}
