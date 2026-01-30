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

import static android.app.servertransaction.ActivityLifecycleItem.ON_PAUSE;
import static android.app.servertransaction.ActivityLifecycleItem.ON_STOP;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_STATES;
import static com.android.server.wm.AppCompatCameraOverrides.NONE;
import static com.android.server.wm.AppCompatCameraOverrides.IN_PROGRESS;
import static com.android.server.wm.AppCompatCameraOverrides.REQUESTED;

import android.annotation.NonNull;
import android.app.servertransaction.RefreshCallbackItem;
import android.app.servertransaction.ResumeActivityItem;
import android.content.res.Configuration;
import android.os.Handler;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.ArrayUtils;
import com.android.window.flags.Flags;

import java.util.ArrayList;

/**
 * Class that refreshes the activity (through stop/pause -> resume) based on configuration change.
 *
 * <p>{@link ActivityRefresher} cycles the activity through either stop or pause and then resume,
 * based on the global config and per-app override.
 */
class ActivityRefresher {
    // Delay for ensuring that onActivityRefreshed is always called after an activity refresh. The
    // client process may not always report the event back to the server, such as process is
    // crashed or got killed.
    private static final long REFRESH_CALLBACK_TIMEOUT_MS = 2000L;

    @NonNull private final WindowManagerService mWmService;
    @NonNull private final Handler mHandler;
    // TODO(b/395063101): remove once external camera sandboxing is launched.
    @NonNull private final ArrayList<Evaluator> mEvaluators = new ArrayList<>();

    ActivityRefresher(@NonNull WindowManagerService wmService, @NonNull Handler handler) {
        mWmService = wmService;
        mHandler = handler;
    }

    // TODO(b/395063101): remove once external display sandboxing for camera is launched.
    void addEvaluator(@NonNull Evaluator evaluator) {
        mEvaluators.add(evaluator);
    }

    // TODO(b/395063101): remove once external display sandboxing for camera is launched.
    void removeEvaluator(@NonNull Evaluator evaluator) {
        mEvaluators.remove(evaluator);
    }

    /**
     * If the activity refresh is requested, mark as pending since activity is already relaunching.
     *
     * <p>This is to avoid unnecessary refresh (i.e. cycling activity though stop/pause -> resume),
     * if refresh is requested for camera compat.
     *
     * <p>As camera connection will most likely close and reopen due to relaunch/refresh - which is
     * the goal, to setup camera with new parameters - this method is setting state to
     * {@code IN_PROGRESS} instead of {@code NONE} to avoid unnecessary tear down and setup of
     * camera compat mode.
     */
    void onActivityRelaunching(@NonNull ActivityRecord activity) {
        if (getActivityRefreshState(activity) == REQUESTED) {
            // No need to cycle through stop/pause -> resume if the activity is already relaunching
            // due to config change.
            activity.mAppCompatController.getCameraOverrides().setActivityRefreshState(IN_PROGRESS);
        }
    }

    void requestRefresh(@NonNull ActivityRecord activity) {
        activity.mAppCompatController.getCameraOverrides().setActivityRefreshState(REQUESTED);
    }

    // TODO(b/395063101): remove once external display sandboxing for camera is launched.
    /**
     * "Refreshes" activity by going through "stopped -> resumed" or "paused -> resumed" cycle.
     * This allows to clear cached values in apps (e.g. display or camera rotation) that influence
     * camera preview and can lead to sideways or stretching issues persisting even after force
     * rotation.
     */
    void onActivityConfigurationChanging(@NonNull ActivityRecord activity,
            @NonNull Configuration newConfig, @NonNull Configuration lastReportedConfig) {
        if (!shouldRefreshActivity(activity, newConfig, lastReportedConfig)) {
            return;
        }

        activity.mAppCompatController.getCameraOverrides().setIsRefreshRequested(true);
        if (refreshActivity(activity)) {
            scheduleClearIsRefreshing(activity);
        } else {
            activity.mAppCompatController.getCameraOverrides().setIsRefreshRequested(false);
        }
    }

