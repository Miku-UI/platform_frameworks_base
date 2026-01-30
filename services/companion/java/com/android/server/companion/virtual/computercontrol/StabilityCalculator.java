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

package com.android.server.companion.virtual.computercontrol;

import android.annotation.NonNull;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.IComputerControlStabilityListener;
import android.os.RemoteException;
import android.util.Slog;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Consolidates signals to determine stability of a {@link ComputerControlSession}. */
final class StabilityCalculator {
    private static final String TAG = "StabilityHintsCalculator";

    // TODO(b/428957982): Implement actual stability signals from the framework, and remove any
    // timeout based logic.
    private static final long CONTINUOUS_INPUT_EVENT_STABILITY_TIMEOUT_MS = 2500L;
    private static final long NON_CONTINUOUS_INPUT_EVENT_STABILITY_TIMEOUT_MS = 2000L;
    private static final long APPLICATION_LAUNCH_STABILITY_TIMEOUT_MS = 3000L;

    private final IComputerControlStabilityListener mListener;
    private final ScheduledExecutorService mScheduler;
    private ScheduledFuture<?> mStabilityFuture;

    StabilityCalculator(@NonNull IComputerControlStabilityListener listener,
            @NonNull ScheduledExecutorService scheduler) {
        mListener = listener;
        mScheduler = scheduler;
    }

    void onContinuousInputEvent() {
        notifyListenerAfter(CONTINUOUS_INPUT_EVENT_STABILITY_TIMEOUT_MS);
    }

    void onNonContinuousInputEvent() {
        notifyListenerAfter(NON_CONTINUOUS_INPUT_EVENT_STABILITY_TIMEOUT_MS);
    }

    void onApplicationLaunch() {
        notifyListenerAfter(APPLICATION_LAUNCH_STABILITY_TIMEOUT_MS);
    }

    void onInteractionFailed() {
        notifyListenerImmediately();
    }

    void close() {
        cancelExistingStabilityNotification();
    }

    private void notifyListenerAfter(long timeoutMs) {
        cancelExistingStabilityNotification();

        ScheduledFuture<?> future = mScheduler.schedule(() -> {
            sendStableNotification();
        }, timeoutMs, TimeUnit.MILLISECONDS);

        synchronized (this) {
            mStabilityFuture = future;
        }
    }

    private void notifyListenerImmediately() {
        cancelExistingStabilityNotification();
        sendStableNotification();
    }

    private void sendStableNotification() {
        try {
            mListener.onSessionStable();
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to notify about ComputerControlSession stability");
        }
    }

    private void cancelExistingStabilityNotification() {
        synchronized (this) {
            if (mStabilityFuture != null) {
                mStabilityFuture.cancel(true);
                mStabilityFuture = null;
            }
        }
    }
}
