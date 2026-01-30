/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.wm;

import static android.internal.perfetto.protos.Animationadapter.AnimationAdapterProto.LOCAL;
import static android.internal.perfetto.protos.Animationadapter.LocalAnimationAdapterProto.ANIMATION_SPEC;

import android.annotation.ColorInt;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.SystemClock;
import android.util.proto.ProtoOutputStream;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;

import com.android.server.wm.SurfaceAnimator.AnimationType;
import com.android.server.wm.SurfaceAnimator.OnAnimationFinishedCallback;

import java.io.PrintWriter;

/**
 * Animation that can be executed without holding the window manager lock. See
 * {@link SurfaceAnimationRunner}.
 */
class LocalAnimationAdapter implements AnimationAdapter {

    @NonNull
    private final AnimationSpec mSpec;

    @NonNull
    private final SurfaceAnimationRunner mAnimator;

    LocalAnimationAdapter(@NonNull AnimationSpec spec, @NonNull SurfaceAnimationRunner animator) {
        mSpec = spec;
        mAnimator = animator;
    }

    @Override
    public boolean getShowWallpaper() {
        return mSpec.getShowWallpaper();
    }

    @Override
    public boolean getShowBackground() {
        return mSpec.getShowBackground();
    }

    @ColorInt
    @Override
    public int getBackgroundColor() {
        return mSpec.getBackgroundColor();
    }

    @Override
    public void startAnimation(@NonNull SurfaceControl animationLeash, @NonNull Transaction t,
            @AnimationType int type, @NonNull OnAnimationFinishedCallback finishCallback) {
        mAnimator.startAnimation(mSpec, animationLeash, t,
                () -> finishCallback.onAnimationFinished(type, this));
    }

    @Override
    public void onAnimationCancelled(@Nullable SurfaceControl animationLeash) {
        mAnimator.onAnimationCancelled(animationLeash);
    }

    @Override
    public long getDurationHint() {
        return mSpec.getDuration();
    }

    @Override
    public long getStatusBarTransitionsStartTime() {
        return mSpec.calculateStatusBarTransitionStartTime();
    }

    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        mSpec.dump(pw, prefix);
    }

    @Override
    public void dumpDebug(@NonNull ProtoOutputStream proto) {
        final long token = proto.start(LOCAL);
        mSpec.dumpDebug(proto, ANIMATION_SPEC);
        proto.end(token);
    }

    /**
     * Describes how to apply an animation.
     */
    interface AnimationSpec {

        /**
         * @see AnimationAdapter#getShowWallpaper
         */
        default boolean getShowWallpaper() {
            return false;
        }

        /**
         * @see AnimationAdapter#getShowBackground
         */
        default boolean getShowBackground() {
            return false;
        }

        /**
         * @see AnimationAdapter#getBackgroundColor
         */
        @ColorInt
        default int getBackgroundColor() {
            return 0;
        }

        /**
         * @see AnimationAdapter#getStatusBarTransitionsStartTime
         */
        default long calculateStatusBarTransitionStartTime() {
            return SystemClock.uptimeMillis();
        }

        /**
         * @return The duration of the animation.
         */
        long getDuration();

        /**
         * Called when the spec needs to apply the current animation state to the leash.
         *
         * @param t               The transaction to use to apply a transform.
         * @param leash           The leash to apply the state to.
         * @param currentPlayTime The current time of the animation.
         */
        void apply(@NonNull Transaction t, @NonNull SurfaceControl leash, long currentPlayTime);

        /**
         * @see WindowAnimationSpec#canSkipFirstFrame
         */
        default boolean canSkipFirstFrame() {
            return false;
        }

        /**
         * @return {@code true} if we need to wake-up SurfaceFlinger earlier during this animation.
         *
         * @see Transaction#setEarlyWakeupStart and Transaction#setEarlyWakeupEnd
         */
        default boolean needsEarlyWakeup() { return false; }

        /**
         * @return The fraction of the animation, returns 1 if duration is 0.
         *
         * @param currentPlayTime The current play time.
         */
        default float getFraction(float currentPlayTime) {
            final float duration = getDuration();
            return duration > 0 ? currentPlayTime / duration : 1.0f;
        }

        void dump(@NonNull PrintWriter pw, @NonNull String prefix);

        default void dumpDebug(@NonNull ProtoOutputStream proto, long fieldId) {
            final long token = proto.start(fieldId);
            dumpDebugInner(proto);
            proto.end(token);
        }

        void dumpDebugInner(@NonNull ProtoOutputStream proto);

        @Nullable
        default WindowAnimationSpec asWindowAnimationSpec() {
            return null;
        }
    }
}
