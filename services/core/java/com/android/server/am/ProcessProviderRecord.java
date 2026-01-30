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

import android.util.ArrayMap;
import android.util.TimeUtils;

import com.android.internal.annotations.GuardedBy;
import com.android.server.am.psc.ProcessProviderRecordInternal;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * The state info of all content providers in the process.
 */
final class ProcessProviderRecord extends ProcessProviderRecordInternal {
    final ProcessRecord mApp;
    private final ActivityManagerService mService;

    /**
     * class (String) -> ContentProviderRecord.
     */
    private final ArrayMap<String, ContentProviderRecord> mPubProviders = new ArrayMap<>();

    /**
     * All ContentProviderRecord process is using.
     */
    private final ArrayList<ContentProviderConnection> mConProviders = new ArrayList<>();

    boolean hasProvider(String name) {
        return mPubProviders.containsKey(name);
    }

    ContentProviderRecord getProvider(String name) {
        return mPubProviders.get(name);
    }

    @Override
    public int numberOfProviders() {
        return mPubProviders.size();
    }

    @Override
    public ContentProviderRecord getProviderAt(int index) {
        return mPubProviders.valueAt(index);
    }

    void installProvider(String name, ContentProviderRecord provider) {
        mPubProviders.put(name, provider);
    }

    void removeProvider(String name) {
        mPubProviders.remove(name);
    }

    void ensureProviderCapacity(int capacity) {
        mPubProviders.ensureCapacity(capacity);
    }

    @Override
    public int numberOfProviderConnections() {
        return mConProviders.size();
    }

    @Override
    public ContentProviderConnection getProviderConnectionAt(int index) {
        return mConProviders.get(index);
    }

    void addProviderConnection(ContentProviderConnection connection) {
        mConProviders.add(connection);
    }

    boolean removeProviderConnection(ContentProviderConnection connection) {
        return mConProviders.remove(connection);
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
        for (int i = mPubProviders.size() - 1; i >= 0; i--) {
            final ContentProviderRecord cpr = mPubProviders.valueAt(i);
            if (cpr.proc != mApp) {
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

            cpr.provider = null;
            cpr.setProcess(null);
        }
        mPubProviders.clear();

        // Take care of any launching providers waiting for this process.
        if (mService.mCpHelper.cleanupAppInLaunchingProvidersLocked(mApp, false)) {
            mService.mProcessList.noteProcessDiedLocked(mApp);
            restart = true;
        }

        // Unregister from connected content providers.
        if (!mConProviders.isEmpty()) {
            for (int i = mConProviders.size() - 1; i >= 0; i--) {
                final ContentProviderConnection conn = mConProviders.get(i);
                conn.provider.connections.remove(conn);
                mService.stopAssociationLocked(mApp.uid, mApp.processName, conn.provider.uid,
                        conn.provider.appInfo.longVersionCode, conn.provider.name,
                        conn.provider.info.processName);
            }
            mConProviders.clear();
        }

        return restart;
    }

    void dump(PrintWriter pw, String prefix, long nowUptime) {
        if (getLastProviderTime() > 0) {
            pw.print(prefix); pw.print("lastProviderTime=");
            TimeUtils.formatDuration(getLastProviderTime(), nowUptime, pw);
            pw.println();
        }
        if (mPubProviders.size() > 0) {
            pw.print(prefix); pw.println("Published Providers:");
            for (int i = 0, size = mPubProviders.size(); i < size; i++) {
                pw.print(prefix); pw.print("  - "); pw.println(mPubProviders.keyAt(i));
                pw.print(prefix); pw.print("    -> "); pw.println(mPubProviders.valueAt(i));
            }
        }
        if (mConProviders.size() > 0) {
            pw.print(prefix); pw.println("Connected Providers:");
            for (int i = 0, size = mConProviders.size(); i < size; i++) {
                pw.print(prefix); pw.print("  - ");
                pw.println(mConProviders.get(i).toShortString());
            }
        }
    }
}
