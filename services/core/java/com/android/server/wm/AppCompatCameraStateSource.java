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

import java.util.ArrayList;

/**
 * Adapter for camera state updates, which notifies all camera policies of camera state changes.
 */
class AppCompatCameraStateSource implements AppCompatCameraStatePolicy {
    private final ArrayList<AppCompatCameraStatePolicy> mCameraStatePolicies = new ArrayList<>();

    /** Adds a policy to notify when camera is opened and closed. */
    public void addCameraStatePolicy(@NonNull AppCompatCameraStatePolicy policy) {
        mCameraStatePolicies.add(policy);
    }

    /** Removes a policy to notify about camera opened/closed signals. */
    public void removeCameraStatePolicy(@NonNull AppCompatCameraStatePolicy policy) {
        mCameraStatePolicies.remove(policy);
    }

    @Override
    public void onCameraOpened(@NonNull WindowProcessController appProcess, @NonNull Task task) {
        for (int i = 0; i < mCameraStatePolicies.size(); i++) {
            mCameraStatePolicies.get(i).onCameraOpened(appProcess, task);
        }
    }

    /**
     * @return {@code false} if any listener has reported that they cannot process camera close now.
     */
    @Override
    public boolean canCameraBeClosed(@NonNull String cameraId, @NonNull Task task) {
        for (int i = 0; i < mCameraStatePolicies.size(); i++) {
            if (!mCameraStatePolicies.get(i).canCameraBeClosed(cameraId, task)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onCameraClosed(@Nullable WindowProcessController appProcess, @Nullable Task task) {
        for (int i = 0; i < mCameraStatePolicies.size(); i++) {
            mCameraStatePolicies.get(i).onCameraClosed(appProcess, task);
        }
    }
}
