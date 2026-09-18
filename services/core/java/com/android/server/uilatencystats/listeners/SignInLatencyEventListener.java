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

package com.android.server.uilatencystats.listeners;

import static com.android.internal.util.FrameworkStatsLog.SIGN_IN_DURATION__TYPE__LOCKSCREEN_UNLOCKING_TO_LAUNCHER_SHOWN;

import android.os.Trace;
import android.uilatencystats.Event;
import android.uilatencystats.EventType;
import android.uilatencystats.UiLatencyEventListener;
import android.util.Slog;

import androidx.annotation.GuardedBy;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.util.PerfettoTrigger;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * A {@link UiLatencyEventListener} to report sign-in UI latency metrics.
 *
 * @hide
 */
public final class SignInLatencyEventListener implements UiLatencyEventListener {
    private static final String TAG = "SignInLatencyEventListener";

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mIsDuringSignIn = false;

    @GuardedBy("mLock")
    private Optional<Long> mLockScreenUnlockStartTimestamp = Optional.empty();

    // Threshold to trigger an Always-On-Tracing perfetto trace for a sign-in latency.
    @GuardedBy("mLock")
    private Duration mPerfettoTriggerThreshold = Duration.ofSeconds(3);

    @GuardedBy("mLock")
    private StatsLogWriter mStatsLogWriter = FrameworkStatsLog::write;

    @GuardedBy("mLock")
    private PerfettoTriggerCallback mPerfettoTrigger = PerfettoTrigger::trigger;

    /** @hide */
    public SignInLatencyEventListener() {}

    @Override
    public List<Integer> getEventIdsToListen() {
        return List.of(
                EventType.EVENT_LOCK_SCREEN_UNLOCK_START,
                EventType.EVENT_USER_SWITCH,
                EventType.EVENT_LAUNCHER_SHOWN);
    }

    @Override
    public void onEvent(Event event) {
        synchronized (mLock) {
            EventType eventType = event.getType();

            if (eventType instanceof EventType.UserSwitch) {
                mIsDuringSignIn = true;
                mLockScreenUnlockStartTimestamp = Optional.empty();
            } else if (!mIsDuringSignIn) {
                return;
            }

            if (eventType instanceof EventType.LockScreenUnlockStart) {
                // Trace API lacks a timestamp parameter, so timing is not precise and may be off by
                // a few ms. Good enough for user perceived UI metrics.
                Trace.asyncTraceForTrackBegin(
                        Trace.TRACE_TAG_SYSTEM_SERVER,
                        TAG,
                        "Waiting for LockscreenUnlockingToLauncherShown",
                        /* cookie= */ 0);
                mLockScreenUnlockStartTimestamp = Optional.of(event.getTimestamp());
            } else if (eventType instanceof EventType.LauncherShown) {
                // Finish states.
                mIsDuringSignIn = false;
                if (mLockScreenUnlockStartTimestamp.isPresent()) {
                    Trace.asyncTraceForTrackEnd(
                            Trace.TRACE_TAG_SYSTEM_SERVER, TAG, /* cookie= */ 0);
                    long duration = event.getTimestamp() - mLockScreenUnlockStartTimestamp.get();
                    Slog.i(TAG, "Reporting stat: LockscreenUnlockingToLauncherShown " + duration);
                    mStatsLogWriter.write(
                            FrameworkStatsLog.SIGN_IN_DURATION,
                            SIGN_IN_DURATION__TYPE__LOCKSCREEN_UNLOCKING_TO_LAUNCHER_SHOWN,
                            duration);

                    if (duration > mPerfettoTriggerThreshold.toMillis()) {
                        mPerfettoTrigger.trigger(
                                getPerfettoTriggerName("LockscreenUnlockingToLauncherShown"));
                    }
                }
            }
        }
    }

    String getPerfettoTriggerName(String statName) {
        return "com.android.server.uilatencystats-" + statName;
    }

    @VisibleForTesting
    interface StatsLogWriter {
        void write(int code, int type, long duration);
    }

    @VisibleForTesting
    interface PerfettoTriggerCallback {
        void trigger(String name);
    }

    @VisibleForTesting
    void setStatsLogWriter(StatsLogWriter writer) {
        synchronized (mLock) {
            mStatsLogWriter = writer;
        }
    }

    @VisibleForTesting
    void setPerfettoTriggerCallback(PerfettoTriggerCallback callback) {
        synchronized (mLock) {
            mPerfettoTrigger = callback;
        }
    }

    @VisibleForTesting
    void setPerfettoTriggerThreshold(Duration duration) {
        synchronized (mLock) {
            mPerfettoTriggerThreshold = duration;
        }
    }
}
