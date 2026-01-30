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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link CameraIdAppPairsSet}.
 *
 * Build/Install/Run:
 * atest WmTests:CameraIdAppPairsSetTests
 */
@Presubmit
@RunWith(WindowTestRunner.class)
public class CameraIdAppPairsSetTests {
    private CameraIdAppPairsSet mMapping;

    private static final String TEST_PACKAGE_1 = "com.test.package.one";
    private static final String TEST_PACKAGE_2 = "com.test.package.two";
    private static final int TASK_ID_1 = 1;
    private static final int TASK_ID_2 = 2;
    private static final int PID_1 = 101;
    private static final int PID_2 = 102;
    private static final String CAMERA_ID_1 = "1234";
    private static final String CAMERA_ID_2 = "5678";
    private final CameraAppInfo mCameraAppInfo1Camera1 =
            new CameraAppInfo(CAMERA_ID_1, PID_1, TASK_ID_1, TEST_PACKAGE_1);
    private final CameraAppInfo mCameraAppInfo1Camera2 =
            new CameraAppInfo(CAMERA_ID_2, PID_1, TASK_ID_1, TEST_PACKAGE_1);
    private final CameraAppInfo mCameraAppInfo2Camera1 =
            new CameraAppInfo(CAMERA_ID_1, PID_2, TASK_ID_2, TEST_PACKAGE_2);
    private final CameraAppInfo mCameraAppInfo2Camera2 =
            new CameraAppInfo(CAMERA_ID_2, PID_2, TASK_ID_2, TEST_PACKAGE_2);

    @Before
    public void setUp() {
        mMapping = new CameraIdAppPairsSet();
    }

    @Test
    public void mappingEmptyAtStart() {
        assertTrue(mMapping.isEmpty());
    }

    @Test
    public void addTaskAndCameraId_containsCameraIdAndTask() {
        mMapping.add(mCameraAppInfo1Camera1);

        assertTrue(mMapping.containsCameraIdAndTask(CAMERA_ID_1, mCameraAppInfo1Camera1.mTaskId));
    }

    @Test
    public void addTwoTasksAndCameraIds_containsCameraIdsAndTasks() {
        mMapping.add(mCameraAppInfo1Camera1);
        mMapping.add(mCameraAppInfo2Camera2);

        assertTrue(mMapping.containsCameraIdAndTask(CAMERA_ID_1, mCameraAppInfo1Camera1.mTaskId));
        assertTrue(mMapping.containsCameraIdAndTask(CAMERA_ID_2, mCameraAppInfo2Camera2.mTaskId));
    }

    @Test
    public void addTwoTasksAndCameraIds_checkContainsCameraIdAndTaskFromDifferentPair_false() {
        mMapping.add(mCameraAppInfo1Camera1);
        mMapping.add(mCameraAppInfo2Camera2);

        assertFalse(mMapping.containsCameraIdAndTask(CAMERA_ID_1, mCameraAppInfo2Camera2.mTaskId));
    }

    @Test
    public void addTwoTasksForTheSameCamera_bothExist() {
        mMapping.add(mCameraAppInfo1Camera1);
        mMapping.add(mCameraAppInfo2Camera2);

        assertTrue(mMapping.containsCameraIdAndTask(CAMERA_ID_1, mCameraAppInfo1Camera1.mTaskId));
        assertTrue(mMapping.containsCameraIdAndTask(CAMERA_ID_2, mCameraAppInfo2Camera2.mTaskId));
    }

    @Test
    public void addTwoCamerasForTheSameTask_bothExist() {
        mMapping.add(mCameraAppInfo1Camera1);
        mMapping.add(mCameraAppInfo1Camera2);

        assertTrue(mMapping.containsCameraIdAndTask(CAMERA_ID_1, mCameraAppInfo1Camera1.mTaskId));
        assertTrue(mMapping.containsCameraIdAndTask(CAMERA_ID_2, mCameraAppInfo1Camera2.mTaskId));
    }

    @Test
    public void addTwoTasksForTheSameCamera_returnsAnyTask() {
        mMapping.add(mCameraAppInfo1Camera1);
        mMapping.add(mCameraAppInfo2Camera1);

        assertNotNull(mMapping.getAnyCameraAppStateForCameraId(CAMERA_ID_1));
    }

    @Test
    public void addTwoCamerasForTheSameTask_containsAnyCamera() {
        mMapping.add(mCameraAppInfo1Camera1);
        mMapping.add(mCameraAppInfo1Camera2);

        assertTrue(mMapping.containsAnyCameraForTaskId(mCameraAppInfo1Camera1.mTaskId));
    }

    @Test
    public void addAndRemoveCameraId_containsOtherCameraAndTask() {
        mMapping.add(mCameraAppInfo1Camera1);
        mMapping.add(mCameraAppInfo2Camera2);

        mMapping.remove(mCameraAppInfo1Camera1);

        assertTrue(mMapping.containsCameraIdAndTask(CAMERA_ID_2, mCameraAppInfo2Camera2.mTaskId));
    }

    @Test
    public void addAndRemoveOnlyCameraId_empty() {
        mMapping.add(mCameraAppInfo1Camera1);
        mMapping.remove(mCameraAppInfo1Camera1);

        assertTrue(mMapping.isEmpty());
    }

    @Test
    public void addAndRemoveOnlyCameraIdUsingEqualObject_empty() {
        mMapping.add(mCameraAppInfo1Camera1);
        mMapping.remove(new CameraAppInfo(mCameraAppInfo1Camera1.mCameraId,
                mCameraAppInfo1Camera1.mPid, mCameraAppInfo1Camera1.mTaskId,
                mCameraAppInfo1Camera1.mPackageName));

        assertTrue(mMapping.isEmpty());
    }
}
