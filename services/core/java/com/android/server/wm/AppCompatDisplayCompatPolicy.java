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

import static android.content.pm.ActivityInfo.CONFIG_COLOR_MODE;
import static android.content.pm.ActivityInfo.CONFIG_DENSITY;
import static android.content.pm.ActivityInfo.CONFIG_KEYBOARD;
import static android.content.pm.ActivityInfo.CONFIG_KEYBOARD_HIDDEN;
import static android.content.pm.ActivityInfo.CONFIG_NAVIGATION;
import static android.content.pm.ActivityInfo.CONFIG_TOUCHSCREEN;
import static android.content.pm.ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS;
import static android.view.Display.TYPE_INTERNAL;
import static android.window.DesktopExperienceFlags.ENABLE_AUTO_RECOVERY_FROM_SELF_KILL;

import android.annotation.NonNull;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.util.SparseArray;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.protolog.WmProtoLogGroups;
import com.android.server.LocalServices;
import com.android.server.companion.virtual.VirtualDeviceManagerInternal;

import java.io.PrintWriter;

/**
 * Encapsulates app-compat logic for multi-display environments.
 *
 * <p>This policy implements three main features to improve app compatibility when moving
 * between displays:
 *
 * <ol>
 * <li><b>Display Compat Mode:</b> Suppresses automatic activity restarts caused by
 * display-specific configuration changes (e.g., density, color mode). This is primarily
 * targeted at games to prevent unexpected crashes or state loss.
 *
 * <li><b>Computer Control Compat Mode:</b> Suppresses additional configuration changes
 * (e.g., keyboard, navigation) when moving to/from a ComputerControl (virtual) display.
 *
 * <li><b>Self-Kill Recovery:</b> A heuristic mechanism that detects when an app unintentionally
 * finishes itself ("self-kill") upon receiving configuration changes during a display move.
 * If detected, the system automatically relaunches the activity on the new display to
 * recover the session.
 * </ol>
 *
 * <p>This class also controls the availability of the restart handle menu and determines
 * whether a forced restart is required based on app-compat overrides.
 */
class AppCompatDisplayCompatPolicy {

    private static final int DISPLAY_COMPAT_MODE_CONFIG_MASK =
            CONFIG_DENSITY | CONFIG_TOUCHSCREEN | CONFIG_COLOR_MODE;

    private static final int COMPUTER_CONTROL_COMPAT_MODE_CONFIG_MASK =
            CONFIG_TOUCHSCREEN | CONFIG_COLOR_MODE | CONFIG_NAVIGATION
                    | CONFIG_KEYBOARD_HIDDEN | CONFIG_KEYBOARD;

    @NonNull
    private final ActivityRecord mActivityRecord;

    private final SelfKillStateMachine mSelfKillStateMachine;

    /**
     * {@code true} if the activity has moved to a different display and has not been restarted yet.
     * This is only set true when an external monitor is involved.
     */
    private boolean mDisplayChangedWithoutRestart;

    private boolean mDisplayChangedForComputerControlWithoutRestart;

    AppCompatDisplayCompatPolicy(@NonNull ActivityRecord activityRecord) {
        mActivityRecord = activityRecord;
        mSelfKillStateMachine = new SelfKillStateMachine(mActivityRecord);
    }

    /**
     * Returns whether the restart menu is enabled for display move. Currently it only gets shown
     * when an app is in display or size compat mode.
     *
     * @return {@code true} if the restart menu should be enabled for display move.
     */
    boolean isRestartMenuEnabledForDisplayMove() {
        // Restart menu is only available to apps in display/size compat mode.
        return isInDisplayCompatMode()
                || (mActivityRecord.inSizeCompatMode() && mDisplayChangedWithoutRestart);
    }

