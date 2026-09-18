/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.window.TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY;

import static com.android.wm.shell.shared.TransitionUtil.isOpeningType;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.transition.MixedTransitionHelper.getTopSplitStageToKeep;
import static com.android.wm.shell.transition.Transitions.TRANSIT_SPLIT_DISMISS;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.TaskInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.RemoteTransition;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.activityembedding.ActivityEmbeddingController;
import com.android.wm.shell.bubbles.BubbleHelper;
import com.android.wm.shell.bubbles.transitions.BubbleTransitions;
import com.android.wm.shell.common.ComponentUtils;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.NormalAppLayerHandler;
import com.android.wm.shell.desktopmode.desktoptaskshandlers.DesktopTasksTransitionHandler;
import com.android.wm.shell.keyguard.KeyguardTransitionHandler;
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerHandler;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip2.phone.PipScheduler;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.recents.RecentsTransitionHandler;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.bubbles.BubbleFlagHelper;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.splitscreen.SplitScreen;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.splitscreen.StageCoordinator;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.DefaultMixedHandler.MixedTransition.MixedTransitionType;
import com.android.wm.shell.unfold.UnfoldTransitionHandler;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A handler for dealing with transitions involving multiple other handlers. For example: an
 * activity in split-screen going into PiP. Note this is provided as a handset-specific
 * implementation of {@code MixedTransitionHandler}.
 */
