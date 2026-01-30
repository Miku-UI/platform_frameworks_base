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
package com.android.server.am;

import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_ACTIVITY;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_BACKUP;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_SERVICE_BINDER_CALL;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_UI_VISIBILITY;
import static android.app.ProcessMemoryState.HOSTING_COMPONENT_TYPE_BROADCAST_RECEIVER;

import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_OOM_ADJ;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal.OomAdjReason;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManagerInternal;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.ServiceThread;
import com.android.server.am.psc.AsyncBatchSession;
import com.android.server.am.psc.ProcessRecordInternal;
import com.android.server.am.psc.ServiceRecordInternal;
import com.android.server.am.psc.SyncBatchSession;
import com.android.server.am.psc.annotation.RequiresEnclosingBatchSession;
import com.android.server.wm.WindowProcessController;

import java.lang.ref.WeakReference;
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

    private final OomAdjuster mOomAdjuster;
    private final BiConsumer<ConnectionRecord, Boolean> mServiceBinderCallUpdater;

    // TODO(b/425766486): Investigate if we could use java.util.concurrent.locks.ReadWriteLock.
    private final Object mLock;
    private final Object mProcLock;

    private final Consumer<ProcessRecord> mTopChangeCallback;

    private final ProcessLruUpdater mProcessLruUpdater;

    private final GlobalState mGlobalState = new GlobalState();

    private SyncBatchSession mBatchSession;

    /**
     * Queue for staging asynchronous events. The queue will be drained before each update.
     */
    private final ConcurrentLinkedQueue<Runnable> mStagingQueue = new ConcurrentLinkedQueue<>();

    private ProcessStateController(ActivityManagerService ams, ProcessList processList,
            ActiveUids activeUids, ServiceThread handlerThread,
            Object lock, Object procLock, Consumer<ProcessRecord> topChangeCallback,
            ProcessLruUpdater lruUpdater, OomAdjuster.Injector oomAdjInjector,
            OomAdjuster.Callback callback) {
        mOomAdjuster = new OomAdjusterImpl(ams, processList, activeUids, handlerThread,
                mGlobalState, oomAdjInjector, callback);

        mLock = lock;
        mProcLock = procLock;
        mTopChangeCallback = topChangeCallback;
        mProcessLruUpdater = lruUpdater;
        final Handler serviceHandler = new Handler(handlerThread.getLooper());
        mServiceBinderCallUpdater = (cr, hasOngoingCalls) -> serviceHandler.post(() -> {
            synchronized (ams) {
                if (cr.setOngoingCalls(hasOngoingCalls)) {
                    runUpdate(cr.binding.client, OOM_ADJ_REASON_SERVICE_BINDER_CALL);
                }
            }
        });

    }

    /**
     * Start a batch session for specifically service state changes. ProcessStateController updates
     * will not be triggered until until the returned SyncBatchSession is closed.
     */
    public SyncBatchSession startServiceBatchSession(@OomAdjReason int reason) {
        if (!Flags.pscBatchServiceUpdates()) return null;

        final SyncBatchSession batchSession = getBatchSession();
        batchSession.start(reason);
        return batchSession;
    }

    /**
     * Start a batch session. ProcessStateController updates will not be triggered until the
     * returned SyncBatchSession is closed.
     */
    @GuardedBy("mLock")
    public SyncBatchSession startBatchSession(@OomAdjReason int reason) {
        if (!Flags.pscBatchUpdate()) return null;

        final SyncBatchSession batchSession = getBatchSession();
        batchSession.start(reason);
        return batchSession;
    }

    private SyncBatchSession getBatchSession() {
        if (mBatchSession == null) {
            mBatchSession = new SyncBatchSession(this::runFullUpdateImpl,
                    this::runPendingUpdateImpl);
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
    public void enqueueUpdateTarget(@Nullable ProcessRecord proc) {
        mOomAdjuster.enqueueOomAdjTargetLocked(proc);
    }

    /**
     * Remove a process that was added by {@link #enqueueUpdateTarget}.
     */
    @GuardedBy("mLock")
    public void removeUpdateTarget(@NonNull ProcessRecord proc, boolean procDied) {
        mOomAdjuster.removeOomAdjTargetLocked(proc, procDied);
    }

    /**
     * Trigger an update on a single process (and any processes that have been enqueued with
     * {@link #enqueueUpdateTarget}).
     */
    @GuardedBy("mLock")
    public boolean runUpdate(@NonNull ProcessRecord proc, @OomAdjReason int oomAdjReason) {
        if (mBatchSession != null && mBatchSession.isActive()) {
            // BatchSession is active, just enqueue the proc for now. The update will happen
            // at the end of the session.
            enqueueUpdateTarget(proc);
            return false;
        }
        return runUpdateimpl(proc, oomAdjReason);
    }

    @GuardedBy("mLock")
    private boolean runUpdateimpl(@NonNull ProcessRecord proc, @OomAdjReason int oomAdjReason) {
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

    @GuardedBy("mLock")
    private void runPendingUpdateImpl(@OomAdjReason int oomAdjReason) {
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
     * Returns a {@link BoundServiceSession} for the given {@link ConnectionRecord}. Creates and
     * associates a new one if required.
     */
    public BoundServiceSession getBoundServiceSessionFor(ConnectionRecord connectionRecord) {
        if (connectionRecord.notHasFlag(Context.BIND_ALLOW_FREEZE) && connectionRecord.notHasFlag(
                Context.BIND_SIMULATE_ALLOW_FREEZE)) {
            // Don't incur the memory and compute overhead for process state adjustments for all
            // bindings by default. This should be opted into as needed.
            return null;
        }
        if (connectionRecord.mBoundServiceSession != null) {
            return connectionRecord.mBoundServiceSession;
        }
        connectionRecord.mBoundServiceSession = new BoundServiceSession(mServiceBinderCallUpdater,
                new WeakReference<>(connectionRecord), connectionRecord.toShortString());
        return connectionRecord.mBoundServiceSession;
    }

    private static class GlobalState implements OomAdjuster.GlobalState {
        private boolean mIsAwake = true;
        // TODO(b/369300367): Maintaining global state for backup processes is a bit convoluted.
        //  ideally the state gets migrated to ProcessRecordInternal.
        private final SparseArray<ProcessRecord> mBackupTargets = new SparseArray<>();
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

        private void commitStagedState() {
            mUnlocking = mUnlockingStaged;
        }

        public boolean isAwake() {
            return mIsAwake;
        }

        public ProcessRecord getBackupTarget(@UserIdInt int userId) {
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
    private void setTopProcess(@Nullable ProcessRecord proc) {
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
    public void setBackupTarget(@NonNull ProcessRecord proc, @UserIdInt int userId) {
        final ProcessRecord prev = mGlobalState.mBackupTargets.get(userId);
        if (prev == proc) return;
        mGlobalState.mBackupTargets.put(userId, proc);

        if (Flags.pushGlobalStateToOomadjuster() && Flags.autoTriggerOomadjUpdates()) {
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
        final ProcessRecord prev = mGlobalState.mBackupTargets.removeReturnOld(userId);
        if (prev == null) return;

        if (Flags.pushGlobalStateToOomadjuster() && Flags.autoTriggerOomadjUpdates()) {
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

    /***************************** UID State Events ****************************/
    /**
     * Set a UID as temp allowlisted.
     */
    @GuardedBy("mLock")
    public void setUidTempAllowlistStateLSP(int uid, boolean allowList) {
        mOomAdjuster.setUidTempAllowlistStateLSP(uid, allowList);
    }

    /*********************** Process Miscellaneous Events **********************/
    /**
     * Set the maximum adj score a process can be assigned.
     */
    @GuardedBy("mLock")
    public void setMaxAdj(@NonNull ProcessRecordInternal proc, int adj) {
        proc.setMaxAdj(adj);
    }

    /**
     * Initialize a process that is being attached.
     */
    @GuardedBy({"mService", "mProcLock"})
    public void setAttachingProcessStatesLSP(@NonNull ProcessRecord proc) {
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
     * Sets an active instrumentation running within the given process.
     */
    @GuardedBy("mLock")
    public void setActiveInstrumentation(@NonNull ProcessRecord proc,
            ActiveInstrumentation activeInstrumentation) {
        proc.setActiveInstrumentation(activeInstrumentation);
    }

    @GuardedBy("mLock")
    void forceProcessStateUpTo(@NonNull ProcessRecord proc, int newState) {
        final int prevProcState = proc.getReportedProcState();
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
    public void updateLruProcess(@NonNull ProcessRecord proc, boolean activityChange,
            @Nullable ProcessRecord client) {
        mProcessLruUpdater.updateLruProcessLocked(proc, activityChange, client);
    }

    /**
     * Remove a process from the LRU list.
     */
    @GuardedBy("mLock")
    public void removeLruProcess(@NonNull ProcessRecord proc) {
        mProcessLruUpdater.removeLruProcessLocked(proc);
    }

    /********************* Process Visibility State Events *********************/
    /**
     * Note whether a process has Top UI or not.
     *
     * @return true if the state changed, otherwise returns false.
     */
    @GuardedBy("mLock")
    public boolean setHasTopUi(@NonNull ProcessRecord proc, boolean hasTopUi) {
        if (proc.getHasTopUi() == hasTopUi) return false;
        if (DEBUG_OOM_ADJ) {
            Slog.d(TAG, "Setting hasTopUi=" + hasTopUi + " for pid=" + proc.getPid());
        }
        proc.setHasTopUi(hasTopUi);
        return true;
    }

    /**
     * Note whether a process is displaying Overlay UI or not.
     *
     * @return true if the state changed, otherwise returns false.
     */
    @GuardedBy("mLock")
    public boolean setHasOverlayUi(@NonNull ProcessRecordInternal proc, boolean hasOverlayUi) {
        if (proc.getHasOverlayUi() == hasOverlayUi) return false;
        proc.setHasOverlayUi(hasOverlayUi);
        return true;
    }


    /**
     * Note whether a process is running a remote animation.
     *
     * @return true if the state changed, otherwise returns false.
     */
    @GuardedBy("mLock")
    public boolean setRunningRemoteAnimation(@NonNull ProcessRecord proc,
            boolean runningRemoteAnimation) {
        if (proc.isRunningRemoteAnimation() == runningRemoteAnimation) return false;
        if (DEBUG_OOM_ADJ) {
            Slog.i(TAG, "Setting runningRemoteAnimation=" + runningRemoteAnimation
                    + " for pid=" + proc.getPid());
        }
        proc.setIsRunningRemoteAnimation(runningRemoteAnimation);

        if (Flags.autoTriggerOomadjUpdates()) {
            enqueueUpdateTarget(proc);
            runPendingUpdate(OOM_ADJ_REASON_UI_VISIBILITY);
        }
        return true;
    }

    /**
     * Note that the process is showing a toast.
     */
    @GuardedBy("mLock")
    public void setForcingToImportant(@NonNull ProcessRecordInternal proc,
            @Nullable Object forcingToImportant) {
        if (proc.getForcingToImportant() == forcingToImportant) return;
        proc.setForcingToImportant(forcingToImportant);
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
    public boolean addPublishedProvider(@NonNull ProcessRecord proc, String name,
            ContentProviderRecord cpr) {
        final ProcessProviderRecord providers = proc.mProviders;
        if (providers.hasProvider(name)) return false;
        providers.installProvider(name, cpr);
        return true;
    }

    /**
     * Remove a published content provider from a process.
     */
    @GuardedBy("mLock")
    public void removePublishedProvider(@NonNull ProcessRecord proc, String name) {
        final ProcessProviderRecord providers = proc.mProviders;
        providers.removeProvider(name);
    }

    /**
     * Note that a content provider has an external client.
     */
    @GuardedBy("mLock")
    public void addExternalProviderClient(@NonNull ContentProviderRecord cpr,
            IBinder externalProcessToken, int callingUid, String callingTag) {
        cpr.addExternalProcessHandleLocked(externalProcessToken, callingUid, callingTag);
    }

    /**
     * Remove an external client from a conetnt provider.
     */
    @GuardedBy("mLock")
    public boolean removeExternalProviderClient(@NonNull ContentProviderRecord cpr,
            IBinder externalProcessToken) {
        return cpr.removeExternalProcessHandleLocked(externalProcessToken);
    }

    /**
     * Note the time a process is no longer hosting any content providers.
     */
    @GuardedBy("mLock")
    public void setLastProviderTime(@NonNull ProcessRecord proc, long uptimeMs) {
        proc.mProviders.setLastProviderTime(uptimeMs);
    }

    /**
     * Note that a process has connected to a content provider.
     */
    @GuardedBy("mLock")
    public void addProviderConnection(@NonNull ProcessRecord client,
            ContentProviderConnection cpc) {
        client.mProviders.addProviderConnection(cpc);
    }

    /**
     * Note that a process is no longer connected to a content provider.
     */
    @GuardedBy("mLock")
    public void removeProviderConnection(@NonNull ProcessRecord client,
            ContentProviderConnection cpc) {
        client.mProviders.removeProviderConnection(cpc);
    }

    /*************************** Service State Events **************************/
    /**
     * Note that a process has started hosting a service.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public boolean startService(@NonNull ProcessServiceRecord psr, ServiceRecord sr) {
        return psr.startService(sr);
    }

    /**
     * Note that a process has stopped hosting a service.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public boolean stopService(@NonNull ProcessServiceRecord psr, ServiceRecord sr) {
        return psr.stopService(sr);
    }

    /**
     * Remove all services that the process is hosting.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void stopAllServices(@NonNull ProcessServiceRecord psr) {
        psr.stopAllServices();
    }

    /**
     * Note that a process's service has started executing.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void startExecutingService(@NonNull ProcessServiceRecord psr, ServiceRecord sr) {
        psr.startExecutingService(sr);
    }

    /**
     * Note that a process's service has stopped executing.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void stopExecutingService(@NonNull ProcessServiceRecord psr, ServiceRecord sr) {
        psr.stopExecutingService(sr);
    }

    /**
     * Note all executing services a process has has stopped.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void stopAllExecutingServices(@NonNull ProcessServiceRecord psr) {
        psr.stopAllExecutingServices();
    }

    /**
     * Note that process has bound to a service.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void addConnection(@NonNull ProcessServiceRecord psr, ConnectionRecord cr) {
        psr.addConnection(cr);
    }

    /**
     * Note that process has unbound from a service.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void removeConnection(@NonNull ProcessServiceRecord psr, ConnectionRecord cr) {
        psr.removeConnection(cr);
    }

    /**
     * Remove all bindings a process has to services.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void removeAllConnections(@NonNull ProcessServiceRecord psr) {
        psr.removeAllConnections();
        psr.removeAllSdkSandboxConnections();
    }

    /**
     * Note whether an executing service should be considered in the foreground or not.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void setExecServicesFg(@NonNull ProcessServiceRecord psr, boolean execServicesFg) {
        psr.setExecServicesFg(execServicesFg);
    }

    /**
     * Note whether a service is in the foreground or not and what type of FGS, if so.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void setHasForegroundServices(@NonNull ProcessServiceRecord psr,
            boolean hasForegroundServices,
            int fgServiceTypes, boolean hasTypeNoneFgs) {
        psr.setHasForegroundServices(hasForegroundServices, fgServiceTypes, hasTypeNoneFgs);
    }

    /**
     * Note whether a service has a client activity or not.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void setHasClientActivities(@NonNull ProcessServiceRecord psr,
            boolean hasClientActivities) {
        psr.setHasClientActivities(hasClientActivities);
    }

    /**
     * Note whether a service should be treated like an activity or not.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void setTreatLikeActivity(@NonNull ProcessServiceRecord psr, boolean treatLikeActivity) {
        psr.setTreatLikeActivity(treatLikeActivity);
    }

    /**
     * Update the ongoing binder calls state for a given Connection record.
     */
    public boolean updateBinderServiceCalls(ConnectionRecord cr, boolean ongoing) {
        return cr.setOngoingCalls(ongoing);
    }

    /**
     * Note whether a process has bound to a service with
     * {@link android.content.Context.BIND_ABOVE_CLIENT} or not.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void setHasAboveClient(@NonNull ProcessServiceRecord psr, boolean hasAboveClient) {
        psr.setHasAboveClient(hasAboveClient);
    }

    /**
     * Recompute whether a process has bound to a service with
     * {@link android.content.Context.BIND_ABOVE_CLIENT} or not.
     */
    @GuardedBy("mLock")
    @RequiresEnclosingBatchSession
    public void updateHasAboveClientLocked(@NonNull ProcessServiceRecord psr) {
        psr.updateHasAboveClientLocked();
    }

    /**
     * Cleanup a process's state.
     */
    @GuardedBy("mLock")
    public void onCleanupApplicationRecord(@NonNull ProcessServiceRecord psr) {
        psr.onCleanupApplicationRecordLocked();
    }

    /**
     * Set which process is hosting a service.
     */
    @GuardedBy("mLock")
    public void setHostProcess(@NonNull ServiceRecord sr, @Nullable ProcessRecord host) {
        sr.app = host;
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
    public void setShortFgsInfo(@NonNull ServiceRecord sr, long uptimeNow) {
        sr.setShortFgsInfo(uptimeNow);
    }

    /**
     * Note that a short foreground service has stopped.
     */
    @GuardedBy("mLock")
    public void clearShortFgsInfo(@NonNull ServiceRecord sr) {
        sr.clearShortFgsInfo();
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
    public void updateHasTopStartedAlmostPerceptibleServices(@NonNull ProcessServiceRecord psr) {
        psr.updateHasTopStartedAlmostPerceptibleServices();
    }

    /************************ Broadcast Receiver State Events **************************/
    /**
     * Note that Broadcast delivery to a process has started and what scheduling group should be
     * used.
     */
    @GuardedBy("mLock")
    public void noteBroadcastDeliveryStarted(@NonNull ProcessRecord proc, int schedGroup) {
        proc.getReceivers().setIsReceivingBroadcast(true);
        proc.getReceivers().setBroadcastReceiverSchedGroup(schedGroup);

        if (Flags.pushBroadcastStateToOomadjuster()) {
            proc.mProfile.addHostingComponentType(HOSTING_COMPONENT_TYPE_BROADCAST_RECEIVER);
        }
    }

    /**
     * Note that Broadcast delivery to a process has ended.
     */
    @GuardedBy("mLock")
    public void noteBroadcastDeliveryEnded(@NonNull ProcessRecord proc) {
        proc.getReceivers().setIsReceivingBroadcast(false);
        proc.getReceivers().setBroadcastReceiverSchedGroup(ProcessList.SCHED_GROUP_UNDEFINED);

        if (Flags.pushBroadcastStateToOomadjuster()) {
            proc.mProfile.clearHostingComponentType(HOSTING_COMPONENT_TYPE_BROADCAST_RECEIVER);
        }
    }

    @GuardedBy("mLock")
    private void commitStagedEvents() {
        mGlobalState.commitStagedState();

        if (Flags.pushActivityStateToOomadjuster()) {
            // Drain any activity state changes from the staging queue.
            final ConcurrentLinkedQueue<Runnable> queue = mStagingQueue;
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
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
            if (!Flags.pushActivityStateToOomadjuster()) return null;

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
            if (!Flags.pushActivityStateToOomadjuster()) return;

            mPsc.mGlobalState.mUnlockingStaged = unlocking;
        }

        /**
         * Set whether the top process is occluded by the notification shade.
         */
        public void setExpandedNotificationShadeAsync(boolean expandedShade) {
            if (!Flags.pushActivityStateToOomadjuster()) return;

            getBatchSession().stage(() -> mPsc.setExpandedNotificationShade(expandedShade));
        }

        /**
         * Set the Top process, also clear the Previous process and demotion reason, if necessary.
         */
        public void setTopProcessAsync(@Nullable WindowProcessController wpc, boolean clearPrev,
                boolean cancelExpandedShade) {
            if (!Flags.pushActivityStateToOomadjuster()) return;

            final ProcessRecord top = wpc != null ? (ProcessRecord) wpc.mOwner : null;
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
            if (!Flags.pushActivityStateToOomadjuster()) return;

            getBatchSession().stage(() -> mPsc.setTopProcessState(procState));
        }

        /**
         * Set which process is considered the Previous process, if any.
         */
        public void setPreviousProcessAsync(@Nullable WindowProcessController wpc) {
            if (!Flags.pushActivityStateToOomadjuster()) return;

            final ProcessRecordInternal prev = wpc != null
                    ? (ProcessRecordInternal) wpc.mOwner : null;
            getBatchSession().stage(() -> mPsc.setPreviousProcess(prev));
        }


        /**
         * Set which process is considered the Home process, if any.
         */
        public void setHomeProcessAsync(@Nullable WindowProcessController wpc) {
            if (!Flags.pushActivityStateToOomadjuster()) return;

            final ProcessRecordInternal home = wpc != null
                    ? (ProcessRecordInternal) wpc.mOwner : null;
            getBatchSession().stage(() -> mPsc.setHomeProcess(home));
        }


        /**
         * Set which process is considered the Heavy Weight process, if any.
         */
        public void setHeavyWeightProcessAsync(@Nullable WindowProcessController wpc) {
            if (!Flags.pushActivityStateToOomadjuster()) return;

            final ProcessRecord heavy = wpc != null ? (ProcessRecord) wpc.mOwner : null;
            getBatchSession().stage(() -> mPsc.setHeavyWeightProcess(heavy));
        }

        /**
         * Set which process is showing UI while the screen is off, if any.
         */
        public void setVisibleDozeUiProcessAsync(@Nullable WindowProcessController wpc) {
            if (!Flags.pushActivityStateToOomadjuster()) return;

            final ProcessRecord dozeUi = wpc != null ? (ProcessRecord) wpc.mOwner : null;
            getBatchSession().stage(() -> mPsc.setVisibleDozeUiProcess(dozeUi));
        }

        /**
         * Note whether the process has an activity or not.
         */
        public void setHasActivityAsync(@NonNull WindowProcessController wpc, boolean hasActivity) {
            if (!Flags.pushActivityStateToOomadjuster()) return;

            final ProcessRecordInternal activity = (ProcessRecordInternal) wpc.mOwner;
            getBatchSession().stage(() -> mPsc.setHasActivity(activity, hasActivity));
        }

        /**
         * Set the Activity State for a process, including the Activity state flags and when a
         */
        public void setActivityStateAsync(@NonNull WindowProcessController wpc, int flags,
                long perceptibleStopTimeMs) {
            if (!Flags.pushActivityStateToOomadjuster()) return;

            final ProcessRecordInternal activity = (ProcessRecordInternal) wpc.mOwner;
            getBatchSession().stage(() -> {
                mPsc.setActivityStateFlags(activity, flags);
                mPsc.setPerceptibleTaskStoppedTimeMillis(activity, perceptibleStopTimeMs);
            });
        }

        /**
         * Set whether a process has had any recent tasks.
         */
        public void setHasRecentTasksAsync(@NonNull WindowProcessController wpc,
                boolean hasRecentTasks) {
            if (!Flags.pushActivityStateToOomadjuster()) return;

            final ProcessRecordInternal proc = (ProcessRecordInternal) wpc.mOwner;
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
        void updateLruProcessLocked(ProcessRecord app, boolean activityChange,
                ProcessRecord client);
        /** Remove a process from the LRU list */
        void removeLruProcessLocked(ProcessRecord app);
    }

    /**
     * Builder for ProcessStateController.
     */
    public static class Builder {
        private final ActivityManagerService mAms;
        private final ProcessList mProcessList;
        private final ActiveUids mActiveUids;
        private final OomAdjuster.Callback mOomAdjCallback;

        private ServiceThread mHandlerThread = null;
        private Object mLock = null;
        private Consumer<ProcessRecord> mTopChangeCallback = null;
        private ProcessLruUpdater mProcessLruUpdater = null;
        private OomAdjuster.Injector mOomAdjInjector = null;

        public Builder(ActivityManagerService ams, ProcessList processList, ActiveUids activeUids,
                OomAdjuster.Callback oomAdjCallback) {
            mAms = ams;
            mProcessList = processList;
            mActiveUids = activeUids;
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
            if (mTopChangeCallback == null) {
                mTopChangeCallback = proc -> {};
            }
            if (mProcessLruUpdater == null) {
                // Just attach a no-op updater. For Testing that does not care about the LRU.
                mProcessLruUpdater = new ProcessLruUpdater() {
                    public void updateLruProcessLocked(ProcessRecord app, boolean activityChange,
                            ProcessRecord client) {}
                    public void removeLruProcessLocked(ProcessRecord app) {}
                };
            }
            if (mOomAdjInjector == null) {
                mOomAdjInjector = new OomAdjuster.Injector();
            }
            return new ProcessStateController(mAms, mProcessList, mActiveUids, mHandlerThread,
                    mLock, mAms.mProcLock, mTopChangeCallback, mProcessLruUpdater, mOomAdjInjector,
                    mOomAdjCallback);
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
         * Set a callback for when ProcessStateController is informed about the Top process
         * changing.
         */
        public Builder setTopProcessChangeCallback(Consumer<ProcessRecord> callback) {
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
    }
}
