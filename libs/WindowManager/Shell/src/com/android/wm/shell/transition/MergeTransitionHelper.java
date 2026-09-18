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

import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_FLAG_APP_CRASHED;
import static android.view.WindowManager.TRANSIT_FLAG_AVOID_MOVE_TO_FRONT;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_OCCLUDING;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_UNOCCLUDING;
import static android.view.WindowManager.TRANSIT_FLAG_OPEN_BEHIND;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.isIndependent;

import android.graphics.Rect;
import android.graphics.RectF;
import android.util.ArrayMap;
import android.view.SurfaceControl;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerToken;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.TransitionUtil;

import java.util.List;

/**
 * Helper class that provides methods for processing/handling merging transitions
 **/
public class MergeTransitionHelper {

    /**
     * Get possible merge opportunities based on the container token. If we find a container that is
     * used by both transitions, then we could see if we can create an animation that would bridge
     * the current state from the already playing transition to the next desired end state from the
     * incoming transition.
     *
     * @param runningTransitionAnimations list of currently running {@link  WindowAnimation}
     * @param incomingTransitionInfo      incoming {@link TransitionInfo} from the merge request
     * @return Map containing of currently playing {@link  WindowAnimation} and incoming
     * transition info change
     */
    static ArrayMap<WindowAnimation, TransitionInfo.Change> getMergeConflicts(
            List<WindowAnimation> runningTransitionAnimations,
            TransitionInfo incomingTransitionInfo) {
        List<TransitionInfo.Change> changes = incomingTransitionInfo.getChanges();
        var mergingWindowAnimations = new ArrayMap<WindowAnimation, TransitionInfo.Change>();
        for (int i = 0; i < runningTransitionAnimations.size(); ++i) {
            WindowAnimation windowAnimation = runningTransitionAnimations.get(i);
            WindowContainerToken animatingWindowToken =
                    windowAnimation.mChange.getContainer();
            if (animatingWindowToken == null) {
                continue;
            }
            for (int k = 0; k < changes.size(); ++k) {
                var incomingChange = changes.get(k);
                WindowContainerToken incomingChangeToken = incomingChange.getContainer();
                if (incomingChangeToken == null) {
                    continue;
                }

                if (!isIndependent(incomingChange, incomingTransitionInfo)) {
                    continue;
                }
                if (incomingChangeToken.equals(animatingWindowToken)) {
                    mergingWindowAnimations.put(windowAnimation, incomingChange);
                }
            }
        }
        return mergingWindowAnimations;
    }

    private static final int KNOWN_MERGEABLE_FLAGS = TRANSIT_FLAG_APP_CRASHED
            | TRANSIT_FLAG_OPEN_BEHIND
            | TRANSIT_FLAG_KEYGUARD_OCCLUDING
            | TRANSIT_FLAG_KEYGUARD_UNOCCLUDING
            | TRANSIT_FLAG_AVOID_MOVE_TO_FRONT;

    /** Allowlist of supported transition requests for which we support merge animations **/
    static boolean canCreateMergeAnimation(TransitionInfo info) {
        if ((info.getFlags() & ~KNOWN_MERGEABLE_FLAGS) != 0) {
            return false;
        }
        int type = info.getType();
        return type == TRANSIT_CLOSE
                || type == TRANSIT_TO_BACK
                || type == TRANSIT_OPEN
                || type == TRANSIT_TO_FRONT;
    }

    private static final int BUCKET_LAYER_SIZE = 100;
    private static final int BUCKET_WALLPAPER = 0;
    private static final int BUCKET_CLOSING = BUCKET_LAYER_SIZE;
    private static final int BUCKET_CHANGING = BUCKET_LAYER_SIZE * 2;
    private static final int BUCKET_OPENING = BUCKET_LAYER_SIZE * 3;
    private static final int BUCKET_OVERLAY = BUCKET_LAYER_SIZE * 4;

