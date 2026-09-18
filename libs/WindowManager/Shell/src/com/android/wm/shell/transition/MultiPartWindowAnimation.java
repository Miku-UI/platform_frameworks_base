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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.NonNull;
import android.util.ArraySet;

import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.annotations.ShellAnimationThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A {@link WindowAnimation} that coordinates multiple animations.
 */
class MultiPartWindowAnimation extends WindowAnimation {
    private final Set<Animator> mFinished = new ArraySet<>();
    private final List<Animator> mAnimators = new ArrayList<>();

    @ShellMainThread
    MultiPartWindowAnimation(@NonNull WindowAnimation main,
            @NonNull List<Animator> siblings) {
        super(main.mChange, main.mCornerRadius, main.getAnimation(), main.getAnimator());
        mAnimators.add(main.getAnimator());
        mAnimators.addAll(siblings);
    }

    @ShellMainThread
    @Override
    public void addFinishCallback(@NonNull Consumer<WindowAnimation> finishCallback,
            @NonNull ShellExecutor mainExecutor) {
        if (mAnimators.isEmpty()) {
            mainExecutor.execute(() -> finishCallback.accept(this));
            return;
        }

        final AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mFinished.add(animation) && mFinished.size() == mAnimators.size()) {
                    mainExecutor.execute(
                            () -> finishCallback.accept(MultiPartWindowAnimation.this));
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEnd(animation);
            }
        };

        for (int i = 0; i < mAnimators.size(); i++) {
            final Animator anim = mAnimators.get(i);
            anim.addListener(listener);
        }
    }

    @ShellAnimationThread
    @Override
    void start() {
        mAnimators.forEach(Animator::start);
    }

    @ShellAnimationThread
    @Override
    void end() {
        for (int k = 0; k < mAnimators.size(); k++) {
            var anim = mAnimators.get(k);
            if (!mFinished.contains(anim)) {
                anim.end();
            }
        }
    }
}
