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

package com.android.wm.shell.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.Choreographer;
import android.view.SurfaceControl;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.window.TransitionInfo;

import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.TransactionPool;

import java.util.Objects;
import java.util.function.Consumer;

public class DefaultSurfaceAnimator implements Runnable {

    private static final ThreadLocal<DefaultSurfaceAnimator> sSurfaceAnimator =
            ThreadLocal.withInitial(DefaultSurfaceAnimator::new);

    private final Choreographer mChoreographer = Choreographer.getInstance();
    private SurfaceControl.Transaction mTransaction;
    private boolean mScheduled;
    private long mLastAppliedVsyncId;
    private int mNumRunningAnimations;

    private DefaultSurfaceAnimator() {
    }

    void schedule() {
        if (!mScheduled) {
            mChoreographer.postCallback(Choreographer.CALLBACK_TRAVERSAL, this, null /* token */);
            mScheduled = true;
        }
    }

    @Override
    public void run() {
        mScheduled = false;
        mLastAppliedVsyncId = mChoreographer.getVsyncId();
        mTransaction.setFrameTimelineVsync(mLastAppliedVsyncId);
        mTransaction.apply();
    }

    private static void onAnimationStart(@NonNull AnimationAdapter animation) {
        final DefaultSurfaceAnimator animator = sSurfaceAnimator.get();
        animation.mSurfaceAnimator = animator;
        if (animator.mTransaction != null) {
            animation.mTransaction = animator.mTransaction;
        } else {
            animation.mTransaction = animator.mTransaction = animation.mTransactionPool.acquire();
        }
        animator.mNumRunningAnimations++;
    }

    private static void onAnimationEnd(@NonNull AnimationAdapter animation) {
        final DefaultSurfaceAnimator animator = animation.mSurfaceAnimator;
        animator.mNumRunningAnimations--;
        if (animator.mScheduled || animator.mNumRunningAnimations > 0) {
            // In case Animation#end is called between the applied frame and the next frame, the
            // transaction of end animation should be applied before the finish transaction of
            // transition. Otherwise, the real end state of transition may be overwritten.
            if (animator.mLastAppliedVsyncId == animator.mChoreographer.getVsyncId()) {
                animator.mTransaction.apply();
            } else if (animator.mScheduled) {
                // While Animator#isPostNotifyEndListenerEnabled is true, this usually happens when
                // the first frame is the end (i.e. animation duration is 0). Apply the transaction
                // before notifying the end callback to ensure the order with finish transaction.
                animator.mTransaction.apply();
                if (animator.mNumRunningAnimations == 0) {
                    animator.mScheduled = false;
                    animator.mChoreographer.removeCallbacks(Choreographer.CALLBACK_TRAVERSAL,
                            animator, null /* token */);
                }
            }
            return;
        }
        animation.mTransactionPool.release(animator.mTransaction);
        animator.mTransaction = null;
    }

    /**
     * Builds an animator for the surface without creating a WindowAnimation and with no finish
     * callback function.
     */
    static ValueAnimator createAnimator(@NonNull Animation anim, @NonNull SurfaceControl leash,
            @NonNull TransactionPool pool, @Nullable Point position, float cornerRadius,
            @Nullable Rect clipRect) {
        final DefaultAnimationAdapter adapter = new DefaultAnimationAdapter(anim, leash,
                position, clipRect, cornerRadius, null /* roundedBounds */);
        return buildSurfaceAnimation(anim, null /* finishRunnable */, pool, null /* mainExecutor */,
                adapter);
    }

