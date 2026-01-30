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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.hardware.input.VirtualTouchEvent;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class InteractiveMirrorDisplayTest {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    @Mock
    private IInteractiveMirrorDisplay mMockDisplay;

    private InteractiveMirrorDisplay mDisplay;

    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        mDisplay = new InteractiveMirrorDisplay(mMockDisplay);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void resize_resizesDisplay() throws RemoteException {
        mDisplay.resize(WIDTH, HEIGHT);
        verify(mMockDisplay).resize(WIDTH, HEIGHT);
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
        mDisplay.sendTouchEvent(touchEvent);
        verify(mMockDisplay).sendTouchEvent(eq(touchEvent));
    }

    @Test
    public void close_closesDisplay() throws RemoteException {
        mDisplay.close();
        verify(mMockDisplay).close();
    }
}
