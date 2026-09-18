/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.internal.policy;

import android.content.Intent;
import com.android.internal.policy.IKeyguardDrawnCallback;
import com.android.internal.policy.IKeyguardDismissCallback;
import com.android.internal.policy.IKeyguardStateCallback;
import com.android.internal.policy.IKeyguardExitCallback;
import com.android.internal.policy.KeyguardState;

import android.os.Bundle;

oneway interface IKeyguardService {

    /**
     * Sets whether Keyguard is occluded by another window.
     *
     * @param isOccluded the new occluded state.
     */
    void setOccluded(boolean isOccluded);

    /**
     * Adds a callback for KeyguardService to report state changes.
     *
     * @param callback the callback to add.
     */
    void addStateMonitorCallback(in IKeyguardStateCallback callback);

    /**
     * Verifies whether Keyguard is unlocked and notifies the given callback.
     *
     * @param callback the callback to be informed about the result.
     */
    void verifyUnlock(in IKeyguardExitCallback callback);

    /**
     * Dismisses Keyguard, if it is currently shown.
     *
     * @param callback the callback to be informed about the result.
     * @param message the message that should be displayed in Keyguard.
     */
    void dismiss(in @nullable IKeyguardDismissCallback callback, in @nullable CharSequence message);

    /**
     * Called when dreaming has started.
     */
    void onDreamingStarted();

    /**
     * Called when dreaming has stopped.
     */
    void onDreamingStopped();

    /**
     * Called when the device has started going to sleep.
     *
     * @param pmSleepReason one of PowerManager.GO_TO_SLEEP_REASON_*, detailing the specific reason
     * we're going to sleep, such as GO_TO_SLEEP_REASON_POWER_BUTTON or GO_TO_SLEEP_REASON_TIMEOUT.
     */
    void onStartedGoingToSleep(int pmSleepReason);

    /**
     * Called when the device has finished going to sleep.
     *
     * @param pmSleepReason one of PowerManager.GO_TO_SLEEP_REASON_*, detailing the specific reason
     * we're going to sleep, such as GO_TO_SLEEP_REASON_POWER_BUTTON or GO_TO_SLEEP_REASON_TIMEOUT.
     * @param powerButtonLaunchGestureTriggered whether the power button double tap gesture was
     *                               triggered between {@link #onStartedGoingToSleep} and this
     *                               method; if it's been triggered, we shouldn't lock the device.
     */
    void onFinishedGoingToSleep(int pmSleepReason, boolean powerButtonLaunchGestureTriggered);

    /**
     * Called when the device has started waking up.

     * @param pmWakeReason one of PowerManager.WAKE_REASON_*, detailing the reason we're waking up,
     * such as WAKE_REASON_POWER_BUTTON or WAKE_REASON_GESTURE.
     * @param powerButtonLaunchGestureTriggered whether we're waking up due to a power button
     * double tap gesture.
     */
    void onStartedWakingUp(int pmWakeReason,  boolean powerButtonLaunchGestureTriggered);

    /**
     * Called when the device has finished waking up.
     */
    void onFinishedWakingUp();

    /**
    * Screen turning on reason: unknown
    */
    const int SCREEN_TURNING_ON_REASON_UNKNOWN = 0;

    /**
     * Screen turning on reason: the screen is turning on because of a display switch,
     * e.g. turning on the opposite screen when unfolding a foldable device
     */
    const int SCREEN_TURNING_ON_REASON_DISPLAY_SWITCH = 1;

    /**
     * Called when the device screen has started turning on.
     *
     * @param reason the reason for the screen turning on, must be one of
     *        IKeyguardService.SCREEN_TURNING_ON_REASON_*
     * @param callback the callback to be informed when SystemUI has finished preparations for
     *                 turning on the screen.
     */
    void onScreenTurningOn(int reason, in IKeyguardDrawnCallback callback);

    /**
     * Called when the device screen has finished turning on.
     */
    void onScreenTurnedOn();

    /**
     * Called when the device screen has started turning off.
     */
    void onScreenTurningOff();

    /**
     * Called when the device screen has finished turning off.
     */
    void onScreenTurnedOff();

    /**
     * Sets whether Keyguard is enabled. While disabled it is prevented from showing.
     *
     * <p>If disabled while it is currently showing, it will be hidden. If disabling lead to a hide,
     * re-enabling will show it again.
     *
     * @param enabled the new enabled state.
     */
    @UnsupportedAppUsage
    void setKeyguardEnabled(boolean enabled);

    /**
     * Called when the system is mostly done booting.
     */
    void onSystemReady();

    /**
     * Handle the inactivity timeout while the device is unlocked, after which Keyguard should be
     * shown.
     *
     * @param options the Keyguard timeout options.
     */
    @UnsupportedAppUsage
    void doKeyguardTimeout(in @nullable Bundle options);

    /**
     * Sets whether a user switch is in progress.
     *
     * @param switching the new switching state.
     */
    void setSwitchingUser(boolean switching);

    /**
     * Sets the current user (or user profile) to the given one.
     *
     * @param userId the ID of the new user.
     */
    void setCurrentUser(int userId);

    /**
     * Called when the system is fully done booting.
     */
    void onBootCompleted();

    /**
     * Notifies that the activity behind has now been drawn and it's safe to remove the wallpaper
     * and Keyguard flag.
     *
     * @param startTime the start time of the animation in uptime milliseconds
     * @param fadeoutDuration the duration of the exit animation, in milliseconds
     */
    void startKeyguardExitAnimation(long startTime, long fadeoutDuration);

    /**
     * Notifies Keyguard that the power key was pressed while locked and launched Home rather than
     * putting the device to sleep or waking up. Note that it's called only if the device is
     * interactive.
     */
    void onShortPowerPressedGoHome();

    /**
     * Notifies Keyguard that it needs to bring up a bouncer and then launch the intent as soon as
     * user unlocks the watch.
     *
     * @param intent the Intent to launch.
     */
    void dismissKeyguardToLaunch(in Intent intent);

    /**
     * Notifies Keyguard that a key was pressed while locked so Keyguard can handle it. Note that
     * it's called only if the device is interactive.
     *
     * @param keycode the key that was pressed.
     */
    void onSystemKeyPressed(int keycode);

    /**
     * Requests to show Keyguard immediately without locking the device. It will show regardless
     * whether a screen lock was configured or not (including if screen lock is SWIPE or NONE).
     */
    void showDismissibleKeyguard();

    /**
     * Restores the stored Keyguard state when the KeyguardService is connected. This is called on
     * the first connection, as well as subsequent connections (e.g. if the SystemUI process died
     * and restarted).
     *
     * @param state the Keyguard state to restore.
     * @param stateCallback the callback for KeyguardService to report state changes.
     * @param drawnCallback callback the callback to be informed when SystemUI has finished
     *                      preparations for turning on the screen.
     * @param timeoutRequested whether the Keyguard timeout was reached but not handled yet.
     * @param timeoutOptions the options for the Keyguard timeout.
     */
    void restoreKeyguardState(in KeyguardState state, in IKeyguardStateCallback stateCallback,
        in IKeyguardDrawnCallback drawnCallback, boolean timeoutRequested,
        in @nullable Bundle timeoutOptions);
}