    /** Builds an animator for the surface and attaches a WindowAnimation to the adapter. */
    static WindowAnimation buildWindowAnimation(@NonNull Animation anim,
            @NonNull TransitionInfo.Change change,
            @NonNull SurfaceControl leash,
            Consumer<WindowAnimation> finishCallback, @NonNull TransactionPool pool,
            ShellExecutor mainExecutor, @Nullable Point position, float cornerRadius,
            @Nullable Rect clipRect,
            @Nullable TransitionAnimationHelper.RoundedContentPerDisplay roundedBounds) {
        final DefaultAnimationAdapter adapter = new DefaultAnimationAdapter(anim, leash,
                position, clipRect, cornerRadius, roundedBounds);
        return buildWindowAnimation(anim, change, adapter, finishCallback, pool, mainExecutor,
                cornerRadius);
    }

    /** Builds an animator for the surface and attaches a WindowAnimation to the adapter. */
    static WindowAnimation buildWindowAnimation(@NonNull Animation anim,
            @NonNull TransitionInfo.Change change,
            @NonNull AnimationAdapter adapter,
            Consumer<WindowAnimation> finishCallback, @NonNull TransactionPool pool,
            ShellExecutor mainExecutor,
            float cornerRadius) {
        ValueAnimator va = buildSurfaceAnimation(anim, null /* finishRunnable */,
                pool, null /* mainExecutor */, adapter);
        WindowAnimation windowAnimation = new WindowAnimation(change, cornerRadius, anim, va);
        if (finishCallback != null && mainExecutor != null) {
            windowAnimation.addFinishCallback(finishCallback, mainExecutor);
        }
        return windowAnimation;
    }

    /** Builds an animator for the surface. */
    static ValueAnimator buildSurfaceAnimation(
            @NonNull Animation anim, Runnable finishRunnable,
            @NonNull TransactionPool pool, ShellExecutor mainExecutor,
            @NonNull AnimationAdapter updateListener) {
        updateListener.mTransactionPool = pool;
        final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        // Animation length is already expected to be scaled.
        va.overrideDurationScale(1.0f);
        va.setDuration(anim.computeDurationHint());
        setupValueAnimator(va, updateListener, (vanim) -> {
            DefaultSurfaceAnimator.onAnimationEnd(updateListener);
            if (mainExecutor != null && finishRunnable != null) {
                mainExecutor.execute(finishRunnable);
            }
        });
        return va;
    }

    /** The animation adapter for buildSurfaceAnimation. */
    abstract static class AnimationAdapter implements ValueAnimator.AnimatorUpdateListener {
        @NonNull  final Transformation mTransformation = new Transformation();
        @NonNull final SurfaceControl mLeash;
        @NonNull SurfaceControl.Transaction mTransaction;
        private TransactionPool mTransactionPool;
        private DefaultSurfaceAnimator mSurfaceAnimator;

        AnimationAdapter(@NonNull SurfaceControl leash) {
            mLeash = Objects.requireNonNull(leash, "leash is null in AnimationAdapter constructor");
        }

        @Override
        public void onAnimationUpdate(@NonNull ValueAnimator animator) {
            // The finish callback in buildSurfaceAnimation will ensure that the animation ends
            // with fraction 1.
            final long currentPlayTime = animator.getAnimatedFraction() >= 1f
                    ? animator.getDuration()
                    : Math.min(animator.getDuration(), animator.getCurrentPlayTime());
            applyTransformation(animator, currentPlayTime);
            mSurfaceAnimator.schedule();
        }

        abstract void applyTransformation(@NonNull ValueAnimator animator, long currentPlayTime);
    }

    private static class DefaultAnimationAdapter extends AnimationAdapter {
        final float[] mMatrix = new float[9];
        @NonNull final Animation mAnim;
        @Nullable final Point mPosition;
        @Nullable final Rect mClipRect;
        @Nullable private final Rect mAnimClipRect;
        final float mCornerRadius;
        final int mWindowBottom;

        /**
         * Inset changes aren't synchronized with transitions, so use a "provider" to track the
         * bottom of the display content during the animation.
         */
        @Nullable
        final TransitionAnimationHelper.RoundedContentPerDisplay mRoundedContentBounds;

