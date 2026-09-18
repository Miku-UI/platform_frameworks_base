/*
 * Copyright (C) 2006 The Android Open Source Project
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

import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT;
import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT_UI;

import static com.android.server.am.ActivityManagerDebugConfig.TAG_AM;

import android.app.ContentProviderHolder;
import android.app.IApplicationThread;
import android.content.ComponentName;
import android.content.IContentProvider;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.app.procstats.AssociationState;
import com.android.internal.app.procstats.ProcessStats;
import com.android.server.am.psc.ContentProviderRecordInternal;

import java.io.PrintWriter;
import java.util.ArrayList;

final class ContentProviderRecord extends ContentProviderRecordInternal
        implements ComponentName.WithComponentName {
    // Maximum attempts to bring up the content provider before giving up.
    static final int MAX_RETRY_COUNT = 3;

    final ActivityManagerService mService;
    final ProviderInfo mProviderInfo;
    final int mUid;
    final ApplicationInfo mAppInfo;
    final boolean mSingleton;
    IContentProvider mProvider;
    boolean mNoReleaseNeeded;
    // All attached clients
    final ArrayList<ContentProviderConnection> mConnections = new ArrayList<>();
    //final HashSet<ProcessRecord> clients = new HashSet<ProcessRecord>();
    // Handles for non-framework processes supported by this provider
    ArrayMap<IBinder, ExternalProcessHandle> mExternalProcessTokenToHandle;
    // Count for external process for which we have no handles.
    int mExternalProcessNoHandleCount;
    int mRestartCount; // number of times we tried before bringing up it successfully.
    ProcessRecord mProc; // if non-null, hosting process.
    ProcessRecord mLaunchingApp; // if non-null, waiting for this app to be launched.
    String mStringName;
    String mShortStringName;

    ContentProviderRecord(ActivityManagerService service, ProviderInfo providerInfo,
            ApplicationInfo appInfo, ComponentName componentName, boolean singleton) {
        super(componentName);
        mService = service;
        mProviderInfo = providerInfo;
        mUid = providerInfo.getUid();
        mAppInfo = appInfo;
        mSingleton = singleton;
        mNoReleaseNeeded = (mUid == 0 || mUid == Process.SYSTEM_UID)
                && (componentName == null || !"com.android.settings".equals(
                componentName.getPackageName()));
    }

    public ContentProviderRecord(ContentProviderRecord cpr) {
        super(cpr.name);

        mService = cpr.mService;
        mProviderInfo = cpr.mProviderInfo;
        mUid = cpr.mUid;
        mAppInfo = cpr.mAppInfo;
        mSingleton = cpr.mSingleton;
        mNoReleaseNeeded = cpr.mNoReleaseNeeded;
    }

    public ContentProviderHolder newHolder(ContentProviderConnection conn, boolean local) {
        ContentProviderHolder holder = new ContentProviderHolder(mProviderInfo);
        holder.provider = mProvider;
        holder.noReleaseNeeded = mNoReleaseNeeded;
        holder.connection = conn;
        holder.mLocal = local;
        if (conn == null || conn.provider == null || conn.provider.mProc == null) {
            return holder;
        }
        final int procState = conn.provider.mProc.getProcState();
        if (procState == PROCESS_STATE_PERSISTENT || procState == PROCESS_STATE_PERSISTENT_UI) {
            holder.noReleaseNeededIfUnstable = true;
        }
        return holder;
    }

    public void setProcess(ProcessRecord proc) {
        this.mProc = proc;
        if (ActivityManagerService.TRACK_PROCSTATS_ASSOCIATIONS) {
            for (int iconn = mConnections.size() - 1; iconn >= 0; iconn--) {
                final ContentProviderConnection conn = mConnections.get(iconn);
                if (proc != null) {
                    conn.startAssociationIfNeeded();
                } else {
                    conn.stopAssociation();
                }
            }
            if (mExternalProcessTokenToHandle != null) {
                for (int iext = mExternalProcessTokenToHandle.size() - 1; iext >= 0; iext--) {
                    final ExternalProcessHandle handle = mExternalProcessTokenToHandle.valueAt(
                            iext);
                    if (proc != null) {
                        handle.startAssociationIfNeeded(this);
                    } else {
                        handle.stopAssociation();
                    }
                }
            }
        }
    }

    public boolean canRunHere(ProcessRecord app) {
        return (mProviderInfo.multiprocess || mProviderInfo.processName.equals(app.processName))
                && mUid == app.uid;
    }

    @GuardedBy("mService")
    public void addExternalProcessHandleLocked(IBinder token, int callingUid, String callingTag) {
        if (token == null) {
            mExternalProcessNoHandleCount++;
            notifyHasExternalProcessHandles();
        } else {
            if (mExternalProcessTokenToHandle == null) {
                mExternalProcessTokenToHandle = new ArrayMap<>();
                notifyHasExternalProcessHandles();
            }
            ExternalProcessHandle handle = mExternalProcessTokenToHandle.get(token);
            if (handle == null) {
                handle = new ExternalProcessHandle(token, callingUid, callingTag);
                mExternalProcessTokenToHandle.put(token, handle);
                handle.startAssociationIfNeeded(this);
            }
            handle.mAcquisitionCount++;
        }
    }

    @GuardedBy("mService")
    public boolean removeExternalProcessHandleLocked(IBinder token) {
        if (hasExternalProcessHandles()) {
            boolean hasHandle = false;
            if (mExternalProcessTokenToHandle != null) {
                ExternalProcessHandle handle = mExternalProcessTokenToHandle.get(token);
                if (handle != null) {
                    hasHandle = true;
                    handle.mAcquisitionCount--;
                    if (handle.mAcquisitionCount == 0) {
                        removeExternalProcessHandleInternalLocked(token);
                        return true;
                    }
                }
            }
            if (!hasHandle) {
                mExternalProcessNoHandleCount--;
                notifyHasExternalProcessHandles();
                return true;
            }
        }
        return false;
    }

    @GuardedBy("mService")
    private void removeExternalProcessHandleInternalLocked(IBinder token) {
        ExternalProcessHandle handle = mExternalProcessTokenToHandle.get(token);
        handle.unlinkFromOwnDeathLocked();
        handle.stopAssociation();
        mExternalProcessTokenToHandle.remove(token);
        if (mExternalProcessTokenToHandle.isEmpty()) {
            mExternalProcessTokenToHandle = null;
            notifyHasExternalProcessHandles();
        }
    }

    /**
     * Notifies the {@link ProcessStateController} that the state of whether this content provider
     * has external process handles has changed.
     * <p>
     * This method is called whenever {@link #mExternalProcessTokenToHandle} or
     * {@link #mExternalProcessNoHandleCount} is modified.
     * </p>
     */
    @GuardedBy("mService")
    private void notifyHasExternalProcessHandles() {
        mService.mProcessStateController.setHasExternalProcessHandles(this,
                hasExternalProcessHandles());
    }

    /**
     * Calculates whether this content provider has any external process handles.
     * The result is pushed to {@link ContentProviderRecordInternal} via
     * {@link #notifyHasExternalProcessHandles()} to serve as the source of truth for consumers
     * inside {@link com.android.server.am.psc} package.
     */
    @GuardedBy("mService")
    public boolean hasExternalProcessHandles() {
        return (mExternalProcessTokenToHandle != null || mExternalProcessNoHandleCount > 0);
    }

    @GuardedBy("mService")
    public boolean hasConnectionOrHandle() {
        return !mConnections.isEmpty() || hasExternalProcessHandles();
    }

    /**
     * Notify all clients that the provider has been published and ready to use,
     * or timed out.
     *
     * @param status true: successfully published; false: timed out
     */
    void onProviderPublishStatusLocked(boolean status) {
        final int numOfConns = mConnections.size();
        for (int i = 0; i < numOfConns; i++) {
            final ContentProviderConnection conn = mConnections.get(i);
            if (conn.waiting && conn.client != null) {
                final ProcessRecord client = conn.client;
                if (!status) {
                    if (mLaunchingApp == null) {
                        Slog.w(TAG_AM, "Unable to launch app "

                                + mAppInfo.packageName + "/"
                                + mUid + " for provider "
                                + mProviderInfo.authority + ": launching app became null");
                        EventLogTags.writeAmProviderLostProcess(
                                UserHandle.getUserId(mUid),
                                mAppInfo.packageName,
                                mUid, mProviderInfo.authority);
                    } else {
                        Slog.wtf(TAG_AM, "Timeout waiting for provider "
                                + mAppInfo.packageName + "/"
                                + mUid + " for provider "
                                + mProviderInfo.authority
                                + " caller=" + client);
                    }
                }
                final IApplicationThread thread = client.getThread();
                if (thread != null) {
                    try {
                        thread.notifyContentProviderPublishStatus(
                                newHolder(status ? conn : null, false),
                                mProviderInfo.authority, conn.mExpectedUserId, status);
                    } catch (RemoteException e) {
                    }
                }
            }
            conn.waiting = false;
        }
    }

    @Override
    public ProcessRecord getHostProcess() {
        return mProc;
    }

    @Override
    public int numberOfConnections() {
        return mConnections.size();
    }

    @Override
    public ContentProviderConnection getConnectionsAt(int index) {
        return mConnections.get(index);
    }

    void dump(PrintWriter pw, String prefix, boolean full) {
        if (full) {
            pw.print(prefix);
            pw.print("package=");
            pw.print(mProviderInfo.applicationInfo.packageName);
            pw.print(" process=");
            pw.println(mProviderInfo.processName);
        }
        pw.print(prefix);
        pw.print("proc=");
        pw.println(mProc);
        if (mLaunchingApp != null) {
            pw.print(prefix);
            pw.print("launchingApp=");
            pw.println(mLaunchingApp);
        }
        if (full) {
            pw.print(prefix);
            pw.print("uid=");
            pw.print(mUid);
            pw.print(" provider=");
            pw.println(mProvider);
        }
        if (mSingleton) {
            pw.print(prefix);
            pw.print("singleton=");
            pw.println(mSingleton);
        }
        pw.print(prefix);
        pw.print("authority=");
        pw.println(mProviderInfo.authority);
        if (full) {
            if (mProviderInfo.isSyncable || mProviderInfo.multiprocess
                    || mProviderInfo.initOrder != 0) {
                pw.print(prefix);
                pw.print("isSyncable=");
                pw.print(mProviderInfo.isSyncable);
                pw.print(" multiprocess=");
                pw.print(mProviderInfo.multiprocess);
                pw.print(" initOrder=");
                pw.println(mProviderInfo.initOrder);
            }
        }
        if (full) {
            if (hasExternalProcessHandles()) {
                pw.print(prefix);
                pw.print("externals:");
                if (mExternalProcessTokenToHandle != null) {
                    pw.print(" w/token=");
                    pw.print(mExternalProcessTokenToHandle.size());
                }
                if (mExternalProcessNoHandleCount > 0) {
                    pw.print(" notoken=");
                    pw.print(mExternalProcessNoHandleCount);
                }
                pw.println();
            }
        } else {
            if (!mConnections.isEmpty() || mExternalProcessNoHandleCount > 0) {
                pw.print(prefix);
                pw.print(mConnections.size());
                pw.print(" connections, ");
                pw.print(mExternalProcessNoHandleCount);
                pw.println(" external handles");
            }
        }
        if (!mConnections.isEmpty()) {
            if (full) {
                pw.print(prefix);
                pw.println("Connections:");
            }
            for (int i = 0; i < mConnections.size(); i++) {
                ContentProviderConnection conn = mConnections.get(i);
                pw.print(prefix);
                pw.print("  -> ");
                pw.println(conn.toClientString());
                if (conn.provider != this) {
                    pw.print(prefix);
                    pw.print("    *** WRONG PROVIDER: ");
                    pw.println(conn.provider);
                }
            }
        }
    }

    @Override
    public String toString() {
        if (mStringName != null) {
            return mStringName;
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append("ContentProviderRecord{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(" u");
        sb.append(UserHandle.getUserId(mUid));
        sb.append(' ');
        sb.append(name.flattenToShortString());
        sb.append('}');
        return mStringName = sb.toString();
    }

    public String toShortString() {
        if (mShortStringName != null) {
            return mShortStringName;
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append('/');
        sb.append(name.flattenToShortString());
        return mShortStringName = sb.toString();
    }

    // This class represents a handle from an external process to a provider.
    private class ExternalProcessHandle implements DeathRecipient {
        private static final String LOG_TAG = "ExternalProcessHanldle";

        final IBinder mToken;
        final int mOwningUid;
        final String mOwningProcessName;
        int mAcquisitionCount;
        AssociationState.SourceState mAssociation;
        private Object mProcStatsLock;  // Internal lock for accessing AssociationState

        public ExternalProcessHandle(IBinder token, int owningUid, String owningProcessName) {
            mToken = token;
            mOwningUid = owningUid;
            mOwningProcessName = owningProcessName;
            try {
                token.linkToDeath(this, 0);
            } catch (RemoteException re) {
                Slog.e(LOG_TAG, "Couldn't register for death for token: " + mToken, re);
            }
        }

        public void unlinkFromOwnDeathLocked() {
            mToken.unlinkToDeath(this, 0);
        }

        public void startAssociationIfNeeded(ContentProviderRecord provider) {
            // If we don't already have an active association, create one...  but only if this
            // is an association between two different processes.
            if (ActivityManagerService.TRACK_PROCSTATS_ASSOCIATIONS
                    && mAssociation == null && provider.mProc != null
                    && (provider.mUid != mOwningUid
                            || !provider.mAppInfo.processName.equals(mOwningProcessName))) {
                ProcessStats.ProcessStateHolder holder =
                        provider.mProc.getPkgList().get(provider.name.getPackageName());
                if (holder == null) {
                    Slog.wtf(TAG_AM, "No package in referenced provider "
                            + provider.name.toShortString() + ": proc=" + provider.mProc);
                } else if (holder.pkg == null) {
                    Slog.wtf(TAG_AM, "Inactive holder in referenced provider "
                            + provider.name.toShortString() + ": proc=" + provider.mProc);
                } else {
                    mProcStatsLock = provider.mProc.mService.mProcessStats.mLock;
                    synchronized (mProcStatsLock) {
                        mAssociation = holder.pkg.getAssociationStateLocked(holder.state,
                                provider.name.getClassName()).startSource(mOwningUid,
                                mOwningProcessName, null);
                    }
                }
            }
        }

        public void stopAssociation() {
            if (mAssociation != null) {
                synchronized (mProcStatsLock) {
                    mAssociation.stop();
                }
                mAssociation = null;
            }
        }

        @Override
        public void binderDied() {
            synchronized (mService) {
                if (hasExternalProcessHandles() &&
                        mExternalProcessTokenToHandle.get(mToken) != null) {
                    removeExternalProcessHandleInternalLocked(mToken);
                }
            }
        }
    }

    public ComponentName getComponentName() {
        return name;
    }
}
