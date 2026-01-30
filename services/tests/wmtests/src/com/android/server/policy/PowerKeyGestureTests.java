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
 */
package com.android.server.policy;

import static android.view.KeyEvent.KEYCODE_POWER;
import static android.view.KeyEvent.KEYCODE_VOLUME_UP;

import static com.android.server.policy.PhoneWindowManager.LONG_PRESS_POWER_ASSISTANT;
import static com.android.server.policy.PhoneWindowManager.LONG_PRESS_POWER_GLOBAL_ACTIONS;
import static com.android.server.policy.PhoneWindowManager.LONG_PRESS_POWER_GO_TO_SLEEP;
import static com.android.server.policy.PhoneWindowManager.POWER_MULTI_PRESS_TIMEOUT_MILLIS;
import static com.android.server.policy.PhoneWindowManager.SHORT_PRESS_POWER_DREAM_OR_SLEEP;
import static com.android.server.policy.PhoneWindowManager.SHORT_PRESS_POWER_GO_TO_SLEEP;

import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.view.Display;

import org.junit.Before;
import org.junit.Test;

/**
 * Test class for power key gesture.
 *
 * Build/Install/Run:
 *  atest WmTests:PowerKeyGestureTests
 */
@Presubmit
public class PowerKeyGestureTests extends ShortcutKeyTestBase {
    @Before
    public void setUp() {
        setUpPhoneWindowManager();
        mPhoneWindowManager.overrideStatusBarManagerInternal();
    }

    /**
     * Power single press to turn screen on/off.
     */
    @Test
    public void testPowerSinglePress() {
        mPhoneWindowManager.overrideShortPressOnPower(SHORT_PRESS_POWER_GO_TO_SLEEP);
        sendKey(KEYCODE_POWER);
        mPhoneWindowManager.assertPowerSleep();

        mPhoneWindowManager.moveTimeForward(POWER_MULTI_PRESS_TIMEOUT_MILLIS);

        // turn screen on when begin from non-interactive.
        mPhoneWindowManager.overrideDisplayState(Display.STATE_OFF);
        sendKey(KEYCODE_POWER);
        mPhoneWindowManager.assertPowerWakeUp();
        mPhoneWindowManager.assertNoPowerSleep();
    }

    /**
     * Power single press to start dreaming when so configured.
     */
    @Test
    public void testPowerSinglePressRequestsDream() {
        mPhoneWindowManager.overrideShortPressOnPower(SHORT_PRESS_POWER_DREAM_OR_SLEEP);
        mPhoneWindowManager.overrideCanStartDreaming(true);
        sendKey(KEYCODE_POWER);
        mPhoneWindowManager.assertDreamRequest();
        mPhoneWindowManager.overrideIsDreaming(true);
        mPhoneWindowManager.assertLockedAfterAppTransitionFinished();
    }

    @Test
    public void testAppTransitionFinishedCalledAfterDreamStoppedWillNotLockAgain() {
        mPhoneWindowManager.overrideShortPressOnPower(SHORT_PRESS_POWER_DREAM_OR_SLEEP);
        mPhoneWindowManager.overrideCanStartDreaming(true);
        sendKey(KEYCODE_POWER);
        mPhoneWindowManager.assertDreamRequest();
        mPhoneWindowManager.overrideIsDreaming(false);
        mPhoneWindowManager.assertDidNotLockAfterAppTransitionFinished();
    }

    /**
     * Power double-press to launch camera does not lock device when the single press behavior is to
     * dream.
     */
    @Test
    public void testPowerDoublePressWillNotLockDevice() {
        mPhoneWindowManager.overrideShortPressOnPower(SHORT_PRESS_POWER_DREAM_OR_SLEEP);
        mPhoneWindowManager.overrideCanStartDreaming(false);
        sendKey(KEYCODE_POWER);
        sendKey(KEYCODE_POWER);
        mPhoneWindowManager.assertDoublePowerLaunch();
        mPhoneWindowManager.assertDidNotLockAfterAppTransitionFinished();
    }

    /**
     * Power double press to trigger camera.
     */
    @Test
    public void testPowerDoublePress() {
        sendKey(KEYCODE_POWER);
        sendKey(KEYCODE_POWER);
        mPhoneWindowManager.assertDoublePowerLaunch();
    }

    /**
     * Power long press to show assistant or global actions.
     */
    @Test
    public void testPowerLongPress() {
        // Show assistant.
        mPhoneWindowManager.overrideLongPressOnPower(LONG_PRESS_POWER_ASSISTANT);
        sendKey(KEYCODE_POWER, SingleKeyGestureDetector.sDefaultLongPressTimeout);
        mPhoneWindowManager.assertSearchManagerLaunchAssist();

        mPhoneWindowManager.moveTimeForward(POWER_MULTI_PRESS_TIMEOUT_MILLIS);

        // Show global actions.
        mPhoneWindowManager.overrideLongPressOnPower(LONG_PRESS_POWER_GLOBAL_ACTIONS);
        sendKey(KEYCODE_POWER, SingleKeyGestureDetector.sDefaultLongPressTimeout);
        mPhoneWindowManager.assertShowGlobalActionsCalled();
    }

    /**
     * Power long press to go to sleep (doze).
     */
    @Test
    public void testPowerLongPressGoToSleep() {
        mPhoneWindowManager.overrideLongPressOnPower(LONG_PRESS_POWER_GO_TO_SLEEP);
        sendKey(KEYCODE_POWER, SingleKeyGestureDetector.sDefaultLongPressTimeout);
        mPhoneWindowManager.assertPowerSleep();
    }

    /**
     * Ignore power press if combination key already triggered.
     */
    @Test
    public void testIgnoreSinglePressWhenCombinationKeyTriggered() {
        sendKeyCombination(new int[]{KEYCODE_POWER, KEYCODE_VOLUME_UP}, 0);
        mPhoneWindowManager.assertNoPowerSleep();
    }

    /**
     * When a phone call is active, and INCALL_POWER_BUTTON_BEHAVIOR_HANGUP is enabled, then the
     * power button should only stop phone call. The screen should not be turned off (power sleep
     * should not be activated).
     */
    @Test
    public void testIgnoreSinglePressWhenEndCall() {
        mPhoneWindowManager.overrideIncallPowerBehavior(
                Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_HANGUP);
        sendKey(KEYCODE_POWER);
        mPhoneWindowManager.assertNoPowerSleep();
    }
}
