/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.pm.ActivityInfo.FLAG_SHOW_WHEN_LOCKED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_BEHIND;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.os.Build.VERSION_CODES.P;
import static android.os.Build.VERSION_CODES.Q;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.FLAG_ALLOWS_CONTENT_MODE_SWITCH;
import static android.view.Display.FLAG_PRIVATE;
import static android.view.Display.FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS;
import static android.view.Display.FLAG_TRUSTED;
import static android.view.DisplayCutout.BOUNDS_POSITION_TOP;
import static android.view.DisplayCutout.fromBoundingRect;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;
import static android.view.WindowManager.LayoutParams.FIRST_SUB_WINDOW;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.WindowManager.LayoutParams.INVALID_WINDOW_TYPE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_UNRESTRICTED_GESTURE_EXCLUSION;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_STARTING;
import static android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_NOTIFICATION_SHADE;
import static android.view.WindowManager.LayoutParams.TYPE_SCREENSHOT;
import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_VOICE_INTERACTION;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;
import static android.view.WindowManager.TRANSIT_FLAG_AOD_APPEARING;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_APPEARING;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY;
import static android.window.DisplayAreaOrganizer.FEATURE_WINDOWED_MAGNIFICATION;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.reset;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.display.feature.flags.Flags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT;
import static com.android.server.wm.ActivityTaskSupervisor.ON_TOP;
import static com.android.server.wm.SurfaceAnimator.ANIMATION_TYPE_TOKEN_TRANSFORM;
import static com.android.server.wm.TransitionSubject.assertThat;
import static com.android.server.wm.WindowContainer.AnimationFlags.PARENTS;
import static com.android.server.wm.WindowContainer.POSITION_TOP;
import static com.android.server.wm.WindowManagerService.UPDATE_FOCUS_NORMAL;
import static com.android.window.flags.Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES;
import static com.android.window.flags.Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING;
import static com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE;
import static com.android.window.flags.Flags.FLAG_ENABLE_PERSISTING_DISPLAY_SIZE_FOR_CONNECTED_DISPLAYS;
import static com.android.window.flags.Flags.FLAG_ENABLE_WINDOW_REPOSITIONING_API;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.ActivityTaskManager;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.pm.ActivityInfo.ScreenOrientation;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.HardwareBuffer;
import android.metrics.LogMaker;
import android.os.Binder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.DisplayInfo;
import android.view.Gravity;
import android.view.IDisplayChangeWindowCallback;
import android.view.IDisplayChangeWindowController;
import android.view.ISystemGestureExclusionListener;
import android.view.IWindowManager;
import android.view.InsetsState;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams.WindowType;
import android.window.DisplayAreaInfo;
import android.window.IDisplayAreaOrganizer;
import android.window.ScreenCaptureInternal;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto;
import com.android.server.LocalServices;
import com.android.server.policy.WindowManagerPolicy;
import com.android.server.wm.utils.WmDisplayCutout;
import com.android.window.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Tests for the {@link DisplayContent} class.
 *
 * <p>Build/Install/Run:
 * atest WmTests:DisplayContentTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class DisplayContentTests extends WindowTestsBase {

    @SetupWindows(addAllCommonWindows = true)
    @Test
    public void testForAllWindows() {
        final var exitingAppWin = newWindowBuilder("exitingAppWin", TYPE_BASE_APPLICATION)
                .build();
        final ActivityRecord exitingApp = exitingAppWin.mActivityRecord;
        doReturn(true).when(exitingApp).isExitAnimationRunningSelfOrChild();
        exitingApp.mIsExiting = true;
        // If the activity is animating, its window should not be removed.
        mDisplayContent.handleCompleteDeferredRemoval();

        final var windows = new ArrayList<>(Arrays.asList(
                mWallpaperWindow,
                mChildAppWindowBelow,
                mAppWindow,
                mChildAppWindowAbove,
                exitingAppWin,
                mDockedDividerWindow,
                mImeWindow,
                mImeDialogWindow,
                mStatusBarWindow,
                mNotificationShadeWindow,
                mNavBarWindow));
        assertForAllWindowsOrder(windows);

        doReturn(false).when(exitingApp).isExitAnimationRunningSelfOrChild();
        // The exiting window will be removed because its parent is no longer animating.
        mDisplayContent.handleCompleteDeferredRemoval();
        windows.remove(exitingAppWin);
        assertForAllWindowsOrder(windows);
    }

    @SetupWindows(addAllCommonWindows = true)
    @Test
    public void testForAllWindows_WithAppImeTarget() {
        final var imeAppTarget = newWindowBuilder("imeAppTarget", TYPE_BASE_APPLICATION).build();

        mDisplayContent.setImeLayeringTarget(imeAppTarget);

        assertForAllWindowsOrder(Arrays.asList(
                mWallpaperWindow,
                mChildAppWindowBelow,
                mAppWindow,
                mChildAppWindowAbove,
                imeAppTarget,
                mImeWindow,
                mImeDialogWindow,
                mDockedDividerWindow,
                mStatusBarWindow,
                mNotificationShadeWindow,
                mNavBarWindow));
    }

    @SetupWindows(addAllCommonWindows = true)
    @Test
    public void testForAllWindows_WithChildWindowImeTarget() {
        mDisplayContent.setImeLayeringTarget(mChildAppWindowAbove);

        assertForAllWindowsOrder(Arrays.asList(
                mWallpaperWindow,
                mChildAppWindowBelow,
                mAppWindow,
                mChildAppWindowAbove,
                mImeWindow,
                mImeDialogWindow,
                mDockedDividerWindow,
                mStatusBarWindow,
                mNotificationShadeWindow,
                mNavBarWindow));
    }

    @SetupWindows(addAllCommonWindows = true)
    @Test
    public void testForAllWindows_WithStatusBarImeTarget() {
        mDisplayContent.setImeLayeringTarget(mStatusBarWindow);

        assertForAllWindowsOrder(Arrays.asList(
                mWallpaperWindow,
                mChildAppWindowBelow,
                mAppWindow,
                mChildAppWindowAbove,
                mDockedDividerWindow,
                mStatusBarWindow,
                mImeWindow,
                mImeDialogWindow,
                mNotificationShadeWindow,
                mNavBarWindow));
    }

    @SetupWindows(addAllCommonWindows = true)
    @Test
    public void testForAllWindows_WithNotificationShadeImeTarget() {
        mDisplayContent.setImeLayeringTarget(mNotificationShadeWindow);

        assertForAllWindowsOrder(Arrays.asList(
                mWallpaperWindow,
                mChildAppWindowBelow,
                mAppWindow,
                mChildAppWindowAbove,
                mDockedDividerWindow,
                mStatusBarWindow,
                mNotificationShadeWindow,
                mImeWindow,
                mImeDialogWindow,
                mNavBarWindow));
    }

    @SetupWindows(addAllCommonWindows = true)
    @Test
    public void testForAllWindows_WithInBetweenWindowToken() {
        // This window is set-up to be z-ordered between some windows that go in the same token like
        // the nav bar and status bar.
        final var voiceInteractionWindow = newWindowBuilder("voiceInteractionWindow",
                TYPE_VOICE_INTERACTION).build();

        assertForAllWindowsOrder(Arrays.asList(
                mWallpaperWindow,
                mChildAppWindowBelow,
                mAppWindow,
                mChildAppWindowAbove,
                mDockedDividerWindow,
                mImeWindow,
                mImeDialogWindow,
                mStatusBarWindow,
                mNotificationShadeWindow,
                voiceInteractionWindow, // It can show above lock screen.
                mNavBarWindow));
    }

    @SetupWindows(addAllCommonWindows = true)
    @Test
    public void testComputeImeLayeringTarget() {
        // Verify that an app window can be an IME layering target.
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).build();
        appWin.setHasSurface(true);
        assertTrue(appWin.canBeImeLayeringTarget());
        WindowState imeLayeringTarget =
                mDisplayContent.computeImeLayeringTarget(false /* update */);
        assertEquals(appWin, imeLayeringTarget);
        appWin.mHidden = false;

        // Verify that a child window can be an IME layering target.
        final var childWin = newWindowBuilder("childWin", TYPE_APPLICATION_ATTACHED_DIALOG)
                .setParent(appWin).build();
        childWin.setHasSurface(true);
        assertTrue(childWin.canBeImeLayeringTarget());
        imeLayeringTarget = mDisplayContent.computeImeLayeringTarget(false /* update */);
        assertEquals(childWin, imeLayeringTarget);
    }

    @SetupWindows(addAllCommonWindows = true)
    @Test
    public void testComputeImeLayeringTarget_startingWindow() {
        final ActivityRecord activity = createActivityRecord(mDisplayContent);

        final var startingWin = newWindowBuilder("startingWin", TYPE_APPLICATION_STARTING)
                .setWindowToken(activity).build();
        startingWin.setHasSurface(true);
        assertTrue(startingWin.canBeImeLayeringTarget());

        WindowState imeLayeringTarget =
                mDisplayContent.computeImeLayeringTarget(false /* update */);
        assertEquals(startingWin, imeLayeringTarget);
        startingWin.mHidden = false;

        // Verify that the starting window is still the IME layering target even when an app window
        // is launching behind it.
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION)
                .setWindowToken(activity).build();
        appWin.setHasSurface(true);
        assertTrue(appWin.canBeImeLayeringTarget());

        imeLayeringTarget = mDisplayContent.computeImeLayeringTarget(false /* update */);
        assertEquals(startingWin, imeLayeringTarget);
        appWin.mHidden = false;

        // Verify that the starting window is still the IME layering target even when there is a
        // child window behind a launching app window
        final var childWin = newWindowBuilder("childWin", TYPE_APPLICATION_ATTACHED_DIALOG)
                .setParent(appWin).build();
        childWin.setHasSurface(true);
        assertTrue(childWin.canBeImeLayeringTarget());
        imeLayeringTarget = mDisplayContent.computeImeLayeringTarget(false /* update */);
        assertEquals(startingWin, imeLayeringTarget);
    }

    @Test
    public void testUpdateImeParent_forceUpdateRelativeLayer() {
        final DisplayArea.Tokens imeContainer = mDisplayContent.getImeContainer();
        final ActivityRecord activity = createActivityRecord(mDisplayContent);

        final var startingWin = newWindowBuilder("startingWin", TYPE_APPLICATION_STARTING)
                .setWindowToken(activity).build();
        startingWin.setHasSurface(true);
        assertTrue(startingWin.canBeImeLayeringTarget());
        final WindowContainer imeSurfaceParentWindow = mock(WindowContainer.class);
        final SurfaceControl imeSurfaceParent = mock(SurfaceControl.class);
        doReturn(imeSurfaceParent).when(imeSurfaceParentWindow).getSurfaceControl();
        doReturn(imeSurfaceParentWindow).when(mDisplayContent).computeImeParent();
        spyOn(imeContainer);

        mDisplayContent.setImeInputTarget(startingWin);
        mDisplayContent.onConfigurationChanged(new Configuration());
        verify(mDisplayContent).updateImeParent();

        // Force reassign the relative layer when the IME surface parent is changed.
        verify(imeContainer).assignRelativeLayer(any(), eq(imeSurfaceParent), anyInt(), eq(true));
    }

    @Test
    public void testComputeImeLayeringTargetReturnsNull_windowDidntRequestIme() {
        final var appWin1 = newWindowBuilder("appWin1", TYPE_BASE_APPLICATION).build();
        final var appWin2 = newWindowBuilder("appWin2", TYPE_BASE_APPLICATION).build();

        mDisplayContent.setImeInputTarget(appWin1);
        mDisplayContent.setImeLayeringTarget(appWin2);

        doReturn(true).when(mDisplayContent).shouldImeAttachedToApp();
        // Compute IME parent returns nothing if current target and window receiving input
        // are different i.e. if current window didn't request IME.
        assertNull("computeImeParent() should be null", mDisplayContent.computeImeParent());
    }

    @Test
    @UseTestDisplay(addWindows = W_INPUT_METHOD)
    @RequiresFlagsEnabled(android.view.inputmethod.Flags.FLAG_REPORT_ANIMATING_INSETS_TYPES)
    public void testSetImeInputTargetNullResetsRemoteInsetsControlTargetImeVisibility()
            throws RemoteException {
        final var displayWindowInsetsController = spy(createDisplayWindowInsetsController());
        mDisplayContent.setRemoteInsetsController(displayWindowInsetsController);

        final var appWin = newWindowBuilder("appWin", TYPE_APPLICATION)
                .setWindowingMode(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW).build();
        final var remoteControlTarget = mDisplayContent.mRemoteInsetsControlTarget;

        // Set appWin as the IME input target.
        appWin.setRequestedVisibleTypes(WindowInsets.Type.ime());
        clearInvocations(displayWindowInsetsController);
        mDisplayContent.setImeInputTarget(appWin);
        mDisplayContent.setImeLayeringTarget(appWin);
        assertEquals("RemoteInsetsControlTarget should be the IME control target",
                remoteControlTarget, mDisplayContent.getImeControlTarget());
        assertTrue("appWin should have the IME requested visible",
                appWin.isRequestedVisible(WindowInsets.Type.ime()));

        // Set null input target
        mDisplayContent.setImeInputTarget(null /* target */);
        verify(displayWindowInsetsController).setImeInputTargetRequestedVisibility(
                eq(false) /* visible */, any());
    }

    @Test
    public void testUpdateImeParent_skipForOrganizedImeContainer() {
        final DisplayArea.Tokens imeContainer = mDisplayContent.getImeContainer();
        final ActivityRecord activity = createActivityRecord(mDisplayContent);

        final var startingWin = newWindowBuilder("startingWin", TYPE_APPLICATION_STARTING)
                .setWindowToken(activity).build();
        startingWin.setHasSurface(true);
        assertTrue(startingWin.canBeImeLayeringTarget());
        final WindowContainer imeSurfaceParentWindow = mock(WindowContainer.class);
        final SurfaceControl imeSurfaceParent = mock(SurfaceControl.class);
        doReturn(imeSurfaceParent).when(imeSurfaceParentWindow).getSurfaceControl();
        doReturn(imeSurfaceParentWindow).when(mDisplayContent).computeImeParent();

        // Main precondition for this test: organize the ImeContainer.
        final IDisplayAreaOrganizer mockImeOrganizer = mock(IDisplayAreaOrganizer.class);
        when(mockImeOrganizer.asBinder()).thenReturn(new Binder());
        imeContainer.setOrganizer(mockImeOrganizer);

        mDisplayContent.updateImeParent();

        assertNull("Don't reparent the surface of an organized ImeContainer.",
                mDisplayContent.mInputMethodSurfaceParent);

        // Clean up organizer.
        imeContainer.setOrganizer(null);
    }

    @Test
    public void testImeContainerIsReparentedUnderParentWhenOrganized() {
        final DisplayArea.Tokens imeContainer = mDisplayContent.getImeContainer();
        final ActivityRecord activity = createActivityRecord(mDisplayContent);

        final var startingWin = newWindowBuilder("startingWin", TYPE_APPLICATION_STARTING)
                .setWindowToken(activity).build();
        startingWin.setHasSurface(true);
        assertTrue(startingWin.canBeImeLayeringTarget());

        final Transaction transaction = mDisplayContent.getPendingTransaction();
        spyOn(transaction);

        // Organized the IME container.
        final IDisplayAreaOrganizer mockImeOrganizer = mock(IDisplayAreaOrganizer.class);
        when(mockImeOrganizer.asBinder()).thenReturn(new Binder());
        imeContainer.setOrganizer(mockImeOrganizer);

        // Verify that the IME container surface is reparented under
        // its parent surface as a consequence of the setOrganizer call.
        verify(transaction).reparent(imeContainer.getSurfaceControl(),
                imeContainer.getParentSurfaceControl());

        // Clean up organizer.
        imeContainer.setOrganizer(null);
    }

    /**
     * This tests root task movement between displays and proper root task's, task's and app token's
     * display container references updates.
     */
    @Test
    public void testMoveRootTaskBetweenDisplays() {
        // Create a second display.
        final DisplayContent dc = createNewDisplay();

        // Add root task with activity.
        final Task rootTask = createTask(dc);
        assertEquals(dc.getDisplayId(), rootTask.getDisplayContent().getDisplayId());
        assertEquals(dc, rootTask.getDisplayContent());

        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final ActivityRecord activity = createNonAttachedActivityRecord(dc);
        task.addChild(activity, 0);
        assertEquals(dc, task.getDisplayContent());
        assertEquals(dc, activity.getDisplayContent());

        // Move root task to first display.
        rootTask.reparent(mDisplayContent.getDefaultTaskDisplayArea(), true /* onTop */);
        assertEquals(mDisplayContent.getDisplayId(), rootTask.getDisplayContent().getDisplayId());
        assertEquals(mDisplayContent, rootTask.getDisplayContent());
        assertEquals(mDisplayContent, task.getDisplayContent());
        assertEquals(mDisplayContent, activity.getDisplayContent());
    }

    /**
     * This tests global configuration updates when default display config is updated.
     */
    @Test
    public void testDefaultDisplayOverrideConfigUpdate() {
        DisplayContent defaultDisplay = mWm.mRoot.getDisplayContent(DEFAULT_DISPLAY);
        final Configuration currentConfig = defaultDisplay.getConfiguration();

        // Create new, slightly changed override configuration and apply it to the display.
        final Configuration newOverrideConfig = new Configuration(currentConfig);
        newOverrideConfig.densityDpi += 120;
        newOverrideConfig.fontScale += 0.3f;

        defaultDisplay.updateDisplayOverrideConfigurationLocked(newOverrideConfig,
                null /* starting */, false /* deferResume */);

        // Check that global configuration is updated, as we've updated default display's config.
        Configuration globalConfig = mWm.mRoot.getConfiguration();
        assertEquals(newOverrideConfig.densityDpi, globalConfig.densityDpi);
        assertEquals(newOverrideConfig.fontScale, globalConfig.fontScale, 0.1 /* delta */);

        // Return back to original values.
        defaultDisplay.updateDisplayOverrideConfigurationLocked(currentConfig,
                null /* starting */, false /* deferResume */);
        globalConfig = mWm.mRoot.getConfiguration();
        assertEquals(currentConfig.densityDpi, globalConfig.densityDpi);
        assertEquals(currentConfig.fontScale, globalConfig.fontScale, 0.1 /* delta */);
    }

    @Test
    public void testFocusedWindowMultipleDisplays() {
        doTestFocusedWindowMultipleDisplays(false /* perDisplayFocusEnabled */, Q);
    }

    @Test
    public void testFocusedWindowMultipleDisplaysPerDisplayFocusEnabled() {
        doTestFocusedWindowMultipleDisplays(true /* perDisplayFocusEnabled */, Q);
    }

    @Test
    public void testFocusedWindowMultipleDisplaysPerDisplayFocusEnabledLegacyApp() {
        doTestFocusedWindowMultipleDisplays(true /* perDisplayFocusEnabled */, P);
    }

    private void doTestFocusedWindowMultipleDisplays(boolean perDisplayFocusEnabled,
            int targetSdk) {
        mWm.mPerDisplayFocusEnabled = perDisplayFocusEnabled;

        // Create a focusable window and check that focus is calculated correctly
        final var appWin1 = newWindowBuilder("appWin1", TYPE_BASE_APPLICATION).build();
        appWin1.mActivityRecord.mTargetSdk = targetSdk;
        updateFocusedWindow();
        assertTrue(appWin1.isFocused());
        assertEquals(appWin1, mWm.mRoot.getTopFocusedDisplayContent().mCurrentFocus);

        // Check that a new display doesn't affect focus
        final DisplayContent dc = createNewDisplay();
        updateFocusedWindow();
        assertTrue(appWin1.isFocused());
        assertEquals(appWin1, mWm.mRoot.getTopFocusedDisplayContent().mCurrentFocus);

        // Add a window to the second display, and it should be focused
        final ActivityRecord app2 = new ActivityBuilder(mAtm)
                .setTask(new TaskBuilder(mSupervisor).setDisplay(dc).build())
                .setUseProcess(appWin1.getProcess()).setOnTop(true).build();
        final var appWin2 = newWindowBuilder("appWin2", TYPE_BASE_APPLICATION)
                .setWindowToken(app2).build();
        appWin2.mActivityRecord.mTargetSdk = targetSdk;
        updateFocusedWindow();
        assertTrue(appWin2.isFocused());
        assertEquals(perDisplayFocusEnabled && targetSdk >= Q, appWin1.isFocused());
        assertEquals(appWin2, mWm.mRoot.getTopFocusedDisplayContent().mCurrentFocus);

        // Move the first window to top including parents, and make sure focus is updated
        appWin1.getParent().positionChildAt(POSITION_TOP, appWin1, true);
        updateFocusedWindow();
        assertTrue(appWin1.isFocused());
        assertEquals(perDisplayFocusEnabled && targetSdk >= Q, appWin2.isFocused());
        assertEquals(appWin1, mWm.mRoot.getTopFocusedDisplayContent().mCurrentFocus);

        // Make sure top focused display not changed if there is a focused app.
        appWin1.mActivityRecord.setVisibleRequested(false);
        appWin1.getDisplayContent().setFocusedApp(appWin1.mActivityRecord);
        updateFocusedWindow();
        assertFalse(appWin1.isFocused());
        assertEquals(appWin1.getDisplayId(),
                mWm.mRoot.getTopFocusedDisplayContent().getDisplayId());
    }

    @Test
    public void testShouldWaitForSystemDecorWindowsOnBoot_OnDefaultDisplay() {
        mWm.mSystemBooted = true;
        final DisplayContent defaultDisplay = mWm.getDefaultDisplayContentLocked();
        final WindowState[] windows = createNotDrawnWindowsOn(defaultDisplay,
                TYPE_WALLPAPER, TYPE_APPLICATION);
        final WindowState wallpaper = windows[0];
        assertTrue(wallpaper.mIsWallpaper);
        wallpaper.mToken.asWallpaperToken().setVisibility(false);
        // By default WindowState#mWallpaperVisible is false.
        assertFalse(wallpaper.isVisible());

        // Verify waiting for windows to be drawn.
        assertTrue(defaultDisplay.shouldWaitForSystemDecorWindowsOnBoot());

        // Verify not waiting for drawn window and invisible wallpaper.
        setDrawnState(WindowStateAnimator.READY_TO_SHOW, wallpaper);
        setDrawnState(WindowStateAnimator.HAS_DRAWN, windows[1]);
        assertFalse(defaultDisplay.shouldWaitForSystemDecorWindowsOnBoot());
    }

    @Test
    public void testShouldWaitForSystemDecorWindowsOnBoot_OnSecondaryDisplay() {
        mWm.mSystemBooted = true;
        final DisplayContent secondaryDisplay = createNewDisplay();
        final WindowState[] windows = createNotDrawnWindowsOn(secondaryDisplay,
                TYPE_WALLPAPER, TYPE_APPLICATION);

        // Verify not waiting for display without system decorations.
        doReturn(false).when(secondaryDisplay).isSystemDecorationsSupported();
        assertFalse(secondaryDisplay.shouldWaitForSystemDecorWindowsOnBoot());

        // Verify waiting for non-drawn windows on display with system decorations.
        reset(secondaryDisplay);
        doReturn(true).when(secondaryDisplay).isSystemDecorationsSupported();
        assertTrue(secondaryDisplay.shouldWaitForSystemDecorWindowsOnBoot());

        // Verify not waiting for drawn windows on display with system decorations.
        setDrawnState(WindowStateAnimator.HAS_DRAWN, windows);
        assertFalse(secondaryDisplay.shouldWaitForSystemDecorWindowsOnBoot());
    }

    @Test
    public void testDisplayHasContent() {
        final var appWin = newWindowBuilder("window", TYPE_BASE_APPLICATION).build();
        setDrawnState(WindowStateAnimator.COMMIT_DRAW_PENDING, appWin);
        assertFalse(mDisplayContent.getLastHasContent());
        // The pending draw state should be committed and the has-content state is also updated.
        mDisplayContent.applySurfaceChangesTransaction();
        assertTrue(appWin.isDrawn());
        assertTrue(mDisplayContent.getLastHasContent());
        // If the only window is no longer visible, has-content will be false.
        setDrawnState(WindowStateAnimator.NO_SURFACE, appWin);
        mDisplayContent.applySurfaceChangesTransaction();
        assertFalse(mDisplayContent.getLastHasContent());
    }

    @Test
    public void testImeIsAttachedToDisplayForLetterboxedApp() {
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).build();
        mDisplayContent.setImeInputTarget(appWin);
        mDisplayContent.setImeLayeringTarget(appWin);

        // Adjust bounds so that matchesRootDisplayAreaBounds() returns false.
        final Rect bounds = new Rect(mDisplayContent.getBounds());
        bounds.scale(0.5f);
        appWin.mActivityRecord.setBounds(bounds);
        assertFalse("matchesRootDisplayAreaBounds() should return false",
                appWin.matchesDisplayAreaBounds());

        assertNotEquals("IME shouldn't be attached to app",
                mDisplayContent.computeImeParent().getSurfaceControl(),
                mDisplayContent.getImeLayeringTarget().mActivityRecord.getSurfaceControl());
        assertEquals("IME should be attached to display",
                mDisplayContent.getImeContainer().getParent().getSurfaceControl(),
                mDisplayContent.computeImeParent().getSurfaceControl());
    }

    @NonNull
    private WindowState[] createNotDrawnWindowsOn(@NonNull DisplayContent displayContent,
            @WindowType int... types) {
        final var windows = new WindowState[types.length];
        for (int i = 0; i < types.length; i++) {
            final int type = types[i];
            windows[i] = newWindowBuilder("window-" + type, type).setDisplay(displayContent)
                    .build();
            windows[i].setHasSurface(true);
            windows[i].mWinAnimator.mDrawState = WindowStateAnimator.DRAW_PENDING;
        }
        return windows;
    }

    private static void setDrawnState(int state, @NonNull WindowState... windows) {
        for (WindowState window : windows) {
            window.setHasSurface(state != WindowStateAnimator.NO_SURFACE);
            window.mWinAnimator.mDrawState = state;
        }
    }

    /**
     * This tests setting the maximum ui width on a display.
     */
    @Test
    public void testMaxUiWidth() {
        // Prevent base display metrics for test from being updated to the value of real display.
        final DisplayContent dc = createDisplayNoUpdateDisplayInfo();
        final int baseWidth = 1440;
        final int baseHeight = 2560;
        final int baseDensity = 300;
        final float baseXDpi = 60;
        final float baseYDpi = 60;

        dc.updateBaseDisplayMetrics(baseWidth, baseHeight, baseDensity, baseXDpi, baseYDpi);

        final int maxWidth = 300;
        final float ratioChange = maxWidth / (float) baseWidth;
        final int resultingHeight = (int) (baseHeight * ratioChange);
        final int resultingDensity = (int) (baseDensity * ratioChange);
        final float resultingXDpi = baseXDpi * ratioChange;
        final float resultingYDpi = baseYDpi * ratioChange;

        dc.setMaxUiWidth(maxWidth);
        verifySizes(dc, maxWidth, resultingHeight, resultingDensity, resultingXDpi, resultingYDpi);

        // Assert setting values again does not change;
        dc.updateBaseDisplayMetrics(baseWidth, baseHeight, baseDensity, baseXDpi, baseYDpi);
        verifySizes(dc, maxWidth, resultingHeight, resultingDensity, resultingXDpi, resultingYDpi);

        final int smallerWidth = 200;
        final int smallerHeight = 400;
        final int smallerDensity = 100;

        // Specify smaller dimension, verify that it is honored
        dc.updateBaseDisplayMetrics(smallerWidth, smallerHeight, smallerDensity, baseXDpi,
                baseYDpi);
        verifySizes(dc, smallerWidth, smallerHeight, smallerDensity, baseXDpi, baseYDpi);

        // Verify that setting the max width to a greater value than the base width has no effect
        dc.setMaxUiWidth(maxWidth);
        verifySizes(dc, smallerWidth, smallerHeight, smallerDensity, baseXDpi, baseYDpi);
    }

    @Test
    public void testSetForcedSize() {
        // Prevent base display metrics for test from being updated to the value of real display.
        final DisplayContent dc = createDisplayNoUpdateDisplayInfo();
        final int baseWidth = 1280;
        final int baseHeight = 720;
        final int baseDensity = 320;
        final float baseXDpi = 60;
        final float baseYDpi = 60;

        dc.mInitialDisplayWidth = baseWidth;
        dc.mInitialDisplayHeight = baseHeight;
        dc.mInitialDisplayDensity = baseDensity;
        dc.updateBaseDisplayMetrics(baseWidth, baseHeight, baseDensity, baseXDpi, baseYDpi);

        final int forcedWidth = 1920;
        final int forcedHeight = 1080;

        // Verify that forcing the size is honored and the density doesn't change.
        dc.setForcedSize(forcedWidth, forcedHeight);
        verifySizes(dc, forcedWidth, forcedHeight, baseDensity);

        // Verify that forcing the size is idempotent.
        dc.setForcedSize(forcedWidth, forcedHeight);
        verifySizes(dc, forcedWidth, forcedHeight, baseDensity);
    }

    @Test
    public void testSetForcedSize_WithMaxUiWidth() {
        // Prevent base display metrics for test from being updated to the value of real display.
        final DisplayContent dc = createDisplayNoUpdateDisplayInfo();
        final int baseWidth = 1280;
        final int baseHeight = 720;
        final int baseDensity = 320;
        final float baseXDpi = 60;
        final float baseYDpi = 60;

        dc.mInitialDisplayWidth = baseWidth;
        dc.mInitialDisplayHeight = baseHeight;
        dc.mInitialDisplayDensity = baseDensity;
        dc.updateBaseDisplayMetrics(baseWidth, baseHeight, baseDensity, baseXDpi, baseYDpi);

        dc.setMaxUiWidth(baseWidth);

        final int forcedWidth = 1920;
        final int forcedHeight = 1080;

        // Verify that forcing bigger size doesn't work and density doesn't change.
        dc.setForcedSize(forcedWidth, forcedHeight);
        verifySizes(dc, baseWidth, baseHeight, baseDensity);

        // Verify that forcing the size is idempotent.
        dc.setForcedSize(forcedWidth, forcedHeight);
        verifySizes(dc, baseWidth, baseHeight, baseDensity);
    }

    @Test
    public void testSetForcedDensity() {
        final DisplayContent dc = createDisplayNoUpdateDisplayInfo();
        final int baseWidth = 1280;
        final int baseHeight = 720;
        final int baseDensity = 320;
        final float baseXDpi = 60;
        final float baseYDpi = 60;
        final int originalMinTaskSizeDp = dc.mMinSizeOfResizeableTaskDp;

        dc.mInitialDisplayWidth = baseWidth;
        dc.mInitialDisplayHeight = baseHeight;
        dc.mInitialDisplayDensity = baseDensity;
        dc.updateBaseDisplayMetrics(baseWidth, baseHeight, baseDensity, baseXDpi, baseYDpi);

        final int forcedDensity = 600;

        // Verify that forcing the density is honored and the size doesn't change.
        dc.setForcedDensity(forcedDensity, 0 /* userId */);
        verifySizes(dc, baseWidth, baseHeight, forcedDensity);

        // Verify that forcing the density is idempotent.
        dc.setForcedDensity(forcedDensity, 0 /* userId */);
        verifySizes(dc, baseWidth, baseHeight, forcedDensity);

        // Verify that minimal task size (dp) doesn't change with density of display.
        assertEquals(originalMinTaskSizeDp, dc.mMinSizeOfResizeableTaskDp);

        // Verify that forcing resolution won't affect the already forced density.
        dc.setForcedSize(1800, 1200);
        verifySizes(dc, 1800, 1200, forcedDensity);
    }

    @Test
    public void testDisplayCutout_rot0() {
        final DisplayContent dc = createNewDisplay();
        dc.mInitialDisplayWidth = 200;
        dc.mInitialDisplayHeight = 400;
        final Rect r = new Rect(80, 0, 120, 10);
        final DisplayCutout cutout = new WmDisplayCutout(
                fromBoundingRect(r.left, r.top, r.right, r.bottom, BOUNDS_POSITION_TOP), null)
                .computeSafeInsets(200, 400).getDisplayCutout();

        dc.mInitialDisplayCutout = cutout;
        dc.getDisplayRotation().setRotation(Surface.ROTATION_0);
        dc.computeScreenConfiguration(new Configuration()); // recomputes dc.mDisplayInfo.

        assertEquals(cutout, dc.getDisplayInfo().displayCutout);
    }

    @Test
    public void testDisplayCutout_rot90() {
        // Prevent mInitialDisplayCutout from being updated from real display (e.g. null
        // if the device has no cutout).
        final DisplayContent dc = createDisplayNoUpdateDisplayInfo();
        // This test assumes it's a top cutout on a portrait display, so if it happens to be a
        // landscape display let's rotate it.
        if (dc.mInitialDisplayHeight < dc.mInitialDisplayWidth) {
            int tmp = dc.mInitialDisplayHeight;
            dc.mInitialDisplayHeight = dc.mInitialDisplayWidth;
            dc.mInitialDisplayWidth = tmp;
        }
        // Rotation may use real display info to compute bound, so here also uses the
        // same width and height.
        final int displayWidth = dc.mInitialDisplayWidth;
        final int displayHeight = dc.mInitialDisplayHeight;
        final float density = dc.mInitialDisplayDensity;
        final int cutoutWidth = 40;
        final int cutoutHeight = 10;
        final int left = (displayWidth - cutoutWidth) / 2;
        final int top = 0;
        final int right = (displayWidth + cutoutWidth) / 2;
        final int bottom = cutoutHeight;

        final var zeroRect = new Rect();
        final var bounds = new Rect[]{zeroRect, new Rect(left, top, right, bottom), zeroRect,
                zeroRect};
        final var info = new DisplayCutout.CutoutPathParserInfo(displayWidth, displayHeight,
                displayWidth, displayHeight, density, "", Surface.ROTATION_0, 1f, 1f);
        dc.mInitialDisplayCutout = new WmDisplayCutout(
                DisplayCutout.constructDisplayCutout(bounds, Insets.NONE, info), null)
                .computeSafeInsets(displayWidth, displayHeight).getDisplayCutout();
        dc.getDisplayRotation().setRotation(Surface.ROTATION_90);
        dc.computeScreenConfiguration(new Configuration()); // recomputes dc.mDisplayInfo.

        // ----o----------      -------------
        // |   |     |   |      |
        // |   ------o   |      o---
        // |             |      |  |
        // |             |  ->  |  |
        // |             |      ---o
        // |             |      |
        // |             |      -------------
        final var bounds90 = new Rect[]{new Rect(top, left, bottom, right), zeroRect, zeroRect,
                zeroRect};
        final var info90 = new DisplayCutout.CutoutPathParserInfo(displayWidth, displayHeight,
                displayWidth, displayHeight, density, "", Surface.ROTATION_90, 1f, 1f);
        assertEquals(new WmDisplayCutout(
                        DisplayCutout.constructDisplayCutout(bounds90, Insets.NONE, info90), null)
                        .computeSafeInsets(displayHeight, displayWidth).getDisplayCutout(),
                dc.getDisplayInfo().displayCutout);
    }

    @Test
    public void testLayoutSeq_assignedDuringLayout() {
        final DisplayContent dc = createNewDisplay();
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).setDisplay(dc).build();

        performLayout(dc);

        assertEquals(dc.mLayoutSeq, appWin.mLayoutSeq);
    }

    @Test
    public void testOrientationDefinedByKeyguard() {
        mDisplayContent.getDisplayPolicy().setAwake(true);
        mDisplayContent.setIgnoreOrientationRequest(false);

        // Create a window that requests landscape orientation. It will define device orientation
        // by default.
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).build();
        appWin.mActivityRecord.setOrientation(SCREEN_ORIENTATION_LANDSCAPE);

        final var keyguardWin = newWindowBuilder("keyguardWin", TYPE_NOTIFICATION_SHADE).build();
        keyguardWin.setHasSurface(true);
        keyguardWin.mAttrs.screenOrientation = SCREEN_ORIENTATION_UNSPECIFIED;

        assertEquals("Screen orientation must be defined by the app window by default",
                SCREEN_ORIENTATION_LANDSCAPE, mDisplayContent.getOrientation());

        keyguardWin.mAttrs.screenOrientation = SCREEN_ORIENTATION_PORTRAIT;
        mAtm.mKeyguardController.setKeyguardShown(appWin.getDisplayId(), true /* keyguardShowing */,
                false /* aodShowing */);
        assertEquals("Visible keyguard must influence device orientation",
                SCREEN_ORIENTATION_PORTRAIT, mDisplayContent.getOrientation());

        mAtm.mKeyguardController.keyguardGoingAway(0 /* flags */);
        assertEquals("Keyguard that is going away must not influence device orientation",
                SCREEN_ORIENTATION_LANDSCAPE, mDisplayContent.getOrientation());
    }

    @Test
    public void testOrientationForAspectRatio() {
        final DisplayContent dc = createNewDisplay();
        dc.setIgnoreOrientationRequest(false);

        // When display content is created its configuration is not yet initialized, which could
        // cause unnecessary configuration propagation, so initialize it here.
        final Configuration config = new Configuration();
        dc.computeScreenConfiguration(config);
        dc.onRequestedOverrideConfigurationChanged(config);

        // Create a window that requests a fixed orientation. It will define device orientation
        // by default.
        final var overlay = newWindowBuilder("window", TYPE_APPLICATION_OVERLAY).setDisplay(dc)
                .build();
        overlay.setHasSurface(true);
        overlay.mAttrs.screenOrientation = SCREEN_ORIENTATION_LANDSCAPE;

        // --------------------------------
        // Test non-close-to-square display
        // --------------------------------
        dc.mBaseDisplayWidth = 1000;
        dc.mBaseDisplayHeight = (int) (dc.mBaseDisplayWidth * dc.mCloseToSquareMaxAspectRatio * 2f);
        dc.configureDisplayPolicy();

        assertEquals("Screen orientation must be defined by the window by default.",
                overlay.mAttrs.screenOrientation, dc.getOrientation());

        // ----------------------------
        // Test close-to-square display - should be handled in the same way
        // ----------------------------
        dc.mBaseDisplayHeight = dc.mBaseDisplayWidth;
        dc.configureDisplayPolicy();

        assertEquals(
                "Screen orientation must be defined by the window even on close-to-square display.",
                overlay.mAttrs.screenOrientation, dc.getOrientation());

        dc.computeScreenConfiguration(config, ROTATION_0);
        dc.onRequestedOverrideConfigurationChanged(config);
        assertEquals(Configuration.ORIENTATION_PORTRAIT, config.orientation);
        overlay.setOverrideOrientation(SCREEN_ORIENTATION_NOSENSOR);
        assertEquals(Configuration.ORIENTATION_PORTRAIT,
                overlay.getRequestedConfigurationOrientation());
        // Note that getNaturalOrientation is based on logical display size. So it is portrait if
        // the display width equals to height.
        assertEquals(Configuration.ORIENTATION_PORTRAIT, dc.getNaturalOrientation());
    }

    @Test
    public void testGetPreferredOptionsPanelGravityFromDifferentDisplays() {
        final DisplayContent portraitDisplay = createNewDisplay();
        portraitDisplay.mInitialDisplayHeight = 2000;
        portraitDisplay.mInitialDisplayWidth = 1000;

        portraitDisplay.getDisplayRotation().setRotation(Surface.ROTATION_0);
        assertFalse(isOptionsPanelAtRight(portraitDisplay.getDisplayId()));
        portraitDisplay.getDisplayRotation().setRotation(ROTATION_90);
        assertTrue(isOptionsPanelAtRight(portraitDisplay.getDisplayId()));

        final DisplayContent landscapeDisplay = createNewDisplay();
        landscapeDisplay.mInitialDisplayHeight = 1000;
        landscapeDisplay.mInitialDisplayWidth = 2000;

        landscapeDisplay.getDisplayRotation().setRotation(Surface.ROTATION_0);
        assertTrue(isOptionsPanelAtRight(landscapeDisplay.getDisplayId()));
        landscapeDisplay.getDisplayRotation().setRotation(ROTATION_90);
        assertFalse(isOptionsPanelAtRight(landscapeDisplay.getDisplayId()));
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testImeLayeringTargetUpdateWhenSwitchingOnDisplays() {
        final var appWin1 = newWindowBuilder("appWin1", TYPE_BASE_APPLICATION).build();

        final DisplayContent secondaryDisplay = createNewDisplay();
        final var appWin2 = newWindowBuilder("appWin2", TYPE_BASE_APPLICATION)
                .setDisplay(secondaryDisplay).build();
        appWin1.setHasSurface(true);
        appWin2.setHasSurface(true);

        // Set current IME window on default display, make sure the IME layering target is appWin1
        // and null on the secondary display.
        mDisplayContent.setInputMethodWindowLocked(mImeWindow);
        secondaryDisplay.setInputMethodWindowLocked(null /* win */);
        assertEquals("default display IME layering target",
                appWin1, mDisplayContent.getImeLayeringTarget());
        assertNull("secondary display IME layering target",
                secondaryDisplay.getImeLayeringTarget());

        // Switch IME window on new display and make sure the IME layering target also switched.
        secondaryDisplay.setInputMethodWindowLocked(mImeWindow);
        mDisplayContent.setInputMethodWindowLocked(null /* win */);
        assertNull("default display IME layering target",
                mDisplayContent.getImeLayeringTarget());
        assertEquals("secondary display IME layering target", appWin2,
                secondaryDisplay.getImeLayeringTarget());
    }

    @Test
    public void testAllowsTopmostFullscreenOrientation() {
        final DisplayContent dc = createNewDisplay();
        dc.setIgnoreOrientationRequest(false);
        assertEquals(SCREEN_ORIENTATION_UNSPECIFIED, dc.getOrientation());
        dc.getDisplayRotation().setFixedToUserRotation(
                IWindowManager.FIXED_TO_USER_ROTATION_DISABLED);

        final Task rootTask = new TaskBuilder(mSupervisor)
                .setDisplay(dc)
                .setCreateActivity(true)
                .build();

        final Task freeformRootTask = new TaskBuilder(mSupervisor)
                .setDisplay(dc)
                .setCreateActivity(true)
                .setWindowingMode(WINDOWING_MODE_FREEFORM)
                .build();
        freeformRootTask.getTopChild().setBounds(100, 100, 300, 400);
        freeformRootTask.getTopNonFinishingActivity().setOrientation(SCREEN_ORIENTATION_LANDSCAPE);
        rootTask.getTopNonFinishingActivity().setOrientation(SCREEN_ORIENTATION_PORTRAIT);
        assertEquals(SCREEN_ORIENTATION_PORTRAIT, dc.getOrientation());

        rootTask.getTopNonFinishingActivity().setOrientation(SCREEN_ORIENTATION_LANDSCAPE);
        freeformRootTask.getTopNonFinishingActivity().setOrientation(SCREEN_ORIENTATION_PORTRAIT);
        assertEquals(SCREEN_ORIENTATION_LANDSCAPE, dc.getOrientation());
    }

    private void updateAllDisplayContentAndRotation(@NonNull DisplayContent dc) {
        // NB updateOrientation will not revert the user orientation until a settings change
        // takes effect.
        dc.updateOrientation();
        dc.onDisplayChanged(dc);
        dc.mWmService.updateRotation(true /* alwaysSendConfiguration */, false /* forceRelayout */);
        waitUntilHandlersIdle();
    }

    @Test
    public void testNoSensorRevert() {
        spyOn(mDisplayContent);
        doReturn(true).when(mDisplayContent).getIgnoreOrientationRequest();
        final DisplayRotation dr = mDisplayContent.getDisplayRotation();
        spyOn(dr);
        doReturn(false).when(dr).useDefaultSettingsProvider();
        final ActivityRecord app = new ActivityBuilder(mAtm).setCreateTask(true)
                .setComponent(getUniqueComponentName(mContext.getPackageName())).build();
        app.setOrientation(SCREEN_ORIENTATION_LANDSCAPE, app);

        assertFalse(mDisplayContent.getRotationReversionController().isAnyOverrideActive());
        mDisplayContent.getDisplayRotation().setUserRotation(
                WindowManagerPolicy.USER_ROTATION_LOCKED, ROTATION_90,
                /* caller= */ "DisplayContentTests");
        updateAllDisplayContentAndRotation(mDisplayContent);
        assertEquals(ROTATION_90, mDisplayContent.getDisplayRotation()
                .rotationForOrientation(SCREEN_ORIENTATION_UNSPECIFIED, ROTATION_90));

        app.setOrientation(SCREEN_ORIENTATION_NOSENSOR);
        updateAllDisplayContentAndRotation(mDisplayContent);
        assertTrue(mDisplayContent.getRotationReversionController().isAnyOverrideActive());
        assertEquals(ROTATION_0, mDisplayContent.getRotation());

        app.setOrientation(SCREEN_ORIENTATION_UNSPECIFIED);
        updateAllDisplayContentAndRotation(mDisplayContent);
        assertFalse(mDisplayContent.getRotationReversionController().isAnyOverrideActive());
        assertEquals(WindowManagerPolicy.USER_ROTATION_LOCKED,
                mDisplayContent.getDisplayRotation().getUserRotationMode());
        assertEquals(ROTATION_90, mDisplayContent.getDisplayRotation().getUserRotation());
        assertEquals(ROTATION_90, mDisplayContent.getDisplayRotation()
                .rotationForOrientation(SCREEN_ORIENTATION_UNSPECIFIED, ROTATION_0));
        mDisplayContent.getDisplayRotation().setUserRotation(WindowManagerPolicy.USER_ROTATION_FREE,
                ROTATION_0, /* caller= */ "DisplayContentTests");
    }

    @Test
    public void testOnDescendantOrientationRequestChanged() {
        final DisplayContent dc = createNewDisplay();
        dc.setIgnoreOrientationRequest(false);
        dc.getDisplayRotation().setFixedToUserRotation(
                IWindowManager.FIXED_TO_USER_ROTATION_DISABLED);
        dc.getDefaultTaskDisplayArea().setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        final int newOrientation = getRotatedOrientation(dc);

        final Task task = new TaskBuilder(mSupervisor)
                .setDisplay(dc).setCreateActivity(true).build();
        final ActivityRecord activity = task.getTopMostTask().getTopNonFinishingActivity();
        dc.setFocusedApp(activity);

        activity.setRequestedOrientation(newOrientation);

        assertEquals("The display should be rotated.", 1, dc.getRotation() % 2);

        dc.setIgnoreOrientationRequest(true);
        activity.setRequestedOrientation(SCREEN_ORIENTATION_SENSOR);

        assertEquals("Sensor orientation must be respected with ignore-orientation-request",
                SCREEN_ORIENTATION_SENSOR, dc.getLastOrientation());
    }

    @Test
    public void testOnDescendantOrientationRequestChanged_FrozenToUserRotation() {
        final DisplayContent dc = createNewDisplay();
        dc.setIgnoreOrientationRequest(false);
        dc.getDisplayRotation().setFixedToUserRotation(
                IWindowManager.FIXED_TO_USER_ROTATION_ENABLED);
        dc.getDisplayRotation().setUserRotation(
                WindowManagerPolicy.USER_ROTATION_LOCKED, ROTATION_180,
                /* caller= */ "DisplayContentTests");
        final int newOrientation = getRotatedOrientation(dc);

        final Task task = new TaskBuilder(mSupervisor)
                .setDisplay(dc).setCreateActivity(true).build();
        final ActivityRecord activity = task.getTopMostTask().getTopNonFinishingActivity();
        dc.setFocusedApp(activity);

        activity.setRequestedOrientation(newOrientation);

        verify(dc, never()).updateDisplayOverrideConfigurationLocked(any(), eq(activity),
                anyBoolean());
        assertEquals(ROTATION_180, dc.getRotation());
    }

    @Test
    public void testOrientationBehind() {
        assertNull(mDisplayContent.getLastOrientationSource());
        mDisplayContent.setIgnoreOrientationRequest(false);
        final ActivityRecord prev = new ActivityBuilder(mAtm).setCreateTask(true)
                .setScreenOrientation(getRotatedOrientation(mDisplayContent)).build();
        prev.setVisibleRequested(false);
        final ActivityRecord top = new ActivityBuilder(mAtm).setCreateTask(true)
                .setVisible(false)
                .setScreenOrientation(SCREEN_ORIENTATION_BEHIND).build();
        assertNotEquals(WindowConfiguration.ROTATION_UNDEFINED,
                mDisplayContent.rotationForActivityInDifferentOrientation(top));

        requestTransition(top, WindowManager.TRANSIT_OPEN);
        top.setVisibility(true);
        mDisplayContent.updateOrientation();
        // The top uses "behind", so the orientation is decided by the previous.
        assertEquals(prev, mDisplayContent.getLastOrientationSource());
        // The top will use the rotation from "prev" with fixed rotation.
        assertTrue(top.hasFixedRotationTransform());

        mDisplayContent.continueUpdateOrientationForDiffOrienLaunchingApp();
        assertFalse(top.hasFixedRotationTransform());

        // Assume that the requested orientation of "prev" is landscape. And the display is also
        // rotated to landscape. The activities from bottom to top are TaskB{"prev, "behindTop"},
        // TaskB{"top"}. Then "behindTop" should also get landscape according to ORIENTATION_BEHIND
        // instead of resolving as undefined which causes to unexpected fixed portrait rotation.
        final ActivityRecord behindTop = new ActivityBuilder(mAtm).setTask(prev.getTask())
                .setOnTop(false).setScreenOrientation(SCREEN_ORIENTATION_BEHIND).build();
        mDisplayContent.applyFixedRotationForNonTopVisibleActivityIfNeeded(behindTop);
        assertFalse(behindTop.hasFixedRotationTransform());
    }

    @Test
    public void testFixedToUserRotationChanged() {
        final DisplayContent dc = createNewDisplay();
        dc.setIgnoreOrientationRequest(false);
        dc.getDisplayRotation().setFixedToUserRotation(
                IWindowManager.FIXED_TO_USER_ROTATION_ENABLED);
        dc.getDisplayRotation().setUserRotation(
                WindowManagerPolicy.USER_ROTATION_LOCKED, ROTATION_0,
                /* caller= */ "DisplayContentTests");
        dc.getDefaultTaskDisplayArea().setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        final int newOrientation = getRotatedOrientation(dc);

        final Task task = new TaskBuilder(mSupervisor)
                .setDisplay(dc).setCreateActivity(true).build();
        final ActivityRecord activity = task.getTopMostTask().getTopNonFinishingActivity();
        dc.setFocusedApp(activity);

        activity.setRequestedOrientation(newOrientation);

        dc.getDisplayRotation().setFixedToUserRotation(
                IWindowManager.FIXED_TO_USER_ROTATION_DISABLED);

        assertEquals("The display should be rotated.", 1, dc.getRotation() % 2);
    }

    @Test
    public void testComputeImeParent_app() {
        final DisplayContent dc = createNewDisplay();
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).setDisplay(dc).build();
        dc.setImeInputTarget(appWin);
        dc.setImeLayeringTarget(appWin);
        assertEquals(dc.getImeLayeringTarget().mActivityRecord.getSurfaceControl(),
                dc.computeImeParent().getSurfaceControl());
    }

    @Test
    public void testComputeImeParent_app_notFullscreen() {
        final DisplayContent dc = createNewDisplay();
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).setDisplay(dc)
                .setWindowingMode(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW).build();
        dc.setImeInputTarget(appWin);
        dc.setImeLayeringTarget(appWin);
        assertEquals(dc.getImeContainer().getParentSurfaceControl(),
                dc.computeImeParent().getSurfaceControl());
    }

    @SetupWindows(addWindows = W_ACTIVITY)
    @Test
    public void testComputeImeParent_app_notMatchParentBounds() {
        spyOn(mAppWindow.mActivityRecord);
        doReturn(false).when(mAppWindow.mActivityRecord).matchParentBounds();
        mDisplayContent.setImeLayeringTarget(mAppWindow);
        // The surface parent of IME should be the display instead of app window.
        assertEquals(mDisplayContent.getImeContainer().getParentSurfaceControl(),
                mDisplayContent.computeImeParent().getSurfaceControl());
    }

    @Test
    public void testComputeImeParent_noApp() {
        final DisplayContent dc = createNewDisplay();
        final var statusBar = newWindowBuilder("statusBar", TYPE_STATUS_BAR).setDisplay(dc).build();
        dc.setImeInputTarget(statusBar);
        dc.setImeLayeringTarget(statusBar);
        assertEquals(dc.getImeContainer().getParentSurfaceControl(),
                dc.computeImeParent().getSurfaceControl());
    }

    @SetupWindows(addWindows = W_ACTIVITY)
    @Test
    public void testComputeImeParent_inputTargetNotUpdate() {
        final var appWin1 = newWindowBuilder("appWin1", TYPE_BASE_APPLICATION).build();
        final var appWin2 = newWindowBuilder("appWin2", TYPE_BASE_APPLICATION).build();
        doReturn(true).when(mDisplayContent).shouldImeAttachedToApp();
        mDisplayContent.setImeInputTarget(appWin1);
        mDisplayContent.setImeLayeringTarget(appWin1);
        assertEquals(appWin1.mActivityRecord.getSurfaceControl(),
                mDisplayContent.computeImeParent().getSurfaceControl());
        mDisplayContent.setImeLayeringTarget(appWin2);
        // Expect null means no change IME parent when the IME layering target not yet
        // request IME to be the input target.
        assertNull(mDisplayContent.computeImeParent());
    }

    @SetupWindows(addWindows = W_ACTIVITY)
    @Test
    public void testComputeImeParent_updateParentWhenTargetNotUseIme() {
        final var overlay = newWindowBuilder("overlay", TYPE_APPLICATION_OVERLAY).build();
        overlay.setBounds(100, 100, 200, 200);
        overlay.mAttrs.flags = FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM;
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).build();
        mDisplayContent.setImeInputTarget(appWin);
        mDisplayContent.setImeLayeringTarget(overlay);
        assertFalse(mDisplayContent.shouldImeAttachedToApp());
        assertEquals(mDisplayContent.getImeContainer().getParentSurfaceControl(),
                mDisplayContent.computeImeParent().getSurfaceControl());
    }

    @Test
    public void testComputeImeParent_remoteControlTarget() {
        final var appWin1 = newWindowBuilder("appWin1", TYPE_BASE_APPLICATION)
                .setWindowingMode(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW).build();
        final var appWin2 = newWindowBuilder("appWin2", TYPE_BASE_APPLICATION)
                .setWindowingMode(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW).build();

        mDisplayContent.setRemoteInsetsController(createDisplayWindowInsetsController());
        mDisplayContent.setImeInputTarget(appWin2);
        mDisplayContent.setImeLayeringTarget(appWin1);

        // Expect ImeParent is null since ImeLayeringTarget and ImeInputTarget are different.
        assertNull(mDisplayContent.computeImeParent());

        // ImeLayeringTarget and ImeInputTarget are updated to the same.
        mDisplayContent.setImeInputTarget(appWin1);
        assertEquals(mDisplayContent.getImeLayeringTarget(), mDisplayContent.getImeInputTarget());

        // The ImeParent should be the display.
        assertEquals(mDisplayContent.getImeContainer().getParent().getSurfaceControl(),
                mDisplayContent.computeImeParent().getSurfaceControl());
    }

    @Test
    public void testImeInputTarget_isClearedWhenWindowStateIsRemoved() {
        final DisplayContent dc = createNewDisplay();
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).setDisplay(dc).build();

        dc.setImeInputTarget(appWin);
        assertEquals(appWin, dc.computeImeControlTarget());

        appWin.removeImmediately();

        assertNull(dc.getImeInputTarget());
        assertNull(dc.computeImeControlTarget());
    }

    @Test
    public void testComputeImeControlTarget() {
        final DisplayContent dc = createNewDisplay();
        dc.setRemoteInsetsController(createDisplayWindowInsetsController());
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).setDisplay(dc).build();
        assertFalse(appWin.inMultiWindowMode());

        // Expect returning null IME control target when the focus window has not yet been the
        // IME input target (e.g. IME is restarting) in fullscreen windowing mode.
        dc.setImeInputTarget(null);
        assertNull(dc.computeImeControlTarget());

        dc.setImeInputTarget(appWin);
        dc.setImeLayeringTarget(appWin);
        assertEquals(dc.getImeInputTarget(), dc.computeImeControlTarget());
    }

    @Test
    public void testComputeImeControlTarget_splitScreen() {
        final DisplayContent dc = createNewDisplay();
        dc.setRemoteInsetsController(createDisplayWindowInsetsController());
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).setDisplay(dc)
                .setWindowingMode(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW).build();
        dc.setImeInputTarget(appWin);
        dc.setImeLayeringTarget(appWin);
        assertNotEquals(dc.getImeInputTarget(), dc.computeImeControlTarget());
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testImeSecureFlagGetUpdatedAfterImeInputTarget() {
        // Verify IME window can get up-to-date secure flag update when the IME input target
        // set before setCanScreenshot called.
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).build();
        SurfaceControl.Transaction t = mDisplayContent.mInputMethodWindow.getPendingTransaction();
        spyOn(t);
        mDisplayContent.setImeInputTarget(appWin);
        mDisplayContent.mInputMethodWindow.setCanScreenshot(t, false /* canScreenshot */);

        verify(t).setSecure(eq(mDisplayContent.mInputMethodWindow.mSurfaceControl), eq(true));
    }

    @SetupWindows(addWindows = W_ACTIVITY)
    @Test
    public void testComputeImeControlTarget_notMatchParentBounds() {
        spyOn(mAppWindow.mActivityRecord);
        doReturn(false).when(mAppWindow.mActivityRecord).matchParentBounds();
        mDisplayContent.setRemoteInsetsController(createDisplayWindowInsetsController());
        mDisplayContent.setImeInputTarget(mAppWindow);
        mDisplayContent.setImeLayeringTarget(mAppWindow);
        assertEquals(mAppWindow, mDisplayContent.computeImeControlTarget());
    }

    @Test
    public void testUpdateSystemGestureExclusion() {
        final DisplayContent dc = createNewDisplay();
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).setDisplay(dc)
                .build();
        appWin.mAttrs.flags |= FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR;
        appWin.setSystemGestureExclusion(Collections.singletonList(new Rect(10, 20, 30, 40)));

        performLayout(dc);

        appWin.setHasSurface(true);
        dc.updateSystemGestureExclusion();

        final boolean[] invoked = { false };
        final var verifier = new ISystemGestureExclusionListener.Stub() {
            @Override
            public void onSystemGestureExclusionChanged(int displayId, Region actual,
                    Region unrestricted) {
                Region expected = Region.obtain();
                expected.set(10, 20, 30, 40);
                assertEquals(expected, actual);
                invoked[0] = true;
            }
        };
        try {
            dc.registerSystemGestureExclusionListener(verifier);
        } finally {
            dc.unregisterSystemGestureExclusionListener(verifier);
        }
        assertTrue("SystemGestureExclusionListener was not invoked", invoked[0]);
    }

    @Test
    public void testCalculateSystemGestureExclusion() {
        final DisplayContent dc = createNewDisplay();
        final var appWin1 = newWindowBuilder("appWin1", TYPE_BASE_APPLICATION).setDisplay(dc)
                .build();
        appWin1.mAttrs.flags |= FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR;
        appWin1.setSystemGestureExclusion(Collections.singletonList(new Rect(10, 20, 30, 40)));

        final var appWin2 = newWindowBuilder("appWin2", TYPE_BASE_APPLICATION).setDisplay(dc)
                .build();
        appWin2.mAttrs.flags |= FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR;
        appWin2.setSystemGestureExclusion(Collections.singletonList(new Rect(20, 30, 40, 50)));

        performLayout(dc);

        appWin1.setHasSurface(true);
        appWin2.setHasSurface(true);

        final Region expected = Region.obtain();
        expected.set(20, 30, 40, 50);
        assertEquals(expected, calculateSystemGestureExclusion(dc));
    }

    @NonNull
    private static Region calculateSystemGestureExclusion(@NonNull DisplayContent dc) {
        final var out = Region.obtain();
        final var unrestricted = Region.obtain();
        dc.calculateSystemGestureExclusion(out, unrestricted);
        return out;
    }

    @Test
    public void testCalculateSystemGestureExclusion_modal() {
        final DisplayContent dc = createNewDisplay();
        final var baseWin = newWindowBuilder("baseWin", TYPE_BASE_APPLICATION).setDisplay(dc)
                .build();
        baseWin.mAttrs.flags |= FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR;
        baseWin.setSystemGestureExclusion(Collections.singletonList(new Rect(0, 0, 1000, 1000)));

        final var modalWin = newWindowBuilder("modalWin", TYPE_APPLICATION).setDisplay(dc).build();
        modalWin.mAttrs.flags |= FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR;
        modalWin.mAttrs.privateFlags |= PRIVATE_FLAG_NO_MOVE_ANIMATION;
        modalWin.mAttrs.width = 10;
        modalWin.mAttrs.height = 10;
        modalWin.setSystemGestureExclusion(Collections.emptyList());

        performLayout(dc);

        baseWin.setHasSurface(true);
        modalWin.setHasSurface(true);

        final Region expected = Region.obtain();
        assertEquals(expected, calculateSystemGestureExclusion(dc));
    }

    @Test
    public void testCalculateSystemGestureExclusion_immersiveStickyLegacyWindow() {
        mWm.mConstants.mSystemGestureExcludedByPreQStickyImmersive = true;

        final DisplayContent dc = createNewDisplay();
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).setDisplay(dc)
                .build();
        appWin.mAttrs.flags |= FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR;
        appWin.mAttrs.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        appWin.mAttrs.privateFlags |= PRIVATE_FLAG_NO_MOVE_ANIMATION;
        appWin.mAttrs.insetsFlags.behavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;
        appWin.setRequestedVisibleTypes(0, navigationBars() | statusBars());
        appWin.mActivityRecord.mTargetSdk = P;

        performLayout(dc);

        appWin.setHasSurface(true);

        final Region expected = Region.obtain();
        expected.set(dc.getBounds());
        assertEquals(expected, calculateSystemGestureExclusion(dc));

        appWin.setHasSurface(false);
    }

    @Test
    public void testCalculateSystemGestureExclusion_unrestricted() {
        mWm.mConstants.mSystemGestureExcludedByPreQStickyImmersive = true;

        final DisplayContent dc = createNewDisplay();
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).setDisplay(dc)
                .build();
        appWin.mAttrs.flags |= FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR;
        appWin.mAttrs.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        appWin.mAttrs.privateFlags |= PRIVATE_FLAG_UNRESTRICTED_GESTURE_EXCLUSION;
        appWin.setSystemGestureExclusion(Collections.singletonList(dc.getBounds()));

        performLayout(dc);

        appWin.setHasSurface(true);

        final Region expected = Region.obtain();
        expected.set(dc.getBounds());
        assertEquals(expected, calculateSystemGestureExclusion(dc));

        appWin.setHasSurface(false);
    }

    @SetupWindows(addWindows = { W_ABOVE_ACTIVITY, W_ACTIVITY })
    @Test
    public void testRequestResizeForEmptyFrames() {
        final WindowState win = mChildAppWindowAbove;
        makeWindowVisible(win, win.getParentWindow());
        win.setRequestedSize(mDisplayContent.mBaseDisplayWidth, 0 /* height */);
        win.mAttrs.width = win.mAttrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        win.mAttrs.gravity = Gravity.CENTER;
        performLayout(mDisplayContent);

        // The frame is empty because the requested height is zero.
        assertTrue(win.getFrame().isEmpty());
        // The window should be scheduled to resize then the client may report a new non-empty size.
        win.updateResizingWindowIfNeeded();
        assertThat(mWm.mResizingWindows).contains(win);
    }

    @Test
    public void testOrientationChangeLogging() {
        MetricsLogger mockLogger = mock(MetricsLogger.class);
        Configuration oldConfig = new Configuration();
        oldConfig.orientation = Configuration.ORIENTATION_LANDSCAPE;

        Configuration newConfig = new Configuration();
        newConfig.orientation = Configuration.ORIENTATION_PORTRAIT;
        final DisplayContent dc = createNewDisplay();
        Mockito.doReturn(mockLogger).when(dc).getMetricsLogger();
        Mockito.doReturn(oldConfig).doReturn(newConfig).when(dc).getConfiguration();

        dc.onConfigurationChanged(newConfig);

        ArgumentCaptor<LogMaker> logMakerCaptor = ArgumentCaptor.forClass(LogMaker.class);
        verify(mockLogger).write(logMakerCaptor.capture());
        assertEquals(MetricsProto.MetricsEvent.ACTION_PHONE_ORIENTATION_CHANGED,
                logMakerCaptor.getValue().getCategory());
        assertEquals(Configuration.ORIENTATION_PORTRAIT, logMakerCaptor.getValue().getSubtype());
    }

    @SetupWindows(addWindows = { W_ACTIVITY, W_WALLPAPER, W_STATUS_BAR, W_NAVIGATION_BAR,
            W_INPUT_METHOD, W_NOTIFICATION_SHADE })
    @Test
    public void testApplyTopFixedRotationTransform() {
        mDisplayContent.setIgnoreOrientationRequest(false);
        final DisplayPolicy displayPolicy = mDisplayContent.getDisplayPolicy();
        spyOn(displayPolicy);
        // Only non-movable (gesture) navigation bar will be animated by fixed rotation animation.
        doReturn(false).when(displayPolicy).navigationBarCanMove();
        displayPolicy.addWindowLw(mStatusBarWindow, mStatusBarWindow.mAttrs);
        displayPolicy.addWindowLw(mNavBarWindow, mNavBarWindow.mAttrs);
        displayPolicy.addWindowLw(mNotificationShadeWindow, mNotificationShadeWindow.mAttrs);
        makeWindowVisible(mStatusBarWindow, mNavBarWindow);
        final Configuration config90 = new Configuration();
        mDisplayContent.computeScreenConfiguration(config90, ROTATION_90);

        final Configuration config = new Configuration();
        mDisplayContent.getDisplayRotation().setRotation(ROTATION_0);
        mDisplayContent.computeScreenConfiguration(config);
        mDisplayContent.onRequestedOverrideConfigurationChanged(config);
        assertNotEquals(config90.windowConfiguration.getMaxBounds(),
                config.windowConfiguration.getMaxBounds());

        final ActivityRecord app = mAppWindow.mActivityRecord;
        app.setVisible(false);
        app.setVisibleRequested(false);
        requestTransition(app, WindowManager.TRANSIT_OPEN);
        app.setVisibility(true);
        final int newOrientation = getRotatedOrientation(mDisplayContent);
        app.setRequestedOrientation(newOrientation);

        assertTrue(app.isFixedRotationTransforming());
        assertTrue(mAppWindow.matchesDisplayAreaBounds());
        assertFalse(mAppWindow.areAppWindowBoundsLetterboxed());
        assertTrue(mDisplayContent.getDisplayRotation().shouldRotateSeamlessly(
                ROTATION_0 /* oldRotation */, ROTATION_90 /* newRotation */,
                false /* forceUpdate */));

        final AsyncRotationController asyncRotationController =
                mDisplayContent.getAsyncRotationController();
        assertNotNull(asyncRotationController);
        assertTrue(mStatusBarWindow.isAnimating(PARENTS, ANIMATION_TYPE_TOKEN_TRANSFORM));
        assertTrue(mNavBarWindow.isAnimating(PARENTS, ANIMATION_TYPE_TOKEN_TRANSFORM));
        // Notification shade may have its own view animation in real case so do not fade out it.
        assertFalse(mNotificationShadeWindow.isAnimating(PARENTS, ANIMATION_TYPE_TOKEN_TRANSFORM));

        // If the visibility of insets state is changed, the rotated state should be updated too.
        final int statusBarId = mStatusBarWindow.getControllableInsetProvider().getSource().getId();
        final InsetsState rotatedState = app.getFixedRotationTransformInsetsState();
        final InsetsState state = mDisplayContent.getInsetsStateController().getRawInsetsState();
        assertEquals(state.isSourceOrDefaultVisible(statusBarId, statusBars()),
                rotatedState.isSourceOrDefaultVisible(statusBarId, statusBars()));
        state.setSourceVisible(statusBarId,
                !rotatedState.isSourceOrDefaultVisible(statusBarId, statusBars()));
        mDisplayContent.getInsetsStateController().notifyInsetsChanged();
        assertEquals(state.isSourceOrDefaultVisible(statusBarId, statusBars()),
                rotatedState.isSourceOrDefaultVisible(statusBarId, statusBars()));

        // The display should keep current orientation and the rotated configuration should apply
        // to the activity.
        assertEquals(config.orientation, mDisplayContent.getConfiguration().orientation);
        assertEquals(config90.orientation, app.getConfiguration().orientation);
        assertEquals(config90.windowConfiguration.getBounds(), app.getBounds());

        // Associate wallpaper with the fixed rotation transform.
        final WindowToken wallpaperToken = mWallpaperWindow.mToken;
        wallpaperToken.linkFixedRotationTransform(app);

        // Force the negative offset to verify it can be updated.
        mWallpaperWindow.mXOffset = mWallpaperWindow.mYOffset = -1;
        assertTrue(mDisplayContent.mWallpaperController.updateWallpaperOffset(mWallpaperWindow));
        assertThat(mWallpaperWindow.mXOffset).isNotEqualTo(-1);
        assertThat(mWallpaperWindow.mYOffset).isNotEqualTo(-1);

        // The wallpaper need to animate with transformed position, so its surface position should
        // not be reset.
        final Transaction t = wallpaperToken.getPendingTransaction();
        spyOn(t);
        mWallpaperWindow.mToken.onAnimationLeashCreated(t, null /* leash */);
        verify(t, never()).setPosition(any(SurfaceControl.class), eq(0), eq(0));

        // Launch another activity before the transition is finished.
        final Task task2 = new TaskBuilder(mSupervisor).setDisplay(mDisplayContent).build();
        final ActivityRecord app2 = new ActivityBuilder(mAtm).setTask(task2)
                .setUseProcess(app.app).setVisible(false).build();
        app2.setVisibility(true);
        app2.setRequestedOrientation(newOrientation);

        // The activity should share the same transform state as the existing one. The activity
        // should also be the fixed rotation launching app because it is the latest top.
        assertTrue(app.hasFixedRotationTransform(app2));
        assertTrue(mDisplayContent.isFixedRotationLaunchingApp(app2));

        final Configuration expectedProcConfig = new Configuration(app2.app.getConfiguration());
        expectedProcConfig.windowConfiguration.setActivityType(
                WindowConfiguration.ACTIVITY_TYPE_UNDEFINED);
        assertEquals("The process should receive rotated configuration for compatibility",
                expectedProcConfig, app2.app.getConfiguration());

        // If the rotated activity requests to show IME, the IME window should use the
        // transformation from activity to lay out in the same orientation.
        LocalServices.getService(WindowManagerInternal.class).onToggleImeRequested(true /* show */,
                app.token, app.token, mDisplayContent.mDisplayId);
        assertTrue(asyncRotationController.isTargetToken(mImeWindow.mToken));
        assertTrue(mImeWindow.mToken.hasFixedRotationTransform());
        assertTrue(mImeWindow.isAnimating(PARENTS, ANIMATION_TYPE_TOKEN_TRANSFORM));

        // The fixed rotation transform can only be finished when all animation finished.
        doReturn(false).when(app2).inTransition();
        mDisplayContent.mFixedRotationTransitionListener.onAppTransitionFinishedLocked(app2.token);
        assertTrue(app.hasFixedRotationTransform());
        assertTrue(app2.hasFixedRotationTransform());

        // The display should be rotated after the launch is finished.
        app.setVisible(true);
        doReturn(false).when(app).inTransition();
        mDisplayContent.mFixedRotationTransitionListener.onAppTransitionFinishedLocked(app.token);

        // The fixed rotation should be cleared and the new rotation is applied to display.
        assertFalse(app.hasFixedRotationTransform());
        assertFalse(app2.hasFixedRotationTransform());
        assertEquals(config90.orientation, mDisplayContent.getConfiguration().orientation);
        assertNull(mDisplayContent.getAsyncRotationController());
    }

    @Test
    public void testFinishFixedRotationNoAppTransitioningTask() {
        final ActivityRecord act1 = createActivityRecord(mDisplayContent);
        final Task task = act1.getTask();
        final ActivityRecord act2 = new ActivityBuilder(mWm.mAtmService).setTask(task).build();
        mDisplayContent.setFixedRotationLaunchingApp(act2, (mDisplayContent.getRotation() + 1) % 4);
        doReturn(true).when(act1).inTransition();
        // If the task contains a transition, this should be no-op.
        mDisplayContent.mFixedRotationTransitionListener.onAppTransitionFinishedLocked(act1.token);

        assertTrue(act2.hasFixedRotationTransform());
        assertTrue(mDisplayContent.hasTopFixedRotationLaunchingApp());

        // The display should be unlikely to be in transition, but if it happens, the fixed
        // rotation should proceed to finish because the activity/task level transition is finished.
        doReturn(true).when(mDisplayContent).inTransition();
        doReturn(false).when(act1).inTransition();
        // Although this notifies act1 instead of act2 that uses the fixed rotation, act2 should
        // still finish the transform because there is no more transition event.
        mDisplayContent.mFixedRotationTransitionListener.onAppTransitionFinishedLocked(act1.token);

        assertFalse(act2.hasFixedRotationTransform());
        assertFalse(mDisplayContent.hasTopFixedRotationLaunchingApp());
    }

    @Test
    public void testNoFixedRotationOnResumedScheduledApp() {
        final ActivityRecord app = new ActivityBuilder(mAtm).setCreateTask(true).build();
        app.setVisible(false);
        app.setState(ActivityRecord.State.RESUMED, "test");
        requestTransition(app, WindowManager.TRANSIT_OPEN);
        final int newOrientation = getRotatedOrientation(mDisplayContent);
        app.setRequestedOrientation(newOrientation);

        // The condition should reject using fixed rotation because the resumed client in real case
        // might get display info immediately. And the fixed rotation adjustments haven't arrived
        // client side so the info may be inconsistent with the requested orientation.
        verify(mDisplayContent).updateOrientationAndComputeConfig(anyBoolean());
        assertFalse(app.isFixedRotationTransforming());
        assertFalse(mDisplayContent.hasTopFixedRotationLaunchingApp());
    }

    @Test
    public void testRotationForActivityInDifferentOrientation() {
        mDisplayContent.setIgnoreOrientationRequest(false);
        final ActivityRecord app = new ActivityBuilder(mAtm).setCreateTask(true).build();
        final DisplayRotation displayRotation = mDisplayContent.getDisplayRotation();
        final int rotation = displayRotation.getRotation();
        spyOn(displayRotation);
        doReturn((rotation + 1) % 4).when(displayRotation).rotationForOrientation(
                anyInt() /* orientation */, anyInt() /* lastRotation */);

        assertTrue(app.providesOrientation());
        assertNotEquals(WindowConfiguration.ROTATION_UNDEFINED,
                mDisplayContent.rotationForActivityInDifferentOrientation(app));

        doReturn(false).when(app).providesOrientation();

        assertEquals(WindowConfiguration.ROTATION_UNDEFINED,
                mDisplayContent.rotationForActivityInDifferentOrientation(app));
    }

    @Test
    public void testRespectNonTopVisibleFixedOrientation() {
        spyOn(mWm.mAppCompatConfiguration);
        doReturn(false).when(mWm.mAppCompatConfiguration).isTranslucentLetterboxingEnabled();
        makeDisplayPortrait(mDisplayContent);
        mDisplayContent.setIgnoreOrientationRequest(false);
        final ActivityRecord nonTopVisible = new ActivityBuilder(mAtm)
                .setScreenOrientation(SCREEN_ORIENTATION_PORTRAIT)
                .setCreateTask(true).build();
        final ActivityRecord translucentTop = new ActivityBuilder(mAtm)
                .setScreenOrientation(SCREEN_ORIENTATION_LANDSCAPE)
                .setTask(nonTopVisible.getTask()).setVisible(false)
                .setActivityTheme(android.R.style.Theme_Translucent).build();
        final TestTransitionPlayer player = registerTestTransitionPlayer();
        mDisplayContent.requestTransitionAndLegacyPrepare(WindowManager.TRANSIT_OPEN, 0, null,
                ActionChain.test());
        translucentTop.setVisibility(true);
        mDisplayContent.updateOrientation();
        assertEquals("Non-top visible activity must be portrait",
                Configuration.ORIENTATION_PORTRAIT, nonTopVisible.getConfiguration().orientation);
        assertEquals("Top translucent activity must be landscape",
                Configuration.ORIENTATION_LANDSCAPE, translucentTop.getConfiguration().orientation);

        player.start();
        player.finish();
        assertEquals("Display must be landscape after the transition is finished",
                Configuration.ORIENTATION_LANDSCAPE,
                mDisplayContent.getConfiguration().orientation);
        assertEquals("Non-top visible activity must still be portrait",
                Configuration.ORIENTATION_PORTRAIT,
                nonTopVisible.getConfiguration().orientation);

        translucentTop.finishIfPossible("test", false /* oomAdj */);
        mDisplayContent.updateOrientation();
        player.start();
        player.finish();
        assertEquals("Display must be portrait after closing the translucent activity",
                Configuration.ORIENTATION_PORTRAIT,
                mDisplayContent.getConfiguration().orientation);

        mDisplayContent.setFixedRotationLaunchingAppUnchecked(nonTopVisible);
        mDisplayContent.onTransitionFinished();
        assertFalse("Complete fixed rotation if not in a transition",
                mDisplayContent.hasTopFixedRotationLaunchingApp());
    }

    @Test
    public void testNonTopVisibleFixedOrientationOnDisplayResize() {
        useFakeSettingsProvider();
        spyOn(mWm.mAppCompatConfiguration);
        doReturn(false).when(mWm.mAppCompatConfiguration).isTranslucentLetterboxingEnabled();
        setReverseDefaultRotation(mDisplayContent, false);
        makeDisplayPortrait(mDisplayContent);
        mDisplayContent.setIgnoreOrientationRequest(false);
        final ActivityRecord nonTopVisible = new ActivityBuilder(mAtm).setCreateTask(true)
                .setScreenOrientation(SCREEN_ORIENTATION_LANDSCAPE).setVisible(false).build();
        new ActivityBuilder(mAtm).setCreateTask(true)
                .setScreenOrientation(SCREEN_ORIENTATION_PORTRAIT)
                .setActivityTheme(android.R.style.Theme_Translucent).build();
        nonTopVisible.setVisibleRequested(true);
        clearInvocations(mTransaction);
        mDisplayContent.updateOrientation();
        mDisplayContent.applyFixedRotationForNonTopVisibleActivityIfNeeded();

        assertTrue(nonTopVisible.hasFixedRotationTransform());
        verify(mTransaction).setPosition(eq(nonTopVisible.mSurfaceControl),
                eq((float) mDisplayContent.mBaseDisplayWidth), eq(0f));

        clearInvocations(mTransaction);
        final int newW = mDisplayContent.mBaseDisplayWidth / 2;
        final int newH = mDisplayContent.mBaseDisplayHeight / 2;
        mDisplayContent.setForcedSize(newW, newH);

        final DisplayFrames rotatedFrames = nonTopVisible.getFixedRotationTransformDisplayFrames();
        assertNotNull(rotatedFrames);
        assertEquals(newH, rotatedFrames.mWidth);
        assertEquals(newW, rotatedFrames.mHeight);
        verify(mTransaction).setPosition(eq(nonTopVisible.mSurfaceControl),
                eq((float) newW), eq(0f));
    }

    @Test
    public void testSecondaryInternalDisplayRotationFollowsDefaultDisplay() {
        final DisplayRotationCoordinator coordinator =
                mRootWindowContainer.getDisplayRotationCoordinator();
        final DisplayContent defaultDisplayContent = mDisplayContent;
        final DisplayRotation defaultDisplayRotation = defaultDisplayContent.getDisplayRotation();

        DeviceStateController deviceStateController = mock(DeviceStateController.class);
        when(deviceStateController.shouldMatchBuiltInDisplayOrientationToReverseDefaultDisplay())
                .thenReturn(true);

        // Create secondary display
        final DisplayContent secondaryDisplayContent =
                createSecondaryDisplayContent(Display.TYPE_INTERNAL, deviceStateController);
        final DisplayRotation secondaryDisplayRotation =
                secondaryDisplayContent.getDisplayRotation();
        try {
            // TestDisplayContent bypasses this method but we need it for this test
            doCallRealMethod().when(secondaryDisplayRotation).updateRotationUnchecked(anyBoolean());

            // TestDisplayContent creates this as a mock. Lets set it up to test our use case.
            when(secondaryDisplayContent.mDeviceStateController
                    .shouldMatchBuiltInDisplayOrientationToReverseDefaultDisplay()).thenReturn(
                    true);

            // Check that secondary display registered callback
            assertEquals(secondaryDisplayRotation.mDefaultDisplayRotationChangedCallback,
                    coordinator.mDefaultDisplayRotationChangedCallbacks.get(
                            secondaryDisplayContent.getDisplayId()));

            // Set the default display to a known orientation. This may be a zero or non-zero
            // rotation since mDisplayInfo.logicalWidth/Height depends on the DUT's default display
            defaultDisplayRotation.updateOrientation(SCREEN_ORIENTATION_PORTRAIT, false);
            assertEquals(defaultDisplayRotation.mPortraitRotation,
                    defaultDisplayRotation.getRotation());
            assertEquals(defaultDisplayRotation.mPortraitRotation,
                    coordinator.getDefaultDisplayCurrentRotation());

            // Check that in the initial state, the secondary display is in the right rotation
            assertRotationsAreCorrectlyReversed(defaultDisplayRotation.getRotation(),
                    secondaryDisplayRotation.getRotation());

            // Update primary display rotation, check display coordinator rotation is the default
            // display's landscape rotation, and that the secondary display rotation is correct.
            defaultDisplayRotation.updateOrientation(SCREEN_ORIENTATION_LANDSCAPE, false);
            assertEquals(defaultDisplayRotation.mLandscapeRotation,
                    defaultDisplayRotation.getRotation());
            assertEquals(defaultDisplayRotation.mLandscapeRotation,
                    coordinator.getDefaultDisplayCurrentRotation());
            assertRotationsAreCorrectlyReversed(defaultDisplayRotation.getRotation(),
                    secondaryDisplayRotation.getRotation());
        } finally {
            secondaryDisplayRotation.removeDefaultDisplayRotationChangedCallback();
        }
    }

    @Test
    public void testSecondaryNonInternalDisplayDoesNotFollowDefaultDisplay() {
        final DisplayRotationCoordinator coordinator =
                mRootWindowContainer.getDisplayRotationCoordinator();

        DeviceStateController deviceStateController = mock(DeviceStateController.class);
        when(deviceStateController.shouldMatchBuiltInDisplayOrientationToReverseDefaultDisplay())
                .thenReturn(true);

        // Create secondary non-internal displays
        createSecondaryDisplayContent(Display.TYPE_EXTERNAL, deviceStateController);
        assertEquals(0, coordinator.mDefaultDisplayRotationChangedCallbacks.size());
        createSecondaryDisplayContent(Display.TYPE_VIRTUAL, deviceStateController);
        assertEquals(0, coordinator.mDefaultDisplayRotationChangedCallbacks.size());
    }

    @NonNull
    private DisplayContent createSecondaryDisplayContent(int displayType,
            @NonNull DeviceStateController deviceStateController) {
        final var secondaryDisplayInfo = new DisplayInfo();
        secondaryDisplayInfo.copyFrom(mDisplayInfo);
        secondaryDisplayInfo.type = displayType;

        return new TestDisplayContent.Builder(mAtm, secondaryDisplayInfo)
                .setDeviceStateController(deviceStateController)
                .build();
    }

    private static void assertRotationsAreCorrectlyReversed(@Surface.Rotation int rotation1,
            @Surface.Rotation int rotation2) {
        if (rotation1 == ROTATION_0) {
            assertEquals(rotation1, rotation2);
        } else if (rotation1 == ROTATION_180) {
            assertEquals(rotation1, rotation2);
        } else if (rotation1 == ROTATION_90) {
            assertEquals(ROTATION_270, rotation2);
        } else if (rotation1 == ROTATION_270) {
            assertEquals(ROTATION_90, rotation2);
        } else {
            throw new IllegalArgumentException("Unknown rotation: " + rotation1 + ", " + rotation2);
        }
    }

    @Test
    public void testRemoteRotation() {
        // Shell-transitions version is tested in testRemoteRotationWhenTransitionCombine
        assumeTrue(!Flags.fallbackTransitionPlayer());
        final DisplayRotation dr = mDisplayContent.getDisplayRotation();
        spyOn(dr);
        doReturn((dr.getRotation() + 2) % 4).when(dr).rotationForOrientation(anyInt(), anyInt());
        final boolean[] continued = new boolean[1];
        doAnswer(invocation -> {
            continued[0] = true;
            mDisplayContent.mWaitingForConfig = false;
            mAtm.addWindowLayoutReasons(ActivityTaskManagerService.LAYOUT_REASON_CONFIG_CHANGED);
            return true;
        }).when(mDisplayContent).updateDisplayOverrideConfigurationLocked();
        final boolean[] called = new boolean[1];
        mWm.mDisplayChangeController = new IDisplayChangeWindowController.Stub() {
            @Override
            public void onDisplayChange(int displayId, int fromRotation, int toRotation,
                    DisplayAreaInfo newDisplayAreaInfo, IDisplayChangeWindowCallback callback) {
                called[0] = true;

                try {
                    callback.continueDisplayChange(null);
                } catch (RemoteException e) {
                    fail();
                }
            }
        };

        mWm.updateRotation(true /* alwaysSendConfiguration */, false /* forceRelayout */);

        assertTrue(called[0]);
        waitUntilHandlersIdle();
        assertTrue(continued[0]);
    }

    @Test
    public void testRemoteRotationWhenTransitionCombine() {
        // Create 2 visible activities to verify that they can both receive the new configuration.
        final ActivityRecord activity1 = new ActivityBuilder(mAtm).setCreateTask(true).build();
        doReturn(true).when(activity1).isSyncFinished(any());

        final TestTransitionPlayer testPlayer = registerTestTransitionPlayer();
        final DisplayRotation dr = mDisplayContent.getDisplayRotation();
        spyOn(dr);
        doReturn((dr.getRotation() + 1) % 4).when(dr).rotationForOrientation(anyInt(), anyInt());
        final boolean[] called = new boolean[1];
        mWm.mDisplayChangeController = new IDisplayChangeWindowController.Stub() {
            @Override
            public void onDisplayChange(int displayId, int fromRotation, int toRotation,
                    DisplayAreaInfo newDisplayAreaInfo, IDisplayChangeWindowCallback callback) {
                try {
                    called[0] = true;
                    callback.continueDisplayChange(null);
                } catch (RemoteException e) {
                    fail();
                }
            }
        };

        final int origRot = mDisplayContent.getConfiguration().windowConfiguration.getRotation();
        mDisplayContent.setLastHasContent();

        // Create/collect a transition that will be "interrupted" by the display rotation
        requestTransition(activity1, WindowManager.TRANSIT_CHANGE);

        mWm.updateRotation(true /* alwaysSendConfiguration */, false /* forceRelayout */);
        waitUntilHandlersIdle();
        // Since it got combined (and thus we can't rely on a handleRequest), it should fall-back
        // to the display-change controller
        assertTrue(called[0]);
        assertNotEquals(origRot, mDisplayContent.getConfiguration().windowConfiguration
                .getRotation());
    }

    @Test
    public void testRemoteDisplayChange() {
        mWm.mDisplayChangeController = mock(IDisplayChangeWindowController.class);
        final Boolean[] isWaitingForRemote = new Boolean[2];
        final var callbacks = new RemoteDisplayChangeController.ContinueRemoteDisplayChangeCallback[
                isWaitingForRemote.length];
        for (int i = 0; i < isWaitingForRemote.length; i++) {
            final int index = i;
            var callback = new RemoteDisplayChangeController.ContinueRemoteDisplayChangeCallback() {
                @Override
                public void onContinueRemoteDisplayChange(WindowContainerTransaction transaction) {
                    isWaitingForRemote[index] =
                            mDisplayContent.mRemoteDisplayChangeController
                                    .isWaitingForRemoteDisplayChange();
                }
            };
            mDisplayContent.mRemoteDisplayChangeController.performRemoteDisplayChange(
                    ROTATION_0, ROTATION_0, null /* newDisplayAreaInfo */, callback);
            callbacks[i] = callback;
        }

        // The last callback is completed, all callbacks should be notified.
        mDisplayContent.mRemoteDisplayChangeController.continueDisplayChange(callbacks[1],
                null /* transaction */);
        // When notifying 0, the callback 1 still exists.
        assertTrue(isWaitingForRemote[0]);
        assertFalse(isWaitingForRemote[1]);

        // The first callback is completed, other callbacks after it should remain.
        for (int i = 0; i < isWaitingForRemote.length; i++) {
            isWaitingForRemote[i] = null;
            mDisplayContent.mRemoteDisplayChangeController.performRemoteDisplayChange(
                    ROTATION_0, ROTATION_0, null /* newDisplayAreaInfo */, callbacks[i]);
        }
        mDisplayContent.mRemoteDisplayChangeController.continueDisplayChange(callbacks[0],
                null /* transaction */);
        assertTrue(isWaitingForRemote[0]);
        assertNull(isWaitingForRemote[1]);

        // Complete the last callback. It should be able to consume pending config change.
        mDisplayContent.mWaitingForConfig = true;
        mDisplayContent.mRemoteDisplayChangeController.continueDisplayChange(callbacks[1],
                null /* transaction */);
        assertFalse(isWaitingForRemote[1]);
        assertFalse(mDisplayContent.mWaitingForConfig);
    }

    @Test
    public void testShellTransitRotation() {
        // Create 2 visible activities to verify that they can both receive the new configuration.
        final ActivityRecord activity1 = new ActivityBuilder(mAtm).setCreateTask(true).build();
        final ActivityRecord activity2 = new ActivityBuilder(mAtm).setCreateTask(true).build();
        doReturn(true).when(activity1).isSyncFinished(any());
        doReturn(true).when(activity2).isSyncFinished(any());

        final TestTransitionPlayer testPlayer = registerTestTransitionPlayer();
        final DisplayRotation dr = mDisplayContent.getDisplayRotation();
        spyOn(dr);
        doReturn((dr.getRotation() + 1) % 4).when(dr).rotationForOrientation(anyInt(), anyInt());
        mWm.mDisplayChangeController = new IDisplayChangeWindowController.Stub() {
            @Override
            public void onDisplayChange(int displayId, int fromRotation, int toRotation,
                    DisplayAreaInfo newDisplayAreaInfo, IDisplayChangeWindowCallback callback) {
                try {
                    callback.continueDisplayChange(null);
                } catch (RemoteException e) {
                    fail();
                }
            }
        };

        final int origRot = mDisplayContent.getConfiguration().windowConfiguration.getRotation();
        mDisplayContent.setLastHasContent();
        mWm.updateRotation(true /* alwaysSendConfiguration */, false /* forceRelayout */);
        // Should create a transition request without performing rotation
        assertNotNull(testPlayer.mLastRequest);
        assertEquals(origRot, mDisplayContent.getConfiguration().windowConfiguration.getRotation());

        // Once transition starts, rotation is applied and transition shows DC rotating.
        testPlayer.startTransition();
        waitUntilHandlersIdle();
        verify(activity1).ensureActivityConfiguration(anyBoolean());
        verify(activity2).ensureActivityConfiguration(anyBoolean());
        assertNotEquals(origRot, mDisplayContent.getConfiguration().windowConfiguration
                .getRotation());
        assertNotNull(testPlayer.mLastReady);
        assertTrue(testPlayer.mController.isPlaying());
        WindowContainerToken dcToken = mDisplayContent.mRemoteToken.toWindowContainerToken();
        final var change = testPlayer.mLastReady.getChange(dcToken);
        assertNotNull(change);
        assertNotEquals(change.getEndRotation(), change.getStartRotation());
        testPlayer.finish();

        // The AsyncRotationController should only exist if there is an ongoing rotation change.
        mDisplayContent.finishAsyncRotationIfPossible();
        mDisplayContent.setLastHasContent();
        doReturn(dr.getRotation() + 1).when(dr).rotationForOrientation(anyInt(), anyInt());
        dr.updateRotationUnchecked(true /* forceUpdate */);
        assertNotNull(mDisplayContent.getAsyncRotationController());
        doReturn(dr.getRotation() - 1).when(dr).rotationForOrientation(anyInt(), anyInt());
        dr.updateRotationUnchecked(true /* forceUpdate */);
        assertNull("Cancel AsyncRotationController for the intermediate rotation changes 0->1->0",
                mDisplayContent.getAsyncRotationController());
    }

    @Test
    public void testValidWindowingLayer() {
        final SurfaceControl windowingLayer = mDisplayContent.getWindowingLayer();
        assertNotNull(windowingLayer);

        final List<DisplayArea<?>> windowedMagnificationAreas =
                mDisplayContent.mDisplayAreaPolicy.getDisplayAreas(FEATURE_WINDOWED_MAGNIFICATION);
        if (windowedMagnificationAreas != null) {
            assertEquals("There should be only one DisplayArea for FEATURE_WINDOWED_MAGNIFICATION",
                    1, windowedMagnificationAreas.size());
            assertEquals(windowedMagnificationAreas.get(0).mSurfaceControl, windowingLayer);
            assertEquals(windowingLayer,
                    mDisplayContent.mDisplayAreaPolicy.getWindowingArea().mSurfaceControl);
        } else {
            assertNotEquals(mDisplayContent.mSurfaceControl, windowingLayer);
        }

        // When migrating the surface of default trusted display, the children should belong to the
        // surface of DisplayContent directly.
        clearInvocations(mTransaction);
        mDisplayContent.migrateToNewSurfaceControl(mTransaction);
        for (int i = mDisplayContent.getChildCount() - 1; i >= 0; i--) {
            final SurfaceControl childSc = mDisplayContent.getChildAt(i).mSurfaceControl;
            verify(mTransaction).reparent(eq(childSc), eq(mDisplayContent.mSurfaceControl));
            verify(mTransaction, never()).reparent(eq(childSc), eq(windowingLayer));
        }

        // If a display doesn't have WINDOWED_MAGNIFICATION (e.g. untrusted), it will have an
        // additional windowing layer to put the window content.
        clearInvocations(mTransaction);
        final DisplayInfo info = new DisplayInfo(mDisplayInfo);
        info.flags &= ~Display.FLAG_TRUSTED;
        final DisplayContent dc2 = createNewDisplay(info);
        final SurfaceControl dc2WinLayer = dc2.getWindowingLayer();
        final DisplayArea<?> dc2WinArea = dc2.mDisplayAreaPolicy.getWindowingArea();
        assertEquals(dc2WinLayer, dc2WinArea.mSurfaceControl);

        // When migrating the surface of a display with additional windowing layer, the children
        // layer of display should still belong to the display.
        clearInvocations(mTransaction);
        dc2.migrateToNewSurfaceControl(mTransaction);
        verify(mTransaction).reparent(eq(dc2WinLayer), eq(dc2.mSurfaceControl));
        for (int i = dc2.getChildCount() - 1; i >= 0; i--) {
            verify(mTransaction).reparent(eq(dc2.getChildAt(i).mSurfaceControl),
                    eq(dc2.mSurfaceControl));
        }

        // When migrating the surface of child area under windowing area, the new child surfaces
        // should reparent to the windowing layer.
        clearInvocations(mTransaction);
        for (int i = dc2WinArea.getChildCount() - 1; i >= 0; i--) {
            final WindowContainer<?> child = dc2WinArea.getChildAt(i);
            child.migrateToNewSurfaceControl(mTransaction);
            verify(mTransaction).reparent(eq(child.mSurfaceControl), eq(dc2WinLayer));
        }
    }

    @Test
    public void testFindScrollCaptureTargetWindow_behindWindow() {
        final DisplayContent dc = createNewDisplay();
        final Task rootTask = createTask(dc);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final var appWin = createAppWindow(task, TYPE_BASE_APPLICATION, "appWin");
        final var screenshot = newWindowBuilder("screenshot", TYPE_SCREENSHOT)
                .setDisplay(dc).build();

        final var result = dc.findScrollCaptureTargetWindow(screenshot,
                ActivityTaskManager.INVALID_TASK_ID);
        assertEquals(appWin, result);
    }

    @Test
    public void testFindScrollCaptureTargetWindow_cantReceiveKeys() {
        final DisplayContent dc = createNewDisplay();
        final Task rootTask = createTask(dc);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final var appWin = createAppWindow(task, TYPE_BASE_APPLICATION, "appWin");
        final var invisibleWin = newWindowBuilder("invisibleWin", TYPE_BASE_APPLICATION)
                .setDisplay(dc).build();
        invisibleWin.mViewVisibility = View.INVISIBLE;  // make canReceiveKeys return false

        final var result = dc.findScrollCaptureTargetWindow(null /* searchBehind */,
                ActivityTaskManager.INVALID_TASK_ID);
        assertEquals(appWin, result);
    }

    @Test
    public void testFindScrollCaptureTargetWindow_secure() {
        final DisplayContent dc = createNewDisplay();
        final Task rootTask = createTask(dc);
        createTaskInRootTask(rootTask, 0 /* userId */);
        final var secureWin = newWindowBuilder("secureWin", TYPE_BASE_APPLICATION).setDisplay(dc)
                .build();
        secureWin.mAttrs.flags |= FLAG_SECURE;

        final var result = dc.findScrollCaptureTargetWindow(null /* searchBehind */,
                ActivityTaskManager.INVALID_TASK_ID);
        assertNull(result);
    }

    @Test
    public void testFindScrollCaptureTargetWindow_secureTaskId() {
        final DisplayContent dc = createNewDisplay();
        final Task rootTask = createTask(dc);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final var secureWin = newWindowBuilder("secureWin", TYPE_BASE_APPLICATION).setDisplay(dc)
                .build();
        secureWin.mAttrs.flags |= FLAG_SECURE;

        final var result = dc.findScrollCaptureTargetWindow(null /* searchBehind */, task.mTaskId);
        assertNull(result);
    }

    @Test
    public void testFindScrollCaptureTargetWindow_taskId() {
        final DisplayContent dc = createNewDisplay();
        final Task rootTask = createTask(dc);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final var appWin = createAppWindow(task, TYPE_BASE_APPLICATION, "appWin");
        newWindowBuilder("screenshot", TYPE_SCREENSHOT).setDisplay(dc).build();

        final var result = dc.findScrollCaptureTargetWindow(null /* searchBehind */, task.mTaskId);
        assertEquals(appWin, result);
    }

    @Test
    public void testFindScrollCaptureTargetWindow_taskIdCantReceiveKeys() {
        final DisplayContent dc = createNewDisplay();
        final Task rootTask = createTask(dc);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final var appWin = createAppWindow(task, TYPE_BASE_APPLICATION, "appWin");
        appWin.mViewVisibility = View.INVISIBLE;  // make canReceiveKeys return false
        newWindowBuilder("screenshot", TYPE_SCREENSHOT).setDisplay(dc).build();

        final var result = dc.findScrollCaptureTargetWindow(null, task.mTaskId);
        assertEquals(appWin, result);
    }

    @Test
    public void testEnsureActivitiesVisibleNotRecursive() {
        final TaskDisplayArea mockTda = mock(TaskDisplayArea.class);
        final boolean[] called = { false };
        doAnswer(invocation -> {
            // The assertion will fail if DisplayArea#ensureActivitiesVisible is called twice.
            assertFalse(called[0]);
            called[0] = true;
            mDisplayContent.ensureActivitiesVisible(null, false);
            return null;
        }).when(mockTda).ensureActivitiesVisible(any(), anyBoolean());

        mDisplayContent.ensureActivitiesVisible(null, false);
    }

    @Test
    public void testIsPublicSecondaryDisplayWithDesktopModeForceEnabled() {
        mWm.mForceDesktopModeOnExternalDisplays = true;
        // Not applicable for default display
        assertFalse(mDefaultDisplay.isPublicSecondaryDisplayWithDesktopModeForceEnabled());

        // Not applicable for private secondary display.
        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.copyFrom(mDisplayInfo);
        displayInfo.flags = FLAG_PRIVATE;
        final DisplayContent privateDc = createNewDisplay(displayInfo);
        assertFalse(privateDc.isPublicSecondaryDisplayWithDesktopModeForceEnabled());

        // Applicable for public secondary display.
        final DisplayContent publicDc = createNewDisplay();
        assertTrue(publicDc.isPublicSecondaryDisplayWithDesktopModeForceEnabled());

        // Make sure forceDesktopMode() is false when the force config is disabled.
        mWm.mForceDesktopModeOnExternalDisplays = false;
        assertFalse(publicDc.isPublicSecondaryDisplayWithDesktopModeForceEnabled());
    }

    @Test
    public void testDisplaySettingsReappliedWhenDisplayChanged() {
        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.copyFrom(mDisplayInfo);
        final DisplayContent dc = createNewDisplay(displayInfo);

        // Generate width/height/density values different from the default of the display.
        final int forcedWidth = dc.mBaseDisplayWidth + 1;
        final int forcedHeight = dc.mBaseDisplayHeight + 1;
        final int forcedDensity = dc.mBaseDisplayDensity + 1;
        // Update the forced size and density in settings and the unique id to simulate a display
        // remap.
        dc.mWmService.mDisplayWindowSettings.setForcedSize(dc, forcedWidth, forcedHeight);
        dc.mWmService.mDisplayWindowSettings.setForcedDensity(displayInfo, forcedDensity,
                0 /* userId */);
        dc.mCurrentUniqueDisplayId = mDisplayInfo.uniqueId + "-test";
        // Trigger display changed.
        updateDisplay(dc);
        // Ensure overridden size and density match the most up-to-date values in settings for the
        // display.
        verifySizes(dc, forcedWidth, forcedHeight, forcedDensity);
    }

    @SetupWindows(addWindows = { W_ACTIVITY, W_INPUT_METHOD })
    @Test
    public void testComputeImeLayeringTarget_shouldNotCheckOutdatedImeLayeringTargetWhenRemoved() {
        final var childWin1 = newWindowBuilder("childWin1", FIRST_SUB_WINDOW).setParent(mAppWindow)
                .build();
        final var appWin2 = newWindowBuilder("appWin2", TYPE_BASE_APPLICATION).build();
        spyOn(childWin1);
        doReturn(false).when(mDisplayContent).shouldImeAttachedToApp();
        mDisplayContent.setImeLayeringTarget(childWin1);
        verify(childWin1).needsRelativeLayeringToIme();

        childWin1.removeImmediately();

        verify(mDisplayContent).computeImeLayeringTarget(true /* update */);
        assertEquals("appWin2 is the IME layering target",
                appWin2, mDisplayContent.getImeLayeringTarget());
        assertNull(mDisplayContent.getImeInputTarget());
        // Still only one call, earlier when childWin1 was set as IME layering target.
        verify(childWin1).needsRelativeLayeringToIme();
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testAttachAndShowImeScreenshotOnTarget() {
        // Preparation: Simulate screen state is on.
        spyOn(mWm.mPolicy);
        doReturn(true).when(mWm.mPolicy).isScreenOn();

        // Preparation: Simulate IME screenshot surface.
        spyOn(mWm.mTaskSnapshotController);
        ScreenCaptureInternal.ScreenshotHardwareBuffer mockHwBuffer =
                mock(ScreenCaptureInternal.ScreenshotHardwareBuffer.class);
        doReturn(mock(HardwareBuffer.class)).when(mockHwBuffer).getHardwareBuffer();
        doReturn(mockHwBuffer).when(mWm.mTaskSnapshotController)
                .screenshotImeFromAttachedTask(any(Task.class));

        // Preparation: Simulate snapshot Task.
        final ActivityRecord act1 = createActivityRecord(mDisplayContent);
        final var appWin1 = newWindowBuilder("appWin1", TYPE_BASE_APPLICATION)
                .setWindowToken(act1).build();
        spyOn(appWin1);
        spyOn(appWin1.mWinAnimator);
        appWin1.setHasSurface(true);
        assertTrue(appWin1.canBeImeLayeringTarget());
        doReturn(true).when(appWin1.mWinAnimator).getShown();
        appWin1.mWinAnimator.mLastAlpha = 1f;

        // Test step 1: appWin1 is the current IME layering target and soft-keyboard is visible.
        mDisplayContent.computeImeLayeringTarget(true /* update */);
        assertEquals(appWin1, mDisplayContent.getImeLayeringTarget());
        mDisplayContent.setImeInputTarget(appWin1);
        makeWindowVisible(mDisplayContent.mInputMethodWindow);
        mDisplayContent.getInsetsStateController().getImeSourceProvider().setImeShowing(true);

        // Test step 2: Simulate launching appWin2 and appWin1 is in app transition.
        final ActivityRecord act2 = createActivityRecord(mDisplayContent);
        final var appWin2 = newWindowBuilder("appWin2", TYPE_BASE_APPLICATION)
                .setWindowToken(act2).build();
        appWin2.setHasSurface(true);
        assertTrue(appWin2.canBeImeLayeringTarget());
        doReturn(true).when(appWin1).inTransition();

        // Test step 3: Verify appWin2 will be the next IME layering target and the IME screenshot
        // surface will be attached and shown on the display at this time.
        mDisplayContent.computeImeLayeringTarget(true /* update */);
        assertEquals(appWin2, mDisplayContent.getImeLayeringTarget());
        assertTrue(mDisplayContent.shouldImeAttachedToApp());

        verify(mDisplayContent, atLeast(1)).showImeScreenshot();
        verify(mWm.mTaskSnapshotController).screenshotImeFromAttachedTask(appWin1.getTask());
        assertNotNull(mDisplayContent.mImeScreenshot);
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testShowImeScreenshot_removeCurScreenshotBeforeCreateNext() {
        final Task rootTask = createTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final ActivityRecord activity = createActivityRecord(mDisplayContent, task);
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION)
                .setWindowToken(activity).build();

        mDisplayContent.setImeInputTarget(appWin);
        mDisplayContent.setImeLayeringTarget(appWin);
        makeWindowVisible(mDisplayContent.mInputMethodWindow);
        mDisplayContent.getInsetsStateController().getImeSourceProvider().setImeShowing(true);

        // Verify that when the timing of 2 showImeScreenshot invocations are very close, it will
        // first remove the current screenshot and then create the next one.
        mDisplayContent.showImeScreenshot();
        DisplayContent.ImeScreenshot curScreenshot = mDisplayContent.mImeScreenshot;
        spyOn(curScreenshot);
        mDisplayContent.showImeScreenshot();
        verify(curScreenshot).removeSurface(any(Transaction.class));
        assertNotNull(mDisplayContent.mImeScreenshot);
        assertNotEquals(curScreenshot, mDisplayContent.mImeScreenshot);
    }

    @UseTestDisplay(addWindows = W_INPUT_METHOD)
    @Test
    public void testRemoveImeScreenshot_whenWindowRemoveImmediately() {
        final Task rootTask = createTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final ActivityRecord activity = createActivityRecord(mDisplayContent, task);
        final var appWin = newWindowBuilder("appWin", TYPE_BASE_APPLICATION)
                .setWindowToken(activity).build();
        makeWindowVisible(mDisplayContent.mInputMethodWindow);

        mDisplayContent.setImeInputTarget(appWin);
        mDisplayContent.setImeLayeringTarget(appWin);
        mDisplayContent.getInsetsStateController().getImeSourceProvider().setImeShowing(true);
        mDisplayContent.showImeScreenshot();
        assertNotNull(mDisplayContent.mImeScreenshot);

        // Expect IME screenshot will be removed when the appWin is IME layering target and invoked
        // removeImeScreenshotByTarget.
        appWin.removeImmediately();
        assertNull(mDisplayContent.mImeScreenshot);
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testDispatchImeOverlayLayeringTargetVisibilityChanged_whenLayeringTargetNull() {
        final DisplayContent dc = createNewDisplay();
        final var wmService = dc.mWmService;
        spyOn(wmService);
        final WindowState app = newWindowBuilder("app", TYPE_BASE_APPLICATION).setDisplay(dc)
                .build();
        app.mAttrs.flags |= FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM;
        dc.setInputMethodWindowLocked(mImeWindow);

        assertEquals("app became the IME layering target", app,
                dc.getImeLayeringTarget());
        assertTrue("app is an IME overlay layering target", app.isImeOverlayLayeringTarget());
        verify(wmService).dispatchImeOverlayLayeringTargetVisibilityChanged(
                eq(app.mClient.asBinder()), eq(TYPE_BASE_APPLICATION), eq(true) /* visible */,
                eq(false) /* removed */, eq(dc.mDisplayId));

        // Removing IME window updates IME layering target to null
        dc.setInputMethodWindowLocked(null /* win */);
        assertNotEquals("app is no longer the IME layering target after IME was removed",
                app, dc.getImeLayeringTarget());
        assertFalse("app is no longer an IME overlay layering target after IME was removed",
                app.isImeOverlayLayeringTarget());
        verify(wmService).dispatchImeOverlayLayeringTargetVisibilityChanged(isNull() /* token */,
                eq(INVALID_WINDOW_TYPE), eq(false) /* visible */, eq(true) /* removed */,
                eq(dc.mDisplayId));
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testDispatchImeOverlayLayeringTargetVisibilityChanged_whenLayeringTargetChanges() {
        final DisplayContent dc = createNewDisplay();
        final var wmService = dc.mWmService;
        spyOn(wmService);
        final WindowState app1 = newWindowBuilder("app1", TYPE_BASE_APPLICATION)
                .setDisplay(dc).build();
        final WindowState app2 = newWindowBuilder("app2", TYPE_APPLICATION)
                .setDisplay(dc).build();
        app1.mAttrs.flags |= FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM;
        app2.mActivityRecord.setVisibleRequested(false);
        dc.setInputMethodWindowLocked(mImeWindow);

        assertEquals("app1 became the IME layering target", app1,
                dc.getImeLayeringTarget());
        assertTrue("app1 is an IME overlay layering target", app1.isImeOverlayLayeringTarget());
        verify(wmService).dispatchImeOverlayLayeringTargetVisibilityChanged(
                eq(app1.mClient.asBinder()), eq(TYPE_BASE_APPLICATION), eq(true) /* visible */,
                eq(false) /* removed */, eq(dc.mDisplayId));

        app2.mActivityRecord.setVisibleRequested(true);
        dc.computeImeLayeringTarget(true /* update */);
        assertEquals("app2 became the IME layering target", app2,
                dc.getImeLayeringTarget());
        assertFalse("app2 is not an IME overlay layering target",
                app2.isImeOverlayLayeringTarget());
        verify(wmService).dispatchImeOverlayLayeringTargetVisibilityChanged(isNull() /* token */,
                eq(TYPE_APPLICATION), eq(false) /* visible */, eq(true) /* removed */,
                eq(dc.mDisplayId));
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testDispatchImeOverlayLayeringTargetVisibilityChanged_whenOverlayTargetAdded() {
        final DisplayContent dc = createNewDisplay();
        final var wmService = dc.mWmService;
        spyOn(wmService);
        final WindowState app1 = newWindowBuilder("app1", TYPE_BASE_APPLICATION)
                .setDisplay(dc).build();
        dc.setInputMethodWindowLocked(mImeWindow);

        assertEquals("app1 became the IME layering target", app1,
                dc.getImeLayeringTarget());
        assertFalse("app1 is not an IME overlay layering target",
                app1.isImeOverlayLayeringTarget());
        verify(wmService, never()).dispatchImeOverlayLayeringTargetVisibilityChanged(
                any(Binder.class), anyInt(), anyBoolean(), anyBoolean(), anyInt());

        final WindowState app2 = newWindowBuilder("app2", TYPE_APPLICATION)
                .setParent(app1)
                .setDisplay(dc).build();
        app2.mAttrs.flags |= FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM;

        dc.computeImeLayeringTarget(true /* update */);
        assertEquals("app2 became the IME layering target", app2,
                dc.getImeLayeringTarget());
        assertTrue("app2 is an IME overlay layering target", app2.isImeOverlayLayeringTarget());
        verify(wmService).dispatchImeOverlayLayeringTargetVisibilityChanged(
                eq(app2.mClient.asBinder()), eq(TYPE_APPLICATION), eq(true) /* visible */,
                eq(false) /* removed */, eq(dc.mDisplayId));
    }

    @Test
    public void testRotateBounds_keepSamePhysicalPosition() {
        final DisplayContent dc =
                new TestDisplayContent.Builder(mAtm, 1000, 2000).build();
        final Rect initBounds = new Rect(0, 0, 700, 1500);
        final Rect rotateBounds = new Rect(initBounds);

        // Rotate from 0 to 0
        dc.rotateBounds(ROTATION_0, ROTATION_0, rotateBounds);

        assertEquals(new Rect(0, 0, 700, 1500), rotateBounds);

        // Rotate from 0 to 90
        rotateBounds.set(initBounds);
        dc.rotateBounds(ROTATION_0, ROTATION_90, rotateBounds);

        assertEquals(new Rect(0, 300, 1500, 1000), rotateBounds);

        // Rotate from 0 to 180
        rotateBounds.set(initBounds);
        dc.rotateBounds(ROTATION_0, ROTATION_180, rotateBounds);

        assertEquals(new Rect(300, 500, 1000, 2000), rotateBounds);

        // Rotate from 0 to 270
        rotateBounds.set(initBounds);
        dc.rotateBounds(ROTATION_0, ROTATION_270, rotateBounds);

        assertEquals(new Rect(500, 0, 2000, 700), rotateBounds);
    }

    /**
     * Creates a TestDisplayContent using the constructor that takes in display width and height as
     * parameters and validates that the newly-created TestDisplayContent's DisplayInfo and
     * WindowConfiguration match the parameters passed into the constructor. Additionally, this test
     * checks that device-specific overrides are not applied.
     */
    @Test
    public void testCreateTestDisplayContentFromDimensions() {
        final int displayWidth = 540;
        final int displayHeight = 960;
        final int density = 192;
        final int expectedWidthDp = 450; // = 540/(192/160)
        final int expectedHeightDp = 800; // = 960/(192/160)
        final int windowingMode = WINDOWING_MODE_FULLSCREEN;
        final boolean ignoreOrientationRequests = false;
        final float fixedOrientationLetterboxRatio = 0;
        final DisplayContent testDisplayContent = new TestDisplayContent.Builder(mAtm, displayWidth,
                displayHeight).setDensityDpi(density).build();

        // test display info
        final DisplayInfo di = testDisplayContent.getDisplayInfo();
        assertEquals(displayWidth, di.logicalWidth);
        assertEquals(displayHeight, di.logicalHeight);
        assertEquals(density, di.logicalDensityDpi);

        // test configuration
        final Configuration config = testDisplayContent.getConfiguration();
        assertEquals(expectedWidthDp, config.screenWidthDp);
        assertEquals(expectedHeightDp, config.screenHeightDp);
        final WindowConfiguration windowConfig = config.windowConfiguration;
        assertEquals(displayWidth, windowConfig.getBounds().width());
        assertEquals(displayHeight, windowConfig.getBounds().height());
        assertEquals(windowingMode, windowConfig.getWindowingMode());
        assertEquals(Configuration.SCREENLAYOUT_SIZE_NORMAL,
                config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK);

        // test misc display overrides
        assertEquals(ignoreOrientationRequests, testDisplayContent.mSetIgnoreOrientationRequest);
        assertEquals(fixedOrientationLetterboxRatio,
                mWm.mAppCompatConfiguration.getFixedOrientationLetterboxAspectRatio(),
                0 /* delta */);
    }

    /**
     * Creates a TestDisplayContent using the constructor that takes in a DisplayInfo as a parameter
     * and validates that the newly-created TestDisplayContent's DisplayInfo and WindowConfiguration
     * match the width, height, and density values set in the DisplayInfo passed as a parameter.
     * Additionally, this test checks that device-specific overrides are not applied.
     */
    @Test
    public void testCreateTestDisplayContentFromDisplayInfo() {
        final int displayWidth = 1000;
        final int displayHeight = 2000;
        final int windowingMode = WINDOWING_MODE_FULLSCREEN;
        final boolean ignoreOrientationRequests = false;
        final float fixedOrientationLetterboxRatio = 0;
        final DisplayInfo testDisplayInfo = new DisplayInfo();
        mContext.getDisplay().getDisplayInfo(testDisplayInfo);
        testDisplayInfo.logicalWidth = displayWidth;
        testDisplayInfo.logicalHeight = displayHeight;
        testDisplayInfo.logicalDensityDpi = TestDisplayContent.DEFAULT_LOGICAL_DISPLAY_DENSITY;
        final DisplayContent testDisplayContent = new TestDisplayContent.Builder(mAtm,
                testDisplayInfo).build();

        // test display info
        final DisplayInfo di = testDisplayContent.getDisplayInfo();
        assertEquals(displayWidth, di.logicalWidth);
        assertEquals(displayHeight, di.logicalHeight);
        assertEquals(TestDisplayContent.DEFAULT_LOGICAL_DISPLAY_DENSITY, di.logicalDensityDpi);

        // test configuration
        final WindowConfiguration windowConfig = testDisplayContent.getConfiguration()
                .windowConfiguration;
        assertEquals(displayWidth, windowConfig.getBounds().width());
        assertEquals(displayHeight, windowConfig.getBounds().height());
        assertEquals(windowingMode, windowConfig.getWindowingMode());
        assertEquals(Configuration.SCREENLAYOUT_SIZE_LARGE, testDisplayContent
                .getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK);

        // test misc display overrides
        assertEquals(ignoreOrientationRequests, testDisplayContent.mSetIgnoreOrientationRequest);
        assertEquals(fixedOrientationLetterboxRatio,
                mWm.mAppCompatConfiguration.getFixedOrientationLetterboxAspectRatio(),
                0 /* delta */);
    }

    /**
     * Verifies {@link DisplayContent#remove} should not resume home root task on the removing
     * display.
     */
    @Test
    @SuppressWarnings("GuardedBy")
    public void testNotResumeHomeRootTaskOnRemovingDisplay() {
        // Create a display which supports system decoration and allows reparenting root tasks to
        // another display when the display is removed.
        final DisplayContent display = new TestDisplayContent.Builder(
                mAtm, 1000, 1500).setSystemDecorations(true).build();
        doReturn(false).when(display).shouldDestroyContentOnRemove();

        // Put home root task on the display.
        final Task homeRootTask = new TaskBuilder(mSupervisor)
                .setDisplay(display).setActivityType(ACTIVITY_TYPE_HOME).build();

        // Put a finishing standard activity which will be reparented.
        final Task rootTask = createTaskWithActivity(display.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, ON_TOP, true /* twoLevelTask */);
        rootTask.topRunningActivity().makeFinishingLocked();

        clearInvocations(homeRootTask);
        display.remove();

        // The removed display should have no focused root task and its home root task should never
        // resume.
        assertNull(display.getFocusedRootTask());
        verify(homeRootTask, never()).resumeTopActivityUncheckedLocked(
                any(), any(), anyBoolean());
    }

    /**
     * Verifies the correct activity is returned when querying the top running activity.
     */
    @Test
    public void testTopRunningActivity() {
        final DisplayContent display = mRootWindowContainer.getDefaultDisplay();
        final KeyguardController keyguard = mSupervisor.getKeyguardController();
        final Task rootTask = new TaskBuilder(mSupervisor).setCreateActivity(true).build();
        final ActivityRecord activity = rootTask.getTopNonFinishingActivity();

        // Create empty root task on top.
        final Task emptyRootTask = new TaskBuilder(mSupervisor).build();

        // Make sure the top running activity is not affected when keyguard is not locked.
        assertTopRunningActivity(activity, display);

        // Check to make sure activity not reported when it cannot show on lock and lock is on.
        doReturn(true).when(keyguard).isKeyguardLocked(anyInt());
        assertEquals(activity, display.topRunningActivity());
        assertNull(display.topRunningActivity(true /* considerKeyguardState */));

        // Move root task with activity to top.
        rootTask.moveToFront("testRootTaskToFront");
        assertEquals(rootTask, display.getFocusedRootTask());
        assertEquals(activity, display.topRunningActivity());
        assertNull(display.topRunningActivity(true /* considerKeyguardState */));

        // Add activity that should be shown on the keyguard.
        final ActivityRecord showWhenLockedActivity = new ActivityBuilder(mAtm)
                .setTask(rootTask)
                .setActivityFlags(FLAG_SHOW_WHEN_LOCKED)
                .build();

        // Ensure the show when locked activity is returned.
        assertTopRunningActivity(showWhenLockedActivity, display);

        // Move empty root task to front. The running activity in focusable root task which below
        // the empty root task should be returned.
        emptyRootTask.moveToFront("emptyRootTaskToFront");
        assertEquals(rootTask, display.getFocusedRootTask());
        assertTopRunningActivity(showWhenLockedActivity, display);
    }

    private static void assertTopRunningActivity(ActivityRecord top,
            @NonNull DisplayContent display) {
        assertEquals(top, display.topRunningActivity());
        assertEquals(top, display.topRunningActivity(true /* considerKeyguardState */));
    }

    @Test
    public void testKeyguardGoingAwayWhileAodShown() {
        mDisplayContent.getDisplayPolicy().setAwake(true);

        final KeyguardController keyguard = mAtm.mKeyguardController;
        final ActivityRecord activity = new ActivityBuilder(mAtm).setCreateTask(true).build();
        final int displayId = mDisplayContent.getDisplayId();
        final TestTransitionPlayer transitions = registerTestTransitionPlayer();

        final BooleanSupplier keyguardShowing = () -> keyguard.isKeyguardShowing(displayId);
        final BooleanSupplier keyguardGoingAway = () -> keyguard.isKeyguardGoingAway(displayId);
        final BooleanSupplier appVisible = activity::isVisibleRequested;

        // Begin unlocked.
        keyguard.setKeyguardShown(displayId, false /* keyguard */, false /* aod */);
        transitions.flush();

        // Lock and go to AOD.
        keyguard.setKeyguardShown(displayId, true /* keyguard */, true /* aod */);
        assertTrue(keyguardShowing.getAsBoolean());
        assertFalse(keyguardGoingAway.getAsBoolean());
        assertFalse(appVisible.getAsBoolean());
        if (Flags.ensureKeyguardDoesTransitionStartingBugFix()) {
            assertThat(transitions.mLastTransit).isNull();
        } else {
            if (Flags.aodTransition()) {
                assertThat(transitions.mLastTransit).flags().contains(TRANSIT_FLAG_AOD_APPEARING);
            } else {
                assertThat(transitions.mLastTransit).flags().doesNotContain(
                        TRANSIT_FLAG_AOD_APPEARING);
            }
        }
        transitions.flush();

        // Start unlocking from AOD.
        keyguard.keyguardGoingAway(0x0 /* flags */);
        assertTrue(keyguardGoingAway.getAsBoolean());
        assertTrue(appVisible.getAsBoolean());

        if (Flags.ensureKeyguardDoesTransitionStartingBugFix()) {
            // Transition will be created due to sleep token updates. But no keyguard transition
            // should be there when the transition is not initiated from the system UI.
            assertThat(transitions.mLastTransit).flags()
                    .doesNotContain(TRANSIT_FLAG_KEYGUARD_GOING_AWAY);
        } else {
            assertThat(transitions.mLastTransit).flags()
                    .containsExactly(TRANSIT_FLAG_KEYGUARD_GOING_AWAY);
        }
        transitions.flush();

        // Clear AOD. This does *not* clear the going-away status.
        keyguard.setKeyguardShown(displayId, true /* keyguard */, false /* aod */);
        assertTrue(keyguardGoingAway.getAsBoolean());
        assertTrue(appVisible.getAsBoolean());
        assertThat(transitions.mLastTransit).isNull();

        // Finish unlock
        keyguard.setKeyguardShown(displayId, false /* keyguard */, false /* aod */);
        assertFalse(keyguardGoingAway.getAsBoolean());
        assertTrue(appVisible.getAsBoolean());

        assertThat(transitions.mLastTransit).isNull();
    }

    @Test
    public void testKeyguardGoingAwayCanceledWhileAodShown() {
        mDisplayContent.getDisplayPolicy().setAwake(true);

        final KeyguardController keyguard = mAtm.mKeyguardController;
        final ActivityRecord activity = new ActivityBuilder(mAtm).setCreateTask(true).build();
        final int displayId = mDisplayContent.getDisplayId();
        final TestTransitionPlayer transitions = registerTestTransitionPlayer();

        final BooleanSupplier keyguardShowing = () -> keyguard.isKeyguardShowing(displayId);
        final BooleanSupplier keyguardGoingAway = () -> keyguard.isKeyguardGoingAway(displayId);
        final BooleanSupplier appVisible = activity::isVisibleRequested;

        // Begin unlocked.
        keyguard.setKeyguardShown(displayId, false /* keyguard */, false /* aod */);
        transitions.flush();

        // Lock and go to AOD.
        keyguard.setKeyguardShown(displayId, true /* keyguard */, true /* aod */);
        assertFalse(keyguardGoingAway.getAsBoolean());
        assertFalse(appVisible.getAsBoolean());
        if (!Flags.ensureKeyguardDoesTransitionStartingBugFix()) {
            if (Flags.aodTransition()) {
                assertThat(transitions.mLastTransit).flags().contains(TRANSIT_FLAG_AOD_APPEARING);
            } else {
                assertThat(transitions.mLastTransit).flags().doesNotContain(
                        TRANSIT_FLAG_AOD_APPEARING);
            }
        } else {
            assertThat(transitions.mLastTransit).isNull();
        }
        transitions.flush();

        // Start unlocking from AOD.
        keyguard.keyguardGoingAway(0x0 /* flags */);
        assertTrue(keyguardGoingAway.getAsBoolean());
        assertTrue(appVisible.getAsBoolean());

        if (!Flags.ensureKeyguardDoesTransitionStartingBugFix()) {
            assertThat(transitions.mLastTransit).flags()
                    .containsExactly(TRANSIT_FLAG_KEYGUARD_GOING_AWAY);
        }
        transitions.flush();

        // Clear AOD. This does *not* clear the going-away status.
        keyguard.setKeyguardShown(displayId, true /* keyguard */, false /* aod */);
        assertTrue(keyguardGoingAway.getAsBoolean());
        assertTrue(appVisible.getAsBoolean());
        assertThat(transitions.mLastTransit).isNull();

        // Same API call a second time cancels the unlock, because AOD isn't changing.
        keyguard.setKeyguardShown(displayId, true /* keyguard */, false /* aod */);
        assertTrue(keyguardShowing.getAsBoolean());
        assertFalse(keyguardGoingAway.getAsBoolean());
        assertFalse(appVisible.getAsBoolean());

        if (Flags.ensureKeyguardDoesTransitionStartingBugFix()) {
            assertThat(transitions.mLastTransit).isNull();
        } else {
            assertThat(transitions.mLastTransit).flags()
                    .containsExactly(TRANSIT_FLAG_KEYGUARD_APPEARING);
        }
    }

    @Test
    public void testRemoveRootTaskInWindowingModes() {
        removeRootTaskTests(() -> mRootWindowContainer.removeRootTasksInWindowingModes(
                WINDOWING_MODE_FULLSCREEN));
    }

    @Test
    public void testRemoveRootTaskWithActivityTypes() {
        removeRootTaskTests(() -> mRootWindowContainer.removeRootTasksWithActivityTypes(
                ACTIVITY_TYPE_STANDARD));
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testImeChildWindowFocusWhenImeParentWindowChanges() {
        final var imeChildWin = newWindowBuilder("imeChildWin", TYPE_APPLICATION_ATTACHED_DIALOG)
                .setParent(mImeWindow).build();
        doTestImeWindowFocusWhenImeParentWindowChanged(imeChildWin);
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testImeDialogWindowFocusWhenImeParentWindowChanges() {
        final var imeDialogWin = newWindowBuilder("imeDialogWin", TYPE_INPUT_METHOD_DIALOG)
                .build();
        doTestImeWindowFocusWhenImeParentWindowChanged(imeDialogWin);
    }

    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testImeWindowFocusWhenImeParentWindowChanges() {
        // Verify focusable, non-child IME windows.
        final var otherImeWin = newWindowBuilder("otherImeWin", TYPE_INPUT_METHOD).build();
        doTestImeWindowFocusWhenImeParentWindowChanged(otherImeWin);
    }

    private void doTestImeWindowFocusWhenImeParentWindowChanged(@NonNull WindowState window) {
        makeWindowVisibleAndDrawn(window, mImeWindow);
        assertTrue("Window canReceiveKeys", window.canReceiveKeys());
        mDisplayContent.setInputMethodWindowLocked(mImeWindow);

        // Verify window can be focused if the IME parent is visible and the IME is visible.
        final var imeAppTarget = newWindowBuilder("imeAppTarget", TYPE_BASE_APPLICATION).build();
        mDisplayContent.setImeLayeringTarget(imeAppTarget);
        mDisplayContent.updateImeInputAndControlTarget(imeAppTarget);
        final var imeProvider = mDisplayContent.getInsetsStateController().getImeSourceProvider();
        imeProvider.setImeShowing(true);
        final var imeParentWindow = mDisplayContent.getImeParentWindow();
        assertNotNull("IME parent window is not null", imeParentWindow);
        assertTrue("IME parent window is visible", imeParentWindow.isVisibleRequested());
        assertTrue("IME is visible", imeProvider.isImeShowing());
        assertEquals("Window is the focused one", window, mDisplayContent.findFocusedWindow());

        // Verify window can't be focused if the IME parent is not visible.
        final var nextImeAppTarget = newWindowBuilder("nextImeAppTarget", TYPE_BASE_APPLICATION)
                .build();
        makeWindowVisibleAndDrawn(nextImeAppTarget);
        // Change layering target but keep input target (and thus imeParent) the same.
        mDisplayContent.setImeLayeringTarget(nextImeAppTarget);
        // IME parent window is not visible, occluded by new layering target.
        imeParentWindow.setVisibleRequested(false);
        assertEquals("IME parent window did not change", imeParentWindow,
                mDisplayContent.getImeParentWindow());
        assertFalse("IME parent window is not visible", imeParentWindow.isVisibleRequested());
        assertTrue("IME is visible", imeProvider.isImeShowing());
        assertNotEquals("Window is not the focused one when imeParent is not visible", window,
                mDisplayContent.findFocusedWindow());

        // Verify window can be focused if the IME is not visible.
        mDisplayContent.updateImeInputAndControlTarget(nextImeAppTarget);
        imeProvider.setImeShowing(false);
        final var nextImeParentWindow = mDisplayContent.getImeParentWindow();
        assertNotNull("Next IME parent window is not null", nextImeParentWindow);
        assertNotEquals("IME parent window changed", imeParentWindow, nextImeParentWindow);
        assertTrue("Next IME parent window is visible", nextImeParentWindow.isVisibleRequested());
        assertFalse("IME is not visible", imeProvider.isImeShowing());
        if (window.isChildWindow()) {
            assertNotEquals("Child window is not the focused on when the IME is not visible",
                    window, mDisplayContent.findFocusedWindow());
        } else {
            assertEquals("Window is the focused one when the IME is not visible",
                    window, mDisplayContent.findFocusedWindow());
        }
    }

    @Test
    public void testKeepClearAreasMultipleWindows() {
        final var navBarWin = newWindowBuilder("navBarWin", TYPE_NAVIGATION_BAR).build();
        final Rect rect1 = new Rect(0, 0, 10, 10);
        navBarWin.setKeepClearAreas(List.of(rect1), Collections.emptyList());
        final var keyguardWin = newWindowBuilder("keyguardWin", TYPE_NOTIFICATION_SHADE).build();
        final Rect rect2 = new Rect(10, 10, 20, 20);
        keyguardWin.setKeepClearAreas(List.of(rect2), Collections.emptyList());
        mDisplayContent.updateKeepClearAreas();

        // No keep clear areas on display, because the windows are not visible
        assertEquals(Collections.emptySet(), mDisplayContent.mRestrictedKeepClearAreas);

        makeWindowVisible(navBarWin);
        mDisplayContent.updateKeepClearAreas();

        // The returned keep-clear areas contain the areas just from the visible window
        assertEquals(Set.of(rect1), mDisplayContent.mRestrictedKeepClearAreas);

        makeWindowVisible(navBarWin, keyguardWin);
        mDisplayContent.updateKeepClearAreas();

        // The returned keep-clear areas contain the areas from all visible windows
        assertEquals(Set.of(rect1, rect2), mDisplayContent.mRestrictedKeepClearAreas);
    }

    @Test
    public void testHasAccessConsidersUserVisibilityForBackgroundVisibleUsers() {
        doReturn(true).when(UserManager::isVisibleBackgroundUsersEnabled);
        final int appId = 1234;
        final int userId1 = 11;
        final int userId2 = 12;
        final int uid1 = UserHandle.getUid(userId1, appId);
        final int uid2 = UserHandle.getUid(userId2, appId);
        final DisplayInfo displayInfo = new DisplayInfo(mDisplayInfo);
        final DisplayContent dc = createNewDisplay(displayInfo);
        int displayId = dc.getDisplayId();
        doReturn(true).when(mWm.mUmInternal).isUserVisible(userId1, displayId);
        doReturn(false).when(mWm.mUmInternal).isUserVisible(userId2, displayId);

        assertTrue(dc.hasAccess(uid1));
        assertFalse(dc.hasAccess(uid2));
    }

    @Test
    public void testHasAccessIgnoresUserVisibilityForPrivateDisplay() {
        doReturn(true).when(UserManager::isVisibleBackgroundUsersEnabled);
        final int appId = 1234;
        final int userId2 = 12;
        final int uid2 = UserHandle.getUid(userId2, appId);
        final DisplayInfo displayInfo = new DisplayInfo(mDisplayInfo);
        displayInfo.flags = FLAG_PRIVATE;
        displayInfo.ownerUid = uid2;
        final DisplayContent dc = createNewDisplay(displayInfo);
        int displayId = dc.getDisplayId();

        assertTrue(dc.hasAccess(uid2));

        verify(mWm.mUmInternal, never()).isUserVisible(userId2, displayId);
    }

    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @Test
    public void cameraCompatFreeformFlagEnabled_cameraCompatFreeformPolicyNotNull() {
        doReturn(true).when(() ->
                DesktopModeHelper.canEnterDesktopMode(any(Context.class)));

        assertTrue(createNewDisplay().mAppCompatCameraPolicy.hasSimReqOrientationPolicy());
    }

    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @Test
    public void cameraCompatFreeformFlagNotEnabled_cameraCompatFreeformPolicyIsNull() {
        doReturn(true).when(() ->
                DesktopModeHelper.canEnterDesktopMode(any(Context.class)));

        assertFalse(createNewDisplay().mAppCompatCameraPolicy.hasSimReqOrientationPolicy());
    }

    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @DisableFlags({FLAG_ENABLE_DESKTOP_WINDOWING_MODE, FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES})
    @Test
    public void desktopWindowingFlagNotEnabled_cameraCompatFreeformPolicyIsNull() {
        assertFalse(createNewDisplay().mAppCompatCameraPolicy.hasSimReqOrientationPolicy());
    }

    @EnableFlags(FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    @Test
    public void testSetShouldShowSystemDecorations_defaultDisplay() {
        DisplayContent dc = mWm.mRoot.getDisplayContent(DEFAULT_DISPLAY);

        dc.onDisplayInfoChangeApplied();
        assertTrue(dc.mWmService.mDisplayWindowSettings.shouldShowSystemDecorsLocked(dc));
    }

    @EnableFlags(FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    @Test
    public void testSetShouldShowSystemDecorations_privateDisplay() {
        final DisplayInfo displayInfo = new DisplayInfo(mDisplayInfo);
        displayInfo.flags = FLAG_PRIVATE;
        final DisplayContent dc = createNewDisplay(displayInfo);

        dc.onDisplayInfoChangeApplied();
        assertFalse(dc.mWmService.mDisplayWindowSettings.shouldShowSystemDecorsLocked(dc));
    }

    @EnableFlags(FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    @Test
    public void testSetShouldShowSystemDecorations_shouldShowSystemDecorationsDisplay() {
        // Set up a non-default display with FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS enabled
        final DisplayInfo displayInfo = new DisplayInfo(mDisplayInfo);
        displayInfo.displayId = DEFAULT_DISPLAY + 1;
        displayInfo.flags = FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS;
        final DisplayContent dc = createNewDisplay(displayInfo);

        dc.onDisplayInfoChangeApplied();
        assertFalse(dc.mWmService.mDisplayWindowSettings.shouldShowSystemDecorsLocked(dc));
    }

    @EnableFlags(FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    @Test
    public void testSetShouldShowSystemDecorations_notAllowContentModeSwitchDisplay() {
        // Set up a non-default display without FLAG_ALLOWS_CONTENT_MODE_SWITCH enabled
        final DisplayInfo displayInfo = new DisplayInfo(mDisplayInfo);
        displayInfo.displayId = DEFAULT_DISPLAY + 1;
        displayInfo.flags = FLAG_TRUSTED;
        final DisplayContent dc = createNewDisplay(displayInfo);

        dc.onDisplayInfoChangeApplied();
        assertFalse(dc.mWmService.mDisplayWindowSettings.shouldShowSystemDecorsLocked(dc));
    }

    @EnableFlags(FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    @Test
    public void testSetShouldShowSystemDecorations_untrustedDisplay() {
        // Set up a non-default display without FLAG_TRUSTED enabled
        final DisplayInfo displayInfo = new DisplayInfo(mDisplayInfo);
        displayInfo.displayId = DEFAULT_DISPLAY + 1;
        displayInfo.flags = FLAG_ALLOWS_CONTENT_MODE_SWITCH;
        final DisplayContent dc = createNewDisplay(displayInfo);

        dc.onDisplayInfoChangeApplied();
        assertFalse(dc.mWmService.mDisplayWindowSettings.shouldShowSystemDecorsLocked(dc));
    }

    @EnableFlags(FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    @Test
    public void testSetShouldShowSystemDecorations_nonDefaultNonPrivateDisplay() {
        final DisplayInfo displayInfo = new DisplayInfo(mDisplayInfo);
        displayInfo.displayId = DEFAULT_DISPLAY + 1;
        displayInfo.flags = (FLAG_ALLOWS_CONTENT_MODE_SWITCH | FLAG_TRUSTED);
        final DisplayContent dc = createNewDisplay(displayInfo);

        spyOn(dc.mDisplay);
        doReturn(false).when(dc.mDisplay).canHostTasks();
        dc.onDisplayInfoChangeApplied();
        assertFalse(dc.mWmService.mDisplayWindowSettings.shouldShowSystemDecorsLocked(dc));

        doReturn(true).when(dc.mDisplay).canHostTasks();
        dc.onDisplayInfoChangeApplied();
        assertTrue(dc.mWmService.mDisplayWindowSettings.shouldShowSystemDecorsLocked(dc));
    }

    @EnableFlags(FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    @Test
    public void testRemove_displayWithSystemDecorations_emitRemoveSystemDecorations() {
        final DisplayInfo displayInfo = new DisplayInfo(mDisplayInfo);
        displayInfo.displayId = DEFAULT_DISPLAY + 1;
        displayInfo.flags = (FLAG_ALLOWS_CONTENT_MODE_SWITCH | FLAG_TRUSTED);
        final DisplayContent dc = createNewDisplay(displayInfo);
        spyOn(dc.mDisplay);
        doReturn(true).when(dc.mDisplay).canHostTasks();
        dc.onDisplayInfoChangeApplied();
        final DisplayPolicy displayPolicy = dc.getDisplayPolicy();
        spyOn(displayPolicy);

        dc.remove();

        verify(displayPolicy).notifyDisplayRemoveSystemDecorations();
    }

    @EnableFlags(FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    @Test
    public void testRemove_displayWithoutSystemDecorations_dontEmitRemoveSystemDecorations() {
        final DisplayInfo displayInfo = new DisplayInfo(mDisplayInfo);
        displayInfo.displayId = DEFAULT_DISPLAY + 1;
        displayInfo.flags = (FLAG_ALLOWS_CONTENT_MODE_SWITCH | FLAG_TRUSTED);
        final DisplayContent dc = createNewDisplay(displayInfo);
        spyOn(dc.mDisplay);
        doReturn(false).when(dc.mDisplay).canHostTasks();
        dc.onDisplayInfoChangeApplied();
        final DisplayPolicy displayPolicy = dc.getDisplayPolicy();
        spyOn(displayPolicy);

        dc.remove();

        verify(displayPolicy, never()).notifyDisplayRemoveSystemDecorations();
    }

    @EnableFlags(FLAG_ENABLE_PERSISTING_DISPLAY_SIZE_FOR_CONNECTED_DISPLAYS)
    @Test
    public void testForcedDensityRatioSet_persistDensityScaleFlagEnabled() {
        final DisplayInfo displayInfo = new DisplayInfo(mDisplayInfo);
        displayInfo.displayId = DEFAULT_DISPLAY + 1;
        displayInfo.type = Display.TYPE_EXTERNAL;
        final DisplayContent dc = createNewDisplay(displayInfo);
        final int baseWidth = 1280;
        final int baseHeight = 720;
        final int baseDensity = 320;
        final float baseXDpi = 60;
        final float baseYDpi = 60;

        dc.mInitialDisplayWidth = baseWidth;
        dc.mInitialDisplayHeight = baseHeight;
        dc.mInitialDisplayDensity = baseDensity;
        dc.updateBaseDisplayMetrics(baseWidth, baseHeight, baseDensity, baseXDpi, baseYDpi);

        final int forcedDensity = 640;
        dc.setForcedDensityRatio((float) forcedDensity / baseDensity, 0 /* userId */);

        // Verify that density ratio is set correctly.
        assertEquals((float) forcedDensity / baseDensity, dc.mForcedDisplayDensityRatio, 0.01);
        // Verify that density is set correctly.
        assertEquals(forcedDensity, dc.mBaseDisplayDensity);
    }

    @EnableFlags(FLAG_ENABLE_PERSISTING_DISPLAY_SIZE_FOR_CONNECTED_DISPLAYS)
    @Test
    public void testForcedDensityUpdateWithRatio_persistDensityScaleFlagEnabled() {
        final DisplayInfo displayInfo = new DisplayInfo(mDisplayInfo);
        displayInfo.displayId = DEFAULT_DISPLAY + 1;
        displayInfo.type = Display.TYPE_EXTERNAL;
        final DisplayContent dc = createNewDisplay(displayInfo);
        final int baseWidth = 1280;
        final int baseHeight = 720;
        final int baseDensity = 320;
        final float baseXDpi = 60;
        final float baseYDpi = 60;

        dc.mInitialDisplayWidth = baseWidth;
        dc.mInitialDisplayHeight = baseHeight;
        dc.mInitialDisplayDensity = baseDensity;
        dc.updateBaseDisplayMetrics(baseWidth, baseHeight, baseDensity, baseXDpi, baseYDpi);

        final int forcedDensity = 640;
        dc.setForcedDensityRatio((float) forcedDensity / baseDensity, 0 /* userId */);

        // Verify that density ratio is set correctly.
        assertEquals(2.0f, dc.mForcedDisplayDensityRatio, 0.001);


        dc.mInitialDisplayDensity = 160;
        dc.updateBaseDisplayMetrics(baseWidth, baseHeight, baseDensity, baseXDpi, baseYDpi);

        // Verify that forced density is updated based on the ratio.
        assertEquals(320, dc.mBaseDisplayDensity);
    }

    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testIsTaskMoveAllowedOnDisplay() {
        final TaskDisplayArea taskDisplayArea = mDisplayContent.getDefaultTaskDisplayArea();
        final Task rootTask = taskDisplayArea.createRootTask(WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD, ON_TOP);

        taskDisplayArea.setIsTaskMoveAllowed(true);
        assertTrue(mDisplayContent.isTaskMoveAllowedOnDisplay());

        rootTask.setIsTaskMoveAllowed(true);
        assertTrue(mDisplayContent.isTaskMoveAllowedOnDisplay());

        taskDisplayArea.setIsTaskMoveAllowed(false);
        assertTrue(mDisplayContent.isTaskMoveAllowedOnDisplay());

        rootTask.setIsTaskMoveAllowed(false);
        assertFalse(mDisplayContent.isTaskMoveAllowedOnDisplay());
    }

    @Test
    public void testIsRemoved_nonDefaultDisplay_isNotValid() {
        final DisplayInfo displayInfo = new DisplayInfo(mDisplayInfo);
        displayInfo.displayId = DEFAULT_DISPLAY + 1;
        final DisplayContent dc = createNewDisplay(displayInfo);
        spyOn(dc.mDisplay);
        doReturn(false).when(dc.mDisplay).isValid();
        assertTrue(dc.isRemoved());
    }

    private void removeRootTaskTests(@NonNull Runnable runnable) {
        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        final Task rootTask1 = taskDisplayArea.createRootTask(WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD, ON_TOP);
        final Task rootTask2 = taskDisplayArea.createRootTask(WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD, ON_TOP);
        final Task rootTask3 = taskDisplayArea.createRootTask(WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD, ON_TOP);
        final Task rootTask4 = taskDisplayArea.createRootTask(WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD, ON_TOP);
        final Task task1 = new TaskBuilder(mSupervisor).setParentTask(rootTask1).build();
        final Task task2 = new TaskBuilder(mSupervisor).setParentTask(rootTask2).build();
        final Task task3 = new TaskBuilder(mSupervisor).setParentTask(rootTask3).build();
        final Task task4 = new TaskBuilder(mSupervisor).setParentTask(rootTask4).build();

        // Reordering root tasks while removing root tasks.
        doAnswer(invocation -> {
            taskDisplayArea.positionChildAt(POSITION_TOP, rootTask3, false /* includingParents */);
            return true;
        }).when(mSupervisor).removeTask(eq(task4), anyBoolean(), anyBoolean(), any());

        // Removing root tasks from the display while removing root tasks.
        doAnswer(invocation -> {
            taskDisplayArea.removeRootTask(rootTask2);
            return true;
        }).when(mSupervisor).removeTask(eq(task2), anyBoolean(), anyBoolean(), any());

        runnable.run();
        verify(mSupervisor).removeTask(eq(task4), anyBoolean(), anyBoolean(), any());
        verify(mSupervisor).removeTask(eq(task3), anyBoolean(), anyBoolean(), any());
        verify(mSupervisor).removeTask(eq(task2), anyBoolean(), anyBoolean(), any());
        verify(mSupervisor).removeTask(eq(task1), anyBoolean(), anyBoolean(), any());
    }

    private boolean isOptionsPanelAtRight(int displayId) {
        return (mWm.getPreferredOptionsPanelGravity(displayId) & Gravity.RIGHT) == Gravity.RIGHT;
    }

    private static void verifySizes(@NonNull DisplayContent displayContent, int expectedBaseWidth,
            int expectedBaseHeight, int expectedBaseDensity) {
        assertEquals(expectedBaseWidth, displayContent.mBaseDisplayWidth);
        assertEquals(expectedBaseHeight, displayContent.mBaseDisplayHeight);
        assertEquals(expectedBaseDensity, displayContent.mBaseDisplayDensity);
    }

    private static void verifySizes(@NonNull DisplayContent displayContent, int expectedBaseWidth,
            int expectedBaseHeight, int expectedBaseDensity, float expectedBaseXDpi,
            float expectedBaseYDpi) {
        assertEquals(expectedBaseWidth, displayContent.mBaseDisplayWidth);
        assertEquals(expectedBaseHeight, displayContent.mBaseDisplayHeight);
        assertEquals(expectedBaseDensity, displayContent.mBaseDisplayDensity);
        assertEquals(expectedBaseXDpi, displayContent.mBaseDisplayPhysicalXDpi, 1.0f /* delta */);
        assertEquals(expectedBaseYDpi, displayContent.mBaseDisplayPhysicalYDpi, 1.0f /* delta */);
    }

    private void updateFocusedWindow() {
        mWm.updateFocusedWindowLocked(UPDATE_FOCUS_NORMAL, false /* updateInputWindows */);
    }

    static void performLayout(@NonNull DisplayContent dc) {
        dc.setLayoutNeeded();
        dc.performLayout(true /* initial */, false /* updateImeWindows */);
    }

    /**
     * Create DisplayContent that does not update display base/initial values from device to keep
     * the values set by test.
     */
    @NonNull
    private DisplayContent createDisplayNoUpdateDisplayInfo() {
        final DisplayContent dc = createNewDisplay();
        doNothing().when(dc).updateDisplayInfo(any(DisplayInfo.class));
        return dc;
    }

    private void assertForAllWindowsOrder(@NonNull List<WindowState> expectedWindowsBottomToTop) {
        final var actualWindows = new ArrayList<WindowState>();

        // Test forward traversal.
        mDisplayContent.forAllWindows(actualWindows::addLast, false /* traverseTopToBottom */);
        assertEquals("bottomToTop", expectedWindowsBottomToTop, actualWindows);

        actualWindows.clear();

        // Test backward traversal.
        mDisplayContent.forAllWindows(actualWindows::addLast, true /* traverseTopToBottom */);
        assertEquals("topToBottom", expectedWindowsBottomToTop.reversed(), actualWindows);
    }

    @ScreenOrientation
    private static int getRotatedOrientation(DisplayContent dc) {
        return dc.mBaseDisplayWidth > dc.mBaseDisplayHeight
                ? SCREEN_ORIENTATION_PORTRAIT
                : SCREEN_ORIENTATION_LANDSCAPE;
    }

    private static void updateDisplay(@NonNull DisplayContent displayContent) {
        final var future = new CompletableFuture<>();
        displayContent.requestDisplayUpdate(() -> future.complete(new Object()));
        try {
            future.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
