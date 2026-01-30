/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.window;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.INVALID_WINDOW_TYPE;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;
import static android.window.WindowProvider.KEY_REPARENT_TO_DEFAULT_DISPLAY_WITH_DISPLAY_REMOVAL;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.EmptyActivity;
import android.app.Instrumentation;
import android.app.WindowConfiguration;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.PollingCheck;
import android.view.Display;
import android.view.IWindowManager;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams.WindowType;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerImpl;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.frameworks.coretests.R;
import com.android.internal.jank.Cuj;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.util.GcUtils;
import com.android.window.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link WindowContext}
 *
 * <p>Build/Install/Run:
 *  atest FrameworksCoreTests:WindowContextTest
 *
 * <p>This test class is a part of Window Manager Service tests and specified in
 * {@link com.android.server.wm.test.filters.FrameworksTestsFilter}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class WindowContextTest {
    @Rule
    public ActivityTestRule<EmptyActivity> mActivityRule =
            new ActivityTestRule<>(EmptyActivity.class, false /* initialTouchMode */,
                    false /* launchActivity */);
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final WindowContext mWindowContext = createWindowContext();
    private final IWindowManager mWms = WindowManagerGlobal.getWindowManagerService();
    private WindowTokenClientController mOriginalWindowTokenClientController;

    private static final int TIMEOUT_IN_SECONDS = 4;

    @Before
    public void setUp() {
        // Keeping the original to set it back after each test, in case they applied any override.
        mOriginalWindowTokenClientController = WindowTokenClientController.getInstance();
    }

    @After
    public void tearDown() {
        WindowTokenClientController.overrideForTesting(mOriginalWindowTokenClientController);
    }

    @Test
    public void testCreateWindowContextWindowManagerAttachClientToken() {
        final WindowManager windowContextWm = WindowManagerImpl
                .createWindowContextWindowManager(mWindowContext);
        final WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY);
        mInstrumentation.runOnMainSync(() -> {
            final View view = new View(mWindowContext);
            windowContextWm.addView(view, params);
        });

        assertEquals(mWindowContext.getWindowContextToken(), params.mWindowContextToken);
    }

    /**
     * Test the {@link WindowContext} life cycle behavior to add a new window token:
     * <ul>
     *  <li>The window token is created before adding the first view.</li>
     *  <li>The window token is registered after adding the first view.</li>
     *  <li>The window token is removed after {@link WindowContext}'s release.</li>
     * </ul>
     */
    @Test
    public void testCreateWindowContextNewTokenFromClient() throws Throwable {
        final IBinder token = mWindowContext.getWindowContextToken();

        // Test that the window token is not created yet.
        assertFalse("Token must not be registered until adding the first window",
                mWms.isWindowToken(token));

        final WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY);
        final View testView = new View(mWindowContext);

        final CountDownLatch latch = new CountDownLatch(1);
        testView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                latch.countDown();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {}
        });

        mInstrumentation.runOnMainSync(() -> {
            mWindowContext.getSystemService(WindowManager.class).addView(testView, params);

            assertEquals(token, params.mWindowContextToken);
        });


        assertTrue(latch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));


        // Verify that the window token of the window context is created after first addView().
        assertTrue("Token must exist after adding the first view.",
                mWms.isWindowToken(token));

        mWindowContext.release();

        // After the window context's release, the window token is also removed.
        PollingCheck.waitFor(() -> {
            try {
                return !mWms.isWindowToken(token);
            } catch (RemoteException e) {
                fail("Fail to call isWindowToken:" + e);
                return false;
            }
        });
    }

    @EnableFlags(android.view.accessibility.Flags.FLAG_FORCE_INVERT_COLOR)
    @Test
    public void testCreateWindowContext_applicationTheme() {
        final WindowContext windowContext = createWindowContext();

        final int applicationTheme = mInstrumentation.getTargetContext().getApplicationInfo().theme;
        assertThat(windowContext.getThemeResId()).isEqualTo(applicationTheme);
    }


    /**
     * Verifies the behavior when window context attaches an {@link Activity} by override
     * {@link WindowManager.LayoutParams#token}.
     *
     * The window context token should be overridden to
     * {@link android.view.WindowManager.LayoutParams} and the {@link Activity}'s token must not be
     * removed regardless of release of window context.
     */
    @Test
    public void testCreateWindowContext_AttachActivity_TokenNotRemovedAfterRelease()
            throws Throwable {
        mActivityRule.launchActivity(new Intent());
        final Activity activity = mActivityRule.getActivity();
        final WindowManager.LayoutParams params = activity.getWindow().getAttributes();

        final WindowContext windowContext = createWindowContext(params.type);
        final IBinder token = windowContext.getWindowContextToken();

        final View testView = new View(windowContext);

        mInstrumentation.runOnMainSync(() -> {
            windowContext.getSystemService(WindowManager.class).addView(testView, params);

            assertEquals(token, params.mWindowContextToken);
        });
        windowContext.release();

        // Even if the window context is released, the activity should still exist.
        assertTrue("Token must exist even if the window context is released.",
                mWms.isWindowToken(activity.getActivityToken()));
    }

    /**
     * Verifies the behavior when window context attaches an existing token by override
     * {@link WindowManager.LayoutParams#token}.
     *
     * The window context token should be overridden to
     * {@link android.view.WindowManager.LayoutParams} and the {@link Activity}'s token must not be
     * removed regardless of release of window context.
     */
    @Test
    public void testCreateWindowContext_AttachWindowToken_TokenNotRemovedAfterRelease()
            throws Throwable {
        final WindowContext windowContext = createWindowContext(TYPE_INPUT_METHOD);
        final IBinder token = windowContext.getWindowContextToken();

        final IBinder existingToken = new Binder();
        mWms.addWindowToken(existingToken, TYPE_INPUT_METHOD, windowContext.getDisplayId(),
                null /* options */);

        final WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(TYPE_INPUT_METHOD);
        params.token = existingToken;
        final View testView = new View(windowContext);

        mInstrumentation.runOnMainSync(() -> {
            windowContext.getSystemService(WindowManager.class).addView(testView, params);

            assertEquals(token, params.mWindowContextToken);
        });
        windowContext.release();

        // Even if the window context is released, the existing token should still exist.
        assertTrue("Token must exist even if the window context is released.",
                mWms.isWindowToken(existingToken));

        mWms.removeWindowToken(existingToken, DEFAULT_DISPLAY);
    }

    @Test
    public void testWindowContextAddViewWithSubWindowType_NotCrash() throws Throwable {
        final WindowContext windowContext = createWindowContext(TYPE_INPUT_METHOD);
        final WindowManager wm = windowContext.getSystemService(WindowManager.class);

        // Create a WindowToken with system window type.
        final IBinder existingToken = new Binder();
        mWms.addWindowToken(existingToken, TYPE_INPUT_METHOD, windowContext.getDisplayId(),
                null /* options */);

        final WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(TYPE_INPUT_METHOD);
        params.token = existingToken;
        final View parentWindow = new View(windowContext);

        final AttachStateListener listener = new AttachStateListener();
        parentWindow.addOnAttachStateChangeListener(listener);

        // Add the parent window
        mInstrumentation.runOnMainSync(() -> wm.addView(parentWindow, params));

        assertTrue(listener.mLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));

        final WindowManager.LayoutParams subWindowAttrs =
                new WindowManager.LayoutParams(TYPE_APPLICATION_ATTACHED_DIALOG);
        subWindowAttrs.token = parentWindow.getWindowToken();
        final View subWindow = new View(windowContext);

        // Add a window with sub-window type.
        mInstrumentation.runOnMainSync(() -> wm.addView(subWindow, subWindowAttrs));
    }

    @Test
    public void testGetCustomDrawable() {
        assertNotNull(mWindowContext.getResources().getDrawable(R.drawable.custom_drawable,
                null /* theme */));
    }

    @Test
    public void testRegisterComponentCallbacks() {
        final WindowContext windowContext = createWindowContext();
        final ConfigurationListener listener = new ConfigurationListener();

        windowContext.registerComponentCallbacks(listener);

        try {
            final Configuration config = new Configuration();
            config.windowConfiguration.setWindowingMode(
                    WindowConfiguration.WINDOWING_MODE_FREEFORM);
            config.windowConfiguration.setBounds(new Rect(0, 0, 100, 100));

            windowContext.dispatchConfigurationChanged(config);

            try {
                assertWithMessage("Waiting for onConfigurationChanged timeout.")
                        .that(listener.mLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                fail("Waiting for configuration changed failed because of " + e);
            }

            assertThat(listener.mConfiguration).isEqualTo(config);
        } finally {
            windowContext.unregisterComponentCallbacks(listener);
        }
    }

    @Test
    public void testRegisterComponentCallbacksOnWindowContextWrapper() {
        final WindowContext windowContext = createWindowContext();
        final Context wrapper = new ContextWrapper(windowContext);
        final ConfigurationListener listener = new ConfigurationListener();

        wrapper.registerComponentCallbacks(listener);

        try {
            final Configuration config = new Configuration();
            config.windowConfiguration.setWindowingMode(
                    WindowConfiguration.WINDOWING_MODE_FREEFORM);
            config.windowConfiguration.setBounds(new Rect(0, 0, 100, 100));

            windowContext.dispatchConfigurationChanged(config);

            try {
                assertWithMessage("Waiting for onConfigurationChanged timeout.")
                        .that(listener.mLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                fail("Waiting for configuration changed failed because of " + e);
            }

            assertThat(listener.mConfiguration).isEqualTo(config);
        } finally {
            wrapper.unregisterComponentCallbacks(listener);
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_REPARENT_WINDOW_TOKEN_API)
    public void reparentToDisplayId_wasAttached_reparentToDisplayAreaPropagatedToTokenController() {
        final WindowTokenClientController mockWindowTokenClientController =
                mock(WindowTokenClientController.class);
        when(mockWindowTokenClientController.attachToDisplayArea(any(), anyInt(), anyInt(),
                any())).thenReturn(true);
        WindowTokenClientController.overrideForTesting(mockWindowTokenClientController);

        mWindowContext.reparentToDisplay(DEFAULT_DISPLAY + 1);

        verify(mockWindowTokenClientController).reparentToDisplayArea(any(),
                /* displayId= */ eq(DEFAULT_DISPLAY + 1)
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_REPARENT_WINDOW_TOKEN_API)
    public void reparentToDisplayId_sameDisplayId_noReparenting() {
        final WindowTokenClientController mockWindowTokenClientController =
                mock(WindowTokenClientController.class);
        when(mockWindowTokenClientController.attachToDisplayArea(any(), anyInt(), anyInt(),
                any())).thenReturn(true);
        WindowTokenClientController.overrideForTesting(mockWindowTokenClientController);

        mWindowContext.reparentToDisplay(DEFAULT_DISPLAY);

        verify(mockWindowTokenClientController, never()).reparentToDisplayArea(any(),
                /* displayId= */ eq(DEFAULT_DISPLAY)
        );
    }

    @Test
    public void testAttachWindow() throws InterruptedException {
        final View window = new View(mWindowContext);
        final AttachStateListener listener = new AttachStateListener();
        window.addOnAttachStateChangeListener(listener);

        final WindowManager.LayoutParams subWindowParams =
                new WindowManager.LayoutParams(TYPE_APPLICATION_ATTACHED_DIALOG);

        final WindowManager wm = mWindowContext.getSystemService(WindowManager.class);
        assertThrows(WindowManager.BadTokenException.class,
                () -> mInstrumentation.runOnMainSync(() ->
                        wm.addView(new View(mWindowContext), subWindowParams)));

        mWindowContext.attachWindow(window);

        mInstrumentation.runOnMainSync(() ->
                wm.addView(window, new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY)));

        assertTrue(listener.mLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));

        mInstrumentation.runOnMainSync(() -> wm.addView(new View(mWindowContext), subWindowParams));

        assertThat(subWindowParams.token).isEqualTo(window.getWindowToken());
    }

    @Test
    public void testSetFallbackWindowType() {
        int windowType = INVALID_WINDOW_TYPE;
        mWindowContext.setFallbackWindowType(windowType);
        assertThat(mWindowContext.getFallbackWindowType()).isEqualTo(windowType);

        windowType = mWindowContext.getWindowType();
        mWindowContext.setFallbackWindowType(windowType);
        assertThat(mWindowContext.getFallbackWindowType()).isEqualTo(windowType);

        windowType = TYPE_APPLICATION_ATTACHED_DIALOG;
        mWindowContext.setFallbackWindowType(windowType);
        assertThat(mWindowContext.getFallbackWindowType()).isEqualTo(windowType);

        final int invalidType = TYPE_APPLICATION;
        assertThrows(IllegalArgumentException.class,
                () -> mWindowContext.setFallbackWindowType(invalidType));
    }

    @Test
    public void testSetFallbackWindowTypeAndAddView_invalidFallbackWindowType_noOverride() {
        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY);
        mWindowContext.setFallbackWindowType(INVALID_WINDOW_TYPE);
        final WindowManager wm = mWindowContext.getSystemService(WindowManager.class);
        mInstrumentation.runOnMainSync(() -> wm.addView(new View(mWindowContext), params));

        assertThat(params.type).isEqualTo(TYPE_APPLICATION_OVERLAY);
    }

    @Test
    public void testSetFallbackWindowTypeAndAddView_invalidWindowContextType_override() {
        int windowType = mWindowContext.getWindowType();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        mWindowContext.setFallbackWindowType(windowType);
        final WindowManager wm = mWindowContext.getSystemService(WindowManager.class);
        mInstrumentation.runOnMainSync(() -> wm.addView(new View(mWindowContext), params));

        assertThat(params.type).isEqualTo(windowType);
    }

    @Test
    public void testSetFallbackWindowTypeAndAddView_statusBarPanel_override() {
        int windowType = mWindowContext.getWindowType();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);
        mWindowContext.setFallbackWindowType(windowType);
        final WindowManager wm = mWindowContext.getSystemService(WindowManager.class);
        mInstrumentation.runOnMainSync(() -> wm.addView(new View(mWindowContext), params));

        assertThat(params.type).isEqualTo(windowType);
    }

    @Test
    public void testSetFallbackWindowTypeAndAddView_subWindowWithoutParent_throwException() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        mWindowContext.setFallbackWindowType(TYPE_APPLICATION_ATTACHED_DIALOG);
        final WindowManager wm = mWindowContext.getSystemService(WindowManager.class);

        assertThrows(IllegalArgumentException.class,
                () -> mInstrumentation.runOnMainSync(() ->
                        wm.addView(new View(mWindowContext), params)));
    }

    @Test
    public void testSetFallbackWindowTypeAndAddView_subWindowWithParent_override()
            throws InterruptedException {
        final View parentWindow = new View(mWindowContext);
        final AttachStateListener listener = new AttachStateListener();
        parentWindow.addOnAttachStateChangeListener(listener);
        mWindowContext.attachWindow(parentWindow);
        final WindowManager wm = mWindowContext.getSystemService(WindowManager.class);
        mInstrumentation.runOnMainSync(() -> wm.addView(
                parentWindow, new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY)));

        assertTrue(listener.mLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));

        mWindowContext.setFallbackWindowType(TYPE_APPLICATION_ATTACHED_DIALOG);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        mInstrumentation.runOnMainSync(() -> wm.addView(new View(mWindowContext), params));

        assertThat(params.type).isEqualTo(TYPE_APPLICATION_ATTACHED_DIALOG);
    }

    @Test
    public void testSetFallbackWindowTypeAndAddView_isSubWindow_noOverride()
            throws InterruptedException {
        final View parentWindow = new View(mWindowContext);
        final AttachStateListener listener = new AttachStateListener();
        parentWindow.addOnAttachStateChangeListener(listener);
        mWindowContext.attachWindow(parentWindow);
        final WindowManager wm = mWindowContext.getSystemService(WindowManager.class);
        mInstrumentation.runOnMainSync(() -> wm.addView(
                parentWindow, new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY)));

        assertTrue(listener.mLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));

        mWindowContext.setFallbackWindowType(TYPE_APPLICATION_ATTACHED_DIALOG);

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(TYPE_APPLICATION_SUB_PANEL);
        mInstrumentation.runOnMainSync(() -> wm.addView(new View(mWindowContext), params));

        assertThat(params.type).isEqualTo(TYPE_APPLICATION_SUB_PANEL);
    }

    @Test
    public void testSetFallbackWindowTypeAndAddView_typeMatch_noOverride()
            throws InterruptedException {
        final View parentWindow = new View(mWindowContext);
        final AttachStateListener listener = new AttachStateListener();
        parentWindow.addOnAttachStateChangeListener(listener);
        mWindowContext.attachWindow(parentWindow);
        final WindowManager wm = mWindowContext.getSystemService(WindowManager.class);
        mInstrumentation.runOnMainSync(() -> wm.addView(
                parentWindow, new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY)));

        assertTrue(listener.mLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));

        mWindowContext.setFallbackWindowType(TYPE_APPLICATION_ATTACHED_DIALOG);

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY);
        mInstrumentation.runOnMainSync(() -> wm.addView(new View(mWindowContext), params));

        assertThat(params.type).isEqualTo(TYPE_APPLICATION_OVERLAY);
    }

    @Test
    public void testSetFallbackWindowTypeAndUpdateLayout_diffType_noOverride() {
        final View view = new View(mWindowContext);
        mWindowContext.attachWindow(view);
        final WindowManager wm = mWindowContext.getSystemService(WindowManager.class);
        final WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY);

        mInstrumentation.runOnMainSync(() -> wm.addView(view, params));

        mWindowContext.setFallbackWindowType(TYPE_APPLICATION_ATTACHED_DIALOG);

        mInstrumentation.runOnMainSync(() -> wm.updateViewLayout(view, params));

        assertThat(params.type).isEqualTo(TYPE_APPLICATION_OVERLAY);
    }

    @Test
    public void testBuildInteractionJankMonitorConfigWithWindowAttached_notCrash() {
        final ApplicationInfo appInfo = mWindowContext.getApplicationInfo();
        // Enable hardware accelerated to initialize thread renderer, which is essential to
        // build InteractionJankMonitor Configuration
        final int origFlags = appInfo.flags;
        appInfo.flags |= ApplicationInfo.FLAG_HARDWARE_ACCELERATED;
        try {
            final View window = new View(mWindowContext);
            final AttachStateListener listener = new AttachStateListener();
            window.addOnAttachStateChangeListener(listener);

            final WindowManager wm = mWindowContext.getSystemService(WindowManager.class);
            mWindowContext.attachWindow(window);

            mInstrumentation.runOnMainSync(() ->
                    wm.addView(window, new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY)));

            try {
                assertTrue(listener.mLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                fail("Fail due to " + e);
            }

            assertThat(InteractionJankMonitor.Configuration.Builder.withView(
                    Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, window).build()).isNotNull();
        } finally {
            appInfo.flags = origFlags;
        }
    }

    @EnableFlags(Flags.FLAG_REPARENT_WINDOW_TOKEN_API)
    @Test
    public void testDisplayRemovePolicyReparentToDefault_notAddWindow_reparent() {
        testDisplayRemovePolicyReparentToDefault(false /* shouldVerifyAddingView */);
    }

    @EnableFlags(Flags.FLAG_REPARENT_WINDOW_TOKEN_API)
    @Test
    public void testDisplayRemovePolicyReparentToDefault_addWindow_reparent() {
        testDisplayRemovePolicyReparentToDefault(true /* shouldVerifyAddingView */);
    }

    private void testDisplayRemovePolicyReparentToDefault(boolean shouldVerifyAddingView) {
        final VirtualDisplay virtualDisplay = createVirtualDisplay();

        // Attach the WindowContext to the virtual display
        final Display display = virtualDisplay.getDisplay();
        final Bundle options = new Bundle();
        options.putBoolean(KEY_REPARENT_TO_DEFAULT_DISPLAY_WITH_DISPLAY_REMOVAL, true);
        final Context windowContext = mInstrumentation.getTargetContext().createWindowContext(
                display, TYPE_APPLICATION_OVERLAY, options);

        final int virtualDisplayId = display.getDisplayId();
        assertWithMessage("WindowContext must be attached to display#" + virtualDisplayId)
                .that(windowContext.getDisplay().getDisplayId()).isEqualTo(virtualDisplayId);

        if (shouldVerifyAddingView) {
            final View view = new View(windowContext);
            final AttachStateListener listener = new AttachStateListener();
            view.addOnAttachStateChangeListener(listener);

            mInstrumentation.runOnMainSync(() ->
                    windowContext.getSystemService(WindowManager.class)
                            .addView(view,
                                    new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY)));

            // Checks that the view is attached.
            try {
                assertThat(listener.mLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                fail("Failure due to " + e);
            }

        }
        virtualDisplay.release();

        PollingCheck.waitFor(() -> windowContext.getDisplayId() == DEFAULT_DISPLAY);
    }

    @NonNull
    private VirtualDisplay createVirtualDisplay() {
        final int width = 800;
        final int height = 480;
        final int density = 160;
        ImageReader reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888,
                2 /* maxImages */);
        final Context context = mInstrumentation.getTargetContext();
        final DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        return displayManager.createVirtualDisplay(
                WindowContextTest.class.getName(), width, height, density, reader.getSurface(),
                VIRTUAL_DISPLAY_FLAG_PUBLIC | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
    }

    private WindowContext createWindowContext() {
        return createWindowContext(TYPE_APPLICATION_OVERLAY);
    }

    private WindowContext createWindowContext(@WindowType int type) {
        final Context instContext = mInstrumentation.getTargetContext();
        final Display display = instContext.getSystemService(DisplayManager.class)
                .getDisplay(DEFAULT_DISPLAY);
        return (WindowContext) instContext.createWindowContext(display, type,  null /* options */);
    }

    @Test
    public void testWindowContextCleanup() {
        final WindowTokenClientController mockController = mock(WindowTokenClientController.class);
        doReturn(true).when(mockController).attachToDisplayArea(
                any(), anyInt(), anyInt(), any());
        doNothing().when(mockController).detachIfNeeded(any());
        WindowTokenClientController.overrideForTesting(mockController);

        WeakReference<WindowContext> windowContextRef = new WeakReference<>(createWindowContext());
        final WindowTokenClient token =
                (WindowTokenClient) windowContextRef.get().getWindowContextToken();

        GcUtils.runGcAndFinalizersSync();

        verify(mockController).detachIfNeeded(eq(token));
    }

    private static class ConfigurationListener implements ComponentCallbacks {
        private Configuration mConfiguration;
        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onConfigurationChanged(@NonNull Configuration newConfig) {
            mConfiguration = newConfig;
            mLatch.countDown();
        }

        @Override
        public void onLowMemory() {}
    }

    private static class AttachStateListener implements View.OnAttachStateChangeListener {
        final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onViewAttachedToWindow(View v) {
            mLatch.countDown();
        }

        @Override
        public void onViewDetachedFromWindow(View v) {}
    }
}
