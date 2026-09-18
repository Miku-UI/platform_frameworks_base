/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.pip2.tv;

import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.content.Context;

import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.pip.PipParamsChangedForwarder;
import com.android.wm.shell.pip.tv.TvPipBoundsAlgorithm;
import com.android.wm.shell.pip.tv.TvPipBoundsState;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.pip.PipFlags;

import java.util.Objects;

/**
 * A Task Listener implementation for TV PiP2.
 */
public class TvPipTaskListener implements ShellTaskOrganizer.TaskListener {
    private static final String TAG = "TvPipTaskListener";
    private static final float EPSILON = 0.01f;

    private final PipTransitionState mPipTransitionState;
    private final PipParamsChangedForwarder mPipParamsChangedForwarder;
    private final ShellExecutor mMainExecutor;
    private final TvPipBoundsAlgorithm mTvPipBoundsAlgorithm;

    private PictureInPictureParams mPictureInPictureParams =
            new PictureInPictureParams.Builder().build();

    public TvPipTaskListener(Context context,
            ShellTaskOrganizer shellTaskOrganizer,
            PipTransitionState pipTransitionState,
            TvPipBoundsState tvPipBoundsState,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            PipParamsChangedForwarder pipParamsChangedForwarder,
            @ShellMainThread ShellExecutor mainExecutor) {
        mPipTransitionState = pipTransitionState;
        mPipParamsChangedForwarder = pipParamsChangedForwarder;
        mMainExecutor = mainExecutor;
        mTvPipBoundsAlgorithm = tvPipBoundsAlgorithm;

        if (PipFlags.isPip2ExperimentEnabled()) {
            mMainExecutor.execute(() -> {
                shellTaskOrganizer.addListenerForType(this,
                        ShellTaskOrganizer.TASK_LISTENER_TYPE_PIP);
            });
        }

        // Reset {@link #mPictureInPictureParams} after exiting PiP. For instance, next Activity
        // with null aspect ratio would accidentally inherit the aspect ratio from a previous
        // PiP Activity.
        tvPipBoundsState.addOnPipComponentChangedListener(((oldPipComponent, newPipComponent) ->
                mPictureInPictureParams = new PictureInPictureParams.Builder().build()));
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        PictureInPictureParams params = taskInfo.pictureInPictureParams;
        if (mPictureInPictureParams.equals(params)) {
            return;
        }
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onTaskInfoChanged: %s, state=%s oldParams=%s newParams=%s",
                TAG, taskInfo.topActivity, mPipTransitionState, mPictureInPictureParams, params);
        setPictureInPictureParams(params);
    }

    void setPictureInPictureParams(@Nullable PictureInPictureParams params) {
        if (params == null) {
            mPictureInPictureParams = new PictureInPictureParams.Builder().build();
            return;
        }
        if (mPictureInPictureParams.equals(params)) {
            return;
        }
        float newAspectRatio = params.getAspectRatioFloat();
        if (aspectRatioChanged(newAspectRatio, mPictureInPictureParams.getAspectRatioFloat())) {
            if (mTvPipBoundsAlgorithm.isValidPictureInPictureAspectRatio(newAspectRatio)) {
                mPipParamsChangedForwarder.notifyAspectRatioChanged(newAspectRatio);
            }
        }
        if (aspectRatioChanged(params.getExpandedAspectRatioFloat(),
                mPictureInPictureParams.getExpandedAspectRatioFloat())) {
            mPipParamsChangedForwarder.notifyExpandedAspectRatioChanged(
                    params.getExpandedAspectRatioFloat());
        }
        if (PipUtils.remoteActionsChanged(params.getActions(),
                mPictureInPictureParams.getActions())
                || !PipUtils.remoteActionsMatch(params.getCloseAction(),
                mPictureInPictureParams.getCloseAction())) {
            mPipParamsChangedForwarder.notifyActionsChanged(params.getActions(),
                    params.getCloseAction());
        }
        if (!Objects.equals(params.getTitle(), mPictureInPictureParams.getTitle())) {
            mPipParamsChangedForwarder.notifyTitleChanged(params.getTitle());
        }
        if (!Objects.equals(params.getSubtitle(), mPictureInPictureParams.getSubtitle())) {
            mPipParamsChangedForwarder.notifySubtitleChanged(params.getSubtitle());
        }
        mPictureInPictureParams = params;
    }

    /**
     * Checks if the aspect ratio has changed.
     */
    private boolean aspectRatioChanged(float aspectRatio1, float aspectRatio2) {
        return Math.abs(aspectRatio1 - aspectRatio2) > EPSILON;
    }
}
