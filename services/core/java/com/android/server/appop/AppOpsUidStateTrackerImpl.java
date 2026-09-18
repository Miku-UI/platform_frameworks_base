/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.appop;

import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_CAMERA;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.app.ActivityManager.ProcessCapability;
import static android.app.ActivityManager.ProcessState;
import static android.app.AppOpsManager.MIN_PRIORITY_UID_STATE;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_FOREGROUND;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OP_CAMERA;
import static android.app.AppOpsManager.OP_CONTROL_AUDIO;
import static android.app.AppOpsManager.OP_NONE;
import static android.app.AppOpsManager.OP_RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO;
import static android.app.AppOpsManager.OP_RECORD_AUDIO;
import static android.app.AppOpsManager.UID_STATE_CACHED;
import static android.app.AppOpsManager.UID_STATE_FOREGROUND_SERVICE;
import static android.app.AppOpsManager.UID_STATE_MAX_LAST_NON_RESTRICTED;
import static android.app.AppOpsManager.UID_STATE_NONEXISTENT;
import static android.app.AppOpsManager.UID_STATE_TOP;

import static com.android.server.appop.AppOpsUidStateTracker.processStateToUidState;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.AppOpsManager;
import android.os.Handler;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.util.SparseLongArray;
import android.util.TimeUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.Clock;
import com.android.internal.util.function.pooled.PooledLambda;
import com.android.server.utils.EventLogger;

import java.io.PrintWriter;
import java.util.concurrent.Executor;

class AppOpsUidStateTrackerImpl implements AppOpsUidStateTracker {

    private static final String LOG_TAG = AppOpsUidStateTrackerImpl.class.getSimpleName();
    private static final int EVENT_LOG_SIZE = 2000;

    private final DelayableExecutor mExecutor;
    private final Clock mClock;
    private ActivityManagerInternal mActivityManagerInternal;
    private AppOpsService.Constants mConstants;

    private SparseIntArray mUidStates = new SparseIntArray();
    private SparseIntArray mPendingUidStates = new SparseIntArray();
    private SparseIntArray mCapability = new SparseIntArray();
    private SparseIntArray mPendingCapability = new SparseIntArray();
    private SparseBooleanArray mAppWidgetVisible = new SparseBooleanArray();
    private SparseBooleanArray mPendingAppWidgetVisible = new SparseBooleanArray();
    private SparseLongArray mPendingCommitTime = new SparseLongArray();

    private ArrayMap<UidStateChangedCallback, Executor>
            mUidStateChangedCallbacks = new ArrayMap<>();

    private final EventLogger mEventLogger = new EventLogger(EVENT_LOG_SIZE, LOG_TAG);

    @VisibleForTesting
    interface DelayableExecutor extends Executor {

        void execute(Runnable runnable);

        void executeDelayed(Runnable runnable, long delay);
    }

    AppOpsUidStateTrackerImpl(ActivityManagerInternal activityManagerInternal,
            Handler handler, Executor lockingExecutor, Clock clock,
            AppOpsService.Constants constants) {

        this(activityManagerInternal, new DelayableExecutor() {
            @Override
            public void execute(Runnable runnable) {
                handler.post(() -> lockingExecutor.execute(runnable));
            }

            @Override
            public void executeDelayed(Runnable runnable, long delay) {
                handler.postDelayed(() -> lockingExecutor.execute(runnable), delay);
            }
        }, clock, constants);
    }

    @VisibleForTesting
    AppOpsUidStateTrackerImpl(ActivityManagerInternal activityManagerInternal,
            DelayableExecutor executor, Clock clock, AppOpsService.Constants constants) {
        mActivityManagerInternal = activityManagerInternal;
        mExecutor = executor;
        mClock = clock;
        mConstants = constants;
    }

    @Override
    public int getUidState(int uid) {
        return getUidStateLocked(uid);
    }

    private int getUidStateLocked(int uid) {
        updateUidPendingStateIfNeeded(uid);
        return mUidStates.get(uid, MIN_PRIORITY_UID_STATE);
    }

    @Override
    public int evalMode(int uid, int code, int mode) {
        if (mode != MODE_FOREGROUND) {
            return mode;
        }

        int uidState = getUidState(uid);
        @ProcessCapability int uidCapability = getUidCapability(uid);
        int result = evalModeInternal(uid, code, uidState, uidCapability);

        mEventLogger
                .enqueue(new EvalForegroundModeEvent(uid, uidState, uidCapability, code, result));
        return result;
    }

