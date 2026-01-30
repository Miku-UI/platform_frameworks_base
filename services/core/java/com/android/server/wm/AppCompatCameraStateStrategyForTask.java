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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.os.Process.INVALID_PID;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_STATES;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// TODO(b/442299565): Rename this class once the other strategies have been removed.
/** Class that tracks task-cameraId (and app) pairs for camera compat. */
class AppCompatCameraStateStrategyForTask {
    // Data set for app data and active camera IDs since we need to 1) get a camera id by a task
    // when setting up camera compat mode; 2) get a task by a camera id when camera connection is
    // closed and we need to clean up our records.
    private final CameraIdAppPairsSet mCameraAppInfoSet = new CameraIdAppPairsSet();

    // Repository for the newest camera state update. Camera opened and closed signals are processed
    // with a delay. In case of different signals/states pending without being processed, only the
    // newest state will be processed, and the old overwritten.
    private final PendingCameraUpdateRepository mPendingCameraUpdateRepository =
            new PendingCameraUpdateRepository();

    @NonNull
    private final DisplayContent mDisplayContent;

    AppCompatCameraStateStrategyForTask(@NonNull DisplayContent displayContent) {
        mDisplayContent = displayContent;
    }

    /**
     * Allows saving information: task, process, cameraId, to be processed later on
     * {@link AppCompatCameraStateStrategyForTask#notifyPolicyCameraOpenedIfNeeded} after a delay.
     *
     * <p>The {@link AppCompatCameraStateStrategyForTask} should track which camera operations have
     * been started (delayed), as camera opened/closed operations often compete with each other, and
     * due to built-in delays can cause different order of these operations when they are finally
     * processed. Examples of quickly closing and opening the camera: activity relaunch due to
     * configuration change, switching front/back cameras, new app requesting camera and taking the
     * access rights away from the existing camera app.
     *
     * @return CameraAppInfo of the app which opened the camera with given cameraId.
     */
    @NonNull
    public CameraAppInfo trackOnCameraOpened(@NonNull String cameraId,
            @NonNull String packageName) {
        final CameraAppInfo cameraAppInfo = createCameraAppInfo(cameraId, packageName);
        ProtoLog.v(WM_DEBUG_STATES,
                "Display id=%d is notified that Camera %s is open for package %s",
                mDisplayContent.mDisplayId, cameraId, packageName);
        mPendingCameraUpdateRepository.trackPendingCameraOpen(cameraAppInfo);
        return cameraAppInfo;
    }

    /**
     * Processes camera opened signal, and if the change is relevant for {@link
     * AppCompatCameraStatePolicy} calls {@link AppCompatCameraStatePolicy#onCameraOpened}.
     */    public void notifyPolicyCameraOpenedIfNeeded(@NonNull CameraAppInfo cameraAppInfo,
            @NonNull AppCompatCameraStatePolicy policy) {
        if (!mPendingCameraUpdateRepository.removePendingCameraOpen(cameraAppInfo)) {
            // Camera compat mode update has happened already or was cancelled
            // because camera was closed.
            return;
        }

        final WindowProcessController cameraApp = getAppProcessForCallingId(cameraAppInfo.mPid);

        if (cameraApp == null) {
            return;
        }
        final ActivityRecord cameraActivity = findUniqueActivityWithPackageName(
                cameraApp.mInfo.packageName);

        if (cameraActivity == null || cameraActivity.getTask() == null) {
            return;
        }

        // TODO(b/423883666): Use `WM_DEBUG_CAMERA_COMPAT`.
        ProtoLog.v(WM_DEBUG_STATES,
                "CameraOpen: cameraApp=%s cameraInfo.mPid=%d cameraTask=%s "
                        + "cameraAppInfo.mTaskId=%d",
                cameraApp, cameraAppInfo.mPid, cameraActivity.getTask(), cameraAppInfo.mTaskId);

        final boolean anyCameraAlreadyOpenForTask = mCameraAppInfoSet
                .containsAnyCameraForTaskId(cameraActivity.getTask().mTaskId);

        mCameraAppInfoSet.add(cameraAppInfo);
        if (!anyCameraAlreadyOpenForTask) {
            // Only notify listeners if the app has newly opened camera.
            // This does not currently support multiple camera tasks in a single app - this
            // would be a very rare use case (especially for targeted fixed-orientation apps).
            // Given that the camera framework notifies CameraStateMonitor with a packageName
            // and not a task or activity, it would be difficult to correctly and consistently
            // know which task has camera access.
            //
            // The above check is for whether the same task has opened camera, which usually
            // means either the camera was restarted due to config change, or because the app
            // switched between front and back cameras - either way this is not interesting for
            // camera policies.
            // Note: if any camera policy ever needs to dynamically change the treatment based
            // on the camera (front, back, external) this should notify when camera changes and
            // add a method policies can call to check if camera has been running (mostly used
            // to return early).
            policy.onCameraOpened(cameraApp, cameraActivity.getTask());
        }
    }

