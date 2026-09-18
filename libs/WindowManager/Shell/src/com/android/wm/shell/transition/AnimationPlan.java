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

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_MIXPATCHER;

import android.annotation.NonNull;
import android.util.ArrayMap;
import android.util.Slog;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerToken;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.ShellExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Encapsulates the work that will be done to animate a transition as part of the
 * {@link TransitionMixpatcher} system.
 */
public class AnimationPlan {
    static final String TAG = "TransitionMixpatcher";

    private final ShellExecutor mMainExecutor;
    private final Predicate<WindowContainerToken> mAlreadyAnimatingContainers;

    TransitionInfo mUnplannedInfo = null;
    final ArrayMap<ITransitionAnimation, TransitionInfo> mAnims = new ArrayMap<>();

    /** List of containers which need to be detached from existing animations. */
    final ArrayMap<ITransitionAnimation, ArrayList<WindowContainerToken>> mToDetach =
            new ArrayMap<>();

    /**
     * Tracks the start-states for containers that will animate. There should eventually be an entry
     * for each container in {@link #mAnims}.
     */
    final ArrayMap<WindowContainerToken, WindowAnimationState> mTransferStates = new ArrayMap<>();

    /** List of containers which are in the process of being detached. */
    final ArrayList<WindowContainerToken> mPendingDetachments = new ArrayList<>();

    /**
     * If this is null at the time all detachments are finished, it means all detachments
     * were synchronous (and thus the dispatcher will immediately continue to animation).
     */
    private Runnable mOnAsyncAllDetached = null;

    /** Temporary list of changes which have been assigned by the current planner. */
    final ArrayList<WindowContainerToken> mPlannedSoFar = new ArrayList<>();

    AnimationPlan(@NonNull ShellExecutor mainExecutor,
            @NonNull Predicate<WindowContainerToken> animatingContainers) {
        mMainExecutor = mainExecutor;
        mAlreadyAnimatingContainers = animatingContainers;
    }

    /** {@code true} if an async detach is still in progress. */
    boolean hasPendingTransfer() {
        return !mToDetach.isEmpty() || !mPendingDetachments.isEmpty();
    }

    /**
     * Creates a new {@link TransitionInfo} object that matches {@param info} but is initially
     * empty (same roots but no changes). This does NOT make new surfacecontrol handles, so any
     * user of this is only expected to release the root surfaces once (IOW, don't use this
     * outside Mixpatcher).
     */
    @NonNull
    static TransitionInfo copyInfoWithoutChanges(@NonNull TransitionInfo info) {
        final TransitionInfo out = new TransitionInfo(info.getType(), 0);
        out.setTrack(info.getTrack());
        out.setDebugId(info.getDebugId());
        out.setFlags(info.getFlags());
        for (int i = 0; i < info.getRootCount(); ++i) {
            out.addRoot(info.getRoot(i));
        }
        return out;
    }

    /**
     * Copies the change (reference) for {@param container} from source {@param source} to
     * {@param dest} while maintaining the relative order between containers.
     *
     * This is used to take a subset of containers from {@param source}, so there shouldn't ever be
     * a container in {@param dest} which isn't in {@param source} and its expected that any
     * containers already in {@param dest} are already in the same relative order as in
     * {@param source}.
     */
    private static void copyChange(@NonNull WindowContainerToken container,
            @NonNull TransitionInfo source, @NonNull TransitionInfo dest) {
        int destIdx = 0;
        for (int f = 0; f < source.getChanges().size(); ++f) {
            final TransitionInfo.Change chg = source.getChanges().get(f);
            final boolean found = chg.getContainer().equals(container);
            for (int d = destIdx; d < dest.getChanges().size(); ++d) {
                if (dest.getChanges().get(d).getContainer().equals(chg.getContainer())) {
                    if (found) {
                        Slog.wtf(TAG, "Trying to copy a change which already exists in"
                                + " destination: " + container);
                        return;
                    }
                    destIdx = d + 1;
                    break;
                }
            }
            if (found) {
                dest.getChanges().add(destIdx, chg);
                return;
            }
        }
        throw new IllegalArgumentException("container change doesn't exist in source");
    }

