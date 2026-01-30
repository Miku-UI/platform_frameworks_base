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

import android.annotation.ElapsedRealtimeLong;
import android.app.ActivityManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.TimeUtils;

import com.android.internal.annotations.CompositeRWLock;
import com.android.internal.annotations.GuardedBy;

/**
 * Represents overall state information for a UID that has actively running processes,
 * specifically focusing on fields and logic relevant to OomAdjuster.
 * This class abstracts the common UID-level process state, capabilities, and
 * idle/background tracking used for Out-Of-Memory (OOM) adjustment decisions
 * and process state management.
 *
 * It is an abstract base class, designed to be extended by concrete UID records
 * (e.g., {@link com.android.server.am.UidRecord}) that manage the actual
 * collection of processes for the UID.
 */
public abstract class UidRecordInternal {
    protected final Object mService;
    protected final Object mProcLock;

    /** The UID represented by this record. */
    protected final int mUid;

    /**
     * The minimum (i.e. most important) process state of the non-isolated processes under the UID
     * at the current round of computation.
     */
    @CompositeRWLock({"mService", "mProcLock"})
    protected int mCurProcState;

    /**
     * The minimum (i.e. most important) process state of the non-isolated processes under the UID
     * at the last round of computation.
     * The value will be updated to {@link #mCurProcState} when the computation is done.
     */
    @CompositeRWLock({"mService", "mProcLock"})
    protected int mSetProcState = ActivityManager.PROCESS_STATE_NONEXISTENT;

    /** Whether a process adjustment has changed for this UID since the last check. */
    @CompositeRWLock({"mService", "mProcLock"})
    protected boolean mProcAdjChanged;

    /** The aggregated capability flags for this UID at the current round of computation. */
    @CompositeRWLock({"mService", "mProcLock"})
    protected int mCurCapability;

    /**
     * The aggregated capability flags for this UID at the last round of computation.
     * The value will be updated to {@link #mCurCapability} when the computation is done.
     */
    @CompositeRWLock({"mService", "mProcLock"})
    protected int mSetCapability;

    /**
     * The elapsed real-time when the UID last went into the background.
     * A value of 0 indicates it has not been in the background or its background
     * time is not tracked.
     */
    @CompositeRWLock({"mService", "mProcLock"})
    protected long mLastBackgroundTime;

    /** Last time the UID became idle. Set to 0 when the UID becomes active. */
    @ElapsedRealtimeLong
    @CompositeRWLock({"mService", "mProcLock"})
    protected long mLastIdleTimeIfStillIdle;

    /** Last time the UID became idle. It's not cleared when the UID becomes active. */
    @ElapsedRealtimeLong
    @CompositeRWLock({"mService", "mProcLock"})
    protected long mRealLastIdleTime;

    /** Whether this UID is ephemeral (i.e. instant app). */
    @CompositeRWLock({"mService", "mProcLock"})
    protected boolean mEphemeral;

    /** Whether the UID has any Foreground Services (FGS) of any type, including "short fgs". */
    @CompositeRWLock({"mService", "mProcLock"})
    protected boolean mHasForegroundServices;

    /** Whether the UID is on the allow list at the current round of computation. */
    @CompositeRWLock({"mService", "mProcLock"})
    protected boolean mCurAllowList;

    /**
     * Whether the UID is on the allow list at the last round of computation.
     * The value will be updated to {@link #mCurAllowList} when the computation is done.
     */
    @CompositeRWLock({"mService", "mProcLock"})
    protected boolean mSetAllowList;

    /** Whether the UID is currently considered idle. */
    @CompositeRWLock({"mService", "mProcLock"})
    protected boolean mIdle;

    /**
     * Whether the UID is considered idle at the last round of computation.
     * The value will be updated to {@link #mIdle} when the computation is done.
     */
    @CompositeRWLock({"mService", "mProcLock"})
    protected boolean mSetIdle;

    public UidRecordInternal(int uid, Object service, Object procLock) {
        mUid = uid;
        mService = service;
        mProcLock = procLock;
        mIdle = true;
        reset();
    }

    /**
     * Resets the current process state, foreground services flag, and capability to their
     * default initial values.
     */
    @GuardedBy({"mService", "mProcLock"})
    public void reset() {
        mCurProcState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
        mHasForegroundServices = false;
        mCurCapability = 0;
    }

