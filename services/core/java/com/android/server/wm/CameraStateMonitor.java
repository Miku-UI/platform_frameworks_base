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

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_CAMERA_COMPAT;
import static com.android.server.wm.AppCompatCameraPolicy.TAG_CAMERA_COMPAT;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Class that listens to camera open/closed signals, keeps track of the current apps using camera,
 * and notifies listeners.
 */
class CameraStateMonitor {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "CameraStateMonitor" : TAG_WM;
    // Delay for updating letterbox after Camera connection is closed. Needed to avoid flickering
    // when an app is flipping between front and rear cameras or when size compat mode is restarted.
    // TODO(b/330148095): Investigate flickering without using delays, remove them if possible.
    private static final int CAMERA_CLOSED_LETTERBOX_UPDATE_DELAY_MS = 2000;
    // Delay for updating letterboxing after Camera connection is opened. This delay is selected to
    // be long enough to avoid conflicts with transitions on the app's side.
    // Using a delay < CAMERA_CLOSED_ROTATION_UPDATE_DELAY_MS to avoid flickering when an app
    // is flipping between front and rear cameras (in case requested orientation changes at
    // runtime at the same time) or when size compat mode is restarted.
    // TODO(b/330148095): Investigate flickering without using delays, remove them if possible.
    private static final int CAMERA_OPENED_LETTERBOX_UPDATE_DELAY_MS =
            CAMERA_CLOSED_LETTERBOX_UPDATE_DELAY_MS / 2;

    /** Returns the information about apps using camera, for logging purposes. */
    @NonNull
    private final WindowManagerService mWmService;
    @Nullable
    private final CameraManager mCameraManager;
    @NonNull
    private final Handler mHandler;

    @VisibleForTesting
    final AppCompatCameraStateStrategyForTask mAppCompatCameraStateStrategy;
    @VisibleForTesting
    final AppCompatCameraStatePolicy mAppCompatCameraStatePolicy;

    /** Available rotateAndCropModes per cameraId. */
    private final HashMap<String, ArrayList<Integer>> mAvailableRotateAndCropModesForCamera =
            new HashMap<>();

    /**
     * Value toggled on {@link #startListeningToCameraState()} to {@code true} and on {@link
     * #stopListeningToCameraState()} to {@code false}.
     */
    private boolean mIsListeningToCameraState;

