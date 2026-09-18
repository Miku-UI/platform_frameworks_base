/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.camera;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.job.JobScheduler;
import android.content.Context;
import android.hardware.CameraSessionStats;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.util.StatsEvent;
import android.util.StatsEventTestUtils;
import android.util.StatsLog;
import android.view.Display;
import android.view.Surface;

import androidx.test.InstrumentationRegistry;

import com.android.internal.R;
import com.android.internal.util.FrameworkStatsLog;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.os.AtomsProto.Atom;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class CameraServiceProxyTest {
    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(StatsLog.class).build();

    @Captor ArgumentCaptor<StatsEvent> mStatsEventCaptor;
    @Mock private JobScheduler mMockJobScheduler;

    private static final long SLEEP_TIME_MS = 1000;

    @Test
    public void testGetCropRotateScale() {

        Context ctx = InstrumentationRegistry.getContext();
        if (ctx.getResources().getBoolean(
                    R.bool.config_isWindowManagerCameraCompatTreatmentEnabled)) {
            //'getCropRotateScale' is overridden in case the WM Camera compat treatment
            //is enabled.
            return;
        }

        // Check resizeability and SDK
        CameraServiceProxy.TaskInfo taskInfo = new CameraServiceProxy.TaskInfo();
        taskInfo.isResizeable = true;
        taskInfo.displayId = Display.DEFAULT_DISPLAY;
        taskInfo.isFixedOrientationLandscape = false;
        taskInfo.isFixedOrientationPortrait = true;
        // Resizeable apps should be ignored
        assertThat(CameraServiceProxy.getCropRotateScale(ctx, ctx.getPackageName(), taskInfo,
                Surface.ROTATION_90 , CameraCharacteristics.LENS_FACING_BACK,
                /*ignoreResizableAndSdkCheck*/false)).isEqualTo(
                CameraMetadata.SCALER_ROTATE_AND_CROP_NONE);
        // Resizeable apps will be considered in case the ignore flag is set
        assertThat(CameraServiceProxy.getCropRotateScale(ctx, ctx.getPackageName(), taskInfo,
                Surface.ROTATION_90, CameraCharacteristics.LENS_FACING_BACK,
                /*ignoreResizableAndSdkCheck*/true)).isEqualTo(
                CameraMetadata.SCALER_ROTATE_AND_CROP_90);
        taskInfo.isResizeable = false;
        // Non-resizeable apps should be considered
        assertThat(CameraServiceProxy.getCropRotateScale(ctx, ctx.getPackageName(), taskInfo,
                Surface.ROTATION_90, CameraCharacteristics.LENS_FACING_BACK,
                /*ignoreResizableAndSdkCheck*/false)).isEqualTo(
                CameraMetadata.SCALER_ROTATE_AND_CROP_90);
        // The ignore flag for non-resizeable should have no effect
        assertThat(CameraServiceProxy.getCropRotateScale(ctx, ctx.getPackageName(), taskInfo,
                Surface.ROTATION_90, CameraCharacteristics.LENS_FACING_BACK,
                /*ignoreResizableAndSdkCheck*/true)).isEqualTo(
                CameraMetadata.SCALER_ROTATE_AND_CROP_90);
        // Non-fixed orientation should be ignored
        taskInfo.isFixedOrientationLandscape = false;
        taskInfo.isFixedOrientationPortrait = false;
        assertThat(CameraServiceProxy.getCropRotateScale(ctx, ctx.getPackageName(), taskInfo,
                Surface.ROTATION_90, CameraCharacteristics.LENS_FACING_BACK,
                /*ignoreResizableAndSdkCheck*/true)).isEqualTo(
                CameraMetadata.SCALER_ROTATE_AND_CROP_NONE);
        // Check rotation and lens facing combinations
        Map<Integer, Integer> backFacingMap = Map.of(
                Surface.ROTATION_0, CameraMetadata.SCALER_ROTATE_AND_CROP_NONE,
                Surface.ROTATION_90, CameraMetadata.SCALER_ROTATE_AND_CROP_90,
                Surface.ROTATION_270, CameraMetadata.SCALER_ROTATE_AND_CROP_270,
                Surface.ROTATION_180, CameraMetadata.SCALER_ROTATE_AND_CROP_180);
        taskInfo.isFixedOrientationPortrait = true;
        backFacingMap.forEach((key, value) -> {
            assertThat(CameraServiceProxy.getCropRotateScale(ctx, ctx.getPackageName(), taskInfo,
                    key, CameraCharacteristics.LENS_FACING_BACK,
                    /*ignoreResizableAndSdkCheck*/true)).isEqualTo(value);
        });
        Map<Integer, Integer> frontFacingMap = Map.of(
                Surface.ROTATION_0, CameraMetadata.SCALER_ROTATE_AND_CROP_NONE,
                Surface.ROTATION_90, CameraMetadata.SCALER_ROTATE_AND_CROP_270,
                Surface.ROTATION_270, CameraMetadata.SCALER_ROTATE_AND_CROP_90,
                Surface.ROTATION_180, CameraMetadata.SCALER_ROTATE_AND_CROP_180);
        frontFacingMap.forEach((key, value) -> {
            assertThat(CameraServiceProxy.getCropRotateScale(ctx, ctx.getPackageName(), taskInfo,
                    key, CameraCharacteristics.LENS_FACING_FRONT,
                    /*ignoreResizableAndSdkCheck*/true)).isEqualTo(value);
        });
    }

    @Test
    public void testLogErrorState() throws Exception {
        Context testContext = InstrumentationRegistry.getContext();
        Context ctx = spy(testContext);
        CameraServiceProxy cameraServiceProxy = new CameraServiceProxy(ctx);
        when(ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE))
                .thenReturn(mMockJobScheduler);

        when(mMockJobScheduler.schedule(any())).thenReturn(1);

        Class<?> currentClass = this.getClass();
        Package currentPackage = currentClass.getPackage();
        String packageName = "testingPackageName";
        if (currentPackage != null) {
            packageName = currentPackage.getName();
        }

        CameraSessionStats cameraSessionStats = new CameraSessionStats("0", 1,
                3, packageName, 2, false, 917,
                0, 0, 0, 6379409415148806677L,
                2,
                FrameworkStatsLog.CAMERA_ACTION_EVENT__ERROR_STATE__CAMERA_HAL_REQUEST_ERROR,
                /*sharedMode*/ false);

        int usageEventSize = cameraServiceProxy.getUsageEventCount();
        if (usageEventSize > 0) {
            cameraServiceProxy.dumpCameraEvents();
            sleepForSomeTIme(SLEEP_TIME_MS);
        }
        cameraServiceProxy.updateActivityCount(cameraSessionStats);

        cameraServiceProxy.dumpCameraEvents();
        sleepForSomeTIme(SLEEP_TIME_MS);
        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), atLeastOnce());
        Atom atom = StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue());

        assertThat(atom.hasCameraActionEvent()).isTrue();
        assertThat(atom.getCameraActionEvent().getPackageName()).isEqualTo(packageName);
        assertThat(atom.getCameraActionEvent().getErrorState().getNumber())
                .isEqualTo(FrameworkStatsLog
                        .CAMERA_ACTION_EVENT__ERROR_STATE__CAMERA_HAL_REQUEST_ERROR);
    }

    private void sleepForSomeTIme(long sleepTime) {
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            latch.await(sleepTime, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
