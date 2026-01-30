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
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

import static com.android.server.wm.AppCompatConfiguration.MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.CameraCompatTaskInfo;
import android.content.pm.ActivityInfo.ScreenOrientation;
import android.content.res.Configuration;
import android.view.Surface;
import android.widget.Toast;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Encapsulate policy logic related to app compat display rotation.
 */
class AppCompatCameraPolicy {

    @Nullable
    @VisibleForTesting
    final CameraStateMonitor mCameraStateMonitor;
    @Nullable
    @VisibleForTesting
    final ActivityRefresher mActivityRefresher;
    @Nullable
    final AppCompatCameraDisplayRotationPolicy mDisplayRotationPolicy;
    @Nullable
    final AppCompatCameraSimReqOrientationPolicy mSimReqOrientationPolicy;

    AppCompatCameraPolicy(@NonNull WindowManagerService wmService,
            @NonNull DisplayContent displayContent) {
        // Not checking DeviceConfig value here to allow enabling via DeviceConfig
        // without the need to restart the device.
        final boolean isDisplayRotationPolicyEnabled = AppCompatCameraDisplayRotationPolicy
                .isPolicyEnabled(displayContent);
        final boolean isSimReqOrientationPolicyEnabled = AppCompatCameraSimReqOrientationPolicy
                .isPolicyEnabled(displayContent);
        if (isDisplayRotationPolicyEnabled || isSimReqOrientationPolicyEnabled) {
            final AppCompatCameraStateSource cameraStateListenerDelegate =
                    new AppCompatCameraStateSource();
            mCameraStateMonitor = new CameraStateMonitor(displayContent, wmService.mH,
                    cameraStateListenerDelegate);
            mActivityRefresher = new ActivityRefresher(wmService, wmService.mH);
            mDisplayRotationPolicy = isDisplayRotationPolicyEnabled
                    ? new AppCompatCameraDisplayRotationPolicy(displayContent, mCameraStateMonitor,
                            cameraStateListenerDelegate, mActivityRefresher)
                    : null;
            mSimReqOrientationPolicy = isSimReqOrientationPolicyEnabled
                    ? new AppCompatCameraSimReqOrientationPolicy(displayContent,
                            mCameraStateMonitor, cameraStateListenerDelegate, mActivityRefresher)
                    : null;
        } else {
            mDisplayRotationPolicy = null;
            mSimReqOrientationPolicy = null;
            mCameraStateMonitor = null;
            mActivityRefresher = null;
        }
    }

    static void onActivityRefreshed(@NonNull ActivityRecord activity) {
        final AppCompatCameraPolicy cameraPolicy = getAppCompatCameraPolicy(activity);
        if (cameraPolicy != null && cameraPolicy.mActivityRefresher != null) {
            cameraPolicy.mActivityRefresher.onActivityRefreshed(activity);
        }
    }

    @Nullable
    static AppCompatCameraPolicy getAppCompatCameraPolicy(@NonNull ActivityRecord activityRecord) {
        return activityRecord.mDisplayContent != null
                ? activityRecord.mDisplayContent.mAppCompatCameraPolicy : null;
    }

    /**
     * "Refreshes" activity by going through "stopped -> resumed" or "paused -> resumed" cycle.
     * This allows to clear cached values in apps (e.g. display or camera rotation) that influence
     * camera preview and can lead to sideways or stretching issues persisting even after force
     * rotation.
     */
    static void onActivityConfigurationChanging(@NonNull ActivityRecord activity,
            @NonNull Configuration newConfig, @NonNull Configuration lastReportedConfig) {
        final AppCompatCameraPolicy cameraPolicy = getAppCompatCameraPolicy(activity);
        if (cameraPolicy != null && cameraPolicy.mActivityRefresher != null) {
            cameraPolicy.mActivityRefresher.onActivityConfigurationChanging(activity, newConfig,
                    lastReportedConfig);
        }
    }

    /**
     * Notifies {@link ActivityRefresher} that the activity is already relaunching.
     *
     * <p>This is to avoid unnecessary refresh (i.e. cycling activity though stop/pause -> resume),
     * if refresh is pending for camera compat.
     */
    static void onActivityRelaunching(@NonNull ActivityRecord activity) {
        final AppCompatCameraPolicy cameraPolicy = getAppCompatCameraPolicy(activity);
        if (cameraPolicy != null && cameraPolicy.mActivityRefresher != null) {
            cameraPolicy.mActivityRefresher.onActivityRelaunching(activity);
        }
    }

