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

package com.android.wm.shell.pip2.tv;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;

import static com.android.wm.shell.pip2.tv.TvPipTransition.ANIMATING_BOUNDS_CHANGE_DURATION;
import static com.android.wm.shell.pip2.tv.TvPipTransition.PIP_DESTINATION_BOUNDS;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.RemoteAction;
import android.app.TaskInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.WindowManagerShellWrapper;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TaskStackListenerCallback;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.pip.PipAppOpsListener;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMediaController;
import com.android.wm.shell.pip.PinnedStackListenerForwarder;
import com.android.wm.shell.pip.Pip;
import com.android.wm.shell.pip.PipParamsChangedForwarder;
import com.android.wm.shell.pip.tv.TvPipAction;
import com.android.wm.shell.pip.tv.TvPipActionsProvider;
import com.android.wm.shell.pip.tv.TvPipBoundsAlgorithm;
import com.android.wm.shell.pip.tv.TvPipBoundsController;
import com.android.wm.shell.pip.tv.TvPipBoundsState;
import com.android.wm.shell.pip.tv.TvPipMenuController;
import com.android.wm.shell.pip.tv.TvPipNotificationController;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.sysui.ConfigurationChangeListener;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.sysui.UserChangeListener;

import java.util.List;
import java.util.Set;

/**
 * Manages the picture-in-picture (PIP) UI and states.
 */