    /**
     * Returns whether the app should be restarted when moved to a different display for app-compat.
     */
    boolean shouldRestartOnDisplayMove() {
        // TODO(b/427878712): Discuss opt-in/out policies.
        return mActivityRecord.mAppCompatController.getDisplayOverrides()
                .shouldRestartOnDisplayMove();
    }

    /**
     * Called when the activity is moved to a different display.
     *
     * @param previousDisplay The display the app was on before this display transition.
     * @param newDisplay The new display the app got moved onto.
     */
    void onMovedToDisplay(@NonNull DisplayContent previousDisplay,
            @NonNull DisplayContent newDisplay) {
        if (previousDisplay.getDisplayInfo().type == TYPE_INTERNAL
                && newDisplay.getDisplayInfo().type == TYPE_INTERNAL) {
            // A transition between internal displays (fold<->unfold on foldable) is not considered
            // display move here for now because they generally have many configurations in common,
            // thus are less likely to cause compat issues. However, for foldables whose display
            // different densities, we provide the option to enable self-kill recovery logic.
            if (mActivityRecord.mWmService.mAppCompatConfiguration
                    .isSelfKillRecoveryBetweenInternalDisplaysEnabled()) {
                mSelfKillStateMachine.onMovedToDisplay(previousDisplay, newDisplay);
            }
            return;
        }

        mSelfKillStateMachine.onMovedToDisplay(previousDisplay, newDisplay);

        mDisplayChangedWithoutRestart = true;

        if (android.companion.virtualdevice.flags.Flags.computerControlAccess()) {
            VirtualDeviceManagerInternal vdmInternal =
                    LocalServices.getService(VirtualDeviceManagerInternal.class);
            if (vdmInternal != null) {
                int previousDisplayId = previousDisplay.getDisplayId();
                int newDisplayId = newDisplay.getDisplayId();

                if (vdmInternal.isComputerControlDisplay(previousDisplayId)
                        || vdmInternal.isComputerControlDisplay(newDisplayId)) {
                    mDisplayChangedForComputerControlWithoutRestart = true;
                }
            }
        }

        if (shouldRestartOnDisplayMove()) {
            ProtoLog.v(WmProtoLogGroups.WM_DEBUG_APP_COMPAT,
                    "Automatically restarting app process on display move: %s",
                    mActivityRecord.packageName);
            // At this point, a transition for moving the app between displays should be running, so
            // the restarting logic below will be queued as a new transition, which means the
            // configuration change for the display move has been processed when the process is
            // restarted. This allows the app to be launched in the latest configuration.
            mActivityRecord.restartProcessIfVisible();
        }
    }

    /**
     * Returns {@code true} if the activity has moved to a different display and has not been
     * restarted yet.
     */
    boolean getDisplayChangedWithoutRestart() {
        return mDisplayChangedWithoutRestart;
    }

    /**
     * Called when the activity's process is restarted.
     */
    void onProcessRestarted() {
        mDisplayChangedWithoutRestart = false;
        mDisplayChangedForComputerControlWithoutRestart = false;
        mSelfKillStateMachine.onProcessRestarted();
    }

    /**
     * Called when the activity is finishing itself.
     */
    void onActivityFinishing() {
        mSelfKillStateMachine.onActivityFinishing();
    }

    /**
     * Called when the activity is relaunching.
     *
     * @param configChangeFlags the config change flags that caused the activity to be relaunched.
     */
    void onActivityRelaunching(int configChangeFlags) {
        mSelfKillStateMachine.onActivityRelaunching(configChangeFlags);
    }

    // TODO(b/408704764): Consider renaming this to "Density" Compat Mode once activity recreation
    //  mitigation is fully launched as at that point density will be the only config change that
    //  needs to be sandboxed here.
    private boolean isInDisplayCompatMode() {
        return getDisplayCompatModeConfigMask() != 0;
    }

    private boolean isEligibleForDisplayCompatMode() {
        return getStaticDisplayCompatModeConfigMask() != 0;
    }

