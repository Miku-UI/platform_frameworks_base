/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.keyguard;

import static android.security.Flags.lockscreenIndicateDuplicateGuesses;

import static com.android.internal.util.LatencyTracker.ACTION_CHECK_CREDENTIAL;
import static com.android.internal.util.LatencyTracker.ACTION_CHECK_CREDENTIAL_UNLOCKED;
import static com.android.keyguard.KeyguardAbsKeyInputView.MINIMUM_PASSWORD_LENGTH_BEFORE_REPORT;

import android.content.res.ColorStateList;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.uilatencystats.UiLatencyStatsManager;
import android.util.Log;
import android.util.PluralsMessageFormatter;
import android.view.KeyEvent;

import com.android.internal.util.LatencyTracker;
import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.VerifyCredentialResponse;
import com.android.keyguard.EmergencyButtonController.EmergencyButtonCallback;
import com.android.keyguard.KeyguardAbsKeyInputView.KeyDownListener;
import com.android.keyguard.KeyguardSecurityModel.SecurityMode;
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel;
import com.android.systemui.bouncer.shared.model.BouncerMessageStrings;
import com.android.systemui.bouncer.shared.model.LockoutMessageModel;
import com.android.systemui.bouncer.ui.helper.BouncerHapticPlayer;
import com.android.systemui.classifier.FalsingClassifier;
import com.android.systemui.classifier.FalsingCollector;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.user.domain.interactor.SelectedUserInteractor;
import com.android.systemui.util.wrapper.LockPatternCheckerWrapper;

import java.time.Duration;
import java.util.Optional;

