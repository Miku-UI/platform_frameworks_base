/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_DREAM;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.content.pm.ActivityInfo.LAUNCH_SINGLE_INSTANCE;
import static android.content.pm.ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE;
import static android.content.pm.ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM_VALUE;
import static android.content.pm.ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_SMALL_VALUE;
import static android.content.pm.ActivityInfo.RESIZE_MODE_RESIZEABLE;
import static android.content.pm.ActivityInfo.RESIZE_MODE_UNRESIZEABLE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.content.pm.PackageManager.USER_MIN_ASPECT_RATIO_16_9;
import static android.content.pm.PackageManager.USER_MIN_ASPECT_RATIO_3_2;
import static android.content.pm.PackageManager.USER_MIN_ASPECT_RATIO_4_3;
import static android.content.pm.PackageManager.USER_MIN_ASPECT_RATIO_DISPLAY_SIZE;
import static android.content.pm.PackageManager.USER_MIN_ASPECT_RATIO_SPLIT_SCREEN;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.util.DisplayMetrics.DENSITY_DEFAULT;
import static android.view.Surface.ROTATION_90;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.internal.policy.DesktopModeLaunchBoundsUtils.centerInScreen;
import static com.android.internal.policy.SystemBarUtils.getDesktopViewAppHeaderHeightPx;
import static com.android.server.wm.DesktopModeBoundsCalculator.DESKTOP_MODE_INITIAL_BOUNDS_SCALE;
import static com.android.server.wm.DesktopModeBoundsCalculator.DESKTOP_MODE_LANDSCAPE_APP_PADDING;
import static com.android.server.wm.LaunchParamsController.LaunchParamsModifier.RESULT_CONTINUE;
import static com.android.server.wm.LaunchParamsController.LaunchParamsModifier.RESULT_DONE;
import static com.android.server.wm.LaunchParamsController.LaunchParamsModifier.RESULT_SKIP;
import static com.android.server.wm.SizeCompatTests.rotateDisplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.ActivityOptions;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.util.Size;
import android.view.Gravity;

import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.internal.policy.DesktopModeCompatPolicy;
import com.android.internal.policy.DesktopModeCompatUtils;
import com.android.window.flags.Flags;

