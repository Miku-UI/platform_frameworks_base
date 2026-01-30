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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Tests for {@link AppCompatCameraStateSource}.
 *
 * <p>Build/Install/Run:
 *  atest WmTests:AppCompatCameraStateSourceTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatCameraStateSourceTests extends WindowTestsBase {

    @Test
    public void testOnCameraOpened_noPolicyAdded_policiesNotNotified() {
        runTestScenario((robot) -> {
            robot.callCameraOpened();

            robot.checkPolicyCameraOpenedCalled(/*canClosePolicy=*/ true, 0);
            robot.checkPolicyCameraOpenedCalled(/*canClosePolicy=*/ false, 0);
        });
    }

    @Test
    public void testOnCameraOpened_policyAdded_notifiesCameraOpened() {
        runTestScenario((robot) -> {
            robot.addPolicy(/*canClosePolicy=*/ true);

            robot.callCameraOpened();

            robot.checkPolicyCameraOpenedCalled(/*canClosePolicy=*/ true, 1);
            robot.checkPolicyCameraOpenedCalled(/*canClosePolicy=*/ false, 0);
        });
    }

    @Test
    public void testOnCameraOpened_twoPoliciesAdded_notifiesCameraOpened() {
        runTestScenario((robot) -> {
            robot.addPolicy(/*canClosePolicy=*/ true);
            robot.addPolicy(/*canClosePolicy=*/ false);

            robot.callCameraOpened();

            robot.checkPolicyCameraOpenedCalled(/*canClosePolicy=*/ true, 1);
            robot.checkPolicyCameraOpenedCalled(/*canClosePolicy=*/ false, 1);
        });
    }

    @Test
    public void testCanCameraBeClosed_canClosePolicyAdded_returnsTrueCanCameraBeClosed() {
        runTestScenario((robot) -> {
            robot.addPolicy(/*canClosePolicy=*/ true);

            robot.checkCanCameraBeClosed(true);
        });
    }

    @Test
    public void testCanCameraBeClosed_cannotClosePolicyAdded_returnsFalseCanCameraBeClosed() {
        runTestScenario((robot) -> {
            robot.addPolicy(/*canClosePolicy=*/ true);
            robot.addPolicy(/*canClosePolicy=*/ false);

            robot.checkCanCameraBeClosed(false);
        });
    }

    @Test
    public void testOnCameraClosed_policyAdded_notifiesCameraClosed() {
        runTestScenario((robot) -> {
            robot.addPolicy(/*canClosePolicy=*/ true);

            robot.callCameraClosed();

            robot.checkPolicyCameraClosedCalled(/*canClosePolicy=*/ true, 1);
            robot.checkPolicyCameraClosedCalled(/*canClosePolicy=*/ false, 0);
        });
    }

    @Test
    public void testOnCameraClosed_twoPoliciesAdded_notifiesCameraClosed() {
        runTestScenario((robot) -> {
            robot.addPolicy(/*canClosePolicy=*/ true);
            robot.addPolicy(/*canClosePolicy=*/ false);

            robot.callCameraClosed();

            robot.checkPolicyCameraClosedCalled(/*canClosePolicy=*/ true, 1);
            robot.checkPolicyCameraClosedCalled(/*canClosePolicy=*/ false, 1);
        });
    }

    @Test
    public void testOnCameraOpened_policyAddedAndRemoved_doesntNotifyPolicyCameraOpened() {
        runTestScenario((robot) -> {
            robot.addPolicy(/*canClosePolicy=*/ true);
            robot.addPolicy(/*canClosePolicy=*/ false);

            robot.removePolicy(/*canClosePolicy=*/ true);
            robot.callCameraOpened();

            robot.checkPolicyCameraOpenedCalled(/*canClosePolicy=*/ true, 0);
            robot.checkPolicyCameraOpenedCalled(/*canClosePolicy=*/ false, 1);
        });
    }

    @Test
    public void testOnCameraClosed_policyAddedAndRemoved_doesntNotifyPolicyCameraClosed() {
        runTestScenario((robot) -> {
            robot.addPolicy(/*canClosePolicy=*/ true);
            robot.addPolicy(/*canClosePolicy=*/ false);

            robot.removePolicy(/*canClosePolicy=*/ true);
            robot.callCameraClosed();

            robot.checkPolicyCameraClosedCalled(/*canClosePolicy=*/ true, 0);
            robot.checkPolicyCameraClosedCalled(/*canClosePolicy=*/ false, 1);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<AppCompatCameraStateSourceRobotTests> consumer) {
        final AppCompatCameraStateSourceRobotTests robot =
                new AppCompatCameraStateSourceRobotTests(this);
        consumer.accept(robot);
    }

    private static class AppCompatCameraStateSourceRobotTests extends AppCompatRobotBase {
        private static final String CAMERA_ID = "camera-1";

        private final AppCompatCameraStatePolicy mCanClosePolicy =
                mock(AppCompatCameraStatePolicy.class);
        private final AppCompatCameraStatePolicy mCannotClosePolicy =
                mock(AppCompatCameraStatePolicy.class);

        private final AppCompatCameraStateSource mSourceUnderTest;
        AppCompatCameraStateSourceRobotTests(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);

            mSourceUnderTest = new AppCompatCameraStateSource();

            doReturn(true).when(mCanClosePolicy).canCameraBeClosed(eq(CAMERA_ID), any());
            doReturn(false).when(mCannotClosePolicy).canCameraBeClosed(eq(CAMERA_ID), any());

            activity().createActivityWithComponent();
        }

        private void addPolicy(boolean canClosePolicy) {
            mSourceUnderTest.addCameraStatePolicy(getPolicy(canClosePolicy));
        }

        private void removePolicy(boolean canClosePolicy) {
            mSourceUnderTest.removeCameraStatePolicy(getPolicy(canClosePolicy));
        }

        private void callCameraOpened() {
            mSourceUnderTest.onCameraOpened(activity().top().app, activity().top().getTask());
        }

        private void callCameraClosed() {
            mSourceUnderTest.onCameraClosed(activity().top().app, activity().top().getTask());
        }

        private void checkPolicyCameraOpenedCalled(boolean canClosePolicy, int times) {
            verify(getPolicy(canClosePolicy), times(times)).onCameraOpened(activity().top().app,
                    activity().top().getTask());
        }

        private void checkCanCameraBeClosed(boolean expected) {
            assertEquals(expected, mSourceUnderTest.canCameraBeClosed(CAMERA_ID,
                    activity().top().getTask()));
        }

        private void checkPolicyCameraClosedCalled(boolean canClosePolicy, int times) {
            verify(canClosePolicy ? mCanClosePolicy : mCannotClosePolicy, times(times))
                    .onCameraClosed(activity().top().app, activity().top().getTask());
        }

        private AppCompatCameraStatePolicy getPolicy(boolean canClose) {
            return canClose ? mCanClosePolicy : mCannotClosePolicy;
        }
    }
}
