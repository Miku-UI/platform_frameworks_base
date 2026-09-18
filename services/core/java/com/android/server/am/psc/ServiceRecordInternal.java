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
import android.content.pm.ServiceInfo;
import android.content.pm.ServiceInfo.ForegroundServiceType;
import android.os.Binder;

import java.util.ArrayList;

/**
 * Abstract base class for service records in the Activity Manager.
 * This class centralizes common service state fields that are essential for
 * process state management, particularly utilized by {@link OomAdjuster}.
 * TODO(b/425766486): Make setter methods package-private once OomAdjuster is migrated to psc.
 */
public abstract class ServiceRecordInternal extends Binder {
    /** The service component's per-instance name. */
    public final ComponentName instanceName;
    /** whether this is a sdk sandbox service */
    public final boolean isSdkSandbox;
    private final OomAdjuster.Constants mOomConstants;

    /** where this service is running or null. */
    private ProcessRecordInternal mHostProcess;

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
    private @ForegroundServiceType int mForegroundServiceType;

    /**
     * The last time (in uptime timebase) a bind request was made with BIND_ALMOST_PERCEPTIBLE for
     * this service while on TOP.
     */
    private long mLastTopAlmostPerceptibleBindRequestUptimeMs;

    /** Constant indicating that there is no short FGS start time recorded. */
    private static final long NO_SHORT_FGS_START_TIME = Long.MIN_VALUE;
    /**
     * The uptime timestamp when this service was started as a short foreground service.
     * A value of {@link #NO_SHORT_FGS_START_TIME} indicates it is not currently running as a short
     * FGS.
     */
    private long mShortFgsStartTime = NO_SHORT_FGS_START_TIME;

    public ServiceRecordInternal(ComponentName instanceName, final boolean isSdkSandbox,
            OomAdjuster.Constants oomConstants,
            long lastActivity) {
        this.instanceName = instanceName;
        this.isSdkSandbox = isSdkSandbox;
        mOomConstants = oomConstants;
        mLastActivity = lastActivity;
    }

    public boolean isStartRequested() {
        return mStartRequested;
    }

    void setStartRequested(boolean startRequested) {
        mStartRequested = startRequested;
    }

    public boolean isForeground() {
        return mIsForeground;
    }

    void setIsForeground(boolean isForeground) {
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

    void setLastActivity(long lastActivity) {
        mLastActivity = lastActivity;
    }

    public @ForegroundServiceType int getForegroundServiceType() {
        return mForegroundServiceType;
    }

    void setForegroundServiceType(@ForegroundServiceType int foregroundServiceType) {
        mForegroundServiceType = foregroundServiceType;
    }

    public long getLastTopAlmostPerceptibleBindRequestUptimeMs() {
        return mLastTopAlmostPerceptibleBindRequestUptimeMs;
    }

    void setLastTopAlmostPerceptibleBindRequestUptimeMs(
            long lastTopAlmostPerceptibleBindRequestUptimeMs) {
        mLastTopAlmostPerceptibleBindRequestUptimeMs = lastTopAlmostPerceptibleBindRequestUptimeMs;
    }

    protected long getShortFgsStartTime() {
        return mShortFgsStartTime;
    }

    void setShortFgsStartTime(long uptimeNow) {
        mShortFgsStartTime = uptimeNow;
    }

    /** Resets the start time for the short foreground service. */
    void clearShortFgsStartTime() {
        mShortFgsStartTime = NO_SHORT_FGS_START_TIME;
    }

    /** Checks if the service has a recorded start time as a short foreground service. */
    public boolean hasShortFgsStartTime() {
        return mShortFgsStartTime != NO_SHORT_FGS_START_TIME;
    }

    /** Time when the special procState granted due to the short FGS state should be demoted. */
    public long getShortFgsDemoteTime() {
        return mShortFgsStartTime + mOomConstants.mShortFgsTimeoutDuration
                + mOomConstants.mShortFgsProcStateExtraWaitDuration;
    }

    /**
     * @return true if it's a foreground service of the "short service" type and does not have
     * other FGS type bits set.
     */
    public boolean isShortFgs() {
        // Note if the type contains FOREGROUND_SERVICE_TYPE_SHORT_SERVICE but also other bits
        // set, it's _not_ considered be a short service. (because we shouldn't apply
        // the short-service restrictions)
        // (But we should be preventing mixture of FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        // and other types in Service.startForeground().)
        return isStartRequested() && isForeground() && (getForegroundServiceType()
                == ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
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
    public ProcessRecordInternal getHostProcessInternal() {
        return mHostProcess;
    }

    void setHostProcess(ProcessRecordInternal process) {
        mHostProcess = process;
    }

    /** Returns the isolation host process (e.g., for isolated or SDK sandbox processes). */
    public abstract ProcessRecordInternal getIsolationHostProcess();
}
