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

package com.android.server.wm;

import static com.android.window.flags.Flags.ensureWallpaperDrawnOnDisplaySwitch;

import android.annotation.Nullable;
import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.util.Slog;
import android.view.DisplayInfo;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wm.DisplayUpdater.DisplayInfos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class that is responsible for notifying DisplayManager to 'unblock' the screen
 * after all content is drawn.
 * When turning on a screen, DisplayManager will keep the content hidden first ('blocked'),
 * and allow WindowManager to notify back to DisplayManager only when it has prepared
 * the content for the screen. This ensures that user doesn't see unprepared content.
 * For example, this is used to ensure that the content is resized for the new size
 * on foldable devices when folding or unfolding.
 */
class DisplayUnblocker {

    private static final String TAG = "DisplayUnblocker";
    private static final String TRACE_TAG_WAIT_FOR_TRANSITION =
            "Screen unblock: wait for transition";
    private static final String READY_CONDITION_KEYGUARD_DRAWN = "keyguard_drawn";

    /**
     * Currently, the 95th percentile between creating the transition and presenting
     * the start transaction is ~930ms. Let's set the timeout to two seconds in case
     * a transition just started collecting and we have received another display change that
     * will be put into the TransitionController queue, so in total we would need to wait
     * for both transitions.
     */
    private static final int WAIT_FOR_TRANSITION_TIMEOUT = 2000;

    private final RootWindowContainer mRootWindowContainer;

    @VisibleForTesting
    @NonNull
    Handler mHandler;

    /**
     * Current physical display change Shell transition that we are waiting for.
     * Whenever another physical display change comes while we haven't finished processing
     * the previous one, this field will be overwritten by the latest transition.
     */
    @Nullable
    private Transition mPhysicalDisplayChangeTransition;

    /** Whether {@link #mScreenUnblocker} should wait for transition to be ready. */
    private boolean mShouldWaitForTransitionWhenScreenOn;

    /**
     * True if we are waiting for the IKeyguardDrawnCallback which will eventually invoke
     * {@link DeferredDisplayUpdater#waitForTransition(Message)}}
     */
    private boolean mPendingKeyguardDrawing;

    /** The message to notify PhoneWindowManager#finishWindowsDrawn. */
    @Nullable
    private Message mScreenUnblocker;

    private final List<Transition.ReadyCondition> mWaitingForKeyguardDrawnConditions =
            new ArrayList<>();

    private final Runnable mMeetKeyguardDrawnConditions = () -> {
        for (int i = 0; i < mWaitingForKeyguardDrawnConditions.size(); i++) {
            mWaitingForKeyguardDrawnConditions.get(i).meet();
        }
        mWaitingForKeyguardDrawnConditions.clear();
    };


    private final Runnable mScreenUnblockTimeoutRunnable = () -> {
        Slog.e(TAG, "Timeout waiting for the display switch transition to start");
        continueScreenUnblocking(/* fromTransition= */ null);
    };

    public DisplayUnblocker(RootWindowContainer rootWindowContainer) {
        mRootWindowContainer = rootWindowContainer;
        mHandler = mRootWindowContainer.mWmService.mH;
    }

    public void onCollectionStarted(@NonNull Transition transition,
            @NonNull DisplayInfos newState) {

        final boolean defaultPhysicalDisplayUpdated = isDefaultPhysicalDisplayUpdated(newState);

        if (defaultPhysicalDisplayUpdated) {
            mPhysicalDisplayChangeTransition = transition;

            final DisplayContent defaultDisplay = mRootWindowContainer.getDefaultDisplay();
            final WindowState notificationShade =
                    defaultDisplay.getDisplayPolicy().getNotificationShade();
            if (notificationShade != null && notificationShade.isVisible()
                    && defaultDisplay.mAtmService.mKeyguardController.isKeyguardOrAodShowing(
                    defaultDisplay.mDisplayId)) {
                Slog.i(TAG, notificationShade + " uses blast for display switch");
                notificationShade.useBlastForNextSync();
            }

            transition.addTransactionPresentedListener(() -> continueScreenUnblocking(transition));
        }
    }

    public void onDisplayChangesApplied(@NonNull Transition transition) {
        if (mPendingKeyguardDrawing && ensureWallpaperDrawnOnDisplaySwitch()) {
            // Keyguard hasn't reported that it has drawn yet, defer
            // readiness until it draws
            final Transition.ReadyCondition condition = new Transition.ReadyCondition(
                    READY_CONDITION_KEYGUARD_DRAWN,
                    /* newTrackerOnly= */ false);
            transition.mReadyTracker.add(condition);
            mWaitingForKeyguardDrawnConditions.add(condition);
        }
    }

