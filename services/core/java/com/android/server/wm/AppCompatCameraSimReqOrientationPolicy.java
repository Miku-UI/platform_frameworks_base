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

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.res.CameraCompatibilityInfo.isCameraCompatModeActive;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_UNDEFINED;
import static android.view.Display.TYPE_EXTERNAL;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_CAMERA_COMPAT;
import static com.android.server.wm.AppCompatCameraPolicy.TAG_CAMERA_COMPAT;
import static com.android.server.wm.AppCompatConfiguration.MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.WindowConfiguration;
import android.content.res.CameraCompatibilityInfo;
import android.content.res.CompatibilityInfo;
import android.os.RemoteException;
import android.util.SparseArray;
import android.view.Surface;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.window.flags.Flags;

import java.io.PrintWriter;

/**
 * Policy for camera compatibility simulate requested orientation treatment.
 *
 * <p>This treatment can be applied to a fixed-orientation activity while camera is open.
 * The treatment letterboxes or pillarboxes the activity to the expected orientation and provides
 * changes to the camera and display orientation signals to match those expected on a portrait
 * device in that orientation (for example, on a standard phone).
 */
final class AppCompatCameraSimReqOrientationPolicy implements AppCompatCameraStatePolicy {
    @NonNull
    private final ActivityTaskManagerService mAtmService;
    @NonNull
    private final ActivityRefresher mActivityRefresher;
    @NonNull
    private final AppCompatCameraStateSource mCameraStateNotifier;
    @NonNull
    private final CameraStateMonitor mCameraStateMonitor;
    @VisibleForTesting
    @NonNull
    final AppCompatCameraRotationState mCameraDisplayRotationProvider;

    /**
     * Value toggled on {@link #start()} to {@code true} and on {@link #dispose()} to {@code false}.
     */
    private boolean mIsRunning;

    private final SparseArray<CameraCompatibilityInfo> mActiveCameraCompat = new SparseArray<>();

    /**
     * Last display rotation that this policy reacted to.
     *
     * <p>{@link ROTATION_UNDEFINED} if not tracking camera changes.
     */
    @Surface.Rotation
    private int mDisplayRotation = ROTATION_UNDEFINED;

    AppCompatCameraSimReqOrientationPolicy(@NonNull WindowManagerService wmService,
            @NonNull CameraStateMonitor cameraStateMonitor,
            @NonNull AppCompatCameraStateSource cameraStateNotifier,
            @NonNull ActivityRefresher activityRefresher) {
        mAtmService = wmService.mAtmService;
        mCameraStateMonitor = cameraStateMonitor;
        mCameraStateNotifier = cameraStateNotifier;
        mActivityRefresher = activityRefresher;
        mCameraDisplayRotationProvider = new AppCompatCameraRotationState(wmService);
    }

    void start() {
        mCameraStateNotifier.addCameraStatePolicy(this);
        mCameraDisplayRotationProvider.start();
        mIsRunning = true;
    }

    /** Releases camera callback listener. */
    void dispose() {
        mCameraStateNotifier.removeCameraStatePolicy(this);
        mCameraDisplayRotationProvider.dispose();
        mIsRunning = false;
    }

    @VisibleForTesting
    boolean isRunning() {
        return mIsRunning;
    }

    @Surface.Rotation
    int getCameraDeviceRotation(@NonNull DisplayContent displayContent) {
        return mCameraDisplayRotationProvider.getCameraDeviceRotation(displayContent);
    }

    static boolean isPolicyEnabled(@NonNull WindowManagerService wmService) {
        return (DesktopModeHelper.canEnterDesktopMode(wmService.mContext)
                || Flags.cameraCompatUnifyCameraPolicies())
                && wmService.mAppCompatConfiguration
                        .isCameraCompatSimReqOrientationTreatmentEnabled();
    }

