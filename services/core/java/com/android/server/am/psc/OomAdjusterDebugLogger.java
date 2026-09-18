/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.server.am.psc;

import android.app.StackTrace;
import android.util.Slog;
import android.util.SparseBooleanArray;

import com.android.server.am.EventLogTags;

/**
 * Helper for writing debug log about proc/uid state changes.
 */
public class OomAdjusterDebugLogger {
    // Use the "am_" tag to make it similar to event logs.
    private static final String STACK_TRACE_TAG = "am_stack";

    private final OomAdjuster mOomAdjuster;
    private final OomAdjuster.Constants mOomConstants;

    private static final int MISC_SCHEDULE_IDLE_UIDS_MSG_1 = 1;
    private static final int MISC_SCHEDULE_IDLE_UIDS_MSG_2 = 2;
    private static final int MISC_SCHEDULE_IDLE_UIDS_MSG_3 = 3;

    private static final int MISC_SET_LAST_BG_TIME = 10;
    private static final int MISC_CLEAR_LAST_BG_TIME = 11;

    OomAdjusterDebugLogger(OomAdjuster oomAdjuster, OomAdjuster.Constants oomConstants) {
        mOomAdjuster = oomAdjuster;
        mOomConstants = oomConstants;
    }

    /** Checks if detailed process/UID state change logging is enabled for the specified UID. */
    public boolean shouldLog(int uid) {
        final SparseBooleanArray ar = mOomConstants.mProcStateDebugUids;
        final int size = ar.size();
        if (size == 0) { // Most common case.
            return false;
        }
        // If the array is small (also common), avoid the binary search.
        if (size <= 8) {
            for (int i = 0; i < size; i++) {
                if (ar.keyAt(i) == uid) {
                    return ar.valueAt(i);
                }
            }
            return false;
        }
        return ar.get(uid, false);
    }

    private void maybeLogStacktrace(String msg) {
        if (!mOomConstants.mEnableProcStateStacktrace) {
            return;
        }
        Slog.i(STACK_TRACE_TAG,
                msg + ": " + OomAdjuster.oomAdjReasonToString(mOomAdjuster.mLastReason),
                new StackTrace("Called here"));
    }

    private void maybeSleep(int millis) {
        if (millis == 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

    void logUidStateChanged(int uid, int uidstate, int olduidstate,
            int capability, int oldcapability, int flags) {
        EventLogTags.writeAmUidStateChanged(
                uid, mOomAdjuster.mAdjSeq, uidstate, olduidstate, capability, oldcapability, flags,
                OomAdjuster.oomAdjReasonToString(mOomAdjuster.mLastReason));
        maybeLogStacktrace("uidStateChanged");
        maybeSleep(mOomConstants.mProcStateDebugSetUidStateDelay);
    }

    void logProcStateChanged(int uid, int pid, int procstate, int oldprocstate,
            int oomadj, int oldoomadj) {
        EventLogTags.writeAmProcStateChanged(
                uid, pid, mOomAdjuster.mAdjSeq, procstate, oldprocstate, oomadj, oldoomadj,
                OomAdjuster.oomAdjReasonToString(mOomAdjuster.mLastReason));
        maybeLogStacktrace("procStateChanged");
        maybeSleep(mOomConstants.mProcStateDebugSetProcStateDelay);
    }

    /** Logs the scheduling of a UID to enter the idle state after a specified delay. */
    public void logScheduleUidIdle1(int uid, long delay) {
        EventLogTags.writeAmOomAdjMisc(MISC_SCHEDULE_IDLE_UIDS_MSG_1,
                uid, 0, mOomAdjuster.mAdjSeq, (int) delay, 0, "");
    }

    /**
     * Logs the scheduling of a specific process (UID/PID pair) to enter the idle state after a
     * specified delay.
     */
    public void logScheduleUidIdle2(int uid, int pid, long delay) {
        EventLogTags.writeAmOomAdjMisc(MISC_SCHEDULE_IDLE_UIDS_MSG_2,
                uid, pid, mOomAdjuster.mAdjSeq, (int) delay, 0, "");
    }

    /** Logs a generic scheduling event for UIDs to enter the idle state after a specified delay. */
    public void logScheduleUidIdle3(long delay) {
        EventLogTags.writeAmOomAdjMisc(MISC_SCHEDULE_IDLE_UIDS_MSG_3,
                0, 0, mOomAdjuster.mAdjSeq, (int) delay, 0, "");
    }

    /** Logs the event of setting the last background timestamp for a UID. */
    public void logSetLastBackgroundTime(int uid, long time) {
        EventLogTags.writeAmOomAdjMisc(MISC_SET_LAST_BG_TIME,
                uid, 0, mOomAdjuster.mAdjSeq, (int) time, 0,
                OomAdjuster.oomAdjReasonToString(mOomAdjuster.mLastReason));
    }

    void logClearLastBackgroundTime(int uid) {
        EventLogTags.writeAmOomAdjMisc(MISC_CLEAR_LAST_BG_TIME,
                uid, 0, mOomAdjuster.mAdjSeq, 0, 0,
                OomAdjuster.oomAdjReasonToString(mOomAdjuster.mLastReason));
    }
}
