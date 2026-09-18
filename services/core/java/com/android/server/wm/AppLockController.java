/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.wm;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_APP_LOCK;

import android.annotation.NonNull;
import android.app.AppLockInternal;
import android.content.Context;
import android.os.Trace;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.internal.os.BackgroundThread;
import com.android.internal.protolog.ProtoLog;
import com.android.server.LocalServices;

import java.util.Objects;
import java.util.Set;

/**
 * Manages the App Lock feature within {@link ActivityTaskManagerService} and
 * {@link WindowManagerService}.
 *
 * @hide
 */
final class AppLockController {

    private static final String TAG = "AppLockController";

    private final Context mContext;
    private final ActivityTaskManagerService mAtmService;
    private final WindowManagerService mWmService;

    private RecentTasks mRecentTasks;

    @VisibleForTesting
    final AppLockOverlayController mAppLockOverlayController;

    /**
     * Tracks packages that are currently in a locked state from App Lock.
     *
     * <p>The outer {@link SparseArray} is keyed by userId, and the inner {@link ArraySet} contains
     * the package names of the locked packages for that user. This information is queried by
     * {@link #isPackageLockedByAppLockLocked(String, int)}.
     *
     * <p>This is initialized in {@link #systemReady()} and subsequently updated by
     * {@link #mAppLockLockedPackageStateListener}.
     */
    @GuardedBy("mWmService.mGlobalLock")
    private final SparseArray<ArraySet<String>> mAppLockLockedPackages = new SparseArray<>();

    /**
     * Listener for changes in the App Lock locked state of packages from {@link AppLockInternal}.
     *
     * <p>This listener is responsible for keeping the internal state of locked packages
     * ({@link #mAppLockLockedPackages}) in sync. When a package's locked state changes, this
     * listener also performs the necessary window management tasks:
     * <ul>
     *     <li>When a package becomes locked, it calls the {@link AppLockOverlayController} to
     *     display a lock screen over the relevant activities and tasks. It also updates all
     *     associated {@link WindowState}s to set their hidden state via
     *     {@link WindowState#setHiddenWhileLockedByAppLock(boolean)}.</li>
     *     <li>When a package is unlocked, it updates the hidden state of the associated
     *     {@link WindowState}s to make them visible again.</li>
     * </ul>
     */
    private final AppLockInternal.PackageLockedStateListener mAppLockLockedPackageStateListener =
            new AppLockInternal.PackageLockedStateListener() {

                @Override
                public void onPackageLockedStateChanged(@NonNull String packageName, int userId,
                        boolean locked) {
                    Trace.beginSection(TAG + ".onPackageLockedStateChanged");
                    try {
                        Objects.requireNonNull(packageName);

                        synchronized (mWmService.mGlobalLock) {
                            ProtoLog.d(WM_DEBUG_APP_LOCK, "onPackageLockedByAppLockStateChanged:"
                                    + " %s, userId=%d, locked=%b", packageName, userId, locked);

                            ArraySet<String> lockedPackages = mAppLockLockedPackages.get(userId);
                            if (locked) {
                                if (lockedPackages == null) {
                                    lockedPackages = new ArraySet<>();
                                    mAppLockLockedPackages.put(userId, lockedPackages);
                                }
                                lockedPackages.add(packageName);

                                mAppLockOverlayController.lockActivitiesTasksForAppLock(packageName,
                                        userId);
                            } else {
                                if (lockedPackages == null) {
                                    // The package is not locked, so there is nothing to do.
                                    return;
                                }
                                lockedPackages.remove(packageName);
                                if (lockedPackages.isEmpty()) {
                                    mAppLockLockedPackages.remove(userId);
                                }

                                // When a package is unlocked, there is no need to explicitly remove
                                // any overlays. The LockedAppActivity is responsible for finishing
                                // itself when it detects that the package is no longer locked.
                            }

                            mWmService.mRoot.forAllWindows(w -> {
                                if (packageName.equals(w.getOwningPackage())) {
                                    w.setHiddenWhileLockedByAppLock(locked);
                                }
                            }, false);
                        }
                    } finally {
                        Trace.endSection();
                    }
                }
            };

    /**
     * Monitors package changes related to App Lock enabled state and notifies {@link RecentTasks}.
     */
    @VisibleForTesting
    final PackageMonitor mPackageMonitor = new PackageMonitor() {
        @Override
        public void onPackageAppLockEnabled(String packageName) {
            Trace.beginSection(TAG + ".onPackageAppLockEnabled");
            try {
                super.onPackageAppLockEnabled(packageName);

                final int userId = getChangingUserId();
                ProtoLog.d(WM_DEBUG_APP_LOCK, "onPackageAppLockEnabled: packageName=%s, userId=%d",
                        packageName, userId);
                getRecentTasks().onPackageAppLockEnabledChanged(packageName, userId,
                        /* enabled= */ true);
            } finally {
                Trace.endSection();
            }
        }

        @Override
        public void onPackageAppLockDisabled(String packageName) {
            Trace.beginSection(TAG + ".onPackageAppLockDisabled");
            try {
                super.onPackageAppLockDisabled(packageName);

                final int userId = getChangingUserId();
                ProtoLog.d(WM_DEBUG_APP_LOCK, "onPackageAppLockDisabled: packageName=%s, userId=%d",
                        packageName, userId);
                getRecentTasks().onPackageAppLockEnabledChanged(packageName, userId,
                        /* enabled= */ false);
            } finally {
                Trace.endSection();
            }
        }
    };

