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

import android.annotation.NonNull;
import android.app.ActivityManager.ProcessState;
import android.content.Context;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;
import android.util.Slog;

import com.android.server.am.Flags;
import com.android.server.wm.ActivityServiceConnectionsHolder;

/**
 * An abstract base class encapsulating common internal properties and state for a single binding
 * to a service.
 */
@RavenwoodKeepWholeClass
public abstract class ConnectionRecordInternal implements OomAdjusterImpl.Connection {
    private static final String TAG = "ConnectionRecordInternal";

    /** The service binding operation. */
    private long mFlags;
    /** Whether there are currently ongoing transactions over this service connection. */
    private boolean mOngoingCalls;
    /** The associated bound service session if created. */
    private BoundServiceSession mBoundServiceSession;
    /**
     * The service binding edge from the service client to the host.
     * This is {@code null} unless {@link Flags#enableCapabilityControllerComputation} is true.
     */
    private final ServiceBindingEdge mServiceBindingEdge;

    /** Returns the {@link ActivityServiceConnectionsHolder} associated with this connection. */
    public abstract ActivityServiceConnectionsHolder getActivity();

    /** Returns the service associated with this connection. */
    public abstract ServiceRecordInternal getService();

    /** Returns the client process associated with this connection. */
    public abstract @NonNull ProcessRecordInternal getClient();

    /**
     * Returns the attributed client process associated with this connection, if applicable (e.g.,
     * for SDK Sandbox).
     */
    public abstract ProcessRecordInternal getAttributedClient();

    /** Tracks the current process state and sequence number for association management. */
    public abstract void trackProcState(@ProcessState int procState, int seq);

    /** Returns a concise string representation of this record for logging and debugging. */
    public abstract String toShortString();

    public ConnectionRecordInternal(long flags) {
        this.mFlags = flags;

        if (Flags.enableCapabilityControllerComputation()) {
            mServiceBindingEdge = new ServiceBindingEdge(this);
        } else {
            mServiceBindingEdge = null;
        }
    }

    public long getFlags() {
        return mFlags;
    }

    /**
     * Checks if any of specific flags (int) is set for this connection.
     * Because the bind flags are bitwise flags, we can check for multiple flags by
     * bitwise-ORing them together (e.g., {@code hasFlag(FLAG1 | FLAG2)}). In this
     * case, the method returns true if *any* of the specified flags are present.
     */
    public boolean hasFlag(final int flag) {
        return hasFlag(Integer.toUnsignedLong(flag));
    }

    /**
     * Checks if any of specific flags (long) is set for this connection.
     * Because the bind flags are bitwise flags, we can check for multiple flags by
     * bitwise-ORing them together (e.g., {@code hasFlag(FLAG1 | FLAG2)}). In this
     * case, the method returns true if *any* of the specified flags are present.
     */
    public boolean hasFlag(final long flag) {
        return (mFlags & flag) != 0;
    }

    /** Checks if all of the specific flags (int) are NOT set for this connection. */
    public boolean notHasFlag(final int flag) {
        return !hasFlag(flag);
    }

    /** Checks if all of the specific flag (long) are NOT set for this connection. */
    public boolean notHasFlag(final long flag) {
        return !hasFlag(flag);
    }

    /**
     * Updates flags to match the new set, only adding or removing those
     * within Context.BIND_UPDATEABLE_FLAGS.
     *
     * @return The combined set of flags that were modified.
     */
    boolean updateFlags(final long newFlags) {
        final long updatedFlags = mFlags ^ newFlags;
        if (updatedFlags != (updatedFlags & Context.BIND_UPDATEABLE_FLAGS)) {
            Slog.wtf(TAG, "Attempt to update flags outside of BIND_UPDATEABLE_FLAGS with "
                            + updatedFlags);
            return false;
        }

        mFlags = newFlags;
        return updatedFlags != 0;
    }

    /** Checks if this connection is binding to the same process. */
    public boolean isBindingToSelf() {
        final ProcessRecordInternal host = getService().getHostProcessInternal();
        if (host == null) {
            return false;
        }
        // Check if the host process is the same as the client process.
        return host == getClient();
    }

    public boolean getOngoingCalls() {
        return mOngoingCalls;
    }

    /** Sets the ongoing calls status for this connection. Returns true if the status is changed. */
    boolean setOngoingCalls(boolean ongoingCalls) {
        if (mOngoingCalls != ongoingCalls) {
            mOngoingCalls = ongoingCalls;
            return true;
        }
        return false;
    }

    public BoundServiceSession getBoundServiceSession() {
        return mBoundServiceSession;
    }

    void setBoundServiceSession(BoundServiceSession boundServiceSession) {
        mBoundServiceSession = boundServiceSession;
    }

    /** This is {@code null} unless {@link Flags#enableCapabilityControllerComputation} is true. */
    final ServiceBindingEdge getServiceBindingEdge() {
        return mServiceBindingEdge;
    }

    @Override
    public void computeHostOomAdjLSP(OomAdjuster oomAdjuster, ProcessRecordInternal host,
            ProcessRecordInternal client, long now, ProcessRecordInternal topApp, boolean doingAll,
            int oomAdjReason, int cachedAdj) {
        oomAdjuster.computeServiceHostOomAdjLSP(this, host, client, now, false);
    }

    @Override
    public boolean canAffectCapabilities() {
        return hasFlag(Context.BIND_INCLUDE_CAPABILITIES
                | Context.BIND_BYPASS_USER_NETWORK_RESTRICTIONS);
    }

    @Override
    public int cpuTimeTransmissionType() {
        if (mOngoingCalls) {
            return CPU_TIME_TRANSMISSION_NORMAL;
        }
        if (hasFlag(Context.BIND_ALLOW_FREEZE)) {
            return CPU_TIME_TRANSMISSION_NONE;
        }
        return hasFlag(Context.BIND_SIMULATE_ALLOW_FREEZE) ? CPU_TIME_TRANSMISSION_LEGACY
                : CPU_TIME_TRANSMISSION_NORMAL;
    }
}
