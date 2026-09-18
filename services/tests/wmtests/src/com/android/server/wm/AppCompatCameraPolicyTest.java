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

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.content.pm.ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_ONLY_FOR_CAMERA;
import static android.content.pm.ActivityInfo.OVERRIDE_ORIENTATION_ONLY_FOR_CAMERA;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.AppCompatCameraPolicy.isTreatmentEnabledForActivity;
import static com.android.server.wm.AppCompatCameraPolicy.shouldCameraCompatControlOrientation;
import static com.android.server.wm.AppCompatCameraPolicy.shouldOverrideMinAspectRatioForCamera;
import static com.android.window.flags.Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.app.WindowConfiguration.WindowingMode;
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
            robot.applyOnConf(c -> c.enableCameraCompatForceRotateTreatment(/* enabled= */ true));
            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ true);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testDisplayRotationCompatPolicy_notPresentWhenDisabled() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> c.enableCameraCompatForceRotateTreatment(/* enabled= */ false));
            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ false);
        });
    }

    @Test
    @EnableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testDisplayRotationCompatPolicy_notPresentWhenSimReqOrientationPolicyIsEnabled() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatSimReqOrientationTreatment(/* enabled= */ true);
                c.enableCameraCompatForceRotateTreatment(/* enabled= */ true);
            });
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ false);
        });
    }


    @Test
    @EnableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testDisplayRotationCompatPolicy_presentWhenSimReqOrientationPolicyIsDisabled() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatSimReqOrientationTreatment(/* enabled= */ false);
                c.enableCameraCompatForceRotateTreatment(/* enabled= */ true);
            });
            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ true);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testDisplayRotationCompatPolicy_startedWhenEnabled() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> c.enableCameraCompatForceRotateTreatment(/* enabled= */ true));

            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ true);
            robot.checkTopActivityDisplayRotationCompatPolicyIsRunning();
        });
    }

    @Test
    public void testSimReqOrientationPolicy_presentWhenEnabledAndDW() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatSimReqOrientationTreatment(/* enabled= */ true);
                // Policies will be recreated after `applyOnConf()`, thus set the desktop mode here.
                robot.dw().allowEnterDesktopMode(/* isAllowed= */ true);
            });

            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ true);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testSimReqOrientationPolicy_notPresentWhenNoDW() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatSimReqOrientationTreatment(/* enabled= */ true);
                robot.dw().allowEnterDesktopMode(/* isAllowed= */ false);
            });

            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testSimReqOrientationPolicy_notPresentWhenDesktopWindowingNotAllowed() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatSimReqOrientationTreatment(/* enabled= */ true);
                robot.dw().allowEnterDesktopMode(/* isAllowed= */ false);
            });

            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ false);
        });
    }

    @Test
    @EnableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testSimReqOrientationPolicy_unifyCameraPoliciesAndAllowedViaConfig_present() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatSimReqOrientationTreatment(/* enabled= */ true);
                robot.dw().allowEnterDesktopMode(/* isAllowed= */ false);
            });

            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ true);
        });
    }

    @Test
    public void testSimReqOrientationPolicy_startedWhenEnabledAndDW() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatSimReqOrientationTreatment(/* enabled= */ true);
                robot.dw().allowEnterDesktopMode(/* isAllowed= */ true);
            });

            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ true);
            robot.checkTopActivitySimReqOrientationPolicyIsRunning();
        });
    }

    @Test
    public void testCameraStateManager_existsWhenCameraCompatFreeformExists() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatSimReqOrientationTreatment(/* enabled= */ true);
                robot.dw().allowEnterDesktopMode(true);
            });
            robot.activity().createActivityWithComponentInNewTaskAndDisplay();
            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ true);
            robot.checkTopActivityHasCameraStateMonitor(/* exists= */ true);
        });
    }

    @Test
    public void testCameraStateManager_startedWhenCameraCompatFreeformExists() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatSimReqOrientationTreatment(/* enabled= */ true);
                robot.dw().allowEnterDesktopMode(true);
            });

            robot.checkTopActivityHasSimReqOrientationPolicy(/* exists= */ true);
            robot.checkTopActivityHasCameraStateMonitor(/* exists= */ true);
            robot.checkTopActivityCameraStateMonitorIsListeningToCameraChanges();
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testCameraStateManager_existsWhenDisplayRotationCompatPolicyExists() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> c.enableCameraCompatForceRotateTreatment(/* enabled= */ true));

            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ true);
            robot.checkTopActivityHasCameraStateMonitor(/* exists= */ true);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testCameraStateManager_startedWhenDisplayRotationCompatPolicyExists() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> c.enableCameraCompatForceRotateTreatment(/* enabled= */ true));

            robot.checkTopActivityHasDisplayRotationCompatPolicy(/* exists= */ true);
            robot.checkTopActivityHasCameraStateMonitor(/* exists= */ true);
            robot.checkTopActivityCameraStateMonitorIsListeningToCameraChanges();
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testIsCameraCompatTreatmentActive_whenTreatmentForTopActivityIsEnabled() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> c.enableCameraCompatForceRotateTreatment(/* enabled= */ true));
            robot.activity().enableFullscreenCameraCompatTreatmentForTopActivity(
                    /* enabled */ true);

            robot.checkIsCameraCompatTreatmentActiveForTopActivity(/* active */ true);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testIsCameraCompatTreatmentNotActive_whenTreatmentForTopActivityIsDisabled() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> c.enableCameraCompatForceRotateTreatment(/* enabled= */ true));
            robot.activity().enableFullscreenCameraCompatTreatmentForTopActivity(
                    /* enabled */ false);

            robot.checkIsCameraCompatTreatmentActiveForTopActivity(/* active */ false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    @EnableCompatChanges(OVERRIDE_ORIENTATION_ONLY_FOR_CAMERA)
    public void testShouldOverrideOrientationForCamera_whenCameraIsNotRunning() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatForceRotateTreatment(/* enabled= */ true);
                robot.dw().allowEnterDesktopMode(true);
            });
            robot.applyOnActivity((a)-> {
                a.setIgnoreOrientationRequest(true);
                a.setIsCameraRunningAndWindowingModeEligibleFullscreen(/* enabled */ false);
            });

            robot.checkShouldOverrideOrientationForCamera(/* active */ false);
        });
    }

    @Test
    @EnableCompatChanges(OVERRIDE_ORIENTATION_ONLY_FOR_CAMERA)
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testShouldOverrideOrientationForCameraFullscr_cameraIsRunning() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> c.enableCameraCompatForceRotateTreatment(/* enabled= */ true));
            robot.applyOnActivity((a)-> {
                a.setIgnoreOrientationRequest(true);
                a.setIsCameraRunningAndWindowingModeEligibleFullscreen(/* active */ true);
            });

            robot.checkShouldOverrideOrientationForCamera(/* active */ true);
        });
    }

    @Test
    @EnableCompatChanges(OVERRIDE_ORIENTATION_ONLY_FOR_CAMERA)
    public void testShouldOverrideOrientationForCameraFreeform_cameraRunning_overrideEnabled() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatSimReqOrientationTreatment(/* enabled= */ true);
                robot.dw().allowEnterDesktopMode(true);
            });
            robot.applyOnActivity((a)-> {
                a.setIgnoreOrientationRequest(true);
                a.setIsCameraRunningAndWindowingModeEligibleFreeform(/* active */ true);
            });

            robot.checkShouldOverrideOrientationForCamera(/* active */ true);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    @EnableCompatChanges(OVERRIDE_MIN_ASPECT_RATIO_ONLY_FOR_CAMERA)
    public void testShouldOverrideMinAspectRatioForCamera_whenCameraIsNotRunning() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatForceRotateTreatment(/* enabled= */ true);
                robot.dw().allowEnterDesktopMode(true);
            });
            robot.activity().setIsCameraRunningAndWindowingModeEligibleFullscreen(
                    /* enabled */ false);

            robot.checkShouldOverrideMinAspectRatioForCamera(/* active */ false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    @DisableCompatChanges(OVERRIDE_MIN_ASPECT_RATIO_ONLY_FOR_CAMERA)
    public void testShouldOverrideMinAspectRatioForCamera_whenCameraIsRunning_overrideDisabled() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatForceRotateTreatment(/* enabled= */ true);
                robot.dw().allowEnterDesktopMode(true);
            });
            robot.activity().setIsCameraRunningAndWindowingModeEligibleFullscreen(
                    /* active */ true);

            robot.checkShouldOverrideMinAspectRatioForCamera(/* active */ false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    @EnableCompatChanges(OVERRIDE_MIN_ASPECT_RATIO_ONLY_FOR_CAMERA)
    public void testShouldOverrideMinAspectRatioForCameraFullscr_cameraIsRunning_overrideEnabled() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a)-> {
                robot.applyOnConf(c -> c.enableCameraCompatForceRotateTreatment(
                        /* enabled= */ true));
                a.createActivityWithComponentInNewTaskAndDisplay();
                a.setIsCameraRunningAndWindowingModeEligibleFullscreen(/* active */ true);
            });

            robot.checkShouldOverrideMinAspectRatioForCamera(/* active */ true);
        });
    }

    @Test
    @EnableCompatChanges(OVERRIDE_MIN_ASPECT_RATIO_ONLY_FOR_CAMERA)
    public void testShouldOverrideMinAspectRatioForCameraFreeform_cameraRunning_overrideEnabled() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatSimReqOrientationTreatment(/* enabled= */ true);
                robot.dw().allowEnterDesktopMode(true);
            });
            robot.applyOnActivity((a)-> {
                a.createActivityWithComponentInNewTaskAndDisplay();
                a.setIsCameraRunningAndWindowingModeEligibleFreeform(/* active */ true);
            });

            robot.checkShouldOverrideMinAspectRatioForCamera(/* active */ true);
        });
    }

    @Test
    public void testOnWindowingModeChanged_notifiesSimReqOrientationPolicy() {
        runTestScenario((robot) -> {
            robot.applyOnConf(c -> {
                c.enableCameraCompatSimReqOrientationTreatment(/* enabled= */ true);
                robot.dw().allowEnterDesktopMode(true);
            });

            robot.triggerOnWindowingModeChanged(WINDOWING_MODE_FREEFORM);

            robot.checkSimReqOrientationPolicyNotifiedOfWindowingModeChange(
                    WINDOWING_MODE_FREEFORM);
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
            activity().createActivityWithComponentInNewTaskAndDisplay();
        }

        @Override
        void applyOnConf(@NonNull Consumer<AppCompatConfigurationRobot> consumer) {
            super.applyOnConf(consumer);
            reInitCameraPolicy();
            spyOnPolicy();
        }

        private void spyOnPolicy() {
            spyOn(testBase().mWm.mAppCompatCameraPolicy);
            if (testBase().mWm.mAppCompatCameraPolicy.mDisplayRotationPolicy != null) {
                spyOn(testBase().mWm.mAppCompatCameraPolicy.mDisplayRotationPolicy);
            }
            if (testBase().mWm.mAppCompatCameraPolicy.mSimReqOrientationPolicy != null) {
                spyOn(testBase().mWm.mAppCompatCameraPolicy.mSimReqOrientationPolicy);
            }
        }

        void checkTopActivityHasDisplayRotationCompatPolicy(boolean exists) {
            assertEquals(exists, activity().top().mWmService.mAppCompatCameraPolicy
                    .hasDisplayRotationPolicy());
        }

        void checkTopActivityHasSimReqOrientationPolicy(boolean exists) {
            assertEquals(exists, activity().top().mWmService.mAppCompatCameraPolicy
                    .hasSimReqOrientationPolicy());
        }

        void checkTopActivityHasCameraStateMonitor(boolean exists) {
            assertEquals(exists, activity().top().mWmService.mAppCompatCameraPolicy
                    .hasCameraStateMonitor());
        }

        void checkTopActivityDisplayRotationCompatPolicyIsRunning() {
            assertTrue(activity().top().mWmService.mAppCompatCameraPolicy
                    .mDisplayRotationPolicy.isRunning());
        }

        void checkTopActivitySimReqOrientationPolicyIsRunning() {
            assertTrue(activity().top().mWmService.mAppCompatCameraPolicy
                    .mSimReqOrientationPolicy.isRunning());
        }

        void checkTopActivityCameraStateMonitorIsListeningToCameraChanges() {
            assertTrue(activity().top().mWmService.mAppCompatCameraPolicy
                    .mCameraStateMonitor.isListeningToCameraState());
        }

        void checkIsCameraCompatTreatmentActiveForTopActivity(boolean active) {
            assertEquals(active, isTreatmentEnabledForActivity(activity().top()));
        }

        void checkShouldOverrideMinAspectRatioForCamera(boolean expected) {
            assertEquals(expected, shouldOverrideMinAspectRatioForCamera(activity().top()));
        }

        void checkShouldOverrideOrientationForCamera(boolean expected) {
            assertEquals(expected, shouldCameraCompatControlOrientation(activity().top()));
        }

        void triggerOnWindowingModeChanged(@WindowingMode int windowingMode) {
            AppCompatCameraPolicy.onWindowingModeChanged(activity().top(), windowingMode);
        }

        void checkSimReqOrientationPolicyNotifiedOfWindowingModeChange(
                @WindowingMode int windowingMode) {
            verify(activity().top().mWmService.mAppCompatCameraPolicy.mSimReqOrientationPolicy)
                    .onWindowingModeChanged(eq(activity().top()), eq(windowingMode));
        }

    }
}
