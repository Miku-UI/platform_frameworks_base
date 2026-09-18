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

package com.android.systemui.power.domain.interactor

import android.os.PowerManager
import com.android.systemui.camera.CameraGestureHelper
import com.android.systemui.classifier.FalsingCollector
import com.android.systemui.classifier.FalsingCollectorActual
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.power.data.model.PowerButtonLaunchEvent
import com.android.systemui.power.data.repository.PowerRepository
import com.android.systemui.power.shared.model.DozeScreenStateModel
import com.android.systemui.power.shared.model.ScreenPowerState
import com.android.systemui.power.shared.model.WakeSleepReason
import com.android.systemui.power.shared.model.WakefulnessModel
import com.android.systemui.power.shared.model.WakefulnessState
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.statusbar.phone.ScreenOffAnimationController
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Hosts business logic for interacting with the power system. */
@SysUISingleton
class PowerInteractor
@Inject
constructor(
    private val repository: PowerRepository,
    @FalsingCollectorActual private val falsingCollector: FalsingCollector,
    private val screenOffAnimationController: ScreenOffAnimationController,
    private val statusBarStateController: StatusBarStateController,
    private val cameraGestureHelper: Provider<CameraGestureHelper?>,
    // Unused if Flexiglass is disabled.
    private val deviceEntryInteractor: Lazy<DeviceEntryInteractor?> = Lazy { null },
) {
    /** Whether the screen is on or off. */
    val isInteractive: StateFlow<Boolean> = repository.isInteractive

    /**
     * Whether we're awake or asleep, along with additional information about why we're awake/asleep
     * and whether the power button gesture has been triggered (a special case that affects
     * wakefulness).
     *
     * This is a StateFlow. Intermediate events may be dropped if the system is under high load or
     * the wakefulness changes quickly. If you absolutely need to receive all events in order, you
     * can collect from [detailedWakefulnessEvents].
     *
     * Unless you need to respond differently to different [WakeSleepReason]s, you should use
     * [isAwake].
     */
    val detailedWakefulness: StateFlow<WakefulnessModel> = repository.wakefulness

    /**
     * SharedFlow that emits all* wakefulness events in order. This should only be used by
     * collectors that absolutely need to receive all events - if you just need to know if we're
     * currently awake/asleep, collect from the [detailedWakefulness] StateFlow instead.
     * * This flow has a buffer size of 4, ensuring that one each of the wakefulness event
     *   enumeration will eventually be received (STARTED/FINISHED_WAKING_UP ->
     *   STARTED/FINISHED_GOING_TO_SLEEP, or the opposite order).
     */
    val detailedWakefulnessEvents: SharedFlow<WakefulnessModel> = repository.wakefulnessEvents

    /**
     * Whether we're awake (screen is on and responding to user touch) or asleep (screen is off, or
     * on AOD).
     *
     * Note: This is derived from a StateFlow. You are not guaranteed to receive each individual
     * change to wakefulness (for example, if the device very quickly goes to sleep and then wakes
     * back up, isAwake may simply remain true the entire time). If you need the intermediate events
     * collect from [detailedWakefulnessEvents].
     */
    val isAwake =
        repository.wakefulness
            .map { it.isAwake() }
            .distinctUntilChanged(checkEquivalentUnlessEmitDuplicatesUnderTest)

    /**
     * A stricter version of [isAwake] intended for triggering animations.
     *
     * This flow only emits `true` when the device is fully [WakefulnessState.AWAKE], excluding the
     * [WakefulnessState.STARTING_TO_WAKE] state.
     */
    val isAwakeForAnimations =
        repository.wakefulness
            .map { it.isAwakeForAnimations() }
            .distinctUntilChanged(checkEquivalentUnlessEmitDuplicatesUnderTest)

    /** Helper flow in case "isAsleep" reads better than "!isAwake". */
    val isAsleep = isAwake.map { !it }

    /** The physical on/off state of the display. */
    val screenPowerState: StateFlow<ScreenPowerState> = repository.screenPowerState

    /** The screen state, related to power and controlled by [DozeScreenState] */
    val dozeScreenState: StateFlow<DozeScreenStateModel> = repository.dozeScreenState.asStateFlow()

    /**
     * Emits when a double-tap power button launch gesture is detected, with additional information
     * about the entry status of the device when the gesture was originally initiated.
     *
     * Only valid with Flexiglass enabled.
     */
    val powerButtonLaunchEvents: Flow<PowerButtonLaunchEvent> =
        repository.powerButtonLaunchEvents.onStart {
            SceneContainerFlag.isUnexpectedlyInLegacyMode()
        }

    /**
     * Notifies the power interactor that a user touch happened.
     *
     * @param noChangeLights If true, does not cause the keyboard backlight to turn on because of
     *   this event. This is set when the power key is pressed. We want the device to stay on while
     *   the button is down, but we're about to turn off the screen so we don't want the keyboard
     *   backlight to turn on again. Otherwise the lights flash on and then off and it looks weird.
     */
    fun onUserTouch(noChangeLights: Boolean = false) =
        repository.userTouch(noChangeLights = noChangeLights)

    /**
     * Wakes up the device if the device was dozing.
     *
     * @param why a string explaining why we're waking the device for debugging purposes. Should be
     *   in SCREAMING_SNAKE_CASE.
     * @param wakeReason the PowerManager-based reason why we're waking the device.
     */
    fun wakeUpIfDozing(why: String, @PowerManager.WakeReason wakeReason: Int) {
        if (
            statusBarStateController.isDozing && screenOffAnimationController.allowWakeUpIfDozing()
        ) {
            repository.wakeUp(why, wakeReason)
            falsingCollector.onScreenOnFromTouch()
        }
    }

    /**
     * Wakes up the device if the device was dozing or going to sleep in order to display a
     * full-screen intent.
     */
    fun wakeUpForFullScreenIntent() {
        if (repository.wakefulness.value.isAsleep() || statusBarStateController.isDozing) {
            repository.wakeUp(why = FSI_WAKE_WHY, wakeReason = PowerManager.WAKE_REASON_APPLICATION)
        }
    }

    /**
     * Wakes up the device if dreaming with a screensaver.
     *
     * @param why a string explaining why we're waking the device for debugging purposes. Should be
     *   in SCREAMING_SNAKE_CASE.
     * @param wakeReason the PowerManager-based reason why we're waking the device.
     */
    fun wakeUpIfDreaming(why: String, @PowerManager.WakeReason wakeReason: Int) {
        if (statusBarStateController.isDreaming) {
            repository.wakeUp(why, wakeReason)
        }
    }

    /** Wakes up the device for the Side FPS acquisition event. */
    fun wakeUpForSideFingerprintAcquisition() {
        repository.wakeUp("SFPS_FP_ACQUISITION_STARTED", PowerManager.WAKE_REASON_BIOMETRIC)
    }

    /**
     * Called from [KeyguardService] to inform us that the device has started waking up. This is the
     * canonical source of wakefulness information for System UI. This method should not be called
     * from anywhere else.
     *
     * In tests, you should be able to use [setAwakeForTest] rather than calling this method
     * directly.
     */
    fun onStartedWakingUp(
        @PowerManager.WakeReason reason: Int,
        powerButtonLaunchGestureTriggeredOnWakeUp: Boolean,
    ) {
        // If the launch gesture was previously detected, either via onCameraLaunchGestureDetected
        // or onFinishedGoingToSleep(), carry that state forward. It will be reset by the next
        // onStartedGoingToSleep.
        val powerButtonLaunchGestureTriggered =
            !isPowerButtonGestureSuppressed() &&
                (powerButtonLaunchGestureTriggeredOnWakeUp ||
                    repository.wakefulness.value.powerButtonLaunchGestureTriggered)

        repository.updateWakefulness(
            rawState = WakefulnessState.STARTING_TO_WAKE,
            lastWakeReason = WakeSleepReason.fromPowerManagerWakeReason(reason),
            powerButtonLaunchGestureTriggered = powerButtonLaunchGestureTriggered,
        )
    }

    /**
     * Called from [KeyguardService] to inform us that the device has finished waking up. This is
     * the canonical source of wakefulness information for System UI. This method should not be
     * called from anywhere else.
     *
     * In tests, you should be able to use [setAwakeForTest] rather than calling this method
     * directly.
     */
    fun onFinishedWakingUp() {
        repository.updateWakefulness(
            rawState = WakefulnessState.AWAKE,
            // No longer asleep or STARTING_TO_WAKE, clear this value.
            asleepOrWakingFromPreviouslyEnteredDevice = false,
        )
    }

    /**
     * Called from [KeyguardService] to inform us that the device is going to sleep. This is the
     * canonical source of wakefulness information for System UI. This method should not be called
     * from anywhere else.
     *
     * In tests, you should be able to use [setAsleepForTest] rather than calling this method
     * directly.
     */
    fun onStartedGoingToSleep(@PowerManager.GoToSleepReason reason: Int) {
        repository.updateWakefulness(
            rawState = WakefulnessState.STARTING_TO_SLEEP,
            lastSleepReason = WakeSleepReason.fromPowerManagerSleepReason(reason),
            powerButtonLaunchGestureTriggered = false,
            asleepOrWakingFromPreviouslyEnteredDevice =
                if (SceneContainerFlag.isEnabled) {
                    deviceEntryInteractor.get()?.isDeviceEntered?.value ?: false
                } else {
                    false
                },
        )
    }

    /**
     * Called from [KeyguardService] to inform us that the device has gone to sleep. This is the
     * canonical source of wakefulness information for System UI. This method should not be called
     * from anywhere else.
     *
     * In tests, you should be able to use [setAsleepForTest] rather than calling this method
     * directly.
     */
    fun onFinishedGoingToSleep(powerButtonLaunchGestureTriggeredDuringSleep: Boolean) {
        // If the launch gesture was previously detected via onCameraLaunchGestureDetected, carry
        // that state forward. It will be reset by the next onStartedGoingToSleep.
        val powerButtonLaunchGestureTriggered =
            !isPowerButtonGestureSuppressed() &&
                (powerButtonLaunchGestureTriggeredDuringSleep ||
                    repository.wakefulness.value.powerButtonLaunchGestureTriggered)

        repository.updateWakefulness(
            rawState = WakefulnessState.ASLEEP,
            powerButtonLaunchGestureTriggered = powerButtonLaunchGestureTriggered,
        )
    }

    /**
     * There are two orthogonal aspects to the power launch gesture: whether it was started when the
     * device was awake vs. asleep, and whether the device was entered at the time (or not).
     *
     * If we're asleep, the sequence is as follows:
     * - First power button tap triggers onStartedWakingUp(gestureTriggered=false). The first tap of
     *   a double-tap definitionally cannot trigger the double tap gesture because 1 < 2.
     * - onFinishedWakingUp() is called. This method does not have a gestureTriggered param since
     *   it's not possible to double tap the button fast enough to trigger the gesture between
     *   onStarted/onFinishedWakingUp. asleepOrWakingFromPreviouslyEnteredDevice is cleared at this
     *   point since we're fully awake, not 'asleep or waking'.
     * - The second tap triggers onCameraLaunchGestureDetected() and we emit a
     *   PowerButtonLaunchEvent.
     *
     * Launches started while asleep will always be LAUNCH_FROM_NOT_ENTERED, and the value of
     * asleepOrWakingFromPreviouslyEnteredDevice is not read in this sequence.
     *
     * If we're awake, the sequence is as follows:
     * - First power button tap triggers onStartedGoingToSleep().
     * - Depending on the timing of the second tap, it triggers either:
     *     - onFinishedGoingToSleep(gestureTriggered=true). This happens with a very fast tap.
     *     - onStartedWakingUp(gestureTriggered=true). This happens with slower taps.
     * - In either case, onCameraLaunchGestureDetected() is immediately triggered. Here, we use
     *   asleepOrWakingFromPreviouslyEnteredDevice to determine whether the device was entered when
     *   the gesture started, and emit the appropriate PowerButtonLaunchEvent.
     * - onFinishedWakingUp() is called, which clears asleepOrWakingFromPreviouslyEnteredDevice.
     *
     * For launches while awake, the second tap will always arrive prior to onFinishedWakingUp since
     * the second tap is *why* we woke up in the first place. This is why it's safe to clear
     * asleepOrWakingFromPreviouslyEnteredDevice in onFinishedWakingUp.
     *
     * The tricky part here is that if we go to sleep from an entered device without triggering the
     * power gesture, asleepOrWakingFromPreviouslyEnteredDevice will remain true the entire time we
     * are asleep (until cleared in onFinishedWakingUp). This obviates the need to set a timeout for
     * the double-tap duration and clear the value when that elapses (which would introduce all
     * kinds of race conditions). It may seem like this would result in us going back to Gone if the
     * power gesture is triggered we're asleep and this value remains true. However, a gesture
     * started while asleep will always follow the "if we're asleep" path above, which does not use
     * (and then clears) asleepOrWakingFromPreviouslyEnteredDevice.
     */
    private fun emitPowerButtonLaunchEvent() {
        if (repository.wakefulness.value.asleepOrWakingFromPreviouslyEnteredDevice() == true) {
            repository.onPowerButtonLaunchEvent(PowerButtonLaunchEvent.LAUNCH_FROM_ENTERED)
        } else {
            repository.onPowerButtonLaunchEvent(PowerButtonLaunchEvent.LAUNCH_FROM_NOT_ENTERED)
        }
    }

    fun onScreenPowerStateUpdated(state: ScreenPowerState) {
        repository.setScreenPowerState(state)
    }

    fun onCameraLaunchGestureDetected() {
        if (!isPowerButtonGestureSuppressed()) {
            repository.updateWakefulness(
                powerButtonLaunchGestureTriggered = true,
                lastSleepReason = WakeSleepReason.POWER_BUTTON,
            )

            if (SceneContainerFlag.isEnabled) {
                // onCameraLaunchGestureDetected is called first for all gesture timings. Depending
                // on the timing, this may be followed by
                // onFinishedGoingToSleep(gestureTriggered=true) or
                // onStartedWakingUp(gestureTriggered=true), but that's not always true. In any
                // case, this is reliably the earliest signal, so emit the launch event here.
                emitPowerButtonLaunchEvent()
            }
        }
    }

    fun onWalletLaunchGestureDetected() {
        repository.updateWakefulness(powerButtonLaunchGestureTriggered = true)
    }

    suspend fun hydrateTableLogBuffer(tableLogBuffer: TableLogBuffer) {
        detailedWakefulness
            .logDiffsForTable(
                tableLogBuffer = tableLogBuffer,
                initialValue = detailedWakefulness.value,
            )
            .collect()
    }

    /**
     * Whether the power button gesture isn't allowed to launch anything even if a double tap is
     * detected.
     */
    private fun isPowerButtonGestureSuppressed(): Boolean {
        return cameraGestureHelper
            .get()
            ?.canCameraGestureBeLaunched(statusBarStateController.state) == false
    }

    companion object {
        private const val FSI_WAKE_WHY = "full_screen_intent"

        /**
         * If true, [isAwake] and [isAsleep] will emit the next value even if it's not distinct.
         * This is useful for setting up tests.
         */
        private var emitDuplicateWakefulnessValue = false

        /**
         * Returns whether old == new unless we want to emit duplicate values, in which case we
         * reset that flag and then return false.
         */
        private val checkEquivalentUnlessEmitDuplicatesUnderTest: (Boolean, Boolean) -> Boolean =
            { old, new ->
                if (emitDuplicateWakefulnessValue) {
                    emitDuplicateWakefulnessValue = false
                    false
                } else {
                    old == new
                }
            }

        /**
         * Helper method for tests to simulate the device waking up.
         *
         * If [forceEmit] is true, forces [isAwake] to emit true, even if the PowerInteractor in the
         * test was already awake. This is useful for the first setAwakeForTest call in a test,
         * since otherwise, tests would need to set the PowerInteractor asleep first to ensure
         * [isAwake] emits, which can cause superfluous interactions with mocks.
         *
         * This is also preferred to calling [onStartedWakingUp]/[onFinishedWakingUp] directly, as
         * we want to keep the started/finished concepts internal to keyguard as much as possible.
         */
        @JvmOverloads
        fun PowerInteractor.setAwakeForTest(
            @PowerManager.WakeReason reason: Int = PowerManager.WAKE_REASON_UNKNOWN,
            powerButtonGestureTriggered: Boolean = false,
            forceEmit: Boolean = false,
        ) {
            emitDuplicateWakefulnessValue = forceEmit

            this.onStartedWakingUp(
                reason = reason,
                powerButtonLaunchGestureTriggeredOnWakeUp = powerButtonGestureTriggered,
            )
            this.onFinishedWakingUp()
        }

        /**
         * Helper method for tests to simulate the device sleeping.
         *
         * If [forceEmit] is true, forces [isAsleep] to emit true, even if the PowerInteractor in
         * the test was already asleep. This is useful for the first setAsleepForTest call in a
         * test, since otherwise, tests would need to set the PowerInteractor awake first to ensure
         * [isAsleep] emits, but that can cause superfluous interactions with mocks.
         *
         * This is also preferred to calling [onStartedGoingToSleep]/[onFinishedGoingToSleep]
         * directly, as we want to keep the started/finished concepts internal to keyguard as much
         * as possible.
         */
        @JvmOverloads
        fun PowerInteractor.setAsleepForTest(
            @PowerManager.GoToSleepReason sleepReason: Int = PowerManager.GO_TO_SLEEP_REASON_MIN,
            powerButtonGestureTriggered: Boolean = false,
            forceEmit: Boolean = false,
        ) {
            emitDuplicateWakefulnessValue = forceEmit

            this.onStartedGoingToSleep(reason = sleepReason)
            this.onFinishedGoingToSleep(
                powerButtonLaunchGestureTriggeredDuringSleep = powerButtonGestureTriggered
            )
        }

        /** Helper method for tests to simulate the device screen state change event. */
        fun PowerInteractor.setScreenPowerState(screenPowerState: ScreenPowerState) {
            this.onScreenPowerStateUpdated(screenPowerState)
        }
    }
}
