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

import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE;

import android.content.Context;
import android.graphics.Rect;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip.tv.TvPipBoundsState;
import com.android.wm.shell.pip2.phone.PipTransitionState;

/**
 * Scheduler for Shell-initiated PiP transitions on TV.
 */
public class TvPipScheduler {
    private static final String TAG = "TvPipScheduler";

    private final Context mContext;
    private final PipTransitionState mPipTransitionState;
    private final PipTransitionController mPipTransitionController;
    private final TvPipBoundsState mTvPipBoundsState;
    private final ShellExecutor mMainExecutor;

    public TvPipScheduler(Context context, PipTransitionState pipTransitionState,
            PipTransitionController pipTransitionController,
            TvPipBoundsState tvPipBoundsState,
            ShellExecutor mainExecutor) {
        mContext = context;
        mPipTransitionState = pipTransitionState;
        mPipTransitionController = pipTransitionController;
        mTvPipBoundsState = tvPipBoundsState;
        mMainExecutor = mainExecutor;
    }

    /**
     * Schedules a transition to change the PiP bounds.
     * @param toBounds The new bounds for the PiP window.
     * @param duration The duration of the animation.
     */
    public void scheduleAnimateResizePip(Rect toBounds, int duration) {
        WindowContainerToken pipTaskToken = mPipTransitionState.getPipTaskToken();
        ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                "%s: scheduleAnimateResizePip()", TAG);
        if (pipTaskToken == null || !mPipTransitionState.isInPip()) {
            return;
        }
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setBounds(pipTaskToken, toBounds);
        wct.setCanDropDuringDisplayChange(true);
        mPipTransitionController.startPipBoundsChangeTransition(wct, duration);
    }

    /**
     * Schedules remove PiP transition.
     * @param withFadeout Whether to fade out the PiP window.
     */
    public void scheduleRemovePip(boolean withFadeout) {
        ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                "%s: scheduleRemovePip()", TAG);
        mMainExecutor.execute(() -> {
            if (!mPipTransitionState.isInPip()) return;
            mPipTransitionController.startRemoveTransition(getRemovePipTransaction(), withFadeout);
        });
    }

    @Nullable
    private WindowContainerTransaction getRemovePipTransaction() {
        WindowContainerToken pipTaskToken = mPipTransitionState.getPipTaskToken();
        if (pipTaskToken == null) {
            return null;
        }
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setBounds(pipTaskToken, null);
        wct.setWindowingMode(pipTaskToken, WINDOWING_MODE_UNDEFINED);
        wct.reorder(pipTaskToken, false);
        return wct;
    }


    /**
     * Schedules exit PiP via expand transition.
     *
     * @param wasVisible Whether the underlying activity was visible before entering PiP.
     */
    public void scheduleExitPipViaExpand(boolean wasVisible) {
        ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                "%s: scheduleExitViaExpandPip()", TAG);
        mMainExecutor.execute(() -> {
            if (!mPipTransitionState.isInPip()) return;
            mPipTransitionController.startExpandTransition(getExitPipViaExpandTransaction(),
                    /* toSplit= */ false, wasVisible);
        });
    }

    @Nullable
    private WindowContainerTransaction getExitPipViaExpandTransaction() {
        WindowContainerToken pipTaskToken = mPipTransitionState.getPipTaskToken();
        if (pipTaskToken == null) {
            return null;
        }
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setBounds(pipTaskToken, null);
        wct.setWindowingMode(pipTaskToken, WINDOWING_MODE_UNDEFINED);
        return wct;
    }
}
