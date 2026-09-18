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

import static android.view.WindowManager.TRANSIT_SLEEP;
import static android.window.TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY;
import static android.window.TransitionInfo.FLAG_IS_BEHIND_STARTING_WINDOW;
import static android.window.TransitionInfo.FLAG_NO_ANIMATION;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_MIXPATCHER;
import static com.android.wm.shell.transition.Transitions.transitTypeToString;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.keyguard.KeyguardTransitionPlanner;
import com.android.wm.shell.shared.annotations.ShellMainThread;

import java.util.ArrayList;
import java.util.List;

/**
 * Modular dispatcher/mixer for transition animations.
 *
 * It manages the shell-side lifecycle of a transition and handles mixing and sequencing of their
 * components among multiple shell-defined {@link ITransitionAnimation} implementations.
 *
 * The mixpatching approach is based on the following interfaces:
 *  * {@link ITransitionAnimation} - animates a set of containers from a provided start state. Also
 *                                   handles detaching containers from itself (providing a snapshot
 *                                   of their animating state). An animation is tied to a single
 *                                   Transition.
 *  * {@link ITransitionPlanner} - given a {@link TransitionInfo}, plans out which changes should
 *                                 be animated by which {@link ITransitionAnimation}s.
 *
 * The dispatch/mixing works like this:
 *  1. When a transition is ready, ask a prioritized list of {@link ITransitionPlanner}s how to
 *     assign the transition's containers to a set of *NEW* {@link ITransitionAnimation}s. Each
 *     planner may also add extra instructions to detach a container from its "rest" state.
 *  2. Any containers assigned by one {@link ITransitionPlanner} are removed from the set of
 *     unplanned changes before tapping the next planner. Eventually, there should be no unplanned
 *     changes left (meaning all containers have been assigned to animtaions).
 *  3. All the containers that are already animating are detached from their current animations so
 *     they can be transferred over to their new ones (while recording their animating state).
 *  4. The new animations are all started from the current animation states.
 *
 * The shell-side lifecycle of a transition has the following stages:
 *  1. Pending: Shell asked WM to make changes. Wait for WM to notify that it is ready.
 *  2. Dispatch: The transition is ready. Figure out who is playing what part of the transition.
 *  3. Transfer: If some parts were already animating, transfer those parts to the new animation.
 *  4. Playing: Start all the animations and wait for them to finish.
 *  5. Finished: animations are finished, wait until idle and then report back to WM in order.
 *
 */
public class TransitionMixpatcher {
    static final String TAG = "TransitionMixpatcher";

    private static class TransitionState {
        @NonNull final IBinder mTransition;
        int mDebugId = -1;

        /** List of planners that are interested in the result of this specific transition. */
        List<ITransitionPlanner> mInterest = null;

        /** Transaction to apply right before starting animation. */
        SurfaceControl.Transaction mStartT;

        /** The full information containing all changes that WM made. */
        TransitionInfo mBaseInfo;

        /**
         * Information needed to start all the animations. Used during dispatch and transfer.
         * Becomes {@code null} once the animations have started.
         */
        AnimationPlan mPlan;

        /** List of the animations playing for this transition. */
        final ArrayList<ITransitionAnimation> mAnimations = new ArrayList<>();

        /**
         * Transaction to apply after all animations have ended. This restores surface state and
         * hierarchy to their "resting" state.
         */
        SurfaceControl.Transaction mFinishT;

        /** Collects all the surfaces that need to be released when transition is finished. */
        final ArrayList<SurfaceControl> mCleanupSurfaces = new ArrayList<>();

        TransitionState(@NonNull IBinder transition) {
            mTransition = transition;
        }
    }

    private static class AnimatingContainer {
        @NonNull final WindowContainerToken mContainer;
        @NonNull
        ITransitionAnimation mAnimation;

        AnimatingContainer(@NonNull WindowContainerToken container,
                @NonNull ITransitionAnimation animation) {
            mContainer = container;
            mAnimation = animation;
        }
    }

    final ShellTaskOrganizer mOrganizer;
    final ShellExecutor mMainExecutor;

    /**
     * List of planners. This is checked "backwards" -- planners added later are
     * considered "more-specific" and thus higher priority.
     */
    final ArrayList<ITransitionPlanner> mPlanners = new ArrayList<>();