    @Override
    public void onCameraOpened(@NonNull CameraAppInfo cameraAppInfo,
            @NonNull WindowProcessController appProcess,
            @NonNull Task cameraTask) {
        // App might be forced to fullscreen, but the app might have requested fixed orientation,
        // in which case camera compat might still be applicable. Recompute configuration with the
        // possibility of camera compat dictating app orientation and min aspect ratio.
        // Note: if the app targets SDK 36+, orientation and min aspect ratio requests might be
        // ignored. Camera compatibility will not be applied in that case.
        ProtoLog.v(WM_DEBUG_CAMERA_COMPAT,
                "%s: Policy is notified that Camera %s is opened for package: %s.",
                TAG_CAMERA_COMPAT, cameraAppInfo.mCameraId, cameraAppInfo.mPackageName);
        final ActivityRecord cameraActivity = getTopActivityFromCameraTask(cameraTask);
        // Do not check orientation outside of the config recompute, as the app's orientation intent
        // might be obscured by a fullscreen override. Especially for apps which have a camera
        // functionality which is not the main focus of the app: while most of the app might work
        // well in fullscreen, often the camera setup still assumes it will run on a portrait device
        // in its natural orientation and comes out stretched or sideways.
        // Config recalculation will later check the original orientation to avoid applying
        // treatment to apps optimized for large screens.
        if (cameraActivity == null || (!isCompatibilityTreatmentEnabledForActivity(cameraActivity,
                /* checkOrientation= */ false)
                && !shouldSandboxExternalDisplayRotationForActivity(cameraActivity))) {
            ProtoLog.v(WM_DEBUG_CAMERA_COMPAT,
                    "%s: Activity is not eligible for camera compat treatment: %s.",
                    TAG_CAMERA_COMPAT, cameraActivity);
            return;
        }

        if (cameraTask.getDisplayContent() == null) {
            ProtoLog.v(WM_DEBUG_CAMERA_COMPAT, "%s: Task not connected to display.",
                    TAG_CAMERA_COMPAT);
            return;
        }
        if (cameraTask.getDisplayContent().getDisplay().getType() == TYPE_EXTERNAL) {
            // Camera compat temporarily disabled on the external display.
            // TODO(b/497656545): enable sensor listener only when needed and allow camera compat.
            return;
        }
        mDisplayRotation = cameraTask.getDisplayContent().getRotation();
        updateAndDispatchCameraConfiguration(cameraAppInfo.mTaskId, appProcess, cameraActivity);
    }

    @Override
    public boolean canCameraBeClosed(@NonNull CameraAppInfo cameraAppInfo, @NonNull Task task) {
        ProtoLog.v(WM_DEBUG_CAMERA_COMPAT,
                "%s: Policy is notified that Camera %s is closed for package: %s.",
                TAG_CAMERA_COMPAT, cameraAppInfo.mCameraId, cameraAppInfo.mPackageName);
        // Top activity in the same task as the camera activity, or `null` if the task is
        // closed.
        final ActivityRecord topActivity = getTopActivityFromCameraTask(task);
        if (topActivity == null) {
            return true;
        }

        if (isActivityForCameraIdRefreshing(topActivity, cameraAppInfo.mCameraId)) {
            ProtoLog.v(WM_DEBUG_CAMERA_COMPAT,
                    "%s: Display id=%d is notified that Camera %s is closed but activity is"
                            + " still refreshing. Rescheduling an update.",
                    TAG_CAMERA_COMPAT, topActivity.getDisplayContent().mDisplayId,
                    cameraAppInfo.mCameraId);
            return false;
        }
        return true;
    }

    @Override
    public void onCameraClosed(@NonNull CameraAppInfo cameraAppInfo,
            @Nullable WindowProcessController appProcess, @Nullable Task task) {
        mDisplayRotation = ROTATION_UNDEFINED;
        // Top activity in the same task as the camera activity, or `null` if the task is
        // closed.
        final ActivityRecord topActivity = getTopActivityFromCameraTask(task);
        // `onCameraClosed` is only received when camera is actually closed, and not on activity
        // refresh or when switching cameras. Proceed to revert camera compat mode.
        updateAndDispatchCameraConfiguration(cameraAppInfo.mTaskId, appProcess, topActivity);
    }

    boolean shouldIgnoreReqOrientationForCameraCompat(@NonNull ActivityRecord activity) {
        final CameraCompatibilityInfo cameraCompatInfo = getActiveCameraCompatibilityInfo(activity);
        return isCameraCompatModeActive(cameraCompatInfo);
    }