        DefaultAnimationAdapter(@NonNull Animation anim, @NonNull SurfaceControl leash,
                @Nullable Point position, @Nullable Rect clipRect, float cornerRadius,
                TransitionAnimationHelper.RoundedContentPerDisplay roundedBounds) {
            super(leash);
            mAnim = anim;
            mPosition = (position != null && (position.x != 0 || position.y != 0))
                    ? position : null;
            mClipRect = (clipRect != null && !clipRect.isEmpty()) ? clipRect : null;
            mAnimClipRect = mClipRect != null ? new Rect() : null;
            mCornerRadius = cornerRadius;
            mWindowBottom = clipRect != null ? clipRect.bottom : 0;
            mRoundedContentBounds = roundedBounds;
        }

        @Override
        void applyTransformation(@NonNull ValueAnimator animator, long currentPlayTime) {
            final Transformation transformation = mTransformation;
            final SurfaceControl.Transaction t = mTransaction;
            final SurfaceControl leash = mLeash;
            transformation.clear();
            mAnim.getTransformation(currentPlayTime, transformation);
            if (mPosition != null) {
                transformation.getMatrix().postTranslate(mPosition.x, mPosition.y);
            }
            t.setMatrix(leash, transformation.getMatrix(), mMatrix);
            t.setAlpha(leash, transformation.getAlpha());

            if (mClipRect != null) {
                boolean needCrop = false;
                if (mRoundedContentBounds != null) {
                    mClipRect.bottom = Math.min(mRoundedContentBounds.mBounds.bottom,
                            mWindowBottom);
                }

                mAnimClipRect.set(mClipRect);
                if (transformation.hasClipRect()) {
                    mAnimClipRect.intersectUnchecked(transformation.getClipRect());
                    needCrop = true;
                }
                final Insets extensionInsets = Insets.min(transformation.getInsets(), Insets.NONE);
                if (!extensionInsets.equals(Insets.NONE)) {
                    // Clip out any overflowing edge extension.
                    mAnimClipRect.inset(extensionInsets);
                    needCrop = true;
                }
                if (mCornerRadius > 0 && mAnim.hasRoundedCorners()) {
                    // Rounded corner can only be applied if a crop is set.
                    t.setCornerRadius(leash, mCornerRadius);
                    needCrop = true;
                }
                if (needCrop) {
                    t.setWindowCrop(leash, mAnimClipRect);
                }
            }
        }
    }

    /**
     * Setup some callback logic on a value-animator. This helper ensures that a value animator
     * finishes at its final fraction (1f) and that relevant callbacks are only called once.
     */
    public static ValueAnimator setupValueAnimator(ValueAnimator animator,
            ValueAnimator.AnimatorUpdateListener updateListener,
            Consumer<ValueAnimator> afterFinish) {
        animator.addUpdateListener(updateListener);
        animator.addListener(new AnimatorListenerAdapter() {
            // It is possible for the end/cancel to be called more than once, which may cause
            // issues if the animating surface has already been released. Track the finished
            // state here to skip duplicate callbacks. See b/252872225.
            private boolean mFinished;

            @Override
            public void onAnimationStart(Animator animation) {
                if (updateListener instanceof AnimationAdapter animationAdapter) {
                    DefaultSurfaceAnimator.onAnimationStart(animationAdapter);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                onFinish();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onFinish();
            }

            private void onFinish() {
                if (mFinished) return;
                mFinished = true;
                // Apply transformation of end state in case the animation is canceled.
                if (animator.getAnimatedFraction() < 1f) {
                    animator.setCurrentFraction(1f);
                }
                afterFinish.accept(animator);
                // The update listener can continue to be called after the animation has ended if
                // end() is called manually again before the finisher removes the animation.
                // Remove it manually here to prevent animating a released surface.
                // See b/252872225.
                animator.removeUpdateListener(updateListener);
            }
        });
        return animator;
    }
}
