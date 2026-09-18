/*
 * Copyright (C) 2022 The Android Open Source Project
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
 *
 */

package com.android.systemui.power.data.repository

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.android.app.tracing.coroutines.flow.traceAs
import com.android.keyguard.UserActivityNotifier
import com.android.systemui.Flags
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.power.data.model.PowerButtonLaunchEvent
import com.android.systemui.power.shared.model.DozeScreenStateModel
import com.android.systemui.power.shared.model.ScreenPowerState
import com.android.systemui.power.shared.model.WakeSleepReason
import com.android.systemui.power.shared.model.WakefulnessModel
import com.android.systemui.power.shared.model.WakefulnessState
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.util.time.SystemClock
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Defines interface for classes that act as source of truth for power-related data. */
interface PowerRepository {
    /** Whether the device is interactive. Starts with the current state. */
    val isInteractive: StateFlow<Boolean>

    /**
     * SharedFlow that is guaranteed to emit each wakefulness update. This has a buffer size of 4,
     * so we'll eventually always receive two pairs of STARTED/FINISHED GOING_TO_SLEEP/WAKING.
     *
     * This flow has poor performance characteristics. If possible, use [wakefulness], which will
     * always end up with the current wakefulness state even if intermediate states are dropped.
     */
    val wakefulnessEvents: SharedFlow<WakefulnessModel>

    /**
     * Whether the device is awake or asleep. [WakefulnessState.AWAKE] means the screen is fully
     * powered on, and the user can interact with the device. [WakefulnessState.ASLEEP] means the
     * screen is either off, or in low-power always-on-display mode - in either case, the user
     * cannot interact with the device and will need to wake it up somehow if they wish to do so.
     *
     * As this is a StateFlow, this will represent the most recent wakefulness state of the device,
     * but intermediate states may be dropped (for example, if the user quickly turns the screen off
     * and back on, this may never emit STARTED_GOING_TO_SLEEP prior to emitting
     * FINISHED_WAKING_UP).
     *
     * If you absolutely need to be able to count on receiving all events, use [wakefulnessEvents].
     * However, avoid this if possible as that is a SharedFlow with poor performance
     * characteristics.
     */
    val wakefulness: StateFlow<WakefulnessModel>

    /**
     * Emits when a double tap power button launch is detected, with information about the entry
     * status of the device when the gesture was initiated.
     */
    val powerButtonLaunchEvents: Flow<PowerButtonLaunchEvent>

    /**
     * The physical on/off state of the display. [ScreenPowerState.SCREEN_OFF] means the display is
     * unpowered and nothing is visible. [ScreenPowerState.SCREEN_ON] means the display is either
     * fully powered on, or it's in low-power always-on-display (AOD) mode showing the time and
     * other info.
     *
     * YOU PROBABLY DO NOT WANT TO USE THIS STATE. Almost all System UI use cases for screen state
     * expect that the screen would be considered "off" if we're on AOD, which is not the case for
     * [screenPowerState]. Consider [wakefulness] instead.
     */
    val screenPowerState: StateFlow<ScreenPowerState>

    /** More granular display states, mainly for use in dozing. */
    val dozeScreenState: MutableStateFlow<DozeScreenStateModel>

    /** Wakes up the device. */
    fun wakeUp(why: String, @PowerManager.WakeReason wakeReason: Int)

    /**
     * Notifies the power repository that a user touch happened.
     *
     * @param noChangeLights If true, does not cause the keyboard backlight to turn on because of
     *   this event. This is set when the power key is pressed. We want the device to stay on while
     *   the button is down, but we're about to turn off the screen so we don't want the keyboard
     *   backlight to turn on again. Otherwise the lights flash on and then off and it looks weird.
     */
    fun userTouch(noChangeLights: Boolean = false)

    /** Updates the wakefulness state, keeping previous values by default. */
    fun updateWakefulness(
        rawState: WakefulnessState =
            if (Flags.wakefulnessEventsSharedFlow() && SceneContainerFlag.isEnabled) {
                wakefulnessEvents.replayCache.first().internalWakefulnessState
            } else {
                wakefulness.value.internalWakefulnessState
            },
        lastWakeReason: WakeSleepReason =
            if (Flags.wakefulnessEventsSharedFlow() && SceneContainerFlag.isEnabled) {
                wakefulnessEvents.replayCache.first().lastWakeReason
            } else {
                wakefulness.value.lastWakeReason
            },
        lastSleepReason: WakeSleepReason =
            if (Flags.wakefulnessEventsSharedFlow() && SceneContainerFlag.isEnabled) {
                wakefulnessEvents.replayCache.first().lastSleepReason
            } else {
                wakefulness.value.lastSleepReason
            },
        powerButtonLaunchGestureTriggered: Boolean =
            if (Flags.wakefulnessEventsSharedFlow() && SceneContainerFlag.isEnabled) {
                wakefulnessEvents.replayCache.first().powerButtonLaunchGestureTriggered
            } else {
                wakefulness.value.powerButtonLaunchGestureTriggered
            },
        asleepOrWakingFromPreviouslyEnteredDevice: Boolean =
            if (SceneContainerFlag.isEnabled) {
                if (Flags.wakefulnessEventsSharedFlow()) {
                    wakefulnessEvents.replayCache
                        .first()
                        .asleepOrWakingFromPreviouslyEnteredDevice()
                } else {
                    wakefulness.value.asleepOrWakingFromPreviouslyEnteredDevice()
                }
            } else {
                false
            },
    )