    void onWindowingModeChanged(@NonNull ActivityRecord activity,
            @WindowConfiguration.WindowingMode int newWindowingMode) {
        final Task task = activity.getTask();
        if (task == null) {
            return;
        }
        // Windowing mode change can make camera app eligible or ineligible for treatment (it will
        // not modify the treatment setup for the same policy). To avoid unnecessary recompute,
        // compare if the treatment can possibly be applied in new windowing mode, versus whether it
        // is currently active.
        final boolean activityAndDisplayEligibleForTreatment =
                isCompatibilityTreatmentEnabledForActivity(activity, /* checkOrientation= */ false,
                        newWindowingMode)
                        || shouldSandboxExternalDisplayRotationForActivity(activity);
        final boolean needsTreatmentUpdate = mActiveCameraCompat.contains(task.mTaskId)
                != activityAndDisplayEligibleForTreatment;
        if (needsTreatmentUpdate) {
            // Camera compat should already be running, so any camera-compat-induced config
            // changes to the app orientation and aspect ratio should remain the same.
            ProtoLog.d(WM_DEBUG_CAMERA_COMPAT, "%s: Updating camera compat after windowing"
                    + " mode change: %s", TAG_CAMERA_COMPAT, mActiveCameraCompat);
            updateAndDispatchCameraConfiguration(task.mTaskId, activity.app, activity);
            mDisplayRotation = mActiveCameraCompat.contains(task.mTaskId)
                    ? activity.mDisplayContent.getRotation() : ROTATION_UNDEFINED;
        }
    }

    void onDisplayRotationChanged(@NonNull ActivityRecord activity,
            @Surface.Rotation int newDisplayRotation) {
        final Task task = activity.getTask();
        if (task == null) {
            return;
        }
        // If mDisplayRotation is undefined, the treatment is not active, so there is nothing to
        // update.
        if (mDisplayRotation != ROTATION_UNDEFINED && newDisplayRotation != mDisplayRotation) {
            mDisplayRotation = newDisplayRotation;
            // Camera compat should already be running, so any camera-compat-induced config
            // changes to the app orientation and aspect ratio should remain the same.
            ProtoLog.d(WM_DEBUG_CAMERA_COMPAT, "%s: Updating camera compat after rotation: %s",
                    TAG_CAMERA_COMPAT, mActiveCameraCompat);
            updateAndDispatchCameraConfiguration(task.mTaskId, activity.app, activity);
        }
    }

    /**
     *
     * @param taskId - task that opened the camera. If camera is closing, this task might not exist,
     *               but it should still be passed so the camera treatment can be properly reverted.
     */
    private void updateAndDispatchCameraConfiguration(int taskId,
            @Nullable WindowProcessController app,
            @Nullable ActivityRecord activity) {
        final CameraCompatibilityInfo existingTreatment =
                mActiveCameraCompat.get(taskId) == null
                        ? new CameraCompatibilityInfo.Builder().build()
                        : mActiveCameraCompat.get(taskId);
        // Put a placeholder before the activity configuration is recomputed, to make sure the
        // CameraCompatibilityInfo is up to date when queried by other policies, and to skip
        // computation for any app that doesn't have camera opened (i.e. if there are no entries in
        // mActiveCameraCompat with queried activitiy/task/packageName, return early).
        mActiveCameraCompat.put(taskId, null);
        if (activity != null) {
            activity.recomputeConfiguration();
        }

        if (app != null) {
            final CameraCompatibilityInfo cameraCompatInfo = activity != null
                    ? getActiveCameraCompatibilityInfo(activity)
                    : new CameraCompatibilityInfo.Builder().build();
            final boolean updateSuccessful = updateCompatibilityInfo(app, cameraCompatInfo);
            final boolean isCameraCompatActive = isCameraCompatModeActive(cameraCompatInfo);
            if (isCameraCompatActive) {
                mActiveCameraCompat.put(taskId, cameraCompatInfo);
            } else {
                mActiveCameraCompat.remove(taskId);
            }
            if (activity != null && updateSuccessful
                    // Request refresh in case treatment is started, but also stopped while camera
                    // is still open but treatment is no longer applicable, for example when
                    // switching to an unsupported windowing mode. Refresh ensures camera is set up
                    // without rotate and crop and without display sandboxing.
                    && !cameraCompatInfo.equals(existingTreatment)) {
                mActivityRefresher.requestRefresh(activity);
            }
        } else {
            mActiveCameraCompat.remove(taskId);
        }

        if (activity != null) {
            // Refresh the activity, to get the app to reconfigure the camera setup.
            activity.ensureActivityConfiguration(/* ignoreVisibility= */ true);
            mActivityRefresher.refreshActivityIfEnabled(activity);
        }
        ProtoLog.d(WM_DEBUG_CAMERA_COMPAT, "%s: Active camera compat treatments: %s",
                TAG_CAMERA_COMPAT, mActiveCameraCompat);
    }

