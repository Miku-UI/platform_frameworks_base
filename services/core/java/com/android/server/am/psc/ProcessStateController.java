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

import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_ACTIVITY;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_BACKUP;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_FINISH_RECEIVER;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_SERVICE_BINDER_CALL;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_START_RECEIVER;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_UI_VISIBILITY;
import static android.app.ProcessMemoryState.HOSTING_COMPONENT_TYPE_BROADCAST_RECEIVER;

import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_OOM_ADJ;
import static com.android.server.am.psc.Constants.SCHED_GROUP_UNDEFINED;

import android.annotation.ElapsedRealtimeLong;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal.OomAdjReason;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManagerInternal;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.ServiceThread;
import com.android.server.am.Flags;
import com.android.server.am.HostingRecord;
import com.android.server.am.psc.Constants.OomAdjust;
import com.android.server.am.psc.Constants.SchedGroup;
import com.android.server.am.psc.annotation.RequiresEnclosingBatchSession;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * ProcessStateController is responsible for maintaining state that can affect the OomAdjuster
 * computations of a process. Any state that can affect a process's importance must be set by
 * only ProcessStateController.
 */
public class ProcessStateController {
    public static final String TAG = "ProcessStateController";

    public static final int FOLLOW_UP_UPDATE_MSG = 1;

    private final OomAdjuster.Constants mOomConstants;
    private final OomAdjuster mOomAdjuster;
    private final BiConsumer<ConnectionRecordInternal, Boolean> mServiceBinderCallUpdater;

    // TODO(b/425766486): Investigate if we could use java.util.concurrent.locks.ReadWriteLock.
    private final Object mLock;
    private final Object mProcLock;

    private final Consumer<ProcessRecordInternal> mTopChangeCallback;

    private final ProcessLruUpdater mProcessLruUpdater;

    private final GlobalState mGlobalState = new GlobalState();

    private SyncBatchSession mBatchSession;

    /**
     * Queue for staging asynchronous events. The queue will be drained before each update.
     */
    private final ConcurrentLinkedQueue<Runnable> mStagingQueue = new ConcurrentLinkedQueue<>();

