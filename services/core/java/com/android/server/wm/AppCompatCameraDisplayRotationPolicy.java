/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSET;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.content.pm.ActivityInfo.screenOrientationToString;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_UNDEFINED;
import static android.view.Display.TYPE_INTERNAL;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_CAMERA_COMPAT;
import static com.android.server.wm.AppCompatCameraPolicy.TAG_CAMERA_COMPAT;
import static com.android.server.wm.AppCompatConfiguration.MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;
import static com.android.server.wm.DisplayRotationReversionController.REVERSION_TYPE_CAMERA_COMPAT;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringRes;
import android.content.pm.ActivityInfo.ScreenOrientation;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.util.SparseArray;
import android.widget.Toast;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.server.UiThread;
import com.android.window.flags.Flags;

import java.io.PrintWriter;

/**
 * Controls camera compatibility treatment that handles orientation mismatch between camera
 * buffers and an app window for a particular display that can lead to camera issues like sideways
 * or stretched viewfinder.
 *
 * <p>This includes force rotation of fixed orientation activities connected to the camera.
 *
 * <p>The treatment is enabled for internal displays that have {@code ignoreOrientationRequest}
 * display setting enabled and when {@code
 * R.bool.config_isWindowManagerCameraCompatTreatmentEnabled} is {@code true}.
 */
