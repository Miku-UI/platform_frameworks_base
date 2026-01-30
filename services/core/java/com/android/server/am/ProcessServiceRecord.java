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
import static android.app.ProcessMemoryState.HOSTING_COMPONENT_TYPE_FOREGROUND_SERVICE;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.internal.annotations.GuardedBy;
import com.android.server.am.psc.ProcessServiceRecordInternal;
import com.android.server.am.psc.ServiceRecordInternal;
import com.android.server.wm.WindowProcessController;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * The state info of all services in the process.
 */
final class ProcessServiceRecord extends ProcessServiceRecordInternal {
    /**
     * Are there any client services with activities?
     */
    private boolean mHasClientActivities;

    /**
     * Running any services that are foreground?
     */
    private boolean mHasForegroundServices;

    /**
     * Last reported state of whether it's running any services that are foreground.
     */
    private boolean mRepHasForegroundServices;

    /**
     * Running any services that are almost perceptible (started with
     * {@link Context#BIND_ALMOST_PERCEPTIBLE} while the app was on TOP)?
     */
    private boolean mHasTopStartedAlmostPerceptibleServices;

    /**
     * The latest value of {@link ServiceRecord#getLastTopAlmostPerceptibleBindRequestUptimeMs()}
     * among the currently running services.
     */
    private long mLastTopStartedAlmostPerceptibleBindRequestUptimeMs;


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
     * App is allowed to manage allowlists such as temporary Power Save mode allowlist.
     */
    boolean mAllowlistManager;

    /**
     * All ServiceRecord running in this process.
     */
    final ArraySet<ServiceRecord> mServices = new ArraySet<>();

    /**
     * Services that are currently executing code (need to remain foreground).
     */
    private final ArraySet<ServiceRecord> mExecutingServices = new ArraySet<>();

    /**
     * All outgoing connections from this process.
     */
    private final ArraySet<ConnectionRecord> mConnections = new ArraySet<>();

