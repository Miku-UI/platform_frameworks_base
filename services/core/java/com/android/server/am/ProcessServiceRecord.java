/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.app.ProcessMemoryState.HOSTING_COMPONENT_TYPE_BOUND_SERVICE;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.internal.annotations.GuardedBy;
import com.android.server.am.psc.ProcessServiceRecordInternal;
import com.android.server.am.psc.annotation.RequiresEnclosingBatchSession;
import com.android.server.wm.WindowProcessController;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * The state info of all services in the process.
 */
final class ProcessServiceRecord extends ProcessServiceRecordInternal {
    /**
     * Last reported state of whether it's running any services that are foreground.
     */
    private boolean mRepHasForegroundServices;

    /**
     * App is allowed to manage allowlists such as temporary Power Save mode allowlist.
     */
    boolean mAllowlistManager;

    /**
     * A set of UIDs of all bound clients.
     */
    private ArraySet<Integer> mBoundClientUids = new ArraySet<>();

    /**
     * The process should schedule a service timeout timer but haven't done so.
     */
    private boolean mScheduleServiceTimeoutPending;

    final ProcessRecord mApp;

    private final ActivityManagerService mService;

    ProcessServiceRecord(ProcessRecord app) {
        super(app.mService.mProcessStateController.getOomConstants(), app);

        mApp = app;
        mService = app.mService;
    }

    void setHasReportedForegroundServices(boolean hasForegroundServices) {
        mRepHasForegroundServices = hasForegroundServices;
    }

    boolean hasReportedForegroundServices() {
        return mRepHasForegroundServices;
    }

    boolean areForegroundServiceTypesSame(@ServiceInfo.ForegroundServiceType int types,
            boolean hasTypeNoneFgs) {
        return ((getForegroundServiceTypes() & types) == types)
                && (getHasTypeNoneFgs() == hasTypeNoneFgs);
    }

    /**
     * @return true if the fgs types includes any of the given types.
     * (wouldn't work for TYPE_NONE, which is 0)
     */
    boolean containsAnyForegroundServiceTypes(@ServiceInfo.ForegroundServiceType int types) {
        return (getForegroundServiceTypes() & types) != 0;
    }

