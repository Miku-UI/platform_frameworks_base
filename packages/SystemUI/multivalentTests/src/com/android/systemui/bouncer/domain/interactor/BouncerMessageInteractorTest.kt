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
 */

package com.android.systemui.bouncer.domain.interactor

import android.content.pm.UserInfo
import android.hardware.biometrics.BiometricFaceConstants
import android.hardware.biometrics.BiometricSourceType
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import android.testing.TestableLooper
import androidx.test.filters.SmallTest
import com.android.internal.widget.LockPatternUtils
import com.android.internal.widget.LockPatternUtils.StrongAuthTracker.PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE
import com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE
import com.android.keyguard.KeyguardSecurityModel
import com.android.keyguard.KeyguardSecurityModel.SecurityMode
import com.android.keyguard.KeyguardSecurityModel.SecurityMode.PIN
import com.android.keyguard.KeyguardSecurityModel.SecurityMode.Password
import com.android.keyguard.KeyguardSecurityModel.SecurityMode.Pattern
import com.android.keyguard.KeyguardSecurityModel.SecurityMode.SecureLockDeviceBiometricAuth
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.keyguard.keyguardSecurityModel
import com.android.keyguard.keyguardUpdateMonitor
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.fakeFacePropertyRepository
import com.android.systemui.biometrics.data.repository.fakeFingerprintPropertyRepository
import com.android.systemui.biometrics.shared.model.FaceSensorInfo
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.bouncer.data.repository.fakeKeyguardBouncerRepository
import com.android.systemui.bouncer.shared.model.BouncerMessageModel
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.fakeSystemPropertiesHelper
import com.android.systemui.keyguard.data.repository.fakeBiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeTrustRepository
import com.android.systemui.keyguard.shared.model.AuthenticationFlags
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.res.R.string.kg_primary_auth_locked_out_password
import com.android.systemui.res.R.string.kg_primary_auth_locked_out_password_shortlink
import com.android.systemui.res.R.string.kg_primary_auth_locked_out_pattern
import com.android.systemui.res.R.string.kg_primary_auth_locked_out_pattern_shortlink
import com.android.systemui.res.R.string.kg_primary_auth_locked_out_pin
import com.android.systemui.res.R.string.kg_primary_auth_locked_out_pin_shortlink
import com.android.systemui.res.R.string.kg_too_many_failed_attempts_countdown
import com.android.systemui.res.R.string.kg_too_many_failed_attempts_countdown_days
import com.android.systemui.res.R.string.kg_too_many_failed_attempts_countdown_hours
import com.android.systemui.res.R.string.kg_too_many_failed_attempts_countdown_minutes
import com.android.systemui.res.R.string.kg_too_many_failed_attempts_countdown_seconds
import com.android.systemui.res.R.string.kg_too_many_failed_attempts_countdown_years
import com.android.systemui.res.R.string.kg_trust_agent_disabled
import com.android.systemui.securelockdevice.data.repository.fakeSecureLockDeviceRepository
import com.android.systemui.securelockdevice.domain.interactor.secureLockDeviceInteractor
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.mockito.KotlinArgumentCaptor
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.AdditionalMatchers.or
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Captor
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import platform.test.runner.parameterized.Parameter
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

private const val SYS_BOOT_REASON_PROP = "sys.boot.reason.last"
private const val REBOOT_MAINLINE_UPDATE = "reboot,mainline_update"

data class Params(
    val enterCredString: String,
    val wrongCredString: String,
    val fpOrCredString: String,
    val duplicateCredString: String,
    val lockedOutRes: Int,
    val lockedOutShortlinkRes: Int,
    val deviceUpdatedString: String,
    val tooManyAttemptsString: String,
    val afterRestartString: String,
    val afterTimeoutString: String,
    val afterLockoutString: String,
    val unattendedUpdateString: String,
)

private val paramsByMode =
    mapOf(
        PIN to
            Params(
                enterCredString = "Enter PIN",
                wrongCredString = "Wrong PIN. Try again.",
                fpOrCredString = "Unlock with PIN or fingerprint",
                duplicateCredString = "Already tried that PIN. Try another.",
                lockedOutRes = kg_primary_auth_locked_out_pin,
                lockedOutShortlinkRes = kg_primary_auth_locked_out_pin_shortlink,
                deviceUpdatedString = "Device updated. Enter PIN to continue.",
                tooManyAttemptsString = "PIN is required after too many attempts",
                afterRestartString = "PIN is required after device restarts",
                afterTimeoutString = "Added security required. PIN not used for a while.",
                afterLockoutString = "PIN is required after lockdown",
                unattendedUpdateString = "PIN required for additional security",
            ),
        Pattern to
            Params(
                enterCredString = "Draw pattern",
                wrongCredString = "Wrong pattern. Try again.",
                fpOrCredString = "Unlock with pattern or fingerprint",
                duplicateCredString = "Already tried that pattern. Try another.",
                lockedOutRes = kg_primary_auth_locked_out_pattern,
                lockedOutShortlinkRes = kg_primary_auth_locked_out_pattern_shortlink,
                deviceUpdatedString = "Device updated. Draw pattern to continue.",
                tooManyAttemptsString = "Pattern is required after too many attempts",
                afterRestartString = "Pattern is required after device restarts",
                afterTimeoutString = "Added security required. Pattern not used for a while.",
                afterLockoutString = "Pattern is required after lockdown",
                unattendedUpdateString = "Pattern required for additional security",
            ),
        Password to
            Params(
                enterCredString = "Enter password",
                wrongCredString = "Wrong password. Try again.",
                fpOrCredString = "Unlock with password or fingerprint",
                duplicateCredString = "Already tried that password. Try another.",
                lockedOutRes = kg_primary_auth_locked_out_password,
                lockedOutShortlinkRes = kg_primary_auth_locked_out_password_shortlink,
                deviceUpdatedString = "Device updated. Enter password to continue.",
                tooManyAttemptsString = "Password is required after too many attempts",
                afterRestartString = "Password is required after device restarts",
                afterTimeoutString = "Added security required. Password not used for a while.",
                afterLockoutString = "Password is required after lockdown",
                unattendedUpdateString = "Password required for additional security",
            ),
    )

