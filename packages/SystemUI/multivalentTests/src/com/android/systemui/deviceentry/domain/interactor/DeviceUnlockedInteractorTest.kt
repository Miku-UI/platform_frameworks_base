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

import android.content.pm.UserInfo
import android.os.PowerManager
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.widget.LockPatternUtils
import com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_AUTH_NOT_REQUIRED
import com.android.systemui.Flags
import com.android.systemui.Flags.FLAG_STRONG_AUTH_REQUIRED_AFTER_SIGN_OUT_MESSAGE_FIX
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.authentication.shared.model.AuthenticationResult
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.deviceentry.shared.model.DeviceEntryRestrictionReason
import com.android.systemui.deviceentry.shared.model.DeviceUnlockSource
import com.android.systemui.flags.fakeSystemPropertiesHelper
import com.android.systemui.keyguard.data.repository.biometricSettingsRepository
import com.android.systemui.keyguard.data.repository.fakeBiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeTrustRepository
import com.android.systemui.keyguard.domain.interactor.biometricUnlockInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardEnabledInteractor
import com.android.systemui.keyguard.shared.model.AuthenticationFlags
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.keyguard.shared.model.LockAfterDelayTimerState
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.securelockdevice.data.repository.fakeSecureLockDeviceRepository
import com.android.systemui.securelockdevice.domain.interactor.secureLockDeviceInteractor
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.testKosmos
import com.android.systemui.user.data.model.SelectionStatus
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.data.repository.userSwitcherRepository
import com.android.systemui.user.domain.interactor.selectedUserInteractor
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceUnlockedInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val authenticationRepository by lazy { kosmos.fakeAuthenticationRepository }

    val underTest: DeviceUnlockedInteractor by lazy { kosmos.deviceUnlockedInteractor }

    @Before
    fun setup() {
        kosmos.fakeUserRepository.setUserInfos(listOf(primaryUser, secondaryUser))
    }

    @Test
    fun deviceUnlockStatus_whenUnlockedAndAuthMethodIsNone_isTrue() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.None)

            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
            assertThat(deviceUnlockStatus?.deviceUnlockSource).isNull()
        }

    @Test
    fun deviceUnlockStatus_whenUnlockedAndAuthMethodIsPin_isTrue() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
            assertThat(deviceUnlockStatus?.deviceUnlockSource)
                .isEqualTo(DeviceUnlockSource.Fingerprint)
        }

    @Test
    fun deviceUnlockStatus_whenUnlockedAndAuthMethodIsPinAndInLockdown_isFalse() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            val isInLockdown by collectLastValue(underTest.isInLockdown)

            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            kosmos.fakeBiometricSettingsRepository.setAuthenticationFlags(
                AuthenticationFlags(
                    userId = 1,
                    flag =
                        LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_USER_LOCKDOWN,
                )
            )
            runCurrent()
            assertThat(isInLockdown).isTrue()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
            assertThat(deviceUnlockStatus?.deviceUnlockSource).isNull()
        }

    @Test
    fun deviceUnlockStatus_whenUnlockedAndAuthMethodIsSim_isFalse() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Sim)
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_whenLockedAndAuthMethodIsNone_isTrue() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.None)

            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
        }

    @Test
    fun deviceUnlockStatus_whenLockedAndAuthMethodIsPin_isFalse() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_whenLockedAndAuthMethodIsSim_isFalse() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Sim)

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_whenFaceIsAuthenticatedWhileAwakeWithBypass_isTrue() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            kosmos.powerInteractor.setAwakeForTest()

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
            assertThat(deviceUnlockStatus?.deviceUnlockSource)
                .isEqualTo(DeviceUnlockSource.FaceWithBypassOrUnlockIntent)
        }

    @Test
    fun deviceUnlockStatus_whenFaceIsAuthenticatedWithoutBypass_providesThatInfo() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_NONE_UNLOCKED,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
            assertThat(deviceUnlockStatus?.deviceUnlockSource)
                .isEqualTo(DeviceUnlockSource.FaceWithoutBypass)
        }

    @Test
    fun deviceUnlockStatus_whenFingerprintIsAuthenticated_providesThatInfo() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
            assertThat(deviceUnlockStatus?.deviceUnlockSource)
                .isEqualTo(DeviceUnlockSource.Fingerprint)
        }

    @Test
    fun deviceUnlockStatus_whenUnlockedByTrustAgent_providesThatInfo() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            kosmos.fakeUserRepository.setSelectedUserInfo(
                primaryUser,
                SelectionStatus.SELECTION_COMPLETE,
            )

            kosmos.fakeTrustRepository.setCurrentUserTrusted(true)
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
            assertThat(deviceUnlockStatus?.deviceUnlockSource)
                .isEqualTo(DeviceUnlockSource.TrustAgent)
        }

    @Test
    fun deviceUnlockStatus_isResetToFalse_whenDeviceGoesToSleep() =
        testScope.runTest {
            setLockAfterScreenTimeout(0)
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = false
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest()
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_isResetToFalse_whenDeviceGoesToSleep_afterDelay() =
        testScope.runTest {
            val delay = 5000
            setLockAfterScreenTimeout(delay)
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_TIMEOUT
            )
            kosmos.fakeKeyguardRepository.lockAfterDelayState.value =
                LockAfterDelayTimerState.RUNNING
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.fakeKeyguardRepository.lockAfterDelayState.value =
                LockAfterDelayTimerState.ELAPSED
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_whenDeviceGoesToSleep_thenWakesUpBeforeLockTimerElapses_isTrue() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = false
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_TIMEOUT
            )
            kosmos.fakeKeyguardRepository.lockAfterDelayState.value =
                LockAfterDelayTimerState.RUNNING
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAwakeForTest()
            kosmos.fakeKeyguardRepository.lockAfterDelayState.value =
                LockAfterDelayTimerState.INACTIVE
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
        }

    @Test
    fun deviceUnlockStatus_whenDeviceGoesToSleep_thenLockTimerElapses_isFalse() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_TIMEOUT
            )
            kosmos.fakeKeyguardRepository.lockAfterDelayState.value =
                LockAfterDelayTimerState.RUNNING
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            // Lock timer elapses - device locks
            kosmos.fakeKeyguardRepository.lockAfterDelayState.value =
                LockAfterDelayTimerState.ELAPSED
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()

            // Device wakes up after lock timer has elapsed - device still locked
            kosmos.powerInteractor.setAwakeForTest()
            kosmos.fakeKeyguardRepository.lockAfterDelayState.value =
                LockAfterDelayTimerState.INACTIVE
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_isResetToFalse_whenDeviceGoesToSleep_powerButtonLocksInstantly() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = true
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_notLocked_whenPowerGestureTriggered_powerButtonLocksInstantly() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = true
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON,
                powerButtonGestureTriggered = true,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
        }

    @Test
    fun deviceUnlockStatus_locksImmediatelyOnPowerButton_powerButtonLocksInstantly() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = true
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON,
                powerButtonGestureTriggered = false,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_becomesUnlocked_whenFingerprintUnlocked_whileDeviceAsleep() =
        testScope.runTest {
            setLockAfterScreenTimeout(0)
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()

            kosmos.powerInteractor.setAsleepForTest()
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
        }

    @Test
    fun deviceEntryRestrictionReason_whenFaceOrFingerprintOrTrust_alwaysNull() =
        testScope.runTest {
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(false)
            kosmos.fakeTrustRepository.setTrustUsuallyManaged(false)
            runCurrent()

            verifyRestrictionReasonsForAuthFlags(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT to null,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_USER_REQUEST to null,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_LOCKOUT to null,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_TIMEOUT to null,
                LockPatternUtils.StrongAuthTracker
                    .STRONG_AUTH_REQUIRED_AFTER_NON_STRONG_BIOMETRICS_TIMEOUT to null,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_TRUSTAGENT_EXPIRED to
                    null,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_FOR_UNATTENDED_UPDATE to
                    null,
            )
        }

    @Test
    fun deviceEntryRestrictionReason_whenFaceOrFingerprintOrTrust_whenLockdown() =
        testScope.runTest {
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(false)
            kosmos.fakeTrustRepository.setTrustUsuallyManaged(false)
            runCurrent()

            verifyRestrictionReasonsForAuthFlags(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_USER_LOCKDOWN to
                    DeviceEntryRestrictionReason.UserLockdown,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW to
                    DeviceEntryRestrictionReason.PolicyLockdown,
            )
        }

    @Test
    fun deviceEntryRestrictionReason_whenFaceIsEnrolledAndEnabled_mapsToAuthFlagsState() =
        testScope.runTest {
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(false)
            kosmos.fakeTrustRepository.setTrustUsuallyManaged(false)
            kosmos.fakeSystemPropertiesHelper.set(
                DeviceUnlockedInteractor.SYS_BOOT_REASON_PROP,
                "not mainline reboot",
            )
            runCurrent()

            verifyRestrictionReasonsForAuthFlags(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT to
                    DeviceEntryRestrictionReason.DeviceNotUnlockedSinceReboot,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_ADAPTIVE_AUTH_REQUEST to
                    DeviceEntryRestrictionReason.AdaptiveAuthRequest,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_TIMEOUT to
                    DeviceEntryRestrictionReason.SecurityTimeout,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_USER_LOCKDOWN to
                    DeviceEntryRestrictionReason.UserLockdown,
                LockPatternUtils.StrongAuthTracker
                    .STRONG_AUTH_REQUIRED_AFTER_NON_STRONG_BIOMETRICS_TIMEOUT to
                    DeviceEntryRestrictionReason.NonStrongBiometricsSecurityTimeout,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_FOR_UNATTENDED_UPDATE to
                    DeviceEntryRestrictionReason.UnattendedUpdate,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW to
                    DeviceEntryRestrictionReason.PolicyLockdown,
                LockPatternUtils.StrongAuthTracker.PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE to
                    DeviceEntryRestrictionReason.SecureLockDevicePrimaryAuth,
                LockPatternUtils.StrongAuthTracker
                    .STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE to
                    DeviceEntryRestrictionReason.SecureLockDeviceStrongBiometricOnlyAuth,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_USER_REQUEST to null,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_TRUSTAGENT_EXPIRED to
                    null,
            )
        }

    @Test
    fun deviceEntryRestrictionReason_whenFingerprintIsEnrolledAndEnabled_mapsToAuthFlagsState() =
        testScope.runTest {
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            kosmos.fakeTrustRepository.setTrustUsuallyManaged(false)
            kosmos.fakeSystemPropertiesHelper.set(
                DeviceUnlockedInteractor.SYS_BOOT_REASON_PROP,
                "not mainline reboot",
            )
            runCurrent()

            verifyRestrictionReasonsForAuthFlags(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT to
                    DeviceEntryRestrictionReason.DeviceNotUnlockedSinceReboot,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_ADAPTIVE_AUTH_REQUEST to
                    DeviceEntryRestrictionReason.AdaptiveAuthRequest,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_TIMEOUT to
                    DeviceEntryRestrictionReason.SecurityTimeout,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_USER_LOCKDOWN to
                    DeviceEntryRestrictionReason.UserLockdown,
                LockPatternUtils.StrongAuthTracker
                    .STRONG_AUTH_REQUIRED_AFTER_NON_STRONG_BIOMETRICS_TIMEOUT to
                    DeviceEntryRestrictionReason.NonStrongBiometricsSecurityTimeout,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_FOR_UNATTENDED_UPDATE to
                    DeviceEntryRestrictionReason.UnattendedUpdate,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW to
                    DeviceEntryRestrictionReason.PolicyLockdown,
                LockPatternUtils.StrongAuthTracker.PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE to
                    DeviceEntryRestrictionReason.SecureLockDevicePrimaryAuth,
                LockPatternUtils.StrongAuthTracker
                    .STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE to
                    DeviceEntryRestrictionReason.SecureLockDeviceStrongBiometricOnlyAuth,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_USER_REQUEST to null,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_TRUSTAGENT_EXPIRED to
                    null,
            )
        }

    @Test
    fun deviceEntryRestrictionReason_whenTrustAgentIsEnabled_mapsToAuthFlagsState() =
        testScope.runTest {
            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(false)
            kosmos.fakeTrustRepository.setTrustUsuallyManaged(true)
            kosmos.fakeTrustRepository.setCurrentUserTrustManaged(false)
            kosmos.fakeSystemPropertiesHelper.set(
                DeviceUnlockedInteractor.SYS_BOOT_REASON_PROP,
                "not mainline reboot",
            )
            runCurrent()

            verifyRestrictionReasonsForAuthFlags(
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT to
                    DeviceEntryRestrictionReason.DeviceNotUnlockedSinceReboot,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_ADAPTIVE_AUTH_REQUEST to
                    DeviceEntryRestrictionReason.AdaptiveAuthRequest,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_TIMEOUT to
                    DeviceEntryRestrictionReason.SecurityTimeout,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_USER_LOCKDOWN to
                    DeviceEntryRestrictionReason.UserLockdown,
                LockPatternUtils.StrongAuthTracker
                    .STRONG_AUTH_REQUIRED_AFTER_NON_STRONG_BIOMETRICS_TIMEOUT to
                    DeviceEntryRestrictionReason.NonStrongBiometricsSecurityTimeout,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_FOR_UNATTENDED_UPDATE to
                    DeviceEntryRestrictionReason.UnattendedUpdate,
                LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW to
                    DeviceEntryRestrictionReason.PolicyLockdown,
                LockPatternUtils.StrongAuthTracker.PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE to
                    DeviceEntryRestrictionReason.SecureLockDevicePrimaryAuth,
                LockPatternUtils.StrongAuthTracker
                    .STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE to
                    DeviceEntryRestrictionReason.SecureLockDeviceStrongBiometricOnlyAuth,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_USER_REQUEST to
                    DeviceEntryRestrictionReason.TrustAgentDisabled,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_TRUSTAGENT_EXPIRED to
                    DeviceEntryRestrictionReason.TrustAgentDisabled,
            )
        }

    @Test
    fun deviceEntryRestrictionReason_whenDeviceRebootedForMainlineUpdate_mapsToTheCorrectReason() =
        testScope.runTest {
            val deviceEntryRestrictionReason by
                collectLastValue(underTest.deviceEntryRestrictionReason)
            kosmos.fakeSystemPropertiesHelper.set(
                DeviceUnlockedInteractor.SYS_BOOT_REASON_PROP,
                DeviceUnlockedInteractor.REBOOT_MAINLINE_UPDATE,
            )
            kosmos.fakeBiometricSettingsRepository.setAuthenticationFlags(
                AuthenticationFlags(
                    userId = 1,
                    flag = LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT,
                )
            )
            runCurrent()

            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(false)
            kosmos.fakeTrustRepository.setTrustUsuallyManaged(false)
            runCurrent()

            assertThat(deviceEntryRestrictionReason).isNull()

            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
            runCurrent()

            assertThat(deviceEntryRestrictionReason)
                .isEqualTo(DeviceEntryRestrictionReason.DeviceNotUnlockedSinceMainlineUpdate)

            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            runCurrent()

            assertThat(deviceEntryRestrictionReason)
                .isEqualTo(DeviceEntryRestrictionReason.DeviceNotUnlockedSinceMainlineUpdate)

            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(false)
            kosmos.fakeTrustRepository.setTrustUsuallyManaged(true)
            runCurrent()

            assertThat(deviceEntryRestrictionReason)
                .isEqualTo(DeviceEntryRestrictionReason.DeviceNotUnlockedSinceMainlineUpdate)
        }

    @EnableFlags(FLAG_STRONG_AUTH_REQUIRED_AFTER_SIGN_OUT_MESSAGE_FIX)
    @Test
    fun deviceEntryRestrictionReason_whenUserNotUnlockedSinceSignOut_mapsToTheCorrectReason() =
        testScope.runTest {
            val deviceEntryRestrictionReason by
                collectLastValue(underTest.deviceEntryRestrictionReason)
            kosmos.userSwitcherRepository.isUserSwitchingMustGoThroughLoginScreen = true
            kosmos.fakeBiometricSettingsRepository.setAuthenticationFlags(
                AuthenticationFlags(
                    userId = 1,
                    flag = LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT,
                )
            )
            runCurrent()

            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(false)
            kosmos.fakeTrustRepository.setTrustUsuallyManaged(false)
            runCurrent()

            assertThat(deviceEntryRestrictionReason).isNull()

            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
            runCurrent()

            assertThat(deviceEntryRestrictionReason)
                .isEqualTo(DeviceEntryRestrictionReason.UserNotUnlockedSinceSignOut)

            kosmos.fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(false)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            runCurrent()

            assertThat(deviceEntryRestrictionReason)
                .isEqualTo(DeviceEntryRestrictionReason.UserNotUnlockedSinceSignOut)

            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(false)
            kosmos.fakeTrustRepository.setTrustUsuallyManaged(true)
            runCurrent()

            assertThat(deviceEntryRestrictionReason)
                .isEqualTo(DeviceEntryRestrictionReason.UserNotUnlockedSinceSignOut)
        }

    @Test
    fun deviceUnlockStatus_locksWithDelay_afterDreamStarts_withTimeout() =
        testScope.runTest {
            val isUnlocked by collectLastValue(underTest.deviceUnlockStatus.map { it.isUnlocked })
            unlockDevice()

            startDreaming()
            assertThat(isUnlocked).isTrue()

            kosmos.fakeKeyguardRepository.lockAfterDelayState.value =
                LockAfterDelayTimerState.RUNNING
            runCurrent()
            assertThat(isUnlocked).isTrue()

            kosmos.fakeKeyguardRepository.lockAfterDelayState.value =
                LockAfterDelayTimerState.ELAPSED
            runCurrent()
            assertThat(isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_doesNotLockWithDelay_whenDreamStopsBeforeTimeout() =
        testScope.runTest {
            val isUnlocked by collectLastValue(underTest.deviceUnlockStatus.map { it.isUnlocked })
            unlockDevice()

            startDreaming()
            assertThat(isUnlocked).isTrue()

            kosmos.fakeKeyguardRepository.lockAfterDelayState.value =
                LockAfterDelayTimerState.RUNNING
            runCurrent()
            assertThat(isUnlocked).isTrue()

            stopDreaming()
            assertThat(isUnlocked).isTrue()

            kosmos.fakeKeyguardRepository.lockAfterDelayState.value =
                LockAfterDelayTimerState.INACTIVE
            runCurrent()
            assertThat(isUnlocked).isTrue()
        }

    @Test
    fun lockNow() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            val isUnlocked by collectLastValue(underTest.deviceUnlockStatus.map { it.isUnlocked })
            unlockDevice()
            assertThat(isUnlocked).isTrue()

            underTest.lockNow("test")
            runCurrent()

            assertThat(isUnlocked).isFalse()
        }

    @Test
    fun unlockNow() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            val isUnlocked by collectLastValue(underTest.deviceUnlockStatus.map { it.isUnlocked })
            unlockDevice()
            assertThat(isUnlocked).isTrue()
            underTest.lockNow("test")
            runCurrent()
            assertThat(isUnlocked).isFalse()

            underTest.unlockNowForPowerButtonGesture("test")

            runCurrent()
            assertThat(isUnlocked).isTrue()
        }

    // Regression test for b/457867010
    @Test
    fun deviceUnlockStatus_doesLock_whenLockNowIsCalled_beforeAuthenticationMethodChanges() =
        testScope.runTest {
            val isUnlocked by collectLastValue(underTest.deviceUnlockStatus.map { it.isUnlocked })

            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.None)
            runCurrent()
            assertThat(isUnlocked).isTrue()

            // Simulate user switch, where lock requesting is coming in before authentication method
            // is switched.
            underTest.lockNow("test-reason")
            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Password)
            runCurrent()

            // After the user switch, authenticationMethod.collectLatest should re-trigger
            // handleLockAndUnlockEvents, which will then collect the buffered lockNow request.
            assertThat(isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_doesNotLock_afterUserSwitch_fromSecureToInsecureUser() =
        testScope.runTest {
            val isUnlocked by collectLastValue(underTest.deviceUnlockStatus.map { it.isUnlocked })

            // 1. Start with User A, who has a PIN.
            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
            runCurrent()

            // 2. Lock the device for User A.
            underTest.lockNow("test")
            runCurrent()
            assertThat(isUnlocked).isFalse()

            // 3. Switch to User B, who has no lock method. The device should be unlocked.
            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.None)
            runCurrent()
            assertThat(isUnlocked).isTrue()

            // 4. Now, set a PIN for User B. The device should REMAIN unlocked. The lock request
            //    from User A should have already been consumed.
            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
            runCurrent()
            assertThat(isUnlocked).isTrue()
        }

    @Test
    fun deviceUnlockStatus_doesNotLock_secureAuth_keyguardDisabled() =
        testScope.runTest {
            val isUnlocked by collectLastValue(underTest.deviceUnlockStatus.map { it.isUnlocked })

            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
            runCurrent()
            assertThat(isUnlocked).isFalse()

            kosmos.keyguardEnabledInteractor.notifyKeyguardEnabled(false)
            assertThat(isUnlocked).isTrue()

            underTest.lockNow("test")
            assertThat(isUnlocked).isTrue()
        }

    @Test
    fun deviceUnlockStatus_doesLock_secureSim_keyguardDisabled() =
        testScope.runTest {
            val isUnlocked by collectLastValue(underTest.deviceUnlockStatus.map { it.isUnlocked })

            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Sim)
            runCurrent()
            assertThat(isUnlocked).isFalse()

            kosmos.keyguardEnabledInteractor.notifyKeyguardEnabled(false)
            assertThat(isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_doesNotLock_insecureAuth_keyguardEnabled() =
        testScope.runTest {
            val isUnlocked by collectLastValue(underTest.deviceUnlockStatus.map { it.isUnlocked })

            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.None)
            runCurrent()
            assertThat(isUnlocked).isTrue()

            kosmos.keyguardEnabledInteractor.notifyKeyguardEnabled(true)
            assertThat(isUnlocked).isTrue()

            underTest.lockNow("test")
            assertThat(isUnlocked).isTrue()
        }

    @Test
    fun deviceUnlockStatus_isResetToFalse_whenDeviceGoesToSleep_fromSleepButton() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = false
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_SLEEP_BUTTON
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_isResetToFalse_whenDeviceGoesToSleep_fromFold() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = false
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_DEVICE_FOLD
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_isResetToFalse_whenDeviceGoesToSleep_fromLidClose() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = false
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    fun deviceUnlockStatus_staysUnlocked_whenDeviceGoesToSleep_whileIsTrusted() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = false
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.fakeTrustRepository.setCurrentUserTrusted(true)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_SLEEP_BUTTON
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
        }

    @Test
    fun deviceUnlockStatus_staysUnlocked_whileIsTrusted() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            kosmos.fakeTrustRepository.setCurrentUserTrusted(true)
            unlockDevice()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_SLEEP_BUTTON
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
        }

    @Test
    fun deviceUnlockStatus_becomesLocked_whenNoLongerTrusted_whileAsleep() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            kosmos.fakeTrustRepository.setCurrentUserTrusted(true)
            unlockDevice()
            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_SLEEP_BUTTON
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.fakeTrustRepository.setCurrentUserTrusted(false)
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    fun deviceUnlockStatus_updatesAcrossTwoFactorBouncerUnlock_whenSecureLockDeviceEnabled_fp() =
        testScope.runTest {
            val authenticationFlags by
                collectLastValue(kosmos.deviceEntryBiometricSettingsInteractor.authenticationFlags)
            val deviceEntryRestrictionReason by
                collectLastValue(underTest.deviceEntryRestrictionReason)
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            val requiresPrimaryAuthForSecureLockDevice by
                collectLastValue(
                    kosmos.secureLockDeviceInteractor.requiresPrimaryAuthForSecureLockDevice
                )
            val requiresStrongBiometricAuthForSecureLockDevice by
                collectLastValue(
                    kosmos.secureLockDeviceInteractor.requiresStrongBiometricAuthForSecureLockDevice
                )
            val isSecureLockDeviceEnabled by collectLastValue(underTest.isSecureLockDeviceEnabled)

            // Enroll fingerprint, configure PIN as primary auth method
            kosmos.biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)

            // Mock secure lock device enabled, both StrongAuthFlags set
            kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceEnabled()
            kosmos.biometricSettingsRepository.setAuthenticationFlags(
                AuthenticationFlags(
                    userId = primaryUserId,
                    flag =
                        LockPatternUtils.StrongAuthTracker
                            .PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE or
                            LockPatternUtils.StrongAuthTracker
                                .STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                )
            )
            runCurrent()

            // Assert device lock state, device entry restriction reason, authentication
            // requirements, and secure lock device state updated
            assertThat(authenticationFlags!!.isPrimaryAuthRequiredForSecureLockDevice).isTrue()
            assertThat(deviceEntryRestrictionReason)
                .isEqualTo(DeviceEntryRestrictionReason.SecureLockDevicePrimaryAuth)
            assertThat(requiresPrimaryAuthForSecureLockDevice).isTrue()
            assertThat(requiresStrongBiometricAuthForSecureLockDevice).isFalse()
            assertThat(isSecureLockDeviceEnabled).isTrue()
            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
            assertThat(deviceUnlockStatus?.deviceUnlockSource).isNull()

            // Mock primary auth on bouncer
            authenticationRepository.reportAuthenticationAttempt(AuthenticationResult.SUCCEEDED)

            // Mock primary auth secure lock device flag cleared
            kosmos.fakeSecureLockDeviceRepository.onSuccessfulPrimaryAuth()
            kosmos.biometricSettingsRepository.setAuthenticationFlags(
                AuthenticationFlags(
                    userId = primaryUserId,
                    flag =
                        LockPatternUtils.StrongAuthTracker
                            .STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                )
            )
            runCurrent()

            // Assert device lock state, device entry restriction reason, authentication
            // requirements, and secure lock device state updated to reflect bouncer auth
            assertThat(authenticationFlags!!.isPrimaryAuthRequiredForSecureLockDevice).isFalse()
            assertThat(deviceEntryRestrictionReason)
                .isEqualTo(DeviceEntryRestrictionReason.SecureLockDeviceStrongBiometricOnlyAuth)
            assertThat(requiresPrimaryAuthForSecureLockDevice).isFalse()
            assertThat(requiresStrongBiometricAuthForSecureLockDevice).isTrue()
            assertThat(isSecureLockDeviceEnabled).isTrue()

            // Assert device is still locked, deviceUnlockSource does not update
            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
            assertThat(deviceUnlockStatus?.deviceUnlockSource).isNull()

            // Mock successful strong fingerprint auth
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            // Mock secure lock device auth flags cleared, secure lock device disabled
            kosmos.biometricSettingsRepository.setAuthenticationFlags(
                AuthenticationFlags(userId = primaryUserId, flag = STRONG_AUTH_NOT_REQUIRED)
            )
            kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceDisabled()
            runCurrent()

            // Assert device is now unlocked, deviceUnlockSource updates to fingerprint
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
            assertThat(deviceUnlockStatus?.deviceUnlockSource)
                .isEqualTo(DeviceUnlockSource.Fingerprint)

            // Assert secure lock device disabled after successful strong biometric auth
            assertThat(requiresPrimaryAuthForSecureLockDevice).isFalse()
            assertThat(requiresStrongBiometricAuthForSecureLockDevice).isFalse()
            assertThat(isSecureLockDeviceEnabled).isFalse()
        }

    @Test
    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    fun deviceUnlockStatus_updatesAcrossTwoFactorBouncerUnlock_whenSecureLockDeviceEnabled_face() =
        testScope.runTest {
            val deviceEntryRestrictionReason by
                collectLastValue(underTest.deviceEntryRestrictionReason)
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            val requiresPrimaryAuthForSecureLockDevice by
                collectLastValue(
                    kosmos.secureLockDeviceInteractor.requiresPrimaryAuthForSecureLockDevice
                )
            val requiresStrongBiometricAuthForSecureLockDevice by
                collectLastValue(
                    kosmos.secureLockDeviceInteractor.requiresStrongBiometricAuthForSecureLockDevice
                )
            val isSecureLockDeviceEnabled by collectLastValue(underTest.isSecureLockDeviceEnabled)

            // Enroll face, configure PIN as primary auth method
            kosmos.biometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
            authenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)

            // Mock secure lock device enabled, both StrongAuthFlags set
            kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceEnabled()
            kosmos.biometricSettingsRepository.setAuthenticationFlags(
                AuthenticationFlags(
                    userId = primaryUserId,
                    flag =
                        LockPatternUtils.StrongAuthTracker
                            .PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE or
                            LockPatternUtils.StrongAuthTracker
                                .STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                )
            )
            runCurrent()

            // Assert device is in secure lock device and sets device entry restriction reason,
            // requires bouncer unlock for lockdown
            assertThat(deviceEntryRestrictionReason)
                .isEqualTo(DeviceEntryRestrictionReason.SecureLockDevicePrimaryAuth)
            assertThat(requiresPrimaryAuthForSecureLockDevice).isTrue()
            assertThat(requiresStrongBiometricAuthForSecureLockDevice).isFalse()
            assertThat(isSecureLockDeviceEnabled).isTrue()

            // Assert device is locked, null deviceUnlockSource
            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
            assertThat(deviceUnlockStatus?.deviceUnlockSource).isNull()

            // Mock primary auth on bouncer
            kosmos.fakeSecureLockDeviceRepository.onSuccessfulPrimaryAuth()
            authenticationRepository.reportAuthenticationAttempt(AuthenticationResult.SUCCEEDED)

            // Mock primary auth secure lock device flag cleared
            kosmos.fakeSecureLockDeviceRepository.onSuccessfulPrimaryAuth()
            kosmos.biometricSettingsRepository.setAuthenticationFlags(
                AuthenticationFlags(
                    userId = primaryUserId,
                    flag =
                        LockPatternUtils.StrongAuthTracker
                            .STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                )
            )
            runCurrent()

            // Assert device is in secure lock device and updates device entry restriction reason,
            // no longer requires primary auth on bouncer
            assertThat(deviceEntryRestrictionReason)
                .isEqualTo(DeviceEntryRestrictionReason.SecureLockDeviceStrongBiometricOnlyAuth)
            assertThat(requiresPrimaryAuthForSecureLockDevice).isFalse()
            assertThat(requiresStrongBiometricAuthForSecureLockDevice).isTrue()
            assertThat(isSecureLockDeviceEnabled).isTrue()

            // Assert device is still locked, deviceUnlockSource does not update
            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
            assertThat(deviceUnlockStatus?.deviceUnlockSource).isNull()

            // Mock successful strong face auth
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )
            runCurrent()

            // Assert device is still locked while pending confirmation
            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
            assertThat(deviceUnlockStatus?.deviceUnlockSource).isNull()

            // Face auth confirmed, pending -> confirmed animation played
            kosmos.secureLockDeviceInteractor.onReadyToDismissBiometricAuth()

            // Assert device is now unlocked, deviceUnlockSource updates to face
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
            assertThat(deviceUnlockStatus?.deviceUnlockSource)
                .isEqualTo(DeviceUnlockSource.SecureLockDeviceTwoFactorAuth)
        }

    @Test
    fun deviceUnlockStatus_showBouncer() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            kosmos.powerInteractor.setAwakeForTest()

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_SHOW_BOUNCER,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
            assertThat(deviceUnlockStatus?.deviceUnlockSource).isEqualTo(null)
        }

    @Test
    fun deviceUnlockStatus_null_forInvalidState() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            kosmos.powerInteractor.setAwakeForTest()

            // this is an invalid fingerprint BiometricUnlockState, so it should result in a NOT
            // unlocked state with deviceUnlockSource=null
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_ONLY_WAKE_UNLOCKED,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
            assertThat(deviceUnlockStatus?.deviceUnlockSource).isEqualTo(null)

            // this is an invalid fingerprint BiometricUnlockState, so it should result in a NOT
            // unlocked state with deviceUnlockSource=null
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_NONE_UNLOCKED,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
            assertThat(deviceUnlockStatus?.deviceUnlockSource).isEqualTo(null)
        }

    @Test
    fun deviceUnlockStatus_none() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            kosmos.powerInteractor.setAwakeForTest()

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_NONE,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
            assertThat(deviceUnlockStatus?.deviceUnlockSource).isEqualTo(null)
        }

    @Test
    fun deviceUnlockStatus_noneUnlocked() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            kosmos.powerInteractor.setAwakeForTest()

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_NONE_UNLOCKED,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
            assertThat(deviceUnlockStatus?.deviceUnlockSource)
                .isEqualTo(DeviceUnlockSource.FaceWithoutBypass)
        }

    @Test
    fun deviceUnlockStatus_onlyWake() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            kosmos.powerInteractor.setAwakeForTest()

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_ONLY_WAKE,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
            assertThat(deviceUnlockStatus?.deviceUnlockSource).isEqualTo(null)
        }

    @Test
    fun deviceUnlockStatus_onlyWakeUnlocked() =
        testScope.runTest {
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)
            kosmos.powerInteractor.setAwakeForTest()

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_ONLY_WAKE_UNLOCKED,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
            assertThat(deviceUnlockStatus?.deviceUnlockSource)
                .isEqualTo(DeviceUnlockSource.FaceWithoutBypass)
        }

    @Test
    @EnableFlags(Flags.FLAG_WAKEFULNESS_EVENTS_SHARED_FLOW)
    fun deviceUnlockStatus_isResetToFalse_whenDeviceGoesToSleep_fromVeryBriefLidClose() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = false
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH
            )
            kosmos.powerInteractor.setAwakeForTest()
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_WAKEFULNESS_EVENTS_SHARED_FLOW)
    fun deviceUnlockStatus_isResetToFalse_whenDeviceGoesToSleep_fromVeryBriefFold() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = false
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_DEVICE_FOLD
            )
            kosmos.powerInteractor.setAwakeForTest()
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_WAKEFULNESS_EVENTS_SHARED_FLOW)
    fun deviceUnlockStatus_isResetToFalse_whenDeviceGoesToSleep_fromVeryBriefSleepButton() =
        testScope.runTest {
            setLockAfterScreenTimeout(5000)
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = false
            val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(deviceUnlockStatus?.isUnlocked).isTrue()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_SLEEP_BUTTON
            )
            kosmos.powerInteractor.setAwakeForTest()
            runCurrent()

            assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        }

    private fun TestScope.unlockDevice() {
        val deviceUnlockStatus by collectLastValue(underTest.deviceUnlockStatus)

        kosmos.biometricUnlockInteractor.setBiometricUnlockState(
            unlockStateInt = BiometricUnlockController.MODE_DISMISS,
            biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
        )
        assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
        kosmos.sceneInteractor.changeScene(Scenes.Gone, "reason")
        runCurrent()
    }

    private fun setLockAfterScreenTimeout(timeoutMs: Int) {
        kosmos.fakeSettings.putIntForUser(
            Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
            timeoutMs,
            kosmos.selectedUserInteractor.getSelectedUserId(),
        )
    }

    private fun TestScope.startDreaming() {
        kosmos.fakeKeyguardRepository.setDreaming(true)
        runCurrent()
    }

    private fun TestScope.stopDreaming() {
        kosmos.fakeKeyguardRepository.setDreaming(false)
        runCurrent()
    }

    private fun TestScope.verifyRestrictionReasonsForAuthFlags(
        vararg authFlagToDeviceEntryRestriction: Pair<Int, DeviceEntryRestrictionReason?>
    ) {
        val deviceEntryRestrictionReason by collectLastValue(underTest.deviceEntryRestrictionReason)

        authFlagToDeviceEntryRestriction.forEach { (flag, expectedReason) ->
            kosmos.fakeBiometricSettingsRepository.setAuthenticationFlags(
                AuthenticationFlags(userId = 1, flag = flag)
            )
            runCurrent()

            if (expectedReason == null) {
                assertThat(deviceEntryRestrictionReason).isNull()
            } else {
                assertThat(deviceEntryRestrictionReason).isEqualTo(expectedReason)
            }
            assertThat(underTest.currentDeviceEntryRestrictionReason())
                .isEqualTo(deviceEntryRestrictionReason)
        }
    }

    companion object {
        private const val primaryUserId = 1
        private val primaryUser = UserInfo(primaryUserId, "test user", UserInfo.FLAG_PRIMARY)

        private val secondaryUser = UserInfo(2, "secondary user", 0)
    }
}
