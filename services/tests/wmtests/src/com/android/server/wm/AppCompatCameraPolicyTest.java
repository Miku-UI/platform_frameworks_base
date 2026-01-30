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

import static android.content.pm.ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_ONLY_FOR_CAMERA;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.AppCompatCameraPolicy.isTreatmentEnabledForActivity;
import static com.android.server.wm.AppCompatCameraPolicy.shouldOverrideMinAspectRatioForCamera;
import static com.android.window.flags.Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES;
import static com.android.window.flags.Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.compat.testing.PlatformCompatChangeRule;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Test class for {@link AppCompatCameraPolicy}.
 * <p>
 * Build/Install/Run:
 * atest WmTests:AppCompatCameraPolicyTest
 */
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatCameraPolicyTest extends WindowTestsBase {

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testDisplayRotationCompatPolicy_presentWhenEnabled() {
        runTestScenario((robot) -> {
            robot.conf().enableCameraCompatForceRotateTreatmentAtBuildTime(/* enabled= */ true);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ true);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testDisplayRotationCompatPolicy_notPresentWhenDisabled() {
        runTestScenario((robot) -> {
            robot.conf().enableCameraCompatForceRotateTreatmentAtBuildTime(/* enabled= */ false);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testDisplayRotationCompatPolicy_startedWhenEnabled() {
        runTestScenario((robot) -> {
            robot.conf().enableCameraCompatForceRotateTreatmentAtBuildTime(/* enabled= */ true);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ true);
            robot.checkTopActivityDisplayRotationCompatPolicyIsRunning();
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void testSimReqOrientationPolicy_presentWhenEnabledAndDW() {
        runTestScenario((robot) -> {
            robot.dw().allowEnterDesktopMode(/* isAllowed= */ true);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ true);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testSimReqOrientationPolicy_notPresentWhenNoDW() {
        runTestScenario((robot) -> {
            robot.dw().allowEnterDesktopMode(/* isAllowed= */ false);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ false);
        });
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void testSimReqOrientationPolicy_notPresentWhenNoFlag() {
        runTestScenario((robot) -> {
            robot.dw().allowEnterDesktopMode(/* isAllowed= */ true);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void testSimReqOrientationPolicy_notPresentWhenNoFlagAndNoDW() {
        runTestScenario((robot) -> {
            robot.dw().allowEnterDesktopMode(/* isAllowed= */ false);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ false);
        });
    }

    @Test
    @EnableFlags({FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
            FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES})
    public void testSimReqOrientationPolicy_unifyCameraPoliciesAndAllowedViaConfig_present() {
        runTestScenario((robot) -> {
            robot.dw().allowEnterDesktopMode(/* isAllowed= */ false);
            robot.conf().enableCameraCompatSimulateRequestedOrientationTreatment(
                    /* enabled= */ true);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ true);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void testSimReqOrientationPolicy_startedWhenEnabledAndDW() {
        runTestScenario((robot) -> {
            robot.dw().allowEnterDesktopMode(/* isAllowed= */ true);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ true);
            robot.checkTopActivitySimReqOrientationPolicyIsRunning();
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void testCameraStateManager_existsWhenCameraCompatFreeformExists() {
        runTestScenario((robot) -> {
            robot.dw().allowEnterDesktopMode(true);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ true);
            robot.checkTopActivityHasCameraStateMonitor(/* exists= */ true);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void testCameraStateManager_startedWhenCameraCompatFreeformExists() {
        runTestScenario((robot) -> {
            robot.dw().allowEnterDesktopMode(true);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ true);
            robot.checkTopActivityHasCameraStateMonitor(/* exists= */ true);
            robot.checkTopActivityCameraStateMonitorIsListeningToCameraChanges();
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testCameraStateManager_existsWhenDisplayRotationCompatPolicyExists() {
        runTestScenario((robot) -> {
            robot.conf().enableCameraCompatForceRotateTreatmentAtBuildTime(/* enabled= */ true);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ true);
            robot.checkTopActivityHasCameraStateMonitor(/* exists= */ true);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testCameraStateManager_startedWhenDisplayRotationCompatPolicyExists() {
        runTestScenario((robot) -> {
            robot.conf().enableCameraCompatForceRotateTreatmentAtBuildTime(/* enabled= */ true);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ true);
            robot.checkTopActivityHasCameraStateMonitor(/* exists= */ true);
            robot.checkTopActivityCameraStateMonitorIsListeningToCameraChanges();
        });
    }

    @Test
    @DisableFlags({FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
            FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES})
    public void testCameraStateManager_doesNotExistWhenNoPolicyExists() {
        runTestScenario((robot) -> {
            robot.conf().enableCameraCompatForceRotateTreatmentAtBuildTime(/* enabled= */ false);
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ false);
            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ false);
            robot.checkTopActivityHasCameraStateMonitor(/* exists= */ false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testIsCameraCompatTreatmentActive_whenTreatmentForTopActivityIsEnabled() {
        runTestScenario((robot) -> {
            robot.conf().enableCameraCompatForceRotateTreatmentAtBuildTime(/* enabled= */ true);
            robot.applyOnActivity((a)-> {
                a.createActivityWithComponentInNewTaskAndDisplay();
                a.enableFullscreenCameraCompatTreatmentForTopActivity(/* enabled */ true);
            });

            robot.checkIsCameraCompatTreatmentActiveForTopActivity(/* active */ true);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testIsCameraCompatTreatmentNotActive_whenTreatmentForTopActivityIsDisabled() {
        runTestScenario((robot) -> {
            robot.conf().enableCameraCompatForceRotateTreatmentAtBuildTime(/* enabled= */ true);
            robot.applyOnActivity((a)-> {
                a.createActivityWithComponent();
                a.enableFullscreenCameraCompatTreatmentForTopActivity(/* enabled */ false);
            });

            robot.checkIsCameraCompatTreatmentActiveForTopActivity(/* active */ false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    @EnableCompatChanges(OVERRIDE_MIN_ASPECT_RATIO_ONLY_FOR_CAMERA)
    public void testShouldOverrideMinAspectRatioForCamera_whenCameraIsNotRunning() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a)-> {
                robot.dw().allowEnterDesktopMode(true);
                robot.conf().enableCameraCompatForceRotateTreatmentAtBuildTime(/* enabled= */ true);
                a.createActivityWithComponentInNewTaskAndDisplay();
                a.setIsCameraRunningAndWindowingModeEligibleFullscreen(/* enabled */ false);
            });

            robot.checkShouldOverrideMinAspectRatioForCamera(/* active */ false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    @DisableCompatChanges(OVERRIDE_MIN_ASPECT_RATIO_ONLY_FOR_CAMERA)
    public void testShouldOverrideMinAspectRatioForCamera_whenCameraIsRunning_overrideDisabled() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a)-> {
                robot.dw().allowEnterDesktopMode(true);
                robot.conf().enableCameraCompatForceRotateTreatmentAtBuildTime(/* enabled= */ true);
                a.createActivityWithComponentInNewTaskAndDisplay();
                a.setIsCameraRunningAndWindowingModeEligibleFullscreen(/* active */ true);
            });

            robot.checkShouldOverrideMinAspectRatioForCamera(/* active */ false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    @EnableCompatChanges(OVERRIDE_MIN_ASPECT_RATIO_ONLY_FOR_CAMERA)
    public void testShouldOverrideMinAspectRatioForCameraFullscr_cameraIsRunning_overrideEnabled() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a)-> {
                robot.conf().enableCameraCompatForceRotateTreatmentAtBuildTime(/* enabled= */ true);
                a.createActivityWithComponentInNewTaskAndDisplay();
                a.setIsCameraRunningAndWindowingModeEligibleFullscreen(/* active */ true);
            });

            robot.checkShouldOverrideMinAspectRatioForCamera(/* active */ true);
        });
    }


    @Test
    @EnableCompatChanges(OVERRIDE_MIN_ASPECT_RATIO_ONLY_FOR_CAMERA)
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void testShouldOverrideMinAspectRatioForCameraFreeform_cameraRunning_overrideEnabled() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a)-> {
                robot.dw().allowEnterDesktopMode(true);
                a.createActivityWithComponentInNewTaskAndDisplay();
                a.setIsCameraRunningAndWindowingModeEligibleFreeform(/* active */ true);
            });

            robot.checkShouldOverrideMinAspectRatioForCamera(/* active */ true);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<AppCompatCameraPolicyRobotTest> consumer) {
        final AppCompatCameraPolicyRobotTest robot =
                new AppCompatCameraPolicyRobotTest(this);
        consumer.accept(robot);
    }


    private static class AppCompatCameraPolicyRobotTest extends AppCompatRobotBase {
        AppCompatCameraPolicyRobotTest(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);
        }

        @Override
        void onPostDisplayContentCreation(@NonNull DisplayContent displayContent) {
            super.onPostDisplayContentCreation(displayContent);

            spyOn(displayContent.mAppCompatCameraPolicy);
            if (displayContent.mAppCompatCameraPolicy.mDisplayRotationPolicy != null) {
                spyOn(displayContent.mAppCompatCameraPolicy.mDisplayRotationPolicy);
            }
            if (displayContent.mAppCompatCameraPolicy.mSimReqOrientationPolicy != null) {
                spyOn(displayContent.mAppCompatCameraPolicy.mSimReqOrientationPolicy);
            }
        }

        void checkTopActivityHasDisplayRotationCompatPolicy(boolean exists) {
            assertEquals(exists, activity().top().mDisplayContent.mAppCompatCameraPolicy
                    .hasDisplayRotationPolicy());
        }

        void checkTopActivityHasSimReqOrientationPolicy(boolean exists) {
            assertEquals(exists, activity().top().mDisplayContent.mAppCompatCameraPolicy
                    .hasSimReqOrientationPolicy());
        }

        void checkTopActivityHasCameraStateMonitor(boolean exists) {
            assertEquals(exists, activity().top().mDisplayContent.mAppCompatCameraPolicy
                    .hasCameraStateMonitor());
        }

        void checkTopActivityDisplayRotationCompatPolicyIsRunning() {
            assertTrue(activity().top().mDisplayContent.mAppCompatCameraPolicy
                    .mDisplayRotationPolicy.isRunning());
        }

        void checkTopActivitySimReqOrientationPolicyIsRunning() {
            assertTrue(activity().top().mDisplayContent.mAppCompatCameraPolicy
                    .mSimReqOrientationPolicy.isRunning());
        }

        void checkTopActivityCameraStateMonitorIsListeningToCameraChanges() {
            assertTrue(activity().top().mDisplayContent.mAppCompatCameraPolicy
                    .mCameraStateMonitor.isListeningToCameraState());
        }

        void checkIsCameraCompatTreatmentActiveForTopActivity(boolean active) {
            assertEquals(active, isTreatmentEnabledForActivity(activity().top()));
        }

        void checkShouldOverrideMinAspectRatioForCamera(boolean expected) {
            assertEquals(expected, shouldOverrideMinAspectRatioForCamera(activity().top()));
        }
    }
}