    /** Token for tracking "sleep" as part of a transition. */
    final WindowContainerToken mSleepProxy = WindowContainerToken.createProxy("SleepProxy");

    /**
     * Hard-coded "prefix" planners for special-case handling. These are hard-coded because
     * they have specific interactions with the transition dispatch process.
     */
    private final ITransitionPlanner mInvisiblesPlanner = new InvisiblesPlanner();
    private ITransitionPlanner mKeyguardPlanner;
    private ITransitionPlanner mSleepPlanner = new SleepPlanner();

    /** List of currently-animating containers. */
    private final ArrayList<AnimatingContainer> mContainers = new ArrayList<>();

    /** List of transitions which have been sent to WM and are waiting to become ready. */
    private final ArrayList<TransitionState> mPending = new ArrayList<>();

    /**
     * Ordered list of all transitions which WM has processed and reported as ready. Transitions
     * remain in this list for the duration of their "shell-side lifecycle" -- until they have
     * been reported as finished to WM (via {@link ShellTaskOrganizer#finishTransition}).
     */
    private final ArrayList<TransitionState> mReadyOrder = new ArrayList<>();

    /**
     * Transitions which are ready but are waiting to be dispatched. This happens while an async
     * animation transfer is ongoing.
     */
    private final ArrayList<TransitionState> mDispatchQueue = new ArrayList<>();

    /**
     * The transition that is waiting for all of its containers to be detached from prior
     * animations.
     */
    private TransitionState mTransferring = null;

    /** List of transition animations that are currently animating. */
    private final ArrayList<TransitionState> mPlaying = new ArrayList<>();

    /** List of all transitions which are finished (in order) but have not yet reported to WM. */
    private final ArrayList<TransitionState> mFinished = new ArrayList<>();

    /** List of {@link Runnable} instances to run when the last active transition has finished. */
    private final ArrayList<Runnable> mRunWhenIdleQueue = new ArrayList<>();

    private float mAnimScaleSetting = 1.0f;

    void setAnimScaleSetting(float scale) {
        mAnimScaleSetting = scale;
    }

    TransitionMixpatcher(@NonNull ShellTaskOrganizer organizer,
            @NonNull ShellExecutor mainExecutor) {
        mOrganizer = organizer;
        mMainExecutor = mainExecutor;
        mKeyguardPlanner = new KeyguardTransitionPlanner(mMainExecutor);
    }

    /**
     * Used for the legacy dispatcher to hook into the pre-distribute stage. Using this means
     * that {@param prePlanner} is expected to handle all the prefix transitions like
     * sleep/keyguard.
     *
     * @deprecated as this is only intended to be used during migration.
     */
    @Deprecated
    void overridePrePlanner(@NonNull ITransitionPlanner prePlanner) {
        mKeyguardPlanner = prePlanner;
        // No-op sleep so there's only one pre-planner
        mSleepPlanner = new ITransitionPlanner() {
            @Override
            public void plan(@NonNull AnimationPlan plan, @NonNull TransitionInfo fullInfo,
                    @NonNull IBinder transition, @NonNull TransitionInfo info,
                    @NonNull SurfaceControl.Transaction startTransaction) {
            }

            @NonNull
            @Override
            public String getDebugName() {
                return "NoOpSleep";
            }
        };
    }

    private static int findTransition(@NonNull IBinder transition,
            @NonNull ArrayList<TransitionState> list) {
        for (int i = 0; i < list.size(); ++i) {
            if (list.get(i).mTransition == transition) {
                return i;
            }
        }
        return -1;
    }

    private static TransitionState takeTransition(@NonNull IBinder transition,
            @NonNull ArrayList<TransitionState> list) {
        int idx = findTransition(transition, list);
        return idx >= 0 ? list.remove(idx) : null;
    }

    private TransitionState getKnownTransition(@NonNull IBinder transition) {
        int idx = findTransition(transition, mReadyOrder);
        if (idx >= 0) {
            return mReadyOrder.get(idx);
        }
        idx = findTransition(transition, mPending);
        return idx < 0 ? null : mPending.get(idx);
    }

