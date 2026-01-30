/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_A;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_POWER;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.hardware.input.Flags.FLAG_ABORT_SLOW_MULTI_PRESS;
import static com.android.server.policy.SingleKeyGestureEvent.ACTION_CANCEL;
import static com.android.server.policy.SingleKeyGestureEvent.ACTION_COMPLETE;
import static com.android.server.policy.SingleKeyGestureEvent.ACTION_START;
import static com.android.server.policy.SingleKeyGestureEvent.SINGLE_KEY_GESTURE_TYPE_LONG_PRESS;
import static com.android.server.policy.SingleKeyGestureEvent.SINGLE_KEY_GESTURE_TYPE_PRESS;
import static com.android.server.policy.SingleKeyGestureEvent.SINGLE_KEY_GESTURE_TYPE_VERY_LONG_PRESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test class for {@link SingleKeyGestureDetector}.
 *
 * Build/Install/Run:
 *  atest WmTests:SingleKeyGestureTests
 */
@Presubmit
public class SingleKeyGestureTests {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private SingleKeyGestureDetector mDetector;

    private int mMaxMultiPressCount = 3;
    private int mExpectedMultiPressCount = 2;

    private CountDownLatch mShortPressed = new CountDownLatch(1);
    private CountDownLatch mLongPressed = new CountDownLatch(1);
    private CountDownLatch mVeryLongPressed = new CountDownLatch(1);
    private CountDownLatch mMultiPressed = new CountDownLatch(1);
    private BlockingQueue<KeyUpData> mKeyUpQueue = new LinkedBlockingQueue<>();
    private RandomKeyRule mRandomGestureRule = new RandomKeyRule(KEYCODE_A);
    private final Instrumentation mInstrumentation = getInstrumentation();
    private final Context mContext = mInstrumentation.getTargetContext();
    private long mWaitTimeout;
    private long mLongPressTime;
    private long mVeryLongPressTime;

    // Allow press from non interactive mode.
    private boolean mAllowNonInteractiveForPress = true;
    private boolean mAllowNonInteractiveForLongPress = true;

    private boolean mLongPressOnPowerBehavior = true;
    private boolean mVeryLongPressOnPowerBehavior = true;
    private boolean mLongPressOnBackBehavior = false;