    AppLockController(WindowManagerService wmService) {
        mWmService = wmService;
        mContext = wmService.mContext;
        mAtmService = wmService.mAtmService;
        mAppLockOverlayController = new AppLockOverlayController(wmService);
    }

    private RecentTasks getRecentTasks() {
        if (mRecentTasks == null) {
            mRecentTasks = mAtmService.getRecentTasks();
        }
        return mRecentTasks;
    }

    /**
     * Initializes {@link AppLockController}. Should be called after
     * {@link com.android.server.SystemService#PHASE_SYSTEM_SERVICES_READY}.
     */
    void systemReady() {
        Trace.beginSection(TAG + ".systemReady");
        try {
            mPackageMonitor.register(mContext, UserHandle.ALL, BackgroundThread.getHandler());

            final AppLockInternal appLockInternal = LocalServices.getService(AppLockInternal.class);
            if (appLockInternal == null) {
                // This is a precautionary measure to avoid any potential system crashes or
                // bootloops.
                ProtoLog.wtf(WM_DEBUG_APP_LOCK, "AppLockInternal is null");
                return;
            }
            appLockInternal.registerPackageLockedStateListener(mAppLockLockedPackageStateListener);
            synchronized (mWmService.mGlobalLock) {
                final SparseArray<Set<String>> appLockEnabledPackages =
                        appLockInternal.getAppLockEnabledPackages();
                mAppLockLockedPackages.clear();
                for (int i = 0; i < appLockEnabledPackages.size(); i++) {
                    final int userId = appLockEnabledPackages.keyAt(i);
                    final Set<String> packages = appLockEnabledPackages.valueAt(i);
                    if (packages != null) {
                        mAppLockLockedPackages.put(userId, new ArraySet<>(packages));
                    }
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Returns {@code true} if the given package is currently in a locked state by App Lock.
     *
     * <p>This method checks the internal state of packages that are locked by App Lock, which is
     * stored in {@link #mAppLockLockedPackages}. This state is maintained by listening to changes
     * from {@link AppLockInternal} via {@link #mAppLockLockedPackageStateListener}.
     *
     * @param packageName the package name to check for the App Lock locked state
     * @param userId      the user for whom to check the locked state
     * @return {@code true} if the package is locked for the given user
     */
    @GuardedBy("mWmService.mGlobalLock")
    boolean isPackageLockedByAppLockLocked(@NonNull String packageName, int userId) {
        Objects.requireNonNull(packageName);

        return mAppLockLockedPackages.contains(userId)
                && mAppLockLockedPackages.get(userId).contains(packageName);
    }

    /**
     * Returns {@code true} if the given activity is currently in a locked state by App Lock.
     * Refer to {@link AppLockOverlayController#isActivityLockedByAppLock(ActivityRecord)}
     * for documentation.
     *
     * @param activity the activity to check for the App Lock locked state
     * @return {@code true} if the activity is locked
     */
    @GuardedBy("mWmService.mGlobalLock")
    boolean isActivityLockedByAppLockLocked(@NonNull ActivityRecord activity) {
        Objects.requireNonNull(activity);

        return mAppLockOverlayController.isActivityLockedByAppLock(activity);
    }

    /**
     * Adds the activity-level overlay on top of the given {@code activity} with the
     * {@link AppLockOverlayController}. Refer to
     * {@link AppLockOverlayController#addLockedByAppLockActivityOverlay(ActivityRecord)} for
     * documentation.
     */
    @GuardedBy("mWmService.mGlobalLock")
    void addLockedByAppLockActivityOverlayLocked(@NonNull ActivityRecord activity) {
        Objects.requireNonNull(activity);

        mAppLockOverlayController.addLockedByAppLockActivityOverlay(activity);
    }

    /**
     * Returns a set of package names that currently have a visible App Lock overlay for the
     * specified user.
     *
     * <p>This delegates to {@link AppLockOverlayController} to scan visible activities. This is
     * used in multi-window scenarios to identify all apps that are pending authentication. When
     * a user authenticates one app, this list can be used to simultaneously authenticate all
     * other visible locked apps, reducing user friction.
     *
     * @param userId The user ID for whom to find packages with visible App Lock overlay.
     * @return A set of package names corresponding to the visible App Lock overlay.
     */
    @GuardedBy("mWmService.mGlobalLock")
    Set<String> getPackagesWithVisibleAppLockOverlayLocked(int userId) {
        Trace.beginSection(TAG + ".getPackagesWithVisibleAppLockOverlayLocked");
        try {
            return mAppLockOverlayController.getPackagesWithVisibleAppLockOverlay(userId);
        } finally {
            Trace.endSection();
        }
    }
}
