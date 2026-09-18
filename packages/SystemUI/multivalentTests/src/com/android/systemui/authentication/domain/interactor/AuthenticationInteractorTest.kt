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

package com.android.systemui.authentication.domain.interactor

import android.app.admin.DevicePolicyManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.security.Flags.FLAG_LOCKSCREEN_INDICATE_DUPLICATE_GUESSES
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.latencyTracker
import com.android.internal.util.LatencyTracker
import com.android.internal.widget.LockPatternUtils
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.None
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Password
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pattern
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pin
import com.android.systemui.authentication.shared.model.AuthenticationPatternCoordinate
import com.android.systemui.authentication.shared.model.AuthenticationResult
import com.android.systemui.authentication.shared.model.AuthenticationWipeModel
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.FakeUserRepository
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class AuthenticationInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val underTest = kosmos.authenticationInteractor

    private val onAuthenticationResult by kosmos.collectLastValue(underTest.onAuthenticationResult)
    private val failedAuthenticationAttempts by
        kosmos.collectLastValue(underTest.failedAuthenticationAttempts)
    private val lastWarmUpTrigger
        get() = kosmos.fakeAuthenticationRepository.lastWarmUpTrigger

    private val now
        get() = kosmos.testScope.currentTime.milliseconds

    @Before
    fun setUp() {
        kosmos.runCurrent()
    }

    @Test
    fun authenticationMethod() =
        kosmos.runTest {
            val authMethod by collectLastValue(underTest.authenticationMethod)
            runCurrent()
            assertThat(authMethod).isEqualTo(Pin)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)

            assertThat(authMethod).isEqualTo(Password)
        }

    @Test
    fun authenticationMethod_none_whenLockscreenDisabled() =
        kosmos.runTest {
            val authMethod by collectLastValue(underTest.authenticationMethod)
            runCurrent()

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(None)

            assertThat(authMethod).isEqualTo(None)
        }

    @Test
    fun authenticate_withCorrectPin_succeeds() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)

            assertSucceeded(underTest.authenticate(FakeAuthenticationRepository.DEFAULT_PIN))
        }

    @Test
    fun authenticate_withCorrectPin_succeeds_throughEarlyMatch() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            val completableDeferred = kosmos.fakeAuthenticationRepository.deferFullSuccessResult()
            assertThat(onAuthenticationResult).isNull()

            val job =
                testScope.launch {
                    underTest.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)
                }
            runCurrent()
            assertThat(onAuthenticationResult).isTrue()

            assertThat(job.isActive).isTrue()
            completableDeferred.complete(Unit)
            job.join()
        }

    @Test
    fun authenticate_withIncorrectPin_fails() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)

            assertFailed(underTest.authenticate(listOf(9, 8, 7, 6, 5, 4)))
        }

    @Test
    fun authenticate_withEmptyPin_throwsException() {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            kosmos.runTest {
                kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)

                underTest.authenticate(listOf())
            }
        }
    }

    @Test
    fun authenticate_withCorrectMaxLengthPin_succeeds() =
        kosmos.runTest {
            val correctMaxLengthPin = List(16) { 9 }
            kosmos.fakeAuthenticationRepository.apply {
                setAuthenticationMethod(Pin)
                overrideCredential(correctMaxLengthPin)
            }

            assertSucceeded(underTest.authenticate(correctMaxLengthPin))
        }

    @Test
    fun authenticate_withCorrectTooLongPin_fails() =
        kosmos.runTest {
            // Max pin length is 16 digits. To avoid issues with overflows, this test ensures that
            // all pins > 16 decimal digits are rejected.

            // If the policy changes, there is work to do in SysUI.
            assertThat(DevicePolicyManager.MAX_PASSWORD_LENGTH).isLessThan(17)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)

            assertFailed(underTest.authenticate(List(17) { 9 }))
        }

    @Test
    fun authenticate_withCorrectPassword_succeeds() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)

            assertSucceeded(underTest.authenticate("password".toList()))
        }

    @Test
    fun authenticate_withIncorrectPassword_fails() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)

            assertFailed(underTest.authenticate("alohomora".toList()))
            verify(kosmos.latencyTracker, never())
                .onActionStart(LatencyTracker.ACTION_LOCKSCREEN_UNLOCK)
        }

    @Test
    fun authenticate_withCorrectPattern_succeeds() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pattern)

            assertSucceeded(underTest.authenticate(FakeAuthenticationRepository.DEFAULT_PATTERN))
        }

    @Test
    fun authenticate_withIncorrectPattern_fails() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pattern)
            val wrongPattern =
                listOf(
                    AuthenticationPatternCoordinate(x = 2, y = 0),
                    AuthenticationPatternCoordinate(x = 2, y = 1),
                    AuthenticationPatternCoordinate(x = 2, y = 2),
                    AuthenticationPatternCoordinate(x = 1, y = 2),
                )

            assertFailed(underTest.authenticate(wrongPattern))
            verify(kosmos.latencyTracker, never())
                .onActionStart(LatencyTracker.ACTION_LOCKSCREEN_UNLOCK)
        }

    @Test
    fun authenticate_whenCancelled_endsLatencyTracking() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.pauseCredentialChecking()
            val job =
                testScope.launch {
                    underTest.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)
                }
            runCurrent()
            job.cancel()
            runCurrent()

            verify(kosmos.latencyTracker)
                .onActionEnd(LatencyTracker.ACTION_CHECK_CREDENTIAL_UNLOCKED)
        }

    @Test
    fun tryAutoConfirm_withAutoConfirmPinAndShorterPin_returnsNull() =
        kosmos.runTest {
            val isAutoConfirmEnabled by collectLastValue(underTest.isAutoConfirmEnabled)
            kosmos.fakeAuthenticationRepository.apply {
                setAuthenticationMethod(Pin)
                setAutoConfirmFeatureEnabled(true)
            }
            assertThat(isAutoConfirmEnabled).isTrue()
            val defaultPin = FakeAuthenticationRepository.DEFAULT_PIN.toMutableList()

            assertSkipped(
                underTest.authenticate(
                    defaultPin.subList(0, defaultPin.size - 1),
                    tryAutoConfirm = true,
                )
            )
            assertThat(underTest.lockoutEndTime).isNull()
            assertThat(kosmos.fakeAuthenticationRepository.lockoutStartedReportCount).isEqualTo(0)
        }

    @Test
    fun tryAutoConfirm_withAutoConfirmWrongPinCorrectLength_returnsFalse() =
        kosmos.runTest {
            val isAutoConfirmEnabled by collectLastValue(underTest.isAutoConfirmEnabled)
            kosmos.fakeAuthenticationRepository.apply {
                setAuthenticationMethod(Pin)
                setAutoConfirmFeatureEnabled(true)
            }
            assertThat(isAutoConfirmEnabled).isTrue()

            val wrongPin = FakeAuthenticationRepository.DEFAULT_PIN.map { it + 1 }

            assertFailed(underTest.authenticate(wrongPin, tryAutoConfirm = true))
        }

    @Test
    fun tryAutoConfirm_withAutoConfirmLongerPin_returnsFalse() =
        kosmos.runTest {
            val isAutoConfirmEnabled by collectLastValue(underTest.isAutoConfirmEnabled)
            kosmos.fakeAuthenticationRepository.apply {
                setAuthenticationMethod(Pin)
                setAutoConfirmFeatureEnabled(true)
            }
            assertThat(isAutoConfirmEnabled).isTrue()

            val longerPin = FakeAuthenticationRepository.DEFAULT_PIN + listOf(7)

            assertFailed(underTest.authenticate(longerPin, tryAutoConfirm = true))
        }

    @Test
    fun tryAutoConfirm_withAutoConfirmCorrectPin_returnsTrue() =
        kosmos.runTest {
            val isAutoConfirmEnabled by collectLastValue(underTest.isAutoConfirmEnabled)
            kosmos.fakeAuthenticationRepository.apply {
                setAuthenticationMethod(Pin)
                setAutoConfirmFeatureEnabled(true)
            }
            assertThat(isAutoConfirmEnabled).isTrue()

            val correctPin = FakeAuthenticationRepository.DEFAULT_PIN

            assertSucceeded(underTest.authenticate(correctPin, tryAutoConfirm = true))
        }

    @Test
    fun tryAutoConfirm_withAutoConfirmCorrectPinButDuringLockout_returnsNull() =
        kosmos.runTest {
            val isAutoConfirmEnabled by collectLastValue(underTest.isAutoConfirmEnabled)
            val hintedPinLength by collectLastValue(underTest.hintedPinLength)
            kosmos.fakeAuthenticationRepository.apply {
                setAuthenticationMethod(Pin)
                setAutoConfirmFeatureEnabled(true)
                reportLockoutStarted(42.milliseconds)
            }

            val correctPin = FakeAuthenticationRepository.DEFAULT_PIN

            assertSkipped(underTest.authenticate(correctPin, tryAutoConfirm = true))
            assertThat(isAutoConfirmEnabled).isFalse()
            assertThat(hintedPinLength).isNull()
            assertThat(underTest.lockoutEndTime).isNotNull()
        }

    @Test
    fun tryAutoConfirm_withoutAutoConfirmButCorrectPin_returnsNull() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.apply {
                setAuthenticationMethod(Pin)
                setAutoConfirmFeatureEnabled(false)
            }

            val correctPin = FakeAuthenticationRepository.DEFAULT_PIN

            assertSkipped(underTest.authenticate(correctPin, tryAutoConfirm = true))
        }

    @Test
    fun tryAutoConfirm_withoutCorrectPassword_returnsNull() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)

            assertSkipped(underTest.authenticate("password".toList(), tryAutoConfirm = true))
        }

    @Test
    fun isAutoConfirmEnabled_featureDisabled_returnsFalse() =
        kosmos.runTest {
            val isAutoConfirmEnabled by collectLastValue(underTest.isAutoConfirmEnabled)
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(false)

            assertThat(isAutoConfirmEnabled).isFalse()
        }

    @Test
    fun isAutoConfirmEnabled_featureEnabled_returnsTrue() =
        kosmos.runTest {
            val isAutoConfirmEnabled by collectLastValue(underTest.isAutoConfirmEnabled)
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)

            assertThat(isAutoConfirmEnabled).isTrue()
        }

    @Test
    fun isAutoConfirmEnabled_featureEnabledButDisabledByLockout() =
        kosmos.runTest {
            val isAutoConfirmEnabled by collectLastValue(underTest.isAutoConfirmEnabled)
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)

            // The feature is enabled.
            assertThat(isAutoConfirmEnabled).isTrue()

            // Make many wrong attempts to trigger lockout.
            repeat(FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT) {
                assertFailed(underTest.authenticate(listOf(5, 6, it))) // Wrong PIN
            }
            assertThat(underTest.lockoutEndTime).isNotNull()
            assertThat(kosmos.fakeAuthenticationRepository.lockoutStartedReportCount).isEqualTo(1)

            // Lockout disabled auto-confirm.
            assertThat(isAutoConfirmEnabled).isFalse()

            // Move the clock forward one more second, to completely finish the lockout period:
            advanceTimeBy(FakeAuthenticationRepository.LOCKOUT_DURATION + 1.seconds)
            assertThat(underTest.lockoutEndTime).isNull()

            // Auto-confirm is still disabled, because lockout occurred at least once in this
            // session.
            assertThat(isAutoConfirmEnabled).isFalse()

            // Correct PIN and unlocks successfully, resetting the 'session'.
            assertSucceeded(underTest.authenticate(FakeAuthenticationRepository.DEFAULT_PIN))

            // Auto-confirm is re-enabled.
            assertThat(isAutoConfirmEnabled).isTrue()
        }

    @Test
    fun failedAuthenticationAttempts() =
        kosmos.runTest {
            val failedAuthenticationAttempts by
                collectLastValue(underTest.failedAuthenticationAttempts)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            val correctPin = FakeAuthenticationRepository.DEFAULT_PIN

            assertSucceeded(underTest.authenticate(correctPin))
            assertThat(failedAuthenticationAttempts).isEqualTo(0)

            // Make many wrong attempts, leading to lockout:
            repeat(FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT) { index ->
                underTest.authenticate(listOf(5, 6, 7, index)) // Wrong PIN
                assertThat(failedAuthenticationAttempts).isEqualTo(index + 1)
            }

            // Correct PIN, but locked out, so doesn't attempt it:
            assertSkipped(underTest.authenticate(correctPin), assertNoResultEvents = false)
            assertThat(failedAuthenticationAttempts)
                .isEqualTo(FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT)

            // Move the clock forward to finish the lockout period:
            advanceTimeBy(FakeAuthenticationRepository.LOCKOUT_DURATION)
            assertThat(failedAuthenticationAttempts)
                .isEqualTo(FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT)

            // Correct PIN and no longer locked out so unlocks successfully:
            assertSucceeded(underTest.authenticate(correctPin))
            assertThat(failedAuthenticationAttempts).isEqualTo(0)
        }

    @Test
    fun lockoutEndTimestamp() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            val correctPin = FakeAuthenticationRepository.DEFAULT_PIN

            underTest.authenticate(correctPin)
            assertThat(underTest.lockoutEndTime).isNull()

            // Make many wrong attempts, but just shy of what's needed to get locked out:
            repeat(FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT - 1) {
                underTest.authenticate(listOf(5, 6, it)) // Wrong PIN
                assertThat(underTest.lockoutEndTime).isNull()
            }

            // Make one more wrong attempt, leading to lockout:
            underTest.authenticate(
                listOf(5, 6, FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT)
            ) // Wrong PIN

            val expectedLockoutEndTime =
                testScope.currentTime.milliseconds + FakeAuthenticationRepository.LOCKOUT_DURATION
            assertThat(underTest.lockoutEndTime).isEqualTo(expectedLockoutEndTime)
            assertThat(kosmos.fakeAuthenticationRepository.lockoutStartedReportCount).isEqualTo(1)

            // Correct PIN, but locked out, so doesn't attempt it:
            assertSkipped(underTest.authenticate(correctPin), assertNoResultEvents = false)
            assertThat(underTest.lockoutEndTime).isEqualTo(expectedLockoutEndTime)

            // Move the clock forward to ALMOST skip the lockout, leaving one second to go:
            repeat(FakeAuthenticationRepository.LOCKOUT_DURATION_SECONDS - 1) {
                advanceTimeBy(1.seconds)
                assertThat(underTest.lockoutEndTime).isEqualTo(expectedLockoutEndTime)
            }

            // Move the clock forward one more second, to completely finish the lockout period:
            advanceTimeBy(1.seconds)
            assertThat(underTest.lockoutEndTime).isNull()

            // Correct PIN and no longer locked out so unlocks successfully:
            assertSucceeded(underTest.authenticate(correctPin))
            assertThat(underTest.lockoutEndTime).isNull()
        }

    @Test
    fun upcomingWipe() =
        kosmos.runTest {
            val upcomingWipe by collectLastValue(underTest.upcomingWipe)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            val correctPin = FakeAuthenticationRepository.DEFAULT_PIN
            val wrongPin = FakeAuthenticationRepository.DEFAULT_PIN.map { it + 1 }
            kosmos.fakeUserRepository.asMainUser()
            kosmos.fakeAuthenticationRepository.profileWithMinFailedUnlockAttemptsForWipe =
                FakeUserRepository.MAIN_USER_ID

            underTest.authenticate(correctPin)
            assertThat(upcomingWipe).isNull()

            var expectedFailedAttempts = 0
            var remainingFailedAttempts =
                kosmos.fakeAuthenticationRepository.getMaxFailedUnlockAttemptsForWipe()
            assertThat(remainingFailedAttempts)
                .isGreaterThan(LockPatternUtils.FAILED_ATTEMPTS_BEFORE_WIPE_GRACE)

            // Make many wrong attempts, until wipe is triggered:
            repeat(remainingFailedAttempts) { attemptIndex ->
                underTest.authenticate(wrongPin)
                expectedFailedAttempts++
                remainingFailedAttempts--
                if (underTest.lockoutEndTime != null) {
                    // If there's a lockout, wait it out:
                    advanceTimeBy(FakeAuthenticationRepository.LOCKOUT_DURATION)
                }

                if (attemptIndex < LockPatternUtils.FAILED_ATTEMPTS_BEFORE_WIPE_GRACE) {
                    // No risk of wipe.
                    assertThat(upcomingWipe).isNull()
                } else {
                    // Wipe grace period started; Make additional wrong attempts, confirm the
                    // warning is shown each time:
                    assertThat(upcomingWipe)
                        .isEqualTo(
                            AuthenticationWipeModel(
                                wipeTarget = AuthenticationWipeModel.WipeTarget.WholeDevice,
                                failedAttempts = expectedFailedAttempts,
                                remainingAttempts = remainingFailedAttempts,
                            )
                        )
                }
            }

            // Unlock successfully, no more risk of upcoming wipe:
            assertSucceeded(underTest.authenticate(correctPin))
            assertThat(upcomingWipe).isNull()
        }

    @Test
    fun hintedPinLength_withoutAutoConfirm_isNull() =
        kosmos.runTest {
            val hintedPinLength by collectLastValue(underTest.hintedPinLength)
            kosmos.fakeAuthenticationRepository.apply {
                setAuthenticationMethod(Pin)
                setAutoConfirmFeatureEnabled(false)
            }

            assertThat(hintedPinLength).isNull()
        }

    @Test
    fun hintedPinLength_withAutoConfirmPinTooShort_isNull() =
        kosmos.runTest {
            val hintedPinLength by collectLastValue(underTest.hintedPinLength)
            kosmos.fakeAuthenticationRepository.apply {
                setAuthenticationMethod(Pin)
                overrideCredential(
                    buildList {
                        repeat(kosmos.fakeAuthenticationRepository.hintedPinLength - 1) {
                            add(it + 1)
                        }
                    }
                )
                setAutoConfirmFeatureEnabled(true)
            }

            assertThat(hintedPinLength).isNull()
        }

    @Test
    fun hintedPinLength_withAutoConfirmPinAtRightLength_isSameLength() =
        kosmos.runTest {
            val hintedPinLength by collectLastValue(underTest.hintedPinLength)
            kosmos.fakeAuthenticationRepository.apply {
                setAuthenticationMethod(Pin)
                setAutoConfirmFeatureEnabled(true)
                overrideCredential(
                    buildList {
                        repeat(kosmos.fakeAuthenticationRepository.hintedPinLength) { add(it + 1) }
                    }
                )
            }

            assertThat(hintedPinLength)
                .isEqualTo(kosmos.fakeAuthenticationRepository.hintedPinLength)
        }

    @Test
    fun hintedPinLength_withAutoConfirmPinTooLong_isNull() =
        kosmos.runTest {
            val hintedPinLength by collectLastValue(underTest.hintedPinLength)
            kosmos.fakeAuthenticationRepository.apply {
                setAuthenticationMethod(Pin)
                overrideCredential(
                    buildList {
                        repeat(kosmos.fakeAuthenticationRepository.hintedPinLength + 1) {
                            add(it + 1)
                        }
                    }
                )
                setAutoConfirmFeatureEnabled(true)
            }

            assertThat(hintedPinLength).isNull()
        }

    @Test
    fun authenticate_withTooShortPassword() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)

            val tooShortPassword = buildList {
                repeat(kosmos.fakeAuthenticationRepository.minPasswordLength - 1) { time ->
                    add("$time")
                }
            }
            assertSkipped(underTest.authenticate(tooShortPassword))
        }

    @Test
    fun onPrimaryBouncerUserInput_userInputWithinThrottleDuration_doesNotWarmUpAuth() =
        kosmos.runTest {
            val start = now
            underTest.triggerAuthWarmUp()
            runCurrent()
            assertThat(lastWarmUpTrigger).isEqualTo(start)

            advanceTimeBy(1.seconds)
            underTest.triggerAuthWarmUp()
            runCurrent()

            assertThat(lastWarmUpTrigger).isEqualTo(start)
        }

    @Test
    fun onPrimaryBouncerUserInput_userInputAtThrottleDurationEnd_warmsUpAuth() =
        kosmos.runTest {
            val start = now
            underTest.triggerAuthWarmUp()
            runCurrent()
            assertThat(lastWarmUpTrigger).isEqualTo(start)

            advanceTimeBy(5.seconds)
            underTest.triggerAuthWarmUp()
            runCurrent()

            assertThat(lastWarmUpTrigger).isEqualTo(now)
        }

    @Test
    fun onPrimaryBouncerUserInput_userInputAfterThrottleDuration_warmsUpAuth() =
        kosmos.runTest {
            val start = now
            underTest.triggerAuthWarmUp()
            runCurrent()
            assertThat(lastWarmUpTrigger).isEqualTo(start)

            advanceTimeBy(5.seconds + 1.milliseconds)
            underTest.triggerAuthWarmUp()
            runCurrent()

            assertThat(lastWarmUpTrigger).isEqualTo(now)
        }

    @Test
    @DisableFlags(FLAG_LOCKSCREEN_INDICATE_DUPLICATE_GUESSES)
    fun authenticate_withTooShortPassword_doesNotClearDuplicateStateWithFlagOff() =
        kosmos.runTest {
            val isDuplicateAttempt by collectLastValue(underTest.isDuplicateAttempt)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)
            assertFailed(underTest.authenticate(listOf(9, 8, 7, 6, 5, 4)))
            assertFailed(underTest.authenticate(listOf(9, 8, 7, 6, 5, 4)))
            assertThat(isDuplicateAttempt).isTrue()

            val tooShortPassword = buildList {
                repeat(kosmos.fakeAuthenticationRepository.minPasswordLength - 1) { time ->
                    add("$time")
                }
            }
            assertSkipped(underTest.authenticate(tooShortPassword), assertNoResultEvents = false)
            assertThat(isDuplicateAttempt).isTrue()
        }

    @Test
    @EnableFlags(FLAG_LOCKSCREEN_INDICATE_DUPLICATE_GUESSES)
    fun authenticate_withTooShortPassword_clearsDuplicateState() =
        kosmos.runTest {
            val isDuplicateAttempt by collectLastValue(underTest.isDuplicateAttempt)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)
            assertFailed(underTest.authenticate(listOf(9, 8, 7, 6, 5, 4)))
            assertFailed(underTest.authenticate(listOf(9, 8, 7, 6, 5, 4)))
            assertThat(isDuplicateAttempt).isTrue()

            val tooShortPassword = buildList {
                repeat(kosmos.fakeAuthenticationRepository.minPasswordLength - 1) { time ->
                    add("$time")
                }
            }
            assertSkipped(underTest.authenticate(tooShortPassword), assertNoResultEvents = false)
            assertThat(isDuplicateAttempt).isFalse()
        }

    private fun assertSucceeded(authenticationResult: AuthenticationResult) {
        assertThat(authenticationResult).isEqualTo(AuthenticationResult.SUCCEEDED)
        assertThat(onAuthenticationResult).isTrue()
        assertThat(underTest.lockoutEndTime).isNull()
        assertThat(kosmos.fakeAuthenticationRepository.lockoutStartedReportCount).isEqualTo(0)
        assertThat(failedAuthenticationAttempts).isEqualTo(0)

        inOrder(kosmos.latencyTracker) {
            with(kosmos.latencyTracker) {
                verify(this).onActionStart(LatencyTracker.ACTION_CHECK_CREDENTIAL)
                verify(this).onActionStart(LatencyTracker.ACTION_CHECK_CREDENTIAL_UNLOCKED)
                verify(this).onActionEnd(LatencyTracker.ACTION_CHECK_CREDENTIAL)
                verify(this).onActionEnd(LatencyTracker.ACTION_CHECK_CREDENTIAL_UNLOCKED)
                verify(this).onActionStart(LatencyTracker.ACTION_LOCKSCREEN_UNLOCK)
            }
        }
    }

    private fun assertFailed(authenticationResult: AuthenticationResult) {
        assertThat(authenticationResult).isEqualTo(AuthenticationResult.FAILED)
        assertThat(onAuthenticationResult).isFalse()

        inOrder(kosmos.latencyTracker) {
            with(kosmos.latencyTracker) {
                verify(this).onActionStart(LatencyTracker.ACTION_CHECK_CREDENTIAL)
                verify(this).onActionStart(LatencyTracker.ACTION_CHECK_CREDENTIAL_UNLOCKED)
                verify(this, never()).onActionEnd(LatencyTracker.ACTION_CHECK_CREDENTIAL)
                verify(this, never()).onActionEnd(LatencyTracker.ACTION_CHECK_CREDENTIAL_UNLOCKED)
                verify(this, never()).onActionStart(LatencyTracker.ACTION_LOCKSCREEN_UNLOCK)
            }
        }
    }

    private fun assertSkipped(
        authenticationResult: AuthenticationResult,
        assertNoResultEvents: Boolean = true,
    ) {
        assertThat(authenticationResult).isEqualTo(AuthenticationResult.SKIPPED)
        if (assertNoResultEvents) {
            assertThat(onAuthenticationResult).isNull()
        } else {
            assertThat(onAuthenticationResult).isNotNull()
        }
    }
}