    private boolean updateCompatibilityInfo(@NonNull WindowProcessController app,
            @NonNull CameraCompatibilityInfo cameraCompatibilityInfo) {
        if (app.getThread() == null || app.mInfo == null) {
            ProtoLog.w(WM_DEBUG_CAMERA_COMPAT, "%s: Insufficient app information."
                    + " Cannot revert display rotation sandboxing.", TAG_CAMERA_COMPAT);
            return false;
        }

        // CompatibilityInfo fields are static, so even if task or activity has been closed, this
        // state should be updated in case the app process is still alive.
        final CompatibilityInfo compatibilityInfo = mAtmService
                .compatibilityInfoForPackageLocked(app.mInfo);
        compatibilityInfo.cameraCompatibilityInfo = cameraCompatibilityInfo;
        try {
            ProtoLog.i(WM_DEBUG_CAMERA_COMPAT, "%s: Updating CameraCompatibilityInfo"
                    + " for package: %s to: %s.", TAG_CAMERA_COMPAT, app.mInfo.packageName,
                    compatibilityInfo.cameraCompatibilityInfo);
            // TODO(b/380840084): Consider using a ClientTransaction for this update.
            app.getThread().updatePackageCompatibilityInfo(app.mInfo.packageName,
                    compatibilityInfo);
        } catch (RemoteException e) {
            ProtoLog.w(WM_DEBUG_CAMERA_COMPAT,
                    "%s: Unable to update CompatibilityInfo for app %s",
                    TAG_CAMERA_COMPAT, app);
            return false;
        }

        return true;
    }

    @NonNull
    private CameraCompatibilityInfo getCameraCompatibilityInfo(@Nullable ActivityRecord
            activityRecord) {
        final CameraCompatibilityInfo.Builder cameraCompatibilityInfoBuilder =
                new CameraCompatibilityInfo.Builder();
        if (activityRecord != null && activityRecord.getDisplayContent() != null) {
            // Check the full treatment eligibility first. If applicable, it covers the external
            // display use-case too.
            if (isCompatibilityTreatmentEnabledForActivity(activityRecord,
                    /* checkOrientation= */ true)) {
                final int displayRotation = getDesiredDisplaySandboxForCompat(activityRecord);
                final int rotateAndCropRotation = getCameraRotationFromSandboxedDisplayRotation(
                        activityRecord.getDisplayContent(), displayRotation);
                if (isRotateAndCropModeSupported(activityRecord, rotateAndCropRotation)) {
                    // Full compatibility treatment will be applied: sandbox display rotation,
                    // rotate-and-crop the camera feed, and letterbox the app.
                    return cameraCompatibilityInfoBuilder
                            .setDisplayRotationSandbox(displayRotation)
                            .setShouldLetterboxForCameraCompat(
                                    displayRotation != ROTATION_UNDEFINED)
                            .setRotateAndCropRotation(rotateAndCropRotation)
                            .setShouldOverrideSensorOrientation(
                                    shouldOverrideSensorOrientation(
                                            activityRecord.getDisplayContent()))
                            .setShouldAllowTransformInverseDisplay(false)
                            .build();
                }
            }

            // For responsive apps (not applicable for full treatment) and for fixed-orientation
            // apps where the full required treatment is not supported on this device, check if
            // a lighter treatment for external displays is applicable.
            if (shouldSandboxExternalDisplayRotationForActivity(activityRecord)) {
                // Sandbox only display rotation if needed, for external display.
                cameraCompatibilityInfoBuilder.setDisplayRotationSandbox(
                                mCameraDisplayRotationProvider.getCameraDeviceRotation(
                                        activityRecord.getDisplayContent()))
                        .setShouldAllowTransformInverseDisplay(false);
            }
        }

        return cameraCompatibilityInfoBuilder.build();
    }

