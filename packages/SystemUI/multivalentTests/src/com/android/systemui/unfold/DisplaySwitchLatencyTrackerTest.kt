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

package com.android.systemui.unfold

import android.content.Context
import android.content.res.Resources
import android.hardware.devicestate.DeviceStateManager
import android.os.PowerManager.GO_TO_SLEEP_REASON_DEVICE_FOLD
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.internal.util.LatencyTracker
import com.android.internal.util.LatencyTracker.ACTION_SWITCH_DISPLAY_FOLD
import com.android.internal.util.LatencyTracker.ACTION_SWITCH_DISPLAY_UNFOLD
import com.android.systemui.SysuiTestCase
import com.android.systemui.deviceStateManager
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.FOLDED
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.HALF_FOLDED
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.UNFOLDED
import com.android.systemui.foldedDeviceStateList
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.PowerInteractorFactory
import com.android.systemui.shared.system.SysUiStatsLog
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.testKosmos
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.Companion.FOLDABLE_DEVICE_STATE_CLOSED
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.Companion.FOLDABLE_DEVICE_STATE_HALF_OPEN
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.DisplaySwitchLatencyEvent
import com.android.systemui.unfold.data.repository.ScreenTimeoutPolicyRepository
import com.android.systemui.unfold.domain.interactor.DisplaySwitchState
import com.android.systemui.unfold.domain.interactor.DisplaySwitchState.Corrupted
import com.android.systemui.unfold.domain.interactor.DisplaySwitchState.Idle
import com.android.systemui.unfold.domain.interactor.DisplaySwitchState.Switching
import com.android.systemui.unfold.domain.interactor.fakeDisplaySwitchTrackingInteractor
import com.android.systemui.unfoldedDeviceState
import com.android.systemui.util.mockito.capture
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.times

@RunWith(AndroidJUnit4::class)
@SmallTest
class DisplaySwitchLatencyTrackerTest : SysuiTestCase() {
    private lateinit var displaySwitchLatencyTracker: DisplaySwitchLatencyTracker
    @Captor private lateinit var loggerArgumentCaptor: ArgumentCaptor<DisplaySwitchLatencyEvent>

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope: TestScope = kosmos.testScope

    private val resources = mock<Resources>()
    private val powerInteractor = PowerInteractorFactory.create().powerInteractor
    private val keyguardInteractor = mock<KeyguardInteractor>()
    private val displaySwitchLatencyLogger = mock<DisplaySwitchLatencyLogger>()
    private val screenTimeoutPolicyRepository = mock<ScreenTimeoutPolicyRepository>()
    private val latencyTracker = mock<LatencyTracker>()
    private val activeNotificationsInteractor = mock<ActiveNotificationsInteractor>()

    private val isAodAvailable = MutableStateFlow(false)
    private val screenTimeoutActive = MutableStateFlow(true)
    private val systemClock = FakeSystemClock()

    private val displaySwitchTrackingInteractor = kosmos.fakeDisplaySwitchTrackingInteractor

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        val mockContext = mock<Context>()
        val deviceStateManager = kosmos.deviceStateManager
        setupFoldableStates(mockContext, deviceStateManager)

        whenever(keyguardInteractor.isAodAvailable).thenReturn(isAodAvailable)
        whenever(screenTimeoutPolicyRepository.screenTimeoutActive).thenReturn(screenTimeoutActive)
        powerInteractor.setAwakeForTest()

        setDisplaySwitchState(Idle(newDeviceState = FOLDED))

