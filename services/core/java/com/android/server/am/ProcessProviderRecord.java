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

import android.util.TimeUtils;

import com.android.internal.annotations.GuardedBy;
import com.android.server.am.psc.ProcessProviderRecordInternal;

import java.io.PrintWriter;

/**
 * The state info of all content providers in the process.
 */
final class ProcessProviderRecord extends ProcessProviderRecordInternal {
    final ProcessRecord mApp;
    private final ActivityManagerService mService;

    ContentProviderRecord getProvider(String name) {
        return (ContentProviderRecord) getProviderInternal(name);
    }

    ContentProviderRecord getProviderAt(int index) {
        return (ContentProviderRecord) getProviderInternalAt(index);
    }

    ContentProviderConnection getProviderConnectionAt(int index) {
        return (ContentProviderConnection) getProviderConnectionInternalAt(index);
    }

    ProcessProviderRecord(ProcessRecord app) {
        mApp = app;
        mService = app.mService;
    }

    /**
     * @return Should the process restart or not.
     */
    @GuardedBy("mService")
    boolean onCleanupApplicationRecordLocked(boolean allowRestart) {
        boolean restart = false;
        // Remove published content providers.
        for (int i = numberOfProviders() - 1; i >= 0; i--) {
            final ContentProviderRecord cpr = getProviderAt(i);
            if (cpr.mProc != mApp) {
                // If the hosting process record isn't really us, bail out
                continue;
            }
            final boolean alwaysRemove = mApp.mErrorState.isBad() || !allowRestart;
            final boolean inLaunching = mService.mCpHelper
                    .removeDyingProviderLocked(mApp, cpr, alwaysRemove);
            if (!alwaysRemove && inLaunching && cpr.hasConnectionOrHandle()) {
                // We left the provider in the launching list, need to
                // restart it.
                restart = true;
            }

            cpr.mProvider = null;
            cpr.setProcess(null);
        }
        clearProvider();

        // Take care of any launching providers waiting for this process.
        if (mService.mCpHelper.cleanupAppInLaunchingProvidersLocked(mApp, false)) {
            mService.mProcessList.noteProcessDiedLocked(mApp);
            restart = true;
        }

        // Unregister from connected content providers.
        if (numberOfProviderConnections() > 0) {
            for (int i = numberOfProviderConnections() - 1; i >= 0; i--) {
                final ContentProviderConnection conn = getProviderConnectionAt(i);
                conn.provider.mConnections.remove(conn);
                mService.stopAssociationLocked(mApp.uid, mApp.processName, conn.provider.mUid,
                        conn.provider.mAppInfo.longVersionCode, conn.provider.name,
                        conn.provider.mProviderInfo.processName);
            }
            clearProviderConnection();
        }

        return restart;
    }

    void dump(PrintWriter pw, String prefix, long nowUptime) {
        if (getLastProviderTime() > 0) {
            pw.print(prefix); pw.print("lastProviderTime=");
            TimeUtils.formatDuration(getLastProviderTime(), nowUptime, pw);
            pw.println();
        }
        if (numberOfProviders() > 0) {
            pw.print(prefix); pw.println("Published Providers:");
            for (int i = 0, size = numberOfProviders(); i < size; i++) {
                pw.print(prefix); pw.print("  - "); pw.println(getProviderNameAt(i));
                pw.print(prefix); pw.print("    -> "); pw.println(getProviderAt(i));
            }
        }
        if (numberOfProviderConnections() > 0) {
            pw.print(prefix); pw.println("Connected Providers:");
            for (int i = 0, size = numberOfProviderConnections(); i < size; i++) {
                pw.print(prefix); pw.print("  - ");
                pw.println(getProviderConnectionAt(i).toShortString());
            }
        }
    }
}
