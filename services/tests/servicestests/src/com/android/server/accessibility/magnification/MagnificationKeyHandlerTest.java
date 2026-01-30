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

package com.android.server.accessibility.magnification;

import static com.android.server.accessibility.magnification.MagnificationController.PAN_DIRECTION_DOWN;
import static com.android.server.accessibility.magnification.MagnificationController.PAN_DIRECTION_LEFT;
import static com.android.server.accessibility.magnification.MagnificationController.PAN_DIRECTION_RIGHT;
import static com.android.server.accessibility.magnification.MagnificationController.PAN_DIRECTION_UP;
import static com.android.server.accessibility.magnification.MagnificationController.ZOOM_DIRECTION_IN;
import static com.android.server.accessibility.magnification.MagnificationController.ZOOM_DIRECTION_OUT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Display;
import android.view.KeyEvent;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.accessibility.AccessibilityManagerService;
import com.android.server.accessibility.EventStreamTransformation;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link MagnificationKeyHandler}.
 */
@RunWith(AndroidJUnit4.class)
public class MagnificationKeyHandlerTest {
    private static final int EXTERNAL_DISPLAY_ID = 2;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private MagnificationKeyHandler mMkh;

    @Mock
    MagnificationKeyHandler.Callback mCallback;

    @Mock
    AccessibilityManagerService mAms;

