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

package com.android.extensions.computercontrol;

import android.os.Handler;
import android.os.SystemClock;

import androidx.annotation.NonNull;

/**
 * Tracks device events and executes a callback when the device is considered idle.
 *
 * The device is considered idle if no events are received for a specified timeout
 * ({@code eventIdleTimeoutMs})after the callback is registered. Alternatively, the
 * callback is executed after a maximum timeout ({@code callbackTimeoutMs}) has been
 * reached.
 *
 * Not thread-safe: must be called from the {@code handler} thread.
 */
class EventIdleTracker {
    private final Handler mHandler;
    private final long mEventIdleTimeoutMs;
    private final long mCallbackTimeoutMs;

    private Callback mPendingCallback;
    private long mCallbackExpiryTime;

    private final Runnable mCallbackExecutor = new Runnable() {
        @Override
        public void run() {
            if (mPendingCallback == null) {
                return;
            }
            Callback pendingCallback = mPendingCallback;
            long callbackExpiryTime = mCallbackExpiryTime;
            reset();
            pendingCallback.onEventIdle(SystemClock.uptimeMillis() >= callbackExpiryTime);
        }
    };

    EventIdleTracker(@NonNull Handler handler, long eventIdleTimeoutMs, long callbackTimeoutMs) {
        mHandler = handler;
        mEventIdleTimeoutMs = eventIdleTimeoutMs;
        mCallbackTimeoutMs = callbackTimeoutMs;
    }

    /** Called when an event is received, which resets the idle timer. */
    void onEvent() {
        if (mPendingCallback == null) {
            return;
        }

        mHandler.removeCallbacks(mCallbackExecutor);
        long idleTime = SystemClock.uptimeMillis() + mEventIdleTimeoutMs;
        mHandler.postAtTime(mCallbackExecutor, Math.min(idleTime, mCallbackExpiryTime));
    }

    void registerOneShotIdleCallback(@NonNull Callback callback) {
        if (mPendingCallback != null) {
            throw new IllegalStateException("There is already a pending callback!");
        }

        mPendingCallback = callback;
        long now = SystemClock.uptimeMillis();
        mCallbackExpiryTime = now + mCallbackTimeoutMs;
        mHandler.postAtTime(mCallbackExecutor, now + mEventIdleTimeoutMs);
    }

    void reset() {
        mPendingCallback = null;
        mCallbackExpiryTime = 0L;
        mHandler.removeCallbacks(mCallbackExecutor);
    }

    interface Callback {
        void onEventIdle(boolean timedOut);
    }
}