public abstract class KeyguardAbsKeyInputViewController<T extends KeyguardAbsKeyInputView>
        extends KeyguardInputViewController<T> {
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    protected final LockPatternUtils mLockPatternUtils;
    private final LatencyTracker mLatencyTracker;
    private final FalsingCollector mFalsingCollector;
    private final EmergencyButtonController mEmergencyButtonController;
    private final UserActivityNotifier mUserActivityNotifier;
    private final LockPatternCheckerWrapper mLockPatternChecker;
    private final Optional<UiLatencyStatsManager> mUiLatencyStatsManager;
    private CountDownTimer mCountdownTimer;
    private boolean mDismissing;
    protected AsyncTask<?, ?, ?> mPendingLockCheck;
    protected boolean mResumed;
    protected boolean mLockedOut;
    private static final String TAG = "KeyguardAbsKeyInputViewController";

    private final KeyDownListener mKeyDownListener = (keyCode, keyEvent) -> {
        // Fingerprint sensor sends a KeyEvent.KEYCODE_UNKNOWN.
        // We don't want to consider it valid user input because the UI
        // will already respond to the event.
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            onUserInput();
        }
        return false;
    };

    private final EmergencyButtonCallback mEmergencyButtonCallback = new EmergencyButtonCallback() {
        @Override
        public void onEmergencyButtonClickedWhenInCall() {
            getKeyguardSecurityCallback().reset();
        }
    };

    protected KeyguardAbsKeyInputViewController(T view,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            SecurityMode securityMode,
            LockPatternUtils lockPatternUtils,
            KeyguardSecurityCallback keyguardSecurityCallback,
            KeyguardMessageAreaController.Factory messageAreaControllerFactory,
            LatencyTracker latencyTracker, FalsingCollector falsingCollector,
            EmergencyButtonController emergencyButtonController,
            FeatureFlags featureFlags, SelectedUserInteractor selectedUserInteractor,
            BouncerHapticPlayer bouncerHapticPlayer,
            UserActivityNotifier userActivityNotifier,
            LockPatternCheckerWrapper lockPatternCheckerWrapper,
            Optional<UiLatencyStatsManager> uiLatencyStatsManager
    ) {
        super(view, securityMode, keyguardSecurityCallback, emergencyButtonController,
                messageAreaControllerFactory, featureFlags, selectedUserInteractor,
                bouncerHapticPlayer);
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mLockPatternUtils = lockPatternUtils;
        mLatencyTracker = latencyTracker;
        mFalsingCollector = falsingCollector;
        mEmergencyButtonController = emergencyButtonController;
        mUserActivityNotifier = userActivityNotifier;
        mLockPatternChecker = lockPatternCheckerWrapper;
        mUiLatencyStatsManager = uiLatencyStatsManager;
    }

    abstract void resetState();

    @Override
    public void onInit() {
        super.onInit();
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mView.setKeyDownListener(mKeyDownListener);
        mEmergencyButtonController.setEmergencyButtonCallback(mEmergencyButtonCallback);
        // if the user is currently locked out, enforce it.
        Duration lockoutEndTime = mLockPatternUtils.getLockoutEndTime(
                mSelectedUserInteractor.getSelectedUserId());
        if (shouldLockout(lockoutEndTime)) {
            handleAttemptLockout(lockoutEndTime);
        }
    }

    @Override
    public void reset() {
        super.reset();
        // start fresh
        mDismissing = false;
        mView.resetPasswordText(false /* animate */, false /* announce */);
        resetState();
    }

    @Override
    public boolean needsInput() {
        return false;
    }

    @Override
    public void showMessage(CharSequence message, ColorStateList colorState, boolean animated) {
        if (mMessageAreaController == null) {
            return;
        }

        if (colorState != null) {
            mMessageAreaController.setNextMessageColor(colorState);
        }
        mMessageAreaController.setMessage(message, animated);
    }

    // Allow subclasses to override this behavior
    protected boolean shouldLockout(Duration lockoutEndTime) {
        return !lockoutEndTime.isZero();
    }

    // Prevent user from using the PIN/Password entry until scheduled deadline.
    protected void handleAttemptLockout(Duration lockoutEndTime) {
        mView.setPasswordEntryEnabled(false);
        mView.setPasswordEntryInputEnabled(false);
        mLockedOut = true;
        long now = SystemClock.elapsedRealtime();
        long secondsInFuture = (long) Math.ceil(
                (lockoutEndTime.toMillis() - now) / 1000.0);
        getKeyguardSecurityCallback().onAttemptLockoutStart(secondsInFuture);
        mCountdownTimer = new CountDownTimer(secondsInFuture * 1000, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = Math.round(millisUntilFinished / 1000.0);
                LockoutMessageModel lockoutMessageModel =
                        BouncerMessageStrings.INSTANCE.primaryAuthLockedOut(
                                AuthenticationMethodModel.Password.INSTANCE, secondsRemaining);
                mMessageAreaController.setMessage(
                        PluralsMessageFormatter.format(
                            mView.getResources(),
                            lockoutMessageModel.primaryFormatterArgs(),
                            lockoutMessageModel.getPrimaryMessage()),
                        /* animate= */ false);
            }

            @Override
            public void onFinish() {
                mMessageAreaController.setMessage("");
                mLockedOut = false;
                resetState();
            }
        }.start();
    }

    void onPasswordChecked(int userId, boolean matched, Duration timeout, boolean isValidPassword,
            boolean isDuplicate) {
        boolean dismissKeyguard = mSelectedUserInteractor.getSelectedUserId() == userId;
        if (matched) {
            mBouncerHapticPlayer.playAuthenticationFeedback(
                    /* authenticationSucceeded = */true
            );
            getKeyguardSecurityCallback().reportUnlockAttempt(userId, true,
                    Duration.ZERO, isDuplicate);
            if (dismissKeyguard) {
                mDismissing = true;
                mLatencyTracker.onActionStart(LatencyTracker.ACTION_LOCKSCREEN_UNLOCK);
                mUiLatencyStatsManager.ifPresent(m -> m.reportEvent(
                        UiLatencyStatsManager.EVENT_LOCK_SCREEN_UNLOCK_START,
                        SystemClock.elapsedRealtime()));
                Log.i(TAG,
                        "StartUnlock. "
                        + "User: " + userId
                        + " TS: " + SystemClock.uptimeMillis()
                );
                getKeyguardSecurityCallback().dismiss(true, userId, getSecurityMode());
            }
        } else {
            mBouncerHapticPlayer.playAuthenticationFeedback(
                    /* authenticationSucceeded = */false
            );
            mView.resetPasswordText(true /* animate */, false /* announce deletion if no match */);
            if (isValidPassword) {
                getKeyguardSecurityCallback()
                        .reportUnlockAttempt(userId, false, timeout, isDuplicate);
                if (timeout.isPositive()) {
                    Duration lockoutEndTime = mLockPatternUtils.getLockoutEndTime(userId);
                    handleAttemptLockout(lockoutEndTime);
                }
            }
            if (timeout.isZero()) {
                mMessageAreaController.setMessage(mView.getWrongPasswordStringId(isDuplicate));
            }
            startErrorAnimation();
        }
    }

    protected void startErrorAnimation() { /* no-op */ }

    protected void verifyPasswordAndUnlock() {
        if (mDismissing) return; // already verified but haven't been dismissed; don't do it again.
        if (mLockedOut) return;

        final LockscreenCredential password = mView.getEnteredCredential();
        mView.setPasswordEntryInputEnabled(false);
        if (mPendingLockCheck != null) {
            mPendingLockCheck.cancel(false);
        }

        final int userId = mSelectedUserInteractor.getSelectedUserId();
        if (password.size() <= MINIMUM_PASSWORD_LENGTH_BEFORE_REPORT) {
            // to avoid accidental lockout, only count attempts that are long enough to be a
            // real password. This may require some tweaking.
            mView.setPasswordEntryInputEnabled(true);
            onPasswordChecked(userId, false /* matched */, Duration.ZERO,
                    false /* not valid - too short */, false /* isDuplicate */);
            password.zeroize();
            return;
        }

        mLatencyTracker.onActionStart(ACTION_CHECK_CREDENTIAL);
        mLatencyTracker.onActionStart(ACTION_CHECK_CREDENTIAL_UNLOCKED);

        mKeyguardUpdateMonitor.setCredentialAttempted();
        mPendingLockCheck = mLockPatternChecker.checkCredential(
                mLockPatternUtils,
                password,
                userId,
                new LockPatternChecker.OnCheckCallback() {

                    @Override
                    public void onEarlyMatched() {
                        mLatencyTracker.onActionEnd(ACTION_CHECK_CREDENTIAL);

                        onPasswordChecked(userId, true /* matched */, Duration.ZERO /* timeout */,
                                true /* isValidPassword */, false /* isDuplicate */);
                        password.zeroize();
                    }

                    @Override
                    public void onChecked(VerifyCredentialResponse response) {
                        boolean matched = response.isMatched();
                        Duration timeout = response.getTimeout();
                        boolean isDuplicate = lockscreenIndicateDuplicateGuesses()
                                && response.isCredAlreadyTried();
                        mLatencyTracker.onActionEnd(ACTION_CHECK_CREDENTIAL_UNLOCKED);
                        mView.setPasswordEntryInputEnabled(true);
                        mPendingLockCheck = null;
                        if (!matched) {
                            onPasswordChecked(userId, false /* matched */, timeout,
                                    true /* isValidPassword */, isDuplicate);
                        }
                        password.zeroize();
                    }

                    @Override
                    public void onCancelled() {
                        // We already got dismissed with the early matched callback, so we cancelled
                        // the check. However, we still need to note down the latency.
                        mLatencyTracker.onActionEnd(ACTION_CHECK_CREDENTIAL_UNLOCKED);
                        password.zeroize();
                    }
                });
    }

    @Override
    public void showPromptReason(int reason) {
        if (reason != PROMPT_REASON_NONE) {
            int promtReasonStringRes = mView.getPromptReasonStringRes(reason);
            if (promtReasonStringRes != 0) {
                mMessageAreaController.setMessage(
                        mView.getResources().getString(promtReasonStringRes), false);
            }
        }
    }

    protected void onUserInput() {
        mFalsingCollector.updateFalseConfidence(FalsingClassifier.Result.passed(0.6));
        getKeyguardSecurityCallback().userActivity();
        getKeyguardSecurityCallback().onUserInput();
        mMessageAreaController.setMessage("");
        mUserActivityNotifier.notifyUserActivity();
    }

    @Override
    public void onResume(int reason) {
        mResumed = true;
    }

    @Override
    public void onPause() {
        mResumed = false;

        if (mCountdownTimer != null) {
            mCountdownTimer.cancel();
            mCountdownTimer = null;
        }
        if (mPendingLockCheck != null) {
            mPendingLockCheck.cancel(false);
            mPendingLockCheck = null;
        }
        reset();
    }
}