    /**
     * Notifies that animation in {@link ScreenRotationAnimation} has finished.
     *
     * <p>This class uses this signal as a trigger for notifying the user about forced rotation
     * reason with the {@link Toast}.
     */
    void onScreenRotationAnimationFinished() {
        if (mDisplayRotationPolicy != null) {
            mDisplayRotationPolicy.onScreenRotationAnimationFinished();
        }
    }

    static boolean isActivityEligibleForOrientationOverride(@NonNull ActivityRecord activity) {
        final AppCompatCameraPolicy cameraPolicy = getAppCompatCameraPolicy(activity);
        return cameraPolicy != null && cameraPolicy.mDisplayRotationPolicy != null
                && cameraPolicy.mDisplayRotationPolicy
                        .isActivityEligibleForOrientationOverride(activity);
    }

    /**
     * Whether camera compat treatment is applicable for the given activity.
     *
     * <p>Conditions that need to be met:
     * <ul>
     *     <li>Camera is active for the package.
     *     <li>The activity is in fullscreen
     *     <li>The activity has fixed orientation but not "locked" or "nosensor" one.
     * </ul>
     */
    static boolean isTreatmentEnabledForActivity(@NonNull ActivityRecord activity) {
        final AppCompatCameraPolicy cameraPolicy = getAppCompatCameraPolicy(activity);
        return cameraPolicy != null && cameraPolicy.mDisplayRotationPolicy != null
                && cameraPolicy.mDisplayRotationPolicy
                        .isTreatmentEnabledForActivity(activity);
    }

    void start() {
        if (mDisplayRotationPolicy != null) {
            mDisplayRotationPolicy.start();
        }
        if (mSimReqOrientationPolicy != null) {
            mSimReqOrientationPolicy.start();
        }
        if (mCameraStateMonitor != null) {
            mCameraStateMonitor.startListeningToCameraState();
        }
    }

    void dispose() {
        if (mDisplayRotationPolicy != null) {
            mDisplayRotationPolicy.dispose();
        }
        if (mSimReqOrientationPolicy != null) {
            mSimReqOrientationPolicy.dispose();
        }
        if (mCameraStateMonitor != null) {
            mCameraStateMonitor.stopListeningToCameraState();
        }
    }

    boolean hasDisplayRotationPolicy() {
        return mDisplayRotationPolicy != null;
    }

    boolean hasSimReqOrientationPolicy() {
        return mSimReqOrientationPolicy != null;
    }

    boolean hasCameraStateMonitor() {
        return mCameraStateMonitor != null;
    }

    @ScreenOrientation
    int getOrientation() {
        return mDisplayRotationPolicy != null
                ? mDisplayRotationPolicy.getOrientation()
                : SCREEN_ORIENTATION_UNSPECIFIED;
    }

    // TODO(b/369070416): have policies implement the same interface.
    static boolean shouldCameraCompatControlOrientation(@NonNull ActivityRecord activity) {
        final AppCompatCameraPolicy cameraPolicy = getAppCompatCameraPolicy(activity);
        if (cameraPolicy == null) {
            return false;
        }
        return (cameraPolicy.mDisplayRotationPolicy != null
                        && cameraPolicy.mDisplayRotationPolicy
                                .shouldCameraCompatControlOrientation(activity))
                || (cameraPolicy.mSimReqOrientationPolicy != null
                        && cameraPolicy.mSimReqOrientationPolicy
                                .shouldCameraCompatControlOrientation(activity));
    }

    // TODO(b/369070416): have policies implement the same interface.
    static boolean isFreeformLetterboxingForCameraAllowed(@NonNull ActivityRecord activity) {
        final AppCompatCameraPolicy cameraPolicy = getAppCompatCameraPolicy(activity);
        if (cameraPolicy == null) {
            return false;
        }
        return cameraPolicy.mSimReqOrientationPolicy != null
                        && cameraPolicy.mSimReqOrientationPolicy
                                .isFreeformLetterboxingForCameraAllowed(activity);
    }

