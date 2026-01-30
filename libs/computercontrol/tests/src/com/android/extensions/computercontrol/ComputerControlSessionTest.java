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

package com.android.extensions.computercontrol;

import static android.companion.virtual.computercontrol.ComputerControlSession.ACTION_GO_BACK;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.app.Activity;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualTouchEvent;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.IAccessibilityManager;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.extensions.computercontrol.input.KeyEvent;
import com.android.extensions.computercontrol.input.TouchEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executors;

@RequiresFlagsEnabled(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_ACCESS)
@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionTest {
    private static final int TIMEOUT_MS = 2_000;
    private static final String SESSION_NAME = "test";
    private static final String TARGET_PACKAGE = "com.android.foo";
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Rule public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.createDefault();

    @Mock IVirtualDisplayCallback mVirtualDisplayCallback;
    @Mock IComputerControlSession mIComputerControlSession;
    private ComputerControlSession mSession;
    private int mVirtualDisplayId;
    @Mock ComputerControlSession.StabilityListener mStabilityListener;
    @Mock ComputerControlSession.StabilityHintCallback mStabilityHintCallback;
    @Mock IAccessibilityManager mIAccessibilityManager;

    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() throws Exception {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        VirtualDisplay virtualDisplay = mVirtualDeviceRule.createManagedUnownedVirtualDisplay();
        mVirtualDisplayId = virtualDisplay.getDisplay().getDisplayId();
        when(mIComputerControlSession.getVirtualDisplayId()).thenReturn(mVirtualDisplayId);

        DisplayMetrics metrics = new DisplayMetrics();
        virtualDisplay.getDisplay().getMetrics(metrics);

        ComputerControlSession.Params params =
                new ComputerControlSession.Params.Builder(mContext)
                        .setDisplayDpi(metrics.densityDpi)
                        .setDisplayHeightPx(metrics.heightPixels)
                        .setDisplayWidthPx(metrics.widthPixels)
                        .setDisplaySurface(virtualDisplay.getSurface())
                        .setName(SESSION_NAME)
                        .build();

        AccessibilityManager accessibilityManager = new AccessibilityManager(
                mContext, mContext.getMainThreadHandler(), mIAccessibilityManager, 0, true);
        mSession = new ComputerControlSession(
                new android.companion.virtual.computercontrol.ComputerControlSession(
                        Display.INVALID_DISPLAY, mVirtualDisplayCallback, mIComputerControlSession),
                params, accessibilityManager);
        verify(mIAccessibilityManager).registerProxyForDisplay(any(), eq(mVirtualDisplayId));
        mSession.addStabilityHintCallback(TIMEOUT_MS / 2, mStabilityHintCallback);
    }

    @After
    public void tearDown() throws Exception {
        mSession.close();
        mMockitoSession.close();
    }

    @Test
    public void closeSession_closesSession() throws Exception {
        mSession.close();
        verify(mIAccessibilityManager).unregisterProxyForDisplay(mVirtualDisplayId);
        verify(mIComputerControlSession).close();
    }

    @Test
    public void closeMultipleTimes_closesOnce() throws Exception {
        mSession.close();
        mSession.close();
        mSession.close();
        verify(mIAccessibilityManager, times(1)).unregisterProxyForDisplay(mVirtualDisplayId);
        verify(mIComputerControlSession, times(1)).close();
    }

    @Test
    public void launchApplication_launchesApplication() throws Exception {
        mSession.launchApplication(TARGET_PACKAGE);
        verify(mIComputerControlSession).launchApplication(eq(TARGET_PACKAGE));
    }

    @Test
    public void tap_taps() throws Exception {
        mSession.tap(1, 2);
        verify(mIComputerControlSession).tap(eq(1), eq(2));
    }

    @Test
    public void swipe_swipes() throws Exception {
        mSession.swipe(1, 2, 3, 4);
        verify(mIComputerControlSession).swipe(eq(1), eq(2), eq(3), eq(4));
    }

    @Test
    public void longPress_longPresses() throws Exception {
        mSession.longPress(1, 2);
        verify(mIComputerControlSession).longPress(eq(1), eq(2));
    }

    @Test
    public void sendKeyEvent_sendsKeyEvent() throws Exception {
        KeyEvent event = new KeyEvent.Builder()
                                 .setKeyCode(android.view.KeyEvent.KEYCODE_A)
                                 .setAction(android.view.KeyEvent.ACTION_DOWN)
                                 .setEventTimeNanos(1234567890)
                                 .build();

        ArgumentCaptor<VirtualKeyEvent> captor = ArgumentCaptor.forClass(VirtualKeyEvent.class);
        mSession.sendKeyEvent(event);

        verify(mIComputerControlSession).sendKeyEvent(captor.capture());
        VirtualKeyEvent virtualKeyEvent = captor.getValue();
        assertThat(virtualKeyEvent.getKeyCode()).isEqualTo(event.getKeyCode());
        assertThat(virtualKeyEvent.getAction()).isEqualTo(event.getAction());
        assertThat(virtualKeyEvent.getEventTimeNanos()).isEqualTo(event.getEventTimeNanos());

        verify(mStabilityHintCallback, timeout(TIMEOUT_MS)).onStabilityHint(false);
    }

    @Test
    public void sendTouchEvent_sendsTouchEvent() throws Exception {
        TouchEvent event = new TouchEvent.Builder()
                                   .setX(123)
                                   .setY(234)
                                   .setAction(MotionEvent.ACTION_DOWN)
                                   .setPressure(255)
                                   .setMajorAxisSize(1)
                                   .setPointerId(1)
                                   .setToolType(MotionEvent.TOOL_TYPE_FINGER)
                                   .setEventTimeNanos(1234567890)
                                   .build();

        ArgumentCaptor<VirtualTouchEvent> captor = ArgumentCaptor.forClass(VirtualTouchEvent.class);
        mSession.sendTouchEvent(event);

        verify(mIComputerControlSession).sendTouchEvent(captor.capture());
        VirtualTouchEvent virtualTouchEvent = captor.getValue();
        assertThat(virtualTouchEvent.getX()).isEqualTo(event.getX());
        assertThat(virtualTouchEvent.getY()).isEqualTo(event.getY());
        assertThat(virtualTouchEvent.getAction()).isEqualTo(event.getAction());
        assertThat(virtualTouchEvent.getPressure()).isEqualTo(event.getPressure());
        assertThat(virtualTouchEvent.getMajorAxisSize()).isEqualTo(event.getMajorAxisSize());
        assertThat(virtualTouchEvent.getPointerId()).isEqualTo(event.getPointerId());
        assertThat(virtualTouchEvent.getToolType()).isEqualTo(event.getToolType());
        assertThat(virtualTouchEvent.getEventTimeNanos()).isEqualTo(event.getEventTimeNanos());

        verify(mStabilityHintCallback, timeout(TIMEOUT_MS)).onStabilityHint(false);
    }

    @Test
    public void createSession_launchActivity() {
        ComponentName componentName = new ComponentName(mContext, Activity.class);
        Intent intent = new Intent();
        intent.setComponent(componentName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mSession.startActivity(mContext, intent);
        mVirtualDeviceRule.waitAndAssertActivityResumed(componentName, mVirtualDisplayId);

        verify(mStabilityHintCallback, timeout(TIMEOUT_MS)).onStabilityHint(false);
    }

    @Test
    public void insertText_insertsText() throws Exception {
        mSession.insertText("test", true, true);

        verify(mIComputerControlSession).insertText(eq("test"), eq(true), eq(true));

        verify(mStabilityHintCallback, timeout(TIMEOUT_MS)).onStabilityHint(false);
    }

    @Test
    public void performAction_performsAction() throws Exception {
        mSession.performAction(ACTION_GO_BACK);

        verify(mIComputerControlSession).performAction(eq(ACTION_GO_BACK));

        verify(mStabilityHintCallback, timeout(TIMEOUT_MS)).onStabilityHint(false);
    }

    @Test
    public void setStabilityListener_withNullListener_throwsException() {
        assertThrows(NullPointerException.class, () -> mSession.setStabilityListener(
                Executors.newSingleThreadExecutor(), null));
    }

    @Test
    public void setStabilityListener_withNullExecutor_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mSession.setStabilityListener(null, mStabilityListener));
    }

    @Test
    public void setStabilityListener_setsStabilityListener() throws Exception {
        mSession.setStabilityListener(Executors.newSingleThreadExecutor(), mStabilityListener);
        verify(mIComputerControlSession).setStabilityListener(any());
    }

    @Test
    public void clearStabilityListener_clearsStabilityListener() throws Exception {
        mSession.clearStabilityListener();
        verify(mIComputerControlSession).setStabilityListener(eq(null));
    }
}
