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

import android.app.ActivityManagerInternal;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;
import android.util.Slog;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * A {@link BatchSession} that will synchronously trigger a {@link ProcessStateController} update
 * at the end of the session.
 */
@RavenwoodKeepWholeClass
public class SyncBatchSession extends BatchSession {
    private static final String TAG = "SyncBatchSession";
    private final Consumer<ProcessRecordInternal> mEnqueuer;
    private final IntConsumer mFullUpdater;
    private final IntConsumer mPartialUpdater;
    private boolean mShouldUpdate = false;
    private boolean mFullUpdate = false;

    /**
     * A data structure for tracking when to skip an update due to process state change. This
     * decision should come from the
     * {@link com.android.server.am.ActiveServices.ServiceBindingOomAdjPolicy}.
     *
     * Note:
     * The outer ArrayList is a stack that corresponds to the current BatchSession nest depth.
     * The inner ArrayList is the list of skippable processes for that nest depth. An ArrayList
     * is used over an ArraySet to optimize for the size of 1 scenario, which should be the norm.
     */
    private final ArrayList<ArrayList<ProcessRecordInternal>> mServiceBindPolicySkippedProcsStack =
            new ArrayList<>();

    public SyncBatchSession(Consumer<ProcessRecordInternal> enqueuer,
            IntConsumer fullUpdater, IntConsumer partialUpdater) {
        mEnqueuer = enqueuer;
        mFullUpdater = fullUpdater;
        mPartialUpdater = partialUpdater;
    }

    private boolean mOutOfBoundsEnqueueWtfArmed = true;
    /**
     * Note that a proc should be enqueued for updating. The process enqueue may be ignored due to
     * policy.
     */
    public void maybeEnqueueProcess(ProcessRecordInternal proc) {
        if (!isActive()) {
            if (mOutOfBoundsEnqueueWtfArmed) {
                Slog.wtfStack(TAG,
                        "Unexpected maybeEnqueueProcess called while session is not active");
                mOutOfBoundsEnqueueWtfArmed = false;
            }
            return;
        }
        if (proc == null) return;
        if (getCurrentSkippedProcList().contains(proc)) {
            // Proc was marked as skip by the ServiceBindPolicy, don't enqueue it.
            return;
        }
        mEnqueuer.accept(proc);
        mShouldUpdate = true;
    }

    private boolean mOutOfBoundsSetFullUpdateWtfArmed = true;
    /** Note that the update at the end of the session needs to be a full update. */
    public void setFullUpdate() {
        if (!isActive()) {
            if (mOutOfBoundsSetFullUpdateWtfArmed) {
                Slog.wtfStack(TAG, "Unexpected setFullUpdate called while session is not active");
                mOutOfBoundsSetFullUpdateWtfArmed = false;
            }
            return;
        }
        mShouldUpdate = true;
        mFullUpdate = true;
    }

    private boolean mOutOfBoundsBindPolicyWtfArmed = true;
    /**
     * Note that a process should not be included for an OomAdjuster update for the rest of the
     * current BatchSession. For nested BatchSessions, the skipping will only apply to the
     * current nest level. If the proc needs to be skipped in other levels, this method will need
     * to be called again in the relevant level.
     * TODO: b/448464452 - Migrate the update skipping policy into ProcessStateController.
     *   ProcessStateController should have enough information to make the same evaluation as
     *   ServiceBindPolicy.
     */
    public void skipProcDueToServiceBindPolicy(ProcessRecordInternal proc) {
        if (!isActive()) {
            if (mOutOfBoundsBindPolicyWtfArmed) {
                Slog.wtfStack(TAG,
                        "Unexpected skipProcDueToServiceBindPolicy while session is not active");
                mOutOfBoundsBindPolicyWtfArmed = false;
            }
            return;
        }

        getCurrentSkippedProcList().add(proc);
    }

    @Override
    protected void onNestedStart() {
        final int preAllocatedStackSize = mServiceBindPolicySkippedProcsStack.size();
        if (depth() < preAllocatedStackSize) {
            // A proc list was already instantiated at this depth. Reuse it to avoid an object
            // allocation.
            getCurrentSkippedProcList().clear();
        } else if (depth() == preAllocatedStackSize) {
            // The stack has hit a new depth. Allocate a new proc list for this depth.
            mServiceBindPolicySkippedProcsStack.add(new ArrayList<>());
        } else {
            // This should be impossible. Log and attempt to recover.
            Slog.wtf(TAG, "onNestedStart called for a depth of " + depth()
                    + " when the process skip stack is of size " + preAllocatedStackSize);
            while (mServiceBindPolicySkippedProcsStack.size() <= depth()) {
                mServiceBindPolicySkippedProcsStack.add(new ArrayList<>());
            }
        }
    }

    @Override
    protected void onLastClose() {
        updateIfNecessary(mUpdateReason);
    }

    /**
     * Calling will trigger an update mid batch session, if necessary.
     * TODO: b/468431644 - This method defeats the purpose of having a batch session and usages
     *  of it should be removed when ActiveService is restructured to better utilize batch sessions.
     */
    public void triggerUpdate(@ActivityManagerInternal.OomAdjReason int reason) {
        if (!isActive()) {
            Slog.wtfStack(TAG,
                    "Attempted to forceUpdate while SyncBatchSession is not active! reason:"
                            + reason);
            return;
        }
        updateIfNecessary(reason);
    }

    private void updateIfNecessary(@ActivityManagerInternal.OomAdjReason int reason) {
        if (!mShouldUpdate) return;
        mShouldUpdate = false;
        if (mFullUpdate) {
            // Full update was triggered, reset the flag and run a full update.
            mFullUpdate = false;
            mFullUpdater.accept(reason);
        } else {
            // Otherwise, run a partial update on anything that may have been enqueued.
            mPartialUpdater.accept(reason);
        }
    }

    private ArrayList<ProcessRecordInternal> getCurrentSkippedProcList() {
        return mServiceBindPolicySkippedProcsStack.get(depth());
    }
}

