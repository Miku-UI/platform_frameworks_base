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

package com.android.server.wm;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.Display.TYPE_EXTERNAL;
import static android.view.Display.TYPE_INTERNAL;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_90;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.window.flags.Flags.FLAG_ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX;
import static com.android.window.flags.Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING;

import static org.junit.Assert.assertEquals;

import android.annotation.NonNull;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.res.Configuration;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.view.Surface;

import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Tests for {@link AppCompatCameraRotationState}.
 *
 * Build/Install/Run:
 *  atest WmTests:AppCompatCameraInfoProviderTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatCameraRotationStateTests extends WindowTestsBase {
    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX)
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void testFeatureDisabled_returnsCurrentDisplayRotation() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(ROTATION_90, ORIENTATION_PORTRAIT, TYPE_INTERNAL);
            robot.makeCurrentDisplayDefault();
            // The last created display is 'current'.
            robot.configureActivityAndDisplay(ROTATION_0, ORIENTATION_LANDSCAPE, TYPE_EXTERNAL);

            robot.checkOrientationEventListenerSetUp(/* expected= */ false);
            robot.checkDisplayRotation(/* expected= */ Surface.ROTATION_0);
        });
    }

    @Test
    @EnableFlags({FLAG_ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX,
            FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING})
    public void testFeatureEnabled_internalDisplay_returnsCurrentDisplayRotation() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(ROTATION_0, ORIENTATION_PORTRAIT, TYPE_INTERNAL);
            robot.makeCurrentDisplayDefault();
            // The last created display is 'current'.
            robot.configureActivityAndDisplay(ROTATION_90, ORIENTATION_PORTRAIT, TYPE_INTERNAL);

            robot.checkOrientationEventListenerSetUp(/* expected= */ false);
            robot.checkDisplayRotation(/* expected= */ Surface.ROTATION_90);
        });
    }

    @Test
    @EnableFlags({FLAG_ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX,
            FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING})
    public void testFeatureEnabled_externalDisplay_returnsSensorRotation() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(ROTATION_90, ORIENTATION_PORTRAIT, TYPE_INTERNAL);
            robot.makeCurrentDisplayDefault();
            robot.configureActivityAndDisplay(ROTATION_0, ORIENTATION_LANDSCAPE, TYPE_EXTERNAL);
            robot.checkOrientationEventListenerSetUp(/* expected= */ true);

            // Sensor rotation is continuous, and counted in the opposite direction from display
            // rotation: 360 - 100 = 260, and 260 is closest to ROTATION_270.
            robot.setSensorOrientation(100);

            // Sensor rotation should be returned on external displays, even if built-in display has
            // different rotation (for example from disabled auto-rotation).
            robot.checkDisplayRotation(/* expected= */ Surface.ROTATION_270);
        });
    }

    @Test
    @EnableFlags({FLAG_ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX,
            FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING})
    public void testIsCameraDeviceOrientationPortrait_rotatesToLandscape_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(ROTATION_0, ORIENTATION_PORTRAIT, TYPE_INTERNAL);
            robot.makeCurrentDisplayDefault();
            // The last created display is 'current'.
            robot.configureActivityAndDisplay(ROTATION_0, ORIENTATION_LANDSCAPE, TYPE_EXTERNAL);
            robot.checkOrientationEventListenerSetUp(/* expected= */ true);

            // Sensor rotation is continuous, and counted in the opposite direction from display
            // rotation: 360 - 100 = 260, and 260 is closest to ROTATION_270.
            robot.setSensorOrientation(100);

            // Sensor rotation should be returned on external displays, even if built-in display has
            // different rotation (for example from disabled auto-rotation).
            robot.checkIsCameraDisplayRotationPortrait(/* expected= */ false);
        });
    }

    @Test
    @EnableFlags({FLAG_ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX,
            FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING})
    public void testIsPortraitCamera_portraitInnerDisplay_rotatesToLandscape_true() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(ROTATION_0, ORIENTATION_PORTRAIT, TYPE_INTERNAL);
            robot.makeCurrentDisplayDefault();
            robot.configureActivityAndDisplay(ROTATION_0, ORIENTATION_LANDSCAPE, TYPE_EXTERNAL);
            robot.checkOrientationEventListenerSetUp(/* expected= */ true);

            // Sensor rotation is continuous, and counted in the opposite direction from display
            // rotation: 360 - 100 = 260, and 260 is closest to ROTATION_270.
            robot.setSensorOrientation(100);

            // Current rotation does not affect whether camera sensor is portrait or landscape.
            robot.checkIsPortraitCamera(/* expected= */ true);
        });
    }

    @Test
    @EnableFlags({FLAG_ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX,
            FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING})
    public void testIsCamera_DeviceNaturalOrientationPortrait_landscapeDisplay_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(ROTATION_0, ORIENTATION_LANDSCAPE, TYPE_INTERNAL);

            // Current rotation does not affect whether camera sensor is portrait or landscape.
            robot.checkIsPortraitCamera(/* expected= */ false);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<AppCompatCameraInfoProviderRobotTests> consumer) {
        final AppCompatCameraInfoProviderRobotTests robot =
                new AppCompatCameraInfoProviderRobotTests(this);
        consumer.accept(robot);
    }

    private static class AppCompatCameraInfoProviderRobotTests extends AppCompatRobotBase {
        private AppCompatCameraRotationState mCameraInfoProvider;
        private final WindowManagerService mWm;

        AppCompatCameraInfoProviderRobotTests(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);
            mWm = windowTestBase.mWm;
            setupAppCompatConfiguration();
        }

        @Override
        void onPostDisplayContentCreation(@NonNull DisplayContent displayContent) {
            super.onPostDisplayContentCreation(displayContent);
        }

        @Override
        void onPostActivityCreation(@NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);
            mCameraInfoProvider = new AppCompatCameraRotationState(activity.mDisplayContent);
            mCameraInfoProvider.start();
        }

        private void setupAppCompatConfiguration() {
            applyOnConf((c) -> {
                c.enableCameraCompatForceRotateTreatment(true);
                c.enableCameraCompatForceRotateTreatmentAtBuildTime(true);
            });
        }

        private void configureActivityAndDisplay(
                @Surface.Rotation int displayRotation,
                @Configuration.Orientation int naturalOrientation,
                int displayType) {
            applyOnActivity(a -> {
                dw().allowEnterDesktopMode(true);
                a.createActivityWithComponentInNewTaskAndDisplay(displayType);
                a.setIgnoreOrientationRequest(true);
                a.rotateDisplayForTopActivity(displayRotation);
                a.setDisplayNaturalOrientation(naturalOrientation);
                spyOn(a.top().mAppCompatController.getCameraOverrides());
                spyOn(a.top().info);
                doReturn(a.displayContent().getDisplayInfo()).when(
                        a.displayContent().mWmService.mDisplayManagerInternal).getDisplayInfo(
                        a.displayContent().mDisplayId);
            });
        }

        void makeCurrentDisplayDefault() {
            doReturn(activity().displayContent()).when(mWm).getDefaultDisplayContentLocked();
        }

        void checkDisplayRotation(@Surface.Rotation int expected) {
            assertEquals(expected, mCameraInfoProvider.getCameraDeviceRotation());
        }

        void checkIsPortraitCamera(boolean expected) {
            assertEquals(expected, mCameraInfoProvider.isCameraDeviceNaturalOrientationPortrait());
        }

        void checkIsCameraDisplayRotationPortrait(boolean expected) {
            assertEquals(expected, mCameraInfoProvider.isCameraDeviceOrientationPortrait());
        }

        void checkOrientationEventListenerSetUp(boolean expected) {
            assertEquals(expected, mCameraInfoProvider.mOrientationEventListener != null);
        }
        void setSensorOrientation(int orientation) {
            mCameraInfoProvider.mOrientationEventListener.onOrientationChanged(orientation);
        }
    }
}