@SmallTest
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@RunWith(ParameterizedAndroidJunit4::class)
class BouncerMessageInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val countDownTimerCallback = KotlinArgumentCaptor(CountDownTimerCallback::class.java)
    private val countDownTimerUtil = kosmos.countDownTimerUtil
    private val biometricSettingsRepository = kosmos.fakeBiometricSettingsRepository
    private val updateMonitor = kosmos.keyguardUpdateMonitor
    private val securityModel: KeyguardSecurityModel = kosmos.keyguardSecurityModel
    private val testScope = kosmos.testScope
    @Captor
    private lateinit var keyguardUpdateMonitorCaptor: ArgumentCaptor<KeyguardUpdateMonitorCallback>

    private lateinit var underTest: BouncerMessageInteractor

    @field:Parameter(0) lateinit var securityMode: SecurityMode

    private val params
        get() = paramsByMode[securityMode]!!

    private val enterCredString
        get() = params.enterCredString

    private val wrongCredString
        get() = params.wrongCredString

    private val fpOrCredString
        get() = params.fpOrCredString

    private val duplicateCredString
        get() = params.duplicateCredString

    private val lockedOutRes
        get() = params.lockedOutRes

    private val lockedOutShortlinkRes
        get() = params.lockedOutShortlinkRes

    private val deviceUpdatedString
        get() = params.deviceUpdatedString

    private val tooManyAttemptsString
        get() = params.tooManyAttemptsString

    private val afterRestartString
        get() = params.afterRestartString

    private val afterTimeoutString
        get() = params.afterTimeoutString

    private val afterLockoutString
        get() = params.afterLockoutString

    private val unattendedUpdateString
        get() = params.unattendedUpdateString

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        kosmos.fakeUserRepository.setUserInfos(listOf(PRIMARY_USER, SECONDARY_USER))
        allowTestableLooperAsMainThread()
        whenever(securityModel.getSecurityMode(or(eq(PRIMARY_USER_ID), eq(SECONDARY_USER_ID))))
            .thenReturn(securityMode)
        biometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)
        overrideResource(kg_trust_agent_disabled, "Trust agent is unavailable")
    }

    suspend fun TestScope.init(
        faceAuthCurrentlyAllowed: Boolean = false,
        faceAuthEnrolledAndEnabled: Boolean = false,
        hasStrongFace: Boolean = false,
        fingerprintAuthCurrentlyAllowed: Boolean = true,
        fingerprintAuthEnrolledAndEnabled: Boolean = true,
        secureLockDeviceEnabled: Boolean? = null,
        secureLockDeviceBiometricAuthActive: Boolean? = null,
    ) {
        kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
        underTest = kosmos.bouncerMessageInteractor

        kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
        kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(false)

        if (fingerprintAuthEnrolledAndEnabled) {
            kosmos.fakeFingerprintPropertyRepository.supportsSideFps()
        }
        biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(
            fingerprintAuthEnrolledAndEnabled
        )
        biometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(
            fingerprintAuthCurrentlyAllowed
        )

        if (faceAuthEnrolledAndEnabled) {
            kosmos.fakeFacePropertyRepository.setSensorInfo(
                FaceSensorInfo(
                    id = 0,
                    strength =
                        if (hasStrongFace) {
                            SensorStrength.STRONG
                        } else {
                            SensorStrength.WEAK
                        },
                )
            )
        }
        biometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(faceAuthEnrolledAndEnabled)
        biometricSettingsRepository.setIsFaceAuthCurrentlyAllowed(faceAuthCurrentlyAllowed)

        secureLockDeviceEnabled?.let { enabled ->
            if (enabled) {
                kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceEnabled()
                secureLockDeviceBiometricAuthActive?.let {
                    if (it) {
                        kosmos.fakeSecureLockDeviceRepository.onSuccessfulPrimaryAuth()
                        whenever(securityModel.getSecurityMode(eq(PRIMARY_USER_ID)))
                            .thenReturn(SecureLockDeviceBiometricAuth)
                        biometricSettingsRepository.setAuthenticationFlags(
                            AuthenticationFlags(
                                PRIMARY_USER_ID,
                                STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                            )
                        )
                    } else {
                        biometricSettingsRepository.setAuthenticationFlags(
                            AuthenticationFlags(
                                PRIMARY_USER_ID,
                                (PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE or
                                    STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE),
                            )
                        )
                    }
                }
            } else {
                kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceDisabled()
            }
        }
        kosmos.fakeKeyguardBouncerRepository.setPrimaryShow(true)
        runCurrent()

        if (secureLockDeviceEnabled == true) {
            val hasFingerprint by collectLastValue(kosmos.secureLockDeviceInteractor.hasFingerprint)
            val hasFace by collectLastValue(kosmos.secureLockDeviceInteractor.hasFace)

            runCurrent()
            if (fingerprintAuthEnrolledAndEnabled) {
                assertThat(hasFingerprint).isTrue()
            }

            if (hasStrongFace) {
                assertThat(hasFace).isTrue()
            }
        }
    }

    @Test
    fun initialMessage_cred() =
        testScope.runTest {
            init(fingerprintAuthCurrentlyAllowed = false)
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            kosmos.fakeKeyguardBouncerRepository.setLastShownSecurityMode(securityMode)
            assertThat(bouncerMessage).isNotNull()
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(enterCredString)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun initialMessage_primaryBouncerAuth_secureLockDeviceEnabled() =
        testScope.runTest {
            init(
                fingerprintAuthEnrolledAndEnabled = true,
                fingerprintAuthCurrentlyAllowed = false,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = false,
            )
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            runCurrent()

            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(enterCredString)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun initialMessage_strongFingerprintAuth_secureLockDeviceEnabled() =
        testScope.runTest {
            init(
                fingerprintAuthCurrentlyAllowed = true,
                fingerprintAuthEnrolledAndEnabled = true,
                faceAuthCurrentlyAllowed = false,
                faceAuthEnrolledAndEnabled = false,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = true,
            )
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            runCurrent()

            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            val expectedSubtitle =
                resString(
                    R.string.kg_prompt_subtitle_for_secure_lock_device_biometric_auth_fingerprint
                )
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun initialMessage_strongFaceAuth_secureLockDeviceEnabled() =
        testScope.runTest {
            init(
                faceAuthCurrentlyAllowed = true,
                faceAuthEnrolledAndEnabled = true,
                hasStrongFace = true,
                fingerprintAuthCurrentlyAllowed = false,
                fingerprintAuthEnrolledAndEnabled = false,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = true,
            )
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            runCurrent()

            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            val expectedSubtitle =
                resString(R.string.kg_prompt_subtitle_for_secure_lock_device_biometric_auth_face)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun initialMessage_strongCoexAuth_secureLockDeviceEnabled() =
        testScope.runTest {
            init(
                faceAuthCurrentlyAllowed = true,
                faceAuthEnrolledAndEnabled = true,
                hasStrongFace = true,
                fingerprintAuthCurrentlyAllowed = true,
                fingerprintAuthEnrolledAndEnabled = true,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = true,
            )
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            runCurrent()

            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            val expectedSubtitle =
                resString(R.string.kg_prompt_subtitle_for_secure_lock_device_biometric_auth_coex)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)
        }

    @Test
    fun onPrimaryAuthMethodChangeToPattern_initialMessageUpdates() =
        testScope.runTest {
            init(fingerprintAuthCurrentlyAllowed = false)
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            whenever(securityModel.getSecurityMode(PRIMARY_USER_ID)).thenReturn(Pattern)
            kosmos.fakeKeyguardBouncerRepository.setLastShownSecurityMode(Pattern)
            assertThat(bouncerMessage).isNotNull()
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo("Draw pattern")
        }

    @Test
    fun onIncorrectSecurityInput_providesTheAppropriateValueForBouncerMessage() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            underTest.onPrimaryAuthIncorrectAttempt(isDuplicate = false)

            assertThat(bouncerMessage).isNotNull()
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(wrongCredString)
        }

    @Test
    fun onIncorrectSecurityInputWithDuplicate_providesTheAppropriateValueForBouncerMessage() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            underTest.onPrimaryAuthIncorrectAttempt(isDuplicate = true)

            assertThat(bouncerMessage).isNotNull()
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(duplicateCredString)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun onIncorrectSecurityInput_whenSecureLockDeviceEnabled_providesCorrectBouncerMessage() =
        testScope.runTest {
            init(
                fingerprintAuthEnrolledAndEnabled = true,
                fingerprintAuthCurrentlyAllowed = false,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = false,
            )
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            underTest.onPrimaryAuthIncorrectAttempt(isDuplicate = false)

            assertThat(bouncerMessage).isNotNull()
            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(wrongCredString)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun onIncorrectSecurityInputWithDuplicate_whenSecureLockDeviceEnabled_providesCorrectBouncerMessage() =
        testScope.runTest {
            init(
                fingerprintAuthEnrolledAndEnabled = true,
                fingerprintAuthCurrentlyAllowed = false,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = false,
            )
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            underTest.onPrimaryAuthIncorrectAttempt(isDuplicate = true)

            assertThat(bouncerMessage).isNotNull()
            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(duplicateCredString)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun onFaceFailed_whenSecureLockDeviceEnabled_providesCorrectBouncerMessage() =
        testScope.runTest {
            init(
                faceAuthCurrentlyAllowed = true,
                faceAuthEnrolledAndEnabled = true,
                hasStrongFace = true,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = true,
            )
            captureKeyguardUpdateMonitorCallback()

            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            keyguardUpdateMonitorCaptor.value.onBiometricAuthFailed(BiometricSourceType.FACE)

            assertThat(bouncerMessage).isNotNull()
            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            val expectedSubtitle = resString(R.string.bouncer_face_not_recognized)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun onFingerprintFailed_whenSecureLockDeviceEnabled_providesCorrectBouncerMessage() =
        testScope.runTest {
            init(
                fingerprintAuthCurrentlyAllowed = true,
                fingerprintAuthEnrolledAndEnabled = true,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = true,
            )
            captureKeyguardUpdateMonitorCallback()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            keyguardUpdateMonitorCaptor.value.onBiometricAuthFailed(BiometricSourceType.FINGERPRINT)

            assertThat(bouncerMessage).isNotNull()
            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            val expectedSubtitle = resString(R.string.kg_fp_not_recognized)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)
        }

    @Test
    fun onUserStartsPrimaryAuthInput_clearsAllSetBouncerMessages() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            underTest.onPrimaryAuthIncorrectAttempt(isDuplicate = false)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(wrongCredString)

            underTest.onPrimaryBouncerUserInput()

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
        }

    @Test
    fun setCustomMessage_propagateValue() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)

            underTest.setCustomMessage("not empty")

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryMessage?.message).isEqualTo("not empty")

            underTest.setCustomMessage(null)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryMessage?.message).isNull()
        }

    @Test
    fun setFaceMessage_propagateValue() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)

            underTest.setFaceAcquisitionMessage("not empty")

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryMessage?.message).isEqualTo("not empty")

            underTest.setFaceAcquisitionMessage(null)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryMessage?.message).isNull()
        }

    @Test
    fun resetMessageBackToDefault_faceAuthRestarts() =
        testScope.runTest {
            init()
            captureKeyguardUpdateMonitorCallback()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)

            underTest.setFaceAcquisitionMessage("not empty")

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryMessage?.message).isEqualTo("not empty")

            keyguardUpdateMonitorCaptor.value.onBiometricAcquired(
                BiometricSourceType.FACE,
                BiometricFaceConstants.FACE_ACQUIRED_START,
            )

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryMessage?.message).isNull()
        }

    @Test
    fun faceRestartDoesNotResetFingerprintMessage() =
        testScope.runTest {
            init()
            captureKeyguardUpdateMonitorCallback()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)

            underTest.setFingerprintAcquisitionMessage("not empty")

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryMessage?.message).isEqualTo("not empty")

            keyguardUpdateMonitorCaptor.value.onBiometricAcquired(
                BiometricSourceType.FACE,
                BiometricFaceConstants.FACE_ACQUIRED_START,
            )

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryMessage?.message).isEqualTo("not empty")
        }

    @Test
    fun setFingerprintMessage_propagateValue() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)

            underTest.setFingerprintAcquisitionMessage("not empty")

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryMessage?.message).isEqualTo("not empty")

            underTest.setFingerprintAcquisitionMessage(null)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryMessage?.message).isNull()
        }

    @Test
    @DisableFlags(
        android.security.Flags.FLAG_LOCKSCREEN_TIMEOUT_SHORTLINK,
        android.security.Flags.FLAG_LOCKSCREEN_LARGER_TIMEOUT_TIME_UNITS,
    )
    fun onPrimaryAuthLockoutWithoutShortlink_startsTimerForSpecifiedNumberOfSecondsNoLargeUnits() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)

            underTest.onPrimaryAuthLockedOut(3)

            verify(countDownTimerUtil)
                .startTimer(eq(3000L), eq(1000L), countDownTimerCallback.capture())

            countDownTimerCallback.value.onTick(2000L)

            val primaryMessage = bouncerMessage!!.message!!
            assertThat(primaryMessage.messageResId!!)
                .isEqualTo(kg_too_many_failed_attempts_countdown)
            assertThat(primaryMessage.formatterArgs).isEqualTo(mapOf(Pair("count", 2L)))

            val secondaryMessage = bouncerMessage!!.secondaryMessage!!
            assertThat(secondaryMessage.messageResId!!).isEqualTo(lockedOutRes)
            assertThat(secondaryMessage.formatterArgs).isNull()
        }

    @Test
    @EnableFlags(android.security.Flags.FLAG_LOCKSCREEN_TIMEOUT_SHORTLINK)
    @DisableFlags(android.security.Flags.FLAG_LOCKSCREEN_LARGER_TIMEOUT_TIME_UNITS)
    fun onPrimaryAuthLockout_startsTimerForSpecifiedNumberOfSecondsNoLargeUnits() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)

            underTest.onPrimaryAuthLockedOut(3)

            verify(countDownTimerUtil)
                .startTimer(eq(3000L), eq(1000L), countDownTimerCallback.capture())

            countDownTimerCallback.value.onTick(2000L)

            val primaryMessage = bouncerMessage!!.message!!
            assertThat(primaryMessage.messageResId!!)
                .isEqualTo(kg_too_many_failed_attempts_countdown)
            assertThat(primaryMessage.formatterArgs).isEqualTo(mapOf(Pair("count", 2L)))

            val secondaryMessage = bouncerMessage!!.secondaryMessage!!
            assertThat(secondaryMessage.messageResId!!).isEqualTo(lockedOutShortlinkRes)
            val expectedShortlink =
                resString(com.android.internal.R.string.config_lockscreenLockoutShortlink)
            assertThat(expectedShortlink).isNotEmpty()
            assertThat(secondaryMessage.formatterArgs)
                .isEqualTo(mapOf(Pair("shortlink", expectedShortlink)))
        }

    @Test
    @EnableFlags(android.security.Flags.FLAG_LOCKSCREEN_LARGER_TIMEOUT_TIME_UNITS)
    @DisableFlags(android.security.Flags.FLAG_LOCKSCREEN_TIMEOUT_SHORTLINK)
    fun onPrimaryAuthLockoutWithoutShortlink_startsTimerForSpecifiedNumberOfSeconds() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)

            underTest.onPrimaryAuthLockedOut(120L)

            verify(countDownTimerUtil)
                .startTimer(eq(120000L), eq(1000L), countDownTimerCallback.capture())

            countDownTimerCallback.value.onTick(2000L)

            val primaryMessage = bouncerMessage!!.message!!
            assertThat(primaryMessage.messageResId!!)
                .isEqualTo(kg_too_many_failed_attempts_countdown_seconds)
            assertThat(primaryMessage.formatterArgs).isEqualTo(mapOf(Pair("count", 2L)))

            val secondaryMessage = bouncerMessage!!.secondaryMessage!!
            assertThat(secondaryMessage.messageResId!!).isEqualTo(lockedOutRes)
            assertThat(secondaryMessage.formatterArgs).isNull()
        }

    @Test
    @EnableFlags(
        android.security.Flags.FLAG_LOCKSCREEN_TIMEOUT_SHORTLINK,
        android.security.Flags.FLAG_LOCKSCREEN_LARGER_TIMEOUT_TIME_UNITS,
    )
    fun onPrimaryAuthLockout_startsTimerForSpecifiedNumberOfMinutes() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)

            underTest.onPrimaryAuthLockedOut(3)

            verify(countDownTimerUtil)
                .startTimer(eq(3000L), eq(1000L), countDownTimerCallback.capture())

            countDownTimerCallback.value.onTick(2000L)

            val primaryMessage = bouncerMessage!!.message!!
            assertThat(primaryMessage.messageResId!!)
                .isEqualTo(kg_too_many_failed_attempts_countdown_seconds)
            assertThat(primaryMessage.formatterArgs).isEqualTo(mapOf(Pair("count", 2L)))

            val secondaryMessage = bouncerMessage!!.secondaryMessage!!
            assertThat(secondaryMessage.messageResId!!).isEqualTo(lockedOutShortlinkRes)
            val expectedShortlink =
                resString(com.android.internal.R.string.config_lockscreenLockoutShortlink)
            assertThat(expectedShortlink).isNotEmpty()
            assertThat(secondaryMessage.formatterArgs)
                .isEqualTo(mapOf(Pair("shortlink", expectedShortlink)))
        }

    private val Int.years: Duration
        get() = 365.days * this

    @Test
    @EnableFlags(android.security.Flags.FLAG_LOCKSCREEN_LARGER_TIMEOUT_TIME_UNITS)
    fun onPrimaryAuthLockout_showsCorrectTimeUnits() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)

            fun assertTextFor(timeout: Duration, expectedRes: Int, expectedCount: Long) {
                underTest.onPrimaryAuthLockedOut(timeout.inWholeSeconds)

                verify(countDownTimerUtil)
                    .startTimer(
                        eq(timeout.inWholeMilliseconds),
                        eq(1000L),
                        countDownTimerCallback.capture(),
                    )

                countDownTimerCallback.value.onTick(timeout.inWholeMilliseconds)

                val primaryMessage = bouncerMessage!!.message!!
                assertThat(primaryMessage.messageResId).isEqualTo(expectedRes)
                assertThat(primaryMessage.formatterArgs)
                    .isEqualTo(mapOf(Pair("count", expectedCount)))
            }

            assertTextFor(9.years, kg_too_many_failed_attempts_countdown_years, 9L)
            assertTextFor(9.years - 1.days, kg_too_many_failed_attempts_countdown_years, 9L)
            assertTextFor(8.years + 1.days, kg_too_many_failed_attempts_countdown_years, 9L)
            assertTextFor(8.years, kg_too_many_failed_attempts_countdown_years, 8L)
            assertTextFor(1.years + 1.days, kg_too_many_failed_attempts_countdown_years, 2L)
            assertTextFor(1.years, kg_too_many_failed_attempts_countdown_years, 1L)
            assertTextFor(364.days + 1.hours, kg_too_many_failed_attempts_countdown_years, 1L)

            // Round up to the day above 36 hours
            assertTextFor(364.days, kg_too_many_failed_attempts_countdown_days, 364L)
            assertTextFor(364.days - 1.hours, kg_too_many_failed_attempts_countdown_days, 364L)
            assertTextFor(363.days + 1.hours, kg_too_many_failed_attempts_countdown_days, 364L)
            assertTextFor(363.days, kg_too_many_failed_attempts_countdown_days, 363L)
            assertTextFor(2.days, kg_too_many_failed_attempts_countdown_days, 2L)
            assertTextFor(47.hours, kg_too_many_failed_attempts_countdown_days, 2L)
            assertTextFor(37.hours, kg_too_many_failed_attempts_countdown_days, 2L)

            // Round up to the hour above 90 minutes
            assertTextFor(36.hours, kg_too_many_failed_attempts_countdown_hours, 36L)
            assertTextFor(36.hours - 1.minutes, kg_too_many_failed_attempts_countdown_hours, 36L)
            assertTextFor(35.hours + 1.minutes, kg_too_many_failed_attempts_countdown_hours, 36L)
            assertTextFor(35.hours, kg_too_many_failed_attempts_countdown_hours, 35L)
            assertTextFor(2.hours, kg_too_many_failed_attempts_countdown_hours, 2L)
            assertTextFor(90.minutes + 1.seconds, kg_too_many_failed_attempts_countdown_hours, 2L)

            // Round up to the minute above 59 seconds
            assertTextFor(90.minutes, kg_too_many_failed_attempts_countdown_minutes, 90L)
            assertTextFor(
                90.minutes - 1.seconds,
                kg_too_many_failed_attempts_countdown_minutes,
                90L,
            )
            assertTextFor(
                89.minutes + 1.seconds,
                kg_too_many_failed_attempts_countdown_minutes,
                90L,
            )
            assertTextFor(89.minutes, kg_too_many_failed_attempts_countdown_minutes, 89L)
            assertTextFor(1.minutes + 1.seconds, kg_too_many_failed_attempts_countdown_minutes, 2L)
            assertTextFor(1.minutes, kg_too_many_failed_attempts_countdown_minutes, 1L)

            // Show seconds simply as seconds
            assertTextFor(59.seconds, kg_too_many_failed_attempts_countdown_seconds, 59L)
            assertTextFor(1.seconds, kg_too_many_failed_attempts_countdown_seconds, 1L)
        }

    @Test
    fun onPrimaryAuthLockout_selectingNewUserCancelsTimeout() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)

            underTest.onPrimaryAuthLockedOut(3)

            verify(countDownTimerUtil)
                .startTimer(eq(3000L), eq(1000L), countDownTimerCallback.capture())

            kosmos.fakeUserRepository.setSelectedUserInfo(SECONDARY_USER)
            runCurrent()

            verify(countDownTimerUtil).cancelTimer()
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryMessage?.message).isNull()
        }

    @Test
    fun onPrimaryAuthLockout_timerComplete_resetsRepositoryMessages() =
        testScope.runTest {
            init()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)

            underTest.onPrimaryAuthLockedOut(3)

            verify(countDownTimerUtil)
                .startTimer(eq(3000L), eq(1000L), countDownTimerCallback.capture())

            countDownTimerCallback.value.onFinish()

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(bouncerMessage?.secondaryMessage?.message).isNull()
        }

    @Test
    fun onFaceLockout_propagatesState() =
        testScope.runTest {
            init(
                fingerprintAuthEnrolledAndEnabled = true,
                fingerprintAuthCurrentlyAllowed = true,
                faceAuthEnrolledAndEnabled = true,
                hasStrongFace = false,
            )
            val lockoutMessage by collectLastValue(underTest.bouncerMessage)
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(true)
            runCurrent()

            assertThat(primaryResMessage(lockoutMessage)).isEqualTo(fpOrCredString)
            assertThat(secondaryResMessage(lockoutMessage))
                .isEqualTo("Can’t unlock with face. Too many attempts.")

            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(false)
            runCurrent()

            assertThat(primaryResMessage(lockoutMessage)).isEqualTo(fpOrCredString)
            assertThat(lockoutMessage?.secondaryMessage?.message).isNull()
        }

    @Test
    fun faceLockoutThenFaceFailure_doesNotUpdateMessage() =
        testScope.runTest {
            init(
                fingerprintAuthEnrolledAndEnabled = true,
                fingerprintAuthCurrentlyAllowed = true,
                faceAuthEnrolledAndEnabled = true,
                hasStrongFace = false,
            )
            captureKeyguardUpdateMonitorCallback()
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(true)
            runCurrent()
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(secondaryResMessage(bouncerMessage))
                .isEqualTo("Can’t unlock with face. Too many attempts.")

            // WHEN face failure comes in during lockout
            keyguardUpdateMonitorCaptor.value.onBiometricAuthFailed(BiometricSourceType.FACE)

            // THEN lockout message does NOT update to face failure message
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(fpOrCredString)
            assertThat(secondaryResMessage(bouncerMessage))
                .isEqualTo("Can’t unlock with face. Too many attempts.")
        }

    @Test
    fun onFaceLockoutStateChange_whenFaceIsNotEnrolled_isANoop() =
        testScope.runTest {
            init()
            val lockoutMessage by collectLastValue(underTest.bouncerMessage)

            runCurrent()
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(true)
            runCurrent()

            assertThat(primaryResMessage(lockoutMessage)).isEqualTo(fpOrCredString)
            assertThat(lockoutMessage?.secondaryMessage?.message).isNull()
            assertThat(lockoutMessage?.secondaryMessage?.messageResId).isEqualTo(0)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun onFaceLockout_whenSecureLockDeviceEnabled_propagatesState() =
        testScope.runTest {
            init(
                faceAuthEnrolledAndEnabled = true,
                faceAuthCurrentlyAllowed = false,
                hasStrongFace = true,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = false,
            )
            runCurrent()

            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            runCurrent()
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(true)
            runCurrent()
            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(tooManyAttemptsString)
        }

    @Test
    fun onFaceLockout_whenItIsClass3_propagatesState() =
        testScope.runTest {
            init(
                faceAuthCurrentlyAllowed = true,
                faceAuthEnrolledAndEnabled = true,
                hasStrongFace = true,
                fingerprintAuthCurrentlyAllowed = true,
                fingerprintAuthEnrolledAndEnabled = true,
            )
            val lockoutMessage by collectLastValue(underTest.bouncerMessage)
            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(true)
            runCurrent()

            assertThat(primaryResMessage(lockoutMessage)).isEqualTo(enterCredString)
            assertThat(secondaryResMessage(lockoutMessage)).isEqualTo(tooManyAttemptsString)

            kosmos.fakeDeviceEntryFaceAuthRepository.setLockedOut(false)
            runCurrent()

            assertThat(primaryResMessage(lockoutMessage)).isEqualTo(fpOrCredString)
            assertThat(lockoutMessage?.secondaryMessage?.message).isNull()
        }

    @Test
    fun onFingerprintLockout_propagatesState() =
        testScope.runTest {
            init()
            val lockedOutMessage by collectLastValue(underTest.bouncerMessage)

            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(true)
            runCurrent()

            assertThat(primaryResMessage(lockedOutMessage)).isEqualTo(enterCredString)
            assertThat(secondaryResMessage(lockedOutMessage)).isEqualTo(tooManyAttemptsString)

            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            runCurrent()

            assertThat(primaryResMessage(lockedOutMessage)).isEqualTo(fpOrCredString)
            assertThat(lockedOutMessage?.secondaryMessage?.message).isNull()
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun onFingerprintLockout_whenSecureLockDeviceEnabled_propagatesState() =
        testScope.runTest {
            init(
                fingerprintAuthEnrolledAndEnabled = true,
                fingerprintAuthCurrentlyAllowed = false,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = false,
            )
            runCurrent()

            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            runCurrent()
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(true)
            runCurrent()
            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(tooManyAttemptsString)
        }

    @Test
    fun onFingerprintLockoutStateChange_whenFingerprintIsNotEnrolled_isANoop() =
        testScope.runTest {
            init(fingerprintAuthEnrolledAndEnabled = false)
            val lockoutMessage by collectLastValue(underTest.bouncerMessage)
            runCurrent()
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(true)
            runCurrent()

            assertThat(primaryResMessage(lockoutMessage)).isEqualTo(enterCredString)
            assertThat(lockoutMessage?.secondaryMessage?.message).isNull()
            assertThat(lockoutMessage?.secondaryMessage?.messageResId).isEqualTo(0)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun defaultMessageShown_afterStrongFingerprintAuthUnlock_secureLockDeviceEnabled() =
        testScope.runTest {
            init(
                fingerprintAuthCurrentlyAllowed = true,
                fingerprintAuthEnrolledAndEnabled = true,
                faceAuthCurrentlyAllowed = false,
                faceAuthEnrolledAndEnabled = false,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = true,
            )
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            runCurrent()

            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            val expectedSubtitle =
                resString(
                    R.string.kg_prompt_subtitle_for_secure_lock_device_biometric_auth_fingerprint
                )
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)

            underTest.onSecureLockDeviceUnlock()
            runCurrent()

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun messageUpdated_onStrongFaceAuthErrorShownAndCleared_secureLockDeviceEnabled() =
        testScope.runTest {
            init(
                fingerprintAuthCurrentlyAllowed = false,
                fingerprintAuthEnrolledAndEnabled = false,
                faceAuthCurrentlyAllowed = true,
                faceAuthEnrolledAndEnabled = true,
                hasStrongFace = true,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = true,
            )
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            runCurrent()

            var expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            var expectedSubtitle = resString(R.string.bouncer_face_not_recognized)

            // Error shown when retry available
            underTest.onSecureLockDeviceRetryAuthentication(showingError = true)
            runCurrent()

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)

            expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            expectedSubtitle =
                resString(R.string.kg_prompt_subtitle_for_secure_lock_device_biometric_auth_face)

            // Error cleared, awaiting retry
            underTest.onSecureLockDeviceRetryAuthentication(showingError = false)
            runCurrent()

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun messageUpdated_onStrongFaceAuthPendingConfirmation_secureLockDeviceEnabled() =
        testScope.runTest {
            init(
                fingerprintAuthCurrentlyAllowed = false,
                fingerprintAuthEnrolledAndEnabled = false,
                faceAuthCurrentlyAllowed = true,
                faceAuthEnrolledAndEnabled = true,
                hasStrongFace = true,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = true,
            )
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            runCurrent()

            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            val expectedSubtitle =
                resString(R.string.keyguard_face_successful_unlock_confirm_button)

            underTest.onSecureLockDevicePendingConfirmation()
            runCurrent()

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun defaultMessageShown_afterStrongFaceAuthUnlock_secureLockDeviceEnabled() =
        testScope.runTest {
            init(
                fingerprintAuthCurrentlyAllowed = false,
                fingerprintAuthEnrolledAndEnabled = false,
                faceAuthCurrentlyAllowed = true,
                faceAuthEnrolledAndEnabled = true,
                hasStrongFace = true,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = true,
            )
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            runCurrent()

            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            val expectedSubtitle =
                resString(R.string.kg_prompt_subtitle_for_secure_lock_device_biometric_auth_face)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)

            underTest.onSecureLockDeviceUnlock()
            runCurrent()

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun defaultMessageShown_afterStrongCoexAuthUnlock_secureLockDeviceEnabled() =
        testScope.runTest {
            init(
                fingerprintAuthCurrentlyAllowed = true,
                fingerprintAuthEnrolledAndEnabled = true,
                faceAuthCurrentlyAllowed = true,
                faceAuthEnrolledAndEnabled = true,
                hasStrongFace = true,
                secureLockDeviceEnabled = true,
                secureLockDeviceBiometricAuthActive = true,
            )
            val bouncerMessage by collectLastValue(underTest.bouncerMessage)
            runCurrent()

            val expectedTitle = resString(R.string.kg_prompt_title_after_secure_lock_device)
            val expectedSubtitle =
                resString(R.string.kg_prompt_subtitle_for_secure_lock_device_biometric_auth_coex)
            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)

            underTest.onSecureLockDeviceUnlock()
            runCurrent()

            assertThat(primaryResMessage(bouncerMessage)).isEqualTo(expectedTitle)
            assertThat(secondaryResMessage(bouncerMessage)).isEqualTo(expectedSubtitle)
        }

    @Test
    fun onUdfpsFingerprint_DoesNotShowFingerprintMessage() =
        testScope.runTest {
            init()
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            kosmos.fakeFingerprintPropertyRepository.supportsUdfps()
            val lockedOutMessage by collectLastValue(underTest.bouncerMessage)

            runCurrent()

            assertThat(primaryResMessage(lockedOutMessage)).isEqualTo(enterCredString)
        }

    @Test
    fun onRestartForMainlineUpdate_shouldProvideRelevantMessage() =
        testScope.runTest {
            init(faceAuthEnrolledAndEnabled = true)
            kosmos.fakeSystemPropertiesHelper.set(SYS_BOOT_REASON_PROP, REBOOT_MAINLINE_UPDATE)

            verifyMessagesForAuthFlag(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT to
                    Pair(enterCredString, deviceUpdatedString)
            )
        }

    @Test
    fun onAuthFlagsChanged_withTrustNotManagedAndNoBiometrics_isANoop() =
        testScope.runTest {
            init(
                fingerprintAuthEnrolledAndEnabled = false,
                fingerprintAuthCurrentlyAllowed = false,
                faceAuthEnrolledAndEnabled = false,
                faceAuthCurrentlyAllowed = false,
            )

            kosmos.fakeTrustRepository.setTrustUsuallyManaged(false)
            runCurrent()

            val defaultMessage = Pair(enterCredString, null)

            verifyMessagesForAuthFlag(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT to
                    defaultMessage,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_USER_REQUEST to
                    defaultMessage,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_LOCKOUT to
                    defaultMessage,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_TIMEOUT to
                    defaultMessage,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_USER_LOCKDOWN to
                    defaultMessage,
                LockPatternUtils.StrongAuthTracker
                    .STRONG_AUTH_REQUIRED_AFTER_NON_STRONG_BIOMETRICS_TIMEOUT to defaultMessage,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_TRUSTAGENT_EXPIRED to
                    defaultMessage,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_FOR_UNATTENDED_UPDATE to
                    defaultMessage,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW to
                    Pair(enterCredString, "For added security, device was locked by work policy"),
            )
        }

    @Test
    fun authFlagsChanges_withTrustManaged_providesDifferentMessages() =
        testScope.runTest {
            init(
                fingerprintAuthEnrolledAndEnabled = false,
                fingerprintAuthCurrentlyAllowed = false,
                faceAuthEnrolledAndEnabled = false,
            )

            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            runCurrent()

            kosmos.fakeTrustRepository.setCurrentUserTrustManaged(true)
            kosmos.fakeTrustRepository.setTrustUsuallyManaged(true)

            val defaultMessage = Pair(enterCredString, null)

            verifyMessagesForAuthFlag(
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
    fun authFlagsChanges_withFaceEnrolled_providesDifferentMessages() =
        testScope.runTest {
            init(
                fingerprintAuthEnrolledAndEnabled = false,
                fingerprintAuthCurrentlyAllowed = false,
                faceAuthEnrolledAndEnabled = true,
            )
            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            kosmos.fakeTrustRepository.setTrustUsuallyManaged(false)

            val defaultMessage = Pair(enterCredString, null)

            verifyMessagesForAuthFlag(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_NOT_REQUIRED to defaultMessage,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_LOCKOUT to
                    defaultMessage,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_USER_REQUEST to
                    defaultMessage,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_TRUSTAGENT_EXPIRED to
                    defaultMessage,
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
                        enterCredString,
                        "Added security required. Device wasn’t unlocked for a while.",
                    ),
            )
        }

    @Test
    fun authFlagsChanges_withFingerprintEnrolled_providesDifferentMessages() =
        testScope.runTest {
            init(fingerprintAuthCurrentlyAllowed = true, fingerprintAuthEnrolledAndEnabled = true)
            kosmos.fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            kosmos.fakeTrustRepository.setCurrentUserTrustManaged(false)
            runCurrent()

            verifyMessagesForAuthFlag(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_NOT_REQUIRED to
                    Pair(fpOrCredString, null)
            )
            biometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(false)
            runCurrent()

            verifyMessagesForAuthFlag(
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_USER_REQUEST to
                    Pair(enterCredString, null),
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_TRUSTAGENT_EXPIRED to
                    Pair(enterCredString, null),
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
                        enterCredString,
                        "Added security required. Device wasn’t unlocked for a while.",
                    ),
            )
        }

    private fun primaryResMessage(bouncerMessage: BouncerMessageModel?) =
        resString(bouncerMessage?.message?.messageResId)

    private fun secondaryResMessage(bouncerMessage: BouncerMessageModel?) =
        resString(bouncerMessage?.secondaryMessage?.messageResId)

    private fun resString(msgResId: Int?): String? =
        msgResId?.let { context.resources.getString(it) }

    private fun TestScope.verifyMessagesForAuthFlag(
        vararg authFlagToExpectedMessages: Pair<Int, Pair<String, String?>>
    ) {
        val authFlagsMessage by collectLastValue(underTest.bouncerMessage)

        authFlagToExpectedMessages.forEach { (flag, messagePair) ->
            if (flag != LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT) {
                kosmos.fakeSystemPropertiesHelper.set(SYS_BOOT_REASON_PROP, "not mainline reboot")
            }
            biometricSettingsRepository.setAuthenticationFlags(
                AuthenticationFlags(PRIMARY_USER_ID, flag)
            )
            runCurrent()

            assertThat(primaryResMessage(authFlagsMessage)).isEqualTo(messagePair.first)
            if (messagePair.second == null) {
                assertThat(authFlagsMessage?.secondaryMessage?.messageResId).isEqualTo(0)
                assertThat(authFlagsMessage?.secondaryMessage?.message).isNull()
            } else {
                assertThat(authFlagsMessage?.secondaryMessage?.messageResId).isNotEqualTo(0)
                assertThat(secondaryResMessage(authFlagsMessage)).isEqualTo(messagePair.second)
            }
        }
    }

    private fun captureKeyguardUpdateMonitorCallback() {
        verify(updateMonitor).registerCallback(keyguardUpdateMonitorCaptor.capture())
    }

    companion object {
        private const val PRIMARY_USER_ID = 0
        private val PRIMARY_USER =
            UserInfo(
                /* id= */ PRIMARY_USER_ID,
                /* name= */ "primary user",
                /* flags= */ UserInfo.FLAG_PRIMARY,
            )
        private const val SECONDARY_USER_ID = 10
        private val SECONDARY_USER =
            UserInfo(
                /* id= */ SECONDARY_USER_ID,
                /* name= */ "secondary user",
                /* flags= */ UserInfo.FLAG_FULL,
            )

        @JvmStatic
        @Parameters(name = "securityMode={0}")
        fun data() = listOf(arrayOf(PIN), arrayOf(Pattern), arrayOf(Password))
    }
}
