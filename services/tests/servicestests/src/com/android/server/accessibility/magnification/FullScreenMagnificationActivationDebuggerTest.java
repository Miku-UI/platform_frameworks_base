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

package com.android.server.accessibility.magnification;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Display;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.accessibility.AccessibilityManagerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class FullScreenMagnificationActivationDebuggerTest {

    private static final String DEBUG_ACTIVATE_ZOOM =
            "com.android.server.accessibility.magnification.FULLSCREEN_MAG_ACTIVATE";
    private static final String DEBUG_RESET_ZOOM =
            "com.android.server.accessibility.magnification.FULLSCREEN_MAG_RESET";

    private Context mMockContext;
    private FullScreenMagnificationController mMockController;
    private FullScreenMagnificationActivationDebugger mDebugger;

    @Before
    public void setUp() {
        mMockContext = mock(Context.class);
        mMockController = mock(FullScreenMagnificationController.class);
        mDebugger = new FullScreenMagnificationActivationDebugger(mMockContext, mMockController);
    }

    @After
    public void tearDown() {
        mDebugger = null;
    }

    @Test
    public void testRegisterIfNecessary() {
        mDebugger.registerIfNecessary();

        ArgumentCaptor<IntentFilter> intentFilterCaptor =
                ArgumentCaptor.forClass(IntentFilter.class);
        verify(mMockContext).registerReceiver(
                any(BroadcastReceiver.class),
                intentFilterCaptor.capture(),
                eq(Context.RECEIVER_EXPORTED));

        IntentFilter intentFilter = intentFilterCaptor.getValue();
        assertTrue(intentFilter.hasAction(DEBUG_ACTIVATE_ZOOM));
        assertTrue(intentFilter.hasAction(DEBUG_RESET_ZOOM));
    }

    @Test
    public void testRegisterIfNecessary_alreadyRegistered() {
        mDebugger.registerIfNecessary();
        verify(mMockContext).registerReceiver(
                any(BroadcastReceiver.class),
                any(IntentFilter.class),
                eq(Context.RECEIVER_EXPORTED));

        Mockito.reset(mMockContext);
        // Register again should not call registerReceiver again.
        mDebugger.registerIfNecessary();
        verify(mMockContext, never()).registerReceiver(
                any(BroadcastReceiver.class),
                any(IntentFilter.class),
                eq(Context.RECEIVER_EXPORTED));
    }

    @Test
    public void testUnregister() {
        mDebugger.registerIfNecessary();
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockContext).registerReceiver(
                receiverCaptor.capture(),
                any(IntentFilter.class),
                eq(Context.RECEIVER_EXPORTED));

        mDebugger.unregister();
        verify(mMockContext).unregisterReceiver(receiverCaptor.getValue());
    }
    @Test
    public void onReceive_activateZoom_callsController() {
        mDebugger.registerIfNecessary();
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockContext).registerReceiver(
                receiverCaptor.capture(),
                any(IntentFilter.class),
                eq(Context.RECEIVER_EXPORTED));

        Intent intent = new Intent(DEBUG_ACTIVATE_ZOOM);
        intent.putExtra("displayId", Display.DEFAULT_DISPLAY);
        intent.putExtra("scale", 2.5f);
        intent.putExtra("centerX", 100f);
        intent.putExtra("centerY", 200f);

        receiverCaptor.getValue().onReceive(mMockContext, intent);
        verify(mMockController).setScaleAndCenter(
                Display.DEFAULT_DISPLAY, 2.5f, 100f, 200f, true,
                AccessibilityManagerService.MAGNIFICATION_GESTURE_HANDLER_ID);
    }

    @Test
    public void onReceive_activateZoom_callsControllerWithDefaultValues() {
        mDebugger.registerIfNecessary();
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockContext).registerReceiver(
                receiverCaptor.capture(),
                any(IntentFilter.class),
                eq(Context.RECEIVER_EXPORTED));
        // Intent without extra values should use default values.
        Intent intent = new Intent(DEBUG_ACTIVATE_ZOOM);
        int defaultDisplayId = Display.DEFAULT_DISPLAY;
        float defaultScale = 2.0f;
        float defaultCenterX = Float.NaN;
        float defaultCenterY = Float.NaN;

        receiverCaptor.getValue().onReceive(mMockContext, intent);

        verify(mMockController).setScaleAndCenter(
                defaultDisplayId, defaultScale, defaultCenterX, defaultCenterY, true,
                AccessibilityManagerService.MAGNIFICATION_GESTURE_HANDLER_ID);
    }

    @Test
    public void onReceive_resetZoom_callsController() {
        mDebugger.registerIfNecessary();
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockContext).registerReceiver(
                receiverCaptor.capture(),
                any(IntentFilter.class),
                eq(Context.RECEIVER_EXPORTED));

        Intent intent = new Intent(DEBUG_RESET_ZOOM);
        intent.putExtra("displayId", Display.DEFAULT_DISPLAY);
        when(mMockController.reset(Display.DEFAULT_DISPLAY, true)).thenReturn(true);

        receiverCaptor.getValue().onReceive(mMockContext, intent);

        verify(mMockController).reset(Display.DEFAULT_DISPLAY, true);
    }
}
