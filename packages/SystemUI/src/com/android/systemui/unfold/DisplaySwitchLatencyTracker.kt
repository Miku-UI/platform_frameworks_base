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
import android.hardware.devicestate.DeviceStateManager
import android.util.Log
import com.android.app.tracing.instantForTrack
import com.android.internal.util.LatencyTracker
import com.android.internal.util.LatencyTracker.ACTION_SWITCH_DISPLAY_FOLD
import com.android.internal.util.LatencyTracker.ACTION_SWITCH_DISPLAY_UNFOLD
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.shared.system.SysUiStatsLog
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.DisplaySwitchLatencyEvent
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.TrackingResult.CORRUPTED
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.TrackingResult.SUCCESS
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.TrackingResult.TIMED_OUT
import com.android.systemui.unfold.dagger.UnfoldTracking
import com.android.systemui.unfold.data.repository.ScreenTimeoutPolicyRepository
import com.android.systemui.unfold.domain.interactor.DisplaySwitchState
import com.android.systemui.unfold.domain.interactor.DisplaySwitchTrackingInteractor
import com.android.systemui.util.Compile
import com.android.systemui.util.Utils.isDeviceFoldable
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * [DisplaySwitchLatencyTracker] tracks latency and related fields for display switch of a foldable
 * device. This class populates [DisplaySwitchLatencyEvent] while an ongoing display switch event
 */
