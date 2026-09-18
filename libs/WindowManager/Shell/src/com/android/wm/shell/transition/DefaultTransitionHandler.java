/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.app.ActivityOptions.ANIM_CLIP_REVEAL;
import static android.app.ActivityOptions.ANIM_CUSTOM;
import static android.app.ActivityOptions.ANIM_FROM_STYLE;
import static android.app.ActivityOptions.ANIM_NONE;
import static android.app.ActivityOptions.ANIM_OPEN_CROSS_PROFILE_APPS;
import static android.app.ActivityOptions.ANIM_SCALE_UP;
import static android.app.ActivityOptions.ANIM_SCENE_TRANSITION;
import static android.app.ActivityOptions.ANIM_THUMBNAIL_SCALE_DOWN;
import static android.app.ActivityOptions.ANIM_THUMBNAIL_SCALE_UP;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.admin.DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED;
import static android.app.admin.DevicePolicyManager.EXTRA_RESOURCE_TYPE;
import static android.app.admin.DevicePolicyManager.EXTRA_RESOURCE_TYPE_DRAWABLE;
import static android.app.admin.DevicePolicyResources.Drawables.Source.PROFILE_SWITCH_ANIMATION;
import static android.app.admin.DevicePolicyResources.Drawables.Style.OUTLINE;
import static android.app.admin.DevicePolicyResources.Drawables.WORK_PROFILE_ICON;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_UNSPECIFIED;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_KEYGUARD_UNOCCLUDE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_RELAUNCH;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.FLAG_CROSS_PROFILE_OWNER_THUMBNAIL;
import static android.window.TransitionInfo.FLAG_CROSS_PROFILE_WORK_THUMBNAIL;
import static android.window.TransitionInfo.FLAG_DISPLAY_HAS_ALERT_WINDOWS;
import static android.window.TransitionInfo.FLAG_FILLS_TASK;
import static android.window.TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY;
import static android.window.TransitionInfo.FLAG_IS_BEHIND_STARTING_WINDOW;
import static android.window.TransitionInfo.FLAG_IS_DISPLAY;
import static android.window.TransitionInfo.FLAG_IS_VOICE_INTERACTION;
import static android.window.TransitionInfo.FLAG_IS_WALLPAPER;
import static android.window.TransitionInfo.FLAG_SHOW_WALLPAPER;
import static android.window.TransitionInfo.FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT;
import static android.window.TransitionInfo.FLAG_TRANSLUCENT;

import static com.android.internal.jank.Cuj.CUJ_DEFAULT_TASK_TO_TASK_ANIMATION;
import static com.android.internal.policy.TransitionAnimation.MAX_ANIMATION_DURATION;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_CHANGE;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_CLOSE;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_INTRA_CLOSE;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_INTRA_OPEN;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_NONE;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_OPEN;
import static com.android.wm.shell.transition.DefaultSurfaceAnimator.buildWindowAnimation;
import static com.android.wm.shell.transition.TransitionAnimationHelper.getTransitionBackgroundColorIfSet;
import static com.android.wm.shell.transition.TransitionAnimationHelper.getTransitionTypeFromInfo;
import static com.android.wm.shell.transition.TransitionAnimationHelper.isCoveredByOpaqueFullscreenChange;
import static com.android.wm.shell.transition.TransitionAnimationHelper.loadAttributeAnimation;

import android.animation.ValueAnimator;
import android.annotation.ColorInt;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.window.TransitionInfo;
import android.window.TransitionMetrics;
import android.window.TransitionRequestInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerTransaction;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.policy.ScreenDecorationsUtils;
import com.android.internal.policy.TransitionAnimation;
import com.android.internal.protolog.ProtoLog;
import com.android.window.flags.Flags;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.animation.SizeChangeAnimation;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.animation.Interpolators;
import com.android.wm.shell.sysui.ShellInit;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

/** The default handler that handles anything not already handled. */
public class DefaultTransitionHandler implements Transitions.TransitionHandler {
    private static final int SIZE_CHANGE_ANIMATION_DURATION = 400;
    private static final int MERGE_ANIMATION_DURATION = 400;

    private final TransactionPool mTransactionPool;
    private final DisplayController mDisplayController;
    private final Context mContext;
    private final Handler mMainHandler;
    private final ShellExecutor mMainExecutor;
    private final ShellExecutor mAnimExecutor;
    private final TransitionAnimation mTransitionAnimation;
    private final DevicePolicyManager mDevicePolicyManager;
    private final TransitionAnimationHelper.RoundedContentTracker mRoundedContentBounds;

    /** Keeps track of the currently-running transitions and their window animations */
    private final ArrayMap<IBinder, ArrayList<WindowAnimation>>
            mTransitionAnimators = new ArrayMap<>();
    private final ArrayMap<IBinder, Transitions.TransitionFinishCallback> mFinishCallbacks =
            new ArrayMap<>();
    private final CounterRotatorHelper mRotator = new CounterRotatorHelper();
    private final Rect mInsets = new Rect(0, 0, 0, 0);
    private float mTransitionAnimationScaleSetting = 1.0f;

    private final RootTaskDisplayAreaOrganizer mRootTDAOrganizer;
    private final int mCurrentUserId;

    private Drawable mEnterpriseThumbnailDrawable;

    final InteractionJankMonitor mInteractionJankMonitor;

