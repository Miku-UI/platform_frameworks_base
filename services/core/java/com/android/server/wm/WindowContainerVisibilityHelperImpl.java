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

package com.android.server.wm;


import static android.app.ActivityManager.LOCK_TASK_MODE_LOCKED;
import static android.app.ActivityManager.LOCK_TASK_MODE_PINNED;

import static com.android.server.wm.TaskFragment.TASK_FRAGMENT_VISIBILITY_INVISIBLE;
import static com.android.server.wm.TaskFragment.TASK_FRAGMENT_VISIBILITY_VISIBLE;
import static com.android.server.wm.TaskFragment.TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Rect;
import android.util.ArraySet;

import com.android.window.flags.Flags;

import java.util.function.Predicate;

/**
 * Implementation of {@link WindowContainerVisibilityHelper}.
 *
 * @see ActivityTaskManagerService#mVisibilityHelper
 */
final class WindowContainerVisibilityHelperImpl implements WindowContainerVisibilityHelper {

    @NonNull
    private final ActivityTaskManagerService mService;
    @NonNull
    private final OpaqueContainerHelper mOpaqueContainerHelper = new OpaqueContainerHelper();

    WindowContainerVisibilityHelperImpl(@NonNull ActivityTaskManagerService service) {
        mService = service;
    }

    @Override
    @TaskFragment.TaskFragmentVisibility
    public int getTaskFragmentVisibility(@NonNull TaskFragment current,
            @Nullable ActivityRecord starting) {
        if (!current.isAttached() || current.isForceHidden()) {
            return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
        }

        if (Flags.enablePresentationStopsTopTaskBugfix() && current.mWmService
                .mPresentationController.shouldOccludeActivities(current.getDisplayId())) {
            return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
        }

        if (isTopActivityLaunchedBehind(current)) {
            return TASK_FRAGMENT_VISIBILITY_VISIBLE;
        }
        final WindowContainer<?> parent = current.getParent();
        final Task thisTask = current.asTask();
        if (thisTask != null && parent.asTask() == null
                && current.mTransitionController.isTransientVisible(thisTask)) {
            // Keep transient-hide root tasks visible. Non-root tasks still follow standard rule.
            return TASK_FRAGMENT_VISIBILITY_VISIBLE;
        }

        if (thisTask != null && !isPermittedInLockTask(thisTask)) {
            return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
        }

        boolean gotTranslucentFullscreen = false;
        boolean shouldBeVisible = true;

        // This TaskFragment is only considered visible if all its parent TaskFragments are
        // considered visible, so check the visibility of all ancestor TaskFragment first.
        if (parent.asTaskFragment() != null) {
            final int parentVisibility = getTaskFragmentVisibility(
                    parent.asTaskFragment(), starting);
            if (parentVisibility == TASK_FRAGMENT_VISIBILITY_INVISIBLE) {
                // Can't be visible if parent isn't visible
                return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
            } else if (parentVisibility == TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT) {
                // Parent is behind a translucent container so the highest visibility this container
                // can get is that.
                gotTranslucentFullscreen = true;
            }
        }

        final boolean isForceLeafTaskNonOccluding = isForceNonOccludingByRootTask(current);
        AdjacentVisibilityHelper adjacentVisibilityHelper = null;
        for (int i = parent.getChildCount() - 1; i >= 0; --i) {
            final WindowContainer<?> other = parent.getChildAt(i);
            if (other.asTask() != null && other.asTask().isVisibilityBarrier()) {
                // Visibility barrier and siblings below it are all invisible.
                return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
            }

            final boolean containsCanBeVisibleActivity = containsCanBeVisibleActivity(other);
            if (other == current) {
                if (adjacentVisibilityHelper != null
                        && !adjacentVisibilityHelper.isUnprocessedAdjacentTaskFragment(
                        current)) {
                    if (!adjacentVisibilityHelper.isBehindTranslucentTaskFragment(current)) {
                        return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
                    } else {
                        gotTranslucentFullscreen = true;
                    }
                }
                // Should be visible if there is no other fragment occluding it, unless it doesn't
                // have any running activities, not starting one and not home stack.
                shouldBeVisible = containsCanBeVisibleActivity
                        || (starting != null && starting.isDescendantOf(current))
                        || (current.isActivityTypeHome() && !current.isEmbedded());
                break;
            }

            if (!containsCanBeVisibleActivity) {
                continue;
            }

            if (isForceLeafTaskNonOccluding && other.asTask() != null
                    && !other.asTask().isForceOpaque()) {
                // Leaf Task is forced to be non-occluding unless it is force opaque.
                continue;
            }

            // Must fill the parent to affect visibility.
            boolean affectsSiblingVisibility = other.fillsParentBounds();
            // It also must have filling content itself, to prevent empty or only partially
            // occluding containers from affecting visibility.
            affectsSiblingVisibility &= other.hasFillingContent();
            if (affectsSiblingVisibility) {
                // This task fragment is fully covered by |other|.
                if (isTranslucent(other, starting)) {
                    // Can be visible behind a translucent TaskFragment.
                    gotTranslucentFullscreen = true;
                    continue;
                }
                return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
            }

            final TaskFragment otherTaskFrag = other.asTaskFragment();
            if (otherTaskFrag != null) {
                // For adjacent TaskFragments, we have assumptions that:
                // 1. A set of adjacent TaskFragments always cover the entire Task window, so that
                // if this TaskFragment is behind a set of opaque TaskFragments, then this
                // TaskFragment is invisible.
                // 2. Adjacent TaskFragments do not overlap, so that if this TaskFragment is behind
                // any translucent TaskFragment in the adjacent set, then this TaskFragment is
                // visible behind translucent.
                if (otherTaskFrag.hasAdjacentTaskFragment()
                        && (adjacentVisibilityHelper == null
                        || adjacentVisibilityHelper.isAllAdjacentTaskFragmentProcessed())) {
                    // Same as above. The TaskFragment must have filling content itself,
                    // otherwise it cannot affect the visibility.
                    adjacentVisibilityHelper = new AdjacentVisibilityHelper(
                            otherTaskFrag,
                            t -> t.hasFillingContent() && !isTranslucent(t, starting));
                }

                if (adjacentVisibilityHelper != null) {
                    adjacentVisibilityHelper.process(otherTaskFrag);
                    if (adjacentVisibilityHelper.isAllAdjacentTaskFragmentProcessed()) {
                        if (adjacentVisibilityHelper.occludesParent()) {
                            // Can not be visible behind adjacent TaskFragments.
                            return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
                        }
                        // Can be visible behind a translucent adjacent TaskFragments.
                        gotTranslucentFullscreen = true;
                    }
                }
            }
        }

        if (!shouldBeVisible) {
            return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
        }

        // Lastly - check if there is a translucent fullscreen TaskFragment on top.
        return gotTranslucentFullscreen
                ? TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT
                : TASK_FRAGMENT_VISIBILITY_VISIBLE;
    }

