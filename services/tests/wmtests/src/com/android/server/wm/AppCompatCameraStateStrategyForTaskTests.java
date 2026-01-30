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

import static com.android.window.flags.Flags.FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX;

import static org.junit.Assert.assertEquals;

import android.compat.testing.PlatformCompatChangeRule;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.util.ArraySet;

import androidx.annotation.NonNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Test class for {@link AppCompatCameraStateStrategyForTask}.
 * <p>
 * Build/Install/Run:
 * atest WmTests:AppCompatCameraStateStrategyForTaskTests
 */
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatCameraStateStrategyForTaskTests extends WindowTestsBase {
    private static final String TEST_PACKAGE_1 = "com.android.frameworks.wmtests";
    private static final String CAMERA_ID_1 = "camera-1";
    private static final String CAMERA_ID_2 = "camera-2";

    @Rule
    public TestRule mCompatChangeRule = new PlatformCompatChangeRule();

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testTrackCameraOpened_returnsCorrectCameraAppInfo() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();

            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testTrackCameraOpened_cameraNotYetOpened() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();

            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraOpened(false);
            robot.checkCameraOpenedCalledForCanClosePolicy(0);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testOnCameraOpened_notifiesPolicy() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();

            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.maybeNotifyPolicyCameraOpened(CAMERA_ID_1);

            robot.checkCameraOpenedCalledForCanClosePolicy(1);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testOnCameraOpened_cameraIsOpened() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();

            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.maybeNotifyPolicyCameraOpened(CAMERA_ID_1);

            robot.checkIsCameraOpened(true);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testTrackCameraClosed_returnsCorrectCameraAppInfo() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();
            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.maybeNotifyPolicyCameraOpened(CAMERA_ID_1);

            robot.assertCorrectCameraAppInfoOnCameraClosed(CAMERA_ID_1);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testOnCameraClosed_policyCanCloseCamera_cameraIsClosed() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();
            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.maybeNotifyPolicyCameraOpened(CAMERA_ID_1);
            robot.assertCorrectCameraAppInfoOnCameraClosed(CAMERA_ID_1);

            robot.assertReportsCloseStatusOnCameraClose(CAMERA_ID_1);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testOnCameraClosed_activityCannotCloseCamera_returnsCorrectStatus() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCannotCloseOnce();

            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.maybeNotifyPolicyCameraOpened(CAMERA_ID_1);
            robot.assertCorrectCameraAppInfoOnCameraClosed(CAMERA_ID_1);

            robot.assertReportsCloseStatusOnCameraClose(CAMERA_ID_1);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testActivitySwitchesCameras_policyIsNotNotifiedAgain() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();
            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.maybeNotifyPolicyCameraOpened(CAMERA_ID_1);

            // Only one camera can be active at a time, so Camera 1 close signal will come before
            // Camera 2 open signal.
            robot.assertCorrectCameraAppInfoOnCameraClosed(CAMERA_ID_1);
            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_2, TEST_PACKAGE_1);
            // However, processing delay for opening the camera is shorter than for closing the
            // camera, therefore it will happen first.
            robot.maybeNotifyPolicyCameraOpened(CAMERA_ID_2);
            robot.maybeNotifyPolicyCameraClosed(CAMERA_ID_1);

            robot.checkCameraOpenedCalledForCanClosePolicy(1);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<AppCompatCameraStateStrategyForTaskRobotTest> consumer) {
        final AppCompatCameraStateStrategyForTaskRobotTest robot =
                new AppCompatCameraStateStrategyForTaskRobotTest(this);
        consumer.accept(robot);
    }

    private static class AppCompatCameraStateStrategyForTaskRobotTest extends AppCompatRobotBase {
        private FakeAppCompatCameraStatePolicy mFakePolicyCannotCloseOnce;
        private FakeAppCompatCameraStatePolicy mFakePolicyCanClose;

        private Set<FakeAppCompatCameraStatePolicy> mRegisteredPolicies = new ArraySet<>();

        AppCompatCameraStateStrategyForTaskRobotTest(@NonNull WindowTestsBase windowTestsBase) {
            super(windowTestsBase);
            setupAppCompatConfiguration();
            configureActivityAndDisplay();
        }

        @Override
        void onPostDisplayContentCreation(@NonNull DisplayContent displayContent) {
            super.onPostDisplayContentCreation(displayContent);
            mRegisteredPolicies = new ArraySet<>();
            mFakePolicyCannotCloseOnce = new FakeAppCompatCameraStatePolicy(true);
            mFakePolicyCanClose = new FakeAppCompatCameraStatePolicy(false);
        }

        private void configureActivityAndDisplay() {
            applyOnActivity(a -> {
                a.createActivityWithComponentInNewTaskAndDisplay();
            });
        }

        private void setupAppCompatConfiguration() {
            applyOnConf((c) -> {
                c.enableCameraCompatForceRotateTreatment(true);
                c.enableCameraCompatForceRotateTreatmentAtBuildTime(true);
                c.enableCameraCompatSimulateRequestedOrientationTreatment(true);
            });
        }

        private void addPolicyThatCanClose() {
            getAppCompatCameraStateSource().addCameraStatePolicy(mFakePolicyCanClose);
            mRegisteredPolicies.add(mFakePolicyCanClose);
        }

        private void addPolicyThatCannotCloseOnce() {
            getAppCompatCameraStateSource().addCameraStatePolicy(mFakePolicyCannotCloseOnce);
            mRegisteredPolicies.add(mFakePolicyCannotCloseOnce);
        }

        private AppCompatCameraStateSource getAppCompatCameraStateSource() {
            return (AppCompatCameraStateSource) activity().top().mDisplayContent
                    .mAppCompatCameraPolicy.mCameraStateMonitor.mAppCompatCameraStatePolicy;
        }

        private void assertCorrectCameraAppInfoOnCameraOpened(@NonNull String cameraId,
                @NonNull String packageName) {
            final CameraAppInfo cameraAppInfo = trackCameraOpened(cameraId, packageName);
            assertEquals(cameraId, cameraAppInfo.mCameraId);
            assertEquals(packageName, cameraAppInfo.mPackageName);
            assertEquals(activity().top().getTask().mTaskId, cameraAppInfo.mTaskId);
            assertEquals(activity().top().app.getPid(), cameraAppInfo.mPid);
        }

        private void assertCorrectCameraAppInfoOnCameraClosed(@NonNull String cameraId) {
            final CameraAppInfo cameraAppInfo = trackCameraClosed(cameraId);
            assertEquals(cameraId, cameraAppInfo.mCameraId);
            assertEquals(activity().top().packageName, cameraAppInfo.mPackageName);
            assertEquals(activity().top().getTask().mTaskId, cameraAppInfo.mTaskId);
            assertEquals(activity().top().app.getPid(), cameraAppInfo.mPid);
        }

        private void checkIsCameraOpened(boolean expectedIsOpened) {
            assertEquals(expectedIsOpened, getCameraStateMonitor().mAppCompatCameraStateStrategy
                    .isCameraRunningForActivity(activity().top()));
        }

        private void checkCameraOpenedCalledForCanClosePolicy(int times) {
            assertEquals(times, mFakePolicyCanClose.mOnCameraOpenedCounter);
        }

        private void assertReportsCloseStatusOnCameraClose(@NonNull String cameraId) {
            assertReportsCloseStatusOnCameraClose(getExpectedCameraAppInfo(cameraId));
        }

        private void assertReportsCloseStatusOnCameraClose(@NonNull CameraAppInfo cameraAppInfo) {
            for (FakeAppCompatCameraStatePolicy policy : mRegisteredPolicies) {
                boolean simulateCannotClose = policy == mFakePolicyCannotCloseOnce;
                assertEquals(!simulateCannotClose, maybeNotifyPolicyCameraClosed(cameraAppInfo,
                        simulateCannotClose ? mFakePolicyCannotCloseOnce : mFakePolicyCanClose));
            }
        }

        private CameraAppInfo trackCameraOpened(@NonNull String cameraId,
                @NonNull String packageName) {
            return activity().displayContent().mAppCompatCameraPolicy.mCameraStateMonitor
                    .mAppCompatCameraStateStrategy.trackOnCameraOpened(cameraId, packageName);
        }

        private void maybeNotifyPolicyCameraOpened(@NonNull String cameraId) {
            for (FakeAppCompatCameraStatePolicy policy : mRegisteredPolicies) {
                maybeNotifyPolicyCameraOpened(getExpectedCameraAppInfo(cameraId),
                        policy);
            }
        }

        private void maybeNotifyPolicyCameraOpened(@NonNull CameraAppInfo cameraAppInfo,
                @NonNull AppCompatCameraStatePolicy policy) {
            activity().displayContent().mAppCompatCameraPolicy.mCameraStateMonitor
                    .mAppCompatCameraStateStrategy.notifyPolicyCameraOpenedIfNeeded(cameraAppInfo,
                            policy);
        }

        private CameraAppInfo trackCameraClosed(@NonNull String cameraId) {
            return activity().displayContent().mAppCompatCameraPolicy.mCameraStateMonitor
                    .mAppCompatCameraStateStrategy.trackOnCameraClosed(cameraId);
        }

        private void maybeNotifyPolicyCameraClosed(@NonNull String cameraId) {
            for (FakeAppCompatCameraStatePolicy policy : mRegisteredPolicies) {
                maybeNotifyPolicyCameraClosed(getExpectedCameraAppInfo(cameraId),
                        policy);
            }
        }

        private boolean maybeNotifyPolicyCameraClosed(@NonNull CameraAppInfo cameraAppInfo,
                @NonNull AppCompatCameraStatePolicy policy) {
            return activity().displayContent().mAppCompatCameraPolicy.mCameraStateMonitor
                    .mAppCompatCameraStateStrategy.notifyPolicyCameraClosedIfNeeded(cameraAppInfo,
                            policy);
        }

        private CameraAppInfo getExpectedCameraAppInfo(@NonNull String cameraId) {
            return new CameraAppInfo(cameraId,
                    activity().top().app.getPid(),
                    activity().top().getTask().mTaskId,
                    activity().top().packageName);
        }

        private CameraStateMonitor getCameraStateMonitor() {
            return activity().top().mDisplayContent.mAppCompatCameraPolicy.mCameraStateMonitor;
        }
    }
}