    @Mock
    EventStreamTransformation mNextHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mCallback.isMagnificationActivated(any(Integer.class))).thenReturn(true);
        doReturn(Display.DEFAULT_DISPLAY).when(mAms).getTopFocusedDisplayId();
        mMkh = new MagnificationKeyHandler(mCallback, mAms);
        mMkh.setNext(mNextHandler);
    }

    @Test
    public void onKeyEvent_unusedKeyPress_sendToNext() {
        final KeyEvent event = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_L, 0, 0);
        mMkh.onKeyEvent(event, 0);

        verifySentEventToNext(event);
    }

    @Test
    public void onKeyEvent_arrowKeyPressWithIncorrectModifiers_sendToNext() {
        final KeyEvent event =
                new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                        0, KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(event, 0);

        verifySentEventToNext(event);
    }

    @Test
    public void onKeyEvent_unusedKeyPressWithCorrectModifiers_sendToNext() {
        final KeyEvent event =
                new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_J, 0,
                        KeyEvent.META_META_ON | KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(event, 0);

        verifySentEventToNext(event);
    }

    @Test
    public void onKeyEvent_arrowKeyPressWithTooManyModifiers_sendToNext() {
        // Add ctrl.
        KeyEvent event =
                new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0,
                        KeyEvent.META_META_ON | KeyEvent.META_ALT_ON | KeyEvent.META_CTRL_ON);
        mMkh.onKeyEvent(event, 0);
        verifySentEventToNext(event);

        // Add shift.
        event = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0,
                        KeyEvent.META_META_ON | KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);
        mMkh.onKeyEvent(event, 0);
        verifySentEventToNext(event);

        // Add ctrl and shift.
        event = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0,
                KeyEvent.META_META_ON | KeyEvent.META_ALT_ON | KeyEvent.META_CTRL_ON
                        | KeyEvent.META_SHIFT_ON);
        mMkh.onKeyEvent(event, 0);
        verifySentEventToNext(event);
    }

    @Test
    public void onKeyEvent_arrowKeyPressWithoutMagnificationActive_sendToNext() {
        when(mCallback.isMagnificationActivated(any(Integer.class))).thenReturn(false);
        final KeyEvent event =
                new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0,
                        KeyEvent.META_META_ON | KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(event, 0);

        verifySentEventToNext(event);
    }

    @Test
    public void onKeyEvent_panStartAndEnd_left() {
        testPanMagnification(KeyEvent.KEYCODE_DPAD_LEFT, PAN_DIRECTION_LEFT, Display.DEFAULT_DISPLAY);
    }


    @Test
    public void onKeyEvent_panStartAndEnd_left_onExternalDisplay() {
        doReturn(EXTERNAL_DISPLAY_ID).when(mAms).getTopFocusedDisplayId();
        testPanMagnification(KeyEvent.KEYCODE_DPAD_LEFT, PAN_DIRECTION_LEFT, EXTERNAL_DISPLAY_ID);
    }


    @Test
    public void onKeyEvent_panStartAndEnd_right() {
        testPanMagnification(KeyEvent.KEYCODE_DPAD_RIGHT, PAN_DIRECTION_RIGHT, Display.DEFAULT_DISPLAY);
    }

    @Test
    public void onKeyEvent_panStartAndEnd_right_onExternalDisplay() {
        doReturn(EXTERNAL_DISPLAY_ID).when(mAms).getTopFocusedDisplayId();
        testPanMagnification(KeyEvent.KEYCODE_DPAD_RIGHT, PAN_DIRECTION_RIGHT, EXTERNAL_DISPLAY_ID);
    }

    @Test
    public void onKeyEvent_panStartAndEnd_up() {
        testPanMagnification(KeyEvent.KEYCODE_DPAD_UP, PAN_DIRECTION_UP, Display.DEFAULT_DISPLAY);
    }

    @Test
    public void onKeyEvent_panStartAndEnd_up_onExternalDisplay() {
        doReturn(EXTERNAL_DISPLAY_ID).when(mAms).getTopFocusedDisplayId();
        testPanMagnification(KeyEvent.KEYCODE_DPAD_UP, PAN_DIRECTION_UP, EXTERNAL_DISPLAY_ID);
    }

    @Test
    public void onKeyEvent_panStartAndEnd_down() {
        testPanMagnification(KeyEvent.KEYCODE_DPAD_DOWN, PAN_DIRECTION_DOWN, Display.DEFAULT_DISPLAY);
    }

    @Test
    public void onKeyEvent_panStartAndEnd_down_onExternalDisplay() {
        doReturn(EXTERNAL_DISPLAY_ID).when(mAms).getTopFocusedDisplayId();
        testPanMagnification(KeyEvent.KEYCODE_DPAD_DOWN, PAN_DIRECTION_DOWN, EXTERNAL_DISPLAY_ID);
    }

    @Test
    public void onKeyEvent_scaleStartAndEnd_zoomIn() {
        testScaleMagnification(KeyEvent.KEYCODE_EQUALS, ZOOM_DIRECTION_IN, Display.DEFAULT_DISPLAY);
    }

    @Test
    public void onKeyEvent_scaleStartAndEnd_zoomIn_onExternalDisplay() {
        doReturn(EXTERNAL_DISPLAY_ID).when(mAms).getTopFocusedDisplayId();
        testScaleMagnification(KeyEvent.KEYCODE_EQUALS, ZOOM_DIRECTION_IN, EXTERNAL_DISPLAY_ID);
    }

    @Test
    public void onKeyEvent_scaleStartAndEnd_zoomOut() {
        testScaleMagnification(KeyEvent.KEYCODE_MINUS, ZOOM_DIRECTION_OUT, Display.DEFAULT_DISPLAY);
    }

    @Test
    public void onKeyEvent_scaleStartAndEnd_zoomOut_onExternalDisplay() {
        doReturn(EXTERNAL_DISPLAY_ID).when(mAms).getTopFocusedDisplayId();
        testScaleMagnification(KeyEvent.KEYCODE_MINUS, ZOOM_DIRECTION_OUT, EXTERNAL_DISPLAY_ID);
    }

    @Test
    public void onKeyEvent_panStartAndStop_diagonal() {
        final KeyEvent downLeftEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, 0, KeyEvent.META_META_ON | KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(downLeftEvent, 0);
        verify(mCallback, times(1)).onPanMagnificationStart(Display.DEFAULT_DISPLAY,
                PAN_DIRECTION_LEFT);
        verify(mCallback, times(0)).onPanMagnificationStop(anyInt());

        Mockito.clearInvocations(mCallback);

        // Also press the down arrow key.
        final KeyEvent downDownEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_DOWN, 0, KeyEvent.META_META_ON | KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(downDownEvent, 0);
        verify(mCallback, times(0)).onPanMagnificationStart(Display.DEFAULT_DISPLAY,
                PAN_DIRECTION_LEFT);
        verify(mCallback, times(1)).onPanMagnificationStart(Display.DEFAULT_DISPLAY,
                PAN_DIRECTION_DOWN);
        verify(mCallback, times(0)).onPanMagnificationStop(anyInt());

        Mockito.clearInvocations(mCallback);

        // Lift the left arrow key.
        final KeyEvent upLeftEvent = new KeyEvent(0, 0, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_DPAD_LEFT, 0, KeyEvent.META_META_ON | KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(upLeftEvent, 0);
        verify(mCallback, times(0)).onPanMagnificationStart(Display.DEFAULT_DISPLAY,
                PAN_DIRECTION_LEFT);
        verify(mCallback, times(0)).onPanMagnificationStart(Display.DEFAULT_DISPLAY,
                PAN_DIRECTION_DOWN);
        verify(mCallback, times(1)).onPanMagnificationStop(PAN_DIRECTION_LEFT);
        verify(mCallback, times(0)).onPanMagnificationStop(PAN_DIRECTION_DOWN);

        Mockito.clearInvocations(mCallback);

        // Lift the down arrow key.
        final KeyEvent upDownEvent = new KeyEvent(0, 0, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_DPAD_DOWN, 0, KeyEvent.META_META_ON | KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(upDownEvent, 0);
        verify(mCallback, times(0)).onPanMagnificationStart(Display.DEFAULT_DISPLAY,
                PAN_DIRECTION_LEFT);
        verify(mCallback, times(0)).onPanMagnificationStart(Display.DEFAULT_DISPLAY,
                PAN_DIRECTION_DOWN);
        verify(mCallback, times(0)).onPanMagnificationStop(PAN_DIRECTION_LEFT);
        verify(mCallback, times(1)).onPanMagnificationStop(PAN_DIRECTION_DOWN);

        // The event was not passed on.
        verify(mNextHandler, times(0)).onKeyEvent(any(), anyInt());
    }

    @Test
    public void testPanMagnification_modifiersReleasedBeforeArrows() {
        final KeyEvent downEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_DOWN, 0,
                KeyEvent.META_META_ON | KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(downEvent, 0);

        // Pan started.
        verify(mCallback, times(1)).onPanMagnificationStart(Display.DEFAULT_DISPLAY,
                PAN_DIRECTION_DOWN);
        verify(mCallback, times(0)).onPanMagnificationStop(anyInt());
        verify(mCallback, times(0)).onKeyboardInteractionStop();

        Mockito.clearInvocations(mCallback);

        // Lift the "meta" key.
        final KeyEvent upEvent = new KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT,
                0,
                KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(upEvent, 0);

        // Pan ended.
        verify(mCallback, times(0)).onPanMagnificationStart(Display.DEFAULT_DISPLAY,
                PAN_DIRECTION_DOWN);
        verify(mCallback, times(0)).onPanMagnificationStop(anyInt());
        verify(mCallback, times(1)).onKeyboardInteractionStop();

    }

    private void testPanMagnification(int keyCode, int panDirection, int displayId) {
        final KeyEvent downEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0,
                KeyEvent.META_META_ON | KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(downEvent, 0);

        // Pan started.
        verify(mCallback, times(1)).onPanMagnificationStart(displayId, panDirection);
        verify(mCallback, times(0)).onPanMagnificationStop(anyInt());

        Mockito.clearInvocations(mCallback);

        final KeyEvent upEvent = new KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0,
                KeyEvent.META_META_ON | KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(upEvent, 0);

        // Pan ended.
        verify(mCallback, times(0)).onPanMagnificationStart(displayId, panDirection);
        verify(mCallback, times(1)).onPanMagnificationStop(panDirection);

        // Scale callbacks were not called.
        verify(mCallback, times(0)).onScaleMagnificationStart(anyInt(), anyInt());
        verify(mCallback, times(0)).onScaleMagnificationStop(anyInt());

        // The events were not passed on.
        verify(mNextHandler, times(0)).onKeyEvent(any(), anyInt());
    }

    private void testScaleMagnification(int keyCode, int zoomDirection, int displayId) {
        final KeyEvent downEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0,
                KeyEvent.META_META_ON | KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(downEvent, 0);

        // Scale started.
        verify(mCallback, times(1)).onScaleMagnificationStart(displayId,
                zoomDirection);
        verify(mCallback, times(0)).onScaleMagnificationStop(anyInt());

        Mockito.clearInvocations(mCallback);

        final KeyEvent upEvent = new KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0,
                KeyEvent.META_META_ON | KeyEvent.META_ALT_ON);
        mMkh.onKeyEvent(upEvent, 0);

        // Scale ended.
        verify(mCallback, times(0)).onScaleMagnificationStart(displayId,
                zoomDirection);
        verify(mCallback, times(1)).onScaleMagnificationStop(zoomDirection);

        // Pan callbacks were not called.
        verify(mCallback, times(0)).onPanMagnificationStart(anyInt(), anyInt());
        verify(mCallback, times(0)).onPanMagnificationStop(anyInt());

        // The events were not passed on.
        verify(mNextHandler, times(0)).onKeyEvent(any(), anyInt());
    }

    private void verifySentEventToNext(KeyEvent event) {
        // No callbacks were called.
        verify(mCallback, times(0)).onPanMagnificationStart(anyInt(), anyInt());
        verify(mCallback, times(0)).onPanMagnificationStop(anyInt());
        verify(mCallback, times(0)).onScaleMagnificationStart(anyInt(), anyInt());
        verify(mCallback, times(0)).onScaleMagnificationStop(anyInt());
        verify(mCallback, times(0)).onKeyboardInteractionStop();

        // The event was passed on.
        verify(mNextHandler, times(1)).onKeyEvent(event, 0);
    }
}