    /**
     * Set the {@param animation} that should be used to animate {@param container};
     */
    public void setAnimation(@NonNull WindowContainerToken container,
            @NonNull ITransitionAnimation animation) {
        for (int i = 0; i < mAnims.size(); ++i) {
            if (mAnims.valueAt(i).getChange(container) != null) {
                throw new IllegalArgumentException("Only one animation allowed per container.");
            }
        }
        ProtoLog.v(WM_SHELL_MIXPATCHER, "planning| set anim for %s to %s", container,
                animation.getDebugName());
        mPlannedSoFar.add(container);
        copyChange(container, mUnplannedInfo,
                mAnims.computeIfAbsent(animation, a -> copyInfoWithoutChanges(mUnplannedInfo)));
    }

    /**
     * Query whether a container is already considered "detached". This means an attempt to detach
     * it again will do nothing. This can be used by a {@link ITransitionPlanner} implementation
     * to avoid doing any expensive work or to avoid touching the related surfaces.
     */
    public boolean isDetached(@NonNull WindowContainerToken container) {
        return mTransferStates.containsKey(container)
                || mPendingDetachments.contains(container)
                // If already animating, then the animator will detach
                || mAlreadyAnimatingContainers.test(container);
    }

    /**
     * Call this to detach a container from its static situation and prepare it for animation
     * by providing a start state. A container can only be detached once for a transition. If
     * nothing explicitly detaches the container, then the start-state will be calculated based
     * on the transition info.
     *
     * Since Animators are responsible for detaching their constituents, any currently-animating
     * container is considered detached.
     *
     * @return {@code false} if the container is/will-be detached by something else. This is the
     *         same result {@link #isDetached} would produce.
     */
    public boolean detach(@NonNull WindowContainerToken container,
            @NonNull WindowAnimationState state) {
        if (isDetached(container)) return false;
        mTransferStates.put(container, state);
        return true;
    }

    /**
     * Async version of {@link #detach}. Use this if detaching will take time (eg. synchronized
     * removal from view hierarchy).
     *
     * @deprecated Prefer a synchronous detach if possible.
     *
     * @param result A promise to finish detaching the container at some point.
     *
     * @return {@code false} if the container is/will-be detached by something else. This is the
     *         same result {@link #isDetached} would produce. If {@code false}, then {@param result}
     *         is ignored.
     */
    @Deprecated
    public boolean detachAsync(@NonNull WindowContainerToken container,
            @NonNull DetachResult result) {
        if (isDetached(container)) return false;
        mPendingDetachments.add(container);
        final ArrayList<WindowContainerToken> containers = new ArrayList<>(1);
        containers.add(container);
        result.whenCompleteAsync((state, err) -> detachPending(containers, state), mMainExecutor);
        return true;
    }

    void whenAllDetached(@NonNull Runnable onAllDetached) {
        if (mOnAsyncAllDetached != null) {
            throw new IllegalStateException("Double-registering allDetached callback.");
        }
        mOnAsyncAllDetached = onAllDetached;
    }

    /**
     * Finish detaching a set of containers which were previously promised to be detached at a
     * later time (either via {@link #detachAsync} or by returning {@link DetachResult#promise})
     * from {@link ITransitionAnimation#detach}.
     *
     * {@param containers} and {@param state} are expected to correspond 1-to-1.
     */
    void detachPending(@NonNull List<WindowContainerToken> containers,
            @NonNull List<WindowAnimationState> state) {
        if (containers.size() != state.size()) {
            throw new IllegalArgumentException("Detached " + containers.size()
                    + " containers but only got " + state.size()
                    + " states back.");
        }
        ProtoLog.v(WM_SHELL_MIXPATCHER, "detach| %s of %d pending", containers,
                mPendingDetachments.size());
        for (int c = 0; c < state.size(); ++c) {
            final WindowContainerToken container = containers.get(c);
            mTransferStates.put(container, state.get(c));
            final boolean removed = mPendingDetachments.remove(container);
            if (!removed) {
                throw new IllegalStateException("Possible double-detach of " + container);
            }
        }
        if (mOnAsyncAllDetached != null && mPendingDetachments.isEmpty()) {
            final Runnable allDetached = mOnAsyncAllDetached;
            mOnAsyncAllDetached = null;
            allDetached.run();
        }
    }
}
