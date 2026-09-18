/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.shared;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_DREAM;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.RemoteAnimationTarget.MODE_CHANGING;
import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static android.view.WindowManager.LayoutParams.INVALID_WINDOW_TYPE;
import static android.view.WindowManager.LayoutParams.LAST_SYSTEM_WINDOW;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_KEYGUARD_GOING_AWAY;
import static android.view.WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.FLAG_CHANGED_INTERACTIVE;
import static android.window.TransitionInfo.FLAG_FIRST_CUSTOM;
import static android.window.TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY;
import static android.window.TransitionInfo.FLAG_IS_DISPLAY;
import static android.window.TransitionInfo.FLAG_IS_WALLPAPER;
import static android.window.TransitionInfo.FLAG_MOVED_TO_TOP;
import static android.window.TransitionInfo.FLAG_NO_ANIMATION;
import static android.window.TransitionInfo.FLAG_SHOW_WALLPAPER;
import static android.window.TransitionInfo.FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.util.SparseBooleanArray;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;

import java.util.List;
import java.util.function.Predicate;

/** Various utility functions for transitions. */
public class TransitionUtil {
    /** Flag applied to a transition change to identify it as a divider bar for animation. */
    public static final int FLAG_IS_DIVIDER_BAR = FLAG_FIRST_CUSTOM;
    public static final int FLAG_IS_DIM_LAYER = FLAG_FIRST_CUSTOM << 1;

    /** Flag applied to a transition change to identify it as a desktop wallpaper activity. */
    public static final int FLAG_IS_DESKTOP_WALLPAPER_ACTIVITY = FLAG_FIRST_CUSTOM << 2;

    /**
     * Applied to a {@link RemoteAnimationTarget} to identify dim layers for animation in Launcher.
     */
    public static final int TYPE_SPLIT_SCREEN_DIM_LAYER = LAST_SYSTEM_WINDOW + 1;

    /** @return true if the transition was triggered by opening something vs closing something */
    public static boolean isOpeningType(@WindowManager.TransitionType int type) {
        return type == TRANSIT_OPEN
                || type == TRANSIT_TO_FRONT
                || type == TRANSIT_KEYGUARD_GOING_AWAY
                || type == TRANSIT_PREPARE_BACK_NAVIGATION;
    }

    /** @return true if the transition was triggered by closing something vs opening something */
    public static boolean isClosingType(@WindowManager.TransitionType int type) {
        return type == TRANSIT_CLOSE || type == TRANSIT_TO_BACK;
    }

    /** Returns {@code true} if the transition is opening or closing mode. */
    public static boolean isOpenOrCloseMode(@TransitionInfo.TransitionMode int mode) {
        return isOpeningMode(mode) || isClosingMode(mode);
    }

    /** Returns {@code true} if the transition is opening mode. */
    public static boolean isOpeningMode(@TransitionInfo.TransitionMode int mode) {
        return mode == TRANSIT_OPEN || mode == TRANSIT_TO_FRONT;
    }

    /** Returns {@code true} if the transition is closing mode. */
    public static boolean isClosingMode(@TransitionInfo.TransitionMode int mode) {
        return mode == TRANSIT_CLOSE || mode == TRANSIT_TO_BACK;
    }

    /** Returns {@code true} if the transition has a display change. */
    public static boolean hasDisplayChange(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getMode() == TRANSIT_CHANGE && change.hasFlags(FLAG_IS_DISPLAY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the transition has a display change that is not just an order-change.
     */
    public static boolean hasStationaryOnlyDisplayChange(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getMode() == TRANSIT_CHANGE
                    && change.hasFlags(FLAG_IS_DISPLAY)
                    && !isStationary(change)) {
                return true;
            }
        }
        return false;
    }

