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

import android.annotation.Nullable;

import androidx.annotation.NonNull;

/** Fake {@link AppCompatCameraStatePolicy} for testing. */
public class FakeAppCompatCameraStatePolicy implements AppCompatCameraStatePolicy {
    int mOnCameraOpenedCounter = 0;
    int mCheckCanCloseCounter = 0;
    int mOnCameraClosedCounter = 0;

    private boolean mCheckCanCloseReturnValue;

    /**
     * @param simulateCannotCloseOnce When false, returns `true` on every
     *                                      `checkCanClose`. When true, returns `false` on the
     *                                      first `checkCanClose` callback, and `true on the
     *                                      subsequent calls. This fake implementation tests the
     *                                      retry mechanism in {@link CameraStateMonitor}.
     */
    FakeAppCompatCameraStatePolicy(boolean simulateCannotCloseOnce) {
        mCheckCanCloseReturnValue = !simulateCannotCloseOnce;
    }

    @Override
    public void onCameraOpened(@NonNull WindowProcessController appProcess, @NonNull Task task) {
        mOnCameraOpenedCounter++;
    }

    @Override
    public boolean canCameraBeClosed(@NonNull String cameraId, @NonNull Task task) {
        mCheckCanCloseCounter++;
        final boolean returnValue = mCheckCanCloseReturnValue;
        // If false, return false only the first time, so it doesn't fall in the infinite retry
        // loop.
        mCheckCanCloseReturnValue = true;
        return returnValue;
    }

    @Override
    public void onCameraClosed(@Nullable WindowProcessController appProcess, @Nullable Task task) {
        mOnCameraClosedCounter++;
    }
}
