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
 * limitations under the License
 */
package com.android.systemui.unfold.domain.interactor

import android.os.PowerManager.GO_TO_SLEEP_REASON_DEVICE_FOLD
import android.os.PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.domain.interactor.configurationInteractor
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.FOLDED
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.HALF_FOLDED
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.UNFOLDED
import com.android.systemui.display.data.repository.fakeDeviceStateRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setScreenPowerState
import com.android.systemui.power.domain.interactor.PowerInteractorFactory
import com.android.systemui.power.shared.model.ScreenPowerState.SCREEN_OFF
import com.android.systemui.power.shared.model.ScreenPowerState.SCREEN_ON
import com.android.systemui.testKosmos
import com.android.systemui.unfold.FakeUnfoldTransitionProvider
import com.android.systemui.unfold.data.repository.UnfoldTransitionRepositoryImpl
import com.android.systemui.unfold.domain.interactor.DisplaySwitchState.Corrupted
import com.android.systemui.unfold.domain.interactor.DisplaySwitchState.Idle
import com.android.systemui.unfold.domain.interactor.DisplaySwitchState.Switching
import com.android.systemui.unfold.domain.interactor.DisplaySwitchState.Unknown
import com.android.systemui.unfold.domain.interactor.FoldableDisplaySwitchTrackingInteractor.Companion.COOL_DOWN_DURATION
import com.android.systemui.unfold.domain.interactor.FoldableDisplaySwitchTrackingInteractor.Companion.SCREEN_EVENT_TIMEOUT
import com.android.systemui.util.animation.data.repository.fakeAnimationStatusRepository
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class FoldableDisplaySwitchTrackingInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope: TestScope = kosmos.testScope

    private val deviceStateRepository = kosmos.fakeDeviceStateRepository
    private val powerInteractor = PowerInteractorFactory.create().powerInteractor
    private val animationStatusRepository = kosmos.fakeAnimationStatusRepository
    private val keyguardInteractor = mock<KeyguardInteractor>()
    private val systemClock = FakeSystemClock()
    private val unfoldTransitionProgressProvider = FakeUnfoldTransitionProvider()
    private val unfoldTransitionInteractor =
        UnfoldTransitionInteractor(
            UnfoldTransitionRepositoryImpl(Optional.of(unfoldTransitionProgressProvider)),
            kosmos.configurationInteractor,
        )
    private lateinit var displaySwitchInteractor: FoldableDisplaySwitchTrackingInteractor

    @Before
    fun setup() {
        whenever(keyguardInteractor.isAodAvailable).thenReturn(MutableStateFlow(false))
        animationStatusRepository.onAnimationStatusChanged(true)
        powerInteractor.setAwakeForTest()
        powerInteractor.setScreenPowerState(SCREEN_ON)

        displaySwitchInteractor =
            FoldableDisplaySwitchTrackingInteractor(
                deviceStateRepository,
                powerInteractor,
                unfoldTransitionInteractor,
                animationStatusRepository,
                keyguardInteractor,
                systemClock,
                testScope.backgroundScope,
            )
        displaySwitchInteractor.start()
    }

    private fun displaySwitchState() = displaySwitchInteractor.displaySwitchState.value

    @Test
    fun startUnfolding_emitsSwitching() {
        testScope.runTest {
            setDeviceState(FOLDED)

            startUnfolding()

            assertThat(displaySwitchState()).isEqualTo(Switching(HALF_FOLDED))
        }
    }

    @Test
    fun unfolding_transitionFinished_emitsIdle() {
        testScope.runTest {
            setDeviceState(FOLDED)
            startUnfolding()

            unfoldTransitionProgressProvider.onTransitionStarted()

            assertThat(displaySwitchState()).isEqualTo(Idle(HALF_FOLDED))
        }
    }

    @Test
    fun unfolding_screenTurnedOn_doesntEmitIdle() {
        testScope.runTest {
            setDeviceState(FOLDED)
            val state by collectLastValue(displaySwitchInteractor.displaySwitchState)

            startUnfolding()
            powerInteractor.setScreenPowerState(SCREEN_ON)
            assertThat(state).isEqualTo(Switching(HALF_FOLDED))
        }
    }

    @Test
    fun unfolding_unfoldProgressNotAvailable_emitsIdleAfterScreenOn() {
        testScope.runTest {
            val unfoldTransitionInteractorWithEmptyProgressProvider =
                UnfoldTransitionInteractor(
                    UnfoldTransitionRepositoryImpl(Optional.empty()),
                    kosmos.configurationInteractor,
                )
            val displaySwitchInteractor =
                FoldableDisplaySwitchTrackingInteractor(
                    deviceStateRepository,
                    powerInteractor,
                    unfoldTransitionInteractorWithEmptyProgressProvider,
                    animationStatusRepository,
                    keyguardInteractor,
                    systemClock,
                    testScope.backgroundScope,
                )
            displaySwitchInteractor.start()

            startUnfolding()
            powerInteractor.setScreenPowerState(SCREEN_ON)

            assertThat(displaySwitchState()).isEqualTo(Idle(HALF_FOLDED))
        }
    }

    @Test
    fun unfolding_animationsDisabled_emitsIdleAfterScreenOn() {
        testScope.runTest {
            setDeviceState(FOLDED)
            animationStatusRepository.onAnimationStatusChanged(enabled = false)

            startUnfolding()
            powerInteractor.setScreenPowerState(SCREEN_ON)

            assertThat(displaySwitchState()).isEqualTo(Idle(HALF_FOLDED))
        }
    }

    @Test
    fun unfoldingDevice_animationsDisabled_emitsIdleWhenDeviceGoesToSleep() {
        testScope.runTest {
            animationStatusRepository.onAnimationStatusChanged(enabled = false)
            setDeviceState(FOLDED)

            startUnfolding()
            powerInteractor.setAsleepForTest(sleepReason = GO_TO_SLEEP_REASON_POWER_BUTTON)

            assertThat(displaySwitchState()).isEqualTo(Idle(HALF_FOLDED))
        }
    }

    @Test
    fun startFolding_emitsSwitching() {
        testScope.runTest {
            setDeviceState(UNFOLDED)

            startFolding()

            assertThat(displaySwitchState()).isEqualTo(Switching(FOLDED))
        }
    }

    @Test
    fun switchingToNonFoldingRelatedStates_isIgnored() {
        testScope.runTest {
            val emittedStates by collectValues(displaySwitchInteractor.displaySwitchState)
            val startingStates = listOf(UNFOLDED, FOLDED, HALF_FOLDED)
            val targetStates = DeviceState.entries - startingStates

            startingStates.forEach { startingState ->
                targetStates.forEach { targetState ->
                    setDeviceState(startingState)
                    setDeviceState(targetState)
                }
            }

            assertThat(emittedStates).containsExactly(Unknown, Idle(UNFOLDED))
        }
    }

    @Test
    fun switchingFromNonFoldingRelatedStates_isIgnored() {
        testScope.runTest {
            val emittedStates by collectValues(displaySwitchInteractor.displaySwitchState)

            val targetStates = listOf(UNFOLDED, FOLDED, HALF_FOLDED)
            val startingStates = DeviceState.entries - targetStates
            startingStates.forEach { startingState ->
                targetStates.forEach { targetState ->
                    setDeviceState(startingState)
                    setDeviceState(targetState)
                }
            }

            assertThat(emittedStates).containsExactly(Unknown, Idle(UNFOLDED))
        }
    }

    @Test
    fun folding_screenTurnsOn_emitsIdle() {
        testScope.runTest {
            setDeviceState(UNFOLDED)
            startFolding()

            powerInteractor.setScreenPowerState(SCREEN_ON)

            assertThat(displaySwitchState()).isEqualTo(Idle(FOLDED))
        }
    }

    @Test
    fun folding_screenOff_emitsIdle() {
        testScope.runTest {
            setDeviceState(UNFOLDED)

            startFolding()
            powerInteractor.setAsleepForTest(sleepReason = GO_TO_SLEEP_REASON_DEVICE_FOLD)
            powerInteractor.setScreenPowerState(SCREEN_OFF)

            assertThat(displaySwitchState()).isEqualTo(Idle(FOLDED))
        }
    }

    @Test
    fun unfolding_progressUnavailable_emitsIdleWhenScreenTurnedOn() {
        testScope.runTest {
            val unfoldTransitionInteractorWithEmptyProgressProvider =
                UnfoldTransitionInteractor(
                    UnfoldTransitionRepositoryImpl(Optional.empty()),
                    kosmos.configurationInteractor,
                )
            val displaySwitchInteractor =
                FoldableDisplaySwitchTrackingInteractor(
                    deviceStateRepository,
                    powerInteractor,
                    unfoldTransitionInteractorWithEmptyProgressProvider,
                    animationStatusRepository,
                    keyguardInteractor,
                    systemClock,
                    testScope.backgroundScope,
                )
            displaySwitchInteractor.start()

            startUnfolding()
            powerInteractor.setScreenPowerState(SCREEN_ON)

            assertThat(displaySwitchState()).isEqualTo(Idle(HALF_FOLDED))
        }
    }

    @Test
    fun displaySwitchTimedOut_emittedIdleWithTimeOut() {
        testScope.runTest {
            setDeviceState(FOLDED)
            startUnfolding()
            advanceTimeBy(SCREEN_EVENT_TIMEOUT + 10.milliseconds)

            assertThat(displaySwitchState()).isEqualTo(Idle(HALF_FOLDED, timedOut = true))
        }
    }

    @Test
    fun unfolding_transitionInterrupted_emitsCorrupted() {
        testScope.runTest {
            setDeviceState(FOLDED)

            startUnfolding()
            startFolding()

            assertThat(displaySwitchState()).isEqualTo(Corrupted(HALF_FOLDED))
        }
    }

    @Test
    fun displaySwitchInterrupted_manyStateChanges_emitsOneCorrupted() {
        testScope.runTest {
            setDeviceState(FOLDED)
            startUnfolding()
            startFolding() // entering corrupted state, let's collect new states only after that
            val states by collectValues(displaySwitchInteractor.displaySwitchState)

            startUnfolding()
            startFolding()
            startUnfolding()
            finishUnfolding()

            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun interruptedDisplaySwitchFinished_coolDownPassed_trackingWorksAsUsual() {
        testScope.runTest {
            setDeviceState(FOLDED)

            startUnfolding()
            startFolding()
            finishFolding()

            advanceTimeBy(COOL_DOWN_DURATION.plus(10.milliseconds))
            startUnfolding()
            finishUnfolding()

            assertThat(displaySwitchState()).isEqualTo(Idle(HALF_FOLDED, timedOut = false))
        }
    }

    @Test
    fun interruptedDisplaySwitchFinished_coolDownPassed_emitsIdleState() {
        testScope.runTest {
            setDeviceState(FOLDED)

            startUnfolding()
            startFolding()
            finishFolding()
            advanceTimeBy(COOL_DOWN_DURATION.plus(10.milliseconds))

            assertThat(displaySwitchState()).isEqualTo(Idle(FOLDED))
        }
    }

    @Test
    fun displaySwitchInterrupted_coolDownExtendedByStartEvents() {
        testScope.runTest {
            setDeviceState(FOLDED)

            startUnfolding()
            startFolding()
            advanceTimeBy(COOL_DOWN_DURATION.minus(10.milliseconds))
            startUnfolding()
            advanceTimeBy(20.milliseconds)

            startFolding()
            finishUnfolding()

            assertThat(displaySwitchState()).isEqualTo(Corrupted(HALF_FOLDED))
        }
    }

    @Test
    fun displaySwitchInterrupted_coolDownExtendedByAnyEndEvent() {
        testScope.runTest {
            setDeviceState(FOLDED)

            startUnfolding()
            startFolding()
            startUnfolding()
            advanceTimeBy(COOL_DOWN_DURATION - 10.milliseconds)
            powerInteractor.setScreenPowerState(SCREEN_ON)
            advanceTimeBy(20.milliseconds)

            startFolding()
            finishUnfolding()

            assertThat(displaySwitchState()).isEqualTo(Corrupted(HALF_FOLDED))
        }
    }

    @Test
    fun foldingStarted_screenStillOn_eventSentOnlyAfterScreenSwitches() {
        // can happen for both folding and unfolding (with animations off) but it's more likely to
        // happen when folding as waiting for screen on is the default case then
        testScope.runTest {
            val state by collectLastValue(displaySwitchInteractor.displaySwitchState)
            powerInteractor.setScreenPowerState(SCREEN_ON)
            setDeviceState(UNFOLDED)

            setDeviceState(FOLDED)
            assertThat(state).isEqualTo(Switching(FOLDED))
            powerInteractor.setScreenPowerState(SCREEN_OFF)
            powerInteractor.setScreenPowerState(SCREEN_ON)

            assertThat(state).isEqualTo(Idle(FOLDED))
        }
    }

    private fun startUnfolding() {
        setDeviceState(HALF_FOLDED)
        powerInteractor.setScreenPowerState(SCREEN_OFF)
    }

    private fun startFolding() {
        setDeviceState(FOLDED)
        powerInteractor.setScreenPowerState(SCREEN_OFF)
    }

    private fun finishFolding() {
        powerInteractor.setScreenPowerState(SCREEN_ON)
    }

    private fun finishUnfolding() {
        unfoldTransitionProgressProvider.onTransitionStarted()
    }

    private fun setDeviceState(state: DeviceState) {
        deviceStateRepository.emit(state)
    }
}
