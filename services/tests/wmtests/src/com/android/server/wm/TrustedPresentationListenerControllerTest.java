/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.server.wm;

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.os.InputConfig;
import android.platform.test.annotations.Presubmit;
import android.util.Pair;
import android.util.Size;
import android.view.InputApplicationHandle;
import android.view.InputWindowHandle;
import android.window.ITrustedPresentationListener;
import android.window.TrustedPresentationThresholds;
import android.window.WindowInfosListener;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link TrustedPresentationListenerController}.
 *
 * Build/Install/Run:
 *  atest WmTests:TrustedPresentationListenerControllerTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class TrustedPresentationListenerControllerTest extends WindowTestsBase {

    private TrustedPresentationListenerController mController;
    private TestListener mListener;
    private IBinder mWindowToken;
    private TrustedPresentationThresholds mThresholds;

    private static final int LISTENER_ID = 1;

    private class TestListener extends ITrustedPresentationListener.Stub {
        CountDownLatch mInvocationLatch = new CountDownLatch(1);
        int[] mEntered;
        int[] mExited;

        @Override
        public void onTrustedPresentationChanged(int[] entered, int[] exited) {
            mEntered = entered;
            mExited = exited;
            mInvocationLatch.countDown();
        }

        void setExpectedInvocations(int expectedInvocations) {
            mInvocationLatch = new CountDownLatch(expectedInvocations);
            mEntered = null;
            mExited = null;
        }

        void await() {
            try {
                mInvocationLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        void assertEnteredTrustedState(int id) {
            assertNotNull(mEntered);
            assertEquals(1, mEntered.length);
            assertEquals(id, mEntered[0]);
        }

        void assertExitedTrustedState(int id) {
            assertNotNull(mExited);
            assertEquals(1, mExited.length);
            assertEquals(id, mExited[0]);
        }
    }

    private TrustedPresentationListenerController.WindowInfosListenerProvider mWindowInfosProvider;
    private WindowInfosListener mWindowInfosListener;

    @Before
    public void setUp() {
        mWindowInfosListener = mock(WindowInfosListener.class);
        doReturn(new Pair<>(new InputWindowHandle[0], new WindowInfosListener.DisplayInfo[0]))
            .when(mWindowInfosListener).register();
        mWindowInfosProvider = () -> mWindowInfosListener;
        mController = new TrustedPresentationListenerController(mWindowInfosProvider);
        mListener = new TestListener();
        mWindowToken = new Binder();
        mThresholds = new TrustedPresentationThresholds(
                0.5f /* minAlpha */, 0.5f /* minFractionRendered */,
                100 /* stabilityRequirementMs */);
    }

    @Test
    public void testRegisterAndUnregisterListener() {
        TestListener listener1 = new TestListener();
        TestListener listener2 = new TestListener();

        mController.registerListener(mWindowToken, listener1, mThresholds, 1);
        waitForHandlerIdle();
        verify(mWindowInfosListener, times(1)).register();
        mController.registerListener(mWindowToken, listener2, mThresholds, 2);
        waitForHandlerIdle();
        verify(mWindowInfosListener, times(1)).register(); // Still 1

        assertFalse(mController.mRegisteredListeners.isEmpty());
        assertEquals(2, mController.mRegisteredListeners.mUniqueListeners.size());
        assertEquals(1, mController.mRegisteredListeners.mWindowToListeners.size());
        assertEquals(2, mController.mRegisteredListeners.mWindowToListeners
                        .get(mWindowToken).size());

        mController.unregisterListener(listener1, 1);
        waitForHandlerIdle();
        verify(mWindowInfosListener, never()).unregister();

        assertFalse(mController.mRegisteredListeners.isEmpty());
        assertEquals(1, mController.mRegisteredListeners.mUniqueListeners.size());
        assertEquals(1, mController.mRegisteredListeners.mWindowToListeners.size());
        assertEquals(1, mController.mRegisteredListeners.mWindowToListeners
                        .get(mWindowToken).size());

        mController.unregisterListener(listener2, 2);
        waitForHandlerIdle();
        verify(mWindowInfosListener, times(1)).unregister();

        assertTrue(mController.mRegisteredListeners.isEmpty());
        assertTrue(mController.mRegisteredListeners.mUniqueListeners.isEmpty());
    }

    @Test
    public void testDump() {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        String dumpOutput = dumpController();
        assertTrue(dumpOutput.contains("window=" + mWindowToken));
        assertTrue(dumpOutput.contains("Active unique listeners (1)"));

        mController.unregisterListener(mListener, LISTENER_ID);
        waitForHandlerIdle();

        String dumpOutput2 = dumpController();
        assertTrue(dumpOutput2.contains("Active unique listeners (0)"));
    }

    @Test
    public void testComputeTpl_entersTrustedState() throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        // Window handles that pass the thresholds
        InputWindowHandle windowHandle = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, mWindowToken);
        WindowInfosListener.DisplayInfo displayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{windowHandle},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        // Wait for stability requirement (100ms)
        mListener.await();

        mListener.assertEnteredTrustedState(LISTENER_ID);
    }

    @Test
    public void testComputeTpl_multipleListeners() throws Exception {
        TestListener listener1 = new TestListener();
        TestListener listener2 = new TestListener();
        mController.registerListener(mWindowToken, listener1, mThresholds, 1);

        var unmeetableThresholds = new TrustedPresentationThresholds(
                0.8f /* minAlpha */, 0.5f /* minFractionRendered */,
                100 /* stabilityRequirementMs */);

        mController.registerListener(mWindowToken, listener2, unmeetableThresholds, 2);
        waitForHandlerIdle();

        listener1.setExpectedInvocations(1);
        listener2.setExpectedInvocations(1);

        InputWindowHandle windowHandle = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 0.6f /* alpha */, mWindowToken);
        WindowInfosListener.DisplayInfo displayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{windowHandle},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        listener1.await();
        listener1.assertEnteredTrustedState(1);
        assertEquals(1, listener2.mInvocationLatch.getCount());
    }

    @Test
    public void testComputeTpl_failsAlphaThreshold() throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        // Window handle that fails the alpha threshold
        InputWindowHandle windowHandle = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 0.4f /* < 0.5f */, mWindowToken);
        WindowInfosListener.DisplayInfo displayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{windowHandle},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        // The computation runs, but neither enters nor starts the timer for entered
        waitForHandlerIdle();

        // Wait just in case, latch shouldn't trigger
        assertEquals(1, mListener.mInvocationLatch.getCount());
    }

    @Test
    public void testComputeTpl_entersAndExitsTrustedState() throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        InputWindowHandle windowHandle = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, mWindowToken);
        WindowInfosListener.DisplayInfo displayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{windowHandle},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        mListener.await();
        mListener.assertEnteredTrustedState(LISTENER_ID);

        // Now move it to invisible (alpha = 0)
        mListener.setExpectedInvocations(1);
        windowHandle.alpha = 0f;

        triggerWindowInfosChanged(
                new InputWindowHandle[]{windowHandle},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        mListener.await();

        mListener.assertExitedTrustedState(LISTENER_ID);
    }

    @Test
    public void testComputeTpl_windowFullyOccluded_failsFractionRenderedThreshold()
                throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        InputWindowHandle listenerWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, mWindowToken);
        InputWindowHandle occludingWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, new Binder());

        WindowInfosListener.DisplayInfo displayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{occludingWindow, listenerWindow},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        // The computation runs, but neither enters nor starts the timer for entered
        waitForHandlerIdle();
        assertEquals(1, mListener.mInvocationLatch.getCount());
    }

    @Test
    public void testComputeTpl_windowPartiallyOccluded_passesFractionRenderedThreshold()
                throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        InputWindowHandle listenerWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, mWindowToken);
        // Occludes 40% of the window
        InputWindowHandle occludingWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 40), 1.0f /* alpha */, new Binder());

        WindowInfosListener.DisplayInfo displayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{occludingWindow, listenerWindow},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        mListener.await();

        mListener.assertEnteredTrustedState(LISTENER_ID);
    }

    @Test
    public void testComputeTpl_windowPartiallyOccluded_failsFractionRenderedThreshold()
                throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        InputWindowHandle listenerWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, mWindowToken);
        // Occludes 60% of the window
        InputWindowHandle occludingWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 60), 1.0f /* alpha */, new Binder());

        WindowInfosListener.DisplayInfo displayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{occludingWindow, listenerWindow},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        waitForHandlerIdle();
        assertEquals(1, mListener.mInvocationLatch.getCount());
    }

    @Test
    public void testComputeTpl_windowBelow_passesFractionRenderedThreshold() throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        InputWindowHandle listenerWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, mWindowToken);
        // A window below listenerWindow
        InputWindowHandle windowBelow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, new Binder());

        WindowInfosListener.DisplayInfo displayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{listenerWindow, windowBelow},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        mListener.await();

        mListener.assertEnteredTrustedState(LISTENER_ID);
    }

    @Test
    public void testComputeTpl_occludingWindowCannotOcclude_passes() throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        InputWindowHandle listenerWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, mWindowToken);
        // Occludes 100% of the window but has canOccludePresentation = false
        InputWindowHandle occludingWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, new Binder());
        occludingWindow.canOccludePresentation = false;

        WindowInfosListener.DisplayInfo displayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{occludingWindow, listenerWindow},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        mListener.await();

        mListener.assertEnteredTrustedState(LISTENER_ID);
    }

    @Test
    public void testComputeTpl_occludingWindowNotVisible_passes() throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        InputWindowHandle listenerWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, mWindowToken);
        // Occludes 100% of the window but has NOT_VISIBLE
        InputWindowHandle occludingWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, new Binder());
        occludingWindow.inputConfig |= InputConfig.NOT_VISIBLE;

        WindowInfosListener.DisplayInfo displayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{occludingWindow, listenerWindow},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        mListener.await();

        mListener.assertEnteredTrustedState(LISTENER_ID);
    }

    @Test
    public void testComputeTpl_listenerWindowOutOfScreenBounds_fails() throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        // Window handle is at (100, 100) with size 100x100
        InputWindowHandle listenerWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(100, 100, 200, 200), 1.0f /* alpha */, mWindowToken);

        // Display is 150x150, so the window is partially out of bounds.
        // Its visible area on screen is 50x50, which is 2500. Total size is 10000.
        // Fraction rendered is 0.25 < 0.5 threshold.
        WindowInfosListener.DisplayInfo displayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 150, 150, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{listenerWindow},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        waitForHandlerIdle();
        assertEquals(1, mListener.mInvocationLatch.getCount());
    }

    @Test
    public void testComputeTpl_windowMirrored_passesFractionRenderedThreshold() throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        // Primary window, fully occluded
        InputWindowHandle primaryWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, mWindowToken);
        InputWindowHandle occludingWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, new Binder());

        WindowInfosListener.DisplayInfo primaryDisplayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        // Mirrored window on a different display, not occluded
        int mirrorDisplayId = DEFAULT_DISPLAY + 1;
        InputWindowHandle mirroredWindow = createInputWindowHandle(
                mirrorDisplayId, new Rect(0, 0, 100, 100), 1.0f /* alpha */, mWindowToken);
        WindowInfosListener.DisplayInfo mirrorDisplayInfo = createDisplayInfo(
                mirrorDisplayId, 100, 100, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{occludingWindow, primaryWindow, mirroredWindow},
                new WindowInfosListener.DisplayInfo[]{primaryDisplayInfo, mirrorDisplayInfo});

        mListener.await();
        mListener.assertEnteredTrustedState(LISTENER_ID);
        assertNotNull(mListener.mEntered);
        assertEquals(1, mListener.mEntered.length);
        assertEquals(1, mListener.mEntered[0]);
    }

    @Test
    public void testComputeTpl_windowMirrored_failsWhenDifferentMirrorsMeetDifferentThresholds()
                throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        // Primary window: high alpha, but low fraction rendered (mostly occluded)
        InputWindowHandle primaryWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, mWindowToken);
        InputWindowHandle occludingWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 80), 1.0f /* alpha */,
                new Binder()); // Occludes 80%

        WindowInfosListener.DisplayInfo primaryDisplayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        // Mirrored window: low alpha, but high fraction rendered (not occluded)
        int mirrorDisplayId = DEFAULT_DISPLAY + 1;
        InputWindowHandle mirroredWindow = createInputWindowHandle(
                mirrorDisplayId, new Rect(0, 0, 100, 100), 0.2f,
                mWindowToken); // Alpha 0.2 < threshold 0.5
        WindowInfosListener.DisplayInfo mirrorDisplayInfo = createDisplayInfo(
                mirrorDisplayId, 100, 100, new Matrix());

        triggerWindowInfosChanged(
                new InputWindowHandle[]{occludingWindow, primaryWindow, mirroredWindow},
                new WindowInfosListener.DisplayInfo[]{primaryDisplayInfo, mirrorDisplayInfo});

        // The computation runs, but neither enters nor starts the timer for entered
        waitForHandlerIdle();
        assertEquals(1, mListener.mInvocationLatch.getCount());
    }

    @Test
    public void testComputeTpl_windowMirrored_passesWhenOneMirrorMeetsThresholds()
                throws Exception {
        mController.registerListener(mWindowToken, mListener, mThresholds, LISTENER_ID);
        waitForHandlerIdle();

        mListener.setExpectedInvocations(1);

        // Primary window: fails both thresholds (low alpha and heavily occluded)
        InputWindowHandle primaryWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 0.2f /* alpha */,
                mWindowToken); // Alpha 0.2 < threshold 0.5
        InputWindowHandle occludingWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 80), 1.0f /* alpha */,
                new Binder()); // Occludes 80%

        WindowInfosListener.DisplayInfo displayInfo = createDisplayInfo(
                DEFAULT_DISPLAY, 100, 100, new Matrix());

        // Mirrored window: passes both thresholds
        InputWindowHandle mirroredWindow = createInputWindowHandle(
                DEFAULT_DISPLAY, new Rect(0, 0, 100, 100), 1.0f /* alpha */, mWindowToken);

        triggerWindowInfosChanged(
                new InputWindowHandle[]{mirroredWindow, occludingWindow, primaryWindow},
                new WindowInfosListener.DisplayInfo[]{displayInfo});

        mListener.await();

        mListener.assertEnteredTrustedState(LISTENER_ID);
    }

    private void waitForHandlerIdle() {
        dumpController();
    }

    private String dumpController() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mController.dump(pw);
        pw.flush();
        return sw.toString();
    }

    private InputWindowHandle createInputWindowHandle(
            int displayId, Rect frame, float alpha, IBinder windowToken) {
        InputWindowHandle handle = new InputWindowHandle(
                new InputApplicationHandle(new Binder(), "test", 100), displayId);
        handle.frame.set(frame);
        handle.alpha = alpha;
        handle.transform = new Matrix();
        handle.setWindowToken(windowToken);
        handle.canOccludePresentation = true;
        handle.inputConfig = 0;
        handle.contentSize = new Size(frame.width(), frame.height());
        return handle;
    }

    private WindowInfosListener.DisplayInfo createDisplayInfo(
            int displayId, int logicalWidth, int logicalHeight, Matrix transform) {
        return new WindowInfosListener.DisplayInfo(displayId, logicalWidth, logicalHeight,
                        transform);
    }

    private void triggerWindowInfosChanged(InputWindowHandle[] handles,
            WindowInfosListener.DisplayInfo[] displayInfos) throws Exception {
        mController.onWindowInfosChanged(handles, displayInfos);
        waitForHandlerIdle();
    }
}