    private int evalModeInternal(int uid, int code, int uidState, int uidCapability) {
        if (getUidAppWidgetVisible(uid) || mActivityManagerInternal.isPendingTopUid(uid)
                || mActivityManagerInternal.isTempAllowlistedForFgsWhileInUse(uid)) {
            return MODE_ALLOWED;
        }

        int opCapability = getOpCapability(code);
        if (opCapability != PROCESS_CAPABILITY_NONE) {
            if ((uidCapability & opCapability) == 0) {
                return MODE_IGNORED;
            } else {
                return MODE_ALLOWED;
            }
        }

        if (uidState > AppOpsManager.resolveFirstUnrestrictedUidState(code)) {
            return MODE_IGNORED;
        }

        return MODE_ALLOWED;
    }

    private int getOpCapability(int opCode) {
        switch (opCode) {
            case AppOpsManager.OP_FINE_LOCATION:
            case AppOpsManager.OP_COARSE_LOCATION:
            case AppOpsManager.OP_MONITOR_LOCATION:
            case AppOpsManager.OP_MONITOR_HIGH_POWER_LOCATION:
                return PROCESS_CAPABILITY_FOREGROUND_LOCATION;
            case OP_CAMERA:
                return PROCESS_CAPABILITY_FOREGROUND_CAMERA;
            case OP_RECORD_AUDIO:
            case OP_RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO:
                return PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
            case OP_CONTROL_AUDIO:
                return PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
            default:
                return PROCESS_CAPABILITY_NONE;
        }
    }

    @Override
    public boolean isUidInForeground(int uid) {
        return evalMode(uid, OP_NONE, MODE_FOREGROUND) == MODE_ALLOWED;
    }

    @Override
    public void addUidStateChangedCallback(Executor executor, UidStateChangedCallback callback) {
        if (mUidStateChangedCallbacks.containsKey(callback)) {
            throw new IllegalStateException("Callback is already registered.");
        }

        mUidStateChangedCallbacks.put(callback, executor);
    }

    @Override
    public void removeUidStateChangedCallback(UidStateChangedCallback callback) {
        if (!mUidStateChangedCallbacks.containsKey(callback)) {
            throw new IllegalStateException("Callback is not registered.");
        }
        mUidStateChangedCallbacks.remove(callback);
    }

    @Override
    public void updateAppWidgetVisibility(SparseArray<String> uidPackageNames, boolean visible) {
        int numUids = uidPackageNames.size();
        for (int i = 0; i < numUids; i++) {
            int uid = uidPackageNames.keyAt(i);
            mPendingAppWidgetVisible.put(uid, visible);

            commitUidPendingState(uid);
        }
    }

    @Override
    public void updateUidProcState(int uid, @ProcessState int procState,
            @ProcessCapability int capability) {
        int uidState = processStateToUidState(procState);

        int prevUidState = mUidStates.get(uid, AppOpsManager.UID_STATE_NONEXISTENT);
        int prevCapability = mCapability.get(uid, PROCESS_CAPABILITY_NONE);
        int pendingUidState = mPendingUidStates.get(uid, UID_STATE_NONEXISTENT);
        @ProcessCapability int pendingCapability = mPendingCapability.get(uid,
                PROCESS_CAPABILITY_NONE);
        long pendingStateCommitTime = mPendingCommitTime.get(uid, 0);

        if ((pendingStateCommitTime == 0
                && (uidState != prevUidState || capability != prevCapability))
                || (pendingStateCommitTime != 0
                && (uidState != pendingUidState || capability != pendingCapability))) {

            // If this process update results in a capability or uid state change, log it. It's
            // not interesting otherwise.
            mEventLogger.enqueue(new ProcStateChangedEvent(uid, procState, capability));
            mPendingUidStates.put(uid, uidState);
            mPendingCapability.put(uid, capability);

            boolean hasLostCapability = (prevCapability & ~capability) != 0;

            if (uidState == UID_STATE_NONEXISTENT) {
                commitUidPendingState(uid);
            } else if (uidState < prevUidState) {
                // We are moving to a more important state, or the new state may be in the
                // foreground and the old state is in the background, then always do it
                // immediately.
                commitUidPendingState(uid);
            } else if ((uidState == prevUidState || uidState <= UID_STATE_MAX_LAST_NON_RESTRICTED)
                    && !hasLostCapability) {
                // Process capability hasn't decreased in any bit. UidState has not changed or it
                // has remained at least as important as the restriction threshold
                commitUidPendingState(uid);
            } else if (pendingStateCommitTime == 0) {
                // We are moving to a less important state for the first time,
                // delay the application for a bit.
                final long settleTime;
                if (prevUidState <= UID_STATE_TOP) {
                    settleTime = mConstants.TOP_STATE_SETTLE_TIME;
                } else if (prevUidState <= UID_STATE_FOREGROUND_SERVICE) {
                    settleTime = mConstants.FG_SERVICE_STATE_SETTLE_TIME;
                } else {
                    settleTime = mConstants.BG_STATE_SETTLE_TIME;
                }
                final long commitTime = mClock.elapsedRealtime() + settleTime;
                mPendingCommitTime.put(uid, commitTime);

                mExecutor.executeDelayed(PooledLambda.obtainRunnable(
                                AppOpsUidStateTrackerImpl::updateUidPendingStateIfNeeded, this,
                                uid), settleTime + 1);
            }
        }
    }