    /**
     * "Refreshes" activity by going through "stopped -> resumed" or "paused -> resumed" cycle.
     * This allows to clear cached values in apps (e.g. display or camera rotation) that influence
     * camera preview and can lead to sideways or stretching issues persisting even after force
     * rotation.
     */
    void refreshActivityIfEnabled(@NonNull ActivityRecord activity) {
        if (!shouldRefreshActivity(activity)) {
            activity.mAppCompatController.getCameraOverrides().setActivityRefreshState(NONE);
            return;
        }

        if (getActivityRefreshState(activity) == REQUESTED) {
            activity.mAppCompatController.getCameraOverrides().setActivityRefreshState(IN_PROGRESS);
            if (!refreshActivity(activity)) {
                clearRefreshState(activity);
                return;
            }
        }

        scheduleClearIsRefreshing(activity);
    }

    private boolean refreshActivity(@NonNull ActivityRecord activity) {
        final boolean cycleThroughStop =
                mWmService.mAppCompatConfiguration
                        .isCameraCompatRefreshCycleThroughStopEnabled()
                        && !activity.mAppCompatController.getCameraOverrides()
                        .shouldRefreshActivityViaPauseForCameraCompat();
        ProtoLog.v(WM_DEBUG_STATES,
                "Refreshing activity for freeform camera compatibility treatment, "
                        + "activityRecord=%s", activity);
        final RefreshCallbackItem refreshCallbackItem =
                new RefreshCallbackItem(activity.token, cycleThroughStop ? ON_STOP : ON_PAUSE);
        final ResumeActivityItem resumeActivityItem = new ResumeActivityItem(
                activity.token, /* isForward */ false, /* shouldSendCompatFakeFocus */ false);
        return activity.mAtmService.getLifecycleManager().scheduleTransactionItems(
                activity.app.getThread(), refreshCallbackItem, resumeActivityItem);
    }

    private void scheduleClearIsRefreshing(@NonNull ActivityRecord activity) {
        // Clear refresh state after a delay in case something goes wrong.
        mHandler.postDelayed(() -> {
            synchronized (mWmService.mGlobalLock) {
                onActivityRefreshed(activity);
            }
        }, REFRESH_CALLBACK_TIMEOUT_MS);
    }

    void clearRefreshState(@NonNull ActivityRecord activity) {
        if (Flags.enableCameraCompatSandboxDisplayRotationOnExternalDisplaysBugfix()) {
            activity.mAppCompatController.getCameraOverrides().setActivityRefreshState(NONE);
        }
        activity.mAppCompatController.getCameraOverrides().setIsRefreshRequested(false);
    }

    boolean isActivityRefreshing(@NonNull ActivityRecord activity) {
        return Flags.enableCameraCompatSandboxDisplayRotationOnExternalDisplaysBugfix()
                ? getActivityRefreshState(activity) == IN_PROGRESS
                : activity.mAppCompatController.getCameraOverrides().isRefreshRequested();
    }

    void onActivityRefreshed(@NonNull ActivityRecord activity) {
        // TODO(b/333060789): can we tell that refresh did not happen by observing the activity
        //  state?
        clearRefreshState(activity);
    }

    private boolean shouldRefreshActivity(@NonNull ActivityRecord activity) {
        return mWmService.mAppCompatConfiguration.isCameraCompatRefreshEnabled()
                && activity.mAppCompatController.getCameraOverrides()
                .shouldRefreshActivityForCameraCompat()
                && getActivityRefreshState(activity) != NONE;
    }

    // TODO(b/395063101): remove once external display sandboxing for camera is launched.
    private boolean shouldRefreshActivity(@NonNull ActivityRecord activity,
            @NonNull Configuration newConfig, @NonNull Configuration lastReportedConfig) {
        return mWmService.mAppCompatConfiguration.isCameraCompatRefreshEnabled()
                && activity.mAppCompatController.getCameraOverrides()
                .shouldRefreshActivityForCameraCompat()
                && ArrayUtils.find(mEvaluators.toArray(), evaluator ->
                ((Evaluator) evaluator)
                        .shouldRefreshActivity(activity, newConfig, lastReportedConfig)) != null;
    }

    private @AppCompatCameraOverrides.ActivityRefreshState int getActivityRefreshState(
            @NonNull ActivityRecord activity) {
        return activity.mAppCompatController.getCameraOverrides().getActivityRefreshState();
    }

    // TODO(b/395063101): remove once external display sandboxing for camera is launched.
    /**
     * Interface for classes that would like to refresh the recently updated activity, based on the
     * configuration change.
     */
    interface Evaluator {
        boolean shouldRefreshActivity(@NonNull ActivityRecord activity,
                @NonNull Configuration newConfig, @NonNull Configuration lastReportedConfig);
    }
}
