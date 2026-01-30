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

package com.android.systemui.unfold.domain.interactor

import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.app.tracing.TraceUtils.traceAsync
import com.android.app.tracing.instantForTrack
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.display.data.repository.DeviceStateRepository
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.FOLDED
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.HALF_FOLDED
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.UNFOLDED
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.shared.model.ScreenPowerState
import com.android.systemui.power.shared.model.WakefulnessModel
import com.android.systemui.power.shared.model.WakefulnessState
import com.android.systemui.unfold.dagger.UnfoldTracking
import com.android.systemui.unfold.data.repository.UnfoldTransitionStatus.TransitionStarted
import com.android.systemui.unfold.domain.interactor.DisplaySwitchState.Corrupted
import com.android.systemui.unfold.domain.interactor.DisplaySwitchState.Idle
import com.android.systemui.util.animation.data.repository.AnimationStatusRepository
import com.android.systemui.util.kotlin.pairwise
import com.android.systemui.util.kotlin.race
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

interface DisplaySwitchTrackingInteractor {
    /**
     * Emits latest [DisplaySwitchState] with a guarantee it doesn't emit the same class of state
     * twice in a row.
     */
    val displaySwitchState: StateFlow<DisplaySwitchState>
}

sealed interface DisplaySwitchState {
    val newDeviceState: DeviceState

    /**
     * Displays are in a stable state aka not in the process of switching. If we couldn't track
     * display switch properly because end event never arrived within
     * [FoldableDisplaySwitchTrackingInteractor.SCREEN_EVENT_TIMEOUT], [timedOut] is set to true.
     */
    data class Idle(override val newDeviceState: DeviceState, val timedOut: Boolean = false) :
        DisplaySwitchState

    /**
     * Displays are currently switching. This state can only come directly after [Idle] state.
     * Switching might not be visible to the user, that is, folding device with screen off still
     * emits Switching event as we're swapping default displays.
     */
    data class Switching(override val newDeviceState: DeviceState) : DisplaySwitchState

    /**
     * Switching displays happened multiple times before [Idle] state could settle. This state will
     * hold until no new display switch related events are sent within
     * [FoldableDisplaySwitchTrackingInteractor.COOL_DOWN_DURATION] window. This event can only
     * happen directly after [Switching] state and is always directly followed by [Idle] state.
     */
    data class Corrupted(override val newDeviceState: DeviceState) : DisplaySwitchState

    /** Missing data about device state, should happen only when class is initialized. */
    data object Unknown : DisplaySwitchState {
        override val newDeviceState: DeviceState = DeviceState.UNKNOWN
    }
}

/**
 * Contains data about current state of display switch on foldable device which can be one of
 * [DisplaySwitchState]. [displaySwitchState] emits only states that we're sure are legit changes,
 * compared to super quick toggling between the states caused by Hall sensor misfiring or user doing
 * something very creative. Those kind of changes cause [DisplaySwitchState.CORRUPTED] to be
 * emitted.
 */