    /** Returns `true` if `change` is a wallpaper. */
    public static boolean isWallpaper(TransitionInfo.Change change) {
        return (change.getTaskInfo() == null)
                && change.hasFlags(FLAG_IS_WALLPAPER)
                && !change.hasFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY);
    }

    /** Returns `true` if `change` is not an app window or wallpaper. */
    public static boolean isNonApp(TransitionInfo.Change change) {
        return (change.getTaskInfo() == null)
                && !change.hasFlags(FLAG_IS_WALLPAPER)
                && !change.hasFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY);
    }

    /** Returns `true` if `change` is the divider. */
    public static boolean isDividerBar(TransitionInfo.Change change) {
        return isNonApp(change) && change.hasFlags(FLAG_IS_DIVIDER_BAR);
    }

    /** Returns `true` if `change` is an app's dim layer. */
    public static boolean isDimLayer(TransitionInfo.Change change) {
        return isNonApp(change) && change.hasFlags(FLAG_IS_DIM_LAYER);
    }

    /** Returns `true` if `change` is only re-ordering. */
    public static boolean isOrderOnly(TransitionInfo.Change change) {
        return isStationary(change) && (change.getFlags() & FLAG_MOVED_TO_TOP) != 0;
    }

    /**
     * Returns `true` if a `change` is stationary. Stationary changes are those that do not change
     * task's visual representation and don't need an animation, but do not prevent them either.
     */
    public static boolean isStationary(TransitionInfo.Change change) {
        return change.getMode() == TRANSIT_CHANGE
                && (change.getFlags() & (FLAG_MOVED_TO_TOP | FLAG_CHANGED_INTERACTIVE)) != 0
                && change.getStartAbsBounds().equals(change.getEndAbsBounds())
                && (change.getLastParent() == null
                || change.getLastParent().equals(change.getParent()))
                && (change.getStartRotation() == change.getEndRotation());
    }

    /** Returns true if all changes in this transition are stationary. */
    public static boolean isAllStationary(TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            if (!isStationary(info.getChanges().get(i))) return false;
        }
        return true;
    }

    /**
     * Look through a transition and see if all non-closing changes are no-animation. If so, no
     * animation should play.
     */
    public static boolean isAllNoAnimation(TransitionInfo info) {
        if (isClosingType(info.getType())) {
            // no-animation is only relevant for launching (open) activities.
            return false;
        }
        boolean hasNoAnimation = false;
        final int changeSize = info.getChanges().size();
        for (int i = changeSize - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (isClosingType(change.getMode())) {
                // ignore closing apps since they are a side-effect of the transition and don't
                // animate.
                continue;
            }
            if (change.hasFlags(FLAG_NO_ANIMATION)) {
                hasNoAnimation = true;
            } else if (!isStationary(change) && !change.hasFlags(
                    TransitionInfo.FLAG_IS_OCCLUDED)) {
                // Ignore the non-stationary or occluded changes since they shouldn't be visible
                // during animation. For anything else, we need to animate if at-least one relevant
                // participant *is* animated.
                return false;
            }
        }
        return hasNoAnimation;
    }

    /**
     * Checks if the transition contains a change transitioning to the Home task on the
     * specific display.
     *
     * <p>Note: In Shell, a "Go Home" transition from split-screen is often handled within
     * {@code RecentsMixedTransition}. This helper identifies those
     * transitions so split-screen can coordinate its state (like suppressing local dimming) during
     * the animation to Home.
     */
    public static boolean isHomeTransitionEndingOnDisplay(@Nullable TransitionInfo info,
            int displayId) {
        if (info == null || info.getChanges() == null) {
            return false;
        }
        for (int i = 0; i < info.getChanges().size(); ++i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getEndDisplayId() == displayId && change.getTaskInfo() != null
                    && change.getTaskInfo().getActivityType() == ACTIVITY_TYPE_HOME) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if {@link TransitionInfo} contains a change related to Dream.
     */
    public static boolean isDreamTransition(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getTaskInfo() != null
                    && change.getTaskInfo().topActivityType == ACTIVITY_TYPE_DREAM) {
                return true;
            }
        }

        return false;
    }

    /**
     * Filter that selects leaf-tasks only.
     */
    public static class LeafTaskFilter implements Predicate<TransitionInfo.Change> {
        private final SparseBooleanArray mTaskTargetsWithChildren = new SparseBooleanArray();

        /**
         * Constructs a task filter for leaf task changes in {@code info}.
         */
        public LeafTaskFilter(TransitionInfo info) {
            final List<TransitionInfo.Change> changes = info.getChanges();
            final int n = changes.size();

            // The special case for cyclic references should never happen in practice, but
            // does happen in tests sometimes.
            for (int i = 0; i < n; i++) {
                final ActivityManager.RunningTaskInfo taskInfo = changes.get(i).getTaskInfo();
                if (taskInfo != null && taskInfo.hasParentTask()
                        && taskInfo.parentTaskId != taskInfo.taskId) {
                    mTaskTargetsWithChildren.put(taskInfo.parentTaskId, true);
                }
            }
        }

        @Override
        public boolean test(TransitionInfo.Change change) {
            // If it has children, it's not a leaf.
            return change.getTaskInfo() != null
                    && !mTaskTargetsWithChildren.get(change.getTaskInfo().taskId);
        }
    }


    private static int newModeToLegacyMode(int newMode) {
        switch (newMode) {
            case WindowManager.TRANSIT_OPEN:
            case WindowManager.TRANSIT_TO_FRONT:
                return MODE_OPENING;
            case WindowManager.TRANSIT_CLOSE:
            case WindowManager.TRANSIT_TO_BACK:
                return MODE_CLOSING;
            default:
                return MODE_CHANGING;
        }
    }

    /**
     * Checks whether a transition change should be skipped when setting up surfaces and leashes,
     * based on whether it is independent or a stationary display-level change.
     */
    public static boolean skipReparenting(
            @NonNull TransitionInfo.Change change, @NonNull TransitionInfo info) {
        // Don't reparent anything that isn't independent within its parents.
        if (!TransitionInfo.isIndependent(change, info)) {
            return true;
        }

        // Don't reparent display level if the change is stationary (since root will be inside it).
        if (change.hasFlags(FLAG_IS_DISPLAY) && TransitionUtil.isStationary(change)
                && change.getStartRotation() == change.getEndRotation()) {
            return true;
        }

        return false;
    }

    /**
     * Reparents a transition participant into its transition root, and orders it based on: the
     * global transit type, their transit mode, and their destination z-order.
     */
    public static void setUpSurface(@NonNull TransitionInfo.Change change,
            @NonNull TransitionInfo info, int order, @NonNull SurfaceControl.Transaction t) {
        final SurfaceControl leash = change.getLeash();

        if (skipReparenting(change, info)) {
            return;
        }

        boolean hasParent = change.getParent() != null;

        final TransitionInfo.Root root = TransitionUtil.getRootFor(change, info);
        if (!hasParent) {
            t.reparent(leash, root.getLeash());
            t.setPosition(leash,
                    change.getStartAbsBounds().left - root.getOffset().x,
                    change.getStartAbsBounds().top - root.getOffset().y);
        }
        final int layer =
                calculateAnimLayer(change, order, info.getChanges().size(), info.getType());
        t.setLayer(leash, layer);
    }

    /**
     * Calculates the appropriate layer for a given transition participant based on the transition
     * type, mode, and destination z-order.
     * TODO(b/452329563): consolidate with the similar logic in {@link TransitionUtil#setupLeash}.
     */
    public static int calculateAnimLayer(@NonNull TransitionInfo.Change change, int order,
            int numChanges, @WindowManager.TransitionType int transitType) {
        // Put animating stuff above this line and put static stuff below it.
        final int zSplitLine = numChanges + 1;
        final boolean isOpening = isOpeningType(transitType);
        final boolean isClosing = isClosingType(transitType);
        final int mode = change.getMode();
        // Put all the OPEN/SHOW on top
        if (mode == TRANSIT_OPEN || mode == TRANSIT_TO_FRONT) {
            if (isOpening) {
                // put on top
                return zSplitLine + numChanges - order;
            } else if (isClosing) {
                // put on bottom
                return zSplitLine - order;
            } else {
                // maintain relative ordering (put all changes in the animating layer)
                return zSplitLine + numChanges - order;
            }
        } else if (mode == TRANSIT_CLOSE || mode == TRANSIT_TO_BACK) {
            if (isOpening || change.hasFlags(FLAG_IS_WALLPAPER)
                    || (com.android.window.flags.Flags.keepShowWallpaperOnBottom()
                    && change.hasFlags(FLAG_SHOW_WALLPAPER))) {
                // put on bottom and leave visible
                return zSplitLine - order;
            } else {
                // put on top
                return zSplitLine + numChanges - order;
            }
        } else { // CHANGE or other
            if (isClosing || TransitionUtil.isStationary(change)) {
                // Put below CLOSE mode (in the "static" section).
                return zSplitLine - order;
            } else {
                // Put above CLOSE mode.
                return zSplitLine + numChanges - order;
            }
        }
    }

    /**
     * Very similar to Transitions#setupAnimHierarchy but specialized for leashes.
     */
    @SuppressLint("NewApi")
    private static void setupLeash(@NonNull SurfaceControl leash,
            @NonNull TransitionInfo.Change change, int layer,
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction t) {
        final boolean isOpening = TransitionUtil.isOpeningType(info.getType());
        // Put animating stuff above this line and put static stuff below it.
        int zSplitLine = info.getChanges().size();
        // changes should be ordered top-to-bottom in z
        final int mode = change.getMode();

        final int rootIdx = TransitionUtil.rootIndexFor(change, info);
        t.reparent(leash, info.getRoot(rootIdx).getLeash());
        final Rect absBounds =
                (mode == TRANSIT_OPEN) ? change.getEndAbsBounds() : change.getStartAbsBounds();
        t.setPosition(leash, absBounds.left - info.getRoot(rootIdx).getOffset().x,
                absBounds.top - info.getRoot(rootIdx).getOffset().y);

        if (isDividerBar(change)) {
            if (isOpeningType(mode)) {
                t.setAlpha(leash, 0.f);
            }
            // Set the transition leash position to 0 in case the divider leash position being
            // taking down.
            t.setPosition(leash, 0, 0);
            t.setLayer(leash, Integer.MAX_VALUE);
            return;
        }

        // Put all the OPEN/SHOW on top
        if ((change.getFlags() & FLAG_IS_WALLPAPER) != 0) {
            // Wallpaper is always at the bottom, opening wallpaper on top of closing one.
            if (mode == WindowManager.TRANSIT_OPEN || mode == WindowManager.TRANSIT_TO_FRONT) {
                t.setLayer(leash, -zSplitLine + info.getChanges().size() - layer);
            } else {
                t.setLayer(leash, -zSplitLine - layer);
            }
        } else if (TransitionUtil.isOpeningType(mode)) {
            if (isOpening) {
                t.setLayer(leash, zSplitLine + info.getChanges().size() - layer);
                if ((change.getFlags() & FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT) == 0) {
                    // if transferred, it should be left visible.
                    t.setAlpha(leash, 0.f);
                }
            } else {
                // put on bottom and leave it visible
                t.setLayer(leash, zSplitLine - layer);
            }
        } else if (TransitionUtil.isClosingType(mode)) {
            if (isOpening) {
                // put on bottom and leave visible
                t.setLayer(leash, zSplitLine - layer);
            } else {
                // put on top
                t.setLayer(leash, zSplitLine + info.getChanges().size() - layer);
            }
        } else { // CHANGE
            t.setLayer(leash, zSplitLine + info.getChanges().size() - layer);
        }
    }

    @SuppressLint("NewApi")
    public static SurfaceControl createLeash(TransitionInfo info, TransitionInfo.Change change,
            int order, SurfaceControl.Transaction t) {
        // TODO: once we can properly sync transactions across process, then get rid of this leash.
        if (change.getParent() != null && (change.getFlags() & FLAG_IS_WALLPAPER) != 0) {
            // Special case for wallpaper atm. Normally these are left alone; but, a quirk of
            // making leashes means we have to handle them specially.
            return change.getLeash();
        }
        final int rootIdx = TransitionUtil.rootIndexFor(change, info);
        SurfaceControl leashSurface = new SurfaceControl.Builder()
                .setName(change.getLeash().toString() + "_transition-leash")
                .setContainerLayer()
                // Initial the surface visible to respect the visibility of the original surface.
                .setHidden(false)
                .setParent(info.getRoot(rootIdx).getLeash())
                .build();
        // Copied Transitions setup code (which expects bottom-to-top order, so we swap here)
        setupLeash(leashSurface, change, info.getChanges().size() - order, info, t);
        t.reparent(change.getLeash(), leashSurface);

        t.setAlpha(change.getLeash(), 1.0f);
        if (!isDividerBar(change)) {
            // For divider, don't modify its inner leash position when creating the outer leash
            // for the transition. In case the position being wrong after the transition finished.
            t.setPosition(change.getLeash(), 0, 0);
        }
        t.setLayer(change.getLeash(), 0);
        t.show(change.getLeash());
        return leashSurface;
    }

    /**
     * Creates a new RemoteAnimationTarget from the provided change info
     */
    public static RemoteAnimationTarget newTarget(TransitionInfo.Change change, int order,
            TransitionInfo info, SurfaceControl.Transaction t,
            @Nullable ArrayMap<SurfaceControl, SurfaceControl> leashMap) {
        return newTarget(change, order, false /* forceTranslucent */, info, t, leashMap);
    }

    /**
     * Creates a new RemoteAnimationTarget from the provided change info
     */
    public static RemoteAnimationTarget newTarget(TransitionInfo.Change change, int order,
            boolean forceTranslucent, TransitionInfo info, SurfaceControl.Transaction t,
            @Nullable ArrayMap<SurfaceControl, SurfaceControl> leashMap) {
        final SurfaceControl leash = createLeash(info, change, order, t);
        if (leashMap != null) {
            leashMap.put(change.getLeash(), leash);
        }
        return newTarget(change, order, leash, forceTranslucent);
    }

    /**
     * Creates a new RemoteAnimationTarget from the provided change and leash
     */
    public static RemoteAnimationTarget newTarget(TransitionInfo.Change change, int order,
            SurfaceControl leash) {
        return newTarget(change, order, leash, false /* forceTranslucent */);
    }

    /**
     * Creates a new RemoteAnimationTarget from the provided change and leash
     */
    public static RemoteAnimationTarget newTarget(TransitionInfo.Change change, int order,
            SurfaceControl leash, boolean forceTranslucent) {
        if (isDividerBar(change)) {
            return getDividerTarget(change, leash);
        }
        if (isDimLayer(change)) {
            return getDimLayerTarget(change, leash);
        }

        int taskId;
        boolean isNotInRecents;
        ActivityManager.RunningTaskInfo taskInfo;
        WindowConfiguration windowConfiguration;

        taskInfo = change.getTaskInfo();
        if (taskInfo != null) {
            taskId = taskInfo.taskId;
            isNotInRecents = !taskInfo.isRunning;
            windowConfiguration = taskInfo.configuration.windowConfiguration;
        } else {
            taskId = INVALID_TASK_ID;
            isNotInRecents = true;
            windowConfiguration = new WindowConfiguration();
        }

        Rect localBounds = new Rect(change.getEndAbsBounds());
        localBounds.offsetTo(change.getEndRelOffset().x, change.getEndRelOffset().y);

        RemoteAnimationTarget target = new RemoteAnimationTarget(
                taskId,
                newModeToLegacyMode(change.getMode()),
                // TODO: once we can properly sync transactions across process,
                // then get rid of this leash.
                leash,
                forceTranslucent || (change.getFlags() & TransitionInfo.FLAG_TRANSLUCENT) != 0,
                null,
                // TODO(shell-transitions): we need to send content insets? evaluate how its used.
                new Rect(0, 0, 0, 0),
                order,
                null,
                localBounds,
                new Rect(change.getEndAbsBounds()),
                windowConfiguration,
                isNotInRecents,
                null,
                new Rect(change.getStartAbsBounds()),
                taskInfo,
                change.isAllowEnterPip(),
                INVALID_WINDOW_TYPE
        );
        target.setWillShowImeOnTarget(
                (change.getFlags() & TransitionInfo.FLAG_WILL_IME_SHOWN) != 0);
        target.setRotationChange(change.getEndRotation() - change.getStartRotation());
        target.backgroundColor = change.getBackgroundColor();
        return target;
    }

    /**
     * Creates a new RemoteAnimationTarget from the provided change and leash
     */
    public static RemoteAnimationTarget newSyntheticTarget(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl leash, @TransitionInfo.TransitionMode int mode, int order,
            boolean isTranslucent) {
        int taskId;
        boolean isNotInRecents;
        WindowConfiguration windowConfiguration;

        if (taskInfo != null) {
            taskId = taskInfo.taskId;
            isNotInRecents = !taskInfo.isRunning;
            windowConfiguration = taskInfo.configuration.windowConfiguration;
        } else {
            taskId = INVALID_TASK_ID;
            isNotInRecents = true;
            windowConfiguration = new WindowConfiguration();
        }

        Rect bounds = windowConfiguration.getBounds();
        RemoteAnimationTarget target = new RemoteAnimationTarget(
                taskId,
                newModeToLegacyMode(mode),
                // TODO: once we can properly sync transactions across process,
                // then get rid of this leash.
                leash,
                isTranslucent,
                null,
                // TODO(shell-transitions): we need to send content insets? evaluate how its used.
                new Rect(0, 0, 0, 0),
                order,
                null,
                bounds,
                bounds,
                windowConfiguration,
                isNotInRecents,
                null,
                bounds,
                taskInfo,
                false,
                INVALID_WINDOW_TYPE
        );
        return target;
    }

    private static RemoteAnimationTarget getDividerTarget(TransitionInfo.Change change,
            SurfaceControl leash) {
        return new RemoteAnimationTarget(-1 /* taskId */, newModeToLegacyMode(change.getMode()),
                leash, false /* isTranslucent */, null /* clipRect */,
                null /* contentInsets */, Integer.MAX_VALUE /* prefixOrderIndex */,
                new android.graphics.Point(0, 0) /* position */, change.getStartAbsBounds(),
                change.getStartAbsBounds(), new WindowConfiguration(), true, null /* startLeash */,
                null /* startBounds */, null /* taskInfo */, false /* allowEnterPip */,
                TYPE_DOCK_DIVIDER);
    }

    private static RemoteAnimationTarget getDimLayerTarget(TransitionInfo.Change change,
            SurfaceControl leash) {
        return new RemoteAnimationTarget(-1 /* taskId */, newModeToLegacyMode(change.getMode()),
                leash, false /* isTranslucent */, null /* clipRect */,
                null /* contentInsets */, Integer.MAX_VALUE /* prefixOrderIndex */,
                new android.graphics.Point(0, 0) /* position */, change.getStartAbsBounds(),
                change.getStartAbsBounds(), new WindowConfiguration(), true, null /* startLeash */,
                null /* startBounds */, null /* taskInfo */, false /* allowEnterPip */,
                TYPE_SPLIT_SCREEN_DIM_LAYER);
    }

    /**
     * Finds the "correct" root idx for a change. The change's end display is prioritized, then
     * the start display. If there is no display, it will fallback on the 0th root in the
     * transition. There MUST be at-least 1 root in the transition (ie. it's not a no-op).
     */
    public static int rootIndexFor(@NonNull TransitionInfo.Change change,
            @NonNull TransitionInfo info) {
        int rootIdx = info.findRootIndex(change.getEndDisplayId());
        if (rootIdx >= 0) return rootIdx;
        rootIdx = info.findRootIndex(change.getStartDisplayId());
        if (rootIdx >= 0) return rootIdx;
        return 0;
    }

    /**
     * Gets the {@link TransitionInfo.Root} for the given {@link TransitionInfo.Change}.
     * @see #rootIndexFor(TransitionInfo.Change, TransitionInfo)
     */
    @NonNull
    public static TransitionInfo.Root getRootFor(@NonNull TransitionInfo.Change change,
            @NonNull TransitionInfo info) {
        return info.getRoot(rootIndexFor(change, info));
    }
}