    /**
     * Continues the screen unblocking flow, could be called either on a binder thread as
     * a result of surface transaction presented listener or from {@link WindowManagerService#mH}
     * handler in case of timeout
     */
    private void continueScreenUnblocking(@Nullable Transition fromTransition) {
        synchronized (mRootWindowContainer.mWmService.mGlobalLock) {
            // Do not proceed with unblocking in case if the ready transition doesn't match
            // the current mPhysicalDisplayChangeTransition, this means that while we were
            // waiting for a transition, another one was requested. We want to unblock only
            // when the last transition is ready.
            final boolean isTimeout = fromTransition == null;
            final boolean isTransitionMatching =
                    isTimeout || fromTransition == mPhysicalDisplayChangeTransition;

            if (!isTransitionMatching) {
                return;
            }

            mPhysicalDisplayChangeTransition = null;
            mShouldWaitForTransitionWhenScreenOn = false;
            mHandler.removeCallbacks(mScreenUnblockTimeoutRunnable);
            if (mScreenUnblocker == null) {
                return;
            }
            mScreenUnblocker.sendToTarget();
            if (Trace.isTagEnabled(Trace.TRACE_TAG_WINDOW_MANAGER)) {
                Trace.endAsyncSection(TRACE_TAG_WAIT_FOR_TRANSITION, mScreenUnblocker.hashCode());
            }
            mScreenUnblocker = null;
        }
    }

    /**
     * Called with {@code true} when physical display is going to switch. And {@code false} when
     * the display is turned on or the device goes to sleep.
     * <p>
     * This method must be called synchronously, which will ensure that we update the state
     * before both waitForTransition (onScreenTurningOn) and updateDisplayInfo (onDisplayChanged).
     * These two events could come in any order from the DisplayManager, and
     * onDisplaySwitching(true) indicates that we should expect to receive these calls.
     * <p>
     * This event is invoked from DisplayManager and guarded by
     * the {@link DisplayManagerService.SyncRoot} lock. Do not invoke code that holds WM lock here.
     */
    void onDefaultDisplaySwitching(boolean switching) {
        mShouldWaitForTransitionWhenScreenOn = switching;
        mPendingKeyguardDrawing = switching;

        if (!switching) {
            // Reset keyguard drawn in case for some reason we haven't received the callback
            // and the screen is already fully switched on here
            onKeyguardDrawn();
        }
    }

    /**
     * Returns 'true' if the physical display is currently in the process of switching, for example
     * on foldable devices when folding or unfolding. The value becomes 'false' when the switching
     * has been finished (the new display is fully turned on).
     */
    boolean isDefaultDisplaySwitching() {
        return mShouldWaitForTransitionWhenScreenOn;
    }

    /** Returns {@code true} if the transition will control when to turn on the screen. */
    boolean waitForDefaultDisplayTransition(@NonNull Message screenUnblocker) {
        // waitForTransition() is called by PhoneWindowManager after receiving keyguard
        // drawn callback, so mark keyguard as drawn
        onKeyguardDrawn();

        if (!mShouldWaitForTransitionWhenScreenOn) {
            return false;
        }
        mScreenUnblocker = screenUnblocker;
        if (Trace.isTagEnabled(Trace.TRACE_TAG_WINDOW_MANAGER)) {
            Trace.beginAsyncSection(TRACE_TAG_WAIT_FOR_TRANSITION, screenUnblocker.hashCode());
        }

        mHandler.removeCallbacks(mScreenUnblockTimeoutRunnable);
        mHandler.postDelayed(mScreenUnblockTimeoutRunnable, WAIT_FOR_TRANSITION_TIMEOUT);
        return true;
    }

    /**
     * Called after physical display has changed and after DisplayContent applied new display
     * properties.
     */
    void onDefaultDisplayContentDisplayPropertiesPostChanged() {
        if (mPhysicalDisplayChangeTransition != null) {
            return;
        }
        // Unblock immediately in case there is no transition. This is unlikely to happen.
        if (mScreenUnblocker != null) {
            mScreenUnblocker.sendToTarget();
            mScreenUnblocker = null;
        }
        mShouldWaitForTransitionWhenScreenOn = false;
    }

    private void onKeyguardDrawn() {
        mPendingKeyguardDrawing = false;
        mHandler.post(mMeetKeyguardDrawnConditions);
    }

    private boolean isDefaultPhysicalDisplayUpdated(@NonNull DisplayInfos newState) {
        final DisplayContent defaultDisplay = mRootWindowContainer.getDefaultDisplay();
        final DisplayInfo newDefaultDisplayInfo = newState.displayInfos().get(
                defaultDisplay.getDisplayId());
        if (newDefaultDisplayInfo == null) return false;
        return !Objects.equals(defaultDisplay.getDisplayInfo().uniqueId,
                newDefaultDisplayInfo.uniqueId);
    }
}