    /**
     * Returns the mask of the config changes that should not trigger activity restart with display
     * move for app-compat reasons.
     *
     * @return the mask of the config changes that should not trigger activity restart or 0 if
     * display compat mode is not enabled for the activity.
     */
    int getDisplayCompatModeConfigMask() {
        int configMask = 0;
        if (mDisplayChangedForComputerControlWithoutRestart) {
            configMask = getComputerControlDisplayCompatModeConfigMask();
        // Enable display compat mode only when display move is involved.
        } else if (mDisplayChangedWithoutRestart) {
            configMask = getStaticDisplayCompatModeConfigMask();
        }

        return configMask;
    }

    private int getStaticDisplayCompatModeConfigMask() {
        if (mActivityRecord.info.applicationInfo.category != ApplicationInfo.CATEGORY_GAME) {
            // A large majority of apps that crash with display move are games. Apply this compat
            // treatment only to games to minimize risk.
            return 0;
        }

        // If a specific config change is supported by the activity, it's exempted from this compat
        // treatment. This way, apps can opt out from display compat mode by handling all the config
        // changes that happen with display move by themselves.
        final int supportedConfigChanged = mActivityRecord.info.getRealConfigChanged();
        return DISPLAY_COMPAT_MODE_CONFIG_MASK & (~supportedConfigChanged);
    }

    private int getComputerControlDisplayCompatModeConfigMask() {
        if (!android.companion.virtualdevice.flags.Flags.computerControlAccess()) {
            return 0;
        }

        final int supportedConfigChanged = mActivityRecord.info.getRealConfigChanged();
        return COMPUTER_CONTROL_COMPAT_MODE_CONFIG_MASK & (~supportedConfigChanged);
    }

    /**
     * Returns {@code true} if the activity is likely to be unintentionally killing itself on
     * display move.
     */
    boolean shouldRecoverFromSelfKillOnDisplayMove() {
        return mSelfKillStateMachine.shouldRecoverFromSelfKillOnDisplayMove();
    }