    /**
     * {@link Surface.Rotation} that the app likely expects given its requested orientation.
     */
    @Surface.Rotation
    private int getDesiredDisplaySandboxForCompat(@NonNull ActivityRecord activity) {
        final int appOrientation = activity.getRequestedConfigurationOrientation();
        if (appOrientation == ORIENTATION_PORTRAIT) {
            return ROTATION_0;
        } else if (appOrientation == ORIENTATION_LANDSCAPE) {
            // TODO(b/390183440): differentiate between LANDSCAPE and REVERSE_LANDSCAPE
            //  requested orientation for landscape apps.
            return ROTATION_90;
        }

        return ROTATION_UNDEFINED;
    }

    /**
     * Calculates the angle for camera feed rotate-and-crop.
     *
     * <p>Camera apps most commonly calculate the preview rotation with the formula (simplified):
     * {code rotation = cameraSensorRotation - displayRotation}. When display rotation or sensor
     * orientation is sandboxed, camera feed needs to be rotated by the same amount to keep the
     * preview upright.
     */
    private int getCameraRotationFromSandboxedDisplayRotation(
            @NonNull DisplayContent displayContent, @Surface.Rotation int displayRotation) {
        if (displayRotation == ROTATION_UNDEFINED) {
            return ROTATION_UNDEFINED;
        }
        int realCameraRotation = mCameraDisplayRotationProvider
                .getCameraDeviceRotation(displayContent);
        // Most apps that assume camera sensor orientation expect portrait camera orientation.
        // If sensor orientation is changed (currently only landscape to portrait is supported),
        // this will affect rotate and crop; otherwise sensorRotationOffset should be 0.
        // The value of sensorRotationOffset is calculated by the difference between the real
        // sensor orientation and sandboxed: 0 for landscape cameras, and 90 for portrait cameras.
        // Camera Framework flips this value based on whether the camera is front or back.
        final int sensorRotationOffset = shouldOverrideSensorOrientation(displayContent)
                ? 270 : 0;
        final int displayRotationInDegrees = getRotationToDegrees(displayRotation);
        final int realCameraRotationInDegrees = getRotationToDegrees(realCameraRotation);
        // Feed needs to be rotated by the same amount as the display sandboxing difference and the
        // camera sensor sandboxing difference, in order to keep the preview upright.
        return getRotationDegreesToEnum((displayRotationInDegrees - realCameraRotationInDegrees
                + sensorRotationOffset + 360) % 360);
    }

    private boolean isRotateAndCropModeSupported(@NonNull ActivityRecord activityRecord,
            @Surface.Rotation int rotateAnCropRotation) {
        if (rotateAnCropRotation == ROTATION_0 || rotateAnCropRotation == ROTATION_UNDEFINED) {
            return true;
        }
        return mCameraStateMonitor.isRotateAndCropModeSupported(activityRecord,
                rotateAnCropRotation);
    }

    private static int getRotationToDegrees(@Surface.Rotation int rotation) {
        switch (rotation) {
            case ROTATION_0 -> {
                return 0;
            }
            case ROTATION_90 -> {
                return 90;
            }
            case ROTATION_180 -> {
                return 180;
            }
            case ROTATION_270 -> {
                return 270;
            }
            default -> {
                return ROTATION_UNDEFINED;
            }
        }
    }

    @Surface.Rotation
    private static int getRotationDegreesToEnum(int rotationDegrees) {
        switch (rotationDegrees) {
            case 0 -> {
                return ROTATION_0;
            }
            case 90 -> {
                return ROTATION_90;
            }
            case 180 -> {
                return ROTATION_180;
            }
            case 270 -> {
                return ROTATION_270;
            }
            default -> {
                return ROTATION_UNDEFINED;
            }
        }
    }

    private boolean shouldOverrideSensorOrientation(@NonNull DisplayContent displayContent) {
        return Flags.cameraCompatLandscapeCameraSupport()
                && !mCameraDisplayRotationProvider
                        .isCameraDeviceNaturalOrientationPortrait(displayContent);
    }