    /** Updates the screen power state. */
    fun setScreenPowerState(state: ScreenPowerState)

    /** Notifies the repository that a double tap power button launch gesture has been detected. */
    fun onPowerButtonLaunchEvent(event: PowerButtonLaunchEvent)
}

@SuppressLint("SharedFlowCreation")
@SysUISingleton
class PowerRepositoryImpl
@Inject
constructor(
    private val manager: PowerManager,
    @Application private val applicationContext: Context,
    @Application private val applicationScope: CoroutineScope,
    @Background private val backgroundScope: CoroutineScope,
    private val systemClock: SystemClock,
    dispatcher: BroadcastDispatcher,
    private val userActivityNotifier: UserActivityNotifier,
) : PowerRepository {

    override val dozeScreenState = MutableStateFlow(DozeScreenStateModel.UNKNOWN)

    override val isInteractive: StateFlow<Boolean> =
        if (SceneContainerFlag.isEnabled) {
            dispatcher
                .broadcastFlow(intentFilter) { _, _ -> manager.isInteractive }
                .onStart { emit(manager.isInteractive) }
                .stateIn(backgroundScope, SharingStarted.Eagerly, false)
        } else {
            conflatedCallbackFlow {
                    fun send() {
                        trySendWithFailureLogging(manager.isInteractive, TAG)
                    }

                    val receiver =
                        object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                send()
                            }
                        }

                    dispatcher.registerReceiver(receiver, intentFilter)
                    send()

                    awaitClose { dispatcher.unregisterReceiver(receiver) }
                }
                .stateIn(applicationScope, SharingStarted.Eagerly, false)
        }

    private val _wakefulnessEvents by lazy {
        MutableSharedFlow<WakefulnessModel>(
                replay = 1,
                extraBufferCapacity =
                    3, // Covers a full STARTED/FINISHED WAKING/GOING_TO_SLEEP cycle.
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
            .traceAs("wakefulness")
            .also { it.tryEmit(WakefulnessModel()) }
    }
    override val wakefulnessEvents by lazy { _wakefulnessEvents.asSharedFlow() }

    private val _wakefulness by lazy { MutableStateFlow(WakefulnessModel()).traceAs("wakefulness") }
    override val wakefulness =
        if (SceneContainerFlag.isEnabled && Flags.wakefulnessEventsSharedFlow()) {
            _wakefulnessEvents.stateIn(backgroundScope, SharingStarted.Eagerly, WakefulnessModel())
        } else {
            _wakefulness.asStateFlow()
        }

    @SuppressLint("SharedFlowCreation")
    override val powerButtonLaunchEvents =
        MutableSharedFlow<PowerButtonLaunchEvent>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override fun updateWakefulness(
        rawState: WakefulnessState,
        lastWakeReason: WakeSleepReason,
        lastSleepReason: WakeSleepReason,
        powerButtonLaunchGestureTriggered: Boolean,
        asleepOrWakingFromPreviouslyEnteredDevice: Boolean,
    ) {
        if (Flags.wakefulnessEventsSharedFlow()) {
            _wakefulnessEvents.tryEmit(
                WakefulnessModel(
                    rawState,
                    lastWakeReason,
                    lastSleepReason,
                    powerButtonLaunchGestureTriggered,
                    asleepOrWakingFromPreviouslyEnteredDevice,
                )
            )
        } else {
            _wakefulness.value =
                WakefulnessModel(
                    rawState,
                    lastWakeReason,
                    lastSleepReason,
                    powerButtonLaunchGestureTriggered,
                    asleepOrWakingFromPreviouslyEnteredDevice,
                )
        }
    }

    private val _screenPowerState =
        MutableStateFlow(ScreenPowerState.SCREEN_OFF).traceAs("screenPowerState")
    override val screenPowerState = _screenPowerState.asStateFlow()

    override fun setScreenPowerState(state: ScreenPowerState) {
        _screenPowerState.value = state
    }

    override fun onPowerButtonLaunchEvent(event: PowerButtonLaunchEvent) {
        powerButtonLaunchEvents.tryEmit(event)
    }

    override fun wakeUp(why: String, wakeReason: Int) {
        manager.wakeUp(
            systemClock.uptimeMillis(),
            wakeReason,
            "${applicationContext.packageName}:$why",
        )
    }

    @SuppressLint("MissingPermission")
    override fun userTouch(noChangeLights: Boolean) {
        val pmFlags = if (noChangeLights) PowerManager.USER_ACTIVITY_FLAG_NO_CHANGE_LIGHTS else 0
        if (Flags.bouncerUiRevamp()) {
            userActivityNotifier.notifyUserActivity(
                timeOfActivity = systemClock.uptimeMillis(),
                event = PowerManager.USER_ACTIVITY_EVENT_TOUCH,
                flags = pmFlags,
            )
        } else {
            manager.userActivity(
                systemClock.uptimeMillis(),
                PowerManager.USER_ACTIVITY_EVENT_TOUCH,
                pmFlags,
            )
        }
    }

    companion object {
        private const val TAG = "PowerRepository"
        private val intentFilter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
    }
}
