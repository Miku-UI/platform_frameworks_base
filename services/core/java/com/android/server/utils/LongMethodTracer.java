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

package com.android.server.utils;

import static android.os.Trace.TRACE_TAG_ACTIVITY_MANAGER;

import android.os.Trace;
import android.util.Slog;

import com.android.internal.annotations.Keep;

/**
 * Triggers long method tracing in a process for a fixed duration.
 *
 * <p>This uses a native signal-based mechanism to request tracing in the target process. The actual
 * signal and delivery mechanism are abstracted away.
 *
 * <p>The collected tracing information currently appears in the ANR report if the traced process
 * encounters an ANR during or after the tracing window.
 *
 * @hide
 */
public class LongMethodTracer {
    private static final String TAG = "LongMethodTracer";

    /**
     * Requests method tracing in the given process.
     *
     * @param pid The target process ID to trace.
     * @param durationMs The tracing duration in milliseconds.
     * @return true if the request was successfully sent; false otherwise.
     */
    @Keep
    public static boolean trigger(int pid, int durationMs) {
        if (!Flags.longMethodTrace()) {
            return false;
        }

        if (pid <= 0) {
            throw new IllegalArgumentException("Invalid PID: " + pid);
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("Duration must be positive: " + durationMs);
        }

        Trace.traceBegin(TRACE_TAG_ACTIVITY_MANAGER,
                "LongMethodTracer#trigger()");
        Slog.i(TAG, "Triggering long method tracing for pid:" + pid);

        boolean result = nativeTrigger(pid, durationMs);
        if (!result) {
            Slog.w(TAG, "Failed to trigger long method tracing for pid " + pid);
        }
        Trace.traceEnd(TRACE_TAG_ACTIVITY_MANAGER);
        return result;
    }

    private static native boolean nativeTrigger(int pid, int durationMs);

}
