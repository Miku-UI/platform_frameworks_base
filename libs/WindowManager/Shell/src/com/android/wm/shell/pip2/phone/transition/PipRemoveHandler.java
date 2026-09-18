/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.pip2.phone.transition;

import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_TO_BACK;

import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getChangeByToken;
import static com.android.wm.shell.transition.Transitions.TRANSIT_REMOVE_PIP;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipAlphaAnimator;
import com.android.wm.shell.pip2.phone.PipInteractionHandler;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.transition.Transitions;

public class PipRemoveHandler implements Transitions.TransitionHandler,
        PipDisplayLayoutState.DisplayIdListener {
    private static final String TAG = PipRemoveHandler.class.getSimpleName();

    private Context mContext;
    private final PipBoundsState mPipBoundsState;
    private final PipTransitionState mPipTransitionState;
    @NonNull
    private final PipSurfaceTransactionHelper mSurfaceTransactionHelper;
    @NonNull
    private final PipInteractionHandler mPipInteractionHandler;

    //
    // Local state and caches
    private boolean mPendingRemoveWithFadeout;
    private PipAlphaAnimatorSupplier mPipAlphaAnimatorSupplier;

    @Nullable
    private IBinder mRemoveTransition;

    public PipRemoveHandler(Context context,
            @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            PipBoundsState pipBoundsState,
            PipTransitionState pipTransitionState,
            PipInteractionHandler pipInteractionHandler) {
        mContext = context;
        mSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mPipBoundsState = pipBoundsState;
        mPipTransitionState = pipTransitionState;
        mPipAlphaAnimatorSupplier = PipAlphaAnimator::new;
        mPipInteractionHandler = pipInteractionHandler;
    }

    /** Called by [PipTransition#onDisplayIdChanged] when the display id changes. */
    @Override
    public void onDisplayIdChanged(Context context) {
        mContext = context;
    }

    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        // If a task is about ot close we expect it to be the trigger task in the request.
        final ActivityManager.RunningTaskInfo triggerTask = request.getTriggerTask();
        if (triggerTask == null) return null;

        if (TransitionUtil.isClosingType(request.getType())
                && triggerTask.getToken().equals(mPipTransitionState.getPipTaskToken())) {
            // Do some bookkeeping, but return an empty WCT; we do not need any extra WM changes,
            // but we do want to have PipTransition cached as the firstHandler in case of an abort.
            mRemoveTransition = transition;
            return new WindowContainerTransaction();
        }
        return null;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (transition == mRemoveTransition || isRemovePipTransition(info)) {
            mRemoveTransition = null;
            mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
            return startRemoveAnimation(info, startTransaction, finishTransaction,
                    () -> finishCallback.onTransitionFinished(null /* finishWCT */));
        }
        return false;
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @androidx.annotation.Nullable SurfaceControl.Transaction finishT) {
        mPipTransitionState.setState(isRemoveTransition(transition)
                ? PipTransitionState.EXITED_PIP : PipTransitionState.UNDEFINED);
        mRemoveTransition = null;
        mPendingRemoveWithFadeout = false;
    }

    /**
     * @return true if given transition token is the same as an ongoing removeTransition's
     */
    public boolean isRemoveTransition(@NonNull IBinder transition) {
        return mRemoveTransition == transition;
    }

    /**
     * @param pendingRemoveWithFadeout whether to run next remove transition with fadeout animation.
     */
    public void setPendingRemoveWithFadeout(boolean pendingRemoveWithFadeout) {
        mPendingRemoveWithFadeout = pendingRemoveWithFadeout;
    }

    private boolean startRemoveAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Runnable finishCallback) {
        TransitionInfo.Change pipChange = getChangeByToken(info,
                mPipTransitionState.getPipTaskToken());
        if (pipChange == null) return false;

        if (isPipClosing(info)) {
            // If PiP is removed via a close (e.g. finishing of the activity), then
            // clear out the PiP cache related to that activity component (e.g. reentry state).
            mPipBoundsState.setLastPipComponentName(null /* lastPipComponentName */);
        }

        mPipInteractionHandler.begin(pipChange.getLeash(),
                PipInteractionHandler.INTERACTION_REMOVE_PIP);

        final Rect startBounds = pipChange.getStartAbsBounds();
        startTransaction.setWindowCrop(pipChange.getLeash(),
                startBounds.width(), startBounds.height());
        finishTransaction.setAlpha(pipChange.getLeash(), 0f);
        if (mPendingRemoveWithFadeout) {
            mPendingRemoveWithFadeout = false;
            PipAlphaAnimator animator = mPipAlphaAnimatorSupplier.get(
                    mContext, mSurfaceTransactionHelper, pipChange.getLeash(),
                    startTransaction, finishTransaction, PipAlphaAnimator.FADE_OUT);
            animator.setAnimationEndCallback(() -> {
                mPipInteractionHandler.end();
                finishCallback.run();
            });
            animator.start();
        } else {
            // Jumpcut to a faded-out PiP if no fadeout animation was requested.
            startTransaction.setAlpha(pipChange.getLeash(), 0f);
            startTransaction.apply();
            mPipInteractionHandler.end();
            finishCallback.run();
        }
        return true;
    }

    private boolean isRemovePipTransition(@NonNull TransitionInfo info) {
        if (mPipTransitionState.getPipTaskToken() == null) {
            // PiP removal makes sense if enter-PiP has cached a valid pinned task token.
            return false;
        }
        TransitionInfo.Change pipChange = info.getChange(mPipTransitionState.getPipTaskToken());
        if (pipChange == null) {
            // Search for the PiP change by token since the windowing mode might be FULLSCREEN now.
            return false;
        }

        boolean isPipMovedToBack = info.getType() == TRANSIT_TO_BACK
                && pipChange.getMode() == TRANSIT_TO_BACK;
        // If PiP is dismissed by user (i.e. via dismiss button in PiP menu)
        boolean isPipDismissed = info.getType() == TRANSIT_REMOVE_PIP
                && pipChange.getMode() == TRANSIT_TO_BACK;
        // PiP is being removed if the pinned task is either moved to back, closed, or dismissed.
        return isPipMovedToBack || isPipClosing(info) || isPipDismissed;
    }

    private boolean isPipClosing(@NonNull TransitionInfo info) {
        if (mPipTransitionState.getPipTaskToken() == null) {
            // PiP removal makes sense if enter-PiP has cached a valid pinned task token.
            return false;
        }
        TransitionInfo.Change pipChange = info.getChange(mPipTransitionState.getPipTaskToken());
        TransitionInfo.Change pipActivityChange = info.getChanges().stream().filter(change ->
                        change.getTaskInfo() == null && change.getParent() != null
                                && change.getParent() == mPipTransitionState.getPipTaskToken())
                .findFirst().orElse(null);

        boolean isPipTaskClosed = pipChange != null
                && pipChange.getMode() == TRANSIT_CLOSE;
        boolean isPipActivityClosed = pipActivityChange != null
                && pipActivityChange.getMode() == TRANSIT_CLOSE;
        return isPipTaskClosed || isPipActivityClosed;
    }

    @VisibleForTesting
    interface PipAlphaAnimatorSupplier {
        PipAlphaAnimator get(Context context,
                @androidx.annotation.NonNull
                PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
                SurfaceControl leash,
                SurfaceControl.Transaction startTransaction,
                SurfaceControl.Transaction finishTransaction,
                @PipAlphaAnimator.Fade int direction);
    }

    @VisibleForTesting
    void setPipAlphaAnimatorSupplier(@NonNull PipAlphaAnimatorSupplier supplier) {
        mPipAlphaAnimatorSupplier = supplier;
    }
}
