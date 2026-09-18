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

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.alarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.mockedContext
import android.os.PowerManager
import android.os.UserHandle
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.shared.model.LockAfterDelayTimerState
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.fakePendingIntentCreator
import com.android.systemui.util.settings.fakeSettings
import com.android.systemui.util.time.systemClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class LockAfterDelayInteractorTest : SysuiTestCase() {

    private var registeredBroadcastReceiver: BroadcastReceiver? = null
    private var registeredIntentFilter: IntentFilter? = null
    private val alarmManagerCalls = mutableListOf<AlarmManagerCall>()
    private val kosmos =
        testKosmos().apply {
            // Secure
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
            // This is needed for PendingIntent.getBroadcast that the code under test uses.
            whenever(mockedContext.user).thenReturn(mock<UserHandle>())
            doAnswer {
                    alarmManagerCalls.add(
                        AlarmManagerCall(it.getArgument<Long>(1), it.getArgument<PendingIntent>(2))
                    )
                }
                .whenever(alarmManager)
                .setExactAndAllowWhileIdle(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP), any(), any())
        }
    private val testScope = kosmos.testScope
    private val underTest by lazy { kosmos.lockAfterDelayInteractor }
    private val fakeSettings = kosmos.fakeSettings
    private val systemClock = kosmos.systemClock

    private fun captureBroadcastReceiver() {
        val receiverCaptor = argumentCaptor<BroadcastReceiver>()
        val intentFilterCaptor = argumentCaptor<IntentFilter>()
        verify(kosmos.mockedContext)
            .registerReceiver(
                receiverCaptor.capture(),
                intentFilterCaptor.capture(),
                eq("com.android.systemui.permission.SELF"),
                isNull() /* scheduler */,
                eq(Context.RECEIVER_EXPORTED),
            )
        registeredBroadcastReceiver = receiverCaptor.lastValue
        registeredIntentFilter = intentFilterCaptor.lastValue
        assertThat(registeredIntentFilter?.getPriority())
            .isEqualTo(IntentFilter.SYSTEM_HIGH_PRIORITY)
    }

    @Test
    fun test_lockTimerIrrelevant_onSleepButton() =
        testScope.runTest {
            val state by collectValues(underTest.lockAfterDelayState)

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE // Default.
                    )
                )

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_SLEEP_BUTTON
            )
            runCurrent()

            assertThat(state).isEqualTo(listOf(LockAfterDelayTimerState.INACTIVE))
        }

    @Test
    fun test_lockTimerIrrelevant_onLidSwitch() =
        testScope.runTest {
            val state by collectValues(underTest.lockAfterDelayState)

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE // Default.
                    )
                )

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH
            )
            runCurrent()

            assertThat(state).isEqualTo(listOf(LockAfterDelayTimerState.INACTIVE))
        }

    @Test
    fun test_lockTimerIrrelevant_onPowerButton_ifPowerButtonLocksImmediately() =
        testScope.runTest {
            val state by collectValues(underTest.lockAfterDelayState)

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE // Default.
                    )
                )

            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = true
            runCurrent()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON
            )
            runCurrent()

            assertThat(state).isEqualTo(listOf(LockAfterDelayTimerState.INACTIVE))
        }

    @Test
    fun test_lockTimerActiveAndElapses_onPowerButton_ifPowerButtonDoesNotLockImmediately() =
        testScope.runTest {
            val user = kosmos.fakeUserRepository.asMainUser()
            val state by collectValues(underTest.lockAfterDelayState)

            captureBroadcastReceiver()

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE // Default.
                    )
                )

            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = false
            fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                TEST_SCREEN_OFF_TIMEOUT_MS.toInt(),
                user.id,
            )

            runCurrent()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON
            )
            runCurrent()

            verifyActiveLockTimerEntered()
        }

    @Test
    fun test_lockTimerActiveAndElapses_onScreenTimeout() =
        testScope.runTest {
            val user = kosmos.fakeUserRepository.asMainUser()
            val state by collectValues(underTest.lockAfterDelayState)

            captureBroadcastReceiver()

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE // Default.
                    )
                )

            fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                TEST_SCREEN_OFF_TIMEOUT_MS.toInt(),
                user.id,
            )
            runCurrent()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_TIMEOUT
            )
            runCurrent()

            verifyActiveLockTimerEntered()
        }

    @Test
    fun test_lockTimerActiveAndElapses_onDreaming() =
        testScope.runTest {
            kosmos.powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_POWER_BUTTON)
            val user = kosmos.fakeUserRepository.asMainUser()
            val state by collectValues(underTest.lockAfterDelayState)

            captureBroadcastReceiver()

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE // Default.
                    )
                )

            fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                TEST_SCREEN_OFF_TIMEOUT_MS.toInt(),
                user.id,
            )
            runCurrent()

            startDreaming()

            verifyActiveLockTimerEntered()
        }

    @Test
    fun test_dreamingIgnored_whenNotInteractive() =
        testScope.runTest {
            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH
            )
            val user = kosmos.fakeUserRepository.asMainUser()
            val state by collectValues(underTest.lockAfterDelayState)

            captureBroadcastReceiver()

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE // Default.
                    )
                )

            fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                TEST_SCREEN_OFF_TIMEOUT_MS.toInt(),
                user.id,
            )
            runCurrent()

            startDreaming()

            // The device was not awake when dreaming started -> no change.
            assertThat(state).isEqualTo(listOf(LockAfterDelayTimerState.INACTIVE))

            stopDreaming()

            assertThat(state).isEqualTo(listOf(LockAfterDelayTimerState.INACTIVE))
        }

    @Test
    fun test_lockTimerCanceled_onDreamingThenStopDreaming() =
        testScope.runTest {
            kosmos.powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_POWER_BUTTON)
            val user = kosmos.fakeUserRepository.asMainUser()
            val state by collectValues(underTest.lockAfterDelayState)

            captureBroadcastReceiver()

            assertThat(state).isEqualTo(listOf(LockAfterDelayTimerState.INACTIVE))

            fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                TEST_SCREEN_OFF_TIMEOUT_MS.toInt(),
                user.id,
            )
            runCurrent()

            startDreaming()

            assertThat(state)
                .isEqualTo(
                    listOf(LockAfterDelayTimerState.INACTIVE, LockAfterDelayTimerState.RUNNING)
                )

            verifyAlarmManagerCallCount(1)
            val alarmIntent = lastAlarmManagerIntent()

            stopDreaming()

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE,
                        LockAfterDelayTimerState.RUNNING,
                        LockAfterDelayTimerState.INACTIVE,
                    )
                )

            // Simulate alarm coming in.
            val broadcastReceiver = registeredBroadcastReceiver!!
            broadcastReceiver.onReceive(kosmos.mockedContext, alarmIntent)
            runCurrent()

            // As the alarm was canceled (by incrementing the internal sequence number), it doesn't
            // change anything
            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE,
                        LockAfterDelayTimerState.RUNNING,
                        LockAfterDelayTimerState.INACTIVE,
                    )
                )
        }

    @Test
    fun test_lockTimerCanceled_onScreenTimeoutThenAwake() =
        testScope.runTest {
            val user = kosmos.fakeUserRepository.asMainUser()
            val state by collectValues(underTest.lockAfterDelayState)

            captureBroadcastReceiver()

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE // Default.
                    )
                )

            fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                TEST_SCREEN_OFF_TIMEOUT_MS.toInt(),
                user.id,
            )
            runCurrent()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_TIMEOUT
            )
            runCurrent()

            assertThat(state)
                .isEqualTo(
                    listOf(LockAfterDelayTimerState.INACTIVE, LockAfterDelayTimerState.RUNNING)
                )

            verify(kosmos.alarmManager)
                .setExactAndAllowWhileIdle(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP), any(), any())

            val alarmIntent = lastAlarmManagerIntent()

            // Waking up cancels the timer
            kosmos.powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_POWER_BUTTON)
            runCurrent()

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE,
                        LockAfterDelayTimerState.RUNNING,
                        LockAfterDelayTimerState.INACTIVE,
                    )
                )

            // Simulate alarm coming in.
            val broadcastReceiver = registeredBroadcastReceiver!!
            broadcastReceiver.onReceive(kosmos.mockedContext, alarmIntent)
            runCurrent()

            // As the alarm was canceled (by incrementing the internal sequence number), it doesn't
            // change anything
            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE,
                        LockAfterDelayTimerState.RUNNING,
                        LockAfterDelayTimerState.INACTIVE,
                    )
                )
        }

    @Test
    fun test_lockTimerCanceledAndRestarted() =
        testScope.runTest {
            val user = kosmos.fakeUserRepository.asMainUser()
            val state by collectValues(underTest.lockAfterDelayState)

            captureBroadcastReceiver()

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE // Default.
                    )
                )

            kosmos.fakeAuthenticationRepository.fakePowerButtonInstantlyLocks = false
            fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                TEST_SCREEN_OFF_TIMEOUT_MS.toInt(),
                user.id,
            )
            runCurrent()

            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON
            )
            runCurrent()

            assertThat(state)
                .isEqualTo(
                    listOf(LockAfterDelayTimerState.INACTIVE, LockAfterDelayTimerState.RUNNING)
                )

            verifyAlarmManagerCallCount(1)
            val alarmIntent1 = lastAlarmManagerIntent()

            // Waking up cancels the timer
            kosmos.powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_POWER_BUTTON)
            runCurrent()

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE,
                        LockAfterDelayTimerState.RUNNING,
                        LockAfterDelayTimerState.INACTIVE,
                    )
                )

            // Going to sleep again schedules a second timer
            kosmos.powerInteractor.setAsleepForTest(
                sleepReason = PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON
            )
            runCurrent()

            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE,
                        LockAfterDelayTimerState.RUNNING,
                        LockAfterDelayTimerState.INACTIVE,
                        LockAfterDelayTimerState.RUNNING,
                    )
                )

            verifyAlarmManagerCallCount(2)
            val alarmIntent2 = lastAlarmManagerIntent()

            // Simulate alarm 1 alarm coming in - it should have no effect
            val broadcastReceiver = registeredBroadcastReceiver!!
            broadcastReceiver.onReceive(kosmos.mockedContext, alarmIntent1)
            runCurrent()

            // As the alarm was canceled (by incrementing the internal sequence number), it doesn't
            // change anything
            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE,
                        LockAfterDelayTimerState.RUNNING,
                        LockAfterDelayTimerState.INACTIVE,
                        LockAfterDelayTimerState.RUNNING,
                    )
                )

            // Simulate alarm 2 alarm coming in - it should have an effect as it's not canceled
            broadcastReceiver.onReceive(kosmos.mockedContext, alarmIntent2)
            runCurrent()

            // As the alarm was canceled (by incrementing the internal sequence number), it doesn't
            // change anything
            assertThat(state)
                .isEqualTo(
                    listOf(
                        LockAfterDelayTimerState.INACTIVE,
                        LockAfterDelayTimerState.RUNNING,
                        LockAfterDelayTimerState.INACTIVE,
                        LockAfterDelayTimerState.RUNNING,
                        LockAfterDelayTimerState.ELAPSED,
                    )
                )
        }

    @Test
    fun test_lockDelay_noDevicePolicy() =
        testScope.runTest {
            val user = kosmos.fakeUserRepository.asMainUser()
            fakeSettings.putIntForUser(Settings.System.SCREEN_OFF_TIMEOUT, 3000.toInt(), user.id)

            fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                2000.toInt(),
                user.id,
            )

            runCurrent()

            assertThat(underTest.lockDelay()).isEqualTo(2000)
        }

    @Test
    fun test_lockDelay_devicePolicyEnforced_noEffectiveChange() =
        testScope.runTest {
            val user = kosmos.fakeUserRepository.asMainUser()
            kosmos.fakeAuthenticationRepository.maximumTimeToLock = 6000

            fakeSettings.putIntForUser(Settings.System.SCREEN_OFF_TIMEOUT, 3000.toInt(), user.id)

            fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                2000.toInt(),
                user.id,
            )

            runCurrent()

            // Device policy would cap to 6000 - 3000 = 3000 which is > 2000
            assertThat(underTest.lockDelay()).isEqualTo(2000)
        }

    @Test
    fun test_lockDelay_devicePolicyEnforced_longerThanScreenOff() =
        testScope.runTest {
            val user = kosmos.fakeUserRepository.asMainUser()
            kosmos.fakeAuthenticationRepository.maximumTimeToLock = 5000

            fakeSettings.putIntForUser(Settings.System.SCREEN_OFF_TIMEOUT, 4000.toInt(), user.id)

            fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                2000.toInt(),
                user.id,
            )

            runCurrent()

            // Device policy caps to 5000 - 4000 = 1000
            assertThat(underTest.lockDelay()).isEqualTo(1000)
        }

    @Test
    fun test_lockDelay_devicePolicyEnforced_shorterThanScreenOff() =
        testScope.runTest {
            val user = kosmos.fakeUserRepository.asMainUser()
            kosmos.fakeAuthenticationRepository.maximumTimeToLock = 3000

            fakeSettings.putIntForUser(Settings.System.SCREEN_OFF_TIMEOUT, 4000.toInt(), user.id)

            fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                2000.toInt(),
                user.id,
            )

            runCurrent()

            assertThat(underTest.lockDelay()).isEqualTo(0)
        }

    @Test
    fun test_lockDelay_default_withSwipeSecurity() =
        testScope.runTest {
            val user = kosmos.fakeUserRepository.asMainUser()
            // Not secure
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.None
            )

            fakeSettings.putIntForUser(Settings.System.SCREEN_OFF_TIMEOUT, 4000.toInt(), user.id)

            fakeSettings.putIntForUser(
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                2000.toInt(),
                user.id,
            )

            runCurrent()

            // Uses the default
            assertThat(underTest.lockDelay()).isEqualTo(5000)
        }

    /**
     * Verifies that the number of [AlarmManager#setExactAndAllowWhileIdle] calls is [expectedCount]
     * and that the same number of [PendingIntent#getBroadcast] calls have been attempted through
     * [PendingIntentCreator]
     */
    private fun TestScope.verifyAlarmManagerCallCount(expectedCount: Int) {
        // There should be one PendingIntent creation for each AlarmManager call.
        assertThat(alarmManagerCalls).hasSize(expectedCount)
        assertThat(kosmos.fakePendingIntentCreator.getBroadcastCalls).hasSize(expectedCount)
    }

    /**
     * Returns the `triggerAtTime` passed to the last [AlarmManager#setExactAndAllowWhileIdle] call
     */
    private fun TestScope.lastAlarmManagerTriggerAtTime(): Long {
        return alarmManagerCalls.last().triggerAtTime
    }

    /**
     * Verifies that the last created broadcast [PendingIntent] was passed to
     * [AlarmManager#setExactAndAllowWhileIdle]. Returns the `operation` [Intent] that was used to
     * create that [PendingIntent]
     */
    private fun TestScope.lastAlarmManagerIntent(): Intent {
        assertThat(alarmManagerCalls.last().pendingIntent)
            .isSameInstanceAs(
                kosmos.fakePendingIntentCreator.getBroadcastCalls.last().returnedPendingIntent
            )

        return kosmos.fakePendingIntentCreator.getBroadcastCalls.last().intent
    }

    private suspend fun TestScope.verifyActiveLockTimerEntered() {
        val curState by collectLastValue(underTest.lockAfterDelayState)

        assertThat(curState).isEqualTo(LockAfterDelayTimerState.RUNNING)

        val curTime = systemClock.elapsedRealtime()
        val triggerAtTime = lastAlarmManagerTriggerAtTime()
        val alarmIntent = lastAlarmManagerIntent()

        // Check that the intent would match the intentFilter.
        val intentFilter = registeredIntentFilter!!
        val matches =
            intentFilter.match(null /*ContentResolver*/, alarmIntent, false /*resolve*/, TAG) > 0
        assertThat(matches).isTrue()

        assertThat(triggerAtTime).isAtLeast(curTime + TEST_SCREEN_OFF_TIMEOUT_MS - 1000)
        assertThat(triggerAtTime).isAtMost(curTime + TEST_SCREEN_OFF_TIMEOUT_MS + 1000)

        // Simulate alarm coming in.
        val broadcastReceiver = registeredBroadcastReceiver!!
        broadcastReceiver.onReceive(kosmos.mockedContext, alarmIntent)
        runCurrent()

        assertThat(curState).isEqualTo(LockAfterDelayTimerState.ELAPSED)
    }

    private fun TestScope.startDreaming() {
        kosmos.fakeKeyguardRepository.setDreaming(true)
        runCurrent()
    }

    private fun TestScope.stopDreaming() {
        kosmos.fakeKeyguardRepository.setDreaming(false)
        runCurrent()
    }

    /** Represents calls of [AlaramManager#setExactAndAllowWhileIdle] */
    data class AlarmManagerCall(val triggerAtTime: Long, val pendingIntent: PendingIntent)

    companion object {
        private val TAG = "LockAfterDelayInteractorTest"
        private val TEST_SCREEN_OFF_TIMEOUT_MS = 10000
    }
}
