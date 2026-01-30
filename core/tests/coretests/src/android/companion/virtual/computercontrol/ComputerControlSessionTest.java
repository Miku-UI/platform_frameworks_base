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

package android.companion.virtual.computercontrol;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IDisplayManager;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualTouchEvent;
import android.os.RemoteException;
import android.view.DisplayInfo;
import android.view.KeyEvent;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionTest {

    private static final int DISPLAY_ID = 42;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final String TARGET_PACKAGE = "com.android.foo";

    @Mock
    private IComputerControlSession mMockSession;
    @Mock
    private IDisplayManager mDisplayManager;
    @Mock
    private IVirtualDisplayCallback mVirtualDisplayCallback;
    @Mock
    private IInteractiveMirrorDisplay mMockInteractiveMirrorDisplay;

    private ComputerControlSession mSession;

    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() throws RemoteException {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = WIDTH;
        displayInfo.logicalHeight = HEIGHT;
        when(mDisplayManager.getDisplayInfo(DISPLAY_ID)).thenReturn(displayInfo);
        mSession = new ComputerControlSession(DISPLAY_ID, mVirtualDisplayCallback, mMockSession,
                new DisplayManagerGlobal(mDisplayManager));
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void setsVirtualDisplaySurface() throws RemoteException {
        verify(mDisplayManager).setVirtualDisplaySurface(eq(mVirtualDisplayCallback), any());
    }

    @Test
    public void getVirtualDisplayId_returnsId() throws RemoteException {
        when(mMockSession.getVirtualDisplayId()).thenReturn(DISPLAY_ID);
        assertThat(mSession.getVirtualDisplayId()).isEqualTo(DISPLAY_ID);
    }

    @Test
    public void sendKeyEvent_sendsEvent() throws RemoteException {
        VirtualKeyEvent keyEvent = new VirtualKeyEvent.Builder()
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .setKeyCode(KeyEvent.KEYCODE_A)
                .build();
        mSession.sendKeyEvent(keyEvent);
        verify(mMockSession).sendKeyEvent(eq(keyEvent));
    }

    @Test
    public void performAction_performsAction() throws RemoteException {
        mSession.performAction(ComputerControlSession.ACTION_GO_BACK);
        verify(mMockSession).performAction(eq(ComputerControlSession.ACTION_GO_BACK));
    }

    @Test
    public void insertText_insertsText() throws RemoteException {
        mSession.insertText("text", true, true);
        verify(mMockSession).insertText(eq("text"), eq(true), eq(true));
    }

    @Test
    public void sendTouchEvent_sendsEvent() throws RemoteException {
        VirtualTouchEvent touchEvent = new VirtualTouchEvent.Builder()
                .setPointerId(0)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setX(0)
                .setY(0)
                .build();
        mSession.sendTouchEvent(touchEvent);
        verify(mMockSession).sendTouchEvent(eq(touchEvent));
    }

    @Test
    public void createInteractiveMirrorDisplay_returnsDisplay() throws RemoteException {
        when(mMockSession.createInteractiveMirrorDisplay(eq(WIDTH), eq(HEIGHT), any()))
                .thenReturn(mMockInteractiveMirrorDisplay);
        InteractiveMirrorDisplay display =
                mSession.createInteractiveMirrorDisplay(WIDTH, HEIGHT, new Surface());
        assertThat(display).isNotNull();
    }

    @Test
    public void close_closesSession() throws RemoteException {
        mSession.close();
        verify(mMockSession).close();
    }

    @Test
    public void launchApplication_launchesApplication() throws RemoteException {
        mSession.launchApplication(TARGET_PACKAGE);
        verify(mMockSession).launchApplication(eq(TARGET_PACKAGE));
    }

    @Test
    public void tap_taps() throws RemoteException {
        mSession.tap(1, 2);
        verify(mMockSession).tap(eq(1), eq(2));
    }

    @Test
    public void tapNotInRange_throws() {
        assertThrows(IllegalArgumentException.class, () -> mSession.tap(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> mSession.tap(1, -2));
    }

    @Test
    public void swipe_swipes() throws RemoteException {
        mSession.swipe(1, 2, 3, 4);
        verify(mMockSession).swipe(eq(1), eq(2), eq(3), eq(4));
    }

    @Test
    public void swipeNotInRange_throws() {
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(-1, 2, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(1, -2, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(1, 2, -3, 4));
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(1, 2, 3, -4));
    }

    @Test
    public void longPress_longPresses() throws RemoteException {
        mSession.longPress(1, 2);
        verify(mMockSession).longPress(eq(1), eq(2));
    }

    @Test
    public void longPressNotInRange_throws() {
        assertThrows(IllegalArgumentException.class, () -> mSession.longPress(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> mSession.longPress(1, -2));
    }
}