    int getNumForegroundServices() {
        int count = 0;
        for (int i = 0, serviceCount = numberOfRunningServices(); i < serviceCount; i++) {
            if (getRunningServiceInternalAt(i).isForeground()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Records a service as running in the process. Note that this method does not actually start
     * the service, but records the service as started for bookkeeping.
     *
     * @return true if the service was added, false otherwise.
     */
    @RequiresEnclosingBatchSession
    boolean startService(@NonNull ServiceRecord record) {
        boolean added = mService.mProcessStateController.addRunningService(this, record);
        if (added && record.serviceInfo != null) {
            mApp.getWindowProcessController().onServiceStarted(record.serviceInfo);
            updateHostingComonentTypeForBindingsLocked();
        }
        if (record.getLastTopAlmostPerceptibleBindRequestUptimeMs() > 0) {
            mService.mProcessStateController.setLastTopStartedAlmostPerceptibleBindRequestUptimeMs(
                    this, Math.max(getLastTopStartedAlmostPerceptibleBindRequestUptimeMs(),
                            record.getLastTopAlmostPerceptibleBindRequestUptimeMs()));
            if (!getHasTopStartedAlmostPerceptibleServices()) {
                mService.mProcessStateController.setHasTopStartedAlmostPerceptibleServices(
                        this, isAlmostPerceptible(record));
            }
        }
        return added;
    }

    /**
     * Records a service as stopped. Note that like {@link #startService(ServiceRecord)} this method
     * does not actually stop the service, but records the service as stopped for bookkeeping.
     *
     * @return true if the service was removed, false otherwise.
     */
    boolean stopService(@NonNull ServiceRecord record) {
        final boolean removed = mService.mProcessStateController.removeRunningService(this, record);
        if (record.getLastTopAlmostPerceptibleBindRequestUptimeMs() > 0) {
            mService.mProcessStateController.updateHasTopStartedAlmostPerceptibleServices(this);
        }
        if (removed) {
            updateHostingComonentTypeForBindingsLocked();
        }
        return removed;
    }

    ServiceRecord getRunningServiceAt(int index) {
        return (ServiceRecord) getRunningServiceInternalAt(index);
    }

    ServiceRecord getExecutingServiceAt(int index) {
        return (ServiceRecord) getExecutingServiceInternalAt(index);
    }

    ConnectionRecord getConnectionAt(int index) {
        return (ConnectionRecord) getConnectionInternalAt(index);
    }

    ConnectionRecord getSdkSandboxConnectionAt(int index) {
        return (ConnectionRecord) getSdkSandboxConnectionInternalAt(index);
    }

    void addBoundClientUid(int clientUid, String clientPackageName, long bindFlags) {
        mBoundClientUids.add(clientUid);
        mApp.getWindowProcessController()
                .addBoundClientUid(clientUid, clientPackageName, bindFlags);
    }

    void updateBoundClientUids() {
        clearBoundClientUids();
        if (numberOfRunningServices() == 0) {
            return;
        }
        // grab a set of clientUids of all mConnections of all services
        final ArraySet<Integer> boundClientUids = new ArraySet<>();
        final int serviceCount = numberOfRunningServices();
        WindowProcessController controller = mApp.getWindowProcessController();
        for (int j = 0; j < serviceCount; j++) {
            final ArrayMap<IBinder, ArrayList<ConnectionRecord>> conns =
                    getRunningServiceAt(j).getConnections();
            final int size = conns.size();
            for (int conni = 0; conni < size; conni++) {
                ArrayList<ConnectionRecord> c = conns.valueAt(conni);
                for (int i = 0; i < c.size(); i++) {
                    ConnectionRecord cr = c.get(i);
                    boundClientUids.add(cr.clientUid);
                    controller.addBoundClientUid(cr.clientUid, cr.clientPackageName, cr.getFlags());
                }
            }
        }
        mBoundClientUids = boundClientUids;
    }

    void addBoundClientUidsOfNewService(ServiceRecord sr) {
        if (sr == null) {
            return;
        }
        ArrayMap<IBinder, ArrayList<ConnectionRecord>> conns = sr.getConnections();
        for (int conni = conns.size() - 1; conni >= 0; conni--) {
            ArrayList<ConnectionRecord> c = conns.valueAt(conni);
            for (int i = 0; i < c.size(); i++) {
                ConnectionRecord cr = c.get(i);
                mBoundClientUids.add(cr.clientUid);
                mApp.getWindowProcessController()
                        .addBoundClientUid(cr.clientUid, cr.clientPackageName, cr.getFlags());

            }
        }
    }

    void clearBoundClientUids() {
        mBoundClientUids.clear();
        mApp.getWindowProcessController().clearBoundClientUids();
    }

    @GuardedBy("mService")
    void updateHostingComonentTypeForBindingsLocked() {
        boolean hasBoundClient = false;
        for (int i = numberOfRunningServices() - 1; i >= 0; i--) {
            final ServiceRecord sr = getRunningServiceAt(i);
            if (sr != null && !sr.getConnections().isEmpty()) {
                hasBoundClient = true;
                break;
            }
        }
        if (hasBoundClient) {
            mApp.mProfile.addHostingComponentType(HOSTING_COMPONENT_TYPE_BOUND_SERVICE);
        } else {
            mApp.mProfile.clearHostingComponentType(HOSTING_COMPONENT_TYPE_BOUND_SERVICE);
        }
    }

    @GuardedBy("mService")
    boolean incServiceCrashCountLocked(long now) {
        final boolean procIsBoundForeground = mApp.getProcState()
                == ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
        boolean tryAgain = false;
        // Bump up the crash count of any services currently running in the proc.
        for (int i = numberOfRunningServices() - 1; i >= 0; i--) {
            // Any services running in the application need to be placed
            // back in the pending list.
            ServiceRecord sr = getRunningServiceAt(i);
            // If the service was restarted a while ago, then reset crash count, else increment it.
            if (now > sr.restartTime + ActivityManagerConstants.MIN_CRASH_INTERVAL) {
                sr.crashCount = 1;
            } else {
                sr.crashCount++;
            }
            // Allow restarting for started or bound foreground services that are crashing.
            // This includes wallpapers.
            if (sr.crashCount < mService.mConstants.BOUND_SERVICE_MAX_CRASH_RETRY
                    && (sr.isForeground() || procIsBoundForeground)) {
                tryAgain = true;
            }
        }
        return tryAgain;
    }

    @GuardedBy("mService")
    void noteScheduleServiceTimeoutPending(boolean pending) {
        mScheduleServiceTimeoutPending = pending;
    }

    @GuardedBy("mService")
    boolean isScheduleServiceTimeoutPending() {
        return mScheduleServiceTimeoutPending;
    }

    void onProcessUnfrozen() {
        synchronized (mService) {
            scheduleServiceTimeoutIfNeededLocked();
            mService.mServices.onProcessUnfrozenLocked(this);
        }
    }

    void onProcessFrozenCancelled() {
        synchronized (mService) {
            scheduleServiceTimeoutIfNeededLocked();
        }
    }

    @GuardedBy("mService")
    private void scheduleServiceTimeoutIfNeededLocked() {
        if (mScheduleServiceTimeoutPending && hasExecutingServices()) {
            mService.mServices.scheduleServiceTimeoutLocked(mApp);
            // We'll need to reset the executingStart since the app was frozen.
            final long now = SystemClock.uptimeMillis();
            for (int i = 0, size = numberOfExecutingServices(); i < size; i++) {
                getExecutingServiceAt(i).executingStart = now;
            }
        }
    }

    void dump(PrintWriter pw, String prefix, long nowUptime) {
        if (hasForegroundServices() || mApp.getForcingToImportant() != null) {
            pw.print(prefix);
            pw.print("mHasForegroundServices="); pw.print(hasForegroundServices());
            pw.print(" forcingToImportant="); pw.println(mApp.getForcingToImportant());
        }
        if (getHasTopStartedAlmostPerceptibleServices()
                || getLastTopStartedAlmostPerceptibleBindRequestUptimeMs() > 0) {
            pw.print(prefix); pw.print("mHasTopStartedAlmostPerceptibleServices=");
            pw.print(getHasTopStartedAlmostPerceptibleServices());
            pw.print(" mLastTopStartedAlmostPerceptibleBindRequestUptimeMs=");
            pw.println(getLastTopStartedAlmostPerceptibleBindRequestUptimeMs());
        }
        if (hasClientActivities() || hasBindAboveClient() || isTreatLikeActivity()) {
            pw.print(prefix); pw.print("hasClientActivities="); pw.print(hasClientActivities());
            pw.print(" hasAboveClient="); pw.print(hasBindAboveClient());
            pw.print(" treatLikeActivity="); pw.println(isTreatLikeActivity());
        }
        if (getConnectionGroup() != 0) {
            pw.print(prefix); pw.print("connectionGroup="); pw.print(getConnectionGroup());
            pw.print(" Importance="); pw.print(getConnectionImportance());
        }
        if (mAllowlistManager) {
            pw.print(prefix); pw.print("allowlistManager="); pw.println(mAllowlistManager);
        }
        if (numberOfRunningServices() > 0) {
            pw.print(prefix); pw.println("Services:");
            for (int i = 0, size = numberOfRunningServices(); i < size; i++) {
                pw.print(prefix); pw.print("  - "); pw.println(getRunningServiceAt(i));
            }
        }
        if (hasExecutingServices()) {
            pw.print(prefix); pw.print("Executing Services (fg=");
            pw.print(isExecServicesFg()); pw.println(")");
            for (int i = 0, size = numberOfExecutingServices(); i < size; i++) {
                pw.print(prefix); pw.print("  - "); pw.println(getExecutingServiceAt(i));
            }
        }
        if (numberOfConnections() > 0) {
            pw.print(prefix); pw.println("mConnections:");
            for (int i = 0, size = numberOfConnections(); i < size; i++) {
                pw.print(prefix); pw.print("  - "); pw.println(getConnectionAt(i));
            }
        }
        pw.print(prefix);
        pw.print("scheduleServiceTimeoutPending=");
        pw.println(mScheduleServiceTimeoutPending);
    }
}