        displaySwitchLatencyTracker =
            DisplaySwitchLatencyTracker(
                mockContext,
                powerInteractor,
                screenTimeoutPolicyRepository,
                keyguardInteractor,
                activeNotificationsInteractor,
                testScope.backgroundScope,
                displaySwitchLatencyLogger,
                systemClock,
                deviceStateManager,
                displaySwitchTrackingInteractor,
                latencyTracker,
            )
        displaySwitchLatencyTracker.start()
    }

    private fun setupFoldableStates(
        mockContext: Context,
        mockDeviceStateManager: DeviceStateManager,
    ) {
        whenever(mockContext.resources).thenReturn(resources)
        whenever(mockContext.getSystemService(DeviceStateManager::class.java))
            .thenReturn(mockDeviceStateManager)
        val closedDeviceState = kosmos.foldedDeviceStateList.first()
        val openDeviceState = kosmos.unfoldedDeviceState
        whenever(mockDeviceStateManager.supportedDeviceStates)
            .thenReturn(listOf(closedDeviceState, openDeviceState))
        val nonEmptyClosedDeviceStatesArray = IntArray(2) { closedDeviceState.identifier }
        whenever(resources.getIntArray(R.array.config_foldedDeviceStates))
            .thenReturn(nonEmptyClosedDeviceStatesArray)
    }

    @Test
    fun logsLatencyUntilDisplaySwitchFinished() {
        testScope.runTest {
            setDisplaySwitchState(Switching(HALF_FOLDED))
            systemClock.advanceTime(250)
            setDisplaySwitchState(Idle(HALF_FOLDED))
            systemClock.advanceTime(50)

            val expectedLoggedEvent =
                successfulEvent(
                    latencyMs = 250,
                    fromFoldableDeviceState = FOLDABLE_DEVICE_STATE_CLOSED,
                    toFoldableDeviceState = FOLDABLE_DEVICE_STATE_HALF_OPEN,
                )
            assertThat(capturedLogEvent()).isEqualTo(expectedLoggedEvent)
        }
    }

    @Test
    fun foldWhileStayingAwake_logsLatency() {
        testScope.runTest {
            setDisplaySwitchState(Idle(HALF_FOLDED))

            setDisplaySwitchState(Switching(HALF_FOLDED))
            systemClock.advanceTime(200)
            setDisplaySwitchState(Idle(FOLDED))

            val expectedLoggedEvent =
                successfulEvent(
                    latencyMs = 200,
                    fromFoldableDeviceState = FOLDABLE_DEVICE_STATE_HALF_OPEN,
                    toFoldableDeviceState = FOLDABLE_DEVICE_STATE_CLOSED,
                )
            assertThat(capturedLogEvent()).isEqualTo(expectedLoggedEvent)
        }
    }

    @Test
    fun foldToAod_capturesToStateAsAod() {
        testScope.runTest {
            isAodAvailable.emit(true)
            setDisplaySwitchState(Idle(HALF_FOLDED))

            setDisplaySwitchState(Switching(HALF_FOLDED))
            powerInteractor.setAsleepForTest(sleepReason = GO_TO_SLEEP_REASON_DEVICE_FOLD)
            setDisplaySwitchState(Idle(FOLDED))

            assertThat(capturedLogEvent().toState)
                .isEqualTo(SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TO_STATE__AOD)
        }
    }

    @Test
    fun foldToScreenOff_capturesToStateAsScreenOff() {
        testScope.runTest {
            setDisplaySwitchState(Idle(HALF_FOLDED))
            isAodAvailable.emit(false)

            setDisplaySwitchState(Switching(HALF_FOLDED))
            powerInteractor.setAsleepForTest(sleepReason = GO_TO_SLEEP_REASON_DEVICE_FOLD)
            setDisplaySwitchState(Idle(FOLDED))

            assertThat(capturedLogEvent().toState)
                .isEqualTo(SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TO_STATE__SCREEN_OFF)
        }
    }

    @Test
    fun foldingWhileScreenIsAlreadyOff_capturesToStateAsScreenOff() {
        testScope.runTest {
            setDisplaySwitchState(Idle(HALF_FOLDED))
            powerInteractor.setAsleepForTest()

            setDisplaySwitchState(Switching(HALF_FOLDED))
            setDisplaySwitchState(Idle(FOLDED))

            assertThat(capturedLogEvent().toState)
                .isEqualTo(SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TO_STATE__SCREEN_OFF)
        }
    }

    @Test
    fun foldingWhileScreenIsAlreadyOff_capturesFromStateAsScreenOff() {
        testScope.runTest {
            setDisplaySwitchState(Idle(HALF_FOLDED))
            powerInteractor.setAsleepForTest()

            setDisplaySwitchState(Switching(HALF_FOLDED))
            setDisplaySwitchState(Idle(FOLDED))

            assertThat(capturedLogEvent().fromState)
                .isEqualTo(SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_STATE__SCREEN_OFF)
        }
    }

    @Test
    fun foldingWhileAod_capturesFromStateAsAod() {
        testScope.runTest {
            setDisplaySwitchState(Idle(HALF_FOLDED))
            powerInteractor.setAsleepForTest()
            isAodAvailable.value = true

            setDisplaySwitchState(Switching(HALF_FOLDED))
            setDisplaySwitchState(Idle(FOLDED))

            assertThat(capturedLogEvent().fromState)
                .isEqualTo(SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_STATE__AOD)
        }
    }

    @Test
    fun foldToScreenOff_foldTrackingNotSent() {
        testScope.runTest {
            setDisplaySwitchState(Idle(HALF_FOLDED))
            isAodAvailable.emit(false)

            setDisplaySwitchState(Switching(HALF_FOLDED))

            powerInteractor.setAsleepForTest(sleepReason = GO_TO_SLEEP_REASON_DEVICE_FOLD)

            setDisplaySwitchState(Idle(FOLDED))

            verify(latencyTracker).onActionCancel(ACTION_SWITCH_DISPLAY_FOLD)
            verify(latencyTracker, never()).onActionEnd(ACTION_SWITCH_DISPLAY_FOLD)
        }
    }

    @Test
    fun unfoldingDevice_startsUnfoldLatencyTracking() {
        testScope.runTest {
            setDisplaySwitchState(Switching(HALF_FOLDED))

            verify(latencyTracker).onActionStart(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun foldingDevice_startsFoldLatencyTracking() {
        testScope.runTest {
            setDisplaySwitchState(Idle(UNFOLDED))

            setDisplaySwitchState(Switching(FOLDED))

            verify(latencyTracker).onActionStart(ACTION_SWITCH_DISPLAY_FOLD)
        }
    }

    @Test
    fun foldingDevice_doesntTrackUnfoldLatency() {
        testScope.runTest {
            setDisplaySwitchState(Idle(UNFOLDED))

            setDisplaySwitchState(Switching(FOLDED))

            verify(latencyTracker, never()).onActionStart(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun unfoldingDevice_doesntTrackFoldLatency() {
        testScope.runTest {
            setDisplaySwitchState(Switching(HALF_FOLDED))

            verify(latencyTracker, never()).onActionStart(ACTION_SWITCH_DISPLAY_FOLD)
        }
    }

    @Test
    fun unfoldingDevice_endsUnfoldLatencyWhenSwitchFinished() {
        testScope.runTest {
            setDisplaySwitchState(Switching(HALF_FOLDED))

            setDisplaySwitchState(Idle(UNFOLDED))

            verify(latencyTracker).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun foldingDevice_endsFoldLatencyWhenSwitchFinished() {
        testScope.runTest {
            setDisplaySwitchState(Idle(HALF_FOLDED))

            setDisplaySwitchState(Switching(FOLDED))
            setDisplaySwitchState(Idle(FOLDED))

            verify(latencyTracker).onActionEnd(ACTION_SWITCH_DISPLAY_FOLD)
        }
    }

    @Test
    fun unfoldingDevice_doesntEndUnfoldLatencyTrackingWhenSwitchNotFinished() {
        testScope.runTest {
            setDisplaySwitchState(Switching(HALF_FOLDED))

            verify(latencyTracker, never()).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun foldingDevice_doesntEndFoldLatencyTrackingWhenSwitchNotFinished() {
        testScope.runTest {
            setDisplaySwitchState(Idle(UNFOLDED))

            setDisplaySwitchState(Switching(FOLDED))

            verify(latencyTracker, never()).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun displaySwitchInterrupted_newDeviceState_unfoldTrackingNotSent() {
        testScope.runTest {
            setDisplaySwitchState(Switching(HALF_FOLDED))

            setDisplaySwitchState(Corrupted(HALF_FOLDED))
            setDisplaySwitchState(Idle(UNFOLDED))

            verify(latencyTracker).onActionCancel(ACTION_SWITCH_DISPLAY_UNFOLD)
            verify(latencyTracker, never()).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun displaySwitchInterrupted_newDeviceState_foldTrackingNotSent() {
        testScope.runTest {
            setDisplaySwitchState(Switching(HALF_FOLDED))

            setDisplaySwitchState(Corrupted(HALF_FOLDED))
            setDisplaySwitchState(Idle(UNFOLDED))

            verify(latencyTracker).onActionCancel(ACTION_SWITCH_DISPLAY_FOLD)
            verify(latencyTracker, never()).onActionEnd(ACTION_SWITCH_DISPLAY_FOLD)
        }
    }

    @Test
    fun interruptedDisplaySwitchFinished_coolDownPassed_unfoldTrackingWorksAsUsual() {
        testScope.runTest {
            setDisplaySwitchState(Switching(HALF_FOLDED))
            setDisplaySwitchState(Corrupted(HALF_FOLDED))
            setDisplaySwitchState(Idle(FOLDED))

            setDisplaySwitchState(Switching(HALF_FOLDED))
            setDisplaySwitchState(Idle(UNFOLDED))

            verify(latencyTracker, times(2)).onActionStart(ACTION_SWITCH_DISPLAY_UNFOLD)
            verify(latencyTracker).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun interruptedDisplaySwitchFinished_coolDownPassed_foldTrackingWorksAsUsual() {
        testScope.runTest {
            setDisplaySwitchState(Switching(FOLDED))
            setDisplaySwitchState(Corrupted(FOLDED))
            setDisplaySwitchState(Idle(UNFOLDED))

            setDisplaySwitchState(Switching(FOLDED))
            setDisplaySwitchState(Idle(FOLDED))

            verify(latencyTracker, times(2)).onActionStart(ACTION_SWITCH_DISPLAY_FOLD)
            verify(latencyTracker).onActionEnd(ACTION_SWITCH_DISPLAY_FOLD)
        }
    }

    @Test
    fun interruptedDisplaySwitchFinished_coolDownPassed_eventWithCorruptedResultSent() {
        testScope.runTest {
            setDisplaySwitchState(Switching(HALF_FOLDED))
            setDisplaySwitchState(Corrupted(HALF_FOLDED))

            systemClock.advanceTime(5000)
            setDisplaySwitchState(Idle(FOLDED))

            val event = capturedLogEvent()
            assertThat(event.trackingResult)
                .isEqualTo(SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TRACKING_RESULT__CORRUPTED)
            assertThat(event.latencyMs).isEqualTo(5000)
        }
    }

    @Test
    fun displaySwitchTimedOut_unfoldTrackingCancelled() {
        testScope.runTest {
            setDisplaySwitchState(Switching(HALF_FOLDED))

            setDisplaySwitchState(Idle(UNFOLDED, timedOut = true))

            verify(latencyTracker).onActionCancel(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun displaySwitchTimedOut_foldTrackingCancelled() {
        testScope.runTest {
            setDisplaySwitchState(Switching(HALF_FOLDED))

            setDisplaySwitchState(Idle(FOLDED, timedOut = true))

            verify(latencyTracker).onActionCancel(ACTION_SWITCH_DISPLAY_FOLD)
        }
    }

    @Test
    fun displaySwitchTimedOut_eventLoggedWithTimeOut() {
        testScope.runTest {
            setDisplaySwitchState(Switching(HALF_FOLDED))

            systemClock.advanceTime(15000)
            setDisplaySwitchState(Idle(UNFOLDED, timedOut = true))

            val event = capturedLogEvent()
            assertThat(event.trackingResult)
                .isEqualTo(SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TRACKING_RESULT__TIMED_OUT)
            assertThat(event.latencyMs).isEqualTo(15000)
        }
    }

    @Test
    fun displaySwitch_screenTimeoutActive_logsNoScreenWakelocks() {
        testScope.runTest {
            screenTimeoutActive.value = true

            setDisplaySwitchState(Switching(HALF_FOLDED))
            setDisplaySwitchState(Idle(UNFOLDED))

            val event = capturedLogEvent()
            assertThat(event.screenWakelockStatus)
                .isEqualTo(
                    SysUiStatsLog
                        .DISPLAY_SWITCH_LATENCY_TRACKED__SCREEN_WAKELOCK_STATUS__SCREEN_WAKELOCK_STATUS_NO_WAKELOCKS
                )
        }
    }

    @Test
    fun displaySwitch_screenTimeoutNotActive_logsHasScreenWakelocks() {
        testScope.runTest {
            screenTimeoutActive.value = false

            setDisplaySwitchState(Switching(HALF_FOLDED))
            setDisplaySwitchState(Idle(UNFOLDED))

            val event = capturedLogEvent()
            assertThat(event.screenWakelockStatus)
                .isEqualTo(
                    SysUiStatsLog
                        .DISPLAY_SWITCH_LATENCY_TRACKED__SCREEN_WAKELOCK_STATUS__SCREEN_WAKELOCK_STATUS_HAS_SCREEN_WAKELOCKS
                )
        }
    }

    @Test
    fun displaySwitch_withNotifications_logsNotificationCount() {
        testScope.runTest {
            // Arrange: Set a specific notification count
            val expectedCount = 5
            whenever(activeNotificationsInteractor.allNotificationsCountValue)
                .thenReturn(expectedCount)

            // Act: Perform a display switch
            setDisplaySwitchState(Switching(HALF_FOLDED))
            setDisplaySwitchState(Idle(UNFOLDED))

            // Assert: Check that the logged event contains the correct count
            val event = capturedLogEvent()
            assertThat(event.notificationCount).isEqualTo(expectedCount)
        }
    }

    private fun capturedLogEvent(): DisplaySwitchLatencyEvent {
        verify(displaySwitchLatencyLogger).log(capture(loggerArgumentCaptor))
        return loggerArgumentCaptor.value
    }

    private fun successfulEvent(
        latencyMs: Int,
        fromFoldableDeviceState: Int,
        toFoldableDeviceState: Int,
        toState: Int = SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_STATE__UNKNOWN,
        notificationCount: Int = 0,
    ): DisplaySwitchLatencyEvent {
        return DisplaySwitchLatencyEvent(
            latencyMs = latencyMs,
            fromFoldableDeviceState = fromFoldableDeviceState,
            toFoldableDeviceState = toFoldableDeviceState,
            toState = toState,
            notificationCount = notificationCount,
            screenWakelockStatus =
                SysUiStatsLog
                    .DISPLAY_SWITCH_LATENCY_TRACKED__SCREEN_WAKELOCK_STATUS__SCREEN_WAKELOCK_STATUS_NO_WAKELOCKS,
            trackingResult = SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TRACKING_RESULT__SUCCESS,
        )
    }

    private fun setDisplaySwitchState(state: DisplaySwitchState) {
        displaySwitchTrackingInteractor.setDisplaySwitchState(state)
    }
}
