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

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.SystemClock;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;
import android.util.ArraySet;
import android.util.Slog;

import com.android.server.am.Flags;

import java.util.ArrayList;

/**
 * Base class for internal process service record state information.
 * This class provides common fields and methods for managing service-related properties
 * that influence process importance and OOM adjustment.
 */
@RavenwoodKeepWholeClass
public class ProcessServiceRecordInternal {
    public static final String TAG = "ProcessServiceRecordInternal";

    /** Controls whether argument validation checks are performed. */
    private static final boolean DEBUG_FGS_ARGS = false;

    /** Interface for observing changes in ProcessServiceRecordInternal state. */
    public interface Observer {
        /** Called when {@link #mHasClientActivities} changes. */
        void onHasClientActivitiesChanged(boolean hasClientActivities);

        /** Called when {@link #mHasForegroundServices} changes. */
        void onHasForegroundServicesChanged(boolean hasForegroundServices);
    }

    private final OomAdjuster.Constants mOomConstants;
    private final Observer mObserver;

    /** Last group set by a connection. */
    private int mConnectionGroup;
    /** Last importance set by a connection. */
    private int mConnectionImportance;
    /** Whether any process has bound to this process with the BIND_TREAT_LIKE_ACTIVITY flag. */
    private boolean mTreatLikeActivity;
    /** Whether this process has bound to a service with the BIND_ABOVE_CLIENT flag. */
    private boolean mHasAboveClient;
    /** The number of connections from this process with the BIND_ABOVE_CLIENT flag. */
    private int mBindAboveClientCount;
    /** Whether this process has any client services with activities. */
    private boolean mHasClientActivities;
    /** Do we need to be executing services in the foreground? */
    private boolean mExecServicesFg;

    /** Running any services that are foreground? */
    private boolean mHasForegroundServices;
    /**
     * The OR'ed foreground service types that are running on this process.
     * Note, because TYPE_NONE (==0) is also a valid type for pre-U apps, this field doesn't tell
     * if the process has any TYPE_NONE FGS or not, but {@link #mHasTypeNoneFgs} will be set
     * in that case.
     */
    private int mFgServiceTypes;
    /**
     * Whether the process has any foreground services of TYPE_NONE running.
     * @see #mFgServiceTypes
     */
    private boolean mHasTypeNoneFgs;

    /**
     * Running any services that are almost perceptible (started with
     * {@link Context#BIND_ALMOST_PERCEPTIBLE} while the app was on TOP)?
     */
    private boolean mHasTopStartedAlmostPerceptibleServices;
    /**
     * The latest value of
     * {@link ServiceRecordInternal#getLastTopAlmostPerceptibleBindRequestUptimeMs()} among the
     * currently running services.
     */
    private long mLastTopStartedAlmostPerceptibleBindRequestUptimeMs;

    /** All ServiceRecord running in this process. */
    final ArraySet<ServiceRecordInternal> mServices = new ArraySet<>();
    /** Services that are currently executing code (need to remain foreground). */
    private final ArraySet<ServiceRecordInternal> mExecutingServices = new ArraySet<>();
    /** All outgoing connections from this process. */
    private final ArraySet<ConnectionRecordInternal> mConnections = new ArraySet<>();
    /** All ConnectionRecord this process holds indirectly to SDK sandbox processes. */
    private @Nullable ArraySet<ConnectionRecordInternal> mSdkSandboxConnections;

    protected ProcessServiceRecordInternal(OomAdjuster.Constants oomConstants, Observer observer) {
        mOomConstants = oomConstants;
        mObserver = observer;
    }

    public int getConnectionGroup() {
        return mConnectionGroup;
    }

    void setConnectionGroup(int connectionGroup) {
        mConnectionGroup = connectionGroup;
    }

    public int getConnectionImportance() {
        return mConnectionImportance;
    }

    void setConnectionImportance(int connectionImportance) {
        mConnectionImportance = connectionImportance;
    }

    public boolean isTreatLikeActivity() {
        return mTreatLikeActivity;
    }

    void setTreatLikeActivity(boolean treatLikeActivity) {
        mTreatLikeActivity = treatLikeActivity;
    }

    /** Returns whether this process has any connections with the BIND_ABOVE_CLIENT flag. */
    public boolean hasBindAboveClient() {
        return mHasAboveClient;
    }

    void setHasAboveClient(boolean hasAboveClient) {
        mHasAboveClient = hasAboveClient;
    }

