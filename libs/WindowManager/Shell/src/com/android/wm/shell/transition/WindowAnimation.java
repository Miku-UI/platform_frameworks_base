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
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemProperties;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.annotations.ShellMainThread;

import java.util.function.Consumer;

/**
 * Keeps track of the animation of a single window/container.
 */
class WindowAnimation {
    private static final boolean DEBUG_WINDOW_ANIMATION_STATE = Build.IS_DEBUGGABLE
            && SystemProperties.getBoolean("persist.wm.debug.window_animation_state", false);
    private static final long VELOCITY_CALCULATION_THRESHOLD_MS = 10;

    /**
     * Constant that determines what percentage of the current animation playtime are we using to
     * estimate the velocity
     */
    private static final float VELOCITY_CALCULATION_INTERVAL_RELATIVE_SIZE = 0.1f;

    /**
     * Bias that determines how much are we centering our velocity calculation e.g. if it's 0.7f,
     * we are taking into account 30% of the past and 70% of the future
     *
     */
    private static final float VELOCITY_CALCULATION_BIAS = 0.7f;
    @NonNull
    final TransitionInfo.Change mChange;
    final float mCornerRadius;
    private final WindowAnimationState mWindowAnimationState = new WindowAnimationState();
    @NonNull
    private final ValueAnimator mAnimator;
    @Nullable
    private final Animation mAnimation;

    @ShellMainThread
    WindowAnimation(@NonNull TransitionInfo.Change change, float cornerRadius,
            @Nullable Animation animation,
            @NonNull ValueAnimator animator) {
        mChange = change;
        mCornerRadius = cornerRadius;
        mAnimation = animation;
        mAnimator = animator;
    }

    /**
     * Helper method to add a callback on the Animator for onAnimationEnd and onAnimationCancel with
     * a gurad that ensures it was called only once
     */
    @ShellMainThread
    public void addFinishCallback(@NonNull Consumer<WindowAnimation> finishCallback,
            @NonNull ShellExecutor mainExecutor) {
        mAnimator.addListener(new AnimatorListenerAdapter() {
            private void onFinish() {
                mainExecutor.execute(() -> finishCallback.accept(WindowAnimation.this));
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                onFinish();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onFinish();
            }
        });
    }

    @NonNull
    ValueAnimator getAnimator() {
        return mAnimator;
    }

    @Nullable
    Animation getAnimation() {
        return mAnimation;
    }

    void start() {
        mAnimator.start();
    }

    void end() {
        mAnimator.end();
    }

    void cancelRemoveListeners() {
        mAnimator.removeAllUpdateListeners();
        mAnimator.cancel();
    }

    /**
     * Builds a {@link WindowAnimationState} based on the current transformation and
     * animation properties.
     *
     * @return A {@link WindowAnimationState} representing the current state of the window
     * animation.
     */
    WindowAnimationState getWindowAnimationState() {
        final long currentPlayTime = Math.clamp(mAnimator.getCurrentPlayTime(), 0,
                mAnimator.getDuration());
        Transformation currentTransformation = new Transformation();
        mAnimation.getTransformation(currentPlayTime, currentTransformation);

        final RectF currentBoundsF = new RectF(mChange.getStartAbsBounds());
        currentTransformation.getMatrix().mapRect(currentBoundsF);
        var scale = getScaleOfTransformation(currentTransformation);
        var velocity = getTranslateVelocity(mAnimator, mAnimation, VELOCITY_CALCULATION_BIAS);

        mWindowAnimationState.bounds = currentBoundsF;
        // We assume that the scaling is uniform eg. scale.x == scale.y
        mWindowAnimationState.scale = scale.x;
        // We assume that the corners are the same everywhere
        mWindowAnimationState.bottomLeftRadius = mCornerRadius;
        mWindowAnimationState.topLeftRadius = mCornerRadius;
        mWindowAnimationState.bottomRightRadius = mCornerRadius;
        mWindowAnimationState.topRightRadius = mCornerRadius;
        mWindowAnimationState.timestamp = System.currentTimeMillis();
        mWindowAnimationState.velocityPxPerMs = velocity;
        if (DEBUG_WINDOW_ANIMATION_STATE) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "%s",
                    windowAnimationStateToString(mWindowAnimationState));
        }
        return mWindowAnimationState;
    }

    public static String windowAnimationStateToString(WindowAnimationState state) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("WindowAnimationState ");
        sb.append("{ bounds=");
        sb.append(state.bounds);
        sb.append(" scale=");
        sb.append(state.scale);
        sb.append(" topLeftRadius=");
        sb.append(state.topLeftRadius);
        sb.append(" topRightRadius=");
        sb.append(state.topRightRadius);
        sb.append(" bottomRightRadius=");
        sb.append(state.bottomRightRadius);
        sb.append(" bottomLeftRadius=");
        sb.append(state.bottomLeftRadius);
        sb.append(" timestamp=");
        sb.append(state.timestamp);
        sb.append(" velocityPxPerMs=");
        sb.append(state.velocityPxPerMs);
        sb.append('}');
        return sb.toString();
    }

    private PointF getTranslateVelocity(@NonNull ValueAnimator animator,
            @NonNull Animation animation, float bias) {

        final long duration = animator.getDuration();
        final long currentPlayTime = Math.clamp(animator.getCurrentPlayTime(), 0, duration);

        // If the animation just started or is near the end, we ignore the velocity as it would
        // be unnoticeable if we start a new animation with 0 velocity
        if (currentPlayTime <= VELOCITY_CALCULATION_THRESHOLD_MS
                || currentPlayTime >= duration - VELOCITY_CALCULATION_THRESHOLD_MS) {
            return new PointF(0, 0);
        }

        final float deltaTime = VELOCITY_CALCULATION_INTERVAL_RELATIVE_SIZE * currentPlayTime;
        long tStart = Math.max(0,
                (long) (currentPlayTime - (1 - bias) * deltaTime));
        long tEnd = Math.min(duration,
                (long) (currentPlayTime + bias * deltaTime));

        long effectiveDeltaTime = tEnd - tStart;

        Transformation endTransform = new Transformation();
        animation.getTransformation(tEnd, endTransform);

        Transformation startTransform = new Transformation();
        animation.getTransformation(tStart, startTransform);

        var endPosition = getPositionOfTransformation(endTransform);
        var startPosition = getPositionOfTransformation(startTransform);

        float velocityX = (endPosition.x - startPosition.x) / effectiveDeltaTime; // [px/ms]
        float velocityY = (endPosition.y - startPosition.y) / effectiveDeltaTime; // [px/ms]

        return new PointF(velocityX, velocityY);
    }

    private PointF getScaleOfTransformation(Transformation transformation) {
        final float[] matrix = new float[9];
        transformation.getMatrix().getValues(matrix);

        // Calculate scale magnitudes, robust to rotation/skew.
        float scaleX = (float) Math.hypot(matrix[Matrix.MSCALE_X],
                matrix[Matrix.MSKEW_X]);
        float scaleY = (float) Math.hypot(matrix[Matrix.MSKEW_Y],
                matrix[Matrix.MSCALE_Y]);

        return new PointF(scaleX, scaleY);
    }

    private PointF getPositionOfTransformation(Transformation transformation) {
        final float[] matrix = new float[9];
        transformation.getMatrix().getValues(matrix);
        return new PointF(matrix[Matrix.MTRANS_X], matrix[Matrix.MTRANS_Y]);
    }
}