    public int getUid() {
        return mUid;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public int getCurProcState() {
        return mCurProcState;
    }

    @GuardedBy({"mService", "mProcLock"})
    public void setCurProcState(int curProcState) {
        mCurProcState = curProcState;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public int getSetProcState() {
        return mSetProcState;
    }

    @GuardedBy({"mService", "mProcLock"})
    public void setSetProcState(int setProcState) {
        mSetProcState = setProcState;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public boolean getProcAdjChanged() {
        return mProcAdjChanged;
    }

    @GuardedBy({"mService", "mProcLock"})
    public void setProcAdjChanged(boolean procAdjChanged) {
        mProcAdjChanged = procAdjChanged;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public int getCurCapability() {
        return mCurCapability;
    }

    @GuardedBy({"mService", "mProcLock"})
    public void setCurCapability(int curCapability) {
        mCurCapability = curCapability;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public int getSetCapability() {
        return mSetCapability;
    }

    @GuardedBy({"mService", "mProcLock"})
    public void setSetCapability(int setCapability) {
        mSetCapability = setCapability;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public long getLastBackgroundTime() {
        return mLastBackgroundTime;
    }

    @GuardedBy({"mService", "mProcLock"})
    public void setLastBackgroundTime(long lastBackgroundTime) {
        mLastBackgroundTime = lastBackgroundTime;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public long getLastIdleTimeIfStillIdle() {
        return mLastIdleTimeIfStillIdle;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public long getRealLastIdleTime() {
        return mRealLastIdleTime;
    }

    /**
     * Sets the last time the UID became idle. If the UID becomes active again,
     * {@code mLastIdleTimeIfStillIdle} should be reset to 0. {@code mRealLastIdleTime}
     * is only updated if the provided {@code lastIdleTime} is greater than 0.
     *
     * @param lastIdleTime The elapsed real-time when the UID became idle.
     */
    @GuardedBy({"mService", "mProcLock"})
    public void setLastIdleTime(@ElapsedRealtimeLong long lastIdleTime) {
        mLastIdleTimeIfStillIdle = lastIdleTime;
        if (lastIdleTime > 0) {
            mRealLastIdleTime = lastIdleTime;
        }
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public boolean isEphemeral() {
        return mEphemeral;
    }

    @GuardedBy({"mService", "mProcLock"})
    public void setEphemeral(boolean ephemeral) {
        mEphemeral = ephemeral;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public boolean getHasForegroundServices() {
        return mHasForegroundServices;
    }

    @GuardedBy({"mService", "mProcLock"})
    public void setHasForegroundServices(boolean hasForeGroundServices) {
        mHasForegroundServices = hasForeGroundServices;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public boolean isCurAllowListed() {
        return mCurAllowList;
    }

    @GuardedBy({"mService", "mProcLock"})
    public void setCurAllowListed(boolean curAllowList) {
        mCurAllowList = curAllowList;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public boolean isSetAllowListed() {
        return mSetAllowList;
    }

    @GuardedBy({"mService", "mProcLock"})
    public void setSetAllowListed(boolean setAllowlist) {
        mSetAllowList = setAllowlist;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public boolean isIdle() {
        return mIdle;
    }

    @GuardedBy({"mService", "mProcLock"})
    public void setIdle(boolean idle) {
        mIdle = idle;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public boolean isSetIdle() {
        return mSetIdle;
    }

    @GuardedBy({"mService", "mProcLock"})
    public void setSetIdle(boolean setIdle) {
        mSetIdle = setIdle;
    }

    /** Returns the number of processes currently associated with this UID. */
    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public abstract int getNumOfProcs();

    /** Returns the ProcessRecordInternal at the specified index. */
    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public abstract ProcessRecordInternal getProcessRecordByIndex(int idx);

    /** Adds a ProcessRecordInternal to this UID. */
    @GuardedBy({"mService", "mProcLock"})
    public abstract void addProcess(ProcessRecordInternal app);

    /** Removes a ProcessRecordInternal from this UID. */
    @GuardedBy({"mService", "mProcLock"})
    public abstract void removeProcess(ProcessRecordInternal app);

    /** Returns the minimum OOM adj across all processes managed by this UID. */
    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public abstract int getMinProcAdj();

    /** Generates a string representation of this ProcessUidRecord's state for debugging. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("ProcessUidRecord{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(' ');
        UserHandle.formatUid(sb, mUid);
        sb.append(' ');
        sb.append(ActivityManager.procStateToString(mCurProcState));
        if (mEphemeral) {
            sb.append(" ephemeral");
        }
        if (mHasForegroundServices) {
            sb.append(" fgServices");
        }
        if (mCurAllowList) {
            sb.append(" allowlist");
        }
        if (mLastBackgroundTime > 0) {
            sb.append(" bg:");
            TimeUtils.formatDuration(SystemClock.elapsedRealtime() - mLastBackgroundTime, sb);
        }
        if (mIdle) {
            sb.append(" idle");
        }
        sb.append("}");
        sb.append(" caps=");
        ActivityManager.printCapabilitiesSummary(sb, mCurCapability);
        return sb.toString();
    }
}