    @Before
    public void setUp() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mDetector = SingleKeyGestureDetector.get(mContext, Looper.myLooper());
                    initSingleKeyGestureRules();
                });

        mWaitTimeout = SingleKeyGestureDetector.MULTI_PRESS_TIMEOUT + 50;
        mLongPressTime = SingleKeyGestureDetector.sDefaultLongPressTimeout + 50;
        mVeryLongPressTime = SingleKeyGestureDetector.sDefaultVeryLongPressTimeout + 50;
    }

    private void initSingleKeyGestureRules() {
        // Similar to current POWER key rules defined in PhoneWindowManager
        mDetector.addRule(
                new SingleKeyGestureDetector.SingleKeyRule(KEYCODE_POWER) {
                    @Override
                    boolean supportLongPress() {
                        return mLongPressOnPowerBehavior;
                    }

                    @Override
                    boolean supportVeryLongPress() {
                        return mVeryLongPressOnPowerBehavior;
                    }

                    @Override
                    int getMaxMultiPressCount() {
                        return mMaxMultiPressCount;
                    }

                    @Override
                    void onKeyGesture(@NonNull SingleKeyGestureEvent event) {
                        final int pressCount = event.getPressCount();
                        if (event.getAction() != ACTION_COMPLETE) {
                            return;
                        }
                        switch (event.getType()) {
                            case SINGLE_KEY_GESTURE_TYPE_PRESS:
                                if (event.getPressCount() > 1) {
                                    onMultiPress(pressCount);
                                } else {
                                    onPress();
                                }
                                break;
                            case SINGLE_KEY_GESTURE_TYPE_LONG_PRESS:
                                onLongPress();
                                break;
                            case SINGLE_KEY_GESTURE_TYPE_VERY_LONG_PRESS:
                                onVeryLongPress();
                                break;
                        }
                    }

                    private void onPress() {
                        if (mDetector.beganFromNonInteractive() && !mAllowNonInteractiveForPress) {
                            return;
                        }
                        mShortPressed.countDown();
                    }

                    private void onLongPress() {
                        if (mDetector.beganFromNonInteractive()
                                && !mAllowNonInteractiveForLongPress) {
                            return;
                        }
                        mLongPressed.countDown();
                    }

                    private void onVeryLongPress() {
                        mVeryLongPressed.countDown();
                    }

                    private void onMultiPress(int count) {
                        if (mDetector.beganFromNonInteractive() && !mAllowNonInteractiveForPress) {
                            return;
                        }
                        mMultiPressed.countDown();
                        assertTrue(mMaxMultiPressCount >= count);
                        assertEquals(mExpectedMultiPressCount, count);
                    }

                    @Override
                    void onKeyUp(int multiPressCount, KeyEvent event) {
                        mKeyUpQueue.add(new KeyUpData(KEYCODE_POWER, multiPressCount));
                    }
                });

        // Similar to current POWER key rules defined in PhoneWindowManager
        mDetector.addRule(
                new SingleKeyGestureDetector.SingleKeyRule(KEYCODE_BACK) {
                    @Override
                    boolean supportLongPress() {
                        return mLongPressOnBackBehavior;
                    }

                    @Override
                    int getMaxMultiPressCount() {
                        return mMaxMultiPressCount;
                    }

                    @Override
                    void onKeyGesture(@NonNull SingleKeyGestureEvent event) {
                        final long eventTime = event.getEventTime();
                        final int displayId = event.getDisplayId();
                        final int pressCount = event.getPressCount();
                        if (event.getAction() != ACTION_COMPLETE) {
                            return;
                        }
                        switch (event.getType()) {
                            case SINGLE_KEY_GESTURE_TYPE_PRESS:
                                if (event.getPressCount() > 1) {
                                    onMultiPress(pressCount);
                                } else {
                                    onPress();
                                }
                                break;
                            case SINGLE_KEY_GESTURE_TYPE_LONG_PRESS:
                                onLongPress();
                                break;
                        }
                    }

                    private void onPress() {
                        if (mDetector.beganFromNonInteractive() && !mAllowNonInteractiveForPress) {
                            return;
                        }
                        mShortPressed.countDown();
                    }

                    private void onMultiPress(int count) {
                        if (mDetector.beganFromNonInteractive() && !mAllowNonInteractiveForPress) {
                            return;
                        }
                        mMultiPressed.countDown();
                        assertTrue(mMaxMultiPressCount >= count);
                        assertEquals(mExpectedMultiPressCount, count);
                    }

                    @Override
                    void onKeyUp(int multiPressCount, KeyEvent event) {
                        mKeyUpQueue.add(new KeyUpData(KEYCODE_BACK, multiPressCount));
                    }

                    private void onLongPress() {
                        mLongPressed.countDown();
                    }
                });

        mDetector.addRule(mRandomGestureRule);
    }

    private static class KeyUpData {
        public final int keyCode;
        public final int pressCount;

        KeyUpData(int keyCode, int pressCount) {
            this.keyCode = keyCode;
            this.pressCount = pressCount;
        }
    }

    private void pressKey(int keyCode, long pressTime) {
        pressKey(keyCode, pressTime, true /* interactive */);
    }

    private void pressKey(int keyCode, long pressTime, boolean interactive) {
        pressKey(keyCode, pressTime, interactive, false /* defaultDisplayOn */);
    }

    private void pressKey(
            int keyCode, long pressTime, boolean interactive, boolean defaultDisplayOn) {
        long eventTime = SystemClock.uptimeMillis();
        final KeyEvent keyDown =
                new KeyEvent(
                        eventTime,
                        eventTime,
                        ACTION_DOWN,
                        keyCode,
                        0 /* repeat */,
                        0 /* metaState */);
        mDetector.interceptKey(keyDown, interactive, defaultDisplayOn);

        // keep press down.
        try {
            Thread.sleep(pressTime);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        eventTime += pressTime;
        final KeyEvent keyUp =
                new KeyEvent(
                        eventTime,
                        eventTime,
                        ACTION_UP,
                        keyCode,
                        0 /* repeat */,
                        0 /* metaState */);

        mDetector.interceptKey(keyUp, interactive, defaultDisplayOn);
    }

    @Test
    public void testShortPress() throws InterruptedException {
        pressKey(KEYCODE_POWER, 0 /* pressTime */);
        assertTrue(mShortPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testLongPress() throws InterruptedException {
        pressKey(KEYCODE_POWER, mLongPressTime);
        assertTrue(mLongPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testVeryLongPress() throws InterruptedException {
        pressKey(KEYCODE_POWER, mVeryLongPressTime);
        assertTrue(mVeryLongPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
    }

    @Test
    @EnableFlags(FLAG_ABORT_SLOW_MULTI_PRESS)
    public void testMultipress_noLongPressBehavior_longPressCancelsMultiPress()
            throws InterruptedException {
        mLongPressOnPowerBehavior = false;

        pressKey(KEYCODE_POWER, 0 /* pressTime */);
        pressKey(KEYCODE_POWER, mLongPressTime /* pressTime */);

        assertFalse(mMultiPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
    }

    @Test
    @EnableFlags(FLAG_ABORT_SLOW_MULTI_PRESS)
    public void testMultipress_noVeryLongPressBehavior_veryLongPressCancelsMultiPress()
            throws InterruptedException {
        mLongPressOnPowerBehavior = false;
        mVeryLongPressOnPowerBehavior = false;

        pressKey(KEYCODE_POWER, 0 /* pressTime */);
        pressKey(KEYCODE_POWER, mVeryLongPressTime /* pressTime */);

        assertFalse(mMultiPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
    }

    @Test
    @DisableFlags(FLAG_ABORT_SLOW_MULTI_PRESS)
    public void testMultipress_flagDisabled_noLongPressBehavior_longPressDoesNotCancelMultiPress()
            throws InterruptedException {
        mLongPressOnPowerBehavior = false;
        mExpectedMultiPressCount = 2;

        pressKey(KEYCODE_POWER, 0 /* pressTime */);
        pressKey(KEYCODE_POWER, mLongPressTime /* pressTime */);

        assertTrue(mMultiPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testMultiPress() throws InterruptedException {
        // Double presses.
        mExpectedMultiPressCount = 2;
        pressKey(KEYCODE_POWER, 0 /* pressTime */);
        pressKey(KEYCODE_POWER, 0 /* pressTime */);
        assertTrue(mMultiPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));

        // Triple presses.
        mExpectedMultiPressCount = 3;
        mMultiPressed = new CountDownLatch(1);
        pressKey(KEYCODE_POWER, 0 /* pressTime */);
        pressKey(KEYCODE_POWER, 0 /* pressTime */);
        pressKey(KEYCODE_POWER, 0 /* pressTime */);
        assertTrue(mMultiPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testOnKeyUp() throws InterruptedException {
        pressKey(KEYCODE_POWER, 0 /* pressTime */);

        verifyKeyUpData(KEYCODE_POWER, 1 /* expectedMultiPressCount */);
    }

    private void verifyKeyUpData(int expectedKeyCode, int expectedMultiPressCount)
            throws InterruptedException {
        KeyUpData keyUpData = mKeyUpQueue.poll(mWaitTimeout, TimeUnit.MILLISECONDS);
        assertNotNull(keyUpData);
        assertEquals(expectedKeyCode, keyUpData.keyCode);
        assertEquals(expectedMultiPressCount, keyUpData.pressCount);
    }

    @Test
    public void testNonInteractive() throws InterruptedException {
        // Disallow short press behavior from non interactive.
        mAllowNonInteractiveForPress = false;
        pressKey(KEYCODE_POWER, 0 /* pressTime */, false /* interactive */);
        assertFalse(mShortPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));

        // Allow long press behavior from non interactive.
        pressKey(KEYCODE_POWER, mLongPressTime, false /* interactive */);
        assertTrue(mLongPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testShortPress_Pressure() throws InterruptedException {
        final HandlerThread handlerThread =
                new HandlerThread("testInputReader", Process.THREAD_PRIORITY_DISPLAY);
        handlerThread.start();
        Handler newHandler = new Handler(handlerThread.getLooper());
        mMaxMultiPressCount = 1; // Will trigger short press when event up.
        try {
            // To make sure we won't get any crash while panic pressing keys.
            for (int i = 0; i < 100; i++) {
                mShortPressed = new CountDownLatch(2);
                newHandler.runWithScissors(
                        () -> {
                            pressKey(KEYCODE_POWER, 0 /* pressTime */);
                            pressKey(KEYCODE_BACK, 0 /* pressTime */);
                        },
                        mWaitTimeout);
                assertTrue(mShortPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
            }
        } finally {
            handlerThread.quitSafely();
        }
    }

    @Test
    public void testMultiPress_Pressure() throws InterruptedException {
        final HandlerThread handlerThread =
                new HandlerThread("testInputReader", Process.THREAD_PRIORITY_DISPLAY);
        handlerThread.start();
        Handler newHandler = new Handler(handlerThread.getLooper());
        try {
            // To make sure we won't get any unexpected multi-press count.
            for (int i = 0; i < 5; i++) {
                mMultiPressed = new CountDownLatch(1);
                mShortPressed = new CountDownLatch(1);
                newHandler.runWithScissors(
                        () -> {
                            pressKey(KEYCODE_POWER, 0 /* pressTime */);
                            pressKey(KEYCODE_POWER, 0 /* pressTime */);
                        },
                        mWaitTimeout);
                assertTrue(mMultiPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));

                newHandler.runWithScissors(
                        () -> pressKey(KEYCODE_POWER, 0 /* pressTime */), mWaitTimeout);
                assertTrue(mShortPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
            }
        } finally {
            handlerThread.quitSafely();
        }
    }

    @Test
    public void testOnKeyUp_Pressure() throws InterruptedException {
        final HandlerThread handlerThread =
                new HandlerThread("testInputReader", Process.THREAD_PRIORITY_DISPLAY);
        handlerThread.start();
        Handler newHandler = new Handler(handlerThread.getLooper());
        try {
            // To make sure we won't get any unexpected multi-press count.
            for (int i = 0; i < 5; i++) {
                newHandler.runWithScissors(
                        () -> {
                            pressKey(KEYCODE_POWER, 0 /* pressTime */);
                            pressKey(KEYCODE_POWER, 0 /* pressTime */);
                        },
                        mWaitTimeout);
                newHandler.runWithScissors(
                        () -> pressKey(KEYCODE_BACK, 0 /* pressTime */), mWaitTimeout);

                verifyKeyUpData(KEYCODE_POWER, 1 /* expectedMultiPressCount */);
                verifyKeyUpData(KEYCODE_POWER, 2 /* expectedMultiPressCount */);
                verifyKeyUpData(KEYCODE_BACK, 1 /* expectedMultiPressCount */);
            }
        } finally {
            handlerThread.quitSafely();
        }
    }

    @Test
    public void testUpdateRule() throws InterruptedException {
        // Power key rule doesn't allow the long press gesture.
        mLongPressOnPowerBehavior = false;
        pressKey(KEYCODE_POWER, mLongPressTime);
        assertFalse(mLongPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));

        // Back key rule allows the long press gesture.
        mLongPressOnBackBehavior = true;
        pressKey(KEYCODE_BACK, mLongPressTime);
        assertTrue(mLongPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testAddRemove() throws InterruptedException {
        final SingleKeyGestureDetector.SingleKeyRule rule =
                new SingleKeyGestureDetector.SingleKeyRule(KEYCODE_POWER) {
                    @Override
                    void onKeyGesture(@NonNull SingleKeyGestureEvent event) {
                        if (event.getType() == SINGLE_KEY_GESTURE_TYPE_PRESS
                                && event.getPressCount() == 1) {
                            mShortPressed.countDown();
                        }
                    }
                };

        mDetector.removeRule(rule);
        pressKey(KEYCODE_POWER, 0 /* pressTime */);
        assertFalse(mShortPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));

        mDetector.addRule(rule);
        pressKey(KEYCODE_POWER, 0 /* pressTime */);
        assertTrue(mShortPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
    }

    // Verify short press should not be triggered if no very long press behavior defined but the
    // press time exceeded the very long press timeout.
    @Test
    public void testTimeoutExceedVeryLongPress() throws InterruptedException {
        mVeryLongPressOnPowerBehavior = false;

        pressKey(KEYCODE_POWER, mVeryLongPressTime + 50);
        assertTrue(mLongPressed.await(mWaitTimeout, TimeUnit.MILLISECONDS));
        assertEquals(mVeryLongPressed.getCount(), 1);
        assertEquals(mShortPressed.getCount(), 1);
    }

    @Test
    public void testRandomRuleLongPress() throws InterruptedException {
        // The current flow of events based on implementation is:
        // - long-press(START)
        // - very long press (START)
        // - long press(COMPLETE)
        // - very long press(CANCEL)
        pressKey(KEYCODE_A, mLongPressTime + 50);

        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_LONG_PRESS, ACTION_START);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_VERY_LONG_PRESS,
                ACTION_START);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_LONG_PRESS, ACTION_COMPLETE);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_VERY_LONG_PRESS,
                ACTION_CANCEL);
    }

    @Test
    public void testRandomRuleVeryLongPress() throws InterruptedException {
        // The current flow of events based on implementation is:
        // - long-press(START)
        // - very long press (START)
        // - long press(COMPLETE)
        // - very long press(COMPLETE)
        pressKey(KEYCODE_A, mVeryLongPressTime + 50);

        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_LONG_PRESS, ACTION_START);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_VERY_LONG_PRESS,
                ACTION_START);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_LONG_PRESS, ACTION_COMPLETE);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_VERY_LONG_PRESS,
                ACTION_COMPLETE);
    }

    @Test
    public void testRandomRuleShortPress() throws InterruptedException {
        // The current flow of events based on implementation is:
        // - long-press(START)
        // - very long press (START)
        // - long press(CANCEL)
        // - very long press(CANCEL)
        // - 1-press(START)
        // - 1-press(COMPLETE)
        pressKey(KEYCODE_A, 0);

        // Long press and very long press gestures are started on key down
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_LONG_PRESS, ACTION_START);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_VERY_LONG_PRESS,
                ACTION_START);

        // Long press and very long press gestures are cancelled on key up (duration < thresholds)
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_LONG_PRESS, ACTION_CANCEL);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_VERY_LONG_PRESS,
                ACTION_CANCEL);

        // On key up start single press
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_PRESS,
                ACTION_START, /* pressCount = */1);
        // After waiting for multi-press timeout single press gesture is completed
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_PRESS,
                ACTION_COMPLETE, /* pressCount = */1);
    }

    @Test
    public void testRandomRuleDoublePress() throws InterruptedException {
        // The current flow of events based on implementation is:
        // - long-press(START)
        // - very long press (START)
        // - long press(CANCEL)
        // - very long press(CANCEL)
        // - 1-press(START)
        // - 1-press(CANCEL)
        // - 2-press(START)
        // - 2-press(COMPLETE)
        pressKey(KEYCODE_A, 0);
        pressKey(KEYCODE_A, 0);

        // Long press and very long press gestures are started on key down and cancelled on key up
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_LONG_PRESS, ACTION_START);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_VERY_LONG_PRESS,
                ACTION_START);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_LONG_PRESS, ACTION_CANCEL);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_VERY_LONG_PRESS,
                ACTION_CANCEL);

        // First press will start single press gesture
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_PRESS,
                ACTION_START, /* pressCount = */1);

        // Single press is cancelled on second key down
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_PRESS,
                ACTION_CANCEL, /* pressCount = */1);

        // On second key up start double press
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_PRESS,
                ACTION_START, /* pressCount = */2);
        // After waiting for multi-press timeout double press gesture is completed
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_PRESS,
                ACTION_COMPLETE, /* pressCount = */2);
    }

    @Test
    public void testRandomRuleTriplePress() throws InterruptedException {
        // The current flow of events based on implementation is:
        // - long-press(START)
        // - very long press (START)
        // - long press(CANCEL)
        // - very long press(CANCEL)
        // - 1-press(START)
        // - 1-press(CANCEL)
        // - 2-press(START)
        // - 2-press(CANCEL)
        // - 3-press(COMPLETE)
        pressKey(KEYCODE_A, 0);
        pressKey(KEYCODE_A, 0);
        pressKey(KEYCODE_A, 0);

        // Long press and very long press gestures are started on key down and cancelled on key up
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_LONG_PRESS, ACTION_START);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_VERY_LONG_PRESS,
                ACTION_START);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_LONG_PRESS, ACTION_CANCEL);
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_VERY_LONG_PRESS,
                ACTION_CANCEL);

        // First press will start single press gesture
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_PRESS,
                ACTION_START, /* pressCount = */1);

        // Single press is cancelled on second key down
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_PRESS,
                ACTION_CANCEL, /* pressCount = */1);

        // On second key up start double press
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_PRESS,
                ACTION_START, /* pressCount = */2);
        // Double press is cancelled on third key down
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_PRESS,
                ACTION_CANCEL, /* pressCount = */2);

        // Triple press is completed on third key down (since max press count is 3 no need to wait)
        mRandomGestureRule.assertEventReceived(SINGLE_KEY_GESTURE_TYPE_PRESS,
                ACTION_COMPLETE, /* pressCount = */3);
    }

    private class RandomKeyRule extends SingleKeyGestureDetector.SingleKeyRule {

        private final BlockingQueue<SingleKeyGestureEvent> mEvents = new LinkedBlockingQueue<>();

        private final int mKeyCode;

        RandomKeyRule(int keyCode) {
            super(keyCode);
            mKeyCode = keyCode;
        }

        @Override
        boolean supportLongPress() {
            return true;
        }

        @Override
        boolean supportVeryLongPress() {
            return true;
        }

        @Override
        int getMaxMultiPressCount() {
            return mMaxMultiPressCount;
        }

        @Override
        long getLongPressTimeoutMs() {
            return mLongPressTime;
        }

        @Override
        long getVeryLongPressTimeoutMs() {
            return mVeryLongPressTime;
        }

        @Override
        void onKeyGesture(@NonNull SingleKeyGestureEvent event) {
            if (event.getKeyCode() != mKeyCode) {
                throw new IllegalArgumentException(
                        "Rule generated a gesture for " + KeyEvent.keyCodeToString(
                                event.getKeyCode()) + " but the rule was made for "
                                + KeyEvent.keyCodeToString(mKeyCode));
            }
            mEvents.add(event);
        }

        @Nullable
        SingleKeyGestureEvent getEvent() throws InterruptedException {
            return mEvents.poll(500, TimeUnit.MILLISECONDS);
        }

        void assertEventReceived(int type, int action) throws InterruptedException {
            SingleKeyGestureEvent event = getEvent();
            assertNotNull(event);
            assertEquals("Type mismatch", type, event.getType());
            assertEquals("Action mismatch", action, event.getAction());
        }

        void assertEventReceived(int type, int action, int pressCount) throws InterruptedException {
            SingleKeyGestureEvent event = getEvent();
            assertNotNull(event);
            assertEquals("Type mismatch", type, event.getType());
            assertEquals("Action mismatch", action, event.getAction());
            assertEquals("Count mismatch", pressCount, event.getPressCount());
        }
    }
}