    void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        if (isEligibleForDisplayCompatMode()) {
            pw.println(prefix + "isEligibleForDisplayCompatMode=true");
        }
        if (isInDisplayCompatMode()) {
            pw.println(prefix + "isInDisplayCompatMode=true");
        }
        if (mDisplayChangedWithoutRestart) {
            pw.println(prefix + "displayChangedWithoutRestart=true");
        }
        if (mDisplayChangedForComputerControlWithoutRestart) {
            pw.println(prefix + "displayChangedForComputerControlWithoutRestart=true");
        }
        mSelfKillStateMachine.dump(pw, prefix);
    }

    /**
     * State machine for the self-kill recovery heuristics.
     *
     * <p>The state machine is designed to detect when an app is likely to be unintentionally
     * killing itself on display move, and to recover from it by relaunching the activity.
     *
     * <p>The state machine transitions through the following states:
     * <ol>
     *     <li>{@link SelfKillState#UNDEFINED}: The initial state.
     *     <li>{@link SelfKillState#DISPLAY_MOVING}: The activity is moving to a different display.
     *     <li>{@link SelfKillState#RELAUNCHING_ON_DISPLAY_MOVE}: The activity is relaunching due to
     *     a config change caused by the display move.
     *     <li>{@link SelfKillState#SELF_KILLING_ON_RELAUNCH}: The activity is finishing itself
     *     during the relaunch.
     * </ol>
     */
    private static class SelfKillStateMachine {

        // This needs to be set longer than the transition timeout (5s).
        private static final int DISPLAY_MOVE_TRANSITION_TIMEOUT_MS = 7 * 1000;

        @NonNull
        private final ActivityRecord mActivityRecord;

        // Only one directional from top to bottom. Reset to |UNDEFINED| when
        // |mDisplayMoveTransitions| becomes empty.
        //
        // Possible transitions:
        //
        // 1. UNDEFINED -> DISPLAY_MOVING
        // 2. DISPLAY_MOVING -> RELAUNCHING_ON_DISPLAY_MOVE
        // 3. RELAUNCHING_ON_DISPLAY_MOVE -> SELF_KILLING_ON_RELAUNCH
        // 4. {DISPLAY_MOVING|RELAUNCHING_ON_DISPLAY_MOVE|SELF_KILLING_ON_RELAUNCH} -> UNDEFINED
        private enum SelfKillState {
            UNDEFINED,
            DISPLAY_MOVING,
            RELAUNCHING_ON_DISPLAY_MOVE,
            SELF_KILLING_ON_RELAUNCH
        }

        @NonNull
        private SelfKillState mSelfKillState = SelfKillState.UNDEFINED;

        // A set of sync IDs of active transitions that caused display move to this activity.
        // The runnable value is a timeout callback.
        private final SparseArray<Runnable> mDisplayMoveTransitions = new SparseArray<>();

        SelfKillStateMachine(@NonNull ActivityRecord activityRecord) {
            mActivityRecord = activityRecord;
        }

        private boolean isInState(SelfKillState state) {
            return mSelfKillState == state;
        }

        private void moveToState(SelfKillState state) {
            ProtoLog.v(WmProtoLogGroups.WM_DEBUG_APP_COMPAT,
                    "Self-kill state transitioning from %s to %s.", mSelfKillState, state);
            mSelfKillState = state;
        }

        void onMovedToDisplay(@NonNull DisplayContent previousDisplay,
                @NonNull DisplayContent newDisplay) {
            if (mActivityRecord.mAppCompatController.getTransparentPolicy().isRunning()
                    || mActivityRecord.getTask() == null
                    || mActivityRecord != mActivityRecord.getTask().topRunningActivity()
                    || (mActivityRecord.intent.getFlags() & FLAG_EXCLUDE_FROM_RECENTS) != 0
                    || !mActivityRecord.isActivityTypeStandardOrUndefined()) {
                return;
            }
            final Transition displayMoveTransition =
                    mActivityRecord.mTransitionController.getCollectingTransition();
            if (displayMoveTransition == null) {
                return;
            }
            if (displayMoveTransition.getExistenceChanged(previousDisplay)
                    || displayMoveTransition.getExistenceChanged(newDisplay)) {
                // There are a few problems with enabling self-kill recovery for display
                // disconnection/reconnection.
                // 1.Multiple tasks can be reparented to another display, and as the self-kill
                //  recovery is async, it can mess up z-order.
                // 2. At the end of some automated E2E tests, they clean up external displays and
                // tasks. Removing a display causes the tasks on the display to be reparented to
                // another display, so the self-kill recovery can be triggered and some tasks may be
                // left on another display unexpectedly.
                // For these reasons, self-kill recovery is disabled for display disconnection and
                // reconnection for now.
                return;
            }
            final Runnable timeoutCallback = () -> {
                synchronized (mActivityRecord.mWmService.mGlobalLock) {
                    ProtoLog.e(WmProtoLogGroups.WM_DEBUG_APP_COMPAT,
                            "Timeout waiting for display-move transition (%d) to finish.",
                            displayMoveTransition.getSyncId());
                    mDisplayMoveTransitions.remove(displayMoveTransition.getSyncId());
                    if (mDisplayMoveTransitions.size() == 0) {
                        mSelfKillState = SelfKillState.UNDEFINED;
                    }
                }
            };
            if (isInState(SelfKillState.UNDEFINED)) {
                moveToState(SelfKillState.DISPLAY_MOVING);
            }
            mDisplayMoveTransitions.put(displayMoveTransition.getSyncId(), timeoutCallback);
            mActivityRecord.mWmService.mH.postDelayed(timeoutCallback,
                    DISPLAY_MOVE_TRANSITION_TIMEOUT_MS);

            displayMoveTransition.addTransitionEndedListener(() -> {
                final Runnable callback =
                        mDisplayMoveTransitions.get(displayMoveTransition.getSyncId());
                mDisplayMoveTransitions.remove(displayMoveTransition.getSyncId());
                if (callback != null) {
                    mActivityRecord.mWmService.mH.removeCallbacks(callback);
                }

                startActivityForSelfKillRecoveryIfNeeded(newDisplay.getDisplayId());

                if (mDisplayMoveTransitions.size() == 0) {
                    moveToState(SelfKillState.UNDEFINED);
                }
            });
        }

        void onActivityRelaunching(int configChangeFlags) {
            if ((configChangeFlags & DISPLAY_COMPAT_MODE_CONFIG_MASK) == 0) {
                // For now, we apply sel-kill recovery only when the activity gets restarted by
                // display-related config changes.
                // TODO(b/446998828): Consider expanding this to more config changes.
                return;
            }

            final Transition displayMoveTransition =
                    mActivityRecord.mTransitionController.getCollectingTransition();
            if (displayMoveTransition != null
                    && mDisplayMoveTransitions.get(displayMoveTransition.getSyncId()) != null
                    && isInState(SelfKillState.DISPLAY_MOVING)) {
                moveToState(SelfKillState.RELAUNCHING_ON_DISPLAY_MOVE);
            }
        }

        void onActivityFinishing() {
            if (isInState(SelfKillState.RELAUNCHING_ON_DISPLAY_MOVE)) {
                moveToState(SelfKillState.SELF_KILLING_ON_RELAUNCH);
            }
        }

        void onProcessRestarted() {
            mDisplayMoveTransitions.clear();
        }

        boolean shouldRecoverFromSelfKillOnDisplayMove() {
            if (!ENABLE_AUTO_RECOVERY_FROM_SELF_KILL.isTrue()) {
                return false;
            }

            return isInState(SelfKillState.RELAUNCHING_ON_DISPLAY_MOVE)
                    || isInState(SelfKillState.SELF_KILLING_ON_RELAUNCH);
        }

        private void startActivityForSelfKillRecoveryIfNeeded(int displayId) {
            if (!ENABLE_AUTO_RECOVERY_FROM_SELF_KILL.isTrue()) {
                return;
            }

            if (isInState(SelfKillState.SELF_KILLING_ON_RELAUNCH)) {
                // Posting to the handler to wait for the activity to be fully destroyed and to use
                // the system default identity (not app\'s identity) because the app process may
                // already be in background at this point, and relaunching itself can be blocked by
                // BAL.
                mActivityRecord.mAtmService.mH.post(() -> {
                    ProtoLog.v(WmProtoLogGroups.WM_DEBUG_APP_COMPAT,
                            "Relaunching Self-killed app: %s", mActivityRecord.packageName);
                    final int callingPid = Binder.getCallingPid();
                    final int callingUid = Binder.getCallingUid();
                    final Intent restartIntent = new Intent(mActivityRecord.intent);
                    final SafeActivityOptions options = new SafeActivityOptions(
                            ActivityOptions.makeBasic().setLaunchDisplayId(displayId),
                            callingPid, callingUid);
                    mActivityRecord.mAtmService.getActivityStartController().startActivityInPackage(
                            mActivityRecord.getUid(), callingPid, callingUid,
                            mActivityRecord.packageName, null, restartIntent, null, null, null, 0,
                            0, options, mActivityRecord.mUserId, null, "display-app-compat", false,
                            null, /* allowBalExemptionForSystemProcess */ true);
                });
            }
        }

        void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
            if (!isInState(SelfKillState.UNDEFINED)) {
                pw.println(prefix + "mSelfKillState=" + mSelfKillState);
            }
            if (mDisplayMoveTransitions.size() > 0) {
                pw.println(prefix + "mDisplayMoveTransitions.size()="
                        + mDisplayMoveTransitions.size());
            }
        }
    }
}
