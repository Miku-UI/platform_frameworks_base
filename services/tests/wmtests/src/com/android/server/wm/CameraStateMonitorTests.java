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

import static android.hardware.camera2.CameraMetadata.SCALER_ROTATE_AND_CROP_180;
import static android.hardware.camera2.CameraMetadata.SCALER_ROTATE_AND_CROP_270;
import static android.hardware.camera2.CameraMetadata.SCALER_ROTATE_AND_CROP_90;
import static android.hardware.camera2.CameraMetadata.SCALER_ROTATE_AND_CROP_AUTO;
import static android.hardware.camera2.CameraMetadata.SCALER_ROTATE_AND_CROP_NONE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

import android.app.IApplicationThread;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Tests for {@link CameraStateMonitor}.
 *
 * <p>Build/Install/Run:
 *  atest WmTests:CameraStateMonitorTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public final class CameraStateMonitorTests extends WindowTestsBase {

    private static final String TEST_PACKAGE_1 = "com.android.frameworks.wmtests";
    private static final String CAMERA_ID_1 = "camera-1";
    private static final String CAMERA_ID_2 = "camera-2";

    @Test
    public void testOnCameraOpened_policyAdded_notifiesCameraOpened() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkCameraOpenedCalledForCanClosePolicy(1);
        });
    }

    @Test
    public void testOnCameraOpened_policyAdded_cameraRegistersAsOpenedDuringTheCallback() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkCameraRegisteresAsOpenedForCanClosePolicy(true);
        });
    }

    @Test
    public void testOnCameraOpened_cameraClosed_notifyCameraClosed() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.onCameraClosed(CAMERA_ID_1);

            robot.checkCanCloseCalledForCanClosePolicy(1);
            robot.checkCameraClosedCalledForCanClosePolicy(1);
        });
    }

    @Test
    public void testOnCameraOpenedAndClosed_cameraRegistersAsClosedDuringTheCallback() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.onCameraClosed(CAMERA_ID_1);

            robot.checkCameraRegisteresAsOpenedForCanClosePolicy(false);
        });
    }

    @Test
    public void testOnCameraOpened_policyCannotCloseYet_notifyCameraClosedAgain() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCannotCloseOnce();
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.onCameraClosed(CAMERA_ID_1);

            robot.checkCanCloseCalledForCannotCloseOncePolicy(2);
            robot.checkCameraClosedCalledForCannotCloseOncePolicy(1);
        });
    }

    @Test
    public void testReconnectedToDifferentCamera_notifiesPolicy() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.onCameraClosed(CAMERA_ID_1);
            robot.onCameraOpened(CAMERA_ID_2, TEST_PACKAGE_1);

            robot.checkCameraOpenedCalledForCanClosePolicy(2);
        });
    }

    @Test
    public void testAvailableRotateAndCropModes() {
        runTestScenario((robot) -> {
            final int[] supportedCameraCompatModes = new int[]{
                    SCALER_ROTATE_AND_CROP_NONE,
                    SCALER_ROTATE_AND_CROP_AUTO,
                    SCALER_ROTATE_AND_CROP_90};
            robot.setupSupportedRotateAndCropModes(supportedCameraCompatModes);
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsRotateAndCropModeSupported(SCALER_ROTATE_AND_CROP_NONE, true);
            robot.checkIsRotateAndCropModeSupported(SCALER_ROTATE_AND_CROP_AUTO, true);
            robot.checkIsRotateAndCropModeSupported(SCALER_ROTATE_AND_CROP_90, true);
            robot.checkIsRotateAndCropModeSupported(SCALER_ROTATE_AND_CROP_180, false);
            robot.checkIsRotateAndCropModeSupported(SCALER_ROTATE_AND_CROP_270, false);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<CameraStateMonitorRobotTests> consumer) {
        final CameraStateMonitorRobotTests robot =
                new CameraStateMonitorRobotTests(this);
        consumer.accept(robot);
    }

    private static class CameraStateMonitorRobotTests extends AppCompatRobotBase {
        private final WindowTestsBase mWindowTestsBase;

        // Simulates a policy which will react to the change on a particular activity - for example
        // put the activity in a camera compat mode.
        private FakeAppCompatCameraStatePolicy mFakePolicyCanClose;
        // Simulates a policy which for some reason cannot process `onCameraClosed` event once it
        // first arrives - this means that the update needs to be postponed.
        private FakeAppCompatCameraStatePolicy mFakePolicyCannotCloseOnce;

        private CameraManager.AvailabilityCallback mCameraAvailabilityCallback;

        private CameraManager mMockCameraManager = mock(CameraManager.class);

        CameraStateMonitorRobotTests(@NonNull WindowTestsBase windowTestsBase) {
            super(windowTestsBase);
            mWindowTestsBase = windowTestsBase;
            setupCameraManager();
            setupAppCompatConfiguration();
            reInitCameraPolicy();
            spyOnPolicy();
            configureActivityAndDisplay();
        }

        private void spyOnPolicy() {
            final AppCompatCameraPolicy cameraPolicy = testBase().mWm.mAppCompatCameraPolicy;
            spyOn(cameraPolicy);
            if (cameraPolicy.mSimReqOrientationPolicy != null) {
                spyOn(cameraPolicy.mSimReqOrientationPolicy);
            }

            mFakePolicyCannotCloseOnce = new FakeAppCompatCameraStatePolicy(true);
            mFakePolicyCanClose = new FakeAppCompatCameraStatePolicy(false);
        }

        @Override
        void onPostActivityCreation(@NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);
            setupCameraManager();
            setupHandler();
            setupMockApplicationThread();
        }

        private void setupMockApplicationThread() {
            IApplicationThread mockApplicationThread = mock(IApplicationThread.class);
            spyOn(activity().top().app);
            doReturn(mockApplicationThread).when(activity().top().app).getThread();
        }

        private void setupAppCompatConfiguration() {
            applyOnConf((c) -> {
                c.enableCameraCompatForceRotateTreatment(true);
                c.enableCameraCompatSimReqOrientationTreatment(true);
                c.enableCameraCompatRefresh(true);
                c.enableCameraCompatRefreshCycleThroughStop(true);
                c.enableCameraCompatSplitScreenAspectRatio(false);
                dw().allowEnterDesktopMode(true);
            });
        }

        private void setupCameraManager() {
            doAnswer(invocation -> {
                mCameraAvailabilityCallback = invocation.getArgument(1);
                return null;
            }).when(mMockCameraManager).registerAvailabilityCallback(
                    any(Executor.class), any(CameraManager.AvailabilityCallback.class));

            doReturn(mMockCameraManager).when(mWindowTestsBase.mWm.mContext).getSystemService(
                    CameraManager.class);
            setupSupportedRotateAndCropModes(new int[]{
                    SCALER_ROTATE_AND_CROP_NONE,
                    SCALER_ROTATE_AND_CROP_90,
                    SCALER_ROTATE_AND_CROP_180,
                    SCALER_ROTATE_AND_CROP_270,
                    SCALER_ROTATE_AND_CROP_AUTO});
        }

        private void setupSupportedRotateAndCropModes(int[] rotateAndCropModes) {
            final CameraCharacteristics cameraCharacteristics = mock(CameraCharacteristics.class);
            doReturn(rotateAndCropModes).when(cameraCharacteristics).get(
                    CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES);
            try {
                doReturn(cameraCharacteristics).when(mMockCameraManager)
                        .getCameraCharacteristics(anyString());
            } catch (Exception e) {
                throw new AssertionError("Unable to setup supported camera compat modes.", e);
            }
        }

        private void setupHandler() {
            final Handler handler = activity().top().mWmService.mH;
            spyOn(handler);

            doAnswer(invocation -> {
                ((Runnable) invocation.getArgument(0)).run();
                return null;
            }).when(handler).postDelayed(any(Runnable.class), anyLong());
        }

        private void configureActivityAndDisplay() {
            applyOnActivity(a -> {
                a.createActivityWithComponentInNewTaskAndDisplay();
                a.setIgnoreOrientationRequest(true);
                spyOn(a.top().mAppCompatController.getCameraOverrides());
                spyOn(a.top().info);
                doReturn(a.displayContent().getDisplayInfo()).when(
                        a.displayContent().mWmService.mDisplayManagerInternal).getDisplayInfo(
                        a.displayContent().mDisplayId);
            });
        }

        private void addPolicyThatCanClose() {
            getAppCompatCameraStateSource().addCameraStatePolicy(mFakePolicyCanClose);
        }

        private void addPolicyThatCannotCloseOnce() {
            getAppCompatCameraStateSource().addCameraStatePolicy(mFakePolicyCannotCloseOnce);
        }

        private void onCameraOpened(@NonNull String cameraId, @NonNull String packageName) {
            mCameraAvailabilityCallback.onCameraOpened(cameraId, packageName);
            waitHandlerIdle();
        }

        private void onCameraClosed(@NonNull String cameraId) {
            mCameraAvailabilityCallback.onCameraClosed(cameraId);
        }

        private void checkCameraRegisteresAsOpenedForCanClosePolicy(boolean expectedIsOpened) {
            assertEquals(expectedIsOpened, getCameraStateMonitor().isCameraRunningForActivity(
                    activity().top()));
        }

        private void checkCameraOpenedCalledForCanClosePolicy(int times) {
            assertEquals(times, mFakePolicyCanClose.mOnCameraOpenedCounter);
        }

        private void checkCanCloseCalledForCanClosePolicy(int times) {
            assertEquals(times, mFakePolicyCanClose.mCheckCanCloseCounter);
        }

        private void checkCanCloseCalledForCannotCloseOncePolicy(int times) {
            assertEquals(times, mFakePolicyCannotCloseOnce.mCheckCanCloseCounter);
        }

        private void checkCameraClosedCalledForCanClosePolicy(int times) {
            assertEquals(times, mFakePolicyCanClose.mOnCameraClosedCounter);
        }

        private void checkCameraClosedCalledForCannotCloseOncePolicy(int times) {
            assertEquals(times, mFakePolicyCannotCloseOnce.mOnCameraClosedCounter);
        }

        private void checkIsRotateAndCropModeSupported(int rotateAndCropMode, boolean expected) {
            assertEquals(expected, getCameraStateMonitor().isRotateAndCropModeSupported(
                    activity().top(), rotateAndCropMode));
        }

        private void waitHandlerIdle() {
            mWindowTestsBase.waitHandlerIdle(testBase().mWm.mH);
        }

        private CameraStateMonitor getCameraStateMonitor() {
            return activity().top().mWmService.mAppCompatCameraPolicy.mCameraStateMonitor;
        }

        private AppCompatCameraStateSource getAppCompatCameraStateSource() {
            return ((AppCompatCameraStateSource) getCameraStateMonitor()
                    .mAppCompatCameraStatePolicy);
        }
    }
}
