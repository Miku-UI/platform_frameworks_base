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

package com.android.server.biometrics;

import static android.Manifest.permission.USE_BIOMETRIC_INTERNAL;
import static android.hardware.biometrics.IIdentityCheckStateListener.WatchRangingState;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.os.Handler;
import android.proximity.IProximityResultCallback;
import android.proximity.ProximityResultCode;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.util.Slog;

/**
 * This class is a helper class to start and cancel watch ranging. It also provides the state of
 * the watch ranging session.
 */
public class WatchRangingHelper {
    private static final String TAG = "WatchRangingHelper";

    private final long mAuthenticationRequestId;
    private final AuthenticationPolicyManager mAuthenticationPolicyManager;
    private final Handler mHandler;
    private @WatchRangingState int mWatchRangingState = WatchRangingState.WATCH_RANGING_IDLE;
    private final WatchRangingListener mWatchRangingListener;

    /** Listener for receiving watch ranging state changes */
    public interface WatchRangingListener {
        /** When the watch ranging state has changed */
        void onStateChanged(@WatchRangingState int watchRangingState);
    }

    WatchRangingHelper(long authenticationRequestId,
            @Nullable AuthenticationPolicyManager authenticationPolicyManager,
            @NonNull Handler handler, WatchRangingListener listener) {
        mAuthenticationRequestId = authenticationRequestId;
        mAuthenticationPolicyManager = authenticationPolicyManager;
        mHandler = handler;
        mWatchRangingListener = listener;
    }

    /**
     * Start watch ranging and set watch ranging state as per the callback.
     */
    @RequiresPermission(USE_BIOMETRIC_INTERNAL)
    public void startWatchRanging() {
        if (mAuthenticationPolicyManager == null) {
            Slog.e(TAG, "Authentication policy manager is null");
            return;
        }

        setWatchRangingState(WatchRangingState.WATCH_RANGING_STARTED);

        mAuthenticationPolicyManager.startWatchRangingForIdentityCheck(mAuthenticationRequestId,
                new IProximityResultCallback.Stub() {
                    @Override
                    public void onError(int error) {
                        Slog.v(TAG,
                                "Error received for watch ranging, error code: " + error);
                        mAuthenticationPolicyManager.cancelWatchRangingForRequestId(
                                mAuthenticationRequestId);
                        setWatchRangingState(WatchRangingState.WATCH_RANGING_STOPPED);
                    }

                    @Override
                    public void onSuccess(int result) {
                        Slog.v(TAG, "Watch ranging was successful with result " + result);
                        mAuthenticationPolicyManager.cancelWatchRangingForRequestId(
                                mAuthenticationRequestId);
                        setWatchRangingState(result == ProximityResultCode.SUCCESS
                                ? WatchRangingState.WATCH_RANGING_SUCCESSFUL
                                : WatchRangingState.WATCH_RANGING_STOPPED);
                    }
                }, mHandler);
    }

    /**
     * Cancels watch ranging request.
     */
    @RequiresPermission(USE_BIOMETRIC_INTERNAL)
    public void cancelWatchRanging() {
        if (mAuthenticationPolicyManager == null) {
            Slog.e(TAG, "Authentication policy manager is null");
            return;
        }

        mAuthenticationPolicyManager.cancelWatchRangingForRequestId(mAuthenticationRequestId);
    }

    /**
     * Sets the current state of watch ranging.
     */
    public void setWatchRangingState(@WatchRangingState int watchRangingState) {
        mWatchRangingState = watchRangingState;
        mWatchRangingListener.onStateChanged(watchRangingState);
    }

    /**
     * Returns current state of watch ranging.
     */
    @WatchRangingState
    public int getWatchRangingState() {
        return mWatchRangingState;
    }
}
