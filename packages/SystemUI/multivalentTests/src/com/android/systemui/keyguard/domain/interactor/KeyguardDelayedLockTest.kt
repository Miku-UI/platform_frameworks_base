/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.content.mockedContext
import android.os.PowerManager
import android.os.UserHandle
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.widget.lockPatternUtils
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.deviceentry.data.repository.deviceEntryRepository
import com.android.systemui.deviceentry.domain.interactor.deviceUnlockedInteractor
import com.android.systemui.deviceentry.shared.model.DeviceUnlockSource
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.keyguardRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Integration test for delaying a keyguard lock state. Specifically when:
 * - Power button immediately locks setting is DISABLED
 * - Lock device on screen timeout is non-zero
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class KeyguardDelayedLockTest : SysuiTestCase() {
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

    private val testScope = kosmos.testScope
    private lateinit var lockAfterDelayInteractor: LockAfterDelayInteractor
    private val sceneContainerStartable by lazy { kosmos.sceneContainerStartable }

    @Before
    fun setup() {
        // initialize to start monitorWakefulnessAndDreams which handles starting a delayed
        // lock on keyguard when the devices goes to sleep
        this.lockAfterDelayInteractor = kosmos.lockAfterDelayInteractor

        // automatically trigger scene changes based on asleep/awake states
        sceneContainerStartable.start()
    }

    @Test
    fun powerButtonImmediatelyLocks_wakeUpToLockscreen() =
        testScope.runTest {
            val currentScene by collectLastValue(kosmos.sceneInteractor.currentScene)
            val deviceUnlockStatus by
                collectLastValue(kosmos.deviceUnlockedInteractor.deviceUnlockStatus)

            // GIVEN Power button instantly locks
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = true
            runCurrent()

            // WHEN device goes to sleep from power button
            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON
            )
            runCurrent()

            // THEN device is asleep on lockscreen, locked
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(deviceUnlockStatus!!.isUnlocked).isEqualTo(false)

            // WHEN device wakes up
            kosmos.powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_POWER_BUTTON)
            runCurrent()

            // THEN device wakes to Lockscreen (not Gone)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(deviceUnlockStatus!!.isUnlocked).isEqualTo(false)
        }

    @Test
    fun powerButtonDoesNotImmediatelyLock_wakeUpToGone() =
        testScope.runTest {
            val currentScene by collectLastValue(kosmos.sceneInteractor.currentScene)
            val deviceUnlockStatus by
                collectLastValue(kosmos.deviceUnlockedInteractor.deviceUnlockStatus)

            // GIVEN Power button doesn't instantly lock & lock after timeout > 0
            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = false
            kosmos.fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                TEST_SCREEN_OFF_TIMEOUT_MS,
                kosmos.fakeUserRepository.asMainUser().id,
            )
            runCurrent()

            // WHEN device goes to sleep from power button
            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON
            )
            runCurrent()

            // THEN device is asleep on lockscreen, kept unlocked
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(deviceUnlockStatus!!.isUnlocked).isEqualTo(true)

            // WHEN device wakes up
            kosmos.powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_POWER_BUTTON)
            runCurrent()

            // THEN device wakes directly to Gone, still unlocked
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(deviceUnlockStatus!!.isUnlocked).isEqualTo(true)
        }

    @Test
    fun screenTimeout_wakeUpToGone() =
        testScope.runTest {
            val currentScene by collectLastValue(kosmos.sceneInteractor.currentScene)
            val deviceUnlockStatus by
                collectLastValue(kosmos.deviceUnlockedInteractor.deviceUnlockStatus)

            // GIVEN lock after timeout > 0
            kosmos.fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                TEST_SCREEN_OFF_TIMEOUT_MS,
                kosmos.fakeUserRepository.asMainUser().id,
            )
            runCurrent()

            // WHEN device goes to sleep from timeout
            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_TIMEOUT
            )
            runCurrent()

            // THEN device is asleep on lockscreen, kept unlocked
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(deviceUnlockStatus!!.isUnlocked).isEqualTo(true)

            // WHEN device wakes up
            kosmos.powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_POWER_BUTTON)
            runCurrent()

            // THEN device wakes directly to Gone, still unlocked
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(deviceUnlockStatus!!.isUnlocked).isEqualTo(true)
        }

    @Test
    fun screenTimeout_timeoutElapsed_wakeUpToLockscreen() =
        testScope.runTest {
            val currentScene by collectLastValue(kosmos.sceneInteractor.currentScene)
            val deviceUnlockStatus by
                collectLastValue(kosmos.deviceUnlockedInteractor.deviceUnlockStatus)

            // GIVEN lock after timeout > 0
            kosmos.fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                TEST_SCREEN_OFF_TIMEOUT_MS,
                kosmos.fakeUserRepository.asMainUser().id,
            )
            runCurrent()

            // WHEN device goes to sleep from timeout
            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_TIMEOUT
            )
            runCurrent()

            // THEN device is asleep on lockscreen, kept unlocked
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(deviceUnlockStatus!!.isUnlocked).isEqualTo(true)

            // WHEN screen timeout elapses
            lockAfterDelayInteractor.timeoutElapsedForTesting()
            runCurrent()

            // WHEN device wakes up
            kosmos.powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_POWER_BUTTON)
            runCurrent()

            // THEN device wakes Lockscreen, locked
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(deviceUnlockStatus!!.isUnlocked).isEqualTo(false)
        }

    @Test
    fun screenTimeoutWhenKeyguardDisabled_thenKeyguardReenabled_showLockscreen() =
        testScope.runTest {
            val currentScene by collectLastValue(kosmos.sceneInteractor.currentScene)
            val deviceUnlockStatus by
                collectLastValue(kosmos.deviceUnlockedInteractor.deviceUnlockStatus)

            // GIVEN lock after timeout > 0 & keyguard is disabled
            kosmos.fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                TEST_SCREEN_OFF_TIMEOUT_MS,
                kosmos.fakeUserRepository.asMainUser().id,
            )
            kosmos.keyguardRepository.setKeyguardEnabled(false)
            runCurrent()

            // WHEN device goes to sleep from timeout
            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_TIMEOUT
            )
            runCurrent()

            // THEN device is asleep in Lockscreen, unlocked
            // lockAfterScreenTimeoutState is inactive because keyguard is disabled
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(deviceUnlockStatus!!.isUnlocked).isEqualTo(true)

            // WHEN keyguard is re-enabled
            kosmos.keyguardRepository.setKeyguardEnabled(true)
            runCurrent()

            // THEN device is still on Lockscreen, but now locked
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(deviceUnlockStatus!!.isUnlocked).isEqualTo(false)

            // WHEN device wakes up
            kosmos.powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_POWER_BUTTON)
            runCurrent()

            // THEN device is still on Lockscreen (doesn't wake to Gone)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(deviceUnlockStatus!!.isUnlocked).isEqualTo(false)
        }

    companion object {
        private const val TEST_SCREEN_OFF_TIMEOUT_MS = 10000
    }
}
