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

package com.android.wm.shell.transition;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.os.UserHandle.USER_NULL;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY;
import static android.view.WindowManager.TRANSIT_WAKE;
import static android.window.TransitionInfo.FLAG_BACK_GESTURE_ANIMATED;
import static android.window.TransitionInfo.FLAG_MOVED_TO_TOP;

import static com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_CONVERT_TO_BUBBLE;
import static com.android.wm.shell.transition.Transitions.TransitionObserver;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.os.IBinder;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SingleInstanceRemoteListener;
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer;
import com.android.wm.shell.shared.IHomeTransitionListener;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.bubbles.BubbleFlagHelper;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The {@link TransitionObserver} that observes for transitions involving the home
 * activity on the {@link android.view.Display#DEFAULT_DISPLAY} only.
 * It reports transitions to the caller via {@link IHomeTransitionListener}.
 */
public class HomeTransitionObserver implements TransitionObserver,
        RemoteCallable<HomeTransitionObserver> {
    private SingleInstanceRemoteListener<HomeTransitionObserver, IHomeTransitionListener>
            mListener;

    private @NonNull final Context mContext;
    private @NonNull final ShellExecutor mMainExecutor;
    private @NonNull final DisplayInsetsController mDisplayInsetsController;
    private @NonNull final ShellController mShellController;
    private final Optional<DesksOrganizer> mDesksOrganizer;
    private IBinder mPendingStartDragTransition;

    private final boolean mTrackDesktopVisibility;

    private int mListenerUserId = USER_NULL; // UserId associated with the registered listener.
    private final Map<Integer, UpdateParameters> mHomeUpdateForUser = new HashMap<>();
    private final Map<Integer, Boolean> mDesktopVisibilityForUser = new HashMap<>();


    public HomeTransitionObserver(@NonNull Context context,
            @NonNull ShellExecutor mainExecutor,
            @NonNull DisplayInsetsController displayInsetsController,
            @NonNull ShellController shellController,
            @NonNull ShellInit shellInit,
            @NonNull DesktopState desktopState,
            Optional<DesksOrganizer> desksOrganizer) {
        mContext = context;
        mMainExecutor = mainExecutor;
        mDisplayInsetsController = displayInsetsController;
        mShellController = shellController;
        mDesksOrganizer = desksOrganizer;

        mTrackDesktopVisibility = desktopState.getShouldShowHomeBehindDesktop();

        shellInit.addInitCallback(this::onInit, this);
    }

    private void onInit() {
        mDisplayInsetsController.addInsetsChangedListener(DEFAULT_DISPLAY,
                new DisplayInsetsController.OnInsetsChangedListener() {
                    @Override
                    public void insetsChanged(InsetsState insetsState) {
                        if (mListener == null) return;
                        mListener.call(l -> l.onDisplayInsetsChanged(insetsState));
                    }
                });
    }

    @Override
    public void onTransitionReady(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        Boolean desktopUpdate = updateDesktopVisibilityForUser(info);
        UpdateParameters homeStateUpdate = updateHomeVisibilityForUser(info);

        boolean homeStateChanged = homeStateUpdate != null;
        if (homeStateUpdate == null) {
            homeStateUpdate = mHomeUpdateForUser.get(mListenerUserId);
        }

        // Desktop visibility updates are only relevant if home is visible - skip sending an
        // update if home visibility did not change, and home is not visible.
        boolean ignoreDesktopStateChange = homeStateUpdate != null && !homeStateUpdate.mIsVisible;
        boolean desktopChanged = desktopUpdate != null && !ignoreDesktopStateChange;

        // When converting to a bubble without a change to home visibility in the transition, force
        // an update using the value from the start of drag.
        boolean requiresUpdate = BubbleFlagHelper.enableBubbleToFullscreen()
                && info.getType() == TRANSIT_CONVERT_TO_BUBBLE
                && mPendingStartDragTransition != null;

        if (!requiresUpdate && !desktopChanged && !homeStateChanged) {
            return;
        }

        if (info.getType() == TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP) {
            // Do not apply at the start of desktop drag as that updates launcher UI visibility.
            // Store the value and apply with a next transition or when cancelling the
            // desktop-drag transition.
            storePendingStartDragTransition(transition);
            return;
        }

        if (mTrackDesktopVisibility && desktopUpdate == null) {
            desktopUpdate = mDesktopVisibilityForUser.get(mListenerUserId);
        }

        mPendingStartDragTransition = null;
        notifyHomeVisibilityChangedForUpdates(homeStateUpdate, desktopUpdate);
    }

    private void storePendingStartDragTransition(IBinder transition) {
        mPendingStartDragTransition = transition;
    }

    /**
     * Determines if a given transition represents a change in home visibility for the current user.
     * <p>
     * Only returns the new state for the current user if it is in the transition.
     * <p>
     * If a change is a visibility change for any user, it is cached in
     * {@link #mHomeUpdateForUser} for pending transitions or when registering a listener.
     *
     * @param info The information about the transition.
     * @return Considering the current user, an {@link UpdateParameters} object whose visibility is
     *         {@code true} if its home activity is becoming visible and {@code false} if invisible,
     *         plus whether the transition involves Keyguard going away. If this change does not
     *         involve the home visibility, the method returns null.
     */
    private UpdateParameters updateHomeVisibilityForUser(TransitionInfo info) {
        UpdateParameters homeStateUpdate = null;
        for (TransitionInfo.Change change : info.getChanges()) {
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null
                    || taskInfo.displayId != DEFAULT_DISPLAY
                    || taskInfo.taskId == -1
                    || !taskInfo.isRunning) {
                continue;
            }
            Boolean visibilityUpdate = getHomeVisibilityUpdateForChange(info, change, taskInfo);
            if (visibilityUpdate != null) {
                boolean keyguardGoingAway =
                        (info.getFlags() & TRANSIT_FLAG_KEYGUARD_GOING_AWAY) != 0;
                boolean waking = info.getType() == TRANSIT_WAKE;
                UpdateParameters update =
                        new UpdateParameters(visibilityUpdate, keyguardGoingAway, waking);
                mHomeUpdateForUser.put(taskInfo.userId, update);
                if (taskInfo.userId == mListenerUserId) {
                    homeStateUpdate = update;
                }
            }
        }
        return homeStateUpdate;
    }
    /**
     * If expected to track desktop visibility, determines if a given transition represents a change
     * in desktop visibility for the current user.
     * <p>
     * Only returns the new state for the current user if the transition is for this user.
     * <p>
     * If a change is a visibility change for any user, it is cached in
     * {@link #mDesktopVisibilityForUser} for pending transitions or when registering a listener.
     *
     * @param info The information about the transition.
     * @return Considering the current user, an {@link Boolean} object whose visibility is
     *         {@code true} if a desk is becoming visible and {@code false} if invisible.
     *         If this change does not involve desk visibility, the method returns null.
     *         Returns null if the observer is not configured to track desktop visibility - i.e.
     *         when home is not visible behind desktop windows.
     */
    private Boolean updateDesktopVisibilityForUser(TransitionInfo info) {
        if (!mTrackDesktopVisibility) {
            return null;
        }

        Boolean update = null;
        for (TransitionInfo.Change change : info.getChanges()) {
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null
                    || taskInfo.displayId != DEFAULT_DISPLAY
                    || taskInfo.taskId == -1) {
                continue;
            }
            Boolean visibilityUpdate = getDeskVisibilityUpdateForChange(change);
            if (visibilityUpdate != null) {
                // NOTE: Unable to use user ID from task info because desk root tasks may have
                // the system user ID.
                int userId = mShellController.getCurrentUserId();
                mDesktopVisibilityForUser.put(userId, visibilityUpdate);
                if (userId == mListenerUserId) {
                    update = visibilityUpdate;
                }
            }
        }

        return update;
    }

    /**
     * Determines if a given transition change for a task represents a change in home visibility.
     *
     * @param info The information about the transition.
     * @param change The specific change within the transition.
     * @param taskInfo The information about the task associated with the change.
     * @return {@code true} if the home activity is becoming visible, {@code false} if it's becoming
     *         invisible, or {@code null} if this change does not affect home visibility.
     */
    private Boolean getHomeVisibilityUpdateForChange(TransitionInfo info,
            TransitionInfo.Change change, ActivityManager.RunningTaskInfo taskInfo) {
        final int mode = change.getMode();
        final boolean isBackGesture = change.hasFlags(FLAG_BACK_GESTURE_ANIMATED);
        if (taskInfo.getActivityType() == ACTIVITY_TYPE_HOME) {
            final boolean gestureToHomeTransition = isBackGesture
                    && TransitionUtil.isClosingType(info.getType());
            if (gestureToHomeTransition || TransitionUtil.isClosingMode(mode)
                    || (!isBackGesture && TransitionUtil.isOpeningMode(mode))) {
                return gestureToHomeTransition || TransitionUtil.isOpeningType(mode);
            }
        }
        return null;
    }

    private Boolean getDeskVisibilityUpdateForChange(TransitionInfo.Change change) {
        if (mDesksOrganizer.isEmpty() || !mDesksOrganizer.get().isDeskChange(change)) {
            return null;
        }
        final int mode = change.getMode();
        if (TransitionUtil.isClosingMode(mode)) {
            return false;
        }
        if (TransitionUtil.isOpeningMode(mode) || change.hasFlags(FLAG_MOVED_TO_TOP)) {
            return true;
        }
        return null;
    }

    @Override
    public void onTransitionStarting(@NonNull IBinder transition) {}

    @Override
    public void onTransitionMerged(@NonNull IBinder merged,
            @NonNull IBinder playing) {}

    @Override
    public void onTransitionFinished(@NonNull IBinder transition,
            boolean aborted) {
        // Handle the case where the DragToDesktop START transition is interrupted and we never
        // receive a CANCEL/END transition.
        if (mPendingStartDragTransition == null
                || mPendingStartDragTransition != transition) {
            return;
        }
        mPendingStartDragTransition = null;

        UpdateParameters pendingHomeState = mHomeUpdateForUser.get(mListenerUserId);
        Boolean pendingDesktopVisibility =
                mTrackDesktopVisibility ? mDesktopVisibilityForUser.get(mListenerUserId) : null;
        notifyHomeVisibilityChangedForUpdates(pendingHomeState, pendingDesktopVisibility);
    }

    /**
     * Sets the home transition listener that receives any transitions resulting in a change of
     *
     */
    public void setHomeTransitionListener(Transitions transitions, IHomeTransitionListener listener,
            int userId) {
        if (mListener == null) {
            mListener = new SingleInstanceRemoteListener<>(this,
                    c -> transitions.registerObserver(this),
                    c -> transitions.unregisterObserver(this));
        }

        if (listener != null) {
            mListenerUserId = userId;
            mListener.register(listener);
            UpdateParameters pendingHomeState = mHomeUpdateForUser.get(userId);
            Boolean pendingDesktopVisibility =
                    mTrackDesktopVisibility ? mDesktopVisibilityForUser.get(userId) : null;
            notifyHomeVisibilityChangedForUpdates(pendingHomeState, pendingDesktopVisibility);
        } else {
            mListener.unregister();
            mListenerUserId = USER_NULL;
        }
    }

    /**
     * Notifies the listener that the home visibility has changed.
     * @param isVisible true when home activity is visible, false otherwise.
     * @param keyguardGoingAway true when keyguard is being dismissed, false otherwise.
     * @param waking true when the device is waking as part of the transition, false otherwise.
     */
    public void notifyHomeVisibilityChanged(
            boolean isVisible, boolean keyguardGoingAway, boolean waking, boolean behindDesk) {
        if (mListener != null) {
            mListener.call(l -> l.onHomeVisibilityChanged(isVisible, keyguardGoingAway || waking,
                    behindDesk));
        }
    }

    private void notifyHomeVisibilityChangedForUpdates(
            @Nullable UpdateParameters homeState, @Nullable Boolean desktopState) {
        if (homeState == null && desktopState == null) {
            return;
        }

        boolean homeVisible = homeState == null || homeState.mIsVisible;
        notifyHomeVisibilityChanged(
                homeVisible,
                homeState != null && homeState.mKeyguardGoingAway,
                homeState != null && homeState.mWaking,
                homeVisible && desktopState != null && desktopState);
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public ShellExecutor getRemoteCallExecutor() {
        return mMainExecutor;
    }

    /**
     * Invalidates this controller, preventing future calls to send updates.
     */
    public void invalidate(Transitions transitions) {
        transitions.unregisterObserver(this);
        if (mListener != null) {
            // Unregister the listener to ensure any registered binder death recipients are unlinked
            mListener.unregister();
        }
    }

    /**
     * Container class for Launcher visibility changes, Keyguard transition state, and waking state.
     */
    private static class UpdateParameters {
        boolean mIsVisible;
        boolean mKeyguardGoingAway;
        boolean mWaking;

        UpdateParameters(boolean isVisible, boolean keyguardGoingAway, boolean waking) {
            mIsVisible = isVisible;
            mKeyguardGoingAway = keyguardGoingAway;
            mWaking = waking;
        }
    }
}