    private final CameraManager.AvailabilityCallback mAvailabilityCallback =
            new  CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraOpened(@NonNull String cameraId, @NonNull String packageId) {
                    synchronized (mWmService.mGlobalLock) {
                        notifyCameraOpenedWithDelay(cameraId, packageId);
                    }
                }
                @Override
                public void onCameraClosed(@NonNull String cameraId) {
                    synchronized (mWmService.mGlobalLock) {
                        notifyCameraClosedWithDelay(cameraId);
                    }
                }
            };

    CameraStateMonitor(@NonNull WindowManagerService wmService, @NonNull Handler handler,
            @NonNull AppCompatCameraStatePolicy appCompatCameraStatePolicy) {
        // This constructor is called from WindowManagerService constructor.
        mHandler = handler;
        mAppCompatCameraStatePolicy = appCompatCameraStatePolicy;
        mWmService = wmService;
        mCameraManager = mWmService.mContext.getSystemService(CameraManager.class);
        mAppCompatCameraStateStrategy = new AppCompatCameraStateStrategyForTask(mWmService);
    }

    /** Starts listening to camera opened/closed signals. */
    void startListeningToCameraState() {
        if (mCameraManager != null) {
            mCameraManager.registerAvailabilityCallback(
                    mWmService.mContext.getMainExecutor(), mAvailabilityCallback);
        }
        mIsListeningToCameraState = true;
    }

    /** Stops listening to camera opened/closed signals. */
    void stopListeningToCameraState() {
        if (mCameraManager != null) {
            mCameraManager.unregisterAvailabilityCallback(mAvailabilityCallback);
        }
        mIsListeningToCameraState = false;
    }

    /**
     * Returns whether {@link CameraStateMonitor} is listening to camera opened/closed
     * signals.
     */
    @VisibleForTesting
    boolean isListeningToCameraState() {
        return mIsListeningToCameraState;
    }

    void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        pw.println(prefix + "CameraStateMonitor:");
        pw.println(prefix + "  activeCameraConnections=" + mAppCompatCameraStateStrategy);
        pw.println(prefix + "  mAvailableRotateAndCropModesForCamera="
                + mAvailableRotateAndCropModesForCamera);
    }

    private void notifyCameraOpenedWithDelay(@NonNull String cameraId,
            @NonNull String packageName) {
        // Some apps can’t handle configuration changes coming at the same time with Camera setup so
        // delaying orientation update to accommodate for that.
        // If an activity is restarting or camera is flipping, the camera connection can be
        // quickly closed and reopened.
        ProtoLog.v(WM_DEBUG_CAMERA_COMPAT, "%s: Camera %s is open for package %s",
                TAG_CAMERA_COMPAT, cameraId, packageName);
        final CameraAppInfo cameraAppInfo = mAppCompatCameraStateStrategy.trackOnCameraOpened(
                cameraId, packageName);
        mHandler.postDelayed(() -> {
            synchronized (mWmService.mGlobalLock) {
                notifyCameraOpenedInternal(cameraAppInfo);
            }}, CAMERA_OPENED_LETTERBOX_UPDATE_DELAY_MS);
    }

    private void notifyCameraOpenedInternal(@NonNull CameraAppInfo cameraAppInfo) {
        mAppCompatCameraStateStrategy.notifyPolicyCameraOpenedIfNeeded(cameraAppInfo,
                mAppCompatCameraStatePolicy);
    }

    /**
     * Processes camera closed, and schedules notifying listeners.
     *
     * <p>The delay is introduced to avoid flickering when switching between front and back camera,
     * and when an activity is refreshed due to camera compat treatment.
     */
    private void notifyCameraClosedWithDelay(@NonNull String cameraId) {
        ProtoLog.v(WM_DEBUG_CAMERA_COMPAT, "%s: Camera %s is closed.", TAG_CAMERA_COMPAT,
                cameraId);
        scheduleRemoveCameraId(cameraId);
    }

    /** Returns whether a given activity holds any camera opened. */
    boolean isCameraRunningForActivity(@NonNull ActivityRecord activity) {
        return mAppCompatCameraStateStrategy.isCameraRunningForActivity(activity);
    }

    /** Returns whether a given activity holds a specific camera opened. */
    // TODO(b/336474959): try to decouple `cameraId` from the listeners.
    boolean isCameraWithIdRunningForActivity(@NonNull ActivityRecord activity,
            @NonNull String cameraId) {
        return mAppCompatCameraStateStrategy.isCameraWithIdRunningForActivity(activity, cameraId);
    }

    // Delay is needed to avoid rotation flickering when an app is flipping between front and
    // rear cameras, when size compat mode is restarted or activity is being refreshed.
    private void scheduleRemoveCameraId(@NonNull String cameraId) {
        final CameraAppInfo cameraAppInfo =
                mAppCompatCameraStateStrategy.trackOnCameraClosed(cameraId);
        mHandler.postDelayed(() ->  {
            synchronized (mWmService.mGlobalLock) {
                removeCameraId(cameraAppInfo);
            }}, CAMERA_CLOSED_LETTERBOX_UPDATE_DELAY_MS);
    }

    private void removeCameraId(@NonNull CameraAppInfo cameraAppInfo) {
        final boolean completed = mAppCompatCameraStateStrategy.notifyPolicyCameraClosedIfNeeded(
                cameraAppInfo, mAppCompatCameraStatePolicy);
        if (!completed) {
            // Not ready to process closure yet - the camera activity might be refreshing.
            // Try again later.
            scheduleRemoveCameraId(cameraAppInfo.mCameraId);
        }
    }

    /**
     * Checks whether the rotate-and-crop mode - a certain number of degrees - is supported on
     * Camera HAL.
     *
     * <p>This method with find which camera does the current activity use, and check supported
     * rotate-and-crop degrees.
     */
    boolean isRotateAndCropModeSupported(@NonNull ActivityRecord activity, int rotateAndCropMode) {
        final String cameraId = mAppCompatCameraStateStrategy.getActiveCameraId(activity);
        return cameraId != null && isRotateAndCropModeSupported(cameraId, rotateAndCropMode);
    }

    private boolean isRotateAndCropModeSupported(@NonNull String cameraId, int rotateAndCropMode) {
        if (!fetchAvailableRotateAndCropModes(cameraId)) {
            return false;
        }

        return mAvailableRotateAndCropModesForCamera.get(cameraId).contains(rotateAndCropMode);
    }

    private boolean fetchAvailableRotateAndCropModes(@NonNull String cameraId) {
        if (!mAvailableRotateAndCropModesForCamera.containsKey(cameraId)) {
            try {
                final int[] availableRotateAndCropModes = mCameraManager
                        .getCameraCharacteristics(cameraId)
                        .get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES);
                final ArrayList<Integer> modes = new ArrayList<>();
                for (int i = 0; i < availableRotateAndCropModes.length; i++) {
                    modes.add(availableRotateAndCropModes[i]);
                }
                mAvailableRotateAndCropModesForCamera.put(cameraId, modes);
            } catch (IllegalArgumentException | CameraAccessException e) {
                Slog.w(TAG, "Unable to access camera to check available rotate-and-crop modes.");
                return false;
            }
        }

        return true;
    }

    @NonNull
    @Override
    public String toString() {
        return mAppCompatCameraStateStrategy.toString();
    }
}
