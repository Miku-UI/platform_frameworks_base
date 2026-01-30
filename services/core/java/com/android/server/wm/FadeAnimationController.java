/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.internal.perfetto.protos.Animationadapter.AnimationSpecProto.WINDOW;
import static android.internal.perfetto.protos.Animationadapter.WindowAnimationSpecProto.ANIMATION;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.proto.ProtoOutputStream;
import android.view.SurfaceControl;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;

import java.io.PrintWriter;

/**
 * An animation controller to fade-in/out for a window token.
 */
class FadeAnimationController {
    static final int SHORT_DURATION_MS = 200;
    static final int MEDIUM_DURATION_MS = 350;

    @NonNull
    final DisplayContent mDisplayContent;

    FadeAnimationController(@NonNull DisplayContent displayContent) {
        mDisplayContent = displayContent;
    }

    /**
     * @return a fade-in Animation.
     */
    @NonNull
    public Animation getFadeInAnimation() {
        final AlphaAnimation anim = new AlphaAnimation(0f, 1f);
        anim.setDuration(getScaledDuration(MEDIUM_DURATION_MS));
        anim.setInterpolator(new DecelerateInterpolator());
        return anim;
    }

    /**
     * @return a fade-out Animation.
     */
    @NonNull
    public Animation getFadeOutAnimation() {
        final AlphaAnimation anim = new AlphaAnimation(1f, 0f);
        anim.setDuration(getScaledDuration(SHORT_DURATION_MS));
        anim.setInterpolator(new AccelerateInterpolator());
        return anim;
    }

    long getScaledDuration(int durationMs) {
        return (long) (durationMs * mDisplayContent.getWindowAnimationScaleLocked());
    }

    /** Run the fade in/out animation for a window token. */
    void fadeWindowToken(boolean show, @NonNull WindowToken windowToken,
            @SurfaceAnimator.AnimationType int animationType) {
        fadeWindowToken(show, windowToken, animationType, null);
    }

    /**
     * Run the fade in/out animation for a window token.
     *
     * @param show true for fade-in, otherwise for fade-out.
     * @param windowToken the window token to run the animation.
     * @param animationType the animation type defined in SurfaceAnimator.
     * @param finishedCallback the callback after the animation finished.
     */
    void fadeWindowToken(boolean show, @Nullable WindowToken windowToken,
            @SurfaceAnimator.AnimationType int animationType,
            @Nullable SurfaceAnimator.OnAnimationFinishedCallback finishedCallback) {
        if (windowToken == null || windowToken.getParent() == null) {
            return;
        }

        final Animation animation = show ? getFadeInAnimation() : getFadeOutAnimation();
        final FadeAnimationAdapter animationAdapter =
                createAdapter(createAnimationSpec(animation), show, windowToken);
        windowToken.startAnimation(windowToken.getPendingTransaction(), animationAdapter,
                show /* hidden */, animationType, finishedCallback);
    }

    @NonNull
    FadeAnimationAdapter createAdapter(@NonNull LocalAnimationAdapter.AnimationSpec animationSpec,
            boolean show, @NonNull WindowToken windowToken) {
        return new FadeAnimationAdapter(animationSpec, windowToken.getSurfaceAnimationRunner(),
                show, windowToken);
    }

    @NonNull
    private LocalAnimationAdapter.AnimationSpec createAnimationSpec(@NonNull Animation animation) {
        return new LocalAnimationAdapter.AnimationSpec() {

            final Transformation mTransformation = new Transformation();

            @Override
            public boolean getShowWallpaper() {
                return true;
            }

            @Override
            public long getDuration() {
                return animation.getDuration();
            }

            @Override
            public void apply(@NonNull SurfaceControl.Transaction t, @NonNull SurfaceControl leash,
                    long currentPlayTime) {
                mTransformation.clear();
                animation.getTransformation(currentPlayTime, mTransformation);
                t.setAlpha(leash, mTransformation.getAlpha());
            }

            @Override
            public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
                pw.print(prefix);
                pw.println(animation);
            }

            @Override
            public void dumpDebugInner(@NonNull ProtoOutputStream proto) {
                final long token = proto.start(WINDOW);
                proto.write(ANIMATION, animation.toString());
                proto.end(token);
            }
        };
    }

    static class FadeAnimationAdapter extends LocalAnimationAdapter {
        protected final boolean mShow;
        @NonNull
        protected final WindowToken mToken;

        FadeAnimationAdapter(@NonNull AnimationSpec windowAnimationSpec,
                @NonNull SurfaceAnimationRunner surfaceAnimationRunner, boolean show,
                @NonNull WindowToken token) {
            super(windowAnimationSpec, surfaceAnimationRunner);
            mShow = show;
            mToken = token;
        }

        @Override
        public boolean shouldDeferAnimationFinish(@NonNull Runnable endDeferFinishCallback) {
            // Defer the finish callback (restore leash) of the hide animation to ensure the token
            // stay hidden until it needs to show again. Besides, when starting the show animation,
            // the previous hide animation will be cancelled, so the callback can be ignored.
            return !mShow;
        }
    }
}
