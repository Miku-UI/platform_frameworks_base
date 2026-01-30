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
import android.util.ArraySet;

/**
 * Data set for the currently active cameraId and the app that opened it.
 *
 * <p>This class is not thread-safe.
 */
final class CameraIdAppPairsSet {
    private final ArraySet<CameraAppInfo> mCameraAppInfoSet = new ArraySet<>();

    boolean isEmpty() {
        return mCameraAppInfoSet.isEmpty();
    }

    void add(@NonNull CameraAppInfo cameraAppInfo) {
        mCameraAppInfoSet.add(cameraAppInfo);
    }

    boolean containsAnyCameraForTaskId(int taskId) {
        for (int i = 0; i < mCameraAppInfoSet.size(); i++) {
            final CameraAppInfo info = mCameraAppInfoSet.valueAt(i);
            if (info.mTaskId == taskId) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    CameraAppInfo getAnyCameraAppStateForCameraId(@NonNull String cameraId) {
        for (int i = 0; i < mCameraAppInfoSet.size(); i++) {
            final CameraAppInfo info = mCameraAppInfoSet.valueAt(i);
            if (info.mCameraId.equals(cameraId)) {
                return info;
            }
        }
        return null;
    }

    boolean containsCameraIdAndTask(@NonNull String cameraId, int taskId) {
        for (int i = 0; i < mCameraAppInfoSet.size(); i++) {
            final CameraAppInfo info = mCameraAppInfoSet.valueAt(i);
            if (info.mCameraId.equals(cameraId) && info.mTaskId == taskId) {
                return true;
            }
        }
        return false;
    }

    boolean remove(@NonNull CameraAppInfo cameraAppInfo) {
        return mCameraAppInfoSet.remove(cameraAppInfo);
    }

    @NonNull
    String getSummaryForDisplayRotationHistoryRecord() {
        return "{ mCameraAppInfoSet=" + mCameraAppInfoSet + " }";
    }

    @Override
    public String toString() {
        return getSummaryForDisplayRotationHistoryRecord();
    }
}
