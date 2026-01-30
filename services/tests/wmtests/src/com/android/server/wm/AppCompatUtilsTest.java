/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_PORTRAIT_DEVICE_IN_LANDSCAPE;
import static android.view.Surface.ROTATION_270;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.app.CameraCompatTaskInfo;
import android.app.CameraCompatTaskInfo.CameraCompatMode;
import android.app.TaskInfo;
import android.graphics.Rect;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.view.DisplayInfo;
import android.view.Surface;
import android.window.AppCompatTransitionInfo;

import androidx.annotation.NonNull;

import com.android.window.flags.Flags;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.function.Consumer;

/**
 * Test class for {@link AppCompatUtils}.
 * <p>
 * Build/Install/Run:
 * atest WmTests:AppCompatUtilsTest
 */
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatUtilsTest extends WindowTestsBase {

    @Test
    public void getLetterboxReasonString_inSizeCompatMode() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.setTopActivityInSizeCompatMode(/* inScm */ true);
            });

            robot.checkTopActivityLetterboxReason(/* expected */ "SIZE_COMPAT_MODE");
        });
    }

    @Test
    public void getLetterboxReasonString_fixedOrientation() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.checkTopActivityInSizeCompatMode(/* inScm */ false);
            });
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(
                    /* forFixedOrientationAndAspectRatio */ true);

            robot.checkTopActivityLetterboxReason(/* expected */ "FIXED_ORIENTATION");
        });
    }

    @Test
    public void getLetterboxReasonString_isLetterboxedForDisplayCutout() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.checkTopActivityInSizeCompatMode(/* inScm */ false);
            });
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(
                    /* forFixedOrientationAndAspectRatio */ false);
            robot.setIsLetterboxedForDisplayCutout(/* displayCutout */ true);

            robot.checkTopActivityLetterboxReason(/* expected */ "DISPLAY_CUTOUT");
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_SAFE_REGION_LETTERBOXING_V1)
    public void getLetterboxReasonString_isLetterboxedForSafeRegionOnly() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.checkTopActivityInSizeCompatMode(/* inScm */ false);
            });
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(
                    /* forFixedOrientationAndAspectRatio */ false);
            robot.setIsLetterboxedForDisplayCutout(/* displayCutout */ false);
            robot.setIsLetterboxedForAspectRatioOnly(/* forAspectRatio */ false);
            robot.setIsLetterboxedForSafeRegionOnlyAllowed(/* safeRegionOnly */ true);

            robot.checkTopActivityLetterboxReason(/* expected */ "SAFE_REGION");
        });
    }

    @Test
    public void getLetterboxReasonString_aspectRatio() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.checkTopActivityInSizeCompatMode(/* inScm */ false);
            });
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(
                    /* forFixedOrientationAndAspectRatio */ false);
            robot.setIsLetterboxedForDisplayCutout(/* displayCutout */ false);
            robot.setIsLetterboxedForAspectRatioOnly(/* forAspectRatio */ true);

            robot.checkTopActivityLetterboxReason(/* expected */ "ASPECT_RATIO");
        });
    }

    @Test
    public void getLetterboxReasonString_unknownReason() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.checkTopActivityInSizeCompatMode(/* inScm */ false);
            });
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(
                    /* forFixedOrientationAndAspectRatio */ false);
            robot.setIsLetterboxedForDisplayCutout(/* displayCutout */ false);
            robot.setIsLetterboxedForAspectRatioOnly(/* forAspectRatio */ false);
            robot.setIsLetterboxedForSafeRegionOnlyAllowed(/* safeRegionOnly */ false);

            robot.checkTopActivityLetterboxReason(/* expected */ "UNKNOWN_REASON");
        });
    }

    @Test
    public void testTopActivityEligibleForUserAspectRatioButton_eligible() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponentInNewTask();
                a.setIgnoreOrientationRequest(true);
            });
            robot.conf().enableUserAppAspectRatioSettings(true);

            robot.checkTaskInfoEligibleForUserAspectRatioButton(true);
        });
    }

    @Test
    public void testTopActivityEligibleForUserAspectRatioButton_disabled_notEligible() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponentInNewTask();
                a.setIgnoreOrientationRequest(true);
            });
            robot.conf().enableUserAppAspectRatioSettings(false);

            robot.checkTaskInfoEligibleForUserAspectRatioButton(false);
        });
    }

    @Test
    public void testTopActivityEligibleForUserAspectRatioButton_inSizeCompatMode_notEligible() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponentInNewTask();
                a.setIgnoreOrientationRequest(true);
                a.setTopActivityOrganizedTask();
                a.setTopActivityInSizeCompatMode(true);
                a.setTopActivityVisible(true);
            });
            robot.conf().enableUserAppAspectRatioSettings(true);

            robot.checkTaskInfoEligibleForUserAspectRatioButton(false);
        });
    }

    @Test
    public void testTopActivityEligibleForUserAspectRatioButton_transparentTop_notEligible() {
        runTestScenario((robot) -> {
            robot.transparentActivity((ta) -> {
                ta.launchTransparentActivityInTask();
                ta.activity().setIgnoreOrientationRequest(true);
            });
            robot.conf().enableUserAppAspectRatioSettings(true);

            robot.checkTaskInfoEligibleForUserAspectRatioButton(false);
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @DisableFlags(Flags.FLAG_ENABLE_CAMERA_COMPAT_COMPATIBILITY_INFO_ROTATE_AND_CROP_BUGFIX)
    public void getTaskInfoPropagatesCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.dw().allowEnterDesktopMode(/* isAllowed= */ true);
            robot.applyOnActivity(
                    AppCompatActivityRobot::createActivityWithComponentInNewTaskAndDisplay);
            robot.setCameraCompatTreatmentEnabledForActivity(/* enabled= */ true);

            robot.setCameraCompatMode(CAMERA_COMPAT_PORTRAIT_DEVICE_IN_LANDSCAPE);
            robot.checkTaskInfoCameraCompatMode(
                    CAMERA_COMPAT_PORTRAIT_DEVICE_IN_LANDSCAPE);
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @DisableFlags(Flags.FLAG_ENABLE_CAMERA_COMPAT_COMPATIBILITY_INFO_ROTATE_AND_CROP_BUGFIX)
    public void testTopActivityInCameraCompatMode_rotationSet() {
        runTestScenario((robot) -> {
            robot.dw().allowEnterDesktopMode(/* isAllowed= */ true);
            robot.applyOnActivity(
                    AppCompatActivityRobot::createActivityWithComponentInNewTaskAndDisplay);
            robot.setCameraCompatTreatmentEnabledForActivity(/* enabled= */ true);
            robot.setCameraCompatMode(CAMERA_COMPAT_PORTRAIT_DEVICE_IN_LANDSCAPE);

            final int expectedDisplayRotation = ROTATION_270;
            robot.activity().rotateDisplayForTopActivity(expectedDisplayRotation);
            robot.checkTaskInfoCameraCompatDisplayRotationSet(expectedDisplayRotation);
        });
    }

    @Test
    public void testTopActivityLetterboxed_hasBounds() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.checkTopActivityInSizeCompatMode(/* inScm */ false);
                a.setIgnoreOrientationRequest(true);
            });
            robot.setIsLetterboxPolicyRunning(true);
            robot.setLetterboxPolicyLetterboxBounds(new Rect(20, 30, 520, 630));
            robot.setIsLetterboxedForAspectRatioOnly(/* forAspectRatio */ true);


            robot.checkTaskInfoTopActivityHasBounds(/* expected */ new Rect(20, 30, 520, 630));
        });
    }

    @Test
    public void testTopActivityNotLetterboxed_hasNoBounds() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.setIgnoreOrientationRequest(true);
            });

            robot.checkTaskInfoTopActivityHasBounds(/* expected */ null);
        });
    }

    @Test
    public void testCreateAppCompatTransitionInfo_whenNotLetterboxed_returnsNull() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.setIgnoreOrientationRequest(true);
                a.setTopActivityHasLetterboxedBounds(/* letterboxed */ false);
            });

            robot.createAppCompatTransitionInfo();

            robot.checkAppCompatTransitionInfoIsCreated(/* expected */ false);
        });
    }

    @Test
    public void createAppCompatTransitionInfo_whenLetterboxed_containsLetterboxBounds() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.setTopActivityHasLetterboxedBounds(/* letterboxed */ true);
                a.configureTopActivityBounds(new Rect(20, 30, 520, 630));
            });

            robot.setIsLetterboxPolicyRunning(true);
            robot.setLetterboxPolicyLetterboxBounds(new Rect(20, 30, 520, 630));

            robot.createAppCompatTransitionInfo();

            robot.checkAppCompatTransitionInfoIsCreated(/* expected */ true);
            robot.checkAppCompatTransitionInfoLetterboxBounds(new Rect(20, 30, 520, 630));
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<AppCompatUtilsRobotTest> consumer) {
        final AppCompatUtilsRobotTest robot = new AppCompatUtilsRobotTest(this);
        consumer.accept(robot);
    }

    private static class AppCompatUtilsRobotTest extends AppCompatRobotBase {

        private final WindowState mWindowState;
        @NonNull
        private final AppCompatTransparentActivityRobot mTransparentActivityRobot;

        @Nullable
        private AppCompatTransitionInfo mAppCompatTransitionInfo;

        AppCompatUtilsRobotTest(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);
            mTransparentActivityRobot = new AppCompatTransparentActivityRobot(activity());
            mWindowState = Mockito.mock(WindowState.class);
        }

        @Override
        void onPostActivityCreation(@NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);
            spyOn(activity.mAppCompatController.getAspectRatioPolicy());
            spyOn(activity.mAppCompatController.getSafeRegionPolicy());
            spyOn(activity.mAppCompatController.getLetterboxPolicy());
        }

        @Override
        void onPostDisplayContentCreation(@NonNull DisplayContent displayContent) {
            super.onPostDisplayContentCreation(displayContent);
            mockPortraitDisplay(displayContent);
            if (displayContent.mAppCompatCameraPolicy.hasSimReqOrientationPolicy()) {
                spyOn(displayContent.mAppCompatCameraPolicy.mSimReqOrientationPolicy);
            }
        }

        void transparentActivity(@NonNull Consumer<AppCompatTransparentActivityRobot> consumer) {
            // We always create at least an opaque activity in a Task.
            activity().createNewTaskWithBaseActivity();
            consumer.accept(mTransparentActivityRobot);
        }

        void setIsLetterboxedForFixedOrientationAndAspectRatio(
                boolean forFixedOrientationAndAspectRatio) {
            when(activity().top().mAppCompatController.getAspectRatioPolicy()
                    .isLetterboxedForFixedOrientationAndAspectRatio())
                        .thenReturn(forFixedOrientationAndAspectRatio);
        }

        void setIsLetterboxedForAspectRatioOnly(boolean forAspectRatio) {
            when(activity().top().mAppCompatController.getAspectRatioPolicy()
                    .isLetterboxedForAspectRatioOnly()).thenReturn(forAspectRatio);
        }

        void setIsLetterboxedForDisplayCutout(boolean displayCutout) {
            when(mWindowState.isLetterboxedForDisplayCutout()).thenReturn(displayCutout);
        }

        void setIsLetterboxPolicyRunning(boolean isLetterboxRunning) {
            when(activity().top().mAppCompatController.getLetterboxPolicy().isRunning())
                    .thenReturn(isLetterboxRunning);
        }

        void setLetterboxPolicyLetterboxBounds(@NonNull Rect expectedBounds) {
            doAnswer(invocation -> {
                Rect bounds = invocation.getArgument(0);
                bounds.set(expectedBounds);
                return null;
            }).when(activity().top().mAppCompatController.getLetterboxPolicy())
                    .getLetterboxInnerBounds(any(Rect.class));
        }

        void setIsLetterboxedForSafeRegionOnlyAllowed(boolean safeRegionOnly) {
            when(activity().top().mAppCompatController.getSafeRegionPolicy()
                    .isLetterboxedForSafeRegionOnlyAllowed()).thenReturn(safeRegionOnly);
        }

        void setCameraCompatMode(@CameraCompatMode int mode) {
            doReturn(mode).when(activity().top().mDisplayContent.mAppCompatCameraPolicy
                    .mSimReqOrientationPolicy).getCameraCompatMode(activity().top());
        }

        void checkTopActivityLetterboxReason(@NonNull String expected) {
            Assert.assertEquals(expected,
                    AppCompatUtils.getLetterboxReasonString(activity().top(), mWindowState));
        }

        void createAppCompatTransitionInfo() {
            mAppCompatTransitionInfo = AppCompatUtils.createAppCompatTransitionInfo(
                    activity().top());
        }

        void checkAppCompatTransitionInfoIsCreated(boolean expected) {
            if (expected) {
                Assert.assertNotNull(mAppCompatTransitionInfo);
            } else {
                Assert.assertNull(mAppCompatTransitionInfo);
            }
        }

        void checkAppCompatTransitionInfoLetterboxBounds(@NonNull Rect expectedBounds) {
            Assert.assertEquals(expectedBounds, mAppCompatTransitionInfo.getLetterboxBounds());
        }

        @NonNull
        TaskInfo getTopTaskInfo() {
            return activity().top().getTask().getTaskInfo();
        }

        void checkTaskInfoEligibleForUserAspectRatioButton(boolean eligible) {
            Assert.assertEquals(eligible, getTopTaskInfo().appCompatTaskInfo
                    .eligibleForUserAspectRatioButton());
        }

        void checkTaskInfoCameraCompatMode(@CameraCompatTaskInfo.CameraCompatMode int mode) {
            Assert.assertEquals(mode, getTopTaskInfo().appCompatTaskInfo.cameraCompatTaskInfo
                    .cameraCompatMode);
        }

        void checkTaskInfoCameraCompatDisplayRotationSet(@Surface.Rotation int expectedRotation) {
            Assert.assertEquals(expectedRotation, getTopTaskInfo().appCompatTaskInfo
                    .cameraCompatTaskInfo.displayRotation);
        }

        void checkTaskInfoTopActivityHasBounds(Rect bounds) {
            Assert.assertEquals(bounds, getTopTaskInfo().appCompatTaskInfo
                    .topActivityLetterboxBounds);
        }

        void setCameraCompatTreatmentEnabledForActivity(boolean enabled) {
            doReturn(enabled).when(activity().displayContent().mAppCompatCameraPolicy
                    .mSimReqOrientationPolicy).isCompatibilityTreatmentEnabledForActivity(
                            eq(activity().top()), anyBoolean());
        }

        private void mockPortraitDisplay(DisplayContent displayContent) {
            doAnswer(invocation -> {
                DisplayInfo displayInfo = new DisplayInfo();
                displayContent.getDisplay().getDisplayInfo(displayInfo);
                displayInfo.rotation = Surface.ROTATION_90;
                // Set height and width so that the natural orientation (when rotation is 0) is
                // portrait.
                displayInfo.logicalHeight = 600;
                displayInfo.logicalWidth =  800;
                return displayInfo;
            }).when(displayContent.mWmService.mDisplayManagerInternal).getDisplayInfo(anyInt());
        }
    }
}
