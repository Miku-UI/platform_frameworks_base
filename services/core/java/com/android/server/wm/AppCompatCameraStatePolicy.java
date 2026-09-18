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

/**
 * Interface that camera compat policies to implement to be notified of camera open/close signals.
 */
interface AppCompatCameraStatePolicy {
    /**
     * Notifies the compat listener that a task has opened camera.
     *
     * @param cameraAppInfo Contains the process ID and task ID which requested camera to be opened.
     */
    void onCameraOpened(@NonNull CameraAppInfo cameraAppInfo,
            @NonNull WindowProcessController appProcess,
            @NonNull Task cameraTask);

    /**
     * Checks whether a listener is ready to do a cleanup when camera is closed.
     *
     * <p>The notifier might try again if false is returned.
     */
    boolean canCameraBeClosed(@NonNull CameraAppInfo cameraAppInfo, @NonNull Task task);

    /**
     * Notifies the compat listener that camera is closed.
     *
     * <p>Due to delays in notifying that camera is closed (currently 2 seconds), and the cause of
     * camera close could the that the task is closed, the app process and/or task might be null. As
     * parts of camera compat are done on activity level, application level, or even camera compat
     * policy level, the policies are notified even if the task or app are not active anymore.
     *
     * @param cameraAppInfo the info that was originally sent on {@link #onCameraOpened}.
     */
    void onCameraClosed(@NonNull CameraAppInfo cameraAppInfo,
            @Nullable WindowProcessController appProcess,
            @Nullable Task cameraTask);
}