    /** Resets service-related flags when the application record is being cleaned up. */
    void onCleanupApplicationRecord() {
        setTreatLikeActivity(false);
        setHasAboveClient(false);
        mBindAboveClientCount = 0;
        setHasClientActivities(false);
    }

    /** Recalculates and updates the {@link #mHasAboveClient} flag. */
    void updateHasAboveClient() {
        setHasAboveClient(false);
        for (int i = numberOfConnections() - 1; i >= 0; i--) {
            final ConnectionRecordInternal cr = getConnectionInternalAt(i);
            final boolean isSameProcess = cr.getService().getHostProcessInternal() != null
                    && cr.getService().getHostProcessInternal().getServices() == this;
            if (!isSameProcess && cr.hasFlag(Context.BIND_ABOVE_CLIENT)) {
                setHasAboveClient(true);
                break;
            }
        }
    }

    /**
     * Sets whether this process has any client services with activities.
     * This method also notifies the registered observer of the change.
     */
    void setHasClientActivities(boolean hasClientActivities) {
        mHasClientActivities = hasClientActivities;
        mObserver.onHasClientActivitiesChanged(hasClientActivities);
    }

    /** Returns whether this process has any client services with activities. */
    public boolean hasClientActivities() {
        return mHasClientActivities;
    }

    public boolean isExecServicesFg() {
        return mExecServicesFg;
    }

    void setExecServicesFg(boolean execServicesFg) {
        mExecServicesFg = execServicesFg;
    }

    /** Checks if this process has any foreground services (even timed-out short-FGS) */
    public boolean hasForegroundServices() {
        return mHasForegroundServices;
    }

    /**
     * Returns the FGS types, but it doesn't tell if the types include "NONE" or not, use
     * {@link #hasForegroundServices()}
     */
    public int getForegroundServiceTypes() {
        return mHasForegroundServices ? mFgServiceTypes : 0;
    }

    public boolean getHasTypeNoneFgs() {
        return mHasTypeNoneFgs;
    }

    /** Returns whether the process has any FGS that are NOT a "short" FGS. */
    public boolean hasNonShortForegroundServices() {
        if (!mHasForegroundServices) {
            return false; // Process has no FGS running.
        }
        // Does the process has any FGS of TYPE_NONE?
        if (mHasTypeNoneFgs) {
            return true;
        }
        // If not, we can just check mFgServiceTypes.
        return mFgServiceTypes != ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE;
    }

    /**
     * Sets the foreground service status and types for this process.
     * This method also notifies the registered observer of the change.
     */
    void setHasForegroundServices(boolean hasForegroundServices, int fgServiceTypes,
            boolean hasTypeNoneFgs) {
        // hasForegroundServices should be the same as "either it has any FGS types, or none types".
        // We still take this as a parameter because it's used in the call site...
        if (DEBUG_FGS_ARGS && hasForegroundServices != ((fgServiceTypes != 0) || hasTypeNoneFgs)) {
            throw new IllegalStateException("Argument mismatch: "
                    + "hasForegroundServices=" + hasForegroundServices
                    + ", fgServiceTypes=" + fgServiceTypes
                    + ", hasTypeNoneFgs=" + hasTypeNoneFgs);
        }

        mHasForegroundServices = hasForegroundServices;
        mFgServiceTypes = fgServiceTypes;
        mHasTypeNoneFgs = hasTypeNoneFgs;
        mObserver.onHasForegroundServicesChanged(mHasForegroundServices);
    }

    public boolean getHasTopStartedAlmostPerceptibleServices() {
        return mHasTopStartedAlmostPerceptibleServices;
    }

    void setHasTopStartedAlmostPerceptibleServices(boolean value) {
        mHasTopStartedAlmostPerceptibleServices = value;
    }

    public long getLastTopStartedAlmostPerceptibleBindRequestUptimeMs() {
        return mLastTopStartedAlmostPerceptibleBindRequestUptimeMs;
    }

    void setLastTopStartedAlmostPerceptibleBindRequestUptimeMs(long value) {
        mLastTopStartedAlmostPerceptibleBindRequestUptimeMs = value;
    }

