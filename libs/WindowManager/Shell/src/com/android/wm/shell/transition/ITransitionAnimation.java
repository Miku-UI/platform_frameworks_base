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
import android.annotation.Nullable;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerToken;

import com.android.wm.shell.shared.annotations.ShellMainThread;

import java.util.List;

/**
 * Plays an animation for a set of containers from a start state to a destination. Additionally,
 * supports detaching a subset of the containers mid-animation and providing their current
 * animation state.
 */
public interface ITransitionAnimation {
    /**
     * Stops animating {@param containers} and return their current animating state. The
     * returned {@link DetachResult} can be a promise if detaching is async. Ideally, provide
     * a synchronous result.
     *
     * This animator MUST stop touching {@param containers} once it has completed the detach
     * result.
     *
     * @return the state of all containers in {@param containers} in the same order.
     */
    @NonNull
    DetachResult detach(@NonNull List<WindowContainerToken> containers,
            @NonNull SurfaceControl.Transaction startTransaction);

    /**
     * Called to start playing an animation for some transition info.
     */
    void start(@NonNull TransitionInfo info, @NonNull List<WindowAnimationState> from,
            @NonNull IFinishedCallback onFinished);

    /**
     * Applies a scale factor to the animation duration.
     * Called just before {@link #start}.
     */
    default void setAnimScaleSetting(float scale) {}

    /** for debugging/logging */
    @NonNull String getDebugName();

    /** Interface for an animator to notify when its animation has finished. */
    @FunctionalInterface
    interface IFinishedCallback {
        /**
         * Called when an animation has finished. Caller must not manipulate the corresponding
         * surfaces after calling this.
         */
        @ShellMainThread
        void onFinished(@Nullable SurfaceControl.Transaction finishT);
    }
}