public class TvPipController implements PipTransitionState.PipTransitionStateChangedListener,
        TvPipBoundsController.PipBoundsListener, TvPipMenuController.Delegate,
        DisplayController.OnDisplaysChangedListener, ConfigurationChangeListener,
        UserChangeListener {
    private static final String TAG = "TvPip2Controller";

    static final String ACTION_SHOW_PIP_MENU =
            "com.android.wm.shell.pip.tv.notification.action.SHOW_PIP_MENU";
    static final String ACTION_CLOSE_PIP =
            "com.android.wm.shell.pip.tv.notification.action.CLOSE_PIP";
    static final String ACTION_MOVE_PIP =
            "com.android.wm.shell.pip.tv.notification.action.MOVE_PIP";
    static final String ACTION_TOGGLE_EXPANDED_PIP =
            "com.android.wm.shell.pip.tv.notification.action.TOGGLE_EXPANDED_PIP";
    static final String ACTION_TO_FULLSCREEN =
            "com.android.wm.shell.pip.tv.notification.action.FULLSCREEN";

    private final Context mContext;

    private final ShellController mShellController;
    private final TvPipBoundsState mTvPipBoundsState;
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final TvPipBoundsAlgorithm mTvPipBoundsAlgorithm;
    private final TvPipBoundsController mTvPipBoundsController;
    private final PipTransitionState mPipTransitionState;
    private final PipAppOpsListener mAppOpsListener;
    private final TvPipScheduler mTvPipScheduler;
    private final PipMediaController mPipMediaController;
    private final TvPipActionsProvider mTvPipActionsProvider;
    private final TvPipNotificationController mPipNotificationController;
    private final TvPipMenuController mTvPipMenuController;
    private final TaskStackListenerImpl mTaskStackListener;
    private final PipParamsChangedForwarder mPipParamsChangedForwarder;
    private final DisplayController mDisplayController;
    private final WindowManagerShellWrapper mWmShellWrapper;
    private final ShellExecutor mMainExecutor;
    private final Handler mMainHandler; // For registering the broadcast receiver
    private final TvPipImpl mImpl = new TvPipImpl();

    private final ActionBroadcastReceiver mActionBroadcastReceiver;

    // How long the shell will wait for the app to close the PiP if a custom action is set.
    private int mPipForceCloseDelay;

    private int mResizeAnimationDuration;
    private int mEduTextWindowExitAnimationDuration;

    /**
     * Instantiates {@link TvPipController}, returns {@code null} if the feature not supported.
     */
    public static TvPipImpl create(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            TvPipBoundsState tvPipBoundsState,
            PipDisplayLayoutState pipDisplayLayoutState,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            TvPipBoundsController tvPipBoundsController,
            PipTransitionState pipTransitionState,
            PipAppOpsListener pipAppOpsListener,
            TvPipScheduler tvPipScheduler,
            TvPipMenuController tvPipMenuController,
            PipMediaController pipMediaController,
            TvPipNotificationController pipNotificationController,
            TaskStackListenerImpl taskStackListener,
            PipParamsChangedForwarder pipParamsChangedForwarder,
            DisplayController displayController,
            WindowManagerShellWrapper wmShell,
            Handler mainHandler,
            ShellExecutor mainExecutor) {
        if (!context.getPackageManager().hasSystemFeature(FEATURE_PICTURE_IN_PICTURE)) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Device doesn't support Pip feature", TAG);
            return null;
        }
        return new TvPipController(
                context,
                shellInit,
                shellController,
                tvPipBoundsState,
                pipDisplayLayoutState,
                tvPipBoundsAlgorithm,
                tvPipBoundsController,
                pipTransitionState,
                pipAppOpsListener,
                tvPipScheduler,
                tvPipMenuController,
                pipMediaController,
                pipNotificationController,
                taskStackListener,
                pipParamsChangedForwarder,
                displayController,
                wmShell,
                mainHandler,
                mainExecutor).mImpl;
    }

    private TvPipController(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            TvPipBoundsState tvPipBoundsState,
            PipDisplayLayoutState pipDisplayLayoutState,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            TvPipBoundsController tvPipBoundsController,
            PipTransitionState pipTransitionState,
            PipAppOpsListener pipAppOpsListener,
            TvPipScheduler tvPipScheduler,
            TvPipMenuController tvPipMenuController,
            PipMediaController pipMediaController,
            TvPipNotificationController pipNotificationController,
            TaskStackListenerImpl taskStackListener,
            PipParamsChangedForwarder pipParamsChangedForwarder,
            DisplayController displayController,
            WindowManagerShellWrapper wmShellWrapper,
            Handler mainHandler,
            ShellExecutor mainExecutor) {
        mContext = context;
        mPipTransitionState = pipTransitionState;
        mPipTransitionState.addPipTransitionStateChangedListener(this);
        mMainHandler = mainHandler;
        mMainExecutor = mainExecutor;
        mShellController = shellController;
        mDisplayController = displayController;

        mTvPipBoundsState = tvPipBoundsState;

        DisplayLayout layout = new DisplayLayout(context, context.getDisplay());
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipDisplayLayoutState.setDisplayLayout(layout);
        mPipDisplayLayoutState.setDisplayId(context.getDisplayId());

        mTvPipBoundsAlgorithm = tvPipBoundsAlgorithm;
        mTvPipBoundsController = tvPipBoundsController;
        mTvPipBoundsController.setListener(this);

        mPipMediaController = pipMediaController;
        mTvPipActionsProvider = new TvPipActionsProvider(context, pipMediaController,
                this::executeAction);

        mPipNotificationController = pipNotificationController;
        mPipNotificationController.setTvPipActionsProvider(mTvPipActionsProvider);

        mTvPipMenuController = tvPipMenuController;
        mTvPipMenuController.setDelegate(this);
        mTvPipMenuController.setTvPipActionsProvider(mTvPipActionsProvider);

        mActionBroadcastReceiver = new ActionBroadcastReceiver();

        mAppOpsListener = pipAppOpsListener;
        mTvPipScheduler = tvPipScheduler;
        mPipParamsChangedForwarder = pipParamsChangedForwarder;
        mTaskStackListener = taskStackListener;
        mWmShellWrapper = wmShellWrapper;
        if (PipFlags.isPip2ExperimentEnabled()) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    private void onInit() {
        reloadResources();

        registerPipParamsChangedListener(mPipParamsChangedForwarder);
        registerTaskStackListenerCallback(mTaskStackListener);
        registerWmShellPinnedStackListener(mWmShellWrapper);
        registerSessionListenerForCurrentUser();
        mDisplayController.addDisplayWindowListener(this);

        mShellController.addConfigurationChangeListener(this);
        mShellController.addUserChangeListener(this);

        mAppOpsListener.setCallback(this::closePip);
    }

    @Override
    public void onUserChanged(int newUserId, @NonNull Context userContext) {
        // Re-register the media session listener when switching users
        registerSessionListenerForCurrentUser();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onConfigurationChanged()", TAG);

        int previousDefaultGravityX = mTvPipBoundsState.getDefaultGravity()
                & Gravity.HORIZONTAL_GRAVITY_MASK;

        reloadResources();

        mPipNotificationController.onConfigurationChanged();
        mTvPipBoundsAlgorithm.onConfigurationChanged(mContext);
        mTvPipBoundsState.onConfigurationChanged();
        mPipDisplayLayoutState.onConfigurationChanged();

        int defaultGravityX = mTvPipBoundsState.getDefaultGravity()
                & Gravity.HORIZONTAL_GRAVITY_MASK;
        if (mPipTransitionState.isInPip() && previousDefaultGravityX != defaultGravityX) {
            movePipToOppositeSide();
        }
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onDensityOrFontScaleChanged()", TAG);
        updatePinnedStackBounds();
        mTvPipMenuController.reloadMenu();
    }

    @Override
    public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onDisplayConfigurationChanged(), displayId %d, saved display id %d",
                TAG, displayId, mPipDisplayLayoutState.getDisplayId());
        mPipDisplayLayoutState.setDisplayLayout(
                new DisplayLayout(mContext, mContext.getDisplay()));
        mPipDisplayLayoutState.setDisplayId(mContext.getDisplayId());
    }

    private void reloadResources() {
        final Resources res = mContext.getResources();
        mResizeAnimationDuration = res.getInteger(R.integer.config_pipResizeAnimationDuration);
        mPipForceCloseDelay = res.getInteger(R.integer.config_pipForceCloseDelay);
        mEduTextWindowExitAnimationDuration =
                res.getInteger(R.integer.pip_edu_text_window_exit_animation_duration);
    }

    private void movePipToOppositeSide() {
        ProtoLog.i(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: movePipToOppositeSide", TAG);
        if ((mTvPipBoundsState.getTvPipGravity() & Gravity.RIGHT) == Gravity.RIGHT) {
            movePip(KEYCODE_DPAD_LEFT);
        } else if ((mTvPipBoundsState.getTvPipGravity() & Gravity.LEFT) == Gravity.LEFT) {
            movePip(KEYCODE_DPAD_RIGHT);
        }
    }

    /**
     * Starts the process if bringing up the Pip menu if by issuing a command to move Pip
     * task/window to the "Menu" position. We'll show the actual Menu UI (eg. actions) once the Pip
     * task/window is properly positioned in {@link #onPipTransitionStateChanged(int, int, Bundle)}.
     *
     * @param moveMenu If true, show the moveMenu, otherwise show the regular menu.
     */
    private void showPictureInPictureMenu(boolean moveMenu) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: showPictureInPictureMenu()", TAG);

        if (!mPipTransitionState.isInPip()) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s:  > cannot open Menu from the current state.", TAG);
            return;
        }

        if (moveMenu) {
            mTvPipMenuController.showMovementMenu();
        } else {
            mTvPipMenuController.showMenu();
        }
        updatePinnedStackBounds();
    }

    @Override
    public void onMenuClosed() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: closeMenu()", TAG);
        updatePinnedStackBounds();
    }

    @Override
    public void onInMoveModeChanged() {
        updatePinnedStackBounds();
    }

    /**
     * Opens the "Pip-ed" Activity fullscreen.
     */
    private void movePipToFullscreen(boolean wasVisible) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: movePipToFullscreen()", TAG);

        mTvPipMenuController.promoteMenuToTop();
        mTvPipScheduler.scheduleExitPipViaExpand(wasVisible);
    }

    private void togglePipExpansion() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: togglePipExpansion()", TAG);
        boolean expanding = !mTvPipBoundsState.isTvPipExpanded();
        mTvPipBoundsState.setTvPipManuallyCollapsed(!expanding);
        mTvPipBoundsAlgorithm.updateGravityOnExpansionToggled(expanding);
        mTvPipBoundsState.setTvPipExpanded(expanding);

        updatePinnedStackBounds();
    }

    @Override
    public void movePip(int keycode) {
        if (mTvPipBoundsAlgorithm.updateGravity(keycode)) {
            mTvPipMenuController.updateGravity(mTvPipBoundsState.getTvPipGravity());
            updatePinnedStackBounds();
        } else {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Position hasn't changed", TAG);
        }
    }

    @Override
    public void onKeepClearAreasChanged(int displayId, Set<Rect> restricted,
            Set<Rect> unrestricted) {
        if (mPipDisplayLayoutState.getDisplayId() == displayId) {
            mTvPipBoundsState.setKeepClearAreas(restricted, unrestricted);
            updatePinnedStackBounds(mResizeAnimationDuration, /* immediate= */ false);
        }
    }

    private void updatePinnedStackBounds() {
        updatePinnedStackBounds(mResizeAnimationDuration, true);
    }

    /**
     * Update the PiP bounds based on the state of the PiP and keep clear areas.
     */
    private void updatePinnedStackBounds(int animationDuration, boolean immediate) {
        if (!mPipTransitionState.isInPip()) {
            return;
        }
        final boolean stayAtAnchorPosition = mTvPipMenuController.isInMoveMode();
        final boolean disallowStashing = mTvPipMenuController.isMenuOpen() || stayAtAnchorPosition;
        mTvPipBoundsController.recalculatePipBounds(stayAtAnchorPosition, disallowStashing,
                animationDuration, immediate);
    }

    @Override
    public void onPipTargetBoundsChange(Rect targetBounds, int animationDuration) {
        if (!mPipTransitionState.isInPip()) {
            // Do not schedule a move animation while we're still transitioning into/out of PiP
            return;
        }
        mPipTransitionState.setOnIdlePipTransitionStateRunnable(() -> {
            Bundle extra = new Bundle();
            extra.putParcelable(PIP_DESTINATION_BOUNDS, targetBounds);
            extra.putInt(ANIMATING_BOUNDS_CHANGE_DURATION, animationDuration);

            mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
        });
    }

    /**
     * Closes Pip window.
     */
    public void closePip() {
        closeCurrentPiP(mPipTransitionState.getPipTaskInfo().taskId);
    }

    /**
     * Force close the current PiP after some time in case the custom action hasn't done it by
     * itself.
     */
    public void customClosePip() {
        mMainExecutor.executeDelayed(() -> closeCurrentPiP(
                mPipTransitionState.getPipTaskInfo().taskId), mPipForceCloseDelay);
    }

    private void closeCurrentPiP(int pinnedTaskId) {
        if (mPipTransitionState.getPipTaskInfo().taskId != pinnedTaskId) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: PiP has already been closed by custom close action", TAG);
            return;
        }
        mTvPipMenuController.promoteMenuToTop();
        mTvPipScheduler.scheduleRemovePip(/* withFadeout= */ true);
        mTvPipMenuController.closeMenu();
        mPipNotificationController.dismiss();
    }

    @Override
    public void closeEduText() {
        updatePinnedStackBounds(mEduTextWindowExitAnimationDuration, false);
    }

    private void registerSessionListenerForCurrentUser() {
        mPipMediaController.registerSessionListenerForCurrentUser();
    }

    @Override
    public void onPipTransitionStateChanged(@PipTransitionState.TransitionState int oldState,
            @PipTransitionState.TransitionState int newState,
            @Nullable Bundle extra) {
        switch (newState) {
            case PipTransitionState.ENTERED_PIP:
                // This logic replaces the old onPipTransitionFinished() and onActivityPinned().
                mTvPipMenuController.onPipTransitionFinished(/* enterTransition= */ true);
                mTvPipActionsProvider.updatePipExpansionState(mTvPipBoundsState.isTvPipExpanded());

                final TaskInfo pinnedTask = mPipTransitionState.getPipTaskInfo();
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: Current pinned task=%s", TAG, pinnedTask);
                if (pinnedTask == null || pinnedTask.topActivity == null) return;

                mPipMediaController.onActivityPinned();
                mActionBroadcastReceiver.register();
                mPipNotificationController.show(pinnedTask.topActivity.getPackageName());
                mTvPipBoundsController.reset();
                mAppOpsListener.onActivityPinned(pinnedTask.topActivity.getPackageName());
                break;
            case PipTransitionState.ENTERING_PIP:
                // This logic replaces the old onPipTransitionStarted()
                updateExpansionState();
                break;
            case PipTransitionState.EXITED_PIP:
                // This logic replaces the old onPipTransitionFinished(), onPipDisappeared() and
                // onActivityUnpinned()
                mTvPipMenuController.onPipTransitionFinished(/* enterTransition= */ false);
                mTvPipActionsProvider.updatePipExpansionState(mTvPipBoundsState.isTvPipExpanded());

                mAppOpsListener.onActivityUnpinned();
                mPipNotificationController.dismiss();
                mActionBroadcastReceiver.unregister();

                mTvPipMenuController.detach();
                mTvPipActionsProvider.reset();
                mTvPipBoundsState.resetTvPipState();
                mTvPipBoundsController.reset();
                break;
            case PipTransitionState.SCHEDULED_BOUNDS_CHANGE:
                Rect toBounds = extra.getParcelable(PIP_DESTINATION_BOUNDS, Rect.class);
                int duration = extra.getInt(ANIMATING_BOUNDS_CHANGE_DURATION);
                if (toBounds != null) {
                    mTvPipScheduler.scheduleAnimateResizePip(toBounds, duration);
                    mTvPipMenuController.onPipTransitionToTargetBoundsStarted(toBounds);
                }
                break;
            case PipTransitionState.CHANGED_PIP_BOUNDS:
                mTvPipMenuController.onPipTransitionFinished(/* enterTransition= */ false);
                mTvPipActionsProvider.updatePipExpansionState(mTvPipBoundsState.isTvPipExpanded());
                break;
        }
    }

    private void updateExpansionState() {
        mTvPipActionsProvider.updateExpansionEnabled(mTvPipBoundsState.isTvExpandedPipSupported()
                && mTvPipBoundsState.getDesiredTvExpandedAspectRatio() != 0);
    }

    private void registerTaskStackListenerCallback(TaskStackListenerImpl taskStackListener) {
        taskStackListener.addListener(new TaskStackListenerCallback() {
            @Override
            public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                    boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
                if (task.getWindowingMode() == WINDOWING_MODE_PINNED) {
                    ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: onPinnedActivityRestartAttempt()", TAG);
                    // If the "Pip-ed" Activity is launched again by Launcher or intent, make it
                    // fullscreen.
                    movePipToFullscreen(wasVisible);
                }
            }
        });
    }

    private void registerPipParamsChangedListener(PipParamsChangedForwarder provider) {
        provider.addListener(new PipParamsChangedForwarder.PipParamsChangedCallback() {
            @Override
            public void onActionsChanged(List<RemoteAction> actions,
                    RemoteAction closeAction) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: onActionsChanged()", TAG);

                mTvPipActionsProvider.setAppActions(actions, closeAction);
            }

            @Override
            public void onAspectRatioChanged(float ratio) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: onAspectRatioChanged: %f", TAG, ratio);

                mTvPipBoundsState.setAspectRatio(ratio);
                if (!mTvPipBoundsState.isTvPipExpanded()) {
                    updatePinnedStackBounds();
                }
            }

            @Override
            public void onExpandedAspectRatioChanged(float ratio) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: onExpandedAspectRatioChanged: %f", TAG, ratio);

                if (!mTvPipBoundsState.isTvExpandedPipSupported()) {
                    return;
                }

                mTvPipBoundsState.setDesiredTvExpandedAspectRatio(ratio, false);
                updateExpansionState();

                // 1) PiP is expanded and only aspect ratio changed, but wasn't disabled
                // --> update bounds, but don't toggle
                if (mTvPipBoundsState.isTvPipExpanded() && ratio != 0) {
                    mTvPipBoundsAlgorithm.updateExpandedPipSize();
                    updatePinnedStackBounds();
                }

                // 2) PiP is expanded, but expanded PiP was disabled
                // --> collapse PiP
                if (mTvPipBoundsState.isTvPipExpanded() && ratio == 0) {
                    mTvPipBoundsAlgorithm.updateGravityOnExpansionToggled(/* expanding= */ false);
                    mTvPipBoundsState.setTvPipExpanded(false);
                    updatePinnedStackBounds();
                }

                // 3) PiP not expanded and not manually collapsed and expand was enabled
                // --> expand to new ratio
                if (!mTvPipBoundsState.isTvPipExpanded() && ratio != 0
                        && !mTvPipBoundsState.isTvPipManuallyCollapsed()) {
                    mTvPipBoundsAlgorithm.updateExpandedPipSize();
                    mTvPipBoundsAlgorithm.updateGravityOnExpansionToggled(/* expanding= */ true);
                    mTvPipBoundsState.setTvPipExpanded(true);
                    updatePinnedStackBounds();
                }
            }
        });
    }

    private void registerWmShellPinnedStackListener(WindowManagerShellWrapper wmShell) {
        try {
            wmShell.addPinnedStackListener(new PinnedStackListenerForwarder.PinnedTaskListener() {
                @Override
                public void onImeVisibilityChanged(boolean imeVisible, int imeHeight) {
                    ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: onImeVisibilityChanged(), visible=%b, height=%d",
                            TAG, imeVisible, imeHeight);

                    if (imeVisible == mTvPipBoundsState.isImeShowing()
                            && (!imeVisible || imeHeight == mTvPipBoundsState.getImeHeight())) {
                        // Nothing changed: either IME has been and remains invisible, or remains
                        // visible with the same height.
                        return;
                    }
                    mTvPipBoundsState.setImeVisibility(imeVisible, imeHeight);

                    if (mPipTransitionState.isInPip()) {
                        updatePinnedStackBounds();
                    }
                }
            });
        } catch (RemoteException e) {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Failed to register pinned stack listener, %s", TAG, e);
        }
    }

    private static TaskInfo getPinnedTaskInfo() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: getPinnedTaskInfo()", TAG);
        try {
            final TaskInfo taskInfo = ActivityTaskManager.getService().getRootTaskInfo(
                    WINDOWING_MODE_PINNED, ACTIVITY_TYPE_UNDEFINED);
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: taskInfo=%s", TAG, taskInfo);
            return taskInfo;
        } catch (RemoteException e) {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: getRootTaskInfo() failed, %s", TAG, e);
            return null;
        }
    }

    private void executeAction(@TvPipAction.ActionType int actionType) {
        switch (actionType) {
            case TvPipAction.ACTION_FULLSCREEN:
                movePipToFullscreen(/* wasVisible= */ true);
                break;
            case TvPipAction.ACTION_CLOSE:
                closePip();
                break;
            case TvPipAction.ACTION_MOVE:
                showPictureInPictureMenu(/* moveMenu= */ true);
                break;
            case TvPipAction.ACTION_CUSTOM_CLOSE:
                customClosePip();
                break;
            case TvPipAction.ACTION_EXPAND_COLLAPSE:
                togglePipExpansion();
                break;
            default:
                // NOOP
                break;
        }
    }

    private class ActionBroadcastReceiver extends BroadcastReceiver {
        private static final String SYSTEMUI_PERMISSION = "com.android.systemui.permission.SELF";

        final IntentFilter mIntentFilter;

        {
            mIntentFilter = new IntentFilter();
            mIntentFilter.addAction(ACTION_CLOSE_PIP);
            mIntentFilter.addAction(ACTION_SHOW_PIP_MENU);
            mIntentFilter.addAction(ACTION_MOVE_PIP);
            mIntentFilter.addAction(ACTION_TOGGLE_EXPANDED_PIP);
            mIntentFilter.addAction(ACTION_TO_FULLSCREEN);
        }

        boolean mRegistered = false;

        void register() {
            if (mRegistered) return;

            mContext.registerReceiverForAllUsers(this, mIntentFilter, SYSTEMUI_PERMISSION,
                    mMainHandler, Context.RECEIVER_NOT_EXPORTED);
            mRegistered = true;
        }

        void unregister() {
            if (!mRegistered) return;

            mContext.unregisterReceiver(this);
            mRegistered = false;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: on(Broadcast)Receive(), action=%s", TAG, action);

            if (ACTION_SHOW_PIP_MENU.equals(action)) {
                showPictureInPictureMenu(/* moveMenu= */ false);
            } else {
                executeAction(getCorrespondingActionType(action));
            }
        }

        @TvPipAction.ActionType
        private int getCorrespondingActionType(String broadcast) {
            if (ACTION_CLOSE_PIP.equals(broadcast)) {
                return TvPipAction.ACTION_CLOSE;
            } else if (ACTION_MOVE_PIP.equals(broadcast)) {
                return TvPipAction.ACTION_MOVE;
            } else if (ACTION_TOGGLE_EXPANDED_PIP.equals(broadcast)) {
                return TvPipAction.ACTION_EXPAND_COLLAPSE;
            } else if (ACTION_TO_FULLSCREEN.equals(broadcast)) {
                return TvPipAction.ACTION_FULLSCREEN;
            }

            // Default: handle it like an action we don't know the content of.
            return TvPipAction.ACTION_CUSTOM;
        }
    }

    public class TvPipImpl implements Pip {
        // Not used
    }
}
