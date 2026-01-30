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

import android.os.HandlerThread;

class StabilityHintCallbackTracker implements AutoCloseable {
    private static final long STABILITY_TIMER_MS = 500;

    private final HandlerThread mHandlerThread = new HandlerThread("StabilityHintCallbackTracker");
    private final ComputerControlSession.StabilityHintCallback mCallback;
    private final EventIdleTracker mEventIdleTracker;

    StabilityHintCallbackTracker(
            ComputerControlSession.StabilityHintCallback callback, long timeoutMs) {
        this.mCallback = callback;

        mHandlerThread.start();
        this.mEventIdleTracker = new EventIdleTracker(
                mHandlerThread.getThreadHandler(), STABILITY_TIMER_MS, timeoutMs);
    }

    public void onAccessibilityEvent() {
        mEventIdleTracker.onEvent();
    }

    public void resetStabilityState() {
        mEventIdleTracker.reset();
        mEventIdleTracker.registerOneShotIdleCallback(mCallback::onStabilityHint);
    }

    public ComputerControlSession.StabilityHintCallback getCallback() {
        return mCallback;
    }

    @Override
    public void close() {
        mEventIdleTracker.reset();
        mHandlerThread.quitSafely();
    }
}
