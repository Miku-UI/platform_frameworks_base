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
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.FLAG_FILLS_TASK;
import static android.window.TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY;
import static android.window.TransitionInfo.FLAG_TRANSLUCENT;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.view.animation.Animation;
import android.window.TransitionInfo;

import com.android.internal.R;
import com.android.internal.policy.ScreenDecorationsUtils;
import com.android.internal.policy.TransitionAnimation;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.TransitionUtil;

/**
 * An isolated planner that specifically handles simple Activity Open and Close animations
 * within the Mixpatcher framework, avoiding the legacy DefaultTransitionHandler logic entirely.
 */
public class ActivityPlanner implements ITransitionPlanner {

    private final TransactionPool mTransactionPool;
    private final DisplayController mDisplayController;
    private final ShellExecutor mMainExecutor;
    private final ShellExecutor mAnimExecutor;
    private final TransitionAnimation mTransitionAnimation;
    private final CounterRotatorHelper mRotator;
    private final TransitionAnimationHelper.RoundedContentTracker mRoundedContentBounds;

    public ActivityPlanner(Context context, TransactionPool pool,
            DisplayController displayController, DisplayInsetsController displayInsetsController,
            ShellExecutor mainExecutor, ShellExecutor animExecutor) {
        mTransactionPool = pool;
        mDisplayController = displayController;
        mMainExecutor = mainExecutor;
        mAnimExecutor = animExecutor;
        mTransitionAnimation = new TransitionAnimation(context, false, "ActivityPlanner");
        mRotator = new CounterRotatorHelper();
        mRoundedContentBounds = new TransitionAnimationHelper.RoundedContentTracker(
                displayController, displayInsetsController);
        mRoundedContentBounds.init();
    }

    @Override
    public void plan(@NonNull AnimationPlan plan, @NonNull TransitionInfo fullInfo,
            @NonNull IBinder transition, @NonNull TransitionInfo plannableInfo,
            @NonNull SurfaceControl.Transaction startTransaction) {
        TransitionWindowAnimation transitionWindowAnimation = null;
        for (int i = plannableInfo.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = plannableInfo.getChanges().get(i);
            if (!canPlanAnimationFor(change, plannableInfo)) {
                continue;
            }
            final int type = TransitionAnimationHelper.getTransitionTypeFromInfo(plannableInfo);
            Animation a = loadSimpleActivityAnimation(type, change);

            if (a == null) {
                plan.setAnimation(change.getContainer(), new NoAnimation(mMainExecutor));
                continue;
            }

            var isClosingType = TransitionUtil.isClosingType(change.getMode());
            int rootIdx = TransitionUtil.rootIndexFor(change, plannableInfo);

            final int displayId = plannableInfo.getRoot(rootIdx).getDisplayId();
            final Context displayContext = mDisplayController.getDisplayContext(displayId);

            if (displayContext != null
                    && displayContext.getResources().getConfiguration().isScreenRound()) {
                a.setHasRoundedCorners(true);
            }

            final float cornerRadius = a.hasRoundedCorners() && displayContext != null
                    ? ScreenDecorationsUtils.getWindowCornerRadius(displayContext)
                    : 0f;

            final Rect clipRect = isClosingType
                    ? new Rect(mRotator.getEndBoundsInStartRotation(change))
                    : new Rect(change.getEndAbsBounds());
            clipRect.offsetTo(0, 0);

            final TransitionInfo.Root animRoot = plannableInfo.getRoot(rootIdx);
            final Rect boundsForOffset =
                    com.android.window.flags.Flags.refineAncestorSearchAndBounds()
                            && isClosingType
                            ? change.getStartAbsBounds() : change.getEndAbsBounds();

            final Point animRelOffset = new Point(
                    boundsForOffset.left - animRoot.getOffset().x,
                    boundsForOffset.top - animRoot.getOffset().y);

            animRelOffset.x = Math.max(animRelOffset.x, change.getEndRelOffset().x);
            animRelOffset.y = Math.max(animRelOffset.y, change.getEndRelOffset().y);

            if (!a.isInitialized()) {
                final Rect animationRange = isClosingType
                        ? change.getStartAbsBounds() : change.getEndAbsBounds();
                a.initialize(animationRange.width(), animationRange.height(),
                        change.getEndAbsBounds().width(), change.getEndAbsBounds().height());
            }
            a.restrictDuration(TransitionAnimation.MAX_ANIMATION_DURATION);

            WindowAnimation winAnim = DefaultSurfaceAnimator.buildWindowAnimation(
                    a, change, change.getLeash(),
                    (anim) -> { /* Hook for explicit finish, managed natively by Mixpatcher
                        start() override */
                    },
                    mTransactionPool, mMainExecutor,
                    animRelOffset, cornerRadius, clipRect,
                    mRoundedContentBounds.forDisplay(change.getEndDisplayId()));

            startTransaction.setPosition(change.getLeash(), animRelOffset.x, animRelOffset.y);
            if (a.getExtensionEdges() != 0x0
                    && (change.hasFlags(FLAG_FILLS_TASK | FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY))) {
                startTransaction.setEdgeExtensionEffect(change.getLeash(),
                        a.getExtensionEdges());
            }

            if (transitionWindowAnimation == null) {
                transitionWindowAnimation = new TransitionWindowAnimation(mMainExecutor,
                        mAnimExecutor);
            }
            transitionWindowAnimation.addWindowAnimation(winAnim);
            plan.setAnimation(change.getContainer(), transitionWindowAnimation);
        }
    }


    /**
     * Simplified version of loadAnimation fom DefaultTransitionHandler
     */
    private Animation loadSimpleActivityAnimation(int fullType, TransitionInfo.Change change) {
        boolean enter = TransitionUtil.isOpeningType(change.getMode());
        boolean translucent = (change.getFlags() & FLAG_TRANSLUCENT) != 0;
        int animAttr = 0;

        if (fullType == TRANSIT_OPEN || fullType == TRANSIT_TO_FRONT) {
            animAttr = enter ? R.styleable.WindowAnimation_activityOpenEnterAnimation
                    : R.styleable.WindowAnimation_activityOpenExitAnimation;
        } else if (fullType == TRANSIT_CLOSE || fullType == TRANSIT_TO_BACK) {
            animAttr = enter ? R.styleable.WindowAnimation_activityCloseEnterAnimation
                    : R.styleable.WindowAnimation_activityCloseExitAnimation;
        }

        if (animAttr != 0) {
            return mTransitionAnimation.loadDefaultAnimationAttr(animAttr, translucent);
        }
        return null;
    }

    private boolean canPlanAnimationFor(TransitionInfo.Change change,
            TransitionInfo plannableInfo) {
        // Filter out anything that isn't a simple Activity Open/Close/ToFront/ToBack for now
        if (!TransitionUtil.isOpenOrCloseMode(change.getMode())) return false;
        if (change.getActivityComponent() == null) return false;
        if (change.getAnimationOptions() != null) {
            return false; // Custom animations handled elsewhere
        }
        if (!TransitionInfo.isIndependent(change, plannableInfo)) return false;
        return true;
    }

    @NonNull
    @Override
    public String getDebugName() {
        return "ActivityPlanner";
    }
}