    private ProcessStateController(ProcessListInternal processList,
            ActiveUidsInternal activeUids, ServiceThread handlerThread,
            Object lock, Object procLock, Consumer<ProcessRecordInternal> topChangeCallback,
            ProcessLruUpdater lruUpdater, OomAdjuster.Injector oomAdjInjector,
            OomAdjuster.Constants oomConstants, OomAdjuster.Callback callback,
            OomAdjuster.HostingTypeProvider hostingTypeProvider) {

        mLock = lock;
        mProcLock = procLock;

        final Handler updateHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == FOLLOW_UP_UPDATE_MSG) {
                    // Remove any existing duplicate messages on the handler here while no lock
                    // is being held. If another follow up update is needed, it will be scheduled
                    // by OomAdjuster.
                    removeMessages(FOLLOW_UP_UPDATE_MSG);
                    synchronized (mLock) {
                        ProcessStateController.this.runFollowUpUpdate();
                    }
                }
            }
        };
        mOomConstants = oomConstants;
        mOomAdjuster = new OomAdjusterImpl(mLock, mProcLock, processList, activeUids,
                handlerThread, mOomConstants, mGlobalState, oomAdjInjector, callback,
                updateHandler, hostingTypeProvider);
        mTopChangeCallback = topChangeCallback;
        mProcessLruUpdater = lruUpdater;
        final Handler serviceHandler = new Handler(handlerThread.getLooper());
        mServiceBinderCallUpdater = (cr, hasOngoingCalls) -> serviceHandler.post(() -> {
            synchronized (mLock) {
                if (cr.setOngoingCalls(hasOngoingCalls)) {
                    runUpdate(cr.getClient(), OOM_ADJ_REASON_SERVICE_BINDER_CALL);
                }
            }
        });
    }

    public OomAdjuster.Constants getOomConstants() {
        return mOomConstants;
    }

    public void setServiceBindAlmostPerceptibleTimeoutMs(long value) {
        mOomConstants.mServiceBindAlmostPerceptibleTimeoutMs = value;
    }

    public void setShortFgsTimeoutDuration(long value) {
        mOomConstants.mShortFgsTimeoutDuration = value;
    }

    public void setShortFgsProcStateExtraWaitDuration(long value) {
        mOomConstants.mShortFgsProcStateExtraWaitDuration = value;
    }

    public void setCurMaxCachedProcesses(int value) {
        mOomConstants.mCurMaxCachedProcesses = value;
    }

    public void setCurMaxEmptyProcesses(int value) {
        mOomConstants.mCurMaxEmptyProcesses = value;
    }

    public void setCurTrimEmptyProcesses(int value) {
        mOomConstants.mCurTrimEmptyProcesses = value;
    }

    public void setProcStateDebugUids(SparseBooleanArray value) {
        mOomConstants.mProcStateDebugUids = value;
    }

    public void setForceEnablePssProfiling(boolean value) {
        mOomConstants.mForceEnablePssProfiling = value;
    }

    public void setPssToRssThresholdModifier(float value) {
        mOomConstants.mPssToRssThresholdModifier = value;
    }

    public void setMaxEmptyTimeMillis(long value) {
        mOomConstants.mMaxEmptyTimeMillis = value;
    }

    public void setTopToFgsGraceDuration(long value) {
        mOomConstants.mTopToFgsGraceDuration = value;
    }

    public void setTopToAlmostPerceptibleGraceDuration(long value) {
        mOomConstants.mTopToAlmostPerceptibleGraceDuration = value;
    }

    public void setMaxPreviousTime(long value) {
        mOomConstants.mMaxPreviousTime = value;
    }

    public void setMaxServiceInactivity(long value) {
        mOomConstants.mMaxServiceInactivity = value;
    }

    public void setContentProviderRetainTime(long value) {
        mOomConstants.mContentProviderRetainTime = value;
    }

    public void setEnableProcStateStacktrace(boolean value) {
        mOomConstants.mEnableProcStateStacktrace = value;
    }

    public void setProcStateDebugSetProcStateDelay(int value) {
        mOomConstants.mProcStateDebugSetProcStateDelay = value;
    }

    public void setProcStateDebugSetUidStateDelay(int value) {
        mOomConstants.mProcStateDebugSetUidStateDelay = value;
    }

    public void setProactiveKillsEnabled(boolean value) {
        mOomConstants.mProactiveKillsEnabled = value;
    }

    public void setLowSwapThresholdPercent(float value) {
        mOomConstants.mLowSwapThresholdPercent = value;
    }

    public void setNoKillCachedProcessesUntilBootCompleted(boolean value) {
        mOomConstants.mNoKillCachedProcessesUntilBootCompleted = value;
    }

    public void setNoKillCachedProcessesPostBootCompletedDurationMillis(long value) {
        mOomConstants.mNoKillCachedProcessesPostBootCompletedDurationMillis = value;
    }

    public void setFreezerCutoffAdj(int value) {
        mOomConstants.mFreezerCutoffAdj = value;
    }

    public void setFollowUpOomadjUpdateWaitDuration(long value) {
        mOomConstants.mFollowUpOomadjUpdateWaitDuration = value;
    }

    /**
     * Sets the number of frozen processes.
     */
    public void setFrozenProcessCount(int count) {
        mGlobalState.mFrozenProcessCount = count;
    }

    /**
     * Start a batch session for specifically service state changes. ProcessStateController updates
     * will not be triggered until until the returned SyncBatchSession is closed.
     */
    public SyncBatchSession startServiceBatchSession(@OomAdjReason int reason) {
        if (!Flags.pscBatchServiceUpdates()) return null;
        return startBatchSession(reason);
    }

    /**
     * Start a batch session. ProcessStateController updates will not be triggered until the
     * returned SyncBatchSession is closed.
     */
    @GuardedBy("mLock")
    public SyncBatchSession startBatchSession(@OomAdjReason int reason) {
        final SyncBatchSession batchSession = getBatchSession();
        batchSession.start(reason);
        return batchSession;
    }

    private SyncBatchSession getBatchSession() {
        if (mBatchSession == null) {
            mBatchSession = new SyncBatchSession(this::enqueueUpdateTargetImpl,
                    this::runFullUpdateImpl, this::runPendingUpdateImpl);
        }
        return mBatchSession;
    }

    /**
     * Get the instance of OomAdjuster that ProcessStateController is using.
     * Must only be interacted with while holding the ActivityManagerService lock.
     */
    @GuardedBy("mLock")
    public OomAdjuster getOomAdjuster() {
        return mOomAdjuster;
    }

    /**
     * Add a process to evaluated the next time an update is run.
     */
    @GuardedBy("mLock")
    public void enqueueUpdateTarget(@Nullable ProcessRecordInternal proc) {
        if (mBatchSession != null && mBatchSession.isActive()) {
            // BatchSession is active and a process has been enqueued for an update.
            getBatchSession().maybeEnqueueProcess(proc);
            return;
        }
        enqueueUpdateTargetImpl(proc);
    }

    @GuardedBy("mLock")
    private void enqueueUpdateTargetImpl(@Nullable ProcessRecordInternal proc) {
        mOomAdjuster.enqueueOomAdjTargetLocked(proc);
    }

    /**
     * Remove a process that was added by {@link #enqueueUpdateTarget}.
     */
    @GuardedBy("mLock")
    public void removeUpdateTarget(@NonNull ProcessRecordInternal proc, boolean procDied) {
        mOomAdjuster.removeOomAdjTargetLocked(proc, procDied);
    }

    /**
     * Trigger an update on a single process (and any processes that have been enqueued with
     * {@link #enqueueUpdateTarget}).
     */
    @GuardedBy("mLock")
    public boolean runUpdate(@NonNull ProcessRecordInternal proc, @OomAdjReason int oomAdjReason) {
        if (mBatchSession != null && mBatchSession.isActive()) {
            // BatchSession is active, just enqueue the proc for now. The update will happen
            // at the end of the session.
            enqueueUpdateTarget(proc);
            return false;
        }
        return runUpdateimpl(proc, oomAdjReason);
    }

    @GuardedBy("mLock")
    private boolean runUpdateimpl(@NonNull ProcessRecordInternal proc,
            @OomAdjReason int oomAdjReason) {
        commitStagedEvents();
        return mOomAdjuster.updateOomAdjLocked(proc, oomAdjReason);
    }

    /**
     * Trigger an update on all processes that have been enqueued with {@link #enqueueUpdateTarget}.
     */
    @GuardedBy("mLock")
    public void runPendingUpdate(@OomAdjReason int oomAdjReason) {
        if (mBatchSession != null && mBatchSession.isActive()) {
            // BatchSession is active, don't trigger the update, it will happen at the end of the
            // session.
            return;
        }
        runPendingUpdateImpl(oomAdjReason);
    }

    /**
     * Runs an update on all processes that have been enqueued for an update.
     */
    @GuardedBy("mLock")
    public void runPendingUpdateImpl(@OomAdjReason int oomAdjReason) {
        commitStagedEvents();
        mOomAdjuster.updateOomAdjPendingTargetsLocked(oomAdjReason);
    }

    /**
     * Trigger an update on all processes.
     */
    @GuardedBy("mLock")
    public void runFullUpdate(@OomAdjReason int oomAdjReason) {
        if (mBatchSession != null && mBatchSession.isActive()) {
            // BatchSession is active, just mark the session to run a full update at the end of
            // the session.
            getBatchSession().setFullUpdate();
            return;
        }
        runFullUpdateImpl(oomAdjReason);
    }

    private void runFullUpdateImpl(@OomAdjReason int oomAdjReason) {
        commitStagedEvents();
        mOomAdjuster.updateOomAdjLocked(oomAdjReason);
    }

    /**
     * Trigger an update on any processes that have been marked for follow up during a previous
     * update.
     */
    @GuardedBy("mLock")
    public void runFollowUpUpdate() {
        commitStagedEvents();
        mOomAdjuster.updateOomAdjFollowUpTargetsLocked();
    }

    /**
     * Create an ActivityStateAsyncUpdater to asynchronously update ProcessStateController with
     * important Activity state changes.
     * @param looper which looper to post the async work to.
     */
    public ActivityStateAsyncUpdater createActivityStateAsyncUpdater(Looper looper) {
        return new ActivityStateAsyncUpdater(this, looper, mStagingQueue);
    }

    /**
     * Returns a {@link BoundServiceSession} for the given {@link ConnectionRecordInternal}.
     * Creates and associates a new one if required.
     */
    public BoundServiceSession getBoundServiceSessionFor(
            ConnectionRecordInternal connectionRecord) {
        if (connectionRecord.notHasFlag(Context.BIND_ALLOW_FREEZE) && connectionRecord.notHasFlag(
                Context.BIND_SIMULATE_ALLOW_FREEZE)) {
            // Don't incur the memory and compute overhead for process state adjustments for all
            // bindings by default. This should be opted into as needed.
            return null;
        }
        if (connectionRecord.getBoundServiceSession() == null) {
            connectionRecord.setBoundServiceSession(
                    new BoundServiceSession(mServiceBinderCallUpdater, connectionRecord));
        }
        return connectionRecord.getBoundServiceSession();
    }

    private static class GlobalState implements OomAdjuster.GlobalState {
        private boolean mIsAwake = true;
        // TODO(b/369300367): Maintaining global state for backup processes is a bit convoluted.
        //  ideally the state gets migrated to ProcessRecordInternal.
        private final SparseArray<ProcessRecordInternal> mBackupTargets = new SparseArray<>();
        private boolean mIsLastMemoryLevelNormal = true;

        @ActivityManager.ProcessState
        private int mTopProcessState = ActivityManager.PROCESS_STATE_TOP;
        // TODO: b/424006553 - get rid of the need to use volatile for keyguard unlocking flow.
        private volatile boolean mUnlockingStaged = false;
        private boolean mUnlocking = false;
        private boolean mExpandedNotificationShade = false;
        private ProcessRecordInternal mTopProcess = null;
        private ProcessRecordInternal mHomeProcess = null;
        private ProcessRecordInternal mHeavyWeightProcess = null;
        private ProcessRecordInternal mShowingUiWhileDozingProcess = null;
        private ProcessRecordInternal mPreviousProcess = null;
        private static final int NONE_DEBUG_UID = -1;
        private volatile int mDebugUid = NONE_DEBUG_UID;
        private volatile long mLastUserUnlockingUptime = 0;
        private volatile int mFrozenProcessCount = 0;

        private void commitStagedState() {
            mUnlocking = mUnlockingStaged;
        }

        public boolean isAwake() {
            return mIsAwake;
        }

        public ProcessRecordInternal getBackupTarget(@UserIdInt int userId) {
            return mBackupTargets.get(userId);
        }

        public boolean isLastMemoryLevelNormal() {
            return mIsLastMemoryLevelNormal;
        }

        @ActivityManager.ProcessState
        public int getTopProcessState() {
            return mTopProcessState;
        }

        public boolean isUnlocking() {
            return mUnlocking;
        }

        public boolean hasExpandedNotificationShade() {
            return mExpandedNotificationShade;
        }

        @Nullable
        public ProcessRecordInternal getTopProcess() {
            return mTopProcess;
        }

        @Nullable
        public ProcessRecordInternal getHomeProcess() {
            return mHomeProcess;
        }

        @Nullable
        public ProcessRecordInternal getHeavyWeightProcess() {
            return mHeavyWeightProcess;
        }

        @Nullable
        public ProcessRecordInternal getShowingUiWhileDozingProcess() {
            return mShowingUiWhileDozingProcess;
        }

        @Nullable
        public ProcessRecordInternal getPreviousProcess() {
            return mPreviousProcess;
        }

        public boolean isDebugEnabled(ProcessRecordInternal app) {
            return app.getApplicationUid() == mDebugUid;
        }

        public long getLastUserUnlockingUptime() {
            return mLastUserUnlockingUptime;
        }

        public int getFrozenProcessCount() {
            return mFrozenProcessCount;
        }
    }

    /*************************** Global State Events ***************************/

    @GuardedBy("mLock")
    private void setTopProcessState(@ActivityManager.ProcessState int procState) {
        mGlobalState.mTopProcessState = procState;
    }

    @GuardedBy("mLock")
    private void setExpandedNotificationShade(boolean expandedShade) {
        mGlobalState.mExpandedNotificationShade = expandedShade;
    }

    @GuardedBy("mLock")
    private void setTopProcess(@Nullable ProcessRecordInternal proc) {
        if (mGlobalState.mTopProcess == proc) return;
        mGlobalState.mTopProcess = proc;
        mTopChangeCallback.accept(proc);
    }

    @GuardedBy("mLock")
    private void setPreviousProcess(@Nullable ProcessRecordInternal proc) {
        mGlobalState.mPreviousProcess = proc;
    }

    @GuardedBy("mLock")
    private void setHomeProcess(@Nullable ProcessRecordInternal proc) {
        mGlobalState.mHomeProcess = proc;
    }

    @GuardedBy("mLock")
    private void setHeavyWeightProcess(@Nullable ProcessRecordInternal proc) {
        mGlobalState.mHeavyWeightProcess = proc;
    }

    @GuardedBy("mLock")
    private void setVisibleDozeUiProcess(@Nullable ProcessRecordInternal proc) {
        mGlobalState.mShowingUiWhileDozingProcess = proc;
    }

    /**
     * Set what wakefulness state the screen is in.
     */
    @GuardedBy("mLock")
    public void setWakefulness(int wakefulness) {
        mGlobalState.mIsAwake = (wakefulness == PowerManagerInternal.WAKEFULNESS_AWAKE);
    }

    /**
     * Set for a given user what process is currently running a backup, if any.
     */
    @GuardedBy("mLock")
    public void setBackupTarget(@NonNull ProcessRecordInternal proc, @UserIdInt int userId) {
        final ProcessRecordInternal prev = mGlobalState.mBackupTargets.get(userId);
        if (prev == proc) return;
        mGlobalState.mBackupTargets.put(userId, proc);

        if (Flags.autoTriggerOomadjUpdates()) {
            enqueueUpdateTarget(prev);
            enqueueUpdateTarget(proc);
            runPendingUpdate(OOM_ADJ_REASON_BACKUP);
        }
    }

    /**
     * No longer consider any process running a backup for a given user.
     */
    @GuardedBy("mLock")
    public void stopBackupTarget(@UserIdInt int userId) {
        final ProcessRecordInternal prev = mGlobalState.mBackupTargets.removeReturnOld(userId);
        if (prev == null) return;

        if (Flags.autoTriggerOomadjUpdates()) {
            enqueueUpdateTarget(prev);
            runPendingUpdate(OOM_ADJ_REASON_BACKUP);
        }
    }

    /**
     * Set whether the last known memory level is normal.
     */
    @GuardedBy("mLock")
    public void setIsLastMemoryLevelNormal(boolean isMemoryNormal) {
        mGlobalState.mIsLastMemoryLevelNormal = isMemoryNormal;
    }

    /**
     * Sets the UID for which OOM adjustment debugging messages should be reported.
     */
    public void setDebugUid(int uid) {
        mGlobalState.mDebugUid = uid;
    }

    /**
     * Clears the UID for which OOM adjustment debugging messages are reported.
     */
    public void clearDebugUid() {
        mGlobalState.mDebugUid = GlobalState.NONE_DEBUG_UID;
    }

    /**
     * Returns the UID for which OOM adjustment debugging messages are currently being reported.
     */
    public int getDebugUid() {
        return mGlobalState.mDebugUid;
    }

    /**
     * Sets the timestamp for the last user unlock event. This should be called when a user starts
     * unlocking to record the uptime.
     */
    public void setLastUserUnlockingUptime(long time) {
        mGlobalState.mLastUserUnlockingUptime = time;
    }

    /**
     * Returns the timestamp for the last user unlock event.
     */
    public long getLastUserUnlockingUptime() {
        return mGlobalState.mLastUserUnlockingUptime;
    }

    /***************************** UID State Events ****************************/
    /**
     * Set a UID as temp allowlisted.
     */
    @GuardedBy("mLock")
    public void setUidTempAllowlistStateLSP(int uid, boolean allowList) {
        mOomAdjuster.setUidTempAllowlistStateLSP(uid, allowList);
    }

    /**
     * Set whether the given UID is currently idle.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setUidIdle(@NonNull UidRecordInternal uidRec, boolean idle) {
        uidRec.setIdle(idle);
    }

    /**
     * Set whether the given UID is idle at the last round of computation.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setUidSetIdle(@NonNull UidRecordInternal uidRec, boolean idle) {
        uidRec.setSetIdle(idle);
    }

    /**
     * Set the last time the given UID became idle.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setUidLastIdleTime(@NonNull UidRecordInternal uidRec,
            @ElapsedRealtimeLong long lastIdleTime) {
        uidRec.setLastIdleTime(lastIdleTime);
    }

    /**
     * Set the current process state for a UID.
     */
    @VisibleForTesting
    @GuardedBy({"mLock", "mProcLock"})
    public void setUidCurProcState(@NonNull UidRecordInternal uidRec,
            @ActivityManager.ProcessState int curProcState) {
        uidRec.setCurProcState(curProcState);
    }

    /**
     * Set the last round's process state for a UID.
     */
    @VisibleForTesting
    @GuardedBy({"mLock", "mProcLock"})
    public void setUidSetProcState(@NonNull UidRecordInternal uidRec,
            @ActivityManager.ProcessState int setProcState) {
        uidRec.setSetProcState(setProcState);
    }

    /**
     * Set the current process state sequence number for a UID.
     */
    @VisibleForTesting
    @GuardedBy({"mLock", "mProcLock"})
    public void setUidCurProcStateSeq(@NonNull UidRecordInternal uidRec, long curProcStateSeq) {
        uidRec.setCurProcStateSeq(curProcStateSeq);
    }

    /**
     * Set whether the given UID is currently on the allow list.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setUidCurAllowListed(@NonNull UidRecordInternal uidRec, boolean allowListed) {
        uidRec.setCurAllowListed(allowListed);
    }

    /**
     * Set whether the given UID is on the allow list at the last round of computation.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setUidSetAllowListed(@NonNull UidRecordInternal uidRec, boolean allowListed) {
        uidRec.setSetAllowListed(allowListed);
    }

    /*********************** Process Miscellaneous Events **********************/
    /**
     * Note whether the given process has been killed.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setKilled(@NonNull ProcessRecordInternal proc, boolean killed) {
        proc.setKilled(killed);
    }

    /**
     * Note whether the given process was killed by the activity manager.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setKilledByAm(@NonNull ProcessRecordInternal proc, boolean killedByAm) {
        proc.setKilledByAm(killedByAm);
    }

    /**
     * Note that the given process is waiting to be killed.
     */
    @GuardedBy("mLock")
    public void setWaitingToKill(@NonNull ProcessRecordInternal proc,
            @Nullable String waitingToKill) {
        proc.setWaitingToKill(waitingToKill);
    }

    /**
     * Note the render thread TID of the given process.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setRenderThreadTid(@NonNull ProcessRecordInternal proc, int tid) {
        proc.setRenderThreadTid(tid);
    }

    /**
     * Set the last time the given process was active.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setLastActivityTime(@NonNull ProcessRecordInternal proc, long lastActivityTime) {
        proc.setLastActivityTime(lastActivityTime);
    }

    /**
     * Note whether the given process is background restricted.
     */
    @GuardedBy("mLock")
    public void setBackgroundRestricted(@NonNull ProcessRecordInternal proc, boolean restricted) {
        proc.setBackgroundRestricted(restricted);
    }

    /**
     * Sets the entry point for an isolated process.
     */
    @GuardedBy("mLock")
    public void setIsolatedEntryPoint(@NonNull ProcessRecordInternal proc,
            @Nullable String isolatedEntryPoint) {
        proc.setIsolatedEntryPoint(isolatedEntryPoint);
    }

    /**
     * Set the maximum adj score a process can be assigned.
     */
    @GuardedBy("mLock")
    public void setMaxAdj(@NonNull ProcessRecordInternal proc, @OomAdjust int adj) {
        proc.setMaxAdj(adj);
    }

    /**
     * Sets the current scheduling group for the given process.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setCurrentSchedulingGroup(@NonNull ProcessRecordInternal proc,
            @SchedGroup int curSchedGroup) {
        proc.setCurrentSchedulingGroup(curSchedGroup);
    }

    /**
     * Sets the last set scheduling group for the given process.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setSetSchedGroup(@NonNull ProcessRecordInternal proc,
            @SchedGroup int setSchedGroup) {
        proc.setSetSchedGroup(setSchedGroup);
    }

    /**
     * Sets if the given process has any foreground activities.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setHasForegroundActivities(@NonNull ProcessRecordInternal proc,
            boolean hasForegroundActivities) {
        proc.setHasForegroundActivities(hasForegroundActivities);
    }

    /**
     * Initialize a process that is being attached.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setAttachingProcessStatesLSP(@NonNull ProcessRecordInternal proc) {
        mOomAdjuster.setAttachingProcessStatesLSP(proc);
    }

    /**
     * Note whether a process is pending attach or not.
     */
    @GuardedBy("mLock")
    public void setPendingFinishAttach(@NonNull ProcessRecordInternal proc,
            boolean pendingFinishAttach) {
        proc.setPendingFinishAttach(pendingFinishAttach);
    }

    /**
     * Sets whether the given process has an active instrumentation running.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setHasActiveInstrumentation(@NonNull ProcessRecordInternal proc, boolean value) {
        proc.setHasActiveInstrumentation(value);
    }

    /**
     * Forces the process state of a given process up to a specified state if it's currently in a
     * less important state.
     */
    @GuardedBy("mLock")
    public void forceProcessStateUpTo(@NonNull ProcessRecordInternal proc,
            @ActivityManager.ProcessState int newState) {
        final @ActivityManager.ProcessState int prevProcState = proc.getReportedProcState();
        if (prevProcState > newState) {
            synchronized (mProcLock) {
                proc.setReportedProcState(newState);
                proc.setCurProcState(newState);
                proc.setCurRawProcState(newState);
                mOomAdjuster.onProcessStateChanged(proc, prevProcState);
            }
        }
    }

    /**
     * Bump a process to the end of the LRU list.
     */
    @GuardedBy("mLock")
    public void updateLruProcess(@NonNull ProcessRecordInternal proc, boolean activityChange,
            @Nullable ProcessRecordInternal client) {
        mProcessLruUpdater.updateLruProcessLocked(proc, activityChange, client);
    }

    /**
     * Remove a process from the LRU list.
     */
    @GuardedBy("mLock")
    public void removeLruProcess(@NonNull ProcessRecordInternal proc) {
        mProcessLruUpdater.removeLruProcessLocked(proc);
    }

    /********************* Process Visibility State Events *********************/
    /**
     * Note whether a process has Top UI or not.
     * Triggers an update if the state changed.
     */
    @GuardedBy("mLock")
    public void setHasTopUi(@NonNull ProcessRecordInternal proc, boolean hasTopUi) {
        if (proc.getHasTopUi() == hasTopUi) return;
        if (DEBUG_OOM_ADJ) {
            Slog.d(TAG, "Setting hasTopUi=" + hasTopUi + " for pid=" + proc.getPid());
        }
        proc.setHasTopUi(hasTopUi);
        runUpdate(proc, OOM_ADJ_REASON_UI_VISIBILITY);
    }

    /**
     * Note whether a process is displaying Overlay UI or not.
     * Triggers an update if the state changed.
     */
    @GuardedBy("mLock")
    public void setHasOverlayUi(@NonNull ProcessRecordInternal proc, boolean hasOverlayUi) {
        if (proc.getHasOverlayUi() == hasOverlayUi) return;
        proc.setHasOverlayUi(hasOverlayUi);
        runUpdate(proc, OOM_ADJ_REASON_UI_VISIBILITY);
    }

    /**
     * Note whether a process is running a remote animation.
     */
    @GuardedBy("mLock")
    public void setRunningRemoteAnimation(@NonNull ProcessRecordInternal proc,
            boolean runningRemoteAnimation) {
        if (proc.isRunningRemoteAnimation() == runningRemoteAnimation) return;
        if (DEBUG_OOM_ADJ) {
            Slog.i(TAG, "Setting runningRemoteAnimation=" + runningRemoteAnimation
                    + " for pid=" + proc.getPid());
        }
        proc.setIsRunningRemoteAnimation(runningRemoteAnimation);
        runUpdate(proc, OOM_ADJ_REASON_UI_VISIBILITY);
    }

    /**
     * Forces a process to be considered important, e.g. while showing toasts.
     *
     * @param forcingToImportant A token representing the source and reason. Null to remove forcing.
     * @return {@code true} if the token has changed.
     */
    @GuardedBy("mLock")
    public boolean setForcingToImportant(@NonNull ProcessRecordInternal proc,
            @Nullable Object forcingToImportant) {
        if (proc.getForcingToImportant() == forcingToImportant) return false;
        proc.setForcingToImportant(forcingToImportant);
        return true;
    }

    /**
     * Note that the process has shown UI at some point in its life.
     */
    @GuardedBy("mLock")
    public void setHasShownUi(@NonNull ProcessRecordInternal proc, boolean hasShownUi) {
        // This arguably should be turned into an internal state of OomAdjuster.
        if (proc.getHasShownUi() == hasShownUi) return;
        proc.setHasShownUi(hasShownUi);
    }

    @GuardedBy("mLock")
    private void setHasActivity(@NonNull ProcessRecordInternal proc, boolean hasActivity) {
        proc.setHasActivities(hasActivity);
    }

    @GuardedBy("mLock")
    private void setActivityStateFlags(@NonNull ProcessRecordInternal proc, int flags) {
        proc.setActivityStateFlags(flags);
    }

    @GuardedBy("mLock")
    private void setPerceptibleTaskStoppedTimeMillis(@NonNull ProcessRecordInternal proc,
            long uptimeMs) {
        proc.setPerceptibleTaskStoppedTimeMillis(uptimeMs);
    }

    @GuardedBy("mLock")
    private void setHasRecentTasks(@NonNull ProcessRecordInternal proc, boolean hasRecentTasks) {
        proc.setHasRecentTask(hasRecentTasks);
    }

    /********************** Content Provider State Events **********************/
    /**
     * Note that a process is hosting a content provider.
     */
    @GuardedBy("mLock")
    public boolean addPublishedProvider(@NonNull ProcessRecordInternal proc, String name,
            ContentProviderRecordInternal cpr) {
        final ProcessProviderRecordInternal providers = proc.getProviders();
        if (providers.hasProvider(name)) return false;
        providers.installProvider(name, cpr);
        return true;
    }

    /**
     * Remove a published content provider from a process.
     */
    @GuardedBy("mLock")
    public void removePublishedProvider(@NonNull ProcessRecordInternal proc, String name) {
        final ProcessProviderRecordInternal providers = proc.getProviders();
        providers.removeProvider(name);
    }

    /**
     * Note that a process is expected to host at least the given number of content providers.
     *
     * @param proc The process record.
     * @param capacity The minimum number of content providers the process is expected to host.
     */
    @GuardedBy("mLock")
    public void ensurePublishedProviderCapacity(@NonNull ProcessRecordInternal proc, int capacity) {
        proc.getProviders().ensureProviderCapacity(capacity);
    }

    /**
     * Sets the state indicating whether the given content provider has clients from external
     * processes.
     */
    @GuardedBy("mLock")
    public void setHasExternalProcessHandles(@NonNull ContentProviderRecordInternal cpr,
            boolean value) {
        cpr.setHasExternalProcessHandles(value);
    }

    /**
     * Note the time a process is no longer hosting any content providers.
     */
    @GuardedBy("mLock")
    public void setLastProviderTime(@NonNull ProcessRecordInternal proc, long uptimeMs) {
        proc.getProviders().setLastProviderTime(uptimeMs);
    }

    /**
     * Note that a process has connected to a content provider.
     */
    @GuardedBy("mLock")
    public void addProviderConnection(@NonNull ProcessRecordInternal client,
            ContentProviderConnectionInternal cpc) {
        client.getProviders().addProviderConnection(cpc);
    }

    /**
     * Note that a process is no longer connected to a content provider.
     *
     * @return {@code true} if the connection was found and removed, {@code false} otherwise.
     */
    @GuardedBy("mLock")
    public boolean removeProviderConnection(@NonNull ProcessRecordInternal client,
            ContentProviderConnectionInternal cpc) {
        return client.getProviders().removeProviderConnection(cpc);
    }

    /*************************** Service State Events **************************/
    /**
     * Note that a process has started hosting a service.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public boolean addRunningService(@NonNull ProcessServiceRecordInternal psr,
            ServiceRecordInternal sr) {
        return psr.addRunningService(sr);
    }

    /**
     * Note that a process has stopped hosting a service.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public boolean removeRunningService(@NonNull ProcessServiceRecordInternal psr,
            ServiceRecordInternal sr) {
        return psr.removeRunningService(sr);
    }

    /**
     * Remove all services that the process is hosting.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void stopAllServices(@NonNull ProcessServiceRecordInternal psr) {
        psr.stopAllServices();
    }

    /**
     * Note that a process's service has started executing.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void startExecutingService(@NonNull ProcessServiceRecordInternal psr,
            ServiceRecordInternal sr) {
        psr.startExecutingService(sr);
    }

    /**
     * Note that a process's service has stopped executing.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void stopExecutingService(@NonNull ProcessServiceRecordInternal psr,
            ServiceRecordInternal sr) {
        psr.stopExecutingService(sr);
    }

    /**
     * Note all executing services a process has has stopped.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void stopAllExecutingServices(@NonNull ProcessServiceRecordInternal psr) {
        psr.stopAllExecutingServices();
    }

    /**
     * Note that process has bound to a service.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void addConnection(@NonNull ProcessServiceRecordInternal psr,
            ConnectionRecordInternal cr) {
        psr.addConnection(cr);
    }

    /**
     * Note that process has unbound from a service.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void removeConnection(@NonNull ProcessServiceRecordInternal psr,
            ConnectionRecordInternal cr) {
        psr.removeConnection(cr);
    }

    /**
     * Remove all bindings a process has to services.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void removeAllConnections(@NonNull ProcessServiceRecordInternal psr) {
        psr.removeAllConnections();
        psr.removeAllSdkSandboxConnections();
    }

    /**
     * Updates the flags for a given connection record.
     *
     * @return {@code true} if the flags were changed, {@code false} otherwise.
     */
    @GuardedBy("mLock")
    public boolean updateConnectionFlags(@NonNull ConnectionRecordInternal cr, long flags) {
        return cr.updateFlags(flags);
    }

    /**
     * Note whether an executing service should be considered in the foreground or not.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void setExecServicesFg(@NonNull ProcessServiceRecordInternal psr,
            boolean execServicesFg) {
        psr.setExecServicesFg(execServicesFg);
    }

    /**
     * Note whether a service is in the foreground or not and what type of FGS, if so.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void setHasForegroundServices(@NonNull ProcessServiceRecordInternal psr,
            boolean hasForegroundServices,
            int fgServiceTypes, boolean hasTypeNoneFgs) {
        psr.setHasForegroundServices(hasForegroundServices, fgServiceTypes, hasTypeNoneFgs);
    }

    /**
     * Note whether a service has a client activity or not.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void setHasClientActivities(@NonNull ProcessServiceRecordInternal psr,
            boolean hasClientActivities) {
        psr.setHasClientActivities(hasClientActivities);
    }

    /**
     * Note whether a service should be treated like an activity or not.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void setTreatLikeActivity(@NonNull ProcessServiceRecordInternal psr,
            boolean treatLikeActivity) {
        psr.setTreatLikeActivity(treatLikeActivity);
    }

    /**
     * Update the ongoing binder calls state for a given Connection record.
     */
    public boolean updateBinderServiceCalls(ConnectionRecordInternal cr, boolean ongoing) {
        return cr.setOngoingCalls(ongoing);
    }

    /** Note the last group set by a connection. */
    @GuardedBy("mLock")
    public void setConnectionGroup(@NonNull ProcessServiceRecordInternal psr, int connectionGroup) {
        psr.setConnectionGroup(connectionGroup);
    }

    /** Note the last importance set by a connection. */
    @GuardedBy("mLock")
    public void setConnectionImportance(@NonNull ProcessServiceRecordInternal psr,
            int connectionImportance) {
        psr.setConnectionImportance(connectionImportance);
    }

    /**
     * Cleanup a process's state.
     */
    @GuardedBy("mLock")
    public void onCleanupApplicationRecord(@NonNull ProcessServiceRecordInternal psr) {
        psr.onCleanupApplicationRecord();
    }

    /**
     * Set which process is hosting a service.
     */
    @GuardedBy("mLock")
    public void setHostProcess(@NonNull ServiceRecordInternal sr,
            @Nullable ProcessRecordInternal host) {
        sr.setHostProcess(host);
    }

    /**
     * Note whether a service is a Foreground Service or not
     */
    @GuardedBy("mLock")
    public void setIsForegroundService(@NonNull ServiceRecordInternal sr, boolean isFgs) {
        sr.setIsForeground(isFgs);
    }

    /**
     * Note the Foreground Service type of a service.
     */
    @GuardedBy("mLock")
    public void setForegroundServiceType(@NonNull ServiceRecordInternal sr,
            @ServiceInfo.ForegroundServiceType int fgsType) {
        sr.setForegroundServiceType(fgsType);
    }

    /**
     * Note the start time of a short foreground service.
     */
    @GuardedBy("mLock")
    public void setShortFgsStartTime(@NonNull ServiceRecordInternal sr, long uptimeNow) {
        sr.setShortFgsStartTime(uptimeNow);
    }

    /**
     * Note that a short foreground service has stopped.
     */
    @GuardedBy("mLock")
    public void clearShortFgsStartTime(@NonNull ServiceRecordInternal sr) {
        sr.clearShortFgsStartTime();
    }

    /**
     * Note the last time a service was active.
     */
    @GuardedBy("mLock")
    public void setServiceLastActivityTime(@NonNull ServiceRecordInternal sr,
            long lastActivityUpdateMs) {
        sr.setLastActivity(lastActivityUpdateMs);
    }

    /**
     * Note that a service start was requested.
     */
    @GuardedBy("mLock")
    public void setStartRequested(@NonNull ServiceRecordInternal sr, boolean startRequested) {
        sr.setStartRequested(startRequested);
    }

    /**
     * Note the last time the service was bound by a Top process with
     * {@link android.content.Context.BIND_ALMOST_PERCEPTIBLE}
     */
    @GuardedBy("mLock")
    public void setLastTopAlmostPerceptibleBindRequest(@NonNull ServiceRecordInternal sr,
            long lastTopAlmostPerceptibleBindRequestUptimeMs) {
        sr.setLastTopAlmostPerceptibleBindRequestUptimeMs(
                lastTopAlmostPerceptibleBindRequestUptimeMs);
    }

    /**
     * Recompute whether a process has bound to a service with
     * {@link android.content.Context.BIND_ALMOST_PERCEPTIBLE} or not.
     */
    @GuardedBy("mLock")
    public void updateHasTopStartedAlmostPerceptibleServices(
            @NonNull ProcessServiceRecordInternal psr) {
        psr.updateHasTopStartedAlmostPerceptibleServices();
    }

    /**
     * Sets whether this process has services that were started while it was in the TOP state
     * and are considered almost perceptible to the user.
     */
    @GuardedBy("mLock")
    public void setHasTopStartedAlmostPerceptibleServices(
            @NonNull ProcessServiceRecordInternal psr, boolean value) {
        psr.setHasTopStartedAlmostPerceptibleServices(value);
    }

    /**
     * Sets the uptime in milliseconds when the last request to bind to an almost perceptible
     * service was made while this process was in the TOP state.
     */
    @GuardedBy("mLock")
    public void setLastTopStartedAlmostPerceptibleBindRequestUptimeMs(
            @NonNull ProcessServiceRecordInternal psr, long value) {
        psr.setLastTopStartedAlmostPerceptibleBindRequestUptimeMs(value);
    }

    /************************ Broadcast Receiver State Events **************************/
    /**
     * Note that Broadcast delivery to a process has started and what scheduling group should be
     * used.
     */
    @GuardedBy("mLock")
    public void noteBroadcastDeliveryStarted(@NonNull ProcessRecordInternal proc,
            @SchedGroup int schedGroup) {
        final boolean prevReceivingState = proc.getReceivers().isReceivingBroadcast();
        final @SchedGroup int prevSchedGroup = proc.getReceivers().getBroadcastReceiverSchedGroup();
        if (prevReceivingState && prevSchedGroup == schedGroup) {
            // isReceiveBroadcast is already true and the schedGroup is not changing, skip.
            return;
        }
        proc.getReceivers().setIsReceivingBroadcast(true);
        proc.getReceivers().setBroadcastReceiverSchedGroup(schedGroup);

        proc.addHostingComponentType(HOSTING_COMPONENT_TYPE_BROADCAST_RECEIVER);

        if (Flags.pscAutoUpdateBroadcastState()) {
            runUpdate(proc, OOM_ADJ_REASON_START_RECEIVER);
        }
    }

    /**
     * Note that Broadcast delivery to a process has ended.
     */
    @GuardedBy("mLock")
    public void noteBroadcastDeliveryEnded(@NonNull ProcessRecordInternal proc) {
        final boolean prevReceivingState = proc.getReceivers().isReceivingBroadcast();
        final @SchedGroup int prevSchedGroup = proc.getReceivers().getBroadcastReceiverSchedGroup();
        if (!prevReceivingState && prevSchedGroup == SCHED_GROUP_UNDEFINED) {
            // isReceiveBroadcast is already false and the schedGroup is already undefined, skip.
            return;
        }
        proc.getReceivers().setIsReceivingBroadcast(false);
        proc.getReceivers().setBroadcastReceiverSchedGroup(SCHED_GROUP_UNDEFINED);

        proc.clearHostingComponentType(HOSTING_COMPONENT_TYPE_BROADCAST_RECEIVER);

        if (Flags.pscAutoUpdateBroadcastState()) {
            runUpdate(proc, OOM_ADJ_REASON_FINISH_RECEIVER);
        }
    }

    /**
     * Sets whether the zram memory of this process was written back to disk.
     */
    @GuardedBy({"mLock", "mProcLock"})
    public void setIsZramWrittenBack(@NonNull ProcessRecordInternal proc,
            boolean isZramWrittenBack) {
        if (proc.isZramWrittenBack() == isZramWrittenBack) {
            return;
        }
        proc.setIsZramWrittenBack(isZramWrittenBack);
        mOomAdjuster.onZramWritebackStateChanged(proc, isZramWrittenBack);
    }

    @GuardedBy("mLock")
    private void commitStagedEvents() {
        mGlobalState.commitStagedState();

        // Drain any activity state changes from the staging queue.
        final ConcurrentLinkedQueue<Runnable> queue = mStagingQueue;
        while (!queue.isEmpty()) {
            queue.poll().run();
        }
    }

    /**
     * Helper class for sending Activity related state from Window Manager to
     * ProcessStateController. Because ProcessStateController is guarded by a lock WindowManager
     * avoids acquiring, all of the work will posted to the provided Looper's thread with the
     * provided lock object.
     *
     * ActivityStateAsyncUpdater is not thread-safe and its usage should always be guarded by the
     * WindowManagerGlobalLock.
     */
    public static class ActivityStateAsyncUpdater {
        private final ProcessStateController mPsc;
        private final Looper mLooper;
        private ConcurrentLinkedQueue<Runnable> mStagingQueue;
        private AsyncBatchSession mBatchSession;

        private ActivityStateAsyncUpdater(ProcessStateController psc, Looper looper,
                ConcurrentLinkedQueue<Runnable> stagingQueue) {
            mPsc = psc;
            mLooper = looper;
            mStagingQueue = stagingQueue;
        }

        /**
         * Start a batch session. Any async work will not be posted to the Handler thread until
         * the returned AsyncBatchSession is closed.
         */
        public AsyncBatchSession startBatchSession() {
            final AsyncBatchSession session = getBatchSession();
            session.start(OOM_ADJ_REASON_ACTIVITY);
            return session;
        }

        /**
         * Trigger an OomAdjuster full update.
         */
        public void runUpdateAsync() {
            getBatchSession().runUpdate();
        }

        /**
         * Set whether the device is currently unlocking.
         */
        public void setDeviceUnlocking(boolean unlocking) {
            mPsc.mGlobalState.mUnlockingStaged = unlocking;
        }

        /**
         * Set whether the top process is occluded by the notification shade.
         */
        public void setExpandedNotificationShadeAsync(boolean expandedShade) {
            getBatchSession().stage(() -> mPsc.setExpandedNotificationShade(expandedShade));
        }

        /**
         * Set the Top process, also clear the Previous process and demotion reason, if necessary.
         */
        public void setTopProcessAsync(@Nullable ProcessRecordInternal top, boolean clearPrev,
                boolean cancelExpandedShade) {
            getBatchSession().stage(() -> {
                mPsc.setTopProcess(top);
                if (clearPrev) {
                    mPsc.setPreviousProcess(null);
                }
                if (cancelExpandedShade) {
                    mPsc.setExpandedNotificationShade(false);
                }
            });
        }

        /**
         * Set which process state Top processes should get.
         */
        public void setTopProcessStateAsync(@ActivityManager.ProcessState int procState) {
            getBatchSession().stage(() -> mPsc.setTopProcessState(procState));
        }

        /**
         * Set which process is considered the Previous process, if any.
         */
        public void setPreviousProcessAsync(@Nullable ProcessRecordInternal prev) {
            getBatchSession().stage(() -> mPsc.setPreviousProcess(prev));
        }


        /**
         * Set which process is considered the Home process, if any.
         */
        public void setHomeProcessAsync(@Nullable ProcessRecordInternal home) {
            getBatchSession().stage(() -> mPsc.setHomeProcess(home));
        }


        /**
         * Set which process is considered the Heavy Weight process, if any.
         */
        public void setHeavyWeightProcessAsync(@Nullable ProcessRecordInternal heavy) {
            getBatchSession().stage(() -> mPsc.setHeavyWeightProcess(heavy));
        }

        /**
         * Set which process is showing UI while the screen is off, if any.
         */
        public void setVisibleDozeUiProcessAsync(@Nullable ProcessRecordInternal dozeUi) {
            getBatchSession().stage(() -> mPsc.setVisibleDozeUiProcess(dozeUi));
        }

        /**
         * Note whether the process has an activity or not.
         */
        public void setHasActivityAsync(@NonNull ProcessRecordInternal proc, boolean hasActivity) {
            getBatchSession().stage(() -> mPsc.setHasActivity(proc, hasActivity));
        }

        /**
         * Set the Activity State for a process, including the Activity state flags and the time
         * when a perceptible task stopped.
         */
        public void setActivityStateAsync(@NonNull ProcessRecordInternal proc, int flags,
                long perceptibleStopTimeMs) {
            getBatchSession().stage(() -> {
                mPsc.setActivityStateFlags(proc, flags);
                mPsc.setPerceptibleTaskStoppedTimeMillis(proc, perceptibleStopTimeMs);
            });
        }

        /**
         * Set whether a process has had any recent tasks.
         */
        public void setHasRecentTasksAsync(@NonNull ProcessRecordInternal proc,
                boolean hasRecentTasks) {
            getBatchSession().stage(() -> mPsc.setHasRecentTasks(proc, hasRecentTasks));
        }

        private AsyncBatchSession getBatchSession() {
            if (mBatchSession == null) {
                final Handler h = new Handler(mLooper);
                final Runnable update = () -> mPsc.runFullUpdate(OOM_ADJ_REASON_ACTIVITY);
                mBatchSession = new AsyncBatchSession(h, mPsc.mLock, mStagingQueue, update);
            }
            return mBatchSession;
        }
    }

    /**
     * Interface for injecting LRU management into ProcessStateController
     * TODO(b/430385382): This should be remove when LRU is managed entirely within
     * ProcessStateController.
     */
    public interface ProcessLruUpdater {
        /** Bump a process to the end of the LRU list */
        void updateLruProcessLocked(ProcessRecordInternal app, boolean activityChange,
                ProcessRecordInternal client);
        /** Remove a process from the LRU list */
        void removeLruProcessLocked(ProcessRecordInternal app);
    }

    /**
     * Builder for ProcessStateController.
     */
    public static class Builder {
        private final ProcessListInternal mProcessList;
        private final ActiveUidsInternal mActiveUids;
        private final OomAdjuster.Constants mOomConstants;
        private final OomAdjuster.Callback mOomAdjCallback;

        private ServiceThread mHandlerThread = null;
        private Object mLock = null;
        private Object mProcLock = null;
        private Consumer<ProcessRecordInternal> mTopChangeCallback = null;
        private ProcessLruUpdater mProcessLruUpdater = null;
        private OomAdjuster.Injector mOomAdjInjector = null;
        private OomAdjuster.HostingTypeProvider mHostingTypeProvider = null;

        public Builder(ProcessListInternal processList,
                ActiveUidsInternal activeUids, OomAdjuster.Constants oomConstants,
                OomAdjuster.Callback oomAdjCallback) {
            mProcessList = processList;
            mActiveUids = activeUids;
            mOomConstants = oomConstants;
            mOomAdjCallback = oomAdjCallback;
        }

        /**
         * Build the ProcessStateController object.
         */
        public ProcessStateController build() {
            if (mHandlerThread == null) {
                mHandlerThread = OomAdjuster.createAdjusterThread();
            }
            if (mLock == null) {
                mLock = new Object();
            }
            if (mProcLock == null) {
                mProcLock = new Object();
            }
            if (mTopChangeCallback == null) {
                mTopChangeCallback = proc -> {};
            }
            if (mProcessLruUpdater == null) {
                // Just attach a no-op updater. For Testing that does not care about the LRU.
                mProcessLruUpdater = new ProcessLruUpdater() {
                    public void updateLruProcessLocked(ProcessRecordInternal app,
                            boolean activityChange, ProcessRecordInternal client) {}
                    public void removeLruProcessLocked(ProcessRecordInternal app) {}
                };
            }
            if (mOomAdjInjector == null) {
                mOomAdjInjector = new OomAdjuster.Injector();
            }
            if (mHostingTypeProvider == null) {
                mHostingTypeProvider = app -> HostingRecord.HOSTING_TYPE_EMPTY;
            }
            return new ProcessStateController(mProcessList, mActiveUids, mHandlerThread,
                    mLock, mProcLock, mTopChangeCallback, mProcessLruUpdater, mOomAdjInjector,
                    mOomConstants, mOomAdjCallback, mHostingTypeProvider);
        }

        /**
         * For Testing Purposes. Set what thread OomAdjuster will offload tasks on to.
         */
        @VisibleForTesting
        public Builder setHandlerThread(ServiceThread handlerThread) {
            mHandlerThread = handlerThread;
            return this;
        }

        /**
         * For Testing Purposes. Set an injector for OomAdjuster.
         */
        @VisibleForTesting
        public Builder setOomAdjusterInjector(OomAdjuster.Injector injector) {
            mOomAdjInjector = injector;
            return this;
        }

        /**
         * Set what object ProcessStateController will lock on for synchronized work.
         */
        public Builder setLockObject(Object lock) {
            mLock = lock;
            return this;
        }

        /**
         * Sets the lock object used for synchronizing access to process-related data structures.
         */
        public Builder setProcLockObject(Object lock) {
            mProcLock = lock;
            return this;
        }

        /**
         * Set a callback for when ProcessStateController is informed about the Top process
         * changing.
         */
        public Builder setTopProcessChangeCallback(Consumer<ProcessRecordInternal> callback) {
            mTopChangeCallback = callback;
            return this;
        }

        /**
         * Set a callback for when ProcessStateController is informed about the Top process
         * changing.
         */
        public Builder setProcessLruUpdater(ProcessLruUpdater updater) {
            mProcessLruUpdater = updater;
            return this;
        }

        /**
         * Set the provider for hosting type.
         */
        public Builder setHostingTypeProvider(OomAdjuster.HostingTypeProvider provider) {
            mHostingTypeProvider = provider;
            return this;
        }
    }
}