    /**
     * Returns true if letterboxing should be allowed for camera apps, even if otherwise it isn't.
     *
     * <p>Camera compat is currently the only use-case of letterboxing for desktop windowing.
     */
    boolean isFreeformLetterboxingForCameraAllowed(@NonNull ActivityRecord activity) {
        // Letterboxing is normally not allowed in desktop windowing.
        return getActiveCameraCompatibilityInfo(activity).shouldLetterboxForCameraCompat();
    }

    boolean isActivityEligibleForOrientationOverride(@NonNull ActivityRecord activity) {
        return isCameraRunningAndWindowingModeEligible(activity);
    }

    boolean isCameraRunningAndWindowingModeEligible(@NonNull ActivityRecord activity) {
        return isCameraRunningAndWindowingModeEligible(activity, activity.getWindowingMode());
    }

    boolean isCameraRunningAndWindowingModeEligible(@NonNull ActivityRecord activity,
            @WindowConfiguration.WindowingMode int windowingMode) {
        return mCameraStateMonitor.isCameraRunningForActivity(activity)
                && isWindowingModeEligible(windowingMode)
                && isTreatmentAllowedViaConfig(activity)
                // Do not apply camera compat treatment when an app is running on a candybar
                // display.
                && activity.getDisplayContent().getIgnoreOrientationRequest();
    }

    private static boolean isWindowingModeEligible(@WindowConfiguration.WindowingMode int
            windowingMode) {
        return windowingMode == WINDOWING_MODE_FREEFORM
                || (Flags.cameraCompatUnifyCameraPolicies()
                && windowingMode != WINDOWING_MODE_UNDEFINED);
    }

    boolean shouldCameraCompatControlAspectRatio(@NonNull ActivityRecord activity) {
        // Camera compat should direct aspect ratio when in camera compat mode, unless an app has a
        // different camera compat aspect ratio set: this allows per-app camera compat override
        // aspect ratio to be smaller than the default.
        final CameraCompatibilityInfo cameraCompatInfo = getActiveCameraCompatibilityInfo(activity);
        return cameraCompatInfo.shouldLetterboxForCameraCompat() && !activity.mAppCompatController
                .getCameraOverrides().isOverrideMinAspectRatioForCameraEnabled();
    }

    @NonNull
    private CameraCompatibilityInfo getActiveCameraCompatibilityInfo(@NonNull ActivityRecord
            activity) {
        final Task task = activity.getTask();
        if (task != null) {
            if (mActiveCameraCompat.contains(task.mTaskId)) {
                final CameraCompatibilityInfo existingCameraCompatibilityInfo =
                        mActiveCameraCompat.get(task.mTaskId);
                if (existingCameraCompatibilityInfo != null) {
                    return existingCameraCompatibilityInfo;
                }
                // If cameraCompatibilityInfo is null, it has been cleared for recompute.
                // Calculate camera compat info and save in mActiveCameraCompat to skip
                // recomputing until camera status changes.
                final CameraCompatibilityInfo newCameraCompatInfo =
                        getCameraCompatibilityInfo(activity);
                mActiveCameraCompat.set(task.mTaskId, newCameraCompatInfo);
                return newCameraCompatInfo;
            }
        }

        return new CameraCompatibilityInfo.Builder().build();
    }

    float getCameraCompatAspectRatio(@NonNull ActivityRecord activityRecord) {
        if (shouldCameraCompatControlAspectRatio(activityRecord)) {
            return activityRecord.mWmService.mAppCompatConfiguration.getCameraCompatAspectRatio();
        }

        return MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;
    }

    /**
     * Whether camera compat treatment is applicable for the given activity, ignoring its windowing
     * mode.
     *
     * <p>Conditions that need to be met:
     * <ul>
     *     <li>Treatment is enabled.
     *     <li>Camera is active for the package.
     *     <li>The app has a fixed orientation if {@code checkOrientation} is true.
     * </ul>
     *
     * @param checkOrientation Whether to take apps orientation into account for this check. Only
     *                         fixed-orientation apps should be targeted, but this might be
     *                         obscured by OEMs via fullscreen override and the app's original
     *                         intent inaccessible when the camera opens. Thus, policy would pass
     *                         {@code false} here when considering whether to trigger config
     *                         recalculation, and later pass {@code true} during recalculation.
     */
    @VisibleForTesting
    boolean isCompatibilityTreatmentEnabledForActivity(@NonNull ActivityRecord activity,
            boolean checkOrientation) {
        return isCompatibilityTreatmentEnabledForActivity(activity, checkOrientation,
                activity.getWindowingMode());
    }