    @Override
    public void dumpUidState(PrintWriter pw, int uid, long nowElapsed) {
        int state = mUidStates.get(uid, MIN_PRIORITY_UID_STATE);
        // if no pendingState set to state to suppress output
        int pendingState = mPendingUidStates.get(uid, state);
        pw.print("    state=");
        pw.println(AppOpsManager.getUidStateName(state));
        if (state != pendingState) {
            pw.print("    pendingState=");
            pw.println(AppOpsManager.getUidStateName(pendingState));
        }
        int capability = mCapability.get(uid, PROCESS_CAPABILITY_NONE);
        // if no pendingCapability set to capability to suppress output
        int pendingCapability = mPendingCapability.get(uid, capability);
        pw.print("    capability=");
        ActivityManager.printCapabilitiesFull(pw, capability);
        pw.println();
        if (capability != pendingCapability) {
            pw.print("    pendingCapability=");
            ActivityManager.printCapabilitiesFull(pw, pendingCapability);
            pw.println();
        }
        boolean appWidgetVisible = mAppWidgetVisible.get(uid, false);
        // if no pendingAppWidgetVisible set to appWidgetVisible to suppress output
        boolean pendingAppWidgetVisible = mPendingAppWidgetVisible.get(uid, appWidgetVisible);
        pw.print("    appWidgetVisible=");
        pw.println(appWidgetVisible);
        if (appWidgetVisible != pendingAppWidgetVisible) {
            pw.print("    pendingAppWidgetVisible=");
            pw.println(pendingAppWidgetVisible);
        }
        long pendingStateCommitTime = mPendingCommitTime.get(uid, 0);
        if (pendingStateCommitTime != 0) {
            pw.print("    pendingStateCommitTime=");
            TimeUtils.formatDuration(pendingStateCommitTime, nowElapsed, pw);
            pw.println();
        }
    }

    @Override
    public void dumpEvents(PrintWriter pw) {
        mEventLogger.dump(pw);
    }

    private void updateUidPendingStateIfNeeded(int uid) {
        updateUidPendingStateIfNeededLocked(uid);
    }

    private void updateUidPendingStateIfNeededLocked(int uid) {
        long pendingCommitTime = mPendingCommitTime.get(uid, 0);
        if (pendingCommitTime != 0) {
            long currentTime = mClock.elapsedRealtime();
            if (currentTime < mPendingCommitTime.get(uid)) {
                return;
            }
            commitUidPendingState(uid);
        }
    }

    private void commitUidPendingState(int uid) {

        int uidState = mUidStates.get(uid, UID_STATE_NONEXISTENT);
        int capability = mCapability.get(uid, PROCESS_CAPABILITY_NONE);
        boolean appWidgetVisible = mAppWidgetVisible.get(uid, false);

        int pendingUidState = mPendingUidStates.get(uid, uidState);
        int pendingCapability = mPendingCapability.get(uid, capability);
        boolean pendingAppWidgetVisible = mPendingAppWidgetVisible.get(uid, appWidgetVisible);

        // UID_STATE_NONEXISTENT is a state that isn't used outside of this class, nonexistent
        // processes have always been represented as CACHED
        int externalUidState = Math.min(uidState, UID_STATE_CACHED);
        int externalPendingUidState = Math.min(pendingUidState, UID_STATE_CACHED);

        boolean foregroundChange = externalUidState <= UID_STATE_MAX_LAST_NON_RESTRICTED
                != externalPendingUidState <= UID_STATE_MAX_LAST_NON_RESTRICTED
                || capability != pendingCapability
                || appWidgetVisible != pendingAppWidgetVisible;

        if (externalUidState != externalPendingUidState
                || capability != pendingCapability
                || appWidgetVisible != pendingAppWidgetVisible) {

            if (foregroundChange) {
                // To save on memory usage, log only interesting changes.
                mEventLogger.enqueue(new UidStateCommitEvent(uid, externalPendingUidState,
                        pendingCapability, pendingAppWidgetVisible,
                        appWidgetVisible != pendingAppWidgetVisible));
            }

            for (int i = 0; i < mUidStateChangedCallbacks.size(); i++) {
                UidStateChangedCallback cb = mUidStateChangedCallbacks.keyAt(i);
                Executor executor = mUidStateChangedCallbacks.valueAt(i);

                executor.execute(PooledLambda.obtainRunnable(
                        UidStateChangedCallback::onUidStateChanged, cb, uid,
                        externalPendingUidState, foregroundChange));
            }
        }

        if (pendingUidState == UID_STATE_NONEXISTENT && uidState != pendingUidState) {
            mUidStates.delete(uid);
            mCapability.delete(uid);
            mAppWidgetVisible.delete(uid);
            for (int i = 0; i < mUidStateChangedCallbacks.size(); i++) {
                UidStateChangedCallback cb = mUidStateChangedCallbacks.keyAt(i);
                Executor executor = mUidStateChangedCallbacks.valueAt(i);

                // If foregroundness changed it should be handled in earlier callback invocation
                executor.execute(PooledLambda.obtainRunnable(
                        UidStateChangedCallback::onUidProcessDeath, cb, uid));
            }
        } else {
            mUidStates.put(uid, pendingUidState);
            mCapability.put(uid, pendingCapability);
            mAppWidgetVisible.put(uid, pendingAppWidgetVisible);
        }

        mPendingUidStates.delete(uid);
        mPendingCapability.delete(uid);
        mPendingAppWidgetVisible.delete(uid);
        mPendingCommitTime.delete(uid);
    }