// TODO(b/261444714): Consider moving Camera-specific logic outside of the WM Core path
final class AppCompatCameraDisplayRotationPolicy implements AppCompatCameraStatePolicy,
        ActivityRefresher.Evaluator {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "AppCompatCameraDRPolicy" : TAG_WM;

    @NonNull
    private final WindowManagerService mWmService;
    @NonNull
    private final CameraStateMonitor mCameraStateMonitor;
    @NonNull
    private final AppCompatCameraStateSource mCameraStateNotifier;
    @NonNull
    private final ActivityRefresher mActivityRefresher;

    @ScreenOrientation
    private int mLastReportedOrientation = SCREEN_ORIENTATION_UNSET;

    /**
     * Value toggled on {@link #start()} to {@code true} and on {@link #dispose()} to {@code false}.
     */
    private boolean mIsRunning;

    // Display id which is currently affected by the treatment. This structure is used to revert the
    // treatment in case a task is moved to another display, or activity is destroyed, therefore no
    // longer attached to a display when camera is closed.
    // Key is the taskId that opened camera.
    // TODO(b/492518230): notify the policy when display changes and update mActiveCameraCompat.
    private final SparseArray<Integer> mActiveCameraCompat = new SparseArray<>();
    AppCompatCameraDisplayRotationPolicy(@NonNull WindowManagerService wmService,
            @NonNull CameraStateMonitor cameraStateMonitor,
            @NonNull AppCompatCameraStateSource cameraStateNotifier,
            @NonNull ActivityRefresher activityRefresher) {
        // This constructor is called from WindowManagerService constructor.
        mWmService = wmService;
        mCameraStateMonitor = cameraStateMonitor;
        mCameraStateNotifier = cameraStateNotifier;
        mActivityRefresher = activityRefresher;
    }

    void start() {
        mCameraStateNotifier.addCameraStatePolicy(this);
        mActivityRefresher.addEvaluator(this);
        mIsRunning = true;
    }

    /** Releases camera state listener. */
    void dispose() {
        mCameraStateNotifier.removeCameraStatePolicy(this);
        mActivityRefresher.removeEvaluator(this);
        mIsRunning = false;
    }

    @VisibleForTesting
    boolean isRunning() {
        return mIsRunning;
    }

    static boolean isPolicyEnabled(@NonNull WindowManagerService wmService) {
        return !(Flags.cameraCompatUnifyCameraPolicies()
                && wmService.mAppCompatConfiguration
                        .isCameraCompatSimReqOrientationTreatmentEnabled())
                && wmService.mAppCompatConfiguration
                        .isCameraCompatForceRotateTreatmentEnabled();
    }

    /**
     * Determines orientation for Camera compatibility.
     *
     * <p>The goal of this function is to compute a orientation which would align orientations of
     * portrait app window and natural orientation of the device and set opposite to natural
     * orientation for a landscape app window. This is one of the strongest assumptions that apps
     * make when they implement camera previews. Since app and natural display orientations aren't
     * guaranteed to match, the rotation can cause letterboxing.
     *
     * <p>If treatment isn't applicable returns {@link SCREEN_ORIENTATION_UNSPECIFIED}. See {@link
     * #isTreatmentEnabledForDisplay} for conditions enabling the treatment.
     */
    @ScreenOrientation
    int getOrientation(@NonNull DisplayContent displayContent) {
        mLastReportedOrientation = getOrientationInternal(displayContent);
        if (mLastReportedOrientation != SCREEN_ORIENTATION_UNSPECIFIED) {
            rememberOverriddenOrientationIfNeeded(displayContent);
        } else {
            restoreOverriddenOrientationIfNeeded(displayContent);
        }
        return mLastReportedOrientation;
    }

    float getCameraCompatAspectRatio(@NonNull ActivityRecord unusedActivity) {
        // This policy does not apply camera compat aspect ratio by default, only via overrides.
        return MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;
    }

    @ScreenOrientation
    private synchronized int getOrientationInternal(@NonNull DisplayContent displayContent) {
        if (!isTreatmentEnabledForDisplay(displayContent)) {
            return SCREEN_ORIENTATION_UNSPECIFIED;
        }
        final ActivityRecord topActivity = displayContent.topRunningActivity(
                /* considerKeyguardState= */ true);
        if (!isTreatmentEnabledForActivity(topActivity)) {
            return SCREEN_ORIENTATION_UNSPECIFIED;
        }
        boolean isPortraitActivity =
                topActivity.getRequestedConfigurationOrientation() == ORIENTATION_PORTRAIT;
        boolean isNaturalDisplayOrientationPortrait =
                displayContent.getNaturalOrientation() == ORIENTATION_PORTRAIT;
        // Rotate portrait-only activity in the natural orientation of the displays (and in the
        // opposite to natural orientation for landscape-only) since many apps assume that those
        // are aligned when they compute orientation of the preview.
        // This means that even for a landscape-only activity and a device with landscape natural
        // orientation this would return SCREEN_ORIENTATION_PORTRAIT because an assumption that
        // natural orientation = portrait window = portrait camera is the main wrong assumption
        // that apps make when they implement camera previews so landscape windows need be
        // rotated in the orientation opposite to the natural one even if it's portrait.
        // TODO(b/261475895): Consider allowing more rotations for "sensor" and "user" versions
        // of the portrait and landscape orientation requests.
        final int orientation = (isPortraitActivity && isNaturalDisplayOrientationPortrait)
                || (!isPortraitActivity && !isNaturalDisplayOrientationPortrait)
                ? SCREEN_ORIENTATION_PORTRAIT
                : SCREEN_ORIENTATION_LANDSCAPE;
        ProtoLog.v(WM_DEBUG_CAMERA_COMPAT,
                "%s: Display id=%d is ignoring all orientation requests, camera is active "
                        + "and the top activity is eligible for force rotation, return %s,"
                        + "portrait activity: %b, is natural orientation portrait: %b.",
                TAG_CAMERA_COMPAT,
                displayContent.mDisplayId, screenOrientationToString(orientation),
                isPortraitActivity, isNaturalDisplayOrientationPortrait);
        return orientation;
    }

    /**
     * Notifies that animation in {@link ScreenRotationAnimation} has finished.
     *
     * <p>This class uses this signal as a trigger for notifying the user about forced rotation
     * reason with the {@link Toast}.
     */
    void onScreenRotationAnimationFinished(@NonNull DisplayContent displayContent) {
        final ActivityRecord topActivity = displayContent.topRunningActivity(
                /* considerKeyguardState= */ true);
        if (!isTreatmentEnabledForDisplay(displayContent)
                || !isTreatmentEnabledForActivity(topActivity)) {
            return;
        }
        showToast(R.string.display_rotation_camera_compat_toast_after_rotation);
    }

    void dump(@NonNull ActivityRecord activity, @NonNull PrintWriter pw, @NonNull String prefix) {
        pw.println(prefix + "AppCompatCameraDisplayRotationPolicy:");
        final boolean isTreatmentEnabledForDisplay = activity.getDisplayContent() != null
                && isTreatmentEnabledForDisplay(activity.getDisplayContent());
        pw.println(prefix + "  isTreatmentEnabledForDisplay=" + isTreatmentEnabledForDisplay);
        if (isTreatmentEnabledForDisplay) {
            pw.println(prefix + "  mLastReportedOrientation="
                    + screenOrientationToString(mLastReportedOrientation));
            pw.println(prefix + " isTreatmentEnabledForActivity="
                    + isTreatmentEnabledForActivity(activity));
        }
    }

    String getSummaryForDisplayRotationHistoryRecord(@NonNull DisplayContent displayContent) {
        String summaryIfEnabled = "";
        if (isTreatmentEnabledForDisplay(displayContent)) {
            ActivityRecord topActivity = displayContent.topRunningActivity(
                    /* considerKeyguardState= */ true);
            summaryIfEnabled =
                    " mLastReportedOrientation="
                            + screenOrientationToString(mLastReportedOrientation)
                            + " topActivity="
                            + (topActivity == null ? "null" : topActivity.shortComponentName)
                            + " isTreatmentEnabledForActivity="
                            + isTreatmentEnabledForActivity(topActivity)
                            + "mCameraStateMonitor=" + mCameraStateMonitor;
        }
        return "DisplayRotationCompatPolicy{"
                + " isTreatmentEnabledForDisplay=" + isTreatmentEnabledForDisplay(displayContent)
                + summaryIfEnabled
                + " }";
    }

    private void restoreOverriddenOrientationIfNeeded(@NonNull DisplayContent displayContent) {
        if (!isOrientationOverridden(displayContent)) {
            return;
        }
        if (displayContent.getRotationReversionController().revertOverride(
                REVERSION_TYPE_CAMERA_COMPAT)) {
            ProtoLog.v(WM_DEBUG_CAMERA_COMPAT,
                    "%s: Reverting orientation after camera compat force rotation",
                    TAG_CAMERA_COMPAT);
            // Reset last orientation source since we have reverted the orientation.
            displayContent.mLastOrientationSource = null;
        }
    }

    private boolean isOrientationOverridden(@NonNull DisplayContent displayContent) {
        return displayContent.getRotationReversionController().isOverrideActive(
                REVERSION_TYPE_CAMERA_COMPAT);
    }

    private void rememberOverriddenOrientationIfNeeded(@NonNull DisplayContent displayContent) {
        if (!isOrientationOverridden(displayContent)) {
            displayContent.getRotationReversionController().beforeOverrideApplied(
                    REVERSION_TYPE_CAMERA_COMPAT);
            ProtoLog.v(WM_DEBUG_CAMERA_COMPAT,
                    "%s: Saving original orientation before camera compat, last orientation is %d",
                    TAG_CAMERA_COMPAT, displayContent.getLastOrientation());
        }
    }

    // Refreshing only when configuration changes after rotation or camera split screen aspect ratio
    // treatment is enabled
    @Override
    public boolean shouldRefreshActivity(@NonNull ActivityRecord activity,
            @NonNull Configuration newConfig, @NonNull Configuration lastReportedConfig) {
        final boolean displayRotationChanged = newConfig.windowConfiguration.getDisplayRotation()
                != lastReportedConfig.windowConfiguration.getDisplayRotation();
        return activity.getDisplayContent() != null
                && isTreatmentEnabledForDisplay(activity.getDisplayContent())
                && isTreatmentEnabledForActivity(activity)
                && activity.mAppCompatController.getCameraOverrides()
                .shouldRefreshActivityForCameraCompat()
                && (displayRotationChanged
                || activity.mAppCompatController.getCameraOverrides()
                .isCameraCompatSplitScreenAspectRatioAllowed());
    }

    /**
     * Whether camera compat treatment is enabled for the display.
     *
     * <p>Conditions that need to be met:
     * <ul>
     *     <li>{@code R.bool.config_isWindowManagerCameraCompatTreatmentEnabled} is {@code true}.
     *     <li>Setting {@code ignoreOrientationRequest} is enabled for the display.
     *     <li>Associated {@link DisplayContent} is for internal display. See b/225928882
     *     that tracks supporting external displays in the future.
     * </ul>
     */
    private boolean isTreatmentEnabledForDisplay(@NonNull DisplayContent displayContent) {
        return mWmService.mAppCompatConfiguration.isCameraCompatForceRotateTreatmentEnabled()
                && displayContent.getIgnoreOrientationRequest()
                && displayContent.getDisplay().getType() == TYPE_INTERNAL;
    }

    boolean isActivityEligibleForOrientationOverride(@NonNull ActivityRecord activity) {
        return activity.getDisplayContent() != null
                && isTreatmentEnabledForDisplay(activity.getDisplayContent())
                && isCameraRunningAndWindowingModeEligible(activity, /* mustBeFullscreen */ true)
                && activity.mAppCompatController.getCameraOverrides()
                .shouldForceRotateForCameraCompat();
    }

    boolean shouldIgnoreReqOrientationForCameraCompat(@NonNull ActivityRecord activity) {
        return isTreatmentEnabledForActivity(activity);
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
    boolean isTreatmentEnabledForActivity(@Nullable ActivityRecord activity) {
        return isTreatmentEnabledForActivity(activity, /* mustBeFullscreen */ true);
    }

    boolean shouldCameraCompatControlAspectRatio(@NonNull ActivityRecord unusedActivity) {
        // This policy does not apply camera compat aspect ratio by default, only via overrides.
        return false;
    }

    boolean isCameraRunningAndWindowingModeEligible(@NonNull ActivityRecord activity,
            boolean mustBeFullscreen) {
        // Checking windowing mode on activity level because we don't want to
        // apply treatment in case of activity embedding.
        return (!mustBeFullscreen || !activity.inMultiWindowMode())
                && mCameraStateMonitor.isCameraRunningForActivity(activity);
    }

    private boolean isTreatmentEnabledForActivity(@Nullable ActivityRecord activity,
            boolean mustBeFullscreen) {
        return activity != null
                && isCameraRunningAndWindowingModeEligible(activity, mustBeFullscreen)
                && activity.getRequestedConfigurationOrientation() != ORIENTATION_UNDEFINED
                // "locked" and "nosensor" values are often used by camera apps that can't
                // handle dynamic changes so we shouldn't force rotate them.
                && activity.getOverrideOrientation() != SCREEN_ORIENTATION_NOSENSOR
                && activity.getOverrideOrientation() != SCREEN_ORIENTATION_LOCKED
                && activity.mAppCompatController.getCameraOverrides()
                .shouldForceRotateForCameraCompat();
    }

    @Override
    public void onCameraOpened(@NonNull CameraAppInfo cameraAppInfo,
            @NonNull WindowProcessController appProcess,
            @NonNull Task cameraTask) {
        final ActivityRecord cameraActivity = getTopActivity(cameraTask);
        if (cameraActivity == null) {
            ProtoLog.w(WM_DEBUG_CAMERA_COMPAT,
                    "%s: Camera activity is null in onCameraOpened().", TAG_CAMERA_COMPAT);
            return;
        }
        // Checking whether an activity in fullscreen rather than the task as this camera
        // compat treatment doesn't cover activity embedding.
        if (cameraActivity.getWindowingMode() == WINDOWING_MODE_FULLSCREEN) {
            mActiveCameraCompat.put(cameraAppInfo.mTaskId, cameraActivity.getDisplayId());
            recomputeConfigurationForCameraCompatIfNeeded(cameraActivity);
            cameraActivity.getDisplayContent().updateOrientation();
            return;
        }
        // Checking that the whole app is in multi-window mode as we shouldn't show toast
        // for the activity embedding case.
        if (cameraActivity.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW
                && isTreatmentEnabledForActivity(cameraActivity, /* mustBeFullscreen */ false)) {
            final PackageManager packageManager = mWmService.mContext.getPackageManager();
            try {
                showToast(
                        R.string.display_rotation_camera_compat_toast_in_multi_window,
                        (String) packageManager.getApplicationLabel(
                                packageManager.getApplicationInfo(cameraActivity.packageName,
                                        /* flags */ 0)));
            } catch (PackageManager.NameNotFoundException e) {
                ProtoLog.e(WM_DEBUG_CAMERA_COMPAT,
                        "%s: Multi-window toast not shown as "
                                + "package '%s' cannot be found.",
                        TAG_CAMERA_COMPAT, cameraActivity.packageName);
            }
        }
    }

    @VisibleForTesting
    void showToast(@StringRes int stringRes) {
        UiThread.getHandler().post(
                () -> Toast.makeText(mWmService.mContext, stringRes, Toast.LENGTH_LONG).show());
    }

    @VisibleForTesting
    void showToast(@StringRes int stringRes, @NonNull String applicationLabel) {
        UiThread.getHandler().post(
                () -> Toast.makeText(
                        mWmService.mContext,
                        mWmService.mContext.getString(stringRes, applicationLabel),
                        Toast.LENGTH_LONG).show());
    }

    @Override
    public boolean canCameraBeClosed(@NonNull CameraAppInfo cameraAppInfo, @NonNull Task task) {
        final ActivityRecord topActivity = getTopActivity(task);

        if (topActivity == null) {
            return true;
        }

        synchronized (this) {
            if (isActivityForCameraIdRefreshing(topActivity, cameraAppInfo.mCameraId)) {
                ProtoLog.v(WM_DEBUG_CAMERA_COMPAT,
                        "%s: Policy notified that camera is closed but activity is"
                                + " still refreshing. Rescheduling an update.", TAG_CAMERA_COMPAT);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onCameraClosed(@NonNull CameraAppInfo cameraAppInfo,
            @Nullable WindowProcessController appProcess, @Nullable Task task) {
        ProtoLog.v(WM_DEBUG_CAMERA_COMPAT,
                "%s: Display rotation policy is notified that Camera is closed, updating rotation.",
                TAG_CAMERA_COMPAT);

        final ActivityRecord topActivity = getTopActivity(task);
        // Checking whether an activity in fullscreen rather than the task as this camera compat
        // treatment doesn't cover activity embedding.
        if (topActivity == null || topActivity.getWindowingMode() != WINDOWING_MODE_FULLSCREEN) {
            mActiveCameraCompat.remove(cameraAppInfo.mTaskId);
            return;
        }
        recomputeConfigurationForCameraCompatIfNeeded(topActivity);
        if (mActiveCameraCompat.contains(cameraAppInfo.mTaskId)) {
            final int displayId = mActiveCameraCompat.removeReturnOld(cameraAppInfo.mTaskId);
            final DisplayContent displayContent = mWmService.mRoot.getDisplayContent(displayId);
            if (displayContent != null) {
                ProtoLog.v(WM_DEBUG_CAMERA_COMPAT, "%s: Display id=%d is notified that Camera"
                        + " is closed, updating rotation.", TAG_CAMERA_COMPAT, displayId);
                displayContent.updateOrientation();
            }
        }
    }

    // TODO(b/336474959): Do we need cameraId here?
    private boolean isActivityForCameraIdRefreshing(@NonNull ActivityRecord activity,
            @NonNull String cameraId) {
        if (!isTreatmentEnabledForActivity(activity)
                || !mCameraStateMonitor.isCameraWithIdRunningForActivity(activity, cameraId)) {
            return false;
        }
        return mActivityRefresher.isActivityRefreshing(activity);
    }

    private void recomputeConfigurationForCameraCompatIfNeeded(
            @NonNull ActivityRecord activityRecord) {
        if (shouldRecomputeConfigurationForCameraCompat(activityRecord)) {
            activityRecord.recomputeConfiguration();
        }
    }

    @Nullable
    private ActivityRecord getTopActivity(@Nullable Task cameraTask) {
        return cameraTask != null ? cameraTask.getTopActivity(
                /* includeFinishing= */ true, /* includeOverlays= */ false) : null;
    }

    /**
     * @return {@code true} if the configuration needs to be recomputed after a camera state update.
     */
    private boolean shouldRecomputeConfigurationForCameraCompat(
            @NonNull ActivityRecord activityRecord) {
        final AppCompatCameraOverrides overrides = activityRecord.mAppCompatController
                .getCameraOverrides();
        return overrides.isOverrideOrientationOnlyForCameraEnabled()
                || overrides.isCameraCompatSplitScreenAspectRatioAllowed()
                || shouldOverrideMinAspectRatio(activityRecord);
    }

    private boolean shouldOverrideMinAspectRatio(@NonNull ActivityRecord activityRecord) {
        return activityRecord.mAppCompatController.getCameraOverrides()
                .isOverrideMinAspectRatioForCameraEnabled()
                && isCameraRunningAndWindowingModeEligible(activityRecord,
                /* mustBeFullscreen= */ true);
    }
}