import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Tests for desktop mode task bounds.
 *
 * Build/Install/Run:
 * atest WmTests:DesktopModeLaunchParamsModifierTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class DesktopModeLaunchParamsModifierTests extends
        LaunchParamsModifierTestsBase<DesktopModeLaunchParamsModifier> {
    private static final int TEST_USER_ID = 10;
    private static final Rect LANDSCAPE_DISPLAY_BOUNDS = new Rect(0, 0, 2560, 1600);
    private static final Rect PORTRAIT_DISPLAY_BOUNDS = new Rect(0, 0, 1600, 2560);
    private static final float LETTERBOX_ASPECT_RATIO = 1.3f;
    private static final ComponentName HOME_ACTIVITIES = new ComponentName(/* package */
            "com.android.launcher", /* class */ "");

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    public PackageManager mPackageManager = mock(PackageManager.class);
    private static final ComponentName LAUNCH_IN_FULLSCREEN_BY_ALLOWLIST = new ComponentName(
            "com.allowlist.package", "");

    @Before
    public void setUp() throws Exception {
        mActivity = new ActivityBuilder(mAtm).setUid(TEST_USER_ID *  UserHandle.PER_USER_RANGE)
                .build();
        mCurrent = new LaunchParamsController.LaunchParams();
        mCurrent.reset();
        mResult = new LaunchParamsController.LaunchParams();
        mResult.reset();

        final Context spyContext = spy(mContext);
        final DesktopModeCompatPolicy desktopModeCompatPolicy =
                spy(new DesktopModeCompatPolicy(spyContext));
        mTarget = spy(new DesktopModeLaunchParamsModifier(spyContext, mSupervisor,
                desktopModeCompatPolicy));
        doReturn(true).when(mTarget).isEnteringDesktopMode(any(), any(), any(), any());
        doReturn(mPackageManager).when(spyContext).getPackageManager();
        doReturn(HOME_ACTIVITIES.getPackageName()).when(desktopModeCompatPolicy)
                .getDefaultHomePackage(anyInt());
        doReturn(true).when(desktopModeCompatPolicy)
                .isPackageLaunchInFullscreen(eq(LAUNCH_IN_FULLSCREEN_BY_ALLOWLIST));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsSkipIfDesktopWindowingIsDisabled() {
        setupDesktopModeLaunchParamsModifier();

        assertEquals(RESULT_SKIP, new CalculateRequestBuilder().setTask(null).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsSkipIfDesktopWindowingIsEnabledOnUnsupportedDevice() {
        setupDesktopModeLaunchParamsModifier(/*isDesktopModeSupported=*/ false,
                /*enforceDeviceRestrictions=*/ true, /*doesDisplaySupportDesktop*/ true);

        assertEquals(RESULT_SKIP, new CalculateRequestBuilder().setTask(null).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsSkipIfDesktopWindowingIsNotSupportedOnTargetDisplay() {
        setupDesktopModeLaunchParamsModifier(/*isDesktopModeSupported=*/ true,
                /*enforceDeviceRestrictions=*/ true, /*doesDisplaySupportDesktop*/ false);

        assertEquals(RESULT_SKIP, new CalculateRequestBuilder().setTask(null).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsContinueIfDesktopWindowingIsEnabledAndUnsupportedDeviceOverridden() {
        setupDesktopModeLaunchParamsModifier(/*isDesktopModeSupported=*/ true,
                /*enforceDeviceRestrictions=*/ false, /*doesDisplaySupportDesktop*/ true);

        final Task task = createStandardTask();
        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsSkipIfTaskIsNull() {
        setupDesktopModeLaunchParamsModifier();

        assertEquals(RESULT_SKIP, new CalculateRequestBuilder().setTask(null).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsSkipIfIsEnteringDesktopModeFalse() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenReturn(false);

        final Task task = createStandardTask();

        assertEquals(RESULT_SKIP, new CalculateRequestBuilder().setTask(task).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testAppliesFullscreenAndReturnDoneIfRequestViaActivityOptions() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenReturn(true);

        final Task task = createStandardTask();
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);

        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(task).setOptions(options).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
        assertTrue(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    public void testDreamActivitiesForcedToFullscreenWithoutTask() {
        setupDesktopModeLaunchParamsModifier();

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        mActivity.setActivityType(ACTIVITY_TYPE_DREAM);

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(null).setOptions(options)
                .calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
    }

    @Test
    public void testDreamActivitiesForcedToFullscreen() {
        setupDesktopModeLaunchParamsModifier();

        final Task task = createStandardTask();
        mActivity.setActivityType(ACTIVITY_TYPE_DREAM);

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(task).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
    }

    @Test
    public void testHomeActivitiesForcedToFullscreenWithoutTask() {
        setupDesktopModeLaunchParamsModifier();

        final DisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        options.setLaunchDisplayId(display.mDisplayId);
        final Task task = createStandardTask();
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ true, /* isResizeable */ false,
                HOME_ACTIVITIES);

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(null).setOptions(options)
                .setActivity(activity).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
    }

    @Test
    public void testHomeActivitiesForcedToFullscreen() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask();
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ true, /* isResizeable */ false,
                HOME_ACTIVITIES);

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(task).setActivity(
                activity).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
    }

    @Test
    public void testSystemUIActivitiesForcedToFullscreenWithoutTask() {
        setupDesktopModeLaunchParamsModifier();

        final DisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        options.setLaunchDisplayId(display.mDisplayId);
        final Task task = createStandardTask();
        final ComponentName sysUiComponent = new ComponentName(
                mContext.getResources().getString(R.string.config_systemUi), "");
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ true, /* isResizeable */ false,
                sysUiComponent);

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(null)
                .setOptions(options).setActivity(activity).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
    }

    @Test
    public void testSystemUIActivitiesForcedToFullscreen() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask();
        final ComponentName sysUiComponent = new ComponentName(
                mContext.getResources().getString(R.string.config_systemUi), "");
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ true, /* isResizeable */ false,
                sysUiComponent);

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(task).setActivity(
                activity).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
    }

    @Test
    @EnableFlags({
        Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
        Flags.FLAG_LAUNCH_GAMES_IN_FULLSCREEN_BY_ALLOWLIST
    })
    public void testFullscreenLaunchByAllowlist_inAllowlist_activityWithTask_touchFirstInDesktopMode_launchesInFullscreen() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();
        // Set up a touch-first display with an active desk.
        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task deskRoot = setUpActiveDeskRootTask(display);
        // Create a desktop task to simulate being in desktop mode
        final Task desktopTask = new TaskBuilder(mSupervisor)
                .setCreateActivity(true)
                .setDisplay(display)
                .setParentTask(deskRoot)
                .setWindowingMode(WINDOWING_MODE_UNDEFINED)
                .build();

        // Create an activity and task from a package in the fullscreen-by-default allowlist.
        final Task task = new TaskBuilder(mSupervisor).setDisplay(display).build();
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(display.mDisplayId);
        final ActivityRecord activity = new ActivityBuilder(mAtm)
                .setComponent(LAUNCH_IN_FULLSCREEN_BY_ALLOWLIST)
                .setTask(task)
                .build();

        // Verify it's forced to launch in fullscreen, despite being in desktop mode.
        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(task).setActivity(
                activity).setOptions(options).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
    }

    @Test
    @EnableFlags({
        Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
        Flags.FLAG_LAUNCH_GAMES_IN_FULLSCREEN_BY_ALLOWLIST
    })
    public void testFullscreenLaunchByAllowlist_inAllowlist_activityWithTask_desktopFirst_launchesInFullscreen() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();
        // Set up a desktop-first display.
        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FREEFORM);

        // Create an activity and task from a package in the fullscreen-by-default allowlist.
        final Task task = new TaskBuilder(mSupervisor).setDisplay(display).build();
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(display.mDisplayId);
        final ActivityRecord activity = new ActivityBuilder(mAtm)
                .setComponent(LAUNCH_IN_FULLSCREEN_BY_ALLOWLIST)
                .setTask(task)
                .build();

        // Verify it's forced to launch in fullscreen, despite being a desktop-first display.
        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(task).setActivity(
                activity).setOptions(options).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
    }

    @Test
    @EnableFlags({
        Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
        Flags.FLAG_LAUNCH_GAMES_IN_FULLSCREEN_BY_ALLOWLIST
    })
    public void testFullscreenLaunchByAllowlist_inAllowlist_activityWithoutTask_touchFirstInDesktopMode_launchesInFullscreen() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();
        // Set up a touch-first display with an active desk.
        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task deskRoot = setUpActiveDeskRootTask(display);
        // Create a desktop task to simulate being in desktop mode
        final Task desktopTask = new TaskBuilder(mSupervisor)
                .setCreateActivity(true)
                .setDisplay(display)
                .setParentTask(deskRoot)
                .setWindowingMode(WINDOWING_MODE_UNDEFINED)
                .build();

        // Create an activity from a package in the fullscreen-by-default allowlist.
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(display.mDisplayId);
        final ActivityRecord activity = new ActivityBuilder(mAtm)
                .setComponent(LAUNCH_IN_FULLSCREEN_BY_ALLOWLIST)
                .build();

        // Verify it's forced to launch in fullscreen, despite being in desktop mode.
        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(null).setActivity(
                activity).setOptions(options).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
    }

    @Test
    @EnableFlags({
        Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
        Flags.FLAG_LAUNCH_GAMES_IN_FULLSCREEN_BY_ALLOWLIST
    })
    public void testFullscreenLaunchByAllowlist_inAllowlist_activityWithoutTask_desktopFirst_launchesInFullscreen() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();
        // Set up a desktop-first display.
        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FREEFORM);

        // Create an activity from a package in the fullscreen-by-default allowlist.
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(display.mDisplayId);
        final ActivityRecord activity = new ActivityBuilder(mAtm)
                .setComponent(LAUNCH_IN_FULLSCREEN_BY_ALLOWLIST)
                .build();

        // Verify it's forced to launch in fullscreen, despite being a desktop-first display.
        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(null).setOptions(options)
                .setActivity(activity).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
    }

    @Test
    @EnableFlags({
        Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
        Flags.FLAG_LAUNCH_GAMES_IN_FULLSCREEN_BY_ALLOWLIST
    })
    public void testFullscreenLaunchByAllowlist_notInAllowlist_touchFirstInDesktopMode_launchesInDesktop() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();
        // Set up a touch-first display with an active desk.
        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task deskRoot = setUpActiveDeskRootTask(display);
        // Create a desktop task to simulate being in desktop mode
        final Task desktopTask = new TaskBuilder(mSupervisor)
                .setCreateActivity(true)
                .setDisplay(display)
                .setParentTask(deskRoot)
                .setWindowingMode(WINDOWING_MODE_UNDEFINED)
                .build();

        // Create an activity from a package not in the fullscreen-by-default allowlist.
        final Task task = new TaskBuilder(mSupervisor).setDisplay(display).build();
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(display.mDisplayId);
        final ActivityRecord activity = new ActivityBuilder(mAtm)
                .setComponent(new ComponentName("com.normal.package", "NormalClass"))
                .setTask(task)
                .build();

        // Does not force launch in fullscreen, it launches undefined to inherit from parent desk.
        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setOptions(options)
                .setActivity(activity).calculate());
        assertEquals(WINDOWING_MODE_UNDEFINED, mResult.mWindowingMode);
    }

    @Test
    @EnableFlags({
        Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
        Flags.FLAG_LAUNCH_GAMES_IN_FULLSCREEN_BY_ALLOWLIST
    })
    public void testFullscreenLaunchByAllowlist_notInAllowlist_activityWithoutTask_desktopFirst_launchesInDesktop() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();
        // Set up a desktop-first display with an active desk.
        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FREEFORM);
        final Task deskRoot = setUpActiveDeskRootTask(display);

        // Create an activity from a package not in the fullscreen-by-default allowlist.
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(display.mDisplayId);
        final ActivityRecord activity = new ActivityBuilder(mAtm)
                .setComponent(new ComponentName("com.normal.package", "NormalClass"))
                .build();

        // Does not force launch in fullscreen, it launches freeform because it's a desktop-first
        // display.
        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(null).setOptions(options)
                .setActivity(activity).calculate());
        assertEquals(WINDOWING_MODE_FREEFORM, mResult.mWindowingMode);
    }

    @Test
    public void testTransparentActivitiesWithPlatformSignatureForcedToFullscreenWithoutTask() {
        setupDesktopModeLaunchParamsModifier();

        final DisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        options.setLaunchDisplayId(display.mDisplayId);
        mActivity.setOccludesParent(false);
        mActivity.info.applicationInfo = new ApplicationInfo();
        mActivity.info.applicationInfo.privateFlags =
                ApplicationInfo.PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY;

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(null).setOptions(options)
                .calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
    }

    @Test
    public void testTransparentActivitiesWithPlatformSignatureForcedToFullscreen() {
        setupDesktopModeLaunchParamsModifier();

        final Task task = createStandardTask();
        mActivity.setOccludesParent(false);
        mActivity.info.applicationInfo = new ApplicationInfo();
        mActivity.info.applicationInfo.privateFlags =
                ApplicationInfo.PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY;

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(task).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
        assertTrue(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    public void testTransparentActivitiesWithPermissionForcedToFullscreenWithoutTask()
            throws PackageManager.NameNotFoundException {
        setupDesktopModeLaunchParamsModifier();

        final DisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        options.setLaunchDisplayId(display.mDisplayId);
        mActivity.setOccludesParent(false);
        allowOverlayPermissionForAllUsers(
                new String[]{android.Manifest.permission.SYSTEM_ALERT_WINDOW});

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(null).setOptions(options)
                .calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
    }

    @Test
    public void testTransparentActivitiesWithPermissionForcedToFullscreen()
            throws PackageManager.NameNotFoundException {
        setupDesktopModeLaunchParamsModifier();

        final Task task = createStandardTask();
        mActivity.setOccludesParent(false);
        allowOverlayPermissionForAllUsers(
                new String[]{android.Manifest.permission.SYSTEM_ALERT_WINDOW});

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(task).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
        assertTrue(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsContinueIfFreeformTaskExists() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();

        final DisplayContent dc = spy(createNewDisplay());
        final Task existingFreeformTask = createFreeformTask(/* createActivity */ true);
        doReturn(existingFreeformTask).when(dc).getTask(any());
        final Task launchingTask = createStandardTask();
        launchingTask.onDisplayChanged(dc);

        assertEquals(RESULT_CONTINUE,
                new CalculateRequestBuilder().setTask(launchingTask).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsContinueIfTaskInFreeform() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();

        final Task task = createFreeformTask(/* createActivity */ false);

        assertEquals(RESULT_CONTINUE,
                new CalculateRequestBuilder().setTask(task).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsContinueIfFreeformRequestViaActivityOptions() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();

        final Task task = createStandardTask();
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);

        assertEquals(RESULT_CONTINUE,
                new CalculateRequestBuilder().setTask(task).setOptions(options).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsContinueIfFreeformRequestViaPreviousModifier() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();

        final Task task = createStandardTask();
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);

        assertEquals(RESULT_CONTINUE,
                new CalculateRequestBuilder().setTask(task).setOptions(options).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsSkipIfTaskNotUsingActivityTypeStandardOrUndefined() {
        setupDesktopModeLaunchParamsModifier();

        final Task task = createStandardTask(ACTIVITY_TYPE_ASSISTANT);
        assertEquals(RESULT_SKIP, new CalculateRequestBuilder().setTask(task).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsContinueIfTaskUsingActivityTypeStandard() {
        setupDesktopModeLaunchParamsModifier();

        final Task task = createStandardTask();
        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testReturnsContinueIfTaskUsingActivityTypeUndefined() {
        setupDesktopModeLaunchParamsModifier();

        final Task task = createStandardTask(ACTIVITY_TYPE_UNDEFINED);
        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).calculate());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testIgnoreCurrentParamsBounds() {
        setupDesktopModeLaunchParamsModifier();

        final Task task = createStandardTask();
        mCurrent.mBounds.set(/* left */ 0, /* top */ 0, /* right */ 100, /* bottom */ 100);
        new CalculateRequestBuilder().setTask(task).calculate();
        assertNotEquals(mCurrent.mBounds, mResult.mBounds);
    }

    @Test
    public void testReturnsDoneIfTaskNullLaunchInFreeform() {
        setupDesktopModeLaunchParamsModifier();

        final DisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        options.setLaunchDisplayId(display.mDisplayId);

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(null)
                .setOptions(options).calculate());
        assertEquals(options.getLaunchWindowingMode(), mResult.mWindowingMode);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @DisableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    public void testPreserveOrientationAndAspectRatioFromRecentsTaskRelaunch() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ false);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ true, /* isResizeable */ false,
                /* componentName */ null);
        activity.mAppCompatController.getSizeCompatModePolicy().updateAppCompatDisplayInsets();
        assertNotNull(activity.getAppCompatDisplayInsets());
        final float expectedAspectRatio = activity.getAppCompatDisplayInsets().mAspectRatio;
        final ActivityOptions options = ActivityOptions.makeBasic().setFlexibleLaunchSize(
                true).setLaunchBounds(new Rect());
        task.inRecents = true;

        rotateDisplay(display, ROTATION_90);

        assertTrue(activity.inSizeCompatMode());
        // Simulate task in recents launched into desktop via taskbar.
        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).setOptions(options).calculate());
        // Original orientation and aspect ratio of activity is maintained.
        assertEquals(ORIENTATION_PORTRAIT,
                DesktopModeCompatUtils.computeConfigOrientation(mResult.mBounds));
        assertEquals(expectedAspectRatio,
                AppCompatUtils.computeAspectRatio(mResult.mAppBounds), /* delta */ 0.05);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE})
    public void testInheritSourceTaskBoundsFromExistingInstanceIfClosing() {
        setupDesktopModeLaunchParamsModifier();

        final String packageName = "com.same.package";
        // Setup existing task.
        final DisplayContent dc = spy(createNewDisplay());
        final Task sourceTask = createFreeformTask(/* createActivity */ true, packageName);
        sourceTask.getTopMostActivity().launchMode = LAUNCH_SINGLE_INSTANCE;
        sourceTask.setBounds(
                /* left */ 0,
                /* top */ 0,
                /* right */ 500,
                /* bottom */ 500);
        // Set up new instance of already existing task. By default multi instance is not supported
        // so first instance will close.
        final Task launchingTask = createFreeformTask(/* createActivity */ true, packageName);
        launchingTask.getTopMostActivity().launchMode = LAUNCH_SINGLE_INSTANCE;
        launchingTask.onDisplayChanged(dc);

        // New instance should inherit task bounds of old instance.
        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(launchingTask)
                        .setActivity(launchingTask.getTopMostActivity())
                        .setSource(sourceTask.getTopMostActivity()).calculate());
        assertEquals(sourceTask.getBounds(), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testInheritSourceTaskBoundsFromExistingInstanceIfNoLongerVisible() {
        setupDesktopModeLaunchParamsModifier();

        final String packageName = "com.same.package";
        // Setup existing task.
        final Task sourceTask = spy(createFreeformTask(/* createActivity */ true, packageName));
        sourceTask.setBounds(
                /* left */ 0,
                /* top */ 0,
                /* right */ 500,
                /* bottom */ 500);
        // Set source task activity as invisible.
        final ActivityRecord sourceTaskActivity = spy(sourceTask.getTopMostActivity());
        sourceTask.getTopMostActivity().launchMode = LAUNCH_SINGLE_INSTANCE;
        doReturn(false).when(sourceTaskActivity).isVisible();
        // Set up new instance of already existing task.
        final Task launchingTask = createStandardTaskWithActivity(packageName, TEST_USER_ID);

        // New instance should inherit task bounds of old instance.
        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(launchingTask)
                        .setActivity(launchingTask.getTopMostActivity())
                        .setSource(sourceTaskActivity).calculate());
        assertEquals(sourceTask.getBounds(), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testInheritTaskBoundsFromExistingInstanceIfClosing() {
        setupDesktopModeLaunchParamsModifier();

        final String packageName = "com.same.package";
        // Setup existing task.
        final DisplayContent dc = spy(createNewDisplay());
        final Task existingFreeformTask = createFreeformTask(
                /* createActivity */ true, packageName);
        existingFreeformTask.getTopMostActivity().launchMode = LAUNCH_SINGLE_INSTANCE;
        existingFreeformTask.setBounds(
                /* left */ 0,
                /* top */ 0,
                /* right */ 500,
                /* bottom */ 500);
        doReturn(existingFreeformTask.getTopMostActivity()).when(dc)
                .getTopMostVisibleFreeformActivity();
        // Set up new instance of already existing task. By default multi instance is not supported
        // so first instance will close.
        final Task launchingTask = createStandardTaskWithActivity(packageName, TEST_USER_ID);
        launchingTask.getTopMostActivity().launchMode = LAUNCH_SINGLE_INSTANCE;
        launchingTask.onDisplayChanged(dc);

        // New instance should inherit task bounds of old instance.
        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(launchingTask)
                        .setActivity(launchingTask.getTopMostActivity()).calculate());
        assertEquals(existingFreeformTask.getBounds(), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testDontInheritTaskBoundsFromExistingInstanceIfDifferentUser() {
        setupDesktopModeLaunchParamsModifier();

        final String packageName = "com.same.package";
        // Setup existing task.
        final DisplayContent dc = spy(createNewDisplay());
        final Task existingFreeformTask = createFreeformTask(
                /* createActivity */ true, packageName);
        existingFreeformTask.getTopMostActivity().launchMode = LAUNCH_SINGLE_INSTANCE;
        existingFreeformTask.setBounds(
                /* left */ 0,
                /* top */ 0,
                /* right */ 500,
                /* bottom */ 500);
        doReturn(existingFreeformTask.getTopMostActivity()).when(dc)
                .getTopMostVisibleFreeformActivity();
        // Set up new instance of already existing task.
        final Task launchingTask = createStandardTaskWithActivity(packageName, /* userId */ 100);
        launchingTask.getTopMostActivity().launchMode = LAUNCH_SINGLE_INSTANCE;
        launchingTask.onDisplayChanged(dc);


        new CalculateRequestBuilder().setTask(launchingTask)
                .setActivity(launchingTask.getTopMostActivity()).calculate();
        // New instance should not inherit task bounds of old instance.
        assertNotEquals(existingFreeformTask.getBounds(), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @DisableFlags(Flags.FLAG_ENABLE_TRAMPOLINE_TASK_AFFINITY_BUGFIX)
    public void testDontInheritTaskBoundsFromExistingInstanceIfDifferentPackage() {
        setupDesktopModeLaunchParamsModifier();

        final String packageName1 = "com.package.one";
        final String packageName2 = "com.package.two";
        // Setup existing task.
        final DisplayContent dc = spy(createNewDisplay());
        final Task existingFreeformTask = createFreeformTask(
                /* createActivity */ true, packageName1);
        existingFreeformTask.getTopMostActivity().launchMode = LAUNCH_SINGLE_INSTANCE;
        existingFreeformTask.setBounds(
                /* left */ 0,
                /* top */ 0,
                /* right */ 500,
                /* bottom */ 500);
        doReturn(existingFreeformTask.getTopMostActivity()).when(dc)
                .getTopMostVisibleFreeformActivity();
        // Set up new instance of a task from a different package.
        final Task launchingTask = createStandardTaskWithActivity(packageName2, TEST_USER_ID);
        launchingTask.getTopMostActivity().launchMode = LAUNCH_SINGLE_INSTANCE;
        launchingTask.onDisplayChanged(dc);


        new CalculateRequestBuilder().setTask(launchingTask)
                .setActivity(launchingTask.getTopMostActivity()).calculate();
        // New instance should not inherit task bounds of old instance as packages differ.
        assertNotEquals(existingFreeformTask.getBounds(), mResult.mBounds);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
            Flags.FLAG_ENABLE_TRAMPOLINE_TASK_AFFINITY_BUGFIX})
    public void testDontInheritTaskBoundsFromExistingInstanceIfDifferentBasePackage() {
        setupDesktopModeLaunchParamsModifier();

        final String packageName1 = "com.package.one";
        final String packageName2 = "com.package.two";
        // Setup existing task.
        final DisplayContent dc = spy(createNewDisplay());
        final Task existingFreeformTask = createFreeformTask(
                /* createActivity */ true, packageName1);
        existingFreeformTask.getTopMostActivity().launchMode = LAUNCH_SINGLE_INSTANCE;
        existingFreeformTask.setBounds(
                /* left */ 0,
                /* top */ 0,
                /* right */ 500,
                /* bottom */ 500);
        doReturn(existingFreeformTask.getTopMostActivity()).when(dc)
                .getTopMostVisibleFreeformActivity();
        // Set up new instance of a task with an activity from the existing tasks package.
        final Task launchingTask = spy(createFreeformTask(/* createActivity */ true, packageName1));
        // Now mock task to belong to a different base package.
        doReturn(packageName2).when(launchingTask).getBasePackageName();
        launchingTask.getTopMostActivity().launchMode = LAUNCH_SINGLE_INSTANCE;
        launchingTask.onDisplayChanged(dc);


        new CalculateRequestBuilder().setTask(launchingTask)
                .setActivity(launchingTask.getRootActivity()).calculate();
        // New instance should not inherit task bounds of existing instance as task base packages
        // differ despite sharing the same top activity package..
        assertNotEquals(existingFreeformTask.getBounds(), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testDontInheritTaskBoundsFromSameTask() {
        setupDesktopModeLaunchParamsModifier();

        final String packageName = "com.same.package";
        // Setup existing task.
        final DisplayContent dc = spy(createNewDisplay());
        final Task existingFreeformTask = spy(createFreeformTask(
                /* createActivity */ true, packageName));
        existingFreeformTask.getTopMostActivity().launchMode = LAUNCH_SINGLE_INSTANCE;
        existingFreeformTask.setBounds(
                /* left */ 0,
                /* top */ 0,
                /* right */ 500,
                /* bottom */ 500);
        doReturn(existingFreeformTask.getTopMostActivity()).when(dc)
                .getTopMostVisibleFreeformActivity();
        existingFreeformTask.onDisplayChanged(dc);
        // Mock task to not trigger override bounds logic.
        doReturn(false).when(existingFreeformTask).hasOverrideBounds();

        // New instance should not inherit task bounds of old instance.
        new CalculateRequestBuilder().setTask(existingFreeformTask).calculate();
        assertNotEquals(existingFreeformTask.getBounds(), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testRespectOverrideTaskBoundsIfValid() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FREEFORM);
        // Make home visible to trigger desktop-first policy.
        final Task homeTask = createHomeTask(display);
        homeTask.setVisibleRequested(true);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        // Override task bounds within display.
        final Rect displayStableBounds = new Rect();
        display.getStableRect(displayStableBounds);
        task.setBounds(displayStableBounds);

        // Task bounds should be respect.
        new CalculateRequestBuilder().setTask(task).calculate();
        assertEquals(displayStableBounds, mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testDontRespectOverrideTaskBoundsIfNotValid() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FREEFORM);
        // Make home visible to trigger desktop-first policy.
        final Task homeTask = createHomeTask(display);
        homeTask.setVisibleRequested(true);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        // Override task bounds with bounds larger than display in at least on dimension.
        final Rect overrideTaskBounds = new Rect(0, 0, 100, 10000);
        task.setBounds(overrideTaskBounds);

        // Task bounds should not be respected.
        new CalculateRequestBuilder().setTask(task).calculate();
        assertNotEquals(overrideTaskBounds, mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testUsesDesiredBoundsIfEmptyLayoutAndActivityOptionsBounds() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final int desiredWidth =
                (int) (DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight =
                (int) (DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testDefaultLandscapeBounds_landscapeDevice_resizable_undefinedOrientation() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_UNSPECIFIED,
                task, /* ignoreOrientationRequest */ true);

        final int desiredWidth =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testDefaultLandscapeBounds_landscapeDevice_resizable_landscapeOrientation() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_LANDSCAPE,
                task, /* ignoreOrientationRequest */ true);

        final int desiredWidth =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testDefaultLandscapeBounds_landscapeDevice_userFullscreenOverride() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_LANDSCAPE,
                task, /* ignoreOrientationRequest */ true);

        spyOn(mActivity.mAppCompatController.getAspectRatioOverrides());
        doReturn(true).when(
                        mActivity.mAppCompatController.getAspectRatioOverrides())
                .hasFullscreenOverride();

        final int desiredWidth =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testDefaultLandscapeBounds_landscapeDevice_systemFullscreenOverride() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_LANDSCAPE,
                task, /* ignoreOrientationRequest */ true);

        spyOn(mActivity.mAppCompatController.getAspectRatioOverrides());
        doReturn(true).when(
                        mActivity.mAppCompatController.getAspectRatioOverrides())
                .hasFullscreenOverride();

        final int desiredWidth =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    public void testResizablePortraitBounds_landscapeDevice_resizable_portraitOrientation() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ true);

        spyOn(activity.mAppCompatController.getDesktopAspectRatioPolicy());
        doReturn(LETTERBOX_ASPECT_RATIO).when(activity.mAppCompatController
                .getDesktopAspectRatioPolicy()).calculateAspectRatio(any(), anyBoolean());

        final int desiredWidth =
                (int) ((LANDSCAPE_DISPLAY_BOUNDS.height() / LETTERBOX_ASPECT_RATIO) + 0.5f);
        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_SMALL})
    public void testSmallAspectRatioOverride_landscapeDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ false);

        // Mock desired aspect ratio so min override can take effect.
        setDesiredAspectRatio(activity, /* aspectRatio */ 1f);

        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth =
                (int) (desiredHeight / OVERRIDE_MIN_ASPECT_RATIO_SMALL_VALUE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM})
    public void testMediumAspectRatioOverride_landscapeDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ false);

        // Mock desired aspect ratio so min override can take effect.
        setDesiredAspectRatio(activity, /* aspectRatio */ 1f);

        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth =
                (int) (desiredHeight / OVERRIDE_MIN_ASPECT_RATIO_MEDIUM_VALUE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testLargeAspectRatioOverride_landscapeDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ false);

        // Mock desired aspect ratio so min override can take effect.
        setDesiredAspectRatio(activity, /* aspectRatio */ 1f);

        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth =
                (int) (desiredHeight / OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_TO_ALIGN_WITH_SPLIT_SCREEN})
    public void testSplitScreenAspectRatioOverride_landscapeDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ false);

        // Mock desired aspect ratio so min override can take effect.
        setDesiredAspectRatio(activity, /* aspectRatio */ 1f);

        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth =
                (int) ((desiredHeight / activity.mAppCompatController
                        .getAspectRatioOverrides().getSplitScreenAspectRatio()) + 0.5f);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_SMALL})
    public void testSmallAspectRatioOverride_portraitDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ false);
        // Mock desired aspect ratio so min override can take effect.
        setDesiredAspectRatio(activity, /* aspectRatio */ 1f);

        final int desiredWidth =
                (int) (PORTRAIT_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight = (int) (desiredWidth * OVERRIDE_MIN_ASPECT_RATIO_SMALL_VALUE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM})
    public void testMediumAspectRatioOverride_portraitDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ false);
        // Mock desired aspect ratio so min override can take effect.
        setDesiredAspectRatio(activity, /* aspectRatio */ 1f);

        final int desiredWidth =
                (int) (PORTRAIT_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight = (int) (desiredWidth * OVERRIDE_MIN_ASPECT_RATIO_MEDIUM_VALUE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testLargeAspectRatioOverride_portraitDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ false);
        // Mock desired aspect ratio so min override can take effect.
        setDesiredAspectRatio(activity, /* aspectRatio */ 1f);

        final int desiredHeight =
                (int) (PORTRAIT_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth =
                (int) ((desiredHeight / OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE) + 0.5f);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_TO_ALIGN_WITH_SPLIT_SCREEN})
    public void testSplitScreenAspectRatioOverride_portraitDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ false);
        // Mock desired aspect ratio so min override can take effect.
        setDesiredAspectRatio(activity, /* aspectRatio */ 1f);

        final int desiredWidth =
                (int) (PORTRAIT_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight = (int) (desiredWidth * activity.mAppCompatController
                .getAspectRatioOverrides().getSplitScreenAspectRatio());

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    public void testUserAspectRatio32Override_landscapeDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_UNSPECIFIED,
                task, /* ignoreOrientationRequest */ true);
        final float userAspectRatioOverrideValue3_2 = 3 / 2f;
        applyUserMinAspectRatioOverride(activity, USER_MIN_ASPECT_RATIO_3_2,
                userAspectRatioOverrideValue3_2);

        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth = (int) (desiredHeight / userAspectRatioOverrideValue3_2);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    public void testUserAspectRatio43Override_landscapeDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_UNSPECIFIED,
                task, /* ignoreOrientationRequest */ true);
        final float userAspectRatioOverrideValue4_3 = 4 / 3f;
        applyUserMinAspectRatioOverride(activity, USER_MIN_ASPECT_RATIO_4_3,
                userAspectRatioOverrideValue4_3);

        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth = (int) (desiredHeight / userAspectRatioOverrideValue4_3);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    public void testUserAspectRatio169Override_landscapeDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_UNSPECIFIED,
                task, /* ignoreOrientationRequest */ true);
        final float userAspectRatioOverrideValue16_9 = 16 / 9f;
        applyUserMinAspectRatioOverride(activity, USER_MIN_ASPECT_RATIO_16_9,
                userAspectRatioOverrideValue16_9);

        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth = (int) (desiredHeight / userAspectRatioOverrideValue16_9);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    public void testUserAspectRatioSplitScreenOverride_landscapeDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_UNSPECIFIED,
                task, /* ignoreOrientationRequest */ true);
        final float userAspectRatioOverrideValueSplitScreen = activity.mAppCompatController
                .getAspectRatioOverrides().getSplitScreenAspectRatio();
        applyUserMinAspectRatioOverride(activity, USER_MIN_ASPECT_RATIO_SPLIT_SCREEN,
                userAspectRatioOverrideValueSplitScreen);

        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth =
                (int) ((desiredHeight / userAspectRatioOverrideValueSplitScreen) + 0.5f);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    public void testUserAspectRatioDisplaySizeOverride_landscapeDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_UNSPECIFIED,
                task, /* ignoreOrientationRequest */ true);
        final float userAspectRatioOverrideValueDisplaySize = activity.mAppCompatController
                .getAspectRatioOverrides().getDisplaySizeMinAspectRatio();
        applyUserMinAspectRatioOverride(activity, USER_MIN_ASPECT_RATIO_DISPLAY_SIZE,
                userAspectRatioOverrideValueDisplaySize);

        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth = (int) (desiredHeight / userAspectRatioOverrideValueDisplaySize);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    public void testUserAspectRatio32Override_portraitDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_UNSPECIFIED,
                task, /* ignoreOrientationRequest */ true);
        final float userAspectRatioOverrideValue3_2 = 3 / 2f;
        applyUserMinAspectRatioOverride(activity, USER_MIN_ASPECT_RATIO_3_2,
                userAspectRatioOverrideValue3_2);

        final int desiredWidth =
                (int) (PORTRAIT_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight = (int) (desiredWidth * userAspectRatioOverrideValue3_2);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    public void testUserAspectRatio43Override_portraitDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_UNSPECIFIED,
                task, /* ignoreOrientationRequest */ true);
        final float userAspectRatioOverrideValue4_3 = 4 / 3f;
        applyUserMinAspectRatioOverride(activity, USER_MIN_ASPECT_RATIO_4_3,
                userAspectRatioOverrideValue4_3);

        final int desiredWidth =
                (int) (PORTRAIT_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight = (int) (desiredWidth * userAspectRatioOverrideValue4_3);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    public void testUserAspectRatio169Override_portraitDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_UNSPECIFIED,
                task, /* ignoreOrientationRequest */ true);
        final float userAspectRatioOverrideValue16_9 = 16 / 9f;
        applyUserMinAspectRatioOverride(activity, USER_MIN_ASPECT_RATIO_16_9,
                userAspectRatioOverrideValue16_9);

        final int desiredHeight =
                (int) (PORTRAIT_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth =
                (int) ((desiredHeight / userAspectRatioOverrideValue16_9) + 0.5f);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    public void testUserAspectRatioSplitScreenOverride_portraitDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_UNSPECIFIED,
                task, /* ignoreOrientationRequest */ true);
        final float userAspectRatioOverrideValueSplitScreen = activity.mAppCompatController
                .getAspectRatioOverrides().getSplitScreenAspectRatio();
        applyUserMinAspectRatioOverride(activity, USER_MIN_ASPECT_RATIO_SPLIT_SCREEN,
                userAspectRatioOverrideValueSplitScreen);

        final int desiredWidth =
                (int) (PORTRAIT_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight = (int) (desiredWidth * userAspectRatioOverrideValueSplitScreen);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    public void testUserAspectRatioDisplaySizeOverride_portraitDevice() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_UNSPECIFIED,
                task, /* ignoreOrientationRequest */ true);
        final float userAspectRatioOverrideValueDisplaySize = activity.mAppCompatController
                .getAspectRatioOverrides().getDisplaySizeMinAspectRatio();
        applyUserMinAspectRatioOverride(activity, USER_MIN_ASPECT_RATIO_DISPLAY_SIZE,
                userAspectRatioOverrideValueDisplaySize);

        final int desiredWidth =
                (int) (PORTRAIT_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight = (int) (desiredWidth * userAspectRatioOverrideValueDisplaySize);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @DisableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    public void testDefaultLandscapeBounds_landscapeDevice_unResizable_landscapeOrientation() {
        setupDesktopModeLaunchParamsModifier();
        final int captionHeight = getDesktopViewAppHeaderHeightPx(mContext);

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ false);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_LANDSCAPE,
                task, /* ignoreOrientationRequest */ true);

        final float displayAspectRatio = (float) LANDSCAPE_DISPLAY_BOUNDS.width()
                / LANDSCAPE_DISPLAY_BOUNDS.height();
        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth = (int) ((desiredHeight - captionHeight) * displayAspectRatio);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
        assertEquals(desiredHeight - captionHeight, mResult.mAppBounds.height());
    }

    @Test
    @DisableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    public void testUnResizablePortraitBounds_landscapeDevice_unResizable_portraitOrientation() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ false);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ true);
        final int captionHeight = getDesktopViewAppHeaderHeightPx(mContext);

        spyOn(activity.mAppCompatController.getDesktopAspectRatioPolicy());
        doReturn(LETTERBOX_ASPECT_RATIO).when(activity.mAppCompatController
                .getDesktopAspectRatioPolicy()).calculateAspectRatio(any(), anyBoolean());

        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth = (int) ((desiredHeight - captionHeight) / LETTERBOX_ASPECT_RATIO);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
        assertEquals(desiredHeight - captionHeight, mResult.mAppBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testDefaultPortraitBounds_portraitDevice_resizable_undefinedOrientation() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_UNSPECIFIED,
                task, /* ignoreOrientationRequest */ true);

        final int desiredWidth =
                (int) (PORTRAIT_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight =
                (int) (PORTRAIT_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testDefaultPortraitBounds_portraitDevice_resizable_portraitOrientation() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ true);

        final int desiredWidth =
                (int) (PORTRAIT_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight =
                (int) (PORTRAIT_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testDefaultPortraitBounds_portraitDevice_userFullscreenOverride() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ true);

        spyOn(activity.mAppCompatController.getAspectRatioOverrides());
        doReturn(true).when(
                        activity.mAppCompatController.getAspectRatioOverrides())
                .isUserFullscreenOverrideEnabled();

        final int desiredWidth =
                (int) (PORTRAIT_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight =
                (int) (PORTRAIT_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testDefaultPortraitBounds_portraitDevice_systemFullscreenOverride() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ true);

        spyOn(activity.mAppCompatController.getAspectRatioOverrides());
        doReturn(true).when(
                        activity.mAppCompatController.getAspectRatioOverrides())
                .isSystemOverrideToFullscreenEnabled();

        final int desiredWidth =
                (int) (PORTRAIT_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight =
                (int) (PORTRAIT_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    public void testResizableLandscapeBounds_portraitDevice_resizable_landscapeOrientation() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_LANDSCAPE,
                task, /* ignoreOrientationRequest */ true);

        spyOn(activity.mAppCompatController.getDesktopAspectRatioPolicy());
        doReturn(LETTERBOX_ASPECT_RATIO).when(activity.mAppCompatController
                .getDesktopAspectRatioPolicy()).calculateAspectRatio(any(), anyBoolean());

        final int desiredWidth = PORTRAIT_DISPLAY_BOUNDS.width()
                - (DESKTOP_MODE_LANDSCAPE_APP_PADDING * 2);
        final int desiredHeight = (int)
                ((PORTRAIT_DISPLAY_BOUNDS.width() / LETTERBOX_ASPECT_RATIO) + 0.5f);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @DisableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    public void testDefaultPortraitBounds_portraitDevice_unResizable_portraitOrientation() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ false);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_PORTRAIT,
                task, /* ignoreOrientationRequest */ true);
        final int captionHeight = getDesktopViewAppHeaderHeightPx(mContext);

        final float displayAspectRatio = (float) PORTRAIT_DISPLAY_BOUNDS.height()
                / PORTRAIT_DISPLAY_BOUNDS.width();
        final int desiredHeight =
                (int) (PORTRAIT_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredWidth =
                (int) (((desiredHeight - captionHeight) / displayAspectRatio) + 0.5f);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
        assertEquals(desiredHeight - captionHeight, mResult.mAppBounds.height());
    }

    @Test
    @DisableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    public void testUnResizableLandscapeBounds_portraitDevice_unResizable_landscapeOrientation() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_PORTRAIT,
                PORTRAIT_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ false);
        final ActivityRecord activity = createActivity(display, SCREEN_ORIENTATION_LANDSCAPE,
                task, /* ignoreOrientationRequest */ true);
        final int captionHeight = getDesktopViewAppHeaderHeightPx(mContext);

        spyOn(activity.mAppCompatController.getDesktopAspectRatioPolicy());
        doReturn(LETTERBOX_ASPECT_RATIO).when(activity.mAppCompatController
                .getDesktopAspectRatioPolicy()).calculateAspectRatio(any(), anyBoolean());

        final int desiredWidth = PORTRAIT_DISPLAY_BOUNDS.width()
                - (DESKTOP_MODE_LANDSCAPE_APP_PADDING * 2);
        final int desiredHeight = (int) (desiredWidth / LETTERBOX_ASPECT_RATIO) + captionHeight;

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(activity).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
        assertEquals(desiredHeight - captionHeight, mResult.mAppBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNullActivity_defaultBoundsApplied() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS);
        final Task task = createStandardTask(display, /* isResizeable */ true);

        final int desiredWidth =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int desiredHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setActivity(null).calculate());
        assertEquals(desiredWidth, mResult.mBounds.width());
        assertEquals(desiredHeight, mResult.mBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyActivityOptionsBounds() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityOptions options = ActivityOptions.makeBasic()
                .setLaunchBounds(new Rect(0, 0, DISPLAY_BOUNDS.width(), DISPLAY_BOUNDS.height()));

        assertEquals(RESULT_CONTINUE,
                new CalculateRequestBuilder().setTask(task).setOptions(options).calculate());
        assertTrue(mResult.mBoundsSetFromOptions);
        assertEquals(DISPLAY_BOUNDS.width(), mResult.mBounds.width());
        assertEquals(DISPLAY_BOUNDS.height(), mResult.mBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testOptionsBoundsSet_flexibleLaunchSize_windowingModeSet() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityOptions options = ActivityOptions.makeBasic()
                .setLaunchBounds(new Rect(
                        DISPLAY_STABLE_BOUNDS.left,
                        DISPLAY_STABLE_BOUNDS.top,
                        /* right = */ 500,
                        /* bottom = */ 500))
                .setFlexibleLaunchSize(true);
        options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);

        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(task).setOptions(options).calculate());
        assertEquals(WINDOWING_MODE_FREEFORM, mResult.mWindowingMode);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testOptionsBoundsSet_flexibleLaunchSizeWithFullscreenOverride_noModifications() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityOptions options = ActivityOptions.makeBasic()
                .setLaunchBounds(new Rect(
                        DISPLAY_STABLE_BOUNDS.left,
                        DISPLAY_STABLE_BOUNDS.top,
                        /* right = */ 500,
                        /* bottom = */ 500))
                .setFlexibleLaunchSize(true);
        spyOn(mActivity.mAppCompatController.getAspectRatioOverrides());
        doReturn(true).when(
                        mActivity.mAppCompatController.getAspectRatioOverrides())
                .hasFullscreenOverride();

        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(task).setOptions(options).calculate());
        assertEquals(options.getLaunchBounds(), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testOptionsBoundsSet_flexibleLaunchSize_boundsSizeModified() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityOptions options = ActivityOptions.makeBasic()
                .setLaunchBounds(new Rect(
                        DISPLAY_STABLE_BOUNDS.left,
                        DISPLAY_STABLE_BOUNDS.top,
                        /* right = */ 500,
                        /* bottom = */ 500))
                .setFlexibleLaunchSize(true);
        final int modifiedWidth =
                (int) (DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int modifiedHeight =
                (int) (DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);

        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(task).setOptions(options).calculate());
        assertEquals(modifiedWidth, mResult.mBounds.width());
        assertEquals(modifiedHeight, mResult.mBounds.height());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @DisableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    public void testOptionsBoundsSet_flexibleLaunchSizeWithCascading_cornerCascadeRespected() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        // Set launch bounds with corner cascade.
        final ActivityOptions options = ActivityOptions.makeBasic()
                .setLaunchBounds(new Rect(
                        DISPLAY_STABLE_BOUNDS.left,
                        DISPLAY_STABLE_BOUNDS.top,
                        /* right = */ 500,
                        /* bottom = */ 500))
                .setFlexibleLaunchSize(true);

        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(task).setOptions(options).calculate());
        assertEquals(DISPLAY_STABLE_BOUNDS.left, mResult.mBounds.left);
        assertEquals(DISPLAY_STABLE_BOUNDS.top, mResult.mBounds.top);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testOptionsBoundsSet_flexibleLaunchSize_centerCascadeRespected() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        // Set launch bounds with center cascade.
        final ActivityOptions options = ActivityOptions.makeBasic()
                .setLaunchBounds(new Rect(
                        /* left = */ 320,
                        /* top = */ 100,
                        /* right = */ 640,
                        /* bottom = */ 200))
                .setFlexibleLaunchSize(true);
        final int modifiedWidth =
                (int) (DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int modifiedHeight =
                (int) (DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final Rect centerCascadedBounds = centerInScreen(
                new Size(modifiedWidth, modifiedHeight), DISPLAY_STABLE_BOUNDS);

        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(task).setOptions(options).calculate());
        assertEquals(centerCascadedBounds, mResult.mBounds);
        assertEquals(centerCascadedBounds.top, mResult.mBounds.top);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBounds_CenterToDisplay() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder().setWidth(120)
                .setHeight(80).build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(layout)
                .calculate());
        assertEquals(new Rect(800, 400, 920, 480), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBounds_LeftGravity() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder().setWidth(120)
                .setHeight(80).setGravity(Gravity.LEFT).build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(layout)
                .calculate());
        assertEquals(new Rect(100, 400, 220, 480), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBounds_TopGravity() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder().setWidth(120)
                .setHeight(80).setGravity(Gravity.TOP).build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(layout)
                .calculate());
        assertEquals(new Rect(800, 200, 920, 280), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBounds_TopLeftGravity() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder().setWidth(120)
                .setHeight(80).setGravity(Gravity.TOP | Gravity.LEFT).build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(layout)
                .calculate());
        assertEquals(new Rect(100, 200, 220, 280), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBounds_RightGravity() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder().setWidth(120)
                .setHeight(80).setGravity(Gravity.RIGHT).build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(layout)
                .calculate());
        assertEquals(new Rect(1500, 400, 1620, 480), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBounds_BottomGravity() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder()
                .setWidth(120).setHeight(80).setGravity(Gravity.BOTTOM).build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(layout)
                .calculate());
        assertEquals(new Rect(800, 600, 920, 680), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBounds_RightBottomGravity() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder()
                .setWidth(120).setHeight(80).setGravity(Gravity.BOTTOM | Gravity.RIGHT).build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(layout)
                .calculate());
        assertEquals(new Rect(1500, 600, 1620, 680), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutFractionBounds() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder()
                .setWidthFraction(0.125f).setHeightFraction(0.1f).build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task)
                .setLayout(layout).calculate());
        assertEquals(new Rect(765, 416, 955, 464), mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBoundsRespectsGravityWithEmptySize_LeftGravity() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder()
                .setGravity(Gravity.LEFT).build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(
                layout).calculate());
        assertEquals(DISPLAY_STABLE_BOUNDS.left, mResult.mBounds.left);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBoundsRespectsGravityWithEmptySize_TopGravity() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder().setGravity(Gravity.TOP)
                .build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(
                layout).calculate());
        assertEquals(DISPLAY_STABLE_BOUNDS.top, mResult.mBounds.top);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBoundsRespectsGravityWithEmptySize_TopLeftGravity() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);

        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder()
                .setGravity(Gravity.TOP | Gravity.LEFT).build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(
                layout).calculate());
        assertEquals(DISPLAY_STABLE_BOUNDS.left, mResult.mBounds.left);
        assertEquals(DISPLAY_STABLE_BOUNDS.top, mResult.mBounds.top);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBoundsRespectsGravityWithEmptySize_RightGravity() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder().setGravity(Gravity.RIGHT)
                .build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(
                layout).calculate());
        assertEquals(DISPLAY_STABLE_BOUNDS.right, mResult.mBounds.right);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBoundsRespectsGravityWithEmptySize_BottomGravity() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);
        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder()
                .setGravity(Gravity.BOTTOM).build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(
                layout).calculate());
        assertEquals(DISPLAY_STABLE_BOUNDS.bottom, mResult.mBounds.bottom);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testNonEmptyLayoutBoundsRespectsGravityWithEmptySize_BottomRightGravity() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent display = createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final Task task = createStandardTask(display);

        final ActivityInfo.WindowLayout layout = new WindowLayoutBuilder()
                .setGravity(Gravity.BOTTOM | Gravity.RIGHT).build();

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).setLayout(
                layout).calculate());
        assertEquals(DISPLAY_STABLE_BOUNDS.right, mResult.mBounds.right);
        assertEquals(DISPLAY_STABLE_BOUNDS.bottom, mResult.mBounds.bottom);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE, Flags.FLAG_ENABLE_STEPPED_CASCADING})
    public void testOptionsBoundsSet_steppedCascading() {
        setupDesktopModeLaunchParamsModifier();
        doReturn(true).when(mTarget).isDesktopModeSupportedOnDisplay(any());
        doReturn(true).when(mTarget).isEnteringDesktopMode(any(), any(), any(), any());

        final TestDisplayContent display = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);

        // Create a root task that will contain both existing and launching tasks
        final Task rootTask = createDesktopRootTask(display);

        // Create an existing task that matches the centered bounds
        final int modifiedWidth =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final int modifiedHeight =
                (int) (LANDSCAPE_DISPLAY_BOUNDS.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        final Rect centeredBounds = centerInScreen(
                new Size(modifiedWidth, modifiedHeight), LANDSCAPE_DISPLAY_BOUNDS);

        final Task existingTask = createChildTask(rootTask, WINDOWING_MODE_FREEFORM);
        existingTask.setBounds(centeredBounds);
        existingTask.getTopMostActivity().setVisibleRequested(true);
        existingTask.getTopMostActivity().setVisible(true);

        // New task to be launched
        final Task launchingTask = createChildTask(rootTask, WINDOWING_MODE_FREEFORM);

        // ActivityOptions with flexibleLaunchSize. Set some inappropriate launch bounds so that
        // they will be adjusted in Core and we can verify cascading is applied to the adjusted
        // bounds in this case.
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setFlexibleLaunchSize(true);
        options.setLaunchBounds(new Rect(0, 0, 100, 100));
        options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);

        assertEquals(RESULT_DONE, new CalculateRequestBuilder()
                .setTask(launchingTask)
                .setActivity(launchingTask.getTopMostActivity())
                .setOptions(options)
                .calculate());

        // The result should be cascaded from existingTask.getBounds()
        final int offset = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.desktop_mode_cascading_offset);
        final Rect expectedBounds = new Rect(centeredBounds);
        expectedBounds.offset(offset, offset);

        assertEquals(expectedBounds, mResult.mBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_PRIORITIZE_VISIBLE_TASK_DISPLAY_OVER_SOURCE)
    public void test_taskLaunchFromSource_visibleTask_tasksDisplayAreaPrioritized() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent primaryDisplay =
                createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final TestDisplayContent secondaryDisplay =
                createNewDisplayContent(WINDOWING_MODE_FREEFORM);
        final Task launchingTask = createStandardTask(primaryDisplay);
        final Task sourceTask = createStandardTaskWithActivity(secondaryDisplay);

        // Mark task as already visible.
        launchingTask.setVisibleRequested(true);

        new CalculateRequestBuilder().setTask(launchingTask)
                .setSource(sourceTask.getRootActivity()).calculate();
        assertNotEquals(sourceTask.getTaskDisplayArea(), mResult.mPreferredTaskDisplayArea);
        assertEquals(launchingTask.getTaskDisplayArea(), mResult.mPreferredTaskDisplayArea);

    }

    @Test
    @EnableFlags(Flags.FLAG_PRIORITIZE_VISIBLE_TASK_DISPLAY_OVER_SOURCE)
    public void test_taskLaunchFromSource_notVisibleTask_sourceDisplayAreaPrioritized() {
        setupDesktopModeLaunchParamsModifier();

        final TestDisplayContent primaryDisplay =
                createNewDisplayContent(WINDOWING_MODE_FULLSCREEN);
        final TestDisplayContent secondaryDisplay =
                createNewDisplayContent(WINDOWING_MODE_FREEFORM);
        final Task launchingTask = createStandardTask(primaryDisplay);
        final Task sourceTask = createStandardTaskWithActivity(secondaryDisplay);

        // Mark task not already visible.
        launchingTask.setVisibleRequested(false);

        new CalculateRequestBuilder().setTask(launchingTask)
                .setSource(sourceTask.getRootActivity()).calculate();
        assertNotEquals(launchingTask.getTaskDisplayArea(), mResult.mPreferredTaskDisplayArea);
        assertEquals(sourceTask.getTaskDisplayArea(), mResult.mPreferredTaskDisplayArea);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testDoesntInheritWindowingModeFromCurrentParams() {
        setupDesktopModeLaunchParamsModifier();
        doCallRealMethod().when(mTarget).isEnteringDesktopMode(any(), any(), any(), any());

        final Task task = createStandardTask();
        final TaskDisplayArea currTaskDisplayArea = mock(TaskDisplayArea.class);
        mCurrent.mPreferredTaskDisplayArea = currTaskDisplayArea;
        mCurrent.mWindowingMode = WINDOWING_MODE_FREEFORM;

        assertEquals(RESULT_CONTINUE, new CalculateRequestBuilder().setTask(task).calculate());
        assertEquals(task.getRootTask().getDisplayArea(), mResult.mPreferredTaskDisplayArea);
        assertNotEquals(currTaskDisplayArea, mResult.mPreferredTaskDisplayArea);
        assertEquals(WINDOWING_MODE_UNDEFINED, mResult.mWindowingMode);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testFreeformWindowingModeAppliedIfSourceTaskExists() {
        setupDesktopModeLaunchParamsModifier();

        final Task task = createStandardTask();
        // edit below
        final Task sourceTask = createFullscreenTask(null);
        final ActivityRecord sourceActivity = new ActivityBuilder(task.mAtmService)
                .setTask(sourceTask).build();

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(task)
                .setSource(sourceActivity).calculate());
        assertEquals(WINDOWING_MODE_FREEFORM, mResult.mWindowingMode);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void testInMultiDesk_requestFullscreen_returnDone() {
        setupDesktopModeLaunchParamsModifier();

        final Task deskRoot = createDesktopRootTask(null);
        final Task sourceTask = createFullscreenTask(null);
        // Creating a fullscreen task under the desk root.
        final Task task = new TaskBuilder(mSupervisor)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .setUserId(TEST_USER_ID)
                .setParentTask(deskRoot).build();

        final ActivityRecord sourceActivity = new ActivityBuilder(task.mAtmService)
                .setTask(sourceTask).build();

        assertEquals(RESULT_DONE, new CalculateRequestBuilder().setTask(task)
                .setSource(sourceActivity).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
        final Rect emptyRect = new Rect();
        assertEquals(emptyRect, mResult.mBounds);
        assertEquals(emptyRect, mResult.mAppBounds);
        assertTrue(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM)
    public void testCalculate_desktopFirstPolicy_forcesFreeform() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();

        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);
        // Make home visible to trigger desktop-first policy.
        final Task homeTask = createHomeTask(dc);
        homeTask.setVisibleRequested(true);
        final Task launchingTask = createStandardTask(dc, WINDOWING_MODE_FULLSCREEN);
        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(launchingTask).calculate());
        assertEquals(WINDOWING_MODE_FREEFORM, mResult.mWindowingMode);
        assertFalse(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX)
    public void testCalculate_desktopFirstPolicy_taskNull_forcesFreeform() {
        setupDesktopModeLaunchParamsModifier();

        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(dc.getDisplayId());

        // When task is null, getPreferredLaunchTaskDisplayArea will use the display from options.
        // Then forceFreeformByDesktopFirstPolicy will be true.
        // Because task is null, it should return RESULT_DONE.
        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(null).setOptions(options).calculate());
        assertEquals(WINDOWING_MODE_FREEFORM, mResult.mWindowingMode);
        assertFalse(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX})
    public void testCalculate_desktopFirstPolicy_taskNull_activeDeskInvisibleHome_forceFreeform() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();
        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);
        final Task deskRoot = createDesktopRootTask(dc);

        // Activate a desk.
        dc.getDefaultTaskDisplayArea().setLaunchRootTask(deskRoot,
                new int[]{WINDOWING_MODE_FREEFORM}, new int[]{ACTIVITY_TYPE_STANDARD});

        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(null).setOptions(
                        ActivityOptions.makeBasic().setLaunchDisplayId(
                                dc.getDisplayId())).calculate());
        assertEquals(WINDOWING_MODE_FREEFORM, mResult.mWindowingMode);
        assertFalse(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX})
    public void testCalculate_desktopFirstPolicy_taskNull_activeDeskVisibleHome_forceFreeform() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();
        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);
        final Task deskRoot = createDesktopRootTask(dc);
        final Task homeTask = createHomeTask(dc);
        homeTask.setVisibleRequested(true);

        // Activate a desk.
        dc.getDefaultTaskDisplayArea().setLaunchRootTask(deskRoot,
                new int[]{WINDOWING_MODE_FREEFORM}, new int[]{ACTIVITY_TYPE_STANDARD});

        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(null).setOptions(
                        ActivityOptions.makeBasic().setLaunchDisplayId(
                                dc.getDisplayId())).calculate());
        assertEquals(WINDOWING_MODE_FREEFORM, mResult.mWindowingMode);
        assertFalse(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX})
    public void testCalculate_desktopFirstPolicy_taskNull_inactiveDeskVisibleHome_forceFreeform() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();
        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);
        final Task homeTask = createHomeTask(dc);
        homeTask.setVisibleRequested(true);

        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(null).setOptions(
                        ActivityOptions.makeBasic().setLaunchDisplayId(
                                dc.getDisplayId())).calculate());
        assertEquals(WINDOWING_MODE_FREEFORM, mResult.mWindowingMode);
        assertFalse(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX})
    public void testCalculate_desktopFirstPolicy_taskNull_inactiveDeskInvisibleHome_fullscreen() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();
        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);

        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(null).setOptions(
                        ActivityOptions.makeBasic().setLaunchDisplayId(
                                dc.getDisplayId())).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
        assertTrue(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM)
    public void testCalculate_desktopFirstPolicy_fullscreenRelaunch_bypassesPolicy() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();

        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);
        final Task launchingTask = createFullscreenTask(dc);
        final ActivityRecord source = new ActivityBuilder(mAtm).setTask(launchingTask).build();

        // The task is launched by a different task on a desktop-first display.
        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(launchingTask).setSource(source).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
        assertTrue(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX)
    public void testCalculate_desktopFirstPolicy_fullscreenSourceTask_forcesFreeform() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();

        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);
        final Task launchingTask = createFullscreenTask(dc);
        final Task sourceTask = createStandardTask(dc, WINDOWING_MODE_FREEFORM);
        final ActivityRecord source = new ActivityBuilder(mAtm).setTask(sourceTask).build();

        // The task is launched by a different task on a desktop-first display.
        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(launchingTask).setSource(source).calculate());
        assertEquals(WINDOWING_MODE_FREEFORM, mResult.mWindowingMode);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM)
    public void testCalculate_desktopFirstPolicy_taskNull_requestFullscreen_bypassesPolicy() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();

        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(dc.getDisplayId());
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);

        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(null).setOptions(options).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
        assertTrue(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM)
    public void testCalculate_desktopFirstPolicy_requestFullscreen_bypassesPolicy() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();

        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);
        final Task launchingTask = createFullscreenTask(dc);
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);

        // The policy should be bypassed if fullscreen is requested.
        assertEquals(RESULT_DONE,
                new CalculateRequestBuilder().setTask(launchingTask).setOptions(
                        options).calculate());
        assertEquals(WINDOWING_MODE_FULLSCREEN, mResult.mWindowingMode);
        assertTrue(mResult.mIsTaskMoveDisallowed);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM)
    public void testCalculate_desktopFirstPolicy_clearCurrentParams() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();

        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);
        final Task launchingTask = createStandardTask(dc, WINDOWING_MODE_MULTI_WINDOW);
        mCurrent.mWindowingMode = WINDOWING_MODE_FREEFORM;

        // The current mode should be cleared.
        assertEquals(RESULT_CONTINUE,
                new CalculateRequestBuilder().setTask(launchingTask).calculate());
        assertEquals(WINDOWING_MODE_UNDEFINED, mResult.mWindowingMode);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_MULTIPLE_LAUNCH_ROOT_BUGFIX})
    public void testCalculate_desktopFirstPolicy_taskNull_multipleLaunchRoots_bypassesPolicy() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();
        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);
        final Task deskRoot = createDesktopRootTask(dc);
        final Task homeTask = createHomeTask(dc);
        homeTask.setVisibleRequested(true);
        // Add a second launch root for WINDOWING_MODE_UNDEFINED.
        // This should cause the policy to be bypassed and the task to launch in UNDEFINED.
        final Task undefinedRoot = createRootTask(dc, WINDOWING_MODE_UNDEFINED);

        dc.getDefaultTaskDisplayArea().setLaunchRootTask(deskRoot,
                new int[]{WINDOWING_MODE_FREEFORM}, new int[]{ACTIVITY_TYPE_STANDARD});
        dc.getDefaultTaskDisplayArea().setLaunchRootTask(undefinedRoot,
                new int[]{WINDOWING_MODE_UNDEFINED}, new int[]{ACTIVITY_TYPE_STANDARD});

        assertEquals(RESULT_SKIP,
                new CalculateRequestBuilder().setTask(null).setOptions(
                        ActivityOptions.makeBasic().setLaunchDisplayId(
                                dc.getDisplayId())).calculate());
        assertEquals(WINDOWING_MODE_UNDEFINED, mResult.mWindowingMode);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_MULTIPLE_LAUNCH_ROOT_BUGFIX})
    public void testCalculate_desktopFirstPolicy_multipleLaunchRoots_bypassesPolicy() {
        setupDesktopModeLaunchParamsModifier();
        when(mTarget.isEnteringDesktopMode(any(), any(), any(), any())).thenCallRealMethod();

        final DisplayContent dc = createDisplayContent(ORIENTATION_LANDSCAPE,
                LANDSCAPE_DISPLAY_BOUNDS, WINDOWING_MODE_FREEFORM);
        final TaskDisplayArea tda = dc.getDefaultTaskDisplayArea();

        // Create a freeform launch root.
        final Task freeformRoot = createDesktopRootTask(dc);
        tda.setLaunchRootTask(freeformRoot,
                new int[]{WINDOWING_MODE_FREEFORM}, new int[]{ACTIVITY_TYPE_STANDARD});

        // Create an undefined launch root.
        final Task undefinedRoot = createRootTask(dc, WINDOWING_MODE_UNDEFINED);
        tda.setLaunchRootTask(undefinedRoot,
                new int[]{WINDOWING_MODE_UNDEFINED}, new int[]{ACTIVITY_TYPE_STANDARD});

        final Task launchingTask = createStandardTask(dc);

        // If multiple launch roots are detected, it should return WINDOWING_MODE_UNDEFINED.
        assertEquals(RESULT_CONTINUE,
                new CalculateRequestBuilder().setTask(launchingTask).calculate());
        assertEquals(WINDOWING_MODE_UNDEFINED, mResult.mWindowingMode);
    }

    private Task createTask(DisplayContent display, int windowingMode, int activityType,
            boolean isResizeable, String packageName, int userId, boolean createdByOrganizer,
            boolean createActivity, Task parent) {
        final TaskBuilder builder = new TaskBuilder(mSupervisor)
                .setActivityType(activityType)
                .setWindowingMode(windowingMode)
                .setUserId(userId)
                .setPackage(packageName)
                .setCreateActivity(createActivity)
                .setCreatedByOrganizer(createdByOrganizer)
                .setParentTask(parent);

        if (display != null) {
            builder.setDisplay(display);
        }

        final Task task = builder.build();
        task.setResizeMode(isResizeable ? RESIZE_MODE_RESIZEABLE : RESIZE_MODE_UNRESIZEABLE);
        return task;
    }

    private Task createStandardTask() {
        return createStandardTask(/* display */ null);
    }

    private Task createStandardTask(DisplayContent display) {
        return createStandardTask(display, /* isResizable */ true);
    }

    private Task createStandardTask(int activityType) {
        return createTask(/* display */ null, WINDOWING_MODE_UNDEFINED, activityType,
                /* isResizeable */ true, /* packageName */ null, TEST_USER_ID,
                /* createdByOrganizer */ false, /* createActivity */ false, /* parent */ null);
    }

    private Task createStandardTask(DisplayContent display, boolean isResizable) {
        return createTask(display, WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD,
                isResizable, /* packageName */ null, TEST_USER_ID,
                /* createdByOrganizer */ false, /* createActivity */ false, /* parent */ null);
    }

    private Task createStandardTask(DisplayContent display, int windowingMode) {
        return createTask(display, windowingMode, ACTIVITY_TYPE_STANDARD,
                /* isResizeable */ true, /* packageName */ null, TEST_USER_ID,
                /* createdByOrganizer */ false, /* createActivity */ true, /* parent */ null);
    }

    private Task createStandardTaskWithActivity(DisplayContent display) {
        return createTask(display, WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD,
                /* isResizable */ true, /* packageName */ null, TEST_USER_ID,
                /* createdByOrganizer */ false, /* createActivity */ true, /* parent */ null);
    }

    private Task createStandardTaskWithActivity(String packageName, int userId) {
        return createTask(/* display */ null, WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD,
                /* isResizable */ true, packageName, userId,
                /* createdByOrganizer */ false, /* createActivity */ true, /* parent */ null);
    }

    private Task createFreeformTask(boolean createActivity) {
        return createFreeformTask(createActivity, /* packageName */ null);
    }

    private Task createFreeformTask(boolean createActivity, String packageName) {
        return createTask(/* display */ null, WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD,
                /* isResizeable */ true, packageName, TEST_USER_ID,
                /* createdByOrganizer */ false, createActivity, /* parent */ null);
    }

    private Task createFullscreenTask(DisplayContent display) {
        return createTask(display, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                /* isResizeable */ true, /* packageName */ null, TEST_USER_ID,
                /* createdByOrganizer */ false, /* createActivity */ false, /* parent */ null);
    }

    private Task createChildTask(Task parent, int windowingMode) {
        return createTask(parent.getDisplayContent(), windowingMode, ACTIVITY_TYPE_STANDARD,
                /* isResizeable */ true, /* packageName */ null, TEST_USER_ID,
                /* createdByOrganizer */ false, /* createActivity */ true, parent);
    }

    private Task createDesktopRootTask(DisplayContent display) {
        return createRootTask(display, WINDOWING_MODE_FREEFORM);
    }

    private Task createRootTask(DisplayContent display, int windowingMode) {
        return createTask(display, windowingMode, ACTIVITY_TYPE_STANDARD,
                /* isResizeable */ true, /* packageName */ null, TEST_USER_ID,
                /* createdByOrganizer */ true, /* createActivity */ false, /* parent */ null);
    }

    private Task createHomeTask(DisplayContent display) {
        Task homeTask = createTask(display, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME,
                /* isResizeable */ true, /* packageName */ null, TEST_USER_ID,
                /* createdByOrganizer */ false, /* createActivity */ false, /* parent */ null);
        homeTask.setVisibleRequested(true);
        return homeTask;
    }


    @NonNull
    ActivityRecord createActivity(DisplayContent dc, int orientation, Task task,
            boolean ignoreOrientationRequest) {
        return createActivity(dc, orientation, task, ignoreOrientationRequest, true, null);
    }

    @NonNull
    private ActivityRecord createActivity(DisplayContent display, int orientation, Task task,
            boolean ignoreOrientationRequest, boolean isResizeable, ComponentName componentName) {
        final ComponentName component = componentName != null ? componentName :
                ComponentName.createRelative(task.mAtmService.mContext,
                        DesktopModeLaunchParamsModifierTests.class.getName());
        final int resizeMode = isResizeable ? RESIZE_MODE_RESIZEABLE
                : RESIZE_MODE_UNRESIZEABLE;
        final ActivityRecord activity = new ActivityBuilder(task.mAtmService)
                .setTask(task)
                .setComponent(component)
                .setScreenOrientation(orientation)
                .setResizeMode(resizeMode)
                .setOnTop(true).build();
        activity.onDisplayChanged(display);
        activity.setOccludesParent(true);
        activity.setVisible(true);
        activity.setVisibleRequested(true);
        activity.mDisplayContent.setIgnoreOrientationRequest(ignoreOrientationRequest);

        return activity;
    }

    private void setDesiredAspectRatio(ActivityRecord activity, float aspectRatio) {
        final DesktopAppCompatAspectRatioPolicy desktopAppCompatAspectRatioPolicy =
                activity.mAppCompatController.getDesktopAspectRatioPolicy();
        spyOn(desktopAppCompatAspectRatioPolicy);
        doReturn(aspectRatio).when(desktopAppCompatAspectRatioPolicy)
                .getDesiredAspectRatio(any(), anyBoolean());
    }

    private void applyUserMinAspectRatioOverride(ActivityRecord activity, int overrideCode,
            float overrideValue) {
        // Set desired aspect ratio to be below minimum so override can take effect.
        final DesktopAppCompatAspectRatioPolicy desktopAppCompatAspectRatioPolicy =
                activity.mAppCompatController.getDesktopAspectRatioPolicy();
        spyOn(desktopAppCompatAspectRatioPolicy);
        doReturn(1f).when(desktopAppCompatAspectRatioPolicy)
                .getDesiredAspectRatio(any(), anyBoolean());

        // Enable user aspect ratio settings
        final AppCompatConfiguration appCompatConfiguration =
                activity.mWmService.mAppCompatConfiguration;
        spyOn(appCompatConfiguration);
        doReturn(true).when(appCompatConfiguration)
                .isUserAppAspectRatioSettingsEnabled();

        // Simulate user min aspect ratio override being set.
        final AppCompatAspectRatioOverrides appCompatAspectRatioOverrides =
                activity.mAppCompatController.getAspectRatioOverrides();
        spyOn(appCompatAspectRatioOverrides);
        doReturn(overrideValue).when(appCompatAspectRatioOverrides).getUserMinAspectRatio();
        doReturn(overrideCode).when(appCompatAspectRatioOverrides)
                .getUserMinAspectRatioOverrideCode();
    }

    private TestDisplayContent createDisplayContent(@Configuration.Orientation int orientation,
            @NonNull Rect displayBounds) {
        return createDisplayContent(orientation, displayBounds, WINDOWING_MODE_FULLSCREEN);
    }

    private TestDisplayContent createDisplayContent(@Configuration.Orientation int orientation,
            @NonNull Rect displayBounds, int windowingMode) {
        final TestDisplayContent display = new TestDisplayContent
                .Builder(mAtm, displayBounds.width(), displayBounds.height())
                .setPosition(DisplayContent.POSITION_TOP).build();
        display.setBounds(displayBounds);
        display.getConfiguration().densityDpi = DENSITY_DEFAULT;
        display.getConfiguration().orientation = orientation;
        display.getDefaultTaskDisplayArea().setWindowingMode(windowingMode);

        return display;
    }

    private Task setUpActiveDeskRootTask(DisplayContent display) {
        final Task deskRoot = new TaskBuilder(mSupervisor).setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_FREEFORM).setDisplay(display)
                .setCreatedByOrganizer(true).build();
        display.getDefaultTaskDisplayArea().setLaunchRootTask(deskRoot,
                new int[]{WINDOWING_MODE_FREEFORM, WINDOWING_MODE_UNDEFINED},
                new int[]{ACTIVITY_TYPE_STANDARD});
        return deskRoot;
    }

    private void setupDesktopModeLaunchParamsModifier() {
        setupDesktopModeLaunchParamsModifier(/*isDesktopModeSupported=*/ true,
                /*enforceDeviceRestrictions=*/ true, /*doesDisplaySupportDesktop*/ true);
    }

    private void setupDesktopModeLaunchParamsModifier(boolean isDesktopModeSupported,
            boolean enforceDeviceRestrictions, boolean doesDisplaySupportDesktop) {
        doReturn(isDesktopModeSupported)
                .when(() -> DesktopModeHelper.canEnterDesktopMode(any()));
        doReturn(enforceDeviceRestrictions)
                .when(DesktopModeHelper::shouldEnforceDeviceRestrictions);
        doReturn(doesDisplaySupportDesktop)
                .when(mTarget).isDesktopModeSupportedOnDisplay(any());
    }

    private void allowOverlayPermissionForAllUsers(String[] permissions)
            throws PackageManager.NameNotFoundException {
        final PackageInfo packageInfo = mock(PackageInfo.class);
        packageInfo.requestedPermissions = permissions;
        packageInfo.requestedPermissionsFlags = new int[permissions.length];
        Arrays.fill(
                packageInfo.requestedPermissionsFlags,
                PackageInfo.REQUESTED_PERMISSION_GRANTED);
        doReturn(packageInfo).when(mPackageManager).getPackageInfoAsUser(
                anyString(),
                eq(PackageManager.GET_PERMISSIONS),
                anyInt());
    }
}