    /**
     * Allows saving information: task, process, cameraId, to be processed later on
     * {@link AppCompatCameraStateStrategyForTask#notifyPolicyCameraClosedIfNeeded} after a delay.
     *
     * <p>The {@link AppCompatCameraStateStrategyForTask} should track which camera operations have
     * beenstarted (delayed), as camera opened/closed operations often compete with each other, and
     * due to built-in delays can cause different order of these operations when they are finally
     * processed. Examples of quickly closing and opening the camera: activity relaunch due to
     * configuration change, switching front/back cameras, new app requesting camera and taking the
     * access rights away from the existing camera app.
     *
     * @return CameraAppInfo of the app which closed the camera with given cameraId.
     */
    @NonNull
    public CameraAppInfo trackOnCameraClosed(@NonNull String cameraId) {
        // This function is synchronous, and cameraClosed signal will come before cameraOpened.
        // Therefore, there will be only one app recorded with this camera opened.
        CameraAppInfo cameraAppInfo = mCameraAppInfoSet.getAnyCameraAppStateForCameraId(cameraId);
        if (cameraAppInfo == null) {
            ProtoLog.w(WM_DEBUG_STATES, "Camera closed but cannot find the app which had it"
                    + " opened.");
            cameraAppInfo = new CameraAppInfo(cameraId, INVALID_PID, INVALID_TASK_ID, null);
        }
        mPendingCameraUpdateRepository.trackPendingCameraClose(cameraAppInfo);
        return cameraAppInfo;
    }

    /**
     * Processes camera closed signal, and if the change is relevant for {@link
     * AppCompatCameraStatePolicy} calls {@link AppCompatCameraStatePolicy#onCameraClosed}.
     *
     * @return true if policies were able to handle the camera closed event, or false if it needs to
     * be rescheduled.
     */
    public boolean notifyPolicyCameraClosedIfNeeded(@NonNull CameraAppInfo cameraAppInfo,
            @NonNull AppCompatCameraStatePolicy policy) {
        if (!mPendingCameraUpdateRepository.removePendingCameraClose(cameraAppInfo)) {
            // Already reconnected to this camera, no need to clean up.
            return true;
        }

        final Task cameraTask = mDisplayContent.getTask(task ->
                task.getTaskInfo().taskId == cameraAppInfo.mTaskId);
        final boolean canClose = cameraTask == null
                || policy.canCameraBeClosed(cameraAppInfo.mCameraId, cameraTask);
        if (canClose) {
            // Finish cleaning up. Remove only cameraId of this particular task.
            mCameraAppInfoSet.remove(cameraAppInfo);
            if (!mCameraAppInfoSet.containsAnyCameraForTaskId(cameraAppInfo.mTaskId)) {
                final WindowProcessController app = getAppProcessForCallingId(
                        cameraAppInfo.mPid);
                // Only notify the listeners if the camera is not running - this close signal
                // could be from switching cameras (e.g. back to front camera, and vice versa).
                policy.onCameraClosed(app, cameraTask);
            }
        }

        return canClose;
    }

    /** Returns whether a given activity holds any camera opened. */
    public boolean isCameraRunningForActivity(@NonNull ActivityRecord activity) {
        return activity.getTask() != null && mCameraAppInfoSet
                .containsAnyCameraForTaskId(activity.getTask().mTaskId);
    }

    // TODO(b/336474959): try to decouple `cameraId` from the listeners.
    /** Returns whether a given activity holds a specific camera opened. */
    public boolean isCameraWithIdRunningForActivity(@NonNull ActivityRecord activity,
            @NonNull String cameraId) {
        return activity.getTask() != null && mCameraAppInfoSet
                .containsCameraIdAndTask(cameraId, activity.getTask().mTaskId);
    }