    private static int getZBucket(TransitionInfo.Change change) {
        if (change.hasFlags(TransitionInfo.FLAG_IS_WALLPAPER)) return BUCKET_WALLPAPER;
        if (change.hasFlags(TransitionInfo.FLAG_ALWAYS_ON_TOP)) return BUCKET_OVERLAY;
        return switch (change.getMode()) {
            case TRANSIT_OPEN, TRANSIT_TO_FRONT -> BUCKET_OPENING;
            case TRANSIT_CLOSE, TRANSIT_TO_BACK -> BUCKET_CLOSING;
            default -> BUCKET_CHANGING;
        };
    }

    /** Arrange layers for merge animation **/
    static void arrangeStartTransactionLayers(
            TransitionInfo incomingInfo,
            List<TransitionInfo.Change> conflictingChanges,
            SurfaceControl.Transaction startTransaction) {

        // When transition is incoming, right now the new transition roots are empty/invisible.
        // We reparent and make visible all changes from the incoming transition, including the
        // already animating from the playing transition as we created new animations from them

        // Show transition roots
        for (int i = 0; i < incomingInfo.getRootCount(); ++i) {
            startTransaction.show(incomingInfo.getRoot(i).getLeash());
        }

        // Reparent changes to new roots and make them visible
        for (int i = 0; i < incomingInfo.getChanges().size(); ++i) {
            var change = incomingInfo.getChanges().get(i);
            int layer = getZBucket(change) + i;

            // If the change is of type TRANSIT_CLOSE/TRANSTI_TO_BACK we can leave the change in
            // the already playing transition root
            if (conflictingChanges.contains(change)
                    && TransitionUtil.isClosingType(change.getMode())) {
                continue;
            }
            startTransaction.reparent(change.getLeash(),
                    TransitionUtil.getRootFor(change, incomingInfo).getLeash());
            startTransaction.setLayer(change.getLeash(), layer);
            startTransaction.show(change.getLeash());
        }
    }

    /**
     * An animation that morphs a window from a starting state to a target rect.
     *
     * <p>This class animates the bounds of a window by interpolating the position (left, top)
     * and dimensions (width, height) from a {@link WindowAnimationState} to a final {@link Rect}.
     *
     * <p>The transformation is applied by:
     * <ol>
     *   <li>Calculating the interpolated bounds based on the animation progress.</li>
     *   <li>Scaling the target (end) bounds to match the dimensions of the interpolated bounds
     *   .</li>
     *   <li>Translating the result to the position of the interpolated bounds.</li>
     * </ol>
     *
     * <p>The alpha value is explicitly set to 1.0f throughout the animation.
     */
    static class StateToRectAnimation extends Animation {
        private final RectF mStartRect;
        private final Rect mEndRect;

        StateToRectAnimation(WindowAnimationState start, Rect end, int durationMs,
                Interpolator interpolator) {
            mStartRect = start.bounds;
            mEndRect = end;
            setDuration(durationMs);
            setInterpolator(interpolator);
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            float left = mStartRect.left + (mEndRect.left - mStartRect.left) * interpolatedTime;
            float top = mStartRect.top + (mEndRect.top - mStartRect.top) * interpolatedTime;
            float width =
                    mStartRect.width() + (mEndRect.width() - mStartRect.width()) * interpolatedTime;
            float height = mStartRect.height()
                    + (mEndRect.height() - mStartRect.height()) * interpolatedTime;

            float scaleX = width / mEndRect.width();
            float scaleY = height / mEndRect.height();

            t.getMatrix().setScale(scaleX, scaleY);
            t.getMatrix().postTranslate(left, top);
            t.setAlpha(1f);

            if (interpolatedTime == 0 || interpolatedTime == 1.0f) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        "StateToRectAnimation: t=%f left=%f top=%f w=%f h=%f scale=%f,%f",
                        interpolatedTime, left, top, width, height, scaleX, scaleY);
            }
        }
    }
}
