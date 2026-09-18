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

package com.android.wm.shell.transition;

import android.annotation.NonNull;
import android.graphics.RectF;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerToken;

import com.android.wm.shell.common.ShellExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * An animation impl that doesn't do anything besides finish immediately.
 */
public class NoAnimation implements ITransitionAnimation {
    private TransitionInfo mInfo = null;
    private final ShellExecutor mFinishExecutor;

    public NoAnimation(@NonNull ShellExecutor finishExecutor) {
        mFinishExecutor = finishExecutor;
    }

    @NonNull
    @Override
    public DetachResult detach(@NonNull List<WindowContainerToken> containers,
            @NonNull SurfaceControl.Transaction startTransaction) {
        final ArrayList<WindowAnimationState> states = new ArrayList<>(containers.size());
        for (int i = 0; i < containers.size(); ++i) {
            final WindowAnimationState state = new WindowAnimationState();
            state.bounds = new RectF(mInfo.getChange(containers.get(i)).getEndAbsBounds());
            state.scale = 1.f;
            state.timestamp = System.currentTimeMillis();
            states.add(state);
        }
        return new DetachResult(states);
    }

    @Override
    public void start(@NonNull TransitionInfo info,
            @NonNull List<WindowAnimationState> from,
            @NonNull IFinishedCallback onFinished) {
        mInfo = info;
        mFinishExecutor.executeDelayed(() -> {
            mInfo = null;
            onFinished.onFinished(null /* finishT */);
        }, 0);
    }

    @NonNull
    @Override
    public String getDebugName() {
        return "NoAnim:" + hashCode();
    }
}