    @NonNull
    private CameraAppInfo createCameraAppInfo(@NonNull String cameraId,
            @Nullable String packageName) {
        final ActivityRecord cameraActivity = packageName == null ? null
                : findUniqueActivityWithPackageName(packageName);
        final Task cameraTask = cameraActivity == null ? null : cameraActivity.getTask();
        final WindowProcessController cameraApp = cameraActivity == null ? null
                : cameraActivity.app;
        return new CameraAppInfo(cameraId,
                cameraApp == null ? INVALID_PID : cameraApp.getPid(),
                cameraTask == null ? INVALID_TASK_ID : cameraTask.mTaskId,
                packageName);
    }

    // TODO(b/335165310): verify that this works in multi instance and permission dialogs.
    /**
     * Finds a visible activity with the given package name.
     *
     * <p>If there are multiple visible activities with a given package name, and none of them are
     * the `topRunningActivity`, returns null.
     */
    @Nullable
    private ActivityRecord findUniqueActivityWithPackageName(@NonNull String packageName) {
        final ActivityRecord topActivity = mDisplayContent.topRunningActivity(
                /* considerKeyguardState= */ true);
        if (topActivity != null && topActivity.packageName.equals(packageName)) {
            return topActivity;
        }

        final List<ActivityRecord> activitiesOfPackageWhichOpenedCamera = new ArrayList<>();
        mDisplayContent.forAllActivities(activityRecord -> {
            if (activityRecord.isVisibleRequested()
                    && activityRecord.packageName.equals(packageName)) {
                activitiesOfPackageWhichOpenedCamera.add(activityRecord);
            }
        });

        if (activitiesOfPackageWhichOpenedCamera.isEmpty()) {
            ProtoLog.w(WM_DEBUG_STATES, "Cannot find camera activity.");
            return null;
        }

        if (activitiesOfPackageWhichOpenedCamera.size() == 1) {
            return activitiesOfPackageWhichOpenedCamera.getFirst();
        }

        // Return null if we cannot determine which activity opened camera. This is preferred to
        // applying treatment to the wrong activity.
        ProtoLog.w(WM_DEBUG_STATES, "Cannot determine which activity opened camera.");
        return null;
    }

    @NonNull
    public String toString() {
        return " mCameraAppInfoSet=" + mCameraAppInfoSet
                .getSummaryForDisplayRotationHistoryRecord();
    }

    @Nullable
    private WindowProcessController getAppProcessForCallingId(int pid) {
        return mDisplayContent.mAtmService.mProcessMap.getProcess(pid);
    }

    /**
     * Repository for @{@link CameraAppInfo}s and the camera status changes (opening or closing
     * camera) that are in-flight.
     *
     * <p>As camera opening and closing have different delays, and some of them are quick switches
     * caused by activity refresh or front/back camera switch, tracking pending states enables
     * to skip brief activity changes which cause flickering.
     */
    static class PendingCameraUpdateRepository {
        /** Enum describing the newest camera state that is not yet processed. */
        enum PendingCameraState {
            OPENED,
            CLOSED
        }

        /**
         * Set of apps that have camera status update (newly opened or closed) scheduled to be
         * processed.
         *
         * <p>Existing state will be overwritten, as the newest signal (opened/closed) should be
         * respected.
         */
        private final HashMap<CameraAppInfo, PendingCameraState> mPendingCameraStateMap =
                new HashMap<>();

        void trackPendingCameraOpen(@NonNull CameraAppInfo cameraAppInfo) {
            // Some apps can’t handle configuration changes coming at the same time with Camera
            // setup so delaying orientation update to accommodate for that.
            mPendingCameraStateMap.put(cameraAppInfo, PendingCameraState.OPENED);
        }

        void trackPendingCameraClose(@NonNull CameraAppInfo cameraAppInfo) {
            mPendingCameraStateMap.put(cameraAppInfo, PendingCameraState.CLOSED);
        }

        /**
         * @return true if camera open was pending for given {@param cameraAppInfo}.
         */
        boolean removePendingCameraOpen(@NonNull CameraAppInfo cameraAppInfo) {
            return mPendingCameraStateMap.remove(cameraAppInfo, PendingCameraState.OPENED);
        }

        /**
         * @return true if camera close was pending for given {@param cameraAppInfo}.
         */
        boolean removePendingCameraClose(@NonNull CameraAppInfo cameraAppInfo) {
            return mPendingCameraStateMap.remove(cameraAppInfo, PendingCameraState.CLOSED);
        }
    }
}