    /**
     * All ConnectionRecord this process holds indirectly to SDK sandbox processes.
     */
    private @Nullable ArraySet<ConnectionRecord> mSdkSandboxConnections;

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
        mApp = app;
        mService = app.mService;
    }

    void setHasClientActivities(boolean hasClientActivities) {
        mHasClientActivities = hasClientActivities;
        mApp.getWindowProcessController().setHasClientActivities(hasClientActivities);
    }

    boolean hasClientActivities() {
        return mHasClientActivities;
    }

    void setHasForegroundServices(boolean hasForegroundServices, int fgServiceTypes,
            boolean hasTypeNoneFgs) {
        // hasForegroundServices should be the same as "either it has any FGS types, or none types".
        // We still take this as a parameter because it's used in the callsite...
        if (ActivityManagerDebugConfig.DEBUG_SERVICE
                && hasForegroundServices != ((fgServiceTypes != 0) || hasTypeNoneFgs)) {
            throw new IllegalStateException("hasForegroundServices mismatch");
        }

        mHasForegroundServices = hasForegroundServices;
        mFgServiceTypes = fgServiceTypes;
        mHasTypeNoneFgs = hasTypeNoneFgs;
        mApp.getWindowProcessController().setHasForegroundServices(hasForegroundServices);
        if (hasForegroundServices) {
            mApp.mProfile.addHostingComponentType(HOSTING_COMPONENT_TYPE_FOREGROUND_SERVICE);
        } else {
            mApp.mProfile.clearHostingComponentType(HOSTING_COMPONENT_TYPE_FOREGROUND_SERVICE);
        }
    }

    @Override
    public boolean hasForegroundServices() {
        return mHasForegroundServices;
    }

    void setHasReportedForegroundServices(boolean hasForegroundServices) {
        mRepHasForegroundServices = hasForegroundServices;
    }

    boolean hasReportedForegroundServices() {
        return mRepHasForegroundServices;
    }

    /**
     * Returns the FGS types, but it doesn't tell if the types include "NONE" or not, use
     * {@link #hasForegroundServices()}
     */
    int getForegroundServiceTypes() {
        return mHasForegroundServices ? mFgServiceTypes : 0;
    }

    boolean areForegroundServiceTypesSame(@ServiceInfo.ForegroundServiceType int types,
            boolean hasTypeNoneFgs) {
        return ((getForegroundServiceTypes() & types) == types)
                && (mHasTypeNoneFgs == hasTypeNoneFgs);
    }

    /**
     * @return true if the fgs types includes any of the given types.
     * (wouldn't work for TYPE_NONE, which is 0)
     */
    boolean containsAnyForegroundServiceTypes(@ServiceInfo.ForegroundServiceType int types) {
        return (getForegroundServiceTypes() & types) != 0;
    }

    /**
     * @return true if the process has any FGS that are _not_ a "short" FGS.
     */
    boolean hasNonShortForegroundServices() {
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
     * @return if this process:
     * - has at least one short-FGS
     * - has no other types of FGS
     * - and all the short-FGSes are procstate-timed out.
     */
    boolean areAllShortForegroundServicesProcstateTimedOut(long nowUptime) {
        if (!mHasForegroundServices) { // Process has no FGS?
            return false;
        }
        if (hasNonShortForegroundServices()) {  // Any non-short FGS running?
            return false;
        }
        // Now we need to look at all short-FGS within the process and see if all of them are
        // procstate-timed-out or not.
        return !hasUndemotedShortForegroundService(nowUptime);
    }

    boolean hasUndemotedShortForegroundService(long nowUptime) {
        for (int i = mServices.size() - 1; i >= 0; i--) {
            final ServiceRecord sr = mServices.valueAt(i);
            if (!sr.isShortFgs() || !sr.hasShortFgsInfo()) {
                continue;
            }
            if (sr.getShortFgsInfo().getProcStateDemoteTime() >= nowUptime) {
                // This short fgs has not timed out yet.
                return true;
            }
        }
        return false;
    }

    int getNumForegroundServices() {
        int count = 0;
        for (int i = 0, serviceCount = mServices.size(); i < serviceCount; i++) {
            if (mServices.valueAt(i).isForeground()) {
                count++;
            }
        }
        return count;
    }

    void updateHasTopStartedAlmostPerceptibleServices() {
        mHasTopStartedAlmostPerceptibleServices = false;
        mLastTopStartedAlmostPerceptibleBindRequestUptimeMs = 0;
        for (int s = mServices.size() - 1; s >= 0; --s) {
            final ServiceRecordInternal sr = mServices.valueAt(s);
            mLastTopStartedAlmostPerceptibleBindRequestUptimeMs = Math.max(
                    mLastTopStartedAlmostPerceptibleBindRequestUptimeMs,
                    sr.getLastTopAlmostPerceptibleBindRequestUptimeMs());
            if (!mHasTopStartedAlmostPerceptibleServices && isAlmostPerceptible(sr)) {
                mHasTopStartedAlmostPerceptibleServices = true;
            }
        }
    }

    boolean hasTopStartedAlmostPerceptibleServices() {
        return mHasTopStartedAlmostPerceptibleServices
                || (mLastTopStartedAlmostPerceptibleBindRequestUptimeMs > 0
                && SystemClock.uptimeMillis() - mLastTopStartedAlmostPerceptibleBindRequestUptimeMs
                < mService.mConstants.mServiceBindAlmostPerceptibleTimeoutMs);
    }

    void updateHasAboveClientLocked() {
        setHasAboveClient(false);
        for (int i = mConnections.size() - 1; i >= 0; i--) {
            ConnectionRecord cr = mConnections.valueAt(i);

            final boolean isSameProcess = cr.binding.service.app != null
                    && cr.binding.service.app.mServices == this;
            if (!isSameProcess && cr.hasFlag(Context.BIND_ABOVE_CLIENT)) {
                setHasAboveClient(true);
                break;
            }
        }
    }

    /**
     * Records a service as running in the process. Note that this method does not actually start
     * the service, but records the service as started for bookkeeping.
     *
     * @return true if the service was added, false otherwise.
     */
    boolean startService(ServiceRecord record) {
        if (record == null) {
            return false;
        }
        boolean added = mServices.add(record);
        if (added && record.serviceInfo != null) {
            mApp.getWindowProcessController().onServiceStarted(record.serviceInfo);
            updateHostingComonentTypeForBindingsLocked();
        }
        if (record.getLastTopAlmostPerceptibleBindRequestUptimeMs() > 0) {
            mLastTopStartedAlmostPerceptibleBindRequestUptimeMs = Math.max(
                    mLastTopStartedAlmostPerceptibleBindRequestUptimeMs,
                    record.getLastTopAlmostPerceptibleBindRequestUptimeMs());
            if (!mHasTopStartedAlmostPerceptibleServices) {
                mHasTopStartedAlmostPerceptibleServices = isAlmostPerceptible(record);
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
    boolean stopService(ServiceRecord record) {
        final boolean removed = mServices.remove(record);
        if (record.getLastTopAlmostPerceptibleBindRequestUptimeMs() > 0) {
            updateHasTopStartedAlmostPerceptibleServices();
        }
        if (removed) {
            updateHostingComonentTypeForBindingsLocked();
        }
        return removed;
    }

    /**
     * The same as calling {@link #stopService(ServiceRecord)} on all current running services.
     */
    void stopAllServices() {
        mServices.clear();
        updateHasTopStartedAlmostPerceptibleServices();
    }

    /**
     * Returns the number of services added with {@link #startService(ServiceRecord)} and not yet
     * removed by a call to {@link #stopService(ServiceRecord)} or {@link #stopAllServices()}.
     *
     * @see #startService(ServiceRecord)
     * @see #stopService(ServiceRecord)
     */
    @Override
    public int numberOfRunningServices() {
        return mServices.size();
    }

    /**
     * Returns the service at the specified {@code index}.
     *
     * @see #numberOfRunningServices()
     */
    @Override
    public ServiceRecord getRunningServiceAt(int index) {
        return mServices.valueAt(index);
    }

    void startExecutingService(ServiceRecord service) {
        mExecutingServices.add(service);
    }

    void stopExecutingService(ServiceRecord service) {
        mExecutingServices.remove(service);
    }

    void stopAllExecutingServices() {
        mExecutingServices.clear();
    }

    ServiceRecord getExecutingServiceAt(int index) {
        return mExecutingServices.valueAt(index);
    }

    int numberOfExecutingServices() {
        return mExecutingServices.size();
    }

    @Override
    public boolean hasExecutingServices() {
        return !mExecutingServices.isEmpty();
    }

    void addConnection(ConnectionRecord connection) {
        mConnections.add(connection);
        addSdkSandboxConnectionIfNecessary(connection);
    }

    void removeConnection(ConnectionRecord connection) {
        mConnections.remove(connection);
        removeSdkSandboxConnectionIfNecessary(connection);
    }

    void removeAllConnections() {
        for (int i = 0, size = mConnections.size(); i < size; i++) {
            removeSdkSandboxConnectionIfNecessary(mConnections.valueAt(i));
        }
        mConnections.clear();
    }

    @Override
    public ConnectionRecord getConnectionAt(int index) {
        return mConnections.valueAt(index);
    }

    @Override
    public int numberOfConnections() {
        return mConnections.size();
    }

    private void addSdkSandboxConnectionIfNecessary(ConnectionRecord connection) {
        final ProcessRecord attributedClient = connection.binding.attributedClient;
        if (attributedClient != null && connection.binding.service.isSdkSandbox) {
            if (attributedClient.mServices.mSdkSandboxConnections == null) {
                attributedClient.mServices.mSdkSandboxConnections = new ArraySet<>();
            }
            attributedClient.mServices.mSdkSandboxConnections.add(connection);
        }
    }

    private void removeSdkSandboxConnectionIfNecessary(ConnectionRecord connection) {
        final ProcessRecord attributedClient = connection.binding.attributedClient;
        if (attributedClient != null && connection.binding.service.isSdkSandbox) {
            if (attributedClient.mServices.mSdkSandboxConnections != null) {
                attributedClient.mServices.mSdkSandboxConnections.remove(connection);
            }
        }
    }

    void removeAllSdkSandboxConnections() {
        if (mSdkSandboxConnections != null) {
            mSdkSandboxConnections.clear();
        }
    }

    @Override
    public ConnectionRecord getSdkSandboxConnectionAt(int index) {
        return mSdkSandboxConnections != null ? mSdkSandboxConnections.valueAt(index) : null;
    }

    @Override
    public int numberOfSdkSandboxConnections() {
        return mSdkSandboxConnections != null ? mSdkSandboxConnections.size() : 0;
    }

    void addBoundClientUid(int clientUid, String clientPackageName, long bindFlags) {
        mBoundClientUids.add(clientUid);
        mApp.getWindowProcessController()
                .addBoundClientUid(clientUid, clientPackageName, bindFlags);
    }

    void updateBoundClientUids() {
        clearBoundClientUids();
        if (mServices.isEmpty()) {
            return;
        }
        // grab a set of clientUids of all mConnections of all services
        final ArraySet<Integer> boundClientUids = new ArraySet<>();
        final int serviceCount = mServices.size();
        WindowProcessController controller = mApp.getWindowProcessController();
        for (int j = 0; j < serviceCount; j++) {
            final ArrayMap<IBinder, ArrayList<ConnectionRecord>> conns =
                    mServices.valueAt(j).getConnections();
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
        final boolean procIsBoundForeground = mApp.getCurProcState()
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
    void onCleanupApplicationRecordLocked() {
        setTreatLikeActivity(false);
        setHasAboveClient(false);
        setHasClientActivities(false);
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
        }
    }

    void onProcessFrozenCancelled() {
        synchronized (mService) {
            scheduleServiceTimeoutIfNeededLocked();
        }
    }

    @GuardedBy("mService")
    private void scheduleServiceTimeoutIfNeededLocked() {
        if (mScheduleServiceTimeoutPending && mExecutingServices.size() > 0) {
            mService.mServices.scheduleServiceTimeoutLocked(mApp);
            // We'll need to reset the executingStart since the app was frozen.
            final long now = SystemClock.uptimeMillis();
            for (int i = 0, size = mExecutingServices.size(); i < size; i++) {
                mExecutingServices.valueAt(i).executingStart = now;
            }
        }
    }

    void dump(PrintWriter pw, String prefix, long nowUptime) {
        if (mHasForegroundServices || mApp.getForcingToImportant() != null) {
            pw.print(prefix); pw.print("mHasForegroundServices="); pw.print(mHasForegroundServices);
            pw.print(" forcingToImportant="); pw.println(mApp.getForcingToImportant());
        }
        if (mHasTopStartedAlmostPerceptibleServices
                || mLastTopStartedAlmostPerceptibleBindRequestUptimeMs > 0) {
            pw.print(prefix); pw.print("mHasTopStartedAlmostPerceptibleServices=");
            pw.print(mHasTopStartedAlmostPerceptibleServices);
            pw.print(" mLastTopStartedAlmostPerceptibleBindRequestUptimeMs=");
            pw.println(mLastTopStartedAlmostPerceptibleBindRequestUptimeMs);
        }
        if (mHasClientActivities || isHasAboveClient() || isTreatLikeActivity()) {
            pw.print(prefix); pw.print("hasClientActivities="); pw.print(mHasClientActivities);
            pw.print(" hasAboveClient="); pw.print(isHasAboveClient());
            pw.print(" treatLikeActivity="); pw.println(isTreatLikeActivity());
        }
        if (getConnectionGroup() != 0) {
            pw.print(prefix); pw.print("connectionGroup="); pw.print(getConnectionGroup());
            pw.print(" Importance="); pw.print(getConnectionImportance());
        }
        if (mAllowlistManager) {
            pw.print(prefix); pw.print("allowlistManager="); pw.println(mAllowlistManager);
        }
        if (mServices.size() > 0) {
            pw.print(prefix); pw.println("Services:");
            for (int i = 0, size = mServices.size(); i < size; i++) {
                pw.print(prefix); pw.print("  - "); pw.println(mServices.valueAt(i));
            }
        }
        if (mExecutingServices.size() > 0) {
            pw.print(prefix); pw.print("Executing Services (fg=");
            pw.print(isExecServicesFg()); pw.println(")");
            for (int i = 0, size = mExecutingServices.size(); i < size; i++) {
                pw.print(prefix); pw.print("  - "); pw.println(mExecutingServices.valueAt(i));
            }
        }
        if (mConnections.size() > 0) {
            pw.print(prefix); pw.println("mConnections:");
            for (int i = 0, size = mConnections.size(); i < size; i++) {
                pw.print(prefix); pw.print("  - "); pw.println(mConnections.valueAt(i));
            }
        }
        pw.print(prefix);
        pw.print("scheduleServiceTimeoutPending=");
        pw.println(mScheduleServiceTimeoutPending);
    }
}
