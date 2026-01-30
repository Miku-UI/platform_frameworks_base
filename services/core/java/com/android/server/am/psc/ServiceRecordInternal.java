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

import android.content.ComponentName;
import android.os.Binder;

import java.util.ArrayList;

/**
 * Abstract base class for service records in the Activity Manager.
 * This class centralizes common service state fields that are essential for
 * process state management, particularly utilized by {@link com.android.server.am.OomAdjuster}.
 * TODO(b/425766486): Make setter methods package-private once OomAdjuster is migrated to psc.
 */
public abstract class ServiceRecordInternal extends Binder {
    /** The service component's per-instance name. */
    public final ComponentName instanceName;

    /** Whether the service has been explicitly requested to start by an application. */
    private boolean mStartRequested;
    /** Whether the service is currently running in foreground mode. */
    private boolean mIsForeground;
    /**
     * Whether the service is designated to keep its host process warm to maintain critical
     * code paths.
     */
    protected boolean mKeepWarming;
    /** The last time there was notable activity associated with this service. */
    private long mLastActivity;
    /** The bitmask of foreground service types declared for this service. */
    private int mForegroundServiceType;

    /**
     * The last time (in uptime timebase) a bind request was made with BIND_ALMOST_PERCEPTIBLE for
     * this service while on TOP.
     */
    private long mLastTopAlmostPerceptibleBindRequestUptimeMs;

    public ServiceRecordInternal(ComponentName instanceName, long lastActivity) {
        this.instanceName = instanceName;
        mLastActivity = lastActivity;
    }

    public boolean isStartRequested() {
        return mStartRequested;
    }

    public void setStartRequested(boolean startRequested) {
        mStartRequested = startRequested;
    }

    public boolean isForeground() {
        return mIsForeground;
    }

    public void setIsForeground(boolean isForeground) {
        this.mIsForeground = isForeground;
    }

    public boolean isKeepWarming() {
        return mKeepWarming;
    }

    /** Updates the {@link #mKeepWarming} state for this service. */
    public abstract void updateKeepWarmLocked();

    public long getLastActivity() {
        return mLastActivity;
    }

    public void setLastActivity(long lastActivity) {
        mLastActivity = lastActivity;
    }

    public int getForegroundServiceType() {
        return mForegroundServiceType;
    }

    public void setForegroundServiceType(int foregroundServiceType) {
        mForegroundServiceType = foregroundServiceType;
    }

    public long getLastTopAlmostPerceptibleBindRequestUptimeMs() {
        return mLastTopAlmostPerceptibleBindRequestUptimeMs;
    }

    public void setLastTopAlmostPerceptibleBindRequestUptimeMs(
            long lastTopAlmostPerceptibleBindRequestUptimeMs) {
        mLastTopAlmostPerceptibleBindRequestUptimeMs = lastTopAlmostPerceptibleBindRequestUptimeMs;
    }
    /**
     * Checks if this foreground service is allowed to access "while-in-use" permissions
     * (e.g., location, camera, microphone) for capability determination.
     */
    public abstract boolean isFgsAllowedWiu_forCapabilities();

    /** Returns the number of connections to this service. */
    public abstract int getConnectionsSize();

    /** Returns the list of connections for a given index. */
    public abstract ArrayList<? extends ConnectionRecordInternal> getConnectionAt(int index);

    /** Returns the host process that hosts this service. */
    public abstract ProcessRecordInternal getHostProcess();

    /** Returns the isolation host process (e.g., for isolated or SDK sandbox processes). */
    public abstract ProcessRecordInternal getIsolationHostProcess();
}