@SysUISingleton
class FoldableDisplaySwitchTrackingInteractor
@Inject
constructor(
    private val deviceStateRepository: DeviceStateRepository,
    private val powerInteractor: PowerInteractor,
    private val unfoldTransitionInteractor: UnfoldTransitionInteractor,
    private val animationStatusRepository: AnimationStatusRepository,
    private val keyguardInteractor: KeyguardInteractor,
    private val systemClock: SystemClock,
    @UnfoldTracking private val scope: CoroutineScope,
) : DisplaySwitchTrackingInteractor, CoreStartable {

    private val _displaySwitchState =
        MutableStateFlow<DisplaySwitchState>(DisplaySwitchState.Unknown)

    override val displaySwitchState: StateFlow<DisplaySwitchState> = _displaySwitchState

    private val displaySwitchStarted =
        deviceStateRepository.state.pairwise().filter { (previousState, newState) ->
            // React only when the foldable device is
            // folding(UNFOLDED/HALF_FOLDED -> FOLDED) or unfolding(FOLDED -> HALF_FOLD/UNFOLDED)
            (previousState == FOLDED && newState.isUnfoldingState()) ||
                (newState == FOLDED && previousState.isUnfoldingState())
        }

    private fun DeviceState.isUnfoldingState() = this == HALF_FOLDED || this == UNFOLDED

    private val startOrEndEvent: Flow<Any> = merge(displaySwitchStarted, anyEndEventFlow())

    private var isCoolingDown = false

    override fun start() {
        scope.launch {
            _displaySwitchState.value =
                Idle(deviceStateRepository.state.first { it == FOLDED || it.isUnfoldingState() })
            displaySwitchStarted.collectLatest { (previousState, newState) ->
                if (isCoolingDown) return@collectLatest
                log { "received previousState=$previousState, newState=$newState" }
                try {
                    _displaySwitchState.value = DisplaySwitchState.Switching(newState)
                    withTimeout(SCREEN_EVENT_TIMEOUT) {
                        traceAsync(TAG, "displaySwitch") { waitForDisplaySwitch(newState) }
                        _displaySwitchState.value = Idle(deviceStateRepository.state.value)
                    }
                } catch (e: TimeoutCancellationException) {
                    log { "tracking timed out" }
                    _displaySwitchState.value = Idle(newState, timedOut = true)
                } catch (e: CancellationException) {
                    log { "new state interrupted, entering cool down" }
                    _displaySwitchState.value = Corrupted(newState)
                    startCoolDown()
                }
            }
        }
    }

    private inline fun log(msg: () -> String) {
        if (DEBUG) Log.d(TAG, msg())
    }

    @OptIn(FlowPreview::class)
    private fun CoroutineScope.startCoolDown() {
        if (isCoolingDown) return
        isCoolingDown = true
        launch {
            val startTime = systemClock.elapsedRealtime()
            try {
                startOrEndEvent.timeout(COOL_DOWN_DURATION).collect {}
            } catch (e: TimeoutCancellationException) {
                val totalCooldownTime = systemClock.elapsedRealtime() - startTime
                instantForTrack(TAG) { "cool down finished, lasted $totalCooldownTime ms" }
                _displaySwitchState.value = Idle(deviceStateRepository.state.value)
                isCoolingDown = false
            }
        }
    }

    private suspend fun waitForDisplaySwitch(toFoldableDeviceState: DeviceState) {
        val isTransitionEnabled =
            unfoldTransitionInteractor.isAvailable &&
                animationStatusRepository.areAnimationsEnabled().first()
        if (shouldWaitForTransitionStart(toFoldableDeviceState, isTransitionEnabled)) {
            traceAsync(TAG, "waitForTransitionStart()") {
                unfoldTransitionInteractor.waitForTransitionStart()
            }
        } else {
            race({ waitForScreenTurnedOn() }, { waitForGoToSleepWithScreenOff() })
        }
    }

    private fun anyEndEventFlow(): Flow<Any> {
        val unfoldStatus =
            unfoldTransitionInteractor.unfoldTransitionStatus.filter { it is TransitionStarted }
        // dropping first emission as we're only interested in new emissions, not current state
        val screenOn =
            powerInteractor.screenPowerState.drop(1).filter { it == ScreenPowerState.SCREEN_ON }
        val goToSleep =
            powerInteractor.detailedWakefulness.drop(1).filter { sleepWithScreenOff(it) }
        return merge(screenOn, goToSleep, unfoldStatus)
    }

    private fun shouldWaitForTransitionStart(
        toFoldableDeviceState: DeviceState,
        isTransitionEnabled: Boolean,
    ): Boolean = (toFoldableDeviceState != FOLDED && isTransitionEnabled)

    private suspend fun waitForScreenTurnedOn() {
        traceAsync(TAG, "waitForScreenTurnedOn()") {
            // dropping first as it's stateFlow and will always emit latest value but we're
            // only interested in new states
            powerInteractor.screenPowerState
                .drop(1)
                .filter { it == ScreenPowerState.SCREEN_ON }
                .first()
        }
    }

    private suspend fun waitForGoToSleepWithScreenOff() {
        traceAsync(TAG, "waitForGoToSleepWithScreenOff()") {
            powerInteractor.detailedWakefulness.filter { sleepWithScreenOff(it) }.first()
        }
    }

    private fun sleepWithScreenOff(model: WakefulnessModel) =
        model.internalWakefulnessState == WakefulnessState.ASLEEP &&
            !keyguardInteractor.isAodAvailable.value

    companion object {
        private const val TAG = "FoldableDisplaySwitch"
        private val DEBUG = Build.IS_DEBUGGABLE

        @VisibleForTesting val COOL_DOWN_DURATION = 2.seconds
        @VisibleForTesting val SCREEN_EVENT_TIMEOUT = 15.seconds
    }
}
