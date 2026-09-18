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

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import com.android.internal.policy.ScreenDecorationsUtils;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.animation.Interpolators;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Handles the animation of a task moving across physical displays.
 *  Throughout this implementation:
 *  - "SRC" refers to the display the task is moving FROM.
 *  - "DST" refers to the display the task is moving TO.
 */
class DisplayMoveAnimation {
    private static final int FADE_OUT_DURATION = 100;
    private static final int FADE_IN_DURATION = 150;
    private static final int TRANSLATION_DURATION = 400;
    private static final float TRANSLATION_DISTANCE_DP = 50f;
    /** Delay factor which influences the time overlap between the SRC and DST animations. */
    private static final float DELAY_FACTOR = 0.8333f;

    private final TransactionPool mTransactionPool;
    private final DisplayController mDisplayController;

    DisplayMoveAnimation(TransactionPool transactionPool,
            DisplayController displayController) {
        mTransactionPool = transactionPool;
        mDisplayController = displayController;
    }

    /**
     * Starts the display move animation.
     * <pre>
     * Layers on source(SRC) and destination(DST) displays:
     *  SRC: Snapshot (S): fade out
     *  DST: Leash (L): fade in + translate
     *
     * Timeline:
     *  S=snapshot
     *  L=leash
     *  |-----------------------total 483ms-----------------------|
     *  |>-100ms S anim--|
     *  |----83ms----|>--150ms L fade in---|
     *  |----83ms----|>-----------------400ms L translate---------|
     * </pre>
     */
    Optional<WindowAnimation> startAnimation(
            @NonNull SurfaceControl.Transaction startT,
            @NonNull TransitionInfo.Change change,
            @NonNull TransitionInfo info,
            @NonNull Consumer<WindowAnimation> finishCallback,
            @NonNull ShellExecutor mainExecutor) {
        final SurfaceControl snapshotLeash = change.getSnapshot();

        TransitionInfo.Root startRoot = null;
        if (snapshotLeash != null) {
            int rootIndex = info.findRootIndex(change.getStartDisplayId());
            if (rootIndex != -1) {
                startRoot = info.getRoot(rootIndex);
            }
        }
        final boolean hasSrcAnimation = startRoot != null;

        final TransitionInfo.Root endRoot = TransitionUtil.getRootFor(change, info);
        final Point endPos = new Point(
                change.getEndAbsBounds().left - endRoot.getOffset().x,
                change.getEndAbsBounds().top - endRoot.getOffset().y);
        startT.reparent(change.getLeash(), endRoot.getLeash());
        final int dstAnimationDelay;

        if (hasSrcAnimation) {
            dstAnimationDelay = (int) (FADE_OUT_DURATION * DELAY_FACTOR);
            final Point startPos = new Point(
                    change.getStartAbsBounds().left - startRoot.getOffset().x,
                    change.getStartAbsBounds().top - startRoot.getOffset().y);
            startT.reparent(snapshotLeash, startRoot.getLeash());
            startT.setPosition(snapshotLeash, startPos.x, startPos.y);
            startT.show(snapshotLeash);
        } else {
            dstAnimationDelay = 0;
        }

        final PointF dstTranslationVector = calculateTranslationVector(change);
        // Set up position and start visibility on the DST display.
        startT.setPosition(change.getLeash(), endPos.x - dstTranslationVector.x,
                endPos.y - dstTranslationVector.y);
        startT.setAlpha(change.getLeash(), 0f);

        // Round corners if it's fullscreen or not in freeform mode.
        final float cornerRadius = isCornerRadiusApplicable(change) ? getCornerRadius(change) : 0;
        if (cornerRadius > 0) {
            startT.setCornerRadius(change.getLeash(), cornerRadius);
        }

        final ValueAnimator moveAnimation = ValueAnimator.ofFloat(0f, 1f);
        moveAnimation.setDuration(dstAnimationDelay + TRANSLATION_DURATION);
        moveAnimation.addUpdateListener(animation -> {
            final long playTime = animation.getCurrentPlayTime();
            final SurfaceControl.Transaction t = mTransactionPool.acquire();
            if (hasSrcAnimation) {
                applyFadeOut(t, snapshotLeash, playTime);
            }
            applyFadeIn(t, change.getLeash(), playTime, dstAnimationDelay);
            applyTranslation(t, change.getLeash(), endPos, dstTranslationVector, playTime,
                    dstAnimationDelay);

            t.apply();
            mTransactionPool.release(t);
        });

        Consumer<WindowAnimation> onFinish = (animation) -> {
            final SurfaceControl.Transaction t = mTransactionPool.acquire();
            if (hasSrcAnimation) {
                t.remove(snapshotLeash);
            }
            t.setAlpha(change.getLeash(), 1f);
            t.setPosition(change.getLeash(), endPos.x, endPos.y);
            t.apply();
            mTransactionPool.release(t);
            mainExecutor.execute(() -> finishCallback.accept(animation));
        };

        final WindowAnimation winAnim = new WindowAnimation(change, cornerRadius, null,
                moveAnimation);
        winAnim.addFinishCallback(onFinish, mainExecutor);
        return Optional.of(winAnim);
    }

    private void applyFadeOut(SurfaceControl.Transaction t, SurfaceControl snapshotLeash,
            long playTime) {
        float fraction = Math.clamp((float) playTime / FADE_OUT_DURATION, 0f, 1f);
        t.setAlpha(snapshotLeash, 1f - fraction);
    }

    private void applyFadeIn(SurfaceControl.Transaction t, SurfaceControl leash,
            long playTime, int delay) {
        float fraction = Math.clamp((float) (playTime - delay) / FADE_IN_DURATION, 0f, 1f);
        t.setAlpha(leash, fraction);
    }

    private void applyTranslation(SurfaceControl.Transaction t, SurfaceControl leash,
            Point endPos, PointF translationVector, long playTime, int delay) {
        float fraction = Math.clamp((float) (playTime - delay) / TRANSLATION_DURATION, 0f, 1f);
        float interpolated = Interpolators.EMPHASIZED_DECELERATE.getInterpolation(fraction);
        float posFraction = 1f - interpolated;
        t.setPosition(leash, endPos.x - translationVector.x * posFraction,
                endPos.y - translationVector.y * posFraction);
    }

    private PointF calculateTranslationVector(TransitionInfo.Change change) {
        // Calculate the relative direction vector in DP
        final PointF translationDirectionDp = mDisplayController.getRelativeTranslationDp(
                change.getStartDisplayId(), change.getEndDisplayId(), change.getStartAbsBounds(),
                change.getEndAbsBounds(), TRANSLATION_DISTANCE_DP);
        // Account for different display resolutions by scaling the vector per display to ensure
        // the movement is consistent across displays
        return mDisplayController.convertDpVectorToPxVector(
                change.getEndDisplayId(), translationDirectionDp);
    }

    private boolean isCornerRadiusApplicable(TransitionInfo.Change change) {
        final int windowingMode = change.getTaskInfo() != null
                ? change.getTaskInfo().getWindowingMode() : WINDOWING_MODE_UNDEFINED;
        return windowingMode != WINDOWING_MODE_FREEFORM;
    }

    private float getCornerRadius(TransitionInfo.Change change) {
        final Context destinationDisplayContext = mDisplayController.getDisplayContext(
                change.getEndDisplayId());
        return destinationDisplayContext == null ? 0
                : ScreenDecorationsUtils.getWindowCornerRadius(destinationDisplayContext);
    }
}
