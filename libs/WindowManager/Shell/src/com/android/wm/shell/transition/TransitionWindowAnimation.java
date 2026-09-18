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

import android.animation.ValueAnimator;
import android.util.ArraySet;
import android.view.SurfaceControl;
import android.view.animation.Animation;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerToken;

import androidx.annotation.NonNull;

import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.annotations.ShellAnimationThread;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Composite animation container that coordinates multiple WindowAnimations for a single transition.
 */
public class TransitionWindowAnimation implements ITransitionAnimation {
    private float mScale = 1.0f;
    private final List<WindowAnimation> mAnimations = new ArrayList<>();
    private final Set<WindowAnimation> mFinished = new ArraySet<>();
    private final ShellExecutor mAnimExecutor;
    private final ShellExecutor mMainExecutor;

    TransitionWindowAnimation(@NonNull ShellExecutor mainExecutor,
            @NonNull ShellExecutor animExecutor) {
        mMainExecutor = mainExecutor;
        mAnimExecutor = animExecutor;
    }

    /**
     * Adds a {@link WindowAnimation} to the animations list.
     */
    public void addWindowAnimation(@NonNull WindowAnimation anim) {
        mAnimations.add(anim);
    }

    @NonNull
    @Override
    public DetachResult detach(@NonNull List<WindowContainerToken> containers,
            @NonNull SurfaceControl.Transaction startTransaction) {
        List<WindowAnimationState> states = new ArrayList<>();
        for (int j = 0; j < mAnimations.size(); ++j) {
            WindowAnimation anim = mAnimations.get(j);
            if (containers.contains(anim.mChange.getContainer())) {
                anim.cancelRemoveListeners();
                states.add(anim.getWindowAnimationState());
            }
        }
        return new DetachResult(states);
    }

    @Override
    @ShellAnimationThread
    public void start(@NonNull TransitionInfo info, @NonNull List<WindowAnimationState> from,
            @NonNull IFinishedCallback onFinished) {
        if (mAnimations.isEmpty()) {
            onFinished.onFinished(null);
            return;
        }

        final Consumer<WindowAnimation> finishCallback = (anim) -> {
            if (mFinished.add(anim) && mFinished.size() == mAnimations.size()) {
                onFinished.onFinished(null);
            }
        };

        updateAnimScale();
        for (int j = 0; j < mAnimations.size(); j++) {
            final WindowAnimation anim = mAnimations.get(j);
            anim.addFinishCallback(finishCallback, mMainExecutor);
            mAnimExecutor.execute(anim::start);
        }

        if (mFinished.size() == mAnimations.size()) {
            onFinished.onFinished(null);
        }
    }

    @Override
    public void setAnimScaleSetting(float scale) {
        mScale = scale;
    }

    private void updateAnimScale() {
        for (int j = 0; j < mAnimations.size(); j++) {
            WindowAnimation anim = mAnimations.get(j);
            Animation animation = anim.getAnimation();
            ValueAnimator animator = anim.getAnimator();
            if (animation != null) {
                animation.scaleCurrentDuration(mScale);
            }
            long newDuration = animation != null ? animation.computeDurationHint()
                    : (long) (animator.getDuration() * mScale);
            animator.setDuration(newDuration);
        }
    }

    @NonNull
    @Override
    public String getDebugName() {
        return "TransitionWindowAnimation";
    }
}