    /**
     * Recalculates and updates the {@link #mHasTopStartedAlmostPerceptibleServices} flag
     * and {@link #mLastTopStartedAlmostPerceptibleBindRequestUptimeMs} based on the
     * currently running services in this process.
     *
     * It iterates through all running services to determine if any are considered
     * "almost perceptible" and updates the latest bind request uptime.
     */
    void updateHasTopStartedAlmostPerceptibleServices() {
        mHasTopStartedAlmostPerceptibleServices = false;
        mLastTopStartedAlmostPerceptibleBindRequestUptimeMs = 0;
        for (int s = numberOfRunningServices() - 1; s >= 0; --s) {
            final ServiceRecordInternal sr = getRunningServiceInternalAt(s);
            mLastTopStartedAlmostPerceptibleBindRequestUptimeMs = Math.max(
                    mLastTopStartedAlmostPerceptibleBindRequestUptimeMs,
                    sr.getLastTopAlmostPerceptibleBindRequestUptimeMs());
            if (!mHasTopStartedAlmostPerceptibleServices && isAlmostPerceptible(sr)) {
                mHasTopStartedAlmostPerceptibleServices = true;
            }
        }
    }

    /**
     * Checks if this process currently has or recently had a service that was started as
     * "almost perceptible" (via {@link Context#BIND_ALMOST_PERCEPTIBLE}) while the app was in
     * the TOP state.
     */
    public boolean hasTopStartedAlmostPerceptibleServices() {
        return mHasTopStartedAlmostPerceptibleServices
                || (mLastTopStartedAlmostPerceptibleBindRequestUptimeMs > 0
                && SystemClock.uptimeMillis() - mLastTopStartedAlmostPerceptibleBindRequestUptimeMs
                < mOomConstants.mServiceBindAlmostPerceptibleTimeoutMs);
    }

    /**
     * @return if this process:
     * - has at least one short-FGS
     * - has no other types of FGS
     * - and all the short-FGSes are procstate-timed out.
     */
    public boolean areAllShortForegroundServicesProcstateTimedOut(long nowUptime) {
        if (!hasForegroundServices()) { // Process has no FGS?
            return false;
        }
        if (hasNonShortForegroundServices()) {  // Any non-short FGS running?
            return false;
        }
        // Now we need to look at all short-FGS within the process and see if all of them are
        // procstate-timed-out or not.
        return !hasUndemotedShortForegroundService(nowUptime);
    }

    private boolean hasUndemotedShortForegroundService(long nowUptime) {
        for (int i = numberOfRunningServices() - 1; i >= 0; i--) {
            final ServiceRecordInternal sr = getRunningServiceInternalAt(i);
            if (!sr.isShortFgs() || !sr.hasShortFgsStartTime()) {
                continue;
            }
            if (sr.getShortFgsDemoteTime() >= nowUptime) {
                // This short fgs has not timed out yet.
                return true;
            }
        }
        return false;
    }

    /** Returns the number of services currently running in this process. */
    public int numberOfRunningServices() {
        return mServices.size();
    }

    /** Retrieves the {@link ServiceRecordInternal} for a running service at the specified index. */
    public ServiceRecordInternal getRunningServiceInternalAt(int index) {
        return mServices.valueAt(index);
    }

    /** Adds the specified service to the set of running services for this process. */
    boolean addRunningService(ServiceRecordInternal service) {
        return mServices.add(service);
    }

    /** Removes the specified service from the set of running services for this process. */
    boolean removeRunningService(ServiceRecordInternal service) {
        return mServices.remove(service);
    }

    /** Stops all services running in this process. */
    void stopAllServices() {
        mServices.clear();
        updateHasTopStartedAlmostPerceptibleServices();
    }

    /** Checks if there are any services currently executing in this process. */
    public boolean hasExecutingServices() {
        return !mExecutingServices.isEmpty();
    }

    /** Returns the number of services that are currently executing code in this process. */
    public int numberOfExecutingServices() {
        return mExecutingServices.size();
    }

    /** Retrieves the executing service at the specified index. */
    protected ServiceRecordInternal getExecutingServiceInternalAt(int index) {
        return mExecutingServices.valueAt(index);
    }

    /** Adds a service to the set of services that are currently executing code. */
    void startExecutingService(ServiceRecordInternal service) {
        mExecutingServices.add(service);
    }

    /** Removes a service from the set of services that are currently executing code. */
    void stopExecutingService(ServiceRecordInternal service) {
        mExecutingServices.remove(service);
    }

    /** Clears the set of all executing services. */
    void stopAllExecutingServices() {
        mExecutingServices.clear();
    }

    /** Returns the number of active connections to services in this process. */
    public int numberOfConnections() {
        return mConnections.size();
    }

    /** Retrieves the {@link ConnectionRecordInternal} at the specified index. */
    public ConnectionRecordInternal getConnectionInternalAt(int index) {
        return mConnections.valueAt(index);
    }

