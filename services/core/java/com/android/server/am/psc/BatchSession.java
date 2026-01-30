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
package com.android.server.am.psc;

import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_COUNT;

import static com.android.server.am.OomAdjuster.oomAdjReasonToStringSuffix;

import android.app.ActivityManagerInternal.OomAdjReason;
import android.os.Trace;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;
import android.util.Slog;

import java.util.Arrays;

/**
 * A simple tool for tracking the concept of a process state change "session". Session starts can
 * be nested and a session is considered active until every start has an accompanying close.
 *
 * A BatchSession must be active when a method annotated with
 * {@link com.android.server.am.psc.annotation.RequiresEnclosingBatchSession} is called.
 *
 * BatchSession is not thread-safe and should only be used within the same thread or lock.
 */
@RavenwoodKeepWholeClass
public abstract class BatchSession implements AutoCloseable {
    private static final String TAG = "BatchSession";
    public static final String[] BATCH_SESSION_TAGS = new String[OOM_ADJ_REASON_COUNT];
    static {
        Arrays.setAll(BATCH_SESSION_TAGS,
                i -> "BatchSession:" + oomAdjReasonToStringSuffix(i));
    }
    private int mNestedStartCount = 0;
    protected @OomAdjReason int mUpdateReason;

    /** Start a session with a given reason. Nested start reasons will be ignored. */
    public void start(@OomAdjReason int reason) {
        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, BATCH_SESSION_TAGS[reason]);
        if (mNestedStartCount == 0) {
            // This is the outermost batch session, use the reason provided here.
            mUpdateReason = reason;
        }
        mNestedStartCount++;
    }

    /** Session is currently active. ProcessStateController updates should skipped. */
    public boolean isActive() {
        return mNestedStartCount > 0;
    }

    @Override
    public void close() {
        if (!isActive()) {
            Slog.wtfStack(TAG, "close() called on an unstarted BatchSession!");
            return;
        }

        mNestedStartCount--;

        if (!isActive()) {
            // This is the end of the outermost session, run the end of session behavior.
            onClose();
        }
        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
    }

    /** What behavior to perform on the last close of an active session. */
    protected abstract void onClose();
}