    private int findAnimatingContainer(@NonNull WindowContainerToken token) {
        for (int i = 0; i < mContainers.size(); ++i) {
            if (mContainers.get(i).mContainer.equals(token)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Start a new or requested transition.
     *
     * @param transition If non-null, starts a transition requested by WM (via
     *                   @{link ITransitionPlayer#requestStartTransition})
     * @param interest An optional list of planners that should have first dibs (in order) at
     *                 planning the transition's animation
     * @return The started transition's token (same as {@param transition} if it was not-null)
     */
    IBinder startTransition(@Nullable IBinder transition, @WindowManager.TransitionType int type,
            @Nullable WindowContainerTransaction wct,
            @Nullable List<ITransitionPlanner> interest) {
        ProtoLog.v(WM_SHELL_MIXPATCHER, "startTransition| %s type=%s wct=%s interest=%s",
                transition, transitTypeToString(type), wct, interest);
        final TransitionState pt;
        if (transition == null) {
            final IBinder newTransit = mOrganizer.startNewTransition(type, wct);
            pt = new TransitionState(newTransit);
        } else {
            mOrganizer.startTransition(transition, wct);
            pt = new TransitionState(transition);
        }
        pt.mInterest = interest;
        mPending.add(pt);
        return pt.mTransition;
    }

    void onTransitionReady(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT) {
        ProtoLog.v(WM_SHELL_MIXPATCHER, "onTransitionReady| %s info=%s", transition, info);
        // TODO: hookup observers? (for housekeeping)
        TransitionState state = takeTransition(transition, mPending);
        if (state == null) {
            if (getKnownTransition(transition) != null) {
                Log.wtf(TAG, "Trying to re-dispatch a transition that is no-longer pending #"
                        + info.getDebugId() + " " + transition);
            } else {
                Log.wtf(TAG, "Trying to dispatch an unknown transition #" + info.getDebugId()
                        + " " + transition);
            }
            return;
        }
        mReadyOrder.add(state);
        state.mBaseInfo = info;
        state.mDebugId = info.getDebugId();
        state.mStartT = startT;
        state.mFinishT = finishT;

        // Record root surfaces that need to be cleaned-up later
        for (int i = 0; i < info.getRootCount(); ++i) {
            state.mCleanupSurfaces.add(info.getRoot(i).getLeash());
        }

        if (mDispatchQueue.isEmpty() && mTransferring == null) {
            // nothing preventing dispatch, so dispatch now.
            dispatch(state);
        } else {
            ProtoLog.v(WM_SHELL_MIXPATCHER, "onTransitionReady| queueing dispatch #%d %s",
                    info.getDebugId(), transition);
            mDispatchQueue.add(state);
        }
    }

    /**
     * Asks {@param planner} to plan animations for some of the changes in (and ONLY in)
     * {@code plan.unplannedInfo}. It can attempt to {@link AnimationPlan#detach} any container in
     * {@param fullInfo} though.
     *
     * @param plan contains the plan to populate (using {@link AnimationPlan#setAnimation}
     *             and {@link AnimationPlan#detach}.
     * @param fullInfo is the info for the whole transition including change which have already
     *                 been planned.
     */
    private boolean addToPlan(@NonNull AnimationPlan plan, @NonNull TransitionInfo fullInfo,
            ITransitionPlanner planner, @NonNull IBinder transition,
            @NonNull SurfaceControl.Transaction startT) {
        planner.plan(plan, fullInfo, transition, plan.mUnplannedInfo, startT);
        if (plan.mPlannedSoFar.isEmpty()) return false;
        ProtoLog.v(WM_SHELL_MIXPATCHER, "dispatch| planned %d containers from #%d via %s",
                plan.mPlannedSoFar.size(), fullInfo.getDebugId(),
                planner.getDebugName());
        for (int w = 0; w < plan.mPlannedSoFar.size(); ++w) {
            plan.mUnplannedInfo.removeChangeFor(plan.mPlannedSoFar.get(w));
        }
        plan.mPlannedSoFar.clear();
        return true;
    }

    private void dispatch(@NonNull TransitionState state) {
        ProtoLog.v(WM_SHELL_MIXPATCHER, "dispatch| #%d %s", state.mDebugId, state.mTransition);
        if (mTransferring != null) {
            throw new IllegalStateException("Can't dispatch while a transfer is ongoing");
        }
        final AnimationPlan plan = new AnimationPlan(mMainExecutor,
                token -> findAnimatingContainer(token) >= 0);
        plan.mUnplannedInfo = AnimationPlan.copyInfoWithoutChanges(state.mBaseInfo);
        plan.mUnplannedInfo.getChanges().addAll(state.mBaseInfo.getChanges());

        // TODO: housekeeping / observer stuff

        // Some hard-coded always-first planners for universal situations.
        addToPlan(plan, state.mBaseInfo, mKeyguardPlanner, state.mTransition, state.mStartT);
        final boolean isSleep = addToPlan(plan, state.mBaseInfo, mSleepPlanner,
                state.mTransition, state.mStartT);
        addToPlan(plan, state.mBaseInfo, mInvisiblesPlanner, state.mTransition, state.mStartT);

        // If there's any explicit interest, give those the first shot at planning changes.
        if (state.mInterest != null) {
            for (int i = 0; i < state.mInterest.size(); ++i) {
                addToPlan(plan, state.mBaseInfo, state.mInterest.get(i), state.mTransition,
                        state.mStartT);
            }
        }
        // Next, let everything else make plans for the rest.
        // backwards so that things registered "later" are higher priority
        for (int i = mPlanners.size() - 1; i >= 0; --i) {
            final ITransitionPlanner planner = mPlanners.get(i);
            if (state.mInterest != null && state.mInterest.contains(planner)) {
                continue;
            }
            addToPlan(plan, state.mBaseInfo, planner, state.mTransition, state.mStartT);
        }
        if (!plan.mUnplannedInfo.getChanges().isEmpty()) {
            throw new IllegalStateException("Transition wasn't fully planned. This should"
                    + " be impossible since the default-planner should consume everything: "
                    + plan.mUnplannedInfo);
        }
        state.mPlan = plan;

        // Now, update our records of which animation each container is part-of.
        for (int i = 0; i < plan.mAnims.size(); ++i) {
            final ITransitionAnimation nextAnim = plan.mAnims.keyAt(i);
            state.mAnimations.add(nextAnim);
            for (int c = 0; c < plan.mAnims.valueAt(i).getChanges().size(); ++c) {
                final WindowContainerToken container =
                        plan.mAnims.valueAt(i).getChanges().get(c).getContainer();
                int existing = findAnimatingContainer(container);
                if (existing < 0) {
                    mContainers.add(new AnimatingContainer(container, nextAnim));
                    continue;
                }
                final ITransitionAnimation priorAnim = mContainers.get(existing).mAnimation;
                plan.mToDetach.computeIfAbsent(priorAnim, k -> new ArrayList<>()).add(container);
                mContainers.get(existing).mAnimation = nextAnim;
            }
        }

        if (plan.hasPendingTransfer()) {
            startTransfer(state);
        } else {
            startAnimations(state);
        }
    }

    /**
     * Starts detaching any currently-animating containers from their animations so that they can
     * be transferred to the new animation.
     *
     * Ideally, this is a synchronous operation; however, in some cases it may take time (eg.
     * taking surfaces out of a view-hierarchy). If all detaches are synchronous, then this will
     * start animating immediately. Otherwise, the transition will be moved to the
     * {@link #mTransferring} state until all the detachments finish.
     */
    private void startTransfer(@NonNull TransitionState state) {
        if (!state.mPlan.hasPendingTransfer()) {
            throw new IllegalStateException("Can't start non-existent transfer");
        }
        if (!state.mPlan.mPendingDetachments.isEmpty()) {
            ProtoLog.v(WM_SHELL_MIXPATCHER, "transferring| ongoing %d to #%d",
                    state.mPlan.mPendingDetachments.size(), state.mDebugId);
        }
        ArrayMap<ITransitionAnimation, ArrayList<WindowContainerToken>> detaches =
                state.mPlan.mToDetach;
        if (!detaches.isEmpty()) {
            for (int i = 0; i < detaches.size(); ++i) {
                final ArrayList<WindowContainerToken> containers = detaches.valueAt(i);
                ProtoLog.v(WM_SHELL_MIXPATCHER, "transferring| %s from %s to #%d", containers,
                        detaches.keyAt(i).getDebugName(), state.mDebugId);
                state.mPlan.mPendingDetachments.addAll(containers);
                final DetachResult result = detaches.keyAt(i).detach(containers, state.mStartT);
                if (result.isDone()) {
                    state.mPlan.detachPending(containers, result.resultNow());
                } else {
                    result.whenCompleteAsync(
                            (states, err) -> state.mPlan.detachPending(containers, states),
                            mMainExecutor);
                }
            }
            detaches.clear();
        }
        if (state.mPlan.hasPendingTransfer()) {
            mTransferring = state;
            state.mPlan.whenAllDetached(() -> {
                mTransferring = null;
                ProtoLog.v(WM_SHELL_MIXPATCHER, "transferring| finished async #%d", state.mDebugId);
                startAnimations(state);
            });
            return;
        }
        ProtoLog.v(WM_SHELL_MIXPATCHER, "transferring| finished, sync! #%d", state.mDebugId);
        startAnimations(state);
    }

    private void startAnimations(@NonNull TransitionState transit) {
        final AnimationPlan start = transit.mPlan;
        transit.mPlan = null;
        mPlaying.add(transit);
        // TODO: setup start state in startTransaction.
        transit.mStartT.apply();
        for (int i = 0; i < start.mAnims.size(); ++i) {
            final TransitionInfo subInfo = start.mAnims.valueAt(i);
            final ArrayList<WindowAnimationState> startStates = new ArrayList<>();
            for (int c = 0; c < subInfo.getChanges().size(); ++c) {
                final TransitionInfo.Change chg = subInfo.getChanges().get(c);
                // Get or initialize start state
                WindowAnimationState state = start.mTransferStates.get(chg.getContainer());
                if (state == null) {
                    // TODO: initialize start state
                    state = new WindowAnimationState();
                }
                startStates.add(state);
                // TODO: leash management
            }
            // Record surfaces that need to be released later.
            Transitions.recordReleaseSurfaces(transit.mCleanupSurfaces, subInfo.getChanges());

            final ITransitionAnimation anim = start.mAnims.keyAt(i);
            ProtoLog.v(WM_SHELL_MIXPATCHER, "animating #%d| start %s", transit.mDebugId,
                    anim.getDebugName());

            anim.setAnimScaleSetting(mAnimScaleSetting);
            anim.start(start.mAnims.valueAt(i), startStates,
                    (finishT) -> mMainExecutor.execute(() -> onFinish(anim, finishT)));
        }
        if (start.mAnims.isEmpty()) {
            // There weren't any animations, so there will be no onFinish callback. This means
            // we need to explicitly jump to finish here
            mPlaying.removeLast();
            mFinished.add(transit);
            ProtoLog.v(WM_SHELL_MIXPATCHER, "animating #%d| No animations, finish now",
                    transit.mDebugId);
            checkAllFinished();
        }
        if (!mDispatchQueue.isEmpty()) {
            final TransitionState next = mDispatchQueue.removeFirst();
            dispatch(next);
        }
    }

    @ShellMainThread
    private void onFinish(@NonNull ITransitionAnimation animation,
            @Nullable SurfaceControl.Transaction finishT) {
        mMainExecutor.assertCurrentThread();
        int transitIdx = -1;
        for (int i = 0; i < mPlaying.size(); ++i) {
            if (mPlaying.get(i).mAnimations.remove(animation)) {
                transitIdx = i;
                break;
            }
        }
        if (transitIdx < 0) {
            throw new IllegalStateException("All animations must be associated with a transition");
        }
        final TransitionState transit = mPlaying.get(transitIdx);
        ProtoLog.v(WM_SHELL_MIXPATCHER, "animating #%d| finished %s", transit.mDebugId,
                animation.getDebugName());
        // stop tracking containers in this animation
        mContainers.removeIf(c -> c.mAnimation == animation);
        if (finishT != null) {
            transit.mFinishT.merge(finishT);
        }
        // Don't finish transition if it still has playing animations
        if (!transit.mAnimations.isEmpty()) return;
        ProtoLog.v(WM_SHELL_MIXPATCHER, "finished| #%d", transit.mDebugId);
        mPlaying.remove(transitIdx);
        mFinished.add(transit);
        checkAllFinished();
    }

    private void checkAllFinished() {
        if (!mPlaying.isEmpty() || mTransferring != null || !mDispatchQueue.isEmpty()) {
            // Still ongoing animations
            return;
        }
        if (mFinished.isEmpty()) {
            throw new IllegalStateException("Called allFinished with no finished transitions");
        }
        if (mFinished.size() != mReadyOrder.size()) {
            throw new IllegalStateException("A transition got lost somewhere");
        }
        ProtoLog.v(WM_SHELL_MIXPATCHER, "finished| ALL finished");
        // Merge all associated transactions together (in order)
        SurfaceControl.Transaction fullFinish = mReadyOrder.get(0).mFinishT;
        for (int i = 1; i < mReadyOrder.size(); ++i) {
            fullFinish.merge(mReadyOrder.get(i).mFinishT);
        }
        fullFinish.apply();
        // Now perform all the finish callbacks (starting with the playing one and then all the
        // transitions merged into it).
        for (int i = 0; i < mReadyOrder.size(); ++i) {
            final TransitionState state = mReadyOrder.get(i);
            if (state.mStartT != null) {
                // Clear (just in case) to remove any references.
                state.mStartT.clear();
            }
            for (int s = 0; s < state.mCleanupSurfaces.size(); ++s) {
                state.mCleanupSurfaces.get(s).release();
            }
            ProtoLog.v(WM_SHELL_MIXPATCHER, "finished| notify WM #%d %s", state.mDebugId,
                    state.mTransition);
            mOrganizer.finishTransition(state.mTransition, null /* wct */);
        }
        mReadyOrder.clear();
        mFinished.clear();
        executeIdle();
    }

    /**
     * Runs the given {@code runnable} when the last active transition has finished, or immediately
     * if there are currently no active transitions.
     *
     * <p>This method should be called on the Shell main-thread, where the given {@code runnable}
     * will be executed when the last active transition is finished.
     */
    public void runOnIdle(Runnable runnable) {
        mMainExecutor.assertCurrentThread();
        if (mReadyOrder.isEmpty() && mPending.isEmpty()) {
            runnable.run();
        } else {
            mRunWhenIdleQueue.add(runnable);
        }
    }

    private void executeIdle() {
        ProtoLog.v(WM_SHELL_MIXPATCHER, "Transition system is idle now.");
        for (int i = 0; i < mRunWhenIdleQueue.size(); i++) {
            mRunWhenIdleQueue.get(i).run();
        }
        mRunWhenIdleQueue.clear();
    }

    private class InvisiblesPlanner implements ITransitionPlanner {
        @Nullable
        @Override
        public void plan(@NonNull AnimationPlan plan, @NonNull TransitionInfo fullInfo,
                @NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction) {
            ITransitionAnimation nullAnim = null;
            final int changeSize = info.getChanges().size();
            for (int i = changeSize - 1; i >= 0; --i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                //  Change is hidden behind starting window:
                if ((change.hasAllFlags(FLAG_IS_BEHIND_STARTING_WINDOW | FLAG_NO_ANIMATION)
                        || change.hasAllFlags(
                        FLAG_IS_BEHIND_STARTING_WINDOW | FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY))
                        // Change is occluded with no animation
                        || change.hasAllFlags(TransitionInfo.FLAGS_IS_OCCLUDED_NO_ANIMATION)) {
                    // Extract the change because it should be invisible in the animation.
                    if (nullAnim == null) {
                        nullAnim = new NoAnimation(mMainExecutor);
                    }
                    plan.setAnimation(change.getContainer(), nullAnim);
                }
            }
            if (nullAnim != null) {
                ProtoLog.v(WM_SHELL_MIXPATCHER,
                        "Found non-visible containers in transition #%d", info.getDebugId());
            }
        }

        @NonNull
        @Override
        public String getDebugName() {
            return "Invisibles";
        }
    }

    private class SleepPlanner implements ITransitionPlanner {
        @Override
        public void plan(@NonNull AnimationPlan plan, @NonNull TransitionInfo fullInfo,
                @NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction) {
            if (!(info.getType() == TRANSIT_SLEEP
                    || (info.getFlags() & TransitionInfo.FLAG_SYNC) != 0)) {
                return;
            }
            ITransitionAnimation sleepAnim = new NoAnimation(mMainExecutor);
            final TransitionInfo.Change sleepChg = new TransitionInfo.Change(mSleepProxy,
                    new SurfaceControl.Builder().setName("SleepProxy").build());
            info.addChange(sleepChg);
            plan.setAnimation(sleepChg.getContainer(), sleepAnim);
            ProtoLog.v(WM_SHELL_MIXPATCHER, "Build sleep proxy in transition #%d",
                    info.getDebugId());
        }

        @NonNull
        @Override
        public String getDebugName() {
            return "Sleep";
        }
    }
}