    @VisibleForTesting
    private boolean isCompatibilityTreatmentEnabledForActivity(@NonNull ActivityRecord activity,
                boolean checkOrientation, @WindowConfiguration.WindowingMode int windowingMode) {
        return isCameraRunningAndWindowingModeEligible(activity, windowingMode)
                && isOrientationEligibleForTreatment(activity, checkOrientation)
                // TODO(b/332665280): investigate whether we can support activity embedding.
                && !activity.isEmbedded();
    }

    void dump(@NonNull ActivityRecord activity, @NonNull PrintWriter pw, @NonNull String prefix) {
        pw.println(prefix + "AppCompatCameraSimReqOrientationPolicy:");
        for (int i = 0; i < mActiveCameraCompat.size(); i++) {
            final int taskId = mActiveCameraCompat.keyAt(i);
            if (activity.getTask() != null && activity.getTask().mTaskId == taskId) {
                pw.println(prefix + " cameraCompatibilityInfo=" + mActiveCameraCompat.valueAt(i));
            }
        }
    }

    private boolean isTreatmentAllowedViaConfig(@NonNull ActivityRecord activity) {
        return mCameraDisplayRotationProvider.isCameraDeviceNaturalOrientationPortrait(
                activity.getDisplayContent())
                ? activity.mAppCompatController.getCameraOverrides()
                        .shouldApplyCameraCompatSimReqOrientationTreatment()
                : activity.mAppCompatController.getCameraOverrides()
                        .shouldApplyCameraCompatSimReqOrientationTreatmentForLandscapeCamera();
    }

    private boolean isOrientationEligibleForTreatment(@NonNull ActivityRecord activity,
            boolean checkOrientation) {
        final int orientation = activity.getRequestedConfigurationOrientation();
        return  (!checkOrientation || orientation != ORIENTATION_UNDEFINED)
                // "locked" and "nosensor" values are often used by camera apps that can't
                // handle dynamic changes so we shouldn't force-letterbox them.
                && activity.getRequestedOrientation() != SCREEN_ORIENTATION_NOSENSOR
                && activity.getRequestedOrientation() != SCREEN_ORIENTATION_LOCKED;
    }

    /**
     * Whether display rotation should be sandboxed to that of current camera rotation.
     *
     * <p>Only eligible if the activity is running on an external display.
     *
     * @return false if the activity is opted-out, not on external display, or a full camera compat
     * treatment is more suitable (most likely if it is a fixed-orientation activity).
     */
    private boolean shouldSandboxExternalDisplayRotationForActivity(
            @NonNull ActivityRecord activity) {
        return mCameraStateMonitor.isCameraRunningForActivity(activity)
                && isOnExternalDisplayWithDifferentOrientation(activity)
                && isTreatmentAllowedViaConfig(activity);
    }

    private boolean isOnExternalDisplayWithDifferentOrientation(@NonNull ActivityRecord activity) {
        final boolean externalDisplay = activity.getDisplayContent().getDisplay().getType()
                == TYPE_EXTERNAL;
        final int displayRotation = activity.getConfiguration().windowConfiguration
                .getDisplayRotation();
        final int cameraDeviceRotation = mCameraDisplayRotationProvider
                .getCameraDeviceRotation(activity.getDisplayContent());
        // If camera and external display rotations are the same, this treatment has no effect.
        return externalDisplay && cameraDeviceRotation != ROTATION_UNDEFINED
                && displayRotation != cameraDeviceRotation;
    }

    @Nullable
    private ActivityRecord getTopActivityFromCameraTask(@Nullable Task task) {
        return task != null
                ? task.getTopActivity(/* isFinishing */ false, /* includeOverlays */ false)
                : null;
    }

    private boolean isActivityForCameraIdRefreshing(@NonNull ActivityRecord topActivity,
            @NonNull String cameraId) {
        if (!isCompatibilityTreatmentEnabledForActivity(topActivity, /* checkOrientation= */ true)
                || !mCameraStateMonitor.isCameraWithIdRunningForActivity(topActivity, cameraId)) {
            return false;
        }
        return mActivityRefresher.isActivityRefreshing(topActivity);
    }
}