    private @ProcessCapability int getUidCapability(int uid) {
        return mCapability.get(uid, ActivityManager.PROCESS_CAPABILITY_NONE);
    }

    private boolean getUidAppWidgetVisible(int uid) {
        return mAppWidgetVisible.get(uid, false);
    }

    private static class ProcStateChangedEvent extends EventLogger.Event {

        private int mUid;
        private @ProcessState int mProcState;
        private @ProcessCapability int mCapability;

        ProcStateChangedEvent(int uid, @ProcessState int procState,
                @ProcessCapability int capability) {
            mUid = uid;
            mProcState = procState;
            mCapability = capability;
        }

        @Override
        public String eventToString() {
            return String.format("%-22s "
                    + "uid=%-10d "
                    + "procState=%-29s "
                    + "capability=%-12s ",
                    "UPDATE_UID_PROC_STATE",
                    mUid,
                    ActivityManager.procStateToString(mProcState),
                    ActivityManager.getCapabilitiesSummary(mCapability));
        }
    }

    private static class UidStateCommitEvent extends EventLogger.Event {

        private int mUid;
        private int mUidState;
        private @ProcessCapability int mCapability;
        private boolean mAppWidgetVisible;
        private boolean mAppWidgetVisibleChanged;

        UidStateCommitEvent(int uid, int uidState, int capability,
                boolean appWidgetVisible, boolean appWidgetVisibleChanged) {
            mUid = uid;
            mUidState = uidState;
            mCapability = capability;
            mAppWidgetVisible = appWidgetVisible;
            mAppWidgetVisibleChanged = appWidgetVisibleChanged;
        }

        @Override
        public String eventToString() {
            return String.format("%-22s "
                            + "uid=%-10d "
                            + "uidState=%-30s "
                            + "capability=%-12s "
                            + "appWidgetVisible=%s",
                    "COMMIT_UID_STATE",
                    mUid,
                    AppOpsManager.uidStateToString(mUidState),
                    ActivityManager.getCapabilitiesSummary(mCapability),
                    mAppWidgetVisible + (mAppWidgetVisibleChanged ? " (changed)" : ""));
        }
    }

    private static class EvalForegroundModeEvent extends EventLogger.Event {

        private int mUid;
        private int mUidState;
        private @ProcessCapability int mCapability;
        private final int mCode;
        private final int mResult;

        EvalForegroundModeEvent(int uid, int uidState, @ProcessCapability int capability, int code,
                int result) {
            mUid = uid;
            mUidState = uidState;
            mCapability = capability;
            mCode = code;
            mResult = result;
        }

        @Override
        public String eventToString() {
            return String.format("%-22s "
                            + "uid=%-10d "
                            + "uidState=%-30s "
                            + "capability=%-12s "
                            + "code=%-22s"
                            + "result=%s",
                    "EVAL_FOREGROUND_MODE",
                    mUid,
                    AppOpsManager.uidStateToString(mUidState),
                    ActivityManager.getCapabilitiesSummary(mCapability),
                    AppOpsManager.opToName(mCode),
                    AppOpsManager.modeToName(mResult));
        }
    }
}
