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

package com.android.server.companion.virtual.computercontrol;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.SystemClock;

import com.android.internal.annotations.GuardedBy;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A timer that executes an action after a specified duration, with the ability to pause and resume.
 */
final class PausableTimer implements AutoCloseable {

    private final ScheduledExecutorService mScheduler;
    private final Runnable mAction;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private long mRemainingDurationMs;
    @GuardedBy("mLock")
    private long mStartTimeMs;
    @GuardedBy("mLock")
    private boolean mIsPaused = false;
    @GuardedBy("mLock")
    @Nullable
    private ScheduledFuture<?> mFuture;

    PausableTimer(@NonNull ScheduledExecutorService scheduler, long durationMs,
            @NonNull Runnable action) {
        mScheduler = scheduler;
        mRemainingDurationMs = durationMs;
        mAction = action;
        mStartTimeMs = SystemClock.elapsedRealtime();
        mIsPaused = false;
        mFuture = mScheduler.schedule(mAction, mRemainingDurationMs, TimeUnit.MILLISECONDS);
    }

    void pause() {
        synchronized (mLock) {
            if (mIsPaused) {
                return;
            }
            if (mFuture != null) {
                mFuture.cancel(false);
                mFuture = null;
            }
            long elapsedMs = SystemClock.elapsedRealtime() - mStartTimeMs;
            mRemainingDurationMs -= elapsedMs;
            if (mRemainingDurationMs < 0) {
                mRemainingDurationMs = 0;
            }
            mIsPaused = true;
        }
    }

    void resume() {
        synchronized (mLock) {
            if (!mIsPaused) {
                return;
            }
            mIsPaused = false;
            mStartTimeMs = SystemClock.elapsedRealtime();
            mFuture = mScheduler.schedule(mAction, mRemainingDurationMs, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void close() {
        synchronized (mLock) {
            if (mFuture != null) {
                mFuture.cancel(false);
                mFuture = null;
            }
        }
    }

    void monitor() {
        synchronized (mLock) { /* no-op */ }
    }
}