    @Override
    public boolean shouldActivityBeVisible(@NonNull ActivityRecord current,
            boolean ignoringKeyguard) {
        final Task task = current.getTask();
        if (task == null) {
            return false;
        }

        final boolean behindOccludedContainer = isActivityBehindOccluded(current);
        return current.updateAndCheckVisibility(behindOccludedContainer, ignoringKeyguard);
    }

    @Override
    public boolean hasFillingContent(@NonNull WindowContainer current) {
        final int childCount = current.getChildCount();
        if (childCount == 0) {
            return false;
        }

        AdjacentVisibilityHelper adjacentVisibilityHelper = null;
        for (int i = childCount - 1; i >= 0; --i) {
            final WindowContainer<?> child = current.getChildAt(i);
            if (child.asTask() != null && child.asTask().isVisibilityBarrier()) {
                // Siblings behind the visibility barrier cannot be made visible, nor filling
                // parent.
                return false;
            }
            if (child.fillsParentBounds() && child.hasFillingContent()) {
                // At least one child fills this container and has content filling itself.
                return true;
            }

            final TaskFragment tf = child.asTaskFragment();
            if (tf != null) {
                if (tf.hasAdjacentTaskFragment() && adjacentVisibilityHelper == null) {
                    adjacentVisibilityHelper = new AdjacentVisibilityHelper(
                            tf, TaskFragment::hasFillingContent);
                }
                if (adjacentVisibilityHelper != null) {
                    adjacentVisibilityHelper.process(tf);
                    if (adjacentVisibilityHelper.isAllAdjacentTaskFragmentProcessed()) {
                        if (adjacentVisibilityHelper.occludesParent()) {
                            return true;
                        } else {
                            if (Flags.partialTranslucentActivityEmbedding() && i == 0
                                    && current.asTask() != null && current.asTask().isLeafTask()
                                    && adjacentVisibilityHelper.containsOccludingTaskFragment()) {
                                // For adjacent TFs in leaf Task (Activity Embedding), if any of the
                                // adjacent TFs is occluding while there is no other sibling below,
                                // treating them as occluding.
                                // We do this because a partial translucent Activity Embedding task
                                // in general provide a bad UX.
                                return true;
                            }
                            adjacentVisibilityHelper = null;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean isOpaque(@NonNull WindowContainer<?> current,
            @Nullable ActivityRecord starting, boolean ignoringKeyguard,
            boolean ignoringInvisibleActivity, boolean ignoringFinishing) {
        return mOpaqueContainerHelper.isOpaque(current, starting, ignoringKeyguard,
                ignoringInvisibleActivity, ignoringFinishing);
    }

    /**
     * Whether this or any of its activities can be made visible without changing their z-order.
     */
    private static boolean containsCanBeVisibleActivity(@NonNull WindowContainer wc) {
        if (wc.asTaskFragment() != null) {
            if (wc.asTaskFragment().isForceHidden()) {
                // Activity in hidden container cannot be made visible.
                return false;
            }
            if (wc.asTask() != null && !wc.asTask().isLeafTask()) {
                for (int i = wc.getChildCount() - 1; i >= 0; --i) {
                    final WindowContainer<?> child = wc.getChildAt(i);
                    if (child.asTask() != null && child.asTask().isVisibilityBarrier()) {
                        // Siblings behind the visibility barrier cannot be made visible.
                        return false;
                    }
                    if (containsCanBeVisibleActivity(child)) {
                        return true;
                    }
                }
                return false;
            }
            return wc.asTaskFragment().topRunningActivity() != null;
        }
        return wc.asActivityRecord() != null && !wc.asActivityRecord().finishing;
    }

    private static boolean isTranslucent(@NonNull WindowContainer wc,
            @Nullable ActivityRecord starting) {
        if (wc.asTaskFragment() != null) {
            return wc.asTaskFragment().isTranslucent(starting);
        } else if (wc.asActivityRecord() != null) {
            return !wc.asActivityRecord().occludesParent();
        }
        return false;
    }

    private static boolean isTopActivityLaunchedBehind(@NonNull TaskFragment current) {
        final ActivityRecord top = current.topRunningActivity();
        return top != null && top.mLaunchTaskBehind;
    }

    /**
     * Checks if a task is allowed to run in the lock task mode.
     *
     * <p>Returns {@code true} if the device is not currently in lock task.
     *
     * <p>A task is permitted if it's a leaf task that is allowed by the lock task admin policy, or
     * if any of its descendant leaf tasks are permitted by the policy.
     *
     * @param task the task to evaluate.
     * @return {@code true} if the task is allowed to run, {@code false} otherwise.
     */
    private boolean isPermittedInLockTask(@NonNull Task task) {
        final int lockTaskState = mService.getLockTaskController().getLockTaskModeState();
        final boolean isInLockTask =
                lockTaskState == LOCK_TASK_MODE_LOCKED || lockTaskState == LOCK_TASK_MODE_PINNED;
        if (!isInLockTask) {
            return true;
        }
        return task.forAllTasks(leafTask ->
                !mService.getLockTaskController().isLockTaskModeViolation(leafTask));
    }

    /** Whether the given activity is behind another occluded window. */
    private boolean isActivityBehindOccluded(@NonNull ActivityRecord current) {
        final TaskFragment tf = current.getTaskFragment();
        if (tf == null || !tf.shouldBeVisible(null /* starting */)) {
            // Its parent is behind occluded.
            return true;
        }
        for (int i = tf.getChildCount() - 1; i >= 0; i--) {
            final WindowContainer<?> child = tf.getChildAt(i);
            if (child == current) {
                return false;
            }
            if (isOpaque(child, null /* starting */, true /* ignoringKeyguard */,
                    false /* ignoringInvisibleActivity */, true /* ignoringFinishing */)) {
                // Check whether there is any opaque siblings above the given activity.
                // Including invisible activities, but not finishing activities.
                return true;
            }
        }
        // Shouldn't reach.
        return true;
    }

    /** Whether all leaf Tasks in the same root Task are forced to be non-occluding. */
    private static boolean isForceNonOccludingByRootTask(@NonNull TaskFragment current) {
        final Task rootTask = current.getRootTask();
        return current != rootTask && rootTask != null && rootTask.isForceLeafTasksNonOccluding();
    }

    /** The helper to calculate whether a container is opaque. */
    private static class OpaqueContainerHelper implements Predicate<ActivityRecord> {
        @Nullable
        private ActivityRecord mStarting;
        private boolean mIgnoringInvisibleActivity;
        private boolean mIgnoringKeyguard;
        private boolean mIgnoringFinishing;

        /** Whether the container is opaque. */
        boolean isOpaque(
                @NonNull WindowContainer<?> container, @Nullable ActivityRecord starting,
                boolean ignoringKeyguard,  boolean ignoringInvisibleActivity,
                boolean ignoringFinishing) {
            mStarting = starting;
            mIgnoringInvisibleActivity = ignoringInvisibleActivity;
            mIgnoringKeyguard = ignoringKeyguard;
            mIgnoringFinishing = ignoringFinishing || ignoringInvisibleActivity;

            final boolean isOpaque = isOpaqueInner(container);
            mStarting = null;
            return isOpaque;
        }

        private boolean isOpaqueInner(@NonNull WindowContainer<?> container) {
            final boolean isActivity = container.asActivityRecord() != null;
            final boolean isLeafTaskFragment = container.asTaskFragment() != null
                    && container.asTaskFragment().isLeafTaskFragment();
            final boolean isForceOpaque = container.asTask() != null
                    && container.asTask().isForceOpaque();
            if (isForceOpaque) {
                return true;
            }
            if (container.asTask() != null && container.asTask().isLeafTask()
                    && isForceNonOccludingByRootTask(container.asTask())) {
                // All leaf Tasks are forced to be non-occluding.
                return false;
            }
            if (isActivity || isLeafTaskFragment) {
                // When it is an activity or leaf task fragment, then opacity is calculated based
                // on itself or its activities.
                return container.getActivity(this,
                        true /* traverseTopToBottom */, null /* boundary */) != null;
            }
            // Otherwise, it's considered opaque if any of its opaque children fill this
            // container, unless the children are adjacent fragments, in which case as long as they
            // are all opaque then |container| is also considered opaque, even if the adjacent
            // task fragment aren't filling.
            AdjacentVisibilityHelper adjacentVisibilityHelper = null;
            for (int i = container.getChildCount() - 1; i >= 0; --i) {
                final WindowContainer<?> child = container.getChildAt(i);
                if (child.asTask() != null && child.asTask().isVisibilityBarrier()) {
                    // Siblings behind the visibility barrier cannot be made visible, nor opaque.
                    break;
                }

                if (child.fillsParent() && isOpaqueInner(child)) {
                    return true;
                }

                final TaskFragment tf = child.asTaskFragment();
                if (tf != null) {
                    if (tf.hasAdjacentTaskFragment() && adjacentVisibilityHelper == null) {
                        adjacentVisibilityHelper =
                                new AdjacentVisibilityHelper(tf, this::isOpaqueInner);
                    }
                    if (adjacentVisibilityHelper != null) {
                        adjacentVisibilityHelper.process(tf);
                        if (adjacentVisibilityHelper.isAllAdjacentTaskFragmentProcessed()) {
                            if (adjacentVisibilityHelper.occludesParent()) {
                                // return early if the adjacent TFs are opaque.
                                return true;
                            } else {
                                if (Flags.partialTranslucentActivityEmbedding() && i == 0
                                        && container.asTask() != null
                                        && container.asTask().isLeafTask()
                                        && adjacentVisibilityHelper
                                        .containsOccludingTaskFragment()) {
                                    // For adjacent TFs in leaf Task (Activity Embedding), if any of
                                    // the adjacent TFs is opaque while there is no other sibling
                                    // below, treating them as opaque.
                                    // We do this because a partial translucent Activity Embedding
                                    // task in general provide a bad UX.
                                    return true;
                                }
                                adjacentVisibilityHelper = null;
                            }
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public boolean test(ActivityRecord r) {
            if (mIgnoringInvisibleActivity && r != mStarting
                    && ((mIgnoringKeyguard && !r.visibleIgnoringKeyguard)
                    || (!mIgnoringKeyguard && !r.isVisible()))) {
                // Ignore invisible activities that are not the currently starting activity
                // (about to be visible).
                return false;
            }
            return r.occludesParent(!mIgnoringFinishing);
        }
    }

    /**
     * The helper class to calculate the visibility of the adjacent TaskFragments.
     * </p>
     * For a complex case as below, the adjacent TaskFragments contain a translucent TaskFragment.
     * In that case, the TaskFragment B should be visible, but Task#1 should not be visible if
     * TaskFragment B occludes the whole area of the translucent TaskFragment C.
     * Task#2
     *    - TaskFragment C (adjacent to A, translucent)
     *    - TaskFragment B
     *    - TaskFragment A (adjacent to C)
     * Task#1
     * </p>
     * The visibility calculation should be done by processing the TaskFragments from top to
     * bottom, by calling {@link #process(TaskFragment)}.
     */
    private static class AdjacentVisibilityHelper {

        @NonNull
        private final ArraySet<TaskFragment> mUnprocessedAdjacentTaskFragments;
        @NonNull
        private final ArraySet<TaskFragment> mTranslucentTaskFragments = new ArraySet<>();
        @NonNull
        private final Predicate<TaskFragment> mOccludingCallback;
        private boolean mHasProcessedOpaque;

        AdjacentVisibilityHelper(@NonNull TaskFragment taskFragment,
                @NonNull Predicate<TaskFragment> occludingCallback) {
            mUnprocessedAdjacentTaskFragments =
                    taskFragment.getAdjacentTaskFragments().getTaskFragments();
            mOccludingCallback = occludingCallback;
        }

        /**
         * Process the given TaskFragment. The TaskFragment should be one of the adjacent
         * TaskFragments or the TaskFragments in between the adjacent TFs.
         *
         * Note: the caller must call this on TaskFragments from top to bottom.
         */
        void process(@NonNull TaskFragment taskFragment) {
            final boolean isAdjacent = mUnprocessedAdjacentTaskFragments.remove(taskFragment);
            if (mOccludingCallback.test(taskFragment)) {
                mHasProcessedOpaque = true;
                // Remove the translucent TaskFragments if it can be fully occluded by this
                // TaskFragment.
                mTranslucentTaskFragments.removeIf(
                        t -> taskFragment.getBounds().contains(t.getBounds()));
            } else {
                if (!isAdjacent) {
                    if (!isBehindTranslucentTaskFragment(taskFragment)) {
                        // A non-adjacent TaskFragment should not be counted if it is not occluded
                        // by other translucent adjacent TaskFragment.
                        return;
                    }
                }
                mTranslucentTaskFragments.add(taskFragment);
            }
        }

        /**
         * Returns {@code true} if the process is done, i.e. the adjacent TaskFragments are all
         * processed.
         */
        boolean isAllAdjacentTaskFragmentProcessed() {
            return mUnprocessedAdjacentTaskFragments.isEmpty();
        }

        /**
         * Returns {@code true} if the given TaskFragment is one of the adjacent TaskFragment
         * that's not yet processed.
         */
        boolean isUnprocessedAdjacentTaskFragment(TaskFragment tf) {
            return mUnprocessedAdjacentTaskFragments.contains(tf);
        }

        /**
         * Returns {@code true} if the adjacent TaskFragments (and the TaskFragments in between)
         * can occlude parent container. Must be called after all adjacent TFs are processed.
         */
        boolean occludesParent() {
            return mTranslucentTaskFragments.isEmpty();
        }

        /**
         * Returns {@code true} if any of the adjacent TaskFragments (and the TaskFragments in
         * between) is occluding. Must be called after all adjacent TFs are processed.
         */
        boolean containsOccludingTaskFragment() {
            return mHasProcessedOpaque;
        }

        /**
         * Return {@code true} if the given TaskFragment is behind any of the translucent
         * TaskFragments
         */
        boolean isBehindTranslucentTaskFragment(@NonNull TaskFragment tf) {
            if (mUnprocessedAdjacentTaskFragments.contains(tf)) {
                return false;
            }

            final Rect bounds = tf.getBounds();
            for (int i = mTranslucentTaskFragments.size() - 1; i >= 0; i--) {
                final TaskFragment taskFragment = mTranslucentTaskFragments.valueAt(i);
                if (bounds.intersect(taskFragment.getBounds())) {
                    return true;
                }
            }
            return false;
        }
    }
}
