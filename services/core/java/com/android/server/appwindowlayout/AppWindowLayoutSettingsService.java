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

package com.android.server.appwindowlayout;

import static android.content.pm.PackageManager.USER_MIN_ASPECT_RATIO_UNSET;
import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

import android.annotation.UserIdInt;
import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.util.Slog;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.server.LocalServices;
import com.android.server.SystemService;

import java.time.Clock;
import java.util.List;

/**
 * {@link SystemService} that restores per-app window layout settings when apps are installed.
 *
 * <p>This service is used by the Settings app to persist restored user aspect ratio settings, for
 * apps that are not installed at the time of Setup Wizard and Restore (which is most
 * non-prepackaged apps). The Settings app process can be killed at any point, so it is not suitable
 * for listening to package changes.
 *
 * <p>{@link AppWindowLayoutSettingsService} registers a {@link PackageMonitor} to listen to
 * package-added signals, and when an app is restored, calls
 * {@link IPackageManager#setUserMinAspectRatio(String, int, int)}.
 *
 * @hide
 */
public class AppWindowLayoutSettingsService extends SystemService {
    private static final String TAG = "AppWinLayoutSetService";

    @NonNull
    private final Context mContext;
    @NonNull
    private final IPackageManager mIPackageManager;

    private final Object mLock = new Object();

    @NonNull
    @GuardedBy("mLock")
    private final SparseArray<AppWindowLayoutSettingsRestoreStorage> mUserStorageMap =
            new SparseArray<>();

    private boolean mIsPackageMonitorRegistered = false;

    @NonNull
    @GuardedBy("mLock")
    private final AppWindowLayoutSettingsPackageMonitor mPackageMonitor;

    private final HandlerThread mBackgroundThread;

    public AppWindowLayoutSettingsService(@NonNull Context context,
            @NonNull List<Class<?>> dependencies) {
        super(context, dependencies);
        mContext = context;
        mIPackageManager = AppGlobals.getPackageManager();
        mPackageMonitor = new AppWindowLayoutSettingsPackageMonitor();
        mPackageMonitor.setCallback(this::onPackageAdded);
        mBackgroundThread = new HandlerThread("AppWinLayoutSetService",
                THREAD_PRIORITY_BACKGROUND);
        mBackgroundThread.start();
    }

    public AppWindowLayoutSettingsService(@NonNull Context context) {
        super(context);
        mContext = context;
        mIPackageManager = AppGlobals.getPackageManager();
        mPackageMonitor = new AppWindowLayoutSettingsPackageMonitor();
        mPackageMonitor.setCallback(this::onPackageAdded);
        mBackgroundThread = new HandlerThread("AppWinLayoutSetService",
                THREAD_PRIORITY_BACKGROUND);
        mBackgroundThread.start();
    }

    @VisibleForTesting
    AppWindowLayoutSettingsService(@NonNull Context context,
            @NonNull IPackageManager iPackageManager,
            @NonNull AppWindowLayoutSettingsPackageMonitor packageMonitor) {
        super(context);
        mContext = context;
        mIPackageManager = iPackageManager;
        mPackageMonitor = packageMonitor;
        mPackageMonitor.setCallback(this::onPackageAdded);
        mBackgroundThread = new HandlerThread("AppWinLayoutSetService",
                THREAD_PRIORITY_BACKGROUND);
        mBackgroundThread.start();
    }

    @Override
    public void onStart() {
        synchronized (mLock) {
            LocalServices.addService(AppWindowLayoutSettingsService.class, this);

            // Register package monitor now, because restore might have already been completed, but
            // the device rebooted before all apps were installed. PackageMonitor will be
            // unregistered on the first packageAdded signa if there is no restore data, and
            // registered again if restore data is received.
            registerPackageMonitor();
        }
    }
    /**
     * Stores packageName and aspectRatio for a given user to be set when package is installed.
     *
     * <p>This method also registers {@link AppWindowLayoutSettingsPackageMonitor} for package
     * updates.
     */
    // TODO(b/414381398): expose this API for apps to call directly, with a privileged permission.
    public void awaitPackageInstallForAspectRatio(@NonNull String packageName,
            @UserIdInt int userId,
            @PackageManager.UserMinAspectRatio int aspectRatio) {
        Slog.d(TAG, "Await package installed " + packageName + " to restore aspect ratio: "
                + aspectRatio);
        synchronized (mLock) {
            createAndGetStorage(userId).storePackageAndUserAspectRatio(packageName, aspectRatio);

            registerPackageMonitor();
        }
    }

    private void onPackageAdded(@NonNull String packageName, @UserIdInt int userId) {
        Slog.d(TAG, "Notified package installed: " + packageName);
        synchronized (mLock) {
            final AppWindowLayoutSettingsRestoreStorage storage = createAndGetStorage(userId);
            final int aspectRatio = storage.getAndRemoveUserAspectRatioForPackage(packageName);
            Slog.d(TAG, "Found aspect ratio: " + aspectRatio + " for package: "
                    + packageName);
            if (aspectRatio != USER_MIN_ASPECT_RATIO_UNSET) {
                checkExistingAspectRatioAndApplyRestore(packageName, userId, aspectRatio);
            }

            // If all restore data has been removed - either because all apps with restore data have
            // been restored or because the data has expired - stop listening to package updates.
            if (!storage.hasDataStored()) {
                unregisterPackageMonitor();
            }
        }
    }

    @GuardedBy("mLock")
    private AppWindowLayoutSettingsRestoreStorage createAndGetStorage(@UserIdInt int userId) {
        if (mUserStorageMap.get(userId) == null) {
            mUserStorageMap.put(userId, new AppWindowLayoutSettingsRestoreStorage(mContext, userId,
                    Clock.systemUTC()));
        }
        return mUserStorageMap.get(userId);
    }

    @GuardedBy("mLock")
    private void registerPackageMonitor() {
        if (!mIsPackageMonitorRegistered) {
            mIsPackageMonitorRegistered = true;
            mPackageMonitor.register(this.getContext(), mBackgroundThread.getLooper(),
                    UserHandle.ALL, true);
            Slog.d(TAG, "Registered package monitor for restoring aspect ratios.");
        }
    }

    @GuardedBy("mLock")
    private void unregisterPackageMonitor() {
        if (mIsPackageMonitorRegistered) {
            mIsPackageMonitorRegistered = false;
            mPackageMonitor.unregister();
            Slog.d(TAG, "Unregistered package monitor.");
        }
    }

    /** Applies the restore for per-app user set min aspect ratio. */
    private void checkExistingAspectRatioAndApplyRestore(@NonNull String pkgName, int userId,
            @PackageManager.UserMinAspectRatio int aspectRatio) {
        try {
            final int existingUserAspectRatio = mIPackageManager.getUserMinAspectRatio(pkgName,
                    userId);
            // Don't apply the restore if the aspect ratio have already been set for the app.
            // Packages which are not yet installed will return `USER_MIN_ASPECT_RATIO_UNSET`.
            if (existingUserAspectRatio != USER_MIN_ASPECT_RATIO_UNSET) {
                Slog.d(TAG, "Not restoring user aspect ratio: " + aspectRatio + " for package: "
                        + pkgName + " as it is already set to " + existingUserAspectRatio + ".");
                return;
            }

            mIPackageManager.setUserMinAspectRatio(pkgName, userId, aspectRatio);
            Slog.d(TAG, "Restored user aspect ratio: " + aspectRatio + " for package: " + pkgName);
        } catch (Exception e) {
            Slog.e(TAG, "Could not restore user aspect ratio for package " + pkgName, e);
        }
    }
}
