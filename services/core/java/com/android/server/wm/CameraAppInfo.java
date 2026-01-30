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

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.Objects;

/** Data class to track apps and tasks which opened camera, for camera compat mode. */
final class CameraAppInfo {

    // ID of the camera the app is using.
    @NonNull
    final String mCameraId;

    // Process ID for the app which opened the camera.
    final int mPid;

    // Task ID for the app which opened the camera.
    final int mTaskId;

    // TODO(b/380840084): remove after refactoring flag is launched.
    @Nullable
    final String mPackageName;

    CameraAppInfo(@NonNull String cameraId, int pid, int taskId, @Nullable String packageName) {
        mCameraId = cameraId;
        mPid = pid;
        mTaskId = taskId;
        mPackageName = packageName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCameraId, mPid, mTaskId, mPackageName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CameraAppInfo other)) {
            return false;
        }
        return mPid == other.mPid && mTaskId == other.mTaskId
                && Objects.equals(mCameraId, other.mCameraId)
                && Objects.equals(mPackageName, other.mPackageName);
    }

    @Override
    public String toString() {
        return "{mCameraId=" + mCameraId + ", mPid=" + mPid + ", mTaskId=" + mTaskId
                + ", mPackageName=" + mPackageName + "}";
    }
}