    private BroadcastReceiver mEnterpriseResourceUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getIntExtra(EXTRA_RESOURCE_TYPE, /* default= */ -1)
                    != EXTRA_RESOURCE_TYPE_DRAWABLE) {
                return;
            }
            updateEnterpriseThumbnailDrawable();
        }
    };

    DefaultTransitionHandler(@NonNull Context context,
            @NonNull ShellInit shellInit,
            @NonNull DisplayController displayController,
            @NonNull DisplayInsetsController displayInsetsController,
            @NonNull TransactionPool transactionPool,
            @NonNull ShellExecutor mainExecutor, @NonNull Handler mainHandler,
            @NonNull ShellExecutor animExecutor,
            @NonNull RootTaskDisplayAreaOrganizer rootTDAOrganizer,
            @NonNull InteractionJankMonitor interactionJankMonitor) {
        mDisplayController = displayController;
        mTransactionPool = transactionPool;
        mContext = context;
        mMainHandler = mainHandler;
        mMainExecutor = mainExecutor;
        mAnimExecutor = animExecutor;
        mTransitionAnimation = new TransitionAnimation(context, false /* debug */, Transitions.TAG);
        mCurrentUserId = UserHandle.myUserId();
        mDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);
        shellInit.addInitCallback(this::onInit, this);
        mRootTDAOrganizer = rootTDAOrganizer;
        mRoundedContentBounds = new TransitionAnimationHelper.RoundedContentTracker(
                displayController, displayInsetsController);
        mInteractionJankMonitor = interactionJankMonitor;
    }

    private void onInit() {
        updateEnterpriseThumbnailDrawable();
        mContext.registerReceiver(
                mEnterpriseResourceUpdatedReceiver,
                new IntentFilter(ACTION_DEVICE_POLICY_RESOURCE_UPDATED),
                /* broadcastPermission = */ null,
                mMainHandler);

        TransitionAnimation.initAttributeCache(mContext, mMainHandler);
        mRoundedContentBounds.init();
    }

    private void updateEnterpriseThumbnailDrawable() {
        mEnterpriseThumbnailDrawable = mDevicePolicyManager.getResources().getDrawable(
                WORK_PROFILE_ICON, OUTLINE, PROFILE_SWITCH_ANIMATION,
                () -> mContext.getDrawable(R.drawable.ic_corp_badge));
    }

    @VisibleForTesting
    static int getRotationAnimationHint(@NonNull TransitionInfo.Change displayChange,
            @NonNull TransitionInfo info, @NonNull DisplayController displayController) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                "Display is changing, resolve the animation hint.");
        // The explicit request of display has the highest priority.
        if (displayChange.getRotationAnimation() == ROTATION_ANIMATION_SEAMLESS) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    "  display requests explicit seamless");
            return ROTATION_ANIMATION_SEAMLESS;
        }

        boolean allTasksSeamless = false;
        boolean rejectSeamless = false;
        ActivityManager.RunningTaskInfo topTaskInfo = null;
        int animationHint = ROTATION_ANIMATION_ROTATE;
        // Traverse in top-to-bottom order so that the first task is top-most.
        final int size = info.getChanges().size();
        for (int i = 0; i < size; ++i) {
            final TransitionInfo.Change change = info.getChanges().get(i);

            // Only look at changing things. showing/hiding don't need to rotate.
            if (change.getMode() != TRANSIT_CHANGE) continue;

            // This container isn't rotating, so we can ignore it.
            if (change.getEndRotation() == change.getStartRotation()) continue;
            if ((change.getFlags() & FLAG_IS_DISPLAY) != 0) {
                // In the presence of System Alert windows we can not seamlessly rotate.
                if ((change.getFlags() & FLAG_DISPLAY_HAS_ALERT_WINDOWS) != 0) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                            "  display has system alert windows, so not seamless.");
                    rejectSeamless = true;
                }
            } else if ((change.getFlags() & FLAG_IS_WALLPAPER) != 0) {
                if (change.getRotationAnimation() != ROTATION_ANIMATION_SEAMLESS) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                            "  wallpaper is participating but isn't seamless.");
                    rejectSeamless = true;
                }
            } else if (change.getTaskInfo() != null) {
                final int anim = change.getRotationAnimation();
                final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
                final boolean isTopTask = topTaskInfo == null;
                if (isTopTask) {
                    topTaskInfo = taskInfo;
                    if (anim != ROTATION_ANIMATION_UNSPECIFIED
                            && anim != ROTATION_ANIMATION_SEAMLESS) {
                        animationHint = anim;
                    }
                }
                // We only enable seamless rotation if all the visible task windows requested it.
                if (anim != ROTATION_ANIMATION_SEAMLESS) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                            "  task %d isn't requesting seamless, so not seamless.",
                            taskInfo.taskId);
                    allTasksSeamless = false;
                } else if (isTopTask) {
                    allTasksSeamless = true;
                }
            }
        }

        if (!allTasksSeamless || rejectSeamless) {
            return animationHint;
        }

        // This is the only way to get display-id currently, so check display capabilities here.
        final DisplayLayout displayLayout = displayController.getDisplayLayout(
                topTaskInfo.displayId);
        // This condition should be true when using gesture navigation or the screen size is large
        // (>600dp) because the bar is small relative to screen.
        if (displayLayout.allowSeamlessRotationDespiteNavBarMoving()) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "  nav bar allows seamless.");
            return ROTATION_ANIMATION_SEAMLESS;
        }
        // For the upside down rotation we don't rotate seamlessly as the navigation bar moves
        // position. Note most apps (using orientation:sensor or user as opposed to fullSensor)
        // will not enter the reverse portrait orientation, so actually the orientation won't
        // change at all.
        final int upsideDownRotation = displayLayout.getUpsideDownRotation();
        if (displayChange.getStartRotation() == upsideDownRotation
                || displayChange.getEndRotation() == upsideDownRotation) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    "  rotation involves upside-down portrait, so not seamless.");
            return animationHint;
        }

        // If the navigation bar cannot change sides, then it will jump when changing orientation
        // so do not use seamless rotation.
        if (!displayLayout.navigationBarCanMove()) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    "  nav bar changes sides, so not seamless.");
            return animationHint;
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "  Rotation IS seamless.");
        return ROTATION_ANIMATION_SEAMLESS;
    }

    @Nullable
    final TransitionAnimationHelper.RoundedContentPerDisplay getRoundedContentBounds(
            TransitionInfo.Change change) {
        if (change.getTaskInfo() == null && change.getActivityComponent() == null) {
            return null;
        }
        return mRoundedContentBounds.forDisplay(change.getEndDisplayId());
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @Nullable TransitionInfo info,
            @NonNull TransitionDispatchState dispatchState,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (info == null) {
            // In data collection mode: there can't be errors - nothing to do
            return false;
        }
        // In animation mode: always play everything
        return startAnimation(
                transition, info, startTransaction, finishTransaction, finishCallback);
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                "start default transition animation, info = %s", info);
        // If keyguard goes away, we should loadKeyguardExitAnimation. Otherwise this just
        // immediately finishes since there is no animation for screen-wake.
        if (info.getType() == WindowManager.TRANSIT_WAKE && !info.isKeyguardGoingAway()) {
            startTransaction.apply();
            finishCallback.onTransitionFinished(null /* wct */);
            return true;
        }

        // Early check if the transition doesn't warrant an animation.
        if (isAnimationsDisabledForAnyDisplay(info) || TransitionUtil.isAllNoAnimation(info)
                || TransitionUtil.isAllStationary(info)
                || (info.getFlags() & WindowManager.TRANSIT_FLAG_INVISIBLE) != 0) {
            startTransaction.apply();
            // As a contract, finishTransaction should only be applied in Transitions#onFinish
            finishCallback.onTransitionFinished(null /* wct */);
            return true;
        }

        if (mTransitionAnimators.containsKey(transition)) {
            throw new IllegalStateException("Got a duplicate startAnimation call for "
                    + transition);
        }
        final ArrayList<WindowAnimation> animations = new ArrayList<>();
        mTransitionAnimators.put(transition, animations);
        mFinishCallbacks.put(transition, finishCallback);

        final boolean isTaskTransition = com.android.window.flags.Flags.transitionHandlerCujTags()
                && isTaskTransition(info);

        final Consumer<WindowAnimation> onAnimFinish = (winAnim) -> {
            animations.remove(winAnim);
            if (!animations.isEmpty()) return;
            finishTransition(transition, info, finishCallback, isTaskTransition);
        };

        @ColorInt int backgroundColorForTransition = 0;
        final int wallpaperTransit = getWallpaperTransitType(info);
        int animatingDisplayId = Integer.MIN_VALUE;
        final boolean isDreamTransition = TransitionUtil.isDreamTransition(info);
        final boolean isOnlyTranslucent = isOnlyTranslucent(info);
        final boolean isActivityLevel = isActivityLevelOnly(info);

        // Don't create a background color layer if there is only one change in the transition.
        // This is to avoid incorrectly occluding other layers when a transition only contains a
        // single "close" change, for example. With only one change, there are no other layers
        // within the transition to interact with, so a background is unnecessary.
        final boolean allowBackground = info.getChanges().size() > 1
                && (wallpaperTransit != WALLPAPER_TRANSITION_INTRA_OPEN
                && wallpaperTransit != WALLPAPER_TRANSITION_INTRA_CLOSE);

        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.hasAllFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY
                    | FLAG_IS_BEHIND_STARTING_WINDOW)) {
                // Don't animate embedded activity if it is covered by the starting window.
                // Non-embedded case still needs animation because the container can still animate
                // the starting window together, e.g. CLOSE or CHANGE type.
                continue;
            }
            if (change.hasFlags(TransitionInfo.FLAGS_IS_NON_APP_WINDOW)) {
                // Wallpaper, IME, and system windows don't need any default animations.
                continue;
            }
            final boolean isTask = change.getTaskInfo() != null;
            final boolean isFreeform = isTask && change.getTaskInfo().isFreeform();
            final int mode = change.getMode();
            boolean isSeamlessDisplayChange = false;

            if (mode == TRANSIT_CHANGE && change.hasFlags(FLAG_IS_DISPLAY)) {
                if (info.getType() == TRANSIT_CHANGE || isOnlyTranslucent) {
                    final int anim = getRotationAnimationHint(change, info, mDisplayController);
                    isSeamlessDisplayChange = anim == ROTATION_ANIMATION_SEAMLESS;
                    if (!(isSeamlessDisplayChange || anim == ROTATION_ANIMATION_JUMPCUT)) {
                        final int flags = wallpaperTransit != WALLPAPER_TRANSITION_NONE
                                ? ScreenRotationAnimation.FLAG_HAS_WALLPAPER : 0;
                        startRotationAnimation(startTransaction, change,
                                info, anim, flags, onAnimFinish)
                                .ifPresent(animations::add);
                        animatingDisplayId = change.getEndDisplayId();
                        continue;
                    }
                } else {
                    // Opening/closing an app into a new orientation.
                    mRotator.handleClosingChanges(info, startTransaction, change);
                }
            }

            if (mode == TRANSIT_CHANGE) {
                // If task is child task, only set position in parent and update crop when needed,
                // unless the task is changing displays.
                if (isTask && change.getParent() != null
                        && info.getChange(change.getParent()).getTaskInfo() != null
                        && !(com.android.window.flags.Flags.crossDisplayTransitionV2()
                                && change.isCrossDisplay())) {
                    final Point positionInParent = change.getTaskInfo().positionInParent;
                    startTransaction.setPosition(change.getLeash(),
                            positionInParent.x, positionInParent.y);

                    if (!change.getEndAbsBounds().equals(
                            info.getChange(change.getParent()).getEndAbsBounds())) {
                        startTransaction.setWindowCrop(change.getLeash(),
                                change.getEndAbsBounds().width(),
                                change.getEndAbsBounds().height());
                    }

                    continue;
                }

                // There is no default animation for Pip window in rotation transition, and the
                // PipTransition will update the surface of its own window at start/finish.
                if (isTask && change.getTaskInfo().configuration.windowConfiguration
                        .getWindowingMode() == WINDOWING_MODE_PINNED) {
                    continue;
                }
                // No default animation for this, so just update bounds/position.
                if (change.getParent() == null) {
                    // For independent change without a parent, we have reparented it to the root
                    // leash in Transitions#setupAnimHierarchy.
                    final int rootIdx = TransitionUtil.rootIndexFor(change, info);
                    startTransaction.setPosition(change.getLeash(),
                            change.getEndAbsBounds().left - info.getRoot(rootIdx).getOffset().x,
                            change.getEndAbsBounds().top - info.getRoot(rootIdx).getOffset().y);
                } else {
                    startTransaction.setPosition(change.getLeash(),
                            change.getEndRelOffset().x, change.getEndRelOffset().y);
                }
                // Seamless display transition doesn't need to animate.
                if (isSeamlessDisplayChange) continue;
                if (isTask || (change.hasFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY)
                        && !change.hasFlags(FLAG_FILLS_TASK))) {
                    // Update Task and embedded split window crop bounds, otherwise we may see crop
                    // on previous bounds during the rotation animation.
                    startTransaction.setWindowCrop(change.getLeash(),
                            change.getEndAbsBounds().width(), change.getEndAbsBounds().height());
                }

                // Display move
                if (com.android.window.flags.Flags.crossDisplayTransitionV2()
                        && change.isCrossDisplay()) {
                    startDisplayMoveAnimation(startTransaction, change, info,
                            onAnimFinish, mMainExecutor).ifPresent(animations::add);
                    continue;
                }

                // Rotation change of independent non display window container.
                if (change.getParent() == null && !change.hasFlags(FLAG_IS_DISPLAY)
                        && change.getStartRotation() != change.getEndRotation()) {
                    startRotationAnimation(startTransaction, change, info,
                            ROTATION_ANIMATION_ROTATE, 0 /* flags */,
                            onAnimFinish)
                            .ifPresent(animations::add);

                    continue;
                }

                if (Flags.portWindowSizeAnimation() && isTask
                        && TransitionInfo.isIndependent(change, info)
                        && change.getSnapshot() != null) {
                    startBoundsChangeAnimation(startTransaction, change, onAnimFinish,
                            mMainExecutor)
                            .ifPresent(animations::add);
                    continue;
                }
            }

            // Hide the invisible surface directly without animating it if there is a display
            // rotation animation playing.
            if (animatingDisplayId == change.getEndDisplayId()) {
                if (TransitionUtil.isClosingType(mode)) {
                    startTransaction.hide(change.getLeash());
                }
                // Only need to play display level animation.
                continue;
            }

            // Don't animate anything that isn't independent.
            if (!TransitionInfo.isIndependent(change, info)) continue;

            final int type = getTransitionTypeFromInfo(info);
            Animation a = loadAnimation(type, info, change, wallpaperTransit, isDreamTransition);
            if (a != null) {
                final int displayId = isTask ? change.getTaskInfo().displayId
                        : info.getRoot(TransitionUtil.rootIndexFor(change, info))
                                .getDisplayId();
                final Context displayContext =
                        mDisplayController.getDisplayContext(displayId);
                if (displayContext != null
                        && displayContext.getResources().getConfiguration().isScreenRound()) {
                    // ensure that any animation on a round display is using rounded corners
                    a.setHasRoundedCorners(true);
                }

                if (isTask) {
                    final boolean isTranslucent = (change.getFlags() & FLAG_TRANSLUCENT) != 0;
                    if (!isTranslucent && TransitionUtil.isOpenOrCloseMode(mode)
                            && TransitionUtil.isOpenOrCloseMode(info.getType())
                            && wallpaperTransit == WALLPAPER_TRANSITION_NONE
                            && allowBackground) {
                        // Use the overview background as the background for the animation
                        final Context uiContext = ActivityThread.currentActivityThread()
                                .getSystemUiContext();
                        backgroundColorForTransition =
                                uiContext.getColor(R.color.overview_background);
                    }
                    if (wallpaperTransit == WALLPAPER_TRANSITION_OPEN
                            && TransitionUtil.isOpeningType(info.getType())) {
                        // Need to flip the z-order of opening/closing because the WALLPAPER_OPEN
                        // always animates the closing task over the opening one while
                        // traditionally, an OPEN transition animates the opening over the closing.

                        // See Transitions#setupAnimHierarchy for details about these variables.
                        final int numChanges = info.getChanges().size();
                        final int zSplitLine = numChanges + 1;
                        if (TransitionUtil.isOpeningType(mode)) {
                            final int layer = zSplitLine - i;
                            startTransaction.setLayer(change.getLeash(), layer);
                        } else if (TransitionUtil.isClosingType(mode)) {
                            final int layer = zSplitLine + numChanges - i;
                            startTransaction.setLayer(change.getLeash(), layer);
                        }
                    } else if (!isCoveredByOpaqueFullscreenChange(info, change)
                            && isFreeform
                            && TransitionUtil.isOpeningMode(type)
                            && change.getMode() == TRANSIT_TO_BACK) {
                        // Reparent the minimize-change to the root task so the minimizing Task
                        // isn't shown in front of other Tasks.
                        mRootTDAOrganizer.reparentToDisplayArea(
                                change.getTaskInfo().displayId,
                                change.getLeash(),
                                startTransaction);
                    } else if (isOnlyTranslucent && TransitionUtil.isOpeningType(info.getType())
                            && TransitionUtil.isClosingType(mode)) {
                        // If there is a closing translucent task in an OPENING transition, we will
                        // actually select a CLOSING animation, so move the closing task into
                        // the animating part of the z-order.

                        // See Transitions#setupAnimHierarchy for details about these variables.
                        final int numChanges = info.getChanges().size();
                        final int zSplitLine = numChanges + 1;
                        final int layer = zSplitLine + numChanges - i;
                        startTransaction.setLayer(change.getLeash(), layer);
                    }
                }

                final float cornerRadius;
                if (a.hasRoundedCorners()) {
                    cornerRadius = displayContext == null ? 0
                            : ScreenDecorationsUtils.getWindowCornerRadius(displayContext);
                } else {
                    cornerRadius = 0;
                }

                if (allowBackground) {
                    backgroundColorForTransition = getTransitionBackgroundColorIfSet(change, a,
                            backgroundColorForTransition);
                }

                final Rect clipRect = TransitionUtil.isClosingType(mode)
                        ? new Rect(mRotator.getEndBoundsInStartRotation(change))
                        : new Rect(change.getEndAbsBounds());
                clipRect.offsetTo(0, 0);

                final TransitionInfo.Root animRoot = TransitionUtil.getRootFor(change, info);
                final Rect boundsForOffset =
                        com.android.window.flags.Flags.refineAncestorSearchAndBounds()
                                && TransitionUtil.isClosingType(change.getMode())
                                ? change.getStartAbsBounds() : change.getEndAbsBounds();
                final Point animRelOffset = new Point(
                        boundsForOffset.left - animRoot.getOffset().x,
                        boundsForOffset.top - animRoot.getOffset().y);

                final boolean isActivity = change.getActivityComponent() != null;
                if (isActivity) {
                    // For appcompat letterbox: we intentionally report the task-bounds so that we
                    // can animate as-if letterboxes are "part of" the activity. This means we can't
                    // always rely solely on endAbsBounds and need to also max with endRelOffset.
                    animRelOffset.x = Math.max(animRelOffset.x, change.getEndRelOffset().x);
                    animRelOffset.y = Math.max(animRelOffset.y, change.getEndRelOffset().y);
                }
                if (!isTask && a.getExtensionEdges() != 0x0
                        && (change.hasFlags(FLAG_FILLS_TASK
                        | FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY))) {
                    startTransaction.setEdgeExtensionEffect(
                            change.getLeash(), a.getExtensionEdges());
                    finishTransaction.setEdgeExtensionEffect(change.getLeash(), /* edge */ 0);
                }

                if (isActivity && !isActivityLevel
                        && !mRotator.isRotated(change)) {
                    // At this point, this is an independent activity change in a non-activity
                    // transition. This means that an activity transition got erroneously combined
                    // with another ongoing transition. This then means that the animation root may
                    // not tightly fit the activities, so we have to put them in a separate crop.
                    final int layer = TransitionUtil.calculateAnimLayer(change, i,
                            info.getChanges().size(), info.getType());
                    final SurfaceControl leash = new SurfaceControl.Builder()
                            .setName("Transition ActivityWrap: "
                                    + change.getActivityComponent().toShortString())
                            .setParent(animRoot.getLeash())
                            .setContainerLayer().build();
                    startTransaction.setCrop(leash, clipRect);
                    startTransaction.setPosition(leash, animRelOffset.x, animRelOffset.y);
                    startTransaction.setLayer(leash, layer);
                    startTransaction.show(leash);
                    startTransaction.reparent(change.getLeash(), leash);
                    startTransaction.setPosition(change.getLeash(), 0, 0);
                    animRelOffset.set(0, 0);
                    finishTransaction.reparent(leash, null);
                    leash.release();
                }

                WindowAnimation winAnim = buildWindowAnimation(a, change, change.getLeash(),
                        onAnimFinish,
                        mTransactionPool, mMainExecutor, animRelOffset, cornerRadius, clipRect,
                        isTask || isActivity
                                ? mRoundedContentBounds.forDisplay(change.getEndDisplayId())
                                : null);
                animations.add(winAnim);

                final TransitionInfo.AnimationOptions options = change.getAnimationOptions();
                if (options != null) {
                    attachThumbnail(onAnimFinish, change, options, cornerRadius)
                            .ifPresent(animations::add);
                }
            }
        }

        if (backgroundColorForTransition != 0) {
            addBackgroundColor(info, backgroundColorForTransition, startTransaction,
                    finishTransaction);
        }

        startTransaction.apply();

        final boolean hasAnimations = !animations.isEmpty();
        if (hasAnimations) {
            if (isTaskTransition) {
                mInteractionJankMonitor.begin(info.getRoot(0).getLeash(), mContext,
                        mMainHandler, CUJ_DEFAULT_TASK_TO_TASK_ANIMATION);
            }

            // now start animations. they are started on another thread, so we have to post them
            // *after* applying the startTransaction
            mAnimExecutor.execute(() -> {
                animations.forEach(WindowAnimation::start);
            });
        }

        mRotator.cleanUp(finishTransaction);
        TransitionMetrics.getInstance().reportAnimationStart(transition);
        // run finish now in-case there are no animations
        if (!hasAnimations) {
            finishTransition(transition, info, finishCallback, isTaskTransition);
        }
        return true;
    }

    private void finishTransition(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            boolean isTaskTransition) {
        if (isTaskTransition) {
            mInteractionJankMonitor.end(CUJ_DEFAULT_TASK_TO_TASK_ANIMATION);
        }
        mTransitionAnimators.remove(transition);
        mFinishCallbacks.remove(transition);

        if (!Flags.releaseAllTransitionSurfacesOnIdle()) {
            info.releaseAllSurfaces();
        }
        finishCallback.onTransitionFinished(null /* wct */);
    }

    private boolean isAnimationsDisabledForAnyDisplay(@NonNull TransitionInfo info) {
        boolean disabled = false;
        int rootCount = info.getRootCount();
        for (int i = 0; i < rootCount; i++) {
            disabled |= mDisplayController.isAnimationsDisabled(info.getRoot(i).getDisplayId());
        }
        return disabled;
    }

    private void addBackgroundColor(@NonNull TransitionInfo info,
            @ColorInt int color, @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        final Color bgColor = Color.valueOf(color);
        final float[] colorArray = new float[]{bgColor.red(), bgColor.green(), bgColor.blue()};

        boolean isSplitTaskInvolved = false;
        for (var change : info.getChanges()) {
            isSplitTaskInvolved |= (change.getTaskInfo() != null
                    && change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW);
        }

        for (int i = 0; i < info.getRootCount(); ++i) {
            final int displayId = info.getRoot(i).getDisplayId();
            final SurfaceControl backgroundSurface = new SurfaceControl.Builder()
                    .setName("animation-background for #" + info.getDebugId())
                    .setCallsite("DefaultTransitionHandler")
                    .setColorLayer()
                    .setParent(info.getRoot(i).getLeash())
                    .build();

            startTransaction.setColor(backgroundSurface, colorArray)
                    .setLayer(backgroundSurface, -1)
                    .show(backgroundSurface);

            // Attaching the background surface to the transition root could unexpectedly make it
            // cover one of the split root tasks. To avoid this, put the background surface just
            // above the display area when split is on.
            if (isSplitTaskInvolved) {
                try {
                    mRootTDAOrganizer.relZToDisplayArea(
                            displayId, backgroundSurface, startTransaction, -1);
                } catch (NoSuchElementException e) {
                    ProtoLog.wtf(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                            "Unable to add background because display %d does not exist",
                            displayId);
                }
            }

            finishTransaction.remove(backgroundSurface);
        }
    }

    /**
     * A task transition is defined as a transition where there is exaclty one open/to_front task
     * and one close/to_back task. Nothing else is allowed to be included in the transition
     */
    public static boolean isTaskTransition(@NonNull TransitionInfo info) {
        if (info.getChanges().size() != 2) {
            return false;
        }
        boolean hasOpeningTask = false;
        boolean hasClosingTask = false;

        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getTaskInfo() == null) {
                // A non-task is in the transition
                return false;
            }
            int mode = change.getMode();
            hasOpeningTask |= mode == TRANSIT_OPEN || mode == TRANSIT_TO_FRONT;
            hasClosingTask |= mode == TRANSIT_CLOSE || mode == TRANSIT_TO_BACK;
        }
        return hasOpeningTask && hasClosingTask;
    }

    /**
     * Does `info` only contain translucent visibility changes (CHANGEs are ignored). We select
     * different animations and z-orders for these
     */
    private static boolean isOnlyTranslucent(@NonNull TransitionInfo info) {
        int translucentOpen = 0;
        int translucentClose = 0;
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getMode() == TRANSIT_CHANGE) continue;
            if (change.hasFlags(FLAG_TRANSLUCENT)) {
                if (TransitionUtil.isOpeningType(change.getMode())) {
                    translucentOpen += 1;
                } else {
                    translucentClose += 1;
                }
            } else {
                return false;
            }
        }
        return (translucentOpen + translucentClose) > 0;
    }

    /**
     * Does `info` only contain activity-level changes? This kinda assumes that if so, they are
     * all in one task.
     */
    private static boolean isActivityLevelOnly(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getActivityComponent() == null) return false;
        }
        return true;
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ArrayList<WindowAnimation> animations = mTransitionAnimators.get(mergeTarget);
        if (animations == null) return;
        if (!Flags.enableMergeAnimations() || !MergeTransitionHelper.canCreateMergeAnimation(
                info)) {
            endAllAnimations(animations);
            return;
        }

        Transitions.TransitionFinishCallback originalFinishCallback = mFinishCallbacks.get(
                mergeTarget);

        List<WindowAnimation> newAnimations = new ArrayList<>();
        List<WindowAnimation> animationsToCancel = new ArrayList<>();

        var conflictingWindowAnimationsMap = MergeTransitionHelper.getMergeConflicts(animations,
                info);

        Consumer<WindowAnimation> onAnimFinish = (wa) -> {
            animations.remove(wa);
            if (animations.isEmpty()) {
                finishTransition(mergeTarget, info, originalFinishCallback, true);
            }
        };

        // Create animations for the conflicting layers
        var validConflictingChanges = new ArrayList<TransitionInfo.Change>();
        for (int i = 0; i < conflictingWindowAnimationsMap.size(); ++i) {
            WindowAnimation runningWindowAnimation = conflictingWindowAnimationsMap.keyAt(i);
            TransitionInfo.Change incomingChange = conflictingWindowAnimationsMap.valueAt(i);

            animationsToCancel.add(runningWindowAnimation);

            WindowAnimationState mergingWindowState =
                    runningWindowAnimation.getWindowAnimationState();
            Animation a = new MergeTransitionHelper.StateToRectAnimation(
                    mergingWindowState,
                    incomingChange.getEndAbsBounds(),
                    MERGE_ANIMATION_DURATION,
                    Interpolators.EMPHASIZED);

            // We need to modify the startT so that the window remains visually "frozen" in its
            // correct starting position during the tiny gap between the merge request and
            // the first animation frame
            if (incomingChange.getEndAbsBounds().width() > 0
                    && incomingChange.getEndAbsBounds().height() > 0) {
                Transformation transformation = new Transformation();
                a.getTransformation(0, transformation);
                float[] matrix = new float[9];

                startT.setMatrix(incomingChange.getLeash(),
                        transformation.getMatrix(),
                        matrix);
                startT.setAlpha(incomingChange.getLeash(), 1f);
            }

            WindowAnimation newWinAnim = buildWindowAnimation(a, incomingChange,
                    incomingChange.getLeash(),
                    onAnimFinish, mTransactionPool, mMainExecutor, null /* position */,
                    runningWindowAnimation.mCornerRadius,
                    null /* clipRect */, null /* roundedBounds */);
            newAnimations.add(newWinAnim);
            validConflictingChanges.add(incomingChange);
        }

        // Arrange the layers based on recency, as we want overwriting changes to have priority
        MergeTransitionHelper.arrangeStartTransactionLayers(info, validConflictingChanges,
                startT);

        startT.apply();

        if (!newAnimations.isEmpty()) {
            finishCallback.onTransitionFinished(null);
            animations.addAll(newAnimations);
            mAnimExecutor.execute(() -> {
                animationsToCancel.forEach(WindowAnimation::cancelRemoveListeners);
                newAnimations.forEach(WindowAnimation::start);
            });
        } else {
            // Fallback if no matching changes found
            for (int i = animations.size() - 1; i >= 0; --i) {
                final WindowAnimation winAnim = animations.get(i);
                mAnimExecutor.execute(winAnim::end);
            }
        }
    }

    private void endAllAnimations(List<WindowAnimation> animations) {
        for (int i = animations.size() - 1; i >= 0; --i) {
            final WindowAnimation winAnim = animations.get(i);
            mAnimExecutor.execute(winAnim::end);
        }
    }

    private Optional<WindowAnimation> startRotationAnimation(
            SurfaceControl.Transaction startTransaction,
            TransitionInfo.Change change, TransitionInfo info, int animHint, int flags,
            Consumer<WindowAnimation> finishCallback) {
        final int rootIdx = TransitionUtil.rootIndexFor(change, info);
        final ScreenRotationAnimation anim = new ScreenRotationAnimation(mContext,
                mTransactionPool, startTransaction, change, info.getRoot(rootIdx).getLeash(),
                animHint, flags);

        Consumer<WindowAnimation> wrappedCallback = (winAnim) -> {
            anim.kill();
            finishCallback.accept(winAnim);
        };

        final WindowAnimation rotationAnimation = anim.buildAnimation(wrappedCallback,
                mTransitionAnimationScaleSetting, mMainExecutor);
        if (rotationAnimation == null) {
            anim.kill();
            return Optional.empty();
        }
        return Optional.of(rotationAnimation);
    }

    /**
     * Animates a task moving across physical displays.
     * This method coordinates a two-part animation:
     * 1. A 'departure' animation on the source display using a snapshot (if available).
     * 2. An 'arrival' animation on the destination display using the actual task leash.
     */
    private Optional<WindowAnimation> startDisplayMoveAnimation(
            @NonNull SurfaceControl.Transaction startT,
            @NonNull TransitionInfo.Change change,
            @NonNull TransitionInfo info,
            @NonNull Consumer<WindowAnimation> finishCallback,
            @NonNull ShellExecutor mainExecutor) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "displayMoveAnimation");
        final DisplayMoveAnimation displayMoveAnimation = new DisplayMoveAnimation(mTransactionPool,
                mDisplayController);
        return displayMoveAnimation.startAnimation(startT, change, info, finishCallback,
            mainExecutor);
    }

    private Optional<WindowAnimation> startBoundsChangeAnimation(
            @NonNull SurfaceControl.Transaction startT,
            @NonNull TransitionInfo.Change change,
            @NonNull Consumer<WindowAnimation> finishCallback,
            @NonNull ShellExecutor mainExecutor) {
        final SizeChangeAnimation sca = new SizeChangeAnimation(change.getStartAbsBounds(),
                change.getEndAbsBounds(), /* initialScale= */ 1f, /* scaleFactor= */ 1f);
        sca.initialize(change.getLeash(), change.getSnapshot(), startT);
        final ValueAnimator va = sca.buildAnimator(change.getLeash(), change.getSnapshot(),
                (animation) -> { /* cleanups handled in sca.buildAnimator internally */ });
        va.setDuration(SIZE_CHANGE_ANIMATION_DURATION);
        va.setInterpolator(Interpolators.EMPHASIZED);
        final WindowAnimation winAnim = new WindowAnimation(change, 0 /* cornerRadius */,
                sca.getAnimation(), va);
        winAnim.addFinishCallback(finishCallback, mainExecutor);
        return Optional.of(winAnim);
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        return null;
    }

    @Override
    public void setAnimScaleSetting(float scale) {
        mTransitionAnimationScaleSetting = scale;
    }

    @Nullable
    private Animation loadAnimation(@WindowManager.TransitionType int type,
            @NonNull TransitionInfo info, @NonNull TransitionInfo.Change change,
            int wallpaperTransit, boolean isDreamTransition) {
        Animation a;

        final int flags = info.getFlags();
        final int changeMode = change.getMode();
        final int changeFlags = change.getFlags();
        final boolean isOpeningType = TransitionUtil.isOpeningType(type);
        final boolean enter = TransitionUtil.isOpeningType(changeMode);
        final boolean isTask = change.getTaskInfo() != null;
        final TransitionInfo.AnimationOptions options = change.getAnimationOptions();
        final int overrideType = options != null ? options.getType() : ANIM_NONE;
        final int userId = options != null ? options.getUserId() : UserHandle.USER_CURRENT;
        final Rect endBounds = TransitionUtil.isClosingType(changeMode)
                ? mRotator.getEndBoundsInStartRotation(change)
                : change.getEndAbsBounds();

        if (info.isKeyguardGoingAway()) {
            a = mTransitionAnimation.loadKeyguardExitAnimation(flags,
                    (changeFlags & FLAG_SHOW_WALLPAPER) != 0);
        } else if (type == TRANSIT_KEYGUARD_UNOCCLUDE) {
            a = mTransitionAnimation.loadKeyguardUnoccludeAnimation(userId);
        } else if ((changeFlags & FLAG_IS_VOICE_INTERACTION) != 0) {
            if (isOpeningType) {
                a = mTransitionAnimation.loadVoiceActivityOpenAnimation(enter, userId);
            } else {
                a = mTransitionAnimation.loadVoiceActivityExitAnimation(enter, userId);
            }
        } else if (changeMode == TRANSIT_CHANGE) {
            // Apply end state directly by default.
            return null;
        } else if (type == TRANSIT_RELAUNCH) {
            a = mTransitionAnimation.createRelaunchAnimation(endBounds, mInsets, endBounds);
        } else if (overrideType == ANIM_CUSTOM
                && (!isTask || options.getOverrideTaskTransition())) {
            a = mTransitionAnimation.loadAnimationRes(options.getPackageName(), enter
                    ? options.getEnterResId() : options.getExitResId(), userId);
        } else if (overrideType == ANIM_OPEN_CROSS_PROFILE_APPS && enter) {
            a = mTransitionAnimation.loadCrossProfileAppEnterAnimation(userId);
        } else if (overrideType == ANIM_CLIP_REVEAL) {
            a = mTransitionAnimation.createClipRevealAnimationLocked(type, wallpaperTransit, enter,
                    endBounds, endBounds, options.getTransitionBounds());
        } else if (overrideType == ANIM_SCALE_UP) {
            a = mTransitionAnimation.createScaleUpAnimationLocked(type, wallpaperTransit, enter,
                    endBounds, options.getTransitionBounds());
        } else if (overrideType == ANIM_THUMBNAIL_SCALE_UP
                || overrideType == ANIM_THUMBNAIL_SCALE_DOWN) {
            final boolean scaleUp = overrideType == ANIM_THUMBNAIL_SCALE_UP;
            a = mTransitionAnimation.createThumbnailEnterExitAnimationLocked(enter, scaleUp,
                    endBounds, type, wallpaperTransit, options.getThumbnail(),
                    options.getTransitionBounds());
        } else if ((changeFlags & FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT) != 0 && isOpeningType) {
            // This received a transferred starting window, so don't animate
            return null;
        } else if (overrideType == ANIM_SCENE_TRANSITION) {
            // If there's a scene-transition, then jump-cut.
            return null;
        } else {
            a = loadAttributeAnimation(
                    type, info, change, wallpaperTransit, mTransitionAnimation, isDreamTransition);
        }

        if (a != null) {
            if (!a.isInitialized()) {
                final Rect animationRange = TransitionUtil.isClosingType(changeMode)
                        ? change.getStartAbsBounds() : change.getEndAbsBounds();
                a.initialize(animationRange.width(), animationRange.height(),
                        endBounds.width(), endBounds.height());
            }
            a.restrictDuration(MAX_ANIMATION_DURATION);
            a.scaleCurrentDuration(mTransitionAnimationScaleSetting);
        }
        return a;
    }

    private Optional<WindowAnimation> attachThumbnail(
            @NonNull Consumer<WindowAnimation> finishCallback,
            TransitionInfo.Change change, TransitionInfo.AnimationOptions options,
            float cornerRadius) {
        final boolean isOpen = TransitionUtil.isOpeningType(change.getMode());
        final boolean isClose = TransitionUtil.isClosingType(change.getMode());
        if (isOpen) {
            if (options.getType() == ANIM_OPEN_CROSS_PROFILE_APPS) {
                return attachCrossProfileThumbnailAnimation(finishCallback, change,
                        cornerRadius);
            } else if (options.getType() == ANIM_THUMBNAIL_SCALE_UP) {
                return attachThumbnailAnimation(finishCallback, change, options,
                        cornerRadius);
            }
        } else if (isClose && options.getType() == ANIM_THUMBNAIL_SCALE_DOWN) {
            return attachThumbnailAnimation(finishCallback, change, options,
                    cornerRadius);
        }
        return Optional.empty();
    }

    private Optional<WindowAnimation> attachCrossProfileThumbnailAnimation(
            @NonNull Consumer<WindowAnimation> finishCallback,
            TransitionInfo.Change change, float cornerRadius) {
        final Rect bounds = change.getEndAbsBounds();
        // Show the right drawable depending on the user we're transitioning to.
        final Drawable thumbnailDrawable = change.hasFlags(FLAG_CROSS_PROFILE_OWNER_THUMBNAIL)
                ? mContext.getDrawable(R.drawable.ic_account_circle)
                : change.hasFlags(FLAG_CROSS_PROFILE_WORK_THUMBNAIL)
                        ? mEnterpriseThumbnailDrawable : null;
        if (thumbnailDrawable == null) {
            return Optional.empty();
        }
        final HardwareBuffer thumbnail = mTransitionAnimation.createCrossProfileAppsThumbnail(
                thumbnailDrawable, bounds);
        if (thumbnail == null) {
            return Optional.empty();
        }
        final Animation a =
                mTransitionAnimation.createCrossProfileAppsThumbnailAnimationLocked(bounds);

        return startThumbnailAnimation(finishCallback, change, cornerRadius, thumbnail, a);
    }

    private Optional<WindowAnimation> attachThumbnailAnimation(
            @NonNull Consumer<WindowAnimation> finishCallback,
            TransitionInfo.Change change, TransitionInfo.AnimationOptions options,
            float cornerRadius) {
        final Rect bounds = change.getEndAbsBounds();
        final int orientation = mContext.getResources().getConfiguration().orientation;
        final Animation a = mTransitionAnimation.createThumbnailAspectScaleAnimationLocked(bounds,
                mInsets, options.getThumbnail(), orientation, null /* startRect */,
                options.getTransitionBounds(), options.getType() == ANIM_THUMBNAIL_SCALE_UP);

        return startThumbnailAnimation(finishCallback, change, cornerRadius,
                options.getThumbnail(), a);
    }

    private Optional<WindowAnimation> startThumbnailAnimation(
            @NonNull Consumer<WindowAnimation> finishCallback,
            TransitionInfo.Change change, float cornerRadius,
            HardwareBuffer thumbnail, Animation a) {
        if (thumbnail == null || a == null) {
            return Optional.empty();
        }
        final SurfaceControl.Transaction transaction = mTransactionPool.acquire();
        final WindowThumbnail wt = WindowThumbnail.createAndAttach(
                change.getLeash(), thumbnail, transaction);
        final Consumer<WindowAnimation> finisher = (wAnim) -> {
            wt.destroy(transaction);
            mTransactionPool.release(transaction);
            finishCallback.accept(wAnim);
        };
        a.restrictDuration(MAX_ANIMATION_DURATION);
        a.scaleCurrentDuration(mTransitionAnimationScaleSetting);
        return Optional.of(buildWindowAnimation(a, change, wt.getSurface(), finisher,
                mTransactionPool, mMainExecutor, change.getEndRelOffset(), cornerRadius,
                change.getEndAbsBounds(), getRoundedContentBounds(change)));
    }

    private static int getWallpaperTransitType(TransitionInfo info) {
        boolean hasWallpaper = false;
        boolean hasOpenWallpaper = false;
        boolean hasCloseWallpaper = false;

        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if ((change.getFlags() & FLAG_SHOW_WALLPAPER) != 0
                    || (change.getFlags() & FLAG_IS_WALLPAPER) != 0) {
                hasWallpaper = true;
                if (TransitionUtil.isOpeningType(change.getMode())) {
                    hasOpenWallpaper = true;
                } else if (TransitionUtil.isClosingType(change.getMode())) {
                    hasCloseWallpaper = true;
                }
            }
        }

        if (hasOpenWallpaper && hasCloseWallpaper) {
            return TransitionUtil.isOpeningType(info.getType())
                    ? WALLPAPER_TRANSITION_INTRA_OPEN : WALLPAPER_TRANSITION_INTRA_CLOSE;
        } else if (hasOpenWallpaper) {
            return WALLPAPER_TRANSITION_OPEN;
        } else if (hasCloseWallpaper) {
            return WALLPAPER_TRANSITION_CLOSE;
        } else if (hasWallpaper) {
            return WALLPAPER_TRANSITION_CHANGE;
        } else {
            return WALLPAPER_TRANSITION_NONE;
        }
    }

    /**
     * Returns {@code true} if the default transition handler can run the override animation.
     *
     * @see #loadAnimation(int, TransitionInfo, TransitionInfo.Change, int, boolean)
     */
    public static boolean isSupportedOverrideAnimation(
            @NonNull TransitionInfo.AnimationOptions options) {
        final int animType = options.getType();
        return animType == ANIM_CUSTOM || animType == ANIM_SCALE_UP
                || animType == ANIM_THUMBNAIL_SCALE_UP || animType == ANIM_THUMBNAIL_SCALE_DOWN
                || animType == ANIM_CLIP_REVEAL || animType == ANIM_OPEN_CROSS_PROFILE_APPS
                || animType == ANIM_FROM_STYLE;
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @Nullable SurfaceControl.Transaction finishTransaction) {
        mInteractionJankMonitor.cancel(CUJ_DEFAULT_TASK_TO_TASK_ANIMATION);
        mFinishCallbacks.remove(transition);
    }

}