@SysUISingleton
class DisplaySwitchLatencyTracker
@Inject
constructor(
    private val context: Context,
    private val powerInteractor: PowerInteractor,
    private val screenTimeoutPolicyRepository: ScreenTimeoutPolicyRepository,
    private val keyguardInteractor: KeyguardInteractor,
    private val activeNotificationsInteractor: ActiveNotificationsInteractor,
    @UnfoldTracking private val scope: CoroutineScope,
    private val displaySwitchLatencyLogger: DisplaySwitchLatencyLogger,
    private val systemClock: SystemClock,
    private val deviceStateManager: DeviceStateManager,
    private val displaySwitchInteractor: DisplaySwitchTrackingInteractor,
    private val latencyTracker: LatencyTracker,
) : CoreStartable {

    private val isAodEnabled: Boolean
        get() = keyguardInteractor.isAodAvailable.value

    override fun start() {
        if (!isDeviceFoldable(context.resources, deviceStateManager)) {
            return
        }
        scope.launch {
            displaySwitchInteractor.displaySwitchState
                .filter { it !is DisplaySwitchState.Unknown }
                .onEach(::startLatencyTracking)
                .map(::toDisplaySwitchUpdate)
                // we need full chain of updates to create tracking event with possible options for
                // display switch chain:
                // Idle -> Switching -> Corrupted -> Idle
                // * -> Idle -> Switching -> Idle
                // so the longest chain of events we might need is 4
                .windowed(size = 4)
                .filter { updates ->
                    val lastState = updates.last().switchState
                    val enoughUpdatesToCreateEvent = updates.size > 2
                    lastState is DisplaySwitchState.Idle && enoughUpdatesToCreateEvent
                }
                .map(::toUpdatesChain)
                .collect { updatesChain ->
                    instantForTrack(TAG) { "new states: $updatesChain " }
                    log { "new display switch states: $updatesChain " }
                    if (updatesChain.wasCorrupted || updatesChain.timedOut) {
                        latencyTracker.onActionCancel(ACTION_SWITCH_DISPLAY_UNFOLD)
                        latencyTracker.onActionCancel(ACTION_SWITCH_DISPLAY_FOLD)
                    } else {
                        latencyTracker.onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)

                        if (getToState()
                            != SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TO_STATE__SCREEN_OFF
                        ) {
                            latencyTracker.onActionEnd(ACTION_SWITCH_DISPLAY_FOLD)
                        } else {
                            latencyTracker.onActionCancel(ACTION_SWITCH_DISPLAY_FOLD)
                        }
                    }
                    logDisplaySwitchEvent(updatesChain)
                }
        }
    }

    private fun startLatencyTracking(switchState: DisplaySwitchState) {
        if (switchState !is DisplaySwitchState.Switching) return
        val startedUnfolding = switchState.newDeviceState != DeviceState.FOLDED
        if (startedUnfolding) {
            latencyTracker.onActionStart(ACTION_SWITCH_DISPLAY_UNFOLD)
        } else {
            latencyTracker.onActionStart(ACTION_SWITCH_DISPLAY_FOLD)
        }
    }

    private fun toDisplaySwitchUpdate(state: DisplaySwitchState) =
        DisplaySwitchUpdate(
            switchState = state,
            elapsedTime = systemClock.elapsedRealtime(),
            event =
                (state as? DisplaySwitchState.Switching)?.let {
                    DisplaySwitchLatencyEvent().withBeforeFields()
                },
        )

    private fun logDisplaySwitchEvent(updatesChain: UpdatesChain) {
        val switchingUpdate = updatesChain.lastSwitchingUpdate
        val startingIdleState = updatesChain.startIdleState
        if (switchingUpdate == null || startingIdleState == null) {
            log { "Incorrect updates chain: $updatesChain. Not enough data to log event" }
            return
        }
        val trackingResult =
            when {
                updatesChain.wasCorrupted -> CORRUPTED
                updatesChain.timedOut -> TIMED_OUT
                else -> SUCCESS
            }
        val event = switchingUpdate.event ?: return
        val toState = getToState()
        val displaySwitchTimeMs =
            if (isStateScreenOff()) {
                LATENCY_UNDEFINED // we don't care about latency in this case
            } else {
                updatesChain.finalUpdate.elapsedTime - switchingUpdate.elapsedTime
            }
        log {
            "trackingResult=$trackingResult, " +
                "fromFoldableDeviceState=${startingIdleState.switchState.newDeviceState}, " +
                "toFoldableDeviceState=${updatesChain.finalUpdate.switchState.newDeviceState}, " +
                "toState=${toState}, " +
                "latencyMs=${displaySwitchTimeMs}"
        }
        instantForTrack(TAG) {
            "toFoldableDeviceState=${updatesChain.finalUpdate.switchState.newDeviceState}, toState=${toState}"
        }
        displaySwitchLatencyLogger.log(
            event.copy(
                fromFoldableDeviceState = startingIdleState.switchState.newDeviceState.toStatsInt(),
                toFoldableDeviceState =
                    updatesChain.finalUpdate.switchState.newDeviceState.toStatsInt(),
                latencyMs = displaySwitchTimeMs.toInt(),
                toState = toState,
                trackingResult = trackingResult.toStatsInt(),
            )
        )
    }

    private data class UpdatesChain(
        val startIdleState: DisplaySwitchUpdate?,
        val lastSwitchingUpdate: DisplaySwitchUpdate?,
        val wasCorrupted: Boolean,
        val finalUpdate: DisplaySwitchUpdate,
        val timedOut: Boolean,
    )

    private fun toUpdatesChain(updates: List<DisplaySwitchUpdate>): UpdatesChain {
        require(updates.size > 2) { "Expected more than two updates" }
        require(updates.last().switchState is DisplaySwitchState.Idle) {
            "Expected last update to be Idle"
        }
        val lastSwitchingStateIndex =
            updates.indexOfLast { it.switchState is DisplaySwitchState.Switching }
        // check if the state before final state is corrupted
        val wasCorrupted = updates[updates.size - 2].switchState is DisplaySwitchState.Corrupted
        val finalUpdate = updates.last()
        return UpdatesChain(
            startIdleState = updates.getOrNull(lastSwitchingStateIndex - 1),
            lastSwitchingUpdate = updates.getOrNull(lastSwitchingStateIndex),
            wasCorrupted = wasCorrupted,
            finalUpdate = finalUpdate,
            timedOut = (finalUpdate.switchState as? DisplaySwitchState.Idle)?.timedOut ?: false,
        )
    }

    private data class DisplaySwitchUpdate(
        val switchState: DisplaySwitchState,
        val elapsedTime: Long,
        val event: DisplaySwitchLatencyEvent? = null,
    )

    private fun <T> Flow<T>.windowed(size: Int): Flow<List<T>> {
        require(size >= 1) { "Expected positive window size, but got $size" }
        return flow {
            val result: ArrayDeque<T> = ArrayDeque(size)
            collect { value ->
                if (result.size == size) {
                    result.removeFirst()
                }
                result.add(value)
                emit(result.toList())
            }
        }
    }

    private fun DeviceState.toStatsInt(): Int =
        when (this) {
            DeviceState.FOLDED -> FOLDABLE_DEVICE_STATE_CLOSED
            DeviceState.HALF_FOLDED -> FOLDABLE_DEVICE_STATE_HALF_OPEN
            DeviceState.UNFOLDED -> FOLDABLE_DEVICE_STATE_OPEN
            DeviceState.CONCURRENT_DISPLAY -> FOLDABLE_DEVICE_STATE_FLIPPED
            else -> FOLDABLE_DEVICE_STATE_UNKNOWN
        }

    private fun TrackingResult.toStatsInt(): Int =
        when (this) {
            SUCCESS -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TRACKING_RESULT__SUCCESS
            CORRUPTED -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TRACKING_RESULT__CORRUPTED
            TIMED_OUT -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TRACKING_RESULT__TIMED_OUT
        }

    private fun getToState(): Int =
        // not checking asleep/screen off reason means we misrepresent toState for case when user
        // folds and quickly puts device to sleep with power button. But still it seems better
        // than not putting SCREEN_OFF as reason when device is just asleep and user folds.
        when {
            isStateAod() -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TO_STATE__AOD
            isStateScreenOff() -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TO_STATE__SCREEN_OFF
            else -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TO_STATE__UNKNOWN
        }

    private fun getFromState(): Int =
        when {
            isStateAod() -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_STATE__AOD
            isStateScreenOff() ->
                SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_STATE__SCREEN_OFF
            else -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_STATE__UNKNOWN
        }

    private fun isStateAod() = isAsleep() && isAodEnabled

    private fun isStateScreenOff() = isAsleep() && !isAodEnabled

    private fun isAsleep() = powerInteractor.detailedWakefulness.value.isAsleep()

    private inline fun log(msg: () -> String) {
        if (DEBUG) Log.d(TAG, msg())
    }

    private fun DisplaySwitchLatencyEvent.withBeforeFields(): DisplaySwitchLatencyEvent {
        val screenTimeoutActive = screenTimeoutPolicyRepository.screenTimeoutActive.value
        val screenWakelockStatus =
            if (screenTimeoutActive) {
                NO_SCREEN_WAKELOCKS
            } else {
                HAS_SCREEN_WAKELOCKS
            }
        return copy(
            screenWakelockStatus = screenWakelockStatus,
            fromState = getFromState(),
            notificationCount = activeNotificationsInteractor.allNotificationsCountValue,
        )
    }

    /**
     * Stores values corresponding to all respective [DisplaySwitchLatencyTrackedField] in a single
     * event of display switch for foldable devices.
     *
     * Once the data is captured in this data class and appropriate to log, it is logged through
     * [DisplaySwitchLatencyLogger]
     */
    data class DisplaySwitchLatencyEvent(
        val latencyMs: Int = VALUE_UNKNOWN,
        val fromFoldableDeviceState: Int = FOLDABLE_DEVICE_STATE_UNKNOWN,
        val fromState: Int = SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_STATE__UNKNOWN,
        val fromFocusedAppUid: Int = VALUE_UNKNOWN,
        val fromPipAppUid: Int = VALUE_UNKNOWN,
        val fromVisibleAppsUid: Set<Int> = setOf(),
        val fromDensityDpi: Int = VALUE_UNKNOWN,
        val toFoldableDeviceState: Int = FOLDABLE_DEVICE_STATE_UNKNOWN,
        val toState: Int = SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_STATE__UNKNOWN,
        val toFocusedAppUid: Int = VALUE_UNKNOWN,
        val toPipAppUid: Int = VALUE_UNKNOWN,
        val toVisibleAppsUid: Set<Int> = setOf(),
        val toDensityDpi: Int = VALUE_UNKNOWN,
        val notificationCount: Int = VALUE_UNKNOWN,
        val externalDisplayCount: Int = VALUE_UNKNOWN,
        val throttlingLevel: Int =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__THROTTLING_LEVEL__NONE,
        val vskinTemperatureC: Int = VALUE_UNKNOWN,
        val hallSensorToFirstHingeAngleChangeMs: Int = VALUE_UNKNOWN,
        val hallSensorToDeviceStateChangeMs: Int = VALUE_UNKNOWN,
        val onScreenTurningOnToOnDrawnMs: Int = VALUE_UNKNOWN,
        val onDrawnToOnScreenTurnedOnMs: Int = VALUE_UNKNOWN,
        val trackingResult: Int =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TRACKING_RESULT__UNKNOWN_RESULT,
        val screenWakelockStatus: Int =
            SysUiStatsLog
                .DISPLAY_SWITCH_LATENCY_TRACKED__SCREEN_WAKELOCK_STATUS__SCREEN_WAKELOCK_STATUS_UNKNOWN,
    )

    enum class TrackingResult {
        SUCCESS,
        CORRUPTED,
        TIMED_OUT,
    }

    companion object {
        private const val VALUE_UNKNOWN = -1
        private const val LATENCY_UNDEFINED = -1
        private const val TAG = "DisplaySwitchLatency"
        private val DEBUG = Compile.IS_DEBUG && Log.isLoggable(TAG, Log.VERBOSE)

        private const val FOLDABLE_DEVICE_STATE_UNKNOWN =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_FOLDABLE_DEVICE_STATE__STATE_UNKNOWN
        const val FOLDABLE_DEVICE_STATE_CLOSED =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_FOLDABLE_DEVICE_STATE__STATE_CLOSED
        const val FOLDABLE_DEVICE_STATE_HALF_OPEN =
            SysUiStatsLog
                .DISPLAY_SWITCH_LATENCY_TRACKED__FROM_FOLDABLE_DEVICE_STATE__STATE_HALF_OPENED
        private const val FOLDABLE_DEVICE_STATE_OPEN =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_FOLDABLE_DEVICE_STATE__STATE_OPENED
        private const val FOLDABLE_DEVICE_STATE_FLIPPED =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_FOLDABLE_DEVICE_STATE__STATE_FLIPPED

        private const val HAS_SCREEN_WAKELOCKS =
            SysUiStatsLog
                .DISPLAY_SWITCH_LATENCY_TRACKED__SCREEN_WAKELOCK_STATUS__SCREEN_WAKELOCK_STATUS_HAS_SCREEN_WAKELOCKS
        private const val NO_SCREEN_WAKELOCKS =
            SysUiStatsLog
                .DISPLAY_SWITCH_LATENCY_TRACKED__SCREEN_WAKELOCK_STATUS__SCREEN_WAKELOCK_STATUS_NO_WAKELOCKS
    }
}
