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

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.servertransaction.ActivityLifecycleItem.ON_STOP;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.Display.TYPE_EXTERNAL;
import static android.view.Display.TYPE_INTERNAL;
import static android.view.Display.TYPE_OVERLAY;
import static android.view.Display.TYPE_VIRTUAL;
import static android.view.Display.TYPE_WIFI;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_90;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;
import static com.android.internal.hidden_from_bootclasspath.com.android.window.flags.Flags.FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import android.annotation.NonNull;
import android.app.IApplicationThread;
import android.app.WindowConfiguration;
import android.app.servertransaction.RefreshCallbackItem;
import android.app.servertransaction.ResumeActivityItem;
import android.content.pm.ActivityInfo;
import android.content.pm.ActivityInfo.ScreenOrientation;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Configuration.Orientation;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.Presubmit;
import android.view.Surface;

import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.window.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Tests for {@link AppCompatCameraDisplayRotationPolicy}.
 *
 * Build/Install/Run:
 *  atest WmTests:AppCompatCameraDisplayRotationPolicyTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public final class AppCompatCameraDisplayRotationPolicyTests extends WindowTestsBase {
    private static final String TEST_PACKAGE_1 = "com.android.frameworks.wmtests";
    private static final String TEST_PACKAGE_2 = "com.test.package.two";
    private static final String CAMERA_ID_1 = "camera-1";
    private static final String TEST_PACKAGE_1_LABEL = "testPackage1";

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOpenedCameraInSplitScreen_showToast() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_MULTI_WINDOW);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertMultiWindowToastShown(true);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOpenedCameraInSplitScreen_orientationNotFixed_doNotShowToast() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_UNSPECIFIED, WINDOWING_MODE_MULTI_WINDOW);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertMultiWindowToastShown(false);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOnScreenRotationAnimationFinished_treatmentNotEnabled_doNotShowToast() {
        runTestScenario((robot) -> {
            robot.setTreatmentEnabledViaConfig(false);
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.finishRotationAnimation();

            robot.assertPostRotationToastShown(false);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOnScreenRotationAnimationFinished_noOpenCamera_doNotShowToast() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.finishRotationAnimation();

            robot.assertPostRotationToastShown(false);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOnScreenRotationAnimationFinished_notFullscreen_doNotShowToast() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_MULTI_WINDOW);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.finishRotationAnimation();

            robot.assertPostRotationToastShown(false);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOnScreenRotationAnimationFinished_orientationNotFixed_doNotShowToast() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_UNSPECIFIED);
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.finishRotationAnimation();

            robot.assertPostRotationToastShown(false);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOnScreenRotationAnimationFinished_showToast() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.finishRotationAnimation();

            robot.assertPostRotationToastShown(true);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testTreatmentNotEnabled_noForceRotationOrRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.setTreatmentEnabledViaConfig(false);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertDisplayRotationFromPolicy(SCREEN_ORIENTATION_UNSPECIFIED);

            robot.assertNoForceRotationOrRefresh();
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testTreatmentDisabledPerApp_noForceRotationOrRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.setForceRotateEnabledForActivity(false);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);

            robot.assertNoForceRotationOrRefresh();
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOrientationUnspecified_noForceRotationOrRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_UNSPECIFIED);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);

            robot.assertNoForceRotationOrRefresh();
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOrientationLocked_noForceRotationOrRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_LOCKED);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);

            robot.assertNoForceRotationOrRefresh();
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOrientationNoSensor_noForceRotationOrRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_NOSENSOR);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);

            robot.assertNoForceRotationOrRefresh();
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testIgnoreOrientationRequestIsFalse_noForceRotationOrRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.activity().setIgnoreOrientationRequest(false);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);

            robot.assertNoForceRotationOrRefresh();
        });
    }

    @Test
    @DisableFlags({Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES,
            FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX})
    public void testDisplayTypeExternal_noForceRotationOrRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT,
                    WINDOWING_MODE_FULLSCREEN, ORIENTATION_PORTRAIT, TYPE_EXTERNAL);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertNoForceRotationOrRefresh();
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testDisplaTypeyWifi_noForceRotationOrRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT,
                    WINDOWING_MODE_FULLSCREEN, ORIENTATION_PORTRAIT, TYPE_WIFI);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);

            robot.assertNoForceRotationOrRefresh();
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testDisplayTypeOverlay_noForceRotationOrRefresh() {
        runTestScenario((robot) -> {

            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT,
                    WINDOWING_MODE_FULLSCREEN, ORIENTATION_PORTRAIT, TYPE_OVERLAY);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);

            robot.assertNoForceRotationOrRefresh();
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testDisplayTypeVirtual_noForceRotationOrRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT,
                    WINDOWING_MODE_FULLSCREEN, ORIENTATION_PORTRAIT, TYPE_VIRTUAL);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);

            robot.assertNoForceRotationOrRefresh();
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testNoCameraConnection_noForceRotationOrRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);

            robot.assertNoForceRotationOrRefresh();
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testCameraDisconnected_revertRotationAndRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT,
                    WINDOWING_MODE_FULLSCREEN, ORIENTATION_LANDSCAPE);
            robot.activity().rotateDisplayForTopActivity(ROTATION_90);
            // Open camera and test for compat treatment
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);
            robot.assertActivityRefreshed(/* refreshed */ true);
            robot.assertDisplayRotationFromPolicy(SCREEN_ORIENTATION_LANDSCAPE);

            // Close camera and test for revert
            robot.onCameraClosed(CAMERA_ID_1);
            robot.assertDisplayRotationFromPolicy(SCREEN_ORIENTATION_UNSPECIFIED);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testGetOrientation_cameraConnectionClosed_returnUnspecified() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.assertDisplayRotationFromPolicy(SCREEN_ORIENTATION_UNSPECIFIED);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);

            robot.assertActivityRefreshed(/* refreshed */ true);
            robot.assertDisplayRotationFromPolicy(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraClosed(CAMERA_ID_1);

            robot.assertDisplayRotationFromPolicy(SCREEN_ORIENTATION_UNSPECIFIED);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testCameraOpenedForDifferentPackage_noForceRotationOrRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_2);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);

            robot.assertNoForceRotationOrRefresh();
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testGetOrientation_portraitActivity_portraitNaturalOrientation_returnPortrait() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT,
                    WINDOWING_MODE_FULLSCREEN, ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertDisplayRotationFromPolicy(SCREEN_ORIENTATION_PORTRAIT);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testGetOrientation_portraitActivity_landscapeNaturalOrientation_returnLandscape() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT,
                    WINDOWING_MODE_FULLSCREEN, ORIENTATION_LANDSCAPE);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertDisplayRotationFromPolicy(SCREEN_ORIENTATION_LANDSCAPE);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testGetOrientation_landscapeActivity_portraitNaturalOrientation_returnLandscape() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_LANDSCAPE,
                    WINDOWING_MODE_FULLSCREEN, ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertDisplayRotationFromPolicy(SCREEN_ORIENTATION_LANDSCAPE);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testGetOrientation_landscapeActivity_landscapeNaturalOrientation_returnPortrait() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_LANDSCAPE,
                    WINDOWING_MODE_FULLSCREEN, ORIENTATION_LANDSCAPE);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertDisplayRotationFromPolicy(SCREEN_ORIENTATION_PORTRAIT);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOnActivityConfigurationChanging_displayRotationNotChanging_noRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ false);

            robot.assertActivityRefreshed(/* refreshed */ false);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOnActivityConfigurationChanging_splitScreenAspectRatioAllowed_refresh() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.conf().enableCameraCompatSplitScreenAspectRatio(true);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging(/*isDisplayRotationChanging=*/ true);

            robot.assertActivityRefreshed(/* refreshed */ true);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testShouldCameraCompatControlOrientationWhenInvokedNoMultiWindow_returnTrue() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertShouldCameraCompatControlOrientation(true);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testShouldCameraCompatControlOrientationWhenNotInvokedNoMultiWindow_returnFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.assertShouldCameraCompatControlOrientation(false);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testShouldCameraCompatControlOrientationWhenNotInvokedMultiWindow_returnFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_MULTI_WINDOW);

            robot.assertShouldCameraCompatControlOrientation(false);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testShouldCameraCompatControlOrientationWhenInvokedMultiWindow_returnFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_MULTI_WINDOW);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertShouldCameraCompatControlOrientation(false);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<AppCompatCameraDisplayRotationPolicyRobotTests>
            consumer) {
        final AppCompatCameraDisplayRotationPolicyRobotTests robot =
                new AppCompatCameraDisplayRotationPolicyRobotTests(this);
        consumer.accept(robot);
    }

    private static class AppCompatCameraDisplayRotationPolicyRobotTests extends AppCompatRobotBase {
        private final WindowTestsBase mWindowTestsBase;

        private CameraManager.AvailabilityCallback mCameraAvailabilityCallback;

        AppCompatCameraDisplayRotationPolicyRobotTests(@NonNull WindowTestsBase windowTestsBase) {
            super(windowTestsBase);
            mWindowTestsBase = windowTestsBase;
            setupCameraManager();
            setupAppCompatConfiguration();
        }

        @Override
        void onPostDisplayContentCreation(@NonNull DisplayContent displayContent) {
            super.onPostDisplayContentCreation(displayContent);
            spyOn(displayContent.mAppCompatCameraPolicy);
            if (displayContent.mAppCompatCameraPolicy.mDisplayRotationPolicy != null) {
                spyOn(displayContent.mAppCompatCameraPolicy.mDisplayRotationPolicy);
            }
        }

        @Override
        void onPostActivityCreation(@NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);
            setupCameraManager();
            setupHandler();
            setupMockApplicationThread();
            setupFakeToasts();
        }

        private void setupMockApplicationThread() {
            IApplicationThread mockApplicationThread = mock(IApplicationThread.class);
            spyOn(activity().top().app);
            doReturn(mockApplicationThread).when(activity().top().app).getThread();
        }

        private void setupAppCompatConfiguration() {
            applyOnConf((c) -> {
                c.enableCameraCompatForceRotateTreatment(true);
                c.enableCameraCompatForceRotateTreatmentAtBuildTime(true);
                c.enableCameraCompatRefresh(true);
                c.enableCameraCompatRefreshCycleThroughStop(true);
                c.enableCameraCompatSplitScreenAspectRatio(false);
            });
        }

        private void setupCameraManager() {
            final CameraManager mockCameraManager = mock(CameraManager.class);
            doAnswer(invocation -> {
                mCameraAvailabilityCallback = invocation.getArgument(1);
                return null;
            }).when(mockCameraManager).registerAvailabilityCallback(
                    any(Executor.class), any(CameraManager.AvailabilityCallback.class));

            doReturn(mockCameraManager).when(mWindowTestsBase.mWm.mContext).getSystemService(
                    CameraManager.class);
        }

        private void setupHandler() {
            final Handler handler = activity().top().mWmService.mH;
            spyOn(handler);

            doAnswer(invocation -> {
                ((Runnable) invocation.getArgument(0)).run();
                return null;
            }).when(handler).postDelayed(any(Runnable.class), anyLong());
        }

        private void setupFakeToasts() {
            // Do not show the real toast.
            doNothing().when(cameraCompatPolicy()).showToast(anyInt());
            doNothing().when(cameraCompatPolicy()).showToast(anyInt(), anyString());

            final PackageManager mockPackageManager = mock(PackageManager.class);
            final ApplicationInfo mockApplicationInfo = mock(ApplicationInfo.class);
            when(mWindowTestsBase.mWm.mContext.getPackageManager()).thenReturn(mockPackageManager);
            try {
                when(mockPackageManager.getApplicationInfo(anyString(), anyInt()))
                        .thenReturn(mockApplicationInfo);
            } catch (PackageManager.NameNotFoundException e) {
                fail(e.getMessage());
            }

            doReturn(TEST_PACKAGE_1_LABEL).when(mockPackageManager)
                    .getApplicationLabel(mockApplicationInfo);
        }

        private void configureActivity(@ScreenOrientation int activityOrientation) {
            configureActivity(activityOrientation, WINDOWING_MODE_FULLSCREEN);
        }

        private void configureActivity(@ScreenOrientation int activityOrientation,
                @WindowConfiguration.WindowingMode int windowingMode) {
            configureActivityAndDisplay(activityOrientation, windowingMode, ORIENTATION_PORTRAIT);
        }

        private void configureActivityAndDisplay(@ScreenOrientation int activityOrientation,
                @WindowConfiguration.WindowingMode int windowingMode,
                @Orientation int naturalOrientation) {
            configureActivityAndDisplay(activityOrientation, windowingMode, naturalOrientation,
                    TYPE_INTERNAL);
        }
        private void configureActivityAndDisplay(@ScreenOrientation int activityOrientation,
                @WindowConfiguration.WindowingMode int windowingMode,
                @Orientation int naturalOrientation,  int displayType) {
            applyOnActivity(a -> {
                dw().allowEnterDesktopMode(true);
                a.createActivityWithComponentInNewTaskAndDisplay(displayType);
                a.setIgnoreOrientationRequest(true);
                a.configureTopActivity(/* minAspect */ -1, /* maxAspect */ -1,
                        activityOrientation, /* isUnresizable */ false);
                a.top().setWindowingMode(windowingMode);
                a.setDisplayNaturalOrientation(naturalOrientation);
                spyOn(a.top().mAppCompatController.getCameraOverrides());
                spyOn(a.top().info);
                spyOn(a.top().info.applicationInfo);
                doReturn(a.displayContent().getDisplayInfo()).when(
                        a.displayContent().mWmService.mDisplayManagerInternal).getDisplayInfo(
                        a.displayContent().mDisplayId);

                // Disable for camera compat, otherwise the treatment might not trigger for
                // fixed-orientation apps.
                doReturn(false).when(a.top().info.applicationInfo).isChangeEnabled(
                        ActivityInfo.UNIVERSAL_RESIZABLE_BY_DEFAULT);
            });
        }

        private void onCameraOpened(@NonNull String cameraId, @NonNull String packageName) {
            mCameraAvailabilityCallback.onCameraOpened(cameraId, packageName);
            waitHandlerIdle();
        }

        private void onCameraClosed(@NonNull String cameraId) {
            mCameraAvailabilityCallback.onCameraClosed(cameraId);
        }

        private void waitHandlerIdle() {
            mWindowTestsBase.waitHandlerIdle(activity().displayContent().mWmService.mH);
        }

        private void callOnActivityConfigurationChanging(boolean isDisplayRotationChanging) {
            activity().displayContent().mAppCompatCameraPolicy.mActivityRefresher
                    .onActivityConfigurationChanging(activity().top(),
                    /* oldConfig */ createConfigurationWithDisplayRotation(ROTATION_0),
                    /* newConfig */ createConfigurationWithDisplayRotation(
                            isDisplayRotationChanging ? ROTATION_90 : ROTATION_0));
        }

        private static Configuration createConfigurationWithDisplayRotation(
                @Surface.Rotation int rotation) {
            final Configuration config = new Configuration();
            config.windowConfiguration.setDisplayRotation(rotation);
            return config;
        }

        void setForceRotateEnabledForActivity(boolean enabled) {
            doReturn(enabled).when(activity().top().mAppCompatController.getCameraOverrides())
                    .shouldForceRotateForCameraCompat();
        }

        void setTreatmentEnabledViaConfig(boolean enable) {
            conf().enableCameraCompatForceRotateTreatment(enable);
        }

        void finishRotationAnimation() {
            cameraCompatPolicy().onScreenRotationAnimationFinished();
        }

        private void assertNoForceRotationOrRefresh() {
            assertEquals(SCREEN_ORIENTATION_UNSPECIFIED, cameraCompatPolicy().getOrientation());
            assertActivityRefreshed(/* refreshed */ false);
        }

        private void assertActivityRefreshed(boolean refreshRequested) {
            verify(activity().top().mAppCompatController.getCameraOverrides(),
                    times(refreshRequested ? 1 : 0)).setIsRefreshRequested(true);

            final RefreshCallbackItem refreshCallbackItem =
                    new RefreshCallbackItem(activity().top().token, ON_STOP);
            final ResumeActivityItem resumeActivityItem = new ResumeActivityItem(
                    activity().top().token,
                    /* isForward */ false, /* shouldSendCompatFakeFocus */ false);
            verify(activity().top().mAtmService.getLifecycleManager(),
                    times(refreshRequested ? 1 : 0))
                    .scheduleTransactionItems(activity().top().app.getThread(),
                            refreshCallbackItem, resumeActivityItem);
            assertFalse(activity().top().mAppCompatController.getCameraOverrides()
                    .isRefreshRequested());
        }

        void assertDisplayRotationFromPolicy(int expectedOrientation) {
            assertEquals(expectedOrientation, cameraCompatPolicy().getOrientation());
        }

        private void assertShouldCameraCompatControlOrientation(boolean shouldControl) {
            assertEquals(shouldControl,
                    cameraCompatPolicy().shouldCameraCompatControlOrientation(activity().top()));
        }

        private void assertMultiWindowToastShown(boolean shown) {
            verify(cameraCompatPolicy(), times(shown ? 1 : 0)).showToast(
                    anyInt(), //eq(R.string.display_rotation_camera_compat_toast_in_multi_window),
                    anyString());
        }

        private void assertPostRotationToastShown(boolean shown) {
            verify(cameraCompatPolicy(), times(shown ? 1 : 0)).showToast(
                    R.string.display_rotation_camera_compat_toast_after_rotation);
        }

        AppCompatCameraDisplayRotationPolicy cameraCompatPolicy() {
            return activity().displayContent().mAppCompatCameraPolicy.mDisplayRotationPolicy;
        }
    }
}
