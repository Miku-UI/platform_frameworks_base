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

import static android.companion.virtual.computercontrol.ComputerControlSession.UNSTABLE_REASON_CALLER_INTERACTION;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.companion.virtual.computercontrol.ComputerControlSession.ScreenshotException;
import android.content.ComponentName;
import android.graphics.Canvas;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IDisplayManager;
import android.media.Image;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Size;
import android.view.DisplayInfo;
import android.view.Surface;
import android.view.accessibility.AccessibilityDisplayProxy;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionTest {

    private static final int DISPLAY_ID = 42;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int FRAME_PROCESSING_DELAY_MS = 100;
    private static final String TARGET_PACKAGE = "com.android.foo";
    private static final String TARGET_CLASS = "com.android.foo.FooActivity";

    @Mock
    private IComputerControlSession mMockSession;
    @Mock
    private IDisplayManager mDisplayManager;
    @Mock
    private IInteractiveMirror mMockInteractiveMirror;
    @Mock
    private Runnable mMockOnClosedRunnable;
    @Mock
    private ComputerControlSession.StabilityListener mMockStabilityListener;
    @Mock
    private ComputerControlSession.InjectedA11yManager mA11yManager;

    @Captor
    private ArgumentCaptor<AccessibilityDisplayProxy> mProxyCaptor;
    @Captor
    private ArgumentCaptor<Surface> mSurfaceCaptor;
    @Captor
    private ArgumentCaptor<IComputerControlLifecycleCallback> mLifecycleCaptor;

    private ComputerControlSession mSession;
    private IComputerControlLifecycleCallback mLifecycle;
    private Surface mSurface;

    private AutoCloseable mMockitoSession;

    private final Executor mExecutor = new TestExecutor();

    @Before
    public void setUp() throws RemoteException {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = WIDTH;
        displayInfo.logicalHeight = HEIGHT;
        when(mDisplayManager.getDisplayInfo(DISPLAY_ID)).thenReturn(displayInfo);

        mSession = new ComputerControlSession(DISPLAY_ID, mMockSession, mA11yManager,
                mMockOnClosedRunnable, new DisplayManagerGlobal(mDisplayManager));

        // Capture session initialization args from constructor.
        verify(mMockSession).initialize(mLifecycleCaptor.capture(), mSurfaceCaptor.capture());
        mLifecycle = mLifecycleCaptor.getValue();
        assertThat(mLifecycle).isNotNull();
        mSurface = mSurfaceCaptor.getValue();
        assertThat(mSurface).isNotNull();
        clearInvocations(mMockSession);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
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
    public void createInteractiveMirror_returns() throws RemoteException {
        when(mMockSession.createInteractiveMirror(any(), any()))
                .thenReturn(mMockInteractiveMirror);
        InteractiveMirror mirror = mSession.createInteractiveMirror(null);
        assertThat(mirror).isNotNull();
    }

    @Test
    public void getDisplaySize_returns() {
        assertThat(mSession.getDisplaySize()).isEqualTo(new Size(WIDTH, HEIGHT));
    }

    @Test
    public void close_closesSession() throws RemoteException {
        mSession.close();
        verify(mMockSession).close();
    }

    @Test
    public void launchApplication_launchesApplication() throws RemoteException {
        mSession.launchApplication(TARGET_PACKAGE);
        verify(mMockSession).launchApplication(TARGET_PACKAGE, null);
    }

    @Test
    public void launchApplication_withComponentName_launchesApplication() throws RemoteException {
        mSession.launchApplication(new ComponentName(TARGET_PACKAGE, TARGET_CLASS));
        verify(mMockSession).launchApplication(TARGET_PACKAGE, TARGET_CLASS);
    }

    @Test
    public void handOverApplications_handsOverApplications() throws RemoteException {
        mSession.handOverApplications();
        verify(mMockSession).handOverApplications();
    }

    @Test
    public void tap_taps() throws RemoteException {
        mSession.tap(1, 2);
        verify(mMockSession).tap(eq(1), eq(2));
    }

    @Test
    public void tapNotInRange_throws() {
        // Negative coordinates
        assertThrows(IllegalArgumentException.class, () -> mSession.tap(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> mSession.tap(1, -2));

        // Coordinates outside of display bounds
        assertThrows(IllegalArgumentException.class, () -> mSession.tap(WIDTH, 2));
        assertThrows(IllegalArgumentException.class, () -> mSession.tap(1, HEIGHT));
    }

    @Test
    public void tap_atBounds_succeeds() throws RemoteException {
        // Top-left corner
        mSession.tap(0, 0);
        verify(mMockSession).tap(0, 0);

        // Bottom-right corner
        mSession.tap(WIDTH - 1, HEIGHT - 1);
        verify(mMockSession).tap(WIDTH - 1, HEIGHT - 1);
    }

    @Test
    public void swipe_swipes() throws RemoteException {
        mSession.swipe(1, 2, 3, 4);
        verify(mMockSession).swipe(eq(1), eq(2), eq(3), eq(4));
    }

    @Test
    public void swipeNotInRange_throws() {
        // Negative coordinates
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(-1, 2, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(1, -2, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(1, 2, -3, 4));
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(1, 2, 3, -4));

        // Coordinates outside of display bounds
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(WIDTH, 2, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(1, HEIGHT, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(1, 2, WIDTH, 4));
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(1, 2, 3, HEIGHT));
    }

    @Test
    public void swipe_atBounds_succeeds() throws RemoteException {
        // Swipe from top-left to bottom-right
        mSession.swipe(0, 0, WIDTH - 1, HEIGHT - 1);
        verify(mMockSession).swipe(0, 0, WIDTH - 1, HEIGHT - 1);
    }

    @Test
    public void longPress_longPresses() throws RemoteException {
        mSession.longPress(1, 2);
        verify(mMockSession).longPress(eq(1), eq(2));
    }

    @Test
    public void longPressNotInRange_throws() {
        // Negative coordinates
        assertThrows(IllegalArgumentException.class, () -> mSession.longPress(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> mSession.longPress(1, -2));

        // Coordinates outside of display bounds
        assertThrows(IllegalArgumentException.class, () -> mSession.longPress(WIDTH, 2));
        assertThrows(IllegalArgumentException.class, () -> mSession.longPress(1, HEIGHT));
    }

    @Test
    public void longPress_atBounds_succeeds() throws RemoteException {
        // Top-left corner
        mSession.longPress(0, 0);
        verify(mMockSession).longPress(0, 0);

        // Bottom-right corner
        mSession.longPress(WIDTH - 1, HEIGHT - 1);
        verify(mMockSession).longPress(WIDTH - 1, HEIGHT - 1);
    }

    @Test
    public void setLifecycleCallback_providesLifecycleCallbacks() throws RemoteException {
        ComputerControlSession.LifecycleCallback mockCallback = Mockito.mock(
                ComputerControlSession.LifecycleCallback.class);

        mSession.setLifecycleCallback(mExecutor, mockCallback);

        mLifecycle.onClosed(123);
        verify(mockCallback).onClosed(eq(123));
        verify(mMockOnClosedRunnable).run();
    }

    @Test
    public void attachNotificationInfo_attachesNotificationInfo() throws RemoteException {
        final int notificationId = 5;
        final String notificationTag = "hello";
        mSession.attachNotificationInfo(notificationId, notificationTag);
        verify(mMockSession).attachNotificationInfo(eq(notificationId), eq(notificationTag));
    }

    @Test
    public void clearLifecycleCallback_stopsLifecycleCallbacks() throws RemoteException {
        ComputerControlSession.LifecycleCallback mockCallback = Mockito.mock(
                ComputerControlSession.LifecycleCallback.class);
        mSession.setLifecycleCallback(mExecutor, mockCallback);

        mSession.clearLifecycleCallback();

        mLifecycle.onClosed(123);
        verify(mockCallback, never()).onClosed(anyInt());
        verify(mMockOnClosedRunnable).run();
    }

    @Test
    public void setStabilityListener_withDuration_succeeds() {
        // Verifies that setting a stability listener with a valid duration, executor,
        // and listener does not throw an exception.
        mSession.setStabilityListener(Duration.ofMillis(500), mExecutor, mMockStabilityListener);

        mSession.clearStabilityListener();
    }

    @Test
    public void setStabilityListener_nullDuration_throwsException() {
        // Verifies that passing a null duration throws a NullPointerException, as expected from
        // the Objects.requireNonNull check.
        assertThrows(NullPointerException.class,
                () -> mSession.setStabilityListener(null, mExecutor, mMockStabilityListener));
    }

    @Test
    public void setStabilityListener_nullExecutor_throwsException() {
        // Verifies that passing a null executor throws a NullPointerException.
        assertThrows(NullPointerException.class,
                () -> mSession.setStabilityListener(
                        Duration.ofMillis(500), null, mMockStabilityListener));
    }

    @Test
    public void setStabilityListener_nullListener_throwsException() {
        // Verifies that passing a null listener throws a NullPointerException.
        assertThrows(NullPointerException.class,
                () -> mSession.setStabilityListener(Duration.ofMillis(500), mExecutor, null));
    }

    @Test
    public void setStabilityListener_calledTwice_throwsException() {
        // Sets a listener successfully.
        mSession.setStabilityListener(Duration.ofMillis(500), mExecutor, mMockStabilityListener);

        // Verifies that attempting to set another listener throws an IllegalStateException.
        assertThrows(IllegalStateException.class,
                () -> mSession.setStabilityListener(
                        Duration.ofMillis(500), mExecutor, mMockStabilityListener));
    }

    @Test
    public void clearStabilityListener_withoutListener_throwsException() {
        // Verifies that attempting to clear a listener when none has been set throws an
        // IllegalStateException.
        assertThrows(IllegalStateException.class, () -> mSession.clearStabilityListener());
    }

    @Test
    public void setStabilityListener_waitsForFirstFrame() throws Exception {
        mSession.setStabilityListener(Duration.ofMillis(0), mExecutor, mMockStabilityListener);
        // Tap to reset the idle state.
        mSession.tap(0, 0);

        verify(mMockStabilityListener, timeout(100))
                .onSessionUnstable(UNSTABLE_REASON_CALLER_INTERACTION);

        // Verify onSessionStable is NOT called initially
        verify(mMockStabilityListener, Mockito.after(200).never()).onSessionStable();

        drawFrame(mSurface);

        verify(mMockStabilityListener, timeout(FRAME_PROCESSING_DELAY_MS)).onSessionStable();
    }

    @Test
    public void setStabilityListener_waitsForFirstFrame_afterAccessibilityEvent()
            throws RemoteException {
        mLifecycle.onActive();
        verify(mA11yManager).registerDisplayProxy(mProxyCaptor.capture());
        var a11yProxy = mProxyCaptor.getValue();
        mSession.setStabilityListener(Duration.ofMillis(0), mExecutor, mMockStabilityListener);
        // Tap to reset the idle state.
        mSession.tap(0, 0);
        verify(mMockStabilityListener, timeout(100))
                .onSessionUnstable(UNSTABLE_REASON_CALLER_INTERACTION);
        verify(mMockStabilityListener, Mockito.after(100).never()).onSessionStable();

        // Verify listener is NOT called after accessibility event
        a11yProxy.onAccessibilityEvent(null);
        verify(mMockStabilityListener, Mockito.after(100).never()).onSessionStable();

        drawFrame(mSurface);

        verify(mMockStabilityListener, timeout(FRAME_PROCESSING_DELAY_MS)).onSessionStable();
    }

    @Test
    public void setStabilityListener_accessibilityEvent_extendsTimeout() throws RemoteException {
        mLifecycle.onActive();
        verify(mA11yManager).registerDisplayProxy(mProxyCaptor.capture());
        var a11yProxy = mProxyCaptor.getValue();
        drawFrame(mSurface);

        mSession.setStabilityListener(Duration.ofMillis(100), mExecutor, mMockStabilityListener);
        // Tap to reset the idle state.
        mSession.tap(0, 0);
        verify(mMockStabilityListener, timeout(100))
                .onSessionUnstable(UNSTABLE_REASON_CALLER_INTERACTION);

        for (int i = 0; i < 4; i++) {
            SystemClock.sleep(60);
            a11yProxy.onAccessibilityEvent(null);
        }
        verify(mMockStabilityListener, never()).onSessionStable();

        verify(mMockStabilityListener, timeout(2 * 100)).onSessionStable();
    }

    @Test
    public void setStabilityListener_additionalInteractions_extendsTimeout() {
        drawFrame(mSurface);

        mSession.setStabilityListener(Duration.ofMillis(100), mExecutor, mMockStabilityListener);
        // Tap to reset the idle state.
        mSession.tap(0, 0);
        verify(mMockStabilityListener, timeout(100))
                .onSessionUnstable(UNSTABLE_REASON_CALLER_INTERACTION);

        for (int i = 0; i < 4; i++) {
            SystemClock.sleep(60);
            mSession.tap(0, 0);
        }
        verify(mMockStabilityListener, never()).onSessionStable();

        verify(mMockStabilityListener, timeout(2 * 100)).onSessionStable();
    }

    @Test
    public void setStabilityListener_frameAvailableWithNoInteractions_noCallback() {
        mSession.setStabilityListener(Duration.ofMillis(0), mExecutor, mMockStabilityListener);

        drawFrame(mSurface);
        SystemClock.sleep(FRAME_PROCESSING_DELAY_MS);

        verifyNoInteractions(mMockStabilityListener);
    }

    @Test
    public void setStabilityListener_frameAlreadyAvailable_firesImmediately() throws Exception {
        drawFrame(mSurface);
        SystemClock.sleep(FRAME_PROCESSING_DELAY_MS);

        mSession.setStabilityListener(Duration.ofMillis(0), mExecutor, mMockStabilityListener);
        // Tap to reset the idle state.
        mSession.tap(0, 0);

        verify(mMockStabilityListener, timeout(100))
                .onSessionUnstable(UNSTABLE_REASON_CALLER_INTERACTION);
        verify(mMockStabilityListener, timeout(100)).onSessionStable();
    }

    @Test
    public void setStabilityListener_frameAlreadyAvailable_firesAfterAccessibilityEvent()
            throws RemoteException {
        mLifecycle.onActive();
        verify(mA11yManager).registerDisplayProxy(mProxyCaptor.capture());
        var a11yProxy = mProxyCaptor.getValue();
        drawFrame(mSurface);
        SystemClock.sleep(FRAME_PROCESSING_DELAY_MS);

        mSession.setStabilityListener(Duration.ofMillis(100), mExecutor, mMockStabilityListener);
        // Tap to reset the idle state.
        mSession.tap(0, 0);
        verify(mMockStabilityListener, timeout(100))
                .onSessionUnstable(UNSTABLE_REASON_CALLER_INTERACTION);
        a11yProxy.onAccessibilityEvent(null);

        verify(mMockStabilityListener, timeout(2 * 100)).onSessionStable();
    }

    @Test
    public void requestScreenshot_sessionActive_succeeds() throws RemoteException {
        var callback = Mockito.mock(ScreenshotCallback.class);
        mLifecycle.onActive();

        when(mMockSession.requestScreenshot()).thenReturn(true);
        mSession.requestScreenshot(mExecutor, callback, null);

        verify(mMockSession).requestScreenshot();
        verify(mMockSession, never()).notifyScreenshotResult();
        verify(callback, never()).onError(any());
    }

    @Test
    public void requestScreenshot_sessionBlocked_succeeds() throws RemoteException {
        var callback = Mockito.mock(ScreenshotCallback.class);
        mLifecycle.onBlocked(ComputerControlSession.BLOCK_REASON_UNKNOWN, "ABC");

        when(mMockSession.requestScreenshot()).thenReturn(true);
        mSession.requestScreenshot(mExecutor, callback, null);

        verify(mMockSession).requestScreenshot();
        verify(mMockSession, never()).notifyScreenshotResult();
        verify(callback, never()).onError(any());
    }

    @Test
    public void requestScreenshot_sessionClosed_fails() throws RemoteException {
        var callback = Mockito.mock(ScreenshotCallback.class);
        mSession.close();
        mLifecycle.onClosed(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED);

        when(mMockSession.requestScreenshot()).thenReturn(true);
        mSession.requestScreenshot(mExecutor, callback, null);

        verify(mMockSession, never()).requestScreenshot();
        verify(mMockSession, never()).notifyScreenshotResult();
        verify(callback).onError(withCode(ScreenshotException.ERROR_PROHIBITED));
    }

    @Test
    public void requestScreenshot_remoteRequestFails_fails() throws RemoteException {
        mLifecycle.onActive();
        when(mMockSession.requestScreenshot()).thenReturn(false);

        var callback = Mockito.mock(ScreenshotCallback.class);
        mSession.requestScreenshot(mExecutor, callback, null);

        verify(mMockSession).requestScreenshot();
        verify(mMockSession, never()).notifyScreenshotResult();
        verify(callback).onError(withCode(ScreenshotException.ERROR_INTERNAL));
    }

    @Test
    public void requestScreenshot_remoteRequestSucceeds_callbackPending() throws RemoteException {
        mLifecycle.onActive();
        when(mMockSession.requestScreenshot()).thenReturn(true);

        var callback = Mockito.mock(ScreenshotCallback.class);
        mSession.requestScreenshot(mExecutor, callback, null);

        verify(mMockSession).requestScreenshot();
        verify(mMockSession, never()).notifyScreenshotResult();
        verifyNoInteractions(callback);
    }

    @Test
    public void requestScreenshot_concurrentRequests_fails() throws RemoteException {
        mLifecycle.onActive();
        when(mMockSession.requestScreenshot()).thenReturn(true);

        var callback1 = Mockito.mock(ScreenshotCallback.class);
        mSession.requestScreenshot(mExecutor, callback1, null);

        var callback2 = Mockito.mock(ScreenshotCallback.class);
        mSession.requestScreenshot(mExecutor, callback2, null);

        verifyNoInteractions(callback1);
        verify(callback2).onError(withCode(ScreenshotException.ERROR_DUPLICATE_REQUEST));
        verify(mMockSession, times(1)).requestScreenshot();
        verify(mMockSession, never()).notifyScreenshotResult();
    }

    @Test
    public void requestScreenshot_remoteException_fails() throws RemoteException {
        mLifecycle.onActive();
        when(mMockSession.requestScreenshot()).thenThrow(new RemoteException());
        var callback = Mockito.mock(ScreenshotCallback.class);

        mSession.requestScreenshot(mExecutor, callback, null);

        verify(callback).onError(withCode(ScreenshotException.ERROR_REMOTE));
        verify(mMockSession, never()).notifyScreenshotResult();
    }

    @Test
    public void requestScreenshot_imageAvailable_succeeds() throws Exception {
        mLifecycle.onActive();
        when(mMockSession.requestScreenshot()).thenReturn(true);
        var callback = Mockito.mock(ScreenshotCallback.class);

        mSession.requestScreenshot(mExecutor, callback, null);

        drawFrame(mSurface);

        verify(callback, timeout(FRAME_PROCESSING_DELAY_MS)).onResult(notNull());
        verify(mMockSession).notifyScreenshotResult();
    }

    @Test
    public void requestScreenshot_remoteRequestRacesImageAvailable_succeeds()
            throws RemoteException {
        mLifecycle.onActive();
        when(mMockSession.requestScreenshot()).thenAnswer((inv) -> {
            drawFrame(mSurface);
            SystemClock.sleep(FRAME_PROCESSING_DELAY_MS);
            return true;
        });

        var callback = Mockito.mock(ScreenshotCallback.class);
        mSession.requestScreenshot(mExecutor, callback, null);

        verify(mMockSession).requestScreenshot();
        verify(mMockSession).notifyScreenshotResult();
        verify(callback).onResult(notNull());
    }

    @Test
    public void requestScreenshot_multipleFramesDrawn_usesLatestImage() throws Exception {
        mLifecycle.onActive();
        when(mMockSession.requestScreenshot()).thenAnswer((inv) -> {
            // Draw multiple frames before the remote request completes
            drawFrame(mSurface);
            SystemClock.sleep(FRAME_PROCESSING_DELAY_MS);
            drawFrame(mSurface);
            SystemClock.sleep(FRAME_PROCESSING_DELAY_MS);
            return true;
        });

        var callback = Mockito.mock(ScreenshotCallback.class);
        mSession.requestScreenshot(mExecutor, callback, null);

        verify(mMockSession).requestScreenshot();
        verify(mMockSession).notifyScreenshotResult();
        verify(callback).onResult(notNull());
    }

    @Test
    public void requestScreenshot_failedRemoteRequestRacesImageAvailable_succeeds()
            throws RemoteException {
        mLifecycle.onActive();
        // Set up a situation where the remote request will fail and return false, but the
        // ImageReader still happens to get a new frame within that time.
        when(mMockSession.requestScreenshot()).thenAnswer((inv) -> {
            drawFrame(mSurface);
            SystemClock.sleep(FRAME_PROCESSING_DELAY_MS);
            return false;
        });

        var callback = Mockito.mock(ScreenshotCallback.class);
        mSession.requestScreenshot(mExecutor, callback, null);

        verify(mMockSession).requestScreenshot();
        verify(mMockSession, never()).notifyScreenshotResult();
        verify(callback).onResult(notNull());
    }

    @Test
    public void getScreenshot_returnsImage() throws Exception {
        mLifecycle.onActive();
        when(mMockSession.requestScreenshot()).thenAnswer(invocation -> {
            drawFrame(mSurface);
            return true;
        });

        Image image = mSession.getScreenshot();
        assertThat(image).isNotNull();
        image.close();
    }

    @Test
    public void getScreenshot_requestReturnsFalse_returnsNull() throws RemoteException {
        mLifecycle.onActive();
        when(mMockSession.requestScreenshot()).thenReturn(false);

        Image image = mSession.getScreenshot();
        assertThat(image).isNull();
    }

    @Test
    public void requestScreenshot_canBeCancelled() throws RemoteException {
        mLifecycle.onActive();
        when(mMockSession.requestScreenshot()).thenReturn(true);
        var callback = Mockito.mock(ScreenshotCallback.class);
        var canceller = new CancellationSignal();

        mSession.requestScreenshot(mExecutor, callback, canceller);
        canceller.cancel();

        verify(mMockSession).requestScreenshot();
        verify(callback, timeout(100)).onError(withCode(ScreenshotException.ERROR_CANCELED));
        verify(mMockSession).notifyScreenshotResult();
    }

    @Test
    public void requestScreenshot_afterCancelledRequest_succeeds() throws RemoteException {
        mLifecycle.onActive();
        when(mMockSession.requestScreenshot()).thenReturn(true);
        var callback = Mockito.mock(ScreenshotCallback.class);
        var canceller = new CancellationSignal();

        mSession.requestScreenshot(mExecutor, callback, canceller);
        canceller.cancel();
        verify(callback, timeout(100)).onError(withCode(ScreenshotException.ERROR_CANCELED));
        clearInvocations(callback);

        mSession.requestScreenshot(mExecutor, callback, null);

        drawFrame(mSurface);

        verify(callback, timeout(100)).onResult(notNull());
    }

    @Test
    public void requestScreenshot_withCancelledRequest_fails() throws RemoteException {
        mLifecycle.onActive();
        when(mMockSession.requestScreenshot()).thenReturn(true);
        var callback = Mockito.mock(ScreenshotCallback.class);
        var canceller = new CancellationSignal();

        // Use an already-cancelled signal, and let the cancellation race the frame draw.
        canceller.cancel();
        mSession.requestScreenshot(mExecutor, callback, canceller);
        drawFrame(mSurface);

        verify(mMockSession).requestScreenshot();
        verify(mMockSession).notifyScreenshotResult();
        verify(callback, timeout(100)).onError(withCode(ScreenshotException.ERROR_CANCELED));
        clearInvocations(callback);
    }

    private static void drawFrame(Surface surface) {
        Canvas canvas;
        try {
            canvas = surface.lockCanvas(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        surface.unlockCanvasAndPost(canvas);
    }

    /** Type alias for ScreenshotCallback. */
    private interface ScreenshotCallback extends OutcomeReceiver<Image, ScreenshotException> {
    }

    /** Syntactic sugar for an error code matcher for ScreenshotExceptions. */
    private static ScreenshotException withCode(@ScreenshotException.ErrorCode int code) {
        return Mockito.argThat(exception -> exception.getErrorCode() == code);
    }

    /**
     * A mock Executor that runs the runnable immediately on the calling thread.
     */
    private static final class TestExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
