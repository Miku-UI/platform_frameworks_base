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

package com.android.systemui.keyguard.domain.interactor

import android.app.admin.devicePolicyManager
import android.content.mockedContext
import android.os.PowerManager
import android.os.UserHandle
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.internal.widget.lockPatternUtils
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectValues
import com.android.systemui.deviceentry.data.repository.deviceEntryRepository
import com.android.systemui.deviceentry.shared.model.DeviceUnlockSource
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.LockAfterDelayTimerState
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.Scenes.Gone
import com.android.systemui.statusbar.policy.data.repository.fakeDeviceProvisioningRepository
import com.android.systemui.statusbar.policy.domain.interactor.deviceProvisioningInteractor
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.FakeUserRepository
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class KeyguardWakeDirectlyToGoneInteractorTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().apply {
            deviceEntryRepository.deviceUnlockStatus.value =
                DeviceUnlockStatus(
                    isUnlocked = true,
                    deviceUnlockSource = DeviceUnlockSource.BouncerInput,
                )
            whenever(mockedContext.user).thenReturn(mock<UserHandle>())
            whenever(lockPatternUtils.isSecure(anyInt())).thenReturn(true)
        }

    private val underTest by lazy { kosmos.keyguardWakeDirectlyToGoneInteractor }
    private val repository = kosmos.fakeKeyguardRepository
    private val transitionRepository = kosmos.fakeKeyguardTransitionRepository

    @Test
    fun canWakeDirectlyToGone_keyguardServiceEnabledThenDisabled() =
        kosmos.runTest {
            val canWake by collectValues(underTest.canWakeDirectlyToGone)

            assertEquals(
                listOf(
                    false // Defaults to false.
                ),
                canWake,
            )

            repository.setKeyguardEnabled(false)
            runCurrent()

            assertEquals(
                listOf(
                    false, // Default to false.
                    true, // True now that keyguard service is disabled
                ),
                canWake,
            )

            repository.setKeyguardEnabled(true)
            runCurrent()

            assertEquals(listOf(false, true, false), canWake)
        }

    @Test
    fun canWakeDirectlyToGone_lockscreenDisabledThenEnabled_lockNowEvent() =
        kosmos.runTest {
            val canWake by collectValues(underTest.canWakeDirectlyToGone)

            assertEquals(
                listOf(
                    false // Defaults to false.
                ),
                canWake,
            )

            whenever(lockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(true)
            runCurrent()

            assertEquals(
                listOf(
                    // Still false - isLockScreenDisabled only causes canWakeDirectlyToGone to
                    // update on the next wakefulness or lockNow event.
                    false
                ),
                canWake,
            )

            kosmos.keyguardServiceShowLockscreenInteractor.onKeyguardServiceDoKeyguardTimeout()
            runCurrent()

            assertEquals(
                listOf(
                    false,
                    // True when lockNow() called after setting isLockScreenDisabled=true
                    true,
                ),
                canWake,
            )

            whenever(lockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(false)
            runCurrent()

            assertEquals(
                listOf(
                    false,
                    // Still true since no lockNow() calls made.
                    true,
                ),
                canWake,
            )

            kosmos.keyguardServiceShowLockscreenInteractor.onKeyguardServiceDoKeyguardTimeout()
            runCurrent()

            assertEquals(
                listOf(
                    false,
                    true,
                    // False again after the lockNow() call.
                    false,
                ),
                canWake,
            )
        }

    @Test
    fun canWakeDirectlyToGone_wakeAndUnlock() =
        kosmos.runTest {
            val canWake by collectValues(underTest.canWakeDirectlyToGone)

            assertEquals(
                listOf(
                    false // Defaults to false.
                ),
                canWake,
            )

            repository.setBiometricUnlockState(BiometricUnlockMode.WAKE_AND_DISMISS)
            runCurrent()

            assertEquals(listOf(false, true), canWake)

            repository.setBiometricUnlockState(BiometricUnlockMode.NONE)
            runCurrent()

            assertEquals(listOf(false, true, false), canWake)
        }

    @Test
    fun canWakeDirectlyToGone_andSetsAlarm_ifPowerButtonDoesNotLockImmediately() =
        kosmos.runTest {
            val canWake by collectValues(underTest.canWakeDirectlyToGone)

            assertEquals(
                listOf(
                    false // Defaults to false.
                ),
                canWake,
            )

            repository.setCanIgnoreAuthAndReturnToGone(true)
            runCurrent()

            assertEquals(listOf(false, true), canWake)

            repository.setCanIgnoreAuthAndReturnToGone(false)
            runCurrent()

            assertEquals(listOf(false, true, false), canWake)
        }

    @Test
    fun setsCanIgnoreAuth_whenTimingOut() =
        kosmos.runTest {
            val userSettingDelay = 12345
            val canWake by collectValues(underTest.canWakeDirectlyToGone)
            kosmos.setSceneTransition(ObservableTransitionState.Idle(Scenes.Lockscreen))

            assertEquals(
                listOf(
                    false // Defaults to false.
                ),
                canWake,
            )

            whenever(kosmos.devicePolicyManager.getMaximumTimeToLock(eq(null), anyInt()))
                .thenReturn(-1)
            kosmos.fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                userSettingDelay,
                FakeUserRepository.DEFAULT_SELECTED_USER,
            )

            kosmos.sceneInteractor.changeScene(Gone, loggingReason = "for test")
            runCurrent()
            transitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GONE,
                testScope,
            )
            runCurrent()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_TIMEOUT
            )
            repository.lockAfterDelayState.value = LockAfterDelayTimerState.RUNNING
            runCurrent()

            assertEquals(listOf(false, true), canWake)
        }

    @Test
    fun lockAfterScreenTimeoutTimerElapses() =
        kosmos.runTest {
            val canWake by collectValues(underTest.canWakeDirectlyToGone)
            kosmos.sceneInteractor.changeScene(
                toScene = Scenes.Lockscreen,
                loggingReason = "for test",
            )

            assertEquals(
                listOf(
                    false // Defaults to false.
                ),
                canWake,
            )

            whenever(kosmos.devicePolicyManager.getMaximumTimeToLock(eq(null), anyInt()))
                .thenReturn(-1)
            kosmos.fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                500,
                FakeUserRepository.DEFAULT_SELECTED_USER,
            )

            kosmos.sceneInteractor.changeScene(toScene = Scenes.Gone, loggingReason = "for test")
            runCurrent()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_TIMEOUT
            )
            kosmos.sceneInteractor.changeScene(
                toScene = Scenes.Lockscreen,
                loggingReason = "for test",
            )
            repository.lockAfterDelayState.value = LockAfterDelayTimerState.RUNNING
            runCurrent()
            transitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                testScope = testScope,
            )
            runCurrent()

            assertEquals(
                listOf(
                    false,
                    // Timed out, so we can ignore auth/return to GONE.
                    true,
                ),
                canWake,
            )

            kosmos.powerInteractor.setAwakeForTest()
            transitionRepository.sendTransitionSteps(
                from = KeyguardState.AOD,
                to = KeyguardState.UNDEFINED,
                testScope = testScope,
            )
            repository.lockAfterDelayState.value = LockAfterDelayTimerState.INACTIVE
            runCurrent()
            kosmos.sceneInteractor.changeScene(toScene = Scenes.Gone, loggingReason = "for test")
            runCurrent()

            assertEquals(
                listOf(
                    false,
                    true,
                    // Should be canceled by the wakeup, but there would still be an
                    // alarm in flight that should be canceled.
                    false,
                ),
                canWake,
            )

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_TIMEOUT
            )
            repository.lockAfterDelayState.value = LockAfterDelayTimerState.RUNNING
            runCurrent()

            assertEquals(
                listOf(
                    false,
                    true,
                    false,
                    // Back to sleep.
                    true,
                ),
                canWake,
            )

            // The "lock after screen timeout" timer expires
            repository.lockAfterDelayState.value = LockAfterDelayTimerState.ELAPSED
            runCurrent()

            // Not possible to go directly back to Gone anymore
            assertEquals(listOf(false, true, false, true, false), canWake)
        }

    @Test
    fun canWakeDirectlyToGone_deviceNotProvisioned() =
        kosmos.runTest {
            val isDeviceProvisioned by
                collectLastValue(deviceProvisioningInteractor.isDeviceProvisioned)
            val canWakeDirectlyToGone by collectLastValue(underTest.canWakeDirectlyToGone)
            assertThat(isDeviceProvisioned).isTrue()
            assertThat(canWakeDirectlyToGone).isFalse()

            fakeDeviceProvisioningRepository.setDeviceProvisioned(false)
            assertThat(isDeviceProvisioned).isFalse()
            assertThat(canWakeDirectlyToGone).isTrue()

            fakeDeviceProvisioningRepository.setDeviceProvisioned(true)
            assertThat(isDeviceProvisioned).isTrue()
            assertThat(canWakeDirectlyToGone).isFalse()
        }
}
