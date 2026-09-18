package com.android.systemui.power.shared.model

import com.android.systemui.Flags
import com.android.systemui.keyguard.KeyguardService
import com.android.systemui.log.table.Diffable
import com.android.systemui.log.table.TableRowLogger
import com.android.systemui.scene.shared.flag.SceneContainerFlag

/**
 * Models whether the device is awake or asleep, along with information about why we're in that
 * state.
 */
class WakefulnessModel(
    /**
     * Internal-only wakefulness state, which we receive via [KeyguardService]. This is a more
     * granular state that tells us whether we've started or finished waking up or going to sleep.
     *
     * This distinction has historically been confusing - the display is on once we've "finished"
     * waking up, but we're still playing screen-on animations. Similarly, the screen off animation
     * is still playing even once we've "finished" going to sleep.
     *
     * Avoid using this whenever possible - [isAwake] and [isAsleep] should be sufficient for nearly
     * all use cases. If you need more granular information about a waking/sleeping transition, use
     * the [KeyguardTransitionInteractor].
     */
    val internalWakefulnessState: WakefulnessState = WakefulnessState.AWAKE,
    val lastWakeReason: WakeSleepReason = WakeSleepReason.OTHER,
    val lastSleepReason: WakeSleepReason = WakeSleepReason.OTHER,

    /**
     * Whether the power button double tap gesture was triggered since the last time went to sleep.
     * If this value is true while [isAsleep]=true, it means we'll be waking back up shortly. If it
     * is true while [isAwake]=true, it means we're awake because of the button gesture.
     *
     * This value remains true until the next time [isAsleep]=true, since it would otherwise be
     * totally arbitrary at what point we decide the gesture was no longer "triggered". Since a
     * sleep event is guaranteed to arrive prior to the next power button gesture (as the first tap
     * of the double tap always begins a sleep transition), this will always be reset to false prior
     * to a subsequent power gesture.
     */
    val powerButtonLaunchGestureTriggered: Boolean = false,
    private val asleepOrWakingFromPreviouslyEnteredDevice: Boolean = false,
) : Diffable<WakefulnessModel> {

    fun isAwake() =
        internalWakefulnessState == WakefulnessState.AWAKE ||
            internalWakefulnessState == WakefulnessState.STARTING_TO_WAKE

    fun isAwakeForAnimations() =
        if (Flags.wakefulnessForAnimations()) {
            internalWakefulnessState == WakefulnessState.AWAKE
        } else {
            isAwake()
        }

    fun isAsleep() = !isAwake()

    fun isAwakeFrom(wakeSleepReason: WakeSleepReason) =
        isAwake() && lastWakeReason == wakeSleepReason

    fun isAwakeFromTouch(): Boolean {
        return isAwake() && lastWakeReason.isTouch
    }

    fun isAsleepFrom(wakeSleepReason: WakeSleepReason) =
        isAsleep() && lastSleepReason == wakeSleepReason

    fun isAwakeOrAsleepFrom(reason: WakeSleepReason) = isAsleepFrom(reason) || isAwakeFrom(reason)

    fun isAwakeFromTapOrGesture(): Boolean {
        return isAwake() &&
            (lastWakeReason == WakeSleepReason.TAP || lastWakeReason == WakeSleepReason.GESTURE)
    }

    fun isAwakeFromMotionOrLift(): Boolean {
        return isAwake() &&
            (lastWakeReason == WakeSleepReason.MOTION || lastWakeReason == WakeSleepReason.LIFT)
    }

    /**
     * Whether we're STARTING_TO_SLEEP, ASLEEP, or STARTING_TO_WAKE, and the device was entered (per
     * [DeviceEntryInteractor] at the time of the most recent onStartedGoingToSleep call. Null if
     * value has not yet been set, or if we're fully AWAKE.
     *
     * This value is not valid if [SceneContainerFlag] is not enabled.
     *
     * See [PowerInteractor.emitPowerButtonLaunchEvent] for a detailed explanation of how this value
     * is used for the unlocked power launch gesture.
     */
    fun asleepOrWakingFromPreviouslyEnteredDevice(): Boolean {
        SceneContainerFlag.isUnexpectedlyInLegacyMode() // Concept of device "entered" is flexi-only
        return asleepOrWakingFromPreviouslyEnteredDevice
    }

    override fun logDiffs(prevVal: WakefulnessModel, row: TableRowLogger) {
        row.logChange(columnName = "wakefulness", value = toString())
    }

    override fun toString(): String {
        return "WakefulnessModel(" +
            "internalWakefulnessState=$internalWakefulnessState, " +
            "lastWakeReason=$lastWakeReason, " +
            "lastSleepReason=$lastSleepReason, " +
            "powerGesture=$powerButtonLaunchGestureTriggered, " +
            "asleepOrWakingFromPreviouslyEnteredDevice=$asleepOrWakingFromPreviouslyEnteredDevice" +
            ")"
    }
}