public class DefaultMixedHandler implements MixedTransitionHandler,
        RecentsTransitionHandler.RecentsMixedHandler {

    private final Transitions mPlayer;
    private PipTransitionController mPipHandler;
    private @Nullable PipScheduler mPipScheduler;
    private RecentsTransitionHandler mRecentsHandler;
    private @Nullable StageCoordinator mSplitHandler;
    private final KeyguardTransitionHandler mKeyguardHandler;
    private DesktopTasksController mDesktopTasksController;
    // Depend on DesktopTasksTransitionHandler to make sure that handler executes after
    // DefaultMixedHandler.
    private DesktopTasksTransitionHandler mDesktopTasksTransitionHandler;
    private BubbleTransitions mBubbleTransitions;
    private BubbleHelper mBubbleHelper;
    private UnfoldTransitionHandler mUnfoldHandler;
    private ActivityEmbeddingController mActivityEmbeddingController;
    private @Nullable PinnedLayerHandler mPinnedLayerHandler;
    private @Nullable NormalAppLayerHandler mNormalAppLayerHandler;

    abstract static class MixedTransition {

        // Mixed transition types

        /** Entering Pip from split, breaks split. */
        static final int TYPE_ENTER_PIP_FROM_SPLIT = 1;

        /** Both the display and split-state (enter/exit) is changing */
        static final int TYPE_DISPLAY_AND_SPLIT_CHANGE = 2;

        /**
         * While handling an intent with its own remoteTransition, a PIP enter or Desktop immersive
         * exit change is found.
         */
        static final int TYPE_OPTIONS_REMOTE_AND_PIP_OR_DESKTOP_CHANGE = 3;

        /** Recents transition while split-screen foreground. */
        static final int TYPE_RECENTS_DURING_SPLIT = 4;

        /** Keyguard exit/occlude/unocclude transition. */
        static final int TYPE_KEYGUARD = 5;

        /** Recents transition on top of the lock screen. */
        static final int TYPE_RECENTS_DURING_KEYGUARD = 6;

        /** Recents Transition while in desktop mode. */
        static final int TYPE_RECENTS_DURING_DESKTOP = 7;

        /** Fold/Unfold transition. */
        static final int TYPE_UNFOLD = 8;

        /** Enter pip from one of the Activity Embedding windows. */
        static final int TYPE_ENTER_PIP_FROM_ACTIVITY_EMBEDDING = 9;

        /** Entering Pip from split, but replace the Pip stage instead of breaking split. */
        static final int TYPE_ENTER_PIP_REPLACE_FROM_SPLIT = 10;

        /** The display changes when pip is entering. */
        static final int TYPE_ENTER_PIP_WITH_DISPLAY_CHANGE = 11;

        /** Open transition during a desktop session. */
        static final int TYPE_OPEN_IN_DESKTOP = 12;

        /** Transition of a visible app into a bubble. */
        static final int TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE = 13;

        /** Transition of a visible app in a split pair into a bubble. */
        static final int TYPE_LAUNCH_OR_CONVERT_SPLIT_TASK_TO_BUBBLE = 14;

        /** Transition of a visible app in Pip into a bubble. */
        static final int TYPE_LAUNCH_OR_CONVERT_PIP_TASK_TO_BUBBLE = 15;

        /**
         * Transition of a visible app into a bubble when launched from another bubble or for an
         * existing bubble.
         */
        static final int TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE_FROM_EXISTING_BUBBLE = 16;

        /** Transition of a visible app in desktop mode into a bubble. */
        static final int TYPE_LAUNCH_OR_CONVERT_DESKTOP_TASK_TO_BUBBLE = 17;

        /** Entering Pip when another task is pinned. */
        static final int TYPE_ENTER_PIP_WITH_PINNED_LAYER_DISMISS = 18;

        /**
         * Returns {@code true} if the given type is one of the mixed transition type for app
         * bubble transition.
         */
        static boolean isAppBubbleTypeTransition(@MixedTransitionType int type) {
            return type == TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE
                    || type == TYPE_LAUNCH_OR_CONVERT_SPLIT_TASK_TO_BUBBLE
                    || type == TYPE_LAUNCH_OR_CONVERT_PIP_TASK_TO_BUBBLE
                    || type == TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE_FROM_EXISTING_BUBBLE
                    || type == TYPE_LAUNCH_OR_CONVERT_DESKTOP_TASK_TO_BUBBLE;
        }

        @IntDef(prefix = {"TYPE_"}, value = {
                TYPE_ENTER_PIP_FROM_SPLIT,
                TYPE_DISPLAY_AND_SPLIT_CHANGE,
                TYPE_OPTIONS_REMOTE_AND_PIP_OR_DESKTOP_CHANGE,
                TYPE_RECENTS_DURING_SPLIT,
                TYPE_KEYGUARD,
                TYPE_RECENTS_DURING_KEYGUARD,
                TYPE_RECENTS_DURING_DESKTOP,
                TYPE_UNFOLD,
                TYPE_ENTER_PIP_FROM_ACTIVITY_EMBEDDING,
                TYPE_ENTER_PIP_REPLACE_FROM_SPLIT,
                TYPE_ENTER_PIP_WITH_DISPLAY_CHANGE,
                TYPE_OPEN_IN_DESKTOP,
                TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE,
                TYPE_LAUNCH_OR_CONVERT_SPLIT_TASK_TO_BUBBLE,
                TYPE_LAUNCH_OR_CONVERT_PIP_TASK_TO_BUBBLE,
                TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE_FROM_EXISTING_BUBBLE,
                TYPE_LAUNCH_OR_CONVERT_DESKTOP_TASK_TO_BUBBLE,
                TYPE_ENTER_PIP_WITH_PINNED_LAYER_DISMISS,
        })
        @Retention(RetentionPolicy.SOURCE)
        @interface MixedTransitionType {
        }

        // Mixed transition sub-animation types

        /** The default animation for this mixed transition. */
        static final int ANIM_TYPE_DEFAULT = 0;

        /** For ENTER_PIP_FROM_SPLIT, indicates that this is a to-home animation. */
        static final int ANIM_TYPE_GOING_HOME = 1;

        /** For RECENTS_DURING_SPLIT, is set when this turns into a pair->pair task switch. */
        static final int ANIM_TYPE_PAIR_TO_PAIR = 2;

        @IntDef(prefix = {"ANIM_TYPE_"}, value = {
                ANIM_TYPE_DEFAULT,
                ANIM_TYPE_GOING_HOME,
                ANIM_TYPE_PAIR_TO_PAIR,
        })
        @interface SubAnimationType {
        }

        @MixedTransitionType
        final int mType;
        @SubAnimationType
        int mAnimType = ANIM_TYPE_DEFAULT;
        final IBinder mTransition;

        protected final Transitions mPlayer;
        protected final MixedTransitionHandler mMixedHandler;
        protected final PipTransitionController mPipHandler;
        protected final StageCoordinator mSplitHandler;
        protected final KeyguardTransitionHandler mKeyguardHandler;
        protected final BubbleTransitions mBubbleTransitions;
        protected final BubbleHelper mBubbleHelper;
        protected final @Nullable PinnedLayerHandler mPinnedLayerHandler;

        Transitions.TransitionHandler mLeftoversHandler = null;
        TransitionInfo mInfo = null;
        WindowContainerTransaction mFinishWCT = null;
        SurfaceControl.Transaction mFinishT = null;
        Transitions.TransitionFinishCallback mFinishCB = null;

        /**
         * Whether the transition has request for remote transition while mLeftoversHandler
         * isn't remote transition handler.
         * If true and the mLeftoversHandler can handle the transition, need to notify remote
         * transition handler to consume the transition.
         */
        boolean mHasRequestToRemote;

        /**
         * Mixed transitions are made up of multiple "parts". This keeps track of how many
         * parts are currently animating.
         */
        int mInFlightSubAnimations = 0;

        MixedTransition(@MixedTransitionType int type, IBinder transition, Transitions player,
                MixedTransitionHandler mixedHandler, PipTransitionController pipHandler,
                StageCoordinator splitHandler, KeyguardTransitionHandler keyguardHandler,
                BubbleTransitions bubbleTransitions, BubbleHelper bubbleHelper,
                PinnedLayerHandler pinnedLayerHandler) {
            mType = type;
            mTransition = transition;
            mPlayer = player;
            mMixedHandler = mixedHandler;
            mPipHandler = pipHandler;
            mSplitHandler = splitHandler;
            mKeyguardHandler = keyguardHandler;
            mBubbleTransitions = bubbleTransitions;
            mBubbleHelper = bubbleHelper;
            mPinnedLayerHandler = pinnedLayerHandler;
        }

        abstract boolean startAnimation(
                @NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction,
                @NonNull Transitions.TransitionFinishCallback finishCallback);

        abstract void mergeAnimation(
                @NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startT,
                @NonNull SurfaceControl.Transaction finishT,
                @NonNull IBinder mergeTarget,
                @NonNull Transitions.TransitionFinishCallback finishCallback);

        abstract void onTransitionConsumed(
                @NonNull IBinder transition, boolean aborted,
                @Nullable SurfaceControl.Transaction finishT);

        protected boolean startSubAnimation(
                Transitions.TransitionHandler handler, TransitionInfo info,
                SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT) {
            if (mInfo != null) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        "startSubAnimation #%d.%d", mInfo.getDebugId(), info.getDebugId());
            }
            mInFlightSubAnimations++;
            if (!handler.startAnimation(
                    mTransition, info, startT, finishT, wct -> onSubAnimationFinished(info, wct))) {
                mInFlightSubAnimations--;
                return false;
            }
            return true;
        }

        private void onSubAnimationFinished(TransitionInfo info, WindowContainerTransaction wct) {
            mInFlightSubAnimations--;
            if (mInfo != null) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        "onSubAnimationFinished #%d.%d remaining=%d",
                        mInfo.getDebugId(), info.getDebugId(), mInFlightSubAnimations);
            }

            joinFinishArgs(wct);

            if (mInFlightSubAnimations == 0) {
                mFinishCB.onTransitionFinished(mFinishWCT);
            }
        }

        void joinFinishArgs(WindowContainerTransaction wct) {
            if (wct != null) {
                if (mFinishWCT == null) {
                    mFinishWCT = wct;
                } else {
                    mFinishWCT.merge(wct, true /* transfer */);
                }
            }
        }

        /**
         * When a Transition is chosen during {@link #handleRequest}, there is a chance that the
         * handler cannot actually animate the transition when {@link #startAnimation}.
         */
        boolean canAnimateTransition(@NonNull IBinder transition, @NonNull TransitionInfo info) {
            if (!BubbleFlagHelper.enableRootTaskForBubble()) {
                return true;
            }
            if (MixedTransition.isAppBubbleTypeTransition(mType)) {
                return mBubbleTransitions.canAnimateTransition(transition, info);
            } else {
                // The previously resolved mixed handler is no longer relevant, and we can replace
                // it entirely because there are only opening bubble tasks in the changes.
                final List<Integer> openingAppBubbleChangeIndexes =
                        getOpeningAppBubbleChangeIndexes(mBubbleHelper, info);
                return openingAppBubbleChangeIndexes.isEmpty()
                        || openingAppBubbleChangeIndexes.size() != info.getChanges().size();
            }
        }
    }

    @VisibleForTesting
    final ArrayList<MixedTransition> mActiveTransitions = new ArrayList<>();

    public DefaultMixedHandler(
            @NonNull ShellInit shellInit,
            @NonNull Transitions player,
            Optional<SplitScreenController> splitScreenControllerOptional,
            @Nullable PipTransitionController pipTransitionController,
            @Nullable Optional<PipScheduler> pipScheduler,
            @Nullable NormalAppLayerHandler normalAppLayerHandler,
            @Nullable PinnedLayerHandler pinnedLayerHandler,
            Optional<RecentsTransitionHandler> recentsHandlerOptional,
            KeyguardTransitionHandler keyguardHandler,
            Optional<DesktopTasksController> desktopTasksControllerOptional,
            DesktopTasksTransitionHandler desktopTasksTransitionHandler,
            Optional<UnfoldTransitionHandler> unfoldHandler,
            Optional<ActivityEmbeddingController> activityEmbeddingController,
            BubbleTransitions bubbleTransitions,
            BubbleHelper bubbleHelper) {
        mPlayer = player;
        mKeyguardHandler = keyguardHandler;
        mPipHandler = pipTransitionController;
        // Add after dependencies because it is higher priority
        shellInit.addInitCallback(() -> {
            if (mPipHandler != null) {
                mPipHandler.setMixedHandler(this);
            }
            mPipScheduler = pipScheduler.orElse(null);
            mNormalAppLayerHandler = normalAppLayerHandler;
            mPinnedLayerHandler = pinnedLayerHandler;
            mSplitHandler = splitScreenControllerOptional.map(
                    SplitScreenController::getTransitionHandler).orElse(null);
            if (mSplitHandler != null) {
                mSplitHandler.setMixedHandler(this);
            }
            mRecentsHandler = recentsHandlerOptional.orElse(null);
            if (mRecentsHandler != null) {
                mRecentsHandler.addMixer(this);
            }
            mDesktopTasksController = desktopTasksControllerOptional.orElse(null);
            mDesktopTasksTransitionHandler = desktopTasksTransitionHandler;
            mUnfoldHandler = unfoldHandler.orElse(null);
            mActivityEmbeddingController = activityEmbeddingController.orElse(null);
            mBubbleTransitions = bubbleTransitions;
            mBubbleHelper = bubbleHelper;
            mPlayer.addHandler(this);
        }, this);
    }

    @Nullable
    @Override
    public Transitions.RequestResult handleRequestOnly(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        // Transitions involving a task that is being bubbled
        final ActivityManager.RunningTaskInfo task = request.getTriggerTask();
        if (requestHasBubbleEnter(request)) {
            consumeRemoteTransitionIfNecessary(transition, request.getRemoteTransition());

            final WindowContainerTransaction out = new WindowContainerTransaction();
            if (mSplitHandler != null && mSplitHandler.requestImpliesSplitToBubble(task)) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        " Got a Bubble-enter request from a split task");
                mBubbleTransitions.storePendingEnterTransition(transition, request);
                mActiveTransitions.add(createDefaultMixedTransition(
                        MixedTransition.TYPE_LAUNCH_OR_CONVERT_SPLIT_TASK_TO_BUBBLE, transition));
                mBubbleTransitions.handleRequestOnly(out, transition, request);
                mSplitHandler.addExitForBubblesIfNeeded(request, out);
                return new Transitions.RequestResult(out, this);
            } else if (task != null && mPipHandler.isTaskActiveInPip(task.taskId)) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        " Got a Bubble-enter request from a pip task");
                mBubbleTransitions.storePendingEnterTransition(transition, request);
                mActiveTransitions.add(createDefaultMixedTransition(
                        MixedTransition.TYPE_LAUNCH_OR_CONVERT_PIP_TASK_TO_BUBBLE, transition));
                mBubbleTransitions.handleRequestOnly(out, transition, request);
                return new Transitions.RequestResult(out, this);
            } else if (task != null && mDesktopTasksController != null
                    && mDesktopTasksController.isDesktopTask(task)) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        " Got a Bubble-enter request from a desktop task");
                mBubbleTransitions.storePendingEnterTransition(transition, request);
                mActiveTransitions.add(createDefaultMixedTransition(
                        MixedTransition.TYPE_LAUNCH_OR_CONVERT_DESKTOP_TASK_TO_BUBBLE, transition));
                mBubbleTransitions.handleRequestOnly(out, transition, request);
                mDesktopTasksController.addMoveToBubbleFromDesktopChange(out, task, transition);
                return new Transitions.RequestResult(out, this);
            } else {
                // This check should happen after we've checked for split + bubble enter
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        " Got a Bubble-enter request");
                mBubbleTransitions.storePendingEnterTransition(transition, request);
                if (BubbleFlagHelper.isBubbleTransitionPlannerEnabled()) {
                    Transitions.RequestResult requestResult =
                            mBubbleTransitions.handleRequestOnly(out, transition, request);
                    if (requestResult != null && requestResult.hasInterestPlanners()) {
                        return requestResult;
                    }
                } else {
                    mActiveTransitions.add(createDefaultMixedTransition(
                            MixedTransition.TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE, transition));
                    mBubbleTransitions.handleRequestOnly(out, transition, request);
                }
                return new Transitions.RequestResult(out, this);
            }
        } else if (requestHasBubbleEnterFromAppBubbleOrExistingBubble(request)) {
            consumeRemoteTransitionIfNecessary(transition, request.getRemoteTransition());

            if (mSplitHandler != null && mSplitHandler.requestImpliesSplitToBubble(task)) {
                // TODO: Handle from split
            } else {
                // Note: This will currently "intercept" launches even while the bubble is collapsed
                // but we will not actually play any animation in DefaultMixedTransition unless the
                // launch contains an appBubble task as well
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Got a Bubble-enter request "
                        + "from an app bubble or for an existing bubble");
                WindowContainerTransaction out = new WindowContainerTransaction();
                if (!BubbleFlagHelper.enableRootTaskForBubble()) {
                    if (task != null && mBubbleHelper.isAppBubbleTask(task)) {
                        int currentWindowingMode = task.getWindowingMode();
                        if (currentWindowingMode != WINDOWING_MODE_MULTI_WINDOW) {
                            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                                    " Task windowingMode=%d when launching in bubble, update to %d",
                                    currentWindowingMode, WINDOWING_MODE_MULTI_WINDOW);
                            // Make sure the task launching in a bubble is uses multi-window
                            out.setWindowingMode(task.token, WINDOWING_MODE_MULTI_WINDOW);
                        }
                    }
                }

                mActiveTransitions.add(createDefaultMixedTransition(
                        MixedTransition.TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE_FROM_EXISTING_BUBBLE,
                        transition));
                return new Transitions.RequestResult(out, this);
            }
        }

        if (isSplitToPip(request)) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Got a PiP-enter request while "
                    + "Split-Screen is active, so treat it as Mixed.");
            mActiveTransitions.add(createDefaultMixedTransition(
                    MixedTransition.TYPE_ENTER_PIP_FROM_SPLIT, transition));

            WindowContainerTransaction out = new WindowContainerTransaction();
            mPipHandler.augmentRequest(transition, request, out);
            if (mPinnedLayerHandler != null && mPinnedLayerHandler.hasActivePinnedTask()) {
                mPinnedLayerHandler.augmentRequestDismissPinnedTask(transition, request, out);
            }
            if (PipFlags.isPip2ExperimentEnabled() && mSplitHandler.isSplitScreenVisible()) {
                mSplitHandler.removePipFromSplitIfNeeded(request, out);
            }
            mSplitHandler.addEnterOrExitForPipIfNeeded(request, out);
            if (TransitionUtil.isOpeningType(request.getType())
                    && task != null
                    && mSplitHandler.getSplitItemPosition(task.token) == SPLIT_POSITION_UNDEFINED) {
                // If it's an OPEN and the trigger isn't pinned/split,
                // bring the task to the front.
                out.reorder(task.token, true);
            }

            return new Transitions.RequestResult(out, this);
        } else if (request.getType() == TRANSIT_PIP
                && (request.getFlags() & FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY) != 0 && (
                mActivityEmbeddingController != null)) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    " Got a PiP-enter request from an Activity Embedding split");
            mActiveTransitions.add(createDefaultMixedTransition(
                    MixedTransition.TYPE_ENTER_PIP_FROM_ACTIVITY_EMBEDDING, transition));
            // Postpone transition splitting to later.
            WindowContainerTransaction out = new WindowContainerTransaction();
            mPipHandler.augmentRequest(transition, request, out);
            if (mPinnedLayerHandler != null && mPinnedLayerHandler.hasActivePinnedTask()) {
                mPinnedLayerHandler.augmentRequestDismissPinnedTask(transition, request, out);
            }
            return new Transitions.RequestResult(out, this);
        } else if (request.getRemoteTransition() != null
                && TransitionUtil.isOpeningType(request.getType())
                && (task == null
                || (task.topActivityType != ACTIVITY_TYPE_HOME
                && task.topActivityType != ACTIVITY_TYPE_RECENTS))) {
            // Only select transitions with an intent-provided remote-animation because that will
            // usually grab priority and often won't handle PiP. If there isn't an intent-provided
            // remote, then the transition will be dispatched normally and the PipHandler will
            // pick it up.
            Transitions.RequestResult handler = mPlayer.dispatchRequest(transition, request, this);
            if (handler == null) {
                return null;
            }
            final MixedTransition mixed = createDefaultMixedTransition(
                    MixedTransition.TYPE_OPTIONS_REMOTE_AND_PIP_OR_DESKTOP_CHANGE, transition);
            mixed.mLeftoversHandler = handler.mHandler;
            mActiveTransitions.add(mixed);
            if (mixed.mLeftoversHandler != mPlayer.getRemoteTransitionHandler()) {
                mixed.mHasRequestToRemote = true;
                mPlayer.getRemoteTransitionHandler().handleRequest(transition, request);
            }
            if (handler.mHandler == mPipHandler && mPinnedLayerHandler != null
                    && mPinnedLayerHandler.hasActivePinnedTask()) {
                // pip side effect to dismiss the pinned layer
                mPinnedLayerHandler.augmentRequestDismissPinnedTask(transition, request,
                        handler.mWct);
            }
            return new Transitions.RequestResult(handler.mWct, this);
        } else if (mSplitHandler != null && mSplitHandler.isSplitScreenVisible()
                && isOpeningType(request.getType()) && task != null
                && task.getWindowingMode() == WINDOWING_MODE_FULLSCREEN
                && task.getActivityType() == ACTIVITY_TYPE_HOME) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Got a going-home request while "
                    + "Split-Screen is foreground, so treat it as Mixed.");
            Transitions.RequestResult handler = mPlayer.dispatchRequest(transition, request, this);
            if (handler == null) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        " Lean on the remote transition handler to fetch a proper remote via"
                                + " TransitionFilter");
                handler = new Transitions.RequestResult(new WindowContainerTransaction(),
                        mPlayer.getRemoteTransitionHandler());
            }
            final MixedTransition mixed = createRecentsMixedTransition(
                    MixedTransition.TYPE_RECENTS_DURING_SPLIT, transition, task.displayId);
            mixed.mLeftoversHandler = handler.mHandler;
            mActiveTransitions.add(mixed);
            return new Transitions.RequestResult(handler.mWct, this);
        } else if (mUnfoldHandler != null && mUnfoldHandler.shouldPlayUnfoldAnimation(request)) {
            final WindowContainerTransaction wct =
                    mUnfoldHandler.handleRequest(transition, request);
            if (wct != null) {
                mActiveTransitions.add(createDefaultMixedTransition(
                        MixedTransition.TYPE_UNFOLD, transition));
                mBubbleTransitions.notifyUnfoldTransitionStarting(transition);
            }
            return new Transitions.RequestResult(wct, this);
        } else if (mDesktopTasksController != null
                && mDesktopTasksController.shouldPlayDesktopAnimation(request)) {
            final Transitions.RequestResult handler =
                    mPlayer.dispatchRequest(transition, request, /* skip= */ this);
            if (handler == null) {
                return null;
            }
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Got a desktop request, so"
                    + " treat it as Mixed. handler=%s", handler.mHandler);
            final MixedTransition mixed = createDefaultMixedTransition(
                    MixedTransition.TYPE_OPEN_IN_DESKTOP, transition);
            mixed.mLeftoversHandler = handler.mHandler;
            mActiveTransitions.add(mixed);
            return new Transitions.RequestResult(handler.mWct, this);
        }

        // all mixed transitions related to pinned layer should be handled here.
        if (mPinnedLayerHandler != null && mPipScheduler != null) {
            // pinned layer works only with pip2, therefore it's expected to have both controllers
            // always set at this point.

            // dismissing media pip when task is moving a to pinned layer
            if (mPinnedLayerHandler.isPinningRequest(request) && mPipHandler.isInPip()) {
                // TODO: b/451545067 - Optimize this to be done in one transition
                // it's safe to just call schedule, do not actually handle the request here
                // and let the request be handled normally by pinned layer controller
                mPipScheduler.scheduleRemovePip(/* withFadeout= */ true);
                return null;
            }

            // dismissing pinned layer if pip is opening
            if (requestHasPipEnter(request) && mPinnedLayerHandler.hasActivePinnedTask()) {
                mActiveTransitions.add(createDefaultMixedTransition(
                        MixedTransition.TYPE_ENTER_PIP_WITH_PINNED_LAYER_DISMISS, transition));
                final WindowContainerTransaction out = new WindowContainerTransaction();
                mPipHandler.augmentRequest(transition, request, out);
                mPinnedLayerHandler.augmentRequestDismissPinnedTask(transition, request, out);
                return new Transitions.RequestResult(out, this);
            }
        }

        return null;
    }

    private boolean isSplitToPip(@NonNull TransitionRequestInfo request) {
        if (mSplitHandler == null) {
            return false;
        }
        final boolean isPipTransition =
                (request.getType() == TRANSIT_PIP || request.getPipChange() != null);
        if (!isPipTransition) {
            return false;
        }

        if (!mSplitHandler.isSplitActive()) {
            return false;
        }

        final TaskInfo triggerTask = request.getTriggerTask();
        if (triggerTask != null) {
            if (!mSplitHandler.isTaskOnSplitDisplay(triggerTask)) {
                return false;
            }

            if (mSplitHandler.getSplitPositionIgnoreVisible(triggerTask.taskId)
                    != SPLIT_POSITION_UNDEFINED) {
                return true;
            }
        }

        if (PipFlags.isPip2ExperimentEnabled() && request.getPipChange() != null) {
            // In PiP2, PiP-able task can also come in through the pip change request field.
            final TaskInfo pipChangeTask = request.getPipChange().getTaskInfo();
            if (mSplitHandler.getSplitPositionIgnoreVisible(pipChangeTask.taskId)
                    != SPLIT_POSITION_UNDEFINED) {
                return true;
            }
        }

        // If one of the splitting tasks support auto-pip, wm-core might reparent the task to TDA
        // and file a TRANSIT_PIP transition when finishing transitions.
        // @see com.android.server.wm.RootWindowContainer#moveActivityToPinnedRootTask
        return mSplitHandler.hasEmptyStage();
    }

    private DefaultMixedTransition createDefaultMixedTransition(
            @MixedTransitionType int type, IBinder transition) {
        return new DefaultMixedTransition(
                type, transition, mPlayer, this, mPipHandler, mSplitHandler, mKeyguardHandler,
                mUnfoldHandler, mActivityEmbeddingController, mDesktopTasksController,
                mBubbleTransitions, mBubbleHelper, mPinnedLayerHandler);
    }

    @Override
    public Consumer<IBinder> handleRecentsRequest(int displayId) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                " handleRecentsRequest displayId=%d deskActive=%b",
                displayId,
                mDesktopTasksController != null && mDesktopTasksController.isAnyDeskActive(
                        displayId));
        if (mRecentsHandler != null) {
            if (mSplitHandler != null && mSplitHandler.isSplitScreenVisible()) {
                return transition -> setRecentsTransitionDuringSplit(transition, displayId);
            } else if (mKeyguardHandler.isKeyguardShowing()
                    && !mKeyguardHandler.isKeyguardAnimating()) {
                return transition -> setRecentsTransitionDuringKeyguard(transition, displayId);
            } else if (mDesktopTasksController != null
                    && mDesktopTasksController.isAnyDeskActive(displayId)) {
                return transition -> setRecentsTransitionDuringDesktop(transition, displayId);
            }
        }
        return null;
    }

    @Override
    public void handleFinishRecents(boolean returnToApp,
            @NonNull WindowContainerTransaction finishWct,
            @NonNull SurfaceControl.Transaction finishT) {
        if (mRecentsHandler != null) {
            for (int i = mActiveTransitions.size() - 1; i >= 0; --i) {
                final MixedTransition mixed = mActiveTransitions.get(i);
                if (mixed.mType == MixedTransition.TYPE_RECENTS_DURING_SPLIT) {
                    ((RecentsMixedTransition) mixed).onAnimateRecentsDuringSplitFinishing(
                            returnToApp, finishWct, finishT);
                } else if (mixed.mType == MixedTransition.TYPE_RECENTS_DURING_DESKTOP) {
                    ((RecentsMixedTransition) mixed).onAnimateRecentsDuringDesktopFinishing(
                            returnToApp, finishWct);
                }
            }
        }
    }

    private void setRecentsTransitionDuringSplit(IBinder transition, int displayId) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                " Got a recents request while Split-Screen is foreground, so treat it as Mixed.");
        mActiveTransitions.add(createRecentsMixedTransition(
                MixedTransition.TYPE_RECENTS_DURING_SPLIT, transition, displayId));
    }

    private void setRecentsTransitionDuringKeyguard(IBinder transition, int displayId) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                " Got a recents request while keyguard is visible, so treat it as Mixed.");
        mActiveTransitions.add(createRecentsMixedTransition(
                MixedTransition.TYPE_RECENTS_DURING_KEYGUARD, transition, displayId));
    }

    private void setRecentsTransitionDuringDesktop(IBinder transition, int displayId) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                " Got a recents request while desktop mode is active, so treat it as Mixed.");
        mActiveTransitions.add(createRecentsMixedTransition(
                MixedTransition.TYPE_RECENTS_DURING_DESKTOP, transition, displayId));
    }

    private MixedTransition createRecentsMixedTransition(@MixedTransitionType int type,
            IBinder transition, int displayId) {
        return new RecentsMixedTransition(type, transition, mPlayer, this, mPipHandler,
                mSplitHandler, mKeyguardHandler, mRecentsHandler, mDesktopTasksController,
                mBubbleTransitions, mBubbleHelper, mPinnedLayerHandler, displayId);
    }

    static TransitionInfo subCopy(@NonNull TransitionInfo info,
            @WindowManager.TransitionType int newType, boolean withChanges) {
        final TransitionInfo out = new TransitionInfo(newType, withChanges ? info.getFlags() : 0);
        out.setTrack(info.getTrack());
        out.setDebugId(info.getDebugId());
        if (withChanges) {
            for (int i = 0; i < info.getChanges().size(); ++i) {
                out.getChanges().add(info.getChanges().get(i));
            }
        }
        for (int i = 0; i < info.getRootCount(); ++i) {
            out.addRoot(info.getRoot(i));
        }
        return out;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {

        MixedTransition mixed = null;
        for (int i = mActiveTransitions.size() - 1; i >= 0; --i) {
            if (mActiveTransitions.get(i).mTransition != transition) continue;
            mixed = mActiveTransitions.get(i);
            break;
        }

        if (mixed != null && !mixed.canAnimateTransition(transition, info)) {
            // Let the previous mixed handler do the cleanup.
            mixed.onTransitionConsumed(transition, true /* aborted */, finishTransaction);

            // The previously resolved mixed handler is no longer relevant. Let's see if any other
            // mixed handler is going to animate it.
            mActiveTransitions.remove(mixed);
            mixed = null;
        }

        if (BubbleFlagHelper.enableRootTaskForBubble()) {
            if (mixed == null) {
                // If there was no requested transition but the transition includes an opening
                // bubble task, then handle it here now.
                final List<Integer> openingAppBubbleChangeIndexes =
                        getOpeningAppBubbleChangeIndexes(mBubbleHelper, info);
                if (!openingAppBubbleChangeIndexes.isEmpty()) {
                    if (mSplitHandler != null && mSplitHandler.requestImpliesSplitToBubble(
                            info.getChanges().get(
                                    openingAppBubbleChangeIndexes.getFirst()).getTaskInfo())) {
                        // TODO: Handle from split
                    } else {
                        // Add a mixed transition
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Got a Bubble-enter "
                                + "transition from an app bubble or for an existing bubble");
                        mixed = createDefaultMixedTransition(MixedTransition
                                        .TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE_FROM_EXISTING_BUBBLE,
                                transition);
                        mActiveTransitions.add(mixed);
                    }
                }
            }
        } else {
            // If there was no requested transition but the transition includes an opening bubble
            // task, then handle it here now
            TransitionInfo.Change bubbleChange =
                    transitionHasBubbleEnterFromAppBubbleOrExistingBubble(info);
            if (mixed == null && bubbleChange != null) {
                if (mSplitHandler != null && mSplitHandler.requestImpliesSplitToBubble(
                        bubbleChange.getTaskInfo())) {
                    // TODO: Handle from split
                } else {
                    // Add a mixed transition
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Got a Bubble-enter "
                            + "transition from an app bubble or for an existing bubble");
                    mixed = createDefaultMixedTransition(
                            MixedTransition.TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE_FROM_EXISTING_BUBBLE,
                            transition);
                    mActiveTransitions.add(mixed);
                }
            }
        }

        // Offer Keyguard the opportunity to take over lock transitions - ideally we could know by
        // the time of handleRequest, but we need more information than is available at that time.
        if (KeyguardTransitionHandler.handles(info)) {
            if (mixed != null && mixed.mType != MixedTransition.TYPE_KEYGUARD) {
                final MixedTransition keyguardMixed =
                        createDefaultMixedTransition(MixedTransition.TYPE_KEYGUARD, transition);
                mActiveTransitions.add(keyguardMixed);
                Transitions.TransitionFinishCallback callback = wct -> {
                    mActiveTransitions.remove(keyguardMixed);
                    finishCallback.onTransitionFinished(wct);
                };
                final boolean hasAnimateKeyguard;
                if (MixedTransition.isAppBubbleTypeTransition(mixed.mType)) {
                    hasAnimateKeyguard = animateKeyguardWithBubbles(keyguardMixed, transition, info,
                            startTransaction, finishTransaction, callback);
                } else {
                    hasAnimateKeyguard = animateKeyguard(keyguardMixed, info, startTransaction,
                            finishTransaction, callback, mKeyguardHandler, mPipHandler);
                }
                if (hasAnimateKeyguard) {
                    ProtoLog.w(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                            "Converting mixed transition into a keyguard transition");
                    // Consume the original mixed transition
                    mActiveTransitions.remove(mixed);
                    mixed.onTransitionConsumed(transition, false, null);
                    return true;
                } else {
                    // Keyguard handler cannot handle it, process through original mixed
                    mActiveTransitions.remove(keyguardMixed);
                }
            } else if (mPipHandler != null) {
                mPipHandler.syncPipSurfaceState(info, startTransaction, finishTransaction);
            }
        }

        if (mixed == null) return false;

        final MixedTransition chosenTransition = mixed;
        Transitions.TransitionFinishCallback callback = wct -> {
            mActiveTransitions.remove(chosenTransition);
            finishCallback.onTransitionFinished(wct);
        };

        boolean handled = chosenTransition.startAnimation(
                transition, info, startTransaction, finishTransaction, callback);
        if (!handled) {
            mActiveTransitions.remove(chosenTransition);
        }
        return handled;
    }

    private void unlinkMissingParents(TransitionInfo from) {
        for (int i = 0; i < from.getChanges().size(); ++i) {
            final TransitionInfo.Change chg = from.getChanges().get(i);
            if (chg.getParent() == null) continue;
            if (from.getChange(chg.getParent()) == null) {
                from.getChanges().get(i).setParent(null);
            }
        }
    }

    private boolean isWithinTask(TransitionInfo info, TransitionInfo.Change chg) {
        TransitionInfo.Change curr = chg;
        while (curr != null) {
            if (curr.getTaskInfo() != null) return true;
            if (curr.getParent() == null) break;
            curr = info.getChange(curr.getParent());
        }
        return false;
    }

    /**
     * This is intended to be called by SplitCoordinator as a helper to mix a split handling
     * transition with an entering-pip change. The use-case for this is when an auto-pip change
     * gets collected into the transition which has already claimed by
     * StageCoordinator.handleRequest. This happens when launching a fullscreen app while having an
     * auto-pip activity in the foreground split pair.
     */
    // TODO(b/287704263): Remove when split/mixed are reversed.
    public boolean animatePendingEnterPipFromSplit(IBinder transition, TransitionInfo info,
            SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT,
            Transitions.TransitionFinishCallback finishCallback, boolean replacingPip) {
        int type = replacingPip
                ? MixedTransition.TYPE_ENTER_PIP_REPLACE_FROM_SPLIT
                : MixedTransition.TYPE_ENTER_PIP_FROM_SPLIT;
        final MixedTransition mixed = createDefaultMixedTransition(type, transition);
        mActiveTransitions.add(mixed);
        Transitions.TransitionFinishCallback callback = wct -> {
            mActiveTransitions.remove(mixed);
            finishCallback.onTransitionFinished(wct);
        };
        return mixed.startAnimation(transition, info, startT, finishT, callback);
    }

    /**
     * This is intended to be called by SplitCoordinator as a helper to mix an already-pending
     * split transition with a display-change. The use-case for this is when a display
     * change/rotation gets collected into a split-screen enter/exit transition which has already
     * been claimed by StageCoordinator.handleRequest. This happens during launcher tests.
     */
    public boolean animatePendingSplitWithDisplayChange(@NonNull IBinder transition,
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        final TransitionInfo everythingElse = subCopy(info, info.getType(), true /* withChanges */);
        final TransitionInfo displayPart = subCopy(info, TRANSIT_CHANGE, false /* withChanges */);
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (isWithinTask(info, change)) continue;
            displayPart.addChange(change);
            everythingElse.getChanges().remove(i);
        }
        if (displayPart.getChanges().isEmpty()) return false;
        unlinkMissingParents(everythingElse);
        final MixedTransition mixed = createDefaultMixedTransition(
                MixedTransition.TYPE_DISPLAY_AND_SPLIT_CHANGE, transition);
        mActiveTransitions.add(mixed);
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animation is a mix of display change "
                + "and split change.");
        // We need to split the transition into 2 parts: the split part and the display part.
        mixed.mInFlightSubAnimations = 2;

        Transitions.TransitionFinishCallback finishCB = (wct) -> {
            --mixed.mInFlightSubAnimations;
            mixed.joinFinishArgs(wct);
            if (mixed.mInFlightSubAnimations > 0) return;
            mActiveTransitions.remove(mixed);
            finishCallback.onTransitionFinished(mixed.mFinishWCT);
        };

        // Dispatch the display change. This will most-likely be taken by the default handler.
        // Do this first since the first handler used will apply the startT; the display change
        // needs to take a screenshot before that happens so we need it to be the first handler.
        mixed.mLeftoversHandler = mPlayer.dispatchTransition(mixed.mTransition, displayPart,
                startT, finishT, finishCB, mSplitHandler);

        // Note: at this point, startT has probably already been applied, so we are basically
        // giving splitHandler an empty startT. This is currently OK because display-change will
        // grab a screenshot and paste it on top anyways.
        mSplitHandler.startPendingAnimation(transition, everythingElse, startT, finishT, finishCB);
        return true;
    }

    /**
     * For example: pip is entering in rotation 0, and then the display changes to rotation 90
     * before the pip transition is ready. So the info contains both the entering pip and display
     * change. In this case, the pip can go to the end state in new rotation directly, and let the
     * display level animation cover all changed participates.
     */
    public void animateEnteringPipWithDisplayChange(@NonNull IBinder transition,
            @NonNull TransitionInfo info, @NonNull TransitionInfo.Change pipChange,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        // In order to play display level animation, force the type to CHANGE (it could be PIP).
        final TransitionInfo changeInfo = info.getType() != TRANSIT_CHANGE
                ? subCopy(info, TRANSIT_CHANGE, true /* withChanges */) : info;
        changeInfo.getChanges().remove(pipChange);
        final MixedTransition mixed = createDefaultMixedTransition(
                MixedTransition.TYPE_ENTER_PIP_WITH_DISPLAY_CHANGE, transition);
        mActiveTransitions.add(mixed);
        mixed.mInFlightSubAnimations = 2;
        final Transitions.TransitionFinishCallback finishCB = wct -> {
            --mixed.mInFlightSubAnimations;
            mixed.joinFinishArgs(wct);
            if (mixed.mInFlightSubAnimations > 0) return;
            mActiveTransitions.remove(mixed);
            finishCallback.onTransitionFinished(mixed.mFinishWCT);
        };
        // Perform the display animation first.
        mixed.mLeftoversHandler = mPlayer.dispatchTransition(mixed.mTransition, changeInfo,
                startT, finishT, finishCB, mPipHandler);
        // Use a standalone finish transaction for pip because it will apply immediately.
        final SurfaceControl.Transaction pipFinishT = new SurfaceControl.Transaction();
        mPipHandler.startEnterAnimation(pipChange, startT, pipFinishT, wct -> {
            // Apply immediately to avoid potential flickering by bounds change at the end of
            // display animation.
            mPipHandler.applyTransaction(wct);
            finishCB.onTransitionFinished(null /* wct */);
        });
        // Jump to the pip end state directly and make sure the real finishT have the latest state.
        mPipHandler.end();
        mPipHandler.syncPipSurfaceState(info, startT, finishT);
    }

    private static boolean animateKeyguard(@NonNull final MixedTransition mixed,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull KeyguardTransitionHandler keyguardHandler,
            PipTransitionController pipHandler) {
        if (mixed.mFinishT == null) {
            mixed.mFinishT = finishTransaction;
            mixed.mFinishCB = finishCallback;
        }
        // Sync pip state.
        if (pipHandler != null) {
            pipHandler.syncPipSurfaceState(info, startTransaction, finishTransaction);
        }
        return mixed.startSubAnimation(keyguardHandler, info, startTransaction, finishTransaction);
    }

    /**
     * Animate keyguard when the transition also contains changes related to app bubbles.
     * Runs the animation in stages by first animating away the keyguard. Once that is complete,
     * starts the animation for bubbles.
     * Bubbles animation needs to run after keyguard as bubbles views are hidden as long as keyguard
     * or notification shade is visible.
     */
    private boolean animateKeyguardWithBubbles(MixedTransition keyguardMixed,
            @NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {

        // We need to split out bubbles changes from the other changes
        final TransitionInfo keyguardInfo = subCopy(info, info.getType(), /* withChanges= */ false);
        final TransitionInfo bubbleInfo = subCopy(info, info.getType(), /* withChanges= */ false);
        // Copy over flags as keyguard handler uses them to detect if it should animate
        keyguardInfo.setFlags(info.getFlags());

        final Set<Integer> bubbleChangeIndexes = new HashSet<>(
                getOpeningAppBubbleChangeIndexes(mBubbleHelper, info));
        for (int i = 0; i < info.getChanges().size(); i++) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getTaskInfo() != null
                    && change.getTaskInfo().getActivityType() == ACTIVITY_TYPE_HOME) {
                // Add home change to both as both handlers want to animate it
                bubbleInfo.addChange(change);
                keyguardInfo.addChange(change);
                continue;
            }
            if (bubbleChangeIndexes.contains(i)) {
                bubbleInfo.addChange(change);
            } else {
                keyguardInfo.addChange(change);
            }
        }

        // Start bubbles animation from keyguard finishCallback so it runs after keyguard
        Transitions.TransitionFinishCallback keyguardFinishCallback = wct -> {
            animateExpandBubblesFromKeyguard(transition, bubbleInfo,
                    new SurfaceControl.Transaction(), finishTransaction, finishCallback);
        };

        return animateKeyguard(keyguardMixed, keyguardInfo, startTransaction, finishTransaction,
                keyguardFinishCallback, mKeyguardHandler, mPipHandler);
    }

    private void animateExpandBubblesFromKeyguard(@NonNull IBinder transition,
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        final TransitionInfo.Change bubbleTask = mBubbleHelper.getEnterBubbleTask(info);
        if (bubbleTask == null || bubbleTask.getTaskInfo() == null) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    "Tried to open bubbles from keyguard but bubbles changes are missing");
            finishCallback.onTransitionFinished(null);
            return;
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                "Animating bubbles expand as part of keyguard going away transition");
        // Home task is reparented under the notification shade during keyguard animation.
        // In order for home to show up behind bubbles animation, we need to move it
        // to the transition root while the bubbles part of the transition is animating.
        TransitionInfo.Change homeChange = MixedTransitionHelper.getHomeChange(info);
        if (homeChange != null) {
            // Create a new transaction as the bubbles handler first inflates the bubble views,
            // and only after that is complete, applies the startTransaction.
            try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                tx.reparent(homeChange.getLeash(), info.getRoot(0).getLeash());
                tx.apply();
            }
        }
        Consumer<Transitions.TransitionHandler> onInflatedCallback =
                handler -> handler.startAnimation(transition, info, startTransaction,
                        finishTransaction, finishCallback);
        mBubbleTransitions.startExpandAndSelectBubbleForExistingTransition(transition,
                bubbleTask.getTaskInfo(), onInflatedCallback);
    }

    /**
     * Use to when split use intent to enter, check if this enter transition should be mixed or
     * not.
     */
    public boolean isIntentInPip(PendingIntent intent) {
        // Check if this intent package is same as pip one or not, if true we want let the pip
        // task enter split.
        if (mPipHandler != null) {
            return mPipHandler
                    .isPackageActiveInPip(ComponentUtils.getPackageName(intent.getIntent()));
        }
        return false;
    }

    /**
     * Use to when split use taskId to enter, check if this enter transition should be mixed or
     * not.
     */
    public boolean isTaskInPip(int taskId, ShellTaskOrganizer shellTaskOrganizer) {
        // Check if this intent package is same as pip one or not, if true we want let the pip
        // task enter split.
        if (mPipHandler != null) {
            return mPipHandler.isPackageActiveInPip(
                    ComponentUtils.getPackageName(taskId, shellTaskOrganizer));
        }
        return false;
    }

    /** @return whether the transition-request represents a pip-entry. */
    public boolean requestHasPipEnter(TransitionRequestInfo request) {
        return mPipHandler.requestHasPipEnter(request);
    }

    /** Whether a particular change is a window that is entering pip. */
    // TODO(b/287704263): Remove when split/mixed are reversed.
    public boolean isEnteringPip(TransitionInfo.Change change,
            @WindowManager.TransitionType int transitType) {
        return mPipHandler.isEnteringPip(change, transitType);
    }

    /**
     * Returns whether the given request for a launching bubble and should be handled by the
     * bubbles transition.
     */
    public boolean requestHasBubbleEnter(@NonNull TransitionRequestInfo request) {
        return BubbleFlagHelper.enableCreateAnyBubble()
                && request.getTriggerTask() != null
                && mBubbleTransitions.hasPendingEnterTransition(request);
    }

    /**
     * Returns whether the given request for a launching task is from an app bubble or for an
     * existing bubble and should be handled by the bubbles transition.
     */
    public boolean requestHasBubbleEnterFromAppBubbleOrExistingBubble(
            @NonNull TransitionRequestInfo request) {
        return BubbleFlagHelper.enableCreateAnyBubble()
                && request.getTriggerTask() != null
                && mBubbleHelper.isAppBubbleTask(request.getTriggerTask());
    }

    /**
     * Returns the associated indexes of the opening app bubble task changes in the given
     * started transition.
     */
    private static List<Integer> getOpeningAppBubbleChangeIndexes(
            @NonNull BubbleHelper bubbleHelper, @NonNull TransitionInfo info) {
        final ArrayList<Integer> bubbleChangeIndexes = new ArrayList<>();
        for (int i = 0; i < info.getChanges().size(); i++) {
            final TransitionInfo.Change chg = info.getChanges().get(i);
            final ActivityManager.RunningTaskInfo taskInfo = chg.getTaskInfo();
            // Exclude non-standard activity transition scenarios.
            if (taskInfo == null || taskInfo.getActivityType() != ACTIVITY_TYPE_STANDARD) {
                continue;
            }
            // Only process opening transitions.
            if (!TransitionUtil.isOpeningMode(chg.getMode())) {
                continue;
            }
            // Skip non-app-bubble tasks
            if (!bubbleHelper.isAppBubbleTask(taskInfo)
                    && !bubbleHelper.isAppBubbleRootTask(taskInfo)) {
                continue;
            }
            bubbleChangeIndexes.add(i);
        }
        return bubbleChangeIndexes;
    }

    /**
     * Returns the associated change for the bubbled task in the given started transition if it is
     * from an app bubble or for an existing bubble and should be handled by the bubbles transition
     */
    public TransitionInfo.Change transitionHasBubbleEnterFromAppBubbleOrExistingBubble(
            @NonNull TransitionInfo info) {
        if (!BubbleFlagHelper.enableCreateAnyBubble()) {
            return null;
        }
        final TransitionInfo.Change change = mBubbleHelper.getEnterBubbleTask(info);
        return change != null && TransitionUtil.isOpeningMode(change.getMode()) ? change : null;
    }

    /**
     * Notifies the remote transition that it will not be played and is consumed by another
     * transition (and it can clean up accordingly).
     */
    private void consumeRemoteTransitionIfNecessary(@NonNull IBinder transition,
            @Nullable RemoteTransition remote) {
        if (remote != null) {
            try {
                remote.getRemoteTransition().onTransitionConsumed(transition, false /* aborted */);
            } catch (RemoteException e) {
                Log.e(ShellProtoLogGroup.WM_SHELL_TRANSITIONS.getTag(),
                        "Error notifying remote onTransitionConsumed()", e);
            }
        }
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        for (int i = 0; i < mActiveTransitions.size(); ++i) {
            if (mActiveTransitions.get(i).mTransition != mergeTarget) continue;

            MixedTransition mixed = mActiveTransitions.get(i);
            if (mixed.mInFlightSubAnimations <= 0
                    // The split-to-bubble trampoline transition also splits to two transitions.
                    // Also merge these two transitions here.
                    && !shouldMergeSplitToBubbleTrampolineTransition(mixed, info)) {
                // Already done, so no need to end it.
                return;
            }
            mixed.mergeAnimation(transition, info, startT, finishT, mergeTarget, finishCallback);
        }
    }

    private boolean shouldMergeSplitToBubbleTrampolineTransition(@NonNull MixedTransition mixed,
            @NonNull TransitionInfo info) {
        return isSplitToBubbleTrampolineTransition(mixed, info);
    }

    /**
     * Returns whether the given transition is a split-to-bubble trampoline transition,
     * which includes one launch-or-convert-to-bubble transition with one split dismiss transition.
     */
    private boolean isSplitToBubbleTrampolineTransition(@NonNull MixedTransition mixed,
            @NonNull TransitionInfo info) {
        return mixed.mType == MixedTransition.TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE
                && info.getType() == TRANSIT_SPLIT_DISMISS
                && getTopSplitStageToKeep(
                        info.getChanges(), mSplitHandler, null /* bubblingTask */)
                                != SplitScreen.STAGE_TYPE_UNDEFINED;
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @Nullable SurfaceControl.Transaction finishT) {
        MixedTransition mixed = null;
        for (int i = mActiveTransitions.size() - 1; i >= 0; --i) {
            if (mActiveTransitions.get(i).mTransition != transition) continue;
            mixed = mActiveTransitions.remove(i);
            break;
        }
        if (mixed != null) {
            mixed.onTransitionConsumed(transition, aborted, finishT);
        }
    }

    /**
     * Update an incoming {@link TransitionInfo} with the leashes from an existing
     * {@link TransitionInfo} so that it can take over some parts of the animation without
     * reparenting to new transition roots.
     */
    static void handoverTransitionLeashes(
            @NonNull TransitionInfo from,
            @NonNull TransitionInfo to,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT) {

        // Show the roots in case they contain new changes not present in the original transition.
        for (int j = to.getRootCount() - 1; j >= 0; --j) {
            startT.show(to.getRoot(j).getLeash());
        }

        // Find all of the leashes from the original transition.
        Map<WindowContainerToken, TransitionInfo.Change> originalChanges = new ArrayMap<>();
        for (TransitionInfo.Change oldChange : from.getChanges()) {
            if (oldChange.getContainer() != null) {
                originalChanges.put(oldChange.getContainer(), oldChange);
            }
        }

        // Merge the animation leashes by re-using the original ones if we see the same container
        // in the new transition and the old.
        for (TransitionInfo.Change newChange : to.getChanges()) {
            if (originalChanges.containsKey(newChange.getContainer())) {
                final TransitionInfo.Change oldChange = originalChanges.get(
                        newChange.getContainer());
                startT.reparent(newChange.getLeash(), null);
                newChange.setLeash(oldChange.getLeash());
            }
        }
    }
}
