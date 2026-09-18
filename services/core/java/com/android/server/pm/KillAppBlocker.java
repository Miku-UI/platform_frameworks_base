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
package com.android.server.pm;

import static android.app.ActivityManager.PROCESS_STATE_NONEXISTENT;
import static android.app.privatecompute.flags.Flags.enablePccFrameworkSupport;
import static android.content.pm.PackageManager.MATCH_ALL;
import static android.os.Process.INVALID_UID;

import static com.android.window.flags.Flags.enableAppRestartAfterUpdate;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.IActivityManager;
import android.app.IUidObserver;
import android.app.UidObserver;
import android.os.Process;
import android.os.RemoteException;
import android.util.IntArray;
import android.util.Slog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Use to monitor UIDs are really killed by the {@link IUidObserver}
 */
final class KillAppBlocker {
    private static final String TAG = "KillAppBlocker";
    private static final int DEFAULT_MAX_WAIT_TIMEOUT_MS = 1000;
    private final int mMaxWaitTimeoutMs;
    private CountDownLatch mUidsGoneCountDownLatch = new CountDownLatch(1);
    private List mActiveUids = new ArrayList();
    private boolean mRegistered = false;

    KillAppBlocker() {
        this(DEFAULT_MAX_WAIT_TIMEOUT_MS);
    }

    KillAppBlocker(int maxWaitTimeoutMs) {
        mMaxWaitTimeoutMs = maxWaitTimeoutMs;
    }

    private final IUidObserver mUidObserver = new UidObserver() {
        @Override
        public void onUidGone(int uid, boolean disabled) {
            synchronized (this) {
                mActiveUids.remove((Integer) uid);

                if (mActiveUids.size() == 0) {
                    mUidsGoneCountDownLatch.countDown();
                }
            }
        }
    };

    void register() {
        if (!mRegistered) {
            IActivityManager am = ActivityManager.getService();
            if (am != null) {
                try {
                    am.registerUidObserver(mUidObserver, ActivityManager.UID_OBSERVER_GONE,
                            ActivityManager.PROCESS_STATE_UNKNOWN, "pm");
                    mRegistered = true;
                } catch (RemoteException e) {
                    // no-op
                }
            }
        }
    }

    void unregister() {
        if (mRegistered) {
            IActivityManager am = ActivityManager.getService();
            if (am != null) {
                try {
                    mRegistered = false;
                    am.unregisterUidObserver(mUidObserver);
                } catch (RemoteException e) {
                    // no-op
                }
            }
        }
    }

    /**
     * Waits for all processes associated with the given package name to terminate.
     * This method monitors the UIDs (including isolated UIDs if applicable) across all users.
     *
     * @param ami The ActivityManagerInternal service.
     * @param snapshot The current computer snapshot for package metadata.
     * @param userManager The UserManagerService.
     * @param packageName The name of the package to wait for.
     * @return {@code true} if all monitored processes have terminated before the timeout;
     *         {@code false} if the timeout was reached or the observer is not registered.
     */
    boolean waitAppProcessGone(ActivityManagerInternal ami, Computer snapshot,
            UserManagerService userManager, String packageName) {
        if (!mRegistered) {
            return false;
        }
        synchronized (this) {
            if (ami != null) {
                int[] users = userManager.getUserIds();

                for (int i = 0; i < users.length; i++) {
                    final int userId = users[i];
                    final int uid = snapshot.getPackageUidInternal(
                            packageName, MATCH_ALL, userId, Process.SYSTEM_UID);
                    if (uid != INVALID_UID) {
                        if (ami.getUidProcessState(uid) != PROCESS_STATE_NONEXISTENT) {
                            Slog.d(TAG, "Adding uid " + uid + " for package " + packageName);
                            mActiveUids.add(uid);
                        }
                        if (enableAppRestartAfterUpdate()) {
                            // Add isolated UIDs owned by this app UID
                            IntArray isolatedUids = snapshot.getIsolatedUidsForUid(uid);
                            if (isolatedUids.size() > 0) {
                                Slog.d(TAG, "Found isolated uids " + isolatedUids + " for owner "
                                        + uid);
                            }
                            for (int j = 0; j < isolatedUids.size(); j++) {
                                int isolatedUid = isolatedUids.get(j);
                                if (ami.getUidProcessState(isolatedUid)
                                        != PROCESS_STATE_NONEXISTENT) {
                                    Slog.d(TAG, "Adding isolated uid " + isolatedUid
                                            + " for package " + packageName);
                                    mActiveUids.add(isolatedUid);
                                }
                            }
                        }
                    }

                    if (enablePccFrameworkSupport()) {
                        final int pccUid = snapshot.getPackageUidInternal(
                                packageName, MATCH_ALL, userId,
                                Process.SYSTEM_UID, true /* forPcc */);
                        if (pccUid != INVALID_UID
                                && ami.getUidProcessState(pccUid) != PROCESS_STATE_NONEXISTENT) {
                            Slog.d(TAG, "Adding pcc uid " + pccUid + " for package "
                                    + packageName);
                            mActiveUids.add(pccUid);
                        }
                    }
                }
            }
            if (mActiveUids.size() == 0) {
                // no active uid
                return true;
            }
        }

        try {
            return mUidsGoneCountDownLatch.await(mMaxWaitTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // no-op
        }
        return false;
    }
}