    // TODO(b/369070416): have policies implement the same interface.
    static boolean shouldCameraCompatControlAspectRatio(@NonNull ActivityRecord activity) {
        final AppCompatCameraPolicy cameraPolicy = getAppCompatCameraPolicy(activity);
        if (cameraPolicy == null) {
            return false;
        }
        return (cameraPolicy.mDisplayRotationPolicy != null
                        && cameraPolicy.mDisplayRotationPolicy
                                .shouldCameraCompatControlAspectRatio(activity))
                || (cameraPolicy.mSimReqOrientationPolicy != null
                        && cameraPolicy.mSimReqOrientationPolicy
                                .shouldCameraCompatControlAspectRatio(activity));
    }

    // TODO(b/369070416): have policies implement the same interface.
    /**
     * @return {@code true} if the Camera is active for the provided {@link ActivityRecord} and
     * any camera compat treatment could be triggered for the current windowing mode.
     */
    private static boolean isCameraRunningAndWindowingModeEligible(
            @NonNull ActivityRecord activity) {
        final AppCompatCameraPolicy cameraPolicy = getAppCompatCameraPolicy(activity);
        if (cameraPolicy == null) {
            return false;
        }
        return (cameraPolicy.mDisplayRotationPolicy != null
                && cameraPolicy.mDisplayRotationPolicy
                        .isCameraRunningAndWindowingModeEligible(activity,
                                /* mustBeFullscreen */ true))
                || (cameraPolicy.mSimReqOrientationPolicy != null
                        && cameraPolicy.mSimReqOrientationPolicy
                                .isCameraRunningAndWindowingModeEligible(activity));
    }

    @Nullable
    String getSummaryForDisplayRotationHistoryRecord() {
        return mDisplayRotationPolicy != null
                ? mDisplayRotationPolicy.getSummaryForDisplayRotationHistoryRecord()
                : null;
    }

    // TODO(b/369070416): have policies implement the same interface.
    static float getCameraCompatMinAspectRatio(@NonNull ActivityRecord activity) {
        final AppCompatCameraPolicy cameraPolicy = getAppCompatCameraPolicy(activity);
        if (cameraPolicy == null) {
            return 1.0f;
        }
        float displayRotationCompatPolicyAspectRatio =
                cameraPolicy.mDisplayRotationPolicy != null
                ? cameraPolicy.mDisplayRotationPolicy.getCameraCompatAspectRatio(activity)
                : MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;
        float simReqOrientationPolicyAspectRatio = cameraPolicy.mSimReqOrientationPolicy != null
                ? cameraPolicy.mSimReqOrientationPolicy.getCameraCompatAspectRatio(activity)
                : MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;
        return Math.max(displayRotationCompatPolicyAspectRatio, simReqOrientationPolicyAspectRatio);
    }

    @CameraCompatTaskInfo.CameraCompatMode
    static int getCameraCompatSimReqOrientationMode(@NonNull ActivityRecord activity) {
        final AppCompatCameraPolicy cameraPolicy = getAppCompatCameraPolicy(activity);
        return cameraPolicy != null && cameraPolicy.mSimReqOrientationPolicy
                != null
                ? cameraPolicy.mSimReqOrientationPolicy.getCameraCompatMode(activity)
                : CameraCompatTaskInfo.CAMERA_COMPAT_NONE;
    }

    @Surface.Rotation
    static int getCameraDeviceRotation(@NonNull ActivityRecord activity) {
        final AppCompatCameraPolicy cameraPolicy = getAppCompatCameraPolicy(activity);
        return cameraPolicy != null && cameraPolicy.mSimReqOrientationPolicy != null
                ? cameraPolicy.mSimReqOrientationPolicy.getCameraDeviceRotation()
                : ROTATION_UNDEFINED;
    }

    /**
     * Whether we should apply the min aspect ratio per-app override only when an app is connected
     * to the camera.
     */
    static boolean shouldOverrideMinAspectRatioForCamera(@NonNull ActivityRecord activityRecord) {
        return AppCompatCameraPolicy.isCameraRunningAndWindowingModeEligible(activityRecord)
                && activityRecord.mAppCompatController.getCameraOverrides()
                        .isOverrideMinAspectRatioForCameraEnabled();
    }
}