    /**
     * Adds an outgoing connection from this process to a service.
     * This also handles the special logic for connections to services running in an SDK sandbox.
     */
    void addConnection(ConnectionRecordInternal connection) {
        if (mConnections.add(connection)) {
            addSdkSandboxConnectionIfNecessary(connection);

            // Update internal state for connections with the BIND_ABOVE_CLIENT flag set.
            if (connection.hasFlag(Context.BIND_ABOVE_CLIENT) && !connection.isBindingToSelf()) {
                setHasAboveClient(true);
                // Shadow verification for incremental reference counting.
                if (Flags.incrementalHasAboveClient()) {
                    mBindAboveClientCount++;
                    verifyBindAboveClient("addConnection", connection.getAttributedClient());
                }
            }
        }
    }

    /**
     * Removes an outgoing connection from this process.
     * This also handles the necessary cleanup for connections to services in an SDK sandbox.
     */
    void removeConnection(ConnectionRecordInternal connection) {
        if (mConnections.remove(connection)) {
            removeSdkSandboxConnectionIfNecessary(connection);

            // Update internal state for connections with the BIND_ABOVE_CLIENT flag set.
            if (connection.hasFlag(Context.BIND_ABOVE_CLIENT) && !connection.isBindingToSelf()) {
                updateHasAboveClient();
                // Shadow verification for incremental reference counting.
                if (Flags.incrementalHasAboveClient()) {
                    mBindAboveClientCount--;
                    if (mBindAboveClientCount < 0) { // Defensive check.
                        Slog.wtf(TAG, "mBindAboveClientCount went below 0 for "
                                + connection.getAttributedClient());
                        mBindAboveClientCount = 0;
                    }
                    verifyBindAboveClient("removeConnection", connection.getAttributedClient());
                }
            }
        }
    }

    /**
     * Removes all outgoing connections from this process.
     * This clears the connection set and performs necessary cleanup for any SDK sandbox
     * connections.
     */
    void removeAllConnections() {
        for (int i = 0, size = mConnections.size(); i < size; i++) {
            removeSdkSandboxConnectionIfNecessary(mConnections.valueAt(i));
        }
        mConnections.clear();
        setHasAboveClient(false);
        mBindAboveClientCount = 0;
    }

    private void addSdkSandboxConnectionIfNecessary(ConnectionRecordInternal connection) {
        final ProcessRecordInternal attributedClient = connection.getAttributedClient();
        if (attributedClient != null && connection.getService().isSdkSandbox) {
            attributedClient.getServices().addSdkSandboxConnection(connection);
        }
    }

    private void removeSdkSandboxConnectionIfNecessary(ConnectionRecordInternal connection) {
        final ProcessRecordInternal attributedClient = connection.getAttributedClient();
        if (attributedClient != null && connection.getService().isSdkSandbox) {
            attributedClient.getServices().removeSdkSandboxConnection(connection);
        }
    }

    /** Returns the number of active connections to services within SDK sandbox processes. */
    public int numberOfSdkSandboxConnections() {
        return mSdkSandboxConnections != null ? mSdkSandboxConnections.size() : 0;
    }

    /**
     * Retrieves the {@link ConnectionRecordInternal} for an SDK sandbox process at the
     * specified index.
     */
    public ConnectionRecordInternal getSdkSandboxConnectionInternalAt(int index) {
        return mSdkSandboxConnections != null ? mSdkSandboxConnections.valueAt(index) : null;
    }

    /** Adds a connection record that this process holds indirectly to an SDK sandbox process. */
    void addSdkSandboxConnection(ConnectionRecordInternal connection) {
        if (mSdkSandboxConnections == null) {
            mSdkSandboxConnections = new ArraySet<>();
        }
        mSdkSandboxConnections.add(connection);
    }

    /** Removes a connection record to a service running in an SDK sandbox. */
    void removeSdkSandboxConnection(ConnectionRecordInternal connection) {
        if (mSdkSandboxConnections != null) {
            mSdkSandboxConnections.remove(connection);
        }
    }

    /** Removes all tracked connection records to services running in SDK sandboxes. */
    void removeAllSdkSandboxConnections() {
        if (mSdkSandboxConnections != null) {
            mSdkSandboxConnections.clear();
        }
    }

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

    private void verifyBindAboveClient(String op, ProcessRecordInternal processRecordInternal) {
        if ((mBindAboveClientCount > 0) != mHasAboveClient) {
            Slog.wtf(TAG, "hasAboveClient inconsistency during " + op
                    + "! NewCount=" + mBindAboveClientCount
                    + " LegacyBoolean=" + mHasAboveClient + " for " + processRecordInternal);
        }
    }
}
