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

import android.content.Context;

import java.util.ArrayList;

/**
 * Base class for internal process service record state information.
 * This class provides common fields and methods for managing service-related properties
 * that influence process importance and OOM adjustment.
 */
public abstract class ProcessServiceRecordInternal {
    /** Last group set by a connection. */
    private int mConnectionGroup;
    /** Last importance set by a connection. */
    private int mConnectionImportance;
    /** Whether any process has bound to this process with the BIND_TREAT_LIKE_ACTIVITY flag. */
    private boolean mTreatLikeActivity;
    /** Whether this process has bound to a service with the BIND_ABOVE_CLIENT flag. */
    private boolean mHasAboveClient;
    /** Do we need to be executing services in the foreground? */
    private boolean mExecServicesFg;

    public int getConnectionGroup() {
        return mConnectionGroup;
    }

    public void setConnectionGroup(int connectionGroup) {
        mConnectionGroup = connectionGroup;
    }

    public int getConnectionImportance() {
        return mConnectionImportance;
    }

    public void setConnectionImportance(int connectionImportance) {
        mConnectionImportance = connectionImportance;
    }

    public boolean isTreatLikeActivity() {
        return mTreatLikeActivity;
    }

    public void setTreatLikeActivity(boolean treatLikeActivity) {
        mTreatLikeActivity = treatLikeActivity;
    }

    public boolean isHasAboveClient() {
        return mHasAboveClient;
    }

    public void setHasAboveClient(boolean hasAboveClient) {
        mHasAboveClient = hasAboveClient;
    }

    public boolean isExecServicesFg() {
        return mExecServicesFg;
    }

    public void setExecServicesFg(boolean execServicesFg) {
        mExecServicesFg = execServicesFg;
    }

    /** Returns the number of services currently running in this process. */
    public abstract int numberOfRunningServices();

    /** Retrieves the {@link ServiceRecordInternal} for a running service at the specified index. */
    public abstract ServiceRecordInternal getRunningServiceAt(int index);

    /** Returns the number of active connections to services in this process. */
    public abstract int numberOfConnections();

    /** Retrieves the {@link ConnectionRecordInternal} at the specified index. */
    public abstract ConnectionRecordInternal getConnectionAt(int index);

    /** Returns the number of active connections to services within SDK sandbox processes. */
    public abstract int numberOfSdkSandboxConnections();

    /**
     * Retrieves the {@link ConnectionRecordInternal} for an SDK sandbox process at the
     * specified index.
     */
    public abstract ConnectionRecordInternal getSdkSandboxConnectionAt(int index);

    /** Checks if there are any services currently executing in this process. */
    public abstract boolean hasExecutingServices();

    /** Checks if this process has any foreground services (even timed-out short-FGS) */
    public abstract boolean hasForegroundServices();

    protected static boolean isAlmostPerceptible(ServiceRecordInternal record) {
        if (record.getLastTopAlmostPerceptibleBindRequestUptimeMs() <= 0) {
            return false;
        }
        for (int i = record.getConnectionsSize() - 1; i >= 0; --i) {
            final ArrayList<? extends ConnectionRecordInternal> clist = record.getConnectionAt(i);
            for (int j = clist.size() - 1; j >= 0; --j) {
                final ConnectionRecordInternal cr = clist.get(j);
                if (cr.hasFlag(Context.BIND_ALMOST_PERCEPTIBLE)) {
                    return true;
                }
            }
        }
        return false;
    }
}
