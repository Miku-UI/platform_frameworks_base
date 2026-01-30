/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.am;

import android.Manifest;
import android.app.ActivityManager;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.TimeUtils;
import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoUtils;

import com.android.internal.annotations.CompositeRWLock;
import com.android.internal.annotations.GuardedBy;
import com.android.server.am.UidObserverController.ChangeRecord;
import com.android.server.am.psc.ProcessRecordInternal;
import com.android.server.am.psc.UidRecordInternal;

import java.util.function.Consumer;

/**
 * Overall information about a uid that has actively running processes.
 */
public final class UidRecord extends UidRecordInternal {
    @CompositeRWLock({"mService", "mProcLock"})
    private ArraySet<ProcessRecord> mProcRecords = new ArraySet<>();

    /**
     * Sequence number associated with the {@link #mCurProcState}. This is incremented using
     * {@link ActivityManagerService#mProcStateSeqCounter}
     * when {@link #mCurProcState} changes from background to foreground or vice versa.
     */
    @GuardedBy("networkStateUpdate")
    long curProcStateSeq;

    /**
     * Last seq number for which NetworkPolicyManagerService notified ActivityManagerService that
     * network policies rules were updated.
     */
    @GuardedBy("networkStateUpdate")
    long lastNetworkUpdatedProcStateSeq;

    /**
     * Indicates if any thread is waiting for network rules to get updated for {@link #mUid}.
     */
    volatile long procStateSeqWaitingForNetwork;

    /**
     * Indicates whether this uid has internet permission or not.
     */
    volatile boolean hasInternetPermission;

    /**
     * This object is used for waiting for the network state to get updated.
     */
    final Object networkStateLock = new Object();

    /*
     * Change bitmask flags.
     */
    static final int CHANGE_GONE = 1 << 0;
    static final int CHANGE_IDLE = 1 << 1;
    static final int CHANGE_ACTIVE = 1 << 2;
    static final int CHANGE_CACHED = 1 << 3;
    static final int CHANGE_UNCACHED = 1 << 4;
    static final int CHANGE_CAPABILITY = 1 << 5;
    static final int CHANGE_PROCADJ = 1 << 6;
    static final int CHANGE_PROCSTATE = 1 << 31;

    // Keep the enum lists in sync
    private static int[] ORIG_ENUMS = new int[] {
            CHANGE_GONE,
            CHANGE_IDLE,
            CHANGE_ACTIVE,
            CHANGE_CACHED,
            CHANGE_UNCACHED,
            CHANGE_CAPABILITY,
            CHANGE_PROCSTATE,
    };
    private static int[] PROTO_ENUMS = new int[] {
            UidRecordProto.CHANGE_GONE,
            UidRecordProto.CHANGE_IDLE,
            UidRecordProto.CHANGE_ACTIVE,
            UidRecordProto.CHANGE_CACHED,
            UidRecordProto.CHANGE_UNCACHED,
            UidRecordProto.CHANGE_CAPABILITY,
            UidRecordProto.CHANGE_PROCSTATE,
    };

    // UidObserverController is the only thing that should modify this.
    final ChangeRecord pendingChange = new ChangeRecord();

    @GuardedBy("mService")
    private int mLastReportedChange;

    /**
     * This indicates whether the entire Uid is frozen or not.
     * It is used by CachedAppOptimizer to avoid sending multiple
     * UID_FROZEN_STATE_UNFROZEN messages on process unfreeze.
     */
    @GuardedBy(anyOf = {"mService", "mProcLock"})
    private boolean mUidIsFrozen;

    public UidRecord(int uid, ActivityManagerService service) {
        super(uid, service, service != null ? service.mProcLock : null);
    }

    @Override
    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public int getNumOfProcs() {
        return mProcRecords.size();
    }

    @Override
    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public int getMinProcAdj() {
        int minAdj = ProcessList.UNKNOWN_ADJ;
        for (int i = mProcRecords.size() - 1; i >= 0; i--) {
            int adj = mProcRecords.valueAt(i).getSetAdj();
            if (adj < minAdj) {
                minAdj = adj;
            }
        }
        return minAdj;
    }


    @GuardedBy(anyOf = {"mService", "mProcLock"})
    void forEachProcess(Consumer<ProcessRecord> callback) {
        for (int i = mProcRecords.size() - 1; i >= 0; i--) {
            callback.accept(mProcRecords.valueAt(i));
        }
    }

