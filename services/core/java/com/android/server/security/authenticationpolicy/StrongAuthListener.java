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

package com.android.server.security.authenticationpolicy;

import android.annotation.NonNull;
import android.hardware.biometrics.AuthenticationStateListener;
import android.hardware.biometrics.events.AuthenticationAcquiredInfo;
import android.hardware.biometrics.events.AuthenticationErrorInfo;
import android.hardware.biometrics.events.AuthenticationFailedInfo;
import android.hardware.biometrics.events.AuthenticationHelpInfo;
import android.hardware.biometrics.events.AuthenticationStartedInfo;
import android.hardware.biometrics.events.AuthenticationStoppedInfo;
import android.hardware.biometrics.events.AuthenticationSucceededInfo;
import android.os.Handler;
import android.util.Slog;

import com.android.server.locksettings.LockSettingsStateListener;

import java.util.function.IntConsumer;

/**
 * Internal helper class for subscribing to  strong auth methods across various
 * system services, such as LockSettings and BiometricManager.
 *
 * @hide
 */
public class StrongAuthListener {

    private static final String TAG = "StrongAuthListener";

    private final Handler mHandler;
    private final IntConsumer mOnStrongAuth;

    private final LockSettingsStateListener mLockSettingsStateListener =
            new LockSettingsStateListener() {
                @Override
                public void onAuthenticationSucceeded(int userId) {
                    mHandler.post(() -> onStrongAuth(userId));
                }

                @Override
                public void onAuthenticationFailed(int userId) {
                }
            };

    private final AuthenticationStateListener mAuthenticationStateListener =
            new AuthenticationStateListener.Stub() {

                @Override
                public void onAuthenticationSucceeded(AuthenticationSucceededInfo authInfo) {
                    if (authInfo.isIsStrongBiometric()) {
                        mHandler.post(() -> onStrongAuth(authInfo.getUserId()));
                    }
                }

                @Override
                public void onAuthenticationAcquired(AuthenticationAcquiredInfo authInfo) {
                }

                @Override
                public void onAuthenticationError(AuthenticationErrorInfo authInfo) {
                }

                @Override
                public void onAuthenticationFailed(AuthenticationFailedInfo authInfo) {
                }

                @Override
                public void onAuthenticationHelp(AuthenticationHelpInfo authInfo) {
                }

                @Override
                public void onAuthenticationStarted(AuthenticationStartedInfo authInfo) {
                }

                @Override
                public void onAuthenticationStopped(AuthenticationStoppedInfo authInfo) {
                }
            };

    public StrongAuthListener(@NonNull Handler handler, IntConsumer action) {
        mHandler = handler;
        mOnStrongAuth = action;
    }

    /**
     * Returns a listener for registering with
     * {@link
     * com.android.server.locksettings.LockSettingsInternal#registerLockSettingsStateListener(LockSettingsStateListener)}.
     */
    public LockSettingsStateListener asLockSettingsStateListener() {
        return mLockSettingsStateListener;
    }

    /**
     * Returns a listener for registering with
     * {@link
     * android.hardware.biometrics.BiometricManager#registerAuthenticationStateListener(AuthenticationStateListener)}.
     */
    public AuthenticationStateListener asAuthenticationStateListener() {
        return mAuthenticationStateListener;
    }

    private void onStrongAuth(int userId) {
        Slog.v(TAG, "Strong auth event for user: " + userId);
        mOnStrongAuth.accept(userId);
    }
}
