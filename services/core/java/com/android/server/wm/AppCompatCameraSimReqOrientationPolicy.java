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

import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_LANDSCAPE;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_PORTRAIT;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_NONE;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_PORTRAIT_DEVICE_IN_LANDSCAPE;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_PORTRAIT_DEVICE_IN_PORTRAIT;
import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOW_CONFIG_APP_BOUNDS;
import static android.app.WindowConfiguration.WINDOW_CONFIG_DISPLAY_ROTATION;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_UNDEFINED;
import static android.view.Display.TYPE_EXTERNAL;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.android.server.wm.AppCompatConfiguration.MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.CameraCompatTaskInfo;
import android.content.res.CameraCompatibilityInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.os.RemoteException;
import android.util.Slog;
import android.view.Surface;
import android.window.DesktopModeFlags;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.protolog.WmProtoLogGroups;
import com.android.window.flags.Flags;

/**
 * Policy for camera compatibility simulate requested orientation treatment.
 *
 * <p>This treatment can be applied to a fixed-orientation activity while camera is open.
 * The treatment letterboxes or pillarboxes the activity to the expected orientation and provides
 * changes to the camera and display orientation signals to match those expected on a portrait
 * device in that orientation (for example, on a standard phone).
 */
final class AppCompatCameraSimReqOrientationPolicy implements AppCompatCameraStatePolicy,
        ActivityRefresher.Evaluator {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "AppCompatCameraSROPolicy" : TAG_WM;

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

    AppCompatCameraSimReqOrientationPolicy(@NonNull DisplayContent displayContent,
            @NonNull CameraStateMonitor cameraStateMonitor,
            @NonNull AppCompatCameraStateSource cameraStateNotifier,
            @NonNull ActivityRefresher activityRefresher) {
        mAtmService = displayContent.mAtmService;
        mCameraStateMonitor = cameraStateMonitor;
        mCameraStateNotifier = cameraStateNotifier;
        mActivityRefresher = activityRefresher;
        mCameraDisplayRotationProvider = new AppCompatCameraRotationState(displayContent);
    }

    void start() {
        mCameraStateNotifier.addCameraStatePolicy(this);
        if (!Flags.enableCameraCompatSandboxDisplayRotationOnExternalDisplaysBugfix()) {
            mActivityRefresher.addEvaluator(this);
        }
        mCameraDisplayRotationProvider.start();
        mIsRunning = true;
    }

    /** Releases camera callback listener. */
    void dispose() {
        mCameraStateNotifier.removeCameraStatePolicy(this);
        if (!Flags.enableCameraCompatSandboxDisplayRotationOnExternalDisplaysBugfix()) {
            mActivityRefresher.removeEvaluator(this);
        }
        mCameraDisplayRotationProvider.dispose();
        mIsRunning = false;
    }

    @VisibleForTesting
    boolean isRunning() {
        return mIsRunning;
    }

    @Surface.Rotation
    int getCameraDeviceRotation() {
        return mCameraDisplayRotationProvider.getCameraDeviceRotation();
    }

    static boolean isPolicyEnabled(@NonNull DisplayContent displayContent) {
        return DesktopModeFlags.ENABLE_CAMERA_COMPAT_SIMULATE_REQUESTED_ORIENTATION.isTrue()
                && (DesktopModeHelper.canEnterDesktopMode(displayContent.mWmService.mContext)
                        || Flags.cameraCompatUnifyCameraPolicies())
                && displayContent.mWmService.mAppCompatConfiguration
                        .isCameraCompatSimulateRequestedOrientationTreatmentEnabled();
    }

    // Refreshing only when configuration changes after applying camera compat treatment.
    @Override
    public boolean shouldRefreshActivity(@NonNull ActivityRecord activity,
            @NonNull Configuration newConfig,
            @NonNull Configuration lastReportedConfig) {
        return (isCompatibilityTreatmentEnabledForActivity(activity,
                /* checkOrientation= */ true)
                || isExternalDisplaySandboxEnabledForActivity(activity))
                && haveCameraCompatAttributesChanged(newConfig, lastReportedConfig);
    }

    private boolean haveCameraCompatAttributesChanged(@NonNull Configuration newConfig,
            @NonNull Configuration lastReportedConfig) {
        // Camera compat treatment changes the following:
        // - Letterboxes app bounds to camera compat aspect ratio in app's requested orientation,
        // - Changes display rotation so it matches what the app expects in its chosen orientation,
        // - Rotate-and-crop camera feed to match that orientation (this changes iff the display
        //     rotation changes, so no need to check).
        // TODO(b/395063101): For external display treatment, and for some apps that are
        //  already in the desired aspect ratio, this will not show a need to refresh, but
        //  it should always be done when camera compat is applied.
        final long diff = newConfig.windowConfiguration.diff(lastReportedConfig.windowConfiguration,
                /* compareUndefined= */ true);
        final boolean appBoundsChanged = (diff & WINDOW_CONFIG_APP_BOUNDS) != 0;
        // TODO(b/395063101): display rotation change is not visible in the system process,
        //  therefore this currently does nothing -> fix.
        final boolean displayRotationChanged = (diff & WINDOW_CONFIG_DISPLAY_ROTATION) != 0;
        return appBoundsChanged || displayRotationChanged;
    }

    @Override
    public void onCameraOpened(@NonNull WindowProcessController appProcess, @NonNull Task task) {
        final ActivityRecord cameraActivity = getTopActivityFromCameraTask(task);
        // Do not check orientation outside of the config recompute, as the app's orientation intent
        // might be obscured by a fullscreen override. Especially for apps which have a camera
        // functionality which is not the main focus of the app: while most of the app might work
        // well in fullscreen, often the camera setup still assumes it will run on a portrait device
        // in its natural orientation and comes out stretched or sideways.
        // Config recalculation will later check the original orientation to avoid applying
        // treatment to apps optimized for large screens.
        if (cameraActivity == null || (!isCompatibilityTreatmentEnabledForActivity(cameraActivity,
                /* checkOrientation= */ false)
                && !isExternalDisplaySandboxEnabledForActivity(cameraActivity))) {
            return;
        }

        updateAndDispatchCameraConfiguration(appProcess, task);
    }

    @Override
    public boolean canCameraBeClosed(@NonNull String cameraId, @NonNull Task task) {
        // Top activity in the same task as the camera activity, or `null` if the task is
        // closed.
        final ActivityRecord topActivity = getTopActivityFromCameraTask(task);
        if (topActivity == null) {
            return true;
        }

        if (isActivityForCameraIdRefreshing(topActivity, cameraId)) {
            ProtoLog.v(WmProtoLogGroups.WM_DEBUG_STATES,
                    "Display id=%d is notified that Camera %s is closed but activity is"
                            + " still refreshing. Rescheduling an update.",
                    topActivity.getDisplayContent().mDisplayId, cameraId);
            return false;
        }
        return true;
    }

    @Override
    public void onCameraClosed(@Nullable WindowProcessController appProcess, @Nullable Task task) {
        // `onCameraClosed` is only received when camera is actually closed, and not on activity
        // refresh or when switching cameras. Proceed to revert camera compat mode.
        updateAndDispatchCameraConfiguration(appProcess, task);
    }

    private void updateAndDispatchCameraConfiguration(@Nullable WindowProcessController app,
            @Nullable Task task) {
        final ActivityRecord activity = getTopActivityFromCameraTask(task);
        if (activity != null) {
            activity.recomputeConfiguration();
        }
        if (task != null) {
            task.dispatchTaskInfoChangedIfNeeded(/* force= */ true);
        }
        if (app != null) {
            final boolean refreshNeeded = updateCompatibilityInfo(app, activity);
            if (activity != null
                    && Flags.enableCameraCompatSandboxDisplayRotationOnExternalDisplaysBugfix()
                    && refreshNeeded) {
                mActivityRefresher.requestRefresh(activity);
            }
        }
        if (activity != null) {
            // Refresh the activity, to get the app to reconfigure the camera setup.
            activity.ensureActivityConfiguration(/* ignoreVisibility= */ true);
            if (Flags.enableCameraCompatSandboxDisplayRotationOnExternalDisplaysBugfix()) {
                mActivityRefresher.refreshActivityIfEnabled(activity);
            }
        }
    }

    private boolean updateCompatibilityInfo(@NonNull WindowProcessController app,
            @Nullable ActivityRecord activityRecord) {
        if (app.getThread() == null || app.mInfo == null) {
            Slog.w(TAG, "Insufficient app information. Cannot revert display rotation sandboxing.");
            return false;
        }

        // CompatibilityInfo fields are static, so even if task or activity has been closed, this
        // state should be updated in case the app process is still alive.
        final CompatibilityInfo compatibilityInfo = mAtmService
                .compatibilityInfoForPackageLocked(app.mInfo);
        compatibilityInfo.cameraCompatibilityInfo = getCameraCompatibilityInfo(activityRecord);
        try {
            // TODO(b/380840084): Consider using a ClientTransaction for this update.
            app.getThread().updatePackageCompatibilityInfo(app.mInfo.packageName,
                    compatibilityInfo);
        } catch (RemoteException e) {
            ProtoLog.w(WmProtoLogGroups.WM_DEBUG_STATES,
                    "Unable to update CompatibilityInfo for app %s", app);
            return false;
        }

        return CameraCompatibilityInfo.isCameraCompatModeActive(compatibilityInfo
                .cameraCompatibilityInfo);
    }

    @NonNull
    private CameraCompatibilityInfo getCameraCompatibilityInfo(@Nullable ActivityRecord
            activityRecord) {
        final CameraCompatibilityInfo.Builder cameraCompatibilityInfoBuilder =
                new CameraCompatibilityInfo.Builder();
        if (activityRecord != null) {
            if (isCompatibilityTreatmentEnabledForActivity(activityRecord,
                    /* checkOrientation= */ true)) {
                // Full compatibility treatment will be applied: sandbox display rotation,
                // rotate-and-crop the camera feed, and letterbox the app.
                final int displayRotation = getDesiredDisplaySandboxForCompat(activityRecord);
                cameraCompatibilityInfoBuilder
                        .setDisplayRotationSandbox(displayRotation)
                        .setShouldLetterboxForCameraCompat(displayRotation != ROTATION_UNDEFINED)
                        .setRotateAndCropRotation(getCameraRotationFromSandboxedDisplayRotation(
                                displayRotation))
                        .setShouldOverrideSensorOrientation(shouldOverrideSensorOrientation())
                        .setShouldAllowTransformInverseDisplay(false);
            } else if (isExternalDisplaySandboxEnabledForActivity(activityRecord)) {
                // Sandbox only display rotation if needed, for external display.
                cameraCompatibilityInfoBuilder.setDisplayRotationSandbox(
                                mCameraDisplayRotationProvider.getCameraDeviceRotation())
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
    private int getCameraRotationFromSandboxedDisplayRotation(@Surface.Rotation int
            displayRotation) {
        if (displayRotation == ROTATION_UNDEFINED) {
            return ROTATION_UNDEFINED;
        }
        int realCameraRotation = mCameraDisplayRotationProvider.getCameraDeviceRotation();
        // Most apps that assume camera sensor orientation expect portrait camera orientation.
        // If sensor orientation is changed (currently only landscape to portrait is supported),
        // this will affect rotate and crop; otherwise sensorRotationOffset should be 0.
        // The value of sensorRotationOffset is calculated by the difference between the real
        // sensor orientation and sandboxed: 0 for landscape cameras, and 90 for portrait cameras.
        // Camera Framework flips this value based on whether the camera is front or back.
        final int sensorRotationOffset = shouldOverrideSensorOrientation() ? 270 : 0;
        final int displayRotationInDegrees = getRotationToDegrees(displayRotation);
        final int realCameraRotationInDegrees = getRotationToDegrees(realCameraRotation);
        // Feed needs to be rotated by the same amount as the display sandboxing difference and the
        // camera sensor sandboxing difference, in order to keep the preview upright.
        return getRotationDegreesToEnum((displayRotationInDegrees - realCameraRotationInDegrees
                + sensorRotationOffset + 360) % 360);
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

    private boolean shouldOverrideSensorOrientation() {
        return Flags.cameraCompatLandscapeCameraSupport()
                && !mCameraDisplayRotationProvider.isCameraDeviceNaturalOrientationPortrait();
    }
    /**
     * Returns true if letterboxing should be allowed for camera apps, even if otherwise it isn't.
     *
     * <p>Camera compat is currently the only use-case of letterboxing for desktop windowing.
     */
    boolean isFreeformLetterboxingForCameraAllowed(@NonNull ActivityRecord activity) {
        // Letterboxing is normally not allowed in desktop windowing.
        return isCameraRunningAndWindowingModeEligible(activity);
    }

    boolean shouldCameraCompatControlOrientation(@NonNull ActivityRecord activity) {
        return isCameraRunningAndWindowingModeEligible(activity);
    }

    boolean isCameraRunningAndWindowingModeEligible(@NonNull ActivityRecord activity) {
        return  activity.mAppCompatController.getCameraOverrides()
                .shouldApplyCameraCompatSimReqOrientationTreatment()
                // Do not apply camera compat treatment when an app is running on a candybar
                // display.
                && activity.getDisplayContent().getIgnoreOrientationRequest()
                && isWindowingModeEligible(activity)
                && mCameraStateMonitor.isCameraRunningForActivity(activity);
    }

    private boolean isWindowingModeEligible(@NonNull ActivityRecord activity) {
        // TODO(b/432218134): consider all windowing modes, e.g. WINDOWING_MODE_PINNED.
        return activity.inFreeformWindowingMode()
                || (Flags.cameraCompatUnifyCameraPolicies() && (activity.inMultiWindowMode()
                        || activity.getWindowingMode() == WINDOWING_MODE_FULLSCREEN));
    }

    boolean shouldCameraCompatControlAspectRatio(@NonNull ActivityRecord activity) {
        // Camera compat should direct aspect ratio when in camera compat mode, unless an app has a
        // different camera compat aspect ratio set: this allows per-app camera compat override
        // aspect ratio to be smaller than the default.
        return getCameraCompatibilityInfo(activity).shouldLetterboxForCameraCompat()
                && !activity.mAppCompatController.getCameraOverrides()
                        .isOverrideMinAspectRatioForCameraEnabled();
    }

    float getCameraCompatAspectRatio(@NonNull ActivityRecord activityRecord) {
        if (shouldCameraCompatControlAspectRatio(activityRecord)) {
            return activityRecord.mWmService.mAppCompatConfiguration.getCameraCompatAspectRatio();
        }

        return MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;
    }

    @CameraCompatTaskInfo.CameraCompatMode
    int getCameraCompatMode(@NonNull ActivityRecord topActivity) {
        if (!isCompatibilityTreatmentEnabledForActivity(topActivity,
                /* checkOrientation= */ true)) {
            return CAMERA_COMPAT_NONE;
        }

        // This treatment targets only devices with portrait natural orientation, which most tablets
        // have.
        if (!mCameraDisplayRotationProvider.isCameraDeviceNaturalOrientationPortrait()) {
            // TODO(b/365725400): handle landscape natural orientation.
            return CAMERA_COMPAT_NONE;
        }

        final int appOrientation = topActivity.getRequestedConfigurationOrientation();
        final boolean isDisplayRotationPortrait = mCameraDisplayRotationProvider
                .isCameraDeviceOrientationPortrait();
        if (appOrientation == ORIENTATION_PORTRAIT) {
            if (isDisplayRotationPortrait) {
                return CAMERA_COMPAT_PORTRAIT_DEVICE_IN_PORTRAIT;
            } else {
                return CAMERA_COMPAT_PORTRAIT_DEVICE_IN_LANDSCAPE;
            }
        } else if (appOrientation == ORIENTATION_LANDSCAPE) {
            if (isDisplayRotationPortrait) {
                return CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_PORTRAIT;
            } else {
                return CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_LANDSCAPE;
            }
        }

        return CAMERA_COMPAT_NONE;
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
        return activity.mAppCompatController.getCameraOverrides()
                .shouldApplyCameraCompatSimReqOrientationTreatment()
                // Do not apply camera compat treatment when an app is running on a candybar
                // display. External displays should have this set to true.
                && activity.getDisplayContent().getIgnoreOrientationRequest()
                && mCameraStateMonitor.isCameraRunningForActivity(activity)
                && isOrientationEligibleForTreatment(activity, checkOrientation)
                && isWindowingModeEligible(activity)
                // TODO(b/332665280): investigate whether we can support activity embedding.
                && !activity.isEmbedded();
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
    boolean isExternalDisplaySandboxEnabledForActivity(@NonNull ActivityRecord activity) {
        if (!Flags.enableCameraCompatSandboxDisplayRotationOnExternalDisplaysBugfix()
                || !mCameraStateMonitor.isCameraRunningForActivity(activity)
                // For compatibility apps (fixed-orientation), apply the full treatment: sandboxing
                // display rotation to match app's requested orientation, letterboxing, and
                // rotating-and-cropping the camera feed.
                || isCompatibilityTreatmentEnabledForActivity(activity,
                /* checkOrientation= */ true)) {
            return false;
        }
        final boolean isSandboxAllowed = activity.mAppCompatController
                .getCameraOverrides().shouldApplyCameraCompatSimReqOrientationTreatment();
        final boolean externalDisplay = activity.getDisplayContent().getDisplay().getType()
                == TYPE_EXTERNAL;
        // If camera and external display rotations are the same, this treatment has no effect.
        final boolean externalDisplayDifferentOrientation = externalDisplay
                && (activity.getDisplayContent().getRotation()
                != mCameraDisplayRotationProvider.getCameraDeviceRotation());
        return isSandboxAllowed && externalDisplayDifferentOrientation;
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
        return Flags.enableCameraCompatSandboxDisplayRotationOnExternalDisplaysBugfix()
                ? mActivityRefresher.isActivityRefreshing(topActivity)
                : topActivity.mAppCompatController.getCameraOverrides().isRefreshRequested();
    }
}