    @Override
    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public ProcessRecord getProcessRecordByIndex(int idx) {
        return mProcRecords.valueAt(idx);
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    ProcessRecord getProcessInPackage(String packageName) {
        for (int i = mProcRecords.size() - 1; i >= 0; i--) {
            final ProcessRecord app = mProcRecords.valueAt(i);
            if (app != null && TextUtils.equals(app.info.packageName, packageName)) {
                return app;
            }
        }
        return null;
    }

    /**
     * Check whether all processes in the Uid are frozen.
     *
     * @param excluding Skip this process record during the check.
     * @return true if all processes in the Uid are frozen, false otherwise.
     */
    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public boolean areAllProcessesFrozen(ProcessRecord excluding) {
        for (int i = mProcRecords.size() - 1; i >= 0; i--) {
            final ProcessRecord app = mProcRecords.valueAt(i);
            final ProcessCachedOptimizerRecord opt = app.mOptRecord;

            if (excluding != app && !opt.isFrozen()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return true if all processes in the Uid are frozen, false otherwise.
     */
    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public boolean areAllProcessesFrozen() {
        return areAllProcessesFrozen(null);
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public void setFrozen(boolean frozen) {
        mUidIsFrozen = frozen;
    }

    @GuardedBy(anyOf = {"mService", "mProcLock"})
    public boolean isFrozen() {
        return mUidIsFrozen;
    }

    @Override
    @GuardedBy({"mService", "mProcLock"})
    public void addProcess(ProcessRecordInternal app) {
        // Only ProcessRecord extends ProcessRecordInternal, so it's safe to cast directly.
        mProcRecords.add((ProcessRecord) app);
    }

    @Override
    @GuardedBy({"mService", "mProcLock"})
    public void removeProcess(ProcessRecordInternal app) {
        // Only ProcessRecord extends ProcessRecordInternal, so it's safe to cast directly.
        mProcRecords.remove((ProcessRecord) app);
    }

    @GuardedBy("mService")
    void setLastReportedChange(int lastReportedChange) {
        mLastReportedChange = lastReportedChange;
    }

    public void updateHasInternetPermission() {
        hasInternetPermission = ActivityManager.checkUidPermission(Manifest.permission.INTERNET,
                mUid) == PackageManager.PERMISSION_GRANTED;
    }

    void dumpDebug(ProtoOutputStream proto, long fieldId) {
        long token = proto.start(fieldId);
        proto.write(UidRecordProto.UID, mUid);
        proto.write(UidRecordProto.CURRENT, ProcessList.makeProcStateProtoEnum(mCurProcState));
        proto.write(UidRecordProto.EPHEMERAL, mEphemeral);
        proto.write(UidRecordProto.FG_SERVICES, mHasForegroundServices);
        proto.write(UidRecordProto.WHILELIST, mCurAllowList);
        ProtoUtils.toDuration(proto, UidRecordProto.LAST_BACKGROUND_TIME,
                mLastBackgroundTime, SystemClock.elapsedRealtime());
        proto.write(UidRecordProto.IDLE, mIdle);
        if (mLastReportedChange != 0) {
            ProtoUtils.writeBitWiseFlagsToProtoEnum(proto, UidRecordProto.LAST_REPORTED_CHANGES,
                    mLastReportedChange, ORIG_ENUMS, PROTO_ENUMS);
        }

        long seqToken = proto.start(UidRecordProto.NETWORK_STATE_UPDATE);
        proto.write(UidRecordProto.ProcStateSequence.CURURENT, curProcStateSeq);
        proto.write(UidRecordProto.ProcStateSequence.LAST_NETWORK_UPDATED,
                lastNetworkUpdatedProcStateSeq);
        proto.end(seqToken);

        proto.end(token);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("UidRecord{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(' ');
        UserHandle.formatUid(sb, mUid);
        sb.append(' ');
        sb.append(ProcessList.makeProcStateString(mCurProcState));
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
        if (mLastReportedChange != 0) {
            sb.append(" change:");
            boolean printed = false;
            if ((mLastReportedChange & CHANGE_GONE) != 0) {
                printed = true;
                sb.append("gone");
            }
            if ((mLastReportedChange & CHANGE_IDLE) != 0) {
                if (printed) {
                    sb.append("|");
                }
                printed = true;
                sb.append("idle");
            }
            if ((mLastReportedChange & CHANGE_ACTIVE) != 0) {
                if (printed) {
                    sb.append("|");
                }
                printed = true;
                sb.append("active");
            }
            if ((mLastReportedChange & CHANGE_CACHED) != 0) {
                if (printed) {
                    sb.append("|");
                }
                printed = true;
                sb.append("cached");
            }
            if ((mLastReportedChange & CHANGE_UNCACHED) != 0) {
                if (printed) {
                    sb.append("|");
                }
                sb.append("uncached");
            }
            if ((mLastReportedChange & CHANGE_PROCSTATE) != 0) {
                if (printed) {
                    sb.append("|");
                }
                sb.append("procstate");
            }
            if ((mLastReportedChange & CHANGE_PROCADJ) != 0) {
                if (printed) {
                    sb.append("|");
                }
                sb.append("procadj");
            }
        }

        // Keep the legacy field to maintain backward compatibility for downstream readers.
        // TODO: b/425766486 - Remove the fixed string.
        sb.append(" procs:0");

        sb.append(" seq(");
        sb.append(curProcStateSeq);
        sb.append(",");
        sb.append(lastNetworkUpdatedProcStateSeq);
        sb.append(")}");
        sb.append(" caps=");
        ActivityManager.printCapabilitiesSummary(sb, mCurCapability);
        return sb.toString();
    }
}
